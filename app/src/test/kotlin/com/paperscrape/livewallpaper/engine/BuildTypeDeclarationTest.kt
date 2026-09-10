package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The build types this project declares, and what keeps the fourth one from becoming shippable.**
 *
 * `BACKLOG_v4_26.md` item 66: the `perf` build type — a release-like build, signed with the
 * committed debug keystore, that the item-32 CPU protocol is measured on — was rebuilt by hand
 * every session that needed it and deleted again afterwards, and in v4.25 the deletion did not
 * happen. Four lines of build configuration reached the delivery ZIP and the published tag. The
 * maintainer decided in v4.27 that it is committed instead, on the grounds that a rule enforced by
 * remembering fails on the session that forgets, and that two sessions measuring the same thing
 * should be measuring the same binary.
 *
 * **This file is the other half of that decision.** Committing it is only safe if it cannot quietly
 * turn into something a user could install, and if nothing in CI ever builds it. Both are checked
 * here rather than trusted:
 *
 * - the declared set of build types is pinned, so a fifth one cannot appear unnoticed;
 * - `perf` is pinned to the debug signing config and the `.debug` application id suffix, so it can
 *   never be signed with the release key nor install over a real installation;
 * - no workflow builds it, and the release workflow builds `assembleRelease`.
 *
 * The argument against committing it, recorded in item 66, was that `initWith(release)` plus a debug
 * signing config is a build that looks like a release and is not one. That argument is answered by
 * the last two assertions and not by this comment.
 *
 * ### One limitation, and it bites locally rather than in CI
 *
 * **Everything this class reads — `app/build.gradle.kts` and the workflow files — is invisible to
 * Gradle's up-to-date check for `testDebugUnitTest`.** The task's inputs are the compiled classes,
 * and editing a build script does not change them. So a local run right after changing a build type
 * can report `UP-TO-DATE` and pass without executing a line of this file: three mutations were
 * applied while writing it and all three appeared to be caught by nothing, which is exactly what
 * that looks like. **When you have changed a build script or a workflow, run this with
 * `--rerun-tasks`.** CI is unaffected — every run is a fresh checkout with no previous outcome to
 * reuse — which is why this is a note rather than a defect.
 */
class BuildTypeDeclarationTest {

    private val buildFile: String by lazy { File(repoRoot(), "app/build.gradle.kts").readText() }

    /**
     * Every build type the file declares, in the order it declares them.
     *
     * Read out of the `buildTypes { }` block rather than from a list somebody keeps in step: the
     * point of the check is to notice a declaration nobody told it about.
     */
    private fun declaredBuildTypes(): List<String> {
        val block = buildFile.substringAfter("\n    buildTypes {\n").substringBefore("\n    }\n")
        // `release {` and `debug {` are accessors on the container; anything else arrives as
        // `create("name") {`. Both forms are matched so a rename cannot slip past by changing shape.
        val named = Regex("""create\("([A-Za-z0-9_]+)"\)\s*\{""").findAll(block).map { it.groupValues[1] }
        val accessors = Regex("""(?m)^        (release|debug)\s*\{""").findAll(block).map { it.groupValues[1] }
        return (accessors + named).toList()
    }

    @Test
    fun `the project declares exactly release, debug and perf`() {
        assertEquals(
            "a build type has appeared or been renamed. Every one of them is something a build can " +
                "produce, so adding one is a decision: record it in the backlog and in this test, or " +
                "remove it",
            listOf("release", "debug", "perf"),
            declaredBuildTypes(),
        )
    }

    @Test
    fun `perf cannot be signed with the release key and cannot install over a real installation`() {
        val block = buildFile.substringAfter("""create("perf") {""").substringBefore("\n        }")
        assertTrue(
            "perf no longer starts from release, so it is no longer the release-like build the " +
                "item-32 protocol is measured on and every number taken on it is about something else",
            block.contains("""initWith(getByName("release"))"""),
        )
        assertTrue(
            "perf is no longer signed with the committed debug keystore. It must never reach the " +
                "release signing config: that config is the app's identity and only exists on the " +
                "maintainer's machine and in GitHub Secrets",
            block.contains("""signingConfig = signingConfigs.getByName("debug")"""),
        )
        assertTrue(
            "perf has lost its .debug application id suffix, so it would install over a user's real " +
                "PaperScrape rather than beside it",
            block.contains("""applicationIdSuffix = ".debug""""),
        )
        assertTrue(
            "perf is debuggable again, which is exactly what it exists not to be: a debug build is " +
                "not what users run, and v4.26 measured a whole conclusion that was an artefact of one",
            block.contains("isDebuggable = false"),
        )
    }

    /**
     * **Nothing that reaches a user is built from `perf`, and this is the check rather than the
     * claim.**
     *
     * The release job builds `assembleRelease`; the checks build `assembleDebug`, `test` and `lint`.
     * If a workflow ever names this build type, committing it stops being free and the decision has
     * to be taken again.
     */
    @Test
    fun `no workflow builds perf, and the release workflow builds assembleRelease`() {
        val workflows = File(repoRoot(), ".github/workflows").listFiles()
            ?.filter { it.name.endsWith(".yml") || it.name.endsWith(".yaml") }
            ?: emptyList()
        assertTrue("no workflows found; this check would pass over an empty directory", workflows.isNotEmpty())
        for (file in workflows) {
            val text = file.readText()
            for (forbidden in listOf("assemblePerf", "bundlePerf", "installPerf", "testPerf", "perfUnitTest")) {
                assertTrue(
                    "${file.name} builds $forbidden. `perf` is committed on the basis that no " +
                        "workflow builds it, which is what makes committing it change nothing that " +
                        "ships; that basis is gone",
                    !text.contains(forbidden),
                )
            }
        }
        assertTrue(
            "no workflow builds assembleRelease any more, so what CI publishes is not what this " +
                "test was written against",
            workflows.any { it.readText().contains("assembleRelease") },
        )
    }

    private fun repoRoot(): File {
        // `dir` is declared nullable and walked to null rather than tested through `parentFile`:
        // the older shape in this suite compiles with a "Java type mismatch: inferred type is
        // 'File?'" warning on every copy of it, because `parentFile` is a platform type.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }
}
