> **ARCHIVED — superseded by `ROADMAP.md`.**
>
> This file is the project's original roadmap, kept verbatim for its historical
> and technical content: the reasoning behind past decisions, measurements taken
> at the time, and ideas not yet scheduled. It is **not** the operational plan
> and must not be used to decide what to work on next.
>
> The authoritative operational plan is `ROADMAP.md`. Where the two disagree,
> `ROADMAP.md` wins. Nothing below has been edited.

---

# PaperScrape — Roadmap

> Source of truth for upcoming development phases. Should be read and
> followed exactly as written — only edit it on aa's explicit request,
> never summarize, reinterpret, or alter it on its own initiative in a
> future conversation.

Last updated: after v72.

**Current initiative, cross-cutting every phase below**: aa provided a
full export of the reference app's real sprite atlases (`res/drawable-nodpi/*.png`,
142 files, both from a jadx decompile and a direct `unzip` of the device
backup's own `base.apk` -- verified identical count in both) plus its 7
GLSL shaders, and asked for two things: (1) eventually convert every
remaining vector-drawn scene object to sprite-based rendering (CPU/
battery), continuing what v65 piloted for houses/buildings/trees/palm
trees, and (2) substantially improve every sprite's *aesthetics*,
informed by that reference (not copied -- original art, see each
delivery's own CHANGELOG entry for the "original, not copied" framing).
Given the scope (~25 more object categories beyond the v65 pilot), agreed
with aa on a **one-batch-at-a-time approach, each batch independently
verified/approved before starting the next**, rather than one giant
delivery. Batches so far:
- ✅ **Batch 1 (v66)**: house (wall/roof/trim/window), tree (canopy +
  snowcap), palm tree (trunk/fronds/frost) -- the 4 things v65's pilot
  had already converted to sprites, completely redrawn for aesthetics.
  Also changed `drawTintedSprite`'s tint mode `SRC_IN` → `MULTIPLY` so
  the new sprites' baked-in "paper fold" mottling survives the runtime
  tint as shading -- see that CHANGELOG entry's explicit color-fidelity
  callout (a few percent darkening vs the exact configured hex, given
  this project's history with the removed paper-grain overlay; kept
  low-strength and scoped per-object, not a full-scene overlay, but
  flagged for aa's own eyes on a device before treating it as settled).
- ✅ **v67 fix**: aa confirmed batch 1 looked good except the palm tree's
  fronds reading as slightly detached from the trunk -- traced to stale
  v65-pilot anchor constants never recalibrated for v66's redrawn
  (centered/symmetric) frond art, fixed with a documented recalculation.
  See CHANGELOG for the exact before/after world-position numbers.
- ✅ **Batch 2 (v67)**: skyscraper (wall only -- window grid stays
  vector, per-instance randomized), restaurant (wall/awning/door/
  window), bar (wall/door -- hanging sign/string lights stay vector).
- ✅ **Batch 3 (v68)**: cars. **Not pure reskin like batches 1-2** --
  PaperScrape only ever had one generic user-colored car before this;
  added a real `CarType` enum (PLAIN/POLICE/TAXI/FIRE_TRUCK) with a
  stable weighted assignment per candidate, fixed non-tintable colors
  for the 3 special types (matching the reference's own fixed-color
  vehicle sprites), and their own accessory sprites (light bar, checker
  stripe, roof ladder). Deliberately did NOT add per-type show/hide
  settings this round (scope decision, see CHANGELOG) -- all 4 types
  still respect the existing "Show Cars" visibility/density uniformly.
  If aa wants independent toggles (matching the reference's own
  "Police Cars / Fire Trucks / Taxis" checkboxes), that's real new
  plumbing across `SceneCustomization`/`WallpaperPrefs`/
  `CustomThemeData`/Settings UI -- worth its own delivery, not bundled.
  **CI caught a real build-breaking bug in the first attempt** (a
  duplicate `reverse` field in `CarObject` from a sloppy text edit,
  surfacing only as confusing errors at unrelated call sites) --
  fixed before any working release existed, see CHANGELOG for the full
  trace. This is the concrete example, not just a hypothetical, of why
  every delivery from v66 on has been flagged as unverified without a
  real Android SDK available to this environment.
- ⏳ **Batch 4, part 1 (v69) -- terrain sub-group**: dolphin/sailboat genuinely
  sprite-converted (the reference's own `Dolphin`/`SailboatBottom`/
  `SailboatSails` really do blit `land1.png` textures). Mountains/hills got a
  procedural "paper fold" aesthetic pass instead of a sprite conversion --
  cross-checked against the decompiled reference first and confirmed
  `Mountain`/`Hills` are genuine vertex-colored GL geometry with **no
  texture** even in the reference app itself, so there's nothing to convert *to*
  there. **Batch 4 still has the rest to go**: clouds, birds, sun/moon
  (+ moon phases), stars, rainbow, Santa/reindeer/sleigh, and every
  seasonal decoration (snowman, gift, parasol, penguin, Easter egg/bunny,
  balloon, pumpkin). No agreed order yet for what comes after terrain --
  pick up with aa next session.
- ✅ **v70 fix**: mountains were rendering as an invisible triangle (a geometry
  bug in v69's two-face fill), and dolphins/sailboats were almost always
  hidden behind the hill layer (a placement bug, not new in v69 but only
  now reported/fixed). Both root-caused with rendered Python mocks of the
  exact math before touching the Kotlin -- see CHANGELOG for the numbers.
- ✅ **Batch 4, part 2 (v71) -- clouds & rainbow sub-group**: clouds
  genuinely sprite-converted (real texture in the reference, and the single
  most expensive per-frame vector-drawn path in this file -- 4 `Path.op`
  booleans x up to 41 candidates/frame, now one sprite blit each). Rainbow
  deliberately left procedural this round (see CHANGELOG for why -- its
  size scales with screenWidth, unlike every other sprite-converted object
  so far, so a real conversion needs its own dynamic-scale draw path, not
  a rushed bolt-on onto the shared fixed-size sprite convention), given a
  cheap aesthetic touch-up instead (inner-edge highlight per band).
  **Batch 4 still has the rest to go**: birds, sun/moon (+ moon phases),
  stars, Santa/reindeer/sleigh, and every seasonal decoration (snowman,
  gift, parasol, penguin, Easter egg/bunny, balloon, pumpkin), plus
  rainbow's own proper sprite conversion whenever that's worth its own
  delivery. No agreed order yet -- pick up with aa next session.
- ✅ **Batch 4, part 3 (v72) -- birds & sun/moon+stars sub-group**: all 4
  genuinely sprite-converted (real textures in the reference for all of
  them, unlike the terrain sub-group's mountains/hills). Moon phases now
  use 4 real baked silhouettes reused via 180° rotation for the waning
  half -- the reference's own actual technique, not just a visual
  approximation of it (replaces the old geometric ellipse-width approach).
  Bird flap animation now matches the reference's own sign-flip technique
  too, not just cheaper to draw. **Batch 4 still has the rest to go**:
  Santa/reindeer/sleigh, and every seasonal decoration (snowman, gift,
  parasol, penguin, Easter egg/bunny, balloon, pumpkin), plus rainbow's
  own proper sprite conversion whenever that's worth its own delivery.
  No agreed order yet -- pick up with aa next session.
- **None of v66-v72 could be verified with a real `compileDebugKotlin`/
  `lintDebug`/device build** -- the environment these were produced in
  has no Android SDK. Manually cross-checked instead each time (pixel
  dimensions against bbox comments, every `R.drawable.*` reference
  against an actual file present, no leftover dead code from removed
  vector branches; v68 additionally hand-verified all 4 `CarObject(...)`
  construction sites stayed in sync and `CarType.entries` is valid for
  this project's Kotlin version). Treat all three as less proven than
  usual until CI confirms and, ideally, aa has looked at them live on a
  device -- v68 especially, since it's the first of these batches to
  add real new logic (a data model field + JSON serialization) rather
  than only new pixels.

---

Older history below this point (pre-dates the sprite initiative above;
kept for archival context, not the current restart point):

Last updated: after v58 -- aa reported the paper-grain effect still
pegged CPU cores even after v56's scale-down, and -- more fundamentally
-- that colors no longer matched what was actually configured (a sky
set to light blue rendering gray on device). Asked for it to be removed
entirely rather than tuned further; revisit later if at all. Deleted
`PaperGrainTexture.kt`, its drawable resource, every `apply()` call
site, and the `Context` parameter `PaperRenderer` only needed for that
resource. Every element is back to flat, exact color fills -- no
multiply blend, no per-frame shader/clip work. Also re-verified parasols
(re-reported as floating): the v57 continuous-depth anchoring math
checks out numerically (groundY safely within the hill's guaranteed-
solid zone, clear of the lake even at max Beach settings), so the fix
was strengthening the v53 ground shadow (wider, darker) rather than
re-deriving placement math that was already correct. Verified with a
real compileDebugKotlin + lintDebug build (confirming no leftover
references to the removed class/resource anywhere in the project).

Next up, once aa confirms the v58
build actually looks and performs right: Phase 1, point 1d (dynamic
weather), or further reference-informed fixes if more come up first.

## Current state (confirmed by reading the code, not from memory)

What already exists and works: houses/buildings/cars/trees/umbrellas
(structural categories, "Scene Objects"), seasonal decorations (snowmen/
gifts/balloons/penguins/bunny/eggs/pumpkins, "Seasonal Decorations",
per-theme with sensible defaults and overridable), built-in themes +
saveable custom themes, automatic theme by date (Christmas/New Year/
Easter/Beach only), fireworks (New Year), Santa's sleigh with falling
gifts (Christmas), mountains, a lake with sailboats/dolphins, an ambient
bird flock, hills with editable day/night color + variation, global
scroll settings (v36).

What does NOT exist (verified via grep on the code, not assumed):
- Hills: height is still hardcoded (deliberately left alone, see Phase 0
  point 4 below)
- Stars: fixed count (70), fixed theme color, not editable
- Sky: fixed 4-color gradient per theme, no user controls, no concept of
  "High/Low" or dedicated sunrise/sunset colors
- Sun/Moon: color tied to the theme, no independent show/hide toggle, no
  "realistic moon phases" option
- Clouds/rain-snow/rainbow/weather: zero, no file at all

## Phase 0 — General scene elements ✅ DONE (v36)1. ✅ **Birds** — done. Ambient flock, ~~4 colors each with a weighted
   frequency~~ implemented as `BirdsConfig`/`BirdColorWeight` (weighted
   random pick, "Bird Color Frequencies" in the UI), show/hide, density,
   "Night Birds" toggle. Per-theme.
2. ✅ **Lakes/bodies of water** — done. `LakeConfig`: show/hide, day/
   night color, lake height (%), nested sailboats (show/#) and dolphins
   (show/#). Off by default. Per-theme.
3. ✅ **Mountains** — done. `MountainLayerConfig` x2 (Front/Back),
   independent show/hide, day/night color, density each. Visible by
   default. Per-theme.
4. **Hills**: expose what's currently hardcoded as user sliders — day/
   night color, hills height (%), hills variation (%)
   - ✅ **Variation** — done in v35, per-theme (added as a plain field on
     `SceneCustomization`, reusing existing per-theme machinery), safely
     bounded (interpolates toward the center of the already-proven-safe
     random range, never exceeds it)
   - ~~⏳ **Height** — deliberately deferred: `heightFractions`/`yOffsets`
     are the same geometry `ROAD_SAFE_ROW_LIMIT`/`HILL_SAFE_ROW_MIN`/`MAX`
     depend on (v24/v28's floating-object and road-clipping fixes).
     Scaling height requires correctly re-deriving that placement math
     alongside it, not just the visual silhouette — needs its own
     careful pass, not to be rushed under time/token pressure.~~
     **Struck through, not removed, on purpose: current height behavior
     is considered good as-is and won't be touched. Kept here only as a
     reminder this was looked at and deliberately left alone, not
     forgotten.**
   - ✅ **Day/Night color** — done in v36. Single Day Color + Night Color
     per theme (not one color per layer); the 3 depth layers auto-derive
     their own shade via `PaperRenderer.hillLayerColor()` (progressively
     darker toward black for nearer layers, checked against sunset's own
     hand-authored palette ratios). `defaultCustomizationFor()` seeds
     each theme's starting color from its own existing farthest-layer
     tone via `ThemeCatalog.byId()`.
5. ✅ **Parallax desync between hills and static objects while
   scrolling** — root cause found last session, fixed in v36. Android's
   home-screen `xOffset` is always normalized to `[0,1]` across the
   entire scrollable range (a documented `WallpaperService.Engine`
   guarantee) -- capping the effective parallax rate at `1.0`
   (`PaperRenderer.drawHillLayers`) guarantees `|shiftX|` never exceeds
   one screen width for any layer at any valid offset, so neither hills'
   wide-tile modulo nor objects' narrow-tile modulo ever actually
   triggers during a normal swipe, eliminating the drift. Only visible
   trade-off: the very top of the "Scroll speed" slider (2.0x) combined
   with the nearest hill layer is capped from an uncapped 1.2x to 1.0x, a
   minor reduction at one extreme setting.
6. ✅ **Scroll settings, global** — done in v36. "Scroll Speed" turned
   out to be the exact same mechanism as the already-existing "Parallax
   strength" slider (confirmed via a reference app's own decompiled
   source), so it was relabeled and reused rather than duplicated. "Scroll
   Background" (sun/moon/stars optionally scroll with the parallax
   hills, off by default) and "Swipe Scroll" (disable wallpaper scrolling
   entirely) are genuinely new, global settings.
7. ✅ **Fixed post-delivery, v37**: 3 real bugs found in the v36 delivery
   above, reported with a screenshot for the first:
   - **Flying mountains** — mountains' base Y was two fixed guessed
     constants (0.50/0.545), independent of where the hill silhouette
     they were supposed to sit on actually starts (which, per
     `buildBaseHillPath`'s own random range, can be anywhere from
     fraction 0.15 to 0.75 of the farthest layer's band). Since 0.50 sits
     *above* even the hill's best-case (highest) possible top edge, the
     mountains floated with a visible gap of empty sky beneath them in
     most cases. Fixed by deriving the base Y from the same proven-safe
     `HILL_SAFE_ROW_MIN` bound already used elsewhere, guaranteeing the
     mountain's base is always at or behind the hill's own worst-case
     top edge.
   - **Lake swallowing buildings** — the lake's bottom edge was fixed at
     a hardcoded 0.78 of screen height, which is *inside* the
     skyscraper/"buildings" category's own placement rows (0-2, reaching
     as near as ~0.765 at row 0) -- so turning Lake Height up enough put
     buildings' feet literally inside the lake's rectangle. Fixed by
     deriving the lake's bottom edge from that same row's real groundY,
     with a safety margin, so the two can never overlap regardless of
     the height slider.
   - **Unrecognizable dolphins** — redrawn from a plain teardrop-with-a-
     dot into an actual leaping-arc silhouette (curved back, dorsal fin,
     upturned tail flukes).

## Phase 1 — Sky, celestial bodies, and weather (built from scratch)

**Design constraint, checked against the current code before being added
here**: there is currently no mechanism anywhere in PaperScrape that
auto-switches to the theme literally named "Sunset" based on actual
time-of-day (the only "sunset" reference in the code is just the app's
default starting theme, unrelated to the clock) -- so this isn't a bug
to fix, but a requirement for how 1a's dawn/dusk color transitions must
be built: color blending across sunrise/sunset/night must always happen
*within* whichever theme is currently active (manually chosen, or
auto-selected by date for things like Christmas), via that theme's own
color set -- it must never cause an actual theme swap to a different
theme (named "Sunset" or otherwise) just because it's golden hour.

**1a — Base (stars, sky, sun, moon)** ✅ DONE (v39)
- Stars: show/hide, density -- `StarsConfig`, per-theme, count scales
  from the old fixed 70 by the density fraction
- Sky: Day/Night Color High+Low (2-stop gradient), dedicated Sunrise/
  Sunset colors, sun/cloud height -- `SkyConfig`, 6 user-editable colors
  replacing the old fixed `theme.skyDay`/`skyNight`/`skyDawn`/`skyDusk`
  (4 arrays of 2 colors, blended via a "twilight bump"). Only the bottom
  gets dedicated sunrise/sunset colors (the near-horizon glow); the top
  blends day↔night directly, same twilight-weighted math as before, just
  parameterized by these 6 colors instead of the old palette arrays.
- Sun: show/hide, color -- `SunConfig`, per-theme
- Moon: show/hide, color, "realistic moon phases" toggle -- `MoonConfig`.
  The real astronomical phase calculation already existed
  (`SunPositionCalculator.moonPhase()`); the toggle lets a user opt for
  a plain always-full decorative moon instead.
- `sunCloudHeight` (in `SkyConfig`) controls how high the sun/moon's arc
  rises, and (once clouds needed it) where clouds' vertical band sits.
- All 4 pieces' per-theme defaults are derived from that theme's own
  existing hardcoded colors via `defaultCustomizationFor()` (e.g. sky's
  day-high/day-low come from `theme.skyDay[0]`/`[1]`), so nothing looks
  different until a user actually customizes it.
- New Scene Objects menu rows: "Sun and Moon", "Sky", "Stars".

**1b — Clouds** ✅ DONE (v39)
- Show/hide, density ("# of Clouds"), day/night color -- `CloudsConfig`.
  Puffy clouds (overlapping circles + a rounded base, matching the app's
  soft paper-cutout style rather than sharp cartoon cloud outlines),
  drawn with the same independent-candidate-pool approach as mountains/
  birds (own gentle parallax + a slow independent horizontal drift on
  top of it, own density filter, zero interaction with the hill/object
  row-placement system). Positioned using `sky.sunCloudHeight` for their
  vertical band.
- Seasonal four-leaf-clover/heart-shaped cloud variants -- **not yet
  done**, deferred to Phase 2 alongside the rest of that phase's
  holiday-specific content (this base system is what those variants will
  build on).

**1c — Precipitation and rainbow** ✅ DONE (v41)
- Rain/snow: show/hide, type (Rain or Snow, mutually exclusive),
  intensity, independent color pairs for each type -- `PrecipitationConfig`.
  Rendered as the closest layer in the whole scene (drawn last, in front
  of houses/cars/fireworks/Santa), same stateless deterministic-candidate
  approach as Clouds/Birds (no per-particle list to manage between
  frames).
- Thunderstorm: occasional full-screen lightning flash, only meaningful
  while Rain is selected -- a small self-contained timer/fade on
  `PaperRenderer`, not a new `Effect` class (too simple to earn one).
- Rainbow: show/hide, opacity -- `RainbowConfig`. Deliberately
  independent of the rain toggle (manual per-theme decoration, not tied
  to actual weather conditions -- that connection is Phase 1d's job).
  Fades toward night like stars fade in. Anchored to the same base-Y
  mountains use, drawn before mountains/hills so their silhouettes
  occlude its base naturally.
- Per-theme defaults: both off by default (opt-in, like the lake), but
  Winter/Christmas/Tundra pre-set precipitation's type to Snow.
- New Scene Objects menu rows: "🌧️ Precipitation", "🌈 Rainbow".

**1d — Dynamic weather** ✅ DONE (v60)
- Live Weather (real forecasts, requires an external API key). Two key
  sources, user's choice: a hardcoded key aa provides (baked into the
  build, no setup needed) or the user's own key entered in Settings.
  Provider: Open-Meteo (free tier needs no key at all -- the hardcoded/
  user key is an optional upgrade to its higher-limit customer
  endpoint), fetched hourly, overrides each theme's manual Rain/Snow/
  Thunderstorm settings (fully) and Clouds density (only when the
  theme's own Clouds toggle is already on) with real current
  conditions -- theme colors are untouched, only visibility/type/
  intensity/density are weather-driven.
- Fall Colors (trees with autumn tones and periodic falling leaves) — a
  seasonal variant tied to this system
- Winter/Christmas colors (trees with snow and Christmas lights) — a
  second seasonal variant, same mechanism as Fall Colors
- Weather and sunrise/sunset driven by the phone's real GPS position —
  one toggle, mutually exclusive with the row below
- Weather and sunrise/sunset driven by a manually-entered custom
  location — one toggle, mutually exclusive with the row above

## Phase 2 — New festive object types

- Halloween bats (replace birds when active)
- Thanksgiving turkey
- Leprechaun (St. Patrick's Day)
- Cupid (Valentine's Day)
- Easter flowers and baskets
- Halloween trees (optional variant, like palm trees at the beach)
- Christmas trees (optional variant, same mechanism)

**Explicitly excluded**: "African American Santa" — will not be
implemented.

**Also in this phase (not festive -- general-purpose, single toggle)**:
- **Humans**: cartoon-style people inside houses/buildings (moving
  around, passing window to window) and driving cars. One on/off toggle,
  no per-item density/color like the seasonal categories -- this is a
  single "liveliness" feature for the existing houses/buildings/cars, not
  its own placeable object category.
- **Emergency vehicles on the road**: fire trucks and ambulances, as
  additional vehicle types alongside the existing plain cars -- note
  PaperScrape doesn't currently have police cars or taxis as distinct
  types either (only plain cars), unlike the reference app's screenshots,
  so a fuller "vehicle types" system (matching that reference's Types
  sub-section: Police Cars / Fire Trucks / Taxis, each independently
  toggleable) may be worth doing as one piece here rather than just
  fire trucks/ambulances alone.

## Phase 3 — Boolean sub-options

The current data model (`ObjectVariantConfig`) only has visibility/
density/color. Needs a mechanism for extra flags like "Random Gifts",
"Day Fireworks", "Constant Fireworks" — probably a `Map<String, Boolean>`
of extra options per category, or new dedicated fields.

## Phase 4 — Theme/season automatic consistency

Extend `SeasonalThemeRules`/`ThemeCatalog` to include Halloween,
Thanksgiving, St. Patrick's Day, and Valentine's Day as new automatic
by-date themes — currently missing from the catalog, they exist only as
"decorations" in the Seasonal Decorations menu.

## Phase 5 — Polish

- "Pumpkins only under trees if enabled" behavior (conditional dependency
  between decorations)
- Live preview in the Seasonal Decorations menu (like "Scene Objects"
  already has)
- **Review every built-in theme's defaults** now that there's far more
  to customize than when they were first authored (mountains, lake,
  birds, hills colors, etc. all added since) -- make sure each theme's
  starting point still makes sense as a cohesive whole, not just
  individually-reasonable defaults per category.
- **Shrink the 0-100% slider touch targets slightly.** Full-width
  sliders are hard to drag all the way to either edge on a real touch
  screen -- users get stuck around 1-2% or 98-99% instead of reaching a
  clean 0%/100%. Needs either a narrower track with padding, or
  snap-to-edge behavior near the ends.
- **Make automatic update checks opt-in**, off by default, with a
  checkbox in Settings -- currently `UpdateChecker` always makes a
  network call on launch regardless of preference; this should only
  touch the network at all if the user has explicitly turned it on.
- **Improve the theme previews in the theme picker** -- current ones are
  described as hard to understand at their preview size.
- **Fix too many cars on screen at once** -- at higher car density
  settings, 3 cars can appear close enough together to look like they're
  about to collide. Needs either better spacing/minimum-gap logic in car
  placement, or a lower practical density ceiling.
- **Manual location entry for "Use location for sunrise/sunset"** -- an
  alternative to GPS position for users who'd rather type a location than
  grant location permission.
- **Aesthetic passes**: Christmas gifts, houses/buildings, beach
  umbrellas, cars -- all called out as needing a visual quality pass,
  not just more settings.
- **Try tiny distant houses on the mountain slopes** -- an experimental
  detail to see if it reads well at wallpaper scale, not a guaranteed
  addition.
- **Widen the mountains a bit** -- current silhouettes read as too
  pointy/narrow.
- **Harmonize object sizes across categories** -- e.g. beach umbrellas
  currently render larger than houses, which reads as visually
  inconsistent/wrong scale. Needs a pass comparing every category's
  actual rendered size against each other, not just against itself.
- **Overall "paper cutout" aesthetic pass**, across every element, not
  just the specific items already called out above (gifts/houses/
  umbrellas/cars/dolphins/mountains) -- reference screenshots showed
  a visibly more convincing torn/layered-paper texture and shading
  throughout than PaperScrape currently has. A broader visual-quality
  pass, not a single fix.
- **Thunder needs an actual visual treatment, not just a flash.**
  Currently a thunderstorm only shows a plain full-screen white flash
  (`drawLightningFlash`) -- no jagged lightning bolt shape, no visible
  "thunder" element beyond the flash itself.
- ✅ **Mountains/lake/hills layering redone from scratch to match the
  reference app's actual structure, done (v46, then corrected further in
  v47 after the v46 result still didn't match)** -- v45's fix (mountains
  dynamically reaching down to the lake's real top) was a genuine
  improvement but built on a wrong mental model: it assumed hills would
  draw *after* the lake and let their own wavy silhouette show water
  through the "valleys" -- but hills are 3 *stacked*, largely opaque
  layers, not a single thin line, so verified with an actual rendered
  mock of the geometry (not just the math, after getting burned once by
  trusting math alone) that there was almost no room behind them for
  water to ever show through. v46 moved the lake to sit *above* where
  hills begin at all -- correct in principle, but the actual v46 result
  (checked against real screenshots, not just another mock) still showed
  a visible gap between mountains and the lake, and the mountains
  themselves still read as cluttered/noisy rather than the reference's
  clean 2-3 large mounds. v47 traced both down: (1) the gap came from a
  wavy top edge that had been given to the lake for a *different*,
  now-obsolete reason (blending against hills, back when the lake still
  overlapped them) -- its per-segment random jitter didn't match the
  mountains' fixed anchor point, opening a sliver of sky at some x
  positions; reverted the lake to a plain flat rectangle, which lines up
  with the mountains' fixed base exactly, everywhere; (2) the "noisy"
  mountains turned out to be a proportions problem, not a shape problem
  -- extracted the reference app's own actual game assets (from the
  provided full APK) and found its real "parabola" mountain sprite,
  measured its width-vs-height profile directly (10 sample points),
  and confirmed it follows a true parabola (`width ∝ √(height from
  peak)`, matching the sprite's own name) at close to 1:1 width:height
  proportions -- both very different from PaperScrape's previous shape
  *and* its 2:1 flat/wide proportions. Rebuilt the silhouette by
  sampling real points along that measured curve (not another guessed
  bezier), and reduced candidate count (7 → 4) while substantially
  widening each mountain (0.34–0.40 → 0.60–0.70 of screen width) to
  match the reference's "few large, clearly separated mounds" look
  instead of many small overlapping ones. Verified the *combined* scene
  (new mountains + flat lake + all 3 existing hill layers together) with
  a rendered mock across 4 different seeds/heights before touching the
  real Kotlin, specifically to catch composition-level issues no amount
  of per-element math would reveal on its own.
- ✅ **Follow-up correction, v48**: v47's layering still had the real bug
  (confirmed by a fresh report, not just re-reading the code): sky visible
  between hills and mountains (worse with the lake off), mountains reading
  as floating, and the back mountain layer visibly see-through with the sun
  showing behind it. Traced to the actual root cause this time by
  decompiling the reference app's own APK (`jadx`, both the standalone one
  and the one inside the provided device backup -- same build) rather than
  reasoning about PaperScrape's own geometry in isolation: `Mountain.java`
  positions each mountain at `Scene.mountainBottomY`, defined as
  `max(hillsVisibleBottomY, waterVisibleTopY if lakes are on)` --
  `hillsVisibleBottomY` there is deliberately the hill's own
  *worst-case-covered* line (the deepest point its randomized top edge can
  ever reach), not its peak. PaperScrape's `drawMountains()`/
  `lakeTopBottomY()` anchored to the opposite extreme -- the shallowest,
  best-case peak fraction (0.15) from `buildBaseHillPath`'s random range,
  a point the wavy edge only reaches at a couple of x positions per screen,
  leaving a real gap of bare sky beneath the fixed anchor at almost every
  other x. Fixed by reusing `HILL_SAFE_ROW_MIN` (0.78 at the time) --
  PaperScrape's own already-proven "always-solid regardless of the random
  roll" fraction, originally derived for object row placement -- for this
  anchor instead, via a new shared `hillGuaranteedTopFraction`. Also removed
  the back mountain layer's `alpha = 200` (a depth cue the reference app's
  own `Mountain` model doesn't use at all -- it's plain opaque -- and which
  let the sun/moon, drawn behind mountains in z-order, visibly bleed
  through). Verified with a rendered Python mock reproducing
  `buildBaseHillPath`'s exact algorithm (old anchor vs new anchor) before
  touching the real Kotlin, then with a real `assembleDebug` + `lintDebug`
  build.
- ✅ **Follow-up redesign, v49**: aa confirmed the v48 gap/transparency fix
  worked, then asked for a further pass using the reference's decompiled
  source directly: mountains/hills were still much taller and more jagged
  than the reference's low, gentle scenery, and hills still visually read
  as 3 stacked colors despite only one day/night color ever being
  user-editable (that second part was v48's own open question, now
  resolved as "yes, needed its own pass" rather than a symptom of the gap).
  Ported the reference's real numbers instead of eyeballing new ones:
  **hills 3 layers -> 1** (matching the reference's own single-silhouette
  `Hills` class -- `hillLayerColor()`'s per-layer darkening is now a no-op
  with only one layer, so the hill is genuinely the one picked color, not
  three), **~40% shorter** (reference's `hillsTopHeight` defaults to ~42.5%
  of screen height; PaperScrape's 3 stacked layers totaled far more), and
  **far gentler** (reference's wavy top edge, a sine wave, only swings
  within about the top 12% of its own height by default; PaperScrape's old
  per-segment random range swung across a full 60%, `[0.15,0.75]`, narrowed
  to `[0.04,0.22]`). The 9-row object placement system (houses/trees/road)
  was preserved by spreading all 9 rows across the one remaining layer
  (`ROWS_PER_LAYER` 3 -> 9, `layerCount` 3 -> 1, `TOTAL_ROWS` unchanged at
  9) rather than removed, with `HILL_SAFE_ROW_MIN`/`MAX`/
  `ROAD_SAFE_ROW_LIMIT` all re-derived from the new range/geometry, not
  just left alone. **Mountains** resized from the reference's own
  `Mountain`-object-creation code (not just its sprite shape, which v47
  already matched): back mountains average ~15% of screen height, front
  ones ~10.5% -- both far smaller than PaperScrape's old 29%/34%; widths
  scaled down by the same ratio to keep the already-correct sqrt-curve
  shape intact. Verified with a rendered mock of the new geometry (hill
  band, mountain sizes, and where the 9 object rows land relative to the
  road) before touching the real Kotlin, then with a real
  `compileDebugKotlin` + `lintDebug` build -- not `assembleDebug`, per aa's
  instruction that CI now owns producing the actual installable APK (see
  Process notes).
- ✅ **Follow-up fixes + audit, v50**: aa reported mountains still too
  narrow/not round, hills not "harmonious", the lake needing another
  look, elements static during infinite scroll, and sun/moon vanishing
  during infinite scroll -- then asked for a systematic audit of
  everything else already built against the reference's real source.
  Found and fixed 6 concrete bugs (full technical detail in
  CHANGELOG.md's v50 entry): sun/moon/stars drifting permanently
  off-screen during long uptime with "Scroll background" on (missing a
  `% tileWidth` wrap every other layer already had); the road's dashed
  line never animating (reference's `Road.mTextureScroll` keeps its
  surface flowing on an otherwise-fixed quad); mountains too narrow (a
  real bug introduced in v49 itself -- the width:height ratio was
  carried over from the old too-tall mountains instead of re-derived,
  when the reference's own sx/sy need no aspect-ratio conversion at
  all); mountains not rounded at the peak (curve formula was already
  correct, sampling density wasn't -- fixed by sampling evenly in width
  instead of height, since √t bends fastest exactly where the old
  sampling was sparsest); hills rebuilt as a true sine wave matching the
  reference's real formula, instead of independent random segments, for
  genuine smoothness rather than tuned-to-look-smooth randomness; the
  lake's max height could swallow v49's now-smaller mountains, so its
  band-height cap was tightened with a verified margin. Also audited
  clouds/cars/lake-motion against their own reference classes and found
  them already consistent (left alone). Found two real *differences*
  from the reference and deliberately did **not** port either, since aa
  asked to keep the aesthetic already reached rather than match the
  reference pixel-for-pixel: the reference's sun/moon move only
  vertically at fixed left/right screen positions (PaperScrape's
  full-sky horizontal sweep is the existing, deliberate look); the
  reference sky has a 4th color for a sunrise/sunset horizon glow that
  PaperScrape doesn't have (a feature gap, not a bug). Both flagged for
  aa to decide on. Verified with rendered mocks (mountain peak rounding
  side-by-side, full combined scene) plus a real `compileDebugKotlin` +
  `lintDebug` build.
- ✅ **Follow-up fix, v54**: aa reported (with a screenshot) that hills
  were still visibly cutting off with sky showing through on the right
  side during scrolling, after v51's fix -- v51 was a real fix, but for
  a different, longer-uptime cause (float precision); it was never the
  only one. Re-checking `drawHillLayers`'s own tiling math found the
  second cause: hills draw exactly one copy of their own path per
  frame, unlike every other scrolling layer here (mountains, clouds,
  objects), which all draw 3 tile copies (-1, 0, +1) specifically to
  avoid gaps as the wrap position moves. Since the hill path is built to
  exactly match its own wrap period (`tileWidth`), a single copy only
  covers the full screen for the first quarter of each wrap cycle --
  reachable within ~10 minutes at default scroll speed, not an extreme
  edge case. Fixed by giving hills the same 3-copy pattern, verified
  numerically (a script sweeping the shift across a full cycle,
  confirming zero gaps) before touching the real Kotlin.
- **"Sea" vs "lake" still an open item** -- the layering fix above
  makes mountains/lake/hills correctly ordered and gap-free, but a full
  "use this water as an ocean, not a middle-distance lake" mode (e.g.
  for Beach) is still not done. Needs either a way to disable hills
  entirely for a theme, or a second water-placement mode/position, so
  the same water feature can convincingly be either.
- **Two reference-app differences found during the v50 audit, pending
  aa's decision (not done, deliberately not guessed at)**: (1) the
  reference's sun/moon move only vertically, at fixed left/right
  horizontal screen positions -- PaperScrape sweeps them across the
  whole sky instead, which is the current, deliberate look; (2) the
  reference sky blends in a 4th color for a sunrise/sunset horizon glow
  near the horizon, which PaperScrape's day/night-only blend doesn't
  have. Neither was ported in v50 since both are style choices, not
  bugs -- only worth doing on explicit request.
- **In-app update flow doesn't show the version transition** (e.g.
  "updating from v6 to v7") when installing via Android's own APK
  installer -- separately from the in-app "Update available" dialog
  (which already shows this, added in v31). This may be a limitation of
  Android's sideload installer UI rather than something PaperScrape's
  code controls directly -- needs investigation into whether a custom
  install flow can surface it, likely alongside the next item.
- **Auto-download updates instead of making the user manually download
  the APK.** Currently "Update now" just opens the GitHub release page in
  a browser. A real in-app updater (download via `DownloadManager`,
  install via `PackageInstaller`/`ACTION_INSTALL_PACKAGE`) is a
  substantial feature on its own -- needs `REQUEST_INSTALL_PACKAGES`
  permission, download progress UI, and careful error handling, not a
  quick add-on.
- **Lake decorations (dolphins/sailboats) only ever move left-to-right**
  -- should be mixed direction (some going each way).
- **Dolphins face backwards** -- the tail-fin end currently leads the
  direction of travel instead of the snout. A real bug in the v37
  redesign, not just a style preference.
- **Waves in the lake**, with adjustable size and frequency -- the lake
  currently only has static ripple lines, no actual wave motion/shape
  control.
- **Redo the security assessment**, starting from the vulnerabilities
  Dependabot has flagged since the last audit (see the chat history
  around the `paperscrape-claude-fix-plan-v2.docx` review and the 53
  Netty/BouncyCastle/etc. alerts traced to Android Gradle Plugin's own
  bundled tooling, not the shipped app) -- worth a fresh pass now that
  the dependency graph has had time to accumulate new findings.

## Process notes

- **Before packaging any delivery zip, verify hidden files survived** --
  `find PaperScrape -maxdepth 2 -name ".*"` should list `.github/`
  (both workflow files) and `.gitignore` at minimum. This bit v54/v55:
  an intermediate `cp -r source/* dest/` (unquoted glob) silently
  dropped every dotfile, and the resulting zip's `.github/workflows/`
  being absent is exactly what stops GitHub Actions from auto-
  triggering once the user replaces their repo's files with it (git
  records the workflow file as deleted on commit). Never copy a
  project tree via a shell glob (`*`) -- copy the directory itself
  (`cp -r source dest` or `cp -r source/. dest/`, both of which include
  dotfiles) or use `rsync -a`.
- **Paper grain was removed entirely in v58 -- do not re-add
  `paperGrain`/`PaperGrainTexture` calls anywhere without an explicit,
  fresh decision from aa first.** It went through 3 real attempts
  (v53 single global overlay, v55 per-element matching the reference's
  UV mapping, v56 scoped to sky/hills/lake) before aa asked for it to
  be removed outright: even the scaled-down v56 version still measurably
  cost CPU, and more fundamentally, *any* multiply blend against a
  grain texture inherently darkens colors away from exactly what the
  user configured -- fundamentally in tension with color-fidelity
  expectations, not just a tuning problem. If this is revisited, start
  from the real findings already on record rather than from scratch:
  v57's CHANGELOG entry has the reference's actual GLSL shader source
  (only `sky_shader_frag.glsl`, used by Sky/Hills/Water/Road, ever
  samples a crinkle texture at all -- everything else uses only its own
  sprite art, which this app doesn't have an equivalent for without
  switching to sprite-based rendering).
- **Every bug fix or new feature starts from the reference app's decompiled
  source, used as the actual basis for the fix/implementation** -- not
  just consulted after the fact. `jadx`-decompiled sources live under
  `/home/claude/work/jadx_out` and `/home/claude/work/jadx_deobf` in the
  environment this was done in (re-decompile `the reference app's APK`/the
  provided device backup's `base.apk` if starting fresh). Some methods
  are not decompilable by `jadx` ("Method not decompiled", seen for
  `Scene.addCloudsAndBalloons`, `createTreesRanged`,
  `createBuildingsRanged`) even after a deobfuscation pass -- **before
  concluding a method truly can't be recovered, try CFR as a second
  decompiler**: `dex2jar` (`d2j-dex2jar.sh the reference app's APK -o out.jar`)
  converts the APK's dex to a jar, then `java -jar cfr.jar Some.class
  --outputdir out_dir` decompiles it -- CFR successfully decompiled all
  3 of the methods above where jadx failed (v57), including in Scene.java
  which the file's own doc comments no longer need to caveat as
  partially-recovered. Only fall back to "say so explicitly, build from
  adjacent evidence" (constructors, field defaults, other classes'
  formulas, real reference screenshots/behavior aa provides -- v51's
  cloud-coverage fix and v52's cloud outline/row-consolidation fix both
  used this) if CFR *also* fails on a given method. Also worth checking
  for a method's actual GLSL shader source directly: the reference ships
  its real shaders as raw asset files (`res/raw/*.glsl` inside the
  APK) -- no decompilation needed at all, just unzip -- which is how
  v57 settled the paper-grain scope question definitively rather than
  by inference.
- Every phase must be verified with real builds (`compileDebugKotlin` +
  `lint` at minimum) before delivery, never just a code read-through --
  see `.github/workflows/android-build.yml`'s own `build` job for the
  full picture. Producing the actual installable/signed APK is CI/CD's
  job (that workflow's `assembleDebug`/`assembleRelease` steps), not
  something to redo locally in chat for every delivery.
- Every delivery includes a `versionCode` bump, a `CHANGELOG.md` entry
  (technical) and a `release-notes/vN.md` entry (plain language for
  users) -- except a delivery whose only change is non-functional (e.g.
  a stray comment fix with no code behavior change): hold that and fold
  it into the next delivery that has real substance, rather than bumping
  a version for it alone.
- **This file (`ROADMAP.md`) must be updated at every delivery too** --
  both the "Last updated" summary at the top and, if the delivery
  touches an item tracked below, that item's own bullet (a ✅ follow-up
  note, same as the mountains/lake/hills entry's v46-v54 history) -- so
  it stays the accurate restart point described below, not just
  `CHANGELOG.md`.
- Phases are large: expect each one to need multiple "Continue" turns
  and, potentially, multiple separate conversations — this file is the
  exact restart point to resume without losing context.
