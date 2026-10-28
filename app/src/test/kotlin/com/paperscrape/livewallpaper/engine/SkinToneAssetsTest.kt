package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Skin tone is an automatic property of a generated person and never a preference, enforced against
 * the sources rather than asserted in a report.
 *
 * A person is drawn as fixed art plus one weight mask per colourable region, and the tone arrives at
 * the blit as a number in `PeopleColours`; that the layers add up to the drawing is
 * `PeopleLayerAssetTest`'s. What this class holds is the half that is not about the artwork:
 * the tones are distinct, and **nobody can choose a tone** -- a hard UX requirement that a unit test
 * over the generator cannot see on a settings screen.
 */
class SkinToneAssetsTest {

    /**
     * The tones must be visibly distinct, or the axis is decorative.
     *
     * Read off `PeopleColours.SKIN`, where they live. The threshold is a channel distance rather than
     * a count of differing pixels: the tones are flat paint, so "how far apart are they" is a
     * question about three numbers.
     */
    @Test
    fun `the tones are visibly different from one another`() {
        val tones = PeopleColours.SKIN
        assertEquals("the tone count the population deals over", PedestrianPopulation.SKIN_TONE_COUNT, tones.size)
        for (a in tones.indices) {
            for (b in a + 1 until tones.size) {
                val distance = (0 until 3).sumOf { c ->
                    val shift = 16 - c * 8
                    kotlin.math.abs(((tones[a] ushr shift) and 0xFF) - ((tones[b] ushr shift) and 0xFF))
                }
                assertTrue(
                    "tones $a and $b are ${distance} channel levels apart in total",
                    distance >= 60,
                )
            }
        }
    }

    // ------------------------------------------------- no user configuration

    /**
     * `USER CONFIGURATION = NONE`, enforced against the sources rather than asserted in a report.
     *
     * Looks for a skin-related preference key, DataStore entry or customisation field anywhere in the
     * app's Kotlin, for a skin string in the UI resources, and **for a settings row**: no code line of
     * the settings screens (`ui/`) or of the stored settings (`prefs/`) names a skin -- not a row's
     * title or line, a key, a field or a function. The screens write their words in Kotlin, not in
     * `strings.xml`, so a row offering a tone is a string in `ui/`. A comment is not a setting and is
     * left out; "asking" is not a skin, "skinTone", "driverSkin", "person_skin" and "Skin tone" are.
     */
    @Test
    fun `no user-facing setting selects a skin tone`() {
        val offenders = mutableListOf<String>()
        val preferenceLike = Regex(
            """(stringPreferencesKey|intPreferencesKey|booleanPreferencesKey|floatPreferencesKey)\s*\(\s*"[^"]*skin""",
            RegexOption.IGNORE_CASE,
        )
        for (file in mainSources.walkTopDown().filter { it.extension == "kt" }) {
            // The generator and its own documentation legitimately say "skin" a great deal.
            if (file.name in setOf("PedestrianPopulation.kt", "WindowOccupants.kt")) continue
            val text = file.readText()
            if (preferenceLike.containsMatchIn(text)) offenders += "${file.name}: preference key"
            if (Regex("""val\s+skinTone\s*:""").containsMatchIn(text)) offenders += "${file.name}: config field"
        }
        val skin = Regex("""(?<![a-z])skin|Skin|SKIN""")
        var screens = 0
        for (dir in listOf("ui", "prefs")) {
            for (file in File(mainSources, "kotlin/com/paperscrape/livewallpaper/$dir").walkTopDown().filter { it.extension == "kt" }) {
                screens++
                for ((number, line) in codeLines(file)) if (skin.containsMatchIn(line)) offenders += "${file.name}:$number: $line"
            }
        }
        assertTrue("the settings screens and the store were not found under ${mainSources.path}", screens > 0)
        // Read, never skipped: a file that is not where this looks fails here rather than passing unread.
        val strings = File(mainSources, "res/values/strings.xml")
        check(strings.isFile) { "strings.xml not found at ${strings.path}" }
        for (line in strings.readLines()) {
            if (Regex("""name="[^"]*skin""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                offenders += "strings.xml: ${line.trim()}"
            }
        }
        assertTrue("skin became user-configurable: $offenders", offenders.isEmpty())
    }

    /**
     * Skin must not have leaked into the customisation model that the settings screen edits. The file is read or the test
     * fails: moved or renamed, it would otherwise pass without having looked at anything.
     */
    @Test
    fun `the customisation model has no skin field`() {
        val model = File(mainSources, "kotlin/com/paperscrape/livewallpaper/engine/SceneCustomization.kt")
        val text = checkNotNull(model.takeIf { it.isFile }?.readText()) { "SceneCustomization.kt not found at ${model.path}" }
        assertTrue(
            "SceneCustomization gained a skin field",
            !Regex("""skin""", RegexOption.IGNORE_CASE).containsMatchIn(text),
        )
    }

    /** [file]'s code lines with their numbers: a comment line dropped, a comment at the end of a line cut off. */
    private fun codeLines(file: File): List<Pair<Int, String>> =
        file.readLines().mapIndexed { index, line -> (index + 1) to line.trim() }
            .filterNot { (_, line) -> line.startsWith("*") || line.startsWith("/*") || line.startsWith("//") }
            .map { (number, line) -> number to line.substringBefore(" //") }

    private companion object {

        /** Gradle's working directory is a default, not a guarantee, so walk up to find the tree. */
        val mainSources: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            throw AssertionError("could not locate src/main from ${File(".").absolutePath}")
        }

    }
}
