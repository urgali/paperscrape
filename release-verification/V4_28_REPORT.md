# V4_28_REPORT.md — a bird that reads, an umbrella in the rain, and a sea that moves

**Release prepared, not published.** `versionCode = 59`, `versionName = "4.28"`, prepared
2026-09-11. No tag, no push, no GitHub Release: that half is the maintainer's.

**Baseline: v4.27, and it is published.** Read from the public GitHub API on 2026-09-11 —
published 2026-09-10T16:54:58Z, `draft: false`, `prerelease: false`, with `PaperScrape-v4.27.apk`
and its `.sha256` attached. `ROADMAP.md` said otherwise; §6 is about that.

**Base ZIP**: `PaperScrape_v4_27.zip`,
SHA-256 `ae79c87fbf2a814e32bf45630ced8c6b3fa51b828f769e1c54de12ef9030ab8b`, 1 444 files — verified by
`sha256sum` before extracting, not assumed.

**Device**: Blackview BV6600 (MediaTek Helio A25, PowerVR GE8320, Android 10, 720×1440).
**Where each number was taken**: everything about colour is arithmetic over each theme's own numbers
and was measured **on the host** in a JVM test that sweeps every combination in seconds. The device
carried the memory measurement, the worst-case look and the instrumented suite.

**Labels used throughout**: **MEASURED** = a number with the command or the calculation that
produced it; **OBSERVED** = seen on the device or in a file; **DEDUCED** = reasoning not verified.

---

## 0. The three things to read first

1. **The condition on the ceiling raise was discharged, and the umbrella stands.** The A/B memory
   measurement moved by **−42 KB of PSS against a pooled standard deviation of 465 KB (0.09σ)**, and
   GL texture residency was **identical to the byte**. §4 has the protocol, the twelve samples, and
   an honest statement of what the measurement does *not* exercise.
2. **The wave's gates were left where the maintainer set them (24.9 / 34.9), and what changed is
   which end of the derivation the renderer aims at.** Re-deriving the floor with the shipped shape
   gives a much larger number on two of the three lake themes — **33.40 against a gate of 24.90** —
   and changing the day gate to match would alter every daytime frame already approved from the
   phase-3 photographs. That is `BACKLOG_v4_28.md` item 83, measured and pinned, not silently
   adopted. §3.
3. **`ROADMAP.md` denied a release that had been out for a day.** Third occurrence of the same
   failure. §6.

---

## 1. The bird — a silhouette defect, and why v4.27 was right and the report was too

**OBSERVED, from the v4.27 work**: the sprite carries its head at the leading end, `drawBirds`
applies only a vertical mirror, every bird drifts +x. Mirroring the sprite would have *created* the
defect it was meant to cure. That conclusion stands and nothing in v4.28 contradicts it.

**What it did not explain** is why the maintainer saw the birds fly backwards. v4.28's answer: at
the **42 px** a bird reaches, the largest shape in "Colomba" was the pair of raised wings, which
rise up and *forward* — and a pair of forward humps is also what a fanned tail looks like. The eye
takes the largest shape and fills in the rest, so the animal read tail-first; the head, a 3.6 px
disc drawn continuous with the body, was too small to argue with it.

**B1 "Rondine"** moves the weight behind the head: a deep forked tail, sickle wings swept back, a
small head and a short beak. **Nothing is mirrored.**

| | |
|---|---|
| canvas | 51 × 21, unchanged (MEASURED: `bird_body.png` header) |
| blit origin | `(-25, -15)`, unchanged |
| flap axis | canvas row 15, unchanged — the wing-beat is a mirror about it |
| decoded cost | **0 B** — same canvas, so the budget does not move for the bird at all |
| provenance | `sources/svg/bird_body.svg` replaced; the staged render is **byte-identical** to the concept PNG the maintainer approved (MEASURED: `sha256sum` on both, `26039ca9e1eed45b…`) |

The `bird-facing` golden was re-authored — it exists for exactly this — and `DESIGN_NOTES.md` §2
now carries the rule: **a silhouette defect and a facing defect present identically, and only one of
them is fixed by mirroring.**

---

## 2. The umbrella — one limb, and a rule borrowed from the cars

**Pose P1 "Alzato" with canopy U1 "Alta".** Near arm bent, hand at cheek height, **still on all
three walk frames** while the far arm keeps swinging. Adults only; about two in three of them.

**The proof that this is the shipped drawing and not a second family.** OBSERVED, and it runs on
every invocation of the generator: `build_carry_sprites.py` re-renders the shipped man's three
summer walk frames through the same code, the same trim and the same rasteriser, and compares them
**byte for byte** with `app/src/main/res/drawable-nodpi/`:

```
  reproduces shipped person_man_summer_walk0: byte-identical
  reproduces shipped person_man_summer_walk1: byte-identical
  reproduces shipped person_man_summer_walk2: byte-identical
```

If that ever stops passing, the pose has stopped being a variation of the real walker.

**What ships**: 36 PNGs — 2 families × 2 seasons × 3 frames × 3 tones — MEASURED at **261 695 B on
disk, 4 245 696 B decoded**. No un-toned base: the call site indexes by tone only, and shipping one
would be 1 415 232 decoded bytes nothing blits. The base is pixel-identical to the tone matching
each family's own skin colour (MEASURED: man → `_skin1`, woman → `_skin0`, all twelve frames), which
is the `retiredBases` pattern the registry already has for the vehicle busts, and it is declared
there.

**Rain only.** The gate is the renderer's single rain predicate — the one `drawPrecipitation` paints
rain on — so snow is excluded by construction rather than by a second rule that could drift from it.

**The handle is a rectangle drawn in code** from the hand to a point above the head, with only the
canopy as artwork. The canopy is tintable, so one drawing serves five colours; and the pose will
carry a bag or a case later with no new person artwork.

**Who carries changes only off screen.** `CarSelection.offScreen`'s own doc says the rule was
deliberately *not* extended to pedestrians — *"a pedestrian materialising mid-pavement is
forgiven"* — and it is right about a pedestrian appearing at the frame edge. It is not right about
an object appearing in the hand of a figure already walking. v4.28 takes the rule over for this one
property, using the draw pass's **own** cull rather than a second copy of the geometry, so the
moment a change is permitted is exactly a moment nothing was drawn.

**MEASURED, and this is the property that decides whether the feature works at all**: the scene
tiles every `tileWidth` = twice the screen width, so a walker has a stretch of its loop with no copy
visible. `PedestrianCarryTest` walks the loop through the real cull helpers and asserts that more
than a quarter of it is off screen — if it were never off screen the rule would be a deadlock and no
umbrella would ever go up. **What it costs is the delay**: a walker on screen when the rain starts
finishes bare-headed, up to about 75 s. That is the right way round.

---

## 3. The wave — WA3 "Tubo", and a colour derived for the third time

Two tintable masks per wave — a **body** (the face under the curling lip) and the **foam** thrown
forward off it — three slots, drifting +x, **rain and thunderstorm only**. With a clear sky no wave
is gathered at all and the lake pass is bit-identical to v4.27's.

### 3.1 The derivation, reproduced rather than quoted

`WaveContrastTest` sweeps **2 592 situations** — the three themes that draw a lake by default × 288
five-minute steps × three weathers. MEASURED, printed by the test:

```
WAVE floor 9.80 at beach 07:24 theme-rain | signal body 40.00 foam 60.00 at beach 12:00 theme-rain
     (surface luma 186.65) | gates body 24.90 foam 34.90
```

- **floor 9.80** — how much the mirror's own gradient already varies over one wave's height, at its
  worst. Below that a body cannot be told from the gradient under it.
- **signal 40 / 60** — the gaps of the frame the maintainer read as a wave.
- **gates 24.9 / 34.9** — halfway, which is where the v4.22 rule puts every gate.

### 3.2 One direction does not hold, and the measurement says so

```
WAVE darkest surface luma 44.91 at beach 00:00 thunderstorm | black-only body under 40 luma in
     901 of 2592 situations | most direction flips per day 2 (beach theme-rain)
```

Carrying the body always toward black — the obvious rule, and the one the proposals used — puts it
under 40 of luma in **901 of the 2 592**, every one at night: a dark body on dark water is a rock,
not a wave. The body therefore goes to the **cheaper side**, toward black above 127.5 of luma and
toward white below, exactly as v4.27's rain remedy does. The foam is always the lighter paper by at
least its own gate, so the two never trade places, and the direction changes **at most twice a day**
on any theme.

### 3.3 The change the maintainer asked for: a gate is a floor, not a target

**How the number was chosen, which is the part that was asked for.** Nothing new was invented. The
derivation has two measured ends — the **floor**, below which nothing can be told apart, and the
**signal**, the gap that was actually read as a wave — and the gate sits halfway. The proposals
aimed the renderer at the gate *everywhere*, which is aiming at the minimum. The photographs say
that by day, low sun, rain and storm the minimum reads, and at night it comes out "discreet".

So the night target is **the signal itself** — 40 for the body, 60 for the foam — and the day target
stays at the gate the photographs approved. The crossfade is `dayBlend`, the same value the scene's
colours, the car count and the pedestrian count already cross-fade on, so the sea firms up over the
length of dusk instead of stepping between two frames. The floor still holds everywhere: the target
is never below the gate at any `dayBlend`, because the gate is one end of the interpolation, and the
test asserts that at 101 points along it.

Concretely, at the darkest surface the sweep finds (44.91): the body goes to **84.91** instead of
**69.81**, and the foam to **144.91** instead of **104.71**.

### 3.4 The Tundra case, looked at rather than reasoned about

```
WAVE inverted papers in 139 of 2592 situations, themes [tundra], palest surface 233.93
     at tundra 07:45 theme-rain
```

Where the surface passes `255 − foamGap` there is no white left, so the papers invert: the foam goes
dark and the body darker still, which is the only arrangement that keeps both gaps **and** their
order. MEASURED: **139 situations**, up from the 136 the flat gates gave — the night target widened
it by 3, because a larger foam gap runs out of white slightly sooner. All 139 are Tundra, whose
water is ice (`#BFE3EE`), and the test fails if it ever spreads to a second theme.

*(The device look at the palest case is §5.)*

### 3.5 Depth order

The proposals drew every wave before every boat and every dolphin, so a breaker crossing the near
edge was cut off behind a hull plainly further away — the sail-and-dolphin defect of v3.1 in a new
pair. The waves now enter `LakeLanes.orderByDepth` in the same slots.

**The difficulty is the key**, and it is worth stating because it is the whole of the fix: the three
categories do not share a reference point. A sailboat's key is its *placement point* and
`drawSailboat` hangs the hull 8 units below it and 17 tall, so its waterline is **25 boat units under
its key**; a dolphin's key is its lane; a wave's base *is* its waterline. Keyed by its bare base a
wave was compared against a boat's placement point — and the first burst of phase-3 frames showed
exactly that, a wave cutting the sail of a boat whose hull was obviously nearer. The wave's key is
its base lifted by `SAILBOAT_HULL_WATERLINE_UNITS`, so wave and hull meet waterline to waterline.

All three properties `LakeLanesTest` fixes survive **by construction**: boats are untouched, the lift
is never negative so nothing is pulled forward, and one key still orders everything. Wave against
dolphin remains off by ~16 px — `BACKLOG_v4_28.md` item 84, with the one-function fix and why it is
not this release's work.

---

## 4. The ceiling, and the condition attached to it

### 4.1 Why it had to move

MEASURED on the shipped v4.27 tree, by reading the PNG headers:

| | bytes |
|---|---|
| shipped set, decoded | **28 619 568** |
| 29 MiB ceiling | 30 408 704 |
| **margin** | **1 789 136** |
| carrying pose (36 × 117 × 252 × 4) | 4 245 696 |
| wave (2 × 360 × 132 × 4) | 380 160 |
| canopy (144 × 72 × 4) | 41 472 |
| bird | **0** — same canvas |
| **wanted** | **4 667 328** |

**The space was looked for first**, as the v4.20 paragraph in the budget's KDoc demands, and there is
none: `SpriteReachabilityTest` now fails any shipped PNG no source file names, **in both
directions**, so the hunt v4.20 did by hand is a standing check and the set carries no dead weight.

**No useful reduced coverage fits** (MEASURED):

| coverage | total wanted | vs the 1 789 136 margin |
|---|---|---|
| full, 36 frames | 4 667 328 | over by 2 878 192 |
| one season, 18 | 2 544 480 | over by 755 344 |
| one tone, 12 | 1 836 864 | **over by 47 728** — less than half a sprite |
| one season *and* one tone, 6 | 1 129 248 | fits, by 659 888 |

The only thing that fits under the old ceiling is a **summer-only umbrella on a single skin tone**,
in a street of walkers drawn in three — a regression of exactly the axis v4.1 raised this ceiling to
buy. The item is whole or refused.

**The set now lands at 33 286 896 B = 31.745 MiB**, and 32 MiB leaves **267 536 B** — the same "just
above the measured figure" every raise in that KDoc has used.

### 4.2 The A/B, which is the condition

**Protocol.** Both sides built as the committed release-like `perf` build type (`initWith(release)`,
R8, `isDebuggable = false`), v4.27's rebuilt from its own ZIP. The live wallpaper component is the
same in both, so `adb install -r` swaps the code underneath a running wallpaper **without touching
the stored preferences** — same theme (Sunset), same settings, same component, same elapsed time
after restart. Six rounds **alternating A/B/A/B/A/B** so any drift in the device cancels, two samples
per round at t = 90 s and t = 150 s, `dumpsys meminfo`.

| round | build | t | PSS total | GL mtrack | EGL mtrack |
|---|---|---|---|---|---|
| 1 | v4.28 | 90 | 50 742 | 28 854 | 3 795 |
| 1 | v4.28 | 150 | 49 833 | 28 511 | 3 795 |
| 1 | v4.27 | 90 | 50 571 | 28 511 | 3 795 |
| 1 | v4.27 | 150 | 49 866 | 28 854 | 3 795 |
| 2 | v4.28 | 90 | 50 460 | 28 511 | 3 795 |
| 2 | v4.28 | 150 | 49 943 | 28 511 | 3 795 |
| 2 | v4.27 | 90 | 50 685 | 28 511 | 3 795 |
| 2 | v4.27 | 150 | 49 278 | 28 511 | 3 450 |
| 3 | v4.28 | 90 | 49 993 | 28 511 | 3 450 |
| 3 | v4.28 | 150 | 49 813 | 28 511 | 3 795 |
| 3 | v4.27 | 90 | 50 589 | 28 511 | 3 795 |
| 3 | v4.27 | 150 | 50 048 | 28 511 | 3 795 |

MEASURED:

| | v4.28 | v4.27 | delta |
|---|---|---|---|
| PSS total, mean (sd) | 50 130.7 KB (381.0) | 50 172.8 KB (548.5) | **−42.2 KB** |
| GL mtrack, mean (sd) | 28 568.2 KB (140.0) | 28 568.2 KB (140.0) | **0.0 KB** |

**|delta| / pooled sd = 0.09 for PSS and 0.00 for GL.** The difference is a twelfth of one standard
deviation, and GL texture residency is identical to the byte — the two builds hold the same textures.
**The condition is discharged: the measurement did not move beyond the noise, so the umbrella item
stands.**

### 4.3 What this measurement does not exercise, said plainly

The scene measured is **Sunset in clear weather**, so **none of v4.28's new sprites is decoded in
it** — no rain, therefore no umbrella; no lake on that theme, therefore no wave. The A/B answers
"did the shipped build's real memory move", which is the question v4.20's authorisation asked and
the protocol it named, and it answers it decisively. It does not measure the worst case.

Three things bound that worst case, and they are recorded rather than left to be rediscovered:

- **`SpriteCache` has no standing size cap.** OBSERVED in the code: `get()` decodes and inserts and
  never evicts; eviction happens only on an `onTrimMemory` callback or an explicit `release`. So the
  resident set grows to whatever the scenes visited have drawn.
- **On the shipped GL path the CPU copy does not stay.** `GlTextureCache` calls
  `SpriteCache.release()` once a sprite is uploaded, so the decoded-sprite ceiling lands in **GPU
  texture memory**, not in heap. That is why GL mtrack is the majority of this process (the memory
  investigation measured **55%**, with the atlas alone at **31.7%**), and it is why GL mtrack being
  identical between the two builds is the more informative half of the table above.
- **The arithmetic bound is 4 667 328 B**, reached only by a scene that is raining over a lake with
  adults on the pavement. And a walker carrying an umbrella draws the carry frame **instead of** its
  walk frame, not on top of it.

An attempt was made to drive the settings UI to turn rain on and repeat the A/B in that state. It
was abandoned after several minutes: the scene settings are a collapsible Compose tree and blind
navigation through it is the time sink `CLAUDE.md` §7 warns about. Recorded as what it is — a
measurement not taken — rather than left implied.

---

## 5. On the device

### 5.1 The golden pass — attribution before regeneration

**Sixteen committed frames moved, and every one of them is the bird.** MEASURED, by taking the
bounding box of each committed diff rather than by assuming:

| golden | pixels | bounding box |
|---|---|---|
| bird-facing | 1 633 | (16, 69) – (360, 296) |
| day, lake-busy, lake-empty, people-single | 1 448 each | (81, 61) – (197, 306) |
| dusk | 1 596 | (80, 60) – (197, 306) |
| overcast | 1 491 | (81, 61) – (197, 306) |
| rain | 1 606 | (80, 60) – (197, 306) |
| thunderstorm | 1 600 | (80, 60) – (197, 306) |
| snow, people-mixed | 951 / 961 | (116, 107) – (360, 147) |
| rain-worst-sky | 1 151 | (66, 112) – (360, 210) |
| people-commercial | 1 241 | (124, 126) – (360, 290) |
| people-overlap | 1 594 | (49, 81) – (302, 295) |
| people-skin | 839 | (3, 160) – (55, 215) |
| people-window | 972 | (213, 141) – (312, 215) |

Every box lies in the **upper 40%** of an 800 px frame — the band birds fly in. Not one diff reaches
the pavement (y ≈ 600) or the water, so **no committed frame moved because of an umbrella or a
wave**: the four that would have been the candidates (`rain`, `thunderstorm`, `rain-worst-sky`,
`snow`) changed in the bird band and nowhere else. Three identical 1 448-pixel diffs across
unrelated scenes is the same bird in the same place, which is what the attribution is for.

**Two more failed in the full suite, and both are the same bird.** The intermediate run above was
filtered on three classes, and there are **four** that assert a Canvas golden plus the GL suite —
`CLAUDE.md` named three and has been corrected to name the command instead of a list. The two:

- **`waterline-worst-theme`** (`SkyWaterGoldenTest`, the class the filter missed): 1 526 px at
  (109, 56) – (311, 139). OBSERVED in the diff: **three bird silhouettes and nothing else.** The
  waterline itself did not move, which is the point of that frame.
- **`gl-day`** (`GlSceneGoldenTest`): 1 172 of 64 516 px inside the **`sun glow`** focus rectangle,
  1.817% against a 0.500% limit. The region is (53, 33) – (307, 287) and the diff's own box is
  **(81, 60) – (198, 306) — the same box, to the pixel, as the Canvas `day` bird diff.** The glow
  did not change; the region the gate measures contains the bird's flight path. That gate exists
  because destroying the glow entirely moves no pixel by more than 15/255, which is exactly why it
  is sensitive enough to notice a 51 × 21 animal inside it.

`gl-day` was re-authored on this device, which is where the GL references have been authored since
v4.26. `gl-lake-busy` and `gl-thunderstorm` passed and were **not** touched, and no tolerance was
moved.

**Eight frames differed below the tolerance and were deliberately *not* re-committed** —
`lake-boats`, `lake-dolphin-leap`, `night`, `shops-closed-night`, `traffic-day`,
`traffic-day-sparse`, and the two GL frames that passed. `BACKLOG_v4_25.md` item 64, decided by the maintainer, says the regeneration
runs **only on the scenes whose frames changed**, and a frame that passes its own assertion has not
changed; re-committing it would bake device noise into the reference. They were restored from
v4.27's ZIP and verified byte-identical to it.

**Determinism.** The regenerated set was rendered a second time and asserted against what had been
committed: **32 of 32 Canvas assertions pass**, so the frames are reproducible and not a snapshot of
one run.

**Twenty committed frames differ from v4.27 in the end**: the 16 above, `waterline-worst-theme`,
`gl-day`, and the two new ones. Everything else is byte-identical to the base ZIP, verified by
`cmp` file by file rather than by trusting the run.

### 5.2 Two new goldens, because a feature no frame portrays is invisible to the net

This is `rain-worst-sky`'s lesson applied at the moment of the fix rather than after it, and
`BACKLOG_v4_25.md` item 69 measured what it is worth: the golden suite once went green over a scene
in which **every person had been redrawn**.

- **`wave-storm`** — Beach under a thunderstorm, boats and dolphins at full density. OBSERVED in the
  committed frame: a breaker on the near lane with its white lip curling forward, **and the hull of
  a nearer boat drawn over it**, which is the depth-order half of the item portrayed rather than
  asserted in prose.
- **`umbrella-rain`** — rain over the default theme with people at full density. OBSERVED: two
  adults carrying (one green canopy, one yellow), and the children beside them walking without one.

**Both need a warm-up, and the reason is the rule itself.** Who carries — and which wave slots are
on — is *state* that may move only while nothing is on screen, so a scene rendered cold has no
umbrellas and no waves in it whatever the weather says. Both scenes run **320 frames at 0.25 s = 80
seconds of their own clock** before the frame is taken, which is more than the ~38 s a walker takes
to cross its tile and more than the 38–55 s a wave slot takes. Both numbers are pure inputs, so the
frames are reproducible.

### 5.3 The Tundra case, looked at — and a correction to how it is reached

The maintainer asked for the inverted-papers situations to be looked at on the phone rather than
left as arithmetic. Doing so turned up a fact about **how they are reached** that the sweep's own
labels hide.

The three weather columns are storm *strengths*, not settings: "theme-rain" means strength **zero**,
i.e. the sky undimmed. MEASURED: **all 139 inverted situations are in that column, and none occurs
with the sky dimmed by a storm** — a storm darkens the surface and the inversion stops happening.
But Tundra **snows** by default, so no wave is drawn there out of the box at all. The case therefore
belongs to a user who switches Tundra's own precipitation to rain, which draws rain without the
dimming a live-weather override brings. `WaveContrastTest` now asserts the dimmed count is zero, so
if that ever changes the photographed case is no longer the worst one and the test says so.

`V428TundraCapture` renders exactly that — Tundra, 07:45, theme rain, lake raised to 0.8 as
`V426CaptureTest` already does for this theme (its own 0.25 puts the whole band behind the snow
hills; the tint does not depend on the height, so this changes what is visible and not what is
judged).

**OBSERVED, and the judgement asked for:** the inverted papers are **acceptable**. Two waves stand
on the pale ice water as a **grey body with a lighter grey lip curling forward**, spray thrown ahead
of it — the foam lighter than the body, both darker than the water, order kept, which is exactly
what `WaveTint.inverted` specifies. They read as waves and not as smudges or rocks, and the
direction is legible from the overhang. On ice water they are slate rather than white-capped, which
is the right answer for a frozen sea and is certainly better than the alternative the rule exists to
avoid: foam saturating to white and disappearing into the surface.

### 5.4 The bird, seen

OBSERVED in the Tundra frame at the size it ships: a dark swallow with **swept-back sickle wings and
a forked tail**, and the direction of travel reads immediately. That is the whole of the item — the
largest shape is now behind the head — and it is the first frame in which that can be checked
without a measuring tape.

---

## 6. `ROADMAP.md` denied a published release, for the third time

The status block opened **"v4.27 prepared — not published and not approved"** and gave v4.26 as the
baseline. Three lines below it, the same document carried the recipe *and* the reason:

> **Re-read the API rather than this line** — nothing in a working tree learns that a release went
> out

Run as written, `v4.27` comes back published at 2026-09-10T16:54:58Z, non-draft, non-prerelease,
with its APK. Corrected, and the baseline moved to v4.27.

`BACKLOG_v4_24.md` item 55 named this mechanism and stays open. What v4.28 adds is the observation
that **having the recipe in the document is not enough** — somebody has to run it — and the shape of
the check that would close it: one that fails when the tree's `versionName` is older than the newest
published tag and the status block still calls it unpublished. That is a test with a network call in
it, which is why it is proposed rather than written.

**Archiving.** `BACKLOG_v4_23.md`, `BACKLOG_v4_24.md` and `BACKLOG_v4_25.md` moved to
`docs/archive/`, with every item they still leave open — **18, 25, 30, 40, 50–55, 56, 58, 63** —
carried forward by number into `BACKLOG_v4_28.md`. That table is what makes the move safe: the
reasoning stays in the archived file, the fact that the item is open stays in the root.

`CHANGELOG.md` was **deliberately not touched**: its own first lines say it is the historical log of
the pre-release `vN` sequence and that "nothing here describes a current version". It ends at v73.
`RELEASE_HISTORY.md` is the engineering log and carries the v4.28 entry.

---

## 7. Verification

**Level 3.** The reason: this release changes Kotlin, resources, the asset pipeline and
`app/build.gradle.kts` (the version bump), and it is a release candidate. Level 2 would not have
built an APK, and the memory measurement that the ceiling raise was conditional on needs one.

### 7.1 What the numbers are, and what they were

Counts are commands, not memories — every figure below was produced by the recipe in `CLAUDE.md` §5
in this tree, and the v4.27 column by the same recipe in the extracted base ZIP.

| | v4.27 | v4.28 | |
|---|---|---|---|
| JVM unit tests | 1 365 | **1 382** | +17: the wave's night target, its inverted case and the shipped shape's floor; the carrying rule; the wave's depth key |
| instrumented tests | 158 | **161** | +3: two goldens and the Tundra capture. **161 of 161 pass**, 0 failures, 43 min |
| Canvas golden assertions | 31 | **33** | +2 |
| committed golden PNGs | 31 | **33** | 30 Canvas + 3 GL |
| GL references | 3 | **3** | untouched |
| shipped sprites | 266 | **305** | +39 |
| decoded sprite set | 28 619 568 B | **33 286 896 B** | 31.745 MiB against the new 32 MiB ceiling |
| registry entries | 266 (140 svg) | **305 (143 svg, 162 generated)** | |

**JVM: 1 382 tests, 0 failures, 0 errors.** `./gradlew test --dry-run` still resolves to
`testDebugUnitTest` alone, and it was run with `--rerun-tasks` because `app/build.gradle.kts`
changed — `CLAUDE.md` §7's first trap is that a test which reads a build script reports UP-TO-DATE
and green without executing.

**Lint: 0 errors, 28 warnings, 3 hints — identical to v4.27**, down to the per-rule breakdown
(18 `UnusedResources`, 4 `UseKtx`, 3 `AutoboxingStateCreation`, and one each of five others). The
39 new sprites added **no** `UnusedResources`: every one of them is reached by a draw path, which is
also what `SpriteReachabilityTest` asserts.

**Asset tooling.** `validate` clean; `python -m unittest discover -s tests` → **108 tests, 2
failures**, and those two are **byte-for-byte the same two the base ZIP fails**
(`KNOWN_PENDING_CROP_COUNT` reads 2 against 5 actual, and `sailboat_sail` still carries trailing
padding). Verified by running the same suite in the extracted v4.27 tree and comparing the assertion
messages. The carrying frames and the wave were kept *out* of that count properly rather than by
raising it: the carrying pose joined the `person_walk` co-registered group, because `drawPerson`
swaps a carry frame in for a walk frame **at the same origin** and a crop that moved one against the
other would make a figure jump the moment it put an umbrella up; the wave's two masks got a group of
their own for the same reason, and are then declared as a deliberate exclusion — the pair *is*
croppable, and the 12 672 decoded bytes it would recover are not worth trading the byte-identity
with the approved artwork plus an origin compensation with a device look attached.

### 7.2 The mutations — a green test that has never failed is not a check

`AI_PROJECT_RULES.md` 12.11. Four mutations, applied to the tree and reverted.

| mutation | expected to fail | result |
|---|---|---|
| `PedestrianCarry.nextCarrying` ignores `onScreen` — umbrellas may open in front of the viewer | `PedestrianCarryTest` | **caught** (1 of 8 failed) |
| `WAVE_BODY_LUMA_GAP_NIGHT` collapsed onto the day gate — the night target undone | `WaveContrastTest` | **caught** (1 of 5 failed) |
| the wave keyed by its bare base — the phase-3 depth defect reintroduced | `wave-storm` golden | **SURVIVED** |
| `SAILBOAT_HULL_WATERLINE_UNITS = 0` — the same defect at its constant | `LakeLanesTest` (added because of the row above) | **caught** (1 of 23 failed) |

**The third row is a finding and is reported as one.** The `wave-storm` golden portrays a wave and a
nearer hull overlapping correctly, and it does *not* catch the defect coming back: the frame happens
not to contain a pair whose order the key changes. One frame samples one configuration. The response
was not to hunt for a luckier frame but to put the check where the property lives —
`LakeLanesTest` now asserts, as arithmetic, that a wave whose base sits below the boat's key but
above the boat's hull is painted **behind** it, and that the same pair keyed by its bare base comes
out wrong. The hull offset is pinned to the artwork it comes from (MEASURED: `sailboat_hull` is
252 × 51 px = 84 × 17 units, blitted at +8, so 8 + 17 = **25**). `BACKLOG_v4_28.md` item 84 carries
it.

### 7.3 The template

```
Release identifier:            v4.28 (versionCode 59, versionName "4.28")
Verification level:            3
Reason for the level:          Kotlin, resources, the asset pipeline and app/build.gradle.kts all
                               change, and the release candidate's APK is what the memory
                               measurement the ceiling raise was conditional on is taken on.
Tests run:                     JVM 1 382, 0 failures, 0 errors (testDebugUnitTest --rerun-tasks)
                               instrumented 161 of 161 on the BV6600, 0 failures (43 min)
                               asset tooling 108, 2 failures -- both identical to the base ZIP's
Lint run:                      yes -- lintDebug: 0 errors, 28 warnings, 3 hints (identical to v4.27,
                               per rule as well as in total)
APK build run:                 yes -- assembleDebug and assemblePerf; assemblePerf for both v4.28
                               and v4.27-rebuilt-from-its-ZIP, for the A/B memory measurement
Static / bytecode checks:      lintDebug; SpriteGeometryTest, SpriteReachabilityTest,
                               SpriteTintClassTest, SpriteCanvasConventionTest and UnitFrameTest
                               read the shipped PNGs and the Kotlin sources directly
Mutation testing:              yes -- four mutations, three caught, one survived and is reported
                               (§7.2) with the test written that does catch it
ZIP verification:              yes -- all eight steps of 12.18, in order. File lists identical
                               (1 761 files, `find` on both sides, not `git ls-files`);
                               .gitignore / .github/ / CLAUDE.md / AI_PROJECT_RULES.md present;
                               .git / build / .gradle / .kotlin / local.properties / __pycache__ /
                               staging all absent; secret scan clean; and `git check-ignore` run in
                               a throwaway `git init` of the *extraction* confirms CLAUDE.md is
                               still untracked
Clean build from extracted ZIP: yes -- assembleDebug (23 049 714 B APK), testDebugUnitTest
                               **1 382 tests, 0 failures, 0 errors**, lintDebug 0 errors /
                               28 warnings, and the asset tooling's 108 tests with the same 2
                               pre-existing failures. The extracted build is what caught this
                               report quoting 1 378: the working tree's last full run predated the
                               four depth-key tests.
Maintainer-side verification required:
                               publication (tag, push, GitHub Release) -- never done here;
                               the visual judgement on the three artwork items as they ship;
                               the Tundra inverted-papers frame (§5.3), which is a judgement
Release identifier verified unique: yes -- `git tag --list` and the public Releases API both show
                               v4.27 as the newest; v4.28 does not exist
```

---

## 8. What is not done, and what is yours

**Publication.** No tag, no push, no GitHub Release. `release-notes/v4.28.md` is written and matches
the tag the release would carry.

**Four open items were opened rather than done**, three of them because the maintainer asked for
them to be opened and one because it needs photographs:

- **80** — `SpriteCache` decodes at the artwork's resolution, so an adult pedestrian is a 117 × 252
  bitmap that reaches the screen 37 px tall. This is the item that would stop the ceiling being an
  argument for several releases, and it is not a tick-box: the grid rule, the single global
  `SPRITE_PIXELS_PER_UNIT` divisor and the fact that downsampling is a visual change all have to
  move together.
- **81** — the atlas's 1024 px gate rejects nothing (MEASURED: largest dimension in the set is 798,
  0 of 305 over), and **11 892 KiB of the process is atlas that is allocated and empty — 23%**.
  **Deliberately not resized here**, on the maintainer's instruction and for a good reason: 2048 is
  the smallest maximum texture size ES 2.0 guarantees, and the replacement can only be chosen
  against the worst theme with this release's 39 sprites already in the set.
- **82** — three load-bearing numbers in comments are stale, and two of them now argue the opposite
  of the truth. Not corrected, because correcting them means re-measuring rather than re-typing, and
  two of the three are the paragraphs somebody would read before deciding item 81.
- **83** — the shipped wave stands over more of the lake band than its own derivation assumed
  (33.40 against a gate of 24.90 on Spring), and the tint is derived against the water at the band's
  **top** rather than at the wave's own lane. Both are measured, pinned by a test, and left: closing
  them changes daytime frames that were approved from photographs, and no photograph of the changed
  ones exists.

**Three judgements are yours**, and none of them is a defect:

1. **The bird at the size it ships.** §5.4 and the `bird-facing` golden.
2. **The umbrella on the street**, including the ~75 s a walker takes to pick one up after the rain
   starts. That delay is the price of the rule and it is deliberate; if it reads as too long, the
   alternative is umbrellas appearing in hands, and that is the thing the rule exists to prevent.
3. **The Tundra inverted papers.** §5.3. My reading is that they are acceptable — grey waves on ice
   water, correctly ordered and legible — but it is a look, not a measurement.

**`CHANGELOG.md` was deliberately not touched.** Its own opening says it is the historical log of
the pre-release `vN` sequence and that nothing in it describes a current version; it ends at v73.
`RELEASE_HISTORY.md` is the engineering log and carries the v4.28 entry.

**One measurement was attempted and not taken**, and §4.3 says so rather than leaving it implied:
the A/B repeated with rain switched on, so the new sprites would actually be resident. Driving the
scene settings needs several steps through a collapsible Compose tree and was abandoned. The bound
on what it would have shown is arithmetic and is in §4.3.
