"""Deterministic SVG to PNG rasterisation.

One function renders, and it takes no options that a caller could vary. That is
deliberate: every knob exposed here is a way for two runs of the same source to
disagree, and the whole point of the pipeline is that they cannot.

The settings below are fixed for the same reason:

* ``skip_system_fonts`` -- a sprite must never depend on what is installed on the
  machine that rendered it. Sprite sources carry no text at all (see
  ``DESIGN_NOTES.md`` section 4); this makes that a property of the tool rather
  than a habit.
* ``shape_rendering="geometric_precision"`` -- the antialiasing policy is stated
  rather than left at whatever the rasteriser's default happens to be in the
  installed version.
* The size comes from the SVG's own ``width``/``height``, never from a zoom or a
  DPI. A sprite's pixel dimensions are part of its contract with the Kotlin
  anchor offsets that position it, so they are declared in the source document
  and nowhere else.

The PNG is re-encoded through Pillow with fixed settings rather than passed
through as the rasteriser emitted it, so the bytes on disk depend only on the
pixels and not on the encoder version bundled inside the rasteriser wheel.
"""

from __future__ import annotations

import hashlib
import io
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import resvg_py
from PIL import Image, features

#: Rendered with the pinned toolchain, this document's PNG bytes hash to
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
#: ### It used to hash the PNG file, and that was the bug
#:
#: Until v4.13 this was `hashlib.sha256(png_bytes)` -- the *compressed* file. That
#: made the fingerprint a function of three things, only one of which anybody wanted
#: to measure: the rendered pixels, the PNG encoder, and **the zlib build underneath
#: it**. Pillow wheels do not all bundle the same compressor; this machine's reports
#: `1.3.1.zlib-ng` (zlib-ng 2.3.3) where the machine that first recorded the value had
#: stock zlib.
#:
#: Measured, not argued. Rendering the probe here and pulling the PNG apart:
#:
#: - the decompressed IDAT is 16 448 bytes hashing to `01d4b1d3...`;
#: - recompressing *those exact bytes* with CPython's own zlib at level 9 gives
#:   `c43a0846...`, while the stream Pillow actually wrote is `6dfe20c8...`.
#:
#: Same pixels in, different bytes out. The old fingerprint was reporting a
#: compressor difference as a rasteriser difference, which is why it disagreed while
#: `compare` reported **PIXEL_IDENTICAL for all 125 SVG-sourced sprites** -- and why
#: the disagreement survived being documented three times without ever being a real
#: defect in the art.
#:
#: ### Why this value may be recorded from this machine
#:
#: Because `compare` proves the rasteriser here is the one the shipped library was
#: rendered with: all 125 sprites with an SVG source come back byte-for-byte identical
#: in their pixels. That is far stronger evidence of "same rasteriser" than one
#: synthetic document, and it is what licenses recording the pixel fingerprint here
#: rather than treating this build as foreign.
#:
#: A mismatch now means what the name always claimed: the rasteriser draws differently.
#: Re-measure every figure in `reports/` before trusting it. An encoder or zlib change
#: no longer trips it, and `probe_png_sha256` is reported alongside so that such a
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
    """Encode with fixed settings so the bytes depend only on the pixels.

    ``optimize=False`` with an explicit compression level, rather than Pillow's
    optimiser: the optimiser's choices have changed between Pillow releases, and
    a sprite whose bytes move when the encoder is upgraded is a sprite that shows
    up as changed in every diff for no reason.
    """
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=False, compress_level=9)
    return buffer.getvalue()


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
