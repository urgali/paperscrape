package com.paperscrape.livewallpaper.icon

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest, the enum and the drawables have to agree, and nothing at runtime can tell you
 * they do not.
 *
 * [SeasonalIcon] names six components and says which one the manifest enables; the manifest is
 * where those components actually exist. A mismatch is invisible in every unit test that does not
 * read both: an alias missing from the manifest turns into a `setComponentEnabledSetting` on a
 * component that is not there, and a wrong `enabledInManifest` turns into a fresh install that
 * believes the wrong icon is showing and never writes the right one.
 *
 * The other half is the one the test device cannot see at all. **This project's phone is Android
 * 10, and themed icons are Android 13+**, so the `<monochrome>` layer of these five new adaptive
 * icons is not drawn anywhere in the instrumented suite, on any screen, in any golden. An alias
 * carries a whole `<adaptive-icon>`, so a forgotten `<monochrome>` is a perfectly working icon
 * here and a broken themed icon on every modern phone. That is what the last test in this file
 * is for, and it is the only thing standing between that mistake and a release.
 */
class SeasonalIconManifestTest {

    private val manifest: String by lazy { walkUp("src/main/AndroidManifest.xml").readText() }

    private fun aliasBlock(icon: SeasonalIcon): String {
        val shortName = icon.aliasClassName.removePrefix("com.paperscrape.livewallpaper")
        val start = manifest.indexOf("android:name=\"$shortName\"")
        assertTrue("no activity-alias named $shortName in the manifest", start >= 0)
        return manifest.substring(start).substringBefore("</activity-alias>")
    }

    @Test
    fun `every icon has an activity-alias, and there are no others`() {
        for (icon in SeasonalIcon.entries) {
            val block = aliasBlock(icon)
            assertTrue("$icon must point at the one real launcher activity",
                "android:targetActivity=\".ui.SettingsActivity\"" in block)
            assertTrue("$icon must be a launcher entry",
                "android.intent.category.LAUNCHER" in block)
        }
        assertEquals(
            "the manifest has activity-alias blocks that SeasonalIcon does not know about",
            SeasonalIcon.entries.size,
            Regex("<activity-alias").findAll(manifest).count(),
        )
    }

    @Test
    fun `enabledInManifest says what the manifest says`() {
        for (icon in SeasonalIcon.entries) {
            val declared = Regex("""android:enabled="(true|false)"""").find(aliasBlock(icon))?.groupValues?.get(1)
            assertEquals("$icon", icon.enabledInManifest.toString(), declared)
        }
    }

    @Test
    fun `the launcher activity itself is not a launcher entry`() {
        // Otherwise the app drawer holds a permanent second entry beside the seasonal one, which
        // is the gate this whole feature has to pass.
        val activity = manifest.substring(manifest.indexOf("android:name=\".ui.SettingsActivity\""))
            .substringBefore("</activity>")
            .substringBefore("<activity-alias")
        assertTrue(
            "SettingsActivity must not declare a LAUNCHER filter: the aliases are the launcher entries",
            "android.intent.category.LAUNCHER" !in activity,
        )
    }

    @Test
    fun `each alias points at an adaptive icon that exists`() {
        for (icon in SeasonalIcon.entries) {
            val res = Regex("""android:icon="@mipmap/([a-z_0-9]+)"""").find(aliasBlock(icon))?.groupValues?.get(1)
            assertTrue("$icon declares no android:icon", res != null)
            assertEquals("$icon: roundIcon must be the same drawable", res,
                Regex("""android:roundIcon="@mipmap/([a-z_0-9]+)"""").find(aliasBlock(icon))?.groupValues?.get(1))
            assertTrue(
                "$icon points at @mipmap/$res, which does not exist",
                walkUpOrNull("src/main/res/mipmap-anydpi-v26/$res.xml") != null,
            )
        }
    }

    @Test
    fun `every adaptive icon carries all three layers, monochrome included`() {
        // The layer this device cannot draw. See the class comment.
        for (icon in SeasonalIcon.entries) {
            val res = Regex("""android:icon="@mipmap/([a-z_0-9]+)"""").find(aliasBlock(icon))!!.groupValues[1]
            val xml = walkUp("src/main/res/mipmap-anydpi-v26/$res.xml").readText()
            for (layer in listOf("background", "foreground", "monochrome")) {
                val drawable = Regex("""<$layer android:drawable="@drawable/([a-z_0-9]+)" */>""")
                    .find(xml)?.groupValues?.get(1)
                assertTrue("$res has no <$layer>", drawable != null)
                assertTrue(
                    "$res: <$layer> points at @drawable/$drawable, which does not exist",
                    walkUpOrNull("src/main/res/drawable/$drawable.xml") != null,
                )
            }
            assertTrue(
                "$res must share the one shipped monochrome: five seasonal silhouettes of the " +
                    "same town would be five identical shapes once a themed icon drops the colour",
                """<monochrome android:drawable="@drawable/ic_launcher_monochrome" />""" in xml,
            )
        }
    }

    @Test
    fun `the seasonal drawings stay inside the safe zone`() {
        // The number in each file's header is measured by the converter on that file's own
        // pathData; this re-measures it here so a hand edit cannot move the drawing without
        // moving the comment. 33 dp is the safe radius, 36 dp the tightest mask.
        val centre = 54.0
        for (icon in SeasonalIcon.entries) {
            if (icon == SeasonalIcon.DEFAULT) continue
            val res = Regex("""android:icon="@mipmap/([a-z_0-9]+)"""").find(aliasBlock(icon))!!.groupValues[1]
            val fg = walkUp("src/main/res/drawable/${res}_foreground.xml").readText()
            var worst = 0.0
            for (m in Regex("""android:pathData="([^"]+)"""").findAll(fg)) {
                var x = 0.0
                var y = 0.0
                for (cmd in Regex("""([MLHVZ])([^MLHVZ]*)""").findAll(m.groupValues[1])) {
                    val n = Regex("""-?\d+(\.\d+)?""").findAll(cmd.groupValues[2]).map { it.value.toDouble() }.toList()
                    when (cmd.groupValues[1]) {
                        "M", "L" -> for (i in n.indices step 2) { x = n[i]; y = n[i + 1]; worst = maxOf(worst, Math.hypot(x - centre, y - centre)) }
                        "H" -> for (v in n) { x = v; worst = maxOf(worst, Math.hypot(x - centre, y - centre)) }
                        "V" -> for (v in n) { y = v; worst = maxOf(worst, Math.hypot(x - centre, y - centre)) }
                    }
                }
            }
            assertTrue("${res}_foreground reaches %.2f dp, past the 33 dp safe radius".format(worst), worst <= 33.0)
            val stated = Regex("""at\s+([0-9.]+) dp against a""").find(fg)?.groupValues?.get(1)?.toDouble()
            assertEquals("${res}_foreground's header disagrees with its own pathData", worst, stated!!, 0.005)
        }
    }

    private fun walkUp(suffix: String): File = walkUpOrNull(suffix) ?: error("could not locate $suffix")

    private fun walkUpOrNull(suffix: String): File? {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + suffix)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }
}
