package com.paperscrape.livewallpaper.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The lake overrides its boats and dolphins; it does not overwrite the user's preferences.**
 *
 * ### The behaviour this is about, and where it already lived
 *
 * `PaperRenderer.updateLakeBandY` returns early while `lake.visible` is false, so `drawLake`
 * never reaches the sailboats or the dolphins. That is correct: there is no water for them to
 * float on. What v5.7C changes is the settings screen, which -- on *Lake, boats and dolphins*,
 * with **Show Lake** off -- still drew "Show Sailboats" and "Show Dolphins" reading **on**, with
 * both density sliders fully live. Four controls that move without effect, on the screen a user
 * meets first: the shipped Sunset theme has the lake off out of the box.
 *
 * This is the same defect `MoonPhaseControlTest` pins for Halloween and the moon, one screen
 * over, and it is fixed the same way for the same reason -- so this test is deliberately that
 * test's shape, and the two should be read together.
 *
 * ### Two halves, and why neither proves the invariant alone
 *
 * **The rule** is [SettingsUiModel.lakeContents]: shown off and locked while the lake is off, the
 * stored values otherwise. A pure function of three booleans, so all eight combinations are
 * asserted directly, including the property that matters most -- that the *stored* values never
 * decide interactivity.
 *
 * **The wiring** cannot be reached that way. This project has no Compose UI test infrastructure
 * (not one `createComposeRule` in the tree), so there is no way to tap a switch and read a
 * DataStore back on the JVM; and an instrumented test that did would prove the rows are locked
 * without proving *nothing writes*, which is the half that destroys data when it is wrong. So the
 * wiring is pinned by reading the source, exactly as `MoonPhaseControlTest` pins its own.
 *
 * **What the source half actually proves.** That the four rows read their `checked` and `enabled`
 * from the derivation rather than from the raw flags; that all three inputs come from the same
 * resolved `customization`, which is what makes the override per-theme; and -- the load-bearing
 * one -- that each of the four lake-contents preferences has **exactly one writer in the whole of
 * `src/main`**, its own control's callback. Together those say the stored values cannot be
 * reached by the lake path at all, which is the only way "the preferences survive" can be proved
 * rather than observed once.
 *
 * The rule it comes from is written down already, in `PeopleDensity.resolveNightDensity`: a
 * settings change is not entitled to silently alter what an existing user had set up. Writing
 * `false` into `sailboatsVisible` to make the switch look right would be exactly that.
 */
class LakeContentsControlTest {

    // ------------------------------------------------------------------ the rule

    @Test
    fun `with the lake on the controls are the stored values, live`() {
        for (boats in listOf(true, false)) {
            for (dolphins in listOf(true, false)) {
                val state = SettingsUiModel.lakeContents(
                    lakeVisible = true,
                    storedSailboatsVisible = boats,
                    storedDolphinsVisible = dolphins,
                )
                assertEquals("the sailboat switch must show what is stored", boats, state.sailboatsShownOn)
                assertEquals("the dolphin switch must show what is stored", dolphins, state.dolphinsShownOn)
                assertTrue("the controls must accept a touch", state.interactive)
                assertFalse("nothing is holding them", state.blockedByLakeOff)
            }
        }
    }

    @Test
    fun `with the lake off the controls are off and locked, whatever is stored`() {
        for (boats in listOf(true, false)) {
            for (dolphins in listOf(true, false)) {
                val state = SettingsUiModel.lakeContents(
                    lakeVisible = false,
                    storedSailboatsVisible = boats,
                    storedDolphinsVisible = dolphins,
                )
                assertFalse("there is no water, so the switch shows off", state.sailboatsShownOn)
                assertFalse("there is no water, so the switch shows off", state.dolphinsShownOn)
                assertFalse("and they must not accept a touch that would do nothing", state.interactive)
                assertTrue("the rows owe the user the reason", state.blockedByLakeOff)
            }
        }
    }

    /**
     * Only the lake decides whether the four are locked.
     *
     * Stated separately because it is the property a future edit is most likely to break: making
     * interactivity depend on the stored values as well would produce a slider that is live only
     * while its own switch is on, which looks reasonable and is a different feature from the one
     * approved -- and it would leave the *sliders* live under a switch that is locked off.
     */
    @Test
    fun `only the lake decides whether the four controls are locked`() {
        val interactivity = buildSet {
            for (boats in listOf(true, false)) {
                for (dolphins in listOf(true, false)) {
                    add(SettingsUiModel.lakeContents(true, boats, dolphins).interactive)
                }
            }
        }
        assertEquals("with the lake on, always live", setOf(true), interactivity)
        val locked = buildSet {
            for (boats in listOf(true, false)) {
                for (dolphins in listOf(true, false)) {
                    add(SettingsUiModel.lakeContents(false, boats, dolphins).interactive)
                }
            }
        }
        assertEquals("with the lake off, never live", setOf(false), locked)
    }

    /**
     * The stored values come back the moment the lake goes on -- the whole point of overriding
     * rather than writing.
     */
    @Test
    fun `turning the lake back on restores exactly what was stored`() {
        for (boats in listOf(true, false)) {
            for (dolphins in listOf(true, false)) {
                assertTrue(SettingsUiModel.lakeContents(false, boats, dolphins).blockedByLakeOff)
                val back = SettingsUiModel.lakeContents(true, boats, dolphins)
                assertEquals("the preference is overridden, never replaced", boats, back.sailboatsShownOn)
                assertEquals("the preference is overridden, never replaced", dolphins, back.dolphinsShownOn)
            }
        }
    }

    // ------------------------------------------------------------------ the wiring

    @Test
    fun `the four rows read their state from the derivation, not from the raw flags`() {
        val body = lakeSubScreenSource()
        assertTrue(
            "the screen must build the state through SettingsUiModel.lakeContents",
            body.contains("SettingsUiModel.lakeContents("),
        )
        assertTrue("the sailboat switch must be drawn from the derivation", body.contains("checked = lakeContents.sailboatsShownOn"))
        assertTrue("the dolphin switch must be drawn from the derivation", body.contains("checked = lakeContents.dolphinsShownOn"))
        assertEquals(
            "all four controls -- two switches and two sliders -- must take enabled from the derivation",
            4,
            Regex("""enabled = lakeContents\.interactive""").findAll(body).count(),
        )
        assertFalse(
            "the raw flag must not reach the switch any more -- that is the control that lied",
            body.contains("checked = customization.lake.sailboatsVisible"),
        )
        assertFalse(
            "the raw flag must not reach the switch any more -- that is the control that lied",
            body.contains("checked = customization.lake.dolphinsVisible"),
        )
    }

    /**
     * All three inputs come from the same resolved `customization`, which is what makes the
     * override per-theme. An input read from a global would show one theme's lake locking
     * another theme's boats.
     */
    @Test
    fun `the override and the values it overrides are read from the same theme's customization`() {
        val body = lakeSubScreenSource()
        assertTrue(body.contains("lakeVisible = customization.lake.visible"))
        assertTrue(body.contains("storedSailboatsVisible = customization.lake.sailboatsVisible"))
        assertTrue(body.contains("storedDolphinsVisible = customization.lake.dolphinsVisible"))
    }

    /**
     * **Each of the four preferences has exactly one writer, and it is its own control.**
     *
     * This is the assertion that stands for "the stored values survive". A `LaunchedEffect` that
     * tidied the preferences when the lake went off -- the obvious and wrong repair -- would be
     * invisible to every other test in this suite and would destroy the user's setup on the first
     * frame of the screen.
     */
    @Test
    fun `nothing but its own control ever writes a lake-contents preference`() {
        for (setter in listOf(
            "setLakeSailboatsVisible",
            "setLakeSailboatsDensity",
            "setLakeDolphinsVisible",
            "setLakeDolphinsDensity",
        )) {
            val callers = mainSources()
                .filter { it.name != "WallpaperPrefs.kt" }
                .flatMap { file ->
                    file.readLines().withIndex()
                        .filter { (_, line) -> line.contains("$setter(") }
                        .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
                }
            assertEquals(
                "$setter must have exactly one writer outside WallpaperPrefs, its own control: $callers",
                1,
                callers.size,
            )
            assertTrue(
                "the one writer must be in the lake screen: ${callers.first()}",
                callers.first().startsWith("WorldSceneScreen.kt:"),
            )
        }
    }

    /** Turning the lake off writes the lake's own visibility key and nothing else. */
    @Test
    fun `setting the lake's visibility touches no other preference`() {
        val setter = prefsSource().readText()
            .substringAfter("suspend fun setLakeVisible(")
            .substringBefore("\n    suspend fun ")
        val keysWritten = Regex("""Keys\.([A-Z_0-9]+)""").findAll(setter).map { it.groupValues[1] }.toSet()
        assertTrue(
            "setLakeVisible must write its own key: $keysWritten",
            keysWritten.any { it.contains("LAKE") && !it.contains("SAILBOAT") && !it.contains("DOLPHIN") },
        )
        assertTrue(
            "setLakeVisible must not touch the boats or the dolphins: $keysWritten",
            keysWritten.none { it.contains("SAILBOAT") || it.contains("DOLPHIN") },
        )
    }

    // ------------------------------------------------------------------ source plumbing

    private fun lakeSubScreenSource(): String =
        uiSource("WorldSceneScreen.kt").readText()
            .substringAfter("private fun LakeSubScreen(")
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
