# BACKLOG_v4_20.md — the v4.19 backlog, closed

**Replaces `BACKLOG_v4_19.md`, and keeps its numbering** -- source comments and older documents cite
that file by item number, and every one of those numbers means the same thing here, so the citations
still resolve. Items 16 and 17 are new.

Every one of its fifteen items leaves this pass with an outcome written down: **RESOLVED** (corrected in code, with a test), **REJECTED** (decided against, with the
reason), or **DOCUMENTED** (nothing to fix; recorded so it is not rediscovered).

Closing the backlog does not mean everything was fixed. It means nothing is left without a
decision, which is a different and more useful property: an item with no outcome is an item the next
pass re-litigates from scratch, and three of the fifteen had been re-measured by hand in three
consecutive releases for exactly that reason.

---

## No open debt

Fifteen items in, fifteen outcomes out. Three new items were found during the pass and are recorded
at the end; one of them is open, deliberately, and says why.

| item | what | outcome |
|---|---|---|
| 1 | GL-GOLDEN-ADRENO, the characterised driver gap | **DOCUMENTED**, and now guarded |
| 2 | Pane fill at the neck row | closed in v4.19 |
| 3 | Winter visible-skin parity, 0.88 / 1.58 | **DOCUMENTED**, inside the test |
| 4 | Children do not ride | closed in v4.19 |
| 5 | A clothing-colour axis for the occupants | **RESOLVED** |
| 6 | The pillar light nobody had derived | closed in v4.19 |
| 7 | The sprite ceiling, and files the renderer cannot reach | **RESOLVED** |
| 8 | `VehicleOccupantAbCapture` cannot show direction | **RESOLVED** |
| 9 | Custom themes saved before pass six carry duplicate storefronts | **RESOLVED** |
| 10 | Shops and towers share the buildings palette | **REJECTED** |
| 11 | Special-vehicle spawn density can double up | **RESOLVED** |
| 12 | Release dates before v73 are unrecoverable | **DOCUMENTED**, moved to `RELEASE_HISTORY.md` |
| 13 | A force-stopped wallpaper does not come back | **DOCUMENTED**, it is the platform |
| 14 | The same as 11, photographed | **RESOLVED** with 11 |
| 15 | A pedestrian behind a nearer car is hidden to the shins | **DOCUMENTED**, the guard exists |

---

## Resolved

### 5 — the clothing-colour axis, and what the entry got wrong

**Done: 12 new sprites, a second outfit for both adult families, both seasons, all three tones.**

The entry said a family carries "the red top with the yellow band" or "the blue one". Measured on the
artwork, that is not what the sprites are. On a 141x132 bust the shoulders occupy y 111-127, and the
colour filling that band is the clothing: `(78,159,181)` for the man's summer, `(71,105,143)` for his
winter, `(228,98,62)` and `(191,65,48)` for the woman's. The **yellow is a headband** at y 27-44,
and the man's winter blue at y 3-46 is his hat, with his scarf at y 96-122. Each garment is a
*single flat colour*, which is why the axis needed no machinery beyond the recolour that already
generates the skin tones.

The second outfit is **the other adult family's garment paint for the same season**, so nothing was
invented -- the same rule the three skin tones follow, where every tone is a colour the set already
paints people in. `tools/generate_skin_variants.py` produces them in two verified single-colour
moves, garment then tone.

**One thing had to be added to the tool, and it matters.** Unrestricted, the garment move bled
outside the garment: the edge decomposition looks for any pixel that reads as a blend of the moved
colour with another, and on the winter busts the coat sits close enough in hue to the *hat* that
hat-edge pixels fitted that description. The man's winter bust came out with **93 pixels above the
shoulders moved by more than 24 levels** -- a quiet recolour of his hat's outline. The move is now
confined to the garment plus two pixels, the same reach the outline tests call the edge band, and
the generator fails if a single pixel outside it moves. Measured after: **zero** pixels change above
y=100 on all four busts.

**What it cost and how much of the frame it moves.** 12 sprites x 74 448 B = 893 376 B decoded.
See item 7 for where the space came from and `SpriteGeometryTest` for the ceiling argument.

The size is worth stating plainly, because it is the part the entry never asked about. The garment
is 21 canvas px across the shoulders = 7 canvas units = **3.330 local units** at
`CAR_OCCUPANT_SCALE`, which on the reference 1080x2340 device is **4.14 px tall in the far lane**
and **4.78 px in the near one**. Both clear the 3 px legibility floor v4.19 derived for the pillar
light -- so the axis does read, in both lanes -- but neither clears it by much. This is a small
change to a small part of the frame, and the reason to record the number rather than the impression
is that the next person to weigh 0.85 MiB against it should be weighing it against 4 px.

**The entry was also stale in a second way, and the correction is item 16.** It said every car
carries one man and one woman. It carried one *woman driver*, in every car, always.

### 7 — the sprite ceiling and the files nothing can reach

**Done, both halves, and the space was looked for before the ceiling was moved.**

Six sprites left the set:

- `house_window`, `road_asphalt`, `road_curb`, `road_line` — **212 328 B**. No call site has ever
  blitted them. They had been deferred as "their own question" in this backlog, in `ROADMAP.md` and
  in an exemption list inside `SpriteTintClassTest`, for four releases. Deleted, with their SVG
  sources.
- `person_boy_summer_head_car`, `person_boy_winter_head_car` — **148 896 B**. Verified byte-for-byte
  identical to their own `_skin2`, and re-deriving the other two tones from `_skin2` reproduces the
  shipped files with **zero differing pixels**. Declared in `retiredBases`, exactly as v4.19 did for
  the four adult bases.

**The girl's two bases still ship, and that is a decision rather than an omission.** Hers are not
duplicates of any of her tones: her own painted skin is `(239,185,148)`, a fourth colour no shipped
file carries, and the three canonical tones are the woman's, the man's and the boy's. Retiring them
in favour of `_skin0` was measured and it costs pixels -- regenerating her other two tones from
`_skin0` moves **165 to 406** anti-aliased pixels (summer 291/195, winter 406/165) against zero for
every base that was retired. So they stay, they are declared `usage: "orphan"` in the registry with
that measurement as the reason, and `SpriteReachabilityTest` requires exactly that of an unreachable
sprite.

**The mechanism that hid all of this is gone too, and it was the more important half.** A table in
`SceneObjectRenderer` listed the eight `head_car` bases and was read by nothing: its only effect was
to keep the files *referenced* so `UnusedResources` would stay quiet. `SpriteReachabilityTest` now
fails on any sprite table that is declared and never read, which is the shape the defect actually
took.

Where the set stands: **266 PNGs, 30 161 196 B = 28.764 MiB** against a ceiling raised to **29 MiB**
with its argument written where the limit lives.

### 8 — `VehicleOccupantAbCapture`

**Kept and parameterised, not deleted.** `reverse` was hardcoded `true`, so every frame the harness
had ever produced faced left by construction -- and those frames were read as evidence about which
way the traffic drives, which is what cost a wrong conclusion. It is kept because the releases it
serves are judgements about how something looks and the maintainer has to be able to regenerate the
pictures those judgements were made from.

Both direction captures are produced now, and `bothDirectionsAreActuallyRendered` is the one
assertion in the file that runs without `-e captureAb true`. It checks the *picture*, not the flag:
each direction is rendered on an empty street and the cabin glass has to be found where `drawCar`'s
own x mapping puts a car facing that way, which is on opposite sides of the screen. Asserting
`spec.reverse == reverse` would pass on a build that ignored the flag.

### 9 — themes saved before pass six

**Done: a one-shot migration, custom-theme schema 3 -> 4.** Of the shop-band candidates, the
depth-middle of each variant half-band stays a shop and the surplus take tower depths -- the same
rule and the same arithmetic as `SceneObjectCatalog.singleShopPerVariant`, applied to a stored layout
instead of a generated one. Only `depthFraction` moves, and only for the surplus: no theme gains or
loses a building, and none of its colours, densities or positions change.

The same migration repairs stored duplicate special vehicles (items 11/14), because the two are the
same story one release apart.

**It cannot lose the store, and that was the requirement.**
`customThemeDataFromJsonString` turns *any* exception out of the migration into
`CustomThemeData.EMPTY`, which is every theme the user has ever saved; the v3.1 DataStore corruption
is what that looks like from outside. Each entry is repaired inside its own `runCatching`, so a
malformed one is left exactly as found while its healthy neighbours are still repaired.
`PrePassSixThemeMigrationTest` feeds it seven shapes it has no business understanding -- a layout
that is a string, a car that is a number, a depth that is a word -- and requires it not to throw.
The migration is idempotent, which matters because a payload is re-migrated on every load until the
user next saves it.

### 11 and 14 — special-vehicle density

**Done: one vehicle of each special type per candidate set, capped where the types are rolled.**

The two entries are one defect, recorded once from the code and once from a photograph. Measured
before the fix: **eight of the twelve shipped themes** carried a duplicated special type, eleven
vehicles in total (five patrol cars, three taxis, three fire engines), and over 250 generator seeds
**171 carried at least one**. `sunset`, `autumn` and `halloween` each carried two fire engines, which
is what the v4.19 night capture photographed.

A theme's ten candidates all drive the same road and any of them can be on screen together, so
capping the *set* is strictly stronger than capping the screen and needs no per-frame state. A
surplus special becomes a plain car and keeps its slot, lane, speed and colour, so the road carries
the same number of vehicles. After: zero, in all twelve themes and at every density the slider
reaches. The chaos generator goes through the same cap, applied after its roll so its colours do not
move.

---

## Decisions recorded

These were not fixed. Each has a measurement and a reason, so a later pass does not reopen it out of
habit.

### 1 — GL-GOLDEN-ADRENO: **DOCUMENTED**, and turned into a guard

The three GL goldens are authored on the emulator's reference driver; on the phone's Adreno 630 the
same build places edges differently. **1.18 / 1.07 / 0.92%** of the outline when the metric was
derived, **1.2-1.4%** when v4.19 re-measured, against a 3% gate. It passes on both environments and
is not a test failure on either.

Closing it properly means per-driver golden sets, which double the GL golden maintenance and make
"the golden" ambiguous, or a shader change that removes the driver's freedom at an edge. Neither is
worth it for a gap under the gate. **So it stays — but it is watched.**
`GlDriverGapGuardTest` measures it on whatever driver it runs on and fails at **2%**, derived: about
40% above the worst figure ever recorded, so driver-revision noise does not reach it, and a third
below the gate, so it fires while a golden run still passes and there is time to find out *why* the
gap grew. A smoke alarm, not a limit.

That is the whole of what was missing. The gap was never a bug; it was a number nobody was watching,
which is why it was re-measured by hand three times.

### 3 — winter visible-skin parity: **DOCUMENTED**, inside the test

0.876 and 1.576 measured on *visible skin*; **0.905 and 0.964** measured crown-of-hat to chin, which
is the landmark a viewer reads, inside the ±10% band the summer faces are held to and asserted since
pass eight. No scale error produces a ratio below 1 for one figure and above 1 for the other: the
cause is that the woman's winter walking sprite hides more of her chin than her winter bust does.

An artefact of which measurement is taken, not a defect. The explanation now lives **in
`OccupantHeadFitTest` itself**, where whoever meets the 0.88 next will be standing.

### 10 — shops and towers share the buildings palette: **REJECTED**

Closing it needs a new palette slot, which is a theme-**and**-backup schema change, a migration and a
backup format version bump. Against that: the painted shop fronts added in v4.18 already carry the
differentiation, and the entry itself records that it "reads much less than it did". The
risk-to-benefit is negative and the answer is no, not later.

### 12 — release dates before v73: **DOCUMENTED**, and moved

The repository was received without Git history, so no date before v73 can be stated. Nothing to
fix. The note belongs in `RELEASE_HISTORY.md`, which is where someone looking for a release date
will be, and it is there now.

### 13 — a force-stopped wallpaper does not come back: **DOCUMENTED**, it is the platform

`WallpaperManagerService` rebinds with a **null** component, resolves to
`com.android.systemui/ImageWallpaper`, and calls `saveSettingsLocked` -- so the fallback is
persisted and survives a reboot. Reproduced twice on the OnePlus 6T on Android 15, with no crash
record of the package anywhere on the device. Nothing inside the process can prevent a decision the
platform takes after the process is gone.

Recorded in `release-notes/v4.20.md` as behaviour rather than left here as a defect, because the
person it affects is the maintainer and the notes are where they will read it.

### 15 — a pedestrian hidden to the shins: **DOCUMENTED**, the guard is already there

The deepest figure's feet sit **0.0135 of screen height** below a far-lane car's roof line, 32 px on
a 2400 px screen, against v4.18's 0.0100 and 24 px. The draw order is right: a car *is* nearer.
Closing it means moving the pavement rows or the lanes, which moves every object standing on them --
out of proportion to eight pixels. `PeopleTrafficDepthTest` carries both numbers, so the next pass
that moves either will be told.

---

## Guards in force

The tests that watch the documented items, so none of them has to be rediscovered:

| item | guard | what it asserts |
|---|---|---|
| 1 | `GlDriverGapGuardTest` | the Adreno-vs-reference edge displacement stays under 2%, a third below the gate |
| 3 | `OccupantHeadFitTest` | the crown-to-chin ratios stay in the ±10% band, with the 0.88 explained in place |
| 7 | `SpriteReachabilityTest` | every shipped PNG is reachable or declared orphan with a reason, **and** no sprite table is declared and never read |
| 7 | `SpriteGeometryTest` | the decoded set stays under 29 MiB, with the argument for the number beside it |
| 8 | `VehicleOccupantAbCapture.bothDirectionsAreActuallyRendered` | the harness renders the direction it is asked for, checked on the frame |
| 9 | `PrePassSixThemeMigrationTest` | the migration repairs, is idempotent, and cannot throw |
| 11/14 | `SpecialVehicleDensityTest` | at most one of each special type, in every theme, at every density |
| 15 | `PeopleTrafficDepthTest` | the pedestrian/traffic depth overlap, both numbers |
| 16 | `SeatedOccupantsTest`, `OneOccupantRuleTest` | both adults drive, all four families ride, every deal as even as ten allows |
| 18 | `VehicleOccupantScaleTest` | the driver's face against the constant, for **both** families, with the driver identified by position |
| — | `VehicleShellRotationTest` | the body deal is 4/3/3, stable per vehicle, and reads as a mixture |

---

## Found during this pass

### 16 — every car was driven by a woman: **RESOLVED**

Found while preparing item 5, and it is the reason item 5's entry was stale.

Everything about a seated occupant came out of `driverSeed % n`, where
`driverSeed = abs((laneYFraction * 7919 + startDelaySeconds * 131).toInt())`. That reads like a
mixture and is not one, for exactly the reason v4.19's shell hash was not: **the two fields carry
ten values between them**, and those ten are 6643 to 7033 stepping by 42 — so every one of them is
odd, and `driverSeed % 2` is 1 in all ten. Index 1 is the woman.

Measured across the twelve shipped themes' 120 cars, before the fix:

- the driver was a **woman in every single car**; no man had ever driven;
- the passenger was only ever a man (60) or a girl (60), so **no boy had ever ridden** — despite
  v4.19 seating children specifically so all four families could;
- the driver's skin tone came out 5 / 3 / 2 across the ten, the same imbalance the bodies had.

`SeatedOccupants` deals all five choices — driver family, passenger family, both tones, outfit —
over the ten identities, each as even as ten allows and each ordered so no lane repeats a value at
consecutive queue positions. Stability is untouched: still a pure function of the candidate's own
immutable lane and queue slot.

**Why nothing caught it, which is the part worth keeping.** `OneOccupantRuleTest` did exercise the
shipped expression — over `seed in 0 until 100_000`. It proved the rule (a passenger is never the
driver) and could not see that the app produces ten seeds, not a hundred thousand. *A test over an
input space the program cannot reach proves something about a function, not about the app.* It now
runs over the ten that exist and asserts the population as well as the rule.

The same lesson closed item 5 of the v4.19 backlog and the shell distribution: **wherever a car's
identity feeds a choice, the space is ten and the answer is a table.**

### 18 — the woman's face constant was wrong, and neither side could see it: **RESOLVED**

Found when the dealt occupants first put a woman behind the wheel in a test fixture.

`VehicleOccupantScaleTest` predicts a driver's face height from two constants: `MAN_FACE_UNITS =
77/3` and `WOMAN_FACE_UNITS = 82/3`. Measured on the shipped busts with the same tolerance the
frame scan uses, the man's visible skin spans **78 rows** and the woman's **66** -- her fringe cuts
a much lower hairline than his flat-top. The file's own comment said exactly that and the numbers
said the opposite, making hers the taller by 6%. She is 15% shorter.

**Why a whole release of tests never touched it.** The fixture built its car with
`startDelaySeconds = -0.5`, whose occupant seed is even, so **the fixture's driver was always the
man**. The app's ten real start delays are all odd, so **the app's driver was always the woman**
(item 16). The constant that was exercised was the one the app never used, and the one the app used
was never exercised. Neither blind spot is visible from inside the other, and the pair of them is
why this is recorded as a finding rather than as a typo.

Both constants now come from the measurement, and the note in the file records how they hid.

Two tests were corrected with them, and both corrections are about *what is being measured* rather
than about a threshold:

- the driver used to be identified as **the tallest face in the frame**, which worked only while
  the driver was reliably the taller family. With the seats dealt, the tallest face is often the
  passenger, so the measurement silently changed subject. The driver is now identified by
  **position** -- the leading face -- which is the rule the renderer is separately asserted to obey.
- `aDriversFaceMatchesAPedestriansOnceDepthIsRemoved` compared a driver's visible skin against the
  tallest pedestrian's without noticing that "tallest" selects a *family* as well as a depth. With a
  woman driving, it was measuring how much fringe she has -- which is precisely the artefact item 3
  documents. Both sides are now divided by their own family's visible skin first, so what is left
  is the depth question the test is named after.

### 17 — one malformed theme still costs the whole store: **OPEN, deliberately**

`customThemeDataFromJsonString` wraps the entire read in one `catch` that returns
`CustomThemeData.EMPTY`, so a single unreadable entry loses every *other* saved theme too. Five of
the seven damage shapes in `PrePassSixThemeMigrationTest` do exactly that. The v4.20 migration is
safe against this by construction — that is what its own tests assert — but the reader's behaviour is
older and wider.

**It is left open on purpose, because the obvious fix is worse.** Parsing each entry in its own
`runCatching` and skipping the unreadable ones would make a partial read look healthy, and the next
save would then write the store back **without the damaged theme** — silently discarding user data
instead of refusing to read it. `customThemeDataOrNull` exists precisely to tell "absent" from
"unreadable" for the read-modify-write path, and a partial read defeats it.

Closing it properly means a partial read that is *marked* as partial and blocks the write path until
the user is told. That is a UI decision as much as a data one, so it is recorded here rather than
guessed at.
