#!/usr/bin/env python3
"""Builds the three v4.25 people concepts -- Stampino, Rilievo, Bambola -- as SVG sources
and renders them through the project's own rasteriser.

Run from ``tools/assets`` with the pinned venv:

    /home/bober/.venvs/paperscrape-assets/bin/python concepts/people/build_people_concepts.py

For each concept it writes ``concepts/people/<concept>/svg/<name>.svg`` and ``.png`` for the
whole family -- four characters x (three walk frames x two seasons + a window bust x two
seasons + a car bust x two seasons) = 40 sprites -- and ``sprites.concept.json`` in the
registry's own schema, measured off the rendered pixels.

Every canvas is the shipped one (walk 123x255, window 159x171, car 141x132), so no call
site, anchor or byte of the decoded budget moves, exactly as the v4.23 sky concepts did.
Edges are cut, not struck: every shape is a polygon whose vertices carry a small
deterministic wobble written here into the coordinates. No feature is thinner than three
units, because the GL backend minifies with a plain bilinear tap and no mipmaps
(GlTextureAtlas.kt), and at the ~1:8 reduction a walker gets anything thinner flickers.

Skin is one flat colour per character -- the character's own shipped tone -- so
``tools/generate_skin_variants.py`` can still produce the tone copies later.
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

# ---------------------------------------------------------------- palette (all shipped paint)
SKIN = {"man": "#DCA97C", "woman": "#F0C9A6", "boy": "#A9714B", "girl": "#EFB994"}
HAIR = {"man": "#2B2A33", "woman": "#8C5A38", "boy": "#3B2A22", "girl": "#C98F5A"}
DARK = "#2B2A33"
OUTLINE = "#4A4038"
CREAM = "#F4F1EA"
SUMMER = {
    "man": dict(top="#4E9FB5", bottom="#EFDFC4", belt="#C6AC78", shoe=DARK),
    "woman": dict(top="#E4623E", bottom=None, belt="#F7CE64", shoe="#8C3A2A"),
    "boy": dict(top="#5FA85A", bottom="#3E6FA8", belt=None, shoe=CREAM, cap="#3F8A4A"),
    "girl": dict(top="#F7CE64", bottom=None, belt="#E4623E", shoe="#E4623E", bow="#E4623E"),
}
WINTER = {
    "man": dict(top="#47698F", bottom="#3A3F4A", belt="#E4623E", shoe="#5A3E2B", hat="#2F4F73"),
    "woman": dict(top="#BF4130", bottom="#3A3F4A", belt="#F4F1EA", shoe="#5A3E2B", hat=CREAM),
    "boy": dict(top="#3F8A4A", bottom="#3A3F4A", belt=CREAM, shoe="#5A3E2B", hat="#3F8A4A"),
    "girl": dict(top="#F2A03C", bottom="#3A3F4A", belt="#BF4130", shoe="#5A3E2B", hat="#E4623E"),
}
KINDS = ("man", "woman", "boy", "girl")
ADULT = {"man": True, "woman": True, "boy": False, "girl": False}
CHILD_TOP = 84 - 62  # child content is 62 units tall, adult 80 (SceneSpace / VehiclePedestrianScaleTest)
ADULT_TOP = 84 - 80


# ------------------------------------------------------------------------------ geometry
def wobble(seed: str, index: int, amplitude: float) -> tuple[float, float]:
    """A deterministic hand-cut wobble for one vertex, written into the coordinates."""
    digest = hashlib.sha256(f"{seed}:{index}".encode()).digest()
    dx = (digest[0] / 255.0 - 0.5) * 2 * amplitude
    dy = (digest[1] / 255.0 - 0.5) * 2 * amplitude
    return dx, dy


def cut(points, seed: str, amplitude: float = 0.35):
    return [(x + wobble(seed, i, amplitude)[0], y + wobble(seed, i, amplitude)[1])
            for i, (x, y) in enumerate(points)]


def oval(cx, cy, rx, ry, n=12, start=0.0):
    return [(cx + rx * math.cos(start + 2 * math.pi * i / n), cy + ry * math.sin(start + 2 * math.pi * i / n))
            for i in range(n)]


def chamfered(x0, y0, x1, y1, c=1.5):
    """A rectangle whose corners are cut off -- scissors do not turn a compass corner."""
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


def quad(x0, y0, w0, x1, y1, w1):
    """A limb: top centre (x0,y0) width w0 to bottom centre (x1,y1) width w1."""
    return [(x0 - w0 / 2, y0), (x0 + w0 / 2, y0), (x1 + w1 / 2, y1), (x1 - w1 / 2, y1)]


def mix(hex_a: str, hex_b: str, t: float) -> str:
    a = [int(hex_a[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(hex_b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#%02X%02X%02X" % tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def shade(color: str, t: float = 0.22) -> str:
    return mix(color, DARK, t)


#: The face variant asked for in phase 2: Concept B drawn exactly as it was, plus a hint of eyes.
#:
#: B dropped the face for one reason only -- at the ~1:7 reduction a walker gets, a two-pixel mark
#: appeared and disappeared as the figure moved, because the GL backend minified with a single
#: bilinear tap and no pre-reduced copy. v4.25 removes that constraint (see SpriteDetailLevel), so
#: the question "does a face read at this size" can finally be answered by looking instead of being
#: settled by the sampler. Both families are generated so the maintainer can compare them on the
#: same scenes at the size they are actually seen at.
EYED = {"rilievo_occhi": "rilievo"}


def drawing_style(style: str) -> str:
    """The style whose *shapes* to draw. The eyed variant draws Rilievo's."""
    return EYED.get(style, style)


def has_eyes(style: str) -> bool:
    """Whether this style puts a face on a three-quarter head."""
    return style in EYED


@dataclass
class Part:
    points: list
    fill: str
    opacity: float | None = None   # only the ground shadow uses it
    relief: bool = True            # Rilievo draws an under-paper beneath it
    outline: bool = True           # Bambola includes it in the outer outline


@dataclass
class Sprite:
    name: str
    width_units: int
    height_units: int
    parts: list = field(default_factory=list)
    #: `(cx, cy, rx, ry)` of the head, recorded by whichever builder drew it, so a face can be
    #: added afterwards without every builder growing a style argument.
    head: tuple | None = None

    def add(self, points, fill, **kw):
        self.parts.append(Part(list(points), fill, **kw))


# ------------------------------------------------------------------------------ styles
def emit_svg(sprite: Sprite, style: str) -> str:
    w, h = sprite.width_units * UNIT, sprite.height_units * UNIT
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
           f'viewBox="0 0 {sprite.width_units} {sprite.height_units}">',
           f"<!-- v4.25 people concept '{style}': {sprite.name}. Generated by "
           "tools/assets/concepts/people/build_people_concepts.py; the wobble is written into the "
           "points, never computed at runtime. -->"]

    def poly(points, fill, extra=""):
        pts = " ".join(f"{x:.2f},{y:.2f}" for x, y in points)
        return f'<polygon points="{pts}" fill="{fill}"{extra}/>'

    if style == "bambola":
        # The outer outline: the whole figure once more underneath itself, filled and stroked
        # in the outline colour, so overlapping strokes merge into one band round the union.
        out.append(f'<g fill="{OUTLINE}" stroke="{OUTLINE}" stroke-width="1.5" stroke-linejoin="round">')
        for p in sprite.parts:
            if p.opacity is None and p.outline:
                out.append(poly(p.points, OUTLINE))
        out.append("</g>")
    for p in sprite.parts:
        if p.opacity is not None:
            out.append(poly(p.points, p.fill, f' opacity="{p.opacity}"'))
            continue
        if drawing_style(style) == "rilievo" and p.relief:
            # The paper underneath: the same cut, offset down and to the right, in a darker
            # tone of the piece's own colour -- the Quercia larga's recipe.
            #
            # The marker is what `tests/test_relief.py` reads. Rilievo has no outer outline, so the
            # under-paper is the only thing separating one piece from the next, and a hand edit that
            # dropped it would leave a figure that still renders and no longer reads. A comment
            # rather than a group because each under-paper has to sit immediately beneath its own
            # piece: gathering them would be a z-order change, not an annotation.
            under = [(x + RELIEF_OFFSET[0], y + RELIEF_OFFSET[1]) for x, y in p.points]
            out.append("<!-- paperscrape-relief -->")
            out.append(poly(under, shade(p.fill, 0.34)))
        out.append(poly(p.points, p.fill))
    out.append("</svg>")
    return "\n".join(out)


def add_eye_hint(s: Sprite) -> None:
    """Two dark dots on a three-quarter head, sized from that head rather than fixed.

    The head faces +x, so the near eye sits well off centre and the far one close to the silhouette
    edge -- a frontal pair on a three-quarter face reads as a head turned the wrong way. The radius
    is a fixed fraction of the head's own half-height, so the child's smaller head gets a smaller
    mark instead of the same one: on an adult walker that is 3.2 units, which is 1.4 screen pixels
    at the reduction a walker is drawn at.

    No relief and no outline: an under-paper beneath a mark this small would double its area and
    turn two dots into two smudges, which is the opposite of the point.
    """
    if s.head is None:
        return
    hx, hy, rx, ry = s.head
    # Sized from the *narrower* half-axis: the seated bust's head is tall and narrow, and taking the
    # radius from its height alone gave two marks wider than the gap between them, which merged into
    # one blot.
    r = round(min(rx, ry) * 0.17, 2)
    # **The space between the eyes is one eye wide**, and that is a screen measurement rather than a
    # taste: at 2.7 the gap was 1.25 local units, which on the walker's measured draw factor is
    # 0.53 of a screen pixel. Half a pixel cannot separate anything, so the pair rendered as one
    # dark bar -- a visor rather than two eyes. At four the gap is 1.52 px, the same as each eye,
    # and the three read as three.
    gap = r * 4.0
    cx_eyes = hx + rx * 0.30
    ey = hy + ry * 0.08
    for i, ex in enumerate((cx_eyes - gap / 2, cx_eyes + gap / 2)):
        s.add(cut(oval(ex, ey, r, r, 8), s.name + f"eyehint{i}", 0.10), DARK, relief=False, outline=False)


# ------------------------------------------------------------------------------ walkers
def ground_shadow(s: Sprite, cx=20.5, rx=13.0):
    s.add(oval(cx, 82.4, rx, 2.1, 16), DARK, opacity=0.16)


def walker_stampino(kind: str, season: str, frame: int) -> Sprite:
    """Three-quarter figure facing +x. Head, one body block, two legs, feet, hair. Nothing else."""
    s = Sprite(f"person_{kind}_{season}_walk{frame}", 41, 85)
    seed = s.name
    adult = ADULT[kind]
    top = ADULT_TOP if adult else CHILD_TOP
    gar = (SUMMER if season == "summer" else WINTER)[kind]
    skin, hair = SKIN[kind], HAIR[kind]
    cx = 20.5
    head_ry = 12.0 if adult else 10.0
    head_rx = 10.5 if adult else 9.0
    head_cy = top + head_ry + 1
    chin = head_cy + head_ry
    shoulder = chin + 1.5
    hip = 57 if adult else 63
    stride = (7.0, 0.0, -7.0)[frame]
    bob = 1.0 if frame == 1 else 0.0
    ground_shadow(s, rx=13 if adult else 11)

    leg_w = 7.0 if adult else 6.0
    leg_col = gar["bottom"] or skin
    far_col = shade(leg_col, 0.18)
    # legs: far leg first (darker paper), then the near one
    s.add(cut(quad(cx - 3, hip - 2, leg_w, cx - 3 - stride, 82 - bob, leg_w), seed + "L1"), far_col)
    s.add(cut(quad(cx + 3, hip - 2, leg_w, cx + 3 + stride, 82 - bob, leg_w), seed + "L2"), leg_col)
    # feet, toes forward
    for i, (fx, col) in enumerate(((cx - 3 - stride, shade(gar["shoe"], 0.18)), (cx + 3 + stride, gar["shoe"]))):
        s.add(cut([(fx - 4, 79.5 - bob), (fx + 5, 79.5 - bob), (fx + 6, 83.5 - bob), (fx - 4, 83.5 - bob)], seed + f"F{i}"), col)
    # body: one block, shoulders to hips, arms folded into the silhouette
    hem = hip + (7 if season == "winter" else 0)
    if kind in ("woman", "girl") and season == "summer":
        hem = hip + 3
    sw = 13.0 if adult else 10.5    # half shoulder width
    hw = (16.0 if season == "summer" and kind in ("woman", "girl") else 10.5) if adult else (12.0 if kind == "girl" else 8.5)
    body = [(cx - sw, shoulder + 1), (cx + sw, shoulder + 1), (cx + sw + 1, shoulder + 14), (cx + hw, hem), (cx - hw, hem), (cx - sw - 1, shoulder + 14)]
    s.add(cut(body, seed + "B"), gar["top"])
    if gar.get("belt") and season == "summer" and kind == "man":
        s.add(cut([(cx - 10, hip - 4), (cx + 10, hip - 4), (cx + 10, hip - 1), (cx - 10, hip - 1)], seed + "belt"), gar["belt"])
    # neck + head
    s.add(cut([(cx - 3, chin - 3), (cx + 3.5, chin - 3), (cx + 3.5, shoulder + 2), (cx - 3, shoulder + 2)], seed + "N"), skin)
    hx, hy = cx + 0.5, head_cy
    if season == "winter" and kind == "woman":
        # hair behind the head, under the hat: a lobe on the back side
        s.add(cut(oval(hx - head_rx + 1, hy + 4, 4.5, 9.5, 10), seed + "hairw", 0.3), hair)
    if season == "winter" and kind == "boy":
        # the hood: a paper around the back of the head, open toward the face
        s.add(cut(oval(hx - 3, hy + 0.5, head_rx + 4.5, head_ry + 4, 14), seed + "hood", 0.3), gar["top"])
        s.add(cut(oval(hx - 1.5, hy + 0.5, head_rx + 1.5, head_ry + 1.5, 14), seed + "hoodin", 0.25), CREAM)
    s.add(cut(oval(cx + 0.5, head_cy, head_rx, head_ry, 14), seed + "H", 0.3), skin)
    # hair or hat: drawn toward the back (-x); the front is the face
    if season == "summer":
        if kind == "man":
            s.add(cut([(hx - head_rx - 0.5, hy - 2), (hx - head_rx + 1, hy - head_ry + 2), (hx - 3, hy - head_ry - 0.8), (hx + 6, hy - head_ry - 0.5), (hx + head_rx - 1, hy - head_ry + 3), (hx + 5, hy - head_ry + 3.5), (hx - 2, hy - head_ry + 5), (hx - head_rx + 3, hy - 1)], seed + "hair"), hair)
        elif kind == "woman":
            s.add(cut([(hx - head_rx - 3, hy + 9), (hx - head_rx - 2.5, hy - 4), (hx - head_rx + 1, hy - head_ry + 1), (hx - 2, hy - head_ry - 1), (hx + 7, hy - head_ry - 0.5), (hx + head_rx, hy - head_ry + 3), (hx + 4, hy - head_ry + 3), (hx - 3, hy - head_ry + 5), (hx - head_rx + 4, hy + 1), (hx - head_rx + 3, hy + 9)], seed + "hair"), hair)
            s.add(cut([(hx - head_rx + 1, hy - head_ry + 2.5), (hx + head_rx - 2, hy - head_ry + 1.5), (hx + head_rx - 1.5, hy - head_ry + 4.5), (hx - head_rx + 1.5, hy - head_ry + 5.5)], seed + "band"), gar["belt"])
        elif kind == "boy":
            s.add(cut([(hx - head_rx - 1, hy - 1), (hx - head_rx + 1, hy - head_ry + 1), (hx - 2, hy - head_ry - 1.5), (hx + 6, hy - head_ry - 1), (hx + head_rx - 1, hy - head_ry + 2.5), (hx + head_rx + 5, hy - head_ry + 3.5), (hx + head_rx + 5, hy - head_ry + 6.5), (hx + head_rx - 2, hy - head_ry + 6), (hx - head_rx + 3, hy - head_ry + 6.5)], seed + "cap"), gar["cap"])
        else:
            s.add(cut([(hx - head_rx - 1, hy + 2), (hx - head_rx + 1, hy - head_ry + 1), (hx - 2, hy - head_ry - 1), (hx + 6, hy - head_ry - 0.5), (hx + head_rx, hy - head_ry + 3), (hx + 4, hy - head_ry + 3.5), (hx - 3, hy - head_ry + 5), (hx - head_rx + 3, hy - 1)], seed + "hair"), hair)
            for i, bx in enumerate((hx - head_rx - 2.5, hx + head_rx + 1.5)):
                s.add(cut(oval(bx, hy + 4, 3.6, 5.2, 10), seed + f"bun{i}", 0.3), hair)
                s.add(cut(oval(bx, hy - 1.5, 2.2, 2.0, 8), seed + f"bow{i}", 0.2), gar["bow"])
    else:
        # scarf over the neck, then hat / hood
        s.add(cut([(cx - 8, chin - 2), (cx + 8, chin - 2), (cx + 8.5, chin + 4), (cx - 8, chin + 4)], seed + "scarf"), gar["belt"])
        if kind == "boy":
            pass  # the hood is drawn behind the head above
        else:
            brim = hy - head_ry + 4
            s.add(cut([(hx - head_rx - 1.5, brim), (hx - head_rx + 0.5, hy - head_ry - 3), (hx, hy - head_ry - 5), (hx + head_rx, hy - head_ry - 3.5), (hx + head_rx + 1.5, brim)], seed + "hat"), gar["hat"])
            s.add(cut([(hx - head_rx - 2, brim - 3.5), (hx + head_rx + 2, brim - 3.5), (hx + head_rx + 2, brim), (hx - head_rx - 2, brim)], seed + "hatband"), gar["belt"] if kind != "girl" else CREAM)
            if kind == "girl":
                s.add(cut(oval(hx, hy - head_ry - 5, 3.2, 3.0, 10), seed + "pom", 0.25), CREAM)
    return s


def walker_rilievo(kind: str, season: str, frame: int) -> Sprite:
    """Three-quarter figure facing +x, built as stacked papers: legs, body, two swinging arms
    with hands, neck, head, a cushion of hair lobes. Every piece carries an under-paper."""
    s = Sprite(f"person_{kind}_{season}_walk{frame}", 41, 85)
    seed = s.name
    adult = ADULT[kind]
    top = ADULT_TOP if adult else CHILD_TOP
    gar = (SUMMER if season == "summer" else WINTER)[kind]
    skin, hair = SKIN[kind], HAIR[kind]
    cx = 20.0
    head_ry = 11.5 if adult else 9.5
    head_rx = 10.5 if adult else 9.0
    head_cy = top + head_ry + 1.5
    chin = head_cy + head_ry
    shoulder = chin + 2
    hip = 56 if adult else 62
    stride = (6.5, 0.0, -6.5)[frame]
    swing = (-5.0, 0.0, 5.0)[frame]   # arms swing against the legs
    bob = 1.0 if frame == 1 else 0.0
    ground_shadow(s, cx=cx, rx=13 if adult else 11)

    leg_w = 6.5 if adult else 5.5
    leg_col = gar["bottom"] or skin
    s.add(cut(quad(cx - 3, hip - 2, leg_w, cx - 3 - stride, 81 - bob, leg_w - 0.5), seed + "L1"), shade(leg_col, 0.14))
    s.add(cut(quad(cx + 3, hip - 2, leg_w, cx + 3 + stride, 81 - bob, leg_w - 0.5), seed + "L2"), leg_col)
    for i, fx in enumerate((cx - 3 - stride, cx + 3 + stride)):
        s.add(cut(oval(fx + 1, 81.5 - bob, 5.0, 2.6, 10), seed + f"F{i}", 0.25), gar["shoe"] if i else shade(gar["shoe"], 0.14))
    # body: rounded block (chamfered), a little wider at the shoulders
    hem = hip + (8 if season == "winter" else (3 if kind in ("woman", "girl") else 0))
    sw = 11.5 if adult else 9.5
    hw = (14.5 if season == "summer" and kind in ("woman", "girl") else 10.0) if adult else (11.0 if kind == "girl" else 8.5)
    body = [(cx - sw + 2, shoulder), (cx + sw - 2, shoulder), (cx + sw, shoulder + 3), (cx + hw, hem - 2), (cx + hw - 2, hem), (cx - hw + 2, hem), (cx - hw, hem - 2), (cx - sw, shoulder + 3)]
    s.add(cut(body, seed + "B"), gar["top"])
    # back arm (behind the body would be hidden; drawn over, darker paper), then the front arm
    arm_len = 22 if adult else 16
    arm_w = 5.5 if adult else 4.5
    sleeve = gar["top"] if season == "winter" or kind != "man" else gar["top"]
    for i, (ax, sw_dir, col) in enumerate(((cx - sw + 1.5, -swing, shade(sleeve, 0.16)), (cx + sw - 1.5, swing, sleeve))):
        s.add(cut(quad(ax, shoulder + 2, arm_w, ax + sw_dir, shoulder + 2 + arm_len, arm_w - 0.5), seed + f"A{i}"), col)
        s.add(cut(oval(ax + sw_dir, shoulder + 3 + arm_len, 3.2, 3.2, 10), seed + f"Hd{i}", 0.25), skin if i else shade(skin, 0.14))
    # collar / scarf
    if season == "winter":
        s.add(cut([(cx - 8.5, chin - 1.5), (cx + 8.5, chin - 1.5), (cx + 9, chin + 4.5), (cx + 3, chin + 12), (cx - 2, chin + 12), (cx - 2, chin + 5), (cx - 8.5, chin + 4.5)], seed + "scarf"), gar["belt"])
    s.add(cut([(cx - 3, chin - 3), (cx + 3.5, chin - 3), (cx + 3.5, shoulder + 2), (cx - 3, shoulder + 2)], seed + "N"), skin, relief=False)
    hx, hy = cx + 0.5, head_cy
    if season == "winter" and kind == "woman":
        s.add(cut(oval(hx - head_rx + 1, hy + 4, 4.5, 9.5, 10), seed + "hairw", 0.3), hair)
    if season == "winter" and kind == "boy":
        s.add(cut(oval(hx - 3, hy + 0.5, head_rx + 4.5, head_ry + 4.5, 14), seed + "hood", 0.3), gar["top"])
        s.add(cut(oval(hx - 1.5, hy + 0.5, head_rx + 1.5, head_ry + 1.5, 14), seed + "hoodin", 0.25), CREAM, relief=False)
    s.add(cut(oval(cx + 0.5, head_cy, head_rx, head_ry, 14), seed + "H", 0.3), skin)
    s.head = (hx, hy, head_rx, head_ry)
    if season == "summer":
        lobes = {
            "man": [(hx - 5, hy - head_ry + 3, 6.5, 4.5), (hx + 2.5, hy - head_ry + 1.5, 6.5, 4.5), (hx - head_rx + 1.5, hy - 4, 3.5, 6.5)],
            "woman": [(hx - 4, hy - head_ry + 2.5, 7.5, 5), (hx + 4, hy - head_ry + 2, 6, 4.5), (hx - head_rx - 1, hy + 1, 4.5, 11), (hx - head_rx + 3, hy + 7, 4, 6)],
            "boy": [(hx - 3, hy - head_ry + 3, 7, 4.5), (hx + 3.5, hy - head_ry + 2.5, 6, 4.5)],
            "girl": [(hx - 3, hy - head_ry + 2.5, 7.5, 5), (hx + 3.5, hy - head_ry + 2, 6, 4.5), (hx - head_rx - 2, hy + 3, 4, 6), (hx + head_rx + 2, hy + 3, 4, 6)],
        }[kind]
        for i, (lx, ly, rx, ry) in enumerate(lobes):
            s.add(cut(oval(lx, ly, rx, ry, 10), seed + f"lobe{i}", 0.3), hair if not (kind == "boy") else gar["cap"])
        if kind == "boy":
            s.add(cut([(hx + 2, hy - head_ry + 3), (hx + head_rx + 5.5, hy - head_ry + 3.5), (hx + head_rx + 5.5, hy - head_ry + 6.5), (hx + 2, hy - head_ry + 6.5)], seed + "brim"), gar["cap"])
        if kind == "woman":
            s.add(cut([(hx - head_rx + 1, hy - head_ry + 4), (hx + head_rx - 2, hy - head_ry + 3), (hx + head_rx - 1.5, hy - head_ry + 6), (hx - head_rx + 1.5, hy - head_ry + 7)], seed + "band"), gar["belt"])
        if kind == "girl":
            for i, bx in enumerate((hx - head_rx - 2, hx + head_rx + 2)):
                s.add(cut(oval(bx, hy - 2.5, 2.4, 2.2, 8), seed + f"bow{i}", 0.2), gar["bow"], relief=False)
    else:
        if kind == "boy":
            pass  # hood drawn behind the head above
        else:
            s.add(cut(oval(hx, hy - head_ry + 1.5, head_rx + 1.5, 5.5, 12), seed + "hat", 0.3), gar["hat"])
            s.add(cut([(hx - head_rx - 2, hy - head_ry + 1.5), (hx + head_rx + 2, hy - head_ry + 1.5), (hx + head_rx + 2, hy - head_ry + 5), (hx - head_rx - 2, hy - head_ry + 5)], seed + "hatband"), gar["belt"] if kind != "girl" else CREAM)
            if kind == "girl":
                s.add(cut(oval(hx, hy - head_ry - 4, 3.2, 3.0, 10), seed + "pom", 0.25), CREAM)
    return s


def walker_bambola(kind: str, season: str, frame: int) -> Sprite:
    """Frontal paper doll: a big head with two eyes, a small body, arms down, legs that splay
    with the walk. Outlined once round the union of the whole figure."""
    s = Sprite(f"person_{kind}_{season}_walk{frame}", 41, 85)
    seed = s.name
    adult = ADULT[kind]
    top = ADULT_TOP if adult else CHILD_TOP
    gar = (SUMMER if season == "summer" else WINTER)[kind]
    skin, hair = SKIN[kind], HAIR[kind]
    cx = 20.5
    head_ry = 14.0 if adult else 11.5
    head_rx = 13.0 if adult else 11.0
    head_cy = top + head_ry + 1.5 + (4 if season == "winter" else 0)
    chin = head_cy + head_ry
    shoulder = chin + 0.5
    hip = 60 if adult else 65
    splay = (5.0, 0.0, -5.0)[frame]     # frame 0: left foot out; frame 2: right foot out
    bob = 1.0 if frame == 1 else 0.0
    ground_shadow(s, rx=13.5 if adult else 11.5)

    leg_w = 6.5 if adult else 5.5
    leg_col = gar["bottom"] or skin
    lx, rx_ = cx - 4.5, cx + 4.5
    s.add(cut(quad(lx, hip - 2, leg_w, lx - max(splay, 0) - 0.5, 80 - bob, leg_w), seed + "L1"), leg_col)
    s.add(cut(quad(rx_, hip - 2, leg_w, rx_ + max(-splay, 0) + 0.5, 80 - bob, leg_w), seed + "L2"), leg_col)
    for i, fx in enumerate((lx - max(splay, 0) - 0.5, rx_ + max(-splay, 0) + 0.5)):
        s.add(cut(chamfered(fx - 4.2, 79 - bob, fx + 4.2, 83.5 - bob, 1.2), seed + f"F{i}"), gar["shoe"])
    # body: small trapezoid, arms as stubs beside it
    hem = hip + (6 if season == "winter" else (3 if kind in ("woman", "girl") else 0))
    sw = 10.5 if adult else 9.0
    hw = (14.0 if season == "summer" and kind in ("woman", "girl") else 9.5) if adult else (11.5 if kind == "girl" else 8.0)
    body = [(cx - sw, shoulder), (cx + sw, shoulder), (cx + hw, hem), (cx - hw, hem)]
    s.add(cut(body, seed + "B"), gar["top"])
    arm_w = 5.0 if adult else 4.2
    arm_len = 16 if adult else 12
    for i, ax in enumerate((cx - sw - arm_w / 2 + 0.8, cx + sw + arm_w / 2 - 0.8)):
        s.add(cut(quad(ax, shoulder + 1, arm_w, ax + (-1.5 if i == 0 else 1.5), shoulder + 1 + arm_len, arm_w - 0.5), seed + f"A{i}"), gar["top"])
        s.add(cut(oval(ax + (-1.5 if i == 0 else 1.5), shoulder + 2 + arm_len, 2.9, 2.9, 10), seed + f"Hd{i}", 0.2), skin)
    if kind == "man" and season == "summer":
        s.add(cut([(cx - hw - 0.5, hip - 3.5), (cx + hw + 0.5, hip - 3.5), (cx + hw + 0.5, hip - 0.5), (cx - hw - 0.5, hip - 0.5)], seed + "belt"), gar["belt"])
    if season == "winter":
        s.add(cut([(cx - 9, chin - 2), (cx + 9, chin - 2), (cx + 9, chin + 3.5), (cx - 9, chin + 3.5)], seed + "scarf"), gar["belt"])
    # head and hair
    hx, hy = cx, head_cy
    if season == "winter" and kind == "woman":
        for i, bx in enumerate((hx - head_rx - 1, hx + head_rx + 1)):
            s.add(cut(oval(bx, hy + 4, 3.5, 8.5, 10), seed + f"hairw{i}", 0.3), hair)
    if season == "winter" and kind == "boy":
        s.add(cut(oval(hx, hy + 0.5, head_rx + 4.5, head_ry + 4, 16), seed + "hood", 0.3), gar["top"])
        s.add(cut(oval(hx, hy + 0.5, head_rx + 1.5, head_ry + 1.5, 16), seed + "hoodin", 0.25), CREAM, outline=False)
    s.add(cut(oval(cx, head_cy, head_rx, head_ry, 16), seed + "H", 0.3), skin)
    if season == "summer":
        if kind == "man":
            s.add(cut([(hx - head_rx - 0.5, hy - 3), (hx - head_rx + 1.5, hy - head_ry + 1.5), (hx - 4, hy - head_ry - 1), (hx + 5, hy - head_ry - 1), (hx + head_rx - 1.5, hy - head_ry + 1.5), (hx + head_rx + 0.5, hy - 3), (hx + head_rx - 2, hy - 2), (hx + 4, hy - head_ry + 4.5), (hx - 5, hy - head_ry + 4.5), (hx - head_rx + 2, hy - 2)], seed + "hair"), hair)
        elif kind == "woman":
            s.add(cut([(hx - head_rx - 3, hy + 8), (hx - head_rx - 2.5, hy - 3), (hx - head_rx + 1, hy - head_ry + 0.5), (hx - 3, hy - head_ry - 1.5), (hx + 4, hy - head_ry - 1.5), (hx + head_rx - 1, hy - head_ry + 0.5), (hx + head_rx + 2.5, hy - 3), (hx + head_rx + 3, hy + 8), (hx + head_rx - 1, hy + 8), (hx + head_rx - 1.5, hy - 1), (hx + 3, hy - head_ry + 4), (hx - 4, hy - head_ry + 4), (hx - head_rx + 1.5, hy - 1), (hx - head_rx + 1, hy + 8)], seed + "hair"), hair)
            s.add(cut([(hx - head_rx + 1, hy - head_ry + 1.5), (hx + head_rx - 1, hy - head_ry + 1.5), (hx + head_rx - 1.5, hy - head_ry + 4.5), (hx - head_rx + 1.5, hy - head_ry + 4.5)], seed + "band"), gar["belt"], outline=False)
        elif kind == "boy":
            s.add(cut([(hx - head_rx - 0.5, hy - 3), (hx - head_rx + 1.5, hy - head_ry + 1), (hx - 3, hy - head_ry - 1.5), (hx + 4, hy - head_ry - 1.5), (hx + head_rx - 1.5, hy - head_ry + 1), (hx + head_rx + 0.5, hy - 3), (hx + head_rx + 5, hy - 3), (hx + head_rx + 5, hy), (hx - head_rx - 0.5, hy)], seed + "cap"), gar["cap"])
        else:
            s.add(cut([(hx - head_rx - 0.5, hy - 2), (hx - head_rx + 1.5, hy - head_ry + 1), (hx - 3, hy - head_ry - 1.5), (hx + 4, hy - head_ry - 1.5), (hx + head_rx - 1.5, hy - head_ry + 1), (hx + head_rx + 0.5, hy - 2), (hx + head_rx - 2, hy - 1), (hx + 3, hy - head_ry + 4), (hx - 4, hy - head_ry + 4), (hx - head_rx + 2, hy - 1)], seed + "hair"), hair)
            for i, bx in enumerate((hx - head_rx - 2.5, hx + head_rx + 2.5)):
                s.add(cut(oval(bx, hy + 4, 3.6, 6.0, 10), seed + f"bun{i}", 0.3), hair)
                s.add(cut(oval(bx, hy - 2.5, 2.3, 2.1, 8), seed + f"bow{i}", 0.2), gar["bow"], outline=False)
    else:
        if kind == "boy":
            pass  # hood drawn behind the head above
        else:
            brim = hy - head_ry + 4.5
            s.add(cut([(hx - head_rx - 1.5, brim), (hx - head_rx + 0.5, hy - head_ry - 3.5), (hx, hy - head_ry - 5.5), (hx + head_rx - 0.5, hy - head_ry - 3.5), (hx + head_rx + 1.5, brim)], seed + "hat"), gar["hat"])
            s.add(cut([(hx - head_rx - 2, brim - 3.5), (hx + head_rx + 2, brim - 3.5), (hx + head_rx + 2, brim), (hx - head_rx - 2, brim)], seed + "hatband"), gar["belt"] if kind != "girl" else CREAM, outline=False)
            if kind == "girl":
                s.add(cut(oval(hx, hy - head_ry - 5.5, 3.4, 3.2, 10), seed + "pom", 0.25), CREAM)
    # the face: two eyes, 3.6 units across -- one screen pixel and a half on a walker
    ey = head_cy + (1.5 if adult else 1.0)
    for i, ex in enumerate((cx - 5.5, cx + 5.5)):
        s.add(cut(oval(ex, ey, 1.8, 1.8, 8), seed + f"eye{i}", 0.12), DARK, outline=False)
    return s


# ------------------------------------------------------------------------------ busts
def bust_window(style: str, kind: str, season: str) -> Sprite:
    """53x57 units, eye line centred on x=26.8 (WINDOW_HEAD_ANCHOR_X_UNITS), sill at the bottom.
    The head is ~37 units crown to chin, the premise WINDOW_HEAD_HEAD_UNITS encodes."""
    s = Sprite(f"person_{kind}_{season}_head_window", 53, 57)
    seed = s.name + style
    gar = (SUMMER if season == "summer" else WINTER)[kind]
    skin, hair = SKIN[kind], HAIR[kind]
    cx = 26.8
    adult = ADULT[kind]
    head_ry = 18.0 if adult else 16.5
    # 14.0 and 13.0 until v4.25's proportion pass: the window busts measured 0.83-1.18 of width
    # over height against the 1.05-1.47 of the family they replaced, narrow by a fifth, for the
    # same reason the seated ones were narrow by a half. Nothing constrains a head at a window --
    # there is no second occupant beside it -- so this is simply the proportion the drawing wants.
    head_rx = 18.0 if adult else 17.0
    head_cy = 3 + head_ry + (3 if season == "winter" else 0)
    chin = head_cy + head_ry
    # shoulders on the sill
    s.add(cut([(cx - 22, 57), (cx - 21, chin + 3), (cx - 12, chin - 2), (cx + 12, chin - 2), (cx + 21, chin + 3), (cx + 22, 57)], seed + "sh"), gar["top"])
    if season == "winter":
        s.add(cut([(cx - 12, chin - 3), (cx + 12, chin - 3), (cx + 12, chin + 4), (cx - 12, chin + 4)], seed + "scarf"), gar["belt"])
    frontal = style == "bambola"
    hx = cx if frontal else cx + 0.8
    s.add(cut([(cx - 4, chin - 4), (cx + 4, chin - 4), (cx + 4, chin + 2), (cx - 4, chin + 2)], seed + "N"), skin, relief=False)
    hy = head_cy
    behind_head(s, style, kind, season, gar, hair, hx, hy, head_rx, head_ry, seed, frontal)
    s.add(cut(oval(hx, head_cy, head_rx, head_ry, 16), seed + "H", 0.35), skin)
    s.head = (hx, head_cy, head_rx, head_ry)
    add_hair(s, style, kind, season, gar, hair, hx, hy, head_rx, head_ry, seed, frontal)
    if frontal:
        for i, ex in enumerate((cx - 6, cx + 6)):
            s.add(cut(oval(ex, hy + 2, 2.3, 2.3, 8), seed + f"eye{i}", 0.12), DARK, outline=False)
    return s


def bust_car(style: str, kind: str, season: str, rise: float = 0.0) -> Sprite:
    """The seated bust: eyes centred on HEAD_CAR_ANCHOR_X_UNITS, the topmost ink on the declared
    crown and the chin on the declared chin, so a child's head really is 90% of an adult's.

    **[rise] is what makes that declaration true.** Headwear is drawn relative to the head oval
    and every piece stands proud of it by its own amount -- a cushion of hair lobes by about two
    units, a hat by rather less. Left to itself the topmost ink lands wherever that puts it: on
    the first v4.25 build the four adults' crowns measured 0.00, 2.67, 0.00 and 2.67 against a
    declared 2.0, and the crown-to-chin block they define spread over four units where the test
    that reads it allows one and a half. Nothing in the concept asks for the man's hair to sit
    two units above his own hat; the generator was simply not drawing what it declared.

    So the head is pushed down by exactly the amount its own headwear rises and shortened by the
    same amount, leaving the chin where it was. [build] measures the rise on a first pass and
    hands it back. One pass is enough: every piece is placed at an offset from the oval's top,
    so the rise does not depend on the oval's height.
    """
    s = Sprite(f"person_{kind}_{season}_head_car", 47, 44)
    seed = s.name + style
    gar = (SUMMER if season == "summer" else WINTER)[kind]
    skin, hair = SKIN[kind], HAIR[kind]
    cx = 23.0
    adult = ADULT[kind]
    crown = 2.0 if adult else 5.5
    chin = 37.0
    # No hat reservation. It used to push the head down six units in winter so a hat could sit
    # above it; `rise` now does that job from the headwear each member actually wears, which is
    # also why the boy -- whose winter headwear is a hood drawn behind the head, not a hat on
    # top of it -- no longer ends up four and a half units short of every other child.
    head_top = crown + rise
    head_ry = (chin - head_top) / 2
    # **A head is as round here as it is anywhere else the same person is drawn.** These were 9.0
    # and 8.5, against 14.0 and 13.0 for the same head at a window, and the seated family shipped
    # at 0.61-0.72 of width over height where every other placement of the same people sits near
    # 1.0. The reason was [SEATED_HALF_BAND] being read in the wrong unit; with the band right,
    # the oval is drawn at the proportion the artwork has everywhere else and the band is no
    # longer the thing that shapes it.
    head_rx = 13.8 if adult else 12.8
    head_cy = head_top + head_ry
    # shoulders and (Rilievo, Bambola) the seatbelt that says "in a car"
    s.add(cut([(cx - 17, 43.7), (cx - 16, chin + 2), (cx - 9, chin - 2), (cx + 9, chin - 2), (cx + 16, chin + 2), (cx + 17, 43.7)], seed + "sh"), gar["top"])
    if drawing_style(style) != "stampino":
        s.add(cut([(cx - 17, 43.7), (cx - 13, 43.7), (cx + 9, chin - 1.5), (cx + 6, chin - 2.5)], seed + "belt"), "#3A3F4A", relief=False, outline=False)
    if season == "winter":
        s.add(cut([(cx - 9, chin - 2.5), (cx + 9, chin - 2.5), (cx + 9, chin + 3), (cx - 9, chin + 3)], seed + "scarf"), gar["belt"])
    frontal = style == "bambola"
    hx = cx if frontal else cx + 0.5
    s.add(cut([(cx - 3.5, chin - 3), (cx + 3.5, chin - 3), (cx + 3.5, chin + 1), (cx - 3.5, chin + 1)], seed + "N"), skin, relief=False)
    behind_head(s, style, kind, season, gar, hair, hx, head_cy, head_rx, head_ry, seed, frontal, narrow=True)
    s.add(cut(oval(hx, head_cy, head_rx, head_ry, 14), seed + "H", 0.3), skin)
    s.head = (hx, head_cy, head_rx, head_ry)
    add_hair(s, style, kind, season, gar, hair, hx, head_cy, head_rx, head_ry, seed, frontal, narrow=True)
    if frontal:
        for i, ex in enumerate((cx - 4.2, cx + 4.2)):
            s.add(cut(oval(ex, head_cy + 1.5, 1.9, 1.9, 8), seed + f"eye{i}", 0.12), DARK, outline=False)
    return s


#: How far the under-paper sits below and to the right of the piece it shadows, in local units.
RELIEF_OFFSET = (1.4, 1.9)

#: The largest displacement `cut` writes into a point. Its own default, and the deepest any call
#: site asks for.
CUT_MAX_WOBBLE = 0.35

#: A bust unit, in the car's own units: `SceneObjectRenderer.CAR_OCCUPANT_SCALE`.
#:
#: The bust is drawn into a car, and the two are authored on different grids. Everything below
#: that compares a bust dimension against a car dimension goes through this number.
CAR_UNITS_PER_BUST_UNIT = 0.5255

#: The seat pitch, in the car's units: `SceneObjectRenderer` seats the driver at -7.75 and the
#: passenger at 13.75 of the narrowest cabin. It was 23 until v4.25 re-derived it -- see
#: `SceneObjectRenderer.CAR_HEAD_X_UNITS`. No piece of this artwork is clamped by the band at
#: either value, so the shipped sprites are byte-identical across the change; the constant is
#: updated because the derivation below reads it, not because a pixel moves.
SEAT_PITCH_CAR_UNITS = 21.5

#: What v4.24 left between two occupants' ink at that pitch, in car units, and the reason the
#: number is two rather than nothing: two heads that merge draw one silhouette with four eyes.
#: v4.24's own worst case is a child beside an adult, which cleared 2.05.
SEATED_CLEARANCE_CAR_UNITS = 2.0

#: Half the band a seated head may occupy, in the bust's own units.
#:
#: **This was 11.0, and it was the wrong unit.** The band is in *bust* units and the seat pitch it
#: is justified against is in *car* units, and a bust unit is [CAR_UNITS_PER_BUST_UNIT] of a car
#: unit -- so a 22-unit band is 11.6 car units against a pitch of 23, and the constraint was
#: twice as tight as the car actually is. The head was then drawn to fit it: `head_rx` came out
#: at 9.0 where the window bust of the same person is 14.0, and the seated head shipped 20.7
#: units wide against the 37.0 of the family it replaced, at the same height. A head squeezed to
#: fit a window is exactly the per-asset correction `AI_PROJECT_RULES.md` forbids; the cause was
#: on the car's side of the comparison and this is where it is fixed.
SEATED_HALF_BAND = (SEAT_PITCH_CAR_UNITS - SEATED_CLEARANCE_CAR_UNITS) / CAR_UNITS_PER_BUST_UNIT / 2


def in_band(hx, x, r, narrow):
    """A side piece's centre, pulled in so the piece's own edge lands inside [SEATED_HALF_BAND].

    Moves the piece rather than shrinking it: a bunch drawn at three units and slid one unit
    inboard is still a bunch, where the same bunch cut down to two units is a different drawing.
    A piece already inside the band is returned untouched, so this narrows the seven that overflow
    and leaves everything else exactly where the concept put it.
    """
    if not narrow:
        return x
    # **The under-paper counts.** Rilievo draws every piece a second time, offset down and to the
    # right by [RELIEF_OFFSET], so a piece whose own outline stops exactly on the band still puts
    # ink past it. Forgetting that left the family a unit and a half over the seat pitch after the
    # band rule was already in place -- the rule was right and the edge it measured was the wrong
    # one.
    # The wobble counts too, for the same reason: `cut` writes a displacement of up to
    # [CUT_MAX_WOBBLE] into every point, so a polygon placed exactly on the band draws a third of a
    # unit past it on whichever points happened to wobble outward.
    left = SEATED_HALF_BAND - r - CUT_MAX_WOBBLE
    right = SEATED_HALF_BAND - r - RELIEF_OFFSET[0] - CUT_MAX_WOBBLE
    return hx + max(-left, min(right, x - hx))


def add_hair(s, style, kind, season, gar, hair, hx, hy, rx, ry, seed, frontal, narrow=False):
    """Hair or headwear for the busts. Frontal (Bambola) or three-quarter (the other two);
    Rilievo builds it from lobes, the others from one cut piece. `narrow` keeps every piece
    inside 22 units of width for the car family."""
    side = 0 if frontal else 1   # three-quarter: hair mass toward -x
    reach = 1.5 if narrow else 3.0
    # **The hair follows the head.** The crown lobes were written as absolute offsets from the
    # eye axis, tuned against the head each bust had when the concept was drawn -- 9.0 units of
    # radius seated, 14.0 at a window. A head drawn wider than that keeps the same small cushion
    # of hair on top, which reads as a wig two sizes down rather than as a wider head. Every
    # horizontal extent below is therefore a multiple of the head's own radius. The vertical ones
    # are not: the head is no taller than it was.
    k = rx / (9.0 if narrow else 14.0)
    if season == "summer":
        if drawing_style(style) == "rilievo":
            lobes = {
                "man": [(hx - 5 * k, hy - ry + 3, 6.5 * k, 4.5), (hx + 2.5 * k, hy - ry + 1.5, 6.5 * k, 4.5), (hx - rx + 1.5 * k, hy - 5, 3.5 * k, 6.5)],
                # The inner lock is dropped on the seated bust (`narrow`): there it falls across
                # the cheek and ends level with the jaw, which narrows the face exactly where the
                # crown-to-chin measurement reads its width -- her block measured four units short
                # of the other three adults' for that reason alone. The outer lock keeps the
                # silhouette; nothing else about her hair changes.
                "woman": [(hx - 4 * k, hy - ry + 2.5, 7.5 * k, 5), (hx + 4 * k, hy - ry + 2, 6 * k, 4.5), (in_band(hx, hx - rx - 0.5, 4.0 * k, narrow), hy + 2, 4.0 * k, 12)]
                + ([] if narrow else [(hx - rx + 3 * k, hy + 9, 4 * k, 6)])
                + ([(hx + rx - 1, hy + 4, 3.5 * k, 9)] if frontal else []),
                "boy": [(hx - 3 * k, hy - ry + 3, 7 * k, 4.5), (hx + 3.5 * k, hy - ry + 2.5, 6 * k, 4.5)],
                # Her crown lobes follow the head; her bunches do not. A bunch hangs *beside* a
                # head rather than covering it, so scaling it with the skull both makes it a
                # different drawing and pushes it into the seat band -- which then pulls it back
                # over her own cheek, which is where it was ending up.
                "girl": [(hx - 3 * k, hy - ry + 2.5, 7.5 * k, 5), (hx + 3.5 * k, hy - ry + 2, 6 * k, 4.5), (in_band(hx, hx - rx - reach + 1, 3.2 if narrow else 4, narrow), hy + 4, 3.2 if narrow else 4, 7), (in_band(hx, hx + rx + reach - 1, 3.2 if narrow else 4, narrow), hy + 4, 3.2 if narrow else 4, 7)],
            }[kind]
            # Every lobe through the band, not only the ones that sit at the side: the woman's
            # crown lobe is seven and a half units across and centred four off the axis, so it
            # reached further out than either of her side pieces did. Clamping the list rather
            # than the pieces that happened to overflow is what stops this being whack-a-mole.
            for i, (lx, ly, lrx, lry) in enumerate(lobes):
                s.add(cut(oval(in_band(hx, lx, lrx, narrow), ly, lrx, lry, 10), seed + f"lobe{i}"),
                      hair if kind != "boy" else gar["cap"])
            if kind == "boy":
                s.add(cut([(hx - (rx if frontal else -2), hy - ry + 3.5), (in_band(hx, hx + rx + reach + 1, 0.0, narrow), hy - ry + 3.5), (in_band(hx, hx + rx + reach + 1, 0.0, narrow), hy - ry + 6.5), (hx - (rx if frontal else -2), hy - ry + 6.5)], seed + "brim"), gar["cap"])
            if kind == "woman":
                s.add(cut([(hx - rx + 1, hy - ry + 4), (hx + rx - 2, hy - ry + 3), (hx + rx - 1.5, hy - ry + 6.5), (hx - rx + 1.5, hy - ry + 7.5)], seed + "band"), gar["belt"])
            if kind == "girl":
                for i, bx in enumerate((in_band(hx, hx - rx - reach + 1, 2.4, narrow), in_band(hx, hx + rx + reach - 1, 2.4, narrow))):
                    s.add(cut(oval(bx, hy - 2, 2.4, 2.2, 8), seed + f"bow{i}", 0.2), gar["bow"], relief=False)
            return
        if kind == "man":
            pts = [(hx - rx - 0.5, hy - 3), (hx - rx + 1.5, hy - ry + 1.5), (hx - 4, hy - ry - 1), (hx + 5, hy - ry - 1), (hx + rx - 1, hy - ry + 2), (hx + rx + 0.5 * (1 - side), hy - 3 - 3 * side), (hx + 4, hy - ry + 4.5), (hx - 5, hy - ry + 4.5), (hx - rx + 2 + side, hy - 2 + 2 * side)]
            s.add(cut(pts, seed + "hair"), hair)
        elif kind == "woman":
            r = reach
            pts = [(hx - rx - r, hy + 9), (hx - rx - r + 0.5, hy - 3), (hx - rx + 1, hy - ry + 0.5), (hx - 3, hy - ry - 1.5), (hx + 4, hy - ry - 1.5), (hx + rx - 1, hy - ry + 0.5)]
            if frontal:
                pts += [(hx + rx + r - 0.5, hy - 3), (hx + rx + r, hy + 9), (hx + rx - 1, hy + 9), (hx + rx - 1.5, hy - 1), (hx + 3, hy - ry + 4), (hx - 4, hy - ry + 4), (hx - rx + 1.5, hy - 1), (hx - rx + 1, hy + 9)]
            else:
                pts += [(hx + 4, hy - ry + 4), (hx - 4, hy - ry + 5), (hx - rx + 3, hy + 1), (hx - rx + 3, hy + 9)]
            s.add(cut(pts, seed + "hair"), hair)
            s.add(cut([(hx - rx + 1, hy - ry + 1.5), (hx + rx - 1, hy - ry + 1.5), (hx + rx - 1.5, hy - ry + 4.5), (hx - rx + 1.5, hy - ry + 4.5)], seed + "band"), gar["belt"], outline=False)
        elif kind == "boy":
            pts = [(hx - rx - 0.5, hy - 3), (hx - rx + 1.5, hy - ry + 1), (hx - 3, hy - ry - 1.5), (hx + 4, hy - ry - 1.5), (hx + rx - 1.5, hy - ry + 1), (hx + rx + 0.5, hy - 3), (in_band(hx, hx + rx + reach + 2.5, 0.0, narrow), hy - 3), (in_band(hx, hx + rx + reach + 2.5, 0.0, narrow), hy), (hx - rx - 0.5 + 6 * side, hy)]
            s.add(cut(pts, seed + "cap"), gar["cap"])
        else:
            pts = [(hx - rx - 0.5, hy - 2), (hx - rx + 1.5, hy - ry + 1), (hx - 3, hy - ry - 1.5), (hx + 4, hy - ry - 1.5), (hx + rx - 1.5, hy - ry + 1), (hx + rx + 0.5 * (1 - side), hy - 2 - 2 * side), (hx + 3, hy - ry + 4), (hx - 4, hy - ry + 4), (hx - rx + 2, hy - 1)]
            s.add(cut(pts, seed + "hair"), hair)
            for i, bx in enumerate((in_band(hx, hx - rx - reach + 1, 3.2 if narrow else 3.8, narrow), in_band(hx, hx + rx + reach - 1, 3.2 if narrow else 3.8, narrow))):
                s.add(cut(oval(bx, hy + 4, 3.2 if narrow else 3.8, 7, 10), seed + f"bun{i}", 0.3), hair)
                s.add(cut(oval(bx, hy - 2.5, 2.3, 2.1, 8), seed + f"bow{i}", 0.2), gar["bow"], outline=False)
    else:
        if kind == "boy":
            pass  # the hood is drawn by behind_head
        else:
            brim = hy - ry + 4.5
            s.add(cut([(in_band(hx, hx - rx - 1.5, 0.0, narrow), brim), (hx - rx + 0.5, hy - ry - 3.5), (hx, hy - ry - 5.5), (hx + rx - 0.5, hy - ry - 3.5), (in_band(hx, hx + rx + 1.5, 0.0, narrow), brim)], seed + "hat"), gar["hat"])
            s.add(cut([(in_band(hx, hx - rx - 2, 0.0, narrow), brim - 3.5), (in_band(hx, hx + rx + 2, 0.0, narrow), brim - 3.5), (in_band(hx, hx + rx + 2, 0.0, narrow), brim), (in_band(hx, hx - rx - 2, 0.0, narrow), brim)], seed + "hatband"), gar["belt"] if kind != "girl" else CREAM, outline=False)
            if kind == "girl":
                s.add(cut(oval(hx, hy - ry - 5.5, 3.0, 2.8, 10), seed + "pom", 0.25), CREAM)


def behind_head(s, style, kind, season, gar, hair, hx, hy, rx, ry, seed, frontal, narrow=False):
    """What sits behind the head: the winter woman's hair and the winter boy's hood, which
    opens toward the face on the three-quarter figures and is a thin ring on the frontal one."""
    if season != "winter":
        return
    reach = 1.5 if narrow else 3.0
    if kind == "woman":
        sides = ((hx - rx - reach + 1.5, hx + rx + reach - 1.5) if frontal else (in_band(hx, hx - rx + 0.5, 3.2 if narrow else 4.0, narrow),))
        for i, bx in enumerate(sides):
            s.add(cut(oval(bx, hy + 5, 3.2 if narrow else 4.0, 8.5, 10), seed + f"hairw{i}", 0.3), hair)
    if kind == "boy":
        back = 0 if frontal else -2.5
        grow = 3.0 if narrow else 4.5
        s.add(cut(oval(hx + back, hy + 0.5, min(rx + grow, SEATED_HALF_BAND - abs(back)) if narrow else rx + grow, ry + grow - 0.5, 16), seed + "hood", 0.3), gar["top"])
        s.add(cut(oval(hx + back / 2, hy + 0.5, rx + 1.4, ry + 1.4, 16), seed + "hoodin", 0.25), CREAM, relief=False, outline=False)


# ------------------------------------------------------------------------------ driver
WALKERS = {"stampino": walker_stampino, "rilievo": walker_rilievo, "bambola": walker_bambola}
BLURB = {
    "stampino": "Concept A 'Stampino' -- the least paper that still reads as a person: head, one body block, two legs, feet, hair. No outline, no face, no hands.",
    "rilievo": "Concept B 'Rilievo' -- stacked papers with an under-paper shadow beneath every piece (the Quercia larga's recipe), hair as a cushion of lobes, arms as separate strips. No face.",
    "bambola": "Concept C 'Bambola' -- a frontal paper doll: a big head, two eyes, one outer outline round the union of the figure. The only concept with a face and an outline.",
    "rilievo_occhi": "Concept B Rilievo with a hint of a face: identical in every other respect, plus two dark dots on the three-quarter head, sized from that head. Built so the face question can be judged on the device at the size the figures are actually seen at.",
}


#: The declared crown of a seated bust: where the topmost ink must land, in the bust's own units.
SEATED_CROWN = {True: 2.0, False: 5.5}


def seated_rise(shapes: str, kind: str, season: str) -> float:
    """How far this member's headwear stands above the head oval it is drawn on.

    Measured, on this member, by rendering it once: the concept's pieces are cut polygons with a
    deterministic wobble written into their coordinates, so "how high does the hair go" has no
    closed form worth trusting and one render answers it exactly. The value feeds straight back
    into [bust_car], which lowers the head by it -- so this is a measurement the generator takes
    of itself, not a table of per-character offsets somebody has to keep in step with the artwork.
    """
    # **Probed with headroom, because a probe at rise zero measures the canvas, not the hair.** A
    # winter hat is drawn five and a half units above the head oval; with the head at the declared
    # crown that puts its peak off the top of the canvas, the render clips it, and the measurement
    # comes back as "the ink starts at row zero" -- which is true of the picture and says nothing
    # about the hat. Probing with the head pushed well down leaves every piece inside the frame,
    # and the padding subtracts out exactly.
    pad = 12.0
    probe = bust_car(shapes, kind, season, rise=pad)
    rendered = raster.render_svg(emit_svg(probe, shapes))
    top = measure_raster(probe.name, rendered).content_bbox[1] / UNIT
    return pad + SEATED_CROWN[ADULT[kind]] - top


def family(style: str):
    shapes = drawing_style(style)
    for kind in KINDS:
        for season in ("summer", "winter"):
            for frame in range(3):
                yield faced(WALKERS[shapes](kind, season, frame), style)
            yield faced(bust_window(shapes, kind, season), style)
            yield faced(bust_car(shapes, kind, season, seated_rise(shapes, kind, season)), style)


def faced(sprite: Sprite, style: str) -> Sprite:
    if has_eyes(style):
        add_eye_hint(sprite)
    return sprite


def trim_to_content(sprites: list, style: str) -> None:
    """Shrink each shared canvas onto what the family drawn on it actually covers.

    Every sprite that shares a canvas is trimmed by the **same** box -- the union of the family's
    content -- so the co-registration that lets one blit origin serve all of them survives the
    trim. Losing that is the whole reason the shipped `head_car` family declares its margin
    load-bearing in `SpriteCanvasConventionTest`.

    Done here, at authoring time, rather than by cropping the rendered PNGs afterwards. A crop
    afterwards leaves the SVG describing a canvas the PNG no longer has, so `render` stops
    reproducing what ships -- and `tools/assets`' own `normalize --apply` cannot fix that for this
    family, because it resolves a source by sprite name and six of these are drawn from a file
    named after the base they replaced.

    The trim is quantised to whole local units, which is what keeps every sprite on the authoring
    grid: a crop of a fraction of a unit would put the artwork half a source pixel off it.
    """
    groups: dict[tuple[int, int], list] = {}
    for sprite in sprites:
        groups.setdefault((sprite.width_units, sprite.height_units), []).append(sprite)
    for (w_units, h_units), members in groups.items():
        left = top = math.inf
        right = bottom = -math.inf
        for sprite in members:
            r = raster.render_svg(emit_svg(sprite, style))
            box = measure_raster(sprite.name, r).content_bbox
            left = min(left, box[0] / UNIT); top = min(top, box[1] / UNIT)
            right = max(right, box[2] / UNIT); bottom = max(bottom, box[3] / UNIT)
        dx, dy = math.floor(left), math.floor(top)
        new_w = min(w_units, math.ceil(right)) - dx
        new_h = min(h_units, math.ceil(bottom)) - dy
        if (dx, dy, new_w, new_h) == (0, 0, w_units, h_units):
            continue
        print(f"  canvas {w_units}x{h_units} -> {new_w}x{new_h} units, origin +({dx},{dy})")
        for sprite in members:
            sprite.width_units, sprite.height_units = new_w, new_h
            for part in sprite.parts:
                part.points = [(x - dx, y - dy) for x, y in part.points]
            if sprite.head is not None:
                hx, hy, rx, ry = sprite.head
                sprite.head = (hx - dx, hy - dy, rx, ry)


def build(style: str) -> None:
    out = HERE / style / "svg"
    out.mkdir(parents=True, exist_ok=True)
    entries = []
    sprites = list(family(style))
    trim_to_content(sprites, style)
    for sprite in sprites:
        svg = emit_svg(sprite, style)
        (out / f"{sprite.name}.svg").write_text(svg + "\n", encoding="utf-8")
        r = raster.render_svg(svg)
        expected = (sprite.width_units * UNIT, sprite.height_units * UNIT)
        if r.size != expected:
            raise SystemExit(f"{sprite.name}: rendered {r.size}, expected {expected}")
        (out / f"{sprite.name}.png").write_bytes(r.png_bytes)
        m = measure_raster(sprite.name, r)
        box = list(m.content_bbox)
        anchor = [round((box[0] + box[2]) / 2 / UNIT, 2), round(box[3] / UNIT, 2)] if False else None
        walk = "_walk" in sprite.name
        entries.append({
            "name": sprite.name, "category": "person",
            "width": expected[0], "height": expected[1], "contentBox": box,
            "scale": "SCENE_UNITS", "tint": "FIXED_ART", "usage": "concept",
            "anchorRule": "CONTENT_BOTTOM_CENTRE",
            "anchor": [round((box[0] + box[2]) / 2, 1), box[3]],
            "season": "summer" if "_summer_" in sprite.name else "winter",
            "source": {"kind": "svg", "file": f"{sprite.name}.svg"},
            "notes": BLURB[style] + (" Walk frame; co-registered on the shared 41x85-unit canvas, feet on the canvas bottom." if walk else
                                    (" Window bust on the shared 53x57-unit canvas; unreachable in winter today (seasonIndexFor(INDOORS) always reads the summer column)." if "window" in sprite.name and "_winter_" in sprite.name else "")),
        })
    reg = {"schemaVersion": 2,
           "note": f"Registry of the v4.25 people concept '{style}': the shipped registry's own schema, kept here because these sprites are NOT shipped. Canvases, scale convention, anchor rule and tint class are the shipped ones, so no call site changes.",
           "sprites": entries}
    (HERE / style / "sprites.concept.json").write_text(json.dumps(reg, indent=1) + "\n", encoding="utf-8")
    print(style, len(entries), "sprites ->", out)


if __name__ == "__main__":
    styles = sys.argv[1:] or list(WALKERS) + list(EYED)
    for style in styles:
        build(style)
