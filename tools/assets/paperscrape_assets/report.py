"""Report output: machine-readable JSON, readable markdown, and one image.

All three are written for every run. The JSON is what a later phase reads, the
markdown is what a human reviews, and the sheet is the only one of the three that
can catch the failure the numbers cannot: a reconstruction that scores well and
still looks wrong. `AI_PROJECT_RULES.md` section 6.8 requires that an asset be
looked at, not read about, and a metric table is reading about it.

The sheet composites each sprite over a mid-tone background rather than over
white or over a checkerboard. These sprites are white silhouettes: on white they
are invisible, and a checkerboard hides exactly the soft edge the comparison is
about.
"""

from __future__ import annotations

import json
import os
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from .fidelity import FidelityResult
from .inventory import SpriteMeasurement

#: Mid-tone slate. Chosen so a white silhouette and its antialiased edge are both
#: visible against it, and so the difference column's red reads clearly.
SHEET_BACKGROUND = (108, 132, 150, 255)
SHEET_CELL = 150
SHEET_LABEL_HEIGHT = 16


#: The four name prefixes `buildings/core.py`'s `budget` counts as its "shipped perimeter",
#: and the suffix it excludes. Duplicated from there rather than imported, because
#: `buildings/` is a standalone script directory that imports its own modules by bare name and
#: cannot be imported from inside this package without a `sys.path` edit at call time.
#: `tests/test_budget.py` asserts the two selections agree on the real tree, so the duplicate
#: is a checked one rather than a remembered one.
BUDGET_PERIMETER_PREFIXES = ("house", "skyscraper", "restaurant", "bar")
BUDGET_PERIMETER_EXCLUDES = "_q"

#: `Perimetro spedito (46 PNG, senza palma): 3480876 B decodificati, 3435092 B caricati ...`
_BUDGET_MD_PERIMETER = re.compile(
    r"Perimetro spedito \((\d+) PNG[^)]*\): (\d+) B decodificati, (\d+) B caricati"
)


def budget_perimeter(measurements: dict[str, "SpriteMeasurement"]) -> dict[str, int]:
    """`budget`'s `shipped_perimeter` block, re-derived from measurements already taken.

    `buildings/core.py`'s `budget` opens each PNG a second time to compute this. Here it comes
    out of the inventory pass `validate` has already run, so checking the committed budget for
    staleness costs `validate` nothing beyond the arithmetic -- the same reason
    :func:`stale_reports` compares the reports' own claims instead of re-running the generators.

    `uploaded_level0` is the ink bounding box grown by one texel on each side and clamped to the
    canvas, times four bytes: what the atlas uploads at mip level 0 once the fully transparent
    border is cropped away. The one-texel skirt is there so bilinear sampling at the edge reads a
    transparent neighbour rather than clamping.
    """
    total = {"files": 0, "decoded": 0, "uploaded_level0": 0}
    for name, m in measurements.items():
        if name.split("_")[0] not in BUDGET_PERIMETER_PREFIXES:
            continue
        if BUDGET_PERIMETER_EXCLUDES in name:
            continue
        total["files"] += 1
        total["decoded"] += m.decoded_bytes
        if m.content_bbox is not None:
            x0, y0, x1, y1 = m.content_bbox
            width = min(x1, m.width - 1) - max(x0 - 1, 0) + 1
            height = min(y1, m.height - 1) - max(y0 - 1, 0) + 1
            total["uploaded_level0"] += width * height * 4
    return total


def stale_reports(
    reports_dir: Path,
    measurements: dict[str, "SpriteMeasurement"],
    buildings_dir: Path | None = None,
) -> list[str]:
    """Which committed reports no longer describe the shipped artwork.

    **This check exists because both of them were found stale at once, and neither said so.**
    `reports/runtime-inventory.json` carried the pre-v5.1 palm -- 120x120 px with a 120x111
    content box -- for the whole of v5.1 and into v5.2, so anyone answering a question about the
    crown from the report got the geometry of a drawing that had been replaced (168x144, content
    157x119). `reports/fidelity.json` carried the same pre-v5.1 canvas. Both are the output of a
    command nobody re-ran after the redraw, and `CLAUDE.md` section 4's advice -- re-measure
    rather than trust them -- is advice a reader has to already suspect something to follow.

    A report going stale is not the defect; a report going stale **in silence** is. So this is run
    by `validate`, which the release checklist runs, and it names the sprites rather than saying
    the file is old: the repair is `inventory` and `compare`, and the point of the list is that
    the number of names tells you at a glance whether a redraw or a whole library moved.

    Each report is checked against whatever of itself is a claim about a PNG: the inventory's
    per-file SHA-256, fidelity's recorded reference size and content box, and -- since v5.5C --
    the neighbourhood budget's `shipped_perimeter` block.

    **What the budget half does and does not reach.** `buildings/budget.json` has two parts. Its
    `shipped_perimeter` is a claim about the PNGs in `drawable-nodpi`, so it is checked here, and
    `buildings/budget.md` is checked to still quote the same three numbers -- the two files are
    written by one statement and can only diverge if one of them is edited by hand, which is
    exactly the way a committed artefact starts lying. Its `concepts` block describes the PNGs
    under `buildings/out/`, which are a generator's scratch output, are not committed and are
    excluded from the delivered archive; there is nothing shipped to compare them against, and
    saying so here is better than a check that silently covers half a file.

    Until v5.5C the budget was the one committed report nothing looked at: item 125's second half
    was closed in v5.2 over two of the three, and the third was named in the item and missed.
    """
    problems: list[str] = []

    inventory_path = reports_dir / "runtime-inventory.json"
    if inventory_path.is_file():
        recorded = json.loads(inventory_path.read_text(encoding="utf-8")).get("sprites", [])
        names = {entry["name"] for entry in recorded}
        for name in sorted(names - set(measurements)):
            problems.append(f"runtime-inventory.json: {name} is recorded but no longer ships")
        for name in sorted(set(measurements) - names):
            problems.append(f"runtime-inventory.json: {name} ships but is not recorded")
        for entry in sorted(recorded, key=lambda e: e["name"]):
            live = measurements.get(entry["name"])
            if live is not None and entry.get("sha256") != live.sha256:
                problems.append(
                    f"runtime-inventory.json: {entry['name']} was measured at "
                    f"{entry.get('width')}x{entry.get('height')} content "
                    f"{entry.get('content_width')}x{entry.get('content_height')}; it now ships at "
                    f"{live.width}x{live.height} content {live.content_width}x{live.content_height}"
                )

    fidelity_path = reports_dir / "fidelity.json"
    if fidelity_path.is_file():
        for result in sorted(
            json.loads(fidelity_path.read_text(encoding="utf-8")).get("results", []),
            key=lambda r: r["name"],
        ):
            live = measurements.get(result["name"])
            if live is None:
                problems.append(f"fidelity.json: {result['name']} is recorded but no longer ships")
                continue
            recorded_size = tuple(result.get("reference_size") or ())
            if recorded_size and recorded_size != (live.width, live.height):
                problems.append(
                    f"fidelity.json: {result['name']} was compared against a "
                    f"{recorded_size[0]}x{recorded_size[1]} reference; it now ships at "
                    f"{live.width}x{live.height}"
                )
                continue
            recorded_bbox = result.get("reference_bbox")
            if recorded_bbox is not None and tuple(recorded_bbox) != tuple(live.content_bbox or ()):
                problems.append(
                    f"fidelity.json: {result['name']}'s reference content box was "
                    f"{tuple(recorded_bbox)}; it now ships as {live.content_bbox}"
                )

    if buildings_dir is not None:
        problems.extend(_stale_budget(buildings_dir, measurements))

    return problems


def _stale_budget(
    buildings_dir: Path,
    measurements: dict[str, "SpriteMeasurement"],
) -> list[str]:
    """`buildings/budget.json`'s shipped perimeter against the PNGs that ship, and `.md` against it."""
    problems: list[str] = []
    json_path = buildings_dir / "budget.json"
    if not json_path.is_file():
        return problems

    recorded = json.loads(json_path.read_text(encoding="utf-8")).get("shipped_perimeter") or {}
    live = budget_perimeter(measurements)
    for field in ("files", "decoded", "uploaded_level0"):
        if field in recorded and recorded[field] != live[field]:
            problems.append(
                f"buildings/budget.json: shipped_perimeter.{field} was recorded as "
                f"{recorded[field]}; the shipped PNGs now give {live[field]}"
            )

    md_path = buildings_dir / "budget.md"
    if md_path.is_file() and recorded:
        match = _BUDGET_MD_PERIMETER.search(md_path.read_text(encoding="utf-8"))
        if match is None:
            problems.append(
                "buildings/budget.md: the shipped-perimeter line is not there to be checked"
            )
        else:
            quoted = dict(zip(("files", "decoded", "uploaded_level0"), (int(g) for g in match.groups())))
            for field, value in quoted.items():
                if field in recorded and recorded[field] != value:
                    problems.append(
                        f"buildings/budget.md: quotes {field} as {value}; budget.json beside it "
                        f"records {recorded[field]}"
                    )

    return problems


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def inventory_markdown(measurements: list[SpriteMeasurement], duplicates: dict[str, list[str]]) -> str:
    total_decoded = sum(m.decoded_bytes for m in measurements)
    total_padding = sum(m.transparent_padding_bytes for m in measurements)
    total_files = sum(m.file_bytes for m in measurements)
    unique = len({m.sha256 for m in measurements})
    off_grid = [m.name for m in measurements if not m.on_grid]

    lines = [
        "# Runtime sprite inventory",
        "",
        "Generated by `paperscrape-assets inventory`. Every figure here is measured",
        "from the shipped PNGs; nothing is copied from documentation.",
        "",
        "| Metric | Value |",
        "|---|---|",
        f"| Files | {len(measurements)} |",
        f"| Unique contents | {unique} |",
        f"| Bytes on disk | {total_files / 1024:.1f} KB |",
        f"| Decoded `ARGB_8888` | {total_decoded / 1e6:.2f} MB |",
        f"| Of which transparent padding | {total_padding / 1e6:.2f} MB "
        f"({100 * total_padding / total_decoded:.0f} %) |",
        f"| Off the 3x authoring grid | {len(off_grid)} |",
        f"| Byte-identical duplicate groups | {len(duplicates)} |",
        "",
    ]

    if off_grid:
        lines += ["Off-grid: " + ", ".join(f"`{n}`" for n in sorted(off_grid)), ""]

    if duplicates:
        lines += ["## Byte-identical groups", "", "| Members |", "|---|"]
        for names in sorted(duplicates.values()):
            lines.append("| " + ", ".join(f"`{n}`" for n in names) + " |")
        lines.append("")

    lines += [
        "## Heaviest decoded sprites",
        "",
        "| Sprite | Size | Decoded | Padding |",
        "|---|---|---|---|",
    ]
    for m in sorted(measurements, key=lambda x: -x.decoded_bytes)[:10]:
        lines.append(
            f"| `{m.name}` | {m.width}x{m.height} | {m.decoded_bytes / 1e6:.2f} MB "
            f"| {100 * m.transparent_padding_fraction:.0f} % |"
        )
    lines.append("")

    lines += [
        "## Every sprite",
        "",
        "| Sprite | Size | Mode | Content bbox | Padding | Opaque RGB | Grid |",
        "|---|---|---|---|---|---|---|",
    ]
    for m in sorted(measurements, key=lambda x: x.name):
        box = "-" if m.content_bbox is None else ",".join(str(v) for v in m.content_bbox)
        lines.append(
            f"| `{m.name}` | {m.width}x{m.height} | {m.mode} | {box} "
            f"| {100 * m.transparent_padding_fraction:.0f} % | {m.opaque_rgb_count} "
            f"| {'yes' if m.on_grid else 'NO'} |"
        )
    return "\n".join(lines) + "\n"


def _wrap_names(names: list[str], width: int = 96) -> list[str]:
    """One comma-separated paragraph of sprite names, wrapped so a diff stays readable."""
    lines: list[str] = []
    current = ""
    for i, name in enumerate(names):
        piece = f"`{name}`" + ("," if i < len(names) - 1 else "")
        if current and len(current) + 1 + len(piece) > width:
            lines.append(current)
            current = piece
        else:
            current = f"{current} {piece}".strip()
    if current:
        lines.append(current)
    return lines


def _shared_reason(reasons: list[str]) -> str:
    """The reason a group of gaps shares, with whatever varies between them elided.

    The gap reasons are written per sprite and several of them differ only in the figure
    they name, so printing one per row means printing the same forty words two hundred
    times. This returns the text they genuinely share: identical reasons come back whole,
    and where they diverge the varying middle is replaced by a marker rather than by one
    row's arbitrary value.
    """
    first = reasons[0]
    if all(r == first for r in reasons):
        return first
    prefix = os.path.commonprefix(reasons)
    suffix = os.path.commonprefix([r[::-1] for r in reasons])[::-1]
    # The two halves are computed independently and can overlap on the shortest reason in
    # the group; trimming against that one keeps the result a substring of every member
    # rather than a sentence no reason actually contains.
    shortest = min(len(r) for r in reasons)
    if len(prefix) + len(suffix) > shortest:
        suffix = suffix[len(prefix) + len(suffix) - shortest:]
    return f"{prefix}<varies per sprite>{suffix}"


def fidelity_markdown(results: list[FidelityResult], gaps: list[tuple[str, str]]) -> str:
    """The readable half of `compare`, deliberately shorter than what it reports on.

    Every result and every gap is written in full to `fidelity.json` beside this file,
    so nothing here has to be exhaustive to be complete. What this document carries is
    what a reader decides something about: the criteria, the verdict counts, and the
    sprites that are *not* in the expected state. An exhaustive list of what is fine is
    not evidence that it is fine -- the verdict count is -- and printing it cost this
    report 11 000 of its 13 800 words, 267 of which were the same sentence repeated.
    """
    by_verdict: dict[str, int] = {}
    for r in results:
        by_verdict[r.verdict] = by_verdict.get(r.verdict, 0) + 1

    lines = [
        "# Staged reconstruction fidelity",
        "",
        "Generated by `paperscrape-assets compare`. Each sprite with a committed SVG source is",
        "rendered from it and compared against the PNG the app ships today.",
        "**No shipped PNG is modified by this pipeline.**",
        "",
        "Verdicts are defined in `paperscrape_assets/fidelity.py`. In short:",
        "`PIXEL_IDENTICAL` means all four channels match everywhere;",
        "`EDGE_EQUIVALENT` means no pixel where one image is solid and the other",
        "empty, an exact fill colour, and every differing pixel confined to the",
        "antialiased boundary; `DIVERGENT` means the geometry was not recovered.",
        "`IoU` is reported but does not gate: it is an area ratio, and an",
        "antialiased boundary is a fixed share of the perimeter, so a single",
        "absolute threshold asks small sprites for more precision than large ones.",
        "",
        f"| Verdict | Sprites |",
        "|---|---|",
    ]
    for verdict in ("PIXEL_IDENTICAL", "EDGE_EQUIVALENT", "DIVERGENT"):
        lines.append(f"| `{verdict}` | {by_verdict.get(verdict, 0)} |")
    lines += [f"| **compared** | **{len(results)}** |", ""]

    lines += [
        "**Nothing is elided from the record.** `fidelity.json`, written beside this file by the",
        "same run, carries every sprite with every measured column, and every gap with its own",
        "reason spelled out.",
        "This document carries what a reader has to decide something about; the JSON carries the",
        "measurement. A verdict count is the evidence that the set is in the expected state -- a",
        "list of rows saying so one at a time is the same evidence, at two hundred times the length.",
        "",
    ]

    off = [r for r in sorted(results, key=lambda x: (x.verdict, x.name)) if r.verdict != "PIXEL_IDENTICAL"]
    lines += ["## Sprites that are not `PIXEL_IDENTICAL`", ""]
    if not off:
        lines += [
            f"**None.** All {len(results)} compared sprites reconstruct pixel for pixel, so every",
            "column of the table that would appear here is zero by definition. It is listed in",
            "`fidelity.json` if you need to see it.",
            "",
        ]
    else:
        lines += [
            f"{len(off)} of {len(results)}. These are the rows to look at, and the comparison sheet",
            "beside this file is where to look at them rather than read about them.",
            "",
            "| Sprite | Size | IoU | Mean alpha diff | Max alpha diff "
            "| Differing px | Solid/empty conflicts | Edge-confined | Max RGB diff "
            "| bbox delta | Verdict |",
            "|---|---|---|---|---|---|---|---|---|---|---|",
        ]
        for r in off:
            delta = "-" if r.bbox_delta is None else ",".join(str(v) for v in r.bbox_delta)
            lines.append(
                f"| `{r.name}` | {r.reference_size[0]}x{r.reference_size[1]} "
                f"| {r.alpha_iou:.6f} | {r.mean_alpha_diff:.4f} | {r.max_alpha_diff} "
                f"| {r.differing_pixels} / {r.total_pixels} | {r.interior_alpha_mismatch} "
                f"| {'yes' if r.boundary_confined else 'NO'} "
                f"| {r.max_rgb_diff_where_opaque} | {delta} | `{r.verdict}` |"
            )
        lines.append("")

    lines += [
        f"## Sprites with no recoverable source ({len(gaps)})",
        "",
        "These ship today and cannot be regenerated from an SVG. The names are listed so the gap is",
        "a recorded state of the project rather than an omission from a report; the reason is",
        "written once per group, because it is the same reason.",
        "",
    ]
    groups: dict[str, list[tuple[str, str]]] = {}
    for name, reason in sorted(gaps):
        match = re.search(r"tools/[\w/.-]+\.py", reason)
        groups.setdefault(match.group(0) if match else "no generator recorded", []).append((name, reason))
    for key in sorted(groups):
        members = groups[key]
        lines += [
            f"### `{key}` -- {len(members)} sprites",
            "",
            _shared_reason([reason for _, reason in members]),
            "",
        ]
        lines += _wrap_names([name for name, _ in members])
        lines.append("")
    return "\n".join(lines).rstrip("\n") + "\n"


def _cell(image: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), SHEET_BACKGROUND)
    fitted = image.copy()
    fitted.thumbnail((size - 8, size - 8), Image.LANCZOS)
    canvas.alpha_composite(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2))
    return canvas


def _difference_image(reference: np.ndarray, candidate: np.ndarray) -> Image.Image:
    """Alpha difference, amplified, in red. Black means the two agree exactly."""
    diff = np.abs(reference[..., 3].astype(np.int32) - candidate[..., 3].astype(np.int32))
    amplified = np.clip(diff * 4, 0, 255).astype(np.uint8)
    out = np.zeros((*diff.shape, 4), dtype=np.uint8)
    out[..., 0] = amplified
    out[..., 3] = 255
    return Image.fromarray(out, mode="RGBA")


def comparison_sheet(
    path: Path,
    rows: list[tuple[str, np.ndarray, np.ndarray]],
    cell: int = SHEET_CELL,
) -> None:
    """Three columns per sprite: shipped, staged, amplified difference."""
    if not rows:
        return
    width = cell * 3
    row_height = cell + SHEET_LABEL_HEIGHT
    sheet = Image.new("RGBA", (width, row_height * len(rows)), (28, 32, 38, 255))
    draw = ImageDraw.Draw(sheet)

    for index, (name, reference, candidate) in enumerate(rows):
        y = index * row_height
        ref_image = Image.fromarray(reference, mode="RGBA")
        cand_image = Image.fromarray(candidate, mode="RGBA")
        sheet.paste(_cell(ref_image, cell), (0, y))
        sheet.paste(_cell(cand_image, cell), (cell, y))
        sheet.paste(_cell(_difference_image(reference, candidate), cell), (cell * 2, y))
        draw.text((6, y + cell + 3), f"{name}   shipped | staged | alpha diff x4",
                  fill=(220, 226, 232, 255))

    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(path, format="PNG", optimize=False, compress_level=9)
