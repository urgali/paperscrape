# BACKLOG_v4_27.md — what v4.27 decided, and what it left open

**Replaces `BACKLOG_v4_26.md` for new items only.** Everything `BACKLOG_v4_23.md`,
`BACKLOG_v4_24.md`, `BACKLOG_v4_25.md` and `BACKLOG_v4_26.md` leave open is still open and is not
restated here; all five files stay in the repository root until their open items are closed.
Numbering continues: `BACKLOG_v4_26.md` reached item 71, so this file starts at 72.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided
against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered), or
**OPEN** (left undone on purpose, with what closing it would take).

The measuring device is a **Blackview BV6600** (MediaTek Helio A25, PowerVR GE8320, Android 10,
720×1440). **Colour measurements in this pass were taken on the host**, not on it: the contrast
between a theme's precipitation colour and its sky is arithmetic over the theme's own numbers, and
computing it for every theme at every hour costs seconds with the phone switched off. The device
confirmed the remedy in the worst case and took the captures, which is what it is for.

---

## What v4.27 closed from `BACKLOG_v4_26.md`

| item | what | how it was closed |
|---|---|---|
| 66 | The `perf` build type is rebuilt by hand every session | **CLOSED by the maintainer** — committed, with the proposed check. `BuildTypeDeclarationTest` pins the declared set of build types, pins `perf` to the debug signing config, the `.debug` suffix and `isDebuggable = false`, and asserts that no workflow builds it. That nothing shipped changes was **verified rather than deduced**: see item 77 |
| 71 | The GL references were re-captured on PowerVR, and the Adreno gap is no longer measured | **RATIFIED by the maintainer** — accepted as the only possible outcome. What it would take to get the lost measurement back is written down in item 78 rather than left to be rediscovered |

---

## Summary

| item | what | outcome |
|---|---|---|
| 72 | "The birds fly backwards" — the mechanism verified, and the conclusion it does not support | **DOCUMENTED** — the sprite already faces its own direction of travel; mirroring it would have created the defect it was meant to fix |
| 73 | The rain and the sky can be exactly the same brightness, in eleven of twelve themes | **RESOLVED** — measured over the whole sweep on the host, corrected by a derived minimum luma gap, pinned by a golden at the measured worst case |
| 74 | The horror sky never recorded the sky it drew | **RESOLVED** — a one-line defect in v4.26, found by the item-73 work, reachable by any theme with the horror sky and the lake both on |
| 75 | Snow against the cloud it is born in measures 0.00 | **DOCUMENTED** — measured, out of scope for the reported defect, and recorded with what closing it would mean |
| 76 | The acceptance sheet was satisfied by a mirror the shipped build does not have | **RESOLVED** — `DESIGN_NOTES.md` §14 rewritten, and a golden that shows a bird as the shipped build draws it |
| 77 | Committing `perf` changes nothing that ships | **RESOLVED** — the release APK built with and without the block, and compared |
| 78 | The cross-driver GL measurement, and what it would take to have it again | **OPEN** — a condition, not work; recorded so it is not rediscovered in six releases |

---

## 72 — "The birds fly backwards" — the mechanism verified, and the conclusion it does not support

**DOCUMENTED, and the artwork was not touched.** The maintainer reported from the phone that the
birds carry their tails in the direction of flight and their heads behind, and supplied the
mechanism: `PaperRenderer.drawBirds` applies only a **vertical** mirror, which is the wing-beat —

```kotlin
canvas.scale(1f, if (flap < 0f) -1f else 1f)
```

— there is no horizontal mirror and no direction term anywhere in the function, and every bird
moves left to right (`x = drift * (screenWidth + 200f) - 100f`, `drift` from 0 to 1). The proposed
remedy was to mirror the SVG source horizontally and regenerate through the asset pipeline.

**Every part of the mechanism is correct. The conclusion is not.**

- The mechanism was checked and holds: `drawBirds` carries one `canvas.scale(1f, ±1f)` and nothing
  else; neither backend adds a mirror of its own (`GlSceneTarget.drawSprite` maps `u0` to the left
  edge and `u1` to the right, `SpriteBlitter` blits `CANVAS_PIXELS` straight through); and the
  travel direction was confirmed **on the device** rather than read — four frames 1.2 s apart put
  one bird at x 462, 531, 595 and 663.
- **The shipped sprite already faces that way.** `bird_body.png` is 51×21 with the head circle at
  x 43 and the beak from x 46 to x 49.5 — the right-hand end — and the tail wedge between x 4 and
  x 14. Blitted at origin `(-25, -15)` and moved toward +x, the head leads.
- So mirroring the source would have **created** the defect it was meant to fix.

**What is true underneath the report.** At 51 px the bird's direction is carried by features that
do not survive: the head is a 3.6 px disc continuous with the body and the beak is a 3.5 px wedge,
while the raised wing is the largest shape in the silhouette and rises up and forward, which is
also the shape of a fanned tail. `DESIGN_NOTES.md` §2 already states the rule this runs into —
*"silhouettes are judged at the size they are drawn"* — and the bird was judged in v4.26 on sheets
where half the candidates were mirrored by a capture patch, which is item 76.

**What was done instead**: nothing to the artwork, because artwork needs a mockup and the
maintainer's approval (`AI_PROJECT_RULES.md` 13.1), and because the sprite is not wrong in the way
reported. What the pass does add is the cheap half of "stop it happening again", which the
maintainer asked to be proposed with its cost: **`bird-facing`, a golden frame with one bird on
clean sky at the size it ships, with a focus rectangle on the animal.** One committed PNG and one
assertion. It cannot judge whether a drawing reads; it makes any change to the bird's shape or its
facing fail a check and put the frame in front of a person, which is exactly the step v4.26 skipped.

**What is left for the maintainer**: whether the bird should be redrawn so its direction reads at
51 px. That is artwork and is not decided here.

---

## 73 — The rain and the sky can be exactly the same brightness, in eleven of twelve themes

**RESOLVED.** Reported from the phone as rain that could not be seen until it reached the hills,
on Autumn at about 08:00 with the cloud cover high.

### The mechanism

The maintainer's reading was right in substance: the drop's colour comes from the theme and is
fixed —

```kotlin
blendColor(precip.rainColorNight, precip.rainColorDay, dayPhase.dayBlend)
```

— while the sky changes with the hour. One correction to it: **the sky's own gradient does not
change with the cloud cover.** Coverage changes what is *drawn over* the sky, and it changes the
sky's colour only under Live Weather, through `StormAtmosphere.dimSky`. Both paths are in the
measurement below.

### The measurement — on the host, 10 368 situations

Twelve built-in themes × the clock swept in five-minute steps through the real
`SunPositionCalculator` × clear / live rain / thunderstorm × thirteen heights down the stretch a
drop crosses with sky behind it (the cloud band's own middle, where a drop is born, to the top of
the hills). The metric is the **Rec. 601 luma of the stroke as it is actually composited** — rain
is painted at alpha 190, and luma is linear in RGB, so the separation the eye is given is the
colour separation scaled by that alpha.

| | rain against the sky | snow against the sky |
|---|---|---|
| worst | **0.00** | **19.13** |
| median | 28.62 | 112.76 |
| worst case | Easter **06:35**, live rain, sky `#878F96`, drop `#6A96BE`, 0.54 down the screen | Easter midday |

**Eleven of the twelve themes have an hour at which the rain is exactly the sky's own brightness.**
The exception is Halloween, and it is not a virtue: the horror sky is near-black over a hard orange,
so a mid-blue rain cannot collide with it. At the worst case the drop is CIELab dE **20.95** from
the sky — "clearly a different colour" — and 0.00 of luma from it. **dE is the wrong metric here**,
and that is the finding rather than a detail: `LakeContrastTest` uses it for a 40 px animal, where a
shared lightness with a different hue still reads, but a raindrop is a **1.19 px** stroke at
720×1440 and chromatic acuity collapses at that width. The right metric is the one the eye still
has there, which is the one `WATERLINE_MIN_LUMA_GAP` already uses for the project's other hairline.

### The remedy, derived

`PaperRenderer.PRECIPITATION_MIN_LUMA_GAP = 13.47f`, by the v4.22 rule with both arms measured on
the same sweep: the floor is the failing case, **0.00**; the signal is the same drop, at the same
width and alpha, over the background the defect report itself names as where the rain becomes
visible — the hills — whose median separation is **26.94**. The gate is the midpoint.

`drawPrecipitation` carries the theme's colour toward white or black until it is that far clear of
the sky, **and no further**: a theme already clear is drawn exactly as v4.26 drew it, bit for bit.
2 259 of the 10 368 situations are corrected at all, 21.8 %. The worst case goes from `#6A96BE` to
`#85A9C9` — the same pale blue, lifted — and the largest carry anywhere is CIELab **16.19**, New
Year at 08:40, `#7EB2DF` drawn `#6189AB`.

**One colour for the whole fall, and that is forced rather than preferred.** A per-height
correction cannot exist: the sky's luma runs monotonically down the fall and the drop's does not
change, so whenever the two are close the sky crosses the drop inside the fall, and a function
clearing the gap at every height would have to sit above the sky at one end and below it at the
other with no continuous way between. That is a frame carrying pale rain above one line and dark
rain below it. What one colour costs instead is a direction flip when the sky's own band crosses the
drop's brightness, which is the same impossibility moved from space into time: measured at **twice a
day in every theme, at the two ends of it** — Sunset 06:09→06:15 takes the drawn rain from `#81A2C0`
to `#4D6D8B`, and 19:39→19:45 takes it back. Bounded at two per theme per day by
`PrecipitationContrastTest` rather than removed, because a third flip would mean the derivation had
stopped being monotone in the hour and the design would need re-examining.

### Snow

**Measured the same way and left alone, with the number.** Worst separation from the sky **19.13** (Easter at 07:39),
above the gate, so the correction resolves to zero for every snow situation there is. It stays on
the shared code path because falling snow is precipitation and a second path would be a second
thing to keep true; `PrecipitationContrastTest` asserts that it never fires. Snow against the cloud
it is born in is item 75.

### What the correction deliberately does not reach

**The theme preview cards.** `ThemePreviewScene` draws precipitation as **dots at the theme's day
colour**, not as the renderer's streaks, on a card whose sky is a fixed day sky. It is a stylised
card rather than a reproduction of the scene, and it is not corrected. Two reasons, and the second
is the one that decides it: the card has no clock, so "the sky at this hour" — the whole input to
the derivation — does not exist there; and by default the only themes whose preview shows
precipitation at all are Winter and Christmas, both snow, which the correction never fires on. So
the card and the wallpaper agree on every default today. If a future preview grows a time of day,
this is the paragraph to come back to.

### The frame

v4.26 shipped a derived waterline for a case no committed golden portrayed. This one is pinned at
the moment of the fix: **`rain-worst-sky`** draws Easter at 06:35 under live rain, with a focus
rectangle on the band of open sky the rain crosses, and `PrecipitationContrastTest` asserts that
this is still the worst case the twelve themes can paint — so the frame and the measurement cannot
drift apart silently.

---

## 74 — The horror sky never recorded the sky it drew

**RESOLVED, and it is a defect in v4.26 rather than a consequence of this pass.** Found while
building item 73's measurement.

`drawSky` writes `skyTopColorNow` and `skyHorizonColorNow` — the two fields that mean "the sky this
frame drew" — after the horror-sky branch, and that branch **returns**. So with the horror sky on,
both fields held whatever the previous frame left, and **zero on the first frame**, which is
transparent black.

Three things read them: the water's mirror (`lakePaint.color` carried toward the horizon by
`LAKE_MIRROR_SKY_SHARE`), the struck waterline, and now the rain. The horror sky is a **user
switch, independent of the theme** (`DESIGN_NOTES.md` §16), so this is reachable in any theme, not
only Halloween: turn on the horror sky and the lake together and the water mirrors a colour that
was never computed. The Halloween defaults do not turn the lake on, which is why nobody had seen it.

Fixed by recording both colours before the return. `PrecipitationContrastTest` reads the source back
and fails if the assignment leaves that branch again.

---

## 75 — Snow against the cloud it is born in measures 0.00

**DOCUMENTED. Recorded so it is not rediscovered, and deliberately not fixed here.**

The item-73 sweep measured every background a drop meets, not only the sky. Snow against the
**cloud band** is white on white: separation **0.00** at midday in every theme, median 91.38. Snow
against **snow-laden hills** is the same shape of thing — Christmas 3.08, Tundra 3.78, Winter 5.77.

It is left alone for three reasons, and none of them is that it does not matter:

- the reported defect is about the sky, and this pass's gate was derived for that case. A gate
  derived for one background is not evidence about another;
- a flake leaving a white cloud is what the fade-in over the first tenth of the fall already exists
  for: the drop is *supposed* to emerge rather than appear;
- a snowflake on a white mountain is arguably the scene being right rather than the scene being
  broken, and that is a judgement to make while looking at the app, not a number to fix.

**What closing it would take**: the same sweep with the cloud and the winter hill colours as the
background, a signal arm chosen from a case that reads, and a decision about whether snow should
stand off its own cloud at all — which is a look, and so needs a mockup and approval.

---

## 76 — The acceptance sheet was satisfied by a mirror the shipped build does not have

**RESOLVED.** `DESIGN_NOTES.md` §14 required every sprite with a facing to be shown in both
directions. v4.26's bird sheets met it, and met it through a **capture-only patch that mirrored the
odd candidates** — the sheets say so in their own headers: *"i candidati dispari volano verso
sinistra: specchio di sola cattura"*. The patch was removed for the release. So the requirement was
satisfied by a picture of something the app cannot draw, and the bird's real, single facing was
never the subject of a judgement. That is how item 72 got as far as a defect report.

§14 item 2 now reads that a direction belongs in the judging image **when the shipped build can
produce it**, and that where a direction is unreachable without scaffolding the thing to report is
that it is unreachable. A capture patch may enlarge, slow down or place a sprite; it may not change
what the sprite is.

The checkable half is the `bird-facing` golden described in item 72.

---

## 77 — Committing `perf` changes nothing that ships

**RESOLVED, and it was verified rather than deduced**, which is what the maintainer asked for.

Three claims, three checks:

- **CI never builds it.** `BuildTypeDeclarationTest` asserts that no workflow names `assemblePerf`,
  `bundlePerf`, `installPerf` or any `perf` test task, and that some workflow still builds
  `assembleRelease`. A claim about CI that is only in a comment is a claim nobody re-checks.
- **It cannot become shippable.** The same test pins `perf` to `initWith(release)`, the committed
  debug signing config, `applicationIdSuffix = ".debug"` and `isDebuggable = false`, and pins the
  whole declared set of build types so a fifth cannot appear unnoticed.
- **The published artefact is byte-identical.** `assembleRelease` was built twice from a clean
  tree, once with the block present and once with it removed, and the two APKs compared entry by
  entry. The result is in the v4.27 report.

---

## 78 — The cross-driver GL measurement, and what it would take to have it again

**OPEN, and it is a condition rather than work.** Item 71 is ratified: the three GL reference frames
are PowerVR GE8320's, the re-capture was forced by artwork that made the Adreno-authored frames
impossible for any driver to match, and no tolerance was moved to get there. Nothing about that
decision is reopened here.

**What it cost, stated once so it is not restated as news later.** `GlDriverGapGuardTest` measures
the gap between the running driver and the committed frames. On this device that is now
approximately zero, which means the guard currently proves that this device still agrees with
itself. The **cross-driver** gap — 1.18 / 1.07 / 0.92 % when it was first characterised, 1.2–1.4 %
when v4.19 re-measured it — **is not being measured anywhere**, and will not be until the suite runs
somewhere else.

**What would close it**, and it is one sentence: **run the instrumented suite on a second device
with a different GPU vendor**, and re-derive `GlGolden.EdgeDisplacement`'s characterisation from
that run. Nothing else is needed and nothing else will do — per-driver golden sets were considered
and rejected in v4.20, because they double the maintenance and make "the golden" ambiguous, and
nothing here reopens that either.

**Why this is written down rather than left implicit.** The guard still passes, so nothing will ever
fail to remind anybody that it has stopped measuring what it was built to measure. A green check
over a check that no longer checks anything is the failure mode this project has already met twice —
`BACKLOG_v4_25.md` item 65 and `BACKLOG_v4_26.md` item 69 are both instances of it.
