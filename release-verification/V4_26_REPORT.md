# V4_26_REPORT.md — the sky and the sea redrawn, and what a measurement on the wrong build costs

One report for the release (`AI_PROJECT_RULES.md` 14.9). Every claim carries its label:
**OSSERVATO** (seen with my own tools in this pass), **MISURATO** (a number produced by a procedure
stated beside it), **DEDOTTO** (a conclusion drawn from those, not itself seen), or **DICHIARATO**
(stated by the maintainer or by an earlier pass and not re-derivable here).

Device for everything on hardware: **Blackview BV6600**, MediaTek Helio A25, PowerVR GE8320,
Android 10, 720×1440 at density 320, over `adb`. "The device" always means that one.

Baseline: **`PaperScrape_v4_25.zip`**, SHA-256
`af6dc0809571a7253e246341986ad4e9a9a97ebaf8c431939e6d123663047e1c`, 7 656 101 bytes, 1 312 files.
**v4.25 is published** — OSSERVATO, read from the public Releases API on 2026-09-09: `v4.25`,
non-draft, non-prerelease, published 09:23 UTC.

---

## 0. The three things to read first

**1. "+4.5 points of CPU for every PNG substituted" was the debug build talking, and that is a
result, not a footnote.** DICHIARATO by the v4.26 phase-2 pass and carried into three rounds of
concept work: replacing any sprite appeared to cost about 4.5 points of process CPU, whatever the
replacement was and however many vertices it carried. MISURATO on a release-like build, a
substitution costs what repeating the same measurement costs — the difference is inside the
run-to-run spread. The number was real; reading it as a property of the artwork was not. **It
applies to every future measurement: a figure taken on a debug build is a figure about a debug
build.**

**2. The `perf` build type was not this session's dirt — it shipped in v4.25.** OSSERVATO: the block
is in the delivered v4.25 ZIP *and* in the published `v4.25` tag on GitHub, carrying the comment
"TEMPORARY, for the v4.25 CPU measurement only. Never committed, never in the ZIP." The rule was
right and was ignored. It is removed here. **The impact on what anyone installed is nil** — CI
builds `assembleRelease`, so no published artefact was ever built from it — and there is nothing to
do to the published release. `BACKLOG_v4_26.md` item 66 asks whether "never commit it" is still the
right rule, with both arguments and a proposal, and is the maintainer's to decide after the release.

**3. The three GL reference frames had to be re-captured, and that is a decision this release took
because the artwork forced it.** See §6. It closes `BACKLOG_v4_25.md` item 56 as a side effect of
redrawing the sky, and it costs the Adreno driver-gap measurement `GlDriverGapGuardTest` was
calibrated on. It needs the maintainer's ratification.

---

## 1. Voce 61 — the seven length systems, closed with proposal C

**Taken first, before a single new constant was written**, which is what the instruction asked for
and is also the only order that makes sense: the new geometry could not be allowed to be born in the
ambiguous system.

**Which proposal, and why in one line.** **C**, the test that scans — it is the only one of the three
that keeps working after the next redraw and the only one that can be shown to bite by mutating the
source. **A** (put the frame in every name) catches nothing automatically, and the second of the two
v4.25 defects proves that a human reads past a name. **B** (`value class` per frame) makes the
mistake unrepresentable, but those types reach the draw path where 5.1 forbids per-frame allocation,
so its real cost is the boxing audit of 5.11 rather than the refactor.

**One addition to proposal C as the backlog describes it.** The backlog's own stated limit on C is
that it sees constants and not local variables — and v4.25's second defect compared a constant
against a **local** (`val pitch = CAR_PASSENGER_X_UNITS - CAR_HEAD_X_UNITS`). So `UnitFrameTest`
propagates the frame through local `val` declarations, and a `val` whose initialiser contains a
conversion becomes a conversion itself.

### What it does

Four assertions in `app/src/test/.../UnitFrameTest.kt`:

| assertion | what it holds |
|---|---|
| every length constant declares its frame | a `*_UNITS` name resolves through the prefix table or through an override **with a reason**, or it fails. Silence is not an option |
| no frame override restates what the name already declares | the override map is checked in both directions, so it cannot become an allowlist nobody prunes — the shape `SpriteReachabilityTest` established after item 57 |
| no expression compares two length systems without converting between them | the rule item 61 states, enforced over `main`, `test` and `androidTest` |
| the rule bites on a mixed expression | the mutation, in-source, so the claim is falsifiable on every run |

**A frame is a transform, not a sprite**, which is why a constant declares a *set* of frames.
`car_lamp_front` is blitted through `SpriteScale.SCENE_UNITS` inside the car's transform *and*
inside the fire engine's, so its own size is a length in both — and the three sites item 61 read as
"mixed and correct" need no exception, because they are not mixed.

### That it bites — MISURATO

Two demonstrations, and the second is the one that matters.

- **In-source**, on every run: `theRuleBitesOnAMixedExpression` builds v4.25's second defect and
  requires it to be caught on the line that mixes the frames, then requires the corrected form to
  pass.
- **On the real tree**: the actual defect was reintroduced by deleting `* CAR_OCCUPANT_SCALE` from
  `VehiclePedestrianScaleTest`. **Caught at `VehiclePedestrianScaleTest.kt:472`** —
  `pitch [car], widestDrawn [bustCar]` — which is the `assertTrue` two lines below the deleted
  conversion, **through two local variables**. That is precisely the shape the original
  constant-keyed scan could not see. Reverted.

### What the scan found on the tree — OSSERVATO

**Ten sites flagged on the first run, and none of them was a third defect.** Each was a shape the
analysis had to learn to read, and each is now written into the test with its reason:

1. a `val` re-declared under a name an earlier, unrelated `val` in the same file had left a frame on
   (three sites) — a `val` does not compare with itself;
2. an `if`/`else` **selection** between two vehicles' constants (`SceneObjectRenderer.kt:3667`) —
   branches are alternatives, not terms of one expression;
3. a **table** of several sprites' own constants in one `listOf` (three sites) — entries are
   unrelated to each other;
4. a family's `*_BASE_SCALE`, which *is* `scaleForHeight` written once per family and therefore a
   conversion (three sites in `SceneSpaceTest`).

One rename, and only where the backlog itself says the name declares nothing:
`WIDEST_SEATABLE_HEAD_UNITS` → `WIDEST_SEATABLE_HEAD_BUST_UNITS`.

### What stays open

`BACKLOG_v4_26.md` item 67: **the scanner reads Kotlin and not the generators**, and the *first* of
item 61's two defects was in Python — `build_people_concepts.SEATED_HALF_BAND`, which does not end
in `_UNITS` and names no frame, so there is nothing for a frame table to resolve. Closing it is
proposal A applied to `tools/assets`, then pointing the same scanner at those files. Item 68 records
the scanner's three honest limits.

---

## 2. The four families, through the pipeline

**Sources committed, PNG as the output of `render`, nothing copied out of a concept directory.**
`tools/assets/concepts/skywater/promote_v4_26.py` reuses the round-4 generator to write the five
production SVGs into `tools/assets/sources/svg/`; the shipped PNG is what
`python -m paperscrape_assets render` makes of them.

**MISURATO, `compare` after the promotion: all five sprites `PIXEL_IDENTICAL`** — the shipped PNG is
exactly what the committed source renders to. 140 of 266 sprites have a source; 140 `PIXEL_IDENTICAL`,
0 `EDGE_EQUIVALENT`, 0 `DIVERGENT`. `probe` matched its pinned fingerprint before any of it.

| sprite | canvas | what changed |
|---|---|---|
| `bird_body` | **90x24 → 51x21**, `CANVAS_PIXELS` | concept A "Colomba" at the reduced size; flap axis canvas row 18 → 15, and `BIRD_SPRITE_ORIGIN_X/Y_PX` (-45,-18) → (-25,-15) in the same change |
| `cloud_body` | 798x396, unchanged | concept C1 "Batuffolo", edge feathered 1.5 units, one vertical ramp inside one mass |
| `dolphin_body` | 345x174, unchanged | concept B "Rilievo", and three of its four papers **derived** — see §3 |
| `sailboat_hull` | 252x51, unchanged | concept B "Rilievo" |
| `sailboat_sail` | 210x180, unchanged | concept B "Rilievo" |

**The one difference from the photographed concept, and it is measured rather than waved at.** The
concept build post-processed the two tint masks to set near-transparent pixels back to white, which
the pipeline's `render` does not do. Shipping the raw render instead keeps `render` → shipped PNG
byte-identical, which is the whole point of the pipeline. MISURATO: alpha is identical everywhere,
and the largest **premultiplied** difference is **3.02/255 on 1% of the cloud's pixels** and
2.98/255 on 70 pixels of the bird — i.e. below the threshold any of this project's own comparisons
use. The tint-class means are 239.6 and 236.3 against the 220 `SpriteTintClassTest` requires.

**Two claim tests moved, and neither is a relaxation.** `SpriteMeasurementClaimTest` now states the
bird at 51x21, and `SpriteCanvasConventionTest` drops `bird_body` from `marginIsLoadBearing`: the
registration it needed is unchanged and still exact — the flap axis is canvas row 15 and the blit
origin is -15 — but it is now carried by a canvas the drawing fills rather than by a margin around
it. The library-wide count moved 255 → 256 with it. **The same recovery the person families made in
v4.25.**

**One thing `normalize` now reports and this release deliberately did not act on** — OSSERVATO: the
new dolphin, hull and sail carry removable transparent padding (345x174 → 342x168 and so on).
Trimming a canvas moves its blit origin, and the brief for these four families was that **every
canvas, scale convention, anchor and tint class stays the shipped one**, so no call site and no byte
of the decoded budget moves. Trimming them is a separate change with its own origin compensation.

---

## 3. The dolphin, derived rather than chosen

**The complaint.** `#4A6A84` on a `#15495C` night sea: invisible at night, invisible in the storm.

**The measurement, and it is worse than the complaint said.** MISURATO over **every** surface the
eleven dolphin-drawing themes can paint — the whole day/night sweep rather than its two ends, both
twilight branches, clear and storm, and both edges of the mirror, 906 distinct water colours — the
v4.25 back sat at **CIELab dE 1.53** from the water at its worst. That is below the ~2.3 that is a
just-noticeable difference for a *large flat field*, and this animal is 40 px of moving sprite over
textured water.

**Why CIELab and not a contrast ratio.** MISURATO: measured as WCAG luminance contrast the
sailboat's hull scores **1.009** against one of these waters and is plainly visible — it is orange on
blue, and a luminance ratio cannot see hue at all. The dolphin's problem is that it shares the
water's hue *and* its lightness, so the metric has to tell those apart.

**The gate, by the v4.22 method**: between the floor the failing element produces (**1.53**) and the
signal a working one produces — the animal's **own belly**, on the same water, in the same lanes, at
the same size (**18.78**). Gate = **10.16**. The belly is therefore deliberately left alone: moving
it would move the gate with it.

| paper | share of the animal | v4.25 | v4.26 | worst dE, v4.25 → v4.26 |
|---|---|---|---|---|
| back | 64.3% | `#4A6A84` | **`#BAA8AE`** | 1.53 → **17.97** |
| under-paper | 16.3% | `#2E4457` | **`#917F84`** | 0.78 → **12.68** |
| belly | 10.8% | `#E6EFF4` | unchanged | 18.78 |
| belly under-paper | 5.3% | `#B4C9D6` | **`#E6CED7`** | 9.11 → **24.72** |

Three of the four moved. The fourth is the signal arm. **The animal is lighter, which is what was
asked for, and lighter by the amount the derivation asks for rather than by an amount that looked
right.**

`LakeContrastTest` holds all of it: it reads the tones off the shipped PNG, enumerates the waters
from `ThemeCatalog` and `defaultCustomizationFor`, and holds **every paper covering at least 5% of
the animal** above the gate. Tundra's exclusion is *checked* rather than asserted in prose — it is
the one theme that switches the animals off, and if that default ever changes the test fails and the
derivation has to be redone.

**That it bites** — MISURATO: rendering the dolphin with the v4.25 palette fails it with
`#4A6A84 is 64% of the dolphin and measures dE 1.53 from the water at its worst (christmas
blend=0.30 progress=0.25 clear), against a gate of 10.16`.

---

## 4. S1 "Specchio", and the edge it needed

### The water

One vertical gradient from the sky's own weathered horizon colour into the theme's lake colour, the
drifting glints the surface has carried since v46, a soft reflected glow under the sun or the moon,
and the light's path — nine slivers that widen and lengthen toward the near edge. **No waves.** The
swell and the strokes were rejected from the device photographs: at the height this band is drawn
they read as stripes.

### The edge, and why the lake needed one

A mirror reflects the sky, so the closer a theme's sky and lake are, the more the two agree and the
less the water's top edge exists. **The worst case among the twelve themes — MISURATO, not picked**,
over the whole day/night sweep, both twilight branches, clear and storm, **against the sky
immediately above each theme's own shore**:

| | |
|---|---|
| worst | **Tundra, near midday, clear**: sky `#D0E7F2` above the shore against water `#D6EAF2` — **CIELab dE 2.16**, and **0.1 of Rec. 601 luma**: the same brightness |
| worst luma gap | Winter mid-morning, **0.1** |
| median over the twelve | dE 13.93, **14.5 of luma** |

**The top edge may not be made wavy** — the mountains anchor to the band's own nominal top Y, and a
jittered edge dips below it at some x and opens a sliver of bare sky. That argument is unchanged and
is still in `drawLakeBand`. So the distinction is made with a **struck edge**: a 2 px line along the
top of the band whose colour is carried away from the sky's luma until it clears
`WATERLINE_MIN_LUMA_GAP`, and no further.

**The gap is 14.5, and it is the median the twelve themes already produce**, measured the same way.
Deriving it from the distribution rather than picking it means the worst theme is lifted to what a
typical one already does, and no theme gets an edge louder than the scene's own habit. A theme
already that far clear gets `t = 0`, which is the water's own colour: **the line appears only where
it is needed.**

### The edge was derived against the wrong sky first, and only the picture said so

**OSSERVATO, and it is the reason images come before numbers.** The first implementation compared
the water against `skyHorizonColorNow` — the sky's colour at the **bottom of the screen**. The
water's top edge is nowhere near there: on Tundra the shore sits at 0.58 of the screen, where the
sky is **25 units of luma** away from the colour at the bottom. `LakeContrastTest` passed, because it
was restating the same wrong reference; the frame did not.

MISURATO at the shore on Tundra at midday, column 20, with the first implementation: sky **225.1**,
the struck line **228.8**, the water **232.9** — the line sat *between* the two instead of clear of
both, and did nothing. With the sky sampled where the line actually is: sky 225.1, line **239.2**,
water 232.9.

The gap was re-derived over the corrected reference at the same time — the median moved from 19.0 to
**14.5**, because judged against the right part of the sky the themes are closer together than they
looked.

### A defect found while promoting, and fixed

**OSSERVATO, and it is exactly the thing this release is trying to sharpen.** The reflected glow was
centred `bandHeight * 0.12` below the waterline with a radius of `bandHeight * 0.8`, and
`SceneCanvas` has **no clip** — so its upper half spilled into the sky above the shore. Measured
against the v4.25 frame, `people-skin` changed **11 rows above its own waterline, under the sun's x
and nowhere else**. Its centre is now one radius below the line, so its own zero alpha lands exactly
on the shore; its lower half runs under the hills, which are drawn after the water and cover it.

### What the water costs

**MISURATO, counted through the real `SceneCanvas` by a counting delegate over the real renderer:
28 primitives per frame**, against the **48** the v4.25 band cost (three tile copies × [1 fill + 6
tone bands + 4 ripple lines + 5 sparkles]). The phase-2 estimate was 22; the true number is 28,
because the estimate predated the struck waterline and assumed a scroll position where only two tile
copies reach the screen.

**Where the saving comes from**, and it is two things rather than one: the mirror is 6 primitives a
copy instead of 16, and **only the copies that reach the screen are drawn at all**. `lakeWrapped` is
in `(-screenWidth, 0]`, so the copy at `-1` is always entirely off the left edge — a third of the
water the old code painted outside the frame, every frame. The waterline, the glow and the light's
path are drawn once each outside the tile loop, because none of them varies along the scroll.

`LakeDrawCallTest` fails if the surface ever costs as much as the band it replaced.

---

## 5. Voce 65 — what the golden net covers now

### Before

**MISURATO.** Running the 26 committed Canvas assertions against this release's frames before
regenerating: **25 of 26 fired.** For comparison, the release that redrew every person in the scene
fired **1 of 26**. That is not the net getting better — it is this release changing the two largest
surfaces in the frame — and it is why the closure had to be derived gates rather than a number.

### The attribution, region by region, before regenerating

**MISURATO**, host-side, comparing each committed golden against the frame the device rendered, by
the suite's own per-pixel rule (any channel > 8):

| | |
|---|---|
| scenes that changed | **24 of 25** (`halloween-moon` is byte-identical: no lake, no clouds in frame) |
| changed pixels **below the ground line** (y ≥ 563 of 800) | **0, in every one of the 24** |
| scenes with **zero** change between the bird band and the ground | **18 of 24** |
| the six that do change there | the four lake scenes, plus `people-skin` (Beach's lake) and `people-window` (the sailboat) — every one accounted for by a family this release redrew |

### The gates, derived

Three rectangles, one per family, each placed by the v4.22 rule between the measured noise floor
(0.0000%) and the **weakest** regression that must fail. They hang off golden scenes that already
exist, so they add **assertions and no committed PNG**.

| rectangle | weakest regression | signal | gate |
|---|---|---|---|
| cloud band, on `day` | half the clouds gone | **17.5600%** | 8.7800% |
| bird band, on `day` | **every bird hidden** | **1.5232%** | 0.7616% |
| water band, on `lake-busy` | **every dolphin hidden** | **0.2050%** | 0.1025% |

**Two of those three are item 65's complaint made concrete.** Losing the entire bird family moves
1.52% of its rectangle and losing every dolphin moves 0.21% — **both under the shared 2% focus
limit**, and the water one is under the whole-frame gate's 0.2% as well. Before these gates existed,
every bird and every dolphin could vanish from the scene and the suite would have gone green.

**The cloud gate is derived from half the clouds and not from all of them, and the difference
matters.** The band is 92% cloud, so deriving from total absence would put the gate at 46% — a
number only a structural failure could reach, which the whole-frame gate already catches. A gate has
to be derived from the weakest regression it must see, not the loudest one that is easy to produce.

**No tolerance was moved and no number was lowered.** `MAX_DIFFERING_FRACTION` is 0.002 and
`MAX_FOCUS_DIFFERING_FRACTION` is 0.02, exactly as before; the four v4.22 gates are untouched.

### What the net catches now, measured on a mutation that changes one family

**The measurement item 65 asks for.** The bird family was put back to its v4.25 artwork — the 90x24
sprite and the (-45,-18) origin it needs — and the golden suite run against the committed frames.

| | |
|---|---|
| assertions that fired | **18 of 29** |
| the same measurement for v4.25, which redrew *every person in the scene* | **1 of 26** |

The scenes that fired are `day`, `dusk`, `lake-busy`, `lake-empty`, `overcast`, `rain`, `snow`,
`thunderstorm`, `waterline-worst-theme` and six of the `people-*` frames — every frame with sky in
it, which is every frame with a bird in it.

**And the honest reading of that number**: the redrawn bird moves 1 981 pixels, **0.688% of the
frame**, which is more than three times the whole-frame gate — so this mutation is caught by the
gate that already existed, and the new bird rectangle never got to speak, because the whole-frame
check throws first. What the new rectangles buy is what happens *below* that line, and the derived
signals say it exactly:

- **every dolphin disappearing** moves 0.2050% of the water band — **75 pixels, 0.026% of the
  frame**, an eighth of the whole-frame gate. Nothing but the water rectangle could see it;
- **every bird disappearing** moves 1.5232% of the bird band, which is under the shared 2% focus
  limit that rectangle would otherwise have carried.

### The honest limit

These three rectangles catch a family's **absence**, and the cloud one catches half of it. A redraw
that changes less than that is still invisible to them, and saying so is what keeps the number
above meaning something.

---

## 6. The three GL reference frames had to be re-captured

**This is the one decision in the release that I took on the maintainer's conditional instruction
rather than on a rule already written down, and it needs ratifying.**

The instruction was "GL untouched **if** the Canvas frames of their scenes do not change". The three
GL scenes are `day`, `lake-busy` and `thunderstorm`, and all three Canvas frames changed — by
17.1%, 23.9% and 13.0% of the frame. So the condition does not hold, and the conditional implies
re-capture.

**MISURATO, before re-capturing**, running the suite against the Adreno-authored references:

| | |
|---|---|
| `GlSceneGoldenTest.day` | outline moved **6.31%**, limit 3.00% |
| `GlSceneGoldenTest.lakeBusy` | outline moved **9.51%**, limit 3.00% |
| `GlSceneGoldenTest.thunderstorm` | **3.192%** of flat interiors differ, limit 0.500% |
| `GlDriverGapGuardTest` | gap grown to day 6.31%, lake-busy 9.51%, thunderstorm 2.12%, characterised limit 2% |

There was no third option. Those frames portray a sky and a sea the build no longer draws, so **no
driver could match them**, and raising a tolerance to make them pass is the exact move `CLAUDE.md`
§7 forbids. **No tolerance was raised.** The three references were re-captured on this device with
`-e updateGoldens true`, exactly as the protocol prescribes.

### What that costs, precisely

- **`BACKLOG_v4_25.md` item 56 is closed as a side effect of redrawing the sky**, not as a decision
  taken on its own merits. That item said re-baselining "is a release of its own"; the artwork made
  it a consequence of this one.
- **The reference driver moves from Adreno 630 to PowerVR GE8320.** `GlDriverGapGuardTest` measures
  the gap between whatever driver is running and whatever is committed, so **on this device it will
  now read ~0** — which its own doc says is correct on the authoring environment, and which also
  means **the cross-driver gap is no longer being measured anywhere** until someone runs the suite
  on another driver. The 1.18 / 1.07 / 0.92% characterisation is now history rather than a live
  measurement.
- **Nothing is destroyed.** The Adreno-authored frames are in `PaperScrape_v4_25.zip` and in the
  published `v4.25` tag. What is gone is their usefulness *against this artwork*, not the files.

`BACKLOG_v4_26.md` item 71 records it as the maintainer's to ratify, with what putting it back would
mean.

---

## 7. What was left alone, and one thing that surprised me

**`normalize` now reports removable padding on three of the five new sprites** and it was not acted
on — see §2. Trimming a canvas moves its blit origin, and these four families were promoted on the
explicit basis that every canvas, anchor and tint class stays the shipped one.

**No committed golden contained a struck waterline, and that is a coverage finding.** OSSERVATO:
regenerating all twenty-five goldens after the waterline landed changed **zero** of them. In every
scene the suite already pinned, the theme's sky and its water are far enough apart that the line
correctly does not appear — so the feature that exists for the *worst* theme had nothing pinning it.
`waterline-worst-theme` is the twenty-sixth golden and the first frame in which it is drawn: Tundra
with a tall lake at midday, the measured worst case, with a focus rectangle eight pixels tall around
the line itself. It is the same answer v4.23 gave when no committed frame drew a celestial body.

---

## 8. Verification

**Level 3**, and the reason is the level's own definition: this release changes the **asset
pipeline** (five new SVG sources and a promotion script), **resources** (five shipped PNGs) and the
**build configuration** (the `perf` build type removed, `versionCode`/`versionName` bumped).

```
Release identifier:            v4.26  (versionCode 57, versionName "4.26")
Verification level:            3
Reason for the level:          asset pipeline + resources + build config + release candidate
Tests run:                     JVM 1353 tests, 0 failures, 0 errors
                               instrumented, whole suite on the BV6600: 156 tests, 0 failures
Lint run:                      yes -- 0 errors, 28 warnings, 3 hints (unchanged from v4.25)
APK build run:                 yes  (assembleDebug, Level 3)
Static / bytecode checks:      paperscrape-assets probe/inventory/validate/normalize/render/compare
Mutation testing:              yes  (four, all reverted -- see below)
ZIP verification:              yes -- all eight steps of 12.18
Clean build from extracted ZIP: yes -- assembleDebug, testDebugUnitTest and lintDebug
Maintainer-side verification required: publication -- tag, push and GitHub Release; plus the two
                               ratifications in BACKLOG_v4_26.md items 66 and 71
Release identifier verified unique: yes -- `v4.26` is absent from `git tag --list` and from the
                               Releases API, read on 2026-09-09
```

### The mutations, and what each one proved

| mutation | what it proved |
|---|---|
| `* CAR_OCCUPANT_SCALE` deleted from `VehiclePedestrianScaleTest` — v4.25's real second defect | `UnitFrameTest` catches it at line 472, through **two local variables** |
| the dolphin rendered with the v4.25 palette | `LakeContrastTest` fails with `dE 1.53 ... against a gate of 10.16` |
| `drawWaterline(canvas, top)` deleted from `drawLake` | `LakeContrastTest` fails with `drawLake no longer strikes the waterline` |
| the bird family put back to its v4.25 artwork | **18 of 29** golden assertions fire — see §5 |

Every one was reverted, and the tree that produced the green suite is the tree in the archive.

### The device

The BV6600 is left with this build installed, as the maintainer instructed. No restore captures were
taken, nothing was uninstalled, and the phone was not reset.

---

## 9. The archive

**`PaperScrape_v4_26.zip`**, in `/home/bober/claude-shit/consegna_v4_26/`.

| step of 12.18 | result |
|---|---|
| 1. build the archive | 1437 files. **An archive cannot contain its own checksum**, so the SHA-256 is in `SHA256SUMS.txt` beside it and in the delivery message, not in this file |
| 2. extract into a clean directory | OK, shares nothing with the working tree |
| 3. completeness against the working tree, file by file | **identical file sets, 1437 files**, compared with `find -type f` on both sides rather than `git ls-files`, which an extraction has no `.git` to answer |
| 4. `.gitignore`, `.github/`, `CLAUDE.md`, `AI_PROJECT_RULES.md` present | all present, plus `gradlew` (mode 0755 preserved), `gradlew.bat`, `debug.keystore`, the wrapper jar and `release-notes/v4.26.md` |
| 5. `.git/`, `build/`, `.gradle/`, `local.properties` absent | all absent, and so are `app/build/`, `.kotlin/`, `tools/assets/staging/` and every `__pycache__` |
| 6. scan for secrets | clean. Two hits are placeholder strings in test fixtures (`"weatherapi-com-key"`, `"fake-openmeteo-key"`); `keyPassword = "android"` is the debug keystore's deliberately public password. `git check-ignore` in the extraction confirms `CLAUDE.md` is still ignored by `.gitignore:44` |
| 7. build **from the extracted copy** | `assembleDebug` OK, 22 767 782-byte APK |
| 8. run the tests **from the extracted copy** | 1353 tests, 0 failures, 0 errors; lint 0 errors |

Alongside it in the same directory:

- **`anteprime/`** — the four rounds of proposal photographs, carried over rather than re-made:
  `fase1`, `fase1b`, `fase2`, `fase3`.
- **`immagini_report/`** — the pictures this report refers to: the dolphin before and after on a
  night sea and in the storm, the struck waterline with and without it on the two closest themes,
  the before/after sheets for the water and the sky, and the four full frames.

**Publication is outstanding and is the maintainer's.** No tag, no push, no GitHub Release, no
credential used. `v4.26` is absent from `git tag --list` and from the Releases API.
