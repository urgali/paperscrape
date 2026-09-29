package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The seated artwork looks toward +x of its canvas, which is why the renderer mirrors it** (v5.9B,
 * inventory row I-42).
 *
 * `SceneObjectRenderer.drawSeatedOccupant` blits every bust with `scale(-scale, scale)` because the
 * artwork is drawn three-quarter with the hair toward -x -- so the face looks toward +x, which in a
 * vehicle's local frame is its rear (`BACKLOG_v4_25.md` item 62). That mirror is only right while
 * the drawing faces this way. This reads the premise off the PNGs, on the JVM and in seconds; the
 * behaviour itself -- every occupant looking the way the vehicle travels -- is asserted on the
 * device by `OccupantFacingTest`, which reverses red if the mirror is taken out.
 *
 * Measured on the two adults' summer heads, whose hair is the one unambiguous signal: the head
 * region's ink sits 11 px (man) and 20 px (woman) behind the eye axis at the time of writing. The
 * children's caps and the winter hats sit within 2 px of the axis and say nothing either way.
 */
class SeatedArtworkFacingTest {

    @Test
    fun `the adult seated heads carry their hair behind the eye axis`() {
        val eyeAxisPx = SceneObjectRenderer.HEAD_CAR_ANCHOR_X_UNITS * SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        for (name in listOf("person_man_summer_head_car_mh", "person_woman_summer_head_car_mh")) {
            val image = ImageIO.read(File(drawableDir(), "$name.png"))
            var weight = 0.0
            var sum = 0.0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val a = (image.getRGB(x, y) ushr 24).toDouble()
                weight += a; sum += a * x
            }
            val hairCentre = sum / weight
            assertTrue(
                "$name: the hair's centre is at $hairCentre px and the eye axis at $eyeAxisPx. If the " +
                    "seated family has been redrawn facing -x, drawSeatedOccupant's mirror now turns " +
                    "every occupant to the boot -- take the mirror out, and OccupantFacingTest will say so",
                hairCentre < eyeAxisPx - 5.0,
            )
        }
    }

    private fun drawableDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + "src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi")
    }
}
