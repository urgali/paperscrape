# BACKLOG_v4_31.md — what v4.31 decided, and what it left open

**Replaces `BACKLOG_v4_29.md` for new items only**, and **carries forward by name** the items that
file still leaves open — it moved to [`docs/archive/`](docs/archive/) in this release, under the
convention the v4.24 documentation pass established, and the table below is what makes moving it
safe. `BACKLOG_v4_30.md` stays in the repository root: four of its items are closed or re-scoped
here and three are still open, and it also carries the items of the backlogs v4.30 archived.
Numbering continues: `BACKLOG_v4_30.md` reached item 103, so this file starts at 104.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided
against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered), or
**OPEN** (left undone on purpose, with what closing it would take).

The measuring device is a **Blackview BV6600** (MediaTek Helio A25, PowerVR GE8320, Android 10,
720×1440). This release is a defect round and took **no artwork decision**: the only pixels that
moved are the ones a compensated crop moved by zero. The device was used for three things — the
mutation that attributed item 104, the golden pass that proved the crop identical, and the
photographs item 99 asks the maintainer for. Everything else was measured on the host.

---

## Carried forward from `BACKLOG_v4_29.md`

Restated here by number and one line each, so that nothing is lost by the move. The reasoning stays
in the archived file; read it there.

| item | from | what | still |
|---|---|---|---|
| 67 | `BACKLOG_v4_26.md` | The `_UNITS` frame rule sees Kotlin and not the generators | **OPEN**, untouched here |
| 78 | `BACKLOG_v4_27.md` | The cross-driver GL measurement is no longer taken anywhere | **OPEN**; a condition rather than work |
| 90 | `BACKLOG_v4_29.md` | A pixel claim that omits its unit is invisible to the guard | **OPEN in its stated half**, and item 108 below closes a *different* half of it that item did not anticipate |
| 92 | `BACKLOG_v4_29.md` | `SceneObjectRenderer.drawPreviewPair` is dead and safely removable | **OPEN**, untouched here |

**And `BACKLOG_v4_30.md` stays in the root**, carrying items 18, 25, 30, 40, 50–55, 56, 63 and 83
from the backlogs it archived, plus its own 94 and 100.

---

## Summary

| item | what | outcome |
|---|---|---|
| 104 | Item 98's stated cause is arithmetically impossible. The drift was a redrawn bird, and the whole-frame gate was 41× larger than the weakest thing it must catch | **RESOLVED** — cause attributed by mutation, gate derived down to the measured floor |
| 105 | Item 99 re-anchored the rain **ceiling** off the child and left the **floor** on it, so the floor relaxed 16.1 % with nobody deciding | **RESOLVED** — restated against the adult at the value it has always had |
| 106 | Item 101's two red tests, closed: three lake sprites cropped with their origins compensated, and the suite put in the release template | **RESOLVED** — 109/109, and `n-a` is now an answer that has to be earned |
| 107 | `normalize --apply` has **never once completed**, and it fails after rewriting six files | **RESOLVED** — two defects, both fixed, both shown to bite |
| 108 | The dolphin's origin does not land its content on its leap point, and two separate comments said it did | **RESOLVED** in prose, **OPEN** as a drawing question with the number attached |
| 109 | `SceneSpace.PERSON_METRES_TALL` says the child's height lives in four places, written in the release that found the fifth | **RESOLVED** |
| 110 | Item 103 and `switchToCanvasFallback` describe the same surface with opposite halves of the truth | **RESOLVED** — both paths written down; item 103 itself needs no code change |
| 111 | A measurement against "the previous release" was taken against **this** release, because `adb install -r` silently refuses a downgrade | **DOCUMENTED** — it produced a false attribution that reached the maintainer before the correction did |
| 112 | `wave-storm` is a **warmed-up storm**, which `GoldenScene`'s own KDoc says a scene must not be. It fails about **1 run in 32**, and has since v4.28 | **RESOLVED in v5.0 Fase 0** — the golden pins the strike timer, the guard the KDoc called impossible is in both harnesses, and the shipped lightning is unchanged and measured to be |
| 99 | (from `BACKLOG_v4_30.md`) whether 0.51 of a child is too much rain | **OPEN, for the maintainer** — the photographs it asked for are in `immagini/` |
| 103 | (from `BACKLOG_v4_30.md`) the `Canvas` backend's +13.6 % | **OPEN, unchanged** — re-scoped by item 110, not re-measured; see below for why |
| 93 | (from `BACKLOG_v4_29.md`) the people family's grid-2 packing | **REJECTED**, per the release brief; not reopened |

---

## 104 — The wing-flap story is impossible, the bird was redrawn, and the gate was 41× too loose

**RESOLVED**, and it is the largest finding of this release because the thing v4.31 was asked to
fix was not the thing that was broken.

### What item 98 said

> A bird's wing-flap is a vertical mirror switched by `sin(seconds · 9 + phase · 6.28)`, and at that
> scene's `sceneSeconds = 200` the argument is about 1800 radians. The frame the golden was authored
> from caught the sine on one side of a zero crossing and this device catches it on the other.

It is a good story and every part of it can be checked. All of it was, and it does not hold.

### The arithmetic, on the host

Everything the flap depends on is exactly reproducible: `SceneTime.seconds` is a `Double` the scene
sets literally, `phase` comes from `CandidateNoise.value` — integer mixing — and the seed is
`String.hashCode`, which the Java language specifies. Swept over **every built-in theme × every
`sceneSeconds` a committed golden uses × all six birds** (`BirdFlapSamplingTest`):

| | `|sin(flap)|` | scene seconds to the next sign change |
|---|---:|---:|
| `lake-dolphin-leap`'s own frame, closest of its six birds | **0.2615** | 0.0294 |
| closest approach anywhere in the sweep (`new_year`, 60 s, bird 1) | **0.0038** | 0.00042 |
| a `Double`'s precision at ~1800 radians | 4 × 10⁻¹³ | — |

The worst case in the whole sweep is **ten orders of magnitude** above the precision of its own
argument, and the scene actually accused is **twelve**. Two runs cannot disagree about the sign.
The sweep is kept as a test rather than as this paragraph, because a number in a document goes
stale and `AI_PROJECT_RULES.md` 14.11 is about exactly that.

### The real cause, attributed by mutation

v4.29 was built from its own delivery ZIP and every Canvas golden re-rendered on the BV6600 under
`-e updateGoldens true`, then diffed against v4.29's **committed** files. The six drifting goldens
reproduced exactly — and the boxes are the finding:

| golden | differing | box | where that is |
|---|---:|---|---|
| `lake-dolphin-leap` | **384** | (0,70)–(27,89) | sky |
| `lake-boats` | 62 | (0,148)–(9,302) | sky |
| `night` | 23 | (166,128)–(175,132) | sky |
| `traffic-day` | 14 | (358,58)–(359,67) | sky |
| the other 15 in that class | **0** | — | — |

Not one of them is in the lake. Bird 5 of that scene stands at **x 3.7, y 69.1** — the box.

Then the mutation. The same v4.29 build with **v4.26's `bird_body.png` swapped back in**:

| golden | v4.29 as shipped | v4.29 + v4.26's bird |
|---|---:|---:|
| `lake-dolphin-leap` | 384 | **0** |
| `lake-boats` | 62 | **0** |
| `traffic-day` | 14 | **0** |
| `night` | 23 | 23 |
| `day`, `dusk`, `rain`, `thunderstorm`, `lake-busy`, `lake-empty`, `overcast`, `snow`, `wave-storm`, `bird-facing`, `rain-worst-sky` | 0 | 951 – 1633 |

The second column is the whole attribution. The three that fall to zero were authored in v4.26 and
never re-authored; the eleven that rise were authored in v4.28 **with** the new bird, which is why
giving them the old one breaks them. A census of `drawable-nodpi/` between the two releases finds
**exactly one sprite changed, `bird_body.png`**, in v4.28.

So: a sprite was redrawn, some goldens were re-authored with it and some were not, and the ones that
were not went stale for two releases without failing.

**`night`'s 23 pixels are not the bird and not artwork** — the mutation leaves them untouched and no
other PNG moved between v4.26 and v4.29. The shape is a small bright sliver in the night sky that
the fresh render does not draw. It is a code change in v4.27 or v4.28, unbisected: two more builds
would name it, and the gate below catches it either way. Recorded rather than guessed at.

### Why nothing failed: the gate

`MAX_DIFFERING_FRACTION` was `0.002` — **576 pixels** of a 360×800 frame. `lake-dolphin-leap` spent
**67 %** of that on a stale bird and passed. Its own KDoc ended *"small enough that a sprite moving
by one pixel fails"*; the sprite was not moved by a pixel, it was **replaced**, and the frame passed.

The per-pixel tolerance is a different constant and is the one that absorbs anti-aliasing:
`CHANNEL_TOLERANCE = 8`, which the KDoc's own argument is about. The fraction was a second
allowance stacked on top of it and was never derived from anything.

Derived now, the way v4.22 derives a focus gate — between the measured floor and the weakest
regression that must fail:

- **the floor is 0.** A matching Canvas golden differs by exactly zero pixels on this device: 24 of
  30 in v4.30's attribution pass, 15 of 19 in this release's re-measurement, and
  `TrafficGoldenTest.theWarmedUpFrameIsDeterministic` has been asserting a literal `0.0` between two
  renders of the same scene for releases;
- **the weakest regression that must fail is 14 pixels**, `traffic-day`'s stale bird.

The old gate sat **41×** above the weakest thing it has to catch. Every value in `[0, 14)` is
defensible and all but one are arbitrary, so the gate is the floor: **`MAX_DIFFERING_FRACTION = 0.0`**.

**It is a tightening, and the rule this project has about tolerances is about the other direction.**
Nothing that used to fail now passes.

What is deliberately **not** touched: `MAX_FOCUS_DIFFERING_FRACTION` and the derived per-focus gates,
which measure small patches where a diagonal really is mostly anti-aliased edge; and the three GL
references, which answer to `GlGolden`'s own gates and to `GlDriverGapGuardTest` — re-authoring those
here would spend the cross-driver check (item 56), and this release does not.

**Shown to bite**, not asserted: with the gate at zero, the v4.26-bird mutation fails
`lake-dolphin-leap`, `lake-boats` and `traffic-day`, and under the old gate all three passed.

**What the focus rectangles are for now.** They no longer *catch* a moved sprite — the frame does —
and four comments that said otherwise are annotated rather than deleted. They still say what each
golden is about, which is what a re-authoring needs.

---

## 105 — The rain ceiling was re-anchored and the floor was not, so the floor moved

**RESOLVED.** This is the half of `BACKLOG_v4_30.md` item 99 that was not done, and unlike the half
that was, it had already cost a gate.

Item 99 is a careful piece of reasoning. A raindrop's **ceiling** was `0.44 × child`; v4.30 redrew
the children from 0.779 to 0.65 of an adult; rather than shrink the rain (an unasked-for change) or
raise the fraction (a tolerance moved to stay green), it restated the ceiling against the adult at
exactly the value it had always had: `0.58 ≤ 0.341 × 1.75`. Checked here, that restatement is
arithmetically exact — `0.44 × 1.35625 = 0.341 × 1.75 = 0.59675`.

**Three lines below it, the floor was still `RAIN_LENGTH_MIN_METRES >= 0.20 × childMetres`**, and
`childMetres` is the thing that moved:

| | child | floor | margin under the shipped 0.36 m |
|---|---:|---:|---:|
| before v4.30 (62 units) | 1.35625 m | **0.27125 m** | 0.0888 |
| after v4.30 (52 units) | 1.13750 m | **0.22750 m** | 0.1325 |

**The floor relaxed by 16.1 %** as a side effect of a decision about how tall to draw a child —
which is precisely the failure the paragraph above it argues against, in the same file, in the same
release.

Fixed the same way and at the same number: `0.155 × 1.75 = 0.27125`. No tolerance moves in either
direction, `RAIN_LENGTH_MIN_METRES` is untouched, and what changes is which figure the sentence
names. `childMetres` survives only inside a failure message, and its KDoc now says so.

Two sentences went with it. The ceiling test's own KDoc still opened *"A raindrop is at most 40 % of
the shortest person in the scene"* — refuted by the assertion four lines below it, which had said
0.341 of an **adult** since v4.30, and the drop is **0.51** of a child. And the file's class comment
said every bound is "a relation to something in the world — a child, a head, the skyline", when the
point of both fixes is that a child is a **drawing** and drawings get redrawn.

**Item 99's open half is untouched and is for the maintainer**: whether 0.51 of a child is too much
rain is a question for a photograph. `immagini/v431-pioggia-1x.png` and
`immagini/v431-temporale-1x.png` are that photograph, at 1× on the BV6600 with people on the
pavement. Nothing in the scene moved; there is no hurry.

---

## 106 — The two red asset tests, and the four releases nobody ran them

**RESOLVED**, both halves — the sprites and the procedure — and the procedure is the half
`BACKLOG_v4_30.md` item 101 called the more useful one.

### When it broke, which item 101 did not say

Item 101 established the failures were inherited from v4.29. Measured further here, by reading
`dolphin_body`, `sailboat_hull` and `sailboat_sail` out of **every delivery ZIP from v4.23 to
v4.30**:

```
v4.23 … v4.25   dolphin 345x174 lead=(0,0)   hull 252x51 lead=(0,0)   sail 210x180 lead=(0,0)
v4.26 … v4.30   dolphin 345x174 lead=(4,6)   hull 252x51 lead=(8,0)   sail 210x180 lead=(27,0)
```

**It entered in v4.26**, the release that redrew the lake as concept B "Rilievo", and the canvases
did not change — the redraw put less ink inside the same box. So the tests were red for **four**
releases, not one, and every release in between shipped green because nothing in the checklist ran
the suite that knew.

### What shipped

Three sprites cropped, every origin compensated in the same change:

| sprite | canvas | ink was at | origin | decoded |
|---|---|---|---|---:|
| `dolphin_body` | 345×174 → **342×171** | (4,6) | `DOLPHIN_ORIGIN_X/Y_UNITS` (−57.3,−29) → **(−56.3,−28)** | −6 192 B |
| `sailboat_hull` | 252×51 → **246×51** | (8,0) | (−42,8) → **(−40,8)** | −1 224 B |
| `sailboat_sail` | 210×180 → **183×180** | (27,0) | (−35,−50) → **(−27,−50)** | −19 440 B |

(Those are the sizes **after** the guard cell of the next section; cropped tight to the grid they
were 342×168, 246×51 and 180×180 for 33 120 B, and the guard cell gives 6 264 B of that back. It is
the right trade and the next section is why.)

**26 856 B of decoded bitmap**, and the honest size of that: it is 0.07 % of the shipped set, and on
the GL backend it is **zero** — `GlTextureCache` has uploaded only the content box since v4.29, so
the padding never cost a texel. The crop is worth doing because the invariant is worth holding, not
because of the bytes, and saying otherwise would be the kind of argument this project keeps
catching.

**`KNOWN_PENDING_CROP_COUNT` is still 2 and was never touched.** The two that remain are the
deliberate pair — `tree_canopy_snowcap` and `tree_dead_branches`, whose leading margin *is* a shared
blit origin. The test went green because reality came back to what the constant says.

### Both call sites, which the tool did not say

`normalize --apply` reported *"single call site"* for all three. Every one of them has **two**: the
renderer's blit and `ThemePreviewScene`'s gallery card. Compensating only the first would have left
the preview's boats shifted by the crop. That message is a claim the tool had not earned and is
fixed in item 107.

### The crop was *not* free the first time, and finding out is most of this item

The whole-frame golden gate is zero as of item 104, so every Canvas golden that draws a boat or a
dolphin became a bit-exact assertion that no drawn pixel moved. **Three of them failed**, by
**6 pixels each**, up to 70 levels on a single contour pixel, in frames where the other 288 000 were
identical.

The ink had not moved. Checked, in the sprite's own space: `SpriteBlitter` blits `SCENE_UNITS` at
`origin × 3` px inside a canvas scaled by 1/3, so the dolphin's first ink column sits at
`−171.9 + 4 = −167.9` before the crop and `−168.9 + 1 = −167.9` after. Identical, and the three
cropped PNGs are byte-identical to the same crop taken from the base ZIP.

**What moved was the edge, and the cause is general enough to be worth more than these three
sprites.** A sprite's outermost transparent pixel is not waste — it is what the bilinear sampler
reads at the drawing's edge. The destination pixel that straddles that edge blends the ink texel
with the transparent one beside it. Crop the transparent one away and there is nothing on that side,
so the sampler **clamps to the edge** and reads the ink twice. The edge comes out heavier, without
anything having moved.

`normalize`'s rounding made that inevitable for any sprite whose ink begins on a grid line: it
rounded *outward to the grid and stopped*, which for `sailboat_sail` (ink at x = 27, a multiple of 3)
and for `dolphin_body`'s top margin (6) landed the drawing exactly on the new canvas edge.

**The rule, and where it lives.** A side that is trimmed keeps at least one transparent pixel; a side
whose ink already reaches the canvas edge is left alone, because no margin exists there to preserve
and the crop is not what removed it. It costs one grid cell of canvas per trimmed side:

| sprite | grid-tight (v4.31 first pass) | with the guard cell | origin |
|---|---|---|---|
| `dolphin_body` | 342×168 | **342×171** | +(1, 1) units |
| `sailboat_hull` | 246×51 | **246×51** — unchanged, its margin was never a whole cell | +(2, 0) units |
| `sailboat_sail` | 180×180 | **183×180** | +(8, 0) units |

The general statement is in `ARCHITECTURE.md` §3, *"The transparent margin is part of the drawing"*,
with the measurement and a row for each of the four draw paths — not only in `normalize.py`, because
two of those paths solve it independently and a third is covered by accident.

### Where the other three draw paths stand, since the maintainer asked

- **`GlTextureCache.cropToContent` → the atlas: already safe, and it always was.** That function
  crops the *reduced* bitmap to **zero** margin on every side — the same mistake, one layer down —
  but `GlTextureAtlas.add` uploads every entry inside a one-texel transparent border it allocates
  itself, and its own "Bleeding" note says exactly why: *"a bilinear sample that strays past an edge
  finds transparency rather than the neighbouring sprite."* The guard texel the crop removes is put
  back before any sampler sees it. **v4.30's crop-after-reduction is not exposed to this**;
- **`GlTextureCache.uploadStandalone`: not covered, and currently unreached.** No atlas, no border,
  and `GL_CLAMP_TO_EDGE` with `GL_LINEAR` — which is the clamp described above, exactly. Since
  v4.29's skyline packer the twelve-theme census counts **zero** standalone entries at full density,
  so nothing takes this path today. Recorded rather than fixed: adding a border there is a change to
  a path with no traffic, and the honest note is that the day a sprite spills into it, its edge is
  the clamped one;
- **`CanvasSceneTarget`: the shipped PNG is the only guard it has**, which is why the fix had to be
  on the asset side rather than in either backend.

### And then it was free

**Almost.** Re-measured on the device with the guard cell in place, against the same committed
files:

| golden | grid-tight crop | with the guard cell |
|---|---:|---:|
| `lake-busy` | 6 px, max delta 70 | **0** |
| `lake-boats` | 6 px, max delta 41 | **1 px**, delta 10 |
| `lake-dolphin-leap` | 6 px, max delta 52 | **1 px**, delta 15 |

**A factor of six on the count and of four on the worst pixel, and one of the three is exactly
zero** — which is what says the mechanism was the right one. What is left is a single contour pixel
in two frames, and it is **not** the clamp: those two sprites now carry a guard pixel on every side
they lost one on, and `lake-busy` draws the same sprites through the same code and comes out
identical. The likeliest remainder is that the bitmap's own extent still changed — 342 texels where
there were 345 — and Skia's filtered blit of a minified bitmap is not required to be invariant under
that. Not chased further: it is one pixel, the mechanism that mattered is measured and fixed, and
guessing at Skia's internals is not a measurement.

So the crop is **not** provably free, and the honest word for it is *nearly* free. On the
maintainer's instruction, `lake-boats` and `lake-dolphin-leap` are **re-authored with this
paragraph as the attribution**; `lake-busy` needed nothing. The crop stays, because reverting it
would leave two tests red and throw away the rule above, which is worth more than these three
sprites.

The 1× photographs in `immagini/v431-lago-prima-dopo-1x.png` are the maintainer's own look at the
same claim, on the whole frame rather than on a golden's 360×800.

The hull's **y** origin is untouched at 8, which is what `SAILBOAT_HULL_WATERLINE_UNITS` derives its
25 from; only x moved on that sprite.

### The procedure

`AI_PROJECT_RULES.md` 12.14 gains a line — **Asset tooling tests run** — and 12.18 gains a step 9.
`n-a` is only available to a release that touches neither `app/src/main/res/`, nor `tools/assets/`,
nor `tools/*.py`; anything else states the count. `CLAUDE.md` §4, which told the next session those
two failures "are not yours", now says 109 of 109 and that a failure there is a real one.

---

## 107 — `normalize --apply` had never once completed, and it failed after rewriting six files

**RESOLVED.** Found by running it, which is the only way it could have been found: item 106 is the
first change in the project's history that needed this command.

### Defect one: it looked for an indent the file does not use

`_rewrite_registry_geometry` located each sprite's entry with `text.index("\n    }", start)` — a
closing brace at four spaces. `sprites.json` is written with a **one**-space indent, so an entry
closes on `"\n  }"` and the four-space form appears nowhere in the document. Every `--apply` run
died on the first sprite with `ValueError: substring not found`.

The function's own docstring had already noticed the consequence without naming the cause: an
anchor branch inside it was annotated *"unexercised because no `--apply` run had ever completed"*.
That is the symptom of this bug, written down next to it, one release earlier.

Replaced with brace matching from the entry's own `{`, skipping string literals — a `notes` field is
free text and this registry's notes contain both braces and escaped quotes. The registry can now be
reformatted without breaking the applier again.

### Defect two: it wrote every file it could before the step that fails

`_crop_targets` cropped and saved each PNG, rewrote each SVG, and called the registry patcher
**last**. So the abort above left three shipped PNGs cropped, three sources cropped, and the registry
describing canvases none of them had — measured, in this session: six files rewritten, registry
untouched, and nothing inside the working tree to put them back with. The tree is not a git
repository; the base ZIP is what recovered it.

Restructured into compute-then-write: the first pass decodes, crops in memory, checks that no opaque
pixel is discarded, and renders the new SVG and registry **text**, and may raise as often as it
likes; the second pass writes. By the time anything is written, the only remaining way to fail is
the filesystem.

### Defect three: a message claiming something it had not checked

`"single call site"` was printed whenever `origin_site` was unset — an assertion of singularity from
a field that means *unknown*. It was wrong for all three sprites in item 106. Now prints
`call sites NOT resolved -- grep for every blit of this sprite`.

### And `--only`

The pending set mixes drift with decisions: three sprites that should be cropped and two whose
leading margin is a deliberate shared origin. `--apply` is all-or-nothing and could not express
"crop the three that drifted", so item 106 would have had to be done by hand and would not have been
reproducible by command. `normalize --apply --only <name>` is that, repeatable, and it refuses a
name that is not pending.

**Shown to bite:** `--only nonexistent` is rejected; the crop of item 106 was performed through it
and the two tree sprites are byte-identical afterwards.

---

## 108 — The dolphin is not on its leap point, and two comments said it was

**RESOLVED as prose. OPEN as a drawing question**, with the number attached so it is a decision and
not a discovery.

`DOLPHIN_ORIGIN_X_UNITS` is justified by a derivation:

> The sprite is 345x174 px -- 115x58 local units -- **filled edge to edge**, so its content centre
> sits at (57.5, 29).

The canvas half is true, and `SpriteMeasurementClaimTest` checks it and passes. The half the
constant actually rests on — *filled edge to edge* — stopped being true in v4.26, when the redraw
left `4,6` px of margin inside the same box. Measured:

| | content centre | where (−57.3, −29) puts it |
|---|---|---|
| v4.25, ink to the edge | (57.500, 29.000) u | (+0.20, 0.00) — the nudge was deliberate |
| v4.26 onward | (58.167, 30.000) u | **(+0.87, +1.00)** |

One unit of vertical displacement, 1.8 % of the animal's own height. The registry's note for the
same sprite carried the error independently, claiming the content was *"placed so
DOLPHIN_ORIGIN_X/Y_UNITS (-57.3, -29) still land the animal on its leap point"* — **one false
sentence in two places, written by two different passes.** Both corrected.

Item 106's crop does not change the displacement and could not: a compensated crop moves no drawn
pixel, so the content centre is (57.167, 28.0) against an origin of (−56.3, −27) and lands in the
same `(+0.87, +1.00)`. That identity is a useful check on the crop, and it holds.

**Not fixed, and the reason.** Landing the content centre on the leap point means −57.167 / −28,
which **moves the drawn dolphin**. That is an artwork change in a release that was told to make
none, and the -0.2 unit nudge the x carried before any of this is part of the same question. What
closing it takes: one photograph at 1× of the dolphin at its apex, before and after, and the
maintainer's word.

**The guard that would have caught it.** `SpriteMeasurementClaimTest` matched `` `name` … is NxM px ``
and nothing else, so it read the canvas and never the ink. It now also reads any comment block
claiming a sprite is "filled edge to edge" / has "content filling it" / "fills its canvas", and
checks that block's named sprite against the **alpha channel** rather than the registry — the
registry is a declaration and the PNG is the artwork.

Two properties of it worth stating, because both were found by writing it:

- **it is scoped by comment block**, not by character distance: the two halves of the dolphin's
  sentence are four lines apart, and a windowed regex either misses that or drags in the next
  constant's prose;
- **a quoted claim is history, not a claim.** `AI_PROJECT_RULES.md` §3 says to annotate a wrong
  number rather than delete it, so the corrected comment has to be able to say *This said "filled
  edge to edge"* without the guard reading it as the assertion being made again. Double quotes are
  how this codebase already marks a superseded sentence, and they are what is stripped.

**Shown to bite:** run against the uncorrected comment it fails with
`PaperRenderer.kt says dolphin_body fills its canvas, and its ink box is [4, 6, 345, 174] of 345x174`.
`cloud_body`'s *"with content filling it"* is its positive control and is true.

**This is a third shape for `BACKLOG_v4_29.md` item 90**, whose open half is "a pixel claim that
omits its unit is invisible". This claim carries no number at all: it is a sentence about the ink,
attached to a true sentence about the canvas, and the true half is what a checker was reading.

---

## 109 — "The number lives in four places", written in the release that found the fifth

**RESOLVED**, and it is a one-line fix worth an entry because of when it was written.

`SceneSpace.PERSON_METRES_TALL`'s KDoc, rewritten by v4.30:

> The number lives in four places and this is the one the other three quote —
> `VehiclePedestrianScaleTest.CHILD_SPRITE_UNITS_TALL`, `PrecipitationScaleTest.childMetres` and
> `build_people_concepts.CHILD_OF_ADULT` — so moving it means moving all four.

`BACKLOG_v4_30.md` item 102, in the same delivery, is titled *"The child's height lived in five
places, and only the device could find the fifth"*. The fifth is
`VehicleScalePixelTest.CHILD_UNITS`, and it is still 54 where the others are 52 — deliberately, because
it is the ink a child occupies in the **sprite's own** units with its ground shadow, not the
80-unit metre the other four use. That is exactly why a search for the number does not find it, and
exactly why the KDoc naming "four" is the sentence a future redraw would trust.

Corrected to five, with the unit difference stated where the list is.

**The same release both discovered a fact and published a comment contradicting it**, which is the
shape items 82, 63 and 94 record. What is new here is the interval: not four releases, not nine —
the same ZIP.

---

## 110 — One fallback, two entry paths, and two documents each naming one

**RESOLVED** in prose. **The code is right and is already tested**; only the sentences were wrong,
and they were wrong in opposite directions, which is why neither was obviously so.

`switchToCanvasFallback`'s KDoc:

> Reached only when EGL could not be initialised at all.

`BACKLOG_v4_30.md` item 103, about the same surface:

> the fallback the wallpaper takes after `GlLifecyclePolicy.MAX_CONTEXT_REBUILDS` EGL failures.

`GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext, rebuildsSoFar)` is
`hadWorkingContext && rebuildsSoFar < MAX_CONTEXT_REBUILDS`, so the fallback is reached when either:

- **`hadWorkingContext == false`** — EGL never initialised, and the **first** failed frame lands
  there. That is the KDoc's case, and item 103 misses it;
- **`hadWorkingContext == true` and three rebuilds are spent** — a GPU that worked and stopped. That
  is item 103's case, and the KDoc misses it. It is also the case `MAX_CONTEXT_REBUILDS` was
  *added* for, and `GlLifecyclePolicy`'s own doc says the old latch-on-any-failure rule "is right
  for 'this device cannot do EGL' and wrong for everything else" — so the KDoc is describing the
  behaviour that constant replaced.

`GlLifecyclePolicyTest` already pins both, including `(true, 3) → false`. Nothing to fix but the
prose, and the prose is what a reader deciding how much item 103's +13.6 % matters would use.

### And item 103 itself

**Left OPEN and deliberately not acted on.** Its two load-bearing claims were checked rather than
re-timed:

- *"`ThemePreviewScene` blits the walkers' un-suffixed bases directly, one blit each. Unaffected."*
  **True** — `SUMMER_MAN`, `WINTER_MAN`, `SUMMER_WOMAN`, `WINTER_WOMAN`, `SUMMER_GIRL`,
  `WINTER_GIRL` are the un-suffixed `person_*_walk*` drawables and no layer table is reached;
- *where it lands* — corrected above, and it lands on **more** devices than the item said, not fewer.

The +13.6 % is **not re-measured** here, and the reason is that nothing in this release touches the
people draw path: `SceneObjectRenderer.drawPersonLayers` is byte-identical to v4.30's. Re-running the
A/B would produce a number about this afternoon's thermals rather than about a change.

**And it is not a defect.** What would remove it — composing a figure's layers into one bitmap per
(shape, colour set) and blitting that — is a *second drawing path*, for a degraded fallback, that
nothing has reported. That is a feature, and this was a defect round. It stays open with its
measurement intact and its blast radius now stated correctly.

---

## 111 — "Rendered the previous release" is a claim about which APK is installed

**DOCUMENTED**, and it is here because it is the one thing in this release that went out wrong
before it went out right.

### What happened

The gate of item 104 went to zero and four Canvas goldens failed: three from the crop of item 106,
and **`people-window` by a single pixel** at (26,526) — `(137, 148, 138)` rendered against
`(137, 155, 149)` committed, a delta of 11 in 288 000 pixels.

Two runs of `PeopleGoldenTest` were byte-identical to each other and both differed from the
committed file at that same pixel, so it was deterministic and not noise. The next question was
whether v4.31 caused it, and the method is the one item 104 had just used successfully: **build the
previous release from its own delivery ZIP and render the same frame.** That was done. It produced
`(137, 148, 138)` too, so the pixel was reported as **inherited from v4.30** — and reported to the
maintainer, who authorised re-authoring the golden on that basis.

**It was not inherited. The v4.30 APK was never installed.**

`adb install -r` refuses to install a lower `versionCode` over a higher one. v4.31 had already been
bumped to 62 and the v4.30 build is 61, so the install failed — and the command's output had been
sent to `/dev/null`, so the failure was invisible. Every "v4.30" frame in that comparison was
rendered by **v4.31's own APK**, which is why it agreed with v4.31 exactly.

Caught by the guard-cell round of item 106: on the rebuilt crop `people-window` **passed**. A frame
that a rebuild of the previous release supposedly reproduced cannot be fixed by changing this
release, so the attribution had to be wrong. `adb shell dumpsys package … | grep versionCode`
answered it in one line: **62**.

### The correction

Re-run properly — `adb uninstall` first, then install v4.30's two APKs, confirm `versionCode=61`
from `dumpsys`, then render:

```
PLACEHOLDER_V430
```

So the pixel was **caused by this release's first, grid-tight crop**, and the guard cell of item 106
removed it along with the other five. There was nothing inherited, and `people-window` is **not**
re-authored: it passes.

### Why it is written down rather than quietly fixed

`AI_PROJECT_RULES.md` §3, and because the failure is general and cheap to repeat:

> **Rendering the previous release is only a measurement if the previous release is what ran.**
> `adb install -r` silently refuses a downgrade, and a release round bumps `versionCode` *before*
> the attribution pass that wants the old APK. Uninstall first; then read the installed
> `versionCode` back out of `dumpsys` and put it beside the number.

`CLAUDE.md` §4 carries that as a command now. It matters more than one pixel: the same method is
what attributed item 104, and item 104's conclusion would have been just as confidently wrong if
the v4.29 install had been refused the same way. **It was not** — v4.29 is `versionCode` 60 and was
installed over 60, and its mutation run swapped a *file* rather than a release, so both halves of
that measurement stand. But nothing in the method made that luck visible, and this entry is what
replaces the luck with a check.

---

## 112 — A golden that flashes: `wave-storm` is a warmed-up thunderstorm

**OPEN**, found by accident while correcting item 111, and it is a coin flip that has been in the
suite since v4.28.

### The rule, and the scene that breaks it

`GoldenScene.warmUpFrames`'s own KDoc, on why a warmed-up scene stays deterministic:

> The one thing in the renderer that draws from an unseeded `Random` is the lightning timer, and
> `updateLightning` only touches it while a storm is active — **so a warmed-up scene must not be a
> storm**, which `SceneGolden.assertMatches` has no way to check and `SharedGoldenScenes` therefore
> does not do.

`SceneGoldenTest.waveStorm` is `warmUpFrames = 320`, `warmUpDeltaSeconds = 0.25f`, and
`isThunderstorm = true`. **Eighty seconds of simulated storm, with the lightning timer drawing from
the unseeded `Random` the whole way.** The rule is written in the class that defines the scene and
is violated two files away; `SharedGoldenScenes` obeys it and `SceneGoldenTest` was never checked
against it.

### Caught in the act, and then explained exactly

v4.30's own build (`versionCode` 61, verified installed — item 111) rendered all 28 goldens at zero
except `wave-storm`, which came back **285 858 pixels different**: the whole frame, with a bolt in
the sky that the committed file does not have.

The arithmetic matches the picture to a tenth of a level:

- a strike sets `lightningFlashAlpha = 1f`, and the **same call** decays it by
  `deltaSeconds × 3 = 0.75`, so the strike frame itself draws at **0.25** and the next frame at 0.
  **Exactly one frame per strike shows anything;**
- the veil is `LIGHTNING_VEIL_MAX_ALPHA × 0.25 = 45` of 255, white, over the whole frame. Over a
  scene whose mean channel is 131.7 that predicts a lift of `(255 − 131.7) × 45 / 255 =` **21.8**.
  Measured lift: **21.9** (131.7 → 153.6);
- the interval is `4 + U(0, 8)` seconds, mean 8 s = **32 frames** at 0.25 s.

So **P(the captured frame is a strike frame) ≈ 1/32 ≈ 3.1 %**, and the failure when it happens is
the entire frame.

### What this is not

**It is not the new gate.** A flash is 285 858 pixels against the old 576-pixel budget as well: this
golden has been failing about one run in thirty-two since v4.28, under the gate that was there
before. The zero gate neither caused it nor hid it; the census that found it was one of the runs the
zero gate made worth doing.

### Why it is open rather than fixed

Three ways out and each one is somebody else's decision:

- **suppress the flash for this golden.** The scene's own doc says it is about the water — *"The
  focus rectangle is the water and nothing else: the sky above it carries the storm veil and the
  lightning, which are v4.26's business and have their own frames"* — and the committed file has no
  bolt, so clearing `lightningFlashAlpha` before the measured frame would be deterministic **and
  byte-identical to what is committed**. It needs a test-visible hook on `PaperRenderer`, which is
  production code changed for a test;
- **take the storm out of the warm-up**, which re-cuts a scene the maintainer approved in v4.28 and
  re-authors its golden;
- **make the lightning a pure function of the clock**, like every other animated thing in this
  renderer. That is arguably what it should always have been — the renderer's whole design is that
  animation is a function of `SceneTime` — but it changes the shipped lightning pattern, and a
  defect round is not where the sky gets re-timed.

**And the guard the KDoc says cannot exist, can.** `assertMatches` receives the `GoldenScene`, which
carries `warmUpFrames`, `weather.isThunderstorm` and the customisation the storm flag lives in — so
"a warmed-up scene must not be a storm" is checkable in the harness rather than only written down.
It is not added here because it would fail immediately on `wave-storm`, and adding a guard together
with an exemption for the one thing it catches is the move this project keeps refusing. **The guard
belongs in the same change as the fix.**


### Closed in v5.0 Fase 0, and by which of the three ways out

**The first one, in its stronger form.** Not "clear `lightningFlashAlpha` before the measured
frame" but "**do not let the strike timer fire at all** while this golden is being rendered":
`PaperRenderer.lightningStrikesEnabled` gates the firing branch of `updateLightning`, the scene
declares `GoldenScene.pinLightning`, and `configure` is the only thing that ever writes it. Clearing
the alpha would have left the warm-up drawing veils into frames that are painted over — harmless,
but it would have made the *render* depend on the unseeded `Random` while arranging for the *frame*
not to. Not firing means no draw in this harness ever reads that `Random` at all, which is a
property that can be stated rather than argued about.

The other two stay refused for the reasons written above, and the third one is refused by the
maintainer explicitly: **the shipped lightning stays random and stays off the clock.**

**The guard is in the same change**, as this entry required, and with no exemption in it:
`GoldenScene.requireDeterministicLightning` rejects *any* warmed-up storm that has not pinned, runs
from `SceneGolden.assertMatches` and from `GlGolden.assertGlBackendUnchanged`, and reads the storm
the way the renderer does — through `LiveWeatherSceneRules.stormActive`, so the theme's own
thunderstorm toggle is caught as well as the live forecast's. `wave-storm` is not excused from it;
it satisfies it.

### What was measured, and what each number is

| claim | evidence |
|---|---|
| the coin is gone | `SceneGoldenTest#waveStorm` run **100 times consecutively** on the BV6600 against `versionCode` 63, read back with `dumpsys` before and after: **100 green, 0 failures**. Under the old behaviour the chance of that is 0.96^100 ≈ **4 %** |
| the guard bites on the real scene | `pinLightning = true` removed from `waveStorm` and the test APK rebuilt: it fails in **0.238 s**, before any frame is drawn, naming the scene and what to do. Restored and rebuilt afterwards |
| the guard bites on both kinds of storm | `LightningPinTest` — a live-forecast storm and a theme-toggle storm are both rejected; a **cold** storm (`thunderstorm`, which both the Canvas and the GL suites pin) and a warmed-up scene in plain rain are both accepted |
| the switch is a switch | `LightningPinTest` renders 80 frames of storm each way: unpinned **2 flashes**, peak lift **21.17** of 255; pinned **0 flashes**, peak **0.18** (the scene's own motion). Two pinned renders of the same scene differ by **0 pixels** |
| the shipped lightning did not change | `LightningCadenceTest` at `-e lightningFrames 2000` — 500 s of storm in the production configuration — run on a build of **v4.31's own production code** (`versionCode` 62, verified installed) and on this one (63): strikes **61 vs 60**, mean interval **32.53 vs 33.36** frames against a predicted 32, observed range **[18, 48] vs [17, 48]** against the `4 + U(0, 8)` s bound of [16, 48], mean lift **22.76 vs 22.74** of 255. The difference between the columns is the roll, which is the point |
| nothing was re-authored | **0 goldens regenerated.** `wave-storm` matches its committed PNG — the one captured before any of this existed — at 0 differing pixels, which is also the proof that pinning removes the veil and nothing else. The three GL references were not touched |

**The frame this golden pins is the frame it always pinned**, so `MAX_DIFFERING_FRACTION = 0.0` is
what demonstrates the change is inert: a single moved pixel anywhere in the 28 Canvas goldens would
have failed the suite.
