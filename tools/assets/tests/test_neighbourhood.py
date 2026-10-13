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


if __name__ == "__main__":
    unittest.main()
