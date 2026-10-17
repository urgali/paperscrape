"""The neighbourhood generator's own contracts.

`build_neighbourhood.py` draws the nine building families and writes four things that have to
agree: the PNGs, the Kotlin table, the registry entries and the budget. These are the checks that
the drawing itself is sound -- the ones that used to live in Kotlin against a flat facade and
cannot any more, because what they were checking is now decided where the piece is drawn.

`SkyscraperCanopyTest` is the one that moved. It read `skyscraper_canopy.png` off the shipped set
and asserted the rc2 criterion -- *zero frontage pixels outside the building's side edges, and the
base on the ground line, not straddling it* -- against `SkyscraperSpriteLayout`'s constants. The
tower has no canopy sprite any more: its canopy is a card of the first tier, declared with
`host="t1"`, and the generator refuses to render a card that leaves its host face. So the
criterion is checked here, on the declaration, for **every** card of every piece rather than for
one sprite of one building -- and the second case below is the one that matters, because a check
that cannot fail asserts nothing.

Run from `tools/assets/`:

    python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))
sys.path.insert(0, str(TOOL_ROOT / "buildings"))

import core  # noqa: E402
from core import ContainmentError  # noqa: E402
from names import production  # noqa: E402

REPO_ROOT = TOOL_ROOT.parent.parent
RUNTIME_DIR = REPO_ROOT / "app/src/main/res/drawable-nodpi"
REGISTRY_PATH = TOOL_ROOT / "sources/sprites.json"


def every_group():
    """Every `Group` of every piece of every family, built fresh -- the tower's three heights and
    the distant houses (v5.11) included, which belong to no option list of their own."""
    from build_neighbourhood import families, extras
    groups = []
    pieces = [piece for building in families().values() for slot in building.slots
              for piece in list(slot.options) + list(slot.heights)]
    pieces += [piece for piece, _kind in extras()]
    for piece in pieces:
        for group, _x, _y in piece.ordered():
            groups.append(group)
    return groups


class ContainmentTest(unittest.TestCase):
    """A card stays inside the face it is declared to lie on."""

    def test_every_shipped_card_is_contained(self):
        for group in every_group():
            with self.subTest(group=group.name):
                group.check()

    def test_the_check_bites(self):
        """The half that makes the half above worth having.

        A window moved twenty units up its own gable leaves the wall it is cut into; without this
        case, `test_every_shipped_card_is_contained` would pass just as happily against a `check()`
        that had been emptied.
        """
        group = core.Group("deliberately_broken")
        group.face("wall", core.rect(-30, -28, 30, 0))
        group.declare(core.bbox(core.rect(-25, -44, -7, -27)), "wall", label="a window")
        with self.assertRaises(ContainmentError) as raised:
            group.check()
        self.assertIn("wall", str(raised.exception))
        self.assertIn("a window", str(raised.exception))

    def test_a_card_that_rests_on_a_face_may_stand_above_it(self):
        """`rests=True` is a different rule, not a weaker one: inside in x, foot on the face.

        The restaurant's dome sign and the tower's crown sit *on* a coping rather than inside it,
        and reading them by the `inside` rule would reject every one of them.
        """
        group = core.Group("resting")
        group.face("coping", core.rect(-51, -43, 51, -39.5))
        group.add(core.rect(-14, -56, 14, -42), "#FFFFFF", host="coping", rests=True, margin=1.0)
        group.check()
        # ... and still refused when it overhangs sideways.
        wide = core.Group("resting_too_wide")
        wide.face("coping", core.rect(-51, -43, 51, -39.5))
        wide.add(core.rect(-60, -56, 60, -42), "#FFFFFF", host="coping", rests=True, margin=1.0)
        with self.assertRaises(ContainmentError):
            wide.check()


class ShippedSetTest(unittest.TestCase):
    """What the generator writes is what the app ships."""

    def setUp(self):
        self.shipped = {p.stem for p in RUNTIME_DIR.glob("*.png")}
        self.registry = {s["name"] for s in json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))["sprites"]}

    def test_no_shipped_sprite_carries_a_concept_round_name(self):
        """`k1_` and `k2_` name a proposal round, not a drawing.

        They are the generator's own names for the two concepts the mix was assembled from, and
        `names.production` exists to keep them out of the shipped set. A `k1_*` in
        `drawable-nodpi` means a piece reached the app without going through that map.
        """
        stray = sorted(n for n in self.shipped if n.startswith(("k1_", "k2_", "k3_")))
        self.assertEqual([], stray)

    def test_no_shipped_sprite_carries_a_capture_suffix(self):
        """`_q1` is the capture builds' suffix, which let a concept's PNGs sit beside the shipped
        ones in one `res` directory. Nothing in a delivery may carry it."""
        stray = sorted(n for n in self.shipped if n.endswith(("_q1", "_q2", "_q3")))
        self.assertEqual([], stray)

    def test_every_piece_of_the_table_ships_and_is_declared(self):
        from build_neighbourhood import families, extras
        from core import build_concept
        out = TOOL_ROOT / "buildings" / "out"
        _table, _pieces, files = build_concept("mix", families(), out, extras())
        for name in sorted(files):
            shipped = production(name)
            with self.subTest(sprite=shipped):
                self.assertIn(shipped, self.shipped, f"{shipped} is in the table but not in res/")
                self.assertIn(shipped, self.registry, f"{shipped} ships but has no registry entry")


class IdentityDetailTest(unittest.TestCase):
    """v5.13: every part that says what the shop or a station is measures 2.4 units or more.

    Each of the six buildings among the houses can stand in any of the five places of the street, and
    the farthest draws a unit at 0.89 px or more on the BV6600 (`StorefrontDetailTest` measures every
    place of every built-in street), so 2.4 units are two pixels there. The numbers the drawings are
    made with are asserted here, and a card that would be thinner is caught by reading the cards: every
    card of the three new buildings narrower than 2.4 units in either direction is a detail that would
    fall under two pixels, unless it is the shared vocabulary (`_is_vocabulary`)."""

    def test_the_floor_is_the_one_the_far_place_needs(self):
        import civic
        self.assertGreaterEqual(civic.DETAIL, 2.4)
        self.assertGreaterEqual(civic.BAG_BAND, civic.DETAIL)
        self.assertGreaterEqual(civic.BAG_GAP, civic.DETAIL)

    def test_no_card_of_the_three_new_buildings_is_thinner_than_the_floor(self):
        import civic
        thin = []
        for make in (civic.shop, civic.fire_station, civic.police_station):
            for group, _x, _y in make().ordered():
                if group.snow:
                    continue
                for index, part in enumerate(group.parts, start=1):
                    xs = [x for x, _ in part.points]
                    ys = [y for _, y in part.points]
                    w, h = max(xs) - min(xs), max(ys) - min(ys)
                    if min(w, h) < civic.DETAIL - 0.05 and not _is_vocabulary(part.fill, w, h):
                        thin.append(f"{group.name} card {index}: {w:.2f} x {h:.2f}")
        self.assertEqual([], thin)

    def test_the_school_eraser_and_the_bar_emblem_are_the_floor_too(self):
        """The two identity parts of the buildings of before that v5.13 brought to the floor with the three new
        ones: the school pencil's eraser (2.2 units until v5.13), measured along the pencil, and the corner
        bar's red emblem, measured across."""
        import math
        import civic
        import school
        import vocab
        from core import Group, RED, YELLOW
        g = Group("probe")
        school.pencil(g, school.EMBLEM_CENTRE[0], school.EMBLEM_CENTRE[1], school.EMBLEM_HEIGHT)
        erasers = [part for part in g.parts if part.fill == YELLOW]
        self.assertEqual(1, len(erasers))
        along = [(x - y) / math.sqrt(2) for x, y in erasers[0].points]
        self.assertGreaterEqual(max(along) - min(along), civic.DETAIL - 0.05, "the school's eraser")
        g = Group("probe")
        vocab.sign_plate(g, "wall", -32, -44, -14, -36, None, 0.0, "bar", colour=RED)
        emblems = [part for part in g.parts if part.fill == RED]
        self.assertEqual(1, len(emblems))
        ys = [y for _, y in emblems[0].points]
        self.assertGreaterEqual(max(ys) - min(ys), civic.DETAIL - 0.05, "the corner bar's emblem")

    def test_the_gaps_between_identity_parts_are_the_floor_too(self):
        """Two parts that must read apart are 2.4 units apart or more: the shop's two crates (one wood),
        the fire engine's lamps from its bumper and its windscreen, the police light bar's red and blue
        lamps, each garage door's panes from its panel lines. A gap under two pixels joins them."""
        import civic

        def boxes(make, group_name, fill):
            out = []
            for group, _x, _y in make().ordered():
                if group.name != group_name:
                    continue
                for part in group.parts:
                    if part.fill == fill:
                        xs = [x for x, _ in part.points]
                        ys = [y for _, y in part.points]
                        out.append((min(xs), min(ys), max(xs), max(ys)))
            return out

        def gap(a, b):
            dx = max(b[0] - a[2], a[0] - b[2], 0.0)
            dy = max(b[1] - a[3], a[1] - b[3], 0.0)
            return max(dx, dy)

        floor = civic.DETAIL - 0.05
        crates = sorted(b for b in boxes(civic.shop, "shop", civic.WOOD) if b[1] > -7.0)
        self.assertEqual(2, len(crates))
        self.assertGreaterEqual(gap(crates[0], crates[1]), floor, "the crates")
        lamps = [b for b in boxes(civic.fire_station, "fire_station", civic.YELLOW) if b[1] > -15.0 and b[0] < -20.0]
        self.assertEqual(2, len(lamps))
        for other in boxes(civic.fire_station, "fire_station", civic.FIRE_GREY) + boxes(civic.fire_station, "fire_station", civic.TRUCK_GLASS):
            for lamp in lamps:
                if other[0] < -20.0:
                    self.assertGreaterEqual(gap(lamp, other), floor, f"a lamp of the engine and {other}")
        red = boxes(civic.police_station, "police_station", civic.LIGHTBAR_RED)
        blue = boxes(civic.police_station, "police_station", civic.LIGHTBAR_BLUE)
        self.assertGreaterEqual(gap(red[0], blue[0]), floor, "the light bar's lamps")
        for make, name, under in ((civic.fire_station, "fire_station", civic.FIRE_RED_U),
                                  (civic.police_station, "police_station", civic.GARAGE_BLUE_U)):
            panes = [b for b in boxes(make, name, civic.TRUCK_GLASS) if b[0] > -20.0 or name == "police_station"]
            lines = [b for b in boxes(make, name, under) if b[3] - b[1] < civic.DETAIL + 0.1]
            self.assertTrue(panes and lines, name)
            for pane in panes:
                for line in lines:
                    self.assertGreaterEqual(gap(pane, line), floor, f"{name}: a pane and a panel line")


def _is_vocabulary(fill, w, h):
    """The shared vocabulary every building draws at every depth (`vocab.py`): the door handle's dot
    (1.8 across), the sills' cream bands and the steps' stone ones, which are not identity details --
    the module docstring of `civic.py` says so. Recognised by shape and colour, not by name: a dot of
    1.8 units, or a band at least ten units long in the sills' or the steps' colour. A long band in any
    other colour is an identity part and is held to the floor: the garage doors' panel lines, the
    engine's bumper, the light bar's base (until v5.13C the length alone let those through)."""
    if abs(w - 1.8) < 0.3 and abs(h - 1.8) < 0.3:
        return True
    return max(w, h) >= 10.0 and fill in (core.CREAM, core.STONE)



class ChristmasPlacesTest(unittest.TestCase):
    """Where the Christmas decorations hang and the shutter comes down (v5.14), as the generator declares them.

    `build_neighbourhood.py` writes into `NeighbourhoodTable.kt`, beside the sills it always wrote, each top piece's
    roofline and highest point and each piece's front door -- the door's box, the outline its card was cut to and
    its shadow paper's offset -- off the drawing itself (`core.roofline_and_top`, `vocab.door`, `vocab.glass_door`).
    These hold that what the table says is what the generator draws today, and that it is on the drawing."""

    @classmethod
    def setUpClass(cls):
        from build_neighbourhood import families, extras, kotlin_table
        from core import build_concept
        out = TOOL_ROOT / "buildings" / "out"
        cls.table, cls.pieces, _files = build_concept("mix", families(), out, extras())
        distant = [piece.name for piece, _ in extras()]
        cls.kotlin = kotlin_table(cls.table, cls.pieces, distant)
        cls.distant = distant
        cls.tops = {o for t in cls.table.values() for o in t["slots"][-1]["options"] + t["slots"][-1]["heights"]} | set(distant)
        cls.grounds = {o for t in cls.table.values() for o in t["slots"][0]["options"] + t["slots"][0]["heights"]}

    def test_the_committed_table_is_what_the_generator_writes(self):
        committed = (REPO_ROOT / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/NeighbourhoodTable.kt").read_text()
        self.assertEqual(committed, self.kotlin, "NeighbourhoodTable.kt is not the generator's output: run "
                                                 "`python3 -m buildings.build_neighbourhood --res`")

    def test_every_building_has_one_front_door_on_its_ground_piece(self):
        # One: no piece but a ground piece has a door, where the engine would hang a second wreath (v5.14C) -- the
        # generator already refuses two doors in one piece.
        for name in sorted(set(self.pieces) - self.grounds):
            with self.subTest(piece=name):
                self.assertIsNone(self.pieces[name]["door"], f"{name} is not a ground piece and has a front door")
        for name in sorted(self.grounds):
            with self.subTest(piece=name):
                door = self.pieces[name]["door"]
                self.assertIsNotNone(door, f"{name} has no front door")
                x, y, w, h = door["box"]
                xs = [p[0] for p in door["outline"]]
                ys = [p[1] for p in door["outline"]]
                # The outline is the card as cut: within the scissors' wobble of its box.
                self.assertLess(max(abs(min(xs) - x), abs(max(xs) - (x + w)), abs(min(ys) - y), abs(max(ys) - (y + h))), 1.2)

    def test_a_tower_door_is_the_opening_of_its_frame_and_casts_no_shadow(self):
        for name in sorted(self.grounds):
            if not name.startswith("k2_t_"):
                continue
            with self.subTest(piece=name):
                self.assertEqual((0.0, 0.0), tuple(self.pieces[name]["door"]["relief"]))

    def test_every_top_piece_has_a_roofline_of_bulbs_about_six_units_apart(self):
        """The outline's length shared out evenly, a bulb every 6 units or a little more; across a step of the
        outline -- a turret's wall, a cornice -- two bulbs stand closer than their distance along it; and where the
        outline crossed open sky past an overhang its bulbs were left out (`core.ROOF_BULB_REACH`), so two bulbs
        further apart than two steps have sky between them."""
        import math
        for name in sorted(self.tops):
            with self.subTest(piece=name):
                roof = self.pieces[name]["roof"]
                self.assertGreaterEqual(len(roof), 4, f"{name}: {roof}")
                steps = [math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(roof, roof[1:])]
                gaps = [(a, b) for a, b in zip(roof, roof[1:]) if math.hypot(b[0] - a[0], b[1] - a[1]) > 2 * core.ROOF_BULB_SPACING]
                self.assertLessEqual(len(gaps), 1, f"{name}: {gaps}")
                self.assertIn(name, ("fire_station", "k1_roof_turret_b") if gaps else (name,), f"{name}: a gap {gaps}")
                # About six units apart (v5.14C: until then any step between 1 and 12 units passed): every step but the
                # one across the sky between half the spacing -- two bulbs either side of a step of the outline -- and
                # half as much again as the spacing, the length shared out evenly (7.7 on the tall distant house).
                for step in steps:
                    if step > 2 * core.ROOF_BULB_SPACING:
                        continue
                    self.assertGreaterEqual(step, core.ROOF_BULB_SPACING / 2, f"{name}: two bulbs {step:.2f} units apart")
                    self.assertLessEqual(step, 1.5 * core.ROOF_BULB_SPACING, f"{name}: two bulbs {step:.2f} units apart")

    def test_every_bulb_of_a_roofline_sits_on_the_roof_edge(self):
        """Independent of how the line was found, off the shipped PNGs: within half a bulb of a bulb's centre there is
        paper of the piece -- no bulb hangs in the air -- and within half the narrowest part kept (`ROOF_NARROW`)
        there is sky: a bulb is on the roof's edge, or a few units under a tip too narrow to carry a string. Looked
        for in every direction: the roofline climbs a turret's or a tower's wall as well as a roof's slope."""
        import numpy as np
        from PIL import Image
        for name in sorted(self.tops):
            piece = self.pieces[name]
            layers = []
            for role, png, x, y in piece["parts"]:
                if role in ("FIXED", "ADD_WALL", "ADD_GLASS"):
                    a = np.array(Image.open(RUNTIME_DIR / f"{production(png)}.png").convert("RGBA"))[:, :, 3]
                    layers.append((a, x, y))

            def opaque(ux, uy):
                for a, x, y in layers:
                    px, py = int(round((ux - x) * 3)), int(round((uy - y) * 3))
                    if 0 <= py < a.shape[0] and 0 <= px < a.shape[1] and a[py, px] > 128:
                        return True
                return False

            def disc(r):
                n = int(r * 4)
                return [(dx * 0.25, dy * 0.25) for dx in range(-n, n + 1) for dy in range(-n, n + 1) if dx * dx + dy * dy <= n * n]

            near, wide = disc(core.ROOF_BULB_REACH), disc(core.ROOF_NARROW / 2)
            for bx, by in piece["roof"]:
                with self.subTest(piece=name, bulb=(round(bx, 2), round(by, 2))):
                    self.assertTrue(any(opaque(bx + dx, by + dy) for dx, dy in near), "no paper within half a bulb: floating")
                    self.assertFalse(all(opaque(bx + dx, by + dy) for dx, dy in wide), "buried deeper than a narrow tip")


if __name__ == "__main__":
    unittest.main()
