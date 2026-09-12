"""How a person sprite separates one piece of paper from the next.

**This file used to test an outer outline, and v4.25 changed what it protects rather than
relaxing it.** Concept B "Rilievo" -- the family the maintainer chose -- draws no outline at all.
Every piece instead carries an *under-paper*: the same cut, offset down and to the right, in a
darker tone of that piece's own colour, which is the recipe the v4.21 oak already uses. An arm
reads as separate from the body because a sliver of its own shadow shows along the edge, not
because a dark band runs round the figure.

The property worth defending did not change with the artwork. The rim that both treatments
replaced failed *between* frames: it was present down one side of a figure and gone along an arm,
so it flickered the moment the arm moved, and it passed every per-sprite check there was because
every check looked at one sprite at a time. So these tests still look at a **sequence** -- one
figure across three frames -- and still ask whether the three carry the same treatment, applied to
the same extent, going the same way round.

What is deliberately no longer asserted: that a dark band encloses the silhouette. It does not, by
the concept's own decision, and asserting it would be asserting the artwork the project no longer
ships.
"""

import sys
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import registry  # noqa: E402

RUNTIME = TOOL_ROOT.parent.parent / "app/src/main/res/drawable-nodpi"
SVG_DIR = TOOL_ROOT / "sources/svg"
REGISTRY_PATH = TOOL_ROOT / "sources/sprites.json"

#: The tone every paper is shaded toward. The same constant `build_people_concepts.py` mixes
#: toward and `tools/generate_skin_variants.py` moves a paint's shadows with.
SHADE_TOWARDS = np.array((43, 42, 51), dtype=np.float64)

#: How deep a shade the under-paper is, and how far off that a measured colour may sit. The
#: generator uses 0.34; the band is wide enough for the rasteriser's rounding and narrow enough
#: that an ordinary second paint does not fall inside it.
RELIEF_T = 0.34
RELIEF_T_BAND = 0.10
RELIEF_RESIDUAL = 8.0

#: Palette colours rarer than this are anti-aliasing rather than paint.
PALETTE_MIN = 40

#: The eight walk cycles: four people, two seasons, three frames each.
WALK_CYCLES = [
    [f"person_{who}_{season}_walk{frame}" for frame in range(3)]
    for who in ("man", "woman", "boy", "girl")
    for season in ("summer", "winter")
]

#: The still occupant sprites, which share the walkers' treatment and must match it.
#:
#: v4.19 retired the four adult vehicle bases and v4.20 the two boy ones -- in each case a base
#: that was pixel-identical to one of its own tone copies and that no draw path could reach -- so
#: those six were measured on the heir the registry's `retiredBases` names, which was the same
#: drawing under the surviving name.
#:
#: **v4.30 removed the tone copies entirely**, so there are no heirs left to stand in. A seated bust
#: is now a fixed layer plus its region masks, and the fixed layer is what carries the relief: the
#: under-paper is a shade of a piece's own paint, and a shade is `(1-t)·paint + t·DARK` whose
#: `t·DARK` half does not follow the colour and is therefore fixed art by construction. So the
#: relief is measured where it lives, on `_fx`, for every one of the eight -- which is also why this
#: list stopped needing a per-family exception for the girl.
OCCUPANTS = [
    f"person_{who}_{season}_head_window"
    for who in ("man", "woman", "boy", "girl")
    for season in ("summer", "winter")
] + [
    f"person_{who}_{season}_head_car_fx"
    for who in ("man", "woman", "boy", "girl")
    for season in ("summer", "winter")
]


def pixels(name: str) -> np.ndarray:
    with Image.open(RUNTIME / f"{name}.png") as image:
        return np.array(image.convert("RGBA"))


def paints(image: np.ndarray) -> np.ndarray:
    """The sprite's actual flat colours, as an (n,3) float array."""
    solid = image[image[..., 3] > 200][:, :3]
    colours, counts = np.unique(solid, axis=0, return_counts=True)
    return colours[counts >= PALETTE_MIN].astype(np.float64)


def relief_mask(image: np.ndarray) -> np.ndarray:
    """Pixels painted in a colour that is another of this sprite's colours, shaded.

    Measured off the sprite rather than read from a list of expected values: the under-paper is
    defined by a *relation* between two of the drawing's own colours, and a test that hard-coded
    the resulting values would have to be rewritten every time a garment palette moved.
    """
    palette = paints(image)
    mask = np.zeros(image.shape[:2], dtype=bool)
    for shade in palette:
        for paint in palette:
            if np.array_equal(shade, paint):
                continue
            axis = SHADE_TOWARDS - paint
            denom = float(axis @ axis)
            if denom == 0:
                continue
            t = float((shade - paint) @ axis / denom)
            if abs(t - RELIEF_T) > RELIEF_T_BAND:
                continue
            if np.linalg.norm(shade - (paint + t * axis)) > RELIEF_RESIDUAL:
                continue
            mask |= np.all(image[..., :3] == shade.astype(np.uint8), axis=2)
            break
    return mask


class PaperReliefTest(unittest.TestCase):
    """The under-paper exists, covers a stable share of the figure, and sits where it should."""

    def relief_share(self, name: str) -> float:
        image = pixels(name)
        solid = image[..., 3] > 128
        self.assertTrue(solid.any(), f"{name} has no silhouette at all")
        return float(relief_mask(image).sum()) / float(solid.sum())

    def test_every_walk_frame_carries_an_under_paper(self):
        for cycle in WALK_CYCLES:
            for name in cycle:
                with self.subTest(name=name):
                    self.assertGreater(
                        self.relief_share(name), 0.02,
                        f"{name} carries no under-paper: nothing separates its pieces",
                    )

    def test_the_three_frames_of_a_cycle_carry_the_same_amount_of_it(self):
        """The failure the rim had. A treatment that covers a fifth of one frame and a twentieth
        of the next appears and disappears as the figure walks, which is exactly what a still of
        any single frame cannot show."""
        for cycle in WALK_CYCLES:
            with self.subTest(cycle=cycle[0]):
                shares = [self.relief_share(name) for name in cycle]
                spread = max(shares) - min(shares)
                self.assertLess(
                    spread, 0.06,
                    f"{cycle[0][:-1]} frames carry visibly different amounts of relief: {shares}",
                )

    def test_the_under_paper_is_darker_than_the_paper_above_it(self):
        """Separation, not decoration: a shadow that is not darker separates nothing."""
        for cycle in WALK_CYCLES:
            name = cycle[0]
            with self.subTest(name=name):
                image = pixels(name)
                relief = relief_mask(image)
                above = (image[..., 3] > 200) & ~relief
                self.assertTrue(relief.any() and above.any())
                self.assertLess(
                    image[relief][:, :3].mean(), image[above][:, :3].mean(),
                    f"{name}'s under-paper is not darker than what it sits under",
                )

    def test_the_under_paper_falls_down_and_to_the_right(self):
        """One light source for the whole set. Two pieces lit from opposite sides read as two
        drawings, and nothing else in this test would notice."""
        for cycle in WALK_CYCLES:
            name = cycle[0]
            with self.subTest(name=name):
                image = pixels(name)
                relief = relief_mask(image)
                above = (image[..., 3] > 200) & ~relief
                ry, rx = np.nonzero(relief)
                ay, ax = np.nonzero(above)
                self.assertGreater(
                    ry.mean(), ay.mean(),
                    f"{name}'s under-paper does not sit below the paper it shadows",
                )
                self.assertGreater(
                    rx.mean(), ax.mean(),
                    f"{name}'s under-paper does not sit to the right of the paper it shadows",
                )

    def test_the_still_occupants_match_the_walkers(self):
        """A head in a window is the same person as the one on the pavement."""
        for name in OCCUPANTS:
            with self.subTest(name=name):
                self.assertGreater(
                    self.relief_share(name), 0.02,
                    f"{name} carries no under-paper, so it is not drawn like the walkers",
                )

    def test_no_frame_lost_its_relief_to_a_source_edit(self):
        """The marker is in the source, so a hand edit that drops it fails here."""
        for cycle in WALK_CYCLES:
            for name in cycle:
                with self.subTest(name=name):
                    self.assertIn(
                        "paperscrape-relief", (SVG_DIR / f"{name}.svg").read_text(),
                        f"{name} has no under-paper in its source",
                    )

    def test_every_person_sprite_still_matches_its_registry_geometry(self):
        """The content box the registry declares is the one the shipped pixels have.

        Only sprites that *have* an SVG can be regenerated at all; the registry has always allowed
        `source.kind = "none"` for the recolours, and this loop reads the source for the ones that
        have it and the shipped bytes for every one.
        """
        for spec in registry.load(REGISTRY_PATH):
            if spec.category != "person":
                continue
            with self.subTest(name=spec.name):
                image = pixels(spec.name)
                ys, xs = np.nonzero(image[..., 3])
                measured = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
                self.assertEqual(tuple(spec.content_box), measured)


if __name__ == "__main__":
    unittest.main()
