package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Three promises about the APK that leaves this repository, each one made by a build file rather
 * than by code, and each one the subject of a v5.3 repair.**
 *
 * They have nothing in common except where they live: what goes *into* the binary is decided in
 * `app/build.gradle.kts`, `AndroidManifest.xml` and `.github/workflows/`, none of which any Kotlin
 * test would otherwise read. The v5.3B security audit found all three by reading those files, and
 * a finding from reading a file is one nothing re-reads.
 *
 * ### Why it lives beside `BuildTypeDeclarationTest` and not in a package of its own
 *
 * It started in a `com.paperscrape.livewallpaper.build` package, which is the obvious name and is
 * a trap: the package directory is then literally called `build`, and **every tool that skips
 * build output skips it**. The delivery archive for this very round was assembled once with this
 * file silently absent, and only the entry-by-entry comparison against the previous archive found
 * it -- one added file where two were expected. It sits next to `BuildTypeDeclarationTest`, which
 * reads the same files for the same kind of reason.
 *
 * ### The up-to-date trap, and why it is smaller than it was
 *
 * `BuildTypeDeclarationTest` carries a warning that a test reading a build script can pass without
 * running: the unit-test task's inputs are the compiled classes, and editing `build.gradle.kts`
 * changes none of them, so the task reports `UP-TO-DATE` and green having executed nothing. v5.3
 * declared `app/build.gradle.kts` and `.github/workflows` as inputs of every `Test` task (see the
 * bottom of `app/build.gradle.kts`), which fixes it for both files and for that older test too.
 * The mutations behind each assertion below were run *after* that declaration and each one did go
 * red without `--rerun-tasks`.
 */
class ShippedApkContractTest {

    private val buildFile: String by lazy { File(repoRoot(), "app/build.gradle.kts").readText() }
    private val manifest: String by lazy { File(repoRoot(), "app/src/main/AndroidManifest.xml").readText() }

    /**
     * The build script with its comment lines removed.
     *
     * The same distinction `InternetInventoryTest` draws for hostnames, and for the same reason:
     * a name in a comment is a *reference*, a name in an expression is a *fact about the build*.
     * These files are heavily commented and the comments explain what was removed and why, so a
     * check that matched comment text would fail on its own explanation -- which is exactly what
     * happened on the first run of this class.
     */
    private val buildFileCode: String by lazy {
        buildFile.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }

    /**
     * **No secret is compiled into the app.**
     *
     * Until v5.3 the maintainer's Open-Meteo key came from a `PAPERSCRAPE_OPENMETEO_API_KEY` env
     * var in CI and went into `BuildConfig` through `buildConfigField`. A `buildConfigField` of
     * type `String` is a **string constant in the dex**, and R8 renames classes and methods, not
     * literals: the audit built a release with a marker in that variable and read the marker back
     * out of the minified `classes.dex`, sitting in the string table between `ATOMIC` and
     * `AUTUMN`. Downloading the published APK and running `strings` on it was the entire attack.
     *
     * The check is on the *shape*, not on the name of that one key. `System.getenv` in a build
     * script is fine — the release signing config reads four of them, and those stay on the
     * runner and sign the APK rather than travelling inside it. What must never happen again is an
     * environment value reaching `buildConfigField`, so that is what is asserted.
     */
    @Test
    fun `no environment secret is baked into BuildConfig`() {
        val fields = Regex("""buildConfigField\s*\(([^)]*)\)""").findAll(buildFileCode).map { it.groupValues[1] }.toList()
        val fromEnv = fields.filter { it.contains("getenv") || it.contains("openMeteoApiKey") }
        assertTrue(
            "a buildConfigField is being fed from the build environment:\n" +
                fromEnv.joinToString("\n") { "  buildConfigField($it)" } +
                "\nA String buildConfigField is a plain constant in classes.dex -- R8 does not " +
                "obfuscate literals -- so anything put there is published to every user with the " +
                "APK. Secrets belong on the runner (see the release signingConfig), never in the " +
                "binary. This is v5.3B audit finding S1, and it shipped for several releases.",
            fromEnv.isEmpty(),
        )

        val forbidden = "PAPERSCRAPE_OPENMETEO_API_KEY"
        assertTrue(
            "$forbidden is read in app/build.gradle.kts again. The key it carried was readable " +
                "in every published APK; v5.3 removed the mechanism, not just the value.",
            !buildFileCode.contains(forbidden),
        )
        val injecting = workflows().filter { file ->
            file.readLines().any { !it.trimStart().startsWith("#") && it.contains("$forbidden:") }
        }
        assertTrue(
            "these workflows still inject $forbidden into the build environment: " +
                injecting.joinToString { it.name } +
                ". Nothing reads it, so it is a secret handed to a build for no reason -- and the " +
                "next person to add a buildConfigField would find it waiting.",
            injecting.isEmpty(),
        )
    }

    /**
     * **Cleartext HTTP is refused by declaration, not by inherited default.**
     *
     * With `targetSdk = 37` the framework default is already "refuse" — but that default arrived
     * in Android 9, and `minSdk = 26`. On 8.0 and 8.1, which this app supports, the platform
     * default is "allow". The attribute is honoured from API 23, so it covers exactly the gap.
     *
     * The audit was explicit that this is not a hole being closed: the app never builds an
     * `http://` URL, and `sanitizeGitHubUrl` refuses any scheme that is not https. It is one line
     * that turns an inherited default into a stated one. (v5.3B audit, S3.)
     */
    @Test
    fun `the manifest refuses cleartext traffic`() {
        assertTrue(
            "android:usesCleartextTraffic=\"false\" has gone from <application>. Between minSdk 26 " +
                "and Android 9 the platform default is \"allow\", so without this the app's " +
                "behaviour on 8.0 and 8.1 is decided by the platform rather than by this file.",
            manifest.contains("""android:usesCleartextTraffic="false""""),
        )
        assertTrue(
            "the manifest declares a networkSecurityConfig as well. That is not wrong, but it " +
                "overrides the attribute above and this test would then be checking the wrong " +
                "file -- point it at res/xml/ instead of deleting it.",
            !manifest.contains("networkSecurityConfig"),
        )
    }

    /**
     * **Only ARM libraries are packaged.**
     *
     * Two transitive AndroidX dependencies ship a `.so` per ABI and AGP packages all four by
     * default. Built both ways rather than estimated: the release APK went from 2 732 518 B to
     * 2 665 676 B — **-66 842 B, -2.45 %** — for processors this app's installed base does not
     * have. For scale, the v5.3 dependency round added 16 756 B closing 41 advisories and that
     * was argued over line by line.
     *
     * **The cost is real and was accepted deliberately:** the APK no longer installs on an x86
     * emulator. Reopening that door is one string in this list, at the measured price — which is
     * why the assertion names the trade rather than just failing.
     */
    @Test
    fun `only ARM ABIs are packaged`() {
        val block = buildFileCode.substringAfter("        ndk {", "").substringBefore("        }")
        assertTrue(
            "the defaultConfig has no ndk { abiFilters } block. Without it AGP packages all four " +
                "ABIs and the APK carries ~66 800 bytes of x86 and x86_64 libraries for hardware " +
                "no Android phone has shipped with in years (v5.3B audit, W2).",
            block.contains("abiFilters"),
        )
        val abis = Regex(""""([a-z0-9_-]+)"""").findAll(block).map { it.groupValues[1] }.toList()
        assertEquals(
            "the packaged ABI list has changed. Adding x86_64 back is a legitimate decision — it " +
                "buys back emulator installs for about 2.45 % of the APK — but it is a decision, " +
                "so record it here and in the backlog rather than letting it drift.",
            listOf("armeabi-v7a", "arm64-v8a"),
            abis,
        )
    }

    private fun workflows(): List<File> {
        val found = File(repoRoot(), ".github/workflows").listFiles()
            ?.filter { it.name.endsWith(".yml") || it.name.endsWith(".yaml") }
            ?: emptyList()
        assertTrue("no workflow files found; every workflow assertion here would pass over nothing", found.isNotEmpty())
        return found
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
