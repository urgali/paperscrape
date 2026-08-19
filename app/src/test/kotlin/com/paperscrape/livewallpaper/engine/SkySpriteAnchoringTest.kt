package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins each sky sprite's PNG size against the scale convention and the origin its call site
 * passes, because those three numbers are only correct *together* and nothing else checks that.
 *
 * This is the test that was missing. `star_sparkle.png` was a correct 64x64 raw-pixel sprite in
 * v72, drawn at origin `-32` under `canvas.scale(radius / 32)`, spanning exactly the star's own
 * radius. v73's art pass replaced it with a 192x192 redraw -- exactly
 * [SpriteBlitter.SPRITE_PIXELS_PER_UNIT] times larger -- and left the call site alone, so it
 * rendered three times too large and hung off the star's lower right. Nothing failed: a sprite's
 * authoring convention is not recorded in the PNG, so swapping the file silently changed what the
 * unchanged call site meant. `sun_glow.png` was introduced in the same pass with the mirror-image
 * mistake: a raw-pixel sprite given the origin an oversampled one would want.
 *
 * The property asserted is the one both defects broke: **the sprite's own centre must land on the
 * point the caller anchored it to.** For a sprite blitted under `canvas.translate(centre)` with
 * origin `o`, the bitmap covers `bitmapPx / pixelsPerUnit` local units starting at `o`, so its
 * centre sits at `o + bitmapPx / pixelsPerUnit / 2`, and that must be 0.
 *
 * The PNG dimensions are read from the file rather than restated here. Restating them would make
 * this test agree with itself while the asset moved underneath it -- which is exactly the failure
 * being guarded against.
 */
class SkySpriteAnchoringTest {

    /**
     * @param name the drawable, without extension
     * @param scale the convention its call site passes
     * @param origin the origin argument at that call site, in the caller's local units
     * @param nominalRadiusUnits the radius the caller's own `canvas.scale` is expressed against:
     *   120 for the sun and moon (`radius / 120f`), 32 for a star (`star.radius / 32f`)
     */
    private data class SkySprite(
        val name: String,
        val scale: SpriteScale,
        val origin: Float,
        val nominalRadiusUnits: Float,
    )

    /**
     * The scale and origin come from `PaperRenderer`'s own constants, never restated here: the
     * call sites pass those same constants, so a change at either end reaches this test. An
     * earlier version of this file declared the values itself, and a mutation run proved the
     * point -- reverting each call site to its pre-fix argument left every assertion green.
     */
    private val skySprites = listOf(
        SkySprite(
            "sun_body",
            PaperRenderer.CELESTIAL_DISC_SCALE,
            PaperRenderer.CELESTIAL_DISC_ORIGIN_UNITS,
            120f,
        ),
        SkySprite(
            "sun_glow",
            PaperRenderer.SUN_GLOW_SCALE,
            PaperRenderer.SUN_GLOW_ORIGIN_UNITS,
            120f,
        ),
        SkySprite(
            "moon_full",
            PaperRenderer.CELESTIAL_DISC_SCALE,
            PaperRenderer.CELESTIAL_DISC_ORIGIN_UNITS,
            120f,
        ),
        SkySprite(
            "moon_crescent",
            PaperRenderer.CELESTIAL_DISC_SCALE,
            PaperRenderer.CELESTIAL_DISC_ORIGIN_UNITS,
            120f,
        ),
        SkySprite(
            "moon_half",
            PaperRenderer.CELESTIAL_DISC_SCALE,
            PaperRenderer.CELESTIAL_DISC_ORIGIN_UNITS,
            120f,
        ),
        SkySprite(
            "moon_gibbous",
            PaperRenderer.CELESTIAL_DISC_SCALE,
            PaperRenderer.CELESTIAL_DISC_ORIGIN_UNITS,
            120f,
        ),
        SkySprite(
            "star_sparkle",
            PaperRenderer.STAR_SPRITE_SCALE,
            PaperRenderer.STAR_SPRITE_ORIGIN_UNITS,
            32f,
        ),
    )

    private fun pixelsPerUnit(scale: SpriteScale) = when (scale) {
        SpriteScale.SCENE_UNITS -> SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        SpriteScale.CANVAS_PIXELS -> 1f
    }

    /** Local units the bitmap covers once [SpriteBlitter] has applied the convention. */
    private fun sideUnits(sprite: SkySprite) =
        pngSize(sprite.name).first / pixelsPerUnit(sprite.scale)

    // --- The property both D-1 defects broke ---------------------------------------------------

    @Test
    fun `every sky sprite is centred on the point its call site anchors it to`() {
        for (sprite in skySprites) {
            val centre = sprite.origin + sideUnits(sprite) / 2f
            assertEquals(
                "${sprite.name} is anchored ${centre} units off its own centre: a " +
                    "${pngSize(sprite.name).first}px bitmap read as ${sprite.scale} covers " +
                    "${sideUnits(sprite)} units, so the origin has to be " +
                    "${-sideUnits(sprite) / 2f}, not ${sprite.origin}",
                0f, centre, 0.001f,
            )
        }
    }

    /** Square, so one origin argument can serve both axes as every call site assumes. */
    @Test
    fun `every sky sprite is square`() {
        for (sprite in skySprites) {
            val (w, h) = pngSize(sprite.name)
            assertEquals("${sprite.name} is not square", w, h)
        }
    }

    // --- The size each convention implies ------------------------------------------------------

    /**
     * An oversampled sprite's PNG must be a whole multiple of the oversample, or the convention
     * cannot be what the call site claims.
     */
    @Test
    fun `oversampled sky sprites are a whole multiple of the oversample`() {
        for (sprite in skySprites.filter { it.scale == SpriteScale.SCENE_UNITS }) {
            val px = pngSize(sprite.name).first
            assertEquals(
                "${sprite.name} is ${px}px, which is not a multiple of " +
                    "${SpriteBlitter.SPRITE_PIXELS_PER_UNIT}",
                0f, px % SpriteBlitter.SPRITE_PIXELS_PER_UNIT, 0f,
            )
        }
    }

    /**
     * The sun's disc and the moon's phases must come out at exactly the radius the caller scaled
     * for, since the whole celestial geometry -- including the bound that keeps the body on
     * screen -- is expressed against it.
     */
    @Test
    fun `the sun and moon discs come out at exactly the nominal radius`() {
        for (sprite in skySprites.filter { it.name == "sun_body" || it.name.startsWith("moon_") }) {
            assertEquals(
                "${sprite.name} does not span the nominal diameter",
                sprite.nominalRadiusUnits * 2f, sideUnits(sprite), 0.001f,
            )
        }
    }

    /**
     * A star sparkle must fill the star's own radius without exceeding it.
     *
     * This was an equality against the full diameter until the padding normalisation, when the
     * sprite lost the 6px transparent margin per side that made its *canvas* exactly `2 x radius`
     * while its *artwork* only ever reached 0.9375 of that. Equality would now be a claim about
     * padding rather than about the drawing, so the property is stated as the bracket it always
     * really was.
     *
     * The bracket still catches what it exists to catch. The two authoring conventions are a
     * factor of [SpriteBlitter.SPRITE_PIXELS_PER_UNIT] apart, so any window narrower than 3:1
     * admits only one of them: reading this sprite as raw pixels would put it at 90 units against
     * the star's 32, and a sprite a third of the size would fall below the lower bound.
     */
    @Test
    fun `a star sparkle fills the star's radius without exceeding it`() {
        val star = skySprites.first { it.name == "star_sparkle" }
        val reach = sideUnits(star) / 2f
        assertTrue(
            "star_sparkle reaches $reach units, past the star's own ${star.nominalRadiusUnits}",
            reach <= star.nominalRadiusUnits + 0.001f,
        )
        assertTrue(
            "star_sparkle reaches only $reach units of the star's ${star.nominalRadiusUnits}",
            reach > star.nominalRadiusUnits / 2f,
        )
    }

    /**
     * The sunburst has to reach past the disc it rings, or it is drawn every frame and never
     * seen. This is what rules out reading `sun_glow.png` as an oversampled sprite: at that
     * reading it would cover 148 units and sit entirely inside the disc's 120.
     */
    @Test
    fun `the sunburst reaches beyond the disc it rings`() {
        val glow = skySprites.first { it.name == "sun_glow" }
        val disc = skySprites.first { it.name == "sun_body" }
        assertTrue(
            "sun_glow covers ${sideUnits(glow)} units against the disc's ${sideUnits(disc)}",
            sideUnits(glow) / 2f > sideUnits(disc) / 2f,
        )
    }

    // --- The star extents the tile range is derived from ---------------------------------------

    /**
     * `PaperRenderer`'s star extents must be what the sprite actually reaches. They are in canvas
     * pixels and the sprite's own span is in local units scaled by `star.radius / 32`, so the
     * reach is `sideUnits / 2 / 32 * radius`.
     */
    @Test
    fun `the star extents cover what the sprite actually reaches`() {
        val star = skySprites.first { it.name == "star_sparkle" }
        val reach = sideUnits(star) / 2f / 32f * PaperRenderer.MAX_STAR_RADIUS_PX
        assertTrue(
            "left extent ${PaperRenderer.STAR_SPRITE_LEFT_EXTENT_PX} does not cover $reach",
            PaperRenderer.STAR_SPRITE_LEFT_EXTENT_PX >= reach - 0.001f,
        )
        assertTrue(
            "right extent ${PaperRenderer.STAR_SPRITE_RIGHT_EXTENT_PX} does not cover $reach",
            PaperRenderer.STAR_SPRITE_RIGHT_EXTENT_PX >= reach - 0.001f,
        )
        // Over-reserving is safe but must stay proportionate, or the tile range widens for nothing.
        assertTrue(
            "the extents over-reserve by more than a factor of two",
            PaperRenderer.STAR_SPRITE_RIGHT_EXTENT_PX <= reach * 2f,
        )
    }

    // --- Reading the PNG header ----------------------------------------------------------------

    /**
     * Width and height straight out of the PNG's IHDR chunk: bytes 16..19 and 20..23, big-endian,
     * after the 8-byte signature and the chunk's own length and type. No Android or image library
     * is involved, so this runs as a plain JVM unit test.
     */
    private fun pngSize(name: String): Pair<Int, Int> {
        val file = File(drawableDir, "$name.png")
        assertTrue("${file.path} does not exist", file.isFile)
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(24)
            assertEquals("${file.name} is too short to be a PNG", 24, input.read(buffer))
            buffer
        }
        fun intAt(offset: Int) = (0 until 4).fold(0) { acc, i ->
            (acc shl 8) or (header[offset + i].toInt() and 0xFF)
        }
        // 0x89504E47 does not fit in a signed Int, so the literal is a Long and has to be
        // narrowed before it can be compared with the four bytes actually read.
        assertEquals("${file.name} is not a PNG", 0x89504E47.toInt(), intAt(0))
        return intAt(16) to intAt(20)
    }

    private companion object {
        /**
         * Gradle runs unit tests with the module directory as the working directory, but that is
         * a default rather than a guarantee, so walk up until the drawable directory is found
         * instead of assuming a fixed depth.
         */
        val drawableDir: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            throw AssertionError(
                "could not locate src/main/res/drawable-nodpi from ${File(".").absolutePath}",
            )
        }
    }
}
