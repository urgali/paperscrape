#!/usr/bin/env python3
"""The Headless Horseman, his rider in three pieces with the arm that throws: the rider of Sleepy Hollow on his black
horse, drawn from one geometry.

The drawing is `DESIGN_NOTES.md` §12, decision 79: the classic rider, his falling pumpkins on fire (the flames are drawn
by the renderer, `HeadlessHorsemanEffect`; the pumpkin he throws is his own, drawn here), the rider as tall as Santa; and
he throws the pumpkin from his hand, with the arm's gesture (decision 84). This is *the rider of Sleepy Hollow*: a
slate-black horse with a smoke-grey mane and tail, galloping; the headless rider in a dark coat with gold buttons and a high cream collar round the
empty neck, his black cape lined in red flying back from his shoulders, the carved pumpkin lit from inside and held up
high in his hand; the reins from his other hand to the horse's head.

The pieces, all fixed art and `SCENE_UNITS` (3 px a unit), each trimmed to its content on the 3x grid, all `PART_LOCAL`
(``HorsemanTable`` says where each stands):

* ``horseman_horse_0`` .. ``_3`` -- the horse without its legs (body, neck and head, mane, tail, saddle cloth and saddle)
  in the four poses of a flying gallop (the head's nod and the body's pitch are the pose's), facing left, its body's
  middle at (0, 0): the group flies left, as the sleigh does, and the renderer mirrors it to fly right;
* ``horseman_legs_0`` .. ``_3`` -- its four legs in the same four poses, drawn behind the body, the far ones in the body's
  shadow tone;
* the rider in three pieces, so he throws from his hand with the arm's gesture (decision 84), laid in this order and,
  with the arm unturned, the rider holding his pumpkin up:
  ``horseman_rider_body`` -- his seat at (0, 0): his near leg down the horse's side, his coat, the collar, the round of his
  near shoulder ([SHOULDER_CAP]) and the arm reaching for the reins --; ``horseman_rider_arm`` -- the near arm and its
  glove, in a frame a whole unit from its pivot at the shoulder ([ARM_PIVOT]), which the renderer turns to throw --; and
  ``horseman_rider_pumpkin`` -- the carved pumpkin lit from inside, in a frame a whole unit from its middle, drawn in his
  hand and, thrown, alone. The whole units keep each piece's pixels where the rider's in one piece would be;
* ``horseman_cape`` -- his cape, its pivot at his shoulders (0, 0), which the renderer swings a little with the stride;
* the reins are a cord the renderer draws from the rider's hand to the horse's head ([BIT]), between the horse and the
  rider (`HeadlessHorsemanDrawer`: the cape, the legs, the horse, the reins, the rider).

**The size** is the sleigh's rule (decision 73): the rider, what is seen of him from the top of his collar to the bottom
of his boot (measured off the drawing, [rider]), is [RIDER_TO_PEDESTRIAN] times an adult walking on the near row of the
pavement, as Santa is -- about 49 px on the BV6600 -- so the group is drawn at `RIDER_TO_PEDESTRIAN x pedestrian / the
rider's height` px a unit there (1.235). **Every band of colour is [Size.band] units or more** -- 2 px on the BV6600 at
that size and a quarter more, the sleigh's rule, `round(2.0 / px_per_unit x 1.25, 2)` --, the under-papers' relief
included, and the reins are 2.5 px or more ([Size.rein]).

What the size asks of the drawing: every band drawn at the narrowest width is [Size.band] units, 2.02 at the shipped
size (the under-papers of the body, the coat, the collar, the cape and the pumpkin, the saddle cloth's trim, the
stirrup, the pumpkin's stem); and four details are drawn so that at this size and at every moment of the gallop -- the
horse rising and falling with its stride while the rider sits it the other way, so the two move 1.8 units against each
other, and the cape swinging -- none leaves a piece of some colour under 2 px:

* **the coat's buttons**: two ([BUTTONS]), spaced evenly between the collar's shadow and the belt -- under the shadow,
  2.02 units deep, a third button at the top would be covered down to 1.5 units;
* **the coat's skirt** hangs low at its back ([SKIRT]), so the saddle never shows under it as a sliver behind him when
  the rider rises out of the saddle (in front of the coat a scrap of it, under the 12 px of the 3x drawing every 2 px
  test leaves out as an edge's speck, can show at one moment of the gallop);
* **the reins reach the head high**, at [BIT]: reaching it at the corner of the mouth, along the lower jaw they would
  leave a strip of the head under 2 px between the rein and the jaw's edge, and at its front they would cut the muzzle
  in two;
* **the pumpkin's carved face** ([FACE]): its two eyes are slanted quadrilaterals, and the grin with its two teeth is
  small and low, so each eye and the grin hold 2 px and so does the pumpkin's paper between any two of them. The
  pumpkin's ring of paper round the grin is under 2 px at its bottom, as Santa's skin between his eyes and his hat's band
  is (decision 73): no rule holds it.

And the rider's own frame is a unit above the drawing's ([RIDER_FRAME_DY]): his seat ([SEAT]) lies on the saddle's
paper, about half a unit below its top edge (0.43 to 0.66 in the four poses: the seat turns with the body's pitch), and
everything of him is drawn a unit higher in his frame, so he is drawn sitting on top of the saddle.

**The shoulder, drawn for the throw** (decision 84). The arm, cut out of the rider and turned to its gesture's angles,
uncovers what lies under it, and without the shoulder three slivers under 2 px on the BV6600 would show there: the
collar's under-paper beside it from the arm at rest to its wind-up, the cape behind it wound right back, and -- thrown
forward, the arm over the collar's right side -- the thin crescent of the collar's inside over the neck, left alone. So
the near shoulder is a round of coat over the collar's right end and the cape behind it, from the pivot up to the
collar's top ([SHOULDER_CAP]), and the dark of the neck is 7.8 units across ([NECK_HOLE]), so the collar's inside holds
2 px on its left side alone; at rest the arm covers most of the round.

It writes, from one run (and, with ``--res``, the twelve PNGs rendered from the SVGs):

* ``sources/svg/horseman_horse_0..3.svg``, ``horseman_legs_0..3.svg``, ``horseman_rider_body.svg``,
  ``horseman_rider_arm.svg``, ``horseman_rider_pumpkin.svg`` and ``horseman_cape.svg``;
* ``app/.../engine/HorsemanTable.kt`` -- where each piece blits, the seat and the bit in each pose, the rider's hand, his
  shoulders, his arm's pivot and his pumpkin's middle and radius, the group's extent, the rider's height and the size
  chosen, the reins' width and the narrowest band.

``--check`` writes nothing and fails, naming every stale file, unless every file this writes is what it draws
(``tests/test_horseman.py`` makes the same comparison). ``--two-px`` composes the group as the renderer draws it -- in its
order, the cape swung and the horse and the rider bobbing by the drawer's own constants, at the moments of a stride
[moments] names, the reins included, the near arm turned to every angle of the throw the flight's constants give
([gesture_angles], every degree) with the pumpkin in its hand -- from the SVGs rendered without antialiasing, and lists
every piece of one colour that holds no square of 2 px on the BV6600 anywhere (the pumpkin's papers, turned with the arm, no
disc 2 px across: [thin_pieces]). ``--rider-to-pedestrian R --out DIR`` (or ``--group-to-pedestrian G
--out DIR``: the size at which the whole group, its lowest hoof to the top of the pumpkin's stem, is G pedestrians tall)
writes into DIR, and nothing into the tree, the whole set drawn for that size -- every band drawn at the narrowest width
thickened to that size's own (what is drawn at a size of its own, as the coat's, the collar's and the face's shapes, is
not) --: the PNGs, the SVGs, the table's numbers and the size's measures as ``horseman.json``, and the ``--two-px`` report,
which says what is then under 2 px, as ``two_px.txt``.

Usage, from ``tools/assets`` with the venv's interpreter:

    python horseman/build_horseman.py [--res] [--check] [--two-px]
    python horseman/build_horseman.py (--rider-to-pedestrian R | --group-to-pedestrian G) --out DIR
"""
from __future__ import annotations

import io
import json
import math
import re
import sys
from collections import deque
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent
sys.path.insert(0, str(ASSETS))
from papercut import (Sheet, capsule, content_box, cut, darker, ellipse, move, polyline_band,  # noqa: E402
                      render_body, rotate, svg_document)
from paperscrape_assets.raster import render_svg, same_output  # noqa: E402

REPO = ASSETS.parent.parent
SVG_DIR = ASSETS / "sources" / "svg"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
ENGINE = REPO / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / "engine"
TABLE = ENGINE / "HorsemanTable.kt"
#: The renderer that draws the group: its motion is read from it for the ``--two-px`` composition.
DRAWER = ENGINE / "HeadlessHorsemanDrawer.kt"
PX = 3  # SpriteBlitter.SPRITE_PIXELS_PER_UNIT

# ------------------------------------------------------------------ the size, and what it asks of the drawing
#: How tall the rider is drawn -- the top of his collar to the bottom of his boot, what is seen of him -- against an adult
#: walking on the near row of the pavement: Santa's (`SantaSleighTable.SANTA_TO_PEDESTRIAN`),
#: `DESIGN_NOTES.md` §12, decision 79.
RIDER_TO_PEDESTRIAN = 1.3

#: An adult walking on the near row of the pavement on the BV6600, in px: `SceneSpace.PERSON_METRES_TALL` (1.75 m) at
#: `PIXELS_PER_METRE_AT_REFERENCE` (45 px/m on a 2 400 px screen, x0.6 for its 1 440), at the perspective of
#: `PAVEMENT_NEAR_Y_FRACTION` (0.807: (0.807 - 0.655) / (0.846 - 0.655)) -- `SantaSleighScale.pedestrianPx` there.
PEDESTRIAN_PX_BV6600 = 1.75 * 45.0 * (1440 / 2400) * ((0.807 - 0.655) / (0.846 - 0.655))

#: The reins' width, in units, at the shipped size (3 px on the BV6600); never under 2.5 px at any size ([Size.rein]).
REIN_UNITS = 2.4


@dataclass(frozen=True)
class Size:
    """The size the group is drawn for, and what it asks of the drawing: [ratio], the rider against a pedestrian;
    [px_per_unit] on the BV6600; [band], the narrowest band in units (2 px there and a quarter more); [rein], the reins'
    width in units (2.5 px there or more)."""
    ratio: float
    px_per_unit: float
    band: float
    rein: float

    def thick(self, units: float) -> float:
        """A band's width, or an under-paper's drop: as drawn, but never under [band]."""
        return max(units, self.band)

    def drop(self, dx: float, dy: float) -> tuple[float, float]:
        return self.thick(dx), self.thick(dy)

    def half(self, radius: float) -> float:
        """A round piece's radius: as drawn, but never under half of [band]."""
        return max(radius, self.band / 2)


def size_for(ratio: float) -> Size:
    """The size for a rider [ratio] times a pedestrian: the px a unit from the rider's height as drawn at that size's own
    narrowest band, found again until the band no longer moves it (it does not today: the collar's top and the boot's
    bottom are no band)."""
    band = 0.0
    for _ in range(20):
        _, p = rider(Size(ratio, 1.0, band, REIN_UNITS))
        px_per_unit = ratio * PEDESTRIAN_PX_BV6600 / (p["bottom"] - p["top"])
        new = round(2.0 / px_per_unit * 1.25, 2)
        if new == band:
            break
        band = new
    rein = max(REIN_UNITS, math.ceil(2.5 / px_per_unit * 100) / 100)
    return Size(ratio, px_per_unit, band, rein)


# ------------------------------------------------------------------ the papers
HORSE = "#3E3D4C"
BELLY = "#4F4E5C"
MANE = "#7C798C"
HOOF = "#1B1A20"
EYE = "#E8562A"
CLOTH = "#5A2A44"
TRIM = "#E07A2E"
SADDLE = "#5A3324"
COAT = "#2F3B4E"
COAT_DARK = "#232C3B"
BUTTON = "#C9A24A"
COLLAR = "#E9E1CF"
NECK = "#1A1418"
GLOVE = "#1E1B20"
BOOT = "#1E1B20"
TROUSER = "#3A3440"
STIRRUP = "#9A958A"
CAPE = "#1F1D27"
LINING = "#B3282C"
PUMPKIN = "#E8862A"
STEM = "#3F6A33"
LIGHT = "#FFD54F"
REIN_COLOUR = "#2A1E18"
#: The head's two papers, where the reins end: the head and its muzzle.
MUZZLE = darker(HORSE, 0.86)
#: The collar's three papers -- its back inside the neck, its shadow and the band --, whose top is the rider's.
COLLAR_PAPERS = (darker(COLLAR, 0.86), darker(COLLAR, 0.78), COLLAR)

# ------------------------------------------------------------------ the horse
#: The four poses of a flying gallop -- the reindeer's (`sleigh/build_sleigh.py`): (front upper, front lower), (back
#: upper, back lower) in degrees from straight down, positive towards the tail; the head's nod, the body's pitch and how
#: far it rises (up negative); in pose 0 the far front leg turns further from the near one, so it shows beside it.
POSES = (
    dict(f=(-58, -78), b=(58, 74), nod=-4, pitch=-2.0, rise=0.0, fsplay=12),
    dict(f=(-22, 30), b=(30, -14), nod=0, pitch=1.0, rise=-1.0),
    dict(f=(14, 52), b=(-10, -38), nod=4, pitch=2.5, rise=-1.8),
    dict(f=(-40, -12), b=(44, 34), nod=1, pitch=0.5, rise=-0.6),
)
#: The body's half-length and half-height, in units.
RX, RY = 17.0, 9.6
#: A leg: upper length, lower length, upper width, lower width.
LEG = (10.0, 9.6, 5.4, 3.6)
SHOULDER, HIP = (-11.0, 3.2), (11.0, 2.4)
#: How far the far legs stand from the near ones, in units, and how far they turn from them, in degrees.
FAR_SHIFT, FAR_SPLAY = 5.2, 4
#: Where the neck joins the body: the head nods about it.
NECK_ROOT = (-11.0, -4.0)
#: Where the reins reach the horse's head, before the nod: on the head's paper behind the muzzle, high enough that the
#: rein leaves the lower jaw a band of 2 px or more under it behind the bit at every moment of the gallop (toward the throat
#: it narrows to nothing where the jaw's line meets the rein, as an edge meeting a line does).
BIT = (-28.8, -14.4)
#: Where the rider sits: on the saddle's paper, about half a unit below its top edge (0.43 to 0.66 in the four poses).
SEAT = (-1.0, -9.4)


def leg_dir(a):
    r = math.radians(a)
    return math.sin(r), math.cos(r)


def leg(z: Size, s: Sheet, x, y, ua, la, colour, hoof, seed):
    """A leg from (x, y): the upper leg at [ua] degrees from straight down, the lower at [la], and its hoof."""
    lu, ll, wu, wl = LEG
    dx, dy = leg_dir(ua)
    kx, ky = x + dx * lu, y + dy * lu
    ex, ey = leg_dir(la)
    fx, fy = kx + ex * ll, ky + ey * ll
    s.add(cut(capsule(x, y, kx, ky, z.thick(wu), z.thick(wu * 0.86)), seed + "u", 0.1), colour)
    s.add(cut(capsule(kx, ky, fx, fy, z.thick(wl + 0.6), z.thick(wl)), seed + "l", 0.1), colour)
    hx, hy = fx + ex * 2.2, fy + ey * 2.2
    s.add(cut(capsule(fx, fy, hx, hy, z.thick(wl + 0.8), z.thick(wl + 1.0), n=3), seed + "h", 0.08), hoof)


def legs(z: Size, pose: int) -> tuple[Sheet, float]:
    """The four legs in pose [pose], in the body's frame (its middle at 0, 0), the far ones behind and darker; and the
    lowest point of a hoof."""
    q = POSES[pose]
    s = Sheet()
    far = darker(HORSE, 0.72)
    fa, fb = q["f"], q["b"]

    def T(pts):
        return move(rotate(pts, 0.0, 0.0, q["pitch"]), 0.0, q["rise"])

    tmp = Sheet()
    ua, ub = math.radians(fa[0]), math.radians(fb[0])
    leg(z, tmp, SHOULDER[0] - FAR_SHIFT * math.cos(ua), SHOULDER[1] + FAR_SHIFT * math.sin(ua) - 0.8,
        fa[0] + q.get("fsplay", FAR_SPLAY), fa[1] + q.get("fsplay", FAR_SPLAY), far, far, f"h{pose}ff")
    leg(z, tmp, HIP[0] + FAR_SHIFT * math.cos(ub), HIP[1] - FAR_SHIFT * math.sin(ub) - 0.8,
        fb[0] - FAR_SPLAY, fb[1] - FAR_SPLAY, far, far, f"h{pose}fb")
    leg(z, tmp, *SHOULDER, *fa, HORSE, HOOF, f"h{pose}nf")
    leg(z, tmp, *HIP, *fb, HORSE, HOOF, f"h{pose}nb")
    lowest = -1e9
    for pts, c, _ in tmp.pieces:
        moved = T(pts)
        s.add(moved, c)
        lowest = max(lowest, max(y for _, y in moved))
    return s, lowest


def horse(z: Size, pose: int):
    """The horse without its legs in pose [pose], facing left, its middle at (0, 0); and its points in that frame: the
    bit (the reins' end) and the seat (the rider's)."""
    q = POSES[pose]
    s = Sheet()

    def T(pts):
        return move(rotate(pts, 0.0, 0.0, q["pitch"]), 0.0, q["rise"])

    # The tail, behind the body: a band flowing back and down, and a lock beside it.
    tail_root = (RX * 0.92, -RY * 0.55)
    tail = [tail_root, (RX + 5.0, -RY * 0.7), (RX + 9.6, -RY * 0.2), (RX + 12.0, 3.6), (RX + 12.6, 8.4)]
    s.add(T(cut(polyline_band(tail, z.thick(4.8)), "htail", 0.22)), MANE)
    s.add(T(cut(polyline_band([(RX + 6.0, -RY * 0.6), (RX + 11.4, -RY * 0.1), (RX + 14.4, 4.0)], z.thick(2.6)), "htail2", 0.12)), MANE)
    # The body over its under-paper, the chest and the rump rounding it, and the belly's lighter paper.
    body = cut(ellipse(0, 0, RX, RY, 26, -2), "hbody", 0.25)
    chest = cut(ellipse(-RX * 0.72, 0.6, 8.0, 8.6, 16), "hchest", 0.18)
    rump = cut(ellipse(RX * 0.7, -0.8, 8.4, 9.4, 16), "hrump", 0.18)
    for p in (body, chest, rump):
        s.add(T(move(p, *z.drop(0.0, 1.4))), darker(HORSE, 0.74))
    for p in (body, chest, rump):
        s.add(T(p), HORSE)
    s.clip(f"hbodyc{pose}", [T(body), T(chest), T(rump)])
    s.add(T(cut(ellipse(0.6, RY * 0.72, RX * 0.7, RY * 0.42, 16), "hbelly", 0.18)), BELLY, clip=f"hbodyc{pose}")
    # The neck and the head, nodding with the stride about the neck's root: the neck arching up from the chest, the long
    # head pointing down and forward, the muzzle, the ear, the eye, the nostril; the mane along the neck's top in locks.
    head = Sheet()
    nx, ny = NECK_ROOT
    neck = [(-20.6, -23.6), (-15.4, -20.6), (-10.4, -15.4), (-5.6, -9.4), (-8.4, -3.6), (-15.6, 1.8), (-19.0, -5.0),
            (-20.4, -12.0), (-20.0, -17.6)]
    head.add(cut(neck, "hneck", 0.2), HORSE)
    skull = cut([(-20.6, -23.4), (-24.4, -23.8), (-27.4, -21.2), (-31.0, -16.2), (-33.6, -12.6), (-32.8, -10.2), (-29.8, -9.6),
                 (-25.6, -11.4), (-21.6, -13.8), (-19.0, -17.4)], "hhead", 0.15)
    head.add(skull, HORSE)
    head.add(cut([(-29.4, -16.0), (-31.0, -16.2), (-33.6, -12.6), (-32.8, -10.2), (-29.8, -9.6), (-28.6, -12.6)], "hmuzzle", 0.08), MUZZLE)
    head.add(cut([(-21.8, -22.4), (-21.4, -30.0), (-18.0, -22.6)], "hear", 0.06), darker(HORSE, 0.9))
    head.add(ellipse(-24.8, -19.8, z.half(1.4), z.half(1.5), 10), EYE)
    head.add(ellipse(-31.8, -12.6, z.half(1.3), z.half(1.3), 10), darker(HORSE, 0.5))
    mane_line = [(-19.0, -23.0), (-15.6, -21.0), (-11.0, -16.0), (-6.6, -10.4)]
    head.add(cut(polyline_band(mane_line, z.thick(3.6)), "hmane", 0.15), MANE)
    for i in range(3):
        (x0, y0), (x1, y1) = mane_line[i], mane_line[i + 1]
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        head.add(cut([(mx - 1.6, my - 0.4), (mx + 2.0, my - 1.6), (mx + 5.6, my - 4.4)], f"hlock{i}", 0.1), MANE)
    nod = q["nod"]
    for pts, c, _ in head.pieces:
        s.add(T(rotate(pts, nx, ny, nod)), c)
    # The saddle cloth, its trim, and the saddle over the back.
    cloth = [(-6.0, -8.8), (6.6, -9.0), (7.4, 0.4), (-5.6, 1.0)]
    s.add(T(cut(cloth, "hcloth", 0.15)), CLOTH)
    s.add(T(cut(polyline_band([(-5.8, 0.2), (7.2, -0.4)], z.thick(2.0)), "htrim", 0.06)), TRIM)
    saddle = [(-7.4, -10.4), (-5.6, -12.6), (-3.6, -10.0), (2.4, -10.0), (5.6, -13.4), (7.6, -12.2), (6.2, -8.4), (-6.4, -8.0)]
    s.add(T(cut(saddle, "hsaddle", 0.12)), SADDLE)
    bit = T(rotate([BIT], nx, ny, nod))[0]
    return s, dict(bit=bit, seat=T([SEAT])[0])


# ------------------------------------------------------------------ the pumpkin he holds up
#: The pumpkin's radius across, in units (its height is 0.84 of it).
PUMPKIN_RADIUS = 7.2

#: The carved face, in fractions of the pumpkin's radius from its middle: the left eye's outer top corner, its inner top
#: corner's height (its inner edge at [eye_inner]), its inner bottom corner (how far further out, and its height), its outer
#: bottom corner -- the right eye is its mirror --; and the grin, left corner first, the two teeth hanging from its top
#: edge. Its narrowest band -- the paper between the eyes and between an eye and the grin, an eye's height -- is about 0.29
#: of the radius, 2.1 units at 7.2.
FACE = dict(
    eye_inner=0.155,
    eye=((-0.68, -0.42), -0.25, (0.045, 0.02), (-0.56, -0.08)),
    grin=((-0.70, 0.26), (-0.26, 0.44), (-0.26, 0.34), (0.26, 0.44), (0.26, 0.34), (0.70, 0.26), (0.40, 0.64), (-0.40, 0.64)),
)

def face(cx, cy, r):
    """The carved face's three cuts, the two eyes and the grin, round the pumpkin's middle (cx, cy)."""
    (ax, ay), by, (cdx, cdy), (dx, dy) = FACE["eye"]
    xi = FACE["eye_inner"]
    left = [(cx + ax * r, cy + ay * r), (cx - xi * r, cy + by * r), (cx - (xi + cdx) * r, cy + cdy * r), (cx + dx * r, cy + dy * r)]
    right = [(2 * cx - x, y) for x, y in left][::-1]
    return [left, right, [(cx + x * r, cy + y * r) for x, y in FACE["grin"]]]


def pumpkin(z: Size, s: Sheet, cx, cy, r, seed) -> float:
    """A carved pumpkin lit from inside: the fruit over its under-paper, the stem, and the face's cuts in the candle's
    light. Returns its top, the stem's."""
    body = cut(ellipse(cx, cy, r, r * 0.84, 20), seed + "b", 0.12)
    s.add(move(body, *z.drop(0.6, 0.8)), darker(PUMPKIN, 0.72))
    s.add(body, PUMPKIN)
    stem = cut(capsule(cx + 0.4, cy - r * 0.78, cx + 1.8, cy - r * 1.22, z.thick(2.0), z.thick(1.8), n=3), seed + "s", 0.06)
    s.add(stem, STEM)
    for i, e in enumerate(face(cx, cy, r)):
        s.add(cut(e, f"{seed}f{i}", 0.05), LIGHT)
    return min(y for _, y in stem + body)


# ------------------------------------------------------------------ the rider and his cape
#: How much lower than the coat's outline the skirt hangs at its three back corners (the back of the waist, the back of
#: the hem, the hem under the seat), in units: so the saddle never shows under it as a sliver when the rider rises out of
#: the saddle with the stride.
SKIRT = (0.0, 1.2, 1.6)
#: The coat's buttons: their radius, and their heights in the drawing's frame (see [RIDER_FRAME_DY]), evenly between the
#: collar's shadow (2.02 units under the collar) and the belt.
BUTTON_RADIUS = 1.49
BUTTONS = (-14.02, -9.56)
#: The rider's frame against the drawing's: everything of him is drawn this much higher, so his seat ([SEAT]) lies on the
#: saddle's paper while he is drawn sitting on top of the saddle.
RIDER_FRAME_DY = -1.0
#: The near arm's pivot at the shoulder, in the drawing's frame: where its sleeve starts, inside the coat under the collar.
#: The renderer turns the arm about it to throw (`HorsemanFlight.armDegrees`).
ARM_PIVOT = (2.6, -18.6)
#: The round of the near shoulder, (cx, cy, rx, ry) in the drawing's frame: coat over the collar's right end and the cape
#: behind it, from the pivot up to the collar's top. Turned to throw, the arm uncovers what lies under it: without the
#: round, slivers under 2 px on the BV6600 of the collar's under-paper (`#B6B0A1`, from the arm at rest to its wind-up)
#: and of the cape (`#1F1D27`, wound up) would show there, and the shoulder covers them.
SHOULDER_CAP = (6.2, -20.8, 3.2, 4.2)
#: The dark of the neck inside the collar, (cx, cy, rx, ry) in the drawing's frame: 7.8 units across, a little left of the
#: collar's middle, so the collar's back holds 2 px on its left side alone -- thrown forward, the arm covers its right
#: side, and a wider dark would leave a thin crescent of it (`#C8C2B2`) alone over the neck.
NECK_HOLE = (-0.6, -23.0, 3.9, 1.6)


def rider(z: Size):
    """The headless rider sitting on the saddle, facing left, the seat at (0, 0), in three papers' worth of pieces:
    ``body`` -- his near leg down the horse's side, his coat, the far arm reaching for the reins, the collar round the empty
    neck and the round of his near shoulder ([SHOULDER_CAP]) --; ``arm`` -- the near arm holding the pumpkin up, which the
    renderer turns about [ARM_PIVOT] to throw --; and ``pumpkin`` -- the carved pumpkin lit from inside, in his hand and
    thrown. Laid in that order with the arm unturned they are the rider holding his pumpkin up. Returns the three sheets
    and his points: the rein hand, the shoulders (the cape's pivot), the arm's pivot, the pumpkin's middle and its top, the
    collar's top and the boot's bottom (what is seen of him)."""
    body, arm, lantern = Sheet(), Sheet(), Sheet()
    s = body
    # The far arm's sleeve first, behind the coat, reaching forward for the reins.
    rein_hand = (-12.6, -8.6)
    s.add(cut(capsule(-3.0, -16.0, -9.6, -10.0, z.thick(4.6), z.thick(4.0)), "rfarm", 0.1), COAT_DARK)
    s.add(cut(ellipse(*rein_hand, z.half(2.4), z.half(2.2), 10), "rfglove", 0.06), GLOVE)
    # The near leg down the horse's side: thigh forward, shin down, the boot in the stirrup.
    s.add(cut(capsule(2.4, -1.0, -4.8, 2.6, z.thick(6.0), z.thick(5.0)), "rthigh", 0.12), TROUSER)
    s.add(cut(capsule(-4.8, 2.6, -4.0, 10.6, z.thick(4.6), z.thick(4.0)), "rshin", 0.1), TROUSER)
    boot = cut([(-7.6, 9.4), (-1.6, 9.4), (-1.0, 13.4), (-8.8, 13.6), (-9.0, 11.6)], "rboot", 0.08)
    s.add(boot, BOOT)
    s.add(cut(polyline_band([(-7.4, 14.0), (-1.4, 14.0)], z.thick(1.8)), "rstirrup", 0.04), STIRRUP)
    # The coat: from the saddle to the shoulders, leaning a little forward, its skirt over the horse's back.
    coat = [(-6.4, 0.6), (-6.8, -10.0), (-7.4, -18.6), (-3.6, -22.2), (4.6, -22.0), (6.8, -17.4), (6.2, -6.0),
            (10.8, -0.6 + SKIRT[0]), (8.6, 1.8 + SKIRT[1]), (2.0, 1.2 + SKIRT[2])]
    s.add(move(cut(coat, "rcoat", 0.2), *z.drop(0.6, 1.0)), darker(COAT, 0.72))
    s.add(cut(coat, "rcoat", 0.2), COAT)
    # The belt and the buttons.
    s.add(cut([(-7.0, -6.6), (6.6, -6.8), (6.6, -4.4), (-6.8, -4.2)], "rbelt", 0.08), COAT_DARK)
    for i, by in enumerate(BUTTONS):
        s.add(cut(ellipse(-4.4, by, z.half(BUTTON_RADIUS), z.half(BUTTON_RADIUS), 8), f"rbtn{i}", 0.04), BUTTON)
    # The collar round the empty neck, and the dark of the neck inside it: no head, and nothing gory.
    nx, ny, nrx, nry = NECK_HOLE
    collar_back = cut(ellipse(-0.4, -23.2, 6.6, 2.8, 16), "rcollarb", 0.08)
    s.add(collar_back, COLLAR_PAPERS[0])
    s.add(cut(ellipse(nx, ny, nrx, nry, 12), "rneck", 0.05), NECK)
    band = [(-7.0, -23.0), (6.2, -23.0), (5.8, -19.0), (-6.6, -19.0)]
    s.add(move(cut(band, "rcollar", 0.1), *z.drop(0.4, 0.8)), COLLAR_PAPERS[1])
    s.add(cut(band, "rcollar", 0.1), COLLAR_PAPERS[2])
    # The round of the near shoulder, over the collar's end: the arm turns out of it ([SHOULDER_CAP]).
    s.add(cut(ellipse(*SHOULDER_CAP, 14), "rshoulder", 0.06), COAT)
    # The near arm holding the pumpkin up high, the glove under it.
    elbow, hand = (9.0, -24.6), (7.4, -32.4)
    r = PUMPKIN_RADIUS
    pc = (7.8, -41.2)
    arm.add(cut(capsule(*ARM_PIVOT, *elbow, z.thick(4.6), z.thick(4.2)), "rnarm", 0.1), COAT)
    arm.add(cut(capsule(*elbow, *hand, z.thick(4.2), z.thick(3.8)), "rnfore", 0.1), COAT)
    arm.add(cut(ellipse(*hand, z.half(2.4), z.half(2.2), 10), "rnglove", 0.06), GLOVE)
    pumpkin_top = pumpkin(z, lantern, *pc, r, "rp")
    dy = RIDER_FRAME_DY
    for sheet in (body, arm, lantern):
        sheet.pieces = [(move(p, 0.0, dy), c, clip) for p, c, clip in sheet.pieces]
    return dict(body=body, arm=arm, pumpkin=lantern), dict(
        rein_hand=(rein_hand[0] - 1.0, rein_hand[1] + dy), shoulder=(2.0, -20.0 + dy), arm_pivot=(ARM_PIVOT[0], ARM_PIVOT[1] + dy),
        pumpkin=(pc[0], pc[1] + dy), pumpkin_top=pumpkin_top + dy, top=min(y for _, y in collar_back) + dy,
        bottom=max(y for _, y in boot) + dy)


def cape(z: Size) -> Sheet:
    """The cape flying back from the shoulders (its pivot at 0, 0), over its under-paper, the red lining showing where it
    curls."""
    s = Sheet()
    outline = [(-4.0, -1.4), (3.6, -3.4), (12.0, -3.2), (20.0, -1.0), (27.4, 2.6), (25.6, 6.6), (21.4, 9.4), (15.0, 12.2), (8.6, 14.4),
               (3.4, 13.6), (0.6, 7.0), (-3.6, 3.4)]
    s.add(move(cut(outline, "ccape", 0.2), *z.drop(0.4, 1.2)), darker(CAPE, 0.7))
    s.add(cut(outline, "ccape", 0.2), CAPE)
    lining = [(14.0, -2.6), (20.0, -1.0), (27.4, 2.6), (25.6, 6.6), (21.0, 4.2), (16.0, 1.4)]
    s.add(cut(lining, "clining", 0.12), LINING)
    return s


# ------------------------------------------------------------------ the pieces and the table
#: The rider's three pieces, and each one's frame in the drawing's: the arm's and the pumpkin's moved by whole units, the
#: whole unit nearest the arm's pivot and the pumpkin's middle, so that at rest their pixels fall where the rider's would.
RIDER_PIECES = ("horseman_rider_body", "horseman_rider_arm", "horseman_rider_pumpkin")
NAMES = [f"horseman_horse_{p}" for p in range(4)] + [f"horseman_legs_{p}" for p in range(4)] + list(RIDER_PIECES) + ["horseman_cape"]

COMMENT = ("The Headless Horseman, the rider of Sleepy Hollow (DESIGN_NOTES.md §12, decision 79): {what}. Fixed art. "
           "Generated by tools/assets/horseman/build_horseman.py: edit that, not this.")


def boxed(body: list[str], frame: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    """[body]'s content box on the 3x grid ([content_box], which measures it over [frame] grown by 6 units), refusing a box
    that reaches that margin's edge: the drawing would have been cut there."""
    box = content_box(body, frame, PX)
    fx, fy, fw, fh = frame
    assert fx - 6 < box[0] and fy - 6 < box[1] and box[0] + box[2] < fx + fw + 6 and box[1] + box[3] < fy + fh + 6, (frame, box)
    return box


def build(ratio: float = RIDER_TO_PEDESTRIAN):
    """Everything this writes for a rider [ratio] times a pedestrian, as {path: bytes}, the table's numbers and the size."""
    z = size_for(ratio)
    out, t = {}, dict(poses=[], legs=[])
    hoof = -1e9
    for p in range(4):
        hs, hp = horse(z, p)
        body = hs.body()
        box = boxed(body, (-60, -50, 120, 80))
        name = f"horseman_horse_{p}"
        out[SVG_DIR / f"{name}.svg"] = svg_document(body, box, PX, COMMENT.format(
            what=f"the horse without its legs in pose {p} of the four of its gallop, facing left, its body's middle at (0,0)")).encode()
        out[RES / f"{name}.png"] = render_body(body, box, PX)
        t["poses"].append(dict(ox=box[0], oy=box[1], w=box[2], h=box[3], bit=hp["bit"], seat=hp["seat"]))
        ls, lowest = legs(z, p)
        hoof = max(hoof, lowest)
        lbody = ls.body()
        lbox = boxed(lbody, (-50, -20, 100, 60))
        name = f"horseman_legs_{p}"
        out[SVG_DIR / f"{name}.svg"] = svg_document(lbody, lbox, PX, COMMENT.format(
            what=f"the horse's four legs in pose {p} of its gallop, in the body's frame, drawn behind it")).encode()
        out[RES / f"{name}.png"] = render_body(lbody, lbox, PX)
        t["legs"].append(dict(ox=lbox[0], oy=lbox[1], w=lbox[2], h=lbox[3]))
    sheets, rp = rider(z)
    t["rider"] = dict(rp)
    for name, part, frame, what in (
        ("horseman_rider_body", "body", (-40, -90, 70, 120),
         "the headless rider without his near arm and his pumpkin (his coat, the collar round the empty neck, the round of his "
         "near shoulder, his near leg, the far arm on the reins), his seat at (0,0)"),
        ("horseman_rider_arm", "arm", (-20, -30, 40, 45),
         "his near arm and its glove, in a frame a whole unit from its pivot at the shoulder, about which the renderer turns it "
         "to throw"),
        ("horseman_rider_pumpkin", "pumpkin", (-15, -20, 30, 30),
         "the carved pumpkin lit from inside that he holds up and throws, in a frame a whole unit from its middle"),
    ):
        origin = {"body": (0, 0), "arm": whole(rp["arm_pivot"]), "pumpkin": whole(rp["pumpkin"])}[part]
        sheet = Sheet()
        sheet.clips = {n: [move(sh, -origin[0], -origin[1]) for sh in shapes] for n, shapes in sheets[part].clips.items()}
        for pts, colour, clip in sheets[part].pieces:
            sheet.add(move(pts, -origin[0], -origin[1]), colour, clip)
        pbody = sheet.body()
        pbox = boxed(pbody, frame)
        out[SVG_DIR / f"{name}.svg"] = svg_document(pbody, pbox, PX, COMMENT.format(what=what)).encode()
        out[RES / f"{name}.png"] = render_body(pbody, pbox, PX)
        # Where it blits: from the seat (the body), from the pivot (the arm), from the middle (the pumpkin).
        anchor = {"body": (0.0, 0.0), "arm": rp["arm_pivot"], "pumpkin": rp["pumpkin"]}[part]
        t["rider"][f"{part}_piece"] = dict(ox=pbox[0] + origin[0] - anchor[0], oy=pbox[1] + origin[1] - anchor[1], w=pbox[2], h=pbox[3],
                                frame=origin, box=pbox)
    cbody = cape(z).body()
    cbox = boxed(cbody, (-20, -20, 60, 50))
    out[SVG_DIR / "horseman_cape.svg"] = svg_document(cbody, cbox, PX, COMMENT.format(
        what="his black cape lined in red, its pivot at his shoulders (0,0), which the renderer swings with the stride")).encode()
    out[RES / "horseman_cape.png"] = render_body(cbody, cbox, PX)
    t["cape"] = dict(ox=cbox[0], oy=cbox[1], w=cbox[2], h=cbox[3])
    # The group's extent: every piece's box, in each pose, at rest (no bob, the cape not swung).
    R, C = t["rider"], t["cape"]
    boxes = []
    for P, L in zip(t["poses"], t["legs"]):
        sx, sy = P["seat"]
        kx, ky = sx + R["shoulder"][0], sy + R["shoulder"][1]
        boxes += [(P["ox"], P["oy"], P["w"], P["h"]), (L["ox"], L["oy"], L["w"], L["h"]), (kx + C["ox"], ky + C["oy"], C["w"], C["h"])]
        for part, anchor in (("body", (0.0, 0.0)), ("arm", R["arm_pivot"]), ("pumpkin", R["pumpkin"])):
            Q = R[f"{part}_piece"]
            boxes.append((sx + anchor[0] + Q["ox"], sy + anchor[1] + Q["oy"], Q["w"], Q["h"]))
    t["left"] = min(b[0] for b in boxes)
    t["right"] = max(b[0] + b[2] for b in boxes)
    t["top"] = min(b[1] for b in boxes)
    t["bottom"] = max(b[1] + b[3] for b in boxes)
    t["hoof_bottom"] = hoof
    t["pumpkin_top"] = min(P["seat"][1] + R["pumpkin_top"] for P in t["poses"])
    out[TABLE] = kotlin_table(t, z).encode()
    return out, t, z


def whole(point) -> tuple[int, int]:
    """The whole unit nearest [point]: the origin of a piece's own frame."""
    return int(round(point[0])), int(round(point[1]))


def f(x: float) -> str:
    return f"{x:.2f}f"


def fa(values) -> str:
    return "floatArrayOf(" + ", ".join(f(v) for v in values) + ")"


def kotlin_table(t, z: Size) -> str:
    P, L, R, C = t["poses"], t["legs"], t["rider"], t["cape"]
    return f'''package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * The Headless Horseman, in units of the group: the horse's body's middle at (0, 0), the group facing left (the
 * drawer mirrors it to fly right). `*_X`/`*_Y` of a piece is where its sprite's top-left blits. **Generated** by
 * `tools/assets/horseman/build_horseman.py` from the geometry it draws the SVGs with; edit that script, not this file.
 */
internal object HorsemanTable {{
    /** The reins, from the rider's hand to the bit: their width in units (2.5 px or more on the BV6600) and their colour. */
    const val REIN_UNITS = {f(z.rein)}
    const val REIN_COLOUR = 0xFF{REIN_COLOUR[1:]}.toInt()

    /** The narrowest designed band, in units: 2 px and a quarter more on the BV6600. The card draws him at its sleigh's scale. */
    const val HORSEMAN_BAND_UNITS = {f(z.band)}

    /** The horse without its legs, and its four legs, in the four poses of the gallop; where each blits. */
    val HORSE_RES = intArrayOf({", ".join(f"R.drawable.horseman_horse_{p}" for p in range(4))})
    val LEGS_RES = intArrayOf({", ".join(f"R.drawable.horseman_legs_{p}" for p in range(4))})
    val HORSE_X = {fa([p["ox"] for p in P])}
    val HORSE_Y = {fa([p["oy"] for p in P])}
    val LEGS_X = {fa([p["ox"] for p in L])}
    val LEGS_Y = {fa([p["oy"] for p in L])}

    /** In each pose: the bit, where the reins reach the horse's head, and the seat, where the rider sits on the saddle. */
    val BIT_X = {fa([p["bit"][0] for p in P])}
    val BIT_Y = {fa([p["bit"][1] for p in P])}
    val SEAT_X = {fa([p["seat"][0] for p in P])}
    val SEAT_Y = {fa([p["seat"][1] for p in P])}

    /**
     * The rider in three pieces, laid in this order: his body (all of him but his near arm and his pumpkin), from the seat;
     * his near arm, which turns about its pivot at the shoulder to throw -- the pivot from the seat, and where the arm's
     * sprite blits from the pivot --; and the carved pumpkin he holds up and throws -- its middle from the seat, in his hand
     * with the arm unturned, where its sprite blits from its middle, and its radius across.
     */
    val BODY_RES = R.drawable.horseman_rider_body
    const val BODY_X = {f(R["body_piece"]["ox"])}
    const val BODY_Y = {f(R["body_piece"]["oy"])}
    val ARM_RES = R.drawable.horseman_rider_arm
    const val ARM_PIVOT_X = {f(R["arm_pivot"][0])}
    const val ARM_PIVOT_Y = {f(R["arm_pivot"][1])}
    const val ARM_X = {f(R["arm_piece"]["ox"])}
    const val ARM_Y = {f(R["arm_piece"]["oy"])}
    val PUMPKIN_RES = R.drawable.horseman_rider_pumpkin
    const val PUMPKIN_X = {f(R["pumpkin"][0])}
    const val PUMPKIN_Y = {f(R["pumpkin"][1])}
    const val PUMPKIN_BLIT_X = {f(R["pumpkin_piece"]["ox"])}
    const val PUMPKIN_BLIT_Y = {f(R["pumpkin_piece"]["oy"])}
    const val RIDER_PUMPKIN_RADIUS_UNITS = {f(PUMPKIN_RADIUS)}

    /** From the seat: the hand holding the reins, and the shoulders (the cape's pivot). */
    const val HAND_X = {f(R["rein_hand"][0])}
    const val HAND_Y = {f(R["rein_hand"][1])}
    const val SHOULDER_X = {f(R["shoulder"][0])}
    const val SHOULDER_Y = {f(R["shoulder"][1])}

    /** The cape, and where it blits from its pivot at the shoulders. */
    val CAPE_RES = R.drawable.horseman_cape
    const val CAPE_X = {f(C["ox"])}
    const val CAPE_Y = {f(C["oy"])}

    /** The group's extent: every piece's box in every pose, at rest. The drawer puts its middle on the flight point. */
    const val LEFT = {f(t["left"])}
    const val RIGHT = {f(t["right"])}
    const val TOP = {f(t["top"])}
    const val BOTTOM = {f(t["bottom"])}

    /** What is seen of the rider, from the seat: the top of his collar and the bottom of his boot. His size is set on it. */
    const val RIDER_TOP = {f(R["top"])}
    const val RIDER_BOTTOM = {f(R["bottom"])}

    /**
     * How tall the rider is drawn against an adult walking on the near row of the pavement: Santa's
     * (`DESIGN_NOTES.md` §12, decision 79).
     */
    const val RIDER_TO_PEDESTRIAN = {f(z.ratio)}
}}
'''


# ------------------------------------------------------------------ the group composed as the renderer draws it
def drawer_motion() -> dict[str, float]:
    """The gallop's constants, read from the renderer that moves the group."""
    text = DRAWER.read_text()
    names = ("HORSE_BOB_UNITS", "RIDER_BOB_UNITS", "CAPE_SWING_DEGREES", "CAPE_PHASE", "STRIDE_RATE", "POSES_PER_SECOND")
    return {n: float(re.search(rf"const val {n} = (-?[0-9.]+)f", text).group(1)) for n in names}


#: The order `HeadlessHorsemanDrawer.draw` lays the group down in (`tests/test_horseman.py` reads it off the drawer).
ORDER = ("cape", "legs", "horse", "reins", "body", "arm", "pumpkin")


def moments(k: dict[str, float]) -> list[tuple[int, float]]:
    """The moments of a stride looked at, as (pose, phase): the drawer shows pose p while the stride's phase
    (`seconds x STRIDE_RATE`) is in [p, p + 1) quarter turns -- `POSES_PER_SECOND` over `STRIDE_RATE` is a pose a quarter
    turn -- so each pose at the start, the end and three points between of its quarter (the bob's extremes fall at the
    quarters' ends), and the cape's two extremes where they fall."""
    assert abs(k["STRIDE_RATE"] / k["POSES_PER_SECOND"] - math.pi / 2) < 1e-4, "a pose is no longer a quarter of the stride"
    out = [(pose, (pose + j / 4) * math.pi / 2) for pose in range(4) for j in range(5)]
    for extreme in (math.pi / 2, 3 * math.pi / 2):
        phase = (extreme - k["CAPE_PHASE"]) % (2 * math.pi)
        out.append((int(phase // (math.pi / 2)), phase))
    return out


#: The flight that throws the pumpkins, whose gesture's angles the ``--two-px`` composition reads.
EFFECT = ENGINE / "HeadlessHorsemanEffect.kt"


def gesture() -> dict[str, float]:
    """The throw's two extreme angles of the arm, read from the flight that moves it: back as it winds up
    (`WIND_BACK_DEGREES`, clockwise on the screen, towards the tail) and forward where it lets the pumpkin go
    (`RELEASE_DEGREES`)."""
    text = EFFECT.read_text()
    return {n: float(re.search(rf"const val {n} = (-?[0-9.]+)f", text).group(1)) for n in ("WIND_BACK_DEGREES", "RELEASE_DEGREES")}


def gesture_angles(g: dict[str, float], step: float = 1.0) -> list[float]:
    """Every angle the arm passes on its way, looked at: from the release to the wind-up's most, every [step] degrees, the
    two ends and the arm at rest (0) included."""
    lo, hi = g["RELEASE_DEGREES"], g["WIND_BACK_DEGREES"]
    out = {lo, hi, 0.0}
    a = math.ceil(lo / step) * step
    while a < hi:
        out.add(float(a))
        a += step
    return sorted(out)


def crisp(svg: str) -> str:
    """The same SVG rendered without antialiasing: every pixel one paper's, so a band's width is its own."""
    return svg.replace("<svg ", '<svg shape-rendering="crispEdges" ', 1)


def svg_frame(svg: str) -> tuple[float, float, float, float]:
    return tuple(float(v) for v in re.search(r'viewBox="([-0-9.]+) ([-0-9.]+) ([-0-9.]+) ([-0-9.]+)"', svg).groups())


def svg_inner(svg: str) -> str:
    return svg[svg.index(">") + 1:svg.rindex("</svg>")]


def turned(svg: str, degrees: float) -> tuple[Image.Image, tuple[int, int]]:
    """An SVG drawn turned by [degrees] (clockwise on the screen, as the renderer's canvas turns) about its frame's (0, 0),
    without antialiasing: the image and its top-left, in units of that frame."""
    x, y, w, h = svg_frame(svg)
    corners = rotate([(x, y), (x + w, y), (x, y + h), (x + w, y + h)], 0.0, 0.0, degrees)
    x0, y0 = math.floor(min(p[0] for p in corners)), math.floor(min(p[1] for p in corners))
    x1, y1 = math.ceil(max(p[0] for p in corners)), math.ceil(max(p[1] for p in corners))
    doc = (f'<svg xmlns="http://www.w3.org/2000/svg" shape-rendering="crispEdges" width="{(x1 - x0) * PX}" '
           f'height="{(y1 - y0) * PX}" viewBox="{x0} {y0} {x1 - x0} {y1 - y0}"><g transform="rotate({degrees:.6f})">{svg_inner(svg)}</g></svg>')
    return Image.open(io.BytesIO(render_svg(doc).png_bytes)).convert("RGBA"), (x0, y0)


def in_hand(out, t, part: str, scale: float = 1.0) -> str:
    """The arm's or the pumpkin's SVG moved into the arm pivot's own frame (the pivot at (0, 0)) -- as the drawer blits
    each, the pumpkin about its middle by [scale] --, so that [turned] turns it as the renderer turns the arm."""
    R = t["rider"]
    name = {"arm": "horseman_rider_arm", "pumpkin": "horseman_rider_pumpkin"}[part]
    svg = out[SVG_DIR / f"{name}.svg"].decode()
    x, y, w, h = svg_frame(svg)
    Q = R[f"{part}_piece"]
    if part == "arm":
        dx, dy = Q["ox"] - x, Q["oy"] - y
        transform = f"translate({dx:.6f},{dy:.6f})"
        box = (Q["ox"], Q["oy"], w, h)
    else:
        mx, my = R["pumpkin"][0] - R["arm_pivot"][0], R["pumpkin"][1] - R["arm_pivot"][1]
        transform = f"translate({mx:.6f},{my:.6f}) scale({scale:.6f}) translate({Q['ox'] - x:.6f},{Q['oy'] - y:.6f})"
        box = (mx + Q["ox"] * scale, my + Q["oy"] * scale, w * scale, h * scale)
    x0, y0 = math.floor(box[0]), math.floor(box[1])
    x1, y1 = math.ceil(box[0] + box[2]), math.ceil(box[1] + box[3])
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{(x1 - x0) * PX}" height="{(y1 - y0) * PX}" '
            f'viewBox="{x0} {y0} {x1 - x0} {y1 - y0}"><g transform="{transform}">{svg_inner(svg)}</g></svg>')


def compose(out, t, z: Size, pose: int, phase: float, k: dict[str, float], order=ORDER, rest: bool = False,
            arm: float = 0.0, hand: float = 1.0) -> Image.Image:
    """The group as `HeadlessHorsemanDrawer.draw` lays it down, at 3 px a unit, from the SVGs rendered without
    antialiasing: in pose [pose] at the stride's [phase] -- the horse and its legs bobbing by `HORSE_BOB_UNITS` with the
    stride, the rider the other way by `RIDER_BOB_UNITS`, the cape swung `CAPE_SWING_DEGREES` about his shoulders a little
    behind it (`CAPE_PHASE`) -- the reins a line [Size.rein] wide with round ends from his hand to the bit, as the
    renderer's paint draws them, and his near arm turned [arm] degrees about its pivot with the pumpkin in its hand, the
    pumpkin at [hand] of its size about its middle (0: none in his hand). [rest]: no bob and no swing. The canvas's (0, 0)
    is the group's (floor(LEFT) - 6, floor(TOP) - 6)."""
    stride = 0.0 if rest else math.sin(phase)
    horse_bob, rider_bob = stride * k["HORSE_BOB_UNITS"], -stride * k["RIDER_BOB_UNITS"]
    swing = 0.0 if rest else math.sin(phase + k["CAPE_PHASE"]) * k["CAPE_SWING_DEGREES"]
    left, top = math.floor(t["left"]) - 6, math.floor(t["top"]) - 6
    canvas = Image.new("RGBA", (int((math.ceil(t["right"]) + 6 - left) * PX), int((math.ceil(t["bottom"]) + 6 - top) * PX)), (0, 0, 0, 0))

    def at(ux, uy):
        return int(round((ux - left) * PX)), int(round((uy - top) * PX))

    def sprite(name):
        return Image.open(io.BytesIO(render_svg(crisp(out[SVG_DIR / f"{name}.svg"].decode())).png_bytes)).convert("RGBA")

    P, L, R = t["poses"][pose], t["legs"][pose], t["rider"]
    seat_x, seat_y = P["seat"][0], P["seat"][1] + rider_bob
    pivot_x, pivot_y = seat_x + R["arm_pivot"][0], seat_y + R["arm_pivot"][1]

    def cape_():
        image, (x0, y0) = turned(out[SVG_DIR / "horseman_cape.svg"].decode(), swing)
        canvas.alpha_composite(image, at(seat_x + R["shoulder"][0] + x0, seat_y + R["shoulder"][1] + y0))

    def legs_():
        canvas.alpha_composite(sprite(f"horseman_legs_{pose}"), at(L["ox"], L["oy"] + horse_bob))

    def horse_():
        canvas.alpha_composite(sprite(f"horseman_horse_{pose}"), at(P["ox"], P["oy"] + horse_bob))

    def reins_():
        layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        ends = [((seat_x + R["rein_hand"][0] - left) * PX, (seat_y + R["rein_hand"][1] - top) * PX),
                ((P["bit"][0] - left) * PX, (P["bit"][1] + horse_bob - top) * PX)]
        r = z.rein * PX / 2
        draw.line(ends, fill=REIN_COLOUR, width=int(round(z.rein * PX)))
        for x, y in ends:
            draw.ellipse((x - r, y - r, x + r, y + r), fill=REIN_COLOUR)
        canvas.alpha_composite(layer)

    def body_():
        Q = R["body_piece"]
        canvas.alpha_composite(sprite("horseman_rider_body"), at(seat_x + Q["ox"], seat_y + Q["oy"]))

    def arm_():
        image, (x0, y0) = turned(in_hand(out, t, "arm"), arm)
        canvas.alpha_composite(image, at(pivot_x + x0, pivot_y + y0))

    def pumpkin_():
        if hand <= 0.0:
            return
        image, (x0, y0) = turned(in_hand(out, t, "pumpkin", hand), arm)
        canvas.alpha_composite(image, at(pivot_x + x0, pivot_y + y0))

    steps = dict(cape=cape_, legs=legs_, horse=horse_, reins=reins_, body=body_, arm=arm_, pumpkin=pumpkin_)
    for step in order:
        steps[step]()
    return canvas


def pieces(mask: np.ndarray) -> list[list[tuple[int, int]]]:
    """The 4-connected pieces of [mask], each as its pixels (row, column)."""
    seen = np.zeros_like(mask)
    H, W = mask.shape
    out = []
    for y, x in zip(*(a.tolist() for a in np.nonzero(mask))):
        if seen[y, x]:
            continue
        queue = deque([(y, x)])
        seen[y, x] = True
        pts = []
        while queue:
            a, b = queue.popleft()
            pts.append((a, b))
            for c, d in ((a + 1, b), (a - 1, b), (a, b + 1), (a, b - 1)):
                if 0 <= c < H and 0 <= d < W and mask[c, d] and not seen[c, d]:
                    seen[c, d] = True
                    queue.append((c, d))
        out.append(pts)
    return out


def squares(mask: np.ndarray, k: int) -> np.ndarray:
    """True at the top-left corner of every k x k square lying wholly inside [mask]."""
    H, W = mask.shape
    corners = np.zeros((H, W), dtype=bool)
    if H < k or W < k:
        return corners
    c = np.zeros((H + 1, W + 1), dtype=np.int64)
    c[1:, 1:] = np.cumsum(np.cumsum(mask.astype(np.int64), 0), 1)
    corners[:H - k + 1, :W - k + 1] = (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) == k * k
    return corners


def discs(mask: np.ndarray, side: float) -> np.ndarray:
    """True at the middle of every disc [side] px across lying wholly inside [mask] (the pixels within half of it of the
    middle): a turned band's measure, which an upright square's is not -- a band [side] wide turned 45 degrees holds a disc
    [side] across and an upright square of only 0.71 of it."""
    r = side / 2
    reach = int(math.floor(r))
    H, W = mask.shape
    padded = np.pad(mask, reach)
    out = np.ones_like(mask)
    for dy in range(-reach, reach + 1):
        for dx in range(-reach, reach + 1):
            if dx * dx + dy * dy <= r * r:
                out &= padded[reach + dy:reach + dy + H, reach + dx:reach + dx + W]
    return out


def thin_pieces(image: Image.Image, k: int, smallest: int = 12, disc: float | None = None, turned: frozenset[str] = frozenset()):
    """Every piece of one colour of [image] (its opaque pixels) that holds no k x k square anywhere -- or, for a colour in
    [turned] (a paper turned off the upright), no disc [disc] px across --, as (colour, pixels, (x0, x1, y0, y1)); pieces
    under [smallest] pixels are the corners where three papers meet."""
    a = np.array(image).astype(np.int64)
    opaque = a[:, :, 3] == 255
    keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
    found = []
    for key in np.unique(keys[opaque]):
        mask = opaque & (keys == key)
        colour = "#%06X" % int(key)
        ok = discs(mask, disc) if disc is not None and colour in turned else squares(mask, k)
        for pts in pieces(mask):
            if len(pts) >= smallest and not any(ok[p] for p in pts):
                ys, xs = [p[0] for p in pts], [p[1] for p in pts]
                found.append((colour, len(pts), (min(xs), max(xs), min(ys), max(ys))))
    return found


def pumpkin_papers() -> frozenset[str]:
    """The colours of the pumpkin he holds and throws, which no other piece of the group wears: the papers that turn with
    his arm and fall turning."""
    sheets, _ = rider(size_for(RIDER_TO_PEDESTRIAN))
    return frozenset(c.upper() for _, c, _ in sheets["pumpkin"].pieces)


def two_px_report(out, t, z: Size, step: float = 1.0) -> list[str]:
    """Every piece of one colour of the group, composed as the renderer draws it at every moment [moments] names and with
    the near arm at every angle of the throw [gesture_angles] names ([step] degrees apart), the pumpkin in its hand, that is
    under 2 px on the BV6600 (at [z]'s size) everywhere: a piece that holds no upright square of 2 px anywhere -- but for the
    pumpkin's papers with the arm turned ([pumpkin_papers]), held to a disc 2 px across, because they are not upright then,
    and an upright square in a band shrinks as the band turns (by 0.71 at 45 degrees) while the band does not. And the
    reins' width."""
    k = drawer_motion()
    side = 2.0 / z.px_per_unit * PX
    square = math.ceil(side)
    angles = gesture_angles(gesture(), step)
    turned = pumpkin_papers()
    lines, bad = [], 0
    for angle in angles:
        for pose, phase in moments(k):
            image = compose(out, t, z, pose, phase, k, arm=angle)
            for colour, n, (x0, x1, y0, y1) in thin_pieces(image, square, disc=side, turned=turned if angle != 0.0 else frozenset()):
                bad += 1
                lines.append(f"arm {angle:+.0f} pose {pose} phase {phase:.3f}: {colour} {n} px at x {x0}..{x1} y {y0}..{y1} of the "
                             f"3x composition")
    lines.append(f"{bad} pieces under 2 px in {len(moments(k))} moments x {len(angles)} angles of the arm ({angles[0]:+.0f} to "
                 f"{angles[-1]:+.0f} degrees, every {step:g}; a square of {square} px of the 3x drawing, the turned pumpkin's papers a "
                 f"disc of {side:.2f}: 2 px are {side:.2f}); the reins {z.rein:.2f} units, {z.rein * z.px_per_unit:.2f} px")
    return lines


def measures(out, t, z: Size) -> dict[str, float]:
    """The size on the BV6600: the px a unit; the rider (collar to boot) and the whole group (lowest hoof to the top of the
    pumpkin's stem), from the geometry; the group's width, its ink in the four poses at rest; in units and in px; the
    bands."""
    rider_units = t["rider"]["bottom"] - t["rider"]["top"]
    group = t["hoof_bottom"] - t["pumpkin_top"]
    k = drawer_motion()
    x0, x1 = 1e9, -1e9
    for pose in range(4):
        box = compose(out, t, z, pose, 0.0, k, rest=True).getchannel("A").getbbox()
        x0, x1 = min(x0, box[0]), max(x1, box[2])
    width = (x1 - x0) / PX
    return dict(rider_to_pedestrian=z.ratio, px_per_unit=z.px_per_unit, band_units=z.band, rein_units=z.rein,
                rider_units=rider_units, rider_px=rider_units * z.px_per_unit, group_units=group, group_px=group * z.px_per_unit,
                group_to_pedestrian=group * z.px_per_unit / PEDESTRIAN_PX_BV6600, width_units=width, width_px=width * z.px_per_unit)


def ratio_for_group(peds: float) -> float:
    """The rider's ratio at which the whole group, its lowest hoof to the top of the pumpkin's stem, is [peds] pedestrians
    tall -- its bands, and so its pumpkin, thickened for that size: found again until it no longer moves."""
    ratio = RIDER_TO_PEDESTRIAN
    for _ in range(40):
        _, t, _ = build(ratio)
        rider_units = t["rider"]["bottom"] - t["rider"]["top"]
        new = round(peds * rider_units / (t["hoof_bottom"] - t["pumpkin_top"]), 4)
        if abs(new - ratio) < 1e-4:
            break
        ratio = new
    return ratio


def print_measures(m, where: str = "") -> None:
    print(f"the rider {m['rider_to_pedestrian']:.4f} pedestrians: {m['px_per_unit']:.4f} px a unit on the BV6600, the rider "
          f"{m['rider_px']:.1f} px tall (collar to boot, {m['rider_units']:.2f} units), the whole group {m['group_px']:.1f} px tall "
          f"({m['group_to_pedestrian']:.2f} pedestrians; lowest hoof to the top of the pumpkin's stem, {m['group_units']:.2f} units) "
          f"and {m['width_px']:.1f} px wide (its ink, {m['width_units']:.2f} units); the narrowest band {m['band_units']:.2f} units, the reins "
          f"{m['rein_units']:.2f} units {where}".rstrip())


def staging(ratio: float, folder: Path) -> int:
    """The whole set drawn for a rider [ratio] times a pedestrian, into [folder], and nothing into the tree."""
    out, t, z = build(ratio)
    folder.mkdir(parents=True, exist_ok=True)
    for p, data in out.items():
        if p.suffix in (".png", ".svg"):
            (folder / p.name).write_bytes(data)
    m = measures(out, t, z)
    (folder / "horseman.json").write_text(json.dumps(dict(measures=m, table=t), indent=1) + "\n")
    report = two_px_report(out, t, z)
    (folder / "two_px.txt").write_text("\n".join(report) + "\n")
    print_measures(m, f"-- into {folder}")
    print(report[-1])
    return 0


def main(argv) -> int:
    if "--out" in argv and ("--rider-to-pedestrian" in argv or "--group-to-pedestrian" in argv):
        folder = Path(argv[argv.index("--out") + 1])
        if "--rider-to-pedestrian" in argv:
            ratio = float(argv[argv.index("--rider-to-pedestrian") + 1])
        else:
            ratio = ratio_for_group(float(argv[argv.index("--group-to-pedestrian") + 1]))
        return staging(ratio, folder)
    res, check = "--res" in argv, "--check" in argv
    out, t, z = build()
    if "--two-px" in argv:
        for line in two_px_report(out, t, z):
            print(line)
    if check:
        stale = [p for p, data in out.items() if not same_output(p, data)]
        for p in stale:
            print(f"stale: {p.relative_to(REPO)}")
        return 1 if stale else 0
    if "--two-px" in argv and not res:
        return 0
    for p, data in out.items():
        if p.parent == RES and not res:
            continue
        p.write_bytes(data)
        print(f"wrote {p.relative_to(REPO)} ({len(data)} B)")
    print_measures(measures(out, t, z))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
