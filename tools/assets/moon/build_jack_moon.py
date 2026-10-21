#!/usr/bin/env python3
"""The Halloween moon (v5.15): the moon with a jack-o'-lantern's face cut out of it, lit from inside.

The maintainer, 2026-10-18: *«migliorare estetica/sprite zucca halloween che prende il posto della luna»*. Shown three
pumpkins in the moon's place (2026-10-19): *«va bene l'espressione da zucca ma non deve essere una zucca, è comunque la
luna / rifalla»*; shown three moons, *«C – faccia accesa»* (2026-10-19). So it is the moon -- the full moon's disc cut
with the scissors' 40 facets, five craters, always full -- with the pumpkins' carved face: two slanted triangle eyes, a
triangle nose and a wide grin with two teeth left in it, cut out of the disc (`DESIGN_NOTES.md` §16, "carved rather
than painted"), and a warm light inside the cuts.

It writes, from one geometry, in the moon's frame (240 x 240 px, `CANVAS_PIXELS`, its middle on the moon's centre, as
every moon):

* ``sources/svg/moon_jack_o_lantern.svg`` -- the moon with its craters and the face cut out, a **tintable mask** as
  every moon is (one light tone and the craters a little darker, `#F4F4F4` and `#E1E1E1` as `moon_full`): the renderer
  gives it the theme's own moon colour (until v5.15 an orange fixed for every theme);
* ``sources/svg/moon_jack_o_lantern_light.svg`` -- fixed art, blitted over the moon untinted: the light inside the cuts,
  a warm yellow with an orange rim, so the face glows as a lit lantern's does. **Trimmed to the face** with a
  transparent guard pixel on every side (`ARCHITECTURE.md` §5, "The transparent margin is part of the drawing") and
  rounded up to the 3-px grid, so it
  costs its own content rather than the moon's whole canvas (inventory I-940); where it blits from the moon's centre
  ([light_origin]) is `PaperRenderer.HALLOWEEN_LIGHT_ORIGIN_X`/`_Y`, which `tests/test_jack_moon.py` compares with it.

With ``--res`` the two are rendered with the pipeline's own rasteriser into ``app/src/main/res/drawable-nodpi/``;
``--check`` writes nothing and fails unless every committed output is what this draws (``tests/test_jack_moon.py``).

**Every crater, eye and nose, and the paper between two of them, 11 px of the 240 or more** -- 2 px on the gallery card
(where the moon's 240-px frame is about 44 px) and 7 px on the BV6600's wallpaper (about 158). The grin's two teeth are
about 14 px of the 240 wide; the first is 10 to 11 px long (1.8 to 2.1 px on the card), the second 12 to 13 (measured on
the generator's own corners by the seal v5.15D): the first stays so, the maintainer's *«Lascia così (consigliata)»*
(2026-10-20), asked with the card's photograph. **The light's orange rim is [RIM] px of the 240 wide all along every
cut** -- 2.6 px on the wallpaper, under 1 px on the card, where it reads as the yellow's edge (chosen so with the moon C,
*«C – faccia accesa»*, 2026-10-19, on the page of v5.15A that gave the card's rim as about 0.8 px): the cut's own outline
stroked twice that wide over the yellow and
clipped to the cut, so the rim follows the corners and the teeth too. Until v5.15C2 the rim was only what the yellow, the
cut drawn smaller and lower, left of the cut: 3 px of the 240 somewhere in every cut, but 0.3 to 0.8 px at the corners of
the eyes and the nose and none along the teeth.

Usage, from ``tools/assets`` with the venv's interpreter:

    python moon/build_jack_moon.py [--res] [--check]
"""
from __future__ import annotations

import io
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent
sys.path.insert(0, str(ASSETS))
from papercut import cut, d, ellipse  # noqa: E402
from paperscrape_assets.raster import render_svg  # noqa: E402

REPO = ASSETS.parent.parent
SVG_DIR = ASSETS / "sources" / "svg"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
W = 240
C = 120.0
TONE = "#F4F4F4"
CRATER = "#E1E1E1"
LIT = "#FFD54F"
LIT_RIM = "#F39C2E"


def disc():
    """The moon's own edge: the 40 cuts of `moon_full`, read from its source, so the disc is the full moon's exactly."""
    text = (SVG_DIR / "moon_full.svg").read_text()
    pts = re.search(r'<polygon points="([^"]+)" fill="#F4F4F4"', text).group(1)
    return [tuple(float(v) for v in p.split(",")) for p in pts.split()]


def face():
    """The carved face, the pumpkins' expression the maintainer liked, set on the disc."""
    cx, cy = C, C - 10.0
    le = [(cx - 54, cy - 30), (cx - 16, cy - 20), (cx - 30, cy + 4)]
    rg = [(cx + 54, cy - 30), (cx + 16, cy - 20), (cx + 30, cy + 4)]
    # The nose a little larger than in the drawing photographed in v5.15A (22 x 17), so the square that fits in it is 11 px of the 240: 2 px
    # on the gallery card.
    nose = [(cx, cy - 4), (cx + 13, cy + 16), (cx - 13, cy + 16)]
    mouth = [(cx - 66, cy + 20), (cx - 44, cy + 28), (cx - 34, cy + 28), (cx - 34, cy + 40), (cx - 20, cy + 40),
             (cx - 20, cy + 30), (cx + 8, cy + 32), (cx + 8, cy + 44), (cx + 22, cy + 44), (cx + 22, cy + 31),
             (cx + 44, cy + 28), (cx + 66, cy + 20), (cx + 52, cy + 48), (cx + 26, cy + 64), (cx - 2, cy + 68),
             (cx - 28, cy + 64), (cx - 52, cy + 50)]
    return [cut(le, "j-le", 1.2), cut(rg, "j-re", 1.2), cut(nose, "j-n", 0.8), cut(mouth, "j-m", 1.0)]


def craters():
    """Five irregular craters where the face leaves room: the forehead, the temples, a cheek, the chin's side."""
    # Each at least 16 px across its narrow way (the two smallest of v5.15A's drawing were 14): an 11-px square, 2 px on the
    # gallery card, fits in every one.
    spots = [(C - 18, C - 70, 13, 9, 10), (C + 30, C - 62, 10, 8, -15), (C - 78, C + 6, 8, 11, 5), (C + 80, C + 12, 8, 12, -8),
             (C + 60, C + 68, 11, 8, 20)]
    return [cut(ellipse(x, y, rx, ry, 10, rot), f"crater{i}", 0.6) for i, (x, y, rx, ry, rot) in enumerate(spots)]


def moon_svg() -> str:
    holes = "".join(f'<path d="{d(f)}" fill="#000000"/>' for f in face())
    return "\n".join([
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{W}" viewBox="0 0 {W} {W}">',
        "  <!-- The Halloween moon (v5.15, the maintainer's «C – faccia accesa», 2026-10-19): the full moon's disc and five "
        "craters, the jack-o'-lantern's face cut out of it so the sky shows through. Tintable mask, one tone #F4F4F4 and the "
        "craters #E1E1E1, in the moon's 240-px frame. Generated by tools/assets/moon/build_jack_moon.py: edit that, not this. -->",
        f'  <defs><mask id="face" maskUnits="userSpaceOnUse" x="0" y="0" width="{W}" height="{W}"><rect x="0" y="0" width="{W}" height="{W}" fill="#ffffff"/>{holes}</mask></defs>',
        '  <g mask="url(#face)">',
        f'    <path d="{d(disc())}" fill="{TONE}"/>',
        *[f'    <path d="{d(c)}" fill="{CRATER}"/>' for c in craters()],
        "  </g>",
        "</svg>",
    ]) + "\n"


def light_paths() -> list[str]:
    """Each cut filled with the rim's orange; over it the yellow, the cut drawn smaller about its middle and a little
    lower, as a lantern's light pools; and over the yellow the rim's band, the cut's outline [RIM] wide inside it. All
    clipped to the cut, so the light never lies on the moon's paper -- the teeth keep their length."""
    out = []
    for i, (f, (kx, ky)) in enumerate(zip(face(), LIGHT_SCALE)):
        cx = sum(x for x, _ in f) / len(f)
        cy = sum(y for _, y in f) / len(f)
        out.append(f'  <clipPath id="cut{i}"><path d="{d(f)}"/></clipPath>')
        out.append(f'  <path d="{d(f)}" fill="{LIT_RIM}"/>')
        out.append(f'  <path d="{d([(cx + (x - cx) * kx, cy + (y - cy) * ky + 2) for x, y in f])}" fill="{LIT}" clip-path="url(#cut{i})"/>')
        out.append(f'  <path d="{d(f)}" fill="none" stroke="{LIT_RIM}" stroke-width="{2 * RIM:g}" stroke-linejoin="round" '
                   f'clip-path="url(#cut{i})"/>')
    return out


#: How much smaller than its cut the yellow is drawn, across and down, for the two eyes, the nose and the mouth: the
#: small cuts' a little smaller still than the mouth's (0.76 and 0.7 for all four in v5.15A's drawing).
LIGHT_SCALE = ((0.72, 0.64), (0.72, 0.64), (0.62, 0.56), (0.76, 0.7))

#: The orange rim's width inside every cut, in px of the 240: 2.6 px on the BV6600's wallpaper (158 px across), so the
#: 2 px rule holds with the margin a pixel grid needs; 3.04 would be 2 px exactly.
RIM = 4.0


def light_box() -> tuple[int, int, int, int]:
    """The light's content in the moon's frame, with a transparent guard pixel on every side: (x, y, w, h)."""
    probe = "\n".join([f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{W}" viewBox="0 0 {W} {W}">',
                       *light_paths(), "</svg>"])
    a = np.array(Image.open(io.BytesIO(render_svg(probe).png_bytes)).convert("RGBA"))[:, :, 3]
    ys, xs = np.where(a > 0)
    x0, y0 = int(xs.min()) - 1, int(ys.min()) - 1
    x1, y1 = int(xs.max()) + 2, int(ys.max()) + 2
    # A whole number of the 3-px grid every shipped sprite is authored on (DESIGN_NOTES.md §4): what is over goes to the
    # right and the bottom, so the origin stays the guard pixel's.
    w, h = -(-(x1 - x0) // 3) * 3, -(-(y1 - y0) // 3) * 3
    return x0, y0, w, h


def light_origin() -> tuple[int, int]:
    """Where the light blits, from the moon's centre, in px of the 240-px frame: its trimmed box's corner."""
    x, y, _, _ = light_box()
    return x - int(C), y - int(C)


def light_svg() -> str:
    x, y, w, h = light_box()
    ox, oy = light_origin()
    return "\n".join([
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{x} {y} {w} {h}">',
        "  <!-- The light inside the Halloween moon's carved face (v5.15, the maintainer's «C – faccia accesa», 2026-10-19): a warm "
        f"yellow with an orange rim. Fixed art, blitted untinted over the moon at ({ox},{oy}) px from its centre: trimmed to the "
        "face, a transparent pixel round it. Generated by tools/assets/moon/build_jack_moon.py: edit that, not this. -->",
        *light_paths(),
        "</svg>",
    ]) + "\n"


def build():
    """Everything this writes, as {path: bytes}."""
    out = {}
    for name, svg in (("moon_jack_o_lantern", moon_svg()), ("moon_jack_o_lantern_light", light_svg())):
        out[SVG_DIR / f"{name}.svg"] = svg.encode()
        out[RES / f"{name}.png"] = render_svg(svg).png_bytes
    return out


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
    x, y, w, h = light_box()
    print(f"the light: {w}x{h} px at {light_origin()} from the moon's centre, decoded {w * h * 4} B (the whole frame: {W * W * 4} B)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
