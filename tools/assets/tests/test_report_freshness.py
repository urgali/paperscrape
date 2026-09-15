"""The check that says when a committed report has stopped describing the shipped artwork.

`BACKLOG_v5_1.md` item 125 opened with two facts established by measurement: nothing tested the
report generators, and the committed reports were stale. v5.2's documentation pass closed the
first half. This is the second -- *"nothing detects a stale committed artefact"* -- and the reason
it is worth a file of its own is the shape of the failure rather than its size.

`reports/runtime-inventory.json` and `reports/fidelity.json` both described `palmtree_fronds` as a
120x120 canvas with a 120x111 content box for the whole of v5.1 and into v5.2, while the shipped
drawing was 168x144 with content 157x119. The v5.1 palm redraw changed the artwork and never
re-ran `inventory` or `compare`. **No verdict changed and no number looked wrong**, because a
report that is internally consistent about the wrong sprite is internally consistent: the audit
that found it found it by measuring the PNG, not by reading the file. A rule in a checklist is
not a check, and item 125 says so in those words.

So `validate` now compares whatever of each report is a claim about a PNG -- the inventory's
per-file SHA-256 and fidelity's recorded reference size and content box -- against the file that
ships, and names the sprites. The cases below are about that contract: it must be silent on a
report that matches, and it must name the sprite and both geometries on a report that does not.

The 750 KB `comparison-sheet.png` is deliberately not compared: item 125 argues that one out, and
the rasteriser probe already guards the toolchain that draws it.

Run from `tools/assets/`:

    /home/bober/.venvs/paperscrape-assets/bin/python -m unittest discover -s tests
"""

from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

from paperscrape_assets import inventory, report  # noqa: E402

REPO_ROOT = TOOL_ROOT.parent.parent
RUNTIME_DIR = REPO_ROOT / "app/src/main/res/drawable-nodpi"
REPORTS_DIR = TOOL_ROOT / "reports"


class StaleReportTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.measurements = {m.name: m for m in inventory.measure_directory(RUNTIME_DIR)}

    def test_the_committed_reports_describe_the_shipped_artwork(self):
        """The state the tree must be delivered in, and the one it was NOT in at v5.2's start."""
        self.assertEqual([], report.stale_reports(REPORTS_DIR, self.measurements))

    def _with_patched_reports(self, patch) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            scratch = Path(tmp)
            for name in ("runtime-inventory.json", "fidelity.json"):
                shutil.copy(REPORTS_DIR / name, scratch / name)
            patch(scratch)
            return report.stale_reports(scratch, self.measurements)

    def test_an_inventory_entry_left_behind_by_a_redraw_is_named(self):
        """The v5.1 palm, replayed: the recorded geometry is the sprite that used to ship."""
        def patch(scratch: Path) -> None:
            path = scratch / "runtime-inventory.json"
            data = json.loads(path.read_text())
            for entry in data["sprites"]:
                if entry["name"] == "palmtree_fronds":
                    entry.update(sha256="0" * 64, width=120, height=120,
                                 content_width=120, content_height=111)
            path.write_text(json.dumps(data))

        problems = self._with_patched_reports(patch)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("palmtree_fronds", problems[0])
        # Both geometries, because "it is stale" is not actionable and "120x120 against 168x144" is.
        self.assertIn("120x120", problems[0])
        self.assertIn("168x144", problems[0])

    def test_a_fidelity_entry_left_behind_by_a_redraw_is_named(self):
        def patch(scratch: Path) -> None:
            path = scratch / "fidelity.json"
            data = json.loads(path.read_text())
            for result in data["results"]:
                if result["name"] == "palmtree_fronds":
                    result["reference_size"] = [120, 120]
            path.write_text(json.dumps(data))

        problems = self._with_patched_reports(patch)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("fidelity.json", problems[0])
        self.assertIn("palmtree_fronds", problems[0])

    def test_a_content_box_that_moved_without_the_canvas_is_caught_too(self):
        """A sprite cropped in place keeps its canvas and changes its box; the size check alone
        would miss it, which is why fidelity's bounding box is compared as well."""
        def patch(scratch: Path) -> None:
            path = scratch / "fidelity.json"
            data = json.loads(path.read_text())
            for result in data["results"]:
                if result["name"] == "tree_canopy":
                    result["reference_bbox"] = [4, 4, 300, 190]
            path.write_text(json.dumps(data))

        problems = self._with_patched_reports(patch)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("tree_canopy", problems[0])
        self.assertIn("content box", problems[0])

    def test_a_sprite_that_no_longer_ships_is_named_in_both_directions(self):
        def patch(scratch: Path) -> None:
            path = scratch / "runtime-inventory.json"
            data = json.loads(path.read_text())
            data["sprites"] = [e for e in data["sprites"] if e["name"] != "tree_trunk"]
            data["sprites"].append(dict(data["sprites"][0], name="a_sprite_that_was_deleted"))
            path.write_text(json.dumps(data))

        problems = self._with_patched_reports(patch)
        joined = "\n".join(problems)
        self.assertIn("a_sprite_that_was_deleted is recorded but no longer ships", joined)
        self.assertIn("tree_trunk ships but is not recorded", joined)

    def test_a_missing_report_is_not_a_stale_one(self):
        """A tree with no reports committed is a different question, and not this check's."""
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual([], report.stale_reports(Path(tmp), self.measurements))


if __name__ == "__main__":
    unittest.main()
