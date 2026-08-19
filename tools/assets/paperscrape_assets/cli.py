"""Command line entry point.

    python3 -m paperscrape_assets <command>

Commands
--------
``probe``      fingerprint the rasteriser toolchain
``inventory``  measure the shipped PNGs
``validate``   check the registry against what actually ships
``normalize``  check -- or with ``--apply``, perform -- padding and grid normalisation
``fit``        recover rectangular geometry from a shipped PNG
``render``     render every SVG source into staging/
``compare``    measure staged renders against the shipped PNGs
``all``        probe, inventory, validate, normalize, render, compare

Every path defaults relative to the repository root, found by walking up from
this file. `render` never writes to `app/src/main/res/`: staged output is
compared against the shipped sprites, never substituted for them, and `--out`
cannot be pointed at the runtime directory (see `_reject_runtime_directory`).

`normalize --apply` is the one command that does write there, and the difference
is the point rather than an exception to it. `render` produces *new artwork* from
a source, which is a visual decision; `normalize` removes rows and columns whose
alpha is zero, which changes no visible pixel and is reversible arithmetic at the
call site. Without ``--apply`` it only measures, and it is part of ``all`` in that
form, so a sprite that regains padding later is reported rather than accumulated.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from . import callsites, fidelity, fit, inventory, normalize, raster, registry, report

TOOL_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = TOOL_ROOT.parent.parent
RUNTIME_DIR = REPO_ROOT / "app/src/main/res/drawable-nodpi"
KOTLIN_SOURCE_DIR = REPO_ROOT / "app/src"
SOURCES_DIR = TOOL_ROOT / "sources"
SVG_DIR = SOURCES_DIR / "svg"
REGISTRY_PATH = SOURCES_DIR / "sprites.json"
STAGING_DIR = TOOL_ROOT / "staging"
REPORTS_DIR = TOOL_ROOT / "reports"


def _reject_runtime_directory(path: Path) -> Path:
    """Refuse to write anywhere inside the directory the app reads.

    The rule that staged output never becomes runtime output is enforced here
    rather than left to whoever types the command. Replacing a shipped sprite is
    a decision with a visual approval attached to it, not a `--out` flag.
    """
    resolved = path.resolve()
    if resolved == RUNTIME_DIR.resolve() or RUNTIME_DIR.resolve() in resolved.parents:
        raise SystemExit(
            f"refusing to write into the runtime asset directory: {resolved}\n"
            "Staged output is compared against the shipped sprites, never substituted "
            "for them."
        )
    return path


def _load_runtime() -> dict[str, inventory.SpriteMeasurement]:
    return {m.name: m for m in inventory.measure_directory(RUNTIME_DIR)}


def _runtime_pixels(name: str) -> np.ndarray:
    with Image.open(RUNTIME_DIR / f"{name}.png") as image:
        return np.array(image.convert("RGBA"))


def _referenced_names() -> set[str]:
    """Drawable names reachable from Kotlin, read from the sources themselves."""
    names: set[str] = set()
    for path in (REPO_ROOT / "app/src").rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        marker = "R.drawable."
        start = 0
        while True:
            index = text.find(marker, start)
            if index < 0:
                break
            cursor = index + len(marker)
            end = cursor
            while end < len(text) and (text[end].isalnum() or text[end] == "_"):
                end += 1
            names.add(text[cursor:end])
            start = end
    return names


def cmd_probe(args: argparse.Namespace) -> int:
    result = raster.probe()
    report.write_json(REPORTS_DIR / "rasterizer-probe.json", result)
    for key, value in result.items():
        print(f"{key}: {value}")
    if not result["matches_expected"]:
        print(
            "\nThe toolchain fingerprint does not match the pinned expectation.\n"
            "Every fidelity figure in reports/ was measured with a different "
            "rasteriser and must be re-measured before being relied on.",
            file=sys.stderr,
        )
        return 0 if args.allow_mismatch else 1
    return 0


def cmd_inventory(_: argparse.Namespace) -> int:
    measurements = inventory.measure_directory(RUNTIME_DIR)
    duplicates = inventory.duplicate_groups(measurements)
    report.write_json(
        REPORTS_DIR / "runtime-inventory.json",
        {
            "sprites": [m.as_dict() for m in measurements],
            "duplicateGroups": duplicates,
        },
    )
    report.write_text(
        REPORTS_DIR / "runtime-inventory.md",
        report.inventory_markdown(measurements, duplicates),
    )
    decoded = sum(m.decoded_bytes for m in measurements)
    padding = sum(m.transparent_padding_bytes for m in measurements)
    print(
        f"{len(measurements)} files, {len({m.sha256 for m in measurements})} unique, "
        f"{decoded / 1e6:.2f} MB decoded, {padding / 1e6:.2f} MB padding, "
        f"{len(duplicates)} duplicate groups"
    )
    return 0


def cmd_validate(_: argparse.Namespace) -> int:
    specs = registry.load(REGISTRY_PATH)
    runtime = _load_runtime()
    problems = registry.validate_against_runtime(
        specs,
        runtime_names=set(runtime),
        measured_sizes={name: (m.width, m.height) for name, m in runtime.items()},
        measured_content_boxes={name: m.content_bbox for name, m in runtime.items()},
        svg_dir=SVG_DIR,
    )

    variants = registry.load_variants(REGISTRY_PATH, {s.name for s in specs})
    problems.extend(
        registry.validate_variants(variants, {name: m.sha256 for name, m in runtime.items()})
    )

    referenced = _referenced_names()
    for spec in specs:
        expected = "referenced" if spec.name in referenced else "orphan"
        if spec.usage != expected:
            problems.append(
                f"{spec.name}: registry says {spec.usage}, Kotlin sources say {expected}"
            )

    sites_by_sprite, unattributed = callsites.scan_sources(KOTLIN_SOURCE_DIR)
    site_problems, unresolved = registry.validate_against_callsites(specs, sites_by_sprite)
    problems.extend(site_problems)

    if problems:
        for problem in problems:
            print(f"FAIL {problem}", file=sys.stderr)
        return 1

    with_source = sum(1 for s in specs if s.has_svg_source)
    with_anchor = sum(1 for s in specs if s.has_anchor)
    scale_tint_checked = sum(1 for s in specs if sites_by_sprite.get(s.name))
    origin_checked = sum(
        1
        for s in specs
        if s.has_anchor
        and any(site.origin is not None for site in sites_by_sprite.get(s.name, []))
    )
    print(
        f"registry OK: {len(specs)} entries, {with_source} with an SVG source, "
        f"{len(specs) - with_source} recorded as gaps"
    )
    print(f"anchors: {with_anchor} determined, {len(specs) - with_anchor} undetermined")
    gaps = [v for v in variants if not v.must_differ]
    print(
        f"variants: {len(variants)} groups checked against the shipped bytes, "
        f"{len(variants) - len(gaps)} distinct, {len(gaps)} declared identical gaps"
    )
    for gap in gaps:
        print(f"  gap {gap.id}: {', '.join(gap.members)}")
    # Printed on success, not only on failure, and split per check rather than
    # lumped: a sprite whose scale was compared but whose origin could not be is
    # neither fully checked nor unchecked, and one combined figure would have to
    # round it to whichever is more flattering.
    print(
        f"call-site check: scale and tint compared for {scale_tint_checked} sprites, "
        f"origin for {origin_checked}; {len(unresolved)} sprites carry at least one "
        f"unresolved item"
    )
    for expression in sorted({site.expression for site in unattributed}):
        print(f"  blit with a non-literal sprite argument: {expression}")
    return 0


def _format_anchor(value: float) -> str:
    """Match the registry's own number style: integers stay integers."""
    return str(int(value)) if float(value).is_integer() else repr(float(value))


def _rewrite_registry_geometry(
    path: Path,
    updates: dict[str, tuple[int, int, tuple[int, int, int, int]]],
    specs: list[registry.SpriteSpec],
) -> None:
    """Patch the geometry fields in place, leaving the file's shape alone.

    A load-and-dump round trip would reformat all 118 entries and bury a
    four-field change in a whole-file diff, so each entry is edited as text
    inside its own object and nothing else in the document is touched.

    `anchor` is re-derived rather than carried over, but **only for the two rules
    that derive it from the box**. `CONTENT_BOTTOM_CENTRE` reads the content box
    and `SPRITE_CENTRE` reads the canvas, so a crop moves either by exactly the
    amount the origin is compensated by, and leaving the old value would declare an
    anchor that no rule produces. `PART_LOCAL` and `DECLARED_ATTACHMENT` are
    *declarations* -- a part's own zero, and the joint another sprite attaches to --
    and `derive_anchor` returns `None` for both by design.

    Guarding this on `has_anchor` instead was what aborted the v76.9 normalisation
    partway through, on `bar_sign`, with a message saying `PART_LOCAL` no longer
    held. It held perfectly well; the applier was asking a derivation question of a
    rule that does not answer one, and the abort was recorded as an anchor-model
    conflict rather than as the tooling defect it was. `validate` re-derives every
    anchor afterwards, so a mistake here still fails there.
    """
    by_name = {s.name: s for s in specs}
    text = path.read_text(encoding="utf-8")
    for name, (width, height, box) in updates.items():
        marker = f'"name": "{name}"'
        start = text.index(marker)
        end = text.index("\n    }", start)
        entry = text[start:end]
        for field, value in (("width", str(width)), ("height", str(height))):
            entry = re.sub(rf'("{field}": )\d+', rf"\g<1>{value}", entry, count=1)
        entry = re.sub(
            r'("contentBox": )\[[^\]]*\]',
            rf"\g<1>[{box[0]}, {box[1]}, {box[2]}, {box[3]}]",
            entry,
            count=1,
        )
        spec = by_name[name]
        if spec.derives_anchor_from_box:
            # In pixels, deliberately: `derive_anchor`'s own docstring records that
            # passing `units_per_pixel` here produces local units and then writes
            # them into a field the registry declares in pixels, so every
            # `SCENE_UNITS` sprite ends up disagreeing with itself by a factor of
            # three. This call site was making exactly that mistake, unexercised
            # because no `--apply` run had ever completed.
            anchor = registry.derive_anchor(spec.anchor_rule, box, (width, height))
            if anchor is None:
                raise SystemExit(
                    f"{name}: {spec.anchor_rule} no longer holds after normalisation -- "
                    "the crop moved the content off the point the rule names"
                )
            entry = re.sub(
                r'("anchor": )\[[^\]]*\]',
                rf"\g<1>[{_format_anchor(anchor[0])}, {_format_anchor(anchor[1])}]",
                entry,
                count=1,
            )
        text = text[:start] + entry + text[end:]
    path.write_text(text, encoding="utf-8")


def _resize_svg_canvas(path: Path, box: tuple[int, int, int, int]) -> None:
    """Move a source document's canvas onto ``box`` without moving its drawing.

    The drawing itself is never touched. What changes is the `viewBox`, whose
    origin moves to the crop's top-left corner and whose extent shrinks to the
    crop's size, both converted from pixels by the scale the `width`/`height` pair
    already carries -- `SPRITE_PIXELS_PER_UNIT` for every V2 source. Every
    coordinate written in the document keeps its value and its meaning, and the
    rendered result is the same drawing with the same pixels removed that the PNG
    crop removed.

    The origin is the part that is easy to leave out. A trailing-only crop leaves
    it at zero and the temptation is to hardcode that; a crop that removes columns
    on the left has to shift the view by exactly those columns, or the source
    renders the drawing where the PNG no longer has it.

    Without this the PNG and its source would disagree, and
    `ShippedAgainstSourceTest` -- the measurement D-7's closure rests on -- would
    fail on the first sprite cropped.
    """
    text = path.read_text(encoding="utf-8")
    match = re.search(
        r'width="([\d.]+)" height="([\d.]+)" '
        r'viewBox="([-\d.]+) ([-\d.]+) ([\d.]+) ([\d.]+)"',
        text,
    )
    if match is None:
        raise SystemExit(f"{path.name}: no width/height/viewBox triple to resize")
    width, height, view_x, view_y, view_width, view_height = (
        float(g) for g in match.groups()
    )
    scale = width / view_width
    if height / view_height != scale:
        raise SystemExit(f"{path.name}: width and height disagree on the viewBox scale")
    size = (box[2] - box[0], box[3] - box[1])
    new_view = (
        view_x + box[0] / scale,
        view_y + box[1] / scale,
        size[0] / scale,
        size[1] / scale,
    )
    if any(v != int(v) for v in new_view):
        raise SystemExit(f"{path.name}: the crop is not a whole number of user units")
    replacement = (
        f'width="{size[0]}" height="{size[1]}" viewBox="'
        + " ".join(str(int(v)) for v in new_view)
        + '"'
    )
    text = text[: match.start()] + replacement + text[match.end() :]
    path.write_text(text, encoding="utf-8")


def _crop_targets(
    targets: list[normalize.Normalisation], specs: list[registry.SpriteSpec]
) -> int:
    """Crop each target's PNGs to its box and keep the source and registry with them."""
    updates: dict[str, tuple[int, int, tuple[int, int, int, int]]] = {}
    for item in targets:
        for name in item.members:
            path = RUNTIME_DIR / f"{name}.png"
            with Image.open(path) as image:
                pixels = np.array(image.convert("RGBA"))
                cropped = image.crop(item.box)
            # A crop that removes an opaque pixel is a redraw, not a normalisation.
            # Checked here rather than trusted from the plan, because this is the
            # step that cannot be undone from the repository.
            discarded = np.concatenate(
                (
                    pixels[:, item.box[2] :, 3].reshape(-1),
                    pixels[item.box[3] :, : item.box[2], 3].reshape(-1),
                )
            )
            if discarded.size and int(discarded.max()) != 0:
                raise SystemExit(
                    f"{name}: the crop would discard a pixel with alpha "
                    f"{int(discarded.max())}, which is artwork rather than padding"
                )
            cropped.save(path, format="PNG", optimize=True)
            with Image.open(path) as written:
                box = written.convert("RGBA").getchannel("A").getbbox()
                updates[name] = (written.width, written.height, tuple(box))
            source = SVG_DIR / f"{name}.svg"
            if source.is_file():
                _resize_svg_canvas(source, item.box)
    _rewrite_registry_geometry(REGISTRY_PATH, updates, specs)
    return len(updates)


def cmd_normalize(args: argparse.Namespace) -> int:
    specs = registry.load(REGISTRY_PATH)
    scales = {s.name: s.scale for s in specs}
    measurements = _load_runtime()
    referenced = _referenced_names()
    plans = normalize.plan(measurements, scales, referenced)
    outstanding = normalize.pending(plans)
    anchor_rules = {s.name: s.anchor_rule for s in specs}
    trailing = normalize.trailing_plan(outstanding, anchor_rules)

    if getattr(args, "apply_trailing", False):
        cropped = _crop_targets(trailing, specs)
        recovered = sum(item.recovered_bytes for item in trailing)
        print(f"trailing-cropped {cropped} PNG(s) across {len(trailing)} target(s)")
        print(f"decoded ARGB_8888 recovered: {recovered / 1e6:.2f} MB")
        print("no origin compensation is due: pixel (0,0) did not move for any of them")
        return 0

    if not args.apply:
        for item in outstanding:
            dx, dy = item.compensation
            print(
                f"{item.key:26} {item.size[0]}x{item.size[1]} -> "
                f"{item.new_size[0]}x{item.new_size[1]}  "
                f"origin +({dx:g},{dy:g}) units  "
                f"{len(item.members)} file(s), {item.recovered_bytes / 1e6:.2f} MB"
            )
        if trailing:
            print(
                f"\nof those, {len(trailing)} target(s) still have padding on the right or "
                "bottom only, which `--apply-trailing` removes with no origin to compensate."
            )
        if outstanding:
            print(
                f"\n{len(outstanding)} target(s) still carry removable padding. "
                "Re-run with --apply, and compensate every origin listed above in the same "
                "change: a crop without its compensation moves the sprite.",
                file=sys.stderr,
            )
            return 1
        print(
            f"normalisation OK: {len(plans)} target(s) checked, none carries removable padding; "
            f"{len(normalize.EXCLUSIONS)} sprite(s) excluded by decision"
        )
        return 0

    cropped = _crop_targets(outstanding, specs)
    recovered = sum(item.recovered_bytes for item in outstanding)
    print(f"normalised {cropped} PNG(s) across {len(outstanding)} target(s)")
    print(f"decoded ARGB_8888 recovered: {recovered / 1e6:.2f} MB")
    print("origin compensations to apply at the call sites:")
    for item in outstanding:
        dx, dy = item.compensation
        where = item.origin_site or "single call site"
        print(f"  {item.key:26} +({dx:g},{dy:g}) units   {where}")
    return 0


def cmd_fit(args: argparse.Namespace) -> int:
    results = []
    for name in args.names:
        result = fit.fit_rounded_rect(name, _runtime_pixels(name), step=args.step)
        results.append(result)
        print(
            f"{name:24} {result.width}x{result.height:<6} "
            f"best r={result.best_radius:g} (mean diff {result.best_mean_alpha_diff:.4f})  "
            f"snapped r={result.snapped_radius:g} (mean diff {result.snapped_mean_alpha_diff:.4f})  "
            f"-> use r={result.recommended_radius:g}"
        )
        if args.emit:
            note = (
                f"{name}: geometry recovered by `paperscrape-assets fit`, not authored by eye. "
                f"Corner radius swept against the shipped PNG's alpha channel: best {result.best_radius:g} "
                f"(mean alpha error {result.best_mean_alpha_diff:.4f}/255), "
                f"grid-snapped {result.snapped_radius:g} "
                f"(mean alpha error {result.snapped_mean_alpha_diff:.4f}/255); "
                f"{result.recommended_radius:g} used. White fill: this sprite is a tint mask, "
                "and its colour comes from the runtime MULTIPLY filter."
            )
            svg = fit.rounded_rect_svg(
                result.width, result.height, result.recommended_radius, note=note
            )
            SVG_DIR.mkdir(parents=True, exist_ok=True)
            (SVG_DIR / f"{name}.svg").write_text(svg, encoding="utf-8")
    report.write_json(
        REPORTS_DIR / "geometry-fit.json",
        [
            {
                "name": r.name,
                "width": r.width,
                "height": r.height,
                "bestRadius": r.best_radius,
                "bestMeanAlphaDiff": r.best_mean_alpha_diff,
                "snappedRadius": r.snapped_radius,
                "snappedMeanAlphaDiff": r.snapped_mean_alpha_diff,
                "snapCost": r.snap_cost,
                "recommendedRadius": r.recommended_radius,
            }
            for r in results
        ],
    )
    return 0


def cmd_render(args: argparse.Namespace) -> int:
    out = _reject_runtime_directory(Path(args.out))
    out.mkdir(parents=True, exist_ok=True)
    specs = [s for s in registry.load(REGISTRY_PATH) if s.has_svg_source]
    for spec in specs:
        rendered = raster.render_svg_file(SVG_DIR / spec.source_file)
        width, height = rendered.size
        if (width, height) != (spec.width, spec.height):
            print(
                f"FAIL {spec.name}: source renders {width}x{height}, "
                f"registry declares {spec.width}x{spec.height}",
                file=sys.stderr,
            )
            return 1
        (out / f"{spec.name}.png").write_bytes(rendered.png_bytes)
    print(f"rendered {len(specs)} sprites into {out}")
    return 0


def cmd_compare(args: argparse.Namespace) -> int:
    staging = Path(args.staging)
    specs = registry.load(REGISTRY_PATH)
    results = []
    sheet_rows = []
    for spec in specs:
        if not spec.has_svg_source:
            continue
        staged_path = staging / f"{spec.name}.png"
        if not staged_path.is_file():
            print(f"FAIL {spec.name}: nothing staged at {staged_path}", file=sys.stderr)
            return 1
        with Image.open(staged_path) as image:
            candidate = np.array(image.convert("RGBA"))
        reference = _runtime_pixels(spec.name)
        results.append(fidelity.compare(spec.name, reference, candidate))
        sheet_rows.append((spec.name, reference, candidate))

    gaps = [
        (s.name, s.source_reason or "no reason recorded")
        for s in specs
        if not s.has_svg_source
    ]

    report.write_json(
        REPORTS_DIR / "fidelity.json",
        {"results": [r.as_dict() for r in results], "gaps": dict(gaps)},
    )
    report.write_text(REPORTS_DIR / "fidelity.md", report.fidelity_markdown(results, gaps))
    report.comparison_sheet(REPORTS_DIR / "comparison-sheet.png", sheet_rows)

    counts: dict[str, int] = {}
    for r in results:
        counts[r.verdict] = counts.get(r.verdict, 0) + 1
    print(", ".join(f"{verdict}: {count}" for verdict, count in sorted(counts.items())))
    print(f"{len(gaps)} sprites recorded as having no recoverable source")
    return 1 if counts.get("DIVERGENT") else 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="paperscrape-assets", description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    probe = sub.add_parser("probe", help="fingerprint the rasteriser toolchain")
    probe.add_argument("--allow-mismatch", action="store_true",
                       help="report a fingerprint mismatch without failing")
    probe.set_defaults(func=cmd_probe)

    sub.add_parser("inventory", help="measure the shipped PNGs").set_defaults(func=cmd_inventory)
    sub.add_parser("validate", help="check the registry against the shipped set").set_defaults(
        func=cmd_validate
    )

    normalise = sub.add_parser(
        "normalize", help="check padding and grid normalisation, or perform it"
    )
    normalise.add_argument(
        "--apply",
        action="store_true",
        help="crop the shipped PNGs to their normalised boxes and update the registry",
    )
    normalise.add_argument(
        "--apply-trailing",
        action="store_true",
        help="crop only the padding on the right and bottom, which needs no origin compensation",
    )
    normalise.set_defaults(func=cmd_normalize)

    fit_parser = sub.add_parser("fit", help="recover rectangular geometry from a shipped PNG")
    fit_parser.add_argument("names", nargs="+")
    fit_parser.add_argument("--step", type=float, default=0.1)
    fit_parser.add_argument(
        "--emit",
        action="store_true",
        help="write the fitted geometry to sources/svg/<name>.svg",
    )
    fit_parser.set_defaults(func=cmd_fit)

    render = sub.add_parser("render", help="render SVG sources into staging")
    render.add_argument("--out", default=str(STAGING_DIR))
    render.set_defaults(func=cmd_render)

    compare = sub.add_parser("compare", help="measure staged renders against the shipped PNGs")
    compare.add_argument("--staging", default=str(STAGING_DIR))
    compare.set_defaults(func=cmd_compare)

    every = sub.add_parser("all", help="probe, inventory, validate, render, compare")
    every.set_defaults(func=cmd_all)
    return parser


def cmd_all(args: argparse.Namespace) -> int:
    args.allow_mismatch = False
    args.out = str(STAGING_DIR)
    args.staging = str(STAGING_DIR)
    args.apply = False
    args.apply_trailing = False
    for step in (cmd_probe, cmd_inventory, cmd_validate, cmd_normalize, cmd_render, cmd_compare):
        code = step(args)
        if code:
            return code
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
