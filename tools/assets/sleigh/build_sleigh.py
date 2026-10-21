#!/usr/bin/env python3
"""Santa, his sleigh and his reindeer (v5.15), drawn again from nothing, from one geometry.

The maintainer, 2026-10-18: *«... possiamo anche rifarlo da 0»*; 2026-10-19: *«il disegno di babbo natale deve essere
rivisto, compresa slitta e renne, non solo babbo natale»*. Of the first three groups drawn (a Santa in profile, small
beside his sleigh), *«non mi piace nessuno dei disegni, rifanne di nuovi partendo da 0 e mantenendo lo stile paper
cutout / il disegno deve essere cozy e si deve capire che è babbo natale»*; of the second three, *«va bene B ma fai la
slitta un poco più alta, babbo natale deve sembrare seduto (come in A e C) e non in piedi»* (2026-10-19). This is B
with its side raised: **Santa faces us**, as the library's people do -- a round face with two dot eyes, rosy cheeks
and a round nose, a white beard that fills his chest, a red hat flopping to one side with its pompom, a round red coat
with its white fur and a black belt with a gold buckle -- one hand waving, the other holding the reins; he sits in a
red sleigh with gold scrolls, a gold band and gold runners, whose side rises to just under his belt, with presents
piled behind him; two chubby reindeer with red collars and gold bells gallop in front, Rudolph leading with his red
nose. To the round's table the same day: *«fallo più piccolo, così è gigante rispetto ai pedoni»*, and at night as it
is (no shade); the size is [SANTA_TO_PEDESTRIAN], chosen by the maintainer from the sizes shown beside the people.

The pieces, all fixed art and `SCENE_UNITS` (3 px a unit), each trimmed to its content on the 3x grid:

* ``santa_sleigh`` -- the sleigh with Santa and the presents, one sprite;
* ``santa_reindeer_0`` .. ``_3`` -- a reindeer in the four poses of a flying gallop, facing left, its body's middle
  at (0, 0). **One series for both reindeer**: Rudolph's red nose is a disc the renderer draws over his, by day as by
  night (it covers the brown nose under it), so no second series with the red nose drawn in is needed;
* the reins and the traces are cords the renderer draws between the points this writes, so they follow the gallop:
  the traces behind everything, the reins over the sleigh and behind the reindeer (``SantaSleighDrawer``) -- from the top
  of Santa's fist, over the sleigh's front scroll, to the front of each collar ([REIN_ON_COLLAR]).

**2 px on the BV6600** at the size chosen: the group is drawn at [px_per_unit_on_the_bv6600] px a unit there, so every
band of colour -- the under-papers' relief included -- and both cords are drawn at least [SANTA_MIN_BAND_UNITS] units
across ([thick]); ``tests/test_sleigh.py`` erodes every colour of the group as the renderer draws it -- Rudolph in each
pose, the second reindeer half a stride behind, both bobbing, the sleigh swaying, and the cords in the renderer's order --
and finds every piece 2 px wide somewhere, and measures the buckle's four bars and its hole where they show. That is why
the second reindeer stands 42 units behind Rudolph: at 40 his front legs cut Rudolph's tail to a sliver at one moment of the
stride; why the buckle stands a little high on the belt, its lower bar above the sleigh's rim and its frame going on under it (until v5.15C2 the rim cut
that bar to 1.66 px); and why the reins pass behind the reindeer, leaving the fist at its top (until v5.15C2 they were drawn
over everything, from the fist's middle to the collars' middles, and cut the glove, the front scroll, Rudolph's bell and the
second reindeer's head into pieces under 2 px).

It writes, from one run (and, with ``--res``, the five PNGs rendered from the SVGs):

* ``sources/svg/santa_sleigh.svg`` and ``santa_reindeer_0..3.svg``;
* ``app/.../engine/SantaSleighTable.kt`` -- where each piece blits, the cords' ends, the sack's mouth the gifts leave
  from, Rudolph's nose, the group's extent, Santa's height and the size chosen, and the cords' widths.

``--check`` writes nothing and fails unless every committed output is what this draws (``tests/test_sleigh.py``
makes the same comparison). ``--preview-size R DIR`` draws the group as it would be at another size -- Santa R times a
pedestrian's height, its bands thickened for that size -- into DIR, and nothing into the tree: how the sizes shown to the
maintainer were drawn.

Usage, from ``tools/assets`` with the venv's interpreter:

    python sleigh/build_sleigh.py [--res] [--check] [--preview-size R DIR]
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent
sys.path.insert(0, str(ASSETS))
from papercut import (Sheet, capsule, content_box, cut, darker, ellipse, move, polyline_band,  # noqa: E402
                      render_body, rotate, svg_document)

REPO = ASSETS.parent.parent
SVG_DIR = ASSETS / "sources" / "svg"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
TABLE = REPO / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / "engine" / "SantaSleighTable.kt"
PX = 3  # SpriteBlitter.SPRITE_PIXELS_PER_UNIT

# ------------------------------------------------------------------ the size, and what it asks of the drawing
#: How tall Santa is drawn -- his hat's top to the sleigh's rim, what is seen of him -- against an adult walking on the
#: near row of the pavement: the maintainer's choice (2026-10-19, from the sizes shown beside the people).
SANTA_TO_PEDESTRIAN = 1.3

#: An adult walking on the near row of the pavement on the BV6600, in px: `SceneSpace.PERSON_METRES_TALL` (1.75 m) at
#: `PIXELS_PER_METRE_AT_REFERENCE` (45 px/m on a 2 400 px screen, x0.6 for its 1 440), at the perspective of
#: `PAVEMENT_NEAR_Y_FRACTION` (0.807: (0.807 - 0.655) / (0.846 - 0.655)). `SantaSleighOriginTest` computes it again from
#: `SceneSpace` and fails if this has gone stale.
PEDESTRIAN_PX_BV6600 = 1.75 * 45.0 * (1440 / 2400) * ((0.807 - 0.655) / (0.846 - 0.655))

#: Santa's height in the group's units, his hat's top to the sleigh's rim: measured off the drawing in [build] and
#: checked against this, which the size depends on.
SANTA_UNITS_TALL = 39.2

def px_per_unit_on_the_bv6600(ratio: float = SANTA_TO_PEDESTRIAN) -> float:
    """Pixels a unit of the group on the BV6600 at the size [ratio]: `SantaSleighScale.pxPerUnit` there."""
    return ratio * PEDESTRIAN_PX_BV6600 / SANTA_UNITS_TALL


#: The narrowest any band of colour, or a cord, is drawn, in the group's units: two pixels on the BV6600 and a quarter
#: more, so that where a band falls across the pixel grid it still covers two whole pixels.
SANTA_MIN_BAND_UNITS = round(2.0 / px_per_unit_on_the_bv6600() * 1.25, 2)


def thick(units: float) -> float:
    """A band's width, or an under-paper's drop: as drawn, but never under [SANTA_MIN_BAND_UNITS]."""
    return max(units, SANTA_MIN_BAND_UNITS)


def drop(dx: float, dy: float) -> tuple[float, float]:
    return thick(dx), thick(dy)


HOOF = "#3B2618"
EYE = "#24180F"
WHITE = "#F7F2E8"
SKIN = "#F3C79C"
CHEEK = "#EE8F80"
NOSE = "#E88470"
SANTA_RED = "#C9302B"
GOLD = "#EDB943"
MITTEN = "#2F6B45"
BELT = "#2A2320"
#: Rudolph's nose, the disc the renderer draws over the reindeer's own: the same red by day and by night.
RUDOLPH_NOSE = "#E33A2E"
#: How large Rudolph's nose is drawn, in units of the reindeer: wide enough to cover the brown nose under it.
RUDOLPH_NOSE_RADIUS = 2.9
#: The reins (Santa's hand to each collar) and the traces (a reindeer to the one in front, or to the sleigh).
REIN_COLOUR = "#3A2416"
TRACE_COLOUR = "#962820"

# ------------------------------------------------------------------ the reindeer
#: The four poses of a flying gallop, short legs: (front upper, front lower, back upper, back lower) in degrees from
#: straight down, positive towards the tail; the head's nod; the body's pitch; how far the body rises (up negative);
#: whether the far legs show -- in the tucked stride the near legs cross under the belly and the far ones, behind them,
#: would show only as a sliver between the two (the 2 px rule), so there they are not drawn.
POSES = (
    dict(f=(-58, -76), b=(56, 72), nod=-3, pitch=-2.0, rise=0.0, far=True),
    dict(f=(-22, 30), b=(30, -14), nod=0, pitch=1.0, rise=-1.0, far=True),
    dict(f=(14, 52), b=(-10, -38), nod=3, pitch=2.5, rise=-1.8, far=False),
    dict(f=(-40, -12), b=(44, 34), nod=1, pitch=0.5, rise=-0.6, far=True),
)

DEER = dict(rx=13.6, ry=8.6, body="#94603A", belly="#E4C291", antler="#E7D2A8", nose="#2B1D16",
            head=(6.8, 5.6), snout=(4.2, 3.4), leg=(4.8, 4.6, 3.4, 2.8))
DEER_NOSE_RADIUS = 2.4
#: The far legs stand this far out from the near ones, in units (the front ones further forward, the back ones further
#: back), and turn this far from them, in degrees: so each shows as a leg of its own beside the near one in every pose,
#: never as a sliver of paper behind it (the 2 px rule; the drawing photographed in v5.15A turned them 14 degrees and did
#: not move them).
FAR_LEG_SHIFT = 3.0
FAR_LEG_SPLAY = 10
#: How far behind the near antler the far one stands, in units: enough that its beam shows, not only its tips.
FAR_ANTLER_OFFSET = 4.4
#: Where a rein ends on the collar, from the collar's middle, in units: its front, towards the head. From the middle, the
#: rein to Rudolph showed for a moment as a scrap between the second reindeer's head and Rudolph's back (v5.15C2).
REIN_ON_COLLAR = (-2.0, 0.0)


def leg_dir(a):
    r = math.radians(a)
    return math.sin(r), math.cos(r)


def leg(s: Sheet, x, y, ua, la, colour, seed):
    lu, ll, wu, wl = DEER["leg"]
    dx, dy = leg_dir(ua)
    kx, ky = x + dx * lu, y + dy * lu
    ex, ey = leg_dir(la)
    fx, fy = kx + ex * ll, ky + ey * ll
    s.add(cut(capsule(x, y, kx, ky, wu, wu * 0.94), seed + "u", 0.1), colour)
    s.add(cut(capsule(kx, ky, fx, fy, wu * 0.9, wl), seed + "l", 0.1), colour)
    hx, hy = fx + ex * 1.8, fy + ey * 1.8
    s.add(cut(capsule(fx, fy, hx, hy, wl + 0.3, wl + 0.4, n=3), seed + "h", 0.08), HOOF)


def antler(base):
    """A small rounded antler: a beam bending back and two short tines."""
    bx, by = base
    beam = [(bx, by), (bx + 0.6, by - 4.0), (bx + 3.0, by - 7.2), (bx + 6.0, by - 8.4)]
    tines = [((bx + 0.5, by - 3.4), (bx - 2.6, by - 6.6)), ((bx + 2.8, by - 7.0), (bx + 2.4, by - 10.8))]
    return beam, tines, thick(2.6)


def reindeer(pose: int):
    """The reindeer in pose `pose`, facing left, its body's middle at (0, 0), and its points in that frame."""
    p, q = DEER, POSES[pose]
    s = Sheet()
    rx, ry = p["rx"], p["ry"]
    # The far legs in the tone of the body's under-paper: both are the shadow side of the same paper, and where they
    # meet they read as one piece rather than as two slivers of nearly one brown.
    far_legs = darker(p["body"], 0.74)

    def T(pts):
        return move(rotate(pts, 0.0, 0.0, q["pitch"]), 0.0, q["rise"])

    shoulder, hip = (-rx * 0.5, ry * 0.5), (rx * 0.52, ry * 0.45)
    fa, fb = q["f"], q["b"]
    tmp = Sheet()
    # The far legs' roots moved off the near ones' across the leg's own direction -- forward and up for the front leg,
    # back and up for the hind one -- so a leg stretched out (a flying gallop) and a leg straight down both show their
    # far twin beside them.
    if q["far"]:
        ua = math.radians(fa[0])
        leg(tmp, shoulder[0] - FAR_LEG_SHIFT * math.cos(ua), shoulder[1] + FAR_LEG_SHIFT * math.sin(ua) - 0.6,
            fa[0] + FAR_LEG_SPLAY, fa[1] + FAR_LEG_SPLAY, far_legs, f"b{pose}ff")
        ub = math.radians(fb[0])
        leg(tmp, hip[0] + FAR_LEG_SHIFT * math.cos(ub), hip[1] - FAR_LEG_SHIFT * math.sin(ub) - 0.6,
            fb[0] - FAR_LEG_SPLAY, fb[1] - FAR_LEG_SPLAY, far_legs, f"b{pose}fb")
    for pts, c, _ in tmp.pieces:
        s.add(T(pts), c)
    s.add(T(cut(ellipse(rx * 0.96, -ry * 0.42, 2.8, 2.4, 10, -20), "btail", 0.12)), WHITE)
    body = cut(ellipse(0, 0, rx, ry, 24, -3), "bbody", 0.25)
    # The body's under-paper straight below it, a little deeper than the rest's: along the belly's slope in front of a
    # tucked leg an under-paper moved right as well shows only as a sliver, and below it is where a flying reindeer's
    # shadow side is.
    s.relief(T(body), p["body"], drop=(0.0, thick(1.2) + 0.6))
    s.clip(f"bodyb{pose}", [T(body)])
    # The belly between the legs' roots, so neither leg leaves a sliver of it in front or behind.
    s.add(T(cut(ellipse(0.4, ry * 0.6, rx * 0.6, ry * 0.46, 16), "bbelly", 0.18)), p["belly"], clip=f"bodyb{pose}")
    hx, hy = p["head"]
    neck_root = (-rx * 0.6, -ry * 0.35)
    head_c = (-rx - hx * 0.35, -ry - hy * 0.55)
    head = Sheet()
    neck = [(neck_root[0] + 4.4, neck_root[1] + 2.6), (neck_root[0] - 3.2, neck_root[1] + 3.6),
            (head_c[0] - 1.6, head_c[1] + hy * 0.6), (head_c[0] + hx * 0.8, head_c[1] - 0.6)]
    ab = (head_c[0] + hx * 0.1, head_c[1] - hy * 0.8)
    for k, (dxa, colour) in enumerate(((FAR_ANTLER_OFFSET, darker(p["antler"], 0.82)), (0.0, p["antler"]))):
        beam, tines, w = antler((ab[0] + dxa, ab[1] + (0.6 if k == 0 else 0.0)))
        head.add(cut(polyline_band(beam, w), f"bbeam{k}", 0.08), colour)
        for j, (a0, a1) in enumerate(tines):
            head.add(cut(capsule(*a0, *a1, w, w * 0.9, n=4), f"btine{k}{j}", 0.08), colour)
    head.add(cut(neck, "bneck", 0.18), p["body"])
    head.add(cut(ellipse(head_c[0] + hx * 0.85, head_c[1] - hy * 0.55, 3.4, 1.9, 10, -35), "bear", 0.08), darker(p["body"], 0.86))
    head.relief(cut(ellipse(*head_c, hx, hy, 16, -8), "bhead", 0.18), p["body"], drop=drop(0.6, 0.9))
    sx_, sy_ = p["snout"]
    snout_c = (head_c[0] - hx * 0.7, head_c[1] + hy * 0.38)
    head.add(cut(ellipse(*snout_c, sx_, sy_, 14, -6), "bsnout", 0.12), p["belly"])
    nose_c = (snout_c[0] - sx_ * 0.78, snout_c[1] - sy_ * 0.2)
    head.add(cut(ellipse(*nose_c, DEER_NOSE_RADIUS, DEER_NOSE_RADIUS * 0.9, 12), "bnose", 0.06), p["nose"])
    eye_c = (head_c[0] - hx * 0.15, head_c[1] - hy * 0.12)
    head.add(ellipse(*eye_c, 1.5, 1.6, 10), EYE)
    head.add(ellipse(head_c[0] + hx * 0.2, head_c[1] + hy * 0.3, 1.9, 1.3, 8), darker(CHEEK, 0.92))
    nod = q["nod"]
    for pts, c, _ in head.pieces:
        s.add(T(rotate(pts, *neck_root, nod)), c)
    collar_c = (neck_root[0] - 1.4, neck_root[1] + 0.6)
    # Out to the neck's front edge, so no sliver of the neck shows in front of it.
    collar = [(collar_c[0] - 5.2, collar_c[1] - 1.4), (collar_c[0] - 0.6, collar_c[1] - 4.0),
              (collar_c[0] + 2.6, collar_c[1] - 0.2), (collar_c[0] - 2.2, collar_c[1] + 2.9)]
    s.add(T(cut(collar, "bcollar", 0.1)), "#C8372D")
    s.add(T(ellipse(collar_c[0] - 1.8, collar_c[1] + 3.0, 2.0, 2.0, 10)), GOLD)
    band = [(-1.2, -ry * 0.98), (1.8, -ry * 0.98), (2.4, ry * 0.98), (-0.6, ry * 0.98)]
    s.add(T(band), "#C8372D", clip=f"bodyb{pose}")
    s.add(T(ellipse(0.6, -ry * 0.5, 1.8, 1.8, 10)), GOLD)
    tmp = Sheet()
    leg(tmp, *shoulder, *fa, p["body"], f"b{pose}nf")
    leg(tmp, *hip, *fb, p["body"], f"b{pose}nb")
    for pts, c, _ in tmp.pieces:
        s.add(T(pts), c)
    rein = (collar_c[0] + REIN_ON_COLLAR[0], collar_c[1] + REIN_ON_COLLAR[1])
    return s, dict(collar=T([collar_c])[0], rein=T([rein])[0], girth=T([(rx * 0.8, -ry * 0.1)])[0],
                   nose=T(rotate([nose_c], *neck_root, nod))[0])


# ------------------------------------------------------------------ Santa, facing us
def santa(s: Sheet, x: float, y: float):
    """Santa sitting, facing us, his middle at (x, y) on the sleigh's floor (its side hides him from the belt down).
    One hand waving; the other holding the reins out towards the reindeer (to the left). Returns where the reins leave
    that fist ([REIN_GRIP]) and his hat's top."""
    def P(pts):
        return [(x + px, y + py) for px, py in pts]
    coat = cut([(-12.0, 2.0), (-12.6, -8.0), (-10.4, -15.0), (-5.0, -18.6), (5.0, -18.6), (10.4, -15.0), (12.6, -8.0), (12.0, 2.0)], "s-coat", 0.25)
    hand = (-17.2, -9.0)
    # The coat's under-paper under the arms: so between the waving arm and the coat it is covered by the sleeve rather
    # than left as a sliver of shadow.
    s.add(move(P(coat), max(thick(1.0), 2.0), thick(1.3)), darker(SANTA_RED, 0.74))
    s.add(P(cut(capsule(-9.6, -14.0, -15.6, -9.4, 5.2, 4.8), "s-arm", 0.12)), darker(SANTA_RED, 0.9))
    s.add(P(cut(capsule(9.4, -14.4, 14.6, -24.0, 5.2, 4.6), "s-arm2", 0.12)), darker(SANTA_RED, 0.9))
    s.add(P(coat), SANTA_RED)
    s.add(P(cut([(-2.4, -18.4), (2.4, -18.4), (2.8, 2.0), (-2.8, 2.0)], "s-fur", 0.15)), WHITE)
    # The belt and its buckle go down behind the sleigh's rim (RIM_Y, 4.2 units above Santa's middle), which is added after
    # Santa. The buckle -- a gold frame round a black hole, each band [SANTA_MIN_BAND_UNITS] -- stands with its hole
    # [BUCKLE_LOWER_BAR_SHOWS] above the rim, so at least [SANTA_MIN_BAND_UNITS] of its lower bar shows whatever the rim's
    # cut does, and its frame goes on under the rim; the belt is drawn a unit taller than in v5.15B, so the hole stays on it
    # (the frame's top bar stands above the belt, as it did) (until v5.15C2 the buckle sat on the rim, which left 1.33 units
    # of its lower bar, 1.66 px on the BV6600).
    s.add(P(cut([(-12.4, -8.8), (12.4, -8.8), (12.6, -3.8), (-12.6, -3.8)], "s-belt", 0.12)), BELT)
    f, hole = thick(2.0), thick(2.0)
    bw = max(3.2, hole / 2 + f)
    hole_bottom = RIM_Y - y - BUCKLE_LOWER_BAR_SHOWS
    by = hole_bottom - hole / 2
    s.add(P(cut([(-bw, by - hole / 2 - f), (bw, by - hole / 2 - f), (bw, -3.6), (-bw, -3.6)], "s-buckle", 0.08)), GOLD)
    s.add(P([(-hole / 2 - 0.2, by - hole / 2), (hole / 2 + 0.2, by - hole / 2), (hole / 2 + 0.2, by + hole / 2),
             (-hole / 2 - 0.2, by + hole / 2)]), BELT)
    s.add(P(cut(capsule(-14.6, -10.2, -16.0, -9.2, 5.0, 5.0, n=3), "s-cuff", 0.08)), WHITE)
    s.add(P(cut(ellipse(*hand, 2.9, 2.6, 12), "s-mitten", 0.08)), MITTEN)
    s.add(P(cut(capsule(14.0, -22.6, 14.4, -24.4, 5.0, 5.0, n=3), "s-cuff2", 0.08)), WHITE)
    s.add(P(cut(ellipse(15.2, -27.6, 2.9, 3.4, 12, 10), "s-mitten2", 0.08)), MITTEN)
    s.add(P(cut(ellipse(12.8, -26.4, 1.4, 2.2, 8, -30), "s-thumb", 0.06)), MITTEN)
    s.add(P(cut(ellipse(0.0, -26.0, 8.4, 8.0, 16), "s-face", 0.15)), SKIN)
    # The beard's lowest four points 0.6 units higher than the drawing of v5.15B: so the white of the coat's fur between the
    # beard and the buckle, raised in v5.15C2, keeps a square of 2 px on the BV6600 at its middle (the rule
    # `tests/test_sleigh.py` holds); towards its ends it narrows, as in v5.15B's drawing.
    beard = [(-9.4, -26.0), (-9.6, -21.0), (-8.0, -17.0), (-6.0, -14.4), (-3.6, -13.2), (-1.2, -14.2), (1.2, -13.0), (3.6, -14.0),
             (6.0, -14.6), (8.0, -17.2), (9.6, -21.0), (9.4, -26.0), (6.4, -23.0), (3.0, -22.0), (-3.0, -22.0), (-6.4, -23.0)]
    s.relief(P(cut(beard, "s-beard", 0.2)), WHITE, drop=drop(0.5, 0.8), k=0.86)
    s.add(P(cut(ellipse(-4.4, -25.2, 2.3, 2.0, 10), "s-cheekl", 0.06)), CHEEK)
    s.add(P(cut(ellipse(4.4, -25.2, 2.3, 2.0, 10), "s-cheekr", 0.06)), CHEEK)
    s.add(P(cut([(-6.4, -22.6), (-3.6, -24.6), (0.0, -23.2), (3.6, -24.6), (6.4, -22.6), (3.4, -21.2), (0.0, -22.0), (-3.4, -21.2)], "s-moustache", 0.1)), WHITE)
    s.add(P(cut(ellipse(0.0, -24.6, 2.0, 1.8, 10), "s-nose", 0.06)), NOSE)
    s.add(P(ellipse(-3.0, -28.2, 1.25, 1.4, 10)), EYE)
    s.add(P(ellipse(3.0, -28.2, 1.25, 1.4, 10)), EYE)
    brow = thick(1.8)
    s.add(P(cut(capsule(-5.2, -31.0, -1.6, -31.4, brow, brow, n=2), "s-browl", 0.05)), WHITE)
    s.add(P(cut(capsule(1.6, -31.4, 5.2, -31.0, brow, brow, n=2), "s-browr", 0.05)), WHITE)
    hat = [(-8.6, -33.2), (-5.4, -39.6), (1.0, -43.4), (7.4, -43.0), (12.6, -39.4), (15.4, -35.2), (13.0, -35.0), (9.4, -37.4), (8.6, -33.2)]
    s.relief(P(cut(hat, "s-hat", 0.15)), SANTA_RED, drop=drop(0.6, 0.8))
    s.add(P(cut([(-10.0, -34.6), (10.0, -34.6), (10.4, -30.6), (-10.4, -30.6)], "s-band", 0.12)), WHITE)
    s.add(P(cut(ellipse(15.0, -34.2, 3.6, 3.6, 12), "s-pompom", 0.1)), WHITE)
    grip = (hand[0] + REIN_GRIP[0], hand[1] + REIN_GRIP[1])
    return (x + grip[0], y + grip[1]), y - 43.4


def present(s: Sheet, x, y, w, h, box, ribbon, seed, rot=0.0, bow=True):
    """A present: its box over its under-paper, the ribbon's cross, and a bow on top where nothing lies on it."""
    c = (x + w / 2, y + h / 2)
    r = thick(2.2) / 2
    s.relief(rotate(cut([(x, y), (x + w, y), (x + w, y + h), (x, y + h)], seed, 0.12), *c, rot), box, drop=drop(0.6, 0.8))
    s.add(rotate([(c[0] - r, y), (c[0] + r, y), (c[0] + r, y + h), (c[0] - r, y + h)], *c, rot), ribbon)
    s.add(rotate([(x, c[1] - r), (x + w, c[1] - r), (x + w, c[1] + r), (x, c[1] + r)], *c, rot), ribbon)
    if bow:
        s.add(rotate(cut(ellipse(c[0] - 2.0, y - 1.2, 2.2, max(1.4, thick(2.0) / 2), 8, -25), seed + "b1", 0.05), *c, rot), ribbon)
        s.add(rotate(cut(ellipse(c[0] + 2.0, y - 1.2, 2.2, max(1.4, thick(2.0) / 2), 8, 25), seed + "b2", 0.05), *c, rot), ribbon)


#: The rim of the sleigh's side, under Santa's belt, in the sleigh's frame: what is seen of him ends there.
RIM_Y = 7.8

#: How much of the buckle's lower bar shows above the rim, in units: [SANTA_MIN_BAND_UNITS] and the most the rim's cut
#: (0.25 units each way) can take of it.
BUCKLE_LOWER_BAR_SHOWS = 2.25

#: Where the reins leave Santa's fist, from its middle, in units: at its top, towards the reindeer, so they cross only the
#: top of the sleigh's front scroll, which stays one piece (v5.15C2: from the fist's middle they cut the glove and the
#: scroll into pieces under 2 px).
REIN_GRIP = (-1.8, -1.6)

#: The presents piled behind Santa, back to front: (x, y, w, h, box, ribbon, seed, turn, bow). Each stands clear enough
#: of the others and of the sleigh's rising back that what shows of it is a present, not a sliver (the 2 px rule), and
#: only the two on top of the pile carry a bow -- the others' would be under the presents over them.
PRESENTS = (
    (45.6, -8.0, 8.4, 8.0, "#2F6B45", GOLD, "b-g1", -6, False),
    (61.2, -11.0, 7.8, 7.6, "#3D5A9E", GOLD, "b-g3", 8, True),
    (51.4, -16.6, 8.8, 8.4, "#F1E4C8", "#C8372D", "b-g2", 4, True),
    (53.2, -7.4, 9.2, 8.6, "#E9B949", "#C8372D", "b-g4", -3, False),
)


def sleigh():
    """The red sleigh with gold scrolls, Santa in it and the presents piled behind him."""
    s = Sheet()
    red = "#BE2F29"
    # The maintainer, on the second drawing (2026-10-19): *«va bene B ma fai la slitta un poco più alta, babbo natale deve
    # sembrare seduto (come in A e C) e non in piedi»*: the side rises to just under his belt, so the rim hides his lap;
    # the presents behind him stand a little higher to stay in sight.
    for g in PRESENTS:
        present(s, *g)
    hand, hat_top = santa(s, 32.0, 12.0)
    side = [(3.4, 4.0), (7.6, 2.0), (11.6, 4.6), (13.0, RIM_Y), (26.0, RIM_Y), (44.0, RIM_Y), (58.0, 7.2), (64.0, 2.4), (67.0, -2.0),
            (72.0, -3.0), (75.0, -0.4), (73.6, 6.0), (71.0, 19.0), (66.0, 27.4), (14.0, 28.0), (8.4, 24.6), (5.2, 17.6), (3.6, 8.0)]
    # The side's under-paper shows below the side and at its back, not behind Santa and the presents above the rim,
    # where it would be a sliver between them and the rim.
    s.clip("b-under", [[(-20.0, -40.0), (10.0, -40.0), (10.0, RIM_Y), (66.0, RIM_Y), (66.0, -40.0), (110.0, -40.0),
                        (110.0, 60.0), (-20.0, 60.0)]])
    s.add(move(cut(side, "b-side", 0.25), *drop(1.2, 1.6)), darker(red, 0.74), clip="b-under")
    s.add(cut(side, "b-side", 0.25), red)
    s.add(cut([(8.6, 21.6), (70.0, 21.2), (69.2, 24.0), (9.6, 24.4)], "b-band", 0.12), GOLD)
    s.add(cut(ellipse(7.4, 5.0, 2.6, 2.6, 12), "b-s1", 0.06), GOLD)
    s.add(cut(ellipse(71.6, 0.2, 2.6, 2.6, 12), "b-s2", 0.06), GOLD)
    s.add(cut(polyline_band([(26.0, 15.0), (32.0, 12.6), (38.0, 15.0), (44.0, 12.6), (50.0, 15.0)], thick(2.2)), "b-curl", 0.06), GOLD)
    for i, x in enumerate((20.0, 54.0)):
        s.add(cut([(x, 27.4), (x + 2.6, 27.4), (x + 2.6, 33.6), (x, 33.6)], f"b-strut{i}", 0.1), darker(GOLD, 0.82))
    runner = [(75.0, 32.4), (70.0, 35.2), (40.0, 35.8), (12.0, 35.8), (3.6, 34.6), (-0.6, 30.6), (0.0, 26.2), (3.2, 25.0), (5.2, 27.2), (3.2, 29.0)]
    s.add(cut(polyline_band(runner, thick(3.0)), "b-runner", 0.1), darker(GOLD, 0.82))
    return s, dict(hand=hand, front=(6.0, 20.0), sack=(56.0, -2.0), hat_top=hat_top)


#: Where the pieces stand in the group, in its units (the flight line at y 0; the drawer puts the group's middle on the
#: flight point): the two reindeer's middles (Rudolph in
#: front, at the left), the sleigh's frame origin.
LEAD_DEER = (-74.0, 4.0)
NEAR_DEER = (-32.0, 4.0)
SLEIGH_AT = (-8.0, -18.0)

NAMES = ["santa_sleigh"] + [f"santa_reindeer_{p}" for p in range(4)]


def build():
    """Everything this writes, as {path: bytes}, and the table's numbers."""
    out, table = {}, {}
    sheet, pts = sleigh()
    body = sheet.body()
    box = content_box(body, (-12, -50, 100, 92), PX)
    svg = svg_document(body, box, PX, "Santa's sleigh (v5.15, the maintainer's «B» with the side raised, 2026-10-19): the "
                       "sleigh, Santa facing us and the presents, fixed art. Generated by tools/assets/sleigh/build_sleigh.py: "
                       "edit that, not this.")
    out[SVG_DIR / "santa_sleigh.svg"] = svg.encode()
    out[RES / "santa_sleigh.png"] = render_body(body, box, PX)
    table["sleigh"] = dict(ox=box[0] + SLEIGH_AT[0], oy=box[1] + SLEIGH_AT[1], w=box[2], h=box[3])
    table["hand"] = (pts["hand"][0] + SLEIGH_AT[0], pts["hand"][1] + SLEIGH_AT[1])
    table["front"] = (pts["front"][0] + SLEIGH_AT[0], pts["front"][1] + SLEIGH_AT[1])
    table["sack"] = (pts["sack"][0] + SLEIGH_AT[0], pts["sack"][1] + SLEIGH_AT[1])
    table["santa_top"] = pts["hat_top"] + SLEIGH_AT[1]
    table["santa_bottom"] = RIM_Y + SLEIGH_AT[1]
    table["right"] = box[0] + SLEIGH_AT[0] + box[2]
    poses = []
    for pose in range(4):
        dsheet, dpts = reindeer(pose)
        dbody = dsheet.body()
        dbox = content_box(dbody, (-36, -40, 60, 64), PX)
        name = f"santa_reindeer_{pose}"
        dsvg = svg_document(dbody, dbox, PX, f"A reindeer of Santa's sleigh (v5.15), pose {pose} of the four of its gallop, "
                            "facing left, its body's middle at (0,0); Rudolph is this with a red nose the renderer draws over "
                            "it. Fixed art. Generated by tools/assets/sleigh/build_sleigh.py: edit that, not this.")
        out[SVG_DIR / f"{name}.svg"] = dsvg.encode()
        out[RES / f"{name}.png"] = render_body(dbody, dbox, PX)
        poses.append(dict(ox=dbox[0], oy=dbox[1], w=dbox[2], h=dbox[3], **dpts))
    table["poses"] = poses
    table["left"] = min(LEAD_DEER[0] + p["ox"] for p in poses)
    out[TABLE] = kotlin_table(table).encode()
    return out, table


def f(x: float) -> str:
    return f"{x:.2f}f"


def fa(values) -> str:
    return "floatArrayOf(" + ", ".join(f(v) for v in values) + ")"


def kotlin_table(t) -> str:
    P = t["poses"]
    return f'''package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * Santa's sleigh (v5.15), in units of the group (the flight line at y 0, the reindeer to the left; the drawer puts the
 * group's middle, halfway between [LEFT] and [RIGHT], on the flight point): where each piece blits, where the cords and the gifts start, and how large the group is drawn. **Generated**
 * by `tools/assets/sleigh/build_sleigh.py` from the geometry it draws the SVGs with; edit that script, not this file.
 */
internal object SantaSleighTable {{
    /** The sleigh with Santa and the presents, and where it blits. */
    val SLEIGH_RES = R.drawable.santa_sleigh
    const val SLEIGH_X = {f(t["sleigh"]["ox"])}
    const val SLEIGH_Y = {f(t["sleigh"]["oy"])}

    /** The reindeer's four poses, the same series for both; where each blits from the reindeer's middle. */
    val DEER_RES = intArrayOf(R.drawable.santa_reindeer_0, R.drawable.santa_reindeer_1, R.drawable.santa_reindeer_2, R.drawable.santa_reindeer_3)
    val DEER_X = {fa([p["ox"] for p in P])}
    val DEER_Y = {fa([p["oy"] for p in P])}

    /** The two reindeer's middles: Rudolph leading at the left, the second behind him. */
    const val LEAD_X = {f(LEAD_DEER[0])}
    const val LEAD_Y = {f(LEAD_DEER[1])}
    const val NEAR_X = {f(NEAR_DEER[0])}
    const val NEAR_Y = {f(NEAR_DEER[1])}

    /** In each pose, from the reindeer's middle: the collar (where the trace from the one in front ends), the collar's front (where the rein from Santa's hand ends), the girth (where its trace to the one behind, or to the sleigh, starts), the nose. */
    val COLLAR_X = {fa([p["collar"][0] for p in P])}
    val COLLAR_Y = {fa([p["collar"][1] for p in P])}
    val REIN_X = {fa([p["rein"][0] for p in P])}
    val REIN_Y = {fa([p["rein"][1] for p in P])}
    val GIRTH_X = {fa([p["girth"][0] for p in P])}
    val GIRTH_Y = {fa([p["girth"][1] for p in P])}
    val NOSE_X = {fa([p["nose"][0] for p in P])}
    val NOSE_Y = {fa([p["nose"][1] for p in P])}

    /** Rudolph's nose: the radius of the red disc drawn over the reindeer's own, and its colour, by day and by night. */
    const val RUDOLPH_NOSE_RADIUS = {f(RUDOLPH_NOSE_RADIUS)}
    const val RUDOLPH_NOSE_COLOUR = 0xFF{RUDOLPH_NOSE[1:]}.toInt()

    /** Where the reins leave Santa's fist, and the front of the sleigh where the second reindeer's trace ends. */
    const val HAND_X = {f(t["hand"][0])}
    const val HAND_Y = {f(t["hand"][1])}
    const val FRONT_X = {f(t["front"][0])}
    const val FRONT_Y = {f(t["front"][1])}

    /** Where a gift leaves the pile behind Santa. */
    const val SACK_X = {f(t["sack"][0])}
    const val SACK_Y = {f(t["sack"][1])}

    /** The group's extent across, its middle drawn on the flight point. */
    const val LEFT = {f(t["left"])}
    const val RIGHT = {f(t["right"])}

    /** What is seen of Santa: his hat's top to the sleigh's rim. */
    const val SANTA_TOP = {f(t["santa_top"])}
    const val SANTA_BOTTOM = {f(t["santa_bottom"])}

    /**
     * How tall Santa is drawn against an adult walking on the near row of the pavement: the maintainer's choice of
     * 2026-10-19, from the sizes shown beside the people (*«fallo più piccolo, così è gigante rispetto ai pedoni»*).
     */
    const val SANTA_TO_PEDESTRIAN = {f(SANTA_TO_PEDESTRIAN)}

    /** The narrowest band of the drawing, in units: 2 px on the BV6600 at that size, and a quarter more. */
    const val SANTA_MIN_BAND_UNITS = {f(SANTA_MIN_BAND_UNITS)}

    /** The cords, in units: the reins and the traces, never narrower than [SANTA_MIN_BAND_UNITS]. */
    const val SANTA_REIN_UNITS = {f(thick(1.4))}
    const val SANTA_TRACE_UNITS = {f(thick(1.6))}
    const val REIN_COLOUR = 0xFF{REIN_COLOUR[1:]}.toInt()
    const val TRACE_COLOUR = 0xFF{TRACE_COLOUR[1:]}.toInt()
}}
'''


def preview(ratio: float, folder: Path) -> int:
    """The group at [ratio] -- its bands thickened for that size -- into [folder], with its table as JSON."""
    global SANTA_TO_PEDESTRIAN, SANTA_MIN_BAND_UNITS
    SANTA_TO_PEDESTRIAN = ratio
    SANTA_MIN_BAND_UNITS = round(2.0 / px_per_unit_on_the_bv6600(ratio) * 1.25, 2)
    out, table = build()
    folder.mkdir(parents=True, exist_ok=True)
    import json
    for p, data in out.items():
        if p.suffix in (".png", ".svg"):
            (folder / p.name).write_bytes(data)
    table["px_per_unit"] = px_per_unit_on_the_bv6600(ratio)
    table["min_units"] = SANTA_MIN_BAND_UNITS
    table["rein_units"] = thick(1.4)
    table["trace_units"] = thick(1.6)
    (folder / "table.json").write_text(json.dumps(table, indent=1))
    print(f"ratio {ratio}: {table['px_per_unit']:.3f} px a unit, the group {(table['right'] - table['left']) * table['px_per_unit']:.0f} px, "
          f"the narrowest band {SANTA_MIN_BAND_UNITS} units, into {folder}")
    return 0


def main(argv) -> int:
    if "--preview-size" in argv:
        i = argv.index("--preview-size")
        return preview(float(argv[i + 1]), Path(argv[i + 2]))
    res, check = "--res" in argv, "--check" in argv
    out, table = build()
    measured = table["santa_bottom"] - table["santa_top"]
    if abs(measured - SANTA_UNITS_TALL) > 0.05:
        print(f"SANTA_UNITS_TALL is {SANTA_UNITS_TALL}, the drawing measures {measured:.2f}: correct it")
        return 1
    if check:
        stale = [p for p, data in out.items() if not p.exists() or p.read_bytes() != data]
        for p in stale:
            print(f"stale: {p.relative_to(REPO)}")
        return 1 if stale else 0
    for p, data in out.items():
        if p.parent == RES and not res:
            continue
        p.write_bytes(data)
        print(f"wrote {p.relative_to(REPO)} ({len(data)} B)")
    print(f"BV6600: {px_per_unit_on_the_bv6600():.3f} px a unit, the group {(table['right'] - table['left']) * px_per_unit_on_the_bv6600():.0f} px "
          f"across, Santa {measured * px_per_unit_on_the_bv6600():.1f} px tall against a pedestrian's {PEDESTRIAN_PX_BV6600:.1f}; "
          f"the narrowest band {SANTA_MIN_BAND_UNITS} units")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
