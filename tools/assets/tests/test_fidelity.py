"""Tests for the fidelity criterion.

A comparison that returns `EDGE_EQUIVALENT` for everything would be worse than no
comparison at all: it would put a verdict in a report that asserts
nothing. These tests exist to show the criterion fails when it should, which is
the standard every test of the project meets: a new test is not trusted until
it has been shown to fail.

The cases that matter are the near misses. A reconstruction that is obviously
wrong is easy to reject; the ones worth pinning are a shape displaced by a single
pixel, a corner radius one grid unit off, and a fill colour off by one -- each of
which a permissive metric would wave through.

Run from `tools/assets/`:

    python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

import sys
import struct
import unittest
import zlib
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


def source_pixels(source_file: str) -> np.ndarray:
    """The sprite as the pinned toolchain renders its committed SVG source.

    Takes the registry's own `source.file` rather than assuming `<name>.svg`. The two coincide for
    every sprite today, but the registry is what says where a sprite's source is, and assuming the
    name is a shortcut that breaks the day the two differ.
    """
    return raster.render_svg_file(SVG_DIR / source_file).pixels


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

    `car_lamp_front` is a full-canvas rounded rectangle in the shipped library --
    one `<rect>` with `rx="1.5"` and nothing else in the document -- so `fit`
    determines it completely: one free parameter, swept exhaustively. It is pinned
    in both directions: the recovered radius reproduces the sprite, and the
    neighbouring grid values do not.

    The case has to stay on a sprite that ships as a single full-canvas `<rect>`
    whose radius the source declares: a white rectangle compared against a drawing
    of several elements is a different picture, not a different antialiasing
    decision.
    """

    #: The lens's radius, in pixels, as its committed source declares it: `rx="1.5"`
    #: in a viewBox authored at `SPRITE_PIXELS_PER_UNIT` pixels per scene unit.
    LENS_RADIUS = 1.5 * inventory.SPRITE_PIXELS_PER_UNIT

    #: The sprite these cases are fitted against: one `<rect>`, full canvas, declared radius.
    LENS = "car_lamp_front"

    def test_the_shipped_trims_radius_is_recovered_from_its_pixels(self):
        fitted = fit.fit_rounded_rect(self.LENS, runtime_pixels(self.LENS))
        self.assertEqual((18, 12), (fitted.width, fitted.height))
        self.assertLess(abs(fitted.best_radius - self.LENS_RADIUS), 0.5)
        # **The snapped radius is not asserted equal to the declared one.** Snapping puts the
        # recovered radius on the sprite grid, and this lens is `rx="1.5"` -- half a unit -- so its
        # 4.5 px snaps to 6.0, and an assertion that they are equal would be asserting the snap is
        # wrong. What the test is about is the recovery, which the line above pins to within half a
        # pixel.
        self.assertEqual(6.0, fitted.snapped_radius)

    def test_fitted_radius_reproduces_the_shipped_trim(self):
        """Reproduced from the sprite's own source form, not from a 1:1 rounded rectangle.

        `rounded_rect_svg` writes a document at one pixel per user unit; every committed
        source is authored at `SPRITE_PIXELS_PER_UNIT` and scaled by its viewBox. The two
        describe the same rectangle and the rasteriser resolves their curves a hair
        differently, which matters because the shipped PNGs are exact renders of their own
        sources. The radius is the one `fit` recovers; only the document form it is rendered
        in is the source's.
        """
        result = fidelity.compare(
            self.LENS, runtime_pixels(self.LENS), source_pixels(f"{self.LENS}.svg")
        )
        self.assertIn(result.verdict, ("PIXEL_IDENTICAL", "EDGE_EQUIVALENT"))
        self.assertEqual(0, result.interior_alpha_mismatch)

    def test_wrong_radius_does_not_reproduce_the_shipped_trim(self):
        reference = runtime_pixels(self.LENS)
        step = inventory.SPRITE_PIXELS_PER_UNIT
        for radius in (self.LENS_RADIUS - step, self.LENS_RADIUS + step):
            with self.subTest(radius=radius):
                result = fidelity.compare(
                    self.LENS, reference, render_rounded_rect(18, 12, radius)
                )
                self.assertEqual("DIVERGENT", result.verdict)

    def test_low_iou_alone_does_not_reject_a_recovered_shape(self):
        """A near-miss radius on a small sprite scores under the floor without being wrong.

        The case is made on a constructed pair: every shipped sprite is byte-exact against its
        own source and scores 1.0, so none of them can show it.

        A 60x12 pill at radius 6 against the same pill at 5.9 or 5.8 is the same shape recovered to
        within a tenth of a unit: no solid/empty conflict anywhere, every difference on the
        antialiased edge. It still scores 0.9976, because on a sprite that small the edge is a
        large share of the area -- which is the failure mode of an area ratio applied to a
        boundary phenomenon, and the reason `IOU_REPORTING_FLOOR` reports rather than gates.
        """
        reference = render_rounded_rect(60, 12, 6)
        for radius in (5.8, 5.9):
            with self.subTest(radius=radius):
                result = fidelity.compare("pill", reference, render_rounded_rect(60, 12, radius))
                self.assertLess(result.alpha_iou, fidelity.IOU_REPORTING_FLOOR)
                self.assertEqual(0, result.interior_alpha_mismatch)


class ShippedAgainstSourceTest(unittest.TestCase):
    """What the pinned toolchain does and does not reproduce, across the set.

    `reports/fidelity.json` records every compared sprite as `PIXEL_IDENTICAL`, a
    largest alpha difference of 0, so the shape bounds hold with room to spare and
    stay as the guard against a rasteriser that resolves partially covered pixels
    differently.

    Two of the bounds are *shape* bounds, and criterion-independent: no sprite has
    a pixel that is solid in one rendering and empty in the other, and no single
    pixel's coverage moves by as much as half. A geometry difference -- a displaced
    shape, a wrong radius, a re-authored drawing -- cannot satisfy either. The third
    holds the colour as well: every sprite is its source pixel for pixel.
    """

    #: Half of 255, rounded down: a disagreement this large would mean the two
    #: rasterisers do not agree about which side of a pixel's centre an edge falls
    #: on, which is a geometry difference rather than a coverage one.
    #: `reports/fidelity.json` records 0 across the set.
    MAX_COVERAGE_DELTA = 127

    @classmethod
    def setUpClass(cls):
        cls.results = [
            fidelity.compare(spec.name, runtime_pixels(spec.name), source_pixels(spec.source_file))
            for spec in registry.load(REGISTRY_PATH)
            if spec.has_svg_source
        ]

    def test_every_sprite_with_a_source_is_covered(self):
        # Every registry entry with an SVG source. The sprites without one say why in their
        # `source.reason`: a building piece is drawn by `tools/assets/buildings/build_neighbourhood.py`
        # and decomposed into a fixed layer and its weight masks, a seated bust is fixed art plus
        # region masks, and the carrying frames are recolours of a base that is generated and not
        # shipped. A drawing added or removed with an SVG of its own moves this count.
        self.assertEqual(130, len(self.results))

    def test_no_shipped_sprite_differs_from_its_source_in_shape(self):
        for result in self.results:
            with self.subTest(name=result.name):
                self.assertTrue(result.size_match)
                self.assertEqual(0, result.interior_alpha_mismatch)

    def test_no_pixels_coverage_moves_by_half(self):
        for result in self.results:
            with self.subTest(name=result.name):
                self.assertLessEqual(result.max_alpha_diff, self.MAX_COVERAGE_DELTA)

    def test_every_shipped_sprite_is_its_source_pixel_for_pixel(self):
        """The colour too, not only the shape: every channel of every pixel.

        The two bounds above hold the shape and nothing else, so a fill changed in a source
        SVG and never exported -- `taxi_checker.svg`'s `#2B2A33` made `#2B2A34` -- passes them,
        and `validate` and every other test with them; only `compare`, outside the routine,
        says DIVERGENT. Every sprite with a source is rendered pixel-identical to what ships,
        so that is the bound. If the pinned rasteriser itself ever moves, the probe
        (`RasteriserTest`) fails beside this and every figure is re-measured.
        """
        for result in self.results:
            with self.subTest(name=result.name):
                self.assertEqual(
                    "PIXEL_IDENTICAL", result.verdict,
                    f"{result.name}: {result.differing_pixels} pixels differ from its source, "
                    f"colour by up to {result.max_rgb_diff_where_opaque} where both are opaque. "
                    "Export the source again (its generator, or `render` and a copy) or put the source back.",
                )


class RasteriserTest(unittest.TestCase):
    def test_toolchain_matches_the_pinned_fingerprint(self):
        probe = raster.probe()
        self.assertTrue(
            probe["matches_expected"],
            "rasteriser fingerprint moved; every fidelity figure needs re-measuring",
        )

    def test_the_fingerprint_is_the_pixels_and_not_the_compressed_file(self):
        """The bug this pins: a fingerprint of the compressed file instead of the pixels.

        Hashing ``png_bytes`` makes the fingerprint depend on the PNG encoder and on
        the zlib build under it -- and Pillow wheels do not all bundle the same one.
        A rasteriser fingerprint that moves when the *compressor* changes reports a
        toolchain difference that is not there: the probe disagrees while ``compare``
        finds every shipped sprite pixel-identical.

        So the probe's ``probe_sha256`` must be the pixel hash. The file hash is still
        reported, as ``probe_png_sha256``, so an encoder change stays visible.
        """
        probe = raster.probe()
        rendered = raster.render_svg(raster.PROBE_SVG)
        self.assertEqual(rendered.pixels_sha256, probe["probe_sha256"])
        self.assertNotEqual(
            rendered.sha256,
            probe["probe_sha256"],
            "the fingerprint is hashing the compressed file again",
        )
        self.assertEqual(rendered.sha256, probe["probe_png_sha256"])

    def test_the_same_pixels_survive_a_different_compressor(self):
        """Re-compressing the identical pixels must not move the fingerprint.

        Reproduces the difference between machines locally: the IDAT stream is
        decompressed and recompressed with CPython's zlib at level 1, another stream for
        the same rows, as another machine's zlib writes one (`encode_png` may already have
        used CPython's zlib at level 9, so that level would give the same stream back),
        and the probe's PNG is written again around it. That file's bytes differ; the
        fingerprint of what it decodes to is the probe's.
        """
        rendered = raster.render_svg(raster.PROBE_SVG)
        chunks = []
        offset = 8
        data = rendered.png_bytes
        while offset < len(data):
            length, kind = struct.unpack(">I4s", data[offset : offset + 8])
            chunks.append((kind, data[offset + 8 : offset + 8 + length]))
            offset += 12 + length
        idat = b"".join(body for kind, body in chunks if kind == b"IDAT")
        recompressed = zlib.compress(zlib.decompress(idat), 1)
        # If these were equal the test would prove nothing, so assert the premise too.
        self.assertNotEqual(idat, recompressed, "the two compressors agree here; pick another probe")
        # The same chunks in the same order, the image data one IDAT of the other stream.
        other_file = data[:8]
        written = False
        for kind, body in chunks:
            if kind == b"IDAT":
                if written:
                    continue
                body, written = recompressed, True
            other_file += struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))
        other = raster.Raster(png_bytes=other_file, pixels=raster.png_pixels(other_file))
        self.assertNotEqual(rendered.sha256, other.sha256, "the premise: the two files differ")
        self.assertEqual(raster.probe()["probe_sha256"], other.pixels_sha256)

    def test_rendering_is_repeatable_within_a_run(self):
        first = raster.render_svg(raster.PROBE_SVG)
        second = raster.render_svg(raster.PROBE_SVG)
        self.assertEqual(first.sha256, second.sha256)
        self.assertEqual(first.pixels_sha256, second.pixels_sha256)

    def test_zero_radius_emits_a_plain_rectangle(self):
        self.assertNotIn("rx=", fit.rounded_rect_svg(10, 10, 0))
        self.assertIn('rx="6"', fit.rounded_rect_svg(10, 10, 6))


if __name__ == "__main__":
    unittest.main()
