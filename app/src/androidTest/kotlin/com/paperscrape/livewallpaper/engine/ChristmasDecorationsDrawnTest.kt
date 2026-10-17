package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Where the renderer hangs the Christmas decorations, read off what it draws** (v5.14, decision 67): every building's
 * wreath centred on its own front door ([BuildingDoor], as the buildings' generator wrote it into `NeighbourhoodTable.kt`)
 * at [SceneObjectRenderer.WREATH_DROP] below its top, and its roof's bulbs on its top piece's roofline (`roofX`), a
 * tower's half as large again ([SceneObjectRenderer.TOWER_DECORATION_SCALE]) -- the factor `ChristmasDecorationsTest`
 * holds the two pixels with, on the JVM, from the constant.
 *
 * Until v5.14C nothing read where `drawDecorations` puts them: `DoorShutterTest` places the wreath from the constants and
 * `ChristmasDecorationsTest` takes the towers' factor from its constant, and no golden turns the Christmas lights on, so a
 * wreath moved off its door, or every decoration drawn at the houses' size on a tower, passed every test.
 *
 * The frame of every built-in theme with *Christmas lights* on, at one in the morning, is drawn on a canvas that draws
 * nothing and records the transform calls: a building is drawn in a frame of its own (`canvas.save()`, its scale), its
 * pieces each in a frame under it (`SpriteBlitter`'s), its wreath in a frame under it too (translated to the wreath's
 * centre, scaled by the decorations' factor), and its bulbs straight into it ([SceneCanvas.drawDisc]). A building is
 * known by its pieces' drawings, and its door by the one piece with a door among them.
 *
 * **And the rest of what the gallery card hangs since v5.14C2** (`CardDecorationsTest` holds the card to the same points):
 * a tower's star -- its middle pentagon and five points, the fir's gold, centred 6.5 units times a tower's scale above its
 * top piece's highest point, its points 7 and its notches 3 units times that scale from its middle -- and none on another
 * building; the strings under its sills, as many as the rule gives -- every sill of a building with four or fewer, three in
 * four of a larger one's, the building's pieces counted by their drawings -- each on a sill of its pieces, across and at the
 * sill's height on the floor that piece stands on (read off where its drawings are blitted), its cord's two lines from the
 * sill's ends to its middle 16 % of its width lower, its four bulbs hung 1.6 units under the cord; and three bulbs on every
 * distant house (the houses on at 100 %), at the first, the middle and the last point of its drawing's roofline, half a
 * radius below them, in three colours of the lights following one another, [PaperRenderer.DISTANT_BULB_PX] across on the
 * screen. Which of a building's sills carry its strings is the building's seed's: not checked here; `CardDecorationsTest`
 * reads that the wallpaper hands [ChristmasDecorations.forEachSillString] the building's own seed, and holds the card's
 * choice, under the same rule, sill by sill.
 */
@RunWith(AndroidJUnit4::class)
class ChristmasDecorationsDrawnTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The renderer's numbers, read into fields and not used as constants inside the loops below: with them inlined,
     * d8 kept the bulb's radius in a register the wreaths' loop then reused, and this test's own debug APK multiplied
     * whatever was left in it -- the expected radius came out 0.0 on the real code (v5.14C, `dexdump` of the method).
     */
    private val bulbRadius = SceneObjectRenderer.CHRISTMAS_LIGHT_BULB_RADIUS_UNITS
    private val towerScale = SceneObjectRenderer.TOWER_DECORATION_SCALE
    private val wreathDrop = SceneObjectRenderer.WREATH_DROP
    private val starRise = SceneObjectRenderer.TOWER_STAR_RISE
    private val starOuter = SceneObjectRenderer.TOWER_STAR_OUTER
    private val starInner = SceneObjectRenderer.TOWER_STAR_INNER
    private val pixelsPerUnit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
    private val starGold = SceneObjectRenderer.STAR_GOLD
    private val cordColour = ChristmasDecorations.STRING_CORD_COLOUR
    private val stringSag = ChristmasDecorations.STRING_SAG
    private val stringDrop = ChristmasDecorations.STRING_BULB_DROP
    private val distantBulbPx = PaperRenderer.DISTANT_BULB_PX
    private val lightColours = SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS.toList()

    /** A piece with a front door, the drawables of it every season draws, and whether it is a tower's. */
    private class DoorPiece(val piece: BuildingPiece, val res: Set<Int>, val tower: Boolean, val house: Boolean)

    private val pieces: List<DoorPiece> = buildList {
        val seen = HashSet<BuildingPiece>()
        val families = NeighbourhoodTable.FAMILIES.entries.map { (v, f) -> f to (v == SceneSpace.SceneVariant.TOWER) } +
            NeighbourhoodTable.STOREFRONTS.values.map { it to false }
        for ((family, tower) in families) for (slot in family.slots) for (piece in slot.options + slot.heights) {
            if (!seen.add(piece)) continue
            val res = piece.parts.filter { it.role == PartRole.FIXED || it.role == PartRole.WALL_MASK || it.role == PartRole.GLASS_MASK }
                .map { it.res }.toSet()
            if (res.isNotEmpty()) add(DoorPiece(piece, res, tower, family.kind == WindowBuildingKind.HOUSE))
        }
    }

    /** One `save()` of the canvas: under which other, and what was translated and scaled in it. */
    private class Frame(val parent: Int) {
        val translates = mutableListOf<Pair<Float, Float>>()
        val scales = mutableListOf<Pair<Float, Float>>()
    }

    private class Disc(val frame: Int, val cx: Float, val cy: Float, val radius: Float, val colour: Int)

    /** A shape filled: its frame, its paint's colour, the middle of its points, and its points (x, y, x, y, ...). */
    private class Shape(val frame: Int, val colour: Int, val cx: Float, val cy: Float, val points: FloatArray)

    /** A sprite drawn: its drawable, its frame, and where its top-left went in that frame. */
    private class Blit(val res: Int, val frame: Int, val left: Float, val top: Float)

    private class Line(val frame: Int, val colour: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    /** Records the frames, the sprites drawn in each and the discs; draws nothing. */
    private class Recorder : SceneCanvas {
        val frames = mutableListOf(Frame(-1))
        private val stack = ArrayDeque<Int>()
        var current = 0
        val sprites = mutableListOf<Pair<Int, Int>>() // resId to frame
        val blits = mutableListOf<Blit>()
        val discs = mutableListOf<Disc>()
        val shapes = mutableListOf<Shape>()
        val lines = mutableListOf<Line>()
        override fun save() {
            stack.addLast(current)
            frames += Frame(current)
            current = frames.size - 1
        }
        override fun restore() { current = stack.removeLast() }
        override fun translate(dx: Float, dy: Float) { frames[current].translates += dx to dy }
        override fun scale(sx: Float, sy: Float) { frames[current].scales += sx to sy }
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            lines += Line(current, paint.color, startX, startY, stopX, stopY)
        }
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) {
            var x = 0f
            var y = 0f
            val points = FloatArray(shape.pointCount * 2)
            for (i in 0 until shape.pointCount) {
                x += shape.xAt(i); y += shape.yAt(i)
                points[2 * i] = shape.xAt(i); points[2 * i + 1] = shape.yAt(i)
            }
            shapes += Shape(current, paint.color, x / shape.pointCount, y / shape.pointCount, points)
        }
        override fun drawVerticalGradientShape(shape: SceneShape, gradientTopY: Float, gradientBottomY: Float, topColor: Int, bottomColor: Int, alpha: Int) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean) {
            sprites += resId to current
            blits += Blit(resId, current, left, top)
        }
        override fun drawSpriteClipped(
            resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean,
            clipLeft: Float, clipTop: Float, clipRight: Float, clipBottom: Float,
        ) {
            sprites += resId to current
        }
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) {
            discs += Disc(current, cx, cy, radius, colour)
        }
    }

    @Test
    fun everyWreathIsOnItsDoorAndEveryRoofBulbOnItsRoofline() {
        var wreaths = 0
        var towerWreaths = 0
        var houseWreaths = 0
        var roofBulbs = 0
        var towerRoofBulbs = 0
        var buildings = 0
        var towerStars = 0
        var strings = 0
        var houseBulbs = 0
        for (theme in ThemeCatalog.ALL) {
            val scene = GoldenScene(
                name = "decorations", dayPhase = GoldenScene.night(1f), themeId = theme.id,
                customise = { it.copy(christmasDecorationsEnabled = true, distantHouses = DistantHousesConfig(true, 1f)) },
            )
            val renderer = PaperRenderer(GlGolden.WIDTH, GlGolden.HEIGHT, context)
            scene.configure(renderer)
            val rec = Recorder()
            renderer.draw(rec, scene.dayPhase, SceneTime(scene.sceneSeconds), 0f)
            // The drawables drawn in the frames straight under each frame: a building's pieces, under the building's.
            val under = HashMap<Int, MutableSet<Int>>()
            for ((res, frame) in rec.sprites) under.getOrPut(rec.frames[frame].parent) { HashSet() } += res
            // The buildings: a frame under which every drawing of a piece with a front door is drawn.
            val doorOf = HashMap<Int, DoorPiece>()
            for ((frame, drawn) in under) {
                val owners = pieces.filter { it.piece.door != null && drawn.containsAll(it.res) }
                if (owners.isEmpty()) continue
                assertEquals("${theme.id}: a building with ${owners.size} front doors", 1, owners.map { it.piece.door }.distinct().size)
                doorOf[frame] = owners.first()
            }
            buildings += doorOf.size
            val wreathsOf = HashMap<Int, Int>()
            for ((res, frame) in rec.sprites) {
                if (res != R.drawable.deco_wreath) continue
                val wreathFrame = rec.frames[rec.frames[frame].parent]
                val building = wreathFrame.parent
                val owner = doorOf[building]
                assertTrue("${theme.id}: a wreath drawn on something with no front door", owner != null)
                val door = owner!!.piece.door!!
                val k = if (owner.tower) towerScale else 1f
                val (cx, cy) = wreathFrame.translates.single()
                val scale = wreathFrame.scales.singleOrNull()?.first ?: 1f
                val what = "${theme.id}: the wreath of a ${if (owner.tower) "tower" else if (owner.house) "house" else "building among the houses"}"
                val across = door.x + door.w / 2f
                val down = door.y + wreathDrop * k
                assertTrue("$what hangs at x=$cx, not $across, its door's middle", kotlin.math.abs(cx - across) < 1e-4f)
                assertTrue("$what hangs at y=$cy, not $down, under its door's top", kotlin.math.abs(cy - down) < 1e-4f)
                assertTrue("$what is drawn $scale times its size, not $k", kotlin.math.abs(scale - k) < 1e-6f)
                wreathsOf[building] = (wreathsOf[building] ?: 0) + 1
                wreaths++
                if (owner.tower) towerWreaths++
                if (owner.house) houseWreaths++
            }
            for ((building, owner) in doorOf) {
                assertEquals("${theme.id}: wreaths on one building", 1, wreathsOf[building] ?: 0)
                // Its top piece's roofline: one bulb at each of its points, the decorations' size.
                val drawn = under.getValue(building)
                val k = if (owner.tower) towerScale else 1f
                val tops = pieces.filter { it.piece.roofX.isNotEmpty() && drawn.containsAll(it.res) }.map { it.piece }.distinct()
                assertEquals("${theme.id}: a building with ${tops.size} top pieces", 1, tops.size)
                val discs = rec.discs.filter { it.frame == building }
                val top = tops.single()
                for (x in top.roofX) {
                    val bulb = discs.firstOrNull { it.cx == x }
                    assertTrue("${theme.id}: no bulb at x=$x of a roofline", bulb != null)
                    val want = bulbRadius * k
                    val got = bulb!!.radius
                    assertTrue(
                        "${theme.id}: a ${if (owner.tower) "tower's" else "building's"} roof bulb at x=$x has a radius of $got, not $want",
                        kotlin.math.abs(got - want) < 1e-4f,
                    )
                    roofBulbs++
                    if (owner.tower) towerRoofBulbs++
                }
                // The top piece's foot, read off its first roof bulb: where its star stands.
                val topBase = discs.first { it.cx == top.roofX[0] }.cy - top.roofY[0]
                val stars = rec.shapes.filter { it.frame == building && it.colour == starGold }
                if (owner.tower) {
                    assertEquals("${theme.id}: a tower's star is its pentagon and five points", 6, stars.size)
                    val middle = stars.first()
                    assertTrue("${theme.id}: a tower's star at x=${middle.cx}, not its top's ${top.topX}", kotlin.math.abs(middle.cx - top.topX) < 1e-3f)
                    val y = topBase + top.topY - starRise * k
                    assertTrue("${theme.id}: a tower's star at y=${middle.cy}, not $y", kotlin.math.abs(middle.cy - y) < 1e-3f)
                    // Its size: the pentagon's corners are the notches, each point's farthest corner its tip.
                    for (i in 0 until middle.points.size / 2) {
                        val r = kotlin.math.hypot(middle.points[2 * i] - middle.cx, middle.points[2 * i + 1] - middle.cy)
                        assertTrue("${theme.id}: a tower's star's notch $r from its middle, not ${starInner * k}", kotlin.math.abs(r - starInner * k) < 1e-3f)
                    }
                    for (point in stars.drop(1)) {
                        var tip = 0f
                        for (i in 0 until point.points.size / 2) {
                            tip = maxOf(tip, kotlin.math.hypot(point.points[2 * i] - middle.cx, point.points[2 * i + 1] - middle.cy))
                        }
                        assertTrue("${theme.id}: a tower's star's point $tip from its middle, not ${starOuter * k}", kotlin.math.abs(tip - starOuter * k) < 1e-3f)
                    }
                    towerStars++
                } else {
                    assertEquals("${theme.id}: a star on a building that is not a tower", 0, stars.size)
                }
                // The strings: two cord lines each, on a sill of the building's pieces, four bulbs hung under the cord.
                val cords = rec.lines.filter { it.frame == building && it.colour == cordColour }
                assertEquals("${theme.id}: a string's cord is two lines", 0, cords.size % 2)
                val sills = pieces.filter { drawn.containsAll(it.res) }.flatMap { it.piece.lights }
                // The floors: each of the building's pieces at the foot its drawings were blitted from (a part's top is its
                // piece's foot plus the part's own y, in the sprite's pixels), so a sill's height is its floor's plus its own.
                val floors = HashSet<Float>()
                for (blit in rec.blits) {
                    if (rec.frames[blit.frame].parent != building) continue
                    for (p in pieces) {
                        if (p.piece.lights.isEmpty() || blit.res !in p.res) continue
                        val part = p.piece.parts.first { it.res == blit.res }
                        floors += blit.top / pixelsPerUnit - part.y
                    }
                }
                for (n in cords.indices step 2) {
                    val a = cords[n]
                    val b = cords[n + 1]
                    val width = b.x1 - a.x0
                    assertTrue("${theme.id}: a string from x=${a.x0}, ${width} wide, on no sill of its building",
                        sills.any { kotlin.math.abs(it.x - a.x0) < 1e-3f && kotlin.math.abs(it.w - width) < 1e-3f })
                    assertTrue("${theme.id}: a string from (${a.x0}, ${a.y0}) at the height of no sill of its building's floors",
                        pieces.any { p ->
                            drawn.containsAll(p.res) && p.piece.lights.any { l ->
                                kotlin.math.abs(l.x - a.x0) < 1e-3f && kotlin.math.abs(l.w - width) < 1e-3f &&
                                    floors.any { f -> kotlin.math.abs(f + l.y - a.y0) < 1e-3f }
                            }
                        })
                    assertTrue("${theme.id}: a cord that does not sag to its middle",
                        kotlin.math.abs(a.x1 - (a.x0 + width / 2f)) < 1e-3f && kotlin.math.abs(a.y1 - (a.y0 + width * stringSag)) < 1e-3f && a.y0 == b.y1)
                    for (m in 0 until 4) {
                        val along = (m + 1f) / 5f
                        val bx = a.x0 + width * along
                        val by = a.y0 + width * stringSag * (1f - kotlin.math.abs(along - 0.5f) * 2f) + stringDrop
                        assertTrue("${theme.id}: no bulb of a string at ($bx, $by)",
                            discs.any { kotlin.math.abs(it.cx - bx) < 1e-3f && kotlin.math.abs(it.cy - by) < 1e-3f && it.radius == bulbRadius })
                    }
                    strings++
                }
                if (sills.isNotEmpty()) assertTrue("${theme.id}: a building with sills and no string", cords.isNotEmpty())
                // How many: the building's pieces counted by the drawings drawn under it (each piece's drawings are blitted
                // once an instance), every sill of the building up to four, three in four above.
                val count = HashMap<Int, Int>()
                for ((res, frame) in rec.sprites) if (rec.frames[frame].parent == building) count[res] = (count[res] ?: 0) + 1
                val sillCount = pieces.filter { drawn.containsAll(it.res) }.distinctBy { it.piece }
                    .sumOf { p -> (p.res.minOf { count[it] ?: 0 }) * p.piece.lights.size }
                val expected = if (sillCount <= 4) sillCount else (sillCount * 3) / 4
                assertEquals("${theme.id}: the strings of a building with $sillCount sills", expected, cords.size / 2)
            }
            // The distant houses: a frame translated to the house's foot and scaled to its size, its drawing's sprites under
            // it, its three bulbs straight into it.
            for ((frame, drawnHere) in under) {
                val piece = NeighbourhoodTable.DISTANT_HOUSES.firstOrNull { p -> drawnHere.contains(p.parts.first().res) } ?: continue
                val f = rec.frames[frame]
                val scale = f.scales.single().first
                val bulbs = rec.discs.filter { it.frame == frame }
                val xs = piece.roofX
                assertEquals("${theme.id}: a distant house's bulbs", listOf(xs[0], xs[xs.size / 2], xs[xs.size - 1]), bulbs.map { it.cx })
                val ys = piece.roofY
                for ((n, i) in listOf(0, xs.size / 2, xs.size - 1).withIndex()) {
                    val bulb = bulbs[n]
                    assertTrue("${theme.id}: a distant house's bulb at y=${bulb.cy}, not half a radius under its roof's ${ys[i]}",
                        kotlin.math.abs(bulb.cy - (ys[i] + bulb.radius * 0.5f)) < 1e-4f)
                }
                val first = lightColours.indexOf(bulbs[0].colour)
                assertTrue("${theme.id}: a distant house's bulb not in the lights' colours", first >= 0)
                assertEquals("${theme.id}: a distant house's three colours", (0 until 3).map { lightColours[(first + it) % lightColours.size] }, bulbs.map { it.colour })
                for (bulb in bulbs) {
                    assertTrue("${theme.id}: a distant house's bulb ${bulb.radius * 2f * scale} px across", kotlin.math.abs(bulb.radius * 2f * scale - distantBulbPx) < 1e-3f)
                }
                houseBulbs += bulbs.size
            }
        }
        println(
            "ChristmasDecorationsDrawnTest: $buildings buildings with a front door on the twelve themes' frames, $wreaths wreaths " +
                "($towerWreaths on towers, $houseWreaths on houses), $roofBulbs roof bulbs ($towerRoofBulbs on towers), $towerStars towers' stars, " +
                "$strings strings, $houseBulbs distant houses' bulbs",
        )
        assertTrue("no tower's star drawn", towerStars > 0)
        assertTrue("no string drawn", strings > 20)
        assertTrue("no distant house's bulb drawn", houseBulbs > 20)
        assertTrue("no wreath drawn", wreaths > 20)
        assertTrue("no tower's wreath drawn", towerWreaths > 0)
        assertTrue("no house's wreath drawn", houseWreaths > 0)
        assertTrue("no building among the houses' wreath drawn", wreaths - towerWreaths - houseWreaths > 0)
        assertTrue("no tower's roof bulb drawn", towerRoofBulbs > 0)
    }
}
