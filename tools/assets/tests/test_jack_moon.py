"""The Halloween moon: `moon/build_jack_moon.py` draws it and its light, from one geometry.

The moon with the face lit (`DESIGN_NOTES.md` §12, decision 74). These hold that the two SVG sources and the two PNGs the
pipeline renders of them are the generator's output today; that the moon's edge is the full moon's own; that every
piece of each tone -- the disc, each crater -- and each cut of the face holds a square of 11 px of the 240 (2 px on the
gallery card) somewhere in it, and that the paper between two cuts is 11 px of the 240 across or more, measured on a
rendering without antialiasing (the paper is one piece with the disc, which the first rule cannot see; the grin's teeth
are measured on the generator's corners, its docstring says how long, and the first one's 1.8 to 2.1 px on the card is
kept by decision 74);
that the light lies inside the cuts,
and that its orange rim is 2 px wide on the BV6600's wallpaper all along every cut, its corners and the teeth included --
no yellow nearer the cut's edge than that, not only "somewhere in every cut", which a rim thin at the corners and missing
along the teeth would pass; and that the renderer blits the light where the generator trimmed it to.

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
from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))
sys.path.insert(0, str(TOOL_ROOT / "moon"))

import build_jack_moon  # noqa: E402
from paperscrape_assets.raster import render_svg, same_output  # noqa: E402

RENDERER = TOOL_ROOT.parent.parent / "app" / "src" / "main" / "kotlin" / "com" / "paperscrape" / "livewallpaper" / "engine" / "PaperRenderer.kt"

#: The moon is about 44 px across on the gallery card and 158 on the BV6600's wallpaper, for its 240-px frame.
CARD_PX, PHONE_PX = 44.0, 158.0


def erode(m, r):
    out = m.copy()
    for _ in range(r):
        p = np.pad(out, 1, constant_values=False)
        out = (p[1:-1, 1:-1] & p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
               & p[:-2, :-2] & p[2:, 2:] & p[:-2, 2:] & p[2:, :-2])
    return out


def pieces(m):
    seen = np.zeros_like(m)
    H, W = m.shape
    out = []
    for y in range(H):
        for x in range(W):
            if m[y, x] and not seen[y, x]:
                q = deque([(y, x)])
                seen[y, x] = True
                pts = []
                while q:
                    a, b = q.popleft()
                    pts.append((a, b))
                    for da, db in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        c, d = a + da, b + db
                        if 0 <= c < H and 0 <= d < W and m[c, d] and not seen[c, d]:
                            seen[c, d] = True
                            q.append((c, d))
                out.append(pts)
    return out


def crisp(svg: str) -> np.ndarray:
    return np.array(Image.open(io.BytesIO(render_svg(svg.replace("<svg ", '<svg shape-rendering="crispEdges" ', 1)).png_bytes)).convert("RGBA"))


class JackMoonTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.out = build_jack_moon.build()
        cls.moon = crisp(cls.out[build_jack_moon.SVG_DIR / "moon_jack_o_lantern.svg"].decode())
        cls.light = crisp(cls.out[build_jack_moon.SVG_DIR / "moon_jack_o_lantern_light.svg"].decode())

    def test_every_file_is_the_generator_output(self):
        for path, data in self.out.items():
            with self.subTest(file=path.name):
                self.assertTrue(path.exists(), f"{path} is missing")
                self.assertTrue(same_output(path, data), f"{path.name} is not what moon/build_jack_moon.py writes: run it with --res")

    def test_the_edge_is_the_full_moons(self):
        """The Halloween moon's own source carries the full moon's 40 cuts as its edge: read off `moon_full.svg`, here, and
        looked for in `moon_jack_o_lantern.svg` (looked for in `moon_full` itself, they would be found whatever the
        Halloween moon drew)."""
        full = (build_jack_moon.SVG_DIR / "moon_full.svg").read_text()
        points = [tuple(float(v) for v in p.split(",")) for p in re.search(r'<polygon points="([^"]+)" fill="#F4F4F4"', full).group(1).split()]
        self.assertEqual(40, len(points))
        jack = (build_jack_moon.SVG_DIR / "moon_jack_o_lantern.svg").read_text()
        edge = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in points) + " Z"
        self.assertIn(f'<path d="{edge}" fill="{build_jack_moon.TONE}"/>', jack)

    def test_every_part_is_two_pixels_or_more_on_the_card(self):
        """11 px of the 240 is 2 px on the card: every piece of each tone, and every cut, survives an erosion that leaves
        an 11-px square. The paper between the cuts is one piece with the disc, so it is measured by the test after this
        one, not here."""
        side = math.ceil(2.0 / (CARD_PX / build_jack_moon.W))
        r = math.ceil((side - 1) / 2)
        a = self.moon.astype(int)
        opaque = a[:, :, 3] == 255
        keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
        # the cuts: what is not paper inside the disc
        from PIL import ImageDraw
        disc = Image.new("L", (build_jack_moon.W, build_jack_moon.W), 0)
        ImageDraw.Draw(disc).polygon(build_jack_moon.disc(), fill=255)
        inside = np.array(disc) == 255
        masks = {"#%06X" % int(k): opaque & (keys == k) for k in np.unique(keys[opaque])}
        masks["the cuts"] = inside & (a[:, :, 3] == 0)
        for name, m in masks.items():
            er = erode(m, r)
            for pts in pieces(m):
                if len(pts) < 12:
                    continue
                with self.subTest(part=name, at=pts[0]):
                    self.assertTrue(any(er[y, x] for y, x in pts), f"{len(pts)} px of {name} under {side} px everywhere")

    def test_the_paper_between_two_cuts_is_two_pixels_or_more_on_the_card(self):
        """Between any two cuts of the face -- an eye and the nose, the nose and the grin -- the paper is 11 px of the 240
        across or more, 2 px on the card: the nearest pixels of two different cuts are farther apart than that (the paper
        is one piece with the disc, so the erosion of every piece would pass it whatever the cuts did)."""
        side = math.ceil(2.0 / (CARD_PX / build_jack_moon.W))
        from PIL import ImageDraw
        disc = Image.new("L", (build_jack_moon.W, build_jack_moon.W), 0)
        ImageDraw.Draw(disc).polygon(build_jack_moon.disc(), fill=255)
        cut = (np.array(disc) == 255) & (self.moon[:, :, 3] == 0)
        cuts = [np.array(p) for p in pieces(cut) if len(p) >= 12]
        self.assertEqual(4, len(cuts), "two eyes, the nose and the grin")
        for i in range(len(cuts)):
            for j in range(i + 1, len(cuts)):
                a, b = cuts[i], cuts[j]
                nearest = min(float(np.sqrt(((a[k:k + 256, None, :] - b[None, :, :]) ** 2).sum(axis=2)).min())
                              for k in range(0, len(a), 256))
                with self.subTest(cuts=(tuple(a[0]), tuple(b[0]))):
                    # Two pixels whose centres are n apart have n - 1 pixels of paper between them.
                    self.assertGreaterEqual(nearest - 1, side, f"{nearest - 1:.1f} px of paper between two cuts")

    def test_the_light_lies_in_the_cuts_and_its_rim_reads_on_the_wallpaper(self):
        """The light inside the cuts only; and its rim, measured everywhere: at four times the light's size, every yellow
        pixel is 2 px of the BV6600's wallpaper or more from the nearest pixel outside the light -- so along every edge of
        every cut, at its corners and along the teeth, the orange between the cut's edge and the yellow is that wide."""
        ox, oy = build_jack_moon.light_origin()
        h, w = self.light.shape[:2]
        x0, y0 = int(build_jack_moon.C) + ox, int(build_jack_moon.C) + oy
        paper = erode(self.moon[y0:y0 + h, x0:x0 + w, 3] == 255, 1)
        lit = self.light[:, :, 3] == 255
        # The light's edge is the cut's edge, and one pixel of it may fall either side of the cut's.
        self.assertEqual(0, int((lit & paper).sum()), "the light is drawn over the moon's paper")
        # The rim at four times the light's size, so 2 px on the wallpaper -- 3.04 px of the 240 -- is a whole number
        # of pixels to erode by.
        k = 4
        x, y, w4, h4 = build_jack_moon.light_box()
        big = re.sub(r'width="\d+" height="\d+"', f'width="{w4 * k}" height="{h4 * k}"', build_jack_moon.light_svg(), count=1)
        a = crisp(big).astype(int)
        light = a[:, :, 3] == 255
        yellow = light & (np.abs(a[:, :, :3] - np.array([0xFF, 0xD5, 0x4F])).sum(axis=2) == 0)
        self.assertGreater(int(yellow.sum()), 1000, "no yellow found")
        width = 2.0 / (PHONE_PX / build_jack_moon.W) * k
        # The light eroded by a disc of that radius: what is left is at least that far from every pixel outside it.
        reach = int(math.ceil(width))
        far = light.copy()
        padded = np.pad(light, reach, constant_values=False)
        h4, w4 = light.shape
        for dy in range(-reach, reach + 1):
            for dx in range(-reach, reach + 1):
                if dx * dx + dy * dy <= width * width:
                    far &= padded[reach + dy:reach + dy + h4, reach + dx:reach + dx + w4]
        near = yellow & ~far
        self.assertEqual(0, int(near.sum()), f"yellow under {width / k:.2f} px of the 240 from a cut's edge: the rim is under 2 px there on the wallpaper")
        # And there is a rim: the orange is between them.
        orange = light & (np.abs(a[:, :, :3] - np.array([0xF3, 0x9C, 0x2E])).sum(axis=2) == 0)
        self.assertGreater(int(orange.sum()), 1000)

    def test_the_renderer_blits_the_light_where_it_was_trimmed(self):
        text = RENDERER.read_text()
        x = float(re.search(r"HALLOWEEN_LIGHT_ORIGIN_X = (-?[0-9.]+)f", text).group(1))
        y = float(re.search(r"HALLOWEEN_LIGHT_ORIGIN_Y = (-?[0-9.]+)f", text).group(1))
        self.assertEqual(build_jack_moon.light_origin(), (int(x), int(y)))


if __name__ == "__main__":
    unittest.main()
