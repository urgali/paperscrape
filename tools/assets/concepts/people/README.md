# People concepts for v4.25 — three proposals, not shipped

Three candidate redraws of the whole people family, built for the size they are actually
seen at: an adult walker is **37 px tall on the BV6600** (measured on a captured frame),
a child about 28, a bust in a car about 15 and a bust at a window about 8. Nothing here is
installed in `res/drawable-nodpi/`; the choice is the maintainer's, made by looking at the
full-frame captures.

`build_people_concepts.py` writes every source and renders it through the project's own
rasteriser (`paperscrape_assets.raster`, probe verified). Each concept directory holds the
40 SVG sources with their PNGs and a `sprites.concept.json` in the shipped registry's schema.

## What every concept shares

- **The shipped canvases**: walk 123×255, window bust 159×171, car bust 141×132. Same
  `SCENE_UNITS` convention, same `CONTENT_BOTTOM_CENTRE` anchors, same `FIXED_ART` class,
  so no call site, no constant and no byte of the 29 MiB decoded budget moves.
- **Cut edges**: every shape is a polygon with a small deterministic wobble written into
  its coordinates — the rule the sky adopted in v4.23.
- **No feature thinner than three units.** The GL backend minifies with one bilinear tap
  and no mipmaps (`GlTextureAtlas.kt`), so at the ~1:8 reduction a walker gets, anything
  thinner appears and disappears as the figure moves.
- **One flat skin colour per character**, the shipped tone, so `generate_skin_variants.py`
  still applies. Garment paints are the shipped ones too.
- Walk frames 0 and 2 differ by construction on the side-view figures: the far leg is a
  darker paper than the near one.

## The three directions

| | A `stampino` | B `rilievo` | C `bambola` |
|---|---|---|---|
| Idea | the least paper that still reads as a person | stacked papers, the Quercia larga's recipe | a frontal paper doll with a face |
| Pieces per walker | 3–4 (+ hair, feet) | 6–8, each with an under-paper shadow | 6–7 |
| View | three-quarter, facing +x | three-quarter, facing +x | frontal |
| Face | none | none | two eyes, 3.6 units |
| Outline | none | none (the under-paper does the separating) | one outer outline round the union, 1.5 units |
| Arms | folded into the body block | separate strips with hands, swinging | short stubs with hands |

## Known consequences for phase 2, whichever is chosen

- `tools/assets/tests/test_outline.py` encodes the current rule that every person sprite
  carries an outer outline; A and B drop it deliberately, so that rule changes with them.
- `SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS` (23.7) is measured off the shipped man's
  crown and jaw rows; a redraw re-measures it, and for C the head is larger (see the
  generator), which moves `CAR_OCCUPANT_SCALE` unless the seated fit or the car glass moves.
- The winter window busts are drawn but unreachable: `seasonIndexFor(Exposure.INDOORS)`
  always reads the summer column.
