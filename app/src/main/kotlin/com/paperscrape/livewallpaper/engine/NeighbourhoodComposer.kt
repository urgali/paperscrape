package com.paperscrape.livewallpaper.engine

/**
 * Deals one building out of [NeighbourhoodTable], for whoever is drawing it.
 *
 * ### Why this is not inside the renderer
 *
 * Two places draw a building: `SceneObjectRenderer`, which draws the wallpaper, and
 * `ThemePreviewScene`, which builds the flat picture the gallery and the settings screen show.
 * Before v5.0 those two carried the buildings' geometry **twice, copied by hand**, and
 * `PreviewRendererAgreementTest` exists because one of the two copies had drifted: a snow cap
 * three units right and two down of where the wallpaper put it.
 *
 * A stack of pieces chosen per instance is a much larger thing to copy by hand than a snow cap,
 * so it is not copied. The table is one source, the stacking arithmetic is this file, and the two
 * callers differ only in what they *are*: the wallpaper deals from the building's own position in
 * the scene, the preview from the position it declares for a picture. Give both the same position
 * and they deal the same silhouette, which is what the agreement test now asserts.
 *
 * ### The deal
 *
 * One alternative per slot and one repeat count, both from the object's own stable identity --
 * never from a random source and never from the frame. Two neighbours differ because their
 * positions differ; the same building is the same building on every frame, in every session, and
 * after a reinstall. That is the property the golden scenes rest on.
 */
internal object NeighbourhoodComposer {

    /** One piece of a dealt building: the piece, the height its foot stands at, and the index its
     *  first window takes in the building's own window numbering (which is what decides who is
     *  standing at it -- see `WindowOccupants`). */
    class Placed {
        var piece: BuildingPiece = EMPTY
        var baseY: Float = 0f
        var firstWindow: Int = 0
    }

    /**
     * A dealt building, owned and reused by its caller.
     *
     * Reused rather than returned fresh because a scene draws every building on every frame: a
     * list per building per frame is garbage at 30 Hz for no gain, and the `Canvas` path -- the
     * one that pays the most per frame anyway -- is the one that would feel it.
     *
     * Each instance belongs to one thread. The renderer's lives on whichever thread owns the
     * scene (the GL render thread, or the main looper on the `Canvas` fallback); the preview's
     * lives on the caller that is building a picture. They are never shared, which is why this is
     * a class the caller holds rather than a scratch buffer on the object.
     */
    class Deal {
        internal var slots = arrayOfNulls<Placed>(INITIAL_CAPACITY)
        var size: Int = 0
            internal set

        /** How many windows the whole building has, which is the denominator every per-window
         *  behaviour (busts, Christmas strings) is spread over. */
        var windowCount: Int = 0
            internal set

        operator fun get(index: Int): Placed = slots[index]!!

        internal fun add(piece: BuildingPiece, baseY: Float, firstWindow: Int) {
            if (size == slots.size) {
                slots = slots.copyOf(slots.size * 2)
            }
            val slot = slots[size] ?: Placed().also { slots[size] = it }
            slot.piece = piece
            slot.baseY = baseY
            slot.firstWindow = firstWindow
            size++
        }
    }

    /**
     * The hash every per-instance choice in this engine is made from.
     *
     * Identical to `SceneCustomization`'s own `stableFraction(StaticSceneObject, Float)` -- the
     * function that decides which objects a density keeps. (Until v5.7F it also decided which of a
     * category's two colours an object wears; that is its own coin now, see
     * `SceneCustomization.variantIndexFor`.) Stated here as well, against the two fields rather
     * than the object, so the preview can ask the same question about a position that is not a
     * scene object at all.
     */
    fun stableFraction(tileFractionX: Float, depthFraction: Float, salt: Float): Float {
        val raw = tileFractionX * 7919f + depthFraction * 7919f * 131f + salt
        return raw - kotlin.math.floor(raw)
    }

    /**
     * Fills [into] with the pieces [family] deals at the identity `(tileFractionX, depthFraction)`.
     *
     * The salts are the slot's index folded into a constant, so adding a slot to a family changes
     * that family's deal and no other's, and the pieces stack bottom-up: a piece's own
     * coordinates are relative to its foot, and the foot climbs by the height of everything
     * already placed.
     */
    fun deal(family: BuildingFamily, tileFractionX: Float, depthFraction: Float, into: Deal) {
        into.size = 0
        var baseY = 0f
        var windows = 0
        val slots = family.slots
        for (index in slots.indices) {
            val slot = slots[index]
            val choice = (stableFraction(tileFractionX, depthFraction, 3.7f * index + 11.3f) * slot.options.size)
                .toInt().coerceIn(0, slot.options.size - 1)
            val piece = slot.options[choice]
            val span = slot.repeatMax - slot.repeatMin + 1
            val repeats = slot.repeatMin +
                (stableFraction(tileFractionX, depthFraction, 5.1f * index + 23.9f) * span)
                    .toInt().coerceIn(0, span - 1)
            for (repeat in 0 until repeats) {
                into.add(piece, baseY, windows)
                windows += piece.windows.size
                baseY -= piece.height
            }
        }
        into.windowCount = windows
    }

    /**
     * Fills [into] with the pieces [family] deals for [spec].
     *
     * The wallpaper's entry point, and the only one that can read a **dealt** silhouette: a slot
     * the generator dealt takes the entry [SilhouetteDeal] recorded on it, and a slot nobody dealt
     * falls back to the position hash above. Both paths stack the pieces with the same arithmetic,
     * which is the property `PreviewRendererAgreementTest` rests on -- what changed in v5.5 is
     * which silhouette a slot is asked for, never how one is built.
     *
     * Allocation-free on the draw path: the catalogue is enumerated once at class init and this
     * indexes it.
     */
    fun deal(family: BuildingFamily, spec: StaticSceneObject, into: Deal) {
        val catalogue = SilhouetteDeal.catalogueFor(spec)
        val silhouette = if (catalogue == null) null else catalogue.getOrNull(spec.silhouette)
        if (silhouette == null) {
            deal(family, spec.tileFractionX, spec.depthFraction, into)
        } else {
            deal(family, silhouette, into)
        }
    }

    /**
     * Fills [into] with the pieces of one named [silhouette].
     *
     * The stacking is [deal]'s own, with the two hashes replaced by the silhouette's two arrays --
     * so a dealt building and a hashed one at the same silhouette are the same building, piece for
     * piece and window index for window index.
     */
    fun deal(family: BuildingFamily, silhouette: SilhouetteDeal.Silhouette, into: Deal) {
        into.size = 0
        var baseY = 0f
        var windows = 0
        val slots = family.slots
        for (index in slots.indices) {
            val piece = slots[index].options[silhouette.choices[index]]
            for (repeat in 0 until silhouette.repeats[index]) {
                into.add(piece, baseY, windows)
                windows += piece.windows.size
                baseY -= piece.height
            }
        }
        into.windowCount = windows
    }

    /** Four pieces covers the tallest family (ground + two storeys + roof); the rest grow. */
    private const val INITIAL_CAPACITY = 4

    private val EMPTY = BuildingPiece(0f, emptyList(), emptyList(), emptyList(), 0f, 0f, 0f, 0f)
}
