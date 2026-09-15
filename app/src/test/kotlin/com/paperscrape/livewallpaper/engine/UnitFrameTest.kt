package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Seven length systems in this project are all called `_UNITS`, and this is what stops two of
 * them being compared.** `BACKLOG_v4_25.md` item 61 is the search that found the class; this is
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
 * `theRuleBitesOnAMixedExpression` renders that claim falsifiable.
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
        "car" to "1/3 px of car_body; 56 units is CAR_METRES_TALL",
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
        "LIVERY_" to setOf("car"),
        "TALLEST_VEHICLE_" to setOf("car"),
        "DOOR_ACCESSORY_" to setOf("car"),
        "PERSON_" to setOf("walk"),
        "PEDESTRIAN_" to setOf("walk"),
        "CHILD_" to setOf("walk"),
        "HOUSE_" to setOf("building"),
        "CHRISTMAS_LIGHT_" to setOf("building"),
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
        "WHEEL_ARCH_AIR_UNITS" to (setOf("car", "firetruck") to
            "the arch gap, authored as the same number in both vehicles; the fire engine divides " +
            "it by its own metre-per-unit where the rendered gap has to match"),
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
            for (name in declaredUnitNames(body)) {
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

    // ---------------------------------------------------------------- the scanner

    private fun mixedExpressions(): List<Pair<String, String>> =
        sources().flatMap { (file, body) -> mixedExpressionsIn(file.name, body) }

    /**
     * Returns `file:line` and a description for every statement in [body] that names two frames
     * and no conversion. Comments and string literals are blanked first: a doc comment saying
     * "a bust unit is CAR_OCCUPANT_SCALE of a car unit" is prose, not arithmetic.
     */
    private fun mixedExpressionsIn(fileName: String, body: String): List<Pair<String, String>> {
        val code = blankCommentsAndStrings(body)
        val localFrames = HashMap<String, Set<String>>()
        val localConversions = HashSet<String>()
        val found = mutableListOf<Pair<String, String>>()

        for ((lineNumber, statement) in statements(code)) {
            val declared = Regex("""\bva[lr]\s+([A-Za-z_][A-Za-z0-9_]*)""").find(statement)?.groupValues?.get(1)
            val hasConversion = conversions.any { statement.contains(it) } ||
                localConversions.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(statement) }

            // Each segment is analysed on its own: the branches of a selection are alternatives
            // rather than a comparison, and the entries of a table are unrelated to each other.
            val perSegment = segments(statement).map { segment ->
                Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""").findAll(segment).map { it.value }
                    // A `val` does not compare with itself: on its own declaration the name still
                    // carries whatever an earlier, unrelated `val` of that name left behind.
                    .filter { it != declared }
                    .mapNotNull { id -> (framesOf(id) ?: localFrames[id])?.let { id to it } }
                    .toList()
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
            if (declared != null) {
                val all = perSegment.flatten().map { it.second }.distinct()
                when {
                    hasConversion -> localConversions += declared
                    offending != null && isSelection(statement) -> localFrames[declared] = all.flatten().toSet()
                    offending != null -> Unit
                    all.isNotEmpty() -> localFrames[declared] = all.reduce { a, b -> a intersect b }
                        .ifEmpty { all.flatten().toSet() }
                }
            }
        }
        return found
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
    private fun statements(code: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        val lines = code.lines()
        var i = 0
        while (i < lines.size) {
            val start = i
            val buffer = StringBuilder(lines[i])
            var depth = bracketDelta(lines[i])
            while (i + 1 < lines.size &&
                (depth > 0 || lines[i].trimEnd().endsWithOperator() || lines[i + 1].trimStart().startsWithOperator())
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

    private fun declaredUnitNames(body: String): List<String> =
        Regex("""\bva[lr]\s+([A-Za-z_][A-Za-z0-9_]*)""").findAll(blankCommentsAndStrings(body))
            .map { it.groupValues[1] }
            .filter { isUnitName(it) }
            .toList()

    private fun sources(): List<Pair<File, String>> =
        listOf("app/src/main/kotlin", "app/src/test/kotlin", "app/src/androidTest/kotlin")
            .map { File(repoRoot(), it) }
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" } }
            .sortedBy { it.path }
            .map { it to it.readText() }

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }
}
