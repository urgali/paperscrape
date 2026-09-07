# Santa sleigh — canvas normalisation, origin compensation, regression guard

> **Delivery documentation.** Not part of PaperScrape's functional source. It ships inside the
> archive so the change travels with its evidence, and it is untracked in the batch's local git
> repository, so the committed diff is the fix and nothing else.

**Batch type:** asset maintenance. **Not an Android release** — `versionName 4.7`,
`versionCode 38`, both unchanged.

---

## Executive Summary

The crop is done, through the project's own tooling, and it is **pixel-identical**: 0 differing
pixels, on both frames, in sprite space and again through the real 0.5× screen scale. The two
`normalize` failures are gone; the Python suite drops from 3 failures to 1, and the one left is
the pre-existing environmental probe fingerprint.

**Both open questions came back "not a bug", and neither was touched.** `RELEASE_HISTORY.md`'s
v4.7 section answers both explicitly — see §*Origin X Decision* and §*Anchor Decision*. That was
the decision gate, and it closed on documented evidence rather than judgement.

**Scope delivered:** crop + Y compensation + regression guard. Seven files, nothing else.

---

## Baseline

| | |
|---|---|
| State | v4.7 + Pillow 12.3.0 + asset registry fix, as published |
| Files | 734 (733 tracked; `CLAUDE.md` present and gitignored, as the project requires) |
| `versionName` / `versionCode` | `4.7` / `38` — unchanged |
| Pillow / resvg_py / numpy | 12.3.0 / 0.4.0 / 2.4.4 — untouched |
| Registry / shipped / unregistered | 221 / 221 / 0 |
| `release-verification/` | 6 files, present |
| Baseline commit | `4664934` "Baseline: v4.7 + Pillow 12.3.0 + asset registry fix" |
| Fix commit | `85141c2` "Normalise the sleigh's canvas, and move its origin by the same trim" |
| Environment | Python 3.14.7, JDK for Gradle 9.7.1 / AGP 9.3.1 offline, Pixel 9 emulator API 37 |

— **VERIFIED**

## Santa Scene Measurements

| | before | after |
|---|---|---|
| canvas | 600 × 153 px = 200 × 51 units | **594 × 123 px = 198 × 41 units** |
| alpha bbox | [0, 19, 592, 140] | **[0, 1, 592, 122]** |
| content size | 592 × 121 px = 197.33 × 40.33 units | unchanged |
| padding L/R/T/B | 0 / 8 / 19 / 13 px | 0 / 2 / 1 / 1 px |
| ink (alpha > 0) | 38,276 | **38,276** |
| fully opaque | 35,719 | **35,719** |
| file | 23,673 B | 22,872 B (−801, −3.4 %) |
| decoded ARGB_8888 | 0.367 MB | 0.292 MB (−20.4 %) |
| sha256 | `b504d6c2792b037a…` | `fa8f366bcab547e8…` |

## Santa Trot Measurements

Identical in every geometric respect, before and after.

| | before | after |
|---|---|---|
| canvas | 600 × 153 | **594 × 123** |
| alpha bbox | [0, 19, 592, 140] | **[0, 1, 592, 122]** |
| ink / opaque | 38,276 / 35,719 | **38,276 / 35,719** |
| file | 23,668 B | 22,871 B (−797, −3.4 %) |
| decoded | 0.367 MB | 0.292 MB (−20.4 %) |
| sha256 | `3e960cea1d4ca0ac…` | `c98dd6b098cd6d60…` |

**Totals:** 47,341 → 45,743 B (**−1,598 B**); 0.734 → 0.584 MB decoded (**−0.150 MB**);
37,476 canvas pixels removed, **all of them fully transparent**. — **VERIFIED**

## Root Cause

The padding was **residue of the v4.7 redraw**, not design. `RELEASE_HISTORY.md` records it:

> *"The content box changed: `(0, 1, 598, 152)` → `(0, 19, 592, 140)`, because the composition
> aligns the team's baselines — hooves and runner share a line — so the group no longer touches
> the canvas edges."*

The drawing shrank inside a canvas that stayed 600 × 153. `normalize` had been reporting both
sprites as croppable ever since, and the report was correct. Nothing about the padding served the
anchor, the animation or the composition — proven below by the crop being pixel-neutral.

Not in `EXCLUSIONS` (15 sprites are, deliberately), and no note anywhere claims a purpose for it.
— **VERIFIED**

## Crop

Applied with `paperscrape-assets normalize --apply` — the project's own path, not a hand crop.
That single command is what keeps the three artefacts consistent:

- crops the PNG to the grid-aligned box the planner computed, `(0, 18, 594, 141)`
- **refuses outright** if the crop would discard a pixel that is not fully transparent
- resizes the SVG source's canvas: `width/height 600×153 → 594×123`,
  `viewBox="4 2 200 51"` → `"4 8 198 41"`, with every path coordinate untouched
- rewrites the registry geometry: size, `contentBox`, and the `CONTENT_BOTTOM_CENTRE` anchor
  `(296, 140) → (296, 122)`

Both frames received the **same** rectangle, which is what makes the pair stay aligned by
construction rather than by luck.

**Artwork integrity** — independently re-checked rather than trusted:

- the retained region is byte-for-byte the same rectangle of the original, on both frames
- max alpha anywhere in the discarded region: **0**
- ink and opaque pixel counts unchanged

— **VERIFIED**

## Origin Compensation

`SANTA_SLEIGH_ORIGIN_Y_UNITS`: **−25.5 → −19.5**. `SANTA_SLEIGH_ORIGIN_X_UNITS` unchanged.

The blit path is `SpriteBlitter.blit` under `SCENE_UNITS`:

```
canvas.scale(1/3, 1/3);  drawSprite(resId, originX * 3, originY * 3)
```

so the constants place the bitmap's **pixel (0,0)**, i.e. the canvas corner. The crop removed 18
rows off the top, so pixel (0,0) now sits 18 sprite pixels lower in the drawing; the origin moves
down by the same 18 = **6 local units**. X does not move because the trim removed nothing from
the left — the content already started at column 0. This is exactly the compensation the tool
itself printed: `origin +(0,6) units`.

```
before:  origin × 3 = (−299.01, −76.50) sprite px
after :  origin × 3 = (−299.01, −58.50) sprite px
delta  =            (     0.00, +18.00)   crop top trim = −18.00   → cancels exactly
```

Both terms are **integers**, so no sub-pixel component is introduced. — **VERIFIED**

## Origin X Decision

### **Origin X is intentional, not a bug. Not changed.**

The assessment observed that `−99.67 = −598/2/3` derives from the *pre-redraw* content width
(598 px) while the actual content is 592 px wide, leaving the group ~3 sprite pixels left of its
flight point. That observation is correct — and it is **already recorded as an accepted
decision**. `RELEASE_HISTORY.md`, v4.7:

> *"What did move is the drawing inside that canvas: its centre shifted by (−3, +3) sprite pixels,
> which after the 0.5× blit is **1.5 px left and 1.5 px down on screen**. Under two pixels, and
> **no constant was touched to compensate** — recorded here because it is a real change in
> apparent position and should not surface later as a surprise."*

Three things follow, and all three point the same way:

1. It was **seen, measured and consciously left alone** by the change that caused it.
2. The magnitude is **1.5 screen pixels**, not the ~4.5 the assessment estimated — that estimate
   applied `SANTA_SLEIGH_SCALE = 1.5` without dividing by the `SCENE_UNITS` oversample. The net
   factor is 1.5 / 3 = **0.5**, which is what the release history states independently.
3. Changing it now would **move the artwork on screen**, which is the one thing this batch is
   required not to do. It would also be a design decision about where the group sits relative to
   its flight point, which is not a crop's business.

— **VERIFIED** (that it is documented and deliberate)

## Anchor Decision

### **Santa deliberately uses an independent canvas-corner origin. The registry anchor is descriptive metadata. Not realigned.**

The assessment found the declared `CONTENT_BOTTOM_CENTRE` anchor `(296, 140)` implies an origin
of `(−98.667, −46.667)` while the call site passes `(−99.67, −25.5)` — a 21-unit disagreement
vertically. `RELEASE_HISTORY.md` answers it in one sentence:

> *"**Nothing in Kotlin reads the manifest.** The renderer places the sprite by
> `SANTA_SLEIGH_ORIGIN_{X,Y}_UNITS`, which address the **canvas** corner"*

So the two numbers are not two statements of the same fact that have drifted apart; they are
**two different things**. The registry records the sprite's geometry for the asset tooling, and
the renderer places the group by a flight point of its own. `validate` never compares them
because the blit is a conditional expression
(`if (trotting) R.drawable.santa_sleigh_trot else …`) that `callsites.py` correctly declines to
resolve — it reports both sprites as *unresolved*, which is the honest answer, not a missed check.

Realigning them would change where the sleigh flies. **Not done, and not proposed as a follow-up
either** — there is nothing to fix.

The crop nevertheless keeps the registry honest: `--apply` moved the declared anchor to
`(296, 122)` so it still describes the shipped pixels. — **VERIFIED**

## Regression Tests

`app/src/test/kotlin/…/engine/SantaSleighOriginTest.kt` — four JVM tests, using the project's
established pattern (`ImageIO` + the `drawableDir` walk-up, as `SpriteTintClassTest` and
`SkinToneAssetsTest` already do).

The asserted property is deliberately **not** `originY == -19.5f`, which a later crop would
simply edit to match. It is the thing that must not move:

```
content position in local units  =  origin (from PaperRenderer)  +  alpha bbox (from the PNG)
```

Both halves are read from what ships; only the answer is written down.

| Test | Guards |
|---|---|
| `the drawing lands where it always has, whatever the canvas around it is` | a crop without its compensation, in either axis |
| `the drawing keeps its size, so a crop never took artwork with it` | a crop that ate ink |
| `both frames are blitted through one transform, so the animation cannot jump` | the two frames drifting to different canvases or content boxes |
| `the two frames are still different drawings` | a trot frame that animates nothing |

### Proof of effectiveness

Reverting **only** the compensation (`-19.5f` → `-25.5f`) while keeping the cropped PNG:

```
SantaSleighOriginTest > the drawing lands where it always has … FAILED
  java.lang.AssertionError: santa_sleigh_scene: the drawing's top edge moved -- if the canvas
  was cropped, SANTA_SLEIGH_ORIGIN_Y_UNITS has to move by the same trim
  expected:<-19.1667> but was:<-25.166666>
BUILD FAILED
```

The other three still pass, correctly: they guard different properties. — **VERIFIED**

## Pixel Identity Verification

Each frame composited at its own origin — baseline PNG at −25.5, cropped PNG at −19.5 — on a
fixed canvas, then compared:

| | sprite space | through the real 0.5× screen scale |
|---|---|---|
| `santa_sleigh_scene` | **0 differing pixels** | **0 differing pixels** |
| `santa_sleigh_trot` | **0 differing pixels** | **0 differing pixels** |

On-canvas bounding box before and after, both frames: `(501, 343, 1093, 464)` → identical.

This is the strong form of the criterion: not "looks the same", but the same bytes. It holds
exactly because the origin delta (+18 sprite px) and the crop's top trim (−18) are both integers.
— **VERIFIED**

## Scene vs Trot Consistency

| | result |
|---|---|
| same canvas after crop | ✅ 594 × 123 both |
| same content box after crop | ✅ [0, 1, 592, 122] both |
| same crop rectangle applied | ✅ (0, 18, 594, 141) |
| same origin compensation | ✅ one shared pair of constants |
| opaque pixels lost | **0** on both |
| differences between the frames | 3,956 px (4.31 %), confined to **x 35…265, y 102…139** — the reindeer legs |

No other region differs. No micro-shift is possible: the two frames are indexed from one call
site through one transform, and the new test pins that they share a canvas and a box.
— **VERIFIED**

## Normalize / Validate

| | before | after |
|---|---|---|
| `validate` | exit 0 (already green) | **exit 0**, 0 FAIL |
| `normalize` | exit 1, 2 targets: `santa_sleigh_scene`, `santa_sleigh_trot` | **exit 0** — `normalisation OK: 73 target(s) checked, none carries removable padding; 15 sprite(s) excluded by decision` |
| Python suite | 103 tests, **3 failures**, 0 errors | 103 tests, **1 failure**, 0 errors |

The one remaining Python failure is `test_toolchain_matches_the_pinned_fingerprint` — the
pre-existing environmental probe mismatch (zlib / interpreter build), identical before and after,
and **the pinned fingerprint was not modified and not regenerated**. — **VERIFIED**

### Kotlin suite

| | baseline | after |
|---|---|---|
| passed | 1,031 | **1,035** (+4, the new tests) |
| failing classes | `ApkDownloadPathTest` (12) | `ApkDownloadPathTest` (12) |

`ApkDownloadPathTest` binds a local HTTP server, which this sandbox blocks; it fails identically
on the untouched baseline. **Zero regressions.** — **VERIFIED**

## Runtime Pixel 9 Verification

Debug APK built offline from this tree and installed on the emulator: `sdk_gphone16k_x86_64`,
**Android 17 / SDK 37**, 1080 × 2424. Christmas theme selected; Seasons & decorations confirms
*"Christmas lights, Santa, Gifts — 3 on"*. Live wallpaper opened full screen and sampled across
several flight cycles (the flight is randomly timed, 25–60 s apart, lasting 9–13 s).

**Two flights captured.** — **OBSERVED**

- The group renders in its flight band in the upper sky, at the expected scale.
- Santa, the sleigh tub, the load behind him and **both reindeer** are all present and aligned;
  the harness lines connect, and hooves and runner sit on one line — the composition the v4.7
  redraw describes.
- No clipping, no vertical displacement, no seam, no visible gap where the canvas was trimmed.
- Gifts drop from the sleigh, so the effect's own logic is running.
- One flight travelled left, one right — the random direction `SantaSleighEffect` picks, not an
  asset difference.

**What this does not establish:** a pixel-level runtime before/after. The flight is randomly
timed, randomly directed and randomly placed vertically, so the same moment cannot be captured
twice for comparison. The pixel-identity claim rests on the offline proof above, which is exact;
the device run confirms the sprite renders and is positioned sanely with the new asset and the new
constant. — **OBSERVED**, not VERIFIED at pixel level.

## PNG / File Size Impact

| | before | after | delta |
|---|---|---|---|
| files on disk | 47,341 B | 45,743 B | **−1,598 B (−3.4 %)** |
| decoded ARGB_8888 | 0.734 MB | 0.584 MB | **−0.150 MB (−20.4 %)** |
| canvas pixels | 2 × 91,800 | 2 × 73,062 | −37,476, all fully transparent |

On a ~5 MB archive the file saving is negligible; the decoded-memory saving is the real one, and
it is what `normalize` was reporting. — **VERIFIED**

## Golden Impact

**No golden changed, and none could have.** All 27 are byte-identical to the baseline.

Checked structurally rather than by eye: `sceneCustomization.santaEnabled` defaults to `false`
(`SceneCustomization.kt:291`) and `BuiltInThemeCoherenceTest` pins that only the `christmas` theme
sets it — *"christmas must have Santa"*, and every other theme *"should not fly Santa"*. No golden
scene is the christmas theme, so `SantaSleighEffect.update` takes its `if (!enabled)` branch and
`draw` returns before blitting anything. The sleigh cannot appear in a golden.

This matches what `RELEASE_HISTORY.md` recorded for v4.7 (*"No golden contains the sleigh — all 27
were scanned … No golden was regenerated"*), arrived at independently. — **VERIFIED**

## Remaining Findings

Reported, deliberately **not** acted on.

1. **Stale geometry in `PaperRenderer.kt` comments.** Lines ~513 and ~1010–1021 still describe the
   sprite as *"624x168 with a content box of (12,12)-(610,159)"* and *"the content box is 598px
   wide"*. Both were already wrong before this batch — the v4.7 redraw made them so — and the
   block at ~511 is an orphaned KDoc attached to nothing. Correcting them is a documentation
   change with no behavioural component, so it was left out rather than folded in silently. The
   new constant carries an accurate KDoc of its own. — **VERIFIED** (that they are stale)
2. **`rainbow_arc` has the same unguarded shape.** D-10 cropped it with an origin compensation
   exactly like this one, and `SkySpriteAnchoringTest` covers only the sun, moon and star sprites
   — so `RAINBOW_SPRITE_ORIGIN_{X,Y}_UNITS` is a hand-edited pair with nothing pinning it to the
   PNG. `SantaSleighOriginTest` is the pattern that would close it. — **OBSERVED**
3. **The probe fingerprint** does not reproduce on this environment. Pre-existing, unrelated, and
   left untouched. — **NOT VERIFIED**

## Git

```
branch: main            working tree: clean
  85141c2  Normalise the sleigh's canvas, and move its origin by the same trim
           7 files changed, 201 insertions(+), 27 deletions(-)
  4664934  Baseline: v4.7 + Pillow 12.3.0 + asset registry fix     733 files tracked
```

Files changed — exactly the approved scope, nothing else:

```
M  app/src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt
M  app/src/main/res/drawable-nodpi/santa_sleigh_scene.png
M  app/src/main/res/drawable-nodpi/santa_sleigh_trot.png
M  tools/assets/sources/sprites.json
M  tools/assets/sources/svg/santa_sleigh_scene.svg
M  tools/assets/sources/svg/santa_sleigh_trot.svg
A  app/src/test/kotlin/com/paperscrape/livewallpaper/engine/SantaSleighOriginTest.kt
```

`CLAUDE.md` is present on disk and correctly **untracked** (`.gitignore:44`), as the project
requires. This report is untracked too, so the committed diff is the fix alone.

**Not done:** no push, no tag, no GitHub Release, no pull request, no credential used, and no
operation of any kind against the maintainer's own checkout. — **VERIFIED**

## ZIP

Complete archive of the project with the change applied, built from the filesystem so gitignored
project files (`CLAUDE.md`) travel with it. Excluded: `.git/`, `build/`, `app/build/`, `.gradle/`,
`.idea/`, `.kotlin/`, `local.properties`, APK/AAB, release keystores, virtualenvs, caches,
`tools/assets/staging/`, `__pycache__/`.

`debug.keystore` **is** included: a tracked project file holding the publicly-known Android debug
credentials, pinned so every machine signs debug builds with the same certificate.

Name, size, SHA-256, file count and the accounting against the baseline are in the delivery
message accompanying this archive.

## Limitations

1. **Runtime verification is visual, not pixel-level** — the flight cannot be reproduced at the
   same moment twice. — **OBSERVED**
2. **The probe fingerprint could not be validated** on this environment. — **NOT VERIFIED**
3. **`ApkDownloadPathTest`** fails on this sandbox, identically on the untouched baseline
   (local HTTP server binding). — **VERIFIED as pre-existing**
4. **No instrumented (`connectedAndroidTest`) run** — the change touches two decoration sprites
   and one constant; the goldens provably cannot contain the sleigh.
5. **The commits sit on a scaffold base**, not the project's real history.
6. **Origin X and the registry anchor were investigated and left alone.** If the maintainer later
   decides the 1.5 px offset should be closed, that is an artwork-position decision needing its
   own mockup and its own device look — not a crop.

---

*Seven files. Pixel-identical rendering, proven offline and confirmed on device. Nothing pushed,
tagged or released.*
