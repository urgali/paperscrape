package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The Halloween decorations** (`DESIGN_NOTES.md` §12 decision 85): one cobweb a building, in the corner of the roof
 * the building's top piece chooses, the same every time; and its colour from the building's walls -- dark on a light wall,
 * light on a dark one.
 */
class HalloweenDecorationsTest {

    /**
     * **The colour from the walls** ([HalloweenDecorations.webTintOn]): above a relative luminance of 0.45 the dark web, at or
     * below it the light one -- the sRGB channels linearised and weighed 0.2126, 0.7152, 0.0722 --, so a grey of 179 takes
     * the dark web and one of 178 the light one; and the two tints are what v5.17A's photographs showed: the 5.16's web
     * `#E8E6EE`, and `#56516A` multiplied over it channel by channel.
     */
    @Test
    fun `a light wall takes the dark web and a dark wall the light one, at a luminance of 0_45`() {
        assertEquals(0.45f, HalloweenDecorations.WEB_DARK_ABOVE_LUMINANCE, 0f)
        assertEquals(HalloweenDecorations.WEB_DARK, HalloweenDecorations.webTintOn(0xFFFFFFFF.toInt()))
        assertEquals(HalloweenDecorations.WEB_LIGHT, HalloweenDecorations.webTintOn(0xFF000000.toInt()))
        assertEquals("a grey of 179", HalloweenDecorations.WEB_DARK, HalloweenDecorations.webTintOn(0xFFB3B3B3.toInt()))
        assertEquals("a grey of 178", HalloweenDecorations.WEB_LIGHT, HalloweenDecorations.webTintOn(0xFFB2B2B2.toInt()))
        assertEquals(0.4508f, HalloweenDecorations.relativeLuminance(0xFFB3B3B3.toInt()), 0.0005f)
        assertEquals(0.4452f, HalloweenDecorations.relativeLuminance(0xFFB2B2B2.toInt()), 0.0005f)
        // The channels' weights, each alone.
        assertEquals(0.2126f, HalloweenDecorations.relativeLuminance(0xFFFF0000.toInt()), 1e-4f)
        assertEquals(0.7152f, HalloweenDecorations.relativeLuminance(0xFF00FF00.toInt()), 1e-4f)
        assertEquals(0.0722f, HalloweenDecorations.relativeLuminance(0xFF0000FF.toInt()), 1e-4f)
        // The linear segment at the dark end: 10 / 255 / 12.92 of each channel.
        assertEquals(10f / 255f / 12.92f, HalloweenDecorations.relativeLuminance(0xFF0A0A0A.toInt()), 1e-6f)
        assertEquals(0xFFE8E6EE.toInt(), HalloweenDecorations.WEB_LIGHT)
        val violet = intArrayOf(0x56, 0x51, 0x6A)
        val web = intArrayOf(0xE8, 0xE6, 0xEE)
        var dark = 0xFF shl 24
        for (k in 0 until 3) dark = dark or (Math.round(violet[k] * web[k] / 255.0).toInt() shl (16 - 8 * k))
        assertEquals("%08X".format(dark), dark, HalloweenDecorations.WEB_DARK)
    }

    /**
     * **The band the web turns over** (v5.17, inventory I-1127: the maintainer's *«Sì a tutte»* to the v5.17B report's row 14, 2026-10-25),
     * on a wall whose colours by day and at night stand far outside it, white and black: at the luminance of a grey of 175
     * (0.4285) the web is still the light one whole and at one of 183 (0.4736) the dark one, while at those of 176 (0.4341)
     * and 182 (0.4679) it is between the two, the lighter wall the darker web -- so each end of the band,
     * [HalloweenDecorations.WEB_FADE_FROM_LUMINANCE] 0.43 and [HalloweenDecorations.WEB_FADE_TO_LUMINANCE] 0.47, holds to a grey
     * on each side; and in between the light web blended into the dark one by how far the wall is across the band, opaque.
     */
    @Test
    fun `across the band from 0_43 to 0_47 the light web turns into the dark one`() {
        assertEquals(0.43f, HalloweenDecorations.WEB_FADE_FROM_LUMINANCE, 0f)
        assertEquals(0.47f, HalloweenDecorations.WEB_FADE_TO_LUMINANCE, 0f)
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        fun web(v: Int) = HalloweenDecorations.webTintFor(HalloweenDecorations.relativeLuminance(grey(v)), white, black)
        assertEquals(0.4285f, HalloweenDecorations.relativeLuminance(grey(175)), 0.0005f)
        assertEquals(0.4341f, HalloweenDecorations.relativeLuminance(grey(176)), 0.0005f)
        assertEquals(0.4679f, HalloweenDecorations.relativeLuminance(grey(182)), 0.0005f)
        assertEquals(0.4736f, HalloweenDecorations.relativeLuminance(grey(183)), 0.0005f)
        assertEquals("a grey of 175", HalloweenDecorations.WEB_LIGHT, web(175))
        assertEquals("a grey of 183", HalloweenDecorations.WEB_DARK, web(183))
        val from = HalloweenDecorations.WEB_FADE_FROM_LUMINANCE
        val to = HalloweenDecorations.WEB_FADE_TO_LUMINANCE
        for (v in 176..182) {
            val l = HalloweenDecorations.relativeLuminance(grey(v))
            val want = SceneColour.blendArgb(HalloweenDecorations.WEB_LIGHT, HalloweenDecorations.WEB_DARK, (l - from) / (to - from)) or (0xFF shl 24)
            assertEquals("a grey of $v, luminance $l", "%08X".format(want), "%08X".format(web(v)))
            assertEquals("a grey of $v: opaque", 0xFF, web(v) ushr 24)
            assertTrue("a grey of $v wears neither web whole", web(v) != HalloweenDecorations.WEB_LIGHT && web(v) != HalloweenDecorations.WEB_DARK)
        }
        // The lighter the wall, the darker the web, channel by channel.
        for (v in 176..183) {
            for (shift in intArrayOf(16, 8, 0)) {
                assertTrue("a grey of $v", (web(v) shr shift and 0xFF) <= (web(v - 1) shr shift and 0xFF))
            }
        }
        assertTrue("176 and 182 apart", (web(176) shr 16 and 0xFF) > (web(182) shr 16 and 0xFF) + 100)
    }

    /**
     * **By day and at night every web is the threshold's, on any wall** ([HalloweenDecorations.webTintAt] at a `dayBlend` of
     * 1 and 0 against [HalloweenDecorations.webTintOn] of the wall's colour by day and at night): the band is narrowed to
     * keep out the wall's own luminance by day and at night, so a wall that stands inside it at rest -- a grey of a theme of
     * the user's -- wears at rest the web the threshold gives it, as in v5.17B, not one between the two. Every pair of
     * greys, and pairs of colours across the cube; and a wall standing in the band turns over what is left of it.
     */
    @Test
    fun `at rest the web is the threshold's on every wall, a wall standing in the band included`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        for (day in 0..255 step 3) {
            for (night in 0..255 step 3) {
                assertEquals("greys $day by day, $night at night, by day", HalloweenDecorations.webTintOn(grey(day)), HalloweenDecorations.webTintAt(grey(day), grey(night), 1f))
                assertEquals("greys $day by day, $night at night, at night", HalloweenDecorations.webTintOn(grey(night)), HalloweenDecorations.webTintAt(grey(day), grey(night), 0f))
            }
        }
        for (v in 170..190) {
            for (w in listOf(0, 40, 120, 176, 178, 179, 182, 220, 255)) {
                assertEquals("greys $v and $w, by day", HalloweenDecorations.webTintOn(grey(v)), HalloweenDecorations.webTintAt(grey(v), grey(w), 1f))
                assertEquals("greys $w and $v, at night", HalloweenDecorations.webTintOn(grey(v)), HalloweenDecorations.webTintAt(grey(w), grey(v), 0f))
            }
        }
        val random = java.util.Random(1127)
        repeat(20_000) {
            val day = random.nextInt() or (0xFF shl 24)
            val night = random.nextInt() or (0xFF shl 24)
            assertEquals("#%08X by day".format(day), HalloweenDecorations.webTintOn(day), HalloweenDecorations.webTintAt(day, night, 1f))
            assertEquals("#%08X at night".format(night), HalloweenDecorations.webTintOn(night), HalloweenDecorations.webTintAt(day, night, 0f))
        }
        // A wall of a grey of 178 by day (0.4452, the light web at rest) and black at night: the light web all day and all
        // night -- the band shrunk above it to 0.4452, which its dusk never climbs back to.
        val day178 = grey(178)
        for (b in 0..1000) {
            assertEquals("dayBlend ${b / 1000f}", HalloweenDecorations.WEB_LIGHT, HalloweenDecorations.webTintAt(day178, black, b / 1000f))
        }
        // A grey of 179 by day (0.4508, the dark web at rest): the dark web by day, and over the dusk it turns over the band
        // from 0.43 to 0.4508 that is left.
        val day179 = grey(179)
        val l179 = HalloweenDecorations.relativeLuminance(day179)
        val l177 = HalloweenDecorations.relativeLuminance(grey(177))
        val want = SceneColour.blendArgb(HalloweenDecorations.WEB_LIGHT, HalloweenDecorations.WEB_DARK, (l177 - 0.43f) / (l179 - 0.43f)) or (0xFF shl 24)
        assertEquals("%08X".format(want), "%08X".format(HalloweenDecorations.webTintFor(l177, day179, black)))
        // The same with the night standing in the band: a wall white by day and a grey of 179 at night (0.4508, the dark web at
        // rest) wears the dark web at every hour; one of 178 at night (0.4452, the light web) turns over the band from 0.4452
        // to 0.47 that is left.
        for (b in 0..1000) {
            assertEquals("dayBlend ${b / 1000f}", HalloweenDecorations.WEB_DARK, HalloweenDecorations.webTintAt(white, grey(179), b / 1000f))
        }
        val l178 = HalloweenDecorations.relativeLuminance(grey(178))
        val l181 = HalloweenDecorations.relativeLuminance(grey(181))
        val wantNight = SceneColour.blendArgb(HalloweenDecorations.WEB_LIGHT, HalloweenDecorations.WEB_DARK, (l181 - l178) / (0.47f - l178)) or (0xFF shl 24)
        assertEquals("%08X".format(wantNight), "%08X".format(HalloweenDecorations.webTintFor(l181, white, grey(178))))
    }

    /**
     * **Across the dusk and the dawn every built-in theme's web turns over some ten minutes, never at one frame**
     * (inventory I-1127; in v5.17B the web jumped from the dark to the light at one frame of the dusk, the second its wall
     * crossed 0.45): each of the 48 walls -- twelve themes, the houses' and the shops' two colours each -- at the colour
     * the wallpaper draws it ([ObjectVariantConfig.colorAt], at [SunPositionCalculator]'s `dayBlend` for a sunrise at 6:00
     * and a sunset at 20:00, the hours of a phone with no position), second by second from 16:00 to 22:00 and from 4:00 to
     * 9:00: no channel of the web moves by more than 2 levels in a second; the turn lasts from 8 to 14 minutes, from the
     * last second of the day's web to the first of the night's; the second the threshold flipped it in v5.17B falls
     * inside the turn, 3 minutes or more from either end; and outside the turn the web is the threshold's. Printed, wall
     * by wall, the turn's minutes from the sunset and the sunrise.
     */
    @Test
    fun `across the dusk and the dawn every built-in web turns over some ten minutes, never at one frame`() {
        var walls = 0
        var shortest = Double.MAX_VALUE
        var longest = 0.0
        var steepest = 0
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            for ((group, config) in listOf("houses" to c.houses, "shops" to c.shops)) {
                for (variant in 0..1) {
                    val day = config.colorAt(variant, 1f)
                    val night = config.colorAt(variant, 0f)
                    val turns = ArrayList<String>()
                    for ((name, from, to, at) in listOf(Sweep("dusk", 16, 22, 20), Sweep("dawn", 4, 9, 6))) {
                        val where = "${theme.id} $group ${variant + 1} $name"
                        val start = if (name == "dusk") HalloweenDecorations.WEB_DARK else HalloweenDecorations.WEB_LIGHT
                        val end = if (name == "dusk") HalloweenDecorations.WEB_LIGHT else HalloweenDecorations.WEB_DARK
                        var lastWall = 0
                        var lastWeb = 0
                        var lastLeft = Double.NaN
                        var firstArrived = Double.NaN
                        var flipped = Double.NaN
                        for (second in from * 3600..to * 3600) {
                            val dayBlend = SunPositionCalculator.compute(second / 3600f).dayBlend
                            val wall = config.colorAt(variant, dayBlend)
                            val web = HalloweenDecorations.webTintAt(day, night, dayBlend)
                            val minutes = (second - at * 3600) / 60.0
                            if (second > from * 3600) {
                                for (shift in intArrayOf(16, 8, 0)) {
                                    val step = kotlin.math.abs((web shr shift and 0xFF) - (lastWeb shr shift and 0xFF))
                                    steepest = maxOf(steepest, step)
                                    assertTrue("$where at %+.2f min: a channel moved $step levels in a second".format(minutes), step <= 2)
                                }
                                if (flipped.isNaN() && HalloweenDecorations.webTintOn(wall) != HalloweenDecorations.webTintOn(lastWall)) flipped = minutes
                            }
                            when (web) {
                                start -> {
                                    assertTrue("$where at %+.2f min: back to the day's web".format(minutes), firstArrived.isNaN())
                                    lastLeft = minutes
                                }
                                end -> if (firstArrived.isNaN()) firstArrived = minutes
                                else -> assertTrue("$where at %+.2f min: between the two webs after the turn".format(minutes), firstArrived.isNaN())
                            }
                            if (web == start || web == end) {
                                assertEquals("$where at %+.2f min: outside the turn, the threshold's web".format(minutes), HalloweenDecorations.webTintOn(wall), web)
                            }
                            lastWall = wall
                            lastWeb = web
                        }
                        assertTrue("$where: no turn ($lastLeft, $firstArrived, the flip $flipped)", !lastLeft.isNaN() && !firstArrived.isNaN() && !flipped.isNaN())
                        val length = firstArrived - lastLeft
                        assertTrue("$where: the turn lasts %.1f min".format(length), length in 8.0..14.0)
                        assertTrue(
                            "$where: the old flip at %+.1f min, %.1f and %.1f from the ends".format(flipped, flipped - lastLeft, firstArrived - flipped),
                            flipped - lastLeft >= 3.0 && firstArrived - flipped >= 3.0,
                        )
                        shortest = minOf(shortest, length)
                        longest = maxOf(longest, length)
                        turns += "$name %+.1f..%+.1f min (%.1f), the old flip at %+.1f".format(lastLeft, firstArrived, length, flipped)
                    }
                    println("WEB_TURN %-10s %-6s %d %s".format(theme.id, group, variant + 1, turns.joinToString("; ")))
                    walls++
                }
            }
        }
        println("WEB_TURN $walls walls, the turn from %.1f to %.1f minutes, at most $steepest levels of a channel in a second".format(shortest, longest))
        assertEquals("every wall of every built-in theme", ThemeCatalog.ALL.size * 2 * 2, walls)
    }

    private data class Sweep(val name: String, val from: Int, val to: Int, val at: Int)

    /**
     * **Every built-in theme's walls, and the web each wears** -- the houses' two colours and the shops' (the restaurant, the
     * bar and the shop wear them), by day and at night, as the theme ships them: printed, and the counts held, so a change of
     * the rule or of a theme's walls shows here. The cream walls that hid the 5.16's light web by day (v5.17A, inventory
     * I-1105) take the dark one.
     */
    @Test
    fun `every built-in theme's walls, by day and at night, and the web each wears`() {
        var dark = 0
        var light = 0
        var darkByDay = 0
        var lightAtNight = 0
        val flips = ArrayList<String>()
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            for ((group, config) in listOf("houses" to c.houses, "shops" to c.shops)) {
                for ((variant, pair) in listOf(1 to (config.colorDay1 to config.colorNight1), 2 to (config.colorDay2 to config.colorNight2))) {
                    val (day, night) = pair
                    for ((hour, wall) in listOf("day" to day, "night" to night)) {
                        val tint = HalloweenDecorations.webTintOn(wall)
                        val l = HalloweenDecorations.relativeLuminance(wall)
                        println(
                            "WEB_TINT %-10s %-6s %d %-5s #%06X luminance %.3f -> %s".format(
                                theme.id, group, variant, hour, wall and 0xFFFFFF, l,
                                if (tint == HalloweenDecorations.WEB_DARK) "dark" else "light",
                            ),
                        )
                        if (tint == HalloweenDecorations.WEB_DARK) dark++ else light++
                        if (hour == "day" && tint == HalloweenDecorations.WEB_DARK) darkByDay++
                        if (hour == "night" && tint == HalloweenDecorations.WEB_LIGHT) lightAtNight++
                    }
                    if (HalloweenDecorations.webTintOn(day) != HalloweenDecorations.webTintOn(night)) {
                        // Where the day's and the night's colours fall either side of the threshold, the web at rest changes
                        // colour between day and night: the moment of the dusk (and the dawn) the blend of the two crosses it is
                        // the middle of its turn since the seal of v5.17 (the test below), and was its one-frame jump in v5.17B.
                        var b = 0f
                        while (b <= 1f && HalloweenDecorations.webTintOn(SceneColour.blendArgb(night, day, b)) == HalloweenDecorations.webTintOn(night)) b += 0.001f
                        flips += "${theme.id} $group $variant at dayBlend %.3f".format(b)
                    }
                }
            }
        }
        println("WEB_TINT $dark dark, $light light; $darkByDay walls by day dark, $lightAtNight at night light; its colour at rest changes between day and night on ${flips.size}, the threshold crossed at: $flips")
        assertEquals("every wall of every built-in theme", ThemeCatalog.ALL.size * 2 * 2 * 2, dark + light)
        assertTrue("a light wall by day somewhere takes the dark web: $darkByDay", darkByDay > 0)
        assertTrue("a dark wall at night somewhere takes the light web: $lightAtNight", lightAtNight > 0)
        assertEquals("the walls by day that take the dark web", DARK_BY_DAY, darkByDay)
        assertEquals("the walls at night that take the light web", LIGHT_AT_NIGHT, lightAtNight)
    }

    /**
     * **One cobweb a building, in the corner its drawing chooses** ([HalloweenDecorations.cobwebCorner]): every house and
     * every restaurant, bar and shop of the twelve built-in streets gets exactly one, at the left end of its top piece's
     * roofline as drawn or at the right end mirrored, by the rule; the same building dealt again gets it in the same corner;
     * and over all of them about half go left -- the counts held, so a change of the rule, which moves the corners from
     * where v5.17A photographed them, shows here.
     */
    @Test
    fun `one cobweb a building, in the corner its drawing chooses, about half left and half right`() {
        val deal = NeighbourhoodComposer.Deal()
        val again = NeighbourhoodComposer.Deal()
        var left = 0
        var right = 0
        for (theme in ThemeCatalog.ALL) {
            val layout = SceneObjectCatalog.layoutFor(theme.id, 0xFF8899AA.toInt())
            val buildings = ArrayList<Pair<BuildingFamily, StaticSceneObject>>()
            for (spec in layout.staticObjects) {
                if (spec.type != SceneObjectType.HOUSE) continue
                val family = NeighbourhoodTable.FAMILIES[SceneObjectRenderer.variantFor(spec)] ?: continue
                buildings += family to spec
            }
            val identities = StorefrontStreet.identities(layout, theme.id)
            val shops = ArrayList<Pair<Storefront, StaticSceneObject>>()
            if (identities != null) {
                for ((k, storefront) in Storefront.ALL.withIndex()) {
                    if (storefront == Storefront.RESTAURANT || storefront == Storefront.BAR || storefront == Storefront.SHOP) {
                        shops += storefront to identities[k]
                    }
                }
            }
            val all = buildings.map { (family, spec) -> Triple(family, spec, null as Storefront?) } +
                shops.map { (storefront, spec) -> Triple(storefront.family, spec, storefront) }
            for ((family, spec, storefront) in all) {
                fun dealIt(into: NeighbourhoodComposer.Deal) {
                    if (storefront != null) SceneObjectRenderer.dealStorefront(family, storefront, spec, into)
                    else NeighbourhoodComposer.deal(family, spec, into)
                }
                dealIt(deal)
                dealIt(again)
                val corner = HalloweenDecorations.cobwebCorner(deal)
                assertEquals("${theme.id}: the same building, the same corner", corner, HalloweenDecorations.cobwebCorner(again))
                val top = deal[deal.size - 1]
                val xs = top.piece.roofX
                val ys = top.piece.roofY
                var webs = 0
                HalloweenDecorations.forEachCobweb(deal) { x, y, mirror ->
                    webs++
                    val end = if (corner == 0) 0 else xs.size - 1
                    assertEquals("${theme.id}: at its corner's end", xs[end], x, 0f)
                    assertEquals("${theme.id}: on the roofline", top.baseY + ys[end], y, 0f)
                    assertEquals("${theme.id}: mirrored at the right end only", if (corner == 0) 1f else -1f, mirror, 0f)
                }
                assertEquals("${theme.id}: one cobweb a building", 1, webs)
                if (corner == 0) left++ else right++
            }
        }
        println("COBWEB_CORNER $left buildings left, $right right, of ${left + right}")
        val total = left + right
        assertTrue("both ends, neither under 35 %: $left left, $right right", left >= total * 0.35 && right >= total * 0.35)
        assertEquals("the left corners, as v5.17A photographed them", LEFT_CORNERS, left)
        assertEquals("the buildings", BUILDINGS, total)
    }

    private companion object {
        /**
         * Measured on the twelve built-in themes (v5.17B): every wall of every built-in theme by day -- the houses' two and the
         * shops' two of each -- takes the dark web, and every one at night the light one; of the 156 houses, restaurants, bars
         * and shops their streets deal, 92 hang it at the left end and 64 at the right.
         */
        const val DARK_BY_DAY = 48
        const val LIGHT_AT_NIGHT = 48
        const val LEFT_CORNERS = 92
        const val BUILDINGS = 156
    }
}
