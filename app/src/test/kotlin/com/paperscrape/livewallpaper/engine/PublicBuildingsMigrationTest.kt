package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.prefs.AppBackup
import com.paperscrape.livewallpaper.prefs.BackupParseResult
import com.paperscrape.livewallpaper.prefs.ThemeParseResult
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.prefs.parseAppBackup
import com.paperscrape.livewallpaper.prefs.parseThemeShare
import com.paperscrape.livewallpaper.prefs.toJsonString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The public buildings' colours of a payload written before v5.13** -- a saved theme, a theme file, a
 * backup's theme or archive: whoever had changed the shops' colours by hand finds them on the school and
 * the two stations too, and whoever had not gets the new pair of the theme the payload belongs to (the
 * maintainer's *«il resto accetto i consigliati»* of 2026-10-11, inventory I-615). The rule is
 * [publicsCarriedOver]; the theme is the one the payload carries, or the catalogue's for its id. The
 * live edit and the archives in the preference store are `PublicBuildingsPreferenceStoreTest`'s.
 */
class PublicBuildingsMigrationTest {

    private val mine = intArrayOf(0xFF8A2BE2.toInt(), 0xFF2E1048.toInt(), 0xFF20B2AA.toInt(), 0xFF0B3C39.toInt())

    private fun colours(c: ObjectVariantConfig) = listOf(c.colorDay1, c.colorNight1, c.colorDay2, c.colorNight2)

    /** [c] as the 5.12 wrote it: no public buildings, their colours or their hours. */
    private fun writtenBy512(c: SceneCustomization): JSONObject = JSONObject(c.toJson().toString()).apply {
        remove("publics"); remove("publicHoursEnabled"); remove("publicOpenHour"); remove("publicCloseHour")
    }

    private fun withMyShops(c: SceneCustomization) =
        c.copy(shops = c.shops.copy(colorDay1 = mine[0], colorNight1 = mine[1], colorDay2 = mine[2], colorNight2 = mine[3]))

    @Test
    fun `the rule - shops changed by hand are carried, shops left as they started give the theme's new pair`() {
        for (theme in ThemeCatalog.ALL) {
            val d = defaultCustomizationFor(theme.id)
            assertEquals(colours(d.publics), colours(publicsCarriedOver(d.shops, theme)))
            assertEquals(mine.toList(), colours(publicsCarriedOver(withMyShops(d).shops, theme)))
            // One automatic mode changed is a change too.
            val moded = d.shops.copy(autoMode2 = AutoColorMode.FROM_DAY)
            assertEquals(moded, publicsCarriedOver(moded, theme))
        }
        // No theme to compare against: the shops', the look the school had.
        val d = defaultCustomizationFor("autumn")
        assertEquals(d.shops, publicsCarriedOver(d.shops, null as SceneTheme?))
    }

    @Test
    fun `a saved theme written by the 5_12 reads its public buildings against the theme it carries`() {
        fun entry(c: SceneCustomization) = JSONObject().apply {
            put("id", "custom:mine")
            put("name", "Mine")
            // A copy of Autumn under its own id: its sky and hills are Autumn's.
            put("theme", ThemeCatalog.byId("autumn").copy(id = "custom:mine", displayName = "Mine").toJson())
            put("layout", SceneObjectCatalog.layoutFor("autumn", 0).toJson())
            put("customization", writtenBy512(c))
        }
        val autumn = defaultCustomizationFor("autumn")
        val untouched = customThemeEntryFromJson(entry(autumn)).customization
        assertEquals("a copy of Autumn starts from Autumn's pair", colours(autumn.publics), colours(untouched.publics))
        val changed = customThemeEntryFromJson(entry(withMyShops(autumn))).customization
        assertEquals(mine.toList(), colours(changed.publics))
    }

    @Test
    fun `a theme file written by the 5_12 reads its public buildings against its theme`() {
        fun file(c: SceneCustomization) = JSONObject().apply {
            put("kind", com.paperscrape.livewallpaper.prefs.ThemeShare.DOCUMENT_KIND)
            put("schemaVersion", com.paperscrape.livewallpaper.prefs.ThemeShare.SCHEMA_VERSION)
            put("name", "Shared winter")
            put("sourceThemeId", "winter")
            put("theme", ThemeCatalog.byId("winter").toJson())
            put("layout", SceneObjectCatalog.layoutFor("winter", 0).toJson())
            put("customization", writtenBy512(c))
        }.toString()
        val winter = defaultCustomizationFor("winter")
        val untouched = parseThemeShare(file(winter))
        assertTrue("read: $untouched", untouched is ThemeParseResult.Ok)
        assertEquals(colours(winter.publics), colours((untouched as ThemeParseResult.Ok).share.customization.publics))
        val changed = parseThemeShare(file(withMyShops(winter))) as ThemeParseResult.Ok
        assertEquals(mine.toList(), colours(changed.share.customization.publics))
    }

    @Test
    fun `a backup written by the 5_12 reads each theme's public buildings against that theme`() {
        val backup = JSONObject(
            AppBackup.from(
                WallpaperSettings(themeCustomizations = mapOf("beach" to SceneCustomization.DEFAULT, "easter" to SceneCustomization.DEFAULT)),
                CustomThemeData(), appVersionName = "5.12", nowMillis = 0L,
            ).toJsonString(),
        )
        backup.getJSONObject("themeCustomizations").put("beach", writtenBy512(defaultCustomizationFor("beach")))
        backup.getJSONObject("themeCustomizations").put("easter", writtenBy512(withMyShops(defaultCustomizationFor("easter"))))
        val parsed = parseAppBackup(backup.toString())
        assertTrue("read: $parsed", parsed is BackupParseResult.Ok)
        val read = (parsed as BackupParseResult.Ok).backup.themeCustomizations
        assertEquals(colours(defaultCustomizationFor("beach").publics), colours(read.getValue("beach").publics))
        assertEquals(mine.toList(), colours(read.getValue("easter").publics))
        assertNotEquals(colours(read.getValue("beach").shops), colours(read.getValue("beach").publics))
    }

    @Test
    fun `a payload written since keeps its own public buildings, whatever the shops beside them`() {
        val c = defaultCustomizationFor("spring").let { it.copy(publics = it.publics.copy(colorDay2 = 0xFF123456.toInt())) }
        val back = sceneCustomizationFromJson(JSONObject(c.toJson().toString()), ThemeCatalog.byId("spring"))
        assertEquals(c.publics, back.publics)
        assertEquals(c.shops, back.shops)
    }
}
