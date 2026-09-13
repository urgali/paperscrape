# BACKLOG_v4_30.md — what v4.30 decided, and what it left open

**Replaces `BACKLOG_v4_28.md` for new items only**, and **carries forward by name** the items that
file still leaves open — it moved to [`docs/archive/`](docs/archive/) in this release, under the
convention the v4.24 documentation pass established, and the table below is what makes moving it
safe. `BACKLOG_v4_29.md` stays in the repository root: two of its items are still open and it
carries the two items of the backlogs v4.29 archived. Numbering continues: `BACKLOG_v4_29.md`
reached item 93, so this file starts at 94.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided
against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered), or
**OPEN** (left undone on purpose, with what closing it would take).

The measuring device is a **Blackview BV6600** (MediaTek Helio A25, PowerVR GE8320, Android 10,
720×1440). This release used it for three things it is the only place for: counting the draw calls a
figure in five layers really costs, sweeping the twelve themes to see whether the atlas still holds
everything, and re-authoring thirty Canvas goldens. Everything else — the decomposition, both
ceilings, the child's proportion, the crossing counter — was measured on the host first.

---

## Carried forward from `BACKLOG_v4_28.md`

Restated here by number and one line each, so that nothing is lost by the move. The reasoning stays
in the archived file; read it there.

| item | from | what | still |
|---|---|---|---|
| 18, 25, 30, 40, 50–55 | v4.23 / v4.24 | carried by `BACKLOG_v4_28.md` from the three backlogs it archived | **OPEN** as recorded there |
| 56 | `BACKLOG_v4_25.md` | The three GL reference frames portray people who no longer exist | **CLOSED in v5.0** as a decision — see `BACKLOG_v5_0.md`. What this line said when v4.30 wrote it: "and more so: v4.30 redrew the children and recoloured everybody, and the three Adreno-authored references are deliberately still not re-authored — re-authoring them on this device is what would cost the cross-driver check `GlDriverGapGuardTest` exists for". **Two words of that were wrong.** The references had not been Adreno-authored since v4.26 (item 71, ratified v4.27), so re-authoring them cost nothing: the cross-driver check had already stopped measuring anything three releases earlier |
| 63 | `BACKLOG_v4_25.md` | A redraw left six stale sizes in the comments, and the guard that exists for exactly that missed all six | **OPEN**; item 94 is the same failure again |
| 83 | `BACKLOG_v4_28.md` | The wave stands over more of the lake band than its derivation assumed | **OPEN**, untouched here |

**`BACKLOG_v4_29.md` stays in the root**, carrying item 92 (`drawPreviewPair` is dead and safely
removable) and item 93, plus items 67 and 78 from the backlogs v4.29 archived. **Item 93 is closed
by this release** and the entry below says how.

---

## Summary

| item | what | outcome |
|---|---|---|
| 94 | The layer investigation's own §1 and §6 say a mask carries three regions; its §A.2 says one, and the two halves of one report disagree | **DOCUMENTED** — the same failure as items 82 and 63, recorded because it is this project's recurring one |
| 95 | "The crossing counter is off screen by construction" — it is on screen 52.8 % of the time | **RESOLVED** — measured, and the off-screen guarantee taken from the cull that actually ran |
| 96 | Twelve winter window recolours ship and no draw path can reach them | **RESOLVED** — retired, and the season column that pretended otherwise retired with them |
| 97 | A table nobody reads is still alive if a doc comment mentions it | **RESOLVED** — comments stripped before counting, and the rule shown to bite |
| 98 | Six goldens were already 2–67 % of the way to their limit before this release began, on the device that authored them | **DOCUMENTED** — measured on both builds; re-authoring here resets them, and the cause is a bird's wing at a zero crossing |
| 99 | The rain's ceiling was expressed against a figure this release redrew | **DOCUMENTED** — the rain did not move and the sentence did; the maintainer may want to judge the rain from a photograph |
| 100 | Four regions, and the draw count did not move | **DOCUMENTED** — 1 `glDrawArrays` per frame on three crowded themes, counted on the device |
| 101 | Two of the asset tooling's own tests were already failing in v4.29, and nothing runs them | **OPEN** — inherited, measured on v4.29's own extraction, deliberately not fixed here |
| 102 | The child's height lived in **five** places, not four, and the fifth was only findable on the device | **RESOLVED** — moved with the rest, and the reason the host could not see it is recorded |
| 103 | Drawing a person in layers costs the `Canvas` backend 13.6 %, measured | **DOCUMENTED** — it lands only on the EGL fallback, and the A/B is here |
| 93 | (from `BACKLOG_v4_29.md`) The only sprite reduction the headroom permits | **CLOSED and superseded** — the people set shrank by more than the proposal offered, by a different route |

---

## 94 — One report, two answers, and the second one is right

**DOCUMENTED. This is the third time this exact shape has been written down** — items 82 and 63 are
the other two — and it is worth one more entry because the two previous ones were about comments
going stale, while this one is about a conclusion being contradicted **inside the same document**
and the wrong half being the one a reader meets first.

`indagine_strati/REPORT.md` §1 and §6 say that a weight mask has three colour channels of which one
is used, so two more colourable regions come "at the same price". Its appendix §A.2 says the
opposite and is right:

```glsl
gl_FragColor = vec4(tex.rgb * v_Color.rgb, tex.a * cover) * abs(v_Color.a);
```

`tex.rgb * v_Color.rgb` is a component-wise product, not a sum of products. One tint per draw times
three channels is the **same** colour filtered three ways, not three colours. Resolving three
regions from one texture needs `tex.r*C1 + tex.g*C2 + tex.b*C3`, which is three colours per vertex —
nine floats where there are four, so the vertex grows from eight floats to thirteen for **every**
vertex in the scene, or a second program and a flush every time a person alternates with anything
else. That is exactly the cost v4.29 removed.

So: **one region, one mask, and the texels are paid per region.** A file may hold three as three
rectangles of the atlas; the file is free and the area is not.

**What v4.30 did with it.** It took §A.2 and shipped four separate masks — one of the three packings
the release brief offered — which needs no shader change at all and keeps the batch, the vertex
format and the draw count exactly where v4.29 left them (item 100 below is the measurement).

**Why it is recorded rather than just obeyed.** The report is the source this release was built
from, its §1 is where a reader starts, and its correction is 400 lines later in an appendix added a
day afterwards. A sentence that survives its own refutation is this project's most repeated defect,
and `AI_PROJECT_RULES.md` §3's answer — do not clean the history, annotate it — is what this entry
is. The investigation is not part of the repository, so the annotation lives here.

---

## 95 — "Off screen by construction" is on screen 52.8 % of the time

**RESOLVED**, and the resolution is v4.28's machine rather than a new one.

The plan this release was written from says the colours may be re-dealt on the crossing counter
alone, because *"the point at which the count passes 1 is off screen by construction, so nothing
changes under the eye — the same convention the cars use"*.

**The difference between a pedestrian and a car is what makes that false.** A car has one body and
it drives off the edge; `CarSelection.offScreen` is about that body. A pedestrian is tiled: it
exists at `x + k · tileWidth` for every whole `k`, and the draw pass walks the copies that intersect
the viewport. When `tileFraction` wraps from just under 1 to just over 0 the figure does not move at
all — the copy at `k` stops being the visible one and the copy at `k + 1` starts. Whether that
instant is visible depends on `shiftXWrapped`, which scrolls with the ground.

Measured, on the scene's own geometry (`PedestrianTileWrapTest`, and the same arithmetic on the
host):

| | |
|---|---|
| share of a crossing during which no copy of a walker is on screen | **47.2 %** |
| share of scroll positions at which the counter turns over **in view** | **52.8 %** |

The first number is why an off-screen rule is possible at all; the second is why the counter alone
is not it.

**What ships instead.** The colour is a pure function of *(theme, person, crossing)* — so the
determinism the goldens need is untouched — and the crossing number **in force** is held per walker
and moved only when that walker's own draw pass reports that no copy was drawn. That is
`PedestrianCarry.nextCarrying`'s shape, which v4.28 built for the umbrella and which the plan itself
points at in its own note on umbrella colours: *"the machine is already in production: it is not to
be invented, it is to be reused."* The guarantee then comes from the cull that actually ran, which
is a fact about the frame rather than an argument about geometry.

---

## 96 — Twelve window recolours nobody could reach, and the axis that hid them

**RESOLVED.** `BACKLOG_v4_25.md` item 57 recorded them and recommended keeping them; v4.30's brief
reversed that call, and the reversal turned out to be the smaller half of the change.

Indoors the season index was `0` whatever the theme did — *the hat belongs to the street, not to the
room behind the pane* — so `person_*_winter_head_window_skin*` was shipped and never selected. Item
57 counted it at 297 840 B of texels and 1 206 576 B of decoded bytes, and it had been reported as a
defect twice by two different readers.

What made it invisible was not the files but **the `Exposure` enum**. A call site asking
`seasonIndexFor(Exposure.INDOORS)` reads as a choice being made; the answer was a constant. So:

- the four winter window shapes are **not converted** to layers — they are retired;
- `PeopleLayerTable.WINDOW` has **no season axis**, so there is nothing to choose;
- the enum and `seasonIndexFor` are gone, replaced by `outdoorSeasonIndex()`, which is only asked by
  the two places that really have two seasons: the street and the cars.

`IndoorClothingTest` was rewritten around the absence: it now fails if a window bust ever chooses a
season again.

**The eight un-suffixed bases stay**, as item 57 recommended, and are declared `usage: "orphan"` in
`tools/assets/sources/sprites.json` — see item 97 for why that declaration became necessary and why
it is the right place for it.

---

## 97 — A table nobody reads is alive if a comment mentions it

**RESOLVED.** `BACKLOG_v4_25.md` item 58, open since v4.25, and it is the mechanism that kept item
96 invisible.

`SpriteReachabilityTest`'s rule is *a `val` whose initialiser mentions `R.drawable.` and whose own
name occurs at most once across the main sources is a table nothing reads.* It counted occurrences
in the raw source, so a mention inside a KDoc block satisfied it. `personWindowHeadDrawables`
occurred twice — once as itself, once inside the doc comment of the table that replaced it — and so
did `personWalkDrawables`.

v4.30 deleted the tone tables, which took both of those mentions with them, and the rule fired on
its own. **That is the instance, not the hole**, so the rule is tightened too: comments are stripped
before counting. Shown to bite by declaring a table referenced only from prose and watching it fail,
then removing it.

What the stricter rule found in the rest of the module: **nothing**. The v4.25 note expected it
would "very likely find more than this one table"; it does not, and the triage that was left for a
later pass turned out to be empty.

**The consequence, followed through rather than patched around.** Fourteen un-suffixed bases are now
named by nothing: the eight window busts, and the boy's six walk frames — `ThemePreviewScene` blits
the other three families' walkers itself for the gallery card, and never his. They are declared
`usage: "orphan"` with the reason, which is what that test asks of an unreachable sprite instead of
a dead table that hides it from lint.

---

## 98 — Six goldens were already carrying device drift before this release touched them

**DOCUMENTED**, and it changes how a golden failure in this project should be read.

Attribution for this release was done by rendering every Canvas golden **on both builds** — v4.29
extracted from its own delivery ZIP, and v4.30 — on the same device in the same session, and
diffing the two renders rather than each against the committed file. That is what proved every
v4.30 pixel is inside the pavement-and-road band. It also showed something nobody had measured:

| golden | v4.29's own render vs its committed golden |
|---|---:|
| `lake-dolphin-leap` | **384 px** (67 % of its 576-pixel budget) |
| `shops-closed-night` | 112 px |
| `lake-boats` | 62 px |
| `night` | 23 px |
| `traffic-day`, `traffic-day-sparse` | 14 px |
| every other Canvas golden | 0 |

All six passed, because all six are under the gate. But `lake-dolphin-leap` had **192 pixels of room
left**, and v4.30's 399 pixels of legitimate change is what pushed it over — so the failure named a
release that was responsible for a little over half of it.

**The cause, for the worst one.** A bird's wing-flap is a vertical mirror switched by
`sin(seconds · 9 + phase · 6.28)`, and at that scene's `sceneSeconds = 200` the argument is about
1800 radians. The frame the golden was authored from caught the sine on one side of a zero crossing
and this device catches it on the other; the bird is in the same place, drawn upside down. Nothing
about it is wrong — it is a frame of an animation sampled at a knife edge.

**Not fixed here**, and the options are worth recording rather than choosing in a release about
something else: move the scene's `sceneSeconds` off the crossing, damp the flap near zero, or accept
it and re-author. Re-authoring the thirty goldens in this release resets all six to zero, so the
symptom is gone until the next thing perturbs it.

**What to take from it:** a whole-frame golden budget is not all available to the next change, and
how much of it is already spent can only be found by rendering the previous release. The
`-e dumpFrames true` switch added to `SceneGolden` in this release is what makes that a five-minute
measurement instead of a bisect.

---

## 99 — The rain's ceiling named a figure this release redrew

**DOCUMENTED**, and deliberately not acted on.

`PrecipitationScaleTest` holds the longest raindrop to a fraction of *the scene's smallest human
figure*, which was a child at 1.356 m: 0.58 m is 0.43 of one, against a ceiling of 0.44.

v4.30 redrew the children at 0.65 of an adult instead of 0.779 — a decision about the **drawing**,
taken by the maintainer from photographs — so the same unchanged drop is now **0.51 of a child**.
The old fraction no longer holds.

Three ways out, and the reasoning for the one taken:

- **shrink the rain to 0.50 m.** An unasked-for change to precipitation, made as a side effect of a
  change to children, and v4.26 and v4.27 both spent rounds on those numbers by eye. Refused.
- **raise the fraction to 0.52.** Raising a tolerance to stay green, which this project forbids in
  as many words. Refused.
- **restate the ceiling against the figure the scene's metre is actually defined by** —
  `SceneSpace.PERSON_METRES_TALL`, the adult, which did not move — at exactly the value it has
  always had in those terms: `0.58 ≤ 0.341 × 1.75`. What changed is which figure the sentence names.
  Taken.

**Left for the maintainer:** whether 0.51 of a child is too much rain is a question for a
photograph, not for arithmetic. Nothing in the scene moved, so there is no hurry; the record is here
so the next person to read that ceiling knows it was re-anchored and why.

---

## 100 — Four regions, and the draw count did not move

**DOCUMENTED**, because the brief asked for the number before anything else and because the
arithmetic has been wrong on this project twice.

The plan predicted the obvious consequence of drawing a person in layers: *"a pedestrian is one
draw today; in layers it becomes three, and with twelve people that is twenty-four more draw calls a
frame."* Counted on the BV6600, on three crowded themes, with **four** regions and not one
(`GlDrawCallTest`, which reads `GlSceneTarget.drawCalls` off the final frame):

| scene | draw calls | vertices | sprite blits |
|---|---:|---:|---:|
| `crowd-city` | **1** | 3 723 | 168 |
| `crowd-spring` | **1** | 5 295 | 212 |
| `crowd-winter` | **1** | 6 972 | 228 |

One `glDrawArrays` per frame, which is what v4.29 left and what v4.30 hands back. Two things make
that true rather than lucky, and both are deliberate:

- **the additive contribution travels in the sign of the vertex alpha**, not in a blend state.
  `glBlendFunc(GL_ONE, GL_ONE)` is the obvious way to add and would end the batch at every layer —
  two draws per pedestrian, which is precisely the cost the plan feared;
- **the masks are in the atlas like everything else.** Swept across the twelve built-in themes at
  full density in one context: **270 sprite-and-level entries, 0 standalone textures, 516 of 2048
  atlas rows** (`GlAtlasOccupancyTest`). A standalone texture would end the batch wherever the draw
  order crossed it.

So the cost of a region is **six vertices in a batch that was already open**. It did not stop being
zero, and the number is here rather than in a sentence.

---

## 101 — Two asset-tooling tests were already red, and the release procedure cannot see them

**OPEN, inherited, and not fixed here.**

`tools/assets`' own suite has 109 tests and they are **not part of the Level 3 verification
template** (`AI_PROJECT_RULES.md` 12.18 builds and runs the Gradle suites from the extracted ZIP;
`CLAUDE.md` §4 lists `python -m unittest discover -s tests` under asset tooling, as something to run
when touching assets). v4.30 touched assets, so it ran them — and two failed before anything in this
release was written:

```
FAIL: test_normalize.ShippedSetTest.test_nothing_in_scope_still_carries_removable_padding
      AssertionError: 2 != 5
FAIL: test_normalize.TrailingCropTest.test_the_shipped_set_has_no_trailing_padding_left
      AssertionError: [] != ['sailboat_sail']
```

**Measured on v4.29 rather than assumed**: the base ZIP was extracted into a scratch tree and its
own suite run there. Same two failures, same two messages. They are inherited.

What they say:

- `KNOWN_PENDING_CROP_COUNT` is 2 and the real number is **5**. The two recorded ones are
  `tree_canopy_snowcap` and `tree_dead_branches`, whose leading margin *is* a shared blit origin;
  the three that drifted in unrecorded are `dolphin_body`, `sailboat_hull` and `sailboat_sail`,
  which carry 3–27 px of leading padding. Each would need its origin compensated in the same change
  and a device look afterwards, which is exactly the trade the two recorded ones are deferred on.
- `sailboat_sail` also carries 3 px of **trailing** padding, and trailing is the half that costs
  nothing at a call site — no drawn pixel moves. That one is genuinely free apart from keeping the
  SVG, the PNG and the registry in step, which `normalize --apply` is for.

**Why v4.30 leaves them.** Both are about lake artwork and neither is about people; cropping a
shipped sprite is a change to the artwork with a device look attached, and doing it inside a release
about something else is how a small correct change becomes an unreviewed one. Raising
`KNOWN_PENDING_CROP_COUNT` to 5 to make the suite green would be recording the drift as a decision
nobody took — the constant's own comment says to update it *when the crop set is intended to
change*, and this change was not intended by anybody.

**The more useful half of this item is the procedure, not the sprites.** A suite that nothing in the
release checklist runs will go red and stay red, and this one had for at least one release. Either
`tools/assets`' tests join the Level 3 template, or the template says in as many words that they are
the asset author's responsibility and when. That is a maintainer's call about process, which is why
it is here rather than done.

---

## 102 — The child's height lived in five places, and only the device could find the fifth

**RESOLVED**, and worth an entry because of *why* the fifth one hid.

The release brief named four: the KDoc of `SceneSpace.PERSON_METRES_TALL`,
`VehiclePedestrianScaleTest.CHILD_SPRITE_UNITS_TALL` with its `0.775` and its "1.356 m",
`PrecipitationScaleTest.childMetres`, and `build_people_concepts`'s own constant. All four were
moved together and the JVM suite went green.

The fifth is `VehicleScalePixelTest.CHILD_UNITS`, and the full instrumented suite found it:

```
a CHILD at x=30..36 is 14.0px where its row implies 15.8
```

**Nothing on the host could have found it**, and that is the transferable part. The other four
express the child on the **80-unit metre** the size table uses, so they read as the same number and
a search finds them together. This one is the ink a child occupies **in the sprite's own units** —
62 became 64 in v4.25 and 54 now — so it neither looks like the same quantity nor shares a spelling
with it. And it is only checkable by rendering a figure and measuring the pixels, which is an
instrumented test.

`DESIGN_NOTES.md` already has the heading for this — *a redraw invalidates every number measured off
the old drawing, including the ones in tests* — and `BACKLOG_v4_25.md` item 63 is the same failure
one release earlier. What this adds is the shape of the hiding place: **the same measurement in a
different unit is not findable by searching for the number.**

---

## 103 — A person in layers costs the `Canvas` backend 13.6 %, and it lands where it can

**DOCUMENTED**, measured rather than estimated, and accepted.

A person drawn in layers is one `drawBitmap` plus one per region. On the GL backend that is six
vertices in a batch that was already open and the draw count does not move (item 100). The `Canvas`
backend has no batch: every blit is a blit.

Measured on the BV6600, same session, adjacent runs, a crowded `city` frame at full density through
`SceneGolden.render` — nine renders after three to warm the sprite cache, median: **[M]**

| | median frame |
|---|---:|
| v4.29, built from its own delivery ZIP | **82.16 ms** |
| v4.30 | **93.30 ms** |

**+11.14 ms, +13.6 %.** v4.29 ran second, so if the device drifted at all it drifted against this
result rather than for it.

**Where it lands, which is why it is accepted.** `CanvasSceneTarget` is built in exactly two places:

- `ui/ThemePreview.kt`, for the gallery and settings cards — and that draws a `ThemePreviewScene`,
  which blits the walkers' **un-suffixed bases** directly, one blit each. **Unaffected.** The thing
  that kept those 34 bases in the set is the thing that keeps the previews at their old cost;
- `PaperWallpaperService.canvasTarget`, the fallback the wallpaper takes after
  `GlLifecyclePolicy.MAX_CONTEXT_REBUILDS` EGL failures. That is the affected surface, and it is a
  degraded mode by construction: a crowded frame there was already 82 ms, which is 12 fps.

**What would remove it if it ever mattered**: the `Canvas` backend could compose a figure's layers
into one bitmap once per (shape, colour set) and blit that, which is the "colour before the upload"
branch the investigation measured and rejected for the GPU — rejected there because it buys the
decoded ceiling and nothing else, but it is exactly the right trade on a backend with no batch.
Not done here: it is a second drawing path for a fallback, and nothing has reported it.
