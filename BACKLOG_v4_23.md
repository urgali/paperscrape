# BACKLOG_v4_23.md — what v4.23 decided, and what it left open

**Replaces `BACKLOG_v4_22.md`.** That file's resolved and documented items are settled and are not
restated; what it left open is carried forward below (items 18, 25, 30). Numbering continues from
it: items 37 onward are new to this release.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided
against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered), or
**OPEN** (left undone on purpose, with what closing it would take).

The measuring device is unchanged from v4.22: a **Blackview BV6600** (MediaTek Helio A25, PowerVR
GE8320, Android 10, 720×1440). Every number below was measured on it in this pass unless it says
otherwise.

---

## Summary

| item | what | outcome |
|---|---|---|
| 18 | An unreadable custom theme loses the whole store | **OPEN**, carried forward from v4.20 unchanged |
| 25 | The palm is the odd tree out | **OPEN**, carried forward — and now the most visible piece of the old language left. Which family is next is the maintainer's call |
| 30 | Hand-maintained counts still in the current-state documents | **OPEN**, carried forward — one closed here (item 43), the rule unchanged |
| 37 | The celestial family was the last drawn with a compass | **RESOLVED** — eight sprites promoted from concept B through the asset pipeline; canvases, conventions and anchors unchanged |
| 38 | The star sparkle's tile extents were the star's radius, not the sprite's reach | **RESOLVED** — derived from the divisor; the obvious repair (doubling `MAX_STAR_RADIUS_PX`) would have hidden it by changing the star field |
| 39 | `STAR_POINT_COLOR` was not the colour its own comment named | **RESOLVED** — corrected to the artwork's cream and pinned by a test that reads the PNG |
| 40 | The Canvas goldens barely exercise the sky | **RESOLVED in part** — the cloud band hid **every** celestial body, the moon in the four night frames as well; item 41's new golden is the first committed frame with one clear of it. The sun's own half stays **OPEN** |
| 41 | No committed frame draws `moon_jack_o_lantern` | **RESOLVED** — `halloween-moon`, Halloween **and** realistic phases both on, so the frame can fail if the renderer's override ever leaks |
| 42 | Paper grain as a project-wide device | **OPEN** — a decision for the maintainer that touches every sprite, and must be measured before it is taken |
| 43 | `reports/runtime-inventory.{json,md}` described a 260-sprite set that ships 266 | **RESOLVED** — regenerated; the budget margin is unchanged and was verified, not assumed |
| 44 | The preview's fir baubles are `star_sparkle` blits, so they changed with the artwork | **DOCUMENTED** — the follow-up `BACKLOG_v4_21.md` item 26 anticipated |
| 45 | `sun_body`'s drawing is not centred in its own canvas | **DOCUMENTED** — inherent to the approved artwork, ~2.5 units, not corrected here |
| 46 | `getExternalFilesDir` works on this device after all | **DOCUMENTED** — v4.22's note that it does not did not reproduce |
| 47 | The realistic-phases switch was live and did nothing under Halloween | **RESOLVED** — shown off and locked with the reason; the stored preference is overridden, never written |
| 48 | The moon's phase is chosen from the wall clock, so no golden can pin it | **DOCUMENTED** — measured: no committed frame draws the moon at all, so nothing is flaky today, and nothing covers the phase path either |

---

## 18, 25, 30 — carried forward

Unchanged from `BACKLOG_v4_22.md`; see that file, and `BACKLOG_v4_21.md`, for the full accounts.
Nothing in v4.23 touched the custom-theme store's error path (18) or swept the undated counts (30).

> **Item 25 gets a note, not a change.** The item says the palm is the odd tree out: a thin straight
> rod of a trunk under a fan, against a tree that has been redrawn into a stocky flared language.
> After this release it is more than that — **with the sky cut, the palm is the most conspicuous
> piece of the old drawing language still shipping**, because the two families a viewer looks at
> longest are now drawn to different rules. That is an observation, not an authorisation: no palm
> artwork was touched here, no concept was drawn for one, and **which family is revisited next is
> the maintainer's decision**, not this pass's.

---

## 37 — The celestial family was the last family still drawn with a compass

**RESOLVED.** The V2 library cut every sprite in the scene with scissors — faceted rims, hand-cut
wobble, flat paper colour — except the sky, which kept smooth circles, swept terminator arcs and a
gradient sunburst. v4.23 promotes concept B, "Forbici", chosen by the maintainer over concepts A and
C in phase 1 and refined across rounds 1b, 1c, 1d and phase 2 part A:

| sprite | what it is now | source |
|---|---|---|
| `sun_body` | a faceted disc with a hand-cut wobble | `concepts/b/svg/sun_body.svg` |
| `sun_glow` | a compass-struck ring at full opacity over a warmed halo (0.55 `#FBE289` / 0.28 `#F0A03C`) | `concepts/b/alone/v2_rifinita/c/` |
| `moon_full`, `moon_gibbous`, `moon_half`, `moon_crescent` | 40-vertex rims, 9° per facet; terminators cut, not arced | `concepts/b/svg/` |
| `moon_jack_o_lantern` | the face cut through the paper — the sky shows through the eyes, nose and grin | `concepts/b/zucca/minima/` |
| `star_sparkle` | four unequal points on a full waist, concave two-segment sides | `concepts/b/svg/` |

**Promoted through the pipeline, not copied.** The eight SVGs were installed under
`tools/assets/sources/svg/` and the shipped PNGs are `render`'s own output, byte-identical to the
files the maintainer judged from. `PIXEL_IDENTICAL: 134` of 134 after the promotion, so every
shipped sprite still regenerates exactly from its committed source.

**Nothing about the placement moved.** Every canvas is byte-for-byte the size it was — 240×240 for
the sun and the five moons, 396×396 for the glow, 180×180 for the sparkle — so every scale
convention, every anchor and every blit origin in `PaperRenderer` is the number it was, and the
decoded sprite set is unchanged at 30 254 580 B (154 124 B under the 29 MiB ceiling). Verified,
not assumed.

## 38 — The star sparkle's tile extents were the star's radius, not the sprite's reach

**RESOLVED, and the interesting part is the repair that was refused.**

v4.23 halves `PaperRenderer.STAR_SPRITE_RADIUS_DIVISOR` (32 → 16), which doubles how far the
sparkle *drawing* reaches from the star it marks. The star field is a tiled pattern one screen wide,
and `firstStarTileOffset` / `starTileOffsetLimit` derive the tile range from
`STAR_SPRITE_{LEFT,RIGHT}_EXTENT_PX`. Those read `MAX_STAR_RADIUS_PX` — 5.6 px — which was a
deliberate over-reservation while the sprite reached `0.9375 × radius`, and became a **two-fold
under-reservation** the moment it reached `1.875 × radius`. An under-reservation drops a tile copy
at a seam: a sparkle clipped where the field wraps.

**The repair that makes the symptom go away is the wrong one.** Doubling `MAX_STAR_RADIUS_PX` also
makes the tiles wide enough — it is what the throwaway measurement build of phase 2 part A did — and
it is wrong, because that constant is read by `regenerateStars`: the largest star is exactly the
size it always was, and doubling it would draw a different star field to fix a tiling bug. The
derivation is:

```
reach = STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR × MAX_STAR_RADIUS_PX
      = 30 / 16 × 5.6 = 10.5 canvas px
```

with `STAR_SPRITE_HALF_UNITS = 30` being the bitmap's own half-span (180 px ÷ 3 px per unit ÷ 2),
which `SkySpriteAnchoringTest` already pins against the PNG's header, and
`STAR_SPRITE_ORIGIN_UNITS` now derived as its negation so the origin and the extents cannot
disagree about how big the bitmap is.

**Reserved on the bitmap, not on the artwork inside it.** The redrawn sparkle leaves a transparent
margin again (content box `14,8..168,164`, so the drawing reaches 9.57 px), and the two readings
that had coincided while the sprite filled its canvas differ once more. Over-reserving those 0.93 px
costs one comparison; under-reserving costs a visible clip.

Three tests were re-derived with it, and each was seen failing under the mutation it exists to
catch: `SkySpriteAnchoringTest` twice (a literal `32` in the fixture and in the reach), and
`BackgroundScrollGeometryTest`'s deliberately duplicated extents. **Measured on the device with the
new artwork installed: no sparkle is clipped at a tile seam** — condition T did not fire.

## 39 — `STAR_POINT_COLOR` was not the colour its own comment named

**RESOLVED.** Four in five stars are drawn as filled circles rather than blitted sparkles, and
`STAR_POINT_COLOR`'s comment says it is "the cream the sparkle art is drawn in, so a point and a
sparkle are the same star". It was `#FFF6DC`; `star_sparkle.png` is, and always was, `#FBF4E6`. The
gap is 4/2/10 levels — invisible on a two-pixel dot, which is exactly why it survived four releases:
a false claim that cannot be seen to be false cannot be caught by looking at the device.

Two repairs were available — correct the sentence, or correct the colour. **The colour is
corrected**, because the sentence states the property that is actually wanted, and
`StarFieldColourTest` now reads the one fully-opaque colour out of `star_sparkle.png` and asserts
the constant equals it. Seen failing with the old value before being trusted.

## 40 — The Canvas goldens barely exercise the sky

**DOCUMENTED, and it is a measurement rather than an impression.** A sky redraw ought to move most
of a 24-frame golden suite. It moved 16, and **8 frames — `day`, `lake-busy`, `lake-empty`,
`overcast`, `people-single`, `rain`, `snow`, `thunderstorm` — are byte-identical**, because in those
scenes the sun sits behind the cloud band and is not drawn to a single visible pixel.

Where the sky *is* visible the coverage is thin in a second way: of the 16 that moved, only two
exceeded the whole-frame limit and failed — `dusk` (0.6809%) and `people-skin` (0.4292%), both of
them the low sun's ring — while fourteen changed under it, between 0.0014% and 0.1167%. The largest
connected component on any night scene is 40 px.

Nothing here is a defect: the golden suite was built to pin traffic, people, lakes and weather, and
it does. It is recorded so that "the goldens are green" is not read as "the sky is covered". Closing
it would mean committing a frame with the sun clear of the clouds — a scene that does not exist
today, and a decision about what the suite is for, so it is not taken here. See also item 41.

> **Round 2C measured the other half of this, and it is worse than the entry says.** The sun is not
> the only body behind the band. The cloud band's own centre is
> `800 × (0.06 + (SUN_CLOUD_HEIGHT_MAX − 0.42) × 0.5) = 120` at the default `sunCloudHeight`, and
> the celestial body sits at `cy = 0.62 × 800 − 0.42 × 800 = 160` with a radius of 39.6 — so the
> disc's own square, `(140,120)–(220,200)`, is *inside* the band. Read off the committed PNGs
> rather than reasoned about: in **`night`, `theme-city`, `traffic-night`, `traffic-night-quiet`
> and `shops-closed-night` the pixel at (180, 160) is the identical flat cloud grey `(74, 85, 104)`
> and not one pixel of that 6 400-pixel square is 40 levels brighter than its own median.** **No
> committed frame drew a sun or a moon at all.**
>
> **`halloween-moon` (item 41) is the first one that does**, and it took turning the clouds off to
> get there — the scene's first capture had the pumpkin behind the band and was thrown away, which
> is this item happening live. So the **moon half of item 40 is closed**: there is now one frame in
> which a celestial body is drawn clear of the clouds and asserted, whole-frame and on its own
> rectangle. **The sun's half is not**, and closing it is still the decision this item describes —
> one more scene, deliberately cloudless, about the sunburst rather than the lantern.

## 41 — No committed frame draws `moon_jack_o_lantern`

**RESOLVED.** The pumpkin moon was the one celestial sprite no golden rendered: it appears only when
Halloween is on, and no `GoldenScene` turned it on. What covered it was `ThemePreviewSceneTest`,
which asserts at the resource-id and tint level that the halloween preview carries it, and a look at
the device — the right evidence for an artistic decision and the wrong evidence for a regression
gate, especially the release that redrew the face.

`SceneGoldenTest.halloweenMoon` closes it: the `halloween` theme at deep night, one more committed
PNG (`halloween-moon.png`, 28 in the directory, 26 Canvas assertions).

**The part that carries the argument is that both flags are on.** `halloweenEnabled = true` **and**
`moon.realisticPhases = true`, written down in the scene rather than inherited from the two
defaults that happen to supply them. `PaperRenderer.drawMoonWithPhase` blits the lantern and returns
*before* it reads `realisticPhases`, and that early return is the invariant; a scene with Halloween
on and phases off would come out identical whether the guard stood or fell, because with nothing to
ignore there is nothing to prove. With both on, a leak of the phase path replaces the lantern with
one of the four silhouettes in the moon's own colour. Measured at this frame's own scale — the
sprites resampled to the 79.2 px the renderer draws them at — that is **809 px (crescent), 1 750
(half), 2 660 (gibbous) or 3 449 (full)** against a lantern of **2 609**, where the whole-frame
budget is `0.002 × 360 × 800 = 576`. Even the smallest of them clears the budget on its own. **The
frame has a way to fail**, which is the same requirement `SettingsGateScenesTest` derives its gates
from.

**What is in the frame**, looked at before it was committed and not only asserted: a deep-night
halloween sky — near-black overhead going to a hard orange horizon — with the carved disc at the
apex of its arc, the sky showing *through* the eyes, the nose and the grin (a star is visible
through one of the teeth), the star field, bare trees, lit house and shop windows, pumpkins on the
ground, and the empty night road. Measured on the committed PNG, the lit orange spans
`147..211 × 128..191` with its centre at **(179.0, 159.5)** against the derived (180, 160) — the
sub-pixel offset being item 45's, the drawing not centred in its own canvas.

**The clouds are off in this scene**, and that is item 40 rather than a convenience: the first
capture put the lantern behind the cloud band and contained no pumpkin at all. It was thrown away.

**No tolerance was touched and no gate moved.** The focus rectangle carries the shared
`SceneGolden.MAX_FOCUS_DIFFERING_FRACTION`, which every focus that is not a v4.22 settings gate has
always carried. `GoldenUniquenessTest` is green with 28 PNGs: no two are byte-identical.

## 42 — Paper grain as a device of the project

**OPEN, and registered rather than resolved on purpose.** Concept B reads as cut paper by its edges
alone: facets, wobble, and flat colour. Real cut paper also has *grain* — a faint fibrous texture
across the sheet — and every sprite in this project is flat fill. Adopting grain would be the single
largest change to the look since the V2 library.

It is not a small decision and it is not this pass's:

- **It touches every sprite, not the sky.** Grain on eight celestial sprites and nowhere else would
  read as a defect, not as a style. The unit of the decision is the whole 266-sprite set.
- **It has to be measured before it is taken.** Grain is either baked into each PNG — which
  changes nothing at runtime but multiplies every sprite's entropy, and the set is already 154 124 B
  under a ceiling that has been raised four times — or applied as an overlay at draw time, which is
  a new per-frame cost on a device whose whole frame budget is 14.72 ms of 33.3.
- **It interacts with the tint classes.** A grain baked under `MULTIPLY` darkens with the tint;
  `SpriteTintClassTest` requires a tintable sprite to average ≥ 220, and the current celestial set
  sits at 244.1.

What closing it would take: a mockup pass on one family in both directions (baked and overlaid), the
decoded-byte and per-frame numbers for each, and the maintainer's judgement on the look. **No work
towards it was done here.**

## 43 — The committed sprite inventory described a set that has not shipped for two releases

**RESOLVED, and it is one of the counts item 30 is about.** `reports/runtime-inventory.{json,md}`
described **260** sprites; `res/drawable-nodpi/` holds **266**, and the registry has 266 entries
that `validate` checks. The inventory is regenerated by `paperscrape-assets inventory` and was
simply not re-run after the set last grew, so it was evidence describing a set that no longer
existed.

Regenerated in this pass. The number that matters is unchanged and was **verified rather than
assumed**: the decoded set is **30 254 580 B** against `SpriteGeometryTest`'s 29 MiB ceiling
(30 408 704 B), leaving **154 124 B**. v4.23's own sprites contribute exactly zero to that, because
all eight canvases are the size they were.

## 44 — The preview's fir baubles are `star_sparkle` blits, so they changed with the artwork

**DOCUMENTED.** `ThemePreviewScene.fir()` decorates the gallery card's Christmas fir with three
`star_sparkle` blits at hand-picked offsets, tinted gold, red and blue. `BACKLOG_v4_21.md` item 26
recorded that they are placed by eye and never agreed with the renderer's own
`drawChristmasLights` — deliberately, and that has not changed.

What has changed is what they are made of. The sparkle is new artwork with a different silhouette
and a content box that no longer fills its canvas, so **the three baubles are a different shape than
they were**, at the same three positions. No test moved (`ThemePreviewSceneTest` asserts which
resources and tints the card carries, not their pixels) and nothing needs to: the card reads
correctly. Recorded so the next person to look does not read it as drift.

## 45 — `sun_body`'s drawing is not centred in its own canvas

**DOCUMENTED, not corrected.** The promoted `sun_body` has content box `21,21..224,227` in a 240×240
canvas, so the drawing's own centre sits at `(122.5, 124)` rather than `(120, 120)` — about 2.5
units right and 4 units down. The anchor rule is `SPRITE_CENTRE`, which is the *canvas* centre, so
the sun is blitted with its canvas centred on the celestial point and its drawing therefore sits a
little off it: at the reference frame size that is under 2 real pixels, and the disc's own reach
stays inside the nominal 120 units either way.

This is a property of the approved artwork, not of the code, and correcting it means moving the
drawing inside its canvas — which is redrawing. **Phase 2 part B does not draw**, so it is recorded
here. Whether it is worth a nudge is an artistic call.

## 46 — `getExternalFilesDir` works on this device after all

**DOCUMENTED.** The v4.22 migration report notes that `SceneGolden.outputDir()` had to be pointed
locally at `filesDir` because `getExternalFilesDir` "non è disponibile all'app" on this device. That
did not reproduce in this pass: the GL harness, whose own `outputDir()` was left untouched, wrote
all three `gl-*.png` frames to
`/sdcard/Android/data/com.paperscrape.livewallpaper.debug/files/golden-output/` and they were
readable from the shell.

The local `filesDir` patch was applied here anyway, out of caution, and reverted — `SceneGolden.kt`
is byte-identical to the file the baseline archive shipped. Recorded because the note as written
would send the next session round a detour it does not need, and because **the difference is not
attributable**: nothing was measured about why the earlier pass saw it unavailable, and external
storage state is not something this pass observed then.

## 47 — The realistic-phases switch was live and did nothing under Halloween

**RESOLVED.** `WorldSceneScreen`'s "Realistic Moon Phases" row accepted a tap while
`halloweenEnabled` was on, and the renderer had already decided: `drawMoonWithPhase` blits
`moon_jack_o_lantern`, always full, and returns before it ever reads the flag. **The renderer is
right and was not touched** — its own comment carries the derivation, that a carved face waxing and
waning would be a lit fraction of a grin, which reads as a rendering fault rather than as a
decoration. What was wrong was the control: a switch that moves without effect is worse than an
absent one, because it teaches the user that the setting does not work.

The row is now shown **off and locked**, with a line saying why and where to undo it, which is the
`liveWeatherDriving` pattern the clouds and precipitation screens have used since v3.1.

**The stored preference is not touched, and that is the decision rather than a detail.** Writing
`false` into it would make the switch look right for free, and it would destroy a setting the user
chose; turning Halloween off would restore a value they never set. The rule is already written down
in `PeopleDensity.resolveNightDensity`, about a different setting: any default that "would silently
change what an existing user had set up" is "not something a settings refactor is entitled to do".
So the value is **overridden for display and left alone in the DataStore**, and it comes back
exactly as it was — per theme, since both flags are per-theme settings.

**Where the rule lives.** `SettingsUiModel.moonPhases(storedRealisticPhases, halloweenEnabled)`, a
pure function of two booleans next to `liveWeather` and `seasonalPalette`, which are there for the
same reason: the mapping between stored flags and what the UI shows is the part worth testing, and
it is testable only if it is free of Compose.

**How it is proved**, in two halves, because they fail in different places and neither is enough
alone (`MoonPhaseControlTest`):

- **The rule** is asserted directly — all four flag combinations, plus the property that only
  Halloween decides whether the row is locked. Making interactivity depend on the *stored* value as
  well would produce a switch locked *on* for a user who had phases enabled, which looks reasonable
  and is the dead end v3.0's Live Weather switch was in.
- **The wiring** cannot be reached that way. There is not one `createComposeRule` in this tree, so
  there is no tapping a switch and reading a DataStore back on the JVM; and an instrumented test
  that did would show the row locked without showing that **nothing writes**, which is the half
  that destroys data when it is wrong. So it reads the source, the way `BusinessHoursWiringTest`
  pins the two business-hours call sites and `SkyscraperWindowTest` the window-colour coupling —
  for the same reason in all three cases: the property is about *which call sites exist*, and no
  rendered frame and no round-tripped preference can say that a second one does not. It asserts
  that the row takes `checked` and `enabled` from the derivation and not from the raw flag; that
  both inputs come from the same resolved `customization`, which is what keeps the override
  per-theme; that **`setMoonRealisticPhases` has exactly one caller in the whole of `src/main`**,
  the switch's own `onCheckedChange`; and that `setHalloweenEnabled` writes its own key and the
  pending-theme marker and nothing else.

**Every one of the eight was seen failing before it was trusted**, under seven mutations, each
caught by the test that exists for it and by no other: the raw flag back on the switch; a
`shownOn` that ignores Halloween; a `shownOn` fixed at false; an `interactive` that the stored value
can unlock; the override read from something other than the theme's customization;
`setHalloweenEnabled` clearing the moon key; and — the one that matters — **a `scope.launch {
prefs.setMoonRealisticPhases(false, forThemeId) }` added to the screen to "tidy" the preference when
Halloween comes on**, which is the obvious repair, the forbidden one, and is caught by the
single-writer assertion.

**No behaviour of the scene changed.** The renderer draws exactly what it drew.

## 48 — The moon's phase is chosen from the wall clock, so no golden can pin it

**DOCUMENTED, and the measurement is the reassuring half.** `drawMoonWithPhase` picks its
silhouette from `SunPositionCalculator.moonPhase()`, whose only argument defaults to
`System.currentTimeMillis()`. A golden that drew a phase moon would therefore be pinned to the real
sky on the day it was captured, and would fail on its own, without a code change, when the moon
crossed one of the four `illuminated` thresholds — 0.02, 0.35, 0.65, 0.98. The moon read
**0.2616 illuminated (waning crescent)** while this pass ran, about **3.7 days** from the new-moon
edge, and the smallest silhouette is **809 px** against a whole-frame budget of 576, so a crossing
would not have been forgiven.

**Nothing is flaky, and the reason is item 40.** Measured on the committed PNGs rather than argued:
**no committed frame draws the moon** — in all five night goldens the moon's own square is the flat
cloud grey. The clock reaches nothing because the cloud band gets there first.

`halloween-moon` is deliberately not an exception. It has `realisticPhases` on, but the whole point
of the scene is that the phase path is *not taken*: it pins the override, and the frame it commits
is the lantern, which has no phase and no dependence on the date. **Adding a golden that pins a real
phase is not possible as the harness stands** — `drawMoonWithPhase` calls `moonPhase()` with no
argument, so there is no seam to inject a fixed instant through, and creating one is a renderer
change. Closing this would mean threading the scene's own clock into the phase the way
`GoldenScene` already threads `dayPhase`; that is a design decision about the renderer's inputs and
was not taken here.
