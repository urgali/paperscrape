#!/usr/bin/env python3
"""The neighbourhood, drawn and declared.

The nine building families (small house, large house, tower, restaurant, bar, school, and the shop,
the fire station and the police station of `civic.py`) are not one flat facade each. The two houses
are a STACK of pieces (ground floor, storeys, roof) chosen per instance, and the tower a body under a
crown; the restaurant, the bar, the school, the shop and the two stations are one cut-out figure
each. This script draws
every piece, writes the PNGs into `res/drawable-nodpi`, writes the table the engine composes from,
and writes the registry entries that describe them -- the four outputs that have to agree, from
one run, so they cannot drift apart.

**The colour rule, which is the whole of the colour system.** Every wall surface descends from
`SceneCustomization.colorFor(spec, dayBlend)`, that is from one of the two user-editable colours
of the object's category (HOUSES for the two houses and the distant houses; BUILDINGS for the
tower; SHOPS for the restaurant, the bar and the shop; PUBLICS for the school, the fire station and
the police station: `DESIGN_NOTES.md` §12, decision 62). There is no per-instance hue and no colour of a piece's own: each tinted card
is `w * wall + (1 - w) * k` with `k` only ink (#2B2A33) or white, baked as a weight into the piece's
own wall mask. So a piece ships two masks at most -- one wall, one glass -- and never a colour
variant, and the sixteen editable colours -- two a category, by day and by night, in the four categories
above -- are the whole palette.

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
#: file, because the next run would delete them: a note written by hand into `NeighbourhoodTable.kt`
#: is one regeneration away from being lost.
FAMILY_NOTES = {
    "RESTAURANT": (
        "        // **56 and 73: what the pavilion and the corner bar draw.** `unitsTall` is one of the\n"
        "        // three numbers that describe each building's height, with its variant's `metresTall`\n"
        "        // and `spriteUnitsTall` -- see [SceneSpace.SceneVariant.RESTAURANT]. The pavilion\n"
        "        // draws 56 piece units and the corner bar 53 or 73, measured off their own parts by\n"
        "        // `BuildingHeightDeclarationTest`. The blit scale depends on `metresTall / unitsTall`\n"
        "        // alone, so those two move together, by one factor, or the building is drawn at\n"
        "        // another size.\n"
    ),
    "SCHOOL": (
        "        // **The school: one figure with one deal**, drawn 74 piece units above the ground line\n"
        "        // against the 72 it declares (the turret cornice and the grid margin);\n"
        "        // `BuildingHeightDeclarationTest` measures the ratio. Its `WindowBuildingKind` is its own:\n"
        "        // street-level glass like the shops and the stations, whose windows show only children.\n"
    ),
    "SHOP": (
        "        // **The shop and the two stations.** No place of their own in the street: each of the six\n"
        "        // buildings among the houses stands in whichever of the places [StorefrontStreet] deals\n"
        "        // it, drawn at that place's metre, so these three have no [SceneSpace.SceneVariant] row and\n"
        "        // their `unitsTall` is their own drawing's height. The two stations are their own\n"
        "        // `WindowBuildingKind`: street glass, adults only.\n"
    ),
}
# ---- the derivations, all of them functions of the wall -------------------------------------
vocab.BASE_DARK = W(0.74, DARK)    # ground-floor band: the wall 26 % towards the ink
vocab.BASE_LIGHT = W(0.62, WHITE)  # the tower's hall: the wall 38 % towards white
vocab.TRIM = W(0.45, WHITE)        # cornices, coping, canopies, brackets: 55 % towards white
vocab.ROOF_TILE = W(0.52, DARK)    # roof: the wall 48 % towards the ink
vocab.ROOF_SLATE = W(0.52, DARK)   # the same thing: a roof has no colour of its own
vocab.DOOR = W(0.40, DARK)         # door: 60 % towards the ink
vocab.CHIMNEY = W(0.60, DARK)      # chimney, box: 40 % towards the ink
vocab.SIDE = None
vocab.TOP = None
import k1_box, k2_profile, school, distant, civic   # noqa: E402  (they import the constants already substituted)
k2_profile.W_TIER2 = W(0.90, WHITE)

#: The families that are a row of `SceneSpace.SceneVariant` (the size table), and the six that stand among the
#: houses, in `Storefront`'s order.
VARIANT_FAMILIES = ("HOUSE_SMALL", "HOUSE_LARGE", "TOWER", "RESTAURANT", "BAR", "SCHOOL")
STOREFRONT_FAMILIES = ("RESTAURANT", "SCHOOL", "BAR", "SHOP", "FIRE_STATION", "POLICE_STATION")

HERE = Path(__file__).resolve().parent
TABLE_KT = core.REPO / "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/NeighbourhoodTable.kt"
REGISTRY = core.TOOL_ROOT / "sources/sprites.json"

#: The prefixes of every shipped sprite of the nine families (and, through `house_`, of the distant
#: houses), which a run deletes and writes again, so a piece the drawing code stops making is not left
#: behind, still in `res/` and in the registry. The palm is not one
#: of them and neither is anything a house shares with the rest of
#: the scene, which is why this is a prefix list and not `startswith("house")`.
RETIRED_PREFIXES = ("house_", "skyscraper_", "tower_", "restaurant_", "bar_", "school_", "shop_", "fire_station_",
                    "police_station_")

#: Why a registry entry for one of these has no SVG, said as the people's layers say it: a sprite
#: written by a generator from its own drawing code has no source file to re-render from, and the
#: registry says so rather than leaving the field looking like an oversight.
SOURCE_REASON = (
    "Written by tools/assets/buildings/build_neighbourhood.py, which draws the piece and "
    "decomposes it into the fixed layer and the weight masks the engine sums at the blit. "
    "Re-run that script to regenerate it, not `render`: it has no SVG of its own and cannot "
    "have one."
)


def families(house_roofs_small=("gable", "mansard"),
             house_roofs_large=("gable", "mansard", "turret"),
             bar_figures=("signboard", "chamfer"),
             crowns=("spire", "dome")):
    """The mix: the houses from the "Box" concept (`k1_box`), the tower and the shops from the
    "Profile" one (`k2_profile`).

    Every silhouette is a parameter, and the defaults draw all of them: what dropping one would
    save is then measured, by building the mix without it and reading the budget, instead of
    estimated.
    """
    k1 = k1_box.pieces()
    hs_roofs = [k1["roof_" + r + "_a"] for r in house_roofs_small]
    hl_roofs = [k1["roof_" + r + "_b"] for r in house_roofs_large]
    bars = {"signboard": k2_profile.b_signboard, "chamfer": k2_profile.b_chamfer}
    crown = {"spire": k2_profile.crown_spire, "dome": k2_profile.crown_dome}
    return {
        "HOUSE_SMALL": Building("HOUSE_SMALL", [Slot([k1["gh_a"]]), Slot([k1["sh_a"]], 0, 1), Slot(hs_roofs)], 30.0, [0.0], 0.0),
        "HOUSE_LARGE": Building("HOUSE_LARGE", [Slot([k1["gh_b"]]), Slot([k1["sh_b"]], 1, 2), Slot(hl_roofs)], 42.0, [0.0], 0.0),
        # Three heights, by a coin of the engine's own rather than by the deal -- see
        # `core.Slot.heights` -- so the deal sees one body, and the heights do not re-deal the crowns.
        "TOWER": Building("TOWER", [Slot([k2_profile.tower_body("mid")],
                                         heights=[k2_profile.tower_body(s) for s in ("short", "mid", "tall")]),
                                    Slot([crown[c]() for c in crowns])], 35.0, [0.0], 0.0),
        "RESTAURANT": Building("RESTAURANT", [Slot([k2_profile.r_pavilion()])], 50.0, [0.0], 0.0),
        "BAR": Building("BAR", [Slot([bars[b]() for b in bar_figures])], 33.0, [0.0], 0.0),
        # One figure and one deal, like the restaurant: `SilhouetteDeal` enumerates a
        # catalogue of exactly one for it, which is what keeps `indexFor` off the rotation it
        # would otherwise take on a family with no alternatives to rotate through.
        "SCHOOL": Building("SCHOOL", [Slot([school.school()])], 40.0, [0.0], 0.0),
        # The shop and the two stations, one figure each like the restaurant and the school.
        # None of them is a place in the street of its own: each of the six buildings among the houses
        # may stand in any of the places there (`StorefrontStreet`), drawn at that place's metre.
        "SHOP": Building("SHOP", [Slot([civic.shop()])], 32.0, [0.0], 0.0),
        "FIRE_STATION": Building("FIRE_STATION", [Slot([civic.fire_station()])], 46.0, [0.0], 0.0),
        "POLICE_STATION": Building("POLICE_STATION", [Slot([civic.police_station()])], 48.0, [0.0], 0.0),
    }


def extras():
    """The pieces that belong to no family: the distant houses, drawn in the houses' paper."""
    return [(piece, "HOUSE") for piece in distant.pieces()]


# ------------------------------------------------------------------ the Kotlin table
def fa(values) -> str:
    """A Kotlin `floatArrayOf` of [values], two decimals each."""
    return "floatArrayOf(" + ", ".join(f"{v:.2f}f" for v in values) + ")"


def kotlin_table(table: dict, pieces: dict, distant_names: list) -> str:
    # The panes are a house's: what its night lights or leaves dark, pane by pane. Every other
    # building lights all its glass together, so its pieces carry none.
    house_pieces = {o for f in ("HOUSE_SMALL", "HOUSE_LARGE") for slot in table[f]["slots"] for o in slot["options"] + slot["heights"]}
    # The roofline and the top are read off a building's top piece -- a house's roof, a tower's crown, a
    # building of one piece -- and off the distant houses: the other pieces carry none.
    top_pieces = {o for t in table.values() for o in t["slots"][-1]["options"] + t["slots"][-1]["heights"]} | set(distant_names)
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
        people = ""
        if p["people"] != len(p["windows"]):
            people = f"        people = {p['people']},\n"
        panes = ""
        if pname in house_pieces and p["panes"]:
            panes = ("        panes = listOf(" +
                     ", ".join(f"BuildingWindow({x:.2f}f, {y:.2f}f, {w:.2f}f, {h:.2f}f)" for x, y, w, h in p["panes"]) + "),\n")
        door = ""
        if p["door"] is not None:
            d = p["door"]
            x, y, w, h = d["box"]
            door = (f"        door = BuildingDoor({x:.2f}f, {y:.2f}f, {w:.2f}f, {h:.2f}f, "
                    f"{fa([px for px, _ in d['outline']])}, {fa([py for _, py in d['outline']])}, "
                    f"{d['relief'][0]:.2f}f, {d['relief'][1]:.2f}f),\n")
        roof = ""
        if pname in top_pieces:
            roof = (f"        roofX = {fa([x for x, _ in p['roof']])},\n"
                    f"        roofY = {fa([y for _, y in p['roof']])},\n"
                    f"        topX = {p['top'][0]:.2f}f, topY = {p['top'][1]:.2f}f,\n")
        blocks.append(
            f"    private val {production(pname).upper()} = BuildingPiece(\n"
            f"        {p['height']:.2f}f,\n"
            f"        listOf(\n" + "\n".join(parts) + "\n        ),\n"
            f"        listOf({wins}),\n"
            f"        listOf({lights}),\n"
            f"        {p['smoke'][0]:.2f}f, {p['smoke'][1]:.2f}f, {p['beacon'][0]:.2f}f, {p['beacon'][1]:.2f}f,\n"
            + people + panes + door + roof +
            f"    )")
    families = []
    for family, t in table.items():
        def slot_kt(s):
            heights = ""
            if s["heights"]:
                heights = f", heights = listOf({', '.join(production(o).upper() for o in s['heights'])})"
            return (f"            BuildingSlot(listOf({', '.join(production(o).upper() for o in s['options'])}), "
                    f"{s['rmin']}, {s['rmax']}{heights})")
        slots = ",\n".join(slot_kt(s) for s in t["slots"])
        families.append(
            (FAMILY_NOTES.get(family, "").replace("        //", "    //")) +
            f"    private val {family}_FAMILY = BuildingFamily(\n"
            f"        {t['unitsTall']}f, {t['shadowHalf']}f, WindowBuildingKind.{t['kind']},\n"
            f"        listOf(\n{slots},\n        ),\n"
            f"    )")
    body = "\n\n".join(blocks)
    families_vals = "\n\n".join(families)
    families_map = "\n".join(f"        SceneSpace.SceneVariant.{f} to {f}_FAMILY," for f in VARIANT_FAMILIES)
    storefronts_map = "\n".join(f"        Storefront.{f} to {f}_FAMILY," for f in STOREFRONT_FAMILIES)
    distant_list = ", ".join(production(n).upper() for n in distant_names)
    return f'''package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * What each of the nine building families is made of, and the distant houses. **Generated** by
 * `tools/assets/buildings/build_neighbourhood.py`; edit that script, not this file.
 *
 * A family is a list of SLOTS, bottom-up. A slot holds the alternative PIECES the composer may
 * choose from for one instance, and how many times that piece repeats. A piece's coordinates are
 * relative to its own foot, and the baseline climbs by the height of everything already placed, so
 * two neighbours carry two silhouettes rather than one facade repeated.
 *
 * [PartRole] is what a part is *for*:
 * - `FIXED` is art that never takes a tint (awnings, plaques, stone steps, the busts' frames) plus
 *   the fixed term of every tinted card;
 * - `WALL_MASK` and `GLASS_MASK` are weight masks **summed** over the fixed layer at the blit, as
 *   the people's layers are: a weight interpolates at a mask's edge, where an index leaves a halo;
 * - `SNOW` is drawn only while `winterColorsEnabled`, as a layer over the roof, never the roof
 *   tinted white;
 * - `LAMP` and `OCCUPANTS` place a porch light and a bust in a window at the piece's declared
 *   coordinates.
 *
 * **Every wall surface descends from one of the two editable colours of the object's category**;
 * the glass mask takes the scene's day/night glass colours (`SceneObjectRenderer.windowGlassColor`;
 * at night `unlitWindowGlassColor` for a shut business, and for a house with its lit panes laid over
 * it). A card is `w * wall + (1 - w) * k` with `k` only ink or white and `w` baked into the wall
 * mask, so no piece has a colour of its own. See the script's own doc comment.
 */
internal enum class PartRole {{ FIXED, SNOW, WALL_MASK, GLASS_MASK, LAMP, OCCUPANTS }}

internal class BuildingPart(val res: Int, val x: Float, val y: Float, val role: PartRole)

/**
 * An opening a bust may stand in, a pane of a house's glass (`BuildingPiece.panes`), or (with
 * `h` = 0) a sill a light string may hang from.
 */
internal class BuildingWindow(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * A piece's front door, in the piece's own units: its box ([x], [y], [w], [h]), the outline of its card as
 * the scissors cut it ([outlineX], [outlineY], convex but for the wobble's small dents), and how far its
 * shadow paper is offset ([reliefX], [reliefY]; 0 for the towers' glass doors, an opening in a framed
 * card). Where the Christmas wreath hangs, and what a shut building's shutter covers: the card and its shadow.
 */
internal class BuildingDoor(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val outlineX: FloatArray, val outlineY: FloatArray,
    val reliefX: Float, val reliefY: Float,
)

internal class BuildingPiece(
    /** How far the next piece's foot rises above this one's. */
    val height: Float,
    val parts: List<BuildingPart>,
    val windows: List<BuildingWindow>,
    val lights: List<BuildingWindow>,
    val smokeX: Float, val smokeY: Float,
    val beaconX: Float, val beaconY: Float,
    /**
     * A house piece's panes of glass, each one a window its night may light or leave dark: first the
     * [windows], in their order (pane `k` is window `k` for every `k` below `windows.size`), then the
     * dormers and the turret's small windows. Empty for the other buildings, which light all their
     * glass together while they are open.
     */
    val panes: List<BuildingWindow> = emptyList(),
    /**
     * How many of its building's people this piece brings, as a count of windows: its own window
     * count, except for the storeys, the school and the bars, which have more windows than people, and
     * the shop and the two stations, which bring fewer at the shops' rate (`civic.py`). The number of
     * people at a building's windows does not change with its windows (`DESIGN_NOTES.md` §12, decision 54):
     * people are dealt over this ([NeighbourhoodComposer.Deal.peopleWindows]), the windows they stand at
     * over all of them.
     */
    val people: Int = windows.size,
    /** The piece's front door, where it has one: see [BuildingDoor]. */
    val door: BuildingDoor? = null,
    /**
     * Where a roof string's bulbs hang on a building's top piece and on a distant house: its roofline -- the
     * top outline of what the piece draws, parts narrower than 9 units (a chimney, a pole, a spire's needle) left
     * out -- every 6 units along it. Empty for the other pieces.
     */
    val roofX: FloatArray = floatArrayOf(),
    val roofY: FloatArray = floatArrayOf(),
    /** The highest point the piece draws, where a tower's star stands; 0, 0 where [roofX] is empty. */
    val topX: Float = 0f,
    val topY: Float = 0f,
)

/**
 * One slot of a family: the [options] the deal chooses between and how many times the piece repeats.
 *
 * [heights], where a slot has them, are alternatives the deal does **not** choose between: each
 * instance takes one by a coin of its own (`NeighbourhoodComposer.heightIndex`), in place of the one
 * piece in [options]. The towers' three bodies vary a skyline's height this way without growing
 * the catalogue `SilhouetteDeal` deals from, which would re-deal every theme's crowns.
 */
internal class BuildingSlot(
    val options: List<BuildingPiece>,
    val repeatMin: Int,
    val repeatMax: Int,
    val heights: List<BuildingPiece> = emptyList(),
)

internal class BuildingFamily(
    /** The height the piece stack is drawn in, which [SceneSpace.SceneVariant] scales to. */
    val unitsTall: Float,
    val shadowHalf: Float,
    val kind: WindowBuildingKind,
    val slots: List<BuildingSlot>,
)

internal object NeighbourhoodTable {{

{body}

{families_vals}

    /** The families that have a row of their own in the size table, by that row. */
    val FAMILIES: Map<SceneSpace.SceneVariant, BuildingFamily> = mapOf(
{families_map}
    )

    /**
     * The six buildings that stand among the houses, by what they are: the restaurant, the
     * school and the bar are their size-table rows' families above, the shop and the two stations have
     * none -- each of the six is drawn in whichever of the places [StorefrontStreet] deals it.
     */
    val STOREFRONTS: Map<Storefront, BuildingFamily> = mapOf(
{storefronts_map}
    )

    /**
     * The distant houses on the mountains, in the order `DistantHouses` deals them: the
     * cottage, the chalet, the tall house. Each piece's `height` is its drawing's own, roof peak to
     * foot, which is what `SceneSpace.distantHousePixelsTall` sizes.
     */
    val DISTANT_HOUSES: List<BuildingPiece> = listOf({distant_list})
}}
'''


# ------------------------------------------------------------------ the registry
def registry_entries(png_dir: Path, files: dict) -> list[dict]:
    """One entry per PNG this writes, measured from the PNG rather than declared beside it.

    `test_registry_coverage` is the reason this is not optional: an unregistered PNG is a sprite
    nothing in the tooling can say anything about.
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
    """Replace the nine families' and the distant houses' declarations with this run's, idempotently.

    An entry goes if this run produces one under that name (it is being replaced) **or** if its
    name starts with one of [RETIRED_PREFIXES] (a piece of these families this run does not draw).
    The name test is what keeps a family whose prefix is missing from that list from being declared
    twice, its old entry beside this run's under the same name.
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
    distant_houses = extras()
    table, pieces, files = build_concept("mix", families(), out, distant_houses)
    png_dir = out / "mix" / "png"
    print(f"drawn: {len(files)} PNGs, {len(pieces)} pieces, {len(table)} families")

    if "--res" in argv:
        for shipped in (p.stem for p in RES.glob("*.png")):
            if shipped.startswith(RETIRED_PREFIXES):
                (RES / f"{shipped}.png").unlink()
        for name in files:
            shutil.copyfile(png_dir / f"{name}.png", RES / f"{production(name)}.png")
        TABLE_KT.write_text(kotlin_table(table, pieces, [p.name for p, _ in distant_houses]), encoding="utf-8")
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
