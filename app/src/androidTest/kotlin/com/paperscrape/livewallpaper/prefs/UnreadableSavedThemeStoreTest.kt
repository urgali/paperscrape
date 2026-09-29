package com.paperscrape.livewallpaper.prefs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.engine.CUSTOM_THEME_SCHEMA_VERSION
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.StaticSceneObject
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.toJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Item 18 against the real store (v5.9A): the saved theme that cannot be read stays on disk byte for
 * byte while the user edits the others.
 *
 * `UnreadableSavedThemeTest` holds the same three properties on the JVM, with the reference
 * `org.json` that the unit tests run on. This runs them on **Android's own** `org.json`, which
 * writes differently (it escapes `/` as `\/`, and keeps keys in insertion order), through the real
 * `CustomThemeStore.update` and DataStore, and reads the answer off the file itself: the bytes of the
 * damaged entry must be in `paperscrape_custom_themes.preferences_pb` after every edit.
 */
@RunWith(AndroidJUnit4::class)
class UnreadableSavedThemeStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = CustomThemeStore(context)
    private val file get() = File(context.filesDir, "datastore/$CUSTOM_THEME_STORE_NAME.preferences_pb")

    @Before fun clean() = runBlocking { wipe() }
    @After fun tidy() = runBlocking { wipe() }

    private suspend fun wipe() {
        store.replaceAll(CustomThemeData.EMPTY)
        CustomThemeRegistry.update(store.dataFlow.first())
    }

    @Test
    fun theOthersAreShownAndEditedWhileTheUnreadableOneStaysOnDisk() = runBlocking {
        // Written by this platform's org.json, then damaged the way a vanished object type damages
        // it, so the kept text carries Android's escapes.
        val broken = entry("custom:1003", "Old / fountain é").toJson().toString()
            .replaceFirst("\"HOUSE\"", "\"FOUNTAIN\"")
        assertTrue("the fixture must carry Android's escaped slash", broken.contains("\\/"))
        assertTrue("the fixture must be damaged", broken.contains("FOUNTAIN"))
        val mine = entry("custom:1001", "My autumn")
        val other = entry("custom:1002", "Night walk")
        store.replaceAllJson(
            "{\"schemaVersion\":$CUSTOM_THEME_SCHEMA_VERSION,\"overrides\":{}," +
                "\"customThemes\":[${mine.toJson()},$broken,${other.toJson()}]}",
        )

        val before = store.dataFlow.first()
        assertEquals("the healthy themes are shown", listOf("My autumn", "Night walk"), before.customThemes.map { it.name })

        for (round in 1..3) {
            store.renameCustomTheme(other.id, "Night walk $round")
            val now = store.dataFlow.first()
            assertEquals("round $round: the edit was dropped", "Night walk $round", now.customThemes.last().name)
            val onDisk = file.readBytes()
            assertTrue(
                "round $round: the unreadable theme's bytes are not on disk any more",
                indexOf(onDisk, broken.toByteArray(Charsets.UTF_8)) >= 0,
            )
        }
        assertEquals(listOf("My autumn", "Night walk 3"), store.dataFlow.first().customThemes.map { it.name })
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun entry(id: String, name: String) = CustomThemeEntry(
        id = id,
        name = name,
        theme = ThemeCatalog.byId("autumn").copy(id = id, displayName = name),
        layout = SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.12f, tileFractionX = 0.2f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.30f, tileFractionX = 0.6f),
            ),
            cars = emptyList(),
        ),
        customization = defaultCustomizationFor("autumn"),
    )
}
