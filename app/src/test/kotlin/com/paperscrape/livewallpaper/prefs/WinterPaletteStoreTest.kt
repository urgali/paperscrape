package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Turning the winter palette on and off never rewrites the hills' and the mountains' colours** (decision 117):
 * the snow is drawn from the rule (`GroundSnow`), not stored, so the colours the user chose are in the store untouched
 * while it is on and come back as they were when it is turned off. Nothing new is written for it: the store after the
 * palette's switches holds exactly the keys it held before, but the two palette flags.
 *
 * [WallpaperPrefs] is handed a store over a scratch file, as `ShootingStarsPreferenceStoreTest` does.
 */
class WinterPaletteStoreTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("winter-palette-store").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
    }

    /** Every key of the store by name, but the two palette flags the switch writes. */
    private suspend fun storeWithoutThePalette(): Map<String, Any> =
        store.data.first().asMap()
            .mapKeys { it.key.name }
            .filterKeys { it != "winter_colors_enabled" && it != "fall_colors_enabled" }

    @Test
    fun `the winter palette on and off leaves the hills and the mountains as the user chose them`() = runBlocking {
        val hillsDay = 0xFF123456.toInt()
        val hillsNight = 0xFF0A0B0C.toInt()
        val frontDay = 0xFF405060.toInt()
        val backNight = 0xFF203040.toInt()
        prefs.setHillsColorDay(hillsDay, "autumn")
        prefs.setHillsColorNight(hillsNight, "autumn")
        prefs.setMountainColorDay(true, frontDay, "autumn")
        prefs.setMountainColorNight(false, backNight, "autumn")
        val before = storeWithoutThePalette()

        prefs.setWinterColorsEnabled(true, "autumn")
        val on = prefs.settingsFlow.first().pendingCustomization
        assertTrue("the palette is on", on.winterColorsEnabled)
        assertEquals("nothing else written while it is on", before, storeWithoutThePalette())
        assertEquals("the stored hills by day, under the snow", hillsDay, on.hillsColorDay)
        assertEquals(hillsNight, on.hillsColorNight)
        assertEquals(frontDay, on.mountainsFront.colorDay)
        assertEquals(backNight, on.mountainsBack.colorNight)

        prefs.setWinterColorsEnabled(false, "autumn")
        val off = prefs.settingsFlow.first().pendingCustomization
        assertFalse("the palette is off", off.winterColorsEnabled)
        assertEquals("nothing else written when it is turned off", before, storeWithoutThePalette())
        assertEquals("the hills come back as they were", hillsDay, off.hillsColorDay)
        assertEquals(hillsNight, off.hillsColorNight)
        assertEquals(frontDay, off.mountainsFront.colorDay)
        assertEquals(backNight, off.mountainsBack.colorNight)
    }

    @Test
    fun `on a winter theme turned off and on, its own colours stay`() = runBlocking {
        prefs.setSnowPiles(0.5f, "winter")
        val before = storeWithoutThePalette()
        val shipped = prefs.settingsFlow.first().pendingCustomization
        prefs.setWinterColorsEnabled(false, "winter")
        prefs.setWinterColorsEnabled(true, "winter")
        assertEquals(before, storeWithoutThePalette())
        val after = prefs.settingsFlow.first().pendingCustomization
        assertEquals(shipped.hillsColorDay, after.hillsColorDay)
        assertEquals(shipped.hillsColorNight, after.hillsColorNight)
        assertEquals(shipped.mountainsFront, after.mountainsFront)
        assertEquals(shipped.mountainsBack, after.mountainsBack)
    }
}
