package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test

/**
 * Every sprite is either a greyscale mask that a call site multiplies by a colour, or finished
 * art blitted as authored. This test pins each sprite to the class its call site treats it as,
 * because neither half is correct on its own and nothing else compares them.
 *
 * **Both directions have shipped as defects, in the same file, one release apart.**
 *
 * The first was a mask blitted untinted. `dolphin_body` and `sailboat_hull` held exactly one
 * colour, pure white, across every opaque pixel, and their call sites passed no tint on the
 * stated grounds that the colours were baked in. White is the `MULTIPLY` identity, so the
 * wallpaper showed white silhouettes drifting on the lake. v74.1 repaired it by supplying the
 * colour at the blit. A second sprite had the same defect and was still open as D-6.
 *
 * The second is the mirror image, and it is what the V2 asset set would have caused if the call
 * sites had been left alone: finished art multiplied by a constant is not a recolouring, it is
 * two colours compounded. A green frond times an autumn orange is mud; an orange sun disc times
 * a user's blue is black.
 *
 * Nothing can catch either from one side. A PNG does not record whether its greys are finished
 * artwork or a mask awaiting a colour, so a sprite whose class flipped underneath an unchanged
 * call site passes every per-sprite check there is -- size, content box, anchor, scale. The only
 * place the contradiction is visible is between the pixels and the blit, which is what this
 * reads.
 *
 * The two lists below are the call sites, restated. That is deliberate and it is the same trade
 * `SpriteVariantTest` makes: `tools/assets` holds the richer version, resolved from the Kotlin
 * sources by `paperscrape-assets validate`, but Gradle is the only thing CI runs, so the
 * tooling's answer never gates a release. What is duplicated here is only the narrow property
 * that has to hold in the APK.
 *
 * **Moving a sprite between the lists is not how a failure here is fixed** -- not on its own. A
 * sprite changes class only when its artwork changes, and then its call site has to move in the
 * same change: to `SpriteBlitter.draw` if it gained colours, to `drawTinted` with a colour if it
 * lost them.
 */
class SpriteTintClassTest {

    /**
     * Sprites some call site multiplies by a colour. Every one must be a light neutral mask.
     *
     * Read off the `drawTinted` call sites in `SceneObjectRenderer` and `PaperRenderer`. The sky
     * contributes only the moons and the birds: the sun, its glow and the star sparkle are all
     * fixed art in V2, so nothing multiplies them any more.
     */
    private val tintedSprites = listOf(
        "house_small_wall", "house_small_roof", "house_small_trim", "house_small_chimney",
        "house_small_door",
        "house_large_wall", "house_large_roof", "house_large_trim", "house_large_chimney",
        "house_large_door",
        "tree_canopy", "snowman_body", "gift_box",
        // `skyscraper_wall_lit` joined this list in v4.12: it is the tower's window grid, and
        // since it stopped carrying its own warm colour it is tinted cool by day and warm at
        // night like every other window in the scene. See `windowGlassColor`.
        "skyscraper_wall", "skyscraper_setback", "skyscraper_wall_lit",
        "restaurant_wall", "restaurant_window", "restaurant_door",
        "bar_wall", "bar_door",
        "penguin_body", "penguin_belly", "easteregg_shell", "bunny_body",
        "pumpkin_body", "car_body",
        "cloud_body", "bird_body",
        "moon_full", "moon_crescent", "moon_half", "moon_gibbous",
        // The Halloween moon. Tintable like every other phase: it is the same disc under the
        // same theme colour, with the skull cut out of it rather than painted on.
        "moon_jack_o_lantern",
    )

    /**
     * Sprites blitted as authored. Every one must carry a colour of its own, or the blit draws a
     * white or grey silhouette -- the D-6 family of defects.
     *
     * A sprite in neither list is caught by `every shipped sprite is classified`, so this list
     * cannot be left behind by an asset pass that adds one.
     */
    private val fixedArtSprites = listOf(
        // v4.17. All three are blitted untinted and all three carry their own colour: a carved
        // hole is not a colour a theme picks, snow is white wherever it lies, and a heap of leaves
        // is painted in the same four autumn tones `drawFallingLeaves` uses.
        "leaf_pile",
        "pumpkin_face",
        "snow_pile",
        "house_shared_window", "house_shared_planter", "house_window_lit",
        "tree_trunk", "tree_canopy_snowcap",
        // Halloween's bare crown. Fixed art in the trunk browns rather than tintable: a dead
        // tree is not a theme colour, and multiplying it by a leaf green is the one thing
        // that would give the effect away.
        "tree_dead_branches",
        // The dolphin's re-entry splash, two frames. Water in its own greys and blues, the
        // same fixed art the dolphin and the lake decorations already are.
        "water_splash0", "water_splash1",
        // The roof caps, added in v76.12 for defect D-8. Fixed art for the same reason the tree's
        // cap is: snow is white with its own cool shadow, and tinting it would make it the roof's
        // colour, which is the thing a layer of snow is meant not to be.
        "house_small_roof_snow", "house_large_roof_snow",
        "restaurant_roof_snow", "bar_roof_snow", "skyscraper_roof_snow",
        "palmtree_trunk", "palmtree_fronds", "palmtree_fronds_frost",
        // Halloween's palm crown. Fixed art in the trunk browns for the same reason
        // `tree_dead_branches` is: a dead frond is not a theme colour.
        "palmtree_fronds_dead",
        // The flower clump. Fixed art for the reason its own registry note gives.
        "ground_flowers",
        // v2.8: the tower's entrance is glass and metal, and a fir is a species. Both fixed.
        "skyscraper_entrance", "tree_fir", "tree_fir_snow",
        "snowman_nose", "snowman_scarf", "gift_ribbon",
        "skyscraper_canopy", "restaurant_awning", "restaurant_sign", "bar_sign",
        "penguin_beak", "penguin_feet", "easteregg_pattern", "bunny_innerear", "bunny_tail",
        "pumpkin_stem",
        "car_window", "police_stripe", "police_lightbar", "taxi_checker",
        "firetruck_ladder", "firetruck_body",
        "dolphin_body", "sailboat_hull", "sailboat_sail",
        "sun_body", "sun_glow", "star_sparkle",
        "santa_sleigh_scene", "santa_sleigh_trot",
        "rainbow_arc", "lightning_bolt", "firework",
    )

    /**
     * Person art: every walk frame and every head, all of it blitted untinted.
     *
     * Matched by prefix rather than listed, because the set is 36 sprites generated from four
     * kinds, two seasons and five poses, and a hand-written list of that size stops being read.
     * The property is the same one [fixedArtSprites] carries, and a person sprite that lost its
     * colours would fail it exactly as loudly.
     */
    private fun isPersonSprite(name: String) = name.startsWith("person_")

    /**
     * Sprites no call site reaches, so there is no blit to agree or disagree with.
     *
     * Whether the orphans should exist at all is its own question (`ROADMAP.md`, 5.5 and 7.2);
     * classifying them here would answer it by implication.
     */
    private val orphans = listOf("house_window", "road_asphalt", "road_curb", "road_line")

    // --- The masks -----------------------------------------------------------------------------

    /**
     * Every opaque pixel of a tinted sprite must be a neutral grey. A sprite whose channels
     * differ carries a hue of its own, and multiplying a hue by a second hue compounds them.
     */
    @Test
    fun `every tinted sprite is authored as a colourless mask`() {
        for (name in tintedSprites) {
            val offending = firstColouredPixel(name)
            assertEquals(
                "$name carries a colour of its own at $offending, but its call site multiplies " +
                    "it by a colour, which compounds two hues instead of tinting a mask. If the " +
                    "artwork was redrawn with baked colours, its call site has to move to " +
                    "SpriteBlitter.draw in the same change.",
                null, offending,
            )
        }
    }

    /**
     * The masks must actually be light, or the tint has nothing to work with: `MULTIPLY` can only
     * darken, so a mask averaging mid-grey renders every colour at roughly half the value it
     * names.
     */
    @Test
    fun `every tinted sprite is light enough for MULTIPLY to carry the colour`() {
        for (name in tintedSprites) {
            val mean = meanOpaqueLevel(name)
            assertTrue("$name averages $mean, too dark to be a tint mask", mean >= 220f)
        }
    }

    // --- The finished art ----------------------------------------------------------------------

    /**
     * Every fixed-art sprite must carry a colour somewhere. A colourless one blitted untinted is
     * a white or grey silhouette, which is exactly what the lake decorations and the seasonal
     * basket shipped as.
     */
    @Test
    fun `every fixed-art sprite carries a colour of its own`() {
        for (name in fixedArtSprites + spriteNames().filter(::isPersonSprite)) {
            assertTrue(
                "$name is blitted untinted but every opaque pixel is neutral grey, so it draws " +
                    "as a silhouette. Either the artwork lost its colours, or this sprite is a " +
                    "mask and its call site should be tinting it.",
                firstColouredPixel(name) != null,
            )
        }
    }

    // --- The set ------------------------------------------------------------------------------

    /**
     * Nothing may ship unclassified. A new sprite that reaches neither list is a sprite whose
     * blit nothing checks, which is the state every defect above was found in.
     */
    @Test
    fun `every shipped sprite is classified`() {
        val classified = (tintedSprites + fixedArtSprites + orphans).toSet()
        val shipped = spriteNames().toSet()

        val unclassified = shipped.filterNot { it in classified || isPersonSprite(it) }.sorted()
        assertEquals(
            "these sprites ship but no list here says whether their call site tints them: " +
                "$unclassified",
            emptyList<String>(), unclassified,
        )

        val missing = (classified - shipped).sorted()
        assertEquals(
            "these sprites are classified here but no longer ship: $missing",
            emptyList<String>(), missing,
        )
    }

    /** A sprite cannot be both, and listing it twice would make one of the two assertions vacuous. */
    @Test
    fun `no sprite is classified twice`() {
        val all = tintedSprites + fixedArtSprites + orphans
        val duplicated = all.groupBy { it }.filterValues { it.size > 1 }.keys.sorted()
        assertEquals("classified more than once: $duplicated", emptyList<String>(), duplicated)
    }

    // --- Reading the PNG -----------------------------------------------------------------------

    /** The first opaque pixel whose channels are not all equal, as `x,y`, or `null` if none is. */
    private fun firstColouredPixel(name: String): String? {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
            ?: throw AssertionError("$name.png could not be read")
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                if ((argb ushr 24) and 0xFF == 0) continue
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                if (r != g || g != b) return "$x,$y"
            }
        }
        return null
    }

    /** Mean level of the opaque pixels, on the 0..255 scale the channels already agree on. */
    private fun meanOpaqueLevel(name: String): Float {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
            ?: throw AssertionError("$name.png could not be read")
        var total = 0L
        var count = 0L
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                if ((argb ushr 24) and 0xFF == 0) continue
                total += (argb ushr 16) and 0xFF
                count++
            }
        }
        assertTrue("$name.png has no opaque pixels", count > 0)
        return total.toFloat() / count
    }

    private fun spriteNames(): List<String> {
        val names = drawableDir.listFiles { file -> file.name.endsWith(".png") }
            .orEmpty()
            .map { it.name.removeSuffix(".png") }
            .sorted()
        assertTrue("no sprites found in ${drawableDir.path}", names.isNotEmpty())
        return names
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
