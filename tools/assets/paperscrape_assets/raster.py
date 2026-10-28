"""Deterministic SVG to PNG rasterisation.

One function renders, and it takes no options that a caller could vary. That is
deliberate: every knob exposed here is a way for two runs of the same source to
disagree, and the whole point of the pipeline is that they cannot.

The settings below are fixed for the same reason:

* ``skip_system_fonts`` -- a sprite must never depend on what is installed on the
  machine that rendered it. Sprite sources carry no text at all, since a font
  renders differently from machine to machine; this makes that a property of the
  tool rather than a habit.
* ``shape_rendering="geometric_precision"`` -- the antialiasing policy is stated
  rather than left at whatever the rasteriser's default happens to be in the
  installed version.
* The size comes from the SVG's own ``width``/``height``, never from a zoom or a
  DPI. A sprite's pixel dimensions are part of its contract with the Kotlin
  anchor offsets that position it, so they are declared in the source document
  and nowhere else.

The PNG is re-encoded by :func:`encode_png` rather than passed through as the
rasteriser emitted it. Its bytes depend on the pixels and on the zlib build that
compresses them, so two machines can write different files for one drawing: a
drawing is compared by its pixels (:func:`same_output`), never by its bytes.
"""

from __future__ import annotations

import hashlib
import io
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import resvg_py
from PIL import Image, features

#: Rendered with the pinned toolchain, this document's pixels hash to
#: PROBE_EXPECTED_SHA256. It deliberately exercises the parts of the rasteriser
#: whose output would move first if the toolchain changed: a curved edge at a
#: shallow angle, a straight edge landing exactly on a pixel boundary, a straight
#: edge landing exactly between two, and a partially transparent overlap.
PROBE_SVG = (
    '<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" '
    'viewBox="0 0 64 64">'
    '<rect x="4" y="4" width="24" height="24" fill="#ffffff"/>'
    '<rect x="36.5" y="4.5" width="23" height="23" fill="#ffffff"/>'
    '<circle cx="16" cy="46" r="13" fill="#ffffff"/>'
    '<rect x="34" y="36" width="26" height="20" rx="6" fill="#ffffff" '
    'opacity="0.5"/>'
    "</svg>"
)

#: The pinned toolchain's **pixels** for [PROBE_SVG], as a SHA-256 of the raw RGBA
#: array. Update it only together with the pins, and re-run `compare` in the same
#: change.
#:
#: ### The pixels, never the PNG file
#:
#: A hash of the PNG file -- `hashlib.sha256(png_bytes)`, the *compressed* bytes -- is
#: a function of three things, only one of which is worth measuring: the rendered
#: pixels, the PNG encoder, and **the zlib build underneath it**. Pillow wheels do not
#: all bundle the same compressor; this machine's reports `1.3.1.zlib-ng` (zlib-ng
#: 2.3.3) where another machine's has stock zlib.
#:
#: Measured, not argued. Rendering the probe here and pulling its PNGs apart:
#:
#: - Pillow's own encoder at level 9 writes rows that decompress to 16 448 bytes
#:   hashing to `01d4b1d3...`; recompressing *those exact bytes* with CPython's own
#:   zlib at level 9 gives `c43a0846...`, while the stream Pillow writes is
#:   `6dfe20c8...`;
#: - the file :func:`encode_png` keeps holds other rows for the same pixels (each
#:   with filter 0, `0aa6204a...`), in CPython's level-9 stream `bbd47649...`.
#:
#: Same pixels in, different bytes out. A file fingerprint reports an encoder or
#: compressor difference as a rasteriser difference: it disagrees while `compare`
#: reports **PIXEL_IDENTICAL for every SVG-sourced sprite**, with no defect in the art.
#:
#: ### Why this value may be recorded from this machine
#:
#: Because `compare` proves the rasteriser here is the one the shipped library was
#: rendered with: every sprite with an SVG source comes back identical in its
#: pixels. That is far stronger evidence of "same rasteriser" than one
#: synthetic document, and it is what licenses recording the pixel fingerprint here
#: rather than treating this build as foreign.
#:
#: A mismatch means what the name says: the rasteriser draws differently.
#: Re-measure every figure in `reports/` before trusting it. An encoder or zlib change
#: does not trip it, and `probe_png_sha256` is reported alongside so that such a
#: change is still *visible* without being fatal.
PROBE_EXPECTED_SHA256 = "ec77e95decaa9c2705fa0b3f07d9dc9332ba552fc176ac8a32c38f293fbb367f"


@dataclass(frozen=True)
class Raster:
    """A rendered sprite: its PNG bytes and its pixels."""

    png_bytes: bytes
    pixels: np.ndarray  # (h, w, 4) uint8, straight (non-premultiplied) alpha

    @property
    def size(self) -> tuple[int, int]:
        return self.pixels.shape[1], self.pixels.shape[0]

    @property
    def sha256(self) -> str:
        """The encoded file's hash. Right for "did this file change", wrong for the probe."""
        return hashlib.sha256(self.png_bytes).hexdigest()

    @property
    def pixels_sha256(self) -> str:
        """The rendered pixels' hash, independent of how they are compressed.

        This is what a *rasteriser* fingerprint has to be. See [probe].
        """
        return hashlib.sha256(self.pixels.tobytes()).hexdigest()


def render_svg(svg_text: str) -> Raster:
    """Rasterise ``svg_text`` at the size its own root element declares."""
    raw = bytes(
        resvg_py.svg_to_bytes(
            svg_string=svg_text,
            skip_system_fonts=True,
            shape_rendering="geometric_precision",
        )
    )
    image = Image.open(io.BytesIO(raw)).convert("RGBA")
    return Raster(png_bytes=encode_png(image), pixels=np.array(image))


def render_svg_file(path: Path) -> Raster:
    return render_svg(path.read_text(encoding="utf-8"))


def encode_png(image: Image.Image) -> bytes:
    """The smaller of two lossless encodings of ``image``, as the tools write a PNG into the app.

    The first, for an RGBA image or an RGB one with no colour marked transparent, writes every
    row with PNG filter 0 (none) and compresses the rows with zlib at level 9
    (:func:`_unfiltered_png`). The second is Pillow's own encoder at level 9, which picks a
    filter row by row. Any other image gets that one alone: another mode, and an RGB image
    whose ``info["transparency"]`` marks a colour transparent, which Pillow writes as a
    ``tRNS`` chunk and the first encoding has no place for.
    The sprites are flat colours, and a per-row filter (Sub, Up, Paeth) breaks the runs of equal
    pixels that zlib compresses best: on the shipped set the first is the smaller for nearly
    every file, by about a fifth. Android's packager re-encodes every PNG and keeps the original
    when its own result is not smaller, so a smaller file here is a smaller APK. The sprites are
    RGBA; the opaque comparison sheet under ``reports/`` is RGB, and is written here too.

    Both decode to the same pixels, and the bytes are not portable: they depend on the zlib
    under CPython and under the Pillow wheel. Compare drawings with :func:`same_output` or
    :attr:`Raster.pixels_sha256`, never with the file's bytes.
    """
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=False, compress_level=9)
    filtered = buffer.getvalue()
    if image.mode not in ("RGBA", "RGB") or (image.mode == "RGB" and "transparency" in image.info):
        return filtered
    unfiltered = _unfiltered_png(np.asarray(image))
    return unfiltered if len(unfiltered) <= len(filtered) else filtered


def _unfiltered_png(pixels: np.ndarray) -> bytes:
    """An 8-bit PNG of ``pixels``, ``(h, w, 4)`` RGBA or ``(h, w, 3)`` RGB: IHDR, one IDAT of the
    rows each with filter 0, IEND."""
    height, width, channels = pixels.shape
    colour_type = {4: 6, 3: 2}[channels]
    rows = np.zeros((height, 1 + width * channels), dtype=np.uint8)
    rows[:, 1:] = pixels.reshape(height, width * channels)
    header = struct.pack(">IIBBBBB", width, height, 8, colour_type, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", header)
        + _chunk(b"IDAT", zlib.compress(rows.tobytes(), 9))
        + _chunk(b"IEND", b"")
    )


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def png_pixels(data: bytes) -> np.ndarray:
    """The pixels a PNG file decodes to, ``(h, w, 4)`` uint8 RGBA with straight alpha."""
    with Image.open(io.BytesIO(data)) as image:
        return np.array(image.convert("RGBA"))


def same_output(path: Path, data: bytes) -> bool:
    """Whether the file at ``path`` is what a generator writes as ``data``.

    A PNG is the same when it decodes to the same pixels -- the same size and every channel
    of every pixel equal --, whatever compressed them (:func:`encode_png`); any other file
    when it has the same bytes. A missing file is not the same.
    """
    if not path.is_file():
        return False
    if path.suffix == ".png":
        return np.array_equal(png_pixels(path.read_bytes()), png_pixels(data))
    return path.read_bytes() == data


def probe() -> dict[str, object]:
    """Fingerprint the installed toolchain against the pinned expectation."""
    raster = render_svg(PROBE_SVG)
    actual = raster.pixels_sha256
    return {
        "resvg_py": getattr(resvg_py, "__version__", "unknown"),
        "pillow": Image.__version__,
        "numpy": np.__version__,
        # Reported so that an encoder or zlib change stays visible. It is deliberately
        # not what `matches_expected` is decided on -- see PROBE_EXPECTED_SHA256.
        "zlib": features.version("zlib"),
        "probe_png_sha256": raster.sha256,
        "probe_sha256": actual,
        "probe_expected_sha256": PROBE_EXPECTED_SHA256,
        "matches_expected": actual == PROBE_EXPECTED_SHA256,
    }
