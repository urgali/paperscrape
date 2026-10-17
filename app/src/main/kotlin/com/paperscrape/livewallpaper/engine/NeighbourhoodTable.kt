package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * What each of the nine building families is made of, and the distant houses. **Generated** by
 * `tools/assets/buildings/build_neighbourhood.py`; edit that script, not this file.
 *
 * A family is a list of SLOTS, bottom-up. A slot holds the alternative PIECES the composer may
 * choose from for one instance, and how many times that piece repeats. A piece's coordinates are
 * its own -- relative to the piece's foot -- and the baseline climbs by the height of everything
 * already placed, which is how one table gives a street where two neighbours carry two
 * silhouettes rather than one facade repeated.
 *
 * [PartRole] is what a part is *for*, not how it looks:
 * - `FIXED` is art that never takes a tint (awnings, plaques, the lanterns' brackets, stone steps,
 *   the busts' cream frames) plus the fixed term of every tinted card;
 * - `WALL_MASK` and `GLASS_MASK` are weight masks **summed** over the fixed layer at the blit,
 *   the system the people have used since v4.30 -- a weight interpolates, an index does not, and
 *   an index is what left a 63/255 halo when this was tried the other way round;
 * - `SNOW` is drawn only while `winterColorsEnabled`, as a layer *over* the roof and never as the
 *   roof tinted white;
 * - `LAMP` and `OCCUPANTS` are call-outs to the behaviours that already exist, at the piece's own
 *   declared coordinates, so a porch light and a bust in a window do not have to be re-found from
 *   the artwork.
 *
 * **Every wall surface descends from one of the two editable colours of the object's category**;
 * the glass mask takes the scene's fixed day/night glass colours (`SceneObjectRenderer.windowGlassColor`;
 * at night the dark glass of `unlitWindowGlassColor` for a business while it is shut, and for a house
 * with its lit panes laid over it as flat cards, v5.12). A card is `w * wall + (1 - w) * k` with `k` only ink
 * or white, and `w` is baked into the wall mask; so there is no colour variant in this set and no
 * colour of a piece's own. See the script's own doc comment.
 */
internal enum class PartRole { FIXED, SNOW, WALL_MASK, GLASS_MASK, LAMP, OCCUPANTS }

internal class BuildingPart(val res: Int, val x: Float, val y: Float, val role: PartRole)

/**
 * An opening a bust may stand in, a pane of a house's glass (`BuildingPiece.panes`, v5.12), or (with
 * `h` = 0) a sill a light string may hang from.
 */
internal class BuildingWindow(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * A piece's front door (v5.14), in the piece's own units: its box ([x], [y], [w], [h] -- the rectangle the door is
 * drawn in), the outline of its card as the scissors cut it ([outlineX], [outlineY], convex but for the wobble's small dents), and how far its
 * shadow paper is offset ([reliefX], [reliefY]; 0 for the towers' glass doors, which are an opening in a framed
 * card). Where the Christmas wreath hangs, and what a shut building's shutter covers: the card and its shadow, exactly.
 */
internal class BuildingDoor(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val outlineX: FloatArray, val outlineY: FloatArray,
    val reliefX: Float, val reliefY: Float,
)

internal class BuildingPiece(
    /** How far the next piece's foot rises above this one's. */
    val height: Float,
    val parts: List<BuildingPart>,
    val windows: List<BuildingWindow>,
    val lights: List<BuildingWindow>,
    val smokeX: Float, val smokeY: Float,
    val beaconX: Float, val beaconY: Float,
    /**
     * A house piece's panes of glass (v5.12), each one a window its night may light or leave dark:
     * first the [windows], in their order -- pane `k` is window `k` for every `k` below
     * `windows.size` -- then the rest, the dormers and the turret's small windows. Empty for the other
     * buildings, which light all their glass together while they are open.
     */
    val panes: List<BuildingWindow> = emptyList(),
    /**
     * How many of its building's people this piece brings, as a count of windows (v5.12): its own
     * window count, except where a piece gained windows in v5.12 -- the storeys, the school's upper
     * floor, the bars' -- and brings the people it did before; and the shop and the two stations (v5.13), which
     * bring fewer than their windows, at the shops' rate (`civic.py`). The number of people at a building's
     * windows is one of the scene's quantities the maintainer keeps, and the table he approved said it
     * stays (*«il resto si a tutto»*, 2026-10-09): it is dealt over this
     * ([NeighbourhoodComposer.Deal.peopleWindows]), the windows they stand at over all of them.
     */
    val people: Int = windows.size,
    /** The piece's front door, where it has one (v5.14): see [BuildingDoor]. */
    val door: BuildingDoor? = null,
    /**
     * Where a roof string's bulbs hang on a building's top piece and on a distant house (v5.14): its roofline -- the
     * top outline of what the piece draws, parts narrower than 9 units (a chimney, a pole, a spire's needle) left
     * out -- every 6 units along it. Empty for the other pieces.
     */
    val roofX: FloatArray = floatArrayOf(),
    val roofY: FloatArray = floatArrayOf(),
    /** The highest point the piece draws, where a tower's star stands (v5.14); 0, 0 where [roofX] is empty. */
    val topX: Float = 0f,
    val topY: Float = 0f,
)

/**
 * One slot of a family: the [options] the deal chooses between and how many times the piece repeats.
 *
 * [heights], where a slot has them, are alternatives the deal does **not** choose between: each
 * instance takes one by a coin of its own (`NeighbourhoodComposer.heightIndex`), in place of the one
 * piece in [options]. The towers' three bodies (v5.11) vary a skyline's height this way without
 * growing the catalogue `SilhouetteDeal` deals from, which would have re-dealt every theme's crowns.
 */
internal class BuildingSlot(
    val options: List<BuildingPiece>,
    val repeatMin: Int,
    val repeatMax: Int,
    val heights: List<BuildingPiece> = emptyList(),
)

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
        panes = listOf(BuildingWindow(-23.00f, -22.00f, 14.00f, 13.00f)),
        door = BuildingDoor(8.00f, -21.00f, 12.00f, 19.00f, floatArrayOf(7.36f, 11.12f, 14.47f, 16.46f, 19.94f, 20.50f, 8.23f), floatArrayOf(-16.47f, -19.93f, -21.01f, -20.12f, -17.17f, -1.85f, -1.87f), 1.50f, 2.00f),
    )

    private val HOUSE_SMALL_STOREY = BuildingPiece(
        22.00f,
        listOf(
        BuildingPart(R.drawable.house_small_storey_fx, -30.00f, -22.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_small_storey_mw, -30.00f, -22.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_small_storey_mg, -23.00f, -19.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        ),
        listOf(BuildingWindow(-22.00f, -18.00f, 13.00f, 12.00f), BuildingWindow(9.00f, -18.00f, 13.00f, 12.00f)),
        listOf(BuildingWindow(-22.00f, -4.00f, 13.00f, 0f), BuildingWindow(9.00f, -4.00f, 13.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 1,
        panes = listOf(BuildingWindow(-22.00f, -18.00f, 13.00f, 12.00f), BuildingWindow(9.00f, -18.00f, 13.00f, 12.00f)),
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
        roofX = floatArrayOf(-34.00f, -29.31f, -24.62f, -20.03f, -15.34f, -10.65f, -5.96f, -0.14f, 5.66f, 10.26f, 15.75f, 20.14f, 21.54f, 26.13f, 30.82f, 35.00f),
        roofY = floatArrayOf(-1.00f, -5.02f, -9.05f, -13.30f, -17.33f, -21.35f, -25.37f, -26.67f, -25.34f, -21.08f, -19.00f, -16.90f, -11.13f, -6.87f, -2.85f, 1.67f),
        topX = -0.17f, topY = -30.33f,
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
        panes = listOf(BuildingWindow(-10.52f, -10.80f, 5.04f, 5.20f)),
        roofX = floatArrayOf(-33.00f, -30.46f, -27.91f, -25.99f, -22.88f, -17.77f, -11.46f, -5.30f, 1.00f, 7.17f, 13.47f, 19.64f, 23.76f, 26.71f, 28.64f, 31.32f, 34.00f),
        roofY = floatArrayOf(-1.33f, -7.09f, -12.84f, -18.69f, -24.12f, -26.33f, -26.33f, -26.00f, -26.00f, -25.67f, -25.67f, -25.33f, -21.48f, -15.98f, -10.06f, -4.36f, 1.33f),
        topX = 14.33f, topY = -32.00f,
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
        panes = listOf(BuildingWindow(-36.00f, -22.00f, 14.00f, 13.00f), BuildingWindow(-10.00f, -22.00f, 14.00f, 13.00f)),
        door = BuildingDoor(24.00f, -21.00f, 12.00f, 19.00f, floatArrayOf(24.24f, 26.39f, 29.31f, 32.96f, 36.57f, 36.69f, 23.94f), floatArrayOf(-16.91f, -20.08f, -20.63f, -20.10f, -17.33f, -1.62f, -1.84f), 1.50f, 2.00f),
    )

    private val HOUSE_LARGE_STOREY = BuildingPiece(
        22.00f,
        listOf(
        BuildingPart(R.drawable.house_large_storey_fx, -42.00f, -22.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_large_storey_mw, -42.00f, -22.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_large_storey_mg, -35.00f, -18.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        ),
        listOf(BuildingWindow(-34.00f, -18.00f, 13.00f, 12.00f), BuildingWindow(-6.50f, -18.00f, 13.00f, 12.00f), BuildingWindow(21.00f, -18.00f, 13.00f, 12.00f)),
        listOf(BuildingWindow(-34.00f, -4.00f, 13.00f, 0f), BuildingWindow(-6.50f, -4.00f, 13.00f, 0f), BuildingWindow(21.00f, -4.00f, 13.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 1,
        panes = listOf(BuildingWindow(-34.00f, -18.00f, 13.00f, 12.00f), BuildingWindow(-6.50f, -18.00f, 13.00f, 12.00f), BuildingWindow(21.00f, -18.00f, 13.00f, 12.00f)),
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
        panes = listOf(BuildingWindow(-18.80f, -17.65f, 5.60f, 5.85f)),
        roofX = floatArrayOf(-45.67f, -40.94f, -36.32f, -31.69f, -26.97f, -22.58f, -18.68f, -13.47f, -8.85f, -4.04f, 2.09f, 7.27f, 12.00f, 16.62f, 21.35f, 27.48f, 29.56f, 32.87f, 37.56f, 42.22f, 46.33f),
        roofY = floatArrayOf(0.33f, -3.06f, -6.68f, -10.31f, -13.70f, -17.45f, -21.65f, -23.86f, -27.49f, -30.67f, -30.67f, -28.39f, -25.00f, -21.38f, -18.00f, -18.00f, -13.54f, -9.13f, -5.67f, -2.12f, 2.00f),
        topX = 0.00f, topY = -34.00f,
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
        panes = listOf(BuildingWindow(-22.52f, -10.80f, 5.04f, 5.20f), BuildingWindow(11.48f, -10.80f, 5.04f, 5.20f)),
        roofX = floatArrayOf(-45.33f, -42.91f, -40.49f, -38.57f, -37.30f, -34.32f, -29.02f, -23.04f, -16.93f, -10.81f, -4.83f, 1.28f, 7.40f, 13.38f, 19.49f, 25.47f, 31.59f, 35.29f, 38.14f, 39.65f, 41.92f, 44.24f, 46.67f),
        roofY = floatArrayOf(0.00f, -5.60f, -11.20f, -16.92f, -22.73f, -28.02f, -29.33f, -29.67f, -29.67f, -29.67f, -30.00f, -30.00f, -30.00f, -30.33f, -30.33f, -30.67f, -30.67f, -26.43f, -21.05f, -15.22f, -9.57f, -3.93f, 1.67f),
        topX = 26.33f, topY = -36.00f,
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
        panes = listOf(BuildingWindow(28.50f, -30.00f, 5.00f, 7.00f), BuildingWindow(28.50f, -18.00f, 5.00f, 7.00f)),
        roofX = floatArrayOf(-45.33f, -40.77f, -36.21f, -34.14f, -30.46f, -24.98f, -20.43f, -15.81f, -10.74f, -4.71f, -0.04f, 4.60f, 9.18f, 13.74f, 17.35f, 17.65f, 20.29f, 23.78f, 28.22f, 34.25f, 38.16f, 41.84f, 45.33f),
        roofY = floatArrayOf(-0.67f, -4.23f, -7.79f, -12.84f, -15.33f, -16.67f, -20.24f, -23.67f, -26.00f, -26.00f, -22.70f, -19.33f, -15.82f, -12.26f, -11.03f, -35.17f, -40.29f, -45.12f, -48.33f, -48.33f, -44.18f, -39.49f, -34.67f),
        topX = 30.50f, topY = -54.00f,
    )

    private val TOWER_BODY = BuildingPiece(
        188.00f,
        listOf(
        BuildingPart(R.drawable.tower_tier1_fx, -34.00f, -121.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier1_mw, -34.00f, -121.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_tier1_mg, -9.00f, -24.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_tier2_fx, -27.00f, -160.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier2_mw, -27.00f, -160.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_tier3_fx, -19.00f, -189.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier3_mw, -19.00f, -189.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_bay_door_fx, -30.00f, -23.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_door_mg, -30.00f, -23.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_door_fx, 18.00f, -23.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_door_mg, 18.00f, -23.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -31.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -31.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -15.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -15.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, 1.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, 1.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, 17.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, 17.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -113.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -113.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -101.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -101.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -89.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -89.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -77.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -77.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -65.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -65.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -53.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -53.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -155.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -155.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -143.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -143.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -131.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -131.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier3_fx, -15.00f, -183.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier3_mg, -15.00f, -183.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier3_fx, -15.00f, -171.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier3_mg, -15.00f, -171.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.tower_snow_left1_fx, -34.00f, -123.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_right1_fx, 25.00f, -123.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_left2_fx, -27.00f, -163.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_right2_fx, 17.00f, -163.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_top_fx, -19.00f, -191.00f, PartRole.SNOW),
        BuildingPart(0, -13.00f, -26.50f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-30.00f, -22.00f, 12.00f, 12.00f), BuildingWindow(18.00f, -22.00f, 12.00f, 12.00f), BuildingWindow(-30.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(-14.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(2.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(18.00f, -40.00f, 12.00f, 11.00f)),
        listOf(BuildingWindow(-27.00f, -45.00f, 18.00f, 0f), BuildingWindow(9.00f, -45.00f, 18.00f, 0f), BuildingWindow(-27.00f, -105.00f, 18.00f, 0f), BuildingWindow(9.00f, -105.00f, 18.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        door = BuildingDoor(-8.00f, -23.00f, 16.00f, 21.00f, floatArrayOf(-8.00f, 8.00f, 8.00f, -8.00f), floatArrayOf(-23.00f, -23.00f, -2.00f, -2.00f), 0.00f, 0.00f),
    )

    private val TOWER_BODY_SHORT = BuildingPiece(
        148.00f,
        listOf(
        BuildingPart(R.drawable.tower_tier1_short_fx, -34.00f, -109.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier1_short_mw, -34.00f, -109.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_tier1_short_mg, -9.00f, -24.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_tier2_fx, -27.00f, -148.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier2_mw, -27.00f, -148.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_bay_door_fx, -30.00f, -23.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_door_mg, -30.00f, -23.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_door_fx, 18.00f, -23.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_door_mg, 18.00f, -23.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -31.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -31.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -15.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -15.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, 1.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, 1.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, 17.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, 17.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -101.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -101.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -89.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -89.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -77.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -77.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -65.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -65.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -53.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -53.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -143.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -143.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -131.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -131.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -119.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -119.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.tower_snow_left1_fx, -34.00f, -111.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_right1_fx, 25.00f, -111.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_top_wide_fx, -27.00f, -151.00f, PartRole.SNOW),
        BuildingPart(0, -13.00f, -26.50f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-30.00f, -22.00f, 12.00f, 12.00f), BuildingWindow(18.00f, -22.00f, 12.00f, 12.00f), BuildingWindow(-30.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(-14.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(2.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(18.00f, -40.00f, 12.00f, 11.00f)),
        listOf(BuildingWindow(-27.00f, -45.00f, 18.00f, 0f), BuildingWindow(9.00f, -45.00f, 18.00f, 0f), BuildingWindow(-27.00f, -93.00f, 18.00f, 0f), BuildingWindow(9.00f, -93.00f, 18.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        door = BuildingDoor(-8.00f, -23.00f, 16.00f, 21.00f, floatArrayOf(-8.00f, 8.00f, 8.00f, -8.00f), floatArrayOf(-23.00f, -23.00f, -2.00f, -2.00f), 0.00f, 0.00f),
    )

    private val TOWER_BODY_TALL = BuildingPiece(
        224.00f,
        listOf(
        BuildingPart(R.drawable.tower_tier1_tall_fx, -34.00f, -145.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier1_tall_mw, -34.00f, -145.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_tier1_tall_mg, -9.00f, -24.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_tier2_tall_fx, -27.00f, -197.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier2_tall_mw, -27.00f, -197.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_tier3_fx, -19.00f, -225.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_tier3_mw, -19.00f, -225.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.tower_bay_door_fx, -30.00f, -23.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_door_mg, -30.00f, -23.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_door_fx, 18.00f, -23.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_door_mg, 18.00f, -23.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -31.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -31.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, -15.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, -15.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, 1.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, 1.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_bay_fx, 17.00f, -41.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_bay_mg, 17.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -137.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -137.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -125.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -125.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -113.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -113.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -101.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -101.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -89.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -89.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -77.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -77.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -65.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -65.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier1_fx, -28.00f, -53.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier1_mg, -28.00f, -53.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -191.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -191.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -179.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -179.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -167.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -167.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier2_fx, -23.00f, -155.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier2_mg, -23.00f, -155.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier3_fx, -15.00f, -219.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier3_mg, -15.00f, -219.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.tower_row_tier3_fx, -15.00f, -207.00f, PartRole.FIXED),
        BuildingPart(R.drawable.tower_row_tier3_mg, -15.00f, -207.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.tower_snow_left1_fx, -34.00f, -147.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_right1_fx, 25.00f, -147.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_left2_fx, -27.00f, -199.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_right2_fx, 17.00f, -199.00f, PartRole.SNOW),
        BuildingPart(R.drawable.tower_snow_top_fx, -19.00f, -227.00f, PartRole.SNOW),
        BuildingPart(0, -13.00f, -26.50f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-30.00f, -22.00f, 12.00f, 12.00f), BuildingWindow(18.00f, -22.00f, 12.00f, 12.00f), BuildingWindow(-30.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(-14.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(2.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(18.00f, -40.00f, 12.00f, 11.00f)),
        listOf(BuildingWindow(-27.00f, -45.00f, 18.00f, 0f), BuildingWindow(9.00f, -45.00f, 18.00f, 0f), BuildingWindow(-27.00f, -129.00f, 18.00f, 0f), BuildingWindow(9.00f, -129.00f, 18.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        door = BuildingDoor(-8.00f, -23.00f, 16.00f, 21.00f, floatArrayOf(-8.00f, 8.00f, 8.00f, -8.00f), floatArrayOf(-23.00f, -23.00f, -2.00f, -2.00f), 0.00f, 0.00f),
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
        roofX = floatArrayOf(-9.67f, -3.50f, 2.67f, 8.85f, 12.00f),
        roofY = floatArrayOf(-6.67f, -6.67f, -6.67f, -6.67f, -3.33f),
        topX = -0.17f, topY = -22.00f,
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
        roofX = floatArrayOf(-15.00f, -12.55f, -9.31f, -3.81f, 2.51f, 8.28f, 12.37f, 15.08f, 16.67f),
        roofY = floatArrayOf(-5.67f, -10.78f, -16.02f, -17.67f, -17.67f, -16.33f, -12.33f, -6.84f, -1.33f),
        topX = -0.17f, topY = -26.67f,
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
        door = BuildingDoor(26.00f, -22.00f, 12.00f, 20.00f, floatArrayOf(26.53f, 38.00f, 37.42f, 38.47f, 26.26f, 25.52f), floatArrayOf(-21.20f, -22.03f, -21.09f, -1.30f, -2.15f, -21.70f), 1.90f, 2.60f),
        roofX = floatArrayOf(-51.33f, -46.70f, -40.61f, -34.52f, -28.43f, -22.34f, -16.25f, -12.86f, -9.95f, -5.22f, 0.87f, 6.68f, 10.98f, 14.40f, 18.51f, 24.60f, 30.69f, 36.78f, 42.87f, 48.96f, 52.33f),
        roofY = floatArrayOf(-40.67f, -42.67f, -42.67f, -42.67f, -42.67f, -42.67f, -42.67f, -46.56f, -51.72f, -55.00f, -55.00f, -54.33f, -50.35f, -45.42f, -42.67f, -42.67f, -42.67f, -42.67f, -42.67f, -42.67f, -39.00f),
        topX = 0.83f, topY = -55.67f,
    )

    private val BAR_SIGNBOARD = BuildingPiece(
        72.00f,
        listOf(
        BuildingPart(R.drawable.bar_signboard_fx, -34.00f, -73.00f, PartRole.FIXED),
        BuildingPart(R.drawable.bar_signboard_mw, -34.00f, -50.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.bar_signboard_mg, -23.00f, -41.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.bar_signboard_snow_fx, -34.00f, -75.00f, PartRole.SNOW),
        BuildingPart(0, -20.00f, -27.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-8.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(9.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(8.00f, -40.00f, 12.00f, 11.00f), BuildingWindow(-12.00f, -40.00f, 12.00f, 11.00f)),
        listOf(BuildingWindow(-8.00f, -6.50f, 30.00f, 0f), BuildingWindow(8.00f, -27.00f, 12.00f, 0f), BuildingWindow(-12.00f, -27.00f, 12.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 3,
        door = BuildingDoor(-26.00f, -22.00f, 12.00f, 20.00f, floatArrayOf(-25.14f, -15.55f, -14.49f, -14.14f, -26.49f, -26.46f), floatArrayOf(-22.43f, -22.23f, -20.86f, -1.46f, -2.06f, -21.03f), 1.50f, 2.00f),
        roofX = floatArrayOf(-33.33f, -28.18f, -21.89f, -15.60f, -9.31f, -8.07f, -7.89f, -7.75f, -5.31f, 0.84f, 7.12f, 13.41f, 19.56f, 25.70f, 27.71f, 27.82f, 27.92f, 29.74f, 34.33f),
        roofY = floatArrayOf(-48.00f, -49.67f, -49.67f, -49.67f, -49.67f, -54.97f, -61.25f, -67.54f, -71.67f, -72.00f, -72.00f, -72.00f, -72.33f, -71.97f, -66.79f, -60.50f, -54.21f, -49.67f, -47.67f),
        topX = 19.83f, topY = -72.33f,
    )

    private val BAR_CHAMFER = BuildingPiece(
        52.00f,
        listOf(
        BuildingPart(R.drawable.bar_chamfer_fx, -36.00f, -53.00f, PartRole.FIXED),
        BuildingPart(R.drawable.bar_chamfer_mw, -36.00f, -53.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.bar_chamfer_mg, -31.00f, -42.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.bar_chamfer_snow_fx, -37.00f, -55.00f, PartRole.SNOW),
        BuildingPart(0, 20.00f, -27.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-30.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(-13.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(4.00f, -20.00f, 13.00f, 12.00f), BuildingWindow(-10.00f, -41.00f, 11.00f, 11.00f), BuildingWindow(6.00f, -41.00f, 11.00f, 11.00f)),
        listOf(BuildingWindow(-30.00f, -6.50f, 30.00f, 0f), BuildingWindow(-10.00f, -28.50f, 11.00f, 0f), BuildingWindow(6.00f, -28.50f, 11.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 3,
        door = BuildingDoor(19.00f, -22.00f, 12.00f, 20.00f, floatArrayOf(20.53f, 30.71f, 31.38f, 30.60f, 19.46f, 19.17f), floatArrayOf(-22.54f, -22.11f, -21.54f, -2.47f, -2.60f, -21.36f), 1.50f, 2.00f),
        roofX = floatArrayOf(-35.67f, -29.25f, -22.83f, -16.41f, -10.13f, -3.71f, 2.71f, 9.00f, 15.42f, 21.40f, 25.93f, 30.28f, 34.82f, 37.00f),
        roofY = floatArrayOf(-51.67f, -51.67f, -51.67f, -51.67f, -52.00f, -52.00f, -52.00f, -52.33f, -52.33f, -51.27f, -46.73f, -42.05f, -37.51f, -31.67f),
        topX = 13.00f, topY = -52.33f,
    )

    private val SCHOOL = BuildingPiece(
        72.00f,
        listOf(
        BuildingPart(R.drawable.school_fx, -54.00f, -74.00f, PartRole.FIXED),
        BuildingPart(R.drawable.school_mw, -54.00f, -74.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.school_mg, -49.00f, -46.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.school_snow_fx, -54.00f, -76.00f, PartRole.SNOW),
        BuildingPart(0, 0.00f, -28.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-48.00f, -24.00f, 13.00f, 15.00f), BuildingWindow(-30.00f, -24.00f, 13.00f, 15.00f), BuildingWindow(17.00f, -24.00f, 13.00f, 15.00f), BuildingWindow(35.00f, -24.00f, 13.00f, 15.00f), BuildingWindow(-48.00f, -45.00f, 11.00f, 11.00f), BuildingWindow(-32.00f, -45.00f, 11.00f, 11.00f), BuildingWindow(21.00f, -45.00f, 11.00f, 11.00f), BuildingWindow(37.00f, -45.00f, 11.00f, 11.00f)),
        listOf(BuildingWindow(-48.00f, -32.50f, 27.00f, 0f), BuildingWindow(21.00f, -32.50f, 27.00f, 0f), BuildingWindow(-48.00f, -7.50f, 13.00f, 0f), BuildingWindow(-30.00f, -7.50f, 13.00f, 0f), BuildingWindow(17.00f, -7.50f, 13.00f, 0f), BuildingWindow(35.00f, -7.50f, 13.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 4,
        door = BuildingDoor(-7.00f, -23.00f, 14.00f, 21.00f, floatArrayOf(-5.40f, 6.78f, 7.72f, 7.19f, -7.22f, -6.58f), floatArrayOf(-22.66f, -23.68f, -21.94f, -2.80f, -2.64f, -21.96f), 1.70f, 2.30f),
        roofX = floatArrayOf(-53.33f, -47.29f, -41.24f, -35.20f, -29.15f, -23.10f, -17.06f, -11.98f, -11.83f, -11.68f, -9.11f, -4.70f, 1.34f, 7.28f, 10.49f, 13.02f, 13.20f, 14.80f, 20.84f, 26.89f, 32.80f, 38.84f, 44.89f, 50.93f, 54.67f),
        roofY = floatArrayOf(-52.33f, -52.33f, -52.33f, -52.33f, -52.33f, -52.33f, -52.33f, -53.32f, -59.36f, -65.41f, -70.79f, -73.67f, -73.67f, -73.39f, -68.35f, -62.96f, -56.91f, -52.33f, -52.33f, -52.33f, -52.67f, -52.67f, -52.67f, -52.67f, -49.33f),
        topX = -0.17f, topY = -73.67f,
    )

    private val SHOP = BuildingPiece(
        56.00f,
        listOf(
        BuildingPart(R.drawable.shop_fx, -33.00f, -56.00f, PartRole.FIXED),
        BuildingPart(R.drawable.shop_mw, -33.00f, -56.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.shop_mg, -24.00f, -39.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.shop_snow_fx, -34.00f, -58.00f, PartRole.SNOW),
        BuildingPart(0, -22.00f, -27.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-11.00f, -21.00f, 11.00f, 13.00f), BuildingWindow(3.00f, -21.00f, 11.00f, 13.00f), BuildingWindow(17.00f, -21.00f, 11.00f, 13.00f), BuildingWindow(-24.00f, -38.00f, 11.00f, 11.00f), BuildingWindow(13.00f, -38.00f, 11.00f, 11.00f)),
        listOf(BuildingWindow(-11.00f, -6.50f, 39.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 3,
        door = BuildingDoor(-28.00f, -22.00f, 12.00f, 20.00f, floatArrayOf(-26.42f, -17.31f, -15.68f, -15.58f, -28.39f, -28.03f), floatArrayOf(-21.50f, -21.41f, -20.69f, -1.58f, -1.50f, -20.78f), 1.60f, 2.20f),
        roofX = floatArrayOf(-33.00f, -26.66f, -20.32f, -14.49f, -12.22f, -7.34f, -1.42f, 4.93f, 10.02f, 14.28f, 17.80f, 24.14f, 30.49f, 34.33f),
        roofY = floatArrayOf(-44.00f, -44.00f, -44.00f, -44.86f, -50.45f, -53.99f, -55.00f, -55.00f, -51.98f, -47.72f, -44.00f, -44.00f, -44.00f, -40.67f),
        topX = -0.17f, topY = -55.67f,
    )

    private val FIRE_STATION = BuildingPiece(
        84.00f,
        listOf(
        BuildingPart(R.drawable.fire_station_tower_fx, 28.00f, -91.00f, PartRole.FIXED),
        BuildingPart(R.drawable.fire_station_tower_mw, 28.00f, -87.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.fire_station_tower_mg, 35.00f, -67.00f, PartRole.GLASS_MASK),
        BuildingPart(R.drawable.fire_station_fx, -52.00f, -61.00f, PartRole.FIXED),
        BuildingPart(R.drawable.fire_station_mw, -52.00f, -61.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.fire_station_mg, -48.00f, -56.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.fire_station_tower_snow_fx, 29.00f, -89.00f, PartRole.SNOW),
        BuildingPart(R.drawable.fire_station_snow_fx, -52.00f, -63.00f, PartRole.SNOW),
        BuildingPart(0, 38.00f, -25.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-47.00f, -55.00f, 11.00f, 11.00f), BuildingWindow(-31.00f, -55.00f, 11.00f, 11.00f), BuildingWindow(-3.00f, -55.00f, 11.00f, 11.00f), BuildingWindow(13.00f, -55.00f, 11.00f, 11.00f)),
        listOf(BuildingWindow(-47.00f, -42.50f, 27.00f, 0f), BuildingWindow(-3.00f, -42.50f, 27.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 2,
        door = BuildingDoor(33.00f, -21.00f, 10.00f, 19.00f, floatArrayOf(34.33f, 41.60f, 43.50f, 42.50f, 33.08f, 33.68f), floatArrayOf(-21.80f, -21.42f, -20.78f, -2.02f, -1.98f, -20.67f), 1.70f, 2.30f),
        roofX = floatArrayOf(-51.33f, -45.36f, -39.24f, -33.13f, -27.01f, -20.90f, -14.78f, -8.67f, -2.55f, 3.56f, 9.68f, 15.79f, 21.91f, 28.02f, 28.66f, 32.97f, 38.94f, 44.36f, 48.33f),
        roofY = floatArrayOf(-59.67f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -60.00f, -78.03f, -81.97f, -82.33f, -80.67f, -76.67f),
        topX = 37.83f, topY = -90.67f,
    )

    private val POLICE_STATION = BuildingPiece(
        70.00f,
        listOf(
        BuildingPart(R.drawable.police_station_fx, -52.00f, -71.00f, PartRole.FIXED),
        BuildingPart(R.drawable.police_station_mw, -52.00f, -65.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.police_station_mg, -16.00f, -55.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.police_station_snow_fx, -52.00f, -67.00f, PartRole.SNOW),
        BuildingPart(0, -17.00f, -20.00f, PartRole.LAMP),
        ),
        listOf(BuildingWindow(-15.00f, -24.00f, 12.00f, 13.00f), BuildingWindow(1.00f, -24.00f, 12.00f, 13.00f), BuildingWindow(-15.00f, -54.00f, 11.00f, 11.00f), BuildingWindow(1.00f, -54.00f, 11.00f, 11.00f), BuildingWindow(17.00f, -54.00f, 11.00f, 11.00f), BuildingWindow(33.00f, -54.00f, 11.00f, 11.00f)),
        listOf(BuildingWindow(-15.00f, -9.50f, 12.00f, 0f), BuildingWindow(1.00f, -9.50f, 12.00f, 0f)),
        0.00f, 0.00f, 0.00f, 0.00f,
        people = 3,
        door = BuildingDoor(-38.00f, -24.00f, 14.00f, 21.40f, floatArrayOf(-37.62f, -25.06f, -24.25f, -24.75f, -37.53f, -37.70f), floatArrayOf(-23.29f, -23.17f, -22.85f, -3.31f, -2.02f, -23.60f), 1.70f, 2.30f),
        roofX = floatArrayOf(-51.00f, -45.71f, -39.57f, -33.43f, -27.28f, -21.14f, -15.00f, -9.27f, -5.06f, 1.08f, 7.23f, 11.02f, 17.16f, 23.30f, 29.45f, 35.59f, 41.74f, 47.88f, 52.00f),
        roofY = floatArrayOf(-63.00f, -64.33f, -64.33f, -64.33f, -64.33f, -64.33f, -64.33f, -64.81f, -67.00f, -67.00f, -67.00f, -64.33f, -64.33f, -64.33f, -64.33f, -64.33f, -64.33f, -64.33f, -62.00f),
        topX = -0.17f, topY = -71.00f,
    )

    private val HOUSE_DISTANT_COTTAGE = BuildingPiece(
        23.00f,
        listOf(
        BuildingPart(R.drawable.house_distant_cottage_fx, -12.00f, -24.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_distant_cottage_mw, -12.00f, -24.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_distant_cottage_mg, -6.00f, -11.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_distant_cottage_snow_fx, -10.00f, -25.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        0.00f, 0.00f, 0.00f, 0.00f,
        roofX = floatArrayOf(-11.00f, -6.94f, -3.25f, 2.88f, 7.37f, 11.67f),
        roofY = floatArrayOf(-13.00f, -17.55f, -20.33f, -20.12f, -15.96f, -11.67f),
        topX = -0.50f, topY = -23.00f,
    )

    private val HOUSE_DISTANT_CHALET = BuildingPiece(
        22.00f,
        listOf(
        BuildingPart(R.drawable.house_distant_chalet_fx, -15.00f, -23.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_distant_chalet_mw, -15.00f, -23.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_distant_chalet_mg, -10.00f, -16.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_distant_chalet_snow_fx, -13.00f, -24.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        0.00f, 0.00f, 0.00f, 0.00f,
        roofX = floatArrayOf(-14.67f, -10.06f, -5.35f, 0.61f, 6.21f, 10.82f, 15.33f),
        roofY = floatArrayOf(-10.33f, -14.28f, -17.98f, -18.67f, -17.12f, -13.18f, -9.33f),
        topX = 0.00f, topY = -22.33f,
    )

    private val HOUSE_DISTANT_TALL = BuildingPiece(
        30.00f,
        listOf(
        BuildingPart(R.drawable.house_distant_tall_fx, -10.00f, -31.00f, PartRole.FIXED),
        BuildingPart(R.drawable.house_distant_tall_mw, -10.00f, -31.00f, PartRole.WALL_MASK),
        BuildingPart(R.drawable.house_distant_tall_mg, -3.00f, -19.00f, PartRole.GLASS_MASK),
        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),
        BuildingPart(R.drawable.house_distant_tall_snow_fx, -8.00f, -32.00f, PartRole.SNOW),
        ),
        listOf(),
        listOf(),
        0.00f, 0.00f, 0.00f, 0.00f,
        roofX = floatArrayOf(-9.00f, -3.19f, 4.55f, 9.67f),
        roofY = floatArrayOf(-21.00f, -25.67f, -25.67f, -20.00f),
        topX = 0.17f, topY = -30.00f,
    )

    private val HOUSE_SMALL_FAMILY = BuildingFamily(
        67.434f, 30.0f, WindowBuildingKind.HOUSE,
        listOf(
            BuildingSlot(listOf(HOUSE_SMALL_GROUND), 1, 1),
            BuildingSlot(listOf(HOUSE_SMALL_STOREY), 0, 1),
            BuildingSlot(listOf(HOUSE_SMALL_ROOF_GABLE, HOUSE_SMALL_ROOF_MANSARD), 1, 1),
        ),
    )

    private val HOUSE_LARGE_FAMILY = BuildingFamily(
        88.976f, 42.0f, WindowBuildingKind.HOUSE,
        listOf(
            BuildingSlot(listOf(HOUSE_LARGE_GROUND), 1, 1),
            BuildingSlot(listOf(HOUSE_LARGE_STOREY), 1, 2),
            BuildingSlot(listOf(HOUSE_LARGE_ROOF_GABLE, HOUSE_LARGE_ROOF_MANSARD, HOUSE_LARGE_ROOF_TURRET), 1, 1),
        ),
    )

    private val TOWER_FAMILY = BuildingFamily(
        182.634f, 35.0f, WindowBuildingKind.SKYSCRAPER,
        listOf(
            BuildingSlot(listOf(TOWER_BODY), 1, 1, heights = listOf(TOWER_BODY_SHORT, TOWER_BODY, TOWER_BODY_TALL)),
            BuildingSlot(listOf(TOWER_CROWN_SPIRE, TOWER_CROWN_DOME), 1, 1),
        ),
    )

    // **v5.4, item 113: 56 and 73, not 96 and 90.146.** Both families inherited the height of
    // the two-storey facade the v5.0 redraw replaced, and `unitsTall` is the third of the
    // three numbers that said so -- see [SceneSpace.SceneVariant.RESTAURANT]. The pavilion
    // draws 56 piece units and the corner bar 53 or 73, measured off their own parts by
    // `BuildingHeightDeclarationTest`. Each family's `unitsTall` moved by exactly the factor
    // its variant's `metresTall` did, so `metresTall / unitsTall` -- the only quantity the
    // blit scale depends on -- is unchanged and no piece of either building is drawn at a
    // different size.
    private val RESTAURANT_FAMILY = BuildingFamily(
        56.0f, 50.0f, WindowBuildingKind.COMMERCIAL,
        listOf(
            BuildingSlot(listOf(RESTAURANT_PAVILION), 1, 1),
        ),
    )

    private val BAR_FAMILY = BuildingFamily(
        73.0f, 33.0f, WindowBuildingKind.COMMERCIAL,
        listOf(
            BuildingSlot(listOf(BAR_SIGNBOARD, BAR_CHAMFER), 1, 1),
        ),
    )

    // **v5.6F: the sixth family.** One figure with one deal, drawn 74 piece units above the
    // ground line against the 72 it declares -- the two are the turret cornice and the grid
    // margin, and `BuildingHeightDeclarationTest` measures the ratio rather than trusting
    // this sentence. Its `WindowBuildingKind` is its own: a school is street-level glass
    // like the shops and the stations, and the only one of the six whose windows show only children.
    private val SCHOOL_FAMILY = BuildingFamily(
        72.0f, 40.0f, WindowBuildingKind.SCHOOL,
        listOf(
            BuildingSlot(listOf(SCHOOL), 1, 1),
        ),
    )

    // **v5.13: the shop and the two stations.** No place of their own in the street: each of the six
    // buildings among the houses stands in whichever of the places [StorefrontStreet] deals
    // it, drawn at that place's metre, so these three have no [SceneSpace.SceneVariant] row and
    // their `unitsTall` is their own drawing's height. The two stations are their own
    // `WindowBuildingKind`: street glass, adults only.
    private val SHOP_FAMILY = BuildingFamily(
        56.0f, 32.0f, WindowBuildingKind.COMMERCIAL,
        listOf(
            BuildingSlot(listOf(SHOP), 1, 1),
        ),
    )

    private val FIRE_STATION_FAMILY = BuildingFamily(
        88.0f, 46.0f, WindowBuildingKind.STATION,
        listOf(
            BuildingSlot(listOf(FIRE_STATION), 1, 1),
        ),
    )

    private val POLICE_STATION_FAMILY = BuildingFamily(
        70.0f, 48.0f, WindowBuildingKind.STATION,
        listOf(
            BuildingSlot(listOf(POLICE_STATION), 1, 1),
        ),
    )

    /** The families that have a row of their own in the size table, by that row. */
    val FAMILIES: Map<SceneSpace.SceneVariant, BuildingFamily> = mapOf(
        SceneSpace.SceneVariant.HOUSE_SMALL to HOUSE_SMALL_FAMILY,
        SceneSpace.SceneVariant.HOUSE_LARGE to HOUSE_LARGE_FAMILY,
        SceneSpace.SceneVariant.TOWER to TOWER_FAMILY,
        SceneSpace.SceneVariant.RESTAURANT to RESTAURANT_FAMILY,
        SceneSpace.SceneVariant.BAR to BAR_FAMILY,
        SceneSpace.SceneVariant.SCHOOL to SCHOOL_FAMILY,
    )

    /**
     * The six buildings that stand among the houses (v5.13), by what they are: the restaurant, the
     * school and the bar are their size-table rows' families above, the shop and the two stations have
     * none -- each of the six is drawn in whichever of the places [StorefrontStreet] deals it.
     */
    val STOREFRONTS: Map<Storefront, BuildingFamily> = mapOf(
        Storefront.RESTAURANT to RESTAURANT_FAMILY,
        Storefront.SCHOOL to SCHOOL_FAMILY,
        Storefront.BAR to BAR_FAMILY,
        Storefront.SHOP to SHOP_FAMILY,
        Storefront.FIRE_STATION to FIRE_STATION_FAMILY,
        Storefront.POLICE_STATION to POLICE_STATION_FAMILY,
    )

    /**
     * The distant houses on the mountains (v5.11), in the order `DistantHouses` deals them: the
     * cottage, the chalet, the tall house. Each piece's `height` is its drawing's own, roof peak to
     * foot, which is what `SceneSpace.distantHousePixelsTall` sizes.
     */
    val DISTANT_HOUSES: List<BuildingPiece> = listOf(HOUSE_DISTANT_COTTAGE, HOUSE_DISTANT_CHALET, HOUSE_DISTANT_TALL)
}
