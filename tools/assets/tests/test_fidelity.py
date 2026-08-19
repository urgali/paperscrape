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

from paperscrape_assets import fidelity, fit, raster  # noqa: E402

RUNTIME = TOOL_ROOT.parent.parent / "app/src/main/res/drawable-nodpi"


def solid_square(size: int = 32, inset: int = 8, alpha: int = 255) -> np.ndarray:
    image = np.zeros((size, size, 4), dtype=np.uint8)
    image[inset:-inset, inset:-inset, :3] = 255
    image[inset:-inset, inset:-inset, 3] = alpha
    return image


def runtime_pixels(name: str) -> np.ndarray:
    with Image.open(RUNTIME / f"{name}.png") as image:
        return np.array(image.convert("RGBA"))


def render_rounded_rect(width: int, height: int, radius: float) -> np.ndarray:
    return raster.render_svg(fit.rounded_rect_svg(width, height, radius)).pixels


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

    `house_shared_planter` is one of the sprites whose 0.999 IoU gate failed
    while its geometry was in fact recovered. It is pinned here in both
    directions: the fitted radius passes, and the neighbouring grid value does
    not.
    """

    def test_fitted_radius_reproduces_the_shipped_planter(self):
        reference = runtime_pixels("house_shared_planter")
        result = fidelity.compare(
            "house_shared_planter", reference, render_rounded_rect(78, 18, 6)
        )
        self.assertEqual("EDGE_EQUIVALENT", result.verdict)
        self.assertEqual(0, result.interior_alpha_mismatch)

    def test_wrong_radius_does_not_reproduce_the_shipped_planter(self):
        reference = runtime_pixels("house_shared_planter")
        for radius in (3, 9):
            with self.subTest(radius=radius):
                result = fidelity.compare(
                    "house_shared_planter", reference, render_rounded_rect(78, 18, radius)
                )
                self.assertEqual("DIVERGENT", result.verdict)

    def test_low_iou_alone_does_not_reject_a_recovered_shape(self):
        """The sprites the IoU gate rejected still score below it.

        There were three until Phase 3.4: `house_small_planter` was a second copy
        of `house_shared_planter`, so it scored identically and is now gone.
        """
        for name, size, radius in (
            ("house_shared_planter", (78, 18), 6),
            ("road_line", (52, 8), 3.9),
        ):
            with self.subTest(name=name):
                result = fidelity.compare(
                    name, runtime_pixels(name), render_rounded_rect(*size, radius)
                )
                self.assertLess(result.alpha_iou, fidelity.IOU_REPORTING_FLOOR)
                self.assertEqual("EDGE_EQUIVALENT", result.verdict)


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
