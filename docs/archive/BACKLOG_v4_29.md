# BACKLOG_v4_29.md — what v4.29 decided, and what it left open

**Replaces `BACKLOG_v4_26.md` and `BACKLOG_v4_27.md` for new items only**, and **carries forward by
name** the two items those files still leave open — they moved to [`docs/archive/`](docs/archive/)
in this release, under the convention the v4.24 documentation pass established, and the table below
is what makes moving them safe. `BACKLOG_v4_28.md` stays in the repository root: three of its items
are closed here and one is annotated, but **item 83 is still open** and it also carries forward the
items of the three backlogs v4.28 archived, so moving it would mean re-carrying fourteen entries for
one open item. Numbering continues: `BACKLOG_v4_28.md` reached item 85, so this file starts at 86.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided
against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered), or
**OPEN** (left undone on purpose, with what closing it would take).

The measuring device is a **Blackview BV6600** (MediaTek Helio A25, PowerVR GE8320, Android 10,
720×1440). This release took **no artwork decision at all**, so unlike v4.26–v4.28 the device was
not a photographic bench: it was the only place the atlas census can be taken, because the packer's
occupancy depends on the order the scenes actually ask for sprites, and nothing on the host knows
that order. The host then replayed the recorded order against candidate packers, which is what made
choosing between three of them cost seconds instead of a twenty-minute walk each.

---

## Carried forward from the two archived backlogs

Restated here by number and one line each, so that nothing is lost by the move. The reasoning stays
in the archived file; read it there.

| item | from | what | still |
|---|---|---|---|
| 67 | `BACKLOG_v4_26.md` | The `_UNITS` frame rule sees Kotlin and not the generators | **OPEN**; the stated limit of item 61's fix, and closing it is proposal A applied to `tools/assets` |
| 78 | `BACKLOG_v4_27.md` | The cross-driver GL measurement is no longer taken anywhere | **OPEN**; a condition rather than work. It also ratifies item 71, which is why 71 is **not** carried forward |

**And `BACKLOG_v4_28.md` stays in the root**, carrying item 83 (the wave stands over more of the
lake band than its derivation assumed) plus items 18, 25, 30, 40, 50-55, 56, 58 and 63 from the
three backlogs v4.28 archived.

---

## Summary

| item | what | outcome |
|---|---|---|
| 86 | `ROADMAP.md` declared v4.28 unpublished, and so did v4.29's own phase A — an hour before it went out | **RESOLVED** — corrected against the public API, and the failure mode is now recorded in both directions |
| 87 | The atlas packer lost more than half the atlas to the tails of its rows, and never went back for any of it | **RESOLVED** — replaced with a skyline; zero spilled sprites on both censuses, against 25 and 34 |
| 88 | Item 81's second half closes **the other way round**: the atlas is not oversized, it is marginal | **RESOLVED** — 2048 is the floor; content-area fill was being read as capacity, and both numbers are now reported |
| 89 | The atlas's dimension gate rejects nothing, and the sprite it cited had not had those dimensions for ten releases | **DOCUMENTED** — kept as a guard against a future sprite, with the comment rewritten against the real set |
| 90 | Item 82's three stale numbers, and a fourth the santa crop report had already reported and nobody removed | **RESOLVED in part** — all four corrected, the guard's stale file list replaced by a directory walk; what it still cannot see is recorded |
| 91 | The decoded-sprite ceiling is not GPU memory, and item 85's raise was argued partly as though it were | **RESOLVED** — two limits with two honest names, and the argument in both KDocs |
| 92 | `SceneObjectRenderer.drawPreviewPair` is dead, and safely removable | **OPEN** — measured as safe to delete; deleting it is not this release's subject |
| 93 | The only sprite reduction the measured headroom permits, and it needs a maintainer's decision | **OPEN** — the person family, exactly convertible; item 80 is closed and this is what is left of it |

---

## 86 — `ROADMAP.md` declared v4.28 unpublished, and so did this release's own phase A

**RESOLVED**, and it is the **fourth** recording of the same failure — `BACKLOG_v4_24.md` item 55,
`BACKLOG_v4_28.md` item 79, and now twice in one day.

Read from the public API at the start of this pass:

```
$ curl -s https://api.github.com/repos/urgali/paperscrape/releases | head
v4.28 | draft=False | prerelease=False | published=2026-09-11T17:47:11Z
        asset: PaperScrape-v4.28.apk           3 171 121 B
        asset: PaperScrape-v4.28.apk.sha256           88 B
```

**v4.28 is published**, non-draft, non-prerelease, with its APK and checksum attached.
`ROADMAP.md`'s "Current status" said "v4.28 prepared — not published and not approved" and named
v4.27 as the baseline. Corrected.

**What is new here, and worth more than the correction.** `V4_29_FASE_A.md` ran the same `curl`,
at 16:36 UTC, and recorded — correctly, at that moment — *"v4.28 is not published yet."* The
release went out at 17:47, seventy-one minutes later. So the rule this project has been writing
down since v4.24 needs its second half said explicitly:

> **Read the API, not the document — and record the time you read it.** An API read is a fact about
> an instant, not a standing property. A phase report that says "not published" without a timestamp
> becomes indistinguishable from a stale document the moment it is filed.

Phase A did timestamp its read, which is the only reason this was resolvable rather than a third
contradiction. Both the ROADMAP line and this item now carry the instant.

---

## 87 — The atlas packer lost more than half the atlas to the tails of its rows

**RESOLVED.** This is the defect v4.29 was opened to find, and it was hiding behind the number
item 81 was reading.

### What was wrong

`ShelfPacker` packed first-fit rows and kept **one** row open: entries filled a row left to right
until one did not fit, then a new row opened below the tallest entry of the row just closed. Its own
class comment said so and dismissed it:

> It wastes more area than a real bin packer, and it cannot reuse the space of an entry that is no
> longer wanted. Both are acceptable here and neither is worth the code it would take to fix: the
> set is small […]

The second half of that — no reuse after eviction — is still true and still does not matter, because
`GlTextureCache` never evicts a single entry. **The first half was measured in v4.29 and it is wrong
by more than a factor of two.** A shelf packer loses two things and goes back for neither:

- **the tail of every closed row**, because only one row is ever open, and
- **the slack above every entry shorter than the tallest one in its row.**

### What it cost, on the device

Twelve themes, **each at its own default customisation — no slider touched, clear weather** — at
13:00 and again at 23:00, walked in one process, with `capShelfRowsUsed` reporting the rows the
packer had consumed alongside the content-area ratio v4.28 had been reading:

| after theme | entries | standalone | content fill | rows used |
|---|---:|---:|---:|---:|
| sunset | 98 | 0 | 27 % | 1 144 / 2 048 (**55 %**) |
| winter | 171 | 0 | 34 % | 1 550 (75 %) |
| **christmas** | 221 | **1** | 42 % | **2 029 (99 %)** ← saturated, the fifth theme |
| spring (night), the 24th scene | 262 | **25** | 42 % | 2 045 / 2 048 (99 %) |

**The atlas fills up on the fifth theme with nothing switched on**, and from there every new sprite
the session meets becomes a texture of its own — a potential batch break per frame each, which is
precisely what the atlas exists to prevent. At full density the same walk ends at **34** spilled.
Content fill never passed 44 % while rows passed 99 %: **the gap is the shelf waste, and it is over
half the atlas.**

### The obvious fix does not work, and that is why it is written down

Keeping **every** shelf open — so a later short entry can use an earlier row's tail — is the
one-paragraph change and it is the first thing anyone will propose. Replayed against the recorded
insertion sequence it is **not better and sometimes worse**:

```
275 add() calls, the recorded max-density sequence
shelf-next-fit (v4.28)                 placed=232  standalone= 43  content=42.2%  rows=99.7%
shelf-first-fit (all rows stay open)   placed=240  standalone= 35  content=42.6%  rows=99.7%
skyline bottom-left                    placed=275  standalone=  0  content=52.7%  rows=66.6%
```

Row tails are not where most of the waste is; the vertical slack is, and no shelf packer can see it.
On an earlier partial sequence shelf-first-fit was *worse* than what shipped (147 spilled against
142), because filling tails with short entries leaves tall ones with nowhere to go. **Recorded as
measured and refused**, so it is not proposed again as the cheap option.

### What replaced it

[`AtlasPacker`](app/src/main/kotlin/com/paperscrape/livewallpaper/engine/AtlasPacker.kt): bottom-left
placement over a skyline, about eighty lines, **online** — no sorting and no deferred upload, which
matters because the first upload of a sprite happens in the frame that first draws it and nothing
knows the working set in advance. Height-sorted input would pack better still and is not available.

`AtlasPackerTest` keeps an **occupancy grid** and stamps every placement into it, so a corrupt
skyline shows up as a placement onto texels already taken rather than as a plausible-looking
rectangle; and it asserts `occupiedHeight` against that grid after *every* placement, because a
packer that loses a raised segment would under-report exactly the way v4.28's diagnostics did.

The numbers before and after, on the device and on the host replay, are in
`release-verification/V4_29_REPORT.md`.

---

## 88 — The atlas is not oversized; it is marginal, and one number was being read as another

**RESOLVED**, and it closes the second half of `BACKLOG_v4_28.md` item 81 **in the opposite
direction to the one that item proposed.**

Item 81 recorded: the atlas allocates 16 384 KiB and the reference scene fills 4 492 KiB of it, so
"**11 892 KiB is allocated and empty**, which is 23 % of the whole process […] the largest single
block of unused memory in the app."

**The measurement was right and the conclusion was not.** `atlasFill` is `capPlacedBytes /
capAllocatedBytes` — the padded content rectangles over the whole texture. A shelf packer's waste
lives in the space it has consumed and not filled, and that ratio cannot see any of it. Adding a
counter for the rows actually consumed and re-running **the investigation's own scene** gives:

```
v4.29   FRAME#600  entries=98  atlasPackedKiB=4492  atlasFill=27%  shelfRows=1144/2048  shelfUsed=55%
v4.28   FRAME#900  entries=98  atlasPackedKiB=4492  atlasFill=27%
```

Entry for entry and byte for byte the same scene — and **55 % of the atlas was already gone**, not
27 %. Slightly under half was free in that scene, and the other eleven themes eat it: see item 87.

**So `DEFAULT_SIZE = 2048` is the floor, not a luxury.** Shrinking it would make worse the exact
thing the atlas exists to prevent, and the v4.28 investigation's own 1024 probe — 68 of 98 sprites
standalone on a single scene — was not an outlier but the same effect one step further along. A
**non-square** atlas is a line away, because the packer takes width and height separately, and is
refused for the same reason: 2048×1024 halves rows that were already at 99 %.

`GlTextureAtlas.DEFAULT_SIZE`'s KDoc now says this, and deliberately does **not** re-type the
figures: it points at the two tests that measure them and fail when they move.

**The instruction not to resize in v4.28 was right, and for the reason it gave** — "only against the
worst theme, and only with the new sprites in". That is exactly what turned the answer over.

---

## 89 — The dimension gate rejects nothing, and is kept anyway

**DOCUMENTED**, closing the first half of `BACKLOG_v4_28.md` item 81.

`GlTextureAtlas.accepts` refuses any sprite over `DEFAULT_MAX_ENTRY_DIMENSION = 1024` px, justified
by: *"The sleigh alone is 1563x434."* Measured on the shipped set:

- `santa_sleigh_scene` and `santa_sleigh_trot` are **594 × 123 px** — the v4.19 crop in
  `SANTA_CROP_REPORT.md`;
- the largest single dimension anywhere is `cloud_body`'s, and **0 of 305 sprites exceed 1024**;
- over the twelve-theme walk — 33 000 frames, 314 entries — the dimension rejection fired **0**
  times. What fired was the space rejection, and item 87 is why.

**Kept, not deleted**, and the argument is the one the original comment made rather than the number
it made it with: a 1024-square entry is 4 MiB of texels, a quarter of the whole atlas, and would
push out many small sprites that repeat per frame; a sprite that large is drawn about once a frame,
so standing outside the atlas costs it a single batch break. That reasoning does not depend on any
sprite currently existing. The comment now says plainly that it is **a guard against a future
sprite, not a description of this one**, which is the part a reader could not previously tell.

---

## 90 — Item 82's three stale numbers, a fourth nobody removed, and what the guard still cannot see

**RESOLVED in part.** `BACKLOG_v4_28.md` item 82 named three load-bearing comments whose numbers had
rotted, and said what closing it would take: *"re-measure, not re-type"*, and extend item 63's guard.
Both done, and a fourth was found on the way.

### The four

| where | said | is |
|---|---|---|
| `GlTextureAtlas.accepts` | "The sleigh alone is 1563x434" | **594 × 123 px**, both frames |
| `GlTextureAtlas.DEFAULT_SIZE` | "~16.4 MB the whole sprite set would occupy as individual textures", therefore the atlas is "a rearrangement of that budget" and "a scene fills only a fraction of it" | both halves false — see item 88 |
| `GlTextureCache` | "18.5 MiB of texture at level 0, and 1.8 MiB once reduced" | level 0 is 31.745 MiB, and the resident reduced figure is about four times the 1.8 |
| **`PaperRenderer`, twice** | "`santa_sleigh_scene` is 624x168 with a content box of (12,12)-(610,159)" and "the content box is 598px wide" | 594 × 123 px. **This was already reported** — `SANTA_CROP_REPORT.md`'s "Remaining Findings" #1 called it stale, said it was documentation-only, and left it. It then survived nine releases |

The first of those was an **orphaned KDoc attached to nothing** — the crop report said that too. It
is removed, and an accurate one now sits on `SANTA_SLEIGH_SCALE`, including the thing most likely to
be "fixed" by mistake: **-99.67 is knowingly not the centre of the drawing**, and the crop report
records the decision not to realign it.

**None of the three MiB figures is replaced with a fresh MiB figure.** Re-typing is how they rotted.
The comments now point at `SpriteDrawScaleTest.uploadedTexelBudget` and
`SpriteGeometryTest.decodedByteBudget`, which measure and fail — `AI_PROJECT_RULES.md` 14.11, a
command wherever a number decays.

### The guard, and the limit that is still there

`SpriteMeasurementClaimTest` scanned **two hand-named files**, `SceneObjectRenderer.kt` and
`PaperRenderer.kt`. Every one of item 82's three was in `GlTextureAtlas.kt` or `GlTextureCache.kt`
and was simply never read. That is the same shape as the stale golden-class list in `CLAUDE.md` §5,
which cost v4.28 a missed `SkyWaterGoldenTest`: **ask the tree, do not keep the list.** It now walks
`src/main/kotlin`.

**What it still cannot see, stated rather than papered over.** The pattern requires the unit:
`` `name` … is NxM px ``. The sleigh's "is 624x168 with a content box of …" names no unit and is
invisible to it. v4.29 **tried** making ` px` optional and put it back within the hour, because the
looser pattern immediately mis-read "`house_shared_window` is 22x21" — a true statement in **local
units** about a 66 × 63 px sprite. Both frames are in daily use three lines apart in that file, so
an unqualified NxM is genuinely ambiguous and a checker cannot resolve it.

So the rule went the other way: **a pixel claim must be written in the shape the guard reads**, and
the sleigh's comments were rewritten into it. The residue is real and is this item's open half — a
pixel claim that omits its unit is still invisible, and the only proposals that would catch it are a
whitelist of every non-sprite dimension in the engine (`BACKLOG_v4_25.md` item 58's "table nobody
reads", immediately) or a naming rule for comments, which is a style change across a large file.

---

## 91 — The decoded-sprite ceiling is not GPU memory, and one raise was argued as though it were

**RESOLVED**, and it corrects the premise under `BACKLOG_v4_28.md` item 85 rather than its decision.

`SpriteGeometryTest.decodedByteBudget` sums width × height × 4 over every shipped PNG, and its
failure message said raising it "is a decision about memory pressure and **atlas sizing**". It is
not a decision about atlas sizing, and v4.28 raised it to 32 MiB — for the umbrella-carrying pose —
partly on that reading. Two things in the code say why, and neither was new in v4.29:

- `GlTextureCache.register` uploads `reduce(bitmap, SpriteDetailLevel.levelFor(scale))`, sized
  against the scale the sprite is about to be **drawn** at. `SpriteDetailLevel` picks the level whose
  residual lands nearest 0.5, so the copy on the GPU is always between 1.4× and 2.8× the drawn size
  **whatever the artwork does**. Authored size does not reach the GPU.
- `SpriteBlitter.onSpriteUploaded` releases the decoded bitmap the moment the upload succeeds, so on
  the GPU path the authored-size pixels are a **transient**, not a resident. Its own KDoc says this
  was worth "up to ~17 MB of heap".

Measured at the reference viewport, the shipped set is **31.74 MiB decoded and 17.09 MiB uploaded**,
and the two do not move together: reduce the whole set to two thirds and decoded falls to 14.11
while uploaded falls only to 13.06 — and for the *selective* reduction the headroom actually permits
(item 93), uploaded **rises**, because the level quantisation tips the wrong way for exactly those
sprites.

### Why the old one was not simply repointed at texels

Because it is the only thing measuring a path that every device takes. **The `Canvas` backend is not
a fallback only.** `ThemePreview.kt` builds a `CanvasSceneTarget` unconditionally, so the settings
and gallery previews decode sprites at their authored size and keep them for every frame —
`SpriteCache.release`'s own KDoc says the Canvas path "needs the bitmap on every single frame and
must not release it". The wallpaper joins them once EGL has failed `MAX_CONTEXT_REBUILDS` times.
Beside that, this ceiling bounds the APK and the per-sprite transient decode peak.

**So: two limits, two honest names.** `decodedByteBudget` keeps its name, its value and its whole
history, with a rewritten argument; `SpriteDrawScaleTest.uploadedTexelBudget` is new at **18 MiB
against 17 921 692 B measured**, the same "just above the measured figure" convention every raise of
the older one has used. Each KDoc carries the argument and a table of which limit protects what, and
both say plainly that they are not the same number.

**Item 85's decision stands.** The A/B measurement its authorisation required was taken and the
umbrella shipped; nothing here reopens that. What changes is that the *next* pass to argue about
either ceiling will be arguing about the right one.

---

## 92 — `SceneObjectRenderer.drawPreviewPair` is dead, and safely removable

**OPEN.** Found in v4.29's phase A and confirmed here:

```
$ grep -rn "drawPreviewPair" app/src/main app/src/test app/src/androidTest --include=*.kt
SceneObjectRenderer.kt:1053:  * The preview strip height the fitting factors in [drawPreviewPair] …
SceneObjectRenderer.kt:2160: fun drawPreviewPair(canvas: SceneCanvas, screenWidth: Float, …
```

One reference, and it is the KDoc of the constant the function itself uses. No call site since
`ThemeScenePreview` replaced the strip. `drawPreviewItem` and `PREVIEW_REFERENCE_HEIGHT_PX` go with
it.

**Checked, because the obvious hazard is real:** `SpriteReachabilityTest` fails any shipped PNG no
source file names, so dead code can be load-bearing by accident. It is not here — `drawPreviewPair`
calls only `drawSmallHouse`, `drawTree` and `drawSkyscraperBuilding`, all of which the real scene
also calls, and it names no sprite of its own. **Deleting it is safe.**

Not done here because it is not this release's subject and this release changed no rendering code at
all, which is what makes its "no golden moves" claim cheap to believe. It is a five-minute item for
whatever pass next opens that file.

---

## 93 — The only sprite reduction the measured headroom permits, and it is the maintainer's call

**OPEN**, and it is what is left of `BACKLOG_v4_28.md` item 80 after that item was closed and
rejected.

**Item 80 is closed: a global grid of 2 would magnify a third of the set.** `SpriteDrawScaleTest`
measures, for every one of the 305 sprites, the pixels it is drawn with against the pixels in its
file. At the reference 1080×2340 the minimum is **0.448** — `rainbow_arc` is drawn 2.2× larger than
its own artwork today — and **50 of 305 sit below the 1.5** a grid of 2 needs merely to stay at 1:1:
palms at 1.011, skyscrapers at 1.045, shop fronts at 1.049, trees at 1.115, the sun and moon discs
at 1.010, and every sprite the gallery preview draws. The 3× oversample the item assumed was spare
**has already been spent**, by the size table and the viewport growing underneath it over many
releases. The constant's own KDoc — "authored at 3x the size it is drawn at on screen" — is false
for a third of the set.

**What the measurement does clear**, and the arithmetic is exact, which is what makes it a proposal:
the **person family**, 202 of the 305 sprites and **63.0 % of the decoded bytes**, at headroom
3.93–5.48. All three canvases are multiples of 3 on both axes, so two thirds of each is a whole even
number and the local units are unchanged:

| canvas | today | at grid 2 | local units |
|---|---|---|---|
| walk / carry | 117 × 252 | **78 × 168** | 39 × 84, unchanged (132 sprites) |
| bust in a car | 114 × 126 | **76 × 84** | 38 × 42, unchanged (38 sprites) |
| bust behind a window | 147 × 171 | **98 × 114** | 49 × 57, unchanged (32 sprites) |

So `PERSON_ANCHOR_Y_UNITS`, `WINDOW_HEAD_ANCHOR_*`, `HEAD_CAR_ANCHOR_*` and the co-registered-family
assertion all keep their values. **And it needs no third scale convention** — the hazard `CLAUDE.md`
§6 warns about: all 202 go through exactly three call sites that already apply a scale of their own
(`drawPerson`, `drawWindowOccupant`, `drawSeatedOccupant`), and multiplying those three by 1.5 puts
the smaller artwork in the same pixels. `SpriteBlitter` is untouched and `SpriteScale` keeps its two
members.

**What it buys, and what it does not.** The decoded set goes to 20.635 MiB and the *decoded* ceiling
could come down from 32 MiB to 21 — the first time that number would ever move down. It buys
**nothing on the GPU**, and on the model it costs: see item 91. So this is an APK, `Canvas`-path and
headroom decision, not a texture-memory one, and it should be argued as that.

**Two things travel with it**, and neither is a detail:

- **`umbrella_canopy` must move too.** It is blitted inside `drawPerson`'s transform with no scale of
  its own, so it follows whatever that call site does. Its headroom is the pedestrian's, so it can.
- **`SpriteGeometryTest`'s grid rule becomes a rule with two grids in it**, which is a real cost in a
  test whose whole value is that it states one.

**Not started, and not to be started without a decision**, because it is an artwork change: it needs
the photographs at the sizes things actually reach, which is what item 80's own third point said.
