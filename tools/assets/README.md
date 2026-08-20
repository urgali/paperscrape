# Asset source pipeline

Offline developer tooling. **Gradle never runs any of this**, and the app has no
dependency on it. It exists to fix one structural problem: until now a sprite's
only source was the sprite itself, so no sprite could be re-derived, re-scaled or
corrected except by editing pixels.

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
else here is still read-only with respect to the runtime directory.

## Setup

```bash
cd tools/assets
pip install -r requirements.txt      # add --break-system-packages on Debian/Ubuntu
python3 -m paperscrape_assets probe  # must report matches_expected: true
```

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
sources/sprites.json      one entry per shipped sprite, every one with a source
sources/svg/              SVG sources
staging/                  rendered output (gitignored; never the runtime directory)
reports/                  measurements, committed as evidence
tests/                    tests that the fidelity criterion can fail
```

## The registry covers every sprite, and every sprite now has a source

`sources/sprites.json` has an entry for all 111 shipped PNGs, and every one of
them names an SVG. That is new. The registry was built when 22 of 108 sprites
could be regenerated and the other 86 were declared gaps — entries whose
`source.kind` was `"none"` with a stated reason — because the original generators
were lost and geometry could only be recovered by measurement for the sprites
that were made of measurable primitives.

The V2 asset library replaced the artwork wholesale and shipped its own sources,
so the gap set is empty:

```json
{ "kind": "svg", "file": "house_shared_window.svg" }
```

**This closes blocker B1.** Group 4 could not start while the sprites it needs —
people, vehicles, buildings, decorations — were among the gaps, because
re-anchoring a sprite means being able to regenerate it with normalised padding
and declared metadata. All of them have a source now.

Schema 4 records what the V2 library declares per sprite: `anchorRule` and
`anchor` for **every** sprite rather than the 17 an origin could be solved for,
plus `season`. The anchors are no longer inferred from call-site origins — see
below.

`validate` fails if a shipped PNG has no entry, if an entry has no PNG, if
declared dimensions or `contentBox` disagree with the file, if a declared anchor
is not what its rule derives, if a referenced SVG is missing, or if `usage`,
`scale`, `tint` or a determined anchor disagree with what the Kotlin sources
actually do.

It also fails on the two things a per-sprite check cannot see. A **variant group**
declared `DISTINCT` whose members are byte-identical has lost the distinction it
names — the seasonal outfits shipped that way in v73, and every per-sprite rule
passed, because two copies of one picture satisfy all of them. A group declared
`IDENTICAL_GAP` whose members have started to **differ** has gained artwork the
declaration has not caught up with, which is what makes a gap close itself instead
of being forgotten. And any byte-identical pair that **no** group declares fails
outright: it is one drawing under two names, which is two decodes, two atlas
entries, and two files that can be edited apart in one place only.

## The manifest, and what it can and cannot check

Schema 4 declares `anchorRule`, `anchor` and `season` for every sprite and drops
`anchorReason`, which existed only to explain an absent anchor. Schema 3 added
the top-level `variants` array; schema 2 added `contentBox`, `anchorRule` and
`anchor`. `contentBox` is re-derived on every run, so it cannot drift away from
the PNG it describes.

**All 18 variant groups are `DISTINCT` as of schema 4.** Six were `IDENTICAL_GAP`
— the seasonal heads for window occupants and car drivers, whose winter artwork
had never been drawn — and the V2 library drew them. There is no
`IDENTICAL_GAP` group left, and no byte-identical pair anywhere in the shipped
set.

Nothing at runtime reads any of this. The manifest is developer tooling; the app
is unaffected by it.

The point of comparing it to the Kotlin sources is defect D-1: a sprite's pixel
size, its scale convention and its origin are correct only together, nothing in a
PNG records the convention, so the registry declared it and nothing checked the
declaration against the code.

`callsites.py` resolves a blit call site syntactically — no dataflow analysis,
because a resolver that guesses is worse than one that admits it cannot see. A
sprite chosen from a lookup table, or an origin computed from the drawn object's
own dimensions, resolves to nothing and is reported as **unresolved**. An
unresolved item is never counted as agreement, and `validate` prints the coverage
on success rather than only on failure:

| Check | Sprites reached |
|---|---|
| `contentBox` against the PNG | 111 |
| `scale` and `tint` against the code | 10 |
| origin against the declared anchor | 4 |
| variant group against the shipped bytes | 18 groups, 36 sprites |

The `scale`/`tint` and origin figures are far lower than they should be, and that
is **defect D-4**, not a property of the manifest: the call-site resolver
recognises a blit wrapper only when its first parameter is typed `Canvas`, and the
GPU migration changed `SceneObjectRenderer`'s two wrappers to take `SceneCanvas`,
so that file's ~60 call sites stopped resolving. See `ROADMAP.md`.

## Anchors are declared, not inferred

The registry used to record an anchor for 17 of 108 sprites and `UNDETERMINED`
for the rest, and the reason was structural rather than lazy: the only evidence
available was the origin a call site blits the sprite at, and that origin is
`placement - anchor` — one equation, two unknowns. It collapses to the anchor
alone only when the sprite is an object in its own right. For a part of a
composite the origin is a composition placement carrying no anchor at all, which
is why `house_shared_window` is drawn at five different origins.

The V2 library declares the anchor at authoring time instead, so all 111 now
carry one, under four rules:

| Rule | Meaning |
|---|---|
| `CONTENT_BOTTOM_CENTRE` | Ground-anchored wholes, and every person |
| `SPRITE_CENTRE` | Sun, moon, star, firework |
| `DECLARED_ATTACHMENT` | The palm frond fan, at (60,102) |
| `PART_LOCAL` | Parts whose offset the composite owns; origin (0,0) |

`PART_LOCAL` is the honest successor to `UNDETERMINED`: it says the same thing —
this sprite's placement belongs to whatever composes it — but as a positive
declaration rather than an absence.

## Two declarations the registry does not take from the manifest

The V2 manifest is the source of truth for the artwork, not for what the code
does with it, and it disagreed with the call sites twice. Both are recorded in
the affected entry's `notes`.

`star_sparkle` is declared `CANVAS_PIXELS` there. That is defect D-1 written
down: read as raw pixels, the 180px sparkle covers 180 local units against a
star's own 32, which is the three-times-too-large rendering v73.7 fixed. The
registry keeps `SCENE_UNITS`, because a *convention* is a fact about the call
site and `PaperRenderer.drawStars` is where it lives.

`santa_sleigh_scene` is the mirror case: the manifest says `SCENE_UNITS` and the
shipped call site said `CANVAS_PIXELS`, and there the manifest was right — the
sprite was redrawn on the authoring grid. The call site was changed to agree with
it rather than the entry being bent to agree with the call site.

The rule the two cases share: **a scale convention is only ever correct together
with the PNG and the origin**, so when they disagree the answer comes from
whichever of the two was actually re-derived, never from whichever is easier to
edit.

## Why the geometry is fitted rather than drawn

The SVG sources carry corner radii of 6, 9 and 12. Those are not eyeballed:
`fit` sweeps the radius against the shipped PNG's alpha channel and keeps the
value that minimises the error, then reports it next to the nearest multiple of
`SPRITE_PIXELS_PER_UNIT`. Every radius it recovered landed on a multiple of 3 —
two, three and four on-screen units — which is independent evidence that the lost
generator worked on the same grid the project documents.

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

## Padding, and the origin that has to move with it

**`SpriteBlitter` puts the bitmap's own pixel (0,0) on the origin its call site passes.**
Cropping transparent rows off the left or the top of a sprite therefore changes what that
pixel is, and the drawing lands somewhere else unless the origin moves by exactly the
trim. That is why `--apply` prints a compensation for every target and why it is not a
standalone asset change: the crop and the origin are one edit, and defect D-10 stayed open
for as long as it did because it was recorded as the former.

Cropping the **right and bottom** is a different matter. Pixel (0,0) does not move, every
drawn pixel keeps its coordinates, and nothing outside `GlTextureAtlas` and
`CanvasSceneTarget` reads a sprite's dimensions at all. `--apply-trailing` is that half,
and it needs nothing from the renderer.

Both round the retained box **outward to `SPRITE_PIXELS_PER_UNIT`, for every sprite**.
That is the grid `SpriteGeometryTest` requires of the whole shipped set regardless of
scale convention; only the compensation follows the convention, one unit per pixel for
`CANVAS_PIXELS` and one per three for `SCENE_UNITS`. Rounding outward leaves up to two
pixels of padding, and that padding is load-bearing for alignment.

Both also rewrite the SVG source's `viewBox` — its origin to the crop's top-left corner
and its extent to the crop's size — so the source keeps describing the PNG that ships.
Nothing inside the document moves.

`EXCLUSIONS` in `normalize.py` lists the sprites this rule deliberately leaves alone, each
with its reason. It is not a backlog: a `SPRITE_CENTRE` sprite is placed by the centre of
its canvas, so cropping it moves its anchor even though no drawn pixel moves, and the sun,
the four moon phases and `moon_jack_o_lantern` share one origin constant that would have to
be split per sprite first.

### What the pinned rasteriser does and does not reproduce

The shipped PNGs came from the V2 library's own rasteriser, and the pinned one
resolves partially covered pixels differently. `ShippedAgainstSourceTest` bounds
that difference instead of describing it: across all 118 sprites there is no
pixel that is solid in one rendering and empty in the other, so **no sprite's
shape differs from its source**, and no single pixel's coverage moves by as much
as half (worst case 121/255, one pixel on `rainbow_arc`'s shallowest stroke
edge). Everything the two rasterisers disagree about is therefore the resolution
of a boundary pixel. That was defect D-7, and it is closed.

Most sprites still report `DIVERGENT` against their sources. The V2 library is
layered paper-cutout artwork, so where two opaque shapes meet, the antialiased
band lives in RGB at full alpha rather than in the alpha channel, and the three
gating conditions only look at alpha. The verdict counts should be read with that
in mind; the shape bounds above are what the closure of D-7 rests on.

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
origin. D-10 did exactly this in v2.2: 34 targets cropped, 34 origins moved, and
every sprite's ink hashed before and after to prove it landed where it started.

Two parts of the rule look like details and are not:

- **Rounded outward, not to the measured box.** The compensation is
  `trim / unit`, and the blitter multiplies the origin by the same unit again at
  draw time. A trim of 17 px would give 5.667 units, which returns as 17.000002 —
  a sub-pixel origin, resampled because the blit paint carries
  `FILTER_BITMAP_FLAG`. Outward rounding keeps the compensation an exact integer
  and leaves up to two pixels behind. That residue is deliberate.
- **Rounded to the sprite grid even for a raw-pixel sprite.** `unit` governs the
  compensation, not the grid: a `CANVAS_PIXELS` sprite writes its origin in
  pixels, but `SpriteGeometryTest` still requires its canvas to be a whole
  multiple of `SPRITE_PIXELS_PER_UNIT`. Rounding `bird_body` to its own pixel
  produced 88x21, off the grid on both axes.
- **The union covers a group, not a sprite.** Sprites chosen from a lookup table
  at draw time share one origin literal, so they must share one crop. Cropping
  each walk frame to its own box would need 32 origins that do not exist, and the
  frames would jitter horizontally against each other. Sprites that merely share
  an origin *value* — two call sites that happen to pass the same number — are not
  a group and each take their own crop.

`EXCLUSIONS` in `normalize.py` lists the sprites left alone, each with its reason.
An empty list there would be a claim that every sprite can be normalised, which is
not true: the canvas-anchored sky sprites are placed by the centre of their bitmap,
and the sun and the four moon phases share one origin constant that would have to be
split per sprite before any of them could be cropped.

`normalize` runs in check form as part of `all`. Gradle never invokes this tooling,
so `SpriteGeometryTest` on the Kotlin side repeats the part of the invariant that has
to hold in the APK — every canvas on the grid, and the whole set inside its decoded
byte budget — where CI will actually run it.
