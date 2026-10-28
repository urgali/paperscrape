package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Three promises about the APK that leaves this repository, each one made by a build file rather
 * than by code.**
 *
 * They have nothing in common except where they live: what goes *into* the binary is decided in
 * `app/build.gradle.kts`, `AndroidManifest.xml` and `.github/workflows/`, which no code reads, so a
 * promise kept there is kept only if a test reads the file.
 *
 * ### Why it lives beside `BuildTypeDeclarationTest` and not in a package of its own
 *
 * A `com.paperscrape.livewallpaper.build` package is the obvious name and a trap: the package
 * directory is then called `build`, and **every tool that skips build output skips it**, the
 * delivery archive's among them. It sits next to `BuildTypeDeclarationTest`, which reads the same
 * files for the same kind of reason.
 *
 * ### Up to date
 *
 * A test reading a build script runs only if the script is an input of the test task: the task's
 * other inputs are the compiled classes, which an edit to `build.gradle.kts` does not change, so the
 * task would report `UP-TO-DATE` and green having executed nothing. `app/build.gradle.kts` and
 * `.github/workflows` are declared as inputs of every `Test` task at the bottom of
 * `app/build.gradle.kts`, so an edit to either re-runs this class.
 */
class ShippedApkContractTest {

    private val buildFile: String by lazy { File(repoRoot(), "app/build.gradle.kts").readText() }
    private val manifest: String by lazy { File(repoRoot(), "app/src/main/AndroidManifest.xml").readText() }

    /**
     * The build script with its comment lines removed.
     *
     * The same distinction `InternetInventoryTest` draws for hostnames, and for the same reason:
     * a name in a comment is a *reference*, a name in an expression is a *fact about the build*.
     * The build script is heavily commented, and its comments name the things they explain, so a
     * check that matched comment text would fail on its own explanation.
     */
    private val buildFileCode: String by lazy {
        buildFile.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }

    /**
     * **No secret is compiled into the app.**
     *
     * A `buildConfigField` of type `String` is a **string constant in the dex**, and R8 renames
     * classes and methods, not literals: whatever is put there can be read out of the published APK
     * with `strings`. So no environment value reaches a `buildConfigField`, and
     * `PAPERSCRAPE_OPENMETEO_API_KEY`, the variable an Open-Meteo key would come from, is read by no
     * build file and injected by no workflow.
     *
     * The check is on the *shape*, not on the name of that one key. `System.getenv` in a build
     * script is fine — the release signing config reads four variables, and those stay on the
     * runner and sign the APK rather than travelling inside it.
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
                "binary.",
            fromEnv.isEmpty(),
        )

        val forbidden = "PAPERSCRAPE_OPENMETEO_API_KEY"
        assertTrue(
            "$forbidden is read in app/build.gradle.kts again: a key read there is readable in " +
                "every APK built with it. The mechanism stays gone, not just the value.",
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
     * It closes no hole: the app never builds an `http://` URL, and `sanitizeGitHubUrl` refuses any
     * scheme that is not https. It is one line that turns an inherited default into a stated one.
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
     * Transitive AndroidX dependencies ship a `.so` per ABI and AGP packages all four by default:
     * the x86 pair is weight in the APK for processors this app's users do not have.
     *
     * **The cost is accepted on purpose:** the APK does not install on an x86 emulator. Reopening
     * that door is one string in this list, at the price the assertion's message names — which is
     * why it names the trade rather than just failing.
     */
    @Test
    fun `only ARM ABIs are packaged`() {
        val block = buildFileCode.substringAfter("        ndk {", "").substringBefore("        }")
        assertTrue(
            "the defaultConfig has no ndk { abiFilters } block. Without it AGP packages all four " +
                "ABIs and the APK carries ~66 800 bytes of x86 and x86_64 libraries for hardware " +
                "no Android phone has shipped with in years.",
            block.contains("abiFilters"),
        )
        val abis = Regex(""""([a-z0-9_-]+)"""").findAll(block).map { it.groupValues[1] }.toList()
        assertEquals(
            "the packaged ABI list has changed. Adding x86_64 back is a legitimate decision — it " +
                "buys back emulator installs for about 2.45 % of the APK — but it is a decision, " +
                "so change this list on purpose rather than letting it drift.",
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
