"""Tests for the padding and grid normalisation rule.

The rule has one job that matters -- crop away what is not drawn, and leave the
composed result identical -- and two ways to get it wrong that no picture would
show. Cropping to the measured box makes the origin compensation fractional,
which the blitter turns back into a sub-pixel position. Cropping each member of
a lookup group to its own box moves the members relative to each other, which is
a visible jitter in a walk cycle and nothing at all in a still frame.

So the cases here are near misses rather than obvious errors: a box one pixel
off the grid, a group whose members disagree by one unit, a sprite that should
not be touched at all. A test that only rejects nonsense would have passed on
both of the real mistakes.

Run from `tools/assets/`:

    python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

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

UNIT = normalize.SPRITE_PIXELS_PER_UNIT


def measurement(name: str, size: tuple[int, int], box: tuple[int, int, int, int]):
    """A measurement carrying only the fields the planner reads."""
    width, height = size
    return inventory.SpriteMeasurement(
        name=name,
        width=width,
        height=height,
        mode="RGBA",
        has_alpha_channel=True,
        file_bytes=0,
        decoded_bytes=width * height * 4,
        content_bbox=box,
        content_width=box[2] - box[0],
        content_height=box[3] - box[1],
        transparent_padding_bytes=0,
        transparent_padding_fraction=0.0,
        opaque_rgb_count=1,
        distinct_colour_count=2,
        fully_opaque=False,
        on_grid=True,
        sha256="",
    )


class NormalisedBoxTest(unittest.TestCase):
    """The rounding, which is what keeps the compensation an integer."""

    def test_a_box_already_on_the_grid_is_left_alone(self):
        self.assertEqual(
            (6, 6, 96, 120),
            normalize.normalised_box([(6, 6, 96, 120)], (120, 126), UNIT),
        )

    def test_a_box_off_the_grid_is_rounded_outward_never_inward(self):
        # 7,8 -> 6,6 and 97,119 -> 99,120: every edge moves away from the content,
        # so no drawn pixel can be cropped by the rounding.
        self.assertEqual(
            (6, 6, 99, 120),
            normalize.normalised_box([(7, 8, 97, 119)], (120, 126), UNIT),
        )

    def test_rounding_never_leaves_the_canvas(self):
        self.assertEqual(
            (0, 0, 120, 126),
            normalize.normalised_box([(1, 1, 119, 125)], (120, 126), UNIT),
        )

    def test_a_raw_pixel_sprite_needs_no_rounding_at_all(self):
        self.assertEqual(
            (7, 8, 97, 119),
            normalize.normalised_box([(7, 8, 97, 119)], (120, 126), 1),
        )

    def test_the_compensation_is_an_exact_integer_number_of_units(self):
        # The whole reason for rounding outward: the blitter multiplies the origin
        # by the same unit again, so a compensation of 17/3 comes back as 17.000002
        # and lands the sprite on a sub-pixel position.
        for left in range(0, 60):
            box = normalize.normalised_box([(left, 0, 90, 90)], (90, 90), UNIT)
            plan = normalize.Normalisation(
                key="probe", members=("probe",), unit=UNIT,
                size=(90, 90), box=box, origin_site=None,
            )
            self.assertEqual(
                plan.compensation[0],
                int(plan.compensation[0]),
                f"a content box starting at {left} gives a fractional compensation",
            )


class GroupTest(unittest.TestCase):
    """The union, which is what keeps the members of a lookup group registered."""

    FRAMES = {
        "person_a_walk0": (116, 65, 184, 248),
        "person_a_walk1": (116, 65, 184, 248),
        "person_a_walk2": (107, 65, 197, 248),  # the mid-stride frame, wider both ways
        "person_a_walk3": (116, 65, 184, 248),
    }

    def _plan(self):
        measurements = {
            name: measurement(name, (300, 300), box) for name, box in self.FRAMES.items()
        }
        scales = dict.fromkeys(self.FRAMES, "SCENE_UNITS")
        return normalize.plan(measurements, scales, set(self.FRAMES))

    def test_the_frames_of_a_walk_cycle_form_one_group(self):
        plans = self._plan()
        self.assertEqual(1, len(plans))
        self.assertEqual("person_walk", plans[0].key)
        self.assertEqual(tuple(sorted(self.FRAMES)), plans[0].members)

    def test_every_member_is_cropped_by_the_same_rectangle(self):
        plan = self._plan()[0]
        # The union covers the widest frame, so the narrower ones keep a margin.
        # That margin is what holds them in place relative to each other.
        self.assertEqual((105, 63, 198, 249), plan.box)
        self.assertEqual((93, 186), plan.new_size)

    def test_a_per_frame_crop_would_move_the_frames_against_each_other(self):
        # Not the behaviour under test -- the behaviour being ruled out. Cropping
        # each frame to its own box shifts walk2's own origin 3 units from the
        # others', and one shared origin literal cannot absorb two different shifts.
        own = {
            name: normalize.normalised_box([box], (300, 300), UNIT)
            for name, box in self.FRAMES.items()
        }
        shifts = {box[0] // UNIT for box in own.values()}
        self.assertEqual(
            {35, 38}, shifts,
            "the frames were expected to disagree; if they no longer do, this test "
            "has stopped exercising the case the group rule exists for",
        )

    def test_a_group_whose_members_fill_the_canvas_is_not_cropped(self):
        # The moon phases: each is the same disc lit differently, so individually
        # they carry a lot of padding and together they carry none. Cropping them
        # by their own boxes would make the moon move as it waxes.
        phases = {
            "moon_full": (0, 0, 240, 240),
            "moon_gibbous": (43, 0, 240, 240),
            "moon_half": (117, 0, 240, 240),
            "moon_crescent": (95, 0, 240, 240),
        }
        measurements = {n: measurement(n, (240, 240), b) for n, b in phases.items()}
        plans = normalize.plan(
            measurements, dict.fromkeys(phases, "CANVAS_PIXELS"), set(phases)
        )
        self.assertEqual(1, len(plans))
        self.assertTrue(plans[0].is_noop)
        self.assertEqual([], normalize.pending(plans))

    def test_members_of_a_group_must_share_a_canvas(self):
        measurements = {
            "person_a_walk0": measurement("person_a_walk0", (300, 300), (10, 10, 20, 20)),
            "person_a_walk1": measurement("person_a_walk1", (300, 288), (10, 10, 20, 20)),
        }
        with self.assertRaises(ValueError):
            normalize.plan(
                measurements,
                dict.fromkeys(measurements, "SCENE_UNITS"),
                set(measurements),
            )


class ScopeTest(unittest.TestCase):
    """What the planner refuses to touch, and why it refuses."""

    def test_an_excluded_sprite_is_never_planned(self):
        name = "palmtree_fronds"
        self.assertIn(name, normalize.excluded_names())
        measurements = {name: measurement(name, (102, 176), (0, 70, 102, 176))}
        plans = normalize.plan(measurements, {name: "SCENE_UNITS"}, {name})
        self.assertEqual([], plans)

    def test_every_exclusion_states_a_reason(self):
        for exclusion in normalize.EXCLUSIONS:
            self.assertTrue(exclusion.reason.strip(), f"{exclusion.name} has no reason")

    def test_a_sprite_no_call_site_references_is_left_alone(self):
        # There is no origin to compensate, so cropping it would be a change with
        # nothing keeping it correct.
        name = "house_window"
        measurements = {name: measurement(name, (180, 36), (14, 0, 167, 36))}
        self.assertEqual([], normalize.plan(measurements, {name: "SCENE_UNITS"}, set()))

    def test_the_unit_follows_the_scale_convention(self):
        self.assertEqual(UNIT, normalize.unit_for("SCENE_UNITS"))
        self.assertEqual(1, normalize.unit_for("CANVAS_PIXELS"))


#: How many normalisation targets still carry removable padding.
#:
#: The V2 asset library never went through Phase 3.3's padding pass. See the test
#: below for why this is a recorded state rather than a failure.
#:
#: Rose from 35 to 40 in v76.12 with the five roof snow caps, and deliberately: each
#: is authored on the canvas of the roof it covers, so its blit origin is that roof's
#: origin plus a stated crest rather than a number of its own. Trimming them to their
#: own content would buy a few kilobytes and cost that derivation.
KNOWN_PENDING_CROP_COUNT = 40


class ShippedSetTest(unittest.TestCase):
    """The invariant on what actually ships, not on a fixture."""

    def setUp(self):
        self.specs = registry.load(REGISTRY_PATH)
        self.measurements = {
            m.name: m for m in inventory.measure_directory(RUNTIME_DIR)
        }
        self.referenced = set()
        for path in KOTLIN_SOURCES.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            for chunk in text.split("R.drawable.")[1:]:
                end = 0
                while end < len(chunk) and (chunk[end].isalnum() or chunk[end] == "_"):
                    end += 1
                self.referenced.add(chunk[:end])

    def test_nothing_in_scope_still_carries_removable_padding(self):
        plans = normalize.plan(
            self.measurements,
            {s.name: s.scale for s in self.specs},
            self.referenced,
        )
        pending = normalize.pending(plans)
        # **Not empty, and deliberately so.** Phase 3.3 normalised the set that
        # existed then; the V2 asset library replaced almost all of it and was never
        # put through the same pass, so its sprites still carry croppable transparent
        # padding -- 2.84 MB of the decoded total, by `inventory`.
        #
        # Cropping is not a standalone change: every crop shifts the sprite's content
        # inside its own box, so each one needs its blit origin compensated in the
        # same commit, and a mistake there is a visibly misplaced sprite. That is a
        # task with a device look attached, recorded in `ROADMAP.md`, not something to
        # slip into a tooling fix.
        #
        # Pinned as a count rather than waved through: cropping some of them, or
        # adding a new padded sprite, both show up here.
        self.assertEqual(
            KNOWN_PENDING_CROP_COUNT,
            len(pending),
            "the set of croppable targets changed; if that was intended, update "
            "KNOWN_PENDING_CROP_COUNT and compensate every origin you cropped",
        )

    def test_the_registry_still_describes_the_shipped_pixels(self):
        # The crop and the declaration are one change: this is the assertion that
        # fails if a PNG is normalised and the manifest is not.
        for spec in self.specs:
            measured = self.measurements[spec.name]
            self.assertEqual(
                (spec.width, spec.height), (measured.width, measured.height),
                f"{spec.name}: registry and PNG disagree on the canvas",
            )
            self.assertEqual(
                spec.content_box, measured.content_bbox,
                f"{spec.name}: registry and PNG disagree on the content box",
            )


if __name__ == "__main__":
    unittest.main()
