package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.Test

/**
 * Which shipped sprites are required not to be the same drawing as another.
 *
 * Two pixel-identical PNGs are one of exactly two things, and every other check in this project is
 * blind to both. **One drawing under two names** costs two decodes, two atlas entries and two
 * files that can be edited apart in one place only. **A variant that never got drawn** is worse,
 * because the feature it implements silently does nothing: summer and winter heads as one file make
 * a window occupant look the same in January and July while every per-sprite check -- size,
 * content box, anchor, scale, tint -- passes, because all of those are satisfied by two copies of
 * one picture.
 *
 * `tools/assets` holds the richer version of this: `sources/sprites.json` declares each variant
 * group with an axis, a state and a reason, and `paperscrape-assets validate` checks the whole
 * table. **This test is not that check restated.** Gradle is the only thing CI runs, so the
 * tooling's answer never gates a release; what is held here is only the narrow property that has
 * to hold in the APK, in the smallest form that still fails for the right reason.
 */
class SpriteVariantTest {

    /**
     * The head sprites of window occupants and car drivers that are required to differ between
     * summer and winter, as their groups in `tools/assets/sources/sprites.json` are `DISTINCT`.
     */
    private val seasonalHeadPairs = listOf(
        "person_man_summer_head_window" to "person_man_winter_head_window",
        "person_woman_summer_head_window" to "person_woman_winter_head_window",
        "person_boy_summer_head_window" to "person_boy_winter_head_window",
        "person_girl_summer_head_window" to "person_girl_winter_head_window",
        // The seated pairs are checked on the fixed layer, which carries the drawing's own outline
        // and everything in it that does not follow one of the four colours -- the hat, the scarf,
        // the hood, the collar. That is precisely what "different artwork" means for a season.
        "person_man_summer_head_car_fx" to "person_man_winter_head_car_fx",
        "person_boy_summer_head_car_fx" to "person_boy_winter_head_car_fx",
        "person_girl_summer_head_car_fx" to "person_girl_winter_head_car_fx",
        "person_woman_summer_head_car_fx" to "person_woman_winter_head_car_fx",
    )

    /**
     * The walkers whose summer and winter frames must differ. Asserted rather than assumed: a
     * regeneration that copied one season over the other would be invisible to every other check.
     */
    private val seasonalKinds = listOf("man", "woman", "boy", "girl")

    // --- What must not be shared ---------------------------------------------------------------

    @Test
    fun `summer and winter walking sprites are different artwork`() {
        for (kind in seasonalKinds) {
            for (frame in 0..2) {
                val summer = "person_${kind}_summer_walk$frame"
                val winter = "person_${kind}_winter_walk$frame"
                assertNotEquals(
                    "$summer and $winter are pixel-identical: the seasonal outfit is not in the " +
                        "artwork, so winter looks like summer for walking people",
                    digest(summer), digest(winter),
                )
            }
        }
    }

    @Test
    fun `summer and winter head sprites are different artwork`() {
        for ((summer, winter) in seasonalHeadPairs) {
            assertNotEquals(
                "$summer and $winter are pixel-identical, so a window occupant or a driver looks " +
                    "the same in January as in July. This was a declared gap until the V2 asset " +
                    "set drew the winter heads; reverting to one shared drawing is a regression, " +
                    "not a deduplication.",
                digest(summer), digest(winter),
            )
        }
    }

    // --- What must not be duplicated -----------------------------------------------------------

    /**
     * No two shipped sprites are the same drawing, compared by their pixels.
     *
     * Stated as a property rather than as a count of files: a count would have to be edited by
     * whoever added a duplicate, which is the opposite of a check. There is no exemption list:
     * every pixel-identical pair is a finding.
     */
    @Test
    fun `no two shipped sprites are the same drawing`() {
        val byDigest = mutableMapOf<String, MutableList<String>>()
        for (name in spriteNames()) {
            byDigest.getOrPut(digest(name)) { mutableListOf() } += name
        }
        val duplicated = byDigest.values
            .filter { it.size > 1 }
            .map { it.sorted() }

        assertEquals(
            "these sprites ship as the same pixels. Either they are one drawing under two names, " +
                "and one should go with its call sites pointed at the survivor, or they are " +
                "variants whose artwork was never drawn apart, and both this file and " +
                "tools/assets/sources/sprites.json have to say so: $duplicated",
            emptyList<List<String>>(), duplicated,
        )
    }

    // --- Reading the PNGs ----------------------------------------------------------------------

    private fun spriteNames(): List<String> {
        val names = drawableDir.listFiles { file -> file.name.endsWith(".png") }
            .orEmpty()
            .map { it.name.removeSuffix(".png") }
            .sorted()
        assertTrue("no sprites found in ${drawableDir.path}", names.isNotEmpty())
        return names
    }

    /**
     * The SHA-256 of the sprite's decoded RGBA pixels, row by row -- the `pixels_sha256` of
     * `tools/assets/reports/runtime-inventory.json`. Of the pixels and not of the file: two files
     * of one drawing written by two encoders, or by one encoder over two zlib builds, have
     * different bytes, and a byte digest would call them two drawings.
     */
    private fun digest(name: String): String {
        val file = File(drawableDir, "$name.png")
        assertTrue("${file.path} does not exist", file.isFile)
        val image = ImageIO.read(file)
        val sha = MessageDigest.getInstance("SHA-256")
        val row = ByteArray(image.width * 4)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                row[x * 4] = (argb shr 16).toByte()
                row[x * 4 + 1] = (argb shr 8).toByte()
                row[x * 4 + 2] = argb.toByte()
                row[x * 4 + 3] = (argb ushr 24).toByte()
            }
            sha.update(row)
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /**
         * Gradle runs unit tests with the module directory as the working directory, but that is
         * a default rather than a guarantee, so walk up until the drawable directory is found
         * instead of assuming a fixed depth.
         */
        val drawableDir: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            throw AssertionError(
                "could not locate src/main/res/drawable-nodpi from ${File(".").absolutePath}",
            )
        }
    }
}
