#!/usr/bin/env python3
"""The Halloween decorations of the houses and the shops: a cobweb in a corner of the roof, and a jack-o'-lantern by the
door.

The set A1 (`DESIGN_NOTES.md` §12, decisions 83 and 85): no bat, **one small cobweb** in a corner of every roof, in a
colour the building's walls choose, and a jack-o'-lantern about 1.2 m tall by the door. The model is the Christmas
decorations (decision 67): every place one hangs from is one the buildings' generator already writes -- the roofline
(`BuildingPiece.roofX`), the front door (`BuildingDoor`) -- so none is a number typed in the renderer (`HalloweenDecorations` lays them out, `SceneObjectRenderer.drawHalloweenDecorations` draws them). The pieces,
`SCENE_UNITS` (3 px a unit, drawn on the building's own piece units), each trimmed to its content on the 3x grid:

* ``deco_cobweb`` -- a spider's web in the left corner of a roof: its corner at (0, 0), three threads running along the
  eave, down the wall and between them, and one row of scallops sagging toward the corner between them; the renderer
  mirrors it at the right. **A greyscale mask** in white, which the renderer tints: light on a dark wall, dark on a light
  one (`HalloweenDecorations.webTintOn`, decision 85), the one turned into the other over the dusk and the dawn
  (`HalloweenDecorations.webTintAt`);
* ``deco_jack_o_lantern`` -- a small carved pumpkin standing on (0, 0), its bottom's middle, by a front door: by day its
  cuts are the dark of a carved pumpkin; fixed art;
* ``deco_jack_o_lantern_light`` -- the candle's light in its cuts, the same cuts in a warm yellow, laid over them at night;
  fixed art.

**Every band 2 px or more on the BV6600 where a house or a shop stands farthest.** A piece unit of a building is
`metres x 27 / the family's units` px at the reference line of the BV6600 (2.306 for both houses and for the restaurant,
the bar and the shop's places), times the perspective where it stands and its size roll: the farthest house of the
twelve built-in streets stands 0.28 deep, at the smallest roll (0.92) 0.812 px a unit ([PX_PER_UNIT_FARTHEST]), and the
farthest place among the houses 0.356 deep, 0.884 px a unit (never under 0.833, `SceneObjectRenderer.placeScaleFloor`);
the decorations hang on no tower (`HalloweenDecorations.decorates`), so a tower's half as large again
(`ChristmasDecorations.scaleFor`) does not enter. Every band is drawn [MIN_BAND] units or more: 2 px there and a
twentieth more. So the cobweb's threads are [MIN_BAND] wide, the whole band, and the jack-o'-lantern's grin starts a
band below its eyes: 0.2 units nearer, the paper between them would be 2.39 units, 1.94 px. The cobweb reaches 6.2 bands
from its corner with one row of scallops: two rows over that reach would leave holes too small to read at the size it
is drawn.

It writes, from one run (and, with ``--res``, the three PNGs rendered from the SVGs):

* ``sources/svg/deco_cobweb.svg``, ``deco_jack_o_lantern.svg`` and ``deco_jack_o_lantern_light.svg``;
* ``app/.../engine/HalloweenDecorationTable.kt`` -- where each blits from the point it hangs from or stands on, and the
  narrowest band.

``--check`` writes nothing and fails, naming every stale file, unless every file this writes is what it draws
(``tests/test_halloween_decorations.py`` makes the same comparison); ``--two-px`` lists every piece of one colour of each
piece -- the cobweb tinted light and dark, the jack-o'-lantern by day and with its light at night --, rendered without
antialiasing, that holds no square of 2 px anywhere at the farthest house.

Usage, from ``tools/assets`` with the venv's interpreter:

    python halloween/build_halloween_deco.py [--res] [--check] [--two-px]
"""
from __future__ import annotations

import io
import math
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent
sys.path.insert(0, str(ASSETS))
from papercut import Sheet, content_box, cut, ellipse, polyline_band, render_body, svg_document  # noqa: E402
from paperscrape_assets.raster import render_svg, same_output  # noqa: E402

REPO = ASSETS.parent.parent
SVG_DIR = ASSETS / "sources" / "svg"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
TABLE = REPO / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / "engine" / "HalloweenDecorationTable.kt"
PX = 3  # SpriteBlitter.SPRITE_PIXELS_PER_UNIT

# ------------------------------------------------------------------ the size, and what it asks of the drawing
#: A piece unit of a house on the BV6600 at the reference line: `HOUSE_SMALL`'s 5.76 m over its family's 67.434 units (the
#: large house's 7.6 over 88.976, the restaurant's place's 4.783 over 56, are the same 2.306) at 27 px a metre (45 x 1 440 /
#: 2 400).
PX_PER_UNIT_AT_REFERENCE = 5.76 * 27.0 / 67.434
#: The perspective of the farthest house of the built-in streets, 0.28 deep (`SceneObjectCatalog`'s houses' far band,
#: 0.28 to 0.48): (0.704 + 0.28 x 0.086 - 0.655) / 0.191 (`SceneSpace.groundYFraction` and `perspectiveScaleAt`).
FARTHEST_PERSPECTIVE = (0.704 + 0.28 * 0.086 - 0.655) / 0.191
#: A piece unit there at the smallest size roll (0.92): 0.812 px.
PX_PER_UNIT_FARTHEST = PX_PER_UNIT_AT_REFERENCE * FARTHEST_PERSPECTIVE * 0.92
#: The narrowest band of the pieces, in units: 2 px at the farthest house and a twentieth more.
MIN_BAND = round(2.0 / PX_PER_UNIT_FARTHEST * 1.05, 2)

PUMPKIN = "#EE8A2C"
STEM = "#4E7A3A"
#: A carved cut by day, and the candle's light in it at night.
CUT = "#3A1E0E"
LIGHT = "#FFC94A"
#: The cobweb's paper: a mask, white, which the renderer tints -- [WEB_LIGHT] on a dark wall, [WEB_DARK] on a light one
#: (`HalloweenDecorations.WEB_LIGHT`, `WEB_DARK`; `tests/test_halloween_decorations.py` holds the two sides together).
WEB = "#FFFFFF"
#: Its colour on a dark wall (`DESIGN_NOTES.md` §12, decision 85).
WEB_LIGHT = "#E8E6EE"
#: Its colour on a light wall (decision 85): the dark violet `#56516A` multiplied over [WEB_LIGHT], channel by channel.
WEB_DARK = "#4E4963"


def jack_o_lantern():
    """The pumpkin standing on (0, 0) -- its middle at x 0, its bottom at y 0 -- and its light. Each cut and the paper
    between two of them [MIN_BAND] or more: the eyes cut square-ish rather than as thin triangles."""
    s, light = Sheet(), Sheet()
    M = MIN_BAND
    eye, gap, mouth, margin = M * 1.12, M * 1.0, M * 1.1, M * 0.6
    ry = (eye + gap + mouth) / 2 + margin
    rx = ry * 1.25
    cy = -ry
    body = cut(ellipse(0, cy, rx, ry, 20), "hwp", 0.08)
    s.add(body, PUMPKIN)
    s.add(cut([(-M * 0.55, cy - ry + 0.6), (M * 0.6, cy - ry + 0.4), (M * 0.7, cy - ry - M * 1.0), (-M * 0.45, cy - ry - M * 1.0)], "hwps", 0.05), STEM)
    top = cy - (eye + gap + mouth) / 2
    ex0, ex1 = M * 0.5, M * 0.5 + M * 1.15
    cuts = [[(-ex1, top + eye), (-ex0, top + eye), (-ex0, top + 0.3), (-ex1, top)],
            [(ex0, top + eye), (ex1, top + eye), (ex1, top), (ex0, top + 0.3)],
            [(-M * 1.7, top + eye + gap), (M * 1.7, top + eye + gap), (M * 1.2, top + eye + gap + mouth), (-M * 1.2, top + eye + gap + mouth)]]
    s.clip("hwpc", [body])
    light.clip("hwpc", [body])
    for i, c in enumerate(cuts):
        c = cut(c, f"hwpf{i}", 0.04)
        s.add(c, CUT, clip="hwpc")
        light.add(c, LIGHT, clip="hwpc")
    return s, light


#: The cobweb: its reach from the corner and how wide its threads are, in units.
WEB_REACH = MIN_BAND * 6.2
WEB_THREAD = MIN_BAND
#: The row of scallops: at this share of the reach from the corner, each of its two runs between two threads sagging
#: toward the corner by up to this share at its middle, cut at five points.
SCALLOP_REACH = 0.9
SCALLOP_SAG = 0.24
SCALLOP_POINTS = 5


def scallop_points(seg: int) -> list[tuple[float, float]]:
    """The middle line of the run of scallops between the thread at [seg] x 45 degrees and the next."""
    rr = WEB_REACH * SCALLOP_REACH
    pts = []
    for k in range(SCALLOP_POINTS):
        a = math.radians(seg * 45.0 + 45.0 * k / (SCALLOP_POINTS - 1))
        sag = 1.0 - SCALLOP_SAG * math.sin(math.pi * k / (SCALLOP_POINTS - 1))
        pts.append((rr * sag * math.cos(a), rr * sag * math.sin(a)))
    return pts


def cobweb():
    """A spider's web in the left corner of a roof: its corner at (0, 0), three threads running right (along the eave),
    down (along the wall) and between, and one row of scallops sagging toward the corner between them, every thread
    [WEB_THREAD] wide; one paper, white: a mask the renderer tints."""
    s = Sheet()
    for i, a in enumerate((0.0, 45.0, 90.0)):
        r = math.radians(a)
        s.add(cut(polyline_band([(0.0, 0.0), (WEB_REACH * math.cos(r), WEB_REACH * math.sin(r))], WEB_THREAD), f"hwwr{i}", 0.04), WEB)
    for seg in range(2):
        s.add(cut(polyline_band(scallop_points(seg), WEB_THREAD), f"hwws{seg}", 0.04), WEB)
    return s


NAMES = ["deco_cobweb", "deco_jack_o_lantern", "deco_jack_o_lantern_light"]

NOTES = {
    "deco_cobweb": "a spider's web in the left corner of a roof, its corner at (0,0); the renderer mirrors it at the right",
    "deco_jack_o_lantern": "a small carved pumpkin standing on (0,0), its bottom's middle, by a front door; its cuts dark by day",
    "deco_jack_o_lantern_light": "the candle's light in the small carved pumpkin's cuts, laid over them at night, in its frame",
}


def comment(name: str) -> str:
    """The source's header: what the piece is, and that this script generates it; the cobweb's says it is a white mask
    the renderer tints, the jack-o'-lantern's and its light's that they are fixed art."""
    if name == "deco_cobweb":
        return (f"The Halloween decorations (DESIGN_NOTES.md §12, decision 85: one cobweb a roof, no bat): {NOTES[name]}. "
                "A white mask the renderer tints, light on a dark wall and dark on a light one. Generated by "
                "tools/assets/halloween/build_halloween_deco.py: edit that, not this.")
    return (f"The Halloween decorations (DESIGN_NOTES.md §12, decision 83): {NOTES[name]}. Fixed "
            "art. Generated by tools/assets/halloween/build_halloween_deco.py: edit that, not this.")


def build():
    """Everything this writes, as {path: bytes}, and the table's numbers."""
    pumpkin, light = jack_o_lantern()
    out, t = {}, {}
    for name, sheet, frame in (("deco_cobweb", cobweb(), (-10, -10, 40, 40)), ("deco_jack_o_lantern", pumpkin, (-20, -30, 40, 40)),
                               ("deco_jack_o_lantern_light", light, (-20, -30, 40, 40))):
        body = sheet.body()
        box = content_box(body, frame, PX, margin=2)
        fx, fy, fw, fh = frame
        assert fx - 2 < box[0] and fy - 2 < box[1] and box[0] + box[2] < fx + fw + 2 and box[1] + box[3] < fy + fh + 2, (name, box)
        out[SVG_DIR / f"{name}.svg"] = svg_document(body, box, PX, comment(name)).encode()
        out[RES / f"{name}.png"] = render_body(body, box, PX)
        t[name] = dict(x=box[0], y=box[1], w=box[2], h=box[3])
    out[TABLE] = kotlin_table(t).encode()
    return out, t


def f(x: float) -> str:
    return f"{x:.2f}f"


def kotlin_table(t) -> str:
    web, pumpkin, light = (t[n] for n in NAMES)
    return f'''package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * The Halloween decorations of the houses and the shops (one cobweb a roof, no bat), in the building's piece
 * units: where each piece blits from the point it hangs from or stands on ([HalloweenDecorations] says where those are).
 * **Generated** by
 * `tools/assets/halloween/build_halloween_deco.py` from the geometry it draws the SVGs with; edit that script, not this
 * file.
 */
internal object HalloweenDecorationTable {{
    /**
     * The cobweb in the left corner of a roof, from the corner it hangs in (mirrored in the right corner): a white mask,
     * tinted as the walls choose ([HalloweenDecorations.webTintAt]; at rest, [HalloweenDecorations.webTintOn]).
     */
    val WEB_RES = R.drawable.deco_cobweb
    const val WEB_X = {f(web["x"])}
    const val WEB_Y = {f(web["y"])}

    /** The jack-o'-lantern by a front door, from its bottom's middle, where it stands. */
    val PUMPKIN_RES = R.drawable.deco_jack_o_lantern
    const val PUMPKIN_X = {f(pumpkin["x"])}
    const val PUMPKIN_Y = {f(pumpkin["y"])}

    /** The candle's light in its cuts, laid over it at night, from the same point. */
    val PUMPKIN_LIGHT_RES = R.drawable.deco_jack_o_lantern_light
    const val PUMPKIN_LIGHT_X = {f(light["x"])}
    const val PUMPKIN_LIGHT_Y = {f(light["y"])}

    /** The narrowest designed band, in units; the card sizes its copy on it. */
    const val DECORATION_BAND_UNITS = {f(MIN_BAND)}
}}
'''


# ------------------------------------------------------------------ the 2 px rule at the farthest house
def crisp(svg: str) -> Image.Image:
    """An SVG rendered without antialiasing: every pixel one paper's, so a band's width is its own."""
    return Image.open(io.BytesIO(render_svg(svg.replace("<svg ", '<svg shape-rendering="crispEdges" ', 1)).png_bytes)).convert("RGBA")


def tinted(image: Image.Image, colour: str) -> Image.Image:
    """[image] multiplied by [colour], channel by channel, as the renderer's tint draws a mask."""
    a = np.array(image).astype(np.int64)
    rgb = np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)])
    a[:, :, :3] = (a[:, :, :3] * rgb + 127) // 255
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def renders(out, t) -> dict[str, Image.Image]:
    """Each piece rendered without antialiasing: the cobweb tinted light and dark, as the walls choose; the jack-o'-lantern
    by day and at night, its light laid over it."""
    images = {n: crisp(out[SVG_DIR / f"{n}.svg"].decode()) for n in NAMES}
    p, q = t["deco_jack_o_lantern"], t["deco_jack_o_lantern_light"]
    night = images["deco_jack_o_lantern"].copy()
    night.alpha_composite(images["deco_jack_o_lantern_light"], (int(round((q["x"] - p["x"]) * PX)), int(round((q["y"] - p["y"]) * PX))))
    return {"deco_cobweb light": tinted(images["deco_cobweb"], WEB_LIGHT), "deco_cobweb dark": tinted(images["deco_cobweb"], WEB_DARK),
            "deco_jack_o_lantern by day": images["deco_jack_o_lantern"], "deco_jack_o_lantern at night": night}


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


def two_px_report(out, t) -> list[str]:
    """Every piece of one colour of each decoration that holds no square of 2 px anywhere at the farthest house."""
    side = 2.0 / PX_PER_UNIT_FARTHEST * PX
    k = math.ceil(side)
    lines, bad = [], 0
    for label, image in renders(out, t).items():
        a = np.array(image).astype(np.int64)
        opaque = a[:, :, 3] == 255
        keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
        for key in np.unique(keys[opaque]):
            mask = opaque & (keys == key)
            ok = squares(mask, k)
            for pts in pieces(mask):
                if len(pts) >= 12 and not any(ok[p] for p in pts):
                    bad += 1
                    lines.append(f"{label}: #{int(key):06X} {len(pts)} px under 2 px")
    lines.append(f"{bad} pieces under 2 px at the farthest house, {PX_PER_UNIT_FARTHEST:.3f} px a unit (a square of {k} px of the 3x "
                 f"drawing, 2 px are {side:.2f}); the narrowest band {MIN_BAND} units, {MIN_BAND * PX_PER_UNIT_FARTHEST:.2f} px")
    return lines


def main(argv) -> int:
    res, check = "--res" in argv, "--check" in argv
    out, t = build()
    if "--two-px" in argv:
        for line in two_px_report(out, t):
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
    for n, b in t.items():
        print(f"{n}: {b['w'] * PX} x {b['h'] * PX} px at ({b['x']}, {b['y']}) units, {b['w'] * PX * b['h'] * PX * 4} B decoded")
    print(f"at the farthest house {PX_PER_UNIT_FARTHEST:.3f} px a unit; the narrowest band {MIN_BAND} units, "
          f"{MIN_BAND * PX_PER_UNIT_FARTHEST:.2f} px")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
