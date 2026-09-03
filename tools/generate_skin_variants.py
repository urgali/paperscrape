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


def recolour(path, base, target):
    return recolour_image(Image.open(path).convert("RGBA"), base, target)


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
    """Every colour that is not the skin must keep exactly the mask it had."""
    a = np.array(source)
    b = np.array(variant)
    if a.shape != b.shape:
        return "dimensioni diverse"
    if not np.array_equal(a[:, :, 3], b[:, :, 3]):
        return "canale alpha modificato"
    rgb = a[:, :, :3].astype(np.float64)
    for colour in palette(rgb, a[:, :, 3]):
        if np.array_equal(colour, np.array(base, dtype=np.float64)):
            continue
        c = colour.astype(np.uint8)
        before = np.all(a[:, :, :3] == c, axis=2)
        after = np.all(b[:, :, :3] == c, axis=2)
        if not np.array_equal(before, after):
            return f"colore non-pelle {tuple(int(v) for v in c)} alterato"
    return None


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
    region = dilate(np.all(pixels[:, :, :3] == np.array(garment), axis=2), GARMENT_EDGE_DEPTH)
    _, dressed = recolour_image(source, garment, replacement, region=region)
    problem = verify(source, dressed, garment)
    if problem:
        sys.exit(f"{src.name} secondo completo: {problem}")
    outside = (np.array(dressed) != pixels).any(axis=2) & ~region
    if outside.any():
        sys.exit(f"{src.name} secondo completo: {int(outside.sum())} pixel fuori dal vestito")
    written = 0
    stem = f"person_{kind}_{season}_{variant}_{OUTFIT_SUFFIX}"
    for index, tone in enumerate(TONES):
        toned_source, out = recolour_image(dressed, skin_base, tone)
        problem = verify(toned_source, out, skin_base)
        if problem:
            sys.exit(f"{stem} tono {index}: {problem}")
        out.save(RES / f"{stem}_skin{index}.png", optimize=True)
        written += 1
    return written


if __name__ == "__main__":
    main()
