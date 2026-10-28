package com.paperscrape.livewallpaper.engine

/**
 * Where a building's Halloween decorations hang (`DESIGN_NOTES.md` §12, decisions 83 and 85),
 * **one sequence for the wallpaper and the gallery card**, as [ChristmasDecorations] is for the Christmas ones:
 * `SceneObjectRenderer.drawHalloweenDecorations` draws what this yields at the scene's moment -- the jack-o'-lantern lit by
 * night -- and `ThemePreviewScenes` lays the same points out on the card's buildings, still, lit by the card's night. Every
 * place is one the buildings' generator wrote into `NeighbourhoodTable.kt`, in the building's own piece units from its feet,
 * so none is a number typed in the renderer:
 *
 * - **one cobweb**, at one end of the top piece's roofline ([BuildingPiece.roofX]): the drawing's own at the left end,
 *   mirrored at the right, the end the drawing chooses ([cobwebCorner]); in a colour the walls choose, turning from the
 *   one to the other over some ten minutes of the dusk and of the dawn ([webTintAt]);
 * - a jack-o'-lantern on the ground by every front door ([BuildingDoor]), its middle [DOOR_ASIDE_UNITS] to the door's left.
 *
 * **Only on the houses and the three shops** ([decorates], decision 83), lit whether a shop is open or shut. Each
 * function that takes a block is `inline`, so the draw path allocates nothing for it.
 */
internal object HalloweenDecorations {

    /** How far to the left of a front door's left edge the jack-o'-lantern stands, in piece units. */
    const val DOOR_ASIDE_UNITS = 7f

    /**
     * The cobweb's colour on a dark wall: the white mask tinted to the generator's light web
     * (`build_halloween_deco.WEB_LIGHT`).
     */
    const val WEB_LIGHT = 0xFFE8E6EE.toInt()

    /**
     * The cobweb's colour on a light wall: the dark violet `#56516A` multiplied over [WEB_LIGHT] channel by channel,
     * `round(a x b / 255)` -- 0x56 x 0xE8 = 78, 0x51 x 0xE6 = 73, 0x6A x 0xEE = 99 -- (`build_halloween_deco.WEB_DARK`).
     */
    const val WEB_DARK = 0xFF4E4963.toInt()

    /**
     * Above this relative luminance of its wall a building's cobweb is [WEB_DARK]: the light web would not show on the cream
     * walls by day (`DESIGN_NOTES.md` §12, decision 85).
     * The colour at rest -- by day and at night --; across the dusk and the dawn the web turns from the one to
     * the other between [WEB_FADE_FROM_LUMINANCE] and [WEB_FADE_TO_LUMINANCE] ([webTintAt]).
     */
    const val WEB_DARK_ABOVE_LUMINANCE = 0.45f

    /**
     * **The band the cobweb turns over** (`DESIGN_NOTES.md` §12, decision 85): at or below this
     * luminance of its wall the web is [WEB_LIGHT], at or above [WEB_FADE_TO_LUMINANCE] [WEB_DARK], and in between the one
     * blended into the other by how far the wall is across the band, so the web does not jump from the one to the other
     * at one frame of the dusk as a wall crosses [WEB_DARK_ABOVE_LUMINANCE]. Every built-in theme's walls cross the band in
     * 8.7 to 12.9 minutes, from 3 to 5 minutes before they cross [WEB_DARK_ABOVE_LUMINANCE] to 5 to 8 after -- with a sunset
     * at 20:00 between 78 and 17 minutes before it, and as long after a sunrise at 6:00 --; by day they stand at 0.549 or
     * more and at night at 0.119 or less, so by day and at night every web is the threshold's colour
     * (`HalloweenDecorationsTest`).
     */
    const val WEB_FADE_FROM_LUMINANCE = 0.43f

    /** The top of the band the cobweb turns over: see [WEB_FADE_FROM_LUMINANCE]. */
    const val WEB_FADE_TO_LUMINANCE = 0.47f

    /** Whether a building wears them: a house, or one of the three shops among the houses. */
    fun decorates(kind: WindowBuildingKind, storefront: Storefront?): Boolean =
        kind == WindowBuildingKind.HOUSE ||
            storefront == Storefront.RESTAURANT || storefront == Storefront.BAR || storefront == Storefront.SHOP

    /**
     * The cobweb's tint on a wall of [wallColor] (ARGB) at rest -- the colour the walls are drawn in by day or at night --:
     * [WEB_DARK] on a wall lighter than [WEB_DARK_ABOVE_LUMINANCE], [WEB_LIGHT] otherwise. Across the dusk and the dawn the
     * web turns from the one to the other ([webTintAt]). Pure arithmetic: no object made.
     */
    fun webTintOn(wallColor: Int): Int = if (relativeLuminance(wallColor) > WEB_DARK_ABOVE_LUMINANCE) WEB_DARK else WEB_LIGHT

    /**
     * **The cobweb's tint at [dayBlend] on a wall whose colours by day and at night are [dayWall] and [nightWall]** -- the
     * frame's web --: [webTintFor] of the luminance of the wall at that moment, its colour worked
     * out as the walls' own blend of the two (`SceneCustomization`'s, [SceneColour.blendArgb]) but kept in fractions of a
     * level, so the web follows the dusk smoothly and not by the steps of a wall's 8-bit colour, which move the luminance
     * by up to about 0.004 at once (a level of green) and the web by some 15 levels. By day and at night the wall is [dayWall] and [nightWall]
     * exactly. Pure arithmetic: no object made.
     */
    fun webTintAt(dayWall: Int, nightWall: Int, dayBlend: Float): Int {
        val b = dayBlend.coerceIn(0f, 1f)
        val l = relativeLuminance(
            channelAt(nightWall, dayWall, 16, b), channelAt(nightWall, dayWall, 8, b), channelAt(nightWall, dayWall, 0, b),
        )
        return webTintFor(l, dayWall, nightWall)
    }

    /**
     * The cobweb's tint on a wall of luminance [wallLuminance] whose colours by day and at night are [dayWall] and
     * [nightWall]: the threshold's web ([webTintOn]), **except across the band** from [WEB_FADE_FROM_LUMINANCE] to
     * [WEB_FADE_TO_LUMINANCE], where the light web is blended into the dark one by how far the wall is across it, opaque.
     *
     * **By day and at night the web is the threshold's, on every wall**: the band is narrowed to keep out the wall's own luminance by day and at night -- a wall that stands inside it at rest,
     * a grey of a theme of the user's, would otherwise wear a web between the two, harder to see on it than either --, which
     * leaves the built-in themes' walls, all outside it, the whole band. The luminances of the two ends are worked out
     * inside the band only.
     */
    fun webTintFor(wallLuminance: Float, dayWall: Int, nightWall: Int): Int {
        if (wallLuminance <= WEB_FADE_FROM_LUMINANCE) return WEB_LIGHT
        if (wallLuminance >= WEB_FADE_TO_LUMINANCE) return WEB_DARK
        var from = WEB_FADE_FROM_LUMINANCE
        var to = WEB_FADE_TO_LUMINANCE
        val day = relativeLuminance(dayWall)
        val night = relativeLuminance(nightWall)
        if (day > WEB_DARK_ABOVE_LUMINANCE) to = minOf(to, day) else from = maxOf(from, day)
        if (night > WEB_DARK_ABOVE_LUMINANCE) to = minOf(to, night) else from = maxOf(from, night)
        if (wallLuminance <= from) return WEB_LIGHT
        if (wallLuminance >= to) return WEB_DARK
        // [SceneColour.blendArgb] blends the alpha too, and 255 x (1 - t) + 255 x t can come out 254.99: kept opaque.
        return SceneColour.blendArgb(WEB_LIGHT, WEB_DARK, (wallLuminance - from) / (to - from)) or (0xFF shl 24)
    }

    /** The relative luminance of [color]'s sRGB channels, linearised: 0.2126 R + 0.7152 G + 0.0722 B, 0..1. */
    fun relativeLuminance(color: Int): Float =
        relativeLuminance(((color shr 16) and 0xFF).toFloat(), ((color shr 8) and 0xFF).toFloat(), (color and 0xFF).toFloat())

    /** The relative luminance of sRGB channels [r], [g], [b] given in levels, 0..255, fractions of a level allowed. */
    fun relativeLuminance(r: Float, g: Float, b: Float): Float = 0.2126f * linear(r) + 0.7152f * linear(g) + 0.0722f * linear(b)

    /** Channel [shift] of the blend of [night] into [day] at [dayBlend], [SceneColour.blendArgb]'s arithmetic before it is cut to a level. */
    private fun channelAt(night: Int, day: Int, shift: Int, dayBlend: Float): Float =
        (night shr shift and 0xFF) * (1f - dayBlend) + (day shr shift and 0xFF) * dayBlend

    private fun linear(channel: Float): Float {
        val x = channel / 255f
        return if (x <= 0.04045f) x / 12.92f else Math.pow(((x + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

    /**
     * **Which end of the roof the cobweb hangs at: the top piece's drawing chooses**
     * (`DESIGN_NOTES.md` §12, decision 85). A rule written on the drawing, as every decoration's place is: a hash of
     * the points of the top piece's roofline, as the generator measured them ([BuildingPiece.roofX]), and of how many pieces
     * the building stands in -- the same building hangs it at the same end on the wallpaper and on the card, every time; a
     * redraw of the piece may move it. Bit 0: 0 the left end, 1 the right. Over the buildings the twelve built-in streets deal
     * it comes out 92 left, 64 right (`HalloweenDecorationsTest`), kept as it is (decision 85, `DESIGN_NOTES.md` §5).
     */
    fun cobwebCorner(deal: NeighbourhoodComposer.Deal): Int {
        val xs = deal[deal.size - 1].piece.roofX
        var h = xs.size * 31 + deal.size * 7
        for (i in xs.indices) h = h * 17 + (xs[i] * 8f).toInt()
        return (h xor (h ushr 7)) and 1
    }

    /**
     * The one cobweb: [action] gets its corner of the roof and +1 for the drawing as it is (the left end), -1 mirrored
     * (the right end). None on a building with no roofline.
     */
    inline fun forEachCobweb(deal: NeighbourhoodComposer.Deal, action: (x: Float, y: Float, mirror: Float) -> Unit) {
        if (deal.size == 0) return
        val top = deal[deal.size - 1]
        val xs = top.piece.roofX
        val ys = top.piece.roofY
        if (xs.size < 2) return
        val last = xs.size - 1
        if (cobwebCorner(deal) == 0) action(xs[0], top.baseY + ys[0], 1f) else action(xs[last], top.baseY + ys[last], -1f)
    }

    /** The jack-o'-lantern by every front door: [action] gets the bottom middle it stands on. */
    inline fun forEachDoorPumpkin(deal: NeighbourhoodComposer.Deal, action: (x: Float, y: Float) -> Unit) {
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val door = placed.piece.door ?: continue
            action(door.x - DOOR_ASIDE_UNITS, placed.baseY + door.y + door.h)
        }
    }
}
