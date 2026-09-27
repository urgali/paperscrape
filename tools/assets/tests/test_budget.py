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
of the rows beneath them, the report passes no verdict of its own (v5.8B: `core.BUDGET` is gone),
and `uploaded_bytes` measures the ink box grown by one texel and clamped to the
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
        self.assertIn("# The neighbourhood's sprite bytes", md)

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

    def test_the_report_measures_and_passes_no_verdict_of_its_own(self):
        """v5.8B, item 138: `core.BUDGET` is gone, and the report says where the ceiling is.

        What stood here were two tests of the verdict -- under the ceiling and over it, printed in
        Italian -- for a ceiling nothing read and the shipped set had been over since v5.0.
        A report that prints a failure every release reads past teaches people to read past
        failures; this one prints the measure and names the gate that judges it.
        """
        rep, _, md = self._run({"house_big": (1200, 1200)})
        self.assertFalse(hasattr(core, "BUDGET"))
        self.assertNotRegex(md, r"(?i)\b(over|under|above|below) the (budget|ceiling)\b")
        self.assertIn("SpriteGeometryTest.decodedByteBudget", md)
        self.assertIn(f"{rep['concepts']['mix']['decoded']} B decoded", md)

    def test_the_shipped_perimeter_counts_the_six_families_and_not_the_concept_pngs(self):
        # 46 until v5.6F added the school's four layers, and 50 until v5.8B, when the towers'
        # 26 were added: the prefix list said `skyscraper` and the towers are `tower_*`, so the
        # test's own name ("six families") was the one thing it did not check. The number is
        # pinned rather than derived because the point is that the perimeter is read from the
        # shipped drawable set and not from whatever the concept run happened to draw.
        rep, _, md = self._run({"house_a": (24, 24)})
        perimeter = rep["shipped_perimeter"]
        self.assertEqual(76, perimeter["files"])
        self.assertIn(f"Shipped perimeter ({perimeter['files']} PNG", md)
        towers = [n for n in (p.stem for p in RUNTIME_DIR.glob("tower_*.png"))]
        self.assertEqual(26, len(towers), "the towers are part of the perimeter")
        # The concept's own PNG is not in it: the perimeter is read from the shipped drawable set.
        self.assertNotEqual(perimeter["files"], rep["concepts"]["mix"]["files"])

    def test_what_ships_is_what_the_generator_draws(self):
        """The committed report, read: the shipped perimeter and the mix are the same bytes.

        Only true since the perimeter counts all six families; with the towers missing, the two
        figures in `budget.md` differed by 1 122 984 B and the report compared them as if they
        were one thing over its budget.
        """
        data = json.loads((BUILDINGS_DIR / "budget.json").read_text())
        mix = data["concepts"]["mix"]
        self.assertEqual(mix["files"], data["shipped_perimeter"]["files"])
        self.assertEqual(mix["decoded"], data["shipped_perimeter"]["decoded"])


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
