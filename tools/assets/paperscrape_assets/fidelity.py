"""Comparison of a staged render against the sprite it is meant to reproduce.

The verdicts exist because "identical" and "looks the same" are different claims
and the difference matters. A reconstructed sprite that is one alpha unit off
along a curve is not identical, and calling it identical would put a number in
`RELEASE_HISTORY.md` that nobody could reproduce.

Verdicts
--------
``PIXEL_IDENTICAL``   every one of the four channels matches everywhere. Only
                      reachable for geometry with no antialiased edge at all --
                      in practice, axis-aligned shapes that land on pixel
                      boundaries.
``EDGE_EQUIVALENT``   the shape is the same and the entire difference sits on the
                      antialiased boundary. This is what a faithful
                      reconstruction through a different rasteriser looks like:
                      the same geometry, with the coverage of partially covered
                      pixels resolved by different arithmetic.
``DIVERGENT``         anything else: the geometry has not been recovered, and the
                      sprite belongs in the gap list rather than in staging.

What gates `EDGE_EQUIVALENT`, and why not IoU
---------------------------------------------
Three conditions, all of them scale-free:

1. ``interior_alpha_mismatch == 0`` -- no pixel where one image is fully opaque
   and the other fully transparent. Antialiasing cannot produce that; a wrong
   shape always does.
2. ``max_rgb_diff_where_opaque == 0`` -- the fill colour is exactly right.
3. ``boundary_confined`` -- every differing pixel is partially covered *in the
   reference*, so the whole disagreement lies inside the reference's own
   antialiased band, one pixel wide. The band is deliberately the reference's
   and not either image's: were the candidate allowed to define it too, a
   semi-transparent bulge into empty space would qualify as an edge difference,
   which is a shape change wearing an edge's clothing. A corner radius one grid
   unit off fails this even where it does not disturb a solid interior pixel.

``alpha_iou`` is still measured and reported, but it does **not** gate. An area
ratio is the wrong instrument here because the antialiased boundary is a fixed
share of the *perimeter* while the denominator is the *area*: on a 270x450
sprite the boundary is a rounding error, and on a 78x18 one it is a large
fraction of the shape. A single absolute IoU threshold therefore asks small
sprites to be far more precise than large ones for no reason connected to how
either looks. Three sprites -- both house planters and `road_line` -- failed a
0.999 IoU gate at 0.9988 with zero solid/empty conflicts and every differing
pixel on their own antialiased edge, which is the failure mode of the metric
rather than of the reconstruction.

RGB is compared only where both images are opaque. Where alpha is zero the RGB
channels are unobservable, and PNG encoders do not agree on what to store there.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass

import numpy as np

#: Reported alongside every result as a shape-similarity figure. Deliberately not
#: a gate -- see the module docstring.
IOU_REPORTING_FLOOR = 0.999

#: A pixel is "solid" at this alpha and "empty" at zero. Disagreement between the
#: two is what `interior_alpha_mismatch` counts.
SOLID_ALPHA = 255


@dataclass(frozen=True)
class FidelityResult:
    name: str
    size_match: bool
    reference_size: tuple[int, int]
    candidate_size: tuple[int, int]
    alpha_iou: float
    mean_alpha_diff: float
    max_alpha_diff: int
    differing_pixels: int
    total_pixels: int
    interior_alpha_mismatch: int
    boundary_confined: bool
    max_rgb_diff_where_opaque: int
    reference_bbox: tuple[int, int, int, int] | None
    candidate_bbox: tuple[int, int, int, int] | None
    bbox_delta: tuple[int, int, int, int] | None
    reference_padding_fraction: float
    candidate_padding_fraction: float
    verdict: str

    def as_dict(self) -> dict[str, object]:
        return asdict(self)


def _bbox(alpha: np.ndarray) -> tuple[int, int, int, int] | None:
    rows = np.flatnonzero(alpha.any(axis=1))
    cols = np.flatnonzero(alpha.any(axis=0))
    if rows.size == 0 or cols.size == 0:
        return None
    return int(cols[0]), int(rows[0]), int(cols[-1]) + 1, int(rows[-1]) + 1


def _padding_fraction(alpha: np.ndarray) -> float:
    box = _bbox(alpha)
    total = alpha.size
    if box is None or total == 0:
        return 1.0
    covered = (box[2] - box[0]) * (box[3] - box[1])
    return 1.0 - covered / total


def compare(name: str, reference: np.ndarray, candidate: np.ndarray) -> FidelityResult:
    """Compare two ``(h, w, 4)`` uint8 arrays, reference first."""
    ref_size = (reference.shape[1], reference.shape[0])
    cand_size = (candidate.shape[1], candidate.shape[0])
    if ref_size != cand_size:
        return FidelityResult(
            name=name,
            size_match=False,
            reference_size=ref_size,
            candidate_size=cand_size,
            alpha_iou=0.0,
            mean_alpha_diff=255.0,
            max_alpha_diff=255,
            differing_pixels=-1,
            total_pixels=reference.shape[0] * reference.shape[1],
            interior_alpha_mismatch=-1,
            boundary_confined=False,
            max_rgb_diff_where_opaque=255,
            reference_bbox=_bbox(reference[..., 3]),
            candidate_bbox=_bbox(candidate[..., 3]),
            bbox_delta=None,
            reference_padding_fraction=_padding_fraction(reference[..., 3]),
            candidate_padding_fraction=_padding_fraction(candidate[..., 3]),
            verdict="DIVERGENT",
        )

    ref_alpha = reference[..., 3].astype(np.int32)
    cand_alpha = candidate[..., 3].astype(np.int32)
    alpha_diff = np.abs(ref_alpha - cand_alpha)

    # Coverage-weighted IoU: a partially covered edge pixel contributes its own
    # coverage, so the measure does not depend on where a threshold is put.
    ref_cov = ref_alpha / 255.0
    cand_cov = cand_alpha / 255.0
    intersection = float(np.minimum(ref_cov, cand_cov).sum())
    union = float(np.maximum(ref_cov, cand_cov).sum())
    iou = 1.0 if union == 0 else intersection / union

    ref_solid = ref_alpha == SOLID_ALPHA
    cand_solid = cand_alpha == SOLID_ALPHA
    ref_empty = ref_alpha == 0
    cand_empty = cand_alpha == 0
    interior_mismatch = int(((ref_solid & cand_empty) | (cand_solid & ref_empty)).sum())

    both_opaque = ref_solid & cand_solid
    if both_opaque.any():
        rgb_diff = np.abs(
            reference[..., :3][both_opaque].astype(np.int32)
            - candidate[..., :3][both_opaque].astype(np.int32)
        )
        max_rgb = int(rgb_diff.max())
    else:
        max_rgb = 0

    differs = np.abs(reference.astype(np.int32) - candidate.astype(np.int32)).sum(axis=2) > 0
    differing = int(differs.sum())

    # The band is the *reference's* own antialiased edge, not either image's.
    # Allowing the candidate's partial coverage to define the band would let a
    # semi-transparent bulge into empty space count as an edge difference, which
    # is a shape change wearing an edge's clothing -- caught by a test.
    on_boundary = (ref_alpha > 0) & (ref_alpha < SOLID_ALPHA)
    boundary_confined = bool(not (differs & ~on_boundary).any())

    ref_box = _bbox(ref_alpha)
    cand_box = _bbox(cand_alpha)
    delta = (
        tuple(c - r for r, c in zip(ref_box, cand_box)) if ref_box and cand_box else None
    )

    if differing == 0:
        verdict = "PIXEL_IDENTICAL"
    elif interior_mismatch == 0 and max_rgb == 0 and boundary_confined:
        verdict = "EDGE_EQUIVALENT"
    else:
        verdict = "DIVERGENT"

    return FidelityResult(
        name=name,
        size_match=True,
        reference_size=ref_size,
        candidate_size=cand_size,
        alpha_iou=iou,
        mean_alpha_diff=float(alpha_diff.mean()),
        max_alpha_diff=int(alpha_diff.max()),
        differing_pixels=differing,
        total_pixels=int(ref_alpha.size),
        interior_alpha_mismatch=interior_mismatch,
        boundary_confined=boundary_confined,
        max_rgb_diff_where_opaque=max_rgb,
        reference_bbox=ref_box,
        candidate_bbox=cand_box,
        bbox_delta=delta,
        reference_padding_fraction=_padding_fraction(ref_alpha),
        candidate_padding_fraction=_padding_fraction(cand_alpha),
        verdict=verdict,
    )
