package com.paperscrape.livewallpaper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ***Notify me about new versions* is on only if a notification can really arrive** (v5.10C, row 1
 * of the maintainer's table of 2026-09-30, inventory I-201) -- his rule of 2026-09-29: *«l'utente
 * normale non legge, vede il toggle e si arrabbia perché non funziona»*.
 *
 * Asserted at API **29** (this project's phone, no notification permission), **33** (the first with
 * one), **34** and **37**, in the four branches the round was asked for: the permission granted, the
 * dialog refused, notifications blocked in the phone's settings, and PaperScrape not the wallpaper.
 * **Evidence about the decision, not about a phone**, as `UpdateNotificationPolicyTest` says of the
 * same object: the branches of Android 13+ cannot be reached on the BV6600, and the phone's own
 * settings are not touched by these rounds.
 */
class NotifySwitchTruthTest {

    private val levels = listOf(29, 33, 34, 37)

    private fun row(
        sdk: Int,
        granted: Boolean = true,
        appNotificationsEnabled: Boolean = true,
        channelTurnedOff: Boolean = false,
        notifySwitchOn: Boolean = true,
        automaticCheckEnabled: Boolean = true,
        isTheWallpaper: Boolean = true,
        requestRefused: Boolean = false,
    ) = UpdateNotificationPolicy.notifyRow(
        automaticCheckEnabled = automaticCheckEnabled,
        notifySwitchOn = notifySwitchOn,
        permission = UpdateNotificationPolicy.permissionFor(sdk, granted),
        appNotificationsEnabled = appNotificationsEnabled,
        channelTurnedOff = channelTurnedOff,
        isTheWallpaper = isTheWallpaper,
        requestRefused = requestRefused,
    )

    @Test
    fun `granted, the wallpaper, both switches on - on at every level, and a tap turns it off`() {
        for (sdk in levels) {
            val r = row(sdk)
            assertTrue("API $sdk", r.shownOn)
            assertEquals(UpdateNotificationPolicy.NotifyRowLine.DESCRIPTION, r.line)
            assertEquals(UpdateNotificationPolicy.NotifyTap.TURN_OFF, r.tap)
        }
    }

    @Test
    fun `the dialog refused - off, said, and a tap asks again`() {
        for (sdk in levels.filter { it >= UpdateNotificationPolicy.RUNTIME_PERMISSION_SDK }) {
            // A refusal leaves the switch saved off (the row asks before it stores "on"), and on 33+
            // the phone then reports notifications disabled.
            val r = row(sdk, granted = false, appNotificationsEnabled = false, notifySwitchOn = false, requestRefused = true)
            assertFalse("API $sdk", r.shownOn)
            assertEquals("API $sdk", UpdateNotificationPolicy.NotifyRowLine.REFUSED, r.line)
            assertEquals("API $sdk", UpdateNotificationPolicy.NotifyTap.ASK_PERMISSION, r.tap)
        }
        // API 29 has no dialog to refuse: nothing to ask, and it is never asked.
        assertFalse(UpdateNotificationPolicy.mustAsk(UpdateNotificationPolicy.permissionFor(29, granted = false)))
    }

    @Test
    fun `blocked in the phone's settings - off, said, and a tap goes to the phone's page or asks`() {
        for (sdk in levels) {
            // The switch saved on, the user then switching PaperScrape's notifications off in the
            // phone's settings: below 33 the permission stays NOT_REQUIRED and the app's switch is off;
            // from 33 the permission is taken away with it.
            val r = row(sdk, granted = false, appNotificationsEnabled = false, notifySwitchOn = true)
            assertFalse("API $sdk: on while the phone blocks it", r.shownOn)
            if (sdk < UpdateNotificationPolicy.RUNTIME_PERMISSION_SDK) {
                assertEquals("API $sdk", UpdateNotificationPolicy.NotifyRowLine.BLOCKED, r.line)
                assertEquals("API $sdk", UpdateNotificationPolicy.NotifyTap.OPEN_APP_NOTIFICATIONS, r.tap)
            } else {
                assertEquals("API $sdk", UpdateNotificationPolicy.NotifyRowLine.BLOCKED_ASK, r.line)
                assertEquals("API $sdk", UpdateNotificationPolicy.NotifyTap.ASK_PERMISSION, r.tap)
            }
            // Only the *Update available* channel turned off: its own page, at every level.
            val channel = row(sdk, channelTurnedOff = true)
            assertFalse("API $sdk: on with its channel off", channel.shownOn)
            assertEquals(UpdateNotificationPolicy.NotifyRowLine.BLOCKED, channel.line)
            assertEquals(UpdateNotificationPolicy.NotifyTap.OPEN_CHANNEL, channel.tap)
        }
    }

    @Test
    fun `not the wallpaper - off, said, and a tap sets it`() {
        for (sdk in levels) {
            val r = row(sdk, isTheWallpaper = false)
            assertFalse("API $sdk: on with another wallpaper, where the check never runs", r.shownOn)
            assertEquals("API $sdk", UpdateNotificationPolicy.NotifyRowLine.NOT_THE_WALLPAPER, r.line)
            assertEquals("API $sdk", UpdateNotificationPolicy.NotifyTap.SET_AS_WALLPAPER, r.tap)
        }
        // Not the wallpaper and the permission never asked: the line names the wallpaper, and the
        // tap asks first (granted, the screen carries on to the wallpaper -- see AdvancedScreen).
        val both = row(34, granted = false, appNotificationsEnabled = false, notifySwitchOn = false, isTheWallpaper = false)
        assertEquals(UpdateNotificationPolicy.NotifyRowLine.NOT_THE_WALLPAPER, both.line)
        assertEquals(UpdateNotificationPolicy.NotifyTap.ASK_PERMISSION, both.tap)
    }

    @Test
    fun `on only when everything holds - every combination at every level`() {
        val bools = listOf(true, false)
        for (sdk in levels) for (granted in bools) for (app in bools) for (channel in bools) for (saved in bools)
            for (check in bools) for (wallpaper in bools) for (refused in bools) {
                val r = row(sdk, granted, app, channel, saved, check, wallpaper, refused)
                val permission = UpdateNotificationPolicy.permissionFor(sdk, granted)
                val canArrive = check && saved && wallpaper && !channel && app &&
                    UpdateNotificationPolicy.mayPost(permission)
                val label = "API $sdk granted=$granted app=$app channel-off=$channel saved=$saved check=$check wallpaper=$wallpaper refused=$refused"
                assertEquals(label, canArrive, r.shownOn)
                // A switch drawn off is never one a tap leaves as it is, unless the row is greyed.
                if (!r.shownOn && r.enabled) {
                    assertFalse(label, r.tap == UpdateNotificationPolicy.NotifyTap.TURN_OFF || r.tap == UpdateNotificationPolicy.NotifyTap.NOTHING)
                }
                assertEquals(label, check, r.enabled)
            }
    }

    // ------------------------------------------------------------------ the wiring

    @Test
    fun `the screen stores on only after a grant, and routes every other tap`() {
        val screen = source("ui/AdvancedScreen.kt")
        val ask = screen.substring(screen.indexOf("UpdateNotificationPolicy.NotifyTap.ASK_PERMISSION ->"))
            .substringBefore("UpdateNotificationPolicy.NotifyTap.OPEN_APP_NOTIFICATIONS ->")
        val grantedBranch = ask.substringAfter("granted -> {").substringBefore("!canAskAgain")
        assertTrue("granted stores on", grantedBranch.contains("prefs.setUpdateNotificationsEnabled(true)"))
        assertTrue("granted and not the wallpaper carries on to it", grantedBranch.contains("if (!isTheWallpaper) onApplyWallpaper()"))
        assertTrue(
            "a refusal the system will not ask again goes to the page -- after one already seen, or with the switch saved on",
            ask.contains("!canAskAgain && pageAfterRefusal -> {") &&
                ask.contains("val pageAfterRefusal = permissionRefused || settings.updateNotificationsEnabled"),
        )
        assertFalse("the ask path must not store before the answer", ask.substringBefore("onRequestNotificationPermission").contains("setUpdateNotificationsEnabled"))
        for (tap in UpdateNotificationPolicy.NotifyTap.entries) {
            assertTrue("the screen does not handle $tap", screen.contains("UpdateNotificationPolicy.NotifyTap.${tap.name} ->"))
        }
        assertTrue(screen.contains("UpdateNotifier.appNotificationSettingsIntent(context)"))
        assertTrue(screen.contains("UpdateNotifier.channelSettingsIntent(context)"))
        assertTrue("PaperScrape-is-the-wallpaper must come from the engines", screen.contains("WallpaperEngineCensus.isTheWallpaper.collectAsState()"))
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
