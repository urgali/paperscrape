"""The Halloween scarecrow (v5.16): `scarecrow/build_scarecrow.py` draws it and declares it, from one geometry.

The maintainer chose the scarecrow B, the Halloween scarecrow, and its size, 2.4 m from the pole's foot to the hat's tip
(2026-10-21). These hold that the two SVG sources, the two PNGs the pipeline renders of them and `ScarecrowTable.kt` are
the generator's output today; that no piece of any colour of the scarecrow -- by day, and at night with its light laid over
it where the table blits it -- is under 2 px everywhere where it stands farthest on the BV6600 (a snowman's farthest place,
read from `SceneObjectCatalog`, at the smallest size roll), on the SVGs rendered without antialiasing; that the face's paper
between two cuts is 2 px or more there too; that the light lies inside the face's cuts and lights them whole; that the
table's origin is the registry's anchor -- the pole's foot is the bottom centre of what is drawn, `CONTENT_BOTTOM_CENTRE` --;
and that the height the renderer sizes it on is the drawing's, the pole's foot to the hat's tip.

Run from `tools/assets/`: python3 -m unittest discover -s tests
"""
from __future__ import annotations

import io
import json
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
sys.path.insert(0, str(TOOL_ROOT / "scarecrow"))

import build_scarecrow  # noqa: E402
from paperscrape_assets.raster import render_svg  # noqa: E402

PX = 3
ENGINE = TOOL_ROOT.parent.parent / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine"


def farthest_px_tall() -> float:
    """How tall a scarecrow is drawn where it stands farthest on the BV6600 (1 440 px tall), from the Kotlin sources: at
    the snowmen's nearest-half band's far end (`seasonalDecorationCandidates`), the object band's projection
    (`SceneSpace.groundYFraction`, `perspectiveScaleAt`) and the smallest size roll (`1 - SIZE_VARIATION_SPREAD / 2`)."""
    space = (ENGINE / "SceneSpace.kt").read_text()
    catalog = (ENGINE / "SceneObject.kt").read_text()

    def c(name):
        return float(re.search(rf"const val {name} = ([0-9.]+)f", space).group(1))
    depth = float(re.search(r"generateStaticCandidates\(SceneObjectType\.SNOWMAN, seed \+ \d+, ([0-9.]+)f\.\.", catalog).group(1))
    top = c("HILL_LAYER_TOP_FRACTION") + c("HILL_LAYER_HEIGHT_FRACTION") * c("HILL_SOLID_TOP_DEPTH_FRACTION")
    y = top + depth * (c("OBJECT_BAND_BOTTOM_Y_FRACTION") - top)
    perspective = (y - c("HORIZON_Y_FRACTION")) / (c("REFERENCE_Y_FRACTION") - c("HORIZON_Y_FRACTION"))
    metre = c("PIXELS_PER_METRE_AT_REFERENCE") * 1440.0 / c("REFERENCE_SCREEN_HEIGHT_PX")
    return build_scarecrow.METRES_TALL * metre * perspective * (1.0 - c("SIZE_VARIATION_SPREAD") / 2)


def crisp(svg: str) -> Image.Image:
    return Image.open(io.BytesIO(render_svg(svg.replace("<svg ", '<svg shape-rendering="crispEdges" ', 1)).png_bytes)).convert("RGBA")


def pieces(m):
    seen = np.zeros_like(m)
    H, W = m.shape
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
                if 0 <= c < H and 0 <= d < W and m[c, d] and not seen[c, d]:
                    seen[c, d] = True
                    q.append((c, d))
        out.append(pts)
    return out


def squares(m, k):
    """True at the top-left corner of every k x k square lying wholly inside [m]."""
    H, W = m.shape
    out = np.zeros((H, W), dtype=bool)
    if H < k or W < k:
        return out
    c = np.zeros((H + 1, W + 1), dtype=np.int64)
    c[1:, 1:] = np.cumsum(np.cumsum(m.astype(np.int64), 0), 1)
    out[:H - k + 1, :W - k + 1] = (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) == k * k
    return out


def largest_square(m, pts):
    """The side of the largest upright square the piece [pts] of [m] holds, in px."""
    lo, hi = 0, 64
    while lo < hi:
        k = (lo + hi + 1) // 2
        ok = squares(m, k)
        if any(ok[p] for p in pts):
            lo = k
        else:
            hi = k - 1
    return lo


def narrowest_piece(image, smallest=12):
    """The smallest of the largest squares the pieces of one colour of [image] hold (pieces under [smallest] px left out)."""
    a = np.array(image).astype(np.int64)
    opaque = a[:, :, 3] == 255
    keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
    out = 1 << 30
    for key in np.unique(keys[opaque]):
        m = opaque & (keys == key)
        for pts in pieces(m):
            if len(pts) >= smallest:
                out = min(out, largest_square(m, pts))
    return out


def grow(m, r):
    out = m.copy()
    for _ in range(r):
        p = np.pad(out, 1, constant_values=False)
        out = (p[1:-1, 1:-1] | p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:])
    return out


def colour_mask(a, colour):
    rgb = np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)])
    return (np.abs(a[:, :, :3].astype(int) - rgb).sum(axis=2) == 0) & (a[:, :, 3] == 255)


def table_value(text, name):
    return float(re.search(rf"const val {name} = (-?[0-9.]+)f", text).group(1))


class ScarecrowTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.out, cls.table = build_scarecrow.build()
        cls.kt = cls.out[build_scarecrow.TABLE].decode()
        cls.t = {n: table_value(cls.kt, n) for n in ("X", "Y", "LIGHT_X", "LIGHT_Y", "UNITS_TALL", "METRES_TALL", "SCARECROW_BAND_UNITS")}
        cls.day = crisp(cls.out[build_scarecrow.SVG_DIR / "scarecrow.svg"].decode())
        light = crisp(cls.out[build_scarecrow.SVG_DIR / "scarecrow_light.svg"].decode())
        cls.light = Image.new("RGBA", cls.day.size, (0, 0, 0, 0))
        cls.light.alpha_composite(light, (int(round((cls.t["LIGHT_X"] - cls.t["X"]) * PX)), int(round((cls.t["LIGHT_Y"] - cls.t["Y"]) * PX))))
        cls.night = cls.day.copy()
        cls.night.alpha_composite(cls.light)
        cls.px_per_unit = farthest_px_tall() / cls.t["UNITS_TALL"]

    def test_every_file_is_the_generator_output(self):
        for path, data in self.out.items():
            with self.subTest(file=path.name):
                self.assertTrue(path.exists(), f"{path} is missing")
                self.assertEqual(data, path.read_bytes(), f"{path.name} is not what scarecrow/build_scarecrow.py writes: run it with --res")

    def test_the_farthest_place_is_the_generators(self):
        """The size the generator thickens the drawing for is the one the Kotlin sources give today."""
        self.assertAlmostEqual(farthest_px_tall(), build_scarecrow.FARTHEST_PX_TALL, delta=0.01)

    def test_no_colour_is_a_piece_under_two_pixels_where_it_stands_farthest(self):
        """By day and at night, every piece of one colour holds a square of 2 px at the farthest place somewhere: of
        `ceil(2 / pxPerUnit x 3)` px of the drawing. Pieces under 12 px are the corners where three papers meet."""
        k = math.ceil(2.0 / self.px_per_unit * PX)
        for label, image in (("day", self.day), ("night", self.night)):
            a = np.array(image).astype(np.int64)
            opaque = a[:, :, 3] == 255
            keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
            for key in np.unique(keys[opaque]):
                m = opaque & (keys == key)
                ok = squares(m, k)
                for pts in pieces(m):
                    if len(pts) < 12:
                        continue
                    with self.subTest(at=label, colour="#%06X" % int(key), first=pts[0]):
                        self.assertTrue(any(ok[p] for p in pts), f"{len(pts)} px of #{int(key):06X} under 2 px everywhere")

    def test_the_paper_between_two_cuts_is_two_pixels_or_more_where_it_stands_farthest(self):
        """Between the two eyes, and between an eye and the grin, the pumpkin's paper is 2 px at the farthest place or more:
        the nearest pixels of two cuts are farther apart than that (the paper is one piece with the head, so the rule above
        cannot see it: the moon's rule)."""
        side = math.ceil(2.0 / self.px_per_unit * PX)
        cut = colour_mask(np.array(self.day), build_scarecrow.CUT)
        cuts = [np.array(p) for p in pieces(cut) if len(p) >= 12]
        self.assertEqual(3, len(cuts), "two eyes and the grin")
        for i in range(len(cuts)):
            for j in range(i + 1, len(cuts)):
                nearest = min(float(np.sqrt(((cuts[i][k:k + 256, None, :] - cuts[j][None, :, :]) ** 2).sum(axis=2)).min())
                              for k in range(0, len(cuts[i]), 256))
                with self.subTest(cuts=(tuple(cuts[i][0]), tuple(cuts[j][0]))):
                    self.assertGreaterEqual(nearest - 1, side, f"{nearest - 1:.1f} px of paper between two cuts")

    def test_the_light_lies_in_the_face_and_lights_it_whole(self):
        """The light, where the table blits it, falls on the face's cuts only -- one pixel of it may fall either side of a
        cut's edge -- and leaves no cut unlit: at night no dark of a cut shows."""
        cut = colour_mask(np.array(self.day), build_scarecrow.CUT)
        lit = np.array(self.light)[:, :, 3] == 255
        self.assertGreater(int(lit.sum()), 200, "no light found")
        self.assertEqual(0, int((lit & ~grow(cut, 1)).sum()), "the light falls outside the face's cuts")
        self.assertEqual(0, int(colour_mask(np.array(self.night), build_scarecrow.CUT).sum()), "a cut is left dark at night")

    def test_the_pole_foot_is_the_bottom_centre_and_the_registrys_anchor(self):
        """The canvas is centred on the pole and ends at the ground, so the table's origin is minus the anchor the registry
        declares (`CONTENT_BOTTOM_CENTRE`, in px) in units: the renderer stands the scarecrow on its pole's foot."""
        w, h = self.day.size
        self.assertEqual((-w / 2 / PX, -h / PX), (self.t["X"], self.t["Y"]))
        registry = json.loads((TOOL_ROOT / "sources" / "sprites.json").read_text())
        entry = next(s for s in registry["sprites"] if s["name"] == "scarecrow")
        self.assertEqual("CONTENT_BOTTOM_CENTRE", entry["anchorRule"])
        self.assertEqual((-entry["anchor"][0] / PX, -entry["anchor"][1] / PX), (self.t["X"], self.t["Y"]))
        bottom = np.nonzero(np.array(self.day)[-1, :, 3] == 255)[0]
        self.assertTrue(len(bottom) > 0, "nothing stands on the ground")
        self.assertAlmostEqual(w / 2, (bottom.min() + bottom.max() + 1) / 2, delta=1.0, msg="the pole does not stand at the middle")

    def test_the_height_is_the_drawings(self):
        """UNITS_TALL, which the renderer sizes the scarecrow on, is the hat's tip above the pole's foot as drawn; and it
        reads as 2.4 m."""
        hat = colour_mask(np.array(self.day), build_scarecrow.HAT)
        tip = self.t["Y"] + int(np.nonzero(hat.any(axis=1))[0].min()) / PX
        self.assertAlmostEqual(-self.t["UNITS_TALL"], tip, delta=1 / PX + 0.01)
        self.assertEqual(2.4, self.t["METRES_TALL"])

    def test_the_narrowest_band_is_the_drawings(self):
        """SCARECROW_BAND_UNITS, the narrowest band the table declares, is the band every part is drawn in multiples of, and the
        drawing's: its narrowest piece, by day and at night, holds a square within a pixel and a half of SCARECROW_BAND_UNITS x 3;
        and it is 2 px or more where the scarecrow stands farthest."""
        band = self.t["SCARECROW_BAND_UNITS"]
        self.assertEqual(build_scarecrow.M_UNITS, band)
        self.assertGreaterEqual(band * self.px_per_unit, 2.0)
        for label, image in (("day", self.day), ("night", self.night)):
            narrowest = narrowest_piece(image)
            with self.subTest(at=label):
                self.assertLessEqual(abs(narrowest - band * PX), 1.5, f"the narrowest piece holds {narrowest} px, the band is {band * PX:.1f}")

    def test_every_sprite_is_on_the_grid(self):
        # The PNGs as shipped, read off the disk: the generator writes them on the grid by construction, a hand-edited or
        # re-exported one need not be.
        for name in build_scarecrow.NAMES:
            image = Image.open(build_scarecrow.RES / f"{name}.png")
            with self.subTest(sprite=name):
                self.assertEqual((0, 0), (image.width % PX, image.height % PX))


if __name__ == "__main__":
    unittest.main()
