package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * **Whichever of the settings' classes a process touches first, every one of them initialises, and whole** (v5.16).
 *
 * The wallpaper's process can start from its engine (an update, `adb install -r`, a restart of the phone: the system
 * creates the engine before anything else) or from the settings screen, and the two touch the classes in another order.
 * The v5.16B build crashed at its engine's first touch -- *"PaperScrape debug keeps stopping"*, on the BV6600 after an
 * `install -r`, `ExceptionInInitializerError` in `SceneCustomizationKt` -- while every test passed: the scarecrows' default
 * was a value of the file's own (`SceneCustomizationKt`), whose initialiser reads `SceneCustomization.DEFAULT`, so
 * `SceneCustomization` first built `DEFAULT`, its constructor asked the file for the default, and the file read the
 * `DEFAULT` still being built: null. The other way round the file first would have built `DEFAULT` with a null in it.
 * A JVM test process touches these classes once, in the order its first test happens to, which is why none saw it.
 *
 * So each class here is touched first in a class loader of its own, over the test's own class path (the boot loader its
 * only parent, so nothing comes from the test's), and the defaults it leads to are read back whole.
 */
class FirstClassTouchedTest {

    private val firsts = listOf(
        "com.paperscrape.livewallpaper.engine.SceneCustomization",
        "com.paperscrape.livewallpaper.engine.SceneCustomizationKt",
        "com.paperscrape.livewallpaper.prefs.WallpaperSettings",
        "com.paperscrape.livewallpaper.engine.CustomThemeDataKt",
        "com.paperscrape.livewallpaper.engine.ThemeCatalog",
        "com.paperscrape.livewallpaper.engine.ObjectVariantConfig",
        "com.paperscrape.livewallpaper.engine.DecorationExclusion",
        "com.paperscrape.livewallpaper.ui.SettingsUiModel",
    )

    @Test
    fun `every settings class initialises whichever is touched first, and the defaults come out whole`() {
        val urls = System.getProperty("java.class.path").orEmpty().split(File.pathSeparator)
            .filter { it.isNotBlank() }.map { File(it).toURI().toURL() }.toTypedArray()
        assertTrue("the test's class path", urls.isNotEmpty())
        for (name in firsts) {
            URLClassLoader(urls, null).use { loader ->
                val first = runCatching { Class.forName(name, true, loader) }
                assertTrue("$name touched first: ${first.exceptionOrNull()?.let { it.toString() + " <- " + it.cause }}", first.isSuccess)
                // And what it led to is whole: the shipped defaults, every one of their configurations there.
                val customization = Class.forName("com.paperscrape.livewallpaper.engine.SceneCustomization", true, loader)
                val companion = customization.getField("Companion").get(null)
                val default = companion.javaClass.getMethod("getDEFAULT").invoke(companion)
                assertNotNull("$name first: SceneCustomization.DEFAULT", default)
                for (getter in customization.methods.filter { it.parameterCount == 0 && it.name.startsWith("get") && !it.returnType.isPrimitive }) {
                    assertNotNull("$name first: DEFAULT.${getter.name}()", getter.invoke(default))
                }
            }
        }
    }
}
