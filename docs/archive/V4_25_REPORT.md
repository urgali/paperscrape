# V4_25_REPORT.md — the people redrawn, the small figures steadied, and what a redraw brings with it

One report for the release (`AI_PROJECT_RULES.md` 14.9). Every claim carries its label:
**OBSERVED** (seen with my own tools in this pass), **MEASURED** (a number produced by a
procedure stated beside it), **INFERRED** (a conclusion drawn from those, not itself seen), or
**DECLARED** (stated by the maintainer or by an earlier pass and not re-derivable here).

Device for everything on hardware: **Blackview BV6600**, MediaTek Helio A25, PowerVR GE8320,
Android 10, 720×1440 at density 320, over `adb`. Anything that says "the device" means that one.

## What this delivery replaces, and why

**It replaces `PaperScrape_v4_25.zip`, SHA-256
`430e5d93eff52a3b514ccf24aa9a624ea2c626e0ee9b7e8b9418cbf3270a90f7`, delivered on 2026-09-08 and
superseded the same day. That archive must not be published.** It carries the same version number
and three defects in the seated occupants that were found after it was built:

- **the heads are squashed** — 0.61 to 0.72 of width over height, where every other drawing of
  those people sits between 0.96 and 1.52 — because a clearance band was written in the bust's
  units and justified against a seat pitch in the car's;
- **every occupant in every car faces the vehicle's rear**, in both directions of travel;
- **the seat pitch is 23**, which the corrected head width does not fit: the saloon's pillar
  criterion fails on two of six cabins.

It also carries `VehicleOccupantScaleTest`'s fill floors lowered to 40% and 35%, which was the
wrong answer to the first defect and is reverted here. Nothing else about it is wrong, and the
sections below that are unchanged from its report are unchanged because they were right.

---

## 1. Results

### 1.1 The second `PaperScrapeGlThread` is the GPU driver's, and it is legitimate

This was the open question the maintainer cared about most: two kernel threads carrying the render
thread's name in every measured condition, at 46.41% and 3.82% of a core with the detail levels
off and 40.66% and 5.19% with them on, cause unestablished, with a leaked wallpaper-picker preview
as the standing hypothesis. If it were a leak it would be current drawn continuously on the
maintainer's phone.

**It is not a leak, and the second thread is not ours.**

- **OBSERVED.** In the process, `Thread.getAllStackTraces()` — logged from inside the render loop
  of an instrumented build — reports exactly **one** Java thread named `PaperScrapeGlThread`, at
  the same moment `/proc/<pid>/task` lists **two** whose `comm` is `PaperScrapeGlTh`. A Java
  `Thread` always appears in that map, so the second one was never created by this app.
- **MEASURED.** `simpleperf record --app com.paperscrape.livewallpaper.debug -t <tid>`, 780
  samples on the second thread: **72.6% `[kernel.kallsyms]`, 13.4% `libc.so`, 10.7%
  `/vendor/lib64/libsrv_um.so`, 2.9% `gralloc.mt6765.so`, 0.2% `libIMGegl.so`** — PowerVR's
  user-mode driver — with `PVRSRVBridgeCall` and `__ioctl` among the named symbols and **no
  `libart.so` frame at all**. Our own render thread, profiled in the same minute, 11 022 samples:
  **38.3% JIT-compiled Kotlin, 19.3% kernel, 15.0% `libart.so`, 5.6% `libGLESv2_mtk.so`**.
- **OBSERVED.** In a second process — one where the settings activity opened before the wallpaper
  engine — the same driver thread appears with the name **`RenderThread`**, and that process shows
  only one thread called `PaperScrapeGlTh`. **INFERRED:** Linux gives a new thread its creator's
  `comm`, and the driver never renames its worker, so it wears the name of whichever thread in the
  process first initialised EGL.
- **MEASURED**, 20-second windows on the home screen, debug build, ticks from
  `/proc/<pid>/task/<tid>/stat`:

  | | wallpaper visible | wallpaper hidden |
  |---|---:|---:|
  | our render thread | **52.40%** of one core | **0.55%** |
  | the driver's worker | **4.45%** | **the thread does not exist** |
  | MediaTek `ged-swd` | 0.40% | 0.00% |

  It is created when we start drawing and torn down when we stop: its cost is part of the cost of
  the frames we submit, not something running beside them.

**What follows.** There is nothing to close, and one thing to correct: `CLAUDE.md` §5 carried
`PaperScrapeGlTh 42.71%` beside the process figure as though the render thread were one thread.
The process figure is the sound one and is unchanged; the per-thread line has been replaced with
the attribution and with the instruction to quote the process. `ARCHITECTURE.md` §3 now says the
same thing where anybody counting threads will be standing. The archived OnePlus reports carry
per-thread lines with the same ambiguity; they are historical and were left as history.

**Not verified, and it stays open on the maintainer's side:** whether the wallpaper picker leaves
anything behind on *their* phone in ordinary use. What was observed is that a picker task left
open by an earlier session was still alive in the window manager days later, with its preview
engine and its own render thread — and that engine's thread exited correctly when the surface went
away. The engine lifecycle behaved: `onDestroy` reached `glThread.shutdown()` on every preview
this pass created, and every preview thread exited.

### 1.2 A vehicle detector that was standing on a cliff

**DECLARED by the artwork pass, and it is a defect that predates this release.**
`CarNightCrossfadeTest` reported a car vanishing in mid-road at dusk. Nothing vanishes: across
frames 124–131 the far-lane car is in the middle of the road in every frame, and the detector
loses it in three of them. As dusk darkens the body, its dense column run falls from 26 columns to
9, under a minimum of 12. `VEHICLE_COLUMN_DEPTH_SHARE` was re-derived by the method the file
declares — weakest column of the car to accept **0.167** (tenth percentile), lane strip to reject
**0.062** (median), constant halfway — from **0.20 to 0.12**. There is nothing in between: at 0.16
and below the run is 47–54 columns in every frame; at 0.20 it is 9–26. **The constant had been on
that cliff since the last time it was re-derived, for the same reason, when the saloon was
redrawn.** Nobody had seen it because the old artwork never darkened far enough to fall off.

### 1.3 A test that normalised a bust by a pedestrian's fringe

**DECLARED by the artwork pass; the consequences were re-measured here.**
`VehicleOccupantScaleTest` divided *both* sides of its occupant-versus-pedestrian ratio by the
walking family's visible skin — the seated side included. It held only while the two poses happened
to leave similar amounts of face uncovered, which the old artwork did and B "Rilievo" does not.

That defect is the visible half of a larger one, and §2.1 is what this pass did about it: **the
quantity itself was never a dimension.**

### 1.4 Every occupant in every car was facing the rear

**OBSERVED, and it predates nothing — this release created it, in phase 1, and three review rounds
did not see it.** The seated artwork is drawn three-quarter with the hair mass toward −x, which
puts the face toward **+x**; +x in a vehicle's local frame is its **rear**, because the vehicle art
faces left and the driver sits at a negative `CAR_HEAD_X_UNITS`. Every driver and every passenger
rode looking out of the back window.

**It is not a missing mirror, and that distinction changed the fix.** **OBSERVED:** `drawCar`
draws the whole vehicle — body, glass, livery, lamps and both busts — inside its own
`scale(dir, 1)`. Photographed with `reverse` true and false on the same saloon with the same
occupants: the seats swap, the belt changes shoulder, the mirror moves to the other side, and the
heads turn with them. Occupants already turn with the direction of travel and the driver is always
at the leading seat. What was wrong was the artwork's constant sense, identically in both
directions — which is exactly why no image of one car could show it.

**MEASURED on the rendered frame, using the lamps as the reference** — amber forward, red aft,
which is the job those two sprites exist to do. Confirmed again here on the regenerated
`traffic-day` golden: the taxi's red lamp is at its left, and after the fix both occupants look
right. `anteprime/occupanti_prima_dopo.png` is that frame before and after at 9×.

**INFERRED, and it is the general lesson:** the family this replaced was drawn **frontal and
symmetric**. A head with no side has no wrong side. The defect arrived *with* an improvement to the
artwork, and nothing in the approval material could have caught it — which is what §2.10 is about.

**Checked, nothing else in the scene has the same exposure**: the window busts are three-quarter
but owe the street no direction; the pedestrians carry `person.direction` and their mirrored
frames and the code applies it; the fire engine's single occupant goes through the same
`drawSeatedOccupant` and was straightened by the same line.

### 1.5 The same unit error, twice, in two files, both times found by accident

**MEASURED.** A length measured on the bust's canvas was compared against a length in the car's
units. One bust unit is **0.5255** car units, so both comparisons were wrong by nearly a factor of
two:

| where | what it compared | the margin it read | the margin it is |
|---|---|---:|---:|
| `build_people_concepts.py`, `SEATED_HALF_BAND` | an 11-unit half-band against a 23-unit seat pitch | tight by construction | the band is 11.6 car units, not 22 |
| `VehiclePedestrianScaleTest`, `WIDEST_SEATABLE_HEAD_UNITS` | a head band measured on the bust canvas against the same pitch | **1 unit** | **11 units** |

The first one **shipped a squashed family for a whole round**: the head was drawn down to satisfy
the band, `head_rx` reaching 9.0 where the same person's head at a window is 18.0. The second was
found only because shortening the seat pitch made an assertion fail — not because the assertion was
right.

**Two occurrences of one mistake are not two coincidences**, so the search was made systematic:
every `_UNITS` constant grouped by the frame it belongs to, every use site of the two bust frames
read one by one (44 in Kotlin), every use of the five conversions, and the generator's whole band
derivation. **Result: those two, and no third.** Three more sites read as mixed frames and are
correct; each is written down in `BACKLOG_v4_25.md` item 61 so the next reader does not "fix" one.

**The honest limit of that search, stated because it matters more than the result:** it reads
constants, and the second defect compared a constant against a **local variable**. A scan keyed on
constant names would not have flagged it and did not, until the shape was already known. Item 61
carries three ways to make the mistake impossible — the frame in the name, a `value class` per
frame, and a scanning test — with the cost of each. **None of them is implemented here**; the
release is the search and the record.

### 1.6 A redraw left six stale sizes, under a guard written to catch exactly that

**MEASURED.** The three person canvases all moved — walk 41×85 → **39×84** units, window bust
53×57 → **49×57**, seated bust 47×44 → **38×42** — and six statements in the comments still
described the old artwork, including the seated family being called "frontal" when it is
three-quarter. `SpriteMeasurementClaimTest` exists for this and **caught none of them**: its
general assertion matches one written shape, `` `sprite_name` … is NxM px ``, and not one of the
six was written that way. All six are corrected; the guard is not touched.

Two more of the same family, also corrected: **`drawWindowOccupant` named
`WindowOccupantScaleTest` as the test that pins its two halves, and that class does not exist** —
not in this tree and not in the v4.24 archive, checked. The test that actually does it is
`OneOccupantRuleTest`. And a KDoc block describing `drawCar` had been stranded above
`drawSeatedOccupant` when that function was inserted between the comment and its subject, so one
function had two doc comments and the other had none.

**INFERRED:** this is item 58's shape a second time — a rule keyed on one written form, defeated by
another. `BACKLOG_v4_25.md` item 63.

### 1.7 The golden suite would have gone green over a scene in which every person was redrawn

**MEASURED, and it was worth measuring rather than assuming.** Before regenerating anything, the
committed frames were compared against freshly rendered ones by the suite's own rule, and each
assertion's own limit was applied, to find out **which assertions were actually red**.

**One of twenty-six.** `people-single`'s pavement density gate, at 0.1478% against 0.1415%. Every
other Canvas assertion would have passed:

| | |
|---|---|
| whole-frame gate | 0.200% of 288 000 px = **576 px** |
| the largest whole-frame change in the release | `traffic-day`, **476 px**, 0.1653% — under it |
| closest focus rectangles after the one that fired | `people-skyscraper`'s two tower windows, **1.41%** and **1.92%** against 2% — 11 and 15 px of 780 |

So a release that redrew every person, mirrored every car occupant and moved the seat pitch was
one derived gate away from leaving the whole golden suite green. **INFERRED:** the whole-frame gate
is insensitive to people *by construction* — a figure is a few hundred pixels of 288 000 — which is
the same property item 56 explains about the GL contour metric, and it is correct for what that
gate is for. What watches people is the focus rectangles; the one that fired is the one that had
been **derived** by the v4.22 method, and the default 2% limit is loose wherever the rectangle is
large (785 px on the pavement band, 15 px on a tower window).

**Nothing was changed for this.** No tolerance was moved in this release, and closing it means
deriving gates rather than lowering numbers. `BACKLOG_v4_25.md` item 65.

---

## 2. What was done

### 2.1 `VehicleOccupantScaleTest`: three red assertions, and nothing is loosened in what ships

The artwork pass left three instrumented assertions failing on purpose. All three are resolved and
**no limit in this test is looser than it was in v4.24** — but that took two attempts on the third
one, which was briefly answered by lowering its floors before the artwork was corrected instead
(§2.2). The first thing worth reporting is that **they were not three instances of one problem**,
which is how the handover described them.

**Two were measuring the visible skin as though it were a size.** MEASURED on the shipped PNGs:
the skin's share of the head block is **0.861** for a walking man, **0.794** for the same man
seated, **0.623** for a walking woman and **0.733** for her seated. A third of the quantity is
hair. No scale can make two poses agree on it, and a test that asks them to is asking the wrong
question.

They measure the **head block** now — the crown of the hair down to the jaw, which is what
`PERSON_HEAD_SPRITE_UNITS` and `HEAD_CAR_HEAD_UNITS` declare and what every occupant scale is
derived from. It is the same for both adults, so the prediction no longer has to ask which of them
was dealt this car. The rule for reading it off a painted frame is `OccupantHeadFitTest`'s rule for
reading it off a PNG: content top for the crown, and for the jaw *the width* — the last row of the
skin's first run still at least half as wide as the widest.

Getting there cost three attempts and each one is worth a line, because each failed for a reason
that is now written into the test:

1. **Matching hair colours** measured one driver correctly and the next thirteen pixels short. The
   palette it matched against still held the previous family's hair. A palette written into a test
   is a copy of the artwork that goes stale silently — see §2.4, where the same copy was found
   weakening a different gate.
2. **Ink as "anything that is not the background"** overshot the driver's crown by two rows (the
   pane's own anti-aliased rim) and overshot a pedestrian's by a hundred: at full density the two
   nearest adults are standing in front of somebody else, and no rule reading painted pixels can
   say where one silhouette ends and the next begins.
3. **A capped walk** fixed both: ink is now a colour that has moved more than 40 levels from the
   background it is measured against — the pane's glass for a driver, the same frame with the
   people switched off for a pedestrian — and a figure whose crown is still inked at 1.5 faces
   above its own face is reported as unmeasurable rather than measured wrongly.

MEASURED, all twelve vehicle-and-lane cases, drawn head against the size table's prediction:

| | far lane | near lane |
|---|---:|---:|
| saloon / compact | +2.51 px | +1.71 px |
| saloon / saloon | +2.51 | +1.71 |
| saloon / estate | −0.49 | −1.29 |
| taxi | +2.51 | −1.29 |
| police | +2.51 | −1.29 |
| fire engine | −0.49 | +2.71 |

The sign follows the vehicle, not the lane: it is where each bust's crown and jaw fall between two
pixel rows on a head 43 to 53 px tall. **INFERRED:** a wrong constant moves all twelve the same
way, so the mean is the figure that carries the check. The mean is held to **1.5 px** — tighter in
relative terms than the 1.5 px this test used on a 31 px face — and each individual case to 4 px,
which no rounding reaches and a misplaced seat does.

**The pedestrian half of the comparison was retired rather than rebuilt.** Three measured reasons:
a walker's ground row is not in the frame (the scene's soft shadows put an ink walk **thirty
pixels below the heel**, reading a person as standing a metre nearer than they are); the two crowns
are found against different backgrounds and the anti-aliasing does not cancel; and the crowd hides
the figures worth measuring. What it was for is covered where it can be answered exactly.
**MEASURED by mutation:** setting `PERSON_HEAD_SPRITE_UNITS` to 20.0 — the 17% this release's
first build was wrong by — fails **six** assertions across `OccupantHeadFitTest` and
`VehiclePedestrianScaleTest`, including *the pedestrian head constant is what the walking artwork
measures* and *an occupant's head stays in a sane relation to a pedestrian's at the same depth*.
The same mutation **passed** the rebuilt pixel version, which is why the rebuilt pixel version is
not in the tree.

**The third assertion was not about skin at all**, and it is §2.2.

### 2.2 The criterion was right and the artwork was wrong: the fill floors are back at 50%

rc5's criterion is *the occupants fill at least half the glass*, and it exists because of a real
complaint: rc4's single occupant filled 26% of the pane and the cabin read as empty.

**The first build of this release lowered it, and that was the wrong answer.** It measured
42.9–44.5% at the head band and 37.6–41.0% averaged, all six frames short of 50% by about six
points, and moved the floors to 40% and 35% on the reading that a three-quarter drawing simply
fills less pane than a frontal one.

**It does not.** The heads were failing the criterion because they had been drawn narrow to satisfy
a band read in the wrong unit (§1.5), and the criterion was doing exactly the job it was written
for: telling the maintainer that the glass had gone empty. **`BAND_FILL_FLOOR` and
`MEAN_FILL_FLOOR` are `0.50` and `0.50` again**, unchanged from every release before this one, and
the corrected artwork passes them.

**The sentence used to justify the move was not a measurement, and does not survive one.** It said
the v4.24 heads were as wide as the seat pitch, so the two occupants touched and the pane read as
a wall of faces. MEASURED, over every seatable pair and every row of the sprites, placing each bust
at its seat and about its own anchor exactly as `drawSeatedOccupant` does:

| | seat pitch | closest the two occupants' ink comes |
|---|---:|---:|
| v4.24 | 23.0 car units | **1.27 car units** |
| v4.25 | 21.5 car units | **2.06 car units** |

They never touched, there was no wall of faces, and **the shorter pitch leaves more glass between
two heads than v4.24 did** — which is why the pitch could be shortened to give the pillars their
light back without narrowing anything. Two figures quoted while that reading stood do not
reproduce and are corrected rather than left standing: the gap was never 4.4 units, and **18.6 car
units is the widest bust this release ships, not v4.24's**, whose widest is 21.6.

**What it cost, stated plainly:** one delivered archive with a family drawn to fit a wrong number,
and a criterion loosened to admit it. `BACKLOG_v4_25.md` item 59 carries the whole correction.

### 2.3 The three occupant defects, and what was changed to close them

All three were present in the concept from phase 1 and survived approval. Each is fixed at the
cause, on the side the cause is on.

**The head.** MEASURED: with the band derived through `CAR_UNITS_PER_BUST_UNIT`, `head_rx` goes
from 9.0 to **13.8** for adults and 12.8 for children in a car, 18.0/17.0 at a window, and the
seated family's width over height goes from 0.61–0.72 to **1.01 / 1.03 / 1.00 / 1.16**. The hair
followed: every horizontal extension is now a multiple of the head's own radius instead of an
offset tuned on the narrow drawing — except the girl's bunches, deliberately, because a bunch
hangs *beside* a head rather than covering it and scaling it pushed it into the band that then
slid it onto her cheek.

**The new assertion, with its mutation.** `OccupantHeadFitTest` gains *a head is as round wherever
the same person is drawn*: every head, in all three placements, at least 0.85 of its own height.
The floor is derived — 0.96 is the narrowest the shipped artwork draws, 0.72 the widest the defect
drew. **MEASURED by mutation (12.11):** putting the shipped-narrow bust back reads **0.64** and
fails. Nothing in that class had been looking at width; every assertion in it was about height,
which was right.

**The facing.** One line: `drawSeatedOccupant` blits with `scale(-scale, scale)`. The reflection is
about the **anchor**, not the canvas centre, so the eye axis stays where the seat put it. §1.4 has
the measurement.

**The seat pitch, 23 → 21.5.** MEASURED: the saloon's visible pane is about 44 car units; two heads
at the old pitch take 23 + 17 = 40, leaving 4 to split between two pillar lights where the
criterion asks for 15% of a head each, 2.55 + 2.55 = 5.1. **Short by 1.1 units on every saloon at
every seat offset** — moving the pair only chooses which pillar the shortfall lands on, and the
plain and police saloons cross at 12–13% without either reaching 15%. `CAR_HEAD_X_UNITS` −8.5 →
**−7.75** and `CAR_PASSENGER_X_UNITS` 14.5 → **13.75**, which keeps the pair's centre and therefore
the seat back where they were. **OBSERVED: the artwork did not move by one pixel** — the pitch also
feeds the generator's band, and regenerating at 21.5 produces byte-identical PNGs, because no piece
of this family reaches the band at either value.

MEASURED, the pillar light against a 15% criterion. The first column is the round head at the old
pitch with no mirror and no offset — the state the fix started from; the second is what ships. The
three cabins with no first-column figure were not measured at that stage, because the criterion was
stopping at the first failure and never reached them:

| cabin | round head, pitch 23, no mirror | shipped: pitch 21.5, mirrored, offset applied |
|---|---:|---:|
| compact | 20.0% | **28.0%** |
| saloon | 14.0% ✗ | **18.0%** |
| estate | — | **30.0%** |
| taxi | — | **30.0%** |
| police saloon | 10.4% ✗ | **18.0%** |
| fire engine | — | **30.6%** |

**And the criterion itself had a defect**: it stopped at the first cabin that failed instead of
measuring all six, which is how the compact's 24% covered the police saloon's 10% for a whole
round. It measures every cabin before asserting now.

### 2.4 Two gates that were reading a copy of the artwork

**`SpriteTintClassTest` cannot see a tint applied at the blit.** It reads the shipped PNGs, and its
two lists of sprite names are the call sites restated by hand. So the failure it cannot see is a
call site that starts multiplying finished art by a colour: the artwork it reads is unchanged, both
its assertions hold, and the scene draws two hues compounded.

**`SpriteTintAtBlitTest`** closes it from the other side. The scene is driven through a recording
canvas across all twelve themes by day and by night, every blit is caught with the tint it was
given, and the sprite's own pixels decide which rule applies: **finished art must be blitted with
the white identity; a colourless mask must never be.** No list of names is involved — the artwork
says which of the two a sprite is and the recorder says what was done to it, so a sprite that
changes class without its call site moving fails whichever way round the change was made. The
fixture reaches **69 distinct sprites**, and asserts a floor of 60 so that a fixture which quietly
stops drawing cannot look like a pass.

**MEASURED by mutation:** darkening `SpriteBlitter.draw` by 1% (white identity → `#EEEEEE`) leaves
**all five** `SpriteTintClassTest` assertions green and fails the new one.

**The occupant colour table had two dead entries.** `VehicleOccupantScaleTest` scans for the
people's non-skin colours to check that nothing of an occupant is painted outside its own pane, and
that palette still held the previous family's: the woman's hair as `F7CE64`, which is now her
hairband and the girl's shirt, and the boy's shirt as `6BA84F`. **MEASURED** over the shipped
summer people: the colours actually painted are `8C5A38` (52 592 px, her hair) and `5FA85A` /
`3F8A4A` (61 029 px, the boy's shirt and cap). A colour that has left the artwork is not a missing
pixel — it is a pixel the scan cannot see, which makes a gate quietly weaker rather than noisily
wrong. The table was re-measured, and a new assertion fails if any colour it scans for stops
appearing in the shipped sprites.

### 2.5 A headline number re-measured, twice

**MEASURED.** The canvas trims were reported as freeing 1.67 MB of decoded budget, then re-measured
at 1.82 MB. Both are superseded: the proportion pass gave the two bust canvases a unit of width
back, and the shipped set changed with it. Decoding both sets file by file, with
`paperscrape-assets inventory` re-run against the artwork actually in the tree:

**30 254 580 B at v4.24 against 28 623 924 B here — 1 630 656 B freed, which is 1.63 MB or
1.56 MiB.** Both sets hold 266 PNGs. The headroom under `SpriteGeometryTest`'s 29 MiB ceiling
(30 408 704 B) is **1 784 780 B**. The documents carry the measured figure with its unit spelled
out, because MB and MiB have been confused on this project before.

**And the inventory itself was stale.** `tools/assets/reports/runtime-inventory.md` and its JSON
still described the v4.24 artwork sprite by sprite — listing `person_boy_summer_head_window` as
159×171 when the shipped file is 147×171 — after 266 PNGs had been replaced. Regenerated.

### 2.6 Backlog item 42, paper grain: rejected on the measurement

**MEASURED.** Over the 36 tintable sprites, the darkest is **`bar_cornice` at a mean level of
225.33**; `SpriteTintClassTest` requires ≥ 220, so the whole set has **2.36% of mean level** to
spend. A grain darkening uniformly between nothing and its full depth spends half of it, which puts
the deepest permissible grain at about **0.045** — confirming the ceiling the maintainer named, and
the sprite that fixes it.

**OBSERVED in `CHANGELOG.md`:** the overlay was implemented and removed twice, v56 scaling it down
and **v58 deleting it outright**, for CPU cost *and* colour fidelity — that build darkened by about
45% and rendered a light-blue sky grey. **INFERRED:** what the colour gate permits today is a
twentieth of the effect that was already too strong to be faithful, so there is no depth that is
both visible and inside the gate. Closed as **REJECTED**, with the note that the thing to propose
instead is baked per-object shading, which is what replaced it and what B "Rilievo" is built on.

**Not reproduced here:** any per-frame GPU cost for a grain overlay. GPU busy is not measurable on
this device — `kgsl` is Adreno-only and MediaTek's `ged` nodes are root-only — so a GPU percentage
for this feature belongs to the hardware it was taken on. It does not change the outcome; the
colour ceiling closes the item on its own, and it is the same reason v58 closed it.

### 2.7 The winter window heads: kept, and the mechanism that hid them named

**OBSERVED.** `seasonIndexFor(Exposure.INDOORS)` is `0` whatever the theme does — the comment
beside it says the hat belongs to the street, not to the room behind the pane — so the twelve
winter recolours of the window busts are shipped and never selected. **This is the intended
behaviour and it is not a defect.** It has now been read as a bug by two different people, which is
why it is written into `BACKLOG_v4_25.md` item 57 rather than into a comment nobody finds.

**MEASURED, and the count is larger than sixteen.** The draw path reads only the *recoloured*
table; the base table `personWindowHeadDrawables` is read by nothing, so its eight sprites are dead
as well:

| | files | bytes |
|---|---:|---:|
| base window heads | 8 | 38 039 |
| winter recolours | 12 | 62 585 |
| **never blitted** | **20** | **100 624 (98.3 KiB)** |

That is **0.44%** of a 21.88 MiB debug APK, and nothing at runtime: they are never decoded, so they
cost nothing against the decoded-sprite ceiling.

**Recommendation, and what was done: keep them.** Deleting twenty PNGs saves under half a percent
of the APK and costs the family's symmetry — the generator produces every family in both seasons
and all three tones, and a set with holes is a set somebody re-fills by hand. The reason they are
unreachable is a rule about clothing, and a rule can change. What was missing was the record.

**`SpriteReachabilityTest` has a real gap and it is item 58.** Its second assertion exists to catch
exactly this shape — a table that keeps sprites referenced for lint while no draw path reaches
them — and its rule is *a `val` whose name occurs at most once in the main sources*.
`personWindowHeadDrawables` occurs **twice**: its declaration, and a mention inside a KDoc block.
**A doc comment satisfies the rule**, so the table passes as read and its sprites pass as named.
The fix is a few lines in the test's plumbing; it was not taken here because a stricter rule will
report more than this one table and triaging those is the work.

### 2.8 The goldens, regenerated a second time, by the v4.23 protocol with nothing relaxed

The frames were regenerated once for the artwork and again here, after the heads, the facing and
the seat pitch moved the occupants. The second pass is what the tree ships.

**Attribution first, before anything was overwritten.** The 25 committed Canvas frames were
compared against freshly rendered ones by the golden's own rule (8 levels per channel):

- **MEASURED: 2 877 pixels changed across all 25 frames, 0 of them outside the three bands the
  people occupy** — facades 376–636, pavement 546–655, road 626–703. The extreme rows touched are
  **508 and 675**.
- Broken into connected components, the change is **16 to 51 clusters per frame, the largest 64 px**,
  and every one sits on a person: a bust behind a shop or a tower window, or the two occupants
  behind a windscreen. No bodywork, no building, no tree, no sky.
  `anteprime/golden_attribuzione_per_regione.png` paints every changed pixel magenta on three of
  the frames; `anteprime/occupanti_prima_dopo.png` is one cabin at 9×, before and after.
- **Which assertions were actually red is §1.7**, and the answer is one of twenty-six.

**The four gate rectangles measure 0.0000%**, verified as the protocol asks — **two independent
instrumentation runs**, with the device's output directory cleared and the process force-stopped
between them:

| gate | rectangle | area | differing, run A vs run B |
|---|---|---:|---:|
| `CAR_COUNT_GATE` | `traffic-day-sparse` road band | 27 720 | **0** |
| `CAR_NIGHT_GATE` | `traffic-night-quiet` road band | 27 720 | **0** |
| `BUSINESS_HOURS_GATE` | `shops-closed-night` facades band | 93 600 | **0** |
| `PEOPLE_DENSITY_GATE` | `people-single` pavement | 39 240 | **0** |

and all **25 of 25** frames are byte-identical between the two runs, not merely equal within
tolerance. (Whether that check has to be repeated every release or only when the device or the
toolchain changes is `BACKLOG_v4_25.md` item 64, with both arguments; it was **not** changed here.)

**No tolerance moved, and it is a diff rather than a claim.** Against the published v4.24 archive,
`SceneGolden.kt`, `SettingsGates.kt` and `GoldenScene.kt` are **byte-identical**, and `GlGolden.kt`
differs only by an added log line. Nothing in this release touches a limit.

**Counted as assertions rather than as files**, which this project has got wrong before:
**26 Canvas assertions over 25 PNGs, 3 GL assertions over 3 PNGs, 28 PNGs in the directory** — the
directory holds two kinds of file and listing it has produced a wrong count twice.

**The trap was taken seriously**: a committed golden is not a golden until the instrumented APK is
rebuilt, so `installDebugAndroidTest` was re-run and the APK's own `assets/golden/` was extracted
and compared against the tree, 28 of 28 identical, before the suite was started.

### 2.9 The GL reference frames

**Not re-authored, deliberately, and now an explicit backlog item rather than a note.**
`BACKLOG_v4_25.md` item 56: three committed images portray a drawing of the people that no longer
exists. They are tied to the OnePlus 6T's Adreno driver, and re-capturing them here would move the
reference onto PowerVR and delete the one measurement `GlDriverGapGuardTest` exists to make.

**Why they pass anyway**, which is the part that confuses people: the three frames measure
**0.05% / 0.05% / 0.24%** of contour displacement against a 3% gate. A pedestrian is a few hundred
pixels of 288 000, so the metric is insensitive to the figures by construction. That is correct for
what the gate measures — the gap between two drivers — and it is exactly why the pictures can be
stale while the numbers are healthy.

**MEASURED on this suite run**, so the claim is this release's and not the last one's: the driver
gap reads **day 0.05%, lake-busy 0.05%, thunderstorm 0.24%**, unchanged. The GL-against-Canvas
cross-check, which *does* see the new people because it renders both backends now, reads:

| scene | coarse (channel 64) | fine (channel 32) | limits |
|---|---:|---:|---|
| day | 0.0177% | 0.5524% | 1.000% / 2.000% |
| lake-busy | 0.0177% | 0.5736% | |
| thunderstorm | 0.0156% | 0.4632% | |

against 0.0167 / 0.0167 / 0.0149 and 0.5510 / 0.5722 / 0.4615 before the occupants moved — the
redrawn people are inside the noise of that metric, which is the same insensitivity stated from the
other side. `GLREGION` reports `day/sun glow` at **0.395%** of 64 516 px against a 0.500% limit.

### 2.10 The acceptance sheet: why three visible defects survived approval

**OBSERVED, and it is the most useful thing this release produced.** The three occupant defects of
§2.3 were all present in the phase-1 concept and all visible in it. They cost **four correction
rounds after the concept had already been approved**. Nobody saw them for one reason, and it is not
inattention: **the images the judgement was made on could not show them.** The busts sat small at
the bottom of a family sheet, and every car photographed was driving the same way.

So `DESIGN_NOTES.md` §14 now carries an **acceptance sheet**, and `AI_PROJECT_RULES.md` 13.6 makes
it a condition rather than advice: a mockup that does not carry these is not a mockup, it is a
picture, and approving it approves nothing.

1. **Every sprite at the size it is drawn on screen, inside a real frame** — not only enlarged.
   Earned twice: v4.23's sparkle, drawn well and reduced to a one-pixel cross; and this release's
   suggestion of a face, which read as a visor enlarged and as eyes at true size.
2. **Every sprite that has a facing, in both directions.** Earned by §1.4.
3. **Every sprite in the geometry that constrains it** — a bust inside its window, two occupants
   in one pane. Earned by the head width, which was wrong from phase 1 and became visible only
   when somebody built a comparison for that one question.
4. **The current artwork beside it, at the same scale.** Without the "before", a wrong proportion
   reads as a style.
5. **The same subject at the same instant on both sides of a comparison.** Earned by a series whose
   third pedestrian was a different character in the two rows, which makes every difference in the
   image unattributable.

Plus one rule of composition: **no buried sub-family.** A sheet showing twenty sprites where the
busts end up small in the last row has not shown the busts.

### 2.11 Documents

- **`DESIGN_NOTES.md`'s reference scale read 40 local units per metre**; the code has said **45**
  since v2.5, and says so in the one place the world's size is stated. Corrected.
- Checking the rest of that table against the code found **four more wrong rows**: the pumpkin at
  0.5 m against the enum's 1.0, the car at 1.45 m / 48 units against 1.6912 / 56, the tower at
  16.8 / 196 against 15.6 / 182, and a hot-air balloon with a row although `SceneObjectType` has
  had no balloon in it for releases; the fir was missing entirely. The lake's own metric read 15 px
  per metre against `LAKE_PIXELS_PER_METRE = 21`. All corrected, and the table now carries the two
  commands that recompute it (14.11).
- The head proportion in the same file read 25.00 units and 0.547 m; the constant is 24.3 and
  0.5316 m on this artwork. Corrected, and the **jaw rule** written up as a design decision, with
  the four skin-share numbers and why the visible skin must never be measured as a size.
- `ROADMAP.md` and `README.md` now say v4.25 prepared and **v4.24 published** — read from the
  Releases API on 2026-09-08, not from a document.

---

## 3. Verification

```
Release identifier:             v4.25  (versionCode 56, versionName "4.25")
Verification level:             3
Reason for the level:           release candidate on the critical rendering path
Tests run:                      testDebugUnitTest 1346 tests, 0 failures, 0 errors
                                asset tooling 108 tests, OK; probe matches_expected: true;
                                validate OK (266 entries, 140 with an SVG source, 126 gaps)
                                instrumented suite on the BV6600: 150 tests, 0 failures,
                                0 ignored, 2 450 s
Lint run:                       lintDebug, 0 errors, 27 warnings
APK build run:                  yes  (assembleDebug, 22 977 868 B)
Static / bytecode checks:       lint only; no bytecode analysis in this project
Mutation testing:               yes  (four mutations, below; one of them is a finding)
ZIP verification:               yes  (12.18, all eight steps)
Clean build from extracted ZIP: yes
Maintainer-side verification required:
                                battery and thermal behaviour; the look of the redrawn people at
                                arm's length over a day; whether the shorter seat pitch reads well
                                at arm's length, which is a spacing judgement no measurement makes
Release identifier verified unique: yes — this tree carries no Git history to hold a tag, and the
                                Releases API read on 2026-09-08 has v4.24 as its newest
```

**A note on two of those numbers, because both moved since the superseded archive's report.** The
JVM total is **1346** rather than 1345: the roundness assertion of §2.3 is the extra test. `lintDebug`
reports **27** warnings rather than 30 — 18 `UnusedResources`, 4 `UseKtx`, and one each of
`VectorRaster`, `UnusedAttribute`, `ObsoleteSdkInt`, `DataExtractionRules`. The earlier report
recorded only a count and no list, so **the three that went cannot be identified from it** and this
is stated rather than explained. There are 0 errors either way, and no lint baseline exists in this
project to hide anything.

### The ZIP's own results (12.19)

`consegna_v4_25_2/PaperScrape_v4_25.zip`, **1 312 files**. Its size and SHA-256 are reported with
the delivery rather than here: this file is inside the archive, so it cannot state the archive's
own checksum. All eight steps of 12.18, in order:

1. built, with each entry's mode written into `external_attr` by hand — `zip` is not installed on
   this machine and Python's `zipfile` does not carry the bit on its own;
2. extracted into a directory sharing nothing with the working tree;
3. **completeness file by file: 1 312 against 1 312, none missing, none extra**, compared with
   `find`-style walks on both sides rather than `git ls-files`, which compares nothing against an
   extraction that has no `.git`;
4. `.gitignore`, `.github/workflows/android-build.yml`, `CLAUDE.md`, `AI_PROJECT_RULES.md`,
   `debug.keystore`, `release-notes/v4.25.md`, `docs/archive/V4_25_REPORT.md`, `BACKLOG_v4_25.md`,
   `gradlew`, `gradlew.bat` and the wrapper jar all present, and **`gradlew` carries 0755 inside
   the archive** — read from the entry's own `external_attr`, because `zipfile.extractall` drops
   modes and reading the mode after that extraction reports a false 0644. `gradlew` is the only
   entry with an execute bit, in this archive and in v4.24's;
5. `.git/`, `app/build/`, `.gradle/`, `.kotlin/`, `local.properties`, `tools/assets/staging/`,
   `__pycache__`, `.pyc`, and any APK or AAB **absent**;
6. secret scan over every non-binary entry — private-key blocks, GitHub and AWS token shapes and
   `password=`-style assignments — **0 hits**;
7. **built from the extracted copy with `--no-build-cache`: 53 of 53 tasks executed**, none
   up-to-date and none from cache, `assembleDebug` producing a **22 732 914-byte** APK. The
   Kotlin compiler emits **19 warnings** over the two source sets that build reaches — 11 in
   `main`, 8 in `test` — which is exactly the figure `BACKLOG_v4_24.md` item 50 records;
   compiling `androidTest` as well adds 2 more, for 21. **The cache is the trap here and it is
   the second time it has been written down**: a cached build finishes in seconds and reports no
   warnings at all, so `--no-build-cache` is what makes this step mean anything;
8. **tests from the extracted copy: 1 346 tests, 0 failures, 0 errors**; `lintDebug` **0 errors,
   27 warnings**, the same breakdown as the working tree's.

`git init` on the *extraction* confirms `CLAUDE.md` is ignored by the project's own `.gitignore`
(line 44) while `debug.keystore` is explicitly un-ignored (line 24): git would track **1 311 of the
1 312 files**, and `CLAUDE.md` is the only ignored one. The archive ships that local file
deliberately and it stays untracked.

**Mutation testing (12.11).** Four mutations, each reverted:

| mutation | what fired |
|---|---|
| `SpriteBlitter.draw` tints by `#EEEEEE` instead of the identity | `SpriteTintAtBlitTest` fails; **all five** `SpriteTintClassTest` assertions stay green — the gap, demonstrated |
| `PERSON_HEAD_SPRITE_UNITS` 24.3 → 20.0 | six assertions across `OccupantHeadFitTest` and `VehiclePedestrianScaleTest`; the rebuilt pixel comparison passed, and was retired for it |
| the shipped-narrow seated bust put back | *a head is as round wherever the same person is drawn* reads **0.64** against a floor of 0.85 and fails |
| **`drawSeatedOccupant`'s mirror removed** — `scale(-scale, scale)` → `scale(scale, scale)`, putting every occupant back to facing the rear | **one assertion of 45**, and not the one anybody would expect: `everyOccupantClearsItsPillarsByFifteenPercentOfItsHead`, saloon **14.0%** and police saloon 16.3%. **All 26 Canvas golden assertions passed. All 3 GL ones passed. `VehicleDrawOrderTest` passed. `theOccupantsFillHalfTheGlass` passed.** |

**The fourth is a finding, and 12.11 says to report it as one.** The suite does catch a reversed
occupant, but **only as a side effect**: mirroring moves the drawing's asymmetric ink to the other
side of the seat, and the saloon is the cabin with no margin to spare, so the pillar-clearance
assertion notices. Nothing in the project asserts *which way a person is looking*. On a cabin with
symmetric clearance the reversal would be invisible to every test and to every golden — and the
goldens are the frames that were regenerated **after** the fix, so they now depict the correct
facing and still do not defend it. What defends it is the acceptance sheet's second line (§2.10):
show every facing in both directions in the image the decision is made on.

**Publication is outstanding.** No tag, no push, no GitHub Release: that half is the maintainer's
and has not been done.
