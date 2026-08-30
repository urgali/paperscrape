# PaperScrape v4.11 — release candidate, verification report

Companion to `V4_10_REPORT.md`, in the same place and for the same purpose: what was actually run
before this release was handed over, and what was not.

Legend: **VERIFIED** = executed in the preparing session with the output observed ·
**OBSERVED** = read directly from code or seen at runtime without a full diagnosis ·
**INFERRED** = reasoned, not executed · **NOT VERIFIED** = not done, with the reason ·
**BLOCKED** = attempted and prevented by the environment, with the reason.

## Baseline

**VERIFIED** — v4.10 (`versionCode 41`), 745 files, clean tree. `tools/assets/requirements.txt` pins
`Pillow==12.3.0`, `resvg_py==0.4.0`, `numpy==2.4.4`. Asset registry: 221 sprites. 27 golden PNGs.
Accounting against the published v4.10 package: 0 files added, 0 removed, 215 modified, 530
unchanged (215 = 132 person PNGs + 36 SVGs + 27 goldens + 20 documentation and source files).

## Device

**VERIFIED** — every instrumented figure below was produced on a real device, not inferred:

| | |
|---|---|
| Model | `sdk_gphone16k_x86_64` (emulator) |
| API / release | 37 / 17 |
| ABI | `x86_64` |
| Screen | 1080x2424, density 420 (override 460) |
| Build type | `user` (not rooted; `date` and `settings put secure` are refused) |

## What this release contains

Three corrections that change what is drawn, and a documentation pass. No architectural change; no
public signature moved.

- **REN-03-origin** — the cloud blit origin `(-128f, -85f)` centred a 768x510 px canvas that does
  not ship. `cloud_body.png` is 798x396, so the drawn cloud sat 5 units right and 19 units above the
  point the coverage kernel measures from. Both the origin and `CLOUD_CONTENT_HALF_UNITS` are now
  derived from the asset.
- **SCL-01** — five winter sprites carried artwork past the top of their own viewBox. The three
  co-registered families each grew 3 px taller as a whole and their single shared anchor grew with
  them, per `normalize.py`'s own rule that a family's members must share a canvas.
- **SCL-05** — Santa's gift target now derives from `SceneSpace.PAVEMENT_FAR/NEAR_Y_FRACTION`
  instead of restating a number the road moves had made stale.
- **SCL-03 / documentation** — declared metres corrected to match the artwork already drawn;
  README, CONTRIBUTING, ARCHITECTURE, DESIGN_NOTES, CLAUDE.md and a `build.gradle.kts` comment.

## Test execution

**VERIFIED** — run twice: once as a standalone pass, then again in full from `clean`. Both runs gave
identical counts.

| Suite | Result |
|---|---|
| `./gradlew test --rerun-tasks` (JVM) | **1086 tests, 0 failures, 0 errors, 0 skipped**, 87 classes |
| `./gradlew connectedDebugAndroidTest` | **102 tests, 0 failures, 0 errors, 0 skipped**, 16 classes |
| `./gradlew lint` | **BUILD SUCCESSFUL** — 31 issues: 28 warnings, 3 hints, 0 errors, 0 fatal |
| `./gradlew clean` | **BUILD SUCCESSFUL** |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL**, `app-debug.apk` 22 373 583 B |
| `./gradlew assembleDebugAndroidTest` | **BUILD SUCCESSFUL**, `app-debug-androidTest.apk` 1.75 MB |
| `./gradlew assembleRelease` | **BUILD SUCCESSFUL**, `app-release-unsigned.apk` 2.25 MB |
| `./gradlew bundleRelease` | **BUILD SUCCESSFUL**, `app-release.aab` 4.52 MB |

Instrumented classes actually executed, with their test counts:

`SceneGoldenTest` 16 · `PeopleGoldenTest` 8 · `TrafficGoldenTest` 6 · `GlSceneGoldenTest` 3 ·
`PrecipitationPixelTest` 9 · `ThemeCustomizationPersistenceTest` 13 · `BackupRepositoryTest` 8 ·
`VehicleOccupantScaleTest` 6 · `BeachRoadRepairTest` 5 · `TreeArtworkAlignmentTest` 5 ·
`VehicleScalePixelTest` 5 · `PeopleOcclusionTest` 4 · `PrefsCorruptionRecoveryTest` 4 ·
`BackgroundLocationManifestTest` 4 · `CanvasGradientAllocationTest` 3 · `VehicleOccupantAbCapture` 3.

## Golden

**VERIFIED** — 33 golden assertions across four classes, **33 PASS / 0 FAIL**, on the device above:

- `SceneGoldenTest` (16): day, dusk, night, overcast, rain, snow, thunderstorm, lakeEmpty,
  lakeBoats, lakeBusy, lakeDolphinLeap, themeCity, themeDesert, themeWinter, trafficDay,
  trafficNight
- `PeopleGoldenTest` (8): people-single, people-group, people-mixed, people-overlap, people-window,
  people-skin, people-commercial, people-skyscraper
- `TrafficGoldenTest` (6) and `GlSceneGoldenTest` (3): day, lakeBusy, thunderstorm

The 27 golden PNGs were regenerated in commit `ea8afbd` for the cloud blit origin. **The regenerated
set was proved to belong to the cloud alone**: with `CLOUD_BLIT_X/Y` reverted and the SCL-01 sprite
work left in place, the suite passes against the *v4.10* golden images unchanged. Every pixel that
moved in this release moved because of the cloud.

## SCL-01 group coherence

**VERIFIED** — read from the shipped files, not from the declarations:

| Family | Members | Canvas (PNG) | Canvas (registry) | SVG viewBox | Anchor x grid |
|---|---|---|---|---|---|
| `person_walk` | 96 (72 skin) | 123x255 | 123x255 | 41x85 | 85 x 3 = **255** |
| `person_head_window` | 32 (24 skin) | 159x171 | 159x171 | 53x57 | 57 x 3 = **171** |
| `person_head_car` | 4 | 120x147 | 120x147 | 40x49 | 49 x 3 = **147** |

One canvas per family in every representation, and each family's single anchor equals its canvas
height exactly. No per-sprite constant was introduced; no lateral geometry was touched.

## Asset tooling

**VERIFIED** — pinned environment (`resvg_py==0.4.0`, `Pillow==12.3.0`, `numpy==2.4.4`):

| Command | Result |
|---|---|
| `validate` | `registry OK: 221 entries, 125 with an SVG source, 96 recorded as gaps`; `anchors: 221 determined, 0 undetermined`; **0 failures** |
| `normalize` (check) | `normalisation OK: 73 targets checked, none carries removable padding` |
| `inventory` | `221 files, 221 unique, 27.09 MB decoded, 4.51 MB padding, 0 duplicate groups` |
| `render` + `compare` | `PIXEL_IDENTICAL: 125` of 125 SVG-sourced sprites |
| `python3 -m unittest discover -s tests` | 103 tests, **1 failure** — see below |

**The one asset-suite failure is pre-existing and environmental.**
`test_toolchain_matches_the_pinned_fingerprint` reports that the rasteriser fingerprint has moved.
It is isolated to the native `resvg` build on this machine, not to Pillow: the fingerprint is
identical under Pillow 12.1.1 and 12.3.0. It fails identically on the v4.10 baseline, predates every
change in this release, and **the pinned fingerprint was deliberately not edited to make it pass**.
The check it guards — that renders still match the shipped bytes — is independently satisfied by
`compare` reporting PIXEL_IDENTICAL for all 125 sprites.

## Runtime scenarios on the device

**VERIFIED** — driven through the real UI and read back from the app's own DataStore.

### BCK-01 — per-theme customisation and per-theme reset

| Step | Sunset | Winter |
|---|---|---|
| Night density set on Sunset | **33%** | — |
| Switch to Winter | — | **100%** (default) |
| Switch back to Sunset | **33%** | — |
| Night density set on Winter | — | **68%** |
| "Reset this theme's scene to defaults" on Sunset | **100%** | — |
| Re-check Winter | — | **68%** (untouched) |

The customisation is stored against `pending_customization_theme_id`, which tracked the theme it was
made on throughout. Resetting one theme's scene left the other theme's customisation intact.

### BCK-02 — `PEOPLE_NIGHT_DENSITY` neither inherited nor resurrected

The same table above is the proof for the specific key: `people_night_density` set to 33% on Sunset
reads back as the 100% default on Winter, and does not reappear on Sunset after that theme's own
reset.

### WEA-02 — the settings screen and the renderer agree

A/B across three published states, reading `enabled` off the real Compose controls:

| `live_weather_status` | Banner | Show Clouds switch | Density slider |
|---|---|---|---|
| `ok` | "Real conditions are driving this scene's clouds and precipitation, so their screens are read-only." | `enabled=false` | `enabled=false` |
| `no_location` | none | `enabled=true` | `enabled=true` |
| `failed` | "Open-Meteo could not be reached, and there are no earlier conditions to fall back on…" | `enabled=true` | `enabled=true` |

`missing_api_key` was also observed, selecting OpenWeather with no key: the UI reported it as a
missing key rather than as a failure, and stated that no requests are being made.

### WEA-01 — a transient failure earns an early retry

**VERIFIED, timed.** With a snapshot in effect and the network taken down (airplane mode), a forced
fetch failed at **15:33:38** and the status became `stale`. The network was restored at 15:33:59 and
the status returned to `ok` at **15:35:39** — **2 min 01 s after the failure**, which is
`LiveWeatherSchedule.RETRY_BASE_MILLIS` for one consecutive transient failure. Under the normal
hourly cadence the next attempt would not have been made until roughly 16:33.

### WEA-06 — `STALE` vs `FAILED`

Two of the three propositions were verified at runtime; the third was blocked.

- **VERIFIED** — a failed fetch *with* a usable snapshot publishes `stale`, and the scene keeps
  drawing the last known-good conditions.
- **VERIFIED** — a failed fetch *without* a snapshot publishes `failed`, not `stale`, and the scene
  returns to the theme's own weather. Produced by switching Live Weather off (which clears
  `lastWeatherSnapshot`) and back on while offline.
- **BLOCKED** — the transition from `stale` to `failed` once the snapshot passes
  `SNAPSHOT_MAX_AGE_MILLIS` (three refresh intervals = 3 h). The snapshot is an in-memory field of
  the engine stamped with `System.currentTimeMillis()`, so ageing it requires moving the device
  clock; the emulator is a `user` build without root and refuses (`date: cannot set date: Operation
  not permitted`). **The project was not modified to make this observable.** The rule itself is a
  pure function (`LiveWeatherSchedule.snapshotIsUsable` / `decide`) and is covered by the JVM suite.

### GL lifecycle

**VERIFIED** — the wallpaper was set live on home and lock screen, then exercised:

| Scenario | Result |
|---|---|
| Preview engine alongside the live engine | Two `PaperEngine` instances coexisted; the preview one received `onVisibilityChanged(false)`; no error |
| Install over the active wallpaper (`installDebug`) | Wallpaper component unchanged afterwards, engine rebound, no crash |
| 5 preview open/close cycles | Clean |
| Screen off/on x3, home/recents transitions | 8 x `onVisibilityChanged(true)` / 8 x `onVisibilityChanged(false)`, balanced |
| logcat | No `FATAL EXCEPTION`, no ANR, **no EGL error attributable to the app**. The `GFXSTREAM … EGL_BAD_ATTRIBUTE` lines present in the log belong to pid 638, `system_server`, not to the app's pid. |

### What the scene looks like

**OBSERVED** — full-screen captures of the live GL wallpaper:

- Pedestrians whole, feet on the ground line, no clipping and no lateral shift.
- Winter hats complete: the recovered pompoms render as full circles at wallpaper scale.
- Occupants visible in house windows; drivers visible in car windows.
- No skin-variant regression across the 72 walker and 24 window-head variants (all pass
  `people-skin` and share the family canvas).

## Mutation testing

**VERIFIED — newly executed for this release**, not carried over:

| Mutation | Result |
|---|---|
| `PERSON_ANCHOR_Y_UNITS` -85 → -84 | `SpriteGeometryTest` **FAILS**: "person_walk: the shared anchor is 84.0 units, which is 252.0 px, but the family's canvas is 255 px tall." |
| `WINDOW_HEAD_ANCHOR_Y_UNITS` 57 → 54 | `SpriteGeometryTest` **FAILS**: "person_head_window: the shared anchor is 54.0 units, which is 162.0 px, but the family's canvas is 171 px tall." |

Both were reverted and the tree confirmed byte-identical afterwards. The new test
`each co-registered family shares one canvas, and its anchor is that canvas` was confirmed present
in the executed test list of the JVM run, not merely present in the source.

## Decisions carried forward unchanged

**VERIFIED** by inspection, with no code touched:

- **SCL-06** — `PENGUIN(1.1717f, 49f)`, untouched since the v4.10 baseline commit.
- **REN-01** — remains WONTFIX by the maintainer's decision; the blit convention is unchanged.
- **SCL-03** — `TREE(9.479f, 118f)` and `FIR(9.8f, 122f)`, matching the recorded decision that one
  metre governs both.
- **SCL-01 lateral** — the 15 opaque pixels in a 2 px sliver on the two winter `head_car` remain
  classified, not fixed: it is the outline stroke's own half-width clipped where artwork meets the
  frame, shared with `car_body` (104 px) and roughly twenty other sprites. Widening for it would
  open a library-wide change nobody asked for. Recorded beside the constants.

## Not verified

- **NOT VERIFIED** — SCL-05 at runtime. Santa's sleigh is a seasonal, time-gated effect; no pass was
  observed during the session. The derivation is covered by JVM tests and by inspection.
- **BLOCKED** — the `STALE` → `FAILED` age-cap transition, for the reason given above.
- **NOT VERIFIED** — behaviour on physical hardware, on any API level other than 37, and on any
  screen geometry other than 1080x2424.
