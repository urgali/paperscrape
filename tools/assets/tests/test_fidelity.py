"""Tests for the fidelity criterion.

A comparison that returns `EDGE_EQUIVALENT` for everything would be worse than no
comparison at all: it would put a verdict in `RELEASE_HISTORY.md` that asserts
nothing. These tests exist to show the criterion fails when it should, which is
the same standard `AI_PROJECT_RULES.md` section 12.11 applies to the Kotlin
suite.

The cases that matter are the near misses. A reconstruction that is obviously
wrong is easy to reject; the ones worth pinning are a shape displaced by a single
pixel, a corner radius one grid unit off, and a fill colour off by one -- each of
which a permissive metric would wave through.

Run from `tools/assets/`:

    python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import fidelity, fit, inventory, raster, registry  # noqa: E402

RUNTIME = TOOL_ROOT.parent.parent / "app/src/main/res/drawable-nodpi"
SVG_DIR = TOOL_ROOT / "sources/svg"
REGISTRY_PATH = TOOL_ROOT / "sources/sprites.json"


def solid_square(size: int = 32, inset: int = 8, alpha: int = 255) -> np.ndarray:
    image = np.zeros((size, size, 4), dtype=np.uint8)
    image[inset:-inset, inset:-inset, :3] = 255
    image[inset:-inset, inset:-inset, 3] = alpha
    return image


def runtime_pixels(name: str) -> np.ndarray:
    with Image.open(RUNTIME / f"{name}.png") as image:
        return np.array(image.convert("RGBA"))


def source_pixels(name: str) -> np.ndarray:
    """The sprite as the pinned toolchain renders its committed SVG source."""
    return raster.render_svg_file(SVG_DIR / f"{name}.svg").pixels


def render_rounded_rect(width: int, height: int, radius: float, fill: str = "#ffffff") -> np.ndarray:
    return raster.render_svg(fit.rounded_rect_svg(width, height, radius, fill=fill)).pixels


class VerdictTest(unittest.TestCase):
    def test_identical_input_is_pixel_identical(self):
        square = solid_square()
        result = fidelity.compare("square", square, square.copy())
        self.assertEqual("PIXEL_IDENTICAL", result.verdict)
        self.assertEqual(0, result.differing_pixels)

    def test_edge_only_difference_is_edge_equivalent(self):
        """Disagreement confined to partially covered pixels is not a shape change."""
        reference = solid_square()
        reference[8, 8:24, 3] = 128  # a partially covered top edge
        candidate = reference.copy()
        candidate[8, 8:24, 3] = 141  # the same edge, resolved slightly differently

        result = fidelity.compare("square", reference, candidate)
        self.assertEqual("EDGE_EQUIVALENT", result.verdict)
        self.assertEqual(0, result.interior_alpha_mismatch)
        self.assertTrue(result.boundary_confined)

    def test_one_pixel_displacement_is_divergent(self):
        reference = solid_square()
        candidate = np.roll(reference, 1, axis=1)
        result = fidelity.compare("square", reference, candidate)
        self.assertEqual("DIVERGENT", result.verdict)
        self.assertGreater(result.interior_alpha_mismatch, 0)

    def test_growth_into_empty_space_is_divergent(self):
        """A bulge into transparent space is not on anybody's antialiased edge."""
        reference = solid_square()
        candidate = reference.copy()
        candidate[4, 12:20, :3] = 255
        candidate[4, 12:20, 3] = 90
        result = fidelity.compare("square", reference, candidate)
        self.assertEqual("DIVERGENT", result.verdict)
        self.assertFalse(result.boundary_confined)

    def test_off_by_one_fill_colour_is_divergent(self):
        reference = solid_square()
        candidate = reference.copy()
        candidate[..., 0] = np.where(candidate[..., 3] == 255, 254, candidate[..., 0])
        result = fidelity.compare("square", reference, candidate)
        self.assertEqual("DIVERGENT", result.verdict)
        self.assertEqual(1, result.max_rgb_diff_where_opaque)

    def test_size_mismatch_is_divergent(self):
        result = fidelity.compare("square", solid_square(32), solid_square(30, inset=7))
        self.assertEqual("DIVERGENT", result.verdict)
        self.assertFalse(result.size_match)


class RecoveredGeometryTest(unittest.TestCase):
    """The criterion applied to the real sprites, including the near misses.

    `house_large_trim` is a full-canvas rounded rectangle in the shipped library,
    so `fit` determines it completely: one free parameter, swept exhaustively.
    It is pinned in both directions -- the recovered radius reproduces the sprite,
    the neighbouring grid values do not.

    These cases named `house_shared_planter` and `road_line` at a 78x18 radius 6
    and a 52x8 radius 3.9 until the V2 redesign. Both sprites were plain rounded
    rectangles when the assertions were written; V2 replaced the planter with a
    box carrying three foliage circles and re-authored `road_line` at 54x9, and
    the assertions were never re-derived against the artwork that actually ships.
    They are not a rasteriser matter: a white rectangle compared against a
    coloured three-element drawing is a different picture, not a different
    antialiasing decision.
    """

    #: The trim's radius, in pixels, as its committed source declares it: `rx="3"`
    #: in a viewBox authored at `SPRITE_PIXELS_PER_UNIT` pixels per scene unit.
    TRIM_RADIUS = 3 * inventory.SPRITE_PIXELS_PER_UNIT

    def test_the_shipped_trims_radius_is_recovered_from_its_pixels(self):
        fitted = fit.fit_rounded_rect("house_large_trim", runtime_pixels("house_large_trim"))
        self.assertEqual((450, 18), (fitted.width, fitted.height))
        self.assertEqual(float(self.TRIM_RADIUS), fitted.snapped_radius)
        self.assertLess(abs(fitted.best_radius - self.TRIM_RADIUS), 0.5)

    def test_fitted_radius_reproduces_the_shipped_trim(self):
        reference = runtime_pixels("house_large_trim")
        result = fidelity.compare(
            "house_large_trim", reference, render_rounded_rect(450, 18, self.TRIM_RADIUS)
        )
        self.assertEqual("EDGE_EQUIVALENT", result.verdict)
        self.assertEqual(0, result.interior_alpha_mismatch)

    def test_wrong_radius_does_not_reproduce_the_shipped_trim(self):
        reference = runtime_pixels("house_large_trim")
        step = inventory.SPRITE_PIXELS_PER_UNIT
        for radius in (self.TRIM_RADIUS - step, self.TRIM_RADIUS + step):
            with self.subTest(radius=radius):
                result = fidelity.compare(
                    "house_large_trim", reference, render_rounded_rect(450, 18, radius)
                )
                self.assertEqual("DIVERGENT", result.verdict)

    def test_low_iou_alone_does_not_reject_a_recovered_shape(self):
        """Small sprites score far below the reporting floor and are still right.

        Each of these reproduces its shipped PNG with no solid/empty conflict, an
        exact fill and every differing pixel on the reference's own antialiased
        edge, while scoring under `IOU_REPORTING_FLOOR`. That is the failure mode
        of an area ratio applied to a boundary phenomenon, not of the
        reconstruction: `bunny_innerear` is 48x48 and `penguin_feet` is 60x12, so
        their antialiased band is a large share of their area, and a single
        absolute IoU threshold would ask them for precision it never asks of a
        270x450 wall.
        """
        for name in ("bunny_innerear", "pumpkin_stem", "penguin_feet"):
            with self.subTest(name=name):
                result = fidelity.compare(name, runtime_pixels(name), source_pixels(name))
                self.assertLess(result.alpha_iou, fidelity.IOU_REPORTING_FLOOR)
                self.assertEqual("EDGE_EQUIVALENT", result.verdict)


class ShippedAgainstSourceTest(unittest.TestCase):
    """What the pinned toolchain does and does not reproduce, across the set.

    The shipped PNGs were rendered by the V2 library's own rasteriser, and the
    pinned one resolves partially covered pixels differently. That is defect D-7,
    and this class is what bounds it rather than leaving it as an adjective.

    The bound that matters is a *shape* bound, and it is criterion-independent:
    no sprite has a pixel that is solid in one rendering and empty in the other,
    and no single pixel's coverage moves by as much as half. Everything the two
    rasterisers disagree about is therefore the resolution of a boundary pixel,
    which is invisible once the sprite is blitted. A geometry difference -- a
    displaced shape, a wrong radius, a re-authored drawing -- cannot satisfy
    either condition, which is what makes them worth asserting.
    """

    #: Half of 255, rounded down: a disagreement this large would mean the two
    #: rasterisers do not agree about which side of a pixel's centre an edge falls
    #: on, which is a geometry difference rather than a coverage one. The measured
    #: worst case across the set is 121, on a single pixel of `rainbow_arc`'s
    #: shallowest stroke edge.
    MAX_COVERAGE_DELTA = 127

    @classmethod
    def setUpClass(cls):
        cls.results = [
            fidelity.compare(spec.name, runtime_pixels(spec.name), source_pixels(spec.name))
            for spec in registry.load(REGISTRY_PATH)
            if spec.has_svg_source
        ]

    def test_every_sprite_with_a_source_is_covered(self):
        self.assertEqual(122, len(self.results))

    def test_no_shipped_sprite_differs_from_its_source_in_shape(self):
        for result in self.results:
            with self.subTest(name=result.name):
                self.assertTrue(result.size_match)
                self.assertEqual(0, result.interior_alpha_mismatch)

    def test_no_pixels_coverage_moves_by_half(self):
        for result in self.results:
            with self.subTest(name=result.name):
                self.assertLessEqual(result.max_alpha_diff, self.MAX_COVERAGE_DELTA)


class RasteriserTest(unittest.TestCase):
    def test_toolchain_matches_the_pinned_fingerprint(self):
        probe = raster.probe()
        self.assertTrue(
            probe["matches_expected"],
            "rasteriser fingerprint moved; every fidelity figure needs re-measuring",
        )

    def test_rendering_is_repeatable_within_a_run(self):
        first = raster.render_svg(raster.PROBE_SVG)
        second = raster.render_svg(raster.PROBE_SVG)
        self.assertEqual(first.sha256, second.sha256)

    def test_zero_radius_emits_a_plain_rectangle(self):
        self.assertNotIn("rx=", fit.rounded_rect_svg(10, 10, 0))
        self.assertIn('rx="6"', fit.rounded_rect_svg(10, 10, 6))


if __name__ == "__main__":
    unittest.main()
