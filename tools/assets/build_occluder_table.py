#!/usr/bin/env python3
"""v5.2 item 124 -- the occlusion geometry of a crown, measured off its own artwork.

### What this exists to remove

An occlusion box is a claim: *behind this rectangle, nothing can be seen*. Until v5.2 the claim
for a palm crown was its whole 56x48-unit canvas, and the drawing on that canvas is 51% ink and
49% air -- so the layout pass moved a shop out from behind a fan the shop was plainly visible
through. Correcting that is not the interesting part. The interesting part is that the same
rectangle is written down **twice**: once in `SceneObjectCatalog.occluderBoxes`, which decides,
and once in `ShopFrontVisibilityTest`, which checks -- deliberately, so that a bug in the pass and
a bug in the measurement have to agree to hide a covered shop.

Tighten one and the test goes red, correctly. Tighten both by hand and the test stops being an
independent metre and becomes a check that two hand-copied numbers match. Neither is acceptable,
so the number stops being hand-written on **either** side: it is measured here, from the shipped
PNG, by the same pipeline that already measures every content box, and both sides read what this
writes and derive their own rectangle from it.

### What it writes, and what it deliberately does not

It writes the **artwork's own geometry** -- where the ink is and how much of the box it fills --
in object units, and nothing about occlusion. The inset model (how a coverage becomes a smaller
rectangle) lives in Kotlin and is written twice on purpose, once in the pass and once in the test:
that is the half of the old independence worth keeping, because it is a judgement rather than a
measurement. What this removes is the half that was only ever a transcription.

`SpriteOccluderTableFreshnessTest` re-measures every figure below straight from the PNG, in
Kotlin, without this script -- so the table cannot fall behind a redraw in silence, which is
exactly what `reports/runtime-inventory.json` did for the whole of v5.1.

Run from `tools/assets/` with the asset venv:

    python3 build_occluder_table.py
"""
from __future__ import annotations

import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parent
REPO = TOOL_ROOT.parent.parent
RUNTIME_DIR = REPO / "app/src/main/res/drawable-nodpi"
ENGINE = REPO / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine"
TABLE_KT = ENGINE / "SpriteOccluderTable.kt"
RENDERER = ENGINE / "SceneObjectRenderer.kt"

#: Mirrors `SpriteBlitter.SPRITE_PIXELS_PER_UNIT`, as `inventory.py` does.
PPU = 3.0

#: A family is every drawing the renderer may blit in one place, because the layout pass does not
#: know which one a given theme will pick and the box has to hold for all of them. The palm's three
#: crowns share one origin and one canvas; the oak's two share `TreeSpriteLayout.CANOPY_*`.
#:
#: `tree_fir` is **not** here, and that is a pre-existing gap this pass did not widen: a fir stands
#: in a leafy tree's place under the same `SceneVariant.TREE`, it is a whole tree rather than a
#: crown on a trunk, and the shipped box has never described it. Declaring it would move the box
#: for a shape the maintainer has not been shown -- V5_2A_REPORT.md section 8 carries it as found.
FAMILIES = {
    "PALM_CROWN": {
        "sprites": ["palmtree_fronds", "palmtree_fronds_dead", "palmtree_fronds_frost"],
        "blit_origin": ("PalmSpriteLayout.CROWN_X", "PalmSpriteLayout.CROWN_Y"),
        "object_origin": (("PalmSpriteLayout.CROWN_X", -21.0), ("PalmSpriteLayout.CROWN_Y", -82.0)),
        "doc": "The palm's fan, in its three drawings: live, Halloween's dead one, winter's frost.",
    },
    "TREE_CROWN": {
        "sprites": ["tree_canopy", "tree_dead_branches"],
        # Drawn *with* a family member rather than instead of one, so it occludes nothing the
        # member does not already occlude -- and the generator proves that rather than asserting
        # it, by checking the overlay's ink lies inside the union of the members' ink at the
        # shared origin. `tree_canopy_snowcap` is cut from `tree_canopy`'s own circles, which is
        # why it holds; if a redraw ever breaks it, this list is the wrong place for it.
        "overlays": ["tree_canopy_snowcap"],
        "blit_origin": ("TreeSpriteLayout.CANOPY_X", "TreeSpriteLayout.CANOPY_Y"),
        # The blit origin is not the object-space origin here: `drawTree` lifts the crown by
        # `CANOPY_LIFT_Y` so it sways about the fork, and `FLAT_CANOPY_*` is that lift already
        # folded in -- the space the occlusion geometry is stated in. Getting these two confused
        # would put the whole crown 38 units too low.
        "object_origin": (("TreeSpriteLayout.FLAT_CANOPY_X", -50.0), ("TreeSpriteLayout.FLAT_CANOPY_Y", -118.0)),
        "doc": "The oak's crown, in its two drawings: the five-lobe canopy and Halloween's bare branches.",
    },
}

#: The parasol's fan is not a sprite: `drawParasol` sweeps five 36-degree wedges of radius 34 from
#: 180 degrees, which is a filled half-disc with no PNG and no transparent pixel inside it. It is
#: measured the same way all the same -- rasterised from its own declared geometry and put through
#: the identical profile code -- so the table holds one rule and not two, and the number it lands
#: on is the exact pi/4 the shape's area gives. See `PARASOL_FAN_CHECK` below.
PARASOL = {
    "radius": 34.0,
    "top": -84.0,
    "bottom": -50.0,
    "supersample": 12,
}


def ink_profile(mask: np.ndarray) -> dict[str, float]:
    """The share of the content box that carries ink. One line, and it is the whole measurement.

    Ink is `alpha > 0`, not `alpha == 255`: an antialiased edge stops part of what is behind it,
    and the failure this rule guards against is declaring a shop unhidden when it is hidden, so
    the generous reading is the safe one. `inventory._content_profile` states the three candidate
    numbers and the gap between them.
    """
    height, width = mask.shape
    bands = []
    for index in range(2):
        top = (index * height) // 2
        bottom = ((index + 1) * height) // 2
        band = mask[top:bottom]
        columns = band.sum(axis=0)
        total = float(columns.sum())
        bands.append((
            float(band.sum()) / ((bottom - top) * width),
            float((columns * (np.arange(width) + 0.5)).sum()) / total / width if total else 0.5,
        ))
    return {
        "coverage": float(mask.sum()) / (height * width),
        "row_max": float(mask.sum(axis=1).max()) / width,
        "column_max": float(mask.sum(axis=0).max()) / height,
        "upper_coverage": bands[0][0],
        "upper_centre_x": bands[0][1],
        "lower_coverage": bands[1][0],
        "lower_centre_x": bands[1][1],
    }



def measure_sprite(name: str) -> tuple[tuple[int, int, int, int], dict[str, float], tuple[int, int]]:
    with Image.open(RUNTIME_DIR / f"{name}.png") as image:
        rgba = image.convert("RGBA")
        canvas = rgba.size
        alpha = np.array(rgba)[..., 3]
    bbox = rgba.getchannel("A").getbbox()
    if bbox is None:
        raise SystemExit(f"{name}: the drawing is entirely transparent")
    mask = alpha[bbox[1]:bbox[3], bbox[0]:bbox[2]] > 0
    return bbox, ink_profile(mask), canvas


def parasol_profile() -> dict[str, float]:
    """The half-disc, rasterised from `drawParasol`'s own numbers and profiled by the same code."""
    radius = PARASOL["radius"]
    supersample = PARASOL["supersample"]
    rows = int(round(radius * supersample))
    columns = int(round(2 * radius * supersample))
    yy, xx = np.mgrid[0:rows, 0:columns]
    x = (xx + 0.5) / supersample - radius
    y = (yy + 0.5) / supersample
    return ink_profile(np.hypot(x, radius - y) <= radius)


def _origin_aliases() -> dict[str, str]:
    """Every spelling that resolves to one of the declared origin constants.

    Three spellings reach the same number and all three are the renderer's own: the constant
    itself (`TreeSpriteLayout.CANOPY_X`), a second constant declared equal to it
    (`DEAD_BRANCHES_X = CANOPY_X`, so the bare branches cannot drift off the crown they replace),
    and a local `val` the call site reads it into (`val crownX = PalmSpriteLayout.CROWN_X`). One
    level of aliasing is resolved, syntactically and per file, in the spirit of `callsites.py`:
    a resolver that guesses is worse than one that says it cannot see.
    """
    aliases: dict[str, str] = {}
    canonical = {name for spec in FAMILIES.values() for name in spec["blit_origin"]}
    for name in canonical:
        aliases[name] = name
    for path in (ENGINE / "PalmSpriteLayout.kt", ENGINE / "TreeSpriteLayout.kt", RENDERER):
        text = path.read_text(encoding="utf-8")
        object_name = path.stem
        for _ in range(2):  # two passes: an alias of an alias is still one hop from a constant
            for match in re.finditer(r"(?:const\s+)?val\s+(\w+)\s*=\s*([\w.]+)\s*$", text, re.M):
                declared, value = match.group(1), match.group(2)
                for candidate in (value, f"{object_name}.{value}"):
                    if candidate in aliases:
                        aliases.setdefault(f"{object_name}.{declared}", aliases[candidate])
                        aliases.setdefault(declared, aliases[candidate])
                        break
    return aliases


def _check_overlays(family: str, spec: dict) -> list[str]:
    """An overlay may only be left out of the box if its ink is inside the members' ink."""
    overlays = spec.get("overlays", ())
    if not overlays:
        return []
    # Compared in ORIGIN space, not canvas space: the family shares a blit origin, not a canvas
    # size (`tree_dead_branches` is 282 px wide against the canopy's 303, `tree_canopy_snowcap`
    # 294x114 against 303x198). The origin is each canvas's own (0, 0), so top-left alignment in a
    # frame as large as the largest member *is* the scene-space comparison.
    masks = {name: _alpha_mask(name) for name in list(spec["sprites"]) + list(overlays)}
    rows = max(mask.shape[0] for mask in masks.values())
    columns = max(mask.shape[1] for mask in masks.values())

    def framed(mask: np.ndarray) -> np.ndarray:
        out = np.zeros((rows, columns), dtype=bool)
        out[: mask.shape[0], : mask.shape[1]] = mask
        return out

    union = np.zeros((rows, columns), dtype=bool)
    for name in spec["sprites"]:
        union |= framed(masks[name])
    problems = []
    for name in overlays:
        outside = int((framed(masks[name]) & ~union).sum())
        if outside:
            problems.append(
                f"{family}: {name} puts {outside} px of ink outside every member's silhouette, "
                f"so it is not covered by them and must be declared as a member"
            )
    return problems


def _alpha_mask(name: str) -> np.ndarray:
    with Image.open(RUNTIME_DIR / f"{name}.png") as image:
        return np.array(image.convert("RGBA"))[..., 3] > 0


def check_call_sites() -> None:
    """Every declared drawing must be blitted at the declared origin, in the renderer, and no
    undeclared drawing may be blitted there.

    Not a formality: the whole table is a claim about where a drawing lands, and the only thing
    that makes it true is the blit. A crown added to the artwork and to the renderer but not to
    `FAMILIES` would be described here by its siblings' geometry, which is the silent-staleness
    failure this file exists to make impossible.
    """
    source = RENDERER.read_text(encoding="utf-8")
    aliases = _origin_aliases()
    blits: list[tuple[str, str, str]] = []
    for match in re.finditer(r"R\.drawable\.(\w+)\s*,\s*([\w.\-]+)\s*,\s*([\w.\-]+)", source):
        sprite, ox, oy = match.groups()
        blits.append((sprite, aliases.get(ox, ox), aliases.get(oy, oy)))

    problems = []
    for family, spec in FAMILIES.items():
        origin = spec["blit_origin"]
        at_origin = {sprite for sprite, ox, oy in blits if (ox, oy) == origin}
        for name in spec["sprites"]:
            if name not in at_origin:
                problems.append(f"{family}: {name} is not blitted at {origin} in SceneObjectRenderer.kt")
        declared = set(spec["sprites"]) | set(spec.get("overlays", ()))
        for surplus in sorted(at_origin - declared):
            problems.append(f"{family}: {surplus} is blitted at {origin} but is not declared")
        problems.extend(_check_overlays(family, spec))
    if problems:
        raise SystemExit("call-site check failed:\n  " + "\n  ".join(problems))


HEADER = '''package com.paperscrape.livewallpaper.engine

/**
 * Where the ink is, in every drawing an occlusion box has to speak for. **Generated** by
 * `tools/assets/build_occluder_table.py` from the shipped PNGs; edit that script, not this file.
 *
 * ### Why a generated table and not two literals
 *
 * An occlusion box says *nothing behind this rectangle can be seen*, and for a palm crown that was
 * the whole 56x48-unit canvas of a drawing which is 51% ink -- so the layout pass moved a shop out
 * from behind a fan it was plainly visible through (`BACKLOG_v5_1.md` item 124, and the audit's
 * photograph of the desert bar). The rectangle was written down twice, here in the catalogue's
 * [SceneObjectCatalog] geometry and again in `ShopFrontVisibilityTest`, the second copy deliberate
 * so that a wrong number has to be typed twice to hide a covered shop.
 *
 * Tightening one of the two turns the test red, correctly. Tightening both by hand would make the
 * test a comparison of two transcriptions instead of an independent measurement. So the number is
 * no longer typed on either side: it is **measured off the artwork** and both sides read it and
 * derive their own rectangle from it. What each side still writes for itself is the *model* -- how
 * a coverage becomes a smaller rectangle -- because that is a judgement and not a measurement, and
 * it is the half of the independence worth keeping.
 *
 * `SpriteOccluderTableFreshnessTest` re-measures every figure below straight from the PNG, in
 * Kotlin, without the generator -- so this table cannot fall behind a redraw in silence. That is
 * not a hypothetical: `tools/assets/reports/runtime-inventory.json` carried the pre-v5.1 palm for
 * the whole of v5.1 and nothing said so.
 *
 * ### The units
 *
 * [contentLeft], [contentTop], [contentRight], [contentBottom] are the drawing's own ink bounding
 * box in **object units at the blit origin** -- the canvas's transparent guard margin excluded,
 * which is the first thing the old box got wrong. [coverage] is the share of that box carrying
 * ink, counting a pixel as ink at any alpha above zero.
 */
internal object SpriteOccluderTable {

    /** One drawing's ink: its box in object units, and how much of that box it fills. */
    internal class InkBox(
        val contentLeft: Float,
        val contentTop: Float,
        val contentRight: Float,
        val contentBottom: Float,
        /**
         * The fullest single row, as a share of the box's width, and the fullest single column,
         * as a share of its height. **These two are the whole of the chosen model** and the
         * reason it is these two rather than the box's overall coverage is in
         * `SceneObjectCatalog.crownBoxes`. The overall coverage, the two-band profile and the
         * ink centroids were measured too and are in `tools/assets/reports/runtime-inventory.json`
         * for anyone re-opening the choice; they are not here because nothing reads them, and a
         * generated number nothing reads is a number nothing checks.
         */
        val rowMax: Float,
        val columnMax: Float,
    )

'''


def entry(name: str, left: float, top: float, right: float, bottom: float, p: dict[str, float]) -> str:
    return (
        f"        // {name}\n"
        f"        // coverage {p['coverage']:.4f} of the box, for the record; the model reads the two below\n"
        f"        InkBox(\n"
        f"            {left:.6f}f, {top:.6f}f, {right:.6f}f, {bottom:.6f}f,\n"
        f"            rowMax = {p['row_max']:.6f}f, columnMax = {p['column_max']:.6f}f,\n"
        f"        ),\n"
    )


def main() -> int:
    check_call_sites()
    body = []
    console = []
    for family, spec in FAMILIES.items():
        (ox_name, ox), (oy_name, oy) = spec["object_origin"]
        lines = [f"    /** {spec['doc']} Stated at ({ox_name}, {oy_name}). */"]
        lines.append(f"    val {family}: List<InkBox> = listOf(")
        for name in spec["sprites"]:
            bbox, profile, canvas = measure_sprite(name)
            left = ox + bbox[0] / PPU
            top = oy + bbox[1] / PPU
            right = ox + bbox[2] / PPU
            bottom = oy + bbox[3] / PPU
            lines.append(entry(name, left, top, right, bottom, profile).rstrip("\n"))
            console.append(
                f"{family:<12} {name:<24} canvas {canvas[0]}x{canvas[1]} "
                f"content {bbox[2]-bbox[0]}x{bbox[3]-bbox[1]} "
                f"units ({left:.3f},{top:.3f})..({right:.3f},{bottom:.3f}) "
                f"coverage {profile['coverage']:.4f} rowMax {profile['row_max']:.4f} "
                f"columnMax {profile['column_max']:.4f} "
                f"bands {profile['upper_coverage']:.4f}@{profile['upper_centre_x']:.4f} / "
                f"{profile['lower_coverage']:.4f}@{profile['lower_centre_x']:.4f}"
            )
        lines.append("    )")
        body.append("\n".join(lines))

    fan = parasol_profile()
    coverage = fan["coverage"]
    exact = math.pi / 4
    if abs(coverage - exact) > 5e-4:
        raise SystemExit(
            f"the parasol fan rasterises to {coverage:.6f}, and a filled half-disc in its own "
            f"circumscribed rectangle is exactly pi/4 = {exact:.6f}. One of the two is wrong."
        )
    body.append(
        "    /**\n"
        "     * The parasol's fan. **Not a sprite**: `drawParasol` sweeps five 36-degree wedges of\n"
        f"     * radius {PARASOL['radius']:.0f} from 180 degrees, so it is a filled half-disc with no PNG and no\n"
        "     * transparent pixel inside it. Measured the same way all the same -- rasterised from\n"
        "     * that declared geometry and put through the identical profile code -- so the table\n"
        "     * holds one rule and not two. The generator refuses to write this line unless the\n"
        "     * rasterised figure agrees with the exact pi/4 a half-disc's area gives, which is the\n"
        "     * check that the profile code itself is measuring what it claims to.\n"
        "     */\n"
        "    val PARASOL_FAN: List<InkBox> = listOf(\n"
        + entry("the wedge fan", -PARASOL["radius"], PARASOL["top"], PARASOL["radius"], PARASOL["bottom"], fan)
        + "    )"
    )
    console.append(
        f"PARASOL_FAN  (procedural half-disc)      units ({-PARASOL['radius']:.3f},{PARASOL['top']:.3f}).."
        f"({PARASOL['radius']:.3f},{PARASOL['bottom']:.3f}) coverage {coverage:.4f} "
        f"(pi/4 = {exact:.6f})"
    )

    TABLE_KT.write_text(HEADER + "\n\n".join(body) + "\n}\n", encoding="utf-8")
    print("\n".join(console))
    print(f"\nwrote {TABLE_KT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
