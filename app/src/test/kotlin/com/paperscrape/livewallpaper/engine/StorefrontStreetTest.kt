package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The street of v5.13 ([StorefrontStreet]), five places a tile since v5.13B2: the maintainer's *«ci vanno
 * tutti sempre in qualsiasi tema ma in modo randomico mentre scorre il wallpaper»* (2026-10-11) and, shown
 * a street with two towers fewer and five places, *«Sì, 5 posti, in un giro a parte»* (2026-10-12) -- all
 * six buildings on every theme, in any place; dealt at random as the scene scrolls, every copy that comes in
 * from the side a new one, by the theme's seed and the copy, never changing under the eyes; never two alike
 * on the screen at once; all six within a short stretch.
 *
 * The rule's arithmetic is asserted over long runs of the street; the scroll over the twelve built-in
 * themes, frame by frame, through the renderer's own copy numbering ([SceneObjectRenderer.anchorXOf],
 * [SceneObjectRenderer.representativeCopy]) and its own cull ([SceneObjectRenderer.firstVisibleTileOffset],
 * [SceneObjectRenderer.isHorizontallyVisible]), against a copy number worked out here from the unwrapped
 * scroll.
 */
class StorefrontStreetTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    /**
     * Every theme's seed, some that are no theme's -- a saved theme's or a random one's is any -- and the extremes.
     * (Until v5.14 each return of the wallpaper dealt the street a new round, another seed: the rules were held over
     * a few rounds of each theme's; they are held over these seeds.)
     */
    private val seeds = themes.map { it.hashCode() } + listOf(0, 1, -1, 12345, Int.MIN_VALUE, Int.MAX_VALUE) +
        (1..36).map { it * 7_919 + 104_729 * (it % 5) }

    @Test
    fun `every deck of six places holds the six buildings once each`() {
        for (seed in seeds) for (deck in -200..200) {
            val got = (0 until StorefrontStreet.DECK).map { StorefrontStreet.storefrontAt(seed, deck * StorefrontStreet.DECK + it) }
            assertEquals("seed $seed, deck $deck: $got", Storefront.ALL.toSet(), got.toSet())
        }
    }

    /**
     * **No building twice in four places in a row**, across a deck's seam too: a screen shows at most four
     * places of a built-in street (the plan refuses five, as `StreetPlanMovesTest` holds in `five places on
     * one screen are what the plan refuses`; the scroll below counts them), so this is never two alike on
     * the screen.
     */
    @Test
    fun `no building twice in four places in a row, across a deck's seam too`() {
        for (seed in seeds) {
            val street = (-1200..1200).map { StorefrontStreet.storefrontAt(seed, it) }
            for (k in 0 until street.size - 3) {
                val four = street.subList(k, k + 4)
                assertEquals("seed $seed at place ${k - 1200}: $four", 4, four.toSet().size)
            }
        }
    }

    /**
     * Any eleven places in a row hold a whole deck, so all six: 2.2 tiles of a built-in street, 4.4 screens.
     * The shortest run that always holds all six, over every seed tried, is printed beside it.
     */
    @Test
    fun `any eleven places in a row hold all six`() {
        var shortestAlways = 0
        for (seed in seeds) {
            val street = (-1200..1200).map { StorefrontStreet.storefrontAt(seed, it) }
            for (k in 0..street.size - 11) {
                assertEquals("seed $seed at place ${k - 1200}", 6, street.subList(k, k + 11).toSet().size)
            }
            for (w in 6..11) {
                if ((0..street.size - w).all { street.subList(it, it + w).toSet().size == 6 }) { shortestAlways = maxOf(shortestAlways, w); break }
            }
        }
        println("STREET_ALL_SIX every run of $shortestAlways places in a row held all six, over every seed tried")
    }

    @Test
    fun `the street is the theme's seed and the place, and nothing else`() {
        val a = IntArray(StorefrontStreet.DECK)
        val b = IntArray(StorefrontStreet.DECK)
        for (seed in seeds) for (place in -50..50) {
            val first = StorefrontStreet.storefrontAt(seed, place)
            // Scratch arrays dirtied by another place first: nothing carries over.
            StorefrontStreet.storefrontAt(seed + 7, place + 13, a, b)
            assertEquals(first, StorefrontStreet.storefrontAt(seed, place, a, b))
        }
        // And the themes' streets differ: a seed is not a decoration.
        val autumn = (0 until 60).map { StorefrontStreet.storefrontAt("autumn".hashCode(), it) }
        val winter = (0 until 60).map { StorefrontStreet.storefrontAt("winter".hashCode(), it) }
        assertNotEquals(autumn, winter)
    }

    /**
     * A random-looking order: on each street each building takes every place of a deck but the street's anchor,
     * which stands in every deck's head -- its first three places -- and takes each of those; and over the seeds
     * every building is the anchor of some.
     */
    @Test
    fun `a random-looking order - each building takes each position of a deck, the anchor each of the head's`() {
        val anchors = HashSet<Storefront>()
        for (seed in seeds) {
            val positions = Storefront.ALL.associateWith { HashSet<Int>() }
            for (deck in 0 until 120) for (i in 0 until StorefrontStreet.DECK) {
                positions.getValue(StorefrontStreet.storefrontAt(seed, deck * StorefrontStreet.DECK + i)) += i
            }
            val heads = positions.filterValues { it == setOf(0, 1, 2) }.keys
            assertEquals("seed $seed: one anchor, $positions", 1, heads.size)
            anchors += heads
            for ((storefront, seen) in positions) {
                if (storefront in heads) continue
                assertEquals("seed $seed: $storefront", (0 until StorefrontStreet.DECK).toSet(), seen)
            }
        }
        assertEquals("every building the anchor of some street", Storefront.ALL.toSet(), anchors)
    }

    /**
     * How wide a place among the houses is on screen: the widest of the six, its ink, at the place's metre
     * and size (`Storefront.halfWidthUnits`, which `StorefrontFrontVisibilityTest` re-measures off the PNGs).
     * Not the renderer's cull ([SceneObjectRenderer.MAX_OBJECT_HALF_WIDTH_UNITS], 96 units for every object
     * whatever it is), which reaches half as far again into transparent air: a building is on the screen when
     * its paper is.
     */
    private fun halfOnScreen(place: StaticSceneObject, screenH: Float): Float {
        val v = SceneObjectRenderer.variantFor(place)
        val metre = v.spriteUnitsTall / NeighbourhoodTable.FAMILIES.getValue(v).unitsTall
        return Storefront.ALL.maxOf { it.halfWidthUnits } * metre * SceneObjectRenderer.effectiveScaleFor(place, screenH)
    }

    /**
     * The scroll, frame by frame, on every built-in theme, the BV6600's screen and two taller ones: five
     * places a tile at the spots the plan gives them; at every scroll position at most four places on the
     * screen and the buildings in them all different; a copy keeps one building from the moment it enters to
     * the moment it leaves, and its number is the physical copy's; and along the scroll all six come by.
     */
    @Test
    fun `scrolling the twelve streets - never two alike on screen, no copy changes, all six come by`() {
        for ((screenW, screenH) in listOf(720f to 1440f, 1080f to 2340f, 1080f to 2592f)) {
            val tileW = screenW * 2f
            var most = 0
            for (themeId in themes) {
                val layout = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt())
                val places = StorefrontStreet.places(layout)
                assertEquals("$themeId: five places among the houses", 5, places.size)
                val seed = themeId.hashCode()
                val seen = HashSet<Storefront>()
                val held = HashMap<Pair<Int, Int>, Storefront>()
                val geom = GroundGeometry(0f, tileW, 0)
                // Eight tiles of drift, a fortieth of a screen a step, the way the scene moves (leftward).
                var step = 0
                while (step < 8 * 80) {
                    val unwrapped = -step * (screenW / 40.0)
                    var wrapped = unwrapped % tileW
                    if (wrapped > 0.0) wrapped -= tileW
                    geom.shiftXWrapped = wrapped.toFloat()
                    geom.scrollTileBias = Math.round((unwrapped - wrapped) / tileW).toInt()
                    val visible = ArrayList<Storefront>()
                    for ((rank, place) in places.withIndex()) {
                        val scale = SceneObjectRenderer.effectiveScaleFor(place, screenH)
                        val cull = SceneObjectRenderer.MAX_OBJECT_HALF_WIDTH_UNITS * scale
                        val half = halfOnScreen(place, screenH)
                        val x = SceneObjectRenderer.anchorXOf(place.tileFractionX, geom)
                        val first = SceneObjectRenderer.firstVisibleTileOffset(x, cull, tileW)
                        val limit = SceneObjectRenderer.tileOffsetLimit(x, cull, tileW, screenW)
                        for (tileIndex in first until limit) {
                            val copyX = x + tileIndex * tileW
                            if (!SceneObjectRenderer.isHorizontallyVisible(copyX, cull, screenW)) continue
                            val copy = SceneObjectRenderer.copyOf(place.tileFractionX, geom, tileIndex)
                            // The physical copy, from the unwrapped scroll: copy c of the place stands at
                            // unwrapped + (fraction + c) * tile.
                            val physical = Math.round((copyX - unwrapped) / tileW - place.tileFractionX).toInt()
                            assertEquals("$themeId: place $rank's copy at x=$copyX numbered $copy", physical, copy)
                            val storefront = StorefrontStreet.storefrontAt(seed, StorefrontStreet.placeIndex(copy, rank, places.size))
                            val before = held.put(rank to copy, storefront)
                            if (before != null) assertEquals("$themeId: copy $copy of place $rank changed", before, storefront)
                            seen += storefront
                            if (copyX + half > 0f && copyX - half < screenW) visible += storefront
                        }
                    }
                    most = maxOf(most, visible.size)
                    assertTrue("$themeId ${screenW.toInt()}x${screenH.toInt()} at step $step: ${visible.size} places on screen", visible.size <= 4)
                    assertEquals("$themeId at step $step: $visible on screen at once", visible.size, visible.toSet().size)
                    step++
                }
                assertEquals("$themeId: all six came by", Storefront.ALL.toSet(), seen)
            }
            println("STREET_ON_SCREEN ${screenW.toInt()}x${screenH.toInt()}: at most $most places on one screen")
        }
    }

    /**
     * How many places a screen shows, theme by theme, on the BV6600: the share of the scroll at which 1, 2,
     * 3 or 4 buildings among the houses are on it -- the maintainer was told he would see two or three a screen
     * instead of one or two. Each place counts while its paper is on screen; whole means the building is all on it.
     */
    @Test
    fun `how many buildings a screen shows, theme by theme`() {
        val screenW = 720f
        val screenH = 1440f
        val tileW = screenW * 2f
        val sb = StringBuilder("STREET_PER_SCREEN theme: places on screen 0/1/2/3/4 (share of the scroll), mean; whole 0/1/2/3/4, mean\n")
        var leastMean = Float.MAX_VALUE
        for (themeId in themes) {
            val places = StorefrontStreet.places(SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()))
            val any = IntArray(6)
            val whole = IntArray(6)
            val steps = 2000
            for (step in 0 until steps) {
                val left = step * tileW / steps
                var a = 0
                var w = 0
                for (place in places) {
                    val half = halfOnScreen(place, screenH)
                    for (t in -1..1) {
                        val x = (place.tileFractionX + t) * tileW
                        if (x + half > left && x - half < left + screenW) a++
                        if (x - half >= left && x + half <= left + screenW) w++
                    }
                }
                any[a]++
                whole[w]++
            }
            val meanAny = any.withIndex().sumOf { it.index * it.value } / steps.toFloat()
            val meanWhole = whole.withIndex().sumOf { it.index * it.value } / steps.toFloat()
            leastMean = minOf(leastMean, meanAny)
            sb.append("STREET_PER_SCREEN %-10s %s mean %.2f; whole %s mean %.2f\n".format(
                themeId, any.take(5).joinToString("/") { "%.2f".format(it / steps.toFloat()) }, meanAny,
                whole.take(5).joinToString("/") { "%.2f".format(it / steps.toFloat()) }, meanWhole,
            ))
            assertEquals("$themeId: a screen with no building among the houses", 0, any[0])
        }
        println(sb)
        assertTrue("a theme whose screens show under two buildings on the mean: $leastMean", leastMean >= 2f)
    }

    /**
     * **The street read in its order along the scroll is dealt in decks of six**: copy after copy, place after
     * place across the tile, every six places from the start of the street hold the six once each -- read off
     * the scroll on every built-in street, from the buildings each copy of each place shows, not from the
     * rule's own numbering. A street that counted its places without the tile's five (a copy's places
     * overlapping the next copy's) still kept two alike off the screen and failed here (a mutation of v5.13B2).
     */
    @Test
    fun `along the scroll every six places of the street hold the six`() {
        val screenW = 720f
        val tileW = screenW * 2f
        for (themeId in themes) {
            val places = StorefrontStreet.places(SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()))
            val seed = themeId.hashCode()
            val geom = GroundGeometry(0f, tileW, 0)
            val seen = HashMap<Int, Storefront>()
            for (step in 0 until 8 * 40) {
                val unwrapped = -step * (screenW / 20.0)
                var wrapped = unwrapped % tileW
                if (wrapped > 0.0) wrapped -= tileW
                geom.shiftXWrapped = wrapped.toFloat()
                geom.scrollTileBias = Math.round((unwrapped - wrapped) / tileW).toInt()
                for ((rank, place) in places.withIndex()) for (tileIndex in -1..1) {
                    val copy = SceneObjectRenderer.copyOf(place.tileFractionX, geom, tileIndex)
                    // Where the copy stands along the street, counted from the street's start.
                    val along = Math.round((SceneObjectRenderer.anchorXOf(place.tileFractionX, geom) + tileIndex * tileW - unwrapped) / tileW - place.tileFractionX).toInt()
                    seen[along * places.size + rank] = StorefrontStreet.storefrontAt(seed, StorefrontStreet.placeIndex(copy, rank, places.size))
                }
            }
            val ordered = seen.keys.sorted()
            var decks = 0
            for (start in ordered) {
                if (Math.floorMod(start, StorefrontStreet.DECK) != 0) continue
                val six = (start until start + StorefrontStreet.DECK).mapNotNull { seen[it] }
                if (six.size < StorefrontStreet.DECK) continue
                decks++
                assertEquals("$themeId: places $start..${start + 5} along the street: $six", Storefront.ALL.toSet(), six.toSet())
            }
            assertTrue("$themeId: no whole deck seen along the scroll", decks >= 5)
        }
    }

    /**
     * **Another seed is another shuffle, not the same one with another anchor**: the streets of two seeds agree on
     * about a sixth of their places, as two independent shuffles of six would; a street dealt from the same
     * shuffles with only the anchor moved agreed on most of them (a mutation of v5.13B2 the tests above let
     * through, when a round of the street was another seed).
     */
    @Test
    fun `two seeds agree on about a sixth of the places, as two shuffles would`() {
        var same = 0
        var asked = 0
        for ((seed, other) in seeds.zipWithNext()) for (place in 0 until 120) {
            asked++
            if (StorefrontStreet.storefrontAt(seed, place) == StorefrontStreet.storefrontAt(other, place)) same++
        }
        val share = same.toFloat() / asked
        println("STREET_SEEDS two seeds' streets agree on %.3f of the places".format(share))
        assertTrue("two seeds' streets agree on $share of the places", share in 0.10f..0.25f)
    }

    /**
     * **On the random themes too, never two alike on the screen**: a random theme's layout (`RandomSceneGenerator`,
     * its static objects rebuilt here from the same random sequence, as `StorefrontDetailTest` does) puts its
     * places where its candidates fell, from none to as many as it has building candidates in the shop band. The
     * rule of four in a row keeps two alike off any screen that shows four places or fewer; twenty thousand random
     * themes scrolled across their tile on the BV6600: how many places a tile, the most on one screen, and the
     * screens with two alike.
     */
    @Test
    fun `on the random themes no screen shows two alike`() {
        val screenW = 720f
        val screenH = 1440f
        val tileW = screenW * 2f
        val perTile = IntArray(8)
        var most = 0
        var alike = 0
        for (k in 0 until 20000) {
            val rnd = kotlin.random.Random(k xor 0x5EED)
            val pool = listOf(
                SceneObjectType.HOUSE, SceneObjectType.TREE,
                SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PALM_TREE, SceneObjectType.PARASOL,
                SceneObjectType.SKYSCRAPER, SceneObjectType.PENGUIN,
                SceneObjectType.BUNNY, SceneObjectType.EASTER_EGG,
            )
            val objects = RandomSceneGenerator.atMostFourPlaces(
                (0 until rnd.nextInt(3, 7)).map {
                    StaticSceneObject(type = pool.random(rnd), depthFraction = rnd.nextFloat(), tileFractionX = rnd.nextFloat(), scale = 0.82f + rnd.nextFloat() * 0.36f)
                },
            )
            val places = StorefrontStreet.places(SceneObjectLayout(staticObjects = objects, cars = emptyList()))
            perTile[places.size.coerceAtMost(7)]++
            if (places.size < 2) continue
            for (step in 0 until 200) {
                val left = step * tileW / 200
                val visible = ArrayList<Storefront>()
                for ((rank, place) in places.withIndex()) {
                    val half = halfOnScreen(place, screenH)
                    for (copy in -1..1) {
                        val x = (place.tileFractionX + copy) * tileW
                        if (x + half > left && x - half < left + screenW) {
                            visible += StorefrontStreet.storefrontAt(k, StorefrontStreet.placeIndex(copy, rank, places.size))
                        }
                    }
                }
                most = maxOf(most, visible.size)
                if (visible.size != visible.toSet().size) alike++
            }
        }
        println("STREET_RANDOM places a tile over 20000 random themes, 0..7: ${perTile.toList()}; the most on one screen $most; screens with two alike $alike")
        assertEquals("screens of a random theme with two alike", 0, alike)
        // And a random theme never stands five places in a tile, which twenty thousand of them never rolled and
        // about three in a million do: six building candidates in the shop band keep their first four as places,
        // the others go on the skyline.
        val six = (0 until 6).map { StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.35f + it * 0.1f, tileFractionX = it * 0.05f) }
        val capped = RandomSceneGenerator.atMostFourPlaces(six)
        assertEquals(StorefrontStreet.RUN, capped.count { StorefrontStreet.isPlace(it) })
        assertEquals(six.take(4), capped.take(4))
        assertTrue(capped.drop(4).all { it.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH && it.tileFractionX == six[capped.indexOf(it)].tileFractionX })
    }

    /**
     * **The generator itself stands no more than four**: `random:23018`, the first random theme whose rolls put
     * five building candidates in the shop band (found by searching the seeds from 0; about three in a million
     * do), is laid out with four places, its fifth candidate on the skyline where it was rolled, and everything
     * else as rolled.
     */
    @Test
    fun `a random theme that rolls five places stands four`() {
        val layout = RandomSceneGenerator.generateLayout("random:23018", 0xFF8899AA.toInt())
        val rnd = kotlin.random.Random(23018 xor 0x5EED)
        val pool = listOf(
            SceneObjectType.HOUSE, SceneObjectType.TREE,
            SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PALM_TREE, SceneObjectType.PARASOL,
            SceneObjectType.SKYSCRAPER, SceneObjectType.PENGUIN,
            SceneObjectType.BUNNY, SceneObjectType.EASTER_EGG,
        )
        val rolled = (0 until rnd.nextInt(3, 7)).map {
            StaticSceneObject(type = pool.random(rnd), depthFraction = rnd.nextFloat(), tileFractionX = rnd.nextFloat(), scale = 0.82f + rnd.nextFloat() * 0.36f)
        }
        assertEquals("random:23018 rolls five places", 5, rolled.count { StorefrontStreet.isPlace(it) })
        assertEquals(StorefrontStreet.RUN, layout.staticObjects.count { StorefrontStreet.isPlace(it) })
        assertEquals(RandomSceneGenerator.atMostFourPlaces(rolled), layout.staticObjects)
    }

    @Test
    fun `the card draws three of the street's first stretch, three different buildings, each in its row`() {
        for (themeId in themes) {
            val layout = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt())
            val places = StorefrontStreet.places(layout)
            val card = StorefrontStreet.firstStretch(themeId.hashCode(), places)
            assertEquals("$themeId: ${card.toList()}", 3, card.toSet().size)
            for ((row, band) in listOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR).withIndex()) {
                val rank = StorefrontStreet.cardPlaceOf(places, band)!!
                assertEquals("$themeId: the card's row $row is a place of that row", band, SceneObjectRenderer.variantFor(places[rank]))
                // The row's place the catalogue kept when a row had one: 0.444, 0.622, 0.800.
                assertEquals("$themeId: row $row", listOf(0.44444448f, 0.62222224f, 0.8f)[row], places[rank].depthFraction, 1e-6f)
                assertEquals("$themeId: the card's row $row is copy 0's place $rank", StorefrontStreet.storefrontAt(themeId.hashCode(), rank), card[row])
            }
        }
        // A layout with one place: its band's row has its building, the other two the street's next ones.
        val one = listOf(StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.62f, tileFractionX = 0.4f))
        val card = StorefrontStreet.firstStretch(7, one)
        assertEquals(3, card.toSet().size)
        assertEquals(StorefrontStreet.storefrontAt(7, 0), card[1])
    }

    /**
     * The draw loop numbers each copy it draws with [SceneObjectRenderer.copyOf], the expression the scroll
     * above is checked with, and with that copy's own tile index: read in the renderer's source, because the
     * loop draws on a canvas the JVM does not have. A copy numbered without its tile index -- the one that
     * comes in at the right edge before the wrap moves on -- would show the building of the copy to its left
     * and change under the eyes when the wrap moved (a mutation of v5.13B that every other test let through).
     */
    @Test
    fun `the draw loop hands every copy its own number`() {
        val text = source("SceneObjectRenderer.kt")
        val calls = Regex("""drawStaticObject\(canvas, r, [^\n]*""").findAll(text).map { it.value }.toList()
        assertEquals("the two draws of a static object: $calls", 2, calls.size)
        assertTrue(calls.toString(), calls.any { it.endsWith("copyOf(r.spec.tileFractionX, geom, tileIndex))") })
        assertTrue(calls.toString(), calls.any { it.endsWith("copyOf(r.spec.tileFractionX, geom, 0))") })
        assertTrue("the place's copy is dealt from that number", text.contains("StorefrontStreet.placeIndex(copy, r.place, places.size), streetDeckA,"))
    }

    /**
     * **Coming back into view changes nothing of the street** (v5.14, the maintainer's «C» of 2026-10-14, which took
     * back *«Nuovi edifici al ritorno sempre»* of 2026-10-12): a building changes only by scrolling. So the deal is
     * the theme's seed and the place alone (above), and nothing in the engine touches it when the wallpaper becomes
     * visible -- read in the sources, the engine and the renderer needing a phone: `onVisibilityChanged` hands the
     * renderer nothing about the street, and no round of the street is counted or handed on anywhere in the app.
     * The phone shows it: three returns from an app and the screen off and on again, the same buildings (v5.14B,
     * filmed on the BV6600). And what numbers the copies -- the scroll -- moves only by the frame's time times the scroll
     * speed, a frame's time capped at half a second, in the one renderer an engine builds.
     */
    @Test
    fun `coming back into view deals the street nothing new`() {
        val service = source("PaperWallpaperService.kt").substringAfter("override fun onVisibilityChanged(visible: Boolean) {")
            .substringBefore("override fun onOffsetsChanged(")
        assertTrue("onVisibilityChanged was not found", service.contains("thread.setVisible(drawing)"))
        for (word in listOf("Street", "street", "Round", "storefront", "Storefront")) {
            assertTrue("onVisibilityChanged names \"$word\" in code", service.lines().none { it.trim().let { l -> !l.startsWith("//") && l.contains(word) } })
        }
        val main = mainSources().walk().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("the app's sources were not found", main.size > 50)
        for (file in main) {
            val text = file.readText()
            assertTrue("${file.name} counts a round of the street", !text.contains("streetRound") && !text.contains("nextStreetRound"))
        }
        assertTrue(source("SceneObjectRenderer.kt").contains("StorefrontStreet.placeIndex(copy, r.place, places.size), streetDeckA, streetDeckB"))
        // Nothing renumbers the copies but the scroll itself (v5.14C): the accumulator a copy is counted from is set once,
        // to 0, and only ever grows by a frame's time times the scroll speed -- at *Scroll speed* 0 % it never moves -- and
        // a frame's time is capped at half a second on both paths, so a wallpaper that comes back after an hour moves on by
        // half a second's scroll; and the engine builds its renderer once, keeping it over a new surface.
        val writes = source("PaperRenderer.kt").lines().map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && Regex("""continuousScrollAccum\s*[-+*/]?=""").containsMatchIn(it) }
            .map { it.substringBefore(" //") }
        assertEquals(
            "what writes the scroll a copy is counted from",
            listOf("private var continuousScrollAccum = 0.0", "continuousScrollAccum += deltaSeconds * scrollSpeed * 0.04"),
            writes,
        )
        assertEquals("the engine builds its renderer more than once", 1, Regex("""renderer = PaperRenderer\(""").findAll(source("PaperWallpaperService.kt")).count())
        assertTrue("the GL path's frame time is not capped", source("GlRenderThread.kt").contains("callbacks.onGlDrawFrame(target, delta.coerceIn(0f, 0.5f))"))
        assertTrue("the Canvas path's frame time is not capped", source("PaperWallpaperService.kt").contains("((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.5f)"))
    }

    /**
     * On a built-in street every building takes the identity v5.13B gave it, whatever the plan does with the
     * places (v5.13B2 moved them all): its people, its colour and its figure stay. Six identities, all
     * different; and on a layout that is not a built-in street's own, the restaurant, the school and the bar
     * take the first place of their row as it stands. That they are the ones v5.13B gave is
     * `WindowPeopleCountTest`'s, which counts their people and colour against v5.13B's.
     */
    @Test
    fun `each building has an identity of its own, in its home's row, on every built-in street`() {
        for (themeId in themes) {
            val places = StorefrontStreet.places(SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()))
            val ids = StorefrontStreet.identities(places, themeId)!!
            assertEquals("$themeId: six identities, all different", 6, ids.map { SceneObjectRenderer.buildingSeedOf(it) }.toSet().size)
            for ((k, storefront) in Storefront.ALL.withIndex()) {
                assertEquals("$themeId: the $storefront's identity is in its home's row", storefront.home, SceneObjectRenderer.variantFor(ids[k]))
            }
            val asTheyStand = StorefrontStreet.identities(places)!!
            for (storefront in listOf(Storefront.RESTAURANT, Storefront.SCHOOL, Storefront.BAR)) {
                val first = places.first { SceneObjectRenderer.variantFor(it) == storefront.home }
                assertTrue("$themeId: as they stand, the $storefront's identity is its row's first place", asTheyStand[storefront.ordinal] === first)
            }
        }
        org.junit.Assert.assertNull(StorefrontStreet.identities(emptyList()))
    }

    /**
     * On a random theme too, with places wherever its towers fell: no two of the six share an identity's
     * seed -- the case the read-only review of v5.13B found: one place, in the school's band, made the
     * restaurant (moved half a tile from it) the fire station's twin (moved half a tile from its home).
     */
    @Test
    fun `on any layout the six identities are six - one place in a station's home included`() {
        for (depth in listOf(0.40f, 0.55f, 0.70f)) {
            val ids = StorefrontStreet.identities(listOf(StaticSceneObject(SceneObjectType.SKYSCRAPER, depth, 0.25f)))!!
            assertEquals("one place at depth $depth", 6, ids.map { SceneObjectRenderer.buildingSeedOf(it) }.toSet().size)
        }
        val rnd = kotlin.random.Random(613)
        repeat(2000) {
            val places = (0 until rnd.nextInt(1, 7)).map {
                StaticSceneObject(SceneObjectType.SKYSCRAPER, 0.30f + rnd.nextFloat() * 0.7f, rnd.nextFloat())
            }.sortedBy { it.tileFractionX }
            val ids = StorefrontStreet.identities(places)!!
            assertEquals("places $places", 6, ids.map { SceneObjectRenderer.buildingSeedOf(it) }.toSet().size)
        }
    }

    private fun source(name: String): String = java.io.File(mainSources(), "engine/$name").readText()

    /** The app's Kotlin sources, `src/main/kotlin/com/paperscrape/livewallpaper`. */
    private fun mainSources(): java.io.File {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper")
                if (java.io.File(f, "engine").isDirectory) return f
            }
            dir = dir.parentFile
        }
        error("src/main/kotlin")
    }
}
