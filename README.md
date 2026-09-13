AI SLOP WARNING! I'm not a developer just a humble Networker. I don't know how to code. I just asked Chatgpt and Claude to do this app and that's it! Feel free to use it :)

# PaperScrape

An Android live wallpaper: a layered 2D paper-cutout world with an animated
environment, themes, seasonal elements and parallax.

---

## What it does

PaperScrape replaces your home screen background with a small landscape that keeps
moving on its own.

- **A live wallpaper**, not a static image. Hills, a lake, a village and a road, drawn
  every frame.
- **A day that follows yours.** The sun and moon move with your device clock, and the
  whole palette blends from night through dawn to day and back. Sunrise and sunset
  times can come from the clock alone, from your location, or from a place you pick —
  by searching for it by name, or by entering coordinates.
- **Twelve themes** — sunset, autumn, winter, spring, desert, Christmas, new year, beach,
  city, tundra, Easter and Halloween — with optional automatic switching by date, which
  covers every day of the year and moves Easter with the calendar. Each one's gallery card
  draws a small version of that theme's own world, so you can see what you are choosing.
- **Custom themes.** Save your own, built on any of the twelve, and keep them.
- **Every part of the scene is adjustable.** Houses, buildings, trees, umbrellas,
  cars, people, hills, mountains, clouds, stars, rainbows, the lake and its boats and
  dolphins: each can be shown, hidden, thinned out, and — where the artwork allows it
  — recoloured, with separate day and night colours.
- **Seasonal decorations** on any theme at any time of year: snowmen, presents,
  pumpkins, Easter eggs, penguins, rabbits, wildflowers, and snow that settles on
  roofs and trees.
- **Halloween**, and a **horror sky**, as two independent switches. The first carves the
  moon into a jack-o'-lantern and strips the trees to bare branches; the second turns the
  sky near-black with a hard orange horizon. The Halloween theme starts with both on;
  either can be turned off afterwards, in any combination, and neither touches winter,
  Christmas or the autumn palette.
- **Parallax.** Swiping between home screens scrolls the world, with nearer things
  moving further than distant ones.
- **Traffic and pedestrians.** Two lanes of cars, taxis, police cars and fire engines,
  each carrying an occupant; people walking the ground between the buildings and the
  road, dressed for the season.
- **Weather.** Rain, snow and cloud cover per theme — or Live Weather, which replaces
  them with the real conditions where you are, fetched from Open-Meteo (no account
  needed), WeatherAPI.com or OpenWeather (free account, own API key). If no location is available,
  or the chosen provider needs a key it does not have, it says so and falls back to the
  theme's own weather rather than quietly asking the other service.
- **Occasional visitors.** Santa's sleigh, fireworks, lightning and birds.
- **Traffic and shop hours.** The car count is an explicit setting rather than a curve — at
  its lowest a single sporadic car — and the road quietens by itself at dusk. Shops, the bar
  and the towers can be given opening hours: outside them nobody stands at the glass and the
  windows stay dark, even at night. Houses are homes and are unaffected. The toggle is off by
  default.
- **Realistic moon phases**, as an optional switch. Halloween overrides it — the carved moon
  is the theme's own — and shows the switch off and locked while that theme is showing, without
  overwriting what you chose.

---

## Visual style

**2D paper-cutout / paper-craft.** Flat layered paper shapes with soft drop shadows —
no 3D rendering, no perspective projection of geometry, no lighting model. Depth comes
from layering, from scale, and from where a thing stands on the ground.

All artwork is original and drawn for this project. Every drawn sprite has an SVG source in
the repository, and the drawing language is still being revised family by family: the tree
became a wide oak in v4.21, the sun, the sunburst, the four moon phases, the carved Halloween
moon and the star sparkle were redrawn in v4.23, the people — walking, at the windows and in
the cars — in v4.25, and the sky and the water in v4.26: the cloud, the bird, the sailboat and the
dolphin, plus the water's own surface, which is drawn by the renderer rather than by a sprite.

Two things in the scene are not sprites at all but **hairlines struck over a surface the scene
recomputes** — the water's cut edge and the falling rain — and neither can carry a colour of its
own. Both derive one per frame, far enough from what is behind them to be seen and no further, to a
separation measured across the twelve themes rather than chosen: the shoreline in v4.26 and the
rain in v4.27. Neither is tuned per theme — one derivation covers all of them, and the worst theme
is the one that sets it.

---

## Technical overview

Kotlin, minSdk 26, compileSdk 37, targetSdk 37.

**Rendering.** The scene is drawn through `SceneCanvas`, a small drawing interface with
two implementations. `GlSceneTarget` turns those calls into OpenGL ES 2.0 geometry and
is what runs on a device; `CanvasSceneTarget` delegates the same calls straight to
`android.graphics.Canvas` and serves the settings preview and the fallback path if EGL
setup fails. The scene renderers know only the interface.

**The render loop.** `GlRenderThread` owns the EGL context, the GL surface and the loop
for one wallpaper engine. A GL context is bound to one thread, so the loop leaves the
main thread — and because scene state is then mutated from a different thread than it is
read from, preference, theme, weather and scroll changes arrive as runnables executed
between frames rather than behind a lock. Sprites are uploaded into `GlTextureAtlas`
pages as they are first drawn.

**Scene geometry.** `SceneSpace` is the single source of truth for the ground plane, the
horizon, the perspective, the road and its lanes, the pavement and the size of every
category. A category's on-screen size is *derived* from a declared real-world height and
the local-unit height its own drawing occupies, rather than authored per sprite, so the
whole scene stays in proportion and scales with screen height.

**Themes.** `SceneTheme`/`ThemeCatalog` hold the twelve built-in palettes; `SceneCustomization`
holds per-category visibility, density and colours; `CustomThemeData` serialises user
themes to JSON with a versioned schema and migrations. `SeasonalThemeRules` decides the
automatic by-date theme.

**Settings.** A Jetpack Compose UI (Material 3, complete colour scheme derived from the
app's own palette) backed by DataStore Preferences. Five destinations — Weather & time,
Seasons & decorations, World & scene, Advanced & about, and the theme gallery — reached
from a home screen that says which theme is showing and who chose it. The wallpaper
service collects the preferences flow, so changes reach the running scene without a
restart.

**Theme previews.** A gallery card is a real mini scene, drawn from the shipping sprites
at the renderer's own part offsets with the theme's own palette, and containing only what
that theme actually has switched on. It is static: no GL context, no animation, and the
sprite pixels are shared with the rest of the process.

**Updates.** Advanced & about checks the GitHub Releases API, downloads the release's own APK,
verifies it against the SHA-256 the release publishes, and hands it to Android's installer, which
asks the user to confirm. A release without a checksum is not installed in-app at all. Nothing
downloads or installs without an explicit tap.

The flow reports four states — checking, downloading, verifying, ready to install — and cannot be
left stuck on any of them: a cancelled download returns the screen to the offer rather than freezing
on a progress bar. (v2.13–v2.16 could hang on `Downloading`; that was **D13**, fixed in v3.0.)

**Assets.** Every shipped PNG in `app/src/main/res/drawable-nodpi/` carries a registry entry in
`tools/assets/sources/sprites.json`, and most are generated from an SVG source under
`tools/assets/sources/svg/`; the rest name the generator that wrote them instead. A Python pipeline
renders, measures and checks them against that registry — which records every sprite's size, content
box, anchor rule, scale convention and tint class — and against the Kotlin call sites that blit
them.
(Counts are deliberately not written here: `ls app/src/main/res/drawable-nodpi/*.png | wc -l` is the
answer, and a number kept by hand in a document goes stale. Measured at v4.28: 305 PNGs, 143 of them
with an SVG source, 162 declared gaps.)

**People are a special case, since v4.30.** A person is not shipped once per colour. Each shape is a
**fixed layer** plus up to four **weight masks** — skin, head, shirt, trousers — and the renderer
composes `fixed + Σ (mask × colour)` at the blit, adding each mask rather than laying it over. Both
are written by `tools/generate_people_layers.py`, which also generates the engine's lookup table, so
a shape that gains or loses a region cannot be remembered in one place and forgotten in the other.

---

## Project structure

```
app/src/main/kotlin/com/paperscrape/livewallpaper/
  engine/     the wallpaper service, renderers, GL backend, scene model, themes,
              theme-preview scene descriptions
  ui/         Compose settings UI, one file per destination
  prefs/      DataStore preferences
  weather/    Live Weather: the provider interface, Open-Meteo, WeatherAPI.com, OpenWeather,
              and the normalised model they both produce
  location/   optional location for sunrise/sunset and weather
  update/     GitHub release check
app/src/main/res/drawable-nodpi/   the shipped sprites
app/src/test/                      Kotlin unit tests
tools/assets/                      SVG sources, sprite registry, Python pipeline
release-notes/                     user-facing notes, one file per release
```

Documentation: `ARCHITECTURE.md` is how the code works — the two rendering backends, the
scene graph, the asset pipeline and the test layers. `CHANGELOG.md` is the technical log of the
pre-release development, kept as history and not extended. What each release contains is on the
Releases page, and `release-notes/` holds the same text one file per tag.

The project's own working documents — the design reasoning, the plans, the backlogs and the
per-release verification reports — are not published. They are working notes between the author
and the assistants doing the work, not documentation, and they are written for that audience.

---

## Build

Requires JDK 17 and an Android SDK with platform 37 (Android 17). The Gradle wrapper is committed —
use it rather than a local Gradle install.

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew testDebugUnitTest      # unit tests; also compiles the whole debug source set
./gradlew lintDebug              # static analysis
./gradlew assembleDebug          # debug APK
```

Debug builds are signed with `debug.keystore`, committed at the repository root. It
holds no security value — the standard public debug alias and password — and exists so
every build, local or CI, is signed with the same certificate, which is what lets one
build update another. It is never used for release signing.

The asset pipeline is separate and optional; you only need it to regenerate sprites:

```bash
cd tools/assets
pip install -r requirements.txt
python3 -m paperscrape_assets probe     # must report matches_expected: true — run this first
python3 -m paperscrape_assets validate  # registry against shipped PNGs and Kotlin call sites
python3 -m unittest discover -s tests
```

`render` writes into `staging/` and never into the runtime asset directory; installing a
regenerated sprite is a deliberate copy.

---

## Release

Releases are built by GitHub Actions (`.github/workflows/android-build.yml`), not
locally. Pushing a tag builds and publishes the APK to a GitHub Release; release signing
uses secrets held in the repository settings and never present in the source tree.

Tags are `vMAJOR.MINOR` and must equal `versionName` in `app/build.gradle.kts`; the
workflow checks that before it builds anything. `versionCode` is Android's own install
counter and simply increments by one each release — it is deliberately not tied to the
tag, because the two answer different questions.

The full tag-to-version mapping is not kept here by hand — `git tag --list 'v*' | sort -V`
and the Releases page are the answer, and a table maintained in a README goes stale.

Every release is published as latest. There is no pre-release tag form yet; one will be added
when it is needed. The `versionCode` counter only has to increase, not to be contiguous — 3 is
unused because no v1.2 was ever released.

---

## Development

- Read `ARCHITECTURE.md` before changing anything: §3 is the authority on the two rendering
  backends, and nothing about the draw path should be inferred without it. The draw path
  allocates nothing per frame — no object is created inside `draw`, and that is a rule, not a
  preference.
- Sizes and ground positions come from `SceneSpace`. If something draws at the wrong
  size, the fix is its entry in the size table, never a correction at the call site.
- Sprites are described by `tools/assets/sources/sprites.json`. Changing artwork means
  changing its SVG source and re-rendering, not editing a PNG.
- `./gradlew testDebugUnitTest` and `python3 -m paperscrape_assets validate` are the two
  checks worth running on almost any change.
- There is no visual regression test. Anything that changes what is drawn has to be
  looked at on a device.

---

## License

See `LICENSE`.
