package com.paperscrape.livewallpaper.update

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **A configuration change during an update's download does not end the download.**
 *
 * The settings Activity handles itself the configuration changes a user makes with the phone in hand -- turning it,
 * dark mode, the font's size, and the font's weight, *Bold text*, a change of its own since Android 12
 * (`fontWeightAdjustment`) -- and the ones the phone makes around them: a keyboard, a mouse or a touch screen joined
 * or taken away, another colour range of the screen, a SIM card, the grammatical gender chosen in the phone's
 * settings. So none of them makes it again and cancels the download running in its scope (`UpdateDownloadLifetimeTest`).
 * One the list does not claim recreates the Activity, cancels the download and, for a screen opened from the update
 * notification, asks GitHub again. Reads the manifest, as `UpdateDownloadLifetimeTest` does.
 */
class SettingsActivityConfigChangesTest {

    @Test
    fun `the settings activity handles Bold text itself`() {
        val handled = handledChanges()
        assertTrue("SettingsActivity must handle 'fontWeightAdjustment'; declared: $handled", "fontWeightAdjustment" in handled)
        // And the font's size, so the two font settings are handled alike.
        assertTrue("fontScale" in handled)
    }

    /**
     * The changes the phone makes while the user is not touching the screen's settings. Each is named with the API
     * level from which Android sends it to an Activity (`ActivityInfo.CONFIG_*`); below that level the flag is read
     * and ignored, and the build accepts it because the compile SDK names it.
     */
    @Test
    fun `the settings activity handles the changes the phone makes around the user`() {
        val handled = handledChanges()
        val sinceApi = mapOf(
            "keyboard" to 1,
            "navigation" to 1,
            "touchscreen" to 1,
            "mcc" to 1,
            "mnc" to 1,
            "colorMode" to 26,
            "grammaticalGender" to 34,
            "assetsPaths" to 36,
        )
        for ((change, api) in sinceApi) {
            assertTrue(
                "SettingsActivity must handle '$change' itself (sent from API $api), or a download dies when it " +
                    "happens; declared: $handled",
                change in handled,
            )
        }
    }

    /** The flags of `android:configChanges` on `.ui.SettingsActivity`. */
    private fun handledChanges(): Set<String> {
        val manifest = walkUp("src/main/AndroidManifest.xml").readText()
        val declaration = manifest.substring(manifest.indexOf("android:name=\".ui.SettingsActivity\"")).substringBefore(">")
        return Regex("""android:configChanges="([^"]*)"""").find(declaration)?.groupValues?.get(1).orEmpty()
            .split("|").map { it.trim() }.toSet()
    }

    private fun walkUp(relative: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$relative")
                if (f.isFile) return f
            }
            dir = dir.parentFile
        }
        error("$relative not found")
    }
}
