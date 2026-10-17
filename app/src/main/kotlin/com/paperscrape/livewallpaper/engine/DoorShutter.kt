package com.paperscrape.livewallpaper.engine

/**
 * The shutter of a shut building's front door (v5.14), as geometry: where its card comes down to, and the band of
 * it between two heights -- its box, a slat, the whole of it, or its shadow paper moved by the door's relief.
 *
 * **The shutter is the door's own card.** The maintainer chose the blinds with a shutter on the door, *«B ma con
 * saracinesca su porta»* (2026-10-14), and on the photographs of the prototype -- whose shutter was a rectangle
 * round the door's box with its box above it -- *«va centrata bene sulla porta»* (2026-10-15). So the shutter is cut
 * out of the outline the generator cut the door to ([BuildingDoor.outlineX], [BuildingDoor.outlineY]), from its top
 * down to how far it has come, over a shadow paper offset as the door's own is ([BuildingDoor.reliefX],
 * [BuildingDoor.reliefY]): fully down, the shutter is the door card and its shadow the door's shadow, the same
 * left and right edges, the same foot, its box inside the top of the door; nothing of the door shows and nothing of
 * the shutter stands outside it. `DoorShutterTest` holds it on every door of the neighbourhood.
 *
 * Pure, and allocation-free: the bands are written into arrays the caller owns
 * (`SceneObjectRenderer.drawShut`, which lays them into a shape, and the gallery card's `ThemePreviewScenes.shut`,
 * whose outlines `ThemePreview` fills).
 */
internal object DoorShutter {

    /** The shutter's box, in piece units before a tower's scale: the band at the top of the door it rolls out of. */
    const val BOX = 3f

    /** A slat's height, the gap above the first, and the pitch from one to the next, in piece units. */
    const val SLAT = 2.4f
    const val SLAT_GAP = 0.6f
    const val SLAT_PITCH = 6f

    /** The top of [door]'s card, its foot at [baseY]: the highest point of its outline. */
    fun top(door: BuildingDoor, baseY: Float): Float {
        var top = Float.MAX_VALUE
        for (y in door.outlineY) if (y < top) top = y
        return top + baseY
    }

    /** The foot of [door]'s card, its foot at [baseY]: the lowest point of its outline. */
    fun foot(door: BuildingDoor, baseY: Float): Float {
        var foot = -Float.MAX_VALUE
        for (y in door.outlineY) if (y > foot) foot = y
        return foot + baseY
    }

    /**
     * How far down the shutter of [door] has come at [shut] (0 .. 1), its box [box] high: from just under the box, at
     * 0, to the door's foot, at 1.
     */
    fun downTo(door: BuildingDoor, baseY: Float, shut: Float, box: Float): Float {
        val top = top(door, baseY)
        return top + box + (foot(door, baseY) - top - box) * shut.coerceIn(0f, 1f)
    }

    /** Which paper a band of the shutter is cut from: the shadow paper under it, its face, or a slat (its box is the first). */
    enum class Paper { UNDER, FACE, SLAT }

    /**
     * The bands the shutter of [door] is drawn in at [shut] (0 .. 1), its building's decorations [k] times their size,
     * in the order they are laid: the shadow paper moved by the door's relief (when it has one), the face from the
     * door's top down to how far it has come, the box at the top, then a slat every [SLAT_PITCH] down to there.
     * [band] is handed each one's paper, its top and bottom and the offset to cut it at with [DoorShutter.band]. The one sequence the
     * wallpaper (`SceneObjectRenderer.drawShut`) and the gallery card (`ThemePreviewScenes.shut`, fully down) both draw, so
     * the card's shutter is the wallpaper's. Inline: nothing allocated on the frame path.
     */
    inline fun forEachBand(
        door: BuildingDoor,
        baseY: Float,
        shut: Float,
        k: Float,
        band: (paper: Paper, bandTop: Float, bandBottom: Float, dx: Float, dy: Float) -> Unit,
    ) {
        val top = top(door, baseY)
        val box = BOX * k
        val down = downTo(door, baseY, shut, box)
        if (door.reliefX != 0f || door.reliefY != 0f) band(Paper.UNDER, top, down, door.reliefX, door.reliefY)
        band(Paper.FACE, top, down, 0f, 0f)
        band(Paper.SLAT, top, top + box, 0f, 0f)
        var line = top + box + SLAT_GAP * k
        while (line + SLAT * k <= down) {
            band(Paper.SLAT, line, line + SLAT * k, 0f, 0f)
            line += SLAT_PITCH * k
        }
    }

    /**
     * [door]'s outline, its piece's foot at [baseY], cut to the band [bandTop] .. [bandBottom] and moved by ([dx],
     * [dy]), into [outX] and [outY]; returns how many points it has (0 when the band misses the door). Two passes of
     * Sutherland-Hodgman, one a side of the band, the first through [scratchX] and [scratchY]. The outline is convex
     * but for the scissors' small dents (the restaurant's has two, at its top corners, the police station's one), and every band the
     * shutter is drawn in fans from its first point as the GL path fills a shape (`DoorShutterTest` checks each on every
     * door). Every array at least twice the outline's points and four.
     */
    fun band(
        door: BuildingDoor,
        baseY: Float,
        bandTop: Float,
        bandBottom: Float,
        dx: Float,
        dy: Float,
        outX: FloatArray,
        outY: FloatArray,
        scratchX: FloatArray,
        scratchY: FloatArray,
    ): Int {
        val xs = door.outlineX
        val ys = door.outlineY
        var n = 0
        for (i in xs.indices) {
            val ax = xs[i]
            val ay = ys[i] + baseY
            val j = if (i + 1 < xs.size) i + 1 else 0
            val bx = xs[j]
            val by = ys[j] + baseY
            val aIn = ay >= bandTop
            val bIn = by >= bandTop
            if (aIn) { scratchX[n] = ax; scratchY[n] = ay; n++ }
            if (aIn != bIn) {
                scratchX[n] = ax + (bx - ax) * (bandTop - ay) / (by - ay); scratchY[n] = bandTop; n++
            }
        }
        var m = 0
        for (i in 0 until n) {
            val ax = scratchX[i]
            val ay = scratchY[i]
            val j = if (i + 1 < n) i + 1 else 0
            val bx = scratchX[j]
            val by = scratchY[j]
            val aIn = ay <= bandBottom
            val bIn = by <= bandBottom
            if (aIn) { outX[m] = ax + dx; outY[m] = ay + dy; m++ }
            if (aIn != bIn) {
                outX[m] = ax + (bx - ax) * (bandBottom - ay) / (by - ay) + dx; outY[m] = bandBottom + dy; m++
            }
        }
        return if (m >= 3) m else 0
    }
}
