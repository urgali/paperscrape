# RELEASE_HISTORY.md

Progressive record of PaperScrape releases: what changed, what was fixed, what
assets moved, what changed architecturally, what decisions were taken, and what
is known to be broken or limited.

**Relationship to the other files:**
`CHANGELOG.md` is the full technical log, one long entry per version.
`release-notes/vMAJOR.MINOR.md` is the user-facing text published to the GitHub Release
and shown in the in-app update dialog. **This file is the engineering-facing
summary** — the one to read when picking up the project after a gap.

### A note on dates

Neither `CHANGELOG.md` nor the release notes record release dates, and this
repository was received without Git history, so **no release date before v73
can be stated accurately**. They are deliberately left blank rather than
guessed. Dates are recorded from the next release onward.

---

## v3.5 — a race in PaperScrape's own test, and the rule that the emulator job cannot hold up a release

**Prepared, not published.** `versionCode = 26`, `versionName = "3.5"`. No tag, no push, no GitHub
Release (`AI_PROJECT_RULES.md` §10.A / §11.D).

**No application code changed.** The only source file touched is one test.

### What failed

The v3.4 build failed its `build` job on `./gradlew test`:

```
773 tests completed, 1 failed
AwaitOnceTest > two threads racing to complete resume once FAILED
    expected:<200> but was:<199>   (AwaitOnceTest.kt:119)
```

`release` never ran, because `release.needs: build` — which is correct and is not what this release
changes.

### The cause: a race in the test, not in the code under test

The test starts four threads per iteration, each doing `calls.incrementAndGet()` then `complete(i)`,
and after fifty iterations asserts the counter is 200. **It never joined them.**
`awaitOnceOrNull` returns on the *first* completion — that is its contract — so the other three
threads of an iteration may not have incremented yet when the loop moves on, and on the last
iteration they are still in flight when the assert reads the counter. 199 means exactly one
straggler; the reachable range is 197..200.

Established before changing anything:

- **`v3.4` and `main` are the same commit**, `16c7a3de`. "Green on main, red on v3.4" is one commit
  producing two outcomes, which rules out any code, toolchain or configuration difference. The seven
  relevant files were fetched from that SHA and compared byte for byte against the working tree:
  identical.
- The failure is **not** in `awaitOnceOrNull`. Its resume-once guarantee is checked by the *other*
  assert in the same test (`value in 0..3`, line 117), which has never failed.
- Modelling the same structure without the join produced 199 in **299 of 300** trials and 198 in
  one. Adding the join produced 200 in **300 of 300**. That is the whole difference.
- The real test passed 40/40 in isolation on a 16-core machine; CI runs on four vCPU alongside the
  Gradle and Kotlin daemons, which is where the window opens.

### The fix

`app/src/test/kotlin/com/paperscrape/livewallpaper/location/AwaitOnceTest.kt`, and nothing else:
the racer list is hoisted so the caller can reach it, and each iteration joins the threads it
started before the loop continues.

Nothing was relaxed. No sleep, no retry, no widened timeout, no softened threshold, nothing
disabled, and `AwaitOnce.kt` untouched. The assert is *stronger* afterwards: `assertEquals(200, ...)`
was previously true by luck and is now deterministic.

Verified: the test ran **30 times in isolation, 30 green**, and the full suite is **773 tests, 0
failures**.

### The CI rule, written down

`AI_PROJECT_RULES.md` gains **10.12**: the `instrumented` job must never block a release by failing,
nor hold one up by still running, and — because `continue-on-error` only delivers the first of those
— `release` must not reach `instrumented` by *any* path in the graph.

**The workflow was verified and left unchanged**, because it already satisfies this. Every coupling
was checked, not just a literal `needs:`:

| path | present |
|---|---|
| `instrumented` in `release.needs` | no |
| `instrumented` in the transitive closure of `release.needs` (`{build}`) | no |
| `needs.instrumented.*` in an expression | no |
| `outputs` declared by `instrumented` | none |
| an artifact `release` downloads that `instrumented` uploads | `release` downloads nothing |
| a third job bridging the two | the workflow has three jobs; none bridges |
| a `concurrency:` key serialising runs | none |

A job-level `success()` evaluates only the jobs in that job's own `needs`, so `release`'s `if` does
not couple them either.

- 773 JVM tests, 0 failures.
- `lintDebug` 0 errors, 32 warnings/notes — unchanged.
- `assembleDebug`, `assembleDebugAndroidTest` and `assembleRelease` (R8) all produce artefacts.
- Nothing under `app/src/main` changed; nothing under `app/src/test` changed except this one test.

---

## v3.4 — the CI emulator job waits until the device can actually install a package

**Prepared, not published.** `versionCode = 25`, `versionName = "3.4"`. No tag, no push, no GitHub
Release (`AI_PROJECT_RULES.md` §10.A / §11.D).

**No application code changed.** The diff is the `instrumented` job, the two version numbers and the
documentation. Nothing under `app/src` was touched and no golden was regenerated.

### What failed

With v3.3's provisioning fix in place, CI reached the emulator for the first time and then ran no
tests at all:

```
Failed to install split APK(s)
java.lang.SecurityException: android from uid 1000 not allowed to perform GET_USAGE_STATS
    at StorageStatsService.checkStatsPermission / enforceStatsPermission / getCacheBytes
    at StorageManager.getAllocatableBytes
    at InstallLocationUtils.checkFitOnVolume / resolveInstallVolume
    at PackageInstallerService.createSessionInternal   (pm install-create)
Starting 0 tests
Finished 0 tests
```

### What it is not, and how that was established

The `GET_USAGE_STATS` in the message invites the conclusion that PaperScrape wants a permission it
does not have. It does not, and no manifest change was made. Each of the obvious suspects was ruled
out by running the real thing locally against **component versions identical to the ones CI
installs** — emulator 37.1.11, platform-tools 37.0.1, `platforms;android-37.0` rev 2,
`system-images;android-37.0;google_apis;x86_64` rev 6, with `sdkmanager` reporting no updates
available for any of them:

| suspect | result |
|---|---|
| the app or its APK | `adb install -r -t app-debug.apk` -> **Success** |
| the ddmlib/UTP session path | `cmd package install-create -r --bypass-low-target-sdk-block -t --user 0`, then `install-write`, then `install-commit` -> **Success** |
| the API 37 image | identical revision, `connectedDebugAndroidTest` -> **21/21** |
| a first-ever boot | `-wipe-data` cold boot, install immediately after `boot_completed` -> **Success** |
| disk pressure | 4.5 GB free on `/data`; the partition is dynamic and could not be constrained |
| memory | the emulator forces Android 17's 4 GB minimum regardless of the AVD, and even against an explicit `-memory 2048` (`Increasing RAM size to 4096MB` in its own log) |

### The actual cause, reproduced

**`sys.boot_completed` is not the same as "ready to install a package".** The action starts its
script the instant that property turns 1, while `PackageManagerService`, `AppOpsService` and
`UsageStatsService` are still initialising behind it.

Until v3.3 the gap was covered by accident: the script's first act was `./gradlew
connectedDebugAndroidTest`, whose first act was a full Kotlin compile lasting minutes. v3.3 fixed the
provisioning, CI reached this point for the first time with a warm Gradle cache, and the install
arrived seconds after boot instead. Appops answered from a half-initialised state — a non-default
mode for `GET_USAGE_STATS` — `StorageStatsService` threw, and the session was never created.

Reproduced locally on a freshly created API 37 AVD, running the job's script the moment the device
answered:

```
Starting 0 tests on ci-api37(AVD) - 17
Finished 0 tests on ci-api37(AVD) - 17
[Failure [DELETE_FAILED_INTERNAL_ERROR]]
```

A different service caught mid-initialisation — the uninstall rather than the install — and the same
outcome: zero tests. **The identical command on the same device a few seconds later ran 21/21
green.** That is the whole bug.

### The fix

Two changes to the `instrumented` job, both purely CI.

**1. Both APKs are built before the emulator step.** A new `./gradlew assembleDebug
assembleDebugAndroidTest` step runs before `android-emulator-runner`. The emulator is then alive only
for the install and the tests — seconds — instead of for a multi-minute compile, and an Android 17
guest holding its mandatory 4 GB no longer sits alongside a Gradle daemon, a Kotlin daemon and AGP's
workers for the duration. The work is the same work, moved.

**2. The script waits for a real installer transaction, not a signal.** Before invoking Gradle it
installs and uninstalls the app APK in a bounded retry loop, and only proceeds once both succeed:

```sh
for i in $(seq 1 60); do
  if adb install -r -t "$APK" >/dev/null 2>&1 && adb uninstall "$PKG" >/dev/null 2>&1; then
    ready=1; break
  fi
  sleep 2
done
```

**A query is not sufficient and that was tested.** An earlier version of this fix probed with
`cmd package list packages`; it answered after 7 s on a device that then still failed the install.
An install followed by an uninstall exercises both halves of what `connectedDebugAndroidTest` does
first, so when the probe succeeds the operation that used to fail cannot. If it never succeeds the
job fails loudly with the last attempt's output rather than waiting out its 45-minute cap or going
green having tested nothing.

**3. Diagnostics on failure.** v3.3's failure arrived as one stack trace with no device state, which
is why the investigation had to be done by rebuilding the whole environment locally. A new
failure-only step now collects SDK component revisions, device properties, `/proc/meminfo`,
`df /data`, `dumpsys diskstats`, the `GET_USAGE_STATS` appop, the package list, host memory and disk,
and full logcat, and uploads them as an artefact.

### Verification

Three consecutive cycles, each starting from an AVD **created from scratch** with the action's own
command and cold-booted with the workflow's own emulator options:

| cycle | installer ready after | tests |
|---|---|---|
| 1 | ~16 s | 21/21 |
| 2 | ~16 s | 21/21 |
| 3 | ~16 s | 21/21 |

The ~16 s is the measurement of the race window: for the first fourteen seconds after the device
answered, it could not complete an install/uninstall pair. That is precisely where v3.3 was landing.

Each run: `sdk=37`, `release=17`, `preview_sdk=0` (stable image, not the `37.2-beta3` preview the
goldens were first taken on), 14 `SceneGoldenTest` + 3 `GlSceneGoldenTest` + 4
`PrefsCorruptionRecoveryTest` = 21, 0 failures, 0 errors, clean `adb emu kill` shutdown.

- 773 JVM tests, 0 failures — unchanged.
- `lintDebug` 0 errors, 32 warnings/notes — unchanged.
- `assembleDebug` and `assembleRelease` (R8) both produce APKs.
- No golden regenerated, no test modified, nothing under `app/src` touched.

### Upstream context

The action's own maintainers attempted API 37 in `ReactiveCircus/android-emulator-runner#476`. They
reached the same `'37.0'` string form v3.3 arrived at independently, then hit a separate
emulator-level problem on hosted runners ("device seems to remain offline after 5 minutes"), filed
it with Google as issue 524601393, and **closed the PR without merging**. API 37 on GitHub-hosted
runners is therefore not known-good upstream, which is the standing reason this job still gates
nothing.

### Unchanged, deliberately

`release.needs` is still `build` alone; `continue-on-error: true` remains; the action is still pinned
to the same SHA; permissions are still `contents: read`; no secret was added. No `PACKAGE_USAGE_STATS`
was added to the manifest, no AppOps was granted, no PackageInstaller check was disabled, and the job
was not moved to API 36.

---

## v3.3 — the CI emulator job asks the SDK for a package that exists

**Prepared, not published.** `versionCode = 24`, `versionName = "3.3"`. No tag, no push, no GitHub
Release (`AI_PROJECT_RULES.md` §10.A / §11.D).

**No application code changed.** The diff is one workflow input, the two version numbers, and the
documentation. v3.2 remains the application baseline; this release exists because build
configuration changed and every change gets its own version, not because anything the app does moved.

### What failed

The first real GitHub Actions run of the `instrumented` job added in v3.2 died before the emulator
was created:

```
/usr/bin/sh -c sdkmanager --install 'build-tools;37.0.0' platform-tools 'platforms;android-37'
Warning: Failed to find package 'platforms;android-37'
adb -s emulator-5554 emu kill
error: could not connect to TCP port 5554: Connection refused
The process '/usr/bin/sh' failed with exit code 1
```

`/usr/bin/sh` was not missing and ran fine; it returned 1 because `sdkmanager` did. The `emu kill`
line underneath is the action's own cleanup running against an emulator that was never started, not
a second fault.

### Why `platforms;android-37` does not exist

**Android platform packages carry their minor version from 36.1 onwards.** Reading the SDK
repository through `sdkmanager --list`, what is published is:

```
platforms;android-35   platforms;android-36   platforms;android-36.1
platforms;android-37.0 platforms;android-37.1 platforms;android-37.2
```

There is no bare `platforms;android-37`, and there never was — 37 exists only as `37.0`, `37.1`,
`37.2`. GitHub's own `runner-images` manifest for `ubuntu-24.04` (which is `ubuntu-latest`) agrees:
it lists `android-37.0 (rev 2)`, `android-37.1`, `android-37.2-beta*`, `android-36.1`, `android-36`
as preinstalled, and no `android-37`. The platform the job needs was already on the runner; the job
was asking for a name that names nothing.

### Which part of the action produced it

`reactivecircus/android-emulator-runner`, at the pinned `a421e43` (v2.38.0), `src/sdk-installer.ts`:

```ts
sdkmanager --install 'build-tools;${BUILD_TOOLS_VERSION}' platform-tools 'platforms;android-${apiLevel}'
```

and `src/emulator-manager.ts`:

```ts
avdmanager create avd --package 'system-images;android-${systemImageApiLevel};${target};${arch}'
```

`apiLevel` is `core.getInput('api-level')` — **a plain string, interpolated verbatim, never parsed
as a number** anywhere in the action (`input-validator.ts` validates `emulator-build` and
`disk-size` numerically; `api-level` is not among them). The action's own input documentation says
as much: *"API level of the platform and system image - e.g. 23, 33, 35-ext15, Baklava"*. It is a
package-name fragment, not an integer, and v3.2 handed it a fragment that names no package.

### The fix

One value, quoted:

```yaml
api-level: '37.0'
```

`system-image-api-level` is left unset and defaults to it, so the same string feeds all three
commands and there is one place to change:

```
sdkmanager --install 'build-tools;37.0.0' platform-tools 'platforms;android-37.0'
sdkmanager --install 'system-images;android-37.0;google_apis;x86_64'
avdmanager create avd --package 'system-images;android-37.0;google_apis;x86_64' --device pixel_6
```

**The quotes are load-bearing.** Unquoted, `37.0` is a YAML float, and a float that reaches the
action's string input as `"37"` would reinstate the bug silently. Quoting removes the question.

Options A and D were evaluated and rejected on evidence. **A** — a fixed release of the action —
does not exist: `main` still carries the identical line, and v2.38.0 (2026-07-05) is the newest
release, which is already what is pinned. **D** — replacing the action — has no justification when
the action's own documented string input expresses the correct package. **B** partly applies and is
noted above: the platform is already on the runner, so the install is now a no-op for it and only
the system image is fetched.

### Verification

**The failure was reproduced and the fix proven at the level of the failing command**, using the
exact command-line tools the action downloads (`commandlinetools-linux-14742923`, newer than the
locally installed set, which is why a stale local `sdkmanager` returns 0 where CI returns 1):

| command | exit |
|---|---|
| `... 'platforms;android-37'` | **1**, `Warning: Failed to find package` — the CI log, reproduced |
| `... 'platforms;android-37.0'` | 0 |
| `... 'system-images;android-37.0;google_apis;x86_64'` | 0 |
| `... 'system-images;android-37;google_apis;x86_64'` | **1** — the counter-proof: the next command would have failed too |

That last row matters. Fixing only the platform name would have moved the failure one step later;
because both derive from the same input, one change fixes both.

**The whole CI path was then reproduced locally**: an AVD created with the action's own command
(`avdmanager create avd --force -n ci-api37 --package 'system-images;android-37.0;google_apis;x86_64'
--device pixel_6`), booted with the workflow's own emulator options
(`-no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -camera-back none`), and
shut down with the action's own `adb -s emulator-5554 emu kill`.

The device reported `ro.build.version.sdk=37`, `release=17`, `preview_sdk=0` — a **stable** API 37
image, not the `37.2-beta3` preview the goldens were originally taken on. All 21 tests passed there:
14 `SceneGoldenTest`, 3 `GlSceneGoldenTest`, 4 `PrefsCorruptionRecoveryTest`, 0 failures, 0 errors.
The emulator shut down cleanly in about six seconds with no leftover process.

- 773 JVM tests, 0 failures — unchanged from v3.2.
- `lintDebug` 0 errors, 32 warnings/notes — unchanged.
- `assembleDebug` and `assembleRelease` (R8) both produce APKs.
- No golden was regenerated and no test was modified.

### Unchanged, deliberately

`release.needs` is still `build` alone and the job still carries `continue-on-error: true`: it has
now been shown to work, not shown to be stable, and promoting it to a gate stays a separate later
decision (`ROADMAP.md`). The action is still pinned to the same SHA, permissions are still
`contents: read`, and no secret was added.

---

## v3.2 — the golden tests run themselves, the GL backend is under test, and a solar day may cross midnight

**Prepared, not published.** `versionCode = 23`, `versionName = "3.2"`. No tag, no push, no GitHub
Release: from v3.2 onward publication is the maintainer's act and Claude's deliverable is a verified
ZIP (`AI_PROJECT_RULES.md` §10.A, §11.D, §12.F, added in this batch).

The remaining P1 and two of the P2 items from the v3.0 assessment, and nothing else.

### Permanent rules added first (Fase 0)

`AI_PROJECT_RULES.md` §10.A forbids Claude from pushing, tagging, releasing or uploading anything,
and from reaching GitHub with any of the maintainer's credentials — SSH keys included, and
explicitly forbids working around a refused HTTPS push rather than stopping at it. §11.D splits
release preparation from release publication and says which half is whose. §12.F makes a verified
delivery ZIP the deliverable of every modifying batch, with what it must and must not contain and
the order the checks run in. `CLAUDE.md` §2 and §5.6 carry the operational form of the same rules,
including the specific trap on this machine: `~/.ssh/id_rsa` authenticates as the maintainer and
*will* let a push through, which is exactly why it is not to be touched.

### P1-3 closed: the instrumented tests now run in CI

`android-build.yml` ran `lint`, `test` and `assembleDebug`; nothing ran `connectedAndroidTest`, so
the only defence against a visual regression was somebody remembering to pull it by hand.

A new `instrumented` job runs the whole suite on an emulator: `reactivecircus/android-emulator-runner`
pinned to `a421e43` (v2.38.0) like every other action here, **API 37 `google_apis` x86_64** —
matching the platform the goldens were taken on, and available as a stable image, which was checked
rather than assumed — `pixel_6`, headless, `-gpu swiftshader_indirect`, KVM enabled by the runner's
own udev rule, 45-minute cap, and `androidTest-results` plus the rejected frames uploaded on failure.

**It gates nothing.** `continue-on-error: true`, and `release.needs` is still `build` alone. An
emulator job is the flakiest thing in an Android CI and a new one has no track record; promoting it
is a deliberate later change and `ROADMAP.md` records it as such. It also skips pull requests.

**Not observed running.** Claude cannot execute GitHub Actions without pushing, which §10.A forbids,
so the job is statically valid (YAML parsed, every action SHA-pinned, `release.needs` confirmed
unchanged) and its Gradle task is proven locally — but its first real run, and therefore its true
duration and flakiness, belong to the maintainer. This is stated as an outstanding item rather than
folded into the pass.

### P1-4 closed: the shipped GL backend has visual coverage

All fourteen goldens rendered through `CanvasSceneTarget`, which is right and stays. The
consequence was that `GlSceneTarget` — ~690 lines of hand tessellation, batching, atlas UVs and
premultiplied blending, and what actually draws the wallpaper wherever EGL works — had nothing
pinning a single pixel.

`GlGolden` stands up an **offscreen EGL pbuffer**, hands the real `GlSceneTarget` to the real
`PaperRenderer` through the same `SceneCanvas` seam the wallpaper uses, and reads the framebuffer
back. No second renderer: every pixel comes from shipped code. The config is `GlRenderThread`'s own
— 8888, no depth, no stencil, 4x MSAA with a fallback — differing only in `EGL_PBUFFER_BIT`.

Three scenes, `day`, `lake-busy` and `thunderstorm`, and their definitions moved into
`SharedGoldenScenes` so both suites render provably the same objects. Two comparisons per scene:

| | against | channel | limit | healthy |
|---|---|---|---|---|
| GL golden | committed `gl-<name>.png` | >=16 | 0.50% | 0.12% |
| Canvas cross-check | the Canvas golden | >=64 / >=32 | 1.0% / 2.0% | 0.21% / 1.01% |

**Every threshold is measured.** Rendering the three scenes under two very different GL drivers —
the host-GPU translator and `swiftshader_indirect` — showed they differ from each other by only
0.12% of pixels at `>=16`, while GL differs from Canvas by 1.01% at `>=32`. That gap is what lets
the GL golden be four times tighter than the cross-check. The committed GL goldens were generated
under `swiftshader_indirect`, the driver CI uses, and then verified to still pass under the
host-GPU driver — so the portability the numbers imply was checked, not assumed.

Both comparisons are kept because they fail differently: the GL golden is sensitive but pins only
this backend against itself, so on the day a driver change forces a regeneration it would bless a
real bug at the same time; the cross-check is what stands in the way of that.

**Teeth, with the negative results reported too.** Four deliberate regressions:

| mutation | caught | numbers |
|---|---|---|
| premultiplied blend function swapped for the non-premultiplied one | **yes, 3/3 scenes** | 3.75% / 2.09% / 1.74% against a 0.50% limit |
| orthographic projection shifted one pixel | **yes, 3/3 scenes** | 1.74%–2.75% against 0.50% |
| `drawRadialGlow`'s fan reduced to a single triangle | **no** | max delta 15/255; 0.47% at `>=8` where two correct drivers already differ by 0.88% |
| hill gradient highlight flattened | **no** | max delta 17/255; 0.128% at `>=16` against a 0.12% driver floor |

The last two are not gaps to be closed by lowering a threshold: both effects are low-contrast by
design (the glow is alpha 90 over a bright sky), and both move fewer pixels than two correct GL
drivers move between themselves. A limit under that floor would fail on the next emulator instead of
on the next bug. Recorded here rather than omitted, per §12.11.

Under the earlier design — cross-check only — the blend regression was caught on **one** scene of
three, by 5.18% against a 5.0% limit. Adding the GL golden is what took it to three of three.

### P2-3 closed: a solar day may cross the device's midnight

`solarNoon = 12 - longitude/15 + utcOffset` is not pinned to 12:00, and a long day on top of a late
solar noon puts sunset past 24:00 — Ísafjörður in June, Nome in June, anywhere keeping a timezone
far from its geography. `approximateSunriseSunset` closed both values inside `0..23.98`, which does
not move such a day: it deletes the end that did not fit. Kiritimati (UTC+14 at 157W, solar noon
near 36:30) came out as a zero-length day at midnight, two degrees from the equator.

Fixed along the whole path, not at the `coerceIn`:

- **The calculation** wraps onto the clock instead of clamping into it. The two poles are answered
  before any wrapping can blur them — `2 * hourAngleHours >= 24` returns the literal `(0, 24)`,
  `<= 0` returns `(noon, noon)` — because after wrapping a 24-hour day and a 0-hour day are the same
  pair, and they mean opposite things.
- **`dayLengthHours(sunrise, sunset)`** is new and public: a day is an arc on a circle, so its length
  is `sunset - sunrise` only when the two share a date.
- **`compute()`** classifies day and night circularly (`wrap24(hour - sunrise) <= dayLength`) and
  measures both arcs around the clock. For a window that does not wrap this is arithmetically
  identical to the subtraction it replaces, which is why no ordinary location moved.

Fourteen new JVM cases: the three reference cities pinned to real ranges (Mountain View 05:51/20:26,
New York 05:28/20:24, Tokyo 04:29/18:54, none wrapping), a sunset after midnight, a sunrise before
it, a solar noon past the end of the clock, both poles, and a 1 470-combination sweep of
latitude x longitude x offset x day-of-year asserting every result is a real clock time with a
duration between 0 and 24 hours. Both halves are load-bearing: restoring the clamp fails three
tests, restoring the linear day/night test fails two.

**Runtime A/B on the device.** Custom location Kiritimati, clock frozen at 05:00, everything else
identical: v3.2 draws daylight with the sun near the horizon, v3.1 draws night with the moon. The
light window is 18:28 -> 06:31 in device-clock terms and 05:00 is inside it. Milan at 13:00 and
03:00 is unchanged — day and night respectively.

### P2-4 closed: the geocoder cannot hang

`LocationLabelResolver` passed a **lambda** to `Geocoder.getFromLocation(..., GeocodeListener)`.
That interface declares `onGeocode` *and* `onError`; a SAM conversion implements the first only, so
every error the platform reported arrived at a method nobody had written, the continuation was never
resumed, and there was no timeout either. Upstream: a settings row on "Locating..." for the life of
the screen.

Three changes, and each closes a different way to hang:

- The full `Geocoder.GeocodeListener` is implemented, so the error path reaches the coroutine.
- `awaitOnceOrNull` (new, pure Kotlin, no Android imports) bounds the wait at 6 s, guarantees the
  continuation resumes exactly once whatever the platform does, resumes immediately if the platform
  call throws synchronously, and stays cancellable. No polling.
- The pre-API-33 branch, which is synchronous and was running on the main thread from a
  `LaunchedEffect`, moved to `Dispatchers.IO` — without which the timeout would bound nothing, since
  `withTimeoutOrNull` can only give up at a suspension point.
- `CancellationException` is rethrown rather than swallowed by the `catch (Exception)`.

Eight JVM cases on the bridge: result, error, no callback at all, a late callback after the timeout,
a double callback, four threads racing, a synchronous throw, and outer cancellation. Removing the
timeout fails two of them — and the "never comes" test carries its own outer bound specifically so
that removal produces a red assertion instead of a hung suite.

**Reported rather than claimed:** removing the `AtomicBoolean` once-only guard does *not* fail the
suite, because `continuation.isActive` alone covers the sequential case and the 200-thread race did
not reproduce the window. The guard stays — the check-then-resume pair is genuinely not atomic — but
it is protection the tests cannot force. §12.11.

**Runtime.** Reverse lookup resolves ("Milano, Italia") with no stuck loading state. City search
online returns results; in aeroplane mode it reports *"Couldn't reach the city search — check your
connection and try again. Your current location is unchanged."* and settles there. The platform
geocoder's own `onError` could **not** be provoked on this emulator, which answers from a local
dataset even with the radios off; that path is covered by the JVM tests only.

### P2-7 closed: the bird that could be tapped for is gone from the README, and so is the leftover

The README advertised a bird summoned by tapping. The gesture was removed releases ago.
`setTouchEventsEnabled(true)` was still in `PaperEngine.onCreate` with nothing overriding
`onTouchEvent` or `onCommand`, so the window manager was dispatching every touch over the home
screen to an engine that discarded it. Both removed; a comment marks the absence as deliberate so
the call is not restored without a handler. A global search for `setTouchEventsEnabled`,
`onTouchEvent`, `MotionEvent`, `onCommand` and "summon" across sources and documentation finds only
that comment. Twelve taps on the running wallpaper: nothing happens, nothing logs, the scene keeps
drawing.

### Verification

- **773 JVM tests**, 0 failures (765 in v3.1; +8 geocoder, +14 sun, and three v3.1 sun tests
  subsumed).
- **21 instrumented tests** on Pixel_9 / Android 17, 0 failures (18 in v3.1; +3 GL).
- `lintDebug`: 0 errors, 32 warnings/notes — unchanged, none in any file this release touched.
- `assembleDebug` and `assembleRelease` (R8) both produce APKs.
- Runtime pass on an Android 17 emulator; logcat clean — no `FATAL`, no ANR, no application error.
- GL goldens generated under `swiftshader_indirect` and re-verified under the host-GPU driver.

### Known limitations carried forward

`P2-5` (Canvas `Shader` allocation in the draw path), `P2-6` (three scene fields shared across
threads), `P2-8` (`ARCHITECTURE.md`'s validity stamp) are untouched, as are the weather-provider
work and `targetSdk 37`. The CI emulator job has not been observed running. See `ROADMAP.md`.

---

## v3.1 — a corrupt preferences file no longer kills the wallpaper, and four smaller lies fixed

**Stable / latest.** `versionCode = 22`, `versionName = "3.1"`. Tag `v3.1`.

A deliberately narrow hardening release. Everything below comes from the full static + runtime
assessment of v3.0 on an Android 17 emulator; nothing else was touched, no feature was added, and
the four items the assessment classified as v3.2 or later (CI emulator goldens, an offscreen
`GlSceneTarget` test, the weather-provider work, and the P2-3..P2-8 group) were deliberately left
alone.

### P0-1 closed: one damaged preferences file took the whole wallpaper down

**The failure.** None of the three `preferencesDataStore` declarations passed a `corruptionHandler`,
and none of the three read paths caught anything -- a search of the v3.0 source found no `.catch`,
no `corruptionHandler`, no `emptyPreferences`. The settings collector lives in
`PaperEngine.onCreate`, inside `CoroutineScope(Dispatchers.Main + engineJob)` with no
`CoroutineExceptionHandler`, so a `CorruptionException` reached the process's default handler and
killed the process that draws the wallpaper. Android answered by swapping PaperScrape for
`ImageWallpaper`, and the crash repeated on every restart. The only user-reachable remedy was
"clear app data", which destroys all three stores including the two that were fine.

**The fix, three parts, deliberately not one.**

- `PrefsRecovery.replacingCorruptFile()` -- a `ReplaceFileCorruptionHandler` on each of the three
  declarations. Corruption is unrecoverable and would throw identically forever, so the file is
  rewritten empty **once** and the store comes up on its declared defaults. Each store owns its own
  file, so this can only ever destroy the file that was already unreadable.
- `PrefsRecovery.recoveringFromReadErrors()` -- `.catch { if (it is IOException) emit(emptyPreferences()) else throw it }`
  on `WallpaperPrefs.settingsFlow`, `CustomThemeStore.dataFlow` and `UpdatePrefs.readSnoozeState`.
  This is the *transient* path: defaults for that emission, nothing written, the real settings back
  on the next successful read. Deliberately different from the corruption path -- overwriting here
  would turn a busy disk into permanent data loss.
- Anything that is neither is rethrown. The engine scope became `SupervisorJob` +
  `CoroutineExceptionHandler`, so a collector that fails no longer cancels its siblings and no
  longer reaches the default handler. That is a backstop for the *next* collector somebody adds, not
  a substitute for the two rules above.

**Tests.** `PrefsRecoveryTest` (JVM, 5 cases) pins the IOException/other split, including that an
unexpected exception still propagates. `PrefsCorruptionRecoveryTest` (instrumented, 4 cases) writes
39 non-proto bytes into files carrying the real store names and asserts: the store reads as unset,
the bytes were replaced, the replacement is durable, the store is writable again, a healthy store is
never rewritten by being opened, and -- for each of the three stores in turn -- that corrupting one
leaves the other two byte-identical. It opens each store through
`PreferenceDataStoreFactory.create` with the production handler and shuts it down again, because
`preferencesDataStore` caches per process: a test that read the app's own warm store would pass
whether or not the fix existed.

**Runtime proof (Android 17 emulator, debug build set as the live wallpaper).** All three files
corrupted with the same 39 bytes the assessment used:

- `paperscrape_prefs` corrupted, app relaunched -> no `FATAL`, no `CorruptionException`, settings
  back to defaults, and the saved custom theme still listed ("12 built-in, 1 saved" survived).
- `paperscrape_custom_themes` corrupted, **device rebooted** -> `dumpsys wallpaper` still reports
  `com.paperscrape.livewallpaper.debug/...PaperWallpaperService` after the cold start, the scene is
  drawing, the corrupt store is empty, and `paperscrape_prefs` / `paperscrape_update_prefs` keep
  their exact byte counts and mtimes.
- `paperscrape_update_prefs` corrupted -> no crash, store reset, the other two unaffected.

A reboot, not `am force-stop`, is the faithful restart here: `WallpaperManagerService` logs
`Wallpaper uninstalled, removing` for a force-stopped package and reverts the wallpaper by design,
which is a property of force-stop rather than of the app.

### P1-1 closed: Live Weather could be left on, greyed out, with no way to turn it off

`WeatherTimeScreen` gated the switch on `syncWithRealTime && locationMode != OFF` while
`WorldSceneScreen` gated Clouds and Rain and snow on `settings.liveWeatherEnabled` alone. Turning
Live Weather on and then setting Location to Off (or turning off "Follow real time") produced a
persistent state with no exit: the weather controls said "turn Live Weather off in Weather & time",
and there the switch was disabled while reading on -- it did not even appear among the accessibility
tree's clickable elements.

`SettingsUiModel.liveWeather(...)` now returns a `LiveWeatherUiState` that separates the four things
the one boolean was doing:

| | means |
|---|---|
| `configuredOn` | what the user asked for |
| `canBeTurnedOn` | whether the prerequisites for a fetch are in place |
| `switchIsInteractive` | `canBeTurnedOn \|\| configuredOn` -- **an on switch is always off-able** |
| `drivingTheScene` | `configuredOn && status.isDrivingTheScene` (`OK` or `STALE`) |

`drivingTheScene`, not the stored flag, is what now makes Clouds/Precipitation read-only and what
"Driven by Live Weather" is allowed to claim. Two banners were corrected with it: the `OFF` status
used to share the `OK` branch and announce that the forecast was in charge and the screens locked,
in a state where neither was true.

One thing the fix surfaced and did **not** change: the engine's fetch loop never consulted
`syncWithRealTime`, so Live Weather really does keep running over a frozen clock even though the UI
will not let it be switched on in that state. The switch's supporting line was rewritten to stop
claiming otherwise and to leave "is a forecast in effect" to the status banner, which reads it from
what the engine actually did. Changing the engine's gate would be a behaviour change and is out of
scope for this batch.

`SettingsUiModelTest` gained 7 cases, including an exhaustive sweep asserting that **every**
combination of prerequisites and status leaves an enabled switch interactive.

**Runtime proof.** Case A (Custom -> Live Weather on -> Location Off): switch present in the
clickable tree, Rain and snow fully editable, both screens agreeing on the theme's own weather.
Case B (Live Weather on -> Follow real time off): switch tappable, tapped, Live Weather off. GPS
mode re-tested end to end afterwards -- "Milano, Italy", status OK, "Driven by Live Weather" shown
only then.

### P1-2 closed: a leaping dolphin was painted across a sailboat's sail

`LakeLanes.orderByDepth` sorted on the lane, i.e. the waterline, while `drawSailboat` puts
`sailboat_sail` 50 local units above its placement point -- about 82 px on a 2424 px screen, against
a lane spacing of roughly 22 px at high lake settings. A sail is therefore about four lanes tall,
and a dolphin one lane nearer than a boat -- painted after it, correctly by lane -- crossed the sail
in mid-air.

**`LakeLanes` was not rewritten.** The lane system, the pool sizes and the far-to-near pass are
untouched. What was added is one pure function:

```kotlin
fun depthOf(laneY: Float, heightAboveLane: Float): Float = laneY - heightAboveLane
```

Boats pass `0f`, so nothing about them moves. A dolphin passes its current climb, so its depth is
where its body actually is: it recedes as it rises, drops behind the boat whose waterline it has
climbed past, and returns in front as it lands. Three properties make it safe and
`LakeLanesTest` pins all three -- boats are untouched, a dolphin's depth only ever decreases, and a
farther dolphin cannot overtake a nearer one at realistic lane spacing.

**The assessment's own first suggestion was evaluated and rejected.** Sorting boats by
`laneY - sailHeight` as well would subtract a constant from every boat, pushing all of them behind
dolphins up to four lanes further out -- a far dolphin painted over a near boat's hull, which is a
worse defect than the one being fixed. Only the dolphin half of that suggestion is implemented.

**Golden.** `lake-dolphin-leap` at `sceneSeconds = 200.0`, solved for rather than picked: dolphin
candidate 0 is at `sin = 1.000`, its exact apex, six pixels horizontally from sailboat 0, with
candidate 2 repeating the situation half way up its own arc on the other side of the frame; lake
height 1.0 so the eight lanes are about 6 px apart at the golden's frame size, which is the
proportion a phone renders at its own lake settings.

Because a dolphin covers about 160 px and `MAX_DIFFERING_FRACTION` of a 360x800 frame is 576, the
whole-frame rule cannot see this sprite at all. `GoldenScene.focus` was added for it: named
rectangles compared a second time on their own area at `MAX_FOCUS_DIFFERING_FRACTION`. Verified to
have teeth -- reverting `depthOf` to plain lane ordering moves 99 pixels, passes the whole-frame
check at 0.03%, and fails the focused check at 6.19% against a 2% limit.

`lake-busy` was regenerated: the same change moves 57 pixels in it (one dolphin now passing behind a
sail). It still passed the committed v3.0 image, so this is a deliberate refresh rather than a
forced one.

**Runtime proof.** Lake at 100% height with both densities at 100%, 45 frames captured from the
running wallpaper: a dolphin mid-leap is clipped by the sail it overlaps, and two overlapping boats
still read as one passing in front of the other.

### P2-1 closed: "You're up to date" was shown when nothing had been checked

`UpdateChecker.checkForUpdate` returned `UpdateInfo?` and `AdvancedScreen` mapped every null --
offline, DNS, timeout, 403, unexpected JSON -- to `UpToDate`. Right for the silent launch check,
false for a button the user just pressed.

It now returns `UpdateCheckResult`: `Available`, `UpToDate`, or `Unreachable(reason)` with
`NO_CONNECTION` / `SERVER_ERROR` / `UNREADABLE_RESPONSE`, each carrying its own sentence.
`SettingsScreen`'s launch check acts on `Available` and ignores the rest, unchanged in behaviour;
`AdvancedScreen` reports all three through a new `UpdateUiState.CheckFailed`.

`UpdateCheckOutcomeTest` runs the real checker against a `com.sun.net.httpserver.HttpServer` on a
loopback port -- a 200 with releases, a 200 with none, five HTTP error codes, a non-JSON body, and a
port with nothing listening -- so the exception-to-outcome mapping is exercised rather than mocked.
`checkForUpdate` gained a test-only `apiUrl` parameter for it.

**Runtime proof.** Online and current -> "You're up to date (v3.0)". Aeroplane mode -> "Couldn't
check - no connection. Your version may or may not be current." A build temporarily stamped `2.9`
against the real v3.0 release -> both the launch dialog and the button offered the update.

### P2-2 closed: coordinates followed the device's locale

Four call sites used `"%.3f, %.3f".format(...)`, which uses the default locale, so an Italian,
French or German phone rendered `45,464, 9,190` -- one comma separating the pair and one inside each
number. `location/Coordinates.kt` formats them with `Locale.US`; `CityGeocoder.coordinatesText` and
the three `WeatherTimeScreen` sites go through it.

`WorldSceneScreen`'s `"%.1fx"` speed multiplier is deliberately left localised -- it is a quantity
read as language, not an identifier -- and `CoordinateFormatTest` has a case that fails if a future
tidy-up "fixes" it too. Also covered: it/en/fr/de/es, a locale with non-ASCII numerals, negatives,
and the coarse two-decimal form.

**Runtime proof.** App locale set to `it-IT`: the custom-location rows and every city-search result
read `45.464, 9.190`, `47.833, 26.600`, `-29.447, 27.708`.

### Verification

- **753 JVM tests**, 0 failures (715 in v3.0; +38).
- **18 instrumented tests** on Pixel_9 / Android 17, 0 failures (13 in v3.0; +4 DataStore, +1 golden).
- `lintDebug`: 0 errors, 32 warnings/notes -- none in any file this release touched.
- `assembleDebug` and `assembleRelease` (R8, `isMinifyEnabled`) both produce APKs.
- Logcat across the whole runtime pass: no `FATAL`, no `CorruptionException`, no ANR, no application
  error. The only `E` lines naming the package are `WallpaperManagerService: Wallpaper uninstalled,
  removing` and its `InputDispatcher` consequences, both produced by the test's own `am force-stop`.

### Known limitations carried forward

Unchanged from v3.0 and explicitly out of scope here: golden tests still do not run in CI (P1-3),
`GlSceneTarget` still has no visual coverage (P1-4), and P2-3 through P2-8 are untouched. See
`ROADMAP.md`.

---

## v3.0 — the updater fixed at the root, the lake given depth, location split three ways, and the scene put under golden test

**Stable / latest.** `versionCode = 21`, `versionName = "3.0"`. Tag `v3.0`.

### D13 closed: the updater hung because the screen cancelled its own download

**Reproduced first, on the real thing.** The published v2.15 release APK was installed on an
Android 17 emulator and its "Install update" tapped against the real v2.16 release. It sat on
`Downloading...` for over two minutes with no error, no exception and no way forward -- `Downloading`
disables the check row, so the screen was a dead end.

**Then proved, not guessed.** A differential first: the *other* download entry point, the "Download
and install" button, uses `scope.launch` and completed the same download in under four seconds. Same
release, same network, same `ApkDownloader` -- so the transfer was never the problem, the call site
was. A temporary instrumented build of v2.15 then produced the exact sequence:

```
21:33:20.318  LaunchedEffect ENTER key=UpdateInfo(v2.16)
21:33:20.318  calling onInstallStarted()
21:33:20.318  runDownload START v2.16
21:33:20.346  LaunchedEffect ENTER key=null          <- 28 ms later, the key changed
21:33:23.597  LaunchedEffect THREW LeftCompositionCancellationException
21:33:23.597  LaunchedEffect FINALLY, state=Downloading(percent=-1)
```

`LaunchedEffect(startInstallFor)` was keyed on the state its own body cleared: `onInstallStarted()`
sets the caller's `pendingInstall` to null, the key changed from the release to `null`, Compose
cancelled the effect it had just started, and the download died **before its first progress
callback**. Nothing ever overwrote `Downloading(-1)`. The path has been broken since v2.13, which is
when "Install update" became the dialog's primary action.

**The fix is three things, not one.** The effect is keyed on the tag and guarded by an
already-started check rather than by a key that clears itself; the download runs in the settings
screen's own scope, which outlives the effect, so even a genuine key change cannot cut a transfer in
half; and `runDownload` catches `CancellationException` and puts the state back to `Available`, so
whatever cancels it -- a recomposition, a configuration change, leaving the screen -- the UI can
never be left saying "Downloading" with nothing running.

**A `Verifying` state was added**, because there was a real lie in the old one: after the last byte
arrives there is still a digest to compare and a 2 MB package for `PackageManager` to parse, and the
screen said "Downloading" through all of it. `DownloadPhase` now carries `Downloading(percent)` and
`Verifying`, and the UI shows both.

**The download path became testable.** `downloadAndVerify` took a `Context` only to decide where the
file goes; `downloadAndVerifyTo` takes a `File`, so `ApkDownloadPathTest` drives the whole thing
against a real `com.sun.net.httpserver` on localhost: a good download, the phase sequence, progress
reaching 100, a server with no `Content-Length`, a truncated body, a 500 on the APK, a 404 on the
checksum, an unreadable checksum, a wrong hash, an unreachable host, and cancellation. Eleven tests,
no new dependency.

**Two honest results from mutation testing**, recorded rather than hidden:

- Removing the explicit `CancellationException` branch in `downloadHashing` leaves the suite green.
  It changes no observable behaviour today, because `withContext` re-throws on a cancelled job
  whatever the function returns. The branch is kept anyway -- the generic `catch (Exception)` below
  it would otherwise swallow a cancellation, and the first edit that adds work after that catch
  would turn a cancelled download into a silent success -- and its comment now says exactly that
  rather than claiming a fix it does not deliver.
- Removing the `downloaded != total` truncation guard also leaves the suite green, because
  `HttpURLConnection` detects a short fixed-length body and throws first. The test pins the
  *outcome* (`Failed`, no partial file) and its doc comment now says the guard itself is unproven.

**End to end on real releases.** v2.15 → v2.16 was downloaded through the app, verified against the
release's SHA-256, handed to the system installer, installed, and the new version launched. The
fixed code was then run through the same "Install update" tap that used to hang: `Downloading` →
`Verifying` → `Ready to install` → the system installer dialog, in under two seconds.

### The lake: two defects, one system

Reported as "two boats can completely overlap, one appears to be sailing on top of the other".
Reproduced on the emulator with Lake Height at 100 % and Sailboats at 99 %: at 22:22 two boats sat
on the same waterline with their hulls interpenetrating and their sails merged into one shape.

Two causes, and neither is a per-asset nudge:

1. **Lane aliasing.** `laneIndex = (i * 2 + category) % 6` with four candidates per category folded
   candidate 3 back onto candidate 0's lane. Two boats on one line, each with its own speed, means
   they must eventually slide through each other. One lane per candidate per category is eight
   lanes, not six, and then nothing folds.
2. **No depth order at all.** Boats were drawn in candidate order, then dolphins in candidate order.
   Whichever had the higher index covered the other regardless of where it sat on the water. On a
   flat scene with a horizon, distance *is* height: the lower thing is nearer and must be painted
   last.

`LakeLanes` is both rules, pure and unit-tested (10 tests). `drawLakeDecorations` was split into
`gatherLakeDecorations` -- which places both categories into preallocated slots, no per-frame
allocation on a draw path -- and one depth-sorted pass over them. Assets, speeds, paths, sizes and
the paper-cutout look are untouched; nothing is scaled by depth, because the scene is deliberately
flat.

Verified by 24 frames before and 24 after at the same settings: the overlapping-hull frames are gone
and boats close together now read as one passing in front of another.

### Live Weather: GPS, Network / Cell, Custom

"Phone" was two different things wearing one label. `DeviceLocationProvider` asked
`isProviderEnabled(NETWORK)` and fell back to `GPS_PROVIDER` when that was false -- so the cheap
option could start the GNSS receiver without saying so -- and it held a ten-minute
`requestLocationUpdates` subscription for the wallpaper's whole life to feed an hourly forecast.

| | Before | After |
|---|---|---|
| Modes | Off / Phone / Custom | Off / **GPS** / **Network** / Custom |
| Provider choice | whichever was enabled | exactly the one the mode names, never substituted |
| Permission | coarse, for both | coarse for Network, fine only if GPS is chosen |
| Requests | standing subscription, every 10 min | one bounded request, at most once per refresh |
| Cached fix | not consulted | preferred; a fix under 15 min old costs nothing |
| No fix available | nothing | falls back to the last saved position |

`DeviceLocationKind` names the two systems and their permissions; `LocationSource` gained `GPS` and
`NETWORK` so switching between them counts as a change of source and invalidates the held fix, the
same way switching to Custom already did. `currentFix` prefers `getLastKnownLocation`, falls back to
one `getCurrentLocation` (API 30+) or a self-removing single update below that, and is bounded by a
timeout on every path. The saved fix carries a timestamp and survives a reboot.

**One request per service, not per engine.** A wallpaper service runs an engine per surface, and
each had its own settings collector: measured on the emulator, one user action produced three
simultaneous GPS registrations. The provider and a `Mutex` moved to the service.

**Migration is silent.** An install from before v3.0 has the device flag and no stored kind, and
reads as **Network** -- which is what the old mode used in practice, so behaviour and permission
both stay put.

**Verified on an Android 17 emulator, both directions:**

- Network mode: the system prompt says *approximate*, only `ACCESS_COARSE_LOCATION` is granted, and
  `dumpsys location` shows the gps provider `ProviderRequest[OFF]`, `mStarted=false`, with no
  registration from this package at all.
- GPS mode: the system offers the *precise* upgrade, `ACCESS_FINE_LOCATION` is granted, and
  `dumpsys location` shows one bounded registration (`duration=+30s`, 3.9 s active, 3 locations)
  that stops by itself. Position resolved and labelled "Mountain View, United States".
- Permission refused: the mode does not change.
- Network position unavailable (the emulator's network provider is `enabled=false`): no GPS
  fallback, the saved position is used, and with no saved position the app says "Location
  unavailable — showing this theme's own weather instead."
- Mode switches, a reinstall and a force-stop all preserve the choice.

### Golden-image tests

13 scenes -- day, dusk, night, overcast, rain, snow, thunderstorm, three lakes and three themes --
rendered at 360×800 through `CanvasSceneTarget`, the same Canvas backend the settings preview and
the EGL fallback use, and compared against PNGs committed under `app/src/androidTest/assets/golden/`.

They are instrumented rather than JVM tests for a reason worth writing down: `SceneCanvas` passes
`android.graphics.Paint` through, and a unit test would be reading colours off the mockable
`android.jar`'s stub. The alternative was a JVM-only drawing surface, which is a second renderer --
and a golden produced by different drawing code proves nothing about the code that ships.

Reproducibility comes from `deltaSeconds = 0`: every candidate system is seeded from the theme id,
and the only unseeded `Random` is the lightning timer, which never advances. The bolt is therefore
deliberately *not* in the goldens; what the storm golden does pin is all of `StormAtmosphere` --
darkened sky, darker cloud band, attenuated sun.

**Shown to have teeth**: reverting the lake lane fix fails exactly `lakeBusy` and `lakeBoats`, and
nothing else. Tolerance is 8 per channel with at most 0.2 % of pixels exceeding it, which absorbs
anti-aliasing across Skia builds while still failing on a one-pixel move.

`assembleDebug`/`test`/`lint` are unaffected; the goldens run with
`./gradlew connectedDebugAndroidTest` and need a device.

### The external reference, removed

PaperScrape was built partly by comparison with another wallpaper app, whose name was a forbidden
string with a release-gate scan attached. Every **operational** trace is gone: about 45 source
comments that cited it as the authority for a current decision, rewritten to say what the code does
and why; `AI_PROJECT_RULES.md` §2 and §3 replaced with a standalone statement and a rule against
acquiring a new one; the forbidden-name declaration and its scan retired from `CLAUDE.md` and from
the release checklist. A global search -- text, binary and filenames -- now returns nothing anywhere
in the repository.

**The history was deliberately left alone.** `CHANGELOG.md` and the pre-v2.0 files under
`release-notes/` still describe work done by that comparison, because rewriting a published release
note to say something other than what it said is falsifying the record. `AI_PROJECT_RULES.md` §3
says so explicitly, so a future pass does not "tidy" them.

**D1 is closed as a side effect**: the README said the project is not a decompilation of a
third-party product while some source comments implied otherwise. The comments no longer imply
anything.

### The README

Opens with the maintainer's own note, added verbatim at their request:

> AI SLOP WARNING! I'm not a developer just a humble Networker. I don't know how to code. I just
> asked Chatgpt and Claude to do this app and that's it! Feel free to use it :)

### Measured

715 JVM unit tests (688 + 11 download-path + 10 lake-lane + 6 location, minus reshaped ones), 0
failures. 13 instrumented golden tests, 0 failures. `lint` 0 errors. `assembleDebug` and
`assembleRelease` both produce an APK, R8 clean. No rendering change beyond the two fixes above and
the lake's lane geometry, which the goldens now pin.

---

## v2.16 — the build stack, taken to the current stable line without touching the app

`versionCode = 20`, `versionName = "2.16"`. Tag `v2.16`.

This release closes **D5**, the dependency upgrade that had been deferred since v2.0 on the grounds
that nothing was broken by it. Nothing was, and nothing is: **no Kotlin source file was modified**.
The 688 unit tests, the lint result and the APK all came through the upgrade unchanged. What
follows is the reasoning, because the value of this release is entirely in *which* versions were
chosen and why.

### What moved, and what forced it

| Component | From | To | Why |
|---|---|---|---|
| Gradle | 9.5.0 | 9.7.1 | Latest stable of the same major. The wrapper jar was regenerated by Gradle's own `wrapper` task and its SHA-256 (`7a9ce74c…`) matches the `wrapperChecksum` Gradle publishes for 9.7.1, so CI wrapper validation still passes. |
| Android Gradle Plugin | 9.3.0 | 9.3.1 | Patch of the same minor. 9.4.0 exists only as `rc01` and was not taken. |
| Kotlin / Compose plugin | 2.2.10 | 2.2.21 | Not cosmetic: coroutines 1.11 and Compose 1.12 constrain `kotlin-stdlib` to 2.2.20 while the compiler was still 2.2.10. With AGP's built-in Kotlin the compiler version follows the Compose plugin version, so this one line moves both. Kotlin 2.3.x was **not** taken — that is a language-version jump nothing here requires. |
| `compileSdk` | 36 | 37 | Forced. `androidx.core 1.19.0` and the Compose `1.12` line both declare `minCompileSdk=37` in their AAR metadata. |
| `targetSdk` | 36 | **36** | Deliberately not moved. See D10 below. |
| `androidx.core:core-ktx` | 1.13.1 | 1.19.0 | Current stable. |
| `androidx.appcompat` | 1.7.0 | 1.8.0 | Current stable. |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.6 | 2.11.0 | Current stable. |
| `androidx.activity:activity-compose` | 1.9.3 | 1.13.0 | Current stable. |
| Compose BOM | 2024.10.01 | 2026.08.00 | Compose 1.12.0, Material3 1.4.0. |
| `androidx.datastore:datastore-preferences` | 1.1.1 | 1.2.1 | Latest stable; 1.3.0 is alpha and was not taken. |
| `kotlinx-coroutines-android` | 1.9.0 | 1.11.0 | Latest stable. |
| `org.json:json` (test only) | 20260719 | 20260814 | Test classpath only, never packaged. |
| `androidx.test.ext:junit` | 1.2.1 | 1.3.0 | `androidTest` only. |
| `espresso-core` | 3.6.1 | 3.7.0 | `androidTest` only. |

**Left alone on purpose:** `junit 4.13.2` (already the latest); build-tools 36.0.0 (AGP 9.3.1's own
default — `compileSdk 37` does not require build-tools 37); JDK 17 in CI (checked by running the
whole build on a Temurin 17 matching the `setup-java` step, not inferred); `gradle/actions` at
v5.0.2 (the MIT-licensing decision recorded in the workflow still holds); `material-icons-extended`
(its version comes from the BOM, and upstream has frozen it at 1.7.8). No `dependabot.yml` was
added — that would be a new capability, not an upgrade.

**The method was incremental, not a single bump.** Five groups — toolchain, `compileSdk`,
non-Compose AndroidX, the Compose BOM, then Kotlin — with `test`, `lint` and `assembleDebug` re-run
after each, so any breakage would have had one obvious cause. Every candidate version was checked
against its published AAR metadata (`minCompileSdk`, `minAndroidGradlePluginVersion`) *before* being
written into the build file rather than by trying it and reading the failure.

### The one user-visible change: D12

Material3 1.4.0 restyled `OutlinedButton`. Measured by sampling pixels on the device rather than
inferred from release notes: the content colour moved from `primary` to `onSurfaceVariant`
(`0xFF54443A`) and the border from `outline` to `outlineVariant` (`0xFFD9C7B7`) — exactly this
project's own tokens. It affects the seven `OutlinedButton` call sites. `TextButton` and filled
`Button` are unchanged, confirmed by opening the "Reset all customised themes?" dialog and seeing
its two text buttons still drawn in `primary`.

**It was left as Material draws it.** Rule 3 says the app is Material 3; pinning the old look would
mean hard-coding a superseded default into seven call sites. Whether the quieter button reads well
is a judgement to make while looking at the app — recorded as D12, with the one-line revert written
down if it is wanted.

### Verification

**Static.** 688 unit tests, 0 failures. `lint` 0 errors, 29 warnings and 3 hints, down from 40
warnings — twelve dependency-staleness warnings disappeared because the dependencies are no longer
stale, and one `ConfigurationScreenWidthHeight` warning plus three `AutoboxingStateCreation` hints
appeared, which are new checks in the newer tooling firing on unchanged code (D11).
`assembleDebug` and `assembleRelease` both produce an APK; the release build proves the R8 rules
still hold with the new libraries, with no `Missing class` output. The delivery archive was
extracted into a clean directory and rebuilt with `--no-build-cache`, so all 53 tasks genuinely
executed, and that APK's SHA-256 matches the one that was installed on the emulator.

**Runtime, on a clean Android 17 (API 37) emulator at 1080×2424.** The v2.15 build was installed
first and every screen photographed; the v2.16 build was then installed *over* it — same debug
keystore, so the saved settings survived and both builds were compared with identical state — and
the same screens photographed again and diffed pixel by pixel.

- Main settings screen: **0 differing pixels** below the status bar, theme mini-preview included.
- Theme gallery, Weather & time, World & scene, Advanced & about, top and scrolled to the bottom:
  content, geometry, colours and positions identical. Residual differences are one-pixel outlines
  on glyph and sprite edges. That they are anti-aliasing and not layout drift was established by
  re-capturing the same screen twice on one build, which came out byte-identical — so the
  comparison has no noise floor of its own to hide behind.
- The v2.14 bottom-inset fix still holds: the last row of every screen sits fully above the
  navigation bar.
- Live wallpaper set from the app, seen running in the system preview and on the home screen.
- `WallpaperManagerService` re-bound the engine by itself across the in-place update.
- Real network paths exercised: the update *check* returned "You're up to date", and Live Weather
  fetched from Open-Meteo and drove cloud cover to 40 % with the slider correctly read-only.
- DataStore 1.1.1 → 1.2.1 lost nothing: theme, Live Weather state and the custom location were all
  still there after the update.
- Logcat across the whole session: no `FATAL`, no crash, no ANR, no skipped-frame warnings.

**APK size, the one measurable cost:** debug 19.17 → 21.63 MB; minified release 1.79 → 1.99 MB
(+192 KB, +10.7 %).

**CI needed no change.** JDK 17 still builds AGP 9.3.1 on Gradle 9.7.1, and `compileSdk 37` needs
nothing installed — the `ubuntu-latest` runner image already ships `android-37.0`, read from the
image manifest rather than assumed.

### Known broken: the in-app updater hangs on `Downloading`

**Reported against v2.15 and NOT fixed in v2.16.** The in-app updater can enter
`UpdateUiState.Downloading` and stay there indefinitely; the download never completes and the user
has to fetch the APK from the Releases page by hand. *Checking* for updates is unaffected and was
seen working on the emulator during this release's verification.

**No cause is recorded here, because none has been established.** The updater code was not read,
not instrumented and not modified during this release — deliberately, so that v2.16 is exactly one
thing. Recorded as **D13**; it is the next task, and the fix is to be reproduced on an emulator and
verified end to end against a real GitHub release, not against a fixture.

### Documentation

`ARCHITECTURE.md` §8 rewritten for the new stack, including a correction found while editing it:
that section still described the release job as running "only on pushes to `main`" with the tag
derived from `versionCode`, a rule the workflow had already stopped enforcing in favour of `v*` tags
validated against `versionName`.

One incidental diff worth not being surprised by: Gradle's `wrapper` task regenerated `gradlew` and
`gradlew.bat`. The difference against v2.15 is four comment lines ("Gradle" → "gradlew"); the script
bodies are identical. They were left as Gradle generates them rather than hand-edited back.

---

## v2.15 — the storm now flashes only when something is falling, the sky knows about the weather, and the snow path was finally seen

`versionCode = 19`, `versionName = "2.15"`. Tag `v2.15`.

### The lightning system already existed and was already wired

The review asked whether a thunderstorm reaches the existing lightning/flash machinery. It does,
and it has since before Live Weather: `PaperRenderer.updateLightning`/`drawLightningFlash` are a
full-screen white veil plus the `lightning_bolt` sprite, on a randomised 4-12 s timer with a
randomised x position and bolt height, fading at 3/s. No second system was built and none was
needed. `WeatherCondition.THUNDERSTORM` comes from WMO codes 95/96/99 on Open-Meteo and from the
icon slug or the `conditions` text on Visual Crossing (its free `icons1` set has no thunder value,
so the text is the only place it appears), and `PaperRenderer` already read
`liveWeatherOverride?.isThunderstorm`.

**What was wrong was the gate.** The theme's own storm toggle has always required rain to actually
be falling -- `precipitation.visible && type == RAIN && thunderstorm`. The forecast-driven path
required only the condition. So a thunderstorm code arriving with every measurement at zero -- the
same code-flapping artefact v2.14 documented for Florence -- would have flashed lightning over a
dry scene: a strobe, not a storm. `isThunderstorm` in the snapshot now means "the scene should
storm", which is the condition **and** something falling, and the precedence between the two
sources moved into `LiveWeatherSceneRules.stormActive` next to the cloud rule, so all three layers
answer the "who is in charge" question in one tested place.

**Verified on the emulator against a real provider case**, not a fixture: Open-Meteo reported
`weather_code: 95, precipitation: 1.4, showers: 1.4, cloud_cover: 100` at (10, 150), and the app
produced `isThunderstorm=true, precipitationType=RAIN, cloudCoverFraction=1.0` with `stormActive=true`.
Twelve consecutive strikes were logged at intervals of 6.7, 9.8, 11.4, 12.0, 11.2, 9.4, 11.2, 7.2,
4.4, 8.0, 4.4 and 6.2 s -- mean 8.5 s against a designed 4-12 s, and with a ~0.33 s fade that is a
visible-flash duty cycle near 4 %. Occasional, randomised, never continuous. The scene at that
moment was a night sky with a dark full-cover cloud band and visible rain, which also covers the
day/night interaction.

A second candidate storm at (10, -90) had decayed to code 55 by the time the emulator was
configured, and the app correctly reported `isThunderstorm=false` for it. Real weather moving is
what makes these runs real.

The sky darkening that this section originally flagged as *not done* was approved separately and is
the next section.

### The storm atmosphere: heavy rain no longer falls out of a summer afternoon

Before this, the forecast reached exactly two things — how many cloud sprites were placed and how
many raindrops fell. The sky's colour, the clouds' colour and the sun's brightness came only from
the theme and the time of day. A thunderstorm at two in the afternoon therefore rendered as bright
blue sky, a full sun with its rays, a band of cloud and heavy rain: four things that cannot all be
true at once.

`StormAtmosphere` is one pure function, `strength(precipitationType, precipitationIntensity,
isThunderstorm, cloudCoverFraction) -> 0..1`, and three transforms driven from that one number, so
sky, clouds and sun can never disagree about how bad the weather is:

| State | intensity | strength | sky darkening | sun left |
|---|---|---|---|---|
| Clear | — | 0.00 | none, bit for bit | 100 % |
| Overcast, dry | — | 0.10 | 4 % | 92 % |
| Light rain | 0.15 | 0.22 | 9 % | 82 % |
| A real 1.8 mm/h | 0.23 | 0.29 | 12 % | 76 % |
| Rain | 0.40 | 0.41 | 17 % | 66 % |
| Heavy rain | 1.00 | 0.75 | 32 % | 39 % |
| Thunderstorm | 0.15–1.00 | 0.79–1.00 | 33–42 % | 35–18 % |

**Why this is not the old density darkening returning.** §27's removal was of *density-driven*
cloud darkening — a slider, blended toward black, when a cloud's colour is the theme's flat
day/night pair and how many clouds there are is not what colour they are. This is different in all
three respects: it is driven by the **forecast**
rather than by a slider, it is a **blend** rather than a palette substitution, and it is derived
from **the theme's own colour** rather than from a fixed storm palette. `dim` pulls a colour toward
its own Rec. 601 luminance and then pulls that luminance down, so a warm sunset stays warm as it
goes dull and dark and two themes never converge on one storm grey. Nothing reaches black.

**Day/night and weather are independent and combine.** The storm blend is applied to the colour the
day/night system has already produced, so `FINAL SKY = NORMAL DAY/NIGHT SKY + WEATHER STORM BLEND`.
The sun keeps its position, its arc and its part in the day blend; only how strongly it is painted
changes, and it never falls below 18 % — a scene with no light source reads as night, and a storm
must stay recognisably daytime. The moon is deliberately left untouched (see the residual
observation below).

**The rain response is not linear, and that was measured rather than chosen.** With a linear rain
term the six-level ramp was walked on a device and its bottom half did not read: light rain was
indistinguishable from a dry overcast sky and a moderate rain looked like a bright blue afternoon
with some drops in it. The cause is upstream — `FULL_INTENSITY_MM` is 8 mm/h, a torrential rate, so
the everyday 1–2 mm/h most forecasts report lands near 0.2 of the range. Rather than change what
the millimetres mean, the intensity is raised to 0.65 before scaling, which lifts the low and middle
of the range while pinning both ends. The "linear" column of the table above would have read 0.11,
0.17 and 0.30 for the three middle rows.

**Lightning came out of the top of the sky.** Reported from a live render — *"i fulmini sono
giganti e escono dalla cima del cielo"* — and both halves were real. The bolt used a constant of its
own, a flat 8 % of screen height, while the cloud band at the default arc starts at 15 % and is
16 % tall, so every bolt was born roughly half a band **above** the cloud it was meant to come out
of; at 26–40 % of screen height it was also taller than the entire cloud layer. The band arithmetic
was duplicated in three call sites and one of them had drifted, so it now lives once in `CloudBand`
and the bolt's origin is *derived* from it: 60 % into the band, past its midpoint, so the bolt's
head is inside the cloud mass. Height is now 10–16 % of screen height, sized against the band. The
timer (4–12 s), the randomisation, the fade and the sprite are unchanged — no second system.

**Verified on a clean Android 17 emulator**, one unchanging Florence scene stepped through every
level by a temporary harness so the levels could be compared against each other rather than against
six different places at six different local times — and because nothing sampled worldwide during the
session was above 4 mm/h, so heavy rain could not have been reached from a real reading at all:

| Case | Kind | Observed |
|---|---|---|
| A Clear, day | controlled | Untouched: bright blue sky, full sun. `strength=0.002` |
| A Clear, night | controlled | Untouched: stars, full moon |
| B Overcast, dry | controlled | White band, sky and sun essentially unchanged. `strength=0.1` |
| C Light rain | controlled | Slightly duller sky, sun slightly dimmed. `strength=0.219` |
| D Rain | controlled | Muted steel-blue sky, mid-grey cloud, visibly dimmed sun. `strength=0.413` |
| E Heavy rain | controlled | Dull grey-blue sky, dark cloud, pale sun, dense visible rain. `strength=0.75` |
| F Thunderstorm, day | controlled | Darkest sky and cloud, sun at its 18 % floor. `strength=1.0` |
| G Rain + sunset | controlled | Low warm sun keeps its position and hue; dusk sky darkened, rain visible |
| H Thunderstorm + night | controlled | Night stays night: stars and moon intact, storm-dark cloud band, rain visible |
| Light drizzle | **real provider** | Kano (11.986, 7.998), Open-Meteo `weather_code: 51, rain: 0.1, cloud_cover: 91` — rendered as the light-rain row above |

Rain stayed clearly visible against every darkened sky; the darker background raises its contrast
rather than lowering it. The bolt geometry was confirmed in a separate observation build with the
strike interval shortened, since a 0.33 s flash on a 4–12 s timer is not something a screenshot
catches reliably; the geometry has no day-phase input, so one verification covers both.

**Residual observation, not changed.** At night the moon and stars are not attenuated, so a
night-time storm is a dark cloud band and rain under a crisp bright moon. The brief scoped the
attenuation to the sun, and dimming the moon risks making night scenes unreadable, so this is
recorded rather than done.

**Cost.** Colour blending and alpha arithmetic on values the renderer was already computing:
`strength()` is one property read and a handful of multiplies once per frame, `dim` is integer
maths returning a primitive. No new texture, no new particle system, no extra draw call, no
per-frame allocation.

### D9 closed: the snow path, seen running on real snowfall

D9 was "snow verified by fixture only". It is now verified on a device against a **live provider
reading**: Mawson, Antarctica (-67.6, 62.87) at 17:15 local, `snowfall: 0.07, precipitation: 0.10,
rain: 0.00, showers: 0.00, weather_code: 73, cloud_cover: 88, temperature: -11.2`. The app produced
`precipitationType=SNOW, precipitationIntensity=0.15, cloudCoverFraction=0.88, isThunderstorm=false`,
and snow fell in the scene.

No code change was needed. What the run confirmed is the separation the design already has, which
was the part actually worth checking:

| Case | Setup | Observed |
|---|---|---|
| B | Live snow, Sunset theme | Snow in the air; **no** roof snow, no tree caps, no winter clothing |
| C | Live snow, Winter theme | Snow in the air **and** roof snow, snow-capped firs, winter clothing |
| A | Winter theme, Live Weather off | Theme's own snow falls; dressing intact; cloud cover drops to the theme's 40 % |
| D | Live rain (the storm case) | Rain, no snow anywhere |
| E | Live snow, any theme | Christmas dressing never appears -- it is its own flag |
| F | Storm location -> snow location | Override switched `RAIN` -> `SNOW` cleanly, one coherent state throughout |

Case B is the one that matters: **a live snowfall does not dress the buildings.** Falling snow is
weather-driven (`PrecipitationType.SNOW`); roof snow, tree caps and winter clothing are
theme-driven (`SceneCustomization.winterColorsEnabled`), a decoration a user opts into on any theme.
Christmas is a third independent flag. Nothing a `LiveWeatherSnapshot` carries can reach any of
them.

The Winter theme does ship with falling snow of its own, deliberately -- "a theme called Winter
whose weather is off is a theme whose central subject the user has to go and find in a menu" -- and
that is a theme default setting two independent fields, not one implying the other.

**Still fixture-only:** nothing. D9 moves to Completed. Snow is covered by a live provider reading
on a device *and* by two captured real responses in the test suite.

Measured: 688 Kotlin unit tests passing, `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK.

---

## v2.14 — the settings screens were the wrong size, the sky was not the forecast's, and a second weather provider

`versionCode = 18`, `versionName = "2.14"`. Tag `v2.14`.

### Live Weather drew rain out of a dry forecast, and clouds out of nothing at all

Reported after the rest of v2.14 was already written and fixed before the tag: with Custom Location
= Florence during real rain, the sky showed no clouds while rain fell. Reproduced from a clean
install on a fresh Android 17 emulator (`sdk_gphone16k_x86_64`, 1080x2424 at 420 dpi) with the
whole pipeline instrumented, and it turned out to be **two independent defects that happened to
compose into one symptom**.

**What the provider actually said.** The request the running app made, and the reply, captured from
logcat at 13:15 local:

```
GET https://api.open-meteo.com/v1/forecast?latitude=43.77925109863281&longitude=11.246259689331055
    &current=temperature_2m,precipitation,rain,showers,snowfall,weather_code,cloud_cover&timezone=auto
200  {"current":{"time":"2026-08-21T13:15","temperature_2m":25.3,"precipitation":0.00,"rain":0.00,
                 "showers":0.00,"snowfall":0.00,"weather_code":80,"cloud_cover":100}}
```

The same request issued directly from the diagnostic tooling returned the same body, which rules
out caching, a timezone mismatch and a stale timestamp: the provider reports `Europe/Rome`,
`utc_offset_seconds: 7200`, and a `current.time` inside the live quarter-hour. Fifteen minutes
earlier the same coordinates had returned `weather_code: 3` with the same four zeroes -- the code
alternates between "overcast" and "slight rain showers" across a dry hour while not one measurement
moves.

**Defect 1, normalisation.** v2.13's mapper put the measurements first and then fell back to the
weather code **unconditionally**. So four measurements reading 0.00 were outvoted by a code, and the
snapshot came out `precipitationType=RAIN, precipitationIntensity=0.15` -- the 0.15 being the
minimum-visible floor, which is what a phantom looks like: drops with no millimetres behind them.
v2.12 had the same bug pointing the other way (code-only, so measured rain under an overcast code
drew a dry sky). The rule is now one sentence, and it cuts both ways: **a measurement, where one
exists, is the answer.** The code only chooses the *kind* when a positive total has no breakdown to
explain it, or decides anything at all when the provider reported no measurements whatsoever -- the
case Open-Meteo's customer endpoint and Visual Crossing's response shape both produce.

**Defect 2, the weather-to-scene step.** The two layers answered the same question differently:

```
drawPrecipitation:  if (liveOverride != null) { ... }    // the theme's own switch is not consulted
drawClouds:         if (!clouds.visible) return          // consulted, and before the override
```

Measured on the device with the theme's cloud switch off and the forecast reporting full cover:

```
SCENE clouds.visible=false clouds.density=0.4 override.cloudCover=1.0 -> drawn=false
SCENE precip.visible=false override.type=RAIN                          -> drawn=true
```

Rain from the forecast, no clouds from the same forecast. The settings screen promises that real
conditions replace the theme's manual cloud setting; that is now true of both layers, via
`engine/LiveWeatherSceneRules`, which is pure and therefore testable. A forecast reporting a clear
sky draws no clouds whatever the theme's switch says, and the coverage field is treated as uniform
whenever no clouds are placed so that precipitation the forecast *does* report is never silently
cancelled by an empty field.

**Verified on the emulator, against live data, per case:**

| Case | Live reading | Scene |
|---|---|---|
| No precipitation, 0 % cloud (Concordia, Antarctica) | all zero, code 0 | clear sky, no clouds, no rain |
| 100 % cloud, no precipitation (Florence) | precip/rain/showers/snow 0.00, code 80 | full cloud band, **no rain** |
| 100 % cloud, rain and showers (Yangon) | precip 0.40, rain 0.20, showers 0.20 | grey cloud band **and** rain |

The Yangon run was made with the theme's cloud switch still off -- the reported configuration --
and rendered coherently.

**Snow was not verified against a real event.** No location sampled had snowfall at the time of
testing, so the snow path is covered by fixtures only. It is not a device observation and is not
claimed as one.

### The bottom-spacing bug was never spacing

Changed in v2.10, changed again in v2.12, still wrong on the device in v2.13. It was fixed this
time by measuring the window rather than reasoning about the padding.

`dumpsys window` on the Pixel 9 (Android 16, gesture navigation, 1080x2424 at 2.625x), with a
settings destination open:

```
mAttrs={(0,0)(1079x2423) gr=CENTER ... fitTypes=statusBars navigationBars captionBar systemOverlays}
Frames: parent=[0,142][1080,2361] frame=[0,142][1079,2361]
```

The window is **2219 px** tall — display minus status bar (142 px) minus gesture bar (63 px) — and
that is right. Its layout parameters ask for **2423 px**, because with `usePlatformDefaultWidth =
false` Compose measures a dialog's content against the display, not against the window frame. So
`Modifier.fillMaxSize()` laid out **204 px of every settings screen outside its own window**, where
the window clipped it.

The last rows were therefore not under the gesture bar; they were outside the window. That is why
a trailing spacer could not fix it however large it was, and why scrolling to the very end still
left the last row cut: the end of the content was off-window.

Instrumented `WindowInsets` readings, logged from inside the running app, corroborate it exactly:

| Where | `safeDrawing.bottom` | scaffold bottom | scroll viewport |
|---|---|---|---|
| Activity (home screen) | 63 px | 24 dp | `top=310 height=2051` → ends at 2361 |
| Dialog (Weather & time), before | **0 px** | 0 dp | `top=168 height=2255` → ends at **2423** |
| Dialog (Weather & time), after | 0 px | 0 dp | `top=168 height=2050` → ends at **2218** |

The dialog's zero is correct — its window already fits the bars. The content was simply sized
against the other window.

**The fix.** The dialog's content is given the height of the area its window occupies: the display
less the insets the activity measures (`SettingsInsets.safeAreaHeight`, pure and unit-tested). The
scaffold inside reserves the dialog's *own* insets, which are zero exactly when the window is
already inside the bars and real values on a device whose dialog window is full-bleed instead — so
both arrangements work without the code asking which one it is on. The trailing spacer is a 24 dp
constant again and carries no inset.

**Verified by scrolling to the end and reading positions off the accessibility tree.** Weather &
time's last row moved from y = 2380 (inside the gesture bar's 2361–2424 band) to y = 2238. Also
checked at the end of the scroll on World & scene, Themes, Advanced & about, a form sub-screen,
the home screen, and Weather & time with the keyboard open.

### Live Weather: what was actually broken

The reported bug was that switching Live Weather on did not fetch immediately. **It did.** Measured
on the device with the preference write and the request both logged: the write landed at
11:45:12.166 and the request started at 11:45:12.183 — 17 ms — with a custom location, and the
same within a millisecond with phone location. v2.13's wake-up path was working.

What the measurement *did* find is a different defect, in the same area. Switching Location from
Custom to Phone left `lastLocationFix` holding the **custom** coordinates: `maybeStartLocationUpdates`
returns early when a fix is already held, `hasFixLocation` was set by both sources, and nothing
invalidated a fix when the source changed. Live Weather went on fetching Florence's weather with
Phone selected, indefinitely. A fix now belongs to the `LocationSource` that produced it, and a
change of source invalidates it.

The immediate-refresh rule itself was also widened and made testable. v2.13 compared the toggle and
Open-Meteo's key, which was a complete list at the time; `LiveWeatherInputs` now names every input
a fetch depends on — the toggle, the provider, and both providers' keys — as one pure function with
a test over it, because entering the Visual Crossing key is exactly the change that turns "no
requests are being made" into "requests can be made" and would otherwise have been missed.

### A second provider

`WeatherProvider` is now an interface, and the pipeline is
`provider → normalised WeatherObservation → WeatherRepository → cache/scheduler → scene`. A provider
owns its endpoint, its query and its response shape and nothing else.

`WeatherObservation` carries temperature, cloud cover, total precipitation, rain, showers,
snowfall, a normalised `WeatherCondition`, a timestamp and the source. Every field is nullable,
because **"not reported" and "reported zero" are different facts** and the mapping depends on
telling them apart — Visual Crossing has no showers category at all, and reading its silence as
"no showers" would reintroduce v2.12's bug from the other end.

Open-Meteo's mapping is unchanged in behaviour: snowfall first, then rain-or-showers, then the
code, then a positive total. Visual Crossing reports one `precip` figure plus a `preciptype` array,
so millimetres are attributed to rain only when it says rain is falling, and its condition is read
from the icon slug, the `conditions` text (the only place a thunderstorm appears on the free
`icons1` set) and `preciptype` together.

Visual Crossing **requires a key** — its free plan is 1,000 records a day and has no anonymous
tier. Without one the provider returns `MissingApiKey` and sends nothing; the settings screen says
so and offers the way back to Open-Meteo. No key for it is compiled into the app, added to a
workflow, or logged, and the field is masked.

**No silent fallback between providers.** A failure is reported as a failure and the selection
stands.

### What was verified on the device, and what was not

Verified on the Pixel 9 through the Android MCP bridge: every bottom-spacing case above; the
provider selector persisting and switching; the missing-key state; entering a key forcing an
immediate fetch; that fetch reaching the real Visual Crossing host and being rejected, producing
the `STALE` banner with Visual Crossing still selected; and switching back to Open-Meteo producing
an immediate successful fetch.

**Not verified:** a successful Visual Crossing response. No account was available, so its parser is
tested against fixtures built from the published field list rather than captured from the wire, and
the provider is **not** end-to-end verified. Nor is snowfall: no sampled location was snowing during
testing, so that path rests on fixtures too.

Measured: 636 Kotlin unit tests passing, `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK, the settings work seen running on a Pixel 9, and the Live Weather work seen
running on a clean Android 17 emulator against live Open-Meteo data.

---

## v2.13 — the update button updates, and showers count as rain

`versionCode = 17`, `versionName = "2.13"`. Tag `v2.13`.

### The update dialog's main action opened a browser

v2.11 built a download -> verify -> install path and then left the update dialog's primary button
pointing at the GitHub release page, because that button predated the flow. So the feature existed
and almost nobody would have found it: it was reachable only by going to Advanced & about and
starting a check again.

Three actions now, with the primary one doing the primary thing: **Remind me later** (closes,
snooze unchanged), **Check project page** (opens the release, and that is all it claims to do), and
**Install update**, which drops straight into the download with the release already selected.

### Install permission

`ACTION_MANAGE_UNKNOWN_APP_SOURCES` opens PaperScrape's **own** per-app page when it is given a
`package:` URI, and that is what the app sends. It is launched for a result purely to get a
callback on return -- the screen reports nothing useful in its result code, so the permission is
re-read instead. Granted, the interrupted install resumes and Android's installer opens; not
granted, the screen says what is still missing rather than silently doing nothing.

### The Florence rain: what the API actually said

Checked against live responses for 43.7696, 11.2558 (Europe/Rome, +7200 s, model elevation 65 m):

| Local time | precipitation | rain | showers | snowfall | code | cloud |
|---|---|---|---|---|---|---|
| 2026-08-21T09:00 (current) | 0.0 | 0.0 | 0.0 | 0.0 | 3 | 100% |
| 2026-08-21T00:00 | 0.1 | **0.0** | **0.1** | 0.0 | 80 | 100% |
| 2026-08-21T13:00 | 1.0 | **0.0** | **1.0** | 0.0 | 80 | 100% |
| 2026-08-21T14:00 | 0.6 | **0.0** | **0.6** | 0.0 | 61 | 100% |

Over 72 hours the grid square had 3 wet hours and near-permanent 100% cloud -- which is exactly the
shape of the report: clouds present, rain absent.

**The finding that matters: Open-Meteo files a Florence shower under `showers` and leaves `rain` at
0.0.** The app was not reading either field -- it mapped `weather_code` and used `precipitation`
for intensity -- so case **B** from the brief was ruled out by inspection, and codes 80-82 were
already mapped, ruling out **C** for showers.

What was left is a real hole plus two things no code change reaches:

- **The hole (a variant of A):** `precipitation > 0` under a non-precipitation code produced no
  rain at all. That happens when a shower ends inside the reporting hour. Fixed: measurements now
  decide *that* something is falling, `snowfall`/`rain`/`showers` decide *which*, and the code is
  the fallback. `precipitation`, `rain`, `showers` and `snowfall` are now all requested.
- **Staleness (D)** is real but bounded and unchanged: the service refreshes at most hourly, so a
  shower starting just after a fetch can be up to an hour late. Left alone deliberately -- cutting
  the interval multiplies network calls for a wallpaper.
- **(E) cannot be excluded and is likely part of it.** The observation has no recorded timestamp,
  so it cannot be matched against a specific response; and a model grid square is not a window. A
  convective shower it did not resolve will not appear however the response is read.

### Measured

548 Kotlin unit tests passing (v2.12: 533), `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK.

**Not verified on a device.** No Pixel 9 was available, so the three-button dialog, the permission
deep link, the resumed install and the weather mapping have not been seen running. The updater in
particular still has never been exercised end to end against a real release.

---

## v2.12 — the sky after sunset, two crowds, and one honest slider

**Stable.** `versionCode = 16`, `versionName = "2.12"`. Tag `v2.12`.

### The moon was never early; the sky was wrong

The Pixel 9 report was "the moon starts rising while it is still nearly daylight, around 19:00".
The moon's timing turned out to be correct -- `isSunVisible` flips exactly at sunset, and for
Florence in late August the location-aware sunset computes to 20:02 against an almanac 20:07 -- but
`SunPositionCalculator.compute` gave the night arc a `dayBlend` of `1f - smoothEdge(arcT)`, which
is 0 in the middle of the night and **1 at both of its ends**. The day arc ended at 0 at that same
instant. So the blend the entire scene is coloured with jumped from full night to full day across
sunset and then slid back down over 12% of the night -- more than an hour in summer -- with the
moon already climbing through it.

Both arcs now meet at `TERMINATOR_BLEND = 0.5`: full day easing to half-light at sunset, easing on
to full night, and symmetrically at dawn. Continuous, with the sun's and moon's own timings
untouched.

One existing test asserted the old behaviour outright ("sunrise should start from full dark") and
was updated deliberately, with the reason recorded in place: it had pinned the bug.

### Sun/Cloud Height

Three complaints, one cause each, all real:

- **"The slider doesn't move the clouds."** It did, by `0.08 + (1 - h) * 0.15` of screen height --
  a total travel of about 7% across the entire range. Now `0.06 + (max - h) * 0.5`, which spans a
  range the eye can see, still clears the horizon at its lowest, and lands within a few pixels of
  the old position at the default so existing scenes are not rearranged.
- **"At 60% the sun is too high."** The slider printed the stored value as a percentage, and the
  stored range is 0.1..0.6 -- so "60%" was the maximum, not the middle.
- **"It's 0-60 instead of 0-100."** It now shows 0-100% mapped onto that same stored range. The
  scale the renderer reads is unchanged, so **nothing saved needs migrating**.

The semantics were confirmed from the code rather than assumed: one value feeds both the celestial
arc (`drawCelestialBody`) and the cloud band (`drawClouds`, and the precipitation origin that hangs
off it), which is answer (C)-with-(B) from the brief -- a real single parameter whose cloud half
was too weak to notice.

### Day and night crowds

`people.density` is read in exactly one place, `SceneObjectRenderer.drawPeople`, and governs
pedestrians only -- drivers, passengers and lit-window figures are drawn elsewhere and are
untouched. A dedicated `peopleNightDensity` sits beside it (not a second density on every
`ObjectVariantConfig`: no other category has a population that plausibly depends on the hour), and
the renderer crossfades between the two with the scene's own `dayBlend` rather than switching at a
threshold, so the street empties over the length of dusk instead of between two frames.

**Migration:** the preference is absent for every pre-v2.12 install, and absent reads as "use the
daytime value" (`PeopleDensity.resolveNightDensity`). The scene after the upgrade is the scene
before it. Saved custom themes without the key fall back to their own daytime density for the same
reason, and `resetCategory(PEOPLE)` clears the new key too -- otherwise "reset to default" would
have left the night population wherever it had been dragged.

### Bottom spacing, again

v2.10 asked one window for the bottom inset and floored the result at 48 dp. That was not enough on
the device, and raising the constant would have been the wrong fix: the problem is that **neither
window is reliable alone**. A settings destination is a `Dialog` with a window of its own, and
depending on whether it fits system windows, either it reports the gesture inset and the activity's
figure is stale, or it reports zero while the activity holds the real one. The spacer now takes the
larger of the two and the floor is only reached when neither knows.

### Measured

533 Kotlin unit tests passing (v2.11: 505), `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK.

**Not verified on a device.** No Pixel 9 and no emulator were available for this batch, so none of
the four fixes has been watched on a screen -- including the bottom spacing, which is the second
time it has been changed without being seen. The sun/moon and people work is pinned by unit tests
over the pure arithmetic (blend continuity across both terminators, real-location sunset against an
almanac figure, the DST trap, slider round-trips at every percent, crossfade endpoints and the
migration), but arithmetic is not a screenshot.

---

## v2.11 — updating from inside the app, and one preview system

**Stable.** `versionCode = 15`, `versionName = "2.11"`. Tag `v2.11`.

### CHECK -> DOWNLOAD -> VERIFY -> INSTALL

The update checker could only ever say "there is a newer release" and open the release page, which
left the user to find the APK among the attachments, download it and install it by hand. The whole
flow now runs in Advanced & about.

The pieces are deliberately split so that the parts which fail silently are the parts that are
testable without a network or a device:

- `ReleaseAssets` picks the APK and its checksum **by exact name** -- `PaperScrape-<tag>.apk` and
  the same name plus `.sha256`, which is what `.github/workflows/android-build.yml` publishes.
  Not "the first asset ending in .apk": Gradle's own output is `app-release.apk`, the workflow
  renames it for a reason, and a loose rule would also match an asset attached to another tag.
- `ChecksumFile` reads the `sha256sum` format and a bare hash, and its `matches` refuses to pass
  on a missing, short or malformed value. An install that proceeds because verification was
  *skipped* is worse than one that does not happen.
- `ApkDownloader` streams the file to the app's cache while hashing it in the same pass, so the
  digest describes exactly what was written and a 19 MB APK is not read twice. A truncated
  transfer, a mismatch, or any failure deletes the file.
- `ApkSafety` reads the downloaded package's own id and version code and refuses anything that is
  not this app, or is not newer. The comparison that decided to *offer* the update was made against
  release tags; this one is made against the bytes on disk, which is a different claim.
- `ApkInstaller` hands the verified file to Android through a `FileProvider` content URI scoped to
  `cache/updates` alone. **There is no silent install path and no attempt to find one**: Android
  shows its own confirmation, and declining is a normal outcome that changes nothing.

A release without a checksum is not installable in-app at all, by design; the user is sent to the
release page and told why. `Check for updates automatically` is unchanged and still only reports.

New permission: `REQUEST_INSTALL_PACKAGES`, used only after the user taps through the flow. No
secret was added, and the signing config and CI workflow are untouched.

### One preview system

Roadmap priority 7, closed. The strip at the top of World & scene still magnified the size table
with per-item fitting factors so a house, a tree and a tower of very different heights would fit a
120 dp band -- honest about colour and nothing else, and sitting one tap from the gallery's mini
scenes it read as a leftover.

It is now the **same** `ThemeScenePreview`, at the same 4:3 shape and the same uniform scale, both
call sites going through the new `ThemePreviewGeometry`. Neither applies a crop, a zoom or a
fitting factor of its own, and `ThemePreviewGeometryTest` pins that: the ratio of the two scales is
exactly the ratio of the two widths, and identical inputs produce an identical scene whichever
screen asks.

The one thing World & scene adds is a `forceNight` override on `ThemePreviewScenes.forTheme`,
because half the values edited on the screens below it are night colours and a preview fixed at
midday cannot show them. The gallery never passes it, so cards are unaffected.

### Measured

505 Kotlin unit tests passing (v2.10: 469), `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK.

**Not seen rendering, and the updater has not been run end to end.** No device was available for
this batch. The download, verification and install hand-off are covered by unit tests over their
pure parts -- asset selection, checksum parsing and comparison, install verdicts, version
comparison -- but no APK has actually been fetched from a release and installed. That is the first
thing to try on the Pixel 9, and it can only be tried properly once v2.11 itself is published and
a v2.12 exists to update *to*.

### The v2.10 bottom spacing, not verified

The device pass asked for confirmation that the v2.10 bottom-spacing fix holds. It could not be
given here: no device. The screenshot supplied with the request shows the theme gallery mid-scroll
(its top row is cut too), so it is not evidence either way. The rule is unchanged and remains
`SettingsInsets`: system inset + 24 dp, floored at 48 dp, applied by the two shared shells. Nothing
was adjusted on a guess.

---

## v2.10 — a city by name, and the bottom of the page

**Stable.** `versionCode = 14`, `versionName = "2.10"`. Tag `v2.10`.

Two contained changes found on the v2.9 device pass. Nothing in the wallpaper, the themes, the
previews or the weather logic was touched.

### The last row was under the gesture bar

Scrolling to the bottom of a settings screen left the final row partly hidden on some screens and
not others. The cause is structural rather than cosmetic: every destination is a full-screen
`Dialog`, and a dialog has its own window. Unless that window is told otherwise it fits system
windows itself, so `WindowInsets.safeDrawing` measured *inside* it can report zero while the
content still runs to the bottom of the display — and each screen was left carrying whatever
padding it happened to have.

`SettingsInsets` is now the one rule: the system's own bottom inset plus 24 dp of breathing room,
floored at 48 dp. The inset is read once in the **activity's** composition, where it is real, and
passed to the dialogs through `LocalSettingsBottomInset`; the two shared shells apply it, so every
screen ends the same way and no screen sets its own. The shells also gained `imePadding`, which is
what keeps the new search field and its results above the keyboard.

The floor is the part that matters: it is what makes the fix hold on a window that reports no
inset at all, which is the case the bug came from.

### Custom location by city name

A user setting up Live Weather had to know their latitude and longitude. There is now a search
field above the coordinate fields: type a name, pick a result, and the same three values a manual
entry writes — latitude, longitude, label — are written through the same `setCustomLocation` call.
**Downstream nothing can tell the two apart**, which is the whole design: the search is a more
convenient way to fill the existing fields, not a second location system.

**Provider: Open-Meteo's geocoding API** — the same provider Live Weather already uses. It needs no
API key, adds no library, and reuses `WeatherRepository`'s exact networking style
(`HttpURLConnection`, fixed timeouts, every failure becoming a value). No secret was added and the
CI workflow is unchanged. The device's own `Geocoder` was the alternative and was rejected for
forward search: `getFromLocationName` is optional on Android, absent without Play services, and
populates its region fields inconsistently — which is exactly the information needed to tell three
Springfields apart. Reverse geocoding stays on the platform `LocationLabelResolver`, which works
offline and has no reason to move.

**Ambiguity is the user's to resolve.** Every place sharing a name is listed with its region,
second-level division and country, and nothing is auto-selected — not even when there is a single
result. Choosing for the user is how the wrong continent's weather ends up on the wallpaper.

**Nothing is written until a result is tapped.** A failed search, an empty one, or a cancelled one
leaves the existing custom location exactly as it was, and "couldn't reach the search" and "no such
place" are separate messages because they are separate answers.

Requests are bounded by a 500 ms debounce plus an explicit search action on the keyboard, and an
8-entry in-memory cache so backspacing a letter and retyping it does not re-ask. The cache is not
persisted.

### Measured

468 Kotlin unit tests passing (v2.9: 442), `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK.

**Version note.** This is the first release whose minor number is two digits. `AppVersion` compares
parsed integers, so 2.10 is correctly newer than 2.9; a string comparison would have read it as
older and silently stopped offering updates. There is now a test saying so.

**Not seen rendering.** No device or emulator was available for this batch. The bottom-spacing rule
is pinned by unit tests and the search is verified through its parser, its cache and its query
rules — but neither has been watched on a screen.

---

## v2.9 — the settings rebuilt, and previews that show the theme

**Stable.** `versionCode = 13`, `versionName = "2.9"`. Tag `v2.9`.

A UI release. The renderer, `SceneSpace`, the sprite library, the themes, the calendar, Live
Weather, the reset behaviours and every stored preference are untouched; what changed is how the
settings present them, and what a theme's gallery card draws.

### The settings were one list

v2.8's settings were a single `SettingsScreen.kt` of 2,414 lines producing one scroll about four
and a half screens tall, plus two full-screen dialogs. Three things were wrong with it, and none
of them was a matter of taste:

1. **Weather had no section**, and worse, it lived *inside* the "Follow real time" branch — so
   switching the clock to a fixed hour removed the two location toggles, Live Weather and the API
   key field from the screen entirely, with nothing saying where they had gone.
2. **Seasonal Decorations expanded six category blocks inline** — about sixty controls in one
   scroll, with each block's season named in a heading two thousand pixels above it, and the
   Flowers switch sitting under the *Christmas* heading.
3. **The active theme was never named.** The home preview drew it and said nothing.

### What replaced it

Five destinations, all drill-downs from a home screen that holds no settings of its own: **Weather
& time**, **Seasons & decorations**, **World & scene**, **Advanced & about**, and the theme gallery
from Home's Theme row. One file each, plus `SettingsComponents.kt` for the shared Material 3
vocabulary. Every v2.8 control is present.

Two pairs of mutually exclusive booleans became one choice each — location source
(Off / Phone / Custom) and seasonal palette (None / Autumn / Winter). **Presentation only.** The
flags are the same flags, written by the same setters, whose own exclusivity is what makes three
states out of two booleans; `SettingsUiModelTest` pins both mappings in both directions, including
that the enum ordinals match the segmented labels, because indexing by ordinal is how a reorder
would silently swap two settings.

Live Weather now stays visible and **disabled**, with the reason, when the clock is fixed —
the disabled pattern v2.8 already used when no location was set, so nothing previously unreachable
became reachable.

### Material 3, completed

`PaperScrapeTheme` defined 4 roles out of roughly 30, so switches, inactive slider tracks,
containers and dialog surfaces fell back to Material's baseline violet. The scheme is now complete
in both light and dark, every role a tone of the four colours the app already had. The emoji used
as section markers are gone; the icons are Material Symbols from `material-icons-extended`, which
was already a dependency.

### Theme previews

`ThemeScenePreview` drew a sky gradient, a circle for the sun and a rectangle for the hills. It was
honest about the palette and silent about everything else, which left six of the twelve built-in
themes looking alike in the gallery.

A card now draws a real mini scene from the shipping sprites at the renderer's own part offsets,
with the theme's own palette and the customization the theme actually carries.
`engine/ThemePreviewScene.kt` holds the description — no Android type beyond resource ids, so what
a preview contains is a unit-testable question — and `ui/ThemePreview.kt` replays it through
`CanvasSceneTarget` and the same `SpriteBlitter` the wallpaper uses.

The rule that keeps it honest is that **every object is conditional on the flag the wallpaper
reads**. No lake where `lake.visible` is false; no sailboats or dolphins on the tundra, which turns
both off; palms only on the two themes whose tree slots map to `PALM_TREE`; the carved moon tinted
with `PaperRenderer.HALLOWEEN_MOON_COLOUR` rather than the theme's `moonColor`; the fall canopies
in `fallLeafColorFor`'s own palette. Where the scene has no sprite at all — parasols are drawn
procedurally — the preview shows nothing rather than standing in a different sprite. Nineteen tests
pin this, in both directions.

It is static: no GL context, no animation, no timer, no per-card bitmap. The description is built
once and kept by `remember`; pixels come from the process-wide `SpriteCache`; a card is roughly
twenty blits on composition and on scroll, and nothing at rest.

One thing the previews revealed rather than introduced: the default mountain colours are green
(`#4CAF7C` / `#3E8F68`), which is what the wallpaper has always drawn on the themes that do not
override them. The preview shows it because it is true.

### Measured

442 Kotlin unit tests passing (v2.8: 407) across 31 classes, `lintDebug` **0 errors / 40 warnings**
— one below v2.8's 41, because two pre-existing `UseKtx` warnings were closed along the way.
`assembleDebug` produced an APK, so resource linking, dexing and packaging are proven for this
source tree and not only compilation.

**Not seen rendering.** No device or emulator was available. The previews were verified by dumping
the shipping `ThemePreviewScenes` output and rasterising it against the real PNGs, which checks the
composition the code produces but is not the app drawing on a screen.

---

## v2.8 — the buildings measured against a person

**Stable.** `versionCode = 12`, `versionName = "2.8"`. Tag `v2.8`.

v2.7 raised the shops' metres and left their single-storey artwork, which multiplied every opening
the drawing contains. Measured in metres as a 1.9 m person reads them, v2.7 shipped a **4.20 m
restaurant door**, a **4.28 m bar door**, a **5.25 m sign** and a **0.86 m tower window**. That is
the defect this release corrects, and the correction is not "make the shops smaller".

### metres and spriteUnits are one decision, not two

Every element's drawn size is `units x metres / spriteUnitsTall x 45`. Raising `metres` alone
scales the openings with the building, which is why v2.7's shops looked like toys enlarged rather
than buildings. **Both numbers moved together here**, and the artwork moved with them.

| | v2.7 | v2.8 | door reads |
|---|---|---|---|
| `HOUSE_SMALL` | 6.4 m / 110 u | **5.76 m / 110 u** | 1.99 m |
| `HOUSE_LARGE` | 7.6 m / 145 u | unchanged | 2.36 m |
| `BAR` | 8.4 m / 55 u | **7.7 m / 92 u** | 2.34 m |
| `RESTAURANT` | 9.0 m / 60 u | **8.2 m / 96 u** | 2.39 m |
| `TOWER` | 21 m / 196 u | **16.8 m / 196 u** | 2.31 m entrance |
| `FIR` | — | **9.3 m / 122 u** | — |

`HOUSE_SMALL` takes the large house's own metres-per-unit exactly, which is what makes their
windows the same size; the window also came down 6 units so the two sills line up. The facade went
86 to 96 units wide, and the roof and eaves 96 to 106 — width is artwork, not a `SceneSpace`
number.

`FIR` shares `TREE`'s 122 units so one metre governs both: a fir cannot drift out of scale with the
wood it stands in, whichever is redrawn.

### The shops got a storey, because that is the only way to be taller without bigger doors

`bar_wall` 90x55 to **90x92** and `restaurant_wall` 100x60 to **100x96**: a residential storey over
the shop front, a string course at the division and a cornice at the parapet, with the shaded
return running the full height so it reads as one mass. The upper windows are
`house_shared_window`, so a shop's first floor cannot drift from a house's. `bar_sign` came down
from 36 to 24 units and off the roofline onto the facade.

### The tower

`skyscraper_wall` was a `userSpaceOnUse` pattern at an 18-unit pitch with 8-unit windows —
eighteen storeys in 150 units. It is now **four rows of four 14-unit windows at a 27 pitch**, which
at 16.8 m is a 1.2 m window on a 2.3 m floor, over a 32-unit glazed hall.

**The grid stops 18 units clear of the hall**, and that blank course is deliberate: a row of
windows sitting on the door head made the entrance read as one more pane. `skyscraper_entrance` is
a new 32x32 sprite — recessed frame, awning lintel, two glazed leaves on a central mullion.
Everything in it reaches the sprite's bottom edge: an earlier cut ended the leaves five units short
over a threshold slab, and on a device the canopy then looked like a raised floor with a door
standing on it. **The doors meet the ground the building and the people meet.**

### Firs, and lights that are actually scattered

`standsAsFir` hashes a tree's own seed and takes about one in three. Not a count, which would need
state to distribute, and not a position, which would put the firs on a line. The same third every
frame — a wood that reshuffled itself while you watched would be worse than no firs.

`litWindowChosen` ranks a facade's windows by `hashWindow(seed, index)` and lights the first N.
Deterministic, different between two buildings side by side, steady frame to frame, and **N is a
cap**, which is what holds the draw calls where they were: twelve on a tower, as before, but spread
over all sixteen windows instead of filling the three lowest floors.

### What the previews caught

Two defects, both found by looking rather than by testing. The fir was **upside down** — widest
tier at the top — and `tree_fir_snow` was an achromatic white mask blitted untinted, which is the
tint-class defect `DESIGN_NOTES` decision 25 exists to prevent; it now carries a cool shadow under
the white, the recipe `tree_canopy_snowcap` already uses.

### Canvas against facade

Checked as asked: `bar_wall` is 270x276 px = 90x92 units with a full content box, `restaurant_wall`
300x288 = 100x96 with a full content box. In both the canvas *is* the facade — there is no
difference to document.

### Verification

```
Release identifier:            v2.8
Verification level:            3
Tests run:                     yes -- 407 Kotlin tests, 0 failures
Lint run:                      yes -- 41 warnings, 0 errors
Python tooling suite:          yes -- 96 tests, 0 failures
Asset validate:                yes -- 0 failures, 125 entries, anchors 125/125
Normalisation:                 yes -- 73 targets, none pending, 15 excluded by decision
Previews:                      the full scene and the Christmas scene built from the shipped
                               PNGs at the shipped origins under SceneSpace's own projection,
                               plus per-asset checks of the tower facade, the entrance and the
                               fir. **Not** OpenGL frames.
APK build run:                 no
Maintainer-side verification required: yes, on the Pixel 9.
```

### Known limitations

- **Nothing was seen rendering.** The previews use the shipped sprites and the real projection,
  but they are compositions.
- `skyscraper_wall_lit` is kept in step with `skyscraper_wall` by hand: the two grids are written
  out separately and a change to one has to be copied to the other.
- The fir's presents reuse `gift_box`/`gift_ribbon` at a third of their size rather than having
  artwork of their own.
- `restaurant_sign` was left at its old size; only the bar's sign was cut down.


## v2.7 — two device-pass bugs, flowers, lights on the buildings, and the balloons removed

`versionCode = 11`, `versionName = "2.7"`. Tag `v2.7`.

### The snow was cut for a roof that no longer existed

v2.6 widened the small house's roof from 80 local units to 96 and left `house_small_roof_snow`
drawn on the old one, so the drift stopped short of both eaves and sat off-centre against the
ridge. Re-authored by mapping every x through the roof's own change -- left half [0,30] to [0,38],
ridge [30,50] to [38,58], right half mirrored -- so the crest, the shadow inset and the scalloped
lower edge are the approved shape and only the roof under them moved. Canvas 186x99, origin -31.

### Leaves were never told where the trees are

`drawFallingLeaves` computed `x = xFraction * screenWidth` at a fixed `fallStartY`. Nothing tied a
leaf to a tree, so most of them appeared in clear sky. The positions were not *available*: a
crown's screen position is depth, ground line, effective scale and wrap-tile offset combined, and
all four resolve inside `SceneObjectRenderer.draw`. It now records the frame's crowns in three
parallel `FloatArray`s and a count -- fixed ceiling, no per-frame allocation -- and each leaf takes
one, offset across *that crown's own half width*. No trees on screen means no leaves, which is the
right answer rather than a fallback.

**Rendering path, since it was asked for explicitly.** The leaves are `canvas.drawOval` calls on
the `SceneCanvas` seam, so they already go through the GL backend like everything else: not a
particle system, not a separate Canvas path. **No draw call was added** -- the count is still
`FALLING_LEAF_POOL_SIZE` -- and no geometry or texture is generated. The change costs one modulo
and two array reads per leaf.

### Flowers

`flowersEnabled` is a plain boolean, not an `ObjectVariantConfig`, and that is the decision worth
recording: every other decoration carries visibility, density and a day/night colour pair, which is
right for a snowman and wrong for a meadow. `ground_flowers` is one clump of three kinds at three
sizes on a single canvas -- one blit per clump rather than one per bloom -- and it is fixed art.
Spring turns it on by default; every other theme leaves it off and the user owns it either way.

**The scatter was wrong the first time and the preview is what caught it.** Banding depth by the
clump index as well as x correlated the two and laid every clump on one straight diagonal. Only
the horizontal slice is stratified now; depth is its own hash.

### Lights under the windows, not beside them

The existing `drawChristmasLights` scatters bulbs around a canopy's ellipse, which is a tree's
shape. `drawWindowLights` draws a slack two-segment cord between two points on a window's own sill
and hangs four bulbs off it, at the cord's own height at each point. Geometry only, no new sprite,
and the window is not touched. Hung on: both small-house windows, all four large-house windows, the
restaurant frontage, the bar's two bays, and the tower's three lowest floors -- the tower's windows
are painted into its wall, so the strings follow the grid the artwork states (four columns of 9 at
an 18 pitch from 4.5) rather than a guess.

### Balloons, removed rather than hidden

`SceneObjectType.BALLOON`, `SceneVariant.BALLOON`, `ObjectCategory.BALLOONS`, the
`SceneCustomization.balloons` field and its defaults, the New Year preset, the structural
comparison, the theme JSON read and write, the prefs read, the settings section, the draw function,
the candidate generator, the random-scene type list, both sprites, both SVG sources and the two
registry entries. `SceneCustomizationStructureTest`'s reflected field count went 13 to 12, which is
the guard that would have caught a half-removal. A saved theme that still carries a `balloons`
block loads and comes back without one.

### The shops were measured as a domestic storey

`RESTAURANT` was 5.2 m and `BAR` 4.8 m against a 6.4 m cottage, so a parade of shops read as
outbuildings *behind* the houses. Now 9 m and 8.4 m -- a commercial storey is taller than a
domestic one and carries a parapet -- and `TOWER` from 17 to 21 m so it still out-tops them.
`SceneSpaceTest`'s ordering list was re-derived rather than relaxed, and two direct relations were
added, because a chain can be satisfied by moving either end.

### The skyscraper grid was flush left

The defect was in the asset, not the renderer. The window field used a `patternUnits="userSpaceOnUse"`
pattern whose tile starts at the document origin rather than at the field rect, so the columns
landed at 4, 22, 40, 58 and the field was clipped at 70 -- no margin on the left, none to spare on
the right. Four columns of 9 at an 18 pitch span 63 on a 72-unit front face, so 4.5 either side
centres them. Fixed in `skyscraper_wall` and `skyscraper_wall_lit` together.

### Release artefacts carry their version

`PaperScrape-${GITHUB_REF_NAME}.apk`, taken from the ref rather than written down, so v2.8 and v3.0
name themselves. The rename happens before the checksum, so the name inside the `.sha256` is the
name of the file you downloaded. Signing and keystore untouched.

### Verification

```
Release identifier:            v2.7
Verification level:            3
Tests run:                     yes -- 407 Kotlin tests, 0 failures (was 395)
Lint run:                      yes -- 41 warnings, 0 errors
Python tooling suite:          yes -- 96 tests, 0 failures
Asset validate:                yes -- 0 failures, 122 entries, anchors 122/122
Normalisation:                 yes -- 72 targets, none pending, 13 excluded by decision
Sprite memory:                 14.88 MB decoded across 122 PNGs
Previews generated:            building hierarchy on one ground line; flowers ON/OFF on spring
                               ground; Christmas lights on small house, large house, restaurant
                               and tower; six consecutive fall-leaf frames; the skyscraper grid;
                               the small house in winter. All from the shipped PNGs at the real
                               SceneSpace heights. **Not** OpenGL frames.
APK build run:                 no
Maintainer-side verification required: **yes**, on the Pixel 9, before any release.
```

### Known limitations

- **Nothing was seen rendering.** The previews use the shipped PNGs and the real height table, but
  they are compositions, not engine output.
- The fall-leaf preview reconstructs the renderer's spawn rule in Python rather than running it;
  what it verifies is that the rule puts leaves on crowns, not that the Kotlin executes it.
- The bar's hanging sign is placed by hand in the hierarchy preview and is not at its call-site
  origin; the wall heights either side of it are.
- `drawWindowLights` adds up to 12 small draw calls to a tower and 4 to a house, and only while
  the Christmas flag is on.


## v2.6 — the outline moved outside, and the small house got its facade

`versionCode = 10`, `versionName = "2.6"`. Tag `v2.6`.

A device pass on v2.5 approved the world scale, Spring, the Halloween palms and the carved moon,
and rejected two things. Both are corrected here; nothing else was reopened.

### The rim failed because every check looked at one sprite at a time

v2.5's readability edge was clipped to the **inside** of every shape, so its thickness was a
function of what each shape happened to overlap. On a still that is invisible. Across the walk
cycle, where the arms and legs move and the overlaps move with them, the band appeared and
vanished between consecutive frames — and it passed every test there was, because the tests were
per-sprite and the defect is per-sequence.

**The replacement draws the whole sprite a second time underneath itself**, filled and stroked in
the outline colour. The strokes of overlapping shapes merge into one contour and the normal fills
on top hide every internal seam, so what is left is a continuous band of one width around the
**union** of the artwork — and the union is the only thing it depends on. Baked into the PNG; no
runtime draw call was added.

An outer outline grows the silhouette by half the stroke on every side, which is what an outer
outline *is*. The registry was re-measured and the affected anchors and origins followed it, the
same way a crop is handled.

### Tinted sprites cannot carry a dark edge, and that turned out to be an advantage

`SpriteTintClassTest` requires every tintable sprite to be a colourless mask averaging at least
220 — the runtime multiplies it by the user's colour, and a dark or hued band would compound with
it. The first pass gave walls, vehicles and animals the same dark edge as the people and failed
both of those assertions.

The fix is better than a special case: tintable sprites get a **light neutral grey** (`#dcdcdc` to
`#e4e4e4`), which `MULTIPLY` turns into a slightly darker version of whatever colour the user
chose. Fixed-art sprites — the people above all — carry their dark edge directly. Two treatments
because the two classes of sprite reach the screen by different arithmetic, not because one looked
nicer.

### The tests now look at the sequence

`tools/assets/tests/test_outline.py`, seven tests over the eight walk cycles (four people, two
seasons, three frames each): the frames of one cycle must agree on the outline colour; the band
must run all the way round each frame's own silhouette; its thickness, measured as the share of
the silhouette it occupies, may not vary more than 6% across a cycle; the still window and car
occupants must match the walkers; the marker must be present in each source; the band must be
darker than the interior; and every outlined sprite must match its registry geometry.

The middle one is the assertion the rim would have failed, and the reason it is stated as a
property of a cycle rather than of a sprite.

### The small house needed facade, not height

The height was right — `SceneSpace.SceneVariant.HOUSE_SMALL` governs it and v2.5 had already
settled it at 6.4 m. What was wrong was the width: 70 local units of wall with the two windows
reaching to within two units of each edge, which at the size a Pixel 9 draws it read as a pair of
windows about to fall off the front.

The wall is 86 units now and the roof and eaves 96, keeping the same five-unit overhang, so there
are **six units of facade either side of a window instead of two**. Every origin moved with it:
wall, roof, roof snow, trim, both windows, the lit glass, the occupant, the planter, the flowers,
the door and the porch light. Pitch, door and window count are untouched.

### Verification

```
Release identifier:            v2.6
Verification level:            3
Tests run:                     yes -- 395 Kotlin tests, 0 failures
Lint run:                      yes -- ./gradlew lintDebug, 41 warnings, 0 errors
Python tooling suite:          yes -- 96 tests, 0 failures (was 89; seven are the new
                               animation-sequence checks)
Asset validate:                yes -- 0 failures, 123 entries, anchors 123/123,
                               18 variant groups distinct
Normalisation:                 yes -- 74 targets, none pending, 12 excluded by decision
Visual mockup:                 yes -- all eight walk cycles at Pixel 9 size (85 px tall) and
                               at 3x, and the widened small house beside a large house, a tree
                               and a car at their real scales. **Not** an OpenGL frame.
APK build run:                 no
Maintainer-side verification required: **yes** -- specifically, watch a pedestrian walk rather
                               than looking at one standing, which is what the v2.5 defect
                               needed to be seen.
```

### Known limitations

- **At Pixel 9 size the pedestrian outline is about 1.2 px.** Stable and continuous, but at the
  low end of visible. If it reads as too timid on the device it is one number per category --
  1.2 to 1.5 local units -- and no change to the principle.
- `cloud_body`'s source was rebuilt by hand after the old rim's removal cut the wrong closing
  tag. It is the authored artwork plus the outline group, but it is the one file that did not go
  through the automated path.
- **Nothing was seen rendering.** The mockups use the shipped PNGs at the real scales.
- Eight walk frames that could not carry the v2.5 rim carry the new outline without trouble --
  the failure mode that forced those reverts was specific to clipping.


## v2.5 — a readability rim, a bigger world, dead palms, and a calendar that covers the year

`versionCode = 9`, `versionName = "2.5"`. Tag `v2.5`.

### The rim is the snowman's trick, generalised

The snowman already solved this once: white on white separated by nothing but antialiasing, fixed
with **a tonal rim inset into the silhouette** rather than an outline drawn around it. It did that
by shrinking each circle by half a stroke width and straddling the edge with the other half, which
works for a circle and not for a path.

Clipping a sprite to its own shapes keeps only the inner half of every stroke, which is the same
thing and exact for any geometry: **the content box cannot move**, and every anchor and origin in
the registry is measured against it. 39 sprites carry it now -- walls, roofs, roof snow, canopies,
palms, vehicles, people, the light animals, the cloud and the gull -- each with a rim in its own
tone rather than one colour for the library. Baked into the PNG, so it costs nothing at runtime.

**Eight walk frames were reverted rather than forced.** Their clip did not confine the stroke
(a parent `<g transform>` the clipPath copies do not carry), and their content box moved 2 px.
`person_boy_summer_head_window` was skipped for a different reason -- it already carried strokes of
its own, and adding a second set is a duplicate-attribute error.

The cloud got a hand-written variant: rimming every internal circle made the puff read as a bag of
separate bubbles, so only the front layer is stroked and the back layer stays a plain shadow.

### One number made the world bigger

`PIXELS_PER_METRE_AT_REFERENCE` went from 40 to 45. Every category's base scale is
`metres * that / spriteUnits`, so a 12.5% rise enlarges houses, buildings, trees, people and cars
by the same amount and **cannot change a single ratio between them**. A per-category pass would
have had to be argued object by object, with the ratios as the thing at risk.

12.5% is deliberately short of what the impression alone would ask for: the road is laid out in
fractions of the screen and does not scale with it, so a 1.45 m car went from 58 px against a 67 px
lane spacing to 65 px. Past this the near lane's traffic starts meeting the far lane's. The lake
keeps its own metric and is deliberately not raised: growing its boats in step with the foreground
would flatten the depth two separate metrics exist to express.

### The small house was a cabin because of its elevation, not its size

One window, a door pushed to one side, and a 5.8 m ridge. The door now sits on the wall's centre
with a window mirrored either side of it -- the same drawable at the same size, so a second window
cannot drift from the first -- and the height went to 6.4 m, which puts it in a defensible relation
to the 7.6 m large house rather than at three quarters of it.

### Halloween reaches the palms

The leafy trees lost their canopy from the first release of the flag and the palms did not, so a
Halloween beach kept healthy green fans over its bare-branch neighbours. `palmtree_fronds_dead` is
drawn on the live fan's canvas with the same content box, so it blits at the same origin and the
frost overlay and the light ellipse keep the geometry they were derived from. Desaturating the live
fan was the cheaper option and the wrong one: a grey palm is a palm in bad light.

### The moon is orange without a gradient

The sprite stays a colourless mask -- `SpriteTintClassTest` requires that of every tintable sprite.
What the artwork carries is *luminance*: three concentric paper rings, dark at the rim and bright at
the centre. `HALLOWEEN_MOON_COLOUR` turns that into a warm lantern at the blit, with no glow, no
gradient and no second draw call. Fixed rather than derived from the theme, because letting a cool
moon colour through would produce a blue jack-o'-lantern.

### The calendar covered four windows and now covers the year

It returned `null` for most dates, leaving the caller on whatever the user last picked -- so
"automatic" meant "automatic in December, at Easter and over the summer". It also had a real defect:
New Year began on 30 December and Christmas ran to 6 January, so **Christmas was unreachable on the
last two days of December**, decided by list ordering with a comment asking the next editor to
preserve it.

Occasions are now an ordered list checked before the seasons, and the seasons partition what is
left: Easter, then Halloween, then Christmas, then New Year, then Spring/Winter/Autumn/Beach. Easter
is Good Friday to Easter Monday computed per year, not a fixed week. Every date resolves.

| Window | Theme |
|---|---|
| 1–7 Jan | `new_year` |
| 8 Jan – 1 Mar | `winter` |
| 2 Mar – 31 May | `spring` |
| 1 Jun – 31 Aug | `beach` |
| 1–30 Sep | `autumn` |
| 1–31 Oct | `halloween` |
| 1–30 Nov | `autumn` |
| 1–26 Dec | `christmas` |
| 27–31 Dec | `new_year` |
| Good Friday – Easter Monday | `easter`, above all of the above |

`LocalDate.now()` reads the device's default zone, so the turnover is local midnight and the same
local date always gives the same theme.

### Spring is a theme, not a recolour

Twelfth built-in. Not Easter -- that is four days of decoration that fall inside it -- and not Beach.
What separates it is the light: a pale washed sky with green rather than blue in it, and hills in
the sharp new green that only exists for a few weeks. Its defaults are mostly about what is off:
no winter palette, no fall palette, no Christmas layer, no parasols, plus a full canopy.

### Verification

```
Release identifier:            v2.5
Verification level:            3
Tests run:                     yes -- 395 tests, 0 failures (was 378).
                               SeasonalCalendarTest 23/23 new, SeasonalThemeRulesTest 6/6,
                               BuiltInThemeCoherenceTest 20/20, HalloweenAndSplashTest 21/21,
                               SpriteGeometryTest 3/3, SkySpriteAnchoringTest 7/7,
                               SpriteTintClassTest 5/5, SpriteVariantTest 3/3
Lint run:                      yes -- ./gradlew lintDebug, 41 warnings, 0 errors
Python tooling suite:          yes -- 89 tests, 0 failures
Asset validate:                yes -- 0 failures, 123 entries, anchors 123/123
Normalisation:                 yes -- 74 targets, none pending, 12 excluded by decision
Visual mockup:                 yes -- rim before/after on a close-toned ground and on a white
                               cloud; small house before/after beside a large house; dead palms
                               against live ones; the orange moon at 96 px; a spring frame.
                               **Not** an OpenGL frame.
APK build run:                 no
Maintainer-side verification required: **yes**, and more than usual -- the global scale change
                               touches every standing object at once.
```

### Known limitations

- **The world scale has not been seen on a device.** 12.5% is an argued figure, not an observed one.
- Re-rendering the library from source while baking the rim made the shipped PNGs byte-exact against
  their own sources, which is a better state than D-7 measured -- and cost two fidelity tests their
  shipped examples. Both were re-derived on a constructed pair with the reason recorded.
- Eight walk frames and one window head carry no rim (above).
- Spring has no seasonal decoration of its own, in the way Autumn has pumpkins.


## v2.4 — the refinement pass, and a Halloween theme to hold it

`versionCode = 8`, `versionName = "2.4"`. Tag `v2.4`.

v2.3 shipped the machinery; the device look said the artwork was not there yet. Three
sprites redrawn, the splash extended to both crossings of the surface, and the eleventh
built-in theme added.

### The bird was a bat for three reasons, not one

Small notches under each wing that read as claws, a hard elbow in the leading edge with broad
wing roots, and a head circle sitting apart from the body. Any one of those alone might have
passed; together they were unmistakable.

Before touching it the sprite was set beside `bunny_body` and `penguin_body` to read the
library's own rule off them: three to seven shapes, large primitives, flat tints, no outline,
almost no interior detail. The gull that replaced it has long tapered wings drawn to a point,
a body and head in one piece, and a tail that narrows away rather than forking.

The canvas went from 90x21 to 90x24 so the wings have room to rise, and
`BIRD_SPRITE_ORIGIN_Y_PX` moved from -15 to -18 with it. **The body still sits on y = 0**,
because the wing-flap is a vertical mirror of the coordinate frame and the axis is the one
thing about this sprite that cannot move.

### The dolphin was rebuilt with the library's own idiom

Nine iterations, each checked at 345, 97 and 48 px. What finally worked was building it the
way `bunny_body` is built -- a circle for the melon, a wedge for the beak, a fusiform body
over them -- rather than trying to carry the whole animal in one outline. Six shapes instead
of eight; the dark mouth crease is gone, and the back's peak moved forward where a dolphin's
actually is.

**Recorded honestly: this is better, not finished.** At full size the beak is still thinner
than it should be and the melon-to-back junction has a step. At 48 px, which is the size the
lake draws it, it reads correctly. Accepted on that basis with the maintainer's agreement.

### The splash now fires on both crossings

`arc` is `sin(theta)` and the animal is above water for the first half of every turn of that
angle, so written as a position in a 0..1 cycle the two crossings are the two ends of that
half: **out at 0, in at 0.5.** Each opens a window of `SPLASH_WINDOW_CYCLES`, and the two
cannot overlap because the window is a small fraction of half a cycle.

**One splash per crossing, not one per phase change.** A frame inside a window draws the
burst at the size and opacity its position calls for; a frame outside both draws nothing.
Nothing accumulates, nothing trails the animal across the lake, and a dropped frame costs a
frame of the effect rather than the whole event. Still no state: a remembered "was it above
water last frame" flag would need allocating per dolphin, keeping across a surface change and
a visibility pause, and would be wrong for one frame every resume mid-leap.

Drawn **after** the animal, so on the way out it rises up through its own splash.

### The moon stopped being friendly

Narrow slanted eyes with the inner corner dropped -- the shape a lowered brow makes -- a
triangular nose, and a wide ragged gash with uneven fangs top and bottom, deliberately not
symmetric. One `fill-rule="evenodd"` path still, cut out of the disc so the sky shows through
it. Checked at 240, 110, 72 and 48 px before it was wired up.

### The Halloween theme did not exist

`ThemeCatalog` had ten themes and none of them was Halloween, so the request to preset its
flags had nowhere to land. `SceneTheme.HALLOWEEN` is the eleventh: a late-October dusk,
bruised violet overhead and low amber at the horizon. That palette matters even though
`horrorSkyEnabled` overrides it on arrival -- **it is what comes back when the user turns the
horror sky off**, and a Halloween theme with both switches off still has to look like
something.

Its defaults set `halloweenEnabled`, `horrorSkyEnabled` and the pumpkins. **Presetting is not
coupling.** Both flags stay exactly as independent as they were; this seeds their starting
value the way every other theme seeds `winterColorsEnabled` or `parasols.visible`, and
neither flag reads the other anywhere. A test starts from the theme's own defaults and
asserts each can be turned off without disturbing the other.

The pumpkins joined it for the reason Autumn's are on: they are the season's own decoration.
`BuiltInThemeCoherenceTest`'s "pumpkins stay in autumn" became "pumpkins stay in the two
themes that are about pumpkins", and now asserts both directions rather than excusing
Halloween from the rule. Broadleaf trees, not palms -- a palm has no dead variant and would
stand in leaf through the whole presentation.

### Verification

```
Release identifier:            v2.4
Verification level:            3
Reason for the level:          three sprites redrawn, a new built-in theme, renderer and
                               settings changes.
Tests run:                     yes -- ./gradlew testDebugUnitTest, 378 tests, 0 failures
                               (was 371). HalloweenAndSplashTest 21/21,
                               BuiltInThemeCoherenceTest 20/20, SpriteTintClassTest 5/5,
                               SpriteGeometryTest 3/3, SkySpriteAnchoringTest 7/7,
                               SpriteVariantTest 3/3, CustomThemeDataJsonTest 23/23
Lint run:                      yes -- ./gradlew lintDebug, 41 warnings, 0 errors
Python tooling suite:          yes -- 89 tests, 0 failures
Rasteriser probe:              yes -- fingerprint matches the pin
Asset validate:                yes -- 0 failures, 122 entries, anchors 122/122,
                               18 variant groups distinct
Normalisation:                 yes -- 74 targets, none pending, 11 excluded by decision
Fidelity compare:              yes -- 18 PIXEL_IDENTICAL, 14 EDGE_EQUIVALENT, 90 DIVERGENT
Visual mockup:                 yes -- a full Halloween frame from the shipped PNGs at the
                               real origins and scales: horror sky, carved moon at its
                               on-screen size, four dead trees, gulls at 90 px, and a leap
                               cycle with the splash at both crossings. Each redrawn sprite
                               also checked at 48 px on its own. **Not** an OpenGL frame.
APK build run:                 no
ZIP verification:              yes
Git tag created:               no
Maintainer-side verification required: **yes.** Select the Halloween theme and confirm both
                               switches arrive on; then turn each off in turn and confirm the
                               other stays. Watch a dolphin through a full leap for the two
                               splashes. Check the gulls against the sky at their real size.
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **The dolphin is accepted rather than finished** -- see above.
- **Nothing was seen rendering.** The mockups use real assets and real geometry, but no
  device or emulator was available.
- The Halloween theme has no entry in `SeasonalThemeRules`, so it never auto-selects by date.
  Deliberate: this batch was asked for the theme and its defaults, not for a date window.
- The dead-tree crown is still on the sparse side at scene scale.
- The reference export was used as **direction only**, per its own manifest and this project's
  position on original assets. No pixel from it ships.


## v2.3 — Halloween, a horror sky, a dolphin splash, and two sprites redrawn

`versionCode = 7`, `versionName = "2.3"`. Tag `v2.3`.

Four visible changes, two new flags, four new sprites and two redrawn ones. 122 sprites now,
15.31 MB decoded.

### Halloween and the horror sky are two flags, not one

`halloweenEnabled` carves the moon into `moon_jack_o_lantern` and swaps every canopy for
`tree_dead_branches`. `horrorSkyEnabled` overrides the six sky colours with near-black
overhead and a hard orange horizon. **Neither implies the other, and neither reaches winter,
Christmas, New Year or the fall palette in either direction.**

That separation is the lesson v2.0 recorded, applied before the mistake rather than after it.
Christmas lights hung off the winter flag for a whole release and nothing failed -- each was
internally consistent, and the only way to see the defect was to want a snowy January without
fairy lights and find it unreachable. A season and a decoration layer are different statements;
so are a decoration layer and a palette. `HalloweenAndSplashTest` pins that all four
combinations are expressible and that the existing seasonal flags are untouched, because that
is the property that would rot silently.

**Scope kept narrow on purpose.** Halloween does two things. The pumpkins already have their
own switch and keep it, for the same reason Santa keeps his: one thing with two controls that
can disagree is worse than two things with one each. The snow cap and the Christmas lights are
not disabled by it either -- they simply have nothing to draw on a tree with no foliage.

The horror sky **overrides** the user's palette rather than editing it, so switching it off
returns exactly the colours that were there. It keeps the day/night blend: a sky that never
changed would stop the sun and the moon meaning anything.

### The moon is carved, not painted

`moon_jack_o_lantern` is one `fill-rule="evenodd"` path: the eyes, nose and grin are holes in
the disc, so the sky shows through them. Painting the face on in a second colour would have
been easier and would have stopped reading at about 90 px; a moon is drawn at roughly 48.
Checked at 240, 90 and 48 px before it was wired up. Tintable like every other phase, and
excluded from normalisation with the rest of the canvas-anchored sky set.

Halloween replaces the disc outright, phases and all. A carved face that waxed and waned
would be a lit fraction of a grin, which reads as a rendering fault rather than as a
decoration.

### The dolphin splash carries no state

The leap is `sin(0.9t + phase * 6.28)` and the animal is drawn only while that is positive, so
it meets the water again exactly when the angle, expressed as a position in a 0..1 cycle,
passes 0.5. The splash occupies the 6% of cycle after that -- about 0.07 s -- with the frame
chosen and the alpha faded from where the frame lands inside it.

**Derived rather than remembered, and that is the point.** A "was it above water last frame"
flag has to be allocated per dolphin, kept across a surface change and a visibility pause, and
is wrong for one frame whenever the wallpaper resumes mid-leap. This allocates nothing in the
draw path and costs one modulo on frames that are already skipping the animal.

Sized against the animal that made it, so the two can only be wrong together.

### Two sprites redrawn, and what the mockup caught

`bird_body` was reading as a bat: a sharp elbow in the leading edge, broad wing roots and a
head circle sitting apart from the body. It is a gull now -- smooth tapered wings sweeping
back to a point, head continuous with the body, a wedge of tail. The geometric contract is
unchanged: 90x21 on the same viewBox with the body on y=0, because the wing-flap is a vertical
mirror of the frame and the body has to sit on the axis.

`dolphin_body` gained a tapered beak, a distinct melon, a swept dorsal fin and a notched
two-lobed tail, on the same canvas so no origin moved.

**The before/after mockup caught a real defect in that redraw.** The first version had the
flukes and the head on the same end: the group is mirrored, and the eye had been kept at low
x, matching the original, while the flukes were moved there too. The result was an animal with
two tails and no face, and it would have shipped. The mockup existed because the batch changed
artwork; this is what it was for.

### Verification

```
Release identifier:            v2.3
Verification level:            3
Reason for the level:          new sprites, new flags, renderer and settings changes.
Tests run:                     yes -- ./gradlew testDebugUnitTest, 371 tests, 0 failures
                               (was 357). HalloweenAndSplashTest 14/14 new,
                               SpriteTintClassTest 5/5, SpriteGeometryTest 3/3,
                               SkySpriteAnchoringTest 7/7, SpriteVariantTest 3/3,
                               CustomThemeDataJsonTest 23/23
Lint run:                      yes -- ./gradlew lintDebug, 41 warnings, 0 errors
Python tooling suite:          yes -- 89 tests, 0 failures
Rasteriser probe:              yes -- fingerprint matches the pin
Asset validate:                yes -- 0 failures, 122 entries, anchors 122/122,
                               18 variant groups distinct
Normalisation:                 yes -- 74 targets, none pending, 11 excluded by decision
Fidelity compare:              yes -- 18 PIXEL_IDENTICAL, 14 EDGE_EQUIVALENT, 90 DIVERGENT
Visual mockup:                 yes -- the four Halloween/Horror-Sky combinations, the leap
                               and splash sequence at runtime scale, and before/after for
                               the dolphin and the gull. Composed from the shipped PNGs at
                               the real origins and scales; **not** an OpenGL frame.
APK build run:                 no
ZIP verification:              yes
Git tag created:               no
Maintainer-side verification required: **yes.** Install on the Pixel 9 and check: the four
                               flag combinations, the moon at its real on-screen size, the
                               trees under sway with Halloween on, and a dolphin leap timed
                               so the splash lands with the animal.
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing here was seen rendering.** The mockups use real assets and real geometry, but no
  device or emulator was available.
- The dead-tree crown is on the sparse side at scene scale. It reads correctly as a bare tree;
  slightly heavier limbs would read better, and that is a judgement best made on the device.
- The reference export supplied for this batch was used as **direction only**. Its own manifest
  records that it was extracted from a third-party APK and is study material, which matches this
  project's stated position on original assets. What it contributed was the decision to carve
  the moon's face rather than paint it, and the two-level forked structure of the bare tree. No
  pixel from it ships.
- The fidelity criterion still reads antialiasing out of the alpha channel only. Recorded at
  v2.1, unchanged.


## v2.2 — D-10 closed: the padding, and the origins that had to move with it

`versionCode = 6`, `versionName = "2.2"`. Tag `v2.2`.

67 PNGs cropped, 34 blit origins compensated, decoded artwork **16.28 MB -> 14.79 MB**
and transparent padding **3.08 MB -> 1.59 MB**. Nothing in the scene moves, and that is
asserted rather than asserted-to-be-obvious.

### What D-10 actually was

Recorded as an asset problem, it was never one. `SpriteBlitter` puts the bitmap's own
pixel (0,0) on the origin its call site passes, so cropping padding off the left or the
top of a sprite moves what that pixel is and the drawing lands somewhere else. A crop is
only correct together with a compensation in the renderer, and the entry that deferred it
described a decision about artwork.

Two tooling defects sat underneath, both in `_rewrite_registry_geometry` and both
unexercised because no `--apply` run had ever completed:

* it guarded the anchor re-derivation on `has_anchor` rather than
  `derives_anchor_from_box`, so it asked `PART_LOCAL` for a derivation that rule does not
  have and aborted. **That is what stopped v76.9 on `bar_sign`**, and the abort was
  recorded as a conflict between the crop rule and the anchor model. There was no
  conflict; `derive_anchor` returns `None` for a declaration by design.
* it passed `units_per_pixel` into `derive_anchor`, which writes local units into a field
  the registry declares in pixels -- a factor of three on every `SCENE_UNITS` sprite. This
  one surfaced immediately, as 20 `validate` failures, the first time a crop got far
  enough to reach it.

A third was in `normalize` itself: the box was rounded to the sprite's own unit, which is
1 px for a `CANVAS_PIXELS` sprite, and that took `bird_body` to 88x21 -- off the grid
`SpriteGeometryTest` requires of the whole set. The rounding grid is now
`SPRITE_PIXELS_PER_UNIT` for every sprite; only the compensation still follows the scale
convention.

### Done in two passes, and the first one needed nothing

**Trailing first.** Padding on the right and the bottom can be removed with no
compensation at all: pixel (0,0) does not move, every drawn pixel keeps its coordinates,
and nothing outside `GlTextureAtlas` and `CanvasSceneTarget` reads a sprite's dimensions.
30 targets, 63 files, 0.67 MB, no Kotlin touched. `normalize --apply-trailing` is that
rule, and it is in the tool rather than in a script because the distinction it draws is
the useful half of the answer.

**Then leading, with its compensation.** 34 targets, each origin moved by the trim in the
same change: 27 literal call sites, and seven constants -- `PERSON_ANCHOR_X_UNITS`,
`WINDOW_HEAD_ANCHOR_X_UNITS`, `CAR_HEAD_ANCHOR_X_UNITS`/`_Y_UNITS`,
`SANTA_SLEIGH_ORIGIN_X_UNITS`/`_Y_UNITS`, `DOLPHIN_ORIGIN_Y_UNITS`,
`BIRD_SPRITE_ORIGIN_Y_PX`, `LIGHTNING_BOLT_WIDTH_UNITS`.

**Two constants were scale references, not origins, and moving them would have resized
something.** `RAINBOW_SPRITE_HALF_WIDTH_UNITS` divides into `maxRadius`; lowering it from
100 to the new canvas's 99 would have scaled the whole rainbow up by a percent, so it
stays at 100 and the blit now uses its own `RAINBOW_SPRITE_ORIGIN_X_UNITS`/`_Y_UNITS`.
`LIGHTNING_BOLT_HEIGHT_UNITS` is the bolt's scale reference and its height did not change;
only the width, which exists solely to centre it, moved from 34 to 30.

### Excluded, by decision rather than by omission

Ten sprites, each with its reason in `normalize.EXCLUSIONS`. The eight canvas-anchored sky
sprites -- the sun, the four moon phases, `sun_glow`, `star_sparkle`, `firework` -- are
placed by the centre of their bitmap, and `CELESTIAL_DISC_ORIGIN_UNITS` positions the sun
and all four phases from one number while their content boxes differ. Cropping them would
mean splitting that constant per sprite and changing the anchor rule with it, which is an
anchoring decision and not padding removal; `SkySpriteAnchoringTest` is the test that
caught defect D-1 twice, and it pins what is there now. The two palm fronds keep their
existing exclusion.

### The verification that matters

Before any crop, every sprite's ink was hashed as the tuple of (x, y, RGBA) over every
pixel with non-zero alpha. Afterwards, each sprite's ink was searched for the translation
that reproduces that hash. **All 118 matched, and every shift was exactly the trim its
origin was compensated by** -- 3 px per local unit for a `SCENE_UNITS` sprite, 1 px for a
`CANVAS_PIXELS` one. No pixel changed colour, and no pixel ended up anywhere other than
where it started once the blit is applied.

`santa_sleigh_*` and `bird_body` are the cases where that mattered most: both are blitted
under a mirror (`canvas.scale(dir * SANTA_SLEIGH_SCALE, ...)`, and the bird's vertical
wing-flap). The mirror is applied to the coordinate frame, so what has to stay put is the
drawing's position in that frame -- which is exactly what the compensation preserves.

**Three sprites lost `PIXEL_IDENTICAL`**, and it is not a shape or position difference.
`dolphin_body`, `santa_sleigh_scene` and `santa_sleigh_trot` now differ from a fresh
render of their sources by 30, 27 and 27 pixels, at most 32 alpha units each. The cause is
that resvg is not invariant to the size of the pixmap it renders into: rendering the
original document and cropping the result gives the same 30-pixel difference as rendering
the cropped document, so the source edit is exact and the rasteriser is the variable.
Measured directly: solid/empty conflicts 0, bounding-box delta (0,0,0,0), and the
coverage-weighted centroid moves by 0.011 px on the dolphin and 0.001 px on the sleighs,
with total coverage differing by 1.7 px out of 25,911 and 0.2 px out of 40,132. That is
antialiasing on a curve, inside the envelope D-7 already measures and bounds.

### Verification

```
Release identifier:            v2.2
Verification level:            3
Reason for the level:          shipped artwork and renderer call sites changed together.
Tests run:                     yes -- ./gradlew testDebugUnitTest, 357 tests, 0 failures.
                               First run in this project's recorded history: an Android
                               SDK and a JDK with a compiler were installed for it.
                               SpriteGeometryTest 3/3, SkySpriteAnchoringTest 7/7,
                               SpriteTintClassTest 5/5, SpriteVariantTest 3/3,
                               SceneSpaceTest 18/18, SceneTransformTest 19/19
Lint run:                      yes -- ./gradlew lintDebug, 0 errors, 41 warnings
Python tooling suite:          yes -- 89 tests, 0 failures
Rasteriser probe:              yes -- fingerprint matches the pin, toolchain unmoved
Asset validate:                yes -- 0 failures, 118 entries, anchors 118/118,
                               18 variant groups distinct
Normalisation:                 yes -- 71 targets checked, none carries removable padding,
                               10 excluded by decision
Ink invariance:                yes -- all 118 sprites reproduced their pre-crop ink hash
                               under the translation their origin was compensated by
APK build run:                 no
ZIP verification:              yes
Git tag created:               no
Maintainer-side verification required: **yes, and it is the point of this release.**
                               Install on the Pixel 9 and look at: trees, houses and
                               their windows, the roof snow on all four building types,
                               cars and their drivers and passengers, walking people,
                               the bunny, the penguin, the snowman, the pumpkin, clouds,
                               the rainbow, lightning, the dolphins, the sailboat and
                               Santa's sleigh. Anything that moved by a few units would
                               show as a part sitting slightly off its parent.
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing in this release was seen rendering.** The invariance argument is measured and
  the tests are green, but no device or emulator was available here.
- The fidelity criterion still reads the antialiased boundary out of the alpha channel
  only, so most sprites report `DIVERGENT` against their sources despite the shape bounds
  D-7 pins. Recorded at v2.1, unchanged.
- `tools/assets/README.md` and `CLAUDE.md` still quote an older sprite count.


## v2.1 — D-7 closed: rasteriser fidelity, measured rather than asserted

`versionCode = 5`, `versionName = "2.1"`. Tag `v2.1`.

Offline tooling and documentation only. **No Kotlin, no asset, no resource, no Gradle
plugin and no manifest change**, so nothing about the running wallpaper differs from
v2.0. The version exists because the project's recorded state changed, not because its
behaviour did.

### The three failing tests were never D-7

They had been carried since v76.8 as the price of D-7 — "the shipped PNGs came from the
V2 library's own rasteriser, and the pinned toolchain antialiases differently". That
description fitted the deferral, but not the failures.

`tests/test_fidelity.py` still asserted the **pre-V2** sprite library.
`house_shared_planter` was pinned as a white full-canvas rounded rectangle at 78x18
radius 6; the V2 artwork is a `#C98F5A` box occupying only the lower part of its viewBox
with three foliage circles over it. Measured against the assertion that gives **113
solid/empty conflicts and a maximum RGB difference of 176** — a different picture, not a
different antialiasing decision. `road_line` was pinned at 52x8 radius 3.9 and ships at
54x9 radius 4.5, so it failed on size before any pixel was compared.

The count stayed at three across the redesign, which is why the mislabel survived: the
number in the verification block never moved, so nothing prompted anyone to re-read what
was behind it. `reports/geometry-fit.json` carried the same staleness — it still named
`house_large_planter` and `house_small_planter`, both removed in Phase 3.4.

### What replaced them

The assertions were re-derived against `house_large_trim`, which really is a full-canvas
rounded rectangle in the V2 set, so `fit` determines it completely: one free parameter,
swept exhaustively. It is pinned in both directions — the radius recovered from the
shipped pixels reproduces the sprite, the grid values either side of it do not. The IoU
case moved to the sprites that genuinely score under the reporting floor while
reproducing exactly: `bunny_innerear` (0.9905), `pumpkin_stem` (0.9934) and
`penguin_feet` (0.9955), all small enough that their antialiased band is a large share
of their area, which is the point the metric was there to make.

### D-7, bounded

With the mislabel removed, the residual divergence could be measured. Across all 118
sprites, comparing each shipped PNG against a fresh render of its committed SVG source
with the pinned toolchain:

- **no pixel is solid in one rendering and empty in the other** — no sprite's shape
  differs from its source;
- **no single pixel's coverage moves by as much as half** — worst case 121 of 255, on
  one pixel of `rainbow_arc`'s shallowest stroke edge.

Everything the two rasterisers disagree about is therefore the resolution of a boundary
pixel. Both bounds are pinned by `ShippedAgainstSourceTest`, so the claim fails loudly
if it ever stops being true rather than decaying into prose. **The 108-sprite re-render
that was thought to be the price of closing D-7 was never required**; it would only have
made three unrelated assertions pass.

### Verification

```
Release identifier:            v2.1
Verification level:            1
Reason for the level:          offline tooling and documentation only; no Kotlin,
                               asset, resource, Gradle or manifest change.
Tests run:                     no Kotlin change -- last run 357 tests, 0 failures
Lint run:                      no -- same reason; last run 41 warnings, 0 errors
Python tooling suite:          yes -- 83 tests, 0 failures (was 79 with 3 failures)
Rasteriser probe:              yes -- fingerprint matches the pin, toolchain unmoved
Asset validate:                yes -- 0 failures, 118 entries, 118 with an SVG source
Fidelity compare:              yes -- 16 PIXEL_IDENTICAL, 14 EDGE_EQUIVALENT,
                               88 DIVERGENT; reports regenerated
New tests shown to fail:       yes -- AI_PROJECT_RULES 12.11 applied to each of the
                               rewritten and added tests
APK build run:                 no
ZIP verification:              yes
Git tag created:               no
Maintainer-side verification required: none -- nothing user-visible changed
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **The fidelity criterion is tuned for single-layer silhouettes.** It reads the
  antialiased boundary out of the alpha channel only. The V2 library is layered
  paper-cutout artwork, so where two opaque shapes meet, the antialiased band lives in
  RGB at full alpha and the three gating conditions cannot see it. That is why 88
  sprites still report `DIVERGENT` despite the shape bounds above, and why
  `paperscrape-assets compare` exits non-zero. Recorded, not fixed: correcting it is a
  redesign of the criterion, not part of closing D-7.
- `tools/assets/README.md` and `CLAUDE.md` still quote an older sprite count (111 PNGs,
  22 of 108 with sources). The current figure is 118, every one with an SVG source.
- **Nothing in this release was seen rendering**, and nothing needed to be: no code the
  wallpaper executes was touched.
- D-10 remains open and was not touched.


## v2.0 — the complete built-in theme review

`versionCode = 4`, `versionName = "2.0"`. Tag `v2.0`.

Every built-in theme's defaults reviewed and corrected. No renderer redesign, no
asset work: this is configuration, plus one architectural split that the
configuration needed in order to be expressible.

### The flag that was never switched on

`winterColorsEnabled` drives tree snow caps, roof snow and winter clothing — three of
the things that make a winter scene — and defaulted to **off for every theme**,
including Winter, Christmas and Tundra. `fallColorsEnabled` did the same for Autumn.
The features worked; nothing ever turned them on. So the winter themes shipped with
green summer trees, bare roofs and people in short sleeves standing on snow, and the
roof snow added two releases earlier was invisible in the only themes it was drawn
for.

### Winter and Christmas are now two flags

The lights hung off the winter flag, which made the two words synonyms: a plain snowy
January was impossible, and Christmas cost a full winter presentation whether or not
one was wanted. `christmasDecorationsEnabled` is now its own flag. Neither implies the
other and all four combinations are reachable.

**Scope, and the part that is a judgement.** The new flag governs the Christmas
dressing that has no category of its own — currently the tree lights, and whatever is
added later. **Santa and the presents keep their own switches**, because they already
had them and folding them in would give one thing two controls that can disagree. A
theme's defaults set all three together; a user can still take any of them separately.
Stated here because the instruction listed Santa and presents under the Christmas flag,
and this is a deliberate departure from that reading.

### Per-theme corrections

| Theme | What was wrong |
|---|---|
| Winter | no winter presentation; beach umbrellas in the snow; no falling snow |
| Christmas | the same, plus lights inseparable from the season |
| New Year | not in winter at all, despite the date; umbrellas at a night party |
| Tundra | no winter presentation; **sailboats and dolphins in the Arctic**, inherited from the generic lake default the theme's own override did not name; a forest where trees stop |
| Autumn | autumn sky over midsummer foliage; no pumpkins; umbrellas |
| Beach | **the ground drew in the sea's own colour** — `hillColorsDay[0]` is the water tone, and only entry 0 is read since the scene dropped to one hill layer, so the two sand tones behind it were unreachable |
| Desert | broadleaf woodland in a desert |
| City | as many cottages as offices |

Winter and Christmas now snow by default — a deliberate exception to the opt-in
weather rule, on the grounds that a theme called Winter whose weather is off hides its
own subject in a menu.

### A reset that had stopped resetting

The Seasonal Decorations screen's "reset everything to defaults" wrote `false` into
the seasonal flags. That was indistinguishable from a default while every theme
defaulted to off, and stopped being a reset the moment four themes started defaulting
to on. `resetSeasonalPalettes()` removes the keys instead, so they fall back to the
theme's own defaults.

### Migration

**None required.** Defaults apply only where the user has never set the preference;
`readVariantConfig` reads the stored value and falls back to the default only when
absent. Custom themes persist a whole `SceneCustomization` and are untouched. The new
JSON field is absent from older payloads and falls back to the theme's default — a
missing field is not a changed one.

### Verification

```
Release identifier:            v2.0
Verification level:            2
Tests run:                     yes -- 357 Kotlin unit tests, 0 failures
Lint run:                      yes -- 41 warnings, 0 errors, 0 fatal (unchanged baseline)
APK build run:                 no
ZIP verification:              yes
Git tag created:               no
Maintainer-side verification required: the four winter themes, Autumn, Beach's ground,
                               Desert's palms and the City's density
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing in this release was seen rendering.** No device, no emulator, no OpenGL.
  Every change is a default, which means none of it is visible until someone installs
  the app fresh — there is no running build that would have shown it.
- The falling snow now on by default in Winter and Christmas, and the absence of
  lights in Winter, are the two changes most worth looking at first.
- D-7 and D-10 remain open and were not touched.


## v1.0 — first stable release

**Stable / latest.** `versionCode = 1`, `versionName = "1.0"`.

The contents of v76.12, released. **No functional change: this entry records the
version reset and what the release contains, not new work.**

### The version reset

`versionCode` went from 76 to 1 and `versionName` from "76.0" to "1.0". The numbers
up to 76 were the internal build sequence of an unreleased project and meant nothing
to anyone installing it; v1.0 is where the version a user sees starts.

**Two consequences follow, and both matter to the maintainer rather than to the
code.** Android refuses to install a lower `versionCode` over a higher one, so a
device carrying any earlier internal build must uninstall before installing this —
and uninstalling clears its DataStore, which is where saved settings and custom
themes live.

**On the tag.** When this release was prepared, CI still required a stable tag's major
number to equal `versionCode`, which would have made the tag `v1` rather than `v1.0`.
That rule was replaced immediately afterwards: tags are now `vMAJOR.MINOR` and are
checked against `versionName`, so the tag for this release is **`v1.0`**. No tag was
created in either session.

`AI_PROJECT_RULES.md` §11.2 says never to change the Android version merely because
the project release identifier advanced. This change is the explicit exception the
same rule allows: it was asked for directly, and it is the point of the release.

### What v1.0 contains

The whole of the work recorded below, in one build:

- A paper-cutout landscape rendered with OpenGL ES 2.0, with the `Canvas` backend
  kept behind the same `SceneCanvas` abstraction for the settings preview and as a
  fallback.
- The V2 asset library: 118 sprites, every one with an SVG source and a committed
  pipeline that can regenerate it, no byte-identical pair anywhere in the set.
- One coherent scene geometry. `SceneSpace` owns the ground plane, the horizon, the
  perspective, the road, the pavement and the size of every category, and every size
  is derived from a declared real-world height rather than authored per sprite.
- Ten themes plus custom themes, with per-category visibility, density and colour;
  seasonal decorations placeable on any theme; automatic seasonal theme switching.
- Live Weather from real conditions, with a stated fallback when no location is
  available.
- Sunrise and sunset from the device clock, GPS or a chosen location.

### Verification

```
Release identifier:            v1.0
Verification level:            2
Reason for the level:          version metadata and documentation only; no source,
                               asset, test or tooling change.
Tests run:                     no -- nothing executable changed since v76.12's run
                               of 330 Kotlin tests, 0 failures
Lint run:                      no -- same reason; last run 41 warnings, 0 errors
Python tooling suite:          no -- last run 79 tests, 3 failures, all D-7 fidelity
Asset validate:                no -- last run 0 failures across 118 sprites
APK build run:                 no
ZIP verification:              yes
Git tag created:               no
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Changed after v1.0 was cut

The release tag scheme moved to semver — `vMAJOR.MINOR`, checked against `versionName`
— immediately after this release, and `UpdateChecker` was reading a tag as a bare
integer. Under the new scheme it would have parsed nothing and reported "no update"
forever. It now compares `MAJOR.MINOR` and ignores any other tag shape, which also
makes the pre-release history's integer tags invisible rather than readable as
absurdly high versions. Not part of v1.0's shipped APK; it ships with v1.1.

### Known limitations carried into v1.0

These are open and shipped as such, not oversights:

- **D-7** — the shipped PNGs came from the V2 library's own rasteriser while the
  pinned toolchain renders antialiased edges slightly differently. Invisible at
  runtime; it costs three fidelity tests in the offline tooling.
- **D-10** — 40 sprites still carry croppable transparent padding. Cropping needs the
  crop rule and the `PART_LOCAL` anchor model reconciled first, and every origin
  compensated in the same change.
- **B5** — the renderer, the wallpaper engine, the preferences layer and the Compose
  UI cannot be unit tested without first being decoupled from `Canvas`/`Context`, so
  coverage stays narrow and engine changes are verified on a device.
- **Nothing in this release was built or seen rendering by Claude.** No device, no
  emulator, no OpenGL. Every visual claim in this file below v76 rests on the
  maintainer's own device passes.


## v76.12 — polish batch 2: snow on buildings, people controls, star field, lake

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

### D-8: snow settles on buildings

Open since v76.3. Five new sprites, each cut to the roof it lies on: the two house
roofs, the restaurant's and bar's parapets, and the tower's setback.

**A layer on the roof, never the roof tinted white.** Tinting repaints the building
rather than covering it, and `winterColorsEnabled` is already a palette override, so
the two would be indistinguishable — the shortcut this defect's own entry rejected.

The pitched caps follow their roof's slopes exactly and crest four units above the
ridge, which is what makes them read as resting *on* the roof rather than as part of
it; below the ridge they stay strictly inside the outline, checked against both
slopes at the drift's lowest point. The flat roofs get a drift standing proud of the
parapet. The tower's is deliberately shallower: a roof that high is swept, and a deep
cap would read as a hat.

Each is two polygons, cool shadow under white — the recipe `tree_canopy_snowcap`
already uses, and the reason they carry colour at all: they are blitted untinted, so
an achromatic white mask would be exactly the defect `DESIGN_NOTES.md` decision 25
exists to prevent. Every origin is derived from the sprite it covers, so redrawing
either moves both. Drawn before the chimney and before the tower's mast, so those
stand out of the drift.

Asset `validate` stays at 0 failures across the 118 sprites.

### People are a category

Visibility and density, through the same generic storage every other category uses —
no new settings system, which was the condition for doing it at all. Density thins
the shared candidate pool through the same threshold, with its own salt, so lowering
it removes a particular pedestrian and leaves the rest where they were instead of
reshuffling everybody.

**No colour controls, deliberately.** The walk sprites are finished art in four kinds
across two seasons and there is nothing for a tint to reach; offering swatches that
did nothing would be worse than offering none. Their clothing still follows Winter
Colors, exactly as before.

Saved themes written before this release simply fall back to the default, so there is
no schema step: a missing category is not a changed one.

**Passengers are now a property of the vehicle.** `CarType.carriesPassengers` is false
for police cars and fire engines — they are crewed, not travelled in, and a child in
the back of either reads as something being wrong. Written as a property rather than
a list of exclusions at the call site, so a service vehicle added later is excluded by
default instead of by somebody remembering. The driver is still always an adult, and
still by construction: the driver comes from a table holding only the man and the
woman.

### Star field

Every star was the sparkle sprite under its own save/translate/rotate/scale/blit/
restore — six canvas operations each, seventy times a frame, for a field where most
of them are a couple of pixels across and the rotation is invisible at that size.

One star in five is still a sparkle; the rest are points, one `drawCircle` each. That
is roughly 130 operations a frame against 420. **The look is better for it rather than
merely cheaper:** a real night sky is mostly points with a few bright stars in it, and
seventy identical rotating sparkles read as a pattern. The sparkles that remain are
the ones that were legible before. The point colour is the sparkle art's own cream, at
0.55 of the radius to match its apparent weight rather than its four-tip extent.

### D-5, reopened and done properly

v76.11 gave boats the far half of the lake and dolphins the near half. That fixed the
overlap by taking half the lake away from each, which is the wrong trade: the surface
is the scene's only open space and both belong on all of it.

The band is instead cut into six lanes spanning it top to bottom, with boats on the
even lanes and dolphins on the odd. Both reach the near edge and the far edge, and two
of them still cannot be placed on the same line. Where inside its lane a candidate
sits is still its own noise, so nothing reads as a grid.

### Verification

```
Release identifier:            v76.12
Verification level:            2
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
Asset pipeline:                render + validate; 118 sprites, 0 validate failures
APK build run:                 no
ZIP verification:              yes
Maintainer-side verification required: the winter and Christmas themes, for the snow
                               on all four building types; the night sky; the People
                               screen; the lake
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing was seen rendering.** The snow caps were checked as composites against
  their own roofs, which establishes the fit and nothing about how they read in a
  scene. The star field's new look is reasoned, not observed.
- Localisation was excluded from this batch by instruction; the app stays English-only.
- D-7 and D-10 remain open and were not touched.


## v76.11 — polish batch 1

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

Four of the batch's eight items are done. The four that are not are listed at the
end with the reason, because three of them are bigger than the batch and one is
not worth its risk.

### Pedestrians move with the ground

The reported defect: swiping between home screens scrolled the village past the
people while they stayed almost still.

Their position was a fraction of *screen* width, so the walk was the only motion
they had — they were the one thing in the scene outside the parallax, and since
v76.7 put them among the buildings it was the most visible place to be outside it.

A pedestrian now has a position on the tiling ground exactly like a house. The walk
advances that position and `GroundGeometry` scrolls it with everything else standing
on the same ground, so the two motions compose instead of competing. That is also
what makes the walk read as walking: a figure sliding against a static background is
a figure on a treadmill.

Tiled like static objects too, for the same reason — the ground repeats every
`tileWidth`, so a pedestrian near the seam exists on both sides of it and both copies
have to be drawn or one pops. Row, scale, speed and animation are unchanged.

### Live Weather: a fallback that says so

With Live Weather on and no location obtainable, the scene kept running on the
theme's own clouds and precipitation — which is a valid scene, and exactly what it
shows with Live Weather off. The failure was never that the scene broke; it was that
the switch looked dead and nothing said why.

The service now publishes a fallback flag through the same settings flow the settings
screen already collects, so the notice appears and clears as the state changes, with
no polling and no restart. Under the switch, while it is on and the fallback is
active: *"Location unavailable — showing this theme's own weather instead."* Nothing
is shown when a location is available and the weather is working, and nothing is
shown when Live Weather is off.

The renderer is untouched. Saying what happened is the whole fix.

### Update check is opt-in

It ran on every settings open — a network request the user never made, for a feature
they may not want. It is now off by default behind a switch, and the manual "check
now" button works whether the switch is on or not.

### D-5: boats and dolphins have separate lanes

They had decorrelated noise but no knowledge of each other, so nothing stopped one
being placed on the other's lane and drifting through it. Each category now owns half
the usable water — boats the far half, dolphins the near one, so a breach happens in
front of the traffic rather than behind it. Neither ever used more than a slice of the
band anyway, and two rows at different distances is what a lake with things on it
looks like.

### Not done in this batch

- **D-8, snow on buildings.** Four roof shapes need a snow cap each, drawn to follow
  their own silhouette the way `tree_canopy_snowcap` follows the crown's, plus a
  registry entry and an anchor each. That is an artwork task with a visual approval
  attached, not a code change, and doing it badly means white shapes floating near
  roofs. **Still open.**
- **Person visibility and density controls.** These need a new customisable category:
  a config in `SceneCustomization`, preference keys, JSON round-trip, migration and a
  settings section. The batch said to skip it if it needed a significant settings
  addition, and it does. **Still deferred, and still behind decision D3.**
- **Localisation.** Partial and honestly so: the strings this batch touched are in
  `strings.xml`, which is roughly ten of about seventy. The rest is a mechanical pass
  worth its own change, where the diff is reviewable as one thing.
- **Star field performance.** ~1,890 Canvas calls a frame, and no simple safe fix:
  the cheap options either change what is drawn or need a batching path the
  `SceneCanvas` abstraction does not currently expose. The batch said to leave it in
  the backlog if it needed a refactor. **Left in the backlog.**

### Verification

```
Release identifier:            v76.11
Verification level:            2
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
APK build run:                 no
ZIP verification:              yes
Maintainer-side verification required: swipe between home screens and confirm the
                               people scroll with the village; turn Live Weather on
                               with location off and confirm the notice appears
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing was seen rendering.** The parallax and the fallback notice are both
  reasoned from the code path.
- The pedestrian change alters how far people travel per loop: their walk is now
  measured against a tile of ground rather than a screen width. The two are close but
  not equal, so their pace on screen may need one look.
- D-7, D-8 and D-10 remain open.


## v76.10 — Live Weather with a custom location, and the Easter pair

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

### Live Weather: two gates, both closed

v76.9's fix was real but incomplete, and the custom-location case exposed both of
the reasons why.

**A race the custom-location path could not survive.** `settings` was assigned
inside the block queued onto the render thread, while the custom-location branch of
the same collector runs immediately on the collector's own coroutine — and that
branch wakes the weather loop. The loop woke, read a `settings` the render thread
had not updated yet, saw Live Weather still off, and went back to sleep. With GPS a
fix arrives seconds later and wakes it again, which is why the case looked fixed;
with a custom location **no second wake-up is ever coming**, because the coordinates
were already known. `settings` is now published on the collector, before anything
can observe the change, so the window does not exist.

**A location change did not invalidate the cached fetch.** The loop had one reason
to fetch — an hour since the last one. The refresh timer answers "are these
conditions stale"; it does not answer "are these the conditions of the place we are
actually showing", and only the second question changes when the user edits their
custom location. Moving the location left the scene showing the old town's weather
for the rest of the hour. The loop now also fetches when the fix differs from the
one the last fetch was made for.

The two are independent: the first made the switch appear dead, the second made a
location edit appear ignored. Either alone would have left half the report standing.

### Easter: the rabbit and the eggs

Both were drawn at life size, which at the depth they stand made them a couple of
dozen pixels the same colour as the ground behind them. They are the Easter theme's
two subjects, and an object nobody can see is not carrying a theme.

Raised through the size table, which is the only place a size may come from:
`BUNNY` 0.55 m → **0.9 m**, `EASTER_EGG` 0.6 m → **1.0 m**. Deliberately past life
size, and recorded as such beside the numbers. They now draw at roughly 21–28 px
against a person's 44–54 px in the same band — a third to a half of a person, which
reads without competing. No artwork, anchor or layering changed.

### Verification

```
Release identifier:            v76.10
Verification level:            2
Reason for the level:          two size-table entries and a service-side propagation
                               fix. No asset, Gradle, manifest or CI change.
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
APK build run:                 no
ZIP verification:              yes
Maintainer-side verification required: with a custom location set, switch Live
                               Weather on and confirm the scene changes at once;
                               then edit the location and confirm it changes again
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing was seen rendering, and the weather fix has no unit test.** Both changes
  are in the wallpaper Engine, which cannot be unit tested without first being
  decoupled from `Context` — blocker B5. The reasoning is from the code path; the
  device is the check.
- If it still fails, the remaining suspect is unchanged from v76.9: a location fix
  is only ever obtained when `useLocationForSunTimes` or `useCustomLocation` is on,
  and neither is a weather setting. A user with Live Weather on and both off gets no
  fix and therefore no weather, ever.
- D-7 and D-10 remain open and were not touched.


## v76.9 — D-9, B9, Live Weather; D-10 attempted and withdrawn

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

### Live Weather now applies the moment it is switched on

v76.4 made the *preference* wake the weather loop, which is why toggling the switch
stopped being a complete no-op. It was still not enough. The loop's condition has
**two** inputs -- the preference and a location fix -- and only one of them woke it.
Throwing the switch wakes the loop, it finds no fix yet (GPS takes seconds, and the
switch is thrown from the settings screen), does nothing, and goes back to waiting
out its full two-minute tick. That is the "nothing happens until a restart or a
theme change" that was reported: the fetch was two minutes away, not broken.

A fix arriving is exactly as much a reason to re-evaluate as the preference
changing, so it now signals the same conflated channel.

`settings`, `lastLocationFix` and `lastWeatherFetchMillis` are `@Volatile`. Each is
written on the render thread or a location callback and read by the weather loop on
its own coroutine, so their visibility across the two was being left to chance.

### D-9: two different causes behind one symptom

Three sprites were blitted one local unit above the ground line their content bottom
implied. They did not have the same fault.

- **`snowman_body` and `bunny_body` genuinely floated.** Corrected at the call site,
  a whole drawing at a time -- the snowman's face and scarf and the bunny's ears and
  tail move with the body, because what is wrong is where the *drawing* sits, not how
  its pieces register against each other.
- **`penguin_body` was correct all along.** The penguin stands on `penguin_feet`,
  blitted separately at the ground line, so its body is *supposed* to sit above it.
  The fault was the registry declaring the body `CONTENT_BOTTOM_CENTRE` when it is a
  part. Reclassified `PART_LOCAL`.
- **`bunny_body` is a part too**, for the same reason plus a deliberate horizontal
  offset that puts its ears over its head: the ears reach further left than right, so
  the body's content centre is not the animal's visual centre. Reclassified.

`validate` now reports **0 failures**, from 3.

### B9: a saved theme may not carry scene geometry

The rule v76.8 established is now pinned by its own test file. Anything a theme
persists that is really a `SceneSpace` constant is recomputed on load and never
believed; what the theme owns -- how many cars, their colours, their types -- must
survive untouched. The boundary is asserted in both directions, including that a
static object's `scale` stays a variation around 1 rather than becoming a size again.

### D-7: three fidelity tests, left open deliberately

The shipped PNGs came from the V2 library's own rasteriser and the pinned toolchain
renders antialiased edges differently. Nothing about it is visible at runtime.
Closing it means re-rendering 108 sprites at once, which is its own decision with its
own device look. **Not done, as instructed.**

### D-10: attempted, and withdrawn

`normalize --apply` **aborted partway**, on `bar_sign`: "PART_LOCAL no longer holds
after normalisation -- the crop moved the content off the point the rule names." It
had already cropped a run of PNGs before reaching that sprite, so the working tree
was left half-normalised; the sprite set was restored from the v76.8 ZIP and
re-verified at 113 files.

That abort is the finding, and it is not a bug in the tool. Cropping a `PART_LOCAL`
sprite moves its content relative to the local zero its parent composes against, so
the crop and the anchor model disagree for exactly the sprites that make up most of
the set. Reconciling them is design work on the anchor rules, not a mechanical pass,
and every sprite it touches needs its blit origin compensated in the same change --
with a device look, because a mistake there is a visibly misplaced sprite.

**D-10 stays open and needs its own task.** It buys memory only, and half-doing it
would have shipped a scene with sprites in the wrong places.

### Verification

```
Release identifier:            v76.9
Verification level:            2
Reason for the level:          three blit origins, registry classifications, a
                               service-side propagation fix and tests.
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
Python tooling suite:          yes -- 79 tests, 3 failures, all D-7 fidelity
Asset validate:                yes -- 3 failures -> 0
Sprite set integrity:          113 PNGs restored from v76.8 and re-verified after the
                               withdrawn normalisation
APK build run:                 no
ZIP verification:              yes
Maintainer-side verification required: switch Live Weather on and confirm the scene
                               changes without a restart; check the snowman and bunny
                               sit on the ground
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing was seen rendering.** No device, no emulator, no OpenGL.
- The Live Weather fix is reasoned from the code path, not observed. If it still
  needs a restart, the next suspect is the location gate: a fix is only ever obtained
  when `useLocationForSunTimes` or `useCustomLocation` is on, and neither is a weather
  setting.
- D-10 and D-7 remain open.


## v76.8 — custom theme schema 3, and the asset resolver's blind spot

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

Two technical fixes from the post-Group-4 assessment. No renderer or scene logic
was changed.

### Saved themes could drag the road back over the pavement

The custom theme schema was at 2, and the traffic lanes moved three times after
it: v76.5 wrote 0.820/0.855, v76.6 0.818/0.846, v76.7 0.834/0.862. A theme saved
*by* version 2 is stamped 2, so no migration ever runs on it again — while the
painted road is derived from the layout's own lanes. Such a theme pulls the
carriageway back to where it was saved, straight over the strip of ground v76.7
gave the pedestrians. They walk on tarmac.

**A schema version cannot guard this, and that is the actual lesson.** It records
a change of *shape*, and nothing about the shape changed: the field is still a
float and still parses. A migration step catches the payloads written before the
bump and nothing after, so the next time a lane constant moves the defect comes
back — which is exactly what happened between v76.5 and v76.7.

Lane position, speed, direction and loop slot are **scene geometry, not theme
data**. Nothing in the app produces a car anywhere but the canonical lanes, so a
stored lane coordinate can only ever be a stale copy of a constant. It is now
recomputed on **every** load, at any version, by
`SceneObjectCatalog.canonicaliseTraffic`. What the theme keeps is what is genuinely
its own: how many cars, their colours, their types.

Schema 3 is still taken. It has no rewrite step — there is nothing left to rewrite
— and it records that a version 2 payload may hold lane coordinates that describe
no road the app draws.

Two regression tests: one reproduces the exact v76.5 payload and asserts the
restored road clears the pavement; one asserts that no stored lane survives a
load at **any** schema version, which is the guard that outlives the next lane
move.

### D-4: the asset resolver had been blind since v73.11

`callsites._wrapper_bindings` recognised a wrapper only when its first parameter
was literally `Canvas`. The GPU migration changed both of `SceneObjectRenderer`'s
wrappers to `SceneCanvas`, so all sixty of that file's blit call sites stopped
resolving — silently, with nothing failing. The type is now a set, because the
same substitution can happen again: what identifies a wrapper is that its first
parameter is *the drawing surface*, whichever type currently names one.

**Fixing it exposed 131 validation failures, as D-4's own note predicted.** Three
were bugs in the validator itself, invisible while the file it checks could not be
reached:

- The anchor-to-origin comparison never converted units. An anchor is declared in
  the sprite's pixels and a call site writes its origin in the units it blits in,
  so every 3× oversampled sprite disagreed with itself by a factor of three.
- `derive_anchor` had the mirrored bug, converting *to* local units and comparing
  against a pixel declaration.
- The anchor check was applied to `PART_LOCAL` and `DECLARED_ATTACHMENT` sprites.
  A part sits wherever the drawing containing it puts it and a declared attachment
  is positioned by its joint; neither is predicted by its own anchor, and forty-nine
  sprites were reported as failing a rule they never claimed to follow.
- `SPRITE_CENTRE` refused any sprite whose content was not also centred in its
  bitmap. Those are two different statements: a crescent moon's content is
  off-centre by construction and is still placed by the bitmap centre.

Registry data corrected against the shipped PNGs: 15 stale `contentBox` entries
re-measured, 24 anchors re-derived from them, and three sprites reclassified as
`PART_LOCAL` — `house_small_door`, `restaurant_door` and `sailboat_hull` are pieces
of a larger drawing, not sprites placed on their own anchor.

`validate` goes from 131 failures to **3**, and the Python suite from 13 to 3.

### What is left, and why

- **Three sprites sink by one local unit.** `bunny_body`, `penguin_body` and
  `snowman_body` are each blitted one unit above the ground line their content
  bottom implies — consistently, across three unrelated sprites, which reads as an
  authoring convention rather than drift. Correcting it means editing a blit origin
  in the renderer, which this task was not allowed to do. Pinned by a test that
  fails if any of them moves by something other than one unit.
- **Three fidelity tests fail, and they are D-7.** The shipped PNGs came from the
  V2 library's own rasteriser; re-rendering through the project's pinned one
  diverges at antialiased edges. Closing them means re-rendering 108 sprites at
  once, which is D-7's own decision.
- **35 sprites still carry croppable padding**, 2.84 MB of the decoded total. The
  V2 library never went through Phase 3.3's normalisation pass. Cropping shifts
  content inside the box, so each one needs its blit origin compensated in the same
  change — a task with a device look attached. Pinned as a count.

### Verification

```
Release identifier:            v76.8
Verification level:            2
Reason for the level:          persisted-data handling, offline tooling and its
                               registry data. No renderer, asset, Gradle or
                               manifest change.
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
Python tooling suite:          yes -- 78 tests, 3 failures, all D-7 fidelity
Asset validate:                yes -- 131 failures -> 3
APK build run:                 no
ZIP verification:              yes
Clean build from extracted ZIP: no
Maintainer-side verification required: load a custom theme saved on v76.5 or v76.6
                               and confirm the road sits where v76.7 put it
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing was seen rendering.** No device, no emulator, no OpenGL.
- The three one-unit sinks, the three D-7 fidelity failures and the 35 padded
  sprites are recorded above and open.


## v76.7 — Group 4 final device tuning

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

The last Group 4 pass, from a second Pixel 9 verification. Dolphins, sailboats,
mountains, the GPU renderer and the depth model are untouched, as instructed.

### The pedestrian band

The largest change, and the one the rest follows from. People were walking below
the road's lower edge, where they read as standing on the tarmac and as having
nothing to do with the village behind them.

Both lanes moved down by 0.016 of screen height **keeping their spacing**, so the
carriageway is in exactly the same place relative to its own traffic and is
exactly as wide as it was — 145 px on a 2400 px screen. What the move opens is a
strip of ground between the buildings and the road, and the two pavement rows now
sit in it, at 0.795 and 0.807 against an object band that ends at 0.790 and a road
that starts at 0.818.

**People are drawn considerably smaller as a result, and that is the projection
working rather than a regression.** They are further away now and are charged for
it exactly as everything else is. `PERSON_METRES_TALL` carries a small reduction
on top, 2.0 → 1.9, for the foreground row reading slightly overscaled; almost all
of the visible change is the move.

A new test asserts that a near-row pedestrian clears the far lane's cars. People
are drawn after the vehicles, so an overlap would paint a pedestrian over a car
standing closer to the viewer than they are.

### The reference line is no longer a lane

`REFERENCE_Y_FRACTION` was defined as `ROAD_LANE_NEAR_Y_FRACTION`. That made the
metre a function of a composition element: moving the road one step down would
have rescaled every object in the scene, because the projection's denominator
moved with it. It is now its own constant, keeping the value the lane happened to
have, so nothing changed size when the two were separated.

This is why the road could be moved at all without re-tuning the whole table.

### Tree lights

They hung out of the bottom of the canopy. The cloud reached y=-2 against a
canopy whose content stops at -6, so the lowest lights were below the leaves and
out over the trunk, and the highest reached barely half way up a crown twice as
tall as the cloud — neither number was derived from the artwork they were meant to
be scattered across.

The offsets are now a **unit disc**, and each caller passes its own foliage's
measured half-extents: (0,-43) with 30 × 26 for the leafy canopy, derived from
`tree_canopy`'s content box, and (0,-72) with 13 × 10 for the palm's frond fan,
inset further because a fan is mostly gaps. Lights are inside the foliage by
construction, whatever it is next redrawn to.

They are also drawn **inside** each plant's sway transform now, so they lean with
the branches instead of staying rigid while the leaves move around them.

### Parasols

2.3 m → 2.9 m. They had shrunk out of the composition.

### Verification

```
Release identifier:            v76.7
Verification level:            2
Reason for the level:          scale constants and one decoration placement.
                               No asset, Gradle, manifest or CI change.
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
APK build run:                 no
Mutation testing:              not repeated
ZIP verification:              yes
Clean build from extracted ZIP: no
Maintainer-side verification required: local APK build, install and a visual pass
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing here has been seen rendering.** No device, no emulator, no OpenGL.
  The composition was re-derived arithmetically from the four screenshots.
- The road's lower edge now sits at 0.878 of screen height, closer to where a
  launcher dock overlays the wallpaper. Worth a look on the device.
- The asset pipeline's 9 test failures and `validate` disagreements, recorded at
  v76.6, are still open and still belong to the assessment.


## v76.6 — Group 4 final proportion and readability tuning

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

Closes Group 4. Tuning only: no new logic, no architectural change,
`SceneSpace` remains the single source of truth and no parallel scale was
introduced. Driven by four Pixel 9 screenshots.

### The size table

The heights in `SceneSpace.SceneVariant` are now stated as what an object should
**read as** rather than as physical measurements. They started from real-world
sizes and stay within sight of them, because a table anchored to something real
is the only kind that can be argued about, but a wallpaper is looked at for a
second at arm's length and a few entries needed to serve legibility instead.
Every departure is recorded beside the number it changes.

| | was | now | why |
|---|---|---|---|
| Person | 1.75 m | **2.0 m** | a readable silhouette and no more; the scene should have people in it |
| Car | 1.55 m | **1.45 m** | the V2 car sprite is stubby (100 units long, 48 tall), so matching its height exactly read as bulky beside a person |
| Fire engine | 3.1 m | **2.9 m** | follows the car |
| Tower | 20 m | **17 m** | dominated the foreground it is meant to stand behind |
| Tree | 9 m | **9.8 m** | presence beside the houses |
| Gift | 0.6 m | **0.95 m** | read as a speck |
| Lake metric | 15 px/m | **21 px/m** | boat and dolphin were right against each other and nearly invisible on screen; one number moves both and preserves their ratio |

### The road

The carriageway read as a dark band with the traffic sitting inside it with room
to spare. Lane spacing narrowed from 0.035 to 0.028 of screen height and the
shoulder from 0.22 of a lane half to 0.16; because the road's edges are derived
from its lanes, that narrows the strip with them — 145 px against 220 on a
2400 px screen, against a near-lane car 58 px tall and 121 px long.

Lanes moved to 0.818 / 0.846 and the pavement rows to 0.886 / 0.906. Clearance
above the road is 28 px, so nothing standing in the object band is covered; below
it is 57 px. The two lanes remain separated and the traffic behaviour, direction
and spacing are untouched.

### Snowman readability

A white snowman on white winter ground was separated from its background by
nothing but antialiasing. Fixed in the **asset**, not with a runtime effect: a
tonal rim inset into the silhouette, so the outer radii — and therefore the
bounding box, the declared `contentBox` and every anchor measured against them —
are unchanged.

The rim is a **neutral grey**, not the cool blue-grey it wants to look like. A
`TINTABLE` sprite has to be authored as a colourless mask, because the runtime
multiplies it by the user's colour and multiplying one hue by another compounds
them; `SpriteTintClassTest` caught the first attempt, which used a cool tone. The
neutral rim is the better answer rather than merely the permitted one: it
inherits whatever hue the user chose instead of arguing with it, and the winter
palette is already cool.

### Asset pipeline: the registry could not be loaded at all

Found while regenerating the snowman. `registry.py` still declared
`SCHEMA_VERSION = 3` and an `ANCHOR_RULES` tuple without `PART_LOCAL` or
`DECLARED_ATTACHMENT`, while `sources/sprites.json` is at schema 4 and uses both.
Every command that loads the registry — `render`, `validate`, `compare`,
`normalize`, `all` — failed before doing anything.

The loader already reads and validates every field the version 4 document
carries, and both anchor rules are documented in `DESIGN_NOTES.md`; the code was
simply stale. Corrected. **This is offline tooling; Gradle never runs it and the
app does not depend on it.**

With the tool running again, its own suite reports **9 failures out of 76** and
`validate` reports call-site disagreements, including the pre-existing ones for
`bird_body`, `cloud_body` and `star_sparkle` that no part of this release
touches. Those are the tooling's accumulated backlog against the current shipped
set, now visible rather than hidden behind a load error. **They are not Group 4
work and were not addressed here** — they belong to the comprehensive assessment.

### Verification

```
Release identifier:            v76.6
Verification level:            2
Reason for the level:          scale constants, one regenerated sprite, offline
                               tooling. No Gradle, manifest or CI change.
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
Asset pipeline:                probe matches the pinned toolchain hash; render,
                               inventory, validate, compare re-run
APK build run:                 no
Mutation testing:              not repeated; v76.5's two checks still stand
ZIP verification:              yes
Clean build from extracted ZIP: no
Maintainer-side verification required: local APK build, install and a visual pass
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing here has been seen rendering.** No device, no emulator, no OpenGL.
  The proportions were re-derived arithmetically from the four screenshots and
  the snowman was checked as a composited still, not on a phone.
- The mountains' silhouette, the layering, the depth model, the traffic
  behaviour, the GPU renderer and the Live Weather path are untouched.
- The asset pipeline's 9 test failures and `validate` disagreements are open.


## v76.5 — Group 4: perspective, scaling and proportions

**Beta / pre-release, on top of stable v76. `versionCode` unchanged at 76.**

The whole of Group 4, in one pass, at the maintainer's instruction.

### What was wrong

Measured on a 1080x2400 screen before this release: a person 67 px tall, a car
96 px, a restaurant 103 px, a small house 228 px. A car was drawn taller than a
person and a commercial building shorter than one. Three causes, all structural:

1. **No single owner.** Four multiplicative factors -- `spec.scale`,
   `GLOBAL_OBJECT_SCALE`, `depthScaleFor` and a `canvas.scale` correction inside
   each house drawing -- spread across three classes.
2. **The sprites are authored at incompatible internal scales.** Measured on
   their own artwork the V2 set runs from ~13 local units per metre for a shop
   front to ~46 for a person. No set of hand-written per-category multipliers had
   ever corrected for that, and none could: each was expressed against its own
   sprite's arbitrary scale, so no two were comparable.
3. **The depth band had collapsed.** Every static object stood between 0.704 and
   0.7505 of screen height -- 111 px -- with 1.51x between the smallest and
   largest. Cars and pedestrians were outside the depth system entirely, at fixed
   scales and hardcoded ground lines.

### What changed

**`SceneSpace` (new)** owns the ground plane, the horizon, the perspective, the
road, the pavement, the traffic speeds and the size of every category. Pure
Kotlin, no Android types, fully unit-tested. Four stages, each answering one
question, and no stage may compensate for another:

```
finalScale = variantScale x sizeVariation x perspectiveScale(y) x sceneScale(height)
```

- **The size table is derived, not authored.** Each category declares the real
  height it should read as and the local-unit height its drawing occupies; the
  base scale falls out. A person is 1.75 m, a car 1.55 m, a cottage 5.8 m, a tree
  9 m, a tower 20 m. Height is the governed dimension and width follows the
  artwork, because the V2 sprites are stylised and governing width instead makes a
  person shorter than a car again. Full table in `DESIGN_NOTES.md` §5.
- **Perspective is proportional to the distance below the horizon**, which is
  what a flat ground plane seen from a fixed viewpoint does. Static objects, both
  traffic lanes and both pavement rows read the same function, so relative sizes
  and speeds follow from ground lines rather than being kept in step by hand. The
  far lane's speed and the far pavement's are now *derived* from the near ones.
- **The depth range is uncapped.** `ROAD_SAFE_DEPTH_MAX` existed because the road
  was drawn over anything lower; the object band is now above the road by
  construction, with the margin asserted in `SceneSpaceTest`. The band is 206 px
  and spans 2.75x.
- **Sizes scale with screen height** against a 2400 px reference. They were
  absolute canvas pixels while every ground line was a fraction of screen height,
  so the composition only worked on one device.
- **The road is derived from its lanes**, symmetric about the centre line, with
  the shoulder expressed as a fraction of the lane spacing. The 55-unit top margin
  is gone: it existed to keep a car cabin inside the strip, which is not what a
  road edge is for, and Group 4 removed the reason by making the vehicles the
  right size.
- **A building's style comes from its depth**, not from a position hash, so towers
  sit on the skyline and shop fronts among the houses.
- **Re-anchoring.** Pedestrians were drawn four units into the ground; window
  occupants were centred on their canvas rather than placed from their declared
  `CONTENT_BOTTOM_CENTRE` anchor; the dolphin's origin was neither its canvas
  centre nor its content centre. All are now `placement - anchor` with the anchor
  named as a constant.
- **The lake has its own metric** because it sits at the horizon where the ground
  projection is zero. Its only job is keeping a 2.6 m dolphin right against a
  6.5 m sailboat -- the animal used to be drawn longer than the boat.
- **Custom themes migrate to schema 2.** `StaticSceneObject.scale` changed meaning
  from an absolute size to a variation around 1, and the lanes moved. Both are
  quiet breaks: the payload still parses and renders wrong. Saved cars are moved
  onto the canonical lanes, given one speed per lane and spaced evenly around the
  loop.

### Car density no longer resizes the road

Reported from a device against the Group 4 build, and fixed before release.

`drawRoad` derived its top and bottom edges from the lane span of `carRuntimes`
-- the car list *after* density thinning. Moving the Cars slider therefore
changed the road's geometry: at a low setting only one lane survived, the span
collapsed to zero and the painted strip collapsed with it; at zero the road
disappeared entirely. The defect predates Group 4 -- the same list was read
before -- but the old code added a fixed 55/12 local-unit margin that masked it,
and deriving the margin from the lane spacing exposed it.

The road is terrain. Its edges now come from the lane span of the theme's whole
`SceneObjectLayout`, computed once at construction and off the frame path, and
it is drawn whenever the theme has a road and the Cars category is switched on.
Density is not consulted at all, so the geometry is identical at 0 %, 50 % and
100 %. A degenerate span -- every car on one lane fraction, which is what a
pre-v76.2 custom theme has -- falls back to the canonical lane spacing rather
than to zero.

**The other density controls were audited** for the same coupling: clouds,
mountains, birds, precipitation, lake decorations and stars read density for
presence or count only, and none of their bands, heights or widths depends on
it. `lake.height` is a genuine geometry control and a separate slider.

**No artwork changed.** The mountains' silhouette was not touched.

### Verification

```
Release identifier:            v76.5
Verification level:            2
Reason for the level:          Kotlin source, tests and persisted-data migration;
                               no Gradle, manifest, CI or asset-pipeline change.
                               The breadth would ordinarily justify Level 3; the
                               maintainer directed assembleDebug be skipped.
Tests run:                     yes -- ./gradlew testDebugUnitTest
Lint run:                      yes -- ./gradlew lintDebug
APK build run:                 no
Static / bytecode checks:      draw-path review of the changed call sites; the new
                               per-frame work is arithmetic on primitives only
Mutation testing:              yes -- two targeted mutations, both caught:
                               widening the object band breaks the road-clearance
                               invariant; removing the degenerate lane-spacing
                               guard breaks the road-density regression test
ZIP verification:              yes
Clean build from extracted ZIP: no
Maintainer-side verification required: local APK build, install, and a full visual
                               pass. This is the release where that matters most.
Release identifier verified unique: yes
```

`assembleDebug intentionally skipped under normal verification policy.`

### Known limitations

- **Nothing in this release has been seen rendering.** Claude has no device, no
  emulator and no OpenGL implementation. The composition was checked against a
  mockup composited from the real sprites at the real numbers, which establishes
  the geometry and nothing about how it looks on a phone.
- **Visual approval was not obtained before implementation**, contrary to
  `AI_PROJECT_RULES.md` §13, because the maintainer directed an
  implementation-first pass.
- Pedestrians are still outside `GroundGeometry`: they do not tile or scroll with
  the terrain. That is Group 5.1.
- Roof snow (D-8) still needs artwork.


## Current version

| | |
|---|---|
| **Version** | **v2.8 — Stable / latest** (`versionCode = 12`, `versionName = "2.8"`) |
| **Latest stable** | v2.8 |
| **Date** | 2026-08-20 |
| **Build status** | ⚠️ `testDebugUnitTest` **407 passing, 0 failures**; `lintDebug` **41 warnings, 0 errors, 0 fatal**; Python tooling **96 tests, 0 failures**; asset `validate` **0 failures across 125 sprites**; `normalize` **0 targets pending, 15 excluded by decision**. **`assembleDebug` was not run and no APK was produced** — Level 3 |
| **APK size (debug)** | Not measured. Last measured: **19,017,989 bytes** at v75 |
| **Sprite memory** | **125 PNGs, 15.51 MB decoded, 1.63 MB of it padding** — re-measured at v2.8, which added the tower entrance and the two fir sprites and re-authored seven others |
| **Tests** | 407 Kotlin unit tests, 96 Python tooling tests |
| **Device verification** | ⚠️ **Four device passes (v76, v76.1, v76.2, v76.3), twenty-five defects between them, all fixed except roof snow.** **A sixth device pass, on v76.6, produced this release's tuning list; it confirmed the dolphins and sailboats as correct.** v76.7's own result has not been seen on a device |

---

## v76.4 — fourth device pass: road geometry, facade placement, lake life, live weather

**Date:** 2026-08-19. Beta on top of v76; `versionCode` and `versionName` unchanged.

Seven fixes, one refusal.

### The road was asymmetric by construction

`drawRoad` reached 55 units above the highest lane and 12 below the lowest, so the
far lane's half of the strip was two and a half times the near lane's — one lane
read as a road and the other as a verge — and the top edge rode up over the ground
the houses stand on. The 55 was there to keep a car's cabin inside the strip, which
is a losing argument at the current proportions: a car is taller than its own lane
half whatever this edge does.

The strip is now symmetric: each lane owns half of it, the road extends beyond the
outer lanes by half the lane spacing plus a small shoulder, and `midY` lands exactly
between the lanes because it always did — it was the *edges* that were lopsided.
Both lanes moved up slightly (0.771 / 0.803) to keep the lower edge clear of the
pavement. **The far lane's cabins now overlap the ground above the road**, and that
is the global proportion problem, which is Group 4.

### Facades were built off-centre

The large house's four windows sat at -46 and 24 on a wall running -70..70 — 15
units of wall to one side of the pair and 33 to the other. They are centred on the
door now, which was already at 0. The small house's window and door were 2 units
off mirror symmetry and were squared up in the same change; the restaurant's window
moved 3 units to match its door's offset on the other side.

### Mountains were two mountains

`drawSoftMountain` filled its two halves at +10 % and −8 % of the layer colour to
fake a paper fold. Against the V2 palette that is not a fold, it is a hard vertical
seam straight down the peak — which is precisely where a fold would not be. One
colour per mountain; the only division left is the one the hills make by overlapping
them, which is the division the whole scene is built on.

### Dolphins were gliding, and were sharks

Two separate faults. The animation drew the sprite every frame with a ±10 unit bob,
so it slid across the surface permanently visible. It is now drawn **only while
above the water**: `arc` is the positive half of a sine, the animal is skipped
entirely while it is negative, and the tilt follows the arc's own slope via a new
`SceneTime.cosAt`. No clipping is involved and none is available at the `SceneCanvas`
seam.

The artwork was the other half: pointed snout, tall triangular dorsal, no eye or
mouth. Redrawn as a porpoise, and mirrored — lake decorations only ever drift right,
and it was facing left.

### The sailboat was two objects

The sail was blitted after the hull and four units to its right, so its foot sat on
the deck planking off to one side. Sail first, hull over it, and the sail's 70 units
of content centred on the hull's 84: the gunwale covers the foot and the mast reads
as stepped into the deck amidships.

### Live Weather could not take effect

The switch only ever reached the scene through a polling loop that ticked every two
minutes and then refused to fetch unless an hour had passed since the last one — so
turning it on typically did nothing until the service was restarted or a theme
change rebuilt everything, which is exactly what was reported. The settings collector
now clears the refresh timer and wakes the loop through a conflated `Channel`, and
the loop waits on that channel or the tick, whichever comes first.

### Cars carry passengers

Every car had exactly one occupant. A car may now also carry a passenger — another
adult, a boy or a girl — in the rear pane of the glass, on the far side of the
pillar from the driver, so the two cannot overlap.

**A child can never be drawn driving, and that is structural rather than a check.**
The driver is selected from `personCarHeadDrawables`, which contains the man and the
woman and nothing else; the passenger is selected from `personWindowHeadDrawables`,
which contains all four. There is no child driving head to reuse and none was
invented, precisely so that a later edit cannot put one in the driving seat by
changing an index.

### Refused: snow on roofs

Houses, shops, bars and towers show no snow in the winter and Christmas themes. This
is **not** a placement or a lost call: the V2 asset set has no roof snow for them.
The trees work because they have their own `tree_canopy_snowcap`; the five building
types have nothing equivalent. Fixing it means drawing snow caps for each roof shape,
which is artwork with a visual approval attached, and the shortcut — tinting the
roof masks toward white in winter — would repaint the whole roof rather than settle
snow on it. Recorded rather than improvised.

### Verification

289 tests, 0 failures. `lintDebug` 41 warnings, 0 errors. `assembleDebug` was not
run, no APK was produced, no Git tag was created.

**None of these fixes has been seen on a device.** The sailboat, the dolphin and the
facade changes were checked by compositing the real PNGs at the renderer's own local
coordinates. The road geometry was checked arithmetically against a 2424 px screen.
The live-weather and passenger changes have **no visual check at all** — one is a
timing path and the other only shows on a car that happens to roll a passenger.

---

## v76.3 — third device pass: animation, traffic behaviour and two redrawn sprites

**Date:** 2026-08-18. Beta on top of v76; `versionCode` and `versionName` unchanged.

Six defects from a Pixel 9. Two are artwork, three are placement or behaviour, and
one — the traffic — turned out to be two separate causes that had been hiding each
other.

### Traffic: two causes, not one

v76.2 gave the road two lanes and tied direction to lane, and the road still looked
congested. The lanes were not the whole problem.

**Cause one: speed was per car.** `speedFraction` was rolled across 0.05–0.14, a
factor of nearly three, so within a single crossing the fast cars in a lane caught
the slow ones and drove through them. A lane is a queue, and a queue only holds its
spacing if nothing in it overtakes. Speed is now a property of the lane.

**Cause two: the wrap discarded phase.** `if (progress > 1.3f) progress = -0.3f`
snapped every car back to the same point at the end of its lap, throwing away the
head start it had over the car behind it. One lap was enough to collapse a lane
into a pack. It now subtracts the 1.6 span instead, which preserves phase
indefinitely.

With both fixed, `startDelaySeconds` could stop being a random delay and become an
even division of the loop: each lane has five slots and the nth car starts one
fifth of the span behind the one ahead. That spacing is now permanent rather than
initial.

Lane separation also went from 0.0225 to 0.028 of screen height — 68 px on a
2400 px screen against a car 78 px tall — which cost 15 px at the road's top edge
and nothing at the bottom.

One consequential side effect: the driver-head seed was derived from
`speedFraction`, which is now shared within a lane, so every car in a lane would
have had the same driver. It reads `startDelaySeconds` instead, which is unique per
candidate.

### Animation: the reindeer

They stopped moving their legs in v73, when the sleigh, Santa and both reindeer
became one sprite. A bitmap cannot bend, so the trot is a **second drawing**:
`santa_sleigh_trot`, alternated at `SANTA_TROT_FRAMES_PER_SECOND`. Both frames are
emitted from one description with a leg-phase parameter, so they cannot drift
apart, and the two reindeer carry opposite phases within a frame so the pair never
steps in unison.

### Artwork: two sprites redrawn

**`bird_body`** was a flat angular M whose wing tips pointed downward — a bat at the
size it is drawn. It is now a gull: swept wings, a body, a head and a beak. It is
symmetric about its own horizontal centre **on purpose**, because `drawBirds`
animates the flap by mirroring the sprite vertically; anything above the centre line
would spend half the cycle below it. The head points right because birds only ever
drift right.

**`santa_sleigh_scene`** got the cozier Santa the maintainer asked for: rounder coat,
fuller beard with a rounded hem, rosy cheeks, a smile, a slouched hat with a larger
pompom, a mitten on the reins, and a belt that stops at the coat rather than running
across the sleigh. The sleigh's own geometry and the effect's logic are untouched;
the content box moved by one unit vertically and the origin constant followed it.

### Placement

- **Snowman arms** were drawn at y=-44, which is inside the head sphere (-61..-39 on
  the V2 body), so the twigs appeared to be stuck through his face. They start from
  the torso at -30, where the lower sphere reaches ±15.7.
- **Car windows.** v76.2 centred the glass on the greenhouse measured at the glass's
  own mid-height, which is arithmetically centred and still reads wrong: the
  greenhouse is not symmetric, so a centred glass runs its vertical rear edge into the
  roof's rear curve and leaves no C-pillar while the raked front keeps a wide band.
  Four units forward gives it a pillar at each end. **The glass was not reshaped.**

### Verification

289 tests, 0 failures. `lintDebug` 41 warnings, 0 errors. `assembleDebug` was not run, no APK was
produced, no Git tag was created.

**None of these fixes has been seen on a device.** The placement and artwork changes
were checked by compositing the real PNGs at the renderer's own local coordinates;
the traffic change was reasoned from the arithmetic of the loop and is the one here
with no visual check at all, because spacing over time cannot be composited.

**Known limitation carried forward:** car objects are persisted inside custom themes,
so a custom theme saved before v76.2 keeps its old single-lane layout until it is
regenerated. Built-in themes are generated per run and are unaffected.

---

## v76.2 — placement and direction cleanup after the V2 integration

**Date:** 2026-08-18. Beta on top of v76; `versionCode` and `versionName` unchanged.

Eight defects reported from a Pixel 9, plus four more found by inspecting the rest
of the integrated set for the same failure modes. **No artwork changed in this
release.** Every fix is a placement, a direction or a count — which is what a
wholesale asset replacement leaves behind when the call sites keep numbers that
described the previous drawings.

### Direction: the V2 vehicle art faces the other way

`car_body`'s long bonnet is at its **left** end and `car_window`'s raked edge is on
the same side; the sleigh's reindeer are drawn at the left of their sprite and pull
away from it. The shipped art faced right. The flip sign was not revisited when the
artwork was replaced, so every car on the road and Santa above it were mirrored and
drove backwards. `dir` is inverted in `drawCar` and in `SantaSleighEffect.draw`,
with the evidence written at both call sites so the next art pass can re-derive it
rather than guess.

### Traffic: one lane carrying both directions

`generateCarCandidates` drew `laneYFraction` from `0.79 + rnd * 0.015` and
`reverse` from an independent coin flip. That is a 36 px band on a 2400 px screen
against a car 78 px tall, so the entire fleet shared one lane and oncoming traffic
drove through it; three or four candidates could stack into an apparent pile-up.

Lane now comes from the candidate index, so both lanes are always populated at any
density, and **direction follows from lane**: near lane rightward, far lane
leftward. Two more things had to move with it, and neither is cosmetic:

- `buildCarRuntimes` sorts by lane, far first. Draw order is depth order, and index
  parity alternates lanes, so without the sort a far car would paint over the near
  car it was passing.
- The dashed centre line was `(top + bottom) / 2` of the painted strip. The strip is
  not symmetric about the lanes — it reaches 55 units above the highest lane to
  clear a cabin and 24 below the lowest — so its midpoint sat above every car on the
  road. It is now halfway between the two lanes' own ground lines.

`ROAD_BOTTOM_MARGIN_UNITS` went from 24 to 12 so the widened road keeps clear of
the pavement at 0.83 of screen height. The road's **top** edge is unchanged, by
choosing the far lane to be the value the old single band was centred on: nothing
standing beside the road gets covered.

### Placement: seven sprites positioned against drawings that no longer exist

| Sprite | Was | Now | What it looked like |
|---|---|---|---|
| `car_window` | (-31,-10) | (-19,-7) | Glass overhanging the bonnet, above the roof line |
| `police_stripe` | (-70,27) | (-34,13) | A loose bar on the road under the car; the white car unmarked |
| `police_lightbar` | (-11,-18) | (-11,-17) | Floating one unit above the roof |
| `taxi_checker` | (-35,23) | (-34,13) | Straddling the body's floor and the wheels |
| `snowman_nose` | (11,-64) | (4,-52) | Level with the hat brim |
| `snowman_scarf` | (-12,-54) | (-12,-41) | Across the middle of the face |
| `penguin_beak` | (-6,-46) | (-6,-37) | On top of the head, above the eyes |
| `bunny_innerear` | (6,-58) | (-4,-58) | Covering one ear, the other patch in mid-air |

The snowman's twig arms, drawn in code, started at ±15 where the V2 sphere reaches
±12 at that height, and were pulled in to ±11.

Every one of these was derived by measuring the new artwork — the snowman's neck is
its narrowest row, the bunny's ears occupy x -9.3..15.3 — and then composing the
real sprites at the renderer's own local coordinates and looking at the result.

### Count: one bird is one bird

v76 read the asset package's note that `bird_body` had stopped being "a three-bird
strip" as an instruction to place it three times, and drew a flock at a third of the
size. **The shipped 420×65 sprite was never three birds**: it was one wide gull,
and the historical `15/70` divisor brought its 420 px down to a 90 px wingspan. The
V2 bird is 90 px wide, so it is blitted at its own size and reaches exactly the
wingspan the old one did. The flock offsets are gone.

### Reported, and deliberately not done

**"The ambulance still renders as a white car."** There is no ambulance in this
project. `CarType` is `PLAIN`, `POLICE`, `TAXI`, `FIRE_TRUCK`, and the white vehicle
is the police car — which had *no visible markings at all*, because its livery
stripe was being drawn on the road underneath it. That is the same defect as the
white/black line reported beneath it, and fixing the stripe fixes both: the vehicle
now carries a navy-and-cream stripe along its doors under a red-and-blue lightbar.
**Whether the project should also have an ambulance is a content decision, not a
defect**, and it is not taken here.

**Global proportions** between people, cars, houses and trees were reported as wrong
and are explicitly out of scope: that is Group 4.

### Verification

289 tests, 0 failures. `lintDebug` 41 warnings, 0 errors. `assembleDebug` was not
run, no APK was produced, no Git tag was created.

**None of this has been seen on a device.** The placement fixes were checked by
compositing the real PNGs at the renderer's own local coordinates, and the lane
geometry by computing the road band, the two lanes, the centre line and the
pedestrian line against a 2424 px screen and drawing the result. Both are better
arguments than reading the code; neither is an observation of the app.

---

## v76.1 — four defects found on a device against the V2 artwork

**Date:** 2026-08-18. Beta on top of v76; `versionCode` and `versionName` unchanged.

The maintainer ran v76 on a Pixel 9 and reported four things. Every one of them is
in the artwork or in the single number that places it — no scene logic changed,
and the renderer was not touched.

### 1. The moon had a vertical cut down its right side

**Cause: the sprite overflowed its own canvas.** `moon_crescent` closes the lit
limb with a terminator arc, and the shipped path used `A52 34 0 0 0` — an ellipse
whose 52-unit x-radius bulges 12 units past the disc's own 34 and 12 past the
80-unit canvas. The rasteriser clipped it at the canvas edge, which is exactly the
straight vertical line that showed on the device. It was not an anchor, a content
box or a UV problem: the PNG itself already contained the cut, measurable as a
content box reaching x=240 of 240.

`moon_gibbous` had the mirror error — `A20 34 0 1 0`, a large-arc flag on an
under-sized radius — and drew a thin crescent where a gibbous belongs, with its
crater circle stranded outside the lit shape as a floating dot. Nobody reported
it because the phase only comes up for part of the month.

Both terminators are now arcs that stay inside the disc: `A20 34 0 0 0` for the
crescent, `A18 34 0 0 1` for the gibbous. The four phases composite over the dark
earthshine disc as four clean discs.

### 2. Car-driver heads sat below the window

**Cause: the head was placed by centring its canvas.** The call site read
`drawSprite(driverRes, -27f, -27f)` under `scale(0.24)`, which centres a 60×60
sprite on the anchor point — correct for the sprite that existed when it was
written. The V2 head is 171×162 with a declared `CONTENT_BOTTOM_CENTRE` anchor, so
centring its canvas put the bust's shoulders a third of the way down the door,
outside the glass.

The origin is now `placement − anchor`: the declared anchor is subtracted so the
bust's content bottom-centre lands on the bottom-centre of that vehicle's glass.
**The artwork was not touched** — re-cutting the head to compensate for a call-site
number is the failure mode `DESIGN_NOTES.md` records against five earlier releases.

The four car-head sprites declare 84 or 86 px on x; `CAR_HEAD_ANCHOR_X_UNITS` is
the midpoint, because the 2 px spread is 0.4 px on screen after the head scale and
`GLOBAL_OBJECT_SCALE`.

### 3. Snow did not cover the treetop

**Cause: the cap was cut for a different crown.** `tree_canopy_snowcap` came
across from a canopy whose outline the V2 tree does not have. Measured against it,
the cap's ridge sat 2 units *below* the crown's own, and its corners fell 5 units
short of each shoulder — so a green rim showed above the snow and both shoulders
stayed bare.

Redrawn at 234×126 with its top edge repeating the crown's own upper vertices, so
the snow reaches the ridge and both shoulders exactly, and falls away below with an
uneven edge over a shadow band. The origin moved from `(-36,-78)` to `(-42,-82)`,
derived from the canopy rather than guessed. The V2 look is kept; nothing reverted
to the old design.

### 4. The fire truck was a red car

**Cause: it shared `car_body`.** Every vehicle type was the same low-sedan
silhouette differing only in tint and one accessory, which is fine for a taxi and a
police car and wrong for a fire engine. The ladder made it worse rather than
better: at `(-60,-32)` it cleared the sedan roof entirely and hovered above the
vehicle, which is the floating ladder visible in the device screenshots.

`firetruck_body` (300×162, fixed art) is new: a flat roof at local y=−16 against
the sedan's −11, a cab with its own window, a cream stripe over three equipment
lockers, and a dark chassis bar the wheels sit into. `firetruck_ladder` is
unchanged and now drawn **first**, at `(-48,-31)`, so the body's roof line paints
over its lower rail and it reads as carried rather than hovering. The two warning
lights are unchanged and land on the rack between the rails.

### Rasterisation note

The four regenerated PNGs were rendered through the project's own pipeline with
the pinned `resvg_py`, and `paperscrape-assets probe` reports
`matches_expected: true`. **The other 108 sprites were rendered by the V2 library's
own tool**, so their antialiased edges carry a slightly different signature. The
difference is confined to edge pixels and does not affect geometry, but it means
`compare` will report the untouched sprites as differing from their sources until
the whole set is re-rendered — a decision for whoever takes defect D-4.

### Verification

289 tests, 0 failures. `lintDebug` 41 warnings, 0 errors. `assembleDebug` was not
run and no APK was produced, on instruction. No Git tag was created.

**None of these four fixes has been seen on a device.** They were checked by
composing the real sprites at the renderer's own local coordinates and looking at
the result, which is a better argument than reading the code and still not an
observation of the app.

---

## v76 — the V2 asset library

**Date:** 2026-08-18. Stable, `versionCode` 76, no intervening beta.

The whole runtime sprite set was redrawn from zero and replaced in one change:
108 PNGs out, 111 in. The scene logic is unchanged; what moved is the artwork,
the call sites that had to follow its new geometry, and the classification rules
that had been describing intent rather than bytes.

**Why this is a release and not an asset swap.** Four defects and one blocker were
open against v75, and every one of them was really a statement about the artwork
rather than about the code. They are all closed here by the library, not by
patches.

### What the library changed

| | v75 | v76 |
|---|---|---|
| Files / unique contents | 108 / 102 | **111 / 111** |
| Decoded `ARGB_8888` | 16.14 MB | **14.43 MB** |
| Off the 3× authoring grid | 5 | **0** |
| Sprites with a committed source | 22 | **111** |
| Variant groups still an `IDENTICAL_GAP` | 6 of 18 | **0 of 18** |
| Orphan drawables | 7 | 4 |

Six sprites are new, and each replaces something the renderer used to draw in
code: `tree_trunk`, `rainbow_arc`, `firework`, `lightning_bolt`,
`house_window_lit` and `skyscraper_wall_lit`.

### Closed

- **B1 — the asset generators are lost.** Partially lifted in Phase 3.1, which
  reached 22 of 108 sprites; the rest could not be recovered because a
  best-scoring fit over free parameters is a redraw presented as a recovery. The
  V2 library sidesteps recovery entirely by shipping sources for artwork drawn
  from zero. **Group 4 is no longer blocked.**
- **D-6 — the balloon basket draws white.** It was a pure-white mask blitted
  untinted, and white is the `MULTIPLY` identity. The V2 basket is wicker brown.
  The five other sprites the v75 re-measurement found sharing the profile are
  resolved the same way, or no longer exist.
- **D-2 — an Italian caption rasterised into `santa_sleigh_scene.png`.** The
  sprite was redrawn at 624×168 and the caption is not in the new artwork —
  read off the file rather than assumed from the redraw. The original entry's
  caveat still applies to the new set: the heuristic that missed this caption
  cannot certify the other 110 sprites.
- **D2 — should the seasonal head sprites differ?** Resolved in v74.2 as "yes,
  and the artwork does not exist". It exists now: hat, scarf, hood, raised
  collar, cold cheeks. All 18 variant groups are `DISTINCT` and the shipped set
  contains no byte-identical pair at all.

### The tint classification, and what it costs

`DESIGN_NOTES.md` decision 25 supersedes decision 23. A sprite's class is now a
property of its bytes: tintable means a greyscale mask, fixed art means the PNG
carries its colours, and `SpriteTintClassTest` asserts both directions across all
111 sprites.

Decision 23 had allowed a fixed-art sprite to be a mask coloured at the blit. It
was a correct repair for artwork that did not honour its own classification — it
is what fixed the white dolphins in v74.1 — but it left the class undecidable
from the file, which is how the defect got in. Roughly a dozen accent constants
existed only because of it, and all of them were deleted rather than moved: the
penguin's beak and feet, the bunny's inner ear, the gift ribbon, the house
planter, the skyscraper's lit and dark window, the tree trunk, and v74.1's three
lake-decoration colours. Two survive, and both are the cases that were never
about a sprite: the parasol pole, which is a `drawRect`, and the penguin belly,
whose sprite is still a mask.

**Five user-visible behaviours are retired as a consequence, deliberately and
without compensation** — recorded as pending decision **D7**, which asks the
maintainer to look at them:

| Behaviour | Now |
|---|---|
| **Sun Color** on the disc and sunburst | Fixed art; the setting still drives the ambient radial glow |
| The theme's star colour | Reaches nothing. `theme.starColor` stays on `SceneTheme` because custom themes persist it |
| **Fall Colors** on palm fronds | Fixed art. Winter still applies — the frost is a separate sprite, not a tint |
| Per-building skyscraper window lighting | Day and night are both artwork now, crossfaded on `nightGlow` |
| Per-burst firework colour | The palette is in the sprite |

None of these is to be recovered by tinting the new art. If one reads wrong on a
device, the fix is artwork, or restoring a mask for that one sprite.

### Call sites that had to follow the geometry

- **`santa_sleigh_scene`** 1563×434 → 624×168. It leaves the `CANVAS_PIXELS`
  convention: V2 re-authored it on the grid, so the manifest was right and the
  call site moved. `130f/680f` and the `(-283, +244)` origin are retired for
  `SANTA_SLEIGH_SCALE = 1.5f` centred on the flight point — which also fixed a
  latent misalignment, since the old origin put the sleigh 95 px right and 130 px
  below the point its own code spawns falling gifts from.
- **`bird_body`** 420×65 → 90×42. One candidate now draws three birds at hoisted
  offsets, filling the footprint the wide sprite used to. Each is blitted centred
  on the flip axis, because the wing-flap is a vertical mirror.
- **`palmtree_fronds`** 102×176 → 120×120 with a `DECLARED_ATTACHMENT` at
  (60,102), which retires the hand-tuned `-87.45` origin. The trunk widened to
  42×186 to carry it.
- **`house_large_trim`** 12 → 18 px; its origin drops one unit so the border stays
  centred on the wall/roof seam instead of growing into the wall.
- **`tree_trunk`** replaces a `drawRect`. Its 44-unit height is not a discrepancy:
  the new canopy's content bottom lands at −44 too.
- **`star_sparkle`** keeps `SCENE_UNITS`. The manifest declares it
  `CANVAS_PIXELS`, which is defect D-1 restated, so the call site won here — the
  opposite resolution to the sleigh, and the reason both are recorded in the
  registry's `notes`.

### Out of the frame loop

Not the goal, but a consequence worth recording: the skyscraper's ~24 `drawRect`
calls per building per wrap-tile, the rainbow's 14 arc strokes and 14 `RectF`
allocations, and the firework's 18 `drawCircle` calls per burst plus the
`List<Particle>` allocated per spawn are all gone, replaced by blits.

### Tests

Two classes were replaced rather than repaired, because the properties they
asserted stopped describing the asset set.

- **`SpriteNormalisationTest` → `SpriteGeometryTest`.** The old rule was that no
  sprite may carry removable transparent padding. V2 declares a `contentBox` and
  an anchor rule per sprite and places drawings inside grid-sized canvases, so 34
  sprites carry margin on purpose and cropping them would move them. The new test
  asserts what is still true of the set: every canvas on the 3 px grid, a ceiling
  on total decoded bytes, and no single sprite over an eighth of it.
- **`LakeDecorationTintTest` → `SpriteTintClassTest`.** Generalised from three
  sprites to all 111, in both directions. The old test's own doc comment had
  specified exactly this migration: when artwork gains baked colours, its call
  site goes back to an untinted blit in the same change.

`SpriteVariantTest` kept its name and flipped its meaning: the six seasonal head
pairs moved from "allowed to be identical" to "required to differ", and the
exemption list is gone.

289 tests, 0 failures. `lintDebug` 41 warnings, 0 errors — down from 50, the
difference being `UnusedResources` 7 → 4 as three orphan drawables disappeared.

### Verification limits — read this before trusting the release

- **Nothing has been seen rendering.** No device, no emulator, no GL
  implementation in the session that produced this. Every claim about what the
  scene looks like is an argument about code and about pixels read off disk.
- **`assembleDebug` was not run and no APK was produced**, on the maintainer's
  explicit instruction. Compilation is proven only because `testDebugUnitTest`
  compiles the whole `debug` source set; **resource linking, dexing and packaging
  are unproven for this release.** The asset-pipeline change would normally put
  this at Level 3.
- **No Git tag was created.**
- **The Python tooling tests were not re-run.** The registry they check was
  rewritten wholesale, and `probe` needs a pinned rasteriser that was not
  installed. Defect **D-4** remains open and unaddressed.
- The new lit-window and lit-wall crossfades, the lightning bolt, the three-bird
  flock and the recentred sleigh are all first appearances. They are the most
  likely places for something to look wrong.

---

## v75 — stable: the v74.1 and v74.2 betas, verified on a device

`versionCode` 74 → **75**, `versionName` "74.0" → **"75.0"**. This release
contains **no code, asset, test or tooling change** beyond those two numbers:
everything in it shipped in the v74.1 and v74.2 betas, whose entries below are
the technical record. What changed is that it has now been observed.

### Maintainer verification on a Pixel 9

| Checked | Outcome |
|---|---|
| Sprite deduplication — no regression | ✅ |
| Houses, windows, planters | ✅ |
| Summer characters | ✅ |
| Winter characters | ✅ |
| Full Summer → Winter → Summer switch | ✅ |

This closes the verification limit that both betas carried. The seasonal switch
in both directions is the case that mattered: a wrong entry in
`personWalkDrawables` or in either head table would have shown there and nowhere
else, and the argument that deduplication is pixel-identical — the removed files
were byte-identical to the ones that remain, so the bitmap reaching each blit is
the same object — was an argument until this point.

Phases **3.4, 3.5 and 3.6** are therefore closed by observation, and **Group 3 is
complete**.

### One defect reported and deliberately not fixed

`balloon_basket` draws white. Recorded as **D-6** and excluded from v75 at the
maintainer's instruction, so that a release whose whole point is "the betas were
verified" does not also carry an unverified change.

It is the same defect family as D-3, not a regression from it: the PNG holds one
colour, pure white, across every opaque pixel, and its call site blits it
untinted, so the `MULTIPLY` identity leaves it as it is.

**The scope is wider than the basket**, and re-measuring the whole shipped set at
v75 is what shows it. Five other `FIXED_ART` sprites are blitted untinted from
artwork carrying no colour of its own:

| Sprite | White | Blitted | Runtime effect |
|---|---|---|---|
| `balloon_basket` | 100 % | `drawSprite`, untinted | **Reported wrong** |
| `bunny_tail` | 100 % | `drawSprite`, untinted | Plausibly correct — a white tail |
| `car_window` | 100 % | `drawSprite`, untinted | Plausibly correct — a glare |
| `firetruck_ladder` | 95 % | `drawSprite`, untinted | Needs judgement |
| `house_wall` | 59 % | orphan, no call site | None |
| `house_trim` | 57 % | orphan, no call site | None |

So D-6 needs a judgement per sprite rather than a blanket tint. The repair itself
is D-3's: a named non-user-editable constant at the blit per `DESIGN_NOTES.md`
decision 23, plus extending `LakeDecorationTintTest`'s artwork/constant pairing to
whichever sprites are decided to need colour.

### Verification

- `./gradlew testDebugUnitTest` — **287 tests, 0 failures, 0 errors**.
- `./gradlew assembleDebug` — SUCCESS, APK **19,017,989 bytes**. `versionCode 75`
  / `versionName 75.0` read out of the packaged APK with `aapt2 dump badging`, not
  trusted from the build script.
- `./gradlew lintDebug` — 50 warnings, 0 errors, 0 fatal.
- Python tooling — 74 of 76 passing; the 2 failures are defect D-4, present since
  v73.11.
- No mutation testing: nothing new is testable. The change is two integers and a
  release-notes file.

### Verification limits

- The CI tag check requires a `vNN` stable tag to equal `versionCode`, so **`v75`
  is the only tag this build will publish under**. The tag was not created — the
  maintainer creates it.
- v74.1's three lake-decoration colours have now been seen in the scene, but were
  never judged against a mockup. If any reads wrong, each is a single named
  `const val`.
- Practical CPU, battery and thermal observation of the cumulative Phase 1 and
  Phase 2 work is still outstanding.

---



Delivered at **Verification Level 3**. Resource names changed, so `assembleDebug`
is not optional here: a stale `R.drawable` reference is a compile error and a
missing PNG is an `aapt` error, and neither is reachable from a JVM unit test.

`versionCode` and `versionName` are unchanged: this is a beta on top of Android
version 74.

### The three phases are one change, because the same fact underlies all three

Sixteen groups of shipped PNGs were byte-identical. That single measurement means
two completely different things depending on the group, and telling them apart
*is* the work:

| Kind | Groups | What it means |
|---|---|---|
| One drawing under two names | 2 | The small and large houses' window and planter. Two resource names, one picture, two decodes, two atlas entries |
| One drawing at two points in a cycle | 8 | `person_*_walk1` and `person_*_walk3`. The walk cycle's passing pose, shipped twice |
| A variant that was never drawn | 6 | The summer and winter person heads. The seasonal feature is real; the artwork for it does not exist |

3.4 removes the first two kinds. 3.5 declares the third. 3.6 makes the
distinction machine-checked so it cannot be lost again — which matters because
until now nothing in the project could see it: size, content box, anchor, scale
and tint are all per-sprite properties, and **two copies of one picture satisfy
every one of them.**

### 3.4 — Deduplication

**118 PNGs → 108.** Decoded artwork **17.20 MB → 16.14 MB**, 1.06 MB recovered,
and ten fewer atlas entries and decodes.

**House parts.** `house_small_window` ≡ `house_large_window` and
`house_small_planter` ≡ `house_large_planter`; the two SVG sources were identical
too, apart from the sprite's own name inside a comment. Both pairs collapse into
`house_shared_window` and `house_shared_planter` — a neutral name rather than
either variant's, because a small house drawing `house_large_window` reads as a
bug. (`house_window` was unavailable: it is one of the seven orphan drawables.)
Seven call sites in `SceneObjectRenderer` renamed. The two houses still differ
where they actually differ — wall, roof, trim, chimney, door — and the size
difference comes from the enclosing `canvas.scale`, not from the artwork.

**Walk cycle.** `person_{man,woman,boy,girl}_{summer,winter}_walk1` ≡ `..._walk3`,
eight groups. **Verified by looking at the frames rather than inferred from the
hashes:** it is a four-frame cycle of two poses — frames 0 and 2 are the contacts,
one per leading leg, and 1 and 3 are the passing pose, where the legs are together
and a flat silhouette draws the same picture whichever leg leads. The duplication
is intentional art, so the eight `..._walk3.png` files are removed and the frame-3
slot in `personWalkDrawables` names `walk1`.

**No runtime cost, and no new indirection.** `personWalkDrawables` is already a
flat `IntArray` indexed by kind, season and frame — built once, no allocation, no
string comparison. Deduplication changes *which ID sits in one slot* and nothing
else. Frame for frame, the animation is identical.

**Not done here:** the six seasonal head pairs, which are 3.5's subject and which
3.4 was explicitly warned not to pre-empt by deleting a file whose variant is
meant to diverge later.

### 3.5 — Seasonal variants (decision D2, resolved)

The frames were examined before anything was decided. **The seasonal distinction
already works, and only for the walking sprites**: the winter set has a beanie
instead of hair, long sleeves, a snowflake motif, and the girl wears trousers
where the summer girl wears a skirt. It was never drawn for the **heads** — window
occupants and car drivers — so those six pairs are byte-identical and a face at a
window looks the same in January as in July.

**D2 is resolved as a declared gap, not as artwork.** Person art has
`source.kind = "none"` throughout: there is nothing to regenerate from, so drawing
a winter head is asset redesign. Inventing one here would have been a redraw
presented as a fix, which is the same trade Phase 3.1 refused under decision D12.

What was built instead:

- **Registry schema 2 → 3**, adding a top-level `variants` array. A group carries
  an `id`, an `axis` (`season` is the only one so far), two or more `members`, a
  `state` of `DISTINCT` or `IDENTICAL_GAP`, and a `reason`. Eighteen groups: the
  six head pairs as `IDENTICAL_GAP`, and the twelve walking pairs as `DISTINCT`.
- **The twelve working pairs are pinned as `DISTINCT`** — not decoration. A
  regeneration that copied one season over the other now fails, and that is
  precisely how the head sprites became identical in the first place.
- **The runtime is unchanged and the lookup tables stay two columns wide**, so
  drawing the six sprites is the entire fix, with no code change at either call
  site.

`IDENTICAL_GAP` is a first-class value in the same family as `UNDETERMINED` and
`source.kind = "none"`: it records what is missing, in terms of what would close
it.

### 3.6 — Difference / regression testing

`registry.load_variants` and `registry.validate_variants`, wired into
`paperscrape-assets validate`, check **two** properties.

**Every declared group against its members' bytes, in both directions.** A
`DISTINCT` group whose members turn out identical has lost the distinction it
names. An `IDENTICAL_GAP` group whose members have started to differ has gained
artwork the declaration has not caught up with. The second direction is the one
that makes the gap self-closing: drawing a winter head produces a failure saying
so, instead of a silent success nobody records.

**Any byte-identical pair that no group declares.** This is what holds 3.4: a
duplicate outside a variant group is one drawing under two names, and it now
fails rather than accumulating.

Tests: `tools/assets/tests/test_variants.py`, 17 cases across document
well-formedness, both failure directions, and the shipped table.
`SpriteVariantTest` in Kotlin adds 3 more. **The Kotlin test is not the tooling
check restated** — Gradle is the only thing CI runs, so the tooling's answer never
gates a release, and the manifest is deliberately tooling-side, so its declaration
cannot be imported. What is duplicated is only the narrow property that must hold
in the APK.

One coverage rule is worth naming: `test_every_seasonal_sprite_belongs_to_a_variant_group`
asserts that any sprite whose name carries a season is in the table. Stated as a
rule rather than a count, so a new seasonal sprite is *caught* rather than
*counted* — a count would have to be edited by whoever reduced the coverage.

### Verification

- `./gradlew testDebugUnitTest` — **287 tests, 0 failures, 0 errors** (284 at
  v74.1; +3 for `SpriteVariantTest`).
- `./gradlew assembleDebug` — SUCCESS. Run rather than skipped: this is the only
  check that reaches a renamed resource.
- `./gradlew lintDebug` — **50 warnings, 0 errors, 0 fatal**, down from 60. The
  entire drop is `IconDuplicates`, 16 → 6: Android's own duplicate-resource
  detector now reports exactly the six declared seasonal gaps and nothing else,
  which corroborates the deduplication from outside this project's tooling.
- APK **19,017,989 bytes**, 40,257 smaller. `aapt2 dump resources` on the packaged
  APK confirms `house_shared_window`/`house_shared_planter` are present and that no
  `house_small_window`, `house_large_window`, `house_small_planter`,
  `house_large_planter` or `*_walk3` resource remains; `aapt2 dump badging`
  confirms `versionCode 74` / `versionName 74.0`.
- `paperscrape-assets all` — probe fingerprint matches the pin; 108 files,
  16.14 MB decoded, **6 duplicate groups, exactly the declared gaps**; registry
  OK; 18 variant groups checked; normalisation OK; fidelity 11
  `PIXEL_IDENTICAL` + 11 `EDGE_EQUIVALENT`, none divergent. All `reports/`
  regenerated, since they named sprites that no longer exist.
- Python tooling — **74 of 76 passing**. The 2 failures are defect D-4, confirmed
  present in an untouched v74 extraction.
- **Mutation testing, targeted rather than broad.** Two mutations on the one piece
  of genuinely new logic, both killed: restoring `person_girl_winter_walk3.png`
  killed the Kotlin duplicate assertion; altering one pixel of a winter head
  killed the tooling's `IDENTICAL_GAP` direction. A third confirmed the
  `DISTINCT`-collapsed direction directly against `validate_variants`. The
  remaining new code is schema validation, whose tests are themselves the
  negative cases.

### Verification limits

- **No device, no emulator, no OpenGL implementation.** `assembleDebug` proves
  every renamed resource resolves and packages; it does not prove a house still
  draws its window. **Nothing in this release has been seen rendered.** The
  argument that the scene is unchanged is that the deduplicated files were
  byte-identical, so the bitmap reaching each blit is the same object it was.
- The walk cycle's frames 1 and 3 were judged **by eye** from a rendered
  side-by-side sheet, not by a device.
- v74.1's three lake-decoration colours are still unobserved.

### Left open

- **The six seasonal head pairs.** Declared, not closed. Closing them is asset
  redesign against sources that do not exist.
- **Seven orphan drawables** (`house_roof`, `house_trim`, `house_wall`,
  `house_window`, `road_asphalt`, `road_curb`, `road_line`) — dead weight in the
  APK, but not duplicates, so removing them is Group 7 housekeeping and was not
  taken here.
- **D-4** unchanged.

---



Delivered at **Verification Level 2**. The change touches one draw path, three
colour constants and the asset manifest — no Gradle or build configuration, no
manifest, no CI, no lifecycle, no asset pipeline code, and no PNG.
`assembleDebug` intentionally skipped under normal verification policy.

Android `versionCode` and `versionName` are deliberately unchanged: this is a beta
on top of Android version 74.

### The defect, and why it was not what it looked like

`ROADMAP.md` D-3 recorded two candidate causes — a silent `GlTextureCache.register()`
failure, or UV coordinates pointing at the wrong atlas region — and both were
GPU-side. Both were wrong, and the same file already contained the evidence: the
defect was confirmed to **predate the GPU renderer**, so a cause that only exists
inside the GPU backend cannot explain it. That contradiction was recorded and not
acted on for a release.

A device screenshot supplied by the maintainer settled the rest. The dolphins and
the sailboats **do** render, at the right size, in the right place, moving as
designed — as **blank white shapes**. Correct silhouette means correct alpha, and
correct alpha means the sampled texture region is correct. Every other sprite in
the scene comes through the same atlas, the same UVs and the same upload path with
its colours intact. Nothing on the GPU side was implicated at any point.

### Root cause

The three sprites carry **no colour of their own**. Measured off the shipped PNGs:

| Sprite | Distinct colours over opaque pixels | Mean level |
|---|---|---|
| `dolphin_body.png` | **1** — pure white `#FFFFFF` | 255 |
| `sailboat_hull.png` | **1** — pure white `#FFFFFF` | 255 |
| `sailboat_sail.png` | 7 — greys 227..255 on white | 249 |

That is the **tintable** authoring profile, the same one `car_body`, `tree_canopy`
and `penguin_body` have, and the opposite of a genuine fixed-art sprite such as
`palmtree_trunk` (283 colours) or `taxi_checker` (56).

`DESIGN_NOTES.md` §3 classifies the dolphin and the sailboat as **fixed-art** —
final colours baked into the PNG, blitted with no colour filter — and the three
call sites were written to match: `SpriteBlitter.draw`, no tint. **The artwork
never held up its end of that contract.** White is the `MULTIPLY` identity on both
backends by explicit design (`CanvasSceneTarget` skips the filter for it entirely,
`GlSceneTarget` writes it as a vertex colour that changes nothing), so an untinted
blit of an all-white mask draws a white shape. The in-code comment asserted the
opposite of the truth in as many words: *"colors are baked into the PNG at
generation time"*.

**Why nothing caught it.** A PNG does not record whether its greys are finished
artwork or a mask awaiting a colour. The manifest's `tint` field is resolved
*from the call site*, so the declaration and the code agreed with each other while
both disagreed with the pixels. The only place the contradiction is visible is
between the artwork and the colour it is multiplied by, and until now nothing read
those two together.

### The fix

The colour is supplied at the blit instead of being baked in — exactly what
`SceneObjectRenderer` already does for the penguin's beak, the bunny's inner ear
and the gift ribbon (`DESIGN_NOTES.md` §7, "Fixed accent colours"):

| Constant | Value | Chosen because |
|---|---|---|
| `PaperRenderer.DOLPHIN_COLOR` | `#8CA3B5` | Desaturated grey-blue: reads against every built-in lake palette, whose day colours run from saturated cyan (`#2FA8D8`, `#1E9BC4`) to very pale (`#BFE3EE`) |
| `PaperRenderer.SAILBOAT_HULL_COLOR` | `#B5651D` | Paper Orange Dark, an existing brand token, from the same warm family as the `#7A4B2E` tree-trunk accent |
| `PaperRenderer.SAILBOAT_SAIL_COLOR` | `#FFF7EC` | Paper Cream. A sail reads as white, but a large pure-white fill does not read as paper (`DESIGN_NOTES.md` §7 rule 4), and the off-tone is what lets the sprite's own 227..255 mottling survive `MULTIPLY` as shading |

The three blits move from `sprites.draw` to `sprites.drawTinted`. **Origins
`(-28,-14)`, `(-10,8)`, `(4,-36)` and `SpriteScale.SCENE_UNITS` are unchanged**,
confirmed by re-running the project's own call-site resolver against the edited
source. No PNG was touched, no coordinate moved, no geometry changed, and nothing
outside `drawLakeDecorations` was modified.

The decorations stay **not user-editable**. The fixed-art classification per
category is a protected element (`DESIGN_NOTES.md` §11), so this restores the
intended appearance without promoting the lake decorations to a recolourable
category.

`tools/assets/sources/sprites.json` moves the three from `FIXED_ART` to
`TINTABLE`. That is not bookkeeping: `validate` fails without it, with
*"registry declares tint FIXED_ART, PaperRenderer.kt blits it as TINTABLE"* —
verified by making the edit and reverting it.

### New test

`LakeDecorationTintTest` reads the three PNGs with `ImageIO` and checks them
against `PaperRenderer`'s own constants, in **both** directions, because neither
half is correct alone:

- every opaque pixel of each sprite is a neutral grey, so the mask carries no hue
  that the tint would compound;
- each mask is light enough (mean ≥ 220) for `MULTIPLY` to carry a colour at all;
- no constant is the `MULTIPLY` identity, which would be indistinguishable from
  the untinted blit that caused the defect;
- each constant is fully opaque, since `GlSceneTarget` ignores a tint's alpha and
  `CanvasSceneTarget` does not — an incidental alpha byte would make the wallpaper
  and its own settings preview disagree;
- no constant is so dark that the sprite reads as a silhouette against the darkest
  built-in lake colour.

If a future asset pass bakes real colours into one of these PNGs, the first two
assertions fail and say so: that sprite's call site has to return to `draw` in the
same change, or its finished art would be multiplied a second time.

### Verification

- `./gradlew testDebugUnitTest` — **284 tests, 0 failures, 0 errors** (was 279 at
  v74; +5 for `LakeDecorationTintTest`).
- `./gradlew lintDebug` — **60 warnings, 0 errors, 0 fatal**, unchanged from v74.
- `paperscrape-assets validate` — **OK**, 118 entries, 24 with an SVG source, 94
  gaps, no call-site disagreement.
- **Mutation testing on the new test — 3 mutations, 3 killed.** `DOLPHIN_COLOR`
  set to the identity white killed the identity assertion; `SAILBOAT_HULL_COLOR`
  set to a dark, non-opaque value killed the opacity and legibility assertions;
  pointing the test at `palmtree_trunk`, a genuinely coloured sprite, killed both
  artwork assertions. Every mutation was reverted and the file re-read afterwards.
- No allocation audit: the change replaces one blitter call with another on the
  same object and adds three compile-time constants. Neither entry point
  allocates, and this was already established at v74.

### A pre-existing tooling defect found while verifying, and deliberately not fixed

`tools/assets` reports **57 of 59 Python tests passing**. The two failures were
confirmed to be **present in an untouched extraction of `PaperScrape_v74.zip`**,
so they are not caused by this release.

Root cause, for whoever picks it up: `callsites._wrapper_bindings` recognises a
wrapper only when its first parameter type is literally `Canvas`, and the v73.11
GPU migration changed `SceneObjectRenderer`'s two wrappers to take `SceneCanvas`.
**Every one of that file's ~60 blit call sites has therefore been invisible to
`validate` since v73.11**, silently — `bar_door` reports "declares an anchor with
no call site", and `driverRes` disappeared from the unattributed list. The
declarations Phase 3.2 built the resolver to check are consequently unchecked for
that file.

Left alone on purpose: it is a separate defect, outside the scope of D-3, and
fixing it would re-expose ~60 call sites to comparison at once — findings that
need triaging on their own rather than inside a defect fix. `PaperRenderer.kt` is
scanned correctly, so the manifest change above was verified against real
resolution rather than against a resolver that had stopped looking. Recorded as
defect **D-4**.

### Verification limits

- **No device, no emulator, no OpenGL implementation and no profiler** in the
  build environment. The rendered result of this change **has not been observed**.
  The reasoning that the three sprites will now draw in colour is an argument
  about the artwork's measured content and the documented behaviour of the
  `MULTIPLY` identity, not an observation of a frame.
- **The three colours have not been judged on a device, and no mockup was produced
  before implementing them.** `AI_PROJECT_RULES.md` §13 would normally require one;
  the maintainer directed the fix to be applied directly. They are three named
  `const val`s, so revising any of them is a one-line change.
- The sail is deliberately close to white, so against `tundra`'s pale lake
  (`#BFE3EE`) its contrast is low by construction. If that reads badly, the
  correct answer is a day/night pair, not a darker constant.
- `assembleDebug` intentionally skipped under normal verification policy.
- **No Git tag was created**, at the maintainer's instruction, and the release ZIP
  carries no `.git`, so the identifier was taken from `RELEASE_HISTORY.md` and
  `release-notes/`.

### Known defects not addressed here

- **Dolphins and sailboats can overlap** while drifting, visible in the same
  screenshot. The two effects use decorrelated threshold offsets but neither knows
  where the other is; nothing in this release changes that. Recorded as **D-5**.
- **At default densities exactly one dolphin and one sailboat exist** (pool of 4;
  thresholds 0.069 and 0.340 against a density of 0.30), and at the default lake
  height the band sits almost entirely behind the hills. Both were left untouched:
  now that the colours are right, how sparse the lake actually reads is a question
  to answer by looking, not by arithmetic.
- **D-2**, the Italian caption baked into `santa_sleigh_scene.png`, is unchanged.

---

**The first stable release drawn on the GPU.** `versionCode` 73 → **74**, `versionName`
"73.0" → **"74.0"**. Everything accumulated across the v73.1–v73.11 betas ships here,
with the OpenGL ES renderer as the headline.

### Device result

Measured by the maintainer on a Pixel 9, day and night, all scene elements at roughly
50%: the CPU cluster carrying the wallpaper sits at **~357 MHz against ~2600 MHz** with
the v73.10 `Canvas` renderer. No visual anomalies and no perceptible slowdown. That
measurement is what promoted the renderer from experiment to default.

### What v74 adds on top of v73.11

v73.11 delivered the renderer; v74 makes it batch properly and stop duplicating itself in
memory.

**A shared texture atlas.** Sprites are packed into one 2048² texture as they are first
drawn. Sprites over 1024 px in either dimension stay on textures of their own — the sleigh
alone is 1563x434, and letting it take a shelf row would push out the small sprites that
actually repeat every frame, while itself costing only one batch break because it is drawn
once. `GlTextureCache` decides placement; callers get a handle and a UV rectangle either
way, so a standalone texture is simply the `0..1` case.

**The flat-fill white pixel is packed into the atlas first**, and that is the point of the
whole change rather than a detail of it. A batch ends when the bound texture changes, and
draw order *is* depth order here, so it cannot be reordered around. With flat fills and
sprites in the same texture, a scene object's solid details no longer end the batch between
its sprite parts — an entire house, and then the objects after it, accumulate into one.

Its UV is taken from the **centre** of its atlas entry, not the corner. A 1x1 entry is one
texel inside a transparent border; sampling at the corner sits exactly on that boundary and
bilinear filtering would mix the transparency in, making every flat fill in the scene
half-alpha.

**Sprite pixels are pulled, not pushed.** `SceneCanvas.drawSprite` now takes a
`SpriteSource` instead of a decoded `Bitmap`, because the two backends need pixels at
completely different rates — the `Canvas` backend every blit, the GPU backend once per
sprite per context. Passing a bitmap made everyone pay the more expensive of the two.
`GlTextureCache` records each sprite's dimensions at upload, so a steady-state blit resolves
its size from the registry and **never touches `SpriteCache`**, which was otherwise a
synchronised lookup with an LRU touch, once per sprite per frame, to recover a width and a
height that had not changed since the first one.

**The CPU copy is released once the GPU has one.** `SpriteSource.onSpriteUploaded` drops the
decoded bitmap through the new `SpriteCache.release` / `SpriteCacheIndex.remove`, freeing up
to ~17 MB of heap that duplicated what the GPU already held. Re-decoding is always available
— the same property that makes memory-pressure eviction safe — so being wrong costs one
decode. The `Canvas` backend never reports an upload, because it holds no durable copy that
would justify releasing one.

**Fully transparent draws are skipped.** Under premultiplied blending a zero-alpha primitive
contributes exactly nothing, and the scene fades plenty of things through zero:
precipitation, leaves, star twinkle, the sleigh's edge fade.

### Verification

- `./gradlew test` — **279 tests, 0 failures, 0 errors** (was 264; +15 covering `ShelfPacker`
  and `SpriteCacheIndex.remove`). The 59 Python tooling tests were not re-run: no asset,
  manifest or tooling file changed.
- `./gradlew lintDebug` — **60 warnings, 0 errors, 0 fatal**.
- `./gradlew assembleDebug` — **SUCCESS, 0 compiler warnings**, APK **19,058,246 bytes**,
  `versionCode 74` / `versionName 74.0` confirmed by reading the packaged binary with
  `aapt2 dump badging` rather than the build script.
- **Mutation testing** on `ShelfPacker` and `SpriteCacheIndex.remove`: 8 mutants, **8 killed**
  — after one survived the first run. See below.
- **`javap -c` allocation audit** of every steady-state method in `GlSceneTarget`,
  `GlTextureCache`, `ShelfPacker`, `SpriteBlitter`, `SpriteCacheIndex` and `SpriteCache`: no
  `new`, no `newarray`/`anewarray`, no `valueOf` boxing. The single hit is the
  `IllegalStateException` on `SpriteCache.get`'s decode-failure path, which is pre-existing
  and not on the frame path at all now that the GPU backend stops calling it.

### The mutant that survived

The row-break test in `ShelfPacker.place` compared the **content** width against the atlas
width instead of the padded width. An entry that fits by content and overflows only once its
one-pixel border is counted would have been placed with that border hanging outside the
texture. Randomised size sweeps do not find this: it needs an entry constructed to sit
exactly on the boundary. Recorded because the same shape of gap — a test that exercises a
range but never the edge — is what let it through in the first place.

### Verification limits

- **No device, no emulator, no profiler and no OpenGL implementation** in the build
  environment. Nothing here is a measured CPU figure; the Pixel 9 numbers above are the
  maintainer's. **No frame produced by either backend has been observed in this environment**,
  and `GlSceneTarget`, `GlTextureAtlas`, `GlRenderThread` and `GlTextureCache` have no
  automated test because they need a GL context. What is covered is the pure logic they
  depend on: `SceneTransform`, `SceneShape` and `ShelfPacker`.
- The atlas's **bleed border, entry placement and the white pixel's centre-sampled UV are
  unverified against a rendered frame.** They are the three things most worth a direct look.
- `git` was available, but **the release ZIP carries no `.git`**, so the identifier was taken
  from `release-notes/` and this file rather than from tags. `.gitignore` behaviour was
  verified properly, by extracting the ZIP, running `git init` there and checking
  `CLAUDE.md` against `git check-ignore` and a forced `git add -A`.

### Known defect carried into this release

**Dolphins and sailboats do not render** (`K5`, `ROADMAP.md` D-3). Confirmed by the
maintainer to **predate the GPU renderer**, so neither the OpenGL backend nor the atlas
introduced it. It was deliberately left alone in v74 rather than guessed at: the two
candidate causes — a silent `register()` failure versus UV coordinates pointing at the wrong
region — produce opposite symptoms, and telling them apart needs a rendered frame. Shipping
a guess inside a release marked Stable was rejected. First task after v74.

### Known limitations carried forward

- The atlas **cannot reclaim space**: shelf packing wastes area against a real bin packer and
  frees only wholesale. It also fills in first-draw order, so a scene whose sprite set exceeds
  2048² pushes its *later* sprites — objects and people, which benefit most — out to
  standalone textures. Neither has been observed to matter.
- **Each engine has its own EGL context**, so the picker's preview and the live wallpaper do
  not share textures.

---

## v73.11 — GPU renderer: the scene is drawn with OpenGL ES 2.0

Delivered at **Verification Level 3**. The change replaces the rendering backend and
moves drawing onto a new thread, which is a core-integration and critical-rendering
change on two counts, so `assembleDebug` was run rather than skipped.

Android `versionCode` and `versionName` are deliberately unchanged: this is a beta on
top of Android version 73.

### What changed, and what deliberately did not

**The backend, and only the backend.** The scene logic is untouched: the candidate
system, themes, seasons, people, vehicles, precipitation, clouds, animations, logical
coordinates, the asset pipeline, the manifest, persistence and the determinism of every
seed are all exactly as they were in v73.10. No sprite was regenerated, no coordinate
moved, no visual decision was revisited.

What replaced them is how those instructions reach the screen: a
`Canvas`/`SurfaceHolder.lockCanvas` software rasteriser on the main looper became an
OpenGL ES 2.0 renderer on a per-engine render thread.

### The seam

`SceneCanvas` is the interface both renderers now draw into. It exposes exactly the
operation set they already used and nothing more — a transform stack, rects, lines,
circles, ovals, stroked arcs, filled sectors, closed shapes, three named gradient forms,
and sprite blits. An interface that admitted arbitrary `Path`s, clips or `Xfermode`s
would be one the GPU backend could not honour, and a call site could then compile while
producing a different picture on each backend.

`Paint` is passed through rather than decomposed into arguments. Reading `color`,
`alpha`, `style`, `strokeWidth` and `strokeCap` allocates nothing, and it left ~90 call
sites unchanged. The exception is deliberate: a `Shader` cannot be read back off a
`Paint`, so the sky, the hill highlight and the sun/moon glow pass their stops
explicitly instead.

Two implementations:

- **`GlSceneTarget`** — the wallpaper.
- **`CanvasSceneTarget`** — the settings screen's live preview, which draws into a
  Compose canvas where there is no GL context, and the wallpaper itself if EGL fails.
  This is why the `Canvas` path is kept rather than deleted.

`Path` was replaced by `SceneShape`, a closed polygon that keeps its vertices: the
`Canvas` backend builds a `Path` from them lazily, the GPU backend triangulates them.
The parasol's `moveTo + arcTo + close` became `drawWedge`, a primitive both backends
generate directly.

### Five decisions inside the GPU backend

1. **The projection is screen pixels with Y down**, not a normalised world space. Every
   coordinate, sprite origin, depth constant and historical divisor therefore keeps its
   existing value *and its existing meaning*. A world space would have meant rescaling
   all of them — and a sprite whose origin is only correct together with its scale
   convention is exactly how defect D-1 happened.
2. **One shader program, not two.** A flat fill is a textured quad sampling a 1x1 white
   texture, so a batch breaks only on a texture change — never because a solid shape sat
   between two sprites. The star field and the precipitation pool each collapse to one
   draw call.
3. **Premultiplied alpha throughout**, with `glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`.
   `BitmapFactory` decodes premultiplied and `GLUtils.texImage2D` uploads those bytes
   unchanged; mixing the two conventions is the standard cause of dark fringes on every
   soft sprite edge.
4. **Tinting is the same operation as before.** The fragment shader computes
   `vec4(tex.rgb * v_Color.rgb, tex.a) * v_Color.a`, which is what
   `PorterDuffColorFilter(MULTIPLY)` plus `paint.alpha` produces. Baked-in shading
   survives the tint for the same reason it did, and white is still the identity.
5. **Transforms are applied on the CPU as vertices are emitted.** A model-matrix uniform
   would end the batch at every `save()`, and the scene changes transform far more often
   than it changes texture.

### The threading consequence, taken with the change rather than after it

A GL context belongs to one thread, so drawing had to leave the main looper. That made
scene state cross-thread for the first time. The answer is `onRenderThread { }`: prefs,
theme, custom-theme, weather and home-screen-offset updates are queued as runnables run
between two frames, so the scene is still only ever touched by the thread that draws it.
A lock around the renderer was rejected — it would put every settings write in
contention with the frame loop.

Three process-wide objects genuinely became multi-threaded, because a process can host
two engines and therefore two render threads. `SpriteCache`, `TintFilterCache` and
`SunPositionCalculator.currentHour24()` are now synchronised. `SpriteCache`'s lack of a
lock had been correct *and explicitly documented as conditional* on the render loop
running on the main looper; that premise is what this release removed, so the lock came
with it. `currentHour24` was the subtler one: its two memo fields are only meaningful
together, and an interleaved read could have paired one engine's minute stamp with
another's hour and held a stale time for a full minute.

### Two bugs found during implementation, both silent

- **The hill highlight would have washed over the whole hill.** Filling the ridge as a
  triangle fan from a base vertex interpolates the gradient across each triangle's full
  height, so a highlight defined over the top 35% would have bled all the way down. The
  shape is now filled as vertical columns **split at the gradient's lower stop**, which
  puts a real vertex on the boundary and makes the flat region flat.
- **`currentHour24` and the sprite caches**, above. Neither would have thrown.

### Frame pacing

The render loop still targets 33 ms and still subtracts its own measured cost. It
deliberately does **not** free-run: `eglSwapBuffers` blocks on vsync, so an unpaced loop
would draw at 60, 90 or 120 Hz and do two to four times the work for motion this slow.

### Verification

- `./gradlew test` — **264 tests, 0 failures, 0 errors** (was 240; +24, no test removed
  or weakened). The 59 Python tooling tests were not re-run: no asset, manifest or
  tooling file changed.
- `./gradlew lintDebug` — **59 warnings, 0 errors, 0 fatal**, down from 86. The drop is
  real rather than suppressed: `UseKtx` fell from 33 to 3 because the `Canvas` calls that
  triggered it no longer exist in the scene renderers.
- `./gradlew assembleDebug` — **SUCCESS, 0 compiler warnings**, APK **19,041,862 bytes**
  (+16,384 on v73.9's 19,025,478 — the new renderer classes).
- **Mutation testing on `SceneTransform` and `SceneShape`**, the only new pure logic:
  8 mutants, **8 killed** — but only after three of them survived the first run and the
  tests were fixed. See below.
- **`javap -c` allocation audit** of every steady-state method in `GlSceneTarget`,
  `SceneTransform`, `SceneShape`, `SpriteBlitter` and `GlTextureCache`: no `new`, no
  `newarray`/`anewarray`, no `valueOf` boxing. The only `new` in the sprite path is the
  `NoWhenBranchMatchedException` Kotlin emits for the unreachable arm of an exhaustive
  `when`.

### Three per-frame Shader allocations removed as a side effect

The v73.10 CPU audit listed "three `LinearGradient`/`RadialGradient` allocated per frame"
as an unapproved hotspot. Expressing gradients as stops closed it without being aimed at
it: `javap` now reports **`drawSky` 0 allocations** (was a `LinearGradient` per frame),
**`drawCelestialBody` 0** (was a `RadialGradient` per frame), and `drawHillLayers` down
to the pre-existing `GroundGeometry` alone. `drawParasol` is also at 0.

The other recorded hotspots are untouched and remain unapproved.

### Three mutants that survived the first run

Recorded because each was a real gap that a passing suite hid:

- **The sign of the cross term feeding `a` in `rotate`.** From an axis-aligned state `c`
  is zero, so *every single rotation in the scene* produces the identical result either
  way. Only a rotation composed onto an already-rotated basis distinguishes them. Fixed
  with a test asserting `rotate(30) + rotate(60) == rotate(90)`.
- **Counting saves dropped by stack overflow.** The original test balanced the totals,
  which passes whether or not the drops are counted. The distinguishing case is an
  *interleaved* restore: without the counter, an overflowed save's restore pops a real
  level and every draw after it in the frame is transformed by the wrong matrix.
- **Swapping the `b` and `c` slots on `restore`.** The original test saved a state built
  from translate and scale only, where both are zero. The saved state now carries a
  rotation and a non-uniform scale so the two differ.

### Verification limits

- **No device, no emulator, no profiler, no GL implementation in this environment.**
  Nothing here is a measured CPU improvement, and **no frame produced by either backend
  has been observed**. What is verified is that the project compiles, packages, tests
  green, allocates nothing new on the frame path, and that the transform arithmetic
  matches the `Canvas` contract it has to reproduce.
- **`GlSceneTarget`, `GlRenderThread`, `GlTextureCache` and `GlSpriteProgram` have no
  automated test at all.** They need a GL context to do anything. Visual parity, EGL
  lifecycle behaviour across lock/unlock and wallpaper-picker preview, and the fallback
  path are all maintainer-side.
- **Antialiasing is the most likely visible difference.** `Canvas` antialiases circles,
  arcs and thin strokes analytically; GL relies on multisampling. 4x MSAA is requested at
  EGL config time with a non-MSAA fallback, but whether the result reads the same is a
  device question.
- `git` itself was available this session, but **the release ZIP carries no `.git`
  directory**, so there is no tag history to check the identifier against: v73.11 was
  determined from `release-notes/` and this file. Confirm it against the real tags before
  tagging. `.gitignore` behaviour *was* verified properly — the ZIP was extracted into a
  clean directory, `git init` run there, and `git check-ignore -v CLAUDE.md` plus a forced
  `git add -A` confirmed `CLAUDE.md` is ignored and untracked (309 tracked files, 310 in
  the ZIP).

### Known limitations carried forward

- **No texture atlas**, so a scene object alternating sprites and flat parts still ends a
  batch between them. Runs of one sprite do not.
- **Each engine has its own EGL context**, so the picker's preview and the live wallpaper
  do not share textures the way they share `SpriteCache`'s bitmaps.

---

## v73.10 — CPU audit, and the first batch of fixes from it

Delivered at **Verification Level 2**. The change is Kotlin and tests only: no
asset, no manifest, no tooling file, no resource, no Gradle or CI configuration.
`assembleDebug` was deliberately not re-run — v73.9 established the APK at
19,025,478 bytes from a clean extraction, and nothing here changes what the build
consumes.

Android `versionCode` and `versionName` are deliberately unchanged: this is a beta
on top of Android version 73.

### Where this came from

The maintainer ran v73.9 on real hardware with every seasonal and non-seasonal
element enabled and reported three things: the scene is fluid; the rain/snow
stutter that was perceptible in v73.8 is gone; CPU use is still high.

That produced a **static audit of the frame loop** — read of the render path,
`javap -c` on the compiled classes, and a Python simulation of the candidate
selection logic to get real per-frame draw counts. Its ranked hotspot list is
recorded in `ROADMAP.md` under Current / Next Work.

**What the audit is not.** It contains no measured CPU shares, because there was
no profiler and no device in the session that produced it. Every figure in it is
an operation count, a bytecode fact, or a simulation of code that is in the
repository. The two candidate explanations it offers for why v73.9 removed the
stutter — fewer destination pixels composited per blit, and a sprite cache at half
the footprint hitting the trim threshold far less often — are both consistent with
the observation and **cannot be told apart without a profiler**.

### Batch 1: what changed

Five fixes, each approved individually. Every one removes an allocation or a
redundant state write from a path that runs every frame, and none changes what is
drawn.

1. **`drawChristmasLights`.** Built an `intArrayOf`, an `arrayOf(x to y, …)` of six
   boxed `Pair<Float, Float>`, a mapped copy of it and a `List` wrapper on every
   call — 14 `Float.valueOf` in the bytecode — for constant data. It runs once per
   tree and once per palm, for every wrap-tile copy, on every frame the winter
   palette is on. The colours and unscaled positions are now three fields, kept as
   parallel `FloatArray`s precisely because an array of points is what produced the
   boxing. The `lx * scale` multiplication stayed, in the same order, and now runs
   only for the lights actually drawn.
2. **`drawPrecipitation`.** `style`, `strokeWidth` and `strokeCap` were set inside
   the loop, so with `isRain` fixed for the whole call they were rewritten
   identically up to 90 times a frame. Hoisted above the loop; `alpha` and the
   geometry stay in it. Geometry and candidate selection untouched. `precipPaint`
   is used by nothing else, so the paint state left between frames is not
   observable.
3. **`drawClouds`.** Three `floatArrayOf` tier tables allocated inside the function
   every frame, moved to the companion object as `CLOUD_TIER_PARALLAX`,
   `CLOUD_TIER_Y_OFFSET` and `CLOUD_TIER_SIZE_MULTIPLIER`. Same values, same index
   order.
4. **`SunPositionCalculator.currentHour24()`.** Allocated `Calendar.getInstance(zone)`
   *and* the `TimeZone.getDefault()` feeding it — the latter returns a defensive
   clone — every frame, for a value that changes 1,440 times a day. Now computed
   from the epoch with `floorDiv`/`floorMod` and memoised on the minute. The
   bytecode confirms `TimeZone.getDefault()` sits inside the cache-miss branch, so
   the steady path is `currentTimeMillis`, a division, a comparison and a return.
   The zone is still re-read every minute, so a DST transition or a device
   time-zone change is picked up as promptly as before.
5. **`lakeTopBottomY()`.** Returned `Pair<Float, Float>?` — two boxed floats and a
   `Pair` — and was called twice per frame, by `drawMountains` and `drawLake`. Now
   `updateLakeBandY(): Boolean` writing two fields, whose KDoc states they are only
   valid after a `true`.

### What was deliberately not touched

The star field, every tile-copy count, cloud and mountain culling, mountain Path
caching, the frame scheduling and `onOffsetsChanged`. The last two exist as they
do because of an earlier perceived-stutter fix; changing them is a device
question, not a static one. Anything altering a tile-copy count changes what is
visible at a screen edge and needs visual approval first.

### Verification

- `./gradlew test` — **240 tests, 0 failures, 0 errors** (was 236; four new).
- `./gradlew lintDebug` — 86 warnings, 0 errors, 0 fatal. Same total *and* same
  per-id distribution as v73.9.
- **`javap -c` on the five modified paths**: `drawChristmasLights`, `drawClouds`,
  `drawPrecipitation`, `updateLakeBandY`, `drawLake`, `drawMountains` and `hourAt`
  contain no `new`, no `newarray`/`anewarray`, no `valueOf` boxing and no iterator
  allocation. `currentHour24`'s only remaining reference is the
  `TimeZone.getDefault()` inside its cache-miss branch.
- **Mutation testing on `hourAt`**, the only new logic with testable content —
  three mutants, all killed: `floorDiv` → `/`, `floorMod` → `%`, and the integer
  hour division → float.

The four hoisting fixes were **not** mutation-tested, and that is a statement
about coverage rather than a claim of it: a mutant there either changes nothing
or breaks rendering, and no JVM test in this project observes rendering.

### One test that would have lied

The first version of the clock test sampled pre-epoch instants at exact minute
boundaries. At a whole minute, truncating and flooring division agree even for
negative values — so the `floorDiv` → `/` mutant **survived**, against arithmetic
that would have been a full day out for every other instant before 1970. The test
now offsets its samples by 37,123 ms and kills it. Recorded because the gap was
invisible to a passing test suite and only mutation testing found it.

The surviving test compares `hourAt` against the `Calendar` it replaced at
tolerance **`0f`** — bit-identical, not close — across eight zones chosen for the
cases that break naive arithmetic (a half-hour offset, a three-quarter-hour one, a
southern-hemisphere DST schedule, a zone with no DST), a year of samples at a
stride that is not a whole number of hours so it lands inside transitions rather
than stepping over them, and a pre-epoch sweep.

### Verification limits

- **No device, no emulator, no profiler.** Nothing here is a measured CPU
  improvement. What is verified is that specific allocations and state writes are
  gone from specific per-frame paths, and that the tests and lint are unchanged.
  Whether that is perceptible on hardware is the maintainer's to judge.
- The rendering paths themselves are not covered by any automated test, so the
  claim that the four hoisting fixes are behaviour-preserving rests on the code
  being provably the same arithmetic, not on a test asserting it.
- `git` was unavailable in the session, so `.gitignore` behaviour was verified by
  extracting the ZIP into a clean directory rather than by `git check-ignore`.

---

## v73.9 — Phase 3.3: normalise padding and grid

Delivered at **Verification Level 3**. The change touches runtime resources, the
renderers, the asset tooling and the tests in one delivery, which is exactly the
combination `AI_PROJECT_RULES.md` §12.B escalates: `test` + `lintDebug` +
`assembleDebug`, plus a clean extraction and rebuild from the release ZIP.

Android `versionCode` and `versionName` are deliberately unchanged: this is a beta
on top of Android version 73.

### What changed

**76 of the 118 shipped PNGs were cropped to their normalised content boxes, and
the 35 call-site origins that position them were compensated in the same change.**
Decoded memory fell from **33.37 MB to 17.20 MB** — 16.17 MB, 48 % of everything
the sprite set used to decode. On-disk the drawable directory went from 864 KB to
808 KB, and the debug APK from 19,090,926 to 19,025,478 bytes; the memory saving
is the point, and it does not show up in either of those numbers.

The composed rendering is unchanged. Not "should be" — measured: **109 composites
were built before and after, placing each shipped PNG at the origin the pre-change
Kotlin passed and each cropped PNG at the origin the current Kotlin passes, and
compared channel by channel. 0 differing pixels, peak channel delta 0.**

### The rule

A sprite's normalised content box is the union of the measured alpha bounding
boxes of its co-registered group, rounded outward to a multiple of
`SPRITE_PIXELS_PER_UNIT` for a `SCENE_UNITS` sprite and of 1 px for a
`CANVAS_PIXELS` one. The sprite is cropped to it; its call site's origin is
compensated by `trim / unit`.

**Why outward and not to the measured box.** The blitter multiplies the origin by
the same unit the compensation divided by. Crop to the measured box and a trim of
17 px becomes a compensation of 5.667 units, which returns as 17.000002 — a
sub-pixel origin, resampled because the blit paint carries `FILTER_BITMAP_FLAG`.
Rounding outward keeps the compensation an exact integer and leaves up to
`unit - 1` px of padding behind. That residue is load-bearing, not an unfinished
job.

**Why a union over a group.** 44 sprites are selected from a lookup table at draw
time, so one origin literal positions all of them: the 32 walk frames, the 8
window occupants, the 4 car drivers. Their content boxes differ — the mid-stride
walk frame reaches 9 px further left than the others — so cropping each to its own
box would need one origin per member, which does not exist and which rule 7.3
forbids. The result would be a horizontal jitter in every walk cycle. The union is
the box that removes the padding they all share while holding them registered
against each other.

A shared origin *value* is not a group: `tree_canopy` and `tree_canopy_snowcap`
are both blitted at (-45,-84), but from two separate call sites with their own
literals, so each took its own crop and its own compensation.

### What was deliberately not done

- **`palmtree_fronds` and `palmtree_fronds_frost`.** 102×176, and 176 is not a
  multiple of the oversample, so the pair is already off the grid. Cropping the
  empty rows above the fronds is clean but leaves it off the grid still, because
  the bottom edge is the sprite's own; putting it on the grid means padding back
  what was removed or cropping artwork. They also share the hand-tuned `-87.45f`
  origin, which is anchor semantics. Deferred.
- **The moon phases.** Individually 0–49 % padding; together, the union is the
  full canvas. The group rule refuses the crop on its own, with no special case,
  and that is what keeps the moon still as it waxes.
- **The orphan drawables.** No call site references them, so there is no origin to
  compensate. Whether they should exist is 7.2's question.
- **Anchor semantics.** No `anchorRule` was added, changed or resolved; the 101
  `UNDETERMINED` anchors are still undetermined. The 17 determined ones moved by
  exactly the amount their origins did, so `validate`'s `origin == -anchor` check
  still holds — it is re-derived, not carried over.

### Files

- `tools/assets/paperscrape_assets/normalize.py` — new: the rule, the groups, the
  exclusions, and the plan they produce.
- `tools/assets/paperscrape_assets/cli.py` — new `normalize` command, part of
  `all` in check form. `--apply` is the one command permitted to write into
  `res/drawable-nodpi`, and the docstring says why that is not an exception to
  `render`'s prohibition: `render` produces new artwork, `normalize` removes rows
  whose alpha is zero.
- `tools/assets/sources/sprites.json` — `width`, `height`, `contentBox` and the
  derived `anchor` updated for the 76 cropped sprites, edited in place so the
  diff stays three fields per entry rather than a reformat of all 118.
- `app/src/main/res/drawable-nodpi/*.png` — 76 files cropped.
- `engine/SceneObjectRenderer.kt` — 27 origins compensated, 3 of them the shared
  lookup-group literals.
- `engine/PaperRenderer.kt` — 8 origins compensated, including
  `SUN_GLOW_ORIGIN_UNITS` (-222 → -198) and `STAR_SPRITE_ORIGIN_UNITS` (-32 →
  -30), each with its KDoc corrected in the same edit.
- `app/src/test/kotlin/.../SpriteNormalisationTest.kt` — new, 1 test.
- `app/src/test/kotlin/.../SkySpriteAnchoringTest.kt` — the star-span case
  restated (see below).
- `tools/assets/tests/test_normalize.py` — new, 16 tests.

### Two things future work must not undo

**The historical divisors.** `130f / 680f` for the sleigh and `15f / 70f` for the
birds reproduce the old vector versions' on-screen footprints; they are *not*
readings of a sprite's canvas. Both sprites were cropped heavily, and both
comments now say explicitly that the divisor must not be re-derived from the PNG.
Recomputing either would rescale the artwork — the exact shape of defect D-1.

**The star-span assertion.** `SkySpriteAnchoringTest` used to assert that
`star_sparkle` spans exactly `2 × radius`. That was a property of the sprite's
*canvas*, which carried 6 px of transparent margin per side; the *artwork* only
ever reached 0.9375 of that. After the crop the canvas is the artwork, so the
equality would now be a claim about padding. It is restated as the bracket it
always really was — the sparkle fills the star's radius without exceeding it —
and the bracket still catches what it exists to catch, because the two authoring
conventions are a factor of 3 apart and any window narrower than 3:1 admits only
one of them.

### Verification

- `./gradlew test` — **236 tests, 0 failures, 0 errors** (was 235).
- `./gradlew lintDebug` — 86 warnings, 0 errors, 0 fatal. Same total *and* same
  per-id distribution as v73.8, `IconDuplicates` at 16 included: cropping 76
  sprites changed their bytes but not which of them are identical to each other.
- `./gradlew assembleDebug` — SUCCESS, 0 compiler warnings, APK 19,025,478 bytes.
- **Clean extraction of the release ZIP into an empty directory, then `test` and
  `assembleDebug` from the extract: 236 tests, 0 failures, and an APK of
  19,025,478 bytes — byte-for-byte the same size as the in-place build.** The
  Python tooling was also re-installed and re-run from the extract: probe
  fingerprint matched, 59 tests passed, `validate` and `normalize` clean. A
  repository was initialised in the extract to confirm `CLAUDE.md` matches
  `.gitignore:44` and stays untracked.
- 59 Python tooling tests (was 43), `paperscrape-assets all` clean, rasteriser
  probe fingerprint unchanged, `compare` still 11 pixel-identical and 13
  edge-equivalent.
- **Mutation testing on the rule**, 5 mutants, all killed: rounding inward instead
  of outward, replacing the group union with the first member's box, ignoring the
  grid unit, ignoring the exclusion list, and cropping unreferenced sprites.

### Verification limits

- **No device or emulator was available.** Every equality claim above is static:
  composites reconstructed from `SpriteBlitter`'s placement model and compared
  numerically. That proves the buffers agree before the uniform `canvas.scale`;
  it is not an observation of the running wallpaper, and maintainer confirmation
  on hardware is still outstanding.
- **The 48 lookup-selected sprites remain outside `validate`'s reach.** Their
  origins are literals the resolver cannot attribute to a sprite, so the
  registry check does not cover them. The 109-composite comparison does, and it
  is the strongest check available without a device — but it is a one-off run,
  not a standing invariant.
- `git` was unavailable in the session, so `.gitignore` behaviour was verified by
  extracting the ZIP into a clean directory rather than by `git check-ignore`.

### Known defect recorded, not fixed

**D-2: an Italian caption is rasterised into `santa_sleigh_scene.png`**, inside
the content box and therefore drawn at runtime. Found while measuring the sprite
for this phase. It is content rather than geometry, the sprite has no source, and
editing it is a redraw with a visual approval attached — so it is registered and
left alone. The crop treats it as opaque artwork like every other drawn pixel and
keeps it. A heuristic scan for similar captions across the other 117 sprites
returned no candidates *and failed to find this one*, so the rest are unchecked,
not clean.

---

## v73.8 — Phase 3.2: the asset manifest

Delivered at **Verification Level 2**: the change touches the offline asset
tooling under `tools/assets/` and its registry, plus documentation. **Nothing
under `app/` changed** — the tree there was diffed against the v73.7 release ZIP
and is byte-identical, so the APK is unaffected by construction. No Gradle or
build configuration, no `AndroidManifest.xml`, no CI, no runtime PNG, no
rasterisation code, no lifecycle change. `assembleDebug` intentionally skipped
under normal verification policy.

Android `versionCode` and `versionName` are deliberately unchanged: this is a beta
on top of Android version 73.

The level was chosen deliberately and is worth recording, because a literal
reading of `AI_PROJECT_RULES.md` §12.B — which lists "the asset pipeline" among
the Level 3 triggers — would point at Level 3. The distinction applied here is the
one the project has already used: v73.5 *created* the pipeline and was Level 3;
v73.7 changed one data field in the registry and was Level 2. This release changes
`registry.py`, `cli.py`'s validation path and the registry data, but not
`raster.py`, `fit.py` or `fidelity.py`, and it produces no PNG. **The maintainer
was asked and approved Level 2 before implementation.**

### What Phase 3.2 was for

`AI_PROJECT_RULES.md` §6.2 requires every asset to declare its nominal size,
content bounding box, anchor point, scale convention, category and tint class.
Phase 3.1 delivered four of those six. The bounding box was *measured* into
`reports/` but never declared, and the anchor was neither.

The concrete motivation is defect D-1. A sprite's pixel size, its scale convention
and its origin are correct only together; **nothing in a PNG records which
convention applies**; so the convention lived at the call site, the registry
declared it separately, and nothing compared the two. When v73 replaced
`star_sparkle.png` with a 3x redraw and left the call site alone, every check in
the project passed.

So the deliverable is not "more fields in a JSON file". It is that the registry
stops being a document and becomes a contract something verifies.

### What was implemented

- **Registry schema 2.** `contentBox` is mandatory for all 118 sprites.
  `anchorRule` is mandatory, with `anchor` in the sprite's own local units —
  pixels divided by `SPRITE_PIXELS_PER_UNIT` for a `SCENE_UNITS` sprite, pixels
  unchanged for `CANVAS_PIXELS`, which is the space a call site blits in. Two
  rules are in use: `CONTENT_BOTTOM_CENTRE` (13 sprites) and `SPRITE_CENTRE` (4).
- **`UNDETERMINED` is a first-class value**, carrying a mandatory `anchorReason`
  and forbidden from carrying an anchor — the same shape `source.kind = "none"`
  already had. 101 of 118.
- **`paperscrape_assets/callsites.py`** (new) resolves sprite blit call sites from
  the Kotlin sources.
- **`validate` gained four comparisons**: `contentBox` against the PNG; `anchor`
  against what its rule derives; `scale` and `tint` against the code; and, for a
  determined anchor, the blit origin against it.
- **Coverage is printed on success**, split per check rather than lumped into one
  figure.

### Why 101 anchors are undetermined, and why that is the result rather than a shortfall

The only evidence for an anchor is the origin a call site blits the sprite at, and
that origin is `placement - anchor`: one equation, two unknowns. It collapses to
the anchor alone only when the sprite is an object in its own right, placed at the
object's own position.

Measured against the sources: of 54 literal call sites in `SceneObjectRenderer`,
**13 sit exactly at the content box's bottom centre** — the root sprite of each
composite object. The other 41 are composition placements of parts;
`house_large_window` alone is drawn at four different origins. The `person_*`
sprites are drawn at `(-50, -95)`, which is neither the bitmap centre nor the
content base, consistent with their being outside the anchoring system entirely
(`DESIGN_NOTES.md` §6).

Choosing a plausible rule for those would be an invention presented as a recovery
— the exact thing Phase 3.1's gap declarations exist to prevent. Separating
placement from anchor at each call site is the re-anchoring work in Group 4.

### The resolver refuses to guess, on purpose

`callsites.py` does no dataflow analysis. A blit whose sprite argument is not a
literal `R.drawable.<name>` (`resId`, `driverRes`, `phaseSprite`) is recorded as
unattributed; an origin computed from the drawn object's own dimensions
(`-width / 2f`, `-height - 50f`) resolves to nothing. Both are reported as
**unresolved**, never folded into the pass count.

This is the property the whole check depends on. A resolver that guessed would
turn "not checked" into "checked and fine", which is the shape of failure that let
D-1 ship.

Reach: `contentBox` 118 sprites, `scale`/`tint` 64, origin-against-anchor 17.

### Two findings

- **`santa_sleigh_scene` is blitted through `drawTinted` with an identity white
  tint.** The first run of the check flagged it as contradicting its `FIXED_ART`
  declaration. It does not: white is the identity under `MULTIPLY`, and the tinted
  entry point is used only because `draw` has no alpha argument. The resolver now
  models this and a test pins it. Phase 3.1 had already recorded the behaviour in
  the sprite's `notes` — but nothing enforced it, which is this release in
  miniature. Out of scope to change; noted for whenever `draw` gains an alpha
  parameter.
- **Wrapper detection had to be tightened during implementation.** The first
  version treated any private function taking a `Canvas` and containing exactly
  one blit as a forwarding wrapper — which matched ordinary drawing functions like
  `drawCloud` and swallowed the only call site those sprites have. A wrapper now
  has to forward its own `Canvas, Int, Float, Float` parameters into the blit. A
  test covers the near miss directly.

### Verification

- **`./gradlew test` — 235 tests, 0 failures, 0 errors.** Unchanged from v73.7, as
  expected: no Kotlin was touched. Run before the change as a baseline and not
  re-run after, because `app/` was proven byte-identical by diff.
- **`./gradlew lintDebug` — 86 warnings, 0 errors, 0 fatal**, same total and same
  per-id distribution as the v73.7 baseline. Same reasoning.
- **`python3 -m unittest discover -s tests` in `tools/assets/` — 43 tests, 0
  failures** (was 12; 31 new).
- **`paperscrape_assets validate`** — clean: 118 entries, 24 with an SVG source, 94
  gaps; 17 anchors determined, 101 undetermined; scale and tint compared for 64
  sprites, origin for 17.
- **`paperscrape_assets probe`** — toolchain fingerprint matches the pinned value,
  so the Phase 3.1 fidelity figures under `reports/` remain valid.
- **Mutation testing — 9 mutations, 9 killed, 0 survivors.** Anchor read from the
  content top; anchor read from the canvas base rather than the content base;
  scale convention dropped from the derivation; `units_per_pixel` forced to 1;
  `SPRITE_CENTRE`'s centring check disabled; `contentBox` comparison removed;
  origin/anchor comparison removed; and the two that matter most — an unresolvable
  scale treated as agreement, and a sprite with no call site treated as agreement.
- **No allocation audit**: no draw path exists in this change.

### Tests

`tools/assets/tests/test_manifest.py` is built around near misses, because a
criterion that cannot fail asserts nothing: a bounding box off by one pixel, an
anchor off by one unit, an anchor left in the other scale convention (D-1 in a
single case), a swapped scale, a swapped tint. The resolver half covers what it
must *not* read — a commented-out call, a lookup-table sprite, a computed origin —
and `ShippedSourcesTest` pins coverage by naming the three expressions that cannot
be reached rather than by asserting a count, since a bare number would simply be
edited by whoever reduced the coverage.

### Maintainer-side verification

- **Nothing to look at on a device.** This release adds no code to `app/` and
  modifies no runtime asset; `app/` is byte-identical to v73.7. If anything looks
  different on screen, that is a defect in this claim, not a change in behaviour.
- Reviewing `tools/assets/sources/sprites.json` is worthwhile — particularly
  whether the 101 `anchorReason` texts read as honest, since they are the record a
  future session will trust.
- Confirm `v73.8` is unused before tagging. **No tag was created in this session,
  at the maintainer's instruction.**
- Practical CPU, battery and thermal observation of cumulative Phase 1 and 2 work
  remains outstanding.

### Residual risks and limitations

- **The call-site resolver is syntactic and will lose reach if the sources change
  shape.** Moving a sprite behind a constant, or an origin into a computed
  expression, silently converts a checked sprite into an unresolved one. It is
  reported rather than hidden, and `ShippedSourcesTest` fails if the set of
  unreadable expressions grows — but nobody is *forced* to look at the coverage
  line.
- **101 anchors remain undetermined**, so for those sprites the manifest records
  the absence rather than the value. Phase 3.3 will move content inside the canvas
  for many of them with no anchor declaration to check the result against.
- **`contentBox` is now a declared value that Phase 3.3 will invalidate wholesale.**
  Intended, but it means 3.3 must update the manifest in the same change that
  regenerates a sprite.
- **Nothing was observed on a device**, and nothing in this release could be.
- **No Git state could be inspected**: the release ZIP carries no `.git`, so
  `git check-ignore`, `git ls-files` and `git tag --list` could not be run against
  the working tree. `CLAUDE.md`'s ignored status was verified by extracting the ZIP
  into a clean directory and initialising a repository there. `v73.8` was
  determined from `RELEASE_HISTORY.md` and `release-notes/`, not from tags.
