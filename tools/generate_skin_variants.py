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

VARIANTS = ["walk0", "walk1", "walk2", "head_window"]
SEASONS = ["summer", "winter"]

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
    image = Image.open(path).convert("RGBA")
    data = np.array(image)
    rgb = data[:, :, :3].astype(np.float64)
    alpha = data[:, :, 3]
    base = np.array(base, dtype=np.float64)
    target = np.array(target, dtype=np.float64)
    out = rgb.copy()

    exact = np.all(rgb == base, axis=2)
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
    for kind, base in SKIN_BASE.items():
        for season in SEASONS:
            for variant in VARIANTS:
                src = RES / f"person_{kind}_{season}_{variant}.png"
                if not src.is_file():
                    sys.exit(f"sorgente mancante: {src}")
                for index, tone in enumerate(TONES):
                    source, out = recolour(src, base, tone)
                    problem = verify(source, out, base)
                    if problem:
                        sys.exit(f"{src.name} tono {index}: {problem}")
                    out.save(
                        RES / f"person_{kind}_{season}_{variant}_skin{index}.png",
                        optimize=True,
                    )
                    written += 1
    print(f"{written} varianti scritte, tutte verificate")


if __name__ == "__main__":
    main()
