#!/usr/bin/env python3
"""The Christmas fir (v5.14): drawn and declared, from one geometry.

The maintainer, 2026-10-14: *«alberi natalizi - voglio rifare il modello da 0»*; of three firs drawn from
nothing and photographed on the BV6600 he chose *«A – a palchi (consigliato)»* the same day. This is A: four
tiers whose hems are cut in a zigzag, each over a darker under-paper, a five-pointed star on the top, ten lights
on the teeth of the hems, the snow of the winter palette on what is seen of each tier, three presents at the foot.

It writes, from one run, the five things that have to agree (and, with ``--res``, the three PNGs rendered from the SVGs):

* ``sources/svg/tree_fir.svg`` -- the fir: the trunk, the under-papers and the tiers, fixed art, one flat colour a
  card, cut with the scissors' small wobble (``DESIGN_NOTES.md`` §2, "Cut edges, not struck ones");
* ``sources/svg/tree_fir_snow.svg`` -- the snow it wears under the winter palette, each tier's cap cut to what is
  seen of that tier (an SVG mask: the tier less the tiers above it, their under-papers and the star), so it rests
  on the shoulders the next tier leaves uncovered;
* ``sources/svg/tree_fir_star.svg`` -- the star, a piece of its own: the fir is drawn under the trees' night shade
  (decision 36) and the star, like the snow and the lights, is not;
* ``app/.../engine/FirTable.kt`` -- where each of the three blits (its origin, in units from the foot of the trunk),
  where the lights hang, where the presents stand and where the gallery card puts its three baubles;
* the fir of the Christmas launcher icon, ``res/drawable/ic_launcher_christmas_foreground.xml``, between the two
  comments that mark it there: the same cards scaled into the box the icon's fir has always had (the star's top at
  51.9 dp, the trunk's foot at 66.2 dp, centred at 78.5), each tier's snow cut to it by a clip-path and covered by
  the tiers drawn after it, as in the sprite; three baubles where the card hangs them; the paper shadow every object
  of the icon carries, its silhouette 1.5 dp right and 2 dp down at a quarter of black; and **the star as large as
  the icon's star had been** -- 3.8 dp across, its top at 51.9 dp -- where the fir's own, scaled with it, would be
  1.7 dp across: the maintainer's *«si a tutte»* (2026-10-15) to the row that proposed it.

Each SVG is trimmed to its content on the 3x grid (``DESIGN_NOTES.md`` §4, rule 3), so each carries an origin of
its own, derived here with the others. With ``--res`` the three are rendered with the pipeline's own rasteriser
(``paperscrape_assets.raster``, the PNG ``render`` gives) into ``app/src/main/res/drawable-nodpi/``; with
``--check`` nothing is written and the run fails unless every committed output is what this draws
(``tests/test_fir.py`` makes the same comparison).

**Every part at least 3 units across, and every band in a slope 4.5.** A fir stands where a tree does, 0.18 to 1.0
deep: the farthest is drawn at 0.674 px a unit on the BV6600 (TREE's 9.479 m over 118 units, x45 px/m, x0.6 for
a 1 440 px screen, x0.338 of perspective at 0.7195 of the screen, x0.92 at the smallest size jitter), so 2 px are
2.97 units there; a band of 3 units cut in a slope loses some of it across, so the under-paper and the snow's cool
rim show 4.5 below their card. ``tests/test_fir.py`` erodes every colour of the render to hold it.

Usage, from ``tools/assets`` with the venv's interpreter:

    python fir/build_fir.py [--res] [--check]
"""
from __future__ import annotations

import hashlib
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image
import io

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent
sys.path.insert(0, str(ASSETS))
from paperscrape_assets.raster import render_svg  # noqa: E402

SVG_DIR = ASSETS / "sources" / "svg"
REPO = ASSETS.parent.parent
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
TABLE = REPO / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / "engine" / "FirTable.kt"
ICON = REPO / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher_christmas_foreground.xml"

PX = 3  # SpriteBlitter.SPRITE_PIXELS_PER_UNIT

#: The fir's frame, in units: 72 wide, 126 tall, the trunk's foot at the middle of the bottom edge.
W, H = 72, 126
CX = 36.0

TRUNK = "#533217"
STAR = "#F2C24A"
SNOW = "#FFFFFF"
SNOW_RIM = "#C9D6E0"
UNDER = "#1F4A2A"
#: How far the under-paper and the snow's cool rim show below their card: 4.5 units, so that on the teeth's slopes
#: too the band is 3 units across or more (2 px at the farthest tree, where 2.97 units are 2 px).
UNDER_DROP = 4.5
RIM_DROP = 4.5

#: The tiers, bottom first: apex, hem, half width, teeth, tooth depth, colour.
TIERS = (
    (52, 111, 35.0, 5, 6.5, "#2F6B3D"),
    (33, 88, 28.5, 4, 6.0, "#357545"),
    (17, 64, 21.5, 3, 5.5, "#3C7F4B"),
    (5, 40, 14.5, 2, 5.0, "#438952"),
)
#: The star's centre, and its outer and inner radii.
STAR_CENTRE = (CX, 7.5)
STAR_RADII = (7.8, 3.4)
#: How far above the tip of a tooth a light hangs, in units.
LIGHT_RISE = 3.5
#: Where the three presents stand at the foot, from the trunk, in units (`SceneObjectRenderer.drawFir`).
GIFTS = (-17.0, -1.0, 14.0)
#: The centres of the gallery card's three baubles (tinted `star_sparkle`s), from the trunk's foot, top first: on
#: the hems of the three upper tiers, as the card of the fir A was photographed and chosen (2026-10-14).
#: `tests/test_fir.py` holds each on the fir's paper.
BAUBLES = ((0.0, -84.0), (14.0, -58.0), (-12.0, -32.0))


# ------------------------------------------------------------------ geometry
def wobble(seed: str, i: int, amp: float):
    d = hashlib.sha256(f"{seed}:{i}".encode()).digest()
    return (d[0] / 255.0 - 0.5) * 2 * amp, (d[1] / 255.0 - 0.5) * 2 * amp


def cut(points, seed, amp=0.35):
    """The scissors' wobble: every vertex nudged by a hash of its index, never a random draw."""
    return [(x + wobble(seed, i, amp)[0], y + wobble(seed, i, amp)[1]) for i, (x, y) in enumerate(points)]


def path(points) -> str:
    return "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in points) + " Z"


def star(cx, cy, r_out, r_in, n=5, rot=-90.0):
    pts = []
    for k in range(2 * n):
        r = r_out if k % 2 == 0 else r_in
        a = math.radians(rot + k * 180.0 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def zigzag_tier(cx, apex_y, hem_y, half, teeth, depth):
    """A tier with a hem cut in a zigzag: blunt apex, right shoulder, the teeth right to left, left shoulder.
    Returns (polygon, tips) -- the tips are where a tooth points down."""
    pts = [(cx - 1.6, apex_y + 1.2), (cx + 1.6, apex_y + 1.2)]
    x0, x1 = cx + half, cx - half
    step = (x0 - x1) / teeth
    tips = []
    for k in range(teeth + 1):
        x = x0 - k * step
        pts.append((x, hem_y))
        tips.append((x, hem_y))
        if k < teeth:
            pts.append((x - step / 2, hem_y - depth))
    return pts, tips


def trunk():
    return cut([(CX - 3.4, 126), (CX - 2.8, 108), (CX + 2.8, 108), (CX + 3.4, 126)], f"trunk{CX}", 0.25)


def design():
    """The tiers (each a dict of its cards, colour, snow line and teeth) and the lights."""
    tiers, lights = [], []
    for i, (apex, hem, half, teeth, depth, colour) in enumerate(TIERS):
        poly, tips = zigzag_tier(CX, apex, hem, half, teeth, depth)
        nxt = TIERS[i + 1] if i + 1 < len(TIERS) else None
        top_seen = (nxt[1] + 3.0) if nxt else apex
        tiers.append(dict(shapes=[cut(poly, f"a-tier{i}")], colour=colour,
                          snow_line=top_seen + (7.5 if nxt else 13.0), snow_teeth=teeth + 1, half=half))
        for k, (tx, ty) in enumerate(tips):
            if 0 < k < teeth:
                lights.append((tx, ty - LIGHT_RISE))
    return tiers, lights


def star_points():
    cx, cy = STAR_CENTRE
    return cut(star(cx, cy, *STAR_RADII), "a-star", 0.2)


# ------------------------------------------------------------------ SVG
def shapes_d(shapes) -> str:
    return " ".join(path(s) for s in shapes)


def masks(tiers) -> str:
    """For each tier, the mask of what is seen of it: the tier less every tier above it, their under-papers and
    the star."""
    defs = []
    star_d = path(star_points())
    for i, t in enumerate(tiers):
        above = [s for u in tiers[i + 1:] for s in u["shapes"]]
        above_under = [[(x, y + UNDER_DROP) for x, y in s] for u in tiers[i + 1:] for s in u["shapes"]]
        hide = shapes_d(above + above_under)
        defs.append(f'<mask id="seen{i}" maskUnits="userSpaceOnUse" x="-10" y="-10" width="{W + 20}" height="{H + 20}">'
                    f'<path d="{shapes_d(t["shapes"])}" fill="#ffffff"/>' + (f'<path d="{hide}" fill="#000000"/>' if hide else "")
                    + f'<path d="{star_d}" fill="#000000"/></mask>')
    return "<defs>" + "".join(defs) + "</defs>"


def header(width, height, box) -> str:
    x, y, w, h = box
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w * PX}" height="{h * PX}" '
            f'viewBox="{x} {y} {w} {h}">')


def fir_body(tiers) -> list[str]:
    out = [f'  <path d="{path(trunk())}" fill="{TRUNK}"/>']
    for t in tiers:
        out.append(f'  <path d="{shapes_d([[(x, y + UNDER_DROP) for x, y in s] for s in t["shapes"]])}" fill="{UNDER}"/>')
        out.append(f'  <path d="{shapes_d(t["shapes"])}" fill="{t["colour"]}"/>')
    return out


def snow_caps(tiers):
    """Per tier, the cap: everything above a scalloped line at the tier's snow line."""
    caps = []
    for i, t in enumerate(tiers):
        y, n, half = t["snow_line"], t["snow_teeth"], t["half"] + 6
        cap = [(CX - half, -5), (CX + half, -5)]
        for k in range(n * 2 + 1):
            x = CX + half - k * (2 * half) / (n * 2)
            cap.append((x, y + (3.5 if k % 2 else 0.0)))
        caps.append((i, cut(cap, f"a-snow{i}", 0.25)))
    return caps


def snow_body(tiers) -> list[str]:
    out = ["  " + masks(tiers)]
    for i, cap in snow_caps(tiers):
        out.append(f'  <path d="{path([(px, py + RIM_DROP) for px, py in cap])}" fill="{SNOW_RIM}" mask="url(#seen{i})"/>')
        out.append(f'  <path d="{path(cap)}" fill="{SNOW}" mask="url(#seen{i})"/>')
    return out


def star_body() -> list[str]:
    return [f'  <path d="{path(star_points())}" fill="{STAR}"/>']


COMMENTS = {
    "tree_fir": "the fir: the trunk, an under-paper 4.5 units under each tier, the four tiers with their hems cut in a "
                "zigzag. Fixed art, drawn under the trees' night shade (decision 36)",
    "tree_fir_snow": "the snow of the winter palette, each tier's cap cut to what is seen of that tier (a mask: the "
                     "tier less the tiers above, their under-papers and the star), with its cool rim 4.5 units under it",
    "tree_fir_star": "the star on the fir's top, a piece of its own so it stays bright at night as the lights do",
}


def svg(name, body, box) -> str:
    x, y, w, h = box
    return "\n".join([
        header(W, H, box),
        f"  <!-- The Christmas fir (v5.14, the maintainer's «A – a palchi (consigliato)», 2026-10-14): {COMMENTS[name]}. "
        f"Generated by tools/assets/fir/build_fir.py: edit that, not this. viewBox {x} {y} {w} {h} in the fir's {W}x{H}-unit "
        f"frame, so it blits at ({x - W // 2},{y - H}) from the trunk's foot (FirTable). -->",
        *body,
        "</svg>",
    ]) + "\n"


def content_box(name, body) -> tuple[int, int, int, int]:
    """The content of `body` drawn over a margin round the fir's frame, in whole units: (x, y, w, h)."""
    m = 4
    probe = "\n".join([f'<svg xmlns="http://www.w3.org/2000/svg" width="{(W + 2 * m) * PX}" height="{(H + 2 * m) * PX}" '
                       f'viewBox="{-m} {-m} {W + 2 * m} {H + 2 * m}">', *body, "</svg>"])
    a = np.array(Image.open(io.BytesIO(render_svg(probe).png_bytes)).convert("RGBA"))[:, :, 3]
    ys, xs = np.where(a > 0)
    x0, y0 = xs.min() // PX - m, ys.min() // PX - m
    x1, y1 = -(-(xs.max() + 1) // PX) - m, -(-(ys.max() + 1) // PX) - m
    return int(x0), int(y0), int(x1 - x0), int(y1 - y0)


def build():
    """Everything this writes, as {path: bytes}, and the table's numbers."""
    tiers, lights = design()
    bodies = {"tree_fir": fir_body(tiers), "tree_fir_snow": snow_body(tiers), "tree_fir_star": star_body()}
    out, origins = {}, {}
    for name, body in bodies.items():
        box = content_box(name, body)
        text = svg(name, body, box)
        rendered = render_svg(text)
        assert rendered.size == (box[2] * PX, box[3] * PX), (name, rendered.size, box)
        out[SVG_DIR / f"{name}.svg"] = text.encode()
        out[RES / f"{name}.png"] = rendered.png_bytes
        origins[name] = (box[0] - W // 2, box[1] - H)
    lights_u = [(round(x - CX, 2), round(y - H, 2)) for x, y in lights]
    out[TABLE] = kotlin_table(origins, lights_u, BAUBLES).encode()
    out[ICON] = icon_xml(ICON.read_text()).encode()
    return out


# ------------------------------------------------------------------ the launcher icon's fir
#: The box the icon's fir stands in, in dp of the 108-dp vector: the top of the fir's frame, the trunk's foot, the
#: middle; and the star, as large as the icon's has been since v5.4: 3.8 dp across, its top on the frame's top.
ICON_TOP, ICON_FOOT, ICON_CX = 51.9, 66.2, 78.5
ICON_STAR_WIDTH = 3.8
#: The paper shadow under every object of the icon: its silhouette moved by this much, at this alpha.
ICON_SHADOW = (1.5, 2.0)
ICON_SHADOW_ALPHA = "0.25"
#: The icon's baubles: a gold and two red, the radius the icon's baubles have always had.
ICON_BAUBLES = ("#FFF2C230", "#FFC1443B", "#FFC1443B")
ICON_BAUBLE_RADIUS = 0.75
ICON_BEGIN = "<!-- The fir: generated by tools/assets/fir/build_fir.py from the drawing of tree_fir; edit that, not this. -->"
ICON_END = "<!-- The fir ends here. -->"


def signed_area(pts) -> float:
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1])) / 2


def icon_d(pts) -> str:
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"


def icon_block() -> str:
    """The fir of the Christmas launcher icon, as VectorDrawable paths: see the module's doc comment."""
    k = (ICON_FOOT - ICON_TOP) / H
    to_dp = lambda pts: [(ICON_CX + (x - CX) * k, ICON_TOP + y * k) for x, y in pts]
    tiers, _ = design()
    cx, cy = STAR_CENTRE
    s = ICON_STAR_WIDTH / (2 * STAR_RADII[0] * math.sin(math.radians(72)))
    star_pts = [(ICON_CX + (x - cx) * s, ICON_TOP + STAR_RADII[0] * s + (y - cy) * s) for x, y in star_points()]
    trunk_pts = to_dp(trunk())
    unders = [to_dp([(x, y + UNDER_DROP) for x, y in t["shapes"][0]]) for t in tiers]
    bodies = [to_dp(t["shapes"][0]) for t in tiers]
    caps = dict(snow_caps(tiers))
    # The shadow: every card of the silhouette, wound the same way, so the one path fills their union once.
    silhouette = [trunk_pts] + unders + bodies + [star_pts]
    sx, sy = ICON_SHADOW
    shadow = []
    for pts in silhouette:
        moved = [(x + sx, y + sy) for x, y in pts]
        shadow.append(moved if signed_area(moved) > 0 else moved[::-1])
    path = lambda colour, d, extra="": (f'    <path android:fillColor="{colour}"{extra} android:fillType="evenOdd"'
                                        f'    android:pathData="{d}" />')
    out = [f"    {ICON_BEGIN}",
           f'    <path android:fillColor="#FF000000" android:fillAlpha="{ICON_SHADOW_ALPHA}" android:fillType="nonZero"'
           f'    android:pathData="{" ".join(icon_d(p) for p in shadow)}" />',
           path("#FF" + TRUNK[1:], icon_d(trunk_pts))]
    for i, t in enumerate(tiers):
        out.append(path("#FF" + UNDER[1:], icon_d(unders[i])))
        out.append(path("#FF" + t["colour"][1:], icon_d(bodies[i])))
        cap = to_dp(caps[i])
        out.append(f'    <group>\n        <clip-path android:pathData="{icon_d(bodies[i])}" />')
        out.append("    " + path("#FF" + SNOW_RIM[1:], icon_d([(x, y + RIM_DROP * k) for x, y in cap])))
        out.append("    " + path("#FF" + SNOW[1:], icon_d(cap)))
        out.append("    </group>")
    out.append(path("#FF" + STAR[1:], icon_d(star_pts)))
    for (bx, by), colour in zip(BAUBLES, ICON_BAUBLES):
        x, y = ICON_CX + bx * k, ICON_FOOT + by * k
        ring = [(x + ICON_BAUBLE_RADIUS * math.cos(2 * math.pi * j / 14), y + ICON_BAUBLE_RADIUS * math.sin(2 * math.pi * j / 14))
                for j in range(14)]
        out.append(path(colour, icon_d(ring)))
    out.append(f"    {ICON_END}")
    return "\n".join(out)


def icon_xml(text: str) -> str:
    """The icon's XML with its fir between the two markers replaced by [icon_block]."""
    start, end = text.index("    " + ICON_BEGIN), text.index(ICON_END) + len(ICON_END)
    return text[:start] + icon_block() + text[end:]


def fa(values) -> str:
    return "floatArrayOf(" + ", ".join(f"{v}f" for v in values) + ")"


def kotlin_table(origins, lights, baubles) -> str:
    (fx, fy), (sx, sy), (tx, ty) = origins["tree_fir"], origins["tree_fir_snow"], origins["tree_fir_star"]
    return f'''package com.paperscrape.livewallpaper.engine

/**
 * The Christmas fir (v5.14), in units from the foot of its trunk: where its three pieces blit, where its lights
 * hang and its presents stand, and where the gallery card hangs its baubles. **Generated** by
 * `tools/assets/fir/build_fir.py` from the same geometry it draws the SVGs with; edit that script, not this file.
 * The fir stands {W} units wide and {H} tall, star included.
 */
internal object FirTable {{
    /** The fir's width and height, star included, in units. */
    const val WIDTH = {W}f
    const val HEIGHT = {H}f

    /** Where `tree_fir` blits: the trunk, the under-papers and the tiers. */
    const val FIR_X = {fx}f
    const val FIR_Y = {fy}f

    /** Where `tree_fir_snow` blits, trimmed to its content. */
    const val SNOW_X = {sx}f
    const val SNOW_Y = {sy}f

    /** Where `tree_fir_star` blits, trimmed to its content. */
    const val STAR_X = {tx}f
    const val STAR_Y = {ty}f

    /** The centres of the ten lights, {LIGHT_RISE} units above the tips of the hems' inner teeth. */
    val LIGHT_X = {fa([x for x, _ in lights])}
    val LIGHT_Y = {fa([y for _, y in lights])}

    /** Where the three presents stand at the foot. */
    val GIFT_X = {fa(GIFTS)}

    /** The centres of the gallery card's three baubles, over the hems of the upper three tiers, top first. */
    val BAUBLE_X = {fa([x for x, _ in baubles])}
    val BAUBLE_Y = {fa([y for _, y in baubles])}
}}
'''


def main(argv) -> int:
    res, check = "--res" in argv, "--check" in argv
    out = build()
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
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
