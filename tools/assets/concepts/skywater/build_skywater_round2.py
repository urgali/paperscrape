#!/usr/bin/env python3
"""Round 2 of the v4.26 sky-and-water concepts: the cloud and the bird go back to the drawing
board, the dolphin and the sailboat stay as concept B "Rilievo" delivered them.

Run from ``tools/assets`` with the pinned venv:

    /home/bober/.venvs/paperscrape-assets/bin/python concepts/skywater/build_skywater_round2.py

Three combinations are built, one directory each under ``concepts/skywater/round2/``, so that
one capture build carries one cloud variant and one bird variant; the pairing is for capture
economy only and the two families are judged independently:

    cavolfiore_pieno       cloud N1 "Cavolfiore"      + bird U1 "Pieno"
    ombra_smerlato         cloud N2 "Ventre in ombra" + bird U2 "Smerlato"
    sfilacciata_trequarti  cloud N3 "Sfilacciata"     + bird U3 "Tre quarti"

**The cloud is drawn for the band, not for itself.** On the device the band is about sixteen
copies of this one sprite at four scales, painted in index order, and each copy's interior is
covered by the next; what survives is the crest, the belly, and -- because a later copy is
painted on top -- any tone inside the copy that lands on top. So: a crest of lobes of three
very different heights with deep cut cusps; a belly of inverted lobes on a *slanted* base, never
a horizontal line; an asymmetric mass so the per-candidate scale spreads the copies' crests and
bellies over different heights; and, in N2, a shaded belly that shows through the band as soft
undersides of clouds in front of others.

**The bird keeps concept B's recipe and gains volume from its silhouette**: a spindle body ten
pixels deep, a round head set off by a neck, a short beak, two separate wing papers (the far one
a tone darker and behind the body, the near one in front), under-papers offset horizontally
only -- a vertical offset would swap sides twice a second under the flap's vertical mirror --
and wings that taper to a point with a concave trailing edge. No cut-through openings: on the
dark tint the relief is invisible by arithmetic (14% of an already dark colour), so the bat
reading has to come from the silhouette alone, which is what U2's scalloped trailing edge is
for.

Canvases, conventions and tint classes are the shipped ones, as in round 1.
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
    UNIT, WHITE, PAPER_1, PAPER_2, UNDER_MASK, Sprite, Piece, emit_svg, cut, arc, circle_pts,
    dolphin, hull, sail, CLOUD_W, CLOUD_H, NOTES,
)
from paperscrape_assets import raster  # noqa: E402
from paperscrape_assets.inventory import measure_raster  # noqa: E402

SHADE = "#E9E9E9"      # the belly in shade: 9% below white, well inside the 14% a mask may carry
FAR_WING = "#ECECEC"   # the wing behind the body, a tone back


# ------------------------------------------------------------------------------ ring outline
def _intersections(a, b):
    (x0, y0, r0), (x1, y1, r1) = a, b
    d = math.hypot(x1 - x0, y1 - y0)
    if d >= r0 + r1 or d <= abs(r0 - r1) or d == 0:
        raise ValueError(f"lobes {a} and {b} do not overlap as neighbours (d={d:.1f})")
    l = (r0 * r0 - r1 * r1 + d * d) / (2 * d)
    h = math.sqrt(max(r0 * r0 - l * l, 0.0))
    mx, my = x0 + l * (x1 - x0) / d, y0 + l * (y1 - y0) / d
    return [(mx + h * (y1 - y0) / d, my - h * (x1 - x0) / d), (mx - h * (y1 - y0) / d, my + h * (x1 - x0) / d)]


def ring_contour(circles, step_deg=11.0, notch_at=(), notch=(0.0, 0.0)):
    """The outer boundary of a closed ring of overlapping circles, walked clockwise on screen.

    ``circles`` are (cx, cy, r) in ring order: the crest lobes left to right, then the belly
    lobes right to left, each overlapping the next and the last overlapping the first. At every
    joint the outer of the two intersection points is the one farther from the ring's centroid;
    on every circle the walk goes from its entry joint to its exit joint with increasing angle,
    which on a clockwise ring is always the outside arc. ``notch_at`` lists the joints (index of
    the circle *before* the joint) that are cut on into a V of ``notch`` = (depth, half-angle).
    """
    n = len(circles)
    for k in range(n):
        a, b = circles[k], circles[(k + 1) % n]
        d = math.hypot(b[0] - a[0], b[1] - a[1])
        if not abs(a[2] - b[2]) < d < a[2] + b[2]:
            raise SystemExit(f"ring pair {k}->{(k + 1) % n}: {a} {b} d={d:.1f}, needs {abs(a[2] - b[2])} < d < {a[2] + b[2]}")
    gx = sum(c[0] for c in circles) / n
    gy = sum(c[1] for c in circles) / n
    joints = []
    for k in range(n):
        pts = _intersections(circles[k], circles[(k + 1) % n])
        joints.append(max(pts, key=lambda p: math.hypot(p[0] - gx, p[1] - gy)))
    out = []
    for k, (cx, cy, r) in enumerate(circles):
        entry, exit_pt = joints[k - 1], joints[k]
        t0 = math.atan2(entry[1] - cy, entry[0] - cx)
        t1 = math.atan2(exit_pt[1] - cy, exit_pt[0] - cx)
        t1 = t0 + ((t1 - t0) % (2 * math.pi))
        d_in = math.radians(notch[1]) if (k - 1) % n in notch_at else 0.0
        d_out = math.radians(notch[1]) if k in notch_at else 0.0
        seg = arc(cx, cy, r, t0 + d_in, t1 - d_out, step_deg)
        out.extend(seg if (k == 0 or d_in) else seg[1:])
        if k in notch_at:
            out.append((exit_pt[0], exit_pt[1] + notch[0]))
    return out


def check_extent(points, name):
    xs = [p[0] for p in points]; ys = [p[1] for p in points]
    if min(xs) < 1 or max(xs) > CLOUD_W - 1 or min(ys) < 1 or max(ys) > CLOUD_H - 1:
        raise SystemExit(f"{name}: outline leaves the canvas: x {min(xs):.1f}..{max(xs):.1f} y {min(ys):.1f}..{max(ys):.1f}")


# ------------------------------------------------------------------------------ the clouds
#: N1 "Cavolfiore": crest of seven lobes, the tall dome left of centre and deep cut cusps; a
#: belly of eight inverted lobes on a base that rises toward the right; a small puff detached
#: at the lower right so the copies' outlines break up at different heights.
N1_CREST = [(26, 100, 18), (48, 78, 30), (84, 46, 42), (130, 60, 38), (170, 82, 30), (198, 100, 22), (212, 110, 14)]
N1_BELLY = [(220, 114, 9), (206, 113, 12), (184, 114, 13), (162, 114, 14), (138, 114, 14), (114, 112, 14), (90, 110, 13), (68, 107, 12), (48, 106, 11), (28, 108, 11)]
N1_PUFF = [(244, 106, 8), (254, 112, 8), (248, 120, 7), (240, 116, 6)]


def cloud_n1(seed="cloud-n1"):
    ring = N1_CREST + N1_BELLY
    outline = ring_contour(ring, notch_at=range(len(N1_CREST) - 1), notch=(20.0, 9.0))
    outline = cut(outline, seed, 1.2)
    check_extent(outline, "N1")
    puff = cut(ring_contour(N1_PUFF, step_deg=16.0), seed + "-puff", 0.8)
    return outline, puff


def cloud_n2(seed="cloud-n2"):
    outline, puff = cloud_n1(seed)
    # The belly in shade: the belly lobes grown by 1.45 and clipped to the silhouette, so the
    # shade's upper edge is a run of arcs and not a line, and a copy painted on top of the band
    # shows it as a soft underside.
    shade = []
    for k, (cx, cy, r) in enumerate(N1_BELLY):
        shade.append(cut(circle_pts(cx, cy - 2, r * 1.45, 14, start=k * 0.5), f"{seed}-shade{k}", 0.8))
    return outline, puff, shade


#: N3 "Sfilacciata": lower and longer, three domes of different height with cut cusps, a belly
#: that rises toward the tail, and two tapered tails trailing to -x, behind the drift, never
#: thinner than 6 units.
N3_CREST = [(64, 96, 20), (88, 72, 30), (124, 50, 38), (166, 60, 36), (206, 78, 30), (238, 94, 22)]
N3_BELLY = [(246, 106, 9), (226, 108, 12), (206, 110, 13), (184, 111, 14), (160, 111, 14), (136, 110, 14), (112, 108, 13), (90, 106, 12), (68, 104, 11), (50, 104, 10)]
N3_TAILS = [
    [(70, 84), (44, 88), (24, 94), (12, 100), (14, 105), (30, 103), (50, 100), (72, 98)],
    [(66, 106), (44, 112), (26, 118), (18, 125), (34, 123), (54, 118), (72, 116)],
]


def cloud_n3(seed="cloud-n3"):
    ring = N3_CREST + N3_BELLY
    outline = cut(ring_contour(ring, notch_at=range(len(N3_CREST) - 1), notch=(18.0, 9.0)), seed, 1.2)
    check_extent(outline, "N3")
    tails = [cut(t, f"{seed}-tail{k}", 1.0) for k, t in enumerate(N3_TAILS)]
    return outline, tails


def cloud(style: str) -> Sprite:
    s = Sprite("cloud_body", CLOUD_W, CLOUD_H, "SCENE_UNITS", "TINTABLE", "sky")
    if style == "cavolfiore":
        outline, puff = cloud_n1()
        s.add(outline, WHITE)
        s.add(puff, WHITE)
    elif style == "ombra":
        outline, puff, shade = cloud_n2()
        s.add(outline, WHITE)
        for sh in shade:
            s.add(sh, SHADE, clip_to=outline)
        s.add(puff, WHITE)
    elif style == "sfilacciata":
        outline, tails = cloud_n3()
        for t in tails:
            s.add(t, WHITE)
        s.add(outline, WHITE)
    else:
        raise ValueError(style)
    return s


# ------------------------------------------------------------------------------ the birds
#: 90x24 canvas pixels, flap axis row 18. The body is a spindle ten pixels deep on the axis, the
#: head a rounder mass set off by a neck, the beak short; both are symmetric enough about row
#: 18 that the mirrored frame does not hop.
BODY = [(10.0, 17.0), (16.0, 15.6), (26.0, 14.2), (38.0, 13.2), (50.0, 13.6), (57.0, 15.2),
        (60.0, 13.4), (64.0, 12.6), (68.0, 13.4), (71.0, 15.6), (73.6, 18.2), (71.0, 20.6), (68.0, 22.6),
        (64.0, 23.2), (60.0, 22.6), (57.0, 21.2), (50.0, 22.6), (38.0, 22.8), (26.0, 21.8), (16.0, 20.4), (10.0, 19.4)]

#: Wings as (leading edge root->tip, trailing edge tip->root). The root ends sit inside the
#: body, which covers them: the far wing is painted before the body, the near one after it.
FAR_WING_U1 = ([(50.0, 13.4), (40.0, 11.0), (28.0, 7.6), (14.0, 3.8), (2.0, 2.2)],
               [(7.0, 6.0), (16.0, 9.6), (26.0, 12.6), (36.0, 15.2), (46.0, 16.4)])
NEAR_WING_U1 = ([(56.0, 15.0), (66.0, 11.2), (76.0, 7.2), (84.0, 3.4), (88.0, 2.2)],
                [(83.0, 6.2), (74.0, 9.8), (64.0, 13.0), (54.0, 15.8), (46.0, 16.6)])
FAR_WING_U3 = ([(48.0, 13.4), (38.0, 11.0), (26.0, 8.4), (16.0, 6.6), (9.0, 5.6)],
               [(13.0, 8.6), (22.0, 11.2), (32.0, 13.8), (42.0, 15.6), (48.0, 16.2)])
NEAR_WING_U3 = ([(56.0, 14.6), (68.0, 9.6), (80.0, 4.6), (89.0, 1.6)],
                [(85.0, 6.0), (74.0, 10.8), (62.0, 15.2), (50.0, 17.6), (44.0, 17.4)])


def scalloped(edge, depth=2.4, n=3):
    """The trailing edge tip->root as `n` convex scallops between cusps on the original line."""
    (x0, y0), (x1, y1) = edge[0], edge[-1]
    out = [(x0, y0)]
    # the outward normal of the trailing edge points away from the leading edge: down-ish
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy)
    nx, ny = -dy / L, dx / L
    if ny < 0:
        nx, ny = -nx, -ny
    for k in range(n):
        a, b = k / n, (k + 1) / n
        mx, my = x0 + dx * (a + b) / 2, y0 + dy * (a + b) / 2
        out.append((mx + nx * depth, my + ny * depth))
        out.append((x0 + dx * b, y0 + dy * b))
    return out


def wing_polygon(leading, trailing, seed, scallop=False):
    trail = scalloped(trailing) if scallop else trailing
    return cut(leading + trail, seed, 0.4)


def bird(style: str) -> Sprite:
    s = Sprite("bird_body", 90, 24, "CANVAS_PIXELS", "TINTABLE", "animal", view_units=False)
    off = (2.4, 0.0)
    far, near = (FAR_WING_U3, NEAR_WING_U3) if style == "trequarti" else (FAR_WING_U1, NEAR_WING_U1)
    scallop = style == "smerlato"
    s.add(wing_polygon(*far, f"bird-{style}-far", scallop), FAR_WING, under=UNDER_MASK, under_offset=off)
    s.add(cut(BODY, f"bird-{style}-body", 0.35), WHITE, under=UNDER_MASK, under_offset=off)
    s.add(wing_polygon(*near, f"bird-{style}-near", scallop), WHITE, under=UNDER_MASK, under_offset=off)
    return s


# ------------------------------------------------------------------------------ build
COMBOS = {
    "cavolfiore_pieno": ("cavolfiore", "pieno"),
    "ombra_smerlato": ("ombra", "smerlato"),
    "sfilacciata_trequarti": ("sfilacciata", "trequarti"),
}
BLURB = {
    "cavolfiore": "Cloud N1 'Cavolfiore' -- one tone; a crest of seven lobes of three heights with cusps cut 24 units deep, a belly of inverted lobes on a base rising to the right, a detached puff at the lower right. Drawn for a band of overlapping copies, not for itself.",
    "ombra": "Cloud N2 'Ventre in ombra' -- N1's silhouette plus a shaded belly (9% below white) whose upper edge is a run of arcs; a copy painted on top of the band shows it as a soft underside.",
    "sfilacciata": "Cloud N3 'Sfilacciata' -- lower and longer: three domes with cut cusps, a belly rising toward the tail, two tapered tails trailing to -x behind the drift, never thinner than 6 units.",
    "pieno": "Bird U1 'Pieno' -- concept B's recipe with volume from the silhouette: spindle body 10 px deep, round head off a neck, short beak, two separate tapered wings (the far one a tone back, behind the body), under-papers offset 2.4 px along x only.",
    "smerlato": "Bird U2 'Smerlato' -- U1 with three scallops cut into each wing's trailing edge: feather tips on the white gull, membrane on the dark bat.",
    "trequarti": "Bird U3 'Tre quarti' -- U1's body with unequal wings: the near wing long and broad in front, the far wing short and higher behind, for a three-quarter view.",
}


def build(combo: str) -> None:
    cloud_style, bird_style = COMBOS[combo]
    out = HERE / "round2" / combo / "svg"
    out.mkdir(parents=True, exist_ok=True)
    entries = []
    sprites = [bird(bird_style), cloud(cloud_style), dolphin("rilievo"), hull("rilievo"), sail("rilievo")]
    styles = [bird_style, cloud_style, "rilievo", "rilievo", "rilievo"]
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
        entries.append({
            "name": sprite.name, "category": sprite.category,
            "width": expected[0], "height": expected[1], "contentBox": list(m.content_bbox),
            "scale": sprite.scale, "tint": sprite.tint, "usage": "concept",
            "anchorRule": "PART_LOCAL", "anchor": [0, 0], "season": "all",
            "source": {"kind": "svg", "file": f"{sprite.name}.svg"},
            "notes": blurb + " " + NOTES[sprite.name],
        })
        print(f"  {combo:22s} {sprite.name:14s} {expected[0]}x{expected[1]} content {list(m.content_bbox)}")
    reg = {"schemaVersion": 2,
           "note": f"Registry of the v4.26 round-2 combination '{combo}': the shipped registry's own schema, kept here because these sprites are NOT shipped. Canvases, scale conventions, anchor rules and tint classes are the shipped ones.",
           "sprites": entries}
    (HERE / "round2" / combo / "sprites.concept.json").write_text(json.dumps(reg, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    for combo in (sys.argv[1:] or list(COMBOS)):
        build(combo)
