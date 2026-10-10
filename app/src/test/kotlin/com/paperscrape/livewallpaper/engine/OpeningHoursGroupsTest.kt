package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.prefs.AppBackup
import com.paperscrape.livewallpaper.prefs.BackupParseResult
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.prefs.parseAppBackup
import com.paperscrape.livewallpaper.prefs.toJsonString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The opening hours in two groups** (v5.12, the maintainer's *«vorrei inoltre aggiungere uno slide
 * per orari solo grattacieli e solo negozi, al momento è globale per grattacieli e negozi»*,
 * 2026-10-09): the shops -- the restaurant, the school and the bar -- keep the shops' hours, the towers
 * the towers', each building by what it is drawn as, the rule its colours follow; a house keeps none.
 * And **whoever had the hours on finds them on in both groups, at the same hours**: every payload
 * written before v5.12 holds one setting for both, and each group reads it.
 *
 * The preference store's side of the same promise is `OpeningHoursPreferenceStoreTest`.
 */
class OpeningHoursGroupsTest {

    private val shopsOnly = SceneCustomization.DEFAULT.copy(shopHoursEnabled = true, shopOpenHour = 9f, shopCloseHour = 18f)
    private val towersOnly = SceneCustomization.DEFAULT.copy(towerHoursEnabled = true, towerOpenHour = 9f, towerCloseHour = 18f)

    private val shops = listOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR)
    private val houses = listOf(SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE)

    @Test
    fun `each building keeps its own group's hours, and a house keeps none`() {
        val midnight = 0f
        // The shops' hours on: the shops shut at midnight, the towers open.
        for (v in shops) assertEquals("$v follows the shops' hours", 0f, shopsOnly.opennessFor(v, midnight), 0f)
        assertEquals("a tower does not follow the shops' hours", 1f, shopsOnly.opennessFor(SceneSpace.SceneVariant.TOWER, midnight), 0f)
        // The towers' hours on: the other way round.
        for (v in shops) assertEquals("$v does not follow the towers' hours", 1f, towersOnly.opennessFor(v, midnight), 0f)
        assertEquals("a tower follows the towers' hours", 0f, towersOnly.opennessFor(SceneSpace.SceneVariant.TOWER, midnight), 0f)
        // At noon both are open; a house is open at every hour whatever either group says.
        val both = shopsOnly.copy(towerHoursEnabled = true, towerOpenHour = 9f, towerCloseHour = 18f)
        for (v in shops + SceneSpace.SceneVariant.TOWER) assertEquals(1f, both.opennessFor(v, 12f), 0f)
        for (v in houses) for (h in 0..23) assertEquals("$v at $h:00", 1f, both.opennessFor(v, h.toFloat()), 0f)
    }

    @Test
    fun `each group runs its own hours`() {
        val c = SceneCustomization.DEFAULT.copy(
            shopHoursEnabled = true, shopOpenHour = 8f, shopCloseHour = 13f,
            towerHoursEnabled = true, towerOpenHour = 14f, towerCloseHour = 22f,
        )
        assertEquals(1f, c.opennessFor(SceneSpace.SceneVariant.BAR, 10f), 0f)
        assertEquals(0f, c.opennessFor(SceneSpace.SceneVariant.TOWER, 10f), 0f)
        assertEquals(0f, c.opennessFor(SceneSpace.SceneVariant.BAR, 18f), 0f)
        assertEquals(1f, c.opennessFor(SceneSpace.SceneVariant.TOWER, 18f), 0f)
    }

    @Test
    fun `with both switches off nothing depends on the hour, as before the hours existed`() {
        for (v in SceneSpace.SceneVariant.entries) for (h in 0..23) {
            assertEquals("$v at $h:00", 1f, SceneCustomization.DEFAULT.opennessFor(v, h.toFloat()), 0f)
        }
    }

    /**
     * **Shut at night, a business's glass is dark like a house's unlit window** (v5.12, the
     * maintainer's *«Scuri, come le case (consigliato)»* of 2026-10-09): open it is the lit window to
     * the bit, by day both are the day's glass, and across the closing fade it goes from one to the other.
     */
    @Test
    fun `a closed business is dark glass at night, and open it is the lit window it always was`() {
        for (step in 0..20) {
            val night = step / 20f
            assertEquals("open at $night", SceneObjectRenderer.windowGlassColor(night), SceneObjectRenderer.businessGlassColor(night, 1f))
            assertEquals("shut at $night", SceneObjectRenderer.unlitWindowGlassColor(night), SceneObjectRenderer.businessGlassColor(night, 0f))
        }
        for (step in 0..10) {
            assertEquals("by day, open or shut", SceneObjectRenderer.WINDOW_GLASS_DAY, SceneObjectRenderer.businessGlassColor(0f, step / 10f))
        }
        assertEquals("shut at full night: the dark glass", SceneObjectRenderer.UNLIT_GLASS_NIGHT, SceneObjectRenderer.businessGlassColor(1f, 0f))
        // Darker as it closes: the lit window's channels fall toward the dark glass's.
        var previous = SceneObjectRenderer.businessGlassColor(1f, 1f)
        for (step in 19 downTo 0) {
            val now = SceneObjectRenderer.businessGlassColor(1f, step / 20f)
            for (shift in listOf(16, 8)) assertTrue((now ushr shift and 0xFF) <= (previous ushr shift and 0xFF))
            previous = now
        }
    }

    /** What a build from v4.22 to v5.11 wrote: the one setting, and nothing of the two groups. */
    private fun writtenBefore512(enabled: Boolean, open: Float, close: Float): JSONObject {
        val json = JSONObject(SceneCustomization.DEFAULT.toJson().toString())
        for (key in listOf("shop", "tower")) {
            json.remove("${key}HoursEnabled"); json.remove("${key}OpenHour"); json.remove("${key}CloseHour")
        }
        json.put("businessHoursEnabled", enabled)
        json.put("businessOpenHour", open.toDouble())
        json.put("businessCloseHour", close.toDouble())
        return json
    }

    @Test
    fun `a payload written before v5_12 with the hours on opens both groups at its hours`() {
        val c = sceneCustomizationFromJson(writtenBefore512(true, 8.25f, 1.75f))
        assertTrue(c.shopHoursEnabled)
        assertEquals(8.25f, c.shopOpenHour, 0f)
        assertEquals(1.75f, c.shopCloseHour, 0f)
        assertTrue(c.towerHoursEnabled)
        assertEquals(8.25f, c.towerOpenHour, 0f)
        assertEquals(1.75f, c.towerCloseHour, 0f)
        // And off stays off in both, its hours kept for when a group is turned on.
        val off = sceneCustomizationFromJson(writtenBefore512(false, 7f, 21f))
        assertFalse(off.shopHoursEnabled)
        assertFalse(off.towerHoursEnabled)
        assertEquals(7f, off.towerOpenHour, 0f)
        assertEquals(21f, off.shopCloseHour, 0f)
    }

    @Test
    fun `a payload written since keeps each group's own hours, whatever the old setting beside them says`() {
        val c = SceneCustomization.DEFAULT.copy(
            shopHoursEnabled = false, shopOpenHour = 10f, shopCloseHour = 16f,
            towerHoursEnabled = true, towerOpenHour = 6f, towerCloseHour = 23.5f,
        )
        val json = JSONObject(c.toJson().toString())
        val back = sceneCustomizationFromJson(json)
        assertEquals(c.shopHoursEnabled, back.shopHoursEnabled)
        assertEquals(c.shopOpenHour, back.shopOpenHour, 0f)
        assertEquals(c.shopCloseHour, back.shopCloseHour, 0f)
        assertEquals(c.towerHoursEnabled, back.towerHoursEnabled)
        assertEquals(c.towerOpenHour, back.towerOpenHour, 0f)
        assertEquals(c.towerCloseHour, back.towerCloseHour, 0f)
        // The old setting is still written, as the shops', for a build before v5.12 to read.
        assertEquals(false, json.getBoolean("businessHoursEnabled"))
        assertEquals(10.0, json.getDouble("businessOpenHour"), 0.0)
    }

    @Test
    fun `a saved theme and a backup written before v5_12 open both groups at its hours`() {
        // A saved theme: the customization inside its entry.
        val entry = JSONObject().apply {
            put("id", "custom_1")
            put("name", "Mine")
            put("theme", ThemeCatalog.byId("autumn").toJson())
            put("layout", SceneObjectCatalog.layoutFor("autumn", 0).toJson())
            put("customization", writtenBefore512(true, 7.5f, 22f))
        }
        val saved = customThemeEntryFromJson(entry).customization
        assertTrue(saved.shopHoursEnabled && saved.towerHoursEnabled)
        assertEquals(7.5f, saved.towerOpenHour, 0f)
        assertEquals(22f, saved.shopCloseHour, 0f)
        // A backup: written by this build, its customization put back in the shape of before.
        val backup = JSONObject(
            AppBackup.from(
                WallpaperSettings(themeCustomizations = mapOf("winter" to SceneCustomization.DEFAULT)),
                CustomThemeData(), appVersionName = "5.11", nowMillis = 0L,
            ).toJsonString(),
        )
        backup.getJSONObject("themeCustomizations").put("winter", writtenBefore512(true, 9.5f, 19.25f))
        val parsed = parseAppBackup(backup.toString())
        assertTrue("a backup of that shape is read: $parsed", parsed is BackupParseResult.Ok)
        val winter = (parsed as BackupParseResult.Ok).backup.themeCustomizations.getValue("winter")
        assertTrue(winter.shopHoursEnabled && winter.towerHoursEnabled)
        assertEquals(9.5f, winter.shopOpenHour, 0f)
        assertEquals(19.25f, winter.towerCloseHour, 0f)
    }
}
