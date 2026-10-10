package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Every length system in this project is called `_UNITS` -- [frames] lists them -- and this is what
 * stops two of them being compared.** `BACKLOG_v4_25.md` item 61 is the search that found the class; this is
 * proposal **C** of the three it lists, taken in v4.26.
 *
 * ### The rule, in one sentence
 *
 * Every sprite family's local unit is one third of a pixel of *that family's own PNG*
 * ([SpriteBlitter.SPRITE_PIXELS_PER_UNIT]), so a number in the car's units and a number in a
 * seated bust's units are different physical lengths that look identical in source. **An
 * expression that mentions two frames and does not contain one of the declared conversions is a
 * defect.** That sentence was already true and enforced by nothing; it is enforced here.
 *
 * It had already been broken twice in one release, in two files, and both times it was found by
 * accident: `build_people_concepts.SEATED_HALF_BAND` (a bust length justified against a car-unit
 * seat pitch, which shipped a whole release of squashed heads) and
 * `VehiclePedestrianScaleTest.WIDEST_SEATABLE_HEAD_BUST_UNITS` (a bust width compared directly
 * against the seat pitch: the margin read as one unit and is eleven).
 *
 * ### Why C and not A or B
 *
 * **A**, putting the frame in every name, catches nothing automatically -- it only makes a mixed
 * expression *read* wrong to a human, and the second defect proves a human reads past it. **B**,
 * a `value class` per frame, makes the mistake unrepresentable, but those types reach the draw
 * path where `AI_PROJECT_RULES.md` 5.1 forbids per-frame allocation, so its real cost is the
 * boxing audit of 5.11 rather than the refactor. **C is the only one that keeps working after the
 * next redraw**, and the only one that can be shown to bite by mutating the source.
 *
 * ### The one thing added to proposal C as the backlog describes it
 *
 * The backlog's own stated limit on C is that *it sees constants, not local variables* -- and the
 * second defect compared a constant against a local (`val pitch = CAR_PASSENGER_X_UNITS -
 * CAR_HEAD_X_UNITS`), so a constant-only scan would have missed it, exactly as the original scan
 * did. So the frame is **propagated through local `val` declarations**: a `val` whose initialiser
 * resolves to one frame carries that frame afterwards, and a `val` whose initialiser contains a
 * conversion becomes a conversion itself. Both known defects are inside what this sees, and
 * `the rule bites on a mixed expression` renders that claim falsifiable.
 *
 * ### A frame is a transform, not a sprite
 *
 * `car_lamp_front` is blitted through [SpriteScale.SCENE_UNITS] inside the car's transform *and*
 * inside the fire engine's, so its own size is a length in **both** frames, and comparing it
 * against either vehicle's numbers is right. That is why a constant declares a *set* of frames
 * rather than one, and why the three sites item 61 read as "mixed and correct" need no exception:
 * they are not mixed.
 */
class UnitFrameTest {

    // ---------------------------------------------------------------- the frames

    /**
     * One entry per length system. The key is what appears in a failure message; the value says
     * what one unit of it is, because that is the only thing that makes two of them comparable or
     * not.
     */
    private val frames = mapOf(
        "car" to "1/3 px of car_body; 53 units is CAR_METRES_TALL",
        "firetruck" to "1/3 px of firetruck_body; 68 units is FIRE_TRUCK_METRES_TALL",
        "bustCar" to "1/3 px of person_*_head_car, the shared 38x42 canvas",
        "bustWindow" to "1/3 px of person_*_head_window, the shared 49x57 canvas",
        "walk" to "1/3 px of person_*_walk*; 80 units is a person",
        "building" to "1/3 px of that building's own sprite (house pane, shop, tower)",
        "preview" to "ThemePreviewScene's own 320x240 description canvas",
        "cloud" to "1/3 px of cloud_body",
        "star" to "1/3 px of star_sparkle",
        "celestial" to "1/3 px of the sun and moon discs",
        "sunGlow" to "the sun glow's own raw-pixel disc",
        "firework" to "1/3 px of firework",
        "rainbow" to "1/3 px of rainbow",
        "lightning" to "1/3 px of lightning_bolt",
        "splash" to "1/3 px of the dolphin's splash",
        "santaSleigh" to "1/3 px of the sleigh",
        "dolphin" to "1/3 px of dolphin_body",
        "sailboat" to "1/3 px of sailboat_hull and sailboat_sail",
        "flower" to "1/3 px of ground_flowers_bloom / ground_flowers_dry (one canvas, two drawings)",
        "pile" to "1/3 px of leaf_pile",
        "lake" to "1/3 px of the lake band's own sprites",
        "objectLocal" to "any scene object's own local units -- a bound over all of them, " +
            "never a length of one of them",
        "metres" to "a metre of the depicted world",
    )

    /**
     * **The frame is declared by the name.** Longest matching prefix wins, so `HEAD_CAR_` resolves
     * before `CAR_` and the seated bust is not read as a car. A constant that matches nothing here
     * has to be renamed or given an entry in [overrides] with its reason -- silence is not an
     * option, which is the half of proposal A this needs to work at all.
     */
    private val prefixFrames: List<Pair<String, Set<String>>> = listOf(
        "HEAD_CAR_" to setOf("bustCar"),
        "WINDOW_HEAD_" to setOf("bustWindow"),
        "WINDOW_OCCUPANT_" to setOf("bustWindow"),
        "FIRE_TRUCK_" to setOf("firetruck"),
        "CAR_" to setOf("car"),
        "TAXI_SIGN_" to setOf("car"),
        "POLICE_" to setOf("car"),
        "ESTATE_CABIN_" to setOf("car"),
        "ESTATE_GLAZING_" to setOf("car"),
        "LIVERY_" to setOf("car"),
        // v5.5: renamed from TALLEST_VEHICLE_ when the fire engine was given its own window
        // in `VehicleScalePixelTest` -- the constant is the tallest CAR and never was the tallest
        // vehicle, and the frame it is measured in is the car's.
        "TALLEST_CAR_" to setOf("car"),
        "DOOR_ACCESSORY_" to setOf("car"),
        "PERSON_" to setOf("walk"),
        "PEDESTRIAN_" to setOf("walk"),
        "CHILD_" to setOf("walk"),
        "HOUSE_" to setOf("building"),
        "CHRISTMAS_LIGHT_" to setOf("building"),
        // v5.12: the chimney smoke's lengths, in the house piece's own frame -- the one its chimney's
        // point (`BuildingPiece.smokeX/smokeY`) is declared in (`ChimneySmoke`).
        "SMOKE_" to setOf("building"),
        "CLOUD_" to setOf("cloud"),
        "STAR_" to setOf("star"),
        "CELESTIAL_" to setOf("celestial"),
        "SUN_GLOW_" to setOf("sunGlow"),
        "FIREWORK_" to setOf("firework"),
        "RAINBOW_" to setOf("rainbow"),
        "LIGHTNING_" to setOf("lightning"),
        "SPLASH_" to setOf("splash"),
        "SANTA_SLEIGH_" to setOf("santaSleigh"),
        "DOLPHIN_" to setOf("dolphin"),
        "SAILBOAT_" to setOf("sailboat"),
        "FLOWER_" to setOf("flower"),
        "PILE_" to setOf("pile"),
        "LAKE_" to setOf("lake"),
        "WAVE_" to setOf("wave"),
        // The preview scene's own canvas: three bare dimensions declared inside ThemePreviewScene.
        "WIDTH_" to setOf("preview"),
        "HEIGHT_" to setOf("preview"),
        "HORIZON_" to setOf("preview"),
        // v5.7: the card's own canvas again, for lengths whose quantity would otherwise read as a
        // sprite's. `CARD_LAKE_BOTTOM_UNITS` is where the water's floor sits on the 320x240
        // description, not a length of any lake sprite, and `LAKE_` above would have classified it
        // as one -- an entry in the wrong frame is worse than no entry, because it makes a mixed
        // expression look checked. Longest prefix wins, so this resolves before `CAR_`.
        "CARD_" to setOf("preview"),
    )

    /**
     * The names the prefix table cannot decide, each with the reason it is what it is. **Checked
     * in both directions**: an entry the prefix table already resolves the same way is a stale
     * declaration and fails, so this cannot quietly become an allowlist -- the shape
     * `SpriteReachabilityTest` established after item 57.
     */
    private val overrides: Map<String, Pair<Set<String>, String>> = mapOf(
        "VEHICLE_GROUND_Y_UNITS" to (setOf("car", "firetruck") to
            "the height the shadow is painted at, the same number in each vehicle's own frame: " +
            "drawCar and the fire engine both translate by it"),
        "LAMP_FRONT_W_UNITS" to (setOf("car", "firetruck") to
            "car_lamp_front's own 6x4-unit size. Both vehicles blit that one PNG through " +
            "SpriteScale.SCENE_UNITS, so its size is a length in both local frames -- item 61 " +
            "read this site and recorded it as mixed and correct"),
        "LAMP_REAR_W_UNITS" to (setOf("car", "firetruck") to "car_lamp_rear's own 4x4-unit size; see LAMP_FRONT_W_UNITS"),
        "LAMP_H_UNITS" to (setOf("car", "firetruck") to "the shared lamp height; see LAMP_FRONT_W_UNITS"),
        "MAX_OBJECT_HALF_WIDTH_UNITS" to (setOf("objectLocal") to
            "an upper bound over every scene object measured in its own local units, multiplied " +
            "by that category's own scale at the cull site. It is not a length of any one family, " +
            "so it is comparable with none of them"),
        "OCCUPANT_BOX_UNITS" to (setOf("building") to
            "renamed nothing: this is a house window's own width, the building frame, which is " +
            "why the restaurant passes it instead of its own 10.7-unit pane. Item 61 lists it as " +
            "a name that reads like a bust dimension and is not"),
        "WIDEST_SEATABLE_HEAD_BUST_UNITS" to (setOf("bustCar") to
            "measured on the seated bust canvas. Renamed in v4.26 from WIDEST_SEATABLE_HEAD_UNITS, " +
            "which named neither the quantity's frame nor anything else, and was the second of " +
            "item 61's two defects"),
        "CONTENT_LEFT_UNITS" to (setOf("santaSleigh") to "SantaSleighOriginTest measures the sleigh's own canvas"),
        "CONTENT_TOP_UNITS" to (setOf("santaSleigh") to "SantaSleighOriginTest measures the sleigh's own canvas"),
        "CONTENT_WIDTH_UNITS" to (setOf("santaSleigh") to "SantaSleighOriginTest measures the sleigh's own canvas"),
        "CONTENT_HEIGHT_UNITS" to (setOf("santaSleigh") to "SantaSleighOriginTest measures the sleigh's own canvas"),
        // v5.9B (I-41): the people generator, read since this release. Its lengths are mostly named
        // quantity-first, so the prefix table cannot resolve them.
        "SEATED_HALF_BAND" to (setOf("bustCar") to
            "half the band a seated head may occupy, in the seated bust's own units -- item 61's first " +
            "defect: it was 11.0, justified against a seat pitch in car units, and shipped a release of " +
            "squashed heads. It names no frame and does not end in _UNITS, which is why it has to be " +
            "declared here to be read at all (item 67)"),
        "SEAT_PITCH_CAR_UNITS" to (setOf("car") to
            "the seat pitch of the narrowest cabin, in the car's units, which the seated band is " +
            "derived from; named quantity-first, CAR is its frame"),
        "SEATED_CLEARANCE_CAR_UNITS" to (setOf("car") to
            "what v4.24 left between two occupants' ink at that pitch, in car units; named " +
            "quantity-first, CAR is its frame"),
        "ADULT_BOX_UNITS" to (setOf("walk") to
            "the adult walker's content box, 246.5 of the walk sprite's 252 px over three -- a " +
            "length on the walk canvas, named for the quantity"),
    )

    /** Names that end in `_UNITS` and are not lengths at all. */
    private val notLengths = mapOf(
        "SCENE_UNITS" to "a SpriteScale enum constant -- which convention a blit uses, not a length",
    )

    /**
     * **The whole set of conversions between frames.** An expression that mentions two frames is
     * allowed exactly when it contains one of these; that is the rule item 61 states and the only
     * thing this test is checking. `SPRITE_PIXELS_PER_UNIT` is here because it converts a sprite's
     * own pixels into its own units, which is the step every frame is defined by.
     */
    private val conversions = listOf(
        "CAR_OCCUPANT_SCALE",
        "FIRE_TRUCK_OCCUPANT_SCALE",
        "WINDOW_OCCUPANT_DIVISOR_UNITS",
        "scaleForHeight",
        "SPRITE_PIXELS_PER_UNIT",
        "METRES_TALL",
        "metresPerUnit",
        "unitsTall",
        // A family's base scale *is* scaleForHeight, written once per family: it takes that
        // family's own units to screen pixels, which is why two of them are comparable with
        // each other and the raw unit counts behind them are not.
        "BASE_SCALE",
        "baseScale",
        // The people generator's spelling of CAR_OCCUPANT_SCALE: a bust unit in car units.
        "CAR_UNITS_PER_BUST_UNIT",
    )

    // ---------------------------------------------------------------- the assertions

    /**
     * Every `_UNITS` constant declares which length system it belongs to.
     *
     * This is the precondition for the rule below meaning anything: an unclassified constant would
     * be silently skipped, and the next mixed expression would be invisible again.
     */
    @Test
    fun `every length constant declares its frame`() {
        val undeclared = sortedSetOf<String>()
        for ((file, body) in sources()) {
            for (name in declaredUnitNames(file, body)) {
                if (name in notLengths) continue
                if (framesOf(name) == null) undeclared += "${file.name}: $name"
            }
        }
        assertEquals(
            "these constants name a length and no length system. Rename to <QUANTITY>_<FRAME>_UNITS, " +
                "or add an entry to UnitFrameTest.overrides saying which frame it is and why -- a " +
                "constant nothing classifies is a constant this test cannot protect",
            emptyList<String>(),
            undeclared.toList(),
        )
    }

    /**
     * No entry in [overrides] restates what the name already says.
     *
     * Without this the override map turns into the thing item 57 was hidden behind: a list that
     * only ever grows and that nobody prunes.
     */
    @Test
    fun `no frame override restates what the name already declares`() {
        val stale = overrides.filter { (name, declared) -> prefixFramesOf(name) == declared.first }
        assertEquals(
            "these overrides agree with the prefix table, so the name already declares the frame " +
                "and the entry is dead weight",
            emptyList<String>(),
            stale.keys.sorted(),
        )
        for ((name, declared) in overrides) {
            assertTrue("$name is overridden with no reason", declared.second.length > 40)
            assertTrue("$name declares an unknown frame ${declared.first}", frames.keys.containsAll(declared.first))
        }
    }

    /**
     * **No expression mixes two length systems without a conversion in it.**
     *
     * The unit of analysis is a statement, not a line: a Kotlin expression broken across four
     * lines is one comparison, and reading it a line at a time is how a mixed one hides.
     */
    @Test
    fun `no expression compares two length systems without converting between them`() {
        val mixed = mixedExpressions()
        assertEquals(
            "these expressions mention two length systems with no conversion between them. One " +
                "unit of each is a different physical length (see UnitFrameTest.frames), so the " +
                "comparison is wrong by whatever the ratio happens to be -- 0.5255 for the two " +
                "that shipped. Write the conversion into the expression",
            emptyList<String>(),
            mixed.map { it.first },
        )
    }

    /**
     * **The rule bites.** A green rule is not a rule until the mutation it exists to catch has
     * been shown to fail it (`AI_PROJECT_RULES.md` 12.11), and this one is a source scanner, so
     * the mutation can be applied to a copy of the source rather than to the tree.
     *
     * The mutation is the second of item 61's two real defects, in the shape it actually had:
     * a seated-bust width compared against a seat pitch that is a **local variable** in car units.
     * That is the shape the original constant-keyed scan could not see.
     */
    @Test
    fun `the rule bites on a mixed expression`() {
        val defect = """
            val pitch = SceneObjectRenderer.CAR_PASSENGER_X_UNITS - SceneObjectRenderer.CAR_HEAD_X_UNITS
            val margin = pitch - WIDEST_SEATABLE_HEAD_BUST_UNITS
        """.trimIndent()
        val found = mixedExpressionsIn("Mutation.kt", defect)
        assertEquals(
            "the mutation must be caught, and it must be caught on the line that mixes the frames",
            listOf("Mutation.kt:2"),
            found.map { it.first },
        )
        assertTrue(
            "the failure has to name both frames it found: ${found.first().second}",
            found.first().second.contains("bustCar") && found.first().second.contains("car"),
        )

        val corrected = """
            val pitch = SceneObjectRenderer.CAR_PASSENGER_X_UNITS - SceneObjectRenderer.CAR_HEAD_X_UNITS
            val margin = pitch - WIDEST_SEATABLE_HEAD_BUST_UNITS * SceneObjectRenderer.CAR_OCCUPANT_SCALE
        """.trimIndent()
        assertEquals(
            "the corrected form -- the one that actually ships -- must pass, or the rule is just noise",
            emptyList<Pair<String, String>>(),
            mixedExpressionsIn("Mutation.kt", corrected),
        )
    }

    /**
     * **The generator where the first defect was born is read, and `SEATED_HALF_BAND` with it**
     * (v5.9B, I-41). The file is among the sources; its statement declaring the band is analysed as a
     * bust length computed from car lengths; and it passes because the conversion is in it.
     */
    @Test
    fun `the people generator is read, and SEATED_HALF_BAND is checked`() {
        val generator = sources().firstOrNull { it.first.name == GENERATOR }
            ?: throw AssertionError("$GENERATOR is not among the sources this test reads")
        val line = generator.second.lines().indexOfFirst { it.startsWith("SEATED_HALF_BAND =") } + 1
        assertTrue("SEATED_HALF_BAND is not declared where this test expects it", line > 0)
        assertEquals("the shipped derivation converts, and must pass", emptyList<Pair<String, String>>(), mixedExpressionsIn(GENERATOR, generator.second))

        // The defect's own shape: the band computed from the car's seat pitch with no conversion.
        val broken = generator.second.replace(
            "SEATED_HALF_BAND = (SEAT_PITCH_CAR_UNITS - SEATED_CLEARANCE_CAR_UNITS) / CAR_UNITS_PER_BUST_UNIT / 2",
            "SEATED_HALF_BAND = (SEAT_PITCH_CAR_UNITS - SEATED_CLEARANCE_CAR_UNITS) / 2",
        )
        assertTrue("the mutation did not apply: the derivation has changed shape", broken != generator.second)
        val found = mixedExpressionsIn(GENERATOR, broken)
        assertEquals("the band without its conversion must be caught on its own line", listOf("$GENERATOR:$line"), found.map { it.first })
        assertTrue(found.single().second, found.single().second.contains("bustCar") && found.single().second.contains("car]"))
    }

    /**
     * **A local lives in its function** (v5.9B, item 68 / I-41). A local that holds a conversion in
     * one function used to clear a mixed expression in the next, because the scanner kept one map per
     * file. The same mistake, in Kotlin and in Python, must now be caught.
     */
    @Test
    fun `a local does not outlive its function`() {
        val kotlin = """
            fun first() {
                val margin = HEAD_CAR_X_UNITS * CAR_OCCUPANT_SCALE
            }
            fun second() {
                val gap = CAR_PASSENGER_X_UNITS - WIDEST_SEATABLE_HEAD_BUST_UNITS + margin
            }
        """.trimIndent()
        assertEquals(listOf("Mutation.kt:5"), mixedExpressionsIn("Mutation.kt", kotlin).map { it.first })

        val python = """
            def first():
                margin = HEAD_CAR_X_UNITS * CAR_UNITS_PER_BUST_UNIT

            def second():
                gap = SEAT_PITCH_CAR_UNITS - SEATED_HALF_BAND + margin
        """.trimIndent()
        assertEquals(listOf("mutation.py:5"), mixedExpressionsIn("mutation.py", python).map { it.first })

        // And a local still reaches the rest of its own function, which is what propagation is for.
        val sameFunction = """
            fun only() {
                val pitch = CAR_PASSENGER_X_UNITS - CAR_HEAD_X_UNITS
                if (true) {
                    val margin = pitch - WIDEST_SEATABLE_HEAD_BUST_UNITS
                }
            }
        """.trimIndent()
        assertEquals(listOf("Mutation.kt:4"), mixedExpressionsIn("Mutation.kt", sameFunction).map { it.first })
    }

    /** A constant declared in one frame and computed from another's lengths with no conversion. */
    @Test
    fun `a length computed from another frame's lengths is caught`() {
        val defect = "const val HEAD_CAR_PITCH_UNITS = CAR_PASSENGER_X_UNITS - CAR_HEAD_X_UNITS"
        assertEquals(listOf("Mutation.kt:1"), mixedExpressionsIn("Mutation.kt", defect).map { it.first })
        val corrected = "const val HEAD_CAR_PITCH_UNITS = (CAR_PASSENGER_X_UNITS - CAR_HEAD_X_UNITS) / CAR_OCCUPANT_SCALE"
        assertEquals(emptyList<Pair<String, String>>(), mixedExpressionsIn("Mutation.kt", corrected))
    }

    // ---------------------------------------------------------------- the scanner

    private fun mixedExpressions(): List<Pair<String, String>> =
        sources().flatMap { (file, body) -> mixedExpressionsIn(file.name, body) }

    /** Where a `val` (or a Python assignment) put a frame or a conversion, and how deep that scope is. */
    private class Scope(val level: Int) {
        val frames = HashMap<String, Set<String>>()
        val conversions = HashSet<String>()
    }

    /**
     * Returns `file:line` and a description for every statement in [body] that names two frames
     * and no conversion. Comments and string literals are blanked first: a doc comment saying
     * "a bust unit is CAR_OCCUPANT_SCALE of a car unit" is prose, not arithmetic.
     *
     * **Two things v5.9B added (inventory I-41, items 67 and 68).**
     *
     * *A declared name with a frame of its own is part of its own statement.* `val X_CAR_UNITS =
     * <bust lengths>` mixes two frames exactly as `a - b` does, and the declared name used to be left
     * out of its statement altogether. It still is when its only frame would be the one an earlier
     * local of that name left behind -- the reason it was left out -- but a name the prefix table or
     * [overrides] resolves is compared with what it is computed from. That is the shape the first of
     * item 61's defects had, in the generator: `SEATED_HALF_BAND`, a bust length, computed from a seat
     * pitch in car units.
     *
     * *A local lives in its function.* The propagated frames were one map per file, so a `val` in one
     * function reached every function after it: two locals called `left` 110 lines apart were one
     * entry (`RELEASE_HISTORY.md`, v5.6 known limitations), and a local that held a conversion in one
     * function cleared a mixed expression in the next. Each function body -- a Kotlin `fun` or
     * `init` block, a Python `def` -- is now a scope of its own, closed where its braces or its
     * indentation close; what is declared outside every function stays visible to the whole file.
     */
    private fun mixedExpressionsIn(fileName: String, body: String): List<Pair<String, String>> {
        val python = fileName.endsWith(".py")
        val code = if (python) blankPythonCommentsAndStrings(body) else blankCommentsAndStrings(body)
        val lines = code.lines()
        val depths = if (python) IntArray(0) else braceDepths(lines)
        val scopes = ArrayDeque<Scope>().apply { addLast(Scope(Int.MIN_VALUE)) }
        val found = mutableListOf<Pair<String, String>>()

        for ((lineNumber, statement) in statements(code, python)) {
            if (statement.isBlank()) continue
            val level = if (python) lines[lineNumber - 1].takeWhile { it == ' ' || it == '\t' }.length else depths[lineNumber - 1]
            while (scopes.size > 1 && level <= scopes.last().level) scopes.removeLast()
            fun localFrame(id: String): Set<String>? = scopes.reversed().firstNotNullOfOrNull { it.frames[id] }
            fun localConversion(id: String): Boolean = scopes.any { id in it.conversions }

            val declared = declaredName(statement, python)
            val declaredOwnFrame = declared?.let { framesOf(it) }
            val hasConversion = conversions.any { statement.contains(it) } ||
                Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""").findAll(statement).any { localConversion(it.value) }

            // Each segment is analysed on its own: the branches of a selection are alternatives
            // rather than a comparison, and the entries of a table are unrelated to each other.
            val perSegment = segments(statement).map { segment ->
                val carriers = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""").findAll(segment).map { it.value }
                    // A `val` does not compare with itself: on its own declaration the name still
                    // carries whatever an earlier, unrelated `val` of that name left behind.
                    .filter { it != declared }
                    .mapNotNull { id -> (framesOf(id) ?: localFrame(id))?.let { id to it } }
                    .toList()
                // ...unless the name declares a frame of its own, which is then what the value
                // assigned to it has to be in (v5.9B).
                if (declared != null && declaredOwnFrame != null) listOf(declared to declaredOwnFrame) + carriers else carriers
            }

            val offending = perSegment.firstOrNull { carriers ->
                val distinct = carriers.map { it.second }.distinct()
                distinct.size > 1 && distinct.reduce { a, b -> a intersect b }.isEmpty()
            }
            if (offending != null && !hasConversion) {
                found += "$fileName:$lineNumber" to
                    offending.joinToString(", ") { "${it.first} [${it.second.sorted().joinToString("+")}]" }
            }

            // Propagate: `val x = <expr>` carries the expression's frame, or its conversion. A
            // selection carries the union -- `if (isFireTruck) truck else car` is whichever
            // vehicle is being drawn, and is compatible with either.
            if (declared != null && declaredOwnFrame == null) {
                val scope = scopes.last()
                val all = perSegment.flatten().map { it.second }.distinct()
                when {
                    hasConversion -> scope.conversions += declared
                    offending != null && isSelection(statement) -> scope.frames[declared] = all.flatten().toSet()
                    offending != null -> Unit
                    all.isNotEmpty() -> scope.frames[declared] = all.reduce { a, b -> a intersect b }
                        .ifEmpty { all.flatten().toSet() }
                }
            }
            if (opensFunction(statement, python)) scopes.addLast(Scope(level))
        }
        return found
    }

    /** The name a statement declares: a Kotlin `val`/`var`, or a Python assignment at its start. */
    private fun declaredName(statement: String, python: Boolean): String? =
        if (python) {
            Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?::[^=]*)?=(?!=)""").find(statement)?.groupValues?.get(1)
        } else {
            Regex("""\bva[lr]\s+([A-Za-z_][A-Za-z0-9_]*)""").find(statement)?.groupValues?.get(1)
        }

    private fun opensFunction(statement: String, python: Boolean): Boolean =
        if (python) {
            Regex("""^\s*(?:async\s+)?def\s""").containsMatchIn(statement)
        } else {
            Regex(
                """^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|protected|override|inline|suspend|operator|""" +
                    """infix|tailrec|external|abstract|open|final|actual|expect)\s+)*(?:fun\b|init\s*\{)""",
            ).containsMatchIn(statement)
        }

    /** The brace depth at the start of each line of blanked Kotlin. */
    private fun braceDepths(lines: List<String>): IntArray {
        val depths = IntArray(lines.size)
        var depth = 0
        for ((i, line) in lines.withIndex()) {
            depths[i] = depth
            depth += line.count { it == '{' } - line.count { it == '}' }
        }
        return depths
    }

    private fun isSelection(statement: String): Boolean =
        Regex("""\belse\b|\bwhen\s*[({]|->""").containsMatchIn(statement)

    private val tableConstructor =
        Regex("""\b(listOf|listOfNotNull|arrayOf|mapOf|setOf|sortedSetOf|mutableListOf|floatArrayOf|intArrayOf|arrayListOf)\s*\(""")

    /**
     * Cuts a statement where its parts are alternatives rather than terms of one expression: the
     * branches of an `if`/`when`, and the entries of a collection literal.
     *
     * **The honest limit**, and it is the reason this is written out rather than hidden: inside
     * one branch or one table entry the rule still applies in full, but a mix that straddles a
     * cut -- `if (x) A_UNITS else B_UNITS + C_UNITS` -- is only seen in the branch it lives in.
     */
    private fun segments(statement: String): List<String> {
        val selection = isSelection(statement)
        val table = tableConstructor.containsMatchIn(statement)
        if (!selection && !table) return listOf(statement)
        val separator = buildList {
            if (selection) add("""\belse\b|\bif\s*\(|\bwhen\s*[({]|->""")
            if (table) add(",")
        }.joinToString("|")
        return statement.split(Regex(separator))
    }

    /**
     * Splits code into statements, keeping the line the statement starts on. A line continues into
     * the next while its brackets are unbalanced or it ends on an operator, which is what makes a
     * four-line comparison one unit of analysis instead of four.
     */
    private fun statements(code: String, python: Boolean = false): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        val lines = code.lines()
        var i = 0
        while (i < lines.size) {
            val start = i
            val buffer = StringBuilder(lines[i])
            var depth = bracketDelta(lines[i])
            // Python ends a statement at the line break unless a bracket is open or the line ends in
            // a backslash; a trailing `:` opens a block, not a continuation.
            while (i + 1 < lines.size &&
                (depth > 0 || (if (python) lines[i].trimEnd().endsWith("\\") else lines[i].trimEnd().endsWithOperator() || lines[i + 1].trimStart().startsWithOperator()))
            ) {
                i++
                buffer.append(' ').append(lines[i])
                depth += bracketDelta(lines[i])
                if (depth < 0) depth = 0
            }
            out += (start + 1) to buffer.toString()
            i++
        }
        return out
    }

    private fun bracketDelta(line: String): Int =
        line.count { it == '(' || it == '[' } - line.count { it == ')' || it == ']' }

    private fun String.endsWithOperator(): Boolean =
        isNotEmpty() && (last() in "+-*/%<>=&|,.:?" || endsWith("&&") || endsWith("||"))

    private fun String.startsWithOperator(): Boolean =
        isNotEmpty() && (first() in "+-*/%<>=&|.?" && !startsWith("//"))

    /** Comments and string literals are prose; only code is arithmetic. */
    private fun blankCommentsAndStrings(body: String): String {
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '/' && i + 1 < body.length && body[i + 1] == '/' -> {
                    while (i < body.length && body[i] != '\n') { out.append(' '); i++ }
                }
                c == '/' && i + 1 < body.length && body[i + 1] == '*' -> {
                    while (i < body.length && !(body[i] == '*' && i + 1 < body.length && body[i + 1] == '/')) {
                        out.append(if (body[i] == '\n') '\n' else ' '); i++
                    }
                    repeat(minOf(2, body.length - i)) { out.append(' '); i++ }
                }
                c == '"' -> {
                    val triple = body.startsWith("\"\"\"", i)
                    val close = if (triple) "\"\"\"" else "\""
                    var j = i + close.length
                    while (j < body.length && !body.startsWith(close, j)) {
                        if (!triple && body[j] == '\\') j++
                        if (!triple && body[j] == '\n') break
                        j++
                    }
                    val end = minOf(body.length, j + close.length)
                    for (k in i until end) out.append(if (body[k] == '\n') '\n' else ' ')
                    i = end
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** The same for Python: `#` comments, and strings in single, double or tripled quotes. */
    private fun blankPythonCommentsAndStrings(body: String): String {
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '#' -> while (i < body.length && body[i] != '\n') { out.append(' '); i++ }
                c == '"' || c == '\'' -> {
                    val triple = body.startsWith("$c$c$c", i)
                    val close = if (triple) "$c$c$c" else "$c"
                    var j = i + close.length
                    while (j < body.length && !body.startsWith(close, j)) {
                        if (body[j] == '\\') j++
                        else if (!triple && body[j] == '\n') break
                        j++
                    }
                    val end = minOf(body.length, j + close.length)
                    for (k in i until end) out.append(if (body[k] == '\n') '\n' else ' ')
                    i = end
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    // ---------------------------------------------------------------- plumbing

    private fun framesOf(name: String): Set<String>? {
        if (name in notLengths) return null
        overrides[name]?.let { return it.first }
        return prefixFramesOf(name)
    }

    private fun prefixFramesOf(name: String): Set<String>? {
        if (!isUnitName(name)) return null
        return prefixFrames
            .filter { name.startsWith(it.first) }
            .maxByOrNull { it.first.length }
            ?.second
    }

    // `_UNITS_WIDE` joined the three in v4.28: `WAVE_UNITS_WIDE` is a length in the wave's own
    // frame and the scan could not see it, which is the blind spot item 61 exists to close.
    private fun isUnitName(name: String): Boolean =
        name.endsWith("_UNITS") || name.endsWith("_UNITS_TALL") ||
            name.endsWith("_UNITS_LONG") || name.endsWith("_UNITS_WIDE")

    private fun declaredUnitNames(file: File, body: String): List<String> =
        if (file.extension == "py") {
            Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?::[^=\n]*)?=(?!=)""").findAll(blankPythonCommentsAndStrings(body))
        } else {
            Regex("""\bva[lr]\s+([A-Za-z_][A-Za-z0-9_]*)""").findAll(blankCommentsAndStrings(body))
        }
            .map { it.groupValues[1] }
            .filter { isUnitName(it) }
            .toList()

    /**
     * Every Kotlin source of the app and its tests, and **every Python source under `tools/`**
     * (v5.9B, I-41): the generators draw the sprites whose units this test is about, and the first
     * of item 61's two defects was in one of them. Until v5.9B this read the Kotlin alone.
     */
    private fun sources(): List<Pair<File, String>> =
        (
            listOf("app/src/main/kotlin", "app/src/test/kotlin", "app/src/androidTest/kotlin")
                .map { File(repoRoot(), it) }
                .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" } } +
                File(repoRoot(), "tools").walkTopDown().filter { f -> f.extension == "py" && "__pycache__" !in f.path }
            )
            .sortedBy { it.path }
            .map { it to it.readText() }

    private companion object {
        const val GENERATOR = "build_people_concepts.py"
    }

    private fun repoRoot(): File {
        // Walked to null rather than tested through `parentFile`, a platform type: the older shape
        // compiled with "Java type mismatch: inferred type is 'File?'" (v5.9C, inventory I-10).
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }
}
