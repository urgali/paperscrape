"""Tests for the manifest checks: the declaration, and its comparison to the code.

A check that cannot fail asserts nothing, and these checks exist precisely
because defect D-1 shipped without anything failing. So every case here is a
near miss -- a bounding box off by one pixel, an anchor off by one unit, a scale
convention swapped, a tint class swapped -- because those are the mistakes a
permissive check would wave through. The obviously-wrong cases are not
interesting; a check that only catches those is not a check.

The other half is the resolver's honesty. It must classify a call site it cannot
read as *unresolved* and never as agreement, so there are cases here for the
constructs it genuinely cannot see: a sprite chosen from a lookup table, and an
origin computed from the drawn object's own dimensions.

Run from `tools/assets/`:

    python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import callsites, registry  # noqa: E402

REPO_ROOT = TOOL_ROOT.parent.parent
KOTLIN_SOURCES = REPO_ROOT / "app/src"
REGISTRY_PATH = TOOL_ROOT / "sources/sprites.json"


#: Sprites excused from the anchor-to-origin comparison. Empty, and meant to stay so.
#:
#: It held `bunny_body`, `penguin_body` and `snowman_body`, each blitted one local unit
#: above the ground line its content bottom implied -- defect D-9. Two different causes
#: hid behind one symptom. The snowman and the bunny genuinely floated, and their call
#: sites were corrected, whole drawing at a time so the parts kept their registration.
#: The penguin did not: it stands on `penguin_feet`, blitted separately at the ground,
#: so its body is *supposed* to sit above it, and the fault was declaring the body
#: `CONTENT_BOTTOM_CENTRE` when it is a part. The bunny's body is a part too, for the
#: same reason plus a deliberate horizontal offset that puts its ears over its head.
KNOWN_ONE_UNIT_SINKS: tuple[str, ...] = ()


def _reached_only_by_lookup(name: str) -> bool:
    """Sprites a static resolver cannot reach, and the reason each one cannot.

    The person set is chosen from `personWalkDrawables` and its two head tables by
    kind, season and frame; the moons by phase; the sleigh by a conditional. None of
    them is ever named literally at a blit, so there is nothing to attribute -- which
    is why they are excused here rather than counted as agreeing.

    The three `road_*` sprites are a different case with the same symptom: nothing
    blits them at all. They are orphans, recorded as such in the registry and in
    `ROADMAP.md`, and they will leave this predicate when they are wired up or
    deleted.
    """
    return (
        name.startswith("person_")
        or name.startswith("moon_")
        or name.startswith("santa_sleigh_")
        or name in ("road_asphalt", "road_curb", "road_line")
    )


def entry(**overrides) -> dict:
    """A minimal well-formed registry entry, before the case under test edits it."""
    base = {
        "name": "test_sprite",
        "category": "house",
        "width": 210,
        "height": 210,
        "contentBox": [0, 0, 210, 210],
        "scale": "SCENE_UNITS",
        "tint": "TINTABLE",
        "usage": "referenced",
        "anchorRule": "CONTENT_BOTTOM_CENTRE",
        # In the sprite's own pixels, which is the unit the registry declares an
        # anchor in: 210x210 with full-bleed content puts the bottom centre at
        # (105, 210). It used to be [35, 70] -- the same point in local units --
        # which agreed with a comparison that was itself converting the wrong way.
        "anchor": [105.0, 210.0],
        "source": {"kind": "none", "reason": "test fixture"},
        "notes": "",
    }
    base.update(overrides)
    return base


def load_entries(entries: list[dict]) -> list[registry.SpriteSpec]:
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "sprites.json"
        path.write_text(
            json.dumps({"schemaVersion": registry.SCHEMA_VERSION, "sprites": entries}),
            encoding="utf-8",
        )
        return registry.load(path)


class Site:
    """Stand-in for a resolved call site, so a case can state one directly."""

    def __init__(self, scale=None, tint="TINTABLE", origin=None, file="Test.kt"):
        self.scale = scale
        self.tint = tint
        self.origin = origin
        self.file = file


class AnchorDerivationTest(unittest.TestCase):
    def test_bottom_centre_is_the_content_base_not_the_canvas_base(self):
        # 3px of transparent padding below the content is 1 unit of difference.
        # Reading the canvas instead of the content puts the object 1 unit into
        # the ground, which is exactly the class of error the declared box exists
        # to remove.
        anchor = registry.derive_anchor(
            "CONTENT_BOTTOM_CENTRE", (3, 3, 99, 141), (102, 144), 1 / 3
        )
        self.assertEqual(anchor, (17.0, 47.0))

    def test_scale_convention_changes_the_anchor_by_the_oversample(self):
        box, size = (0, 0, 210, 210), (210, 210)
        scene = registry.derive_anchor("CONTENT_BOTTOM_CENTRE", box, size, 1 / 3)
        canvas = registry.derive_anchor("CONTENT_BOTTOM_CENTRE", box, size, 1.0)
        self.assertEqual(scene, (35.0, 70.0))
        self.assertEqual(canvas, (105.0, 210.0))

    def test_sprite_centre_is_the_bitmap_centre_whatever_the_content_does(self):
        # It used to return None unless the content was centred in the bitmap too,
        # which conflated two statements. SPRITE_CENTRE says the sprite is placed by
        # its bitmap centre; it says nothing about where the ink sits inside it. A
        # crescent moon's content is off-centre by construction and is still placed
        # by the bitmap centre, and the old condition called four such declarations
        # broken.
        self.assertEqual(
            registry.derive_anchor("SPRITE_CENTRE", (6, 6, 186, 187), (192, 192)),
            (96.0, 96.0),
        )
        self.assertEqual(
            registry.derive_anchor("SPRITE_CENTRE", (6, 6, 186, 186), (192, 192)),
            (96.0, 96.0),
        )

    def test_an_anchor_is_derived_in_pixels_not_local_units(self):
        # Content boxes and anchors are both measured off the PNG, so the derivation
        # has to answer in the same unit the registry declares. Converting to local
        # units here and comparing against a pixel declaration made every 3x
        # oversampled sprite disagree with itself by a factor of three -- invisible
        # until defect D-4 let the comparison reach them.
        self.assertEqual(
            registry.derive_anchor("CONTENT_BOTTOM_CENTRE", (12, 12, 78, 252), (90, 252)),
            (45.0, 252.0),
        )

    def test_undetermined_derives_nothing(self):
        self.assertIsNone(
            registry.derive_anchor("UNDETERMINED", (0, 0, 10, 10), (10, 10), 1.0)
        )


class SchemaTest(unittest.TestCase):
    def test_an_undetermined_rule_may_not_carry_an_anchor(self):
        with self.assertRaises(registry.RegistryError):
            load_entries([entry(anchorRule="UNDETERMINED", anchorReason="none", anchor=[1.0, 2.0])])

    def test_an_undetermined_rule_needs_a_reason(self):
        with self.assertRaises(registry.RegistryError):
            load_entries([entry(anchorRule="UNDETERMINED", anchor=None)])

    def test_a_determined_rule_may_not_carry_a_reason(self):
        with self.assertRaises(registry.RegistryError):
            load_entries([entry(anchorReason="should not be here")])

    def test_an_empty_content_box_is_rejected(self):
        with self.assertRaises(registry.RegistryError):
            load_entries([entry(contentBox=[10, 10, 10, 40])])

    def test_schema_version_1_is_no_longer_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sprites.json"
            path.write_text(json.dumps({"schemaVersion": 1, "sprites": []}), encoding="utf-8")
            with self.assertRaises(registry.RegistryError):
                registry.load(path)


class RuntimeComparisonTest(unittest.TestCase):
    def check(self, entries, sizes, boxes):
        return registry.validate_against_runtime(
            load_entries(entries),
            runtime_names={e["name"] for e in entries},
            measured_sizes=sizes,
            measured_content_boxes=boxes,
            svg_dir=TOOL_ROOT / "sources/svg",
        )

    def test_a_content_box_off_by_one_pixel_fails(self):
        problems = self.check(
            [entry()], {"test_sprite": (210, 210)}, {"test_sprite": (0, 0, 210, 209)}
        )
        self.assertTrue(any("contentBox" in p for p in problems), problems)

    def test_a_matching_content_box_passes(self):
        problems = self.check(
            [entry()], {"test_sprite": (210, 210)}, {"test_sprite": (0, 0, 210, 210)}
        )
        self.assertEqual(problems, [])

    def test_an_anchor_off_by_one_unit_fails(self):
        problems = self.check(
            [entry(anchor=[35.0, 71.0])],
            {"test_sprite": (210, 210)},
            {"test_sprite": (0, 0, 210, 210)},
        )
        self.assertTrue(any("anchor" in p for p in problems), problems)

    def test_an_anchor_left_at_the_other_scale_convention_fails(self):
        # The whole of defect D-1 in one case: the artwork is unchanged and the
        # anchor is a real point on it, but recorded in the wrong convention. The
        # fixture now declares pixels, so the wrong convention is local units.
        problems = self.check(
            [entry(anchor=[35.0, 70.0])],
            {"test_sprite": (210, 210)},
            {"test_sprite": (0, 0, 210, 210)},
        )
        self.assertTrue(any("anchor" in p for p in problems), problems)


class CallSiteComparisonTest(unittest.TestCase):
    def check(self, entries, sites):
        return registry.validate_against_callsites(load_entries(entries), sites)

    def test_a_swapped_scale_convention_fails(self):
        problems, unresolved = self.check(
            [entry()],
            {"test_sprite": [Site(scale="CANVAS_PIXELS", origin=(-35.0, -70.0))]},
        )
        self.assertTrue(any("scale" in p for p in problems), problems)
        self.assertEqual(unresolved, {})

    def test_a_swapped_tint_class_fails(self):
        problems, _ = self.check(
            [entry()],
            {"test_sprite": [Site(scale="SCENE_UNITS", tint="FIXED_ART", origin=(-35.0, -70.0))]},
        )
        self.assertTrue(any("tint" in p for p in problems), problems)

    def test_an_origin_that_contradicts_the_anchor_fails(self):
        problems, _ = self.check(
            [entry()],
            {"test_sprite": [Site(scale="SCENE_UNITS", origin=(-35.0, -69.0))]},
        )
        self.assertTrue(any("origin" in p for p in problems), problems)

    def test_an_agreeing_call_site_passes(self):
        problems, unresolved = self.check(
            [entry()],
            {"test_sprite": [Site(scale="SCENE_UNITS", origin=(-35.0, -70.0))]},
        )
        self.assertEqual((problems, unresolved), ([], {}))

    def test_a_sprite_with_no_call_site_is_unresolved_not_passing(self):
        problems, unresolved = self.check([entry()], {})
        self.assertEqual(problems, [])
        self.assertIn("test_sprite", unresolved)

    def test_an_unresolvable_origin_is_unresolved_not_passing(self):
        problems, unresolved = self.check(
            [entry()], {"test_sprite": [Site(scale="SCENE_UNITS", origin=None)]}
        )
        self.assertEqual(problems, [])
        self.assertIn("test_sprite", unresolved)

    def test_an_unresolvable_scale_is_unresolved_not_passing(self):
        problems, unresolved = self.check(
            [entry()], {"test_sprite": [Site(scale=None, origin=(-35.0, -70.0))]}
        )
        self.assertEqual(problems, [])
        self.assertIn("test_sprite", unresolved)


class ResolverTest(unittest.TestCase):
    """The resolver against synthetic Kotlin, including what it must not read."""

    def resolve(self, source: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Renderer.kt"
            path.write_text(source, encoding="utf-8")
            return callsites.scan_file(path)

    def test_a_wrapper_binds_the_scale_its_body_passes(self):
        sites, _ = self.resolve(
            """
            private fun drawSprite(canvas: Canvas, resId: Int, x: Float, y: Float) {
                sprites.draw(canvas, resId, x, y, SpriteScale.SCENE_UNITS)
            }
            fun body(canvas: Canvas) {
                drawSprite(canvas, R.drawable.house_small_wall, -35f, -70f)
            }
            """
        )
        self.assertEqual(len(sites), 1)
        self.assertEqual(sites[0].scale, "SCENE_UNITS")
        self.assertEqual(sites[0].origin, (-35.0, -70.0))
        self.assertEqual(sites[0].tint, "FIXED_ART")

    def test_an_ordinary_drawing_function_is_not_mistaken_for_a_wrapper(self):
        # It takes a Canvas and contains exactly one blit, which is all a looser
        # rule would look at -- and treating it as a wrapper would swallow the
        # only call site the sprite has.
        sites, unattributed = self.resolve(
            """
            private fun drawCloud(canvas: Canvas, alpha: Int) {
                sprites.drawTinted(canvas, R.drawable.cloud_body, -245f, -150f,
                    SpriteScale.SCENE_UNITS, color, alpha)
            }
            """
        )
        self.assertEqual([s.sprite for s in sites], ["cloud_body"])
        self.assertEqual(unattributed, [])

    def test_a_sprite_from_a_lookup_table_is_unattributed(self):
        sites, unattributed = self.resolve(
            """
            fun body(canvas: Canvas) {
                val resId = personWalkDrawables[kindIdx][seasonIdx][frame]
                sprites.draw(canvas, resId, -50f, -95f, SpriteScale.SCENE_UNITS)
            }
            """
        )
        self.assertEqual(sites, [])
        self.assertEqual([u.expression for u in unattributed], ["resId"])

    def test_a_computed_origin_resolves_to_no_origin(self):
        sites, _ = self.resolve(
            """
            fun body(canvas: Canvas) {
                sprites.drawTinted(canvas, R.drawable.skyscraper_wall, -width / 2f, -height,
                    SpriteScale.SCENE_UNITS, wallColor)
            }
            """
        )
        self.assertEqual(len(sites), 1)
        self.assertIsNone(sites[0].origin)
        self.assertEqual(sites[0].unresolved_fields, ("origin",))

    def test_a_named_constant_origin_is_resolved(self):
        sites, _ = self.resolve(
            """
            const val STAR_SPRITE_ORIGIN_UNITS = -32f
            val STAR_SPRITE_SCALE = SpriteScale.SCENE_UNITS
            fun body(canvas: Canvas) {
                sprites.drawTinted(canvas, R.drawable.star_sparkle, STAR_SPRITE_ORIGIN_UNITS,
                    STAR_SPRITE_ORIGIN_UNITS, STAR_SPRITE_SCALE, color, alpha)
            }
            """
        )
        self.assertEqual(sites[0].origin, (-32.0, -32.0))
        self.assertEqual(sites[0].scale, "SCENE_UNITS")

    def test_an_identity_tint_is_read_as_fixed_art(self):
        # White multiplies to nothing, so the sprite keeps its own colours. The
        # tinted entry point is used only because `draw` has no alpha argument.
        sites, _ = self.resolve(
            """
            fun body(canvas: Canvas) {
                sprites.drawTinted(canvas, R.drawable.santa_sleigh_scene, -340f, -140f,
                    SpriteScale.CANVAS_PIXELS, 0xFFFFFFFF.toInt(), alpha)
            }
            """
        )
        self.assertEqual(sites[0].tint, "FIXED_ART")

    def test_a_real_colour_stays_tintable(self):
        sites, _ = self.resolve(
            """
            fun body(canvas: Canvas) {
                sprites.drawTinted(canvas, R.drawable.cloud_body, -245f, -150f,
                    SpriteScale.SCENE_UNITS, 0xFFAABBCC.toInt())
            }
            """
        )
        self.assertEqual(sites[0].tint, "TINTABLE")

    def test_a_commented_out_call_is_not_a_call_site(self):
        sites, unattributed = self.resolve(
            """
            // sprites.draw(canvas, R.drawable.house_small_wall, -35f, -70f, SpriteScale.SCENE_UNITS)
            /* sprites.draw(canvas, R.drawable.bar_wall, -45f, -55f, SpriteScale.SCENE_UNITS) */
            """
        )
        self.assertEqual((sites, unattributed), ([], []))


class ShippedSourcesTest(unittest.TestCase):
    """The resolver against the sources as they actually are.

    Coverage is pinned by naming what cannot be reached, not by a count: a bare
    number would have to be edited by whoever reduced the coverage, which is the
    opposite of a check.
    """

    def setUp(self):
        self.sites, self.unattributed = callsites.scan_sources(KOTLIN_SOURCES)
        self.specs = registry.load(REGISTRY_PATH)

    def test_the_only_unreadable_blits_are_the_known_runtime_lookups(self):
        # Every unreadable blit passes a sprite chosen at runtime rather than named
        # literally -- a lookup table index, a phase, a conditional. There is nothing
        # for a static resolver to attribute, and a sprite reached only this way is
        # reported as unresolved rather than counted as checked.
        #
        # This list grew when defect D-4 was fixed: `SceneObjectRenderer`'s sixty
        # call sites had never resolved at all, so its runtime lookups had never been
        # reached either.
        for site in self.unattributed:
            self.assertTrue(
                # rc5 renamed `driverRes`: `drawSeatedOccupant` is one blit serving both of a
                # car's seats, and which bust it draws is chosen at the two call sites in
                # `drawCar` out of the `personCarHeadSkinDrawables` table.
                site.expression == "occupantRes"
                or site.expression == "phaseSprite"
                or site.expression == "resId"
                # `scatterPiles` is one loop drawing either drift sprite, chosen by its
                # caller: `drawGroundPiles` passes `R.drawable.snow_pile` or
                # `R.drawable.leaf_pile` literally and the loop is written once rather
                # than twice. Same shape as the lookups above -- the sprite is named at
                # the call site, not at the blit.
                or site.expression == "drawable"
                or "R.drawable." in site.expression
                or "[" in site.expression,
                f"unreadable blit that is not a runtime lookup: {site.expression}",
            )

    def test_every_sprite_placed_by_its_own_anchor_is_blitted_where_that_anchor_says(self):
        # Only the sprites whose anchor predicts an origin. A PART_LOCAL sprite sits
        # wherever the drawing that contains it puts it, and a DECLARED_ATTACHMENT is
        # positioned by the joint it attaches to; comparing either against its own
        # anchor was comparing unrelated numbers, and it went unnoticed because the
        # file that blits nearly all of them did not resolve until D-4 was fixed.
        #
        # Three sprites are known to disagree by exactly one local unit and are
        # excused here rather than silently tolerated: see `KNOWN_ONE_UNIT_SINKS`.
        for spec in self.specs:
            if not spec.predicts_origin:
                continue
            sites = self.sites.get(spec.name, [])
            if not sites:
                self.assertTrue(
                    _reached_only_by_lookup(spec.name),
                    f"{spec.name} declares an anchor and nothing blits it",
                )
                continue
            if spec.name in KNOWN_ONE_UNIT_SINKS:
                continue
            for site in sites:
                if site.origin is None:
                    continue
                expected = (
                    -spec.anchor[0] * spec.units_per_pixel,
                    -spec.anchor[1] * spec.units_per_pixel,
                )
                for actual, want in zip(site.origin, expected):
                    self.assertAlmostEqual(
                        actual,
                        want,
                        delta=registry.ORIGIN_TOLERANCE,
                        msg=f"{spec.name} is blitted at an origin its anchor does not imply",
                    )

    def test_the_registry_agrees_with_the_sources_on_scale_and_tint(self):
        problems, _ = registry.validate_against_callsites(self.specs, self.sites)
        unexpected = [
            p for p in problems if not any(name in p for name in KNOWN_ONE_UNIT_SINKS)
        ]
        self.assertEqual(unexpected, [])

    def test_nothing_is_excused_from_the_anchor_comparison(self):
        """The excuse list is empty and adding to it needs a reason in writing."""
        self.assertEqual((), KNOWN_ONE_UNIT_SINKS)

    def test_the_one_unit_sinks_are_still_exactly_that(self):
        # The excused disagreements are pinned rather than waved through. Each of the
        # three is blitted one local unit above the ground line its own content bottom
        # would put it on -- consistently, across three unrelated sprites, which reads
        # as an authoring convention rather than drift. Correcting it means changing a
        # blit origin in the renderer, which this task is not allowed to do; the entry
        # in `ROADMAP.md` records it. If one of them ever moves by something other than
        # one unit, that is new and this test says so.
        problems, _ = registry.validate_against_callsites(self.specs, self.sites)
        sinking = [p for p in problems if any(n in p for n in KNOWN_ONE_UNIT_SINKS)]
        self.assertEqual(len(KNOWN_ONE_UNIT_SINKS), len(sinking), sinking)
        for spec in self.specs:
            if spec.name not in KNOWN_ONE_UNIT_SINKS:
                continue
            for site in self.sites.get(spec.name, []):
                self.assertAlmostEqual(
                    site.origin[1],
                    -spec.anchor[1] * spec.units_per_pixel - 1.0,
                    delta=registry.ORIGIN_TOLERANCE,
                    msg=f"{spec.name} no longer sinks by exactly one unit",
                )


if __name__ == "__main__":
    unittest.main()
