#!/usr/bin/env python3
"""Builds the three v4.26 sky-and-water concepts -- Sagoma, Rilievo, A giorno -- as SVG sources
and renders them through the project's own rasteriser.

Run from ``tools/assets`` with the pinned venv:

    /home/bober/.venvs/paperscrape-assets/bin/python concepts/skywater/build_skywater_concepts.py

For each concept it writes ``concepts/skywater/<concept>/svg/<name>.svg`` and ``.png`` for the
five sprites the four families are made of -- ``bird_body``, ``cloud_body``, ``dolphin_body``,
``sailboat_hull`` and ``sailboat_sail`` -- and ``sprites.concept.json`` in the registry's own
schema, measured off the rendered pixels.

**Every canvas, scale convention, anchor and tint class is the shipped one**, so no call site,
no constant and no byte of the decoded budget moves: bird 90x24 px in ``CANVAS_PIXELS``, cloud
798x396 px, dolphin 345x174 px, hull 252x51 px and sail 210x180 px in ``SCENE_UNITS`` (3 px per
local unit). The bird and the cloud stay greyscale masks (lightest ``#ffffff``, nothing darker
than 14% below it, so the mean stays above the 220 the tint-class test requires); the three lake
sprites carry their own colours.

Two constraints of the scene are written into the geometry rather than left to the reader:

* **The wing flap is a vertical mirror of the bird's canvas about row 18** (``PaperRenderer``
  blits the bird at ``(-45, -18)`` and scales ``(1, -1)`` for the down-stroke), so the body and
  the head sit on that row and both wings rise above it. Anything drawn below row 18 spends half
  of every flap above the body.
* **Every sprite with a facing points to +x**, which is the only direction the scene moves birds
  and lake decorations today. The sailboat's bow is at +x, so the jib is forward of the mast and
  the mainsail aft of it.

Edges are cut, not struck: every arc is sampled into facets and every vertex carries a small
deterministic wobble written here into the coordinates (the sky's rule since v4.23). The wobble
and the facet length are chosen per sprite from the size it is *drawn* at on a 720x1440 screen,
because a cut that is one pixel wide on the screen is not a cut.
"""
from __future__ import annotations

import hashlib
import json
import math
import sys
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOL_ROOT = HERE.parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import raster  # noqa: E402
from paperscrape_assets.inventory import measure_raster  # noqa: E402

UNIT = 3  # SpriteBlitter.SPRITE_PIXELS_PER_UNIT

# ------------------------------------------------------------------------------ palette
#: Tint masks: white is the MULTIPLY identity; the greys below it are the only shading a
#: tintable sprite may carry, and none of them is darker than 14% below white.
WHITE = "#FFFFFF"
PAPER_1 = "#F6F6F6"
PAPER_2 = "#ECECEC"
UNDER_MASK = "#DCDCDC"

#: Fixed art, the lake family. Warm off-tones for the boat, cool ones for the animal; nothing
#: pure black, nothing pure white on a large fill.
DOLPHIN_BACK = "#4A6A84"
DOLPHIN_BELLY = "#E6EFF4"
DOLPHIN_UNDER = "#2E4457"
BELLY_UNDER = "#B4C9D6"
HULL = "#B85C3E"
HULL_UNDER = "#7E3A26"
DECK_STRIPE = "#F4E9D2"
SAIL = "#FBF4E6"
SAIL_UNDER = "#D8CDB8"
SAIL_BAND = "#E4623E"
MAST = "#8C5A38"


# ------------------------------------------------------------------------------ geometry
def wobble(seed: str, index: int, amplitude: float) -> tuple[float, float]:
    """A deterministic hand-cut wobble for one vertex, written into the coordinates."""
    digest = hashlib.sha256(f"{seed}:{index}".encode()).digest()
    dx = (digest[0] / 255.0 - 0.5) * 2 * amplitude
    dy = (digest[1] / 255.0 - 0.5) * 2 * amplitude
    return dx, dy


def cut(points, seed: str, amplitude: float):
    return [(x + wobble(seed, i, amplitude)[0], y + wobble(seed, i, amplitude)[1])
            for i, (x, y) in enumerate(points)]


def arc(cx, cy, r, t0, t1, step_deg):
    """Points on a circle from angle t0 to t1 (radians, increasing), one every ~step_deg."""
    n = max(2, int(round(abs(t1 - t0) / math.radians(step_deg))))
    return [(cx + r * math.cos(t0 + (t1 - t0) * i / n), cy + r * math.sin(t0 + (t1 - t0) * i / n))
            for i in range(n + 1)]


def circle_pts(cx, cy, r, n=16, start=0.0):
    return [(cx + r * math.cos(start + 2 * math.pi * i / n), cy + r * math.sin(start + 2 * math.pi * i / n))
            for i in range(n)]


def _upper_intersection(a, b):
    """The upper (smaller y) intersection point of two overlapping circles (cx, cy, r)."""
    (x0, y0, r0), (x1, y1, r1) = a, b
    d = math.hypot(x1 - x0, y1 - y0)
    if d >= r0 + r1 or d <= abs(r0 - r1):
        raise ValueError(f"lobes {a} and {b} do not overlap as a chain")
    l = (r0 * r0 - r1 * r1 + d * d) / (2 * d)
    h = math.sqrt(max(r0 * r0 - l * l, 0.0))
    mx, my = x0 + l * (x1 - x0) / d, y0 + l * (y1 - y0) / d
    p1 = (mx + h * (y1 - y0) / d, my - h * (x1 - x0) / d)
    p2 = (mx - h * (y1 - y0) / d, my + h * (x1 - x0) / d)
    return p1 if p1[1] < p2[1] else p2


def _ang(c, p):
    """Angle of p on circle c, normalised into [pi/2, pi/2 + 2pi) so a walk over the top is
    monotone: the left base point is near pi, the crown is at 3pi/2, the right base near 2pi."""
    t = math.atan2(p[1] - c[1], p[0] - c[0])
    while t < math.pi / 2:
        t += 2 * math.pi
    return t


def chain_contour(lobes, base_y, step_deg=11.0, base=None, notch=None):
    """The outline of a row of overlapping lobes sitting on a flat base -- a paper cloud.

    ``lobes`` are (cx, cy, r) left to right; each must overlap the next, and the two end lobes
    must reach ``base_y``. The walk starts where the first lobe meets the base on the left, runs
    over every crown through the cusps where neighbours meet, comes down where the last lobe meets
    the base on the right, and closes along the base. ``base`` may supply the points of a
    non-straight bottom edge, right to left, instead of the straight line. ``notch`` as
    ``(depth, half_angle_deg)`` cuts a V into every cusp: the scissors go on past the point
    where two lobes meet, so the sky shows between them.
    """
    pts = []
    first, last = lobes[0], lobes[-1]
    dy = base_y - first[1]
    if abs(dy) >= first[2] or abs(base_y - last[1]) >= last[2]:
        raise ValueError("an end lobe does not reach the base line")
    start = (first[0] - math.sqrt(first[2] ** 2 - dy * dy), base_y)
    end = (last[0] + math.sqrt(last[2] ** 2 - (base_y - last[1]) ** 2), base_y)
    entry = start
    for k, lobe in enumerate(lobes):
        last = k == len(lobes) - 1
        exit_pt = end if last else _upper_intersection(lobe, lobes[k + 1])
        t0, t1 = _ang(lobe, entry), _ang(lobe, exit_pt)
        if t1 < t0:
            t1 += 2 * math.pi
        d_in = math.radians(notch[1]) if (notch and k > 0) else 0.0
        d_out = math.radians(notch[1]) if (notch and not last) else 0.0
        seg = arc(lobe[0], lobe[1], lobe[2], t0 + d_in, t1 - d_out, step_deg)
        pts.extend(seg if (k == 0 or notch) else seg[1:])
        if notch and not last:
            pts.append((exit_pt[0], exit_pt[1] + notch[0]))
        entry = exit_pt
    if base is not None:
        pts.extend(base(end[0], start[0]))
    return pts


def scalloped_base(y, radius, depth, step_deg=30.0):
    """A bottom edge cut as a run of shallow scallops -- the fringe A giorno gives the band."""
    def build(x_right, x_left):
        pts = []
        span = x_right - x_left
        n = max(2, int(round(span / (2 * radius))))
        w = span / n
        for i in range(n):
            x1 = x_right - i * w
            x0 = x1 - w
            cx = (x0 + x1) / 2
            r = math.hypot(w / 2, depth) ** 2 / (2 * depth) if depth > 0 else w
            cy = y - r + depth
            t_from = math.atan2(y - cy, x1 - cx)
            t_to = math.atan2(y - cy, x0 - cx)
            if t_to < t_from:
                t_to += 2 * math.pi
            seg = arc(cx, cy, r, t_from, t_to, step_deg)
            pts.extend(seg if i == 0 else seg[1:])
        return pts[1:-1]
    return build


def offset(points, dx, dy):
    return [(x + dx, y + dy) for x, y in points]


def strip_along(points, thickness, inset=0.0):
    """A band of constant thickness hanging below a polyline: the deck stripe on the sheer."""
    top = [(x, y + inset) for x, y in points]
    bottom = [(x, y + inset + thickness) for x, y in reversed(points)]
    return top + bottom


# ------------------------------------------------------------------------------ model
@dataclass
class Piece:
    points: list                       # outer contour
    fill: str
    holes: list = field(default_factory=list)   # inner contours, drawn with fill-rule evenodd
    under: str | None = None           # Rilievo: colour of the paper beneath, drawn offset
    under_offset: tuple = (0.0, 0.0)
    clip_to: list | None = None        # a contour this piece is clipped inside (Sagoma's base band)


@dataclass
class Sprite:
    name: str
    width_units: float
    height_units: float
    scale: str
    tint: str
    category: str
    pieces: list = field(default_factory=list)
    view_units: bool = True            # False: authored in pixels (the CANVAS_PIXELS bird)

    def add(self, points, fill, **kw):
        self.pieces.append(Piece(list(points), fill, **kw))
        return self.pieces[-1]


def emit_svg(sprite: Sprite, style: str) -> str:
    if sprite.view_units:
        w, h = sprite.width_units * UNIT, sprite.height_units * UNIT
        vb = f"0 0 {sprite.width_units:g} {sprite.height_units:g}"
    else:
        w, h = sprite.width_units, sprite.height_units
        vb = f"0 0 {w:g} {h:g}"
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w:g}" height="{h:g}" viewBox="{vb}">',
           f"<!-- v4.26 sky-and-water concept '{style}': {sprite.name}. Generated by "
           "tools/assets/concepts/skywater/build_skywater_concepts.py; every facet and its wobble "
           "is written into the coordinates, nothing is computed at runtime. -->"]

    def path_d(contours):
        d = []
        for c in contours:
            d.append("M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in c) + " Z")
        return " ".join(d)

    clips = 0
    for p in sprite.pieces:
        if p.under is not None:
            under = [offset(p.points, *p.under_offset)]
            under_holes = [offset(hh, *p.under_offset) for hh in p.holes]
            out.append("<!-- paperscrape-relief -->")
            out.append(f'<path d="{path_d(under + under_holes)}" fill="{p.under}" fill-rule="evenodd"/>')
        attrs = f'fill="{p.fill}"'
        if p.holes:
            attrs += ' fill-rule="evenodd"'
        if p.clip_to is not None:
            clips += 1
            out.append(f'<clipPath id="clip{clips}"><path d="{path_d([p.clip_to])}"/></clipPath>')
            attrs += f' clip-path="url(#clip{clips})"'
        out.append(f'<path d="{path_d([p.points] + p.holes)}" {attrs}/>')
    out.append("</svg>")
    return "\n".join(out)


# ------------------------------------------------------------------------------ the bird
#: 90x24 canvas pixels, CANVAS_PIXELS, blitted at (-45, -18): the flap axis is row 18. The bird
#: is 88 px across on the screen, so the wobble is half a pixel and the facets are 4-6 px.
BIRD_AXIS = 18.0


def bird_silhouette(seed: str):
    """One gull: two tapered wings swept to a point, a spindle body continuous with the head, a
    short wedge of tail. Nothing under the wings, no elbow in the leading edge, no separate head
    circle -- the three things DESIGN_NOTES says turn this drawing into a bat."""
    pts = [
        (2.0, 3.0), (12.0, 5.2), (23.0, 8.6), (33.0, 12.2), (39.0, 14.2),          # left wing, leading edge
        (46.0, 13.8), (52.0, 13.4),                                                # the back
        (61.0, 10.6), (71.0, 7.0), (80.0, 4.4), (88.0, 3.0),                       # right wing, leading edge
        (80.0, 8.2), (71.0, 11.4), (63.0, 13.4), (58.0, 13.6),                     # right wing, trailing edge
        (63.0, 13.0), (68.0, 14.6), (73.0, 17.6), (68.0, 20.4), (62.0, 21.8),      # head and beak
        (52.0, 22.6), (42.0, 22.4), (34.0, 21.6),                                  # belly
        (16.0, 19.6), (22.0, 17.4), (32.0, 16.4),                                  # tail wedge, on the axis
        (26.0, 13.6), (18.0, 9.6), (9.0, 6.0),                                     # left wing, trailing edge
    ]
    return cut(pts, seed, 0.45)


def bird_body_only(seed: str):
    pts = [(39.0, 14.6), (52.0, 13.6), (60.0, 12.8), (68.0, 14.6), (73.0, 17.6), (68.0, 20.4),
           (62.0, 21.8), (52.0, 22.6), (42.0, 22.4), (34.0, 21.6), (16.0, 19.6), (22.0, 17.4),
           (32.0, 16.4)]
    return cut(pts, seed, 0.4)


def bird_wings_only(seed: str):
    pts = [(2.0, 3.0), (12.0, 5.2), (23.0, 8.6), (33.0, 12.2), (39.0, 14.4), (46.0, 14.0),
           (52.0, 13.8), (61.0, 10.6), (71.0, 7.0), (80.0, 4.4), (88.0, 3.0), (80.0, 8.2),
           (71.0, 11.4), (63.0, 13.6), (56.0, 15.6), (46.0, 16.4), (36.0, 16.8), (30.0, 15.4),
           (26.0, 13.6), (18.0, 9.6), (9.0, 6.0)]
    return cut(pts, seed, 0.45)


def bird(style: str) -> Sprite:
    s = Sprite("bird_body", 90, 24, "CANVAS_PIXELS", "TINTABLE", "animal", view_units=False)
    if style == "sagoma":
        s.add(bird_silhouette("bird-a"), WHITE)
    elif style == "rilievo":
        # Two papers: the body, and the pair of wings laid over it. The under-paper is offset
        # along x only, because the flap mirrors the canvas vertically and a vertical offset
        # would swap sides twice a second.
        s.add(bird_body_only("bird-b-body"), WHITE, under=UNDER_MASK, under_offset=(1.6, 0.0))
        s.add(bird_wings_only("bird-b-wings"), PAPER_1, under=UNDER_MASK, under_offset=(1.6, 0.0))
    else:
        # The sky shows through: an eye cut out of the head, and a lens cut out of each wing.
        # Every opening is at least 2.5 px on the screen, which is what survives the sampler.
        eye = circle_pts(64.0, 17.4, 1.4, 8)
        left_lens = cut([(12.0, 7.4), (20.0, 8.6), (28.0, 11.2), (26.0, 12.6), (18.0, 10.8), (10.5, 8.6)], "bird-c-l", 0.2)
        right_lens = cut([(78.0, 6.2), (70.0, 8.4), (63.0, 11.0), (64.5, 12.2), (71.5, 10.2), (79.5, 7.4)], "bird-c-r", 0.2)
        s.add(bird_silhouette("bird-c"), WHITE, holes=[eye, left_lens, right_lens])
    return s


# ------------------------------------------------------------------------------ the cloud
#: 266x132 local units, drawn at 0.72-1.44 of that on the screen. A unit is about a pixel, so
#: the wobble is 1.2 units and the facets 8-10 -- the moon's own proportions (v4.23).
CLOUD_W, CLOUD_H = 266.0, 132.0


def cloud(style: str) -> Sprite:
    s = Sprite("cloud_body", CLOUD_W, CLOUD_H, "SCENE_UNITS", "TINTABLE", "sky")
    if style == "sagoma":
        # One paper. Six lobes of six sizes with the crown left of centre, so a row of copies at
        # four scales has no beat in it, and a flat base. The lower band is the same paper in
        # shade -- a second tone, not a second piece -- clipped to the silhouette.
        lobes = [(34, 96, 30), (72, 76, 42), (118, 62, 50), (166, 68, 46), (206, 84, 38), (236, 102, 25)]
        base_y = 124.0
        contour = cut(chain_contour(lobes, base_y), "cloud-a", 1.2)
        s.add(contour, WHITE)
        band = [(0, base_y - 20), (CLOUD_W, base_y - 20), (CLOUD_W, base_y + 2), (0, base_y + 2)]
        s.add(band, PAPER_2, clip_to=contour)
    elif style == "rilievo":
        # Three papers stacked like the oak's three bands of foliage: a wide low base, a middle
        # paper, a small crown, each with its own darker paper beneath, offset down and right.
        base = [(30, 110, 22), (66, 104, 27), (104, 102, 29), (144, 104, 29), (182, 102, 28), (218, 106, 26), (246, 112, 19)]
        mid = [(56, 80, 34), (102, 68, 42), (150, 72, 40), (196, 84, 32)]
        top = [(100, 50, 32), (140, 40, 38), (182, 50, 32), (214, 66, 22)]
        s.add(cut(chain_contour(base, 126.0), "cloud-b-base", 1.2), PAPER_2, under=UNDER_MASK, under_offset=(3.0, 4.0))
        s.add(cut(chain_contour(mid, 108.0), "cloud-b-mid", 1.2), PAPER_1, under=UNDER_MASK, under_offset=(3.0, 4.0))
        s.add(cut(chain_contour(top, 80.0), "cloud-b-top", 1.2), WHITE, under=UNDER_MASK, under_offset=(3.0, 4.0))
    else:
        # One paper cut through: openings just under the cusps between the crowns, where the
        # top edge of the band is the only place another cloud is not behind this one, and a
        # base cut as a run of scallops so the band's lower edge is a fringe, not a ruler.
        lobes = [(32, 98, 27), (70, 72, 41), (116, 56, 49), (164, 62, 46), (206, 78, 37), (236, 98, 24)]
        base_y = 120.0
        # Every cusp is cut on into a V 18 units deep and about 8 wide at the mouth -- the
        # scissors went past the point where two lobes meet -- so the sky shows between the
        # crowns along the top edge of the band, the one edge no other cloud stands behind.
        contour = cut(chain_contour(lobes, base_y, base=scalloped_base(base_y, 15.0, 5.0), notch=(18.0, 5.0)), "cloud-c", 1.2)
        s.add(contour, WHITE)
    return s


# ------------------------------------------------------------------------------ the dolphin
#: 115x58 local units, drawn at 0.2857 of that on the BV6600: 33 px nose to fluke. Only the
#: silhouette survives that, so the animal is a body with a swept dorsal fin, a pectoral fin, a
#: forked tail and a beak, and a belly of lighter paper -- no eye but where it is cut through.
def dolphin_outline(seed: str, with_fins=True):
    upper = [(14.0, 25.5), (24.0, 21.0), (36.0, 17.4), (48.0, 15.2)]
    dorsal = [(54.0, 14.4), (52.0, 9.5), (53.0, 4.5), (57.0, 2.5), (62.0, 5.5), (66.5, 10.5), (70.0, 13.6)]
    head = [(82.0, 13.6), (94.0, 15.2), (102.0, 18.2), (107.0, 22.4), (110.0, 26.4), (114.0, 29.0), (113.0, 31.4),
            (110.0, 31.8), (106.0, 33.6), (99.0, 37.0), (90.0, 40.6)]
    pectoral = [(84.0, 42.4), (80.0, 49.0), (74.0, 53.0), (72.0, 47.0), (70.0, 43.6)]
    belly = [(58.0, 43.0), (46.0, 40.6), (34.0, 36.6), (24.0, 33.4), (14.0, 32.5)]
    flukes = [(3.0, 40.0), (9.0, 29.0), (2.0, 18.0)]
    pts = upper + (dorsal if with_fins else [(56.0, 14.8), (68.0, 13.8)]) + head + \
        (pectoral if with_fins else [(82.0, 42.2), (70.0, 43.4)]) + belly + flukes
    return cut(pts, seed, 1.2)


def dolphin_belly(seed: str):
    pts = [(28.0, 33.4), (42.0, 34.4), (58.0, 36.0), (74.0, 37.6), (88.0, 38.2), (100.0, 35.8),
           (90.0, 40.6), (84.0, 42.4), (70.0, 43.6), (58.0, 43.0), (46.0, 40.6), (34.0, 36.6)]
    return cut(pts, seed, 1.0)


def dolphin(style: str, back: str = DOLPHIN_BACK, under: str = DOLPHIN_UNDER,
            belly_under: str = BELLY_UNDER) -> Sprite:
    """[back] and [under] default to the palette the concept rounds were photographed with.
    v4.26 ships a lighter pair, derived in `LakeContrastTest`; the concepts keep theirs so the
    photographs stay reproducible."""
    s = Sprite("dolphin_body", 115, 58, "SCENE_UNITS", "FIXED_ART", "lake")
    if style == "sagoma":
        s.add(dolphin_outline("dolphin-a"), back)
        s.add(dolphin_belly("dolphin-a-belly"), DOLPHIN_BELLY)
    elif style == "rilievo":
        # Body, belly, dorsal fin, pectoral fin and flukes as five papers. The under-paper is
        # offset by 5.5 units -- 1.6 px at the size the animal is drawn -- because the two units
        # the people use would be half a pixel here and would not exist.
        off = (5.5, 5.5)
        flukes = cut([(15.0, 25.5), (15.0, 32.5), (3.0, 40.0), (9.0, 29.0), (2.0, 18.0)], "dolphin-b-fl", 1.0)
        s.add(flukes, back, under=under, under_offset=off)
        s.add(dolphin_outline("dolphin-b", with_fins=False), back, under=under, under_offset=off)
        dorsal = cut([(52.0, 15.6), (52.0, 9.5), (53.0, 4.5), (57.0, 2.5), (62.0, 5.5), (66.5, 10.5), (71.0, 14.6)], "dolphin-b-d", 1.0)
        s.add(dorsal, back, under=under, under_offset=off)
        pect = cut([(85.0, 41.0), (80.0, 49.0), (74.0, 53.0), (72.0, 47.0), (70.0, 42.5)], "dolphin-b-p", 1.0)
        s.add(pect, back, under=under, under_offset=off)
        s.add(dolphin_belly("dolphin-b-belly"), DOLPHIN_BELLY, under=belly_under, under_offset=(3.0, 3.0))
    else:
        # The eye is cut through the paper: 9 units across, 2.6 px on the screen, the only
        # opening this sprite can afford. Everything else is Sagoma's silhouette.
        eye = circle_pts(100.0, 22.5, 4.5, 10, start=0.3)
        s.add(dolphin_outline("dolphin-c"), back, holes=[eye])
        s.add(dolphin_belly("dolphin-c-belly"), DOLPHIN_BELLY)
    return s


# ------------------------------------------------------------------------------ the sailboat
#: Hull 84x17 units, sail 70x60, both drawn at 0.975 of that: a unit is a pixel. The boat point
#: is hull x=42 (origin -42) and sail x=35 (origin -35); the sail's bottom row is boat y=+10, the
#: hull's top row boat y=+8. The bow is at +x. The sheer rises toward it inside the 17 units.
SHEER = [(3.0, 4.6), (18.0, 3.4), (40.0, 2.2), (62.0, 1.2), (78.0, 0.4), (84.0, 0.0)]


def hull_outline(seed: str):
    pts = SHEER + [(80.0, 9.0), (74.0, 17.0), (12.0, 17.0), (5.0, 11.0)]
    return cut(pts, seed, 0.5)


def deck_stripe(seed: str):
    return cut(strip_along(SHEER[:-1] + [(82.0, 0.3)], 2.4, inset=1.4), seed, 0.25)


def hull(style: str) -> Sprite:
    s = Sprite("sailboat_hull", 84, 17, "SCENE_UNITS", "FIXED_ART", "lake")
    if style == "sagoma":
        s.add(hull_outline("hull-a"), HULL)
        s.add(deck_stripe("hull-a-stripe"), DECK_STRIPE)
    elif style == "rilievo":
        s.add(hull_outline("hull-b"), HULL, under=HULL_UNDER, under_offset=(2.0, 2.0))
        s.add(deck_stripe("hull-b-stripe"), DECK_STRIPE, under=SAIL_UNDER, under_offset=(1.5, 1.5))
    else:
        ports = [circle_pts(28.0, 10.6, 2.6, 8), circle_pts(40.0, 10.2, 2.6, 8), circle_pts(52.0, 9.8, 2.6, 8)]
        s.add(hull_outline("hull-c"), HULL, holes=ports)
        s.add(deck_stripe("hull-c-stripe"), DECK_STRIPE)
    return s


MAST_X = 43.0


def mainsail(seed: str, gap=0.0):
    x = MAST_X - 1.6 - gap
    pts = [(x, 3.0), (x, 57.0), (10.0 - gap * 0.2, 57.0), (13.0, 44.0), (20.0, 30.0), (30.0, 15.0)]
    return cut(pts, seed, 0.6)


def jib(seed: str, gap=0.0):
    x = MAST_X + 1.6 + gap
    pts = [(x, 9.0), (54.0, 30.0), (62.0, 46.0), (67.0, 57.0), (x, 57.0)]
    return cut(pts, seed, 0.6)


def sail(style: str) -> Sprite:
    s = Sprite("sailboat_sail", 70, 60, "SCENE_UNITS", "FIXED_ART", "lake")
    mast = [(MAST_X - 1.6, 0.0), (MAST_X + 1.6, 0.0), (MAST_X + 1.6, 60.0), (MAST_X - 1.6, 60.0)]
    band = [(11.6, 50.0), (MAST_X - 1.6, 50.0), (MAST_X - 1.6, 54.4), (10.7, 54.4)]
    if style == "sagoma":
        s.add(mast, MAST)
        s.add(mainsail("sail-a"), SAIL)
        s.add(cut(band, "sail-a-band", 0.3), SAIL_BAND)
        s.add(jib("sail-a-jib"), SAIL)
    elif style == "rilievo":
        s.add(mast, MAST, under=HULL_UNDER, under_offset=(1.5, 0.0))
        s.add(mainsail("sail-b"), SAIL, under=SAIL_UNDER, under_offset=(2.0, 2.0))
        s.add(cut(band, "sail-b-band", 0.3), SAIL_BAND)
        boom = [(9.0, 56.2), (MAST_X + 1.6, 56.2), (MAST_X + 1.6, 59.4), (9.0, 59.4)]
        s.add(boom, MAST, under=HULL_UNDER, under_offset=(1.5, 1.0))
        s.add(jib("sail-b-jib"), SAIL, under=SAIL_UNDER, under_offset=(2.0, 2.0))
    else:
        # The light between the two sails is real: each stands 3 units off the mast, and the
        # mainsail carries a window cut through it, the way a racing sail does.
        window = cut([(20.0, 36.0), (32.0, 34.0), (34.0, 40.0), (23.0, 43.0)], "sail-c-win", 0.3)
        s.add(mast, MAST)
        s.add(mainsail("sail-c", gap=3.0), SAIL, holes=[window])
        s.add(cut([(12.5, 50.0), (MAST_X - 4.6, 50.0), (MAST_X - 4.6, 54.4), (11.6, 54.4)], "sail-c-band", 0.3), SAIL_BAND)
        s.add(jib("sail-c-jib", gap=3.0), SAIL)
    return s


# ------------------------------------------------------------------------------ build
STYLES = {
    "sagoma": "Concept A 'Sagoma' -- one paper per object: the silhouette alone carries the identity, at most two flat tones and no shadow offset, no outline.",
    "rilievo": "Concept B 'Rilievo' -- the oak's and the people's recipe: two to five papers stacked, each on a darker paper beneath it offset down and right, the offset sized to the pixels the sprite is drawn at.",
    "agiorno": "Concept C 'A giorno' -- the paper is cut through: openings inside the silhouette let the sky or the water show, never narrower than 2.5 px on the screen.",
}

NOTES = {
    "bird_body": "Flap axis is canvas row 18 (blit origin -18): body and head on it, wings above. Faces +x, the only way birds fly. Same 90x24 canvas and CANVAS_PIXELS convention as shipped.",
    "cloud_body": "Content centred on the shipped 798x396 canvas; the flat or fringed base sits at row 372 of 396. Re-derive CloudCoverage.CLOUD_CONTENT_HALF_UNITS from the content box before shipping.",
    "dolphin_body": "Faces +x, the only way lake decorations drift. Content centred so DOLPHIN_ORIGIN_X/Y_UNITS (-57.3, -29) still land the animal on its leap point.",
    "sailboat_hull": "Bow at +x: the sheer rises from 4.6 units at the stern to the prow at row 0 inside the shipped 84x17 canvas, so the origin (-42, 8) is unchanged.",
    "sailboat_sail": "Mast at x=43 of 70, forward of amidships; jib forward (+x), mainsail aft. Foot at row 57 so the boom sits on the deck (boat y=+7..+10). Origin (-35, -50) unchanged.",
}


def build(style: str) -> None:
    out = HERE / style / "svg"
    out.mkdir(parents=True, exist_ok=True)
    entries = []
    for sprite in (bird(style), cloud(style), dolphin(style), hull(style), sail(style)):
        svg = emit_svg(sprite, style)
        (out / f"{sprite.name}.svg").write_text(svg + "\n", encoding="utf-8")
        r = raster.render_svg(svg)
        if sprite.view_units:
            expected = (int(sprite.width_units * UNIT), int(sprite.height_units * UNIT))
        else:
            expected = (int(sprite.width_units), int(sprite.height_units))
        if r.size != expected:
            raise SystemExit(f"{sprite.name}: rendered {r.size}, expected {expected}")
        (out / f"{sprite.name}.png").write_bytes(r.png_bytes)
        m = measure_raster(sprite.name, r)
        entries.append({
            "name": sprite.name, "category": sprite.category,
            "width": expected[0], "height": expected[1], "contentBox": list(m.content_bbox),
            "scale": sprite.scale, "tint": sprite.tint, "usage": "concept",
            "anchorRule": "PART_LOCAL", "anchor": [0, 0], "season": "all",
            "source": {"kind": "svg", "file": f"{sprite.name}.svg"},
            "notes": STYLES[style] + " " + NOTES[sprite.name],
        })
        print(f"  {style:8s} {sprite.name:14s} {expected[0]}x{expected[1]} content {list(m.content_bbox)}")
    reg = {"schemaVersion": 2,
           "note": f"Registry of the v4.26 sky-and-water concept '{style}': the shipped registry's own schema, kept here because these sprites are NOT shipped. Canvases, scale conventions, anchor rules and tint classes are the shipped ones, so no call site changes.",
           "sprites": entries}
    (HERE / style / "sprites.concept.json").write_text(json.dumps(reg, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    for style in (sys.argv[1:] or list(STYLES)):
        build(style)
