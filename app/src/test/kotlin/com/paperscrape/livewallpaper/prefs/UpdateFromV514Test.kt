package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.update.NotificationPermission
import com.paperscrape.livewallpaper.update.UpdateCheckSchedule
import com.paperscrape.livewallpaper.update.UpdateNotificationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Who updates from the 5.14** (v5.15): the files a 5.14 user has, written by the 5.14's own code -- a backup, the
 * saved-theme store, a shared theme (`resources/update/`, written on 2026-10-19 by a probe run in the published 5.14's
 * tree, `ScrittiDalla514Probe`, kept outside the repository) -- read by this release.
 *
 * Their settings are the maintainer's way of using the wallpaper -- Autumn chosen by hand with the calendar off, a place
 * of his own, Live Weather off -- and what this round touches: both update switches on, *Santa* and *Halloween* on in
 * Autumn, *Halloween* on in a theme of the user's own. The 5.15 adds no setting a backup carries and changes no stored
 * one (the moment GitHub's limit lifts is kept in the updater's own store, which no backup carries): every value comes
 * back as written, and what changes is what the same values do -- the check becomes the phone scheduler's job, wanted by
 * the same two switches, and *Notify me* reads on without PaperScrape having to be the wallpaper; the same *Santa* draws
 * the new sleigh and the same *Halloween* the moon with its face lit.
 */
class UpdateFromV514Test {

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("update/$name")) { "update/$name" }.readText()

    @Test
    fun `a backup the 5_14 wrote restores every setting it carried`() {
        val parsed = parseAppBackup(resource("backup-written-by-v5.14.json"))
        assertTrue("$parsed", parsed is BackupParseResult.Ok)
        val backup = (parsed as BackupParseResult.Ok).backup
        val s = backup.settings
        assertEquals("autumn", s.themeId)
        assertFalse(s.autoThemeByDate)
        assertTrue(s.syncWithRealTime)
        assertTrue(s.useCustomLocation)
        assertFalse(s.liveWeatherEnabled)
        assertTrue(s.automaticUpdateCheckEnabled)
        assertTrue(s.updateNotificationsEnabled)
        val autumn = backup.themeCustomizations.getValue("autumn")
        assertTrue(autumn.santaEnabled)
        assertTrue(autumn.halloweenEnabled)
        assertEquals(listOf("custom:1"), backup.customThemeData.customThemes.map { it.id })
        assertTrue(backup.customThemeData.customThemes.single().customization.halloweenEnabled)
        // Every one of the settings the 5.14 wrote comes back as written: the backup this release writes from what it
        // read carries each key with the same value.
        val written = org.json.JSONObject(resource("backup-written-by-v5.14.json")).getJSONObject("settings")
        val again = org.json.JSONObject(backup.toJsonString()).getJSONObject("settings")
        assertEquals("the settings the 5.14 wrote", 21, written.length())
        for (key in written.keys()) {
            assertTrue("$key is not written back", again.has(key))
            val a = written.get(key)
            val b = again.get(key)
            if (a is Number && b is Number) assertEquals(key, a.toDouble(), b.toDouble(), 1e-6) else assertEquals(key, a.toString(), b.toString())
        }
    }

    @Test
    fun `the same two switches now want the scheduler's job, and Notify me reads on whatever the wallpaper`() {
        val s = (parseAppBackup(resource("backup-written-by-v5.14.json")) as BackupParseResult.Ok).backup.settings
        assertTrue(UpdateCheckSchedule.wanted(s.automaticUpdateCheckEnabled, s.updateNotificationsEnabled))
        // Until v5.15 this row read off, "Works only while PaperScrape is your wallpaper", for a user with another one.
        val row = UpdateNotificationPolicy.notifyRow(
            automaticCheckEnabled = s.automaticUpdateCheckEnabled,
            notifySwitchOn = s.updateNotificationsEnabled,
            permission = NotificationPermission.NOT_REQUIRED,
            appNotificationsEnabled = true,
            channelTurnedOff = false,
            requestRefused = false,
        )
        assertTrue(row.shownOn)
        assertEquals(UpdateNotificationPolicy.NotifyRowLine.DESCRIPTION, row.line)
    }

    @Test
    fun `the store the 5_14 saved reads back with its Santa and its Halloween`() {
        val store = customThemeDataFromJsonString(resource("store-saved-by-v5.14.json"))
        val autumn = store.overrides.getValue("autumn").customization
        assertTrue(autumn.santaEnabled && autumn.halloweenEnabled)
        val mine = store.customThemes.single { it.id == "custom:1" }
        assertEquals("My Halloween", mine.name)
        assertTrue(mine.customization.halloweenEnabled)
        assertFalse(mine.customization.santaEnabled)
    }

    @Test
    fun `a theme the 5_14 shared imports with its Santa and its Halloween`() {
        val parsed = parseThemeShare(resource("theme-shared-by-v5.14.json"))
        assertTrue("$parsed", parsed is ThemeParseResult.Ok)
        val c = (parsed as ThemeParseResult.Ok).share.customization
        assertTrue(c.santaEnabled)
        assertTrue(c.halloweenEnabled)
    }
}
