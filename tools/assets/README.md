# Asset source pipeline

Offline developer tooling. **Gradle never runs any of this**, and the app has no
dependency on it. It exists so that a sprite has a source other than the sprite
itself, and can be re-derived, re-scaled or corrected without editing pixels.

```
SVG source  ->  deterministic, version-pinned rasterisation  ->  PNG
```

## What this delivers, and what it does not

**It delivers** a committed source format, a rasterisation path whose output does
not depend on the machine it runs on, a registry covering every shipped sprite
including the ones with no source, and measurement that says how close a
regenerated sprite is to the one that ships.

**It does not** replace artwork. `render` never writes into
`app/src/main/res/drawable-nodpi/` and refuses a `--out` that points inside it:
replacing a shipped sprite changes what users see and needs a mockup and approval
first.

`normalize --apply` is the single exception, and the difference is the point
rather than a loophole. It does not produce artwork; it removes rows and columns
whose alpha is zero and reports the origin compensation each affected call site
needs. No visible pixel changes, and the arithmetic is reversible. Everything
else in `paperscrape_assets` is read-only with respect to the runtime directory;
the generators that write artwork there are listed under *Layout*.

## Setup

```bash
python3 -m venv ~/.venvs/paperscrape-assets    # once; any environment holding exactly these pins will do
. ~/.venvs/paperscrape-assets/bin/activate
cd tools/assets
pip install -r requirements.txt
python3 -m paperscrape_assets probe  # must report matches_expected: true
```

There is no installed `paperscrape-assets` command: every command below is
`python3 -m paperscrape_assets <command>`, run from `tools/assets` in that environment
(`paperscrape-assets` is only the name its `--help` and the reports it writes print).

Run `probe` first, every time. It renders a fixed document and hashes the result
against the value pinned in `raster.py`. If it does not match, the rasteriser has
changed and **every fidelity figure under `reports/` was measured with a
different tool** — re-measure rather than trust them.

## Commands

| Command | What it does |
|---|---|
| `probe` | Fingerprints the toolchain against the pinned expectation |
| `inventory` | Measures the shipped PNGs into `reports/runtime-inventory.{json,md}` |
| `validate` | Checks `sources/sprites.json` against what actually ships, and against the Kotlin sources |
| `normalize` | Reports any sprite still carrying removable transparent padding; `--apply-trailing` crops the right and bottom, which needs no origin compensation; `--apply` crops all four sides, updates the registry and the SVG sources, and prints the origin compensations that must be applied in the same change |
| `fit <name>…` | Recovers rectangular geometry from a shipped PNG; `--emit` writes the SVG |
| `render` | Renders every SVG source into `staging/` |
| `compare` | Measures `staging/` against the shipped PNGs into `reports/` |
| `all` | probe, inventory, validate, normalize, render, compare |

One generator sits beside the module rather than inside it, on the pattern
`buildings/build_neighbourhood.py` set:

```bash
python3 build_occluder_table.py    # writes engine/SpriteOccluderTable.kt from the shipped PNGs
```

It measures the crowns the shop-front pass treats as occluders and writes the Kotlin table both
the pass and its test read, so neither holds a hand-typed rectangle. It refuses to write if a
declared drawing is not blitted at the origin it is declared at, if a drawing *is* blitted there
and is not declared, or if the parasol's rasterised fan disagrees with the exact pi/4 its geometry
gives. `SpriteOccluderTableFreshnessTest` (a JVM unit test, so CI runs it) re-measures every figure
it writes.

```bash
python3 -m paperscrape_assets all
python3 -m unittest discover -s tests
```

## Layout

```
requirements.txt          exact pins; ranges would let antialiasing drift
paperscrape_assets/
  raster.py               rasterisation and the toolchain probe
  callsites.py            resolves sprite blit call sites in the Kotlin sources
  normalize.py            the padding and grid normalisation rule and its plan
  inventory.py            measurement of the shipped PNGs (read-only)
  registry.py             source specification schema and validation
  fit.py                  geometry recovery by measurement
  fidelity.py             comparison metrics and verdicts
  report.py               JSON, markdown and the visual comparison sheet
  cli.py                  command line entry point
sources/sprites.json      one entry per shipped sprite
sources/svg/              SVG sources
staging/                  rendered output (gitignored; never the runtime directory)
reports/                  measurements, committed as evidence
tests/                    tests that the fidelity criterion can fail
build_occluder_table.py   writes engine/SpriteOccluderTable.kt (above)
buildings/                the neighbourhood generator and its budget report
concepts/                 the drawing scripts of the people, the lake and the sky, beside their proposals
```

The generators that write artwork into `res/drawable-nodpi`:

- **The buildings.** From `tools/assets`, `python3 -m buildings.build_neighbourhood --res
  --registry --budget` draws every piece of the six building families into `buildings/out/`
  (gitignored); `--res` copies them into the runtime directory and writes
  `engine/NeighbourhoodTable.kt`, `--registry` rewrites their entries in `sources/sprites.json`,
  and `--budget` writes `buildings/budget.json` and `budget.md`. `validate` checks the budget
  against the shipped PNGs, so neither file is edited by hand.
- **The people.** Drawn by `concepts/people/build_people_concepts.py` and written by two scripts in
  `tools/`, run from the repository root in this order: `tools/generate_people_layers.py` writes
  every figure as fixed art plus up to four region masks, and `engine/PeopleLayerTable.kt`;
  `tools/update_people_registry.py` then rewrites the person entries of `sources/sprites.json`.
  `concepts/people/README.md` has the rest.

`concepts/people/`, `concepts/skywater/` and `concepts/rainbird/` hold the scripts that drew the
people; the dolphin, the sailboat and the cloud; and the bird, the umbrella and the wave, each
beside the proposals it drew. An SVG in `sources/svg/` that one of them drew names it in its
opening comment. The sun-and-moon proposals that stood in `concepts/a/`, `b/` and `c/` were
removed in v5.10 and remain in the repository's git history.

## The registry covers every sprite, and says where each one comes from

`sources/sprites.json` has an entry for **every** shipped PNG — that is the rule, and
`tests/test_registry_coverage.py` enforces it. The three counts that go with it (entries, entries
with an SVG source, declared gaps) are printed by `python3 -m paperscrape_assets validate`; they
are not written here.

An entry's `source` says how to get its file back. A sprite rendered from a committed
SVG names it:

```json
{ "kind": "svg", "file": "tree_canopy.svg" }
```

The other entries carry `source.kind = "none"`, which `validate` counts as gaps,
though nothing is lost: a generator writes each of them, and `source.reason` names
it. They are the people's layer files, which `tools/generate_people_layers.py`
writes as a fixed layer and up to four region weight masks per shape, and the
pieces of the six building families, which `buildings/build_neighbourhood.py`
writes as a fixed layer and at most a wall and a glass mask — each decomposed from
the generator's own drawing of the shape. They have no SVG of their own because
`render` regenerates a sprite from an SVG, and a mask is a *view* of a drawing, not
a drawing: authoring one per region per shape would be exactly the duplication the
generators exist to remove. A person's `notes` say which region the file carries,
and a piece's suffix does (`_fx` fixed, `_mw` wall, `_mg` glass), so "how do I get
this file back" has an answer for every entry in the registry, which is what the
field is for.

`validate` fails if a shipped PNG has no entry, if an entry has no PNG, if
declared dimensions or `contentBox` disagree with the file, if a declared anchor
is not what its rule derives, if a referenced SVG is missing, if `usage`,
`scale`, `tint` or a determined anchor disagree with what the Kotlin sources
actually do, or if a committed report (`reports/runtime-inventory.json`,
`reports/fidelity.json`, `buildings/budget.json` and `budget.md`) no longer
describes the shipped PNGs.

It also fails on the two things a per-sprite check cannot see. A **variant group**
declared `DISTINCT` whose members are pixel-identical has lost the distinction it
names, and every per-sprite rule passes, because two copies of one picture
satisfy all of them. A group declared
`IDENTICAL_GAP` whose members have started to **differ** has gained artwork the
declaration has not caught up with, which is what makes a gap close itself instead
of being forgotten. And any pixel-identical pair that **no** group declares fails
outright: it is one drawing under two names, which is two decodes, two atlas
entries, and two files that can be edited apart in one place only.

## The manifest, and what it can and cannot check

Schema 4 declares `contentBox`, `anchorRule`, `anchor` and `season` for every
sprite, and the variant groups in a top-level `variants` array. `contentBox` is
re-measured on every run, so it cannot drift away from the PNG it describes.

**Every variant group is `DISTINCT`** (`validate`'s `variants:` line counts them). There is no
`IDENTICAL_GAP` group, and no pixel-identical pair anywhere in the shipped
set.

The point of comparing it to the Kotlin sources: a sprite's pixel
size, its scale convention and its origin are correct only together, and nothing in a
PNG records the convention, so the registry declares it and `validate` checks the
declaration against the code.

`callsites.py` resolves a blit call site syntactically — no dataflow analysis,
because a resolver that guesses is worse than one that admits it cannot see. A
sprite chosen from a lookup table, or an origin computed from the drawn object's
own dimensions, resolves to nothing and is reported as **unresolved**. An
unresolved item is never counted as agreement, and `validate` prints the coverage
on success rather than only on failure, one figure per check, on its lines:
`registry OK:` (every entry's `contentBox` checked against its PNG), `anchors:`,
`normalisation:`, `variants:` and `call-site check:` (the sprites whose scale and
tint, and whose origin, were compared with the code). The figures are read there,
not copied here.

## Anchors are declared, not inferred

An anchor cannot be read off the code: the only evidence is the origin a call site
blits the sprite at, and that origin is `placement - anchor` — one equation, two
unknowns. It collapses to the anchor alone only when the sprite is an object in
its own right; for a part of a composite the origin is a composition placement
carrying no anchor at all.

The V2 library declares the anchor at authoring time instead, and the generators
declare it for what they write, so every sprite carries one (`validate`'s
`anchors:` line), under four rules:

| Rule | Meaning |
|---|---|
| `CONTENT_BOTTOM_CENTRE` | Ground-anchored wholes, and every person |
| `SPRITE_CENTRE` | Sun, moon, star, firework |
| `DECLARED_ATTACHMENT` | The palm: its three crowns at (84,78), its trunk at its foot (24,174) |
| `PART_LOCAL` | Parts whose offset the composite owns; origin (0,0) |

## Two declarations the registry does not take from the manifest

The V2 manifest is the source of truth for the artwork, not for what the code
does with it, and it disagreed with the call sites twice. It declared
`star_sparkle` `CANVAS_PIXELS`, which would draw the 180px sparkle three times too
large; the registry keeps `SCENE_UNITS`, because a *convention* is a fact about the
call site and `PaperRenderer.drawStars` is where it lives (the entry's `notes` say
so). It declared `santa_sleigh_scene` `SCENE_UNITS` where the call site then said
`CANVAS_PIXELS`, and there the manifest was right — the sprite had been redrawn on
the authoring grid — so the call site was changed to agree with it.

The rule the two cases share: **a scale convention is only ever correct together
with the PNG and the origin**, so when they disagree the answer comes from
whichever of the two was actually re-derived, never from whichever is easier to
edit.

## `fit`, and why it fits only rectangles

`fit` sweeps a rounded rectangle's corner radius against a shipped PNG's alpha
channel, keeps the value that minimises the error and reports it next to the
nearest multiple of `SPRITE_PIXELS_PER_UNIT`, in `reports/geometry-fit.json`;
`--emit` writes the result as an SVG source. The sprites recorded in that report
no longer ship.

Only rectangles and rounded rectangles are implemented, and that is the point
rather than a shortcoming. Those are determined by their canvas: one free
parameter, swept exhaustively, nothing left to choose. A canopy of overlapping
lobes has a free lobe count, free radii, free placement and a jitter seed —
"the fit that scored best" would be an invention presented as a recovery. Those
sprites are recorded as gaps.

## Verdicts

| Verdict | Meaning |
|---|---|
| `PIXEL_IDENTICAL` | All four channels match everywhere |
| `EDGE_EQUIVALENT` | Same shape and exact fill; the whole difference sits on the reference's own antialiased edge |
| `DIVERGENT` | The geometry was not recovered |

`alpha_iou` is reported but does **not** gate. An antialiased boundary is a fixed
share of a shape's *perimeter* while IoU divides by its *area*, so one absolute
threshold demands far more precision from a 60×12 sprite than from a 270×450 one.
See `fidelity.py` for the three conditions that do gate.

Run the tests before trusting a verdict. They pin the near misses in both
directions — a one-pixel displacement, a radius one grid unit off, a fill colour
off by one — because a criterion that cannot fail asserts nothing.

### What the pinned rasteriser does and does not reproduce

Every sprite with a committed SVG source compares `PIXEL_IDENTICAL` in the
committed `reports/fidelity.json`, with a largest alpha difference of 0.
`ShippedAgainstSourceTest` (`tests/test_fidelity.py`) keeps a looser bound across
the set as the guard: no pixel that is solid in one rendering and empty in the
other, so **no sprite's shape differs from its source**, and no single pixel's
coverage moving by as much as half.

Layered paper-cutout artwork is the case to know: where two opaque shapes meet,
the antialiased band lives in RGB at full alpha rather than in the alpha channel,
outside the band the edge conditions allow, so a rasteriser that resolves it
differently gets `DIVERGENT`. Read that verdict with the shape bounds above in mind.

## Padding and grid normalisation

A sprite's **normalised content box** is the union of the measured alpha bounding
boxes of its co-registered group, rounded outward to a multiple of
`SPRITE_PIXELS_PER_UNIT` — for every sprite, whichever scale convention positions
it. `normalize --apply` crops each sprite to that box, updates its `width`,
`height`, `contentBox` and derived `anchor` in the registry, rewrites the SVG
source's `viewBox` to match, and prints the origin compensation every affected
call site needs.

**Apply the compensations in the same change.** `SpriteBlitter` places the
bitmap's own pixel (0,0) at the caller's origin, so a crop without its
compensation moves the sprite by exactly the amount that was cropped. The tool
cannot make the Kotlin edit for you; it can only tell you the number, and
`validate` catches the omission only for the sprites whose anchor predicts an
origin.

`--apply-trailing` is the half that needs no compensation: it crops only the right
and the bottom, so pixel (0,0) and every drawn pixel keep their coordinates. It
leaves out the `SPRITE_CENTRE` sprites, which are placed by the centre of their
canvas, so any crop moves them.

Three parts of the rule look like details and are not:

- **Rounded outward, not to the measured box.** The compensation is
  `trim / unit`, and the blitter multiplies the origin by the same unit again at
  draw time. A trim of 17 px would give 5.667 units, which returns as 17.000002 —
  a sub-pixel origin, resampled because the blit paint carries
  `FILTER_BITMAP_FLAG`. Outward rounding keeps the compensation an exact integer,
  and a side that is trimmed keeps at least one transparent pixel, because that is
  the neighbour the bilinear filter reads at the edge: one to three pixels stay
  behind. That residue is deliberate.
- **Rounded to the sprite grid even for a raw-pixel sprite.** `unit` governs the
  compensation, not the grid: a `CANVAS_PIXELS` sprite writes its origin in
  pixels, but `SpriteGeometryTest` still requires its canvas to be a whole
  multiple of `SPRITE_PIXELS_PER_UNIT`. Rounding `bird_body` to its own pixel
  produced 88x21, off the grid on both axes.
- **The union covers a group, not a sprite.** Sprites chosen from a lookup table
  at draw time share one origin literal, so they must share one crop. Cropping
  each walk frame to its own box would need an origin per frame where there is one
  for all of them, and the frames would jitter horizontally against each other.
  Sprites that merely share an origin *value* — two call sites that happen to pass
  the same number — are not a group and each take their own crop.

`EXCLUSIONS` in `normalize.py` lists the sprites left alone, each with its reason.
An empty list there would be a claim that every sprite can be normalised, which is
not true: the canvas-anchored sky sprites are placed by the centre of their bitmap,
and the sun, the four moon phases and the carved Halloween moon share one origin
constant that would have to be split per sprite before any of them could be cropped.

`normalize` runs in check form as part of `all`. Gradle never invokes this tooling,
so `SpriteGeometryTest` on the Kotlin side repeats the part of the invariant that has
to hold in the APK — every canvas on the grid, and the whole set inside its decoded
byte budget — where CI will actually run it.

## Proposal names

The concepts under `concepts/` and the drawings they became carry the names they were chosen
under. Until v5.9 those names were Italian, and older commits use them; the tooling and the
code's comments use the English ones. The same drawing, under both names:

| English (since v5.9) | Before v5.9 | What it is |
|---|---|---|
| Broad Oak | «Quercia larga» | the tree, v4.21 |
| Scissors | «Forbici» | the sun, moon and star sprites, concept B, v4.23 |
| Relief | «Rilievo» | the people (v4.25) and the boats and dolphins (v4.26), concept B |
| Mirror | «Specchio» | the lake as a mirror of the sky, S1, v4.26 |
| Puff | «Batuffolo» | the cloud, C1, v4.26 |
| Dove | «Colomba» | the bird of v4.26, concept A |
| Swallow | «Rondine» | the bird since v4.28, B1 |
| Tube | «Tubo» | the storm wave, WA3, v4.28 |
| Coconut | «Cocco» | the palm, concept A, v5.1 |
| Cut-out | «Ritaglio» | the vehicles, D1, v5.6 |
| stencil, relief, doll, relief_eyes | `stampino`, `rilievo`, `bambola`, `rilievo_occhi` | the people concepts' folders and styles |
| silhouette, relief, openwork | `sagoma`, `rilievo`, `agiorno` | the sky-and-water concepts' folders and styles |

Two sets of the old names are still read by code, on purpose: the people's busts and the round-2
birds seed their wobble with the old style names, and the building groups `k2_t_gradini*`,
`k2_r_padiglione`, `k2_b_insegna`, `k2_b_smusso` and `k2_s_orologio_c` seed every card in their
group. Renaming them would redraw what they seed (shipped sprites, for the busts and the
buildings), so the generators keep the strings and say why beside them
(`build_people_concepts.py`, `build_skywater_round2.py`, `buildings/names.py`).
