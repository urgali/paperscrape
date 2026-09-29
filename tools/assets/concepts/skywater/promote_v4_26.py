#!/usr/bin/env python3
"""Writes the v4.26 shipped sky-and-water sources into ``tools/assets/sources/svg/``.

**A record of v4.26's promotion, not a step of today's pipeline.** Four of the five sources it
writes have changed since -- the bird redrawn (v4.28's B1 "Swallow"), and the dolphin, the hull
and the sail cropped to their content (v4.31) -- so running it now would put their v4.26 versions
back over the shipped sources, and the asset tooling's fidelity tests would fail on all four
(found in v5.9F, in a throwaway copy). It therefore refuses to write unless it is asked to in so
many words:

    /home/bober/.venvs/paperscrape-assets/bin/python concepts/skywater/promote_v4_26.py --overwrite-shipped-sources

run from ``tools/assets`` with the pinned venv, and only to reproduce v4.26.

**This writes SVG sources only.** The shipped PNG is whatever
``python -m paperscrape_assets render`` makes of them, which is the whole point of the pipeline:
nothing is copied out of a concept directory, and the artwork can be re-derived from the source
at any later size. It also prints the registry fields (`width`, `height`, `contentBox`) each
sprite needs in ``sources/sprites.json``, measured off the render rather than declared.

What was chosen, from the four rounds of proposals photographed on the BV6600:

* sea **S1 "Mirror"** -- the renderer's, not a sprite;
* cloud **C1 "Puff"**, edge feathered 1.5 units;
* bird **A "Dove"** at the reduced size, 51x21 px, ~1.3 person heights;
* boats and dolphins **B "Relief"**, as approved in the first round.

The one change to the approved artwork is the dolphin's two body tones, **derived** rather than
picked: `LakeContrastTest` holds the derivation and the gate. The animal was `#4A6A84` on a
`#15495C` night sea -- **CIELab dE 1.53 from the water in its worst case**, measured over every
surface the eleven dolphin-drawing themes can paint across the whole day/night sweep, clear and
storm -- and it disappeared at night and under storm exactly as reported. It is now a light warm
grey at **dE 17.97**, against a gate of **10.16** derived by the v4.22 method between the floor
the invisible back produced (1.53) and the signal the animal's own belly produces (18.78).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOL_ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(TOOL_ROOT))

from build_skywater_concepts import UNIT, emit_svg, dolphin, hull, sail  # noqa: E402
from build_skywater_round4 import BIRD_AXIS, BIRD_H, BIRD_W, bird_svg, cloud_svg  # noqa: E402
from paperscrape_assets import raster  # noqa: E402
from paperscrape_assets.inventory import measure_raster  # noqa: E402

SVG_DIR = TOOL_ROOT / "sources" / "svg"

#: The lake animal's papers, derived in `LakeContrastTest` and not chosen by eye. Three of the
#: four moved: the back and its under-paper because the animal was invisible on the water, and the
#: belly's under-paper because it was the one remaining paper under the gate (dE 9.11). The belly
#: itself is unchanged -- at 18.78 it is the *signal arm* of the derivation and moving it would
#: move the gate with it.
SHIPPED_DOLPHIN_BACK = "#BAA8AE"
SHIPPED_DOLPHIN_UNDER = "#917F84"
SHIPPED_BELLY_UNDER = "#E6CED7"

#: C1 "Puff": the feather of the cloud's edge, in canvas units.
CLOUD_BLUR_UNITS = 1.5


def sources() -> list[tuple[str, str, tuple[int, int]]]:
    """(name, svg, expected size) for the five sprites this release replaces."""
    out = [
        ("bird_body", bird_svg("dove"), (BIRD_W, BIRD_H)),
        ("cloud_body", cloud_svg(CLOUD_BLUR_UNITS), (798, 396)),
    ]
    for sprite in (
        dolphin("relief", back=SHIPPED_DOLPHIN_BACK, under=SHIPPED_DOLPHIN_UNDER,
                belly_under=SHIPPED_BELLY_UNDER),
        hull("relief"),
        sail("relief"),
    ):
        out.append((
            sprite.name,
            emit_svg(sprite, "relief"),
            (int(sprite.width_units * UNIT), int(sprite.height_units * UNIT)),
        ))
    return out


def main() -> int:
    if "--overwrite-shipped-sources" not in sys.argv[1:]:
        print("promote_v4_26.py is the record of v4.26's promotion: four of its five sources have changed "
              "since, and running it would overwrite them with the v4.26 versions.\n"
              "Pass --overwrite-shipped-sources to do that anyway (to reproduce v4.26 only).", file=sys.stderr)
        return 2
    fields = {}
    for name, svg, expected in sources():
        rendered = raster.render_svg(svg)
        if rendered.size != expected:
            raise SystemExit(f"{name}: renders {rendered.size}, expected {expected}")
        (SVG_DIR / f"{name}.svg").write_text(svg + "\n", encoding="utf-8")
        measured = measure_raster(name, rendered)
        fields[name] = {
            "width": expected[0],
            "height": expected[1],
            "contentBox": list(measured.content_bbox),
        }
        print(f"  {name:14s} {expected[0]}x{expected[1]} content {list(measured.content_bbox)}")
    print()
    print("registry fields for sources/sprites.json:")
    print(json.dumps(fields, indent=1))
    print()
    print(f"bird flap axis is canvas row {BIRD_AXIS}; the blit origin is (-25, -15).")
    print("now: python -m paperscrape_assets render, then install staging/ into res/drawable-nodpi/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
