# V4_27_REPORT.md — the rain that could not be seen, and a bird that was not backwards

One report for the release (`AI_PROJECT_RULES.md` 14.9). Every claim carries its label:
**OSSERVATO** (seen with my own tools in this pass), **MISURATO** (a number produced by a procedure
stated beside it), **DEDOTTO** (a conclusion drawn from those, not itself seen), or **DICHIARATO**
(stated by the maintainer or by an earlier pass and not re-derivable here).

Device for everything on hardware: **Blackview BV6600**, MediaTek Helio A25, PowerVR GE8320,
Android 10, 720×1440 at density 320, over `adb`. "The device" always means that one. **Colour
measurements in this pass were taken on the host with the phone switched off**, on the maintainer's
instruction and because the contrast between a theme's precipitation colour and its sky is
arithmetic over the theme's own numbers.

Baseline: **`PaperScrape_v4_26.zip`**, SHA-256
`4932e27be139b5f240e16d435d17700505ccb4ca664d57adef070c0836a4dc5b`, 8 517 574 bytes, 1 437 files —
**OSSERVATO**, both checked before extracting.

**v4.26 is published** — **OSSERVATO**, read from the public Releases API on 2026-09-10: tag
`v4.26`, non-draft, non-prerelease, `published_at` **2026-09-09T22:21:32Z**, built by
`github-actions[bot]` from commit `415caa2c`, with `PaperScrape-v4.26.apk` attached. Not read from
any document in the tree.

---

## 0. The three things to read first

**1. The birds are not flying backwards, and the proposed remedy would have made them do so.**
The mechanism you found is correct in every part — the only mirror is vertical, there is no
direction term, and every bird moves left to right. What does not follow is the conclusion: the
shipped sprite already carries its head at the leading end. Mirroring the SVG would have created
the defect it was meant to fix. Details and the evidence in §1.

**2. The rain defect is real, it is worse than reported, and the metric this project has been using
would not have found it.** Eleven of the twelve themes have an hour at which the rain is *exactly*
the sky's own brightness. At the worst case the rain is CIELab dE **20.95** from the sky — by the
measure that settled the dolphin, "clearly a different colour" — and **0.00** of luma from it. dE is
the right metric for a 40 px animal and the wrong one for a 1.19 px stroke.

**3. Your reported case is real but it is not the worst one, and it depends on how the rain got
there.** Autumn at 08:00 with 90 % cover measures **5.97** when the rain comes from Live Weather and
**19.38** when it comes from the theme's own switch. The deepest collapses are Easter, Spring,
Desert, Christmas and Tundra, all at 0.01 or below. The remedy is derived over the whole sweep, so
your case is covered — but the number you would get from measuring only your case would have been
the wrong number.

---

## 1. The birds — the mechanism verified, the conclusion refused

### What was checked, and how

**OSSERVATO.** `PaperRenderer.drawBirds` carries exactly one mirror:

```kotlin
canvas.scale(1f, if (flap < 0f) -1f else 1f)
```

There is no horizontal mirror and no direction term in the function. Neither backend adds one:
`GlSceneTarget.drawSprite` maps `u0` to the quad's left edge and `u1` to its right, and
`SpriteBlitter` blits a `CANVAS_PIXELS` sprite straight through with no matrix at all. Every bird's
x is `drift * (screenWidth + 200f) - 100f` with `drift` running 0 to 1 through `SceneTime.cycle`,
which is monotone increasing.

**MISURATO, on the device.** Rather than trust the reading, the travel direction was confirmed from
the running build: four screenshots 1.2 s apart put one bird's dark mass at x **462 → 531 → 595 →
663**. Left to right, as the code says.

### Where the conclusion fails

**OSSERVATO.** `app/src/main/res/drawable-nodpi/bird_body.png` is 51×21. Its source,
`tools/assets/sources/svg/bird_body.svg`, places the head as a disc at **x 43** with the beak
running **x 46 → 49.5** — the right-hand end — and the tail as a quadrilateral between **x 4 and
x 14**. The blit origin is `(-25, -15)`, so the sprite spans −25…+26 around the bird's own position
and the head sits at +18: **on the leading side of a bird travelling toward +x**.

So the drawing already faces the way it flies. `immagini_report/uccello_A_B.png` is the frame from
your own phone, with the same bird mirrored beside it for comparison.

### What is true underneath the report

**DEDOTTO.** At the size the bird ships, the features that say which way it is going do not survive:
the head is a 3.6 px disc drawn continuous with the body, the beak is a 3.5 px wedge, and the
largest shape in the silhouette is the raised wing, which rises up and **forward** — which is also
the shape of a fanned tail. `DESIGN_NOTES.md` §2 already states the rule this runs into: *a
silhouette is judged at the size it is drawn*.

**Nothing was changed to the artwork.** A redraw is a look, and a look needs a mockup and your
approval (`AI_PROJECT_RULES.md` 13.1). It is also not obvious that a redraw is wanted: the animal is
correct, and what is at stake is legibility at 51 px.

### The cheapest way to stop it happening again — proposed, with the cost

You asked me to propose. **A reference frame, not a test that reads asymmetry.**

`bird-facing` is a golden scene with the clouds switched off, bird density at 1, and a focus
rectangle on one bird at the size it ships. The scene instant was solved from the same candidate
noise the renderer uses rather than picked: at `sceneSeconds = 0.3` bird 0 sits at x 258, y 290 —
clear of the cloud band, clear of both edges — with its flap term positive, which is the pose the
sprite is authored in rather than its vertical mirror.

**Cost: one committed PNG (a few kB) and one assertion.** It cannot judge whether a drawing reads —
no test can. What it does is make any change to that sprite's shape or facing fail a check, so the
frame goes in front of a person. That is precisely the step v4.26 skipped, and §4 below is why.

An asymmetry test was considered and rejected for the reason you gave: it would have to encode what
"the head end" means for every animal in the library, which is a second copy of the artwork
expressed as arithmetic.

---

## 2. The rain — measured, and corrected by derivation

### The mechanism, with one correction to yours

**OSSERVATO.** The drop's colour is `blendColor(precip.rainColorNight, precip.rainColorDay,
dayPhase.dayBlend)` and the pair is the same for **all twelve themes** — no theme overrides it.
The sky changes with the hour, with the twilight branch, and under Live Weather with
`StormAtmosphere.dimSky`.

**One correction.** The sky's own gradient does **not** change with the cloud cover. Raising the
cover changes what is drawn *over* the sky and changes the sky's colour only through Live Weather's
storm strength. Both are in the sweep, so nothing is lost by the correction — but "the sky changes
with coverage" is not what the code does.

### The measurement — on the host, 10 368 situations

**MISURATO.** Twelve built-in themes × the clock swept in **five-minute steps** through the real
`SunPositionCalculator` (288 clock positions per theme) × three weathers (clear / live rain at
0.6 intensity and 0.9 cover / thunderstorm) × **thirteen heights** down the stretch a drop crosses
with sky behind it — `CloudBand.precipitationOriginY`, the band's own middle where a drop is born,
down to `SceneSpace.HILL_LAYER_TOP_FRACTION`, the top of the hills.

The metric is the **Rec. 601 luma of the stroke as it is actually composited.** Rain is painted at
alpha 190 and luma is linear in RGB, so the separation the eye is given is exactly the colour
separation scaled by 190/255. Nothing here is estimated.

| | rain vs sky | snow vs sky |
|---|---|---|
| **worst** | **0.00** | **19.13** |
| p10 | 0.82 | 61.46 |
| median | 28.62 | 112.76 |
| max | 58.14 | 156.81 |

**Where it collapses, per theme** — the worst luma separation each theme reaches anywhere in the
day, rain against sky, with the situation that produces it:

| theme | worst | where | sky | drop | dE there |
|---|---|---|---|---|---|
| easter | **0.00** | 06:35, live rain, 0.54 down | `#878F96` | `#6A96BE` | 20.95 |
| spring | 0.00 | 07:30, live rain | `#94AAAD` | `#7BAEDA` | 23.45 |
| desert | 0.01 | 07:30, live rain | `#99A9A5` | `#7BAEDA` | 27.68 |
| christmas | 0.01 | 20:00, clear | `#6F81A1` | `#5F87AC` | 6.92 |
| tundra | 0.01 | 18:35, live rain | `#94A6AF` | `#79ACD7` | 20.35 |
| city | 0.03 | 06:50, clear | `#8C94A5` | `#6E9DC5` | 16.78 |
| autumn | 0.04 | 19:50, clear | `#888376` | `#628BB1` | 32.29 |
| winter | 0.05 | 07:24, live rain | `#95A5B1` | `#79ACD7` | 19.18 |
| sunset | 0.07 | 06:30, clear | `#6B94B4` | `#6894BB` | 4.00 |
| beach | 0.07 | 05:50, clear | `#5E8390` | `#5A81A4` | 13.03 |
| new_year | 0.14 | 07:35, clear | `#95A5D5` | `#7DB0DD` | 10.83 |
| **halloween** | **23.63** | never collides | `#6C2507` | `#3F5C78` | 63.26 |

**Your own case, measured** — Autumn, 08:00, 90 % cover, rain falling:

| how the rain got there | worst separation | after the fix |
|---|---|---|
| **Live Weather** (rain + 90 % cover from the forecast) | **5.97** | 12.85 |
| the theme's own manual rain switch | 19.38 | unchanged |

Both are recorded because the difference is the reason the sweep covers all three weathers rather
than the one you reported: **the same theme at the same hour fails or does not fail depending on
which path put the rain there.** Live Weather also weathers the sky through
`StormAtmosphere.dimSky`, and that is what brings the two together. If what you were looking at was
the manual switch rather than Live Weather, tell me — the measurement and your eye would then
disagree, and that is worth chasing rather than papering over.

**Eleven of the twelve.** Halloween is the exception and it is not a virtue: the horror sky is
near-black over a hard orange, so a mid-blue rain cannot land on its brightness.

**The worst case, in full**: **Easter, 06:35, live rain**, at 0.54 down the screen. Sky `#878F96`,
drop `#6A96BE`. Luma separation **0.00**. CIELab dE **20.95**.

### The finding that matters more than the number

**DEDOTTO, and it changes how this project measures a hairline.** At that worst case the two colours
are dE 20.95 apart — by the metric that settled the dolphin's visibility in v4.26, that is "clearly
different". The phone says the rain is not there, and the phone is right. **A raindrop is a 1.19 px
stroke at 720×1440.** Chromatic acuity collapses at that width; luminance is what is left.

So dE is the correct metric for a 40 px animal and the wrong one for a hairline. The project already
had the right answer in the other place it draws one: `WATERLINE_MIN_LUMA_GAP` is stated in Rec. 601
luma. This is now written as a rule in `DESIGN_NOTES.md` §17 rather than as two coincidences.

### The remedy, derived

**MISURATO.** `PaperRenderer.PRECIPITATION_MIN_LUMA_GAP = 13.47`, by the v4.22 rule with **both arms
measured on the same sweep**:

- **floor** — the failing case: **0.00**;
- **signal** — the same drop, at the same width and the same alpha, over the background your own
  report names as where the rain becomes visible, the hills: median **26.94**;
- **gate** — the midpoint, **13.47**.

`drawPrecipitation` carries the theme's colour toward white or black until it clears the sky by that
much **and no further**. A theme already clear gets no correction and is drawn **bit-identically to
v4.26**, which is what keeps the golden set from moving on scenes that had no defect.

**What it costs, measured over the same 10 368 situations:**

- situations corrected at all: **2 259 of 10 368, 21.8 %**;
- the worst case goes `#6A96BE` → `#85A9C9` — the same pale blue, lifted;
- the largest carry anywhere: CIELab **16.19**, New Year at 08:40, `#7EB2DF` drawn `#6189AB`.

`immagini_report/pioggia_prima_dopo_due_casi.png` shows both: the worst case before and after, and the largest
carry before and after.

### One colour for the whole fall, and why there is no choice

**DEDOTTO, and it is a proof rather than a preference.** The obvious design is to correct each drop
against the sky at its own height. That design cannot exist. The sky's luma is monotone down the
fall and the drop's does not change, so whenever the two are close the sky **crosses** the drop
somewhere inside the fall: above the crossing the drop is the brighter of the two, below it the
darker. A correction clearing the gap at every height would therefore have to sit above the sky at
one end of the fall and below it at the other, and those two branches never meet — any such function
has a step in it. A step is a frame carrying pale rain above one line and dark rain below it, which
is a worse artefact than the one being fixed.

I built the per-height version first and measured exactly that, which is why this paragraph exists.

**What one colour costs instead, stated exactly.** The direction is priced — how far each carry has
to go — and the cheaper wins, so it flips when the sky's own band crosses the drop's brightness.
**MISURATO: twice a day, in every theme, at the two ends of the day.** Sunset 06:09→06:15 takes the
drawn rain from `#81A2C0` to `#4D6D8B`, and 19:39→19:45 takes it back. It is the same impossibility
as the paragraph above, moved from space into time, so it is bounded rather than removed:
`PrecipitationContrastTest` fails if any theme ever reaches a third flip in a day, because a third
would mean the derivation had stopped being monotone in the hour.

### Snow — measured, and left alone with the number

**MISURATO.** Over the same sweep, snow's worst separation from the sky is **19.13** — Easter at
**07:39**, clear — above the 13.47 gate, so **the correction resolves to zero for every snow situation that
exists**. `PrecipitationContrastTest` asserts that it never fires, so if a theme's sky is ever
retuned into snow's range the suite says so rather than the phone.

Snow against the **cloud it is born in** is a different measurement with a different answer: white
on white, **0.00**, median 91.38. Snow on snow-laden hills is the same shape — Christmas 3.08,
Tundra 3.78, Winter 5.77. **Not fixed here**, and recorded as `BACKLOG_v4_27.md` item 75: a flake
leaving a white cloud is what the fade-in over the first tenth of the fall already exists for, and a
flake on a white mountain may be the scene being right. Both are looks, so both need your eye.

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

**The v4.26 lesson applied at the moment of the fix rather than after it.** `rain-worst-sky` draws
Easter at 06:35 under live rain, with a focus rectangle over the band of open sky the rain crosses
(y 250–470 of the 360×800 frame). `PrecipitationContrastTest` asserts that this is still the worst
case the twelve themes can paint, so the frame and the derivation cannot drift apart without the
suite saying so.

---

## 3. A defect found on the way — the horror sky never recorded the sky it drew

**OSSERVATO, and it is a v4.26 defect rather than something this pass introduced.**

`drawSky` writes `skyTopColorNow` and `skyHorizonColorNow` — the two fields that mean "the sky this
frame drew" — **after** the horror-sky branch, and that branch returns. So with the horror sky on,
both held whatever the previous frame left, and **zero on the first frame**, which is transparent
black.

Three things read them: the water's mirror (`lakePaint.color` carried toward the horizon by
`LAKE_MIRROR_SKY_SHARE`), the struck waterline, and now the rain. The horror sky is a **user switch,
independent of the theme** (`DESIGN_NOTES.md` §16 — "presetting is not coupling"), so this is
reachable in **any** theme. Halloween's defaults leave the lake off, which is why it had not been
met.

Fixed by recording both colours before the return. `PrecipitationContrastTest` reads the source back
and fails if the assignment leaves that branch again.

---

## 4. The hole in the acceptance sheet

**OSSERVATO.** v4.26's bird sheets did show both directions, and they say how in their own headers:
*"i candidati dispari volano verso sinistra: specchio di sola cattura"*. The patch was removed for
the release. So `DESIGN_NOTES.md` §14 item 2 was satisfied by a picture of something the app cannot
draw, and the bird's real, single facing was never the subject of a judgement at all.

§14 item 2 is **replaced**, not annotated (14.10). It now says that a direction belongs in the
judging image **when the shipped build can produce it**; that where a direction is unreachable
without scaffolding, the thing to report is that it is unreachable, which is a fact about the build
worth knowing; and that a capture patch may enlarge, slow down or place a sprite, but may not change
what the sprite is.

---

## 5. The two decisions

### Item 66 — the `perf` build type is committed

**DICHIARATO by you; done, with the check.** `app/build.gradle.kts` declares it with a doc comment
saying what it is for, that it holds no secret, and that it is never published:
`initWith(getByName("release"))`, `applicationIdSuffix = ".debug"`, `isDebuggable = false`,
`signingConfig = signingConfigs.getByName("debug")`.

`BuildTypeDeclarationTest` is the check the proposal called for and it asserts four things: the
declared set of build types is exactly `release`, `debug`, `perf`, read out of the `buildTypes {}`
block rather than from a list somebody maintains; `perf` carries each of the four properties above;
no workflow names `assemblePerf`, `bundlePerf`, `installPerf` or a `perf` test task; and some
workflow still builds `assembleRelease`.

**Documents.** `CLAUDE.md` did **not** state the old rule — I checked before editing, and this is
worth telling you because you thought it did. The rule *"TEMPORARY … Never committed, never in the
ZIP"* lived only in the comment inside the block, and the block was deleted in v4.26, so the rule
had no home in the tree at all. `CLAUDE.md` §4 and §5 now carry the new rule instead: build it,
measure on it, name it beside the number, and do not delete it at the end of a session.
`ARCHITECTURE.md` §8 gains a build-type table.

### The APK comparison, in full

**MISURATO.** `assembleRelease` built twice from a `clean` tree at the same `versionCode 58` /
`versionName "4.27"`, once with the `perf` block present and once with it removed, nothing else
changed:

```
with perf:     5e3cd0a63920fdb06aed3e7f119371590444db9886a6638270e5f7af29776c57   2 887 767 bytes
without perf:  5e3cd0a63920fdb06aed3e7f119371590444db9886a6638270e5f7af29776c57   2 887 767 bytes
```

**The same SHA-256.** 362 entries each, no entry present in one and absent from the other, and zero
entries differing in size or CRC. R8 was served from the Gradle build cache in both runs, which is
itself part of the evidence rather than a caveat: a cache key is a hash of the task's inputs, so two
runs hitting the same entry is a statement that the inputs were identical.

**And CI's work does not grow either** — checked rather than assumed, because a third build type
normally adds a unit-test variant to the `test` lifecycle task. On this project it does not:
`./gradlew test --dry-run` resolves to **`testDebugUnitTest` alone**, and `lint` to `lintDebug`.

### Item 71 — the PowerVR re-capture is ratified

**DICHIARATO by you; written down.** The re-capture is accepted: the artwork forced it, no other
outcome was possible, and no tolerance was moved.

What it costs is now a standing condition rather than a paragraph in a closed backlog —
`BACKLOG_v4_27.md` item 78. `GlDriverGapGuardTest` measures the gap between the running driver and
the committed frames, so on this device it now reads approximately zero: it proves this device still
agrees with itself. The **cross-driver** gap — 1.18 / 1.07 / 0.92 % when characterised, 1.2–1.4 %
when v4.19 re-measured it — **is measured nowhere.** One thing closes it: run the instrumented suite
on a second device with a different GPU vendor and re-derive `GlGolden.EdgeDisplacement`'s
characterisation from that run. Per-driver golden sets stay rejected (v4.20).

It is written down because the guard still passes, so nothing will ever fail to remind anybody that
it has stopped measuring what it was built to measure.

---

## 6. Verification

### The template (`AI_PROJECT_RULES.md` 12.14)

```
Release identifier:              v4.27  (versionCode 58, versionName "4.27")
Verification level:              3
Reason for the level:            build configuration changed (a fourth build type), the renderer's
                                 draw path changed, goldens moved, and this is a release candidate
Tests run:                       yes -- 1365 JVM unit tests, 0 failures, 0 errors, 0 skipped
                                 (clean tree, --no-build-cache), and the **whole** instrumented
                                 suite once on the device: **158 tests, OK, 2 501.9 s**
Lint run:                        yes -- lintDebug: 0 errors, 28 warnings, 3 hints (unchanged from
                                 v4.25 and v4.26)
APK build run:                   yes -- assembleDebug, assembleDebugAndroidTest, and assembleRelease
                                 twice for the item-66 comparison
Static / bytecode checks:        Kotlin compiler warnings: 21, all pre-existing -- counted on a
                                 full compile of the extracted copy, not on a cached one. The two
                                 this pass first introduced (the `File?` platform-type shape copied
                                 from LakeContrastTest's repoRoot) were removed rather than inherited
Mutation testing:                yes -- eight mutations, all caught (6.3)
ZIP verification:                yes -- all eight steps of 12.18, results below
Clean build from extracted ZIP:  yes -- assembleDebug, testDebugUnitTest and lintDebug, all
                                 green, --no-build-cache
Maintainer-side verification required: publication (tag, push, GitHub Release); the judgement on
                                 whether the bird should be redrawn for legibility at 51 px;
                                 battery, thermal and tactile behaviour, which are never
                                 Claude-verified
Release identifier verified unique: yes -- read from the public API, not from a document: neither
                                 /releases nor /tags carries `v4.27`, and the highest of both is
                                 `v4.26`. There is no `.git` in this working tree (the ZIP is the
                                 delivery mechanism), so `git tag --list` was not available and the
                                 API is the whole check
```

### The counts, recomputed rather than quoted

```
JVM @Test                     1365
instrumented @Test             158
shipped sprites                266   (registry OK: 140 with an SVG source, 126 recorded gaps)
Canvas golden assertions        31   (29 before this pass)
GL references                    3   (unchanged, PowerVR, untouched)
committed golden PNGs           31
```

### The goldens

**Attribution before regenerating anything** — every committed Canvas frame re-rendered and compared
against its committed PNG, per pixel, on the device:

| scene | pixels differing by more than one level | by more than the golden's own tolerance of 8 |
|---|---|---|
| `rain` | 2 073 | **1 186 (0.41 %)**, largest per-channel move **7** |
| every other committed scene | **0** | **0** |

**One scene changed, and every other frame is bit-identical.** That is the direct evidence for the
claim that a theme already clear of its sky is drawn exactly as v4.26 drew it: `day`, `dusk`,
`night`, `overcast`, `snow`, `thunderstorm`, `theme-city`, the four lake frames, the two traffic
frames and `halloween-moon` all re-rendered byte for byte.

**And the `rain` golden could not see the fix** — nor could it have seen the defect. Its largest
per-channel move is 7 and `SceneGolden.CHANNEL_TOLERANCE` is 8, so by the golden's own rule *zero*
pixels differ and the test passes either way. This is `BACKLOG_v4_26.md` item 69 happening again in
a new place, and it is why the new frame is pointed at the measured worst case rather than at a
convenient one. `rain` was regenerated anyway: a committed frame that is 2 073 pixels away from what
the build draws is a stale baseline for whatever changes next.

**The new frames**, and what they are worth:

| golden | whole-frame | focus |
|---|---|---|
| `rain-worst-sky` | 360×800 | y 250–470, full width, **derived limit 0.14 %** |
| `bird-facing` | 360×800 | x 225–292, y 268–302, the shared 2 % |

The rain focus carries a **derived** limit because the shared one cannot see this: the patch is
79 200 pixels, where 2 % is 1 584 and the whole regression is 223. Placed by the v4.22 rule between
the measured noise floor — **0.0000 %**, the frame re-captures byte-identical — and the measured
weakest regression that must fail — **0.2816 %**, the correction removed entirely. Both numbers are
written at the declaration. Without it the whole-frame check would be the only thing standing, and
it caught that regression by **582 pixels against a budget of 576**.

**Double regeneration, on the changed scenes only** (`BACKLOG_v4_26.md` item 64, the maintainer's
own decision): `rain`, `rain-worst-sky` and `bird-facing` captured a second time and compared byte
for byte against the committed files — **identical**.

**No tolerance was moved and no number was lowered.** `CHANNEL_TOLERANCE`, `MAX_DIFFERING_FRACTION`
and `MAX_FOCUS_DIFFERING_FRACTION` are what they were; the one new limit is *below* the shared one,
which is the only direction a derived focus limit may go.

**The gates measure zero.** `SettingsGateScenesTest` runs green, so the four derived gate rectangles
are unmoved.

**The GL references are untouched.** `gl-day`, `gl-lake-busy` and `gl-thunderstorm` were predicted
unchanged before the run — none of their scenes draws corrected rain, and the thunderstorm's sky is
dimmed so far below the drop's own luma that the correction resolves to zero — and the run confirms
it: `GlSceneGoldenTest`, `GlDriverGapGuardTest` and `GlGoldenMetricTest`, **12 tests, green, in
5.3 s**, with the PowerVR frames as committed in v4.26.

### Filtered rounds, and one full run

On the maintainer's instruction this pass used `am instrument -e class` for every intermediate
golden round — attribution, two regenerations, re-verification, the five Canvas golden classes, the
three GL classes, and four mutations — and ran the whole instrumented suite once, at the end.

The numbers: two named tests, **under a minute**; the five Canvas golden classes, **7 m 01 s**; the
three GL classes, **5.3 s**; the whole suite, **41 m 42 s** for 158 tests. Ten filtered rounds cost
about twenty minutes in total. Unfiltered they would have cost seven hours, and the mutation testing
that produced the derived focus limit would not have been affordable at all — which is the part that
matters, because that limit is measured rather than chosen precisely because measuring it was
cheap.

### Mutation testing

**MISURATO.** Three mutations, each applied to the tree, the suite run, the tree restored:

| mutation | caught |
|---|---|
| `PRECIPITATION_MIN_LUMA_GAP` 13.47 → 11.00 | yes — the derivation test fails: the gate is no longer the midpoint between the measured floor and the measured signal |
| the derivation removed from the draw path (`drawnColour = themeColour`) | yes — the source read-back fails |
| the horror branch stops recording the sky | yes — the source read-back fails |
| `perf` loses its `.debug` application id suffix | yes — `BuildTypeDeclarationTest` |
| a fourth build type is declared | yes — `BuildTypeDeclarationTest`, `[release, debug, smoke, perf]` |
| a workflow builds `assemblePerf` | yes — `BuildTypeDeclarationTest` |
| the rain correction removed from the draw path | yes — `rain-worst-sky`, 582 px whole-frame, 223 px in the focus against a 111 px limit |
| a horizontal mirror added to `drawBirds` | yes — `bird-facing`, 1 114 px (0.387 %) against 0.200 % |

The restored tree runs green.

**One of these nearly went uncaught, and the reason is worth recording.** The three
`BuildTypeDeclarationTest` mutations first appeared to be caught by nothing. They were not being
run: everything that test reads — `app/build.gradle.kts` and the workflow files — is **invisible to
Gradle's up-to-date check for `testDebugUnitTest`**, whose inputs are the compiled classes. Editing
a build script leaves the task `UP-TO-DATE` and the suite reports success without executing a line
of it. Re-run with `--rerun-tasks` and all three fail as they should. CI is unaffected — a fresh
checkout has no previous outcome to reuse — so this is written into the test's own doc as a note for
whoever next changes a build script here.

### Where the time went

**OSSERVATO**, from the timestamps of the commands themselves rather than from an impression. The
pass ran from **11:20** to **13:45**, so **2 h 25 m** of wall clock.

| | |
|---|---|
| machine time, total | ≈ **1 h 50 m**, of which |
| — the instrumented suite, whole, once | **41 m 42 s** (2 501.9 s, 158 tests) |
| — device rounds, filtered (`-e class`) | ≈ 20 min over ten rounds: attribution, two captures, verification, the five golden classes, the GL classes, four mutations |
| — `assembleRelease` ×4 for the item-66 comparison | ≈ 28 min, **half of it wasted** — see below |
| — JVM builds and test runs (about fifteen, mostly filtered) | ≈ 14 min |
| — the clean-extraction build, twice | ≈ 8 min |
| **overlapped** with writing (the suite and the release builds ran in the background) | ≈ **1 h 10 m** |
| **actually blocked, waiting on the machine** | ≈ **40 m** |
| real work — reading, measuring, deciding, writing | ≈ **1 h 45 m** |
| of which the whole rain measurement, on the host | **seconds per run.** 10 368 situations, four design iterations, phone switched off |

**Which of the three instructions bit.**

- **"Measure on the host, confirm on the device" — bit hardest, and it changed the answer, not just
  the schedule.** The rain design went through **four** versions: per-height correction; per-frame
  direction with per-height magnitude; the direction priced instead of taken from the midpoint; and
  finally one colour for the whole fall. Each iteration was a JVM test over 10 368 situations that
  ran in seconds, and iteration two is the one that *found the impossibility proof* — I could see
  the two-tone frame in the numbers. On the device each of those would have been a build, an
  install, a capture and a look; I would have shipped version one or two.
- **"Filter the intermediate rounds" — bit, and by a large factor.** Ten device rounds were needed
  before the final one. Unfiltered that is ten × forty minutes; filtered it was about twenty minutes
  in total, with the whole suite run once at the end. It also made mutation testing on a *golden*
  affordable at all, which is how the derived focus limit got measured instead of guessed.
- **"Write while the machine works" — bit, but it is the one where I lost time, and to my own
  mistake rather than to the method.** The backlog, the release note, the ROADMAP, the README, the
  ARCHITECTURE sections and most of this report were written under the release builds and the suite.
  What went wrong: the first `assembleRelease` pair was launched as a background job that snapshots
  `app/build.gradle.kts` and restores it at the end, and while it ran I edited that same file to
  bump the version. The two APKs came out at different versions, the comparison was worthless, and
  the pair had to be run again — **≈ 14 minutes lost**. The lesson is not "do not write while it
  builds": it is that a background job which owns a file makes that file untouchable, and the two
  things I was doing in parallel both wanted the same one.

**The fixed cost of a release, separately.** Of the machine time above, the part that is this
release's *content* is the golden work and the suite. The part that is the *shape* of a release —
clean build, clean-extraction rebuild, lint, ZIP verification — is about **10 minutes** and does not
grow with the size of the change. The forty-minute suite is the fixed cost that dominates, and it is
fixed only because it is the whole suite: the maintainer's second instruction is what turned every
*other* device round from forty minutes into one.

### The ZIP, step by step (12.18)

**MISURATO.** `PaperScrape_v4_27.zip`, **1 444 files**, in `/home/bober/claude-shit/consegna_v4_27/`.
Built with Python's `zipfile` and the mode written into `external_attr` by hand, because `zip` is not
installed here and the wrapper loses its execute bit otherwise. **An archive cannot contain its own
checksum or its own size**, so both are in `SHA256SUMS.txt` beside it and in the delivery message,
not in this file.

| step | result |
|---|---|
| 1. build the archive | **1 444 files** — v4.26 had 1 437, and the seven new ones are `BACKLOG_v4_27.md`, `release-notes/v4.27.md`, `release-verification/V4_27_REPORT.md`, two tests and two goldens |
| 2. extract into a clean directory | done, sharing nothing with the working tree |
| 3. completeness, file by file | **identical file sets, 1 444 files**, compared with `find -type f` on both sides — not `git ls-files`, which an extraction has no `.git` to answer |
| 4. `.gitignore`, `.github/`, `CLAUDE.md`, `AI_PROJECT_RULES.md` present | all present, plus `gradlew` (mode `-rwxr-xr-x` preserved), `gradlew.bat`, `debug.keystore`, both workflows, the wrapper jar and `release-notes/v4.27.md` |
| 5. `.git/`, `build/`, `.gradle/`, `local.properties` absent | all absent, and so are `app/build/`, `.kotlin/`, every `__pycache__`, every `.pyc`, and any APK or log |
| 6. scan for secrets | clean. The only credentials in the tree are the debug keystore's deliberately public `"android"` alias and password |
| 7. build **from the extracted copy** | `assembleDebug` **BUILD SUCCESSFUL**, 22 767 778-byte APK. No duration is quoted: it varies by seconds between runs of the same build and would be the one number here that a rebuild of this archive could falsify |
| 8. run the tests **from the extracted copy** | **1 365 tests, 0 failures, 0 errors**; `lintDebug` 0 errors, 28 warnings, 3 hints; **21 Kotlin compiler warnings on a full, uncached compile** |

**`CLAUDE.md` is still untracked**: the extraction was `git init`-ed and `git check-ignore -v
CLAUDE.md` answers `.gitignore:44`, with the file absent from `git ls-files` while `debug.keystore`
is present in it.

Alongside the archive in the same directory: **`immagini_report/`**, the pictures this report refers
to, and **`v4.27.md`**, the release note the CI would publish as the Release body.

### The device

The BV6600 is left with this build installed and the debug wallpaper running, as instructed. No
restore captures were taken, nothing was uninstalled, and the phone was not reset. The **release**
build of v4.25 that was on it before this pass is still installed too, untouched.

### The images

All in `immagini_report/`, and every one of them is either a frame from the device or a render of
colours read out of the tree — none is a drawing made for the report.

| file | what it is |
|---|---|
| `uccello_A_B_dal_telefono.png` | the bird as the running build draws it, from four screenshots of the phone, with the same pixels mirrored beside it. **This is the one to look at** |
| `uccello_sul_telefono_4_istanti.png` | the four instants the travel direction was measured from |
| `uccello_sprite_e_specchio.png` | the shipped sprite at true size and enlarged, with the flight direction marked |
| `pioggia_caso_peggiore.png` | the measured worst sky, rain before and after |
| `pioggia_prima_dopo_due_casi.png` | the same, plus the largest colour carry the correction ever makes |
| `golden_rain_worst_sky.png` | the new golden, with its focus rectangle drawn on |
| `golden_bird_facing.png`, `..._x6.png` | the new bird golden, whole and enlarged |

---

## 7. What is not done, and what is yours

- **Publication.** No push, no tag, no `gh release`, no credential touched. `git push origin main`
  would fail with `could not read Username`, and that failure is the system working.
- **Whether the bird is redrawn.** §1: the animal is correct and the question is legibility at
  51 px. That is a look, and looks are yours.
- **Snow against its own cloud** (`BACKLOG_v4_27.md` item 75) — measured, recorded, not fixed.
- **The cross-driver GL gap** (item 78) — a second GPU vendor, and nothing else.
- **Item 67** and everything the four older backlogs leave open.
