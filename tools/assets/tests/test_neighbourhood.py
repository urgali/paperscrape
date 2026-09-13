"""The neighbourhood generator's own contracts.

`build_neighbourhood.py` draws the five building families and writes four things that have to
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
    """Every `Group` of every piece of every family, built fresh."""
    from build_neighbourhood import families
    groups = []
    for building in families().values():
        for slot in building.slots:
            for piece in slot.options:
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
        from build_neighbourhood import families
        from core import build_concept
        out = TOOL_ROOT / "buildings" / "out"
        _table, _pieces, files = build_concept("mix", families(), out)
        for name in sorted(files):
            shipped = production(name)
            with self.subTest(sprite=shipped):
                self.assertIn(shipped, self.shipped, f"{shipped} is in the table but not in res/")
                self.assertIn(shipped, self.registry, f"{shipped} ships but has no registry entry")


if __name__ == "__main__":
    unittest.main()
