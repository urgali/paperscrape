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
 * **The opening hours in three groups** (v5.12 made two, the maintainer's *«vorrei inoltre aggiungere
 * uno slide per orari solo grattacieli e solo negozi, al momento è globale per grattacieli e negozi»*,
 * 2026-10-09; v5.13 the third, his *«il resto accetto i consigliati»* of 2026-10-11): the towers keep
 * the towers' hours, the shops -- the restaurant, the bar and the shop -- the shops', the public
 * buildings -- the school, the fire station and the police station -- theirs, each building by what it
 * is, the rule its colours follow; a house keeps none. And **whoever had the hours on finds them on**:
 * every payload written before v5.12 holds one setting for all, and each group reads it; every payload
 * written by the 5.12 holds the shops' and the towers', and the public buildings read the shops', which
 * the school kept until then.
 *
 * The preference store's side of the same promise is `OpeningHoursPreferenceStoreTest`.
 */
class OpeningHoursGroupsTest {

    private val shopsOnly = SceneCustomization.DEFAULT.copy(shopHoursEnabled = true, shopOpenHour = 9f, shopCloseHour = 18f)
    private val towersOnly = SceneCustomization.DEFAULT.copy(towerHoursEnabled = true, towerOpenHour = 9f, towerCloseHour = 18f)
    private val publicsOnly = SceneCustomization.DEFAULT.copy(publicHoursEnabled = true, publicOpenHour = 9f, publicCloseHour = 18f)

    private val shops = Storefront.ALL.filter { it.group == StorefrontGroup.SHOPS }
    private val publics = Storefront.ALL.filter { it.group == StorefrontGroup.PUBLIC }
    private val houses = listOf(SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE)

    @Test
    fun `the groups are the ones the maintainer asked for`() {
        assertEquals(listOf(Storefront.RESTAURANT, Storefront.BAR, Storefront.SHOP), shops)
        assertEquals(listOf(Storefront.SCHOOL, Storefront.FIRE_STATION, Storefront.POLICE_STATION), publics)
    }

    @Test
    fun `each building keeps its own group's hours, and a house keeps none`() {
        val midnight = 0f
        // Each group's hours on alone: that group's buildings that keep hours shut at midnight (the stations
        // never do, below), the other two groups stay open.
        for (b in shops) {
            assertEquals("$b follows the shops' hours", 0f, shopsOnly.opennessFor(b, midnight), 0f)
            assertEquals("$b does not follow the towers'", 1f, towersOnly.opennessFor(b, midnight), 0f)
            assertEquals("$b does not follow the public buildings'", 1f, publicsOnly.opennessFor(b, midnight), 0f)
        }
        for (b in publics) {
            // The school keeps the public buildings' hours; the fire and the police station never close (2026-10-12).
            val expected = if (b == Storefront.SCHOOL) 0f else 1f
            assertEquals("$b and the public buildings' hours", expected, publicsOnly.opennessFor(b, midnight), 0f)
            assertEquals("$b does not follow the shops'", 1f, shopsOnly.opennessFor(b, midnight), 0f)
            assertEquals("$b does not follow the towers'", 1f, towersOnly.opennessFor(b, midnight), 0f)
        }
        assertEquals("a tower follows the towers' hours", 0f, towersOnly.opennessFor(SceneSpace.SceneVariant.TOWER, midnight), 0f)
        assertEquals(1f, shopsOnly.opennessFor(SceneSpace.SceneVariant.TOWER, midnight), 0f)
        assertEquals(1f, publicsOnly.opennessFor(SceneSpace.SceneVariant.TOWER, midnight), 0f)
        // At noon all are open; a house is open at every hour whatever any group says.
        val all = shopsOnly.copy(
            towerHoursEnabled = true, towerOpenHour = 9f, towerCloseHour = 18f,
            publicHoursEnabled = true, publicOpenHour = 9f, publicCloseHour = 18f,
        )
        for (b in Storefront.ALL) assertEquals(1f, all.opennessFor(b, 12f), 0f)
        assertEquals(1f, all.opennessFor(SceneSpace.SceneVariant.TOWER, 12f), 0f)
        for (v in houses) for (h in 0..23) assertEquals("$v at $h:00", 1f, all.opennessFor(v, h.toFloat()), 0f)
    }

    @Test
    fun `each group runs its own hours`() {
        val c = SceneCustomization.DEFAULT.copy(
            shopHoursEnabled = true, shopOpenHour = 8f, shopCloseHour = 13f,
            towerHoursEnabled = true, towerOpenHour = 14f, towerCloseHour = 22f,
            publicHoursEnabled = true, publicOpenHour = 11f, publicCloseHour = 16f,
        )
        assertEquals(1f, c.opennessFor(Storefront.BAR, 10f), 0f)
        assertEquals(0f, c.opennessFor(Storefront.SCHOOL, 10f), 0f)
        assertEquals(0f, c.opennessFor(SceneSpace.SceneVariant.TOWER, 10f), 0f)
        assertEquals(1f, c.opennessFor(Storefront.SCHOOL, 15f), 0f)
        assertEquals(0f, c.opennessFor(Storefront.SHOP, 15f), 0f)
        assertEquals(0f, c.opennessFor(Storefront.SCHOOL, 18f), 0f)
        assertEquals(1f, c.opennessFor(SceneSpace.SceneVariant.TOWER, 18f), 0f)
    }

    /**
     * **The fire station and the police station never close** (the maintainer, 2026-10-12: *«pompieri e
     * polizia sono sempre aperti, 24 ore su 24, tutti i giorni, perché nella realtà non chiudono mai.
     * L'interruttore e il "dalle / alle" degli edifici pubblici valgono solo per la scuola»*): at every hour,
     * with every group's hours on at any span, their glass is the open one and their people are in ([peopleFor]).
     */
    @Test
    fun `the fire and the police station are open at every hour, whatever the hours say`() {
        assertEquals(listOf(Storefront.SCHOOL), publics.filter { it.keepsHours })
        val spans = listOf(9f to 20f, 22f to 6f, 12f to 12.25f, 0f to 0.25f)
        for ((open, close) in spans) {
            val c = SceneCustomization.DEFAULT.copy(
                shopHoursEnabled = true, shopOpenHour = open, shopCloseHour = close,
                towerHoursEnabled = true, towerOpenHour = open, towerCloseHour = close,
                publicHoursEnabled = true, publicOpenHour = open, publicCloseHour = close,
            )
            var schoolShut = false
            for (q in 0 until 96) {
                val h = q / 4f
                assertEquals("fire station at $h, $open-$close", 1f, c.opennessFor(Storefront.FIRE_STATION, h), 0f)
                assertEquals("police station at $h, $open-$close", 1f, c.opennessFor(Storefront.POLICE_STATION, h), 0f)
                assertEquals("the fire station's people at $h, $open-$close", 7, c.peopleFor(Storefront.FIRE_STATION, 7, h))
                assertEquals("the police station's people at $h, $open-$close", 7, c.peopleFor(Storefront.POLICE_STATION, 7, h))
                if (c.opennessFor(Storefront.SCHOOL, h) < 1f) schoolShut = true
            }
            assertTrue("the school does keep the hours $open-$close", schoolShut)
        }
    }

    /**
     * **A shut building has nobody at its glass, and an open one all its people but in its last minute** (v5.14, the
     * hours to the minute): with each group's hours on, for every building that keeps hours -- the towers, the restaurant,
     * the bar, the shop, the school -- at every minute of the day, [peopleFor] gives none while [opennessFor] says shut,
     * all of them while it says open, and none in the minute before the closing, when they walk out; a house keeps all
     * its people whatever the hours. Held for the people what `BusinessHoursRenderTest` can no longer tell on a frame by
     * day, where the blinds and the shutter change it on their own.
     */
    @Test
    fun `a shut building's people are out, an open one's in but in its last minute`() {
        val spans = listOf(9f to 20f, 22f to 6f, 13f to 13.5f)
        for ((open, close) in spans) {
            val c = SceneCustomization.DEFAULT.copy(
                shopHoursEnabled = true, shopOpenHour = open, shopCloseHour = close,
                towerHoursEnabled = true, towerOpenHour = open, towerCloseHour = close,
                publicHoursEnabled = true, publicOpenHour = open, publicCloseHour = close,
            )
            val lastMinute = close - 1f / 60f
            for (m in 0 until 24 * 60) {
                val h = m / 60f
                val inLastMinute = Math.floorMod(Math.round((h - lastMinute) * 60f), 24 * 60) == 0
                for (b in Storefront.ALL.filter { it.keepsHours }) {
                    val shut = c.opennessFor(b, h) < 1f
                    val expected = if (shut || inLastMinute) 0 else 5
                    assertEquals("$b at minute $m, $open-$close", expected, c.peopleFor(b, 5, h))
                }
                val towerShut = c.opennessFor(SceneSpace.SceneVariant.TOWER, h) < 1f
                assertEquals("a tower at minute $m, $open-$close", if (towerShut || inLastMinute) 0 else 5, c.peopleFor(SceneSpace.SceneVariant.TOWER, 5, h))
                for (v in listOf(SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE)) {
                    assertEquals("$v at minute $m", 5, c.peopleFor(v, 5, h))
                }
            }
        }
    }

    @Test
    fun `with every switch off nothing depends on the hour, as before the hours existed`() {
        for (h in 0..23) {
            for (v in SceneSpace.SceneVariant.entries) assertEquals("$v at $h:00", 1f, SceneCustomization.DEFAULT.opennessFor(v, h.toFloat()), 0f)
            for (b in Storefront.ALL) assertEquals("$b at $h:00", 1f, SceneCustomization.DEFAULT.opennessFor(b, h.toFloat()), 0f)
        }
    }

    @Test
    fun `a change of any group's hours is a cut for the people at the hours, and nothing else is`() {
        val c = SceneCustomization.DEFAULT
        assertTrue(c.sameOpeningHours(c.copy(shops = c.shops.copy(colorDay1 = 1))))
        assertFalse(c.sameOpeningHours(c.copy(publicHoursEnabled = true)))
        assertFalse(c.sameOpeningHours(c.copy(publicOpenHour = 7f)))
        assertFalse(c.sameOpeningHours(c.copy(publicCloseHour = 21f)))
        assertFalse(c.sameOpeningHours(c.copy(shopHoursEnabled = true)))
        assertFalse(c.sameOpeningHours(c.copy(towerCloseHour = 21f)))
    }

    /**
     * **Shut at night, a business's glass is dark like a house's unlit window** (v5.12, the
     * maintainer's *«Scuri, come le case (consigliato)»* of 2026-10-09): open it is the lit window to
     * the bit, by day both are the day's glass, and over the glass's two seconds (the doorway's, v5.14) it goes from one to
     * the other.
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

    /** What a build from v4.22 to v5.11 wrote: the one setting, and nothing of the groups. */
    private fun writtenBefore512(enabled: Boolean, open: Float, close: Float): JSONObject {
        val json = JSONObject(SceneCustomization.DEFAULT.toJson().toString())
        for (key in listOf("shop", "tower", "public")) {
            json.remove("${key}HoursEnabled"); json.remove("${key}OpenHour"); json.remove("${key}CloseHour")
        }
        json.put("businessHoursEnabled", enabled)
        json.put("businessOpenHour", open.toDouble())
        json.put("businessCloseHour", close.toDouble())
        return json
    }

    /** What the 5.12 wrote: the shops' and the towers' hours, and the old setting as the shops'; no public buildings. */
    private fun writtenBy512(shop: Triple<Boolean, Float, Float>, tower: Triple<Boolean, Float, Float>): JSONObject {
        val json = JSONObject(SceneCustomization.DEFAULT.toJson().toString())
        json.remove("publicHoursEnabled"); json.remove("publicOpenHour"); json.remove("publicCloseHour")
        json.remove("publics")
        json.put("shopHoursEnabled", shop.first); json.put("shopOpenHour", shop.second.toDouble()); json.put("shopCloseHour", shop.third.toDouble())
        json.put("towerHoursEnabled", tower.first); json.put("towerOpenHour", tower.second.toDouble()); json.put("towerCloseHour", tower.third.toDouble())
        json.put("businessHoursEnabled", shop.first); json.put("businessOpenHour", shop.second.toDouble()); json.put("businessCloseHour", shop.third.toDouble())
        return json
    }

    @Test
    fun `a payload written before v5_12 with the hours on opens every group at its hours`() {
        val c = sceneCustomizationFromJson(writtenBefore512(true, 8.25f, 1.75f))
        for ((enabled, open, close) in listOf(
            Triple(c.shopHoursEnabled, c.shopOpenHour, c.shopCloseHour),
            Triple(c.towerHoursEnabled, c.towerOpenHour, c.towerCloseHour),
            Triple(c.publicHoursEnabled, c.publicOpenHour, c.publicCloseHour),
        )) {
            assertTrue(enabled)
            assertEquals(8.25f, open, 0f)
            assertEquals(1.75f, close, 0f)
        }
        // And off stays off in all, its hours kept for when a group is turned on.
        val off = sceneCustomizationFromJson(writtenBefore512(false, 7f, 21f))
        assertFalse(off.shopHoursEnabled)
        assertFalse(off.towerHoursEnabled)
        assertFalse(off.publicHoursEnabled)
        assertEquals(7f, off.towerOpenHour, 0f)
        assertEquals(21f, off.shopCloseHour, 0f)
        assertEquals(21f, off.publicCloseHour, 0f)
    }

    @Test
    fun `a payload written by the 5_12 opens the public buildings at the shops' hours, which the school kept`() {
        val on = sceneCustomizationFromJson(writtenBy512(Triple(true, 10.5f, 18.75f), Triple(false, 6f, 23f)))
        assertTrue("the shops' hours were on: the public buildings' are", on.publicHoursEnabled)
        assertEquals(10.5f, on.publicOpenHour, 0f)
        assertEquals(18.75f, on.publicCloseHour, 0f)
        assertTrue(on.shopHoursEnabled)
        assertEquals(10.5f, on.shopOpenHour, 0f)
        assertFalse("the towers' hours are their own", on.towerHoursEnabled)
        val off = sceneCustomizationFromJson(writtenBy512(Triple(false, 9f, 20f), Triple(true, 6f, 23f)))
        assertFalse("the shops' hours were off: the public buildings' are", off.publicHoursEnabled)
        assertTrue(off.towerHoursEnabled)
    }

    @Test
    fun `a payload written since keeps each group's own hours, whatever the old setting beside them says`() {
        val c = SceneCustomization.DEFAULT.copy(
            shopHoursEnabled = false, shopOpenHour = 10f, shopCloseHour = 16f,
            towerHoursEnabled = true, towerOpenHour = 6f, towerCloseHour = 23.5f,
            publicHoursEnabled = true, publicOpenHour = 8.5f, publicCloseHour = 14.25f,
        )
        val json = JSONObject(c.toJson().toString())
        val back = sceneCustomizationFromJson(json)
        assertEquals(c.shopHoursEnabled, back.shopHoursEnabled)
        assertEquals(c.shopOpenHour, back.shopOpenHour, 0f)
        assertEquals(c.shopCloseHour, back.shopCloseHour, 0f)
        assertEquals(c.towerHoursEnabled, back.towerHoursEnabled)
        assertEquals(c.towerOpenHour, back.towerOpenHour, 0f)
        assertEquals(c.towerCloseHour, back.towerCloseHour, 0f)
        assertEquals(c.publicHoursEnabled, back.publicHoursEnabled)
        assertEquals(c.publicOpenHour, back.publicOpenHour, 0f)
        assertEquals(c.publicCloseHour, back.publicCloseHour, 0f)
        // The old setting is still written, as the shops', for a build before v5.12 to read.
        assertEquals(false, json.getBoolean("businessHoursEnabled"))
        assertEquals(10.0, json.getDouble("businessOpenHour"), 0.0)
    }

    @Test
    fun `a saved theme and a backup written before v5_12 open every group at its hours`() {
        // A saved theme: the customization inside its entry.
        val entry = JSONObject().apply {
            put("id", "custom_1")
            put("name", "Mine")
            put("theme", ThemeCatalog.byId("autumn").toJson())
            put("layout", SceneObjectCatalog.layoutFor("autumn", 0).toJson())
            put("customization", writtenBefore512(true, 7.5f, 22f))
        }
        val saved = customThemeEntryFromJson(entry).customization
        assertTrue(saved.shopHoursEnabled && saved.towerHoursEnabled && saved.publicHoursEnabled)
        assertEquals(7.5f, saved.towerOpenHour, 0f)
        assertEquals(22f, saved.shopCloseHour, 0f)
        assertEquals(7.5f, saved.publicOpenHour, 0f)
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
        assertTrue(winter.shopHoursEnabled && winter.towerHoursEnabled && winter.publicHoursEnabled)
        assertEquals(9.5f, winter.shopOpenHour, 0f)
        assertEquals(19.25f, winter.towerCloseHour, 0f)
        assertEquals(19.25f, winter.publicCloseHour, 0f)
    }
}
