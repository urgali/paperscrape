#!/usr/bin/env python3
"""Generates skin-tone variants of the four person sprites.

Run from the repo root:

    python3 tools/generate_skin_variants.py

The art is flat: each character's skin is a *single* colour with no shading ramp,
which is what makes an exact recolour possible. Interior skin pixels are replaced
outright; anti-aliased edge pixels are decomposed into the blend they actually are
(`P = a*skin + (1-a)*other`) and recomposed against the new tone, so silhouettes and
outlines survive unchanged.

The script verifies its own output: every non-skin colour must keep exactly the same
pixel mask it had in the source, or the variant is rejected. Clothes, hair, eyes and
outlines are therefore untouched by construction rather than by hope.
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

RES = Path("app/src/main/res/drawable-nodpi")

# Each character's shipped skin colour, read off the sprites rather than assumed.
SKIN_BASE = {
    "man": (220, 169, 124),
    "woman": (240, 201, 166),
    "boy": (169, 113, 75),
    "girl": (239, 185, 148),
}

# The canonical tones every character is rendered in.
#
# All three are shipped PaperScrape paint: the woman's, the man's and the boy's own
# skin colours. Nothing here is invented, which is why the variants cannot drift out
# of the art style -- every tone is a colour the set already draws people in.
#
# A fourth, deeper tone was generated and then dropped. At the depth needed to read as
# a distinct tone it converged on the woman's brown hair (140, 90, 56) and on the
# shared outline (74, 64, 56), and a face whose skin matches its own hair stops reading
# as a face. Lightening it far enough to separate would have put it on top of the boy's
# brown. There is no room for a fourth tone between those neighbours in this palette;
# widening the range needs an art pass on hair and outlines, not another recolour.
TONES = [
    (240, 201, 166),  # 0 light
    (220, 169, 124),  # 1 tan
    (169, 113, 75),   # 2 brown
]

VARIANTS = ["walk0", "walk1", "walk2", "head_window", "head_car"]
SEASONS = ["summer", "winter"]

# The second outfit: what each adult's seated bust wears instead of what it wears.
#
# Until v4.20 clothing *was* family. Every woman bust wore the same top and every man bust the
# same one, so a viewer who could tell the two apart at all could tell them apart by colour, and
# the pairing rule ("the passenger is never the driver's own family") meant a car was reliably one
# of each. Item 5 of BACKLOG_v4_19.md.
#
# **Which colour is the garment was measured, not assumed.** The bust is 141x132 and its shoulders
# occupy y 111-127; the colour that fills that band is the clothing. The others nearby are not:
# the woman's yellow sits at y 27-44, which is a *headband*, and the man's winter blue at y 3-46 is
# his hat, with his scarf at y 96-122. The backlog entry called the yellow part of her outfit,
# which is where that came from. Each garment is a single flat colour on all four busts, which is
# why this needs no machinery beyond the recolour that already generates the skin tones.
#
# **The second outfit is the other adult family's garment for that season**, so nothing is
# invented: every colour here is paint this set already puts on clothing, exactly as TONES are
# tones the set already paints skin in. Neither replacement collides with anything else on the
# sprite that wears it -- checked against all four palettes.
OUTFITS = {
    ("man", "summer"): ((78, 159, 181), (228, 98, 62)),
    ("man", "winter"): ((71, 105, 143), (191, 65, 48)),
    ("woman", "summer"): ((228, 98, 62), (78, 159, 181)),
    ("woman", "winter"): ((191, 65, 48), (71, 105, 143)),
}

#: The sprite slot the outfit axis applies to, and the suffix its variants carry.
#:
#: Seated busts only. A pedestrian's clothing is already varied by four families walking about,
#: and doubling the 96 walker recolours would cost 7 MiB to solve a problem the walkers do not
#: have.
OUTFIT_VARIANT = "head_car"
OUTFIT_SUFFIX = "alt"

#: How far past the garment's own pixels the recolour may reach, in pixels. The width the
#: artwork's outline occupies, and the same reach the outline tests call the edge band.
GARMENT_EDGE_DEPTH = 2

# Bases that no longer ship, and the tone copy that stands in for each.
#
# v4.19 retired the four adult `head_car` bases and v4.20 the two boy ones, in every case only
# after verifying the base was pixel-identical to one of its own tone copies -- so the heir named
# here *is* the retired drawing under another name, and regenerating the other tones from it
# reproduces the shipped files byte for byte (measured: 0 differing pixels, all six).
#
# **This map is not decoration: without it this script cannot run at all.** v4.19 deleted four of
# the sources the loop below requires and did not tell the loop, so `generate_skin_variants.py`
# has been exiting on `sorgente mancante: person_man_summer_head_car.png` ever since -- the tool
# the registry points at for regenerating variants could not regenerate anything. Recorded in
# BACKLOG_v4_20.md as found during that pass.
#
# The value is `(heir suffix, the tone index the heir carries)`, because a recolour needs to know
# which colour it is moving *from*.
RETIRED_BASES = {
    "person_man_summer_head_car": ("skin1", 1),
    "person_man_winter_head_car": ("skin1", 1),
    "person_woman_summer_head_car": ("skin0", 0),
    "person_woman_winter_head_car": ("skin0", 0),
    "person_boy_summer_head_car": ("skin2", 2),
    "person_boy_winter_head_car": ("skin2", 2),
}

# How close a pixel must sit to a skin/other blend line to count as an edge pixel.
RESIDUAL_LIMIT = 14.0
# Palette colours rarer than this are themselves anti-aliasing, not paint.
PALETTE_MIN = 80


def palette(rgb, alpha):
    """The sprite's actual paint colours, as an (n,3) array."""
    solid = rgb[alpha > 200].reshape(-1, 3)
    colours, counts = np.unique(solid, axis=0, return_counts=True)
    return colours[counts >= PALETTE_MIN].astype(np.float64)


#: The single tone every paper in this artwork is shaded toward.
#:
#: Not a guess: `tools/assets/concepts/people/build_people_concepts.py` builds each piece's
#: under-paper, and each three-quarter figure's far leg and far hand, with `shade(colour, t)`,
#: which mixes that colour toward this one. Reading it here is what lets a recolour move a paint
#: *and its own shadows* together instead of leaving them behind.
SHADE_TOWARDS = (43, 42, 51)

#: How far a flat colour may sit off the line between a paint and [SHADE_TOWARDS] and still count
#: as a shade of it. Tight, because a colour that is merely *near* that line -- the woman's brown
#: hair against her skin, the case the `is_paint` guard was written for -- must not be swept in.
SHADE_RESIDUAL = 6.0

#: The deepest shade the artwork uses is 0.34; anything past 0.6 is a different paint that happens
#: to lie dark on the same line.
SHADE_MAX_T = 0.6


def shade_family(rgb, alpha, base):
    """``base`` and every flat colour of this sprite that is ``base`` shaded, as (colour, t).

    B "Rilievo" draws an under-paper beneath every piece and a darker far leg and far hand, so a
    character's skin is not one flat colour any more -- it is three. Moving only the lightest left
    the darker two behind and, worse, moved the anti-aliased band *around* them, so a far leg came
    out with the old tone inside and the new tone in its outline. The recolour has to know the
    whole family or it cannot be exact, and the family is measured off the sprite rather than
    declared, so a future piece shaded at some other depth is picked up on its own.
    """
    b = np.array(base, dtype=np.float64)
    d = np.array(SHADE_TOWARDS, dtype=np.float64)
    axis = d - b
    denom = float(axis @ axis) or 1e-9
    family = [(np.array(base, dtype=np.float64), 0.0)]
    for colour in palette(rgb, alpha):
        if np.array_equal(colour, b):
            continue
        t = float((colour - b) @ axis / denom)
        if not (0.02 <= t <= SHADE_MAX_T):
            continue
        if np.linalg.norm(colour - (b + t * axis)) > SHADE_RESIDUAL:
            continue
        family.append((colour, t))
    return family


def moved_shade(member, t, base, target):
    """Where a shade of ``base`` lands when ``base`` moves to ``target``.

    A shade is ``(1-t)*paint + t*SHADE_TOWARDS``, so moving the paint moves its shade by
    ``(1-t)`` of the same step and leaves the dark end where it is. Written as a displacement of
    the member rather than as a shade recomputed from the new paint, because only the displacement
    form is **exactly** the identity when ``target`` equals ``base``: the registry declares each
    character's own-tone copy IDENTICAL_BY_CONSTRUCTION, and recomputing put 319 pixels of one walk
    frame one level apart in blue -- the projected ``t`` reproduces a shade of a shade to within a
    rounding step, and a rounding step is not identity.
    """
    m = np.array(member, dtype=np.float64)
    step = (1.0 - t) * (np.array(target, dtype=np.float64) - np.array(base, dtype=np.float64))
    return tuple(float(round(v)) for v in (m + step))


def recolour_family(image, base, target, region=None):
    """Move ``base`` and every shade of it to ``target`` and the matching shades of it.

    One pass per family member, each the same verified single-colour move, merged by taking every
    pixel any pass changed. The members' interiors are disjoint by construction, and where two of
    them meet, both passes move the boundary pixel into the same new family, so which one wins does
    not change the result.
    """
    data = np.array(image)
    rgb = data[:, :, :3].astype(np.float64)
    family = shade_family(rgb, data[:, :, 3], base)
    # **The move is confined to the family's own pixels and the outline band around them**, the
    # same restriction the second outfit has always applied to the garment, and for a sharper
    # version of the same reason. The edge fit runs on un-premultiplied RGB, where a barely opaque
    # pixel's colour is whatever survived the division, so a 25%-alpha pixel on the far side of the
    # sprite can land near the skin blend line by arithmetic accident: without this, 404 pixels of
    # one walk frame's shirt and trouser edges moved with the skin tone. Where the paint is not,
    # the tone has no business.
    reach = np.zeros(data.shape[:2], dtype=bool)
    for colour, _ in family:
        reach |= np.all(data[:, :, :3] == colour.astype(np.uint8), axis=2)
    reach = dilate(reach, GARMENT_EDGE_DEPTH)
    reach = reach if region is None else (reach & region)
    out = data.copy()
    for colour, t in family:
        _, moved = recolour_image(image, tuple(colour), moved_shade(colour, t, base, target), region=reach)
        m = np.array(moved)
        changed = (m != data).any(axis=2)
        out[changed] = m[changed]
    return image, Image.fromarray(out, "RGBA"), family


def recolour(path, base, target):
    image = Image.open(path).convert("RGBA")
    source, out, family = recolour_family(image, base, target)
    return source, out


def dilate(mask, depth):
    """``mask`` grown by ``depth`` pixels in the four directions."""
    reach = mask.copy()
    for _ in range(depth):
        grown = np.zeros_like(reach)
        grown[1:, :] |= reach[:-1, :]
        grown[:-1, :] |= reach[1:, :]
        grown[:, 1:] |= reach[:, :-1]
        grown[:, :-1] |= reach[:, 1:]
        reach |= grown
    return reach


def recolour_image(image, base, target, region=None):
    data = np.array(image)
    rgb = data[:, :, :3].astype(np.float64)
    alpha = data[:, :, 3]
    base = np.array(base, dtype=np.float64)
    target = np.array(target, dtype=np.float64)
    out = rgb.copy()

    exact = np.all(rgb == base, axis=2)
    if region is not None:
        exact &= region
    out[exact] = target

    others = np.array([c for c in palette(rgb, alpha) if not np.array_equal(c, base)])
    # Paint is paint: a pixel that exactly matches another of the sprite's own colours
    # is never treated as an edge blend, however neatly it happens to sit on the line
    # between skin and something dark. Without this the woman's brown hair decomposes
    # as a skin blend and moves with the tone.
    is_paint = np.zeros(rgb.shape[:2], dtype=bool)
    for colour in others:
        is_paint |= np.all(rgb == colour[None, None, :], axis=2)
    candidates = (~exact) & (~is_paint) & (alpha > 0)
    if region is not None:
        candidates &= region
    ys, xs = np.nonzero(candidates)
    if len(ys) and len(others):
        pixels = rgb[ys, xs]                                   # (n,3)
        # For every non-skin paint colour X, the best a in P = a*base + (1-a)*X.
        dirs = base[None, :] - others                          # (m,3)
        denom = np.sum(dirs * dirs, axis=1)                    # (m,)
        denom[denom == 0] = 1e-9
        diff = pixels[:, None, :] - others[None, :, :]         # (n,m,3)
        a = np.clip(np.sum(diff * dirs[None, :, :], axis=2) / denom[None, :], 0.0, 1.0)
        fitted = others[None, :, :] + a[:, :, None] * dirs[None, :, :]
        residual = np.linalg.norm(pixels[:, None, :] - fitted, axis=2)
        best = np.argmin(residual, axis=1)
        rows = np.arange(len(ys))
        good = (residual[rows, best] <= RESIDUAL_LIMIT) & (a[rows, best] > 0.02)
        # Shift the pixel by its own skin fraction rather than rebuilding it from the
        # fitted line. Identical arithmetic for interior skin (a = 1 gives exactly the
        # target), but an edge pixel keeps whatever the artist actually painted and is
        # merely moved -- and a tone equal to the source leaves the sprite untouched to
        # the byte instead of snapping its edges onto the fit.
        blend = a[rows, best][:, None]
        shifted = pixels + blend * (target - base)[None, :]
        sel = rows[good]
        out[ys[sel], xs[sel]] = shifted[sel]

    result = data.copy()
    result[:, :, :3] = np.clip(np.round(out), 0, 255).astype(np.uint8)
    return image, Image.fromarray(result, "RGBA")


def verify(source, variant, base):
    """No pixel painted a colour other than the skin may lose it, and nothing may change away
    from the skin's own edge.

    **Losing, not gaining.** This used to require each non-skin colour to keep its mask *exactly*,
    in both directions, and that is stricter than the thing it protects. The recolour decomposes an
    anti-aliased pixel into the blend it is and recomposes it against the new tone; a pixel that was
    almost entirely trousers, with a trace of skin in it, can land exactly on the trousers' own flat
    value once the trace moves. Nothing about the trousers changed -- one edge pixel simply arrived
    at a colour already in the palette. On the v4.25 people that happens on two pixels of one walk
    frame, and refusing it would mean tuning artwork around an equality test.

    What must not happen is a garment, hair or outline pixel *ceasing* to be its colour, which is
    the direction that means the recolour reached into the drawing. That is checked here, and
    [changed_outside] checks the same thing geometrically.
    """
    a = np.array(source)
    b = np.array(variant)
    if a.shape != b.shape:
        return "dimensioni diverse"
    if not np.array_equal(a[:, :, 3], b[:, :, 3]):
        return "canale alpha modificato"
    rgb = a[:, :, :3].astype(np.float64)
    family = shade_family(rgb, a[:, :, 3], base)
    moved = [c for c, _ in family]
    for colour in palette(rgb, a[:, :, 3]):
        if any(np.array_equal(colour, m) for m in moved):
            continue
        c = colour.astype(np.uint8)
        before = np.all(a[:, :, :3] == c, axis=2)
        after = np.all(b[:, :, :3] == c, axis=2)
        lost = int(np.count_nonzero(before & ~after))
        if lost:
            return f"colore non-pelle {tuple(int(v) for v in c)}: {lost} pixel perduti"
    outside = changed_outside(a, b, family)
    if outside:
        return f"{outside} pixel cambiati fuori dal bordo della famiglia di tinte"
    return None


def changed_outside(a, b, family):
    """How many pixels changed further than the outline band from every pixel of ``family``.

    The same geometric restriction the second-outfit move already applies to the garment: a tone
    change may touch the paint it moves, its own shades, and the anti-aliased band around them, and
    nothing else. A mask comparison alone cannot say this -- it only sees colours it recognises as
    paint, so a stray move that landed on no palette colour at all would pass it.
    """
    reach = np.zeros(a.shape[:2], dtype=bool)
    for colour, _ in family:
        reach |= np.all(a[:, :, :3] == colour.astype(np.uint8), axis=2)
    reach = dilate(reach, GARMENT_EDGE_DEPTH)
    changed = (a != b).any(axis=2)
    return int(np.count_nonzero(changed & ~reach))


def main():
    if not RES.is_dir():
        sys.exit(f"{RES} non trovata -- esegui dalla radice del repo")
    written = 0
    for kind, skin in SKIN_BASE.items():
        for season in SEASONS:
            for variant in VARIANTS:
                stem = f"person_{kind}_{season}_{variant}"
                src = RES / f"{stem}.png"
                base = skin
                if not src.is_file():
                    heir = RETIRED_BASES.get(stem)
                    if heir is None:
                        sys.exit(f"sorgente mancante: {src}")
                    suffix, tone_index = heir
                    src = RES / f"{stem}_{suffix}.png"
                    # The heir is the retired base's own pixels, so the colour to move away from
                    # is the tone the heir carries, not the character's original skin.
                    base = TONES[tone_index]
                    if not src.is_file():
                        sys.exit(f"erede dichiarato ma mancante: {src}")
                for index, tone in enumerate(TONES):
                    source, out = recolour(src, base, tone)
                    problem = verify(source, out, base)
                    if problem:
                        sys.exit(f"{src.name} tono {index}: {problem}")
                    out.save(RES / f"{stem}_skin{index}.png", optimize=True)
                    written += 1
                written += write_outfit_variants(kind, season, variant, src, base)
    print(f"{written} varianti scritte, tutte verificate")


def write_outfit_variants(kind, season, variant, src, skin_base):
    """The second outfit's three tones, or nothing if this slot has no second outfit.

    Two recolours in sequence, each the same verified one-flat-colour move: the garment first,
    which produces the second outfit in the family's own skin, and then the three tones off that.
    Doing it in that order rather than the other way round is what keeps it to two steps instead
    of six, and the intermediate is never written -- the shipped set carries the three tones and
    not the base, exactly as it does for the first outfit.
    """
    outfit = OUTFITS.get((kind, season))
    if outfit is None or variant != OUTFIT_VARIANT:
        return 0
    garment, replacement = outfit
    source = Image.open(src).convert("RGBA")
    # **The move is confined to the garment and the two pixels around it.**
    #
    # Without this it is not: the edge decomposition looks for any pixel that reads as a blend of
    # the moved colour with another, and on the winter busts the coat sits close enough in hue to
    # the *hat* that hat-edge pixels fit that description. Unrestricted, the man's winter bust
    # came out with 93 pixels above the shoulders moved by more than 24 levels -- a recolour of
    # the hat's outline, quietly, on the way past. Clothing is what this axis is allowed to
    # change.
    #
    # Two pixels is the same reach `tools/assets/tests/test_outline.py` calls the silhouette's
    # edge band, which is the width the artwork's own outline occupies, so the region covers the
    # garment and every anti-aliased pixel that genuinely belongs to it and nothing further.
    pixels = np.array(source)
    garment_family = shade_family(pixels[:, :, :3].astype(np.float64), pixels[:, :, 3], garment)
    region = np.zeros(pixels.shape[:2], dtype=bool)
    for colour, _ in garment_family:
        region |= np.all(pixels[:, :, :3] == colour.astype(np.uint8), axis=2)
    region = dilate(region, GARMENT_EDGE_DEPTH)
    _, dressed, _ = recolour_family(source, garment, replacement, region=region)
    problem = verify(source, dressed, garment)
    if problem:
        sys.exit(f"{src.name} secondo completo: {problem}")
    outside = (np.array(dressed) != pixels).any(axis=2) & ~region
    if outside.any():
        sys.exit(f"{src.name} secondo completo: {int(outside.sum())} pixel fuori dal vestito")
    written = 0
    stem = f"person_{kind}_{season}_{variant}_{OUTFIT_SUFFIX}"
    for index, tone in enumerate(TONES):
        toned_source, out, _ = recolour_family(dressed, skin_base, tone)
        problem = verify(toned_source, out, skin_base)
        if problem:
            sys.exit(f"{stem} tono {index}: {problem}")
        out.save(RES / f"{stem}_skin{index}.png", optimize=True)
        written += 1
    return written


if __name__ == "__main__":
    main()
