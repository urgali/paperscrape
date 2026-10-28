"""The Halloween moon laughing: `moon/build_jack_moon_laugh.py` draws its face on the moon's own geometry.

The moon laughing, its eyes squeezed into arches and its grin thrown open (`DESIGN_NOTES.md` §12, decision 81). These
hold, as `test_jack_moon.py` holds the moon's own face, that the two SVG sources and the two PNGs the pipeline renders of
them are the generator's output today; that the moon's edge is the full moon's and its nose the moon's own; that every
piece of each tone and every cut holds a square of 11 px of the 240 (2 px on the gallery card) somewhere, and that the
paper between two cuts is 11 px across or more, on a rendering without antialiasing; **that no cut leaves a piece of its
own** -- the face is the two arches, the nose and the grin and nothing else, in the moon and in its light (an arch ended
in a point can leave a cut of 1 px apart, which the rule above, blind to pieces under 12 px, would pass); that the light
lies inside the cuts and its orange rim is 2 px wide on the BV6600's wallpaper all along every
cut; and that the renderer blits the light where the generator trimmed it to (`PaperRenderer.HALLOWEEN_LAUGH_LIGHT_ORIGIN_X`
and `_Y`).

Run from `tools/assets/`: python3 -m unittest discover -s tests
"""
from __future__ import annotations

import io
import math
import re
import sys
import unittest
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))
sys.path.insert(0, str(TOOL_ROOT / "moon"))

import build_jack_moon  # noqa: E402
import build_jack_moon_laugh as laugh  # noqa: E402
from paperscrape_assets.raster import render_svg, same_output  # noqa: E402

RENDERER = TOOL_ROOT.parent.parent / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / "engine" / "PaperRenderer.kt"

#: The moon is about 44 px across on the gallery card and 158 on the BV6600's wallpaper, for its 240-px frame.
CARD_PX, PHONE_PX = 44.0, 158.0
W = build_jack_moon.W


def erode(m, r):
    out = m.copy()
    for _ in range(r):
        p = np.pad(out, 1, constant_values=False)
        out = (p[1:-1, 1:-1] & p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
               & p[:-2, :-2] & p[2:, 2:] & p[:-2, 2:] & p[2:, :-2])
    return out


def pieces(m):
    seen = np.zeros_like(m)
    H, W_ = m.shape
    out = []
    for y, x in zip(*(a.tolist() for a in np.nonzero(m))):
        if seen[y, x]:
            continue
        q = deque([(y, x)])
        seen[y, x] = True
        pts = []
        while q:
            a, b = q.popleft()
            pts.append((a, b))
            for c, d in ((a + 1, b), (a - 1, b), (a, b + 1), (a, b - 1)):
                if 0 <= c < H and 0 <= d < W_ and m[c, d] and not seen[c, d]:
                    seen[c, d] = True
                    q.append((c, d))
        out.append(pts)
    return out


def crisp(svg: str) -> np.ndarray:
    return np.array(Image.open(io.BytesIO(render_svg(svg.replace("<svg ", '<svg shape-rendering="crispEdges" ', 1)).png_bytes)).convert("RGBA"))


class LaughingMoonTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.out = laugh.build()
        cls.moon = crisp(cls.out[laugh.SVG_DIR / "moon_jack_o_lantern_laugh.svg"].decode())
        cls.light = crisp(cls.out[laugh.SVG_DIR / "moon_jack_o_lantern_laugh_light.svg"].decode())
        disc = Image.new("L", (W, W), 0)
        ImageDraw.Draw(disc).polygon(build_jack_moon.disc(), fill=255)
        cls.inside = np.array(disc) == 255
        # The cuts: what is not paper inside the disc, three pixels in from its rim, where a polygon filled here and the
        # rasteriser's disagree by a pixel now and then.
        cls.cuts = erode(cls.inside, 3) & (cls.moon[:, :, 3] == 0)

    def test_every_file_is_the_generator_output(self):
        for path, data in self.out.items():
            with self.subTest(file=path.name):
                self.assertTrue(path.exists(), f"{path} is missing")
                self.assertTrue(same_output(path, data), f"{path.name} is not what moon/build_jack_moon_laugh.py writes: run it with --res")

    def test_the_edge_is_the_full_moons_and_the_nose_the_moons_own(self):
        jack = self.out[laugh.SVG_DIR / "moon_jack_o_lantern_laugh.svg"].decode()
        edge = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in build_jack_moon.disc()) + " Z"
        self.assertIn(f'<path d="{edge}" fill="{build_jack_moon.TONE}"/>', jack)
        nose = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in build_jack_moon.face()[2]) + " Z"
        self.assertIn(f'<path d="{nose}" fill="#000000"/>', jack)

    def test_every_part_is_two_pixels_or_more_on_the_card(self):
        """11 px of the 240 is 2 px on the card: every piece of each tone, and every cut, survives an erosion that leaves
        an 11-px square."""
        side = math.ceil(2.0 / (CARD_PX / W))
        r = math.ceil((side - 1) / 2)
        a = self.moon.astype(int)
        opaque = a[:, :, 3] == 255
        keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
        masks = {"#%06X" % int(k): opaque & (keys == k) for k in np.unique(keys[opaque])}
        masks["the cuts"] = self.inside & (a[:, :, 3] == 0)
        for name, m in masks.items():
            er = erode(m, r)
            for pts in pieces(m):
                if len(pts) < 12:
                    continue
                with self.subTest(part=name, at=pts[0]):
                    self.assertTrue(any(er[y, x] for y, x in pts), f"{len(pts)} px of {name} under {side} px everywhere")

    def test_no_cut_leaves_a_piece_of_its_own(self):
        """The face is four cuts -- two arches, the nose, the grin -- and nothing else, whatever its size: in the moon, and
        in its light where the renderer blits it (a cut of 1 px at the end of an arch is a fifth piece)."""
        sizes = sorted(len(p) for p in pieces(self.cuts))
        self.assertEqual(4, len(sizes), f"the face's cuts are pieces of {sizes} px")
        self.assertGreater(sizes[0], 100, "a cut too small to be an eye, the nose or the grin")
        lit = self.light[:, :, 3] > 0
        self.assertEqual(4, len(pieces(lit)), "the light is not four pieces, one a cut")

    def test_the_paper_between_two_cuts_is_two_pixels_or_more_on_the_card(self):
        side = math.ceil(2.0 / (CARD_PX / W))
        cuts = [np.array(p) for p in pieces(self.cuts) if len(p) >= 12]
        self.assertEqual(4, len(cuts), "two eyes, the nose and the grin")
        for i in range(len(cuts)):
            for j in range(i + 1, len(cuts)):
                a, b = cuts[i], cuts[j]
                nearest = min(float(np.sqrt(((a[k:k + 256, None, :] - b[None, :, :]) ** 2).sum(axis=2)).min())
                              for k in range(0, len(a), 256))
                with self.subTest(cuts=(tuple(a[0]), tuple(b[0]))):
                    self.assertGreaterEqual(nearest - 1, side, f"{nearest - 1:.1f} px of paper between two cuts")

    def test_the_light_lies_in_the_cuts_and_its_rim_reads_on_the_wallpaper(self):
        """The light inside the cuts only; and its rim, everywhere: at four times the light's size, every yellow pixel is
        2 px of the BV6600's wallpaper or more from the nearest pixel outside the light."""
        ox, oy = laugh.light_origin()
        h, w = self.light.shape[:2]
        x0, y0 = int(build_jack_moon.C) + ox, int(build_jack_moon.C) + oy
        paper = erode(self.moon[y0:y0 + h, x0:x0 + w, 3] == 255, 1)
        lit = self.light[:, :, 3] == 255
        self.assertEqual(0, int((lit & paper).sum()), "the light is drawn over the moon's paper")
        k = 4
        _, _, w4, h4 = laugh.light_box()
        big = re.sub(r'width="\d+" height="\d+"', f'width="{w4 * k}" height="{h4 * k}"', laugh.light_svg(), count=1)
        a = crisp(big).astype(int)
        light = a[:, :, 3] == 255
        yellow = light & (np.abs(a[:, :, :3] - np.array([0xFF, 0xD5, 0x4F])).sum(axis=2) == 0)
        self.assertGreater(int(yellow.sum()), 1000, "no yellow found")
        width = 2.0 / (PHONE_PX / W) * k
        reach = int(math.ceil(width))
        far = light.copy()
        padded = np.pad(light, reach, constant_values=False)
        H4, W4 = light.shape
        for dy in range(-reach, reach + 1):
            for dx in range(-reach, reach + 1):
                if dx * dx + dy * dy <= width * width:
                    far &= padded[reach + dy:reach + dy + H4, reach + dx:reach + dx + W4]
        self.assertEqual(0, int((yellow & ~far).sum()), "yellow nearer a cut's edge than 2 px of the wallpaper")
        orange = light & (np.abs(a[:, :, :3] - np.array([0xF3, 0x9C, 0x2E])).sum(axis=2) == 0)
        self.assertGreater(int(orange.sum()), 1000)

    def test_the_renderer_blits_the_light_where_it_was_trimmed(self):
        text = RENDERER.read_text()
        x = re.search(r"HALLOWEEN_LAUGH_LIGHT_ORIGIN_X = (-?[0-9.]+)f", text)
        y = re.search(r"HALLOWEEN_LAUGH_LIGHT_ORIGIN_Y = (-?[0-9.]+)f", text)
        self.assertIsNotNone(x, "PaperRenderer has no HALLOWEEN_LAUGH_LIGHT_ORIGIN_X")
        self.assertIsNotNone(y, "PaperRenderer has no HALLOWEEN_LAUGH_LIGHT_ORIGIN_Y")
        self.assertEqual(laugh.light_origin(), (int(float(x.group(1))), int(float(y.group(1)))))


if __name__ == "__main__":
    unittest.main()
