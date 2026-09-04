# BACKLOG_v4_21.md — what v4.21 decided, and what it left open

**Replaces `BACKLOG_v4_20.md`, which closed the v4.19 numbering with fifteen outcomes and three
new items.** That file's items are settled and are not restated here; the one it deliberately left
open is carried forward below as item 18, unchanged.

Numbering continues from it. Items 19 onward are new to this release.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED**
(decided against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not
rediscovered), or **OPEN** (left undone on purpose, with what closing it would take).

---

## Summary

| item | what | outcome |
|---|---|---|
| 18 | An unreadable custom theme loses the whole store | **OPEN**, carried forward from v4.20 |
| 19 | The GL goldens' reference environment | **RESOLVED** as a decision — they are authored on the device now |
| 20 | The emulator side of that decision is a derivation, not an observation | **OPEN**, and it is a one-run debt |
| 21 | The occlusion pass modelled a tree that no longer existed | **RESOLVED** |
| 22 | `ShopFrontVisibilityTest` mirrored the same stale numbers | **RESOLVED** with 21 |
| 23 | The snow cap repeated the crown by care rather than by construction | **RESOLVED** |
| 24 | Two tree sprites still carry a leading transparent margin | **DOCUMENTED**, it is the shared origin |
| 25 | The palm is now the odd tree out | **OPEN**, and out of this release's perimeter |
| 26 | The preview's fir baubles are placed by eye | **DOCUMENTED** |
| 27 | Two ceilings, one pass: CPU headroom and the sprite budget | **DOCUMENTED** |
| 28 | `day.png` and `people-group.png` are the same picture | **RESOLVED** — option C, after the premise was measured |
| 29 | The pavement focus does not catch a change in how many people there are | **DOCUMENTED** |
| 30 | Hand-maintained counts still in the current-state documents | **OPEN**, deliberately not swept in one pass |

---

## 18 — An unreadable custom theme loses the whole store

**OPEN, carried forward from `BACKLOG_v4_20.md` unchanged.** `customThemeDataFromJsonString` wraps
the whole read in one `catch` returning `CustomThemeData.EMPTY`, so one damaged entry discards every
other saved theme. The obvious fix — skipping unreadable entries — is worse, because the next save
would then write the store back without the damaged theme and silently destroy user data. Closing it
properly means a read that is *marked* partial and blocks the write path until the user is told,
which is a UI decision as much as a data one. Nothing in v4.21 touched this path.

## 19 — The GL goldens' reference environment moved to the device

**RESOLVED, as a decision.** The three GL goldens (`gl-day`, `gl-lake-busy`, `gl-thunderstorm`) were
authored on the emulator's reference driver. The tree redraw moved all three by **8.80% of their
outline** against a 3% gate — a real change, not driver noise, and four times the worst driver
difference ever measured. Regenerating them needs the emulator that authored them, and this machine
has none: no `/dev/kvm`, no system image, verified in v4.20 and again here.

The two obvious answers were both bad: regenerate somewhere else and take the result on trust, or
ship three red tests. **The third answer was already in the file.** Item 1 of the v4.19 backlog
characterised the Adreno-versus-emulator gap and it is *symmetric* — a property of the pair, not of
one side:

| | edge displacement, device vs emulator-authored golden |
|---|---|
| `gl-day` | 1.18% |
| `gl-lake-busy` | 1.07% |
| `gl-thunderstorm` | 0.92% |
| re-measured in v4.19 | 1.2–1.4% |
| the gate | **3.00%** |

Authoring on the OnePlus 6T makes the device side exact and leaves the emulator side displaced by
the same 0.92–1.18%, comfortably inside the same gate. **No tolerance was touched**; the gate is
still 3% and `GlGoldenMetricTest` still holds both ends of it. `GlDriverGapGuardTest` needed no
change at all: it measures the gap between whatever driver is running and whatever is committed, so
it now reads ~0 on the device and the characterised gap on the emulator — the same guard read from
the other end.

Recorded in `GlGolden.EdgeDisplacement`'s own doc and in `GlDriverGapGuardTest`'s, so the next
person to open either file finds the decision beside the number that justifies it.

## 20 — The emulator side of item 19 is unverified

**OPEN, and deliberately small.** That the emulator will land within 0.92–1.18% of the
device-authored goldens is a derivation from a measured symmetric gap, not an observation: it cannot
be observed on this machine. **The first run on a machine with an emulator must confirm all three
still pass there.** If it does, this item closes with a measurement. If it does not, the honest
outcome is per-driver golden sets, which v4.20 already priced and rejected — but that decision would
then be made against evidence rather than against an estimate.

Until then the risk is bounded and one-directional: the device, which is where every visual
judgement on this project is taken, is exact.

## 21 — The occlusion pass modelled a tree that no longer existed

**RESOLVED.** `SceneObjectCatalog` decides where a shop can stand by boxing everything nearer to it,
and its tree boxes were literals: crown `x ± 41`, `y -118..-44`; trunk `x ± 5`, `y -44..0`. Those are
the v4.20 artwork's numbers. The "Quercia larga" crown is `x -50..51`, `y -118..-52` and its stem is
32 units wide and 62 tall — **the pass was modelling a stem three times narrower than the one it was
placing shops around.** Nothing failed, because `baseScale` is derived from a variant's declared
*height* and the redraw changed only widths, so no scale, no cull extent and no golden could see it.

Re-derived from the shipped content boxes: `halfWidthUnits(TREE) = 51`, crown box `±51 / -118..-52`,
stem box `±16 / -62..0`, and `verticalMemberBox` likewise. The stem box is deliberately the strict
reading — a V-shaped stem modelled as its widest rectangle — because the rule it serves is "a door
and a window stay clear for their whole height", and a box that under-reports the flared foot would
let a trunk park on a doorway.

**Measured consequence, on all twelve built-in layouts:** the ceiling held without being touched.
Worst occlusion fell from **38.53% to 31.17%**, mean from 24.59% to 24.56%, and no shop exceeded the
32% *target* tier, let alone the 40% ceiling. Eight shops of twenty-four moved, six by one to three
probe steps (21.6–64.8 px) and two to a different slot entirely. **Not one tree had to be stepped
aside** by the last-resort branch. A 27% wider crown and a 3.2× wider stem made the picture better,
because the pass re-parks until it reaches the target tier and the old worst case had been sitting
in the ceiling tier.

## 22 — The test mirrored the same stale numbers

**RESOLVED with 21.** `ShopFrontVisibilityTest` deliberately re-derives the catalogue's private
geometry rather than importing it, so "a bug in the separation pass and a bug in this measurement
have to agree to hide a covered shop". They had agreed: the test carried the same four stale
literals. Both sides are re-measured from the artwork now, independently, and the duplication is
kept for the reason it exists.

**The lesson is not "share the constants".** It is that a duplicate only guards against a *typo*,
never against a stale premise, because a redraw invalidates both copies at once. What actually
caught this was going back to the sprites and measuring them.

## 23 — The snow cap now repeats the crown by construction

**RESOLVED.** v76.1 had to redraw `tree_canopy_snowcap` because it had been cut for a different
crown and left a rim of foliage above the snow; `TreeArtworkAlignmentTest` has guarded it since by
sampling four rows. Sampling is the weak part: a cap can pass four rows and be wrong elsewhere.

The new cap's source **clips three snow bands against a copy of `tree_canopy`'s own circles**, so its
silhouette *is* the crown's wherever the snow covers full width — the property holds by construction
rather than by care. Measured on the shipped pixels: **zero cap pixels fall outside the crown**, and
the first **94 of its 114 rows** carry the crown's own first and last opaque column.

The test was re-derived rather than re-tuned: it now asserts the subset property on *every* row and
requires the exact-match run to reach at least 72 px (24 units) from the top, which a cap cut inside
the shoulders fails on its first row. One old assertion was **deleted rather than adjusted**: the
v3.7 "centre it by the canvas width difference" hypothesis is arithmetically a no-op now that both
canvases are the same width, so it could no longer fail. What it was evidence for — that the shared
origin is the only place the cap fits — is asserted directly instead, over five displacements.

Measuring also found one thing that is *not* true of this artwork and was not asserted anyway:
sliding the cap **down** spills nothing, because the crown is 198 px tall against the cap's 114. That
slide is caught by the silhouette test and by the shared-origin equality, not by a spill count.

## 24 — Two tree sprites keep a leading transparent margin

**DOCUMENTED.** `tree_canopy_snowcap` and `tree_dead_branches` are blitted at the crown's own origin
(`SNOWCAP_X == CANOPY_X`, `DEAD_BRANCHES_X == CANOPY_X`), so a snow cap and a set of bare limbs meet
the trunk exactly where the leaves did. The leading margin each carries — 6 and 11 units — **is** that
shared origin.

The trailing padding on both was removed in this release, because that needs no origin compensation
and costs nothing. What remains is **~55 KB of decoded memory** recoverable by giving both sprites
derived origins, in the way `tree_fir_snow` now has one. It was not taken: the release has 154 KB of
headroom under the 29 MiB ceiling, and turning an equality into a derived offset is a weaker guard
than the one v3.7 installed after the preview and the renderer drifted apart on exactly this pair.

`KNOWN_PENDING_CROP_COUNT` in `tools/assets/tests/test_normalize.py` went from 0 to 2 and says all of
this at the point of the count, so the exception is a declaration and not a bumped number.

## 25 — The palm is now the odd tree out

**OPEN, and outside this release's perimeter by instruction.** `palmtree_trunk` and the frond fan
belong to the Beach and Desert themes and were left untouched: a v4.20 drawing standing beside a
v4.21 one. On the device the palm does not clash the way the old fire engine clashed with the new
cars in v4.19 — it is a different species with its own silhouette and its own fixed art, so it reads
as "a palm" rather than as "the old tree" — but its trunk is the thin straight rod the leafy tree
has just stopped being, and the two now disagree about what a trunk looks like.

Closing it means redrawing `palmtree_trunk` in the stocky flared language and re-cutting
`palmtree_fronds_frost` against whatever fan results, with its own concept pass and its own device
look. It is not a defect and there is no test to write; it is the next artistic pass if the
maintainer wants one.

## 26 — The preview's fir baubles are placed by eye

**DOCUMENTED.** `ThemePreviewScene.fir()` decorates the gallery card's fir with three `star_sparkle`
blits at hand-picked offsets, not with the wallpaper's `drawChristmasLights` ellipse. Their centres
sat off the fir's silhouette before this release and still do, on a card 320 units wide where a
60-unit sparkle overlaps most of the tree anyway.

The fir's two *structural* offsets were moved with the redraw and are shared with the renderer's
derivation; the baubles were left alone because this release changes no decoration artwork and the
card reads correctly on the device. Recorded so the next person to look does not mistake it for
drift from the renderer — it never agreed with the renderer, by design.

## 27 — Two ceilings, one pass: what the tree redraw spent

**DOCUMENTED.** The "Quercia larga" spent margin on two different ceilings in the same release, and
they are recorded together here because the second one is easy to discover only after the first has
already been spent.

**CPU — DICHIARATO, measured by the v4.21 implementation pass and deliberately not re-measured
since.** The release costs **3–4 points of CPU** more than v4.20: **64% of a core → 67–68%**, two
samples each side, on the OnePlus 6T at a settled process. The cause is the artwork, not noise: the
new crown and stem blit **33% more pixels per tree** (58 572 → 77 856 px). Frame cadence, PSS and
dropped updates were all unchanged (29,50 → 29,35 wallpaper updates/s; PSS lower if anything, well
inside its own spread).

**Do not re-measure this casually.** A CPU number here is only worth something under the v4.20
protocol — same theme, same elapsed settle, same process history, and CPU summed over the package's
**threads** rather than the pid alone — and a badly taken measurement is worse than none, because it
becomes the number the next person quotes. If it needs re-measuring, do it properly or not at all.

**Sprites — MISURATO in this pass, independently of the figure that was handed over.** 266 PNGs in
`app/src/main/res/drawable-nodpi`, decoded ARGB_8888 footprint **30 254 580 B = 28,853 MiB** against
the **29 MiB = 30 408 704 B** ceiling: **154 124 B of margin, 0,51% of the ceiling.** Same numbers
the handover carried, arrived at with a separate script.

**The point of filing them together.** These are two ceilings on the same phenomenon — how much
drawing the scene can afford — and the tree pass moved both in the same direction at once. Half a
percent of sprite budget and three points of CPU are each survivable alone; what should not happen
is somebody opening the next fill-heavy pass, spending the CPU headroom because the sprite budget
looked fine, and finding the other ceiling on the way out. **Read them as a pair.** Item 24's ~55 KB
of recoverable padding is the cheapest lever on the sprite side and is still unspent.

## 28 — `day.png` and `people-group.png` were the same picture

**RESOLVED, by option C, after the premise behind it was measured rather than argued.**

**The premise, and the measurement that settled it — MISURATO.** The question was whether
`people-single` and `day` differ *only* in the people density, because if so the 20-versus-100 pair
survives option C with the 100% frame simply renamed. Both scenes were reflected field by field on
the device:

| | verdict |
|---|---|
| `dayPhase`, `sceneSeconds`, `warmUpFrames`, `warmUpDeltaSeconds`, `themeId`, `weather` | identical |
| resolved `SceneCustomization`, all **40** fields | **2 differ**: `people.density` (0.2 vs 1.0) and `peopleNightDensity` (0.2 vs 1.0) |
| the two customizations with only the density restored | **exactly equal** |

`name` and `focus` differ too, but neither is a rendering input: one selects the PNG, the other is
the assertion. **The premise holds.**

**What was done.** `SceneGolden.assertMatches` gained an `extraFocus` parameter — focus rectangles
supplied at the assertion site, three lines of harness. `PeopleGoldenTest`'s `people-group` test is
now `people-at-full-density`, asserting `SharedGoldenScenes.day()` with `PAVEMENT` as extra focus,
so `day.png` carries both assertions and `people-group.png` was **deleted, not regenerated**.
`SharedGoldenScenes.day()` is byte-for-byte untouched, which is why the GL suite still measures only
`SUN_GLOW` — **verified by running it**: `day/sun glow: 0.000% of 64516px differ by >=4` before and
after, identical.

## 29 — The pavement focus does not catch a change in how many people there are

**DOCUMENTED, and found while verifying item 28 rather than looked for.**

`PeopleGoldenTest`'s class doc says of the pavement band: *"Measured against this band instead, a
figure that moves, vanishes or swaps places with another one fails."* The first half is true; the
last part is not, at today's tolerances. **MISURATO on the OnePlus 6T**, against the two frames the
band is asserted on (band 39 240 px, focus limit 2% = 784 px; frame 288 000 px, whole-frame limit
0.2% = 576 px):

| regression | band | whole frame |
|---|---|---|
| every pedestrian hidden, `day` frame (density 1) | 390 px, 0.994% — **passes** | 390 px, 0.135% — **passes** |
| every pedestrian hidden, `people-single` frame (density 0.2) | 111 px, 0.283% — **passes** | 111 px, 0.039% — **passes** |
| density halved on `people-single` | **0 px** — passes | 0 px — passes |
| **the density ignored entirely** (0.2 drawn as 1) | 279 px, 0.711% — **passes** | 279 px, 0.097% — **passes** |

So **if the density setting stopped working, no golden would fail.** That is not a consequence of
this release's change — the numbers are the same whichever PNG the band is asserted on, and the pair
was equally unprotected when it was called `people-group` — but it means the claim these two frames
have carried since v4.2 was stronger than the assertions behind it. The wording in
`people-at-full-density` is now the reduced version that the measurements support: the two committed
PNGs genuinely differ, so the setting demonstrably changes the picture and a human can see how; the
gates do not assert that it still does.

**Not closed here, on purpose.** Closing it means changing a metric — a tighter focus limit for this
band, a smaller rectangle around the figures, or a pixel-count assertion instead of a difference
ratio — and picking one to make a verdict come out is exactly the move this whole thread has been
about refusing. It needs its own derivation: what regression must fail, measured, and what the
driver-and-antialiasing floor is on that rectangle, measured, with the gate placed between them.

## 30 — Hand-maintained counts still in the current-state documents

**OPEN, and deliberately not swept in one pass.** The golden-count error and the sprite-count error
are the same disease: a number typed into a document and never re-measured. A limited sweep of the
three current-state documents (`README.md`, `CLAUDE.md`, `ARCHITECTURE.md` — not the historical
reports) found these, and only the first group was corrected:

**Corrected in this pass**, all one fact repeated — the shipped-sprite coverage, measured at v4.21
as 266 entries / 134 with an SVG / 132 recolours:

- `README.md` "125 PNGs … each generated from an SVG source" and "the 125 shipped sprites"
- `CLAUDE.md` "regenerates 125 of the 221 shipped sprites … the other 96 are declared gaps"
- `ARCHITECTURE.md` "all 221 sprites carry a registry entry, and 125 of them carry an SVG source"

**Reported, not corrected** — each needs its own measurement before anyone touches it:

| where | claim | note |
|---|---|---|
| `ARCHITECTURE.md` §"132 `person_*` sprites" | 132 | **measured 166**; but the table is headed *"Value (measured at v4.10)"*, so it is a dated record and correcting it in place would destroy that |
| `ARCHITECTURE.md` "44 sprites are selected from a table at draw time" | 44 | not re-measured; needs the lookup-group definition to count against |
| `ARCHITECTURE.md` "34 sprites carry margin on purpose" | 34 | `SpriteCanvasConventionTest` measures 49 sprites not touching any edge, but that is a **different metric** from `normalize`'s; needs deciding which one the sentence means |
| `ARCHITECTURE.md` "59 sprite placements across 12 themes" (`PreviewRendererAgreementTest`) | 59 | the test prints its own count at run time; the prose may be stale since v4.21 extended it to a third group |
| `ARCHITECTURE.md` "1085 tests" | 1085 | headed *"Verified build (measured at v4.10)"* — dated, correct for its date |
| `CLAUDE.md` "688 tests … plus 12 Python tests" | 688 / 12 | explicitly *"As of v2.16"* — dated, correct for its date |

**The rule worth extracting**, and the reason this is filed rather than fixed: a count in prose is
only safe if it carries **when it was measured** or **the command that reproduces it**. The dated
ones above are fine as they are. The undated ones are the ones to fix, one at a time, each with its
measurement — not in a documentation sweep, which is how a wrong number gets copied into three more
places.

