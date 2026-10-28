"""How the tools write a PNG into the app, and how they tell two PNGs apart.

`raster.encode_png` writes the smaller of two lossless encodings: every row with filter 0 and zlib
at level 9, or Pillow's own per-row filters. The sprites are flat colours, so the first is nearly
always the smaller, and Android's packager keeps a PNG as it is when its own re-encoding is not
smaller: a smaller file here is a smaller APK, at the same pixels.

The bytes that come out depend on the zlib build as well as on the pixels, so a drawing is
compared by its pixels (`raster.same_output`) and never by its bytes: a byte comparison fails on
a machine whose zlib differs, at the same pixels.

Run from `tools/assets/`:

    python3 -m unittest discover -s tests -v
"""

from __future__ import annotations

import io
import re
import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import raster, report  # noqa: E402

RUNTIME_DIR = TOOL_ROOT.parent.parent / "app/src/main/res/drawable-nodpi"
REPORTS_DIR = TOOL_ROOT / "reports"


def row_filters(data: bytes) -> set[int]:
    """The filter type of every row of an 8-bit RGBA or RGB PNG, read off its IDAT."""
    offset, idat, width, height, channels = 8, b"", 0, 0, 0
    while offset < len(data):
        length, kind = struct.unpack(">I4s", data[offset:offset + 8])
        body = data[offset + 8:offset + 8 + length]
        if kind == b"IHDR":
            width, height = struct.unpack(">II", body[:8])
            channels = {6: 4, 2: 3}[body[9]]
        elif kind == b"IDAT":
            idat += body
        offset += 12 + length
    rows = zlib.decompress(idat)
    stride = 1 + width * channels
    return {rows[y * stride] for y in range(height)}


def decoded(data: bytes) -> np.ndarray:
    """The RGBA pixels of a PNG file, decoded by Pillow here rather than by the module under test."""
    with Image.open(io.BytesIO(data)) as image:
        return np.array(image.convert("RGBA"))


def pillow_png(image: Image.Image, level: int = 9) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=False, compress_level=level)
    return buffer.getvalue()


class ShippedPngTest(unittest.TestCase):

    def test_every_shipped_png_is_written_as_the_encoder_writes_it(self):
        """Every PNG in `drawable-nodpi` is no larger than `encode_png` writes its pixels, and
        `encode_png`'s file of them decodes to them, every channel.

        On this machine the shipped files are the encoder's output byte for byte. The bound is
        one-sided and leaves 2 % and 64 B for another zlib build, whose streams differ by less:
        what it catches is a PNG written by another encoder -- Pillow's filters alone write
        files about a fifth larger.
        """
        names = sorted(RUNTIME_DIR.glob("*.png"))
        self.assertGreater(len(names), 400, "the shipped set was not found")
        larger = []
        for path in names:
            shipped = path.read_bytes()
            pixels = decoded(shipped)
            encoded = raster.encode_png(Image.fromarray(pixels, "RGBA"))
            with self.subTest(sprite=path.stem):
                self.assertTrue(np.array_equal(pixels, decoded(encoded)), "the encoder lost a pixel")
            if len(shipped) > len(encoded) * 1.02 + 64:
                larger.append(f"{path.stem} ({len(shipped)} B, the encoder writes {len(encoded)} B)")
        self.assertEqual([], larger, "these PNGs were not written by raster.encode_png: re-encode them with it")


class EncoderTest(unittest.TestCase):

    def test_a_flat_drawing_is_written_without_row_filters(self):
        """Flat colours with soft edges, as the sprites are: filter 0 on every row is the smaller."""
        pixels = np.zeros((60, 90, 4), dtype=np.uint8)
        pixels[10:50, 10:80] = (0x2B, 0x2A, 0x33, 255)
        pixels[20:30, 20:70] = (0xF2, 0xEB, 0xDD, 255)
        pixels[9, 10:80, 3] = 128
        encoded = raster.encode_png(Image.fromarray(pixels, "RGBA"))
        self.assertEqual({0}, row_filters(encoded))
        self.assertLess(len(encoded), len(pillow_png(Image.fromarray(pixels, "RGBA"))))
        self.assertTrue(np.array_equal(pixels, decoded(encoded)))

    def test_a_smooth_gradient_keeps_the_filters_when_they_are_smaller(self):
        """Where Pillow's per-row filters compress better -- a smooth gradient -- its file is kept."""
        y, x = np.mgrid[0:64, 0:64]
        pixels = np.stack([x * 4, y * 4, (x + y) * 2, np.full_like(x, 255)], axis=2).astype(np.uint8)
        image = Image.fromarray(pixels, "RGBA")
        encoded = raster.encode_png(image)
        self.assertEqual(len(pillow_png(image)), len(encoded))
        self.assertNotEqual({0}, row_filters(encoded))
        self.assertTrue(np.array_equal(pixels, decoded(encoded)))

    def test_an_opaque_rgb_drawing_is_written_without_row_filters_too(self):
        """The comparison sheet's case: flat colours with no alpha channel take filter 0 as RGB."""
        pixels = np.zeros((60, 90, 3), dtype=np.uint8)
        pixels[:, :] = (108, 132, 150)
        pixels[10:50, 10:80] = (0x2B, 0x2A, 0x33)
        pixels[20:30, 20:70] = (0xF2, 0xEB, 0xDD)
        image = Image.fromarray(pixels, "RGB")
        encoded = raster.encode_png(image)
        self.assertEqual({0}, row_filters(encoded))
        self.assertLess(len(encoded), len(pillow_png(image)))
        with Image.open(io.BytesIO(encoded)) as back:
            self.assertEqual("RGB", back.mode)
            self.assertTrue(np.array_equal(pixels, np.array(back)))

    def test_an_rgb_drawing_keeps_the_colour_it_marks_transparent(self):
        """An RGB image whose ``info["transparency"]`` marks a colour transparent -- as `Image.open`
        gives one for a PNG with a ``tRNS`` chunk -- decodes with that colour transparent. The
        encoding without filters has no ``tRNS``, so Pillow's file is written, though it is larger."""
        pixels = np.zeros((60, 40, 3), dtype=np.uint8)
        pixels[10:50, 10:30] = (0x2B, 0x2A, 0x33)
        image = Image.fromarray(pixels, "RGB")
        image.info["transparency"] = (0, 0, 0)
        self.assertLess(len(raster._unfiltered_png(pixels)), len(pillow_png(image)), "the premise: the first is smaller")
        alpha = decoded(raster.encode_png(image))[:, :, 3]
        self.assertEqual(0, alpha[0, 0], "the colour marked transparent came back opaque")
        self.assertEqual(255, alpha[20, 20])
        self.assertTrue(np.array_equal(decoded(pillow_png(image)), decoded(raster.encode_png(image))))

    def test_straight_alpha_is_kept_where_a_pixel_is_transparent(self):
        """A pixel of alpha 0 keeps its colour channels: the file is what the generator drew."""
        rng = np.random.default_rng(7)
        pixels = rng.integers(0, 256, size=(33, 47, 4), dtype=np.uint8)
        pixels[::3, ::5, 3] = 0
        encoded = raster.encode_png(Image.fromarray(pixels, "RGBA"))
        self.assertTrue(np.array_equal(pixels, decoded(encoded)))


class ComparisonSheetTest(unittest.TestCase):
    """`reports/comparison-sheet.png` is committed, so it is written by `encode_png` as the sprites are.

    It is opaque and written as RGB. The bound is the one `ShippedPngTest` uses: Pillow's filters
    alone write the committed sheet some 8 % larger than the encoder does, one sprite's row 12 %.
    """

    def assert_written_by_the_encoder(self, written: bytes) -> None:
        with Image.open(io.BytesIO(written)) as image:
            self.assertEqual("RGB", image.mode, "the sheet is opaque and written as RGB")
            pixels = np.array(image)
        encoded = raster.encode_png(Image.fromarray(pixels, "RGB"))
        with Image.open(io.BytesIO(encoded)) as back:
            self.assertTrue(np.array_equal(pixels, np.array(back)), "the encoder lost a pixel")
        self.assertLessEqual(
            len(written), len(encoded) * 1.02 + 64,
            f"the sheet is {len(written)} B and the encoder writes {len(encoded)} B of its pixels: "
            "it was not written by raster.encode_png",
        )

    def test_the_sheet_is_written_by_the_encoder(self):
        """`report.comparison_sheet` writes its file through `encode_png`: one sprite's row."""
        pixels = decoded((RUNTIME_DIR / "tree_fir.png").read_bytes())
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "comparison-sheet.png"
            report.comparison_sheet(path, [("tree_fir", pixels, pixels)])
            self.assert_written_by_the_encoder(path.read_bytes())

    def test_the_committed_sheet_is_written_by_the_encoder(self):
        """The file under `reports/` is the encoder's, every channel of every pixel kept."""
        self.assert_written_by_the_encoder((REPORTS_DIR / "comparison-sheet.png").read_bytes())


class SameOutputTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.data = (RUNTIME_DIR / "tree_fir.png").read_bytes()
        cls.pixels = decoded(cls.data)

    def write(self, folder: str, name: str, data: bytes) -> Path:
        path = Path(folder) / name
        path.write_bytes(data)
        return path

    def test_a_png_compressed_another_way_is_the_same_output(self):
        """The case a byte comparison gets wrong: the same pixels, another compressor's file."""
        other = pillow_png(Image.fromarray(self.pixels, "RGBA"), level=1)
        self.assertNotEqual(self.data, other, "the premise: the two files differ")
        with tempfile.TemporaryDirectory() as folder:
            self.assertTrue(raster.same_output(self.write(folder, "tree_fir.png", other), self.data))

    def test_one_level_of_one_channel_is_another_output(self):
        for channel in range(4):
            changed = self.pixels.copy()
            y, x = np.argwhere(changed[:, :, 3] == 255)[0]
            changed[y, x, channel] ^= 1
            with self.subTest(channel=channel), tempfile.TemporaryDirectory() as folder:
                path = self.write(folder, "tree_fir.png", raster.encode_png(Image.fromarray(changed, "RGBA")))
                self.assertFalse(raster.same_output(path, self.data))

    def test_another_size_and_a_missing_file_are_another_output(self):
        cropped = raster.encode_png(Image.fromarray(np.ascontiguousarray(self.pixels[:, 1:]), "RGBA"))
        with tempfile.TemporaryDirectory() as folder:
            self.assertFalse(raster.same_output(self.write(folder, "tree_fir.png", cropped), self.data))
            self.assertFalse(raster.same_output(Path(folder) / "absent.png", self.data))

    def test_any_other_file_is_compared_by_its_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.write(folder, "Table.kt", b"const val X = 1f\n")
            self.assertTrue(raster.same_output(path, b"const val X = 1f\n"))
            self.assertFalse(raster.same_output(path, b"const val X = 2f\n"))


class NoByteComparisonTest(unittest.TestCase):

    #: A PNG compared by its bytes: a generator's `--check`, a test's `assertEqual`, a reproduction check.
    FORBIDDEN = (
        re.compile(r"read_bytes\(\)\s*!=\s*data"),
        re.compile(r"assertEqual\(\s*data,\s*path\.read_bytes\(\)"),
        re.compile(r"png_bytes\s*[!=]="),
        re.compile(r"[!=]=\s*\w+\.png_bytes"),
    )

    def test_no_generator_and_no_test_compares_a_png_by_its_bytes(self):
        """Every check of a generated PNG goes through `raster.same_output`, which compares pixels."""
        found = []
        for path in sorted(TOOL_ROOT.parent.rglob("*.py")):
            if path.name == Path(__file__).name or "__pycache__" in path.parts:
                continue
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
                if any(pattern.search(line) for pattern in self.FORBIDDEN):
                    found.append(f"{path.relative_to(TOOL_ROOT.parent)}:{number}: {line.strip()}")
        self.assertEqual([], found)


if __name__ == "__main__":
    unittest.main()
