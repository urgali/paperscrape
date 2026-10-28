package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers written about sprites are the numbers the sprites have.
 *
 * A comment describing artwork is what a future edit is derived from, so a size that describes a
 * drawing which does not ship is a defect even where the live constant is right. This reads the
 * PNGs so the numbers cannot rot, and separates the two kinds of claim: a **measurement** must
 * match the artwork, and a **tuned value** must not pretend to be one.
 */
class SpriteMeasurementClaimTest {

    @Test
    fun `the sprites are the sizes the comments now say`() {
        val expected = mapOf(
            // The flap mirrors the bird's canvas about its origin, BIRD_SPRITE_ORIGIN_X/Y_PX at
            // (-25, -15): the origin *is* the flap's axis, canvas row 15, so a canvas that changes
            // height without it moves the bird.
            "bird_body" to (51 to 21),
            // DOLPHIN_ORIGIN_X/Y_UNITS centre the ink measured in this canvas, so a crop or a
            // padding of the canvas moves the dolphin unless they move with it.
            "dolphin_body" to (342 to 171),
            // The crown: 303x198 px = 101x66 u.
            "tree_canopy" to (303 to 198),
            // The window family's canvas, 49x57 units. WINDOW_HEAD_ANCHOR_X_UNITS is measured in
            // it, so a crop of the canvas moves the anchor or the sprite.
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
        // 60 is a tuned value and the canvas is the canvas; asserting both is what stops somebody
        // "fixing" one into the other. A crop changes the canvas without changing the picture, and
        // the divisor is not derived from it.
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
     * Every `NxM` in a comment block of `src/main` is found -- the block read as one line, so a unit
     * after a line break is still its unit, and with `"quoted"` spans removed, because a quotation is
     * how this codebase marks a superseded sentence -- in pixels, in units or with no unit, and each
     * one must be one of:
     *
     * - **attributed to a shipped sprite** and equal to it: the nearest `` `name` `` before it in the
     *   block, or `name.png` written without backticks, or the pixel size it restates
     *   (`A px = B u`), or, when it names none, the family its size belongs to in [sizeFamilies].
     *   Pixels must equal the canvas, units the canvas over [SpriteBlitter.SPRITE_PIXELS_PER_UNIT];
     *   either may equal the drawing's ink box instead, which is what "of content" claims measure.
     *   **A size with no unit is right if it is right in either frame**, so a size in local units
     *   written without its unit is not read as pixels;
     * - **declared** in [declaredClaims], with the reason it is not a claim about a shipped canvas: a
     *   size the sentence weighs and nothing ships, or the size of something that is not a sprite.
     *   Checked in both directions: a declaration that matches no claim fails.
     *
     * A size in pixels or units that is neither fails, and the message says which. A size with no
     * unit and no sprite before it is not read: "a 2x3 affine", "a 1x1 texture".
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
     * **The guardian sees what the simplest search sees.** Every size in units that
     * `grep -Eo '[0-9]{2,4}x[0-9]{2,4}(-unit| units?| u)' app/src/main` finds has to be a claim
     * this class reads and classifies, or it is proof of a hole.
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
        assertTrue("the plain search found nothing, so this proves nothing", found >= 20)
    }

    /**
     * **The rule bites**: a wrong size planted into a copy of a real comment is caught in units, with
     * no unit, with its unit on the next line, and as a family's size written without its sprite;
     * the true sentence passes.
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
     * **A size after a bare file name is read**: the renderer's own sentence about the sunburst,
     * true and then made wrong, and the live sentence in `PaperRenderer.kt` read and right.
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
        Declared("CarShell.kt", "282x18 px", "not a shipped canvas: the two full-car-width overlays the shared lamps avoid"),
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
     * A sprite named in a comment: in backticks, or as a bare file name -- `sun_glow.png is 396x396`.
     * A bare word without `.png` is not a name: "the ring" is not `ring`.
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
     * **A sentence about the ink is not a canvas size.** An origin derived from "the sprite is
     * N x M px, filled edge to edge, so its content centre sits at ..." is right only while the ink
     * reaches every edge; a redraw inside the same canvas with a margin leaves the canvas half true,
     * so a guard that only reads canvas sizes keeps passing it while the derived centre is wrong.
     * So this reads the **alpha channel**, not the registry: the registry is a declaration and the
     * PNG is the artwork, and the rule -- re-measure, do not re-type -- applies to a checker as much
     * as to a comment.
     *
     * Scoped by comment block rather than by character distance, because the two halves of such a
     * sentence can be lines apart and a windowed regex either misses that or drags in the next
     * constant's prose. A block that makes the claim must name exactly one sprite that ships, which
     * is what lets the assertion say which artwork it is about.
     */
    @Test
    fun `a sprite said to fill its canvas does fill it`() {
        val claim = Regex("""filled edge to edge|content filling it|fills its canvas""")
        val backticked = Regex("""`([a-z0-9_]+)(?:\.png)?`""")
        var checked = 0
        for (file in kotlinSources()) {
            for (block in commentBlocks(file.readText())) {
                // **A quoted claim is a quotation, not a claim.** A comment can quote a sentence in
                // double quotes -- a superseded one among them -- without this test reading it as
                // the assertion being made again, so quotations are what is stripped.
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
     * Every block comment and KDoc in a Kotlin source, plus each run of adjacent `//` lines, as one
     * string each.
     *
     * A run of `//` lines counts as one block because that is how the engine writes an argument
     * inside a function body, and splitting it per line would separate a sprite's name from the
     * claim made about it two lines down.
     */
    private fun commentBlocks(source: String): List<String> {
        val blocks = mutableListOf<String>()
        // Scanned by index rather than by regex: a regex over a whole block comment overflows the
        // stack on `PaperRenderer.kt`, which is the file this test most needs to read.
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
     * test exists to catch, happening to this test: a file left off the list keeps stale numbers
     * that nothing reads. **Ask the tree, do not keep the list.** Walking the directory costs a few
     * milliseconds and cannot go out of date.
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
