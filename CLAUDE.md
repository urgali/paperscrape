# CLAUDE.md

**LOCAL FILE — NOT VERSIONED.** Listed in `.gitignore`, never committed, never referenced
from a tracked file. It *is* deliberately included in the delivery ZIP (`AI_PROJECT_RULES.md`
10.3 / 12.16) so these instructions survive into the next session. If it ever appears in
`git status` as tracked, that is a defect to fix immediately.

Environment, commands and session mechanics. The permanent rules are `AI_PROJECT_RULES.md`;
where the two appear to differ, that file governs.

---

## 1. What to read before working

**Every session, in this order.** Measured 2026-09-12 at **16 431 words**; recount rather than
trust that with `wc -w CLAUDE.md ROADMAP.md AI_PROJECT_RULES.md README.md`.

1. **`ROADMAP.md`** — the authoritative operational plan: current state, what is known
   broken, what is next. Never decide what to work on from conversation memory.
2. **`AI_PROJECT_RULES.md`** — the permanent rules.
3. **`README.md`** — what the app is, for a reader who has never seen it.
4. **this file** — where things are and what breaks.

**Two conditional reads. They are obligations, not suggestions, and the condition is what
activates them:**

- **Before changing Kotlin, resources or the build: read `ARCHITECTURE.md`** — §3 is the
  authority on the two rendering backends, and nothing about the draw path should be
  inferred without it.
- **Before changing anything that is drawn or seen: read `DESIGN_NOTES.md`** — the visual
  system, the protected elements, and the approved decisions (`AI_PROJECT_RULES.md` §13
  requires a mockup first).

**`RELEASE_HISTORY.md` is a targeted consultation, not opening reading.** It is ~88 000
words. Search it for the release or defect you actually need; do not read it through.

**Documents archived out of the root live in `docs/archive/`** — see
[`docs/archive/README.md`](docs/archive/README.md). Source comments and older documents cite
those files by bare name, which still resolves: the index says where each one went.

---

## 2. The session contract

**You never publish. Not once, not "because the batch said so"** (`AI_PROJECT_RULES.md`
§10.A, §11.D). Never run: `git push` in any form to any remote including tags;
`gh release create/edit/upload`; `gh api` with `-X POST/PATCH/PUT/DELETE`;
`git remote set-url` to make a push work. Never use the maintainer's credentials —
`~/.ssh/id_rsa` authenticates as `urgali` and *will* let a push through, which is exactly
why it must not be touched, nor a `git@github.com:` URL, nor a token, nor `gh auth`.
`origin` is HTTPS with no credentials, so `git push origin main` fails with
`could not read Username`. **That failure is the system working.** Read-only Git — `status`,
`log`, `diff`, `show`, `tag --list`, a local commit as a checkpoint — and unauthenticated
`curl` against the public GitHub API are fine.

**What you deliver instead is a verified ZIP** (`AI_PROJECT_RULES.md` 12.F, verification
order in 12.18). A batch ends with the archive handed over and a report saying publication
is outstanding. Never write "released", "shipped" or "published".

**Pick the verification level first** (12.B) and state it: **1** documentation only, no
Gradle; **2** (default) Kotlin/resources/assets, run `test` and `lintDebug`; **3**
build config, manifest, CI, asset pipeline or release candidate, adds `assembleDebug` and
a clean-extraction rebuild. When `assembleDebug` is skipped, write verbatim:
`assembleDebug intentionally skipped under normal verification policy.`

**There is no external product to compare against.** v3.0 removed every dependency on the
one there used to be. If a question cannot be answered from this repository, Android's
documentation, or watching the app run, ask the maintainer. `CHANGELOG.md` and the pre-v2.0
notes still allude to it and that is deliberate history (`AI_PROJECT_RULES.md` §3) — do not
"clean" them.

---

## 3. The environment — verified 2026-09-07 on this machine

Not a sandbox and not ephemeral. Everything below was checked in the session that wrote it.

| | |
|---|---|
| JDK 17 | `JAVA_HOME=/home/bober/.local/jvm/jdk-17.0.20.1+1` — `javac 17.0.20.1`, already on `PATH` |
| Android SDK | `ANDROID_HOME=ANDROID_SDK_ROOT=/home/bober/Android/Sdk` — `platforms/android-37.0`, `build-tools/{36.0.0,37.0.0}`, `platform-tools`, `cmdline-tools/latest` |
| Asset tooling | venv `/home/bober/.venvs/paperscrape-assets`, **Python 3.14.7**, Pillow 12.3.0, numpy 2.4.4, resvg_py 0.4.0 — pinned in `tools/assets/requirements.txt`. The system `python3` is the same 3.14.7 but **has no Pillow**: the tooling runs in the venv or not at all |
| Device | **Blackview BV6600** over USB (`adb devices` → `BV6600EEA0007574`). MediaTek Helio A25, eight Cortex-A53, **PowerVR GE8320**, 720×1440 @ density 320, **Android 10 / SDK 29**. `minSdk = 26`, so the app installs |
| Emulator | **No `emulator` package, no system-image, no AVD.** `/dev/kvm` *is* present — virtualisation is not the obstacle, the missing packages are. Verify with `adb devices` before claiming either way (12.1) |
| Machine | 4 CPU, ~5.8 GB RAM, ~16 GB free |
| `local.properties` | absent and gitignored; write `sdk.dir=$ANDROID_HOME` into it before a Gradle run |

The **OnePlus 6T that every older measurement was taken on is gone and is not coming back**,
so no A/B against it is possible: where something differs, say it is not attributable rather
than picking an explanation. Its numbers are in `docs/archive/V4_22_MISURA_CPU_REPORT.md`
and `docs/archive/V4_22_AUDIT_SPRECO_GL_REPORT.md`.

---

## 4. Commands that work here

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew --no-daemon testDebugUnitTest        # what CI's `test` resolves to
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug            # Level 3 only
./gradlew --no-daemon assemblePerf             # the release-like build every CPU number is taken on
```

`perf` runs R8, and **R8 gets OOM-killed here when the machine is busy** (§7). Build it alone.

Asset tooling — **from `tools/assets`, with the venv's interpreter**. There is no
`paperscrape-assets` console script installed; it is a module:

```bash
cd tools/assets
/home/bober/.venvs/paperscrape-assets/bin/python -m paperscrape_assets probe     # run first, always
/home/bober/.venvs/paperscrape-assets/bin/python -m paperscrape_assets validate
/home/bober/.venvs/paperscrape-assets/bin/python -m unittest discover -s tests
```

A `probe` fingerprint mismatch invalidates every fidelity figure under `reports/` —
re-measure rather than trust them. Gradle never invokes this tooling, and `render` refuses
to write into `res/drawable-nodpi/`.

**Regenerating the people is two scripts, in this order** (v4.30). The first writes the artwork
*and* `engine/PeopleLayerTable.kt`; the second rewrites the person half of the registry from what
the first produced. Running one without the other leaves the engine's table and the artwork saying
different things, which is the duplication both scripts exist to remove:

```bash
/home/bober/.venvs/paperscrape-assets/bin/python tools/generate_people_layers.py
/home/bober/.venvs/paperscrape-assets/bin/python tools/update_people_registry.py
```

The drawing itself is `tools/assets/concepts/people/build_people_concepts.py` (style
`rilievo_occhi`), which is also where a region is declared — by **piece**, through the wobble seed
`cut` already writes. Change the drawing there, re-run that script, copy the changed
`rilievo_occhi/svg/*.png` over the shipped bases and the matching `.svg` over
`tools/assets/sources/svg/`, then run the two above. `build_carry_sprites.py` checks on every run
that it still reproduces the shipped man byte for byte; if that stops passing, stop.

**The asset tooling's own suite is 121 tests and all 121 pass** (v5.0; it was 109 at v4.31). It was 107 of 109 from
v4.26 to v4.30: `test_normalize`'s two padding checks went red when the lake was redrawn and
nothing in the release checklist ran them, so they stayed red for four releases. `BACKLOG_v4_31.md`
item 106 closed them — the three lake sprites are cropped with their origins compensated — and
`AI_PROJECT_RULES.md` 12.14 now has a line for this suite so it cannot go unrun again. **A failure
here is a real one; there is no longer an inherited pair to look past.**

Run it whenever you touch `app/src/main/res/`, `tools/assets/` or `tools/*.py`, and put the count
in the 12.14 report.

Device work:

```bash
adb devices -l
adb shell am instrument -w -r -e updateGoldens true -e class <classes> \
  com.paperscrape.livewallpaper.debug.test/androidx.test.runner.AndroidJUnitRunner
adb shell run-as com.paperscrape.livewallpaper.debug cat files/...    # debug builds only
```

**Filter the intermediate rounds; run the whole suite once, at the end.** The full instrumented
suite is **62 minutes** on this device (measured 2026-09-13: `Time: 3 742,404` s, 171 tests; it was
~40 before the neighbourhood redraw) and a golden pass needs four or five rounds through it —
attribution, regeneration, re-verification, one per mutation. Every one of those is `am instrument`
with `-e class` on the classes actually touched: two tests is under a minute, the five Canvas golden
classes about seven. The whole suite still runs once before delivery and its number is what goes in
the 12.14 template. Widen the filter the moment you realise a class you left out is involved — the
thing to avoid is calling something green that was never executed, not the minutes.

**Measure on the host; confirm on the device.** Anything that is arithmetic over the theme's own
numbers — colour contrast across themes, hours and weathers — is a JVM unit test that runs in
seconds for every combination. The device is for two things: confirming that the chosen remedy reads
in the worst case the host found, and taking the captures.

**`adb install -r` silently refuses a downgrade, so "I rendered the previous release" is a claim
about which APK is installed — check it.** A release round bumps `versionCode` *before* the
attribution pass that wants the old APK, so the install of the older build fails and, if its output
was redirected, fails invisibly; every frame then comes from the build you were trying to compare
against. This cost v4.31 a false attribution that reached the maintainer (`BACKLOG_v4_31.md` item
111). **Uninstall first, then read the installed version back:**

```bash
adb uninstall com.paperscrape.livewallpaper.debug
adb uninstall com.paperscrape.livewallpaper.debug.test
adb install <old>/app-debug.apk && adb install <old>/app-debug-androidTest.apk
adb shell dumpsys package com.paperscrape.livewallpaper.debug | grep versionCode   # put this beside the number
```

**To find out *what* a golden change is, render the previous release too.** `SceneGolden` takes
`-e dumpFrames true`, which writes every rendered frame to the golden output directory whether it
passes or fails. Extract the base ZIP into a scratch tree, install it, dump; install yours, dump;
diff the two sets. That is what attributes a change to this release rather than to the device — and
in v4.30 it is also what found six goldens already carrying up to 67 % of their budget in drift
before the release started (`BACKLOG_v4_30.md` item 98). Comparing a failing frame against its
*committed* file cannot tell those two apart.

**Capture goldens with `am instrument`, not Gradle with a class filter** — Gradle
uninstalls the package at the end and takes the written frames with it. `SceneGolden`
writes to `getExternalFilesDir(null)/golden-output` (which works on this device) and also
emits each frame to logcat as base64 under the `GOLDENPNG` tag; `GlGolden` writes to the
same directory but does not emit.

**Building the delivery ZIP: `zip` is not installed on this machine** (`unzip` is). Build the
archive with Python's `zipfile` and write the mode into `external_attr` by hand, or `gradlew`
loses its execute bit in the archive:

```python
zi = zipfile.ZipInfo.from_file(f, arcname)
zi.compress_type = zipfile.ZIP_DEFLATED
zi.external_attr = stat.S_IMODE(f.stat().st_mode) << 16
```

**Check completeness with `find`, not `git ls-files`.** An extracted archive has no `.git`, so
the recipe that diffs `git ls-files` against the archive silently compares nothing. Diff
`find . -type f` on both sides instead. To prove `CLAUDE.md` is still untracked, `git init` the
*extraction* and run `git check-ignore` there.

**Delete `__pycache__` before archiving.** Running `python -m paperscrape_assets` anything —
even `--help` — writes `.pyc` files into `tools/assets/`, and 12.17 forbids session artefacts in
the ZIP. `find . -name __pycache__ -type d -exec rm -rf {} +` first; the completeness check
against the working tree will not catch them, because they are in both.

Release identifier, never from memory:

```bash
git tag --list 'v*' | sort -V | tail -10
curl -s https://api.github.com/repos/urgali/paperscrape/releases | grep -o '"tag_name": *"[^"]*"' | head
grep -n 'versionCode = \|versionName = ' app/build.gradle.kts
```

Tags are `vMAJOR.MINOR` and must equal `versionName`; CI rejects anything else and never
overwrites an existing tag. `versionCode` is Android's install counter: +1 per release,
not checked against the tag. Write `release-notes/<tag>.md` matching the tag exactly.

---

## 5. Counts are commands, not numbers

A count written into a document goes stale silently. This file has been wrong about the
test total by a factor of two, and off by one about the instrumented suite. So: run these.

```bash
# JVM unit tests
grep -rc '@Test' app/src/test --include='*.kt' | awk -F: '{n+=$2} END{print n" @Test"}'
# instrumented tests
grep -rc '@Test' app/src/androidTest --include='*.kt' | awk -F: '{n+=$2} END{print n" @Test"}'
# shipped sprites
ls app/src/main/res/drawable-nodpi/*.png | wc -l
# goldens: Canvas assertions (the real count), GL references, committed PNGs
grep -rh 'SceneGolden\.assertMatches' app/src/androidTest --include='*.kt' | grep -vc '^\s*\*'
ls app/src/androidTest/assets/golden/gl-*.png | wc -l
ls app/src/androidTest/assets/golden/*.png | wc -l
# after a run, the authoritative test result
python3 -c "
import xml.etree.ElementTree as ET, glob
t=f=e=0
for x in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(x).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures')); e+=int(r.get('errors'))
print(t,'tests,',f,'failures,',e,'errors')"
```

**Do not count goldens by listing the directory.** It holds the Canvas PNGs *and* the three
`gl-*.png`. The Canvas figure is the number of Canvas **assertions**, one `assertMatches` each,
no parameterised tests.

**Since v5.0 one of those calls is not a golden, and the command cannot tell.** `LightningPinTest`
hands `assertMatches` a scene it must *reject* — that is how the guard of item 112 is shown to run
inside the harness rather than only to exist — so the grep returns **one more than the number of
Canvas golden assertions**. It is deliberately the only such call in that file, and it says so at
its own declaration. Subtract it: **34 hits, 33 Canvas assertions** at v5.0.

**And an assertion is not a file — three of them share a golden.** The 33 assertions run against
**30 committed Canvas PNGs**: `day.png` is asserted three times (`SceneGoldenTest.day`,
`PeopleGoldenTest`'s pavement-focus case, `SkyWaterGoldenTest.day-sky`) and `lake-busy.png` twice
(`SceneGoldenTest.lakeBusy`, `SkyWaterGoldenTest.lake-busy-water`), because a focus region is a
second claim about the same frame and not a second frame. So the three numbers that directory and
those greps can give you are **33 Canvas assertions, 30 Canvas PNGs, 3 GL references — 33 PNGs on
disk**, and the two 33s are not the same 33. Say which one you mean whenever you write it down;
"33 Canvas goldens" has already been read as "33 Canvas files" once.

**Do not keep the list of classes here either** — this paragraph named
three and there are four, and v4.28 filtered an intermediate golden run on the stale list and
missed `SkyWaterGoldenTest`, whose `waterline-worst-theme` then failed in the full suite. Ask the
tree, which also finds the GL suite:

```bash
grep -rln 'SceneGolden\.assertMatches\|GlGolden\.assertGlBackendUnchanged' app/src/androidTest --include='*.kt' \
  | xargs grep -l 'class .*Test'
```

**Two things that command gets wrong on its own, both met in v4.31.** It matches **doc comments**,
so it lists `GoldenScene.kt`, which is not a test class — hence the `class .*Test` filter above. And
it lists only classes that *assert* a golden: `TrafficGoldenTest` renders scenes and compares them
itself, and it reads `SceneGolden`'s constants, so **a change to the golden machinery has to include
it even though it owns no PNG**. Filter on what you are changing, not only on what the name suggests.

Listing the directory once produced "30 Canvas goldens" for a suite that had 24, and
that number then propagated. `GoldenUniquenessTest` keeps two names from sharing one
picture; two *tests* may assert one PNG with different focus rectangles, but two *PNGs* of
one scene must not exist.

**Every CPU number is taken on the `perf` build type, and it is committed.** It is a fourth build
type beside `release`, `debug` and the test one: `initWith(release)`, signed with the committed
`debug.keystore`, `applicationIdSuffix = ".debug"`, `isDebuggable = false`. **Build it, measure on
it, and name it beside the figure** — `./gradlew --no-daemon assemblePerf`, then
`adb install -r app/build/outputs/apk/perf/app-perf.apk`.

It is committed on the maintainer's decision in v4.27, replacing the rule that it be rebuilt by
hand each session and deleted afterwards: that rule failed in v4.25, when the block reached the
delivery ZIP and the published tag. It is still **never published** — no workflow builds it, CI
builds `assembleRelease` — and `BuildTypeDeclarationTest` is what keeps that true rather than a
habit. **Do not delete it at the end of a session**, and do not add a fifth build type without
changing that test on purpose.

**A figure taken on `debug` is a figure about `debug`.** v4.26 spent three rounds of concept work
on "+4.5 points of CPU for every PNG substituted", which turned out to be a property of the debug
build and not of the artwork; re-measured on `perf`, substituting a sprite costs what repeating the
measurement costs.

**The one measurement worth keeping as a number**, because it is an experiment and not an
inventory — **BV6600, 2026-09-05, release-like build, Autumn, 60 s windows, n=3**: process
**43.56%** of one core (sd 0.50), hidden **0.137%**, **29.60 fps** from SurfaceFlinger,
therefore **14.72 ms of CPU per frame against a 33.3 ms interval — 44% of the budget**.
Protocol and the superseded OnePlus generations: `docs/archive/V4_22_MISURA_CPU_REPORT.md`,
`docs/archive/V4_22_AUDIT_SPRECO_GL_REPORT.md`.

**Quote the process figure, never a per-thread one.** That line used to carry
`PaperScrapeGlTh 42.71%` beside it, and the name is ambiguous: **two kernel threads wear it**.
Only one is ours. The other is the PowerVR driver's own worker — `libsrv_um.so`,
`PVRSRVBridgeCall`, `gralloc`, and not one frame of `libart` — and it wears our name because
Linux gives a new thread its creator's `comm` and the driver never renames it. In a process
where the settings screen touches EGL first, the same driver thread appears as `RenderThread`
instead. Measured on the home screen in 20 s windows, v4.25 debug build: ours **52.40%** of a
core, the driver's **4.45%**; with the wallpaper hidden the driver thread does not exist at all
and ours reads 0.55%. `BACKLOG_v4_25.md` item 60 has the attribution. The archived OnePlus
reports carry per-thread lines with the same ambiguity and are historical.

**GPU busy is not measurable on this device and must not be estimated**: `kgsl` is
Adreno-only, MediaTek's `ged` nodes are root-only, and `pvr_fence` counts fences. Per-thread
attribution *is* available — `simpleperf record --app <pkg> -t <tid>` works on the debug build
(plain `-t` without `--app` is refused: `perf_event_paranoid` is 1).

---

## 6. Project quick facts

- Single Gradle module `app`, package `com.paperscrape.livewallpaper`. Kotlin, `minSdk 26`,
  `compileSdk 37`, `targetSdk 37`, AGP 9.3.1, Gradle 9.7.1, Java 17. Read the SDK levels
  from `app/build.gradle.kts`; they are two settings doing two jobs that happen to agree.
- Kotlin comes from AGP's built-in support — the `org.jetbrains.kotlin.android` plugin is
  intentionally **not** applied. Do not add it. The compiler version follows
  `org.jetbrains.kotlin.plugin.compose` in the root `build.gradle.kts`.
- **The scene is 2D throughout, with two backends behind it, and neither the scene code nor
  you should assume which is live.** `PaperRenderer` composes and `SceneObjectRenderer`
  draws onto the `SceneCanvas` interface, implemented by `GlSceneTarget` (OpenGL ES 2.0, on
  a per-engine render thread — the normal path) and `CanvasSceneTarget` (the fallback, and
  what the settings preview uses). `ARCHITECTURE.md` §3 is the authority.
- Sprites live in `res/drawable-nodpi/`; `nodpi` is deliberate. `debug.keystore` is
  committed on purpose. `versionCode` drives the release tag.
- **Two sprite scale conventions exist**, named per call site as a `SpriteScale` argument:
  `SCENE_UNITS` (authored 3× and scaled down) or `CANVAS_PIXELS` (authored at on-screen
  size). Passing the wrong one is a silent 3× error, and nothing in the PNG says which.

---

## 7. Traps that are still true

- **A test that reads a build script can pass without running.** `testDebugUnitTest`'s up-to-date
  check watches the compiled classes, and `app/build.gradle.kts` and `.github/workflows/` are not
  among them, so a local run right after changing one reports `UP-TO-DATE` and green without
  executing `BuildTypeDeclarationTest` at all. Three mutations looked as though nothing caught them
  for exactly this reason. **After changing a build script or a workflow, add `--rerun-tasks`.** CI
  is unaffected: a fresh checkout has no previous outcome to reuse.
- **A green suite on first run is a warning sign, not a result.** Break the code under
  test, confirm the test fails, revert (`AI_PROJECT_RULES.md` 12.11). `SceneTheme.equals`
  compares by `id` alone, so a whole-object equality assertion passes even when every
  colour has been lost in a round trip.
- **Do not fix a size or alignment bug with a per-asset constant.** That is how the project
  accumulated five such patches in three releases. Find the system-level cause.
- **A committed golden is not a golden until the androidTest APK is rebuilt.** The expected
  PNGs are assets *of the instrumented APK*. Copying one in and running `am instrument`
  against an older APK fails with `No golden committed for '<name>'`, which reads like the
  copy never happened — it cost a whole 43-minute suite once. Run `installDebugAndroidTest`
  and check the PNG went in.
- **The GL references are tied to one reference driver; the Canvas goldens are portable.**
  Never raise a tolerance to make a different driver pass — that trades a real check for a
  green tick. **The reference driver has been this device's since v4.26** (BV6600, PowerVR
  Rogue GE8320; all three re-authored 2026-09-13 for the neighbourhood), so
  `GlDriverGapGuardTest` reads **0.00%** and will until a second GPU vendor exists — a
  non-zero reading here means the scene moved, not the driver. It was 0.49–0.94% while the
  committed files were Adreno-authored, and that gap is why a byte comparison is the wrong
  metric for a GL frame; keep the metric, the reason outlives the number. The four
  historical figures and their devices are in `GlGolden.EdgeDisplacement`, which is now the
  only place they survive. **Do not repeat the "Adreno-authored since v4.21" line** — it was
  stale from v4.26 and stood in two KDocs and a backlog entry for six releases.
- **`org.json` is a framework class**, so unit tests need the real implementation on the
  test classpath. Without it every `JSONObject` call throws "not mocked". Do not "fix" that
  with `isReturnDefaultValues`, which turns real assertions into assertions about stubs.
- **R8 gets OOM-killed here when the machine is busy** — the release-like build type died
  silently at `-Xmx2400m` while the instrumented suite was running. Run it alone.
- **Never add a raw `Slider` to the settings screen.** Use `PreferenceSlider`, which commits
  once on drag end; a raw one puts a disk round trip inside the thumb's feedback loop.
- **A scripted edit is not finished until it has been read line by line.** A regex pass over
  16 slider call sites left `it` instead of the commit parameter in three of them.
- **This device never calls `onOffsetsChanged`** (verified with a probe; the wallpaper
  picker is the positive control). Horizontal motion is `scrollProgress`'s own accumulator.
  The whole instrumented suite takes **about an hour** here — 62 minutes measured 2026-09-13,
  not the ~40 this line used to say. The redraw is why: a tower is 33 blits where the shipped
  facade was 6, and the tests that count frames pay it multiplied. `CarNightCrossfadeTest` alone
  is ~14 minutes (two methods x 1 280 frames, each a full scene render plus a bitmap measure).
  **It looks hung and is not** — check `adb shell top` for the process's CPU time before
  concluding anything; it sits at ~125%.
- **`elapsedSeconds` freezes at ~12 days** of visible uptime — first suspect for "the
  wallpaper stopped moving". **v66–v72 shipped unverified**, with no build tools available
  then; treat that range as less proven.
- **The original asset generators are lost**, and `tools/assets/` is the replacement: it
  regenerates from committed SVG sources every shipped sprite that has one, while the
  per-skin-tone recolours carry `source.kind = "none"` and name
  `tools/generate_skin_variants.py`. Re-measure the split with `validate`; do not quote it.

---

## 8. Open questions for the maintainer

**They live in `DESIGN_NOTES.md` §12, "Pending decisions"** — D1, D2, D3, D5 and D7 open, D4
answered by measurement. They used to be duplicated here, which is how this file came to carry a
D4 marked answered while `DESIGN_NOTES.md` still called it open. One table, one place: read it
there and do not resolve any of them unilaterally.

---

## 9. Session hygiene

- Delete `.gradle/`, `.kotlin/` and any `__pycache__` from the tree before handing it back.
  The asset tooling writes `__pycache__` the first time it is invoked, from anywhere.
- `local.properties` is machine-specific and gitignored; rewrite it rather than trusting it.
- Never delete anything starting with `.` without checking what it is.
