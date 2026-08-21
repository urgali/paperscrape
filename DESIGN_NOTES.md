# DESIGN_NOTES.md

PaperScrape's visual design system. This is the reference for every artistic,
asset and UI decision.

Sections marked **[OBSERVED]** describe rules derived by reading the current
code and measuring the current assets — they document what the project actually
does today. Sections marked **[TO BE ESTABLISHED]** are gaps: no rule exists
yet, and one must be agreed before the related work proceeds. Nothing in this
document is invented to fill a heading.

Last fully verified against **v74**. Sections rewritten since — §5 proportions,
perspective and scaling, §6 positioning and alignment, and the snow, decoration and
density rules — are current as of **v1.0 Stable**.

---

## 1. Artistic direction

### Visual identity **[OBSERVED]**

A layered paper-cutout landscape. Flat, bold shapes read as pieces of coloured
paper laid on top of one another. Depth comes from stacking, scale and parallax
rather than from perspective drawing or rendered lighting.

### Mood and atmosphere **[OBSERVED]**

Calm and ambient. Nothing moves quickly; nothing demands attention. The scene
is meant to be glanced at, not watched. Motion is gentle drift, sway and bob.

### Level of detail **[OBSERVED]**

Deliberately low. The project has twice added detail (v62) and then removed it
(v63) because the extra detail read as fussy rather than richer. The
established position is: **an object should be identifiable by silhouette
alone**. A shop is recognisable by its hanging sign, a fire truck by its ladder
— not by texture or fine linework.

### Principles that must be preserved

1. Flat colour fills. No rendered gradients on objects, no photographic
   texture.
2. Silhouette clarity over internal detail.
3. Ambient pace. No fast or attention-grabbing motion.
4. Everything the user can recolour must remain legible at every colour they
   can choose.
5. Full-scene overlay effects are off the table. A paper-grain overlay was
   implemented and removed twice (v56 scaled down, v58 deleted) for CPU cost
   and colour-fidelity reasons. Per-object baked shading replaced it.

---

## 2. Graphic style

### Visual language **[OBSERVED]**

- **Silhouettes**: simple, closed, bold. Few concavities.
- **Borders**: mostly absent. Objects are separated by colour contrast and by
  ground shadows, not by outlines. A thin translucent stroke
  (`0x33000000`, 2.5 px) exists on some objects; tree canopies deliberately
  have none.
- **Shadows**: a single soft translucent ellipse (`0x2E000000`) at an object's
  base. Its purpose is anchoring — "this object is standing on the ground" —
  not lighting.
- **Gradients**: used only for the sky (`LinearGradient`), the sun glow
  (`RadialGradient`) and a subtle hill highlight. Never on scene objects.
- **Transparency**: used for glow, shadow, fades and precipitation alpha.
  Objects themselves are opaque.
- **Baked shading**: sprites carry low-strength darker mottling that survives
  the runtime `MULTIPLY` tint as gentle shading. Kept per-object and
  low-strength, never scene-wide.

### Complexity limits **[OBSERVED]**

A scene object is assembled from a small number of separate sprites — typically
4 to 7 (e.g. house: wall, roof, trim, window, door, chimney, planter). Adding
parts beyond that has historically made things look worse, not better.

---

## 3. Tintable vs. fixed-art **[OBSERVED]**

This is the single most useful rule the project has established for asset
authoring.

| Class | Authoring | Runtime | Examples |
|---|---|---|---|
| **Tintable** | Greyscale mask, lightest value `#ffffff`, baked mottling never darker than 14 % | `MULTIPLY` tint at the blit | houses, buildings, tree canopies, plain cars, clouds, moons, birds, snowmen, gifts, eggs, pumpkins, penguin body and belly |
| **Fixed-art** | Final paper colours in the source | Blitted as authored, no colour filter | palm trunk and fronds, sun and its glow, star sparkle, taxi, police car, fire truck, dolphin, sailboat, the sleigh, every person, character accents, the rainbow, the bolt, the firework |

**No sprite mixes the two classes, and the PNG is now the evidence.** This used to
be a statement about intent rather than bytes: fixed-art meant "the colour is the
artist's, not the user's", and it did *not* guarantee the PNG contained a colour.
That gap produced a whole family of defects. `dolphin_body`, `sailboat_hull` and
`sailboat_sail` were classified fixed-art, shipped as pure-white masks and blitted
untinted, so they drew as white silhouettes on the lake until v74.1 patched them
with a tint constant; `balloon_basket` shipped the same way and was still open as
D-6; four more sprites shared the profile.

The V2 asset library closes it from the artwork's side. Every tintable sprite is a
pure greyscale mask and every fixed-art sprite carries real colour, verified across
all 111. So the two classes are now decidable by measuring a PNG, and
`SpriteTintClassTest` measures them — in **both** directions, because the opposite
error is just as silent: multiplying finished art by a constant compounds two hues
instead of recolouring one, and a green frond times an autumn orange is mud.

**Consequences of the V2 classification, accepted deliberately.** Several sprites
that used to be masks the runtime coloured are now finished art, so the colour the
runtime supplied has nowhere to go:

| Sprite | What is lost | Where the control still acts |
|---|---|---|
| `sun_body`, `sun_glow` | The disc and the sunburst no longer follow **Sun Color** | The setting still drives the ambient radial glow, which is a tintable effect rather than a sprite |
| `star_sparkle` | `theme.starColor` no longer reaches the sparkle | Nowhere; the field stays on `SceneTheme` only because custom themes persist it |
| `palmtree_fronds` | Palms no longer respond to **Fall Colors**, and the tree colour does not reach the fronds | Winter still applies, because the frost is a separate sprite laid over the fronds rather than a tint |
| `skyscraper_wall` | Windows no longer light per building on a pseudo-random roll | The day and night states are both artwork now, crossfaded on `nightGlow` |
| `firework` | Bursts no longer vary in hue | The palette is in the sprite |

None of these is a defect to patch at the call site, and none should be recovered
by re-introducing a tint over the new art. If any of them reads wrong on a device,
the fix belongs in the artwork.

Rules:

1. Anything the user can recolour in Settings **must** be authored as tintable.
2. Anything whose identity depends on its colours (a taxi is not a taxi in
   green) **must** be fixed-art, and must not be exposed as recolourable.
3. `MULTIPLY` tinting means the rendered colour is a few percent darker than
   the exact hex the user picked, wherever baked shading sits. This trade-off
   was accepted in v66 to let shading survive tinting. **It has never been
   confirmed on a real device** — see §12, pending decision D4.

---

## 4. Asset system

### Current state **[OBSERVED]**

- **Runtime format: PNG only.** Location: `res/drawable-nodpi/`. `nodpi` is
  required: sprites are positioned and scaled by explicit `canvas.scale()`, so
  Android density scaling must not also apply.
- **Source format: SVG**, rasterised to PNG by a committed, version-pinned
  pipeline in `tools/assets/`. Conventions below. PNG remains the runtime format
  and always will: `VectorDrawable` re-rasterises on every draw, which is why
  scene objects are bitmaps.
- **24 of the 118 shipped sprites have a source. The other 94 do not.** The
  scripts that produced the original PNGs were never versioned and are lost; for
  most sprites the PNG is still its own source. Which sprites have recovered
  geometry, and why the rest cannot, is recorded per sprite in
  `tools/assets/sources/sprites.json`.

### Measured state of the current asset set

Measured off the shipped PNGs. The figures below describe the **V2 asset library**,
which replaced the whole runtime sprite set in v76; anything quoted from before
that release describes different artwork and is not comparable sprite for sprite.

| Metric | Value | Was (v75) |
|---|---|---|
| Files | **112** | 108 |
| Unique contents | **112** — no byte-identical pair | 102 of 108 |
| Decoded `ARGB_8888` | **14.63 MB** | 15.39 MB |
| Off the 3× authoring grid | **0** | 5 |
| Orphans (never referenced) | 4 (`house_window`, `road_asphalt`, `road_curb`, `road_line`) | 7 |
| Tintable masks / fixed art | **34 / 78** | not decidable from the PNGs |
| Sprites with an SVG source | **112** | 22 |
| Variant groups still an `IDENTICAL_GAP` | **0** of 18 | 6 of 18 |

Four things in that table were long-standing defects and are closed by the
library rather than by code:

- **Nothing is off the grid.** The two palm frond variants were the only off-grid
  sprites actually drawn, at 102×176, and their 176 px height is not a multiple of
  the oversample, so no crop could put them on it. V2 redraws the fan at 120×120
  with a declared attachment point, which retires the hand-tuned `-87.45` origin
  in `drawPalmTree` along with it.
- **There are no duplicates left.** Phase 3.4 removed ten files that were one
  drawing under two names; the six that remained were seasonal head variants whose
  winter artwork had never been drawn, recorded as a gap under decision D2. V2
  draws them — hat, scarf, hood, raised collar, cold cheeks.
- **Every sprite has a source.** 86 of 108 were declared gaps, which is what kept
  blocker B1 in place and Group 4 unstartable.
- **Padding is no longer waste.** It is declared: each sprite carries a
  `contentBox` and an anchor rule, and the margin around the content is what the
  anchor is measured against. The old rule — every sprite must reach its own canvas
  edges — would now fail 34 sprites for being drawn as designed, so
  `SpriteNormalisationTest` was replaced by `SpriteGeometryTest`, which asserts the
  grid and a total-memory ceiling instead.

**Four defects found on a device against v76 and fixed in v76.1**, all of them in
the artwork or in the number that places it:

- `moon_crescent` closed its lit limb with a terminator ellipse wide enough to
  bulge past the disc *and* past the 240 px canvas, so the crescent was clipped
  flat by its own right edge. `moon_gibbous` had the mirror error and drew a
  crescent instead of a gibbous, leaving its crater outside the lit shape.
- `tree_canopy_snowcap` was cut for a different crown: it sat 2 units below the
  new canopy's ridge and 5 short of each shoulder, so green showed above the snow
  and at both corners. Redrawn to repeat the crown's own upper vertices.
- The car-driver head was placed by centring its canvas, which is right for a
  60x60 sprite and wrong for a 171x162 one with a `CONTENT_BOTTOM_CENTRE` anchor:
  the bust landed a third of the way down the door. Now placed anchor-first.
- The fire truck shared `car_body`, so its silhouette was a sedan's. It has its
  own body sprite now.

Seven sprites are new, and each replaces something the renderer used to draw in
code, or a shape it had no drawing for: `house_window_lit` and `skyscraper_wall_lit` (night states that were a tint
ramp and a `drawRect` grid), `tree_trunk` (a flat rectangle), `rainbow_arc` (seven
stroked bands and seven highlights), `lightning_bolt` (nothing — the storm was a
white veil only), `firework` (eighteen expanding circles per burst) and
`firetruck_body` (a red-tinted sedan).

### Authoring conventions **[OBSERVED]**

- **Grid**: sprites in the "unit" convention are authored at
  `SPRITE_PIXELS_PER_UNIT = 3`× their on-screen size, for clean antialiased
  edges after the downscale. Dimensions should therefore be multiples of 3.
- **Second convention**: sun, moon and birds are authored at literal on-screen
  pixel size and blitted with `SpriteScale.CANVAS_PIXELS`. Passing the wrong
  convention renders the sprite 3× wrong, silently — the convention is a named
  argument on the shared blitter rather than a choice of helper function, but it
  is still chosen by the caller. This dual convention is a known defect, not a
  design choice; the fix is to declare the convention in each asset's own metadata.
  The sleigh left this group in v76: V2 redrew it on the authoring grid, so it is
  a `SCENE_UNITS` sprite scaled by its own call site.
- **A convention is a fact about the call site, not about the artwork.** The V2
  manifest and the shipped code disagreed twice and the disagreements resolved in
  opposite directions. `star_sparkle` is declared `CANVAS_PIXELS` in the manifest,
  which is defect D-1 restated — read as raw pixels the 180 px sparkle covers 180
  local units against a star's own 32 — so the call site won. `santa_sleigh_scene`
  is declared `SCENE_UNITS` where the call site said `CANVAS_PIXELS`, and there the
  manifest won, because the sprite genuinely was re-authored. The rule they share:
  size, convention and origin are only ever correct **together**, so when two of
  them disagree the answer comes from whichever was actually re-derived.
- **Naming**: `category_part[_variant].png`, lowercase, underscore-separated —
  `house_large_roof.png`, `palmtree_fronds_frost.png`,
  `person_woman_winter_walk2.png`. Follow this exactly.
- **Composite objects**: one PNG per part, composed at draw time by successive
  blits at hand-written local offsets.
- **Anchor**: a sprite's anchor is declared in `tools/assets/sources/sprites.json`
  as an `anchorRule` plus the point it derives, expressed in the sprite's own
  local units. All 111 sprites carry one as of the V2 library; four rules are in
  use:
  - `CONTENT_BOTTOM_CENTRE` — centred on the drawn content, sitting on its base.
    The ground-anchoring rule: an object meets the ground along the bottom edge of
    what is drawn, never of the transparent canvas around it. Every person uses it.
  - `SPRITE_CENTRE` — the sprite's centre, valid where the content is centred in
    the bitmap. Sun, moon, star, firework.
  - `DECLARED_ATTACHMENT` — a named point that is neither the centre nor the base,
    because the sprite joins another one there. Only the palm frond fan, at
    (60,102): the point where the blades converge onto the trunk.
  - `PART_LOCAL` — origin (0,0), for a part whose placement its composite owns.
    This is the positive form of what used to be `UNDETERMINED`: it says the same
    thing, that the sprite carries no anchor of its own, as a declaration rather
    than as an absence. 101 of 118 entries were `UNDETERMINED` before V2.

### Rules for any new or regenerated asset

1. **A committed source is mandatory.** No asset may ship whose only source is
   the PNG itself. The source is an SVG under `tools/assets/sources/svg/` with a
   registry entry beside it, and it must render to the shipped PNG's exact pixel
   dimensions.
2. Declared metadata is mandatory: nominal size, content bounding box, anchor
   point, scale convention, category, tintable or fixed-art.
3. Trim transparent padding, by the rule rather than by eye: crop to the union of
   the co-registered group's content boxes, rounded outward to the sprite's own
   grid, and compensate the call-site origin by `trim / unit` in the same change.
   The crop and the compensation are one edit; either alone moves the sprite.
   Padding that survives the rule is load-bearing — it holds a lookup group's
   members registered against each other, or keeps the compensation an exact
   integer — and must be documented as such rather than cropped away later.
4. Respect the 3× grid.
5. No two shipped assets may be byte-identical. Variants that are meant to
   differ must be asserted to differ by a test.
6. Before regenerating an asset to fix a size or alignment problem, determine
   whether the fault is the asset or the code that positions it.
7. Render the result and look at it before treating it as final. The
   comparison sheet exists for this; the metric table does not replace it.
8. **Never regenerate a shipped asset to improve a fidelity number.** The report
   measures the reconstruction against the asset, not the other way round. If a
   sprite will not reconstruct, that is a recorded gap, not a reason to change
   what ships.

### SVG source conventions **[OBSERVED]**

SVG is the source format for 2D sprite art. It is never a runtime format: the
pipeline is *SVG source → deterministic rasterisation → PNG*, and the app loads
only the PNG. `VectorDrawable` is not involved anywhere in the scene, so its
constraints do not apply to these files and must not be inherited by habit.

**Coordinates, viewBox and units.** One sprite is one SVG. The root carries
`width`, `height` and `viewBox="0 0 width height"` with the same numbers, so one
user unit is exactly one output pixel and the document needs no transform to be
read. Lengths are plain numbers with no unit suffix — `width="66"`, never `66px`
or `66pt` — because a suffix invites a DPI conversion, and a DPI conversion is a
second place where the output size could be decided. The pipeline never passes a
zoom, a DPI or an explicit size: the document's own dimensions are the only thing
that decides how large the PNG is, and `render` fails if the result does not
match the size declared in the registry.

**Grid.** A sprite in the `SCENE_UNITS` convention is authored at
`SPRITE_PIXELS_PER_UNIT = 3` pixels per on-screen unit, so its dimensions and its
significant geometry — corner radii, part offsets, stroke widths — are multiples
of 3. This is not decoration: every corner radius recovered from the existing
sprites landed on a multiple of 3 (6, 9 and 12, i.e. two, three and four units),
which is what a grid looks like from the outside. `CANVAS_PIXELS` sprites are
authored at literal on-screen size and are not on the 3× grid by construction.

**Scaling.** A source is authored once at its final pixel size. Rendering the
same document at a different size to obtain a variant is forbidden: it produces
two assets whose relationship exists only in whoever ran the command. A sprite
needed at a second size gets a second source, or the caller scales the canvas.

**Anchor.** A sprite's origin is its own pixel (0,0), which is what
`SpriteBlitter` places at the caller's coordinates. Where a sprite has a
meaningful attachment point that is *not* (0,0) — the palm crown at local
(17, 27.45), where the frond fan converges onto the trunk — that point belongs in
the source's comment header and in the registry's optional `anchor` field, in the
same coordinate space as the viewBox. It must never exist only as a literal in
Kotlin: that is how `-87.45` came to be a number nobody could re-derive.

**Colour handling.** A tintable sprite is authored as a pure white mask
(`#ffffff`), because the runtime tint is `PorterDuff.Mode.MULTIPLY` and white is
that operation's identity — the sprite's own value therefore does not bias the
colour the user picked. A fixed-art sprite carries its final colours. The two are
never mixed in one file: a sprite is wholly one or wholly the other, and which it
is is declared in the registry.

**Transparency.** Transparency comes only from the absence of geometry. No
background rectangle, no `opacity` on the root, no alpha in a fill used to fake
lighter paint — a lighter tone is a lighter colour, not a transparent dark one,
because alpha and `MULTIPLY` interact and the result is not what the author saw.
Partial alpha is legitimate only where it is the point: a glow, a soft shadow,
precipitation.

**No text.** Sources contain no `<text>` element. A sprite that depended on a
font would render differently on a machine with different fonts installed, which
would defeat the pipeline's only real purpose. Lettering, if ever needed, is
drawn as paths. The rasteriser is invoked with system fonts disabled so that this
is enforced rather than merely intended.

**Naming.** `category_part[_variant].svg`, lowercase, underscore-separated,
matching the PNG's resource name exactly: `house_large_roof.svg` produces
`house_large_roof.png`. The resource name is what `R.drawable.*` resolves to, so
renaming a source is an app change.

**Rasterisation and rasteriser versioning.** One rasteriser, pinned to an exact
version in `tools/assets/requirements.txt`, invoked with fixed options.
`resvg_py` was chosen over a cairo-based rasteriser specifically because it
carries its own scan converter rather than binding to a system graphics library:
output that varies with the host's libcairo would make "reproducible" mean
"similar on this machine", which is the property the pipeline exists to remove.
The pin is *verified, not declared*: `paperscrape-assets probe` renders a fixed
probe document and compares its hash against a recorded value. If the hash moves,
every fidelity figure previously recorded was measured with a different tool and
must be re-measured rather than trusted.

**Verification workflow.** Author or fit the source, `render` into `staging/`,
`compare` against the shipped PNG, and **look at the comparison sheet** — a
metric table is reading about an asset, not looking at it (section 14, and
`AI_PROJECT_RULES.md` 6.8). Staged output is never written into
`res/drawable-nodpi/`; the tool refuses an output path inside it. Replacing a
shipped sprite is a separate, approved step.

**When SVG, and when not.** Every new or regenerated sprite gets an SVG source.
An existing sprite gets one only when its geometry can actually be recovered by
measurement; where it cannot, the honest record is a declared gap in the registry
rather than a redraw described as a recovery.

---

## 5. Proportions, perspective, depth and scaling

### One source of truth **[OBSERVED]**

`SceneSpace` owns the ground plane, the perspective, the road, the pavement and
the size of every category. Nothing else may define any of them. Four stages,
each answering exactly one question:

```
finalScale = variantScale        // metres -> local units, from the size table
           x sizeVariation       // per-candidate jitter around 1.0
           x perspectiveScale(y) // how far away that ground point is
           x sceneScale(height)  // the viewport's own size
```

**No stage may compensate for another.** A sprite that draws too large is a
wrong entry in the size table, never a `canvas.scale` correction at the call
site. Two such corrections existed (`0.83` on the small house, `0.68` on the
large one) and both are gone.

### The projection **[OBSERVED]**

```
horizon            0.655 of screen height
object band        0.704 (hill's guaranteed-solid line) .. 0.790
pavement rows      0.795 (far) .. 0.807 (near)
road lanes         0.834 (far) .. 0.862 (near)
reference line     0.846 -- perspectiveScale is exactly 1 here
perspectiveScale(y) = (y - horizon) / (reference - horizon)
```

The vertical order is **buildings, pavement, road**. People walk on the strip of
ground between the village and the tarmac, not in front of the carriageway,
where they read as standing on the road and as having nothing to do with the
buildings behind them.

The reference line is the projection's **own** constant, not an alias for the
near lane. While it was the lane, moving the road one step down rescaled every
object in the scene, because the denominator above moved with it.

Apparent size is proportional to the distance below the horizon, which is what a
flat ground plane seen from a fixed viewpoint does. Everything that stands on
the ground reads the same function, so a pedestrian in front of the road is
automatically larger than a car on it by exactly the amount their ground lines
imply, and the far lane is automatically smaller and slower than the near one.

Depth range across the object band is **2.75x**, against 1.51x before Group 4.

### The size table **[OBSERVED]**

40 local units per metre at the reference line, on a 2400 px screen. Each
category declares the real height it should read as and the local-unit height
its own drawing occupies; its base scale is derived from the two.

| | read-as height | drawn units |
|---|---|---|
| Pumpkin | 0.5 m | 42 |
| Bunny | 0.55 m | 62 |
| Easter egg | 0.6 m | 40 |
| Gift | 0.95 m | 42 |
| Penguin | 1.1 m | 46 |
| Car | 1.45 m | 48 |
| Snowman | 1.7 m | 75 |
| Person (adult) | 1.9 m | 80 |
| Parasol | 2.9 m | 84 |
| Fire engine | 2.9 m | 68 |
| Bar | 4.8 m | 55 |
| Restaurant | 5.2 m | 60 |
| Small house | 5.8 m | 110 |
| Large house | 7.6 m | 145 |
| Palm | 8 m | 90.33 |
| Tree | 9.8 m | 122 |
| Tower | 17 m | 196 |
| Hot-air balloon | 20 m | 149 |

**These are read-as heights, not measurements.** They started from real-world
sizes and stay within sight of them, because a table anchored to something real
is the only kind that can be argued about. A few were tuned away from the
physical value in v76.6 after a device pass — a person to 1.9 m, a car down to
1.45, a tower down to 17, a tree up to 9.8, a gift up to 0.95, a parasol up to
2.9 — and each departure is recorded in `SceneSpace` beside the number. What must hold is the
hierarchy below; the individual figures serve it.

```
person < car < house < tree < larger building
road   ~ coherent with car size
boat   > dolphin
gift   > tiny accent, < person
```

**Height is the governed dimension; width follows the artwork.** The V2 sprites
are stylised -- a cottage is drawn narrower than a real one, a car shorter than
a real one -- so governing both is impossible without redrawing them, and
governing width instead makes a person shorter than a car. Heights are also what
the eye compares in an elevation where every object meets one ground line.

The table has to exist because the sprites are authored at incompatible internal
scales: measured on their own artwork they run from ~13 units per metre for a
shop front to ~46 for a person. A single global multiplier cannot correct that,
which is what the old `GLOBAL_OBJECT_SCALE = 2` plus per-category base scales
were trying and failing to do.

### Decoration placement comes from the foliage's own content box **[OBSERVED]**

A tree's Christmas lights are scattered across an ellipse the caller measures off
its own canopy sprite, as offsets in a **unit disc**. They were absolute offsets
around a hand-picked centre, and the lowest of them fell below the canopy's
content and hung over the trunk.

Two properties follow. Lights stay inside whatever the foliage is next redrawn
to, because the caller passes that sprite's half-extents rather than a constant.
And they are drawn *inside* each plant's sway transform, so they lean with the
branches instead of staying rigid while the leaves move around them — which also
removes the need to leave slack at the edges for the sway to swing into.

### Snowman readability on snow **[OBSERVED]**

A white snowman on white winter ground was separated from its background by
nothing but antialiasing. The fix is in the **asset**, not in a runtime effect: a
cool tonal rim inset into the silhouette, so the outer radii and therefore the
bounding box and every anchor measured against it are unchanged.

Not a cartoon outline, and not a neutral grey — snow in shadow reads cool, and
grey would look like a printing artefact beside the warm daylight palette. It is
a shadow at a fold, which is what the paper-cutout language already uses
everywhere else. It survives user tinting because the runtime tints with
`MULTIPLY`: the rim keeps its relation to the body under any colour, where a
`SRC_IN` tint would have flattened it away.

### Screen-size independence **[OBSERVED]**

Object sizes are expressed against a 2400 px reference height and multiplied by
`sceneScale(screenHeight)`. Before Group 4 they were absolute canvas pixels
while every ground line was a fraction of screen height, so the composition was
only correct on one device.

### Depth bands **[OBSERVED]**

The whole `0..1` range is available; the old cap at 0.375 existed only because
the road was drawn over anything lower, and the object band is now placed above
the road by construction, asserted in `SceneSpaceTest`.

| Category | Band |
|---|---|
| Buildings | 0.00 .. 0.80 -- tower below 0.30, restaurant/bar above it |
| Houses / parasols | 0.28 .. 0.48 (back), 0.62 .. 0.95 (front) |
| Trees | 0.18 .. 1.00 |
| Seasonal decorations | 0.45 .. 1.00 |
| Balloons | 0.05 .. 0.45 |

A building's style is chosen by **depth**, not by a position hash: a tower
belongs on the skyline and a shop front among the houses.

### A theme's identity lives in its defaults, not in the renderer **[OBSERVED]**

A built-in theme is a set of default values for the same categories every other
theme has. Nothing about a theme is special-cased in drawing code, and nothing
should be: "no umbrellas at Christmas" is `parasols.visible = false` in that
theme's defaults, not a branch in `drawParasol`.

Two consequences worth stating, because both were violated before v1.2.

**A season and a festival are two different statements.** `winterColorsEnabled`
says snow has settled and people have dressed for it; `christmasDecorationsEnabled`
says lights have gone up. They were one flag, which made a plain snowy January
impossible — every winter tree came with fairy lights — and made Christmas cost a
full winter presentation whether or not one was wanted. Neither implies the other,
and all four combinations are reachable. Anything Christmas added later attaches to
the Christmas flag, so the meaning of the winter flag never has to change again.

Santa and the presents keep their own switches rather than folding into the
Christmas flag: they already had them, and one thing with two controls that can
disagree is worse than two things with one each. A theme's defaults set all three
together; a user can still take any of them separately.

**A presentation flag with no default is a feature nobody sees.**
`winterColorsEnabled` drives tree snow caps, roof snow and winter clothing — three
of the things that make a winter scene — and defaulted to off for every theme,
including Winter, Christmas and Tundra. `fallColorsEnabled` did the same for
Autumn. The features worked; they were simply never switched on by anything.

**An inherited default is still a decision.** The Tundra lake set its colour,
height and visibility and inherited `sailboatsVisible`/`dolphinsVisible` from the
generic default, which is `true` — a yachting scene and a pod of dolphins in the
Arctic. When a theme overrides part of a config block, the fields it does not
name are choices it is making silently.

`BuiltInThemeCoherenceTest` pins the matrix, because every one of these is a
*default*: invisible until someone installs the app fresh, with no running build
that fails.

### Density controls never touch geometry **[OBSERVED]**

A density or visibility control decides **how many** members of a category
render. It must never decide the size, position or shape of anything.

The road broke this rule and it was reported from a device: its edges were
derived from the lane span of the *density-filtered* car list, so moving the
Cars slider resized the road, and at a low setting -- where only one lane
survived the thinning -- the span collapsed to zero and the strip with it. The
road is terrain; it is now built from the theme's whole car list, computed once,
and is identical at 0 %, 50 % and 100 %.

Audited alongside it: clouds, mountains, birds, precipitation, lake decorations
and stars all read density for presence or count only. Their bands, heights and
widths are independent of it. `lake.height` is a genuine geometry control and is
a separate slider from any density.

### Sky and lake are outside the ground projection **[OBSERVED]**

The lake sits at and slightly above the horizon, where `perspectiveScaleAt` is
at or near zero, so nothing on it can take its size from the ground plane -- it
would vanish. The water has its own metric (15 px per metre) whose only job is
to keep its inhabitants right relative to *each other*: a 2.6 m dolphin against
a 6.5 m sailboat.

Birds, the sleigh, fireworks and the celestial bodies are composed for
legibility rather than physical scale, and deliberately do not read this table.
A gull sized by the projection at the depth it flies would be a few pixels
across.

### Historical note

Group 4 exists because of this record, which is what a per-asset patch buys:

| Version | Symptom | Fix applied at the time |
|---|---|---|
| v67 | palm fronds detached from trunk | anchor constants recalculated by hand |
| v70 | mountains invisible; dolphins/sailboats hidden | geometry recalculated by hand |
| v73 | cars drifting outside the road strip | coordinates recalculated by hand |
| v73 | driver heads oversized | resized by hand |
| v73 | people rendering tiny | a global factor had simply been omitted |
| v76.4 | cars taller than their own half of the road | road edges rederived; cause deferred here |

**Rule: never fix a size or alignment problem with a per-asset constant when the
real fault is the shared perspective system.**

---

## 6. Positioning and alignment

### Anchoring **[OBSERVED]**

- Ground-anchored objects receive a `GroundGeometry` snapshot each frame
  (`shiftXWrapped`, `tileWidth`, `layerTop`, `layerHeight`) and derive their Y
  from their own `depthFraction`, so they scroll in exact sync with the hills.
- Horizontal position is `tileFractionX` mapped across a screen-width tile
  period, deliberately narrower than the hill silhouette's own wider period.
- Objects tile horizontally, so each one is drawn once per tile copy that
  actually intersects the screen and nothing pops at the wrap seam. Which copies
  those are is derived from the tiling period and the object's own extent, never
  from a fixed copy count — see `ARCHITECTURE.md` §3.
- Sprite origin is the bitmap's own pixel (0,0), placed at the caller's local
  unit coordinates. Anchor offsets are hand-written literals per part.

### Objects outside the system **[OBSERVED]**

**People and vehicles are inside the projection as of Group 4.** Both take their
ground line, their scale and their speed from `SceneSpace`, so a pedestrian is
larger than a car and a far-lane car smaller and slower than a near-lane one,
without any of it being maintained by hand.

What is still outside it, deliberately: pedestrians do not use `GroundGeometry`,
so they do not tile or scroll with the terrain, and they are not a placeable
category with density and visibility controls. That is Group 5.1 and 5.2, and
5.2 is blocked on decision D3.

### Rules

1. Any new ground-anchored category must use `GroundGeometry` and
   `depthFraction` from the start.
2. Objects sharing a ground plane share an anchoring rule.
3. Front/back ordering derives from depth, never from call order.
4. Anchor offsets must come from declared asset bounding boxes, not from
   eyeballed constants. Placement and anchor are separate at every call site that
   Group 4 touched: a walking person, a window occupant, a car driver, a car
   passenger and the dolphin are all placed as `placement - anchor`, with the
   anchor named as a constant rather than folded into the origin.

---

## 7. Colours

### App brand palette **[OBSERVED]**

| Token | Value |
|---|---|
| Paper Orange | `#F2A65A` |
| Paper Orange Dark | `#B5651D` |
| Paper Cream | `#FFF7EC` |
| Paper Night | `#1B1B2F` |

### Scene palettes **[OBSERVED]**

Every scene colour is a **day/night pair** blended by `dayBlend` from
`SunPositionCalculator`. This applies to sky (6 user-editable stops), hills,
mountains, lake, clouds, precipitation, and every object category's two colour
variants.

Each object instance stably picks one of its category's two colour variants via
a hash of its own position — stable meaning it never flickers between frames.

### Fixed accent colours **[OBSERVED]**

There used to be about a dozen of these — penguin beak `#F2A65A`, bunny inner ear
`#EFA8B8`, gift ribbon `#F2C230`, tree trunk `#7A4B2E`, skyscraper lit window
`#FFE79A` and dark `#2E323C`, house planter `#6D8F4F`, and the three lake
decorations added in v74.1. They existed because the artwork for those accents was
a white mask, so the only place its colour could live was a constant multiplied
over it at the blit.

**The V2 artwork carries them, so the constants are gone.** Every one of those
sprites is finished art now, and keeping the constant would have made it a second
colour compounded over the first. Two survive, and both are the cases that were
never really about a sprite: the parasol pole `#EFE0CE`, which is a `drawRect`,
and the penguin belly `#F3F7FB`, whose sprite is still a greyscale mask.

The road surface colours (`#5B5650` day, `#29271F` night) also remain, for the
same reason: the road is drawn, not blitted, and the three `road_*` sprites are
orphans.

### Rules

1. Every new scene colour must be authored as a day/night pair.
2. A colour exposed to the user must remain legible across the full range they
   can choose. Test at both extremes.
3. Accents that define an object's identity stay fixed.
4. Avoid pure black and pure white for large fills; the paper look depends on
   slightly warm off-tones.

---

## 8. Lighting, shadows and effects **[OBSERVED]**

- **Light direction**: none. There is no directional light model. Shading is
  baked ambient mottling, not a lit surface.
- **Shadow direction**: none — the ground shadow is a centred ellipse whose
  role is anchoring, not lighting. **Do not** introduce directional shadows
  without a corresponding light model; a mix of the two reads as an error.
- **Shadow intensity**: `0x2E000000`, uniform.
- **Glow**: sun only (`RadialGradient` plus a sunburst sprite).
- **Window light**: houses' windows warm gradually at night; skyscrapers'
  windows switch on and off randomly. Both are colour changes, not light
  sources — they do not illuminate anything around them.
- **Precipitation** fades in over the first 10 % of its fall and out over the
  last 10 %, so it appears to emerge from the cloud layer rather than popping in
  mid-air.

---

## 9. Icon and UI system

### Material 3 scope **[OBSERVED]**

Material 3 governs the **settings UI only**. It must never be applied to the
wallpaper's illustration style.

### Colour scheme **[DECIDED — v2.9]**

`PaperScrapeTheme` now defines the **complete** Material 3 scheme, light and dark. Every role is a
tone of the four colours the app has always been built on — `PaperOrange #F2A65A`,
`PaperOrangeDark #B5651D`, `PaperCream #FFF7EC`, `PaperNight #1B1B2F`:

- **primary family** — `PaperOrangeDark`, with a light tint of it as the container.
- **secondary family** — the same hue desaturated, so a secondary container reads as paper rather
  than as a second accent competing with primary.
- **tertiary family** — the wallpaper's own day sky (`SceneTheme.SUNSET.skyDay`), the one cool
  colour already in the product, used sparingly.
- **surface family** — `PaperCream` stepped darker for each container level. This is what gives a
  grouped list its container, and it is the reason the settings UI needs neither a border nor a
  divider per row.
- **error family** — a warm brick rather than Material's default red.

Before this, four roles were defined and the remaining ~30 fell back to Material 3's baseline
violet, so switches, inactive slider tracks, containers and dialog surfaces rendered off-brand.

### Icons **[DECIDED — v2.9]**

**Material Symbols, not emoji.** The settings UI used emoji as section markers (🎨 Scene objects,
🎃 Seasonal decorations, 🖼️ Manage themes) as a de facto convention. Every one is now a Material
icon from `material-icons-extended`, which was already a dependency. The one emoji that lived in a
string resource (`live_weather_title`) lost it too.

The wallpaper is untouched by this: Material 3 governs the settings UI only.

### Settings structure **[DECIDED — v2.9]**

Five destinations, all drill-downs from a home screen that holds no settings of its own:
**Home** (which theme is showing, who chose it, where everything else is), **Weather & time**,
**Seasons & decorations**, **World & scene**, **Advanced & about**, plus the theme gallery reached
from Home's Theme row.

Two pairs of mutually exclusive booleans are presented as one choice each — location source
(Off / Phone / Custom) and seasonal palette (None / Autumn / Winter). Both are presentation only:
the flags, the setters and the exclusivity they already enforced are unchanged, and
`SettingsUiModelTest` pins both mappings in both directions.

### Theme previews **[DECIDED — v2.9]**

A gallery card draws a **real mini scene** — sky, horizon, mountains or dunes, water, buildings,
trees, people, traffic, and whatever seasonal decorations that theme actually has — from the
shipping sprites, at the renderer's own part offsets, with the theme's own palette.

Three rules govern it:

1. **Nothing a theme does not have.** Every object is conditional on the flag the wallpaper reads.
   No lake where `lake.visible` is false, no boats on the tundra, no flowers outside Spring. Where
   the scene has no sprite at all — parasols are drawn procedurally — the preview shows nothing
   rather than standing in a different sprite.
2. **A dozen objects in four reading bands** — skyline, tree line, the house row with its people,
   the road. The real scene carries hundreds of objects across a screen five times wider; shrinking
   that produces mush, and a preview needs a hierarchy, not a census.
3. **Static and cheap.** No GL context, no animation, no timer, no per-card bitmap. See
   `ARCHITECTURE.md` § "Theme previews".

Themes whose subject *is* a time of day show that time: Sunset shows its dusk sky, New Year and
Halloween show night. Everything else shows its day palette.

---

## 10. Rendering rules **[OBSERVED]**

1. **Pre-render, then blit.** Objects are drawn as cached bitmap blits, not by
   re-walking antialiased paths each frame. This is the established direction
   and should continue.
2. **Decode once, cache.** `SpriteCache` decodes each resource once for the
   process lifetime. It currently has no memory-pressure release — a known
   defect.
3. **Nothing allocated per frame in a draw path.** See `AI_PROJECT_RULES.md`
   §5.1. The current code violates this in several places; new code must not.
4. **Scene layout is state.** Candidate positions and seeds are computed when
   their inputs change, not every frame.
5. **No full-scene overlays.** Established by the removal of the paper-grain
   effect (v56, v58) for CPU cost and colour fidelity.
6. Target ~30 fps. Frame pacing compensates for measured frame cost. **This is a
   cap, not a floor**: the scene is drawn on a GPU surface whose buffer swap
   blocks on vsync, so an unpaced loop would run at the display's refresh rate —
   60, 90 or 120 Hz — and do several times the work for motion this slow.
8. **The renderer is GPU-backed; the picture is not.** Scene objects are still
   flat paper shapes at fixed colours, drawn in the same order at the same
   coordinates. Moving to OpenGL ES bought CPU headroom, and that headroom is
   explicitly **not** a licence to add per-pixel effects, lighting, blurs or
   full-scene overlays — rules 1, 5 and §8's "no light model" stand unchanged and
   are artistic decisions, not performance ones.
9. **Both rendering backends must produce the same picture.** Anything the scene
   draws has to be expressible in the shared `SceneCanvas` operation set, which is
   deliberately narrow: a transform stack, rects, lines, circles, ovals, stroked
   arcs, filled sectors, closed shapes, three named gradient forms, and sprite
   blits. A new visual effect that needs an operation outside that set is a design
   decision, not just an implementation one, because it either splits the two
   backends or has to be added to both.
7. **A scrolling layer is either tiled or bounded, never wrapped alone.** A
   repeating field (the star field) repeats as it scrolls, so it stays continuous
   and never opens a gap or resets at the wrap. A single object (the sun, the
   moon) is bounded instead, so it stays in the viewport and never leaves,
   duplicates or pops. Wrapping a layer's shift without also tiling its content
   only moves the single copy off screen and snaps it back.

---

## 11. Protected elements

These require explicit maintainer approval before modification.

| Element | Why |
|---|---|
| `HILL_SAFE_DEPTH_MIN` / `MAX`, `ROAD_SAFE_DEPTH_MAX` | Hand-derived so objects sit on solid ground and stay clear of the road. Changing one without re-deriving the others breaks placement globally. |
| `GLOBAL_OBJECT_SCALE = 2` | Every object's size depends on it. |
| `SPRITE_PIXELS_PER_UNIT = 3` | Baked into every `SCENE_UNITS`-convention sprite; defined once, in `SpriteBlitter`. Changing it invalidates the whole asset set. |
| Category depth bands and base scales in `SceneObjectCatalog` | The de facto proportion system. |
| The flat, low-detail artistic direction | Established by two reverted attempts to add detail. |
| Absence of a full-scene overlay | Established by two removals of the paper-grain effect. |
| Tintable / fixed-art classification per category | Determines what the user can recolour. |
| `MULTIPLY` as the tint operation, on both backends | Decision #4. The GPU backend reproduces it in the fragment shader rather than reimplementing tinting; changing it in one backend and not the other would make the wallpaper and its own settings preview disagree. |
| The ~30 fps cap | Decision #20. Removing it hands the loop to vsync and multiplies the frame rate by 2-4× for no visible gain at this pace. |
| The transparent border around each atlas entry | Without it a bilinear sample near a sprite's edge picks up whatever sprite was packed next to it — a one-pixel fringe of an unrelated object, appearing only for the pairs that happen to be adjacent in the atlas. |
| Built-in themes' default palettes | User-facing identity of each theme. |

---

## 12. Approved design decisions

| # | Decision | Version | Reason | Affects |
|---|---|---|---|---|
| 1 | Flat, bold, low-detail objects; added detail reverted | v63 | Extra detail read as fussy, not richer | All scene objects |
| 2 | No full-scene paper-grain overlay | v56 → v58 | CPU cost; colours no longer matched what the user configured | Whole renderer |
| 3 | Pre-rendered sprite blits instead of per-frame vector drawing | v65 → v72 | CPU and battery | Houses, buildings, trees, palms, cars, clouds, terrain, sky objects, decorations |
| 4 | `MULTIPLY` tint instead of `SRC_IN` | v66 | Lets baked shading survive the runtime tint | Every tintable sprite |
| 5 | Single hill layer instead of three stacked bands | — | Matches the intended silhouette read | Hills, and every depth constant derived from them |
| 6 | Ground shadow ellipse on every ground-anchored object | v53, extended later | Objects read as floating without it | All ground-anchored objects |
| 7 | Four real moon-phase silhouettes, reused mirrored for the waning half | v72 | More accurate than the previous geometric approximation | Moon |
| 8 | Two house size variants (small/large), stably picked per instance | v73 | Visual variety without new categories | Houses |
| 9 | **Effect density is a linear fraction of a fixed candidate pool.** Backward compatibility with v73's density curve was explicitly waived by the maintainer. | Phase 2.1/2.2 | Density that means what it says: 25% shows about 25% of the pool. Previously the cloud pool size was itself derived from density, so the slider moved every cloud instead of thinning the field. | Clouds, precipitation, birds, mountains, lake decorations |
| 10 | **Pool sizes are part of the visual contract**, not implementation detail: clouds 41, precipitation 90, birds 6, falling leaves 26, mountains 4, lake decorations 4, lake sparkles 5. Changing one re-arranges that effect in every theme. | Phase 2.1/2.2 | Pool size defines what 100% density looks like | As above |
| 11 | **Per-cloud drift retained** — each cloud keeps its own drift speed and phase rather than moving in lockstep with its depth tier. | Phase 2.1/2.2 (decision D9) | Phase 2 was scoped to determinism and allocations, not to a redesign of atmospheric motion. The wider empty stretches this produces at low density are recorded as a separate visual-tuning question. | Clouds |
| 12 | **A visible category with density above zero always shows at least one element.** | Phase 2.1/2.2 (decision D7) | With a four-candidate pool, a low but non-zero setting would otherwise show nothing, reading as switched off rather than sparse. Not applied as a curve — only as a floor. | Birds, mountains, lake decorations |
| 13 | **Precipitation falls only where there is cloud cover.** Rain and snow are gated by a local coverage field derived from the clouds actually on screen; coverage zero means no precipitation, with no diffuse floor. | Phase 2.2b (device-reported) | Rain falling from wide stretches of clear sky read as obviously wrong once cloud density became predictable enough to set low. | Rain, snow |
| 14 | **Soft coverage edges come from the kernel shape, not from a floor.** Each cloud is fully covering within its own silhouette and falls off smoothly across a margin 1.6× wider. | Phase 2.2b (decision: floor = 0) | Softens the transition at a cloud's edge while keeping genuinely open sky at exactly zero. | Rain, snow |
| 15 | **Hiding the cloud layer does not hide precipitation** — coverage falls back to uniform. | Phase 2.2b (decision D10) | Cloud visibility and precipitation are separate user controls; one must not silently disable the other. | Rain, snow |
| 16 | **SVG is the source format for sprite art; PNG stays the runtime format.** Sources live in `tools/assets/sources/svg/`, rasterised by a version-pinned pipeline. | Phase 3.1 (decision D12) | A PNG that is its own source cannot be re-derived, re-scaled or corrected except by editing pixels. `VectorDrawable` re-rasterises every draw, so vectors must not reach the runtime. | Every future sprite |
| 17 | **The registry covers every shipped sprite.** It was written when 24 had a source and 94 were declared gaps; as of the V2 library all 111 have one. | Phase 3.1, completed v76 | A registry listing only the successes would read as complete while omitting exactly the sprites that are the problem. | Asset inventory |
| 19 | **The sun and the moon never leave the viewport, and the star field is continuous across the wrap.** With `scrollBackground` on, the celestial body moves within the room its own rest position leaves to the screen edge, and the star field is tiled on its real period. The body still drifts and still follows the swipe; it simply cannot go off screen, and no second sun or moon may ever appear. | v73.6 | The sky is the one part of the scene the eye returns to. A sun that vanishes for minutes at a time reads as the wallpaper being broken, and an empty band of sky reads the same way. Bounding the body costs some parallax near sunrise and sunset, where its rest position leaves almost no room; that was accepted as the price of never losing it. A cyclic wrap was rejected because it would put a second sun on screen at the seam. | Sun, moon, star field, when `scrollBackground` is on |
| 20 | **The wallpaper is rendered with OpenGL ES 2.0; the artwork is unchanged.** The scene logic, coordinates, sprite set, colours, layering and animation are identical — only the rasteriser changed. The `Canvas` renderer is kept for the settings preview, which draws into a Compose canvas with no GL context, and as the automatic fallback when EGL is unavailable. | v73.11 | Every frame was being rasterised in software on the main thread: a full-screen gradient, the hill silhouettes and up to ~1,900 sprite blits, all on the CPU. The paper-cutout look never needed software rasterisation — it needs flat shapes composited in order, which is what a GPU does for free. Deliberately *not* taken as an opportunity to change how anything looks. | The whole wallpaper |
| 22 | **Sprites share one GPU texture wherever they fit, and flat fills share it too.** | v74 | Draw order is depth order and cannot be reordered, so the only way to stop a scene object's flat details from breaking the batch between its sprite parts is to put them in the same texture. Invisible to the artwork: what a sprite looks like, where it sits and how it is tinted are all unchanged. | Every sprite |
| 21 | **A gradient is expressed as its stops, never as a `Paint` shader, at the boundary between the scene and the renderer.** | v73.11 | A `Shader` set on a `Paint` cannot be read back, so a backend that is not `Canvas` cannot see it. The three gradients in the scene — the sky, the hill highlight, the sun/moon glow — therefore pass their stops explicitly and each backend realises them its own way. | Sky, hills, sun, moon |
| 24 | **A byte-identical pair is either one drawing under two names, and one of them goes, or a variant whose artwork is missing, and it is declared.** Never left as an unexplained coincidence. Variant groups live in `tools/assets/sources/sprites.json` with an axis, a state and a reason; `paperscrape-assets validate` and `SpriteVariantTest` check both directions. | v74.2 | Sixteen groups of shipped PNGs were byte-identical, and the measurement alone could not distinguish a deliberate share from a feature that silently does nothing. v73's seasonal outfits for window occupants shipped with the summer and winter heads as the same file, and every per-sprite check — size, content box, anchor, scale, tint — passed, because two copies of one picture satisfy all of them. | Every shipped sprite; the person and house sets in particular |
| 23 | **A "fixed-art" sprite's colours may be supplied at the blit rather than baked into the PNG.** The classification in §3 stays a statement about *who chooses the colour* — the artist, not the user — and not about *where the bytes live*. Where the artwork is a light mask, its call site multiplies it by a named non-user-editable constant, as the penguin's beak, the bunny's inner ear and the gift ribbon already did. | v74.1 | Three sprites classified fixed-art shipped as pure-white masks and were blitted untinted, so they rendered as white silhouettes on the lake for many releases. Reading the classification as "the colours are in the PNG" is what made an untinted blit look correct at the call site. Restating it as "the colours are not the user's to change" fixes the defect without promoting a category to recolourable. | `dolphin_body`, `sailboat_hull`, `sailboat_sail`, and any future mask-authored fixed-art sprite |
| 25 | **Tintable means the PNG is a greyscale mask; fixed-art means the PNG carries its colours. The class is a property of the bytes, decidable by measuring the file.** This supersedes decision 23, which allowed a fixed-art sprite to be a mask coloured at the blit. `SpriteTintClassTest` asserts it in both directions. | v76 | Decision 23 was a repair for artwork that did not honour its own classification, and it worked, but it left the class undecidable from the file — which is how the defect got in. The V2 library authors every sprite to one profile or the other, so the weaker rule is no longer needed, and keeping it would have meant multiplying finished art by leftover constants. | Every sprite, and every blit call site |
| 26 | **Consequences of decision 25 on user-facing colour controls are accepted, not compensated.** Sun Color drives only the ambient glow; the star colour, Fall Colors on palms, and per-building window lighting no longer act at all. | v76 | Compensating would mean re-introducing a tint over finished art, which is the error decision 25 exists to prevent. If one reads wrong on a device the fix is artwork. Recorded as pending decision D7. | Sun, stars, palms, skyscrapers, fireworks |
| 18 | **Geometry is recovered by measurement or not at all.** Only shapes determined by their canvas (rectangles, rounded rectangles) are reconstructed; free-form silhouettes and baked mottling are gaps until the redesign. | Phase 3.1 | A best-scoring fit over free parameters is a redraw presented as a recovery. | Reconstruction scope |

### Pending decisions

| # | Question | Status | Blocks |
|---|---|---|---|
| D1 | Contradiction between the README's legal note and provenance statements in source comments | **Deferred by the maintainer.** Recorded, no action taken. | README rewrite; reference-usage rule wording |
| D3 | Do people become a fully customisable category (visibility + density + colours) or remain ambient with a single toggle? | Open | People integration |
| D4 | Is decision #4's colour-fidelity trade-off acceptable on a real device? Never confirmed visually. | Open | Any tint-related work |
| D5 | Dependency upgrade window — before or after the rendering work? | Open | Dependency refresh |
| D7 | The V2 classification retires four user-visible colour behaviours: **Sun Color** no longer reaches the disc or the sunburst, the theme's star colour no longer reaches the sparkle, **Fall Colors** no longer reaches palm fronds, and skyscraper windows no longer light per building. All four are consequences of the artwork carrying its own colours, and none is to be recovered by tinting the new art. **Needs a device look**: if the fixed sun or the fixed palm reads wrong against a given theme, the answer is new artwork, or restoring a mask for that one sprite. | Open — awaiting device verification | Any change to `sun_body`, `sun_glow`, `star_sparkle`, `palmtree_fronds`, `skyscraper_wall_lit` |

---

## 13. Known visual issues

| Issue | Root cause | Status | Approved solution | Resolved in |
|---|---|---|---|---|
| Changing cloud density or rain intensity teleports the whole field | Candidates failing the density filter were skipped before consuming the shared RNG stream, shifting every later candidate's values; for clouds the pool size also moved with the density | **Resolved** | Fixed pools with index-addressed attributes | Phase 2.1/2.2 |
| Also triggered by the hourly live-weather refresh | Same as above; `cloudCoverFraction` feeds the same density input | **Resolved** | Same | Phase 2.1/2.2 |
| Cars restart from their initial position while dragging any unrelated slider | Every configuration change rebuilds the whole `SceneObjectRenderer`, resetting car runtime progress; the settings write path is undebounced | Open | Debounced persistence + incremental renderer update | — |
| Sliders feel like they stick near 0 % and 100 % | Not a touch-target problem: the UI thread is flooded by DataStore writes and recompositions during the drag | Open | Same fix as above | — |
| All animation quantises after days of uptime and freezes at ~12 days | `elapsedSeconds` is an unbounded `Float` | Open | `Double` accumulation, wrapped to a bounded phase before rendering | — |
| Window occupants and car drivers look the same in winter as in summer | The six seasonal head PNGs are byte-identical. The seasonal distinction was drawn for the *walking* sprites — beanie instead of hair, long sleeves, snowflake motif, trousers rather than a skirt — and never for the heads | **Open — declared gap** | None available without asset redesign: person art has no committed source, so a winter head has to be authored rather than regenerated. Recorded as `IDENTICAL_GAP` in the sprite registry (v74.2), with `validate` and `SpriteVariantTest` failing the moment the artwork arrives | Artwork still missing; gap declared v74.2 |
| People do not shrink with distance and are not anchored to the terrain or road | Positioned by a fraction of screen height, outside the depth system | Open | Integrate into the shared scene space | — |
| Clouds and precipitation select the same candidate indices | The intended per-effect salt was a no-op: `(8001×131) mod 1000 == (9001×131) mod 1000` | **Resolved** | Evenly spaced per-effect threshold offsets | Phase 2.1/2.2 |
| Larger empty stretches of sky at low cloud density | Per-cloud drift speeds pull the field apart over time; measured largest gap at 50% density is 0.169 of a tile against an ideal of 0.045 | Open — deferred by decision D9 | Candidate: one drift speed per depth tier (measured at 0.114). Not adopted in Phase 2 to avoid redesigning atmospheric motion | — |
| Precipitation falling from wide areas of completely clear sky | Precipitation read only `precipitation.intensity`; the two systems shared no coordinate space | **Resolved** | Local coverage field derived from the drawn clouds | Phase 2.2b |
| Sun, moon and stars leaving the screen with `scrollBackground` on | The sky layer's shift was wrapped but its content was drawn as a single copy, so the wrap slid that copy off screen and snapped it back once per period instead of bringing anything back into view | **Resolved** | Star field tiled on its real period; celestial body given a bounded, non-cyclic offset | v73.6 |
| Sunburst rays sat below and right of the sun's disc, and star sparkles were oversized and offset the same way | Each sprite's pixel size, its scale convention and its blit origin are only correct together, and a PNG records none of it: `star_sparkle.png` was replaced by a 3× redraw while its call site kept the raw-pixel convention, and `sun_glow.png` shipped as a raw-pixel sprite carrying the origin an oversampled one would want | **Resolved** | `star_sparkle` blitted as `SCENE_UNITS`; `sun_glow` anchored at `-222`. Neither PNG regenerated — the artwork was always correct | v73.7 |
| Edges of circles, stroked arcs and thin lines may read slightly differently from the `Canvas` renderer | `Canvas` antialiases these analytically; OpenGL ES does not, and relies on multisampling instead | Open — mitigated, unverified | 4x MSAA requested at EGL config time, with the same config without it as a fallback. Sprites are unaffected: they are textures, filtered the same way either backend draws them | v73.11, pending device check |
| The hot-air balloon's basket draws white | `balloon_basket.png` is a pure-white mask blitted untinted — the same cause as the lake decorations below, found after that fix rather than by it. Five other `FIXED_ART` sprites share the profile (`bunny_tail`, `car_window`, `firetruck_ladder`, and the orphans `house_wall` and `house_trim`), and some are probably correct as they are | Open — defect D-6 | Decision 23's repair, applied per sprite rather than in bulk: a named non-user-editable constant at the blit for each one judged to need colour | — |
| Dolphins and sailboats appear as blank white shapes on the lake | The three sprites are pure-white (or greyscale-on-white) masks, but are classified fixed-art and were blitted with no tint. White is the `MULTIPLY` identity, so the mask rendered as-is. Not a GPU, atlas or UV fault: the silhouettes were always correct, which means the alpha and the sampled region always were too | **Resolved** | Colour supplied at the blit from three named non-user-editable constants; no PNG regenerated, no coordinate moved | v74.1, pending device check |
| Dolphins and sailboats can overlap while drifting | The two effects select decorrelated candidate indices but neither consults the other's positions | Open — defect D-5 | None decided | — |
| Palm fronds detached from trunk | Stale anchor constants not recalibrated after the art was redrawn | **Resolved** | Documented recalculation | v67 |
| Mountains rendered as an invisible triangle | Geometry bug in the two-face fill | **Resolved** | Root-caused with rendered mocks before editing Kotlin | v70 |
| Dolphins/sailboats almost always hidden behind hills | Placement bug | **Resolved** | Placement corrected | v70 |
| Cars drifting outside the road strip | Coordinate mismatch introduced by the car redraw | **Resolved** | Coordinates corrected | v73 |
| Sun appeared to have a second pale disc beside it | Over-complex sunburst art | **Resolved** | Simplified sunburst | v73 |

---

## 14. Visual approval process

For any significant visual or UX change:

1. **Inspect** the current state — read the code, measure the assets, render
   what exists today.
2. **Propose** the change in writing, including what it will affect.
3. **Create a visual mockup or prototype.**
4. **Obtain the maintainer's approval.**
5. **Implement.**
6. **Verify visually** — render the result and look at it.
7. **Record the decision** in §12, and update any rule in this document that
   the decision changes.

Small technical fixes with no visual impact skip steps 3 and 4. When in doubt
about whether a change is visually significant, treat it as significant.

---

## 15. General consistency rule

Every new asset, visual change or UI component must be evaluated **against the
existing visual system as a whole**, never in isolation. An object that looks
correct alone but wrong beside its neighbours is wrong.

When a new decision changes an existing rule, **edit the existing rule**. Never
leave two contradictory instructions in this document.

## 16. Halloween: two flags, and a moon that is carved rather than painted

**Halloween and the horror sky are separate switches.** A decoration layer and a palette are
different statements, and every combination of the two is a scene somebody might want: bare
trees under an ordinary night sky, an ordinary scene under a lurid orange one, both, or
neither. Tying them together would repeat exactly what winter and Christmas were split to
undo — for a whole release "winter" and "Christmas" were synonyms, and nothing failed, because
each was internally consistent and the defect was only visible as a scene you could not reach.

Neither flag touches winter, Christmas, New Year or the fall palette, in either direction.

**Halloween does two things and no more.** The moon becomes `moon_jack_o_lantern`; every tree
drops its canopy for `tree_dead_branches`. The pumpkins keep their own switch, for the same
reason Santa keeps his: one thing with two controls that can disagree is worse than two things
with one each. The snow cap and the Christmas lights are not disabled by it — they simply have
nothing to draw on a tree with no foliage.

**The moon's face is cut out of the disc, not laid on top of it.** One `fill-rule="evenodd"`
path, so the sky shows through the eyes, the nose and the grin. Painting the face in a second
colour would have been easier and would have stopped reading at around 90 px; the moon is
drawn at roughly 48. This is the same paper-cutout move the rest of the library makes, and it
is what makes the shape survive being small. The carved face is always full: a jack-o'-lantern
that waxed and waned would be a lit fraction of a grin, which reads as a rendering fault.

**The horror sky overrides the user's six sky colours rather than editing them**, so switching
it off gives the palette back untouched. It keeps the day/night blend, because a sky that never
changed would stop the sun and the moon meaning anything — but it holds the whole range between
near-black overhead and one saturated orange at the horizon. Two flat paper tones with a
gradient between them; nothing photographic.

## 17. The dolphin's splash is derived from the leap, not remembered across frames

The leap is a sine and the animal is drawn only while it is positive, so re-entry is exactly
where that angle, read as a position in a 0..1 cycle, passes half. The splash occupies a short
window after that instant, and the frame and the fade come from where the current frame lands
inside it.

**Nothing is stored.** A "was it above water last frame" flag would need allocating per
dolphin, keeping across a surface change and a visibility pause, and would be wrong for one
frame every time the wallpaper resumes mid-leap. Deriving it is correct at exactly the seams
where remembering it is not.

The splash is scaled by the same factor as the animal that made it, so a distant dolphin throws
a small one and a near one a bigger, and the two can only ever be wrong together.

## 18. Silhouettes are judged at the size they are drawn, not at the size they are authored

`bird_body` read as a bat for as long as it shipped: a sharp elbow in the leading edge, broad
wing roots, and a head circle sitting apart from the body — the three things that separate a bat
from a gull, all present at once. The gull that replaced it has smooth tapered wings sweeping
back to a point, a head continuous with the body, and a wedge of tail.

Its geometry could not change while its look did. The wing-flap is a **vertical mirror of the
coordinate frame**, so the body has to sit on y=0 and the wings above it; the redraw kept the
canvas, the viewBox and that axis exactly, and only the shapes moved.

**The before/after mockup is what caught the dolphin redraw's real defect.** The first version
put the flukes and the head at the same end — the sprite is drawn inside a mirrored group, and
the eye had been left at low x, matching the original, while the flukes were moved there too.
An animal with two tails and no face, and it would have shipped. Artwork changes get a mockup
for this reason and not as ceremony.

## 19. The Halloween theme presets its two switches without coupling them

`ThemeCatalog` had ten themes and none was Halloween, so there was nowhere to preset anything
until the eleventh was written. Its own palette — bruised violet overhead, low amber at the
horizon — matters even though `horrorSkyEnabled` overrides it the moment the theme is chosen:
**it is what comes back when the user turns the horror sky off**, and a Halloween theme with
both switches off still has to look like something.

The theme's defaults set `halloweenEnabled`, `horrorSkyEnabled` and the pumpkins. **Presetting
is not coupling.** It seeds a starting value the way every other theme seeds
`winterColorsEnabled` or `parasols.visible`; neither flag reads the other, here or anywhere,
and a test starts from the theme's own defaults and turns each off in turn to prove the other
survives. Winter, Christmas and the fall palette are untouched: bare branches are not a
snowfall, October is not December, and bare branches are not autumn leaves either.

The pumpkins joined it rather than being excused from the rule. `BuiltInThemeCoherenceTest`'s
"pumpkins stay in autumn" became "pumpkins stay in the two themes that are about pumpkins",
and asserts both directions.

## 20. What actually separates a gull from a bat

Three things, and it took all three to fix it: notches under each wing that read as claws, a
hard elbow in the leading edge over broad wing roots, and a head sitting apart from the body.
Any one alone might have passed. The gull has long tapered wings drawn to a point, a body and
head in one piece, and a tail that narrows away rather than forking.

**Before touching an animal, read the library's rule off the animals already in it.**
`bunny_body` and `penguin_body` gave it: three to seven shapes, large primitives, flat tints,
no outline, almost no interior detail. That rule is also what finally fixed the dolphin —
nine iterations of trying to carry the whole animal in one outline, and what worked was
building it the way the bunny is built, a circle for the melon and a wedge for the beak with
a fusiform body over them.

**Judge it at the size it ships.** The gull is 90 px wide on screen and the dolphin about 48;
a sprite that only reads at 345 px has not been checked.

## 21. The splash fires on both crossings, and still keeps no state

The leap is a sine and the animal is above water for the first half of every turn of its
angle, so as a position in a 0..1 cycle the two crossings are the two ends of that half: out
at 0, in at 0.5. Each opens a short window, and the two cannot overlap because the window is a
small fraction of half a cycle.

**One splash per crossing, not one per phase change.** A frame inside a window draws the burst
at the size and opacity its position calls for; a frame outside both draws nothing. Nothing
accumulates, nothing trails the animal across the lake, and a dropped frame costs a frame of
the effect rather than the whole event.

Drawn after the animal, so on the way out it rises up through its own splash. On the way back
in there is nothing left to cover, so the order costs nothing there.

## 22. An outline goes outside, and is judged over a sequence

v2.5's readability edge was clipped to the inside of every shape, which made its thickness a
function of what each shape happened to overlap. Standing still it looked right. Walking, the band
appeared and vanished between frames as the arms and legs moved the overlaps — **and every test
passed, because every test looked at one sprite and the defect only exists across three.**

The replacement draws the whole sprite a second time underneath itself, filled and stroked in the
outline colour: overlapping strokes merge into one contour, the normal fills hide the internal
seams, and what is left is a continuous band of one width around the union of the artwork. It
depends on the union and on nothing else, which is exactly why it is stable.

**Two treatments, because two classes of sprite reach the screen by different arithmetic.** A
fixed-art sprite carries a dark edge directly. A tintable one cannot: the runtime multiplies it by
the user's colour, so it must stay a colourless mask, and its edge is a light neutral grey that
`MULTIPLY` turns into a darker version of whatever colour was chosen. Not a special case — the
same intent expressed in the only form each class can hold.

**An outer outline grows the silhouette by half the stroke on every side.** That is what makes it
outer, and it is handled the way a crop is: re-measure the registry, let the anchors follow, move
the origins that depend on them.

**Anything with frames is verified over its frames.** `test_outline.py` asserts across a walk
cycle, not within a sprite: one colour for the cycle, the band all the way round each frame, and a
thickness that cannot vary more than a few percent between them.

---

## 23. A settings screen is sized to its window, not to the display

**[MEASURED — Pixel 9, Android 16, gesture navigation, 1080x2424 at 2.625x]**

The last row of a scrolled-to-the-bottom settings screen was cut off. It had been treated twice as
a spacing problem, and twice it survived the fix, because it was never one.

Every settings destination is a full-screen `Dialog`. `dumpsys window` reports:

```
mAttrs={(0,0)(1079x2423) gr=CENTER ... fitTypes=statusBars navigationBars captionBar systemOverlays}
Frames: frame=[0,142][1079,2361]
```

The window's frame is **2219 px** — the display less the status bar (142 px) and the gesture bar
(63 px), which is correct and is what `fitTypes` promises. But its layout parameters ask for
**2423 px**, because with `usePlatformDefaultWidth = false` Compose measures a dialog's content
against the display rather than against that frame. `Modifier.fillMaxSize()` therefore laid out
204 px of content below the window's own bottom edge, where the window clipped it.

So the last rows were never under the gesture bar. They were **outside the window**, and no
trailing spacer inside the scrolling content can move something back into a window it has already
overflowed. It also explains the symptom precisely: scrolling did reach the end of the content,
and the end of the content was off-window.

The corroborating measurement, from inside the dialog: `WindowInsets.safeDrawing` reports **0 px on
every edge** — which is right, the window already fits the bars — while the same UI hosted by the
activity reports 63 px at the bottom. Two windows, two correct answers, and content sized against
the wrong one.

**The rule, from v2.14.** A settings destination's content is given the height of the area its
window occupies: the display less the insets the *activity* measures, since the activity's window
does cover the display and does report them. The scaffold inside reserves the dialog's own insets,
which are zero exactly when the window is already inside the bars — and are real values on a
device whose dialog window is full-bleed instead, so both arrangements are handled without asking
which one applies. The trailing spacer goes back to being 24 dp of breathing room, a constant,
carrying no inset at all.

**Verified by scrolling to the end and reading the position off the accessibility tree**, not by
eye: the last row of Weather & time moved from y = 2380 — inside the gesture bar's 2361–2424 band
— to y = 2238.

---

## 24. Two weather providers, and no silent substitution

Live Weather can fetch from Open-Meteo or Visual Crossing. The choice is the user's and the app
does not revise it: if the selected provider fails, the failure is reported and the selection
stands. Quietly answering with the other service would make "which provider am I using"
unanswerable, and the existing behaviour on failure — keep the last good reading, and otherwise
let the theme's own weather run — is already the right one.

The two differ in one way that reaches the UI. Open-Meteo has a keyless free tier, so a blank key
is a working state and the key screen says "optional". Visual Crossing has none, so a blank key is
a **configured state with a name**: the provider stays selected, the settings screen says a key is
required, and **no request is made** — an app that sends a call it knows will be rejected has
spent a round trip to learn nothing and reports it as a network problem.

Status is reported as one of six states rather than the single "running on the theme's own
weather" flag v2.13 had, because with a key involved the reasons now need different answers from
the user: a missing key is one tap from fixed, a dropped request is something to wait out, and a
dropped request with an earlier reading still on screen is not a fallback at all.

---

## 25. A measurement, where one exists, is the answer

**[MEASURED — clean Android 17 emulator, live Open-Meteo, Florence 43.77925 / 11.24626]**

Live Weather has now been wrong in both directions, and the two failures are mirror images:

- **v2.12** read only the `weather_code`. Millimetres falling under an "overcast" code drew a dry,
  fully clouded sky.
- **v2.13** added the measurements but kept the code as an *unconditional* fallback. Four
  measurements reading `0.00` were outvoted by a code, and the sky rained on a dry afternoon.

The captured reading that settles it:

```
current: precipitation 0.00 · rain 0.00 · showers 0.00 · snowfall 0.00 · weather_code 80 · cloud_cover 100
```

`80` is "slight rain showers". Fifteen minutes earlier the same coordinates returned `3`,
"overcast", with the same four zeroes. **The code alternates across a dry hour while the numbers
never move**, which is the whole argument: a code is an interpretation of a grid cell and the
millimetres are the observation, and an interpretation cannot be allowed to overrule four
observations that disagree with it.

**The rule, from v2.14.** Snowfall decides, then rain-or-showers, then — for a positive total with
no breakdown to explain it — the code chooses only the *kind*. If measurements were reported and
every one is zero, nothing is falling. Only a response carrying no measurements at all lets the code
decide whether anything falls, which is the case Open-Meteo's customer endpoint and Visual
Crossing's shape both produce, and the reason the model keeps "not reported" and "reported zero"
distinguishable all the way through.

The intensity floor is what made the phantom visible: with no millimetres behind it, a code-derived
rain came out at the 0.15 minimum-visible intensity — a scatter of drops with nothing measured
under them.

---

## 26. Live Weather drives both layers, or neither

**[MEASURED — same session]**

Rain fell from a cloudless sky, and it was not one bug but two composing. The second was an
asymmetry between two layers that should have answered the same question the same way:

```
drawPrecipitation:  if (liveOverride != null) { ... }    // the theme's own switch is not consulted
drawClouds:         if (!clouds.visible) return          // consulted, and before the override
```

With the theme's cloud switch off:

```
SCENE clouds.visible=false clouds.density=0.4 override.cloudCover=1.0 -> drawn=false
SCENE precip.visible=false override.type=RAIN                          -> drawn=true
```

The settings screen states the contract plainly — *"Real current conditions replace each theme's
manual rain/snow/cloud settings automatically... this theme's own Clouds/Precipitation screens
switch to read-only."* A layer that keeps its veto while its sibling gives one up produces exactly
the scene that was reported, and the user cannot even undo it: the Clouds switch is read-only while
Live Weather is on, so a setting left off earlier is stuck off.

**The rule.** While Live Weather is active the forecast owns the cloud layer as completely as it
already owned precipitation. It lives in `engine/LiveWeatherSceneRules`, pure and tested, rather
than inside the draw call, because what needed pinning was not a value but the *agreement between
the two layers* — a property no test of either one alone would have caught.

Two consequences worth stating:

- A forecast reporting **0 % cover draws no clouds**, whatever the theme's switch says. The forecast
  wins in both directions or it is not driving.
- When no clouds are placed, the coverage field is **uniform**. Precipitation is thinned by the
  cloud cover above it, so an empty field would silently cancel rain the forecast did report —
  which is the same class of bug again, one layer quietly overruling the other.
