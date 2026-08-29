# PaperScrape v4.10 — release candidate, verification report

Companion to `V48_REPORT.md`, in the same place and for the same purpose: what was actually run
before this release was handed over, and what was not.

Legend: **VERIFIED** = executed in the preparing session with the output observed ·
**OBSERVED** = read directly from code or seen at runtime without a full diagnosis ·
**INFERRED** = reasoned, not executed · **NOT VERIFIED** = not done, with the reason.

## Baseline

**VERIFIED** — v4.9 (`versionCode 40`), 741 files, clean tree. `tools/assets/requirements.txt` pins
`Pillow==12.3.0`. Asset registry: 221 sprites, schema 4. 27 golden PNGs.

## What this release contains

Five defects in the GL lifecycle and nothing else. Four were found and fixed in the GL lifecycle
batch; the fifth was found by the review that closed it, in the code that batch had just changed.
Four files carry the fixes and their tests; four more carry the version bump and the release
paperwork, plus one local instructions file corrected because it stated the opposite of the
architecture.

- **ARC-01** — `onSurfaceDestroyed` never called `shutdown()`, so a destroy/create cycle inside one
  engine abandoned a live `GlRenderThread` holding its EGL context, its `GlSceneTarget` and every
  uploaded texture, while a second thread published to the same engine. One thread per engine now,
  with replacement surfaces handed to it and the `PaperRenderer` reused.
- **ARC-05** — every `prepareFrame` failure latched the engine into the Canvas fallback for life.
  A context that has already drawn a frame is now rebuilt instead, bounded at three attempts.
- **ARC-06** — `trimTextures` ran as a queued event, drained at a point in the loop where no context
  is current, so it freed nothing while still losing the handles. It is now a flag consumed only
  after `prepareFrame`. Separately, the `Boolean` from `registerWhitePixel()` was discarded, leaving
  a target that believed it was usable with no white pixel and drew flat fills black.
- **ARC-07** — `onSurfaceDestroyed` did not remove the Canvas fallback's self-rescheduling frame
  callback, which kept calling `lockCanvas` on a destroyed surface at frame cadence.
- **EGL surface ownership** (found in review) — `ensureEglSurface` asked only whether a surface
  existed, never which window it belonged to. Safe while a thread saw one window; wrong once the
  thread outlived surfaces. The loop's surface-gone branch was the only thing releasing it on a
  swap, and it is skipped whenever the replacement arrives before the render thread looks — the
  normal case, since the engine delivers destroy and create back to back while the thread is
  mid-frame or parked.

## Claude-side verification

| Check | Result |
|---|---|
| `clean` | **VERIFIED** — BUILD SUCCESSFUL |
| `test --rerun-tasks` | **VERIFIED** — BUILD SUCCESSFUL, **87 classes, 1085 tests, 0 failures, 0 errors, 0 skipped**, 26/26 tasks executed |
| `lint` | **VERIFIED** — BUILD SUCCESSFUL, 31 issues (28 Warning, 3 Hint), **0 Error**, **0 on the changed files** |
| `assembleDebug` | **VERIFIED** — BUILD SUCCESSFUL |
| `assembleDebugAndroidTest` | **VERIFIED** — BUILD SUCCESSFUL |
| `assembleRelease` | **VERIFIED** — BUILD SUCCESSFUL |
| Clean-extraction rebuild | **VERIFIED** — the delivered archive extracted into an empty directory and built there, not in the working tree |
| Mutation testing | **VERIFIED** — four mutations, all caught (4 / 1 / 2 / 3 failing tests); sources restored byte-identically |
| Golden images | **VERIFIED** — 27 PNGs, none modified |
| Scene rendering code | **VERIFIED** — no change to `SceneObjectRenderer`, `SceneSpace`, `PaperRenderer`, the scenes or the weather renderers. `GlSceneTarget` *is* rendering code and did change, but only in `trimTextures`, which frees GPU resources and draws nothing; no drawing operation was altered. |
| Assets | **VERIFIED** — no file under `app/src/main/res` or `tools/` modified |
| Persistence | **VERIFIED** — 88 DataStore keys before and after; `CUSTOM_THEME_SCHEMA_VERSION` still 3 |
| Allocation audit | **VERIFIED** — `javap -c` on the release class shows no allocation in `loop()` or `drawFrame()` |
| Concurrency | **VERIFIED** — no `synchronized` added; the only new cross-thread state is one `@Volatile Boolean` |

### Runtime, on an emulator

**VERIFIED** on a Pixel 9 AVD, Android 17 (API 37), x86_64, running the APK built from the delivered
archive: the wallpaper starts and animates, visibility off/on works without recreating the surface,
the settings screen and the wallpaper picker preview both render, and no exception from this package
appears in the log.

### Not done

- **Instrumented tests — NOT VERIFIED.** `connectedDebugAndroidTest` fails with
  `DeviceException: No connected devices!`. The preparing environment has no adb-reachable device;
  this is unchanged from v4.8 and v4.9 and is not caused by the project. The instrumented APK does
  build (`assembleDebugAndroidTest` is green).
- **A surface destroy/create cycle within a single engine — NOT VERIFIED on a device.** Rotation,
  lock/unlock and display-size changes do not provoke it on this emulator, and every entry into the
  wallpaper picker builds a *new* engine. The lifecycle properties are asserted by state-machine
  unit tests driven for 1/5/20/100 cycles instead.
- **A GPU context loss — NOT VERIFIED.** It cannot be provoked on the emulator, and simulating it by
  editing the code would be verifying a fix against a mutation of the product.
- **The Canvas fallback — NOT VERIFIED at runtime.** EGL works on this emulator, so the fallback is
  unreachable without breaking EGL deliberately.
- **New screenshots — NOT PRODUCED.** The available screenshot mechanism returns images to the
  session rather than writing files, so no new capture could be saved. The existing screenshots
  under `release-verification/screenshots/` are unchanged and still belong to their original
  releases; none has been relabelled as v4.10.
- **CPU/battery measurement — NOT VERIFIED.** No honest measurement is possible on an emulator. The
  reasoning is that this release removes work rather than adding it: a duplicated render loop, a
  frame callback drawing into a dead surface, and repeated texture re-uploads all go away.

## Maintainer-side verification

Not done here, and not claimed: a local release build, installing on a physical device, a visual
check, and practical CPU/battery observation over time. The last of these is the one this release
most deserves, since every fix in it is about work the wallpaper was doing and should not have been.

## Publication

Outstanding, and the maintainer's. No tag, no push, no GitHub Release.
