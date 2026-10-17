package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Nobody at a window appears or disappears from one frame to the next: they walk** (v5.12, the
 * maintainer's *«le persone devono "camminare" e uscire dalla visuale finestre, non sparire»* and *«va
 * fatto anche per la chiusura, non solo per finestre accese/spente»*, 2026-10-09; [WindowWalk]).
 *
 * Asked of every building of the twelve built-in streets -- the houses, the towers and the six buildings
 * among the houses (the restaurant, the bar, the shop, the school, the fire and the police station) -- frame by frame at thirty frames a second, across the roster's changes and across a
 * closing and an opening, by the rule the renderer draws with (`SceneObjectRenderer.drawWindowPeople`):
 * a person standing still is drawn whole at their window, a walker only where their bust overlaps a pane
 * they may be seen through, cut to it. What is measured is **how much of each person is in sight**, the
 * width of them inside the glass: it may change by no more than a walker's step in a frame.
 */
class WindowWalkTest {

    private class Building(
        val theme: String,
        val kind: WindowBuildingKind,
        val buildingSeed: Int,
        val deal: NeighbourhoodComposer.Deal,
        val plan: WindowRoster.Plan,
        val layout: WindowWalk.Layout,
    )

    /**
     * Every house and tower of the twelve streets, its roster and its windows as `SceneObjectRenderer.giveRoster`
     * makes them, and the six buildings among the houses of every street as the renderer has made them since
     * v5.13 (`SceneObjectRenderer.storefronts`: each from its identity, dealt by `dealStorefront`); a place
     * itself has no roster.
     */
    private val buildings: List<Building> by lazy {
        val out = mutableListOf<Building>()
        for (theme in ThemeCatalog.ALL) {
            val seed = theme.id.hashCode()
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            val identities = StorefrontStreet.identities(layout, theme.id)!!
            val dealt = layout.staticObjects.filter {
                (it.type == SceneObjectType.HOUSE || it.type == SceneObjectType.SKYSCRAPER) && !StorefrontStreet.isPlace(it)
            }.mapNotNull { spec -> NeighbourhoodTable.FAMILIES[SceneObjectRenderer.variantFor(spec)]?.let { Triple(spec, it, null as Storefront?) } } +
                Storefront.ALL.mapIndexed { k, storefront -> Triple(identities[k], storefront.family, storefront) }
            for ((spec, family, storefront) in dealt) {
                val deal = NeighbourhoodComposer.Deal()
                if (storefront != null) {
                    SceneObjectRenderer.dealStorefront(family, storefront, spec, deal)
                } else {
                    NeighbourhoodComposer.deal(family, spec, deal)
                }
                val buildingSeed = SceneObjectRenderer.buildingSeedOf(spec)
                val people = WindowOccupants.occupantCount(seed, buildingSeed, deal.peopleWindows, family.kind)
                val house = family.kind == WindowBuildingKind.HOUSE
                val plan = WindowRoster.plan(
                    seed, buildingSeed, deal.windowCount, deal.paneCount, people,
                    house = house, moves = family.kind != WindowBuildingKind.SKYSCRAPER,
                )
                out += Building(theme.id, family.kind, buildingSeed, deal, plan, WindowWalk.layoutOf(deal, open = !house))
            }
        }
        out
    }

    private val frame = 1.0 / 30.0

    /** The most a person's width in sight may change in one frame: a step at walking pace. */
    private val step = (WindowWalk.SPEED * frame).toFloat() + 1e-3f

    /** How much of each person of [b] is in sight in [figures], into [out] (by person), as the renderer draws them. */
    private fun inSight(b: Building, figures: WindowWalk.Figures, out: FloatArray) {
        out.fill(0f)
        val layout = b.layout
        for (f in 0 until figures.count) {
            val person = figures.person[f]
            val still = figures.still[f]
            if (still >= 0) {
                val width = layout.width[still]
                // Drawn whole and uncut: it has to be all inside its pane, or the first step of a walk,
                // which is cut, would take a sliver away at once.
                val left = WindowWalk.bustLeft(layout.centre(still), 1, width)
                val right = WindowWalk.bustRight(layout.centre(still), 1, width)
                assertTrue("${b.theme} ${b.kind}: a bust standing still pokes out of its pane", left >= layout.edge(still, -1) && right <= layout.edge(still, 1))
                out[person] += right - left
                continue
            }
            val ref = figures.window[f]
            val left = WindowWalk.bustLeft(figures.x[f], figures.facing[f], layout.width[ref])
            val right = WindowWalk.bustRight(figures.x[f], figures.facing[f], layout.width[ref])
            for (w in 0 until layout.windows) {
                if (!figures.seenThrough(f, w, layout)) continue
                val overlap = minOf(right, layout.edge(w, 1)) - maxOf(left, layout.edge(w, -1))
                if (overlap > 0f) out[person] += overlap
            }
        }
    }

    /** The panes a person is seen through in [figures], as a mask, for [person]. */
    private fun panesSeen(b: Building, figures: WindowWalk.Figures, person: Int): Int {
        var mask = 0
        val layout = b.layout
        for (f in 0 until figures.count) {
            if (figures.person[f] != person) continue
            if (figures.still[f] >= 0) { mask = mask or (1 shl figures.still[f]); continue }
            val ref = figures.window[f]
            val left = WindowWalk.bustLeft(figures.x[f], figures.facing[f], layout.width[ref])
            val right = WindowWalk.bustRight(figures.x[f], figures.facing[f], layout.width[ref])
            for (w in 0 until layout.windows) {
                if (!figures.seenThrough(f, w, layout)) continue
                if (minOf(right, layout.edge(w, 1)) - maxOf(left, layout.edge(w, -1)) > 0f) mask = mask or (1 shl w)
            }
        }
        return mask
    }

    @Test
    fun `the streets give houses, shops, schools, stations and towers to walk in`() {
        val kinds = buildings.groupingBy { it.kind }.eachCount()
        val streets = ThemeCatalog.ALL.size
        assertTrue("$kinds", (kinds[WindowBuildingKind.HOUSE] ?: 0) > 60)
        // The six among the houses of every street: the restaurant, the bar and the shop, the school, the two stations.
        assertEquals("$kinds", 3 * streets, kinds[WindowBuildingKind.COMMERCIAL] ?: 0)
        assertEquals("$kinds", streets, kinds[WindowBuildingKind.SCHOOL] ?: 0)
        assertEquals("$kinds", 2 * streets, kinds[WindowBuildingKind.STATION] ?: 0)
        assertTrue("$kinds", (kinds[WindowBuildingKind.SKYSCRAPER] ?: 0) >= 24)
    }

    @Test
    fun `a person never appears or disappears as the roster moves them`() {
        val figures = WindowWalk.Figures()
        var walksSeen = 0
        var framesWalking = 0
        for (b in buildings) {
            val p = b.plan
            if (p.people == 0) continue
            val before = FloatArray(p.people)
            val now = FloatArray(p.people)
            // Every change forward and the turn back, from just before it for thirty seconds (the longest
            // walk is under that, and the next change at least a hundred seconds away).
            for (tick in 1L..(WindowRoster.STEPS + 3L)) {
                val t0 = WindowRoster.tickTime(p, tick)
                var t = t0 - 0.5
                WindowWalk.figuresAt(p, b.layout, SceneTime(t), null, figures)
                inSight(b, figures, before)
                var walked = false
                while (t < t0 + 30.0) {
                    t += frame
                    WindowWalk.figuresAt(p, b.layout, SceneTime(t), null, figures)
                    inSight(b, figures, now)
                    if ((0 until figures.count).any { figures.still[it] < 0 }) { walked = true; framesWalking++ }
                    for (person in 0 until p.people) {
                        assertTrue(
                            "${b.theme} ${b.kind} tick $tick at ${"%.3f".format(t - t0)} s: person $person went from " +
                                "${before[person]} to ${now[person]} units in sight in one frame",
                            kotlin.math.abs(now[person] - before[person]) <= step,
                        )
                    }
                    now.copyInto(before)
                }
                if (walked) walksSeen++
            }
            // Between changes everybody stands, and every one of them is whole in sight.
            WindowWalk.figuresAt(p, b.layout, SceneTime(WindowRoster.tickTime(p, 2) - 1.0), null, figures)
            assertEquals("${b.theme}: all of them at their windows", p.people, figures.count)
            assertTrue("${b.theme}: all standing", (0 until figures.count).all { figures.still[it] >= 0 })
        }
        assertTrue("walks seen: $walksSeen, frames walking: $framesWalking", walksSeen > 500 && framesWalking > 50_000)
    }

    /**
     * Every house of the streets again with as many people as it has windows, one more at a time -- the
     * same windows, the same seeds: a walker takes the light along only where every lit window has
     * somebody at it, which the streets' houses, at a third of their windows, never reach.
     */
    private val crowdedHouses: List<Pair<Building, WindowRoster.Plan>> by lazy {
        buildings.filter { it.kind == WindowBuildingKind.HOUSE }.flatMap { b ->
            (1..b.plan.windows).map { people ->
                b to WindowRoster.plan(b.theme.hashCode(), b.buildingSeed, b.deal.windowCount, b.deal.paneCount, people, house = true)
            }
        }
    }

    @Test
    fun `nobody is seen behind a house window that is not lit, and nobody appears with the light`() {
        val figures = WindowWalk.Figures()
        var checked = 0
        val streets = buildings.filter { it.kind == WindowBuildingKind.HOUSE && it.plan.people > 0 }.map { it to it.plan }
        for ((b, p) in streets + crowdedHouses) {
            val before = FloatArray(p.people)
            val now = FloatArray(p.people)
            for (tick in 0L..(if (p === b.plan) WindowRoster.STEPS + 3L else 8L)) {
                val t0 = WindowRoster.tickTime(p, tick)
                var t = t0
                WindowWalk.figuresAt(p, b.layout, SceneTime(t - frame), null, figures)
                inSight(b, figures, before)
                while (t < t0 + 30.0) {
                    WindowWalk.figuresAt(p, b.layout, SceneTime(t), null, figures)
                    inSight(b, figures, now)
                    for (person in 0 until p.people) {
                        assertTrue("${b.theme} crowded to ${p.people}: person $person jumped", kotlin.math.abs(now[person] - before[person]) <= step)
                        val seen = panesSeen(b, figures, person)
                        for (pane in 0 until p.windows) {
                            if (seen and (1 shl pane) == 0) continue
                            val lit = WindowWalk.litAmount(p, b.layout, SceneTime(t), pane)
                            assertEquals("${b.theme} tick $tick +${"%.2f".format(t - t0)} s: person $person seen at pane $pane lit $lit", 1f, lit, 0f)
                            checked++
                        }
                    }
                    now.copyInto(before)
                    t += frame
                }
            }
        }
        assertTrue("checked $checked", checked > 100_000)
    }

    /** The houses crowded ([crowdedHouses]): the streets' own never have a walker take the light along. */
    @Test
    fun `a light a walker takes along comes on before they come, and goes out after they have gone`() {
        var carried = 0
        for ((b, p) in crowdedHouses) {
            for (step in 1..WindowRoster.STEPS) {
                for ((before, after) in listOf(step - 1 to step, step to step - 1)) {
                    val mover = WindowRoster.moverBetween(p, before, after)
                    if (mover < 0) continue
                    val from = p.seat(before, mover)
                    val to = p.seat(after, mover)
                    val litBefore = WindowRoster.litMask(p.states[before])
                    val litAfter = WindowRoster.litMask(p.states[after])
                    if (litBefore and (1 shl to) != 0) continue // not carried: the window they go to was lit already
                    carried++
                    assertTrue("${b.theme}: $from -> $to the window gone to is dark after", litAfter and (1 shl to) != 0)
                    assertTrue("${b.theme}: and the one left goes dark", litAfter and (1 shl from) == 0)
                    val inSight = WindowWalk.inSightOfTime(b.layout, from, to)
                    assertTrue("${b.theme}: $from -> $to comes into sight at $inSight s, before its light is up", inSight >= WindowRoster.FADE_SECONDS)
                    assertTrue("${b.theme}: and is out of the window left at ${WindowWalk.clearOfTime(b.layout, from, to)} s", WindowWalk.clearOfTime(b.layout, from, to) > 0.0)
                }
            }
        }
        assertTrue("carried lights: $carried", carried > 20)
    }

    @Test
    fun `leaving a window takes a person two or three seconds, as calm as the scene`() {
        var shortest = Double.MAX_VALUE
        var longest = 0.0
        for (b in buildings) {
            val layout = b.layout
            for (w in 0 until layout.windows) {
                for (way in intArrayOf(-1, 1)) {
                    // Out of their own pane entirely, from standing.
                    val distance = way * (layout.edge(w, way) - layout.centre(w)) + WindowWalk.trailing(layout.width[w])
                    val seconds = WindowWalk.setOffTime(distance)
                    shortest = minOf(shortest, seconds)
                    longest = maxOf(longest, seconds)
                }
            }
        }
        println("WindowWalkTest: out of a pane in $shortest to $longest s")
        assertTrue("out of a pane in $shortest to $longest s", shortest >= 1.5 && longest <= 3.5)
        // No walk the roster makes is a dash or a crawl: the longest crosses a school's whole front.
        var longestMove = 0.0
        for (b in buildings) for (s in 1..WindowRoster.STEPS) {
            val mover = WindowRoster.moverBetween(b.plan, s - 1, s)
            if (mover >= 0) longestMove = maxOf(longestMove, WindowWalk.moveTime(b.layout, b.plan.seat(s - 1, mover), b.plan.seat(s, mover)))
        }
        assertTrue("the longest move takes $longestMove s", longestMove in 4.0..25.0)
        // To another floor a person is out of sight on the stairs a while: they do not leave one window and
        // stand at the other in the same breath.
        var stairsSeen = 0
        for (b in buildings) for (s in 1..WindowRoster.STEPS) {
            val mover = WindowRoster.moverBetween(b.plan, s - 1, s)
            if (mover < 0) continue
            val from = b.plan.seat(s - 1, mover); val to = b.plan.seat(s, mover)
            if (b.layout.row[from] == b.layout.row[to]) continue
            stairsSeen++
            val hidden = WindowWalk.inSightOfTime(b.layout, from, to) - WindowWalk.exitTime(b.layout, from, WindowWalk.exitDirection(b.layout, from, to))
            assertTrue("${b.theme}: $from -> $to out of sight for $hidden s", hidden >= 2.0)
        }
        assertTrue("moves to another floor: $stairsSeen", stairsSeen > 50)
        assertTrue("and is over well before the next change", longestMove < WindowRoster.TICK_MIN_SECONDS / 4)
    }

    // ---------------------------------------------------------------- the hours

    /**
     * The hours at the minute (v5.14, [BusinessHours]) around one closing and the next opening, [start] the
     * start of the closing's last minute: everybody in and open before it; nobody due in, still open, through it;
     * shut and nobody due for [shut] seconds; then open with everybody due in again.
     */
    private fun hours(t: Double, start: Double, people: Int, shut: Double = 40.0): Pair<Int, Boolean> {
        val s = t - start
        return when {
            s < 0 -> people to true
            s < 60 -> 0 to true
            s < 60 + shut -> 0 to false
            else -> people to true
        }
    }

    /**
     * **At the hours they walk, one at a time, in the order of before, and nobody is ever behind unlit glass**
     * (v5.12; at the minute since v5.14): in the minute before the closing the people walk out, the last in the
     * order first -- **every one of them out before the closing minute**, on every business of the twelve streets
     * (the maintainer was told: in the last minute they walk out, at 20:00 the glass goes dark) -- and the glass
     * goes dark at the closing minute, over two seconds, once nobody is in; at the opening minute it lights over
     * two seconds and only then do they walk in, the first first. No walker jumps a frame's step, and the glass is
     * below lit only while nobody is in or on the way.
     */
    @Test
    fun `at the hours people walk out one at a time and walk in one at a time, in the order of before`() {
        val figures = WindowWalk.Figures()
        var departures = 0
        var arrivals = 0
        var slowestOut = 0.0
        var slowestIn = 0.0
        for (b in buildings.filter { it.kind != WindowBuildingKind.HOUSE && it.plan.people > 0 }) {
            val p = b.plan
            val doorway = WindowWalk.Doorway()
            // A start among the roster's changes, so walks of both kinds can meet.
            val start = WindowRoster.tickTime(p, 3) - 5.0
            var t = start - 1.0
            doorway.advance(p, b.layout, SceneTime(t), p.people, open = true)
            assertEquals("seen for the first time, everybody is in", p.people, doorway.shown)
            assertEquals("and the glass lit", 1f, doorway.glassAt(t), 0f)
            val before = FloatArray(p.people)
            val now = FloatArray(p.people)
            WindowWalk.figuresAt(p, b.layout, SceneTime(t), doorway, figures)
            inSight(b, figures, before)
            var lastShown = doorway.shown
            var lastWalker = -1
            var walkInStart = Double.NaN
            val order = mutableListOf<Int>()
            val end = start + 60 + 40 + 120
            while (t < end) {
                t += frame
                val (present, open) = hours(t, start, p.people)
                doorway.advance(p, b.layout, SceneTime(t), present, open)
                WindowWalk.figuresAt(p, b.layout, SceneTime(t), doorway, figures)
                inSight(b, figures, now)
                for (person in 0 until p.people) {
                    assertTrue(
                        "${b.theme} ${b.kind} at ${"%.2f".format(t - start)} s: person $person " +
                            "${before[person]} -> ${now[person]} in one frame",
                        kotlin.math.abs(now[person] - before[person]) <= step,
                    )
                }
                now.copyInto(before)
                val glass = doorway.glassAt(t)
                if (glass < 1f) {
                    assertTrue(
                        "${b.theme} at ${"%.2f".format(t - start)} s: somebody behind glass at $glass",
                        doorway.shown == 0 && doorway.walker < 0,
                    )
                }
                val closing = t - start < 100
                if (closing) assertTrue("nobody comes in while it closes", doorway.shown <= lastShown)
                else assertTrue("nobody goes out while it opens", doorway.shown >= lastShown)
                assertTrue("one at a time", kotlin.math.abs(doorway.shown - lastShown) <= 1)
                if (doorway.walker >= 0 && doorway.walker != lastWalker) {
                    // Out by the nearer end of the floor, in from it, walking toward the middle.
                    val outward = b.layout.outward(doorway.window)
                    assertEquals("${b.theme}: the way out and in", if (doorway.leaving) outward else -outward, doorway.direction)
                    order += doorway.walker * (if (doorway.leaving) -1 else 1) - (if (doorway.leaving) 1 else 0)
                    if (doorway.leaving) departures++ else arrivals++
                    if (!doorway.leaving) {
                        assertEquals("${b.theme}: nobody walks in before the glass is lit", 1f, glass, 0f)
                        if (walkInStart.isNaN()) walkInStart = t
                    }
                }
                if (lastShown > 0 && doorway.shown == 0 && doorway.walker < 0) slowestOut = maxOf(slowestOut, t - start)
                if (lastShown < p.people && doorway.shown == p.people) slowestIn = maxOf(slowestIn, t - start - 100)
                lastWalker = doorway.walker
                lastShown = doorway.shown
                if (t - start >= 60 && t - start < 100) {
                    assertEquals("${b.theme}: all out at the closing minute", 0, doorway.shown)
                    assertEquals("${b.theme}: nobody still walking out at the closing minute", -1, doorway.walker)
                }
                if (t - start >= 62.5 && t - start < 100) assertEquals("${b.theme}: dark once shut", 0f, glass, 0f)
            }
            assertEquals("${b.theme}: all in again", p.people, doorway.shown)
            assertEquals("${b.theme}: nobody walking at the end", -1, doorway.walker)
            assertTrue("${b.theme}: the first walks in once the glass is lit, two seconds after the opening", walkInStart - start - 100 >= WindowRoster.FADE_SECONDS)
            // Out the last in the order first, in the first first: the order of before.
            val outs = order.filter { it < 0 }.map { -it - 1 }
            val ins = order.filter { it >= 0 }
            assertEquals("${b.theme}: out in reverse order", (p.people - 1 downTo 0).toList(), outs)
            assertEquals("${b.theme}: in in order", (0 until p.people).toList(), ins)
        }
        println("HOURS_WALK the last out %.1f s into the last minute, the last in %.1f s after the opening, at the slowest".format(slowestOut, slowestIn))
        assertTrue("departures $departures, arrivals $arrivals", departures > 100 && arrivals > 100)
        assertTrue("the slowest closing takes $slowestOut s of its minute", slowestOut < 60.0)
    }

    /**
     * **Out before the closing minute at every closing measured**: on every business of the twelve streets, closings
     * whose last minute starts at up to 32 moments among the roster's own changes -- its first eight changes, four
     * offsets each, a walk of the roster under way or not, from the scene's first second on -- the last person is out
     * within the minute. The slowest is printed
     * (`HOURS_LAST_OUT`): what the documents quote.
     */
    @Test
    fun `at every closing measured the last person is out before the closing minute`() {
        var slowest = 0.0
        var where = ""
        var closings = 0
        for (b in buildings.filter { it.kind != WindowBuildingKind.HOUSE && it.plan.people > 0 }) {
            val p = b.plan
            for (k in 1..8) for (d in doubleArrayOf(-5.0, -1.5, 0.5, 2.5)) {
                // The scene's clock starts at 0 and never runs before it: a closing is measured from then on.
                val start = WindowRoster.tickTime(p, k.toLong()) + d
                if (start < 1.0) continue
                val doorway = WindowWalk.Doorway()
                var t = start - 1.0
                doorway.advance(p, b.layout, SceneTime(t), p.people, open = true)
                var out = Double.NaN
                while (t < start + 60.0) {
                    t += frame
                    doorway.advance(p, b.layout, SceneTime(t), 0, open = true)
                    if (out.isNaN() && doorway.shown == 0 && doorway.walker < 0) out = t - start
                }
                assertTrue("${b.theme} ${b.kind}: somebody still in at the closing minute ($k, $d)", !out.isNaN())
                if (out > slowest) { slowest = out; where = "${b.theme} ${b.kind}, change $k ${"%+.1f".format(d)} s" }
                closings++
            }
        }
        println("HOURS_LAST_OUT %.1f s into the last minute at the slowest, of %d closings (%s)".format(slowest, closings, where))
        assertTrue(closings > 1000)
    }

    /**
     * **Nobody behind glass that is not lit, whatever the hours say** (the 5.12's rule): told to shut while
     * somebody is still in -- which the hours never do, their people walking out in the minute before, but a
     * caller could -- the glass stays lit until the last is out, and only then goes dark over its two seconds.
     */
    @Test
    fun `the glass stays lit while anybody is in or on the way out`() {
        for (b in buildings.filter { it.kind != WindowBuildingKind.HOUSE && it.plan.people > 1 }.take(30)) {
            val p = b.plan
            val doorway = WindowWalk.Doorway()
            var t = 10.0
            doorway.advance(p, b.layout, SceneTime(t), p.people, open = true)
            var dark = Double.NaN
            while (t < 10.0 + 120.0) {
                t += frame
                doorway.advance(p, b.layout, SceneTime(t), 0, open = false)
                val glass = doorway.glassAt(t)
                if (doorway.shown > 0 || doorway.walker >= 0) {
                    assertEquals("${b.theme} at ${"%.2f".format(t - 10.0)} s: somebody in behind glass at $glass", 1f, glass, 0f)
                } else if (dark.isNaN()) dark = t
            }
            assertTrue("${b.theme}: everybody out", !dark.isNaN())
            assertEquals("${b.theme}: dark two seconds after the last is out", 0f, doorway.glassAt(dark + WindowRoster.FADE_SECONDS + frame), 0f)
        }
    }

    @Test
    fun `the hours leave everybody in, or nobody, without a walk when nothing changes`() {
        for (b in buildings.filter { it.kind != WindowBuildingKind.HOUSE }) {
            val doorway = WindowWalk.Doorway()
            for (i in 0..300) doorway.advance(b.plan, b.layout, SceneTime(i * frame), b.plan.people, open = true)
            assertEquals(b.plan.people, doorway.shown)
            assertEquals(-1, doorway.walker)
            assertEquals(1f, doorway.glassAt(300 * frame), 0f)
            val shut = WindowWalk.Doorway()
            for (i in 0..300) shut.advance(b.plan, b.layout, SceneTime(i * frame), 0, open = false)
            assertEquals("seen shut, shut: nobody walks out of an empty building", 0, shut.shown)
            assertEquals(-1, shut.walker)
            assertEquals("and its glass dark", 0f, shut.glassAt(300 * frame), 0f)
        }
    }

    /**
     * **The glass takes two seconds** ([WindowRoster.FADE_SECONDS], every light of the scene): from the opening
     * minute it lights, eased, and at the closing minute -- the building empty -- it goes dark the same way;
     * reopened half-way, it turns back from where it is.
     */
    @Test
    fun `the glass lights and goes dark over two seconds, from where it is`() {
        val b = buildings.first { it.kind != WindowBuildingKind.HOUSE && it.plan.people > 0 }
        val doorway = WindowWalk.Doorway()
        doorway.advance(b.plan, b.layout, SceneTime(0.0), 0, open = false)
        assertEquals(0f, doorway.glassAt(0.0), 0f)
        doorway.advance(b.plan, b.layout, SceneTime(10.0), b.plan.people, open = true)
        assertEquals(0f, doorway.glassAt(10.0), 0f)
        assertEquals(0.5f, doorway.glassAt(11.0), 1e-4f)
        assertEquals(1f, doorway.glassAt(12.0), 0f)
        val still = WindowWalk.Doorway()
        still.advance(b.plan, b.layout, SceneTime(0.0), 0, open = false)
        still.advance(b.plan, b.layout, SceneTime(10.0), 0, open = true)
        still.advance(b.plan, b.layout, SceneTime(11.0), 0, open = false)
        val at = still.glassAt(11.0)
        assertEquals("turned back from half-way", 0.5f, at, 1e-4f)
        assertEquals(0f, still.glassAt(13.0), 0f)
        val copy = WindowWalk.Doorway().also { it.copyFrom(still) }
        assertEquals("a rebuild hands the glass on", still.glassAt(12.0), copy.glassAt(12.0), 0f)
    }

    /**
     * **A cut is not a walk.** Where the hour jumps -- the screen off for an hour, a fixed hour moved,
     * the phone's clock set -- or the opening hours are changed, the sky or the glass changes at once,
     * and the doorways take their numbers without anybody walking (walked out then, they would walk
     * behind glass already dark). The clock going on a minute at a time is not a jump, and any other
     * change of the settings is no cut ([SceneCustomization.sameOpeningHours]).
     */
    @Test
    fun `a jump of the hour or a change of the opening hours is a cut, and nobody walks across it`() {
        assertTrue(WindowWalk.hourJumped(Float.NaN, 12f))
        assertTrue(!WindowWalk.hourJumped(12f, 12f))
        assertTrue(!WindowWalk.hourJumped(12f, 12f + 1f / 60f))
        assertTrue(!WindowWalk.hourJumped(23f + 59f / 60f, 0f))
        assertTrue(!WindowWalk.hourJumped(0f, 23f + 58f / 60f))
        assertTrue(WindowWalk.hourJumped(12f, 12.25f))
        assertTrue(WindowWalk.hourJumped(19f, 22f))
        val base = SceneCustomization.DEFAULT
        assertTrue("the cars' switch is no cut", base.sameOpeningHours(base.copy(cars = base.cars.copy(visible = !base.cars.visible))))
        assertTrue("nor the clouds", base.sameOpeningHours(base.copy(clouds = base.clouds.copy(density = 0.11f))))
        for (changed in listOf(
            base.copy(shopHoursEnabled = !base.shopHoursEnabled), base.copy(shopOpenHour = 7f), base.copy(shopCloseHour = 23f),
            base.copy(towerHoursEnabled = !base.towerHoursEnabled), base.copy(towerOpenHour = 7f), base.copy(towerCloseHour = 23f),
        )) assertTrue("an opening hour is a cut", !base.sameOpeningHours(changed))
        val renderer = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt").readText()
        assertTrue("the renderer's setter cuts on the opening hours only", renderer.contains("if (!previous.sameOpeningHours(value)) doorwaysSettle = true"))
        for (b in buildings.filter { it.kind != WindowBuildingKind.HOUSE && it.plan.people > 0 }) {
            val doorway = WindowWalk.Doorway()
            doorway.advance(b.plan, b.layout, SceneTime(10.0), b.plan.people, open = true)
            doorway.advance(b.plan, b.layout, SceneTime(10.0 + frame), 0, open = false, settle = true)
            assertEquals("${b.theme}: shut by a cut, nobody in", 0, doorway.shown)
            assertEquals("${b.theme}: and nobody walking", -1, doorway.walker)
            assertEquals("${b.theme}: and the glass dark at once", 0f, doorway.glassAt(10.0 + frame), 0f)
            doorway.advance(b.plan, b.layout, SceneTime(10.0 + 2 * frame), b.plan.people, open = true, settle = true)
            assertEquals("${b.theme}: open by a cut, everybody in", b.plan.people, doorway.shown)
            assertEquals("${b.theme}: and the glass lit at once", 1f, doorway.glassAt(10.0 + 2 * frame), 0f)
            // Without the cut the same change walks them, one at a time (once the roster is not walking
            // the one whose turn it is).
            var t = 10.0 + 3 * frame
            while (doorway.walker < 0 && t < 40.0) {
                doorway.advance(b.plan, b.layout, SceneTime(t), 0, open = true)
                assertEquals("${b.theme}: nobody vanishes without the cut", b.plan.people, doorway.shown)
                t += frame
            }
            assertTrue("${b.theme}: one walks out", doorway.walker >= 0 && doorway.leaving)
        }
    }

    /** Open, everybody in; shut, nobody: the rule the first state is ([WindowOccupants.isOccupied]) since v5.14. */
    @Test
    fun `open is everybody at the windows, shut nobody`() {
        for (b in buildings.filter { it.kind != WindowBuildingKind.HOUSE && it.plan.people > 1 }.take(20)) {
            val seed = b.theme.hashCode()
            val dealt = WindowOccupants.occupantCount(seed, 0, b.plan.windows, b.kind)
            val open = (0 until b.plan.windows).count { WindowOccupants.isOccupied(seed, 0, it, b.plan.windows, b.kind, 1f) }
            assertEquals(dealt.coerceAtMost(b.plan.windows), open)
            for (o in floatArrayOf(0f, 0.5f, 0.99f)) {
                assertEquals("at $o", 0, (0 until b.plan.windows).count { WindowOccupants.isOccupied(seed, 0, it, b.plan.windows, b.kind, o) })
            }
        }
    }

    // ---------------------------------------------------------------- the geometry

    @Test
    fun `a floor's windows are one size, so a walker is one size along it`() {
        for (b in buildings) {
            val l = b.layout
            for (w in 0 until l.windows) for (o in 0 until l.windows) {
                if (l.row[w] != l.row[o]) continue
                assertEquals("${b.theme} ${b.kind}: windows $w and $o share a floor", l.width[w], l.width[o], 0f)
                assertEquals(l.bottom(w), l.bottom(o), 0f)
            }
        }
    }

    @Test
    fun `the bust walked is the bust drawn`() {
        // drawWindowOccupant's own expression, read the way OneOccupantRuleTest reads it.
        val source = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt").readText()
        val match = Regex("val s = \\(winW \\* ([0-9.]+)f\\) / WINDOW_OCCUPANT_DIVISOR_UNITS").find(source)
            ?: error("drawWindowOccupant's scale expression has changed shape")
        val share = match.groupValues[1].toFloat()
        val divisor = SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS
        assertEquals("drawWindowOccupant's pane share", share, WindowWalk.BUST_PANE_SHARE, 0f)
        assertEquals(divisor, SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS, 0f)
        for (pane in floatArrayOf(11f, 12f, 13f, 14f, 16f)) {
            assertEquals(pane * share / divisor, WindowWalk.bustScale(pane), 0f)
        }
        // The canvas the busts are drawn from: every layer of the four window busts, at three pixels a unit.
        val dir = File(repoRoot(), "app/src/main/res/drawable-nodpi")
        for (kind in listOf("man", "woman", "boy", "girl")) {
            for (layer in listOf("fx", "ms", "mh", "mt")) {
                val image = ImageIO.read(File(dir, "person_${kind}_summer_head_window_$layer.png"))
                assertEquals("$kind $layer", WindowWalk.WINDOW_HEAD_CANVAS_UNITS * SpriteBlitter.SPRITE_PIXELS_PER_UNIT, image.width.toFloat(), 0f)
            }
        }
        // Behind a walker is the anchor's side, whichever way they face.
        val w = 13f
        assertEquals(WindowWalk.trailing(w), 10f - WindowWalk.bustLeft(10f, 1, w), 1e-5f)
        assertEquals(WindowWalk.trailing(w), WindowWalk.bustRight(10f, -1, w) - 10f, 1e-5f)
        assertEquals(WindowWalk.WINDOW_HEAD_CANVAS_UNITS * WindowWalk.bustScale(w), WindowWalk.bustRight(0f, 1, w) - WindowWalk.bustLeft(0f, 1, w), 1e-5f)
    }

    /**
     * **The same person, from window to window**: the renderer draws each figure with the look dealt at
     * the window that person first stood at ([WindowRoster.Plan.origin]) -- read in its source, as the
     * call sites of this kind are read elsewhere -- and the roster's first state gives person `p` the
     * window ranked `p`.
     */
    @Test
    fun `a walker keeps their look from window to window`() {
        val source = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt").readText()
        val people = source.substringAfter("private fun drawWindowPeople(").substringBefore("\n    private fun ")
        assertEquals("both draws, standing and walking, by the person's origin", 2, Regex("""roster\.origin\(figures\.person\[f]\)""").findAll(people).count())
        val occupant = source.substringAfter("private fun drawWindowOccupant(").substringBefore("\n    private fun ")
        assertTrue("the kind by the origin", occupant.contains("WindowOccupants.occupantKindIndexAt(seed, buildingSeed, origin, kind)"))
        assertTrue("the skin by the origin", occupant.contains("WindowOccupants.occupantSkinIndexAt(seed, buildingSeed, origin)"))
        assertTrue("the colours by the origin", occupant.contains("val address = WindowOccupants.address(buildingSeed, origin)"))
        for (b in buildings) for (person in 0 until b.plan.people) {
            assertEquals("${b.theme}: person $person stood first at the window ranked $person", person, b.plan.ranks[b.plan.origin(person)])
            assertEquals(b.plan.origin(person), b.plan.seat(0, person))
        }
        // A tower's people, who never move, stand where the rule of before puts them (`WindowRosterTest`
        // holds the houses, the shops and the school to it).
        var towers = 0
        for (b in buildings.filter { it.kind == WindowBuildingKind.SKYSCRAPER }) {
            towers++
            val seed = b.theme.hashCode()
            val people = WindowRoster.peopleMask(b.plan.states[0])
            for (w in 0 until b.plan.windows) {
                assertEquals("${b.theme} tower window $w", WindowOccupants.isOccupied(seed, b.buildingSeed, w, b.deal.windowCount, b.kind, 1f, b.deal.peopleWindows), people and (1 shl w) != 0)
            }
            assertTrue("${b.theme}: a tower's people never move", b.plan.states.all { it == b.plan.states[0] })
        }
        assertTrue("towers: $towers", towers >= 24)
    }

    @Test
    fun `the pace sets off, walks and stops without a jump`() {
        for (distance in floatArrayOf(1f, 2.9f, 3f, 10f, 31f, 83f)) {
            val total = WindowWalk.walkTime(distance)
            var previous = 0f
            var t = 0.0
            while (t <= total + 0.1) {
                val s = WindowWalk.walked(distance, t)
                assertTrue("$distance at $t: $s after $previous", s >= previous && s - previous <= step)
                assertEquals("the inverse at $t", minOf(t, total), WindowWalk.walkedTime(distance, s), 1e-3)
                previous = s
                t += frame
            }
            assertEquals(distance, previous, 0f)
        }
        var t = 0.0
        var previous = 0f
        while (t < 5.0) {
            val s = WindowWalk.setOff(t)
            assertTrue(s >= previous && s - previous <= step)
            assertEquals(t, WindowWalk.setOffTime(s), 1e-4)
            previous = s
            t += frame
        }
    }

    /**
     * **A building stops asking who walked once its longest walk is over** (v5.12C, the cost of the walk):
     * [WindowWalk.figuresAt] looks for the roster's walker only while the seconds since the last change are
     * under [WindowWalk.Layout.longestMove]. That is a shortcut and not a rule only if no move of the building
     * lasts longer: every pair of windows is asked here, and the longest of them is the number.
     */
    @Test
    fun `a building's longest move is the longest of its moves, so nobody is still walking past it`() {
        var pairs = 0
        for (b in buildings) {
            val layout = b.layout
            var longest = 0.0
            for (from in 0 until layout.windows) {
                for (to in 0 until layout.windows) {
                    if (from == to) continue
                    val time = WindowWalk.moveTime(layout, from, to)
                    assertTrue("${b.theme} ${b.kind}: $from -> $to takes $time s, past ${layout.longestMove}", time <= layout.longestMove)
                    longest = maxOf(longest, time)
                    pairs++
                }
            }
            assertEquals("${b.theme} ${b.kind}: the longest move", longest, layout.longestMove, 0.0)
            // And a frame past it the figures are those of a building whose walker has arrived: everybody
            // standing. (At the very instant the longest walk ends, its walker may still be a hair short of the
            // end in floating point, flagged walking at the window they arrive at, as before v5.12C.)
            val p = b.plan
            if (p.people == 0 || layout.windows < 2) continue
            val figures = WindowWalk.Figures()
            for (tick in 1L..6L) {
                WindowWalk.figuresAt(p, layout, SceneTime(WindowRoster.tickTime(p, tick) + layout.longestMove + frame), null, figures)
                assertTrue("${b.theme} tick $tick: standing once the longest move is over", (0 until figures.count).all { figures.still[it] >= 0 })
            }
        }
        assertTrue("pairs $pairs", pairs > 5_000)
    }

    /**
     * **The renderer reads a house's lights on the clock its figures were filled at** (v5.12C): once for the
     * building, [WindowWalk.litAmount] with the [WindowWalk.Figures], not once a pane. The two forms must give
     * the same light to the bit at every frame across the changes -- the crowded houses too, where a walker
     * takes the light along.
     */
    @Test
    fun `the lights read once for the building are the lights read for each pane`() {
        val figures = WindowWalk.Figures()
        var compared = 0
        val streets = buildings.filter { it.kind == WindowBuildingKind.HOUSE }.map { it to it.plan }
        for ((b, p) in streets + crowdedHouses) {
            for (tick in 0L..(if (p === b.plan) WindowRoster.STEPS + 3L else 4L)) {
                val t0 = WindowRoster.tickTime(p, tick)
                var t = maxOf(0.0, t0 - 1.0)
                while (t < t0 + 12.0) {
                    WindowWalk.figuresAt(p, b.layout, SceneTime(t), null, figures)
                    for (pane in 0 until p.panes) {
                        val once = WindowWalk.litAmount(p, b.layout, figures, pane)
                        val each = WindowWalk.litAmount(p, b.layout, SceneTime(t), pane)
                        assertEquals("${b.theme} tick $tick +${"%.2f".format(t - t0)} s pane $pane", each, once, 0f)
                        compared++
                    }
                    t += frame * 7
                }
            }
        }
        assertTrue("compared $compared", compared > 100_000)
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }
}
