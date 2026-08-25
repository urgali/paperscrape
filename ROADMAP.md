# PaperScrape Roadmap

Operational plan only. What shipped and why lives in `RELEASE_HISTORY.md`; how the
code works lives in `ARCHITECTURE.md`; the visual rules live in `DESIGN_NOTES.md`;
the rules that always apply live in `AI_PROJECT_RULES.md`.

**Nothing below is approved. Ask before starting any of it.** The v3.5 batch closed everything
that was.

---

## Current status

**v4.5 prepared -- the atmospheric effects sized against the world they fall on.**

`versionCode = 36`, `versionName = "4.5"`. **No tag, no push, no GitHub Release.**
Baseline: the published **v4.4** tag `b3a7389`.

```
v4.5 [x]
 |- rain, snow and the lightning bolt are declared in scene metres and converted by
 |  SceneSpace.pixelsPerMetre, the same route a house or a car takes -- a raindrop was
 |  1.15x the height of the pedestrian beside it and is now 0.40
 |- the precipitation pool is 240 rather than 90, chosen from a measured sweep: the
 |  curtain fills 86% of a grid against 46%, and the frame cost is flat from 90 to 400
 |- a bolt reached 1.32x the tallest painted building and now reaches 0.78
 \- six goldens regenerated, the other twenty-one untouched; the flash veil and the
    falling leaves were measured and deliberately left alone
```

**v4.4 published** -- `versionCode = 35`, tag `v4.4`.

```
v4.4 [x]
 |- every precipitation size scaled to the viewport instead of being absolute canvas
 |  pixels -- the right principle, at three times the right magnitude, which is what
 |  v4.5 corrected
 \- a built-in override the pre-v4.3 save path thinned rather than emptied gets its
    whole car inventory back, behind a reconstruction that has to match exactly
```

Threads still open, recorded rather than scheduled:

- **Three effects are still sized in absolute canvas pixels**, measured in v4.5 and left alone
  because none is demonstrably wrong on a device: `drawFallingLeaves` (a leaf is 0.26 m on a
  phone, which reads, but 0.80 m on the 360x800 test frame), birds (identical at 12x51 px on
  every viewport, so a third of their intended size on a phone) and clouds. Each needs its own
  before/after measurement and a decision about what size it *should* be; none was reported.
- **The lightning flash veil covers the whole frame at 71 % opacity for a third of a second.**
  Measured in v4.5 and rendered beside 120 and 90 for comparison. Not changed: a veil has no size
  to be out of scale. A decision rather than a defect.
- **`drawFallingLeaves` sizes its leaves in absolute canvas pixels**, exactly as precipitation did
  before v4.4 -- a `drawOval(-4, -6, 4, 6)` and a `* 26f` sway, so Fall Colors' leaves are about a
  third of their intended size on a phone. Found while diagnosing v4.4's rain defect and
  deliberately left out of that batch: different feature, not reported, and it needs its own
  before/after measurement and a look at whether any golden moves.
- **A pedestrian can paint over the top row of a car** (1-8 px measured). Pre-existing since
  v4.0; `drawPeople` runs after the vehicle loop and the near pavement sits 1-2 px above the far
  lane. Fixing it means opening the vehicle draw path, which needs its own approved batch.
- **A thinned street can still be one-toned.** `desert` at 65% is four people on tone 2. See
  D-4.2-D: the alternative costs real variety at the default density.
- **A fourth, deeper skin tone needs an art pass**, unchanged from v4.1.
- **The sprite memory budget doubled in v4.1** (14.79 -> 25.67 MB decoded, ceiling 26 MB).
- **The committed goldens are device-specific.** Taken on a Pixel 9 / API 37 emulator; the
  pre-v4.2 set did not match it. CI or another device may need its own regeneration pass.
- **The golden net is blind to a whole-population resize.** All 24 goldens *passed* v4.3's
  pedestrian change at 0.025-0.136% against a 0.2% tolerance. `VehicleScalePixelTest` measures
  instead of comparing, which is the shape that catches it.
- **"Replace my built-in with this shared theme" is designed for but not built.** The theme format
  records `sourceThemeId` so it is possible later; an import today is always a new theme.

1. **`targetSdk 36 -> 37`.** `compileSdk` was already 37 and is unchanged. Every Android 17
   behaviour change was re-assessed against this app's real code from the v3.9 baseline, not
   against a generic list: **none required a fix**, and most are `NOT_APPLICABLE` because the
   capability simply is not used -- no reflection, no notifications, no foreground service, no
   alarms or jobs, no native libraries or dynamic class loading, no local-network access, no SMS,
   contacts, audio or Bluetooth, no orientation or resizability declarations, and every
   `startActivity` is from a visible Activity or a Compose click. Lint confirms the flag took
   effect: `OldTargetApi` is gone and the count drops 32 -> 31.
2. **Certificate transparency and ECH, the two the emulator could not close in v3.8, were
   exercised at `targetSdk = 37`.** All five HTTPS hosts -- `api.open-meteo.com`,
   `geocoding-api.open-meteo.com`, `api.weatherapi.com`, `api.openweathermap.org`,
   `api.github.com` -- completed the TLS handshake through the app's own `HttpURLConnection` path
   on a device running the new build. Nothing in the app touches `SSLContext`, `TrustManager`,
   `HostnameVerifier` or a network security config, so there is no workaround in place and none
   was added. **Still `pending` on real hardware** -- see below.
3. **The location row shows the place name *and* the coordinates.** Reverse geocoding already
   existed and already ran for GPS and Network; what it did was *replace* the coordinates, so the
   two facts were never on screen together and the numbers vanished as soon as a name arrived. The
   name is now the title and the coordinates sit under it, the shape the Custom row has always
   used. A geocoder that fails, times out or is absent costs the name only -- the coordinates are
   the title in that case, and it is never reported as a location failure.
4. **`LocalityLabelCache`** is the new part: a pure, JVM-tested policy for *when* a fix is worth
   geocoding. 1 km threshold, chosen because it exceeds Network-mode jitter and matches the row's
   own two-decimal display, so the cache can never hide a change the row would show. Successes do
   not expire (a place name at a fixed coordinate does not change, and the cache is in-memory);
   failures are not cached as labels and are retried after 60 s. A superseded lookup cannot
   overwrite a newer one.

**Custom is deliberately untouched** -- it already carries a name the user chose, and nothing is
looked up for it.

875 JVM tests (850 in v3.9, +25), `lintDebug` 0 errors and 31 issues, debug + androidTest + R8
release APKs, 37 instrumented tests on Pixel 9 / Android 17, and a runtime pass covering GPS,
Network, Custom, a forced geocoder failure, wallpaper persistence across a reboot, lock/unlock and
the updater. Five deliberate mutations confirm the new tests fail when the code is broken. See
`RELEASE_HISTORY.md`.

**Two things v4.0 does not close, and must not be reported as closed:**

- **Real-hardware verification.** No physical device was available in this session; everything
  runtime above is the Pixel 9 / Android 17 **emulator**. CT and ECH in particular were called out
  in v3.8 as *not* closeable on an emulator, and that judgement stands -- an emulator's network
  stack, CA store and system image are not a phone's.
- **OpenWeather and WeatherAPI.com end-to-end.** Both API keys supplied for the v3.9 session had
  been revoked by the time of this one -- confirmed independently with `curl` from the host, which
  gets the same `401` -- so only Open-Meteo, the keyless default, could be driven to a successful
  fetch. Both keyed hosts were still reached and answered, which is what the CT/ECH question turns
  on.

---

**v3.9 prepared -- a corrective release, strictly two items.**

`versionCode = 30`, `versionName = "3.9"`. **No tag, no push, no GitHub Release.**
**`targetSdk` is still 36** -- v3.9 changed nothing about it, deliberately, and must not.

```
v3.9 [x]
 |- OpenWeather fix
 \- Gradle warning fix

v4.0 (next)
 \- targetSdk 37
```

1. **OpenWeather reported a rejected key as an unreachable service, and now does not.** The device
   report was *"OpenWeather could not be reached"* while Open-Meteo and WeatherAPI.com worked.
   Reproduced on the emulator and isolated: **the provider is correct.** With a valid key the
   endpoint, URL, query parameters, HTTP transport, TLS, parser, unit conversions and condition
   mapping all work and were verified at runtime -- **nothing in `OpenWeatherProvider.kt` was
   changed.** What produced the message was `LiveWeatherStatus.of` folding every
   `WeatherFetchResult.Failed` into `FAILED`/`STALE`, whose banner claims unreachability, so an
   **HTTP 401** -- a service that answered and refused the credential -- was reported as a network
   problem. `WeatherHttp` already classified 401/403 as `UNAUTHORIZED` "because the settings screen
   can say so"; nothing consumed it. One new status, `REJECTED_API_KEY`, and one banner now keep
   that promise. It bites OpenWeather and not the other two because OpenWeather's own error-401 FAQ
   says a newly created free key takes a couple of hours to activate -- so its rejected keys are
   usually *correct* keys.
2. **The `srcDirs` build-script deprecation is gone.** `app/build.gradle.kts` used
   `java.srcDirs("src/androidTest/kotlin")`, which AGP 9.3.1 marks
   `@Deprecated("Use `directories` mutable set instead")` -- confirmed by reading the annotation off
   the packaged API, not from the message alone. Now `java.directories.add(...)`, whose getter
   carries no deprecation. The resolved source directories were printed before and after and are
   identical.

**Open-Meteo is still the default**, still asserted three ways, and no provider's fetch path was
touched. **Visual Crossing stays removed.** Everything v3.8 closed is intact.

850 JVM tests (842 in v3.8, +8), `lintDebug` 0 errors, debug + androidTest + R8 release APKs, 37
instrumented tests on Pixel 9 / Android 17, and a runtime pass in which **all three providers
fetched successfully with real keys**. See `RELEASE_HISTORY.md`.

---

**v3.8 prepared — a third weather provider, goldens that finally contain traffic, the preview/renderer
sharing extended exactly as far as the evidence went, and a v3.7 claim retracted.**

`versionCode = 29`, `versionName = "3.8"`. **No tag, no push, no GitHub Release.**
**`targetSdk` is still 36** — v3.8 changed nothing about it, deliberately.

1. **OpenWeather added as a third provider.** **Open-Meteo remains the default** and is asserted to
   be, three ways. OpenWeather uses the plain **Current Weather Data** API (`/data/2.5/weather`),
   not One Call: One Call requires a payment card even for its free allowance, which was the reason
   v3.7 rejected the provider outright. Its condition ids are *structured* by hundreds digit, so the
   mapping is a group rule plus four named exceptions and is checked over all 700 ids in the space,
   not only the 55 that exist. No key is compiled in; the user's lives in their own DataStore.
2. **Traffic goldens.** `traffic-day` and `traffic-night`, the first frames in this project's
   history to contain a vehicle. `GoldenScene.warmUpFrames` closes the structural gap; 390 frames
   was measured, not guessed. Presence is asserted off the finished pixels, and five deliberate
   regressions — a car moved by one frame, cars off, density halved, cars 10% taller, the near lane
   moved — all fail the goldens while a healthy render passes.
3. **Preview/renderer agreement extended to the skyscraper, and to nothing else.** The audit found
   **55 shared drawables, all 55 in exact agreement**; the tower is the only group with a folded
   expression *and* a real divergence, and it now reads `SkyscraperSpriteLayout`. Unifying the other
   47 would guard against nothing, which the brief called artificial and the evidence agrees with.
4. **Snowcap: v3.7's claim was wrong and is retracted.** Measured from the shipped PNGs, **0 of
   17 182 cap pixels fall off the crown** at the offset the renderer uses. The "3 units off-centre"
   finding came from comparing canvas widths instead of content; centring the cap by canvas width
   would push 442 pixels of snow into open sky. Nothing was changed, and a test now pins it.
5. **A layout defect this release introduced, and fixed.** Three providers do not fit a segmented
   control at full name length: "WeatherAPI.com" wrapped and drew outside the control's outline.
   The selector uses a short name, and the shared component now bounds its label to one line so the
   fourth option cannot find the same edge.

**`targetSdk 37` readiness: READY.** Assessed, and tried — build, lint, 842 tests, 37 instrumented
tests and a runtime pass all clean at `targetSdk = 37`, then reverted. See `RELEASE_HISTORY.md` for
the behaviour-change-by-behaviour-change assessment. **v4.0 can be prepared from this baseline with
no intermediate v3.9.**

842 JVM tests, 37 instrumented on Pixel 9 / Android 17, `lintDebug` 0 errors and the same 32
warnings, debug + R8 release APKs, and a runtime pass with a clean logcat. See `RELEASE_HISTORY.md`.

---

**v3.7 prepared — Visual Crossing replaced, the road measured and left alone, the preview/renderer
drift closed, `ARCHITECTURE.md` brought current, and the GL goldens given a gate that can see what
the whole-frame ones provably could not.**

`versionCode = 28`, `versionName = "3.7"`. **No tag, no push, no GitHub Release.**

Eight strands, assessed first and then implemented where the problem was demonstrated. Three closed
as *no fix needed*, with the measurements on record rather than an opinion.

1. **Weather providers.** **Open-Meteo remains the default and is not in question** — it is the only
   candidate needing no key, so nothing ships a credential and nothing is asked of the user. Visual
   Crossing is **removed from every operational path**; **WeatherAPI.com** replaces it as the
   optional keyed alternative. Chosen over OpenWeather because OpenWeather routes current
   conditions through One Call 3.0, which requires a **credit card** even for the free allowance,
   and because WeatherAPI publishes its condition vocabulary as machine-readable JSON — committed
   as a fixture, all 60 codes walked by a test. That is the specific defect **D8** named about the
   provider being removed, and it is why D8 closes rather than moves.
2. **Road width — no fix needed (E).** Measured, not adjusted: lanes are **0.95–1.10 car heights**
   apart against a documented target of "about one", the carriageway is **2.05–2.37 car heights**
   deep, and even the fire engine fits inside it (1.03). Confirmed on a device against real
   traffic: a near-lane car sits 98% inside the tarmac. What the eye reads is the inherent
   asymmetry that a far-lane car's roof clears the top edge by about a third of its height; closing
   that would mean widening the strip until it dominates, which is the regression v76.6 narrowed
   the spacing to fix.
3. **`ThemePreviewScene` — C, minimal refactor done.** The preview copies the renderer's sprite
   offsets by hand: **71 pairs, 59 sprites shared, 56 in exact agreement** once the renderer's
   nested transforms are folded in — and one drifted. The winter tree's snow cap sat 3 units right
   and 2 down from where the wallpaper draws it. The duplication was **not** removed wholesale
   (the preview is a flat 320x240 description with no perspective, candidates or scroll; unifying
   means refactoring `SceneObjectRenderer`, which nothing supports); the hand copy for the one
   object that actually drifted was, via `TreeSpriteLayout`, and 59 placements across 12 themes are
   now guarded.
4. **`ARCHITECTURE.md` — P2-8 closed.** Re-read in full against the source: stamp now v3.7, the
   fourteen `engine/` files it never mentioned added, the weather section rewritten, the testing
   section split into its JVM and instrumented layers, the removed CI job recorded as history, and
   the false claim that *"no automated test observes a rendered frame"* corrected.
5. **GL golden sensitivity — closed.** A **region-targeted** gate, reusing the `GoldenFocus`
   mechanism the Canvas suite already has. Inside the sun's glow at channel 4: two genuinely
   different GL drivers differ by **0.051%**, reducing the glow's fan to a triangle by **7.02%**,
   halving its intensity by **2.71%**. Limit 0.50%. Both regressions pass every whole-frame gate,
   which is exactly the blind spot v3.2 documented and could not close.
6. **Cache lifecycle — no fix needed (A for all four).** The flagged asymmetry rested on a false
   premise: `SpriteCacheIndex` is not an independent cache but `SpriteCache`'s own `private val`,
   cleared by the same `clear()` the trim path calls. `GradientShaderCache` is per-instance, 768
   bytes of bookkeeping, and dies with its owner. **No `onTrimMemory` hook was added for symmetry.**
7. **Deprecated `DirectionsWalk`** replaced with the `AutoMirrored` variant. Verified on screen.
8. **The `Collect device diagnostics` lesson** is documented as `AI_PROJECT_RULES.md` **10.13** —
   `|| true` bounds an exit status, not a runtime — and **no E2E job, emulator or `adb` diagnostic
   was reintroduced.**

Also corrected: `AI_PROJECT_RULES.md` **§12.3**, which had claimed for five releases that no
emulator was available.

815 JVM tests, 24 instrumented on Pixel 9 / Android 17, `lintDebug` 0 errors and the same 32
warnings, debug + R8 release APKs, and a runtime pass with a clean logcat. See `RELEASE_HISTORY.md`.

---

**v3.6 prepared — the emulator CI job is gone, the Canvas backend stopped rebuilding the same three
gradients every frame, and the three unsynchronised scene fields became one published snapshot.**

`versionCode = 27`, `versionName = "3.6"`. **No tag, no push, no GitHub Release.**

Three items, and deliberately nothing else.

1. **The `instrumented` CI job is removed.** It never once produced a signal about PaperScrape's
   code. Every hosted run it was given failed for a different environment-level reason — SDK
   provisioning (v3.3), a boot/install race (v3.4), and finally a shell syntax error inside the
   action's own wrapper after the AVD had booted, with the diagnostics step then hanging until the
   45-minute timeout so it could not even upload the evidence it exists to collect. The workflow is
   now `build` and `release`, `release` needing only `build`. **The tests themselves are untouched**:
   `app/src/androidTest` still holds all 21 (now 24) of them, and they are run locally against a
   device. `AI_PROJECT_RULES.md` 10.12 is rewritten to describe the CI that exists.
2. **P2-5 closed — Canvas `Shader` allocation in the draw path.** Measured before assuming: a
   counter on the three constructor sites of the pre-v3.6 backend, on an API 37 device, reported
   **180 `Shader` objects built over 60 frames and 900 over 300 scrolling frames, for 3 distinct
   gradients**. `GradientShaderCache` reuses them; the same run now builds **3**. The plausible
   guess that the hill wrap-tile loop asked for three copies per frame was checked and is false —
   its own culling rejects two — so the waste was entirely frame-to-frame. No visual change: all 17
   golden frames pass unregenerated.
3. **P2-6 closed — three scene fields shared across threads.** `sunriseHour`, `sunsetHour` and
   `hasFixLocation` on the wallpaper engine, written by the location path on the main thread and
   read by `renderScene` on the render thread, with nothing ordering the two — while every one of
   their neighbours already carried `@Volatile`. Marking them `@Volatile` would have fixed only
   half of it: three writes are three publications, so a reader can still take the new sunrise with
   the old sunset. Demonstrated deterministically, with the fields *already* `@Volatile`: a fix
   moving Florence → Reykjavík yields a 14.0 h day, against 9.5 h and 20.5 h for the two real ones.
   They are now one immutable `SolarDay` behind a single `@Volatile` reference. 200 000 sampled
   reads under unsynchronised hammering: 0 incoherent.

791 JVM tests, 24 instrumented tests on a Pixel 9 / Android 17, `lintDebug` 0 errors and the same 32
warnings, debug + R8 release APKs, and a runtime pass with a clean logcat. See `RELEASE_HISTORY.md`.

---

**v3.5 prepared — a race in PaperScrape's own test, fixed; the emulator job confirmed unable to hold
up a release.**

`versionCode = 26`, `versionName = "3.5"`. **No tag, no push, no GitHub Release.**

**No application code changed.** The application baseline is still v3.2. The only source file
touched is `AwaitOnceTest.kt`.

v3.4's `build` job failed on `./gradlew test`: `AwaitOnceTest > two threads racing to complete resume
once`, `expected:<200> but was:<199>`. The test starts four threads per iteration and never joined
them, while `awaitOnceOrNull` returns on the first completion by contract — so the counter was read
with stragglers still in flight. Diagnosed first: `v3.4` and `main` are the *same commit*
(`16c7a3de`), and the structure without the join reproduces 199 in 299 of 300 trials while the join
gives 200 in 300 of 300. The test now joins the threads it starts; nothing was relaxed and
`AwaitOnce.kt` is untouched. 30 isolated runs green, full suite 773/0.

`AI_PROJECT_RULES.md` **10.12** now states permanently that `instrumented` must neither block a
release by failing nor hold one up by running, and that `release` must not reach it by any path in
the graph. The workflow already satisfies this and was **left unchanged**; every coupling was
checked. See `RELEASE_HISTORY.md`.

---

**v3.4 prepared — the CI emulator job waits until the device can actually install a package.**

`versionCode = 25`, `versionName = "3.4"`. **No tag, no push, no GitHub Release.**

**No application code changed.** The application baseline is still v3.2; the diff is the
`instrumented` job, the two version numbers and the documentation.

v3.3's provisioning fix got CI as far as the emulator, and then no test ran: `Starting 0 tests`,
because `pm install-create` threw a `SecurityException` out of `StorageStatsService`. The cause is
not a missing app permission — it is that **`sys.boot_completed` fires seconds before the framework
can install a package**, and v3.3 removed the accidental delay (a multi-minute compile) that used to
cover the gap. Reproduced locally on a freshly created API 37 AVD, where the same race produced a
`DELETE_FAILED_INTERNAL_ERROR` and the identical command seconds later ran 21/21.

The job now builds both APKs before the emulator starts, and waits for a real install/uninstall
transaction to succeed before invoking Gradle — measured at ~16 s on a fresh AVD, over three
consecutive from-scratch cycles, each 21/21 with a clean shutdown. Failure-only diagnostics
(component revisions, device state, memory, storage, appops, logcat) are now uploaded as an
artefact, which v3.3's failure had none of.

The job still gates nothing: upstream considers API 37 on hosted runners unproven, with an open
Google bug. See `RELEASE_HISTORY.md`.

---

**v3.3 prepared — the CI emulator job asks the SDK for a package that exists.**

`versionCode = 24`, `versionName = "3.3"`. **No tag, no push, no GitHub Release.**

**No application code changed.** The application baseline is still v3.2; the whole diff is one
workflow input, the two version numbers and the documentation.

v3.2's new `instrumented` job failed on its first real run before the emulator was created:
`sdkmanager` was asked for `platforms;android-37`, which does not exist. Android platform packages
carry their minor version from 36.1 onward — the SDK publishes `android-36`, `android-36.1`,
`android-37.0`, `android-37.1`, `android-37.2` and no bare `android-37` — and the action interpolates
`api-level` verbatim into the package name. The input is now `'37.0'`, quoted so YAML cannot read it
as a float and hand the action back `"37"`.

Reproduced and proven at the level of the failing command with the action's own command-line tools,
then end to end: an AVD created and booted exactly as the action does, on a **stable** API 37 image
(`preview_sdk=0`, where the goldens were first taken on `37.2-beta3`), 21/21 tests green, clean
shutdown. See `RELEASE_HISTORY.md`.

The job still gates nothing — it has been shown to work, not shown to be stable.

---

**v3.2 prepared — the golden tests run themselves, the GL backend is under test, a solar day may
cross midnight, and the geocoder cannot hang.**

`versionCode = 23`, `versionName = "3.2"`. **No tag, no push, no GitHub Release.** From this batch
onward publication is the maintainer's act (`AI_PROJECT_RULES.md` §10.A / §11.D) and what Claude
delivers is a verified ZIP (§12.F). v3.2 exists as a local commit and `PaperScrape_v3_2.zip`; it is
not on GitHub until the maintainer puts it there.

1. **P1-3 — golden tests in CI.** A new `instrumented` job runs `connectedDebugAndroidTest` on an
   API 37 emulator, artefacts uploaded on failure, SHA-pinned action. **Deliberately gates
   nothing** (`continue-on-error`, absent from `release.needs`) until it has a track record.
   Its first real run belongs to the maintainer — Claude cannot execute Actions without pushing.
2. **P1-4 — `GlSceneTarget` under visual test.** An offscreen EGL pbuffer renders the shipped GL
   backend through the real `PaperRenderer`; `day`, `lake-busy` and `thunderstorm` are checked both
   against committed GL goldens and against the Canvas goldens. Every threshold measured against
   two GL drivers; four deliberate regressions run, two caught on all three scenes and two shown to
   be below the driver-to-driver floor.
3. **P2-3 — a solar day may cross midnight.** Sunrise and sunset wrap onto the clock instead of
   being clamped into it, `dayLengthHours` reads a day as an arc, and `compute()` classifies day and
   night circularly. Ordinary locations are bit-for-bit unaffected.
4. **P2-4 — the geocoder cannot hang.** The full `GeocodeListener` (the lambda implemented only
   `onGeocode`, so every error was silently dropped), a 6 s bound, resume-exactly-once, correct
   cancellation, and the legacy blocking path off the main thread.
5. **P2-7 — bird/tap leftover removed** from the README and from `PaperEngine.onCreate`.

Plus **Fase 0**: the permanent Git-publication and delivery-ZIP rules, in `AI_PROJECT_RULES.md`
§10.A / §11.D / §12.F and `CLAUDE.md` §2 / §5.6.

773 JVM tests, 21 instrumented tests, `lintDebug` 0 errors, debug and R8 release APKs, a runtime
pass on an Android 17 emulator and a clean logcat. See `RELEASE_HISTORY.md`.

---

**v3.1 Stable — a damaged preferences file no longer takes the wallpaper down, Live Weather always
has a way out, and a leaping dolphin stops flying through sails.**

`versionCode = 22`, `versionName = "3.1"`. Tag `v3.1`. A deliberately narrow hardening batch: the
five items the v3.0 assessment classified P0/P1/P2 as fixable now, and nothing else. No feature was
added and no working behaviour was changed.

1. **P0-1 — DataStore corruption.** A `CorruptionException` from any of the three preference files
   reached the process's default handler and killed the process that draws the wallpaper; Android
   then replaced PaperScrape with the static system image, and the crash repeated on every restart.
   Each store now carries a `ReplaceFileCorruptionHandler`, each read path answers an `IOException`
   with defaults *without writing*, anything else still propagates, and the engine scope is a
   `SupervisorJob` with a `CoroutineExceptionHandler`. Corrupting one store costs that store and
   nothing else -- proven by corrupting all three in turn, including across a device reboot.
2. **P1-1 — Live Weather with no way out.** An enabled switch is now always disableable, and
   Clouds/Precipitation go read-only on the *effective* state (`OK`/`STALE`) rather than on the
   stored flag, so "Driven by Live Weather" can only be said when it is true.
3. **P1-2 — dolphin over sail.** `LakeLanes.depthOf` sorts a leaping dolphin by where its body
   actually is instead of by the lane it left. `LakeLanes` itself is otherwise untouched; boats do
   not move at all. New golden `lake-dolphin-leap`, with a focused comparison because a dolphin is
   too small for the whole-frame tolerance to see.
4. **P2-1 — updater offline.** `UpdateChecker` returns three outcomes instead of a nullable. The
   explicit button reports "couldn't check"; the launch check still fails silently.
5. **P2-2 — coordinates and locale.** Coordinates are formatted `Locale.US`. Speed multipliers stay
   localised, deliberately, with a test that says so.

753 JVM tests, 18 instrumented tests, `lintDebug` 0 errors, debug and R8 release APKs, and a full
runtime pass on an Android 17 emulator with a clean logcat. See `RELEASE_HISTORY.md`.

---

**v3.0 Stable — the updater works, the lake has depth, Live Weather knows how it is finding you,
the scene is under golden-image test, and PaperScrape stands on its own.**

`versionCode = 21`, `versionName = "3.0"`. Tag `v3.0`. Five pieces of work, each verified on an
Android 17 emulator through MCP as well as by tests:

1. **D13 closed — the updater.** The hang was a `LaunchedEffect` keyed on the state its own body
   cleared: ~30 ms after starting, Compose cancelled the download it had just launched and nothing
   ever moved the UI off `Downloading`. Proven with instrumentation, not inferred. Fixed, plus a
   `Verifying` state and a guarantee that a cancelled download can never leave the screen stuck.
   Run end to end against the real v2.15 → v2.16 releases.
2. **The lake.** Four boats were mapped onto three lanes by a `% 6` that folded, so two shared a
   line and slid through each other; and the water was painted in candidate order rather than by
   depth. `LakeLanes` gives every candidate its own lane and paints far-to-near.
3. **Live Weather location** split into **GPS / Network-Cell / Custom**. Network never touches the
   GNSS receiver -- verified from `dumpsys location`, which shows the GPS provider `OFF` and
   unstarted in that mode. Continuous tracking replaced by one bounded request per refresh, with a
   cached fix preferred and the last saved position as the fallback.
4. **Golden-image tests.** 13 scenes rendered through `CanvasSceneTarget` and compared with
   committed PNGs. Shown to have teeth: reverting the lake fix fails exactly the two lake goldens.
5. **The external reference is gone.** Every operational dependency removed; the history left
   alone deliberately (`AI_PROJECT_RULES.md` §3).

The README now opens with the maintainer's own note about how the app was built.

**v2.16 Stable — the build stack was brought to the current stable line, and nothing else.**

`versionCode = 20`, `versionName = "2.16"`. Tag `v2.16`. **v2.16 is the Android/build component
upgrade and only that** — Phase 2, the dependency upgrade recorded as **D5**, now closed. No
Kotlin source file was modified, no feature was added, and no bug was fixed in it. In particular
it does **not** fix the in-app updater (**D13**, below), which is the next task.

What moved: Gradle 9.5.0 → 9.7.1, AGP 9.3.0 → 9.3.1, Kotlin/Compose plugin 2.2.10 → 2.2.21,
`compileSdk` 36 → 37, and the AndroidX set off its late-2024 versions onto the current stable
line (Compose BOM `2024.10.01` → `2026.08.00`, `core-ktx` 1.13.1 → 1.19.0, `appcompat` 1.7.0 →
1.8.0, `lifecycle` 2.8.6 → 2.11.0, `activity-compose` 1.9.3 → 1.13.0, `datastore-preferences`
1.1.1 → 1.2.1, `coroutines` 1.9.0 → 1.11.0). No source change was needed to make any of it
compile: the 688 tests, the lint result and the debug APK all came through unchanged, and
`targetSdk` deliberately stayed at 36 (**D10**) so nothing about how the app runs could move.

The upgrade was also **run on a clean Android 17 emulator** against the v2.15 build for comparison:
same install, same saved settings, the five settings destinations, the twelve theme previews, the
system live-wallpaper preview and the wallpaper running on the home screen, plus a real update
check and a live Open-Meteo fetch. Screenshots of every screen were diffed pixel by pixel against
v2.15. The main settings screen came out **byte-identical**; everywhere else the only differences
are one-pixel anti-aliasing on glyph and sprite edges — except one real change, recorded as
**D12**: Material3 `1.4.0` restyled `OutlinedButton`.

**v2.15 Stable — the storm flashes only when something is falling, the sky knows about the weather,
and the snow path was finally seen running.**

`versionCode = 19`, `versionName = "2.15"`. The existing lightning system was found already wired
to Live Weather; what was missing was the gate, so a thunderstorm code with every measurement at
zero would have flashed over a dry scene. `isThunderstorm` now means "the scene should storm" —
the condition and something falling — and the source precedence joined the cloud rule in
`LiveWeatherSceneRules`. **D9 is closed**: the snow path was verified on a device against a live
Open-Meteo snowfall, and the weather-driven / theme-driven separation held.

The batch then closed the sky itself. `StormAtmosphere` turns the forecast into one 0–1 strength
that drives sky darkening, cloud darkening and sun attenuation together, as a blend of the theme's
own colours rather than a storm palette, and independent of — but combining with — day/night. Two
defects were found by watching it run and fixed in the same batch: lightning bolts were born above
the cloud band rather than inside it and were three times too large, and the rain response was so
flat at the low end that everyday rain looked like a clear afternoon.

Last measured: 688 Kotlin unit tests passing, `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK. **Seen running on a clean Android 17 emulator** against live provider data for a
thunderstorm, a snowfall and a light drizzle, and through a full controlled A–H weather matrix.

**v2.14 Stable — the settings screens were the wrong size, the sky was not the forecast's, and a
second weather provider.**

`versionCode = 18`, `versionName = "2.14"`. The bottom-spacing bug is closed: it was never
spacing. A settings destination is a full-screen `Dialog` whose content Compose measures against
the display while the window manager sizes the window to the space between the system bars — 2423
px of content in a 2219 px window on the Pixel 9 — so the last 204 px of every screen was laid out
outside the window and clipped. Content is now sized to the window. Live Weather gained a provider
abstraction and Visual Crossing alongside Open-Meteo, with per-provider keys, a required-key state
that makes no request, and no silent fallback between providers. Two Live Weather defects found by
reproducing the reported Florence scene on a clean emulator and fixed before the tag: a weather code
could outvote four measurements reading zero and rain on a dry forecast, and the theme's own cloud
switch could veto the forecast's cloud cover while the same forecast's rain still drew.

Last measured: 636 Kotlin unit tests passing, `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK. **Seen running on a Pixel 9** (Android 16, gesture navigation, 1080x2424 at
2.625x) and on a **clean Android 17 emulator** against live Open-Meteo data — see
`RELEASE_HISTORY.md` for exactly which paths were exercised and which were not.

**Versioning.** Tags are `vMAJOR.MINOR` and must equal `versionName`; `versionCode` is
Android's install counter and only has to increase, independently. v1.0 → 1, v1.1 → 2,
v2.0 → 4, v2.1 → 5, v2.2 → 6, v2.3 → 7, v2.4 → 8, v2.5 → 9, v2.6 → 10, v2.7 → 11, v2.8 → 12, v2.9 → 13, v2.10 → 14, v2.11 → 15, v2.12 → 16, v2.13 → 17, v2.14 → 18, v2.15 → 19, v2.16 → 20, v3.0 → 21, v3.1 → 22, v3.2 → 23, v3.3 → 24, v3.4 → 25, v3.5 → 26 — 3 is unused because no v1.2 was released,
and the counter has no obligation to be contiguous. No pre-release tag form exists yet. `UpdateChecker` compares `MAJOR.MINOR` and ignores any tag that is not
that shape, so the pre-release history's bare integer tags cannot be misread as newer.

---

## Known broken

Nothing. **D13** -- the in-app updater hanging on `Downloading` -- was the only entry and is closed
in v3.0; see Completed for what it actually was. The v3.0 assessment's five P0/P1/P2-now items were
closed in v3.1, and its two P1 test-infrastructure items plus P2-3, P2-4 and P2-7 in v3.2.

---

## Next priorities

**What is left of the v3.0 assessment**, plus the one thing v3.2 created. None of it is approved.

| # | ID | Item | Why it is here |
|---|---|---|---|
| A | — | **Real-hardware verification of v4.0** | `targetSdk 37` shipped in v4.0 and every check that an emulator can perform passed. What an emulator **cannot** settle is **certificate transparency** and **ECH**: its network stack, CA store and system image are not a phone's, and a CT or ECH failure would present as a plain connection failure on real hardware while passing here. All five HTTPS hosts connected at `targetSdk = 37` on the emulator, and no workaround was added, so this is a confirmation rather than an open risk — but it is the maintainer's to confirm. **Also outstanding: OpenWeather and WeatherAPI.com end to end**, which v4.0 could not drive because both supplied keys had been revoked (`401` from `curl` on the host as well as from the app). |
| B | — | **Preview/renderer offset agreement is guarded for two groups** | The tree and the tower. The other 47 shared sprites agree today as plain literals and nothing stops them drifting. v3.8 checked all of them and found no third case worth the indirection; the guard would have to encode each renderer draw function's nested transforms, which is a second copy of the thing being checked. **Not scheduled.** |
| C | — | **The lit night facade's placement is unverified against the artwork** | v3.8 made the preview follow the renderer, which is documented intent (*"laid over it at the same origin"*) and is confirmed by the Christmas window-light grid hanging at the same `+5`. What was *not* possible was measuring it the way the snow cap was: the wall sprite is a plain tintable rectangle with no detectable window grid, so there is no artwork feature to align against. **Not scheduled** — recorded so the basis for the choice stays visible. |
| D | — | **The instrumented tests have no automated trigger** | Unchanged since v3.6. `AI_PROJECT_RULES.md` 10.12 and 10.13 state what a future E2E job must satisfy. **Not scheduled.** |

Deliberately **not** scheduled: any further weather-provider change — the three the comparative
assessed are all now implemented and **Open-Meteo stays the default** — and any refactor of
`PaperRenderer`, `SceneObjectRenderer` or `ThemePreviewScene`. `targetSdk 37` shipped in v4.0 and is no
longer scheduled work; what remains of it is item A's confirmation on real hardware. **D10 is
closed by v4.0** -- `targetSdk` and `compileSdk` are both 37 now.

---

## Older priorities

| # | Item | Why it is here |
|---|---|---|
| 1 | **Device pass on v2.0's theme defaults** | Every built-in theme's defaults were reviewed and corrected: the winter family now enables the winter presentation (roof snow, snow-capped trees, winter clothing), Autumn enables Fall Colors and pumpkins, umbrellas leave the cold themes, the tundra lake loses its yachts and dolphins, Beach stands on sand, Desert gets palms, City is built rather than settled. Winter and Christmas are now two independent flags, so a snowy scene without fairy lights and a lit scene without snow are both expressible. Winter and Christmas snow by default. A fresh install now looks materially different per theme, and v2.0 shipped without any of it having been seen rendering. |
| 2 | **Star-field cost, if it still matters** | Most stars became single `drawCircle` points shortly before v1.0, which cut the per-frame count to roughly a third. Whether the remainder is still worth attention is a question for a device, not for a static count. |
| 3 | **Mountain paths rebuilt per frame** | Two `Path` objects per mountain per frame, from the CPU audit. Real allocation on a draw path; worth doing only if the device shows it. |
| 4 | **Per-vehicle-type toggles** | Cars, taxis, police and fire engines share one visibility switch. Small, self-contained, low value — do it when something else is already open in that file. |
| 5 | **Orphan resources** | Four sprites nothing blits (`house_window`, `road_asphalt`, `road_curb`, `road_line`) and 20 `UnusedResources` lint warnings. Either wire them up or delete them; leaving them is what makes the lint baseline unreadable. |
| 6 | **Device pass on the parts v2.14 did not reach** | v2.14 saw the five destinations, the colour scheme and the settings shells rendering on a Pixel 9, which closes the bottom-spacing half of this. Still unseen: the twelve mini-scene previews (verified by rasterising the scene description the code produces, not by the app drawing it), and v2.12's sun/moon and people work. The updater's end-to-end run left this row for **D13** and was done in v3.0. |
| 7 | **README / lint / KDoc tidying** | `UseKtx`, `ObsoleteSdkInt`, `DataExtractionRules`, and KDoc that has accumulated layers across releases. |

**Both of the items that used to sit above this list shipped in v3.0** — the updater fix and Live
Weather's GPS / Network-Cell / Custom modes. Nothing on the list below is approved.

**Localisation is explicitly out of scope.** PaperScrape is English-only by decision;
about seventy UI strings remain inline in Compose rather than in `strings.xml`, and
that is fine unless the decision changes.

---

## Deferred

Genuinely open, genuinely not worth doing yet.

| ID | Item | Why deferred |
|---|---|---|
| **B5** | The renderer, wallpaper engine, preferences layer and Compose UI cannot be unit tested without being decoupled from `Canvas`/`Context`. | The reason engine fixes are verified on a device rather than by a test. Decoupling is a large refactor with no user-visible result; it earns its place only if engine bugs start recurring. |
| **D4** | Whether the `MULTIPLY` tint's colour-fidelity trade-off is acceptable. | Accepted in practice across the whole V2 set and never reported as a problem. |
| **D10** | ~~`targetSdk` is 36 while `compileSdk` is 37.~~ **CLOSED in v4.0**: both are 37 and the behaviour changes are opted into. Kept here for the reasoning only. | Was deliberate. The Phase 2 dependency upgrade raised `compileSdk` because `core 1.19` and Compose `1.12` require it, and left `targetSdk` alone so the upgrade could not change how the app runs. Raising it is a behaviour change and needs its own device pass — lint's `OldTargetApi` warning is the reminder, not a defect. |
| **D11** | Three lint findings that only appeared once the tooling was current: `ConfigurationScreenWidthHeight` on `SettingsInsets.kt:115`, and three `AutoboxingStateCreation` hints on `SettingsComponents.kt`. | New checks over unchanged code, not regressions. `SettingsInsets` is the file that closed v2.14's dialog-sizing bug, so swapping `Configuration.screenHeightDp` for `LocalWindowInfo.current.containerSize` is a change to the one thing that bug turned on — worth doing deliberately, with a device pass, not as a lint tidy-up. |
| **D12** | Every `OutlinedButton` changed colour in the upgrade. Material3 `1.4.0` moved the default content colour from `primary` to `onSurfaceVariant` and the default border from `outline` to `outlineVariant`, so "Reset this theme's scene to defaults" and the other six outlined buttons now read grey-brown with a pale border instead of orange with a mid border. Verified on an Android 17 emulator by sampling the pixels: the new values are exactly this project's own `onSurfaceVariant` (`0xFF54443A`) and `outlineVariant` (`0xFFD9C7B7`). `TextButton` and filled `Button` are unchanged. | This is Material 3's own current default, and rule 3 says the app follows Material 3 — so it was **left as Material draws it** rather than pinned back, which would mean hard-coding a superseded default into seven call sites. It is nevertheless the one user-visible change the whole upgrade produced, and whether the quieter outlined button reads well is a judgement to make while looking at the app. Pinning it back is one argument: `colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)` plus `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`. |
| **D7** | The V2 artwork retired four user-visible colour behaviours (sun colour reaching only the glow, theme star colour reaching nothing, Fall Colors not reaching palm fronds, per-building window lighting). | Approved as consequences of the redesign. Whether each reads well is a judgement to make while looking at the app, and nothing has been reported. |

---

## Completed

- **v4.0 prepared** — **`targetSdk 36 -> 37`**, with every Android 17 behaviour change re-assessed
  against the real code and none needing a fix, and CT/ECH exercised across all five HTTPS hosts at
  the new target; and the **location row now shows the place name together with the coordinates**
  for GPS and Network, backed by a new JVM-tested caching policy (`LocalityLabelCache`) and a
  documented label format. Custom unchanged, weather unchanged, renderer unchanged.
- **v3.9 prepared** — the two-item corrective release. **OpenWeather's "could not be reached" traced
  to an HTTP 401 being reported as a transport failure**, not to the provider: with a real key the
  provider fetched correctly at runtime and `OpenWeatherProvider.kt` was not changed. A rejected key
  is now its own state, `REJECTED_API_KEY`, and says what OpenWeather actually answered — which
  matters because OpenWeather does not accept a newly created key for a couple of hours, so a
  rejected key there is usually a *correct* one. The `srcDirs` build-script deprecation was replaced
  with `directories`, with the resolved source set proven unchanged. **Open-Meteo still the default,
  `targetSdk` still 36.**
- **v3.8 prepared** — **OpenWeather** added as a third provider on the keyless-signup Current
  Weather API, with **Open-Meteo still the default**; `traffic-day` and `traffic-night`, the first
  goldens containing a vehicle, with five deliberate regressions shown to fail them; the
  preview/renderer sharing extended to the skyscraper and deliberately no further; v3.7's snowcap
  misalignment claim **retracted** after measuring 0 of 17 182 pixels off the crown; and a segmented
  label overflow this release introduced, found on the device and fixed. `targetSdk 37` assessed as
  **READY** without being changed.
- **v3.7 prepared** — Visual Crossing removed and **WeatherAPI.com** put in its place with
  **Open-Meteo still the default**; the road measured and left alone; the one drifted
  preview/renderer sprite offset closed via `TreeSpriteLayout`; **P2-8** closed by re-reading
  `ARCHITECTURE.md` against the source; a region-targeted GL gate that catches two regressions
  every whole-frame gate misses; the cache-lifecycle question answered *no fix needed* with the
  bounds measured; `DirectionsWalk` de-deprecated; and the diagnostics lesson written down as rule
  10.13 without reintroducing any job. **D8** closes with the provider it was about.
- **v3.6 prepared** — the `instrumented` emulator job removed from CI after failing on every hosted
  run it was ever given; **P2-5**, the Canvas backend's per-frame `Shader` construction (measured at
  180 objects over 60 frames for 3 distinct gradients, now 3), closed by `GradientShaderCache`; and
  **P2-6**, the three unsynchronised engine fields, closed by publishing them as one immutable
  `SolarDay` behind a single `@Volatile`. No golden regenerated, no renderer refactored.
- **v3.5 prepared** — the race in `AwaitOnceTest` that failed the v3.4 build (the test never joined
  the threads it started), and `AI_PROJECT_RULES.md` 10.12 making the release's independence from
  `instrumented` a permanent, checkable rule. Workflow unchanged; no application code changed.
- **v3.4 prepared** — the CI emulator job's boot/install race, and nothing else: both APKs are built
  before the emulator starts, and the script waits for a real install/uninstall transaction to
  succeed before running the tests. Failure-only device diagnostics added. No application code
  changed.
- **v3.3 prepared** — the CI emulator job's SDK provisioning, and nothing else: `api-level` is now
  `'37.0'`, the package identifier that exists, instead of `37`, which names nothing. No application
  code changed.
- **v3.2 prepared** — the last two P1s and three P2s from the v3.0 assessment: the instrumented
  suite runs in CI (**P1-3**, non-gating for now), the shipped GL backend has visual coverage
  through an offscreen EGL pbuffer (**P1-4**), a solar day may cross the device's midnight
  (**P2-3**), the geocoder always finishes (**P2-4**), and the bird/tap leftover is gone from the
  README and from the engine (**P2-7**). Also the permanent rules that publication is the
  maintainer's and delivery is a verified ZIP.
- **v3.1 Stable** — the five items from the v3.0 assessment: DataStore corruption recovery that
  costs only the damaged store (**P0-1**), a Live Weather switch that can always be turned off and
  labels that describe the effective state rather than the stored flag (**P1-1**), depth ordering
  that follows a leaping dolphin's body instead of the lane it left (**P1-2**), an update check that
  distinguishes "up to date" from "could not check" (**P2-1**), and locale-independent coordinates
  (**P2-2**). Nothing else was touched.
- **v3.0 Stable** — the updater fixed and proven against real releases, the lake given lanes and
  depth order, Live Weather's location split into GPS / Network-Cell / Custom with one bounded
  request per refresh instead of a standing subscription, 13 golden-image tests over the scene, and
  the external reference removed from everything operational. **D13 closed**, **D1 closed** (the
  README's provenance statement and the source comments no longer disagree, because the comments
  no longer cite anything).
- **v2.16 Stable** — the Android/build component upgrade, and nothing else. Gradle, AGP, Kotlin
  and the whole AndroidX/Compose/DataStore/Coroutines set on the current stable line;
  `compileSdk 37` with `targetSdk` held at 36; no Kotlin source changed; verified statically
  and on an Android 17 emulator against v2.15 screen by screen. **It fixes nothing** — D13 in
  particular is untouched.
- **D5 closed — the dependency upgrade.** Gradle, AGP, Kotlin and the whole AndroidX set taken
  to the current stable line in one controlled pass, with the build, the 688 unit tests and lint
  re-run after each group. `compileSdk` went to 37 because `androidx.core 1.19` and Compose
  `1.12` declare `minCompileSdk=37`; `targetSdk` stayed at 36 on purpose (**D10**). Nothing was
  taken to an alpha, beta or rc, and no library was moved that had no reason to move.
- **v2.15 Stable** — thunderstorm reviewed end to end (the lightning system existed and was wired;
  the missing piece was gating it on precipitation actually falling), and **D9 closed**: the snow
  path verified on a device against a live Open-Meteo snowfall at Mawson, with the weather-driven
  falling snow and the theme-driven winter presentation confirmed independent. Storm atmosphere
  (darker sky and clouds) deliberately **not** added — it would reverse a recorded visual decision;
  raised as an open question instead.
- **v2.14 Stable** — bottom spacing root-caused to the dialog window's size and closed, immediate
  Live Weather refresh widened to every input it depends on, a location fix now belongs to its
  source, a second weather provider behind a common abstraction, and the forecast-to-scene step
  corrected in both directions: measurements now outrank the weather code, and Live Weather drives
  the cloud layer as completely as it already drove precipitation.
- **v2.13 Stable** — three-action update dialog with in-app install, install-permission deep link, and measurement-first weather mapping.
- **v2.12 Stable** — day/night blend continuity, Sun/Cloud Height corrected and rescaled, day/night people density, two-source bottom inset.
- **v2.11 Stable** — in-app update flow with SHA-256 verification, and the World & scene preview merged into the gallery's preview system.
- **v2.10 Stable** — one central bottom-inset rule for every settings screen, and custom location by city search.
- **v2.9 Stable** — settings UI restructured into five destinations, complete Material 3 colour scheme, Material Symbols, and theme previews drawn from the real sprite library.
- **v2.8 Stable** — shop first floors, small-house harmonisation, a shorter tower with a coarse grid and a real entrance, Christmas firs and scattered lights.
- **v2.7 Stable** — roof-snow and leaf-spawn bugs fixed, flowers toggle, window Christmas lights, balloons removed, building hierarchy and skyscraper grid corrected.
- **v2.6 Stable** — a true outer silhouette outline replacing v2.5's inner rim, which flickered across the walk frames, and a wider small-house facade.
- **v2.5 Stable** — readability rim baked into 39 sprites, world scale +12.5%, Halloween palms, orange moon, two-window small house, the `spring` theme and a calendar covering every day.
- **v2.4 Stable** — the gull, dolphin and carved moon redrawn; the dolphin splash on both crossings; the Halloween theme added with both switches preset.
- **v2.3 Stable** — Halloween and Horror Sky as two independent flags, a stateless dolphin re-entry splash, and the dolphin and bird sprites redrawn.
- **v2.2 Stable** — D-10 closed; 1.49 MB of sprite padding removed with every blit origin compensated in the same change.
- **D-10 — sprite padding, closed.** It was never an asset problem. `SpriteBlitter` puts the
  bitmap's pixel (0,0) on the caller's origin, so a crop is only correct together with a
  compensation in the renderer — and the v76.9 abort that made it look like a conflict
  between the crop rule and the anchor model was a tooling defect, an anchor re-derivation
  guarded on `has_anchor` where it meant `derives_anchor_from_box`. Done in two passes:
  the trailing padding first, which needs no compensation because pixel (0,0) does not
  move, then the leading padding together with all 34 origin changes. Every sprite's ink
  was hashed as (x, y, RGBA) before the crop and reproduced afterwards under exactly the
  translation its origin was compensated by, for all 118. Ten sprites stay uncropped by
  recorded decision — the canvas-anchored sky set, whose shared origin constant would have
  to be split per sprite, and the two palm fronds.
- **v2.1 Stable** — D-7 closed; offline tooling and documentation only, nothing user-visible.
- **D-7 — rasteriser fidelity, closed.** The three failing fidelity tests were not
  a rasteriser matter at all: they still asserted the **pre-V2** sprite library.
  `house_shared_planter` was pinned as a white full-canvas rounded rectangle at
  78x18 radius 6, but the V2 artwork is a `#C98F5A` box occupying only the lower
  part of its viewBox with three foliage circles over it — 113 solid/empty
  conflicts and a max RGB difference of 176, a different picture rather than a
  different antialiasing decision. `road_line` was pinned at 52x8 radius 3.9 and
  ships at 54x9 radius 4.5, so it failed on size before anything was measured.
  The count stayed at three across the redesign, which is why the mislabel
  survived. The assertions were re-derived against `house_large_trim`, which
  really is a full-canvas rounded rectangle in the V2 set, and against the
  sprites that genuinely score under the IoU reporting floor while reproducing
  exactly. `reports/geometry-fit.json` carried the same staleness — it still
  named `house_large_planter` and `house_small_planter`, removed in Phase 3.4 —
  and was regenerated. The residual rasteriser divergence is now measured rather
  than asserted: across all 118 sprites there is **no solid/empty conflict
  anywhere**, so no sprite's shape differs from its source, and no single pixel's
  coverage moves by as much as half (worst case 121/255, one pixel of
  `rainbow_arc`). Both bounds are pinned by `ShippedAgainstSourceTest`. The
  108-sprite re-render that was thought to be the price of closing this was never
  required; it would only have made three unrelated assertions pass.
- **v2.0 Stable** — complete review of every built-in theme; winter and Christmas split into independent flags.
- **v1.1 Stable** — semver release tags, and an update checker that can read them.
- **v1.0 Stable** — first public release.
- **V2 asset redesign** — 118 sprites, every one with an SVG source.
- **GPU renderer** — OpenGL ES 2.0 behind the `SceneCanvas` abstraction, `Canvas` kept as fallback.
- **Asset source pipeline** — SVG sources, a registry, and tooling that renders, validates and compares.
- **Scene proportions, depth and scaling** — `SceneSpace` as the single source of truth.
