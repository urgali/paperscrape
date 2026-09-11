# BACKLOG_v4_25.md — what v4.25 decided, and what it left open

**Replaces `BACKLOG_v4_24.md` for new items only.** Everything `BACKLOG_v4_23.md` and
`BACKLOG_v4_24.md` leave open is still open and is not restated here; both files stay in the
repository root alongside this one until their open items are closed. Numbering continues from
them: `BACKLOG_v4_24.md` reached item 55, so this file starts at 56.

Every item carries an outcome: **RESOLVED** (corrected in code, with a test), **REJECTED**
(decided against, with the reason), **DOCUMENTED** (nothing to fix; recorded so it is not
rediscovered), or **OPEN** (left undone on purpose, with what closing it would take).

The measuring device is a **Blackview BV6600** (MediaTek Helio A25, PowerVR GE8320, Android 10,
720×1440). Every number below was measured on it in this pass unless it says otherwise.

---

## Summary

| item | what | outcome |
|---|---|---|
| 56 | The three GL reference frames portray people who no longer exist | **OPEN** — deliberately not re-authored; see the reason and the consequence |
| 57 | Twenty window-occupant sprites ship and no draw path can reach them | **DOCUMENTED** — the winter half is intended; the mechanism that hides them is not |
| 58 | `SpriteReachabilityTest`'s "table nobody reads" rule is defeated by a doc comment | **OPEN** — one line, and it is what kept item 57 invisible |
| 59 | "The busts are too narrow" — and the premise under it was false | **RESOLVED** — the artwork was narrow, not the criterion; the thresholds are back at 50% |
| 60 | The second `PaperScrapeGlThread` is the GPU driver's, not ours | **RESOLVED** — measured, attributed, and the documents corrected |
| 61 | Seven length systems all called `_UNITS`, and nothing stops two of them being compared | **OPEN** — the same mistake was made twice in one release; the search is done, the fix is proposed and deliberately not taken |
| 62 | Every occupant in every car was riding backwards | **RESOLVED** — one constant mirror in the renderer, with the measurement that found it |
| 63 | A redraw left six stale sizes in the comments, and the guard that exists for that missed all six | **OPEN** — the sizes are corrected; the guard only matches one shape of sentence |
| 64 | Is the double golden regeneration a per-release check or a per-device one? | **OPEN** — a question for the maintainer, with both arguments and a proposal; the protocol is unchanged in this release |
| 65 | The golden suite would have gone green over a scene in which every person was redrawn | **OPEN** — measured (1 assertion of 26 was red); no tolerance moved, and closing it means deriving gates, not lowering numbers |

---

## 56 — The three GL reference frames portray people who no longer exist

**OPEN, and it is a consequence rather than a defect.** `gl-day.png`, `gl-lake-busy.png` and
`gl-thunderstorm.png` were captured on the OnePlus 6T's Adreno driver, and this release redrew
every person in the scene. **The three committed frames therefore show the previous drawing of
the people, and the current build does not draw those people any more.** Anyone who opens those
files to see what the scene looks like will be looking at v4.24's family.

**They were not re-authored, on purpose.** `GlDriverGapGuardTest` measures the *gap between two
drivers*, and its reference is Adreno. Re-capturing here would move that reference onto PowerVR
— a bigger decision than this release, taken silently, and it would delete the one measurement
the guard exists to make. `CLAUDE.md` §7 says the same thing from the other end: never move a GL
reference to make a different driver pass.

**Why they pass anyway, and this is the part that surprises people.** The three frames measure
**0.05% / 0.05% / 0.24%** of contour-displacement against a 3% gate. The people occupy a
negligible fraction of those frames — a pedestrian is a few hundred pixels of 288 000 — so the
metric is *insensitive to them by construction*. That is correct for what the gate measures, and
it is exactly why the pictures can be stale while the numbers are healthy.

What closing it would take: a decision to re-baseline the GL references on the BV6600's PowerVR
driver, which means re-capturing all three, re-characterising the driver gap that
`GlDriverGapGuardTest` is calibrated on, and accepting that the Adreno measurement is gone. It is
a release of its own.

---

## 57 — Twenty window-occupant sprites ship and no draw path can reach them

**DOCUMENTED. The winter half is deliberate; the total is larger than anyone had counted.**

`SceneObjectRenderer` chooses a window occupant with
`personWindowHeadSkinDrawables[kind][season][skin]`, and the season index comes from
`seasonIndexFor(Exposure.INDOORS)`, which is:

```kotlin
if (exposure == Exposure.OUTDOORS && customization.winterColorsEnabled) 1 else 0
```

**Indoors it is 0 whatever the theme does**, and the comment beside it says why: *the hat belongs
to the street, not to the room behind the pane.* Nobody stands at their own window in a coat. So
the twelve winter recolours are shipped and never selected, and **that is the intended
behaviour** — it has now been reported as a bug twice by two different readers, which is why it
is written down here rather than in a comment nobody finds.

The count is not twelve, though. Measured on the shipped set:

| what | files | bytes |
|---|---:|---:|
| the base window heads, `person_*_head_window.png` | 8 | 38 039 |
| the winter recolours, `person_*_winter_head_window_skin*.png` | 12 | 62 585 |
| **never blitted by any path** | **20** | **100 624 (98.3 KiB)** |

The eight bases are dead for a second, unrelated reason: the draw path reads only the *recoloured*
table, and the base table `personWindowHeadDrawables` is read by nothing — see item 58. They are
still the artwork the recolours are generated from and the subject of `SpriteVariantTest` and
`SpriteMeasurementClaimTest`, so they are not simply spare.

**Nothing costs a frame.** These files are never decoded, so they contribute nothing to the
decoded-sprite ceiling `SpriteGeometryTest` rations; the cost is 98.3 KiB of a 21.88 MiB APK,
which is **0.44%**.

**Recommendation: keep them.** Deleting twenty PNGs would save under half a percent of the APK
and would cost the family's symmetry — the generator produces every family in both seasons and
all three tones, and a set with holes in it is a set someone re-fills by hand. The reason they
are unreachable is a *rule about clothing*, not an oversight, and a rule can change: if a future
theme ever paints a cold room, the artwork is already there. What was missing was the record, and
this item is it. Reverting the recommendation is `git rm` of twenty files plus the second column
of two tables.

---

## 58 — `SpriteReachabilityTest`'s "table nobody reads" rule is defeated by a doc comment

**OPEN, one line, and it is the mechanism that kept item 57 invisible.**

That test's second assertion exists because of a defect with exactly this shape: a table listing
sprites, referenced nowhere but its own declaration, kept alive so `UnusedResources` would stay
quiet. The rule is *a `val` whose initialiser mentions `R.drawable.` and whose own name occurs at
most once across the main sources is a table nothing reads.*

`personWindowHeadDrawables` occurs **twice**:

```
SceneObjectRenderer.kt:1665:    private val personWindowHeadDrawables = arrayOf(
SceneObjectRenderer.kt:1771:     * Separate from [personWindowHeadDrawables], the base artwork the recolours derive from.
```

The second one is inside a KDoc block. **A mention in a comment satisfies the rule**, so the table
passes as read, and the eight sprites it names pass the first assertion as *named by the code*.
Both halves of the class are green over a table no draw path touches.

What closing it would take: strip block comments and line comments before counting, or count only
occurrences that are followed by `[`, `(` or `.`. Either is a few lines in the test's plumbing.
It was left for a pass that can re-run the whole JVM suite against the change and look at what
else it starts reporting — a stricter rule will very likely find more than this one table, and
triaging those is the work, not the regex.

---

## 59 — "The busts are too narrow" — and the premise under it was false

**RESOLVED. The artwork was narrow, the criterion was right, and the thresholds are back where
they were.** This item was opened as *the heads no longer fill half the pane, and the criterion
moved rather than the artwork*, with the floors re-derived to 40% at the band and 35% averaged.
Both halves of that reading were wrong and the reason is worth keeping, because the wrong reading
survived a whole release.

**What the criterion was doing was right.** rc5's rule — *the occupants fill at least half the
glass* — came from a real complaint about an empty-looking cabin: one occupant filled 26% of the
pane and read as nobody driving. It was failing because the artwork had stopped filling the pane,
which is exactly what it exists to say. `VehicleOccupantScaleTest.BAND_FILL_FLOOR` and
`MEAN_FILL_FLOOR` are **0.50 and 0.50** again, unchanged from every release before this one.

**The head was narrow because a constraint was read in the wrong unit**, not because a
three-quarter drawing needs to be narrower than a frontal one. The generator's
`SEATED_HALF_BAND` was written in the bust's units against a seat pitch in the car's; one bust
unit is 0.5255 car units, so the band was twice as tight as the car is, and `head_rx` was drawn
down to **9.0** where the same person's head at a window is 18.0. Item 61 is that defect as a
class. With the band derived through the conversion, the head is drawn at the proportion the
family has everywhere else — **1.01 / 1.03 / 1.00 / 1.16** of width over height in a car, against
0.61–0.72 while the item was open — and the fill criterion passes at 50% without being touched.

**And the sentence this item used to justify the move was not a measurement.** It said the v4.24
heads were "as wide as the seat pitch, so the two occupants touched and the pane read as a wall of
faces". They never touched. Measured over **every seatable pair and every row of the sprites**,
placing each bust at its seat and about its own anchor the way `drawSeatedOccupant` does:

| | seat pitch | closest the two occupants' ink comes |
|---|---:|---:|
| v4.24 | 23.0 car units | **1.27 car units** |
| v4.25 | 21.5 car units | **2.06 car units** |

To recompute: read each `person_*_head_car` PNG's per-row alpha extents, convert to car units by
`CAR_OCCUPANT_SCALE` about `HEAD_CAR_ANCHOR_X_UNITS`, mirror on x as `drawSeatedOccupant` does,
put one at `CAR_HEAD_X_UNITS` and one at `CAR_PASSENGER_X_UNITS`, and take the minimum of
(passenger's left edge − driver's right edge) over all pairs and all rows.

There was no wall of faces, and **the shorter pitch leaves more glass between two heads than
v4.24 did**, which is why it could be shortened to give the pillars their light back without
squeezing anything. Two numbers quoted while this item was open do not reproduce and are
corrected here rather than left standing: the gap was never 4.4 units, and **18.6 car units is
the widest bust v4.25 ships, not v4.24's** — v4.24's widest is 21.6.

What replaces the lesson: **a criterion that fails after a redraw is evidence about the redraw
until somebody measures otherwise**, and the measurement is cheap. What this cost was one release
shipping a family drawn to fit a number that was wrong.

---

## 60 — The second `PaperScrapeGlThread` is the GPU driver's, not ours

**RESOLVED.** Two threads named `PaperScrapeGlTh` had been observed in the wallpaper process in
every measured condition, and the cause was recorded as unknown with a leaked settings preview as
the standing hypothesis. It is neither a leak nor ours.

Measured on the BV6600, on the debug build, with the wallpaper live:

- the process contains exactly **one Java thread** named `PaperScrapeGlThread`
  (`Thread.getAllStackTraces()`, logged from the render loop) while `/proc/<pid>/task` shows
  **two** with that name;
- `simpleperf` over the second one, 780 samples: **75% kernel, 13% `libc`, 11% `libsrv_um.so`,
  3% `gralloc.mt6765.so`, `libIMGegl.so`** — PowerVR's user-mode driver, and **not one frame of
  `libart` or of this app's code**. Our own render thread, profiled in the same minute: 38%
  JIT-compiled Kotlin, 15% `libart`, 6% `libGLESv2_mtk`;
- in a process where the settings screen opened first, the same driver thread appears under the
  name **`RenderThread`** instead. Linux gives a new thread its creator's `comm`, and the driver
  never renames its worker, so it wears the name of whichever thread first touched EGL.

Cost, 20-second windows on the home screen: our render thread **52.40%** of one core, the driver's
**4.45%**. With the wallpaper hidden the driver thread **does not exist** and ours reads 0.55%.

`CLAUDE.md` §5 and the two archived CPU reports quoted `PaperScrapeGlTh 42.71%` as though the
render thread were one thread. The process figure beside it is the sound one and is unchanged;
the per-thread line has been corrected where it appears.

---

## 61 — Seven length systems all called `_UNITS`, and nothing stops two of them being compared

**OPEN. The search is done and the places are listed; the fix is proposed and deliberately not
taken in this release.**

**The same mistake was made twice, in two files, and both times it was found by accident.** A
length measured on the bust's canvas was compared against a length in the car's units, and one
bust unit is 0.5255 car units, so both comparisons were wrong by nearly a factor of two:

1. `build_people_concepts.py`'s `SEATED_HALF_BAND` — written as 11.0 bust units and justified
   against a 23-unit seat pitch in car units. The band was twice as tight as the car is, the
   seated head was drawn down to fit it (`head_rx` 9.0 against 18.0 for the same person at a
   window), and **a whole release shipped with squashed heads**. Found because the maintainer
   said the busts looked wrong and asked where the constraint came from.
2. `VehiclePedestrianScaleTest`'s `WIDEST_SEATABLE_HEAD_UNITS` — measured on the bust canvas and
   compared directly against the seat pitch. The margin read as **one unit** and is **eleven**.
   Found because shortening the pitch made the assertion fail, not because the assertion was
   right.

Both are corrected at the cause, with the conversion written into the comparison. What is open is
the class.

### Why the shape of the code invites it

Every sprite family's local unit is **one third of a pixel of that family's own PNG**
(`SpriteBlitter.SPRITE_PIXELS_PER_UNIT = 3`), and each family is then scaled onto the screen by
its own object's base scale. So seven different physical lengths all end in `_UNITS`, all come
from the same division, and none of them carries which frame it belongs to except by convention
in its name:

| frame | one unit is | declared by |
|---|---|---|
| scene metres | a metre of the depicted world | `SceneSpace.*_METRES_TALL` |
| screen pixels | a pixel of the frame | every `*_PX` |
| walking-person units | 1/3 px of `person_*_walk*`; 80 units is a person | `SceneSpace.PERSON_SPRITE_UNITS_TALL` |
| car units | 1/3 px of `car_body`; 56 units is 1.6912 m | `SceneSpace.CAR_SPRITE_UNITS_TALL`, `CAR_UNIT_METRES` |
| fire-truck units | 1/3 px of `firetruck_body`; 68 units is 2.9 m | `SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL` |
| seated-bust units | 1/3 px of `person_*_head_car`, a 38x42 canvas | the `HEAD_CAR_*` constants |
| window-bust units | 1/3 px of `person_*_head_window`, a 49x57 canvas | the `WINDOW_HEAD_*` constants |
| building units | 1/3 px of that building's own sprite | `HOUSE_PANE_UNITS`, `RESTAURANT_*`, … |

And the whole set of conversions between them is five expressions:

- `SceneObjectRenderer.CAR_OCCUPANT_SCALE` — seated bust → car (0.5255 today);
- `SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE` — seated bust → fire truck;
- `build_people_concepts.CAR_UNITS_PER_BUST_UNIT` — the generator's own copy of the first, because
  the generator cannot import Kotlin;
- `drawWindowOccupant`'s `winW * 0.85 / WINDOW_OCCUPANT_DIVISOR_UNITS` — window bust → building pane;
- `SceneSpace.scaleForHeight(metres, units)` — any sprite frame → metres → pixels.

**A comparison that does not contain one of those five and mentions two frames is a defect.** That
sentence is the whole rule, and nothing in the project enforces it.

### The search, and what it found

Method, all of it re-runnable:

```bash
# every constant that names a length, grouped by the frame its prefix declares
grep -rno '[A-Za-z_]*_UNITS\b' --include='*.kt' app/src | sed 's/.*://' | sort -u
# every use of the two bust frames outside their own declarations
grep -rn 'HEAD_CAR_[A-Z_]*_UNITS\|WINDOW_HEAD_[A-Z_]*_UNITS' --include='*.kt' app/src
# every use of each conversion
grep -rn 'CAR_OCCUPANT_SCALE\|FIRE_TRUCK_OCCUPANT_SCALE\|CAR_UNITS_PER_BUST_UNIT' --include='*.kt' --include='*.py' app tools
```

Read one by one: **44 Kotlin use sites of the two bust frames**, every use of the five
conversions, and the generator's whole band derivation. Outcome: **the two defects above, and no
third**. Three more sites read as mixed frames and are correct, each recorded so the next reader
does not "fix" one of them:

- **`VehicleOccupantScaleTest.LAMP_FRONT_W_UNITS` / `LAMP_REAR_W_UNITS` / `LAMP_H_UNITS`** are used
  for the cars *and* for the fire truck, whose units are different lengths. They are right: the
  constants are the shared lamp sprites' own size (`car_lamp_front` is 18x12 px = 6x4 units,
  `car_lamp_rear` 12x12 = 4x4), both vehicles blit those same PNGs through
  `SpriteScale.SCENE_UNITS`, and the test compares them inside each vehicle's own local frame.
- **`SceneObjectRenderer.OCCUPANT_BOX_UNITS = 22`** reads like a bust dimension and is a
  *building* one — a house window's own width, which is why the restaurant passes it instead of
  its own 10.7-unit pane.
- **`OneOccupantRuleTest`'s `WINDOW_HEAD_HEAD_UNITS * houseScale / HOUSE_PANE_UNITS`** mixes the
  window-bust frame with the building frame and is right, because `houseScale` *is* the fourth
  conversion above, written out.

**The honest limit of that search**: it reads constants. The second defect compared a constant
against a **local variable** (`val pitch = CAR_PASSENGER_X_UNITS - CAR_HEAD_X_UNITS`), so a scan
keyed on constant names would not have flagged it, and did not until the shape was already known.

### Three ways to make it impossible, none of them taken here

- **A. Put the frame in the name, not the subject.** The generator already does it —
  `SEAT_PITCH_CAR_UNITS`, `SEATED_CLEARANCE_CAR_UNITS`, `CAR_UNITS_PER_BUST_UNIT` — and both
  defects were in code that does not: `HEAD_CAR_HEAD_UNITS` names the *sprite*, not the frame,
  and `WIDEST_SEATABLE_HEAD_UNITS` names neither. Renaming to `<QUANTITY>_<FRAME>_UNITS` makes a
  mixed expression read wrong to a human. Cost: about forty constants and their call sites.
  Catches nothing automatically, which is its weakness and also why it is cheap.
- **B. A Kotlin `value class` per frame** — `CarUnits`, `BustUnits`, `WalkUnits` — with the five
  conversions as the only functions that cross between them. This makes the mistake
  *unrepresentable* rather than merely visible. The real cost is not the refactor: these types
  reach the draw path, where `AI_PROJECT_RULES.md` 5.1 forbids per-frame allocation. An inline
  `value class` over `Float` does not allocate while it stays unboxed, and **does** the moment it
  enters a generic container, a nullable, or an `Any`-typed argument — so this needs the same
  bytecode audit 5.11 describes for boxing, and that audit is the work.
- **C. A test that scans for mixed frames**, in the shape `SpriteReachabilityTest` already
  established: derive each constant's frame from its declaration, and fail any expression that
  combines two frames without one of the five conversions in it. Cheapest to run and the only one
  that keeps working after the next redraw. Its limit is the one above: it sees constants, not
  local variables, so it must be paired with A to be worth much.

**Recommendation: A and C together, in a release of their own.** B if the allocation audit is
wanted anyway. What must not happen is the fourth occurrence being found the way the first three
were.

---

## 62 — Every occupant in every car was riding backwards

**RESOLVED, in one line of the renderer, and the interesting part is why it survived.**

The seated artwork B "Rilievo" is drawn three-quarter with the hair mass toward −x, which puts the
face toward **+x** — and +x in a vehicle's local frame is its **rear**, because the vehicle art
faces left and the driver sits at a negative `CAR_HEAD_X_UNITS`. So every occupant of every car
faced the boot.

**Not a missing mirror.** `drawCar` already draws the whole vehicle — body, glass, livery, lamps
and both busts — inside its own `scale(dir, 1)`, so occupants do turn with the direction of travel
and the driver is always at the leading seat. Photographed with `reverse` both ways: the seats
swap, the belt changes shoulder, the mirror moves, the heads turn with them. What was wrong was
the constant sense of the artwork, in both directions equally, which is why no amount of looking at
one car could show it. `drawSeatedOccupant` now blits with `scale(-scale, scale)`; the reflection
is about the **anchor**, not the canvas centre, so the eye axis stays where the seat put it.

**Measured on the rendered frame with the lamps as the reference** — amber forward, red aft, which
is exactly the job those sprites exist to do. Confirmed again on the regenerated `traffic-day`
golden: the taxi's red lamp is at its left, and both occupants look right.

**Nothing else in the scene has the same exposure**, checked: the window busts are three-quarter
but have no facing to be wrong (nobody at a window owes the street a direction); the pedestrians
carry `person.direction` and their mirrored frames, and the code applies it; the fire engine's
single occupant goes through the same `drawSeatedOccupant` and was straightened by the same line.

**Why it was invisible for a whole phase.** The family this replaced was drawn frontal and
symmetric: a head with no side has no wrong side. The concept gave it one, and no approval image
ever showed two directions at once. That is now a rule rather than a lesson — see the acceptance
sheet in `DESIGN_NOTES.md`.

**What guards it now, stated honestly, because it is less than it looks.** Mutation (12.11):
putting the mirror back to `scale(scale, scale)` and re-running the 45 relevant instrumented tests
fails **one** — `everyOccupantClearsItsPillarsByFifteenPercentOfItsHead`, the saloon at 14.0%. All
26 Canvas golden assertions pass, all three GL ones pass, `VehicleDrawOrderTest` passes,
`theOccupantsFillHalfTheGlass` passes. **The suite catches the reversal as a side effect** —
mirroring moves the drawing's asymmetric ink to the other side of the seat and the saloon has no
margin to spare — and **nothing asserts which way a person is looking**. On a cabin with symmetric
clearance the reversal would be invisible to every test and to every golden, including the ones
regenerated after the fix. A test that reads the facing off the artwork (the hair mass's side of
the eye line) against the vehicle's own +x would close that, and is not written here.

---

## 63 — A redraw left six stale sizes in the comments, and the guard that exists for exactly that missed all six

**OPEN. The six are corrected; the guard is not touched.**

`SpriteMeasurementClaimTest` was written in REN-07 because load-bearing comments were describing
artwork that no longer ships. It has three assertions, and the general one — *a size attributed to
a named sprite is that sprite's size* — matches this regex over `SceneObjectRenderer.kt` and
`PaperRenderer.kt`:

```
`([a-z0-9_]+)(?:\.png)?`[^`\n]{0,40}?is (\d{2,4})x(\d{2,4}) px
```

The v4.25 redraw moved all three person canvases — walk 41x85 → **39x84** units, window bust
53x57 → **49x57**, seated bust 47x44 → **38x42** — and left six statements behind that were true
of the old artwork:

| where | said | is |
|---|---|---|
| `SceneObjectRenderer` walk anchor doc | "All ninety-six walk sprites are 123x255 px -- 41x85 local units" | 117x252 px, 39x84 units |
| `WINDOW_OCCUPANT_DIVISOR_UNITS` doc | "the canvas is 53" | 49 |
| `WINDOW_HEAD_ANCHOR_X_UNITS` doc | "their own 53x57-unit canvas", "trimmed from 53 to 47" | 49x57, trimmed 53 → 49 |
| `HEAD_CAR_HEAD_UNITS` doc | "the shared 47x44 canvas", "the frontal bust artwork" | 38x42, and the artwork is three-quarter |
| `drawWindowOccupant` body | "The canvas is 159x171 px, which is 53x57 units" | 147x171 px, 49x57 units |
| `OccupantHeadFitTest` | "The walk canvas is 123x255" | 117x252 |

**Not one of them matches the regex**, because none of them names a sprite in backticks
immediately before the size — they say "the canvas", "the walk sprites", "the shared canvas". The
guard is green, has been green throughout, and is measuring four hand-listed sprites plus whatever
prose happens to be written in its one shape.

Two more findings of the same family, both corrected in this release:

- **`drawWindowOccupant` cited `WindowOccupantScaleTest` as the thing that pins its two halves.
  That class does not exist and never has** — it is not in this tree and it is not in the v4.24
  archive either. What actually pins them is `OneOccupantRuleTest`, which reads the `0.85` and the
  divisor back out of the function's own source. A named test in a comment is a claim nothing
  checks.
- **A KDoc block describing `drawCar` had been stranded above `drawSeatedOccupant`** when that
  function was inserted between the comment and its subject, so `drawCar` had no doc and
  `drawSeatedOccupant` had two.

What closing this would take: extend the claim regex to the forms actually written (a bare
`NxM px` near a sprite family's name, and `NxM local units` / `NxM-unit canvas` resolved against
the family's PNG), and widen the file list beyond the two main sources. Expect it to report more
than these six on the first run — the triage is the work, exactly as item 58 says of its own
regex. **Both items are the same shape: a rule keyed on one written form, defeated by another.**

---

## 64 — Is the double regeneration a per-release check or a per-device one?

**OPEN, and it is a question for the maintainer rather than a defect.** The golden protocol was
followed unchanged in v4.25 and nothing here proposes changing it mid-release.

Every regeneration pass regenerates the 25 Canvas goldens **twice, in two separate instrumentation
runs**, and compares the two sets byte for byte. The point is to show the noise floor is exactly
zero, so that a later difference is the scene changing and never the phone. It costs roughly
**forty minutes of device time** each time, and it has now been run to a clean result on four
separate executions on this device, across a reboot.

**The argument for doing it once per device or toolchain**: what the check tests is
*the renderer plus this phone plus this Android build*. None of those changes between releases.
Four clean runs is a strong prior, and forty minutes is the most expensive single step in the
pass.

**The argument against, and it is not weak**: a release can introduce non-determinism itself — an
unseeded `Random`, a `HashMap` iteration reaching a draw order, a time source read at draw time,
a cache whose eviction depends on what ran before. In that case the check is looking at *the
release*, not at the phone, and moving it to "device changed" would remove the one thing that
would catch it. `AI_PROJECT_RULES.md` 5.2 and 5.3 exist because this project has shipped exactly
that class of bug before.

**A proposal, not a decision: regenerate twice only the scenes whose frames changed.** The
attribution pass already names them, and a scene whose bytes did not move has demonstrated its own
determinism by matching the committed golden. In v4.25 all 25 changed, so it would have saved
nothing; in a release that moves one sprite it saves nearly all of it, and it keeps a
release-introduced non-determinism inside the check, because a newly non-deterministic scene is a
changed scene by definition. The variant worth weighing beside it is the cheaper
*same-run* check — render each scene twice inside one instrumentation run — which catches an
unseeded generator but not anything that depends on process state.

**Not decided here.**

---

## 65 — The golden suite would have gone green over a scene in which every person was redrawn

**OPEN. Measured, not fixed: no tolerance was moved in this release and none should be moved
without the derivation that justifies it.**

Before the goldens were regenerated, the committed frames were compared against freshly rendered
ones — the same comparison the suite makes, with the same rule — to find out which assertions were
actually red. **One of twenty-six.**

| | |
|---|---|
| whole-frame gate | `MAX_DIFFERING_FRACTION` 0.200% of 288 000 px = **576 px** |
| the largest whole-frame change in the release | `traffic-day`, **476 px** = 0.1653% — **under the gate** |
| assertions that failed | **`people-single`'s pavement density gate**, 0.1478% against 0.1415%, and nothing else |
| the two that came closest after it | `people-skyscraper`'s tower windows, **1.41%** and **1.92%** against a 2% limit — 11 and 15 pixels of a 780-pixel rectangle |

So a release that **redrew every person in the scene, mirrored every car occupant and moved the
seat pitch** would have left twenty-five of twenty-six golden assertions green. The frames were
regenerated because a committed golden has to portray what the renderer draws — a stale reference
is the complaint item 56 makes about the GL frames — not because the suite forced it.

**This is not obviously a defect, and that is why it is written down rather than acted on.** The
whole-frame gate exists to catch structural regressions and a person is a few hundred pixels of
288 000, so it is insensitive to people *by construction*, exactly as the GL contour metric is
(item 56). What watches people is the focus rectangles, and they do work: the two tower rectangles
are 780 px each and came within a fifth of firing.

What is worth examining, when somebody has the derivation to back it:

- **The default focus limit is a fraction, so it is loose where the rectangle is large.**
  `MAX_FOCUS_DIFFERING_FRACTION` at 2% is 15 px on a tower window and **785 px on the pavement
  band**, which is more than any change this release made to any frame. A focus rectangle drawn
  around a whole band is a whole-frame gate wearing a focus rectangle's name.
- **The gate that did fire is the one that was derived** — `PEOPLE_DENSITY_GATE`, half of the
  0.283% that hiding the people actually moves. The v4.22 derivation method is what makes a gate
  mean something, and only four rectangles have been through it.

**Do not close this by lowering a number.** The v4.22 method is to derive a gate between the floor
the shipped scene produces and the signal the regression produces, and doing that for the
people-carrying rectangles is a pass of its own — it needs the "people switched off" arm measured
per scene, which is what `PeopleGoldenTest.theDensityGateStandsBetweenFloorAndSignal` already does
for one of them.
