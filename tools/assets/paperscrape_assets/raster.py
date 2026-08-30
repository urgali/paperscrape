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
from PIL import Image

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

#: Measured with the toolchain pinned in requirements.txt, **on the machine that
#: recorded it**. Update this value only together with the pins, and re-run `compare`
#: in the same change.
#:
#: A mismatch means the rasteriser build differs from the recorded one. It does *not*
#: by itself mean the shipped art would re-render differently, and this is worth
#: stating because the two were once conflated. Measured on a second machine: the
#: fingerprint differs (`20e57750...` against the `e2eb2e10...` below) while
#: `compare` reports **PIXEL_IDENTICAL for all 125 SVG-sourced sprites** -- and the
#: fingerprint is the same under Pillow 12.1.1 and 12.3.0, so the difference is in the
#: native resvg build rather than in the Python pins.
#:
#: The document below is a square at half-pixel coordinates, a circle and a rounded
#: rect at 50 % opacity: it exercises antialiasing of curves and sub-pixel edges on
#: purpose, which is what makes it sensitive enough to be a toolchain probe. The
#: sprite library is flat paper-cutout shapes on the 3x grid and does not depend on
#: that behaviour, which is why it can render identically while this differs.
#:
#: So: treat a mismatch as "this is a different rasteriser build, re-measure before
#: trusting any figure in reports/", and treat `compare` as the answer for the shipped
#: art specifically. `compare` is the stronger evidence of the two -- it checks all 125
#: real sprites rather than one synthetic document.
PROBE_EXPECTED_SHA256 = "e2eb2e1004f7ca8529960f09ff65abc950dd81dffa4543f6044e5b224a3c6390"


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
        return hashlib.sha256(self.png_bytes).hexdigest()


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
    actual = raster.sha256
    return {
        "resvg_py": getattr(resvg_py, "__version__", "unknown"),
        "pillow": Image.__version__,
        "numpy": np.__version__,
        "probe_sha256": actual,
        "probe_expected_sha256": PROBE_EXPECTED_SHA256,
        "matches_expected": actual == PROBE_EXPECTED_SHA256,
    }
