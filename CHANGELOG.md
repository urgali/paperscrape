# Changelog

Each version here corresponds to a zip delivered in chat and a commit on the
user's GitHub repository. From here on every output is versioned: the
delivered file is named `PaperScrape_vN.zip` and this changelog entry
summarizes its contents, so it's always clear what each commit (`v1`, `v2`,
`v3`, ...) contains without having to diff by hand.

## v73 — Material aesthetic pass: houses, vehicles, buildings, seasonal objects, and two new
features (walking people, Santa's sleigh)

A large mockup-then-build pass, worked in chat as SVG previews first (checked against
The reference app's own decompiled reference where applicable, e.g. its noprem1/sky1 atlases for house
variants and the Santa sleigh) and only converted to production sprites once approved. Every new
sprite follows the existing tintable-white-silhouette convention (`drawTintedSprite`) so nothing
that was user-customizable in v72 stopped being customizable here.

- **Houses -- new small/large variants**: `drawHouse` now picks between two sizes per instance
  (same stable per-position hash technique `drawSkyscraperBuilding` already used for its 3
  building styles), each with a cozy pass neither size had before: a flower planter under a
  window, a soft porch light by the door, and gentler chimney smoke (shared helpers
  `drawFlowerDots`/`drawPorchLight`/`drawChimneySmoke`).
- **Cars -- redrawn as one continuous sedan silhouette** (chassis + cabin fused, single glass
  band) instead of the old two-stacked-boxes look, plus headlight/taillight/door-handle detail.
  Taxi/police/fire-truck accessories repositioned to match the new body shape.
- **Restaurant/bar -- now identifiable at a glance**: restaurant gets a hanging fork-and-knife
  sign, bar gets a hanging beer-mug sign (replacing a plain unlabeled circle).
- **Skyscraper**: added a stepped setback tier and a rooftop antenna so the silhouette reads as
  an actual tower, not a plain rectangle.
- **Tree canopy**: redrawn as a 5-lobe cluster (was a single blob) so it reads clearly as a tree
  rather than a bush/cloud at a glance; matching snow-cap sprite redrawn to fit.
- **Balloon -> hot air balloon**: `drawBalloon` now draws a proper envelope+basket silhouette
  instead of a party balloon on a string.
- **Sun**: added a defined 8-ray sunburst + soft ring layer (`sun_glow.png`) behind the existing
  mottled disc and radial-gradient ambient glow.
- **Star**: redrawn softer/rounder (`star_sparkle.png`), less spiky.
- **Seasonal objects sprite-converted**: snowman, gift, penguin, easter egg, bunny, and pumpkin
  were still procedural `Path`/`drawOval` calls in v72 -- converted to `drawTintedSprite` calls
  using the exact same local-space coordinates the old procedural code already used (read
  straight from the v72 source, not re-estimated), so proportions didn't drift in the conversion.
- **New: pedestrians.** Man/woman/boy/girl now walk along the sidewalk in front of the road, each
  with a 4-frame side-profile walk cycle (own drift timer, independent of the
  car/house placement system -- modeled on how `drawBirds` already works), summer/winter outfit
  following the theme's existing winter-colors flag, direction mirrored via a horizontal flip
  rather than doubling the asset count. A person is also sometimes visible at a house window
  (~1 in 3 houses) and driving a plain/taxi/police/fire-truck car (man or woman only).
- **New: Santa's sleigh**, flying across the upper sky at a periodic interval, winter-theme only,
  modeled on `drawBirds`'s own self-contained periodic-crossing approach. Fixed colors (not
  user-tintable), matching how the reference's own Santa art is a fixed real-world red/white.
- Several sprite-generation bugs caught and fixed *before* shipping by actually rendering and
  visually inspecting every new/changed sprite against a colored background (not just trusting
  the SVG source): a car body silhouette that rendered nearly empty from a clipped SVG viewBox, a
  tree canopy missing its top lobe for the same reason, a dolphin silhouette scrambled by a wrong
  `rotate`/`translate` transform order, and a house door/window overlap that wasn't visible until
  composited together at actual scale.

## v72 — Aesthetic pass batch 4 (part 3, birds & sun/moon+stars sub-group): all sprite-converted, moon now uses real phase silhouettes

Continuing Batch 4 with the "birds & sun/moon+stars" sub-group aa picked next. Checked the
reference's decompiled source before writing any code, as always -- `Bird`, `Moon`, and `Star`
all genuinely blit textures (`SpriteSheet.Sprite.birdup`/`moon*`/`star*`), so all four objects in
this sub-group are real sprite conversions, not procedural touch-ups like the mountains/hills/
rainbow scope decisions in the earlier parts of this batch:

- **Sun -- sprite-converted**: was a plain flat-filled circle, now a tinted sprite with baked
  paper-fold mottling (`sun_body.png`), matching the reference's own mottled-paper sun texture
  instead of a solid color disc.
- **Moon -- sprite-converted, and a real behavior upgrade, not just a reskin**: the old
  implementation approximated lunar phases with a clever but purely geometric technique (a
  half-disc plus a variable-width terminator ellipse, `radius * |cos(phaseAngle)|`). Replaced
  with 4 baked phase silhouettes (`moon_crescent`/`moon_half`/`moon_gibbous`/`moon_full.png`)
  reused for the waning half via a 180° rotation -- *exactly* the reference's own technique
  (`Moon.MoonPhase` enum reuses `mooncres`/`moonhalf`/`moongib` with `angle=180` for
  Third Quarter/Waning Gibbous/Waning Crescent), not just a visual approximation of it. Verified
  by rendering the full 7-step phase sequence (waxing crescent through full to waning crescent)
  from the 4 sprites + rotation before touching the Kotlin -- see this delivery's own working
  notes. The "always-visible faint dark disc" earthshine effect and the `realisticPhases` toggle
  are both unchanged in behavior, just re-implemented as sprite blits.
- **Stars -- sprite-converted, plus a size fix**: were plain filled circles at 1-2.8px radius --
  too small for any shape to read as more than a blur, so bumped to 2.4-5.6px and swapped in a
  4-pointed sparkle sprite (`star_sparkle.png`, matching the reference's own `starsmall` texture
  much more closely than a dot) with a slow rotation added for a bit of twinkle life.
- **Birds -- sprite-converted, and the flap animation now matches the reference's actual
  technique**: previously a per-frame quad-bezier path that continuously bent the wing curve.
  The reference's own `Bird` class instead flips `sy`'s sign every few frames -- a mirror flip
  between one baked pose and its vertical reflection, not a continuously-interpolated curve.
  Replaced with a single "wings up" sprite (`bird_body.png`) and the same sign-flip trick
  (`canvas.scale(scale, if (flap < 0f) -scale else scale)`) for the "wings down" half of the
  cycle -- cheaper *and* a closer match to the actual reference behavior, not just a coincidence
  of doing less work.
- Dead code removed: `starPaint`, `birdPaint`, `birdPath` (only ever used by the now-removed
  vector drawing code for each).
- Two bugs caught and fixed *before* finalizing this delivery, not after: the first crescent-moon
  sprite generation attempt produced a fully blank/transparent image (a boolean-subtraction logic
  error -- the eraser circle's bounding box fully covered the source shape instead of leaving a
  sliver), and the first mottling pass on every sprite was far too strong (large, high-opacity
  blotches reading as blobs rather than subtle paper texture). Both caught by rendering and
  looking at the actual output before integrating into the Kotlin, not after.
- Brace balance re-checked against the pre-edit file (89/89, unchanged -- 2 new helper functions
  added balance out against no functions removed, only bodies replaced).
- **Still not verified with a real device build** -- same standing caveat as v66-v71.

## v71 — Aesthetic pass batch 4 (part 2, clouds & rainbow sub-group): clouds sprite-converted, rainbow given a paper-fold touch-up

Continuing Batch 4 with the "clouds & rainbow" sub-group aa picked next. Checked the reference's
decompiled source before writing any code, as always:

- **Clouds -- sprite-converted**, and a genuine architectural + performance win, not just
  aesthetics: the reference's own `Cloud` class blits a real texture (`SpriteSheet.Sprite.cloud`,
  from `clouds1.png`), and this app's own previous implementation was the single most expensive
  per-frame drawing path among everything still vector-drawn -- up to 41 candidates/frame, each
  built from 4 `Path.op(..., UNION)` boolean operations plus a clip, a translated shadow fill,
  and a stroke. New original-art `cloud_body.png` (proportions matched 1:1 to the old procedural
  geometry's own radius convention, not the reference's pixels) bakes in the mottling, the
  under-shading, and a soft rim that used to be drawn at runtime, so [drawPuffyCloud] is now one
  `drawTintedSprite` call. With that per-frame cost gone, also bumped cloud density's candidate
  count back up from 36 to the reference's own real ~41-cloud maximum (previously capped below it
  specifically because of the Path.op cost this delivery removes) -- updated the stale doc
  comments that explained the old cap accordingly, rather than leaving them describing a
  constraint that no longer exists.
- **Rainbow -- explicitly *not* sprite-converted this round, by design, not an oversight.** The
  reference's `Rainbow` class does genuinely blit a texture too, so this would otherwise qualify
  the same as clouds -- but its size scales with `screenWidth` every frame in both apps, unlike
  every other sprite-converted object so far (all roughly fixed-size in "sprite pixels per
  unit"). A fixed-resolution PNG stretched arbitrarily to match arbitrary screen widths would
  blur or pixelate depending on device size -- a correct conversion needs its own dynamic-scale
  draw path, worth its own delivery rather than a rushed bolt-on. Instead gave it a cheap,
  purely-procedural touch-up: a thin brighter highlight along each band's own inner edge, same
  "paper catching light" idea the mountain/hill aesthetic pass used, so each band reads as a
  slightly domed ridge instead of a flat color fill. Still only 7 `drawArc` calls/frame (not
  per-candidate) -- never the performance problem clouds were, so no urgency to convert it purely
  for cost reasons either.
- Dead code removed: `cloudLobePath`, `cloudPath`, `cloudOutlinePaint` (only ever used by the
  now-removed vector cloud path-union code).
- Brace balance re-checked against the pre-edit file (89/89, unchanged from v70 -- the removed
  and added function bodies net out to the same count).
- **Still not verified with a real device build** -- same standing caveat as v66-v70.

## v70 — Batch 4 part 1 fixes: mountains were rendering as an invisible triangle, dolphins were almost always hidden behind hills

aa reported two visual bugs after building v69 on a device:

1. **Mountains rendered as an invisible triangle with two thin crescent "stripes" around it.**
   Root cause: the two-half fill added in v69's aesthetic pass closed each half's path with a
   straight line from the peak *diagonally back to the opposite base corner*, not down the
   mountain's own vertical center axis. Since this mountain shape is a parabola that bulges out
   sharply near the base (by design -- see `drawSoftMountain`'s own doc comment on why segments
   are width-spaced, not height-spaced), that diagonal cuts far inside the curve at every
   mid-height, so only a thin crescent between the diagonal and the curve actually got filled --
   most of the intended half-mountain area sat *outside* the polygon (background/sky showing
   through) instead of inside it. Verified with a rendered mock of the exact broken geometry
   before touching the code, confirming it reproduces the "invisible triangle, two stripes"
   report exactly. Fixed by closing each half via the vertical line from the peak straight down
   to `(cx, baseY)` -- the curve's own true center, `peakX == cx` by construction -- instead of
   the diagonal.
2. **Dolphins/sailboats almost never appeared to be swimming in visible water.** Root cause: the
   lake's own bottom edge is deliberately anchored to the hill layer's guaranteed-covered line
   (see `lakeTopBottomY`'s and `drawHillLayers`' own doc comments -- hills are meant to visually
   paint over the *lower* part of the water for depth, and are drawn *after* the lake). Checked
   the actual numbers against the default theme's Lake Height (0.33): every placement in the old
   `laneFraction` range (0.25-0.75, i.e. the *middle* of the band) landed at or past the hill's
   own worst-case reach -- always at least "sometimes hidden behind whichever hill column
   happens to be over it", several of them "always hidden" outright, regardless of the random
   per-instance roll. Confirmed with the exact fractions computed from the real constants
   (`yOffsets`/`heightFractions`/`HILL_SAFE_DEPTH_MIN` and `buildBaseHillPath`'s own `[0.04,
   0.22]` heightFrac range) before writing the fix, not just from a description of the symptom.
   Fixed by biasing placement toward the *top* of the band instead, using the same worst-case
   hill-geometry derivation `HILL_SAFE_DEPTH_MIN` already uses, just for the opposite guarantee
   ("never covered" instead of "always covered") -- doesn't reach 100% guaranteed-visible at
   every Lake Height setting (a very thin band can sit entirely below that line no matter where
   within it something is placed -- a limitation of the lake/hill geometry contract itself, which
   is shared with mountains and deliberately not touched here), but always picks the best
   achievable position instead of the worst one. At the Big City theme's Lake Height (0.9), the
   new range is fully inside the guaranteed-visible zone.
- Both fixes manually verified with rendered Python mocks of the exact same geometry/math before
  editing the Kotlin (not just reasoned about on paper) -- see this delivery's own working notes.
  Brace balance re-checked against the pre-fix file (89/89, consistent with the one new `if`/
  `else` added).
- **Still not verified with a real device build** -- same standing caveat as v66-v69.

## v69 — Aesthetic pass batch 4 (part 1, terrain sub-group): dolphin/sailboat sprite-converted, mountains/hills given procedural paper-fold shading

aa asked to continue the batch-by-batch conversion, picking the "terreno" (terrain: mountains/
hills/lake decorations) sub-group of Batch 4 first.

**Important finding, cross-checked against the reference app's own decompiled source before
writing any code** (per aa's standing rule to always compare against the real the reference app source):
mountains and hills are **not texture-based in the reference either** -- `Mountain` extends
`TwoColorModel` and `Hills` extends `SegmentedPlane`, both genuine vertex-colored GL geometry
with zero texture reads. So "sprite conversion" (in the sense batches 1-3 did it for houses/
buildings/cars) doesn't literally apply to them -- there's no reference texture to convert *to*.
Dolphins and sailboats are the opposite: `Dolphin`, `SailboatBottom`, and `SailboatSails` all
genuinely blit sprite textures from `land1.png` (`SpriteSheet.Sprite.dolphin`/`dolphin2`/
`sailboatbottom`/`sailboatsails`) in the reference. Split this delivery accordingly:

- **Dolphin, sailboat -- sprite-converted** (real conversion, matching the reference's own
  architecture). New original-art PNGs (`dolphin_body.png`, `sailboat_hull.png`,
  `sailboat_sail.png`, proportions loosely informed by measuring -- not copying -- the
  reference's own `land1.png` regions) replace the old per-frame vector `Path` work in
  `PaperRenderer.drawLakeDecorations`. Neither object has a user-editable color
  (`LakeConfig` has no dolphin/sailboat color fields), so these use a new local `drawSprite`
  helper in `PaperRenderer` (no runtime tint, colors baked into the PNG at generation time --
  same convention `palmtree_trunk.png` already established) rather than
  `drawTintedSprite`. Same leap/bob/rotate animation as before, now blitting bitmaps instead of
  walking 5 separate `Path`s per dolphin every frame. `SPRITE_PIXELS_PER_UNIT = 3f` duplicated
  into `PaperRenderer`'s own companion object (must stay in sync with
  `SceneObjectRenderer`'s constant of the same name and with `gen_terrain_sprites.py`, kept in
  chat, not committed, same convention as `gen_sprites.py`).
- **Mountains -- procedural aesthetic pass, not a sprite conversion** (see finding above).
  `drawSoftMountain` now fills each mountain as two halves sharing the exact peak/base points
  (no seam): left face lightened 10% toward white, right face darkened 8% toward black -- a
  fixed light-from-upper-left convention, cheap (two fills instead of one, no new per-frame
  allocations), meant to stand in for the "paper fold" shading batches 1-3's baked sprite
  mottling gives everything else, since there's no texture to bake it into here.
- **Hills -- same procedural aesthetic pass, adapted for a continuous wavy shape**: `hillPaint`
  now fills via a `LinearGradient` (top of the layer lightened 12% toward white, settling to the
  exact configured color by 35% down the layer) instead of one flat color, built once per layer
  before the 3 tile-offset copies (only X is translated per copy, so one shader covers all
  three). Same reasoning as mountains -- `Hills` has no texture in the reference either.
- Both `lakeDecorPaint`/`lakeDecorPath` fields (only ever used by the now-removed vector dolphin/
  sailboat drawing) deleted as dead code rather than left unused.
- **Still not verified with a real `compileDebugKotlin`/`lintDebug`/device build** -- same caveat
  as v66-v68 (no Android SDK in this environment). Manually checked instead: brace/paren-balance
  diffed against the pre-edit file (both edits net +1 brace pair matching the one new function
  added; the small paren-count delta is prose-comment noise, confirmed present at the same
  magnitude in the unmodified v68 file too, not a real imbalance), the 3 new
  `R.drawable.*` references (`dolphin_body`, `sailboat_hull`, `sailboat_sail`) cross-checked 1:1
  against actual files now present under `res/drawable-nodpi/`, and both new sprites' local
  "unit" origins hand-verified by compositing them together in a mock at the exact same offsets
  the Kotlin code uses. Flagging this as usual for aa's own eyes on a device before treating it
  as fully settled.
- **Not done in this delivery**: lake decorations' *placement/water shading* (`drawLake`,
  `drawLakeBand`) untouched -- only the dolphin/sailboat objects themselves. Birds, clouds,
  rainbow, sun/moon/stars, Santa's sleigh, and every seasonal decoration are still the rest of
  Batch 4, order not yet agreed for what comes after this.

## v68 — Aesthetic pass batch 3: cars sprite-converted, and a genuinely new feature -- police cars, taxis, and fire trucks

**Build-breaking bug found by CI and fixed before this ever reached a working release**: the
first attempt at this delivery declared `val reverse: Boolean = false` *twice* inside
`CarObject` (a copy-paste artifact from inserting the new `type` field via a text edit that
didn't remove the original line below it) -- Kotlin accepted this as two conflicting synthetic
declarations rather than catching it as a plain duplicate, which only surfaced as cryptic
"Overload resolution ambiguity" / "Conflicting declarations" errors at every *call site* that
touched `.reverse` (`CustomThemeData.kt`, `SceneObjectRenderer.kt` x3), not at the actual
duplicate itself -- `compileDebugKotlin` failed in CI exactly as flagged as a real risk when this
batch shipped without local build verification (no Android SDK in the environment it was
produced in). aa pasted the CI log; traced all 6 reported error locations back to this single
duplicate field, removed the stray second declaration, verified no other duplicates in the same
file (brace-balance-checked every file touched by this batch), and confirmed all 3 `CarObject(...)`
construction sites use named arguments (so removing the duplicate couldn't silently reorder
anything). This is exactly the scenario the "please treat this delivery as needing your review
more than the past two" warning below was about -- kept as-is rather than watered down after the
fact, since it turned out to be justified.

aa asked to continue the batch-by-batch conversion with cars, explicitly naming "semplice/
polizia/taxi/pompieri" (plain/police/taxi/fire truck) -- the reference's own real vehicle sprite
set (`road1.png`'s car/car2/policecar/taxi/firetruck). Unlike batches 1-2, this genuinely adds a
new feature rather than only reskinning what already existed: **PaperScrape only ever had one
generic, user-colored car type before this delivery** -- there was no police/taxi/fire-truck
distinction anywhere in the code (only a comment mentioning the reference's own sprite names).
Flagging this plainly since it changes the shape of this delivery versus batches 1-2: new data
model (`CarType` enum), not just new art.

- **New `CarType` enum** (`PLAIN`, `POLICE`, `TAXI`, `FIRE_TRUCK`) on `CarObject`, defaulting to
  `PLAIN` so every existing call site and every already-saved custom theme's JSON (which predates
  this field) keeps behaving exactly as before with zero migration needed.
- **Type assigned by a stable weighted pick** at candidate-generation time (70% plain, 10% each
  special type) -- deterministic per candidate slot, exactly like lane/speed/reverse already are,
  not re-rolled per frame. The "🎲 Randomize" generator got the same treatment independently
  (75% plain / 25% a random special type per generated car).
- **Only `PLAIN` stays user-recolorable** via the existing "Cars" category color pickers -- the 3
  special types use fixed, real-world-associated colors (police white+black, taxi yellow, fire
  truck red) exactly the way the reference's own taxi/fire-truck/police-car sprites are fixed-
  color, non-tintable art (cross-checked against this session's own the reference app sprite export,
  `road1.png`). No new settings toggles added this round -- every type still respects the
  existing "Show Cars" visibility/density controls uniformly; independent per-type show/hide
  (matching the reference's own "Police Cars / Fire Trucks / Taxis" checkboxes) would need real
  plumbing across `SceneCustomization`/`WallpaperPrefs`/`CustomThemeData`/Settings UI and was
  deliberately left out of this batch's scope -- happy to add if aa wants it as its own delivery.
- **Sprites**: `car_body` (tintable, shared by all 4 types -- a low wide body + raised cabin bump,
  two rounded rects unioned, replacing the old hand-authored `Path` with quad-bezier corners) and
  `car_window` (fixed pale fill, shared). Each special type adds its own small accessory sprite on
  top: `police_stripe` (black lower-body two-tone band) + `police_lightbar` (red/blue roof bar),
  `taxi_checker` (black/white side stripe), `firetruck_ladder` (silver roof ladder with rungs).
  Wheels stay vector (2 circles + 2 stroked circles, already cheap, shared unchanged by every
  type). Same baked-in "paper fold" mottling + `MULTIPLY` tint as every sprite since v66.
- Every `R.drawable.*` reference (6 new ones) cross-checked 1:1 against an actual file in
  `res/drawable-nodpi/` before delivery, same as every prior batch.
- **Still could not verify with a real `compileDebugKotlin`/`lintDebug`/device build** -- same
  Android-SDK-less environment as v66/v67. Extra care taken reviewing the new-feature surface by
  hand this time (all 4 `CarObject(...)` construction sites across `SceneObject.kt`,
  `RandomSceneGenerator.kt`, and `CustomThemeData.kt`'s JSON (de)serialization updated together;
  `CarType.entries` confirmed valid for this project's Kotlin 2.2.10; `keepCar`'s filter confirmed
  type-agnostic on purpose, matching the "no new toggles this round" decision above) -- but this
  is real new logic, not just new pixels, so **please treat this delivery as needing your review
  more than the past two, not less**.
- Remaining objects, still vector-drawn: clouds, mountains, hills, lake decorations (dolphins/
  sailboats), birds, sun/moon (+ moon phases), stars, rainbow, Santa/reindeer/sleigh, and every
  seasonal decoration. No agreed order yet for batch 4.

## v67 — Palm crown/trunk seam fixed; aesthetic pass batch 2: skyscraper/restaurant/bar buildings sprite-converted

aa reported the palm tree's fronds reading as slightly detached from the trunk in v66 (everything
else confirmed OK), and asked to fix that plus continue the batch-by-batch conversion with the
rest of the building types (skyscraper, restaurant, bar).

- **Palm crown/trunk seam (fix)**: traced precisely -- scanned the actual generated
  `palmtree_trunk.png` for its own topmost opaque pixel (world position ≈(1,-62)) and compared
  against where the redrawn `palmtree_fronds.png`'s new centered, symmetric crown point actually
  landed at the v66 anchor constants (≈(21,-86.5)): a ~20-unit horizontal gap and ~24-unit
  vertical gap, unrelated to the trunk/frond art itself -- the v65 pilot's original anchor
  constants (`4f, -89.45f`) were tuned for a differently-shaped (asymmetric, crown-at-left-edge)
  frond sprite and never got recalibrated when v66 redrew the fronds as a centered fan. Fixed by
  recalibrating the frond draw origin to `-16f, -87.45f` (both the normal and frost variants) so
  the sprite's own crown point (local (17, 27.45), centered/~47% down) lands 2 units inside the
  trunk's real top pixel -- a deliberate small overlap for a seamless join, not just a touching
  edge. Documented the exact derivation in `SceneObjectRenderer.kt`'s own comment so a future
  frond-art change doesn't silently reintroduce the same class of bug.
- **Skyscraper, restaurant, bar buildings -- sprite-converted** (batch 2 of the aesthetic-pass/
  sprite-conversion initiative, see `ROADMAP.md`):
  - **Skyscraper**: wall sprite redrawn (was a bare flat rectangle, like the other v65-pilot
    sprites before their own batch-1 redo) with rounded corners and a subtly inset rounded
    parapet cap at the roofline instead of a flat top. Window grid stays vector, unchanged
    (per-instance randomized lit/dark pattern, doesn't fit a static sprite).
  - **Restaurant**: wall, awning, door, and window converted from `Path`/`drawRect` calls to
    sprite blits. The awning is a new fixed-color (non-tinted) sprite with a softly scalloped
    bottom edge replacing the old sharp zigzag `Path`. Window and door use the same
    `drawTintedSprite`/day-night-blend values the vector version already computed -- no
    customization or lighting logic changed, only how pixels get painted.
  - **Bar**: wall and door converted to sprite blits (door reuses the same arched-door shape
    language as the house/restaurant doors, sized for the bar's own proportions). Hanging sign
    and string lights deliberately stay vector -- cheap already (one circle+line, four dots) and
    their color is a per-frame day/night blend that doesn't suit a static sprite the way the
    restaurant's fixed-color awning does.
  - All 7 new sprites share the same baked-in subtle "paper fold" mottling + `MULTIPLY` tint
    approach v66 introduced for house/tree/palm -- same color-fidelity trade-off already flagged
    then, not a new consideration.
- Every `R.drawable.*` reference added this batch cross-checked 1:1 against an actual file in
  `res/drawable-nodpi/` before delivery (a missing resource ID would fail the build outright, not
  just look wrong) -- all match.
- **Still could not verify with a real `compileDebugKotlin`/`lintDebug`/device build this
  session** -- same Android-SDK-less environment as v66. Reviewed the diff by hand (unused
  `height`/`awningY` locals removed cleanly from the now-sprite-based restaurant function, no
  leftover dead code; bar's `height` local confirmed still needed for the sign/string-lights
  vector code that stayed). CI plus aa's own look remains the real gate, more so than usual.
- Remaining objects, still vector-drawn: cars (plain + police + taxi + fire truck), clouds,
  mountains, hills, lake decorations (dolphins/sailboats), birds, sun/moon (+ moon phases),
  stars, rainbow, Santa/reindeer/sleigh, and every seasonal decoration. No agreed order yet for
  batch 3 -- pick up with aa next session.

## v66 — Aesthetic pass, batch 1 of N: house/tree/palm-tree sprites redrawn against the real the reference app reference

aa provided a full export of the reference app's own real sprite atlases this session (`res/drawable-nodpi/*.png`
from both a jadx decompile and a direct `unzip` of the device backup's `base.apk` -- 142 PNGs, identical
count in both, confirming completeness) plus its 7 GLSL shaders, and asked to (1) eventually convert every
remaining vector-drawn object to sprites for CPU/battery reasons, and (2) substantially improve the
*aesthetics* of every sprite, informed by that reference. Given the scope (~25 more object categories),
agreed with aa to go one batch at a time, each independently verified before the next -- this is batch 1:
the 4 sprites the v65 pilot already converted (house's 4 layers, tree canopy + snowcap, palm tree's 3
layers), all completely redrawn.

- **New art, original (not copied from the reference)** -- studied the reference's shape *language* (soft
  rounded silhouettes, an organically-lobed "cauliflower" canopy instead of 3 plain overlapping circles, a
  bold flat-color aesthetic with no outline stroke) but every path here is hand-authored fresh via a
  Python/Pillow script (`gen_sprites.py`, kept in chat per the same v65 precedent, not committed --
  only the resulting PNGs under `res/drawable-nodpi/` are):
  - **House**: wall gets softly rounded corners (was a plain rectangle), roof gets a gently rounded peak
    and eave overhang instead of a bare triangle, the door (`house_trim`) is now arch-topped instead of a
    plain rectangle, windows are a row of 3 small rounded squares with real gaps between them (wall shows
    through, was 2 mismatched blocks before).
  - **Tree canopy**: rebuilt as a genuine multi-lobe "cauliflower" shape (one center mass + a ring of
    smaller overlapping lobe circles, unioned via alpha) instead of 3 plain circles with visible concave
    notches only where they happened to overlap -- much closer to the reference's own rounded leafy-canopy
    silhouette. The snow-cap variant is generated from the *identical* lobe geometry/seed so it always
    lines up pixel-perfect with the canopy underneath, masked to keep only a wavy band near the top (snow
    sitting on the canopy, not a bowl underneath it -- an inverted-mask bug caught and fixed before this
    shipped).
  - **Palm tree**: trunk is now a gently curved, tapering silhouette with subtle bark-ring bands (was a
    straight rectangle); fronds are 6 properly tapered, curved blade shapes fanning from a single crown
    point (built by offsetting a curved spine along its own numerical tangent at each sample -- robust
    against any angle/droop combination, unlike an earlier analytic-tangent attempt that produced
    a jagged, partially-clipped fan caught in review and rebuilt). The frost variant reuses the exact same
    per-frond spine/taper math, restricted to just the outer ~38% tip of each blade, so it lines up
    exactly with the full frond underneath.
- **Every sprite bakes in subtle darker "paper fold" mottling** (soft, irregular, blurred blotches, never
  lighter than the sprite's own base tone) -- paired with a **tint-mode change, `SRC_IN` → `MULTIPLY`**
  (`SceneObjectRenderer.drawTintedSprite`), the one change needed for that mottling to actually survive
  the runtime color tint as gentle shading instead of being discarded by a flat single-color replacement.
  **Worth aa's explicit attention, given this project's history with the removed paper-grain overlay**:
  unlike the old `SRC_IN` tinting, the on-screen color is no longer bit-exact to the configured hex --
  darkened by a few percent wherever mottling sits. Kept deliberately low-strength and scoped to individual
  object sprites only (not a full-scene overlay the way the removed `crinkle.png`-based grain was), so the
  deviation should read as subtle texture rather than a color-fidelity problem, but this needs verifying
  on a real device, not just taken on faith from this description.
- Pixel dimensions of every regenerated sprite are **identical** to their v65 predecessors (verified against
  each `drawTintedSprite`/`drawSprite` call's own bbox comment in `SceneObjectRenderer.kt` before
  generating) -- zero anchor/geometry changes in Kotlin beyond the tint-mode line above.
- **Could not verify with a real `compileDebugKotlin`/`lintDebug` build this session** -- this environment
  doesn't have an Android SDK/Gradle available (unlike whatever environment produced v58-v65's own verified
  claims). Reviewed the diff by hand instead (import list already covers `PorterDuff.Mode`, no new imports
  needed; every sprite's pixel dimensions cross-checked 1:1 against the bbox comments consuming them). CI's
  own build on push remains the real gate -- **please treat this delivery as unverified until CI confirms
  and, ideally, until you've looked at it live on a device**, more so than usual.
- Everything else (buildings' remaining pieces, cars, clouds, mountains, hills, lake, dolphins, boats,
  birds, sun, moon, stars, Santa/reindeer/sleigh, and every seasonal decoration) is unchanged --
  intentionally out of scope for this batch, picking up the next batch once this one's confirmed good.

## v65 — Sprite-based rendering, pilot: houses, buildings, trees, palm trees

aa correctly identified that the reference app draws every object by blitting pre-rendered
bitmaps from static PNG atlases (`SpriteSheet.java` + `res/drawable-nodpi/*.png` in its
decompiled sources), not by re-walking vector `Path`s every frame the way this app's
`SceneObjectRenderer` always has -- and asked to move toward that same architecture for CPU/
battery reasons, starting with the most numerous static objects (houses, buildings, trees, palm
trees) as a pilot before converting everything else.

The dedicated sprite-generation skills weren't available in this environment this session, so
this delivery generates the sprites with a plain Python/Pillow script instead (kept in chat, not
committed to the repo -- only the resulting PNGs are).

- **New `res/drawable-nodpi/` sprite assets**: `house_wall`/`house_roof`/`house_trim`/
  `house_window` (4 separate layers, matching the 4 independently-colored regions the old vector
  code already had), `skyscraper_wall`, `tree_canopy`/`tree_canopy_snowcap`, `palmtree_trunk`
  (baked with its fixed, non-user-customizable color directly -- it was never tintable to begin
  with) / `palmtree_fronds`/`palmtree_fronds_frost`. Deliberately no baked-in outline stroke --
  compared against the reference's own actual sprite art, it doesn't have one either (same
  correction v63 already made for the vector-drawn versions).
- **New `engine.SpriteCache`**: decodes each sprite resource exactly once (`BitmapFactory.
  decodeResource` with `inScaled = false`, cached in a `ConcurrentHashMap` keyed by resource ID)
  and keeps it for the process lifetime, regardless of how many times `SceneObjectRenderer` itself
  gets recreated (e.g. every theme switch).
- **`SceneObjectRenderer.drawTintedSprite`/`drawSprite`**: blit a cached bitmap via
  `canvas.drawBitmap`, tinted with `PorterDuffColorFilter(color, SRC_IN)` for the tintable
  variant -- using the *exact same* color values (`wallColor`, `roofColor` derived from it,
  `windowColor` blended day/night, fall-palette leaf colors, etc.) the vector code already
  computed, so none of the existing customization or day/night-blend logic changed at all, only
  how the final pixels get painted. `SPRITE_PIXELS_PER_UNIT = 3f` -- sprites are generated at 3x
  the size they're actually drawn at, downscaled via one `canvas.scale()` for clean antialiased
  edges instead of a blocky small bitmap stretched up.
- **`drawHouse`/`drawSkyscraperBuilding`/`drawTree`/`drawPalmTree` rewritten** to blit instead of
  redraw. The skyscraper's window grid deliberately stayed vector (a handful of small unstroked
  rects, already cheap) rather than becoming a sprite too, since which specific windows are lit
  is randomized per-building-instance and needs to stay that way. The palm tree's sway is now one
  rigid rotation of the whole tree (trunk + frond cluster together) pivoted at its base, instead
  of bending the trunk's curve control points and rotating each of the 5 fronds independently
  every frame -- a static bitmap can't bend, and the old per-frond rotation was already just each
  frond's fixed angle plus one shared sway term, so a single rigid rotation is a very close visual
  match at a fraction of the cost.
- `Context` threaded through `PaperWallpaperService` → `PaperRenderer` → `SceneObjectRenderer`
  (needed to resolve sprite resources); the Scene Objects screen's own live preview renderer
  updated to pass `LocalContext.current` the same way.
- Caught and fixed a color mismatch while generating the palm trunk sprite (used a different fixed
  brown than the existing `palmTrunkColor` Kotlin constant) before it shipped, by regenerating the
  sprite with the exact matching color.
- Verified with the exact CI pipeline steps locally (`lint`, `test`, `assembleDebug`): all three
  clean, 0 errors, 56 warnings (2 more than the previous 54 -- both are `UseKtx` style suggestions
  on the new `canvas.save`/`scale` calls, same pre-existing warning category, not new problems).
- Everything else (cars, clouds, mountains, hills, lake, dolphins, boats, birds, sun, moon, stars,
  Santa/reindeer/sleigh, and every seasonal decoration) is still vector-drawn -- intentionally out
  of scope for this pilot delivery, picking up next once this approach is confirmed working well.

## v64 — Critical fix: cross-theme customization leak, Fall/Winter Colors on palm trees, falling-leaves origin

aa reported Winter/Christmas Colors on the Beach theme not affecting its trees while also
coloring the hills white, and Fall Colors' falling leaves reading as falling from the sky rather
than off the trees. Investigating the hills-turning-white report surfaced a much more serious,
general architectural bug affecting every theme and every setting, not just this one combination.

- **Cross-theme customization leak (critical fix)**: `WallpaperPrefs` stores every per-theme
  scratch setting (hills color, sky, precipitation, everything) as a single flat, un-namespaced
  DataStore key, gated by one `PENDING_CUSTOMIZATION_THEME_ID` marker for which theme those flat
  keys currently "belong to". `setTheme()` (switching the active theme) never cleared that marker
  or any of the flat keys -- so the moment *any* per-theme setter fired for the newly active
  theme (Winter Colors, in aa's report, but this could be literally any control), it flipped
  `PENDING_CUSTOMIZATION_THEME_ID` to match, which made `resolveActiveCustomization` start
  reading the *entire* stale flat state -- including fields last written while a *completely
  different* theme was being edited (e.g. hills set white while customizing the Winter/Christmas
  theme, then leaking into Beach the instant any Beach setting was touched). Confirmed this
  reproduces with any field on any theme combination, not just this one. Fixed with a new guard,
  `WallpaperPrefs.ensureFreshPendingTheme`, called as the first statement in all ~58 per-theme
  setters (inserted mechanically, verified each insertion individually): if a setter's own
  `forThemeId` doesn't match whichever theme the scratch state currently belongs to, every
  per-theme field is wiped first (via a new shared `clearAllThemeCustomizationKeys`, also now used
  by `resetAllCategories`, and extended to cover `fallColorsEnabled`/`winterColorsEnabled`/
  `santaEnabled` -- previously missing from that reset entirely since they were added later).
- **Fall/Winter Colors had no effect on the Beach theme's trees (fix)**: Beach uses
  `treeType = PALM_TREE`, routed to `drawPalmTree` -- but the fallColorsEnabled/
  winterColorsEnabled branches only ever existed in `drawTree` (the non-palm variant), so a palm
  tree never even checked either flag. Added matching branches to `drawPalmTree`: fall tints the
  fronds with the same autumn palette regular trees use; winter adds frost-white tips to the
  fronds (a fully snow-covered frond would read as broken/dead, not wintery) and the same string
  of blinking Christmas lights a regular tree gets, now strung along the trunk. `drawChristmasLights`
  gained `centerY`/`radius` parameters so it can adapt to a palm's crown shape and position
  instead of always assuming a regular tree's canopy center/size.
- **Falling leaves origin (fix)**: `fallStartY` was `-20f` -- literally above the top of the
  screen, the exact same "falls from empty space" mistake `drawPrecipitation`'s own origin had
  before v61's fix against the clouds -- so leaves crossed the *entire* screen height with zero
  relationship to where any tree actually is. Moved the origin to the hill band's own top edge
  (the same `yOffsets[0]` constant `drawHillLayers` itself uses, where tree canopies actually
  poke above the hill line) and shortened the fall to end at the road/ground level instead of the
  bottom of the screen. Added the same fade-in/fade-out `drawPrecipitation` already has, for the
  same "doesn't just pop into/out of existence" reason.
- Verified with the exact CI pipeline steps locally (`lint`, `test`, `assembleDebug`): all three
  clean, 0 errors, 54 pre-existing-pattern warnings.
- The broader aesthetic pass aa also asked for ("still far from the reference's sprites, improve
  every asset again") is real, substantial work on its own scale -- comparable to v63 -- and is
  intentionally not part of this delivery so this critical fix could ship on its own first;
  picking that up fresh next.

## v63 — Aesthetic pass, corrected: lake/dolphins/boats/birds reworked, Santa toggle, houses/buildings/cars simplified back toward the reference, placement density reduced

aa reported multiple issues with the aesthetic direction after v62: dolphins reading as plain
circles, sailboats too small/undefined, the lake too flat, birds needing work, Santa needing
improvement plus a real on/off toggle (previously hardcoded per-theme), and — most importantly —
that v62's own changes to houses/buildings/cars/trees were "bland, possibly worse" and should
copy the reference's actual objects far more directly rather than being invented from general
paper-cutout instinct. Extracted and viewed the reference's real sprite art (house1/2/3,
buildingtall/med/short, car/car2/policecar, dolphin/dolphin2, sailboatbottom/sails, santa/santa2,
birdup) directly rather than working from decompiled geometry alone this time.

- **Lake/dolphins/boats/birds, reworked against the real reference art**:
  - Dolphins: rebuilt with a plumper rounded body, curved snout, visible eye, dorsal fin,
    pectoral fin, upturned tail flukes, and a light belly stripe running the full underside
    (previously a single small circle) -- matched to the reference's actual dolphin/dolphin2
    sprite proportions.
  - Sailboats: rebuilt so one large triangular sail on a full-height mast dominates (previously a
    small sail perched on a disproportionately wide hull) -- matched to the reference's own
    sailboatbottom/sailboatsails sprites, which are almost entirely sail with a thin hull sliver.
    Added a small masthead pennant.
  - Lake: added alternating light/dark horizontal bands that gently undulate and drift (reads as
    actual water depth/movement), plus drifting sparkle glints on the surface. Top edge stays
    flat -- that's an unrelated, deliberate fix from v46 (keeps the mountains' fixed base from
    ever opening a sky gap against the lake edge), not something that needed correcting here.
  - Birds: rebuilt as a filled rounded double-lobed wing shape (matched to the reference's own
    "birdup" sprite) instead of two straight stroked lines, with the flap animation now bending
    each wing's curve rather than just moving line endpoints.
- **Santa**: added a real "🎅 Show Santa" toggle (Seasonal Decorations > Christmas) --
  `SceneCustomization.santaEnabled`, seeded per-theme from the previous hardcoded
  `SceneTheme.hasSantaSleigh` via `defaultCustomizationFor` (so nothing changes for a theme that's
  never had this touched), independently settable/resettable per theme from there on
  (`WallpaperPrefs.setSantaEnabled`/`resetSanta`, the latter removing the override entirely so it
  falls back to the theme's own dynamic default rather than forcing a fixed value). Also
  reworked the visuals against the reference's actual santa/santa2 sprite: fluffy white antler
  tips and a red Rudolph nose on the reindeer (previously bare lines and a plain head), a much
  larger fluffy white beard dominating Santa's face (previously a small hat trim circle), a
  chunkier rounded sleigh silhouette, and visibly colorful gifts peeking from the front pocket
  instead of one plain sack.
- **Houses/buildings/cars, simplified back toward the reference's actual bold-flat style**: v62
  added detail (chimney, shingle lines, arched door with a doorknob, a foundation band, a
  building parapet cap, split cabin windows with a center pillar, headlight/taillight dots, a
  two-tone rocker-panel skirt) that doesn't exist in the reference's own house1/2/3,
  buildingtall/med/short, or car/car2/policecar sprites at all -- those are deliberately bold,
  flat, minimal shapes (one flat wall block, one flat roof triangle, plain rectangular windows/
  doors; one rounded car silhouette with two plain window cutouts and two 2-tone wheels, no
  separate hubcap disc). Stripped all of that added detail back out. Kept the ground shadows and
  thin paper-cutout outlines from v62 -- those match hills/clouds/mountains' own established
  treatment and don't add fussy detail the way the removed elements did.
- **Placement density reduced**: houses/buildings/parasols/trees all defaulted to `density = 1f`
  (every one of `CANDIDATES_PER_CATEGORY`'s 10 slots shown at once, stacking across every depth
  band simultaneously) -- confirmed aa's "feels too crowded" report was a real, measurable default
  rather than only a visual impression, and lowered it to `0.65f`. Still fully adjustable per
  theme via each category's own density slider in either direction; this only changes what a
  fresh, untouched theme looks like out of the box.
- Verified with the exact CI pipeline steps locally (`lint`, `test`, `assembleDebug`): all three
  clean, 0 errors, 54 pre-existing-pattern warnings.

## v62 — Aesthetic pass: ground shadows, paper-cutout outlines, more detail on every structural/decorative object

aa asked for a broad visual-quality pass across every editable/moving element (except clouds,
already handled in v61), starting from an autonomous first pass to then refine together.
Reviewed mountains/hills/lake/dolphins/boats/sun/moon/birds first -- all already carry a
paper-cutout outline/shadow treatment from earlier iterations (visible in their own existing doc
comments) -- so this pass focused on the objects that didn't have that treatment consistently
yet: houses, buildings, cars, trees, palm trees, and every seasonal decoration.

- **Ground shadows, systematically**: new shared `drawGroundShadow` helper (a soft translucent
  ellipse at an object's own local y=0, so it always lines up regardless of the caller's scale) --
  the same technique `drawHouse`'s foundation strip and `drawParasol`'s shadow already used
  individually, now applied consistently to buildings (all 3 variants), cars, trees, palm trees,
  snowmen, gifts, penguins, bunnies, Easter eggs, and pumpkins, which previously had none.
- **Paper-cutout outlines, systematically**: a thin darkened-from-the-fill stroke around each
  object's silhouette, matching the treatment clouds/hills already had. Trees union their 3 canopy
  lobes into one path first (same technique `PaperRenderer.drawPuffyCloud` already uses) so the
  outline reads as one clean edge instead of 3 separately-stroked overlapping circles.
- **Cars**: cabin now splits into a windshield and a smaller rear window with a center pillar
  (previously one continuous glass band); added a headlight and a taillight; wheels get a lighter
  hubcap disc instead of reading as flat black circles; a subtly darker rocker-panel skirt gives
  the body some volume instead of one flat block of color.
- **Buildings**: skyscraper gets a parapet cap along the roofline (darker/wider strip) so the top
  reads as a real structure instead of the wall silhouette just stopping; restaurant's storefront
  window gets a center mullion matching the house/skyscraper window treatment.
- **Balloons**: added a crescent highlight for volume instead of one flat-filled ellipse.
- Fixed a latent paint-state leak found while making these changes: `drawSnowman`'s twig-arm
  stroke left `strokePaint`'s width at 3f for whatever drew next (now reset back to 2.5f, the
  shared outline default) -- same fix already applied to `drawBarBuilding`'s hanging-sign chain.
- Verified with `assembleDebug`.

## v61 — Weather bugfix round: cloud coverage, precipitation origin, location confirmation, Live Weather lockout

- **Clouds too small / don't cover the sky at 100%** (fix): compared against the reference's
  decompiled `Cloud.java`/`Scene.addCloudsAndBalloons` -- at full density it places ~41 heavily
  overlapping clouds (`f26sx` sized at 0.7-1.3x a full 25% of half-screen-height each), while this
  app's own count formula (`density*20+1`, capped at 21) and radius (45f*scale) were both scaled
  down too far for per-frame `Path.op` performance, leaving real gaps even at max density. Bumped
  count to `density*35+1` (cap 36, still below the reference's 41, keeping some deliberate
  performance headroom) and base radius from 45f to 68f (free -- same Path.op call count, just a
  bigger primitive); widened the off-screen culling margin to match (120f→160f) so bigger clouds
  crossing the edge don't get cut off early.
- **Rain/snow falling "from above" instead of out of the clouds** (fix): compared against the
  reference's decompiled `RainDrop.java` -- drops reset to `mMaxDropHeight = scene.baseCloudY`
  (the clouds' own anchor line) and fade alpha in/out over the first/last 10% of the fall
  (`FADE_RANGE`), rather than popping in/out at full opacity. This app's own origin sat at the
  clouds' band *top edge* (above the puffy bodies, which visually center lower) with zero fade --
  moved the origin down to the same band-center line `drawClouds` renders around, and added the
  matching fade-in/out.
- **Live Weather gave no confirmation once applied** (fix): manual custom-location "Apply" now
  shows a Toast ("Location applied") immediately. Also added `location.LocationLabelResolver`
  (reverse geocoding via `Geocoder`, API-33+ listener path and the older synchronous path both
  supported) so *both* the GPS toggle and the custom-location toggle now show a standing "📌 City,
  Country" label confirming exactly where they resolved to, not just raw coordinates or nothing at
  all. The GPS toggle's resolved fix is persisted to `WallpaperPrefs` (`resolvedGpsLatitude/
  Longitude`, written by `PaperWallpaperService` whenever a fix arrives) specifically so Settings
  -- a separate screen/lifecycle from the wallpaper engine -- always has something to geocode.
- **Manual cloud/rain/snow/thunderstorm controls stayed editable while Live Weather was on**
  (fix): those controls did nothing while Live Weather was active (silently overridden every
  frame, see `PaperRenderer.drawClouds`/`drawPrecipitation`'s own doc comments) but gave no
  indication of that. `CloudsSubDialog`/`PrecipitationSubDialog` now take a `liveWeatherEnabled`
  flag and disable (not hide -- still visible for context) Show Clouds/density, Show Rain-Snow/
  type/intensity/Thunderstorm while it's on, with an explanatory line at the top of each screen.
  Colors stay editable in both dialogs -- Live Weather was never designed to touch those (see
  v60's own changelog entry), so there's nothing to lock there.
- Verified with the *exact* CI pipeline steps locally after the v60 manifest incident (`lint`,
  `test`, `assembleDebug`), not just a general "still compiles" check: all three clean, 0 errors,
  54 pre-existing-pattern warnings.

## v60 — Phase 1d, Dynamic Weather: Fall/Winter tree colors, GPS/custom location, Live Weather

Completes ROADMAP Phase 1d in full. Also fixes a recurring "umbrellas float" report.

- **CI build failure (fix)**: `:app:processDebugMainManifest` failed on GitHub Actions with a
  manifest-parse error. Cause: the INTERNET permission's doc comment (updated below to mention
  Live Weather) contained a bare `--` mid-comment ("...Releases API -- and by Live Weather...") --
  XML forbids `--` anywhere inside a comment except as the closing `-->`, so any XML parser
  (including the manifest merger) rejects it outright. This is exactly the kind of mistake the
  local `assembleDebug`/`lintDebug` verification this delivery otherwise relied on should have
  caught, but didn't: the manifest edit was made *after* the last local lint/build check in this
  delivery, so it was never actually re-verified before being shipped. Fixed by rewording the
  comment (no `--`), scanned every other XML file in the project for the same mistake (none
  found), and re-ran the *exact* CI steps locally afterward (`lint`, `test`, `assembleDebug`) to
  confirm the whole pipeline passes now, not just a general "build still compiles" check.
- **Umbrellas floating (fix)**: root cause was never the ground anchoring (verified numerically
  again, still correct) -- the canopy swayed *vertically* (`translate(0, -50 + sin(...)*1.5)`)
  while the pole, drawn separately, stayed rigid and always ended exactly at y=-50. That let the
  canopy's attachment point drift away from the fixed pole tip every frame, reading as "hovering"
  regardless of how solid the ground shadow was (the v53 fix only touched the shadow). Replaced
  the vertical translate with a rotation pivoted at the pole tip -- the canopy now stays visibly
  attached to the pole at every point in its sway.
- **Houses/buildings/trees depth layering (fix)**: `HOUSE`/`SKYSCRAPER`/`PARASOL` were squeezed
  into a `depthFraction` band only ~30% as wide as the margin actually available before
  `ROAD_SAFE_DEPTH_MAX`, so everything landed at nearly the same depth scale/ground line --
  clustered instead of composed. Compared against the reference's decompiled
  `Scene.createBuildingsRanged` (`RangeType.Top`/`Bottom`, two separate bands sandwiching the tree
  zone) and replicated that split via a new `generateSplitStaticCandidates`; widened
  `SKYSCRAPER`/tree bands too, all still within the proven-safe `ROAD_SAFE_DEPTH_MAX`.
- **Fall Colors / Winter-Christmas Colors** (`SceneCustomization.fallColorsEnabled`/
  `winterColorsEnabled`): NOT their own placeable object category -- a palette override on top of
  the existing Trees category, toggled from Seasonal Decorations (not the Trees screen under Scene
  Objects, per aa's explicit framing: Trees' own show/density/color toggle is structural, whether
  those trees currently look autumnal/snowy is a decoration, same as pumpkins on a non-Halloween
  theme). Mutually exclusive. Fall: deterministic per-tree autumn palette + a falling-leaves
  effect (`PaperRenderer.drawFallingLeaves`, same stateless-candidate approach as precipitation).
  Winter: snow-dusted canopy arcs + blinking Christmas lights around the outline.
- **Location, shared groundwork** (`location.DeviceLocationProvider`): extracted the
  LocationManager/permission-check wiring that used to be inlined in `PaperWallpaperService` into
  its own reusable class exposing a plain `DeviceLocationFix(lat, lon)`, specifically so Live
  Weather could reuse the exact same fix sunrise/sunset already had instead of duplicating
  location code a second time. No behavior change for the existing sunrise/sunset feature.
- **Custom location** (`WallpaperSettings.useCustomLocation` + lat/lon/label fields): a
  manually-entered fixed coordinate, mutually exclusive with the phone-GPS toggle (enforced both
  in `WallpaperPrefs.setUseLocation`/`setUseCustomLocation`, which clear each other, and
  immediately in `PaperWallpaperService`'s settings collector). New `CustomLocationFields` UI,
  committed via an explicit "Apply" button rather than per-keystroke (an incomplete lat/lon
  mid-typing isn't a valid fix).
- **Live Weather** (`weather.WeatherRepository` + `WallpaperSettings.liveWeatherEnabled`/
  `liveWeatherApiKey`): fetches from Open-Meteo (free tier needs no key; a hardcoded
  `BuildConfig.OPENMETEO_API_KEY` -- baked in from a `PAPERSCRAPE_OPENMETEO_API_KEY` GitHub Secret,
  release-job-only, same pattern as the release-signing secrets -- or a user-entered key in
  Settings, user's key always wins, either just upgrades to Open-Meteo's higher-limit customer
  endpoint). Maps WMO weather codes + actual precipitation mm to `PrecipitationType`/intensity/
  cloud-cover/thunderstorm. Hourly refresh loop in `PaperWallpaperService` (checks every 2 min
  whether an hour has passed or a location just became available, only actually calls the network
  once per hour); a failed fetch keeps the previous snapshot rather than reverting. `PaperRenderer.
  liveWeatherOverride`: fully drives precipitation visible/type/intensity/thunderstorm (the
  theme's manual Rain/Snow settings aren't consulted at all while active -- that's the point of
  "live"); only blends into Clouds' *density* (not visibility) so a theme's own decision to keep
  Clouds off entirely is still respected. Theme's own rain/snow/cloud *colors* always still apply
  -- Live Weather changes what's happening, not what it looks like in this theme. Live Weather
  requires one of the two location toggles to be on first (disabled + explained in UI otherwise).
- Verified with a real `assembleDebug` + `lintDebug` (0 errors, 54 pre-existing-pattern warnings,
  none in any new/touched file) after 8 incremental rebuilds across this delivery.

## v59 — Houses/buildings/trees now placed in proper front/back depth layers

aa reported that structural scene objects (houses, buildings, trees) are placed on the hills
correctly (matching the reference's own hill-anchored convention) but visually worse-composed
than the reference app -- everything reading as flat and clustered rather than having real
depth. Compared `SceneObjectCatalog.uniformCandidates` against the reference's decompiled
`Scene.createBuildingsRanged`/`createTreesRanged` to find the actual cause.

- **Root cause**: `HOUSE`/`SKYSCRAPER`/`PARASOL` candidates were all squeezed into a
  `depthFraction` band only ~0.30 wide (`0.0-0.26` total), which is less than a third of the
  `0.0-0.375` margin actually available before `PaperRenderer.ROAD_SAFE_DEPTH_MAX` (the proven-safe
  upper bound that keeps objects from ever landing under the road). With everything crammed into
  that narrow slice, houses/buildings/trees ended up at nearly identical depth scale and ground
  line, reading as clustered/overlapping rather than composed with real foreground/background
  separation.
- **Reference comparison**: the decompiled `Scene.java` never places houses/buildings at one
  depth. `Scene.createBuildingsRanged` is called twice per theme, once with `RangeType.Top`
  (a band *behind* the tree zone, closer to the hill crest) and once with `RangeType.Bottom`
  (a band *in front of* the tree zone, closer to the road) -- `onSceneSizeChanged` computes
  `buildingRangeTop`/`buildingRangeBottom` as genuinely separate vertical spans that sandwich the
  tree zone between them, which is what produces the layered look aa pointed at in the reference
  screenshots.
- **Fix, in `SceneObject.kt`**:
  - New `generateSplitStaticCandidates` alternates candidates between a "back" depth sub-range
    and a "front" one, directly mirroring the reference's Top/Bottom building passes (density
    thinning still applies per-candidate via each slot's own stable hash, so both bands stay
    populated at any density setting instead of one draining before the other).
  - `HOUSE`/`PARASOL` now use this split (`0.15-0.23` back, `0.27-0.375` front) instead of one
    `0.12-0.26` band.
  - `SKYSCRAPER` widened to `0.0-0.15`, `TREE`/`PALM_TREE` widened to `0.10-0.375` so trees
    genuinely interleave across both house bands instead of sitting in their own narrower slice.
  - `seasonalDecorationCandidates` (snowmen/gifts/penguins/bunnies/Easter eggs/pumpkins) widened
    to match the same `0.15-0.375` band (balloons kept a bit further back, `0.05-0.30`) so they
    stay visually consistent with the widened house/tree placement.
  - All ranges stay within `ROAD_SAFE_DEPTH_MAX` (0.375) -- no change to the safe-geometry
    constants themselves, just fuller use of the margin they already allow. `RandomSceneGenerator`
    (already using the full `0..ROAD_SAFE_DEPTH_MAX` range for its random depth roll) and
    `CustomThemeData` (pure JSON [de]serialization, no hardcoded ranges) needed no changes.
- Verified with a real `assembleDebug` + `lintDebug` after provisioning a full Android SDK
  (platform 36, build-tools) and JDK 21 into the build sandbox from scratch: 0 lint errors, 52
  pre-existing warnings unrelated to this change (none in `SceneObject.kt`).

## v58 — Paper grain removed entirely: colors, performance, and a parasol shadow reinforced

aa reported the paper-grain effect still pegged CPU cores (little cores at max frequency,
visibly stuttering animations) even after v56 scoped it down to 3 elements, and -- more
fundamentally -- that colors no longer matched what was actually configured (a sky set to light
blue rendering gray on device). Asked for the effect to be removed completely rather than tuned
further, to revisit later if at all. Also re-reported parasols floating.

- **Paper grain removed, not just disabled**: deleted `PaperGrainTexture.kt` entirely, its
  drawable resource (`res/drawable-nodpi/paper_grain.png`), every `paperGrain.apply(...)` call
  in `PaperRenderer` (sky/hills/lake), and the now-unnecessary `Context` parameter `PaperRenderer`
  only needed to load that resource (its one call site in `PaperWallpaperService` updated to
  match). Every element goes back to flat, exact color fills -- no multiply blend, no
  `BitmapShader`/`Matrix` work, no per-frame clip operations for this at all. This directly
  addresses both complaints at the root rather than by further tuning: a flat fill costs nothing
  beyond the fill itself (fixing the performance report), and reads as exactly the color the user
  picked, not a blend darkened by ~45% (fixing the color-fidelity report -- a multiply blend is
  *always* going to darken/desaturate to some degree, however "subtle" it's tuned to be, which is
  fundamentally at odds with "colors should match what I configured"). This aesthetic direction
  can be revisited later if wanted, starting from the real shader-source findings already on
  record (v57's CHANGELOG entry) rather than from scratch.
- **Parasols floating (re-reported)**: re-verified the actual anchoring math numerically (the
  continuous depth system from v57) -- `groundY` for a parasol's depth range (0.12-0.26) works
  out to 0.719-0.736 of screen height, safely below the hill's guaranteed-solid line (0.688), and
  clear of the lake's own bottom edge (0.704 at most) even on Beach at max Lake Height. The
  geometry checks out; the actual issue is that the ground shadow added in v53 was too small/faint
  to read as a clear "planted in the ground" cue against a canopy this large. Widened it
  (16 -> 22 half-width) and darkened it (`0x33` -> `0x55` alpha) instead of re-deriving placement
  math that was already correct.
- Verified with a real `compileDebugKotlin` + `lintDebug` build (0 errors/warnings from either,
  confirming no leftover references to the removed class/resource anywhere in the project) --
  no `assembleDebug`, per aa's standing instruction that CI owns producing the actual installable
  APK.

## v57 — Continuous object placement, real cloud counts, and the paper-grain question settled with real shader source

Following up on aa's "decompile whatever's needed" request: found and used **CFR** (a different
Java decompiler, via `dex2jar`) after `jadx` reported "Method not decompiled" for
`Scene.addCloudsAndBalloons`/`createTreesRanged`/`createBuildingsRanged` -- CFR decompiled all
three cleanly. Also found the reference ships its actual GLSL shader source as raw asset files,
extracted directly rather than inferred.

- **The "9 rows" question, answered for real and acted on**: the reference doesn't use discrete
  rows/slots at all. `createBuildingsRanged`/`createTreesRanged` place every single object at two
  independent *continuous* fractions -- `(index - rangeStart) / (rangeEnd - rangeStart)` for
  position within whichever index sub-range that category got, and `index / totalCount` for size,
  scaled against the *entire* category's population. Replaced PaperScrape's discrete
  `layer: Int` (0..8) row system with a continuous `depthFraction: Float` (0..1) on
  `StaticSceneObject`, carried through `SceneObjectCatalog`'s per-category depth *ranges* (not
  row lists), `RandomSceneGenerator`'s random depth roll, and `SceneObjectRenderer`'s Y/scale
  math -- `LayerGeometry`'s precomputed 9-row lookup map is gone, replaced by `GroundGeometry`'s
  4 scalars plus a direct formula evaluated per object. `HILL_SAFE_ROW_MIN`/`MAX` and
  `ROAD_SAFE_ROW_LIMIT` are now `HILL_SAFE_DEPTH_MIN`/`MAX` (same derivation, continuous) and
  `ROAD_SAFE_DEPTH_MAX` (a fraction, not a row index) -- re-derived the same way, not just
  renamed. Custom themes saved before this change still load: `CustomThemeData`'s JSON parsing
  falls back from a missing `depthFraction` key to the old `layer` field, converted.
- **Clouds, ported from the real recovered formula**: the reference's actual count is
  `numClouds * 40 + 1`, and each cloud gets one of 4 depth layers in rotation (not a single shared
  depth for all of them, which is what this app's old fixed-density-count approach effectively
  did). Ported the *shape* of both -- density-scaled count, 4 depth tiers with their own
  parallax/size/vertical-offset -- but scaled the count down (`density * 20 + 1`, roughly half the
  reference's multiplier) rather than porting the literal ~41-cloud maximum: each cloud here costs
  4 `Path.op(..., UNION)` boolean operations plus a clip and an outline stroke (see
  `drawPuffyCloud`), cheap on the reference's GPU sprite pipeline, not cheap doing dozens of path
  booleans a frame on `Canvas` -- the exact same category of performance problem `PaperGrainTexture`
  hit at full per-object fidelity last release, deliberately not repeated here.
- **The paper-grain scope question, settled with the actual shader source**: extracted the
  reference's real GLSL (`res/raw/*.glsl` in the APK). `sky_shader_frag.glsl` -- used only by
  `Sky` and what extends it (`Hills`/`Water`/`Road`) -- is the *only* shader that ever samples the
  crinkle/grain texture (`gl_FragColor = v_Color * crinkle`). `standard_shader_frag.glsl`/
  `lighted_shader_frag.glsl` (mountains, houses, buildings, trees, clouds, cars) sample only each
  object's own sprite texture; the crinkle texture is referenced in a comment in both but never
  actually sampled. In other words: v56's sky/hills/lake-only scope -- reached from an indirect
  performance/correctness argument, before this shader source was known to be extractable -- turns
  out to be *exactly* architecturally correct, not a compromise. No behavior change; updated
  `PaperGrainTexture`'s doc comment to cite the real shader source instead of the indirect
  reasoning.
- Verified with a real `compileDebugKotlin` + `lintDebug` build (0 errors/warnings from either,
  including a genuine Kotlin nested-block-comment syntax error caught and fixed along the way --
  a literal `res/raw/*.glsl` inside a doc comment opens an unintended nested comment) -- no
  `assembleDebug`, per aa's standing instruction that CI owns producing the actual installable
  APK.

## v56 — Paper grain scaled back to the 3 elements it actually works for, real device bugs fixed

aa reported v55's per-element paper grain looked bad on a real device
(screenshots): a visible square "halo" around round objects (the
sun/moon, tree canopies), the sky's grain reading as an obvious, heavy
mirrored pattern rather than a subtle texture, and severe performance
problems (CPU pegged, animations stuttering).

- **Root cause of the square halos**: [PaperGrainTexture.apply]'s
  4-float overload clips to a *rectangular* bounding box, then fills
  that whole rectangle with the grain paint. For a rectangular object
  (a house) that's invisible since the box matches the silhouette
  closely enough -- but for a circular one (the sun/moon disc, a tree's
  round canopy), the box's corners are background pixels that were
  never part of the shape, and the grain paint darkened them anyway,
  showing up as a visible square behind the circle. Confirmed exactly
  in aa's screenshot (a gray square directly behind the sun).
- **Root cause of the performance regression**: v55 called `paperGrain.
  apply(...)` on every individual object -- every house, tree, car, and
  seasonal decoration, including their 3 wrap-tile copies each -- which
  on a real device could mean 50-100+ separate `canvas.save()` /
  `clipRect`-or-`clipPath` / `Matrix` update / `drawRect` / `restore()`
  sequences *every single frame*. That's cheap on the reference's actual
  environment (GPU texture sampling), not on `Canvas`'s software/
  hardware-accelerated 2D pipeline -- doing dozens of shader/clip
  operations a frame is exactly the kind of thing that pegs a CPU core
  and makes animations stutter, which is exactly what got reported.
- **Fix, not a patch**: removed `paperGrain.apply(...)` entirely from
  every individual object in `SceneObjectRenderer` (houses, trees, all
  3 building styles, cars, every seasonal decoration) and from
  mountains/clouds/the sun-moon in `PaperRenderer` -- the two problems
  above are both inherent to doing this many times a frame on this
  rendering API, not fixable by refining the per-object approach
  further. Paper grain now applies only to the 3 large background
  elements where a handful of calls covers a meaningful area: sky (1
  call), hills (up to 3, for tile-wrap coverage), the lake (3, same
  reason) -- all three already used real silhouette-shaped clips (a
  full-screen rect for sky, the actual wavy path for hills, the lake's
  own rect), so none of them had the square-halo bug to begin with.
- **Fixed the sky's "heavy, obviously mirrored pattern" complaint
  too**: the mirror-tiled source texture creates a real kaleidoscope
  symmetry a human eye reads as a *pattern*, not texture, when each
  repeated tile is large on screen -- the sky's old `repeatCount = 3f`
  made that very visible (confirmed in aa's screenshot). Raised the
  default `repeatCount` to `14f` (small enough tiles that the mirror
  symmetry stops reading as a shape) and reduced the paint's blend
  strength to about 45% (`alpha = 115`, down from full-strength
  multiply) so the whole effect reads as a subtle grain rather than a
  heavy, artificial-looking pattern -- verified with a rendered mock
  (same scene, old vs new grain settings) before touching the real
  Kotlin.
- Verified with a real `compileDebugKotlin` + `lintDebug` build (0
  errors/warnings from either) -- no `assembleDebug`, per aa's standing
  instruction that CI owns producing the actual installable APK.

## v55 — Paper grain rebuilt per-element, matching the reference's real UV mapping, not one global layer

**Delivery fix, same v55 (no app code change, no versionCode bump)**: aa
reported GitHub Actions had stopped triggering automatically since
around v53, and a separate check found v54's zip was missing every
hidden file. Root-caused to my own packaging process, not the app: an
intermediate reconstruction step used `cp -r source/* dest/` -- the
unquoted shell glob `*` silently excludes dotfiles/dotdirs (bash's
default, no `dotglob`), so `.github/` (both workflow files, including
the one that builds/releases the APK) and `.gitignore` were dropped
from the v54 zip and the working tree used to build v55, without any
error or warning. That's exactly enough to explain the report: replacing
a repo's files with a zip missing `.github/workflows/android-build.yml`
deletes that file from the repo on commit, removing the automation
entirely -- not a bug in `android-build.yml` itself (reviewed it fresh:
triggers, permissions, and the `versionCode` regex `Determine release
tag` depends on are all still correct against the current
`app/build.gradle.kts`). v53's own zip was independently confirmed
intact (built via a different, non-glob copy path). Fixed by restoring
`.github/` and `.gitignore` from that known-good v53 copy into v55's
tree, verified with a recursive diff against v53 to confirm nothing
else was missing, then a real `compileDebugKotlin` build to confirm the
restore didn't disturb anything. Also added `.kotlin/` to `.gitignore`
(a Kotlin compiler cache directory that was never listed, noticed while
already in the file) -- unrelated but harmless to fix alongside this.

aa reported the v53 paper-grain effect, applied as a single full-screen
multiply overlay, looked excessive applied uniformly across the whole
scene at once, and asked to check the reference source again and make
it match as closely as possible -- believing it's applied per element
there.

- **Confirmed from the reference's decompiled source**: `TexturedQuad.
  genTexCoordsCrinkleData()` -- the base class most models inherit from
  -- sets every model's crinkle UV coordinates to the fixed unit quad
  `(0,0)-(1,1)`. In plain terms: each object independently maps the
  *same* small grain texture across its own full extent, not a shared
  pattern tied to absolute screen position. Also found `SegmentedPlane`
  (Hills/Water's shared base class) overrides `mTextureScale = 10.0`,
  i.e. the grain repeats ~10 times across those two specific large
  elements rather than being stretched into one big blurry smear the
  way a plain 1x stretch would look on something that size.
- **Rebuilt accordingly**: removed the single full-screen overlay
  entirely. New `PaperGrainTexture` class (shared between
  `PaperRenderer` and `SceneObjectRenderer` so both apply the exact same
  texture consistently) offers two modes matching the evidence above --
  `apply(canvas, bounds)` stretches one full sample across the given
  element's own bounding box (`TileMode.CLAMP`, the default, matching
  most models' implicit `mTextureScale = 1.0`), and an optional
  `repeatCount` parameter (`TileMode.REPEAT`) for elements that need the
  grain to actually repeat across them instead of smearing -- used with
  `10f` for hills and the lake, matching `SegmentedPlane`'s own value.
  Applied per element throughout `PaperRenderer` (sky, sun/moon,
  mountains, clouds, the lake, hills) and `SceneObjectRenderer` (every
  static object type -- house, tree, snowman, gift, palm tree, parasol,
  all 3 building styles, penguin, Easter egg, bunny, balloon, pumpkin --
  plus cars), each clipped to that specific shape's own bounds/path in
  the canvas's current transform, so the grain scales and moves with the
  object exactly like a real UV-mapped texture would. Left untouched on
  purpose: birds, stars, rainbow, precipitation, the road's dashed line
  -- thin strokes or a scattering of a few pixels don't have a
  meaningful "own bounding box" to stretch a grain sample across, and
  the reference's own crinkle mapping is specifically a *fill* texture.
- `Shader.setTileModeXY()` (the natural way to swap a single shader's
  tile mode per call) needs API 31+, above this app's minSdk 26 --
  worked around with two separate pre-built shaders (one `CLAMP`, one
  `REPEAT`) and swapping which one the paint points to, which works on
  every supported version.
- Fixed a real crash risk introduced along the way: `SceneObjectRenderer.
  drawPreviewPair` (the settings screen's live theme preview) calls
  `drawHouse`/`drawTree`/`drawSkyscraper` directly, without going
  through the normal `draw()` that sets up the shared paper-grain
  instance -- an initial `lateinit var` for it would have thrown
  `UninitializedPropertyAccessException` the first time someone opened
  a theme's settings before the main wallpaper surface had drawn a
  frame. Made it nullable with safe calls instead, so the preview simply
  renders without grain rather than crashing.
- Verified with a rendered mock (grain applied to a scene's lower
  hill/mountain region only, contrasted against the sky above it, to
  confirm the effect now reads as scoped per element rather than a flat
  uniform darkening) before finalizing, then with a real
  `compileDebugKotlin` + `lintDebug` build (0 errors/warnings from
  either) -- no `assembleDebug`, per aa's standing instruction that CI
  owns producing the actual installable APK.

## v54 — The hill-cutting bug was still there: a second, different, more immediate cause

aa reported the "hills cut off, sky visible where they should be" bug
was still present, with a screenshot confirming it -- v51's fix
(switching `continuousScrollAccum` to `Double`) addressed a real but
*separate* problem (float-precision drift after very long uptime); it
was never the only cause.

- **Root cause #2, found by re-checking `drawHillLayers`'s own tiling
  math**: every other scrolling layer in this file (mountains, clouds,
  objects) draws 3 tile copies (`-1, 0, +1`) specifically so nothing
  goes uncovered as the wrap position moves -- hills never got that
  treatment, drawing exactly one copy of their own path per frame. That
  path is built to span exactly one `tileWidth` (`screenWidth * 2f`,
  see `buildBaseHillPath`), and `wrappedShift` ranges over that same
  full `tileWidth` span. Worked out the actual coverage condition: a
  single copy only covers the full screen when `wrappedShift >=
  -0.5 * screenWidth` -- just the first quarter of every wrap cycle. For
  the other three-quarters (reachable within roughly 10 minutes at the
  default scroll speed -- not an extreme edge case, and completely
  independent of the float-precision issue, which only shows up after
  a much longer uptime), the path's right edge fell short of the
  screen's right edge, leaving raw sky/background visible with nothing
  drawn there. This is almost certainly what aa's screenshot shows, and
  explains why the bug reappeared quickly in real use despite v51's fix
  being both correct and necessary for the *other* problem.
- **Fix**: hills now draw the same `-1, 0, +1` tile-copy pattern
  everything else in this file already uses, for both the fill and its
  drop shadow. Verified numerically (not just by inspection) before
  touching the real Kotlin: swept `wrappedShift` across a full cycle in
  a small script and confirmed zero screen-width gap at every sampled
  position with the fix, matching the exact failure predicted by the
  coverage condition above without it.
- Verified with a real `compileDebugKotlin` + `lintDebug` build (0
  errors/warnings from either) -- no `assembleDebug`, per aa's standing
  instruction that CI owns producing the actual installable APK.

## v53 — A real paper-grain texture, everywhere, and parasols anchored to the ground

- **Parasols floating (real bug, fixed)**: `drawHouse` already had a "foundation strip" at its
  base specifically so it wouldn't look like it was floating (its own doc comment says so
  directly); `drawParasol` never got the equivalent treatment -- just a bare thin pole with
  nothing marking where it meets the ground, which is exactly what read as floating, more so
  now that v52 made parasols noticeably bigger. Added the same kind of ground shadow (a soft
  dark ellipse) at the pole's base.
- **A genuine paper-grain texture, applied to every element, present and future**: everything in
  this file was flat-colored shapes with soft drop shadows only -- "paper-cutout" as a style
  description, never an actual paper texture the way the reference app's own sprites visibly
  have one. Cropped a clean, edge-free patch directly from the reference's own decompiled
  `land1.png` (the same blank swatch region `SpriteSheet.Sprite.hills` references), mirror-tiled
  it 2x2 into a seamlessly-repeatable 384x384 PNG (`res/drawable-nodpi/paper_grain.png`), and
  applied it as a single full-screen multiply-blended overlay drawn dead last in `draw()` --
  after the sky, every layer, every object, precipitation, everything. A single global pass
  rather than touching each of the ~30 individual draw calls in this file was a deliberate
  choice, not a shortcut: it means the texture automatically covers anything drawn before that
  point, including any future addition, with nothing new to remember to opt in by hand -- the
  same category of problem (two things that are supposed to stay in sync silently drifting
  apart) that `ROAD_SAFE_ROW_LIMIT` exists to prevent for the road/row-placement math elsewhere
  in this file. `PaperRenderer` now needs a `Context` (for `Resources.getDrawable`), so its one
  call site in `PaperWallpaperService` was updated to pass `applicationContext`.
- Verified with a rendered mock (the texture composited over a full sample scene via multiply
  blend) before touching the real Kotlin, then with a real `compileDebugKotlin` + `lintDebug`
  build (0 errors/warnings from either, including the new drawable resource resolving cleanly)
  -- no `assembleDebug`, per aa's standing instruction that CI owns producing the actual
  installable APK.

## v52 — Clouds get an outline/shading, scene objects redesigned to match reference screenshots

aa provided two real screenshots of the reference app's Christmas theme
(confirming the standing rule from v51 is being followed even where the
underlying method can't be decompiled) showing large buildings on very
few depth rows, and asked for the same here; also asked for clouds to
get some visible outline/shading instead of reading as a flat white
smear, per the same screenshots.

- **Clouds: added outline + shading**. Rebuilt `drawPuffyCloud` to union
  its 5 lobe primitives (4 circles + 1 rounded rect) into a single path
  via `Path.op(..., Path.Op.UNION)` first, rather than filling each
  separately -- necessary groundwork, since stroking 5 overlapping
  primitives individually draws ugly seams at every lobe boundary
  instead of one clean silhouette edge. Added a thin stroke in a
  darkened version of the cloud's own color (not a fixed gray, so it
  still respects the user's day/night color choice) plus a soft
  drop-shadow clipped to the cloud's own silhouette and offset slightly
  downward, using the same [shadowPaint] "soft paper-texture depth" trick
  `drawHillLayers` already relies on elsewhere in this file, for
  consistency with the rest of the scene's style.
- **Clouds: fewer, larger, single row** (following up on v51's coverage
  fix, which used 36 small candidates across 3 rows before aa's
  screenshot was available): switched to 12 larger candidates in one
  row, matching the screenshot's handful of large overlapping lobes
  much more directly. Verified with a rendered mock before touching the
  real Kotlin.
- **Scene objects: fewer, larger placement rows, matching aa's reference
  screenshots**: `Scene.createBuildingsRanged`/`createTreesRanged`
  (the methods that would give the reference's *exact* row-count/size
  formula) aren't decompilable (same "Method not decompiled" outcome as
  v51's cloud-placement method), so this is calibrated directly from the
  visual evidence aa provided rather than a recovered formula, per the
  standing rule's own fallback for exactly this situation. Skyscrapers
  went from 3 rows to 2 (0-1); houses/parasols from 2 rows to a single
  shared row (2); trees from a full 5-row spread (0-4) to a narrower
  3-row spread (1-3) so they still read as interspersed among the
  buildings rather than perfectly aligned with them. Base sizes roughly
  doubled across every category (e.g. houses 0.85 -> 1.5, skyscrapers
  0.9 -> 1.3) to match how large everything reads in the reference.
  `HILL_SAFE_ROW_MAX` re-derived again (0.49 -> 0.57) so row 3 -- now the
  deepest row any category actually uses -- keeps the same margin
  behind the road as before; `ROAD_SAFE_ROW_LIMIT` itself is unaffected
  (still 4).
- Confirmed the standing rule (decompiled source first, for every fix)
  is understood and being followed, including its documented fallback
  for methods that turn out to be undecompilable: say so explicitly and
  build from whatever adjacent evidence is recoverable -- constructors,
  field defaults, other classes' formulas, or, as with both fixes in
  this release, real reference screenshots aa provides directly.
- Verified with a rendered mock (cloud outline/shadow balance) plus a
  real `compileDebugKotlin` + `lintDebug` build (0 errors/warnings from
  either) -- no `assembleDebug`, per aa's standing instruction that CI
  owns producing the actual installable APK.

## v51 — Comment cleanup from v50 (folded in below), scroll precision, road/cloud fixes from real reference formulas

Per aa's new standing instruction, every fix below started from the
reference's decompiled source, not from reasoning about PaperScrape's own
code in isolation -- including two attempts to decompile methods that
turned out to be unrecoverable (noted where relevant, rather than
silently guessing).

- **Stale doc comment fixed** (the v50-era "whole farthest hill layer"
  wording, left over from before hills were reduced to a single layer).
  Folded into this release rather than shipped alone, per aa's
  instruction not to bump a version for a comment-only change.
- **Hills breaking apart / objects floating in open sky after prolonged
  scrolling (real bug, root-caused and fixed)**: `continuousScrollAccum`
  was a `Float`, accumulating forever by design (true one-directional
  infinite scroll, no periodic reset -- see its own doc comment for why
  an earlier version's periodic reset was reverted as strictly worse).
  A `Float32`'s per-frame increment becomes smaller than the
  accumulator's own representable precision once it reaches roughly
  10-80 thousand at typical scroll speeds -- reachable within hours to a
  couple of days of continuous uptime, not the "weeks" a previous
  version of this same doc comment claimed. Past that point, each
  layer's *different* parallax multiplier rounds the same imprecise
  value differently, so layers visibly drift out of alignment with each
  other -- hills breaking apart with objects left floating being exactly
  that, at the geometry level. Switched the accumulator to `Double`,
  deferring the precision cliff by roughly 8 more orders of magnitude,
  past any realistic uptime -- the exact same one-directional
  accumulation, just precise for far longer. (The reference itself uses
  `double` for its own accumulating time value, `SceneBase.
  timeDiffSumNano` -- consistent with this fix, though its actual scroll
  mechanism is architecturally different: each `mScrolls` object owns and
  wraps its *own* bounded `x` position every frame rather than a shared
  ever-growing value being translated, per its decompiled
  `The reference appModel.onUpdate`. Matching that architecture exactly would be
  a substantially larger rewrite; the Double fix resolves the reported
  symptom without one.)
- **Road dashed line moving too fast, unrelated to scroll speed or swipe
  (real bug, fixed)**: decompiled `Road`/`Sky.onUpdate` confirmed the
  reference advances its road surface pattern using the exact same
  shared `mParams.scrollSpeed` as everything else, not an independent
  rate. This used to advance via its own fixed `elapsedSeconds`-driven
  speed, ignoring the scroll-speed setting and swipe entirely. Now uses
  the *exact* `shiftXWrapped` value the nearest object row already
  scrolls by (identical across every row now that hills are a single
  layer), so the dashes are guaranteed to move at the same rate as
  everything else, including responding immediately to swipes.
- **Clouds turning gray as density increased (real bug, fixed)**:
  decompiled `Cloud` is a plain two-color day/night model with no
  density-dependent blending at all. PaperScrape had an explicit blend
  toward near-black gray that scaled with density -- removed.
- **Clouds not covering the sky even at 100% density (partially fixed --
  exact reference formula unrecoverable)**: the method that actually
  creates/places clouds, `Scene.addCloudsAndBalloons`, is present in the
  decompiled source but its body reports "Method not decompiled" (tried
  twice: standard and with a deobfuscation pass). Built an improved
  version from what *was* recoverable (`Cloud`'s constructor: a
  `mHeightRand`-driven vertical jitter within a band) plus a real
  reference screenshot aa provided of a fully-clouded sky: a handful of
  large lobed cloud masses overlapping into one continuous band. First
  pass (36 small candidates across 3 stacked rows) didn't match that
  screenshot; switched to 12 larger candidates in a single row, which
  does -- verified with a rendered mock against the screenshot before
  touching the real Kotlin.
- **Road too low, hidden behind the launcher dock on real devices (real
  bug, fixed)**: computed the reference's actual road position from its
  decompiled `Scene.java` geometry (`roadTopY`/`roadBottomY`, built from
  `hillsVisibleBottomY`/`treeRange`/`carRange`) -- works out to
  approximately 0.76-0.83 of screen height, notably higher up than
  PaperScrape's old 0.895-0.92. Raised `laneYFraction` to 0.79-0.805
  (`SceneObjectCatalog`) / 0.785-0.815 (`RandomSceneGenerator`) to match.
  Since rows 5-8 of the 9-row placement system go completely unused by
  every object category today, re-derived `HILL_SAFE_ROW_MAX` (0.97 ->
  0.49) so rows 0-4 -- the ones categories actually use -- compress into
  a shorter span that still comfortably clears the new, higher road
  position with the same margin proportion as before, rather than
  reducing how many rows are usable. `ROAD_SAFE_ROW_LIMIT` itself stays
  4, unchanged, as a direct result.
- **"Do the 9 placement rows match the reference, could we replicate its
  system exactly?" (answered, not changed)**: the reference's own
  `createTreesRanged`/`createBuildingsRanged` calls pass explicit index
  sub-ranges (`RangeType.Full/Top/Bottom` over `0..N`), suggesting a
  broadly similar "split a small total count of slots into ranges by
  object type" concept -- but both method *bodies* are also
  "Method not decompiled", so the exact slot count and Y-mapping formula
  aren't recoverable, and PaperScrape's existing 9-row system wasn't
  changed on the strength of a name-only match. It already reflects the
  same general idea (a handful of discrete depth slots split by category)
  the recoverable evidence points to.
- Verified with rendered mocks (cloud coverage against aa's own reference
  screenshot) plus a real `compileDebugKotlin` + `lintDebug` build (0
  errors/warnings from either) -- no `assembleDebug`, per aa's standing
  instruction that CI owns producing the actual installable APK.

## v50 — Systematic audit against the reference's decompiled source: motion bugs and geometry errors

aa reported several more issues after v49 (mountains still too narrow and
not round, hills not "harmonious", the lake needing another look,
elements staying static during infinite scroll, and the sun/moon no
longer visible during infinite scroll) and asked for a full pass against
the reference app's real source, then a systematic audit of everything
else already built against that same source. Found and fixed 6 concrete
issues, all confirmed against decompiled reference classes
(`HeavenlyBody`/`Moon`, `Road`, `Mountain`/`Scene`, `Hills`, `Water`) --
not guessed from screenshots.

- **Sun/moon permanently disappearing during infinite scroll (real bug,
  found and fixed)**: when "Scroll background" is on, `bgShift` grows
  unbounded by design (`scrollProgress` is deliberately never reset --
  see v44's own doc comment on `continuousScrollAccum`), same as every
  other parallax layer in this file. Every other layer wraps its own
  shift with `% tileWidth` before using it; this one didn't, so after
  enough uptime the sun/moon/stars drifted permanently off-screen and
  never came back. Wrapped it the same way as everything else.
- **The road's dashed center line stayed frozen during infinite scroll
  (real bug, found and fixed)**: the reference's own `Road` class
  (decompiled) doesn't move its position either (`mScrolls` unset) --
  but it does set `mTextureScroll = true`, so its surface pattern keeps
  flowing even though the road band itself is fixed. PaperScrape's dashed
  line had no equivalent animation at all, reading as a static painted
  stripe. Added a continuously advancing, wrapped offset to the dash
  pattern so it flows the same way.
- **Mountains too narrow (real bug in v49, found and fixed)**: v49 sized
  mountain width by reusing the *old* (too-tall) 0.60/0.29≈2.07
  width:height ratio and applying it to the new, correctly-measured
  height -- but that ratio itself was never re-derived from the
  reference, just carried over. The reference's real `sx`/`sy` are both
  fractions of the *same* unit (`mSizeH`, ≈ screen height in portrait),
  giving an actual ratio of 0.25/0.15≈1.67 -- converting that through
  PaperScrape's old widthFraction-of-*screen-width* convention needed a
  device-aspect-ratio conversion that was never done. Fixed by computing
  mountain width directly as a fraction of screenHeight (same as height
  already was), matching the reference's own convention exactly and
  removing the error-prone conversion step entirely. Also added
  per-candidate width jitter (reference randomizes `sx`/`sy`
  independently; this only jittered height before, so every mountain in
  a layer had identical width).
- **Mountains not rounded at the peak (found and fixed)**: re-measured
  the reference's "parabola" sprite properly this time (wide enough crop
  to avoid clipping the base) and confirmed `width ∝ √(height from peak)`
  is accurate to within ~1-2% at every sampled point -- the curve
  formula was never the problem. The actual issue: sampling that curve
  at points evenly spaced by *height* (the old `t = i/segments`), when
  `√t` has infinite slope at `t=0` -- width changes fastest exactly at
  the peak, where evenly-height-spaced sampling places its sparsest
  points, producing a visibly faceted "shoulder" once mountains got
  smaller (v49). Fixed by sampling evenly spaced in *width* instead
  (`t = x²`, `√t`'s own inverse), which naturally bunches samples near
  the peak where the curve bends fastest; segment count also raised
  8 → 16. Verified with a rendered side-by-side mock before touching the
  real Kotlin.
- **Hills "not harmonious" (redesigned)**: replaced the per-segment
  independent-random-plus-bezier approach (even after v49 narrowed its
  range) with an actual sine wave, matching the reference's real `Hills`
  class exactly (`getHeightData() = (1-amp) + amp*sin(f*4π)`) -- a
  perfectly smooth, perfectly periodic wave rather than an approximation
  built from independent random samples, which can still land two
  adjacent rolls asymmetrically and read as an irregular bump no matter
  how narrow the range. `hillsVariation` now scales the sine's amplitude
  directly (reference's own `ampNormalized` is user-adjustable the same
  way). Reproduces the exact same `[0.04, 0.22]` bounds at
  `variation=1`, so `HILL_SAFE_ROW_MIN`/`MAX` needed no changes.
- **Lake could swallow the now-smaller mountains (found and fixed)**:
  v49 shrank mountains to match the reference's real (much smaller)
  proportions but left the lake's max band-height cap untouched. At max
  Lake Height, the lake's top could reach 0.504 of screen height while
  even the *tallest* possible back-mountain candidate only reached
  ~0.5165 -- the lake could cover every mountain on screen at high
  settings. Capped the band height at 0.16 of screen height (down from
  0.20), keeping the lake's highest possible top safely below the
  worst-case mountain peak with margin.
- **Audited but left alone, by design**: clouds (already move via their
  own drift + parallax, matching `Cloud.java`'s independent-motion
  model), cars (already progress-based and scroll-independent, matching
  `Car.java`), and the lake's motion (reference also keeps `Water`'s
  *position* fixed and only animates surface texture -- same effect as
  PaperScrape's animated ripple lines, different mechanism, not a bug).
  Two real *differences* from the reference were found and intentionally
  **not** ported, since they're deliberate PaperScrape design choices
  aa asked to keep the aesthetic style for, not bugs: the reference's
  sun/moon move only vertically at fixed left/right horizontal positions
  (PaperScrape sweeps them across the whole sky, which is the existing,
  deliberate look); the reference's sky has a 4th color for a
  sunrise/sunset horizon glow that PaperScrape doesn't have (a feature
  gap, not a bug -- flagged for aa to decide on, not added unprompted).
- Verified with rendered Python mocks (mountain peak rounding
  side-by-side, and the combined new-mountains + sine-hills + revised
  lake scene) before touching the real Kotlin, then with a real
  `compileDebugKotlin` + `lintDebug` build (0 errors/warnings).

## v49 — Hills/mountains resized and reduced to match the reference app's real proportions

v48 fixed the gap and the transparency, but a fresh comparison against the
reference app's own decompiled source (confirmed on-device) showed
PaperScrape's mountains and hills were still much taller and more jagged
than the reference's low, gentle scenery, and the hills still visibly read
as 3 stacked bands of color rather than one cohesive hillside -- even
though only one day color and one night color have ever been
user-editable for hills. All three fixed by measuring and porting the
reference's own real numbers, not by eyeballing new ones.

- **Hills: 3 layers -> 1**, matching the reference's own `Hills` class,
  which is a single silhouette with a single scroll rate. `hillLayerColor()`
  used to darken each of 3 stacked bands by an extra 12%/24% toward black
  from the one user-picked color -- with only one layer now, that darkening
  is a no-op, so the rendered hill is genuinely the single color it was
  always supposed to be (matches what the "only one day/night color is
  editable" already implied, but wasn't what got drawn). The 9 object
  placement rows (houses/trees/lamps/road etc.) still exist, spread across
  this one layer's band instead of 3 -- `ROWS_PER_LAYER` went from 3 to 9,
  `layerCount` from 3 to 1, so `TOTAL_ROWS` (9) is unchanged and every row
  still lands in the same place relative to the road (`ROAD_SAFE_ROW_LIMIT`
  stays 4, re-derived and confirmed against the new geometry, not just left
  alone).
- **Hills: shorter and gentler**, matching the reference's real numbers
  (decompiled `Scene.java`): its hill's total height defaults to ~42.5% of
  screen height (`hillsHeightMin/Max` interpolated by a height parameter),
  and its wavy top edge -- a sine wave, not independent per-segment
  randomness -- only ever swings within about the top 12% of that height by
  default (`getHeightData()`'s amplitude term). PaperScrape's hill is now a
  single 40%-of-screen-height band, with `buildBaseHillPath`'s random range
  narrowed from the old `[0.15, 0.75]` (a wild 60%-of-height swing) to
  `[0.04, 0.22]` -- close to the reference's own proportions, without
  rewriting the underlying bezier-segment approach itself. `HILL_SAFE_ROW_MIN`
  (0.78 -> 0.26) and `MAX` (0.95 -> 0.97) re-derived from that new range the
  same way the doc comment on those constants has always required.
- **Mountains: resized from the reference's own object-creation code**
  (`Scene.java`'s `Mountain` construction), not just its sprite shape (which
  v47 already got right): its back mountains average ~15% of screen height
  tall, front ones ~10.5% -- both far smaller than PaperScrape's old
  29%/34%. `peakHeightFraction` updated to match (0.29 -> 0.15 back,
  0.34 -> 0.105 front), `widthFraction` scaled down by the same ratio
  (0.60 -> 0.31, 0.70 -> 0.216) to keep `drawSoftMountain`'s already-correct
  width:height shape intact.
- Verified with a rendered Python mock of the new geometry (hill band,
  mountain sizes, and a sanity check of where the 9 object rows land
  relative to the road line) before touching the real Kotlin, then with a
  real `compileDebugKotlin` + `lintDebug` build (0 errors/warnings from
  either). Per aa's instruction from this point on: CI (see
  `.github/workflows/android-build.yml`) builds and publishes the actual
  installable APK -- this and future entries stop short of a local
  `assembleDebug`.

## v48 — Found the actual root cause in the reference app's decompiled source, not another guess

v47 fixed the mountain shape and the lake's jitter, but a fresh report showed
the underlying bug was still there: sky visible between hills and mountains
(worse with the lake off), mountains still reading as floating, and the back
mountain layer visibly see-through with the sun showing behind it. This time,
decompiled the full reference APK (`jadx`, both the standalone APK and the
one inside the provided device backup — same build) instead of reasoning
about the geometry from PaperScrape's own code alone, and found the exact
formula its `Mountain`/`Hills`/`Water`/`Scene` classes use.

- **Root cause, found in the reference's real source**: `Mountain.java`
  positions each mountain at `mScene.mountainBottomY`, and
  `Scene.setupSceneParams()` defines `mountainBottomY = max(hillsVisibleBottomY,
  waterVisibleTopY if lakes are on)` — where `hillsVisibleBottomY` is
  deliberately the hill's own *worst-case-covered* line (the deepest point
  its randomized top edge can ever reach), not its peak. PaperScrape's own
  `drawMountains()`/`lakeTopBottomY()` anchored to the opposite extreme: the
  shallowest, *best-case* peak fraction (`0.15`) from `buildBaseHillPath`'s
  random range — a point the wavy hill edge only reaches at a couple of x
  positions per screen. Anchoring the fixed mountain/lake base line there
  left a real gap of bare sky beneath it at almost every other x, which also
  reads as "mountains floating" since their base visibly doesn't touch
  anything solid most of the time.
- **Fix reuses an already-proven constant instead of inventing a new one**:
  PaperScrape already had exactly the right "always-solid regardless of the
  random roll" fraction derived and verified for a different purpose —
  `HILL_SAFE_ROW_MIN` (0.78), used to keep placed objects from floating above
  the hill silhouette. `drawMountains()` and `lakeTopBottomY()` now both
  anchor to `hillGuaranteedTopFraction = yOffsets[0] + heightFractions[0] *
  HILL_SAFE_ROW_MIN` — the same concept as the reference's
  `hillsVisibleBottomY`, expressed in PaperScrape's own existing terms.
  Verified with a rendered Python mock of both the old and new anchor
  (reproducing `buildBaseHillPath`'s exact algorithm) before touching the
  real Kotlin, per this project's usual practice of not trusting the math
  alone.
- **Fixed the back mountain layer's transparency** (the reported "sun
  visible behind the mountains" bug): it was rendering at `alpha = 200` as a
  cheap depth cue between the two mountain layers, which isn't how the
  reference app does it (its own `Mountain` model is a plain, fully opaque
  solid shape — no alpha blending). With the sun/moon drawn *behind*
  mountains in z-order, that partial transparency let them visibly bleed
  through. Both mountain layers are now fully opaque (`alpha = 255`); depth
  between them is already communicated by their independently editable
  colors, sizes, and positions.
- **Not changed in this pass**: the 3-layer hill depth system itself (the
  reference app has only one hill silhouette, no depth layering) — that's a
  deliberately-built PaperScrape feature (per-layer day/night color,
  density, variation, and the whole object row-placement system are built
  on it), not a bug, so it wasn't touched without a separate explicit
  decision to redesign it. The reported "hills look like 3 disconnected
  overlapping colors" may already read very differently now that the
  topmost layer no longer floats detached from the mountains/lake above it
  — pending confirmation before deciding whether it still needs its own
  pass.

## v47 — Went back to the reference app's real game assets, not just its code

v46 wasn't good enough -- confirmed with fresh screenshots showing a
visible gap between mountains and the lake, and mountains still reading
as cluttered/noisy rather than the reference's clean large mounds. This
time, extracted and measured the reference app's own actual mountain
sprite from its full APK (also provided) rather than continuing to guess
proportions from screenshots alone.

- **Found and measured the reference's real "parabola" mountain
  sprite.** Its texture atlas (`land1.png`) has a grayscale mask shape
  (color-tinted at runtime) for the mountain. Sampled its width at 10
  heights from peak to base and confirmed it follows a genuine parabola
  (`width ∝ √(height from peak)`) at close to 1:1 width:height
  proportions -- both the curve *shape* and the *proportions* were wrong
  in every previous version (v37's twin-quadTo peak, v46's cubic dome
  were both closer to uniform curves at roughly 2:1 flat/wide
  proportions). Rebuilt `drawSoftMountain()` by sampling real points
  along that measured sqrt curve directly, instead of guessing at more
  bezier control points.
- **Found the real cause of the lingering gap**: the lake's wavy top
  edge (added back when it still needed to blend against overlapping
  hills) has its own per-segment random jitter, which doesn't match the
  mountains' single fixed anchor point -- at some x positions the wave
  dipped below that fixed value, opening a thin sliver of sky. That
  waviness stopped serving any purpose once v46 moved the lake to sit
  above the hills entirely (bordering plain sky, not hills, on top), so
  reverted the lake to a plain flat rectangle -- which lines up with the
  mountains' fixed base exactly, everywhere, with no jitter to
  disagree with.
- **Reduced clutter**: mountain candidate count cut from 7 to 4 per
  layer, with each one substantially widened (0.34-0.40 -> 0.60-0.70 of
  screen width) to match the reference's "2-3 large, clearly separated
  mounds" look -- the reference's own mountain-count formula produces a
  similar *count* to what PaperScrape had, so the real difference was
  never count, it was that its mountains are wide enough that only 2-3
  are ever on screen at once, not 6-8 small overlapping ones.
- **Verified the composed scene, not just each piece alone**: built a
  rendered mock of mountains + lake + all 3 hill layers together (not
  mountains or lake in isolation) across 4 different random seeds and
  lake heights before writing the real Kotlin -- specifically because
  v46's isolated per-element checks didn't catch how the pieces actually
  looked stacked together with real hill silhouettes underneath.
- Also removed the `lakePath` field, unused now that the lake is a plain
  `drawRect` again.
- Verified with real builds: `assembleDebug` and `lint` both pass
  cleanly.

## v46 — Mountains/lake/hills layering, redone from scratch

Explicitly requested as "redo this whole thing from zero, as close to
the reference app as possible" -- v45's fix was real progress but built
on an assumption that turned out wrong once actually rendered, not just
reasoned about on paper.

- **v45's mental model, checked against an actual rendering, was
  wrong.** v45 made mountains dynamically reach down to the lake's real
  top edge, assuming hills (drawn last) would then let water peek
  through their own wavy silhouette's "valleys". Built a small standalone
  script rendering the exact same geometry as an SVG (same hill-path
  formula, same random seed logic, same lake/mountain math) to actually
  *see* the result before shipping it again -- and the lake was almost
  completely hidden. Root cause: PaperScrape's hills are 3 *stacked*
  layers that together form a largely opaque mass from about 55% of
  screen height down to the bottom, not a single thin organic line --
  there was very little room behind them for anything to show through
  except in rare, narrow alignments.
- **Redone based on what the reference app's own screenshots actually
  show**: mountains and lake sit *entirely above* where hills begin at
  all, in what would otherwise be plain sky. Lake's bottom edge is fixed
  just past the farthest hill layer's own absolute worst-case peak
  (0.551 of screen height, from `buildBaseHillPath`'s own random range)
  -- comfortably clear, so it's always fully, cleanly visible as its own
  band, exactly like the reference screenshots, instead of fighting for
  visibility behind the hill mass. Mountains anchor to that same
  reference point, connecting directly to the lake's top edge (or
  straight to the hills, when the lake is off) with provably no gap.
  Verified this new geometry the same way -- rendered mock, not just
  math -- across 2 different random seeds and 4 different Lake Height
  settings (0%, 30%, 70%, 100%) before writing a single line of the
  real Kotlin.
- **Mountain shape rerounded and widened**, per explicit feedback that
  the previous silhouette (two `quadTo` curves meeting at a sharp
  vertex) read as too pointy/narrow next to the reference's soft,
  harmonious mounds. Replaced with a pair of cubic Bezier curves forming
  a proper dome (no sharp peak at all) and widened both layers
  (0.24/0.28 -> 0.34/0.40 of screen width).
- Draw order confirmed correct: mountains (farthest) -> lake -> hills
  (nearest, drawn last, painted on top of everything behind it) --
  "hills in front, mountains and water behind", matching what was
  explicitly asked for, verified against the reference app's own
  `Scene.java` depth-layer ordering array in an earlier session and now
  actually producing the intended visual result.
- Simplified `lakeTopBottomY()` in the process -- no longer needs the
  object-placement-safety margin subtraction the old version carried,
  since hills/objects (drawn after the lake now) naturally take
  precedence over it visually wherever they overlap; that concern is
  now moot rather than something to route around.
- Verified with real builds: `assembleDebug` and `lint` both pass
  cleanly after the full change; a follow-up grep confirmed no leftover
  references to the removed formulas.

## v45 — The real "mountains behind water" architecture fix, before Phase 1d

Explicitly requested to be done now, not deferred: went back to the
reference app's decompiled source specifically for `Scene.java`'s water/
mountain geometry (not just `Water.java`, which turned out to just be a
`Sky` subclass) to find the actual mechanism, rather than continuing to
patch the symptom with fixed-margin guesses.

- **Found the real architecture in the reference source**:
  `this.mountainBottomY = Math.max(this.hillsVisibleBottomY,
  this.mParams.objLakes ? this.waterVisibleTopY : -this.mHalfHeight);`
  -- mountains' base isn't a fixed guess there either; it's *dynamically*
  computed every frame as the max of the hills' own reference point and
  the water's *actual current* top edge (only when lakes are enabled).
  This is the piece PaperScrape was missing: v44's fix (increasing the
  lake's max coverage to 0.22) only worked for lake heights near the
  slider's upper end -- at any other Lake Height value, mountains and
  the lake's actual position could still drift apart, reopening the same
  gap the fix was meant to close.
- **Fixed at the root, not patched further**: added `lakeTopBottomY()`,
  a single shared calculation of the lake's real current top/bottom (in
  pixels) that both `drawLake()` and `drawMountains()` now call --
  `drawMountains()`'s base Y is now `max(farthest-hill-reference,
  lake-top)` when the lake is visible, exactly mirroring the reference's
  own `Math.max()` relationship. This is correct at *every* Lake Height
  setting, not just the one value the old fixed-margin version happened
  to be tuned against, since it reads where the water actually is rather
  than assuming it.
- Draw order unchanged (mountains → hills → lake, lake drawn last) --
  since mountains now correctly extend down to at least the lake's real
  top, the lake (opaque, on top) naturally covers whatever mountain/hill
  pixels fall inside its own band, with no gap and no overlap, matching
  "mountains behind the water" without needing to reorder anything.
- **Also updated `ROADMAP.md` with an honest caveat**: the broader "sea
  vs. lake" mode (hills never appearing above/behind water at all, e.g.
  for Beach) is still open, but re-reading the reference's own
  `waterVisibleTopY` formula suggests *some* hill showing above a small
  lake may be inherent to how the reference itself behaves too (it only
  fully submerges hill peaks at high Lake Height values) -- flagged as
  worth confirming with fresh screenshots at a few different height
  settings before assuming more work is needed there, rather than
  assuming the remaining visible-hill cases are automatically bugs.
- Verified with a real build: `assembleDebug` and `lint` both pass
  cleanly.

## v44 — Found and fixed a bug I introduced myself, plus 2 more real bugs

Reported with screenshots right after v43. The most important one here
is a genuine regression traced back to my own "safety net" reasoning in
v42, which turned out to be actively wrong.

- **Infinite scroll "jumping"/resetting periodically -- caused by my own
  v42 fix.** v42 added `continuousScrollAccum %= 2f` as what I described
  as a "purely cosmetic, visually identical" precision safety net,
  reasoning that every consumer already computes its own
  `shiftX % tileWidth` downstream so resetting the shared accumulator at
  the same period wouldn't be visible. That reasoning was wrong: it's
  only seamless for a layer whose *own* parallax factor happens to equal
  exactly `1.0`, since only then does the accumulator's 2.0-unit wrap
  period correspond to that specific layer's actual `tileWidth` in
  pixels. Every other layer -- hills at `0.15`/`0.35`/`0.6`, mountains/
  clouds at `0.04`-`0.08`, the lake at `0.25` -- has a *different*
  effective wrap period in pixels, so force-resetting the shared
  accumulator made each of them jump by a different, nonzero amount at
  the same instant: a visible, synchronized "the whole scene just reset"
  glitch every couple of minutes at typical scroll speeds. Removed the
  wrap entirely -- each layer's own `% tileWidth` already wraps correctly
  and seamlessly at any input magnitude, so the shared accumulator never
  needed an artificial reset in the first place. A plain `Float` has more
  than enough precision headroom for this to stay smooth across weeks of
  continuous uptime regardless.
- **Hills visibly poking above the water, with a gap of bare sky in
  between** (confirmed with screenshots -- the reported "mountains
  behind the water, hills only in front" ordering, matching the
  reference app, is the *real* fix and is a bigger architectural item,
  tracked in `ROADMAP.md`'s Phase 5 "sea vs lake" entry). Applied a
  concrete, immediate mitigation for the specific visible symptom: the
  lake's vertical coverage was too shallow to reach the farthest hill
  layer's own worst-case (highest) peak -- 0.14 of screen height topped
  out short of that peak by a visible margin. Increased to 0.22, which
  comfortably clears it with room to spare for the wavy top edge's own
  jitter on top.
- **Clouds still not forming a solid cover at 100% density, per
  reference screenshots showing a dense, dark, storm-like ceiling of
  overlapping cloud.** Two changes: (1) went from 16 candidates with a
  wide random position jitter to 28 with a much smaller jitter -- evenly
  spaced, densely packed candidates guarantee full coverage once enough
  of them are enabled, where the previous wide randomization could leave
  visible gaps purely by chance even at maximum density; (2) clouds now
  visibly darken toward a near-black gray as density increases (blended
  on top of whatever day/night color the user picked), so a high setting
  reads as genuine storm/blizzard cover, not just "more of the same
  light puffy clouds."
- Also added to `ROADMAP.md`'s Phase 5 (not implemented this version):
  an overall "more convincingly paper-cutout" aesthetic pass across
  every element (not just the specific items already tracked), and a
  real visual treatment for thunder (currently just a plain white
  full-screen flash, no bolt shape).
- Verified with a real build after all three fixes: `assembleDebug` and
  `lint` both pass cleanly.

## v43 — Lake wasn't scrolling at all, unlike everything else

Reported right after v42: scrolling now works, but "buildings feel tied
to the ground, everything else feels almost frozen while the terrain
moves under it."

- **Root cause found**: `drawLake()` never referenced `scrollProgress`
  at all -- it drew its water band, ripples, and (indirectly) the
  boats/dolphins within it at fixed absolute screen coordinates,
  completely independent of any scroll. Every *other* background system
  (`drawHillLayers`, `drawMountains`, `drawClouds`) already correctly
  incorporated `scrollProgress` with its own parallax factor; the lake
  was simply missed when it was first built.
- **Fixed**: the lake now scrolls with a parallax factor of `0.25`
  (between the mid and near hill layers' own `0.35`/`0.6`, roughly
  matching where it sits vertically among them). Since the water band
  previously spanned exactly one screen width at a fixed position,
  making it scroll meant it needed to *tile* to avoid exposing gaps at
  the edges -- refactored the band-drawing logic into `drawLakeBand()`
  (one screen-width copy, parameterized by an x-offset) and call it 3
  times side by side under one `canvas.translate()`, the same "wrap +
  redraw neighboring tiles" shape already used for mountains/clouds.
- Sailboats and dolphins were *not* changed -- they already have their
  own independent drift animation (a boat sailing, a dolphin swimming),
  so they were never the "frozen" element the report described; only
  the water itself was static.
- Rainbow was checked and deliberately left alone: it's a fixed
  atmospheric arc centered on the viewport, not a "thing in the world"
  the way terrain or a lake is -- real rainbows don't have a scrollable
  position either, so this isn't the same bug.
- Verified with real builds: two real compile errors caught and fixed
  along the way (a leftover duplicate line from the refactor causing a
  brace mismatch and a cascade of "unresolved reference" errors, and an
  `Int`/`Float` type mismatch passing a tile offset) -- `assembleDebug`
  and `lint` both pass cleanly after.

## v42 — 6 real bugs reported after v41, root-caused against reference source

Fixed 5, thoroughly diagnosed the 6th (confirmed not a code bug). Started
this session by re-establishing context from the previous conversation's
handoff (`RIEPILOGO_SESSIONE.md`, `ROADMAP.md`, and the `v41` zip), then
went bug-by-bug rather than assuming any prior fix's reasoning was still
correct once new evidence contradicted it.

- **Infinite scroll went both directions; needed to be one-way only,
  matching the reference app.** v40's fix for the hills/objects desync
  bug worked by bounding the continuous auto-scroll to a back-and-forth
  oscillation, specifically because objects wrapped on a *different*,
  narrower period (1x screen width) than the hills they sit on (2x
  screen width) -- two moduli on the same growing value only agree
  within a bounded range. Re-read the reference app's decompiled source
  more carefully this time: its objects don't use a separate narrower
  wrap at all. Root-caused why PaperScrape's did: an even *earlier*
  version had objects sharing the hills' wide period already, and
  narrowed it specifically to fix "only half the candidates visible at
  rest" -- a real problem, but only because density wasn't
  user-adjustable yet at the time. Every category has had its own
  density slider for a long time now, so "roughly half of a wide-period
  set visible at once" is just normal tiled-scene behavior today, not a
  bug. Removed the separate narrow wrap entirely; objects now share the
  *exact* same `tileWidth`/wrapped shift as their hill layer. This makes
  the two provably impossible to desync at *any* scroll magnitude, not
  just within a bounded window -- which finally allowed implementing
  genuine, unbounded, one-directional continuous scroll (replacing the
  sine-based sway) without reopening the bug it was working around.
  Also removed the now-unnecessary `parallaxStrength` cap that existed
  solely for that same bounded-range argument. Kept one small
  precision safety net: the accumulator wraps every 2 scroll-units
  (exactly one hill/object tile period) so it never grows unbounded
  across days/weeks of uptime -- provably invisible, since every
  consumer already computes its own `% tileWidth` downstream regardless
  of the raw accumulator's magnitude.
- **Trees always rendering as palm trees, even on Christmas --
  investigated thoroughly, confirmed not a code bug.** Traced the full
  path: theme→tree-type selection (`builtinLayoutFor`'s `"christmas"`
  branch correctly omits any `treeType` override, defaulting to plain
  `TREE`), the draw dispatch (`SceneObjectType.TREE` correctly routes to
  `drawTree`, not `drawPalmTree`), and the JSON round-trip
  (`SceneObjectType.valueOf(json.getString("type"))` -- name-based, safe
  against any enum reordering). All three confirmed correct. Likely
  cause: `CustomThemeRegistry.overrideLayoutFor()` returns a **saved**
  theme override's frozen `SceneObjectLayout` unconditionally, ahead of
  `builtinLayoutFor()` -- if "christmas" (or any theme) was ever
  overridden via "Manage Themes" while palm trees were showing for any
  reason, that snapshot is frozen forever regardless of later code
  fixes. This exact trade-off was already identified and mitigated in
  an earlier session (`CustomThemeStore.clearAllOverrides()` exists
  specifically for it, its own doc comment describes this scenario
  precisely). No code change made here since none was warranted --
  pointed the user at "Manage Themes" to check for and clear a stale
  override instead of "fixing" code that was already correct.
- **Lake appeared to slice through hills instead of naturally covering
  them.** The lake's opaque rectangle *is* drawn after (on top of)
  every hill layer, so mathematically nothing should show through it --
  but a hard, perfectly flat top edge cutting across the hills' own
  organic wavy silhouette read as an unnatural slice regardless, purely
  as a shape-mismatch problem, not a z-order/coverage one. Gave the
  lake's top edge (the one facing the hills behind it) a gentle wavy
  contour instead of a straight line, using the same per-theme-seeded
  jitter style as the hill/mountain silhouettes -- the near (bottom)
  edge stays a hard, crisp line on purpose, since that's the near
  shoreline in front of everything, where a clean edge is correct.
- **Clouds too small, still, even after the count/density fix.**
  Doubled the base radius (22 -> 45); the earlier fix increased *how
  many* clouds render and let density also affect size, but the
  starting size itself was never revisited.
- **Precipitation fell from empty sky above the clouds, not from the
  clouds themselves.** Root cause: the fall range started at a fixed
  `y = -40` (just above the very top of the screen), entirely
  independent of wherever the cloud layer actually sits (`sky
  .sunCloudHeight`-derived). Both rain and snow now start their fall
  range at that same cloud band instead.
- **Snow fell noticeably faster than real snow drifts.** `fallSpeed`
  for snow was `0.35` (a full screen-height fall in well under 3
  seconds) -- slowed to `0.09`, roughly a 4x reduction, reading as a
  gentle drift instead of a downpour.
- Verified with real builds after the full set of fixes: `assembleDebug`
  and `lint` both pass cleanly.

## v41 — Phase 1, point 1c: Precipitation and Rainbow

Both built following the exact same per-theme customization pattern as
every other Scene Objects category (Clouds/Stars/Sky before it): a data
class on `SceneCustomization`, DataStore keys + read/write in
`WallpaperPrefs`, JSON (de)serialization in `CustomThemeData`, and a
menu row + sub-dialog in `SettingsScreen`. Verified with a real
`assembleDebug` + `lint` (see the CI/local build note at the bottom of
this entry) after the SDK/JDK had to be provisioned into the build
environment first (a fresh sandbox with neither installed).

- **Precipitation** (`PrecipitationConfig`): rain or snow (mutually
  exclusive `type`, matching real weather), show/hide, intensity, and --
  deliberately -- two fully independent color pairs (`rainColorDay`/
  `Night` vs `snowColorDay`/`Night`) rather than one shared pair, so
  switching type doesn't force re-picking a color that made sense for
  the other one.
  - Rendered with the same stateless deterministic-candidate approach as
    Clouds/Birds (`drawPrecipitation`): each drop/flake's fall position
    is purely a function of elapsed time, wrapping smoothly top-to-
    bottom -- no per-particle list to spawn/prune every frame. Rain
    draws as fast diagonal streaks; snow as small circles with a gentle
    horizontal sway.
  - Drawn dead last in the frame, after every other layer including
    houses/cars/fireworks/Santa -- real precipitation reads as the
    closest thing to the "camera" in the whole scene, not part of the
    backdrop.
  - **Thunderstorm** toggle (only meaningful while Rain is selected):
    occasional full-screen lightning flash. Implemented as a small
    self-contained timer/fade directly on `PaperRenderer` (two `var`s),
    not a new `Effect` class like `FireworkEffect` -- a single global
    overlay with one number to fade didn't earn a whole new file the way
    `FireworkEffect`'s actual particle pool did.
- **Rainbow** (`RainbowConfig`): a 7-band decorative arc, show/hide +
  opacity. Deliberately *not* tied to the Precipitation toggle -- unlike
  real weather (which Phase 1d's Random/Live Weather will eventually
  simulate), this stays a manual per-theme toggle so a user can put a
  rainbow on a sunny theme without turning rain on first, the same
  freedom every other decorative category already has. Fades toward
  night the same way stars fade in, rather than a hard cut, since real
  rainbows are a daylight phenomenon.
  - Anchored to the exact same base-Y fraction `drawMountains` already
    derives its own base from (`yOffsets[0] + heightFractions[0] *
    HILL_SAFE_ROW_MIN`), and drawn *before* mountains/hills in the frame
    so their silhouettes naturally occlude the rainbow's base -- it
    visually "grows" out of the same horizon band mountains sit on,
    rather than floating in front of the terrain.
- Per-theme defaults: both stay off by default (same opt-in philosophy
  as the lake), but Winter/Christmas/Tundra pre-set `type = SNOW` so
  turning precipitation on for the first time on those themes starts
  from the obviously-correct choice instead of Rain.
- New Scene Objects menu rows: "🌧️ Precipitation", "🌈 Rainbow".
- **Build environment note**: the sandbox this delivery was built in had
  neither the Android SDK nor a full JDK (only a JRE, no `javac`)
  preinstalled. Downloaded and provisioned `cmdline-tools`, accepted
  licenses, installed `platform-tools`/`platforms;android-36`/
  `build-tools;36.0.0`, and installed `openjdk-21-jdk-headless` (the JRE
  alone lacks `javac`, which AGP 9's toolchain resolution requires even
  though Kotlin compilation itself doesn't need it) before `./gradlew
  assembleDebug`/`lint` would run at all. Both completed successfully;
  `lint`'s ~5 findings are all pre-existing, project-level issues
  (`UseKtx`, `OldTargetApi`, `GradleDependency`, etc.) confirmed via
  `grep` to not reference any of the 5 files this delivery touched.

## v40

Six real bugs fixed, all reported right after v39 shipped, before moving
on to Phase 1's remaining points (1c/1d). One roadmap-only addition
(object size harmonization, Phase 5).

- **"Reset everything to defaults" (Scene Objects menu) did nothing.**
  Root cause: `resetAllCategories()` only cleared the in-progress scratch
  edit (`pendingCustomization`), but `resolveActiveCustomization()`
  checks a *saved* override for the current theme before that scratch
  space -- so if the active theme had ever been overridden via "Manage
  Themes", the saved override kept winning regardless of what reset just
  cleared, and the button appeared to do nothing. Fixed: reset now also
  clears any saved override for the current theme
  (`customThemeStore.clearOverride`), not just the scratch space.
- **"Scroll Background" was built as the wrong mechanism entirely.**
  Confirmed against a reference app's decompiled source: its
  `scrollSpeed` multiplies a per-frame *time delta*
  (`onUpdate(float f)`, a classic game-loop pattern) -- a genuinely
  different, continuous, always-on mechanism from PaperScrape's existing
  swipe-driven `parallaxStrength`, which v36 had mistakenly relabeled
  "Scroll speed" as if they were the same thing. Reverted that mislabel
  and added a real, new `scrollSpeed` setting driving constant motion,
  independent of swiping.
  - **Caught and fixed a serious follow-on bug before shipping it**: an
    unbounded, monotonically-growing auto-scroll would eventually exceed
    the ±1 screen-width bound v37's hills/objects-desync fix relied on
    (that fix worked specifically because swipe's `homeScreenOffset` is
    naturally bounded to `[0,1]`) -- silently reintroducing the exact
    "mountains/houses drift away from the terrain" bug, just delayed by
    elapsed time instead of swipe distance. True one-directional infinite
    scroll would need actual seamless procedural terrain regeneration, a
    real feature of its own, not a quick fix here. Implemented instead as
    a smooth, perpetual back-and-forth sway (`sin`-based), clamped
    together with the swipe contribution to *provably* never exceed the
    same safe bound -- continuous, always-moving scenery, without
    reopening a fixed bug. Documented as a deliberate, reasoned scope
    limitation, not silently shipped as something other than what was
    asked for.
- **In-app changelog only ever showed the latest release's notes.**
  Updating from v36 to v38 showed nothing about what v37 changed.
  `UpdateChecker` now calls GitHub's release *list* endpoint instead of
  `/releases/latest`, finds every release newer than the installed
  version, and concatenates all of their notes (newest first) into one
  combined `releaseNotes` string, capped defensively at 6000 characters.
  Also increased the update dialog's scrollable area (220dp -> 340dp) to
  comfortably fit multiple versions' worth of notes.
- **Clouds at 100% density still looked sparse, sky nowhere near
  covered.** Root cause: only 5 candidate cloud slots spread across a
  2-screen-wide tiling period (of which roughly half is on-screen at
  once) -- meaning at most 2-3 clouds were ever visible even at maximum
  density. Increased to 16 candidates; cloud size now also scales up
  with the density setting (not just count), so a high setting reads as
  genuinely overcast rather than just more small clouds spread further
  apart.
- **Fresh-install themes were indistinguishable from each other.** Hill
  colors turned out to already be well-differentiated per theme (e.g.
  Christmas's hills were already an authored near-white/snowy tone) --
  the actual gap was in Phase 0's newer categories (lake, mountains),
  which used the *same* uniform defaults regardless of theme. Added a
  first, deliberately-quick pass (not the full review already tracked in
  `ROADMAP.md`'s Phase 5): Beach's lake now defaults on, wide, and
  water-colored with sailboats/dolphins (reading as a sea) with
  mountains off; Christmas/Winter/Tundra get snow-capped mountain
  colors; Desert gets sandy mountain tones and no lake; City gets fewer
  birds and no mountains.
- Verified with real builds throughout: `assembleDebug` was run after
  each fix individually (the reset fix, the scroll redesign -- twice,
  once before and once after catching the unbounded-drift issue --, the
  changelog rewrite, the cloud density fix, and the per-theme defaults),
  plus a final `lint` pass across the complete set.

## v39 — in progress

Roadmap Phase 1, points 1a and 1b -- delivered together as one chunk (per
explicit request: smaller pieces than the Phase 0 monolith, but 1a+1b
were judged small enough to do as a pair without a big rewrite of the
rest of the project). 1c (precipitation/rainbow) and 1d (weather) remain
unstarted.

- **1a: Stars, Sky, Sun, Moon, all per-theme.** New `StarsConfig`/
  `SkyConfig`/`SunConfig`/`MoonConfig` on `SceneCustomization`, following
  the exact same per-theme edit/save/reset machinery every other category
  already uses (no new architecture needed).
  - Stars: show/hide, density -- the star count now scales from the old
    fixed 70 by the density fraction, regenerated only when density
    actually changes (cached otherwise, avoiding pointless re-randomizing
    every frame).
  - Sky: a genuinely new 6-color model (Day High/Low, Night High/Low,
    dedicated Sunrise Low, dedicated Sunset Low) replacing the old fixed
    `theme.skyDay`/`skyNight`/`skyDawn`/`skyDusk` (4 arrays of 2 colors).
    `drawSky()` keeps the same "twilight bump near the terminator" blend
    shape as before -- only the top blends day↔night directly (the upper
    sky doesn't change much in reality during a sunrise/sunset); only the
    bottom gets the dedicated near-horizon glow color, matching which
    terminator is active (`dayPhase.progress < 0.5f` picks sunrise vs.
    sunset, same check the old code already used).
  - Sun/Moon: show/hide + color each. "Realistic Moon Phases" toggle --
    the actual astronomical phase calculation
    (`SunPositionCalculator.moonPhase()`) already existed and is
    unchanged; the toggle just lets a user opt out in favor of a plain
    always-full decorative moon.
  - `sky.sunCloudHeight` controls how high the sun/moon's arc rises
    (previously a fixed `0.42f`), and now also where clouds' band sits.
  - Every field's per-theme default is derived from that theme's own
    existing hardcoded colors via `defaultCustomizationFor()` (e.g.
    sky's day-high/day-low come straight from `theme.skyDay[0]`/`[1]`),
    so no theme's look changes until a user actually customizes it.
- **1b: Clouds.** New `CloudsConfig`: show/hide, density, day/night
  color. Puffy overlapping-circle shapes (not sharp cartoon outlines, to
  match the app's existing soft paper-cutout style) using the same
  independent-candidate-pool approach already established for mountains
  and birds -- own gentle parallax, own density filter, zero interaction
  with the hill/object row-placement system -- plus a slow independent
  horizontal drift on top of the parallax shift, so clouds visibly move
  even when the wallpaper isn't being scrolled.
- Seasonal four-leaf-clover/heart-shaped cloud variants (originally
  scoped as part of 1b) deferred to Phase 2, alongside that phase's other
  holiday-specific content -- this base cloud system is what those
  variants will build on top of, so building them before Phase 2 exists
  would mean guessing at integration points prematurely.
- New Scene Objects menu rows: "Sun and Moon", "Sky", "Stars", "Clouds".
- Three real compile errors caught and fixed during implementation, all
  in the new `drawClouds()`/`drawPuffyCloud()` code: a missing `cloudPaint`
  field declaration, a stray reference to a parameter under its
  pre-rename name (`elapsedSecondsForClouds` instead of `elapsedSeconds`)
  left over from an earlier edit, and a `Float`/`Int` type-inference
  issue in a cloud-tile-wrapping calculation, fixed by restructuring it
  as a `var` with explicit compound-assignment operators instead of a
  chained `.let {}` block.
- Verified with real builds throughout: `assembleDebug` was run after
  1a's data model, after its rendering wiring, and again after its
  persistence+UI; same pattern for 1b; plus a final `lint` pass across
  the complete 1a+1b set.

## v38 — in progress

"Scene Objects" reorganized into a menu + focused sub-screens, done now
(before Phase 1) as explicitly requested -- it had grown to 8 stacked
categories after Phase 0 and would only keep growing through the
remaining phases, becoming unusable if left until later. Also: a large
batch of new roadmap items added to `ROADMAP.md` (Phase 5 polish list
including update-flow/dolphin-direction/wave items, Phase 1's sunset-
theme design constraint) -- not implemented this version, tracked for
later.

- **New navigation: `SceneObjectsMenuDialog`.** "Scene Objects" now
  opens a simple menu (Cities, Hills, Mountains, Trees, Umbrellas, Lakes
  Boats and Dolphins, Cars, Birds) instead of one long scrolling screen
  with all 8 stacked on top of each other. Each row drills into its own
  focused sub-dialog (`CitiesSubDialog`, `HillsSubDialog`, etc.) via a
  shared `SceneObjectSubScreenShell` (back arrow, not an X -- these are
  a drill-down from the menu, not independent screens). "Cities" merges
  Houses + Buildings into one screen, matching the reference app's own
  "Show Buildings / Show Houses" combined layout. The live preview and
  "these settings apply to your current theme" explanation moved to the
  top-level menu (seen once before drilling in, rather than repeated on
  every sub-screen). Every sub-dialog reuses the *exact* same
  `ObjectCategorySection`/`MountainLayerSection`/`ColorSwatchRow`
  composables the old monolithic dialog already used -- this is
  purely a navigation restructuring, none of the underlying
  settings/persistence logic changed.
- "Seasonal Decorations" deliberately left as one screen for now --
  Phase 2 (bats, turkey, leprechaun, cupid, etc.) hasn't shipped yet, so
  there isn't a natural per-holiday grouping to split it into today;
  revisit once that phase adds enough holiday-specific content.
- Two real compile errors caught and fixed during the refactor: a
  missing `ColumnScope` import (the shared sub-screen shell takes a
  `@Composable ColumnScope.() -> Unit` content lambda) and a duplicated
  `@OptIn(ExperimentalMaterial3Api::class)` annotation left over from a
  find-and-replace.
- Verified with real builds: `assembleDebug` and `lint` both pass
  cleanly after the full reorganization.

## v37 — in progress

Three real bugs fixed in the v36 Phase 0 delivery, reported right after
it compiled (one with a screenshot). No new features -- see
`ROADMAP.md` for the large batch of newly-added-but-not-yet-implemented
items from this same conversation (Phase 5 polish list, a general
"Humans" object + emergency vehicles in Phase 2, a note on reorganizing
Scene Objects into sub-menus).

- **Flying mountains, fixed.** `drawMountains()`'s base Y was two fixed,
  guessed constants (0.50/0.545 of screen height), entirely independent
  of where the hill silhouette beneath them actually starts -- which,
  per `buildBaseHillPath`'s own random range, can be anywhere from
  fraction 0.15 to 0.75 of the farthest layer's band. Since 0.50 sits
  *above* even the hill's best-case (highest) possible top edge, the
  mountains floated with a visible gap of sky beneath them in most
  cases -- confirmed by the screenshot showing green triangle peaks with
  a wide band of plain sky between their base and the snow hills below.
  Fixed by deriving the base Y from `HILL_SAFE_ROW_MIN` -- the same
  proven-safe bound already used for object placement rows elsewhere --
  guaranteeing a mountain's base is always at or behind the hill's own
  worst-case top edge, so hills always cover at least the base, with
  peaks poking through wherever a given hill segment happens to dip.
- **Lake swallowing buildings, fixed.** The lake's bottom edge was a
  hardcoded 0.78 of screen height -- which lands *inside* the
  skyscraper/"buildings" category's own placement rows (0-2, reaching as
  near as ~0.765 at row 0). Turning "Lake Height" up put buildings'
  feet literally inside the lake's drawn rectangle. Fixed the same way
  as the mountains: derived the lake's bottom edge from that row's real
  groundY with a small safety margin, so the lake and any object row can
  never overlap regardless of the height slider's position.
- **Dolphins, redrawn.** The previous shape (a plain teardrop with a
  dot for an eye) didn't read as any particular animal at typical
  wallpaper viewing size. Replaced with an actual leaping-arc silhouette:
  a curved back and snout, a proper triangular dorsal fin, and upturned
  tail flukes, with a light belly-highlight circle for a bit of
  paper-cutout dimensionality -- animated with a gentle arcing
  bob/lean instead of the previous flat vertical wobble.
- Verified with a real build: `assembleDebug` and `lint` both pass
  cleanly after all three fixes.

## v36 — in progress

Roadmap Phase 0, in full: hills day/night color, the scroll-desync bug
fix, scroll settings, mountains, a lake with sailboats/dolphins, and an
ambient bird flock. All per-theme except the explicitly-global scroll
settings, per the axiom re-confirmed this session: user settings should
be per-theme wherever reasonably possible, not global, unless there's a
concrete reason (like matching a reference app's own convention, as
verified for Swipe Scroll's `saveWithTheme = false` in its decompiled
source).

- **Hills Day/Night color.** Completes what v35 started (Variation only).
  Rather than exposing 3 separate color pickers per layer (matching the
  existing internal 3-color arrays), added a single Day Color + Night
  Color pair per theme, with the 3 depth layers auto-deriving their own
  shade from it (`PaperRenderer.hillLayerColor()`, progressively
  blending toward black for nearer layers) -- simpler UI, and the
  derivation formula was checked against sunset's own hand-authored
  palette and lands within a few percent of the original ratios.
  `defaultCustomizationFor()` now looks up each theme's *existing*
  farthest-layer color as the starting point via `ThemeCatalog.byId()`,
  so switching to a custom hills color for the first time starts from
  that theme's own look, not an unrelated placeholder.
- **Parallax desync between hills and static objects while scrolling,
  root cause fixed.** Diagnosed last session, fixed this one: hills wrap
  their shift modulo a wide `2x screenWidth` tile (for organic
  non-repeating variety) while objects wrap the same shift modulo a
  narrower `screenWidth` (so each candidate slot maps to a distinct
  position). Two different moduli on the same growing value only agree
  while neither has wrapped yet. Android's home-screen `xOffset` is
  always normalized to `[0,1]` across the *entire* scrollable range
  regardless of page count (a documented `WallpaperService.Engine`
  guarantee, not an assumption about a particular launcher) -- so
  capping the effective parallax rate at `1.0` guarantees `|shiftX|`
  never exceeds one screen width for any layer at any valid offset,
  meaning neither modulo ever actually triggers during a normal swipe.
  Only behavior change: the very top of the "Scroll speed" slider (2.0x)
  combined with the nearest hill layer is capped from an uncapped 1.2x
  to 1.0x -- a minor, barely perceptible reduction at one extreme
  setting, in exchange for objects never visibly detaching from the
  terrain.
- **Scroll settings, global by design.** "Scroll speed" turned out to be
  the *same underlying mechanism* as the already-existing "Parallax
  strength" slider (confirmed via the reference app's own decompiled
  source: its `scrollSpeed` is a direct multiplier on the raw swipe
  offset, exactly like `parallaxStrength` already was here) -- reused
  and relabeled rather than adding a second, redundant slider. Two
  genuinely new settings: "Scroll background" (sun/moon/stars now
  optionally scroll with the parallax hills, off by default -- previously
  they never moved with scroll at all, only the hills/objects did) and
  "Swipe scroll" (lets a user turn off wallpaper scrolling from home
  screen swipes entirely; the wallpaper keeps redrawing for smooth
  day/night blending even with it off, only the parallax shift itself is
  suppressed).
- **Mountains**, two independent background layers ("Front"/"Back"),
  each with show/hide, day/night color, and density. Deliberately *not*
  built as new hill layers or new placement rows -- that would have
  meant touching `ROAD_SAFE_ROW_LIMIT`/`HILL_SAFE_ROW_MIN`/`MAX`, the
  already-carefully-proven-safe geometry from v24/v28's floating-object
  and road-clipping fixes. Instead, a fully independent backdrop system:
  discrete soft-rounded triangle silhouettes at their own (much slower
  than any hill layer) parallax rate, drawn behind everything else, with
  zero interaction with the object-placement system.
- **Lake**, with nested sailboats and dolphins. Same independent-backdrop
  philosophy as mountains, for the same safety reason: a horizontal band
  positioned in the "middle distance" (behind the house/road zone,
  confirmed clear of `ROAD_SAFE_ROW_LIMIT`'s safe rows) with subtle
  ripple-line texture, plus two nested decorations that drift slowly
  across it with simple bob/idle animation. Off by default (unlike
  mountains) -- not every theme's landscape should suddenly grow a lake
  unless the user actually wants one.
- **Birds**, an ambient flock -- the first genuinely new *sky* system,
  distinct from anything anchored to the ground. Each bird is a stable,
  weighted-random pick across 4 user-editable colors (`BirdsConfig
  .pickColor()`, "Bird Color Frequencies" in the UI -- not a plain 1-in-4
  choice), with a "Night Birds" toggle controlling whether they fade out
  after dark (default) or keep flying regardless.
- All 6 pieces share one architectural choice: candidate generation and
  density filtering follow the same spirit as the existing structural
  categories (`SceneCustomization`'s `keepCandidate`/`stableFraction`
  pattern) without literally reusing that machinery, since mountains/lake
  decorations/birds aren't `StaticSceneObject`s in the row-placement
  sense -- they're simpler, independently-seeded candidate pools with
  their own lightweight density check, by design, to keep them fully
  decoupled from the placement-safety geometry.
- Verified with real builds throughout, not just at the end: `assembleDebug`
  was run after each of the 6 pieces individually (catching and fixing 2
  real compile errors -- missing cross-package imports for
  `MountainLayerConfig`/`LakeConfig`/`BirdsConfig`/`BirdColorWeight` --
  as they happened rather than in one large batch at the end), plus a
  final `lint` pass across the whole set.

## v35 — in progress

Roadmap Phase 0, point 4 (Hills) — variation only, per explicit
correction mid-implementation.

- **Hills Variation**, per-theme, following the exact same axiom the
  rest of the app already follows: user-editable settings should be
  per-theme, not global, wherever reasonably possible. The first attempt
  at this used a flat, global slider (mirroring `parallaxStrength`,
  which genuinely is global) -- corrected immediately on request to
  instead reuse `SceneCustomization`'s existing per-theme
  edit/save/resolve machinery (the same `pendingCustomization` +
  `resolveActiveCustomization` + save-to-theme workflow every other
  category already uses), since hills are part of a *theme's* look, not
  a device-wide rendering preference. Added as a plain `Float` field on
  `SceneCustomization` (no visibility/density/color-variant shape needed,
  just one value) with a default of `1f`, so nothing changes for existing
  themes until a user opts in; a small "Hills" section was added at the
  top of the existing "Scene Objects" dialog as a lightweight home for it
  until it gets its own full section (with color + height) per
  `ROADMAP.md`'s Phase 0.
- **Height deliberately deferred, not rushed.** `heightFractions`/
  `yOffsets` (the layer proportions this would need to scale) are the
  exact same geometry `ROAD_SAFE_ROW_LIMIT`/`HILL_SAFE_ROW_MIN`/`MAX`
  depend on for guaranteeing objects never float above the terrain or
  get clipped by the road (see v24/v28's fixes). Scaling hill height
  without also correctly re-deriving that placement math risks silently
  reintroducing exactly those bugs. Variation carried no such risk and
  shipped; height needs its own careful pass and is tracked in
  `ROADMAP.md` rather than rushed under this session's token budget.
- **Provably bounded, not just "seems fine".** `buildBaseHillPath`'s
  random peak/edge fractions used to span fixed ranges (`0.15..0.70`,
  `0.55..0.75`) that the placement-safety math was proven against.
  Variation now interpolates each segment toward the *exact center* of
  its original range rather than extending it, so for any variation
  value in `0..1` the result is mathematically guaranteed to stay within
  those same original bounds -- variation=1 reproduces today's exact
  look, variation=0 collapses to a perfectly flat hill, and nothing in
  between can ever exceed the range the safety math already assumes.
- The hill-path cache (rebuilt only when theme/screen size changes, to
  avoid recomputing control points every frame) now also keys on
  variation, so changing the slider actually regenerates the silhouette
  instead of showing a stale cached one.
- Verified with real builds: `assembleDebug` and `lint` both pass
  cleanly.

## v34 — in progress

A real, pre-existing bug found while double-checking the exact workflow
the user asked me to confirm ("can I freely edit and save over an
existing theme?") -- not introduced by v33's per-theme refactor
(`entryFor`'s priority ordering was untouched by that change), but only
surfaces once a theme has been saved/overridden *more than once*, which
nothing in this session had actually exercised yet.

- **`CustomThemeRegistry.resolveActiveCustomization()` always prioritized
  an already-saved entry over an active live edit of that exact same
  theme.** Its priority order was: (1) saved override/custom entry for
  this theme id, unconditionally, (2) the in-progress pending edit, only
  if nothing was saved yet. This meant the *first* time you overwrite a
  built-in theme (or save a custom one) works fine -- but the *second*
  time you try to tweak that same already-saved theme and hit "Replace
  with current" again, `snapshotEntry()` (which itself calls
  `resolveActiveCustomization()`) would hit branch (1) first, silently
  re-saving the *original* frozen snapshot and discarding whatever new
  edits you'd just made -- both in the live wallpaper preview while
  editing and in what actually got written to disk on save. Any
  already-once-saved theme was effectively stuck after its first save.
- **Fixed by reordering the priority check**: if the theme currently
  being viewed is the one actively tagged as mid-edit
  (`pendingThemeId == themeId`), the live pending edit now wins even over
  an existing saved entry -- editing an already-overwritten theme now
  correctly shows live changes and "Replace with current" correctly
  picks them up. The original protection this ordering existed for
  (editing theme A must never bleed into how theme B, a *different*
  theme, currently looks) is preserved exactly: that guarantee only ever
  depended on the `pendingThemeId == themeId` check, not on which branch
  ran first.
- Verified with real builds: `assembleDebug` and `lint` both pass
  cleanly after the fix.

## v33 — in progress

Architecture correction to v32's Seasonal Decorations, per explicit
feedback right after v32 shipped: built-in themes should keep their own
sensible defaults (Christmas *should* already have snowmen), with the
user free to change and then save that change, exactly like structural
categories already work -- not a flat "everything off, global" system.

- **Seasonal decorations are now per-theme, not global.** v32's
  `WallpaperSettings.seasonalCustomization` (a separate, theme-independent
  field merged onto whatever theme was active at render time) is gone.
  Snowmen/gifts/balloons/penguins/bunnies/easterEggs/pumpkins are now
  just 7 more fields on the *same* `pendingCustomization` that houses/
  trees/etc. already used -- edited from the (still separate, as
  originally requested) "Seasonal Decorations" screen, but living on the
  same per-theme scratch space, with the same "switch themes and it
  follows the one you're on" behavior, and the same "Manage Themes" save
  path (Replace with current / Save as new theme) that already existed.
- **New `defaultCustomizationFor(themeId)`**, the actual fix: instead of
  every theme falling back to one flat `SceneCustomization.DEFAULT` when
  nothing's been customized yet, each built-in theme gets its own
  starting point for seasonal categories -- Christmas defaults to
  snowmen+gifts on, Easter to bunnies+eggs on, Tundra to snowmen+penguins
  on, New Year's Eve to balloons on, Winter to a snowman on; everything
  else (structural categories, and seasonal categories on non-seasonal
  themes) stays at the shared flat default. `CustomThemeRegistry
  .resolveActiveCustomization()`'s final fallback now calls this instead
  of the flat default -- the single place both rendering and the
  settings dialogs read from, so both get the fix automatically.
- **A subtler bug caught and fixed in the same pass**: simply changing
  that one fallback wasn't sufficient on its own. `WallpaperPrefs
  .settingsFlow` reads `pendingCustomization` from DataStore one category
  at a time, falling back to a default *per category* whenever that
  category's own key was never written -- and that per-category fallback
  was still the flat `SceneCustomization.DEFAULT`. Left alone, editing
  even a single category for Christmas (say, turning snowmen off) would
  have caused every *other*, untouched category (gifts, houses, ...) to
  silently fall back to the flat default too, losing Christmas's own
  defaults for everything the user didn't explicitly touch yet. Fixed by
  deriving that per-category fallback from `defaultCustomizationFor`
  applied to whichever theme is currently tagged as being edited
  (`PENDING_CUSTOMIZATION_THEME_ID`), so untouched categories keep
  reading as that theme's own defaults for the whole editing session.
- **Saved custom themes now actually remember seasonal choices.**
  `CustomThemeData`'s JSON (de)serialization previously only handled the
  5 structural categories -- correct under v32's global-seasonal design,
  where a saved theme's seasonal fields were meaningless placeholders
  always overridden by the global merge. Under this per-theme design
  that assumption no longer holds: saving "Christmas with snowmen turned
  off" needs to actually persist that choice. All 7 seasonal categories
  are now included in `SceneCustomization.toJson()`/
  `sceneCustomizationFromJson()`.
- **UI simplified, not just fixed.** `SeasonalDecorationsDialog` now
  directly reuses the existing `ObjectCategorySection` composable (same
  one "Scene Objects" uses) instead of a near-duplicate
  `SeasonalCategorySection` -- the two screens' underlying editing model
  is now identical, so there was no longer a reason to maintain two
  copies of the same UI code. Its "reset everything" button resets only
  the 7 seasonal categories for the current theme (not structural ones
  too, and not other themes' saved seasonal choices).
- Verified with real builds: `assembleDebug` and `lint` pass cleanly
  after the full change, including a real compile error caught along the
  way (`defaultCustomizationFor` needing an explicit import across
  packages).

## v32 — in progress

Seasonal decorations decoupled from theme identity entirely -- the big
architectural change requested after reviewing screenshots of a
reference app's own settings menu (a "Seasonal" settings section,
separate from scene objects, with per-decoration toggles usable
regardless of which theme is active).

- **Root problem being fixed**: snowmen, gifts, balloons, penguins,
  bunnies, and Easter eggs were previously hardcoded per theme in
  `builtinLayoutFor()` (Christmas always got snowmen+gifts, Easter always
  got bunnies+eggs, etc.), with fixed, non-editable colors, and no way to
  use one on a different theme -- exactly the two complaints raised:
  "i pupazzi non sono editabili e sono solo in natale, il coniglio
  pasquale non è settabile ed è solo in pasqua".
- **New architecture, reusing the existing customization machinery rather
  than building a parallel one.** `SceneCustomization` (already the
  shared visibility/density/2-color-variant shape used by houses, trees,
  buildings, cars, umbrellas) gained 7 more categories in the same shape:
  snowmen, gifts, balloons, penguins, bunnies, easterEggs, pumpkins --
  all defaulting to `visible = false` (matching the reference app's own
  default-unchecked convention) so nothing about any existing theme's
  look changes until a user opts in. `configFor()`/`keepCandidate()`/
  `colorFor()` needed zero changes to support them -- they already
  worked generically over whatever categories exist on the class.
- **Placement decoupled from theme identity.** New
  `SceneObjectCatalog.seasonalDecorationCandidates()` generates the same
  kind of uniform, road-safe (rows 0-4, respecting
  `PaperRenderer.ROAD_SAFE_ROW_LIMIT`) candidate set as houses/trees, but
  applied in `layoutFor()` to *every* theme -- builtin, custom, and
  Random alike -- rather than being hand-placed inside one theme's
  branch. All the old per-theme hardcoded snowman/gift/balloon/penguin/
  bunny/easter-egg placements were removed from `builtinLayoutFor()`
  entirely, since density=0/visible=false by default already produces
  the same "nothing shows unless enabled" starting state.
- **New `PUMPKIN` object type** (explicitly requested by name), with its
  own `drawPumpkin()` -- a 3-lobe ribbed silhouette with a stem and leaf,
  in the same paper-cutout style as everything else, fully customizable
  from day one rather than needing a later customization pass.
- **Existing "flavor" objects made genuinely editable.** `drawSnowman`,
  `drawGift`, `drawPenguin`, `drawBunny`, `drawEasterEgg`, and
  `drawBalloon` previously pulled their primary color from fixed
  constants or small hardcoded palettes indexed by position (never
  user-editable, never blending day/night). They now call
  `customization.colorFor(r.spec, dayBlend)` like every structural
  category already did -- small character-defining accents (a penguin's
  beak, a bunny's inner ear, a gift's ribbon) stay fixed for
  recognizability, the same way a house's window glow or a building's
  awning stripes do.
- **Global, not per-theme, persistence** -- a deliberate architectural
  split. `WallpaperSettings.pendingCustomization` (structural categories)
  stays scoped to one theme at a time, as it always was. A new sibling
  field, `seasonalCustomization`, is global by design: its 7 fields
  persist independently of any theme via new, unscoped `WallpaperPrefs`
  setters (`setSeasonalVisible`/`setSeasonalDensity`/etc. -- notably
  *not* stamping `PENDING_CUSTOMIZATION_THEME_ID` the way the structural
  setters do), and `PaperWallpaperService.applyEffectiveTheme()` merges
  just those 7 fields onto whichever theme's structural customization
  was just resolved, every frame-relevant update. `ObjectCategory` grew
  7 new entries reusing the exact same per-category DataStore key
  pattern; `CustomThemeData`'s saved-theme JSON deliberately does *not*
  carry seasonal fields (a saved custom theme's `customization` blob was
  never meant to include them -- they're overwritten by the global merge
  regardless of what's in it).
- **New "🎃 Seasonal Decorations" screen, separate from "Scene Objects"**
  as explicitly requested. `SeasonalDecorationsDialog` +
  `SeasonalCategorySection` mirror `SceneObjectsDialog`/
  `ObjectCategorySection`'s UI exactly (visibility toggle, density
  slider, 4 tap-to-edit color swatches, per-category and reset-all
  buttons) but call the new global setters instead of the theme-scoped
  ones.
- **Scope, stated honestly.** The reference app screenshots reviewed for
  this feature show several holiday categories and object types this
  update does not add: Halloween bats, a Thanksgiving turkey, a St.
  Patrick's Day leprechaun and four-leaf-clover clouds, Valentine's Day
  cupid and heart clouds, Easter flowers/baskets, and Halloween
  building-window lights --
  plus several boolean sub-toggles beyond visibility/density/color (day
  vs. constant fireworks, "African American Santa", "random gifts") that
  the current `ObjectVariantConfig` shape has no slot for. Building all
  of that well was a larger scope than one session; what's implemented
  here is the *architecture* (fully reusable for adding the rest later,
  see `CONTRIBUTING.md`'s updated future-work list) plus the objects
  explicitly named in the request (pumpkins, gifts, snowmen, umbrellas)
  and the ones already in the codebase that had the exact bugs described
  (snowmen, bunny).
- Verified with real builds at every stage of this session, not just a
  read-through: `assembleDebug` and `lint` both pass cleanly after the
  full change, including two real compile errors caught and fixed along
  the way (`SceneCustomization`'s new constructor needing values in
  `CustomThemeData.kt`, and a missing `@OptIn(ExperimentalMaterial3Api::class)`
  on the new dialog).

## v31 — in progress

Custom launcher icon and a real user-facing changelog pipeline, both
requested after seeing v30 compile successfully.

- **Launcher icon, redesigned from a generic placeholder to something
  that's actually PaperScrape.** The old icon was an unrelated orange
  gradient with a wavy blob -- no house, no connection to what the app
  actually looks like. The new one uses the *exact* geometry from
  `SceneObjectRenderer.drawHouse()` (wall/roof/chimney/door/window
  coordinates, reused via a `<group>` transform rather than redrawn from
  scratch) and the app's own default house colors
  (`SceneCustomization.DEFAULT.houses`) plus the "winter" theme's palette
  (`SceneTheme.WINTER`), so the icon reads as the same house a user will
  actually see animated in the wallpaper. Verified visually, not just by
  reading the path data: wrote a from-scratch VectorDrawable-to-SVG
  converter, rendered the result at both full size and at real 48px
  launcher scale, and caught + fixed a real visual bug this way (the
  house's fixed-height foundation left a gap where the wavy hill curve
  dipped below it at that exact x, letting the farther/paler hill peek
  through oddly -- fixed with a flattened "yard patch" the house stands
  on, wide enough to always cover that gap regardless of the curve's
  exact shape). Also added `ic_launcher_monochrome.xml`, a simplified
  single-color silhouette for Android 13+'s themed-icon feature -- lint
  had flagged this as missing all the way back at the start of this
  session; now fixed. Verified end-to-end with a real build: compiles,
  lint's `MonochromeLauncherIcon` warning is gone.
- **User-facing release notes, end to end.** Previously: `CHANGELOG.md`
  is (deliberately) a technical dev log, but it was also the *only*
  changelog that existed, and the GitHub release body was just a
  security/checksum blurb with no mention of what actually changed --
  and the in-app update dialog didn't show any release notes at all,
  because `UpdateChecker` never even parsed the `body` field from
  GitHub's release API response. Fixed the whole pipeline: added a
  `release-notes/vN.md` convention (short, plain-language, no file/code
  references -- see this version's own `release-notes/v31.md` for what
  that actually looks like) that the `release` CI job now reads and uses
  as the GitHub release body (falling back to a generic placeholder if a
  version ships without one, and always appending a short verification
  footer after the friendly part, not instead of it). `UpdateChecker` now
  captures that same release body as `UpdateInfo.releaseNotes`, and the
  Settings screen's update dialog shows it under a "What's new" heading
  (scrollable, capped length) -- so the same plain-language text now
  shows up in exactly the two places a regular user would actually see
  it: the GitHub release page, and the in-app prompt itself. Documented
  the convention in `CONTRIBUTING.md` so future versions keep following
  it.

## v30 — in progress

Two changes: the major AGP 8 → 9 upgrade requested last session, plus a
follow-up road-clipping fix for seasonal objects that v28's fix didn't
reach.

- **AGP 8.7.2 → 9.3.0, Gradle 8.9 → 9.5.0, real built-in-Kotlin migration
  (not the temporary opt-out).** Chose 9.3.0 specifically because AGP
  9.0's `android.builtInKotlin=false` opt-out is explicitly scheduled for
  removal in AGP 10.0 -- taking that shortcut now would just mean
  redoing this migration for real in a few months. Concretely: removed
  `org.jetbrains.kotlin.android` from both `build.gradle.kts` files (AGP
  9's built-in Kotlin replaces it entirely -- attempting to keep both
  throws `Cannot add extension with name 'kotlin'`), migrated the
  deprecated `android.kotlinOptions{}` block (removed outright: with
  built-in Kotlin, `jvmTarget` already defaults from
  `compileOptions.targetCompatibility`, so the block was redundant, not
  just deprecated), and fixed `rootProject.buildDir` (deprecated,
  incompatible with Gradle 10) to `layout.buildDirectory`. Downloaded the
  real Gradle 9.5.0 distribution, independently cross-checked its
  SHA-256 against a third-party mirror (a public Docker image's build
  script) in addition to Gradle's own published checksum, and generated
  a genuine wrapper the same way as v26. Verified with real builds at
  every step in this session: `assembleDebug`, `lint`, `test`, and
  `assembleRelease` (with a disposable test keystore) all pass; the
  signed release APK shrank further (R8 in AGP 9 appears more effective)
  and `aapt dump badging` confirms `compileSdkVersion 36` with no
  suppressed-warning flag needed anymore -- removed
  `android.suppressUnsupportedCompileSdk=36` from `gradle.properties`
  since AGP 9.3 officially supports API 36.1, the exact gap that flag
  existed to paper over.
- **Verified against the 53 Dependabot alerts, honestly, not just
  hopefully.** Compared exact resolved versions before/after by
  timestamp in the Gradle dependency cache (not guessed): AGP 9.3.0 pulls
  newer `protobuf-java` (3.22.3 → 3.25.5, which is *exactly* the
  first-patched version GitHub lists -- that alert is now resolved) and
  newer BouncyCastle `bcprov` (1.77 → 1.79, clears most but not all of
  its CVE ranges -- one advisory needs 1.84 and remains open). Netty
  (the package behind 38 of the 53 alerts) did **not** change version
  (still 4.1.93.Final) -- every Netty fix version GitHub lists starts at
  4.1.100.Final or higher, so none of those 38 alerts are resolved by
  this upgrade. Net effect: a real but partial improvement, not the fix
  for "the 53 alerts" as a whole.
- **Road-clipping fix, extended to seasonal objects.** v28 fixed the
  road-cutting bug for houses/trees/parasols/skyscrapers (all placed via
  `uniformCandidates`, which now respects
  `PaperRenderer.ROAD_SAFE_ROW_LIMIT`) but missed a second code path:
  every theme's hand-placed seasonal "flavor" decorations -- snowmen,
  Christmas gifts, New Year balloons, tundra penguins, Easter eggs and
  bunny -- are added directly in `builtinLayoutFor()`, not through
  `uniformCandidates`, and were still hardcoded to `layer = 8` (the same
  unsafe, inside-the-road-band row houses used to sit on before v28).
  These aren't yet exposed in the Scene Objects customization menu, which
  is likely why this half of the bug went unnoticed. Moved all of them to
  `layer = 4` -- the same safe "yard" row houses and parasols already
  use, so they now read as sitting alongside the rest of the neighborhood
  instead of getting sliced by the road.

## v29 — in progress

Full removal (not disabling, not commenting out) of two features, per
explicit request: sound and touch effects entirely, and dogs entirely.
Rather than auditing which parts of several versions' worth of
accumulated touch/sound code still worked correctly and which didn't,
the whole thing comes out clean so whatever replaces it later starts
from a known-good baseline.

- **Sound and touch effects -- fully deleted, not disabled.** Removed
  `PaperBird.kt` and `ReactionSoundPlayer.kt` entirely.
  `PaperWallpaperService.onTouchEvent` (and the `MotionEvent` import),
  `activeBirds`, `PaperRenderer.handleTap()`,
  `SceneObjectRenderer.tryHandleTap()`, `StaticRuntime.reactionTimer`,
  `CarRuntime.honking`, `StaticSceneObject.tappable`/`TAPPABLE_TYPES`, the
  `reactionEase` "hop"/sway-boost logic in `drawStaticObject` and its
  `reactionBoost` parameter threaded through `drawTree`/`drawSnowman`/
  `drawPalmTree`, the `touchEffectsEnabled` setting (`WallpaperPrefs`'
  field, DataStore key, and setter), its Settings-screen toggle, the
  `tappable` field in custom-theme JSON (de)serialization, and the two
  now-orphaned `settings_touch_effects*` string resources are all gone.
  `README.md`/`CONTRIBUTING.md` updated throughout (feature list, object
  behavior table, project structure tree, architecture section, "A note
  on sound" section removed) -- confirmed via full-repo grep that zero
  functional references remain (only historical mentions in this
  changelog and in v28's own entry above).
- **Dogs -- fully deleted, not commented out.** Removed
  `SceneObjectType.DOG`, `drawDog()`, `dogSpotColor`, dogs' special
  (non-doubled) scale-exemption branch in `drawStaticObject`,
  `SceneCustomization.dogs` (field, `DEFAULT` entry, `configFor` mapping),
  `ObjectCategory.DOGS` and its DataStore read in `WallpaperPrefs`, the
  dog entry in `CustomThemeData`'s JSON (de)serialization, and the dog
  sample from the settings-screen live preview row. Unlike v28 (which
  kept this code commented out for a fast re-add), this is a clean-slate
  removal per explicit request -- dogs will be redesigned from scratch,
  not patched back in, so there's nothing here worth preserving as a
  starting point. Noted as an open item in `CONTRIBUTING.md`'s "Ideas for
  future contributions" alongside touch/sound.
- Also removed the now-orphaned `app/src/main/res/raw/` placeholder
  folder (it existed solely to hold future real audio files for the
  now-deleted `ReactionSoundPlayer`).

Verified with a real build + lint in this session, not just read-through:
`assembleDebug` and `lint` both pass cleanly. Lint's `UnusedResources`
check independently confirmed the two touch-effects strings were
genuinely orphaned before they were removed (the other unused-resource
warnings it reports are pre-existing, unrelated scaffolding not touched
here).

## v28 — in progress

Definitive fix for the road-clipping bug (houses/trees/parasols visibly
"cut" by the road), dogs temporarily removed, and two Santa's-sleigh fixes
(jerky motion, gifts vanishing before reaching the houses) -- all
requested after seeing the reference app's art direction again.

- **Road clipping, root cause found.** `SceneObjectRenderer.drawRoad()`
  paints an *opaque* full-width road rectangle at the cars' fixed lane
  position, *after* every static object has already been drawn. HOUSE
  (rows 4-6), `treeType` (rows 4-8), and PARASOL (rows 7-8) all placed
  some of their rows at an absolute screen fraction of ~0.885-0.957 --
  inside or past that road band -- so a real chunk of those objects was
  simply painted over. Fixed at the source: added
  `PaperRenderer.ROAD_SAFE_ROW_LIMIT` (= 4), derived from the actual
  `laneYFraction` ranges used by both generators plus `drawRoad`'s own
  padding, as the single shared boundary below which a row is
  *guaranteed* clear of the road. HOUSE and PARASOL now use rows 3-4,
  `treeType` uses rows 0-4 (gaining farther/skyline placement instead of
  losing variety). `RandomSceneGenerator`'s own row roll -- previously
  `rnd.nextInt(0, TOTAL_ROWS)` with no road awareness at all, the same
  bug in a second place -- now uses the same shared constant, so the two
  generators can't silently drift apart on this again.
- **Dogs temporarily removed** from both the preset-theme generator and
  Randomize mode, per request (coming back later). Only the *placement*
  candidates were removed -- `drawDog`, `ReactionSoundPlayer`'s dog bark,
  `SceneCustomization`'s dog color mapping, and `WallpaperPrefs`'
  `ObjectCategory.DOGS` all stay in place untouched, so re-adding is a
  one-line change, not a rebuild. The Settings screen's "Dogs" color
  section and the live preview row's dog sample are commented out (not
  deleted) for the same reason -- showing customization controls for an
  object that can never appear would just be confusing, dead UI.
- **Santa's sleigh: jerky motion.** Found a concrete, provable cause in
  the render loop, not just in Santa's own code: `PaperWallpaperService`
  scheduled every next frame with a *fixed* `postDelayed(..., 33ms)`
  after the current frame's work had already finished, rather than
  accounting for how long that work took -- so real frame-to-frame timing
  drifted and fluctuated with system load (GC pauses, other apps
  competing for CPU), even though each animation's own position was
  still correctly scaled by the actual measured `deltaSeconds`. Uneven
  real-world frame spacing reads as stutter to the eye regardless of
  whether the math is right, and it's most visible on the single largest,
  fastest, most detail-heavy moving shape in the scene (two reindeer plus
  sleigh plus Santa, ~15-20 draw calls with saves/rotates every frame) --
  which is exactly what got reported. Fixed by measuring each frame's own
  cost and subtracting it from the next scheduling delay, keeping the
  *schedule* itself close to a steady cadence instead of compounding
  drift on top of it every single frame.
- **Santa's sleigh: gifts vanishing before reaching the houses.** The
  falling-gift removal condition was `age > 2.5f` with a constant
  `vy` of 70-100px/s -- in 2.5 seconds a gift could only fall
  ~175-250px, nowhere near the houses (which sit at roughly 80-90%+ of
  screen height on any real device), regardless of the `y > screenHeight`
  check that could realistically never trigger first. Rewrote
  `FallingGift` to fall from Santa's height to an explicit target Y near
  curb/house level (screen-height-relative, so it's correct on any
  screen size) over a fixed duration, using an eased (progress²)
  accelerating fall that reads as a natural toss-and-drop rather than a
  constant-speed slide, and fades out only in the last ~15% of that fall
  instead of on an unrelated fixed timer.

## v27 — in progress

Points 6-8 of the ChatGPT security/quality audit, queued because the repo
is public even though it's a personal, non-commercial project.

- **Point 6 -- CVE/OSV dependency scan.** Resolved the *actual* runtime
  dependency graph via `./gradlew :app:dependencies` (113 unique
  group:artifact:version entries after conflict resolution -- not just the
  ~10 directly declared in `build.gradle.kts`, since the Compose BOM and
  transitive deps resolve to their own pinned versions) and queried all of
  them against the OSV.dev database in a single batch request. Result:
  zero known vulnerabilities at current versions, including the AGP 8.7.2
  and Kotlin 2.0.21 build-time plugins. This was a one-time check, though,
  so added `.github/workflows/dependency-submission.yml`: a weekly
  (plus on-push) job that submits the real resolved graph to GitHub's
  Dependency Graph API via `gradle/actions/dependency-submission`, which
  feeds Dependabot alerts automatically -- turning "no CVEs today" into
  "you'll be told the day one is disclosed for anything you depend on,"
  which a one-off scan can't do by itself.
- **Point 7 -- render loop allocations.** `SceneObjectRenderer` (the
  hottest path -- runs for every visible object, every frame) and
  `SantaSleighEffect`/`PaperRenderer`'s moon phase drawing were
  constructing a fresh `RectF` object for nearly every `drawRect`/
  `drawOval`/`drawRoundRect`/`Path.arcTo` call -- confirmed 41 such
  allocation sites via `grep`, all now replaced with Android's
  float-primitive overloads of those same Canvas/Path methods (available
  since API 21, well within this project's `minSdk = 26`), which take the
  four/six coordinates directly with no intermediate object. Same math,
  same visual output, zero behavior change -- verified by diffing that
  every replaced call still passes the identical literal coordinates, and
  by a full `assembleDebug` + `lint` pass afterward. This eliminates what
  was previously dozens of small object allocations *per visible object,
  per frame* (a live wallpaper redraws continuously in the background),
  which is exactly the kind of steady GC pressure that shows up as jank or
  extra battery drain over time rather than in a single obvious profiler
  spike. `Paint`/`Path` objects were already correctly reused as class
  fields before this pass (verified, not assumed) -- only the `RectF`
  arguments were the gap.
- **Point 8 -- signed release metadata beyond SHA-256.** Added
  `actions/attest-build-provenance` (GitHub's own action, free for public
  repos) to the `release` job: every release APK now gets a Sigstore-signed,
  publicly verifiable build provenance attestation published to GitHub's
  attestations API, in addition to the `.sha256` file from v26. A checksum
  only proves "this file matches a hash I was given"; the attestation
  additionally proves the file was built by *this repository's* GitHub
  Actions workflow from a specific commit -- verifiable by anyone via
  `gh attestation verify app-release.apk --repo urgali/paperscrape`, no
  shared secret or manually-managed signing key required (Sigstore's
  public-good instance + GitHub OIDC handles it). Documented in the
  README's new "Verifying a downloaded release" section.

## v26 — in progress

Points 2-5 of the ChatGPT security/quality audit (see v25's entry for point
1). Every item below was verified against a real, working build in this
session -- a genuine Android SDK 36 + JDK 17/21 toolchain was set up and used
to actually run `assembleDebug`, `assembleRelease`, `lint`, and `test`, not
just read the code and assume it would work.

- **Point 2 -- real release signing.** `release` build type now has its own
  `signingConfig`, sourced *only* from environment variables
  (`PAPERSCRAPE_RELEASE_STORE_FILE`/`_STORE_PASSWORD`/`_KEY_ALIAS`/
  `_KEY_PASSWORD`) -- never a committed file, never a hardcoded password like
  the debug config's (whose password is intentionally public, see its own
  comment). `isDebuggable = false` is now explicit on `release`. If the env
  vars are absent, `release` has no signing config attached at all, so
  `assembleRelease` produces an unsigned, uninstallable APK -- loud failure
  instead of silently shipping something that looks legitimate but isn't
  signed with the real key. Added `scripts/generate-release-keystore.sh` for
  the maintainer to generate their own real keystore locally -- deliberately
  not generated by Claude and handed over, since a release signing key is the
  app's permanent identity and should only ever exist on the maintainer's own
  machine and in their own GitHub Secrets. Verified end-to-end with a
  disposable test keystore: `assembleRelease` correctly produced a signed
  (non-debuggable, R8-shrunk 1.7MB vs. debug's 18.3MB), installable APK.
- **Point 3 -- CI hardening.** Split the single `build` job into `build`
  (runs on every push/PR, `contents: read`, never sees signing secrets) and a
  new `release` job (`contents: write`, only runs on push to `main`, depends
  on `build` succeeding). Removed the `gh release delete --cleanup-tag`
  behavior entirely -- if the computed tag already exists, the workflow now
  fails with a clear error telling the maintainer to bump `versionCode`,
  rather than silently deleting a published release. `gradle/actions/setup-gradle`
  is now pinned to its real commit SHA (`0723195...`, tag `v5.0.2`) instead of
  the mutable `@v5` tag -- deliberately *not* upgraded to v6, since v6 bundles
  a proprietary caching component under a separate commercial Terms of Use
  that shouldn't be opted into silently.
- **Point 4 -- real Gradle Wrapper committed.** Downloaded the actual Gradle
  8.9 distribution, verified its SHA-256 against the value already declared
  in `gradle-wrapper.properties`, and used it to generate genuine
  `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` files, now committed to the
  repo. CI no longer runs `gradle wrapper --gradle-version ...` at runtime to
  fabricate these files on every single run.
- **Point 5 -- update-URL validation + APK checksum.** `UpdateChecker` now
  validates `html_url` from GitHub's API response through a new
  `sanitizeGitHubUrl()` (must be exactly `https://github.com` or
  `https://www.github.com`, falling back to a URL constructed from the
  known owner/repo otherwise) before it's ever handed to
  `Intent.ACTION_VIEW` -- tested against 9 cases including typosquatting
  (`github.com.evil.com`), non-http schemes (`intent://`), and wrong hosts,
  all correctly rejected. The `release` CI job now also generates and
  publishes a `.sha256` file alongside every release APK.

Also confirmed, not yet acted on (out of scope for points 2-5): the project
still has zero unit tests (`./gradlew test` succeeds but reports `NO-SOURCE`
everywhere) -- the audit's suggestion to add tests for
`SunPositionCalculator` and update-JSON parsing remains open.

## v25 — in progress

Point 1 of the ChatGPT security/quality audit (see
`paperscrape-claude-fix-plan-v2.docx`, cross-checked against the actual
repo before touching anything — every claim verified against the real
code, not assumed): the sunrise/sunset calculation was calculating the
wrong thing entirely.

- **`SunPositionCalculator.approximateSunriseSunset()` was silently
  discarding its own UTC-offset input.** `solarNoon = 12.0 - utcOffsetHours
  * 0.0` always evaluated to exactly `12.0` regardless of what was passed
  in — solar noon was hardcoded to civil noon everywhere on Earth.
- **Longitude was never a parameter at all**, despite the function's own
  doc comment claiming to use "latitude/longitude/date". Two different
  cities in the same timezone (e.g. Rome and Madrid, both UTC+1) got
  identical sunrise/sunset times, even though Madrid's is measurably
  later due to sitting much further west within that timezone.
- **The caller used `TimeZone.rawOffset`**, which is explicitly the
  *non*-DST standard offset — every sunrise/sunset was off by an hour
  during daylight saving time.
- Fixed by adding a real `longitudeDeg` parameter, computing solar noon
  as `12.0 - longitudeDeg / 15.0 + utcOffsetHours` (verified numerically:
  Rome at equinox now gives ~6:11/18:09, shifts a full hour under a
  DST offset, and Madrid at the same latitude/timezone but further west
  now correctly comes out ~1h05m later than Rome), and switching the
  caller to `TimeZone.getOffset(now)` instead of `rawOffset` so DST is
  picked up automatically.
- Also documented (not changed — the existing clamp was already
  mathematically correct) why the polar day/night guard doesn't need
  special-casing: `cosHourAngle.coerceIn(-1.0, 1.0)` before `acos()`
  already collapses correctly to a full 24h day arc or a zero-length one
  at the poles, which the existing `dayLength.coerceAtLeast(1f)` floor in
  `compute()` already handles gracefully.

Remaining audit items are queued, not yet started: items 2-5 (release
signing, CI hardening, update-URL validation, custom-theme storage
limits) are next; items 6-8 (dependency CVE/OSV scan, render-loop
allocation reduction, signed release metadata beyond SHA-256) are queued
further out — the repo being public (even though personal/non-commercial)
makes the former worth doing, and reducing allocations is worth doing on
its own merits regardless of any measured perf problem today.

## v24 — in progress

Root-cause fix for the three recurring visual bugs (flying buildings,
house/building overlap, undersized skyscrapers) that earlier versions
patched around symptomatically without finding the underlying mechanism.
All three had a concrete, provable cause — none of this was tuned by
trial and error:

- **Flying buildings, for real this time.** The actual cause was a
  mismatch between two independent systems that were never cross-checked
  against each other: `buildBaseHillPath()`'s random hill silhouette can
  place its visible top edge anywhere from 15% to 75% down a layer's
  band, but object placement rows were anchored at fixed fractions
  (0.20/0.50/0.80) that assumed the hill top rarely went below ~30%.
  Any row above ~75% could, and regularly did, end up sitting in open
  sky whenever that segment of hill happened to roll a low peak — worse
  the higher (farther/smaller) the row. `PaperRenderer` now derives its
  row-placement band (`HILL_SAFE_ROW_MIN`/`MAX` = 0.78-0.95) directly
  from `buildBaseHillPath`'s own random bounds, so a row is
  mathematically guaranteed to always land on solid paper, not just
  "usually".
- **House/building overlap.** v23's "non-overlapping" bands (buildings
  3-5, houses 5-7) still shared row 5 — the actual off-by-one that kept
  producing collisions. Buildings now live exclusively on rows 0-2 (the
  full farthest hill layer, reading as a skyline behind the
  neighborhood) and houses exclusively on rows 4-6, with row 3 left
  empty as a hard buffer so the two candidate clouds can never land on
  the same row.
- **Skyscrapers smaller than houses.** `drawSkyscraperBuilding` (and
  the restaurant/bar building variants sharing the SKYSCRAPER type) were
  multiplying by `r.spec.scale` a second time on top of the
  `canvas.scale()` already applied by the caller — squaring its effect
  instead of applying it once, like every other object type. A
  low-scale roll (common: the category's scale range is 0.65-1.15)
  shrank the building far more than intended, often below a house's
  size. Fixed to apply scale exactly once; the skyscraper's base height
  was also raised (130f -> 210f) to compensate for its new farther,
  smaller-depth-scale row band, so it reads as clearly taller on
  average, not just when the dice cooperate.

## v23 — in progress

- **Replaced the 3 shared placement lines with 9**, to fix houses/buildings
  overlapping once density was pushed up (an inherent problem of cramming
  up to 10 candidates per category onto just 2-3 shared Y positions).
  Each of the 3 visual hill layers is now subdivided into 3 distinct
  placement rows (own Y position, own depth scale), giving objects 9 total
  rows to spread across instead of 3 — the hill silhouette itself is
  unchanged, still 3 visual bands; only how objects are distributed within
  them changed. Buildings and houses were given non-overlapping row bands
  (buildings: rows 3-5, houses: rows 5-7) so a dense city and a full
  neighborhood no longer collide even at 100% density each.
- **This was a breaking change to what `layer` means** (0-2 before, 0-8
  now), so every existing hardcoded placement needed remapping: all
  "flavor" decorations (snowmen, gifts, balloons, penguins, bunnies,
  Easter eggs) moved from layer 2 to row 8 (the equivalent nearest
  position), and the Randomize generator's random layer pick now spans
  the full 0-8 range instead of being stuck on the old 0-2.
- **Fixed the "third row flying again" regression**: this was buildings
  using rows in the farthest hill band again (reintroduced when the v22
  uniform generator gave buildings layers [0,1] for skyline depth variety).
  Buildings now stay within rows 3-5 (the middle hill layer only),
  avoiding the farthest band's floaty-looking ground height entirely.
- Hill/mountain *count and color* customization (letting the user add/
  remove hill layers and recolor them, matching the reference screenshots)
  is intentionally not part of this update — noted as the next step, not
  bundled in here to keep this change reviewable on its own.

## v22 — in progress

- **Every theme now offers the same maximum customization range**: rewrote
  `SceneObjectCatalog` around a single deterministic generator producing
  exactly 10 candidate slots for each of the 6 customizable categories
  (houses, buildings, dogs, cars, umbrellas, trees), used identically by
  every theme. Previously themes had wildly different hand-authored
  counts (Big City: 8 buildings vs. most others: 1), which meant the
  theme itself — not the user — decided how "city-like" or "village-like"
  a scene could get. Now that choice belongs entirely to the density
  sliders in Scene Objects; theme identity comes only from colors and each
  theme's non-editable flavor decorations (snowmen, gifts, balloons,
  penguins, bunnies, Easter eggs), which are unaffected by this change.
  Beach's "trees" category generates palm trees instead of plain trees,
  sharing the same customization.
- **Fixed inverted depth perspective**: nearer objects were sometimes
  smaller than farther ones, because individual scale values had been
  hand-picked for variety without regard to which hill layer (i.e. how
  "close") the object was on. Added a per-layer depth-scale multiplier
  (far = 0.65x, mid = 0.85x, near = 1.15x) applied automatically in the
  renderer on top of each object's own scale, so perspective is now
  correct everywhere by construction rather than depending on how each
  of hundreds of hand-placed objects happened to be authored.
- **Buildings are now a mix of commercial types**: the "buildings"
  category still renders as one `SKYSCRAPER`-typed object internally (so
  the existing color customization keeps applying uniformly), but each
  instance stably (never randomly at runtime) picks one of three visual
  styles — tall office tower, storefront restaurant with a striped
  awning, or a bar with a hanging sign and string lights — instead of
  always being a skyscraper.

## v21 — in progress

- **Fixed CI compile failure from v20**: `LayerGeometry`'s `tileWidth =
  screenWidth` assignment failed to compile — `screenWidth` is an `Int`
  field on `PaperRenderer`, but `tileWidth` expects `Float`. This worked
  everywhere else in the same function (`screenWidth * 2f`, `shiftX %
  screenWidth`) because those go through arithmetic operators, which
  Kotlin has overloads for across mixed numeric types; a *plain*
  assignment has no such implicit widening. Fixed with an explicit
  `screenWidth.toFloat()`. Also corrected a doc comment on
  `LayerGeometry.tileWidth` left over from before v20 that still said
  "2x screen width" (objects now use 1x, screen width, as their own
  tiling period).

## v20 — in progress

- **Corrected the v19 anchor-position fix, which had introduced a new bug**:
  wrapping objects by taking `x mod screenWidth` guaranteed reachability
  (v19's goal), but it also *folded* the wider 2-screen-wide layout space
  down onto one screen — which means two objects originally placed a full
  screen-width apart (e.g. tileFractionX 0.10 and 0.60 in a 2-screen-wide
  layout) would land on the exact same on-screen pixel after folding. This
  is why Christmas started showing "5 houses" instead of 4: the fold
  disrupted the objects' relative left-to-right order and packed some of
  them closer together than intended. Fixed properly this time: objects
  now get their *own* tiling period (screen width), computed independently
  from the hill silhouette's wider tile at the source
  (`PaperRenderer.drawHillLayers`), instead of trying to fold the hill's
  existing wide period after the fact. At rest, this maps every
  `tileFractionX` linearly and uniquely across the visible screen — no
  folding, no collisions, and (re-verified) no unreachable positions
  either.
- **Fixed background buildings appearing to float in the sky**: the lone
  "background accent" skyscraper added to 8 themes (Sunset, Autumn,
  Winter, Desert, Christmas, Beach, Tundra, Easter) sat on the farthest
  hill layer, whose fixed ground height is the highest up the screen of
  the three layers — visually closer to open sky than solid ground,
  especially with nothing else nearby to anchor it. Moved to the middle
  layer instead. Big City and New Year's Eve's original skylines (which
  already mix near/far layers across many buildings) were left untouched,
  since a dense skyline reads fine even with some buildings on the
  farthest layer.
- **Santa's sleigh stutter**: the per-paint alpha fix in v19 removed the
  `saveLayer` call, which was the *known, provable* cause of the earlier
  stutter — that fix stands. If choppiness is still present after this
  update, it's a separate cause and needs fresh reporting with specifics
  (which theme, roughly how choppy, whether other animations stutter at
  the same time) rather than assuming it's the same root cause again.

## v19 — in progress

- **Found and fixed the real cause of "houses/buildings missing despite
  100% density"** — this was not (only) about frozen overrides as
  previously suspected; it was a genuine, provable math bug. Objects were
  wrapped for scrolling using the *hill* tile width (2x screen width), but
  only 3 fixed copies were drawn per object. Worked out on paper (and
  verified with an exhaustive simulation across the full range of scroll
  positions): with that spacing, roughly **half of all possible
  `tileFractionX` values were mathematically unreachable by any drawn
  copy** at common scroll positions — at rest, only positions in
  [0.25, 0.75] ever landed on-screen at all, regardless of density,
  visibility, or theme. This explains every symptom reported: Big City's
  buildings are spread across a wide range of positions (some always
  landed in the visible band), while every other theme's single
  background building sat near the tile edge (0.90+), permanently outside
  it; Beach's houses/building were placed at the very edges for the same
  reason. Fixed by wrapping objects using the *screen* width instead of
  the wider hill tile width as the period — proven correct by exhaustive
  simulation (0 unreachable positions across the full valid scroll range,
  versus 49% before). Cars were not affected (they already use a
  different, non-tile-based positioning system) — their absence is more
  likely explained by a frozen override (see v17) or the sleigh's frame
  stutter below.
- **Santa's sleigh**:
  - Reindeer brought much closer to the sleigh (positive offsets reduced
    from 150/95 to 85/50) so the harness reads as an actual connection
    instead of two disconnected halves.
  - Removed `Canvas.saveLayer` (added in v18 for the edge fade) — it
    allocates an offscreen buffer on every call, expensive enough to cause
    visible stutter every frame the sleigh was on screen. Replaced with
    per-paint alpha blending (same fade effect, no offscreen buffer).
  - Falling gifts redesigned with a ribbon cross and bow (matching the
    static under-the-tree gift look) instead of a single flat-colored
    square, which read as a bomb/mine rather than a wrapped present.

## v18 — in progress

- **Fixed Santa's sleigh appearing to drag the reindeer instead of being
  pulled by them**: the reindeer were drawn at a *negative* local x-offset
  relative to the sleigh, which put the sleigh ahead of them in the
  direction of travel — visually, Santa looked like he was towing the
  reindeer behind him. Flipped the offsets so the reindeer lead and the
  sleigh trails behind, in both flight directions.
- **Fixed the sleigh vanishing abruptly at the edges of its flight**
  instead of fading out: added a ~8%-of-flight fade-in/fade-out window
  (via a `Canvas.saveLayer` alpha group covering the whole reindeer+
  sleigh+Santa illustration) so it now eases in and out gracefully rather
  than hard-cutting the moment it crosses off-canvas.

## v17 — in progress

- **README disclaimer** added at the very top, as requested verbatim.
- **More bottom clearance in "Manage Themes"/"Scene Objects"**: increased
  the bottom safety spacer from 24dp to 56dp — the last row of theme cards
  was sitting close enough to the screen edge that curved-corner phones
  could visually clip the leading character of the theme name.
- **Diagnosed and addressed: Christmas showing far fewer objects than
  defined** (user reported 2/4 houses, 0/1 buildings, 0/1 cars despite
  everything set to visible/100%). Verified the actual source definitions
  are correct and healthy across *every* theme (counts checked
  programmatically, not eyeballed) and that the density-filter math is
  airtight at 100% (mathematically guaranteed to keep every candidate).
  The most likely explanation: a **frozen override** — "Replace with
  current" permanently snapshots a theme's objects at save time, so a
  theme customized (even accidentally, during earlier testing) before a
  later app update added more objects to it keeps rendering the old,
  smaller object set forever, no matter what the live Scene Objects
  sliders say. This is a structural risk for *any* theme that's ever been
  overridden, not a Christmas-specific bug. Added a one-tap fix: **"Reset
  all customized themes to default"** in Manage Themes (only shown when at
  least one override exists), so every theme can be restored to its
  current, up-to-date built-in definition at once instead of having to
  check each one individually.

## v16 — in progress

- **Fixed "Manage Themes" not scrolling all the way down**: the last row of
  theme cards (Tundra/Easter) was getting cut off, names unreadable.
  Likely cause: full-screen `Dialog()` windows (used by both "Manage
  Themes" and "Scene Objects") don't automatically inherit the Activity's
  `enableEdgeToEdge()` inset handling the way the main screen's `Scaffold`
  does. Added explicit `navigationBarsPadding()` to both dialogs' scrollable
  content, plus a small bottom spacer as extra safety margin so the last
  row is never flush with the screen edge.
- **Manual "Check for updates" button** on the main settings screen, next
  to the version number — the existing check was launch-only with no way
  to trigger it on demand. Reuses the same `UpdateChecker` call and the
  same "update available" dialog; if you're already up to date, it says so
  inline instead of silently doing nothing.

## v15 — in progress

- **Fixed CI compile failure from v14**: `SettingsScreen.kt`'s `snapshotEntry()`
  called the `keepCandidate`/`keepCar` extension functions (defined in
  `engine/SceneCustomization.kt`) without importing them. Kotlin requires an
  explicit import for extension functions used outside their own package —
  unlike regular classes/objects, having other symbols from that package
  already imported isn't enough. Added the two missing imports.

## v14 — in progress

- **Fixed stale "appear in the themes that have them" copy** in Scene
  Objects — and, before just rewording it, actually verified the claim: it
  turned out only houses/buildings had candidates in every theme. Umbrellas
  existed only in Beach; several themes were missing dogs, cars, or trees
  entirely. Added the missing candidates across all 10 themes (programmatically
  verified, not eyeballed) so all 6 categories are genuinely usable
  everywhere, then updated the copy to "$title can appear in every theme".
  Caught and fixed a duplicate-`cars`-parameter bug introduced while adding
  car lanes to Beach/Tundra/Easter (they already had `cars = emptyList()`).
- **Scene Objects now apply live to the current theme only**, not globally
  to every theme — a real architecture change, not just a UI tweak:
  - `CustomThemeEntry` now carries its own `SceneCustomization` snapshot
    (density/visibility/colors), serialized alongside its theme/layout.
  - `CustomThemeRegistry.resolveActiveCustomization()` is the single
    authority for "what customization applies to theme X right now":
    a saved theme (override or standalone custom) always uses its own
    baked-in settings; otherwise, an in-progress live edit only applies if
    it's tagged for that exact theme; otherwise, plain defaults.
    Both `PaperWallpaperService` (the real wallpaper) and `SettingsScreen`
    (previews/dialogs) go through this same resolver, so they can never
    disagree with each other.
  - `WallpaperPrefs`'s category setters now take a `forThemeId` and stamp
    a `pendingCustomizationThemeId` tag in the same atomic DataStore edit,
    so switching themes automatically stops applying an in-progress edit
    to the theme you've moved away from — without discarding it, so
    switching back resumes where you left off.
  - "Manage Themes" → Replace with current / Save as new theme now
    correctly bakes in whatever density/visibility/colors were live at
    save time (previously this was silently ignored — a saved theme always
    got the *unfiltered* candidate list regardless of what was on screen).
- **Dogs reverted to their original (pre-v12) size** — doubled, they read
  as oddly large next to everything else. Every other object stays doubled.
- **Houses redesigned** with more visual detail: foundation strip, roof
  overhang with ridge/shingle lines, a chimney with a cap, an arched door
  with a doorknob and entry step, and windows with a sill and cross-mullion
  divider — replacing the previous plain rectangle-plus-triangle.
- **Sun and moon doubled in size** (previously read as too small).
- **The moon now follows its real phase** (new/crescent/quarter/gibbous/
  full) instead of always being a plain circle at night — computed from
  the actual ~29.53-day synodic month cycle against a known reference new
  moon, rendered with the classic half-disc-plus-terminator-ellipse
  technique. Verified by hand against all four key phases (new, first
  quarter, full, last quarter) before considering it correct.

## v13 — in progress

- **In-app update checker**, on every app launch (never a background
  service, never a system notification):
  - `update/UpdateChecker.kt` makes a single HTTPS request to the public
    GitHub Releases API (`/repos/{owner}/{repo}/releases/latest`), compares
    the returned tag (e.g. `v13`) against `BuildConfig.VERSION_CODE`, and
    fails silently on any network/parsing error — a broken connection must
    never crash or interrupt app startup.
  - If a newer version is found, an in-app dialog offers **"Update now"**
    (opens the GitHub release page in the browser — the app does not
    silently download or install anything itself, keeping the user in
    control of the install step) or **"Remind me later"**, which opens a
    second choice: **"Next app launch"** (no-op — the check already runs
    every launch by design) or **"In a month"** (persisted via
    `update/UpdatePrefs.kt`, tied to that specific version so a newer
    release during the snooze period still prompts immediately).
  - Requires the `INTERNET` permission — previously the README stated the
    app made *no* network calls at all; that claim is now updated to be
    accurate rather than left stale. This is still the *only* network
    access anywhere in the app: the wallpaper engine itself remains fully
    offline.
  - **Repo-specific**: `UpdateChecker`'s `OWNER`/`REPO` constants are set to
    the original repository. Update them if you fork or rename it, or the
    checker will silently find nothing (404s fail the same as no internet).

## v12 — in progress

- **App version shown in-app**: a "Version vN (x.y)" row at the bottom of
  Settings, read from `BuildConfig.VERSION_CODE`/`VERSION_NAME` (required
  enabling `buildFeatures.buildConfig = true`, off by default since AGP 8).
- **Every theme now has both house and building candidate slots**: 8 themes
  previously had no buildings at all and 2 had no houses at all — added a
  small background building/house to each so "show houses"/"show
  buildings" and the density slider always have something to work with,
  regardless of theme. Verified programmatically (all 10 themes checked)
  rather than just by eye.
- **Generalized the density+colors customization system from
  houses/buildings to 4 more categories**: dogs, cars, umbrellas, and
  trees now get the same treatment — a visibility toggle, a 0-100% density
  slider, and 4 editable day/night colors each (2 variants, deterministically
  assigned per instance, blending day→night like the rest of the scene).
  - Rewrote `HouseBuildingConfig.kt` into `SceneCustomization.kt`:
    `SceneCustomization` now holds one `ObjectVariantConfig` per category
    instead of hand-duplicated fields, with generic `keepCandidate()` /
    `colorFor()` helpers shared across all 6 categories.
  - `WallpaperPrefs` rewritten with per-category DataStore keys generated
    from the `ObjectCategory` enum, instead of 44 hand-written fields.
  - The "Houses & Buildings" screen became "Scene Objects": one collapsible
    section per category, all built from a single reusable
    `ObjectCategorySection` composable instead of six copy-pasted blocks.
  - The live preview (added in v11) now shows a house, tree, dog, and
    building together instead of just the first two.
  - Parasols are a special case: their 5 wedges alternate between the 2
    configured colors per-wedge (not one color per whole umbrella like
    every other category), via a dedicated `parasolStripeColor()` helper.
- **Doubled the size of every scene element that was too small**: houses,
  buildings, dogs, trees, and cars (via a single `GLOBAL_OBJECT_SCALE = 2f`
  multiplier applied at their `canvas.scale()` call), the road they drive
  on (margins, stroke widths, and dash sizes doubled to match the now-
  bigger cars), and Santa's sleigh + the gifts it drops. Left the paper-bird
  touch effect and firework bursts at their original size — these are
  short-lived touch/particle effects, not persistent scene content, so
  they weren't part of what "too small" was describing.

## v11 — in progress

- **Touch-and-drag color picker**: replaced the three linear Hue/Saturation/
  Brightness sliders with the classic "drag your finger across the
  palette" UX — a saturation/brightness square (drag or tap to jump) plus
  a draggable hue strip, hex field still there for precise/typed input.
  Used for all 8 house/building colors.
- **Fixed house/building color changes not visibly taking effect**: found
  two real gaps while investigating —
  1. `PaperWallpaperService` only forced an immediate redraw when the
     *theme* changed, not when `HouseBuildingConfig` changed on its own
     (e.g. just picking a new house color). The engine would technically
     pick up the new colors on its next scheduled frame, but there was no
     guaranteed *immediate* redraw the way theme changes already got.
     Fixed: config changes now force an immediate redraw too.
  2. More importantly: **none of the in-app previews ever drew houses or
     buildings at all** (`ThemeScenePreview` only ever drew sky/hills/sun),
     so changing a color produced no visible feedback anywhere inside the
     app itself — you'd have had to back out to the actual home screen to
     see anything. Fixed by adding a real live preview (one house + one
     building, drawn with the exact same code the wallpaper uses, via a
     new `SceneObjectRenderer.drawPreviewPair()`) directly at the top of
     the Houses & Buildings screen, with a day/night toggle since colors
     blend between the two.
- "Reset to defaults" remains exactly as before — confirmed still fully
  wired end to end while making the above changes.

## v10 — in progress

- **Configurable houses & buildings**, applied globally across every theme:
  - Independent "show houses" / "show buildings" toggles.
  - A single **density slider (0-100%)** that thins each theme's candidate
    house/building slots in a stable, non-flickering way (based on each
    object's fixed position, never `Random()`), so moving the slider adds
    or removes the same specific houses each time rather than reshuffling.
  - **4 editable colors each** for houses and buildings (Day 1, Night 1,
    Day 2, Night 2). Each individual house/building instance is
    deterministically assigned variant 1 or 2 and blends between its
    variant's day/night color exactly like the rest of the scene — houses
    at night don't all suddenly look identical or flicker between colors.
  - New reusable **HSV color picker** (hue/saturation/brightness sliders +
    two-way-synced hex field) for editing any of the 8 colors.
  - New "Houses & Buildings" screen hosting all of the above, plus a
    "Reset colors to defaults" button.
- **More house/building candidate slots** added to most themes (Sunset,
  Autumn, Winter, Desert, Christmas, Easter, Beach, and Tundra now have
  multiple house slots; New Year's Eve and Big City have more skyscraper
  slots, City reaching 8 at 100% density) — needed so the density slider
  has an actual range to work with, since most themes previously only
  defined 1 house.
- New files: `engine/HouseBuildingConfig.kt` (config model + stable
  per-instance density/color-variant helpers).
- Fixed a bug introduced while wiring this up: `SceneObjectRenderer`'s
  `drawRoad()` still referenced the constructor's `layout` parameter after
  it stopped being a stored property (needed so `layout.staticObjects`
  could be filtered by the new config before becoming the renderer's
  runtime object list) — switched to reading lane positions from
  `carRuntimes` instead.

## v9 — in progress

- **Custom themes**: new "Manage Themes" screen (roadmap item #2, still with
  color values inherited from the current look rather than a full
  color-picker editor — see note below) —
  - **Save the current look as a new theme**, with your own name.
  - **Replace any built-in theme with the current look** ("Replace with
    current"), overriding it everywhere (live wallpaper, previews) while
    keeping its original name and slot in the gallery.
  - **Reset to default** on any customized built-in theme, one tap, fully
    reversible — removes the override and instantly restores the original.
  - **Rename** and **delete** for your own independent custom themes.
  - New files: `engine/CustomThemeData.kt` (data model + hand-rolled JSON
    (de)serialization via `org.json`, already built into Android -- no new
    dependency), `prefs/CustomThemeStore.kt` (DataStore persistence),
    `engine/CustomThemeRegistry.kt` (synchronous in-memory cache, since the
    render thread calls `ThemeCatalog.byId`/`SceneObjectCatalog.layoutFor`
    synchronously and can't suspend on a DataStore read).
  - Fixed a cache-invalidation bug this surfaced: `PaperRenderer`'s object-
    layout cache was keyed only on `theme.id`, but overriding/resetting a
    built-in theme changes what that *same* id resolves to without the id
    changing -- added a generation counter to `CustomThemeRegistry` that
    the cache now also checks.
  - Not yet a full color-picker editor: "custom" currently means "a saved
    snapshot of a look you already reached" (via Randomize, or a future
    picker), not hand-picking individual colors in a dedicated UI. Full
    per-color editing is still a follow-up.
- **Gallery overhaul**: theme previews now render an actual mini scene (sky
  gradient, real hill colors, sun) plus emoji hints for signature objects,
  instead of a flat color swatch — both in the inline gallery and the new
  Manage Themes screen.

## v8 — in progress

- **Fixed app updates failing with "app not installed"**: every CI run was
  signing the debug APK with a fresh, randomly-generated debug certificate
  (GitHub Actions runners start clean each time), so upgrading from one
  version to the next meant installing over a build signed with a
  *different* key — Android refuses that. Fixed by committing a fixed
  `debug.keystore` at the repo root (standard, publicly-known debug
  credentials — holds no real security value, this is not a
  release-signing key) and wiring it into `app/build.gradle.kts`'s debug
  `signingConfig`, so every build (CI or local) is signed identically and
  upgrades work.
- **Added a prominent "Set as wallpaper" button** right under the live
  preview at the top of the settings screen — reachable immediately,
  without scrolling, instead of only being available via the phone's
  system wallpaper picker.
- **Fixed broken scrolling in the settings screen**: the main layout was
  missing a scroll modifier entirely, so anything taller than one screen
  (Touch effects, Parallax strength, and everything below) was simply
  unreachable. Added `verticalScroll` to the root column.
- **Replaced the theme picker with a real gallery**: instead of small flat
  color swatches, each theme now shows an actual mini scene preview (sky
  gradient, layered hills in the theme's real colors, the sun) plus emoji
  hints for its signature objects (🎄🎁 for Christmas, 🐰🥚 for Easter,
  etc.) — laid out as 2-per-row cards so you can actually see what a
  theme looks like before applying it.

## v7

- **Fixed CI build failure introduced by v5's own security fix**: the
  `gradle wrapper --gradle-version 8.9` step (which regenerates the
  wrapper since `gradle-wrapper.jar` isn't committed) started failing
  after v5 added `distributionSha256Sum` to `gradle-wrapper.properties`.
  Gradle's `wrapper` task refuses to run when the properties file already
  has a checksum it wasn't explicitly told to reproduce, rather than
  silently dropping or mismatching it. Fixed by passing
  `--gradle-distribution-sha256-sum` with the same value to the CI command.
- **Release naming now tracks `versionCode`, not the Actions run number**:
  the GitHub Release created on every successful push is now tagged/titled
  `vN` from `app/build.gradle.kts`'s `versionCode` (bumped to 7) instead of
  the unrelated Actions run counter — so the release name always matches
  the version delivered in chat. If the same `versionCode` is pushed again
  (e.g. a quick follow-up fix before bumping it), the existing release for
  that tag is replaced rather than failing the build.

## v6

- **Automatic theme by date/period** (opt-in setting, roadmap item #1):
  new `SeasonalThemeRules.kt` resolves the current date to a themeId —
  Christmas (Dec 18 – Jan 6), New Year's Eve (Dec 30 – Jan 1, takes
  priority over the Christmas window), Easter (± 3 days around Easter
  Sunday, calculated with the standard Computus algorithm — not a fixed
  date), and summer/Beach (Jun 21 – Sep 21). Falls back to the user's
  manually selected theme when no window matches. Re-evaluated on every
  settings change and whenever the wallpaper becomes visible again (so a
  day boundary crossed overnight is picked up promptly). Designed from the
  start to resolve through a plain `themeId` string, so the future custom
  theme editor can plug in without any changes here.
- **New Easter theme**: pastel spring palette, plus two new object types
  (`EASTER_EGG`, decorative; `BUNNY`, tappable) added to the shared object
  system — also available in the Randomize pool.
- Settings screen shows a live "today's automatic theme" indicator when
  the feature is on, and the preview card reflects the effective
  (possibly auto-overridden) theme rather than only the manual pick.
- Renamed the remaining Italian Kotlin identifiers (`NATALE` →
  `CHRISTMAS`, `CAPODANNO` → `NEW_YEAR`, `SPIAGGIA` → `BEACH`, `CITTA` →
  `CITY`) that were missed in v5's string/ID translation pass — only the
  `val` constant names, not user-facing text, so this has no visible
  effect but keeps the codebase's identifiers consistent with the rest of
  v5.

## v5

- **Security & hardening remediation** (reviewed against an external
  security assessment, cross-checked line by line against the real code
  before applying anything):
  - Location updates are now properly stopped (and `hasFixLocation` reset)
    when the user disables the "use location for sunrise/sunset" toggle —
    previously they kept running in the background until the wallpaper
    engine was destroyed, ignoring the user's choice.
  - Added the official SHA-256 checksum (`distributionSha256Sum`) for the
    Gradle 8.9 binary distribution, verified against
    `gradle.org/release-checksums`.
  - CI workflow hardened: added a least-privilege `permissions: contents:
    read` default (with `contents: write` scoped only to the job that
    publishes releases), and pinned `actions/checkout`, `actions/setup-java`,
    and `actions/upload-artifact` to full commit SHAs (cross-verified
    against independent sources) instead of mutable version tags.
    `gradle/actions/setup-gradle` is **not yet pinned** — a verified full
    SHA for its current `v5` tag could not be resolved; left as an
    outstanding item rather than guessing (a wrong SHA would silently break
    every build).
  - `android:allowBackup` disabled (`false`) so wallpaper preferences are
    never swept into Android cloud backup / `adb backup`.
  - Dependency freshness (AGP 8.7.2, Kotlin 2.0.21, and the various AndroxX
    libraries) was **not** changed in this pass — deliberately kept as a
    separate future commit from security fixes, so a dependency-bump
    regression can be reverted without losing the security work. No CVE
    scanner was run (none available in this environment); treat this as
    unverified rather than "confirmed clean".
  - Not checked (out of scope without deeper tooling): git history for
    leaked secrets, and no compiled APK was independently audited.
- **Full English translation**: all user-facing strings (`strings.xml`,
  the Compose settings screen, theme display names), all internal theme
  IDs (`natale` → `christmas`, `capodanno` → `new_year`, `spiaggia` →
  `beach`, `citta` → `city`), and all project documentation (README,
  CONTRIBUTING, this changelog) are now in English for an international
  audience.
- **Automatic GitHub Releases**: every successful CI build on a push to
  `main` now creates a GitHub Release (tagged `build-<run number>`)
  containing only the debug APK, via the GitHub CLI already available on
  the runner (no extra third-party action added, keeping the CI's
  supply-chain surface as small as possible).

## v4

- **Smoother parallax while swiping between home screens**: the hills
  (`buildHillPath`) were being rebuilt from scratch — random control points
  included — on every single frame, even though the shape never actually
  changed (only its position did). Each layer's shape is now computed once
  (cached, invalidated only on theme/screen-size change), and every frame
  just applies a `canvas.translate()`, which is far cheaper. On top of
  that, redraws now fire immediately when a new offset arrives from the
  launcher (`onOffsetsChanged`), instead of waiting for the next scheduled
  ~33ms tick.
- **Fixed snowman/tree interaction**: tapping them now triggers an
  amplified wobble/sway (with sound) instead of just spawning a paper bird
  like the free background does.
- **Two-lane road** under the cars (edges + dashed center line, darker at
  night), in every theme that has traffic.

## v3

- **Full rename**: the project is no longer called PaperScape but
  **PaperScrape** — Kotlin package (`com.paperscrape.livewallpaper`),
  `applicationId`, app name, Compose theme (`PaperScrapeTheme`), Android
  style (`Theme.PaperScrape`), all references in README/CONTRIBUTING.
- **Cats removed** from the whole app (scene objects, sounds, random
  generator, UI text).
- **Santa's sleigh**: new periodic event (Christmas theme) — at random
  intervals it crosses the sky pulled by two reindeer, dropping gifts.
- **Wiki section** added to the README (themes, objects/interactions,
  settings explained in tables).
- **Roadmap updated**: removed the "real sounds" goal; added a new
  priority goal, "automatic theme by date/period"; noted the planning
  needed to connect the custom theme editor to the date-based automation
  (see the Roadmap section in the README).
- Introduced this changelog and the versioning convention.

## v2

- Fixed 2 compile errors surfaced by CI (`companion object` not allowed
  inside an `inner class`; missing opt-in for Material3's experimental
  `TopAppBar` API).
- Silenced the AGP warning about `compileSdk 36` not yet being certified.
- Updated CI actions (`checkout`, `setup-java`, `setup-gradle`,
  `upload-artifact`) to the latest versions compatible with Node 24,
  resolving the deprecation warnings.
- Removed all textual references to third-party products from
  README/CONTRIBUTING/code comments.

## v1

- First working release: paper-layer rendering engine with parallax,
  day/night cycle, 4 base color themes (Sunset, Autumn, Winter, Desert).
- Animated, interactive objects (cars, dogs, cats, houses, trees) with
  touch reaction and synthetic sound.
- 5 additional seasonal/festive themes as distinct scenes (Christmas,
  New Year's Eve with fireworks, Beach, Big City, Tundra).
- Randomize function: procedural generation of themes/objects.
- Repo structure ready for GitHub: MIT license, `.gitignore`, GitHub
  Actions CI, README, CONTRIBUTING.
