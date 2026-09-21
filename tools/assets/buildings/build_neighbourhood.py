#!/usr/bin/env python3
"""v5.0 -- the neighbourhood, drawn and declared.

The six building families (small house, large house, tower, restaurant, bar, school) are no longer
one flat facade each. The two houses are a STACK of pieces (ground floor, storeys, roof) chosen per
instance; the tower, the restaurant and the bar are one cut-out figure each. This script draws
every piece, writes the PNGs into `res/drawable-nodpi`, writes the table the engine composes from,
and writes the registry entries that describe them -- the four outputs that have to agree, from
one run, so they cannot drift apart.

**The colour rule, which is the whole of the colour system.** Every tinted surface descends from
`SceneCustomization.colorFor(spec, dayBlend)`, that is from one of the two user-editable colours
of the object's category (HOUSES for the two houses; BUILDINGS for the tower, the restaurant and
the bar, which share them). There is no per-instance hue and no colour of a piece's own: each
tinted card is `w * wall + (1 - w) * k` with `k` only ink (#2B2A33) or white, baked as a weight
into the piece's own wall mask. So a piece ships two masks at most -- one wall, one glass -- and
never a colour variant, and the eight editable colours are the whole palette.

Run from `tools/assets/` with the asset venv:

    python3 -m buildings.build_neighbourhood --res --registry --budget
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import core
from core import W, DARK, Building, Slot, build_concept, budget, RES
import vocab
from names import production

WHITE = "#FFFFFF"

#: Notes that belong on a family's row in the generated table and cannot live in the generated
#: file, because the next run would delete them. v5.4's item-113 paragraph was written by hand
#: into `NeighbourhoodTable.kt` and was therefore one regeneration away from being lost with the
#: correction it explains -- `core.UNITS_TALL` still carried the pre-v5.4 96 and 90.146 when v5.6F
#: re-ran this script.
FAMILY_NOTES = {
    "RESTAURANT": (
        "        // **v5.4, item 113: 56 and 73, not 96 and 90.146.** Both families inherited the height of\n"
        "        // the two-storey facade the v5.0 redraw replaced, and `unitsTall` is the third of the\n"
        "        // three numbers that said so -- see [SceneSpace.SceneVariant.RESTAURANT]. The pavilion\n"
        "        // draws 56 piece units and the corner bar 53 or 73, measured off their own parts by\n"
        "        // `BuildingHeightDeclarationTest`. Each family's `unitsTall` moved by exactly the factor\n"
        "        // its variant's `metresTall` did, so `metresTall / unitsTall` -- the only quantity the\n"
        "        // blit scale depends on -- is unchanged and no piece of either building is drawn at a\n"
        "        // different size.\n"
    ),
    "SCHOOL": (
        "        // **v5.6F: the sixth family.** One figure with one deal, drawn 74 piece units above the\n"
        "        // ground line against the 72 it declares -- the two are the turret cornice and the grid\n"
        "        // margin, and `BuildingHeightDeclarationTest` measures the ratio rather than trusting\n"
        "        // this sentence. Its `WindowBuildingKind` is its own: a school is street-level glass\n"
        "        // like the two shops, and it is the only one of the three that shows children.\n"
    ),
}
# ---- the derivations, all of them functions of the wall (report v5.0 SS3) -------------------
vocab.BASE_DARK = W(0.74, DARK)    # ground-floor band: the wall 26 % towards the ink
vocab.BASE_LIGHT = W(0.62, WHITE)  # the tower's hall: the wall 38 % towards white
vocab.TRIM = W(0.45, WHITE)        # cornices, coping, canopies, brackets: 55 % towards white
vocab.ROOF_TILE = W(0.52, DARK)    # roof: the wall 48 % towards the ink
vocab.ROOF_SLATE = W(0.52, DARK)   # the same thing: there is no roof colour of its own any more
vocab.DOOR = W(0.40, DARK)         # door: 60 % towards the ink
vocab.CHIMNEY = W(0.60, DARK)      # chimney, box: 40 % towards the ink
vocab.SIDE = None
vocab.TOP = None
import k1_scatola, k2_profilo, school   # noqa: E402  (they import the constants already substituted)
k2_profilo.W_TIER2 = W(0.90, WHITE)

HERE = Path(__file__).resolve().parent
TABLE_KT = core.REPO / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/NeighbourhoodTable.kt"
REGISTRY = core.TOOL_ROOT / "sources/sprites.json"

#: The 34 PNGs the redraw replaces: every shipped sprite of the six families. The palm is not one
#: of them (item 25 keeps it as shipped) and neither is anything a house shares with the rest of
#: the scene, which is why this is a prefix list and not `startswith("house")`.
RETIRED_PREFIXES = ("house_", "skyscraper_", "restaurant_", "bar_", "school_")

#: Why a registry entry for one of these has no SVG. The people's layers (v4.30) set the shape of
#: this: a sprite written by a generator from its own drawing code has no source file to re-render
#: from, and the registry says so rather than leaving the field looking like an oversight.
SOURCE_REASON = (
    "Written by tools/assets/buildings/build_neighbourhood.py, which draws the piece and "
    "decomposes it into the fixed layer and the weight masks the engine sums at the blit. "
    "Re-run that script to regenerate it, not `render`: it has no SVG of its own and cannot "
    "have one."
)


def families(house_roofs_small=("gable", "mansard"),
             house_roofs_large=("gable", "mansard", "turret"),
             bar_figures=("insegna", "smusso"),
             crowns=("spire", "dome")):
    """The mix the maintainer chose: houses from «Scatola», tower and shops from «Profilo».

    Every silhouette is a parameter because each one was costed separately against the budget
    before the ceilings moved (a bar figure -405 000 / -447 948 B, the turret roof -361 656, a
    mansard -308 736 / -194 184, a crown -71 928 / -37 836). The maintainer kept all of them; the
    parameters stay so the next person to ask "what would dropping one give us" can measure it
    instead of estimating it.
    """
    k1 = k1_scatola.pieces()
    hs_roofs = [k1["roof_" + r + "_a"] for r in house_roofs_small]
    hl_roofs = [k1["roof_" + r + "_b"] for r in house_roofs_large]
    bars = {"insegna": k2_profilo.b_insegna, "smusso": k2_profilo.b_smusso}
    crown = {"spire": k2_profilo.crown_spire, "dome": k2_profilo.crown_dome}
    return {
        "HOUSE_SMALL": Building("HOUSE_SMALL", [Slot([k1["gh_a"]]), Slot([k1["sh_a"]], 0, 1), Slot(hs_roofs)], 30.0, [0.0], 0.0),
        "HOUSE_LARGE": Building("HOUSE_LARGE", [Slot([k1["gh_b"]]), Slot([k1["sh_b"]], 1, 2), Slot(hl_roofs)], 42.0, [0.0], 0.0),
        "TOWER": Building("TOWER", [Slot([k2_profilo.tower_body()]), Slot([crown[c]() for c in crowns])], 35.0, [0.0], 0.0),
        "RESTAURANT": Building("RESTAURANT", [Slot([k2_profilo.r_padiglione()])], 50.0, [0.0], 0.0),
        "BAR": Building("BAR", [Slot([bars[b]() for b in bar_figures])], 33.0, [0.0], 0.0),
        # v5.6F. One figure and one deal, like the restaurant: `SilhouetteDeal` enumerates a
        # catalogue of exactly one for it, which is what keeps `indexFor` off the rotation it
        # would otherwise take on a family with no alternatives to rotate through.
        "SCHOOL": Building("SCHOOL", [Slot([school.school()])], 40.0, [0.0], 0.0),
    }


# ------------------------------------------------------------------ the Kotlin table
def kotlin_table(table: dict, pieces: dict) -> str:
    blocks = []
    for pname, p in pieces.items():
        parts = []
        for role, name, x, y in p["parts"]:
            if role == "OCCUPANTS":
                parts.append("        BuildingPart(0, 0f, 0f, PartRole.OCCUPANTS),")
            elif role == "LAMP":
                parts.append(f"        BuildingPart(0, {x:.2f}f, {y:.2f}f, PartRole.LAMP),")
            else:
                kt = {"FIXED": "FIXED", "SNOW": "SNOW", "ADD_WALL": "WALL_MASK", "ADD_GLASS": "GLASS_MASK"}[role]
                parts.append(f"        BuildingPart(R.drawable.{production(name)}, {x:.2f}f, {y:.2f}f, PartRole.{kt}),")
        wins = ", ".join(f"BuildingWindow({x:.2f}f, {y:.2f}f, {w:.2f}f, {h:.2f}f)" for x, y, w, h in p["windows"])
        lights = ", ".join(f"BuildingWindow({x:.2f}f, {y:.2f}f, {w:.2f}f, 0f)" for x, y, w in p["lights"])
        blocks.append(
            f"    private val {production(pname).upper()} = BuildingPiece(\n"
            f"        {p['height']:.2f}f,\n"
            f"        listOf(\n" + "\n".join(parts) + "\n        ),\n"
            f"        listOf({wins}),\n"
            f"        listOf({lights}),\n"
            f"        {p['smoke'][0]:.2f}f, {p['smoke'][1]:.2f}f, {p['beacon'][0]:.2f}f, {p['beacon'][1]:.2f}f,\n"
            f"    )")
    rows = []
    for family, t in table.items():
        slots = ",\n".join(
            f"                BuildingSlot(listOf({', '.join(production(o).upper() for o in s['options'])}), {s['rmin']}, {s['rmax']})"
            for s in t["slots"])
        rows.append(
            (FAMILY_NOTES.get(family, "")) +
            f"        SceneSpace.SceneVariant.{family} to BuildingFamily(\n"
            f"            {t['unitsTall']}f, {t['shadowHalf']}f, WindowBuildingKind.{t['kind']},\n"
            f"            listOf(\n{slots},\n            ),\n"
            f"        ),")
    body = "\n\n".join(blocks)
    families_map = "\n".join(rows)
    return f'''package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * What each of the six building families is made of. **Generated** by
 * `tools/assets/buildings/build_neighbourhood.py`; edit that script, not this file.
 *
 * A family is a list of SLOTS, bottom-up. A slot holds the alternative PIECES the composer may
 * choose from for one instance, and how many times that piece repeats. A piece's coordinates are
 * its own -- relative to the piece's foot -- and the baseline climbs by the height of everything
 * already placed, which is how one table gives a street where two neighbours carry two
 * silhouettes rather than one facade repeated.
 *
 * [PartRole] is what a part is *for*, not how it looks:
 * - `FIXED` is art that never takes a tint (awnings, plaques, lanterns, stone steps, the busts'
 *   cream frames) plus the fixed term of every tinted card;
 * - `WALL_MASK` and `GLASS_MASK` are weight masks **summed** over the fixed layer at the blit,
 *   the system the people have used since v4.30 -- a weight interpolates, an index does not, and
 *   an index is what left a 63/255 halo when this was tried the other way round;
 * - `SNOW` is drawn only while `winterColorsEnabled`, as a layer *over* the roof and never as the
 *   roof tinted white;
 * - `LAMP` and `OCCUPANTS` are call-outs to the behaviours that already exist, at the piece's own
 *   declared coordinates, so a porch light and a bust in a window do not have to be re-found from
 *   the artwork.
 *
 * **Every tinted surface descends from one of the two editable colours of the object's category.**
 * A card is `w * wall + (1 - w) * k` with `k` only ink or white, and `w` is baked into the wall
 * mask; so there is no colour variant in this set and no colour of a piece's own. See the script's
 * own doc comment.
 */
internal enum class PartRole {{ FIXED, SNOW, WALL_MASK, GLASS_MASK, LAMP, OCCUPANTS }}

internal class BuildingPart(val res: Int, val x: Float, val y: Float, val role: PartRole)

/** An opening a bust may stand in, or (with `h` = 0) a sill a light string may hang from. */
internal class BuildingWindow(val x: Float, val y: Float, val w: Float, val h: Float)

internal class BuildingPiece(
    /** How far the next piece's foot rises above this one's. */
    val height: Float,
    val parts: List<BuildingPart>,
    val windows: List<BuildingWindow>,
    val lights: List<BuildingWindow>,
    val smokeX: Float, val smokeY: Float,
    val beaconX: Float, val beaconY: Float,
)

internal class BuildingSlot(val options: List<BuildingPiece>, val repeatMin: Int, val repeatMax: Int)

internal class BuildingFamily(
    /** The height the piece stack is drawn in, which [SceneSpace.SceneVariant] scales to. */
    val unitsTall: Float,
    val shadowHalf: Float,
    val kind: WindowBuildingKind,
    val slots: List<BuildingSlot>,
)

internal object NeighbourhoodTable {{

{body}

    val FAMILIES: Map<SceneSpace.SceneVariant, BuildingFamily> = mapOf(
{families_map}
    )
}}
'''


# ------------------------------------------------------------------ the registry
def registry_entries(png_dir: Path, files: dict) -> list[dict]:
    """One entry per PNG this writes, measured from the PNG rather than declared beside it.

    `test_registry_coverage` is the reason this is not optional: an unregistered PNG is a sprite
    nothing in the tooling can say anything about, and ninety-six of them shipped that way once.
    The content box and the anchor are measured here, from the alpha channel, by the same rule
    `CONTENT_BOTTOM_CENTRE` states -- so a piece redrawn tomorrow re-declares itself.
    """
    import numpy as np
    from PIL import Image

    entries = []
    for name in sorted(files):
        shipped = production(name)
        image = Image.open(png_dir / f"{name}.png").convert("RGBA")
        alpha = np.array(image)[:, :, 3]
        ys, xs = np.where(alpha > 0)
        if len(xs) == 0:
            raise SystemExit(f"{name}: nothing drawn")
        box = [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]
        # A mask is summed at the blit under the user's own colour, so it is TINTABLE whatever the
        # grey in it happens to be; `_fx` never takes a tint by construction (it is what is left
        # after every tinted weight has been taken out of the drawing).
        tint = "FIXED_ART" if name.endswith("_fx") else "TINTABLE"
        family = shipped.split("_")[0]
        entries.append({
            "name": shipped,
            "category": "house" if family == "house" else "building",
            "width": image.width,
            "height": image.height,
            "contentBox": box,
            "scale": "SCENE_UNITS",
            "tint": tint,
            "usage": "referenced",
            # **PART_LOCAL, and that is not a shortcut.** These are pieces of a larger drawing:
            # the composer blits each one at the coordinates the table declares, in the piece's
            # own frame, so there is no anchor rule that predicts the origin from the pixels. The
            # dolphin is declared the same way for the same reason. Declaring
            # `CONTENT_BOTTOM_CENTRE` instead would invite `test_every_sprite_placed_by_its_own_
            # anchor_is_blitted_where_that_anchor_says` to compare two unrelated numbers.
            "anchorRule": "PART_LOCAL",
            "anchor": [0, 0],
            "season": "winter" if "_snow" in shipped else "all",
            "source": {"kind": "none", "reason": SOURCE_REASON},
            "notes": "",
        })
    return entries


def rewrite_registry(entries: list[dict]) -> tuple[int, int]:
    """Replace the five families' declarations with this run's, idempotently.

    An entry goes if this run produces one under that name (it is being replaced) **or** if it is
    one of the flat facades the redraw retires. Testing the prefixes alone is not enough and not
    safe: `house_small_ground_fx` starts with `house_` too, so a second run would retire the
    pieces it had just declared and leave the families that do not share a prefix -- the tower's --
    declared twice. Measured, on the second run: 396 entries against 370 PNGs.
    """
    document = json.loads(REGISTRY.read_text(encoding="utf-8"))
    before = document["sprites"]
    produced = {e["name"] for e in entries}
    kept = [
        s for s in before
        if s["name"] not in produced and not s["name"].startswith(RETIRED_PREFIXES)
    ]
    removed = len(before) - len(kept) - len(produced & {s["name"] for s in before})
    document["sprites"] = sorted(kept + entries, key=lambda s: s["name"])
    REGISTRY.write_text(json.dumps(document, indent=1) + "\n", encoding="utf-8")
    return removed, len(entries)


# ------------------------------------------------------------------ main
def main(argv: list[str]) -> int:
    out = HERE / "out"
    table, pieces, files = build_concept("mix", families(), out)
    png_dir = out / "mix" / "png"
    print(f"drawn: {len(files)} PNGs, {len(pieces)} pieces, {len(table)} families")

    if "--res" in argv:
        for shipped in (p.stem for p in RES.glob("*.png")):
            if shipped.startswith(RETIRED_PREFIXES):
                (RES / f"{shipped}.png").unlink()
        for name in files:
            shutil.copyfile(png_dir / f"{name}.png", RES / f"{production(name)}.png")
        TABLE_KT.write_text(kotlin_table(table, pieces), encoding="utf-8")
        print(f"res: wrote {len(files)} PNGs and {TABLE_KT.name}")

    if "--registry" in argv:
        removed, added = rewrite_registry(registry_entries(png_dir, files))
        print(f"registry: -{removed} retired, +{added} declared")

    if "--budget" in argv:
        report = budget(("mix",), {"mix": (files, png_dir)}, HERE)
        mix = report["concepts"]["mix"]
        print(f"budget: {mix['files']} PNGs, {mix['decoded']} B decoded, {mix['uploaded_level0']} B uploaded")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
