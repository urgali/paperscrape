package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

/**
 * The two properties the shipped sprite set has to hold as a whole: every canvas sits on the
 * authoring grid, and the set as a whole stays inside its decoded-memory budget.
 *
 * **This replaces the padding check, and the reason is a change of rule rather than a relaxation.**
 * Until the V2 asset set, a sprite's geometry was whatever its opaque pixels happened to occupy,
 * so transparent padding was pure waste -- 17.5 MB of it at the worst point -- and the rule was
 * that every sprite must reach its own canvas edges. The V2 library declares `contentBox` and an
 * anchor rule per sprite instead, and places the drawing inside a canvas sized on the grid, so
 * margin is now load-bearing: `palmtree_fronds` hangs its fan above a declared attachment point,
 * `cloud_body` and `sun_body` are centred in canvases their artwork deliberately does not fill,
 * and cropping any of them would move the sprite rather than save anything. Asserting zero
 * padding against that library would fail 34 sprites for being drawn as designed.
 *
 * What is worth keeping from the old rule is the thing it was really protecting -- that the set
 * cannot quietly grow -- and that is stated directly below as a byte budget rather than inferred
 * from per-sprite margins.
 *
 * The grid check is the other half. `SPRITE_PIXELS_PER_UNIT` is baked into every `SCENE_UNITS`
 * sprite at authoring time, and a canvas that is not a whole multiple of it cannot be divided
 * back down to an integral number of local units, which is how a sprite ends up on a fractional
 * origin and gets resampled by the blit's own `FILTER_BITMAP_FLAG`.
 */
class SpriteGeometryTest {

    /**
     * Decoded ARGB_8888 bytes the whole sprite set may occupy.
     *
     * The V2 set measures 14.43 MB against v75's 15.39 MB, with three more sprites in it. The
     * ceiling is set just above the current figure rather than at a round number, so an asset
     * pass that adds a large sprite has to come here and say so -- which is the point, since the
     * budget is what a memory-pressure policy and a texture atlas are both sized against.
     *
     * **Raised from 16 MB to 26 MB by v4.1's skin-tone batch, and this is the decision the old
     * comment demanded be made here rather than waved through.** Three real skin tones for four
     * characters across two seasons and four sprite slots is 96 variant PNGs, and a variant is
     * the same canvas as its source: the whole set goes from 14.79 MB to 25.67 MB. No choice of
     * tone count fits under the old ceiling -- even two tones would clear it -- so the growth is
     * inherent to shipping real variant artwork rather than recolouring at runtime. The ceiling
     * is set just above the measured figure, as before, so the next asset pass has to come here
     * and say so too.
     *
     * A fourth tone was generated, measured at 29.29 MB, and then dropped -- on how it looked
     * rather than on what it cost. See `PedestrianPopulation.SKIN_TONE_COUNT`.
     *
     * **What this costs.** A live wallpaper runs in a process the system kills freely under
     * pressure, and this doubles the worst case that sizing assumes. It is a worst case rather
     * than a resident figure -- sprites decode on demand, and no frame draws four tones of the
     * same character -- but the budget deliberately measures the ceiling, and the ceiling moved.
     *
     * **The alternative, for whoever revisits this.** Recolouring one flat colour into a cached
     * bitmap at load time would give the same tones with zero growth here, at the cost of a
     * one-off per-pixel pass per tone actually used. It was not taken because the batch that
     * requested this asked for real variant PNGs; it remains the cheaper answer if memory
     * pressure is ever measured to be a problem on real devices.
     *
     * **SCL-01 grew the set inside the ceiling rather than moving it, and says so here because
     * the paragraph above asks the next asset pass to.** Widening the three co-registered person
     * families by one to three units of canvas each -- 132 sprites, to recover winter headwear the
     * old viewBox cut flat -- took the set from 26.76 MB to 27.09 MB decoded. That is 25.84 MiB
     * against the 26 MiB ceiling SCL-01 left 169 kB of; SCL-01 deliberately did not raise it and
     * asked the next pass to make the memory-pressure argument rather than nudge the number.
     *
     * **rc4 raises it to 28.5 MiB, and this is that argument.** The maintainer's criterion is
     * that the vehicle occupants cover every family x season x skin combination the pedestrians
     * have, as real variant PNGs like every other skin tone in the set: 8 frontal busts plus 24
     * recolours on the family's shared 48x44 registration canvas is +2.45 MB decoded, against
     * -0.10 MB for the retired profile family -- the set moves from 27.09 MB to 29.37 MB
     * (28.01 MiB). No coverage that satisfies the criterion fits under 26 MiB: the eight bases
     * alone would land within kilobytes of the ceiling. The cost is the same worst-case ceiling
     * the v4.1 paragraph describes -- sprites decode on demand, no frame decodes four tones of
     * one character, and the rc4 release build was measured as the live wallpaper without a
     * memory regression (the pass report carries the PSS figure). The runtime-recolour
     * alternative above remains the cheaper answer if pressure is ever measured on a real
     * device. The ceiling is set just above the measured figure, as always, so the next pass
     * has to come here and say so too.
     *
     * **v4.20 raises it to 29 MiB, and this is that argument.** It buys the clothing-colour axis
     * that item 5 of `BACKLOG_v4_19.md` has been asking for since v4.18 -- a second outfit for the
     * two adult families, two seasons and three tones, 12 sprites at 74 448 B = **893 376 B**.
     *
     * *First, the space was looked for rather than asked for.* Six sprites came out of the set:
     * `house_window` and the three `road_*` drawings, which no call site has ever blitted (212 328
     * B), and the two boy vehicle bases, verified byte-for-byte identical to their own `_skin2`
     * and regenerable from it with zero differing pixels (148 896 B). That is 361 224 B recovered
     * -- real space, not accounting -- and it takes the set from 28.256 MiB to **27.912 MiB**, so
     * 616 596 B were free under the old ceiling. The axis needs 893 376. It does not fit, and the
     * shortfall is 276 780 B.
     *
     * *Then, and only then, the ceiling.* The set lands at 30 161 196 B = **28.764 MiB**, and 29
     * MiB is the next figure just above it, leaving 247 508 B -- the same "just above the measured
     * figure" every paragraph here has used, for the same reason: the pass after this one has to
     * come here and argue too.
     *
     * *What it costs, measured rather than reasoned about.* The v4.20 pass report carries the PSS
     * of the release build running as the live wallpaper, A/B against v4.19 rebuilt from its own
     * ZIP at the same theme and the same elapsed time. The authorisation for this raise was
     * explicitly conditional on that number not moving beyond the noise, and the item was to be
     * refused outright if it did. The worst case this budget measures is unchanged in kind:
     * sprites decode on demand and no frame decodes both outfits of one family in one tone.
     *
     * *How much of the frame it actually changes.* The garment is a band of 21 canvas px across
     * the shoulders, which is 7 canvas units and so **3.330 local units** at
     * [SceneObjectRenderer.CAR_OCCUPANT_SCALE]. On the reference 1080x2340 device that is
     * **4.14 px in the far lane** (1.2418 px per unit) and **4.78 px in the near one** (1.4360) --
     * above the 3 px legibility floor v4.19 derived for the pillar light, in both lanes, but not
     * by much. Recorded here because "it fits" and "it shows" are different questions and the
     * paragraphs above only answer the first.
     *
     * **v4.28 raises it to 32 MiB, and this is that argument.** It buys the three things the
     * maintainer chose for this release, and only one of them is large: the carrying pose that puts
     * an umbrella in an adult's hand (36 PNGs, 2 families x 2 seasons x 3 frames x 3 tones, at
     * 117x252x4 = **4 245 696 B**), the umbrella's canopy (144x72x4 = **41 472 B**) and the wave
     * WA3 "Tubo" (two 360x132 masks = **380 160 B**). The bird is free: B1 "Rondine" replaces
     * `bird_body` on the same 51x21 canvas.
     *
     * *First, the space was looked for rather than asked for -- and there is none left to find.*
     * The v4.20 paragraph above recovered 361 224 B of sprites nothing blitted, and the interesting
     * part of that pass was that a table existed purely to keep them referenced. That cannot happen
     * again: `SpriteReachabilityTest` now fails any shipped PNG no source file names, in **both**
     * directions, so the hunt v4.20 did by hand is a standing check and the set carries no dead
     * weight to reclaim. The shipped set measures **28 619 568 B** and every byte of it is
     * reachable.
     *
     * *Then, the arithmetic, because a smaller version was looked for too.* The margin under 29 MiB
     * is 30 408 704 - 28 619 568 = **1 789 136 B**, and the three items together need **4 667 328**.
     * Cutting the pose down does not rescue it: one season only is 2 544 480 with the wave and the
     * canopy counted, **over by 755 344**; one tone only is 1 836 864, **over by 47 728** -- it
     * misses by less than half a sprite. The only coverage that fits under the old ceiling is one
     * season *and* one tone at once (1 129 248), which is a summer-only umbrella on a single skin
     * tone in a street of walkers drawn in three -- a regression of exactly the axis v4.1 raised
     * this ceiling to buy in the first place, and a winter shower with everybody bare-headed. The
     * item is whole or it is refused; there is no useful middle.
     *
     * *Then, and only then, the ceiling.* The set lands at **33 286 896 B = 31.745 MiB**, and 32 MiB
     * is the next figure just above it, leaving **267 536 B** -- the same "just above the measured
     * figure" every paragraph here has used, for the same reason: the pass after this one has to
     * come here and argue too.
     *
     * *What it costs, measured rather than reasoned about.* The authorisation for this raise was
     * conditional, in the same words v4.20's was: the A/B memory measurement of the release build
     * running as the live wallpaper, against v4.27 rebuilt from its own ZIP at the same theme and
     * the same elapsed time, must not move beyond the noise, **and the umbrella is refused outright
     * if it does**. The v4.28 pass report carries that number. The worst case this budget measures
     * is unchanged in kind: sprites decode on demand, no frame decodes two tones of one character,
     * and the carrying pose is drawn *instead of* the walking one rather than on top of it -- a
     * pedestrian with an umbrella costs one more decoded frame than the same pedestrian without,
     * not one more figure.
     *
     * *What the A/B does and does not exercise, said here because the condition is easy to
     * over-read.* `SpriteCache` decodes on demand and has **no standing size cap** -- it evicts only
     * on a system trim callback -- so the resident set is whatever the scenes visited have drawn,
     * and the figure this budget pins is a ceiling no single frame reaches. The A/B therefore
     * answers "did the shipped build's real memory move", which is the question v4.20's
     * authorisation asked; it does not exercise the new sprites, because the scene it measures is
     * not raining. The worst case those sprites can add is bounded by the arithmetic above --
     * 4 667 328 B, and only in a scene that is raining over a lake with adults on the pavement.
     *
     * *The cheaper answer, still on the table.* Everything the v4.1 paragraph says about recolouring
     * at load time still applies, and `BACKLOG_v4_28.md` item 80 recorded a second and larger one:
     * `SpriteCache` decodes at the resolution the artwork was drawn at, so this 117x252 pose is
     * decoded at four times the area it is ever blitted at.
     *
     * ---
     *
     * ## v4.29: what this number is, which is not what the paragraphs above assumed
     *
     * **Item 80 is closed and rejected, by measurement.** Redrawing the set on a grid of 2 would
     * *magnify* a third of it: `SpriteDrawScaleTest` measures the smallest headroom in the set at
     * **0.448** and **50 of 305 sprites below the 1.5** that a grid of 2 needs merely to stay at
     * 1:1 -- palms at 1.011, skyscrapers at 1.045, shop fronts at 1.049, trees at 1.115, and every
     * sprite the gallery preview draws. The 3x oversample this ceiling's earlier paragraphs treat
     * as spare has **already been spent**, by the size table and the viewport growing underneath it
     * over many releases. Do not reopen that road without re-reading that test's output.
     *
     * **And the sentence "raising the budget is a decision about memory pressure and atlas sizing"
     * -- which is this test's own failure message -- was half wrong.** This number has nothing to
     * do with atlas sizing, and v4.28 raised it to 32 MiB partly on that reading:
     *
     * - `GlTextureCache` uploads `reduce(bitmap, SpriteDetailLevel.levelFor(scale))`, sized against
     *   the scale the sprite is **drawn** at. Authored size does not reach the GPU.
     * - `SpriteBlitter.onSpriteUploaded` then releases the decoded bitmap, so on the GPU path the
     *   authored-size pixels are a transient, not a resident.
     *
     * So **this is the CPU-side limit and there is now a GPU-side one beside it**:
     * `SpriteDrawScaleTest.uploadedTexelBudget`, which v4.30 moved down to 15 MiB. Its comment
     * carries the argument for splitting them and the table of which limit protects what. Move
     * neither without reading it.
     *
     * **What this one still protects, which is why it was not repointed at texels.** The `Canvas`
     * backend holds the authored bitmap for every frame and must not release it, and it is not a
     * fallback only: `ThemePreview.kt` builds a `CanvasSceneTarget` unconditionally, so the
     * settings and gallery previews decode sprites at authored size on **every** device, GL or not.
     * The wallpaper itself joins them once EGL has failed `GlLifecyclePolicy.MAX_CONTEXT_REBUILDS`
     * times. Beside that this bounds the APK and the per-sprite transient decode peak. Those are
     * real and this is the only thing measuring them.
     *
     * ---
     *
     * ## v4.30: 36 MiB, and it went **up** while the GPU limit went down
     *
     * The set is **36 912 672 B = 35.20 MiB**, and 36 MiB is the next figure just above it, leaving
     * **836 064 B** -- the convention every paragraph above has used, for the reason every one of
     * them gives.
     *
     * *What moved.* A person is no longer shipped once per skin tone. It is shipped as fixed art
     * plus one weight mask per colourable region -- skin, head, shirt, trousers -- and the colour
     * arrives at the blit (`PeopleLayerTable`, `PeopleColours`). That deleted **168** PNGs -- twelve
     * of them the winter window recolours whose four shapes were retired rather than converted --
     * and added **195**: 48 shapes' worth of fixed art and masks, twenty of which turned out to be
     * bytes another shape had already written and are shared. Net **+3 625 776 B**.
     *
     * *Why a masked set costs more here and less on the GPU, which is the whole shape of this
     * release.* A mask is a full canvas in the PNG and a few tenths of one in texels: `GlTextureCache`
     * crops the transparent border **after** the reduction, so the GPU is charged for the ink and
     * this limit is charged for the file. Cropping the PNG instead would have paid both, and it is
     * not available: `SpriteDetailLevel.reduced` truncates, so a crop of a 117-wide canvas reduces
     * on a different grid from the canvas and the two layers drift apart across the sprite --
     * measured at dE 5.6 at the device's own level. So the two limits moved in opposite directions
     * on purpose: **14 594 984 B of texels against 17 921 692 before**, and these bytes up.
     *
     * *Why that is the right trade and not a regression dressed up.* This limit protects the
     * `Canvas` path, the APK and the decode peak; the GPU limit protects what the device actually
     * spends on every frame of the live wallpaper, and v4.29 established that it is the one that
     * binds. And the thing bought is not slack -- it is that **a colour axis no longer multiplies
     * the set**. v4.30 adds three of them. Shipped the old way, hair, shirt and trousers would have
     * been 27 copies of every person: 3 402 000 B of texels and roughly 66 MiB here, which is not a
     * ceiling to raise but a road that ends. The masks cost 3.6 MiB once, and the fourth axis after
     * them costs the same again rather than another multiple.
     *
     * *What was looked for first, as every paragraph here has had to.* The four winter window busts
     * (`BACKLOG_v4_25.md` item 57) were retired rather than converted, which is 1 206 576 B of this
     * number that would otherwise have been spent on shapes no draw path can select. The 34
     * un-suffixed bases stay: `ThemePreviewScene` draws three families' walkers, and
     * `SpriteVariantTest` and `SpriteMeasurementClaimTest` measure them.
     *
     * *And one of the three things this limit says it bounds turns out not to include all of them.*
     * Sixteen of those bases are declared `usage: "orphan"` because no source file names them, and
     * **resource shrinking removes exactly those sixteen from the release APK** -- measured by
     * dumping the release build's own resource table, 320 drawable names against 332 shipped PNGs.
     * So they cost this budget and the `Canvas` path's decode peak, and they cost the APK nothing.
     * Stated because the paragraph above claims all three, and one third of that claim is not true
     * of an orphan.
     * ---
     *
     * ## v5.0: 37 MiB, for the neighbourhood redrawn from scratch
     *
     * The set is **38 089 440 B = 36.32 MiB**, and 37 MiB is the next figure just above it, leaving
     * **707 872 B** -- the convention every paragraph above has used, for the reason every one of
     * them gives (v4.30 left 836 064 B).
     *
     * *What moved.* The five building families (small house, large house, tower, restaurant, bar)
     * are no longer one flat facade each, drawn as wall + roof + trim + door with the same window
     * sticker. The two houses are a STACK of pieces -- ground floor, storeys, roof -- chosen per
     * instance from the building's own stable hash, so two neighbours carry two silhouettes (4
     * deals for the small house, 6 for the large one), and the tower, the restaurant and the bar
     * are one cut-out figure each (the tower with two crowns, the bar with two figures). That
     * replaced **34 PNGs, 3 400 236 B** with **72 PNGs, 4 603 860 B**: net **+1 203 624 B**, every
     * byte of it silhouettes. There is no colour variant in the set -- one wall mask and one glass
     * mask per piece, colour at the blit, as the people have been since v4.30 -- and every tinted
     * surface is the user's own category colour scaled towards ink or white, so the eight editable
     * colours of HOUSES / BUILDINGS are still the whole palette.
     *
     * *What was looked for first, as every paragraph here has had to.* The mix over the old 36 MiB
     * was 340 704 B, and the cheap ways out were measured one by one before this line moved:
     * dropping either bar figure (-405 000 / -447 948), the turret roof of the large house
     * (-361 656), a mansard (-308 736 / -194 184), a tower crown (-71 928 / -37 836), or the common
     * metre -- which the houses already use, so it gives nothing. Each of them removes a
     * silhouette, and silhouettes are the reason the redraw exists: the maintainer's reference is a
     * street where no two buildings share an outline. The maintainer chose to keep all of them and
     * move this line instead.
     *
     * *What it costs, measured, on the three things this limit bounds.* The APK grows by
     * **84 943 B** (the 34 shipped PNGs compress to 33 403 B, the 72 new ones to 118 346 B). The
     * `Canvas` path -- settings and gallery previews on every device, the wallpaper once EGL has
     * failed three times -- holds the authored bitmap of every sprite it has drawn until memory
     * pressure (`SpriteCache`), so its worst case grows by the full **1 203 624 B**; the per-sprite
     * transient decode peak does **not** grow, because the largest new PNG (the tower's first tier,
     * 210x342 px, 287 280 B) is smaller than the largest shipped building PNG (`skyscraper_wall`,
     * 270x450, 486 000 B).
     *
     * *And the one number this does not settle.* A tower is now many more sprite blits per frame
     * than the six the shipped facade cost; that is not a byte and this limit cannot see it. It was
     * measured on the `perf` build before this shipped -- see `NeighbourhoodTable` and the v5.0
     * report for the frame cost, which is the number that decides whether a blit count matters.
     *
     * ### v5.1: the line does not move, and the number under it does
     *
     * The set is **38 104 992 B**, leaving **692 320 B**. It rose by **15 552 B**: the one flower
     * clump became two, `ground_flowers_bloom` and `ground_flowers_dry`, so an autumn or a winter
     * scene stops drawing midsummer blooms. One 108x36 canvas at 4 bytes a pixel, and 2.20 % of
     * the 707 872 B v5.0 left. Nothing was traded for it and nothing needed to be: a second
     * reading of one clump is the cheapest seasonal artwork in the set, and the alternative was a
     * meadow in flower under falling leaves.
     *
     * The GL side of the same change is **not** 15 552 B -- see
     * `SpriteDrawScaleTest.uploadedTexelBudget`, v5.1, for why it is 17 080 and what that says.
     *
     * ### v5.1 again, for the palms, and the line still does not move
     *
     * The set is **38 243 376 B**, leaving **553 936 B**. It rose by a further **138 384 B**: the
     * palm was redrawn from scratch and its four canvases grew with it -- the trunk from 33x174 to
     * 63x174 px because a leaning spindle with a flared foot does not fit in eleven units of
     * width, and each of the three crowns from 120x120 to 168x144 because the blades of a palm
     * fall *below* the point they converge at and the old canvas had six units under it.
     *
     * *What was looked for first, as every paragraph here has to.* Two things, and both were
     * refused on what they cost the picture rather than on what they saved. The dead crown fills
     * only 0.51 of its canvas -- a collapsed crown occupies less than a live one -- and giving it a
     * tighter canvas of its own would recover about 40 KB; it would also give it an origin of its
     * own, which is the arrangement v5.1 deliberately left behind, because three crowns at one
     * declared attachment is what lets the renderer *choose* one instead of stacking a frost
     * overlay on a live crown and requiring the two to cover each other pixel for pixel. And the
     * shipped canvases could have been kept at 120x120 -- no palm that falls below its own
     * convergence fits in them, which was the finding that opened the redraw.
     *
     * *What it costs, on the three things this limit bounds.* The four PNGs compress from 13 578 B
     * to 27 507 B, so the APK grows by **13 929 B**. The `Canvas` path's worst case grows by the
     * full 138 384. The per-sprite transient decode peak does not move: the largest of the four is
     * 96 768 B against a set whose largest is `person_*`'s 117x252.
     *
     * The trunk's own canvas was cut back in the same pass and the number above is after it:
     * `paperscrape-assets normalize` reported 78x174 of removable padding down to 63x174, so the
     * drawing was re-authored two units left on a 21-unit canvas rather than cropped, which is
     * 10 440 B of this budget that never arrived and one fewer pending crop.
     *
     * *And the GL side is a third number again.* **+69 004 B**, not 138 384 and not the 63 988 a
     * host measurement of the content boxes predicted -- see
     * `SpriteDrawScaleTest.uploadedTexelBudget`, v5.1's second paragraph.
     */
    private val decodedByteBudget = 37L * 1024L * 1024L

    @Test
    fun `every shipped sprite is authored on the sprite grid`() {
        val grid = SpriteBlitter.SPRITE_PIXELS_PER_UNIT.toInt()
        val offGrid = mutableListOf<String>()
        for (name in spriteNames()) {
            val (width, height) = pngSize(name)
            if (width % grid != 0 || height % grid != 0) {
                offGrid += "$name (${width}x$height)"
            }
        }
        assertEquals(
            "these sprites are not a whole multiple of $grid px on both axes, so they cannot be " +
                "divided back to an integral number of local units: $offGrid",
            emptyList<String>(), offGrid,
        )
    }

    @Test
    fun `the shipped sprite set stays inside its decoded memory budget`() {
        var total = 0L
        for (name in spriteNames()) {
            val (width, height) = pngSize(name)
            total += width.toLong() * height.toLong() * 4L
        }
        assertTrue(
            "the sprite set decodes to $total bytes, past the $decodedByteBudget budget. Raising " +
                "the budget is a decision about what the `Canvas` path holds resident and about " +
                "the APK, not a test fix -- and it is NOT a decision about GL texture memory or " +
                "atlas sizing, which is `SpriteDrawScaleTest.uploadedTexelBudget`.",
            total <= decodedByteBudget,
        )
    }

    /** No sprite may be so large on its own that it cannot share an atlas page with anything. */
    @Test
    fun `no single sprite dominates the set`() {
        val oversized = mutableListOf<String>()
        for (name in spriteNames()) {
            val (width, height) = pngSize(name)
            val bytes = width.toLong() * height.toLong() * 4L
            if (bytes > decodedByteBudget / 8L) oversized += "$name (${width}x$height, $bytes bytes)"
        }
        assertEquals(
            "these sprites each take more than an eighth of the whole budget: $oversized",
            emptyList<String>(), oversized,
        )
    }

    /**
     * Each co-registered sprite family shares one canvas, and the single anchor that serves the
     * family is that canvas's own height.
     *
     * `tools/assets/paperscrape_assets/normalize.py` defines these three families and refuses a
     * set whose "members must share a canvas", because one origin serves them all. The Kotlin side
     * of that rule is here: `drawPerson`, `drawWindowOccupant` and `drawCarDriver` each blit
     * whichever member the lookup picked through one `*_ANCHOR_Y_UNITS`, and that constant is the
     * canvas height in local units -- it is what puts the sprite's bottom edge on the caller's
     * y=0.
     *
     * Two failures follow from breaking it, and neither is visible in a build:
     *
     * - **A family whose canvases disagree.** The shared anchor is then right for some members and
     *   wrong for the rest, which is exactly what a per-sprite constant would be invented to paper
     *   over -- and `CLAUDE.md` says not to build one.
     * - **A canvas that grew without its anchor.** Every member of that family is drawn one unit
     *   into the ground, or one unit above it. This is the case that went unnoticed: the SCL-01
     *   pass widened all three canvases, and nothing in the suite would have caught leaving an
     *   anchor behind.
     *
     * Read from the shipped PNGs rather than declared here, so the assertion follows the artwork
     * instead of restating it.
     */
    @Test
    fun `each co-registered family shares one canvas, and its anchor is that canvas`() {
        data class Family(val key: String, val anchorUnits: Float, val member: (String) -> Boolean)

        val families = listOf(
            Family("person_walk", -SceneObjectRenderer.PERSON_ANCHOR_Y_UNITS) {
                it.startsWith("person_") && it.contains("_walk")
            },
            Family("person_head_window", SceneObjectRenderer.WINDOW_HEAD_ANCHOR_Y_UNITS) {
                it.contains("_head_window")
            },
            Family("person_head_car", SceneObjectRenderer.HEAD_CAR_ANCHOR_Y_UNITS) {
                it.contains("_head_car")
            },
        )
        val grid = SpriteBlitter.SPRITE_PIXELS_PER_UNIT

        for (family in families) {
            val members = spriteNames().filter(family.member)
            assertTrue("${family.key}: no members found", members.isNotEmpty())

            val canvases = members.map { pngSize(it) }.toSet()
            assertEquals(
                "${family.key}: one origin serves every member, so they must share a canvas -- " +
                    "found ${canvases.sortedBy { it.second }}",
                1, canvases.size,
            )

            val (_, height) = canvases.first()
            assertEquals(
                "${family.key}: the shared anchor is ${family.anchorUnits} units, which is " +
                    "${family.anchorUnits * grid} px, but the family's canvas is $height px tall. " +
                    "A canvas that moves without its anchor moves every member of the family.",
                height.toFloat(), family.anchorUnits * grid, 0.001f,
            )
        }
    }

    private fun spriteNames(): List<String> {
        val names = drawableDir.listFiles { file -> file.name.endsWith(".png") }
            .orEmpty()
            .map { it.name.removeSuffix(".png") }
            .sorted()
        assertTrue("no sprites found in ${drawableDir.path}", names.isNotEmpty())
        return names
    }

    /**
     * Width and height straight out of the PNG's IHDR chunk, the same way
     * `SkySpriteAnchoringTest` reads them: no image library, so this runs as a plain JVM test.
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
        assertEquals("${file.name} is not a PNG", 0x89504E47.toInt(), intAt(0))
        return intAt(16) to intAt(20)
    }

    private companion object {
        /** Same walk-up as `SkySpriteAnchoringTest`: the working directory is a default, not a
         * guarantee. */
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
