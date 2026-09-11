# v4.29 — the atlas packer, and a ceiling that measures the right thing

`versionCode = 60`, `versionName = "4.29"`. Base: `PaperScrape_v4_28.zip`, SHA-256
`a2df8f20…6bf655c`, 1 761 files, verified by `sha256sum` before extraction.

**This release changes one line of behaviour.** Every other source edit in it is a comment. The
diff against v4.28, excluding documents:

```
app/build.gradle.kts                   versionCode 59 -> 60, versionName 4.28 -> 4.29
engine/ShelfPacker.kt                  deleted
engine/AtlasPacker.kt                  new
engine/GlTextureAtlas.kt               ONE line of code: ShelfPacker(...) -> AtlasPacker(...)
                                       plus two rewritten comments
engine/GlTextureCache.kt               comment only -- zero non-comment changes
engine/PaperRenderer.kt                comment only -- zero non-comment changes
test/ShelfPackerTest.kt                deleted; AtlasPackerTest.kt replaces it
test/SpriteDrawScaleTest.kt            carried in from phase A, plus one new budget
test/SpriteGeometryTest.kt             the decoded ceiling's argument rewritten
test/SpriteMeasurementClaimTest.kt     the scan's file list replaced by a directory walk
```

**`app/src/main/res/` is untouched and `app/src/androidTest/assets/golden/` is untouched** — not
one byte, verified by `diff -rq` against a fresh extraction of the base ZIP. No artwork moved, and
the brief's rule for this round ("if the visual result changes by one pixel it is a defect of this
round") is therefore checkable at the file level before any frame is captured.

Legend: **[M]** measured, with the command that produced it · **[O]** observed in the code · **[D]**
reasoned, not verified.

---

## 0. The three things to read first

1. **The defect was the packer, not the atlas's size.** `BACKLOG_v4_28.md` item 81 recorded
   "11 892 KiB allocated and empty" and suggested shrinking the atlas. v4.29's phase A showed that
   number was content **area** and the atlas was already at 55 % of its **rows** on that same scene.
   This release fixes what was actually stranding them.
2. **Phase A's two censuses are the "before".** They were taken from a separate session on
   v4.28's code, and they live in `misure/` in the **delivery folder** — `consegna_v4_29/`, beside
   the archive rather than inside it, along with this release's own censuses, the recorded
   insertion sequence and the packer-comparison script. This pass reproduced the max census
   independently before changing anything, which is how the host replay harness was validated; both
   sets of numbers are below, and they agree in kind and differ in detail for a reason that is
   stated rather than smoothed over.
3. **The decoded-sprite ceiling is not GPU memory**, it never was, and v4.28 raised it partly on
   that reading. It is kept — it is the only thing measuring a path every device takes — and a
   second ceiling now measures the GPU.

---

## 1. The packer

### 1.1 What was wrong **[O]**

`ShelfPacker` kept **one** row open. Entries filled it left to right until one did not fit, then a
new row opened below the tallest entry of the row just closed. Its own class comment dismissed the
cost:

> It wastes more area than a real bin packer, and it cannot reuse the space of an entry that is no
> longer wanted. Both are acceptable here and neither is worth the code it would take to fix: the
> set is small […]

The second half is still true and still does not matter — `GlTextureCache` never evicts a single
entry, so a packer that could free one rectangle would have nothing to free. **The first half is
wrong by more than a factor of two.** Two kinds of space are lost and neither is ever reclaimed:

- **the tail of every closed row**, because only one row is ever open;
- **the slack above every entry shorter than the tallest one in its row.**

### 1.2 Why the shipped diagnostics could not see it **[M]**

`atlasFill` is `capPlacedBytes / capAllocatedBytes` — padded content rectangles over the whole
texture. That ratio is blind to space a packer has consumed and not filled, which is precisely where
a shelf packer's waste lives. Phase A added the missing counter and re-ran **the v4.28 memory
investigation's own scene**:

```
v4.29   FRAME#600  entries=98  atlasPackedKiB=4492  atlasFill=27%  shelfRows=1144/2048  shelfUsed=55%
v4.28   FRAME#900  entries=98  atlasPackedKiB=4492  atlasFill=27%
```

Entry for entry and byte for byte the same scene, and **55 % of the atlas was gone, not 27 %**.

### 1.3 The method: the device holds the sequence, the host compares the packers

A packer's result depends on the **order** entries arrive in, and nothing on the host knows that
order — it is whatever the scenes happen to draw. So the device was used for the one thing only it
can do, and the host for everything else:

1. a CAPTURE-ONLY build logs every `GlTextureAtlas.add()` call, in order, tagged with the atlas's
   identity so two live engines can be told apart;
2. a walk driver puts each of the twelve themes on screen through `files/capture.txt` — 45 s each,
   13:00 then 23:00, one process — and records the census alongside;
3. the recorded sequence is replayed on the host against candidate packers, which costs seconds
   instead of a twenty-minute walk each.

The harness is validated by replaying **the shipped packer** against the recorded sequence and
comparing with what the device actually did — below.

### 1.4 The candidates, replayed against the recorded max-density sequence **[M]**

```
292 add() calls, recorded on the device, v4.28's own order
shelf-next-fit (v4.28 as shipped)      placed=232  standalone=60  content=42.2%  rows=2041/2048 (99.7%)
shelf-first-fit (every row stays open) placed=243  standalone=49  content=42.7%  rows=2042/2048 (99.7%)
skyline bottom-left                    placed=292  standalone= 0  content=58.0%  rows=1505/2048 (73.5%)
```

**The obvious cheap fix does not work, and that is worth writing down.** Keeping every shelf open —
so a later short entry can use an earlier row's tail — is the one-paragraph change and the first
thing anyone will propose. It recovers almost nothing: the tails are not where most of the waste is,
the vertical slack is, and no shelf packer can see it. On an earlier partial sequence shelf-first-fit
was *worse* than what shipped (147 spilled against 142), because filling tails with short entries
leaves tall ones nowhere to go. **Recorded as measured and refused.**

### 1.5 What replaced it

`AtlasPacker`: bottom-left placement over a skyline, about eighty lines, same public surface as
`ShelfPacker` (`place`, `fitsAtAll`, `contentX/Y`, `placedCount`, `reset`) plus `occupiedHeight`,
which is the capacity number §1.2 says was missing. **Online** — no sorting, no deferred upload,
because the first upload of a sprite happens in the frame that first draws it and nothing knows the
working set in advance. Height-sorted input would pack better still and is not available here.

Cost: quadratic in skyline segments, in a call that already allocates a padded bitmap and does a
`texSubImage2D`. It runs once per sprite *and level*, never per frame.

**`AtlasPackerTest` does not trust the packer to describe itself.** It keeps an occupancy grid and
stamps every placement into it, so a corrupt skyline surfaces as a placement onto texels already
taken rather than as a plausible rectangle — the failure mode that matters, because two overlapping
entries do not throw, they just render one sprite with another's pixels inside it. And it asserts
`occupiedHeight` against that grid after *every* placement, because a packer that lost a raised
segment would under-report its own consumption in exactly the direction §1.2's error pointed.

### 1.6 The harness validated against the device **[M]**

Replaying **the shipped shelf packer** against the sequence recorded from the shipped shelf packer,
and comparing with what that same run's counters reported:

| | device counters | host replay |
|---|---:|---:|
| placed in atlas | 252 | 232 |
| standalone | 64 | 60 |
| content fill | 43 % | 42.2 % |
| rows consumed | 2 047 / 2 048 (99 %) | 2 041 / 2 048 (99.7 %) |

**Close, and deliberately not called exact.** The gap is `logcat`: the probe's own counter reported
316 entries where the log carried 292 lines for that atlas, so the replay is short about two dozen
entries and lands correspondingly short on placements. Two things follow, and both are stated rather
than smoothed:

- **the device run is the authority** for every before/after figure in §1.7, and the replay is what
  chose the algorithm;
- **the log is lossy under burst**, which is why the committed fixture in `AtlasPackerTest` is
  documented as *a* recorded sequence and not as a census.

Identity-tagging the atlas is what made even this much possible: the first attempt attributed the
wallpaper picker's short-lived preview engine and the live engine to one packer and produced numbers
that matched nothing. Two engines, 116 adds and 304.

### 1.7 Before and after, on the device **[M]**

Same driver, same 45 s dwell, same twelve themes day and night, same process, **every category
visible and every density at 1, raining and in storm** — the fullest scene the app can draw:

| end of the 24-scene walk, full density | v4.28 shelf | v4.29 skyline |
|---|---:|---:|
| entries | 316 | 317 |
| **in the atlas** | 252 | **317** |
| **standalone textures** | **64** | **0** |
| content fill | 43 % | **58 %** |
| **rows consumed** | **2 047 / 2 048 (99 %)** | **1 524 / 2 048 (74 %)** |
| standalone texture bytes | 2 432 KiB | **0** |
| atlas + standalone | 18 816 KiB | **16 384 KiB** |
| **`GL mtrack`** | **45 449 KiB** | **42 629 KiB** |
| `EGL mtrack` | 3 450 KiB | 3 450 KiB |

**`GL mtrack` falls by 2 820 KiB**, and it did not have to: the brief's instruction was to report it
either way, because a spill that disappears is real memory whatever the total does. It happens to
agree with the accounting — the probe's own atlas-plus-standalone figure falls by 2 432 KiB, and the
rest is inside the driver's own bookkeeping — which is a useful cross-check of both.

**The rows number is the one to look at.** 99 % of the atlas consumed to hold 43 % of it, against
74 % consumed to hold 58 %. The old packer was not nearly full; it was nearly *out of room*, which
is a different thing and is the distinction §1.2 says nothing in the app was reporting.

Phase A's independent census of the same walk on the same code ended at **34 standalone**, against
the 64 this pass measured. Both are the same finding — the atlas saturates and then spills for the
rest of the session — and the difference is order sensitivity: a shelf packer's result depends on
the order entries arrive in, and a walk driven by a wall clock does not reproduce that order exactly
between runs. **The skyline's result does not have this property**: it spilled zero in both this
pass's walks, with room to spare.

### 1.8 The default-density walk, which is the one that matters most **[M]**

The full-density walk is the upper bound. **This one is the out-of-the-box product**: twelve themes
at *their own default customisation* — no slider touched, clear weather — which is what the daily
and shuffle theme settings walk through on their own.

The "before" column is phase A's census, delivered in `misure/censimento_atlante_default.txt`,
taken from a separate session on v4.28's code.

| end of the 24-scene walk, each theme's own defaults | v4.28 shelf | v4.29 skyline |
|---|---:|---:|
| entries | 262 | 267 |
| **in the atlas** | 237 | **267** |
| **standalone textures** | **25** | **0** |
| content fill | 42 % | **51 %** |
| **rows consumed** | **2 045 / 2 048 (99 %)** | **1 298 / 2 048 (63 %)** |
| standalone texture bytes | 1 444 KiB | **0** |
| atlas + standalone | 17 828 KiB | **16 384 KiB** |
| `GL mtrack` | not recorded in phase A | 42 489 KiB |

And the theme-by-theme shape of it, which is what the "saturates on the fifth theme" claim rests on:

| after theme | v4.28 rows | v4.28 standalone | v4.29 rows | v4.29 standalone |
|---|---:|---:|---:|---:|
| sunset | 1 144 (55 %) | 0 | 931 (45 %) | 0 |
| winter | 1 550 (75 %) | 0 | 931 (45 %) | 0 |
| **christmas** | **2 029 (99 %)** | **1** | 1 181 (57 %) | **0** |
| spring (night), 24th | 2 045 (99 %) | **25** | **1 298 (63 %)** | **0** |

**Both acceptance criteria are met on both censuses: zero sprites outside the atlas**, against 25
at defaults and 64 at full density, with the atlas at 63 % and 74 % of its rows rather than 99 %.

---

## 2. Item 81, closed in both halves — and the second one the other way round

### 2.1 The size: 2048 is the floor, not a luxury

Item 81 recorded "**11 892 KiB allocated and empty**, which is 23 % of the whole process […] the
largest single block of unused memory in the app", and asked for a smaller atlas chosen against the
worst theme once v4.28's sprites were in.

**Both instructions were right and the answer came out inverted.** §1.2 is why the number said
what it said. Measured against the worst themes, with v4.28's sprites present, **2048 is the
smallest size that works**:

- at their **own defaults**, nothing switched on, the shelf packer saturated at **christmas, the
  fifth theme**;
- the v4.28 investigation's own 1024 probe — 68 of 98 sprites standalone on a single scene — was
  not an outlier but the same effect one step further along;
- a **non-square** atlas is genuinely one line away, because the packer takes width and height
  separately. 2048×1024 halves rows that were at 99 %. **Measured and refused**, recorded so it is
  not offered again as a free saving.

Shrinking `DEFAULT_SIZE` would have made worse the exact thing the atlas exists to prevent. The room
was recovered from the packer instead, which is where the waste was.

### 2.2 The dimension gate: dead, and kept

`GlTextureAtlas.accepts` refuses anything over `DEFAULT_MAX_ENTRY_DIMENSION = 1024`, justified by
*"The sleigh alone is 1563x434."*

- `santa_sleigh_scene` and `santa_sleigh_trot` are **594 × 123 px** — the v4.19 crop;
- **0 of 305 sprites exceed 1024** on either axis;
- over the walk — 33 000 frames, 317 entries — `rejDim` was **0**. Both before and after. What
  fired was `rejSpace`, 64 times, and that is what §1 fixed; after the fix neither fires.

**Kept**, and the comment rewritten. The argument survives its stale number: a 1024-square entry is
4 MiB of texels, a quarter of the atlas, and would push out many small sprites that repeat per
frame; a sprite that large is drawn about once per frame, so standing outside costs it one batch
break. That does not depend on any sprite existing today. The comment now says plainly that it is a
guard against a future sprite rather than a description of this one — which is the part a reader
could not previously tell, and the reason the stale number was load-bearing.

---

## 3. The ceiling that was measuring the wrong thing

### 3.1 What is wrong with one number **[O]**

`SpriteGeometryTest.decodedByteBudget` sums width × height × 4 over every shipped PNG, and its own
failure message called raising it "a decision about memory pressure and **atlas sizing**". v4.28
raised it to 32 MiB for the umbrella-carrying pose partly on that reading. Two things in the shipped
code say it is not an atlas-sizing number, and neither is new:

```kotlin
val level = SpriteDetailLevel.levelFor(transform.uniformScale())   // GlSceneTarget.kt
val reduced = reduce(bitmap, level)                                // GlTextureCache.register
```

- the copy that reaches the GPU is sized against **the scale the sprite is drawn at**, and
  `SpriteDetailLevel` targets a residual nearest 0.5, so it is always between 1.4× and 2.8× the
  drawn size **whatever the artwork does**. Authored size does not reach the GPU at all;
- `SpriteBlitter.onSpriteUploaded` then calls `SpriteCache.release`, so on the GPU path the
  authored-size bitmap is a **transient**, not a resident. Its own KDoc puts that at "~17 MB of
  heap".

Measured at the reference 1080×2340 **[M]**: the set is **31.74 MiB decoded and 17.09 MiB
uploaded**, and the two do not move together — redraw everything two thirds the size and decoded
falls to 14.11 while uploaded falls only to 13.06; for the *selective* reduction the headroom
actually permits, uploaded **rises**, because the level quantisation tips the wrong way for exactly
those sprites.

### 3.2 The part the brief asked to be reasoned rather than executed

The instruction was to repoint the ceiling at uploaded texels **unless** the old one is the only
thing protecting the `Canvas` path. It is, and the check is not a judgement call:

```
$ grep -n "CanvasSceneTarget" app/src/main/kotlin/com/paperscrape/livewallpaper/ui/ThemePreview.kt
54:    val target = remember { CanvasSceneTarget() }
```

`ThemeScenePreview` builds one **unconditionally**. So the settings and gallery previews go through
`SpriteBlitter` → `SpriteCache.get` → `BitmapFactory.decodeResource` with `inScaled = false`, at
authored size, on **every device**, GL or not — and `SpriteCache.release`'s own KDoc says the Canvas
path "needs the bitmap on every single frame and must not release it". The wallpaper itself joins
them once EGL has failed `GlLifecyclePolicy.MAX_CONTEXT_REBUILDS` times. Beside that, the decoded
figure bounds the APK and the per-sprite transient decode peak.

**So: two limits, two honest names.**

| | counts | protects |
|---|---|---|
| `SpriteGeometryTest.decodedByteBudget`, 32 MiB | authored px × 4, every PNG | the `Canvas` path's residency, the APK, the transient decode peak |
| `SpriteDrawScaleTest.uploadedTexelBudget`, **18 MiB, new** | texels actually uploaded, per sprite at the largest scale it reaches | GL texture memory: the atlas plus every standalone texture |

The new one is **18 MiB against 17 921 692 B measured**, leaving 952 676 B — the same "just above
the measured figure" convention every raise of the older one has used, for the same reason: the next
pass has to come and argue too. Both KDocs carry the argument and say plainly that they are not the
same number, and the old test's failure message now names the new one.

**This does not reopen item 85.** The A/B its conditional authorisation required was taken in v4.28
and the umbrella shipped. What changes is that the next argument about either ceiling will be about
the right one.

**Its basis is an upper bound, said once.** Every sprite at the largest scale any path draws it at is
not a scene — no frame draws 305 sprites. The device measured **9 662 KiB packed** at the end of the
full-density walk against the 17.09 MiB modelled here. The model is what a host test can compute
from the artwork alone; the device figure is in §1.7 and cannot be a test.

---

## 4. Item 82 — three stale numbers, a fourth nobody removed, and one widening that failed

### 4.1 The four **[M]**

| where | said | is |
|---|---|---|
| `GlTextureAtlas.accepts` | "The sleigh alone is 1563x434" | **594 × 123 px**, both frames |
| `GlTextureAtlas.DEFAULT_SIZE` | "~16.4 MB […] a rearrangement of that budget", "a scene fills only a fraction of it" | both halves false — §2.1 |
| `GlTextureCache` | "18.5 MiB at level 0, and 1.8 MiB once reduced" | level 0 is 31.745 MiB; the resident reduced figure is about four times the 1.8 |
| **`PaperRenderer`, two places** | "`santa_sleigh_scene` is 624x168 with a content box of (12,12)-(610,159)" and "the content box is 598px wide" | 594 × 123 px |

**The fourth is the interesting one.** It is not a discovery of this pass:
`docs/archive/SANTA_CROP_REPORT.md`'s "Remaining Findings" #1 reported it, said the block at the
first site was "an orphaned KDoc attached to nothing", judged it documentation-only, and left it.
That was v4.19. It then survived nine releases, including a documentation review whose whole subject
was documents that had stopped being true. **A finding filed as "not acted on" is not a plan**, and
that is the part worth carrying forward more than the number itself.

The orphan is removed and an accurate KDoc now sits on `SANTA_SLEIGH_SCALE`, including the thing
most likely to be "fixed" by mistake: **-99.67 is knowingly not the centre of the drawing** — it is
`-598/2/3`, from a pre-redraw content width, and the crop report assessed exactly that and recorded
the decision **not** to realign. No constant moved.

**None of the three MiB figures is replaced with a fresh MiB figure.** Re-typing is how they rotted
(item 82's own instruction: re-measure, not re-type). The comments now point at the two tests that
measure and fail — `AI_PROJECT_RULES.md` 14.11, a command wherever a number decays.

### 4.2 The guard, extended — and a widening that was measured and reverted

`SpriteMeasurementClaimTest` scanned **two hand-named files**. Every one of item 82's three was in
`GlTextureAtlas.kt` or `GlTextureCache.kt` and was simply never read. That is the same shape as the
stale golden-class list in `CLAUDE.md` §5 that cost v4.28 a missed `SkyWaterGoldenTest`: **ask the
tree, do not keep the list.** It now walks `src/main/kotlin`.

That alone does not reach the sleigh, whose claim names no unit. Making ` px` optional was tried:

```
SpriteMeasurementClaimTest > a size attributed to a named sprite is that sprite's size FAILED
  SceneObjectRenderer.kt says house_shared_window is 22x21 expected:<66x63> but was:<22x21>
```

**That is a true statement in local units about a 66 × 63 px sprite.** Both frames are in daily use
three lines apart in that file, so an unqualified `NxM` is genuinely ambiguous and no checker can
resolve it. The widening was reverted within the hour and the rule went the other way: **a pixel
claim must be written in the shape the guard reads**, and the sleigh's comments were rewritten into
it.

**What is still uncovered is recorded rather than papered over** (`BACKLOG_v4_29.md` item 90): a
pixel claim that omits its unit remains invisible, and the two proposals that would catch it are a
whitelist of every non-sprite dimension in the engine — `BACKLOG_v4_25.md` item 58's "table nobody
reads", immediately — or a naming rule for comments, which is a style change across a large file.

---

## 5. `ROADMAP.md`, and a correction to phase A

**v4.28 is published.** Read from the public API at the start of this pass **[M]**:

```
v4.28 | draft=False | prerelease=False | published=2026-09-11T17:47:11Z
        asset: PaperScrape-v4.28.apk  3 171 121 B    asset: …apk.sha256  88 B
```

`ROADMAP.md` said "v4.28 prepared — not published" and named v4.27 as the baseline. Corrected, for
the fourth recorded instance of the same failure.

**And `V4_29_FASE_A.md` says v4.28 is unpublished.** It ran the same `curl` at 16:36 UTC and was
right at that moment; the release went out at 17:47, seventy-one minutes later. That is not an error
in phase A — it timestamped its read, which is the only reason this was resolvable rather than a
third contradiction — but it sharpens the project's own rule:

> **Read the API, not the document — and record the instant you read it.** An API read is a fact
> about a moment, not a standing property. An untimestamped "not published" is indistinguishable
> from a stale document the moment it is filed.

Both the ROADMAP line and `BACKLOG_v4_29.md` item 86 now carry the instant.

---

## 6. Documents

- **`ROADMAP.md`**: v4.29 as current, v4.28 as the published baseline with its timestamp, the
  backlog inventory, and the v4.29 block.
- **`BACKLOG_v4_26.md` and `BACKLOG_v4_27.md` moved to `docs/archive/`**, with their two open items
  — **67** and **78** — carried forward by number into `BACKLOG_v4_29.md`, under the convention the
  v4.24 pass established. Item **71 is deliberately not carried**: v4.27's item 78 ratifies it in as
  many words. Every citation of either file in the sources and the older documents is a **bare
  name**, so nothing needed rewriting; `docs/archive/README.md` is where a bare name resolves and
  now lists both.
- **`BACKLOG_v4_28.md` stays in the root.** Item 83 is still open, and it carries forward fourteen
  items from the three backlogs v4.28 archived; moving it would mean re-carrying all of them for one
  open item.
- **`BACKLOG_v4_29.md`**: items 86-93.
- **`ARCHITECTURE.md`**: the packer's row, and the atlas section rewritten for the skyline and for
  the dimension gate.
- **`release-notes/v4.29.md`**: written for a release whose entire user-visible content is that
  nothing is visible.

---

## 7. Verification

**Level 3.** `app/build.gradle.kts` changes (the version bump), and this is a release candidate
whose central claim — that nothing is drawn differently — can only be settled by building and
running the instrumented suite on the device.

### 7.1 What the numbers are, and what they were

| | v4.28 | v4.29 |
|---|---|---|
| JVM unit tests | 1 382 | **1 388**, 0 failures, 0 errors |
| instrumented | 161 of 161 | see §7.4 |
| `lintDebug` | 0 errors, 28 warnings, 3 hints | **identical: 0 errors, 28 warnings, 3 hints** |
| shipped sprites | 305 | **305, unchanged** |
| committed goldens | 33 Canvas assertions over 30 PNGs, + 3 GL references | **unchanged, byte for byte** |
| decoded sprite set | 33 286 896 B | **unchanged** |

Every figure in that table came from a command, and one of them nearly did not. This report first
carried "24 Canvas assertions", copied from the shape of v4.28's; the count is **33**, over 30
committed Canvas PNGs, because two tests may assert one PNG with different focus rectangles. 24 was
true several releases ago. It is recorded here rather than quietly corrected, because a release
whose subject is load-bearing numbers that stopped being true should say when it nearly added one:

```
$ grep -rh 'SceneGolden\.assertMatches' app/src/androidTest --include=*.kt | grep -vc '^\s*\*'
33
$ ls app/src/androidTest/assets/golden/*.png | wc -l      # 30 Canvas + 3 gl-*
33
$ python3 …                                               # the shipped set, from the PNG headers
sprites: 305   decoded set: 33286896 B = 31.745 MiB   largest dimension: 798 px (cloud_body.png)
sprites over 1024 on either axis: 0
```

The JVM count moves by six: `ShelfPackerTest`'s 11 tests are replaced by `AtlasPackerTest`'s 13,
and `SpriteDrawScaleTest` — carried in from phase A — brings four, its three plus the new
uploaded-texel budget. 1 382 − 11 + 13 + 4 = 1 388. Counted by the command, not by hand:

```
$ python3 -c "…glob('app/build/test-results/testDebugUnitTest/*.xml')…"
1388 tests, 0 failures, 0 errors
```

**An earlier run in this pass reported 1 387, and the extracted copy is what caught it** — the
working tree's `--rerun-tasks` predated the recorded-walk test by twenty minutes. That is the same
trap v4.28's report records for the same reason, and the same thing caught it: 12.18 step 8 is not
a formality.

### 7.2 "No pixel changed", checked three ways

1. **At the file level**, before anything ran: `diff -rq` against a fresh extraction of the base ZIP
   shows `app/src/main/res/` and `app/src/androidTest/assets/golden/` **identical, byte for byte**.
   No artwork exists to have moved.
2. **At the source level**: `GlTextureCache.kt` and `PaperRenderer.kt` have **zero** non-comment
   changes, and `GlTextureAtlas.kt` has exactly one — the packer's type. Filtered by hand and
   reproducible:
   ```
   $ diff -u <base>/PaperRenderer.kt PaperRenderer.kt | grep '^[+-]' | grep -v '^[+-]\s*\*' …
   (no output)
   ```
3. **At the frame level**: the instrumented suite, §7.4. The packer decides *where* in the atlas an
   entry sits; each entry keeps its own one-texel transparent border and is addressed by its own UV
   rectangle, so a bilinear sample inside that rectangle cannot reach a neighbour and the rendered
   result is independent of placement. That is the argument; the goldens are the check.

### 7.3 The mutations — a green test that has never failed is not a check

Four, run against the tests written in this release, each reverted after. Every one bit.

| # | mutation | caught by |
|---|---|---|
| 1 | `AtlasPacker.place` takes the **first** fitting candidate instead of the lowest — i.e. behaves like a shelf packer | three tests, including `the space above a short entry is reused instead of being stranded` ("expected 22 but was 52") and `entries advance along the bottom row` |
| 2 | `spanTop` rests an entry on its **starting** segment instead of the tallest in the span — the classic skyline bug, and a silent one: it produces overlapping entries, not a crash | the occupancy grid, twice: `entry 9 (23x31): texel (9,5) was already occupied` |
| 3 | `uploadedTexelBudget` lowered to 17 MiB, below the measured 17 921 692 B | `the shipped sprite set stays inside the texture memory it uploads`, with the figure in the message |
| 4 | the recorded-walk fill floor raised to 59 % | `every entry of the recorded twelve-theme walk fits in the atlas`, reporting **58.406 %** — which is also how the Kotlin packer was confirmed to agree with the Python model used to choose it, to three decimal places |

Mutation 2 is the one that matters. An overlapping placement does not throw; it renders one sprite
with another's pixels inside it, in whichever scene happens to draw that pair. The occupancy grid
exists so that this cannot pass.

### 7.4 The instrumented suite, whole, once — and it is the claim

```
$ adb shell am instrument -w -r com.paperscrape.livewallpaper.debug.test/androidx.test.runner.AndroidJUnitRunner
Time: 2,602.03
OK (161 tests)
```

**161 of 161 on the BV6600, 0 failures, 43 min 22 s.** Not filtered: the intermediate rounds of this
pass used `-e class` on the packer and budget tests, and this is the single whole run 12.14 asks for.

**This is the release's central claim and the only thing that could have refuted it.** The packer
decides *where* in the atlas an entry is placed, and every golden in the suite draws through it on
the GPU path. Among the 161: 33 `SceneGolden.assertMatches` assertions over 30 committed Canvas
frames, and the 3 GL references with `GlDriverGapGuardTest` on top of them. **All passed, and no
golden was regenerated** — the assets in the instrumented APK are v4.28's own files, byte for byte
(§7.2 item 1), so a frame that had moved would have failed rather than being quietly re-baselined.

The argument that predicted it, now confirmed: each entry keeps its own one-texel transparent border
and is addressed by its own UV rectangle, so a bilinear sample inside that rectangle cannot reach a
neighbour whatever the packer did with the placement.

### 7.5 The ZIP, all eight steps of 12.18, in order

1. **built** with Python's `zipfile` — `zip` is not installed here — writing the mode into
   `external_attr` by hand, so `gradlew` keeps its execute bit (`-rwxr-xr-x` in the extraction,
   checked);
2. **extracted** into a directory sharing nothing with the working tree;
3. **completeness**: `find . -type f | sort` on both sides, **identical, 1 765 files**. Not
   `git ls-files` — an extraction has no `.git`, so that recipe silently compares nothing. v4.28
   shipped 1 761; the six added are `AtlasPacker.kt`, `AtlasPackerTest.kt`, `SpriteDrawScaleTest.kt`,
   `BACKLOG_v4_29.md`, `release-notes/v4.29.md` and this report, and the two removed are
   `ShelfPacker.kt` and `ShelfPackerTest.kt`;
4. **present**: `.gitignore`, `.github/`, `CLAUDE.md`, `AI_PROJECT_RULES.md`;
5. **absent**: `.git`, `build/`, `.gradle`, `.kotlin`, `local.properties`, `__pycache__` — the last
   deleted deliberately after running the asset tooling, which writes it on any invocation;
6. **secret scan** clean (`debug.keystore` is committed on purpose and carries no private key
   pattern);
7. **built from the extraction**: `assembleDebug` → 23 049 710 B APK; `lintDebug` → 0 errors, 28
   warnings, 3 hints;
8. **tested from the extraction**: `testDebugUnitTest` → **1 388 tests, 0 failures, 0 errors**.

**`git check-ignore` run inside a throwaway `git init` of the extraction** confirms `CLAUDE.md` is
still untracked: `.gitignore:44:CLAUDE.md`.

**Asset tooling**: 108 tests, **2 failures** — `test_nothing_in_scope_still_carries_removable_padding`
and `test_the_shipped_set_has_no_trailing_padding_left`. Both were run against a fresh extraction of
the **base v4.28 ZIP** and fail there identically, so they are pre-existing and untouched by this
release, which changed no artwork at all.

**One honesty note about the order**, because a report inside an archive cannot describe the
verification of that archive before it exists. Steps 7 and 8 were run **twice**: once on a candidate
archive, and again on an archive differing from the delivered one only in this paragraph. Both gave
**1 388 tests, 0 failures, 0 errors** and a **23 049 710 B** APK, and `lintDebug` 0 errors /
28 warnings / 3 hints both times. Steps 2-6 were then re-run on the delivered archive itself and
pass as written above. The residue — one Markdown paragraph, which nothing compiles — is stated
rather than pretended away.

### 7.6 The template

```
Release identifier:            v4.29 (versionCode 60, versionName "4.29")
Verification level:            3
Reason for the level:          app/build.gradle.kts changes (the version bump), and the release's
                               central claim -- that nothing is drawn differently -- can only be
                               settled by building and running the instrumented suite on a device.
Tests run:                     JVM 1 388, 0 failures, 0 errors (testDebugUnitTest --rerun-tasks)
                               instrumented 161 of 161 on the BV6600, 0 failures (43 min 22 s)
                               asset tooling 108, 2 failures -- both identical to the base ZIP's,
                               confirmed by running them against a fresh extraction of v4.28
Lint run:                      yes -- lintDebug: 0 errors, 28 warnings, 3 hints (identical to
                               v4.28, per rule as well as in total)
APK build run:                 yes -- assembleDebug, in the working tree and again from the
                               extracted ZIP (23 049 710 B both times). assemblePerf NOT built:
                               this release takes no CPU measurement and changes no rendering code
Static / bytecode checks:      lintDebug; SpriteGeometryTest, SpriteDrawScaleTest,
                               SpriteReachabilityTest, SpriteMeasurementClaimTest,
                               SpriteTintClassTest, SpriteCanvasConventionTest and UnitFrameTest
                               read the shipped PNGs and the Kotlin sources directly.
                               SpriteMeasurementClaimTest now walks src/main/kotlin instead of
                               reading a hand-written list of two files
Device measurement:            four twelve-theme walks (24 scenes each, 45 s dwell, one process),
                               driven by files/capture.txt rather than the UI: max density before
                               and after, defaults after, plus phase A's two as the declared
                               "before". GL mtrack paired on the max walk: 45 449 -> 42 629 KiB
Mutation testing:              yes -- four mutations, four caught, none survived (7.3). Mutation 2
                               is the load-bearing one: a silent overlap, caught by the occupancy
                               grid rather than by the packer's own bookkeeping
ZIP verification:              yes -- all eight steps of 12.18, in order. File lists identical
                               (1 765 files, `find` on both sides, not `git ls-files`);
                               .gitignore / .github/ / CLAUDE.md / AI_PROJECT_RULES.md present;
                               .git / build / .gradle / .kotlin / local.properties / __pycache__
                               all absent; gradlew keeps its execute bit; secret scan clean; and
                               `git check-ignore` in a throwaway `git init` of the *extraction*
                               confirms CLAUDE.md is still untracked
Clean build from extracted ZIP: yes -- assembleDebug (23 049 710 B APK), testDebugUnitTest
                               **1 388 tests, 0 failures, 0 errors**, lintDebug 0 errors /
                               28 warnings / 3 hints. The extracted run is what caught this report
                               quoting 1 387: the working tree's --rerun-tasks predated the
                               recorded-walk test by twenty minutes
Maintainer-side verification required:
                               publication (tag, push, GitHub Release) -- never done here;
                               item 93, the person family at a grid of 2, which is an artwork
                               decision and wants photographs;
                               item 92, deleting drawPreviewPair, which is measured as safe
Release identifier verified unique: yes -- `git tag --list` and the public Releases API both show
                               v4.28 as the newest; v4.29 does not exist
```

---

## 8. What is not done, and what is yours

**Publication.** No tag, no push, no GitHub Release. `release-notes/v4.29.md` is written and matches
the tag this release would carry. `git tag --list` and the public Releases API both show **v4.28**
as the newest; v4.29 does not exist.

**Two items left open on purpose**, with the measurement already taken so that closing them is a
decision rather than an investigation:

- **Item 93 — the person family at a grid of 2.** 202 sprites, 63 % of the decoded bytes, the only
  block the measured headroom clears, exactly convertible with no third scale convention and no
  anchor moved. It is an **artwork change** and wants photographs at the sizes things actually
  reach, which is what item 80's own third point asked for. It buys the decoded ceiling — 32 MiB
  could become 21 — and buys **nothing** on the GPU; on the model it costs there. Yours to call.
- **Item 92 — `drawPreviewPair` is dead**, and checked to be safely removable (it names no sprite of
  its own, so `SpriteReachabilityTest` is not leaning on it). Left because this release changed no
  rendering code at all, which is what makes its "no frame moves" claim cheap to believe.

**What this release did not attempt**, stated so it is not assumed:

- **No artwork, of any kind.** No PNG, no golden, no `res/` file differs from v4.28 by a byte.
- **`DEFAULT_SIZE` was not touched**, and §2.1 is the argument for not touching it.
- **The `Canvas` path's packing is unaffected** — it has no atlas. Everything in §1 is the GPU path.
- **`GlTextureCache` still never evicts.** A session's atlas only grows, and that is unchanged; the
  skyline packer has made the ceiling far enough away that it stopped being the binding constraint,
  not removed it. If a future set outgrows 2048 again, eviction — or a second atlas page — is the
  next question, and §1.4's replay harness is the cheap way to answer it.
- **`rejDim` and `rejSpace` are probe counters, not shipped ones.** The CAPTURE-ONLY build that
  produced every census in this report is **not** in the delivery; `work_v4_29_probe2/` holds it.

**The one thing to look at on a device**, if you want to look at anything: nothing should have
changed. That is the claim.
