package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The shutter of a shut building covers its front door exactly** (v5.14, [DoorShutter]): the maintainer chose the
 * blinds with a shutter on the door, *«B ma con saracinesca su porta»* (2026-10-14), and on the prototype's
 * photographs -- a rectangle round the door's box, its box above the door, a little left of it and higher --
 * *«va centrata bene sulla porta»* (2026-10-15). On every door the neighbourhood draws: fully down the shutter is the
 * door's own card -- the same left and right edges, the same foot, the same top -- over a shadow paper that is the
 * door's own; its box is the top of that card; its slats are inside it; and it rolls down from its box to the foot.
 * Since a band of the card can only be the card or less, nothing of the shutter stands outside the door. The wreath
 * hangs inside the door too.
 */
class DoorShutterTest {

    private class Door(val name: String, val door: BuildingDoor, val k: Float)

    /** Every piece of every family with a front door, a tower's with the tower's scale. */
    private val doors: List<Door> = buildList {
        val seen = HashSet<BuildingPiece>()
        val families = NeighbourhoodTable.FAMILIES.entries.map { it.key.name to it.value } +
            NeighbourhoodTable.STOREFRONTS.entries.map { it.key.name to it.value }
        for ((name, family) in families) {
            val k = if (family.kind == WindowBuildingKind.SKYSCRAPER) SceneObjectRenderer.TOWER_DECORATION_SCALE else 1f
            for (slot in family.slots) for (piece in slot.options + slot.heights) {
                val door = piece.door ?: continue
                if (seen.add(piece)) add(Door("$name/${seen.size}", door, k))
            }
        }
    }

    private val xs = FloatArray(32)
    private val ys = FloatArray(32)
    private val sx = FloatArray(32)
    private val sy = FloatArray(32)

    private fun area(x: FloatArray, y: FloatArray, n: Int): Float {
        var a = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += x[i] * y[j] - x[j] * y[i]
        }
        return kotlin.math.abs(a) / 2f
    }

    private fun box(x: FloatArray, y: FloatArray, n: Int): FloatArray =
        floatArrayOf((0 until n).minOf { x[it] }, (0 until n).minOf { y[it] }, (0 until n).maxOf { x[it] }, (0 until n).maxOf { y[it] })

    @Test
    fun `every building that keeps hours, and every house, has a front door`() {
        for ((name, family) in NeighbourhoodTable.FAMILIES.entries.map { it.key.name to it.value } +
            NeighbourhoodTable.STOREFRONTS.entries.map { it.key.name to it.value }) {
            for (piece in family.slots[0].options + family.slots[0].heights) {
                assertTrue("$name has no front door on its ground piece", piece.door != null)
            }
        }
        assertTrue("doors found: ${doors.size}", doors.size >= 10)
    }

    @Test
    fun `fully down, the shutter is the door's card and its shadow the door's shadow`() {
        for (d in doors) {
            val door = d.door
            val top = DoorShutter.top(door, 0f)
            val foot = DoorShutter.foot(door, 0f)
            assertEquals(foot, DoorShutter.downTo(door, 0f, 1f, DoorShutter.BOX * d.k), 1e-4f)
            val n = DoorShutter.band(door, 0f, top, foot, 0f, 0f, xs, ys, sx, sy)
            val outline = door.outlineX.size
            val cardArea = area(door.outlineX, door.outlineY, outline)
            assertEquals("${d.name}: the face is the card's area", cardArea, area(xs, ys, n), cardArea * 1e-4f)
            val faceBox = box(xs, ys, n)
            val cardBox = box(door.outlineX, door.outlineY, outline)
            for (i in 0..3) assertEquals("${d.name}: the face's edge $i is the card's", cardBox[i], faceBox[i], 1e-4f)
            val m = DoorShutter.band(door, 0f, top, foot, door.reliefX, door.reliefY, xs, ys, sx, sy)
            val shadowBox = box(xs, ys, m)
            assertEquals(cardBox[0] + door.reliefX, shadowBox[0], 1e-4f)
            assertEquals(cardBox[2] + door.reliefX, shadowBox[2], 1e-4f)
            assertEquals(cardBox[3] + door.reliefY, shadowBox[3], 1e-4f)
            assertEquals("${d.name}: the shadow is the card's area", cardArea, area(xs, ys, m), cardArea * 1e-4f)
        }
    }

    @Test
    fun `its box is the top of the door, and it rolls down from under the box to the foot`() {
        for (d in doors) {
            val door = d.door
            val top = DoorShutter.top(door, 0f)
            val foot = DoorShutter.foot(door, 0f)
            val boxH = DoorShutter.BOX * d.k
            val n = DoorShutter.band(door, 0f, top, top + boxH, 0f, 0f, xs, ys, sx, sy)
            val b = box(xs, ys, n)
            assertEquals("${d.name}: the box starts at the door's top", top, b[1], 1e-4f)
            assertEquals("${d.name}: the box is ${DoorShutter.BOX} units, a tower's half as large again", top + boxH, b[3], 1e-4f)
            assertEquals(top + boxH, DoorShutter.downTo(door, 0f, 0f, boxH), 1e-4f)
            var last = top + boxH
            for (s in 1..20) {
                val down = DoorShutter.downTo(door, 0f, s / 20f, boxH)
                assertTrue("${d.name}: it rolls down", down > last)
                assertTrue("${d.name}: never past the foot", down <= foot + 1e-4f)
                last = down
            }
        }
    }

    @Test
    fun `the slats are inside the shutter, every one`() {
        for (d in doors) {
            val door = d.door
            val top = DoorShutter.top(door, 0f)
            val foot = DoorShutter.foot(door, 0f)
            val boxH = DoorShutter.BOX * d.k
            val cardBox = box(door.outlineX, door.outlineY, door.outlineX.size)
            var line = top + boxH + DoorShutter.SLAT_GAP * d.k
            var slats = 0
            while (line + DoorShutter.SLAT * d.k <= foot) {
                val n = DoorShutter.band(door, 0f, line, line + DoorShutter.SLAT * d.k, 0f, 0f, xs, ys, sx, sy)
                assertTrue("${d.name}: a slat missed the door", n >= 3)
                val b = box(xs, ys, n)
                assertTrue("${d.name}: a slat outside the door", b[0] >= cardBox[0] - 1e-4f && b[2] <= cardBox[2] + 1e-4f)
                assertEquals("${d.name}: a slat's top is its line", line, b[1], 1e-4f)
                assertEquals("${d.name}: a slat's foot", line + DoorShutter.SLAT * d.k, b[3], 1e-4f)
                slats++
                line += DoorShutter.SLAT_PITCH * d.k
            }
            assertTrue("${d.name}: $slats slats", slats >= 2)
        }
    }

    /**
     * **Every band the shutter is drawn in is the card cut at its two heights, and fans from its first point**: the
     * GL path fills a shape as a fan from its first point (`GlSceneTarget.drawShape`), and two doors' outlines are not
     * convex -- the scissors' wobble leaves the restaurant's and the police station's a reflex corner -- so every band
     * `SceneObjectRenderer.drawShut` draws, on every door, the shadow paper, the face at 21 steps of the closing, the
     * box and every slat, is checked: its top and foot are the heights asked, and no triangle of its fan turns over.
     */
    @Test
    fun `every band drawn is cut at its heights and fans from its first point`() {
        fun check(d: Door, top: Float, bottom: Float, dx: Float, dy: Float, what: String) {
            val n = DoorShutter.band(d.door, 0f, top, bottom, dx, dy, xs, ys, sx, sy)
            if (n == 0) return
            val b = box(xs, ys, n)
            assertEquals("${d.name} $what: top", top + dy, b[1], 1e-4f)
            assertEquals("${d.name} $what: foot", minOf(bottom, DoorShutter.foot(d.door, 0f)) + dy, b[3], 1e-4f)
            var sign = 0f
            for (i in 1 until n - 1) {
                val cross = (xs[i] - xs[0]) * (ys[i + 1] - ys[0]) - (ys[i] - ys[0]) * (xs[i + 1] - xs[0])
                if (kotlin.math.abs(cross) < 1e-4f) continue
                if (sign == 0f) sign = kotlin.math.sign(cross)
                assertTrue("${d.name} $what: triangle $i of the fan turns over", kotlin.math.sign(cross) == sign)
            }
        }
        for (d in doors) {
            val top = DoorShutter.top(d.door, 0f)
            val boxH = DoorShutter.BOX * d.k
            for (s in 0..20) {
                val down = DoorShutter.downTo(d.door, 0f, s / 20f, boxH)
                check(d, top, down, 0f, 0f, "face at ${s * 5} %")
                if (d.door.reliefX != 0f || d.door.reliefY != 0f) check(d, top, down, d.door.reliefX, d.door.reliefY, "shadow at ${s * 5} %")
            }
            check(d, top, top + boxH, 0f, 0f, "box")
            var line = top + boxH + DoorShutter.SLAT_GAP * d.k
            while (line + DoorShutter.SLAT * d.k <= DoorShutter.foot(d.door, 0f)) {
                check(d, line, line + DoorShutter.SLAT * d.k, 0f, 0f, "slat at $line")
                line += DoorShutter.SLAT_PITCH * d.k
            }
        }
    }

    /** A band that misses the door gives nothing; a band that is all of it gives the card. */
    @Test
    fun `a band outside the door is nothing`() {
        val door = doors.first().door
        assertEquals(0, DoorShutter.band(door, 0f, -1000f, -999f, 0f, 0f, xs, ys, sx, sy))
        assertEquals(0, DoorShutter.band(door, 0f, 999f, 1000f, 0f, 0f, xs, ys, sx, sy))
        assertEquals(door.outlineX.size, DoorShutter.band(door, 0f, -1000f, 1000f, 0f, 0f, xs, ys, sx, sy))
    }

    /** The wreath -- its ring and its bow -- hangs inside the door's box, a tower's half as large again inside its larger door. */
    @Test
    fun `the wreath hangs inside the door`() {
        for (d in doors) {
            val door = d.door
            val k = d.k
            val outer = (SceneObjectRenderer.WREATH_RADIUS + SceneObjectRenderer.WREATH_BAND / 2f) * k
            val cy = door.y + SceneObjectRenderer.WREATH_DROP * k
            assertTrue("${d.name}: the ring is wider than the door", 2f * outer <= door.w + 1e-4f)
            assertTrue("${d.name}: the ring stands over the door's top", cy - outer >= door.y - 1e-4f)
            val bowBottom = cy + (SceneObjectRenderer.WREATH_BOW_DROP + SceneObjectRenderer.WREATH_BOW_RADIUS) * k
            assertTrue("${d.name}: the bow hangs under the door", bowBottom <= door.y + door.h)
        }
    }
}
