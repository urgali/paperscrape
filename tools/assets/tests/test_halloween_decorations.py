"""The Halloween decorations of the houses and the shops (v5.16): `halloween/build_halloween_deco.py` draws them and
declares them, from one geometry.

The maintainer chose the set C, cobwebs and bats, the bats in light violet (2026-10-21). These hold that the four SVG
sources, the four PNGs the pipeline renders of them and `HalloweenDecorationTable.kt` are the generator's output today;
that no piece of any colour of the cobweb, the bat and the jack-o'-lantern -- by day, and at night with its light laid over
it where the table blits it -- is under 2 px everywhere at the farthest house of the built-in streets on the BV6600 (read
from the Kotlin sources: the houses' far band, the house's metre and its family's units, the smallest size roll), on the
SVGs rendered without antialiasing; that the cobweb's threads are 2 px wide there (a thread across the diagonal holds no
upright square, so the rule above sees only where they meet); that the jack-o'-lantern's paper between two cuts is 2 px or
more, and its light lies inside its cuts and lights them whole; that the bat is the light violet only; and that each piece
hangs from, or stands on, the point the table measures it from.

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
sys.path.insert(0, str(TOOL_ROOT / "halloween"))

import build_halloween_deco as deco  # noqa: E402
from paperscrape_assets.raster import render_svg  # noqa: E402

PX = 3
ENGINE = TOOL_ROOT.parent.parent / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine"


def farthest_px_per_unit() -> float:
    """A piece unit of the farthest house of the built-in streets on the BV6600 (1 440 px tall), from the Kotlin sources:
    the houses' far band's near end (`SceneObjectCatalog`), the small house's metres over its family's units (the large
    house's are the same), the projection there and the smallest size roll."""
    space = (ENGINE / "SceneSpace.kt").read_text()
    catalog = (ENGINE / "SceneObject.kt").read_text()
    table = (ENGINE / "NeighbourhoodTable.kt").read_text()

    def c(name):
        return float(re.search(rf"const val {name} = ([0-9.]+)f", space).group(1))
    depth = float(re.search(r"generateSplitStaticCandidates\(SceneObjectType\.HOUSE, seed \+ \d+, ([0-9.]+)f\.\.", catalog).group(1))
    metres = float(re.search(r"HOUSE_SMALL\(([0-9.]+)f, [0-9.]+f\)", space).group(1))
    units = float(re.search(r"HOUSE_SMALL_FAMILY = BuildingFamily\(\s*([0-9.]+)f", table).group(1))
    top = c("HILL_LAYER_TOP_FRACTION") + c("HILL_LAYER_HEIGHT_FRACTION") * c("HILL_SOLID_TOP_DEPTH_FRACTION")
    y = top + depth * (c("OBJECT_BAND_BOTTOM_Y_FRACTION") - top)
    perspective = (y - c("HORIZON_Y_FRACTION")) / (c("REFERENCE_Y_FRACTION") - c("HORIZON_Y_FRACTION"))
    metre = c("PIXELS_PER_METRE_AT_REFERENCE") * 1440.0 / c("REFERENCE_SCREEN_HEIGHT_PX")
    return metres * metre / units * perspective * (1.0 - c("SIZE_VARIATION_SPREAD") / 2)


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


class HalloweenDecorationsTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.out, cls.table = deco.build()
        cls.kt = cls.out[deco.TABLE].decode()
        cls.t = {n: table_value(cls.kt, n) for n in ("WEB_X", "WEB_Y", "BAT_X", "BAT_Y", "PUMPKIN_X", "PUMPKIN_Y", "PUMPKIN_LIGHT_X",
                                                      "PUMPKIN_LIGHT_Y", "DECORATION_BAND_UNITS")}
        cls.crisp = {n: crisp(cls.out[deco.SVG_DIR / f"{n}.svg"].decode()) for n in deco.NAMES}
        day = cls.crisp["deco_jack_o_lantern"]
        cls.light = Image.new("RGBA", day.size, (0, 0, 0, 0))
        cls.light.alpha_composite(cls.crisp["deco_jack_o_lantern_light"], (int(round((cls.t["PUMPKIN_LIGHT_X"] - cls.t["PUMPKIN_X"]) * PX)),
                                                                           int(round((cls.t["PUMPKIN_LIGHT_Y"] - cls.t["PUMPKIN_Y"]) * PX))))
        cls.night = day.copy()
        cls.night.alpha_composite(cls.light)
        cls.px_per_unit = farthest_px_per_unit()

    def test_every_file_is_the_generator_output(self):
        for path, data in self.out.items():
            with self.subTest(file=path.name):
                self.assertTrue(path.exists(), f"{path} is missing")
                self.assertEqual(data, path.read_bytes(), f"{path.name} is not what halloween/build_halloween_deco.py writes: run it with --res")

    def test_the_farthest_house_is_the_generators(self):
        """The size the generator draws the bands for is the one the Kotlin sources give today: 0.812 px a piece unit."""
        self.assertAlmostEqual(farthest_px_per_unit(), deco.PX_PER_UNIT_FARTHEST, delta=0.001)
        self.assertGreaterEqual(deco.MIN_BAND * self.px_per_unit, 2.0)

    def test_no_colour_is_a_piece_under_two_pixels_at_the_farthest_house(self):
        """Every piece of one colour of each decoration -- the jack-o'-lantern by day and at night -- holds a square of 2 px
        at the farthest house somewhere: of `ceil(2 / pxPerUnit x 3)` px of the drawing. Pieces under 12 px are the corners
        where three papers meet."""
        k = math.ceil(2.0 / self.px_per_unit * PX)
        images = {"cobweb": self.crisp["deco_cobweb"], "bat": self.crisp["deco_bat"], "pumpkin by day": self.crisp["deco_jack_o_lantern"],
                  "pumpkin at night": self.night}
        for label, image in images.items():
            a = np.array(image).astype(np.int64)
            opaque = a[:, :, 3] == 255
            keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
            for key in np.unique(keys[opaque]):
                m = opaque & (keys == key)
                ok = squares(m, k)
                for pts in pieces(m):
                    if len(pts) < 12:
                        continue
                    with self.subTest(piece=label, colour="#%06X" % int(key), first=pts[0]):
                        self.assertTrue(any(ok[p] for p in pts), f"{len(pts)} px of #{int(key):06X} under 2 px everywhere")

    def test_the_cobwebs_threads_are_two_pixels_wide_at_the_farthest_house(self):
        """The threads are bands of one width, three of them straight and two arcs; the one across the diagonal holds no
        upright square of 2 px even when it is wider, so the rule above sees only where the threads meet: their width is
        held here, and measured on the drawing -- rendered without antialiasing at eight times the 3x of the others, each
        straight thread at 0.7 of its reach, and the outer arc at four of its points, across the band (along its normal):
        every one 2 px wide at the farthest house, to within a pixel of the render."""
        self.assertGreaterEqual(deco.WEB_THREAD * self.px_per_unit, 2.0)
        k = 8
        svg = self.out[deco.SVG_DIR / "deco_cobweb.svg"].decode()
        w = re.search(r'<svg[^>]* width="([0-9.]+)"', svg).group(1)
        h = re.search(r'<svg[^>]* height="([0-9.]+)"', svg).group(1)
        svg = re.sub(r'(<svg[^>]*?) width="[0-9.]+"', rf'\1 width="{float(w) * k:g}"', svg, count=1)
        svg = re.sub(r'(<svg[^>]*?) height="[0-9.]+"', rf'\1 height="{float(h) * k:g}"', svg, count=1)
        web = colour_mask(np.array(crisp(svg)), deco.WEB)
        scale = PX * k

        def across(point, normal):
            """The band's width through [point] along [normal], in units: the run of the web's pixels that holds the
            point (another thread the line crosses farther on is not this one's)."""
            def web_at(i):
                x = int(round((point[0] + i / scale * normal[0] - self.t["WEB_X"]) * scale))
                y = int(round((point[1] + i / scale * normal[1] - self.t["WEB_Y"]) * scale))
                return 0 <= y < web.shape[0] and 0 <= x < web.shape[1] and bool(web[y, x])
            self.assertTrue(web_at(0), f"no thread at {point}")
            lo = hi = 0
            while web_at(lo - 1):
                lo -= 1
            while web_at(hi + 1):
                hi += 1
            return (hi - lo + 1) / scale

        reach = deco.WEB_REACH
        diagonal = math.cos(math.radians(45))
        widths = {
            "along the eave": across((reach * 0.7, 0.0), (0.0, 1.0)),
            "down the wall": across((0.0, reach * 0.7), (1.0, 0.0)),
            "across the diagonal": across((reach * 0.7 * diagonal, reach * 0.7 * diagonal), (-diagonal, diagonal)),
        }
        rr = reach * 0.9
        arc = []
        for n in range(7):
            a = math.radians(n * 15.0)
            sag = 0.86 + 0.14 * abs(math.cos(math.radians(n * 15.0 * 4)))
            arc.append((rr * sag * math.cos(a), rr * sag * math.sin(a)))
        for n in (1, 2, 4, 5):
            tx, ty = arc[n + 1][0] - arc[n - 1][0], arc[n + 1][1] - arc[n - 1][1]
            length = math.hypot(tx, ty)
            widths[f"the outer arc at {n * 15} degrees"] = across(arc[n], (-ty / length, tx / length))
        need = 2.0 / self.px_per_unit - 1 / scale
        for where, units in widths.items():
            with self.subTest(where=where):
                self.assertGreaterEqual(units, need, f"the thread {where} is {units * self.px_per_unit:.2f} px")

    def test_the_paper_between_two_cuts_is_two_pixels_or_more(self):
        """Between the jack-o'-lantern's two eyes, and between an eye and its grin, the pumpkin's paper is 2 px at the
        farthest house or more (the paper is one piece with the pumpkin: the moon's rule)."""
        side = math.ceil(2.0 / self.px_per_unit * PX)
        cut = colour_mask(np.array(self.crisp["deco_jack_o_lantern"]), deco.CUT)
        cuts = [np.array(p) for p in pieces(cut) if len(p) >= 12]
        self.assertEqual(3, len(cuts), "two eyes and the grin")
        for i in range(len(cuts)):
            for j in range(i + 1, len(cuts)):
                nearest = min(float(np.sqrt(((cuts[i][k:k + 256, None, :] - cuts[j][None, :, :]) ** 2).sum(axis=2)).min())
                              for k in range(0, len(cuts[i]), 256))
                with self.subTest(cuts=(tuple(cuts[i][0]), tuple(cuts[j][0]))):
                    self.assertGreaterEqual(nearest - 1, side, f"{nearest - 1:.1f} px of paper between two cuts")

    def test_the_light_lies_in_the_pumpkins_cuts_and_lights_them_whole(self):
        cut = colour_mask(np.array(self.crisp["deco_jack_o_lantern"]), deco.CUT)
        lit = np.array(self.light)[:, :, 3] == 255
        self.assertGreater(int(lit.sum()), 50, "no light found")
        self.assertEqual(0, int((lit & ~grow(cut, 1)).sum()), "the light falls outside the pumpkin's cuts")
        self.assertEqual(0, int(colour_mask(np.array(self.night), deco.CUT).sum()), "a cut is left dark at night")

    def test_the_bat_is_the_light_violet_only(self):
        """The bat is cut from one paper, its light violet `#8A5BB8` -- every fill of its source, every solid pixel of its
        PNG --: the dark bat the round first showed does not read on the walls at night."""
        self.assertEqual(deco.BAT, "#8A5BB8")
        fills = set(re.findall(r'fill="(#[0-9A-Fa-f]{6})"', self.out[deco.SVG_DIR / "deco_bat.svg"].decode()))
        self.assertEqual({"#8A5BB8"}, fills)
        a = np.array(Image.open(io.BytesIO(self.out[deco.RES / "deco_bat.png"])).convert("RGBA")).astype(int)
        solid = a[:, :, 3] == 255
        self.assertGreater(int(solid.sum()), 100)
        self.assertEqual(0, int(np.abs(a[solid][:, :3] - np.array([0x8A, 0x5B, 0xB8])).max()), "the bat carries a colour of another paper")

    def test_each_piece_hangs_from_its_point(self):
        """The cobweb's corner, the point the bat hangs by and the jack-o'-lantern's bottom's middle are where the table
        measures each from: the web's paper at its corner, the bat's at its feet, and the pumpkin standing on its point --
        its lowest row there, centred on it."""
        def pixel(name, ox, oy, x, y):
            a = np.array(self.crisp[name])
            return a[int(math.floor((y - oy) * PX)), int(math.floor((x - ox) * PX))]
        self.assertEqual(255, int(pixel("deco_cobweb", self.t["WEB_X"], self.t["WEB_Y"], 0.1, 0.1)[3]), "the web is not in its corner")
        self.assertEqual(255, int(pixel("deco_bat", self.t["BAT_X"], self.t["BAT_Y"], 0.0, 0.1)[3]), "the bat does not hang from its point")
        a = np.array(self.crisp["deco_jack_o_lantern"])
        rows = np.nonzero((a[:, :, 3] == 255).any(axis=1))[0]
        bottom = self.t["PUMPKIN_Y"] + (int(rows.max()) + 1) / PX
        self.assertAlmostEqual(0.0, bottom, delta=1 / PX + 0.01, msg="the pumpkin does not stand on its point")
        cols = np.nonzero((a[:, :, 3] == 255).any(axis=0))[0]
        middle = self.t["PUMPKIN_X"] + (int(cols.min()) + int(cols.max()) + 1) / 2 / PX
        self.assertAlmostEqual(0.0, middle, delta=0.5, msg="the pumpkin is not centred on its point")

    def test_the_narrowest_band_is_the_drawings(self):
        """DECORATION_BAND_UNITS, which the gallery card sizes its copies on, is the band the pieces are drawn at -- the cobweb's
        threads, the bat's paper, the jack-o'-lantern's cuts -- and the drawing's: the narrowest piece of the three, the
        jack-o'-lantern by day and at night, holds a square within a pixel and a half of DECORATION_BAND_UNITS x 3, and the cobweb's
        thread along the eave is that many pixels tall within a pixel and a half."""
        band = self.t["DECORATION_BAND_UNITS"]
        self.assertEqual(deco.MIN_BAND, band)
        self.assertEqual(deco.WEB_THREAD, band)
        images = (self.crisp["deco_cobweb"], self.crisp["deco_bat"], self.crisp["deco_jack_o_lantern"], self.night)
        narrowest = min(narrowest_piece(image) for image in images)
        self.assertLessEqual(abs(narrowest - band * PX), 1.5, f"the narrowest piece holds {narrowest} px, the band is {band * PX:.1f}")
        web = colour_mask(np.array(self.crisp["deco_cobweb"]), deco.WEB)
        x = int(round((deco.WEB_REACH * 0.7 - self.t["WEB_X"]) * PX))
        rows = np.nonzero(web[:, x])[0]
        run = int(np.argmax(np.diff(rows) > 1)) + 1 if (np.diff(rows) > 1).any() else len(rows)
        self.assertLessEqual(abs(run - band * PX), 1.5, f"the eave's thread is {run} px tall, the band is {band * PX:.1f}")

    def test_every_sprite_is_on_the_grid(self):
        # The PNGs as shipped, read off the disk: the generator writes them on the grid by construction, a hand-edited or
        # re-exported one need not be.
        for name in deco.NAMES:
            image = Image.open(deco.RES / f"{name}.png")
            with self.subTest(sprite=name):
                self.assertEqual((0, 0), (image.width % PX, image.height % PX))


if __name__ == "__main__":
    unittest.main()
