# Sky-and-water concepts for v4.26 — three proposals, not shipped

Three candidate redraws of the four families that live above the horizon and on the lake —
the bird, the cloud, the dolphin and the sailboat (hull and sail) — built for the size they are
actually seen at on the BV6600 (720×1440): a bird is **88 px** across, a cloud **190–380 px**,
a dolphin **33 px** nose to fluke and a sailboat **82 px** long. Nothing here is installed in
`res/drawable-nodpi/`; the choice is the maintainer's, made by looking at the full-frame
captures.

`build_skywater_concepts.py` writes every source and renders it through the project's own
rasteriser (`paperscrape_assets.raster`, probe verified). Each concept directory holds the five
SVG sources with their PNGs and a `sprites.concept.json` in the shipped registry's schema.

## What every concept shares

- **The shipped canvases, conventions and classes**: bird 90×24 px `CANVAS_PIXELS`, cloud
  798×396, dolphin 345×174, hull 252×51, sail 210×180 `SCENE_UNITS`; bird and cloud tintable
  masks (mean above 220, nothing darker than 14% below white), the lake three fixed art. No
  call site, no constant and no byte of the 29 MiB decoded budget moves.
- **The flap axis**: `drawBirds` mirrors the bird's canvas vertically about row 18, so the body
  and the head sit on that row and both wings rise above it.
- **A facing toward +x** on every sprite that has one, which is the only way the scene moves
  birds and lake decorations today. The sailboat's bow is at +x: jib forward of the mast,
  mainsail aft, the sheer rising to the prow inside the hull's 17 units.
- **Cut edges**: every arc is sampled into facets and every vertex carries a deterministic
  wobble written into the coordinates, sized from the pixels the sprite is drawn at.

## The three directions

| | A `sagoma` | B `rilievo` | C `agiorno` |
|---|---|---|---|
| Idea | one paper per object: the silhouette carries the identity | the oak's and the people's recipe: papers stacked on a darker paper beneath, offset down-right | the paper is cut through: openings let the sky or the water show |
| Tones | at most two, no shadow offset | offset sized per sprite (5.5 units on the dolphin, 2 on the boat, 3–4 on the cloud, x-only on the bird) | one, plus the openings |
| Cloud | six lobes, flat base, a shaded lower band | three stacked papers, each with its shadow | notched cusps and a scalloped base |
| Bird | one piece | body and wings, two papers | a cut eye and a lens in each wing |
| Dolphin | body and belly | five papers | a cut eye 2.6 px wide on screen |
| Sailboat | hull and stripe; mast, main, jib, band | the same with under-papers and a boom | portholes, a sail window, light between the sails |

## Known consequences for phase 2, whichever is chosen

- `CloudCoverage.CLOUD_CONTENT_HALF_UNITS` / `_HALF_HEIGHT_UNITS` are measured off the shipped
  cloud's content box, which fills its canvas; every concept leaves a margin, so both are
  re-derived from the chosen PNG before shipping (the file's own comment says so).
- `SpriteMeasurementClaimTest` pins the bird and dolphin canvases as written in comments; the
  canvases are unchanged here, so nothing moves.
- The capture builds of phase 1 carried a mirror for odd-index birds and lake items so both
  directions could be judged in one frame; that code is marked `CAPTURE-ONLY` in
  `PaperRenderer` and is not part of any proposal.
