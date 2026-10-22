package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.SceneSpace
import com.paperscrape.livewallpaper.engine.Storefront
import com.paperscrape.livewallpaper.engine.StorefrontStreet
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.engine.opennessFor
import com.paperscrape.livewallpaper.engine.peopleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Who updates from the 5.13** (v5.14): the files a 5.13 user has, written by the 5.13's own code -- a backup, the
 * saved-theme store, a shared theme (`resources/update/`, written on 2026-10-15 by a probe run in the 5.13's tree,
 * `ScrittiDalla513Probe`, kept outside the repository) -- read by this release.
 *
 * Their settings are the maintainer's way of using the wallpaper and the round's three changes on top: Autumn chosen
 * by hand with the calendar off, a place of his own, Live Weather and the update check off; *Christmas lights* on in
 * Autumn, off in a saved Christmas; the three groups' hours on (shops 09-20, towers 07-23, public 08-14); the distant
 * houses at 50 %. The 5.14 adds no setting and changes no stored one: every value comes back as written, and what
 * changes is what the same values draw -- the hours kept to the minute, the decorations on every building, the houses
 * on both sides of each mountain, the street changing only by scrolling.
 *
 * And the street: the buildings each place of each built-in street, and of a saved one, shows are the ones the 5.13
 * dealt in its first round (`street-dealt-by-v5.13.txt`), the round a 5.13 started from after the phone restarted --
 * a 5.13 that had come back into view since was past it, and its user sees the street of a restart once.
 */
class UpdateFromV513Test {

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("update/$name")) { "update/$name" }.readText()

    @Test
    fun `a backup the 5_13 wrote restores every setting it carried`() {
        val parsed = parseAppBackup(resource("backup-written-by-v5.13.json"))
        assertTrue("$parsed", parsed is BackupParseResult.Ok)
        val backup = (parsed as BackupParseResult.Ok).backup
        val s = backup.settings
        assertEquals("autumn", s.themeId)
        assertFalse(s.autoThemeByDate)
        assertTrue(s.syncWithRealTime)
        assertTrue(s.useCustomLocation)
        assertFalse(s.liveWeatherEnabled)
        assertFalse(s.automaticUpdateCheckEnabled)
        val autumn = backup.themeCustomizations.getValue("autumn")
        assertTrue((autumn.christmasBuildingLightsEnabled && autumn.christmasTreeLightsEnabled && autumn.christmasFirsEnabled))
        assertTrue(autumn.distantHouses.visible)
        assertEquals(0.5f, autumn.distantHouses.density, 0f)
        assertTrue(autumn.shopHoursEnabled && autumn.towerHoursEnabled && autumn.publicHoursEnabled)
        assertEquals(listOf(9f, 20f, 7f, 23f, 8f, 14f), listOf(
            autumn.shopOpenHour, autumn.shopCloseHour, autumn.towerOpenHour, autumn.towerCloseHour,
            autumn.publicOpenHour, autumn.publicCloseHour,
        ))
        assertFalse("turned off, it stays off", DecorationExclusion.christmasOn(backup.themeCustomizations.getValue("christmas")))
        assertEquals(setOf("autumn"), backup.customThemeData.overrides.keys)
        assertEquals(listOf("custom:1"), backup.customThemeData.customThemes.map { it.id })
        // Every one of the settings the 5.13 wrote comes back as written (v5.14C: the six above alone left fifteen out):
        // the backup this release writes from what it read carries each key with the same value. A key the file holds at
        // its default cannot tell a read from a default here; `BackupAndThemeShareTest`'s round trip holds every key with
        // values that are not the defaults.
        val written = org.json.JSONObject(resource("backup-written-by-v5.13.json")).getJSONObject("settings")
        val again = org.json.JSONObject(backup.toJsonString()).getJSONObject("settings")
        assertEquals("the settings the 5.13 wrote", 21, written.length())
        for (key in written.keys()) {
            assertTrue("$key is not written back", again.has(key))
            val a = written.get(key)
            val b = again.get(key)
            if (a is Number && b is Number) assertEquals(key, a.toDouble(), b.toDouble(), 1e-6) else assertEquals(key, a.toString(), b.toString())
        }
    }

    /** The same hours, kept to the minute now: open at the opening minute, empty in the last, shut at the closing. */
    @Test
    fun `the hours the 5_13 stored are kept to the minute`() {
        val autumn = (parseAppBackup(resource("backup-written-by-v5.13.json")) as BackupParseResult.Ok)
            .backup.themeCustomizations.getValue("autumn")
        fun hm(h: Int, m: Int) = h + m / 60f
        for ((building, open, close) in listOf(
            Triple(Storefront.SHOP, 9, 20), Triple(Storefront.RESTAURANT, 9, 20), Triple(Storefront.BAR, 9, 20),
            Triple(Storefront.SCHOOL, 8, 14),
        )) {
            assertEquals("$building before opening", 0f, autumn.opennessFor(building, hm(open - 1, 59)), 0f)
            assertEquals("$building at opening", 1f, autumn.opennessFor(building, hm(open, 0)), 0f)
            assertEquals("$building at opening", 3, autumn.peopleFor(building, 3, hm(open, 0)))
            assertEquals("$building in the last minute", 1f, autumn.opennessFor(building, hm(close - 1, 59)), 0f)
            assertEquals("$building in the last minute", 0, autumn.peopleFor(building, 3, hm(close - 1, 59)))
            assertEquals("$building at closing", 0f, autumn.opennessFor(building, hm(close, 0)), 0f)
        }
        for (building in listOf(Storefront.FIRE_STATION, Storefront.POLICE_STATION)) {
            assertEquals("$building never closes", 1f, autumn.opennessFor(building, hm(3, 0)), 0f)
            assertEquals("$building keeps its people", 3, autumn.peopleFor(building, 3, hm(3, 0)))
        }
        val tower = SceneSpace.SceneVariant.TOWER
        assertEquals(1f, autumn.opennessFor(tower, hm(7, 0)), 0f)
        assertEquals(0, autumn.peopleFor(tower, 3, hm(22, 59)))
        assertEquals(0f, autumn.opennessFor(tower, hm(23, 0)), 0f)
    }

    @Test
    fun `the store the 5_13 saved reads back, and every street shows the buildings the 5_13 dealt in its first round`() {
        val store = customThemeDataFromJsonString(resource("store-saved-by-v5.13.json"))
        assertTrue((store.overrides.getValue("autumn").customization.christmasBuildingLightsEnabled && store.overrides.getValue("autumn").customization.christmasTreeLightsEnabled && store.overrides.getValue("autumn").customization.christmasFirsEnabled))
        assertFalse(DecorationExclusion.christmasOn(store.customThemes.single { it.id == "custom:1" }.customization))
        var checked = 0
        for (line in resource("street-dealt-by-v5.13.txt").lines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split(' ')
            val seed = parts[0].hashCode()
            for ((i, name) in parts.drop(1).withIndex()) {
                val place = i - 20
                assertEquals("${parts[0]}, place $place", name, StorefrontStreet.storefrontAt(seed, place).name)
                checked++
            }
        }
        assertEquals("twelve built-in streets and a saved one, 80 places each", 13 * 80, checked)
    }

    @Test
    fun `a theme the 5_13 shared imports with its lights, its hours and its houses`() {
        val parsed = parseThemeShare(resource("theme-shared-by-v5.13.json"))
        assertTrue("$parsed", parsed is ThemeParseResult.Ok)
        val c = (parsed as ThemeParseResult.Ok).share.customization
        assertTrue((c.christmasBuildingLightsEnabled && c.christmasTreeLightsEnabled && c.christmasFirsEnabled))
        assertTrue(c.shopHoursEnabled && c.towerHoursEnabled && c.publicHoursEnabled)
        assertEquals(0.5f, c.distantHouses.density, 0f)
    }
}
