# PaperScrape Roadmap

Operational plan only. What shipped and why lives in `RELEASE_HISTORY.md`; how the
code works lives in `ARCHITECTURE.md`; the visual rules live in `DESIGN_NOTES.md`;
the rules that always apply live in `AI_PROJECT_RULES.md`.

**Nothing below is approved. Ask before starting any of it.** The v3.2 batch closed everything
that was.

---

## Current status

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
v2.0 → 4, v2.1 → 5, v2.2 → 6, v2.3 → 7, v2.4 → 8, v2.5 → 9, v2.6 → 10, v2.7 → 11, v2.8 → 12, v2.9 → 13, v2.10 → 14, v2.11 → 15, v2.12 → 16, v2.13 → 17, v2.14 → 18, v2.15 → 19, v2.16 → 20, v3.0 → 21, v3.1 → 22, v3.2 → 23 — 3 is unused because no v1.2 was released,
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
| A | — | **Promote the CI emulator job to a gate** | v3.2 added `instrumented` with `continue-on-error: true` and left it out of `release.needs`, deliberately: a new emulator job has no track record. Claude has never seen it run — executing Actions requires a push, which §10.A forbids — so the first data points are the maintainer's. Once several real runs are green and the duration is known, the promotion is two lines: drop `continue-on-error`, add `instrumented` to `release.needs`. If it proves flaky instead, say so here rather than deleting the job quietly. |
| B | **P2-5** | The Canvas backend allocates `Shader` objects inside the draw path | Against the CPU rules the project holds itself to. |
| C | **P2-6** | Three scene fields shared across threads without synchronisation | |
| D | **P2-8** | `ARCHITECTURE.md`'s validity stamp is twenty releases behind | It is now two releases further behind, and v3.2 added a whole test surface (`GlGolden`, the EGL pbuffer path, `SharedGoldenScenes`) that it does not mention. |
| E | — | **A GL regression below the driver floor is invisible** | Measured in v3.2: two correct GL drivers differ by 0.12% of pixels at `>=16`, and two of the four deliberate regressions moved fewer pixels than that. Not fixable by lowering a threshold. If it ever matters, the answer is a targeted assertion on a region (as `GoldenScene.focus` does for the Canvas suite), not a tighter global limit. |

Deliberately **not** scheduled: any weather-provider change (OpenWeather, WeatherAPI, Tomorrow.io, a
Visual Crossing migration), `targetSdk 37`, and any refactor of `PaperRenderer`,
`SceneObjectRenderer` or `ThemePreviewScene`.

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
| **D10** | `targetSdk` is 36 while `compileSdk` is 37. Android 17's behaviour changes are not opted into. | Deliberate. The Phase 2 dependency upgrade raised `compileSdk` because `core 1.19` and Compose `1.12` require it, and left `targetSdk` alone so the upgrade could not change how the app runs. Raising it is a behaviour change and needs its own device pass — lint's `OldTargetApi` warning is the reminder, not a defect. |
| **D11** | Three lint findings that only appeared once the tooling was current: `ConfigurationScreenWidthHeight` on `SettingsInsets.kt:115`, and three `AutoboxingStateCreation` hints on `SettingsComponents.kt`. | New checks over unchanged code, not regressions. `SettingsInsets` is the file that closed v2.14's dialog-sizing bug, so swapping `Configuration.screenHeightDp` for `LocalWindowInfo.current.containerSize` is a change to the one thing that bug turned on — worth doing deliberately, with a device pass, not as a lint tidy-up. |
| **D12** | Every `OutlinedButton` changed colour in the upgrade. Material3 `1.4.0` moved the default content colour from `primary` to `onSurfaceVariant` and the default border from `outline` to `outlineVariant`, so "Reset this theme's scene to defaults" and the other six outlined buttons now read grey-brown with a pale border instead of orange with a mid border. Verified on an Android 17 emulator by sampling the pixels: the new values are exactly this project's own `onSurfaceVariant` (`0xFF54443A`) and `outlineVariant` (`0xFFD9C7B7`). `TextButton` and filled `Button` are unchanged. | This is Material 3's own current default, and rule 3 says the app follows Material 3 — so it was **left as Material draws it** rather than pinned back, which would mean hard-coding a superseded default into seven call sites. It is nevertheless the one user-visible change the whole upgrade produced, and whether the quieter outlined button reads well is a judgement to make while looking at the app. Pinning it back is one argument: `colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)` plus `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`. |
| **D8** | Visual Crossing is not verified end to end: no account was available, so its parser is tested against fixtures built from the published field list rather than against a captured live response. | Needs a free API key. The missing-key path, the failure path and the request URL are all verified on the device; only a *successful* response is not. Worth closing the next time a key is to hand, not worth blocking on. |
| **D7** | The V2 artwork retired four user-visible colour behaviours (sun colour reaching only the glow, theme star colour reaching nothing, Fall Colors not reaching palm fronds, per-building window lighting). | Approved as consequences of the redesign. Whether each reads well is a judgement to make while looking at the app, and nothing has been reported. |

---

## Completed

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
