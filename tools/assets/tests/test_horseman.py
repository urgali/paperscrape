"""The Headless Horseman, the rider in three pieces with his arm throwing: `horseman/build_horseman.py` draws it and
declares it, from one geometry.

The drawing is the rider of Sleepy Hollow, as tall against the people as Santa (`DESIGN_NOTES.md` §12, decision 79); the
pumpkins are thrown from his hand with the arm's gesture, and his shoulder is drawn so every strip holds 2 px at every
angle of it (decision 84). These hold that every file the generator writes -- the twelve SVG sources, the twelve PNGs the pipeline renders
of them and `HorsemanTable.kt` -- is its output today; that no piece of any colour of the group as the renderer draws it -- in
the order `HeadlessHorsemanDrawer.draw` lays it down, read off its body: the cape, the legs, the horse, the reins, the rider's
body, his arm, the pumpkin in his hand --, at the moments of a stride that show the four poses, the horse's and the rider's
bobs at their ends and the cape's swing at both of its, all read from the drawer's own constants, the reins included, and
with his arm at every degree of the throw read from the flight's constants (every degree from where it lets go to where it
winds back), is under 2 px everywhere at the BV6600's size: each holds a square of 2 px somewhere -- the pumpkin's papers,
turned with the arm, a disc 2 px across --, on the SVGs rendered without antialiasing (the sleigh's rule: antialiasing turns every edge into a band a pixel wide);
that the reins are 2.5 px or more there; that the points the renderer hangs things on lie on the paper they belong to -- the
bit on the horse's head, the rein hand on its glove, the seat on the saddle, the cape's pivot on the coat, the arm's pivot on
its sleeve, the pumpkin's middle on the pumpkin --; that the carved face of the pumpkin he holds and
throws holds 2 px in each cut and in the paper between two of them (the moon's rule: the paper is one piece with the
pumpkin, which the first rule cannot see), and the pumpkin alone holds them turned at any angle as it falls; that the group's
extent holds every piece; and that the rider the size is set on -- the top of his collar to the bottom of his boot -- is the
rider drawn.

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
sys.path.insert(0, str(TOOL_ROOT / "horseman"))

import build_horseman  # noqa: E402
from paperscrape_assets.raster import render_svg, same_output  # noqa: E402

PX = 3
ENGINE = TOOL_ROOT.parent.parent / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine"

# How the group moves as it flies, read from the renderer that moves it.
DRAWER = (ENGINE / "HeadlessHorsemanDrawer.kt").read_text()
MOTION = {n: float(re.search(rf"const val {n} = (-?[0-9.]+)f", DRAWER).group(1))
          for n in ("HORSE_BOB_UNITS", "RIDER_BOB_UNITS", "CAPE_SWING_DEGREES", "CAPE_PHASE", "STRIDE_RATE", "POSES_PER_SECOND")}
assert re.search(r"val stride = t\.sinAt\(STRIDE_RATE\)", DRAWER), "the drawer no longer bobs the group on the stride's sine"
assert re.search(r"val riderBob = -stride \* RIDER_BOB_UNITS", DRAWER), "the rider no longer sits the stride the other way"
assert re.search(r"t\.sinAt\(STRIDE_RATE, CAPE_PHASE\) \* CAPE_SWING_DEGREES", DRAWER), "the cape no longer swings on the stride"
assert re.search(r"t\.frameIndex\(POSES_PER_SECOND, 0f, 4\)", DRAWER), "the pose is no longer the frame of POSES_PER_SECOND"
assert "canvas.rotate(armDegrees)" in DRAWER, "the drawer no longer turns the arm by the flight's angle"

# How far the arm turns for a throw, read from the flight that turns it.
EFFECT = (ENGINE / "HeadlessHorsemanEffect.kt").read_text()
GESTURE = {n: float(re.search(rf"const val {n} = (-?[0-9.]+)f", EFFECT).group(1)) for n in ("WIND_BACK_DEGREES", "RELEASE_DEGREES")}


def arm_angles(step: float = 1.0) -> list[float]:
    """Every angle the arm passes for a throw, every [step] degrees from where it lets go to where it winds back, both ends
    and the rest (0) included."""
    lo, hi = GESTURE["RELEASE_DEGREES"], GESTURE["WIND_BACK_DEGREES"]
    out = {lo, hi, 0.0}
    a = math.ceil(lo / step) * step
    while a < hi:
        out.add(float(a))
        a += step
    return sorted(out)


def pedestrian_px_bv6600() -> float:
    """An adult walking on the near row of the pavement on the BV6600 (1 440 px tall), from `SceneSpace` itself:
    `SantaSleighScale.pedestrianPx`."""
    space = (ENGINE / "SceneSpace.kt").read_text()

    def c(name):
        return float(re.search(rf"const val {name} = ([0-9.]+)f", space).group(1))
    perspective = (c("PAVEMENT_NEAR_Y_FRACTION") - c("HORIZON_Y_FRACTION")) / (c("REFERENCE_Y_FRACTION") - c("HORIZON_Y_FRACTION"))
    return c("PERSON_METRES_TALL") * c("PIXELS_PER_METRE_AT_REFERENCE") * (1440.0 / c("REFERENCE_SCREEN_HEIGHT_PX")) * perspective


def drawing_order() -> list[str]:
    """The order `HeadlessHorsemanDrawer.draw` lays the group down in, read off its body."""
    body = DRAWER[re.search(r"fun draw\(\s*canvas: SceneCanvas", DRAWER).start():]
    body = body[:body.index("\n    }\n")]
    marks = {
        "cape": "HorsemanTable.CAPE_RES",
        "legs": "HorsemanTable.LEGS_RES[pose]",
        "horse": "HorsemanTable.HORSE_RES[pose]",
        "reins": "canvas.drawLine(",
        "body": "HorsemanTable.BODY_RES",
        "arm": "HorsemanTable.ARM_RES",
        "pumpkin": "HorsemanTable.PUMPKIN_RES",
    }
    found = {k: body.find(m) for k, m in marks.items()}
    missing = [k for k, i in found.items() if i < 0]
    assert not missing, f"HeadlessHorsemanDrawer.draw no longer draws {missing} as this test reads it"
    return sorted(found, key=found.get)


def moments() -> list[tuple[int, float]]:
    """(pose, the stride's phase) for every moment looked at: the drawer shows pose p while `seconds x STRIDE_RATE` is in
    [p, p + 1) quarter turns (`POSES_PER_SECOND` poses a second, `STRIDE_RATE` radians a second: a pose a quarter turn), so
    each pose at five points of its quarter, its two ends included -- where the bobs are at their extremes --, and the
    cape's two extremes, a phase of its own (`CAPE_PHASE`) behind."""
    assert abs(MOTION["STRIDE_RATE"] / MOTION["POSES_PER_SECOND"] - math.pi / 2) < 1e-4
    out = [(pose, (pose + j / 4) * math.pi / 2) for pose in range(4) for j in range(5)]
    for extreme in (math.pi / 2, 3 * math.pi / 2):
        phase = (extreme - MOTION["CAPE_PHASE"]) % (2 * math.pi)
        out.append((int(phase // (math.pi / 2)), phase))
    return out


def crisp(svg: str) -> str:
    return svg.replace("<svg ", '<svg shape-rendering="crispEdges" ', 1)


def render(svg: str) -> Image.Image:
    return Image.open(io.BytesIO(render_svg(svg).png_bytes)).convert("RGBA")


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


def discs(m, side):
    """True at the middle of every disc [side] px across lying wholly inside [m]: a turned band's measure."""
    r = side / 2
    reach = int(math.floor(r))
    H, W = m.shape
    padded = np.pad(m, reach)
    out = np.ones_like(m)
    for dy in range(-reach, reach + 1):
        for dx in range(-reach, reach + 1):
            if dx * dx + dy * dy <= r * r:
                out &= padded[reach + dy:reach + dy + H, reach + dx:reach + dx + W]
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


def colour_mask(a, colour):
    rgb = np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)])
    return (np.abs(a[:, :, :3].astype(int) - rgb).sum(axis=2) == 0) & (a[:, :, 3] == 255)


def table_value(text, name):
    return float(re.search(rf"const val {name} = (-?[0-9.]+)f", text).group(1))


def table_array(text, name):
    return [float(v) for v in re.search(rf"val {name} = floatArrayOf\(([^)]*)\)", text).group(1).replace("f", "").split(",")]


class HorsemanTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.out, cls.table, cls.size = build_horseman.build()
        cls.kt = cls.out[build_horseman.TABLE].decode()
        kt = cls.kt
        cls.t = {n: table_array(kt, n) for n in ("HORSE_X", "HORSE_Y", "LEGS_X", "LEGS_Y", "BIT_X", "BIT_Y", "SEAT_X", "SEAT_Y")}
        cls.t.update({n: table_value(kt, n) for n in ("BODY_X", "BODY_Y", "ARM_PIVOT_X", "ARM_PIVOT_Y", "ARM_X", "ARM_Y", "PUMPKIN_X",
                                                       "PUMPKIN_Y", "PUMPKIN_BLIT_X", "PUMPKIN_BLIT_Y", "RIDER_PUMPKIN_RADIUS_UNITS",
                                                       "HAND_X", "HAND_Y", "SHOULDER_X", "SHOULDER_Y", "CAPE_X", "CAPE_Y", "LEFT", "RIGHT",
                                                       "TOP", "BOTTOM", "RIDER_TOP", "RIDER_BOTTOM", "RIDER_TO_PEDESTRIAN", "REIN_UNITS")})
        # `HeadlessHorsemanScale.pxPerUnit` on the BV6600.
        cls.px_per_unit = cls.t["RIDER_TO_PEDESTRIAN"] * pedestrian_px_bv6600() / (cls.t["RIDER_BOTTOM"] - cls.t["RIDER_TOP"])
        cls.svg = {n: cls.out[build_horseman.SVG_DIR / f"{n}.svg"].decode() for n in build_horseman.NAMES}
        # The SVGs rendered without antialiasing: every pixel one paper's, so a piece's width is its own.
        cls.crisp = {n: render(crisp(s)) for n, s in cls.svg.items()}
        # The pumpkin's colours, which no other piece of the group wears: the papers that turn with the arm.
        a = np.array(cls.crisp["horseman_rider_pumpkin"]).astype(np.int64)
        opaque = a[:, :, 3] == 255
        cls.pumpkin_keys = set(np.unique(((a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2])[opaque]).tolist())
        cls._turned = {}

    def test_every_file_is_the_generator_output(self):
        for path, data in self.out.items():
            with self.subTest(file=path.name):
                self.assertTrue(path.exists(), f"{path} is missing")
                self.assertTrue(same_output(path, data), f"{path.name} is not what horseman/build_horseman.py writes: run it with --res")

    def turned_cape(self, degrees):
        """The cape rendered crisp turned by [degrees] about its pivot, clockwise on the screen as the canvas turns it:
        the image and its top-left in units from the pivot."""
        svg = self.svg["horseman_cape"]
        x, y, w, h = (float(v) for v in re.search(r'viewBox="([-0-9.]+) ([-0-9.]+) ([-0-9.]+) ([-0-9.]+)"', svg).groups())
        r = math.radians(degrees)
        xs = [cx * math.cos(r) - cy * math.sin(r) for cx, cy in ((x, y), (x + w, y), (x, y + h), (x + w, y + h))]
        ys = [cx * math.sin(r) + cy * math.cos(r) for cx, cy in ((x, y), (x + w, y), (x, y + h), (x + w, y + h))]
        x0, y0, x1, y1 = math.floor(min(xs)), math.floor(min(ys)), math.ceil(max(xs)), math.ceil(max(ys))
        inner = svg[svg.index(">") + 1:svg.rindex("</svg>")]
        doc = (f'<svg xmlns="http://www.w3.org/2000/svg" shape-rendering="crispEdges" width="{(x1 - x0) * PX}" height="{(y1 - y0) * PX}" '
               f'viewBox="{x0} {y0} {x1 - x0} {y1 - y0}"><g transform="rotate({degrees:.6f})">{inner}</g></svg>')
        return render(doc), (x0, y0)

    def in_pivot_frame(self, name: str, degrees: float) -> tuple[Image.Image, tuple[int, int]]:
        """The arm's or the pumpkin's SVG, as the drawer blits it in the arm's frame -- the arm from its pivot at ARM_X/Y,
        the pumpkin at its middle's place from the pivot and PUMPKIN_BLIT_X/Y from it --, turned [degrees] about the pivot
        and rendered crisp: the image and its top-left in units from the pivot."""
        key = (name, round(degrees, 4))
        if key not in self._turned:
            t = self.t
            svg = self.svg[name]
            x, y, w, h = (float(v) for v in re.search(r'viewBox="([-0-9.]+) ([-0-9.]+) ([-0-9.]+) ([-0-9.]+)"', svg).groups())
            if name == "horseman_rider_arm":
                ox, oy = t["ARM_X"], t["ARM_Y"]
            else:
                ox = t["PUMPKIN_X"] - t["ARM_PIVOT_X"] + t["PUMPKIN_BLIT_X"]
                oy = t["PUMPKIN_Y"] - t["ARM_PIVOT_Y"] + t["PUMPKIN_BLIT_Y"]
            r = math.radians(degrees)
            pts = [(ox, oy), (ox + w, oy), (ox, oy + h), (ox + w, oy + h)]
            xs = [px * math.cos(r) - py * math.sin(r) for px, py in pts]
            ys = [px * math.sin(r) + py * math.cos(r) for px, py in pts]
            x0, y0, x1, y1 = math.floor(min(xs)), math.floor(min(ys)), math.ceil(max(xs)), math.ceil(max(ys))
            inner = svg[svg.index(">") + 1:svg.rindex("</svg>")]
            doc = (f'<svg xmlns="http://www.w3.org/2000/svg" shape-rendering="crispEdges" width="{(x1 - x0) * PX}" height="{(y1 - y0) * PX}" '
                   f'viewBox="{x0} {y0} {x1 - x0} {y1 - y0}"><g transform="rotate({degrees:.6f}) translate({ox - x:.6f},{oy - y:.6f})">{inner}</g></svg>')
            self._turned[key] = (render(doc), (x0, y0))
        return self._turned[key]

    def composed(self, pose: int, phase: float, only: tuple[str, ...] | None = None, arm: float = 0.0) -> Image.Image:
        """The group as `HeadlessHorsemanDrawer.draw` lays it down, at 3 px a unit, in its order ([drawing_order]), from the
        table it reads: in pose [pose] at the stride's [phase] -- the legs and the horse down by `HORSE_BOB_UNITS` x the
        stride's sine, the seat (and the rider, his hand, his shoulders and his arm's pivot) by `-RIDER_BOB_UNITS` x it, the
        cape turned `CAPE_SWING_DEGREES` x the sine of the phase and `CAPE_PHASE` about the shoulders --, the reins a line
        `REIN_UNITS` wide with round ends from the hand to the bit, as the renderer's paint draws them, and his arm with the
        pumpkin in its hand turned [arm] degrees about its pivot; [only], the steps to draw."""
        t = self.t
        stride = math.sin(phase)
        horse_bob, rider_bob = stride * MOTION["HORSE_BOB_UNITS"], -stride * MOTION["RIDER_BOB_UNITS"]
        swing = math.sin(phase + MOTION["CAPE_PHASE"]) * MOTION["CAPE_SWING_DEGREES"]
        left, top = math.floor(t["LEFT"]) - 8, math.floor(t["TOP"]) - 8
        canvas = Image.new("RGBA", (int((math.ceil(t["RIGHT"]) + 8 - left) * PX), int((math.ceil(t["BOTTOM"]) + 8 - top) * PX)), (0, 0, 0, 0))

        def at(ux, uy):
            return int(round((ux - left) * PX)), int(round((uy - top) * PX))

        seat_x, seat_y = t["SEAT_X"][pose], t["SEAT_Y"][pose] + rider_bob

        def cape():
            image, (x0, y0) = self.turned_cape(swing)
            canvas.alpha_composite(image, at(seat_x + t["SHOULDER_X"] + x0, seat_y + t["SHOULDER_Y"] + y0))

        def legs():
            canvas.alpha_composite(self.crisp[f"horseman_legs_{pose}"], at(t["LEGS_X"][pose], t["LEGS_Y"][pose] + horse_bob))

        def horse():
            canvas.alpha_composite(self.crisp[f"horseman_horse_{pose}"], at(t["HORSE_X"][pose], t["HORSE_Y"][pose] + horse_bob))

        def reins():
            layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
            draw = ImageDraw.Draw(layer)
            ends = [((seat_x + t["HAND_X"] - left) * PX, (seat_y + t["HAND_Y"] - top) * PX),
                    ((t["BIT_X"][pose] - left) * PX, (t["BIT_Y"][pose] + horse_bob - top) * PX)]
            r = t["REIN_UNITS"] * PX / 2
            draw.line(ends, fill=build_horseman.REIN_COLOUR, width=int(round(t["REIN_UNITS"] * PX)))
            for x, y in ends:
                draw.ellipse((x - r, y - r, x + r, y + r), fill=build_horseman.REIN_COLOUR)
            canvas.alpha_composite(layer)

        def body():
            canvas.alpha_composite(self.crisp["horseman_rider_body"], at(seat_x + t["BODY_X"], seat_y + t["BODY_Y"]))

        def turned_piece(name):
            image, (x0, y0) = self.in_pivot_frame(name, arm)
            canvas.alpha_composite(image, at(seat_x + t["ARM_PIVOT_X"] + x0, seat_y + t["ARM_PIVOT_Y"] + y0))

        steps = dict(cape=cape, legs=legs, horse=horse, reins=reins, body=body,
                     arm=lambda: turned_piece("horseman_rider_arm"), pumpkin=lambda: turned_piece("horseman_rider_pumpkin"))
        for step in drawing_order():
            if only is None or step in only:
                steps[step]()
        return canvas

    def test_no_colour_is_a_piece_under_two_pixels_on_the_bv6600(self):
        """2 px on the BV6600 are 2 / `pxPerUnit` units there, `side` px of the 3x drawing: a piece of one colour of the
        group composed as the renderer draws it ([composed]) -- at every moment of the stride and with the arm at every angle
        of the throw ([arm_angles]) -- that holds no square of that side anywhere is under 2 px everywhere; with the arm
        turned, the pumpkin's papers, turned with it, are held to a disc that side across, since an upright square in a band
        shrinks as the band turns (by 0.71 at 45 degrees) and the band does not. Pieces under 12 px are the corners where
        three papers meet. With the arm turned, the shoulder's round and the narrow dark of the neck
        (`build_horseman.SHOULDER_CAP`, `NECK_HOLE`) are what keep the collar's under-paper, the collar's inside and the
        cape over 2 px."""
        side = 2.0 / self.px_per_unit * PX
        k = math.ceil(side)
        for angle in arm_angles():
            for pose, phase in moments():
                a = np.array(self.composed(pose, phase, arm=angle)).astype(np.int64)
                opaque = a[:, :, 3] == 255
                keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
                for key in np.unique(keys[opaque]):
                    m = opaque & (keys == key)
                    turned = angle != 0.0 and int(key) in self.pumpkin_keys
                    ok = discs(m, side) if turned else squares(m, k)
                    for pts in pieces(m):
                        if len(pts) < 12:
                            continue
                        with self.subTest(arm=angle, pose=pose, phase=round(phase, 3), colour="#%06X" % int(key), at=pts[0]):
                            self.assertTrue(any(ok[p] for p in pts), f"{len(pts)} px of #{int(key):06X} under {side:.1f} px everywhere")

    def test_the_thrown_pumpkin_holds_two_pixels_at_every_turn(self):
        """Thrown, the pumpkin falls alone and turns (`HeadlessHorsemanEffect.DROP_TURN_DEGREES_PER_SECOND`): every piece of
        it, and each cut of its face, holds a disc 2 px across on the BV6600 at every turn, a degree apart -- the group's
        scale, `HeadlessHorsemanScale` -- and its face's paper between two cuts keeps 2 px (the first rule's other half,
        below, measures it upright)."""
        side = 2.0 / self.px_per_unit * PX
        svg = self.svg["horseman_rider_pumpkin"]
        inner = svg[svg.index(">") + 1:svg.rindex("</svg>")]
        x, y, w, h = (float(v) for v in re.search(r'viewBox="([-0-9.]+) ([-0-9.]+) ([-0-9.]+) ([-0-9.]+)"', svg).groups())
        span = math.ceil(math.hypot(w, h)) + 2
        for degrees in range(0, 360, 1):
            doc = (f'<svg xmlns="http://www.w3.org/2000/svg" shape-rendering="crispEdges" width="{span * PX}" height="{span * PX}" '
                   f'viewBox="{-span / 2} {-span / 2} {span} {span}"><g transform="rotate({degrees}) translate({-x - w / 2:.4f},{-y - h / 2:.4f})">{inner}</g></svg>')
            a = np.array(render(doc)).astype(np.int64)
            opaque = a[:, :, 3] == 255
            keys = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
            for key in np.unique(keys[opaque]):
                m = opaque & (keys == key)
                ok = discs(m, side)
                for pts in pieces(m):
                    if len(pts) < 12:
                        continue
                    with self.subTest(turn=degrees, colour="#%06X" % int(key)):
                        self.assertTrue(any(ok[p] for p in pts), f"{len(pts)} px of #{int(key):06X} under {side:.1f} px turned {degrees}")

    def test_the_group_is_composed_in_the_renderers_order_with_its_reins(self):
        """The composition above is the renderer's: the cape behind everything, the legs behind the horse, the reins over
        the horse and under the rider, his arm over his body and the pumpkin over his hand; and the reins are in it -- a
        cord cuts the papers it crosses, so a composition without it passes pieces under 2 px that the phone shows."""
        self.assertEqual(["cape", "legs", "horse", "reins", "body", "arm", "pumpkin"], drawing_order())
        bare = np.array(self.composed(0, 0.0, only=("cape", "legs", "horse", "body", "arm", "pumpkin")))
        whole = np.array(self.composed(0, 0.0))
        self.assertEqual(0, int(colour_mask(bare, build_horseman.REIN_COLOUR).sum()))
        self.assertGreater(int(colour_mask(whole, build_horseman.REIN_COLOUR).sum()), 150)

    def test_the_reins_are_two_and_a_half_pixels_or_more_on_the_bv6600(self):
        self.assertGreaterEqual(self.t["REIN_UNITS"] * self.px_per_unit, 2.5)

    def at(self, name, ox, oy, x, y):
        """The crisp render of [name], blitted at (ox, oy), at the group's point (x, y): its alpha and its colour."""
        a = np.array(self.crisp[name])
        row, col = int(math.floor((y - oy) * PX)), int(math.floor((x - ox) * PX))
        return a[row, col]

    def assertOnPaper(self, name, ox, oy, x, y, colours, what):
        pixel = self.at(name, ox, oy, x, y)
        self.assertEqual(255, int(pixel[3]), f"{what} is not on {name}'s paper")
        found = "#%02X%02X%02X" % tuple(int(v) for v in pixel[:3])
        self.assertIn(found, colours, f"{what} lies on {found}, not on {colours}")

    def test_the_points_lie_on_the_paper_they_belong_to(self):
        """The bit on the horse's head (its head or its muzzle), in every pose; the rein hand on its glove; the seat on the
        saddle; the cape's pivot on the coat (the sleeve over the shoulder, the arm at rest); the arm's pivot on its sleeve (the
        sleeve's round end covers what is about the pivot at every angle); the pumpkin's middle on the pumpkin or its lit face."""
        t = self.t
        for pose in range(4):
            with self.subTest(pose=pose):
                self.assertOnPaper(f"horseman_horse_{pose}", t["HORSE_X"][pose], t["HORSE_Y"][pose], t["BIT_X"][pose], t["BIT_Y"][pose],
                                   (build_horseman.HORSE, build_horseman.MUZZLE), "the bit")
                self.assertOnPaper(f"horseman_horse_{pose}", t["HORSE_X"][pose], t["HORSE_Y"][pose], t["SEAT_X"][pose], t["SEAT_Y"][pose],
                                   (build_horseman.SADDLE,), "the seat")
        bx, by = t["BODY_X"], t["BODY_Y"]
        self.assertOnPaper("horseman_rider_body", bx, by, t["HAND_X"], t["HAND_Y"], (build_horseman.GLOVE,), "the rein hand")
        # The cape's pivot under the sleeve, at the shoulder: on the arm at rest, which covers it.
        self.assertOnPaper("horseman_rider_arm", t["ARM_PIVOT_X"] + t["ARM_X"], t["ARM_PIVOT_Y"] + t["ARM_Y"], t["SHOULDER_X"], t["SHOULDER_Y"],
                           (build_horseman.COAT,), "the cape's pivot")
        self.assertOnPaper("horseman_rider_arm", t["ARM_PIVOT_X"] + t["ARM_X"], t["ARM_PIVOT_Y"] + t["ARM_Y"], t["ARM_PIVOT_X"], t["ARM_PIVOT_Y"],
                           (build_horseman.COAT,), "the arm's pivot on its sleeve")
        self.assertOnPaper("horseman_rider_pumpkin", t["PUMPKIN_X"] + t["PUMPKIN_BLIT_X"], t["PUMPKIN_Y"] + t["PUMPKIN_BLIT_Y"],
                           t["PUMPKIN_X"], t["PUMPKIN_Y"], (build_horseman.PUMPKIN, build_horseman.LIGHT), "the pumpkin's middle")

    def test_the_rider_is_as_tall_as_the_size_says(self):
        """The size is set on the rider from the top of his collar to the bottom of his boot: the table's RIDER_TOP and
        RIDER_BOTTOM are the collar's top row and the boot's bottom row of the rider drawn (within a pixel of the 3x
        drawing), and RIDER_TO_PEDESTRIAN is Santa's 1.3. The size the drawer works out from those rows makes him that many
        pedestrians tall by construction; that the app draws him at it is `HorsemanFlightTest`'s (the Kotlin scale)."""
        a = np.array(self.crisp["horseman_rider_body"])
        collar = np.zeros(a.shape[:2], dtype=bool)
        for colour in build_horseman.COLLAR_PAPERS:
            collar |= colour_mask(a, colour)
        boot = colour_mask(a, build_horseman.BOOT)
        top = self.t["BODY_Y"] + int(np.nonzero(collar.any(axis=1))[0].min()) / PX
        bottom = self.t["BODY_Y"] + (int(np.nonzero(boot.any(axis=1))[0].max()) + 1) / PX
        self.assertAlmostEqual(self.t["RIDER_TOP"], top, delta=1 / PX + 0.01)
        self.assertAlmostEqual(self.t["RIDER_BOTTOM"], bottom, delta=1 / PX + 0.01)
        self.assertAlmostEqual(1.3, self.t["RIDER_TO_PEDESTRIAN"], places=2)

    def test_the_pumpkins_face_holds_two_pixels_in_its_cuts_and_between_them(self):
        """The carved face of the pumpkin he holds up -- two eyes and the grin, in the candle's light -- on the rider drawn
        without antialiasing: three cuts, each holding a square of 2 px on the BV6600, and the pumpkin's paper between any
        two of them 2 px across or more (the paper is one piece with the pumpkin, so the rule of every piece cannot see it:
        the moon's rule, `test_jack_moon`)."""
        side = math.ceil(2.0 / self.px_per_unit * PX)
        a = np.array(self.crisp["horseman_rider_pumpkin"])
        light = colour_mask(a, build_horseman.LIGHT)
        cuts = [np.array(p) for p in pieces(light) if len(p) >= 12]
        self.assertEqual(3, len(cuts), "two eyes and the grin")
        ok = squares(light, side)
        for c in cuts:
            with self.subTest(cut=tuple(c[0])):
                self.assertTrue(any(ok[tuple(p)] for p in c), "a cut under 2 px")
        for i in range(len(cuts)):
            for j in range(i + 1, len(cuts)):
                nearest = min(float(np.sqrt(((cuts[i][k:k + 256, None, :] - cuts[j][None, :, :]) ** 2).sum(axis=2)).min())
                              for k in range(0, len(cuts[i]), 256))
                with self.subTest(cuts=(tuple(cuts[i][0]), tuple(cuts[j][0]))):
                    self.assertGreaterEqual(nearest - 1, side, f"{nearest - 1:.1f} px of the pumpkin between two cuts")

    def test_the_extent_holds_every_piece(self):
        """LEFT, RIGHT, TOP and BOTTOM, which the drawer centres the group on and keeps off the screen with, hold every
        piece's sprite in every pose at rest."""
        t = self.t
        for pose in range(4):
            sx, sy = t["SEAT_X"][pose], t["SEAT_Y"][pose]
            boxes = [(f"horseman_horse_{pose}", t["HORSE_X"][pose], t["HORSE_Y"][pose]),
                     (f"horseman_legs_{pose}", t["LEGS_X"][pose], t["LEGS_Y"][pose]),
                     ("horseman_rider_body", sx + t["BODY_X"], sy + t["BODY_Y"]),
                     ("horseman_rider_arm", sx + t["ARM_PIVOT_X"] + t["ARM_X"], sy + t["ARM_PIVOT_Y"] + t["ARM_Y"]),
                     ("horseman_rider_pumpkin", sx + t["PUMPKIN_X"] + t["PUMPKIN_BLIT_X"], sy + t["PUMPKIN_Y"] + t["PUMPKIN_BLIT_Y"]),
                     ("horseman_cape", t["SEAT_X"][pose] + t["SHOULDER_X"] + t["CAPE_X"], t["SEAT_Y"][pose] + t["SHOULDER_Y"] + t["CAPE_Y"])]
            for name, x, y in boxes:
                w, h = self.crisp[name].size
                with self.subTest(pose=pose, piece=name):
                    self.assertGreaterEqual(x, t["LEFT"] - 0.01)
                    self.assertLessEqual(x + w / PX, t["RIGHT"] + 0.01)
                    self.assertGreaterEqual(y, t["TOP"] - 0.01)
                    self.assertLessEqual(y + h / PX, t["BOTTOM"] + 0.01)

    def test_the_narrowest_band_is_the_rules_and_the_drawings(self):
        """HORSEMAN_BAND_UNITS, the narrowest band the table declares, is the rule's -- 2 px on the BV6600 at the
        size the drawer works out, and a quarter more -- and the drawing's: the saddle cloth's trim, drawn at it, holds a
        square within a pixel and a half of HORSEMAN_BAND_UNITS x 3 in every pose, and so does the narrowest piece of the group
        composed as the renderer draws it in each pose, at the moment of the gallop the pose stands for (pose x pi/2: for poses 1
        and 3 the top and the bottom of the stride)."""
        band = table_value(self.kt, "HORSEMAN_BAND_UNITS")
        self.assertAlmostEqual(round(2.0 / self.px_per_unit * 1.25, 2), band, places=2)
        for pose in range(4):
            a = np.array(self.crisp[f"horseman_horse_{pose}"])
            trim = colour_mask(a, build_horseman.TRIM)
            sizes = [largest_square(trim, p) for p in pieces(trim) if len(p) >= 12]
            with self.subTest(pose=pose):
                self.assertEqual(1, len(sizes), "the trim is one band")
                self.assertLessEqual(abs(sizes[0] - band * PX), 1.5, f"the trim holds {sizes[0]} px, the band is {band * PX:.1f}")
                narrowest = narrowest_piece(self.composed(pose, pose * math.pi / 2))
                self.assertLessEqual(abs(narrowest - band * PX), 1.5, f"the narrowest piece holds {narrowest} px")

    def test_the_pumpkin_rests_in_his_hand_and_its_radius_is_the_drawings(self):
        """With the arm at rest the pumpkin's middle is above the glove, its bottom on it (the hand holds it up), and its
        radius across -- the flames' foot in flight, `HorsemanFlight.lanternRadiusPx` -- is the drawing's: the pumpkin's
        paper spans twice it across, within a pixel and a half of the 3x drawing."""
        t = self.t
        a = np.array(self.crisp["horseman_rider_pumpkin"])
        paper = colour_mask(a, build_horseman.PUMPKIN) | colour_mask(a, build_horseman.LIGHT)
        cols = np.nonzero(paper.any(axis=0))[0]
        self.assertLessEqual(abs((cols.max() + 1 - cols.min()) - 2 * t["RIDER_PUMPKIN_RADIUS_UNITS"] * PX), 1.5)
        arm = np.array(self.crisp["horseman_rider_arm"])
        glove = colour_mask(arm, build_horseman.GLOVE)
        rows, gcols = np.nonzero(glove)
        glove_x = t["ARM_PIVOT_X"] + t["ARM_X"] + (gcols.mean() + 0.5) / PX
        glove_top = t["ARM_PIVOT_Y"] + t["ARM_Y"] + rows.min() / PX
        self.assertLess(abs(glove_x - t["PUMPKIN_X"]), 2.0, "the pumpkin above the glove")
        bottom = t["PUMPKIN_Y"] + t["PUMPKIN_BLIT_Y"] + (int(np.nonzero(paper.any(axis=1))[0].max()) + 1) / PX
        self.assertLess(abs(bottom - glove_top), 3.0, f"its bottom {bottom:.1f} on the glove's top {glove_top:.1f}")

    def test_every_sprite_is_on_the_grid(self):
        # The PNGs as shipped, read off the disk: the generator writes them on the grid by construction, a hand-edited or
        # re-exported one need not be.
        for name in build_horseman.NAMES:
            image = Image.open(build_horseman.RES / f"{name}.png")
            with self.subTest(sprite=name):
                self.assertEqual((0, 0), (image.width % PX, image.height % PX))


if __name__ == "__main__":
    unittest.main()
