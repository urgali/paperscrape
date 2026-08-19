# ROADMAP.md

**The authoritative operational plan for PaperScrape.** This file decides what
gets worked on next. Read it at the start of every significant task.

It is deliberately *not* a technical description or a changelog:

| Question | File |
|---|---|
| What do I work on next, and why that order? | **`ROADMAP.md`** (this file) |
| How does the code actually work today? | `ARCHITECTURE.md` |
| What shipped, when, and what broke? | `RELEASE_HISTORY.md` |
| What are the visual/design rules? | `DESIGN_NOTES.md` |
| What rules always apply, regardless of task? | `AI_PROJECT_RULES.md` |
| How do I set up this environment and build? | `CLAUDE.md` (local, untracked) |
| What was the original plan and its reasoning? | `ROADMAP_OLD.md` (archived) |

`ROADMAP_OLD.md` is the project's first roadmap, kept verbatim for its
historical reasoning and for ideas not yet scheduled. It is superseded by this
file. Where they disagree, this file wins.

⚠️ **The two files use different, unrelated numbering.** `ROADMAP_OLD.md` has
its own "Phase 0–5"; this file uses the group/phase numbering established by the
audit (1.1, 1.5, 3.2, …). A bare phase number in an old source comment,
changelog entry or release note refers to the **old** scheme. When citing a
phase, name the file.

---

## Current Status

| | |
|---|---|
| **Current project release** | **v1.0** — **Stable / latest**. The first public release |
| **Latest stable Android release** | **v1.0** (`versionCode = 1`, `versionName = "1.0"`) |
| **Android version** | `versionCode = 1`, `versionName = "1.0"` — **reset for the first public release.** The sequence up to 76 was an unreleased project's internal build numbering and meant nothing to anyone installing it. Two consequences: Android will not install a lower `versionCode` over a higher one, so a device carrying an earlier internal build must uninstall first, which clears its saved settings and custom themes; and CI's tag check requires a stable `vNN` tag to equal `versionCode`, so the stable tag here is **`v1`** |
| **Current phase** | **Shipped.** Groups 1, 2, 3, 4, 7.1 and 9 are complete; Group 4 closed at v76.7 after three device passes. Two polish batches followed: v76.11 (pedestrian parallax, Live Weather fallback, opt-in update check) and v76.12 (snow on buildings, People controls, star field, lake lanes) |
| **Current state** | The scene is drawn with OpenGL ES 2.0 on a per-engine render thread through the `SceneCanvas` abstraction, with the `Canvas` backend retained for the settings preview and as fallback. **118 sprites**, every one with an SVG source and a committed pipeline, no byte-identical pair. `SceneSpace` is the single source of truth for the ground plane, the perspective, the road, the pavement and the size of every category, and every size is derived from a declared real height |
| **Next action** | **None approved.** v1.0 is released. The open backlog is Group 5's two remaining items, Group 6 (Material 3, localisation, theme previews), Group 7 cleanup, defects D-7 and D-10, and the star-field/mountain hotspots from the CPU audit. Do not start any of them without asking |
| **Work folded into v1.0** | Everything: the whole history in `RELEASE_HISTORY.md`, from the first entry through v76.12 |
| **Last verified tests** | `./gradlew testDebugUnitTest` — **330 tests, 0 failures, 0 errors** (measured at v76.12, whose code v1.0 is) |
| **Last verified lint** | `./gradlew lintDebug` — **41 warnings, 0 errors, 0 fatal** (v76.12, unchanged baseline) |
| **Last verified build** | **`assembleDebug` has not been run since v75**, at the maintainer's explicit instruction, and no APK was produced. Compilation is proven by `testDebugUnitTest`, which compiles the whole `debug` source set; resource linking, dexing and packaging are **not** proven |
| **Default verification level** | **Level 2** — `test` + `lintDebug` + relevant static checks. Every release from v76 onward was Level 2 by instruction; where a change would normally have justified Level 3, the gap is recorded in that release's own `RELEASE_HISTORY.md` entry rather than glossed |
| **Release identifier scheme** | `vNN` stable, `vNN.N` beta/pre-release. Everything up to **`v76.12`** is taken, and **`v1.0`** is this release. Determined from `RELEASE_HISTORY.md` and `release-notes/`, never from memory (§11.B) — **note:** the ZIP carries no `.git`, so confirm against real tags before tagging. **No tag has been created for any release from v73.5 onward; the maintainer creates them.** For v1.0 the stable tag is `v1` |
| **Forbidden reference name** | 0 occurrences (text, binary, filenames) — re-scanned at v1.0 |

**`versionCode` was reset to 1 at v1.0.** It had reached 76 as an unreleased
project's internal build sequence, which meant nothing to anyone installing the app.
`AI_PROJECT_RULES.md` §11.2 forbids changing the Android version merely because the
project release identifier advanced; this is the exception the same rule allows,
because the reset was the point of the release rather than a side effect of it.

The reset is not free and the cost falls on the maintainer, not the code. Android
refuses to install a lower `versionCode` over a higher one, so **a device carrying any
earlier internal build must uninstall before installing v1.0 — and uninstalling
clears the DataStore, which is where settings and custom themes live.** Back up
anything worth keeping first.

CI's tag check requires a stable `vNN` tag to equal `versionCode`, so the stable tag
for this release is **`v1`**. `v1.0` is the dotted form the workflow classifies as a
pre-release; the ZIP carries that name because it was asked for, and no tag was
created either way.

### Maintainer-side verification on real hardware

Claude has no device or emulator (see the limitations below). The following was
verified **by the maintainer, on a real device**, and is recorded here so a fresh
session knows which claims rest on observation rather than on reasoning:

| Phase | Verified on device | Outcome |
|---|---|---|
| **2.1 / 2.2** | Clouds do not jump or snap while the density slider moves | ✅ confirmed |
| **2.1 / 2.2** | Cloud distribution reads as natural | ✅ confirmed |
| **2.1 / 2.2** | Rain, snow and birds read as naturally distributed | ✅ confirmed |
| **2.2b** | Rain is coherent with cloud cover — no visible precipitation over clear sky | ✅ confirmed |
| **2.3** | Every sprite still renders at the size, position and colour it did before the helper unification | ✅ confirmed — no visual difference from the previous release; sprites, rain and snow all still render correctly |
| **2.4** | Left edge, right edge, slow swipe, objects crossing the wrap seam, and any case with two copies simultaneously visible | ✅ confirmed — wrapping checked at both edges during a slow swipe; no pop, gap, duplicate or scale change observed |
| **9.5** (v74) | GPU renderer: visual parity, day and night, CPU load | ✅ confirmed — Pixel 9, ~357 MHz against ~2600 MHz, no visual anomalies |
| **3.4 / 3.5 / 3.6** (v74.2) | Sprite deduplication: houses, windows and planters; summer characters; winter characters; a full Summer → Winter → Summer switch | ✅ confirmed — Pixel 9, no regression |
| **3.7** (v76) | The V2 asset library on a device: every category, day and night, both seasons | ⚠️ **done — four defects found**: the moon cut vertically, car-driver heads below the window, snow not covering the crown, the fire truck reading as a car. All four fixed in v76.1 |
| **3.7** (v76.1) | The same pass again, plus the four fixes | ⚠️ **done — eight more defects found**: cars and Santa driving backwards, car glass off the body, police livery drawn on the road, birds in threes and too small, and all four cars sharing one lane. All fixed in v76.2 |
| **3.7** (v76.2) | The same pass a third time | ⚠️ **done — six more defects found**: reindeer legs frozen, traffic still bunching, birds reading as bats, snowman arms through his head, Santa not cozy enough, car glass not centred. All fixed in v76.3 |
| **3.7** (v76.3) | The same pass a fourth time | ⚠️ **done — seven more defects found**: lopsided road strip, off-centre facade windows, two-tone mountains, gliding shark-like dolphins, detached sail, Live Weather needing a restart, one occupant per car. All fixed in v76.4 |
| **3.7** (v76.4) | The same pass a fifth time, plus the colour behaviours D7 asks about | ⏳ **not done — overtaken by Group 4, which changes what the pass would be looking at** |
| **Group 4** (v76.5) | The whole scene's proportions, perspective, road geometry and placement | ⚠️ **done — structure confirmed correct; a tuning list of nine items returned**, all addressed in v76.6 |
| **Group 4 tuning** (v76.6) | The tuned proportions, the narrowed road and the snowman rim | ⚠️ **done — dolphins and sailboats confirmed correct; five further items returned**, all addressed in v76.7 |
| **Group 4 tuning** (v76.7) | The pedestrian band, the road's position, the tree lights and the parasols | ⏳ **not yet done — this is what the v76.7 ZIP is for** |

Phases 2.3 and 2.4 are therefore **closed by maintainer verification**: both were
predicted to be visually identical, and both were confirmed on real hardware.
Phase 2.2b's own residual risk — "the fix has not been observed running" — is
likewise **closed by maintainer verification**. Two questions raised at the time
remain open only in the sense that no defect was reported against them: whether
the coverage edge reads as abrupt, and whether drops appearing mid-fall as a
cloud arrives (decision D11) is noticeable. Neither was flagged.

Phases 3.4, 3.5 and 3.6 are therefore **closed by maintainer verification**. The
deduplication was predicted to be pixel-identical — the removed files were
byte-identical to the ones that remain, so the bitmap reaching each blit is the
same object — and the seasonal switch in both directions is the case that would
have exposed a wrong entry in the walk-frame or head lookup tables. Neither showed
a regression.

**`balloon_basket` drawing white (D-6) is closed in v76**, by the artwork rather
than by a code fix: the V2 basket is drawn in wicker browns instead of shipping as
a white mask.

**v76 itself has been seen by nobody.** It replaces every sprite in the app and
changes what a dozen call sites draw, and the only evidence behind it is unit
tests, lint and reading the artwork off disk. The device pass in the row above is
not a formality here.

Still outstanding for the maintainer at some future point: practical CPU,
battery and thermal observation of the cumulative Phase 1 and Phase 2 work. v74.1's
three lake-decoration colours have been seen in the scene but were never judged
against a mockup; if any reads wrong, each is a single named `const val`.

### Known verification limitations

These apply to **every** phase completed so far and must be repeated honestly
in each release entry:

- **Claude has no emulator or physical device.** 1 CPU, no KVM. Claude has never
  observed this project rendering on screen, and must never write as though it
  had. "No visual change" claims from Claude rest on unchanged draw inputs,
  unchanged draw order, unit tests and an unchanged APK — never on an observed
  frame. Device confirmation comes from the maintainer, and is recorded in the
  table above when it happens.
- **No screenshot or frame-comparison tooling** is in use, so visual regression
  cannot be detected automatically.
- **No OpenGL implementation is available in this environment either.** Since v73.11
  the wallpaper's normal path is a GL renderer, so the gap is wider than it was: the
  GPU backend classes cannot be executed here at all, only compiled and read. Any
  claim about what the GPU renderer draws is an argument about code, never an
  observation.
- **The environment resets between sessions.** JDK, Android SDK and Gradle
  caches must be reinstalled each time (~5 min + build time). Procedure in
  `CLAUDE.md` §3.
- **A full build is slow here**: ~12 min cold `assembleDebug`, ~4 min
  `lintDebug`, ~2–3 min for an incremental test run. Budget accordingly; do not
  start a build near the end of a session.

---

## Completed Work

All complete, verified, and approved by the maintainer. Full detail per item is
in `RELEASE_HISTORY.md`.

| Item | Title | Outcome |
|---|---|---|
| **7.1** | Persistent documentation | Created `AI_PROJECT_RULES.md`, `ARCHITECTURE.md`, `DESIGN_NOTES.md`, `RELEASE_HISTORY.md`, and `CLAUDE.md` (local, untracked, in `.gitignore`) |
| **7.6** | Forbidden reference name removal | Reworded 3 release-notes files; global scan clean across text, binaries and filenames |
| **Phase 0** | Verification foundation | First 50 unit tests; `schemaVersion` added to custom-theme JSON with backward-compatible migration; reproducible environment setup documented; CI now runs real tests |
| **Phase 1.1** | Tint filter cache | `TintFilterCache` + `IntLruSlots`; removed per-blit `PorterDuffColorFilter` allocation at 3 sites |
| **Phase 1.2** | Depth sort out of the render loop | Sort moved into runtime-list construction; removed a per-frame list + comparator allocation |
| **Phase 1.3** | Viewport-based culling | Replaced the hardcoded `-200f/3000f` skip with a viewport- and extent-aware predicate; also fixed two real culling bugs (clipping at the left edge, culling visible objects on displays wider than 3000px) |
| **Phase 1.4** | Slider / preferences flow | `PreferenceSlider` commits once per drag; `SceneCustomization` structural-vs-cosmetic comparison; renderer updated in place instead of rebuilt; cars no longer restart on an unrelated slider |
| **Phase 1.5** | Bounded time base | `SceneTime` accumulates in `Double` and bounds at the point of use; `scrollProgress` narrows only its wrapped per-layer result. Closes the 12.14-day animation freeze |
| **Phase 1.6** | Memory pressure response | Tiered `onTrimMemory` policy with LRU eviction; removed a per-blit `Integer` boxing allocation missed by the Phase 1.1 audit. RGB_565 evaluated and rejected on measurement |
| **Phase 2.1 / 2.2** | Deterministic addressed candidates | Fixed candidate pools with index-addressed attributes; density became linear and stable; removed the last seven per-frame `Random` allocations; effects decorrelated |
| **Phase 2.2b** | Rain/cloud coherence | Precipitation reads a local density derived from actual cloud coverage, so rain no longer falls from clear sky. Reported from device testing |
| **Phase 2.3** | Unified sprite helpers | One `SpriteBlitter` replaces six copies of the same blit across two renderers; `SPRITE_PIXELS_PER_UNIT` has a single definition; the scale convention became a named `SpriteScale` argument; one dead helper removed |
| **Phase 2.4** | Per-object tile culling | The fixed three-copy loop became a range derived from the geometry; per-object setup runs once instead of three times; `anchorPosition`'s `Pair` and `drawRoad`'s list (F1) removed — 4 allocations per frame per object gone |
| **Phase 3.1** | Asset source pipeline | `tools/assets/`: SVG sources, deterministic version-pinned rasterisation with a verified toolchain probe, a registry covering all 118 shipped sprites, geometry recovery by measurement, and fidelity reporting. 24 sprites given a source (11 pixel-identical, 13 edge-equivalent); 94 declared gaps. No runtime PNG modified |
| **Phase 3.2** | Asset manifest | Registry schema 2: `contentBox` declared for all 118 sprites, `anchorRule` + `anchor` for 17, each of the 101 undetermined ones with a stated reason. `callsites.py` resolves blit call sites from the Kotlin sources so `validate` can compare `scale`, `tint` and the determined anchors against the code — the check D-1 did not have. Unresolvable call sites are reported as unresolved, never as agreement. Tooling-side only: nothing under `app/` changed |
| **Defect D-1** | Two sky sprites drawn at the wrong scale and anchor | `star_sparkle.png` is authored at the 3x oversample and is now blitted as `SCENE_UNITS`, restoring v72's geometry; `sun_glow.png` is a raw-pixel sprite and is now anchored at `-222` so its ray ring lands outside the disc. Neither PNG was regenerated: the artwork was always correct, the two numbers paired with it were not. Six named constants now carry each sky sprite's origin and convention, so `SkySpriteAnchoringTest` can read them against the PNG headers. Not part of Group 3 |
| **`scrollBackground` bugfix** | Sun, moon and stars leaving the screen | The sky layer wrapped one shift and drew a single copy of everything in it. Wrapping without tiling turned a permanent disappearance into a periodic one. The star field is now tiled on its real period (one screen width, range derived from the geometry); the sun and moon get a bounded, non-cyclic offset capped by the slack their own rest position leaves to the left edge. Not part of Group 3 |

**Cumulative effect:** unit tests 0 → 235; per-frame allocations in the sprite
draw path 3 → 0 and in the static-object draw path 3 per object → 0, plus the
road strip's list (all verified by `javap` before/after); full scene rebuilds
during a slider drag ~30 per drag → 0 or 1 depending on what changed; sprite blit
implementations 6 → 1; tile copies prepared per static object per frame 3 → 1.77
average, of which 0.77 are painted.

---

## Current / Next Work

### CPU / rendering performance work (out of band)

Not a roadmap phase. It started from a device observation the maintainer made on
v73.9 — all seasonal and non-seasonal elements on, the rain/snow stutter that was
perceptible in v73.8 gone, but CPU still high — and produced a static audit of
the frame loop with a ranked hotspot list. **The audit's numbers are operation
counts and bytecode facts, never measured CPU shares: there was no profiler and
no device in the session that produced it.**

**Batch 1 shipped in v73.10.** Five fixes, all approved individually, all of them
an allocation or a redundant state write removed from a per-frame path with the
drawn result unchanged:

1. `drawChristmasLights` — an `intArrayOf`, an array of six boxed
   `Pair<Float, Float>` and the `List` its `.map` produced, rebuilt once per tree
   and per palm per wrap-tile per frame. Now three hoisted fields; 14 `Float`
   boxes per call gone.
2. `drawPrecipitation` — `style`/`strokeWidth`/`strokeCap` rewritten identically
   up to 90 times a frame, hoisted out of the loop. Geometry and candidate
   selection untouched.
3. `drawClouds` — three `floatArrayOf` tier tables allocated every frame, moved
   to the companion object.
4. `SunPositionCalculator.currentHour24()` — `Calendar.getInstance()` **and** the
   `TimeZone.getDefault()` clone feeding it, both per frame, for a value that
   changes 1,440 times a day. Now computed from the epoch and memoised on the
   minute.
5. `lakeTopBottomY()` — a `Pair<Float, Float>?` (two boxed floats) built twice
   per frame, replaced by two fields and a boolean.

**The audit's remaining hotspots are recorded and NOT approved.** Do not pick one
up without asking. In the audit's own priority order: the star field's ~1,890
Canvas calls per frame; the star-field tile pass; the mountains' two Paths rebuilt
per mountain per frame (~48 `drawPath`, ~816 `lineTo`); the lake's tile −1, which
is provably always off-screen; three `LinearGradient`/`RadialGradient` allocated
per frame; the cloud loop's 123 candidate-tile evaluations for ~30 blits; the
per-frame `RectF`s and palette arrays in `drawRainbow`/`fallLeafColorFor`/
`drawFlowerDots`; and the frame scheduling.

**Two of those carry an explicit warning.** The frame scheduling and
`onOffsetsChanged` were written deliberately against a perceived stutter, so
changing them needs device verification, not a static argument. Anything that
changes a tile-copy count changes what is visible at a screen edge and needs
visual approval.

### Group 9 — GPU renderer migration

**Complete, shipped in v73.11 and v74.** Not a Phase-3 item: it was taken
out of order, ahead of 3.4, because the device evidence pointed at the rendering
architecture rather than at any individual hotspot. The maintainer's v73.9 measurement on
a Pixel 9 showed PaperScrape holding the mid CPU cluster far higher than a comparable
GPU-rendered wallpaper even on a minimal scene, and the conclusion taken was that further
micro-optimisation of a software rasteriser was not the right investment.

| # | Title | Status |
|---|---|---|
| 9.1 | `SceneCanvas` seam; `Canvas` backend retained for preview and fallback | **Complete (v73.11)** |
| 9.2 | EGL 2.0 context, per-engine render thread, 30 fps pacing, fallback on failure | **Complete (v73.11)** |
| 9.3 | One shader program, texture-keyed batching, premultiplied blending, `MULTIPLY` tint | **Complete (v73.11)** |
| 9.4 | Gradients as explicit stops (sky, hill highlight, celestial glow) | **Complete (v73.11)** |
| 9.5 | Device verification of visual parity and CPU/battery/thermal | **Complete (v73.11)** — Pixel 9, ~357 MHz vs ~2600 MHz, no visual anomalies |
| 9.6 | Texture atlas, to batch across sprite boundaries | **Complete (v74)** |
| 9.7 | Release the decoded bitmap once the GPU holds the sprite | **Complete (v74)** |

**9.5 came back clean**, which is what promoted the renderer from experiment to default and
released 9.6 to proceed. It remains true that no automated test in this project observes a
rendered frame and that this environment has no GL implementation, so every future renderer
change carries the same gate: it is not proven until it has been seen on a device.

**What 9.6 bought.** It turned out to be a renderer change rather than an asset-pipeline one:
the atlas is packed at runtime from the sprites already shipped, so nothing in `tools/assets/`
or the manifest was touched. Packing the flat-fill white pixel in alongside the sprites is
what actually removed the batch breaks — a scene object's solid details no longer end the
batch between its sprite parts.

### DEFERRED → Phase 3.4

**Phases 3.1, 3.2 and 3.3 are all complete**, as are the two interruptions: the
`scrollBackground` bugfix (v73.6) and defect D-1 (v73.7). **3.4 is now queued behind
Group 9's device verification (9.5)** — it was the next approved task until the GPU
migration was taken ahead of it, and it is deferred rather than cancelled.

Group 3 continues at **3.4 — deduplicate byte-identical sprites**. What 3.3
leaves it:

- **The duplicate picture has been re-measured, not inherited.** The set still
  reports **16 duplicate groups**, the same count as before the crop, but they are
  groups of cropped files now. Any earlier list of duplicate *bytes* is stale;
  `paperscrape-assets inventory` regenerates it.
- **Deduplication is a `usage` question before it is a file question.** Two
  sprites being byte-identical says nothing about whether the two call sites that
  draw them should collapse — that is the seasonal-variant question 3.5 has to
  answer under decision D2, and 3.4 must not pre-empt it by deleting a file whose
  variant is meant to diverge later.

Group 3 still **blocks Group 4**: the perspective work cannot start until assets
can be regenerated with normalised padding and declared metadata. Blocker B1 is
**partially lifted** — a pipeline exists and 24 sprites can be regenerated — but
the sprites Group 4 actually needs (people, vehicles, buildings, decorations) are
still among the 94 gaps, so Group 4 remains blocked in practice. 3.5 is
additionally blocked on decision D2.

### Then, in order

| Order | Phase | Group |
|---|---|---|
| 1 | **2.1–2.4** — Rendering / architecture | Rendering |
| 4 | **3.1–3.6** — Asset source pipeline | Assets |
| 5 | **4.1–4.4** — Perspective and scaling | Proportions |
| 6 | **5.1–5.5** — Vehicles, people, animation | Scene content |
| 7 | **6.1–6.4** — UX / UI | Interface |
| 8 | **7.2–7.8** — Cleanup and documentation | Housekeeping |

Group 8 items are individually small and may be pulled forward opportunistically
when they touch a file already being worked on — except 7.5 (dependency
upgrade), which must be taken alone.

---

## Full Roadmap

### Why this order

Four dependencies were established by the audit and must be preserved:

1. **Per-frame performance fixes come before larger architectural changes.**
   They are independent, carry no visual risk, and are immediately verifiable
   with the build that already works. Doing them first also means later,
   riskier work is not debugged against a noisy baseline.
2. **The asset source pipeline comes before perspective/scaling.** Unifying the
   scale system means re-anchoring every sprite to its real bounding box, which
   requires being able to *regenerate* assets with normalised padding and
   declared metadata. Reversing this order means hand-calibrating ~102 sprites
   and then redoing it once the pipeline exists.
3. **Perspective/scaling comes before extensive visual calibration.** Tuning
   sizes on top of a broken scale system produces per-asset patches, which is
   exactly the failure mode that produced five such patches across v67–v73.
4. **Vehicle/animation work comes after the scene-space work.** Integrating
   people and vehicles into depth, ground anchoring and road geometry is only
   meaningful once there is a single scene-space model to integrate them into.

UX/UI work sits after the rendering and asset foundations because the settings
screen surfaces the very controls those phases change.

### Group 1 — Performance

| # | Title | Status |
|---|---|---|
| 1.1 | Tint filter cache | ✅ Complete |
| 1.2 | Depth sort out of the render loop | ✅ Complete |
| 1.3 | Viewport-based culling | ✅ Complete |
| 1.4 | Slider / preferences flow | ✅ Complete |
| 1.5 | Bounded time base | ✅ Complete |
| 1.6 | Memory pressure response | ✅ Complete |

### Group 2 — Rendering / architecture

| # | Title | Status |
|---|---|---|
| 2.1 | Deterministic addressed effect candidates | ✅ Complete |
| 2.2 | Stable per-candidate seeding | ✅ Complete (same change as 2.1) |
| 2.2b | Rain/cloud coherence follow-up | ✅ Complete (device-reported defect) |
| 2.3 | Unify the four duplicated sprite helpers | ✅ Complete |
| 2.4 | Per-object tile culling | ✅ Complete |

### Group 3 — Asset source pipeline *(blocks Group 4)*

| # | Title | Status |
|---|---|---|
| 3.1 | Rebuild and commit asset generators | ✅ Complete |
| 3.2 | Asset manifest: bbox, anchor, scale, category, tintability | ✅ Complete |
| 3.3 | Normalise padding and grid | **Complete (v73.9)** |
| 3.4 | Deduplicate byte-identical sprites | ✅ **Complete (v74.2)** |
| 3.5 | Real summer/winter head variants (**decision D2**) | ✅ **Complete (v74.2)** — D2 resolved as a declared gap; the artwork itself is still missing |
| 3.6 | Test asserting variants actually differ | ✅ **Complete (v74.2)** |
| 3.7 | Integrate the V2 asset library | ✅ **Complete (v76)** — 111 sprites, all with a source; B1 lifted |
| 3.8 | Fix the four defects the device found against the V2 artwork | ✅ **Complete (v76.1)** — moon, car-driver heads, snow cap, fire truck |
| 3.9 | Placement, direction and count cleanup after the V2 integration | ✅ **Complete (v76.2)** — vehicle and sleigh facing, two traffic lanes, seven sprite origins, bird count |
| 3.11 | Road geometry, facade placement, lake life, Live Weather path, car passengers | ✅ **Complete (v76.4)** — roof snow refused as artwork work, see D-8 |
| 3.10 | Animation, traffic behaviour and two redrawn sprites | ✅ **Complete (v76.3)** — reindeer trot, per-lane speed and phase-preserving loop, gull, cozy Santa, snowman arms, car glass |

### Group 4 — Perspective and scaling ✅ COMPLETE (v76.5)

| # | Title | Status |
|---|---|---|
| 4.1 | `SceneSpace`: single source of truth for depth → position and scale | ✅ **Complete (v76.5)** |
| 4.2 | Revisit the depth range | ✅ **Complete (v76.5)** — full 0..1 band, 2.75× against 1.51× |
| 4.3 | Re-anchor every category to declared bounding boxes | ✅ **Complete (v76.5)** |
| 4.4 | Document the relative proportion table in `DESIGN_NOTES.md` | ✅ **Complete (v76.5)** — §5 |
| 4.5 | Final proportion and readability tuning against a device pass | ✅ **Complete (v76.6)** |
| 4.6 | Pedestrian band, road position, tree lights, parasols | ✅ **Complete (v76.7)** |

### Group 5 — Vehicles, people, animation *(depends on Group 4)*

| # | Title | Status |
|---|---|---|
| 5.1 | Integrate people into depth and road geometry | ✅ **Complete (v76.11)** — the projection in Group 4, the parallax and wrap-tiling here |
| 5.2 | Visibility + density controls for people (**decision D3**) | ✅ **Complete (v76.12)** — D3 resolved in practice: people became a category for visibility and density only, with no colour controls, because their artwork is finished |
| 5.3 | Independent per-vehicle-type toggles | Planned |
| 5.4 | ~~Minimum spacing between cars~~ | ❌ **Removed** — one speed per lane plus even loop slots already guarantee it; nothing overtakes |
| 5.5 | Wire up or remove the 3 orphan `road_*` sprites | Planned |

### Group 6 — UX / UI

| # | Title | Status |
|---|---|---|
| 6.1 | Complete the Material 3 colour scheme | Planned |
| 6.2 | Extract hardcoded strings into `strings.xml` | **Started (v76.11)** — the Live Weather and update strings only, ~10 of ~70. The rest is a mechanical pass worth its own change |
| 6.3 | Make the update check opt-in | ✅ **Complete (v76.11)** |
| 6.4 | Improve theme previews | Planned |

### Group 7 — Cleanup and documentation

| # | Title | Status |
|---|---|---|
| 7.1 | Persistent documentation | ✅ Complete |
| 7.2 | Remove orphan PNGs, dead strings and colours | Planned |
| 7.3 | Reword the personal handle in source comments | Planned |
| 7.4 | Consolidate stacked/contradictory KDoc | Ongoing, per file |
| 7.5 | Version catalog + Dependabot + dependency upgrade (**decision D5**) | Blocked on D5 |
| 7.6 | Forbidden reference name removal | ✅ Complete |
| 7.7 | Bring `README.md` back in sync | Planned |
| 7.8 | Lint cleanup (`UseKtx`, `ObsoleteSdkInt`, `DataExtractionRules`) | Planned |

---

## Phase Details

Effort levels: **High** is the default for focused work in a known area.
**Extra** is for phases touching multiple systems or carrying real regression
risk. **Max** is an escalation only — use it when a phase has stalled or an
audit has found something unexpected, not as a starting point.

### Phase 1.5 — Bounded time base ✅ COMPLETE

- **Objective:** stop all time-driven animation from quantising and eventually
  freezing during long uptime.
- **Scope:** `PaperEngine.elapsedSeconds`, `PaperRenderer.scrollProgress` and
  the conversion boundary between them. Every animation consumes the result.
- **Root problems addressed:** `elapsedSeconds` is an unbounded `Float`;
  measured to quantise visibly from ~5–6 days and stop advancing entirely at
  12.14 days. `scrollProgress` converts an unbounded `Double` back to `Float`
  at read time, reintroducing the same failure mode at the point of use.
- **Dependencies:** none.
- **Expected risk:** medium. The wrap period must divide the periods of every
  time-driven animation exactly, or a visible jump appears at the wrap instant.
  That, not the accumulator change itself, is the hard part.
- **Visual approval required:** no — provided the wrap is genuinely seamless.
  If a seamless common period cannot be found and per-effect wrapping is
  needed instead, stop and report before implementing.
- **Recommended effort:** **Extra**.
- **Status:** ✅ complete. Resolved without any wrap period: `SceneTime`
  accumulates in `Double` and bounds at the point of use. No common wrap period
  exists — see `ARCHITECTURE.md` and `SceneTime`'s own doc comment for the proof.

### Phase 1.6 — Memory pressure response ✅ COMPLETE

- **Objective:** stop the wallpaper being a preferred low-memory-killer target.
- **Scope:** `SpriteCache`, `TintFilterCache`, `PaperWallpaperService`.
- **Root problems addressed:** 31.8 MB of bitmaps held for the process
  lifetime with no `onTrimMemory`/`onLowMemory` response; 11 sprites carry an
  alpha channel they do not use.
- **Dependencies:** none. The largest win (trimming 17.5 MB of transparent
  padding) belongs to Group 3, so expect partial improvement here.
- **Expected risk:** medium — an over-aggressive eviction policy causes a
  visible re-decode when returning to the foreground. Needs thresholds, not a
  blanket clear.
- **Visual approval required:** no.
- **Recommended effort:** **High**.
- **Status:** ✅ complete. Tiered `onTrimMemory` policy plus LRU eviction.
  **RGB_565 was evaluated and rejected**: only 11 of 118 sprites are fully
  opaque, worth 0.63 MB of 31.8 MB (2%), and all of them are large flat wall
  surfaces where 5-6-5 banding shows worst under the runtime `MULTIPLY` tint.
  Revisit only if Group 3 changes the asset mix.

### Phase 2.1 / 2.2 — Deterministic addressed candidates ✅ COMPLETE

- **Objective:** stop rebuilding the whole effect layout every frame, and stop
  density changes from teleporting the entire field.
- **Scope:** the six draw functions in `PaperRenderer` that allocate a seeded
  `Random` per frame — clouds, precipitation, falling leaves, birds, mountain
  layers, lake decorations.
- **Root problems addressed:** scene layout treated as a per-frame computation
  rather than as state; the RNG stream coupled to the density filter, so
  changing density (or an hourly live-weather refresh) reshuffles every
  remaining member instead of adding or removing members.
- **Dependencies:** none technically, but do these together — fixing the
  allocation without fixing the seeding leaves the teleport bug in place.
- **Expected risk:** high. Changing the seeding **changes the current
  arrangement** of clouds, rain, birds and leaves in every theme.
- **Visual approval required:** **yes — mockup required before implementing.**
- **Recommended effort:** **Extra**.
- **Status:** ✅ complete. Implemented **without** precomputed arrays or
  invalidation logic: addressed noise made both unnecessary. Density is now
  linear over a fixed pool (**decision D6**), per-cloud drift was kept
  (**decision D9**), small pools keep at least one element (**D7**), and falling
  leaves were included for the allocation fix only (**D8**).

### Phase 2.3 — Unify the sprite helpers ✅ COMPLETE

- **Objective:** one sprite-blitting path instead of four near-identical ones.
- **Scope:** `drawSprite`/`drawTintedSprite`/`…Raw` duplicated across
  `PaperRenderer` and `SceneObjectRenderer`.
- **Root problems addressed:** two incompatible scale conventions selected by
  which helper the caller happens to use, making a 3× size error silent.
- **Dependencies:** none, but the *full* fix (carrying the scale convention in
  asset metadata) needs Group 3.
- **Expected risk:** low if it stays a pure deduplication.
- **Visual approval required:** no.
- **Recommended effort:** **High**.
- **Status:** ✅ complete. `SpriteBlitter` owns the one `blit`; the convention is
  a `SpriteScale` argument. `SceneObjectRenderer` draws in one convention only
  and binds it in two one-line wrappers, so its 60 call sites are untouched;
  `PaperRenderer` mixes both conventions and therefore names the scale at each of
  its 12 call sites. `SPRITE_PIXELS_PER_UNIT` went from two hand-synchronised
  definitions to one. `drawSpriteRaw` was found to have **no callers** and was
  removed. Carrying the convention in asset metadata is still Group 3's job.

### Phase 2.4 — Per-object tile culling ✅ COMPLETE

- **Objective:** stop preparing three tile copies of every static object each
  frame when at most one or two can be seen.
- **Scope:** `SceneObjectRenderer.draw`, `anchorPosition`, `drawStaticObject`;
  one placeholder line in `PaperRenderer`.
- **Root problems addressed:** the copy count was a hardcoded `-1..1` rather
  than a value derived from the viewport and the object's own extent (the same
  defect class Phase 1.3 fixed for `-200f/3000f`), which forced the scale and
  extent the cull depends on to be recomputed per copy; and `anchorPosition`
  returned a `Pair<Float, Float>` whose second element was the caller's own
  input, allocating three times per object per frame.
- **Dependencies:** none.
- **Expected risk:** medium — a bound error would pop at the wrap seam, which
  cannot be observed in this environment.
- **Visual approval required:** no; equivalence is proven by comparison against
  the previous enumeration rather than by eye.
- **Recommended effort:** **High**.
- **Status:** ✅ complete. Both loop bounds are pure companion functions
  (`firstVisibleTileOffset`, `tileOffsetLimit`) so the enumeration is what the
  tests exercise — the first mutation run survived precisely because the tests
  had reimplemented the loop instead. Painted output is bit-identical to the
  three-copy version across 76,608 swept cases. **F1** (the road strip's
  per-frame list) was folded in at the maintainer's approval.

### Phase 3 — Asset source pipeline

- **Objective:** make every shipped asset reproducible from a committed
  generator, with declared metadata.
- **Scope:** the generators (lost, must be rebuilt), an asset manifest, and the
  118 PNGs in `res/drawable-nodpi/`.
- **Root problems addressed:** the project's largest structural risk — the PNGs
  are their own source. Downstream: 18.3 MB of transparent padding, 16
  byte-identical duplicate groups, no declared bounding boxes (so every anchor
  offset is a hand-tuned constant), 5 sprites off the 3× grid, and a v73 feature
  (seasonal head variants) that ships with no visible effect because the
  variants are identical files.
- **Dependencies:** none. **Blocks Group 4.**
- **Expected risk:** high — regenerating sprites changes pixels.
- **Visual approval required:** **yes**, for 3.3 and 3.5.
- **Recommended effort:** **Extra**.
- **Status:** **3.1 complete** (v73.5). Delivered as decision **D12 option C**, a
  staged hybrid: build the pipeline, reconstruct only what measurement actually
  determines, and record the rest as gaps rather than redrawing them and calling
  it a recovery. 24 of 118 sprites have an SVG source; 94 are declared gaps with
  a stated reason. No runtime PNG was touched.
- **Status:** **3.2 complete** (v73.8). Registry schema 2 declares a `contentBox`
  for all 118 sprites and an `anchorRule`/`anchor` for 17; the other 101 anchors
  are `UNDETERMINED` with a stated reason, because an origin is
  `placement - anchor` and fixes an anchor only for a sprite that is an object
  rather than a part of one. `validate` now also compares `scale`, `tint` and the
  determined anchors against the Kotlin sources, and reports unresolvable call
  sites as unresolved rather than passing. The manifest stays tooling-side: no
  Kotlin reads it, and nothing under `app/` changed.
- **Status:** **3.3 complete** (v73.9). 76 of the 118 shipped PNGs cropped to
  their normalised content boxes and their 35 call-site origins compensated in the
  same change: 33.37 MB → 17.20 MB decoded, **16.17 MB recovered**. The rule is
  one sentence — the union of a co-registered group's content boxes, rounded
  outward to the sprite's own grid — and the outward rounding is what keeps the
  origin compensation an exact integer instead of a resampled sub-pixel offset.
  Composed rendering verified unchanged across 109 before/after composites, 0
  differing pixels. Excluded by decision and untouched: the palm-frond pair
  (off-grid canvas, shared fractional origin), the moon phases (the group rule
  refuses the crop on its own), the orphan drawables (no call site to compensate)
  and all anchor semantics. 3.4–3.6 not started; 3.5 blocked on decision D2.

### Phase 4 — Perspective and scaling

- **Objective:** one source of truth for how depth maps to screen position and
  scale.
- **Scope:** `PaperRenderer` depth constants, `SceneObjectCatalog` base scales,
  `SceneObjectRenderer` anchoring, `GLOBAL_OBJECT_SCALE`,
  `SPRITE_PIXELS_PER_UNIT`.
- **Root problems addressed:** four multiplicative scale factors with no single
  owner; geometry constants spread across three classes; a depth range of only
  2.36× across the whole scene. This is the mechanism that produced five
  per-asset size/alignment patches across v67–v73.
- **Dependencies:** **requires Group 3.** Satisfied in v76.
- **Expected risk:** **highest in the plan.** Touches the position and size of
  every object.
- **Visual approval required:** **yes** — and it has not been given. The
  maintainer directed an implementation-first pass; the composition was checked
  against a mockup built from the real sprites at the real numbers, which is not
  the same as a device.
- **Status:** ✅ **complete (v76.5)**, delivered as one pass rather than one
  category at a time, at the maintainer's instruction.

### Phase 5 — Vehicles, people, animation

- **Objective:** bring people and vehicles fully inside the scene systems.
- **Scope:** people placement, vehicle toggles, car spacing, orphan road
  sprites.
- **Root problems addressed:** people sit outside the depth, ground-anchoring
  and parallax systems entirely — fixed screen-height anchor, fixed scale, no
  road awareness, no visibility or density control. Same failure class as the
  v73 "cars outside the road strip" bug.
- **Dependencies:** **requires Group 4** for 5.1.
- **Expected risk:** medium.
- **Visual approval required:** **yes** for 5.1 and 5.5.
- **Recommended effort:** **High**.
- **Status:** not started. 5.2 blocked on decision D3.

### Phase 6 — UX / UI

- **Objective:** finish the Material 3 adoption and make the UI localisable.
- **Scope:** `PaperScrapeTheme`, `themes.xml`, `SettingsScreen.kt`,
  `strings.xml`, `UpdateChecker`.
- **Root problems addressed:** 4 M3 colour roles defined out of ~30, so
  switches, inactive slider tracks, containers and dialogs render in Material's
  baseline violet; `stringResource` has zero usages while ~71 literals are
  hardcoded, so the UI is not localisable by construction.
- **Dependencies:** 6.2 must precede any localisation work.
- **Expected risk:** low technically; 6.1 is visually broad.
- **Visual approval required:** **yes** for 6.1 and 6.4.
- **Recommended effort:** **High**.
- **Status:** not started.

### Phase 7 — Cleanup and documentation

- **Objective:** remove dead weight and keep the documentation honest.
- **Scope:** orphan resources, source comments, README, lint, dependencies.
- **Dependencies:** 7.5 must be taken **alone** — the Compose BOM jump from
  `2024.10.01` is the single largest compatibility risk in the plan and must not
  be bundled with other changes.
- **Expected risk:** low, except 7.5 (medium-high).
- **Visual approval required:** no.
- **Recommended effort:** **High**.
- **Status:** 7.1 and 7.6 complete; the rest planned.

---

## Pending Decisions

Open questions for the maintainer. **Do not resolve these unilaterally.** Also
recorded in `DESIGN_NOTES.md` §12.

| ID | Decision | Status | Blocks |
|---|---|---|---|
| **D1** | The README states the project is not a decompilation of any third-party product; source comments state the opposite in 23 places. Resolve by rewording the comments behaviourally, by aligning the README, or by leaving it and documenting the discrepancy. | **Deferred by the maintainer. Recorded, no action to be taken.** | 7.7; the wording of the reference-usage rule |
| ~~**D2**~~ | ~~Should summer/winter head sprites actually differ?~~ **Resolved in v74.2: yes, they should, and the artwork does not exist.** The seasonal distinction was found to work already on the *walking* sprites — winter has a beanie, long sleeves, a snowflake motif, and trousers where the summer girl has a skirt — and to have never been drawn for the heads. Person art has `source.kind = "none"` throughout, so drawing a winter head is asset redesign, not regeneration. The six pairs are therefore declared `IDENTICAL_GAP` in the registry, the runtime lookup tables stay two columns wide so drawing the sprites is the whole fix, and `validate` plus `SpriteVariantTest` fail the moment the artwork arrives, so the gap closes itself rather than being forgotten. | **Resolved — v74.2; the artwork arrived in v76.** The V2 asset library draws all six winter heads, so every variant group is `DISTINCT` and no byte-identical pair remains anywhere in the shipped set. | — |
| ~~**D3**~~ | ~~Do people become a fully customisable category (visibility + density + colours), or stay ambient with a single toggle?~~ **Resolved in v76.12: neither, exactly.** People got visibility and density through the existing generic category storage, and no colour controls at all -- the walk sprites are finished art in four kinds across two seasons, so a tint has nothing to reach and swatches that did nothing would be worse than none. | Resolved -- v76.12 | -- |
| **D4** | Is the `MULTIPLY` tint's colour-fidelity trade-off acceptable? Accepted in v66, never confirmed on a real device. | Open | any tint work |
| **D5** | When to take the dependency upgrade — before or after the rendering work? | Open | 7.5 |
| **D7** | **The V2 asset library retires four user-visible colour behaviours, and the retirement needs a device look.** Sprites that were greyscale masks the runtime coloured are now finished art, so: **Sun Color** reaches only the ambient glow, not the disc or the sunburst; the theme's star colour reaches nothing; **Fall Colors** does not reach palm fronds; skyscraper windows no longer light per building on a pseudo-random roll; firework bursts no longer vary in hue. All five follow from `DESIGN_NOTES.md` decision 25 and were approved as intentional consequences of the redesign. **What is not decided is whether each reads well.** If the fixed sun clashes with a theme, or a green palm looks wrong under Fall Colors, the fix is artwork or restoring a mask for that one sprite — never a tint over the new art, which is the error decision 25 exists to prevent. | Open — awaiting device verification | any change to `sun_body`, `sun_glow`, `star_sparkle`, `palmtree_fronds`, `skyscraper_wall_lit`, `firework` |
| **D12** | What "rebuilt generator" means: reproduce today's pixels, re-author everything, or a staged hybrid. | **Resolved — option C (staged hybrid), and SVG as the source format.** Implemented in Phase 3.1 | 3.1 (closed) |

---

## Known Defects

Defects that are understood, reproduced and **not yet fixed**. A defect leaves
this table only when a release closes it, and the release entry in
`RELEASE_HISTORY.md` says how.

| # | Defect | Where | Status |
|---|---|---|---|
| ~~**D-1**~~ | ~~Two sky sprites drawn at the wrong scale and anchor.~~ **Closed in v73.7.** Kept here because the entry as first written was wrong in a way worth remembering: it said both sprites needed `SpriteScale.SCENE_UNITS`. That was right for `star_sparkle.png` (192×192 = 64×3, a 3× redraw that landed while the call site kept the raw-pixel convention) but wrong for `sun_glow.png`, which is a raw-pixel sprite carrying the *origin* an oversampled one would want — read as `SCENE_UNITS` its ray ring would sit at 50..66 units, entirely hidden behind the disc's 120. Two symptoms that looked identical had different causes, and only measuring the artwork separated them. | `engine/PaperRenderer.kt` | **Closed — v73.7** |
| ~~**D-3**~~ | ~~**Dolphins and sailboats do not render.**~~ **Closed in v74.1.** Kept here because the entry as first written was wrong in an instructive way. It recorded two candidate causes — a silent `register()` failure versus UV coordinates pointing at the wrong atlas region — and **both were GPU-side, while the same entry stated the defect predates the GPU renderer.** A cause that only exists inside the GPU backend cannot explain a defect that predates it; the contradiction sat in this table for a release. The real cause was in the artwork: `dolphin_body.png` and `sailboat_hull.png` hold exactly one colour, pure white, over every opaque pixel, and `sailboat_sail.png` is greyscale mottling on white — the *tintable* authoring profile — while all three were blitted untinted because `DESIGN_NOTES.md` §3 classifies them as fixed-art. White is the `MULTIPLY` identity, so they drew as white shapes. The maintainer's device screenshot is what settled it: correct silhouettes mean correct alpha, which means correct UVs. | `engine/PaperRenderer.kt` (`drawLakeDecorations`) | **Closed — v74.1** |
| ~~**D-8**~~ | ~~**No snow settles on house, shop, bar or tower roofs in the winter and Christmas themes.**~~ **Closed in v76.12.** Five roof caps drawn in the V2 language, each cut to the roof it lies on and cresting above it, blitted untinted between the roof and whatever stands out of the drift. The tint shortcut this entry rejected was not used. Original text: Reported from a device against v76.3. It is **not** a placement bug or a lost call site: the V2 asset set simply has no roof snow for those five building types. Trees work because they have `tree_canopy_snowcap`, a fixed-art cap cut to the crown's own outline; nothing equivalent exists for a pitched house roof, a restaurant, a bar or a skyscraper. Fixing it means drawing a cap per roof shape and anchoring each one, which is artwork with a visual approval attached. **The shortcut is wrong and was rejected:** tinting the roof masks toward white in winter repaints the whole roof rather than settling snow on it, and `winterColorsEnabled` is already a palette override, so it would be indistinguishable from a theme colour change. | `res/drawable-nodpi/`, `engine/SceneObjectRenderer.kt` | **Open — needs artwork and approval** |
| ~~**D-9**~~ | ~~**Three sprites are blitted one local unit above the ground line their own content bottom implies**~~ **Closed in v76.9 — two different causes behind one symptom.** `snowman_body` and `bunny_body` genuinely floated and were corrected at the call site, a whole drawing at a time so their parts kept their registration. `penguin_body` was correct all along: the penguin stands on `penguin_feet`, blitted separately at the ground, so its body is supposed to sit above it — the fault was declaring the body `CONTENT_BOTTOM_CENTRE` when it is a part. `bunny_body` is a part too, for the same reason plus a deliberate horizontal offset putting its ears over its head. Both reclassified `PART_LOCAL`; asset `validate` is at 0 failures. Original text: — `bunny_body`, `penguin_body`, `snowman_body`. Consistent across three unrelated sprites, so an authoring convention rather than drift, most likely to keep an antialiased bottom row off the ground shadow. Two units on screen at the scene's current scale. Correcting it means editing a blit origin in the renderer. Pinned by a tooling test that fails if any of them moves by something other than one unit. | `engine/SceneObjectRenderer.kt` | **Open — low impact, not scheduled** |
| **D-10** | **35 sprites still carry croppable transparent padding.** **Attempted and withdrawn in v76.9.** `normalize --apply` aborts partway, on `bar_sign`: cropping a `PART_LOCAL` sprite moves its content relative to the local zero its parent composes against, so the crop rule and the anchor model disagree for exactly the sprites that make up most of the set. It had already cropped a run of PNGs before aborting; the set was restored from the v76.8 ZIP and re-verified at 113 files. **Reconciling the two is design work on the anchor rules, not a mechanical pass**, and every sprite it touches needs its blit origin compensated in the same change, with a device look. It buys memory only. Needs its own task. Original text:, 2.84 MB of the 15.76 MB decoded total. The V2 asset library replaced almost the whole set and never went through Phase 3.3's normalisation pass. Cropping is not a standalone change: every crop shifts content inside its own box, so each needs its blit origin compensated in the same commit, and a mistake there is a visibly misplaced sprite. Pinned as a count by `test_normalize`. | `tools/assets/`, `res/drawable-nodpi/` | **Open — needs a device look** |
| **D-7** | **The shipped sprite set is rendered by two different rasterisers.** The V2 library's 108 untouched PNGs came from its own canvas-2D tool; the four regenerated in v76.1 (`moon_crescent`, `moon_gibbous`, `tree_canopy_snowcap`, `firetruck_body`) came from the project's own pinned `resvg_py`, verified by `probe`. The difference is confined to antialiased edge pixels — geometry, size and content boxes are unaffected, and nothing about it is visible at runtime. **What it breaks is the tooling's own comparison:** `paperscrape-assets compare` will report the 108 untouched sprites as differing from their sources, because they were never rendered by this rasteriser in the first place. Re-rendering the whole set through the pinned toolchain would make the registry self-consistent and would also change 108 PNGs at once, which needs its own decision and its own device look. **Not scoped, not scheduled.** | `tools/assets/`, `res/drawable-nodpi/` | **Open — not scheduled** |
| ~~**D-4**~~ | ~~**The asset tooling's call-site resolver has been blind to `SceneObjectRenderer.kt` since v73.11.**~~ **Closed in v76.8.** The wrapper's first parameter type is now a set rather than the literal `Canvas`, because what identifies a wrapper is that its first parameter is the drawing surface, whichever type currently names one. Fixing it exposed 131 validation failures, as this entry predicted: three were bugs in the validator itself — a missing unit conversion in each direction, and the anchor check being applied to `PART_LOCAL` and `DECLARED_ATTACHMENT` sprites that are placed by their parent rather than by their own anchor. Registry data corrected against the shipped PNGs. `validate` 131 → 3; the Python suite 13 → 3. Original text: `callsites._wrapper_bindings` recognises a wrapper only when its first parameter type is literally `Canvas`; the GPU migration changed both of that file's wrappers to take `SceneCanvas`, so all ~60 of its blit call sites stopped resolving — silently, and with no failure of its own. Two of the 59 Python tooling tests fail as a result (`bar_door` "declares an anchor with no call site"; `driverRes` missing from the unattributed list), and the declarations Phase 3.2 built the resolver to check are unverified for that file. Confirmed present in an untouched `PaperScrape_v74.zip` extraction, so it is not a v74.1 regression. **Deliberately not fixed inside the D-3 fix:** repairing it re-exposes ~60 call sites to comparison at once, and those findings need triaging as their own task. `PaperRenderer.kt` resolves correctly, so v74.1's manifest change was verified against real resolution. | `tools/assets/paperscrape_assets/callsites.py` | **Open — not scheduled** |
| ~~**D-6**~~ | ~~**The hot-air balloon's basket draws white.**~~ **Closed in v76 by the V2 asset library, not by a code fix.** The basket was a pure-white mask blitted untinted, so the `MULTIPLY` identity left it white; the V2 artwork draws it in wicker browns. The same applies to the five other sprites the v75 re-measurement found sharing the profile: `bunny_tail`, `car_window` and `firetruck_ladder` all carry colour now, and the orphans `house_wall` and `house_trim` no longer exist. The general fix is `DESIGN_NOTES.md` decision 25 — a sprite's tint class is a property of its bytes, so an all-grey PNG blitted untinted now fails `SpriteTintClassTest` rather than shipping. | `res/drawable-nodpi/`, `engine/SceneObjectRenderer.kt`, `tools/assets/sources/sprites.json` | **Closed — v76** |
| ~~**D-5**~~ | ~~**Dolphins and sailboats can overlap while drifting.**~~ **Closed in v76.11.** Each category now owns half the usable water — boats the far half, dolphins the near one — so nothing stops them sharing a lane by accident because they cannot share one at all. Original text: Visible in the maintainer's v74 device screenshot. The two effects have decorrelated threshold offsets, so they select different candidate indices, but neither consults the other's positions: `drawLakeDecorations` runs once per category and each candidate's lane and drift phase come from its own addressed noise. Nothing in v74.1 changes this — it fixed the colours only. Note also that at default densities the pool of 4 yields **exactly one** of each (thresholds 0.069 and 0.340 against density 0.30), and at the default lake height the band sits almost entirely behind the hills. How sparse the lake should read is a question to answer by looking now that the colours are correct, not by arithmetic. | `engine/PaperRenderer.kt` (`drawLakeDecorations`), `engine/CandidateNoise.kt` | **Open — not scheduled** |
| ~~**D-2**~~ | ~~**An Italian caption is rasterised into `santa_sleigh_scene.png`.**~~ **Closed in v76.** The sprite was redrawn from zero by the V2 asset library at 624×168, and the caption is not in the new artwork — verified by reading the sprite, not assumed from the redraw. The original entry's honest caveat still stands and now applies to the new set: the heuristic scan that failed to find this caption also cannot certify the other 110 sprites, so they are unchecked rather than clean. | `res/drawable-nodpi/santa_sleigh_scene.png` | **Closed — v76** |

---

## Known Blockers / Risks

| # | Blocker / risk | Affects |
|---|---|---|
| ~~B1~~ | ~~**The asset generators are lost.**~~ **Lifted in v76.** Phase 3.1 partially lifted it with a committed pipeline covering 24 of 118 sprites; the remaining 94 — including every category Group 4 re-anchors — had no source, so the practical block stood. The V2 asset library ships an SVG source for all 111 sprites, so there is no gap left. Group 4 is no longer blocked. | ~~Group 4~~ — cleared |
| B2 | **No device or emulator.** Nothing can be visually verified. Every phase requiring visual approval depends on the maintainer looking at a mockup or a build. | Groups 2, 3, 4, 5, 6 |
| B3 | **No visual regression testing.** A rendering regression can only be caught by eye. | All rendering work |
| B4 | **v66–v72 shipped unverified** (no Android build tools were available then). That range is less proven than usual. | Any work touching sprite conversion code |
| B5 | **Untestable layers.** The renderer, engine lifecycle, preferences layer and Compose UI cannot be unit tested without first being decoupled from `Canvas`/`Context`. Coverage will stay narrow until then. | All phases |
| ~~B9~~ | ~~**A scene constant copied into a saved theme goes stale silently.**~~ **Guarded from v76.9** by `PersistedThemeGeometryTest`, which pins the boundary in both directions: geometry is recomputed on load and never believed, and what the theme owns survives untouched. The risk it names is not gone — a new persisted field can still cross the line — but it now fails a test when it does. Original text: The traffic lanes moved three times while the custom theme schema stayed at 2, and a schema version cannot guard it — it records a change of *shape*, and a stale copy of a constant parses perfectly. Traffic geometry is now recomputed on every load rather than trusted; **anything else a theme persists that is really a `SceneSpace` constant has the same exposure.** | persisted themes |
| B6 | **`MAX_OBJECT_HALF_WIDTH_UNITS` is a measured constant**, not a per-sprite bounding box. An object drawn wider than 96 units either side of its origin would clip. It is a bound in *local units*, so Group 4's rescaling does not invalidate it — the widest local span in the scene is still the large house's ±75 — but it remains a shared bound rather than each sprite's own box. Guarded by a test and an in-code note. | any new wide sprite |
| B7 | **Slow builds on 1 CPU** (~12 min cold). Limits how many verify-fix cycles fit in one session. | All phases |
| B8 | **Dependencies are from late 2024** against `compileSdk 36`. The longer 7.5 is deferred, the larger the eventual jump. | 7.5 |

---

## Verification Gates

**The verification policy itself lives in `AI_PROJECT_RULES.md` §12** — three
levels, what each requires, and the release report template. This section states
only which gates a *phase* must satisfy on top of that.

Pick the level from §12.B, state it and the reason in the release report, and
escalate when uncertain.

### Every phase, at every level

| Gate | Condition |
|---|---|
| **Forbidden name** | Global case-insensitive scan across text, binaries and filenames returns 0. |
| **Hidden files** | `.gitignore` and `.github/` present and intact; workflow YAML still parses. |
| **`CLAUDE.md` untracked** | `git check-ignore` confirms it, and it stays unstaged after a forced `git add -A`. |
| **Release ZIP completeness** | Every file on disk is present in the ZIP; `CLAUDE.md` inside it and still untracked. |
| **Documentation** | `ROADMAP.md` updated; plus `ARCHITECTURE.md` / `DESIGN_NOTES.md` / `AI_PROJECT_RULES.md` if the phase changed what they describe; `RELEASE_HISTORY.md` always. |
| **Release identifier** | Confirmed unused against Git tags and, where reachable, GitHub releases (§11.B). Never taken from memory. |

### Any phase that changes code (Level 2 and above)

| Gate | Condition |
|---|---|
| **Tests** | `./gradlew test` passes with 0 failures and 0 errors. Test count must not decrease. |
| **New tests** | Any new pure, deterministic logic has unit tests. |
| **Lint** | `./gradlew lintDebug` succeeds with 0 errors and no *new* warnings versus the baseline. Baseline: 87–89; `GradleDependency` and `NewerVersionAvailable` query a remote catalogue and vary between runs. |
| **Mutation check** | Where §12.10 says it applies. A mutation that fails to break a test is a finding, not a pass. |

### Level 3 only

| Gate | Condition |
|---|---|
| **APK build** | `./gradlew assembleDebug` succeeds with 0 compiler warnings. |
| **Clean-extraction rebuild** | ZIP extracted to an empty directory, then built and tested from that copy alone. |

### Required when applicable, at any level

| Gate | When |
|---|---|
| **Visual approval** | Any phase marked "visual approval required". Mockup first, maintainer approval, then implementation (§13). |
| **Allocation check** | Any change touching a per-frame path. Disassemble with `javap` and compare allocation opcodes, including boxing via `valueOf`, before and after. |
| **Compatibility check** | Any change to persisted data. Must load existing payloads unchanged, with a test using a fixture of the old shape. |

### Honesty gate

Anything that could not be verified in this environment must be stated as
unverified — in the report, in `RELEASE_HISTORY.md`, and in the commit message.
Never describe an unobserved visual outcome as observed. Separate Claude-side
from maintainer-side verification explicitly (§12.13).

---

## Handoff Notes

**For a fresh Claude session, on any account, with no access to previous
conversations.** Everything needed is in the repository and the release ZIP.

### Where the project is

- **Current project release:** **v1.0**, stable / latest. The first public release; everything below it is history.
- **Latest stable Android release:** **v76**. `versionCode` is 76 and must not be
  bumped for a beta — see `AI_PROJECT_RULES.md` §11.A.
- **On top of v76, not yet in a stable release:** **v76.1 through v76.7** — four device-fix passes, Group 4 and its two tuning passes.
- **Current phase:** **Group 4 is complete and closed (v76.7).** Group 3 is complete, blocker
  B1 is lifted, Group 9 (GPU renderer) is complete and verified on a Pixel 9, and
  Group 2 is complete. Defects D-2, D-3 and D-6 are closed; decision D2 is resolved.
- **Next task: the comprehensive assessment of the whole app**, which the
  maintainer has stated follows Group 4. Not started, and it is the only thing
  approved to come next.
  Group 4 changed the size and vertical placement of every object in the scene at
  once, and nobody has seen it. Five device passes on the V2 artwork found
  twenty-five defects between them; this release deserves the same scrutiny and
  more, because unlike those it changes the composition rather than individual
  objects. **Group 5 is next in order and is not approved.** Open and **not
  approved**: **D-8** (roof snow, needs artwork), **D-7** (two rasterisers),
  **D-4** (the call-site resolver blind to `SceneObjectRenderer`), **D-5**
  (dolphins and sailboats can overlap), **D7**, **D3**, **D4**, **D5**, and the
  four remaining orphan drawables. The CPU audit's remaining hotspots under
  Current / Next Work are **not approved** either.
- **Device confirmation already obtained** for Phases 2.1/2.2, 2.2b, 2.3, 2.4,
  3.4/3.5/3.6 and 9.5 — see the maintainer-verification table under Current
  Status. Those were checked on real hardware; do not re-litigate them. **Note
  that all of them predate v76's artwork**, so a confirmation about how something
  was placed still stands, while a confirmation about how it looked does not
  necessarily.

### Read these first, in this order

1. `README.md` — what the app is (**out of sync**; do not trust it as a feature
   inventory, see 7.7)
2. **`ROADMAP.md`** — this file: current phase and next approved work
3. `AI_PROJECT_RULES.md` — the permanent rules
4. `ARCHITECTURE.md` — how it actually works today
5. `DESIGN_NOTES.md` — the visual design system
6. `RELEASE_HISTORY.md` — what shipped and what is known broken
7. `CLAUDE.md` — environment setup and local workflow notes

### State left by Group 2

`engine/SpriteBlitter.kt` owns the one sprite `blit`, with `SpriteScale` naming
the authoring convention (2.3). Any new sprite draw goes through it. The
convention is still chosen by the caller; moving it into asset metadata is
Group 3's job.

`engine/SceneObjectRenderer.kt` draws static objects by walking the tile range
`firstVisibleTileOffset until tileOffsetLimit` (2.4). Both bounds are pure
companion functions on purpose: `draw()` needs a `Canvas`, so any logic left as a
condition inside it cannot be unit tested, and the first mutation run survived
for exactly that reason. Keep new enumeration logic out of `draw()` itself.

`isHorizontallyVisible` remains the single authority on whether a copy is drawn.
`MAX_OBJECT_HALF_WIDTH_UNITS` is still a measured constant, not a per-sprite
bounding box (blocker B6) — the real fix is Group 3.

### State left by Phase 3.1

`tools/assets/` is offline tooling: **Gradle never invokes it and the app does not
depend on it.** Set it up with `pip install -r tools/assets/requirements.txt`,
then always run `python3 -m paperscrape_assets probe` first — if the toolchain
fingerprint does not match, every fidelity figure under `reports/` was measured
with a different rasteriser and must be re-measured rather than trusted.

`sources/sprites.json` is the registry and it covers **all 118 shipped sprites**,
not just the 24 with sources. Keep it that way: the 94 `"kind": "none"` entries
with their stated reasons are the honest inventory of what still cannot be
regenerated, and a registry listing only successes would read as complete while
hiding the actual problem.

The three sprites that failed the original IoU gate (`house_large_planter`,
`house_small_planter`, `road_line`) were investigated before the metric was
changed, not after: their geometry is recovered, and IoU was found to be an area
ratio applied to a boundary phenomenon. The gate is now scale-free and the
replacement is pinned by tests that fail on a one-pixel displacement, a radius one
grid unit off, and a fill colour off by one. Do not weaken it to make a future
sprite pass.

### State left by the `scrollBackground` bugfix (v73.6)

The sky layer is no longer one translated block. It now has two paths, because it
holds two things with opposite tiling natures, and treating them as one is what
produced the bug:

- the **star field** is a tiled pattern with a period of exactly one screen width,
  drawn over the range `firstStarTileOffset until starTileOffsetLimit` — the same
  shape 2.4 established for static objects;
- the **sun and moon** are single objects and get `celestialParallaxOffset`, a
  bounded, non-cyclic offset. **It must never become a wrap.** A wrap puts a
  second sun on screen at the seam, which the maintainer explicitly rejected, and
  a saturating bound would pin the body in place.

The bound is not a safety constant: it is `restCx - radius`, the real distance the
body's own rest position leaves to the left edge, which exists because the
keep-out margin (`CELESTIAL_MARGIN_FRACTION`, 0.12) is wider than the disc radius
(`CELESTIAL_RADIUS_FRACTION × 2`, 0.11). A test asserts that relationship directly,
so it cannot be broken silently.

`CELESTIAL_RADIUS_FRACTION` is deliberately the **undoubled** value and is
multiplied by `2f` at each use. That preserves the original `screenWidth * 0.055f *
2f` association exactly; folding the `2f` into the constant would change the
floating-point association and could move a rendered position by an ulp.

All three helpers are pure companion functions for the same reason 2.4's are:
`draw()` needs a `Canvas`. Keep new geometry out of `draw()` itself.

The accepted cost, approved by the maintainer: below `celestialX ≈ 0.38` at
`parallaxStrength` 1 — mornings — the slack runs out before the full parallax
does and the body moves less than it used to. That is geometry, not a bug. **A
two-sided sway that would recover the morning motion by letting the body drift
right was proposed and explicitly rejected**: the rest of the background moves
left, and the body must not read as independent of it. Do not reintroduce it.

### State left by Phase 3.2 (v73.8)

`sources/sprites.json` is **schema 2**. Three things about it are load-bearing:

- **`contentBox` is declared for all 118 sprites and checked against the PNG.**
  Phase 3.3 will change most of them; that is the point. Update the manifest in the
  same change that regenerates a sprite, or `validate` fails.
- **`anchorRule` is mandatory and `UNDETERMINED` is one of its values**, carrying
  an `anchorReason` — exactly the shape `source.kind = "none"` already had. Only
  17 anchors are determined. **Do not fill in the other 101 by choosing a
  plausible rule.** An origin is `placement - anchor` with both unknown; a rule
  picked because it looks right is an invention presented as a recovery, which is
  the failure Phase 3.1 was built to avoid.
- **`anchor` is never trusted, always re-derived** from the rule and the content
  box on every `validate` run.

`callsites.py` resolves blit call sites so the declarations can be compared to the
code. It is **syntactic on purpose** — no dataflow analysis. A sprite chosen from
a lookup table (`resId`, `driverRes`, `phaseSprite`) or an origin computed from
the drawn object's own dimensions resolves to nothing and is reported as
*unresolved*. If a future change makes the resolver smarter, keep that property:
the value of the check is that it distinguishes "agrees" from "could not tell",
and a resolver that guesses erases the distinction it exists to make.

One finding worth carrying forward, out of scope to fix: `santa_sleigh_scene` is
blitted through `drawTinted` with an identity white tint, purely because `draw`
has no alpha argument. The resolver models that (white is the MULTIPLY identity,
so the sprite is fixed art), and a test pins it. If `draw` ever gains an alpha
parameter, that call site should move.

### State left by the GPU renderer migration (v73.11 and v74)

**The rule, in one sentence:** the scene renderers draw into `SceneCanvas`, which has two
implementations, and anything the scene wants to draw must be expressible in that
interface's operation set or it splits the two backends.

Five things are load-bearing and must not be quietly undone:

- **The projection is screen pixels with Y down.** `Matrix.orthoM(0, w, h, 0)`. This is
  what lets every coordinate, sprite origin, depth constant and historical divisor keep
  its existing value. Moving to a normalised world space means rescaling all of them, and
  a sprite whose origin is only correct together with its scale convention is exactly how
  D-1 happened.
- **Premultiplied alpha and `glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)` are one
  decision, not two.** `BitmapFactory` decodes premultiplied and `GLUtils.texImage2D`
  uploads unchanged. Changing either half alone puts a dark fringe on every soft sprite
  edge.
- **The flat-fill path samples a 1x1 white texture on purpose.** It is what makes one
  shader program enough, and therefore what stops a solid shape between two sprites from
  breaking the batch. Splitting it into a second program would undo the batching.
- **The hill highlight is filled as columns split at the gradient's lower stop, not as a
  fan.** A fan from a base vertex interpolates the ramp across each triangle's full height
  and washes the highlight down the whole hill. This was caught during implementation, not
  by a test — there is no test that could catch it.
- **`onRenderThread { }` is how scene state is mutated, not a lock.** Every prefs, theme,
  custom-theme, weather and offset update is queued onto the render thread. A lock around
  the renderer would put every settings write in contention with the frame loop.

**Three process-wide objects are now synchronised** — `SpriteCache`, `TintFilterCache` and
`SunPositionCalculator.currentHour24()` — because a process can host two engines and
therefore two render threads. `SpriteCache`'s lack of a lock had been correct *and
documented as conditional* on main-looper rendering; this release removed that premise, so
the lock came with it rather than after it.

**`TintFilterCache` is now used only by the `Canvas` backend.** On the GPU the tint is four
floats in a vertex colour. Do not delete it: the settings preview and the fallback both
need it.

Three more things landed in v74 and are equally load-bearing:

- **The flat-fill white pixel lives in the atlas, and its UV is the entry's *centre*.** A 1x1
  entry is one texel inside a transparent border; sampling at the corner sits on that boundary
  and bilinear filtering mixes the transparency in, which makes *every flat fill in the scene*
  half-alpha. It also has to be the first thing packed, both after context creation and after a
  memory-pressure trim — being first is what keeps it in the atlas instead of pushed out to a
  texture of its own.
- **`SceneCanvas.drawSprite` takes a `SpriteSource`, not a `Bitmap`.** Reverting that would put
  a synchronised `SpriteCache` lookup back on every blit of every frame purely to recover a
  width and a height the registry already holds.
- **The decoded bitmap is released once the GPU holds the sprite.** Safe only because every
  sprite can be decoded again from resources — the same property memory-pressure eviction
  relies on. The `Canvas` backend must never report an upload, because it keeps no durable copy.

**`ShelfPacker` is pure and tested; `GlTextureAtlas` is the GL half and is not.** A packing bug
is silent: overlapping rectangles do not throw, one sprite simply renders with another's pixels
inside it, and only for the pair that happens to be adjacent in the atlas.

**What has no automated test, and cannot easily get one:** `GlSceneTarget`,
`GlRenderThread`, `GlTextureCache` and `GlSpriteProgram` all need a GL context. What *is*
covered is `SceneTransform` and `SceneShape`, and that coverage was earned — three of the
eight mutants survived the first run. In particular, **the sign of the cross term feeding
`a` in `rotate` is invisible to every single rotation the scene actually performs**,
because `c` is zero from an axis-aligned state; only a rotation composed onto an
already-rotated basis distinguishes it. If that test is ever simplified, keep the composed
rotation.

### State left by the CPU audit batch 1 (v73.10)

**The audit is the artefact, not just the batch.** Its ranked hotspot list lives
under Current / Next Work, and the eight items still on it are recorded so the
next session does not have to rediscover them — and are **not approved**.

Three properties of the batch are worth carrying forward:

- **Every fix is behaviour-preserving by construction.** Four are hoisting: data
  that never varied moved out of a loop or out of a function. The fifth replaced a
  `Calendar` with arithmetic that is pinned against that same `Calendar` by a test
  at tolerance `0f`, in eight time zones across a year of samples, including
  pre-epoch instants and DST transitions.
- **The one place a test could have lied.** The first version of that test sampled
  pre-epoch instants at exact minute boundaries, where truncating and flooring
  division agree — so a mutant replacing `floorDiv` with `/` survived. The test
  now offsets by 37,123 ms and kills it. If that test is ever simplified, keep the
  non-whole-minute sample; it is the whole reason the case is covered.
- **`hourAt` exists to be testable.** `currentHour24` caches on the minute and
  reads `TimeZone.getDefault()` inside the cache-miss branch, which is what keeps
  the steady path allocation-free while still picking up a DST or time-zone change
  within a minute. Do not inline `hourAt` back into it.

**What was deliberately left alone**, and must not be treated as an oversight:
the star field, every tile-copy count, cloud and mountain culling, mountain Path
caching, the frame scheduling and `onOffsetsChanged`. The last two were written
against a perceived stutter and need device verification rather than a static
argument.

### State left by Phase 3.3 (v73.9)

**The rule, in one sentence:** a sprite's normalised content box is the union of
the measured alpha bounding boxes of its co-registered group, rounded outward to
a multiple of `SPRITE_PIXELS_PER_UNIT` for a `SCENE_UNITS` sprite and of 1 px for
a `CANVAS_PIXELS` one; the sprite is cropped to it and its call site's origin is
compensated by `trim / unit`. Three parts of that are load-bearing:

- **Outward, not to the measured box.** The blitter multiplies the origin by the
  same unit it was divided by. A crop of 17 px gives a compensation of 5.667
  units, which comes back as 17.000002 — a sub-pixel origin, resampled because
  the blit paint carries `FILTER_BITMAP_FLAG`. Rounding outward keeps the
  compensation an exact integer and leaves up to `unit - 1` px of padding behind.
  **That residue is deliberate and load-bearing. Do not "finish the job" by
  cropping it away.**
- **The union, not the member.** 44 sprites are chosen from a lookup table at
  draw time, so one origin literal positions all of them. Cropping each to its own
  box would need one origin per member — which does not exist, and which rule 7.3
  forbids. The walk frames disagree by 9 px, so a per-frame crop is a visible
  horizontal jitter in every walker.
- **A shared origin *value* is not a group.** `tree_canopy` and
  `tree_canopy_snowcap` are both blitted at (-45,-84), from two separate call
  sites with their own literals, so each took its own crop and its own
  compensation. A group is one call site serving many sprites.

**What was deliberately not done, and must not be quietly done later without a
decision:** the palm-frond pair (`palmtree_fronds`, `palmtree_fronds_frost` —
102x176, off-grid canvas, shared hand-tuned `-87.45f` origin), the moon phases
(the group rule refuses the crop on its own, which is what keeps the moon still
as it waxes), the orphan drawables (no call site to compensate), and **all anchor
semantics** — no `anchorRule` was added or resolved, and the 101 `UNDETERMINED`
anchors are still undetermined.

**Two historical divisors must not be re-derived from the PNGs.** `130f / 680f`
for the sleigh and `15f / 70f` for the birds reproduce the old vector versions'
on-screen footprints; they are not readings of a sprite's canvas. Both sprites
were cropped heavily and both comments now say so explicitly, because
recomputing either from the new canvas would rescale the artwork — the D-1 shape
of failure exactly.

**Where the check lives.** `paperscrape-assets normalize` is part of
`paperscrape-assets all` and fails if any in-scope sprite regains padding. But
Gradle never runs the Python tooling, so `SpriteNormalisationTest` repeats the
invariant as a JVM test — group-aware, with the exclusions listed by name and
reason. It is deliberately convention-blind (it applies the 3-px tolerance to
every sprite, since Kotlin cannot see which convention a PNG was authored in),
which makes it slightly lenient for the raw-pixel sprites and never unsound.

### State left by Group 4 (v76.5)

**The rule, in one sentence:** a category's size is now *derived* from a declared
real height, so an object that draws wrong is a wrong number in one table rather
than a correction to be bolted onto its call site.

- **`SceneSpace` is the only place the ground plane exists.** `HILL_SAFE_DEPTH_MIN`/
  `MAX`, `ROAD_SAFE_DEPTH_MAX` and `depthScaleFor` are gone from `PaperRenderer`;
  `GLOBAL_OBJECT_SCALE` and `ROAD_SHOULDER_UNITS` are gone from
  `SceneObjectRenderer`; the per-category base scales are gone from
  `SceneObjectCatalog`. **Do not reintroduce any of them.** If something is the
  wrong size, the answer is a `metresTall` in `SceneSpace.SceneVariant`.
- **The scale pipeline is four independent stages** and no stage may compensate
  for another. The two `canvas.scale(0.83)`/`(0.68)` corrections inside the house
  drawings were exactly that failure and are deleted.
- **Height is the governed dimension; width follows the artwork.** The V2 sprites
  are stylised and are drawn at internal scales that differ by three and a half
  times between a person and a shop front. Governing width instead makes a person
  shorter than a car. If a house now reads as too narrow, that is the artwork, and
  the fix is a redraw, not a second multiplier.
- **`ROAD_SAFE_DEPTH_MAX` has no successor, deliberately.** The road is below the
  object band by construction and `SceneSpaceTest` asserts the margin. Re-adding a
  depth cap would silently recreate the collapsed band.
- **A building's style comes from its depth**, not from a hash of its position, so
  towers are on the skyline and shop fronts among the houses. `variantFor` decides
  it once and both the size and the dispatch use that answer — they used to be
  decided separately, which is why a per-drawing size could only ever be a
  correction.
- **Objects now scale with screen height.** Sizes are stated against a 2400 px
  reference and multiplied by `sceneScale`. Anything that draws at a fixed pixel
  size regardless of the viewport is a bug now, not a convention.
- **Custom themes migrate at schema 2.** `StaticSceneObject.scale` changed meaning
  from an absolute size to a variation around 1, and the lanes moved; both are
  quiet breaks that parse fine and render wrong, which is why the migration exists.
  `SceneSpace.legacyBaseScaleFor` is the only remaining record of the old base
  scales and must not be deleted while the migration exists.
- **The mountains were not touched.** Their silhouette is out of Group 4's scope;
  only the constant they anchor their base line to was renamed.
- **The lake has its own metric** because it sits at the horizon where the ground
  projection is zero. It governs the sailboat against the dolphin and nothing else.

- **A density control may never touch geometry.** The Cars slider resized the road,
  because `drawRoad` read its lane span from the density-filtered runtime list. The
  road is built from the layout's own car list now, computed once. If any future
  control appears to change the shape of something rather than the number of it,
  this is the shape of the bug. The other density sliders were audited and are clean.

### Added by the v76.7 tuning pass

- **The scene's vertical order is buildings, pavement, road.** People walk on the
  ground *between* the village and the tarmac, not in front of the carriageway.
  Moving them back made them much smaller, and that is the projection working:
  they are further away. Do not "fix" it with a scale bump without checking the
  hierarchy.
- **`REFERENCE_Y_FRACTION` is the projection's own constant, not a lane.** While it
  aliased the near lane, moving the road rescaled every object in the scene. Never
  define the metric in terms of a composition element.
- **The road moves by moving both lanes together.** Spacing is the width control
  and position is the pair's offset; the two are independent and must stay so.
- **Decoration placement comes from the foliage's own measured content box**, as
  unit-disc offsets the caller scales by its sprite's half-extents, drawn inside
  the sway transform. Absolute offsets around a hand-picked centre is what put the
  tree lights below the canopy.

### Added by the v76.6 tuning pass

- **The size table states read-as heights, not measurements.** Person 2.0 m, car
  1.45, tower 17, tree 9.8, gift 0.95, lake metric 21 px/m. Each departure from the
  physical value is recorded in `SceneSpace` beside the number. **The hierarchy is
  what must hold** -- person < car < house < tree < larger building, road coherent
  with car size, boat > dolphin, gift above a tiny accent and below a person -- and
  the individual figures serve it. Do not restore "real" values without checking
  the hierarchy still reads.
- **The road narrows by narrowing its lanes.** Its edges are derived from the lane
  pair, so lane spacing is the control; there is no separate road-height number to
  reach for. Clearance is 28 px above and 57 px below on a 2400 px screen.
- **A `TINTABLE` sprite must be a colourless mask.** The snowman rim was authored
  cool first and `SpriteTintClassTest` rejected it, correctly: `MULTIPLY` compounds
  hues. Value separation in neutral grey is the only shape this fix can take, and
  it inherits the user's hue for free.
- **The asset pipeline could not load its registry** before this pass -- a stale
  `SCHEMA_VERSION` and two missing `ANCHOR_RULES`. Fixed. With it running, its own
  suite reports 9 failures of 76 and `validate` reports call-site disagreements
  including pre-existing ones. **That backlog is not Group 4 work**; it belongs to
  the assessment. Do not treat those failures as regressions from this release.

**What was deliberately not done.** No artwork changed beyond the snowman rim.
Pedestrians still do not
use `GroundGeometry`, so they neither tile nor scroll with the terrain — that is
Group 5.1. Roof snow (D-8) still needs artwork. `assembleDebug` was not run, no
APK was built, no Git tag was created.

### State left by the v76.4 pass

**The rule, in one sentence:** four of these seven were a number that described a
different drawing, one was an animation that was never written, one was a settings
path that could not deliver, and one was refused.

- **The road strip is symmetric about the centre line and derived from the lanes.**
  `top = minLane - halfSpacing - shoulder`, `bottom = maxLane + halfSpacing +
  shoulder`. The old 55-unit top margin existed to keep a cabin inside the strip and
  cost the whole geometry to do it; a car is taller than its own lane half at the
  current proportions and no edge value fixes that. **If cars poking above the road
  is reported again, it is Group 4, not this function.**
- **`SceneTime.cosAt` is new and exists for one reason**: something animating along a
  sine arc can orient itself along it without differencing frames. The dolphin uses
  it for tilt. It is the slope of `sinAt` at the same instant, by construction.
- **A dolphin is drawn only while `arc > 0`.** There is no clipping at the
  `SceneCanvas` seam and none is wanted: skipping the blit is what makes the animal
  disappear under the water. If lake decorations ever gain a below-surface state,
  that is where it goes.
- **Lake decorations only ever drift right.** The dolphin was redrawn facing left and
  had to be mirrored in its own source. Check facing against `drawLakeDecorations`,
  not against the sprite sheet.
- **A child can never be drawn driving, structurally.** The driver comes from
  `personCarHeadDrawables` (man, woman) and the passenger from
  `personWindowHeadDrawables` (all four). There is deliberately no child driving
  head. **Do not "unify" these two tables**: the separation is the guarantee.
- **Live Weather now has a wake-up path.** `weatherWakeUp` is a conflated `Channel`;
  the settings collector clears `lastWeatherFetchMillis` and sends on it, and the
  loop waits on the channel or its two-minute tick, whichever comes first. Any future
  preference that the scene must obey immediately needs the same treatment — a
  polling loop with an hour-long refresh gate cannot deliver one.
- **Roof snow is refused, not forgotten.** See defect **D-8**. It needs artwork.

**What was deliberately not done.** Global proportions remain Group 4. `assembleDebug`
was not run, no APK was built, no Git tag was created.

### State left by the v76.3 pass

**The rule, in one sentence:** two of these six were artwork, one was an animation
that a sprite conversion had silently ended, and the traffic one had **two** causes
that were hiding each other.

- **Traffic needed both halves.** v76.2 gave the road two lanes and it still looked
  congested, because lanes were never the whole problem. Speed was rolled per car
  across a factor of three, so a lane's fast cars caught its slow ones within one
  crossing; and the wrap `progress = -0.3f` discarded each car's head start at the
  end of every lap, collapsing a lane into a pack after one circuit. Speed is now a
  property of the lane, and the wrap **subtracts** the 1.6 span so phase survives.
  Only with both fixed does an even initial spacing mean anything. If a future change
  reintroduces per-car speed, the even spacing becomes decorative again.
- **A sprite conversion can silently end an animation.** The reindeer stopped
  trotting in v73, when the group became one bitmap — a bitmap cannot bend, and
  nothing recorded the loss. The trot is now a second drawing, `santa_sleigh_trot`,
  alternated by the renderer. Both frames come from **one** generator description
  with a leg-phase parameter, so editing one and forgetting the other is not
  possible. Look for the same failure in anything else converted from vector paths.
- **`bird_body` must stay symmetric about its horizontal centre.** `drawBirds`
  animates the wing flap by mirroring the sprite vertically, so any feature above the
  centre line spends half the cycle below it. The gull's body, head and beak are all
  on the centre line and only the wings are off it. A future redraw that puts the head
  on top will look upside-down half the time.
- **Placement bugs keep coming from the same place**: a number that described the
  previous drawing. The snowman's arms were at head height because the old body's
  spheres were arranged differently; the car glass was centred by an arithmetic rule
  that a non-symmetric greenhouse makes wrong. Measure the current artwork, then
  composite it and look — that is how all of these were settled.
- **Custom themes persist their car objects.** A custom theme saved before v76.2
  keeps its old single-lane layout, its per-car speeds and its random delays until it
  is regenerated. Built-in themes are generated per run and are unaffected. If lane
  behaviour is ever reported as still broken, ask which theme first.

**What was deliberately not done.** Global proportions between people, cars, houses
and trees remain reported and untouched — that is Group 4, and it is the one open
item that is a design question rather than a defect. `assembleDebug` was not run, no
APK was built, no Git tag was created.

### State left by the v76.2 cleanup

**The rule, in one sentence:** when the artwork is replaced wholesale, every number
that describes the *old* drawing survives silently — direction, origin, count and
lane were all still describing sprites that no longer exist.

- **Read facing off the artwork, never from a comment.** The V2 car and sleigh face
  **left**: `car_body`'s long bonnet is at its left end, `car_window`'s raked edge is
  on the same side, and the sleigh's reindeer are drawn at the left of their sprite.
  The flip sign in `drawCar` and `SantaSleighEffect.draw` is inverted accordingly. If
  an art pass ever mirrors these, both signs move together.
- **A car's lane decides its direction, and the index decides its lane.** Both used
  to be independent rolls of one `Random`, which is how the whole fleet ended up in
  one 36 px band driving both ways through itself. Lane comes from candidate index
  parity so both lanes are always populated; `reverse = !nearLane`. Do not restore a
  random `reverse`: it re-creates head-on traffic in a shared lane.
- **Three things move together with the lanes**, and changing one alone reintroduces
  a defect: `buildCarRuntimes` sorts far-lane-first because draw order is depth
  order; the dashed centre line is halfway between the two lanes' *ground lines*, not
  the middle of the painted strip; and `ROAD_BOTTOM_MARGIN_UNITS` is 12 rather than
  24 because the near lane moved down toward the pavement at 0.83 of screen height.
  The far lane deliberately keeps the old band's 0.79, which is what holds the road's
  **top** edge still so nothing beside it gets covered.
- **Seven sprite origins were describing the previous drawings.** `car_window`,
  `police_stripe`, `police_lightbar`, `taxi_checker`, `snowman_nose`,
  `snowman_scarf`, `penguin_beak`, `bunny_innerear` — plus the snowman's code-drawn
  twig arms. Each new value was measured off the V2 artwork (the snowman's neck is
  its narrowest row; the bunny's ears occupy x -9.3..15.3) rather than nudged by eye.
  **This is the same failure mode as v76.1's car-driver head**, and it is worth
  expecting more of it in any sprite whose composite parts were not checked.
- **`bird_body` was never a three-bird strip.** The asset package's mapping note said
  it had stopped being one, v76 took that as an instruction to place it three times,
  and birds shrank to a third of their size. One candidate is one bird, blitted at the
  sprite's own 90 px, which is exactly the wingspan the old sprite reached through its
  `15/70` divisor. That divisor is gone and must not come back.
- **There is no ambulance in this project.** It was reported as rendering "as a white
  car"; `CarType` is `PLAIN`, `POLICE`, `TAXI`, `FIRE_TRUCK`, and the white vehicle is
  the police car, which had no visible markings because its stripe was drawn on the
  road beneath it. Fixing the stripe fixed both that and the stray line reported under
  the vehicle. Adding a real ambulance is content, not a defect, and belongs with
  Group 5's vehicle work.

**What was deliberately not done.** No artwork changed in v76.2. Global proportions
between people, cars, houses and trees were reported as wrong and are **Group 4** --
they were explicitly left alone. `assembleDebug` was not run, no APK was built, no
Git tag was created.

### State left by the v76.1 device fixes

**The rule, in one sentence:** all four defects the device found were in the
artwork or in the single number that places it, and none of them was fixable by
adjusting the other half.

- **A sprite can be clipped by its own canvas, and the PNG will not complain.**
  `moon_crescent`'s terminator arc had an x-radius of 52 against a disc of 34 in an
  80-unit canvas, so the shape ran off the right edge and the rasteriser cut it
  flat. The give-away was in the registry all along: a content box reaching x=240
  of 240. **When something looks cut, measure the content box against the canvas
  before touching an anchor or a UV.** `moon_gibbous` carried the mirror error and
  nobody had reported it, because that phase only comes round for part of a month.
- **Centring a sprite's canvas is not the same as honouring its anchor**, and the
  two agree only when the content happens to be centred. The car-driver head was
  placed with `-27f, -27f` — right for the 60×60 sprite that existed when the line
  was written, wrong for a 171×162 one anchored `CONTENT_BOTTOM_CENTRE`. The fix is
  `placement − anchor`, and the artwork was not touched. Re-cutting a sprite to
  absorb a call-site number is the failure mode `DESIGN_NOTES.md` records against
  five earlier releases.
- **Two sprites that meet must be re-derived together.** The snow cap came across
  from a crown the V2 tree does not have: its ridge sat 2 units below the canopy's
  and its corners 5 short of each shoulder. The redrawn cap repeats the crown's own
  upper vertices, so if the canopy art moves, the cap has to move in the same
  change — the same relationship the palm's trunk and frond fan already have.
- **The fire truck needed its own body, not a better accessory.** It shared
  `car_body`, so no ladder placement could have saved it; the ladder's own origin
  cleared the sedan roof entirely and hovered. `firetruck_body` is drawn after
  `firetruck_ladder`, deliberately: the roof line paints over the ladder's lower
  rail, which is what makes it read as carried.
- **The two rasterisers now in play are recorded as defect D-7.** The four
  regenerated sprites went through the project's pinned `resvg_py` with a passing
  `probe`; the other 108 did not. Nothing at runtime depends on it, but
  `paperscrape-assets compare` will now report those 108 as divergent from their
  own sources. Do not "fix" that by re-rendering the set without asking: it changes
  108 PNGs at once.

**What was deliberately not done.** No other asset was touched. The window-occupant
heads were left alone — they use a different sprite and a different call site, and
nothing was reported against them. `assembleDebug` was not run, no APK was built,
no Git tag was created, and Group 4 was not started.

### State left by the V2 asset library (v76)

**The rule, in one sentence:** a sprite's tint class is a property of its bytes —
tintable means a greyscale mask, fixed art means the PNG carries its colours — and
nothing may be both.

Nine things are load-bearing for whoever picks this up next.

- **The manifest is the source of truth for the artwork, not for the code.** It
  disagreed with the shipped call sites twice, and the two resolved in *opposite*
  directions, which is the part worth remembering. `star_sparkle` is declared
  `CANVAS_PIXELS` there — that is defect D-1 written down, since read as raw pixels
  the 180 px sparkle covers 180 local units against a star's own 32 — so the call
  site won. `santa_sleigh_scene` is declared `SCENE_UNITS` where the call site said
  `CANVAS_PIXELS`, and there the manifest won, because the sprite genuinely was
  re-authored on the grid. Size, convention and origin are only correct **together**;
  when two disagree, the answer comes from whichever was actually re-derived, never
  from whichever is easier to edit. Both are recorded in the registry's `notes`.
- **Do not re-introduce a tint over the new art.** Roughly a dozen accent constants
  were deleted, not moved: the penguin's beak and feet, the bunny's inner ear, the
  gift ribbon, the house planter, the skyscraper's lit and dark window, the tree
  trunk, and v74.1's three lake-decoration colours. Every one of them existed
  because the artwork was a white mask. Restoring one now multiplies finished art
  by a second colour, which is exactly the failure `SpriteTintClassTest` exists to
  catch, in the direction opposite to D-3 and D-6.
- **Two historical divisors are gone, and one survives.** The sleigh's `130f/680f`
  and its `(-283, +244)` origin are retired: V2 redrew it at 624×168 on the grid, so
  it is a `SCENE_UNITS` sprite at `SANTA_SLEIGH_SCALE = 1.5f`, centred on the flight
  point. Centring it also fixed a latent misalignment — the old origin put the
  sleigh 95 px right and 130 px below the point its own gift-drop code spawned
  presents from, so presents appeared to fall from above it. The birds' `15f/70f`
  **survives** and must still not be re-derived from the PNG.
- **One bird candidate now draws three birds.** `bird_body` went from a single
  420×65 gull to a 90×42 one, and the flock offsets in `PaperRenderer`'s companion
  fill the footprint the wide sprite used to. Each bird is blitted at `-45,-21` so
  it is centred on the flip axis — the wing-flap is a vertical mirror, and mirroring
  about anything else makes the bird hop.
- **The palm's `-87.45` is gone.** V2 declares a `DECLARED_ATTACHMENT` at (60,102),
  the point where the blades converge, so the frond origin is derived from it rather
  than hand-tuned. If the frond art changes again, recompute from the declared
  anchor — do not re-measure the trunk.
- **Six sprites replaced code.** `tree_trunk` replaced a `drawRect`; `rainbow_arc`
  replaced 7 stroked bands and 7 highlights, taking `rainbowPaint` and 14 per-frame
  `RectF`s with it; `firework` replaced 18 circles per burst and the `List<Particle>`
  allocated per spawn; `skyscraper_wall_lit` and `house_window_lit` replaced a
  `drawRect` grid and a day-to-night tint ramp with crossfades on `nightGlow`;
  `lightning_bolt` is genuinely new content, since the storm was a white veil only.
- **What that cost, deliberately.** Skyscraper windows no longer light per building
  on a pseudo-random roll, and firework bursts no longer vary in hue. Both are the
  same trade: the state is in the artwork now, so there is one of it.
- **Two test classes were replaced, not repaired.** `SpriteNormalisationTest`
  asserted that no sprite carries removable padding; V2 declares a `contentBox` per
  sprite and places drawings inside grid-sized canvases, so 34 sprites carry margin
  on purpose and the old rule would fail them for being drawn as designed.
  `SpriteGeometryTest` asserts what is still true — the 3 px grid, a total decoded
  byte ceiling, no single sprite over an eighth of it. `LakeDecorationTintTest`
  became `SpriteTintClassTest`, generalised from three sprites to all 111 and
  asserting in both directions; its own doc comment had specified this migration.
- **`theme.starColor` is now dead but not deleted.** The sparkle is fixed art, so
  nothing reads the field. It stays on `SceneTheme` because custom themes persist it
  and removing it would break their JSON.

**What was deliberately not done.** `assembleDebug` was not run, no APK was built,
and no Git tag was created — all three on the maintainer's explicit instruction.
Group 4 was not started. The four orphan drawables were left in place. Defects D-4
and D-5 were not touched. Nothing in v76 has been seen rendered.

### State left by Group 3's completion (v74.2)

**The rule, in one sentence:** two sprites with the same bytes are never left
unexplained — either one of them goes, or the registry says why both exist.

- **The variant table is the new load-bearing declaration.**
  `tools/assets/sources/sprites.json` is schema **3**: a top-level `variants`
  array alongside `sprites`. Eighteen groups, each with an `axis`, `members`, a
  `state` of `DISTINCT` or `IDENTICAL_GAP`, and a `reason`. Anything added to the
  sprite set whose name carries a season must join it —
  `test_every_seasonal_sprite_belongs_to_a_variant_group` enforces that as a rule
  rather than a count.
- **`IDENTICAL_GAP` is self-closing, and that is the point.** The six person-head
  groups are declared identical *and should not be*. Drawing one of the winter
  heads makes `validate` and `SpriteVariantTest` **fail**, saying to move the
  group to `DISTINCT`. Do not relax either check to make the failure go away:
  that failure is the mechanism that stops the gap being closed in the artwork
  and left open in the registry.
- **`personWalkDrawables`' frame-3 slot names `walk1` deliberately.** Four frames,
  two poses: 0 and 2 are the contacts, 1 and 3 the passing pose, where the legs
  are together and a flat silhouette is the same whichever leg leads. If a future
  art pass gives the two passing frames different artwork, restore
  `..._walk3.png` **and** the slot together — re-adding the file alone fails
  `SpriteVariantTest`.
- **`house_shared_window` / `house_shared_planter` are shared on purpose.** Both
  house variants blit them, at five and two origins respectively, under different
  canvas scales. `house_window` was not available as a name: it is one of the
  seven orphan drawables.
- **Group 4 is next in order and still blocked by B1.** Group 3 is complete, but
  the sprites Group 4 needs — people, vehicles, buildings, decorations — are still
  among the 86 declared source gaps. Completing Group 3 did not lift that.

**What was deliberately not done.** No winter head artwork was invented; person
art has no committed source, so it is redesign rather than regeneration. The seven
orphan drawables were left in place — they are dead weight, but not duplicates,
so removing them is Group 7 housekeeping and needs its own decision. Defect D-4
was not fixed. Nothing in v74.2 has been seen rendered.

### State left by defect D-3 (v74.1)

**The rule, in one sentence:** a sprite's authoring profile is a property of its
pixels, and until v74.1 nothing in this project ever read them to check it.

Three things are load-bearing:

- **The three lake-decoration sprites are tint masks, not fixed art.**
  `dolphin_body.png` and `sailboat_hull.png` hold a single colour, pure white,
  over every opaque pixel; `sailboat_sail.png` is greyscale mottling on white.
  `DESIGN_NOTES.md` §3 nevertheless classifies the dolphin and the sailboat as
  *fixed-art*, and that classification is unchanged and still correct as a
  **category** decision: they are not user-recolourable. What changed is where the
  colour comes from. `DOLPHIN_COLOR`, `SAILBOAT_HULL_COLOR` and
  `SAILBOAT_SAIL_COLOR` in `PaperRenderer`'s companion supply what the artwork
  never carried, exactly as `SceneObjectRenderer` already did for the penguin's
  beak and the gift ribbon. **Do not promote these to user-editable settings** —
  the fixed-art classification per category is a protected element.
- **The manifest's `tint` field describes the call site, not the artwork.** That
  is why the contradiction survived Phase 3.2's whole call-site checker: the
  declaration and the code agreed with each other while both disagreed with the
  pixels. `LakeDecorationTintTest` is the only thing that reads the two together,
  and it asserts in both directions. If a future asset pass bakes real colours
  into one of these three PNGs, that test fails, and the correct response is to
  move that sprite's call site back to `SpriteBlitter.draw` in the same change —
  not to relax the test. Multiplying finished art by a colour is the same class of
  silent error in the opposite direction.
- **A GPU-side hypothesis cannot explain a pre-GPU defect.** D-3's original entry
  listed two candidate causes, both inside the GL backend, next to its own
  statement that the defect predated that backend. Neither was ever going to be
  right. When a defect's recorded symptom and its recorded hypotheses are
  incompatible, the hypotheses are the thing to discard.

**What was deliberately not done.** The colours were implemented without a mockup,
which `AI_PROJECT_RULES.md` §13 would normally require, because the maintainer
directed the fix to be applied directly; they are unobserved. Defects **D-4** and
**D-5** were found during the work and left alone — see Known Defects for why.
Nothing was changed about the lake decorations' count, placement, occlusion by the
hills, or geometry of any kind.

### State left by defect D-1 (v73.7)

Six constants in `PaperRenderer`'s companion now carry each sky sprite's blit
geometry — `CELESTIAL_DISC_ORIGIN_UNITS`/`_SCALE`, `SUN_GLOW_ORIGIN_UNITS`/
`_SCALE`, `STAR_SPRITE_ORIGIN_UNITS`/`_SCALE` — and all six sky-sprite call sites
read them. **Do not inline them back.** They are not tidiness: a sprite's pixel
size, its scale convention and its origin are only correct together, nothing in a
PNG records which convention applies, and a literal inside a `Canvas`-taking
function cannot be asserted. That combination is precisely how D-1 happened
without anything failing.

`SkySpriteAnchoringTest` reads the PNG headers off disk and checks them against
those constants, so the pairing is pinned from both ends. The first version of
that test declared the expected values itself and **four of five mutations
survived** — it agreed with itself while the code moved underneath it. If a future
change makes that test awkward, fix the change, not the test.

The star extents are symmetric again (`STAR_SPRITE_LEFT_EXTENT_PX` ==
`STAR_SPRITE_RIGHT_EXTENT_PX`), which narrows the star-field tile range on its
own. That is a consequence, not a separate decision.

One thing D-1 did **not** address: the sunburst now reaches `±0.182 W`, wider than
the celestial keep-out margin of `0.12 W`, so near the ends of the day arc the rays
cross the screen edge. `celestialParallaxOffset` still bounds the **disc**, by
design — widening it to cover the rays would cut the swipe parallax further, and
the maintainer's approval covered the disc.

### Environment

- **No device or emulator.** 1 CPU, ~4 GB RAM. Nothing can be visually verified.
- **The filesystem resets between sessions.** JDK 17, Android SDK and Gradle
  caches must be reinstalled every time — full procedure in `CLAUDE.md` §3,
  including the non-obvious JDK truststore fix required behind the TLS
  intercepting proxy and the `-Xmx1400m` ceiling below which the build is
  silently killed.
- **Budget:** setup ~5 min, cold `assembleDebug` ~12 min, `lintDebug` ~4 min,
  incremental test run ~2–3 min.

### Expected workflow for the next session

1. Set up the environment (`CLAUDE.md` §3) and confirm the build is green
   **before** changing anything.
2. Read this file and confirm the current phase and next task with the
   maintainer.
3. Analyse before proposing; propose before implementing; get approval for
   anything visual.
4. Implement, then run every applicable verification gate above.
5. Update `ROADMAP.md` first, then the other documentation, then
   `RELEASE_HISTORY.md`.
6. Build the release ZIP, extract it to a clean directory, and prove it is
   self-sufficient by building and testing from the extract.
7. State explicitly what could not be verified.

### Pending decisions to raise

D2, D3, D4, D5 are open — see Pending Decisions above. **D1 has been explicitly
deferred by the maintainer: record it, do not act on it, do not reopen it.**

### The rules most easily broken

- The external reference's name must never appear in the repository, in any
  form, including in binaries and filenames. It exists only in private
  conversation with the maintainer.
- Never claim a tool or capability without verifying it in the current session.
- `CLAUDE.md` stays untracked by Git, but **is** included in the release ZIP on
  purpose, so local instructions survive into the next session.
- The project is entirely in English. Conversation with the maintainer may be
  in Italian.
