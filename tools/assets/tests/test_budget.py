"""`buildings/core.py`'s `budget`: the third generated report, and the one nothing read.

`BACKLOG_v5_1.md` item 125 names three committed artefacts written by a tool -- `fidelity.md`,
`runtime-inventory.md` and `buildings/budget.md` -- and says of all three that nothing tests them.
v5.2 closed the fidelity third. v5.5C closes the other two, and the gap was demonstrated before it
was closed rather than argued: with `budget` mutated to write a one-line report, `unittest
discover -s tests` stayed green at 143 of 143 and `validate` exited 0, because no test and no
command in the release checklist ever calls it. `budget` is reached only by
`python3 -m buildings.build_neighbourhood --budget`, which is run by hand when the neighbourhood
artwork is regenerated.

Two things are checked, and they are different in kind.

**The arithmetic**, on synthetic PNGs in a temporary directory: the per-concept totals are the sum
of the rows beneath them, the verdict against `BUDGET` flips at the boundary with the sign the
reader acts on, and `uploaded_bytes` measures the ink box grown by one texel and clamped to the
canvas rather than the canvas itself -- which is the whole reason the two columns differ.

**The agreement**, on the real tree: `report.budget_perimeter` re-derives `shipped_perimeter` from
the inventory pass so that `validate` can check the committed budget for staleness without opening
every PNG a second time. That is a duplicated selection rule, and a duplicated rule that is not
checked is how item 125's reports went stale in the first place. The last case runs both and
requires them to agree.

Run from `tools/assets/`:

    /home/bober/.venvs/paperscrape-assets/bin/python -m unittest discover -s tests
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))
sys.path.insert(0, str(TOOL_ROOT / "buildings"))

import core  # noqa: E402
from paperscrape_assets import inventory, report  # noqa: E402

REPO_ROOT = TOOL_ROOT.parent.parent
RUNTIME_DIR = REPO_ROOT / "app/src/main/res/drawable-nodpi"
BUILDINGS_DIR = TOOL_ROOT / "buildings"


def _png(path: Path, width: int, height: int, ink: tuple[int, int, int, int]) -> None:
    """A canvas with an opaque rectangle inset by one pixel on every side.

    The inset is the point: a sprite whose ink fills its canvas cannot tell a report that measures
    the ink box from one that measures the canvas, and those are the two columns under test.
    """
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    image.paste((255, 255, 255, 255), ink)
    image.save(path)


class UploadedBytesTest(unittest.TestCase):
    """The `uploaded_level0` column: ink box, one texel of skirt, clamped to the canvas."""

    def test_the_skirt_is_one_texel_on_each_side(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "s.png"
            _png(path, 40, 30, (10, 10, 20, 20))  # ink box 10x10, so 12x12 with the skirt
            self.assertEqual(12 * 12 * 4, core.uploaded_bytes(path))

    def test_ink_touching_the_edge_clamps_instead_of_growing_past_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "s.png"
            _png(path, 40, 30, (0, 0, 40, 30))  # ink fills the canvas: nowhere to put a skirt
            self.assertEqual(40 * 30 * 4, core.uploaded_bytes(path))

    def test_it_is_smaller_than_the_canvas_whenever_there_is_padding(self):
        """Stated as its own case because it is the claim the budget rests on: cropping pays."""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "s.png"
            _png(path, 96, 96, (40, 40, 56, 56))
            self.assertLess(core.uploaded_bytes(path), 96 * 96 * 4)


class BudgetReportTest(unittest.TestCase):
    """What `budget` writes, measured on a perimeter and a concept it cannot get wrong by luck."""

    def _run(self, concept_sizes: dict[str, tuple[int, int]]):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        png_dir = root / "out"
        png_dir.mkdir()
        files = {}
        for name, (w, h) in concept_sizes.items():
            _png(png_dir / f"{name}.png", w, h, (1, 1, w - 1, h - 1))
            files[name] = (w, h)
        rep = core.budget(("mix",), {"mix": (files, png_dir)}, root)
        return rep, json.loads((root / "budget.json").read_text()), (root / "budget.md").read_text()

    def test_both_files_are_written_and_the_json_is_what_was_returned(self):
        rep, on_disk, md = self._run({"house_a": (24, 24)})
        self.assertEqual(rep, on_disk)
        self.assertIn("# Contabilita' dei concept", md)

    def test_the_concept_total_is_the_sum_of_its_rows(self):
        rep, _, _ = self._run({"house_a": (24, 24), "house_b": (36, 12), "bar_c": (60, 30)})
        mix = rep["concepts"]["mix"]
        self.assertEqual(3, mix["files"])
        self.assertEqual(sum(f["decoded"] for f in mix["per_file"].values()), mix["decoded"])
        self.assertEqual(sum(f["uploaded"] for f in mix["per_file"].values()), mix["uploaded_level0"])

    def test_every_png_gets_a_row_naming_its_size(self):
        _, _, md = self._run({"house_a": (24, 24), "bar_c": (60, 30)})
        self.assertIn("| house_a_q1 | 24x24 |", md)
        self.assertIn("| bar_c_q1 | 60x30 |", md)

    def test_a_concept_under_the_ceiling_says_so_with_the_room_left(self):
        rep, _, md = self._run({"house_a": (24, 24)})
        decoded = rep["concepts"]["mix"]["decoded"]
        self.assertIn(f"sotto il budget di {core.BUDGET - decoded:+d} B", md)

    def test_a_concept_over_the_ceiling_says_SOPRA_with_a_negative_margin(self):
        """The case the report exists for, and the one no run on the real tree has produced."""
        side = 1200  # 1200x1200x4 = 5.76 MB against a 4.26 MB ceiling
        rep, _, md = self._run({"house_big": (side, side)})
        decoded = rep["concepts"]["mix"]["decoded"]
        self.assertGreater(decoded, core.BUDGET)
        self.assertIn("SOPRA il budget di -", md)
        self.assertIn(f"SOPRA il budget di {core.BUDGET - decoded:+d} B", md)

    def test_the_shipped_perimeter_counts_the_six_families_and_not_the_concept_pngs(self):
        # 46 until v5.6F added the school's four layers. The number is pinned rather than
        # derived because the point of the assertion is that the perimeter is read from the
        # shipped drawable set and not from whatever the concept run happened to draw.
        rep, _, md = self._run({"house_a": (24, 24)})
        perimeter = rep["shipped_perimeter"]
        self.assertEqual(50, perimeter["files"])
        self.assertIn(f"Perimetro spedito ({perimeter['files']} PNG", md)
        # The concept's own PNG is not in it: the perimeter is read from the shipped drawable set.
        self.assertNotEqual(perimeter["files"], rep["concepts"]["mix"]["files"])


class PerimeterAgreementTest(unittest.TestCase):
    """The duplicated selection rule, checked instead of remembered.

    `report.budget_perimeter` exists so `validate` can catch a stale `buildings/budget.json`
    without opening 46 PNGs a second time. It repeats `budget`'s prefix list and its `_q`
    exclusion, and this is the case that makes the repetition safe: if either side's rule moves,
    the two stop agreeing here before a release ships a report that disagrees with itself.
    """

    def test_the_two_derivations_of_the_shipped_perimeter_agree(self):
        measurements = {m.name: m for m in inventory.measure_directory(RUNTIME_DIR)}
        derived = report.budget_perimeter(measurements)
        with tempfile.TemporaryDirectory() as tmp:
            generated = core.budget((), {}, Path(tmp))["shipped_perimeter"]
        self.assertEqual(generated, derived)

    def test_the_committed_budget_is_the_one_that_ships(self):
        """The state the tree must be delivered in -- and `validate` now says so too."""
        measurements = {m.name: m for m in inventory.measure_directory(RUNTIME_DIR)}
        self.assertEqual([], report._stale_budget(BUILDINGS_DIR, measurements))


if __name__ == "__main__":
    unittest.main()
