# PaperScrape Roadmap

Operational plan only. The release-by-release account and the completed list moved to
[`docs/archive/ROADMAP_HISTORY.md`](docs/archive/ROADMAP_HISTORY.md) in the documentation
pass of 2026-09-07; what shipped and why lives in `RELEASE_HISTORY.md`; how the code works
lives in `ARCHITECTURE.md`; the visual rules live in `DESIGN_NOTES.md`; the rules that
always apply live in `AI_PROJECT_RULES.md`.

**Nothing below is approved. Ask before starting any of it.**

---

## Current status

**v5.0 prepared — not published and not approved.**

`versionCode = 63`, `versionName = "5.0"`. **No tag, no push, no GitHub Release.** The bump is
made **once**, in Fase 0, and every later phase of this release inherits it rather than bumping
again — bumping twice is how a round walks into `adb install -r`'s silent downgrade refusal
(`BACKLOG_v4_31.md` item 111). `release-notes/v5.0.md` is written and CI reads it as the release
body; it falls back to a generic one if a version is ever published without it.

**Why the major number, and why `versionCode` did not move.** This release was prepared under the
name **4.32** and renamed before it went anywhere: the neighbourhood is not a tweak to the old
drawing, it is a different visual language, and that is what a major number is for. Nothing was
published as 4.32 and no tag was ever cut, so no user ever saw it and the sequence a user sees runs
**v4.31 → v5.0** with nothing missing. `versionName` is the release's name and `versionCode` is
Android's monotonic install counter — different questions — so the 63 that Fase 0 set stands.
`v5.0` was confirmed free on the public GitHub API on **2026-09-13 at 14:11:53 UTC** (404), with
`v4.31` still the newest published tag.

**What v5.0 is:** the neighbourhood redrawn from scratch, in production, plus the dolphin.
The five building families — small house, large house, tower, restaurant, bar — are no longer one
flat facade each: they are dealt per instance from `NeighbourhoodTable`, so two neighbours carry
two silhouettes. **34 shipped PNGs left and 72 arrived**, both ceilings moved (decoded 36 → 37 MiB,
uploaded 15 → 16 MiB) with the paragraphs those comments require, and the wallpaper and the gallery
preview now compose a building from **one** table through **one** composer rather than from two
hand copies. `release-notes/v5.0.md` is written; this is the phase that finishes the version.

**Fase 0 came first, and had to.** `BACKLOG_v4_31.md` item 112 and its guard: the golden that
warmed a thunderstorm up and failed one run in thirty-two is deterministic, the rule that says a
scene may not do that is a check in both harnesses rather than a sentence in a KDoc, and the
lightning the wallpaper draws is measured to be unchanged. Nothing is re-authored while one of the
rulers rolls a die — and this release re-authors goldens.

**The bump was made once, in Fase 0, and this phase did not touch it.**

**Baseline v4.31, and it is published.** Re-read from the public GitHub API on **2026-09-13 at
14:11:53 UTC** for the rename: `v4.31` is still the newest published tag (`published_at`
2026-09-12T17:48:37Z), as are `v4.16` through `v4.30`, and **both `v4.32` and `v5.0` return 404** —
4.32 was never cut and 5.0 is free. **This line said "v4.31 prepared, not published" until Fase 0
read the API and found otherwise, which is the fifth time.** Re-read the API rather than this line,
and record the instant you read it — nothing
in a working tree learns that a release went out, and this line has been wrong in **both**
directions (`BACKLOG_v4_29.md` item 86, `BACKLOG_v4_28.md` item 79, `BACKLOG_v4_24.md` item 55):

```bash
curl -s https://api.github.com/repos/urgali/paperscrape/releases | grep -o '"tag_name": *"[^"]*"' | head
```

**Three backlogs stand in the root — `BACKLOG_v4_30.md`, `BACKLOG_v4_31.md` and this release's
`BACKLOG_v5_0.md` — and ten are in [`docs/archive/`](docs/archive/).**
`BACKLOG_v4_30.md` carries 94–103, of which **94, 99, 100, 103** stay open or documented, and it
continues to carry forward by name items 18, 25, 30, 40, 50–55, **63** and **83**. **Item 56 is no
longer among them**: `BACKLOG_v5_0.md` closes it, as a decision.
`BACKLOG_v4_31.md` carries 104–110 and **carries forward by name** everything still open in
`BACKLOG_v4_29.md`, which moved to [`docs/archive/`](docs/archive/) in v4.31: items **67**,
**78**, **90** and **92**. Carrying the open items forward by number is what makes the move safe,
and the numbering stays continuous, so item 25 means the same thing wherever it is cited.

**Verified at Level 3 here.** The numbers are in `V5_0_REPORT.md`; Fase 0's and v4.31's are in
their own.

**The three GL references were re-authored, and the suite is green.** The maintainer answered item
56: the references portrayed a neighbourhood that no longer exists (19-22 % of outline against a
3 % limit, which is the scene and not the driver), and leaving three red tests pinned to a phone
nobody owns any more is worth less than references that portray what the app draws. Re-authored on
the BV6600 on 2026-09-13, with the attribution written into `GlGolden.EdgeDisplacement` and
`GlDriverGapGuardTest`. **What it costs is the cross-driver reading**: that guard now measures
0.00 % and will until a second driver exists, and the four historical figures survive only in those
two KDocs. Item 56 closes in `BACKLOG_v5_0.md` as a decision, not as a defect that went away.

**One thing is the maintainer's and is not decided here.** The two shops draw a little over half the
height they declare (`BACKLOG_v5_0.md` item 113) -- a property of the figures that were chosen,
visible in the fase 5 photographs, not fixable by scaling.

```
v5.0 [x] the neighbourhood redrawn, and the dolphin
 |- five families dealt per instance from one generated table: 34 shipped PNGs out, 72 in,
 |  byte-identical to the ones photographed in fase 5. Both ceilings moved with their paragraphs
 |- the three measurements came first, and two changed the plan: 33 blits cost +1.93 ms of a 33 ms
 |  budget on the GPU so NO simplification was taken (and fase 5's "two-row stamp at zero bytes"
 |  turned out to cost ~207 KB); the atlas does not overflow, so the allocated cost is 0 and not
 |  +16 MiB; the Canvas A/B measures a contaminated baseline and says so
 |- the wallpaper and the gallery preview compose from ONE table through ONE composer, so the hand
 |  copy PreviewRendererAgreementTest was written about no longer exists. SkyscraperSpriteLayout
 |  is deleted; SceneColour made the colour rule evaluable on the host, which is what allowed it
 |- 30 Canvas goldens re-authored with the attribution done FIRST (18 756 px in the buildings band;
 |  6 px, a 5x5 box, is the dolphin), double regeneration 30/30 byte-identical, tolerance untouched
 |- the 3 GL references re-authored on the BV6600 (PowerVR Rogue GE8320) after the maintainer
 |  answered item 56, the 30 Canvas goldens byte-identical across the rename, no tolerance moved
 \- JVM 1402/1402 (also rebuilt from scratch out of the delivery ZIP), asset tools 121/121, lint
    green, clean build from the extract without the build cache
```

```
v5.0 Fase 0 [x] the golden that rolled a die
 |- `wave-storm` warmed a thunderstorm up for 320 frames, and the strike timer is the one thing in
 |  the renderer that rolls from an unseeded Random. One frame per strike draws the veil and the
 |  interval averages 32 frames, so that golden failed ~1 run in 32 by the whole frame, from v4.28
 |- fixed where the FRAME gets its randomness, not where the sky does: a scene may pin the strike
 |  timer for its own render, and only the golden harness ever writes the switch. The maintainer's
 |  condition was that the shipped lightning stays random and stays off the clock, and it does
 |- proved by measurement and not by "the default is the same object": v4.31's own production code
 |  and this one, 500 s of storm each, 61 vs 60 strikes, mean interval 32.53 vs 33.36 frames, mean
 |  flash lift 22.76 vs 22.74 of 255. The columns differ by the roll, which is what a roll is
 |- and the guard the KDoc called impossible is in both harnesses, with no exemption for the scene
 |  that caused it: any warmed-up storm that has not pinned is rejected before it renders. Shown
 |  biting on `wave-storm` itself by removing the pin -- 0.238 s, no frame drawn
 \- 100 consecutive runs of `waveStorm` on the BV6600 against versionCode 63 read back with dumpsys.
    0 goldens re-authored, the three GL references untouched
```

```
v4.31 [x] a round of defects: the story that was wrong, the gate that was loose, and four red years
 |- `BACKLOG_v4_30.md` item 98 blamed six drifting goldens on a wing-flap sine caught across a zero
 |  crossing. Swept over every theme x every golden clock x every bird, the closest approach
 |  anywhere is |sin| = 0.0038 against a double's 4e-13 at 1800 radians -- ten orders of magnitude.
 |  It cannot happen and it did not
 |- the real cause, attributed by MUTATION and not by argument: `bird_body.png` was redrawn in
 |  v4.28 and six goldens were never re-authored with it. v4.29 rebuilt with v4.26's bird takes
 |  `lake-dolphin-leap` 384 -> 0, `lake-boats` 62 -> 0, `traffic-day` 14 -> 0, and takes eleven
 |  goldens that were at 0 up to 951-1633. Exactly one sprite changed between the two releases
 |- so the defect is the gate. 0.002 of a 360x800 frame is 576 pixels and its own KDoc claimed a
 |  sprite moved by one pixel would fail it; a sprite REPLACED spent 67 % of it and passed. The
 |  per-pixel tolerance is what absorbs anti-aliasing, and this was a second allowance on top of it
 |- derived to the measured floor: a matching Canvas golden differs by exactly zero on this device
 |  (24 of 30, then 15 of 19, and `theWarmedUpFrameIsDeterministic` has asserted a literal 0.0 for
 |  releases), and the weakest regression that must fail is 14 pixels. The gate sat 41x above it.
 |  Zero is the only non-arbitrary point in [0, 14). A tightening, never a raise
 |- the two asset tests that were red since v4.26 -- four releases, not one: `dolphin_body`,
 |  `sailboat_hull` and `sailboat_sail` got leading padding when the lake was redrawn inside
 |  unchanged canvases. Cropped, all six origins compensated, proved identical by the new zero gate
 |- and `KNOWN_PENDING_CROP_COUNT` was NOT raised. It is still 2; reality came back to it
 |- `normalize --apply` had never once completed -- it looked for a four-space indent in a file
 |  written with one -- and it failed AFTER rewriting six files. Both fixed, plus a message that
 |  claimed "single call site" for three sprites that each have two
 |- the rain's FLOOR was still hung off the child v4.30 redrew, so it relaxed 16.1 % with nobody
 |  deciding. Re-anchored to the adult at the value it has always had. v4.30 did the ceiling and
 |  this is the half it left
 |- the dolphin has not been on its leap point since v4.26, by (+0.87, +1.0) units, and two separate
 |  comments said it was. Corrected; MOVING it is an artwork question and stays open
 |- one fallback, two entry paths, and two documents each naming one. Item 103's +13.6 % lands on
 |  more devices than that item said, not fewer
 |- and the crop was NOT free the first time: cropping to the ink changed 6 px in each of three lake
 |  goldens, because a sprite's transparent margin is the neighbour the bilinear filter reads and
 |  removing it makes the sampler clamp. A guard cell on every trimmed side takes that to 0 / 1 / 1.
 |  The general rule is in ARCHITECTURE.md §3 with a row for each of the four draw paths -- the GL
 |  atlas already had it, and that is why v4.30's crop-after-reduction is not exposed
 |- one measurement went out wrong before it went out right: `adb install -r` silently refuses a
 |  downgrade, so "rendered the previous release" rendered THIS one, and a pixel was attributed to
 |  v4.30 that this release had caused. Corrected by uninstalling first and reading `versionCode`
 |  back; the check is in CLAUDE.md now
 \- and `wave-storm` is a warmed-up thunderstorm, which `GoldenScene`'s own doc says a scene must not
    be. One frame in 32 carries a lightning veil, so that golden has been a 1-in-32 coin flip since
    v4.28 -- under the OLD gate too. Characterised to a tenth of a level; left open, item 112
```

```
v4.30 [x] the people drawn instead of shipped, and the children made children
 |- a person is no longer one PNG per skin tone. It is fixed art plus one weight mask per colourable
 |  region -- skin, head, shirt, trousers -- and the colour arrives at the blit. 168 tone copies out,
 |  195 layer files in, and the four colour axes stopped multiplying the set: shipped the old way,
 |  hair and shirt and trousers would have been 27 copies of every person
 |- the mask is SUMMED and not laid over, and that is the difference between right and wrong rather
 |  than a matter of taste: two source-over layers split the pixel's coverage, `a + b(1-a)` is not
 |  linear, and once the engine halves each one separately the edge grows a halo measured at 63
 |  levels of coverage out of 255. Carried in the sign of the vertex alpha so the batch survives it
 |- four regions cost ZERO draw calls, counted on the device and not reasoned about: one
 |  `glDrawArrays` on three crowded themes, 270 atlas entries and no standalone texture across the
 |  twelve themes. The plan had predicted twenty-four extra draws a frame
 |- a region belongs to a piece and not to a colour. The generator drew the figure and knows which
 |  polygon is hair; asking "which pixels are hair-coloured" hands back 868 pixels of shoe and eye
 |  on the man alone, because his hair, his shoes, his eyes and his shadow are all #2B2A33
 |- the colours are re-dealt every time a walker crosses -- skin, head, shirt, trousers and the
 |  umbrella -- off the CLOCK and not off uptime, which restarts with the process. Deterministic, so
 |  `people-skin` still means something; real randomness is refused for exactly that reason
 |- and NOT on the crossing counter alone: that instant is on screen 52.8 % of the time, measured.
 |  The off-screen guarantee comes from v4.28's umbrella gate -- the cull that actually ran
 |- children redrawn at 0.65 of an adult, the maintainer's choice from three photographed
 |  proportions. Not a scaled adult: the head keeps its size and the legs and torso lose the height,
 |  which is the proportion that changes with age. This line said the number lives in four places
 |  and all four moved; item 102 of that same release found the FIFTH, and v4.31 item 109 corrected
 |  the comment that still said four
 |- `GlTextureCache` crops the transparent border AFTER the reduction, which is what makes a mask
 |  nearly free. Cropping the PNG would pay both ceilings and is not available: the reduction
 |  truncates, so a crop reduces on a different grid and the layers drift apart across the sprite
 |- the two ceilings moved in opposite directions on purpose: texels 17 921 692 -> 14 594 984 B and
 |  the limit down to 15 MiB, decoded 33 286 896 -> 36 912 672 B and the limit up to 36 MiB
 |- item 57 closed by retiring the twelve unreachable winter window recolours, and item 58 with it:
 |  a table nobody reads was alive because a doc comment mentioned it, and comments are now stripped
 \- thirty Canvas goldens re-authored, every changed pixel attributed against v4.29's own render on
    the same device first -- which also measured that six of them were already carrying up to 67 %
    of their budget in device drift before this release began
```

```
v4.29 [x] the atlas packer, and a ceiling that measures the right thing
 |- no artwork moved and no rendering code changed: every golden is byte-identical to v4.28's, which
 |  is the claim this release is built to make cheaply
 |- the defect was the packer. `ShelfPacker` kept one row open, so it lost the tail of every row it
 |  closed and the slack above every entry shorter than its neighbour, and went back for neither --
 |  99 % of the atlas's rows consumed to hold 42 % of its area
 |- measured by walking the twelve themes at their OWN defaults, clear weather, day then night: the
 |  atlas saturates at christmas, the fifth theme, and spills 25 sprites into standalone textures.
 |  At full density, 34. That is the out-of-the-box product, not a contrived worst case
 |- replaced by a skyline: zero spilled on both censuses. The obvious cheap fix -- keep every shelf
 |  open -- was replayed against the recorded insertion order and is no better and sometimes worse,
 |  so it is recorded as measured and refused rather than left as a suggestion
 |- the recorded order is the method: the device is the only place the sequence exists, the host is
 |  where three candidate packers were compared against it in seconds
 |- item 81 closes the other way round. 27 % was content *area*; the rows were at 55 % on the same
 |  scene. 2048 is the floor, not a luxury, and non-square is refused for the same reason
 |- item 80 closed and REJECTED by measurement: the 3x oversample is already spent -- minimum
 |  headroom 0.448, and 50 of 305 sprites below the 1.5 a grid of 2 needs just to stay at 1:1
 |- the decoded ceiling is not GPU memory and never was. Two limits now, with two honest names: the
 |  old one keeps its value and guards the `Canvas` path every device's settings screen takes, and
 |  a new 18 MiB one measures the texels actually uploaded
 \- item 82's three stale numbers re-measured rather than re-typed, plus a fourth the santa crop
    report had already reported and nine releases had not removed; the guard's hand-written file
    list replaced by a directory walk
```

```
v4.28 [x] a bird that reads, an umbrella in the rain, and a sea that moves
 |- bird B1 "Rondine" replaces the shipped drawing on the same 51x21 canvas, the same origin and
 |  the same flap axis. Nothing is mirrored: v4.27 measured that the facing was never wrong. What
 |  was wrong is legibility at the 42 px the bird reaches -- raised wings read as a fanned tail, so
 |  the eye turned the animal round -- and the fix is to move the largest shape behind the head
 |- the umbrella: pose P1 "Alzato" with canopy U1 "Alta", adults only, rain only, never snow. The
 |  handle is a rectangle drawn in code from the hand to the crown, so the same pose carries any
 |  object later without new artwork. Who carries changes only while no copy of the walker is on
 |  screen -- v4.22's car rule, which `CarSelection.offScreen` says in as many words was never
 |  extended to people
 |- the wave: WA3 "Tubo", two tintable masks, three slots, rain and thunderstorm only. A clear sky
 |  draws none and the frame is identical to v4.27's
 |- the wave's colour derived per frame, not chosen: gates at 24.9 (body) and 34.9 (foam) of
 |  Rec. 601 luma placed by the v4.22 rule between a measured floor and a measured signal over
 |  2 592 situations, with the direction chosen on the cheaper side. One direction does not hold:
 |  always-toward-black puts the body under 40 of luma in 901 of those 2 592
 |- and the gate is a floor, not a target: at night the renderer aims at the signal instead, which
 |  is the other end of the same derivation rather than a new number
 |- the waves join `LakeLanes.orderByDepth` instead of being drawn before the boats, keyed by their
 |  waterline said in the boat's own convention. A nearer wave now passes in front of a farther hull
 |- the decoded-sprite ceiling raised to 32 MiB for the carrying pose, on the maintainer's
 |  conditional authorisation, with the A/B memory measurement that condition required
 \- this file had v4.27 as unpublished and v4.26 as the baseline; both were false
```

```
v4.27 [x] the rain that could not be seen, and a bird that was not backwards
 |- rain and sky measured against each other over the twelve themes, the clock swept in five-minute
 |  steps, clear / live rain / thunderstorm, thirteen heights down the fall -- 10 368 situations, on
 |  the host. Eleven of the twelve themes have an hour at which the two are the same brightness;
 |  the worst is 0.00 against a median of 28.62
 |- the drop's colour derived per frame against the sky it falls through, to a gap placed by the
 |  v4.22 rule between the 0.00 the failing case gives and the 26.94 the same drop gives over the
 |  hills. No tolerance moved; a theme already clear is drawn bit-identically to v4.26
 |- snow measured the same way and left alone with the number: worst 19.39, so the correction never
 |  fires on it
 |- `rain-worst-sky`: a golden at the measured worst case, with the test that names it asserting it
 |  is still the worst case -- the frame and the measurement cannot drift apart
 |- the reported bird defect does not hold: the sprite already faces its own travel and mirroring it
 |  would have created the defect. `bird-facing` pins one bird at the size it ships
 |- DESIGN_NOTES 14: "in both directions" means the directions the shipped build reaches. v4.26 met
 |  that requirement with a capture-only mirror, which is how the birds were never really judged
 |- the horror sky never recorded the sky it drew, so a lake under it mirrored a colour that was
 |  never computed. One line, reachable in any theme
 \- item 66 decided by the maintainer: the `perf` build type is committed, with the check that keeps
    committing it safe, and the published APK proved identical with and without it

```

```
v4.26 [x] the sky and the sea redrawn
 |- the water becomes a mirror of the sky -- concept S1 "Specchio", chosen from photographs on the
 |  device against a long swell and rows of strokes -- with the light's path under the sun or the
 |  moon, and a struck waterline whose colour is derived per frame so the shore cannot vanish into
 |  a sky the water is reflecting
 |- cloud C1 "Batuffolo" and bird A "Colomba" promoted through the asset pipeline from committed
 |  SVG sources; the bird drops from 90x24 to 51x21 canvas pixels and its flap axis moves with it,
 |  so the wing-beat is a wing-beat instead of a mirror of a symmetric shape
 |- the dolphin's papers derived instead of chosen: CIELab dE 1.53 from the water at its worst on
 |  the shipped artwork, 17.97 now, against a gate placed by the v4.22 method
 |- BACKLOG_v4_25 item 61 closed with proposal C: UnitFrameTest fails any expression naming two of
 |  the seven length systems without one of the five conversions, and it was shown to bite by
 |  reintroducing the real v4.25 defect
 |- item 65 closed by derivation: three new gates over the cloud band, the bird band and the water
 |  band, no tolerance moved and no number lowered
 \- item 64 decided by the maintainer: the double golden regeneration now runs only on the scenes
    whose frames changed
```

```
v4.24 [x] a documentation review: the documents made to say what is true
 |- the mandatory reading list goes from 148 824 words to 13 272; ARCHITECTURE and DESIGN_NOTES
 |  become conditional reads, RELEASE_HISTORY a targeted consultation. ~2 000 words deleted in all
 |- docs/archive/ takes the closed backlogs, the eleven pass reports and the ROADMAP history, with
 |  an index; no source file edited to follow them, because the citations are bare names
 |- AI_PROJECT_RULES 14.9 / 14.10 / 14.11: one report per release, no paragraph correcting another
 |  in the same document, and a command wherever a number decays
 |- stale counts replaced by their commands: 688 tests (1 340), 148 instrumented (149), 1 085
 |  tests, 132 person sprites (166), 0 compiler warnings (19)
 |- DESIGN_NOTES 16-30 regrouped under the themes they belong to, zero lines lost; D1-D5 given
 |  one home in §12, and ANSWERED_QUESTIONS.md folded in and deleted
 \- the publication state read from the Releases API instead of from this file: v4.20-v4.23 are
    published, which this file had denied since the day it stopped being true
```

```
v4.23 [x] the sky cut with scissors: eight celestial sprites redrawn from concept B "Forbici"
 |- sun, sunburst, four moon phases, the pumpkin moon and the star sparkle, promoted through the
 |  asset pipeline from committed SVG sources; every canvas unchanged, so the decoded sprite set
 |  and every blit origin are exactly what they were
 |- the sparkle is blitted at twice the scale (STAR_SPRITE_RADIUS_DIVISOR 32 -> 16) and redrawn
 |  for it: a waist that survives the reduction instead of a one-pixel cross. No star moved
 |- the tile extents re-derived from what is actually drawn rather than doubling the star radius,
 |  which would have hidden the same symptom by changing the star field itself
 |- 16 of 24 Canvas goldens regenerated after a per-region attribution; the four gate rectangles
 |  measure zero on every scene, the three GL goldens deliberately untouched, no tolerance moved
 |- round 2C: a golden for the pumpkin moon, with Halloween AND realistic phases both on, so the
 |  frame can fail if the renderer's override ever leaks. First committed frame in which a
 |  celestial body is drawn clear of the cloud band -- no other one contained a sun or a moon
 \- round 2C: the "Realistic Moon Phases" switch is shown off and locked under Halloween, with
    the reason. The stored preference is overridden, never written: it comes back per theme
```

---

## Known broken

Nothing. **D13** -- the in-app updater hanging on `Downloading` -- was the only entry and is closed
in v3.0; `RELEASE_HISTORY.md` carries what it actually was. The v3.0 assessment's five P0/P1/P2-now
items were closed in v3.1, and its two P1 test-infrastructure items plus P2-3, P2-4 and P2-7 in v3.2.

---

## Next priorities

**What is left of the v3.0 assessment**, plus the one thing v3.2 created. None of it is approved.

| # | ID | Item | Why it is here |
|---|---|---|---|
| A | — | **Real-hardware verification of v4.0** | `targetSdk 37` shipped in v4.0 and every check that an emulator can perform passed. What an emulator **cannot** settle is **certificate transparency** and **ECH**: its network stack, CA store and system image are not a phone's, and a CT or ECH failure would present as a plain connection failure on real hardware while passing here. All five HTTPS hosts connected at `targetSdk = 37` on the emulator, and no workaround was added, so this is a confirmation rather than an open risk — but it is the maintainer's to confirm. **Also outstanding: OpenWeather and WeatherAPI.com end to end**, which v4.0 could not drive because both supplied keys had been revoked (`401` from `curl` on the host as well as from the app). |
| B | — | **Preview/renderer offset agreement is guarded for two groups** | The tree and the tower. The other 47 shared sprites agree today as plain literals and nothing stops them drifting. v3.8 checked all of them and found no third case worth the indirection; the guard would have to encode each renderer draw function's nested transforms, which is a second copy of the thing being checked. **Not scheduled.** |
| C | — | **The lit night facade's placement is unverified against the artwork** | v3.8 made the preview follow the renderer, which is documented intent (*"laid over it at the same origin"*) and is confirmed by the Christmas window-light grid hanging at the same `+5`. What was *not* possible was measuring it the way the snow cap was: the wall sprite is a plain tintable rectangle with no detectable window grid, so there is no artwork feature to align against. **Not scheduled** — recorded so the basis for the choice stays visible. |
| D | — | **The instrumented tests have no automated trigger** | Unchanged since v3.6. `AI_PROJECT_RULES.md` 10.12 and 10.13 state what a future E2E job must satisfy. **Not scheduled.** |

Deliberately **not** scheduled: any further weather-provider change — the three the comparative
assessed are all now implemented and **Open-Meteo stays the default** — and any refactor of
`PaperRenderer`, `SceneObjectRenderer` or `ThemePreviewScene`. `targetSdk 37` shipped in v4.0 and is no
longer scheduled work; what remains of it is item A's confirmation on real hardware. **D10 is
closed by v4.0** -- `targetSdk` and `compileSdk` are both 37 now.

---

## Deferred

Genuinely open, genuinely not worth doing yet. The rows numbered 1-7 came from a section
called "Older priorities" and were merged here by the 2026-09-07 documentation pass: the
section held nothing that was not either deferred or already closed, and the closed rows
are in `docs/archive/ROADMAP_HISTORY.md`.

| ID | Item | Why deferred |
|---|---|---|
| **B5** | The renderer, wallpaper engine, preferences layer and Compose UI cannot be unit tested without being decoupled from `Canvas`/`Context`. | The reason engine fixes are verified on a device rather than by a test. Decoupling is a large refactor with no user-visible result; it earns its place only if engine bugs start recurring. |
| **D4** | Whether the `MULTIPLY` tint's colour-fidelity trade-off is acceptable. | Accepted in practice across the whole V2 set and never reported as a problem. |
| **D11** | Three lint findings that only appeared once the tooling was current: `ConfigurationScreenWidthHeight` on `SettingsInsets.kt:115`, and three `AutoboxingStateCreation` hints on `SettingsComponents.kt`. | New checks over unchanged code, not regressions. `SettingsInsets` is the file that closed v2.14's dialog-sizing bug, so swapping `Configuration.screenHeightDp` for `LocalWindowInfo.current.containerSize` is a change to the one thing that bug turned on — worth doing deliberately, with a device pass, not as a lint tidy-up. |
| **D12** | Every `OutlinedButton` changed colour in the upgrade. Material3 `1.4.0` moved the default content colour from `primary` to `onSurfaceVariant` and the default border from `outline` to `outlineVariant`, so "Reset this theme's scene to defaults" and the other six outlined buttons now read grey-brown with a pale border instead of orange with a mid border. Verified on an Android 17 emulator by sampling the pixels: the new values are exactly this project's own `onSurfaceVariant` (`0xFF54443A`) and `outlineVariant` (`0xFFD9C7B7`). `TextButton` and filled `Button` are unchanged. | This is Material 3's own current default, and rule 3 says the app follows Material 3 — so it was **left as Material draws it** rather than pinned back, which would mean hard-coding a superseded default into seven call sites. It is nevertheless the one user-visible change the whole upgrade produced, and whether the quieter outlined button reads well is a judgement to make while looking at the app. Pinning it back is one argument: `colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)` plus `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`. |
| **D7** | The V2 artwork retired four user-visible colour behaviours (sun colour reaching only the glow, theme star colour reaching nothing, Fall Colors not reaching palm fronds, per-building window lighting). | Approved as consequences of the redesign. Whether each reads well is a judgement to make while looking at the app, and nothing has been reported. |

| # | Item | Why it is here |
|---|---|---|
| 1 | **Device pass on v2.0's theme defaults** | Every built-in theme's defaults were reviewed and corrected: the winter family now enables the winter presentation (roof snow, snow-capped trees, winter clothing), Autumn enables Fall Colors and pumpkins, umbrellas leave the cold themes, the tundra lake loses its yachts and dolphins, Beach stands on sand, Desert gets palms, City is built rather than settled. Winter and Christmas are now two independent flags, so a snowy scene without fairy lights and a lit scene without snow are both expressible. Winter and Christmas snow by default. A fresh install now looks materially different per theme, and v2.0 shipped without any of it having been seen rendering. |
| 2 | **Star-field cost, if it still matters** | Most stars became single `drawCircle` points shortly before v1.0, which cut the per-frame count to roughly a third. Whether the remainder is still worth attention is a question for a device, not for a static count. |
| 3 | **Mountain paths rebuilt per frame** | Two `Path` objects per mountain per frame, from the CPU audit. Real allocation on a draw path; worth doing only if the device shows it. |
| 4 | **Per-vehicle-type toggles** | Cars, taxis, police and fire engines share one visibility switch. Small, self-contained, low value — do it when something else is already open in that file. |
| 6 | **Device pass on the parts v2.14 did not reach** | v2.14 saw the five destinations, the colour scheme and the settings shells rendering on a Pixel 9, which closes the bottom-spacing half of this. Still unseen: the twelve mini-scene previews (verified by rasterising the scene description the code produces, not by the app drawing it), and v2.12's sun/moon and people work. The updater's end-to-end run left this row for **D13** and was done in v3.0. |
| 7 | **README / lint / KDoc tidying** | `UseKtx`, `ObsoleteSdkInt`, `DataExtractionRules`, and KDoc that has accumulated layers across releases. |

**Localisation is explicitly out of scope.** PaperScrape is English-only by decision;
about seventy UI strings remain inline in Compose rather than in `strings.xml`, and
that is fine unless the decision changes.
