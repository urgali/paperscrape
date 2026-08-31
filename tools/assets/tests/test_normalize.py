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
        pixels_sha256="",
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

    def test_a_raw_pixel_sprite_is_rounded_to_the_same_grid(self):
        """The grid is the canvas's, not the origin's.

        A `CANVAS_PIXELS` sprite writes its origin in pixels, so `unit` is 1 and its
        compensation converts one for one -- but `SpriteGeometryTest` requires every
        shipped canvas to be a whole multiple of the sprite grid whatever convention
        positions it. Rounding this one to its own pixel took `bird_body` to 88x21,
        off the grid on both axes.
        """
        self.assertEqual(
            (6, 6, 99, 120),
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
        # The window occupants: four busts on one canvas, chosen from a lookup table
        # and blitted through one origin. Individually each leaves margin; between
        # them they reach every edge, so the union has nothing to remove and
        # cropping them to their own boxes would move each occupant relative to the
        # others.
        #
        # This used to be stated with the moon phases, which say the same thing more
        # neatly. They are no longer usable here: D-10 excluded the whole
        # canvas-anchored sky set by decision, so `plan` now skips them and the case
        # would pass for the wrong reason.
        heads = {
            "person_man_summer_head_window": (0, 0, 180, 120),
            "person_woman_summer_head_window": (21, 0, 180, 162),
            "person_boy_summer_head_window": (0, 42, 159, 162),
            "person_girl_summer_head_window": (30, 0, 180, 162),
        }
        measurements = {n: measurement(n, (180, 162), b) for n, b in heads.items()}
        plans = normalize.plan(
            measurements, dict.fromkeys(heads, "SCENE_UNITS"), set(heads)
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


class TrailingCropTest(unittest.TestCase):
    """The crop that costs nothing, and the three ways it could stop being free.

    The whole claim of `normalize.trailing` is that pixel (0,0) does not move, so
    no origin, anchor or content box needs to move with it. Each test below breaks
    one leg of that: a box that no longer starts at zero, a canvas that leaves the
    grid, and a sprite whose anchor is its canvas rather than its drawing.
    """

    def plan_for(self, size, box, scale="SCENE_UNITS", rule="PART_LOCAL"):
        plans = normalize.plan(
            {"s": measurement("s", size, box)}, {"s": scale}, {"s"}
        )
        return normalize.trailing_plan(plans, {"s": rule})

    def test_the_crop_never_moves_the_bitmaps_own_origin(self):
        (item,) = self.plan_for((120, 120), (12, 12, 90, 90))
        self.assertEqual(0, item.box[0])
        self.assertEqual(0, item.box[1])
        self.assertEqual((0.0, 0.0), item.compensation)

    def test_the_retained_canvas_stays_on_the_grid(self):
        """Rounded outward, so a content edge one pixel past the grid keeps a row."""
        (item,) = self.plan_for((120, 120), (0, 0, 91, 91))
        self.assertEqual((93, 93), item.new_size)

    def test_the_grid_applies_to_raw_pixel_sprites_too(self):
        """`SpriteGeometryTest` makes no exception for `CANVAS_PIXELS`."""
        (item,) = self.plan_for((90, 42), (0, 0, 88, 26), scale="CANVAS_PIXELS")
        self.assertEqual((90, 27), item.new_size)

    def test_a_canvas_anchored_sprite_is_left_alone(self):
        """`SPRITE_CENTRE` moves when the canvas does, even if no drawn pixel does."""
        self.assertEqual([], self.plan_for((120, 120), (12, 12, 90, 90), rule="SPRITE_CENTRE"))

    def test_a_sprite_with_no_trailing_padding_is_not_a_target(self):
        self.assertEqual([], self.plan_for((120, 120), (12, 12, 120, 120)))

    def test_the_shipped_set_has_no_trailing_padding_left(self):
        specs = registry.load(REGISTRY_PATH)
        measurements = {m.name: m for m in inventory.measure_directory(RUNTIME_DIR)}
        referenced = set()
        for path in KOTLIN_SOURCES.rglob("*.kt"):
            for chunk in path.read_text(encoding="utf-8").split("R.drawable.")[1:]:
                end = 0
                while end < len(chunk) and (chunk[end].isalnum() or chunk[end] == "_"):
                    end += 1
                referenced.add(chunk[:end])
        plans = normalize.plan(
            measurements, {s.name: s.scale for s in specs}, referenced
        )
        remaining = normalize.trailing_plan(
            normalize.pending(plans), {s.name: s.anchor_rule for s in specs}
        )
        self.assertEqual(
            [], [item.key for item in remaining],
            "these targets regained padding on the right or bottom, which "
            "`normalize --apply-trailing` removes with nothing to compensate",
        )


#: How many normalisation targets still carry padding that only a compensated crop
#: can remove.
#:
#: The V2 asset library never went through Phase 3.3's padding pass. See the test
#: below for why this is a recorded state rather than a failure.
#:
#: Rose from 35 to 40 in v76.12 with the five roof snow caps, and deliberately: each
#: is authored on the canvas of the roof it covers, so its blit origin is that roof's
#: origin plus a stated crest rather than a number of its own. Trimming them to their
#: own content would buy a few kilobytes and cost that derivation.
#:
#: **Now zero, which is what closing D-10 means.** The trailing padding went first,
#: with no origin to compensate because pixel (0,0) never moved. The leading padding
#: followed in the same change as its compensation: 34 targets cropped, every one of
#: their blit origins moved by the trim, and every ink pixel verified to land on the
#: coordinate it had before. What remains is `EXCLUSIONS`, which is a list of
#: decisions rather than a backlog -- the canvas-anchored sky sprites, whose origin
#: constants would have to be split per sprite, and the two palm fronds.
KNOWN_PENDING_CROP_COUNT = 0


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
        # **Empty, and that is the whole of D-10.** Phase 3.3 normalised the set that
        # existed then; the V2 asset library replaced almost all of it and went
        # through the same pass only now. Nothing in scope carries removable padding
        # any more, so this reads as a guard rather than as a record: a new sprite
        # that ships with padding, or a crop that is undone, fails here.
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
