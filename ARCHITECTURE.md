# ARCHITECTURE.md

Technical description of PaperScrape as it exists today. This document
describes the **current** implementation, including its known weaknesses.
Planned work and visual design decisions are deliberately **not** in here, and they are
not published: they live in the project's internal working notes. This file is limited to the
implementation as it stands.

**Validity stamp: last read end to end against v3.8** (`versionCode = 29`), by reading the source
and running `test` + `lintDebug` + `assembleDebug` + `assembleRelease` and the instrumented suite.
Every release since has updated the sections its work touched and left the rest, which is why most
of the document is in fact current and none of it is *guaranteed* to be. Treat a section as current
if it names a version at or after the thing you are looking at, and check the source otherwise.

The two-item releases that followed the stamp used to be recorded here as three stacked paragraphs,
each correcting the one above it; `AI_PROJECT_RULES.md` 14.10 forbids that shape, and what they said
is in the sections themselves and in `RELEASE_HISTORY.md`. In short: v3.9 added
`LiveWeatherStatus.REJECTED_API_KEY`; v4.0 raised `targetSdk` to 37, equal to `compileSdk`, after
assessing Android 17's behaviour changes one at a time against this app's own code, and added
`LocalityLabelCache` — display only, and it cannot affect the position Live Weather uses.

The stamp itself said *"v75 … current as of v1.0 Stable"* for twenty-seven releases, which is the
whole of **P2-8**: a validity stamp nobody can trust is worse than none.

---

## 1. Project structure

Single-module Gradle project.

```
PaperScrape/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/paperscrape/livewallpaper/
│       │   ├── engine/          rendering, scene model, themes, effects
│       │   ├── prefs/           DataStore persistence
│       │   ├── location/        optional location: GPS, network/cell, or custom
│       │   ├── weather/         optional live weather
│       │   ├── update/          in-app update check
│       │   └── ui/              Compose settings screen
│       └── res/
│           ├── drawable/        vector launcher icon + wallpaper thumbnail
│           ├── drawable-nodpi/  sprite PNGs
│           ├── mipmap-anydpi-v26/
│           ├── values/          strings, colors, themes
│           └── xml/wallpaper.xml
├── tools/assets/                offline asset source pipeline (not part of the build)
├── .github/workflows/           CI
├── gradle/wrapper/
├── release-notes/               one file per shipped version
├── scripts/                     release keystore helper
└── debug.keystore               deliberately committed (see AI_PROJECT_RULES 10.7)
```

### Size

**No counts are written here.** Every row of this table was a hand-kept number, and by v5.1 every
one of them was false — Kotlin files said 46 against 110, sprite PNGs 111 against 371, unit tests
548 against 1 477, and *instrumentation tests 0* against 175, which is the row that mattered because
this project's frame checks are instrumented. The table had not been touched since the v3.8 stamp
above. `AI_PROJECT_RULES.md` 14.11: where the tree can be asked directly, the command is the only
acceptable form.

```bash
find app/src/main -name '*.kt' | wc -l                              # Kotlin files, main
find app/src/main -name '*.kt' -print0 | xargs -0 cat | wc -l       # Kotlin lines, main
ls app/src/main/res/drawable-nodpi/*.png | wc -l                    # shipped sprites
ls app/src/main/res/drawable/*.xml | wc -l                          # vector drawables
grep -rc '@Test' app/src/test --include='*.kt'       | awk -F: '{n+=$2} END{print n}'   # unit
grep -rc '@Test' app/src/androidTest --include='*.kt' | awk -F: '{n+=$2} END{print n}'  # instrumented
find app/src/main -name '*.kt' -print0 | xargs -0 wc -l | sort -rn | sed -n '2,6p'      # largest files
```

The one thing that is a property rather than a count: **the shipped sprite set contains no
byte-identical pair**, and has not since the V2 asset library replaced the whole set in v76.

---

## 2. Main components

### `engine/`

| File | Responsibility |
|---|---|
| `PaperWallpaperService.kt` | `WallpaperService` + inner `PaperEngine`. Owns the render thread, the `Canvas` fallback loop, surface lifecycle, preference collection, location and weather refresh. Holds the Live Weather loop: a two-minute check tick that only fetches once an hour, unless an input in `LiveWeatherInputs` changed or the location did. |
| `PaperRenderer.kt` | Draws sky, stars, sun/moon, clouds, precipitation, rainbow, mountains, hills, lake and its decorations, birds, falling leaves. Owns scroll/parallax state and the depth mapping constants. |
| `SceneObjectRenderer.kt` | Draws ground-anchored scene objects (houses, buildings, trees, parasols, seasonal decorations), the road, cars and people. |
| `SpriteBlitter.kt` | The single sprite-blitting path, shared by both renderers, plus the `SpriteScale` convention selector and the one definition of `SPRITE_PIXELS_PER_UNIT`. |
| `SceneCanvas.kt` | The drawing interface both renderers target, plus `SceneShape`, the closed polygon that replaced `Path`. |
| `CanvasSceneTarget.kt` | `SceneCanvas` over `android.graphics.Canvas`: the settings preview and the EGL fallback. Owns a `GradientShaderCache`, so its three gradient entry points reuse shaders instead of building one per call. |
| `GlSceneTarget.kt` | `SceneCanvas` over OpenGL ES 2.0: transform stack, tessellation, batching. |
| `GlSpriteProgram.kt` | The one shader program; sprites and flat fills share it. |
| `GlTextureCache.kt` | Drawable resource id → texture handle, UV rectangle and pixel size. Routes each sprite to the atlas or to a texture of its own. |
| `GlTextureAtlas.kt` | The shared atlas texture and its uploads. |
| `AtlasPacker.kt` | Where each entry sits in the atlas, as pure testable arithmetic. A skyline since v4.29; it was `ShelfPacker.kt` and shelf packing until the census measured what the rows were costing. |
| `GlRenderThread.kt` | EGL context and surface lifecycle, the render loop, and the cross-thread event queue. |
| `SceneTransform.kt` | The `save`/`restore`/`translate`/`scale`/`rotate` arithmetic, as pure testable code. |
| `SpriteCache.kt` | Process-lifetime `Bitmap` cache keyed by resource id. |
| `SceneObject.kt` | Scene object data model (`StaticSceneObject`, `CarObject`, `SceneObjectLayout`) and `SceneObjectCatalog`, which generates candidate slots per category. |
| `SceneTheme.kt` | Theme data model and built-in theme catalog. |
| `SceneCustomization.kt` | Per-category visibility/density/colour configuration plus sky, stars, clouds, precipitation, rainbow, mountains, lake, birds config. |
| `LiveWeatherSceneRules.kt` | Which layer's settings win while Live Weather is active — clouds and the lightning flash. Pure, because the defect it prevents is not a wrong value in any one layer but the layers disagreeing: precipitation ignored the theme's own switch under the forecast, clouds did not (rain from an empty sky), and the storm required no rain at all (a flash over a dry scene). Three layers, one rule. |
| `StormAtmosphere.kt` | How much the weather darkens the scene, and what that darkening does to a colour. One pure `strength(...) -> 0..1` feeds sky darkening, cloud darkening and sun attenuation, so the three cannot disagree about how bad the weather is. `dim` pulls a colour toward its own Rec. 601 luminance and then down, which keeps the blend relative to the theme's palette rather than substituting a storm one. Applied *on top of* the day/night colour, so the two are orthogonal and combine. |
| `CloudBand.kt` | Where the cloud band sits and what hangs off it: the clouds, the rain's fall origin, and the lightning's origin. Pure, and separate, because the same arithmetic was written out at three call sites and the lightning's copy had drifted — bolts were born above the band instead of inside it. Deriving all three from one function is what keeps them agreeing. |
| `CustomThemeData.kt` | JSON (de)serialisation of custom themes and overrides. |
| `CustomThemeRegistry.kt` | Synchronous in-memory cache of custom themes, with a `generation()` counter used to detect changes. |
| `RandomSceneGenerator.kt` | Procedural theme/layout generation for the "Random" theme. |
| `SeasonalThemeRules.kt` | Date-based automatic theme selection (includes a Computus implementation for Easter). Reads its dates from `SeasonalCalendar` rather than holding them. |
| `SeasonalCalendar.kt` | The calendar as data: the eight `CalendarWindow`s, their factory spans, and the user's edits to them. Stores **only the difference** from the factory, so an untouched install has no document at all. |
| `SeasonalCalendarCoverage.kt` | Walks the year to find gaps and same-tier overlaps. One enumeration, shared by the settings screen's gate and by the tests. |
| `SunPositionCalculator.kt` | Day phase, sun/moon arc position, moon phase, simplified sunrise/sunset. |
| `FireworkEffect.kt`, `SantaSleighEffect.kt` | Self-contained timed effects. |
| `SceneSpace.kt` | **The one place the world's size is stated.** The horizon, the ground plane's projection, the road's lanes and edges, and every category's real height in metres against the local units its art occupies. Every base scale is derived here, so the ratios between objects cannot be edited one at a time. |
| `SceneTime.kt` | Scene time as a `@JvmInline value class` over `Double`, with every read bounded at the point of use. Replaces a `Float` accumulator that stopped advancing after ~12 days of visible uptime. |
| `SolarDay.kt` | Today's sunrise, sunset and whether they came from a real position, as one immutable value (**P2-6**, v3.6). Published through a single `@Volatile` reference on the engine so the render thread cannot read a sunrise from one location beside a sunset from another — which three separate fields, `@Volatile` or not, allow. |
| `LakeLanes.kt` | Which lane each lake decoration occupies and how deep it sits, so boats cannot share a line and a leaping dolphin sorts by where its body is rather than by the lane it left. Since v4.28 the waves sort in the same pass, keyed by their waterline said in the boat's convention. |
| `WaveTint.kt` | Where a wave's body and foam sit in luma, given the water under them. Pure arithmetic, so `WaveContrastTest` measures the same numbers the renderer draws: the cheaper carry for the direction, the foam always the lighter paper, and the gate that is a floor rather than a target. |
| `PedestrianCarry.kt` | Which walkers have an umbrella up and when that may change -- `CarSelection.offScreen`'s "only out of sight" rule taken over for people, plus the share (**dealt over the street since v5.4**, not rolled per walker), the rain predicate and the canopy palette. |
| `CandidateNoise.kt` | The stable per-candidate pseudo-random values the stateless candidate model is built on: same slot, same value, every frame, with density thinning and colour-variant assignment deliberately drawn from uncorrelated streams. |
| `CloudCoverage.kt` | How many clouds a cover fraction means, shared by the theme's own setting and Live Weather's. |
| `PeopleDensity.kt` | How many pedestrians a density setting means, on the same pattern -- and since v4.22 the day/night crossfade model the car count borrows (`CarSelection.densityAt`): one "a crossfade, not a threshold" rule, two users. |
| `CarSelection.kt` | Which cars a density means (v4.22): an explicit count from 1 to every slot, filled in an order whose every prefix has the largest minimum loop gap, seeded per theme, applied per frame against each runtime's stored rank and only ever off screen. |
| `BusinessHours.kt` | How open the shops and towers are at a scene hour (v4.22): a toggle that defaults to bitwise-off, `open == close` as always-open, wraparound spans, and a boundary fade that is `SunPositionCalculator.smoothEdge`'s own twilight over the opening span. Runs on `DayPhase.hour24` -- the hour that moved the sun -- never a clock of its own. |
| `TreeSpriteLayout.kt` | Where a tree's trunk, crown, snow cap and bare branches sit, stated once for both the wallpaper renderer and the gallery preview (v3.7). The preview builds its objects from the same sprites at the same offsets by hand, and the snow cap's copy had drifted 3 units right and 2 down; both now read from here. |
| `NeighbourhoodTable.kt` | **Generated** (`tools/assets/buildings/build_neighbourhood.py`): what each of the six building families is made of, as a list of slots, each holding the alternative pieces one instance may be dealt. A part is `FIXED` art, a `WALL_MASK`/`GLASS_MASK` weight summed at the blit, a `SNOW` layer, or a call-out (`LAMP`, `OCCUPANTS`) to a behaviour at the piece's own declared coordinates. |
| `SpriteOccluderTable.kt` | **Generated** (`tools/assets/build_occluder_table.py`): where the ink is in every drawing an occlusion box has to speak for — the three palm crowns, the oak's two, the parasol's procedural fan — as a content box in object units plus the drawing's fullest row and fullest column. v5.2: the boxes used to be the sprite *canvases*, hand-typed in two places (the layout pass and `ShopFrontVisibilityTest`), and a palm fan that is 51% ink declared 100% of its rectangle solid. Both sides now read this and build their own rectangle from it; `SpriteOccluderTableFreshnessTest` re-measures it straight from the PNG so it cannot fall behind a redraw. |
| `NeighbourhoodComposer.kt` | Deals one building out of that table — one alternative and one repeat count per slot, from the object's own stable identity — and stacks the pieces bottom-up. Read by **both** things that draw a building, which is what replaced `SkyscraperSpriteLayout` (v5.0): rather than hoisting the offsets two hand copies disagreed about, there is one composer and no copy. `Deal` is owned and reused by its caller, so a scene does not allocate a list per building per frame. |
| `SceneColour.kt` | The one blend the colour rules are built from: `ColorUtils.blendARGB`'s arithmetic without the framework call, so `colorFor` and `windowGlassColor` run on the host and the JVM suite can evaluate them. `SceneColourBlendTest` (instrumented) proves the two identical over a sweep. |
| `SpriteCache.kt` / `SpriteCacheIndex.kt` | The bitmap cache and its bookkeeping. The index is `SpriteCache`'s own `private val` — ids, byte counts and LRU order in `IntArray`s, deliberately free of Android types so the eviction logic is unit-testable, and cleared by the same `clear()` the memory-pressure path calls. |
| `MemoryPressurePolicy.kt` | What an `onTrimMemory` level means for a wallpaper, as a pure decision. Notably `TRIM_MEMORY_UI_HIDDEN` is *not* treated as pressure, though its numeric value sits above `RUNNING_CRITICAL`: for a wallpaper it only means the settings screen closed. |
| `TintFilterCache.kt` / `IntLruSlots.kt` | A bounded, exact-LRU cache of `PorterDuffColorFilter`s keyed by colour, so a tinted blit does not allocate a filter per sprite per frame. Global, and therefore `@Synchronized`; released on `RELEASE_ALL`. |
| `GradientShaderCache.kt` / `IntKeyLruSlots.kt` | The same pattern for gradient `Shader`s (**P2-5**, v3.6), with a multi-component key because a gradient is four or five numbers rather than one. Owned **per `CanvasSceneTarget`** rather than globally, so a draw call takes no monitor. Measured: the Canvas backend built 180 `Shader` objects over 60 frames for 3 distinct gradients, and now builds 3. |

### Other packages

- `icon/SeasonalIcon.kt` — the six launcher icons as an enum, the one place a `CalendarWindow` and
  an icon are tied together, and `SeasonalIconRules.iconForDate`, which is pure and JVM-tested.
- `icon/LauncherIconSwitch.kt` — the `PackageManager` side: reads the six components' enabled state
  and writes only what differs, always with `DONT_KILL_APP`.
- `icon/SeasonalIconController.kt` — when it looks: wallpaper start, `ACTION_DATE_CHANGED` and its
  two siblings through a code-registered receiver, and a settings change that moves the calendar.
  All three arrive on the main thread and the work is binder calls, so it owns one single-thread
  executor: off the wallpaper's main thread, and serialised rather than racing.
- `prefs/WallpaperPrefs.kt` — main DataStore store, exposes `settingsFlow`.
- `prefs/CustomThemeStore.kt` — separate DataStore for custom themes/overrides.
- `location/DeviceLocationKind.kt` — the two device positioning systems, each bound to exactly one
  `LocationManager` provider and one permission. `NETWORK` is cell/Wi-Fi with
  `ACCESS_COARSE_LOCATION` and **never** substitutes GPS; `GPS` is the GNSS receiver with
  `ACCESS_FINE_LOCATION`.
- `location/DeviceLocationProvider.kt` — **one fix, asked for when something needs it.** Not a
  subscription: `currentFix` prefers a cached fix under 15 minutes old (no radio at all), otherwise
  makes one bounded `getCurrentLocation` request (API 30+) or a self-removing single update below
  that, and returns `null` rather than trying a different provider. Until v3.0 this held a
  ten-minute `requestLocationUpdates` subscription for the wallpaper's whole life to feed an hourly
  forecast, and picked its provider by whichever was enabled.
- `location/LocationSource.kt` — which of the four mutually exclusive sources a held fix came from
  (`NONE`, `NETWORK`, `GPS`, `CUSTOM`). `GPS` and `NETWORK` are separate values so switching between
  them invalidates the held fix, exactly as switching to or from `CUSTOM` does.
  The engine invalidates the fix when the source changes; without it a custom location survived a
  switch to phone location and Live Weather kept querying the old coordinates.
- `location/LocationLabelResolver.kt` — reverse geocoding through the platform `Geocoder`, which
  needs no network where a device supports it. Its `format` is separated out and pure: the label is
  `"<place>, <country>"`, place being the narrowest field the geocoder filled
  (`locality` → `subAdminArea` → `adminArea`), names untransformed in the device's locale, and a
  city-state's duplicate collapsed to one word.
- `location/LocalityLabelCache.kt` — **v4.0.** *When* a fix is worth geocoding, kept apart from
  *how* it is geocoded so the policy is JVM-testable. A 1 km threshold (above Network-mode jitter,
  and equal to the row's own two-decimal display so the cache cannot hide a change the row would
  show), successes that never expire, failures retried after 60 s and never stored as labels, and a
  request counter so a slow lookup for the previous position cannot overwrite the current one.
  Nothing here polls; it only ever suppresses work.
- `location/CityGeocoder.kt` — forward search by city name, through Open-Meteo's keyless geocoding
  API (the same provider Live Weather uses, and the same `HttpURLConnection` style). The response
  parser and the small in-memory search cache are separated from the network call so both are
  unit-testable.
- `weather/` — the Live Weather pipeline, one step per file:
  `provider → normalised WeatherObservation → WeatherRepository → cache/scheduler → scene`.
  - `WeatherProvider.kt` — the interface every service implements, plus `WeatherProviderId`
    (stored by string id, not ordinal), `WeatherFetchResult` and `WeatherFailure`. A provider owns
    its endpoint, its query and its response shape and nothing else: not the schedule, not the
    cache, not the preferences, not the renderer.
  - `WeatherObservation.kt` — the normalised model both providers produce: temperature, cloud
    cover, precipitation, rain, showers, snowfall, a normalised `WeatherCondition`, a timestamp,
    and the provider it came from. Every field is nullable because "not reported" and "reported
    zero" are different facts the mapping depends on.
  - `OpenMeteoProvider.kt` — **the default, and the reason Live Weather works out of the box.**
    Keyless free tier; a key only upgrades the endpoint, and neither state is a failure. Splits
    precipitation into rain/showers/snowfall, which is why the model has room for it. Its free
    service is licensed CC-BY 4.0 for **non-commercial** use, which is the one thing the second
    provider exists to give an alternative to.
  - `WeatherApiComProvider.kt` — WeatherAPI.com's `/v1/current.json`, the second provider since
    v3.7, replacing Visual Crossing. **Requires a key** (no anonymous tier), which is why
    `WeatherFetchResult.MissingApiKey` exists: without one no request is made at all. No key for it
    is compiled into the app; the user's own lives in their DataStore. Chosen because its condition
    vocabulary is published as machine-readable JSON — committed as a test fixture, with every one
    of its 60 codes walked by a test — where the provider it replaced had icon slugs mapped from
    prose that nothing could check (deferred item **D8**). Reports one `precip_mm` and no snow
    depth in the realtime object, so `showersMm` and `snowfallCm` stay null rather than zero.
  - `OpenWeatherProvider.kt` — OpenWeather's **Current Weather Data** API (`/data/2.5/weather`), the
    third provider since v3.8. **Requires a key**, and none is compiled in. Deliberately *not* One
    Call: that product requires a payment card on file even for its free allowance, and everything
    it adds over this endpoint is data the scene has no use for. Its condition ids are
    **structured** — the hundreds digit is the category — so the mapping is a `when` over `id / 100`
    plus four named exceptions (511 freezing rain inside the Rain group, 611-616 sleet inside Snow,
    and the two shower ranges), and an id the vendor adds later still lands in the right group.
    One unit trap: `rain.1h` and `snow.1h` are documented as always millimetres per hour whatever
    `units` says, while the model's `snowfallCm` is centimetres, so the snow figure is divided by
    ten.
  - **Three providers, one default, no fallback.** The renderer never learns which one answered.
  - **Provider selection is a string id, and an unknown one reads as the default.** An install that
    had chosen Visual Crossing therefore lands on Open-Meteo after upgrading, with no migration
    code and no broken state — a keyed provider whose key is gone would be worse than the keyless
    one. There is deliberately **no automatic fallback between providers** at fetch time: the
    selection stands and the failure is reported, because silently answering from a different
    service makes "which provider am I using" unanswerable.
  - `WeatherSnapshotMapper.kt` — observation → `LiveWeatherSnapshot`, the renderer's vocabulary.
    **A measurement, where one exists, is the answer.** The summary code only chooses the *kind*
    when a positive total has no breakdown to explain it, and only decides whether anything falls
    at all when the provider reported no measurements — otherwise four readings of zero would keep
    being outvoted by a code, which is what rained on a dry Florence afternoon in v2.13.
    `isThunderstorm` carries the same requirement: it means "the scene should storm", so the
    lightning flash cannot fire over a scene with nothing falling in it.
  - `WeatherRepository.kt` — dispatches to the selected provider. **No silent fallback between
    providers:** a failure is reported as one and the selection stands.
  - `LiveWeatherStatus.kt` — what Live Weather is actually doing (`OFF`, `OK`, `NO_LOCATION`,
    `MISSING_API_KEY`, `REJECTED_API_KEY`, `FAILED`, `STALE`), written by the service and read by
    the settings screen. `REJECTED_API_KEY` is v3.9's: an HTTP 401/403 is a provider that answered
    and refused the credential, which is not the same fact as one that could not be reached, and
    folding the two together is what made a not-yet-active OpenWeather key report itself as a
    network failure. It sits before the "is a snapshot still in effect" question, like
    `MISSING_API_KEY`, because an old observation on screen does not change what the user must do.
  - `LiveWeatherInputs.kt` — which settings force an immediate fetch rather than waiting for the
    hourly refresh. Pure, so the list cannot quietly fall behind the settings again.
  - `WeatherHttp.kt` — the one `HttpURLConnection` JSON GET both providers share, and the pure
    status → `WeatherFailure` mapping.
- `update/UpdateChecker.kt`, `update/UpdatePrefs.kt` — GitHub Releases API.
- `update/ReleaseAssets.kt` — which attachment is the APK and which is its checksum (exact names),
  how a `sha256sum` file is read, and whether a downloaded package may be installed. All pure, all
  unit-tested: these are the parts that fail silently.
- `update/ApkDownloader.kt` — streams the APK to `cache/updates` while hashing it in the same pass,
  and `ApkInstaller`, which hands the verified file to Android through a `FileProvider` URI scoped
  to that one directory. No silent-install path exists.
  `downloadAndVerifyTo` is the same download with no `Context` in it, which is what makes the
  failure modes JVM-testable against a local HTTP server (`ApkDownloadPathTest`). It reports a
  `DownloadPhase` -- `Downloading(percent)` then `Verifying` -- because the digest comparison and
  the package parse after the last byte are a visible pause the UI used to call "downloading".
  **D13, fixed in v3.0:** the hang was never here. `AdvancedScreen`'s `LaunchedEffect` was keyed on
  the state its own body cleared, so Compose cancelled the download ~30 ms after it started and the
  UI was left on `Downloading` with the check row disabled. The effect is now keyed on the tag with
  an already-started guard, the transfer runs in the settings screen's scope so it outlives the
  effect, and `runDownload` restores an actionable state on cancellation. Verified end to end
  against the real v2.15 → v2.16 releases.
- `ui/` — the settings UI, one file per destination since v2.9 (it was a single 2,414-line
  `SettingsScreen.kt`):
  - `SettingsActivity.kt` — the activity, edge-to-edge, wraps everything in `PaperScrapeTheme`.
  - `SettingsScreen.kt` — the home screen and the routing between the five destinations.
  - `WeatherTimeScreen.kt`, `SeasonsScreen.kt`, `WorldSceneScreen.kt`, `AdvancedScreen.kt`,
    `ThemeGalleryScreen.kt` — one destination each, all drill-downs from home.
  - `SettingsComponents.kt` — the shared Material 3 vocabulary (section header, grouped
    container, row, switch row, navigation row, segmented choice, banner, caption, screen
    shells, slider, colour picker).
  - `SettingsUiModel.kt` — the pure mapping between the two segmented choices
    (location source, seasonal palette) and the preference flags that back them. No Compose
    and no Android imports, so both directions are unit-tested.
  - `SceneCategorySections.kt` — the per-category and per-mountain-layer editors.
  - `ThemePreview.kt` — draws a theme's preview scene; see `engine/ThemePreviewScene.kt`.
  - `SettingsInsets.kt` — how a settings destination is **sized**. Every destination is a
    full-screen `Dialog`, and with `usePlatformDefaultWidth = false` Compose measures the dialog's
    content against the *display* while the window manager sizes the window to the space between
    the system bars. On a Pixel 9 that is 2423 px of content in a 2219 px window
    (`frame=[0,142][1079,2361]`, from `dumpsys window`), so the last 204 px of every screen was
    laid out outside the window and clipped — which is what the bottom-spacing bug always was, and
    why two rounds of padding could not fix it. The content is now given the height of the area
    its window occupies: the display less the insets the **activity** measures, passed down
    through `LocalSettingsTopInset`/`LocalSettingsBottomInset`. The scaffold inside reserves the
    dialog's *own* insets — zero exactly when the window already fits the bars — so a device whose
    dialog window is full-bleed instead is handled by the same code. The trailing spacer is a
    24 dp constant again; the shells apply all of it, screens never set their own.
  - `theme/PaperScrapeTheme.kt` — the complete Material 3 colour scheme, light and dark.

---

## 3. Rendering pipeline

**OpenGL ES 2.0, on a per-engine render thread**, with the 2D `Canvas` path retained
as a fallback and for the settings preview. There is no external graphics library.

### The two backends

The scene renderers do not know which backend they are drawing into. They draw
onto `SceneCanvas`, an interface exposing exactly the operation set they already
used — a transform stack, rects, lines, circles, ovals, stroked arcs, filled
sectors, closed shapes, three explicit gradient forms, and sprite blits. Two
classes implement it:

| Implementation | Used by |
|---|---|
| `GlSceneTarget` | The wallpaper, normally. Turns each call into GPU geometry. |
| `CanvasSceneTarget` | The settings screen's live preview, which draws onto a Compose `Canvas` where there is no GL context; and the wallpaper itself when EGL initialisation fails. |

The interface is deliberately no wider than what the renderers already did. An
interface admitting arbitrary `Path`s, clips or `Xfermode`s would be one the GPU
backend could not honour, and a call site could then compile while producing a
different picture on each backend.

`Paint` is passed through rather than decomposed into arguments: reading `color`,
`alpha`, `style`, `strokeWidth` and `strokeCap` allocates nothing, and it left the
renderers' existing paint bookkeeping untouched. Paint *shaders* are the exception
— they cannot be read back — so the three gradient effects carry their stops as
arguments instead (`drawVerticalGradientRect`, `drawVerticalGradientShape`,
`drawRadialGlow`).

```
Android WallpaperService
        │
        └─ PaperEngine (inner class)
             │
             ├─ GlRenderThread  ── owns the EGL context and the loop
             │     │  target interval 33 ms (~30 fps), compensated by frame cost
             │     │  eglMakeCurrent → beginFrame → draw → endFrame → eglSwapBuffers
             │     │
             │     └─ renderScene(GlSceneTarget, deltaSeconds)
             │
             └─ Handler on the main Looper  ── fallback only, if EGL fails
                   lockCanvas → renderScene(CanvasSceneTarget, …) → unlockCanvasAndPost
```

Both loops call the same `renderScene`, so scene time advances identically on
either path and the two cannot drift apart in how they treat a late or a first
frame.

```
             renderScene(target, deltaSeconds)
             │     ├─ SunPositionCalculator.compute(hour, sunrise, sunset,
             │     │                                   moonPhase) → DayPhase
             │     └─ PaperRenderer.draw(target, dayPhase, elapsedSeconds, deltaSeconds)
             │           ├─ syncObjectRendererWithTheme()
             │           ├─ drawSky            (vertical gradient)
             │           ├─ drawStars          (cached star list, 2-3 tile copies)
             │           ├─ drawCelestialBody  (radial glow + sprite blit,
             │           │                      bounded parallax offset)
             │           ├─ drawClouds         (sprite blit)
             │           ├─ drawMountains      (SceneShape)
             │           ├─ drawLake + decorations (sprite blit)
             │           ├─ drawHillLayers     (cached SceneShape + translate)
             │           │     └─ SceneObjectRenderer.draw(canvas, GroundGeometry, ...)
             │           │           ├─ static objects, 3 tile copies each
             │           │           ├─ drawRoad
             │           │           ├─ cars
             │           │           └─ people
             │           ├─ drawPrecipitation / drawFallingLeaves / drawRainbow
             │           ├─ drawBirds
             │           └─ FireworkEffect / SantaSleighEffect
```

### The GPU backend

`GlSceneTarget` turns `SceneCanvas` calls into triangles. Five properties of it are
load-bearing.

**The projection is pixels, not world units.** `Matrix.orthoM(0, width, height, 0)`
puts the origin at the top-left with Y increasing downwards — the space `Canvas`
works in. Every coordinate, sprite origin, depth constant and historical divisor in
the scene therefore keeps its existing value *and its existing meaning*. A
normalised world space would have required rescaling all of them, and a sprite
whose origin is only correct together with its scale convention is precisely how
defect D-1 happened.

**One shader program, not two.** A flat fill is a textured quad sampling a 1×1
opaque white pixel. That collapses what would otherwise be two programs and two
vertex streams into one, so a batch is flushed only when the *texture* changes —
never because a solid shape sat between two sprites.

**Sprites are packed into a shared atlas, and the white pixel is packed into it
first.** With both in one texture, an entire scene object — its sprite parts and
its flat details alike — accumulates into a single batch, and so do consecutive
objects. Packing is what removes the batch breaks rather than reordering around
them, which matters because draw order *is* depth order here and cannot be
changed.

**A sprite is reduced, then cropped, then packed — and the order is the whole of it.** Since v4.30
`GlTextureCache` uploads only the texels that carry ink: the transparent border is cut off *after*
the halvings and before the atlas sees it, and the quad is built from the content rectangle rather
than the canvas so nothing moves on screen. It is what makes a region mask nearly free, and it
applies to every sprite — the non-person half of the set gives back 989 208 B of texels on its own.

Cropping the **PNG** instead would be cheaper still, because it would cut the decoded bytes too, and
it is not available: `SpriteDetailLevel.reduced` truncates, so a 117-wide canvas reduced twice is 29
texels of 4.0345 authored pixels each while a 96-wide crop of it is 24 texels of 4.0000 — the two
layers of one figure start together and drift apart across the sprite. Measured at dE 5.6 at the
device's own level and 14.4 one level up. Cropping *after* the reduction has no step to match,
because the crop's texels **are** the canvas's texels; the only thing it changes is what the
bilinear tap reads just outside the ink, and there it reads the transparent texel
`GlTextureAtlas.PADDING` already puts between two entries.

`GlTextureCache` decides placement per sprite: into the atlas when it fits, into a
texture of its own when it does not. Callers get a handle and a UV rectangle either
way, so a standalone texture is just the `0..1` case. Large sprites are excluded on
purpose — a 1024-square entry would be a quarter of the whole atlas and would push
out the small sprites that actually repeat per frame, while itself costing only one
batch break because it is drawn once. **That gate has never rejected a shipped
sprite**: the largest dimension in the set is `cloud_body`'s, and v4.29 measured the
dimension rejection firing zero times over a twelve-theme walk. It is kept as a
guard against a future sprite, not as a description of this one; the comment that
used to justify it with "the sleigh alone is 1563×434" was describing a canvas the
v4.19 crop retired.

`AtlasPacker` holds the placement arithmetic, separately and without GL, for the
same reason `SceneTransform` is separate: a packing bug is silent. Two entries given
overlapping rectangles do not throw — one sprite renders with another's pixels inside
it, in whichever scene happens to draw that pair.

**It packs to a skyline, and until v4.29 it packed to shelves.** A shelf packer keeps
one row open at a time, so it loses the tail of every row it closes and the slack
above every entry shorter than the tallest one beside it, and it never goes back for
either. Measured across the twelve themes at their own defaults, that cost **99 % of
the atlas's rows to hold 42 % of its area**, saturated on the fifth theme and spilled
sprites into standalone textures — a batch break per frame each, which is what the
atlas exists to prevent. The skyline is online, needs no sorting and no deferred
upload, and is quadratic in skyline segments in a call that already allocates a
bitmap and uploads it. Paired over the same 24-scene walk at full density, `GL mtrack` fell from
**45 449 KiB to 42 629 KiB** and the spill to standalone textures went to zero in every theme.
`AtlasPackerTest` replays a recorded insertion sequence so it cannot regress quietly.

Each entry carries a one-pixel transparent border so a bilinear sample near an edge
finds transparency rather than the neighbouring sprite. The border is uploaded, not
assumed: a freshly allocated texture's contents are undefined.

**Alpha is premultiplied throughout.** `BitmapFactory` decodes into premultiplied
`ARGB_8888` and `GLUtils.texImage2D` uploads those bytes unchanged, so the fragment
shader works in premultiplied space and the blend function is
`GL_ONE, GL_ONE_MINUS_SRC_ALPHA` rather than the more familiar `GL_SRC_ALPHA` pair.
The two halves of that pairing must move together; mixing the conventions is the
classic cause of dark fringes on every soft sprite edge.

**Tinting is the same operation as on `Canvas`.** The fragment shader computes
`vec4(tex.rgb * v_Color.rgb, tex.a) * v_Color.a`, which is what
`PorterDuffColorFilter(tint, MULTIPLY)` followed by `paint.alpha` produces. Baked-in
shading survives the tint here for the same reason it does there, and white remains
the identity tint. `TintFilterCache` is consequently used only by the `Canvas`
backend now — on the GPU the tint is four floats in a vertex.

**Transforms are applied on the CPU as vertices are emitted**, by `SceneTransform`,
rather than as a model-matrix uniform. A uniform would end the batch at every
`save()`, and the scene changes transform far more often than it changes texture.

**Sprite pixels are pulled, not pushed.** `SceneCanvas.drawSprite` takes a
`SpriteSource` rather than a decoded `Bitmap`, because the two backends need the
pixels at wildly different rates: the `Canvas` backend needs them for every blit,
the GPU backend once per sprite for the life of the context. `GlTextureCache`
records each sprite's pixel dimensions at upload time, so a steady-state blit
resolves its size from the registry and never touches `SpriteCache` — which was
otherwise a synchronised lookup with an LRU touch, once per sprite per frame, to
recover a width and a height that had not changed since the first one.

Once a sprite is on the GPU the CPU copy is a duplicate, so the GPU backend calls
`SpriteSource.onSpriteUploaded`, which releases it from `SpriteCache`. Re-decoding
is always available — the same property that makes memory-pressure eviction safe —
so being wrong costs one decode. The `Canvas` backend never reports an upload,
because it holds no durable copy to justify releasing one.

**A sprite blit can be summed instead of laid over (v4.30).** `SceneCanvas.drawSprite` carries an
`additive` flag, and it exists for one thing: a person is drawn as fixed art plus one weight mask
per colourable region, and the masks have to **add**. Two source-over layers split the pixel's
coverage between them, `a + b(1-a)` is not linear, and `SpriteDetailLevel` halves each layer
separately before upload — so they stop recomposing and the figure grows a halo at every edge,
measured at up to 63 levels of coverage out of 255. A sum is linear: halving and compositing
commute.

Both backends express it without a second blend state, and neither needs the shader to know what a
region is:

- **GL** already blends `GL_ONE, GL_ONE_MINUS_SRC_ALPHA`, so a contribution with zero outgoing alpha
  is simply added. The flag travels in the **sign of the vertex alpha** — `step` zeroes the output
  alpha, `abs` puts the scale back — which is two instructions and, crucially, **no state change**:
  the batch survives, the vertex format is unchanged, and a figure in five layers is still one
  `glDrawArrays`. `glBlendFunc(GL_ONE, GL_ONE)` would have been the obvious way and would have
  ended the batch at every layer.
- **Canvas** uses `PorterDuff.Mode.ADD`, which sums alpha as well as colour. That would be wrong
  over a transparent destination and this scene has none: the sky is painted opaque under
  everything, so the destination alpha is already 1 and the sum saturates where it stands.

Because the colour is resolved by the *blend* rather than by the shader, the 30 Canvas goldens go on
seeing the people exactly as the GPU draws them. Resolving it in the shader would have put thirty of
the thirty-three checks on a backend they cannot run.

**Fully transparent draws are skipped.** Under premultiplied blending a zero-alpha
primitive contributes exactly nothing, and the scene fades a lot of things through
zero: precipitation, leaves, star twinkle, the sleigh's edge fade.

Curves — circles, ovals, stroked arcs, filled sectors — are tessellated at a segment
count derived from their radius *in device pixels*, so a shape drawn inside a
`scale(1/3)` sprite transform is not tessellated as though it were three times
larger.

Gradients are vertex colours. A two-stop ramp is linear, and so is interpolation
across a triangle, so the sky quad and the sun's glow fan reproduce their gradients
rather than approximating them. The hill highlight needed one extra step: it is
filled as vertical columns **split at the gradient's lower stop**, because a
triangle fan whose apex sat on the base line would carry the highlight down the
whole hill instead of letting it stop, turning a highlight on the top third into a
wash over all of it.

### Frame pacing and threading

Each engine owns a `GlRenderThread`. The loop targets 33 ms (~30 fps) and subtracts
the frame's own measured cost before sleeping, so the schedule stays near a steady
cadence instead of accumulating drift. All animation is driven by measured
`deltaSeconds`, so positions remain time-correct even when frames are late.

It deliberately does **not** free-run at the display's refresh rate. `eglSwapBuffers`
blocks on vsync, so an unpaced loop would render at 60, 90 or 120 Hz and do two to
four times the work for motion this slow.

**Counting the threads called `PaperScrapeGlThread` will give you two, and one of
them is not ours.** The process holds exactly one Java thread of that name — the loop
above — while `/proc/<pid>/task` shows two. The second is the GPU driver's own worker,
created the first time anything in the process touches EGL: profiled on the BV6600 it
is 75% kernel, 11% `libsrv_um.so` (PowerVR's user-mode driver), 3% `gralloc`, and
contains no `libart` frame at all. It wears our name because Linux gives a new thread
its creator's `comm` and the driver never renames it — in a process where the settings
UI initialises EGL first, the same thread appears as `RenderThread`. It costs about
4.5% of a core while the wallpaper draws and **does not exist** while it is hidden. It
is not a leak, there is nothing to close, and a CPU figure for the render path should
be taken for the process rather than by thread name. Measured in v4.25 on the BV6600 in 20 s
windows: ours **52.40%** of a core, the driver's **4.45%**, and with the wallpaper hidden the
driver thread is absent and ours reads **0.55%**.

**Scene state is owned by the render thread.** A GL context belongs to one thread,
so drawing had to leave the main looper — which means preferences, theme changes,
weather snapshots and home-screen offsets now arrive from a different thread than
the one that reads them. The answer is `PaperEngine.onRenderThread { }`, which
queues the update as a runnable executed between two frames, rather than a lock
around the renderer: a lock would put every settings write in contention with the
frame loop. On the `Canvas` fallback the main looper owns the scene and the same
helper runs the update inline.

Three process-wide objects genuinely became multi-threaded as a result, because a
process can host two engines (the picker's preview and the live wallpaper) and
therefore two render threads. `SpriteCache`, `TintFilterCache` and
`SunPositionCalculator.currentHour24()` are now synchronised. `SpriteCache`'s lack of
a lock had been correct and documented while the only caller was a main-looper draw
loop; that premise is gone, and the lock was taken with the change that removed it
rather than after. The cost is an uncontended monitor on a cache hit — the expensive
path is the decode, which happens once per sprite per process.

### EGL lifecycle

```
onSurfaceCreated   → thread starts → eglGetDisplay / eglInitialize / eglChooseConfig
                     → eglCreateContext (ES 2) → eglCreateWindowSurface → eglMakeCurrent
onSurfaceChanged   → glViewport + orthoM, then PaperRenderer.onSizeChanged
onVisibilityChanged→ the loop parks or resumes; context and textures are kept
onSurfaceDestroyed → the window surface is released, the context is kept
onDestroy          → full teardown; the thread exits on its own
EGL_CONTEXT_LOST   → every GL handle is forgotten *without* a GL call, then rebuilt
```

The config is chosen with 4× MSAA first and the same config without it as a
fallback. The scene draws circles, arcs and thin strokes that `Canvas` antialiases
analytically and GL does not, so MSAA is what keeps those edges comparable; a device
that cannot supply it still gets a wallpaper.

Any EGL failure reports once and parks the thread, and the engine then switches to
the `Canvas` loop for the rest of its life. The scene is untouched by that switch:
the same renderer keeps drawing, through the other backend.

Textures are derived data — every sprite can be decoded again from resources — so
memory pressure drops them too, costing a re-upload and never a missing sprite.
Because a texture can only be deleted by the thread whose context owns it,
`onTrimMemory` reaches each engine's render thread as a queued event rather than
acting directly. The white pixel goes with the rest and is re-packed first, both
because flat geometry cannot be drawn without it and because being first is what
keeps it inside the atlas.

### Sprite memory

| | |
|---|---|
| Whole sprite set, decoded | ~16.4 MB (4.3 Mpixels ARGB_8888) |
| Atlas texture | 2048² RGBA = 16 MB, allocated on first sprite, typically a fraction used |
| CPU bitmaps retained by the wallpaper | none, once uploaded |

The atlas is a rearrangement of the sprite budget rather than an addition to it: its
upper bound is roughly what the same sprites would cost as individual textures. What
changed on the heap side is a genuine reduction — up to ~17 MB of decoded bitmaps
released — and heap is what made this process a preferred low-memory-killer victim
in the first place.

### Allocation on the frame path

The rule is that nothing in a draw path allocates, and it is enforced by reading
bytecode rather than by inspection: `javap -c` on the compiled renderer classes,
looking for `new`, `newarray`/`anewarray`, `valueOf` boxing and iterator
allocation inside the per-frame methods. Two of the allocations that mattered
most were invisible in the source — `Integer.valueOf` inside a map lookup, and a
`Pair<Float, Float>` return type — which is why the check is a bytecode check.

Three patterns account for nearly all of what has been removed:

- **Constant data built inside a draw function.** `intArrayOf`/`floatArrayOf`
  literals and `arrayOf(a to b, …)` tables read as declarations but are
  constructed on every call. They belong in a field or the companion object.
- **Tuples as return values.** A `Pair<Float, Float>` boxes both floats. Two
  fields and a boolean say the same thing for free.
- **Platform conveniences.** `Calendar.getInstance()` and `TimeZone.getDefault()`
  both allocate, the latter returning a defensive clone; a value that changes once
  a minute does not need either on a 30 Hz path.

Related but separate: `TintFilterCache` and `SpriteCacheIndex` exist for the same
reason at a different scale — see the sprite blitting and asset sections.

### Sprite blitting

Every sprite goes through `SpriteBlitter`, which exposes exactly two entry
points — `draw` (baked-in colours) and `drawTinted` — over one private `blit`.
The tint colour and the alpha are passed explicitly on every blit rather than left
as paint state, so no blit inherits either from whatever was drawn before it. How
they are applied is the backend's business: `CanvasSceneTarget` builds a
`PorterDuffColorFilter`, `GlSceneTarget` puts the same numbers in the vertex colour.
`draw` is `drawTinted` with white, the `MULTIPLY` identity.

Two scale conventions still coexist, because a sprite's convention is a
property of the asset and no asset declares its own metadata yet (Group 3).
Until then the caller names it, as a `SpriteScale` argument:

| `SpriteScale` | Meaning | Used by |
|---|---|---|
| `SCENE_UNITS` | Sprite is authored at `SPRITE_PIXELS_PER_UNIT = 3` times its on-screen size; the blitter applies `canvas.scale(1/3)` and pre-multiplies the origin. | Every scene object, plus clouds and the lake decorations. |
| `CANVAS_PIXELS` | Sprite is authored at literal on-screen pixel size and blitted straight through, at whatever scale the caller's own `canvas.scale()` established. | Sun disc, sunburst, moon phases, birds, sleigh. |

Passing the wrong one is a silent 3× size error, which is why it is spelled out
at the call site rather than implied by a function name. It is silent in the other
direction too: because nothing in a PNG records its convention, **replacing an
asset can change what an unchanged call site means**. That is exactly what
happened to `star_sparkle.png` between v72 and v73 and went unnoticed until v73.7
(defect D-1). The sky sprites therefore no longer carry their origin and scale as
literals — `PaperRenderer` declares both per sprite in named constants that
`SkySpriteAnchoringTest` checks against the PNG headers on disk, so the three
numbers that are only correct together are pinned from both ends.

`SceneObjectRenderer` draws in one convention only, so it binds `SCENE_UNITS`
once in its thin `drawSprite`/`drawTintedSprite`/`drawSpriteFaded` wrappers instead of repeating
it at every call site. `PaperRenderer` is the only class that mixes conventions, so it has no
wrappers at all: each of its calls names its own scale. This paragraph carried "60" and "12" from
a release in which the blitter's own API was different; count them rather than read them here:

```bash
grep -c 'drawSprite(\|drawTintedSprite(\|drawSpriteFaded(' app/src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt
grep -c 'blit(' app/src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt
```

Tinting uses `PorterDuffColorFilter` in `MULTIPLY` mode (not `SRC_IN`), so
baked-in shading in a sprite survives the runtime tint. Trade-off: the rendered
colour is a few percent darker than the exact configured hex wherever shading
sits.

Filters come from `TintFilterCache`, not from a fresh allocation per blit. The
cache is bounded at 64 entries with exact LRU eviction via `IntLruSlots`, an
allocation-free `Int`-keyed slot allocator. The bound matters because tint
colours are day/night blends rather than fixed palette values, so new colours
can keep arriving indefinitely; the hit rate is nonetheless high because
`dayBlend` is pinned at exactly `0f` or `1f` for most of the cycle and its
quantised 8-bit result changes only every few hundred frames even during the
dawn and dusk ramps.

Static objects are culled by `isHorizontallyVisible(x, halfWidth, screenWidth)`
against the real viewport width and the object's own scaled extent
(`MAX_OBJECT_HALF_WIDTH_UNITS`, measured from the widest sprite blit and
procedural primitive, with headroom).

### Tile enumeration

The scene tiles horizontally with period `tileWidth = screenWidth * 2`, so every
static object exists at `x + k * tileWidth` for integer `k` and each copy that
intersects the viewport must be drawn or the wrap seam shows a gap.

`draw()` computes the object's `effectiveScale`, `halfWidth` and `groundY` once —
they are properties of the object, not of the copy — and then walks the half-open
range `firstVisibleTileOffset until tileOffsetLimit`:

| Bound | Value |
|---|---|
| `firstVisibleTileOffset(x, halfWidth, tileWidth)` | `floor((-halfWidth - x) / tileWidth)` |
| `tileOffsetLimit(x, halfWidth, tileWidth, screenWidth)` | `floor((screenWidth + halfWidth - x) / tileWidth) + 1` |

`floor` rather than `ceil` on the first bound is the safety property: the exact
first visible index is the `ceil`, so taking the `floor` can start one tile early
but never one tile late. An early start costs one rejected iteration; a late start
would drop a copy and pop at a screen edge, and float rounding at an exact tile
boundary can move the quotient either way. The limit is inclusive at the right
edge to match `isHorizontallyVisible`'s own `<=`; if the two disagreed, a copy the
predicate calls visible would never be offered to it.

Both bounds are **pure companion functions rather than a loop condition inside
`draw()`**, which needs a `Canvas`. Logic written as a condition there can only be
tested by reimplementing it in the test — and the first mutation run for this code
survived every mutation for exactly that reason. `isHorizontallyVisible` still
decides each copy; the range only bounds which copies are offered to it.

Each copy's x is recomputed as `x + tileIndex * tileWidth` rather than accumulated,
so the values are bit-identical to the fixed `x`, `x - tileWidth`,
`x + tileWidth` this replaced.

In practice the range holds 1.77 tiles on average and never more than 3, of which
0.77 are painted; the fixed loop always prepared 3. `draw()` guards
`tileWidth <= 0f` and falls back to a single copy. That guard is a correctness
condition rather than padding: the bounds divide by `tileWidth`, and zero is a
reachable value — `tileWidth` is `screenWidth * 2f` and `screenWidth` comes from
`holder.surfaceFrame`, which is 0 until the surface has been sized. `PaperRenderer`'s
placeholder `GroundGeometry` carries `tileWidth = 0f` for the same reason, so the
placeholder and an unsized surface are one state under one condition; a positive
but meaningless period such as `1f` would pass the guard and produce a range of
roughly `screenWidth + 2 * halfWidth` entries per object.

### The sky layer: one tiled pattern and one singleton

When `scrollBackground` is on, the sky drifts sideways with the rest of the
scene, and it holds two things whose tiling natures are opposite. Treating them
as one — a single wrapped translate applied to both, with one copy drawn — is
what made the sun, moon and stars leave the screen periodically. The layer now
has two paths.

**The star field is a tiled pattern.** `regenerateStars` lays stars out across
`[0, screenWidth)`, so its period is exactly one screen width — not the
`screenWidth * 2` the ground layers use. It is drawn over the half-open range
`firstStarTileOffset until starTileOffsetLimit`, the same shape as the static
objects above:

| Bound | Value |
|---|---|
| `firstStarTileOffset(shift, tileWidth, left, right)` | `floor((-shift - tileWidth - right) / tileWidth) + 1` |
| `starTileOffsetLimit(shift, tileWidth, viewport, left, right)` | `ceil((viewport + left - shift) / tileWidth)` |

`left` and `right` are how far a star sprite reaches either side of the star's
own x. They are equal, because the sprite is centred on the star; they were
asymmetric while `star_sparkle.png` was blitted with the wrong scale convention,
and a test pins them so a change to either the asset or the convention has to come
back through them. The range is derived from what is actually drawn rather than
from what was intended: since v4.23 both are literally
`STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR × MAX_STAR_RADIUS_PX`, which is
10.5 px, rather than the star's own radius. **The two stopped being the same number
when v4.23 halved the divisor**: the sprite reached `0.9375 × radius` before and
reaches `1.875 × radius` now, so reserving the radius went from a deliberate
over-reservation to a two-fold under-reservation, which drops a tile copy at a seam.
In practice it holds 2 copies, and 3 only in the ~2 % of the cycle where a sprite
extent crosses a seam.
Neighbouring copies never draw the same star twice in the same place, so there is
nothing to read as a repetition.

**The sun and moon are single objects**, so neither a wrap nor a tiling is
correct for them: a wrap makes the body vanish and reappear once per period, and
a tiling puts a second sun on screen at the seam. `celestialParallaxOffset`
gives them a bounded, non-cyclic offset instead, applied as a value rather than
as a canvas translate so that the bound can be expressed against the body's own
rest position:

```
restCx    = margin + celestialX * (screenWidth - 2 * margin)
slackLeft = restCx - radius
travel    = min(2 * parallax * screenWidth, slackLeft)
sway      = (1 - cos(2π * parallax * continuousScrollAccum)) / 2   // 0..1
offsetX   = -((sway + homeScreenOffset) / 2) * travel
```

`slackLeft` is a measured distance, not a safety margin: the keep-out band the
rest position uses (`CELESTIAL_MARGIN_FRACTION`, 0.12) is wider than the disc
radius (`CELESTIAL_RADIUS_FRACTION * 2`, 0.11), so there is always a computable
gap to the left edge and the body is allowed exactly that gap and no more.

The two inputs are combined as a mean because they have different natures.
`homeScreenOffset` is already bounded to `0..1` by the `onOffsetsChanged`
contract, so it is used linearly. `continuousScrollAccum` grows without bound by
design, and any bounded function of an unbounded input is either saturating —
which would pin the body in place — or periodic. A cosine of the background's own
wrap phase is periodic *and* smooth: zero, with zero slope, at phase 0 and again
at phase 1, so the body crosses the seam the star field wraps at with no step in
position or velocity.

The bound costs parallax where the geometry has none to give: below `celestialX ≈
0.38` at `parallaxStrength` 1, the slack runs out before the full parallax does
and the body moves less than an unbounded offset would. Above it, a full swipe
moves the body exactly as far as it always did.

All three functions are pure and live in `PaperRenderer`'s companion, for the same
reason the tile bounds do. With `scrollBackground` off, `drawCelestialBody` takes
its default `offsetX = 0f` and the star field is drawn once, untranslated — that
path is unchanged.

### Depth model

`SceneSpace` is the single source of truth for the ground plane, the
perspective, the road, the pavement and the size of every category. It is pure
Kotlin with no Android types, so every relation in it is unit-tested directly.

```
finalScale = variantScale        // metres -> local units, from the size table
           x sizeVariation       // per-candidate jitter around 1.0
           x perspectiveScale(y) // how far away that ground point is
           x sceneScale(height)  // viewport height / 2400 px reference

groundYFraction(depth) = lerp(0.704, 0.790, depth)
perspectiveScaleAt(y)  = (y - 0.655) / (0.855 - 0.655)      // 1.0 at the near lane
```

Apparent size is proportional to the distance below the horizon, which is what a
flat ground plane seen from a fixed viewpoint does. Static objects, both traffic
lanes, both pavement rows and every vehicle and pedestrian read the same
function, so their relative sizes and speeds follow from their ground lines with
nothing kept in step by hand.

**The weather is sized the same way the buildings are (v4.5).** Rain, snow and the
lightning bolt declare a size in metres and convert it with
`SceneSpace.pixelsPerMetre`, exactly as every category in the size table does. They
reached that convention in two steps and it is worth recording both, because the
first looked like a fix and was half of one. v4.4 moved them off absolute canvas
pixels onto the viewport scale — correct, and the reason rain stopped vanishing on
tall screens — but left them expressed as *pixels at a reference height*, and a pixel
count answers to nothing: the magnitude chosen was three times too large and a
raindrop ended up 1.15 times the height of the pedestrian beside it. Declared in
metres the same number is checkable against a child, a head and the skyline, which is
what v4.5 did.

**A consequence of the metric that keeps being missed.** A metre is
`45 x screenHeight / 2400` pixels, so *the viewport always shows the same 53.3 m of
world*, however tall it is. A fixed particle count is therefore already a fixed
density per square metre on every device: particle **size** has to scale with the
screen, particle **count** does not. What the count has to be is a separate question
from what the size is, and answering it with size — 90 drops made nine times larger
in area — is how v4.4 produced weather that was present and wrong.

**The people behind glass are the one thing the size table does not govern, and v4.6 is what
that cost.** A driver and a passenger are scaled against the *window* rather than against the
ground plane -- they have to be, or they would not fit in it -- and nobody had checked the result
against the way this artwork actually draws a person. A walk sprite gives a pedestrian a head 31%
of their own height, which is a paper-cutout proportion; the busts were sized in a realistic one,
so a driver's head came out 0.320 m against a pedestrian's 0.547 m while standing *nearer the
viewer than the pavement*. The two conventions meet at the windscreen and disagree there, and
there is no entry in the size table for a head, so no arithmetic could have caught it.

The rule now is that **a bust's content is exactly as tall as the glass it sits behind**. The
occupant scales are quotients rather than tuned values, so a future complaint about occupant size
has to move the glass or the artwork and cannot be answered with a fourth constant.

**v4.19: three bodies, one metre-per-unit.** `CarShell` carries a compact, a saloon and an estate,
and a plain car picks one from its own immutable identity (`laneYFraction`, `startDelaySeconds`),
resolved **once** in `CarRuntime`'s constructor -- nothing per-frame can reach the choice, which is
the structural guard against v4.17's falling-leaf defect. A taxi is always the compact and a police
car always the saloon, so their roof accessories and liveries are drawn to one roof and one door
line each.

**v4.20: that identity has exactly ten values, and everything derived from it is a table.** The two
fields are a lane constant (one of two) and a point on an arithmetic progression (one of
`CAR_SLOTS_PER_LANE`), the same ten in every theme the app ships. So a hash of them is not sampling
a distribution -- it deals one fixed hand, once, forever. v4.19's avalanche mix dealt 5/3/2 bodies
(43/26/16 of 85 civilian cars) and the occupant seed, whose ten values all happen to be odd, made
`driverSeed % 2` constant: **every car in every theme was driven by a woman**, and no boy ever rode.

`SceneObjectCatalog.candidateIndexOf` recovers that index by inverting the expression the candidate
was generated from, and `CarShell` and `SeatedOccupants` deal from it: the body 4/3/3, the driver's
family 5/5, the passenger's over the other three, both tones 4/3/3, and which of the two adult
outfits both seats wear. Each deal is as even as ten items allow and is ordered so no lane repeats a
value at consecutive queue positions. The stability contract is unchanged -- these are still pure
functions of the vehicle's own immutable fields, resolved outside any per-frame path.

The lesson is not about hashing. A hash cannot create entropy that the input does not have, and the
test that should have caught the driver defect did exercise the shipped expression -- over a hundred
thousand seeds the app cannot produce. Where a car's identity feeds a choice, the space is ten and
the honest answer is a table that can be read and counted.

The three bodies are deliberately different heights, so `SceneSpace` governs the family by a
**metre-per-unit** (`CAR_UNIT_METRES`, v4.18's own 1.51/50) rather than by a height: each body's
metres are that constant times its own units, `CAR_BASE_SCALE` stays a single number, and one local
unit is the same on-screen pixel on all three. The whole vertical layout of the cabin -- glass top,
sill, seats, occupant scale -- is shared; only the plan differs. An occupant is therefore exactly
the same size in every car.

**v4.25: the seat pitch is derived, and an occupant is mirrored.** Two things about
`drawSeatedOccupant` are load-bearing and neither is obvious from the call site.

The **pitch** (`CAR_PASSENGER_X_UNITS - CAR_HEAD_X_UNITS`, 21.5) is derived from the widest seated
head **converted into the car's units** — one bust unit is `CAR_OCCUPANT_SCALE` of a car unit, and
reading one as the other is what drew v4.25's first family narrow enough to fit a band half the
size of the car. `CarShell.seatOffsetXUnits` then moves *the pair*, never one seat, so the pitch
cannot change by moving a body's occupants: only the police saloon takes a non-zero offset, because
its livery bands the low glass and shortens the pane the pair has to sit in.

The **mirror** is a constant `scale(-scale, scale)` on the bust alone, inside `drawCar`'s own
`scale(dir, 1)`. Direction of travel is already handled by the outer transform — the busts turn
with the car and the driver is always at the leading seat — so this is not about direction. It is
about the artwork's own sense: the three-quarter seated family faces +x, which is the vehicle's
rear. Mirroring about the *anchor* rather than the canvas centre is what keeps the eye axis where
the seat put it.

**Draw order is depth order for people and traffic too (v4.6).** `drawPeople` runs *before* the
vehicle loop. Every pavement row including its jitter is above 0.819 of screen height and every
lane is at 0.834 or 0.862 -- `SceneObjectCatalog` snaps persisted lanes onto those two -- so a
walking figure is always the farther object, and the old order was wrong wherever the two happened
to coincide in x. It was worth 24 px of a 2400 px screen at the deepest figure the generator can
produce.

A category's base scale is **derived**, not authored: each declares the real
height it should read as and the local-unit height its own drawing occupies
(`SceneSpace.SceneVariant`, plus the vehicle and person constants beside it).
That derivation is necessary because the sprites are authored at incompatible
internal scales -- roughly 13 units per metre for a shop front against 46 for a
person -- which no single global multiplier can correct. Which convention a given sprite is
authored in is declared per sprite in `tools/assets/sources/sprites.json` and re-derived by
`validate`, so the table is the manifest rather than a document.

`SceneObjectRenderer.variantFor` resolves which drawing a static object is
(small or large house; tower, restaurant or bar) once, and both the size and the
dispatch come from that one answer. Buildings choose by depth rather than by a
position hash, so towers sit on the skyline and shop fronts among the houses.

**Since v5.0 the variant chooses a family, not a picture.** All five dispatch to one
`drawNeighbourhoodBuilding`, which deals the family's pieces from the building's own position: the
two houses stack a ground floor, none-to-two storeys and a roof, so two neighbours carry two
silhouettes, and the tower, restaurant and bar pick one cut-out figure each. A family's
`unitsTall` is therefore a **reference** height that the deals vary around rather than a drawn
extent — `BuildingHeightDeclarationTest` measures by how much.

**v5.4 corrected the two shops' reference, which had stayed at the two-storey facade's** (96 and
90.146 piece units against a drawn 56 and 53–73), and with it the two numbers in
`SceneSpace.SceneVariant` that are derived from it. All three moved by the same factor on purpose:
the scale a piece is blitted at reduces to `metresTall * pixelsPerMetre / unitsTall`, with
`spriteUnitsTall` cancelling, so correcting only the variant would have shrunk both shops by 42 %
and correcting all three moves nothing. What the correction does move is the *layout* —
`spriteUnitsTall` is the top edge of the rectangle the shop-front criterion divides by
(`SceneObject.frontRect`) and the height the separation pass places a shop by — and that is the
whole of `BACKLOG_v5_0.md` item 113.

What this replaced: `HILL_SAFE_DEPTH_MIN`/`MAX`, `ROAD_SAFE_DEPTH_MAX` and
`depthScaleFor` in `PaperRenderer`, `GLOBAL_OBJECT_SCALE` and
`ROAD_SHOULDER_UNITS` in `SceneObjectRenderer`, and the per-category base scales
in `SceneObjectCatalog` -- four multiplicative factors with three owners, plus
two `canvas.scale` corrections inside the house drawings. The depth range across
the object band went from 1.51x to 2.75x, and the band itself from 111 px to
206 px on a 2400 px screen.

The road's own edges are derived from the lane span of the theme's **whole** car
list, computed once at construction, never from the density-filtered runtime
list. Feeding it the filtered list made the road's width a function of the Cars
density slider. A degenerate span -- every car on one lane fraction, which is
what a pre-v76.2 custom theme has -- falls back to the canonical lane spacing.

`GroundGeometry` now carries only `shiftXWrapped` and `tileWidth`. It used to
carry the hill layer's top and height as well, which was a second copy of the
vertical ground plane passed once per frame.

**Outside the ground projection, deliberately.** The lake sits at and above the
horizon where `perspectiveScaleAt` is at or near zero, so it has its own metric
(15 px per metre) whose only job is keeping its inhabitants right relative to
each other. Birds, the sleigh, fireworks and the celestial bodies are composed
for legibility and read neither.

**The surface is a mirror of the sky (v4.26), which makes it the one part of the
scene that reads a value the sky computed.** `drawSky` keeps the weathered
horizon colour and `drawCelestialBody` keeps whether a body was drawn, which one
and where; `drawLake` runs later in the same single-threaded pass and uses all
four to build the band's gradient, the reflected glow and the light's path. That
is a value handed forward inside one frame, not shared state — nothing outside
`draw` reads it, and nothing writes it twice.

Three consequences worth knowing before touching this code. The band's **top edge
is flat and must stay flat**, because the mountains anchor to its nominal top Y
and a jittered edge opens a sliver of bare sky at some x; the distinction against
the sky is made by a struck waterline whose colour is derived per frame instead.
The reflected glow's **centre sits one radius below the waterline**, because
`SceneCanvas` has no clip and a glow centred on the line spills its upper half
into the sky above the shore. And only the **tile copies that reach the screen**
are drawn: `lakeWrapped` is in `(-screenWidth, 0]`, so the copy at `-1` never
does, which is a third of the water the pre-v4.26 code painted off-screen every
frame.

### One pass over the water, and the three reference points it has to reconcile

Everything that sits on the lake is placed into one set of slots and then drawn
**far to near**, ordered by `LakeLanes.orderByDepth` on a single key. That pass
was introduced in v3.0 for the boats, extended in v3.1 when a leaping dolphin
had to recede as it rose, and extended again in **v4.28** when the waves joined
it. Before that the waves were painted before the boats and the dolphins, so
*every* wave sat behind *every* boat however the two were placed — a breaker
crossing the near edge cut off behind a hull that was plainly further away,
which is the sail-and-dolphin defect of v3.1 in a new pair.

The slot arrays are fields on `PaperRenderer`, sized `LANE_COUNT + WAVE_POOL`,
because this is a draw path and a per-frame list would be a per-frame
allocation. `lakeItemIsWave` and `lakeItemScale` are the two the waves added:
boats and dolphins are drawn at their category's fixed scale, a wave carries its
own.

**The part that needs care is the key.** The three categories do not measure
depth from the same place:

| kind | its depth key is | where its waterline actually is |
|---|---|---|
| sailboat | its placement point | **25 boat units below** the key — `drawSailboat` hangs the hull 8 units down and 17 tall |
| dolphin | its lane | about **8 px below** the lane |
| wave | its base | the base itself |

So a wave keyed by its bare base is compared against a boat's *placement point*
rather than against the boat's hull, and the first frames drawn that way showed
a wave cutting the sail of a boat whose hull was obviously nearer. The wave's
key is therefore its base **lifted by `SAILBOAT_HULL_WATERLINE_UNITS`**, so wave
and hull meet waterline to waterline. All three properties `LakeLanesTest` fixes
survive by construction: boats are untouched, the lift is never negative so
nothing is ever pulled *forward* of where it sits, and one key still orders
everything.

Wave against dolphin is still off by the difference between the two
conventions — about 16 px on the reference device. It is an open backlog item, and the shape of
the real fix is known: one "visible waterline" function per kind, sorted on instead of the lane. It is not done there because
changing the dolphin's key changes the shipped dolphin-and-boat ordering that
five committed goldens portray.

**The waves themselves.** Three slots, present only when it is raining or there
is a thunderstorm — a clear sky gathers none, so the pass is bit-identical to
v4.27's. A slot's membership changes only while it is off screen, which is
`CarSelection.offScreen`'s rule applied to something that also crosses the frame
in plain sight. Each wave is two blits, body then foam, both tinted per frame by
`WaveTint` from the water under them; no primitives and no allocation are added.

---

## 4. Scene management

### Candidate model

A theme does not contain objects; it contains **candidate slots**.
`SceneObjectCatalog` generates exactly `CANDIDATES_PER_CATEGORY = 10` slots for
each structural category, and the same for each seasonal decoration category.
Each candidate has a stable `tileFractionX`, `depthFraction` and `scale`,
derived from a fixed seed so the same theme always produces the same layout.

`SceneCustomization` then decides, per category, which candidates actually
render (`keepCandidate`, via a stable per-slot hash), at what density, and in
which colours. Density therefore thins a fixed candidate set rather than
generating a variable one.

### Stateful vs. stateless drawing

- **Cached**: star field (`regenerateStars`), hill silhouettes
  (`baseHillPaths` + `cachedPathsThemeId/Width/Height/Variation`), static object
  runtimes, car runtimes. These build `Path` and object graphs, so rebuilding
  them per frame was measurably expensive.
- **Stateless, addressed**: clouds, precipitation, falling leaves, birds,
  mountain layers, lake decorations and lake sparkles. These hold no state at
  all; each candidate's attributes are a pure function of its index.

### The candidate system

Every effect draws from a **fixed candidate pool** of constant size
(`CLOUD_POOL_SIZE = 41`, `PRECIPITATION_POOL_SIZE = 90`, `BIRD_POOL_SIZE = 6`,
`FALLING_LEAF_POOL_SIZE = 26`, `MOUNTAIN_POOL_SIZE = 4`,
`LAKE_DECORATION_POOL_SIZE = 4`, `LAKE_SPARKLE_POOL_SIZE = 5`). Pool size is
part of the visual contract: it defines what 100% density looks like.

**Attributes are addressed, not consumed.** `CandidateNoise.value(seed, index,
channel)` is a pure MurmurHash3-finalizer lookup, so candidate 17's drift speed
is the same number whether it is the only survivor or one of ninety. Each
attribute has its own channel, so adding an attribute cannot disturb the ones
already in use.

**Density is a filter over that pool.** `CandidateThreshold.of(index, offset)`
is `frac(index × φ + offset)`; a candidate is present when its threshold is
below the density. Density is therefore linear (`d` keeps about `d × poolSize`),
monotone in both directions, and cannot move a candidate that stays. The
golden-ratio step keeps survivors evenly spread at every density and pool size —
an independent hash per candidate would clump badly in the four-candidate pools.

**Precipitation reads a local density, not a global one.** `CloudCoverage` is a
64-column field over the screen width, refilled by `drawClouds` from the cloud
copies it actually drew — after parallax, drift, wrapping and culling — and read
by `drawPrecipitation` as `intensity × coverage(x)`. `CandidateThreshold` is
unchanged; it is simply handed a density that varies with position. A drop
therefore keeps its x, phase and speed whatever the clouds do; only its
existence changes.

The kernel has a flat top: coverage is exactly 1 within a cloud's own silhouette
and falls smoothly to 0 across a margin `RAIN_SPREAD_FACTOR` times wider,
combining by maximum so an overcast sky saturates to exactly 1 and reproduces the
pre-coverage drop set. Coverage 0 means no precipitation, with no diffuse floor
anywhere. When the cloud layer is switched off, the field is set uniform so that
hiding clouds does not also hide rain.

This depends on `drawClouds` running before `drawPrecipitation` in the frame,
which it does unconditionally. Reversing that order would leave precipitation
reading a one-frame-stale field.

**A drop's colour is derived per frame, not declared** (v4.27). The theme supplies
a day/night pair; the sky it falls through changes with the hour, the twilight
branch and the weather, and in eleven of the twelve themes there is an hour at
which the two carry the same Rec. 601 luma. `drawPrecipitation` therefore reads
the sky's luma at the two ends of the stretch a drop crosses with sky behind it —
the cloud band's own middle down to `SceneSpace.HILL_LAYER_TOP_FRACTION` — and
`standOffFromSky` carries the theme's colour toward white or black until it clears
that whole band by `PRECIPITATION_MIN_LUMA_GAP`, and no further. A colour already
clear is returned unchanged, so a theme that never collided is drawn bit-identically
to v4.26.

It is **one colour for the whole fall**, and that is forced rather than chosen: the
sky's luma is monotone down the fall and the drop's is constant, so whenever the two
are close the sky crosses the drop inside it, and any per-height correction would
have to sit above the sky at one end and below it at the other with no continuous
path between. Two samples suffice because both `skyAbove` and the luma weighting are
linear, so the ends bound the interval.

This reads `skyTopColorNow` / `skyHorizonColorNow`, which `drawSky` writes — and
which the horror-sky branch did not write before v4.27, because it returned first.
The water's mirror and the struck waterline read the same two fields.

**Effect offsets are evenly spaced**, `(ordinal + 0.5) / EffectId.COUNT`, giving
a guaranteed minimum separation of `1 / COUNT`. Hashed offsets were tried first
and rejected: with nine effects, two landed 0.008 apart and selected identical
candidate sets at most densities.

**Small pools keep at least one element** when the category is visible and
density is above zero (`fallbackIndexFor`), so a four-candidate category turned
down low reads as sparse rather than switched off.

Seeds come from `seedFor(ordinal) = theme.id.hashCode() xor (ordinal × 0x9E3779B9)`.
`String.hashCode` is specified exactly by the Java language, so a theme produces
the same scene on every device and every run.

Nothing here is cached, so nothing needs invalidating: a theme, size or
customization change simply produces different values on the next frame.

### Stratified selection, for the pools too small for a coin (v4.2)

`CandidateNoise` answers "what value does this slot get" with an independent
hash, which is correct when a pool has forty members and wrong when it has four.
The candidate system already knew that -- it is why density uses a low-discrepancy
threshold instead of a hashed one -- but the *attributes* kept flipping coins, and
the people system is the one place where the pool is four groups, the sample is
under a dozen people, and **the seed never changes for as long as a theme is
selected**. A clump there is not an unlucky frame; it is what that theme looks
like for ever.

`engine/SeededBalance.kt` is the correction. Two functions, no state:

- `rankOf(seed, channel, slot, slotCount, addressStride, addressOffset)` -- where a
  slot falls in a seeded ordering of its whole pool, computed for one slot at a
  time so it costs `slotCount` hashes and allocates nothing. Handing values out
  by rank makes a split exact rather than merely expected: with four groups and
  two values, two get each, always.
- `drawCount(seed, channel, index, slotCount, rate)` -- how many of a pool are
  drawn at a rate, as `floor(slotCount × rate + u)` for one seeded `u`. The mean
  is exactly `slotCount × rate`, so a declared rate is preserved, but the "none of
  them" tail is gone.

Both keep the addressing the callers already had, so this changed which value a
slot receives and nothing about which slot is which. `PedestrianPopulation` deals
the four person kinds, the three group sizes, the three skin tones, the two
directions and the two pavement rows; `WindowOccupants` deals a building's
occupant count across its own panes. The stability contract is untouched:
a slot's value is still a pure function of `(seed, slot)`, so lowering a density
still removes particular slots and leaves the rest exactly as they were.

### Where a user's own settings live (v4.3)

Three tiers, and until v4.3 only two of them were persistent.

| what | store | scope |
|---|---|---|
| Global preferences | `paperscrape_prefs`, flat keys | one set, theme-independent |
| **A theme's own customization** | `paperscrape_prefs`, one JSON key per theme (`theme_customization_<id>`) | **one per theme, unlimited** |
| The live edit | `paperscrape_prefs`, the flat per-theme keys plus `pending_customization_theme_id` | exactly one theme at a time |
| Saved themes: built-in overrides and standalone custom themes | `paperscrape_custom_themes`, one JSON blob | unlimited |
| Updater state | `paperscrape_update_prefs` | excluded from backup |

The middle tier is the one v4.3 added, and the defect it fixes is worth stating plainly: the live
edit is a **single flat key set shared by every theme**, and `ensureFreshPendingTheme` wipes it
whenever a setter arrives for a different theme. That guard exists for a real reason -- without it
one theme's values leak into the next, which is what v2.12 was fixing -- but it meant that below
"Save this theme as...", a customization survived only until the user touched a second theme.

The guard now **archives before it wipes**: the outgoing theme's state is serialised into its own
key, and the incoming theme's is restored into the scratch space if it has one. `resolveActiveCustomization`
reads, in order: the live edit if it is this theme's, then this theme's archive, then a saved
entry's baked-in customization, then the theme's default.

The archive uses `SceneCustomization.toJson`, the same serialisation `CustomThemeStore` persists
saved themes with -- so a customised built-in and a saved theme are the same bytes in two places
rather than two formats to keep in step, and the backup format gets both for free.

### Backup and theme-share formats (v4.3)

Two documents, `prefs/AppBackup.kt` and `prefs/ThemeShare.kt`, with **separate schema versions and
separate `kind` markers**. They are not variants of one format: a backup is one user's whole app
and carries their API keys; a theme file is one look meant for a stranger and carries nothing
personal. Merging their versions would mean neither could change alone, and importing one where
the other is expected is refused by name rather than by a parse failure.

Both parse into a whole document or an error, never a partial one, so validation and application
are separate steps. `prefs/BackupRepository.kt` supplies what DataStore cannot: the two stores have
no shared transaction, so an import snapshots the current state, writes both, and writes the
snapshot back if the second write fails.

**And the staging is uncancellable (v4.6).** Snapshotting and parsing may be abandoned freely --
they change nothing on disk -- but from the first `replaceAll` onward the only states worth being
in are "both old" and "both new", so the two writes and the rollback are one `NonCancellable`
region. The failure that closed was not a crash: `import` is called from the settings screen's
`rememberCoroutineScope`, which Compose cancels on a rotation or a back press, and a cancellation
between the two writes left half a restore behind *and* skipped the rollback, because the rollback
then suspended on an already-cancelled job. The window is milliseconds wide and needs no crash to
reach.

**Applying a restore is not the same as showing one (v4.6).** Both stores held the new state
immediately and correctly, and the open settings screen went on showing the old one. `SceneTheme`
compares by `id` alone, `CustomThemeEntry` is a data class containing one, so a restored
`CustomThemeData` whose themes keep their ids is `==` to the one it replaced and
`collectAsState`'s default equality policy suppresses the change. `SettingsScreen` holds that state
under `neverEqualPolicy()` and refreshes `CustomThemeRegistry` from the same collector, so the
synchronous registry and the composition cannot disagree about which is current. The equality
override itself is deliberately untouched -- see `CustomThemeDataEqualityTest`, which fails the day
it is fixed properly and the workaround can go.

A shared theme carries the **resolved** scene and layout rather than a built-in id, so a theme
exported today still renders when that built-in is redrawn; `sourceThemeId` is provenance for
display and is never resolved against.

### Theme previews

`engine/ThemePreviewScene.kt` describes what one theme's gallery card contains: sky colours, the
hill colour, the mountain peaks, the lake band, and a list of objects, each an (x, ground y, scale)
plus the sprite parts the renderer itself blits for that object, at the renderer's own offsets.
`ThemePreviewScenes.forTheme(theme, customization)` builds it, and every object in it is
conditional on the same flag the wallpaper reads — `lake.visible`, `snowmen.visible`,
`winterColorsEnabled`, `halloweenEnabled`, `mountainsFront.visible`, and so on — so a preview
cannot contain something the scene would not. The gallery passes the customization a theme
actually carries: `defaultCustomizationFor(id)` for an untouched built-in, the stored override for
a customised one, the saved snapshot for a user theme.

It holds **no Android type beyond resource ids**, which is what makes "what does this theme's
preview contain" a unit-testable question; `ThemePreviewSceneTest` pins the characteristic object
of each of the twelve themes and, in both directions, that nothing a theme has switched off is
drawn.

Both places that show a preview -- the gallery card and the strip at the top of World & scene --
go through `ThemePreviewGeometry` (one 4:3 shape, one uniform scale, no per-call-site crop or
fitting factor) and through the same scene builder, so they cannot drift apart again. World &
scene passes `forceNight` to see night colours; the gallery never does.

`ui/ThemePreview.kt` replays that description into a Compose `Canvas` through `CanvasSceneTarget`
and the same `SpriteBlitter` the wallpaper uses. There is no GL context, no animation, no timer and
no per-card bitmap: the description is built once and kept by `remember`, sprite pixels come from
the process-wide `SpriteCache`, and a card costs roughly twenty static blits on composition and on
scroll, and nothing at rest.

### The automatic-theme calendar

Two tiers, checked in order: four **occasions** (Easter, Halloween, Christmas, New Year) over four
**seasons** (winter, spring, summer, autumn). An occasion passes *over* whatever season is beneath
it; seasons partition the year and cannot overlap each other. The first match wins, and the order
is `CalendarWindow`'s declaration order — code, not configuration.

**The seasons are a continuous ribbon, and until v5.1 they were not.** The shipped table had five
season entries covering 296 days and leaving **69** — the whole of October, the whole of December
and 1–7 January — with no season at all. Every one of those days happened to be inside an occasion,
so the calendar resolved for every date while resting on the occasions to do it. That was invisible
until the dates became editable: shortening Halloween would have exposed an October with nothing
underneath. The four seasons now start on the first of their month (1 Dec, 1 Mar, 1 Jun, 1 Sep),
which is the meteorological convention; winter's last day is stated as **29 February** so a leap day
cannot fall out of the ribbon, and in a common year no date can equal it.

Autumn was two entries, split around Halloween, and is one. Halloween taking October back is the
tiers doing their job, and `SeasonalCalendarIdentityTest` walks ten years day by day to show the
two shapes agree rather than asserting that they must.

**The dates are the user's, and only the dates.** `SeasonalCalendar` holds a span per window and
two offsets for Easter; it is persisted as versioned JSON under one preference key, and it records
**only the windows that differ from the factory**. An install that has never opened the calendar
screen therefore has no key, resolves exactly as a build without the feature would — which is what
`SeasonalCalendarIdentityTest` checks against a transcription of the v5.0 table — and follows a
future release that moves a factory boundary. Resetting removes the key rather than writing a
document that agrees with today's defaults.

Easter is the one window with no dates. Its Sunday is Computus, and what the user sets is the
window's length either side. It is also exempt from the overlap check: it is first in precedence and
wins wherever it lands, and it moves by up to five weeks between years, so a year-free "does Easter
overlap Halloween" has no answer and a validator that produced one would be inventing it.

`themeForDate` still returns `String?`. With the factory calendar nothing can be uncovered, but a
user may move a season and open a gap; the caller falls back to the hand-picked theme, and the
settings screen names the uncovered dates rather than leaving them to be discovered on the day.

### The seasonal launcher icon (v5.4)

The app icon follows the date through **this same calendar** — the user's own windows, not a civil
calendar of its own. Moving the start of winter on the Holiday calendar screen moves the icon with
it, which is the point: two ideas of "winter" in one app is a duplicate that gets paid for the day
they disagree.

It follows the **date**, not the theme on screen. `autoThemeByDate` is opt-in and off by default,
so an icon that followed the scene would never change for most users; the icon is a calendar
indicator, and in December a user who picked the beach by hand sees a Christmas icon over a summer
scene by design.

**Eight windows, six icons.** Five are seasonal (the four seasons plus Christmas, the one occasion
with a drawing of its own) and one belongs to no season. Halloween, Easter and New Year have no
icon and take the icon of the season they fall in — *derived* by asking the calendar which season
covers that date, never tabulated, so a user who drags a season drags this with it.
`SeasonalIcon.DEFAULT` is the shipped sunset town: it is what the manifest enables, so a fresh
install never shows a season that is not the season, and it is where a calendar with a gap in it
lands.

**The mechanism is `activity-alias`.** An app cannot change the icon it declares; it can only
choose which of its launcher entries the system draws. `ui.SettingsActivity` no longer carries a
LAUNCHER filter — six aliases do, exactly one enabled at a time — and `LauncherIconSwitch` enables
the new one **before** disabling the old, so the package is never momentarily without a launcher
entry for a launcher to react to.

**`DONT_KILL_APP` is the whole of the safety argument.** This process is the live wallpaper.
Without the flag the platform kills it, and measured on a BV6600 that is about seven and a half
seconds of black screen before the system re-binds the service; with it, the engine object is the
same one before and after. The flag cannot be passed from outside the app, which is why this is
code and not a script.

**Nothing polls.** `SeasonalIconController` registers for `ACTION_DATE_CHANGED` (plus
`TIME_CHANGED` and `TIMEZONE_CHANGED`) in code rather than in the manifest — implicit broadcasts
have been refused to manifest receivers since Android 8, and the process is already alive whenever
the wallpaper is set, so there is nothing to wake. A registered receiver costs nothing until it
fires: no alarm, no job, no wakelock. A device whose wallpaper is not this app runs none of it and
corrects itself the next time the wallpaper or the settings screen starts.

### Theme resolution

```
settings.themeId
   └─ if autoThemeByDate → SeasonalThemeRules.themeForDate(settings.seasonalCalendar) may override
        └─ CustomThemeRegistry.resolveActiveCustomization(themeId, pending…)
             ├─ user override for a built-in theme, or
             ├─ saved custom theme's own customization, or
             ├─ in-progress live edit (if tagged for this exact theme), or
             └─ defaultCustomizationFor(themeId)
                  └─ .withResolvedDayNightColors()   ← automatic pairs, once
```

### Windows, and what colour one is (v4.12)

Every window in the scene crossfades between two constants on the frame's own `nightGlow`:
`SceneObjectRenderer.WINDOW_GLASS_DAY` (`#B9CBD9`, cool glass) and `WINDOW_GLASS_NIGHT`
(`#FFE79A`, warm light). `windowGlassColor` is the only place that blends them.

**Since v5.0 there is one caller.** Every window of every building is a `GLASS_MASK` part, and
the composer computes the colour once per building and hands it to all of them — so what used to
be a coupling between five draw functions is a single expression, and the gallery preview reads
the same function rather than the second crossfade it would otherwise have needed.

Since v4.22 the *commercial* buildings' night is scaled by the business openness before it
reaches those ramps (`BusinessHours`, off by default and then arithmetically absent): outside
their hours the shops, the bar and the towers hold their unlit daytime glass whatever the sky
does, and their window occupants' dealt count thins the same way. The houses' windows never
consult it — one line, `glassNight`, which `BusinessHoursWiringTest` pins along with the occupant
path's own exemption, the way `SkyscraperWindowTest` pins the colour coupling.

**A tintable window asset is a white mask.** Since v5.0 it is a *weight* mask summed over the
piece's fixed layer rather than a whole sprite multiplied by a colour — the people's system since
v4.30 — which is also why the frame around a pane can no longer be washed out by the glass's own
tint: the two are different layers. The history below is the flat-facade version of the same rule.

`restaurant_window` always was a white mask; v4.12 made
`skyscraper_wall_lit` one too, regenerating it from its SVG through the normal pipeline. Before
that it carried warm `#ffe9a8` artwork and could only ever be shown at night, which is why the
tower's *daytime* windows came from the grid baked into `skyscraper_wall` and therefore took the
wall's own tint -- a window the colour of the bricks around it, which nothing else in the scene
does. One tinted blit now covers both halves of the day, and the tower's private alpha ramp
(`litWindowAlpha`, still used by the houses and the bar) is gone from it.

The night look moved by a hair as a side effect: the tower's grid was drawn `#ffe9a8` and the shared
constant is the restaurant's `#ffe79a`, 14 levels apart in blue. Having one warm is the point;
having two was the thing worth losing.

`SkyscraperWindowTest` reads the call sites and pins the coupling. It exists because two mutations
of this rule -- swapping the crossfade's ends, and reverting the tower to an untinted overlay --
were missed by every JVM test and caught only by instrumented goldens.

### Automatic day/night colours (v4.12)

Every colour the user can edit that exists as a **day/night pair** carries an
`AutoColorMode`: `MANUAL` (the default, and the behaviour that shipped before
this), `FROM_DAY` (the user sets day, the night half is derived) or
`FROM_NIGHT`. Single colours — the sun, the moon, the four bird colours, the
sunrise/sunset sky bands — have no mode, because there is no twin to derive
from or for.

`DayNightColor` is the only implementation of the transform, and it is plain
Kotlin with no `android.*` import so that `ThemePreviewScene` (which
deliberately avoids the platform) and the JVM tests run the same code the
wallpaper does.

Since **v4.13** it works in **CIELAB**: hue held, `L*` ×0.28, chroma ×0.72, and
a small push towards blue (`b*` −6, scaled by the daytime lightness so black
stays black). Out-of-gamut results are gamut-mapped rather than clipped —
clipping a darkened red pinned green to zero and dragged the hue towards
magenta.

The two factors were ×0.50 and ×0.80 in v4.13 and are **×0.28 and ×0.72 since
v4.14**. v4.13 set them from one sample — a near-white Christmas hill — which
is why they were still wrong everywhere else: the same factor that puts white
in the right place puts a red house at an `L*` a night has no room for. They
now come from the band the twelve built-in themes author their own night
colours in (`L*` 10.9 to 29.6) and are checked across a matrix of eleven
surface kinds by `DayNightMatrixTest`, not against a single colour.

v4.12 worked in HSL with factors fitted to the 41 authored day/night pairs.
**Fitting those pairs was the mistake.** Stratified by daytime lightness they
do not describe one rule at all: the sky at `#CDEFFF` goes to 0.12 of its `L*`,
clouds at `#FFFFFF` to 0.36, a wall to 0.73, and snow on mountains to 0.87 —
because snow is *meant* to stay bright under the moon. They are per-object
artistic decisions, and the median across them left white at `#A2A2A2`, a mid
grey, which is what "the night colours are still too light" was reporting. The
constants now come from the requirement and were settled by looking at a
physical device.

Two properties are worth stating because the design turns on them:

- **It is applied once**, at the single choke point above, so the renderer, the
  settings screen and the theme gallery cannot disagree and nothing derives a
  colour per frame. A customization with every pair on `MANUAL` is returned as
  the same instance, so the feature costs nothing to anyone who ignores it.
- **A derived colour is never written back.** The DataStore and every saved
  theme keep the user's own two values whatever the mode is; the derivation
  produces a copy for drawing. That is the whole of the reversibility promise:
  switching a pair back to Manual restores exactly what was picked by hand,
  because nothing overwrote it. The settings screen shows the derived half
  greyed and inert, the same treatment the Clouds screen gives its controls
  while Live Weather is driving them.

The persisted form is one string key per pair (`…_auto_mode_1`,
`hills_auto_mode`, `sky_auto_mode_high`, …) plus the matching JSON field. Both
readers default to `MANUAL` when the key is absent, so themes and backups
written before v4.12 restore exactly as they always did and no schema version
moved.

### Structural vs. cosmetic configuration changes

`PaperRenderer.syncObjectRendererWithTheme()` runs every frame and resolves in
three tiers:

1. **Identity fast path.** The engine assigns `sceneCustomization` a fresh
   instance only when a preference actually changed, so a reference comparison
   settles the common case without walking the config.
2. **Full reconstruction**, only when the *layout* changes — a different theme
   id, or a custom-theme edit/reset/delete signalled by the registry
   generation. Those are the only inputs to `SceneObjectCatalog.layoutFor`.
3. **In-place update** for everything else:
   `SceneObjectRenderer.customization` is assigned and decides for itself what
   to rebuild.

Only `ObjectVariantConfig.visible` and `.density` can change *which* objects
exist, because those are the only fields `keepCandidate` and the car selection
(`CarSelection`, since v4.22) read. Everything else — all 48 category colours,
the sky/stars/clouds/precipitation/rainbow/mountain/lake/bird sections, hill
variation, the seasonal palette flags — is consumed at draw time.
`SceneCustomization.staticStructurallyEquals` and `.carsStructurallyEquals`
encode that distinction as pure, allocation-free field comparisons (not a hash:
a collision would silently skip a needed rebuild).

The static and car lists are compared separately so that changing, say, house
density rebuilds the static objects **without** resetting every car's in-flight
`progress` along the road. Rebuilding the static list is visually free, since
`StaticRuntime` holds only an `idleSeed` derived deterministically from its
spec; rebuilding the car list is not, which is why it is gated on the cars'
**visibility** alone. A car *density* change rebuilds nothing at all since
v4.22: the slider maps to an explicit count (1 car at 0%, all ten slots at
100% — `CarSelection`), every inventory slot keeps a ticking runtime whatever
the count, and membership flips per car only while that car is off the drawn
span of its loop — so an addition drives in from the edge, a removal finishes
the pass it is on, and nothing pops into or out of the middle of the road.

Before this, any difference at all reconstructed the whole renderer.

---

## 5. Asset management

### Current state

All scene sprites are PNGs in `res/drawable-nodpi/`. `nodpi` is deliberate:
sprites are scaled by an explicit `canvas.scale()`, so Android's automatic
density scaling must not also apply.

`SpriteCache` decodes each resource once (`inScaled = false`) into a Kotlin
`object`, shared by every engine in the process — a wallpaper process can host
the picker's preview engine and the live engine at the same time.

Bookkeeping (keys, byte sizes, LRU order) lives in `SpriteCacheIndex`, a pure
`IntArray`-backed structure. That is not incidental: the previous
`ConcurrentHashMap<Int, Bitmap>` boxed the `Int` key on **every** lookup, and
resource ids are far outside `Integer`'s small-value cache, so every sprite blit
allocated an `Integer`. The allocation happened inside `Integer.valueOf` rather
than as a `new` at the call site, which is why the Phase 1.1 allocation audit
did not catch it.

`onTrimMemory(level, anyEngineVisible)` applies `MemoryPressurePolicy`, evicting
least-recently-drawn sprites to a fraction of current usage, or everything when
the process is a kill candidate. Sprites are dropped, never `recycle()`d:
dropping the reference is enough for the platform to reclaim the pixels (bitmap
storage has been GC-tracked native memory since API 26) and `recycle()` would
risk an `IllegalStateException` if a reference were still held.

The absence of synchronisation is deliberate and load-bearing: rendering runs on
the main looper and `onTrimMemory` is delivered on the main thread, so a trim
cannot interleave with a draw. Moving rendering to its own thread would require
adding a lock **before** that change lands.

Measured footprint if every sprite is decoded. **The figures are deliberately not written
down here.** `paperscrape-assets inventory` measures them from the shipped PNGs, and a number
copied out of it into this document is stale the next time a sprite changes — which is exactly
what happened to the table that used to stand here.

```bash
cd tools/assets && python -m paperscrape_assets inventory   # writes reports/runtime-inventory.{json,md}
```

**`tools/assets/reports/runtime-inventory.md` is evidence of the run that produced it, not a
live view.** Measured 2026-09-07 it disagrees with the tree — it records 24 byte-identical
groups and 30.25 MB decoded where the tree measures **0** and **28.85 MB** — so regenerate it
before quoting it.

What is structural, and therefore worth stating here rather than counting:

- **Every canvas is a whole multiple of the 3 px authoring grid.** `SpriteGeometryTest` fails
  the build if one is not.
- **No two shipped sprites are byte-identical.** `validate` fails on an undeclared duplicate
  pair: one drawing under two names is two decodes, two atlas entries, and two files that can
  be edited apart in one place only.
- **Total decoded size is capped**, and the cap is what the memory-pressure policy and the
  atlas are sized against. `SpriteGeometryTest` asserts both it and the rule that no single
  sprite may take more than an eighth of it; the cap's current value lives in that test, which
  is the only place it can be raised deliberately.
- **A person is fixed art plus one weight mask per colourable region, since v4.30.** Until then the
  `person_*` set was dominated by its per-skin-tone recolours — 168 files that were one drawing with
  one colour moved. A shape now ships as `<shape>_fx` (everything that does not follow one of the
  four colours, plus the dark half of everything that does) and up to four masks `_ms _mh _mt _mb`
  (skin, head, shirt, trousers), and the engine recomposes `fixed + Σ (mask × colour)` at the blit.
  Both are written by `tools/generate_people_layers.py`, which also writes the engine's lookup
  table; `PeopleLayerAssetTest` checks the sum against the drawing. **Every mask is a full canvas in
  its PNG and a fraction of one in texels** — see the crop in §3 — which is why the decoded ceiling
  rose while the texel ceiling fell.

Transparent margin is no longer accounted as waste, because it is no longer
incidental. Each V2 sprite declares a `contentBox` and an anchor rule, and the
margin around the content is what the anchor is measured against: `palmtree_fronds`
hangs its fan above a declared attachment point, `cloud_body` and `sun_body` are
centred in canvases their artwork deliberately does not fill. Cropping any of them
would move the sprite rather than save anything. The rule that every sprite must
reach its own canvas edges therefore no longer applies, and the check that enforced
it was replaced — see the sprite geometry test below.

### The source pipeline

The generators that produced these PNGs (`gen_sprites.py`,
`gen_terrain_sprites.py`, `gen_sky_sprites.py`) were **never committed and are
lost**, so for most sprites the PNG is still its own source. The root cause is
worth stating precisely, because it is not "the files went missing": the practice
of the time deliberately kept the generators out of the repository and shipped
only their output. The project's asset rules now forbid exactly that: whatever renders a shipped
sprite is committed beside it, or the sprite has no source and says so.

`tools/assets/` is the replacement. It is **offline developer tooling: Gradle
never invokes it and the app does not depend on it.** The pipeline is

```
SVG source  ->  version-pinned deterministic rasterisation  ->  PNG
```

| Piece | Role |
|---|---|
| `sources/sprites.json` | Registry (schema 2): one entry per shipped sprite, declaring size, content bounding box, anchor rule and anchor, scale convention, tint class, usage, and either an SVG source or a stated reason there is none |
| `sources/svg/` | The SVG sources |
| `paperscrape_assets/raster.py` | The one rasterisation path, plus a probe that hashes a fixed document to detect toolchain drift |
| `paperscrape_assets/fit.py` | Geometry recovery by sweeping a parameter against a shipped PNG |
| `paperscrape_assets/callsites.py` | Syntactic resolution of sprite blit call sites in the Kotlin sources, so declarations can be compared against the code |
| `paperscrape_assets/normalize.py` | The padding and grid normalisation rule: co-registered groups, exclusions, and the crop plus origin compensation each sprite needs |
| `paperscrape_assets/fidelity.py` | Comparison metrics and the three verdicts |
| `staging/` | Rendered output. Never `res/drawable-nodpi/`; the CLI refuses an output path inside it |
| `reports/` | Committed measurements, including a visual comparison sheet |

### Padding and grid normalisation (Phase 3.3)

A sprite's **normalised content box** is the union of the measured alpha bounding
boxes of its co-registered group, rounded outward to a multiple of
`SPRITE_PIXELS_PER_UNIT` for a `SCENE_UNITS` sprite and of 1 px for a
`CANVAS_PIXELS` one. The sprite is cropped to that box and its call site's origin
is compensated by `trim / unit`. `SpriteBlitter` places the bitmap's own pixel
(0,0) at the origin, so the crop and the compensation are one change: either
without the other moves the sprite.

Three properties of the rule are the reason it is a rule and not a per-sprite
judgement:

- **Outward rounding keeps the compensation an integer.** The blitter multiplies
  the origin by the same unit the compensation divided by, so cropping to the
  measured box would produce fractional units that return as sub-pixel positions
  and get resampled through `FILTER_BITMAP_FLAG`. The price is up to `unit - 1` px
  of retained padding, which is load-bearing rather than leftover.
- **The union holds a lookup group together.** The sprites selected from a
  table at draw time are blitted through a single origin literal — the walk
  frames, the window occupants, the car drivers. Their content boxes differ, so a
  per-member crop would need per-member origins that do not exist, and the walk
  cycle would jitter. Sprites that merely share an origin *value* are not a group:
  two call sites with their own literals each take their own crop.
- **A trimmed side keeps at least one transparent pixel, because the margin is what
  the filter reads** (v4.31). This is the general rule and it is worth stating on its
  own; see below.

#### The transparent margin is part of the drawing (v4.31)

**"Crop to the ink" is not neutral for a scaled, filtered blit, and this is the
general statement of it.** A sprite's outermost transparent pixel is not waste: at
the destination pixel that straddles the drawing's edge, the bilinear sampler reads
the ink texel *and the transparent one beside it* and blends them. Take that pixel
away and the sampler has nothing on that side, so it **clamps to the edge** and reads
the ink twice. Nothing has moved — the ink is at the same coordinates in the sprite's
own space and the origin compensation keeps it there on screen — but the rasterised
edge is heavier than it was.

Measured on the BV6600 in v4.31, cropping `dolphin_body`, `sailboat_hull` and
`sailboat_sail` tight to the grid: **6 pixels changed in each of three lake goldens**,
up to 70 levels on a single contour pixel, in frames where the other 288 000 were
identical.

So the rule, wherever a sprite's border is removed: **the crop leaves a guard pixel on
every side it trims.** A side whose ink already reaches the canvas edge is left alone —
no margin exists there to preserve, the sampler has been clamping since the sprite was
authored, and the crop is not what did it.

**Where each draw path stands against it:**

| path | who removes the margin | covered? |
|---|---|---|
| `tools/assets` `normalize --apply` | crops the shipped PNG | **yes, since v4.31** — `normalised_box` steps one grid cell out on any trimmed side |
| `GlTextureCache.cropToContent` → the atlas | crops the *reduced* bitmap to zero margin before upload | **yes, and it always was** — `GlTextureAtlas.add` uploads every entry inside a one-texel transparent border, which its own "Bleeding" note exists for: *"a bilinear sample that strays past an edge finds transparency rather than the neighbouring sprite"*. The guard pixel `cropToContent` removes is put back before any sampler sees it |
| `GlTextureCache.uploadStandalone` | same crop, no atlas | **no** — `GL_CLAMP_TO_EDGE` with `GL_LINEAR` is exactly the clamp described above. Currently unreached: since v4.29's skyline packer the twelve-theme census finds **zero** standalone entries at full density. The path exists and the day a sprite spills into it, its edge is the heavier one |
| `CanvasSceneTarget` | blits the PNG as it ships | **the PNG is the only guard it has**, which is why the asset-side rule is where this had to be fixed |

The atlas's border and the asset tool's guard pixel are the same invariant one layer
apart, and the atlas had it first. That is the reason this is written here rather than
only in `normalize.py`: two of the four rows above solve it independently, one is
covered by accident of not being reached, and a reader changing any of them needs to
know which.

`normalize` runs in check form as part of `paperscrape-assets all`. **The invariant
it enforces no longer describes the shipped set**: the V2 library places drawings
inside canvases sized on the grid and declares the content box, so a number of sprites carry
margin on purpose and cropping them would move them — `paperscrape-assets normalize` in check
form reports which, and `EXCLUSIONS` in `normalize.py` names the deliberate ones with their
reason. The JVM-side check that
mirrored it for CI, `SpriteNormalisationTest`, was replaced in v76 by
`SpriteGeometryTest`, which asserts what is still true of the set as a whole —
every canvas on the 3 px grid, a ceiling on total decoded bytes, and no single
sprite taking more than an eighth of it. The byte ceiling is the part worth
keeping: it is what a memory-pressure policy and an atlas are sized against, and
stating it directly is more honest than inferring it from per-sprite margins.

Two other sprite tests sit beside it, both reading the PNGs rather than the code:
`SpriteVariantTest` (no two sprites are the same bytes, and the seasonal pairs that
were a declared gap now differ) and `SpriteTintClassTest` (every tinted sprite is a
light neutral mask, every untinted one carries colour). The last replaces
`LakeDecorationTintTest`, which pinned the same property for three sprites and
whose own doc comment specified this migration: when artwork gains baked colours,
its call site goes back to an untinted blit in the same change.

**Determinism** rests on an exactly pinned `resvg_py`, chosen over a cairo-based
rasteriser because it carries its own scan converter instead of binding to a
system graphics library. Output that varied with the host's libcairo would make
"reproducible" mean "similar on this machine". The pin is verified rather than
declared: `probe` renders a fixed document and compares its hash to a recorded
value, so a toolchain change is detected instead of silently invalidating every
recorded figure.

**Coverage as shipped: every sprite carries a registry entry, and every *drawn* sprite carries an
SVG source; the ones that do not name a generator instead.** Since v4.30 that second group is the
people's layer files, which declare `source.kind = "generated"` and name
`tools/generate_people_layers.py`; before it, it was the per-skin-tone recolours under
`source.kind = "none"`. `paperscrape-assets validate` prints the three numbers — entries, sources, declared gaps — which
is where to read them. The v4.28 snapshot that stood here (305 / 143 / 162) was wrong by v5.1 and
is removed rather than re-typed. Every sprite being
*described* by the registry is new in v76 and is the single most consequential thing
the V2 asset library changed; being *regenerable* is a separate, smaller set.
`tools/assets/README.md` states the registry-to-`res` relation as a rule rather than a
count, and `tests/test_registry_coverage.py` enforces it -- prefer those to the numbers
here, which are a snapshot.

The pipeline was built when the original generators were lost, so a sprite could
only be given a source if its geometry was *determined by measurement* —
rectangles and rounded rectangles, whose single free parameter can be swept
exhaustively. That reached 22 of 108. Free-form silhouettes, baked mottling and
figurative art were declared gaps, because a best-scoring fit over free parameters
and a seed would be a redraw presented as a recovery. The V2 library sidesteps the
recovery problem entirely: the artwork was drawn from zero *with* its sources, so
there is nothing left to reconstruct.

**This closes blocker B1.** Group 4 (perspective and scaling) was blocked on being
able to regenerate the sprites it re-anchors — people, vehicles, buildings,
decorations — and every one of them was a gap. They are not any more.

What the earlier phases fixed, and what the library changed underneath them:

- ~~18.3 MB of transparent padding~~ — resolved in Phase 3.3, and superseded: V2
  declares a `contentBox` per sprite, so margin is geometry rather than waste;
- ~~16 byte-identical duplicate groups~~ — resolved in Phase 3.4/3.5 down to the
  six seasonal head pairs, which were declared `IDENTICAL_GAP` because their winter
  artwork had never been drawn. **V2 drew it.** No `IDENTICAL_GAP` group remains
  and the shipped set contains no byte-identical pair at all;
- ~~5 sprites off the 3× grid~~ — resolved by the library. The two palm frond
  variants were the only ones actually drawn, at 102×176 whose height is not a
  multiple of the oversample; V2 redraws the fan at 120×120 with a declared
  attachment point, which also retires the hand-tuned `-87.45` origin;
- ~~91 of 108 anchors undetermined~~ — every sprite now declares an `anchorRule`
  and an `anchor`. `PART_LOCAL` replaces `UNDETERMINED` for the parts whose
  placement their composite owns: the same fact, stated as a declaration instead of
  an absence.

### The manifest, and what checks it

Schema 4 declares, for every sprite, the metadata the asset rules require: a
`contentBox`, an `anchorRule` with the `anchor` it derives, the scale
convention, the tint class and the season. `contentBox` is re-derived by `validate`
rather than trusted, so it cannot drift away from the PNG it describes.

Two entries carry a `notes` field recording a disagreement between the V2 manifest
and the shipped call sites, resolved in opposite directions. `star_sparkle` is
declared `CANVAS_PIXELS` by the manifest, which is defect D-1 restated — read as
raw pixels the 180 px sparkle covers 180 local units against a star's own
`STAR_SPRITE_RADIUS_DIVISOR`, 16 since v4.23 and 32 before it — so the registry
keeps the call site's `SCENE_UNITS`. `santa_sleigh_scene` is declared
`SCENE_UNITS` where the call site said `CANVAS_PIXELS`, and there the manifest was
right because the sprite genuinely was re-authored on the grid, so the call site
moved. Size, convention and origin are only correct together; when two of them
disagree the answer comes from whichever was actually re-derived.

The manifest is **tooling-side only.** No Kotlin reads it, nothing in the Gradle
build depends on it, and the APK is unaffected by its existence. Consuming it at
runtime would need a per-sprite lookup on a draw path, and there is nothing to
consume it *for* until the re-anchoring work in Group 4.

What it does do now is close the gap defect D-1 came through. A sprite's pixel
size, its scale convention and its origin are correct only together, and nothing
in a PNG records the convention — so the registry declared it and nothing
compared the declaration to the code. `callsites.py` now resolves each blit call
site syntactically and `validate` compares `scale`, `tint` and, where an anchor is
determined, the origin.

Resolution is deliberately total-or-nothing. There is no dataflow analysis: a
sprite chosen from a lookup table (`resId`, `driverRes`, `phaseSprite`) or an
origin computed from the drawn object's own dimensions resolves to nothing, and
is reported as **unresolved** rather than counted as agreement. Current reach:

| Check | Sprites reached |
|---|---|
| `contentBox` against the PNG | 111 |
| `scale` and `tint` against the code | 10 (see defect D-4) |
| origin against the declared anchor | 4 (see defect D-4) |
| variant group against the shipped bytes | 18 groups, 36 sprites |

The rest is not a shortfall to be papered over: an origin is `placement - anchor`
with both unknown, so it fixes an anchor only for a sprite that *is* an object
rather than a part of one. `house_large_window` is blitted at four different
origins; the `person_*` sprites at hand-tuned constants outside the anchoring
system entirely.

See `tools/assets/README.md` for the authoring conventions and the commands.

---

## 6. Animation systems

| System | Mechanism |
|---|---|
| Parallax | `continuousScrollAccum` (`Double`) + optional home-screen swipe offset → `scrollProgress` (`Float`) → per-layer multiplier. The celestial body is the one exception: it takes the two inputs separately and bounds the result — see §3, *The sky layer*. |
| Object idle motion | `sin(elapsedSeconds × k + perObjectPhase)`. |
| Cars | Per-runtime `progress` advanced by `deltaSeconds × speedFraction`, wrapped with an off-screen buffer. |
| People | 4 hardcoded candidates, own drift timer, 4-frame walk cycle stepped by elapsed time. |
| Precipitation / leaves | Stateless: each candidate's phase re-derived from `elapsedSeconds` every frame. |
| Fireworks / sleigh | Self-contained effect classes with their own timers. |
| Day/night | `SunPositionCalculator` produces a normalised `DayPhase`; every colour is a blend between a day and a night value by `dayBlend`. |

**Time base:** scene time is `SceneTime`, a `@JvmInline value class` wrapping a
`Double` (so it compiles to a bare `double` — no allocation on the per-frame
path). It is **bounded at the point of use, not at the accumulator**: `sinAt`,
`cycle`, `cycleOf` and `frameIndex` each do their arithmetic in double precision
and narrow to `Float` only *after* the operation that bounds the result.

There is deliberately **no wrap period**. Sinusoidal consumers would tolerate one
(every rate in the renderer is a multiple of `0.05`, so `40π` would work), but
the linear-cycle consumers — cloud drift, precipitation fall, bird drift, lake
decorations — derive their rate from a per-candidate random value, so no period
can be a whole number of cycles for all of them. Any global wrap would make every
cloud, raindrop, bird and leaf jump at the wrap instant.

`scrollProgress` is a `Double` and is never narrowed directly. Each layer's shift
goes through `wrappedScrollShift`, which multiplies and wraps in `Double` and
narrows only the wrapped result. Wrapping `scrollProgress` itself is impossible
for the same reason: every layer applies a different parallax factor and the
user-set `parallaxStrength` is continuous over `0.5..2`.

---

## 7. Persistence

Two separate DataStore Preferences instances:

- `paperscrape_prefs` — `WallpaperPrefs`, all user settings, exposed as
  `settingsFlow: Flow<WallpaperSettings>`.
- `paperscrape_custom_themes` — `CustomThemeStore`, custom themes and built-in
  overrides, serialised as JSON.
- `paperscrape_update_prefs` — `UpdatePrefs`, update snooze state.

Both the Compose UI and the wallpaper engine collect the same flows, which is
what makes settings apply live without a restart.

No flow operators are used: there is no `debounce`, `conflate`, `sample` or
`distinctUntilChanged` in the project. None is needed, because the write path
itself no longer fires per drag tick — see below.

### Continuous controls, and what is not one

A slider is right for a value with few positions and no name — a density, a strength, a count of
days. It is wrong for a value the user already knows, and v5.1's first round proved it: the two
ends of a calendar window were sliders over all 366 month-days, which on the reference device is
**366 positions across a 632-pixel control, 1.7 pixels per day**. A fingertip selects a week there,
not a date. They are typed now (`ui/DateEntry.kt`), and Easter's two **lengths** — nought to seven
days, eight positions on the same track — stayed sliders, which is the distinction rather than a
compromise.

**Every** `Slider` call site goes through `PreferenceSlider`, which holds the
in-flight value in local Compose state for the duration of the drag and writes
to DataStore **once**, from `onValueChangeFinished`, and only when the value
actually changed. The count used to be written here as 16; it was 24 by v5.0 and
26 now, and a number that drifts every release is worse than none — count them
with `grep -rhoE 'PreferenceSlider\(|SettingsSliderRow\(' app/src/main/kotlin`
and subtract the two declarations. Value captions are rendered by the same composable from the
displayed value, so they stay live during a drag without any write.

The handover between the local value and the persisted value arriving back
through the flow is in `SliderDragState` — pure functions, no Compose or
Android types, so it is unit tested directly. The local value is held until the
persisted value matches what was committed, otherwise the thumb would snap back
to a stale value for the frames between the finger lifting and the write
landing.

Text fields (custom location, hex colour, theme name) already followed this
pattern with an explicit Apply/OK commit; the sliders did not.

The custom-theme JSON carries a **`schemaVersion`** field
(`CUSTOM_THEME_SCHEMA_VERSION`, currently `1`). Payloads written before
versioning existed (v73 and earlier) have no such key and are read as version
`0` (`CUSTOM_THEME_SCHEMA_VERSION_LEGACY`); versions 0 and 1 describe the same
shape, so migrating between them is a no-op by construction and simply stamps
the version on next save.

`migrateCustomThemeJson` is the single registration point for future
migrations. Payloads from a *newer* schema than the running build understands
are read best-effort rather than rejected: refusing them would delete every
saved theme when a user installs an older APK over a newer one. The accepted
cost is that re-saving such a payload drops the fields the older build did not
understand.

`readCustomThemeSchemaVersion(raw)` reports a payload's version without parsing
the rest of it, and returns `null` for absent or unparseable data.

Individual field reads remain defensive (`opt*` with defaults), so purely
additive changes still do not require a version bump.

`SceneTheme` overrides `equals`/`hashCode` on `id` alone, so two themes with the
same id but different colours compare equal. `CustomThemeRegistry.generation()`
exists as a counter to work around this.

---

## 8. Build system and CI

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.4.0 (raised from 9.3.1 in v5.3 to close 41 Dependabot alerts -- see *Dependency security* below) |
| Gradle | 9.7.1 (wrapper jar SHA-256 matches the checksum Gradle publishes for 9.7.1) |
| Kotlin Compose plugin | 2.4.20 (raised from 2.2.21 in v5.3, same reason) |
| Kotlin | AGP built-in, driven by the Compose plugin version above -- 2.4.20 (the `org.jetbrains.kotlin.android` plugin is intentionally not applied) |
| `compileSdk` | 37 |
| `targetSdk` | **37** (raised from 36 in v4.0) |
| `minSdk` | 26 |
| Java compatibility | 17 |

**`compileSdk` and `targetSdk` are both 37 as of v4.0**, and the distinction is
still worth knowing because they were one apart for six releases and for a reason.
`compileSdk 37` says only which `android.jar` the code links against; it is what
`androidx.core 1.19` and the Compose `1.12` line require (`minCompileSdk=37` in
their AAR metadata) and it changes nothing about how the app runs. **The platform's
behaviour gates read `targetSdk`**, which is why it was held at 36 through the
Phase 2 dependency upgrade -- so that the upgrade could not move the app's
behaviour -- and why raising it was its own release with its own assessment and
device pass rather than a line changed in passing. That assessment went through every Android 17
behaviour change against this app's real code and required no fix. `lint` is the check that the flag actually
took: `OldTargetApi` exists precisely because the target lags the compile SDK, and
it is gone.

Dependencies are declared as hardcoded version strings; there is no Gradle
version catalog. They were brought to the current stable line in the Phase 2
upgrade (Compose BOM `2026.08.00`, `core-ktx 1.19.0`, `appcompat 1.8.0`,
`lifecycle 2.11.0`, `activity-compose 1.13.0`, `datastore-preferences 1.2.1`,
`coroutines 1.11.0`). Nothing is on an alpha, beta or rc.

### Build types

Four, and the fourth is the one worth explaining.

| Build type | What it is |
|---|---|
| `release` | R8 on, resources shrunk, not debuggable. Signed only if the `PAPERSCRAPE_RELEASE_*` environment variables are present — deliberately left **unsigned and uninstallable** rather than silently falling back to something that looks shippable. This is what CI publishes. |
| `debug` | R8 off, debuggable, `.debug` application id suffix, signed with the committed `debug.keystore`. What the instrumented suite runs against. |
| `perf` | `initWith(release)` — so R8 and shrinking are on and it is not debuggable — with the debug signing config and the `.debug` suffix. **Committed since v4.27**, and never published. |
| *(androidTest)* | Not a build type: the instrumented APK, built from `debug`. |

**Why `perf` exists.** The item-32 CPU protocol has to be measured on something users would run. A
debug build is not that — v4.26 spent three rounds of concept work on a figure ("+4.5 points of CPU
for every PNG substituted") that turned out to be a property of the debug build rather than of the
artwork — and a real release build cannot be signed on a development machine, because the release
key exists only on the maintainer's. `perf` is the intersection: release-like code, debug signature.

**Why it is committed.** Until v4.27 it was written by hand each measuring session and deleted
afterwards. In v4.25 the deletion did not happen and four lines of build configuration reached the
delivery ZIP and the published tag. The maintainer's decision in v4.27 was that a rule enforced by
remembering fails on the session that forgets, and that two sessions measuring the same thing should
be measuring the same binary.

**What keeps committing it safe.** `BuildTypeDeclarationTest` pins the declared set of build types,
pins `perf` to `initWith(release)` + the debug signing config + `.debug` + `isDebuggable = false`,
and asserts that no workflow builds it. The release APK was built with and without the block in
v4.27 and compared entry by entry; the result is in that release's report.

### Workflows

`.github/workflows/android-build.yml`
- `build` job on every push and PR: lint, unit tests, `assembleDebug`, artifact
  upload. Never sees release secrets.
- `release` job, only on a pushed `v*` tag -- never on a merge to `main`: checks
  required secrets, decodes the keystore to a runner temp path, builds a signed
  release APK, emits a SHA-256 checksum, produces a Sigstore build-provenance
  attestation, refuses to overwrite an existing release, composes the body from
  `release-notes/<tag>.md`, and publishes. The tag is validated against
  `versionName`, not `versionCode` -- this paragraph said `versionCode` and was
  describing a rule the workflow had already stopped enforcing.

There is **no third job**. An `instrumented` emulator job existed from v3.2 to v3.5 and was
removed in v3.6: it ran on hosted runners repeatedly and never once produced a signal about this
app's code — every failure was environmental, and each was a different environment (a missing SDK
package, a device not yet able to install, and finally a shell syntax error inside the action's own
wrapper). On its last run its diagnostics step hung until the job timed out, so it could not even
upload the evidence. The rule written out of it is that an auxiliary job may not gate anything
until it has passed on its own for a stated run of releases, and that bounding a diagnostic's exit
status is not the same as bounding its time — a step that cannot fail can still hang. **The instrumented tests themselves were not removed** — see *Testing* above.

Neither workflow needed a change for the Phase 2 upgrade, and neither needed one
for the v5.3 dependency round either. JDK 17 still builds
AGP 9.4.0 / Gradle 9.7.1 (checked locally on a Temurin 17 that matches the
`setup-java` step, not inferred), the wrapper jar matches the SHA-256 Gradle
publishes for 9.7.1 so wrapper validation still passes, and `compileSdk 37`
needs nothing installed: the `ubuntu-latest` runner image already ships
`android-37.0` alongside `android-36`, and build-tools 36.0.0, which is what
AGP 9.4.0 selects by default.

`.github/workflows/dependency-submission.yml`
- Submits the resolved dependency graph on push and weekly, feeding Dependabot
  alerts. Note: this raises alerts but does **not** open update PRs; there is no
  `dependabot.yml`.

All actions are pinned to full commit SHAs. `gradle/actions` is deliberately
held at v5.x for licensing reasons documented inline.

### Dependency security

**Every Dependabot alert this repository has ever carried has been against a build-time
dependency, never against anything in the APK.** At v5.2 there were 49 open, all attributed to
`settings.gradle.kts` -- a file that declares no dependency at all, only repositories. The
attribution is an artefact of how the workflow above reports: it submits the *resolved graph of
the build*, and GitHub labels the whole graph with the settings file. Reading the label as a
location will send you to eighteen lines of repository declarations and no further.

What the graph actually contained was the Android Gradle Plugin's own transitive closure. The
app's declared dependencies are androidx, Compose, DataStore and coroutines, plus JUnit and
`org.json` for tests; `:app:dependencies` shows zero alerted coordinates on
`releaseRuntimeClasspath`, `debugRuntimeClasspath` or `releaseCompileClasspath`. That does not
make the alerts fake -- this code runs on developer machines and CI runners with the checkout in
front of it -- but it does mean the severity of a *shipped* vulnerability never applied.

v5.3 closed all 49 by raising versions, on the maintainer's explicit instruction not to close any
of them by narrowing what the workflow submits, and not by dismissing them:

- **41 Netty alerts** came from eleven `unified-test-platform-*` configurations AGP 9.3.1 created
  on `:app`, two of which pulled `io.grpc:grpc-netty` and with it Netty 4.1.93 and 4.1.110. AGP
  9.4.0 collapses all eleven into one configuration that resolves neither, so there was no
  version of ours to raise -- the newer AGP is the fix.
- **The remaining 8** are forced, in two places that are easy to mistake for one. The root
  `build.gradle.kts` forces the **plugin classpath**; `app/build.gradle.kts` forces this
  project's own configurations, of which `androidLintTool` is the one that mattered. Four alerts
  survived on `androidLintTool` alone after the root force landed, and one of them
  (`httpclient`) looked already fixed on the plugin classpath because ordinary conflict
  resolution had lifted it there. **`buildEnvironment` alone cannot tell you this round is
  finished; `:app:dependencies` can.**

The check that says whether it is still true is not a document. Resolve the graph and scan every
coordinate it contains -- the OSV API answers unauthenticated, and the GitHub Advisory Database
is what it mirrors:

```bash
./gradlew --no-daemon buildEnvironment :app:dependencies
# then query https://api.osv.dev/v1/querybatch for each resolved group:artifact:version
```

Reconstructed that way at v5.2 the scan returned exactly the 49 alerts GitHub showed, with the
same per-package split and the same 2 critical / 19 high / 26 moderate / 2 low severities; after
v5.3 it returns zero, against the whole graph rather than only the 49.

### Verified build

A build is an event, so this is the record of one run, not a property of the tree.
**Do not quote the numbers below as current** — reproduce them:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
python3 -c "
import xml.etree.ElementTree as ET, glob
t=f=e=s=0
for x in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(x).getroot()
    t+=int(r.get('tests')); f+=int(r.get('failures')); e+=int(r.get('errors')); s+=int(r.get('skipped'))
print(t,'tests,',f,'failures,',e,'errors,',s,'skipped')"
python3 -c "import xml.etree.ElementTree as ET,collections;print(collections.Counter(i.get('id') for i in ET.parse('app/build/reports/lint-results-debug.xml').getroot().findall('issue')))"
```

**Last run: v5.1, 2026-09-14**, JDK 17, AGP 9.3.1, Gradle 9.7.1, 4 m 59 s on a four-core Linux
host with a warm dependency cache, `--rerun-tasks` so nothing is answered from the build cache,
from a clean extraction of the delivery archive.

**Every figure in this block was wrong before this run, not only the one that was reported.** It
had been carrying v4.24's numbers since 2026-09-07 — eight releases — and the block *was* dated,
which made the staleness checkable and did not prevent it. Measured against v5.0, before this
round added anything: the test count was out by **63**, the lint count by **16**, the compiler
warnings by **2**, and only the APK size was close. **Re-measure and re-date the whole block rather
than correcting one line of it**: a uniformly old block reads as old, a mixed one does not.

| Task | Result |
|---|---|
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL**, `app-debug.apk` 22 669 106 B (21.62 MiB) |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** — 1452 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew lintDebug` | **BUILD SUCCESSFUL** — 45 issues: 42 warnings, 3 hints, 0 errors, 0 fatal |
| Kotlin compiler warnings | **21** |
| instrumented suite, BV6600 | **OK (171 tests)** in 2 934.3 s |

Lint breakdown: `UnusedResources` ×32, `UseKtx` ×4, `AutoboxingStateCreation` ×3, plus single
instances of `UnusedAttribute`, `VectorRaster`, `GradleDependency`,
`ConfigurationScreenWidthHeight`, `DataExtractionRules` and `ObsoleteSdkInt`. `OldTargetApi` is not
among them: it fires only while `targetSdk` lags `compileSdk`, and since v4.0 both are 37. The
`UnusedResources` count grew with the neighbourhood redraw, which left drawables behind; that is
recorded here rather than silenced.

The twenty-one compiler warnings are five groups, all pre-existing: eleven `Java type mismatch:
inferred type is 'Nothing?', but 'String' was
expected` and three of the same against `File`, which are `org.json`'s platform types read
through Kotlin's nullability; four deprecations of `TRIM_MEMORY_RUNNING_LOW` and
`TRIM_MEMORY_RUNNING_CRITICAL`; and one `Condition is always 'true'`. They are recorded rather
than silenced — an earlier version of this table claimed **0**, which had stopped being true
without anybody noticing, because nothing reads the warnings when the exit code is zero. Closing
them is an open backlog item, not a claim already met.

### Testing

There are **two layers**, and the split is deliberate: what can be answered without a device is
answered without one.

**JVM tests** live in `app/src/test/kotlin/`, mirroring the main source package layout. They are
plain JVM tests: every class currently under test has zero Android imports, and where a class does
hold Android types the *testable half* is split out into one that does not — `IntLruSlots` under
`TintFilterCache`, `IntKeyLruSlots` under `GradientShaderCache`, `SpriteCacheIndex` under
`SpriteCache`, `SceneTransform` and `SceneShape` under the backends. **These are the tests CI
runs.**

**Instrumented tests** live in `app/src/androidTest/kotlin/` and need a device. Since v3.6 **CI does
not run them** — see *Workflows* below for why the emulator job was removed — so they are run
locally against an Android 17 emulator before a release. They are not optional and not decorative:
they are the only thing in the project that looks at a rendered frame.

The table below is the JVM layer; the instrumented layer follows it.

| Test class | Covers |
|---|---|
| `CloudCoverageTest` | The coverage field (falloff shape, saturation, edge clamping, frame reset) and the rain-follows-cloud rule: no rain from clear sky, uniform fallback when clouds are hidden, overcast reproduces the previous drop set, no drop displacement when cloud cover changes |
| `CandidateSystemTest` | The ten candidate-system invariants: determinism per theme, density-independent attributes, stability of survivors across density, monotonicity in both directions, independence from filtered-out candidates, effect decorrelation, the small-pool guarantee, and distribution quality |
| `MemoryPressurePolicyTest` | Trim-level mapping, including that `TRIM_MEMORY_UI_HIDDEN` never evicts despite its numeric value exceeding `TRIM_MEMORY_RUNNING_CRITICAL`; unknown-level handling; mirrored constants match the platform |
| `SpriteCacheIndexTest` | Cache bookkeeping: byte accounting, LRU eviction order, eviction to a byte budget, slot reuse, growth, repeated fill/release cycles |
| `SceneTimeTest` | Bounded time base: accumulation past the 12.14-day Float freeze point, range and smoothness of every helper at one-day/twelve-day/one-year uptime, cycle continuity across wraps, walk-frame ordering, absence of NaN/infinity |
| `SliderDragStateTest` | Slider drag handover: thumb tracks the finger, exactly one commit per drag, no commit when a drag returns to its origin, no snap-back while a write is in flight, correct ordering for two rapid drags |
| `SceneCustomizationStructureTest` | Structural vs cosmetic classification for all 12 categories, the static/car separation that keeps cars running, and a reflection guard that fails if a new category is added without updating the comparison |
| `IntLruSlotsTest` | Bounded LRU slot allocation: capacity is never exceeded under a continuous stream of new keys, exact LRU eviction order, slot recycling, hot-key retention |
| `SceneObjectCullingTest` | Off-screen culling: no early clipping at either edge, continuous visibility while scrolling, and an explicit comparison against the v73 `-200f/3000f` behaviour it replaced |
| `SceneObjectTileCullingTest` | Tile enumeration: bit-exact equality with the fixed three-copy loop over 76,608 swept cases, agreement with a brute-force scan of offsets -40..+40, the `floor` start-offset contract, inclusive behaviour at both edges, exact tile boundaries, degenerate tile widths, out-of-range anchors, and that two copies of one object can never overlap |
| `SunPositionCalculatorTest` | Day/night classification, `progress` and `dayBlend` contracts, the celestial arc, sunrise/sunset approximation (equinox day length, hemispheric asymmetry, polar clamping, longitude offset), moon phase cycling, and the clock reading that replaced a per-frame `Calendar` — pinned against that `Calendar` at tolerance `0f` across eight time zones, a year of non-hour-aligned samples, and pre-epoch instants |
| `SeasonalThemeRulesTest` | Computus against published Easter dates 1900–2100, the Sunday and 22 Mar–25 Apr invariants across 1900–2200, window boundaries and precedence, and that every rule resolves to an id present in `ThemeCatalog` |
| `SeasonalCalendarIdentityTest` | **The gate for the v5.1 rewrite.** Transcribes the v5.0 table and walks it against the factory calendar for fifteen years, allowing exactly one difference — 1 March — and asserting it is present in every year and is `winter`→`spring`. Also walks the continuous autumn against a split one for ten years, and re-measures v5.0's 69 uncovered days so the reason for the change survives as a number |
| `SeasonalCalendarStorageTest` | The calendar as a document: that the factory calendar stores nothing, that an edit back to a factory value stops being an override, the JSON round trip (wrapping spans and 29 February included), unreadable and partly-unknown documents falling back whole rather than in part, the same-tier overlap gate, Easter's exemption from it, and that a gap costs the day rather than breaking the calendar |
| `CustomThemeDataJsonTest` | Serialisation round trips (including all built-in themes), schema versioning and legacy compatibility, and defensive parsing of corrupt input |
| `IntKeyLruSlotsTest` | The multi-component key table `GradientShaderCache` runs on: exactness (a difference in *any* of the five components, including the zero padding, must miss), the capacity bound under a continuous stream of new keys, exact LRU order, slot recycling, and that two floats one ULP apart are distinct keys |
| `SolarDayPublicationTest` | **P2-6.** That three separately-published fields can be read half-updated — demonstrated deterministically with a barrier, and with the fields already `@Volatile`, so it is a statement about the shape and not about a missing annotation — and that one immutable snapshot behind one `@Volatile` cannot be, under the identical interleaving and under 200 000 unsynchronised sampled reads |
| `RoadVehicleGeometryTest` | **Filone B.** The road/vehicle ratios measured from `SceneSpace`'s own constants: lanes about one vehicle apart, the carriageway between 1.5 and 4 car-heights deep, the fire engine fitting inside it, the strip symmetric about the lane pair, and a degenerate lane pair still painting a full-width road |
| `CacheLifecycleTest` | **Filone F.** The memory bound of every cache in the render path, which is what the "no `onTrimMemory` needed" verdict rests on: both key tables bounded whatever they are fed, the gradient cache's bookkeeping under a kilobyte, and `SpriteCacheIndex` accounting for megabytes of pixels it does not hold and releasing them on `clear()` |
| `PreviewRendererAgreementTest` | **Filone C.** That the gallery preview and the wallpaper place a tree's parts identically — the placement count the test prints for itself, across every theme — after an audit found the snow cap's hand-copied offset had drifted from the renderer's |
| `WeatherApiComProviderTest` | The second weather provider: every one of the 60 published condition codes resolves, and resolves to the right *side* (frozen / liquid / thunder / obscuring) as judged against the official English text, walked from the committed `conditions.json`; parsing of a full response, a sparse one, an error body and a snow code; and that a blank key makes no request |
| `WeatherProviderSelectionTest` | **That Open-Meteo is the default**, that the default needs no key, that an install which had chosen the removed provider falls back to it, and that switching provider disturbs no other weather setting |

One non-obvious dependency: `org.json` ships inside the Android framework, so
under local unit tests it resolves against the *mockable* `android.jar` where
every method is stubbed. `testImplementation("org.json:json:…")` puts a real
implementation ahead of the stub on the unit test classpath. It is test-only
and never packaged; on device the app still uses the platform implementation.
Android's bundled `org.json` is Harmony-derived and not byte-identical to the
reference implementation, so these tests should not be treated as proof of
exotic edge-case parsing behaviour on device.

`testOptions.unitTests` enables full test logging so CI failures show assertion
messages and stack traces rather than only a path to a report that does not
survive the runner. `isReturnDefaultValues` is deliberately **not** enabled:
if a test needs a stubbed framework call, that indicates the class under test
has the wrong dependencies.

`SeasonalThemeRules.computeEasterSunday` is `internal` rather than `private`
solely so it can be asserted against known dates directly — testing it only
through `themeForDate` would not catch an off-by-one, since the Easter window
spans three days either side.

#### The instrumented layer

| Suite | Covers |
|---|---|
| `SceneGoldenTest` | Committed PNGs rendered through `CanvasSceneTarget` — the backend that ships, not a test double — and compared per pixel. `GoldenScene` describes each frame as data so that when one changes, "did the scene change or did the drawing change" is answerable. `GoldenFocus` re-checks named patches on their own much smaller area, because 0.2% of a 360x800 frame is 576 pixels and a dolphin covers 160. **Do not quote a count here** — the figure that means anything is the number of `assertMatches` calls, not the number of files in the directory. `grep -rh 'SceneGolden\.assertMatches' app/src/androidTest --include='*.kt' | grep -vc '^\s*\*'` counts them, and **its answer is one too high**: `LightningPinTest` hands `assertMatches` a scene it must *reject*, which is how the guard is shown to run rather than merely to exist. Subtract it. An assertion is also not a file — two scenes are each asserted more than once with different focus rectangles, so the assertion count and the PNG count are different numbers and neither is "the number of goldens" on its own. |
| `GlSceneGoldenTest` | Three of the same scenes rendered through the shipped `GlSceneTarget` on an offscreen EGL pbuffer, configured exactly as `GlRenderThread` configures it, MSAA included. Three gates: against its own committed `gl-*.png`, against the Canvas golden (the claim that the two backends still draw the same picture), and — since v3.7 — **against a named region**. |
| `PrefsCorruptionRecoveryTest` | That a damaged preferences file costs that store its contents and nothing else, including across a process restart. |
| `CanvasGradientAllocationTest` | **P2-5.** Records the full argument tuple of every gradient the real renderer asks for over 60 animated frames, and checks the cache builds one `Shader` per *distinct* gradient rather than one per request. |
| `TrafficGoldenTest` | **v3.8.** That the two traffic goldens actually contain traffic, measured off the finished frame by `VehiclePresence` rather than inferred, that both lanes are occupied, that the frame is bit-identical across two renders, and that three plausible traffic regressions each move more of the frame than the golden's own budget. |
| `TreeArtworkAlignmentTest` | **v3.8.** That the winter tree's snow cap lands entirely on the crown — 0 of 17 182 opaque pixels off it — which disproves v3.7's report of a 3-unit misalignment. An assertion about the *artwork*, which nothing else checks. |
| `SkyWaterGoldenTest` | **v4.26**, and it closes a backlog item raised in v4.25. Three derived gates — the cloud band, the bird band and the water band — attached as `extraFocus` to golden scenes that already exist, so it adds assertions and no committed PNG. Two of the three are the item's own complaint made concrete: every bird disappearing moves **1.52%** of its rectangle and every dolphin disappearing **0.21%**, both *under* the shared 2% focus limit, so before this the whole family could vanish and the suite would have passed. Each signal is re-measured on every run. |
| `LakeDrawCallTest` | **v4.26.** Counts every primitive the real renderer asks for, through the real `SceneCanvas`, with the water on and with it off. The difference is the water's own per-frame cost, measured rather than estimated. |

**`GoldenScene.warmUpFrames` is the v3.8 addition.** A car's `progress` starts negative and only
advances inside `SceneObjectRenderer.update(deltaSeconds)`, so a golden drawn as one frame with
`deltaSeconds = 0` could never contain one — and for seventeen releases none did. Two scenes now
warm up 390 frames (thirteen seconds at 30 fps, the count chosen by measuring vehicle coverage from
0 to 600) before the frame that is compared, which puts four vehicles in the band with none clipped
by a frame edge. Warm-up is deterministic because the clock and the delta are pure inputs. Every
pre-v3.8 scene warms up zero frames and regenerates byte-identical.

**The exception is a storm, and it went unnoticed for four releases.** The lightning timer is the
only unseeded `Random` in the renderer, and `updateLightning` leaves it alone unless a storm is
active — so the rule was that a warmed-up scene must not be a storm. It was written in
`GoldenScene`'s own KDoc, which said in the same breath that the harness had no way to check it, and
`SceneGoldenTest.waveStorm` broke it from v4.28: 320 frames at 0.25 s with a thunderstorm running,
one frame per strike drawing a full-screen veil, a strike interval averaging 32 frames, and
therefore **about one run in 32 failing by the entire frame**. Since **v5.0** the rule is
`GoldenScene.requireDeterministicLightning`, run by both harnesses before they render, and a scene
that needs to warm a storm up says so with `GoldenScene.pinLightning`, which clears
`PaperRenderer.lightningStrikesEnabled` **for that render alone**. The wallpaper's own lightning is
untouched: nothing in `src/main` writes that flag, and v5.0 Fase 0 measured the strike cadence and
flash intensity on v4.31's production build and on this one to say so rather than assume it.

**The wall clock is the v5.4G addition, and it is the same shape of hole one level up.** A golden
is a claim that a frame is a function of the scene written beside it, and everything the harness
pins it pins by *passing* — theme, customisation, scene clock, day phase, scroll, lightning. The
one input the renderer could reach without being handed it was the time of day:
`PaperRenderer.drawMoonWithPhase` called `SunPositionCalculator.moonPhase()` while painting, whose
default argument is `System.currentTimeMillis()`, so the moon in a pinned frame was chosen by the
phone. The phase is discrete — four silhouettes on thresholds of the illuminated fraction, the
waning half reusing the waxing shapes rotated 180° — so the leak showed nothing for days and then
moved 17 pixels of `night` and 39 of `shops-closed-night` the evening the real moon crossed from
crescent to half. The phase now travels in `SunPositionCalculator.DayPhase.moonPhase`, the
wallpaper service fills it in from the real moon, and a caller that names none gets
`SunPositionCalculator.FIXED_MOON_PHASE`. Two checks keep it there:
`SceneGolden.assertReproducesOverTime` renders every Canvas golden a second time with the device's
wall clock moved 191 days (`DeviceClock`, through `cmd alarm set-time`) and requires the two frames
to be identical pixel for pixel, and `RenderPathReadsNoWallClockTest` refuses a clock read in
`PaperRenderer.kt`, `SceneObjectRenderer.kt` or `SunPositionCalculator.compute` at all. The
behavioural one is the one that catches a leak; the source rule is what covers a leak in a theme or
a weather no golden renders.

**The region gate is the v3.7 addition, and it exists because the whole-frame gates provably could
not see one class of regression.** Driver-to-driver disagreement is *spread* — it is anti-aliased
edges, and there are edges everywhere — while a regression in one effect is *concentrated*. Divided
by the whole frame the two are indistinguishable; divided by the effect's own bounding box they are
two orders of magnitude apart. Measured inside the sun's glow at a channel delta of 4: two
genuinely different GL drivers differ by 0.051%, reducing the glow's triangle fan to a triangle
differs by 7.02%, and halving its intensity by 2.71%. Both of those pass every whole-frame gate.
The limit is 0.50%. See `GlGolden.Tolerance` for the full table.

### Environment requirements

Building requires a full JDK (17 recommended, matching CI), the Android SDK
with platform 37 (Android 17) and build-tools 36, and network access to Google
Maven and Maven Central. Platform 37 is what `compileSdk` links against;
build-tools stays at 36.0.0, which is what AGP 9.4.0 selects by default. `README.md`'s *Build*
section has the minimal setup.

**An Android 17 emulator is also required to release**, because the instrumented layer above is not
run by CI and a release is not verified without it. Two GL drivers are worth having available:
`swiftshader_indirect`, the software rasteriser the committed GL goldens were taken under, and the
host-GPU translator — v3.7's region thresholds were set by measuring the same frame under both, and
that comparison is the only way to tell a driver difference from a regression.

---

## 9. Known architectural weaknesses

Recorded here so they are not rediscovered from scratch. Which of them gets worked on, and in
what order, is decided outside this document.

1. **Partial source pipeline for assets.** `tools/assets/` (Phase 3.1) gives 24
   of the sprites an SVG source and a deterministic rasterisation path; the other
   94 remain their own source. Phase 3.2 declared the bounding boxes and Phase 3.3
   removed the padding, so what remains of the downstream consequences is 16
   duplicate groups and the hand-tuned anchors — the anchors being the one that
   still blocks Group 4.
2. **No single scene-space model.** Four multiplicative scale factors, two
   sprite conventions, geometry constants spread across three classes. Produces
   recurring per-asset size/alignment patches.
3. ~~**Per-frame recomputation.**~~ Resolved in Phase 2.1/2.2: effect
   candidates are addressed by index rather than read from a per-frame `Random`.
4. ~~**RNG stream coupled to the density filter.**~~ Resolved in Phase 2.1/2.2.
5. ~~**Unbounded `Float` time base.**~~ Resolved in Phase 1.5: `SceneTime`
   accumulates in `Double` and bounds at the point of use; `scrollProgress`
   narrows only its wrapped per-layer result.
6. ~~**Configuration change rebuilds all scene state.**~~ Resolved in Phase 1.4:
   the write path commits once per drag, and configuration changes are applied
   in place unless the set of rendered objects actually changed.
7. ~~**`SpriteCache` never releases.**~~ Resolved in Phase 1.6: tiered
   `onTrimMemory` response plus LRU eviction. The transparent padding inside those
   bitmaps was 18.3 MB and is now 2.15 MB (Phase 3.3), so the cache holds roughly
   half of what it used to for the same scene.
7b. ~~**`Shader` allocation in the Canvas draw path.**~~ Resolved in v3.6 (**P2-5**):
   `GradientShaderCache` reuses gradient shaders instead of building one per call. Measured at 180
   objects over 60 frames for 3 distinct gradients; now 3.
7c. ~~**Three scene fields shared across threads without synchronisation.**~~ Resolved in v3.6
   (**P2-6**): sunrise, sunset and the has-fix flag are one immutable `SolarDay` behind a single
   `@Volatile`, so a frame cannot mix two locations' days.
8. **People are outside the scene systems.** Fixed screen-height anchor, fixed
   scale, no depth scaling, no ground anchoring, no road awareness, no
   visibility or density control.
9. ~~**Three tile copies are still evaluated per object.**~~ Resolved in Phase
   2.4: the copy range is derived from the tiling period and the object's own
   extent instead of being a fixed `-1..1`, and the per-object setup the cull
   depends on is computed once rather than per copy.
10. **Test coverage is narrow, but less so than this entry used to claim.** The JVM suite
    covers the pure deterministic logic, and the sentence that stood here for many releases —
    *"no automated test in this project observes a rendered frame on either backend"* — has been
    false since v3.2: the Canvas goldens and the three GL references do
    exactly that, and v3.7
    added a region-targeted GL gate; v4.22 added derived per-focus gates on the settings scenes;
    v4.23 added `halloween-moon`, the first committed frame in which a celestial body is drawn
    clear of the cloud band.
    (The Canvas figure is the number of Canvas *assertions* --
    `SceneGoldenTest`, `PeopleGoldenTest` and `SettingsGateScenesTest` -- not the number of PNGs in
    `androidTest/assets/golden/`, which also holds the three `gl-*.png`. Counting the directory is
    how the handover notes came to say 30; v4.21 corrected it and added `GoldenUniquenessTest`.) What remains true is the shape of the gap. The engine lifecycle, the
    preferences layer and the Compose UI are still untested and still cannot be unit tested
    without being decoupled from `Canvas` and `Context` first, which is deferred item **B5**.
    v4.23 narrowed one corner of that and no more: the settings screens' *derivations* are pure and
    unit-tested (`SettingsUiModel`, and `MoonPhaseControlTest` for the Halloween override), and the
    call sites that consume them are pinned by reading the source, because there is no
    `createComposeRule` in this tree and nothing composes a screen in a test. **No composable is
    rendered by any test**, so the gap this entry describes is unchanged in kind.
    Two narrower gaps worth naming, both found in v3.7 and neither scheduled:
    **no golden contains a vehicle** (car `progress` starts negative and the goldens render one
    frame with `deltaSeconds = 0`, so no car has entered the frame), and the preview/renderer
    sprite-offset agreement is pinned for the tree only — the other 55 shared sprites were checked
    by hand once and nothing guards them.
11. **The atlas cannot reclaim space.** Shelf packing wastes area against a real bin
    packer and has no way to free a single entry; it is only ever added to, and reset
    wholesale. It also fills in first-draw order, so a scene whose sprite set exceeds
    2048² pushes its *later* sprites — the objects and people, which benefit most —
    out to standalone textures. Neither has been observed to matter, and neither is
    worth fixing before it does.
12. **Each engine has its own EGL context**, so the picker's preview engine and the
    live engine do not share textures the way they share `SpriteCache`'s bitmaps.
    Whether that costs enough VRAM to matter is unmeasured.
13. **No localisation.** Almost every UI string is a literal in Compose rather than a
    `strings.xml` entry. "Zero `stringResource` usages" stood here until v5.1 and there are
    now a handful; the decision (English-only, `ROADMAP.md` Deferred) is unchanged, the count
    is not worth keeping — `grep -rc stringResource app/src/main --include='*.kt'` answers it.
14. **Incomplete Material 3 colour scheme.** Four roles defined out of ~30; the
    rest fall back to Material's baseline palette. `themes.xml` still inherits
    from a framework Material 1 theme.
