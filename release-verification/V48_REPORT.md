# PaperScrape v4.8 — release candidate, verification report

Companion to `V47_REPORT.md`, in the same place and for the same purpose: what was actually run
before this release was handed over, and what was not.

Legend: **VERIFIED** = executed in the preparing session with the output observed ·
**OBSERVED** = read directly from code or seen at runtime without a full diagnosis ·
**INFERRED** = reasoned, not executed · **NOT VERIFIED** = not done, with the reason.

## Baseline

**VERIFIED** — v4.7 (`versionCode 38`) plus the post-v4.7 sleigh-crop batch, 736 files, clean tree.
`tools/assets/requirements.txt` pins `Pillow==12.3.0`, `numpy==2.4.4`, `resvg_py==0.4.0`.

## What this release contains

Two defects in the per-theme customization scratch model, and nothing else. Five files carry the
fixes and their tests; three more carry the version bump and the release paperwork.

- **BCK-01** — `resetCategory` took no `forThemeId` and did not call `ensureFreshPendingTheme`, so
  it removed the flat scratch keys of whichever theme was last *edited* rather than the one on
  screen. It now takes the theme and uses the same shape as every other per-theme mutator.
- **BCK-02** — `people_night_density` was missing from `clearAllThemeCustomizationKeys`, the single
  wipe shared by a theme switch, "reset everything" and a backup restore, so it survived all three.

## Build and tests

| Task | Result |
|---|---|
| `clean` | SUCCESSFUL — **VERIFIED** |
| `test --rerun-tasks` | **85 classes, 1046 tests, 0 failures, 0 errors, 0 skipped** — **VERIFIED** |
| `lint` | 31 issues: 28 Warning, 3 Hint, **0 Error**; **0 issues in any file this release changes** — **VERIFIED** |
| `assembleDebug` | SUCCESSFUL, APK reports `versionCode 39` / `versionName 4.8` — **VERIFIED** |
| `assembleDebugAndroidTest` | SUCCESSFUL — **VERIFIED** |
| `assembleRelease` (R8 + resource shrinking) | SUCCESSFUL, unsigned (no signing environment) — **VERIFIED** |

## Instrumented tests — NOT VERIFIED

`connectedDebugAndroidTest` was attempted twice, filtered and unfiltered. Both fail with
`com.android.builder.testing.api.DeviceException: No connected devices!`. The environment preparing
this release has no reachable device; nothing in the project was changed to work around it.

The four new tests compile and are present in the test APK's dex. Run them with:

```
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperscrape.livewallpaper.prefs.ThemeCustomizationPersistenceTest
```

Note for whoever runs them: `connectedDebugAndroidTest` does **not** accept `--tests` under AGP 9;
the filter goes through the runner argument above.

## Runtime — emulator, Android 17 (API 37), 1080x2424, density 420

**VERIFIED**, against the v4.8 debug build, app data cleared first. The values quoted are the
customization the app resolves and persists (`settingsFlow` → `resolveActiveCustomization`), read
from the accessibility tree, not an impression of the picture.

### BCK-01 — a reset lands on the theme on screen, and only there

Two categories were customised per theme so that "no other category moved" is observable.

| Step | Winter (on screen at the reset) | Christmas (last edited, holds the tag) |
|---|---|---|
| customised | Houses 21%, Buildings 30% | Houses 40%, Buildings 54% |
| back on Winter, "Reset Houses to default" | Houses **65%** (reset) · Buildings **30%** (untouched) | Houses **40%** · Buildings **54%** (untouched) |
| after a process restart | Houses 65% · Buildings 30% | — |
| after switching themes and back | — | Houses 40% · Buildings 54% |

### BCK-02 — the night pedestrian density stays with its theme

| Step | Result |
|---|---|
| Night density 20% on Christmas | set |
| switch to Desert (never customised), one unrelated edit | Desert reads **100%**, its own default |
| set 32% on Desert, then "Reset this theme's scene to defaults" | — |
| one further edit (the point where a surviving key would resurface) | **100%** |
| after a process restart | **100%** |

## Regression

**VERIFIED** by diffing the whole tree against the v4.7 baseline:

- **0** golden images changed, **0** images of any kind changed anywhere.
- **0** files changed under `engine/`, `weather/`, `location/`, `tools/` or `res/` — no rendering,
  weather, GL or asset code is part of this release.
- **0** new DataStore keys, **0** schema constants touched, **0** manifest changes. Backups written
  by earlier versions import unchanged.
- Asset registry re-measured: **221 shipped PNGs ↔ 221 registry entries**, schema 4, no size
  mismatch, no orphan either way.
- Secret scan over the whole tree: clean. No APK/AAB, no `local.properties`, no release key. The
  only keystore is `debug.keystore`, byte-identical to the baseline and committed on purpose.

## Screenshots — NOT VERIFIED

No screenshots were captured to file for this release. The preparing environment drives the
emulator through a tooling bridge that returns images to the session rather than to disk, and has
no adb access to run `screencap`. Rather than ship invented or unrelated files, this report records
the measured values above; `screenshots/` therefore still holds only the v4.7 set.

## Outstanding

Publication is the maintainer's: no tag, no push, no GitHub Release was made
(`AI_PROJECT_RULES.md` §10.A / §11.D). The instrumented suite above is the one piece of verification
still open.
