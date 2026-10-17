package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The gallery card's shutter is the wallpaper's** (v5.14): on a building the card shows shut, the shutter is drawn in
 * the bands the wallpaper draws (`DoorShutter.forEachBand`, fully down) and each band is cut from the door's own
 * outline (`DoorShutter.band`) -- not laid on the door's box, from which the scissors' wobble moves the paper up to 0.7
 * units (on the BV6600's settings-home card, up to 0.6 px). The maintainer approved the wallpaper's centred on the door,
 * *«Sì, ora è centrata (consigliato)»*, and the card's the same way, *«Sì, è centrata (consigliato)»* (2026-10-15).
 *
 * Every built-in theme's card, by day and by night, with the three groups' hours 13:00-20:00, so every building that
 * keeps hours is shut at the card's hour (the fire and the police station never close; Sunset's day card, at 19:00,
 * finds the others open): each building on a card is found by its front door's piece, and a shut one's outline cards
 * are, in order and to the float, the bands the wallpaper cuts from **its own** door, in the shadow paper's, the face's
 * and the slats' colours; a shut building without them, or an open one or a house with them, fails (v5.14C: the test
 * of v5.14B2 matched the bands against any door of the neighbourhood and passed over a shut building drawn without a
 * shutter).
 */
class CardShutterTest {

    /**
     * A piece with a front door: the door, the drawables the card blits for it (whatever the season), and what keeps the
     * building's hours -- a tower's, one of the six buildings among the houses' ([storefront]), or none (a house).
     */
    private class DoorPiece(val door: BuildingDoor, val res: Set<Int>, val tower: Boolean, val house: Boolean, val storefront: Storefront?)

    private val doorPieces: List<DoorPiece> = buildList {
        val seen = HashSet<BuildingPiece>()
        fun take(family: BuildingFamily, tower: Boolean, storefront: Storefront?) {
            for (slot in family.slots) for (piece in slot.options + slot.heights) {
                val door = piece.door ?: continue
                if (!seen.add(piece)) continue
                val res = piece.parts.filter { it.role == PartRole.FIXED || it.role == PartRole.WALL_MASK || it.role == PartRole.GLASS_MASK }
                    .map { it.res }.toSet()
                add(DoorPiece(door, res, tower, family.kind == WindowBuildingKind.HOUSE, storefront))
            }
        }
        // The six among the houses first: the restaurant's, the school's and the bar's families are also their rows'.
        for ((storefront, family) in NeighbourhoodTable.STOREFRONTS) take(family, false, storefront)
        for ((variant, family) in NeighbourhoodTable.FAMILIES) take(family, variant == SceneSpace.SceneVariant.TOWER, null)
    }

    private val doors: List<BuildingDoor> = buildList {
        val seen = HashSet<BuildingDoor>()
        for (family in NeighbourhoodTable.FAMILIES.values + NeighbourhoodTable.STOREFRONTS.values) {
            for (slot in family.slots) for (piece in slot.options + slot.heights) {
                val door = piece.door ?: continue
                if (seen.add(door)) add(door)
            }
        }
    }

    /** The drawable's name for a resource id, by reflection on `R.drawable`. */
    private val names: Map<Int, String> by lazy {
        com.paperscrape.livewallpaper.R.drawable::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }
    }

    private val shut = { c: SceneCustomization ->
        c.copy(
            shopHoursEnabled = true, shopOpenHour = 13f, shopCloseHour = 20f,
            publicHoursEnabled = true, publicOpenHour = 13f, publicCloseHour = 20f,
            towerHoursEnabled = true, towerOpenHour = 13f, towerCloseHour = 20f,
        )
    }

    /** The outlines the wallpaper cuts for [door] fully down, decorations [k] times their size, with their colours. */
    private fun wallpaperBands(door: BuildingDoor, k: Float, night: Float): List<Pair<Int, List<Float>>> {
        val n = 2 * door.outlineX.size + 4
        val xs = FloatArray(n); val ys = FloatArray(n); val sx = FloatArray(n); val sy = FloatArray(n)
        val under = SceneColour.blendArgb(SceneObjectRenderer.SHUTTER_UNDER_DAY, SceneObjectRenderer.SHUTTER_UNDER_NIGHT, night)
        val face = SceneColour.blendArgb(SceneObjectRenderer.SHUTTER_DAY, SceneObjectRenderer.SHUTTER_NIGHT, night)
        val slat = SceneColour.blendArgb(SceneObjectRenderer.SHUTTER_SLAT_DAY, SceneObjectRenderer.SHUTTER_SLAT_NIGHT, night)
        val out = mutableListOf<Pair<Int, List<Float>>>()
        DoorShutter.forEachBand(door, 0f, 1f, k) { paper, top, bottom, dx, dy ->
            val m = DoorShutter.band(door, 0f, top, bottom, dx, dy, xs, ys, sx, sy)
            if (m > 0) {
                val colour = when (paper) {
                    DoorShutter.Paper.UNDER -> under
                    DoorShutter.Paper.FACE -> face
                    DoorShutter.Paper.SLAT -> slat
                }
                out += colour to List(2 * m) { if (it % 2 == 0) xs[it / 2] else ys[it / 2] }
            }
        }
        return out
    }

    @Test
    fun `a shut building's shutter on the card is the wallpaper's, cut from its door`() {
        var buildings = 0
        var towers = 0
        var offTheBox = 0
        var open = 0
        var houses = 0
        for (theme in ThemeCatalog.ALL) {
            for (forceNight in listOf(false, true)) {
                val c = shut(defaultCustomizationFor(theme.id))
                val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
                val night = 1f - ThemePreviewScenes.cardPhase(theme, forceNight).dayBlend
                val hour = ThemePreviewScenes.cardHour(theme, forceNight)
                for (item in scene.items + scene.backdrop + scene.ground) {
                    val ids = item.parts.map { it.resId }.toSet()
                    val owners = doorPieces.filter { ids.containsAll(it.res) }
                    // The shutter's bands; a tower's Christmas star is an outline card too (v5.14C2), held by `CardDecorationsTest`.
                    val cards = item.parts.filter { it.outline != null && !it.decoration }
                    val where = "${theme.id} (night $forceNight), the building at x=${item.x}"
                    if (owners.isEmpty()) {
                        assertTrue("$where: outline cards on something with no front door", cards.isEmpty())
                        continue
                    }
                    assertEquals("$where: one front door", 1, owners.map { it.door }.distinct().size)
                    val o = owners.first()
                    if (o.house) {
                        houses++
                        assertTrue("$where: a house shut", cards.isEmpty())
                        continue
                    }
                    val openness = if (o.tower) c.opennessFor(SceneSpace.SceneVariant.TOWER, hour) else c.opennessFor(o.storefront!!, hour)
                    if (openness >= 1f) {
                        open++
                        assertTrue("$where: open at $hour and drawn shut", cards.isEmpty())
                        continue
                    }
                    assertTrue("$where: shut at $hour and drawn without its shutter", cards.isNotEmpty())
                    buildings++
                    val drawn = cards.map { it.tint!! to it.outline!! }
                    // A tower's decorations, its rail and its slats are half as large again (`decorationScale`).
                    val k = if (o.tower) SceneObjectRenderer.TOWER_DECORATION_SCALE else 1f
                    if (o.tower) towers++
                    assertEquals("$where: its shutter is not its own door's wallpaper shutter", wallpaperBands(o.door, k, night), drawn)
                    val d = o.door
                    if (d.outlineX.min() != d.x || d.outlineX.max() != d.x + d.w || d.outlineY.min() != d.y || d.outlineY.max() != d.y + d.h) offTheBox++
                }
            }
        }
        println("CardShutterTest: $buildings shut buildings on the cards ($towers towers), $offTheBox of them on a door whose paper is not its box; $open open, $houses houses")
        assertTrue("no card showed a shut building", buildings > 20)
        assertTrue("no card showed a shut tower", towers > 0)
        assertTrue("no card showed an open building that keeps hours, or one that never closes", open > 0)
        assertTrue("no house found by its door", houses > 0)
        assertTrue("no shut building on a card has a door whose paper the scissors moved off its box", offTheBox > 0)
    }

    @Test
    fun `the wallpaper's bands come in its order, the shadow, the face, the box, the slats`() {
        for (door in doors) {
            val papers = mutableListOf<DoorShutter.Paper>()
            DoorShutter.forEachBand(door, 0f, 1f, 1f) { paper, _, _, _, _ -> papers += paper }
            val relief = door.reliefX != 0f || door.reliefY != 0f
            assertEquals(relief, papers.first() == DoorShutter.Paper.UNDER)
            val rest = if (relief) papers.drop(1) else papers
            assertEquals(DoorShutter.Paper.FACE, rest.first())
            assertTrue("a door without a box or a slat", rest.drop(1).size >= 2 && rest.drop(1).all { it == DoorShutter.Paper.SLAT })
        }
    }
}
