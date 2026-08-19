"""Recovery of a sprite's geometry by measurement rather than by eye.

This is committed, not scratch work. The SVG sources under `sources/svg/` carry
numbers -- a corner radius of 6, of 9, of 12 -- and without this module those
numbers would be unexplained constants of exactly the kind the project keeps
having to re-derive by hand. Running `paperscrape-assets fit` reproduces every
one of them from the shipped PNG.

Only two shape families are implemented, and that is the point rather than a
limitation. A rectangle and a rounded rectangle are fully determined by their
canvas: sweep the one free parameter, keep the value that minimises the
difference, and there is nothing left to choose. A canopy of overlapping lobes or
a fanned palm frond is not determined by anything -- the lobe count, their
radii, their placement and the random seed that jittered them are all free, and
"the fit that happened to score best" would be an invention presented as a
recovery. Those sprites are recorded as gaps.

Grid snapping
-------------
The best-scoring radius is reported alongside the nearest multiple of
`SPRITE_PIXELS_PER_UNIT`, with the score of both. Where snapping costs nothing
measurable, the snapped value is the one that goes into the source: the sprite
grid says a SCENE_UNITS sprite is authored at three pixels per on-screen unit, so
a radius of 9 is two units and a radius of 9.1 is two units plus a rounding
artefact of whatever produced the original.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from .inventory import SPRITE_PIXELS_PER_UNIT
from .raster import render_svg


@dataclass(frozen=True)
class RadiusFit:
    name: str
    width: int
    height: int
    best_radius: float
    best_mean_alpha_diff: float
    snapped_radius: float
    snapped_mean_alpha_diff: float
    snap_cost: float

    @property
    def recommended_radius(self) -> float:
        """The snapped radius unless snapping measurably degrades the fit."""
        return self.snapped_radius if self.snap_cost <= SNAP_TOLERANCE else self.best_radius


#: How much mean alpha error (out of 255) snapping to the grid may cost before
#: the unsnapped value is preferred. One hundredth of one alpha unit averaged
#: over the whole canvas is far below anything that could be seen; above that,
#: the grid is not what the original used and pretending otherwise would be
#: fitting the theory rather than the sprite.
SNAP_TOLERANCE = 0.01


def rounded_rect_svg(
    width: int,
    height: int,
    radius: float,
    fill: str = "#ffffff",
    note: str = "",
) -> str:
    """The canonical source form for a rectangular sprite.

    A radius of zero emits no ``rx``: a plain rectangle should read as a plain
    rectangle in the source, not as a rounded one whose rounding was set to
    nothing.

    ``note`` becomes an XML comment. Every committed source carries one, saying
    where its numbers came from, so a reader never has to treat a radius as an
    arbitrary constant.
    """
    radius_attr = "" if radius <= 0 else f' rx="{_number(radius)}" ry="{_number(radius)}"'
    # A double hyphen cannot appear inside an XML comment, and the project's prose
    # style uses one constantly. Fold it here rather than relying on every caller
    # to remember, since the failure is a parse error at render time.
    comment = f"<!-- {note.replace('--', ';').strip()} -->\n" if note else ""
    return (
        comment
        + '<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{width}" height="{height}" viewBox="0 0 {width} {height}">\n'
        f'  <rect x="0" y="0" width="{width}" height="{height}"{radius_attr} fill="{fill}"/>\n'
        "</svg>\n"
    )


def _number(value: float) -> str:
    return str(int(value)) if float(value).is_integer() else f"{value:g}"


def _mean_alpha_diff(reference_alpha: np.ndarray, width: int, height: int, radius: float) -> float:
    rendered = render_svg(rounded_rect_svg(width, height, radius))
    return float(
        np.abs(rendered.pixels[..., 3].astype(np.int32) - reference_alpha).mean()
    )


def fit_rounded_rect(name: str, reference: np.ndarray, step: float = 0.1) -> RadiusFit:
    """Sweep the corner radius of a full-canvas rounded rectangle.

    Coarse pass at one pixel, then a fine pass at ``step`` within one pixel of the
    coarse winner. A full sweep at ``step`` over a 450-pixel canvas is thousands
    of renders for a curve with a single minimum; the two-pass form is the same
    answer at a fraction of the cost. Both passes are exhaustive over their own
    range, so the result does not depend on a starting guess.
    """
    height, width = reference.shape[:2]
    reference_alpha = reference[..., 3].astype(np.int32)
    limit = min(width, height) / 2.0

    def sweep(start: float, stop: float, increment: float) -> tuple[float, float]:
        best_r = max(0.0, start)
        best_s = _mean_alpha_diff(reference_alpha, width, height, best_r)
        value = best_r + increment
        while value <= stop + 1e-9:
            score = _mean_alpha_diff(reference_alpha, width, height, value)
            if score < best_s:
                best_r, best_s = value, score
            value = round(value + increment, 6)
        return best_r, best_s

    coarse_radius, _ = sweep(0.0, limit, 1.0)
    best_radius, best_score = sweep(
        max(0.0, coarse_radius - 1.0), min(limit, coarse_radius + 1.0), step
    )

    snapped = float(round(best_radius / SPRITE_PIXELS_PER_UNIT) * SPRITE_PIXELS_PER_UNIT)
    snapped = min(snapped, limit)
    snapped_score = (
        best_score if snapped == best_radius
        else _mean_alpha_diff(reference_alpha, width, height, snapped)
    )

    return RadiusFit(
        name=name,
        width=width,
        height=height,
        best_radius=best_radius,
        best_mean_alpha_diff=best_score,
        snapped_radius=snapped,
        snapped_mean_alpha_diff=snapped_score,
        snap_cost=snapped_score - best_score,
    )
