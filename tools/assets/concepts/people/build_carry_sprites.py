#!/usr/bin/env python3
"""The shipped carrying pose: the walking adults with the near arm bent, P1 "Alzato" (v4.28).

Run from ``tools/assets`` with the pinned venv:

    /home/bober/.venvs/paperscrape-assets/bin/python concepts/people/build_carry_sprites.py

This is the production half of ``build_carry_concepts.py``, which drew three candidate poses for
the v4.28 phase-2 photographs. The maintainer chose P1 -- the forearm raised, the hand at cheek
height ahead of the face, **fixed on all three walk frames**, with the far arm still swinging --
and only that pose is generated here, under the shipped names
``person_<kind>_<season>_carry<frame>_skin<tone>``.

### Why this is a generator and not twelve more committed SVGs

Concept B "Rilievo" as shipped (``build_people_concepts.walker_rilievo``, style ``rilievo_occhi``)
is what every walking pedestrian in the scene is drawn from. The carrying pose is that same figure
with **one limb replaced**: the near arm becomes an upper arm to an elbow and a forearm to a closed
hand. Authoring it as twelve independent SVGs would put a second hand-maintained copy of the head,
the body, the legs, the hair and the winter clothing of both adult families next to the first, and
the next change to a walker would have to be made twice.

**The proof that this is the same road as the shipped artwork, and it runs on every invocation:**
``check_reproduces_shipped`` re-renders the shipped man's three summer walk frames through this
exact path -- the same drawing code, the same trim, the same rasteriser -- and compares them
**byte for byte** with the PNGs in ``app/src/main/res/drawable-nodpi``. If that check ever stops
passing, the carrying pose has stopped being a variation of the real walker and the difference has
to be looked at before anything is regenerated. It is not a smoke test; it is the whole argument.

### What ships, and what does not

The **base** render of each frame is written to ``carry/`` as SVG and PNG and is *not* shipped: it
is the drawing, kept beside the concept scripts. What ships is written by
``tools/generate_people_layers.py``, which calls [walker] itself and turns each frame into fixed art
plus one weight mask per colourable region -- the v4.30 arrangement, in which a colour is not an
axis of the artwork at all. Until v5.4H this script also wrote 36 **skin-tone** PNGs into
``res``; v4.30 retired those and the loop that wrote them was left behind, so running the script
put files back that nothing draws. It is gone; see [build].

**v5.4H draws the children too.** The maintainer chose strada 1 variante 1b of the v5.4F proposal
round: the near arm raised, and the canopy at 70 %. Four families here, twelve frames, and
``PeopleLayerTable.CARRY`` goes from two families to four.

The hand's centre and the crown of the handle go, **per family**, to ``carry/hands.json``;
``SceneObjectRenderer`` reads them to hang the handle, which is a rectangle drawn in code so that
the same pose can carry any object later without new artwork.
"""
from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOL_ROOT = HERE.parent.parent
REPO = TOOL_ROOT.parent.parent
sys.path.insert(0, str(TOOL_ROOT))
sys.path.insert(0, str(HERE))

import build_people_concepts as bpc  # noqa: E402
from build_people_concepts import (Sprite, cut, oval, quad, shade, ground_shadow, ADULT, ADULT_TOP,  # noqa: E402
                                   CHILD_TOP, SUMMER, WINTER, SKIN, HAIR, CREAM, UNIT,
                                   CHILD_GEOMETRY, CHILD_OF_ADULT, ADULT_BOX_UNITS,
                                   CHILD_SUMMER_RISE, CHILD_GROUND)
from paperscrape_assets import raster  # noqa: E402
from paperscrape_assets.inventory import measure_raster  # noqa: E402

_spec = importlib.util.spec_from_file_location("skin", REPO / "tools" / "generate_skin_variants.py")
skin_tool = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(skin_tool)

RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
STYLE = "rilievo_occhi"
TRIM = (1, 1)            # the shipped family's trim: 41x85 -> 39x84, origin +(1,1)
CANVAS = (39, 84)


def child_walker(kind: str, season: str, frame: int, carrying: bool) -> tuple[Sprite, dict]:
    """``walker_rilievo_child`` verbatim, except the near arm when ``carrying`` (v5.4H).

    A second function beside [walker] for the same reason ``build_people_concepts`` has two: since
    v4.30 the child is not the adult scaled. Its skeleton is derived **from the target box
    downwards** -- crown, head, neck, torso, and the legs are whatever is left to the ground -- so
    ``CHILD_OF_ADULT`` lands on the proportion by construction. A branch inside [walker] would have
    had to choose between the two derivations on every line, and the copy here is held to the
    shipped drawing by [check_reproduces_shipped], which renders boy and girl through *this* path
    and compares them byte for byte with what ships.

    **The pose is the maintainer's choice of v5.4F, strada 1 variante 1b: the near arm raised.** It
    is the adult's P1 at a child's proportions -- elbow 5 down and 2.5 forward of the arm root
    against the adult's 9 and 3, forearm 10.5 up against 16 -- and like P1 it is fixed on all three
    walk frames while the far arm keeps swinging. The 1b half of the choice is not here: it is the
    canopy drawn at 70 %, which is a number in ``SceneObjectRenderer`` and not a drawing.
    """
    p = CHILD_GEOMETRY
    name = f"person_{kind}_{season}_walk{frame}" if not carrying else f"person_{kind}_{season}_carry{frame}"
    s = Sprite(name, 41, 85)
    seed = f"person_{kind}_{season}_walk{frame}"   # the same wobble as the shipped frame
    gar = (SUMMER if season == "summer" else WINTER)[kind]
    skin, hair = SKIN[kind], HAIR[kind]
    cx = 20.0
    head_ry, head_rx = p["head_ry"], p["head_rx"]
    content_top = 85.0 - CHILD_OF_ADULT * ADULT_BOX_UNITS
    crown = content_top + CHILD_SUMMER_RISE[kind]
    head_cy = crown + head_ry
    chin = head_cy + head_ry
    shoulder = chin + p["neck"]
    hip = shoulder + p["torso"]
    stride = (p["stride"], 0.0, -p["stride"])[frame]
    swing = (-p["swing"], 0.0, p["swing"])[frame]
    bob = 1.0 if frame == 1 else 0.0
    ground_shadow(s, cx=cx, rx=p["shadow"])
    leg_w = p["leg_w"]
    leg_col = gar["bottom"] or skin
    s.add(cut(quad(cx - 3, hip - 2, leg_w, cx - 3 - stride, CHILD_GROUND - bob, leg_w - 0.5), seed + "L1"), shade(leg_col, 0.14))
    s.add(cut(quad(cx + 3, hip - 2, leg_w, cx + 3 + stride, CHILD_GROUND - bob, leg_w - 0.5), seed + "L2"), leg_col)
    for i, fx in enumerate((cx - 3 - stride, cx + 3 + stride)):
        s.add(cut(oval(fx + 1, CHILD_GROUND + 0.5 - bob, p["foot"], 2.4, 10), seed + f"F{i}", 0.25), gar["shoe"] if i else shade(gar["shoe"], 0.14))
    hem = hip + (p["coat"] if season == "winter" else (p["dress"] if kind == "girl" else 0))
    sw = p["sw"]
    hw = p["hw_girl"] if (season == "summer" and kind == "girl") else p["hw_boy"]
    body = [(cx - sw + 2, shoulder), (cx + sw - 2, shoulder), (cx + sw, shoulder + 3), (cx + hw, hem - 2), (cx + hw - 2, hem), (cx - hw + 2, hem), (cx - hw, hem - 2), (cx - sw, shoulder + 3)]
    s.add(cut(body, seed + "B"), gar["top"])
    arm_len, arm_w = p["arm_len"], p["arm_w"]
    sleeve = gar["top"]
    hands = {}
    carry_parts = []
    for i, (ax, sw_dir, col) in enumerate(((cx - sw + 1.5, -swing, shade(sleeve, 0.16)), (cx + sw - 1.5, swing, sleeve))):
        if i == 1 and carrying:
            # ---- the carrying arm: upper arm to an elbow, forearm to a hand ----
            elbow = (ax + 2.5, shoulder + 7.0)
            hand = (ax + 2.0, chin - 2.0)
            # Drawn last (see the end): the hand is in front of the face, and the shipped order
            # puts the head over the arms.
            carry_parts = [
                (cut(quad(ax, shoulder + 2, arm_w, elbow[0], elbow[1], arm_w - 0.4), seed + "A1"), col, {}),
                (cut(quad(elbow[0], elbow[1], arm_w - 0.4, hand[0], hand[1], arm_w - 0.9), seed + "A1b"), col, {}),
                (cut(oval(hand[0], hand[1], 3.0, 3.0, 10), seed + "Hd1", 0.25), skin, {}),
            ]
            hands = {"hand": hand}
            continue
        s.add(cut(quad(ax, shoulder + 2, arm_w, ax + sw_dir, shoulder + 2 + arm_len, arm_w - 0.5), seed + f"A{i}"), col)
        s.add(cut(oval(ax + sw_dir, shoulder + 3 + arm_len, 3.0, 3.0, 10), seed + f"Hd{i}", 0.25), skin if i else shade(skin, 0.14))
    if season == "winter":
        sc = p["scarf"]
        s.add(cut([(cx - sc * 0.85, chin - 1.5), (cx + sc * 0.85, chin - 1.5), (cx + sc * 0.9, chin + 4), (cx + 3, chin + 9), (cx - 2, chin + 9), (cx - 2, chin + 4.5), (cx - sc * 0.85, chin + 4)], seed + "scarf"), gar["belt"])
    s.add(cut([(cx - 3, chin - 3), (cx + 3.5, chin - 3), (cx + 3.5, shoulder + 2), (cx - 3, shoulder + 2)], seed + "N"), skin, relief=False)
    hx, hy = cx + 0.5, head_cy
    if season == "winter" and kind == "boy":
        s.add(cut(oval(hx - 3, hy + 0.5, head_rx + 4.5, head_ry + 4.5, 14), seed + "hood", 0.3), gar["top"])
        s.add(cut(oval(hx - 1.5, hy + 0.5, head_rx + 1.5, head_ry + 1.5, 14), seed + "hoodin", 0.25), CREAM, relief=False)
    s.add(cut(oval(cx + 0.5, head_cy, head_rx, head_ry, 14), seed + "H", 0.3), skin)
    s.head = (hx, hy, head_rx, head_ry)
    if season == "summer":
        lobes = {
            "boy": [(hx - 3, hy - head_ry + 3, 7, 4.5), (hx + 3.5, hy - head_ry + 2.5, 6, 4.5)],
            "girl": [(hx - 3, hy - head_ry + 2.5, 7.5, 5), (hx + 3.5, hy - head_ry + 2, 6, 4.5), (hx - head_rx - 2, hy + 3, 4, 6), (hx + head_rx + 2, hy + 3, 4, 6)],
        }[kind]
        for i, (lx, ly, rx, ry) in enumerate(lobes):
            s.add(cut(oval(lx, ly, rx, ry, 10), seed + f"lobe{i}", 0.3), hair if kind != "boy" else gar["cap"])
        if kind == "boy":
            s.add(cut([(hx + 2, hy - head_ry + 3), (hx + head_rx + 5.5, hy - head_ry + 3.5), (hx + head_rx + 5.5, hy - head_ry + 6.5), (hx + 2, hy - head_ry + 6.5)], seed + "brim"), gar["cap"])
        if kind == "girl":
            for i, bx in enumerate((hx - head_rx - 2, hx + head_rx + 2)):
                s.add(cut(oval(bx, hy - 2.5, 2.4, 2.2, 8), seed + f"bow{i}", 0.2), gar["bow"], relief=False)
    else:
        if kind == "girl":
            s.add(cut(oval(hx, hy - head_ry + 1.5, head_rx + 1.5, 5.5, 12), seed + "hat", 0.3), gar["hat"])
            s.add(cut([(hx - head_rx - 2, hy - head_ry + 1.5), (hx + head_rx + 2, hy - head_ry + 1.5), (hx + head_rx + 2, hy - head_ry + 5), (hx - head_rx - 2, hy - head_ry + 5)], seed + "hatband"), CREAM)
            s.add(cut(oval(hx, hy - head_ry - 4, 3.2, 3.0, 10), seed + "pom", 0.25), CREAM)
    for points, fill, kw in carry_parts:
        s.add(points, fill, **kw)
    return s, hands


def walker(kind: str, season: str, frame: int, carrying: bool) -> tuple[Sprite, dict]:
    """``walker_rilievo`` verbatim, except the near arm when ``carrying``.

    Dispatches to [child_walker] for the two child families exactly as ``walker_rilievo`` dispatches
    to ``walker_rilievo_child``: since v4.30 a child is not an adult scaled down.

    Returns the sprite and, for the carrying pose, the hand centre in untrimmed units.
    """
    if not ADULT[kind]:
        return child_walker(kind, season, frame, carrying)
    name = f"person_{kind}_{season}_walk{frame}" if not carrying else f"person_{kind}_{season}_carry{frame}"
    s = Sprite(name, 41, 85)
    seed = f"person_{kind}_{season}_walk{frame}"   # the same wobble as the shipped frame
    adult = ADULT[kind]
    top = ADULT_TOP if adult else CHILD_TOP
    gar = (SUMMER if season == "summer" else WINTER)[kind]
    skin, hair = SKIN[kind], HAIR[kind]
    cx = 20.0
    head_ry = 11.5 if adult else 9.5
    head_rx = 10.5 if adult else 9.0
    head_cy = top + head_ry + 1.5
    chin = head_cy + head_ry
    shoulder = chin + 2
    hip = 56 if adult else 62
    stride = (6.5, 0.0, -6.5)[frame]
    swing = (-5.0, 0.0, 5.0)[frame]
    bob = 1.0 if frame == 1 else 0.0
    ground_shadow(s, cx=cx, rx=13 if adult else 11)
    leg_w = 6.5 if adult else 5.5
    leg_col = gar["bottom"] or skin
    s.add(cut(quad(cx - 3, hip - 2, leg_w, cx - 3 - stride, 81 - bob, leg_w - 0.5), seed + "L1"), shade(leg_col, 0.14))
    s.add(cut(quad(cx + 3, hip - 2, leg_w, cx + 3 + stride, 81 - bob, leg_w - 0.5), seed + "L2"), leg_col)
    for i, fx in enumerate((cx - 3 - stride, cx + 3 + stride)):
        s.add(cut(oval(fx + 1, 81.5 - bob, 5.0, 2.6, 10), seed + f"F{i}", 0.25), gar["shoe"] if i else shade(gar["shoe"], 0.14))
    hem = hip + (8 if season == "winter" else (3 if kind in ("woman", "girl") else 0))
    sw = 11.5 if adult else 9.5
    hw = (14.5 if season == "summer" and kind in ("woman", "girl") else 10.0) if adult else (11.0 if kind == "girl" else 8.5)
    body = [(cx - sw + 2, shoulder), (cx + sw - 2, shoulder), (cx + sw, shoulder + 3), (cx + hw, hem - 2), (cx + hw - 2, hem), (cx - hw + 2, hem), (cx - hw, hem - 2), (cx - sw, shoulder + 3)]
    s.add(cut(body, seed + "B"), gar["top"])
    arm_len = 22 if adult else 16
    arm_w = 5.5 if adult else 4.5
    sleeve = gar["top"]
    hands = {}
    carry_parts = []
    for i, (ax, sw_dir, col) in enumerate(((cx - sw + 1.5, -swing, shade(sleeve, 0.16)), (cx + sw - 1.5, swing, sleeve))):
        if i == 1 and carrying:
            # ---- the carrying arm: upper arm to an elbow, forearm to a hand ----
            # The hand may not reach the canvas edge: hand radius 3.2 plus the under-paper's
            # 1.4 must stay inside 39 trimmed units, which bounds the forward reach at ~ax+3.
            elbow = (ax + 3.0, shoulder + 11.0)
            hand = (ax + 2.5, chin - 3.0)
            # Drawn last (see the end): the hand is in front of the face, and the shipped order
            # puts the head over the arms.
            carry_parts = [
                (cut(quad(ax, shoulder + 2, arm_w, elbow[0], elbow[1], arm_w - 0.5), seed + "A1"), col, {}),
                (cut(quad(elbow[0], elbow[1], arm_w - 0.5, hand[0], hand[1], arm_w - 1.0), seed + "A1b"), col, {}),
                (cut(oval(hand[0], hand[1], 3.2, 3.2, 10), seed + "Hd1", 0.25), skin, {}),
            ]
            hands = {"hand": hand}
            continue
        s.add(cut(quad(ax, shoulder + 2, arm_w, ax + sw_dir, shoulder + 2 + arm_len, arm_w - 0.5), seed + f"A{i}"), col)
        s.add(cut(oval(ax + sw_dir, shoulder + 3 + arm_len, 3.2, 3.2, 10), seed + f"Hd{i}", 0.25), skin if i else shade(skin, 0.14))
    if season == "winter":
        s.add(cut([(cx - 8.5, chin - 1.5), (cx + 8.5, chin - 1.5), (cx + 9, chin + 4.5), (cx + 3, chin + 12), (cx - 2, chin + 12), (cx - 2, chin + 5), (cx - 8.5, chin + 4.5)], seed + "scarf"), gar["belt"])
    s.add(cut([(cx - 3, chin - 3), (cx + 3.5, chin - 3), (cx + 3.5, shoulder + 2), (cx - 3, shoulder + 2)], seed + "N"), skin, relief=False)
    hx, hy = cx + 0.5, head_cy
    if season == "winter" and kind == "woman":
        s.add(cut(oval(hx - head_rx + 1, hy + 4, 4.5, 9.5, 10), seed + "hairw", 0.3), hair)
    if season == "winter" and kind == "boy":
        s.add(cut(oval(hx - 3, hy + 0.5, head_rx + 4.5, head_ry + 4.5, 14), seed + "hood", 0.3), gar["top"])
        s.add(cut(oval(hx - 1.5, hy + 0.5, head_rx + 1.5, head_ry + 1.5, 14), seed + "hoodin", 0.25), CREAM, relief=False)
    s.add(cut(oval(cx + 0.5, head_cy, head_rx, head_ry, 14), seed + "H", 0.3), skin)
    s.head = (hx, hy, head_rx, head_ry)
    if season == "summer":
        lobes = {
            "man": [(hx - 5, hy - head_ry + 3, 6.5, 4.5), (hx + 2.5, hy - head_ry + 1.5, 6.5, 4.5), (hx - head_rx + 1.5, hy - 4, 3.5, 6.5)],
            "woman": [(hx - 4, hy - head_ry + 2.5, 7.5, 5), (hx + 4, hy - head_ry + 2, 6, 4.5), (hx - head_rx - 1, hy + 1, 4.5, 11), (hx - head_rx + 3, hy + 7, 4, 6)],
            "boy": [(hx - 3, hy - head_ry + 3, 7, 4.5), (hx + 3.5, hy - head_ry + 2.5, 6, 4.5)],
            "girl": [(hx - 3, hy - head_ry + 2.5, 7.5, 5), (hx + 3.5, hy - head_ry + 2, 6, 4.5), (hx - head_rx - 2, hy + 3, 4, 6), (hx + head_rx + 2, hy + 3, 4, 6)],
        }[kind]
        for i, (lx, ly, rx, ry) in enumerate(lobes):
            s.add(cut(oval(lx, ly, rx, ry, 10), seed + f"lobe{i}", 0.3), hair if not (kind == "boy") else gar["cap"])
        if kind == "boy":
            s.add(cut([(hx + 2, hy - head_ry + 3), (hx + head_rx + 5.5, hy - head_ry + 3.5), (hx + head_rx + 5.5, hy - head_ry + 6.5), (hx + 2, hy - head_ry + 6.5)], seed + "brim"), gar["cap"])
        if kind == "woman":
            s.add(cut([(hx - head_rx + 1, hy - head_ry + 4), (hx + head_rx - 2, hy - head_ry + 3), (hx + head_rx - 1.5, hy - head_ry + 6), (hx - head_rx + 1.5, hy - head_ry + 7)], seed + "band"), gar["belt"])
        if kind == "girl":
            for i, bx in enumerate((hx - head_rx - 2, hx + head_rx + 2)):
                s.add(cut(oval(bx, hy - 2.5, 2.4, 2.2, 8), seed + f"bow{i}", 0.2), gar["bow"], relief=False)
    else:
        if kind != "boy":
            s.add(cut(oval(hx, hy - head_ry + 1.5, head_rx + 1.5, 5.5, 12), seed + "hat", 0.3), gar["hat"])
            s.add(cut([(hx - head_rx - 2, hy - head_ry + 1.5), (hx + head_rx + 2, hy - head_ry + 1.5), (hx + head_rx + 2, hy - head_ry + 5), (hx - head_rx - 2, hy - head_ry + 5)], seed + "hatband"), gar["belt"] if kind != "girl" else CREAM)
            if kind == "girl":
                s.add(cut(oval(hx, hy - head_ry - 4, 3.2, 3.0, 10), seed + "pom", 0.25), CREAM)
    for points, fill, kw in carry_parts:
        s.add(points, fill, **kw)
    return s, hands


def trimmed(s: Sprite) -> Sprite:
    """The shipped trim applied verbatim, so the frame stays co-registered with what ships."""
    dx, dy = TRIM
    s.width_units, s.height_units = CANVAS
    for part in s.parts:
        part.points = [(x - dx, y - dy) for x, y in part.points]
    if s.head is not None:
        hx, hy, rx, ry = s.head
        s.head = (hx - dx, hy - dy, rx, ry)
    return s


def render(s: Sprite):
    svg = bpc.emit_svg(s, STYLE)
    r = raster.render_svg(svg)
    expected = (s.width_units * UNIT, s.height_units * UNIT)
    if r.size != expected:
        raise SystemExit(f"{s.name}: rendered {r.size}, expected {expected}")
    return svg, r


def check_reproduces_shipped() -> None:
    """The proof that this path is the shipped one: **all four families**, both seasons, every frame.

    v4.28 checked the man's three summer walk frames, which was the whole argument while only the
    two adult families had a carrying pose. v5.4H adds the children, and the children are not the
    adults scaled ([child_walker]), so the check has to reach them: a second derivation of the
    child skeleton that drifted by a unit would draw a child whose umbrella hangs off a shoulder
    nobody ships, and nothing else in the pipeline would say so.

    It is not a smoke test; it is the whole argument.
    """
    checked = 0
    for kind in bpc.KINDS:
        for season in ("summer", "winter"):
            for frame in range(3):
                s, _ = walker(kind, season, frame, carrying=False)
                s = bpc.faced(trimmed(s), STYLE)
                _, r = render(s)
                shipped = (RES / f"{s.name}.png").read_bytes()
                if r.png_bytes != shipped:
                    raise SystemExit(
                        f"the generator no longer reproduces the shipped frame {s.name}; stop and look")
                checked += 1
    print(f"  reproduces {checked} shipped walk frames byte-identically ({', '.join(bpc.KINDS)})")


#: How far above a family's own ink the handle's crown sits, in canvas units.
#:
#: The adults' shipped ``crownY`` is 1.0 and their summer ink reaches 1.667, so the rule the shipped
#: number already obeys is "0.7 above the ink"; the children are given the same rule rather than a
#: second one. **Measured on the summer frame**, which is the skeleton ``CHILD_SUMMER_RISE`` is
#: defined to land on the target box with -- a winter hood or a pompom stands higher and is not
#: compensated, exactly as the adults' winter hat is not.
CROWN_ABOVE_INK = 0.7

#: The adults' crown, **frozen at the value that ships** (``SceneObjectRenderer.carryCrownY``).
#:
#: The rule above is read off the adults rather than applied to them. Measured, the man's summer ink
#: asks for 0.97 and the woman's for 1.30, and the shipped set hangs both handles at 1.0 -- one
#: number for the two families. That is the artwork the v4.28 photographs were approved on and the
#: pixels every adult golden was authored against, so v5.4H does not move it by a third of a unit to
#: tidy an arithmetic: the children need a grip the adults never had, and that is the whole of what
#: this pass is allowed to change about the umbrella. The rule is **checked** against it below, so a
#: future redraw that walks the adults' heads away from 1.0 says so instead of drifting.
SHIPPED_ADULT_CROWN = 1.0


def build() -> None:
    """Writes the carrying pose for every family that has one, and the grips the engine hangs it by.

    ### What lands where

    The **base** render of each frame goes to ``carry/`` as SVG and PNG and is *not* shipped: it is
    the drawing this pose is, kept beside the concept scripts so the next change to it can be seen.
    What ships is written by ``tools/generate_people_layers.py``, which calls [walker] itself and
    turns each frame into fixed art plus one weight mask per colourable region.

    **This used to write 36 skin-tone PNGs straight into ``res`` and no longer does.** That was the
    v4.28 arrangement, and v4.30 retired it: a person is drawn as fixed art plus region masks with
    the colour arriving at the blit, so the tone copies were deleted from the shipped set two
    releases ago. The loop that wrote them stayed here, which meant running this script put 36
    retired files back into ``res`` that nothing draws and both memory ceilings charge for. Nothing
    had run it since, so nothing had noticed.

    ### hands.json

    ``<kind>/<frame>`` is the hand's centre on the 39x84 canvas and ``<kind>/crown`` the top of the
    handle, both in trimmed units, both read by ``SceneObjectRenderer``. Per **family**, because the
    child's grip is not the adult's and the girl's is not the boy's -- ``CHILD_SUMMER_RISE`` puts her
    head a unit higher, so her shoulder, and with it her hand, is a unit lower on the canvas.
    """
    out = HERE / "carry"
    out.mkdir(parents=True, exist_ok=True)
    check_reproduces_shipped()
    hands: dict = {}
    written = 0
    decoded = 0
    for kind in bpc.KINDS:
        for season in ("summer", "winter"):
            for frame in range(3):
                s, h = walker(kind, season, frame, carrying=True)
                s = bpc.faced(trimmed(s), STYLE)
                svg, r = render(s)
                (out / f"{s.name}.svg").write_text(svg + "\n", encoding="utf-8")
                (out / f"{s.name}.png").write_bytes(r.png_bytes)
                box = measure_raster(s.name, r).content_bbox
                if box[2] > CANVAS[0] * UNIT - 1:
                    raise SystemExit(f"{s.name}: content box {box} reaches the canvas edge")
                dx, dy = TRIM
                hands[f"{kind}/{frame}"] = [round(h["hand"][0] - dx, 2), round(h["hand"][1] - dy, 2)]
                if season == "summer" and frame == 0:
                    crown = round(box[1] / UNIT - CROWN_ABOVE_INK, 2)
                    if ADULT[kind]:
                        if abs(crown - SHIPPED_ADULT_CROWN) > 0.35:
                            raise SystemExit(
                                f"{kind}: the shipped adult crown {SHIPPED_ADULT_CROWN} is no longer "
                                f"what the ink asks for ({crown}); the adults' handles have moved "
                                f"and that is a decision, not a regeneration")
                        crown = SHIPPED_ADULT_CROWN
                    hands[f"{kind}/crown"] = crown
                written += 1
                decoded += r.size[0] * r.size[1] * 4
                print(f"  {s.name}: {r.size[0]}x{r.size[1]} content {list(box)} "
                      f"hand {hands[f'{kind}/{frame}']}")
    (out / "hands.json").write_text(json.dumps(hands, indent=1) + "\n", encoding="utf-8")
    # The hand is fixed across the three frames of this pose by construction: P1 does not follow
    # the swing. Said out loud here so a future edit that makes it move is noticed.
    for kind in bpc.KINDS:
        frames = [hands[f"{kind}/{f}"] for f in range(3)]
        if len(set(map(tuple, frames))) != 1:
            raise SystemExit(f"{kind}: P1's hand must not move between frames, got {frames}")
    grips = {tuple(hands[f"{k}/0"]) for k in bpc.KINDS}
    print(f"base frames written to carry/: {written}, {decoded} B if they were shipped "
          f"(they are not -- generate_people_layers.py writes what ships)")
    print(f"grips: " + ", ".join(f"{k}={hands[f'{k}/0']} crown={hands[f'{k}/crown']}" for k in bpc.KINDS))
    if len(grips) < 2:
        raise SystemExit("every family got the same grip, which the children cannot have")


if __name__ == "__main__":
    build()
