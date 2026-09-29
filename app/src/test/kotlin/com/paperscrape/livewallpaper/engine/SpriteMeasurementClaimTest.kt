package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-07: the numbers written about sprites are the numbers the sprites have.
 *
 * The audit found several load-bearing comments describing artwork that no longer ships -- a bird
 * "90x42" that is 90x24, a dolphin "360x225" that is 345x174, a canopy "270x252" that is 246x222,
 * and a window-head canvas "60 units wide" that is 53. The live constants were mostly right; the
 * comments were what a future edit would be derived from, which is the whole risk.
 *
 * This reads the PNGs so the corrected numbers cannot rot again, and separates the two kinds of
 * claim: a **measurement** must match the artwork, and a **tuned value** must not pretend to be one.
 */
class SpriteMeasurementClaimTest {

    @Test
    fun `the sprites are the sizes the comments now say`() {
        val expected = mapOf(
            // v4.26: 90x24 until the bird was redrawn as concept A "Dove" and reduced to the
            // size it is actually read at -- ~48 px on screen, about 1.3 person heights. The flap
            // axis moved with it, from canvas row 18 to row 15, and BIRD_SPRITE_ORIGIN_X/Y_PX
            // moved to (-25, -15) in the same change: the origin *is* the axis the flap mirrors
            // about, so a canvas that changes height without it moves the bird.
            "bird_body" to (51 to 21),
            // v4.31: 345x174 until the leading padding the v4.26 redraw left inside the canvas
            // was cropped, with DOLPHIN_ORIGIN_X/Y_UNITS compensated by the (1, 2) units it
            // removed. No drawn pixel moved.
            "dolphin_body" to (342 to 171),
            // v4.21 redrew the crown: 303x198 px = 101x66 u, the "Broad Oak" cushion.
            "tree_canopy" to (303 to 198),
            // v4.25: 159x171 until the window family was redrawn on a canvas trimmed to what it
            // covers, then 141x171, and 147x171 once the proportion pass gave the head back the
            // width it had -- 49 units wide instead of 53, three of them off the left, compensated
            // at WINDOW_HEAD_ANCHOR_X_UNITS in the same change.
            "person_man_summer_head_window" to (147 to 171),
        )
        for ((name, size) in expected) {
            val image = ImageIO.read(File(drawableDir(), "$name.png"))
            assertEquals("$name width", size.first, image.width)
            assertEquals("$name height", size.second, image.height)
        }
    }

    @Test
    fun `the window occupant divisor is a tuned value, not the canvas width`() {
        // The distinction the comment used to get wrong. 60 is deliberate and the canvas is the
        // canvas; asserting both is what stops somebody "fixing" one into the other. The canvas
        // moved to 49 units in v4.25 -- 53 before it was trimmed, 47 before the head was given
        // back its width -- and the divisor did not, which is the point: they were never the same
        // number, and the gap between them is still there.
        val canvasUnits = ImageIO.read(File(drawableDir(), "person_man_summer_head_window.png")).width /
            SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertEquals("the canvas really is 49 units", 49f, canvasUnits, 0.001f)
        assertEquals(
            "the divisor is 60 and is not the canvas width",
            60f,
            SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS,
            0.001f,
        )
        assertTrue(
            "so an occupant is drawn narrower than the nominal 85% of its pane",
            canvasUnits / SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS < 1f,
        )
    }

    /**
     * **Every size written in a comment is the size of the artwork it is about, or says why not.**
     *
     * *Until v5.9B* the rule was one regex -- `` `name` `` ... `is NxM px` -- and it said so: a claim
     * about a sprite had to be *written in the shape this reads*. v4.29 had tried making `px`
     * optional and put it back, because "`house_shared_window` is 22x21" is a true statement in
     * local units about a 66x63 px sprite and the widened pattern read it as pixels.
     * `BACKLOG_v4_29.md` item 90 recorded what that left uncovered, and item 63 had already met it:
     * **a size written in units, or with no unit, or with the unit on the next line, was invisible.**
     * Thirty sizes in `src/main` are written in units, and not one of them was ever read (inventory
     * row I-40, items 63 and 90).
     *
     * *Since v5.9B* every `NxM` in a comment block of `src/main` is found -- the block read as one
     * line, so a unit after a line break is still its unit, and with `"quoted"` spans removed, because
     * a quotation is how this codebase marks a superseded sentence -- and each one must be one of:
     *
     * - **attributed to a shipped sprite** and equal to it: the nearest `` `name` `` before it in the
     *   block -- or, since v5.9C, `name.png` written without backticks -- or the pixel size it
     *   restates (`A px = B u`), or, when it names none, the family its
     *   size belongs to in [sizeFamilies]. Pixels must equal the canvas, units the canvas over
     *   [SpriteBlitter.SPRITE_PIXELS_PER_UNIT]; either may equal the drawing's ink box instead, which
     *   is what "of content" claims measure. **A size with no unit is right if it is right in either
     *   frame** -- which is what keeps v4.29's `22x21` true;
     * - **declared** in [declaredClaims], with the reason it is not a claim about a shipped canvas: a
     *   size the sentence itself says is history, or the size of something that is not a sprite.
     *   Checked in both directions: a declaration that matches no claim any more fails.
     *
     * A size in pixels or units that is neither fails, and the message says which. A size with no
     * unit and no sprite before it is not read: "a 2x3 affine", "a 1x1 texture". Until v5.9C a
     * sprite written as a bare file name did not count as "a sprite before it", so "sun_glow.png is
     * 396x396" went unread (inventory row I-49); it is read now.
     */
    @Test
    fun `every size written about artwork is that artwork's size, or is declared`() {
        val wrong = mutableListOf<String>()
        val unclassified = mutableListOf<String>()
        val all = claims()
        // The whole reading, into the test's own report, so what the guardian saw can be looked at.
        println("${all.size} sizes in the comments of src/main: " + all.groupingBy { verdictFor(it).javaClass.simpleName }.eachCount())
        for (c in all) println("  ${c.where} \"${c.text}\" -> ${verdictFor(c)}")
        for (claim in all) {
            when (val verdict = verdictFor(claim)) {
                is Verdict.Right, Verdict.Declared, Verdict.NotRead -> Unit
                is Verdict.Wrong -> wrong += "${claim.where}: \"${claim.text}\" -- ${verdict.why}"
                Verdict.Unclassified -> unclassified += "${claim.where}: \"${claim.text}\" (…${claim.before.takeLast(50)})"
            }
        }
        assertEquals("these sizes are written about artwork and are not its size", emptyList<String>(), wrong)
        assertEquals(
            "these sizes name no shipped sprite and match no family: write the sprite's name in " +
                "backticks before the size, or add the size to sizeFamilies, or declare it in " +
                "declaredClaims with the reason it is not a canvas",
            emptyList<String>(),
            unclassified,
        )
    }

    /**
     * **The guardian sees what the simplest search sees.** The inventory counted thirty sizes in
     * units with `grep -Eo '[0-9]{2,4}x[0-9]{2,4}(-unit| units?| u)' app/src/main`; every one of them
     * has to be a claim this class reads and classifies, or it is proof of a hole.
     */
    @Test
    fun `every size in units a plain search finds is read and classified`() {
        val plain = Regex("""([0-9]{2,4})x([0-9]{2,4})(-unit| units?| u)""")
        val seen = claims().filter { verdictFor(it) !is Verdict.Unclassified && verdictFor(it) != Verdict.NotRead }
        var found = 0
        for (file in kotlinSources()) {
            for (m in plain.findAll(file.readText())) {
                found++
                assertTrue(
                    "${file.name}: \"${m.value}\" is a size in units that this guardian does not read",
                    seen.any { it.file == file.name && it.w == m.groupValues[1].toInt() && it.h == m.groupValues[2].toInt() && it.unit == Measure.UNITS },
                )
            }
        }
        assertTrue("the plain search found nothing, so this proves nothing", found >= 30)
    }

    /**
     * **The rule bites**, on the three shapes the old regex could not read, each planted into a copy
     * of a real comment and each caught: a size in units, a size with no unit, and a size whose unit
     * is on the next line.
     */
    @Test
    fun `a wrong size is caught in units, with no unit, and across a line break`() {
        val palm = "PalmSpriteLayout.kt"
        val cases = mapOf(
            "in units" to " * `palmtree_trunk` is 21x57 u, and the point that stands",
            "no unit" to " * `palmtree_trunk` is 63x171, and the point that stands",
            "line break" to " * `palmtree_trunk` is 63x171\n * px, and the point that stands",
            "a family, unnamed" to " * the crown is a 56x46-unit canvas",
        )
        for ((shape, comment) in cases) {
            val verdicts = claimsIn(palm, "/**\n$comment\n */").map { verdictFor(it) }
            assertTrue("a wrong size $shape was not caught: $verdicts", verdicts.any { it is Verdict.Wrong || it == Verdict.Unclassified })
        }
        val right = claimsIn(palm, "/**\n * `palmtree_trunk` is 63x174\n * px = 21x58 u\n */").map { verdictFor(it) }
        assertTrue("the true sentence must pass: $right", right.all { it is Verdict.Right })
    }

    /**
     * **A size after a bare file name is read** (v5.9C, inventory row I-49): the renderer's own
     * sentence about the sunburst, true and then made wrong.
     */
    @Test
    fun `a size written after a sprite's bare file name is read and checked`() {
        val file = "PaperRenderer.kt"
        val right = claimsIn(file, "// sun_glow.png is 396x396 and its ring sits 154..166px from its own centre").map { verdictFor(it) }
        assertEquals("the true sentence must be read and pass", listOf<Verdict>(Verdict.Right("sun_glow")), right)
        val wrong = claimsIn(file, "// sun_glow.png is 396x390 and its ring sits 154..166px from its own centre").map { verdictFor(it) }
        assertTrue("a wrong size after a bare file name was not caught: $wrong", wrong.single() is Verdict.Wrong)
        val live = claims().filter { it.file == file && it.text == "396x396" }
        assertTrue("the renderer's own sentence is not read: ${live.map { verdictFor(it) }}", live.isNotEmpty() && live.all { verdictFor(it) == Verdict.Right("sun_glow") })
    }

    /** No declaration outlives the sentence it declares. */
    @Test
    fun `every declared size is still written where it is declared`() {
        val all = claims()
        for (d in declaredClaims) {
            val matched = all.count { d.matches(it) }
            val message = "${d.file}: \"${d.text}\" is declared (${d.why}) and matches $matched claims"
            if (d.times == ANY) assertTrue(message, matched >= 1) else assertEquals(message, d.times, matched)
            assertTrue("${d.text}: the reason is too short to be a reason", d.why.length > 30)
        }
        for ((size, glob) in sizeFamilies) assertTrue("$size names the family $glob, which ships nothing", spritesMatching(glob).isNotEmpty())
    }

    // ---------------------------------------------------------------- claims, and their verdicts

    private enum class Measure { PX, UNITS, NONE }

    private class Claim(val file: String, val line: Int, val text: String, val w: Int, val h: Int, val unit: Measure, val sprite: String?, val before: String) {
        val where get() = "$file:$line"
    }

    private sealed interface Verdict {
        data class Right(val sprite: String) : Verdict
        data class Wrong(val why: String) : Verdict
        data object Declared : Verdict
        data object Unclassified : Verdict
        data object NotRead : Verdict
    }

    /**
     * A size that is not a claim about a shipped canvas, where it is written, and why. [times] is how
     * often the file writes it, or [ANY] for something that is not a sprite at all.
     */
    private class Declared(val file: String, val text: String, val why: String, val times: Int = 1) {
        fun matches(c: Claim) = c.file == file && c.text == text
    }

    private companion object {
        const val ANY = -1
    }

    private val declaredClaims = listOf(
        Declared("PalmSpriteLayout.kt", "40x40-unit", "history: the crown's canvas before v5.1 redrew it"),
        Declared("TreeSpriteLayout.kt", "10x44 u", "history: the v4.20 trunk, a drawRect and never a sprite"),
        Declared("PaperRenderer.kt", "768x510 px", "history: the canvas the old cloud origin centred, which never shipped"),
        Declared("PaperRenderer.kt", "624x168", "history: the sleigh canvas an orphaned KDoc used to quote, cited twice as such", times = 2),
        Declared("SceneObjectRenderer.kt", "47x44 units", "history: the seated bust canvas before v4.25 trimmed it"),
        Declared("SceneObjectRenderer.kt", "159x171 px", "history: the window bust canvas before REN-07"),
        Declared("SceneObjectRenderer.kt", "297x174", "history: firetruck_body's size from v4.19 until its redraw"),
        Declared("CarShell.kt", "282x18 px", "history: v4.18's two full-car-width overlays, removed since"),
        Declared("ThemePreviewScene.kt", "320x240", "not a sprite: the card's own 320x240 description canvas", times = ANY),
    )

    /**
     * The size a comment writes without naming the sprite, and the family it belongs to. Keyed by the
     * written numbers, so a redraw that changes a family's canvas fails every sentence still quoting
     * the old one.
     */
    private val sizeFamilies = mapOf(
        "56x48 units" to "palmtree_fronds*", "168x144 px" to "palmtree_fronds*",
        "39x84 units" to "person_*_walk*", "117x252 px" to "person_*_walk*",
        "49x57 units" to "person_*_head_window*",
        "38x42 units" to "person_*_head_car*",
        "114x57 units" to "dolphin_body", "342x171 px" to "dolphin_body",
        "21x58 units" to "palmtree_trunk", "63x174 px" to "palmtree_trunk",
        "32x62 units" to "tree_trunk",
        "20x6 units" to "police_lightbar",
        "36x7 units" to "*_pile",
        "51x21 px" to "bird_body",
    )

    private fun verdictFor(c: Claim): Verdict {
        if (declaredClaims.any { it.matches(c) }) return Verdict.Declared
        val family = sizeFamilies["${c.w}x${c.h} ${if (c.unit == Measure.PX) "px" else "units"}"]
        val sprites = when {
            c.sprite != null -> listOf(c.sprite)
            family != null && c.unit != Measure.NONE -> spritesMatching(family).map { it.nameWithoutExtension }
            c.unit == Measure.NONE -> return Verdict.NotRead
            else -> return Verdict.Unclassified
        }
        for (name in sprites) {
            val image = ImageIO.read(File(drawableDir(), "$name.png"))
            val ink = inkBox(image)
            val sizes = listOf(image.width to image.height, (ink[2] - ink[0]) to (ink[3] - ink[1]))
            val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            fun inPx(w: Int, h: Int) = (c.w to c.h) == (w to h)
            fun inUnits(w: Int, h: Int) = (c.w * unit).toInt() == w && (c.h * unit).toInt() == h
            val ok = sizes.any { (w, h) ->
                when (c.unit) {
                    Measure.PX -> inPx(w, h)
                    Measure.UNITS -> inUnits(w, h)
                    Measure.NONE -> inPx(w, h) || inUnits(w, h)
                }
            }
            if (!ok) return Verdict.Wrong("$name is ${image.width}x${image.height} px")
        }
        return Verdict.Right(sprites.first())
    }

    private fun claims(): List<Claim> = kotlinSources().flatMap { claimsIn(it.name, it.readText()) }

    private val sizePattern = Regex("""(?<![\w.])(\d{1,4})x(\d{1,4})(?![\w])(\s*px\b|-unit\b| local units?\b| units?\b| u\b)?""")
    private val backticked = Regex("""`([a-z0-9_]+)(?:\.png)?`""")

    /**
     * A sprite named in a comment: in backticks, or as a bare file name -- `sun_glow.png is 396x396`
     * (v5.9C, inventory row I-49). A bare word without `.png` is not a name: "the ring" is not
     * `ring`.
     */
    private val spriteNamed = Regex("""`([a-z0-9_]+)(?:\.png)?`|(?<![\w.`])([a-z0-9_]+)\.png\b""")

    /**
     * The sizes in one file's comment blocks. Each block is read as one line -- the `*` and `//` at
     * the start of each line dropped, the line breaks made spaces -- and without its quotations.
     */
    private fun claimsIn(fileName: String, source: String): List<Claim> {
        val out = mutableListOf<Claim>()
        for ((line, block) in numberedCommentBlocks(source)) {
            val text = withoutQuotations(block.replace(Regex("""\s*\n\s*(?:\*(?!/)|//)?\s*"""), " "))
            var previous: Claim? = null
            var previousEnd = -1
            for (m in sizePattern.findAll(text)) {
                val unit = when (m.groupValues[3].trim()) {
                    "px" -> Measure.PX
                    "" -> Measure.NONE
                    else -> Measure.UNITS
                }
                val before = text.substring(maxOf(0, m.range.first - 90), m.range.first)
                val named = spriteNamed.findAll(before).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                    .lastOrNull { File(drawableDir(), "$it.png").isFile }
                // `A px = B u`, `A px -- B local units`: the second restates the first.
                val restates = previous?.takeIf {
                    it.unit == Measure.PX && unit == Measure.UNITS && m.range.first - previousEnd <= 8 &&
                        text.substring(previousEnd, m.range.first).all { ch -> ch in " =-,(:" }
                }
                val claim = Claim(fileName, line, m.value.trim(), m.groupValues[1].toInt(), m.groupValues[2].toInt(), unit, restates?.sprite ?: named, before)
                out += claim
                previous = claim
                previousEnd = m.range.last + 1
            }
        }
        return out
    }

    /** [commentBlocks], each with the line it starts on. */
    private fun numberedCommentBlocks(source: String): List<Pair<Int, String>> {
        val lineOf = { index: Int -> source.substring(0, index).count { it == '\n' } + 1 }
        val out = mutableListOf<Pair<Int, String>>()
        var i = source.indexOf("/*")
        while (i >= 0) {
            val end = source.indexOf("*/", i + 2)
            if (end < 0) break
            out += lineOf(i) to source.substring(i, end + 2)
            i = source.indexOf("/*", end + 2)
        }
        val run = StringBuilder()
        var start = 0
        for ((index, raw) in source.lines().withIndex()) {
            val trimmed = raw.trim()
            if (trimmed.startsWith("//")) {
                if (run.isEmpty()) start = index + 1
                run.append(trimmed).append('\n')
            } else if (run.isNotEmpty()) {
                out += start to run.toString()
                run.clear()
            }
        }
        if (run.isNotEmpty()) out += start to run.toString()
        return out
    }

    private fun spritesMatching(glob: String): List<File> {
        val pattern = Regex(glob.split("*").joinToString(".*") { Regex.escape(it) })
        return drawableDir().listFiles { f -> f.extension == "png" && pattern.matches(f.nameWithoutExtension) }!!.sortedBy { it.name }
    }

    /**
     * A comment that says a sprite's drawing reaches its canvas edge is checked against the alpha.
     *
     * **The residue item 90 left, in a shape it did not anticipate.** That item recorded one hole
     * -- a pixel claim that omits its unit -- and closed the rest. The claim that got through is
     * neither: `DOLPHIN_ORIGIN_X_UNITS` said *"The sprite is 345x174 px -- 115x58 local units --
     * **filled edge to edge**, so its content centre sits at (57.5, 29)"*. The canvas half is true
     * and the existing guard above checks it and passes. The load-bearing half is the phrase
     * "filled edge to edge", which is what makes the centre `(57.5, 29)` follow, and it stopped
     * being true in v4.26 when the dolphin was redrawn inside the same canvas with `4,6` px of
     * margin. The real centre is `(58.167, 30.0)` units, so the derivation the constant is
     * justified by gives the wrong answer by `(0.87, 1.0)` units.
     *
     * A canvas size is not the same claim as a content box, and a guard that only reads canvas
     * sizes will keep passing a sentence about the ink. So this reads the **alpha channel**, not
     * the registry: the registry is a declaration and the PNG is the artwork, and item 90's rule
     * -- re-measure, do not re-type -- applies to a checker as much as to a comment.
     *
     * Scoped by comment block rather than by character distance, because the two halves of the
     * dolphin's sentence are four lines apart and a windowed regex either misses that or drags in
     * the next constant's prose. A block that makes the claim must name exactly one sprite that
     * ships, which is what lets the assertion say which artwork it is about.
     */
    @Test
    fun `a sprite said to fill its canvas does fill it`() {
        val claim = Regex("""filled edge to edge|content filling it|fills its canvas""")
        val backticked = Regex("""`([a-z0-9_]+)(?:\.png)?`""")
        var checked = 0
        for (file in kotlinSources()) {
            for (block in commentBlocks(file.readText())) {
                // **A quoted claim is history, not a claim.** `AI_PROJECT_RULES.md` §3 says to
                // annotate a wrong number rather than delete it, so the corrected dolphin comment
                // has to be able to say *This said "filled edge to edge"* without this test
                // reading that as the assertion being made again. Double quotes are how this
                // codebase already marks a superseded sentence, so they are what is stripped.
                if (!claim.containsMatchIn(withoutQuotations(block))) continue
                val named = backticked.findAll(block)
                    .map { it.groupValues[1] }
                    .filter { File(drawableDir(), "$it.png").isFile }
                    .distinct()
                    .toList()
                assertEquals(
                    "${file.name}: a block claiming a sprite fills its canvas must name exactly " +
                        "one shipped sprite, and this one names $named",
                    1,
                    named.size,
                )
                val image = ImageIO.read(File(drawableDir(), "${named[0]}.png"))
                assertEquals(
                    "${file.name} says ${named[0]} fills its canvas, and its ink box is " +
                        "${inkBox(image).toList()} of ${image.width}x${image.height}",
                    listOf(0, 0, image.width, image.height),
                    inkBox(image).toList(),
                )
                checked++
            }
        }
        assertTrue("the pattern matched nothing, so this test proves nothing", checked > 0)
    }

    /** The block with every `"..."` span removed, so a quoted claim is not read as a live one. */
    private fun withoutQuotations(block: String): String {
        val out = StringBuilder()
        var quoted = false
        for (ch in block) {
            if (ch == '"') { quoted = !quoted; continue }
            if (!quoted) out.append(ch)
        }
        return out.toString()
    }

    /** The smallest box containing every pixel with non-zero alpha, as `[left, top, right, bottom]`. */
    private fun inkBox(image: java.awt.image.BufferedImage): IntArray {
        var left = image.width
        var top = image.height
        var right = 0
        var bottom = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 == 0) continue
                if (x < left) left = x
                if (y < top) top = y
                if (x >= right) right = x + 1
                if (y >= bottom) bottom = y + 1
            }
        }
        return if (right == 0) intArrayOf(0, 0, 0, 0) else intArrayOf(left, top, right, bottom)
    }

    /**
     * Every `/** ... */` and `/* ... */` block in a Kotlin source, plus each run of adjacent `//`
     * lines, as one string each.
     *
     * A run of `//` lines counts as one block because that is how the engine writes an argument
     * inside a function body, and splitting it per line would separate a sprite's name from the
     * claim made about it two lines down.
     */
    private fun commentBlocks(source: String): List<String> {
        val blocks = mutableListOf<String>()
        // Scanned by index rather than by regex: `/\*(?:[^*]|\*(?!/))*\*/` overflows the stack on
        // `PaperRenderer.kt`, which is the file this test most needs to read.
        var i = source.indexOf("/*")
        while (i >= 0) {
            val end = source.indexOf("*/", i + 2)
            if (end < 0) break
            blocks += source.substring(i, end + 2)
            i = source.indexOf("/*", end + 2)
        }
        val run = StringBuilder()
        for (line in source.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("//")) {
                run.append(trimmed).append('\n')
            } else if (run.isNotEmpty()) {
                blocks += run.toString()
                run.clear()
            }
        }
        if (run.isNotEmpty()) blocks += run.toString()
        return blocks
    }

    /**
     * Every Kotlin source under `src/main`, because a hand-written file list is the failure this
     * test exists to catch, happening to this test.
     *
     * Until v4.29 the scan above named two files -- `SceneObjectRenderer.kt` and
     * `PaperRenderer.kt` -- and `BACKLOG_v4_28.md` item 82 then found three stale load-bearing
     * numbers in `GlTextureAtlas.kt` and `GlTextureCache.kt`, which were simply not being read.
     * That is the same shape as the stale golden-class list in `CLAUDE.md` §5, which cost v4.28 a
     * missed `SkyWaterGoldenTest`: **ask the tree, do not keep the list.** Walking the directory
     * costs a few milliseconds and cannot go out of date.
     */
    private fun kotlinSources(): List<File> =
        walkUp("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun drawableDir(): File = walkUp("src/main/res/drawable-nodpi")

    private fun sourceDir(): File = walkUp("src/main/kotlin/com/paperscrape/livewallpaper/engine")

    private fun walkUp(suffix: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + suffix)
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate " + suffix)
    }
}
