# BACKLOG_v4_28.md — what v4.28 decided, and what it left open

**Replaces `BACKLOG_v4_26.md` and `BACKLOG_v4_27.md` for new items only**, and **carries forward by
name** everything `BACKLOG_v4_23.md`, `BACKLOG_v4_24.md` and `BACKLOG_v4_25.md` still leave open —
those three moved to [`docs/archive/`](docs/archive/) in this release, under the convention the
v4.24 documentation pass established, and the table below is what makes moving them safe.
`BACKLOG_v4_26.md` and `BACKLOG_v4_27.md` stay in the repository root with their own open items.
Numbering continues: `BACKLOG_v4_27.md` reached item 78, so this file starts at 79.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided
against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered), or
**OPEN** (left undone on purpose, with what closing it would take).

The measuring device is a **Blackview BV6600** (MediaTek Helio A25, PowerVR GE8320, Android 10,
720×1440). As in v4.27, the colour work was done **on the host**: the contrast between a wave and
the water it lies on is arithmetic over each theme's own numbers, and sweeping every theme, every
five minutes of the clock and three weathers costs seconds with the phone switched off. The device
confirmed the worst case, took the captures and carried the memory measurement.

---

## Carried forward from the three archived backlogs

Restated here by number and one line each, so that nothing is lost by the move. The reasoning stays
in the archived file; read it there.

| item | from | what | still |
|---|---|---|---|
| 18 | `BACKLOG_v4_23.md` | An unreadable custom theme loses the whole store | **OPEN**, carried from v4.20 unchanged |
| 25 | `BACKLOG_v4_23.md` | The palm is the odd tree out — the most visible piece of the old drawing language left | **OPEN**; which family is redrawn next is the maintainer's call |
| 30 | `BACKLOG_v4_23.md` | Hand-maintained counts still in the current-state documents | **OPEN**; the rule is unchanged and this release obeyed it |
| 40 | `BACKLOG_v4_23.md` | The Canvas goldens barely exercise the sky — **the sun's half** | **OPEN**; the moon's half was closed in v4.23 |
| 49 | `BACKLOG_v4_24.md` | `tools/assets/reports/runtime-inventory.md` is stale | **CLOSED here** — regenerated against the 305-sprite set this release ships, by the command the item itself gives |
| 50 | `BACKLOG_v4_24.md` | Nineteen Kotlin compiler warnings, and nothing reads them | **OPEN** |
| 51 | `BACKLOG_v4_24.md` | Eighteen `UnusedResources` lint warnings | **OPEN** |
| 52 | `BACKLOG_v4_24.md` | `CONTRIBUTING.md` tells contributors to use an emulator | **OPEN** |
| 53 | `BACKLOG_v4_24.md` | `DESIGN_NOTES.md`'s "last fully verified against v74" line | **OPEN** |
| 54 | `BACKLOG_v4_24.md` | Two sprite counts have no definition to count against | **OPEN** |
| 55 | `BACKLOG_v4_24.md` | Nothing in the tree learns that a release was published | **OPEN**, and it bit again — see item 79 |
| 56 | `BACKLOG_v4_25.md` | The three GL reference frames portray people who no longer exist | **OPEN** |
| 58 | `BACKLOG_v4_25.md` | `SpriteReachabilityTest`'s "table nobody reads" rule is defeated by a doc comment | **OPEN** |
| 63 | `BACKLOG_v4_25.md` | A redraw left six stale sizes in the comments, and the guard missed all six | **OPEN**, and it bit again — see item 82 |

---

## Summary

| item | what | outcome |
|---|---|---|
| 79 | `ROADMAP.md` declared v4.27 unpublished. It has been published since 2026-09-10 | **RESOLVED** — corrected against the public API, by the recipe the document itself carries |
| 80 | `SpriteCache` decodes every sprite at the resolution it was drawn at, not at the one it is blitted at | **OPEN** — the largest single saving available on the one budget this project rations |
| 81 | The GPU atlas's dimension gate rejects nothing, and 23% of the process is atlas that is allocated and empty | **OPEN** — two measured numbers and an explicit instruction not to act on either in this release |
| 82 | Three load-bearing numbers in comments are stale, and two of them now argue the opposite of the truth | **OPEN** — the same class as item 63, found the same way |
| 83 | The shipped wave stands over more of the lake band than its own contrast derivation assumed | **OPEN** — measured and pinned by a test; closing it needs photographs, not arithmetic |
| 84 | Boats, dolphins and waves are sorted against three different reference points | **RESOLVED in part** — the wave was brought onto the boat's, which is the pair that shows; the dolphin's ~16 px remains, with the shape of the real fix |
| 85 | The decoded-sprite ceiling was raised to 32 MiB, and the condition attached to it | **RESOLVED** — the A/B memory measurement was taken and the ceiling stands. See the pass report |

---

## 79 — `ROADMAP.md` declared v4.27 unpublished, and v4.27 has been published since 2026-09-10

**RESOLVED.** The document's "Current status" opened with:

> **v4.27 prepared — not published and not approved.**
>
> `versionCode = 58`, `versionName = "4.27"`. **No tag, no push, no GitHub Release** — that half is
> the maintainer's and has not been done for this version.

**That is false, and the document itself says how to find out.** Three lines further down it carries
the recipe, with the reason: *"Re-read the API rather than this line — nothing in a working tree
learns that a release went out."* Run as written:

```bash
curl -s https://api.github.com/repos/urgali/paperscrape/releases
```

`v4.27` comes back **published at 2026-09-10T16:54:58Z, `draft: false`, `prerelease: false`**, with
`PaperScrape-v4.27.apk` and its `.sha256` attached — as do `v4.16` through `v4.26`. The status block
has been rewritten to say so, and the baseline moved from v4.26 to v4.27.

**This is the third time.** `BACKLOG_v4_24.md` item 55 named the mechanism — a working tree has no
way to learn that a release went out, so the status documents rot in exactly one direction, always
toward "not published" — and `BACKLOG_v4_25.md` recorded it happening again. Item 55 stays open
because nothing here fixes the mechanism; what this release adds is a third data point and the
observation that **the recipe being present in the document is not enough**. Whoever writes the
status block has to run it, and the only proposal that would actually close item 55 is a check that
fails when the tree's own `versionName` is older than the newest published tag and the status block
still calls it unpublished. That is a test with a network call in it, which is why it has not been
written; a pre-delivery step in `AI_PROJECT_RULES.md` 12.18 is the cheaper alternative and is the
maintainer's call.

---

## 80 — `SpriteCache` decodes at the artwork's resolution, not at the one the artwork is drawn at

**OPEN.** This is the largest single saving available on the decoded-sprite ceiling, and it is worth
more than every asset decision that ceiling has ever been raised for.

**What happens now**, read off the code rather than inferred:

```kotlin
// SpriteCache.kt
private val decodeOptions = BitmapFactory.Options().apply { inScaled = false }
```

`inScaled = false`, no `inSampleSize`, no `inDensity` — every sprite is decoded at exactly the pixel
dimensions of its PNG. That is deliberate and correct as far as it goes: `nodpi` plus `inScaled =
false` is what makes a sprite's size a property of the artwork rather than of the device.

**What it costs.** The artwork is authored at `SpriteBlitter.SPRITE_PIXELS_PER_UNIT = 3` pixels per
local unit and then divided back down at the blit:

```kotlin
canvas.scale(1f / SPRITE_PIXELS_PER_UNIT, 1f / SPRITE_PIXELS_PER_UNIT)
```

So an adult pedestrian is a **117 × 252** bitmap in memory, and it reaches the screen **37 px tall**
on the reference device. The decoded bitmap is roughly **46 times the area actually drawn**, and the
person family is 202 of the 305 sprites in the set.

**What halving would buy.** Decoding at `inSampleSize = 2` quarters the decoded area of every sprite
it is applied to. On the v4.28 set of **33 286 896 B** that is of the order of **8 MB** even if only
the person and vehicle families take it, which is more than the last three ceiling raises put
together (v4.1 +10 MB, rc4 +2.5 MB, v4.20 +0.9 MB, v4.28 +4.7 MB) and would make the ceiling stop
being an argument for several releases. `SpriteGeometryTest`'s budget KDoc now points here.

**Why it is not a tick-box.** Three things have to move together:

1. **The grid rule.** `SpriteGeometryTest.every shipped sprite is authored on the sprite grid`
   requires every sprite to be a whole multiple of 3 px on **both** axes, because that is what lets
   the blitter divide back to an integral number of local units. Halving a 117 × 252 sprite gives
   58.5 × 126 — not an integer, let alone a multiple of 3. Either the authoring oversample changes
   from 3 to a number that survives halving (6 is the obvious candidate and doubles the PNGs on
   disk), or the reduction is per-sprite and the grid rule becomes a rule about the *decoded*
   bitmap rather than the file.
2. **`SPRITE_PIXELS_PER_UNIT` is a single global constant** and the divisor at the blit. A sprite
   decoded at half size needs half the divisor, which means the blitter has to learn a per-sprite
   scale — the exact "two sprite scale conventions" hazard `CLAUDE.md` §6 warns about, with a third
   convention added.
3. **It is a visual change.** Downsampling at decode is a resample; the paper edges this project's
   whole drawing language is built on are 1–2 px features. This needs the photographs, at the sizes
   things actually reach, before anything is decided.

The cheaper alternative recorded in `SpriteGeometryTest`'s v4.1 paragraph — recolouring one flat
colour at load time instead of shipping tone variants — is still on the table and is smaller: it
buys back the skin axis (roughly a third of the person family) and nothing else. This item is the
bigger one.

---

## 81 — The atlas's dimension gate rejects nothing, and 11 892 KiB of the process is atlas that is allocated and empty

**OPEN**, and **deliberately not acted on in this release** — the second half on the maintainer's
explicit instruction.

### The gate

`GlTextureAtlas.accepts()` refuses any sprite over `DEFAULT_MAX_ENTRY_DIMENSION = 1024` px, and the
comment justifies the number with a specific sprite:

> Large sprites are excluded on purpose. The sleigh alone is 1563x434, and letting it consume a
> third of a shelf row would evict nothing but would push the many small sprites that actually
> repeat per frame out into standalone textures — the opposite of what the atlas is for.

**Measured on the shipped set** (v4.27's ZIP, before this release's own sprites):

- `santa_sleigh_scene` and `santa_sleigh_trot` are **594 × 123**, not 1563 × 434 — the crop recorded
  in `docs/archive/SANTA_CROP_REPORT.md`;
- the largest dimension anywhere in the set is **798** (`cloud_body`), and
- **0 sprites of 266 exceed 1024 on either axis.** On v4.28's 305, still 0: the tallest thing added
  is 360 px wide.

So the gate is a branch that has not rejected a sprite in several releases, justified by a sprite
that has not had those dimensions in several releases. It is not harmful — it costs a comparison
per candidate — but it is a rule nobody is subject to, and the reader who meets it learns a false
fact about the set.

**Closing it means deciding what the gate is for**, which is not obvious: it may be a live guard
against a *future* large sprite rather than dead code. If it is kept, the comment has to be rewritten
against a real number (see item 82); if it is dropped, the constant and the branch go together.

### The 11 892 KiB

The v4.28 memory investigation measured, on the shipped build:

- **GL `mtrack` is 55% of the process**, so the wallpaper's memory essentially *is* its graphics;
- **the atlas alone is 31.7% of the process**;
- the atlas allocates `DEFAULT_SIZE = 2048` squared at RGBA = **16 384 KiB**, and the reference scene
  fills **4 492 KiB** of it — **27% full**;
- so **11 892 KiB is allocated and empty**, which is **23% of the whole process**.

That is the largest single block of unused memory in the app, and it is larger than everything the
decoded-sprite ceiling has ever been argued over.

**It is not to be resized in v4.28, and this is on the maintainer's instruction rather than on
judgement.** The reason is sound: 2048 is the smallest maximum texture size OpenGL ES 2.0 guarantees,
so shrinking it trades a guarantee for bytes, and the right replacement number can only be chosen
against **the worst theme**, which is not the reference scene, and only **after** this release's 39
new sprites are in the set and can be part of the measurement. Doing it here would be choosing a
number against the wrong scene on the wrong day.

What closing it takes: the atlas's occupancy measured on the fullest theme the app can draw, with
v4.28's sprites present, and a size chosen against that with the ES 2.0 guarantee stated as a
constraint rather than assumed away.

---

## 82 — Three load-bearing numbers in comments are stale, and two of them now argue the opposite of the truth

**OPEN.** Same class as `BACKLOG_v4_25.md` item 63 — a redraw moved the artwork and left the prose
behind — and found the same way, by checking a comment against the thing it describes. What makes
these three worth an item rather than a typo fix is that they are **load-bearing**: each one is the
justification for a constant, so a reader who trusts it reaches a wrong conclusion about whether the
constant is right.

1. **`GlTextureAtlas.accepts` — "The sleigh alone is 1563x434".** It is 594 × 123. See item 81.

2. **`GlTextureAtlas.DEFAULT_SIZE` — "~16.4 MB the whole sprite set would occupy as individual
   textures".** The set was **27.29 MiB** decoded before this release and is **31.745 MiB** now. The
   sentence the number supports is:

   > At RGBA that is 16 MB of texture memory, against the ~16.4 MB the whole sprite set would occupy
   > as individual textures — so this is a rearrangement of that budget rather than an addition to
   > it.

   **That conclusion is now false.** A 16 MiB atlas against a 31.7 MiB set is not a rearrangement of
   the budget; it is roughly half of it, and the other half is still standalone textures. The
   comment is arguing for the atlas's size using arithmetic that stopped holding somewhere around
   v4.1, and it is exactly the paragraph someone would read before deciding item 81.

3. **`GlTextureCache` — "the whole shipped set was measured at 18.5 MiB of texture at level 0, and
   1.8 MiB once each sprite carries only the levels its scenes actually draw".** Level 0 is now
   31.745 MiB. The *ratio* the sentence exists to make — that per-level upload is far fewer texels,
   not more — is still the point and is probably still true; the figure it makes it with is not, and
   the 1.8 MiB half has not been re-measured at all.

None of the three is fixed here, for one reason: **fixing them means re-measuring, not re-typing.**
(2) and (3) are only worth correcting together with item 81, which is the decision they inform, and
correcting a number without re-taking the measurement behind it is how the stale numbers got there.
What closing it takes: re-measure the three, and while doing so extend item 63's guard, which
matches one shape of sentence and would not have caught any of these.

---

## 83 — The shipped wave stands over more of the lake band than its own contrast derivation assumed

**OPEN**, measured, and pinned by a test so it cannot drift unnoticed.

`WaveContrastTest` derives the wave's two luma gates the way v4.22 derives every gate: halfway
between a measured **floor** and a measured **signal**. The floor is "how much the mirror's own
vertical gradient already varies over the wave's own height", and the derivation that produced the
shipped `WAVE_BODY_LUMA_GAP = 24.9` / `WAVE_FOAM_LUMA_GAP = 34.9` measured it with the **phase-2**
shape on the **Beach** band, where a wave covers **0.15** of the water: floor **9.80**, at Beach
07:24 under theme rain.

The shape that actually ships is taller, and two of the three themes that draw a lake draw a much
shallower one. Measured over the same sweep, with WA3 "Tubo" at its largest lane scale:

| theme | `lake.height` | band on the reference screen | wave | fraction of the band | worst gradient | local floor |
|---|---|---|---|---|---|---|
| beach | 0.90 | 345.6 px | 70.8 px | 0.205 | 65.35 | **13.40** |
| tundra | 0.25 | 96.0 px | 70.8 px | 0.738 | 35.30 | **26.05** |
| spring | 0.33 | 126.7 px | 70.8 px | 0.559 | 59.75 | **33.40** |

On Spring the wave is **more than half the height of the lake**, and the water under it varies by
**33.40** of luma — above the 24.9 the body is asked to stand off by. Run the same halfway rule on
that floor and the body gate would be 36.70, the foam gate 46.70.

**Why it was not simply changed.** Moving the day gate from 24.9 to 36.70 changes the tint of every
daytime wave on every theme, and the daytime frames were approved by the maintainer from the phase-3
photographs at 24.9. There is no photograph of the raised one. This is arithmetic saying the
photographs may have been taken on the forgiving theme; it is not arithmetic that can replace them.

**And there is a second, probably larger question underneath it**, found while measuring this one and
recorded here rather than acted on. The tint is derived **once per frame** against the mirror's
colour at the **top** of the band:

```kotlin
val waveSurface = ColorUtils.blendARGB(lakePaint.color, skyHorizonColorNow, LAKE_MIRROR_SKY_SHARE)
```

but the band is a ramp from that colour at the far edge to the theme's lake colour at the near edge,
and a wave sits somewhere down it — `gatherWaves` places slots between 0.42 and 0.92 of `laneMax`.
So a near wave's paper is derived against water it is not lying on, by up to the full gradient
(65 luma on Beach). This is the same mistake `skyAbove(y)` exists to prevent for the waterline —
v4.26's own comment says *"**Not the horizon colour**, and the difference is the whole reason this
function exists"* — applied to the lake's gradient instead of the sky's.

**What closing it takes**, and the two parts are one decision: derive the tint per wave at the wave's
own lane rather than once per frame at the band's top, re-derive the floor with the shipped shape on
each theme's own band, and **photograph the result on the shallow-band themes** before adopting it.
`WaveContrastTest.the shipped shape stands over more of the band than the derivation assumed` holds
the numbers above and fails if the situation worsens.

---

## 84 — Boats, dolphins and waves are sorted against three different reference points

**RESOLVED in part**, and the part that is not resolved is stated rather than hidden.

Before this release every wave was drawn before every boat and every dolphin, so a breaker crossing
the near edge of the water was cut off behind a hull that was plainly further away. That is the
sail-and-dolphin defect of v3.1 in a new pair, and `LakeLanes` already held its answer: one pass,
one key, sorted by base. v4.28 puts the waves into the same slots and sorts all three together.

**The difficulty, which is the actual content of this item:** the three categories do not share a
reference point.

| kind | its depth key is | its visible waterline is |
|---|---|---|
| sailboat | its placement point | **25 boat units below** the key — `drawSailboat` hangs the hull 8 units down and 17 tall |
| dolphin | its lane | about **8 px below** the lane (origin −29 units at the dolphin's own scale) |
| wave | its base | **the base itself** |

Keyed by its bare base, a wave was being compared against a boat's *placement point* rather than
against the boat's hull, and the first burst of phase-3 frames showed a wave cutting the sail of a
boat whose hull was obviously nearer. The wave is therefore keyed by its base **lifted by
`SAILBOAT_HULL_WATERLINE_UNITS`**, so wave and hull meet waterline to waterline; the three properties
`LakeLanesTest` fixes all survive, because boats are untouched, the lift is never negative so nothing
is pulled forward, and one key still orders everything.

**A surviving mutation, recorded because a surviving mutation is a finding.** The `wave-storm`
golden portrays a wave and a nearer hull overlapping correctly, and the obvious question is whether
it would catch the defect coming back. It does not: keying the wave by its bare base again —
reintroducing the phase-3 defect exactly — leaves `wave-storm` **passing**, because the frame
happens not to contain a pair whose order the key changes. One frame samples one configuration;
the property is about all of them.

The answer was not to hunt for a luckier frame but to write the check where the property lives.
`LakeLanesTest` now carries it as arithmetic: a wave whose base sits *below the boat's key but above
the boat's hull* — the band the two conventions disagree over, and the only band where the lift
changes anything — must be painted **behind** the boat, and the same pair keyed by its bare base
must come out wrong. Both mutations fail it, and the hull offset is pinned to the artwork it comes
from (`sailboat_hull` blitted at +8 units, 17 units tall, MEASURED from the PNG). The golden stays,
because a frame shows what arithmetic cannot.

**What remains.** Wave against dolphin is still off by the difference between the boat's convention
and the dolphin's — about **16 px** on the reference device. It was not seen to produce a wrong frame
in the phase-3 burst, and the sail is the large thing a wave can be seen to cut, which is why the
boat's convention is the one that was adopted. But it is a coincidence of magnitudes, not a rule.

**The real fix, which is one function:** a `visibleWaterlineOf(kind)` that every category goes
through — boat +25 of its own units, dolphin +29 of its own scale, wave 0 — with `orderByDepth`
sorting on that instead of on a key each category computes for itself. It is item 61's lesson in a
different currency: three numbers in three frames, compared as though they were in one. It is not
done here because changing the **dolphin's** key changes the shipped dolphin-and-boat ordering, which
five committed goldens portray, and that is a re-authoring pass with a device look attached rather
than a line in a wave release.

---

## 85 — The decoded-sprite ceiling was raised to 32 MiB, and the condition attached to it

**RESOLVED.** Recorded here as well as in `SpriteGeometryTest`'s KDoc because the *condition* is the
part that is easy to lose.

The set could not hold v4.28's artwork: 28 619 568 B shipped, 1 789 136 B of margin under the 29 MiB
ceiling, and 4 667 328 B wanted (the carrying pose 4 245 696, the wave 380 160, the canopy 41 472 —
the bird is free, B1 "Rondine" replaces `bird_body` on the same 51 × 21 canvas). No useful reduced
coverage fits: one season only is over by 755 344 B, one tone only by 47 728 B, and the only thing
that does fit under the old ceiling is one season *and* one tone together, which is a summer-only
umbrella on a single skin tone in a street of walkers drawn in three.

The maintainer authorised 32 MiB **conditionally**, in the same words v4.20's raise carried: the A/B
memory measurement of the release-like build running as the live wallpaper, against v4.27 rebuilt
from its own ZIP at the same theme and the same elapsed time, must not move beyond the noise, and
**the umbrella is refused outright if it does**. That measurement was taken; the figures and the
protocol are in the v4.28 pass report. The set lands at **33 286 896 B = 31.745 MiB**, leaving
**267 536 B** under the new ceiling — the same "just above the measured figure" every raise in that
KDoc has used, so the next pass has to come and argue too.

**Item 80 is why this should be the last raise argued on coverage alone.**
