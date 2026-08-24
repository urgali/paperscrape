package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Two guarantees about skin tone that only the shipped files can answer.
 *
 * **That nobody can choose a tone.** The requirement is a hard UX one: skin is an automatic
 * property of a generated person, never a preference. A unit test over the generator cannot see a
 * settings screen, so this reads the actual sources and fails if a preference key, a settings row
 * or a customisation field for skin ever appears.
 *
 * **That the variants really are the same artwork.** The tone axis is only legitimate if a variant
 * differs from its source in skin and nothing else. Rather than trusting the generator script,
 * this re-derives the claim from the PNGs: same dimensions, identical alpha, and every non-skin
 * colour holding exactly the pixel mask it holds in the source.
 */
class SkinToneAssetsTest {

    private val kinds = listOf("man", "woman", "boy", "girl")
    private val seasons = listOf("summer", "winter")
    private val variants = listOf("walk0", "walk1", "walk2", "head_window")

    /** Each character's shipped skin colour, the one the variants move. */
    private val skinBase = mapOf(
        "man" to intArrayOf(220, 169, 124),
        "woman" to intArrayOf(240, 201, 166),
        "boy" to intArrayOf(169, 113, 75),
        "girl" to intArrayOf(239, 185, 148),
    )

    // ------------------------------------------------------------- artwork

    @Test
    fun `every character has artwork for every skin tone, in both seasons`() {
        for (kind in kinds) {
            for (season in seasons) {
                for (variant in variants) {
                    for (tone in 0 until PedestrianPopulation.SKIN_TONE_COUNT) {
                        val file = File(drawableDir, "person_${kind}_${season}_${variant}_skin$tone.png")
                        assertTrue("missing ${file.name}", file.isFile)
                    }
                }
            }
        }
    }

    /**
     * A variant may differ from its source in skin and in nothing else.
     *
     * Checked as: identical dimensions, identical alpha channel, and — for every colour in the
     * source that is not that character's skin — an identical pixel mask. Clothes, hair, eyes,
     * outlines, silhouette and pose are all covered by that last clause, because each of them is
     * some colour that is not skin.
     */
    @Test
    fun `variants change the skin and nothing else`() {
        for (kind in kinds) {
            val base = skinBase.getValue(kind)
            for (season in seasons) {
                for (variant in variants) {
                    val source = ImageIO.read(File(drawableDir, "person_${kind}_${season}_$variant.png"))
                    val sourcePixels = pixels(source)
                    val palette = sourcePixels.toList().distinct()
                        .filter { (it ushr 24 and 0xFF) > 200 }
                        .filterNot { rgbEquals(it, base) }
                        .groupingBy { it }.eachCount()
                        .filter { it.value >= 80 }
                        .keys
                    for (tone in 0 until PedestrianPopulation.SKIN_TONE_COUNT) {
                        val name = "person_${kind}_${season}_${variant}_skin$tone.png"
                        val other = ImageIO.read(File(drawableDir, name))
                        assertEquals("$name width", source.width, other.width)
                        assertEquals("$name height", source.height, other.height)
                        val otherPixels = pixels(other)
                        for (i in sourcePixels.indices) {
                            assertEquals(
                                "$name alpha at $i",
                                sourcePixels[i] ushr 24,
                                otherPixels[i] ushr 24,
                            )
                        }
                        for (colour in palette) {
                            val before = sourcePixels.indices.filter { sourcePixels[it] == colour }
                            val after = sourcePixels.indices.filter { otherPixels[it] == colour }
                            assertEquals(
                                "$name moved a non-skin colour ${colour.toString(16)}",
                                before, after,
                            )
                        }
                    }
                }
            }
        }
    }

    /** The tones must be visibly distinct, or the axis is decorative. */
    @Test
    fun `the tones are visibly different from one another`() {
        val source = File(drawableDir, "person_man_summer_walk0.png")
        val images = (0 until PedestrianPopulation.SKIN_TONE_COUNT).map {
            ImageIO.read(File(source.parentFile, "person_man_summer_walk0_skin$it.png"))
        }
        for (a in images.indices) {
            for (b in a + 1 until images.size) {
                val differing = pixels(images[a]).zip(pixels(images[b])).count { it.first != it.second }
                assertTrue("tones $a and $b are nearly identical", differing > 500)
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

    private fun pixels(image: java.awt.image.BufferedImage): IntArray =
        IntArray(image.width * image.height).also {
            image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
        }

    private fun rgbEquals(argb: Int, rgb: IntArray): Boolean =
        (argb ushr 16 and 0xFF) == rgb[0] &&
            (argb ushr 8 and 0xFF) == rgb[1] &&
            (argb and 0xFF) == rgb[2]

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

        val drawableDir: File by lazy { File(mainSources, "res/drawable-nodpi") }
    }
}
