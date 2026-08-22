# ARCHITECTURE.md

Technical description of PaperScrape as it exists today. This document
describes the **current** implementation, including its known weaknesses.
Planned work belongs in `ROADMAP.md`; visual and design decisions belong in
`DESIGN_NOTES.md`.

**Last fully verified against v3.7** (`versionCode = 28`), by reading the source and running
`test` + `lintDebug` + `assembleDebug` + `assembleRelease` and the instrumented suite on an
Android 17 device.

This stamp had said *"v75 … current as of v1.0 Stable (`versionCode = 1`)"* for twenty-seven
releases, which is the whole of **P2-8**: sections were updated as work landed, so most of the
document was in fact current, but nothing said which — and a validity stamp nobody can trust is
worse than none. v3.7 re-read the document against the source in full and closed the item. Every
file in `engine/` now appears in the table below; fourteen did not before, including four the same
release added.

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

| Metric | Value |
|---|---|
| Kotlin files | 46 |
| Kotlin lines | ~11,100 |
| Sprite PNGs in `drawable-nodpi/` | **111 files, 111 unique contents** — no byte-identical pair; the V2 asset library replaced the whole set in v76 |
| Vector drawables | 4 |
| Unit tests | **548** (45 classes, JVM-local, no Android dependencies) |
| Instrumentation tests | 0 |
| Largest files | `PaperRenderer.kt` 1,728 · `SceneObjectRenderer.kt` 1,152 · `WorldSceneScreen.kt` 813 · `WallpaperPrefs.kt` 789 · `SettingsComponents.kt` 736 |

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
| `ShelfPacker.kt` | Where each entry sits in the atlas, as pure testable arithmetic. |
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
| `SeasonalThemeRules.kt` | Date-based automatic theme selection (includes a Computus implementation for Easter). |
| `SunPositionCalculator.kt` | Day phase, sun/moon arc position, moon phase, simplified sunrise/sunset. |
| `FireworkEffect.kt`, `SantaSleighEffect.kt` | Self-contained timed effects. |
| `SceneSpace.kt` | **The one place the world's size is stated.** The horizon, the ground plane's projection, the road's lanes and edges, and every category's real height in metres against the local units its art occupies. Every base scale is derived here, so the ratios between objects cannot be edited one at a time. |
| `SceneTime.kt` | Scene time as a `@JvmInline value class` over `Double`, with every read bounded at the point of use. Replaces a `Float` accumulator that stopped advancing after ~12 days of visible uptime. |
| `SolarDay.kt` | Today's sunrise, sunset and whether they came from a real position, as one immutable value (**P2-6**, v3.6). Published through a single `@Volatile` reference on the engine so the render thread cannot read a sunrise from one location beside a sunset from another — which three separate fields, `@Volatile` or not, allow. |
| `LakeLanes.kt` | Which lane each lake decoration occupies and how deep it sits, so boats cannot share a line and a leaping dolphin sorts by where its body is rather than by the lane it left. |
| `CandidateNoise.kt` | The stable per-candidate pseudo-random values the stateless candidate model is built on: same slot, same value, every frame, with density thinning and colour-variant assignment deliberately drawn from uncorrelated streams. |
| `CloudCoverage.kt` | How many clouds a cover fraction means, shared by the theme's own setting and Live Weather's. |
| `PeopleDensity.kt` | How many pedestrians a density setting means, on the same pattern. |
| `TreeSpriteLayout.kt` | Where a tree's trunk, crown, snow cap and bare branches sit, stated once for both the wallpaper renderer and the gallery preview (v3.7 Filone C). The preview builds its objects from the same sprites at the same offsets by hand, and the snow cap's copy had drifted 3 units right and 2 down; both now read from here. |
| `SpriteCache.kt` / `SpriteCacheIndex.kt` | The bitmap cache and its bookkeeping. The index is `SpriteCache`'s own `private val` — ids, byte counts and LRU order in `IntArray`s, deliberately free of Android types so the eviction logic is unit-testable, and cleared by the same `clear()` the memory-pressure path calls. |
| `MemoryPressurePolicy.kt` | What an `onTrimMemory` level means for a wallpaper, as a pure decision. Notably `TRIM_MEMORY_UI_HIDDEN` is *not* treated as pressure, though its numeric value sits above `RUNNING_CRITICAL`: for a wallpaper it only means the settings screen closed. |
| `TintFilterCache.kt` / `IntLruSlots.kt` | A bounded, exact-LRU cache of `PorterDuffColorFilter`s keyed by colour, so a tinted blit does not allocate a filter per sprite per frame. Global, and therefore `@Synchronized`; released on `RELEASE_ALL`. |
| `GradientShaderCache.kt` / `IntKeyLruSlots.kt` | The same pattern for gradient `Shader`s (**P2-5**, v3.6), with a multi-component key because a gradient is four or five numbers rather than one. Owned **per `CanvasSceneTarget`** rather than globally, so a draw call takes no monitor. Measured: the Canvas backend built 180 `Shader` objects over 60 frames for 3 distinct gradients, and now builds 3. |

### Other packages

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
  needs no network where a device supports it.
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
    `MISSING_API_KEY`, `FAILED`, `STALE`), written by the service and read by the settings screen.
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
             │     ├─ SunPositionCalculator.compute(hour, sunrise, sunset) → DayPhase
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

`GlTextureCache` decides placement per sprite: into the atlas when it fits, into a
texture of its own when it does not. Callers get a handle and a UV rectangle either
way, so a standalone texture is just the `0..1` case. Large sprites are excluded on
purpose — the sleigh alone is 1563×434, and letting it consume a shelf row would
push out the small sprites that actually repeat per frame, while itself costing only
one batch break because it is drawn once.

`ShelfPacker` holds the placement arithmetic, separately and without GL, for the
same reason `SceneTransform` is separate: a packing bug is silent. Two entries given
overlapping rectangles do not throw — one sprite renders with another's pixels inside
it, in whichever scene happens to draw that pair.

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
once in two thin `drawSprite`/`drawTintedSprite` wrappers instead of repeating
it at 60 call sites. `PaperRenderer` is the only class that mixes conventions,
so it has no wrappers at all: each of its 12 call sites names its own scale.

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
from what was intended. In practice it holds 2 copies, and 3 only in the ~1 % of
the cycle where a sprite extent crosses a seam.
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

A category's base scale is **derived**, not authored: each declares the real
height it should read as and the local-unit height its own drawing occupies
(`SceneSpace.SceneVariant`, plus the vehicle and person constants beside it).
That derivation is necessary because the sprites are authored at incompatible
internal scales -- roughly 13 units per metre for a shop front against 46 for a
person -- which no single global multiplier can correct. The full table is in
`DESIGN_NOTES.md` §5.

`SceneObjectRenderer.variantFor` resolves which drawing a static object is
(small or large house; tower, restaurant or bar) once, and both the size and the
dispatch come from that one answer. Buildings choose by depth rather than by a
position hash, so towers sit on the skyline and shop fronts among the houses.

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

### Theme resolution

```
settings.themeId
   └─ if autoThemeByDate → SeasonalThemeRules.themeForDate() may override
        └─ CustomThemeRegistry.resolveActiveCustomization(themeId, pending…)
             ├─ user override for a built-in theme, or
             ├─ saved custom theme's own customization, or
             ├─ in-progress live edit (if tagged for this exact theme), or
             └─ defaultCustomizationFor(themeId)
```

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
exist, because those are the only fields `keepCandidate`/`keepCar` read.
Everything else — all 48 category colours, the sky/stars/clouds/precipitation/
rainbow/mountain/lake/bird sections, hill variation, the seasonal palette
flags — is consumed at draw time. `SceneCustomization.staticStructurallyEquals`
and `.carsStructurallyEquals` encode that distinction as pure, allocation-free
field comparisons (not a hash: a collision would silently skip a needed
rebuild).

The static and car lists are compared separately so that changing, say, house
density rebuilds the static objects **without** resetting every car's in-flight
`progress` along the road. Rebuilding the static list is visually free, since
`StaticRuntime` holds only an `idleSeed` derived deterministically from its
spec; rebuilding the car list is not, which is why it is gated on the car
config alone.

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

Measured footprint if every sprite is decoded. Every figure here is produced by
`python3 -m paperscrape_assets inventory` and is reproducible; the full
per-sprite table is `tools/assets/reports/runtime-inventory.md`.

| Metric | Value | Was (v75) |
|---|---|---|
| Files / unique contents | **111 / 111** | 108 / 102 |
| Total decoded `ARGB_8888` | **14.43 MB** | 15.39 MB |
| Largest single sprite | `cloud_body.png` 876×477 → 1.67 MB | same |
| Second largest | `skyscraper_wall.png` and `skyscraper_wall_lit.png`, 270×450 → 0.49 MB each | `santa_sleigh_scene.png` 1563×434 → 2.71 MB |
| `santa_sleigh_scene.png` | 624×168 → **0.42 MB** | 1563×434 → 2.71 MB |
| **36** `person_*` sprites | 3.29 MB | 3.29 MB across the same 36 files |
| Off the 3× authoring grid | **0** | 5 |

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
only their output. `AI_PROJECT_RULES.md` §6.1 now forbids exactly that.

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

Two properties of the rule are the reason it is a rule and not a per-sprite
judgement:

- **Outward rounding keeps the compensation an integer.** The blitter multiplies
  the origin by the same unit the compensation divided by, so cropping to the
  measured box would produce fractional units that return as sub-pixel positions
  and get resampled through `FILTER_BITMAP_FLAG`. The price is up to `unit - 1` px
  of retained padding, which is load-bearing rather than leftover.
- **The union holds a lookup group together.** 44 sprites are selected from a
  table at draw time and blitted through a single origin literal — the walk
  frames, the window occupants, the car drivers. Their content boxes differ, so a
  per-member crop would need per-member origins that do not exist, and the walk
  cycle would jitter. Sprites that merely share an origin *value* are not a group:
  two call sites with their own literals each take their own crop.

`normalize` runs in check form as part of `paperscrape-assets all`. **The invariant
it enforces no longer describes the shipped set**: the V2 library places drawings
inside canvases sized on the grid and declares the content box, so 34 sprites carry
margin on purpose and cropping them would move them. The JVM-side check that
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

**Current coverage: all 111 sprites have a source.** That is new in v76 and it is
the single most consequential thing the V2 asset library changed.

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

Schema 4 declares, for every sprite, the metadata `AI_PROJECT_RULES.md` §6.2
requires: a `contentBox`, an `anchorRule` with the `anchor` it derives, the scale
convention, the tint class and the season. `contentBox` is re-derived by `validate`
rather than trusted, so it cannot drift away from the PNG it describes.

Two entries carry a `notes` field recording a disagreement between the V2 manifest
and the shipped call sites, resolved in opposite directions. `star_sparkle` is
declared `CANVAS_PIXELS` by the manifest, which is defect D-1 restated — read as
raw pixels the 180 px sparkle covers 180 local units against a star's own 32 — so
the registry keeps the call site's `SCENE_UNITS`. `santa_sleigh_scene` is declared
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

See `DESIGN_NOTES.md` §4 for the authoring conventions and
`tools/assets/README.md` for the commands.

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

### Continuous controls

All 16 `Slider` call sites go through `PreferenceSlider`, which holds the
in-flight value in local Compose state for the duration of the drag and writes
to DataStore **once**, from `onValueChangeFinished`, and only when the value
actually changed. Value captions are rendered by the same composable from the
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
| Android Gradle Plugin | 9.3.1 (verified present on Google Maven) |
| Gradle | 9.7.1 (wrapper jar SHA-256 matches the checksum Gradle publishes for 9.7.1) |
| Kotlin Compose plugin | 2.2.21 |
| Kotlin | AGP built-in, driven by the Compose plugin version above -- 2.2.21 (the `org.jetbrains.kotlin.android` plugin is intentionally not applied) |
| `compileSdk` | 37 |
| `targetSdk` | 36 |
| `minSdk` | 26 |
| Java compatibility | 17 |

**`compileSdk` and `targetSdk` are deliberately one apart.** `compileSdk 37` says
only which `android.jar` the code links against; it is what `androidx.core 1.19`
and the Compose `1.12` line require (`minCompileSdk=37` in their AAR metadata) and
it changes nothing about how the app runs. The platform's behaviour gates read
`targetSdk`, which stays at 36 so the dependency upgrade could not move the app's
behaviour. Raising `targetSdk` to 37 is its own change with its own device pass --
see `ROADMAP.md`.

Dependencies are declared as hardcoded version strings; there is no Gradle
version catalog. They were brought to the current stable line in the Phase 2
upgrade (Compose BOM `2026.08.00`, `core-ktx 1.19.0`, `appcompat 1.8.0`,
`lifecycle 2.11.0`, `activity-compose 1.13.0`, `datastore-preferences 1.2.1`,
`coroutines 1.11.0`). Nothing is on an alpha, beta or rc.

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
upload the evidence. `AI_PROJECT_RULES.md` 10.12 states what any future auxiliary job must satisfy
before it gates anything, and 10.13 records why bounding a diagnostic's exit status is not the same
as bounding its time. **The instrumented tests themselves were not removed** — see *Testing* above.

Neither workflow needed a change for the Phase 2 upgrade. JDK 17 still builds
AGP 9.3.1 / Gradle 9.7.1 (checked locally on a Temurin 17 that matches the
`setup-java` step, not inferred), the wrapper jar matches the SHA-256 Gradle
publishes for 9.7.1 so wrapper validation still passes, and `compileSdk 37`
needs nothing installed: the `ubuntu-latest` runner image already ships
`android-37.0` alongside `android-36`, and build-tools 36.0.0, which is what
AGP 9.3.1 selects by default.

`.github/workflows/dependency-submission.yml`
- Submits the resolved dependency graph on push and weekly, feeding Dependabot
  alerts. Note: this raises alerts but does **not** open update PRs; there is no
  `dependabot.yml`.

All actions are pinned to full commit SHAs. `gradle/actions` is deliberately
held at v5.x for licensing reasons documented inline.

### Verified build (current environment)

| Task | Result |
|---|---|
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL**, `app-debug.apk` 19.07 MB |
| Kotlin/Java compiler warnings | **0** |
| `./gradlew test` | **BUILD SUCCESSFUL** — 50 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew lintDebug` | **BUILD SUCCESSFUL** — 88 warnings, 0 errors, 0 fatal |

Lint warning breakdown: `UseKtx` ×33, `UnusedResources` ×23,
`IconDuplicates` ×16, `GradleDependency` ×9, plus single instances of
`OldTargetApi`, `UnusedAttribute`, `VectorRaster`, `AndroidGradlePluginVersion`,
`NewerVersionAvailable`, `DataExtractionRules`, `ObsoleteSdkInt`.

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
| `CustomThemeDataJsonTest` | Serialisation round trips (including all built-in themes), schema versioning and legacy compatibility, and defensive parsing of corrupt input |
| `IntKeyLruSlotsTest` | The multi-component key table `GradientShaderCache` runs on: exactness (a difference in *any* of the five components, including the zero padding, must miss), the capacity bound under a continuous stream of new keys, exact LRU order, slot recycling, and that two floats one ULP apart are distinct keys |
| `SolarDayPublicationTest` | **P2-6.** That three separately-published fields can be read half-updated — demonstrated deterministically with a barrier, and with the fields already `@Volatile`, so it is a statement about the shape and not about a missing annotation — and that one immutable snapshot behind one `@Volatile` cannot be, under the identical interleaving and under 200 000 unsynchronised sampled reads |
| `RoadVehicleGeometryTest` | **Filone B.** The road/vehicle ratios measured from `SceneSpace`'s own constants: lanes about one vehicle apart, the carriageway between 1.5 and 4 car-heights deep, the fire engine fitting inside it, the strip symmetric about the lane pair, and a degenerate lane pair still painting a full-width road |
| `CacheLifecycleTest` | **Filone F.** The memory bound of every cache in the render path, which is what the "no `onTrimMemory` needed" verdict rests on: both key tables bounded whatever they are fed, the gradient cache's bookkeeping under a kilobyte, and `SpriteCacheIndex` accounting for megabytes of pixels it does not hold and releasing them on `clear()` |
| `PreviewRendererAgreementTest` | **Filone C.** That the gallery preview and the wallpaper place a tree's parts identically — 59 sprite placements across 12 themes — after an audit found the snow cap's hand-copied offset had drifted from the renderer's |
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
| `SceneGoldenTest` | 14 committed PNGs rendered through `CanvasSceneTarget` — the backend that ships, not a test double — and compared per pixel. `GoldenScene` describes each frame as data so that when one changes, "did the scene change or did the drawing change" is answerable. `GoldenFocus` re-checks named patches on their own much smaller area, because 0.2% of a 360x800 frame is 576 pixels and a dolphin covers 160. |
| `GlSceneGoldenTest` | Three of the same scenes rendered through the shipped `GlSceneTarget` on an offscreen EGL pbuffer, configured exactly as `GlRenderThread` configures it, MSAA included. Three gates: against its own committed `gl-*.png`, against the Canvas golden (the claim that the two backends still draw the same picture), and — since v3.7 — **against a named region**. |
| `PrefsCorruptionRecoveryTest` | That a damaged preferences file costs that store its contents and nothing else, including across a process restart. |
| `CanvasGradientAllocationTest` | **P2-5.** Records the full argument tuple of every gradient the real renderer asks for over 60 animated frames, and checks the cache builds one `Shader` per *distinct* gradient rather than one per request. |

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
build-tools stays at 36.0.0, which is what AGP 9.3.1 selects by default. See `CLAUDE.md` for the
reproducible setup procedure used in ephemeral environments.

**An Android 17 emulator is also required to release**, because the instrumented layer above is not
run by CI and a release is not verified without it. Two GL drivers are worth having available:
`swiftshader_indirect`, the software rasteriser the committed GL goldens were taken under, and the
host-GPU translator — v3.7's region thresholds were set by measuring the same frame under both, and
that comparison is the only way to tell a driver difference from a regression.

---

## 9. Known architectural weaknesses

Recorded here so they are not rediscovered from scratch. Prioritisation and
sequencing live in `ROADMAP.md`.

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
    false since v3.2: 14 Canvas goldens and 3 GL goldens do exactly that, and v3.7 added a
    region-targeted GL gate. What remains true is the shape of the gap. The engine lifecycle, the
    preferences layer and the Compose UI are still untested and still cannot be unit tested
    without being decoupled from `Canvas` and `Context` first, which is deferred item **B5**.
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
13. **No localisation.** `stringResource` has zero usages; 13 of 15 declared
    strings are unused while ~71 literals are hardcoded in Compose.
14. **Incomplete Material 3 colour scheme.** Four roles defined out of ~30; the
    rest fall back to Material's baseline palette. `themes.xml` still inherits
    from a framework Material 1 theme.
