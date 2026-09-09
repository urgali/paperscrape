#!/usr/bin/env python3
"""Round 3 of the v4.26 sky-and-water concepts: the cloud N1 "Cavolfiore" with one shaded
belly, and the bird U2 "Smerlato" at two smaller sizes. The dolphin and the sailboat stay as
concept B "Rilievo" delivered them.

Run from ``tools/assets`` with the pinned venv:

    /home/bober/.venvs/paperscrape-assets/bin/python concepts/skywater/build_skywater_round3.py

Two combinations are built, one directory each under ``concepts/skywater/round3/``, paired
for capture economy only -- the cloud and the bird are judged independently:

    ombra_leggera_d1   cloud V1 "Ombra leggera" (7.5 % below white) + bird D1 (0.75 x)
    ombra_piena_d2     cloud V2 "Ombra piena"   (13 % below white)  + bird D2 (0.60 x)

**The cloud is N1's silhouette, byte for byte the same outline** (same lobes, same seed for the
cut wobble), so the two variants differ from N1 and from each other in one thing only: the
belly in shade. Round 2's N2 also had N1's outline -- ``cloud_n2`` calls ``cloud_n1`` -- and
what read as narrow cusps and tails in it was the *shade's* upper edge: belly circles grown by
1.45 leave a narrow V between every pair of arcs and thin out into tails at both ends. Here the
shade is the belly's own line repeated 24 units higher: the same lobes, the same joints, so the
shade follows the underside the way light from above would leave it, and it has no silhouette
of its own -- one paper, one tone in shade, clipped inside the one outline. No offset, no rim,
no second sheet.

**The bird is U2 scaled down with the scallop kept at the size the sampler can show.** The
whole drawing scales by 0.75 or 0.60 (the flap axis moves from canvas row 18 to row 13 or 11,
so the blit origin follows), but the scallops on each wing's trailing edge keep an absolute
depth of about 2.5 px -- a scallop that scaled with the bird would be 1.8 or 1.4 px deep and
would not survive the reduction, which is why round 2's U2 was chosen. At 0.60 the near wing
is ~21 px long, so it carries two scallops instead of three; below 0.60 the scallop cannot be
kept at 2.5 px without widening the wing, which is a different drawing.

Canvases: cloud 798x396 (unchanged); bird 68x18 (D1) and 54x15 (D2), CANVAS_PIXELS, flap
axis at row 13 and row 11 respectively -- ``BIRD_SPRITE_ORIGIN_X/Y_PX`` become (-34, -13) and
(-27, -11) in the capture build that carries each one.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent.parent))

from build_skywater_concepts import (  # noqa: E402
    UNIT, WHITE, UNDER_MASK, Sprite, emit_svg, cut, arc,
    dolphin, hull, sail, CLOUD_W, CLOUD_H, NOTES,
)
from build_skywater_round2 import (  # noqa: E402
    N1_CREST, N1_BELLY, N1_PUFF, cloud_n1, _intersections, ring_contour, check_extent,
    BODY, FAR_WING_U1, NEAR_WING_U1, FAR_WING, scalloped,
)
from paperscrape_assets import raster  # noqa: E402
from paperscrape_assets.inventory import measure_raster  # noqa: E402

SHADE_LIGHT = "#ECECEC"   # 7.5 % below white
SHADE_FULL = "#DDDDDD"    # 13.3 % below white, inside the 14 % a tintable mask may carry
SHADE_RISE = 24.0         # how far above the belly line the shade's upper edge runs, in units


# ------------------------------------------------------------------------------ the cloud
def ring_segments(circles, step_deg=11.0):
    """Per-circle outside arcs of a closed ring of circles -- the same walk as
    ``ring_contour`` (same joints, same direction) but returned one circle at a time, so a run
    of them can be taken out: here the belly circles, whose arcs are the cloud's underside."""
    n = len(circles)
    gx = sum(c[0] for c in circles) / n
    gy = sum(c[1] for c in circles) / n
    joints = []
    for k in range(n):
        pts = _intersections(circles[k], circles[(k + 1) % n])
        joints.append(max(pts, key=lambda p: math.hypot(p[0] - gx, p[1] - gy)))
    segs = []
    for k, (cx, cy, r) in enumerate(circles):
        entry, exit_pt = joints[k - 1], joints[k]
        t0 = math.atan2(entry[1] - cy, entry[0] - cx)
        t1 = math.atan2(exit_pt[1] - cy, exit_pt[0] - cx)
        t1 = t0 + ((t1 - t0) % (2 * math.pi))
        segs.append(arc(cx, cy, r, t0, t1, step_deg))
    return segs


def belly_shade(seed="cloud-n1-shade"):
    """The belly line of N1 repeated ``SHADE_RISE`` units higher and closed well below the
    silhouette, to be clipped inside it: a band of constant rise whose upper edge is the
    belly's own run of inverted lobes and cusps."""
    ring = N1_CREST + N1_BELLY
    segs = ring_segments(ring)
    belly = []
    for k in range(len(N1_CREST), len(ring)):
        seg = segs[k]
        belly.extend(seg if not belly else seg[1:])
    # walked right to left, as the ring does on its lower half
    upper = [(x, y - SHADE_RISE) for x, y in belly]
    upper = cut(upper, seed, 1.2)
    x_l, y_l = belly[-1]
    x_r, y_r = belly[0]
    poly = upper + [(x_l - 6, y_l + 30), (x_r + 6, y_r + 30)]
    return poly


def cloud(style: str) -> Sprite:
    s = Sprite("cloud_body", CLOUD_W, CLOUD_H, "SCENE_UNITS", "TINTABLE", "sky")
    outline, puff = cloud_n1()          # N1's outline, same seed: the same paper
    check_extent(outline, "V-" + style)
    s.add(outline, WHITE)
    tone = {"leggera": SHADE_LIGHT, "piena": SHADE_FULL}[style]
    s.add(belly_shade(), tone, clip_to=outline)
    s.add(puff, WHITE)
    return s


# ------------------------------------------------------------------------------ the bird
#: (scale on x, flap-axis row, canvas width, canvas height, scallops per wing)
BIRD_SIZES = {
    "d1": (0.75, 13, 68, 18, 3),
    "d2": (0.60, 11, 54, 15, 2),
}
SCALLOP_DEPTH_PX = 2.5


def _map(points, kx, axis):
    """U2's 90x24 geometry scaled by ``kx`` on x and by ``axis/18`` on y about the flap axis,
    so the body still sits on the axis row and both wings rise above it."""
    ky = axis / 18.0
    return [(x * kx + 0.25, axis + (y - 18.0) * ky) for x, y in points]


def bird(size: str) -> Sprite:
    kx, axis, w, h, n_scallops = BIRD_SIZES[size]
    s = Sprite("bird_body", w, h, "CANVAS_PIXELS", "TINTABLE", "animal", view_units=False)
    off = (2.4 * kx, 0.0)
    far_lead, far_trail = (_map(FAR_WING_U1[0], kx, axis), _map(FAR_WING_U1[1], kx, axis))
    near_lead, near_trail = (_map(NEAR_WING_U1[0], kx, axis), _map(NEAR_WING_U1[1], kx, axis))
    body = _map(BODY, kx, axis)
    wob = 0.4 * kx

    def wing(lead, trail, seed):
        return cut(lead + scalloped(trail, depth=SCALLOP_DEPTH_PX, n=n_scallops), seed, wob)

    s.add(wing(far_lead, far_trail, f"bird-{size}-far"), FAR_WING, under=UNDER_MASK, under_offset=off)
    s.add(cut(body, f"bird-{size}-body", 0.35 * kx), WHITE, under=UNDER_MASK, under_offset=off)
    s.add(wing(near_lead, near_trail, f"bird-{size}-near"), WHITE, under=UNDER_MASK, under_offset=off)
    for p in s.pieces:
        for x, y in p.points:
            if not (0.5 <= x <= w - 0.5 and 0.5 <= y <= h - 0.5):
                raise SystemExit(f"bird {size}: vertex ({x:.1f},{y:.1f}) outside the {w}x{h} canvas")
    return s


# ------------------------------------------------------------------------------ build
COMBOS = {
    "ombra_leggera_d1": ("leggera", "d1"),
    "ombra_piena_d2": ("piena", "d2"),
}
BLURB = {
    "leggera": "Cloud V1 'Ombra leggera' -- N1 'Cavolfiore' outline unchanged, plus one belly in shade at 7.5% below white: the belly's own line repeated 24 units higher, clipped inside the silhouette. One paper, one tone in shade, no offset and no second sheet.",
    "piena": "Cloud V2 'Ombra piena' -- the same as V1 with the shade at 13.3% below white, the most a tintable mask may carry.",
    "d1": "Bird D1 -- U2 'Smerlato' at 0.75 x: 68x18 canvas, flap axis row 13, three scallops per wing kept 2.5 px deep on the screen. Blit origin (-34, -13).",
    "d2": "Bird D2 -- U2 'Smerlato' at 0.60 x: 54x15 canvas, flap axis row 11, two scallops per wing kept 2.5 px deep on the screen. Blit origin (-27, -11).",
}


def build(combo: str) -> None:
    cloud_style, bird_size = COMBOS[combo]
    out = HERE / "round3" / combo / "svg"
    out.mkdir(parents=True, exist_ok=True)
    entries = []
    sprites = [bird(bird_size), cloud(cloud_style), dolphin("rilievo"), hull("rilievo"), sail("rilievo")]
    styles = [bird_size, cloud_style, "rilievo", "rilievo", "rilievo"]
    for sprite, style in zip(sprites, styles):
        svg = emit_svg(sprite, style)
        (out / f"{sprite.name}.svg").write_text(svg + "\n", encoding="utf-8")
        r = raster.render_svg(svg)
        expected = (int(sprite.width_units * UNIT), int(sprite.height_units * UNIT)) if sprite.view_units \
            else (int(sprite.width_units), int(sprite.height_units))
        if r.size != expected:
            raise SystemExit(f"{sprite.name}: rendered {r.size}, expected {expected}")
        (out / f"{sprite.name}.png").write_bytes(r.png_bytes)
        m = measure_raster(sprite.name, r)
        blurb = BLURB.get(style, "Concept B 'Rilievo', as delivered in round 1 and chosen by the maintainer.")
        note = NOTES[sprite.name]
        if sprite.name == "bird_body":
            kx, axis, w, h, _ = BIRD_SIZES[bird_size]
            note = f"Flap axis is canvas row {axis} (blit origin -{axis}): body and head on it, wings above. Faces +x. {w}x{h} canvas, CANVAS_PIXELS."
        entries.append({
            "name": sprite.name, "category": sprite.category,
            "width": expected[0], "height": expected[1], "contentBox": list(m.content_bbox),
            "scale": sprite.scale, "tint": sprite.tint, "usage": "concept",
            "anchorRule": "PART_LOCAL", "anchor": [0, 0], "season": "all",
            "source": {"kind": "svg", "file": f"{sprite.name}.svg"},
            "notes": blurb + " " + note,
        })
        print(f"  {combo:18s} {sprite.name:14s} {expected[0]}x{expected[1]} content {list(m.content_bbox)}")
    reg = {"schemaVersion": 2,
           "note": f"Registry of the v4.26 round-3 combination '{combo}': the shipped registry's own schema, kept here because these sprites are NOT shipped. The bird canvas is smaller than the shipped one on purpose; everything else is the shipped convention.",
           "sprites": entries}
    (HERE / "round3" / combo / "sprites.concept.json").write_text(json.dumps(reg, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    for combo in (sys.argv[1:] or list(COMBOS)):
        build(combo)
