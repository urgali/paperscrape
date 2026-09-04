package com.paperscrape.livewallpaper.engine

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The scene determines the PNG: two goldens that describe the same scene must share one PNG.**
 *
 * ### The derivation
 *
 * A golden is an assertion against a *committed PNG*. If two goldens describe the same scene, their
 * two PNGs come out byte-identical — and the second one then has no way to fail that the first does
 * not already have. Its whole-frame assertion carries no information: any change that moves it moves
 * the other in the same instant, by the same pixels, for the same reason.
 *
 * What *does* carry information in that situation is the [GoldenFocus] — and a focus is an assertion
 * over a rectangle of **the same frame**, so it needs no second PNG to live on. Hence the property:
 * one scene, one PNG, however many focus rectangles that scene has earned.
 *
 * This is not a tidiness preference. It is a statement about the information content of an
 * assertion, and it is checkable by hashing files, which is what this does.
 *
 * ### Why it exists, and why the check is over the whole directory
 *
 * The defect has now happened twice. `PeopleGoldenTest.people-window`'s own comment records the
 * first: *"two goldens of one frame under two names would double the maintenance and halve the
 * coverage, and the first version of this file did exactly that — the two PNGs came out
 * byte-identical."* That was fixed **inside** `PeopleGoldenTest`, by moving one scene to another
 * theme.
 *
 * It came back **between** classes when the theme goldens were added to `SceneGoldenTest`, because
 * nobody re-ran the comparison across the whole folder: `people-mixed` and `theme-winter` described
 * one winter frame, `people-commercial` and `theme-desert` one desert frame, and `people-group`
 * still shares `day`'s. Three pairs, none of them visible from inside either file.
 *
 * So the check is deliberately **over the committed directory rather than over one suite**. A guard
 * that only looked at its own class would have passed every day of the two releases the duplicates
 * survived.
 *
 * ### Why this is a JVM test
 *
 * It is a property of the files in the repository, not of anything rendered: no device, no scene, no
 * backend. Running it here means it runs under `:app:testDebugUnitTest`, which is the first gate
 * anyone runs, rather than only when a phone is plugged in. Several JVM tests already read the
 * repository this way — `SpriteGeometryTest`, `SpriteCanvasConventionTest`,
 * `SpriteMeasurementClaimTest` — and this uses their idiom.
 *
 * ### There is no allowlist, and there must not be one
 *
 * An exception here would be a scene claiming to be two scenes. If a pair ever needs to be excused,
 * the pair is the defect and the fix is to make the two scenes actually differ or to merge them —
 * not to teach this test to look away. A guard with an escape hatch stops being evidence.
 *
 * ### It was parked for one release, and is not any more
 *
 * v4.21 delivered this `@Ignore`d, with one pair still standing: `day.png` = `people-group.png`.
 * Two of the three had been closed in that pass — `theme-winter` and `theme-desert` were the
 * duplicate halves and were removed — but the third needed a decision the implementing session was
 * not authorised to take, so the guard would have failed for a reason that was recorded rather than
 * unknown, and this project does not ship red suites.
 *
 * It was closed in the pass that followed. `people-group`'s scene was measured against `day`'s
 * field by field — of forty `SceneCustomization` fields exactly two differed, both the people
 * density — so the two were one scene, and `people-group.png` was deleted rather than regenerated.
 * Its `PAVEMENT` focus now rides on `day.png` through `SceneGolden.assertMatches`'s `extraFocus`,
 * which leaves `SharedGoldenScenes.day()` untouched and so leaves the GL suite measuring exactly
 * what it measured before — checked by running it, not by reasoning about it.
 *
 */
class GoldenUniquenessTest {

    @Test
    fun `no two committed goldens are byte-identical`() {
        val dir = goldenDir()
        val pngs = dir.listFiles { f -> f.name.endsWith(".png") }?.sortedBy { it.name }.orEmpty()
        assertTrue("no goldens found in ${dir.path}", pngs.size >= 20)

        val byDigest = LinkedHashMap<String, MutableList<String>>()
        for (png in pngs) {
            val digest = MessageDigest.getInstance("SHA-256").digest(png.readBytes())
            val hex = digest.joinToString("") { "%02x".format(it) }
            byDigest.getOrPut(hex) { mutableListOf() } += png.name
        }

        val collisions = byDigest.filterValues { it.size > 1 }
        val report = collisions.entries.joinToString("; ") { (hex, names) ->
            "${names.joinToString(" = ")} (${hex.take(12)})"
        }
        assertTrue(
            "these goldens are byte-identical, so all but one of each group asserts nothing the " +
                "others do not already assert: $report. Two goldens with identical pixels are one " +
                "scene described twice: give them one PNG and put both sets of focus rectangles on " +
                "it, or make the scenes genuinely differ. Do not add an exception here -- see this " +
                "class's own doc for why there is no allowlist.",
            collisions.isEmpty(),
        )
    }

    private fun goldenDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + "src/androidTest/assets/golden")
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError(
            "could not locate src/androidTest/assets/golden from ${File(".").absolutePath}",
        )
    }
}
