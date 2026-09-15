"""Tests for the readable half of `compare`: what `fidelity.md` must and must not say.

The report had no test at all until v5.2, and the gap was demonstrated rather than
assumed: with `fidelity_markdown` mutated to return a single line, the tooling suite
stayed green at 121 of 121 and `validate` stayed green too. Nothing read the file, so
nothing could tell a report from an empty string.

That mattered the moment the report was rewritten. It had grown to 13 801 words, of
which 11 026 were a per-sprite table of gaps whose "why" column repeated the same
forty words 267 times, and 2 636 more were a row per sprite saying that sprite was
`PIXEL_IDENTICAL` -- which is the verdict count restated one row at a time. The
rewrite drops both lists and keeps the counts, the criteria and the rows that are not
in the expected state.

So the cases below are about that contract, not about the report's length: a sprite
whose verdict is anything other than `PIXEL_IDENTICAL` must appear by name with its
numbers, a sprite that is `PIXEL_IDENTICAL` must not get a row of its own, and every
gap must be named once while its reason is written once per group. A length assertion
would need editing by whoever broke it, which is the opposite of a check.

Run from `tools/assets/`:

    /home/bober/.venvs/paperscrape-assets/bin/python -m unittest discover -s tests
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import report  # noqa: E402
from paperscrape_assets.fidelity import FidelityResult  # noqa: E402


def _result(name: str, verdict: str) -> FidelityResult:
    """A result carrying the fields the report prints, with the verdict under test."""
    identical = verdict == "PIXEL_IDENTICAL"
    return FidelityResult(
        name=name,
        size_match=True,
        reference_size=(12, 34),
        candidate_size=(12, 34),
        alpha_iou=1.0 if identical else 0.812345,
        mean_alpha_diff=0.0 if identical else 3.5,
        max_alpha_diff=0 if identical else 91,
        differing_pixels=0 if identical else 77,
        total_pixels=408,
        interior_alpha_mismatch=0 if identical else 5,
        boundary_confined=identical,
        max_rgb_diff_where_opaque=0 if identical else 4,
        reference_bbox=(0, 0, 12, 34),
        candidate_bbox=(0, 0, 12, 34),
        bbox_delta=(0, 0, 0, 0),
        reference_padding_fraction=0.0,
        candidate_padding_fraction=0.0,
        verdict=verdict,
    )


class VerdictSummaryTest(unittest.TestCase):
    def test_every_verdict_is_counted_including_the_empty_ones(self):
        text = report.fidelity_markdown([_result("a", "PIXEL_IDENTICAL")], [])
        self.assertIn("| `PIXEL_IDENTICAL` | 1 |", text)
        self.assertIn("| `EDGE_EQUIVALENT` | 0 |", text)
        self.assertIn("| `DIVERGENT` | 0 |", text)

    def test_the_compared_total_is_stated(self):
        results = [_result(f"s{i}", "PIXEL_IDENTICAL") for i in range(7)]
        self.assertIn("| **compared** | **7** |", report.fidelity_markdown(results, []))

    def test_the_verdict_criteria_survive_the_shortening(self):
        """The report is shorter, not thinner: what a verdict means is how it is read."""
        text = report.fidelity_markdown([_result("a", "PIXEL_IDENTICAL")], [])
        for phrase in ("all four channels match everywhere", "does not gate", "fidelity.py"):
            self.assertIn(phrase, text)


class OffVerdictTest(unittest.TestCase):
    def test_a_divergent_sprite_is_named_with_its_numbers(self):
        text = report.fidelity_markdown(
            [_result("good", "PIXEL_IDENTICAL"), _result("broken", "DIVERGENT")], []
        )
        self.assertIn("`broken`", text)
        self.assertIn("0.812345", text)
        self.assertIn("77 / 408", text)
        self.assertIn("| NO |", text)

    def test_an_edge_equivalent_sprite_is_named_too(self):
        text = report.fidelity_markdown([_result("soft", "EDGE_EQUIVALENT")], [])
        self.assertIn("`soft`", text)

    def test_a_pixel_identical_sprite_gets_no_row_of_its_own(self):
        """The whole saving: 104 rows of zeros say what one verdict count says."""
        text = report.fidelity_markdown([_result("quiet", "PIXEL_IDENTICAL")], [])
        self.assertNotIn("`quiet`", text)

    def test_an_all_identical_run_says_so_rather_than_printing_an_empty_table(self):
        text = report.fidelity_markdown([_result(f"s{i}", "PIXEL_IDENTICAL") for i in range(3)], [])
        self.assertIn("**None.**", text)
        self.assertNotIn("| Sprite | Size | IoU |", text)


class GapTest(unittest.TestCase):
    SAME = (
        "Written by tools/generate_people_layers.py from person_{}'s own drawing. "
        "Re-run that script to regenerate it, not `render`."
    )

    def test_every_gap_is_named_exactly_once(self):
        gaps = [(f"person_{i}_fx", self.SAME.format(i)) for i in range(4)]
        text = report.fidelity_markdown([], gaps)
        for name, _ in gaps:
            self.assertEqual(text.count(f"`{name}`"), 1, name)

    def test_the_shared_reason_is_written_once_not_once_per_sprite(self):
        gaps = [(f"person_{i}_fx", self.SAME.format(i)) for i in range(40)]
        text = report.fidelity_markdown([], gaps)
        self.assertEqual(text.count("Re-run that script to regenerate it"), 1)
        self.assertIn("<varies per sprite>", text)

    def test_identical_reasons_are_printed_whole(self):
        reason = "Written by tools/assets/buildings/build_neighbourhood.py. No SVG of its own."
        text = report.fidelity_markdown([], [(f"tower_{i}", reason) for i in range(5)])
        self.assertIn(reason, text)
        self.assertNotIn("<varies per sprite>", text)

    def test_gaps_are_grouped_by_the_script_that_writes_them(self):
        gaps = [
            ("a_fx", "Written by tools/one.py. No SVG."),
            ("b_fx", "Written by tools/two.py. No SVG."),
        ]
        text = report.fidelity_markdown([], gaps)
        self.assertIn("### `tools/one.py` -- 1 sprites", text)
        self.assertIn("### `tools/two.py` -- 1 sprites", text)

    def test_the_gap_total_is_stated(self):
        text = report.fidelity_markdown([], [(f"x{i}", "Written by tools/one.py.") for i in range(9)])
        self.assertIn("## Sprites with no recoverable source (9)", text)


class SharedReasonTest(unittest.TestCase):
    def test_identical_strings_come_back_unchanged(self):
        self.assertEqual(report._shared_reason(["abc", "abc"]), "abc")

    def test_the_varying_middle_is_marked(self):
        self.assertEqual(report._shared_reason(["x1y", "x2y"]), "x<varies per sprite>y")

    def test_the_result_never_claims_more_than_the_shortest_reason_holds(self):
        """Prefix and suffix are found independently and can overlap on a short member."""
        folded = report._shared_reason(["aaa", "aa"])
        self.assertEqual(folded, "aa<varies per sprite>")
        self.assertLessEqual(len(folded.replace("<varies per sprite>", "")), 2)

    def test_nothing_in_common_folds_to_the_marker_alone(self):
        self.assertEqual(report._shared_reason(["ab", "ba"]), "<varies per sprite>")


if __name__ == "__main__":
    unittest.main()
