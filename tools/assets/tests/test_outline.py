"""The outer outline, and the property the v2.5 rim did not have.

The rim it replaced was clipped to the inside of every shape, so its thickness was a function
of what each shape happened to overlap. On a still that was invisible; across the walk cycle,
where the arms and legs move and the overlaps move with them, the band appeared and vanished
between consecutive frames. It passed every per-sprite check there was, because every check
looked at one sprite at a time.

**These tests look at a sequence.** A walk cycle is one figure over three frames, so the
question is not whether each frame carries an outline but whether the three carry the *same*
one -- same colour, same thickness, present all the way round each frame's own silhouette.
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

#: The eight walk cycles: four people, two seasons, three frames each.
WALK_CYCLES = [
    [f"person_{who}_{season}_walk{frame}" for frame in range(3)]
    for who in ("man", "woman", "boy", "girl")
    for season in ("summer", "winter")
]

#: The still occupant sprites, which share the walkers' outline and must match it.
#:
#: Only the adults ride in cars -- there is no `person_boy_*_head_car` -- so the car set is
#: listed separately rather than crossed with the whole cast.
OCCUPANTS = [
    f"person_{who}_{season}_head_window"
    for who in ("man", "woman", "boy", "girl")
    for season in ("summer", "winter")
] + [
    f"person_{who}_{season}_head_car"
    for who in ("man", "woman")
    for season in ("summer", "winter")
]


def pixels(name: str) -> np.ndarray:
    with Image.open(RUNTIME / f"{name}.png") as image:
        return np.array(image.convert("RGBA"))


def edge_band(image: np.ndarray, depth: int = 2) -> np.ndarray:
    """The opaque pixels within ``depth`` of the silhouette's outside."""
    solid = image[..., 3] > 128
    outside = ~solid
    reach = outside.copy()
    for _ in range(depth):
        grown = np.zeros_like(reach)
        grown[1:, :] |= reach[:-1, :]
        grown[:-1, :] |= reach[1:, :]
        grown[:, 1:] |= reach[:, :-1]
        grown[:, :-1] |= reach[:, 1:]
        reach |= grown
    return solid & reach


class OuterOutlineTest(unittest.TestCase):
    """The outline exists, goes all the way round, and does not move between frames."""

    def outline_colour(self, name: str) -> tuple[int, int, int]:
        """The single most common colour on the sprite's outer edge."""
        image = pixels(name)
        band = edge_band(image)
        self.assertTrue(band.any(), f"{name} has no edge band at all")
        colours, counts = np.unique(image[band][:, :3], axis=0, return_counts=True)
        return tuple(int(c) for c in colours[counts.argmax()])

    def test_every_walk_frame_carries_the_same_outline_colour(self):
        for cycle in WALK_CYCLES:
            with self.subTest(cycle=cycle[0]):
                colours = {self.outline_colour(name) for name in cycle}
                self.assertEqual(
                    1, len(colours),
                    f"{cycle[0][:-1]} frames disagree on their outline colour: {colours}",
                )

    def test_the_outline_goes_all_the_way_round_every_frame(self):
        """Every pixel on a frame's outer edge is the outline colour, not the artwork's.

        This is the one the rim failed. A band that is present down one side of a figure and
        missing along an arm reads as a flicker the moment the arm moves.
        """
        for cycle in WALK_CYCLES:
            colour = np.array(self.outline_colour(cycle[0]))
            for name in cycle:
                with self.subTest(name=name):
                    image = pixels(name)
                    band = image[edge_band(image, depth=1)][:, :3]
                    off = int((np.abs(band.astype(int) - colour).max(axis=1) > 24).sum())
                    self.assertLessEqual(
                        off, band.shape[0] // 50,
                        f"{name}: {off} of {band.shape[0]} outer-edge pixels are not the outline",
                    )

    def test_the_outline_is_the_same_thickness_on_every_frame(self):
        """Thickness measured as the share of the silhouette the band occupies.

        A stroke of one width applied to every frame of one figure gives each frame a band
        proportional to its own perimeter, so the ratio moves a little between a striding
        frame and a standing one -- but only a little. The rim's ratio swung with whatever
        the arms overlapped.
        """
        for cycle in WALK_CYCLES:
            with self.subTest(cycle=cycle[0]):
                shares = []
                for name in cycle:
                    image = pixels(name)
                    solid = image[..., 3] > 128
                    shares.append(edge_band(image).sum() / max(1, solid.sum()))
                spread = max(shares) - min(shares)
                self.assertLess(
                    spread, 0.06,
                    f"{cycle[0][:-1]} frames carry visibly different amounts of outline: {shares}",
                )

    def test_the_still_occupants_match_the_walkers(self):
        """A head in a window is the same person as the one on the pavement."""
        walker = self.outline_colour("person_man_summer_walk0")
        for name in OCCUPANTS:
            with self.subTest(name=name):
                self.assertEqual(walker, self.outline_colour(name))

    def test_no_frame_lost_its_outline_to_a_source_edit(self):
        """The marker is in the source, so a hand edit that drops it fails here."""
        for cycle in WALK_CYCLES:
            for name in cycle:
                with self.subTest(name=name):
                    self.assertIn(
                        "paperscrape-outline", (SVG_DIR / f"{name}.svg").read_text(),
                        f"{name} has no outline group in its source",
                    )

    def test_the_outline_is_darker_than_what_it_surrounds(self):
        """Separation, not decoration: the band has to be darker than the figure it holds."""
        for cycle in WALK_CYCLES:
            name = cycle[0]
            with self.subTest(name=name):
                image = pixels(name)
                band = edge_band(image)
                interior = (image[..., 3] > 128) & ~edge_band(image, depth=3)
                if not interior.any():
                    continue
                self.assertLess(
                    image[band][:, :3].mean(), image[interior][:, :3].mean(),
                    f"{name}'s outline is not darker than its interior",
                )

    def test_every_outlined_sprite_still_matches_its_registry_geometry(self):
        """An outer outline grows the content box; the registry has to have followed it.

        Only sprites that *have* an SVG can be asked whether that SVG draws an outline.
        The registry has always allowed `source.kind = "none"` -- it is the format's main
        job to carry the sprites that cannot be regenerated -- and this loop read
        `<name>.svg` for every entry regardless, which held only while every shipped
        sprite happened to have a source. The skin-tone recolours are the first entries
        since then that do not, so the assumption is now spelled out rather than relied on.
        """
        for spec in registry.load(REGISTRY_PATH):
            if not spec.has_svg_source:
                continue
            if "paperscrape-outline" not in (SVG_DIR / spec.source_file).read_text():
                continue
            with self.subTest(name=spec.name):
                image = pixels(spec.name)
                ys, xs = np.nonzero(image[..., 3])
                measured = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
                self.assertEqual(tuple(spec.content_box), measured)


if __name__ == "__main__":
    unittest.main()
