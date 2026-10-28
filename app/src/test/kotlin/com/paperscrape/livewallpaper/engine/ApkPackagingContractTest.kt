package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What the build leaves out of the APK, and the libraries it does not take.** Four promises made by
 * `app/build.gradle.kts` rather than by code: DataStore's native counter and the libraries' `.version`
 * files out of the APK, only English resources, and no library the app does not use.
 *
 * Read off the build script's code lines -- its comments taken off, as `ShippedApkContractTest` does,
 * because they name what they leave out -- and off `src/main/kotlin`. Both are inputs of the unit tests
 * (the bottom of `app/build.gradle.kts`), so an edit to either runs this again. What lands in the APK is
 * the APK's to show (`unzip -l`); this holds the build file to the decisions, so none drifts back without
 * a test going red.
 */
class ApkPackagingContractTest {

    private val buildFileCode: String by lazy {
        File(repoRoot(), "app/build.gradle.kts").readLines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
    }

    /**
     * `libdatastore_shared_counter.so` serves `MultiProcessDataStoreFactory` alone, and this app keeps its
     * stores in one process: R8 leaves nothing in the release dex that loads it. A source that starts using
     * the multi-process factory needs the library back -- without it the factory fails with
     * `UnsatisfiedLinkError` on the phone -- so the two halves are held together.
     */
    @Test
    fun `DataStore's native counter is left out, and no source makes a multi-process store`() {
        assertTrue(
            "app/build.gradle.kts packages libdatastore_shared_counter.so again: 34 KB of the APK that nothing loads",
            buildFileCode.contains("""excludes += "**/libdatastore_shared_counter.so""""),
        )
        val multiProcess = Regex("""\b(MultiProcessDataStoreFactory|MultiProcessCoordinator|createMultiProcessCoordinator)\b""")
        val users = File(repoRoot(), "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") }
                    .filter { (_, line) -> multiProcess.containsMatchIn(line) }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }
            .toList()
        assertEquals(
            "a source uses DataStore across processes, whose native counter app/build.gradle.kts leaves out of " +
                "the APK: take the exclude out of `packaging { jniLibs }` in the same change",
            emptyList<String>(), users,
        )
    }

    /**
     * Seventy `.version` files under `META-INF`, read by Android Studio's App Inspection on a debuggable app and
     * by nothing else: left out of `release` and `perf`, kept in `debug` for the inspectors. The block that leaves
     * them out is held whole, line by line, so a selector that takes every variant in fails here as a list that
     * names one more build type does; and it is the only place the build leaves them out.
     */
    @Test
    fun `the release and perf APKs leave the libraries' version files out, the debug APK keeps them`() {
        val block = buildFileCode.substringAfter("androidComponents {", "").substringBefore("\n}")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(
            "the androidComponents block: only release and perf leave the version files out",
            listOf(
                """for (notDebuggable in listOf("release", "perf")) {""",
                """onVariants(selector().withBuildType(notDebuggable)) { variant ->""",
                """variant.packaging.resources.excludes.add("/META-INF/*.version")""",
                "}",
                "}",
            ),
            block,
        )
        assertEquals(
            "the version files are left out somewhere else too, for every build type",
            1,
            Regex("""META-INF/\*\.version""").findAll(buildFileCode).count(),
        )
    }

    /**
     * The app is English-only, and the libraries' own strings follow: without the
     * filter Compose and Material3 put their accessibility labels in 74 languages into `resources.arsc`.
     */
    @Test
    fun `only English resources are packaged`() {
        val block = buildFileCode.substringAfter("androidResources {", "").substringBefore("}")
        val locales = Regex(""""([A-Za-z_+-]+)"""").findAll(block).map { it.groupValues[1] }.toList()
        assertEquals("the locales app/build.gradle.kts packages", listOf("en"), locales)
    }

    /**
     * Nothing uses `appcompat` (the theme is the platform's), there is no `@Preview` for `ui-tooling`, and
     * the settings' icons are `material-icons-core`'s and the app's own: `material-icons-extended` would bring
     * 11 398 classes for 39 icons into every debug build. `material-icons-core` is named, because material3
     * does not bring it.
     */
    @Test
    fun `the build takes no library the app does not use`() {
        val unused = listOf(
            "androidx.appcompat:appcompat",
            "androidx.compose.ui:ui-tooling",
            "androidx.compose.material:material-icons-extended",
        ).filter { buildFileCode.contains("\"$it") }
        assertEquals("libraries app/build.gradle.kts takes again", emptyList<String>(), unused)
        assertTrue(
            "material-icons-core is not declared: `Icons` and the core icons come from it, and material3 does not bring it",
            buildFileCode.contains(""""androidx.compose.material:material-icons-core""""),
        )
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
