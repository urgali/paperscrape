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
 * one), **34** and **37**, in the branches the round was asked for: the permission granted, the dialog
 * refused, notifications blocked in the phone's settings. **Since v5.15 whether PaperScrape is the
 * wallpaper is not one of them**: the phone's job scheduler checks with any wallpaper
 * (`UpdateCheckJobService`, the maintainer's *«S sì»* of 2026-10-19), so the row that read "Works only
 * while PaperScrape is your wallpaper" from v5.10C to v5.14 would now say something false, and it is gone.
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
        requestRefused: Boolean = false,
    ) = UpdateNotificationPolicy.notifyRow(
        automaticCheckEnabled = automaticCheckEnabled,
        notifySwitchOn = notifySwitchOn,
        permission = UpdateNotificationPolicy.permissionFor(sdk, granted),
        appNotificationsEnabled = appNotificationsEnabled,
        channelTurnedOff = channelTurnedOff,
        requestRefused = requestRefused,
    )

    @Test
    fun `granted and both switches on - on at every level, whatever the wallpaper, and a tap turns it off`() {
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
    fun `the wallpaper is no condition any more - no line, no tap and no string for it`() {
        // v5.15: the job of the phone's scheduler checks with any wallpaper, so nothing about the row may depend on it.
        assertFalse(UpdateNotificationPolicy.NotifyRowLine.entries.any { "WALLPAPER" in it.name })
        assertFalse(UpdateNotificationPolicy.NotifyTap.entries.any { "WALLPAPER" in it.name })
        val screen = source("ui/AdvancedScreen.kt")
        val updates = screen.substring(screen.indexOf("SettingsSectionHeader(\"Updates\")"))
            .substringBefore("SettingsSectionHeader(\"Backup\")")
        assertFalse("the updates' rows read the wallpaper", updates.contains("isTheWallpaper"))
        assertFalse("the updates' rows put up the wallpaper's preview", updates.contains("onApplyWallpaper"))
        val strings = resource("res/values/strings.xml")
        assertFalse("a line still says the check needs the wallpaper", strings.contains("Works only while PaperScrape is your wallpaper"))
        assertFalse(strings.contains("settings_update_notify_not_wallpaper"))
    }

    @Test
    fun `on only when everything holds - every combination at every level`() {
        val bools = listOf(true, false)
        for (sdk in levels) for (granted in bools) for (app in bools) for (channel in bools) for (saved in bools)
            for (check in bools) for (refused in bools) {
                val r = row(sdk, granted, app, channel, saved, check, refused)
                val permission = UpdateNotificationPolicy.permissionFor(sdk, granted)
                val canArrive = check && saved && !channel && app &&
                    UpdateNotificationPolicy.mayPost(permission)
                val label = "API $sdk granted=$granted app=$app channel-off=$channel saved=$saved check=$check refused=$refused"
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
        val grantedBranch = ask.substringAfter("granted ->").substringBefore("!canAskAgain")
        assertTrue("granted stores on", grantedBranch.contains("storeNotify(true)"))
        // Storing the switch puts the update job where the two switches say, and checks at once when it comes on (v5.15).
        val store = screen.substring(screen.indexOf("fun storeNotify(on: Boolean)")).substringBefore("SettingsSwitchRow(")
        assertTrue(store.contains("prefs.setUpdateNotificationsEnabled(on)"))
        assertTrue(store.contains("UpdateCheckJobs.reconcile(context, userTurnedOn = on)"))
        assertTrue(
            "a refusal the system will not ask again goes to the page -- after one already seen, or with the switch saved on",
            ask.contains("!canAskAgain && pageAfterRefusal -> {") &&
                ask.contains("val pageAfterRefusal = permissionRefused || settings.updateNotificationsEnabled"),
        )
        // Stored through `storeNotify` since v5.15, so both spellings of a store are looked for (v5.15C2, inventory I-975: the
        // check read only the one no line of the ask path uses any more, and passed a `storeNotify(true)` put before the ask).
        val beforeTheAsk = ask.substringBefore("onRequestNotificationPermission")
        assertTrue("the ask path was not found", ask.contains("onRequestNotificationPermission { granted, canAskAgain ->"))
        assertFalse("the ask path must not store before the answer", beforeTheAsk.contains("setUpdateNotificationsEnabled"))
        assertFalse("the ask path must not store before the answer", beforeTheAsk.contains("storeNotify("))
        for (tap in UpdateNotificationPolicy.NotifyTap.entries) {
            assertTrue("the screen does not handle $tap", screen.contains("UpdateNotificationPolicy.NotifyTap.${tap.name} ->"))
        }
        assertTrue(screen.contains("UpdateNotifier.appNotificationSettingsIntent(context)"))
        assertTrue(screen.contains("UpdateNotifier.channelSettingsIntent(context)"))
    }

    private fun source(path: String): String = resource("kotlin/com/paperscrape/livewallpaper/$path")

    private fun resource(path: String): String {
        val suffix = "src/main/$path"
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
