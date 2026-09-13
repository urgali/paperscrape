package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * What each of the five building families is made of. **Generated** by
 * `tools/assets/buildings/build_neighbourhood.py`; edit that script, not this file.
 *
 * A family is a list of SLOTS, bottom-up. A slot holds the alternative PIECES the composer may
 * choose from for one instance, and how many times that piece repeats. A piece's coordinates are
 * its own -- relative to the piece's foot -- and the baseline climbs by the height of everything
 * already placed, which is how one table gives a street where two neighbours carry two
 * silhouettes rather than one facade repeated.
 *
 * [PartRole] is what a part is *for*, not how it looks:
 * - `FIXED` is art that never takes a tint (awnings, plaques, lanterns, stone steps, the busts'
 *   cream frames) plus the fixed term of every tinted card;
 * - `WALL_MASK` and `GLASS_MASK` are weight masks **summed** over the fixed layer at the blit,
 *   the system the people have used since v4.30 -- a weight interpolates, an index does not, and
 *   an index is what left a 63/255 halo when this was tried the other way round;
 * - `SNOW` is drawn only while `winterColorsEnabled`, as a layer *over* the roof and never as the
 *   roof tinted white;
 * - `LAMP` and `OCCUPANTS` are call-outs to the behaviours that already exist, at the piece's own
 *   declared coordinates, so a porch light and a bust in a window do not have to be re-found from
 *   the artwork.
 *
 * **Every tinted surface descends from one of the two editable colours of the object's category.**
 * A card is `w * wall + (1 - w) * k` with `k` only ink or white, and `w` is baked into the wall
 * mask; so there is no colour variant in this set and no colour of a piece's own. See the script's
 * own doc comment.
 */
internal enum class PartRole { FIXED, SNOW, WALL_MASK, GLASS_MASK, LAMP, OCCUPANTS }

internal class BuildingPart(val res: Int, val x: Float, val y: Float, val role: PartRole)

/** An opening a bust may stand in, or (with `h` = 0) a sill a light string may hang from. */
internal class BuildingWindow(val x: Float, val y: Float, val w: Float, val h: Float)

internal class BuildingPiece(
    /** How far the next piece's foot rises above this one's. */
    val height: Float,
    val parts: List<BuildingPart>,
    val windows: List<BuildingWindow>,
    val lights: List<BuildingWindow>,
    val smokeX: Float, val smokeY: Float,
    val beaconX: Float, val beaconY: Float,
)

internal class BuildingSlot(val options: List<BuildingPiece>, val repeatMin: Int, val repeatMax: Int)

internal class BuildingFamily(
    /** The height the piece stack is drawn in, which [SceneSpace.SceneVariant] scales to. */
    val unitsTall: Float,
    val shadowHalf: Float,
    val kind: WindowBuildingKind,
    val slots: List<BuildingSlot>,
)

internal object NeighbourhoodTable {

    private val HOUSE_SMALL_GROUND = BuildingPiece(
        28.00f,
        listOf(
        BuildingPart(R.drawable.house_small_ground_fx, -31.00f, -29.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_small_ground_mw, -31.00f, -29.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_small_ground_mg, -24.00f, -22.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(0, 24.50f, -15.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-23.00f, -22.00f, 14.00f, 13.00f)),
        listOf(BuildingWindow(-23.00f, -7.00f, 14.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    private val HOUSE_SMALL_STOREY = BuildingPiece(
        22.00f,
        listOf(
        BuildingPart(R.drawable.house_small_storey_fx, -30.00f, -22.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_small_storey_mw, -30.00f, -22.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_small_storey_mg, -23.00f, -18.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        ),
        listOf(BuildingWindow(-22.00f, -18.00f, 13.00f, 12.00f)),
        listOf(BuildingWindow(-22.00f, -4.00f, 13.00f, 0f), BuildingWindow(12.00f, -7.00f, 8.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    private val HOUSE_SMALL_ROOF_GABLE = BuildingPiece(
        30.00f,
        listOf(
        BuildingPart(R.drawable.house_small_roof_gable_fx, -35.00f, -31.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_small_roof_gable_mw, -35.00f, -31.00f, PartRole.WALL_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_small_roof_gable_snow_fx, -25.00f, -33.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        16.00f, -23.88f, 0.00f, 0.00f,
    )

    private val HOUSE_SMALL_ROOF_MANSARD = BuildingPiece(
        26.00f,
        listOf(
        BuildingPart(R.drawable.house_small_roof_mansard_fx, -34.00f, -33.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_small_roof_mansard_mw, -34.00f, -33.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_small_roof_mansard_mg, -11.00f, -11.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_small_roof_mansard_snow_fx, -22.00f, -29.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        14.50f, -31.00f, 0.00f, 0.00f,
    )

    private val HOUSE_LARGE_GROUND = BuildingPiece(
        28.00f,
        listOf(
        BuildingPart(R.drawable.house_large_ground_fx, -42.00f, -29.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_large_ground_mw, -42.00f, -29.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_large_ground_mg, -36.00f, -23.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(0, 39.50f, -15.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-36.00f, -22.00f, 14.00f, 13.00f), BuildingWindow(-10.00f, -22.00f, 14.00f, 13.00f)),
        listOf(BuildingWindow(-36.00f, -7.00f, 14.00f, 0f), BuildingWindow(-10.00f, -7.00f, 14.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    private val HOUSE_LARGE_STOREY = BuildingPiece(
        22.00f,
        listOf(
        BuildingPart(R.drawable.house_large_storey_fx, -42.00f, -22.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_large_storey_mw, -42.00f, -22.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_large_storey_mg, -35.00f, -18.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        ),
        listOf(BuildingWindow(-34.00f, -18.00f, 13.00f, 12.00f)),
        listOf(BuildingWindow(-34.00f, -4.00f, 13.00f, 0f), BuildingWindow(-6.00f, -7.00f, 8.00f, 0f), BuildingWindow(10.00f, -7.00f, 8.00f, 0f), BuildingWindow(26.00f, -7.00f, 8.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    private val HOUSE_LARGE_ROOF_GABLE = BuildingPiece(
        34.00f,
        listOf(
        BuildingPart(R.drawable.house_large_roof_gable_fx, -46.00f, -34.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_large_roof_gable_mw, -46.00f, -34.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_large_roof_gable_mg, -19.00f, -18.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_large_roof_gable_snow_fx, -33.00f, -37.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        25.00f, -23.52f, 0.00f, 0.00f,
    )

    private val HOUSE_LARGE_ROOF_MANSARD = BuildingPiece(
        30.00f,
        listOf(
        BuildingPart(R.drawable.house_large_roof_mansard_fx, -46.00f, -37.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_large_roof_mansard_mw, -46.00f, -37.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_large_roof_mansard_mg, -23.00f, -11.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_large_roof_mansard_snow_fx, -34.00f, -33.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        26.50f, -35.00f, 0.00f, 0.00f,
    )

    private val HOUSE_LARGE_ROOF_TURRET = BuildingPiece(
        44.00f,
        listOf(
        BuildingPart(R.drawable.house_large_roof_turret_gable_fx, -46.00f, -30.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_large_roof_turret_gable_mw, -46.00f, -30.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_large_roof_turret_tower_fx, 17.00f, -55.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_large_roof_turret_tower_mw, 17.00f, -55.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_large_roof_turret_tower_mg, 28.00f, -31.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_large_roof_turret_gable_snow_fx, -31.00f, -33.00f, PartRole.SNOW),
        BuildingPart(R.drawable.house_large_roof_turret_tower_snow_fx, 22.00f, -56.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(BuildingWindow(28.50f, -11.00f, 5.00f, 0f)),
        -31.00f, -20.00f, 0.00f, 0.00f,
    )

    private val TOWER_BODY = BuildingPiece(
        176.00f,
        listOf(
        BuildingPart(R.drawable.tower_tier1_fx, -34.00f, -109.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier1_mw, -34.00f, -109.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_tier1_mg, -28.00f, -24.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_tier2_fx, -27.00f, -148.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier2_mw, -27.00f, -148.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_tier3_fx, -19.00f, -177.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier3_mw, -19.00f, -177.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -101.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -101.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -89.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -89.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -77.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -77.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -53.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -53.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -28.00f, -65.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -28.00f, -65.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, 8.00f, -65.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, 8.00f, -65.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -143.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -143.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -119.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -119.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -7.00f, -132.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -7.00f, -132.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier3_fx, -15.00f, -171.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier3_mg, -15.00f, -171.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier3_fx, -15.00f, -159.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier3_mg, -15.00f, -159.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.tower_snow_left1_fx, -34.00f, -111.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_right1_fx, 25.00f, -111.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_left2_fx, -27.00f, -151.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_right2_fx, 17.00f, -151.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_top_fx, -19.00f, -179.00f, PartRole.SNOW),
        BuildingPart(0, -13.00f, -26.50f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-27.00f, -64.00f, 12.00f, 11.00f), BuildingWindow(9.00f, -64.00f, 12.00f, 11.00f), BuildingWindow(-6.00f, -131.00f, 12.00f, 11.00f)),
        listOf(BuildingWindow(-27.00f, -33.00f, 18.00f, 0f), BuildingWindow(9.00f, -33.00f, 18.00f, 0f), BuildingWindow(-27.00f, -93.00f, 18.00f, 0f), BuildingWindow(9.00f, -93.00f, 18.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    private val TOWER_CROWN_SPIRE = BuildingPiece(
        20.00f,
        listOf(
        BuildingPart(R.drawable.tower_crown_spire_fx, -10.00f, -22.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_crown_spire_mw, -10.00f, -7.00f, PartRole.WALL_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.tower_crown_spire_snow_fx, -10.00f, -10.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        0.00f, 0.00f, 0.00f, -24.00f,
    )

    private val TOWER_CROWN_DOME = BuildingPiece(
        18.00f,
        listOf(
        BuildingPart(R.drawable.tower_crown_dome_fx, -15.00f, -27.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_crown_dome_mw, -15.00f, -19.00f, PartRole.WALL_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.tower_crown_dome_snow_fx, -9.00f, -22.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        0.00f, 0.00f, 0.00f, -27.00f,
    )

    private val RESTAURANT_PAVILION = BuildingPiece(
        58.00f,
        listOf(
        BuildingPart(R.drawable.restaurant_pavilion_fx, -52.00f, -56.00f, PartRole.FIXED),
        BuildingPart(R.drawable.restaurant_pavilion_mw, -52.00f, -43.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.restaurant_pavilion_mg, -45.00f, -27.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.restaurant_pavilion_snow_fx, -52.00f, -59.00f, PartRole.SNOW),
        BuildingPart(0, 43.00f, -20.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-44.00f, -26.00f, 16.00f, 15.00f), BuildingWindow(-24.00f, -26.00f, 16.00f, 15.00f), BuildingWindow(-4.00f, -26.00f, 16.00f, 15.00f)),
        listOf(BuildingWindow(-44.00f, -9.50f, 16.00f, 0f), BuildingWindow(-4.00f, -9.50f, 16.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    private val BAR_SIGNBOARD = BuildingPiece(
        72.00f,
        listOf(
        BuildingPart(R.drawable.bar_signboard_fx, -34.00f, -73.00f, PartRole.FIXED),
        BuildingPart(R.drawable.bar_signboard_mw, -34.00f, -50.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.bar_signboard_mg, -24.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.bar_signboard_snow_fx, -34.00f, -75.00f, PartRole.SNOW),
        BuildingPart(0, -20.00f, -27.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-8.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(9.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(8.00f, -40.00f, 12.00f, 11.00f)),
        listOf(BuildingWindow(-8.00f, -6.50f, 30.00f, 0f), BuildingWindow(8.00f, -27.00f, 12.00f, 0f), BuildingWindow(-24.00f, -30.00f, 8.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    private val BAR_CHAMFER = BuildingPiece(
        52.00f,
        listOf(
        BuildingPart(R.drawable.bar_chamfer_fx, -36.00f, -53.00f, PartRole.FIXED),
        BuildingPart(R.drawable.bar_chamfer_mw, -36.00f, -53.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.bar_chamfer_mg, -31.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.bar_chamfer_snow_fx, -37.00f, -55.00f, PartRole.SNOW),
        BuildingPart(0, 20.00f, -27.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-30.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(-13.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(4.00f, -20.00f, 13.00f, 12.00f)),
        listOf(BuildingWindow(-30.00f, -6.50f, 30.00f, 0f), BuildingWindow(-26.00f, -31.00f, 7.00f, 0f), BuildingWindow(-8.00f, -31.00f, 7.00f, 0f), BuildingWindow(10.00f, -31.00f, 7.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
    )

    val FAMILIES: Map<SceneSpace.SceneVariant, BuildingFamily> = mapOf(
        SceneSpace.SceneVariant.HOUSE_SMALL to BuildingFamily(
            67.434f, 30.0f, WindowBuildingKind.HOUSE,
            listOf(
                BuildingSlot(listOf(HOUSE_SMALL_GROUND), 1, 1),
                BuildingSlot(listOf(HOUSE_SMALL_STOREY), 0, 1),
                BuildingSlot(listOf(HOUSE_SMALL_ROOF_GABLE, HOUSE_SMALL_ROOF_MANSARD), 1, 1),
            ),
        ),
        SceneSpace.SceneVariant.HOUSE_LARGE to BuildingFamily(
            88.976f, 42.0f, WindowBuildingKind.HOUSE,
            listOf(
                BuildingSlot(listOf(HOUSE_LARGE_GROUND), 1, 1),
                BuildingSlot(listOf(HOUSE_LARGE_STOREY), 1, 2),
                BuildingSlot(listOf(HOUSE_LARGE_ROOF_GABLE, HOUSE_LARGE_ROOF_MANSARD, HOUSE_LARGE_ROOF_TURRET), 1, 1),
            ),
        ),
        SceneSpace.SceneVariant.TOWER to BuildingFamily(
            182.634f, 35.0f, WindowBuildingKind.SKYSCRAPER,
            listOf(
                BuildingSlot(listOf(TOWER_BODY), 1, 1),
                BuildingSlot(listOf(TOWER_CROWN_SPIRE, TOWER_CROWN_DOME), 1, 1),
            ),
        ),
        SceneSpace.SceneVariant.RESTAURANT to BuildingFamily(
            96.0f, 50.0f, WindowBuildingKind.COMMERCIAL,
            listOf(
                BuildingSlot(listOf(RESTAURANT_PAVILION), 1, 1),
            ),
        ),
        SceneSpace.SceneVariant.BAR to BuildingFamily(
            90.146f, 33.0f, WindowBuildingKind.COMMERCIAL,
            listOf(
                BuildingSlot(listOf(BAR_SIGNBOARD, BAR_CHAMFER), 1, 1),
            ),
        ),
    )
}
