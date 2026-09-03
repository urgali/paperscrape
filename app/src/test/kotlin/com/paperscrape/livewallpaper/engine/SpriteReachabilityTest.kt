package com.paperscrape.livewallpaper.engine

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every PNG the app ships can be reached by a draw path, or the registry says why it cannot.
 *
 * ### Why this test exists rather than a one-off audit
 *
 * Unreachable sprites are not a thing that happens once. They accumulate, they are invisible --
 * an APK is not smaller in any way a user notices and nothing fails -- and they cost the one
 * budget this project actually rations, the decoded-sprite ceiling
 * ([SpriteGeometryTest] holds the argument for where it sits). By v4.19 the shipped set carried
 * **twenty** of them: sixteen occupant busts and four `road_*`/`house_window` drawings that
 * nothing has ever blitted. v4.19 recovered eight, v4.20 recovered the rest, and the interesting
 * part is *how they survived*: a table listing them existed for the sole purpose of keeping them
 * referenced so lint would not report them unused. A one-off audit would have deleted the files
 * and left the mechanism that hid them.
 *
 * So there are two assertions here, and the second is the one that matters more.
 */
class SpriteReachabilityTest {

    // ------------------------------------------------------------------ the shipped set

    /**
     * A shipped sprite is named by the code, or it is declared unreachable with a reason.
     *
     * "Declared" means `usage: "orphan"` in `tools/assets/sources/sprites.json` **with a non-empty
     * note**, because the point is not to have an escape hatch but to make the reason survive to
     * the next person who wonders. And the check runs in **both** directions: an entry declared
     * orphan that the code does in fact reach is a stale declaration, and it fails here too --
     * otherwise the list would silently turn into an allowlist that nobody prunes.
     *
     * Naming is a *necessary* condition for reachability rather than a sufficient one; the second
     * test is what closes that gap.
     */
    @Test
    fun `every shipped sprite is named by the code, or is declared unreachable with a reason`() {
        val shipped = drawableDir().listFiles { f -> f.name.endsWith(".png") }
            .orEmpty().map { it.name.removeSuffix(".png") }.toSortedSet()
        assertTrue("no sprites found -- the test is not looking where it thinks", shipped.size > 100)

        val named = referencedDrawableNames()
        val registry = registryDocument()
        val declaredOrphans = registry.getJSONArray("sprites").let { sprites ->
            (0 until sprites.length()).map { sprites.getJSONObject(it) }
                .filter { it.getString("usage") == "orphan" }
                .associate { it.getString("name") to it.optString("notes") }
        }

        val unreachable = shipped.filterNot { it in named }.toSortedSet()

        assertEquals(
            "these sprites ship and no source file names them, and the registry does not declare " +
                "them orphan -- either wire them up, delete them, or record why they stay",
            emptyList<String>(),
            (unreachable - declaredOrphans.keys).toList(),
        )
        assertEquals(
            "these sprites are declared orphan but the code names them, so the declaration is " +
                "stale -- a list nobody prunes is an allowlist, not a declaration",
            emptyList<String>(),
            (declaredOrphans.keys - unreachable).sorted(),
        )
        for ((name, note) in declaredOrphans) {
            assertTrue(
                "$name is declared orphan with no note: the reason is the whole value of the " +
                    "declaration",
                note.length > 40,
            )
        }
    }

    // ------------------------------------------------------------------ the mechanism

    /**
     * No sprite table is declared and never read.
     *
     * This is the shape the defect actually took. `personCarHeadDrawables` listed eight busts,
     * appeared exactly once in the whole module -- its own declaration -- and existed so that
     * `UnusedResources` would stay quiet about files no draw path could reach. A sprite hidden
     * behind a table like that passes the first test in this class and is still dead weight.
     *
     * The check is deliberately blunt: a `val` whose initialiser mentions `R.drawable.` and whose
     * own name occurs exactly once across the main sources is a table nothing reads. Blunt is
     * right here -- a real table is read at least once by whatever draws from it, so the honest
     * cost of this rule is zero, and anything cleverer would be a dataflow analysis that could be
     * wrong quietly.
     */
    @Test
    fun `no sprite table is declared and never read`() {
        val sources = mainSources()
        val text = sources.associateWith { it.readText() }
        val whole = text.values.joinToString("\n")

        val declaration = Regex(
            """\bval\s+([A-Za-z][A-Za-z0-9_]*)\s*(?::[^=\n]+)?=\s*(?:array|intArray)Of\(""",
        )
        val dead = mutableListOf<String>()
        for ((file, body) in text) {
            for (match in declaration.findAll(body)) {
                val name = match.groupValues[1]
                val initialiser = body.substring(match.range.first, minOf(body.length, match.range.last + 4000))
                if (!initialiser.contains("R.drawable.")) continue
                val uses = Regex("""\b${Regex.escape(name)}\b""").findAll(whole).count()
                if (uses <= 1) dead += "${file.name}: $name"
            }
        }
        assertEquals(
            "these sprite tables are declared and never read. That is how an unreachable sprite " +
                "stays in the set: the table keeps it referenced for lint while no draw path can " +
                "reach it. Delete the table and then decide what to do with the sprites",
            emptyList<String>(),
            dead.sorted(),
        )
    }

    // ------------------------------------------------------------------ plumbing

    private fun referencedDrawableNames(): Set<String> {
        val names = HashSet<String>()
        for (file in mainSources()) {
            for (chunk in file.readText().split("R.drawable.").drop(1)) {
                names += chunk.takeWhile { it.isLetterOrDigit() || it == '_' }
            }
        }
        return names
    }

    private fun mainSources(): List<File> =
        File(repoRoot(), "app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun drawableDir() = File(repoRoot(), "app/src/main/res/drawable-nodpi")

    private fun registryDocument() =
        JSONObject(File(repoRoot(), "tools/assets/sources/sprites.json").readText())

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }
}
