# PaperScrape v4.15 — release verification report

**PREPARED — NOT PUBLISHED.** `versionCode = 46`, `versionName = "4.15"`. Prepared 2026-08-31.
Baseline **v4.14** (`7e00de4`). No push, no tag, no GitHub Release, no PR, no merge, no Dependabot
action. `compileSdk`/`targetSdk` remain 37.

---

## 1. What this release is

A closing pass. Every finding the original audit and the batches after it had left open was re-read
against the current code, measured, and either fixed or classified with concrete evidence. **No
finding is OPEN and none is DEFERRED.**

---

## 2. The three visual controls

### 2.1 Occupants and the car window — the window grew, the people did not

With a winter theme a passenger's bobble hat stood **3 px above a 27 px window** on a OnePlus 6T at
the near lane, painted onto the car's roof, and the driver's beanie 1 px. The cause is one
content-height constant per sprite family, taken from a *representative* rather than from the tallest
member: the winter members are taller, and the bust is anchored at the sill, so the excess goes up
with nothing clipping it.

**Both ways of shrinking the people were tried and measured, and both re-opened the defect the scales
exist to prevent:**

| approach | overflow | what it cost |
|---|---|---|
| window family constant → 169 (the maximum) | fixed | passenger's head **68.3%** of a pedestrian's, against a documented 70–90% band |
| car family constant → 146 alone | driver's 1 px fixed | driver's face **14 px** against the nearest pedestrian's **15** — the nearer person drawn smaller, which is the v4.6 defect |
| **glass 19 → 20.72 units** | **fixed** | passenger's head **exactly the size it has always been** (0.407 m, 74.5%); driver's grows 6.8% to 83.1%, in band |

`CAR_GLASS_HEIGHT_UNITS` is now `19f * 169f / 155f`. The sill moves 13 → 14.72, still 3.3 units clear
of the beltline at y=18, and `police_stripe` and `taxi_checker` are blitted **at the sill** rather
than repeating the old `13f` literal.

**Measured on the OnePlus 6T** (`VehicleOccupantAbCapture`, winter, near lane):

| | glass | passenger's pompom | driver's beanie |
|---|---|---|---|
| v4.14 | 28 px | **+3 px outside** | **+1 px outside** |
| v4.15 | 30 px | inside | inside |

Liveries checked on the device for plain, police, taxi and fire engine, summer and winter: the stripe
and the chequer sit on the doors, clear of the wheels and of the body floor.

### 2.2 Skyscraper proportions — the declaration was corrected, the skyline was not

Every building declares exactly what it draws, **except the tower**: it declared **196** units and
draws **182** (facade 150 + setback 32). 196 is where its *mast* ends, and the rule at the top of the
size table excludes exactly that — "a shop's height is its wall, not the top of the sign hanging
above it", which is why `RESTAURANT` declares 96 for a 96-unit wall and says nothing about its sign.

Corrected to `TOWER(15.6f, 182f)`. The metres-per-unit is **identical** (0.08571), so the scale comes
out at the same 3.857 px per unit: **no pixel moved**, verified by the goldens not changing.

Two hierarchy assertions moved with it, and this is stated plainly rather than quietly: they demanded
`tower > 2 × shop` and passed on the inflated number. The **drawn** ratio is **1.90** and always was.
The test that claimed to check "drawn pixels, not only metres" was a tautology —
`baseScale × spriteUnitsTall` reduces to `metres × pixelsPerMetre` — and is now annotated as resting
on `BuildingHeightDeclarationTest`, which reads every building's blits.

### 2.3 Window occupants are indoors

`Exposure.INDOORS` / `Exposure.OUTDOORS`, with **one** function turning it into a season column. The
three hand-written `if (customization.winterColorsEnabled) 1 else 0` are gone; pedestrians and people
in cars pass `OUTDOORS`, window occupants pass `INDOORS`.

Verified on the OnePlus across **Sunset** (summer), **Winter**, **Christmas** and **Desert**
(non-winter): the same window, same theme, same position — white bobble hat and winter coat before,
hairband and T-shirt after — with pedestrians in the same frame still hooded and scarfed. Skin
variants are unaffected: the season axis is a different index from the skin axis.

---

## 3. GL goldens cross-device — closed

v4.14 recorded three GL goldens failing on Adreno 630 at 1.108% / 1.290% / 1.682% against a 0.500%
gate, **byte-identical between two commits on the same phone**. Characterised rather than tolerated:

- the median difference among differing pixels is **1**;
- **99.8% / 98.2% / 86.4%** of the over-threshold pixels lie within one pixel of an edge;
- away from every edge there remain **6 / 65 / 660** pixels out of 288 000;
- no whole-frame translation reduces the count.

That is sub-pixel edge rasterisation. A flat-colour, hard-edged art style is the worst possible case
for a whole-frame count, because half a pixel of edge placement produces a *maximal* per-channel
delta along every silhouette — and the frame is nothing but silhouettes.

The comparison is now **two measures, both strict**, and the threshold was not widened:

| measure | catches | Adreno vs emulator | a real regression |
|---|---|---|---|
| flat interiors, ≥16/channel, limit 0.500% | wrong colour, wrong tint, erased object | 0.002–0.229% | erased object, global tint |
| outline displacement > 1 px, limit 3.00% | an object somewhere else | 0.92–1.18% | **13.68%** for a 3 px drift |

Neither alone is enough: a tint shift moves no edge, and a band slid three pixels changes only 0.075%
of interiors. `GlGoldenMetricTest` damages a golden five ways and requires each to still be caught,
then reproduces the driver difference itself and requires it to pass. A guard fails the comparison if
a scene were ever so detailed that the edge band covered most of it.

**The OnePlus 6T now runs the full instrumented suite 112/112**, for the first time.

---

## 4. ARC-08 — the download outlives what used to kill it

**Reproduced before it was touched**: two rotations produced two `finishDrawing of relaunch` entries
for `SettingsActivity` on the OnePlus, and that recreation cancels the `rememberCoroutineScope` the
download runs in.

Two local causes, and the second was not in the finding as written:

1. `SettingsActivity` declared no `configChanges`, so every rotation, light/dark switch and
   font-scale change destroyed it. It now handles them itself, which is what a Compose screen is
   built to do.
2. The update state was `remember`ed inside `AdvancedScreen`, one level below the scope that writes
   it — so walking back to the settings home mid-transfer left the job running with nowhere to
   report, and returning showed `Idle` for a download already in the cache. It moved up to the
   composable that owns the scope.

**Verified on the device**: the same two rotations, plus a light/dark switch and a font-scale change,
produce **zero relaunches**. The landscape layout was captured and checked.

**Deliberately not done**: a `Service`, or any scope outliving the Activity. Leaving the screen for
real still destroys it and still cancels the download, which is the behaviour asked for.

`UpdateDownloadLifetimeTest`: 4 tests. Mutations killed: removing `configChanges`, dropping `uiMode`
from the list, moving the state back into `AdvancedScreen`, substituting a process scope.

---

## 5. BCK-06 — an import a kill lands in the middle of, without a journal

`NonCancellable` stops the *caller* going away; it cannot stop the process being killed, and between
the two stores' writes the preferences were new while the saved themes were old.

The two stores will never share a transaction, but **each guarantees its own write is atomic**, and
that is enough to make the *pair* recoverable: the second store's whole payload rides inside the first
store's atomic edit, is applied, and is then cleared. `finishPendingImport()` runs at both entry
points that read the themes — the wallpaper service and the settings screen — so the window closes
before anything can observe it.

**This is not a journal.** There is no sequence to replay and nothing to undo, because the pending
document *is* the whole of the remaining work; `ImportStaging` is nine lines. The store is written
from the staged string rather than a re-serialised copy, so completing later is bit-for-bit the same
write — which is what makes recovery idempotent.

Coverage:

- `AtomicImportTest`: **6 JVM tests** killing the import at every point there is, then recovering;
- `BackupRepositoryTest`: **3 new instrumented tests** against real `DataStore`s, including the kill
  between the two writes;
- **5 mutations killed**: no staging, clear before the themes write, `finish` not writing, `finish`
  not clearing, `finish` acting with nothing pending;
- **end to end on the phone**: export → restore → "Backup restored.", no `pending_import_themes` key
  left in the datastore, no errors in logcat.

Two existing instrumented tests failed on the OnePlus and **were right to**: their store doubles
overrode `replaceAll`, and the import's seam is now `replaceAllJson`. The doubles were moved to the
seam rather than the code bent back to them.

---

## 6. Complete findings table

Every row below was re-checked against the final code; the marker column is the thing grepped for.

| ID | status | evidence in the final tree |
|---|---|---|
| ARC-01 | **CLOSED** (earlier release) | `GlLifecyclePolicy.surfaceCreated` guard; 3 rotations on device = 1 GL thread |
| ARC-02 | **FIXED** | `if (!visible) { weatherWakeUp.receive(); continue }` — screen-off process cost now equals the render thread's alone |
| ARC-03 | **CLOSED / intentional** | measured at **0.05% of one core** over 180 s screen-off; real in code, irrelevant in fact |
| ARC-04 | **CLOSED** (earlier release) | the cross-thread read was removed with WEA-07 |
| ARC-05-res | **CLOSED / intentional** | per-engine rebuild budget, documented by test |
| ARC-06, ARC-07 | **CLOSED** (earlier release) | white-pixel repair; `removeCallbacks(drawRunnable)` |
| **ARC-08** | **FIXED** | `android:configChanges` + `updateState: MutableState` hoisted; 0 relaunches on device |
| ARC-09 | **FIXED** | `editDurably`, 88 call sites, one `NonCancellable` helper |
| ARC-10 | **FIXED** | `if (previous == data) return` |
| ARC-11 | **FIXED** | `MemoryPressurePolicy.dropsGpuTextures` |
| ARC-12 | **FIXED** | three threading comments corrected against the code |
| REN-01 | **WONTFIX** (earlier release) | not reopened |
| REN-02 | **FIXED** | contract narrowed to what holds; the hill never reaches the GPU fan, pinned by test |
| REN-03 | **CLOSED** (earlier release) | cloud geometry corrected in v4.11 |
| REN-05 | **FIXED** | PorterDuff claim corrected; `TintOpacityTest` asserts what it rests on |
| REN-06 | **FIXED** | `vehicleEdgeMarginPx`, identical at every real viewport, correct past 3900 px |
| REN-07 | **FIXED** | five stale measurements corrected; `SpriteMeasurementClaimTest` reads them off the PNGs |
| REN-08 | **FIXED** | jitter clamped to `SceneSpace.roadTopYFraction()` |
| SCL-06-penguin | **CLOSED / intentional** (earlier release) | not reopened |
| BCK-01, BCK-02 | **CLOSED** (earlier release) | verified present in the current code |
| BCK-03 | **FIXED** | `requireFinite` / `optFinite`; the org.json premise is proved in the test, not assumed |
| BCK-04 | **FIXED** | `BoundedImport`, 4 000 000 characters |
| BCK-05 | **FIXED** | `customThemeDataOrNull`: absent and unreadable are different; unreadable leaves the file alone |
| **BCK-06** | **FIXED** | `ImportStaging` + `finishPendingImport()`; kill simulated at every point |
| BCK-07 | **FIXED** | `customThemeSchemaVersion` recorded and honoured; absent means *current*, because legacy would have corrupted every existing backup |
| BCK-08 | **CLOSED / intentional** | = SEC-08, the second read is fully re-validated |
| BCK-09 | **ACCEPTED** (earlier release) | unchanged |
| SEC-01 | **FIXED** | `InstallVerdict.WrongSignature`; unreadable counts as refused |
| SEC-02, SEC-06, SEC-07 | **CLOSED / NOT_A_BUG** (earlier releases) | unchanged |
| SEC-03 | **FIXED** | three HTTP bodies bounded through the same helper as the file imports |
| SEC-04, SEC-05 | **FIXED** (v4.14) | `WeatherRequest` |
| SEC-09 | **FIXED** | `-keep prefs.**` removed; verified by running the shrunk release build on the phone and round-tripping a preference through a restart |
| CLIP-LIBRARY-WIDE | **CLOSED / intentional** | **210 of 221** sprites reach a canvas edge — the authoring convention `normalize` enforces from the other side; 11 declared exceptions whose margin is load-bearing |
| GL-GOLDEN-ADRENO | **FIXED** | interior + edge-displacement metric; OnePlus 112/112 |
| TOOL — duplicate check | **FIXED** | pixel digests + `IDENTICAL_BY_CONSTRUCTION`; per-axis membership |
| TOOL — Gradle inputs | **FIXED** | `AndroidManifest.xml` declared a test input, verified by watching a test re-run and fail |
| TOOL-PROBE-PIN | **FIXED** (v4.14) | probe declares Pillow 12.3.0, matching `requirements.txt` |
| GRADLE-PNG-INPUTS | **FIXED** (earlier release) | `drawable-nodpi` declared |
| RT-01 | **CLOSED** (earlier release) | not reopened |
| WEA-01 … WEA-09 | **FIXED** (v4.13 / v4.14) | backoff ladder, age cap, `solarDayIsStale`, preview status gate, `isAttemptDue`, INTERNET inventory |

**OPEN: none. DEFERRED: none.**

---

## 7. Verification

### JVM and build

| gate | result |
|---|---|
| `clean` + `test --rerun-tasks` | **1224 tests, 0 failures, 0 errors** |
| `lint` | **0 errors** |
| `assembleDebug` | OK |
| `assembleDebugAndroidTest` | OK |
| `assembleRelease` | OK (unsigned: no maintainer credentials used) |
| `bundleRelease` | OK |

### Instrumented

| device | result |
|---|---|
| **OnePlus 6T** (ONEPLUS A6013, Android 15, Adreno 630, 1080×2340) | **112/112, 0 failures** |
| **Pixel 9 emulator** (API 37, SwiftShader) | **112/112, 0 failures** |

### Goldens

27 goldens. **Two were regenerated during this batch** — `traffic-day` and `traffic-night` — and
neither was touched by the version bump.

The diff was analysed before regenerating: **657 pixels each, every one inside `y 649..685,
x 76..357`**, the band the cars occupy. The change is exactly the occupants' heads (the new bust
scale) and the two livery bands (the sill move). Nothing else in either frame differs. The
regenerated goldens were captured on the reference emulator — the device the other 25 come from — and
are byte-identical to the frames the app renders.

### Assets

| command | result |
|---|---|
| `probe` | `probe_sha256 = ec77e95d…`, `matches_expected: True`; Pillow 12.3.0, zlib-ng 1.3.1 |
| `inventory` | 221 files, **197 unique drawings, 24 duplicate groups** (pixel digests, not file bytes) |
| `validate` | EXIT 0 |
| `normalize` | EXIT 0 — 73 targets, no removable padding |
| `compare` | EXIT 0 — 125 PIXEL_IDENTICAL |
| Python suite | **108/108** |

### Mutation testing — the two areas changed in this pass

| area | mutation | caught |
|---|---|---|
| ARC-08 | `configChanges` removed | ✓ |
| ARC-08 | `uiMode` dropped from the list | ✓ |
| ARC-08 | state moved back into `AdvancedScreen` | ✓ |
| ARC-08 | process scope instead of `rememberCoroutineScope` | ✓ |
| BCK-06 | payload not staged | ✓ |
| BCK-06 | pending cleared before the themes write | ✓ |
| BCK-06 | `finish` does not write the themes | ✓ |
| BCK-06 | `finish` does not clear the pending key | ✓ |
| BCK-06 | `finish` acts with nothing pending | ✓ |

### Runtime on the OnePlus 6T — release configuration (R8, `flags=0x0`, not debuggable)

| | v4.14 | v4.15 |
|---|---|---|
| average | 29.87 fps | **29.889 fps** |
| dropped / janky frames | 0 / 0 | **0 / 0** |
| CPU, visible | 27.5–32.1% of 800% | 25.9–29.6% |
| CPU, screen off | 0.056% of one core | **~0.05–0.06%** — now equal to the render thread's own, so the weather loop costs nothing |
| GL threads, 3 rotations + 3 lock cycles | 1 | **1** |
| EGL errors | 0 | **0** |
| RSS | stable | stable (170.8 → 173.1 MB over the cycles) |

Backup/persistence was exercised end to end on the phone: export, restore, "Backup restored.", no
pending key left, no crash and no ANR in logcat.

### The packaged build, on the phone

The APK installed on the OnePlus 6T was built **from the clean extraction of the ZIP**, and the
binary pulled back off the device is byte-identical to it (`sha256 9a1ac084c9d687f8…`), so what was
smoke-tested is what the archive contains.

| check | result |
|---|---|
| `versionName` / `versionCode`, read with `dumpsys package` | **4.15 / 46** |
| app starts | settings screen renders, theme *Sunset*, preview drawn |
| wallpaper runs | full-screen scene live; **741 frames, 0 dropped** over 25 s (≈29.6 fps) |
| set as the real wallpaper | home screen animates — 78 004 px change over 6 s |
| people in cars | fire engine, two police cars, taxi, saloon: **every head inside the glass**, clear of the roof |
| liveries | police stripe and taxi chequer on the doors, at the sill |
| people at windows | occupants in house and tower windows, **short sleeves — no hats, no coats** |
| scene rendering | sky gradient, sun, clouds, birds, hills, trees, buildings, road, all correct |
| animation | traffic advances steadily: 15–17% of the road band changes every 5 s, three intervals running |
| crash / ANR | **none** — no `FATAL EXCEPTION`, no `ANR in`, no `AndroidRuntime`, no EGL error in logcat |

**The phone was put back as it was found**: the live wallpaper is again the maintainer's own release
package `com.paperscrape.livewallpaper` (v4.12) and is running, and the debug package was
uninstalled. The release app raised its own "Update available" dialog during the restore; it is
modal, so it was dismissed through *Remind me later → Next app launch*, the option that changes
nothing. **No update was installed and no setting of the maintainer's app was altered.**

---

## 8. Accounting

| | |
|---|---|
| tracked files, v4.14 | 760 |
| tracked files, v4.15 | **782** |
| added | **22** |
| modified | **47** |
| removed | **0** |
| unchanged | **713** |
| reconciliation | 760 + 22 − 0 = **782** ✓ |
| diff | 69 files, +4999 / −517 |
| goldens | 27 (2 regenerated, analysed) |
| sprites | 221 (0 changed) |
| ZIP entries | **783** = 782 tracked + `CLAUDE.md` |

### Excluded from the package — verified absent

`.git/`, `build/`, `app/build/`, `.gradle/`, `.idea/`, `.kotlin/`, `local.properties`, `*.apk`,
`*.aab`, release keystore, virtualenvs, caches, `__pycache__`, `staging/`, MCP/Claude artefacts,
experiment scratch files, credentials, and any reference to this machine's filesystem.

### Included

`.gitignore`, `.github/` (2 workflows), `CLAUDE.md` (per the project's convention — it is gitignored
but ships in the archive), `AI_PROJECT_RULES.md`, `debug.keystore` (the committed, publicly-known
debug key).

---

## 9. Limitations

None of these is an open finding. They are the honest edges of what was verified.

- **Live Weather was not exercised against a provider.** The test device spent most of the session in
  airplane mode. Every WEA fix is covered by deterministic tests, and the update checker did reach
  GitHub near the end of the session, so the network path works — but a successful weather fetch,
  a transient failure and a STALE transition were not observed on hardware in this pass.
- **A real process kill was not performed mid-import.** The kill is simulated at every point in
  `AtomicImportTest` and by a store that throws in `BackupRepositoryTest`; `adb kill` timed precisely
  between two DataStore writes is not something this harness can do reliably. The recovery step was
  verified against real `DataStore`s and end to end on the phone.
- **ARC-08 covers configuration changes, not process death.** A download interrupted by the process
  being killed still restarts. Carrying a partial APK across a process death would mean persisting
  and resuming it, which is more machinery than a re-downloadable, checksum-verified file is worth.
- **`assembleRelease` and `bundleRelease` produce unsigned artefacts.** Signing needs the
  maintainer's keystore, which was deliberately not used. The R8-shrunk build *was* exercised on the
  phone through a separately-signed measurement variant, which was then uninstalled.
- **No Compose UI test suite exists** (TST-03, an accepted project position). The two UI-lifetime
  properties in ARC-08 are asserted by reading the manifest and the sources, and verified on the
  device by counting Activity relaunches.
- **The local repository has no `origin`.** The tree could not be diffed against GitHub; the baseline
  comparison is against the v4.14 artefact delivered from here.
- **GL goldens remain calibrated on the reference emulator.** The new metric makes them portable to
  Adreno 630; a third GPU family has not been tried.

---

## 10. Status

**v4.15 — PREPARED, NOT PUBLISHED.**

No push, no tag, no GitHub Release, no PR, no merge, no Dependabot action. Publication is the
maintainer's.
