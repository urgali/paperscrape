# v4.19 — verification report

**Status: PREPARED — NOT PUBLISHED.** `versionCode = 50`, `versionName = "4.19"`. No tag, no push,
no GitHub Release. Publication is the maintainer's decision and has not been taken.

Baseline: the published-pending **v4.18** tree, `PaperScrape_v4_18.zip`, SHA-256
`7242872f2b31ed7d04639806bee1f13c1d2831855c7a1d849cfd1ffbf93d500e`, 5 472 723 B, 849 entries —
verified entry-by-entry byte-identical against the working tree before a line was changed
(849/849, 0 differing, 0 extra) — MISURATO.

Every label below is one of **OSSERVATO** (seen on the phone), **MISURATO** (with method and
number), **DEDOTTO** (inferred from code or artwork), **DICHIARATO** (a known limit).

---

## 0. What this pass was, in one paragraph

The concept pass drew three replacement car bodies and the maintainer kept all three. This pass
puts them on the road with a stable rotation, lengthens the estate so it is visibly the long one,
redraws the fire engine into the same drawing language, seats children, and re-derives the
geometric criteria that v4.18 had never derived. **No renderer refactor, no new thread, timer or
polling, and no allocation added to the draw path.**

---

## 1. The three bodies, and the rotation — §1 and §4

| what | number | how |
|---|---|---|
| bodies a plain car can wear | 3 | `CarShell`: COMPACT, SALOON, ESTATE |
| taxi's body | COMPACT, always | DEDOTTO from `CarShell.forCar` |
| police car's body | SALOON, always | DEDOTTO from `CarShell.forCar` |
| distribution over the shipped catalogue | SALOON 43, ESTATE 26, COMPACT 16 of 85 plain cars | MISURATO |
| largest share taken by one body | 50.6% (floor for failure: 60%) | MISURATO |
| bodies never seen | none | MISURATO |

**Stability — the criterion the brief called non-negotiable.** The body is a pure function of the
vehicle's own immutable identity (`laneYFraction`, `startDelaySeconds`), bit-mixed so the rotation
does not read as a strict A/B/C cycle, and **resolved once** in `CarRuntime`'s constructor. Nothing
per-frame can reach it, which is the structural difference from v4.17's falling leaves
(`i % visibleCount` over a per-frame filter).

Four tests, all passing — MISURATO:

* `the body is a pure function of the vehicle's own identity` — 64 repeat calls per shipped car,
  plus a copy differing only in colour (a user-tintable field: if the body depended on it, changing
  a car's colour would change its model);
* `thinning the traffic never changes a surviving car's body` — every prefix of every theme's
  candidate list, re-sorted by lane, which is exactly what a density change does;
* `drawCar reads the body off the runtime and never picks one itself` — read out of the renderer's
  own source, so the choice cannot migrate into the draw path;
* `all three bodies occur across the shipped themes, and none dominates`.

**OSSERVATO on the phone:** swiped between home screens with traffic on screen; no vehicle changed
model. Captures show all three bodies in one frame.

**DICHIARATO.** The candidate grid has only ten distinct (lane, start-delay) pairs, so the same
slot draws the same body in every theme. That is the price of deriving the choice from immutable
identity, and it is the price that buys the stability; within any one frame the mixture reads as a
mixture, and the car *types* vary per theme on top of it.

---

## 2. The estate is the long one — §2

**The brief's premise needs one correction, and it is in the brief's favour.** It reported the
estate rendering at 352 px against the saloon's 392 and asked for the estate to be lengthened. The
352/392 pair does not reproduce: measured on the concept captures, both bodies carried **exactly
108 units of ink** and one local unit was the same pixel on both, so they rendered *identically* —
the blob measurement that produced 352 and 392 was merging and clipping adjacent cars. The estate
was not shorter than the saloon; it was **the same length**, which is the same defect by a
different number and needs the same fix.

| what | v4.19 | v4.18 concept | how |
|---|---|---|---|
| saloon length | 108 units | 108 | MISURATO off the SVG ink |
| estate length | **124 units** | 108 | MISURATO |
| estate / saloon | **1.1481 (+14.81%)** | 1.000 | MISURATO |
| where the length went | nose −58 → −66, tail 50 → 58 | — | front **and** rear, as asked |
| rendered, far lane, 1080x2340 | saloon 134.1 px, estate **154.0 px** | — | MISURATO from the render scale |
| rendered, near lane | saloon 155.1 px, estate **178.1 px** | — | MISURATO |

Pinned by `the vehicles keep their declared heights`, which fails if the ratio ever drops below
1.10.

---

## 3. The fire engine — §3

Redrawn in the cars' vocabulary. It keeps its own metre-per-unit (2.9 m over 68 units against the
cars' 0.0302 m/u) because forcing it onto theirs would need a 96-unit canvas for 0.29 MiB of
decoded sprite and no visible gain; the radii are written divided by the ratio between the two
units, so it is the **rendered** treatment that matches — DEDOTTO, and confirmed by eye.

| what | v4.18 | v4.19 |
|---|---|---|
| wheel arches | chord-and-arc, closing over the tyre | **concentric with the tyre**, one car-unit of air |
| wheel radius | 10 (smaller than a car's 11) | **10.5** = 14.8 car-units, bigger than a car's, as a truck's should be |
| twin rear axle | 23.5 units, 1.175 diameters | 24.68 units, 1.175 diameters |
| lamps | one headlight drawn as a code rectangle, **no rear lamp at all** | the same two lenses every car carries, amber forward and **red aft** |
| equipment lockers | three, at a row the arches ate two of | three, above the cream stripe — MISURATO, 3 runs of locker steel on the row |
| cab | short, windscreen too raked to hold a table-sized head | cab-over, upright screen, 19-unit glass |

**OSSERVATO on the phone:** night frames with the appliance beside a new car; they read as the same
set. Its beacons and the shared lenses light after dark.

---

## 4. The criteria, re-derived — §7

Each number below is *derived*, and the derivation is the point.

### 4.1 Pillar light — against the head, not the pane

v4.18 measured it against the pane, so it moved whenever the glass did and had to be cut from 15%
to 13% to pay for a wider cabin. Item 6 of the backlog asked for it against the head instead.

> **The floor is 15% of the head's own width.** The smallest a car is ever drawn is the far lane,
> where the projection puts **1.242 px on one local unit** at the reference 1080x2340 device
> (`CAR_BASE_SCALE × perspectiveScaleAt(0.834) × sceneScale(2340)` — MISURATO, not estimated). A
> band of glass thinner than about 3 px reads as an antialiasing seam between two shapes rather
> than as daylight between two people. 3 / 1.242 = 2.41 units = **13.3%** of an adult head's 18.08
> units, rounded up to **15%** so the floor sits above the threshold it comes from rather than on
> it — 2.71 units, 3.4 px in the far lane and 3.9 in the near.

Measured worst case on the shipped artwork, on the device, per row: **18.2%** — MISURATO.

### 4.2 Head gap — same derivation, same floor

Same legibility argument, same 15% floor. Worst case **15.2%** (the winter girl, whose bunches make
her head 22 units wide — the pitch was chosen on her, not on the adults) — MISURATO.

### 4.3 Glass fill — on the head's rows, and the neck row is gone

v4.18's intent kept, threshold kept at **50%**, band changed: measured crown-to-chin over the
occupied pane only. The neck row that item 2 showed was unsatisfiable at any pane width is not part
of any band. The estate's third window is excluded — it is a load bay, not cabin glazing, and
counting it would flatter the light and flatten the fill. Measured **50.8–66.3%** — MISURATO.

### 4.4 Driver forward

The driver's head centre falls in the forward half of the cabin on all three bodies, in both
directions — MISURATO.

### 4.5 Where the artwork moved rather than the number

Twice, and both times in the direction the brief asks for — **the cabin serves the people**:

* the seat pitch went 20 → **23 units** because at 20 the winter girl left 0.33 units of clear
  glass between the two heads;
* the compact's and the saloon's glasshouses were **stood up** (glass top-front −15 → −19, roofs
  moved to match) because at 24 units of pitch a crown crossed the raked A-pillar, and again
  because the device measurement found a driver's crown within about a unit of it.

**Nothing was deformed to pass a number, and no tolerance was widened to make a measurement pass.**
Where a v4.18 number was kept it is stated as kept; where it was replaced the derivation is above.

---

## 5. Occupants — §8

* driver: always an adult, man or woman — DEDOTTO from `driverKindIdx = driverSeed % 2`;
* passenger: **any of the four families, boy and girl included**, never the driver's own — proved
  over 100 000 seeds by `the two occupants of a car are never the same family`, which also asserts
  children are reachable;
* zero occupant pixels outside the glass, all three bodies, both lanes, every type — MISURATO on
  the device;
* head size from the height table, unchanged: the occupant is the **same size in every car**,
  because the three bodies share one metre-per-unit;
* occupant-vs-pedestrian parity, summer and winter, within ±10% — MISURATO by `OccupantHeadFitTest`;
* air above the head 10–25% for adults, 10–30% for children — a child is drawn shorter inside the
  same canvas, so a child seated in the same pane legitimately leaves more air. Split by family
  rather than holding children to the adults' ceiling, which would have meant scaling children up
  or shrinking the pane until adults hit the roof.

**OSSERVATO:** children visible as passengers in day, night and winter captures.

---

## 6. Sprite memory — §6

Calculated before drawing, as asked, and reported here as measured after.

| | bytes | MiB |
|---|---|---|
| v4.18 set (258 PNGs) | 29 331 180 | 27.972 |
| **v4.19 set (260 PNGs)** | **29 629 044** | **28.256** |
| ceiling | 29 884 416 | 28.5 |
| **free** | **255 372** | **0.244** |

**Lever 1 (sanctioned, free and safe):** the four adult base busts deleted, 297 792 B. Verified
pixel-by-pixel byte-identical to one of their own tone copies first — the man's to `_skin1`, the
woman's to `_skin0` — so no tone was lost. The registry declares the retirement (`retiredBases`)
and points the generator at the surviving heir, because "a derived variant's base must ship" is a
real rule and a silent deletion would have broken it.

**Lever 2 (sharing):** v4.18's two lamp overlays were 282x18 px each and almost entirely
transparent. They are four small lenses now (18x12 and 12x12), **shared by all three bodies and the
fire engine**: 2 880 B against 40 608 B, and one drawing instead of one per body.

**Lever 3 was not needed. The ceiling was not raised.** Nothing on the "never" list was touched: no
body was dropped, no occupant shrunk, no child bust deleted, no skin tone removed.

---

## 7. Tests

| suite | count | result |
|---|---|---|
| JVM unit tests | **1271** | **0 failures** — MISURATO, run to completion |
| Instrumented, OnePlus 6T | built 1, installed 1, started 134, **executed 134** | **0 failures** — MISURATO |
| Canvas goldens | 27 | 25 byte-identical, **2 regenerated with attribution** |
| GL goldens | 3 | pass unchanged |
| Asset-tool tests (Python) | **108** | **0 failures** |
| Asset pipeline | `probe` matches the pinned hash; `validate` clean; `normalize` reports no removable padding; `render` + `compare` = **PIXEL_IDENTICAL 140/140** | MISURATO |

**The three GL goldens did not move, and not because a tolerance hid it**: `day`, `lake-busy` and
`thunderstorm` render without the traffic warm-up, so those frames contain **no vehicles at all** —
DEDOTTO from `SharedGoldenScenes`, where `TRAFFIC_WARM_UP_FRAMES` is used only by the traffic
goldens.

### Golden attribution, before regenerating — MISURATO

| golden | changed | of frame | outside the four vehicles' columns | above row 640 or below row 689 |
|---|---|---|---|---|
| `traffic-day` | 3 145 px | 1.092% | **0 px** | **0 px** |
| `traffic-night` | 3 088 px | 1.072% | **0 px** | **0 px** |

Every changed pixel belongs to a vehicle or to the rows a taller body now reaches. Sky, hills,
buildings, trees, pavement, leaves, shop lights and road markings are untouched. **No tolerance was
altered**; the two goldens were regenerated because the artwork changed on purpose.

### Tests whose *pinned numbers* moved, and why

Each is a measurement of new artwork, re-measured rather than relaxed:

* `theVehicleItselfIsTheSameSizeItWas` → the band is now derived from the fleet (shortest body to
  tallest body-plus-roof-accessory) instead of one body plus a fixed 4 px;
* `everyOccupantClearsItsPillars…` → denominator changed from the pane to the head (§4.1). A defect
  in the *first* version of that change is worth recording: it took the row's min-to-max occupant
  span as "the head", which on rows showing both occupants is the **pair**, halving every ratio;
* `the deepest figure overlapped a far-lane car by the measured amount` → 0.0100 → 0.0135 of screen
  height, because the reference body grew from 50 to 56 units;
* `a fire engine towers over a car` → ratio 1.92 → **1.715**, since the cars grew;
* `a pedestrian standing on the near row clears the far lane's traffic` → **the premise had been
  stale since v4.6**. Its comment said "people are drawn after the cars"; `drawPeople` has run
  *before* the vehicle loop since v4.6, precisely so a car — always the nearer object — paints over
  a pedestrian. The assertion is now the depth relation the ordering rests on.

---

## 8. Performance — MISURATO, matched A/B, back to back in one session

Both builds are true R8 release configurations (`assembleRelease`, minify + shrink, **`flags=0x0`
non-debuggable verified on the device**) under a temporary env-guarded suffix, each **set as the
phone's actual live wallpaper**, same theme (Sunset, both fresh installs at their default), same
settle time before measuring, screen held on. v4.18 was **rebuilt from its own delivered ZIP** for
this comparison rather than quoted from its report.

| | **v4.19** | v4.18 (rebuilt) |
|---|---|---|
| SurfaceFlinger timestats, wallpaper layer, 31 s | 918 frames | 917 frames |
| dropped / janky | **0 / 0** | 0 / 0 |
| averageFPS | **29.897** | 29.856 |
| CPU (`top -H`, threads summed, 3 samples, 800% scale) | **22.2–24.9%** | 21.6–25.5% |
| PSS at ~1 min / ~2 min | 122.2 / **90.3 MB** | 104.0 / 99.7 MB |
| screen off | **0.0%** | 0.0% |
| same pid on wake | yes | yes |
| eglError / glError / exceptions in the pid's log | **0** | 0 |

**No regression in frame delivery or CPU.** The PSS pair is noisy in both columns and is
**DICHIARATO as such**: both processes had just run the settings UI to set the wallpaper, which the
project's own gotcha says inflates PSS, and the two readings straddle in opposite directions. The
settled two-minute figures favour v4.19 (90.3 against 99.7 MB); the one-minute figures do not. What
can be said cleanly is that both sit in the same band under the same treatment.

**A methodological correction worth keeping:** summing CPU by `tid == pid` reports **0.0%** for this
app, because the work is on the `PaperScrapeGlTh` render thread, which has its own tid. The numbers
above sum every thread of the package.

---

## 9. The static-wallpaper question — §11

**Answer: no crash exists, and the mechanism is the platform's.**

Searched and found nothing: **zero** dropbox entries for the package, **nothing** in the crash
logcat buffer, and the only two tombstones on the device are the vendor radio daemon (`qcrild`)
aborting — unrelated.

What does reproduce, deliberately and twice, is this: force-stopping the process hosting the live
wallpaper makes `WallpaperManagerService` rebind with a **null** component, which resolves to
`com.android.systemui/ImageWallpaper`, and then **persist** it — MISURATO from the log:

```
WallpaperManagerService: bindWallpaperComponentLocked: componentName=null
WallpaperManagerService: WPMS.onServiceConnected-ComponentInfo{com.android.systemui/…ImageWallpaper}
WallpaperManagerService: WPMS.saveSettingsLocked-0
```

So the phone showing the static system image at the start of the concept pass is explained by the
process having been force-stopped or hard-killed at some point, not by PaperScrape failing. It is
`BACKLOG_v4_19.md` item 13, with the note that nothing inside the app's own process can prevent it.

---

## 10. Invariants re-checked — §10

| invariant | result |
|---|---|
| occupant head from the height table | unchanged rule; the same head in all three bodies — MISURATO |
| occupant/pedestrian parity ±10%, summer and winter | MISURATO, `OccupantHeadFitTest` |
| zero occupant pixels outside the glass | **0**, three bodies × both lanes × every type — MISURATO |
| livery drawn before the occupants | `VehicleDrawOrderTest` — MISURATO |
| amber forward, red aft, direction readable from the vehicle alone | MISURATO, and **OSSERVATO** in a night frame carrying both directions |
| ground shadows anchored to the road | each body's own footprint now, not one shared 40 — MISURATO |
| leaves, shop-window light, building composition, shop dedup | untouched: **0 changed pixels** outside the vehicles in either golden — MISURATO |
| performance | §8 |
| no thread, timer, polling or draw-path allocation | none added; the body is resolved once per vehicle at construction — DEDOTTO |
| sprite ceiling 28.5 MiB | 28.256 MiB, ceiling not raised — MISURATO |

**One defect found and fixed in passing, which v4.18 ships:** the police car's night beacons were
absolute coordinates tuned for a roof that a later v4.18 pass then moved by arithmetic. The bar
followed the roof; the lit rectangles did not, so every night beacon since has glowed about seven
units to the right of its own dome. v4.19 derives them from the bar's own origin and from where
`police_lightbar.svg` paints its two domes.

---

## 11. Delivery

* `PaperScrape_v4_19.zip` — SHA-256, byte count and entry count are printed with the delivery, the
  hash not being able to live inside a file it covers. It replaces
  `PaperScrape_v4_18.zip` / `7242872f2b31ed7d04639806bee1f13c1d2831855c7a1d849cfd1ffbf93d500e`.
* The temporary performance scaffold is removed from `app/build.gradle.kts` and verified absent
  from the tree and the archive; both `.rc` packages are uninstalled from the phone.
* APK built from a **clean extraction of the delivered ZIP**, installed on the OnePlus, verified
  byte-identical when pulled back, and left running as the phone's wallpaper.

**Status: PREPARED — NOT PUBLISHED.**
