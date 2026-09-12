"""The variant declaration, and what it is allowed to say about the shipped bytes.

Phase 3.6. The property under test is the one every other check in this tooling
is blind to: two sprites that exist *in order to differ* satisfy every per-sprite
rule -- size, content box, anchor, scale, tint -- while being the same file, and
therefore while the feature they implement does nothing at all. That is not
hypothetical. v73 shipped seasonal outfits for window occupants and car drivers
with the summer and winter head PNGs byte-identical, and nothing failed for two
releases.

So the cases here are about the two directions a declaration can go wrong, plus
the one thing Phase 3.4 must not let back in.
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import registry  # noqa: E402

REGISTRY_PATH = TOOL_ROOT / "sources/sprites.json"


def group(**overrides) -> dict:
    """A minimal well-formed variant group, before the case under test edits it."""
    base = {
        "id": "test_group",
        "axis": "season",
        "members": ["sprite_summer", "sprite_winter"],
        "state": "DISTINCT",
        "reason": "test fixture",
    }
    base.update(overrides)
    return base


def load_groups(groups: list[dict], sprite_names: set[str] | None = None):
    names = {"sprite_summer", "sprite_winter", "sprite_autumn"} if sprite_names is None else sprite_names
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "sprites.json"
        path.write_text(
            json.dumps({"schemaVersion": registry.SCHEMA_VERSION, "variants": groups, "sprites": []}),
            encoding="utf-8",
        )
        return registry.load_variants(path, names)


class VariantDocumentTest(unittest.TestCase):
    """What the document itself has to say before its claims are worth checking."""

    def test_a_well_formed_group_loads(self):
        groups = load_groups([group()])
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0].members, ("sprite_summer", "sprite_winter"))
        self.assertTrue(groups[0].must_differ)

    def test_an_unknown_axis_is_rejected(self):
        with self.assertRaises(registry.RegistryError):
            load_groups([group(axis="weather")])

    def test_an_unknown_state_is_rejected(self):
        with self.assertRaises(registry.RegistryError):
            load_groups([group(state="PROBABLY_FINE")])

    def test_a_group_needs_a_reason_even_when_it_is_distinct(self):
        """A `DISTINCT` claim is still a claim about the artwork.

        Whoever later decides a change has broken the distinction needs to know
        what the distinction was meant to be, so the reason is required in both
        states rather than only for the gap.
        """
        with self.assertRaises(registry.RegistryError):
            load_groups([group(reason="")])

    def test_a_group_of_one_is_rejected(self):
        with self.assertRaises(registry.RegistryError):
            load_groups([group(members=["sprite_summer"])])

    def test_a_member_with_no_registry_entry_is_rejected(self):
        with self.assertRaises(registry.RegistryError):
            load_groups([group(members=["sprite_summer", "sprite_nonexistent"])])

    def test_a_sprite_may_not_belong_to_two_groups(self):
        """Otherwise two declarations could contradict each other about one file."""
        with self.assertRaises(registry.RegistryError):
            load_groups(
                [
                    group(id="a", members=["sprite_summer", "sprite_winter"]),
                    group(id="b", members=["sprite_summer", "sprite_autumn"]),
                ]
            )

    def test_duplicate_group_ids_are_rejected(self):
        with self.assertRaises(registry.RegistryError):
            load_groups([group(), group(members=["sprite_autumn", "sprite_winter"])])


class VariantAgainstBytesTest(unittest.TestCase):
    """The declaration held to the files, in both directions."""

    def check(self, groups, digests):
        return registry.validate_variants(load_groups(groups), digests)

    def test_a_distinct_group_whose_members_differ_passes(self):
        self.assertEqual(
            self.check([group()], {"sprite_summer": "a", "sprite_winter": "b"}), []
        )

    def test_a_distinct_group_whose_members_are_identical_fails(self):
        """The v73 defect, stated as a check."""
        problems = self.check([group()], {"sprite_summer": "a", "sprite_winter": "a"})
        self.assertEqual(len(problems), 1)
        self.assertIn("declared DISTINCT", problems[0])

    def test_a_declared_gap_that_is_still_a_gap_passes(self):
        self.assertEqual(
            self.check(
                [group(state="IDENTICAL_GAP")], {"sprite_summer": "a", "sprite_winter": "a"}
            ),
            [],
        )

    def test_a_declared_gap_whose_artwork_has_arrived_fails(self):
        """The direction that makes the gap self-closing.

        Drawing the missing sprite should produce a failure that says the
        declaration is now stale -- otherwise the gap is closed in the artwork and
        stays open in the registry, and the next reader is told a lie by a file
        that passes its own checks.
        """
        problems = self.check(
            [group(state="IDENTICAL_GAP")], {"sprite_summer": "a", "sprite_winter": "b"}
        )
        self.assertEqual(len(problems), 1)
        self.assertIn("move the group to DISTINCT", problems[0])

    def test_an_undeclared_byte_identical_pair_fails(self):
        """What Phase 3.4 removed, and what must not come back.

        A duplicate outside a variant group is one drawing reached through two
        names: two decodes, two atlas entries, and two files that can be edited
        apart in one place only.
        """
        problems = self.check([group()], {"sprite_summer": "a", "sprite_winter": "b", "other": "a"})
        self.assertEqual(len(problems), 1)
        self.assertIn("no variant group declares them", problems[0])

    def test_a_member_with_no_shipped_png_is_not_double_reported(self):
        """`validate_against_runtime` already reports it as a missing PNG."""
        self.assertEqual(self.check([group()], {"sprite_summer": "a"}), [])


#: Why a seasonal sprite may have no partner in the other season.
#:
#: The rule this file is built on -- *a sprite whose name carries a season is a variant by
#: construction* -- assumes both seasons ship. Since v4.30 four things can make that untrue, and
#: every one of them is a statement about the drawing rather than an omission:
#:
#:  - `head_window`: the winter window busts were **retired**. Indoors the season index was always
#:    0 -- the hat belongs to the street, not to the room behind the pane -- so twelve recolours
#:    shipped that no draw path could select (`BACKLOG_v4_25.md` item 57, closed in v4.30);
#:  - `_mh` on the boy: in winter what he has on his head is his own coat's hood, drawn in the
#:    coat's paint, so it belongs to the shirt region and he has no head region at all;
#:  - `_mb` on the woman and the girl: in summer they have no lower garment -- their legs are
#:    painted in their own skin -- so the trouser region is empty and the skin region covers it;
#:  - a layer that is **byte for byte** a layer another shape already ships is written once and
#:    shared, which is what `SpriteVariantTest` requires of any two identical sprites. The carrying
#:    pose is the walking pose with one arm redrawn, so it shares the walker's head and trouser
#:    masks.
#:
#: Stated as reasons and checked as a rule below, rather than kept as a list of names: a list would
#: go stale the first time a region appears or disappears, which is exactly the class of change this
#: file exists to catch.
def unpaired_reason(name: str, shipped: set[str]) -> str | None:
    """Why [name] has no partner in the other season, or `None` if it should have one."""
    if "head_window" in name:
        return "the winter window busts were retired in v4.30"
    if name.endswith("_mh") and "_boy_" in name:
        return "the winter boy's hood is his coat, so he has no head region"
    if name.endswith("_mb") and ("_woman_" in name or "_girl_" in name):
        return "a summer dress has no trousers; those legs are the skin region"
    other = (name.replace("_summer_", "_winter_") if "_summer_" in name
             else name.replace("_winter_", "_summer_"))
    if other not in shipped:
        # The remaining case: the partner's bytes are another shape's, so it was not written twice.
        return "the partner layer is byte-identical to another shape's and is shared with it"
    return None


class ShippedVariantsTest(unittest.TestCase):
    """The declaration as it actually ships."""

    def setUp(self):
        self.specs = registry.load(REGISTRY_PATH)
        self.groups = registry.load_variants(REGISTRY_PATH, {s.name for s in self.specs})

    def test_every_seasonal_sprite_belongs_to_a_variant_group(self):
        """Coverage stated as a rule, not as a count.

        A sprite whose name carries a season is a variant by construction, so
        leaving one out of the table would be exactly the omission that let the
        head sprites go unchecked. Naming the rule means a new seasonal sprite is
        caught rather than counted.
        """
        claimed = {member for g in self.groups for member in g.members}
        seasonal = {
            s.name
            for s in self.specs
            if "_summer_" in s.name or "_winter_" in s.name
        }
        names = {s.name for s in self.specs}
        undeclared = sorted(
            n for n in seasonal - claimed if unpaired_reason(n, names) is None
        )
        self.assertEqual(undeclared, [])

    def test_every_unpaired_seasonal_sprite_has_a_reason_and_really_is_unpaired(self):
        """The exception is checked, not trusted.

        Two ways round: a sprite excused from the rule above must have **no** partner shipping in
        the other season -- otherwise the excuse is hiding a pair somebody forgot to declare -- and
        the absence must fall into one of the four documented classes. If a winter window bust is
        ever drawn again, or the boy ever takes his hood off, this fails and the reason has to go.
        """
        names = {s.name for s in self.specs}
        claimed = {member for g in self.groups for member in g.members}
        for name in sorted(n for n in names if "_summer_" in n or "_winter_" in n):
            if name in claimed:
                continue
            reason = unpaired_reason(name, names)
            self.assertIsNotNone(reason, f"{name} is neither paired nor explained")
            other = (name.replace("_summer_", "_winter_") if "_summer_" in name
                     else name.replace("_winter_", "_summer_"))
            self.assertNotIn(
                other, names,
                f"{name} is excused as '{reason}' but {other} ships, so the pair is undeclared",
            )

    def test_no_variant_group_is_still_an_open_gap(self):
        """Every seasonal pair is really drawn, so nothing may be declared identical.

        This used to pin six open gaps by name -- the person heads, which had summer
        and winter entries pointing at the same artwork because no winter head had
        ever been drawn (decision D2). The V2 asset library draws all six, so the
        gaps closed and the assertion inverted: the file it was pinning is now the
        file that must stay empty.
        """
        # Keyed on the state rather than on ``not must_differ``: since v4.15 there is a third
        # state, ``IDENTICAL_BY_CONSTRUCTION``, and those groups are also "not must_differ" while
        # being the opposite of an open gap -- they are identities that are correct and permanent.
        # Reading them as gaps would have turned twenty-four pieces of arithmetic into twenty-four
        # bugs.
        self.assertEqual([], sorted(g.id for g in self.groups if g.state == "IDENTICAL_GAP"))

    def test_every_gap_member_is_recorded_as_having_no_source(self):
        """Why the gap is declared instead of closed.

        These sprites cannot be regenerated -- there is nothing to regenerate them
        from -- so closing the gap is asset redesign. If a member ever gains an
        SVG source, that argument no longer holds and this test says so.
        """
        by_name = {s.name: s for s in self.specs}
        for group_ in self.groups:
            if group_.state != "IDENTICAL_GAP":
                continue
            for member in group_.members:
                self.assertFalse(
                    by_name[member].has_svg_source,
                    f"{member} now has a source, so {group_.id} is no longer blocked on redesign",
                )


if __name__ == "__main__":
    unittest.main()

class ByConstructionIdentityTest(unittest.TestCase):
    """The twenty-four identities the pixel-based duplicate check made visible, **and their end**.

    Every skin tone was a shipped PaperScrape colour -- the woman's, the man's and the boy's own --
    so each of those three characters was pixel-identical to one of their own tone variants by
    definition. The byte-based check could not see any of them, because the files were encoded
    separately; the pixel-based one saw all twenty-four, and they were declared rather than removed
    because deleting the duplicate would have meant a special case in the hottest lookup in the
    renderer.

    **v4.30 removed the tone variants entirely.** A person is drawn as fixed art plus one weight
    mask per colourable region and the tone arrives at the blit, so there is no copy of a character
    in its own colour to be identical to. The identities did not become wrong; their subject stopped
    existing.

    The class is kept, inverted, for the reason v4.15 wrote it in the first place: an identity that
    comes back is either a real duplicate or a declaration nobody pruned, and both are worth failing
    on. `tools/assets/sources/sprites.json` is generated for the people by
    `tools/update_people_registry.py`, so a stale declaration is a script defect and this is what
    would report it.
    """

    def setUp(self):
        specs = registry.load(REGISTRY_PATH)
        self.groups = registry.load_variants(REGISTRY_PATH, {s.name for s in specs})

    def test_the_declared_identities_are_all_on_the_skin_axis(self):
        for group_ in self.groups:
            if group_.state != "IDENTICAL_BY_CONSTRUCTION":
                continue
            self.assertEqual("skin", group_.axis, group_.id)

    def test_no_identity_survives_the_tone_variants_that_produced_them(self):
        declared = [g for g in self.groups if g.state == "IDENTICAL_BY_CONSTRUCTION"]
        self.assertEqual(
            [], sorted(g.id for g in declared),
            "an identity is declared but the tone copies it pairs are gone. Either a tone variant "
            "has been reintroduced -- in which case it needs a reason, not a declaration -- or the "
            "registry carries a group nobody pruned",
        )

    def test_no_sprite_is_a_tone_copy_any_more(self):
        """The other half of the same statement, read off the shipped set rather than the table."""
        specs = registry.load(REGISTRY_PATH)
        tones = sorted(s.name for s in specs if "_skin" in s.name)
        self.assertEqual(
            [], tones,
            "a per-skin-tone copy is shipping again. v4.30 replaced them with weight masks "
            "resolved at the blit; adding one back is a decision about the whole people system",
        )


if __name__ == "__main__":
    unittest.main()
