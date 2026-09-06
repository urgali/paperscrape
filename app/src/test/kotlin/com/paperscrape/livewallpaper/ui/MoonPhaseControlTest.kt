package com.paperscrape.livewallpaper.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Halloween overrides the moon-phase control; it does not overwrite the user's preference.**
 *
 * ### The behaviour this is about, and where it already lived
 *
 * `PaperRenderer.drawMoonWithPhase` blits `moon_jack_o_lantern`, always full, and returns before
 * it ever reads `moon.realisticPhases` while `halloweenEnabled` is on. That is correct and carries
 * its own derivation in the renderer: a carved face that waxed and waned would be a lit fraction
 * of a grin, which reads as a rendering fault rather than as a decoration. **Nothing here changes
 * that.** What v4.23 changed is the settings row, which stayed live and clickable while the
 * renderer ignored it -- and a control that moves without effect is worse than an absent one,
 * because it teaches the user that the setting does not work.
 *
 * ### Two halves, and why neither proves the invariant alone
 *
 * The claim has two parts, and they fail in different places.
 *
 * **The rule** is [SettingsUiModel.moonPhases]: shown off and locked while Halloween is on, the
 * stored value otherwise. It is a pure function of two booleans, so it is asserted directly --
 * every one of the four combinations, plus the property that matters most, which is that the
 * *stored* value is never what decides interactivity.
 *
 * **The wiring** cannot be reached that way. This project has no Compose UI test infrastructure
 * (there is not one `createComposeRule` in the tree), so there is no way to tap a switch and read
 * a DataStore back on the JVM; and an instrumented test that did would prove the row is locked
 * without proving *nothing writes*, which is the half that destroys data when it is wrong. So the
 * wiring is pinned by reading the source, the way `BusinessHoursWiringTest` pins the two business
 * hours call sites and `SkyscraperWindowTest` pins the window-colour coupling -- both for the same
 * reason: the property is about *which call sites exist*, and no rendered frame or round-tripped
 * preference can say that a second one does not.
 *
 * **What the source half actually proves.** That the row reads its `checked` and `enabled` from
 * the derivation rather than from the raw flag; that both inputs come from the same resolved
 * `customization`, which is what makes the override per-theme (Halloween and the phase preference
 * are both per-theme settings, so an override read from anywhere else would leak across themes);
 * and -- the load-bearing one -- that `setMoonRealisticPhases` has **exactly one caller in the
 * whole of `src/main`**, the switch's own `onCheckedChange`, and that `setHalloweenEnabled` writes
 * only its own key. Together those say the user's stored value cannot be reached by the Halloween
 * path at all, which is the only way "the preference survives" can be proved rather than observed
 * once.
 *
 * The rule it comes from is written down already, in `PeopleDensity.resolveNightDensity`: any
 * default that silently changes what an existing user had set up "is not something a settings
 * refactor is entitled to do". Writing `false` into the preference to make the switch look right
 * would be exactly that, and it is the reason condition U exists in this pass's brief.
 */
class MoonPhaseControlTest {

    // ------------------------------------------------------------------ the rule

    @Test
    fun `with Halloween off the control is the stored value, live`() {
        for (stored in listOf(true, false)) {
            val state = SettingsUiModel.moonPhases(storedRealisticPhases = stored, halloweenEnabled = false)
            assertEquals("the switch must show what is stored", stored, state.shownOn)
            assertTrue("the switch must accept a tap", state.interactive)
            assertFalse("nothing is overriding it", state.overriddenByHalloween)
        }
    }

    @Test
    fun `with Halloween on the control is off and locked, whatever is stored`() {
        for (stored in listOf(true, false)) {
            val state = SettingsUiModel.moonPhases(storedRealisticPhases = stored, halloweenEnabled = true)
            assertFalse("the carved moon is always full, so the switch shows off", state.shownOn)
            assertFalse("and must not accept a tap that would do nothing", state.interactive)
            assertTrue("the row owes the user the reason", state.overriddenByHalloween)
        }
    }

    /**
     * The override is decided by Halloween alone.
     *
     * Stated separately from the two cases above because it is the property a future edit is most
     * likely to break: making interactivity depend on the stored value as well would produce a
     * switch that is locked *on* for a user who had phases enabled, which looks reasonable and is
     * the same dead end v3.0's Live Weather switch was in ([LiveWeatherUiState.switchIsInteractive]
     * carries that history).
     */
    @Test
    fun `only Halloween decides whether the control is locked`() {
        assertEquals(
            SettingsUiModel.moonPhases(storedRealisticPhases = true, halloweenEnabled = true).interactive,
            SettingsUiModel.moonPhases(storedRealisticPhases = false, halloweenEnabled = true).interactive,
        )
        assertEquals(
            SettingsUiModel.moonPhases(storedRealisticPhases = true, halloweenEnabled = false).interactive,
            SettingsUiModel.moonPhases(storedRealisticPhases = false, halloweenEnabled = false).interactive,
        )
    }

    /**
     * The stored value comes back the moment Halloween goes off -- the whole point of overriding
     * rather than writing.
     *
     * On its own this is a statement about a pure function and would be weak; it is worth having
     * because it is the *shape* of the claim the source assertions below then make true of the
     * real app: the value that comes back is the one that was there, because nothing wrote to it.
     */
    @Test
    fun `turning Halloween off restores exactly what was stored`() {
        for (stored in listOf(true, false)) {
            assertTrue(SettingsUiModel.moonPhases(stored, halloweenEnabled = true).overriddenByHalloween)
            assertEquals(
                "the preference is overridden, never replaced",
                stored,
                SettingsUiModel.moonPhases(stored, halloweenEnabled = false).shownOn,
            )
        }
    }

    // ------------------------------------------------------------------ the wiring

    @Test
    fun `the row reads its state from the derivation, not from the raw flag`() {
        val body = sunMoonSubScreenSource()
        assertTrue(
            "the screen must build the state through SettingsUiModel.moonPhases",
            body.contains("SettingsUiModel.moonPhases("),
        )
        assertTrue("checked must come from the derivation", body.contains("checked = moonPhases.shownOn"))
        assertTrue("enabled must come from the derivation", body.contains("enabled = moonPhases.interactive"))
        assertFalse(
            "the raw flag must not reach the switch any more -- that is the control that lied",
            body.contains("checked = customization.moon.realisticPhases"),
        )
    }

    /**
     * Both inputs come from the same resolved `customization`, which is what makes the override
     * per-theme. Halloween and the phase preference are both per-theme settings; an override that
     * read either from a global would show one theme's Halloween locking another theme's moon.
     */
    @Test
    fun `the override and the value it overrides are read from the same theme's customization`() {
        val body = sunMoonSubScreenSource()
        assertTrue(
            body.contains("storedRealisticPhases = customization.moon.realisticPhases"),
        )
        assertTrue(
            body.contains("halloweenEnabled = customization.halloweenEnabled"),
        )
    }

    /**
     * **The preference has exactly one writer, and it is the switch itself.**
     *
     * This is the assertion that stands for "the stored value survives". A second call site --
     * a `LaunchedEffect` that tidied the preference when Halloween came on, say, which is the
     * obvious and wrong repair -- would be invisible to every other test in this suite and would
     * destroy the user's setting on the first frame of the settings screen.
     */
    @Test
    fun `nothing but the switch ever writes the moon-phase preference`() {
        val callers = mainSources()
            .filter { it.name != "WallpaperPrefs.kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> line.contains("setMoonRealisticPhases(") }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }
        assertEquals(
            "the moon-phase preference must have exactly one writer outside WallpaperPrefs, and " +
                "it must be the switch's own onCheckedChange: $callers",
            1,
            callers.size,
        )
        assertTrue(
            "the one writer must be the switch's own callback: ${callers.first()}",
            callers.first().startsWith("WorldSceneScreen.kt:") &&
                callers.first().contains("onCheckedChange = { scope.launch { prefs.setMoonRealisticPhases(it, forThemeId) } }"),
        )
    }

    /** Turning Halloween on writes Halloween's own key and nothing else. */
    @Test
    fun `setting Halloween touches no other preference`() {
        val setter = prefsSource().readText()
            .substringAfter("suspend fun setHalloweenEnabled(")
            .substringBefore("/** The horror sky")
        assertTrue("it must write its own key", setter.contains("it[Keys.HALLOWEEN_ENABLED] = enabled"))
        val keysWritten = Regex("""it\[Keys\.([A-Z_0-9]+)]""").findAll(setter).map { it.groupValues[1] }.toSet()
        assertEquals(
            "setHalloweenEnabled must write only its own key and the pending-theme marker",
            setOf("HALLOWEEN_ENABLED", "PENDING_CUSTOMIZATION_THEME_ID"),
            keysWritten,
        )
    }

    // ------------------------------------------------------------------ source plumbing

    private fun sunMoonSubScreenSource(): String =
        uiSource("WorldSceneScreen.kt").readText()
            .substringAfter("private fun SunMoonSubScreen(")
            .substringBefore("\nprivate fun ")

    private fun prefsSource(): File =
        moduleRoot().resolve("src/main/kotlin/com/paperscrape/livewallpaper/prefs/WallpaperPrefs.kt")

    private fun uiSource(name: String): File =
        moduleRoot().resolve("src/main/kotlin/com/paperscrape/livewallpaper/ui/$name")

    private fun mainSources(): List<File> =
        moduleRoot().resolve("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    /** The `app` module directory, found the way the other source-reading tests find theirs. */
    private fun moduleRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val root = if (prefix.isEmpty()) dir else File(dir, "app")
                if (File(root, "src/main/kotlin/com/paperscrape/livewallpaper/ui").isDirectory) return root
            }
            dir = dir.parentFile
        }
        error("app module root not found from ${File(".").absolutePath}")
    }
}
