package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Half the windows of a house lit at night, and the lights and the people changing one at a time**
 * (v5.12, inventory I-506 and the maintainer's decisions of 2026-10-09; [WindowRoster]). Asked of every
 * house, shop and school candidate of the twelve built-in streets, as the renderer builds its rosters.
 */
class WindowRosterTest {

    private class Building(val theme: String, val spec: StaticSceneObject, val family: BuildingFamily, val deal: NeighbourhoodComposer.Deal, val plan: WindowRoster.Plan) {
        val layout = WindowWalk.layoutOf(deal, open = family.kind != WindowBuildingKind.HOUSE)
    }

    /** Every house, shop and school of the twelve streets with its roster, as `SceneObjectRenderer.giveRosters` builds it. */
    private val buildings: List<Building> by lazy {
        val out = mutableListOf<Building>()
        for (theme in ThemeCatalog.ALL) {
            val seed = theme.id.hashCode()
            for (spec in SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects) {
                if (spec.type != SceneObjectType.HOUSE && spec.type != SceneObjectType.SKYSCRAPER) continue
                val family = NeighbourhoodTable.FAMILIES[SceneObjectRenderer.variantFor(spec)] ?: continue
                if (family.kind == WindowBuildingKind.SKYSCRAPER) continue
                val deal = NeighbourhoodComposer.Deal()
                NeighbourhoodComposer.deal(family, spec, deal)
                val buildingSeed = SceneObjectRenderer.buildingSeedOf(spec)
                val people = WindowOccupants.occupantCount(seed, buildingSeed, deal.peopleWindows, family.kind)
                val plan = WindowRoster.plan(seed, buildingSeed, deal.windowCount, deal.paneCount, people, family.kind == WindowBuildingKind.HOUSE)
                out += Building(theme.id, spec, family, deal, plan)
            }
        }
        out
    }

    private fun houses() = buildings.filter { it.family.kind == WindowBuildingKind.HOUSE }
    private fun shops() = buildings.filter { it.family.kind != WindowBuildingKind.HOUSE }

    @Test
    fun `the twelve streets give enough houses and shops to test, each within a state's 32 windows`() {
        assertTrue("houses: ${houses().size}", houses().size > 60)
        assertTrue("shops and schools: ${shops().size}", shops().size >= 36)
        // Every building fits a state's two halves.
        for (b in buildings) assertTrue(b.deal.windowCount <= WindowRoster.MAX_PANES && b.deal.paneCount <= WindowRoster.MAX_PANES)
    }

    @Test
    fun `a house lights half its windows at night, every one somebody stands at among them`() {
        assertEquals(2, WindowRoster.litCount(4, 1))
        assertEquals(3, WindowRoster.litCount(5, 0)) // a half rounded up
        assertEquals(3, WindowRoster.litCount(4, 3)) // more people than half: all of theirs
        for (b in houses()) {
            val p = b.plan
            assertEquals("${b.theme}: panes", b.deal.paneCount, p.panes)
            assertEquals("${b.theme}: lit", maxOf((p.panes + 1) / 2, p.people), p.lit)
            for (state in p.states) {
                val lit = WindowRoster.litMask(state)
                val people = WindowRoster.peopleMask(state)
                assertEquals("${b.theme}: the same number lit in every state", p.lit, Integer.bitCount(lit))
                assertEquals("${b.theme}: the same number of people in every state", p.people, Integer.bitCount(people))
                assertEquals("${b.theme}: nobody at dark glass", people, people and lit)
                assertEquals("${b.theme}: people only at windows", 0, people ushr p.windows)
                assertEquals("${b.theme}: only its own panes lit", 0, if (p.panes >= 32) 0 else lit ushr p.panes)
            }
        }
    }

    @Test
    fun `a shop's and the school's people keep their number, at their windows`() {
        for (b in shops()) {
            for (state in b.plan.states) {
                val people = WindowRoster.peopleMask(state)
                assertEquals("${b.theme}: the same number of people", b.plan.people, Integer.bitCount(people))
                assertEquals("${b.theme}: at its windows", 0, people ushr b.plan.windows)
                assertEquals("${b.theme}: no lit mask: all its glass lights together", 0, WindowRoster.litMask(state))
            }
        }
    }

    /** What changed between two states: lights turned off, on; people who left, came. */
    private fun changes(a: Long, b: Long): IntArray {
        val litA = WindowRoster.litMask(a); val litB = WindowRoster.litMask(b)
        val pA = WindowRoster.peopleMask(a); val pB = WindowRoster.peopleMask(b)
        return intArrayOf(
            Integer.bitCount(litA and litB.inv()), Integer.bitCount(litB and litA.inv()),
            Integer.bitCount(pA and pB.inv()), Integer.bitCount(pB and pA.inv()),
        )
    }

    @Test
    fun `one thing changes at a time, and never two at once`() {
        for (b in buildings) {
            val p = b.plan
            // Through the whole walk, forward and back: tick by tick the two states shown.
            for (tick in 1L..(4L * WindowRoster.STEPS)) {
                val before = p.states[WindowRoster.stateIndex(tick - 1)]
                val after = p.states[WindowRoster.stateIndex(tick)]
                val (off, on, left, came) = changes(before, after).toList()
                val lightOnly = off == 1 && on == 1 && left == 0 && came == 0
                val personOnly = off == 0 && on == 0 && left == 1 && came == 1
                // A person who takes the light with them: the window left goes dark as the one come to lights.
                val carried = off == 1 && on == 1 && left == 1 && came == 1 &&
                    WindowRoster.litMask(before) and WindowRoster.litMask(after).inv() == WindowRoster.peopleMask(before) and WindowRoster.peopleMask(after).inv()
                val nothing = off == 0 && on == 0 && left == 0 && came == 0
                assertTrue("${b.theme} at tick $tick: off $off on $on left $left came $came", lightOnly || personOnly || carried || nothing)
            }
        }
    }

    @Test
    fun `the lights and the people do change, at any window`() {
        var housesWithChangingLights = 0
        var buildingsWithMovingPeople = 0
        val reachedByPiece = HashMap<BuildingPiece, MutableSet<Int>>()
        for (b in buildings) {
            val p = b.plan
            val everLit = p.states.fold(0) { acc, s -> acc or WindowRoster.litMask(s) }
            val everOccupied = p.states.fold(0) { acc, s -> acc or WindowRoster.peopleMask(s) }
            if (b.family.kind == WindowBuildingKind.HOUSE && p.panes > p.lit && Integer.bitCount(everLit) > p.lit) housesWithChangingLights++
            if (p.people in 1 until p.windows && Integer.bitCount(everOccupied) > p.people) buildingsWithMovingPeople++
            for (index in 0 until b.deal.size) {
                val placed = b.deal[index]
                val set = reachedByPiece.getOrPut(placed.piece) { HashSet() }
                for (w in placed.piece.windows.indices) if (everOccupied and (1 shl (placed.firstWindow + w)) != 0) set += w
            }
        }
        val houseCandidates = houses().count { it.plan.panes > it.plan.lit }
        val movers = buildings.count { it.plan.people in 1 until it.plan.windows }
        assertTrue("lights change in $housesWithChangingLights of $houseCandidates houses", housesWithChangingLights >= houseCandidates * 9 / 10)
        assertTrue("people move in $buildingsWithMovingPeople of $movers buildings", buildingsWithMovingPeople >= movers * 9 / 10)
        // Every window of every piece that has windows, the new ones included, has somebody at it in
        // some state of some building: the people may stand at any of them.
        for ((piece, reached) in reachedByPiece) {
            if (piece.windows.isEmpty()) continue
            assertEquals("a piece of ${piece.windows.size} windows: reached $reached", piece.windows.indices.toSet(), reached)
        }
    }

    @Test
    fun `the first state places the people where the rule of before does, over every window`() {
        for (b in buildings) {
            val theme = b.theme.hashCode()
            val seed = SceneObjectRenderer.buildingSeedOf(b.spec)
            val people = WindowRoster.peopleMask(b.plan.states[0])
            for (w in 0 until b.plan.windows) {
                val before = WindowOccupants.isOccupied(theme, seed, w, b.deal.windowCount, b.family.kind, 1f, b.deal.peopleWindows)
                assertEquals("${b.theme} window $w", before, people and (1 shl w) != 0)
            }
        }
    }

    @Test
    fun `the clock walks forward, then back, and never jumps`() {
        assertEquals(0, WindowRoster.stateIndex(0))
        assertEquals(WindowRoster.STEPS, WindowRoster.stateIndex(WindowRoster.STEPS.toLong()))
        assertEquals(WindowRoster.STEPS - 1, WindowRoster.stateIndex(WindowRoster.STEPS + 1L))
        assertEquals(0, WindowRoster.stateIndex(2L * WindowRoster.STEPS))
        for (tick in 1L..10_000L) assertEquals(1, kotlin.math.abs(WindowRoster.stateIndex(tick) - WindowRoster.stateIndex(tick - 1)))
        // Days of scene time later it is still a state of the walk, read the same way.
        val p = houses().first().plan
        val late = SceneTime(86_400.0 * 30 + 17.5)
        assertEquals(p.states[WindowRoster.stateIndex(WindowRoster.tickAt(p, late))], WindowRoster.stateAt(p, late))
    }

    @Test
    fun `calm, a change every couple of minutes and a light's fade of two seconds`() {
        for (b in buildings) {
            assertTrue(b.plan.period >= WindowRoster.TICK_MIN_SECONDS && b.plan.period <= WindowRoster.TICK_MAX_SECONDS)
            assertTrue(b.plan.phase >= 0.0 && b.plan.phase < b.plan.period)
            // The rhythm itself, not the constants: no building changes more often than once a
            // minute, none waits more than five ("every few minutes", DESIGN_NOTES.md §1's calm).
            assertTrue("${b.theme}: a change every ${b.plan.period} s", b.plan.period in 60.0..300.0)
        }
        assertTrue(WindowRoster.FADE_SECONDS < WindowRoster.TICK_MIN_SECONDS)
        // Two buildings of a street do not change in step.
        val periods = houses().filter { it.theme == "autumn" }.map { it.plan.period }.toSet()
        assertTrue("autumn's houses keep clocks of their own: $periods", periods.size > 3)
        // A light fades, it does not blink: across a change it goes through the values in between. A
        // light alone, the first change of a house that swaps one (a light a walker takes along waits
        // for them to be out: `WindowWalkTest`).
        val b = houses().first {
            WindowRoster.litMask(it.plan.states[0]) != WindowRoster.litMask(it.plan.states[1]) && WindowRoster.moverBetween(it.plan, 0, 1) < 0
        }
        val p = b.plan
        val change = (p.period - p.phase) // the first tick
        val off = WindowRoster.litMask(p.states[0]) and WindowRoster.litMask(p.states[1]).inv()
        val pane = Integer.numberOfTrailingZeros(off)
        val amounts = (0..20).map { i -> WindowWalk.litAmount(p, b.layout, SceneTime(change + i * 0.1), pane) }
        assertEquals("lit just before the change", 1f, WindowWalk.litAmount(p, b.layout, SceneTime(change - 0.01), pane), 0f)
        for (i in 1 until amounts.size) assertTrue("fades out monotonically: $amounts", amounts[i] <= amounts[i - 1])
        assertTrue("through the values in between: $amounts", amounts.any { it > 0.2f && it < 0.8f })
        assertEquals("dark when done", 0f, amounts.last(), 0f)
    }

    /**
     * Who is in at an openness, before anybody has moved: the first [WindowRoster.presentCount] people,
     * at the windows they stand at in the first state -- exactly the windows the rule of before fills
     * at that openness ([WindowOccupants.isOccupied]). How they walk out and in is `WindowWalkTest`'s.
     */
    @Test
    fun `at closing the people leave one at a time, in the order they always did`() {
        for (b in shops().filter { it.plan.people >= 2 }) {
            val p = b.plan
            val theme = b.theme.hashCode()
            val seed = SceneObjectRenderer.buildingSeedOf(b.spec)
            var previous = p.people
            for (step in 100 downTo 0) {
                val openness = step / 100f
                val n = WindowRoster.presentCount(p.people, openness)
                var set = 0
                for (person in 0 until n) set = set or (1 shl p.seat(0, person))
                for (w in 0 until p.windows) {
                    assertEquals(
                        "${b.theme} window $w at openness $openness",
                        WindowOccupants.isOccupied(theme, seed, w, b.deal.windowCount, b.family.kind, openness, b.deal.peopleWindows),
                        set and (1 shl w) != 0,
                    )
                }
                assertTrue("one at a time", previous - n in 0..1)
                previous = n
            }
            // Person p is the one whose window ranked p: the order they leave in.
            for (person in 0 until p.people) assertEquals(person, p.ranks[p.origin(person)])
        }
    }

    @Test
    fun `the same seeds give the same roster`() {
        val b = houses()[3]
        val again = WindowRoster.plan(b.theme.hashCode(), SceneObjectRenderer.buildingSeedOf(b.spec), b.deal.windowCount, b.deal.paneCount, b.plan.people, true)
        assertTrue(b.plan.states.contentEquals(again.states))
        assertEquals(b.plan.period, again.period, 0.0)
        assertEquals(b.plan.phase, again.phase, 0.0)
    }
}
