package com.paperscrape.livewallpaper.engine

/**
 * The two groups the buildings among the houses keep their colours and their opening hours by: the
 * shops -- the restaurant, the bar and the shop -- and the public buildings -- the school, the fire
 * station and the police station. The towers are the third group, the Buildings category. Three
 * groups, each with colours and opening hours of its own:
 * `DESIGN_NOTES.md` §12, decision 62.
 */
enum class StorefrontGroup { SHOPS, PUBLIC }

/**
 * The six buildings that stand among the houses, and what each of them carries wherever it
 * stands: its group ([StorefrontGroup]: its colours and its opening hours), the people at its windows
 * ([kind]: children at the school, adults elsewhere) and the place its identity is taken from
 * ([home]).
 *
 * **A street has five places among the houses and six buildings to stand in them** ([StorefrontStreet]):
 * all six in every theme, taking turns as the wallpaper scrolls,
 * `DESIGN_NOTES.md` §12, decision 60. The place -- its depth, and with it the size table's row
 * the building is drawn at ([SceneObjectRenderer.variantFor]) -- is the street's; which building
 * stands in it is this.
 *
 * **[home] is not where the building stands.** On a built-in street the restaurant, the school and the
 * bar take their identity from the place each stood in on the v5.12 street, kept as a literal
 * ([StorefrontStreet.identities]) -- the coin of Color 1 or Color 2, the bar's figure, the seed of the
 * people at its windows -- so the restaurant of a theme has the same people, colour and figure in
 * every place it stands (how many people a building shows does not change,
 * `DESIGN_NOTES.md` §12, decision 54).
 * The other three take theirs from a place too, half a tile along ([StorefrontStreet.identities]),
 * so no two of the six share one.
 */
enum class Storefront(
    val group: StorefrontGroup,
    internal val home: SceneSpace.SceneVariant,
    internal val kind: WindowBuildingKind,
    /**
     * The wider side of the building's ink about its foot, in its own piece units: every part of every
     * figure, snow, lamps and busts left out, measured off the shipped PNGs (`StorefrontFrontVisibilityTest`
     * re-measures it). What the street's places are planned with (`SceneObjectCatalog`): a front as wide
     * as the building draws, at the place's metre.
     */
    internal val halfWidthUnits: Float,
) {
    RESTAURANT(StorefrontGroup.SHOPS, SceneSpace.SceneVariant.RESTAURANT, WindowBuildingKind.COMMERCIAL, 52.667f),
    SCHOOL(StorefrontGroup.PUBLIC, SceneSpace.SceneVariant.SCHOOL, WindowBuildingKind.SCHOOL, 55.333f),
    BAR(StorefrontGroup.SHOPS, SceneSpace.SceneVariant.BAR, WindowBuildingKind.COMMERCIAL, 37.333f),
    SHOP(StorefrontGroup.SHOPS, SceneSpace.SceneVariant.RESTAURANT, WindowBuildingKind.COMMERCIAL, 35.0f),
    FIRE_STATION(StorefrontGroup.PUBLIC, SceneSpace.SceneVariant.SCHOOL, WindowBuildingKind.STATION, 51.333f),
    POLICE_STATION(StorefrontGroup.PUBLIC, SceneSpace.SceneVariant.BAR, WindowBuildingKind.STATION, 52.667f),
    ;

    /** The building drawn: its family of pieces in the generated table. */
    internal val family: BuildingFamily
        get() = NeighbourhoodTable.STOREFRONTS.getValue(this)

    /**
     * How tall the building's ink stands above its foot, in its own piece units: the top of the highest
     * part any of its deals draws, snow, lamps and the people at its windows left out. Its front
     * is measured both ways -- as tall as the family declares and as tall as it draws -- by the plan of the
     * places (`SceneObjectCatalog.frontRects`) and by `StorefrontFrontVisibilityTest`, which measures it the
     * same way off the same table.
     */
    internal val inkTallUnits: Float by lazy {
        var top = 0f
        for (slot in family.slots) for (piece in slot.options + slot.heights) for (part in piece.parts) {
            if (part.res == 0 || part.role == PartRole.SNOW || part.role == PartRole.LAMP || part.role == PartRole.OCCUPANTS) continue
            top = maxOf(top, -part.y)
        }
        top
    }

    /**
     * Whether this building keeps its group's opening hours: all but the fire station and the police
     * station, which never close; the public buildings' hours are the school's.
     * `DESIGN_NOTES.md` §12, decision 62.
     */
    internal val keepsHours: Boolean
        get() = this != FIRE_STATION && this != POLICE_STATION

    /** Whether this building takes its identity from its [home] itself, not half a tile along: the restaurant, the school and the bar. */
    internal val stoodThere: Boolean
        get() = ordinal < 3

    internal companion object {
        /** [values], once: the street reads it on the draw path, and `values()` copies the array. */
        val ALL: Array<Storefront> = values()
    }
}

/**
 * Which of the six buildings stands in each place among the houses, copy by copy of the street: five
 * places a tile, and never the same building in four places in a row.
 *
 * ### The street
 *
 * The scene repeats every tile -- two screens wide ([GroundGeometry.tileWidth]) -- and the street is
 * the tile's places in a row, copy after copy: place `p` of the street is the `rank`-th place of the
 * tile (by where it stands across the tile) of copy `c`, `p = c * placesPerTile + rank` ([placeIndex]).
 * A **copy** is one physical repetition of the tile, [SceneObjectRenderer.copyOf] in
 * [SceneObjectRenderer.draw] (the number the falling leaves key on too): it keeps its number for as
 * long as it exists, so the building in a place never changes under the eyes, and scrolling back finds
 * the same one.
 *
 * ### Decks of six, and never the same building in four places in a row
 *
 * The street is dealt in **decks of six**: places `6d .. 6d + 5` hold the six buildings once each. A
 * built-in street has five places a tile, and the plan never lets a screen show five of them
 * (`SceneObjectCatalog`, `fiveOnAScreen`), so **no building is twice in any four places in a row**, which is
 * every set of places a screen can show at once: never two alike on the screen. A deck is a **head** -- its
 * first three places -- and a **tail** -- its last three. The street has an anchor, one of the six drawn by
 * its seed, which stands in every deck's head with two of the other five; the tail holds the other three. So no deck's head is the tail before it, and the seam between two decks is cleared by
 * order alone: the tail ends on a building that is not in the next head, the next head never starts with
 * one of the tail's buildings and never has the tail's second-to-last second. Every order is the deck's own
 * shuffle but for those moves, and every place is worked out from three shuffles, the deck's and its two
 * neighbours': nothing is carried along the street.
 *
 * Each deck holds all six -- 1.2 tiles, 2.4 screens of a built-in street -- and any eleven places in a row
 * hold a whole deck.
 *
 * ### Deterministic
 *
 * Nothing but the theme's seed and the place: no clock, no random number. The same frame is the same
 * picture, which is what the goldens and the tests rely on. **A building changes only by scrolling**: a
 * copy keeps its buildings for as long as it exists, coming back from an app and with the screen on again
 * alike, and a new one comes in from the edge as the scene scrolls -- at *Scroll speed* 0 % never; a new
 * engine (the phone restarted, the app updated) starts the theme's street again at copy 0, where the
 * scroll starts. `DESIGN_NOTES.md` §12, decision 60.
 */
internal object StorefrontStreet {

    /** How many places a deck deals: one of each building. */
    const val DECK = 6

    /** A deck's head and its tail: three places each. */
    private const val HALF = DECK / 2

    /**
     * **No building twice in this many places in a row**, across a deck's seam too: so never two alike on a
     * screen that shows this many places or fewer -- every screen of a built-in street (`SceneObjectCatalog`,
     * `fiveOnAScreen`) and of a random theme, which stands at most this many places in a tile
     * (`RandomSceneGenerator.atMostFourPlaces`).
     */
    const val RUN = 4

    /** The noise channel of the decks' shuffles ([CandidateNoise]): a number no other attribute uses. */
    private const val SHUFFLE_CHANNEL = 601

    /** The noise channel of the street's anchor. */
    private const val ANCHOR_CHANNEL = 602

    /** How many coins a deck's shuffles draw: its address stride on [SHUFFLE_CHANNEL]. */
    private const val COINS_A_DECK = 16

    /** The street's place for the `rank`-th place of a tile, in copy [copy]. */
    fun placeIndex(copy: Int, rank: Int, placesPerTile: Int): Int = copy * placesPerTile + rank

    /**
     * The building at street place [place] for the theme seeded [seed]. [a] and [b] are scratch arrays of
     * [DECK] ints, overwritten: the draw path asks this once a place a frame and allocates nothing.
     */
    fun storefrontAt(seed: Int, place: Int, a: IntArray, b: IntArray): Storefront {
        val anchor = anchorOf(seed)
        val deck = Math.floorDiv(place, DECK)
        val i = Math.floorMod(place, DECK)
        shuffled(seed, anchor, deck, a)
        if (i < HALF) {
            shuffled(seed, anchor, deck - 1, b)
            endTail(b, a)
            startHead(a, b)
        } else {
            shuffled(seed, anchor, deck + 1, b)
            endTail(a, b)
        }
        return Storefront.ALL[a[i]]
    }

    /** [storefrontAt], with scratch arrays of its own: for the card and the tests, off the draw path. */
    fun storefrontAt(seed: Int, place: Int): Storefront =
        storefrontAt(seed, place, IntArray(DECK), IntArray(DECK))

    /** The anchor of the street seeded [seed]: the building every deck has in its head, drawn by the seed. */
    private fun anchorOf(seed: Int): Int =
        Math.floorMod((CandidateNoise.value(seed, 0, ANCHOR_CHANNEL) * DECK).toInt(), DECK)

    /**
     * Deck [deck]'s shuffle into [into], before its seams are cleared: [anchor] and two of the other five
     * in its head, the other three in its tail, each half in an order of its own -- Fisher-Yates, one coin a
     * step, on the street's seed.
     */
    private fun shuffled(s: Int, anchor: Int, deck: Int, into: IntArray) {
        into[0] = anchor
        var w = 1
        for (k in 0 until DECK) if (k != anchor) into[w++] = k
        shuffle(s, deck, into, from = 1, until = DECK, coin = 0)
        shuffle(s, deck, into, from = 0, until = HALF, coin = 5)
        shuffle(s, deck, into, from = HALF, until = DECK, coin = 9)
    }

    /** Fisher-Yates over `into[from until until]`, its coins `coin ..` of the deck's address. */
    private fun shuffle(s: Int, deck: Int, into: IntArray, from: Int, until: Int, coin: Int) {
        for (k in until - 1 downTo from + 1) {
            val n = k - from + 1
            val j = from + (CandidateNoise.value(s, deck * COINS_A_DECK + coin + (k - from), SHUFFLE_CHANNEL) * n).toInt().coerceIn(0, n - 1)
            val t = into[k]
            into[k] = into[j]
            into[j] = t
        }
    }

    /**
     * [deck]'s tail made to end on a building that is not in [next]'s head: the last of its tail that is
     * not moves to the end, the others keeping their order. The anchor is in every head and in no tail, so
     * at most two of a tail's three are in the next head.
     */
    private fun endTail(deck: IntArray, next: IntArray) {
        var k = DECK - 1
        while (k >= HALF && inHead(next, deck[k])) k--
        val moved = deck[k]
        for (m in k until DECK - 1) deck[m] = deck[m + 1]
        deck[DECK - 1] = moved
    }

    /**
     * [deck]'s head ordered against the tail before it, [previous] with its tail already ended
     * ([endTail]): the tail's second-to-last, if this head holds it, goes third; its first, if it stands
     * first, swaps with the second. The tail's last is in no head that follows it.
     */
    private fun startHead(deck: IntArray, previous: IntArray) {
        val first = previous[HALF]
        val second = previous[HALF + 1]
        for (k in 0 until HALF - 1) {
            if (deck[k] == second) { deck[k] = deck[HALF - 1]; deck[HALF - 1] = second; break }
        }
        if (deck[0] == first) { deck[0] = deck[1]; deck[1] = first }
    }

    private fun inHead(deck: IntArray, building: Int): Boolean =
        deck[0] == building || deck[1] == building || deck[2] == building

    /**
     * The places among the houses of a layout: every building candidate of the shop band (deeper than
     * [SceneSpace.BUILDING_TOWER_MAX_DEPTH]; `SceneCustomization.keepCandidate` never thins them), in the
     * order they stand across the tile -- by `tileFractionX`, then by depth. A built-in street has five.
     */
    fun places(layout: SceneObjectLayout): List<StaticSceneObject> =
        layout.staticObjects
            .filter { isPlace(it) }
            .sortedWith(PLACE_ORDER)

    private val PLACE_ORDER = compareBy<StaticSceneObject>({ it.tileFractionX }, { it.depthFraction })

    /**
     * The built-in theme whose street's places [places] are -- the same positions to the bit, the plan's
     * ([ShopPlanTable.SHOP_X]) -- or null. A built-in theme saved with five places stores its street with
     * them where the plan put them, and no other street stands its places there: a street saved by v5.12
     * has three, a random theme's stand where they were rolled.
     */
    internal fun builtInStreetOf(places: List<StaticSceneObject>): String? {
        val xs = FloatArray(places.size) { places[it].tileFractionX }.also { it.sort() }
        for ((id, planned) in ShopPlanTable.SHOP_X) {
            if (planned.size == xs.size && planned.copyOf().also { it.sort() }.contentEquals(xs)) return id
        }
        return null
    }

    /** Whether [spec] is a place among the houses: a building candidate of the shop band. */
    fun isPlace(spec: StaticSceneObject): Boolean =
        spec.type == SceneObjectType.SKYSCRAPER && spec.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH

    private fun identity(x: Int, depth: Int, scale: Int, silhouette: Int) = StaticSceneObject(
        SceneObjectType.SKYSCRAPER,
        depthFraction = Float.fromBits(depth),
        tileFractionX = Float.fromBits(x),
        scale = Float.fromBits(scale),
        silhouette = silhouette,
    )

    /**
     * **The six buildings' identities on each built-in street, fixed** -- the object each one's colour
     * coin, figure and people are taken from ([identities]), in [Storefront.ALL]'s order, bit for bit, so a
     * literal: the places move, the identities do not. The restaurant, the school and the bar take theirs
     * from where they stood on the v5.12 street -- that release's place of each, its position, depth and
     * figure -- and the shop, the fire station and the police station from the place of their
     * [Storefront.home] on the v5.13B street, half a tile along. So every building keeps the people at its
     * windows, the colour and the figure it has (how many people a building shows does not change,
     * `DESIGN_NOTES.md` §12, decision 54). `WindowPeopleCountTest` counts them, theme by theme: the
     * restaurant, the school and the bar against the 5.12, the shop and the two stations against v5.13B.
     */
    private val IDENTITIES: Map<String, Array<StaticSceneObject>> = mapOf(
        "sunset" to arrayOf(
            identity(0x3f2e147b /* 0.68 */, 0x3ee38e3a /* 0.44444448 */, 0x3f6f2535 /* 0.9341615 */, 0), // RESTAURANT
            identity(0x3ee66666 /* 0.45 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f79453c /* 0.9737127 */, 0), // SCHOOL
            identity(0x3de147ae /* 0.11 */, 0x3f4ccccd /* 0.8 */, 0x3f83ee3a /* 1.0307076 */, 1), // BAR
            identity(0x3f333333 /* 0.7 */, 0x3ee38e3a /* 0.44444448 */, 0x3f6f2535 /* 0.9341615 */, 0), // SHOP
            identity(0x3f733333 /* 0.95 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f79453c /* 0.9737127 */, 0), // FIRE_STATION
            identity(0x3e051eb8 /* 0.13 */, 0x3f4ccccd /* 0.8 */, 0x3f83ee3a /* 1.0307076 */, 0), // POLICE_STATION
        ),
        "autumn" to arrayOf(
            identity(0x3e4ccccc /* 0.19999999 */, 0x3ee38e3a /* 0.44444448 */, 0x3f843b20 /* 1.0330544 */, 0), // RESTAURANT
            identity(0x3ed1eb85 /* 0.41 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f84a4fe /* 1.0362852 */, 0), // SCHOOL
            identity(0x3cf5c28f /* 0.03 */, 0x3f4ccccd /* 0.8 */, 0x3f6e9141 /* 0.9319039 */, 1), // BAR
            identity(0x3f30a3d7 /* 0.69 */, 0x3ee38e3a /* 0.44444448 */, 0x3f843b20 /* 1.0330544 */, 0), // SHOP
            identity(0x3f68f5c2 /* 0.90999997 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f84a4fe /* 1.0362852 */, 0), // FIRE_STATION
            identity(0x3e0f5c28 /* 0.13999999 */, 0x3f4ccccd /* 0.8 */, 0x3f6e9141 /* 0.9319039 */, 0), // POLICE_STATION
        ),
        "winter" to arrayOf(
            identity(0x3efae147 /* 0.48999998 */, 0x3ee38e3a /* 0.44444448 */, 0x3f853119 /* 1.0405608 */, 0), // RESTAURANT
            identity(0x3e8a3d70 /* 0.26999998 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f6cd82d /* 0.9251736 */, 0), // SCHOOL
            identity(0x3eb851eb /* 0.35999998 */, 0x3f4ccccd /* 0.8 */, 0x3f823316 /* 1.017184 */, 0), // BAR
            identity(0x3f7d70a4 /* 0.99 */, 0x3ee38e3a /* 0.44444448 */, 0x3f853119 /* 1.0405608 */, 0), // SHOP
            identity(0x3f266666 /* 0.65 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f6cd82d /* 0.9251736 */, 0), // FIRE_STATION
            identity(0x3f428f5c /* 0.76 */, 0x3f4ccccd /* 0.8 */, 0x3f823316 /* 1.017184 */, 0), // POLICE_STATION
        ),
        "desert" to arrayOf(
            identity(0x3ecccccc /* 0.39999998 */, 0x3ee38e3a /* 0.44444448 */, 0x3f804e84 /* 1.0023961 */, 0), // RESTAURANT
            identity(0x3ca3d70a /* 0.02 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f882bec /* 1.0638404 */, 0), // SCHOOL
            identity(0x3e4ccccc /* 0.19999999 */, 0x3f4ccccd /* 0.8 */, 0x3f7abee2 /* 0.97947514 */, 1), // BAR
            identity(0x3f666666 /* 0.9 */, 0x3ee38e3a /* 0.44444448 */, 0x3f804e84 /* 1.0023961 */, 0), // SHOP
            identity(0x3f051eb8 /* 0.52 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f882bec /* 1.0638404 */, 0), // FIRE_STATION
            identity(0x3c23d700 /* 0.00999999 */, 0x3f4ccccd /* 0.8 */, 0x3f7abee2 /* 0.97947514 */, 0), // POLICE_STATION
        ),
        "christmas" to arrayOf(
            identity(0x3e999999 /* 0.29999998 */, 0x3ee38e3a /* 0.44444448 */, 0x3f89d09a /* 1.0766785 */, 0), // RESTAURANT
            identity(0x3e051eb8 /* 0.13 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f84a3f6 /* 1.0362537 */, 0), // SCHOOL
            identity(0x3f2b851e /* 0.66999996 */, 0x3f4ccccd /* 0.8 */, 0x3f7663e1 /* 0.96246153 */, 1), // BAR
            identity(0x3f4ccccc /* 0.79999995 */, 0x3ee38e3a /* 0.44444448 */, 0x3f89d09a /* 1.0766785 */, 0), // SHOP
            identity(0x3f266666 /* 0.65 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f84a3f6 /* 1.0362537 */, 0), // FIRE_STATION
            identity(0x3e199998 /* 0.14999998 */, 0x3f4ccccd /* 0.8 */, 0x3f7663e1 /* 0.96246153 */, 0), // POLICE_STATION
        ),
        "new_year" to arrayOf(
            identity(0x3f000000 /* 0.5 */, 0x3ee38e3a /* 0.44444448 */, 0x3f6ecce5 /* 0.93281394 */, 0), // RESTAURANT
            identity(0x3c23d70a /* 0.01 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f72f80f /* 0.9490976 */, 0), // SCHOOL
            identity(0x3f2b851e /* 0.66999996 */, 0x3f4ccccd /* 0.8 */, 0x3f87197d /* 1.0554653 */, 0), // BAR
            identity(0x3f7d70a4 /* 0.99 */, 0x3ee38e3a /* 0.44444448 */, 0x3f6ecce5 /* 0.93281394 */, 0), // SHOP
            identity(0x3f428f5c /* 0.76 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f72f80f /* 0.9490976 */, 0), // FIRE_STATION
            identity(0x3f051eb8 /* 0.52 */, 0x3f4ccccd /* 0.8 */, 0x3f87197d /* 1.0554653 */, 0), // POLICE_STATION
        ),
        "beach" to arrayOf(
            identity(0x3cf5c28f /* 0.03 */, 0x3ee38e3a /* 0.44444448 */, 0x3f7931a0 /* 0.97341347 */, 0), // RESTAURANT
            identity(0x3e0f5c29 /* 0.14 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f882af9 /* 1.0638114 */, 0), // SCHOOL
            identity(0x3e8f5c29 /* 0.28 */, 0x3f4ccccd /* 0.8 */, 0x3f6c7963 /* 0.9237272 */, 0), // BAR
            identity(0x3f4a3d70 /* 0.78999996 */, 0x3ee38e3a /* 0.44444448 */, 0x3f7931a0 /* 0.97341347 */, 0), // SHOP
            identity(0x3f0a3d71 /* 0.54 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f882af9 /* 1.0638114 */, 0), // FIRE_STATION
            identity(0x3f63d70a /* 0.89 */, 0x3f4ccccd /* 0.8 */, 0x3f6c7963 /* 0.9237272 */, 0), // POLICE_STATION
        ),
        "city" to arrayOf(
            identity(0x3e8a3d70 /* 0.26999998 */, 0x3ee38e3a /* 0.44444448 */, 0x3f811041 /* 1.0083085 */, 0), // RESTAURANT
            identity(0x3c23d70a /* 0.01 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f807837 /* 1.0036687 */, 0), // SCHOOL
            identity(0x3f000000 /* 0.5 */, 0x3f4ccccd /* 0.8 */, 0x3f6d043d /* 0.9258459 */, 0), // BAR
            identity(0x3f028f5c /* 0.51 */, 0x3ee38e3a /* 0.44444448 */, 0x3f811041 /* 1.0083085 */, 0), // SHOP
            identity(0x3f266666 /* 0.65 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f807837 /* 1.0036687 */, 0), // FIRE_STATION
            identity(0x3f6147ae /* 0.88 */, 0x3f4ccccd /* 0.8 */, 0x3f6d043d /* 0.9258459 */, 0), // POLICE_STATION
        ),
        "tundra" to arrayOf(
            identity(0x3efae147 /* 0.48999998 */, 0x3ee38e3a /* 0.44444448 */, 0x3f8602fd /* 1.0469662 */, 0), // RESTAURANT
            identity(0x3e8a3d70 /* 0.26999998 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f6ff128 /* 0.9372735 */, 0), // SCHOOL
            identity(0x3cf5c28f /* 0.03 */, 0x3f4ccccd /* 0.8 */, 0x3f722e1a /* 0.94601595 */, 0), // BAR
            identity(0x3f7d70a4 /* 0.99 */, 0x3ee38e3a /* 0.44444448 */, 0x3f8602fd /* 1.0469662 */, 0), // SHOP
            identity(0x3f07ae14 /* 0.53 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f6ff128 /* 0.9372735 */, 0), // FIRE_STATION
            identity(0x3f23d70a /* 0.64 */, 0x3f4ccccd /* 0.8 */, 0x3f722e1a /* 0.94601595 */, 0), // POLICE_STATION
        ),
        "easter" to arrayOf(
            identity(0x3d8f5c29 /* 0.07 */, 0x3ee38e3a /* 0.44444448 */, 0x3f7c39c9 /* 0.98525673 */, 0), // RESTAURANT
            identity(0x3ee147ae /* 0.44 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f740434 /* 0.95318913 */, 0), // SCHOOL
            identity(0x3e4ccccc /* 0.19999999 */, 0x3f4ccccd /* 0.8 */, 0x3f821fbf /* 1.0165938 */, 0), // BAR
            identity(0x3f11eb85 /* 0.57 */, 0x3ee38e3a /* 0.44444448 */, 0x3f7c39c9 /* 0.98525673 */, 0), // SHOP
            identity(0x3f70a3d7 /* 0.94 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f740434 /* 0.95318913 */, 0), // FIRE_STATION
            identity(0x3f428f5c /* 0.76 */, 0x3f4ccccd /* 0.8 */, 0x3f821fbf /* 1.0165938 */, 0), // POLICE_STATION
        ),
        "halloween" to arrayOf(
            identity(0x3ca3d70a /* 0.02 */, 0x3ee38e3a /* 0.44444448 */, 0x3f7a791b /* 0.9784104 */, 0), // RESTAURANT
            identity(0x3e8a3d70 /* 0.26999998 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f84ee9d /* 1.0385319 */, 0), // SCHOOL
            identity(0x3de147ae /* 0.11 */, 0x3f4ccccd /* 0.8 */, 0x3f7dade3 /* 0.99093455 */, 0), // BAR
            identity(0x3f051eb8 /* 0.52 */, 0x3ee38e3a /* 0.44444448 */, 0x3f7a791b /* 0.9784104 */, 0), // SHOP
            identity(0x3f451eb8 /* 0.77 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f84ee9d /* 1.0385319 */, 0), // FIRE_STATION
            identity(0x3c23d700 /* 0.00999999 */, 0x3f4ccccd /* 0.8 */, 0x3f7dade3 /* 0.99093455 */, 0), // POLICE_STATION
        ),
        "spring" to arrayOf(
            identity(0x3e947ae1 /* 0.29 */, 0x3ee38e3a /* 0.44444448 */, 0x3f847bcc /* 1.035028 */, 0), // RESTAURANT
            identity(0x3e051eb8 /* 0.13 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f83f024 /* 1.030766 */, 0), // SCHOOL
            identity(0x3f147ae1 /* 0.58 */, 0x3f4ccccd /* 0.8 */, 0x3f752408 /* 0.95758104 */, 0), // BAR
            identity(0x3f4a3d70 /* 0.78999996 */, 0x3ee38e3a /* 0.44444448 */, 0x3f847bcc /* 1.035028 */, 0), // SHOP
            identity(0x3f6e147a /* 0.92999995 */, 0x3f1f49f5 /* 0.62222224 */, 0x3f83f024 /* 1.030766 */, 0), // FIRE_STATION
            identity(0x3f23d70a /* 0.64 */, 0x3f4ccccd /* 0.8 */, 0x3f752408 /* 0.95758104 */, 0), // POLICE_STATION
        ),
    )

    /**
     * Each building's identity among [places] (see [Storefront]): the object its colour coin, its figure
     * and its people are taken from, in [Storefront.ALL]'s order. On a built-in street ([themeId], its
     * own layout) the fixed ones ([IDENTITIES]). Elsewhere -- a saved theme, a theme of the user's
     * own, a random one -- the restaurant, the school and the bar take the first place of their own row as
     * it stands, and the shop, the fire station and the police station their [Storefront.home]'s, half a
     * tile along. Where a layout has no place in a building's home (a random theme), it takes the first
     * place's, moved along by a share of the tile of its own. **No two of the six share one**: an
     * identity whose seed ([SceneObjectRenderer.buildingSeedOf], its position) an earlier one already has
     * is moved along a ninety-seventh of the tile until it is its own -- which never happens on the
     * built-in streets (`StorefrontStreetTest`); without it, on a random theme whose only place stands in a
     * station's home the restaurant would be that station's twin. Null when there is no place at all.
     */
    fun identities(layout: SceneObjectLayout, themeId: String? = null): Array<StaticSceneObject>? {
        val stored = places(layout).map { layout.placesAsSaved[it] ?: it }.sortedWith(PLACE_ORDER)
        return identities(stored, themeId?.takeIf { it in IDENTITIES } ?: builtInStreetOf(stored))
    }

    /**
     * The same, of [places] as they stand. [identities] of a layout reads a saved street's places where the
     * theme stores them, before the loader moved any ([SceneObjectLayout.placesAsSaved]): moving a
     * place keeps its buildings' colours and people. And a saved street whose places are a built-in street's
     * -- a built-in theme saved with five places, under its own name or as a copy, or shared as a file -- is that
     * street, and its six buildings keep the identities the built-in theme gives them ([builtInStreetOf]):
     * saving a theme keeps their colours and people (a copy, a new id, deals them to other places).
     */
    fun identities(places: List<StaticSceneObject>, themeId: String? = null): Array<StaticSceneObject>? {
        if (places.isEmpty()) return null
        themeId?.let { IDENTITIES[it] }?.let { return it.copyOf() }
        val out = ArrayList<StaticSceneObject>(Storefront.ALL.size)
        for ((k, storefront) in Storefront.ALL.withIndex()) {
            val home = places.firstOrNull { SceneObjectRenderer.variantFor(it) == storefront.home }
            var identity = when {
                home != null && storefront.stoodThere -> home
                home != null -> home.copy(tileFractionX = (home.tileFractionX + 0.5f).mod(1f), silhouette = 0)
                else -> places[0].copy(
                    tileFractionX = (places[0].tileFractionX + 0.5f + (k + 1) / 14f).mod(1f),
                    silhouette = 0,
                )
            }
            while (out.any { SceneObjectRenderer.buildingSeedOf(it) == SceneObjectRenderer.buildingSeedOf(identity) }) {
                identity = identity.copy(tileFractionX = (identity.tileFractionX + 1f / 97f).mod(1f))
            }
            out += identity
        }
        return out.toTypedArray()
    }

    /**
     * The three buildings a gallery card draws among its houses, far to near (its restaurant's, school's
     * and bar's rows): **three of the street's first stretch**, copy 0 -- one stretch of the street, not the
     * buildings a given wallpaper shows, which deals others as it scrolls -- in each row of the size table, the place of copy 0 the card stands for it ([cardPlaceOf]); the far
     * place of the restaurant's row and of the bar's are left out (`DESIGN_NOTES.md` §12, decision 65). A row
     * with no place on the layout (a random theme) takes the next places of the street, so the three are
     * three different buildings.
     */
    fun firstStretch(seed: Int, places: List<StaticSceneObject>): Array<Storefront> {
        val bands = arrayOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR)
        val out = arrayOfNulls<Storefront>(bands.size)
        for ((row, band) in bands.withIndex()) {
            val rank = cardPlaceOf(places, band) ?: continue
            out[row] = storefrontAt(seed, placeIndex(0, rank, places.size))
        }
        var next = places.size
        for (row in bands.indices) {
            while (out[row] == null) {
                val candidate = storefrontAt(seed, next++)
                if (candidate !in out) out[row] = candidate
            }
        }
        return Array(bands.size) { out[it]!! }
    }

    /**
     * The rank among [places] of the place a gallery card draws for [band]: of the places in that row of the
     * size table, the middle one by depth, the nearer of two (0.444, 0.622 and 0.800 on a built-in street),
     * then the first across the tile. Null when the row has none.
     */
    internal fun cardPlaceOf(places: List<StaticSceneObject>, band: SceneSpace.SceneVariant): Int? {
        val row = places.indices
            .filter { SceneObjectRenderer.variantFor(places[it]) == band }
            .sortedWith(compareBy({ places[it].depthFraction }, { it }))
        return row.getOrNull(row.size / 2)
    }
}
