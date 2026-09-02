package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.update.UPDATE_PREFS_STORE_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A corrupt preferences file must cost the user that one store's settings and nothing else.
 *
 * **Why this cannot simply call `WallpaperPrefs(context).settingsFlow`.** `preferencesDataStore`
 * builds one `DataStore` per process and caches what it has read, so once the app's own store is
 * warm, overwriting the file underneath it changes nothing an in-process reader can observe -- a
 * test written that way would pass whether or not the fix existed. What actually exercises the
 * recovery is a *fresh* `DataStore` opened over the corrupt bytes, which is exactly what the next
 * process gets after this one dies. Every store below is therefore opened the way the production
 * declarations open theirs -- same file layout, same [PrefsRecovery.replacingCorruptFile] handler
 * -- and the remaining half of the claim ("kill the app, restart it, the wallpaper is still
 * there") is verified on a device and written up in `release-notes/v3.1.md`.
 *
 * The corruption is the real one: non-proto bytes whose very first tag has an invalid wire type,
 * which is what the v3.0 assessment wrote into `paperscrape_prefs.preferences_pb` to bring the
 * process down, and DataStore rejects them with the same `CorruptionException` it produced then.
 */
@RunWith(AndroidJUnit4::class)
class PrefsCorruptionRecoveryTest {

    // Restores the real preference store around every test: these tests write the app's own
    // DataStore on purpose, and without this the phone kept their last theme. See RealPrefsGuard.
    @get:org.junit.Rule
    val realPrefs = RealPrefsGuard()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val corruptBytes = ByteArray(39) { 0xFF.toByte() }

    private val marker = booleanPreferencesKey("recovery_test_marker")
    private val textMarker = stringPreferencesKey("recovery_test_text")

    private val allStores = listOf(
        WALLPAPER_PREFS_STORE_NAME,
        CUSTOM_THEME_STORE_NAME,
        UPDATE_PREFS_STORE_NAME,
    )

    /**
     * Scratch files carrying the three real store names, in the real DataStore directory, so the
     * test corrupts the same shape of file at the same kind of path without destroying the
     * settings of whoever happens to be using the device.
     */
    private fun storeFile(name: String): File =
        File(context.filesDir, "datastore/$name-recoverytest.preferences_pb")

    /**
     * Opens one store, runs [block] against it, and shuts it down again.
     *
     * The shutdown is not tidiness: DataStore refuses to have two live instances over one file, and
     * every assertion here depends on being able to re-open a file to see what a previous instance
     * left on disk -- the same thing an app restart does.
     */
    private fun <T> withStore(name: String, block: suspend (DataStore<Preferences>) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = PrefsRecovery.replacingCorruptFile(),
                scope = scope,
                produceFile = { storeFile(name) },
            )
            return runBlocking { block(store) }
        } finally {
            scope.cancel()
            runBlocking { job.join() }
        }
    }

    private fun read(name: String): Preferences = withStore(name) { it.data.first() }

    /** Writes a non-default value into every store and returns each file's bytes as saved. */
    private fun seedAllStores(): Map<String, ByteArray> {
        allStores.forEach { name ->
            withStore(name) { store ->
                store.edit {
                    it[marker] = true
                    it[textMarker] = "set-by-$name"
                }
            }
        }
        return allStores.associateWith { storeFile(it).readBytes() }
    }

    @Before
    fun clean() = allStores.forEach { storeFile(it).delete() }

    @After
    fun cleanUp() = allStores.forEach { storeFile(it).delete() }

    @Test
    fun corruptStoreComesBackOnItsDefaults() {
        seedAllStores()
        val target = WALLPAPER_PREFS_STORE_NAME
        storeFile(target).writeBytes(corruptBytes)

        // The read that used to throw CorruptionException all the way out to the process's default
        // handler. It must now simply produce an empty set of preferences, which is what every
        // reader in the app already resolves to its declared default.
        val recovered = read(target)

        assertNull("the corrupt store must read as unset, not as its old value", recovered[marker])
        assertNull(recovered[textMarker])
        assertTrue(
            "the corrupt bytes must have been replaced, not kept to fail again next launch",
            !storeFile(target).readBytes().contentEquals(corruptBytes),
        )
        // And the replacement has to be durable: a second launch reads the repaired file rather
        // than repairing it again.
        assertNull(read(target)[marker])
    }

    @Test
    fun corruptStoreIsWritableAgainImmediately() {
        seedAllStores()
        val target = CUSTOM_THEME_STORE_NAME
        storeFile(target).writeBytes(corruptBytes)

        // Not just "the read did not throw": the store has to be a working store again, or the
        // user is left with settings that silently refuse to save.
        withStore(target) { store ->
            store.data.first()
            store.edit { it[textMarker] = "written-after-recovery" }
        }

        assertEquals("written-after-recovery", read(target)[textMarker])
    }

    @Test
    fun aCorruptStoreDoesNotCostTheOtherStoresAnything() {
        val before = seedAllStores()

        // One at a time, each of the three, because the property is that the blast radius is
        // exactly one file -- and that is a claim about all three declarations, not about one.
        for (corrupted in allStores) {
            allStores.forEach { storeFile(it).writeBytes(before.getValue(it)) }
            storeFile(corrupted).writeBytes(corruptBytes)

            assertNull("$corrupted should have reset", read(corrupted)[marker])

            for (untouched in allStores.filter { it != corrupted }) {
                assertArrayEquals(
                    "corrupting $corrupted must not rewrite $untouched",
                    before.getValue(untouched),
                    storeFile(untouched).readBytes(),
                )
                val stillThere = read(untouched)
                assertEquals("corrupting $corrupted must not clear $untouched", true, stillThere[marker])
                assertEquals("set-by-$untouched", stillThere[textMarker])
            }
        }
    }

    /**
     * The other half of separating corruption from every other failure: opening a store that is
     * *not* corrupt must never rewrite it. If it did, the handler would be a data-loss bug of its
     * own rather than a recovery from one.
     */
    @Test
    fun healthyStoreIsNeverRewrittenByOpeningIt() {
        val before = seedAllStores()
        val name = UPDATE_PREFS_STORE_NAME

        assertEquals(true, read(name)[marker])
        assertArrayEquals(before.getValue(name), storeFile(name).readBytes())
    }
}
