package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Skin tone is an automatic property of a generated person and never a preference, enforced against
 * the sources rather than asserted in a report.
 *
 * ### What used to be here, and where it went
 *
 * This class also checked that the 168 shipped tone variants differed from their source in skin and
 * in nothing else. **v4.30 stopped shipping them**: a person is drawn as fixed art plus one weight
 * mask per colourable region and the tone arrives at the blit, so there is no variant to compare
 * against a source. The guarantee that replaced it is stronger and lives in `PeopleLayerAssetTest`
 * -- that the layers add up to the drawing itself, with the colours recovered from the files rather
 * than supplied to the check.
 *
 * What stays here is the half that was never about the artwork: **nobody can choose a tone.** That
 * is a hard UX requirement, a unit test over the generator cannot see a settings screen, and the
 * requirement did not change when the artwork did -- if anything it needs saying more loudly now
 * that the tone is a number in `PeopleColours` rather than a choice between three files.
 */
class SkinToneAssetsTest {

    /**
     * The three tones must be visibly distinct, or the axis is decorative.
     *
     * Read off `PeopleColours.SKIN`, which is where they live since v4.30, rather than off three
     * PNGs. The threshold is a channel distance rather than a count of differing pixels: the tones
     * are flat paint now, so "how far apart are they" is a question about three numbers.
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
     * Looks for a skin-related preference key, settings row, DataStore entry or customisation
     * field anywhere in the app's Kotlin, and for a skin string in the UI resources.
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
        val strings = File(mainSources.parentFile, "res/values/strings.xml")
        if (strings.isFile) {
            for (line in strings.readLines()) {
                if (Regex("""name="[^"]*skin""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    offenders += "strings.xml: ${line.trim()}"
                }
            }
        }
        assertTrue("skin became user-configurable: $offenders", offenders.isEmpty())
    }

    /** Skin must not have leaked into the customisation model that the settings screen edits. */
    @Test
    fun `the customisation model has no skin field`() {
        val text = File(mainSources, "kotlin/com/paperscrape/livewallpaper/engine/SceneCustomization.kt")
            .takeIf { it.isFile }?.readText() ?: return
        assertTrue(
            "SceneCustomization gained a skin field",
            !Regex("""skin""", RegexOption.IGNORE_CASE).containsMatchIn(text),
        )
    }

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
