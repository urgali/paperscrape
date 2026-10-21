"""The update notification's small icon (v5.15): `notification/build_glyph.py` draws it.

The maintainer chose the notification «A», the icon at the top (2026-10-19). These hold that the committed
`res/drawable/ic_stat_update.xml` is the generator's output today, that every gap and band of the glyph is 2 px or
more at the 17 dp Android 10 draws a status-bar icon at on the BV6600 (and at the 24 dp of the shade), measured on
its geometry, and that a rendering at 17 dp keeps the arrow a hole of its own.

Run from `tools/assets/`: python3 -m unittest discover -s tests
"""
from __future__ import annotations

import io
import sys
import unittest
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))
sys.path.insert(0, str(TOOL_ROOT / "notification"))

import build_glyph  # noqa: E402
from paperscrape_assets.raster import render_svg  # noqa: E402


def components(mask):
    seen = np.zeros_like(mask)
    h, w = mask.shape
    out = []
    for y in range(h):
        for x in range(w):
            if mask[y, x] and not seen[y, x]:
                q = deque([(y, x)])
                seen[y, x] = True
                n = 0
                while q:
                    a, b = q.popleft()
                    n += 1
                    for da, db in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        c, e = a + da, b + db
                        if 0 <= c < h and 0 <= e < w and mask[c, e] and not seen[c, e]:
                            seen[c, e] = True
                            q.append((c, e))
                out.append(n)
    return out


class GlyphTest(unittest.TestCase):

    def test_the_drawable_is_the_generator_output(self):
        self.assertTrue(build_glyph.OUT.exists())
        self.assertEqual(build_glyph.vector_xml(), build_glyph.OUT.read_text(),
                         "ic_stat_update.xml is not what notification/build_glyph.py writes: run it")

    def test_every_gap_and_band_is_two_pixels_or_more_in_the_status_bar(self):
        for what, units in build_glyph.narrowest().items():
            for size, px in build_glyph.PX_PER_UNIT.items():
                with self.subTest(what=what, size=size):
                    self.assertGreaterEqual(units * px, 2.0, f"{what}: {units * px:.2f} px in the {size}")

    def test_a_narrower_drawing_would_fail(self):
        """The measure has teeth: an arrow one unit lower leaves 1 unit of paper under its point, 1.4 px at 17 dp."""
        saved = build_glyph.ARROW
        try:
            build_glyph.ARROW = [(x, y + 1.0) for x, y in saved]
            low = build_glyph.narrowest()["paper between the arrow and the outline"]
        finally:
            build_glyph.ARROW = saved
        self.assertLess(low * build_glyph.PX_PER_UNIT["status bar (17 dp)"], 2.0)

    def test_the_arrow_is_one_hole_at_seventeen_dp(self):
        px = 34
        rgba = np.array(Image.open(io.BytesIO(render_svg(build_glyph.svg(px)).png_bytes)).convert("RGBA"))
        ink = rgba[:, :, 3] >= 128
        paper = components(ink)
        self.assertEqual(1, len([n for n in paper if n > 4]), "the paper is one piece")
        # The background inside the house's box, less what is outside the house: the hole is what is left.
        from PIL import ImageDraw
        outline = Image.new("L", (px, px), 0)
        ImageDraw.Draw(outline).polygon([(x * px / 24, y * px / 24) for x, y in build_glyph.HOUSE], fill=255)
        inside = np.array(outline) >= 128
        # One pixel in from the outline, so the antialiased rim of the outline is not taken for a hole.
        inside = inside & np.roll(inside, 1, 0) & np.roll(inside, -1, 0) & np.roll(inside, 1, 1) & np.roll(inside, -1, 1)
        hole = inside & (rgba[:, :, 3] < 64)
        holes = [n for n in components(hole) if n > 4]
        self.assertEqual(1, len(holes), "the arrow is one hole")
        self.assertGreater(holes[0], 60, "the arrow's hole is not lost at 17 dp")


if __name__ == "__main__":
    unittest.main()
