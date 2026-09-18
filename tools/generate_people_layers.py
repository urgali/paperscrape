#!/usr/bin/env python3
"""Writes the people as **fixed art plus four region weight masks** (v4.30).

Run from the repo root with the pinned venv:

    /home/bober/.venvs/paperscrape-assets/bin/python tools/generate_people_layers.py

For every shape it writes, into ``app/src/main/res/drawable-nodpi``:

  ``<shape>_fx.png``   the fixed art: every ink that does not follow one of the four colours,
                       **plus the dark term of every ink that does** -- a shadow is
                       ``(1-t)*paint + t*DARK`` and the ``t*DARK`` half does not move when the
                       paint does, so it is fixed art exactly like the shoes;
  ``<shape>_ms.png``   the weight of the **skin** region;
  ``<shape>_mh.png``   the weight of the **head** region -- hair, or whatever is worn instead;
  ``<shape>_mt.png``   the weight of the **shirt/coat** region;
  ``<shape>_mb.png``   the weight of the **trouser** region.

The engine recomposes ``fixed + sum over regions of (mask * colour)``, and
``PeopleLayerAssetTest`` checks that recomposing at the shapes' own painted colours reproduces the
drawing.

## Why a weight, and why it is added rather than laid over

A weight interpolates and a region index does not. The sprite is halved on the CPU before upload
and sampled bilinearly by the GPU, so it is averaged twice before it reaches the screen; halfway
between "skin" and "shirt" is not a region, but halfway between two weights is a weight. Measured
on the device's own draw path, a region index is out by dE 5-8 on 42% of the pixels and a weight by
at most 1.5 with none over 2 (`indagine_strati/REPORT.md` §2.1-2.3).

And the mask is **summed**, not laid over. Two source-over layers split the pixel's coverage
between them, and ``a + b(1-a)`` is not linear -- so once the engine halves each layer separately
they no longer recompose, and what is left is a halo at the edge, measured at up to 63 levels of
coverage out of 255. A sum is linear: halving and compositing commute, and the edge is exact by
construction.

## Where a region comes from, and why it is not a colour

By **piece**. The generator drew the figure and knows which polygon is hair, so
``build_people_concepts.region_of`` maps the piece name -- already written into every ``cut`` call
as its wobble seed -- onto a region. Asking instead "which pixels are hair-coloured" is what does
not work: the man's hair, his shoes, his eyes and his ground shadow are all ``#2B2A33``, and 868 of
the 3 167 pixels of that colour on ``person_man_summer_walk0`` are not hair.

The mask itself is rendered by the *same* drawing code through
``build_people_concepts.emit_region_svg``, with each piece painted its own weight in grey instead
of its paint. The geometry, the z-order and the under-papers are the same objects, so the render's
own anti-aliasing resolves a pixel shared between two pieces exactly the way the shipped render
resolves it -- and the alpha channel that comes back is the figure's, pixel for pixel.

## The file format, and the single rounding in it

A mask is **white with the weight in its alpha**: ``alpha = round(255 * W)``, ``rgb = 255``.
``BitmapFactory`` premultiplies on load, so what the texture carries is ``rgb * alpha / 255 = W``
-- the product the engine multiplies the region's colour by -- through **one** quantisation
instead of two. Splitting the weight between a grey and a coverage, as the investigation's
prototype did, rounds twice for the same result.
"""
from __future__ import annotations

import io
import sys
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parent.parent
TOOL_ROOT = REPO / "tools" / "assets"
PEOPLE = TOOL_ROOT / "concepts" / "people"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
sys.path.insert(0, str(TOOL_ROOT))
sys.path.insert(0, str(PEOPLE))

import build_people_concepts as bpc  # noqa: E402
import build_carry_sprites as carry  # noqa: E402
from paperscrape_assets import raster  # noqa: E402

STYLE = "rilievo_occhi"

#: Suffix per region, in the order the engine's own table lists them.
SUFFIX = {
    bpc.REGION_SKIN: "ms",
    bpc.REGION_HEAD: "mh",
    bpc.REGION_TOP: "mt",
    bpc.REGION_BOTTOM: "mb",
}

#: Shapes that are drawn by no path and are therefore not converted (`BACKLOG_v4_25.md` item 57).
#:
#: Indoors the season index is 0 whatever the theme does -- *the hat belongs to the street, not to
#: the room behind the pane* -- so a winter window bust cannot be selected. The four shapes are
#: retired here rather than converted; their un-suffixed bases stay, because they are what the
#: generator draws from and what `SpriteVariantTest` measures.
RETIRED = {f"person_{kind}_winter_head_window" for kind in bpc.KINDS}


def shape_parts(name: str) -> tuple[str, str]:
    """(family, season) of a shape name."""
    bits = name.split("_")
    return bits[1], bits[2]


def all_sprites() -> list:
    """Every shipped person shape, drawn by the generators that draw the shipped art.

    The walkers and the two busts come from ``build_people_concepts`` with its own canvas trim
    applied, and the carrying pose from ``build_carry_sprites`` with the shipped trim -- the same
    two paths that produced the PNGs in ``res``, so nothing here is a second drawing of the same
    figure.

    **All four families carry since v5.4H.** ``bpc.KINDS`` rather than the two adults: the children
    have a pose now, and writing the loop over the table's own family list is what keeps this from
    being the place that remembers "adults only" after the artwork stopped agreeing.
    """
    sprites = list(bpc.family(STYLE))
    bpc.trim_to_content(sprites, STYLE)
    for kind in bpc.KINDS:
        for season in ("summer", "winter"):
            for frame in range(3):
                s, _ = carry.walker(kind, season, frame, carrying=True)
                sprites.append(bpc.faced(carry.trimmed(s), STYLE))
    return sprites


def render_rgba(svg: str) -> np.ndarray:
    """The rasteriser the shipped art goes through, nothing else."""
    return np.array(Image.open(io.BytesIO(raster.render_svg(svg).png_bytes)).convert("RGBA"))


def build(write: bool = True) -> dict:
    if not RES.is_dir():
        sys.exit(f"{RES} not found -- run from the repo root")
    report: dict = {"shapes": 0, "files": 0, "worst": 0.0, "empty": [], "per_shape": {}}
    #: Content hash -> the name it was first written under, and the aliases that result.
    seen: dict = {}
    alias: dict = {}
    for sprite in all_sprites():
        if sprite.name in RETIRED:
            continue
        kind, season = shape_parts(sprite.name)
        bases = bpc.region_bases(kind, season)
        art = render_rgba(bpc.emit_svg(sprite, STYLE)).astype(np.float64)
        alpha = art[:, :, 3]
        premul = art[:, :, :3] * (alpha / 255.0)[:, :, None]

        weights = {}
        for region in bpc.REGIONS:
            if bases[region] is None:
                continue
            grey = render_rgba(bpc.emit_region_svg(sprite, STYLE, region, bases)).astype(np.float64)
            # The render's grey is straight; premultiplying by the figure's own coverage is what
            # turns "how much of this ink follows the region" into "how much of this pixel does".
            w = grey[:, :, 0] * (grey[:, :, 3] / 255.0) / 255.0
            # **Floor, not round, and that is the whole reason the fixed layer can never go
            # negative.** The drawing is `sum(W_r * B_r) + F` with `F` a coverage times a colour
            # and therefore non-negative, so taking each weight *down* to the nearest level leaves
            # the fixed layer a sum of non-negative terms by construction -- where rounding to
            # nearest can overshoot by half a level per region and drive it below zero at an
            # anti-aliased edge, which is then clipped and shows. The cost is that a region is at
            # most one level light, and even that is invisible at the painted colours because the
            # fixed layer absorbs exactly the residue; it only appears as up to one level when the
            # colour is changed.
            quantised = np.clip(np.floor(w * 255.0), 0, 255)
            if quantised.max() == 0:
                report["empty"].append(f"{sprite.name}:{region}")
                continue
            weights[region] = quantised

        # The fixed layer is what is left once each region's share is taken out, and it is taken
        # out at the **quantised** weight the engine will really multiply by -- not at the exact
        # one -- so that recomposing at the painted colours closes to the rounding of a single
        # channel rather than accumulating four.
        fixed = premul.copy()
        for region, quantised in weights.items():
            base = np.array(bpc._rgb(bases[region]), dtype=np.float64)
            fixed -= (quantised / 255.0)[:, :, None] * base[None, None, :]
        # By construction the fixed layer is a sub-sum of coverage x colour, so it should be
        # between zero and the coverage. It is allowed two levels of slack in each direction and
        # no more, because the rasteriser composites in 8-bit premultiplied and hands back a fully
        # covered patch of the man's skin as 219 where his paint is 220 -- a rounding of the
        # drawing, measured at 61 pixels and 1.2 levels on `person_man_summer_walk0`. A piece
        # tagged into the wrong region does not miss by one level; it misses by tens, which is
        # what this catches.
        if fixed.min() < -2.0 or (fixed - alpha[:, :, None]).max() > 2.0:
            sys.exit(f"{sprite.name}: fixed layer out of range [{fixed.min():.2f}, "
                     f"{(fixed - alpha[:, :, None]).max():.2f}] -- a piece is tagged into the "
                     f"wrong region")
        safe = np.where(alpha > 0, alpha, 255.0)
        straight = np.clip(np.round(fixed / (safe / 255.0)[:, :, None]), 0, 255)
        out = np.zeros_like(art, dtype=np.uint8)
        out[:, :, :3] = straight.astype(np.uint8)
        out[:, :, 3] = alpha.astype(np.uint8)

        # What the engine will actually put on screen at the painted colours, through the same
        # rounding the PNGs impose, against the drawing itself.
        back = out[:, :, :3].astype(np.float64) * (alpha / 255.0)[:, :, None]
        for region, quantised in weights.items():
            base = np.array(bpc._rgb(bases[region]), dtype=np.float64)
            back += (quantised / 255.0)[:, :, None] * base[None, None, :]
        worst = float(np.abs(back - premul).max())
        report["worst"] = max(report["worst"], worst)
        report["per_shape"][sprite.name] = {
            "regions": sorted(weights), "worst_channel": round(worst, 3),
        }
        if worst > 2.01:
            sys.exit(f"{sprite.name}: recomposition is out by {worst:.2f} levels")

        if write:
            layers = [("fx", out)]
            for region, quantised in weights.items():
                mask = np.zeros_like(art, dtype=np.uint8)
                mask[:, :, :3] = 255
                mask[:, :, 3] = quantised.astype(np.uint8)
                layers.append((SUFFIX[region], mask))
            for suffix, pixels in layers:
                name = f"{sprite.name}_{suffix}"
                # **A layer that is byte for byte a layer already written is not written again.**
                # The carrying pose is the walking pose with one arm redrawn, so its head mask and
                # its trouser mask are the walker's -- twelve files that would otherwise ship twice
                # and upload twice. This is the same move `personWalkDrawables` made when it
                # pointed frame 3 at `walk1`, and `SpriteVariantTest` is what would have caught it
                # had it not been made: two shipped sprites may not be the same bytes.
                key = pixels.tobytes()
                twin = seen.get(key)
                if twin is not None:
                    alias[name] = twin
                    continue
                seen[key] = name
                Image.fromarray(pixels, "RGBA").save(RES / f"{name}.png", optimize=True)
                report["files"] += 1
        report["shapes"] += 1
    report["alias"] = alias
    return report


TABLE = REPO / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / \
    "engine" / "PeopleLayerTable.kt"

#: The order the engine reads a shape's layers in: fixed art first, then one slot per region.
SLOTS = ("fx",) + tuple(SUFFIX[r] for r in bpc.REGIONS)


def _slots(name: str, present: dict, alias: dict) -> str:
    ids = []
    for slot in SLOTS:
        have = slot == "fx" or slot in present
        if not have:
            ids.append("0")
            continue
        drawable = alias.get(f"{name}_{slot}", f"{name}_{slot}")
        ids.append(f"R.drawable.{drawable}")
    return "intArrayOf(" + ", ".join(ids) + ")"


def write_table(report: dict) -> None:
    """Writes the engine's lookup table from the artwork that was just produced.

    Generated rather than committed by hand for the same reason the artwork is: a shape that gains
    or loses a region -- a summer dress has no trousers, a hood is a coat and not a hat -- would
    otherwise have to be remembered in two places, and the second one is the one that rots.
    """
    have = {name: set(info["regions"]) for name, info in report["per_shape"].items()}
    present = {n: {SUFFIX[r] for r in rs} for n, rs in have.items()}
    alias = report["alias"]

    def walk_rows() -> str:
        out = []
        for kind in bpc.KINDS:
            seasons = []
            for season in ("summer", "winter"):
                # Frame 3 names walk1 deliberately: a four-frame cycle of two poses, and the
                # passing pose serves both passes. See `SceneObjectRenderer.personWalkDrawables`.
                frames = [f"person_{kind}_{season}_walk{f}" for f in (0, 1, 2, 1)]
                seasons.append("arrayOf(\n" + "".join(
                    f"                {_slots(f, present[f], alias)},\n" for f in frames) + "            )")
            out.append("        arrayOf(\n            " + ",\n            ".join(seasons) + ",\n        ),")
        return "\n".join(out)

    def carry_rows() -> str:
        out = []
        for kind in bpc.KINDS:
            seasons = []
            for season in ("summer", "winter"):
                frames = [f"person_{kind}_{season}_carry{f}" for f in (0, 1, 2, 1)]
                seasons.append("arrayOf(\n" + "".join(
                    f"                {_slots(f, present[f], alias)},\n" for f in frames) + "            )")
            out.append("        arrayOf(\n            " + ",\n            ".join(seasons) + ",\n        ),")
        return "\n".join(out)

    def window_rows() -> str:
        return "\n".join(
            f"        {_slots(n, present[n], alias)},"
            for n in (f"person_{k}_summer_head_window" for k in bpc.KINDS))

    def car_rows() -> str:
        out = []
        for kind in bpc.KINDS:
            names = [f"person_{kind}_{s}_head_car" for s in ("summer", "winter")]
            out.append("        arrayOf(\n" + "".join(
                f"            {_slots(n, present[n], alias)},\n" for n in names) + "        ),")
        return "\n".join(out)

    def worn_rows() -> str:
        out = []
        for kind in bpc.KINDS:
            flags = []
            for season in ("summer", "winter"):
                gar = (bpc.SUMMER if season == "summer" else bpc.WINTER)[kind]
                flags.append("true" if (gar.get("cap") if season == "summer" else gar.get("hat")) else "false")
            out.append(f"        booleanArrayOf({flags[0]}, {flags[1]}),   // {kind}")
        return "\n".join(out)

    TABLE.write_text(f'''package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * Which drawables make up each person, now that a person is **drawn rather than shipped in every
 * colour** (v4.30).
 *
 * A shape is `[fixed, skin, head, top, bottom]`: the fixed art, then one weight mask per
 * colourable region, `0` where the shape has no such region. The engine draws the fixed layer and
 * then adds each mask multiplied by the colour that region is wearing this crossing --
 * `SceneObjectRenderer.drawPersonLayers`.
 *
 * **Generated by `tools/generate_people_layers.py`, which is also what writes the artwork.** Not
 * committed by hand: a shape that gains or loses a region -- a summer dress has no trousers, a
 * hood is part of a coat and not a hat -- would otherwise have to be remembered in two places, and
 * the second one is the one that goes stale. Regenerate both together or neither.
 */
internal object PeopleLayerTable {{

    /** Slot of the fixed art in a shape's array. */
    const val FIXED = 0

    /** Slots of the four masks, in the order `PeopleColours` resolves their colours. */
    const val SKIN = 1
    const val HEAD = 2
    const val TOP = 3
    const val BOTTOM = 4

    /** How many drawables a shape carries: the fixed layer plus one slot per region. */
    const val SLOTS = 5

    /** The walkers: `[kind][season][frame]`. */
    val WALK = arrayOf(
{walk_rows()}
    )

    /**
     * The carrying pose: `[kind][season][frame]`, **all four families since v5.4H**.
     *
     * It was two families, and `PedestrianCarry.canHold` is written to read this array's own length
     * rather than a copy of the number -- so the children started carrying the moment the artwork
     * landed here, with no second edit and no rule to change. That was the arrangement v5.4E left
     * behind on purpose.
     */
    val CARRY = arrayOf(
{carry_rows()}
    )

    /**
     * The window busts: `[kind]`, **summer only**.
     *
     * There is no season axis any more, and its absence is the closure of `BACKLOG_v4_25.md` item
     * 57. Indoors the season index was always 0 -- *the hat belongs to the street, not to the room
     * behind the pane* -- so the winter column named twelve recolours no draw path could reach.
     * They are retired, and so is the column that pretended otherwise.
     */
    val WINDOW = arrayOf(
{window_rows()}
    )

    /** The seated busts: `[kind][season]`. */
    val CAR = arrayOf(
{car_rows()}
    )

    /**
     * Whether this family wears something on its head in this season instead of showing hair:
     * `[kind][season]`.
     *
     * It decides which palette the head region draws from -- hair colours for a bare head, cap
     * colours for a covered one -- and it is a fact about the drawing, so it is written here by
     * the generator that drew it rather than restated in the engine.
     *
     * The winter boy reads `true` and has no head mask at all: what he wears is his own coat's
     * hood, which is drawn in the coat's paint and belongs to the top region. His head changes
     * colour with his coat, which is what a hood does.
     */
    val HEAD_WORN = arrayOf(
{worn_rows()}
    )
}}
''', encoding="utf-8")


def main() -> None:
    report = build()
    write_table(report)
    print(f"{report['shapes']} shapes, {report['files']} PNGs written, "
          f"{len(report['alias'])} layers shared with an earlier one")
    print(f"worst recomposition error: {report['worst']:.3f} of one channel level")
    if report["empty"]:
        print("regions with no ink (correct where the shape has no such garment):")
        for entry in sorted(report["empty"]):
            print("  " + entry)


if __name__ == "__main__":
    main()
