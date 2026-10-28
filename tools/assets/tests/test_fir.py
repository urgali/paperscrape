"""The Christmas fir: `fir/build_fir.py` draws it and declares it, from one geometry.

The fir is the fir A (`DESIGN_NOTES.md` §12, decision 66). These hold that every file the generator writes -- the three SVG sources, the three PNGs the pipeline renders of them, `FirTable.kt` and the fir
of the Christmas launcher icon -- is its output today; that no colour of the fir, with its snow on or not, is a
piece narrower than 2 px at the farthest place a tree stands on the BV6600; that the gallery card's baubles hang
on the fir's paper; and that the icon's fir stays inside the adaptive icon's safe zone.

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
sys.path.insert(0, str(TOOL_ROOT / "fir"))

import build_fir  # noqa: E402
from paperscrape_assets.raster import same_output  # noqa: E402


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


class FirTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.out = build_fir.build()
        table = cls.out[build_fir.TABLE].decode()
        cls.origin = {}
        for name, key in (("tree_fir", "FIR"), ("tree_fir_snow", "SNOW"), ("tree_fir_star", "STAR")):
            x = float(re.search(rf"const val {key}_X = (-?[0-9.]+)f", table).group(1))
            y = float(re.search(rf"const val {key}_Y = (-?[0-9.]+)f", table).group(1))
            cls.origin[name] = (x, y)
        cls.images = {n: Image.open(io.BytesIO(cls.out[build_fir.RES / f"{n}.png"])).convert("RGBA") for n in cls.origin}

    def test_every_file_is_the_generator_output(self):
        for path, data in self.out.items():
            with self.subTest(file=path.name):
                self.assertTrue(path.exists(), f"{path} is missing")
                self.assertTrue(same_output(path, data), f"{path.name} is not what fir/build_fir.py writes: run it with --res")

    def composed(self, snow: bool):
        """The fir, its snow if [snow] and its star, in one canvas of the fir's frame plus a unit of margin."""
        W, H = (build_fir.W + 2) * 3, (build_fir.H + 2) * 3
        canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        names = ["tree_fir"] + (["tree_fir_snow"] if snow else []) + ["tree_fir_star"]
        for n in names:
            ox, oy = self.origin[n]
            canvas.alpha_composite(self.images[n], (int(round((ox + build_fir.W / 2 + 1) * 3)), int(round((oy + build_fir.H + 1) * 3))))
        return canvas

    def test_no_colour_is_a_piece_under_two_pixels_at_the_farthest_tree(self):
        """At the farthest tree a unit is 0.674 px on the BV6600, so 2 px are 2.97 units, 8.9 px of the drawing: a
        piece of one colour that does not survive an erosion of 4 px (a 9 x 9 square) is under 2 px everywhere.
        Pieces under 12 px are the antialiasing at the cards' edges."""
        for snow in (False, True):
            a = np.array(self.composed(snow)).astype(int)
            opaque = a[:, :, 3] > 250
            colours = {}
            for c in map(tuple, a[opaque][:, :3]):
                colours[c] = colours.get(c, 0) + 1
            for c in [c for c, k in colours.items() if k > 150]:
                m = opaque & (np.abs(a[:, :, :3] - np.array(c)).sum(axis=2) <= 6)
                er = erode(m, 4)
                for pts in pieces(m):
                    if len(pts) < 12:
                        continue
                    with self.subTest(snow=snow, colour="#%02X%02X%02X" % c, at=pts[0]):
                        self.assertTrue(any(er[y, x] for y, x in pts), f"{len(pts)} px of #{c} under 9 px everywhere")

    def test_the_card_baubles_hang_on_the_fir(self):
        a = np.array(self.composed(False))[:, :, 3]
        for bx, by in build_fir.BAUBLES:
            with self.subTest(bauble=(bx, by)):
                px = int(round((bx + build_fir.W / 2 + 1) * 3))
                py = int(round((by + build_fir.H + 1) * 3))
                self.assertGreater(a[py, px], 250)

    def test_the_lights_hang_on_the_fir(self):
        a = np.array(self.composed(False))[:, :, 3]
        table = self.out[build_fir.TABLE].decode()
        xs = [float(v) for v in re.search(r"LIGHT_X = floatArrayOf\(([^)]*)\)", table).group(1).replace("f", "").split(",")]
        ys = [float(v) for v in re.search(r"LIGHT_Y = floatArrayOf\(([^)]*)\)", table).group(1).replace("f", "").split(",")]
        self.assertEqual(10, len(xs))
        for x, y in zip(xs, ys):
            with self.subTest(light=(x, y)):
                self.assertGreater(a[int(round((y + build_fir.H + 1) * 3)), int(round((x + build_fir.W / 2 + 1) * 3))], 250)

    def test_the_icon_fir_stays_in_the_safe_zone_and_its_star_is_the_icon_size(self):
        xml = self.out[build_fir.ICON].decode()
        block = xml[xml.index(build_fir.ICON_BEGIN):xml.index(build_fir.ICON_END)]
        worst = 0.0
        for d in re.findall(r'android:pathData="([^"]+)"', block):
            nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", d)]
            for x, y in zip(nums[0::2], nums[1::2]):
                worst = max(worst, math.hypot(x - 54, y - 54))
        self.assertLessEqual(worst, 33.0)
        star = [p for p in re.findall(r'android:fillColor="#FFF2C24A"[^>]*android:pathData="([^"]+)"', block)]
        self.assertEqual(1, len(star))
        nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", star[0])]
        xs, ys = nums[0::2], nums[1::2]
        self.assertAlmostEqual(build_fir.ICON_STAR_WIDTH, max(xs) - min(xs), delta=0.15)
        self.assertAlmostEqual(build_fir.ICON_TOP, min(ys), delta=0.1)


if __name__ == "__main__":
    unittest.main()
