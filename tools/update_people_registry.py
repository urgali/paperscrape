#!/usr/bin/env python3
"""Rewrites the **person** half of ``tools/assets/sources/sprites.json`` for v4.30.

Run from the repo root with the pinned venv, after ``tools/generate_people_layers.py``:

    /home/bober/.venvs/paperscrape-assets/bin/python tools/update_people_registry.py

The registry is one entry per shipped PNG, and v4.30 replaced 168 skin-tone copies with 195 layer
files. Rewriting those entries by hand is 363 edits, so it is done from the artwork instead -- the
same reason `PeopleLayerTable` is generated rather than typed.

Only ``person_*`` entries are touched. Everything else in the registry, and every field this script
does not set, is left exactly as it was.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TOOL_ROOT = REPO / "tools" / "assets"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
REGISTRY = TOOL_ROOT / "sources" / "sprites.json"
sys.path.insert(0, str(TOOL_ROOT))

LAYER_SUFFIXES = ("_fx", "_ms", "_mh", "_mt", "_mb")

REGION_NOTE = {
    "_fx": (
        "The fixed art of this shape: every ink that does not follow one of the four colours the "
        "engine resolves at the blit, plus the dark half of every ink that does -- a shadow is "
        "(1-t)*paint + t*DARK and the t*DARK term does not move when the paint does. Written by "
        "tools/generate_people_layers.py from the same drawing code that draws the shipped figure, "
        "so its alpha channel is the figure's own, pixel for pixel."
    ),
    "_ms": "skin",
    "_mh": "head -- hair, or whatever is worn on it instead",
    "_mt": "shirt or coat",
    "_mb": "trousers",
}

MASK_NOTE = (
    "The weight mask of the {region} region: how much of each pixel follows that region's colour, "
    "white with the weight in the alpha so that BitmapFactory's premultiplication leaves exactly "
    "the product the engine multiplies the colour by. Added to the fixed layer rather than laid "
    "over it -- two source-over layers split the pixel's coverage and a + b(1-a) is not linear, so "
    "once the engine halves each one separately they stop recomposing and a halo appears at the "
    "edge. Written by tools/generate_people_layers.py; PeopleLayerAssetTest checks that the layers "
    "add up to the drawing at the colours it was painted in."
)

WINDOW_BASE_NOTE = (
    "Shipped and blitted by nothing since v4.30, and declared rather than deleted. It is the "
    "drawing tools/generate_people_layers.py decomposes into this shape's fixed layer and masks, "
    "and the subject of SpriteVariantTest's seasonal-pair check and SpriteMeasurementClaimTest's "
    "canvas claim; the draw path reads the layers. Until v4.30 it was kept referenced by "
    "SceneObjectRenderer.personWindowHeadDrawables, a table no draw path read -- BACKLOG_v4_25.md "
    "item 58 -- and deleting that table is what made the orphan visible instead of hidden."
)


BOY_WALK_BASE_NOTE = (
    "Shipped and blitted by nothing since v4.30, and declared rather than deleted. The walkers' "
    "un-suffixed bases are the drawing tools/generate_people_layers.py decomposes, and "
    "ThemePreviewScene blits the other three families' directly for the gallery card -- but not "
    "the boy's, which has never appeared on a preview. Until v4.30 he was kept referenced by "
    "SceneObjectRenderer.personWalkDrawables, a table no draw path read; deleting it is "
    "BACKLOG_v4_25.md item 58 closed, and this is the half of the consequence that has to be "
    "declared rather than wired up. He is also the subject of SpriteVariantTest's frame-3 sharing "
    "claim and of the 65% child proportion regenerated in v4.30."
)


def layer_of(name: str):
    for suffix in LAYER_SUFFIXES:
        if name.endswith(suffix):
            return name[: -len(suffix)], suffix
    return None, None


def main() -> None:
    document = json.loads(REGISTRY.read_text())
    sprites = document["sprites"]
    by_name = {entry["name"]: entry for entry in sprites}
    shipped = sorted(p.stem for p in RES.glob("*.png"))

    # Layer entries are always rewritten rather than kept: they are derived from the artwork, so a
    # stale one is a lie the generator can fix and a hand edit cannot.
    kept = [
        e for e in sprites
        if not e["name"].startswith("person_")
        or (e["name"] in shipped and layer_of(e["name"])[0] is None)
    ]
    kept_names = {e["name"] for e in kept}

    added = 0
    for name in shipped:
        if name in kept_names or not name.startswith("person_"):
            continue
        shape, suffix = layer_of(name)
        if shape is None:
            sys.exit(f"{name} is a person sprite the registry does not know and is not a layer")
        measured = measure_png(name)
        template = by_name.get(shape) or by_name.get(f"{shape}_fx")
        entry = {
            "name": name,
            "category": "person",
            "width": measured["width"],
            "height": measured["height"],
            "contentBox": measured["contentBox"],
            "scale": "SCENE_UNITS",
            # A mask is a colourless weight multiplied by a colour at the blit, which is exactly
            # what TINTABLE means since decision 25; the fixed layer carries its own colours.
            "tint": "FIXED_ART" if suffix == "_fx" else "TINTABLE",
            "usage": "referenced",
            "anchorRule": "CONTENT_BOTTOM_CENTRE",
            "anchor": [
                round((measured["contentBox"][0] + measured["contentBox"][2]) / 2, 1),
                measured["contentBox"][3],
            ],
            "season": "winter" if "_winter_" in name else "summer",
            # `none` in the registry's own vocabulary means "no SVG of its own", which is true: a
            # layer is a *view* of a drawing rather than a drawing, and authoring one per region per
            # shape is the duplication the generator exists to remove. The reason names the
            # generator, which is what makes "how do I get this file back" answerable.
            "source": {
                "kind": "none",
                "reason": (
                    f"Written by tools/generate_people_layers.py from {shape}'s own drawing, "
                    "through the same code that draws the shipped figure. Re-run that script to "
                    "regenerate it, not `render`: it has no SVG of its own and cannot have one."
                ),
            },
            "notes": REGION_NOTE["_fx"] if suffix == "_fx"
            else MASK_NOTE.format(region=REGION_NOTE[suffix]),
        }
        if template is not None and "category" in template:
            entry["category"] = template["category"]
        kept.append(entry)
        added += 1

    # The un-suffixed bases no draw path reaches now that the tables naming them are gone.
    orphans = [f"person_{k}_{s}_head_window" for k in ("man", "woman", "boy", "girl")
               for s in ("summer", "winter")]
    orphans += [f"person_boy_{s}_walk{f}" for s in ("summer", "winter") for f in range(3)]
    for name in orphans:
        entry = next((e for e in kept if e["name"] == name), None)
        if entry is None:
            continue
        entry["usage"] = "orphan"
        entry["notes"] = BOY_WALK_BASE_NOTE if "_walk" in name else WINDOW_BASE_NOTE

    # Every person entry's geometry is re-measured, not only the new ones: v4.30 redrew the twelve
    # child walk frames inside their own canvas, and a content box left at its old value is a
    # registry that describes a drawing that no longer exists.
    for entry in kept:
        if not entry["name"].startswith("person_"):
            continue
        measured = measure_png(entry["name"])
        entry["width"] = measured["width"]
        entry["height"] = measured["height"]
        entry["contentBox"] = measured["contentBox"]
        entry["anchor"] = [
            round((measured["contentBox"][0] + measured["contentBox"][2]) / 2, 1),
            measured["contentBox"][3],
        ]

    kept.sort(key=lambda e: e["name"])
    document["sprites"] = kept

    # Variant groups and retired bases both spoke about the tone copies, which no longer exist.
    names = {e["name"] for e in kept}
    document["variants"] = [
        group for group in document["variants"]
        if all(member in names for member in group["members"])
    ]
    document["retiredBases"] = {
        base: heir for base, heir in document["retiredBases"].items() if heir in names
    }

    # Season pairs for the layer files. A sprite whose name carries a season is a variant by
    # construction, and `tests/test_variants.py` fails any that is not declared -- the rule that
    # caught v73's two identical head sprites. Generated for the same reason the table is: 195
    # files is not a list anybody keeps in step by hand.
    for name in sorted(names):
        if "_summer_" not in name or layer_of(name)[0] is None:
            continue
        winter = name.replace("_summer_", "_winter_")
        if winter not in names:
            continue          # the winter window busts were retired; see the test's own exception
        document["variants"].append({
            "id": name.replace("person_", "").replace("_summer", ""),
            "axis": "season",
            "members": [name, winter],
            "state": "DISTINCT",
            "reason": (
                "The seasonal pair of one layer of one shape. Distinct exactly as the drawings "
                "they are cut from are: a winter figure wears a coat, a hat or a hood and a "
                "scarf, so both the fixed art and every region's weight differ from the summer "
                "figure's."
            ),
        })

    REGISTRY.write_text(json.dumps(document, indent=1) + "\n", encoding="utf-8")
    print(f"{len(kept)} sprites ({added} added), {len(document['variants'])} variant groups, "
          f"{len(document['retiredBases'])} retired bases")


def measure_png(name: str) -> dict:
    from PIL import Image
    import numpy as np

    image = Image.open(RES / f"{name}.png").convert("RGBA")
    alpha = np.array(image)[:, :, 3]
    ys, xs = np.nonzero(alpha)
    box = [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1] if len(ys) \
        else [0, 0, image.width, image.height]
    return {"width": image.width, "height": image.height, "contentBox": box}


if __name__ == "__main__":
    main()
