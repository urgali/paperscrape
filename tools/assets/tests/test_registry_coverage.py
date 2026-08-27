"""Tests for the one contract the registry exists to keep: it describes the shipped set.

`sources/sprites.json` holds one entry per shipped PNG. That is not a convenience,
it is the format's job -- a registry listing only the sprites somebody remembered to
add reads as complete while omitting exactly the sprites nobody is looking after.

It stopped being true without anything failing. Ninety-six person skin-tone recolours
shipped in `res/drawable-nodpi` and never got entries, and the three tools that read
the registry each reported it differently: `validate` listed ninety-six unregistered
sprites, `normalize` raised `KeyError` on the first of them and stopped, and the
committed inventory report went on describing a set of a hundred and twenty-five.
Three symptoms, one cause, and no test that named the cause.

So the cases here are about the relation between the two sets rather than about
either one's size. A count would have to be edited by whoever broke it, which is the
opposite of a check: every assertion below states a rule that a new sprite either
satisfies or fails, whatever the totals happen to be.

Run from `tools/assets/`:

    python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import inventory, normalize, registry  # noqa: E402

REPO_ROOT = TOOL_ROOT.parent.parent
RUNTIME_DIR = REPO_ROOT / "app/src/main/res/drawable-nodpi"
KOTLIN_SOURCES = REPO_ROOT / "app/src"
REGISTRY_PATH = TOOL_ROOT / "sources/sprites.json"

#: A sprite produced by recolouring another one, rather than rendered from its own
#: source. `tools/generate_skin_variants.py` writes these, and the suffix is the whole
#: naming convention: `<base>_skin<tone>`.
DERIVED_SUFFIX = re.compile(r"^(?P<base>.+)_skin(?P<tone>\d+)$")


def shipped_names() -> set[str]:
    return {p.stem for p in RUNTIME_DIR.glob("*.png")}


def referenced_names() -> set[str]:
    """Every `R.drawable.<name>` the Kotlin sources mention, however they reach it."""
    names: set[str] = set()
    for path in KOTLIN_SOURCES.rglob("*.kt"):
        for chunk in path.read_text(encoding="utf-8").split("R.drawable.")[1:]:
            end = 0
            while end < len(chunk) and (chunk[end].isalnum() or chunk[end] == "_"):
                end += 1
            names.add(chunk[:end])
    return names


class ShippedCoverageTest(unittest.TestCase):
    """The registry and the drawable directory describe the same set of sprites."""

    def setUp(self):
        self.specs = registry.load(REGISTRY_PATH)
        self.by_name = {s.name: s for s in self.specs}
        self.shipped = shipped_names()

    def test_every_shipped_png_has_a_registry_entry(self):
        # The direction that broke. A sprite can reach `res/drawable-nodpi` from an
        # art pass or from a generator, and only the registry records which -- so an
        # unregistered PNG is a sprite nothing in the tooling can say anything about.
        self.assertEqual(sorted(self.shipped - set(self.by_name)), [])

    def test_every_registry_entry_has_a_shipped_png(self):
        # The other direction, which has never broken and would mean the opposite
        # mistake: a declaration kept alive after the artwork it describes was
        # deleted, so every measurement made against it is made against nothing.
        self.assertEqual(sorted(set(self.by_name) - self.shipped), [])

    def test_every_derived_variant_names_a_base_that_also_ships(self):
        # `<base>_skin<tone>` is not a free-form name: the generator reads the base
        # sprite to produce it, so a variant whose base is gone cannot be regenerated
        # and cannot be checked against anything.
        for name in sorted(self.shipped):
            match = DERIVED_SUFFIX.match(name)
            if not match:
                continue
            with self.subTest(name=name):
                self.assertIn(match.group("base"), self.by_name)

    def test_a_derived_variant_declares_the_same_geometry_as_its_base(self):
        """A recolour moves paint, never coverage.

        `generate_skin_variants.py` replaces one flat colour and verifies that every
        other colour keeps its exact pixel mask, so a variant's canvas, content box
        and anchor are the base's by construction. Declaring anything else would be a
        registry entry that describes a sprite nobody drew -- and because the variants
        are blitted from the same lookup tables as the base, at the same origin, a
        divergence here is a sprite that lands in the wrong place rather than a
        bookkeeping error.
        """
        for name, spec in sorted(self.by_name.items()):
            match = DERIVED_SUFFIX.match(name)
            if not match:
                continue
            base = self.by_name.get(match.group("base"))
            if base is None:
                continue  # reported by the test above; not double-counted here
            with self.subTest(name=name):
                self.assertEqual(
                    (base.width, base.height, base.content_box, base.anchor_rule,
                     base.anchor, base.scale, base.tint, base.category),
                    (spec.width, spec.height, spec.content_box, spec.anchor_rule,
                     spec.anchor, spec.scale, spec.tint, spec.category),
                    f"{name} declares different geometry or classification from {base.name}",
                )


class PlannerReachTest(unittest.TestCase):
    """The normalisation planner can speak about every sprite it is handed."""

    def setUp(self):
        self.specs = registry.load(REGISTRY_PATH)
        self.measurements = {m.name: m for m in inventory.measure_directory(RUNTIME_DIR)}
        self.referenced = referenced_names()

    def test_the_planner_has_a_scale_for_every_sprite_it_will_reach(self):
        """The exact precondition the `KeyError` violated.

        `normalize.plan` looks a sprite's scale convention up in the registry for
        every measured sprite that is not excluded. There is no default and there
        should not be one -- a sprite whose convention is unknown cannot be cropped
        correctly -- so the check is that the registry covers what the planner walks,
        stated here rather than discovered as a traceback halfway through a run.
        """
        reachable = set(self.measurements) - normalize.excluded_names()
        self.assertEqual(sorted(reachable - {s.name for s in self.specs}), [])

    def test_planning_the_whole_shipped_set_completes(self):
        # The regression itself: this raised `KeyError: person_boy_summer_walk0_skin0`
        # and took `normalize`, and the two shipped-set tests that call it, down with it.
        normalize.plan(
            self.measurements,
            {s.name: s.scale for s in self.specs},
            self.referenced,
        )

    def test_a_derived_variant_is_cropped_with_the_base_it_was_recoloured_from(self):
        """Same lookup table, same origin, so the same crop rectangle.

        A tone is chosen per person and then indexes the table like any other sprite,
        which means the base and its recolours are blitted through one origin. Cropping
        one of them on its own would move it against the others -- the same mistake
        `co_registered_groups` exists to prevent for the walk frames, and the reason
        the group predicates match a family rather than an exact name.
        """
        groups = normalize.co_registered_groups(set(self.measurements))
        group_of = {name: g.key for g in groups for name in g.members}
        for name in sorted(self.measurements):
            match = DERIVED_SUFFIX.match(name)
            if not match:
                continue
            base = match.group("base")
            if base not in self.measurements:
                continue  # reported by ShippedCoverageTest
            with self.subTest(name=name):
                self.assertEqual(
                    group_of.get(base), group_of.get(name),
                    f"{name} and {base} are blitted through one origin but would be "
                    f"cropped by different rectangles",
                )


if __name__ == "__main__":
    unittest.main()
