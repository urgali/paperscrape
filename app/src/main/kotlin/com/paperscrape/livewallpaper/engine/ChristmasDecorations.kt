package com.paperscrape.livewallpaper.engine

/**
 * Where a building's Christmas decorations hang (v5.14, decision 67): **one sequence for the wallpaper and the gallery
 * card**, so the two cannot hang them in different places. `SceneObjectRenderer.drawDecorations` and its sill strings
 * draw what this yields at the scene's moment -- the bulbs blinking -- and `ThemePreviewScenes` lays the same points out
 * as parts of the card's buildings, still and lit (v5.14C2, ROADMAP A186). Every place is in the building's own
 * piece units, from its feet, as the generator wrote it into `NeighbourhoodTable.kt`:
 *
 * - the roof's string: a bulb on every point of the top piece's roofline ([BuildingPiece.roofX], every 6 units along it);
 * - a tower's star on the top piece's highest point ([BuildingPiece.topX]), its middle [SceneObjectRenderer.TOWER_STAR_RISE]
 *   times [scaleFor] above it (9.75 units: every star is a tower's);
 * - a wreath on every front door ([BuildingDoor]), its middle [SceneObjectRenderer.WREATH_DROP] times [scaleFor] below the
 *   door's top;
 * - a string under a sill ([BuildingPiece.lights]): every sill of a building with four or fewer, three in four of a larger
 *   one's, the same ones for ever ([sillStrung]); four bulbs on a cord that sags to its middle ([forEachStringBulb]);
 * - three bulbs on a distant house's roofline, at its eaves, its ridge and its other eaves ([forEachDistantHouseBulb]).
 *
 * A tower's decorations are half as large again ([scaleFor]): see `drawDecorations`. Each function that takes a block is
 * `inline`, the caller's block inlined into it, so the wallpaper's draw path allocates nothing for it.
 */
internal object ChristmasDecorations {

    /** How much larger the decorations of a building of [kind] are: a tower's half as large again. */
    fun scaleFor(kind: WindowBuildingKind): Float =
        if (kind == WindowBuildingKind.SKYSCRAPER) SceneObjectRenderer.TOWER_DECORATION_SCALE else 1f

    /** The bulbs of the roof's string: [action] gets each bulb's index along the roofline, its middle and its radius. */
    inline fun forEachRoofBulb(
        deal: NeighbourhoodComposer.Deal,
        kind: WindowBuildingKind,
        action: (index: Int, x: Float, y: Float, radius: Float) -> Unit,
    ) {
        if (deal.size == 0) return
        val top = deal[deal.size - 1]
        val xs = top.piece.roofX
        val ys = top.piece.roofY
        val radius = SceneObjectRenderer.CHRISTMAS_LIGHT_BULB_RADIUS_UNITS * scaleFor(kind)
        for (i in xs.indices) action(i, xs[i], top.baseY + ys[i], radius)
    }

    /** A tower's star -- none on any other building, nor on a tower whose top draws no roofline: its middle and its two radii. */
    inline fun star(
        deal: NeighbourhoodComposer.Deal,
        kind: WindowBuildingKind,
        action: (cx: Float, cy: Float, outer: Float, inner: Float) -> Unit,
    ) {
        if (deal.size == 0 || kind != WindowBuildingKind.SKYSCRAPER) return
        val top = deal[deal.size - 1]
        if (top.piece.roofX.isEmpty()) return
        val k = scaleFor(kind)
        action(
            top.piece.topX,
            top.baseY + top.piece.topY - SceneObjectRenderer.TOWER_STAR_RISE * k,
            SceneObjectRenderer.TOWER_STAR_OUTER * k,
            SceneObjectRenderer.TOWER_STAR_INNER * k,
        )
    }

    /** The wreath on every front door of the building: its middle, and how many times its size it is drawn. */
    inline fun forEachWreath(
        deal: NeighbourhoodComposer.Deal,
        kind: WindowBuildingKind,
        action: (cx: Float, cy: Float, k: Float) -> Unit,
    ) {
        val k = scaleFor(kind)
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val door = placed.piece.door ?: continue
            action(door.x + door.w / 2f, placed.baseY + door.y + SceneObjectRenderer.WREATH_DROP * k, k)
        }
    }

    /**
     * The sills the building's strings hang from, the building's [idleSeed] choosing which: [action] gets each string's
     * left end, the sill's height and its width. Counted over the whole building, so a tall house does not light its ground
     * floor and nothing else.
     */
    inline fun forEachSillString(
        deal: NeighbourhoodComposer.Deal,
        idleSeed: Float,
        action: (x: Float, sillY: Float, width: Float) -> Unit,
    ) {
        var sills = 0
        for (index in 0 until deal.size) sills += deal[index].piece.lights.size
        var sill = 0
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val lights = placed.piece.lights
            for (lightIndex in lights.indices) {
                val light = lights[lightIndex]
                if (sills <= 4 || sillStrung(idleSeed, sill, sills, (sills * 3) / 4)) {
                    action(light.x, placed.baseY + light.y, light.w)
                }
                sill++
            }
        }
    }

    /**
     * The four bulbs of a string from ([x], [sillY]) [width] across: [action] gets each bulb's number and its middle, hung
     * off the cord -- two straight segments meeting [STRING_SAG] of the width below the middle -- [STRING_BULB_DROP] under it.
     */
    inline fun forEachStringBulb(x: Float, sillY: Float, width: Float, action: (bulb: Int, bx: Float, by: Float) -> Unit) {
        val sag = width * STRING_SAG
        for (bulb in 0 until STRING_BULBS) {
            val along = (bulb + 1f) / (STRING_BULBS + 1f)
            // The cord's own height at this point, so a bulb hangs off the string rather than floating beside it.
            val drop = sag * (1f - kotlin.math.abs(along - 0.5f) * 2f)
            action(bulb, x + width * along, sillY + drop + STRING_BULB_DROP)
        }
    }

    /**
     * The three bulbs of a distant house standing at place [slot] of its mountain, [radius] in its piece's units: at the
     * first, the middle and the last point of its roofline ([BuildingPiece.roofX]), each half its radius below it, in the
     * Christmas lights' colours from the place on ([action] gets the colour's index).
     */
    inline fun forEachDistantHouseBulb(piece: BuildingPiece, radius: Float, slot: Int, action: (x: Float, y: Float, colour: Int) -> Unit) {
        val xs = piece.roofX
        val ys = piece.roofY
        if (xs.isEmpty()) return
        for (n in 0 until 3) {
            val i = when (n) {
                0 -> 0
                1 -> xs.size / 2
                else -> xs.size - 1
            }
            action(xs[i], ys[i] + radius * 0.5f, (slot + n) % SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS.size)
        }
    }

    /**
     * Whether sill [index] of a building's [count] carries a string, when [lit] of them do: the [lit] whose hash under the
     * building's [idleSeed] is lowest. Stable -- the same sills for ever -- and the building's own.
     */
    fun sillStrung(idleSeed: Float, index: Int, count: Int, lit: Int): Boolean {
        if (lit >= count) return true
        val seed = (idleSeed * 100000f).toInt()
        var rank = 0
        val mine = hashSill(seed, index)
        for (other in 0 until count) {
            if (other != index && hashSill(seed, other) < mine) rank++
        }
        return rank < lit
    }

    private fun hashSill(seed: Int, index: Int): Int {
        var h = seed * 0x9E3779B1.toInt() + index * 0x85EBCA77.toInt()
        h = h xor (h ushr 15)
        h *= 0xC2B2AE35.toInt()
        return h xor (h ushr 13)
    }

    /** A string's bulbs. */
    const val STRING_BULBS = 4

    /** How far a string's cord sags at its middle, as a share of its width. */
    const val STRING_SAG = 0.16f

    /** How far below the cord a bulb's middle hangs, in units. */
    const val STRING_BULB_DROP = 1.6f

    /** The cord's width in units, and its colour: a dark green, two thirds opaque. */
    const val STRING_CORD_WIDTH = 1.1f
    const val STRING_CORD_COLOUR = 0xB0203528.toInt()
}
