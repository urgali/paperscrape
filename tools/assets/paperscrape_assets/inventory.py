"""Measurement of the shipped runtime PNGs.

This module only reads. It is what lets a claim about the asset set be checked
rather than repeated: every figure quoted in `ARCHITECTURE.md` section 5 and
`DESIGN_NOTES.md` section 4 comes from here, and can be re-derived by running the
tool again.

Two measurements are worth explaining.

**Decoded bytes** are ``width * height * 4``, not the file size. Android decodes
into ``ARGB_8888`` regardless of how well the PNG compressed, so a small file
with a large canvas costs full price in memory. That gap is the reason the asset
set is 864 KB on disk and tens of megabytes decoded.

**Transparent padding** is measured against the alpha bounding box, so a sprite
with no alpha channel at all reports zero padding rather than being skipped. A
PNG saved without an alpha channel is fully opaque by definition, which is a real
property of the asset and not a missing measurement.
"""

from __future__ import annotations

import hashlib
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
from PIL import Image

from .raster import Raster

#: An asset's authoring oversample. Mirrors `SpriteBlitter.SPRITE_PIXELS_PER_UNIT`
#: on the Kotlin side; a sprite in the SCENE_UNITS convention is authored at this
#: multiple of its on-screen size, so its pixel dimensions should be a multiple
#: of it.
SPRITE_PIXELS_PER_UNIT = 3


@dataclass(frozen=True)
class SpriteMeasurement:
    """Everything measurable about one PNG, with no interpretation applied."""

    name: str
    width: int
    height: int
    mode: str
    has_alpha_channel: bool
    file_bytes: int
    decoded_bytes: int
    content_bbox: tuple[int, int, int, int] | None
    content_width: int
    content_height: int
    transparent_padding_bytes: int
    transparent_padding_fraction: float
    opaque_rgb_count: int
    distinct_colour_count: int
    fully_opaque: bool
    on_grid: bool
    sha256: str

    def as_dict(self) -> dict[str, object]:
        return asdict(self)


def measure_image(name: str, image: Image.Image, file_bytes: int, sha256: str) -> SpriteMeasurement:
    rgba = image.convert("RGBA")
    pixels = np.array(rgba)
    alpha = pixels[..., 3]
    width, height = rgba.size

    bbox = rgba.getchannel("A").getbbox()
    if bbox is None:
        content_w = content_h = 0
    else:
        content_w = bbox[2] - bbox[0]
        content_h = bbox[3] - bbox[1]

    decoded = width * height * 4
    padding = decoded - content_w * content_h * 4

    opaque = alpha == 255
    if opaque.any():
        opaque_rgb = np.unique(pixels[..., :3][opaque].reshape(-1, 3), axis=0)
        opaque_rgb_count = int(len(opaque_rgb))
    else:
        opaque_rgb_count = 0
    distinct = int(len(np.unique(pixels.reshape(-1, 4), axis=0)))

    return SpriteMeasurement(
        name=name,
        width=width,
        height=height,
        mode=image.mode,
        has_alpha_channel=image.mode in ("RGBA", "LA") or "transparency" in image.info,
        file_bytes=file_bytes,
        decoded_bytes=decoded,
        content_bbox=tuple(bbox) if bbox else None,
        content_width=content_w,
        content_height=content_h,
        transparent_padding_bytes=padding,
        transparent_padding_fraction=(padding / decoded) if decoded else 0.0,
        opaque_rgb_count=opaque_rgb_count,
        distinct_colour_count=distinct,
        fully_opaque=bool((alpha == 255).all()),
        on_grid=(width % SPRITE_PIXELS_PER_UNIT == 0 and height % SPRITE_PIXELS_PER_UNIT == 0),
        sha256=sha256,
    )


def measure_file(path: Path) -> SpriteMeasurement:
    data = path.read_bytes()
    with Image.open(path) as image:
        return measure_image(
            name=path.stem,
            image=image,
            file_bytes=len(data),
            sha256=hashlib.sha256(data).hexdigest(),
        )


def measure_raster(name: str, raster: Raster) -> SpriteMeasurement:
    image = Image.fromarray(raster.pixels, mode="RGBA")
    return measure_image(
        name=name,
        image=image,
        file_bytes=len(raster.png_bytes),
        sha256=raster.sha256,
    )


def measure_directory(directory: Path) -> list[SpriteMeasurement]:
    return [measure_file(p) for p in sorted(directory.glob("*.png"))]


def duplicate_groups(measurements: list[SpriteMeasurement]) -> dict[str, list[str]]:
    """Names grouped by content hash, keeping only groups with more than one."""
    by_hash: dict[str, list[str]] = {}
    for m in measurements:
        by_hash.setdefault(m.sha256, []).append(m.name)
    return {h: sorted(names) for h, names in by_hash.items() if len(names) > 1}
