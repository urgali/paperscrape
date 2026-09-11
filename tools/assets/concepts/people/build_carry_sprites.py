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

The **base** render of each frame is written to ``carry/`` as SVG and PNG and is *not* shipped: the
call site indexes by skin tone only, so a fourth un-toned copy of every frame would be 1 415 232
decoded bytes nothing blits. What ships is the 36 tone PNGs -- 2 adult families x 2 seasons x
3 frames x 3 tones -- recoloured by ``tools/generate_skin_variants.py``'s own verified single-colour
move, written straight into ``app/src/main/res/drawable-nodpi`` the way that script writes its own
variants. Children never carry anything, so no child frame is drawn.

The hand's centre per frame goes to ``carry/hands.json``; ``SceneObjectRenderer`` reads those three
numbers to hang the handle, which is a rectangle drawn in code so that the same pose can carry any
object later without new artwork.
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
from build_people_concepts import Sprite, cut, oval, quad, shade, ground_shadow, ADULT, ADULT_TOP, CHILD_TOP, SUMMER, WINTER, SKIN, HAIR, CREAM, UNIT  # noqa: E402
from paperscrape_assets import raster  # noqa: E402
from paperscrape_assets.inventory import measure_raster  # noqa: E402

_spec = importlib.util.spec_from_file_location("skin", REPO / "tools" / "generate_skin_variants.py")
skin_tool = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(skin_tool)

RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
STYLE = "rilievo_occhi"
TRIM = (1, 1)            # the shipped family's trim: 41x85 -> 39x84, origin +(1,1)
CANVAS = (39, 84)


def walker(kind: str, season: str, frame: int, carrying: bool) -> tuple[Sprite, dict]:
    """``walker_rilievo`` verbatim, except the near arm when ``carrying``.

    Returns the sprite and, for the carrying pose, the hand centre in untrimmed units.
    """
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
    """The proof that this path is the shipped one: the shipped man, summer, three frames."""
    for frame in range(3):
        s, _ = walker("man", "summer", frame, carrying=False)
        s = bpc.faced(trimmed(s), STYLE)
        _, r = render(s)
        shipped = (RES / f"{s.name}.png").read_bytes()
        same = r.png_bytes == shipped
        print(f"  reproduces shipped {s.name}: {'byte-identical' if same else 'DIFFERS'}")
        if not same:
            raise SystemExit("the generator no longer reproduces the shipped frame; stop and look")


def build() -> None:
    out = HERE / "carry"
    out.mkdir(parents=True, exist_ok=True)
    check_reproduces_shipped()
    hands: dict = {}
    shipped_bytes = 0
    decoded = 0
    count = 0
    for kind in ("man", "woman"):
        for season in ("summer", "winter"):
            for frame in range(3):
                s, h = walker(kind, season, frame, carrying=True)
                s = bpc.faced(trimmed(s), STYLE)
                svg, r = render(s)
                (out / f"{s.name}.svg").write_text(svg + "\n", encoding="utf-8")
                base_png = out / f"{s.name}.png"
                base_png.write_bytes(r.png_bytes)
                box = measure_raster(s.name, r).content_bbox
                if box[2] > CANVAS[0] * UNIT - 1:
                    raise SystemExit(f"{s.name}: content box {box} reaches the canvas edge")
                dx, dy = TRIM
                hands[f"{kind}/{frame}"] = [round(h["hand"][0] - dx, 2), round(h["hand"][1] - dy, 2)]
                # The three tones, by the shipped recolour, written where the app reads them.
                for tone, target in enumerate(skin_tool.TONES):
                    source, variant = skin_tool.recolour(base_png, skin_tool.SKIN_BASE[kind], target)
                    problem = skin_tool.verify(source, variant, skin_tool.SKIN_BASE[kind])
                    if problem:
                        raise SystemExit(f"{s.name} tone {tone}: {problem}")
                    p = RES / f"{s.name}_skin{tone}.png"
                    variant.save(p, optimize=True)
                    shipped_bytes += p.stat().st_size
                    decoded += r.size[0] * r.size[1] * 4
                    count += 1
                print(f"  {s.name}: {r.size[0]}x{r.size[1]} content {list(box)} hand {hands[f'{kind}/{frame}']}")
    (out / "hands.json").write_text(json.dumps(hands, indent=1) + "\n", encoding="utf-8")
    # The hand is fixed across the three frames of this pose by construction: P1 does not follow
    # the swing. Said out loud here so a future edit that makes it move is noticed.
    for kind in ("man", "woman"):
        frames = [hands[f"{kind}/{f}"] for f in range(3)]
        if len(set(map(tuple, frames))) != 1:
            raise SystemExit(f"{kind}: P1's hand must not move between frames, got {frames}")
    print(f"shipped tone PNGs: {count}, {shipped_bytes} B on disk, {decoded} B decoded")


if __name__ == "__main__":
    build()
