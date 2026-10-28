#!/usr/bin/env python3
"""The Halloween scarecrow: a scarecrow on its pole with a carved pumpkin for a head, drawn from one geometry.

The scarecrows stand in the snowmen's places in the Halloween theme (`DESIGN_NOTES.md` §12, decision 80). This is its
drawing, *the Halloween scarecrow*: a black floppy pointed hat with an orange band, an old green coat with a brown and an
orange patch, brown trousers, straw out of the cuffs and the hem, on its pole, the carved pumpkin's grin for a face. By
day the face's cuts are the dark of a carved pumpkin, as the pumpkins' (`pumpkin_face`); at night the renderer lays a
light over them, the candle inside.

The two pieces, fixed art and `SCENE_UNITS` (3 px a unit), each trimmed to its content on the 3x grid:

* ``scarecrow`` -- the scarecrow on its pole, the foot of the pole at (0, 0): its canvas is centred on the pole and ends
  at the ground (everything is cut flat at y 0, and the two straw cuffs, its widest, are each other's mirror), so the
  foot is the bottom centre of what is drawn -- `CONTENT_BOTTOM_CENTRE`, as the snowman's --;
* ``scarecrow_light`` -- the candle's light in the face's cuts: the same cuts in a warm yellow, laid over them.

**2.4 m from the pole's foot to the hat's tip** ([METRES_TALL]; the snowman beside it is 1.68), and **every band 2 px
or more on the BV6600 where it stands farthest**: a scarecrow stands where a snowman does -- the nearest half of the
object band, 0.45 to 1.0 deep (`SceneObjectCatalog.seasonalDecorationCandidates`) -- so the farthest is drawn at the
perspective of 0.45 deep and the smallest size roll (0.92), [FARTHEST_PX_TALL] px tall, [px_per_unit_farthest] px a
unit; every part is drawn in multiples of [M_UNITS] -- [M_UNITS] x that, about 2.2 px, is its narrowest band. The pumpkin
head is large for it: each eye and the grin, and the head's paper between any two of them, hold [M_UNITS] or nearly
(the paper between an eye and the grin [M_UNITS] less 0.4 at the grin's corners). The pumpkin's paper between the eyes
and the hat's brim is a strip of a quarter of [M_UNITS], as Santa's skin between his eyes and his hat's band: no rule
holds it (decision 73).

Two things of the frame put the foot exactly at the bottom centre of what is drawn and centre the canvas on the pole.
Everything is cut flat at the ground (y 0), so neither the pole's foot nor the shoes' soles wobble below it; and the
left cuff is the right one's mirror, so the two cuffs, the drawing's widest, reach equally far either side.

It writes, from one run (and, with ``--res``, the two PNGs rendered from the SVGs):

* ``sources/svg/scarecrow.svg`` and ``scarecrow_light.svg``;
* ``app/.../engine/ScarecrowTable.kt`` -- where the two blit from the pole's foot, how tall the drawing is in units
  (the pole's foot to the hat's tip), the metres that is, and the narrowest band.

``--check`` writes nothing and fails, naming every stale file, unless every file this writes is what it draws
(``tests/test_scarecrow.py`` makes the same comparison); ``--two-px`` lists every piece of one colour of the scarecrow, by
day and with its light laid over it at night, rendered without antialiasing, that holds no square of 2 px at the
farthest place anywhere.

Usage, from ``tools/assets`` with the venv's interpreter:

    python scarecrow/build_scarecrow.py [--res] [--check] [--two-px]
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
from papercut import Sheet, content_box, cut, darker, ellipse, mirror_x, render_body, svg_document  # noqa: E402
from paperscrape_assets.raster import render_svg, same_output  # noqa: E402

REPO = ASSETS.parent.parent
SVG_DIR = ASSETS / "sources" / "svg"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
TABLE = REPO / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / "engine" / "ScarecrowTable.kt"
PX = 3  # SpriteBlitter.SPRITE_PIXELS_PER_UNIT

# ------------------------------------------------------------------ the size, and what it asks of the drawing
#: How tall a scarecrow reads, the foot of its pole to the tip of its hat: a person's 1.75 m on a pole, and a hat -- the
#: snowman's 1.68 m beside it (`DESIGN_NOTES.md` §12, decision 80).
METRES_TALL = 2.4

#: The narrowest band of the drawing, in units: every part is drawn in multiples of it.
M_UNITS = 4.5

#: The farthest a snowman's place stands -- 0.45 deep (`SceneObjectCatalog.seasonalDecorationCandidates`) --, at the
#: smallest size roll (`1 - SIZE_VARIATION_SPREAD / 2`, 0.92), on the BV6600: 1 440 px tall, so 27 px a metre at the
#: reference line (45 x 1 440 / 2 400), and the perspective of 0.45 deep, (0.704 + 0.45 x 0.086 - 0.655) / 0.191 (the
#: object band from 0.704 to 0.790 of the screen, `SceneSpace.groundYFraction`, over the horizon at 0.655 and the
#: reference line at 0.846).
FARTHEST_PERSPECTIVE = (0.704 + 0.45 * 0.086 - 0.655) / 0.191
FARTHEST_PX_TALL = METRES_TALL * 27.0 * FARTHEST_PERSPECTIVE * 0.92


def px_per_unit_farthest(units_tall: float) -> float:
    """Pixels a unit of the drawing where a scarecrow stands farthest on the BV6600, for a drawing [units_tall] tall."""
    return FARTHEST_PX_TALL / units_tall


STRAW = "#E9C25A"
STRAW_DARK = "#C99A36"
POLE = "#7A5532"
#: A carved cut by day: the pumpkins' own (`pumpkin_face`).
CUT = "#2B1A10"
#: The candle's light at night.
LIGHT = "#FFC94A"
HAT = "#2E2834"
HAT_BAND = "#E07A2E"
COAT = "#3F5C3A"
PATCH = "#8A5A34"
PATCH2 = "#D9772B"
TROUSERS = "#6B4A30"
SHOE = "#3A2A20"
PUMPKIN = "#E8862A"


def hbar(x0, x1, y0, y1, notched_right, seed):
    """A tuft of straw as a bar from x0 to x1 between y0 and y1, its free end cut in two points."""
    h = y1 - y0
    if notched_right:
        pts = [(x0, y0), (x1, y0 - 0.6), (x1 + 2.2, y0 + h * 0.2), (x1, y0 + h * 0.5), (x1 + 2.4, y1 - h * 0.15), (x1, y1 + 0.6), (x0, y1)]
    else:
        pts = [(x1, y0), (x0, y0 - 0.6), (x0 - 2.2, y0 + h * 0.2), (x0, y0 + h * 0.5), (x0 - 2.4, y1 - h * 0.15), (x0, y1 + 0.6), (x1, y1)]
    return cut(pts, seed, 0.08)


def vbar(x0, x1, y0, y1, seed):
    """A tuft of straw hanging from y0 down to y1 between x0 and x1, its free end cut in two points."""
    w = x1 - x0
    return cut([(x0, y0), (x1, y0), (x1 + 0.6, y1), (x1 - w * 0.2, y1 + 2.4), (x0 + w * 0.5, y1), (x0 + w * 0.15, y1 + 2.2), (x0 - 0.6, y1)], seed, 0.08)


def pumpkin_head(s: Sheet, light: Sheet, cx, cy):
    """The pumpkin head: the fruit, and the face's cuts -- dark on [s], the same cuts in the candle's light on [light]. Each
    cut, and the paper between two of them, [M_UNITS] or nearly: the head is large for it, the eyes cut square-ish rather
    than as thin triangles, and the grin carries one broad tooth. Returns the head's top."""
    M = M_UNITS
    eye, gap, mouth, margin = M * 1.25, M * 1.04, M * 1.1, M * 0.75
    ry = (eye + gap + mouth) / 2 + margin
    rx = ry * 1.12
    body = cut(ellipse(cx, cy, rx, ry, 26), "headb", 0.15)
    s.add(body, PUMPKIN)
    top_ = cy - (eye + gap + mouth) / 2
    mid_ = top_ + eye
    bot_ = mid_ + gap
    mb = bot_ + mouth
    ex0, ex1 = M * 0.55, M * 0.55 + M * 1.45          # the eyes across, from the middle
    mw = M * 2.1                                     # the grin's half width at its top
    t = M * 0.5                                      # the tooth's half width
    cuts = [[(cx - ex1, top_), (cx - ex0, top_ + 1.0), (cx - ex0, mid_), (cx - ex1 + 0.6, mid_)],
            [(cx + ex0, top_ + 1.0), (cx + ex1, top_), (cx + ex1 - 0.6, mid_), (cx + ex0, mid_)],
            [(cx - mw, bot_ - 0.4), (cx - t, bot_), (cx - t, bot_ + mouth * 0.42), (cx + t, bot_ + mouth * 0.42), (cx + t, bot_),
             (cx + mw, bot_ - 0.4), (cx + mw * 0.78, mb), (cx - mw * 0.78, mb)]]
    s.clip("headclip", [body])
    light.clip("headclip", [body])
    for i, c in enumerate(cuts):
        c = cut(c, f"headf{i}", 0.06)
        s.add(c, CUT, clip="headclip")
        light.add(c, LIGHT, clip="headclip")
    return cy - ry


def hat(s: Sheet, cx, top_of_head):
    """The floppy pointed hat on the top of the head, its brim down over the pumpkin's forehead, and its band."""
    M = M_UNITS
    by = top_of_head + M * 0.5      # the brim's bottom edge
    bt = by - M * 1.05              # its top
    shape = [(-M * 4.4, by), (-M * 3.0, bt), (-M * 2.0, bt - M * 0.2), (-M * 1.2, bt - M * 1.2), (-M * 0.2, bt - M * 2.0), (M * 1.2, bt - M * 2.5),
             (M * 2.9, bt - M * 2.2), (M * 1.6, bt - M * 1.55), (M * 1.6, bt - M * 0.1), (M * 2.8, bt), (M * 4.4, by)]
    crown = cut([(cx + x, y) for x, y in shape], "hatc", 0.1)
    s.add(crown, HAT)
    s.add(cut([(cx - M * 2.05, bt + 0.2), (cx + M * 1.75, bt + 0.2), (cx + M * 1.7, bt - M * 1.05), (cx - M * 1.6, bt - M * 1.05)], "hatband", 0.06),
          HAT_BAND)
    return min(y for _, y in crown)


def scarecrow():
    """The scarecrow on its pole, its foot at (0, 0), facing us, and the light of its face. Every band [M_UNITS] units or
    more. Returns the two sheets and the hat's tip."""
    s, light = Sheet(), Sheet()
    M = M_UNITS
    # Everything that reaches the ground is cut flat there.
    s.clip("ground", [[(-60.0, -120.0), (60.0, -120.0), (60.0, 0.0), (-60.0, 0.0)]])
    shoe_top, hem, shoulders = -M * 1.0, -M * 2.65, -M * 5.35
    s.add(cut([(-M * 0.64, 0.0), (M * 0.64, 0.0), (M * 0.6, shoulders), (-M * 0.6, shoulders)], "pole", 0.1), POLE, clip="ground")
    for i, sx in enumerate((-1, 1)):
        x0 = sx * M * 1.3
        leg = [(x0 - M * 0.58, hem - 1.0), (x0 + M * 0.58, hem - 1.0), (x0 + M * 0.58 + sx * 0.3, shoe_top + 0.6), (x0 - M * 0.58 + sx * 0.3, shoe_top + 0.6)]
        s.add(cut(leg, f"leg{i}", 0.12), TROUSERS)
        sx0 = x0 + sx * M * 0.3
        s.add(cut([(sx0 - M * 0.68, shoe_top), (sx0 + M * 0.68, shoe_top), (sx0 + M * 0.8, shoe_top * 0.5), (sx0 + M * 0.68, 0.0),
                   (sx0 - M * 0.68, 0.0), (sx0 - M * 0.8, shoe_top * 0.5)], f"shoe{i}", 0.08), SHOE, clip="ground")
    # The arms, straight out along the cross-bar, the straw out of the cuffs: the left cuff the right one's mirror.
    arm_y0, arm_y1 = shoulders, shoulders + M * 1.15
    reach = M * 3.8
    for i, sx in enumerate((-1, 1)):
        arm = [(sx * M * 1.5, arm_y0), (sx * reach, arm_y0 + 0.4), (sx * reach, arm_y1 + 0.4), (sx * M * 1.5, arm_y1)]
        s.add(cut(arm, f"arm{i}", 0.15), COAT)
        if sx > 0:
            cuff = hbar(reach - 0.6, reach + M * 1.15, arm_y0 + 0.4, arm_y1 + 0.2, True, f"cuff{i}")
            s.add(mirror_x(cuff), STRAW)
            s.add(cuff, STRAW)
    # The coat, its hem of straw under it, and its two patches.
    cw = M * 1.95
    s.add(vbar(-M * 0.66, M * 0.66, hem - 1.0, hem + M * 1.1, "hem"), STRAW)
    coat = [(-cw, shoulders - 1.0), (cw, shoulders - 1.0), (cw + 0.6, (shoulders + hem) / 2), (cw + 1.0, hem), (-cw - 1.0, hem),
            (-cw - 0.6, (shoulders + hem) / 2)]
    s.add(cut(coat, "coat", 0.15), COAT)
    p = M * 1.15
    s.add(cut([(M * 0.35, hem - 0.6 - p), (M * 0.35 + p, hem - 0.4 - p), (M * 0.35 + p, hem - 0.6), (M * 0.35, hem - 0.8)], "patch1", 0.1), PATCH)
    s.add(cut([(-cw + 0.6, arm_y1), (-cw + 0.6 + p, arm_y1 - 0.2), (-cw + 0.4 + p, arm_y1 + p), (-cw + 0.4, arm_y1 + p + 0.2)], "patch2", 0.1), PATCH2)
    head_cy = shoulders - (M * 1.08 + M * 1.04 + M * 1.1) / 2 - M * 0.75 + M * 0.55
    top = pumpkin_head(s, light, 0.0, head_cy)
    tip = hat(s, 0.0, top)
    return s, light, tip


NAMES = ["scarecrow", "scarecrow_light"]


def build():
    """Everything this writes, as {path: bytes}, and the table's numbers."""
    s, light, tip = scarecrow()
    out, t = {}, {}
    body = s.body()
    x, y, w, h = content_box(body, (-40, -80, 80, 90), PX)
    # Centred on the pole and ending at the ground: the foot is the bottom centre of the canvas.
    half = max(-x, x + w)
    assert y + h == 0, f"the drawing reaches {y + h} units below the ground"
    box = (-half, y, 2 * half, h)
    out[SVG_DIR / "scarecrow.svg"] = svg_document(body, box, PX, "The Halloween scarecrow (DESIGN_NOTES.md §12, decision 80): "
                                                  "on its pole, the foot of the pole at (0,0) at the bottom centre, the "
                                                  "pumpkin's cuts dark as a carved pumpkin's by day. Fixed art. Generated by "
                                                  "tools/assets/scarecrow/build_scarecrow.py: edit that, not this.").encode()
    out[RES / "scarecrow.png"] = render_body(body, box, PX)
    lbody = light.body()
    lbox = content_box(lbody, (-20, -60, 40, 40), PX, margin=2)
    out[SVG_DIR / "scarecrow_light.svg"] = svg_document(lbody, lbox, PX, "The candle's light in the Halloween scarecrow's carved face"
                                                        ": its cuts in a warm yellow, laid over them at night, in the frame of "
                                                        "the pole's foot. Fixed art. Generated by tools/assets/scarecrow/build_scarecrow.py: "
                                                        "edit that, not this.").encode()
    out[RES / "scarecrow_light.png"] = render_body(lbody, lbox, PX)
    t.update(x=box[0], y=box[1], w=box[2], h=box[3], light_x=lbox[0], light_y=lbox[1], light_w=lbox[2], light_h=lbox[3], tall=-tip)
    out[TABLE] = kotlin_table(t).encode()
    return out, t


def f(x: float) -> str:
    return f"{x:.2f}f"


def kotlin_table(t) -> str:
    return f'''package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * The Halloween scarecrow, in units from the foot of its pole: where its two pieces blit, and how tall it is.
 * **Generated** by `tools/assets/scarecrow/build_scarecrow.py` from the geometry it draws the SVGs with; edit that
 * script, not this file.
 */
internal object ScarecrowTable {{
    /** The scarecrow on its pole, and where it blits: its canvas is centred on the pole and ends at the ground. */
    val RES = R.drawable.scarecrow
    const val X = {f(t["x"])}
    const val Y = {f(t["y"])}

    /** The candle's light in its carved face, laid over it at night, and where it blits. */
    val LIGHT_RES = R.drawable.scarecrow_light
    const val LIGHT_X = {f(t["light_x"])}
    const val LIGHT_Y = {f(t["light_y"])}

    /** The drawing's height, the pole's foot to the hat's tip, in units, and what it reads as. */
    const val UNITS_TALL = {f(t["tall"])}
    const val METRES_TALL = {f(METRES_TALL)}

    /** The narrowest designed band, in units. The card draws its scarecrow at a snowman's height, not on this. */
    const val SCARECROW_BAND_UNITS = {f(M_UNITS)}
}}
'''


# ------------------------------------------------------------------ the 2 px rule at the farthest place
def crisp(svg: str) -> Image.Image:
    """An SVG rendered without antialiasing: every pixel one paper's, so a band's width is its own."""
    return Image.open(io.BytesIO(render_svg(svg.replace("<svg ", '<svg shape-rendering="crispEdges" ', 1)).png_bytes)).convert("RGBA")


def day_and_night(out, t) -> tuple[Image.Image, Image.Image]:
    """The scarecrow rendered without antialiasing, by day, and at night with its light laid over it where it blits."""
    day = crisp(out[SVG_DIR / "scarecrow.svg"].decode())
    night = day.copy()
    night.alpha_composite(crisp(out[SVG_DIR / "scarecrow_light.svg"].decode()),
                          (int(round((t["light_x"] - t["x"]) * PX)), int(round((t["light_y"] - t["y"]) * PX))))
    return day, night


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
    """Every piece of one colour of the scarecrow, by day and at night, that holds no square of 2 px anywhere where it
    stands farthest on the BV6600."""
    ppu = px_per_unit_farthest(t["tall"])
    side = 2.0 / ppu * PX
    k = math.ceil(side)
    lines, bad = [], 0
    for label, image in zip(("day", "night"), day_and_night(out, t)):
        a = np.array(image).astype(np.int64)
        opaque = a[:, :, 3] == 255
        keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
        for key in np.unique(keys[opaque]):
            mask = opaque & (keys == key)
            ok = squares(mask, k)
            for pts in pieces(mask):
                if len(pts) >= 12 and not any(ok[p] for p in pts):
                    bad += 1
                    ys, xs = [p[0] for p in pts], [p[1] for p in pts]
                    lines.append(f"{label}: #{int(key):06X} {len(pts)} px at x {min(xs)}..{max(xs)} y {min(ys)}..{max(ys)} of the 3x drawing")
    lines.append(f"{bad} pieces under 2 px at the farthest place, {ppu:.3f} px a unit ({FARTHEST_PX_TALL:.1f} px tall; a square of {k} px "
                 f"of the 3x drawing, 2 px are {side:.2f}); the narrowest band {M_UNITS} units, {M_UNITS * ppu:.2f} px")
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
    ppu = px_per_unit_farthest(t["tall"])
    print(f"the scarecrow {t['w'] * PX} x {t['h'] * PX} px ({t['w']} x {t['h']} units, {t['tall']:.2f} tall to the hat's tip, {METRES_TALL} m), "
          f"its light {t['light_w'] * PX} x {t['light_h'] * PX} px; at the farthest place {FARTHEST_PX_TALL:.1f} px tall, {ppu:.3f} px a unit, "
          f"the narrowest band {M_UNITS} units = {M_UNITS * ppu:.2f} px")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
