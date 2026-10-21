"""Santa's sleigh (v5.15): `sleigh/build_sleigh.py` draws it and declares it, from one geometry.

The maintainer chose the sleigh B with its side raised (2026-10-19) and a size in proportion to the people. These hold
that every file the generator writes -- the five SVG sources, the five PNGs the pipeline renders of them and
`SantaSleighTable.kt` -- is its output today; that no piece of any colour of the group as the renderer draws it -- its
pieces and its cords, in the order `SantaSleighDrawer.draw` draws them, at every moment of a stride (Rudolph in each of his
four poses, the second reindeer half a stride behind, both bobbing, the sleigh swaying) -- is under 2 px everywhere at the
BV6600's size: each holds a square of 2 px somewhere (until v5.15C2 the composition left the cords out, and the reins,
drawn over everything, cut 62 pieces under 2 px); that the buckle's four bars and its hole are 2 px or more where they
show, not only somewhere (its lower bar was 1.66 px until v5.15C2); that the cords are 2 px or more; that the points the
renderer hangs the cords on -- the collars, their fronts, the girths and Santa's fist --, the nose and the gifts from lie
on the paper they belong to, and that the traces reach the sleigh (their end, a quarter to half a unit off the sleigh's
edge, is within the trace's round end of it: since v5.15D); that Rudolph's red nose covers his brown one; and that the
Santa the size is measured on is the Santa drawn.

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
sys.path.insert(0, str(TOOL_ROOT / "sleigh"))

import build_sleigh  # noqa: E402
from paperscrape_assets.raster import render_svg  # noqa: E402

PX = 3

# How far the reindeer and the sleigh bob as they fly, read from the renderer that moves them.
DRAWER = (TOOL_ROOT.parent.parent / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/SantaSleighDrawer.kt").read_text()
SANTA_DEER_BOB_UNITS = float(re.search(r"const val SANTA_DEER_BOB_UNITS = ([0-9.]+)f", DRAWER).group(1))
SANTA_SLEIGH_BOB_UNITS = float(re.search(r"const val SANTA_SLEIGH_BOB_UNITS = ([0-9.]+)f", DRAWER).group(1))
assert re.search(r"val DEER_PHASE = floatArrayOf\(0f, 2f\)", DRAWER), "the second reindeer is no longer half a stride behind"


def drawing_order() -> list[str]:
    """The order `SantaSleighDrawer.draw` lays the group down in, read off its body: "traces", "sleigh", "reins",
    "reindeer", "nose" -- what the composition below follows, so it composes the group as the renderer draws it."""
    body = DRAWER[DRAWER.index("fun draw(canvas: SceneCanvas"):]
    body = body[:body.index("\n    }\n")]
    marks = {
        "traces": "canvas, x, y, dir, sc, trace,",
        "sleigh": "SantaSleighTable.SLEIGH_RES",
        "reins": "line(canvas, x, y, dir, sc, rein, hx, hy,",
        "reindeer": "drawDeer(canvas, x, y, dir, sc, a,",
        "nose": "sprites.drawDisc(",
    }
    found = {k: body.find(m) for k, m in marks.items()}
    missing = [k for k, i in found.items() if i < 0]
    assert not missing, f"SantaSleighDrawer.draw no longer draws {missing} as this test reads it"
    return sorted(found, key=found.get)


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


def table_value(text, name):
    m = re.search(rf"const val {name} = (-?[0-9.]+)f", text)
    return float(m.group(1))


def table_array(text, name):
    m = re.search(rf"val {name} = floatArrayOf\(([^)]*)\)", text)
    return [float(v) for v in m.group(1).replace("f", "").split(",")]


class SleighTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.out, cls.table = build_sleigh.build()
        cls.kt = cls.out[build_sleigh.TABLE].decode()
        cls.images = {n: Image.open(io.BytesIO(cls.out[build_sleigh.RES / f"{n}.png"])).convert("RGBA") for n in build_sleigh.NAMES}
        # The same SVGs rendered without antialiasing: every pixel is one paper's, so a piece's width is its own.
        cls.crisp = {
            n: Image.open(io.BytesIO(render_svg(cls.out[build_sleigh.SVG_DIR / f"{n}.svg"].decode().replace(
                "<svg ", '<svg shape-rendering="crispEdges" ', 1)).png_bytes)).convert("RGBA")
            for n in build_sleigh.NAMES
        }

    def test_every_file_is_the_generator_output(self):
        for path, data in self.out.items():
            with self.subTest(file=path.name):
                self.assertTrue(path.exists(), f"{path} is missing")
                self.assertEqual(data, path.read_bytes(), f"{path.name} is not what sleigh/build_sleigh.py writes: run it with --res")

    def composed(self, pose: int, crisp: bool = False, lead_bob: float = 0.0, sleigh_bob: float = 0.0,
                 only: tuple[str, ...] | None = None) -> Image.Image:
        """The group as the renderer draws it at 3 px a unit (`SantaSleighDrawer.draw`, in its order, [drawing_order]):
        the traces; the sleigh swaying by [sleigh_bob] units; the reins from Santa's fist to each collar's front; the
        reindeer -- Rudolph in pose [pose], the second two poses on, half a stride behind (`DEER_PHASE`), each bobbing with
        its stride, the second against Rudolph ([lead_bob] units, the second's the opposite) -- and Rudolph's nose; each
        cord a line [`SANTA_REIN_UNITS`/`SANTA_TRACE_UNITS`] wide with round ends, as the renderer's paints. With [crisp],
        from the renders without antialiasing (the cords are drawn without it too); [only], the steps to draw."""
        images = self.crisp if crisp else self.images
        left, right = self.table["left"], self.table["right"]
        top, bottom = -60, 50
        canvas = Image.new("RGBA", (int((right - left + 4) * PX), (bottom - top) * PX), (0, 0, 0, 0))

        def at(ux, uy):
            return int(round((ux - left + 2) * PX)), int(round((uy - top) * PX))

        def atf(ux, uy):
            return (ux - left + 2) * PX, (uy - top) * PX

        def cord(a, b, units, colour):
            layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
            draw = ImageDraw.Draw(layer)
            (x0, y0), (x1, y1) = atf(*a), atf(*b)
            r = units * PX / 2
            draw.line([(x0, y0), (x1, y1)], fill=colour, width=int(round(units * PX)))
            for x, y in ((x0, y0), (x1, y1)):
                draw.ellipse((x - r, y - r, x + r, y + r), fill=colour)
            canvas.alpha_composite(layer)

        lead, near = pose, (pose + 2) % 4
        L, N = self.table["poses"][lead], self.table["poses"][near]
        lx, ly = build_sleigh.LEAD_DEER[0], build_sleigh.LEAD_DEER[1] + lead_bob
        nx0, ny0 = build_sleigh.NEAR_DEER[0], build_sleigh.NEAR_DEER[1] - lead_bob
        rein, trace = table_value(self.kt, "SANTA_REIN_UNITS"), table_value(self.kt, "SANTA_TRACE_UNITS")
        hand = (self.table["hand"][0], self.table["hand"][1] + sleigh_bob)

        def traces():
            cord((lx + L["girth"][0], ly + L["girth"][1]), (nx0 + N["collar"][0], ny0 + N["collar"][1]), trace, build_sleigh.TRACE_COLOUR)
            cord((nx0 + N["girth"][0], ny0 + N["girth"][1]), (self.table["front"][0], self.table["front"][1] + sleigh_bob), trace, build_sleigh.TRACE_COLOUR)

        def reins():
            cord(hand, (lx + L["rein"][0], ly + L["rein"][1]), rein, build_sleigh.REIN_COLOUR)
            cord(hand, (nx0 + N["rein"][0], ny0 + N["rein"][1]), rein, build_sleigh.REIN_COLOUR)

        def reindeer():
            for (dx, dy), p, pz in (((lx, ly), L, lead), ((nx0, ny0), N, near)):
                canvas.alpha_composite(images[f"santa_reindeer_{pz}"], at(dx + p["ox"], dy + p["oy"]))

        def nose():
            x, y = at(lx + L["nose"][0], ly + L["nose"][1])
            r = build_sleigh.RUDOLPH_NOSE_RADIUS * PX
            disc = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
            ImageDraw.Draw(disc).ellipse((x - r, y - r, x + r, y + r), fill=build_sleigh.RUDOLPH_NOSE)
            canvas.alpha_composite(disc)

        def sleigh():
            sl = self.table["sleigh"]
            canvas.alpha_composite(images["santa_sleigh"], at(sl["ox"], sl["oy"] + sleigh_bob))

        steps = dict(traces=traces, reins=reins, reindeer=reindeer, nose=nose, sleigh=sleigh)
        for step in drawing_order():
            if only is None or step in only:
                steps[step]()
        return canvas

    @staticmethod
    def strides():
        """Every moment of a stride the test looks at: Rudolph's pose, his bob through the quarter of the stride that pose
        lasts (`SantaSleighDrawer`: four poses a stride, one bob a stride of `SANTA_DEER_BOB_UNITS`), and the sleigh's sway
        at both ends of its own (`SANTA_SLEIGH_BOB_UNITS`), which runs at a rate of its own."""
        for pose in range(4):
            for k in range(5):
                theta = (pose + k / 4) * math.pi / 2
                for sleigh_bob in (-SANTA_SLEIGH_BOB_UNITS, SANTA_SLEIGH_BOB_UNITS):
                    yield pose, round(SANTA_DEER_BOB_UNITS * math.sin(theta), 3), sleigh_bob

    def test_no_colour_is_a_piece_under_two_pixels_on_the_bv6600(self):
        """The group is drawn at `px_per_unit_on_the_bv6600` px a unit there, so 2 px are 2 / that units, `side` px of the
        drawing: a piece of one colour that does not survive an erosion leaving a square of that side is under 2 px
        everywhere. Measured on the group as the renderer draws it, cords included ([composed]), from the SVGs rendered
        without antialiasing, where every pixel is one paper's. Pieces under 12 px are the corners where three papers
        meet."""
        side = 2.0 / build_sleigh.px_per_unit_on_the_bv6600() * PX
        r = math.ceil((math.ceil(side) - 1) / 2)
        for pose, lead_bob, sleigh_bob in self.strides():
            a = np.array(self.composed(pose, crisp=True, lead_bob=lead_bob, sleigh_bob=sleigh_bob)).astype(int)
            opaque = a[:, :, 3] == 255
            keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
            for k in np.unique(keys[opaque]):
                c = (int(k) >> 16, (int(k) >> 8) & 255, int(k) & 255)
                m = opaque & (keys == k)
                er = erode(m, r)
                for pts in pieces(m):
                    if len(pts) < 12:
                        continue
                    with self.subTest(pose=pose, bob=lead_bob, sway=sleigh_bob, colour="#%02X%02X%02X" % c, at=pts[0]):
                        self.assertTrue(any(er[y, x] for y, x in pts), f"{len(pts)} px of #{'%02X%02X%02X' % c} under {side:.1f} px everywhere")

    def test_the_group_is_composed_in_the_renderers_order_with_its_cords(self):
        """The composition above is the renderer's: the traces behind everything, the reins over the sleigh and behind
        the reindeer (v5.15C2; until then the reins were drawn last, over the details they crossed), and the cords are in
        it -- a composition without them passed while the phone showed 62 pieces under 2 px."""
        self.assertEqual(["traces", "sleigh", "reins", "reindeer", "nose"], drawing_order())
        bare = np.array(self.composed(0, crisp=True, only=("sleigh", "reindeer", "nose")))
        whole = np.array(self.composed(0, crisp=True))
        for colour in (build_sleigh.REIN_COLOUR, build_sleigh.TRACE_COLOUR):
            rgb = np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)])
            with self.subTest(cord=colour):
                self.assertEqual(0, int((np.abs(bare[:, :, :3] - rgb).sum(axis=2) == 0).sum()))
                self.assertGreater(int(((np.abs(whole[:, :, :3] - rgb).sum(axis=2) == 0) & (whole[:, :, 3] == 255)).sum()), 300)

    def test_the_buckle_is_two_pixels_or_more_in_every_band_where_it_shows(self):
        """The gold frame round the black hole, measured band by band on the sleigh as drawn (crisp, the rim over it):
        the top bar and the bottom bar above and below the hole along its columns, the side bars beside it along its rows,
        and the hole itself -- each 2 px on the BV6600 or more, everywhere it shows. The rule above holds a piece 2 px
        *somewhere*, which a thin bar joined to the frame's thick sides passed (the lower bar, 1.66 px until v5.15C2)."""
        need = math.ceil(2.0 / build_sleigh.px_per_unit_on_the_bv6600() * PX)
        a = np.array(self.crisp["santa_sleigh"]).astype(int)
        def is_(colour):
            rgb = np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)])
            return (np.abs(a[:, :, :3] - rgb).sum(axis=2) == 0) & (a[:, :, 3] == 255)
        gold, hole = is_(build_sleigh.GOLD), is_(build_sleigh.BELT)
        # The hole: the black between gold on its left and its right, at the buckle's middle (Santa at x 32 of the sleigh).
        sl = self.table["sleigh"]
        mid_x = int(round((32.0 + build_sleigh.SLEIGH_AT[0] - sl["ox"]) * PX))
        rows = [y for y in range(a.shape[0]) if hole[y, mid_x] and gold[y, :mid_x].any() and gold[y, mid_x:].any()
                and gold[y, mid_x - 3 * PX:mid_x].any() and gold[y, mid_x:mid_x + 3 * PX].any()]
        self.assertTrue(rows, "no buckle hole found at Santa's middle")
        top, bottom = min(rows), max(rows)
        cols = [x for x in range(mid_x - 3 * PX, mid_x + 3 * PX) if hole[(top + bottom) // 2, x]]
        self.assertGreaterEqual(bottom - top + 1, need, "the hole")
        self.assertGreaterEqual(len(cols), need, "the hole across")
        for x in cols:
            up = 0
            while gold[top - 1 - up, x]:
                up += 1
            down = 0
            while gold[bottom + 1 + down, x]:
                down += 1
            with self.subTest(column=x):
                self.assertGreaterEqual(up, need, "the top bar")
                self.assertGreaterEqual(down, need, "the bottom bar, above the sleigh's rim")
        for y in range(top, bottom + 1):
            left = 0
            while gold[y, min(cols) - 1 - left]:
                left += 1
            right = 0
            while gold[y, max(cols) + 1 + right]:
                right += 1
            with self.subTest(row=y):
                self.assertGreaterEqual(left, need, "the left bar")
                self.assertGreaterEqual(right, need, "the right bar")

    def test_the_cords_are_two_pixels_or_more_on_the_bv6600(self):
        sc = build_sleigh.px_per_unit_on_the_bv6600()
        for name in ("SANTA_REIN_UNITS", "SANTA_TRACE_UNITS"):
            with self.subTest(cord=name):
                self.assertGreaterEqual(table_value(self.kt, name) * sc, 2.0)

    def test_the_cords_and_the_nose_hang_on_the_reindeer(self):
        for pose in range(4):
            a = np.array(self.images[f"santa_reindeer_{pose}"])[:, :, 3]
            p = self.table["poses"][pose]
            for what in ("collar", "rein", "girth", "nose"):
                x, y = p[what]
                with self.subTest(pose=pose, point=what):
                    self.assertGreater(a[int(round((y - p["oy"]) * PX)), int(round((x - p["ox"]) * PX))], 250)

    def test_the_traces_reach_the_sleigh(self):
        """Where the traces end on the sleigh ([FRONT], `SantaSleighDrawer` draws them there before the sleigh): not on the
        sleigh's paper -- a quarter to half a unit off its edge --, but no farther from it than the trace's round end
        reaches, half the trace's width, so no sky shows between a trace and the sleigh (v5.15D: no test looked at it)."""
        a = np.array(self.images["santa_sleigh"])[:, :, 3]
        sl = self.table["sleigh"]
        x, y = self.table["front"]
        px, py = (x - sl["ox"]) * PX, (y - sl["oy"]) * PX
        ys, xs = np.nonzero(a > 250)
        nearest = float(np.hypot(xs - px, ys - py).min()) / PX
        self.assertLessEqual(nearest, table_value(self.kt, "SANTA_TRACE_UNITS") / 2,
                             f"the traces end {nearest:.2f} units from the sleigh's paper")

    def test_rudolphs_nose_covers_the_reindeers_own(self):
        """Where the renderer puts it, in every pose: not one pixel of the brown nose is left around Rudolph's red one
        (until v5.15C2 only the radii were compared, so a nose drawn on the eye passed)."""
        self.assertGreaterEqual(build_sleigh.RUDOLPH_NOSE_RADIUS, build_sleigh.DEER_NOSE_RADIUS + 0.3)
        brown = np.array([int(build_sleigh.DEER["nose"][i:i + 2], 16) for i in (1, 3, 5)])
        for pose in range(4):
            with self.subTest(pose=pose):
                # Left of Rudolph's middle, where his head is and the second reindeer's is not.
                before = np.array(self.composed(pose, crisp=True, only=("reindeer",)))
                after = np.array(self.composed(pose, crisp=True, only=("reindeer", "nose")))
                lead = slice(0, int((build_sleigh.LEAD_DEER[0] - self.table["left"] + 2) * PX))
                def count(img):
                    return int(((np.abs(img[:, lead, :3] - brown).sum(axis=2) == 0) & (img[:, lead, 3] == 255)).sum())
                self.assertGreater(count(before), 20, "Rudolph has no brown nose to cover")
                self.assertEqual(0, count(after), "the red nose leaves some of the brown one")

    def test_the_hand_holds_the_reins_and_the_gifts_leave_the_presents(self):
        a = np.array(self.images["santa_sleigh"])[:, :, 3]
        sl = self.table["sleigh"]
        for what in ("hand", "sack"):
            x, y = self.table[what]
            with self.subTest(point=what):
                self.assertGreater(a[int(round((y - sl["oy"]) * PX)), int(round((x - sl["ox"]) * PX))], 250)

    def test_santa_is_as_tall_as_the_size_says(self):
        """The size is Santa's height against a pedestrian's: the top of the sleigh's sprite is his hat's top."""
        a = np.array(self.images["santa_sleigh"])[:, :, 3]
        top_row = int(np.where(a.max(axis=1) > 128)[0].min())
        measured_top = self.table["sleigh"]["oy"] + top_row / PX
        self.assertAlmostEqual(self.table["santa_top"], measured_top, delta=0.7)
        self.assertAlmostEqual(build_sleigh.SANTA_UNITS_TALL, self.table["santa_bottom"] - self.table["santa_top"], delta=0.05)

    def test_the_table_carries_the_size_and_the_narrowest_band(self):
        self.assertAlmostEqual(build_sleigh.SANTA_TO_PEDESTRIAN, table_value(self.kt, "SANTA_TO_PEDESTRIAN"), places=2)
        self.assertAlmostEqual(build_sleigh.SANTA_MIN_BAND_UNITS, table_value(self.kt, "SANTA_MIN_BAND_UNITS"), places=2)
        self.assertEqual(4, len(table_array(self.kt, "DEER_X")))


if __name__ == "__main__":
    unittest.main()
