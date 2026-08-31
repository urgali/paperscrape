# PaperScrape v4.16 — release verification report

**PREPARED — NOT PUBLISHED.** `versionCode = 47`, `versionName = "4.16"`. Prepared 2026-08-31.
Baseline **v4.15** (`aba1554`). No push, no tag, no GitHub Release, no PR, no merge, no Dependabot
action. `compileSdk`/`targetSdk` remain 37.

**Every runtime figure in this report was measured on the physical OnePlus 6T** (ONEPLUS A6013,
Android 15, Adreno 630, 1080×2340). No emulator was started during this pass.

Each claim is tagged **VERIFIED** (observed or measured here), **OBSERVED**, **INFERRED**,
**NOT VERIFIED**, **CLOSED**, **WONTFIX** or **INTENTIONAL**.

---

## 1. Baseline — VERIFIED

| check | result |
|---|---|
| `versionName` / `versionCode` at start | 4.15 / 46 |
| working tree | clean; only `.claude/` and `.mcp.json`, this environment's own artefacts, untracked and excluded from the archive |
| tree vs the published v4.15 artefact | **783 files, byte-identical, SHA-256 file by file** |
| previous fixes present | **23 of 23** markers found in the current sources |
| tracked files | 782 |
| goldens / sprites | 27 / 221 |
| release notes / verification docs | 114 / 7 |
| files lost or added accidentally | none |

---

## 2. The reported defect, and what actually caused it — VERIFIED

The people in the cars are too big for the cars. They were, and the cause is that the scene had
**two rules for the same thing**.

| | head / pane | bust / pane | air above the head | head / vehicle |
|---|---|---|---|---|
| house, shop, tower occupant, since v4.2 | 51.9% | 79.8% | 20.2% | — |
| **car occupant, v4.15** | **72.6%** | **100%** | **0%** | **31.3%** |
| fire engine driver, v4.15 | 72.6% | 100% | 0% | 14.9% |

`drawCar` scaled a bust so its content was exactly as tall as the glass and anchored it on the sill,
so the top of the head coincided with the top of the pane **by construction** — every vehicle, both
lanes, both seasons. The same head sprite was 31.3% of a saloon and 14.9% of a fire engine in the
same frame.

### The perspective, traced in the code — VERIFIED, and not the problem

| | scale applied |
|---|---|
| vehicle | `CAR_BASE_SCALE * perspectiveScaleAt(laneY) * sceneScale` |
| pedestrian | `PERSON_BASE_SCALE * perspectiveScaleAt(rowY) * sceneScale` |
| building window occupant | the building's scale, at the building's own ground line |
| **occupant of a vehicle** | **none of its own** — blitted *inside* the vehicle's `scale(vehicleScale)` |

An occupant therefore inherits its vehicle's depth exactly. Asserted now as well as measured: the
near/far ratio of every occupant equals the two lanes' own ratio, 1.15637, to five decimal places.

**The consequence that decided this release:** because an occupant lives in the vehicle's local
units, `head/vehicle` and `head/pane` are *invariant* under `CAR_METRES_TALL`.

---

## 3. A/B on the OnePlus — VERIFIED

Every variant below was built, installed and rendered on the phone, and the frames were looked at,
not only measured.

| | vehicle | pane | occupant rule | head/pane | head/vehicle | verdict |
|---|---|---|---|---|---|---|
| **A** v4.15 | 1.45 m | 20.72 | bust = pane | 72.6% | 31.3% | heads against the roof line |
| **B** occupant smaller | 1.45 m | 20.72 | head = 51.9% | **51.9%** | **22.4%** | **chosen** |
| **C** bigger pane | 1.45 m | 23 | bust = pane | 72.6% | **34.7%** | *worse* — occupants larger still, taxi chequer onto the wheels |
| **D** bigger vehicle | 1.75 m | 20.72 | bust = pane | **72.6%** | **31.3%** | no change to either ratio; roof 44 px above the carriageway edge |
| **E** both | 1.75 m | 20.72 | bust 89.8% | 65.2% | 28.1% | crowds the road |
| **F** bigger pane + new rule | 1.45 m | 23 | head = 51.9% | 51.9% | 24.9% | occupants right, car reads as a van |
| — | 1.60 m + fire engine 3.20 | 20.72 | 85% | 61.7% | 26.6% | vehicles stretch the carriageway |
| — | 1.75 m + fire engine 3.50 | 20.72 | head-share | 51.9% | 22.4% | vehicles clearly too large for the road |

Supporting measurement: the carriageway is 148 px; a saloon body is 54 px at 1.45 m, 60 at 1.60 and
65 at 1.75, and its roof stands 30 / 37 / 44 px above the road's own top edge.

**Chosen: B.** The vehicle and the pane keep exactly the sizes v4.15 shipped; the occupant is the
only thing that moves.

---

## 4. The rule

```kotlin
OCCUPANT_HEAD_PANE_SHARE = 0.85f * WINDOW_HEAD_HEAD_UNITS / WINDOW_OCCUPANT_DIVISOR_UNITS  // 0.5194
CAR_HEAD_SCALE        = SHARE * CAR_GLASS_HEIGHT_UNITS        / CAR_HEAD_HEAD_UNITS
CAR_PASSENGER_SCALE   = SHARE * CAR_GLASS_HEIGHT_UNITS        / WINDOW_HEAD_HEAD_UNITS
FIRE_TRUCK_HEAD_SCALE = SHARE * FIRE_TRUCK_GLASS_HEIGHT_UNITS / CAR_HEAD_HEAD_UNITS
```

Derived from `drawWindowOccupant`'s own expression, not chosen. Anchored on the **head** rather than
the bust because the two families carry different amounts of head — 106 px of 146 for a driving
head, 110 of 169 for a window one — so matching busts would leave a driver's head 12% larger than
the passenger's beside them.

Final geometry, all VERIFIED on the device:

| | head/pane | bust/pane | air | head/vehicle | near lane | far lane |
|---|---|---|---|---|---|---|
| saloon / taxi / police driver | 51.9% | 71.5% | 28.5% | 22.4% | 15.85 px | 13.71 px |
| passenger | 51.9% | 79.8% | 20.2% | 22.4% | 15.85 px | 13.71 px |
| fire engine driver | 51.9% | 71.5% | 28.5% | 10.7% | 15.13 px | 13.08 px |

Near/far ratio 1.15637 for all of them — the lanes' own ratio.

---

## 5. Skyscraper proportions — VERIFIED, no change needed

Measured against the projection and looked at on the rendered frame, not only in the size table.

| | metres | sprite units | m/unit | vs the tower |
|---|---|---|---|---|
| TOWER | 15.60 | 182 | 0.08571 | — |
| HOUSE_LARGE | 7.60 | 145 | 0.05241 | 2.05× |
| HOUSE_SMALL | 5.76 | 110 | 0.05236 | 2.71× |
| RESTAURANT | 8.20 | 96 | 0.08542 | 1.90× |
| BAR | 7.70 | 92 | 0.08370 | 2.03× |
| TREE | 9.479 | 118 | 0.08033 | 1.65× |

The tower is 8.9 people tall and draws five window rows plus a setback — 3.1 m a storey. On the
rendered skyline it stands at about twice a large house, with the trees between the two, and the
mast rises above the declared height exactly as a shop's sign rises above its wall.
`BuildingHeightDeclarationTest` reads every building's blits and holds. **No discrepancy, documental
or real.** The skyline was not moved to satisfy a table.

---

## 6. People at windows — VERIFIED on the device

`Exposure.INDOORS` / `OUTDOORS` is unchanged and untouched by this release. Verified by rendering a
**winter street** on the phone and looking at it:

- pedestrians: hats, scarves, winter coats — OUTDOORS ✓
- occupants of cars, taxi, police car and fire engine: winter hats and scarves — OUTDOORS ✓
- occupants of houses and towers: short sleeves, no hat, no scarf — INDOORS ✓

Also verified live on the phone in the **Christmas** theme at night: snow, fairy lights, firs and
gifts render; pedestrians dressed for outside, window occupants not. **Sunset** (the capture
harness's theme) day and night, and **Desert** (the maintainer's own theme, seen live during the
runtime checks) show the same. `IndoorClothingTest` pins the rule deterministically.

---

## 7. GL cross-device — VERIFIED, metric unchanged

The two-part metric from v4.15 is untouched and no tolerance was widened. On the OnePlus 6T's
Adreno 630 the full instrumented suite passes, GL goldens included.

`GlGoldenMetricTest` damages a golden and requires the metric to catch it. This release adds two
cases the brief asked for, so it is now **seven kinds of damage**, all caught, plus one that must be
accepted:

| damage | caught |
|---|---|
| an object moved three pixels | ✓ |
| a flat area painted the wrong colour | ✓ |
| an object erased | ✓ |
| a global tint shift | ✓ |
| **a three-percent scale error** (new) | ✓ |
| **a rearranged composition — a mirrored block** (new) | ✓ |
| one pixel of edge displacement | correctly *accepted* |
| the goldens leave enough interior to judge them by | ✓ |

---

## 8. Golden frames — VERIFIED

Two changed: `traffic-day` and `traffic-night`. **They did not fail.**

- diff against the v4.15 goldens, measured before anything was touched: **282 px and 276 px** over
  the channel tolerance out of 288 000 — **0.098% and 0.096% against a 0.200% gate**;
- bounding box `y 637..678, x 93..356`, and **zero differing pixels outside the vehicle band**;
- in the day frame **46% of the changed pixels become glass** — the head withdrawing, which is
  exactly the intended change.

Only then were they regenerated. They were regenerated **from the OnePlus**, because this pass is
not permitted to start an emulator, and that is safe here rather than assumed: the same code
rendered on the emulator and on the phone differs by **at most 3 and 7 per channel with zero pixels
at or over the ≥8 tolerance**, so the two devices' frames are interchangeable for this comparison.

---

## 9. Verification after the bump

| gate | result |
|---|---|
| `clean` + `test --rerun-tasks` | **1230 tests, 0 failures, 0 errors** |
| `lint` | 0 errors |
| `assembleDebug` / `assembleDebugAndroidTest` | OK |
| `assembleRelease` / `bundleRelease` | OK (unsigned: no maintainer credentials used) |
| `connectedDebugAndroidTest`, **OnePlus 6T** | **116/116** |
| goldens on the OnePlus | pass, GL cross-device included |
| Python asset suite | **108/108**, 682 subtests |
| `probe` | `ec77e95d…`, `matches_expected: True` |
| `inventory` | 221 files, 197 unique drawings, 24 duplicate groups |
| `validate` / `normalize` / `compare` | EXIT 0 / 73 targets, no removable padding / 125 PIXEL_IDENTICAL |
| goldens touched by the bump | none |

No emulator was started at any point.

### Mutation testing of the areas changed — VERIFIED

| area | mutation | killed |
|---|---|---|
| occupant scale | head share = 1.0 (the v4.6/v4.15 behaviour) | ✓ |
| occupant scale | head share = 0.35 | ✓ |
| occupant scale | share taken from the bust instead of the head | ✓ |
| occupant scale | driver reverted to the old rule | ✓ |
| occupant scale | passenger reverted to the old rule | ✓ |
| occupant scale | fire engine reverted to the old rule | ✓ |
| occupant scale | passenger measured against the wrong family's head | ✓ |

Seven of seven. The GL metric's own seven damage cases are above.

---

## 10. Performance — VERIFIED on the OnePlus, A/B against v4.15

Both builds measured by the identical method, 120 s each on the home screen with the display held
awake, `dumpsys SurfaceFlinger --timestats` on the `Wallpaper BBQ wrapper` layer.

| | v4.15 | v4.16 |
|---|---|---|
| frames in 120 s | 3554 | **3554** |
| dropped frames | 0 | **0** |
| janky frames | 0 | **0** |
| process CPU | 110.9% of one core | **104.6%** |
| RSS, fresh install, 120 s idle | 147.6 MB | **146.6 MB** |
| screen off, 120 s after a 20 s settle | — | **0.092% of one core** |
| GL / render threads | 2 | **2** |

The change is three compile-time constants in a scale expression: no allocation, no draw call, no
lock, nothing new in the draw path.

**OBSERVED, pre-existing, not caused by this release:** six preview↔home cycles take RSS from
146 MB to 278 MB. It recovers on its own — 278 → 198 MB after 45 s, → 180 MB after one screen
off/on — so it is the preview's surfaces being held, not a leak. The thread count returns to 28 and
the GL/render thread count stays at 2 throughout.

---

## 11. GL lifecycle — VERIFIED on the OnePlus

| exercise | result |
|---|---|
| install over the active wallpaper | wallpaper component survives; process recreated as expected; 2 GL/render threads after |
| 5 × screen off / on | RSS 178.6 MB, 30 threads, 2 GL/render threads |
| 3 × settings ↔ home, then 3 × preview ↔ home | 32 threads, 2 GL/render threads, RSS recovers |
| 6 × rotation with the settings screen in front | **0 Activity relaunches** (ARC-08 holding) |
| EGL / GL errors in logcat | **none** |
| `FATAL EXCEPTION` / ANR | **none** |
| residual threads | none; the count returns to its baseline |

**NOT VERIFIED:** process death and rebind of the wallpaper engine. `adb root` is refused on this
production build and force-stopping a wallpaper makes `WallpaperManagerService` revert to the system
wallpaper by design, so the case cannot be produced honestly here.

---

## 12. Weather — VERIFIED end to end on the device, for the first time

The phone had network this session (airplane mode on, Wi-Fi up, 27 ms to 1.1.1.1), so Live Weather
was exercised for real rather than only in deterministic tests.

| step | observed |
|---|---|
| Location **Off** → **Custom** | coordinates accepted, "Selected location — 45.464, 9.190" |
| Live Weather **on** | *"Waiting for the first forecast. Until it arrives the scene is on this theme's own weather."* |
| after the first fetch | *"Real conditions are driving this scene's clouds and precipitation, so their screens are read-only."* — a real Open-Meteo fetch completed |
| provider switched to one with no key | *"WeatherAPI.com needs an API key. No requests are being made until one is entered; the scene is running on this theme's own weather."* — the guarded-failure path, no request attempted |

**208 deterministic JVM tests** cover the states that cannot be produced on demand — stale, retry
ladder, age cap, clock changes, two engines, restart. **NOT VERIFIED on the device:** a STALE
transition and a transient network failure, because neither can be provoked without changing the
device clock, which `adb root` being refused makes impossible.

---

## 13. Findings

Re-checked against the current sources rather than trusted from the previous report. Every marker
below was grepped for in the tree that ships.

| ID | status | evidence in the shipping tree |
|---|---|---|
| ARC-01 | **CLOSED** | `GlLifecyclePolicy`; 6 rotations on the device, 0 relaunches, 2 GL/render threads |
| ARC-02 | **FIXED** | `if (!visible)` gate; 0.092% of a core with the screen off |
| ARC-03 | **CLOSED / intentional** | the screen-off cost is the render thread's own |
| ARC-04, ARC-06, ARC-07 | **CLOSED** (earlier releases) | verified present |
| ARC-05-res | **CLOSED / intentional** | per-engine rebuild budget, pinned by test |
| ARC-08 | **FIXED** | `android:configChanges` + hoisted `updateState`; 0 relaunches over 6 rotations |
| ARC-09 | **FIXED** | `editDurably` |
| ARC-10 | **FIXED** | `if (previous == data) return` |
| ARC-11 | **FIXED** | `MemoryPressurePolicy.dropsGpuTextures` |
| ARC-12 | **FIXED** | threading comments corrected |
| REN-01 | **WONTFIX** | not reopened |
| REN-02, REN-03, REN-05 | **FIXED / CLOSED** | verified present |
| REN-06 | **FIXED** | `vehicleEdgeMarginPx` |
| REN-07 | **FIXED** | `SpriteMeasurementClaimTest` reads the PNGs |
| REN-08 | **FIXED** | jitter clamped to `roadTopYFraction()` |
| REN-09 | **FIXED** (this release) | the occupant/pedestrian comparison measured in metres, which cancels depth; replaced by pane-share, vehicle-share and depth inheritance |
| REN-10 | **FIXED** (this release) | the scene's two window rules had never been compared; `OneOccupantRuleTest` reads `drawWindowOccupant`'s expression from the source |
| REN-11 | **FIXED** (this release) | the instrumented `driver >= pedestrian` assertion was v4.6's overcorrection, satisfiable only by a bust filling its window; replaced by `everyOccupantHasGlassAboveTheirHead`, read off the rendered frame |
| BCK-01 … BCK-09 | **FIXED / CLOSED / ACCEPTED** | `requireFinite`, `BoundedImport`, `customThemeDataOrNull`, `ImportStaging`, `customThemeSchemaVersion` all present |
| SEC-01 … SEC-09 | **FIXED / CLOSED** | `WrongSignature`, `MAX_HTTP_BODY_CHARS`, `WeatherRequest`, proguard SEC-09 note all present |
| WEA-01 … WEA-09 | **FIXED** | backoff ladder (`isAttemptDue`), age cap, preview status gate, INTERNET inventory; success and guarded-failure paths seen on the device |
| SCL-01 … SCL-06 | **CLOSED / intentional** | co-registered canvases hold; `OccupantHeadFitTest` reads every family member |
| GL-GOLDEN-ADRENO | **FIXED** | two-part metric; OnePlus 116/116 including GL goldens |
| CLIP-LIBRARY-WIDE | **CLOSED / intentional** | 210 of 221 sprites reach a canvas edge by authoring convention |
| TOOL / pipeline | **FIXED** | pixel digests, per-axis membership, declared Gradle test inputs, pinned probe |

**No finding is open and none is deferred.**

---

## 14. Test quality

- The tests written for this change do not restate their own definitions. `OneOccupantRuleTest`
  reads `drawWindowOccupant`'s scale expression **out of the source file**, so the two window rules
  cannot drift apart by editing a constant. `OccupantHeadFitTest` reads the **PNGs**.
  `everyOccupantHasGlassAboveTheirHead` reads the **rendered frame**.
- The one assertion in the touched area that *was* a tautology — "a bust is as tall as its glass",
  true by the definition of the scale — is still there and is still labelled as pinning the shape of
  the rule and nothing about the pictures, with the tests that do look at pictures named beside it.
- Mutation testing was targeted at the changed area only: 7 mutations, 7 killed.

---

## 15. Limitations

None is an open finding.

- **Process death and rebind of the wallpaper engine was not produced.** `adb root` is refused and
  force-stopping a wallpaper reverts it by design.
- **A weather STALE transition and a transient fetch failure were not produced on the device**; both
  need the clock moved. Deterministic tests cover them.
- **`assembleRelease` and `bundleRelease` are unsigned.** Signing needs the maintainer's keystore,
  deliberately not used.
- **No Compose UI test suite exists** (TST-03, an accepted project position).
- **The local repository has no `origin`**, so the tree could not be diffed against GitHub; the
  baseline comparison is against the v4.15 artefact delivered from here, file by file.
- **No emulator was used**, by instruction. The two regenerated goldens were therefore authored on
  the OnePlus; the equivalence of the two devices' frames for these goldens is demonstrated in §8
  rather than assumed.
- **The phone was rebooted once**, to repair the wallpaper state the system itself cleared on
  repeated package replacement. See §16.

---

## 16. The packaged build, on the phone — VERIFIED

The archive was extracted into an empty directory and built there: `assembleDebug`,
`assembleDebugAndroidTest`, `assembleRelease`, `bundleRelease`, `lintDebug` and
`testDebugUnitTest --rerun-tasks` all succeed, **1230 tests, 0 failures**, and the APK reports
`versionCode='47' versionName='4.16'`.

That APK was installed on the OnePlus 6T and the binary pulled back off the device is byte-identical
to it (`sha256 c38f545e6df08154…`), so what was looked at is what the archive contains.

| check | result |
|---|---|
| `versionName` / `versionCode` read with `dumpsys package` | **4.16 / 47** |
| app starts | settings screen renders |
| engine runs | the full live scene renders at 1080×2340 — night sky, moon, stars, clouds, lit buildings, trees, pedestrians and six vehicles |
| people in the cars | clear glass above every head, on both lanes |
| taxi, police, fire engine, saloon | all four correct, near and far |
| people at windows | indoor clothing, winter and Christmas both checked |
| skyscrapers, houses, trees, road, birds, clouds, seasonal elements | correct |
| Winter / Christmas / Sunset / Desert, day and night | all seen |
| animation | traffic advances; near-lane occupants larger than far-lane ones |
| crash / ANR / EGL error | **none** |
| frames | 3554 in 120 s, 0 dropped, 0 janky |

### The device was left as it was found, and one thing had to be repaired

Reinstalling the debug package repeatedly while it was the active wallpaper put
`WallpaperManagerService` into `mBindSource=SET_LIVE_TO_CLEAR` — the system dropped the live
wallpaper and fell back to `ImageWallpaper`. **This is the system's own behaviour on package
replacement, not a defect in the app**, and it took the maintainer's own wallpaper with it.

It was repaired: the phone was rebooted, and the maintainer's release package was set as the live
wallpaper again through its own picker. Two things were learned doing it, and are recorded because
they cost several attempts:

- the picker's *Set wallpaper* button does not respond to `input tap`, because the live preview's
  `SurfaceView` swallows the synthetic touch; `input keyevent DPAD_DOWN` twice then `ENTER` works;
- `input tap` on a dimmed screen only wakes it, so `svc power stayon true` has to come first.

Final state, **VERIFIED**: the live wallpaper is `com.paperscrape.livewallpaper` (the maintainer's
release, v4.15) and it is running — the home screen animates; the debug and test packages are
uninstalled; nothing else on the device was changed.

---

## 17. Accounting

| | |
|---|---|
| tracked files, v4.15 | 782 |
| tracked files, v4.16 | **785** |
| added | **3** |
| modified | **10** |
| removed | **0** |
| unchanged | **772** |
| reconciliation | 782 + 3 − 0 = **785** ✓ |
| diff | 13 files, +983 / −104 |
| goldens | 27 (2 regenerated, cause identified and measured) |
| sprites | 221 (0 changed) |

### Added

`app/src/test/.../OneOccupantRuleTest.kt`, `release-notes/v4.16.md`,
`release-verification/V4_16_REPORT.md`.

### Modified

`RELEASE_HISTORY.md`, `app/build.gradle.kts`, the two `traffic-*` goldens,
`GlGoldenMetricTest.kt`, `VehicleOccupantAbCapture.kt`, `VehicleOccupantScaleTest.kt`,
`SceneObjectRenderer.kt`, `OccupantHeadFitTest.kt`, `VehiclePedestrianScaleTest.kt`.

### Excluded from the archive — verified absent

`.git/`, `build/`, `app/build/`, `.gradle/`, `.idea/`, `.kotlin/`, `local.properties`, `*.apk`,
`*.aab`, release keystore, virtualenvs, caches, `__pycache__`, `staging/`, `golden-output/`,
`.claude/`, `.mcp.json`, experiment scratch files, credentials, and any reference to this machine's
filesystem.

### Included

`.gitignore`, `.github/`, `CLAUDE.md` (per the project's convention — gitignored but shipped),
`AI_PROJECT_RULES.md`, `app/`, `tools/`, tests, androidTest, goldens, documentation, release notes,
release verification, and the committed, publicly-known `debug.keystore`.

---

## 18. Status

**v4.16 — PREPARED, NOT PUBLISHED.** No push, no tag, no GitHub Release, no PR, no merge.
Publication is the maintainer's.
