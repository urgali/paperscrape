package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test

/**
 * Every sprite is either a greyscale mask that a call site multiplies by a colour, or finished
 * art blitted as authored (`DESIGN_NOTES.md` §12, decision 25). This test pins each sprite to the
 * class its call site treats it as, because neither half is correct on its own and nothing else
 * compares them.
 *
 * **Each direction is a defect of its own.**
 *
 * A mask blitted untinted draws white: a white mask holds one colour across every opaque pixel,
 * and white is the `MULTIPLY` identity, so a call site that passes no tint because it takes the
 * colours to be baked in puts white silhouettes on the screen.
 *
 * Finished art multiplied by a colour is not a recolouring, it is two colours compounded. A green
 * frond times an autumn orange is mud; an orange sun disc times a user's blue is black.
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
     * Read off the `drawTinted` call sites in `SceneObjectRenderer` and `PaperRenderer`. Of the sky's
     * sprites only the moons, the clouds and the birds are tinted: the sun, its glow and the star
     * sparkle are fixed art, and nothing multiplies them.
     */
    private val tintedSprites = listOf(
        // **The neighbourhood.** A building's tinted surfaces are two **weight masks** per piece --
        // `_mw` for everything that follows the wall, `_mg` for the glass -- summed over a fixed
        // layer at the blit, as a person's regions are (decision 27). The class is the one this
        // test means (a light neutral mask a colour is carried by); a roof is a weight inside `_mw`,
        // not a sprite with a roof colour of its own.
        "house_small_ground_mg", "house_small_ground_mw",
        "house_small_roof_gable_mw", "house_small_roof_mansard_mg", "house_small_roof_mansard_mw",
        "house_small_storey_mg", "house_small_storey_mw",
        "house_large_ground_mg", "house_large_ground_mw",
        "house_large_roof_gable_mg", "house_large_roof_gable_mw",
        "house_large_roof_mansard_mg", "house_large_roof_mansard_mw",
        "house_large_roof_turret_gable_mw",
        "house_large_roof_turret_tower_mg", "house_large_roof_turret_tower_mw",
        "house_large_storey_mg", "house_large_storey_mw",
        "tower_bay_mg", "tower_crown_dome_mw", "tower_crown_spire_mw",
        "tower_row_tier1_mg", "tower_row_tier2_mg", "tower_row_tier3_mg",
        "tower_tier1_mg", "tower_tier1_mw", "tower_tier2_mw", "tower_tier3_mw",
        "restaurant_pavilion_mg", "restaurant_pavilion_mw",
        "bar_chamfer_mg", "bar_chamfer_mw", "bar_signboard_mg", "bar_signboard_mw",
        // The school's wall and glass weights, summed at the blit under its group's colours, the
        // public buildings', as every building's are (decision 62).
        "school_mg", "school_mw",
        // The short and the tall towers' first tiers and the tall one's second, the glass of the
        // hall's windows by the door, and the three distant houses -- the houses' paper, summed in
        // the houses' colours.
        "tower_bay_door_mg", "tower_tier1_short_mg", "tower_tier1_short_mw",
        "tower_tier1_tall_mg", "tower_tier1_tall_mw", "tower_tier2_tall_mw",
        "house_distant_cottage_mg", "house_distant_cottage_mw",
        "house_distant_chalet_mg", "house_distant_chalet_mw",
        "house_distant_tall_mg", "house_distant_tall_mw",
        // The shop's and the two stations' wall and glass weights, summed at the blit under their
        // group's colours -- the shops' or the public buildings' -- as every building's are.
        "shop_mg", "shop_mw", "fire_station_mg", "fire_station_mw",
        "fire_station_tower_mg", "fire_station_tower_mw", "police_station_mg", "police_station_mw",
        "tree_canopy", "snowman_body", "gift_box",
        // One Christmas bulb, a white disc each light tints its own colour.
        "deco_bulb",
        "penguin_body", "penguin_belly", "easteregg_shell", "bunny_body",
        "pumpkin_body", "car_body_compact", "car_body_saloon", "car_body_estate",
        "cloud_body", "bird_body",
        "moon_full", "moon_crescent", "moon_half", "moon_gibbous",
        // The Halloween moon. Tintable like every other phase: it is the same disc under the
        // theme's moon colour, as every moon (decision 74), with the face cut out of it rather than
        // painted on.
        "moon_jack_o_lantern",
        // Its laughing face, the same disc under the same colour, drawn in its place a moment a minute.
        "moon_jack_o_lantern_laugh",
        // The Halloween cobweb, a white mask tinted light on a dark wall and dark on a light one
        // (`HalloweenDecorations.webTintOn`, decision 85).
        "deco_cobweb",
        // The umbrella's canopy is tinted per pedestrian from `PedestrianCarry.PALETTE`, so
        // one drawing serves every colour on the street; the wave's two masks are tinted per frame
        // from the water under them, which is the whole of `WaveTint` -- a wave with a colour of its
        // own would be wrong at some hour of every theme.
        "umbrella_canopy", "wave_tube_body", "wave_tube_crest",
    )

    /**
     * Sprites blitted as authored. Every one must carry a colour of its own, or the blit draws a
     * white or grey silhouette.
     *
     * A sprite in neither list is caught by `every shipped sprite is classified`, so this list
     * cannot be left behind by an asset pass that adds one.
     */
    private val fixedArtSprites = listOf(
        // All three are blitted untinted and all three carry their own colour: a carved hole is not
        // a colour a theme picks, snow is white wherever it lies, and a heap of leaves is painted in
        // the same four autumn tones `drawFallingLeaves` uses.
        "leaf_pile",
        "pumpkin_face",
        "snow_pile",
        "tree_trunk", "tree_canopy_snowcap",
        // Halloween's bare crown. Fixed art in the trunk browns rather than tintable: a dead
        // tree is not a theme colour, and multiplying it by a leaf green is the one thing
        // that would give the effect away.
        "tree_dead_branches",
        // The dolphin's re-entry splash, two frames. Water in its own greys and blues, the
        // same fixed art the dolphin and the lake decorations already are.
        "water_splash0", "water_splash1",
        // The neighbourhood's drifts, one per roof alternative and five along the tower's setbacks.
        // Fixed art for the reason the tree's cap is: snow is white with its own cool shadow, and
        // tinting it would make it the roof's colour, which is the thing a layer of snow is meant
        // not to be.
        "house_small_roof_gable_snow_fx", "house_small_roof_mansard_snow_fx",
        "house_large_roof_gable_snow_fx", "house_large_roof_mansard_snow_fx",
        "house_large_roof_turret_gable_snow_fx", "house_large_roof_turret_tower_snow_fx",
        "tower_snow_left1_fx", "tower_snow_left2_fx", "tower_snow_right1_fx",
        "tower_snow_right2_fx", "tower_snow_top_fx",
        "tower_crown_dome_snow_fx", "tower_crown_spire_snow_fx",
        "restaurant_pavilion_snow_fx", "bar_chamfer_snow_fx", "bar_signboard_snow_fx",
        "school_snow_fx",
        // The shop's and the two stations' snow, on their cornices and the tower's roof.
        "shop_snow_fx", "fire_station_snow_fx", "fire_station_tower_snow_fx", "police_station_snow_fx",
        // The short tower's one wide cap, and the distant houses' snow.
        "tower_snow_top_wide_fx",
        "house_distant_cottage_snow_fx", "house_distant_chalet_snow_fx", "house_distant_tall_snow_fx",
        "palmtree_trunk", "palmtree_fronds", "palmtree_fronds_frost",
        // Halloween's palm crown. Fixed art in the trunk browns for the same reason
        // `tree_dead_branches` is: a dead frond is not a theme colour.
        "palmtree_fronds_dead",
        // The flower clumps, in bloom and gone over. Fixed art for the reason their own
        // registry notes give: a meadow that followed a theme's building tint would be a meadow
        // of the wrong flowers.
        "ground_flowers_bloom", "ground_flowers_dry",
        // **The neighbourhood's fixed layer.** One `_fx` per piece: every ink that does not follow
        // one of the two colours the engine resolves at the blit, plus the dark half of every ink
        // that does -- a paper shadow is `(1-t)*paint + t*INK` and the `t*INK` term does not move
        // when the paint does. It also carries what is nobody's theme colour: awnings, plaques,
        // lanterns, stone steps, the cream frames of the windows people stand in.
        "house_small_ground_fx", "house_small_roof_gable_fx", "house_small_roof_mansard_fx",
        "house_small_storey_fx",
        "house_large_ground_fx", "house_large_roof_gable_fx", "house_large_roof_mansard_fx",
        "house_large_roof_turret_gable_fx", "house_large_roof_turret_tower_fx",
        "house_large_storey_fx",
        "tower_bay_fx", "tower_crown_dome_fx", "tower_crown_spire_fx",
        "tower_row_tier1_fx", "tower_row_tier2_fx", "tower_row_tier3_fx",
        "tower_tier1_fx", "tower_tier2_fx", "tower_tier3_fx",
        "restaurant_pavilion_fx", "bar_chamfer_fx", "bar_signboard_fx", "school_fx",
        // The short and the tall tower bodies' fixed layers, the hall windows' frames, the distant houses'.
        "tower_bay_door_fx", "tower_tier1_short_fx", "tower_tier1_tall_fx", "tower_tier2_tall_fx",
        "house_distant_cottage_fx", "house_distant_chalet_fx", "house_distant_tall_fx",
        // The shop's and the two stations' fixed layers -- the awning's stripes, the bag, the red
        // doors and the engine, the chequered band, the light bar, the shield -- in their own colours.
        "shop_fx", "fire_station_fx", "fire_station_tower_fx", "police_station_fx",
        // The fir, a species of its own, and its star, a piece of its own so that it stays out of the
        // trees' night shade (decision 66).
        "tree_fir", "tree_fir_snow", "tree_fir_star",
        // The wreath on a front door, its green and its red bow drawn in.
        "deco_wreath",
        "snowman_nose", "snowman_scarf", "gift_ribbon",

        "penguin_beak", "penguin_feet", "easteregg_pattern", "bunny_innerear", "bunny_tail",
        "pumpkin_stem",
        "car_window_compact", "car_window_saloon", "car_window_estate",
        "police_stripe", "police_lightbar", "taxi_checker", "taxi_sign",
        "car_lamp_front", "car_lamp_front_lit", "car_lamp_rear", "car_lamp_rear_lit",
        "firetruck_ladder", "firetruck_body",
        "dolphin_body", "sailboat_hull", "sailboat_sail",
        "sun_body", "sun_glow", "star_sparkle",
        // Santa's sleigh and its reindeer's four poses, fixed art drawn as by day at night too, and the light inside the
        // Halloween moon's carved face, laid over the tinted moon untinted.
        "santa_sleigh", "santa_reindeer_0", "santa_reindeer_1", "santa_reindeer_2", "santa_reindeer_3",
        "moon_jack_o_lantern_light",
        // The Headless Horseman's pieces, fixed art drawn as by day at night too, as the sleigh's (the rider in three: his
        // body, the arm that throws and the pumpkin); the scarecrow and the light of its carved head; the Halloween
        // decorations' jack-o'-lantern and its light (the cobweb is a mask, above); and the light inside the laughing moon's
        // face.
        "horseman_horse_0", "horseman_horse_1", "horseman_horse_2", "horseman_horse_3",
        "horseman_legs_0", "horseman_legs_1", "horseman_legs_2", "horseman_legs_3",
        "horseman_rider_body", "horseman_rider_arm", "horseman_rider_pumpkin", "horseman_cape",
        "scarecrow", "scarecrow_light",
        "deco_jack_o_lantern", "deco_jack_o_lantern_light",
        "moon_jack_o_lantern_laugh_light",
        "rainbow_arc", "lightning_bolt", "firework",
    )

    /**
     * Person art: every walk frame and every head, all of it blitted untinted.
     *
     * Matched by prefix rather than listed, because the set is generated -- kinds, seasons, poses and
     * pieces -- and a hand-written list of that size stops being read.
     * The property is the same one [fixedArtSprites] carries, and a person sprite that lost its
     * colours would fail it exactly as loudly.
     */
    private fun isPersonSprite(name: String) = name.startsWith("person_") && !isRegionMask(name)

    /**
     * A region weight mask -- the half of a person that **is** tinted.
     *
     * A person ships as fixed art plus one mask per colourable region, and the masks go through
     * `SpriteBlitter.drawTintedAdded`: white with the region's weight in the alpha, multiplied by
     * whatever that region is wearing and summed into the frame. So they belong on the *tinted*
     * side of this file's division and must be neutral -- which is exactly what
     * the two mask rules below now check of them, and why they are kept out of the "carries a
     * colour of its own" rule that governs finished art.
     *
     * Matched by name rather than listed for the reason [isPersonSprite] gives and more so: they are
     * generated, one per colourable region of every shape.
     */
    private fun isRegionMask(name: String) = name.matches(Regex("""person_.*_m[shtb]"""))

    /**
     * Sprites no call site reaches, so there is no blit to agree or disagree with.
     *
     * **Empty, and that is the point**: an exemption list is where an unreachable sprite goes to be
     * forgotten, and a sprite nothing blits is deleted rather than listed. `SpriteReachabilityTest`
     * fails on a new one.
     *
     * The person bases declared `usage: "orphan"` in the registry are unreachable and still ship;
     * they are not in this list because the registry carries the reason they ship, which is where
     * such a statement belongs.
     */
    private val orphans = emptyList<String>()

    // --- The masks -----------------------------------------------------------------------------

    /**
     * Every opaque pixel of a tinted sprite must be a neutral grey. A sprite whose channels
     * differ carries a hue of its own, and multiplying a hue by a second hue compounds them.
     */
    @Test
    fun `every tinted sprite is authored as a colourless mask`() {
        for (name in tintedSprites + spriteNames().filter(::isRegionMask)) {
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
        for (name in tintedSprites + spriteNames().filter(::isRegionMask)) {
            val mean = meanOpaqueLevel(name)
            assertTrue("$name averages $mean, too dark to be a tint mask", mean >= 220f)
        }
    }

    // --- The finished art ----------------------------------------------------------------------

    /**
     * Every fixed-art sprite must carry a colour somewhere. A colourless one blitted untinted is
     * a white or grey silhouette.
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
     * blit nothing checks, which is where both defects above would go unseen.
     */
    @Test
    fun `every shipped sprite is classified`() {
        val classified = (tintedSprites + fixedArtSprites + orphans).toSet()
        val shipped = spriteNames().toSet()

        val unclassified = shipped
            .filterNot { it in classified || isPersonSprite(it) || isRegionMask(it) }
            .sorted()
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
