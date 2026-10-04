# ARCHITECTURE.md

Technical description of PaperScrape as it exists today. This document
describes the **current** implementation, including its known weaknesses.
Planned work and visual design decisions are deliberately **not** in here: this file is limited to the
implementation as it stands.

It is kept by hand: where this text and the source disagree, the source is right.

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
│       │   ├── Bounded.kt       the bounded read for downloaded text and imported documents
│       │   ├── engine/          rendering, scene model, themes, effects
│       │   ├── icon/            the seasonal launcher icon
│       │   ├── prefs/           DataStore persistence
│       │   ├── location/        optional location: GPS, network/cell, or custom
│       │   ├── weather/         optional live weather
│       │   ├── update/          in-app update check
│       │   └── ui/              Compose settings screen
│       └── res/
│           ├── drawable/        vector launcher icons + wallpaper thumbnail
│           ├── drawable-nodpi/  sprite PNGs
│           ├── mipmap-anydpi/   adaptive launcher icons, one per launcher alias
│           ├── values/          strings, colors, themes
│           └── xml/             wallpaper, backup rules, update file paths
├── tools/assets/                offline asset source pipeline (not part of the build)
├── .github/workflows/           CI
├── gradle/wrapper/
├── release-notes/               one file per release tag
├── scripts/                     release keystore helper
└── debug.keystore               deliberately committed: it signs the debug and perf builds
```

### Size

**No counts are written here**: the tree answers them.

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
byte-identical pair** (`validate` and `SpriteVariantTest` both fail on one).

---

## 2. Main components

### `engine/`

| File | Responsibility |
|---|---|
| `PaperWallpaperService.kt` | `WallpaperService` + inner `PaperEngine`. Owns the render thread, the `Canvas` fallback loop, surface lifecycle, preference collection, location and weather refresh. Holds the Live Weather loop: a two-minute check tick that only fetches once an hour, unless an input in `LiveWeatherInputs` changed or the location did; the tick comes sooner when a try for the phone's position falls due (`SolarDaySchedule.nextPassDelayMillis`). It also records, once, that the home screen has moved the wallpaper with a swipe (`SwipeReport`, `onOffsetsChanged`), which *Swipe scroll* waits for. |
| `WallpaperEngineCensus.kt` | Whether this process is drawing the phone's wallpaper: its engines that are not a picker's preview, counted. The settings screen's top button reads it to say "PaperScrape is your wallpaper". Counted from the engines rather than asked of `WallpaperManager.getWallpaperInfo()`, which names PaperScrape as soon as the system has opened a connection, engine or not (after a force-stop, over a blank home screen). |
| `PaperRenderer.kt` | Draws sky, stars, sun/moon, clouds, precipitation, rainbow, mountains, hills, lake and its decorations, birds, falling leaves. Owns scroll/parallax state and the depth mapping constants. |
| `SceneObjectRenderer.kt` | Draws ground-anchored scene objects (houses, buildings, trees, parasols, seasonal decorations), the road, cars and people. |
| `SpriteBlitter.kt` | The single sprite-blitting path, shared by both renderers, plus the `SpriteScale` convention selector and the one definition of `SPRITE_PIXELS_PER_UNIT`. |
| `SceneCanvas.kt` | The drawing interface both renderers target, plus `SceneShape`, the closed polygon drawn in place of `Path`. |
| `CanvasSceneTarget.kt` | `SceneCanvas` over `android.graphics.Canvas`: the settings preview and the EGL fallback. Owns a `GradientShaderCache`, so its three gradient entry points reuse shaders instead of building one per call. |
| `GlSceneTarget.kt` | `SceneCanvas` over OpenGL ES 2.0: transform stack, tessellation, batching. |
| `GlSpriteProgram.kt` | The one shader program; sprites and flat fills share it. |
| `GlTextureCache.kt` | Drawable resource id → texture handle, UV rectangle and pixel size. Routes each sprite to the atlas or to a texture of its own. A blit finds its entry through `SpriteEntryIndex`. |
| `GlTextureAtlas.kt` | The shared atlas texture and its uploads. |
| `AtlasPacker.kt` | Where each entry sits in the atlas, as pure testable arithmetic: a skyline packer. |
| `GlRenderThread.kt` | EGL context and surface lifecycle, the render loop, and the cross-thread event queue. |
| `SceneTransform.kt` | The `save`/`restore`/`translate`/`scale`/`rotate` arithmetic, as pure testable code. |
| `SceneObject.kt` | Scene object data model (`StaticSceneObject`, `CarObject`, `SceneObjectLayout`) and `SceneObjectCatalog`, which generates candidate slots per category. The twelve built-in streets place their three shops together, so no spot of the street holds the same shop on more than a few themes; saved and mixed themes are not part of it. The plan (`planShopPositions`) is not computed when the app starts: `ShopPlanTable.kt` holds its answer. |
| `SceneTheme.kt` | Theme data model and built-in theme catalog. |
| `SceneCustomization.kt` | Per-category visibility/density/colour configuration plus sky, stars, clouds, precipitation, rainbow, mountains, lake, birds config. |
| `LiveWeatherSceneRules.kt` | Which layer's settings win while Live Weather is active — clouds and the lightning flash. Pure, because the defect it prevents is not a wrong value in any one layer but the layers disagreeing: rain from an empty sky, or a flash over a dry scene. Three layers, one rule. |
| `StormAtmosphere.kt` | How much the weather darkens the scene, and what that darkening does to a colour. One pure `strength(...) -> 0..1` feeds sky darkening, cloud darkening and sun attenuation, so the three cannot disagree about how bad the weather is. `dim` pulls a colour toward its own Rec. 601 luminance and then down, which keeps the blend relative to the theme's palette rather than substituting a storm one. Applied *on top of* the day/night colour, so the two are orthogonal and combine. |
| `CloudBand.kt` | Where the cloud band sits and what hangs off it: the clouds, the rain's fall origin, and the lightning's origin. Pure, and separate, so all three are derived from one function and cannot drift apart: a copy of the arithmetic at each call site would let the bolts be born above the band instead of inside it. |
| `CustomThemeData.kt` | JSON (de)serialisation of custom themes and overrides. |
| `CustomThemeRegistry.kt` | Synchronous in-memory cache of custom themes, with a `generation()` counter used to detect changes. |
| `RandomSceneGenerator.kt` | Procedural theme/layout generation for the "Random" theme. |
| `SeasonalThemeRules.kt` | Date-based automatic theme selection (includes a Computus implementation for Easter). Reads its dates from `SeasonalCalendar` rather than holding them. |
| `SeasonalCalendar.kt` | The calendar as data: the eight `CalendarWindow`s, their factory spans, and the user's edits to them. Stores **only the difference** from the factory, so an untouched install has no document at all. |
| `SeasonalCalendarCoverage.kt` | Walks the year to find gaps and same-tier overlaps. One enumeration, shared by the settings screen's gate and by the tests. |
| `SunPositionCalculator.kt` | Day phase, sun/moon arc position, moon phase, simplified sunrise/sunset. |
| `FireworkEffect.kt`, `SantaSleighEffect.kt` | Self-contained timed effects. |
| `SceneSpace.kt` | **The one place the world's size is stated.** The horizon, the ground plane's projection, the road's lanes and edges, and every category's real height in metres against the local units its art occupies. Every base scale is derived here, so the ratios between objects cannot be edited one at a time. |
| `SceneTime.kt` | Scene time as a `@JvmInline value class` over `Double`, with every read bounded at the point of use. A `Float` accumulator would stop advancing after ~12 days of visible uptime. |
| `SwipeReport.kt` | Whether the home screen has moved the wallpaper with a swipe: the offset moving between pages (a step above 0) away from the first one an engine that is not a preview received. *Swipe scroll* reads on only after that (`WallpaperSettings.swipeReported`); many home screens never report a swipe, the BV6600's among them. Pure, JVM-tested. |
| `SolarDaySchedule.kt` | When the wallpaper's loop works the sunrise and sunset out again (`onTick`): a stale day recomputed from the held position; the phone asked for its position when a weather refresh is due, while no position from it is held, and when a try after a search that found nothing is due, held position or not; a phone position forgotten the moment the phone stops allowing it; nothing asked at a fixed hour, where the scene draws the default 6:00 and 20:00 (`sceneDay`). Pure, JVM-tested. |
| `SolarDay.kt` | Today's sunrise, sunset and whether they came from a real position, as one immutable value. Published through a single `@Volatile` reference on the engine so the render thread cannot read a sunrise from one location beside a sunset from another — which three separate fields, `@Volatile` or not, allow. |
| `LakeLanes.kt` | Which lane each lake decoration occupies and how deep it sits, so boats cannot share a line and a leaping dolphin sorts by where its body is rather than by the lane it left. The waves sort in the same pass, and every kind is keyed by where its own drawing meets the water (`visibleWaterline`). |
| `WaveTint.kt` | Where a wave's body and foam sit in luma, given the water under them. Pure arithmetic, so `WaveContrastTest` measures the same numbers the renderer draws: the cheaper carry for the direction, the foam always the lighter paper, and the gate that is a floor rather than a target. |
| `PedestrianCarry.kt` | Which walkers have an umbrella up and when that may change -- `CarSelection.offScreen`'s "only out of sight" rule taken over for people (`nextCarrying`); the rule itself, `wantsUmbrella`: in the rain every walker who can hold an umbrella holds one, with no share and no deal; and the canopy palette. |
| `CandidateNoise.kt` | The stable per-candidate pseudo-random values the stateless candidate model is built on: same slot, same value, every frame, with density thinning and colour-variant assignment deliberately drawn from uncorrelated streams. |
| `CloudCoverage.kt` | How many clouds a cover fraction means, shared by the theme's own setting and Live Weather's. |
| `PeopleDensity.kt` | How many pedestrians a density setting means, on the same pattern -- and the day/night crossfade model the car count borrows (`CarSelection.densityAt`): one "a crossfade, not a threshold" rule, two users. |
| `CarSelection.kt` | Which cars a density means: an explicit count from 1 to every slot, filled in an order whose every prefix has the largest minimum loop gap, seeded per theme, applied per frame against each runtime's stored rank and only ever off screen. |
| `BusinessHours.kt` | How open the shops, the school and the towers are at a scene hour: a toggle that defaults to bitwise-off, `open == close` as always-open, wraparound spans, and a boundary fade that is `SunPositionCalculator.smoothEdge`'s own twilight over the opening span. Runs on `DayPhase.hour24` -- the hour that moved the sun -- never a clock of its own. |
| `TreeSpriteLayout.kt` | Where a tree's trunk, crown, snow cap and bare branches sit, stated once for both the wallpaper renderer and the gallery preview, which builds its objects from the same sprites at the same offsets; `PalmSpriteLayout.kt` does the same for a palm's two parts. |
| `NeighbourhoodTable.kt` | **Generated** (`tools/assets/buildings/build_neighbourhood.py`): what each of the six building families is made of, as a list of slots, each holding the alternative pieces one instance may be dealt. A part is `FIXED` art, a `WALL_MASK`/`GLASS_MASK` weight summed at the blit, a `SNOW` layer, or a call-out (`LAMP`, `OCCUPANTS`) to a behaviour at the piece's own declared coordinates. |
| `SpriteOccluderTable.kt` | **Generated** (`tools/assets/build_occluder_table.py`): where the ink is in every drawing an occlusion box has to speak for — the three palm crowns, the oak's two, the parasol's procedural fan — as a content box in object units plus the drawing's fullest row and fullest column. The layout pass and `ShopFrontVisibilityTest` both read this and build their own rectangle from it; `SpriteOccluderTableFreshnessTest` re-measures it straight from the PNG so it cannot fall behind a redraw. |
| `NeighbourhoodComposer.kt` | Deals one building out of that table — one alternative and one repeat count per slot, from the object's own stable identity — and stacks the pieces bottom-up. Read by **both** things that draw a building, the wallpaper and the gallery card, so there is one composer and no copy. `Deal` is owned and reused by its caller, so a scene does not allocate a list per building per frame. |
| `SceneColour.kt` | The one blend the colour rules are built from: `ColorUtils.blendARGB`'s arithmetic without the framework call, so `colorFor` and `windowGlassColor` run on the host and the JVM suite can evaluate them. `SceneColourBlendTest` (instrumented) proves the two identical over a sweep. |
| `SpriteCache.kt` / `SpriteCacheIndex.kt` | The bitmap cache and its bookkeeping. The index is `SpriteCache`'s own `private val` — ids, byte counts and LRU order in `IntArray`s, deliberately free of Android types so the eviction logic is unit-testable, and cleared by the same `clear()` the memory-pressure path calls. |
| `MemoryPressurePolicy.kt` | What an `onTrimMemory` level means for a wallpaper, as a pure decision. Notably `TRIM_MEMORY_UI_HIDDEN` is *not* treated as pressure, though its numeric value sits above `RUNNING_CRITICAL`: for a wallpaper it only means the settings screen closed. |
| `TintFilterCache.kt` / `IntLruSlots.kt` | A bounded, exact-LRU cache of `PorterDuffColorFilter`s keyed by colour, so a tinted blit does not allocate a filter per sprite per frame. Global, and therefore `@Synchronized`; released on `RELEASE_ALL`. |
| `GradientShaderCache.kt` / `IntKeyLruSlots.kt` | The same pattern for gradient `Shader`s, with a multi-component key because a gradient is four or five numbers rather than one. Owned **per `CanvasSceneTarget`** rather than globally, so a draw call takes no monitor. `CanvasGradientAllocationTest` checks that it builds one `Shader` per distinct gradient. |
| `CircleTable.kt` | The cosine and sine of every angle a tessellated circle is cut at, 8 to 64 slices, built once with the loop's own expression: the same floats the loop computed, without a `cos`/`sin` per slice per frame. |
| `SpriteEntryIndex.kt` | `GlTextureCache`'s `(resId, level)` → entry lookup, an open-addressing hash in primitive arrays; `SpriteEntryIndexTest` holds it to the answers of a linear scan. |
| `WakeLatch.kt` | Where the render thread waits while hidden: a count bumped by every wake and read before the inputs, so no wake is lost and the park needs no deadline. |
| `PaintAlpha.kt` | `setAlphaWithoutAllocating`: `Paint.setAlpha` as a write of the colour's alpha byte, because Android 10's `setAlpha` allocates on every call. |
| `DayPhaseCache.kt` | The engine's day phase, recomputed only when the hour or the sun's times change; the moon's phase is written into the one it holds. |
| `FirstFrameGate.kt` | Holds an engine's first frame until its settings and saved themes have arrived, or 2 s. |
| `ShopPlanTable.kt` | **Generated** (`ShopPlanTableTest`): where the twelve built-in streets' shops stand, the shop plan's answer computed when the table is written instead of at every start. |
| `CloudCoverFade.kt` | How the sky gets from one cloud cover to the next without the change being a single frame: the cover itself moves at a fixed rate, and each cloud's opacity eases in or out. Both eases are linear, so they arrive and stop, and the first observation snaps, so a fresh engine and every golden draw the sky they were handed. |
| `GlLifecyclePolicy.kt` | The GL backend's lifecycle rules as pure decisions: one render thread per engine rather than per surface, how many times a working context may be rebuilt before the engine falls back to `Canvas`, and when a memory trim may run (only with a context current). |
| `PeopleColours.kt` | What colour each of a person's four regions wears, dealt afresh each time a walker crosses the scene and deterministic in theme, person and crossing. |
| `PeopleLayerTable.kt` | **Generated** (`tools/generate_people_layers.py`, which also writes the artwork): which drawables make up each person, the fixed art and one weight mask per colourable region. |
| `PrecipitationContrast.kt` | The rule that carries the rain's colour away from the sky it falls across (`standOffFromSky`), kept outside the renderer so the JVM test measures the function the renderer draws with. |
| `SilhouetteDeal.kt` | Which of its category's silhouettes each building slot was dealt: a stratified deal over the slots (`SeededBalance.rankOf`) rather than a hash per slot, so a street shows a spread of silhouettes rather than a clump. |

### Other packages

- `Bounded.kt` (the package root) — `readAtMost`, the one bounded read for every text the app
  downloads (the JSON replies, the release checksum) and every document it imports: past its cap it
  returns `null` rather than a truncated text, which every caller treats as "not something I can read".
- `icon/SeasonalIcon.kt` — the six launcher icons as an enum, the one place a `CalendarWindow` and
  an icon are tied together, and `SeasonalIconRules.iconForDate`, which is pure and JVM-tested.
- `icon/LauncherIconSwitch.kt` — the `PackageManager` side: reads the six components' enabled state
  and writes only what differs, always with `DONT_KILL_APP`.
- `icon/SeasonalIconController.kt` — when it looks: wallpaper start, `ACTION_DATE_CHANGED` and its
  two siblings through a code-registered receiver, and a settings change that moves the calendar.
  All three arrive on the main thread and the work is binder calls, so it owns one single-thread
  executor: off the wallpaper's main thread, and serialised rather than racing.
- `prefs/WallpaperPrefs.kt` — main DataStore store, exposes `settingsFlow`. Each reset of the settings
  screen is one function and takes what its page shows: `resetCategory` (the Trees page's
  takes the palms too), `resetDecorations` (Seasons & decorations, the snow and leaf piles included,
  the palms not), `resetAllCategories` (the theme's whole scene; Motion is every theme's and stays).
  An edit to a theme saved in the gallery starts from its saved look (`savedLookFor`).
- `prefs/CustomThemeStore.kt` — separate DataStore for custom themes/overrides.
- `prefs/PrefsRecovery.kt` — what every DataStore does when its file cannot be read: a corrupt file
  is rewritten empty, once, and only that store falls back to its defaults; a read that fails for an
  I/O reason serves defaults for that emission only and touches nothing; anything else is rethrown.
- `prefs/BoundedImport.kt` — reads the document a user picks for an import through `readAtMost`, with
  a cap far above anything the app writes.
- `location/DeviceLocationKind.kt` — the two device positioning systems, each bound to exactly one
  `LocationManager` provider and one permission. `NETWORK` is cell/Wi-Fi with
  `ACCESS_COARSE_LOCATION` and **never** substitutes GPS; `GPS` is the GNSS receiver with
  `ACCESS_FINE_LOCATION`, asked for together with `ACCESS_COARSE_LOCATION` (from Android
  12 a request for the precise location alone is ignored).
- `location/DeviceLocationAccess.kt` — what the phone lets PaperScrape do with its
  position for one kind, read from the phone (the two permissions and the location switch), never
  from the preferences: `ALLOWED`, `NOT_ALLOWED`, `APPROXIMATE_ONLY` (GPS with only the approximate
  location), `LOCATION_OFF`. `mayUsePosition`: no position from the phone at all without the
  permission, and the last saved one while the location switch is off. Also here,
  `LocationRequestThrottle`: one real request for the phone's position an hour while the phone answers,
  process-wide; after a request that brings nothing, tries 5, 15 and 30 minutes later, then the hour,
  and round again (a cancelled request counts as one
  that brought nothing, `counted`); a source the user has just chosen is asked at once. Pure,
  JVM-tested over every combination.
- `location/DeviceLocationProvider.kt` — **one fix, asked for when something needs it.** Not a
  subscription: `currentFix` prefers a cached fix under 15 minutes old (no radio at all), otherwise
  makes one bounded `getCurrentLocation` request (API 30+) or a self-removing single update below
  that -- only if `LocationRequestThrottle` allows it (the decision is `plan`, pure:
  the permission, the system's cached position, the provider, then the throttle) -- and returns `null` rather than
  trying a different provider. `access` reads the phone's answer for a kind (`DeviceLocationAccess`).
- `location/LocationSource.kt` — which of the four mutually exclusive sources a held fix came from
  (`NONE`, `NETWORK`, `GPS`, `CUSTOM`). `GPS` and `NETWORK` are separate values so switching between
  them invalidates the held fix, exactly as switching to or from `CUSTOM` does.
  The engine invalidates the fix when the source changes, so a custom location cannot outlive a
  switch to phone location and keep Live Weather querying the old coordinates. It also
  drops a fix from the phone the moment the phone stops allowing it (`SolarDaySchedule`'s
  `FORGET_DEVICE_POSITION`), and asks the phone nothing at a fixed hour.
- `location/LocationLabelResolver.kt` — reverse geocoding through the platform `Geocoder`, which
  needs no network where a device supports it. Its `format` is separated out and pure: the label is
  `"<place>, <country>"`, place being the narrowest field the geocoder filled
  (`locality` → `subAdminArea` → `adminArea`), names untransformed in the device's locale, and a
  city-state's duplicate collapsed to one word.
- `location/LocalityLabelCache.kt` — *when* a fix is worth geocoding, kept apart from
  *how* it is geocoded so the policy is JVM-testable. A 1 km threshold (above Network-mode jitter,
  and equal to the row's own two-decimal display so the cache cannot hide a change the row would
  show), successes that never expire, failures retried after 60 s and never stored as labels, and a
  request counter so a slow lookup for the previous position cannot overwrite the current one.
  Nothing here polls; it only ever suppresses work.
- `location/CityGeocoder.kt` — forward search by city name, through Open-Meteo's keyless geocoding
  API (the same provider Live Weather uses, and the same `HttpURLConnection` style). The response
  parser and the small in-memory search cache are separated from the network call so both are
  unit-testable.
- `location/AwaitOnce.kt` — `awaitOnceOrNull`, which turns a callback platform API (the geocoder's)
  into a suspend call that always completes, completes once, and stays cancellable.
- `location/Coordinates.kt` — how a latitude/longitude pair is written for the user to read back:
  always in `Locale.US`, so a decimal comma cannot make two numbers read as four.
- `weather/` — the Live Weather pipeline, one step per file:
  `provider → normalised WeatherObservation → WeatherRepository → cache/scheduler → scene`.
  - `WeatherProvider.kt` — the interface every service implements, plus `WeatherProviderId`
    (stored by string id, not ordinal), `WeatherFetchResult` and `WeatherFailure`. A provider owns
    its endpoint, its query and its response shape and nothing else: not the schedule, not the
    cache, not the preferences, not the renderer.
  - `WeatherObservation.kt` — the normalised model every provider produces: temperature, cloud
    cover, precipitation, rain, showers, snowfall, a normalised `WeatherCondition`, a timestamp,
    and the provider it came from. Every field is nullable because "not reported" and "reported
    zero" are different facts the mapping depends on.
  - `OpenMeteoProvider.kt` — **the default, and the reason Live Weather works out of the box.**
    Keyless free tier; a key only upgrades the endpoint, and neither state is a failure. Splits
    precipitation into rain/showers/snowfall, which is why the model has room for it. Its free
    service is licensed CC-BY 4.0 for **non-commercial** use, which is the one thing the keyed
    providers exist to give an alternative to.
  - `WeatherApiComProvider.kt` — WeatherAPI.com's `/v1/current.json`.
    **Requires a key** (no anonymous tier), which is why
    `WeatherFetchResult.MissingApiKey` exists: without one no request is made at all. No key for it
    is compiled into the app; the user's own lives in their DataStore. Its condition
    vocabulary is published as machine-readable JSON — committed as a test fixture, with every one
    of its codes walked by a test. Reports one `precip_mm` and no snow
    depth in the realtime object, so `showersMm` and `snowfallCm` stay null rather than zero.
  - `OpenWeatherProvider.kt` — OpenWeather's **Current Weather Data** API (`/data/2.5/weather`).
    **Requires a key**, and none is compiled in. Deliberately *not* One
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
    names a provider this build does not have lands on Open-Meteo, with no migration
    code and no broken state — a keyed provider whose key is gone would be worse than the keyless
    one. There is deliberately **no automatic fallback between providers** at fetch time: the
    selection stands and the failure is reported, because silently answering from a different
    service makes "which provider am I using" unanswerable.
  - `WeatherSnapshotMapper.kt` — observation → `LiveWeatherSnapshot`, the renderer's vocabulary.
    **A measurement, where one exists, is the answer.** The summary code only chooses the *kind*
    when a positive total has no breakdown to explain it, and only decides whether anything falls
    at all when the provider reported no measurements — otherwise four readings of zero would keep
    being outvoted by a code, and a dry afternoon would rain.
    `isThunderstorm` carries the same requirement: it means "the scene should storm", so the
    lightning flash cannot fire over a scene with nothing falling in it.
  - `WeatherRepository.kt` — dispatches to the selected provider. **No silent fallback between
    providers:** a failure is reported as one and the selection stands.
  - `LiveWeatherStatus.kt` — what Live Weather is actually doing (`OFF`, `OK`, `NO_LOCATION`,
    `MISSING_API_KEY`, `REJECTED_API_KEY`, `FAILED`, `STALE`), written by the service and read by
    the settings screen. `REJECTED_API_KEY` means an HTTP 401/403: a provider that answered
    and refused the credential, which is not the same fact as one that could not be reached, and
    folding the two together would make a not-yet-active OpenWeather key report itself as a
    network failure. It sits before the "is a snapshot still in effect" question, like
    `MISSING_API_KEY`, because an old observation on screen does not change what the user must do.
    The settings screen draws the *Live Weather* switch off on two of these answers
    (a refused key, no position from the phone), so the service forgets the one it published --
    and says `OFF`, "not known yet" -- whenever the inputs it was about change (`LiveWeatherInputs`
    or the location source), and a pass whose fetch began before such a change publishes nothing;
    otherwise a key typed in a moment ago would read "not accepted" until the wallpaper was next on
    screen.
  - `LiveWeatherInputs.kt` — which settings force an immediate fetch rather than waiting for the
    hourly refresh. Pure, so the list cannot quietly fall behind the settings.
  - `WeatherHttp.kt` — the one `HttpURLConnection` JSON GET every provider shares, and the pure
    status → `WeatherFailure` mapping.
  - `LiveWeatherSchedule.kt` — what the Live Weather loop does next and whether what it holds may
    draw: `decide` returns the status and the snapshot together, so the settings screen and the
    renderer cannot disagree about whether the forecast drives the scene; also the retries after a
    failure, and how old a snapshot may be. Pure.
  - `WeatherRequest.kt` — what a provider URL carries: coordinates rounded to two decimals, and the
    key URL-encoded.
- `update/UpdateChecker.kt`, `update/UpdatePrefs.kt` — GitHub Releases API. A check
  sends the `ETag` of the last reply kept for the installed version (`If-None-Match`;
  `SavedUpdateReply`, which `UpdatePrefs` keeps in a DataStore file of its own, `paperscrape_update_reply`), and a
  304 with no body returns the kept answer; the notes
  are every newer release's, whole, read across the list's pages (30 releases each) until one names
  the installed version, at most `MAX_PAGES`.
- `update/UpdateNotificationPolicy.kt`, `update/UpdateNotifier.kt` — the optional
  check every three hours the wallpaper engine runs from its own loop
  (`checkDue`), and the notification it posts. The
  policy object holds every rule (interval, permission by SDK level, snooze, one notification per
  tag) with no Android type in it, so it is JVM-tested; the notifier is the channel, the
  notification and the `PendingIntent` that opens the existing update dialog. The
  policy also draws the *Notify me about new versions* row (`notifyRow`): on only when a
  notification can arrive -- the automatic check on, the phone letting PaperScrape post, and
  PaperScrape the wallpaper (`WallpaperEngineCensus`) -- and otherwise off, with the line and the tap
  (the permission dialog, the phone's notification or channel page from `UpdateNotifier`, the
  wallpaper preview) for what is missing.
- `update/ReleaseAssets.kt` — which attachment is the APK and which is its checksum (exact names),
  how a `sha256sum` file is read, and whether a downloaded package may be installed. All pure, all
  unit-tested: these are the parts that fail silently.
- `update/ApkDownloader.kt` — streams the APK to `cache/updates` while hashing it in the same pass,
  and `ApkInstaller`, which hands the verified file to Android through a `FileProvider` URI scoped
  to that one directory. No silent-install path exists. The file stays there through the handover
  (the installer reads it while its own screen is open); `pruneInstalled` deletes it at
  the next start of the wallpaper's engine or of the settings screen once the running version has
  reached it, and keeps one that is still newer (`ApkCachePruneTest`).
  `downloadAndVerifyTo` is the same download with no `Context` in it, which is what makes the
  failure modes JVM-testable against a local HTTP server (`ApkDownloadPathTest`). It reports a
  `DownloadPhase` -- `Downloading(percent)` then `Verifying` -- because the digest comparison and
  the package parse after the last byte are a visible pause, not part of the download.
  `AdvancedScreen` starts the download from an effect keyed on the release's tag, with a guard
  against a second run while one is under way, and runs the transfer in the settings screen's scope
  so it outlives the effect; `runDownload` restores an actionable state on cancellation.
- `ui/` — the settings UI, one file per destination:
  - `SettingsActivity.kt` — the activity, edge-to-edge, wraps everything in `PaperScrapeTheme`.
  - `SettingsScreen.kt` — the home screen and the routing between the six destinations.
  - `WeatherTimeScreen.kt`, `SeasonsScreen.kt`, `WorldSceneScreen.kt`, `AdvancedScreen.kt`,
    `ThemeGalleryScreen.kt`, `HolidayCalendarScreen.kt` — one destination each, all drill-downs from home.
  - `SettingsComponents.kt` — the shared Material 3 vocabulary (section header, grouped
    container, row, switch row, navigation row, segmented choice, banner, caption, screen
    shells, slider, colour picker).
  - `SettingsUiModel.kt` — the pure mapping between the two segmented choices
    (location source, seasonal palette) and the preference flags that back them, and
    the rule behind every switch whose effect depends on something it does not control
    (a switch reads on only when what it names really happens): *Live Weather* (`liveWeather`, `liveWeatherTap` -- on only while real
    weather can drive the scene), the switches that need another one (`moonPhases`,
    `dependentSwitch`, `thunderstorm`, `palms` -- drawn on World & scene's Trees page),
    the *Show X* switches at 0 % (`amountSwitch`,
    `amountTap`), the home screen's decoration count (`decorationsOn`, which counts no palm) and the question a
    theme pick asks while the calendar chooses (`pickNeedsCalendarQuestion`). It also holds the
    Location row under GPS and Network (`deviceLocationRow`: working only while the phone gives the
    position, otherwise "GPS - tap to allow" and the tap to the permission dialog, PaperScrape's page
    or the location page; `afterLocationRequest` for the dialog's answer), the weather controls that
    give way to a forecast (`forecastOwnsTheWeatherControls`), and the key rows' lines (`apiKeyLine`).
    It also holds the questions the resets and "Replace with current" ask (`sceneResetMessage`,
    `decorationsResetMessage`, `galleryResetMessage`, `replaceWithCurrentQuestion`), the banner of World
    & scene and Seasons (`themeEditsBanner`), the bird colours' shares (`birdColorShares`), *Swipe
    scroll* and what scrolls (`swipeScroll`, `sceneScrolls`), and the rule that an amount slider locks
    with its stored switch, never at its own 0 % (`amountSliderEnabled`).
    The phone's location facts reach the screens through `DeviceLocationState.kt`
    (`rememberDeviceLocationAccess`, read again on every return to the front). No Compose and no
    Android imports, so all of it is unit-tested; the screens' use of it is pinned by reading
    their source.
  - `SceneCategorySections.kt` — the per-category and per-mountain-layer editors.
  - `ThemePreview.kt` — draws a theme's preview scene; see `engine/ThemePreviewScene.kt`.
  - `SettingsInsets.kt` — how a settings destination is **sized**. Every destination is a
    full-screen `Dialog`, and with `usePlatformDefaultWidth = false` Compose measures the dialog's
    content against the *display* while the window manager sizes the window to the space between
    the system bars, so the bottom of every screen would be laid out outside the window and clipped.
    The content is therefore given the height of the area
    its window occupies: the display less the insets the **activity** measures, passed down
    through `LocalSettingsTopInset`/`LocalSettingsBottomInset`. The scaffold inside reserves the
    dialog's *own* insets — zero exactly when the window already fits the bars — so a device whose
    dialog window is full-bleed instead is handled by the same code. The trailing spacer is a
    24 dp constant; the shells apply all of it, screens never set their own.
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
| `CanvasSceneTarget` | The settings screen's live preview, which draws onto a Compose `Canvas` where there is no GL context; and the wallpaper itself when GL is given up on. |

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
             │     │  ~30 fps, paced on the display's refresh ticks (see Frame pacing)
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
             │     ├─ DayPhaseCache.at(hour, sunrise, sunset, moonPhase) → DayPhase
             │     │     (SunPositionCalculator.compute when the hour or the sun moves)
             │     └─ PaperRenderer.draw(target, dayPhase, elapsedSeconds, deltaSeconds)
             │           ├─ syncObjectRendererWithTheme()
             │           ├─ drawSky            (vertical gradient)
             │           ├─ drawStars          (cached star list, 2-3 tile copies)
             │           ├─ drawCelestialBody  (radial glow + sprite blit,
             │           │                      bounded parallax offset)
             │           ├─ drawClouds         (sprite blit)
             │           ├─ drawRainbow
             │           ├─ drawMountains      (SceneShape)
             │           ├─ drawBirds
             │           ├─ drawLake + decorations (sprite blit)
             │           ├─ drawHillLayers     (cached SceneShape + translate)
             │           ├─ SceneObjectRenderer.draw(canvas, GroundGeometry, ...)
             │           │     ├─ ground flowers and piles
             │           │     ├─ static objects, each over its visible tile range
             │           │     ├─ drawRoad
             │           │     ├─ people
             │           │     └─ cars
             │           ├─ FireworkEffect / SantaSleighEffect
             │           ├─ drawPrecipitation / drawFallingLeaves
             │           └─ drawLightningFlash
```

### The GPU backend

`GlSceneTarget` turns `SceneCanvas` calls into triangles. The properties below are
load-bearing.

**The projection is pixels, not world units.** `Matrix.orthoM(0, width, height, 0)`
puts the origin at the top-left with Y increasing downwards — the space `Canvas`
works in. Every coordinate, sprite origin, depth constant and historical divisor in
the scene therefore keeps its existing value *and its existing meaning*. A
normalised world space would have required rescaling all of them, and a sprite
whose origin is only correct together with its scale convention is precisely what
such a rescaling gets silently wrong.

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

**A sprite is reduced, then cropped, then packed — and the order is the whole of it.**
`GlTextureCache` uploads only the texels that carry ink: the transparent border is cut off *after*
the halvings and before the atlas sees it, and the quad is built from the content rectangle rather
than the canvas so nothing moves on screen. It is what makes a region mask nearly free, and it
applies to every sprite.

Cropping the **PNG** instead would be cheaper still, because it would cut the decoded bytes too, and
it is not available: `SpriteDetailLevel.reduced` truncates, so a 117-wide canvas reduced twice is 29
texels of 4.0345 authored pixels each while a 96-wide crop of it is 24 texels of 4.0000 — the two
layers of one figure start together and drift apart across the sprite.
Cropping *after* the reduction has no step to match,
because the crop's texels **are** the canvas's texels; the only thing it changes is what the
bilinear tap reads just outside the ink, and there it reads the transparent texel
`GlTextureAtlas.PADDING` already puts between two entries.

`GlTextureCache` decides placement per sprite: into the atlas when it fits, into a
texture of its own when it does not. Callers get a handle and a UV rectangle either
way, so a standalone texture is just the `0..1` case. Large sprites are excluded on
purpose — a 1024-square entry would be a quarter of the whole atlas and would push
out the small sprites that actually repeat per frame, while itself costing only one
batch break because it is drawn once. **No shipped sprite is large enough for that
gate**: the largest dimension in the set is `cloud_body`'s, well under it. It is kept as a
guard against a future sprite.

`AtlasPacker` holds the placement arithmetic, separately and without GL, for the
same reason `SceneTransform` is separate: a packing bug is silent. Two entries given
overlapping rectangles do not throw — one sprite renders with another's pixels inside
it, in whichever scene happens to draw that pair.

**It packs to a skyline, not to shelves.** A shelf packer keeps
one row open at a time, so it loses the tail of every row it closes and the slack
above every entry shorter than the tallest one beside it, and it never goes back for
either, so the atlas fills sooner and spills
sprites into standalone textures — a batch break per frame each, which is what the
atlas exists to prevent. The skyline is online, needs no sorting and no deferred
upload, and is quadratic in skyline segments in a call that already allocates a
bitmap and uploads it.
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

**A sprite blit can be summed instead of laid over.** `SceneCanvas.drawSprite` carries an
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

Because the colour is resolved by the *blend* rather than by the shader, the Canvas goldens go on
seeing the people exactly as the GPU draws them. Resolving it in the shader would have put every
Canvas check of a person on a backend it cannot run.

**Fully transparent draws are skipped.** Under premultiplied blending a zero-alpha
primitive contributes exactly nothing, and the scene fades a lot of things through
zero: precipitation, leaves, star twinkle, the sleigh's edge fade.

Curves — circles, ovals, stroked arcs, filled sectors — are tessellated at a segment
count derived from their radius *in device pixels*, so a shape drawn inside a
`scale(1/3)` sprite transform is not tessellated as though it were three times
larger. The slices' cosines and sines come from `CircleTable`, built once with the
loop's own expression; a point two triangles share is mapped through the
transform once; and a hill column wholly off the surface is not emitted. Each is the
same float, or no sample, so the frame is the same frame.

Gradients are vertex colours. A two-stop ramp is linear, and so is interpolation
across a triangle, so the sky quad and the sun's glow fan reproduce their gradients
rather than approximating them. The hill highlight needed one extra step: it is
filled as vertical columns **split at the gradient's lower stop**, because a
triangle fan whose apex sat on the base line would carry the highlight down the
whole hill instead of letting it stop, turning a highlight on the top third into a
wash over all of it.

### Frame pacing and threading

Each engine owns a `GlRenderThread`. The loop draws about 30 frames a second, paced on the
display's own refresh ticks (below); until those have been measured it sleeps 33 ms less the
frame's own measured cost. All animation is driven by measured `deltaSeconds`, so positions
remain time-correct even when frames are late.

**The loop sleeps to the display's own clock.** A frame reaches the
screen on a refresh tick; on the BV6600 two ticks are 32-32.5 ms (the panel runs at 61.45 Hz by
SurfaceFlinger's own vsync model, while the platform reports 60), so a `33 ms - cost` sleep would
slide one tick late every few frames and leave that frame on screen for three ticks, ~49 ms.
`GlRenderThread` sleeps to the tick
`FramePacing` predicts from the grid `VsyncGrid` measures off `Choreographer` about once a second,
the same whole number of ticks apart every frame (two at 60 Hz, three at 90, four at 120); with no
fresh measurement it sleeps 33 ms less the frame's cost. On this panel that is about 30.7 frames a second.

**Each frame also says when it should be shown** (`FramePacing.presentAt`,
`eglPresentationTimeANDROID`), as the Android frame-pacing library does. Sleeping to the tick fixes
when a frame starts, not which refresh shows it: without a time SurfaceFlinger takes the frame for
the first refresh after it is queued, and on the BV6600 the GPU finishes it only 2-4 ms before that
refresh, so a frame a little slower than usual would wait a whole refresh more. The time asked for
is the tick after the next frame's tick, less a quarter period: the loop's next frame then runs
before SurfaceFlinger's call into the display's composer for this one rather than through it.
The price is latency, which a wallpaper that answers no touch cannot show. `FramePacing.presentAt`
has the measurements and what is not known about
them.

It deliberately does **not** free-run at the display's refresh rate. `eglSwapBuffers`
blocks on vsync, so an unpaced loop would render at 60, 90 or 120 Hz and do two to
four times the work for motion this slow.

**Counting the threads called `PaperScrapeGlThread` will give you two, and one of
them is not ours.** The process holds exactly one Java thread of that name — the loop
above — while `/proc/<pid>/task` shows two. The second is the GPU driver's own worker,
created the first time anything in the process touches EGL: on the BV6600 it runs PowerVR's
user-mode driver (`libsrv_um.so`) and `gralloc`, and
contains no `libart` frame at all. It wears our name because Linux gives a new thread
its creator's `comm` and the driver never renames it — in a process where the settings
UI initialises EGL first, the same thread appears as `RenderThread`. It works while
the wallpaper draws and **does not exist** while it is hidden. It
is not a leak, there is nothing to close, and a CPU figure for the render path should
be taken for the process rather than by thread name.

**Scene state is owned by the render thread.** A GL context belongs to one thread,
so drawing had to leave the main looper — which means preferences, theme changes,
weather snapshots and home-screen offsets now arrive from a different thread than
the one that reads them. The answer is `PaperEngine.onRenderThread { }`, which
queues the update as a runnable executed between two frames, rather than a lock
around the renderer: a lock would put every settings write in contention with the
frame loop. On the `Canvas` fallback the main looper owns the scene and the same
helper runs the update inline.

Three process-wide objects are multi-threaded as a result, because a
process can host two engines (the picker's preview and the live wallpaper) and
therefore two render threads. `SpriteCache`, `TintFilterCache` and
`SunPositionCalculator.currentHour24()` are synchronised.
The cost is an uncontended monitor on a cache hit — the expensive
path is the decode, which happens once per sprite per process.

### EGL lifecycle

```
onSurfaceCreated   → thread starts → eglGetDisplay / eglInitialize / eglChooseConfig
                     → eglCreateContext (ES 2) → eglCreateWindowSurface → eglMakeCurrent
onSurfaceChanged   → glViewport + orthoM, then PaperRenderer.onSizeChanged
onVisibilityChanged→ the loop parks or resumes; context and textures are kept. It
                     parks with no deadline to speak of: every input wakes it
                     (`WakeLatch`)
onSurfaceDestroyed → the window surface is released, the context is kept
onDestroy          → full teardown; the thread exits on its own
EGL_CONTEXT_LOST   → every GL handle is forgotten *without* a GL call, then rebuilt
```

The config is chosen with 4× MSAA first and the same config without it as a
fallback. The scene draws circles, arcs and thin strokes that `Canvas` antialiases
analytically and GL does not, so MSAA is what keeps those edges comparable; a device
that cannot supply it still gets a wallpaper.

A failed frame first rebuilds the EGL state (`GlLifecyclePolicy`: at most three times, and only for a context that has drawn); after that the thread reports once and parks, and the engine switches to
the `Canvas` loop for the rest of its life. The scene is untouched by that switch:
the same renderer keeps drawing, through the other backend.

Textures are derived data — every sprite can be decoded again from resources — so
memory pressure drops them too, costing a re-upload and never a missing sprite.
Because a texture can only be deleted by the thread whose context owns it,
`onTrimMemory` reaches each engine's render thread as a request it honours with its
context current, rather than acting directly. The white pixel goes with the rest and
is packed back first, both because flat geometry cannot be drawn without it and
because being first is what keeps it inside the atlas. **While the wallpaper is
hidden it is packed back only when the next frame is prepared**
(`GlSceneTarget.trimTexturesWhileHidden`): packing even that one texel re-creates the
whole 2048² page, which would give back nothing; owed to the next frame, a trim at
screen-off gives back the page's 16 MB.

### Sprite memory

| | |
|---|---|
| Whole sprite set, decoded | printed on every build by `SpriteGeometryTest` (`decodedByteBudget: … B of …`), and by `python -m paperscrape_assets inventory` |
| Atlas texture | 2048² RGBA = 16 MB, allocated on first sprite, typically a fraction used |
| CPU bitmaps retained by the wallpaper | none, once uploaded. The settings screen's cards decode into the same `SpriteCache` in this process, and the screen clears it when it stops (`SettingsActivity.onStop`, unless an engine draws with `Canvas`) |

The atlas is a rearrangement of the sprite budget rather than an addition to it: its
upper bound is roughly what the same sprites would cost as individual textures. On the
heap side it is a genuine reduction — decoded bitmaps are released once uploaded — and
heap is what makes a process a preferred low-memory-killer victim.

### Allocation on the frame path

**A steady frame allocates nothing**, and a test holds it to that: `FrameAllocationTest`
draws every built-in theme (each at one hour, by day or by night), the busiest lake, rain, snow, a storm
driven by Live Weather, the sleigh and the fireworks through the real renderer, and
ART counts what the drawing thread allocates over 150 frames after a warm-up past the
traffic's arrival — zero, except an object when a firework goes off or the sleigh
throws a gift (an event every few seconds, not a frame). Its positive control shows
the counter counts on the device running it.

Five patterns account for nearly all of the allocations that have been removed:

- **Constant data built inside a draw function.** `intArrayOf`/`floatArrayOf`
  literals and `arrayOf(a to b, …)` tables read as declarations but are
  constructed on every call. They belong in a field or the companion object.
- **Tuples as return values.** A `Pair<Float, Float>` boxes both floats. Two
  fields and a boolean say the same thing for free.
- **Platform conveniences.** `Calendar.getInstance()` and `TimeZone.getDefault()`
  both allocate, the latter returning a defensive clone; a value that changes once
  a minute does not need either on a 30 Hz path. `Paint.setAlpha` on Android 10 is
  the same trap, and `setAlphaWithoutAllocating` its answer.
- **A result rebuilt although its inputs did not change**: the crowd, rebuilt
  and re-sorted every frame for the same four groups (`PedestrianPopulation.presentMask`
  now says when it has to be); the day phase, recomputed every frame for an hour that
  changes once a minute (`DayPhaseCache`); a per-frame value object where one instance
  rewritten in place does (`GroundGeometry`).
- **Boxing through an interface**: a `Float?` crossing an API every frame
  (the cloud cover is `NaN` for "none" instead), and a Kotlin function type called with
  `Float`s, which is a generic `Function4` and boxes all four (`FireworkEffect.BurstBlit`
  and `SantaSleighEffect.SleighBlit` are `fun interface`s with primitive parameters).

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

Two scale conventions coexist. Each sprite's convention is declared in its
own metadata (`scale` in `tools/assets/sources/sprites.json`, and `validate`
compares it with every call site it can resolve), but the draw path does not
read that file: the caller names the convention, as a `SpriteScale` argument:

| `SpriteScale` | Meaning | Used by |
|---|---|---|
| `SCENE_UNITS` | Sprite is authored at `SPRITE_PIXELS_PER_UNIT = 3` times its on-screen size; the blitter applies `canvas.scale(1/3)` and pre-multiplies the origin. | Every scene object, plus clouds, stars, the rainbow, the lightning bolt, fireworks, the sleigh and the lake decorations. |
| `CANVAS_PIXELS` | Sprite is authored at literal on-screen pixel size and blitted straight through, at whatever scale the caller's own `canvas.scale()` established. | Sun disc, sunburst, moon phases, birds. |

Passing the wrong one is a silent 3× size error, which is why it is spelled out
at the call site rather than implied by a function name. It is silent in the other
direction too: because nothing in a PNG records its convention, **replacing an
asset can change what an unchanged call site means**. The sky sprites therefore do
not carry their origin and scale as
literals — `PaperRenderer` declares both per sprite in named constants that
`SkySpriteAnchoringTest` checks against the PNG headers on disk, so the three
numbers that are only correct together are pinned from both ends.

`SceneObjectRenderer` draws in one convention only, so it binds `SCENE_UNITS`
once in its thin `drawSprite`/`drawTintedSprite`/`drawSpriteFaded` wrappers instead of repeating
it at every call site. `PaperRenderer` is the only class that mixes conventions, so it has no
wrappers at all: each of its calls names its own scale.

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
tested by reimplementing it in the test, where a mutation of the original survives.
`isHorizontallyVisible` still
decides each copy; the range only bounds which copies are offered to it.

Each copy's x is recomputed as `x + tileIndex * tileWidth` rather than accumulated.

In practice the range holds fewer than two tiles on average and never more than 3.
`draw()` guards
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
as one — a single wrapped translate applied to both, with one copy drawn — would make
the sun, moon and stars leave the screen periodically, so the layer
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
own x. They are equal, because the sprite is centred on the star,
and a test pins them so a change to either the asset or the convention has to come
back through them. The range is derived from what is actually drawn rather than
from what was intended: both are literally
`STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR × MAX_STAR_RADIUS_PX`, which is
10.5 px, rather than the star's own radius: the sprite reaches `1.875 × radius`, so
reserving the radius would under-reserve two-fold and drop a tile copy at a seam.
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
moves the body as far as an unbounded offset would.

All three functions are pure and live in `PaperRenderer`'s companion, for the same
reason the tile bounds do. With `scrollBackground` off, `drawCelestialBody` takes
its default `offsetX = 0f` and the star field is drawn once, untranslated.

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
perspectiveScaleAt(y)  = (y - 0.655) / (0.846 - 0.655)      // 1.0 at the reference line
```

Apparent size is proportional to the distance below the horizon, which is what a
flat ground plane seen from a fixed viewpoint does. Static objects, both traffic
lanes, both pavement rows and every vehicle and pedestrian read the same
function, so their relative sizes and speeds follow from their ground lines with
nothing kept in step by hand.

**The weather is sized the same way the buildings are.** Rain, snow and the
lightning bolt declare a size in metres and convert it with
`SceneSpace.pixelsPerMetre`, exactly as every category in the size table does: a size in
metres is checkable against a child, a head and the skyline, where a pixel count answers to nothing.

**A consequence of the metric.** A metre is
`45 x screenHeight / 2400` pixels, so *the viewport always shows the same 53.3 m of
world*, however tall it is. A fixed particle count is therefore already a fixed
density per square metre on every device: particle **size** has to scale with the
screen, particle **count** does not.

**The people behind glass take the size table's head, and the glass is fitted to it.** A driver
and a passenger are scaled so their head is the table's head (`OCCUPANT_HEAD_METRES`) at 97 %
(`OCCUPANT_SEATED_FIT`), in the vehicle's own units (`CAR_OCCUPANT_SCALE`, `FIRE_TRUCK_OCCUPANT_SCALE`):
the pane was sized to fit the head, never the head to fit the pane. The
occupant scales are quotients rather than tuned values, so a future complaint about occupant size
has to move the glass or the artwork and cannot be answered with a fourth constant.

**Three bodies, one metre-per-unit.** `CarShell` carries a compact, a saloon and an estate,
and a plain car picks one from its own immutable identity (`laneYFraction`, `startDelaySeconds`),
resolved **once** in `CarRuntime`'s constructor, so nothing per-frame can reach the choice.
A taxi is always the compact and a police
car always the saloon, so their roof accessories and liveries are drawn to one roof and one door
line each.

**That identity has exactly ten values, so everything derived from it is a table.** The two
fields are a lane constant (one of two) and a point on an arithmetic progression (one of
`CAR_SLOTS_PER_LANE`), the same ten in every theme the app ships. So a hash of them is not sampling
a distribution -- it would deal one fixed hand, once, forever.

`SceneObjectCatalog.candidateIndexOf` recovers that index by inverting the expression the candidate
was generated from, and `CarShell` and `SeatedOccupants` deal from it: the body 4/3/3, the driver's
family 5/5, the passenger's over the other three, both tones 4/3/3, and which of the two adult
outfits both seats wear. Each deal is as even as ten items allow and is ordered so no lane repeats a
value at consecutive queue positions. The stability contract is unchanged -- these are still pure
functions of the vehicle's own immutable fields, resolved outside any per-frame path.

The three bodies are deliberately different heights, so `SceneSpace` governs the family by a
**metre-per-unit** (`CAR_UNIT_METRES`, 1.51/50) rather than by a height: each body's
metres are that constant times its own units, `CAR_BASE_SCALE` stays a single number, and one local
unit is the same on-screen pixel on all three. The whole vertical layout of the cabin -- glass top,
sill, seats, occupant scale -- is shared; only the plan differs. An occupant is therefore exactly
the same size in every car.

**The seat pitch is derived, and an occupant is mirrored.** Two things about
`drawSeatedOccupant` are load-bearing and neither is obvious from the call site.

The **pitch** (`CAR_PASSENGER_X_UNITS - CAR_HEAD_X_UNITS`, 21.5) is derived from the widest seated
head **converted into the car's units** — one bust unit is `CAR_OCCUPANT_SCALE` of a car unit.
`CarShell.seatOffsetXUnits` then moves *the pair*, never one seat, so the pitch
cannot change by moving a body's occupants: only the police saloon takes a non-zero offset, because
its livery bands the low glass and shortens the pane the pair has to sit in.

The **mirror** is a constant `scale(-scale, scale)` on the bust alone, inside `drawCar`'s own
`scale(dir, 1)`. Direction of travel is already handled by the outer transform — the busts turn
with the car and the driver is always at the leading seat — so this is not about direction. It is
about the artwork's own sense: the three-quarter seated family faces +x, which is the vehicle's
rear. Mirroring about the *anchor* rather than the canvas centre is what keeps the eye axis where
the seat put it.

**Draw order is depth order for people and traffic too.** `drawPeople` runs *before* the
vehicle loop. Every pavement row including its jitter is above 0.819 of screen height and every
lane is at 0.834 or 0.862 -- `SceneObjectCatalog` snaps persisted lanes onto those two -- so a
walking figure is always the farther object.

A category's base scale is **derived**, not authored: each declares the real
height it should read as and the local-unit height its own drawing occupies
(`SceneSpace.SceneVariant`, plus the vehicle and person constants beside it).
That derivation is necessary because the sprites are authored at incompatible
internal scales -- roughly 13 units per metre for a shop front against 46 for a
person -- which no single global multiplier can correct. Which convention a given sprite is
authored in is declared per sprite in `tools/assets/sources/sprites.json` and re-derived by
`validate`, so the table is the manifest rather than a document.

`SceneObjectRenderer.variantFor` resolves which drawing a static object is
(small or large house; tower, restaurant, bar or school) once, and both the size and the
dispatch come from that one answer. A house takes its family from the silhouette it was
dealt (`SilhouetteDeal`); towers and shops choose by depth rather than by a position hash, so towers sit on the skyline and shop fronts among the houses.

**The variant chooses a family, not a picture.** All six dispatch to one
`drawNeighbourhoodBuilding`, which builds the silhouette the building was dealt (from its position, for one nobody dealt): the
two houses stack a ground floor, none-to-two storeys and a roof, so two neighbours carry two
silhouettes, the tower deals a crown onto its body, and the restaurant, bar and school pick one cut-out figure each. A family's
`unitsTall` is therefore a **reference** height that the deals vary around rather than a drawn
extent — `BuildingHeightDeclarationTest` measures by how much.

The road's own edges are derived from the lane span of the theme's **whole** car
list, computed once at construction, never from the density-filtered runtime
list. Feeding it the filtered list would make the road's width a function of the Cars
density slider. A degenerate span -- every car on one lane fraction, which is
what an old custom theme can carry -- falls back to the canonical lane spacing.

`GroundGeometry` carries only the horizontal state -- `shiftXWrapped`, `tileWidth` and
`scrollTileBias`, how many whole tiles the wrap removed; the vertical ground plane is
`SceneSpace`'s alone.

**Outside the ground projection, deliberately.** The lake sits at and above the
horizon where `perspectiveScaleAt` is at or near zero, so it has its own metric
(21 px per metre) whose only job is keeping its inhabitants right relative to
each other. Birds, the sleigh, fireworks and the celestial bodies are composed
for legibility and read neither.

**The surface is a mirror of the sky, which makes it the one part of the
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
does.

### One pass over the water, and the three reference points it has to reconcile

Everything that sits on the lake is placed into one set of slots and then drawn
**far to near**, ordered by `LakeLanes.orderByDepth` on a single key: boats, dolphins
and waves alike, so a wave crossing the near edge is painted over a farther hull
and behind a nearer one, and a leaping dolphin recedes as it rises.

The slot arrays are fields on `PaperRenderer`, sized `LANE_COUNT + WAVE_POOL`,
because this is a draw path and a per-frame list would be a per-frame
allocation. `lakeItemIsWave` and `lakeItemScale` are there for the waves:
boats and dolphins are drawn at their category's fixed scale, a wave carries its
own.

**The part that needs care is the key.** The three categories do not measure
depth from the same place:

| kind | placed from | its key: where its drawing meets the water (`LakeLanes.visibleWaterline`) |
|---|---|---|
| sailboat | its placement point | **25 boat units below** it — `drawSailboat` hangs the hull 8 units down and 17 tall |
| dolphin | its lane | in the air, its belly: the lane less the climb, plus 29 of its own units; under water the lane, where its splash stands |
| wave | its base | the base itself |

`orderByDepth` sorts on that one key, so every kind is compared where its own drawing meets the
water, at its own scale. The climb only ever moves a dolphin backwards, and boats among themselves
keep the order their placement points give them. `LakeLanesTest` and
`LakeWaterlineOrderTest` carry the property as arithmetic and as a real frame rather than a golden.

**The waves themselves.** Three slots, present only when it is raining or there
is a thunderstorm; a clear sky gathers none.
A slot's membership changes only while it is off screen, which is
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
  (`baseHillShapes` + `cachedPathsThemeId/Width/Height/Variation`), static object
  runtimes, car runtimes. These build `SceneShape`s and object graphs, so rebuilding
  them per frame would be expensive.
- **Stateless, addressed**: clouds, precipitation, falling leaves, birds,
  mountain layers, lake decorations and lake sparkles. These hold no state at
  all beyond the clouds' fade (`CloudCoverFade`); each candidate's attributes are a pure function of its index.

### The candidate system

Every effect draws from a **fixed candidate pool** of constant size
(`CLOUD_POOL_SIZE = 41`, `PRECIPITATION_POOL_SIZE = 240`, `BIRD_POOL_SIZE = 6`,
`MOUNTAIN_POOL_SIZE = 4`, `LAKE_GLITTER_POOL_SIZE = 9`,
`LAKE_DECORATION_POOL_SIZE = 4`, `LAKE_SPARKLE_POOL_SIZE = 5`; falling leaves are 3 to 13 per visible crown, `SceneObjectRenderer.leafSourceLeafCount`). Pool size is
part of the visual contract: it defines what 100% density looks like.

**Attributes are addressed, not consumed.** `CandidateNoise.value(seed, index,
channel)` is a pure MurmurHash3-finalizer lookup, so candidate 17's drift speed
is the same number whether it is the only survivor or one of the whole pool. Each
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
full drop set. Coverage 0 means no precipitation, with no diffuse floor
anywhere. When the cloud layer is switched off, the field is set uniform so that
hiding clouds does not also hide rain.

This depends on `drawClouds` running before `drawPrecipitation` in the frame,
which it does unconditionally. Reversing that order would leave precipitation
reading a one-frame-stale field.

**A drop's colour is derived per frame, not declared.** The theme supplies
a day/night pair; the sky it falls through changes with the hour, the twilight
branch and the weather, and in eleven of the twelve themes there is an hour at
which the two carry the same Rec. 601 luma. `drawPrecipitation` therefore reads
the sky's luma at the two ends of the stretch a drop crosses with sky behind it —
the cloud band's own middle down to `SceneSpace.HILL_LAYER_TOP_FRACTION` — and
`PrecipitationContrast.standOffFromSky` carries the theme's colour toward white or black until it clears
that whole band by `PRECIPITATION_MIN_LUMA_GAP`, and no further. A colour already
clear is returned unchanged.

It is **one colour for the whole fall**, and that is forced rather than chosen: the
sky's luma is monotone down the fall and the drop's is constant, so whenever the two
are close the sky crosses the drop inside it, and any per-height correction would
have to sit above the sky at one end and below it at the other with no continuous
path between. Two samples suffice because both `skyAbove` and the luma weighting are
linear, so the ends bound the interval.

This reads `skyTopColorNow` / `skyHorizonColorNow`, which `drawSky` writes on every
branch, the horror sky's included.
The water's mirror and the struck waterline read the same two fields.

**Effect offsets are evenly spaced**, `(ordinal + 0.5) / EffectId.COUNT`, giving
a guaranteed minimum separation of `1 / COUNT`; hashed offsets can land close enough
together to select identical
candidate sets at most densities.

**Small pools keep at least one element** when the category is visible and
density is above zero (`fallbackIndexFor`), so a four-candidate category turned
down low reads as sparse rather than switched off.

Seeds come from `seedFor(ordinal) = theme.id.hashCode() xor (ordinal × 0x9E3779B9)`.
`String.hashCode` is specified exactly by the Java language, so a theme produces
the same scene on every device and every run.

Nothing here is cached, so nothing needs invalidating: a theme, size or
customization change simply produces different values on the next frame.

### Stratified selection, for the pools too small for a coin

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

Both keep the callers' own addressing, so they decide which value a
slot receives and nothing about which slot is which. `PedestrianPopulation` deals
the four person kinds, the three group sizes, the three skin tones, the two
directions and the two pavement rows; `WindowOccupants` deals a building's
occupant count across its own panes; `SilhouetteDeal` deals each building category's silhouettes. The stability contract is untouched:
a slot's value is still a pure function of `(seed, slot)`, so lowering a density
still removes particular slots and leaves the rest exactly as they were.

### Where a user's own settings live

Three tiers of settings, all persistent, beside the saved themes and the updater's own files:

| what | store | scope |
|---|---|---|
| Global preferences | `paperscrape_prefs`, flat keys | one set, theme-independent |
| **A theme's own customization** | `paperscrape_prefs`, one JSON key per theme (`theme_customization_<id>`) | **one per theme, unlimited** |
| The live edit | `paperscrape_prefs`, the flat per-theme keys plus `pending_customization_theme_id` | exactly one theme at a time |
| Saved themes: built-in overrides and standalone custom themes | `paperscrape_custom_themes`, one JSON blob | unlimited |
| Updater state | `paperscrape_update_prefs` | excluded from backup |
| Last update check's reply | `paperscrape_update_reply` | excluded from backup |

The middle tier exists because the live
edit is a **single flat key set shared by every theme**, and `ensureFreshPendingTheme` wipes it
whenever a setter arrives for a different theme -- without that guard one theme's values would
leak into the next -- so below
"Save this theme as...", a customization would survive only until the user touched a second theme.

The guard therefore **archives before it wipes**: the outgoing theme's state is serialised into its own
key, and the incoming theme's is restored into the scratch space if it has one. `resolveActiveCustomization`
reads, in order: the live edit if it is this theme's, then this theme's archive, then a saved
entry's baked-in customization, then the theme's default.

The archive uses `SceneCustomization.toJson`, the same serialisation `CustomThemeStore` persists
saved themes with -- so a customised built-in and a saved theme are the same bytes in two places
rather than two formats to keep in step, and the backup format gets both for free.

### Backup and theme-share formats

Two documents, `prefs/AppBackup.kt` and `prefs/ThemeShare.kt`, with **separate schema versions and
separate `kind` markers**. They are not variants of one format: a backup is one user's whole app
and carries their API keys; a theme file is one look meant for a stranger and carries nothing
personal. Merging their versions would mean neither could change alone, and importing one where
the other is expected is refused by name rather than by a parse failure.

Both parse into a whole document or an error, never a partial one, so validation and application
are separate steps. `prefs/BackupRepository.kt` supplies what DataStore cannot: the two stores have
no shared transaction, so an import snapshots the current state, writes both, and writes the
snapshot back if the second write fails.

**And the staging is uncancellable.** Snapshotting and parsing may be abandoned freely --
they change nothing on disk -- but from the first `replaceAll` onward the only states worth being
in are "both old" and "both new", so the two writes and the rollback are one `NonCancellable`
region. `import` is called from the settings screen's
`rememberCoroutineScope`, which Compose cancels when the screen is left, and a cancellation
between the two writes would otherwise leave half a restore behind *and* skip the rollback, because the rollback
would then suspend on an already-cancelled job. The window is milliseconds wide and needs no crash to
reach.

**Applying a restore is not the same as showing one.** `SceneTheme`
compares by `id` alone, `CustomThemeEntry` is a data class containing one, so a restored
`CustomThemeData` whose themes keep their ids is `==` to the one it replaced and
`collectAsState`'s default equality policy would suppress the change. `SettingsScreen` holds that state
under `neverEqualPolicy()` and refreshes `CustomThemeRegistry` from the same collector, so the
synchronous registry and the composition cannot disagree about which is current. The equality by
`id` is deliberate (the id is what every lookup in the app means by "the same theme"), and
`CustomThemeDataEqualityTest` pins both halves: it fails the day `SceneTheme` gains a content-aware
`equals`, which is when `neverEqualPolicy()` would stop being needed.

A shared theme carries the **resolved** scene and layout rather than a built-in id, so a theme
exported today still renders when that built-in is redrawn; `sourceThemeId` is provenance for
display and is never resolved against.

### Theme previews

`engine/ThemePreviewScene.kt` describes what one theme's gallery card contains: sky colours, the
hill colour, the mountain peaks, the lake band, and a list of objects, each an (x, ground y, scale)
plus the sprite parts the renderer itself blits for that object, at the renderer's own offsets.
`ThemePreviewScenes.forTheme(theme, customization)` builds it, and every object in it is
conditional on the same flag the wallpaper reads — `lake.drawsWater`, `snowmen.visible` with its
density, `winterColorsEnabled`, `halloweenEnabled`, `mountainsFront.visible`, and so on — so a preview
cannot contain something the scene would not. That includes the amounts: at 0 % the card
draws none of what the wallpaper draws none of (the lake, the trees, the houses, the decorations, the
clouds, the birds, the stars, the rain, the boats), where the switches read off. The gallery passes the customization the wallpaper
would draw the theme with -- `CustomThemeRegistry.resolveActiveCustomization`, the engine's own
resolution: an edit in progress, then the theme's own edits, then a saved copy, then the defaults.

**Colours and landscape come from the scene's own rules too.** The card is one real moment
of the wallpaper's day -- `ThemePreviewScenes.cardPhase`: noon, midnight for the two night themes,
19:00 for Sunset, whole hours the fixed-time slider can set -- and at that moment its sky is
`SkyGradient` (the function `PaperRenderer.drawSky` paints with), its hills, mountains, water and
building walls are blended on that moment's `dayBlend`, its road is `SceneObjectRenderer.roadColor`
and appears only where `SceneObjectRenderer.drawsRoad` says the scene has one, its palms are where
the Palms switch puts them, read as the wallpaper reads it (`palmsShown` of the layout's own
`hasPalmSlots`) -- and with palms in the trees' places under the Christmas layer, a fir and a
palm as the scene keeps them, asked of the layout's own kept slots through `keepCandidate` and
`palmSpeciesApplied` (`cardTrees`: the firs stay firs among the palms) -- and its
mountains are `MountainSilhouette`'s parabolic arch.
The objects in front take the same moment too: a tree, a car or a decoration wears
`ObjectVariantConfig.colorAt`, which is what `colorFor` paints an instance of that variant with, a
palm carries `nightShadeAt` as a `PreviewSprite.shade` that the painter hands to `SpriteBlitter.draw`
as the wallpaper does, the clouds blend their pair on the same `dayBlend`, a pumpkin is carved
where `halloweenEnabled` is on, and a penguin's belly is `SceneObjectRenderer.PENGUIN_BELLY_COLOR`.
The card draws no porch light,
car lamp, beacon, wheel or window occupant, at any hour.

It holds **no Android type beyond resource ids**, which is what makes "what does this theme's
preview contain" a unit-testable question; `ThemePreviewSceneTest` pins the characteristic object
of each of the twelve themes and, in both directions, that nothing a theme has switched off is
drawn. `ThemePreviewTruthTest` compares each card with the scene the same customization
builds: every family the scene draws is on the card, every boat and dolphin has its ink in the
water, nothing is more than half covered by what is drawn after it, (R4) the sky, hills,
mountains, water and road are the wallpaper's own at the card's moment, and (R5, R6) every
car body ends on the floor the card gives it, measured off its PNG, a fir stands on a card only
where `SceneObjectRenderer.drawsFirs` lets the wallpaper stand one, and (R7, R8) every tinted
object, palm and cloud wears the wallpaper's colour or shade at the card's moment and a pumpkin is
carved where the wallpaper carves it -- on the built-ins, on customizations no built-in ships, and on
a theme saved under a `custom:` id.

Both places that show a preview -- the gallery card and the strip at the top of World & scene --
go through `ThemePreviewGeometry` (one 4:3 shape, one uniform scale, no per-call-site crop or
fitting factor) and through the same scene builder, so they cannot drift apart. World &
scene passes `forceNight` to see night colours; the gallery never does.

`ui/ThemePreview.kt` replays that description into a Compose `Canvas` through `CanvasSceneTarget`
and the same `SpriteBlitter` the wallpaper uses. There is no GL context, no animation, no timer and
no per-card bitmap: the description is built once and kept by `remember`, sprite pixels come from
the process-wide `SpriteCache`, and a card costs one static blit per sprite part it lists (the
dealt buildings are most of them) on composition and on scroll, and nothing at rest. Rain, snow and falling leaves
are painted over the whole card, as the wallpaper paints them; stars stay in the sky.

### The automatic-theme calendar

Two tiers, checked in order: four **occasions** (Easter, Halloween, Christmas, New Year) over four
**seasons** (winter, spring, summer, autumn). An occasion passes *over* whatever season is beneath
it; seasons partition the year and cannot overlap each other. The first match wins, and the order
is `CalendarWindow`'s declaration order — code, not configuration.

**The seasons are a continuous ribbon**, so with the factory seasons an occasion the user shortens
always has a season underneath it.
The four seasons start on the first of their month (1 Dec, 1 Mar, 1 Jun, 1 Sep),
which is the meteorological convention; winter's last day is stated as **29 February** so a leap day
cannot fall out of the ribbon, and in a common year no date can equal it.

Autumn is one entry, and Halloween taking October back from it is the
tiers doing their job.

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

### The seasonal launcher icon

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
choose which of its launcher entries the system draws. `ui.SettingsActivity` carries no
LAUNCHER filter — six aliases do, exactly one enabled at a time — and `LauncherIconSwitch` enables
the new one **before** disabling the old, so the package is never momentarily without a launcher
entry for a launcher to react to.

**`DONT_KILL_APP` is the whole of the safety argument.** This process is the live wallpaper.
Without the flag the platform kills it, and the home screen is black until the system
re-binds the service; with it, the engine object is the
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
        └─ CustomThemeRegistry.resolveActiveCustomization(themeId, pending…, themeCustomizations)
             ├─ in-progress live edit (if tagged for this exact theme), or
             ├─ this theme's own archived edits, or
             ├─ a saved entry's customization (built-in override or custom theme), or
             └─ defaultCustomizationFor(themeId)
                  └─ .withResolvedDayNightColors()   ← automatic pairs, once
```

### Windows, and what colour one is

Every window in the scene crossfades between two constants on the frame's own `nightGlow`:
`SceneObjectRenderer.WINDOW_GLASS_DAY` (`#B9CBD9`, cool glass) and `WINDOW_GLASS_NIGHT`
(`#FFE79A`, warm light). `windowGlassColor` is the only place that blends them.

**There is one caller.** Every window of every building is a `GLASS_MASK` part, and
the composer computes the colour once per building and hands it to all of them; the gallery preview reads
the same function rather than the second crossfade it would otherwise have needed.

Every building but a house has its night scaled by the business openness before it
reaches those ramps (`BusinessHours`, off by default and then arithmetically absent): outside
their hours the shops, the bar, the school and the towers hold their unlit daytime glass whatever the sky
does, and their window occupants' dealt count thins the same way. The houses' windows never
consult it — one line, `glassNight`, which `BusinessHoursWiringTest` pins along with the occupant
path's own exemption, the way `SkyscraperWindowTest` pins the colour coupling.

**A tintable window asset is a white mask.** It is a *weight* mask summed over the
piece's fixed layer rather than a whole sprite multiplied by a colour — the people's system —
which is also why the frame around a pane cannot be washed out by the glass's own
tint: the two are different layers.

`SkyscraperWindowTest` reads the source and pins the coupling: the one glass call site asks
`windowGlassColor`, the gallery asks the same function, and a house's glass ignores opening hours
while a shop's follows them, so the JVM suite catches what otherwise only a golden would.

### Automatic day/night colours

Every colour the user can edit that exists as a **day/night pair** carries an
`AutoColorMode`: `MANUAL` (the default),
`FROM_DAY` (the user sets day, the night half is derived) or
`FROM_NIGHT`. Single colours — the sun, the moon, the four bird colours, the
sunrise/sunset sky bands — have no mode, because there is no twin to derive
from or for.

`DayNightColor` is the only implementation of the transform, and it is plain
Kotlin with no `android.*` import so that `ThemePreviewScene` (which
deliberately avoids the platform) and the JVM tests run the same code the
wallpaper does.

It works in **CIELAB**: hue held, `L*` ×0.28, chroma ×0.72, and
a small push towards blue (`b*` −6, scaled by the daytime lightness so black
stays black). Out-of-gamut results are gamut-mapped rather than clipped —
clipping a darkened red pinned green to zero and dragged the hue towards
magenta.

The two factors come from the band the twelve built-in themes author their own night
colours in (`L*` 10.9 to 29.6) and are checked across a matrix of eleven
surface kinds by `DayNightMatrixTest`, not against a single colour.

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
written without one restore as they were, with no schema version bump.

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
(`CarSelection`) read. One more field changes *what* a kept object
is: the Palms switch -- `palmsEnabled` on a layout that plants palms, and
`palmsInsteadOfTrees` on one that plants none -- which
`palmSpeciesApplied` resolves when the static list is built, so both are
compared too. And while
`palmsInsteadOfTrees` is on, the Christmas layer: a slot that stands as a fir
(`SceneObjectRenderer.standsAsFir`, the drawing's own rule on the same seed)
stays a tree among the palms, so the layer decides which slots are palms and is
compared then, and only then. Everything
else — every category colour, the sky/stars/clouds/precipitation/rainbow/
mountain/lake/bird sections, hill variation, the seasonal palette flags — is
consumed at draw time.
`SceneCustomization.staticStructurallyEquals` and `.carsStructurallyEquals`
encode that distinction as pure, allocation-free field comparisons (not a hash:
a collision would silently skip a needed rebuild).

The static and car lists are compared separately so that changing, say, house
density rebuilds the static objects **without** resetting every car's in-flight
`progress` along the road. Rebuilding the static list is visually free, since
`StaticRuntime` holds only an `idleSeed` derived deterministically from its
spec; rebuilding the car list is not, which is why it is gated on the cars'
**visibility** alone. A car *density* change rebuilds nothing at all:
the slider maps to an explicit count (1 car at 0%, all ten slots at
100% — `CarSelection`), every inventory slot keeps a ticking runtime whatever
the count, and membership flips per car only while that car is off the drawn
span of its loop — so an addition drives in from the edge, a removal finishes
the pass it is on, and nothing pops into or out of the middle of the road.

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
`IntArray`-backed structure. That is not incidental: a
`ConcurrentHashMap<Int, Bitmap>` would box the `Int` key on **every** lookup, and
resource ids are far outside `Integer`'s small-value cache, so every sprite blit
would allocate an `Integer`, inside `Integer.valueOf` where the source does not
show it as a `new`.

`onTrimMemory(level, anyEngineVisible)` applies `MemoryPressurePolicy`, evicting
least-recently-drawn sprites to a fraction of current usage, or everything when
the process is a kill candidate. Sprites are dropped, never `recycle()`d:
dropping the reference is enough for the platform to reclaim the pixels (bitmap
storage has been GC-tracked native memory since API 26) and `recycle()` would
risk an `IllegalStateException` if a reference were still held.

**`SpriteCache` is synchronised**: every entry point is `@Synchronized`, because
rendering runs on each engine's own GL render thread and a process can host two
engines, so a draw can meet a trim or another draw in the cache (§3, *Scene state is
owned by the render thread*).

Measured footprint if every sprite is decoded. **The figures are deliberately not written
down here.** `inventory` measures them from the shipped PNGs, and a number
copied out of it into this document would be stale the next time a sprite changes.

```bash
cd tools/assets && python -m paperscrape_assets inventory   # writes reports/runtime-inventory.{json,md}
```

**`tools/assets/reports/runtime-inventory.md` is evidence of the run that produced it, not a
live view**, so regenerate it before quoting it. `validate` refuses to pass on a stale one.

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
- **A person is fixed art plus one weight mask per colourable region**, so one drawing serves every
  colour. A shape ships as `<shape>_fx` (everything that does not follow one of the
  four colours, plus the dark half of everything that does) and up to four masks `_ms _mh _mt _mb`
  (skin, head, shirt, trousers), and the engine recomposes `fixed + Σ (mask × colour)` at the blit.
  Both are written by `tools/generate_people_layers.py`, which also writes the engine's lookup
  table; `PeopleLayerAssetTest` checks the sum against the drawing. **Every mask is a full canvas in
  its PNG and a fraction of one in texels** — see the crop in §3.

Transparent margin is not accounted as waste, because it is not
incidental. Each sprite declares a `contentBox` and an anchor rule, and the
margin around the content is what the anchor is measured against: `palmtree_fronds`
hangs its fan above a declared attachment point, `cloud_body` and `sun_body` are
centred in canvases their artwork deliberately does not fill. Cropping any of them
would move the sprite rather than save anything.

### The source pipeline

Whatever renders a shipped sprite is committed beside it: an SVG source, rendered by `tools/assets/`,
or a generator script that the sprite's registry entry names (the people's layers and the
neighbourhood's pieces).

`tools/assets/` is **offline developer tooling: Gradle
never invokes it and the app does not depend on it.** The pipeline is

```
SVG source  ->  version-pinned deterministic rasterisation  ->  PNG
```

| Piece | Role |
|---|---|
| `sources/sprites.json` | Registry (schema 4): one entry per shipped sprite, declaring size, content bounding box, anchor rule and anchor, scale convention, tint class, usage, and either an SVG source or a stated reason there is none |
| `sources/svg/` | The SVG sources |
| `paperscrape_assets/raster.py` | The one rasterisation path, plus a probe that hashes a fixed document to detect toolchain drift |
| `paperscrape_assets/fit.py` | Geometry recovery by sweeping a parameter against a shipped PNG |
| `paperscrape_assets/callsites.py` | Syntactic resolution of sprite blit call sites in the Kotlin sources, so declarations can be compared against the code |
| `paperscrape_assets/normalize.py` | The padding and grid normalisation rule: co-registered groups, exclusions, and the crop plus origin compensation each sprite needs |
| `paperscrape_assets/fidelity.py` | Comparison metrics and the three verdicts |
| `paperscrape_assets/cli.py` | The command line, run from `tools/assets` as `python -m paperscrape_assets <command>`: `probe`, `inventory`, `validate`, `normalize`, `fit`, `render`, `compare` and `all` |
| `paperscrape_assets/registry.py` | The registry's format, and its validation against the shipped set |
| `paperscrape_assets/inventory.py` | Read-only measurement of the shipped PNGs: decoded bytes, padding, duplicates |
| `paperscrape_assets/report.py` | The JSON, markdown and comparison sheet written under `reports/`, and the check that a committed report is not stale |
| `staging/` | Rendered output. Never `res/drawable-nodpi/`; the CLI refuses an output path inside it |
| `reports/` | Committed measurements, including a visual comparison sheet |

### Padding and grid normalisation

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
  the filter reads**. This is the general rule and it is worth stating on its
  own; see below.

#### The transparent margin is part of the drawing

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
| `tools/assets` `normalize --apply` | crops the shipped PNG | **yes** — `normalised_box` steps one grid cell out on any trimmed side |
| `GlTextureCache.cropToContent` → the atlas | crops the *reduced* bitmap to zero margin before upload | **yes** — `GlTextureAtlas.add` uploads every entry inside a one-texel transparent border, which its own "Bleeding" note exists for: *"a bilinear sample that strays past an edge finds transparency rather than the neighbouring sprite"*. The guard pixel `cropToContent` removes is put back before any sampler sees it |
| `GlTextureCache.uploadStandalone` | same crop, no atlas | **no** — `GL_CLAMP_TO_EDGE` with `GL_LINEAR` is exactly the clamp described above. Currently unreached: the twelve-theme census finds **zero** standalone entries at full density. The path exists and the day a sprite spills into it, its edge is the heavier one |
| `CanvasSceneTarget` | blits the PNG as it ships | **the PNG is the only guard it has**, which is why the asset-side rule is where this had to be fixed |

The atlas's border and the asset tool's guard pixel are the same invariant one layer
apart. Two of the four rows above solve it independently and one is covered only because it
is not reached, so a change to any of them has to keep the others in view.

### Checks on the sprite set

`normalize` runs in check form as part of `python -m paperscrape_assets all`. **The invariant
it enforces does not describe the whole shipped set**: the asset library places drawings
inside canvases sized on the grid and declares the content box, so a number of sprites carry
margin on purpose and cropping them would move them — `normalize` in check
form reports which, and `EXCLUSIONS` in `normalize.py` names the deliberate ones with their
reason. On the JVM side,
`SpriteGeometryTest` asserts what is true of the set as a whole —
every canvas on the 3 px grid, a ceiling on total decoded bytes, and no single
sprite taking more than an eighth of it. The byte ceiling is the part that
matters: it is what a memory-pressure policy and an atlas are sized against, and
stating it directly is more honest than inferring it from per-sprite margins.

Two other sprite tests sit beside it, both reading the PNGs rather than the code:
`SpriteVariantTest` (no two sprites are the same bytes, and the seasonal pairs
differ) and `SpriteTintClassTest` (every tinted sprite is a
light neutral mask, every untinted one carries colour).

**Determinism** rests on an exactly pinned `resvg_py`, chosen over a cairo-based
rasteriser because it carries its own scan converter instead of binding to a
system graphics library. Output that varied with the host's libcairo would make
"reproducible" mean "similar on this machine". The pin is verified rather than
declared: `probe` renders a fixed document and compares its hash to a recorded
value, so a toolchain change is detected instead of silently invalidating every
recorded figure.

**Coverage: every sprite carries a registry entry, and every sprite either carries an
SVG source or names the generator that writes it.** The second group is the
people's layer files and the neighbourhood's pieces, which declare `source.kind = "none"` and name
`tools/generate_people_layers.py` or `tools/assets/buildings/build_neighbourhood.py` in their reason.
`validate` prints the three numbers — entries, sources, declared gaps — which
is where to read them.
`tools/assets/README.md` states the registry-to-`res` relation as a rule rather than a
count, and `tests/test_registry_coverage.py` enforces it.

### The manifest, and what checks it

Schema 4 declares, for every sprite, the metadata the asset rules require: a
`contentBox`, an `anchorRule` with the `anchor` it derives, the scale
convention, the tint class and the season. `contentBox` is re-derived by `validate`
rather than trusted, so it cannot drift away from the PNG it describes.

`star_sparkle`'s `notes` record a disagreement between the manifest the asset library came with
and the shipped call site. The sprite was
declared `CANVAS_PIXELS` there, but read as
raw pixels the 180 px sparkle would cover 180 local units against a star's own
`STAR_SPRITE_RADIUS_DIVISOR` of 16, so the registry
keeps the call site's `SCENE_UNITS`.
Size, convention and origin are only correct together; when two of them
disagree the answer comes from whichever was actually re-derived.

The manifest is **tooling-side only.** No Kotlin reads it, nothing in the Gradle
build depends on it, and the APK is unaffected by its existence. Consuming it at
runtime would need a per-sprite lookup on a draw path.

What it does do is close a gap. A sprite's pixel
size, its scale convention and its origin are correct only together, and nothing
in a PNG records the convention — so the registry declares it, and
`callsites.py` resolves each blit call
site syntactically and `validate` compares `scale`, `tint` and, where an anchor is
determined, the origin.

Resolution is deliberately total-or-nothing. There is no dataflow analysis: a
sprite chosen from a lookup table (`resId`, `phaseSprite`) or an
origin computed from the drawn object's own dimensions resolves to nothing, and
is reported as **unresolved** rather than counted as agreement. How far each check
reaches is printed by `validate` itself, and is not copied here: its `registry OK:` line
counts the entries whose `contentBox` was checked against the PNG, `anchors:` the anchors
determined, `variants:` the variant groups compared with the shipped bytes, and
`call-site check:` the sprites whose scale and tint, and whose origin, were compared with
the code.

```bash
cd tools/assets && python -m paperscrape_assets validate
```

The rest is not a shortfall to be papered over: an origin is `placement - anchor`
with both unknown, so it fixes an anchor only for a sprite that *is* an object
rather than a part of one: a part is placed by the piece it belongs to; the
`person_*` sprites at hand-tuned constants outside the anchoring system entirely.

See `tools/assets/README.md` for the authoring conventions and the commands.

---

## 6. Animation systems

| System | Mechanism |
|---|---|
| Parallax | `continuousScrollAccum` (`Double`) + optional home-screen swipe offset → `scrollProgress` (`Float`) → per-layer multiplier. The celestial body is the one exception: it takes the two inputs separately and bounds the result — see §3, *The sky layer*. |
| Object idle motion | `sin(elapsedSeconds × k + perObjectPhase)`. |
| Cars | Per-runtime `progress` advanced by `deltaSeconds × speedFraction`, wrapped with an off-screen buffer. |
| People | Four group slots, each a group of one to three, walking a whole tile per loop at their row's speed (`SceneTime.cycle`); a 4-frame walk cycle stepped by elapsed time. |
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

One DataStore Preferences instance per file:

- `paperscrape_prefs` — `WallpaperPrefs`, all user settings, exposed as
  `settingsFlow: Flow<WallpaperSettings>`.
- `paperscrape_custom_themes` — `CustomThemeStore`, custom themes and built-in
  overrides, serialised as JSON.
- `paperscrape_update_prefs` — `UpdatePrefs`, the update snooze and the last notified tag.
- `paperscrape_update_reply` — `UpdatePrefs`, GitHub's last reply to an update check, kept apart so
  the small file the snooze lives in does not have to parse it.

Both the Compose UI and the wallpaper engine collect the same flows, which is
what makes settings apply live without a restart.

**An engine draws nothing until both stores have answered**
(`FirstFrameGate`). It is built from a fresh install's settings -- Sunset, the real
hour -- because the reads are asynchronous and the renderer exists as soon as the
surface does, so drawing at once would show Sunset before the user's theme.
The render thread is told it may draw
only once the first settings and the first saved themes have been queued to it --
it drains its queue again after seeing that, so the first frame carries them -- or
after a 2 s backstop, for a store that never answers.

No flow operators are used: there is no `debounce`, `conflate`, `sample` or
`distinctUntilChanged` in the project. None is needed, because the write path
itself does not fire per drag tick — see below.

### Continuous controls, and what is not one

A slider is right for a value with few positions and no name — a density, a strength, a count of
days. It is wrong for a value the user already knows: the two
ends of a calendar window as sliders over all 366 month-days would be, on the reference device,
**366 positions across a 632-pixel control, 1.7 pixels per day**. A fingertip selects a week there,
not a date. They are typed (`ui/DateEntry.kt`), and Easter's two **lengths** — nought to seven
days, eight positions on the same track — are sliders, which is the distinction rather than a
compromise.

**Every** `Slider` call site goes through `PreferenceSlider`, which holds the
in-flight value in local Compose state for the duration of the drag and writes
to DataStore **once**, from `onValueChangeFinished`, and only when the value
actually changed. To count the call sites, run
`grep -rhoE 'PreferenceSlider\(|SettingsSliderRow\(' app/src/main/kotlin`
and subtract the two declarations. Value captions are rendered by the same composable from the
displayed value, so they stay live during a drag without any write.

The handover between the local value and the persisted value arriving back
through the flow is in `SliderDragState` — pure functions, no Compose or
Android types, so it is unit tested directly. The local value is held until the
persisted value matches what was committed, otherwise the thumb would snap back
to a stale value for the frames between the finger lifting and the write
landing.

Text fields (custom location, hex colour, theme name) follow the same pattern
with an explicit Apply/OK commit.

### The saved-themes document

The custom-theme JSON carries a **`schemaVersion`** field
(`CUSTOM_THEME_SCHEMA_VERSION` in `engine/CustomThemeData.kt`). Payloads written before
versioning existed have no such key and are read as version
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

**The document is read entry by entry, and an entry that fails is kept, not dropped**, so one
saved theme the reader cannot parse does not make the whole document
read as empty, and `CustomThemeStore.update`, which refuses to write over a document it cannot
read, does not drop every later edit. Each entry has its own `try`; one that fails travels in
`CustomThemeData.unreadable`, invisible to every screen and to the wallpaper, and `toJsonString`
writes it back **as the text it was stored as** into a top-level section, `unreadableEntries` --
each item `{"from", "key", "schemaVersion", "entry"}`, the entry verbatim. The text is found by a
strict scanner (`JsonSpans`), because `org.json` cannot hand back the text of a value it parsed and
re-serialising would change numbers and escapes. Every read retries each kept entry from the schema
it records and puts it back where it came from if it now reads and its place is free. The section
exists only while something is kept: a document with nothing unreadable carries no such section.
A document that is not JSON is unreadable as a whole and refused.

`SceneTheme` overrides `equals`/`hashCode` on `id` alone, so two themes with the
same id but different colours compare equal. `CustomThemeRegistry.generation()`
exists as a counter to work around this.

---

## 8. Build system and CI

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.7.1 (wrapper jar SHA-256 matches the checksum Gradle publishes for 9.7.1) |
| Kotlin Compose plugin | 2.4.20 |
| Kotlin | AGP built-in, driven by the Compose plugin version above -- 2.4.20 (the `org.jetbrains.kotlin.android` plugin is intentionally not applied) |
| `compileSdk` | 37 |
| `targetSdk` | 37 |
| `minSdk` | 26 |
| Java compatibility | 17 |

**`compileSdk` and `targetSdk` are both 37**, and they are two settings doing two jobs.
`compileSdk 37` says only which `android.jar` the code links against; it is what
`androidx.core 1.19` and the Compose `1.12` line require (`minCompileSdk=37` in
their AAR metadata) and it changes nothing about how the app runs. **The platform's
behaviour gates read `targetSdk`**, so raising it is a change of its own, assessed against
each of the platform's behaviour changes, rather than a line moved with a dependency upgrade.
Lint's `OldTargetApi` would report a target that lags the compile SDK.

Dependencies are declared as hardcoded version strings; there is no Gradle
version catalog:
Compose BOM `2026.08.00`, `core-ktx 1.19.0`, `appcompat 1.8.0`,
`lifecycle 2.11.0`, `activity-compose 1.13.0`, `datastore-preferences 1.2.1`,
`coroutines 1.11.0`. Nothing is on an alpha, beta or rc.

### Build types

Four, and the fourth is the one worth explaining.

| Build type | What it is |
|---|---|
| `release` | R8 on, resources shrunk, not debuggable. Signed only if the `PAPERSCRAPE_RELEASE_*` environment variables are present — deliberately left **unsigned and uninstallable** rather than silently falling back to something that looks shippable. This is what CI publishes. |
| `debug` | R8 off, debuggable, `.debug` application id suffix, signed with the committed `debug.keystore`. What the instrumented suite runs against. |
| `perf` | `initWith(release)` — so R8 and shrinking are on and it is not debuggable — with the debug signing config and the `.debug` suffix. **Committed**, and never published. |
| *(androidTest)* | Not a build type: the instrumented APK, built from `debug`. |

**Why `perf` exists.** A CPU figure has to be measured on something users would run. A
debug build is not that — a figure taken on it is a property of the debug build — and a real
release build cannot be signed on a development machine, because the release
key exists only on the maintainer's. `perf` is the intersection: release-like code, debug signature.

**Why it is committed.** So that two measurements of the same thing measure the same binary, and
no hand-written block can reach a release because someone forgot to delete it.

**What keeps committing it safe.** `BuildTypeDeclarationTest` pins the declared set of build types,
pins `perf` to `initWith(release)` + the debug signing config + `.debug` + `isDebuggable = false`,
and asserts that no workflow builds it.

### Workflows

`.github/workflows/android-build.yml`
- `build` job on a push to `main` or of a `v*` tag, a pull request to `main`, and a manual run: lint, unit tests, `assembleDebug`, artifact
  upload. Never sees release secrets.
- `release` job, only on a pushed `v*` tag -- never on a merge to `main`: checks
  required secrets, decodes the keystore to a runner temp path, builds a signed
  release APK, emits a SHA-256 checksum, produces a Sigstore build-provenance
  attestation, refuses to overwrite an existing release, composes the body from
  `release-notes/<tag>.md`, and publishes. The tag is validated against
  `versionName`, not `versionCode`.

There is **no third job**: CI runs no emulator, and **the instrumented tests are run on a phone
instead** — see *Testing* below.

The workflows set up a Temurin JDK 17, which builds
AGP 9.4.0 / Gradle 9.7.1; the wrapper jar matches the SHA-256 Gradle
publishes for 9.7.1 so wrapper validation passes, and `compileSdk 37`
needs nothing installed: the `ubuntu-latest` runner image ships
`android-37.0` alongside `android-36`, and build-tools 36.0.0, which is what
AGP 9.4.0 selects by default.

`.github/workflows/dependency-submission.yml`
- Submits the resolved dependency graph on push and weekly, feeding Dependabot
  alerts. Note: this raises alerts but does **not** open update PRs; there is no
  `dependabot.yml`.

All actions are pinned to full commit SHAs. `gradle/actions` is deliberately
held at v5.x for licensing reasons documented inline.

### Dependency security

**The dependency graph this repository submits is the build's, not only the APK's.**
GitHub attributes every alert on it to
`settings.gradle.kts` -- a file that declares no dependency at all, only repositories. The
attribution is an artefact of how the workflow above reports: it submits the *resolved graph of
the build*, and GitHub labels the whole graph with the settings file. Reading the label as a
location will send you to the repository declarations and no further.

Most of that graph is the Android Gradle Plugin's own transitive closure. The
app's declared dependencies are androidx, Compose, DataStore and coroutines, plus JUnit and
`org.json` for tests. An alert on a build-time dependency is not fake -- this code runs on
developer machines and CI runners with the checkout in front of it -- but the severity of a
*shipped* vulnerability does not apply to it.

Versions are forced in two places that are easy to mistake for one. The root `build.gradle.kts`
forces the **plugin classpath**; `app/build.gradle.kts` forces this project's own configurations,
of which `androidLintTool` is the one that matters: a version lifted on the plugin classpath by
ordinary conflict resolution can survive there. **`buildEnvironment` alone cannot tell you a fix is
complete; `:app:dependencies` can.**

The check that says whether the graph is clean is not a document. Resolve the graph and scan every
coordinate it contains -- the OSV API answers unauthenticated, and the GitHub Advisory Database
is what it mirrors:

```bash
./gradlew --no-daemon buildEnvironment :app:dependencies
# then query https://api.osv.dev/v1/querybatch for each resolved group:artifact:version
```

### Verified build

A build's result is an event, not a property of the tree, so no figure from one is written here;
this runs the build and its checks:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Two figures the build does not print plainly, and how to read them:

- **Kotlin compiler warnings.** The compiler prints them only for the files it compiles, so they
  are counted on a build from scratch:
  `./gradlew --no-daemon --rerun-tasks testDebugUnitTest compileDebugAndroidTestKotlin 2>&1 | grep -c '^w: '`.
  A warning that cannot be removed is suppressed
  at its line with the reason beside it — `WakeLatch`'s lock, which is a `java.lang.Object`
  because the render loop parks on its `wait`; the platform's deprecated trim levels in
  `RegistryAndTrimPolicyTest`, which the policy still receives below API 36 — so a warning that
  appears is a new one. Nothing fails the build on one.
- **Lint's severities.** The SARIF report files every finding as a warning; the XML
  and the HTML reports keep warnings and hints apart. What lint
  still reports is declared rather than silenced: the notices that a newer Gradle or a newer
  dependency exists, which move with the calendar;
  `ConfigurationScreenWidthHeight` in `SettingsInsets.kt`, left
  as it is because the code was checked on an Android 16 phone (2026-09-28) and there is
  nothing measured wrong in it to change. What moves to a new phone is declared rather than
  left to a lint finding: nothing does
  (`res/xml/data_extraction_rules.xml`). The findings that are true of the
  code and not a defect of the project are suppressed where they are, each with its reason: at the
  line in Kotlin, the manifest and the build script, and by exact path in `app/lint.xml` for the
  people's un-suffixed bases and the picker's thumbnail.

### Testing

There are **two layers**, and the split is deliberate: what can be answered without a device is
answered without one.

**JVM tests** live in `app/src/test/kotlin/`, mirroring the main source package layout. They are
plain JVM tests: what they exercise calls no Android framework method, and where a class does
hold Android types the *testable half* is split out into one that does not — `IntLruSlots` under
`TintFilterCache`, `IntKeyLruSlots` under `GradientShaderCache`, `SpriteCacheIndex` under
`SpriteCache`, `SceneTransform` and `SceneShape` under the backends. **These are the tests CI
runs.**

**Instrumented tests** live in `app/src/androidTest/kotlin/` and need a device. **CI does
not run them** — see *Workflows* above — so they are run
locally, on the project's phone (there is no emulator), before every release. They are not optional and not decorative:
they are the only thing in the project that looks at a rendered frame.

The two tables below are a selection, not the whole suite: the JVM layer first, then the instrumented one.

| Test class | Covers |
|---|---|
| `CloudCoverageTest` | The coverage field (falloff shape, saturation, edge clamping, frame reset) and the rain-follows-cloud rule: no rain from clear sky, uniform fallback when clouds are hidden, overcast reproduces the full drop set, no drop displacement when cloud cover changes |
| `CandidateSystemTest` | The candidate-system invariants: determinism per theme, density-independent attributes, stability of survivors across density, monotonicity in both directions, independence from filtered-out candidates, effect decorrelation, the small-pool guarantee, and distribution quality |
| `MemoryPressurePolicyTest` | Trim-level mapping, including that `TRIM_MEMORY_UI_HIDDEN` never evicts despite its numeric value exceeding `TRIM_MEMORY_RUNNING_CRITICAL`; unknown-level handling; mirrored constants match the platform |
| `SpriteCacheIndexTest` | Cache bookkeeping: byte accounting, LRU eviction order, eviction to a byte budget, slot reuse, growth, repeated fill/release cycles |
| `SceneTimeTest` | Bounded time base: accumulation past the 12.14-day Float freeze point, range and smoothness of every helper at one-day/twelve-day/one-year uptime, cycle continuity across wraps, walk-frame ordering, absence of NaN/infinity |
| `SliderDragStateTest` | Slider drag handover: thumb tracks the finger, exactly one commit per drag, no commit when a drag returns to its origin, no snap-back while a write is in flight, correct ordering for two rapid drags |
| `SceneCustomizationStructureTest` | Structural vs cosmetic classification for all 12 categories, the static/car separation that keeps cars running, and a reflection guard that fails if a new category is added without updating the comparison |
| `IntLruSlotsTest` | Bounded LRU slot allocation: capacity is never exceeded under a continuous stream of new keys, exact LRU eviction order, slot recycling, hot-key retention |
| `SceneObjectCullingTest` | Off-screen culling: no early clipping at either edge and continuous visibility while scrolling |
| `SceneObjectTileCullingTest` | Tile enumeration: bit-exact equality with the fixed three-copy loop over a sweep of more than 50 000 cases, agreement with a brute-force scan of offsets -40..+40, the `floor` start-offset contract, inclusive behaviour at both edges, exact tile boundaries, degenerate tile widths, out-of-range anchors, and that two copies of one object can never overlap |
| `CircleTableTest` | Every entry of the circle tables against the tessellation loop's own `cos`/`sin` expression, bit for bit, for every segment count from 8 to 64 |
| `SpriteEntryIndexTest` | The texture cache's hash lookup against a linear scan, on random tables through growth and clears |
| `WakeLatchTest` | The render thread's park with real threads: a wake before the park stops it, a wake during it ends it, 5 000 handovers with no timeout to rescue a lost one |
| `DayPhaseCacheTest`, `FirstFrameGateTest` | The engine's day phase equals a fresh compute over random input sequences; the first frame waits for both stores or the backstop, and is released once |
| `ShopPlanTableTest` | The generated shop plan is `planShopPositions`'s output to the bit, and the twelve streets laid out from it are the planner's; on a mismatch it writes the table it should be |
| `ApkCachePruneTest` | An installed or older APK in the downloads folder is deleted, a newer one kept |
| `SunPositionCalculatorTest` | Day/night classification, `progress` and `dayBlend` contracts, the celestial arc, sunrise/sunset approximation (equinox day length, hemispheric asymmetry, polar clamping, longitude offset), moon phase cycling, and the clock reading, pinned against `Calendar` at tolerance `0f` across eight time zones, a year of non-hour-aligned samples, and pre-epoch instants |
| `SeasonalThemeRulesTest` | Computus against published Easter dates 1900–2100, the Sunday and 22 Mar–25 Apr invariants across 1900–2200, window boundaries and precedence, and that every rule resolves to an id present in `ThemeCatalog` |
| `SeasonalCalendarIdentityTest` | Transcribes the v5.0 table and walks it against the factory calendar for fifteen years, allowing exactly one difference — 1 March — and asserting it is present in every year and is `winter`→`spring`. Also walks the continuous autumn against a split one for ten years |
| `SeasonalCalendarStorageTest` | The calendar as a document: that the factory calendar stores nothing, that an edit back to a factory value stops being an override, the JSON round trip (wrapping spans and 29 February included), unreadable and partly-unknown documents falling back whole rather than in part, the same-tier overlap gate, Easter's exemption from it, and that a gap costs the day rather than breaking the calendar |
| `CustomThemeDataJsonTest` | Serialisation round trips (including all built-in themes), schema versioning and legacy compatibility, and defensive parsing of corrupt input |
| `UnreadableSavedThemeTest` | One saved theme that cannot be read hides none of the others, an edit is applied rather than dropped, and the unreadable entry's text survives every write byte for byte; a kept entry comes back migrated from its own schema, never over a newer save; a document with nothing unreadable carries no section for it |
| `JsonSpansTest` | The strict scanner that finds an unreadable entry's own text, and that it refuses the syntax `org.json` tolerates rather than guessing |
| `SavedThemeNameTest` | "Rename" changes the name the user sees: a saved theme renamed by any earlier build is read with the name the user wrote last (its `name`, which the reader puts in `theme.displayName`), every screen's lookup (`ThemeCatalog.byId`) and an exported file get that name, the next write carries it in both places, and a theme never renamed is read and written byte for byte unchanged |
| `PreviewPrecipitationColourTest` | A gallery card's rain and snow take the wallpaper's own rule -- the night colour blended toward the day colour by the day blend -- so the two midnight cards, Halloween and New Year's Eve, and any card forced to night, rain in the night colours; a day card is unchanged |
| `SeatedArtworkFacingTest` | The premise of `drawSeatedOccupant`'s mirror, read off the PNGs: the adult seated heads carry their hair behind the eye axis, so the drawing looks toward +x. `OccupantFacingTest` on the device asserts the behaviour |
| `IntKeyLruSlotsTest` | The multi-component key table `GradientShaderCache` runs on: exactness (a difference in *any* of the five components, including the zero padding, must miss), the capacity bound under a continuous stream of new keys, exact LRU order, slot recycling, and that two floats one ULP apart are distinct keys |
| `SolarDayPublicationTest` | That three separately-published fields can be read half-updated — demonstrated deterministically with a barrier, and with the fields already `@Volatile`, so it is a statement about the shape and not about a missing annotation — and that one immutable snapshot behind one `@Volatile` cannot be, under the identical interleaving and under 200 000 unsynchronised sampled reads |
| `RoadVehicleGeometryTest` | The road/vehicle ratios measured from `SceneSpace`'s own constants: lanes about one vehicle apart, the carriageway between 1.5 and 4 car-heights deep, the fire engine fitting inside it, the strip symmetric about the lane pair, and a degenerate lane pair still painting a full-width road |
| `CacheLifecycleTest` | The memory bound of every cache in the render path, which is why those caches need no `onTrimMemory` of their own: both key tables bounded whatever they are fed, the gradient cache's bookkeeping under a kilobyte, and `SpriteCacheIndex` accounting for megabytes of pixels it does not hold and releasing them on `clear()` |
| `PreviewRendererAgreementTest` | That the gallery preview and the wallpaper place a tree's and a palm's parts identically, across every theme, and that every building the gallery draws is a deal `NeighbourhoodComposer` makes for its declared identity |
| `WeatherApiComProviderTest` | The second weather provider: every published condition code resolves, and resolves to the right *side* (frozen / liquid / thunder / obscuring) as judged against the official English text, walked from the committed `conditions.json`; parsing of a full response, a sparse one, an error body and a snow code; and that a blank key makes no request |
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
| `SceneGoldenTest` | Committed PNGs rendered through `CanvasSceneTarget` — the backend that ships, not a test double — and compared per pixel: a frame admits no pixel that differs beyond the per-channel tolerance (`SceneGolden.MAX_DIFFERING_FRACTION` is 0). `GoldenScene` describes each frame as data so that when one changes, "did the scene change or did the drawing change" is answerable, and `GoldenFocus` names the patch a golden is about, with a gate of its own. **Do not quote a count here** — the figure that means anything is the number of `assertMatches` calls, not the number of files in the directory. `grep -rh 'SceneGolden\.assertMatches' app/src/androidTest --include='*.kt' \| grep -vc '^\s*\*'` counts them, and **its answer is one too high**: `LightningPinTest` hands `assertMatches` a scene it must *reject*, which is how the guard is shown to run rather than merely to exist. Subtract it. An assertion is also not a file — two scenes are each asserted more than once with different focus rectangles, so the assertion count and the PNG count are different numbers and neither is "the number of goldens" on its own. |
| `GlSceneGoldenTest` | A few of the same scenes rendered through the shipped `GlSceneTarget` on an offscreen EGL pbuffer, configured exactly as `GlRenderThread` configures it, MSAA included. Three gates: against its own committed `gl-*.png`, against the Canvas golden (the claim that the two backends still draw the same picture), and **against a named region**. |
| `PrefsCorruptionRecoveryTest` | That a damaged preferences file costs that store its contents and nothing else, including across a process restart. |
| `UnreadableSavedThemeStoreTest` | The same three properties on the phone's own `org.json` and the real DataStore: the damaged entry's bytes are read back off the store's file after each of three edits |
| `SavedThemeRenameStoreTest` | The real `CustomThemeStore.renameCustomTheme` on DataStore and Android's `org.json`: the new name is what the gallery card and `ThemeCatalog.byId` read back, and it is the theme's `displayName` in the file; a theme renamed by an earlier build is read with its new name |
| `OccupantFacingTest` | Every occupant looks the way their vehicle travels, for four vehicle types in both directions: the direction read off two frames of the real `SceneObjectRenderer`, the artwork's facing off the adult seated heads' hair masks, and the bust's blit off the transform a matrix-keeping `SceneCanvas` records. Reversing the mirror turns it red |
| `LakeWaterlineOrderTest` | A frame of Beach at its factory settings, in a thunderstorm, on a 720x1440 surface, 80.37 s in -- walked up to at 30 fps through the real `PaperRenderer` and recorded as a draw sequence: the wave and the boat whose waterlines are nearer than the leaping dolphin's belly must be painted after it |
| `FrameAllocationTest` | A steady frame allocates nothing: every built-in theme, rain, snow, a Live Weather storm, the busiest lake, the sleigh and the fireworks, 150 frames each after a warm-up, counted by ART, with a positive control for the counter |
| `PaintAlphaEquivalenceTest` | `setAlphaWithoutAllocating` leaves the paint as the platform's `setAlpha` does for all 256 values: colour, alpha, `colorLong` and the pixel drawn |
| `CanvasGradientAllocationTest` | Records the full argument tuple of every gradient the real renderer asks for over 60 animated frames, and checks the cache builds one `Shader` per *distinct* gradient rather than one per request. |
| `TrafficGoldenTest` | That the traffic goldens actually contain traffic, measured off the finished frame by `VehiclePresence` rather than inferred, that both lanes are occupied, that the frame is bit-identical across two renders, and that three plausible traffic regressions each move more of the frame than the golden's own budget. |
| `TreeArtworkAlignmentTest` | That the winter tree's snow cap lands entirely on the crown, with no opaque pixel of it off the crown. An assertion about the *artwork*, which nothing else checks. |
| `SkyWaterGoldenTest` | Three derived gates — the cloud band, the bird band and the water band — attached as `extraFocus` to golden scenes that already exist (`day`, `lake-busy`), each with a derived limit rather than the shared 2 % focus limit; and `waterline-worst-theme`, the one PNG it owns: the theme whose sky and water are closest, where only the struck waterline separates them. |
| `LakeDrawCallTest` | Counts every primitive the real renderer asks for, through the real `SceneCanvas`, with the water on and with it off. The difference is the water's own per-frame cost, measured rather than estimated. |

**`GoldenScene.warmUpFrames`.** A car's `progress` starts negative and only
advances inside `SceneObjectRenderer.update(deltaSeconds)`, so a golden drawn as one frame with
`deltaSeconds = 0` could never contain one. The traffic scenes
warm up `SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES` (thirteen seconds at 30 fps, the count chosen by measuring vehicle coverage from
0 to 600 frames) before the frame that is compared, which puts vehicles in the band with none clipped
by a frame edge. Warm-up is deterministic because the clock and the delta are pure inputs.

**The exception is a storm.** The lightning timer is the
only unseeded `Random` in the renderer, and `updateLightning` leaves it alone unless a storm is
active, so a storm warmed up over many frames can draw a strike's full-screen veil on the compared
frame and fail by the entire frame. The rule is
`GoldenScene.requireDeterministicLightning`, run by both harnesses before they render, and a scene
that needs to warm a storm up says so with `GoldenScene.pinLightning`, which clears
`PaperRenderer.lightningStrikesEnabled` **for that render alone**. The wallpaper's own lightning is
untouched: nothing in `src/main` writes that flag.

**The wall clock is pinned too.** A golden
is a claim that a frame is a function of the scene written beside it, and everything the harness
pins it pins by *passing* — theme, customisation, scene clock, day phase, scroll, lightning. The
time of day has to arrive the same way:
the moon's phase travels in `SunPositionCalculator.DayPhase.moonPhase`, the
wallpaper service fills it in from the real moon, and a caller that names none gets
`SunPositionCalculator.FIXED_MOON_PHASE`. Two checks keep it there:
`SceneGolden.assertReproducesOverTime` renders every Canvas golden a second time with the device's
wall clock moved 191 days (`DeviceClock`, through `cmd alarm set-time`) and requires the two frames
to be identical pixel for pixel, and `RenderPathReadsNoWallClockTest` refuses a clock read anywhere on
the render path -- every file of the `engine` package except the three that hand the
clock in (the wallpaper service and its two calendars), plus the card painter `ui/ThemePreview.kt`
-- and in `SunPositionCalculator.compute`. The
behavioural one is the one that catches a leak; the source rule is what covers a leak in a theme or
a weather no golden renders.

**The GL region gate exists because the whole-frame gates provably could
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

**A phone is also required to release**, because the instrumented layer above is not run by CI and
a release is not verified without it. The project's is a Blackview BV6600 (PowerVR GE8320, Android
10), and the committed GL references are that driver's: `GlGolden.EdgeDisplacement`
keeps the figures of the drivers before it. A second GPU is a second driver — the region
thresholds were set by measuring the same frame under two, and that comparison is the only way to
tell a driver difference from a regression.

---

## 9. Known architectural weaknesses

Recorded here so they are not rediscovered from scratch. **Each is kept as it is, by
decision**: none is visible on the phone, and working on one is new work.

1. **Partial source pipeline for assets.** `tools/assets/` gives part
   of the sprites an SVG source and a deterministic rasterisation path; the rest
   are written by a generator with no SVG of its own (the people's layers, the
   neighbourhood); none is its own source any more. `validate`'s first line counts both
   (`registry OK: … entries, … with an SVG source, … recorded as gaps`). What
   remains is the hand-tuned anchors of the parts and the people, which block nothing.
   Every sprite without an SVG is
   written by a committed generator, which its registry entry names
   (`tools/generate_people_layers.py` or `tools/assets/buildings/build_neighbourhood.py`).
2. **More than one metric, and two sprite conventions.** The ground plane has one model,
   `SceneSpace`, but the lake keeps its own metric and the sky's bodies, the birds, the sleigh and
   the fireworks are composed for legibility; and sprites are authored in two scale conventions
   (`SCENE_UNITS`, `CANVAS_PIXELS`) that nothing in a PNG records. Each sprite's convention is
   declared in `tools/assets/sources/sprites.json`, and `validate` checks every call site it can
   resolve against it.
10. **Test coverage is narrow in one place.** The JVM suite
    covers the pure deterministic logic, and the instrumented layer observes rendered frames on
    both backends: the Canvas goldens and the GL references.
    No test runs the engine itself or the Compose UI: `PaperWallpaperService`, with its surface and
    visibility callbacks and its loops, and the screens cannot be unit tested without being
    decoupled from `Canvas` and `Context` first. Their rules are extracted into pure objects that
    are tested (`GlLifecyclePolicy`, `LiveWeatherSchedule`, `SolarDaySchedule`), their call sites are pinned by reading the source, and the instrumented suite runs whole on the test phone before every release.
    The preferences layer is reached: `WallpaperPrefs` takes its DataStore in an internal
    constructor (the app's public one passes the process's store), so the preference reader and
    its writers run on the JVM over a scratch file (`PalmsPreferenceStoreTest`, `ResetsDoWhatTheySayTest`),
    and the instrumented layer runs the real stores on the phone.
    The settings screens' *derivations* are pure and
    unit-tested (`SettingsUiModel`, and `MoonPhaseControlTest` for the Halloween override), and the
    call sites that consume them are pinned by reading the source, because there is no
    `createComposeRule` in this tree and nothing composes a screen in a test. **No composable is
    rendered by any test**.
    The preview/renderer sprite-offset agreement is
    pinned for the trees, the palms and the buildings; the rest is
    left to `ThemePreviewTruthTest`, which holds the gallery card's families, order
    and water to the scene.
11. **The atlas cannot reclaim space.** The skyline packer (§3)
    has no way to free a single entry; it is only ever added to, and reset
    wholesale. It also fills in first-draw order, so a scene whose sprite set exceeds
    2048² pushes its *later* sprites — the objects and people, which benefit most —
    out to standalone textures. Neither has been observed to matter, and neither is
    worth fixing before it does: 583 of the page's 2048
    rows in use and no sprite standalone (measured on the BV6600, 2026-09-27).
12. **Each engine has its own EGL context**, so the picker's preview engine and the
    live engine do not share textures the way they share `SpriteCache`'s bitmaps.
    Measured on the BV6600 on 2026-09-28: `dumpsys meminfo`'s GL memory for the process reads
    33.0 MB with the wallpaper's engine alone, 61.3 MB with the picker's preview open
    beside it (reached from the settings screen), and 41.3 MB back on the home screen with the
    settings screen's own memory still held: the second engine is about 20 MB of GL memory and
    6 MB of EGL buffers, and only while the picker's preview is open.
13. **No localisation.** Almost every UI string is a literal in Compose rather than a
    `strings.xml` entry, and the app is English-only by decision;
    `grep -rc stringResource app/src/main --include='*.kt'` counts, file by file, the ones read through `stringResource`.
14. **A framework Material 1 theme under the Material 3 one.** The Compose colour
    scheme is the full Material 3 scheme (`ui/theme/PaperScrapeTheme.kt`, `LightColors`),
    but `themes.xml` still inherits from a framework Material 1 theme. No visible effect is
    known.
