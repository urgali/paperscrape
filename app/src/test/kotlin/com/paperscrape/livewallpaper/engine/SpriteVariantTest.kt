package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.security.MessageDigest
import org.junit.Test

/**
 * Which shipped sprites are allowed to be the same file as another, and which are required not to
 * be. Phase 3.6.
 *
 * Two byte-identical PNGs are one of exactly two things, and every other check in this project is
 * blind to both. **One drawing under two names** costs two decodes, two atlas entries and two
 * files that can be edited apart in one place only; Phase 3.4 removed ten of those. **A variant
 * that never got drawn** is worse, because the feature it implements silently does nothing: v73
 * shipped seasonal outfits for window occupants and car drivers, the summer and winter head PNGs
 * were the same file, and the app looked identical in January and July while every per-sprite
 * check -- size, content box, anchor, scale, tint -- passed, because all of those are satisfied
 * by two copies of one picture.
 *
 * `tools/assets` holds the richer version of this: `sources/sprites.json` declares each variant
 * group with an axis, a state and a reason, and `paperscrape-assets validate` checks the whole
 * table. **This test is not that check restated.** It exists because Gradle is the only thing CI
 * runs, so the tooling's answer never gates a release, and because the manifest is deliberately
 * tooling-side -- no Kotlin reads it -- so the declaration cannot simply be imported. What is
 * duplicated here is therefore only the narrow property that has to hold in the APK, spelled out
 * in the smallest form that still fails for the right reason.
 */
class SpriteVariantTest {

    /**
     * The head sprites that had to differ and did not.
     *
     * v73 shipped seasonal outfits for window occupants and car drivers with the summer and
     * winter PNGs as the same file, so the app looked identical in January and July while every
     * per-sprite check passed. Phase 3.5 recorded that as a declared gap rather than inventing
     * artwork for it, and this list held the six pairs that were allowed to stay identical.
     *
     * **The V2 asset set closed it.** All six are genuinely different drawings now -- a hat, a
     * scarf, a hood, a raised collar, cold cheeks -- so the list flipped from "allowed to be the
     * same" to "required to differ", which is where it should have been all along. The matching
     * groups in `tools/assets/sources/sprites.json` moved from `IDENTICAL_GAP` to `DISTINCT` in
     * the same change.
     */
    private val seasonalHeadPairs = listOf(
        "person_man_summer_head_window" to "person_man_winter_head_window",
        "person_woman_summer_head_window" to "person_woman_winter_head_window",
        "person_boy_summer_head_window" to "person_boy_winter_head_window",
        "person_girl_summer_head_window" to "person_girl_winter_head_window",
        "person_man_summer_head_car_skin1" to "person_man_winter_head_car_skin1",
        "person_boy_summer_head_car_skin1" to "person_boy_winter_head_car_skin1",
        "person_girl_summer_head_car_skin1" to "person_girl_winter_head_car_skin1",
        "person_woman_summer_head_car_skin0" to "person_woman_winter_head_car_skin0",
    )

    /**
     * The seasonal difference on the walking sprites: the winter set carries a beanie instead of
     * hair, long sleeves and a snowflake motif, and the girl wears trousers rather than a skirt.
     *
     * Asserted rather than assumed, because a regeneration that copied one season over the other
     * would be invisible everywhere else -- which is exactly how the head sprites above ended up
     * identical in the first place.
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
                    "$summer and $winter are byte-identical: the seasonal outfit is not in the " +
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
                "$summer and $winter are byte-identical, so a window occupant or a driver looks " +
                    "the same in January as in July. This was a declared gap until the V2 asset " +
                    "set drew the winter heads; reverting to one shared drawing is a regression, " +
                    "not a deduplication.",
                digest(summer), digest(winter),
            )
        }
    }

    // --- What must not be duplicated -----------------------------------------------------------

    /**
     * The property Phase 3.4 delivered, stated so it cannot quietly regress.
     *
     * Ten PNGs were removed because they were second copies of a drawing that already shipped:
     * the small and large houses' window and planter, which now share `house_shared_window` and
     * `house_shared_planter`, and the eight `person_*_walk3` frames, whose slot in
     * `SceneObjectRenderer.personWalkDrawables` now names `walk1` — the walk cycle's passing pose,
     * where the legs are together and the flat silhouette is the same whichever leg leads.
     *
     * Stated as "no two sprites are the same bytes" rather than as a count of files: a count
     * would have to be edited by whoever added a duplicate, which is the opposite of a check.
     *
     * The exemption list is gone. It held the six seasonal head pairs while their winter artwork
     * was a declared gap; the V2 asset set drew them, so the shipped set now contains no
     * byte-identical pair at all and every duplicate is a finding.
     */
    @Test
    fun `no two shipped sprites are the same bytes`() {
        val byDigest = mutableMapOf<String, MutableList<String>>()
        for (name in spriteNames()) {
            byDigest.getOrPut(digest(name)) { mutableListOf() } += name
        }
        val duplicated = byDigest.values
            .filter { it.size > 1 }
            .map { it.sorted() }

        assertEquals(
            "these sprites ship as the same bytes. Either they are one drawing under two names, " +
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

    private fun digest(name: String): String {
        val file = File(drawableDir, "$name.png")
        assertTrue("${file.path} does not exist", file.isFile)
        return MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
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
