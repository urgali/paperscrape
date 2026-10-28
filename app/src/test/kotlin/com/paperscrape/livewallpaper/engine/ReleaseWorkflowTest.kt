package com.paperscrape.livewallpaper.engine

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The release job refuses what it cannot take back, before it builds, and hands no secret to a
 * script's text or to git.** Four rules of `.github/workflows/`, none of which changes what the
 * maintainer does to publish: the ZIP, the commit, the tag.
 *
 * A published release cannot be edited, and its body is the in-app update dialog's text: a tag whose
 * `release-notes/<tag>.md` is missing fails the job rather than publish a body that says nothing, and
 * it fails it -- as does a release that already exists -- before the signed build and the build's
 * public attestation. "Already exists" is decided by one request, `gh api` for the release under the tag,
 * and only its 404 lets the job go on: the existing-release step's own script is run by bash against a
 * fake `gh` that answers each way it can.
 *
 * The workflows are read as text, step by step (a step is a `      - ` item of a job's `steps`), because
 * no YAML parser is on the unit tests' classpath; `.github/workflows` and `release-notes` are inputs of
 * the unit tests (the bottom of `app/build.gradle.kts`), so an edit to either runs this again.
 */
class ReleaseWorkflowTest {

    private val workflow: File by lazy { File(repoRoot(), ".github/workflows/android-build.yml") }

    @Test
    fun `a tag without its release note fails before the signed build, and no generic text is published`() {
        val steps = steps(workflow, "release")
        val build = steps.indexOfFirst { runScript(it).contains("./gradlew assembleRelease") }
        val note = steps.indexOfFirst { step ->
            val script = runScript(step)
            script.contains("release-notes/") && script.contains("exit 1")
        }
        assertTrue("the release job no longer builds assembleRelease", build >= 0)
        assertTrue(
            "no step of the release job fails on a missing release-notes/<tag>.md before the signed build " +
                "(the note is step $note, the build step $build)",
            note in 0 until build,
        )
        val fallback = workflow.readLines()
            .filterNot { it.trimStart().startsWith("#") }
            .filter { it.contains("New PaperScrape build") }
        assertEquals("the workflow publishes a generic body again", emptyList<String>(), fallback)
    }

    @Test
    fun `the note of the version the build names exists and says something`() {
        val versionName = Regex("""(?m)^\s*versionName = "([^"]+)"""")
            .find(File(repoRoot(), "app/build.gradle.kts").readText())?.groupValues?.get(1)
        assertTrue("no versionName in app/build.gradle.kts", versionName != null)
        val note = File(repoRoot(), "release-notes/v$versionName.md")
        assertTrue("${note.path} is missing: the tag v$versionName would fail the release", note.isFile)
        assertTrue("${note.path} is empty", note.readText().isNotBlank())
    }

    @Test
    fun `an existing release is refused before the signed build and the attestation`() {
        val steps = steps(workflow, "release")
        val refuse = steps.indexOfFirst { runScript(it).contains(RELEASE_REQUEST) }
        val build = steps.indexOfFirst { runScript(it).contains("./gradlew assembleRelease") }
        val attest = steps.indexOfFirst { it.contains("uses: actions/attest-build-provenance@") }
        assertTrue("no step refuses an existing release", refuse >= 0)
        assertTrue(
            "the existing-release check is step $refuse, after the signed build ($build) or the attestation ($attest)",
            refuse < build && refuse < attest,
        )
    }

    /**
     * The existing-release step's script, run by `bash -e` (the runner's shell for a `run:`) with a fake
     * `gh` first on the `PATH`, answering as `gh api --include --silent` does: the HTTP status line and the
     * headers on stdout when GitHub answered, gh's message on stderr. A release that exists stops the job;
     * so does every failure that is not a 404 -- no network, a refused token, no token, GitHub failing --
     * because it says nothing about whether the release exists, and the job would otherwise go on to the
     * signed build and its public attestation without knowing. Only the 404 lets the job go on.
     */
    @Test
    fun `only a missing release lets the job go on, and any other failure of gh stops it`() {
        val step = steps(workflow, "release").first { runScript(it).contains(RELEASE_REQUEST) }
        val script = runScript(step).trimIndent()
        assertTrue("the step's script holds a workflow expression bash cannot run", "\${{" !in script)
        val answers = listOf(
            Triple("the release exists", ghAnswer("HTTP/2.0 200 OK", null, 0), false),
            Triple("no release under the tag", ghAnswer("HTTP/2.0 404 Not Found", "gh: Not Found (HTTP 404)", 1), true),
            Triple("no network", ghAnswer(null, "error connecting to api.github.com", 1), false),
            Triple("a refused token", ghAnswer("HTTP/2.0 401 Unauthorized", "gh: Bad credentials (HTTP 401)", 1), false),
            Triple(
                "no token",
                ghAnswer(null, "gh: To use GitHub CLI in a GitHub Actions workflow, set the GH_TOKEN environment variable.", 4),
                false,
            ),
            Triple("GitHub failing", ghAnswer("HTTP/2.0 502 Bad Gateway", "gh: HTTP 502", 1), false),
        )
        val wrong = answers.mapNotNull { (answer, gh, goesOn) ->
            val run = runWithFakeGh(script, gh)
            assertEquals(
                "gh was not asked about the tag's release ($answer)",
                "api --include --silent repos/owner/repo/releases/tags/v9.99",
                run.ghArguments,
            )
            if ((run.status == 0) == goesOn) null else "$answer: the step exited ${run.status}, saying: ${run.output.trim()}"
        }
        assertEquals(
            "the existing-release step lets the job go on, or stops it, on the wrong answers of gh",
            emptyList<String>(), wrong,
        )
    }

    @Test
    fun `no secret is pasted into a script's text`() {
        val pasted = workflows().flatMap { file ->
            jobs(file).flatMap { job -> steps(file, job) }
                .filter { runScript(it).contains("\${{ secrets.") }
                .map { "${file.name}: ${it.lines().first().trim()}" }
        }
        assertEquals(
            "a secret is substituted into these scripts before bash reads them; pass it through the step's env",
            emptyList<String>(), pasted,
        )
    }

    @Test
    fun `no checkout leaves the job's token in git's configuration`() {
        val persisting = workflows().flatMap { file ->
            jobs(file).flatMap { job -> steps(file, job).map { job to it } }
                .filter { (_, step) -> step.contains("uses: actions/checkout@") }
                .filterNot { (_, step) -> step.lines().any { it.trim() == "persist-credentials: false" } }
                .map { (job, _) -> "${file.name}: job $job" }
        }
        assertEquals("these checkouts leave the token in .git/config for the build", emptyList<String>(), persisting)
    }

    /**
     * A fake `gh api --include --silent`'s body: [statusLine] and one header on stdout when GitHub answered (null when
     * it did not), [error] on stderr, and exit [exit].
     */
    private fun ghAnswer(statusLine: String?, error: String?, exit: Int): String = buildString {
        if (statusLine != null) append("printf '%s\\n%s\\r\\n\\r\\n' '$statusLine' 'Content-Type: application/json; charset=utf-8'; ")
        if (error != null) append("echo '$error' >&2; ")
        append("exit $exit")
    }

    /** What one run of a step's script did: its exit status, what `gh` was asked ("" when it was not), and its output. */
    private class StepRun(val status: Int, val ghArguments: String, val output: String)

    /**
     * Runs [script] with `bash -e` in a scratch directory, `TAG` v9.99 and `REPOSITORY` owner/repo, and a
     * `gh` first on the `PATH` that records its arguments and then runs [ghBody]. Its temporary files go in the
     * scratch directory too.
     */
    private fun runWithFakeGh(script: String, ghBody: String): StepRun {
        val dir = kotlin.io.path.createTempDirectory("release-check").toFile()
        try {
            val bin = File(dir, "bin").apply { mkdirs() }
            val argsFile = File(dir, "gh-args")
            val outputFile = File(dir, "output")
            File(bin, "gh").apply {
                writeText("#!/bin/bash\necho \"\$*\" > \"${argsFile.path}\"\n$ghBody\n")
                setExecutable(true)
            }
            val stepFile = File(dir, "step.sh").apply { writeText(script + "\n") }
            val builder = ProcessBuilder("bash", "--noprofile", "--norc", "-e", stepFile.path)
                .directory(dir)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
            builder.environment()["PATH"] = bin.path + File.pathSeparator + (System.getenv("PATH") ?: "")
            builder.environment()["TAG"] = "v9.99"
            builder.environment()["REPOSITORY"] = "owner/repo"
            builder.environment()["TMPDIR"] = dir.path
            val process = builder.start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw AssertionError("the existing-release step did not end within 30 s")
            }
            return StepRun(
                status = process.exitValue(),
                ghArguments = if (argsFile.isFile) argsFile.readText().trim() else "",
                output = outputFile.readText(),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun workflows(): List<File> {
        val found = File(repoRoot(), ".github/workflows").listFiles()
            ?.filter { it.name.endsWith(".yml") || it.name.endsWith(".yaml") }
            ?: emptyList()
        assertTrue("no workflow files found; every assertion here would pass over nothing", found.isNotEmpty())
        return found
    }

    /** The jobs of [file]: the keys two spaces in under `jobs:`. */
    private fun jobs(file: File): List<String> {
        val lines = file.readLines()
        val start = lines.indexOf("jobs:")
        assertTrue("${file.name} has no jobs", start >= 0)
        return lines.drop(start + 1)
            .mapNotNull { Regex("""^  ([A-Za-z0-9_-]+):\s*$""").find(it)?.groupValues?.get(1) }
    }

    /** The steps of [job] in [file], in order, each as its lines: from its `      - ` to the next one. */
    private fun steps(file: File, job: String): List<String> {
        val lines = file.readLines()
        val start = lines.indexOf("  $job:")
        assertTrue("${file.name} has no job $job", start >= 0)
        val end = (start + 1 until lines.size).firstOrNull { Regex("""^  [A-Za-z0-9_-]+:\s*$""").matches(lines[it]) }
            ?: lines.size
        val steps = mutableListOf<MutableList<String>>()
        for (line in lines.subList(start + 1, end)) {
            if (line.startsWith("      - ")) {
                steps += mutableListOf(line)
            } else if (steps.isNotEmpty()) {
                steps.last() += line
            }
        }
        assertTrue("${file.name}: job $job has no steps", steps.isNotEmpty())
        return steps.map { it.joinToString("\n") }
    }

    /** The text of a step's `run:` -- a block scalar's lines, or the one line -- and nothing of its `env:`. */
    private fun runScript(step: String): String {
        val lines = step.lines()
        val at = lines.indexOfFirst { Regex("""^\s*(- )?run:""").containsMatchIn(it) }
        if (at < 0) return ""
        val column = lines[at].indexOf("run:")
        val inline = lines[at].substringAfter("run:").trim()
        if (inline != "|" && inline != ">") return inline
        return lines.drop(at + 1)
            .takeWhile { it.isBlank() || it.indexOfFirst { c -> c != ' ' } > column }
            .joinToString("\n")
    }

    private companion object {
        /** What the existing-release step asks GitHub for: the published release under the tag. */
        const val RELEASE_REQUEST = "gh api --include --silent \"repos/\$REPOSITORY/releases/tags/\$TAG\""
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }
}
