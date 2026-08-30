# PaperScrape v4.13 — release candidate, verification report

Companion to `V4_12_REPORT.md`. Legend: **VERIFIED** = executed here, output observed ·
**OBSERVED** = seen on a device · **NOT VERIFIED** = not done, with the reason.

## Baseline

**VERIFIED** — v4.12 (`versionCode 43`), clean tree, 752 files, 27 goldens, 221 sprites, every prior
BCK/WEA/ARC/SCL/REN fix and both v4.12 features present. Tree digest `f96249f9…`.

## Devices

**VERIFIED** — for the first time both were available, and the difference between them turned out
to matter:

| | |
|---|---|
| Physical phone | **OnePlus 6T (ONEPLUS A6013)**, Android 15, 1080×2340, `user` build, GPU **Adreno 630** |
| Reference emulator | Pixel_9 AVD, API 37, 1080×2424, `swiftshader_indirect` — the driver the GL goldens were captured on |

## Test execution

| Suite | Result |
|---|---|
| `./gradlew test --rerun-tasks` | **1114 tests, 0 failures** (v4.12: 1108) |
| `connectedDebugAndroidTest`, **emulator** | **102 tests, 0 failures**, 33/33 golden |
| `connectedDebugAndroidTest`, **phone** | **99 / 102**, the 3 `GlSceneGoldenTest` cases differ — see Goldens |
| `./gradlew lint` | 0 errors |
| `assembleDebug` / `assembleDebugAndroidTest` / `assembleRelease` / `bundleRelease` | all **BUILD SUCCESSFUL** |
| Asset tooling | **105 tests, 0 failures** — green for the first time in months |
| `validate` / `normalize` / `compare` / `inventory` | registry OK 221/221, normalisation OK, `PIXEL_IDENTICAL: 125`, 27.09 MB |

### Mutation testing

| Mutation | Result |
|---|---|
| `NIGHT_LIGHTNESS_FACTOR` 0.50 → 0.635 (back to v4.12) | **FAILS** (3 tests) |
| `NIGHT_BLUE_SHIFT` 6 → 0 | **FAILS** (2 tests) |
| gamut mapping replaced by channel clipping | **FAILS** (2 tests) |

All reverted; the tree was confirmed afterwards.

## AutoColorMode — the transform, and why it was rebuilt

**VERIFIED by measurement.** v4.12 fitted its factors to the 41 authored day/night pairs. Stratified
by daytime lightness those pairs do not describe one rule — the sky goes to 0.124 of its `L*`, snow
to 0.868 — because they are per-object artistic decisions. The median left white at `#A2A2A2`.

The rule now works in CIELAB: hue held, `L*` ×0.50, chroma ×0.80, `b*` −6 scaled by lightness so
black stays black, with gamut mapping instead of channel clipping.

**OBSERVED on the phone**, both reported cases:

| case | day | v4.12 night | v4.13 night |
|---|---|---|---|
| Christmas hills (snow), `FROM_DAY` | `#F3F7FB` `L*` 97.4 | `L*` 65.9 | **`#6C7480` `L*` 48.6** |
| bright red houses, `FROM_DAY` | `#E53935` `L*` 51.7 | `#8C2A27` `L*` 32.7 | **`#810013` `L*` 25.9** |

Both were read off the rendered wallpaper, not only the swatch. `FROM_NIGHT` was exercised on the
emulator in the v4.12 pass and is covered by the reciprocity test (round trip within 2.5 `L*` across
the whole spread).

**NOT VERIFIED on the phone this pass**: reset, and backup export/import through the file picker.
Both are covered by the JVM suite and by the emulator pass; the mode reads as `MANUAL` on any absent
or unrecognised value, which is v4.12's behaviour.

## Goldens — every one run, on both devices

**VERIFIED and attributed.** On the reference emulator: **33/33 PASS**, no golden changed by this
batch, which is the expected result — `MANUAL` is the default so the scene is untouched.

On the phone, 30/33 pass and three fail:

| golden | differs by | limit | verdict |
|---|---|---|---|
| `gl-day` | 1.108% at ≥16 | 0.500% | **driver difference** |
| `gl-lake-busy` | 1.290% | 0.500% | **driver difference** |
| `gl-thunderstorm` | 1.682% | 0.500% | **driver difference** |

Attributed rather than assumed: the same build scores 102/102 on the emulator the images were
captured on, and all 30 **Canvas** goldens — same scenes, same scene-composition code — pass on the
phone. If the batch had changed the scene, the Canvas ones would have failed too. So this is neither
a regression, nor a stale golden, nor non-determinism: it is an **environment difference** between
`swiftshader_indirect` and Adreno 630, newly visible because a physical phone was available.

**Nothing was regenerated and no tolerance was touched.** The GL goldens are a regression check
against one reference driver; regenerating them from a phone would break them on the emulator, and
raising the limit would trade a real check for a green tick.

## Rasteriser / toolchain — closed

**VERIFIED, with the cause demonstrated.** The fingerprint hashed the compressed PNG, so it depended
on the zlib build inside the Pillow wheel — this machine's is `1.3.1.zlib-ng`, the recording
machine's was stock zlib. Rendering the probe and pulling the PNG apart: the decompressed IDAT is
16 448 bytes hashing to `01d4b1d3…`; recompressing those exact bytes with CPython's zlib gives
`c43a0846…` against the `6dfe20c8…` Pillow wrote.

The probe now hashes the pixels and reports `probe_png_sha256` and the zlib build alongside. The
recorded value is licensed by `compare`: all 125 SVG-sourced sprites are pixel-identical, which is
stronger evidence of "same rasteriser" than one synthetic document. Two new tests pin that the
fingerprint is the pixels and that recompressing the same pixels does not move it.
`rasterizer-probe.json` now also records `pillow 12.3.0`, matching `requirements.txt` — it had said
12.1.1. Closes **TOOL-PROBE-PIN** and **TOOL-PROBE-STRICT**.

## Performance — measured on the phone, nothing changed

**VERIFIED by measurement.**

| condition | CPU (of 800%) | note |
|---|---|---|
| wallpaper **hidden** | **0.0%** | idles correctly: no polling, no timers, no wakeups |
| visible, **release** build v4.12 | **~28%** | GL thread ~26%, RSS ~170 MB |
| visible, **debug** build | ~120% | `top -H`: ~48% JIT thread pool, rest a slower interpreted render path |

The debug figure is an artefact of the build type. Nothing measured badly enough to justify a
change, so nothing was optimised. AutoColorMode costs nothing measurable: it resolves once per
settings emission at the choke point, never in the draw loop.

**NOT VERIFIED**: battery drain over hours, `gfxinfo` frame pacing (the GL wallpaper renders on its
own thread and does not report there), and any device other than this one.

## Audit findings re-checked

| finding | verdict |
|---|---|
| `TOOL-PROBE-PIN` | **CLOSED** — probe records `pillow 12.3.0` |
| `TOOL-PROBE-STRICT` | **CLOSED** — cause found and fixed, suite green |
| `GRADLE-PNG-INPUTS` | **CLOSED** — artwork declared as a test input, verified by a one-pixel change |
| `DOC-CLAUDE-ASSETS` | closed — `CLAUDE.md` says "125 of the 221" |
| `DOC-GL` | closed — the only "No OpenGL" left is the historical correction note |
| `DOC-INV` | closed — `ARCHITECTURE` shows `798×396` current against `876×477` in the "was" column |
| `DOC-DESIGN-TABLE` | closed in v4.11, re-checked |
| `CLIP-LIBRARY-WIDE` | **intentional** — the outline stroke's own half-width, library-wide |
| `ARC-05-res` | **intentional** — a per-engine rebuild budget, deliberately not reset |
