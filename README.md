# PaperScrape

An Android live wallpaper: a layered 2D paper-cutout world with an animated
environment, themes, seasonal elements and parallax.

**Current version: v2.11 Stable**

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
- **Custom themes.** Save your own, built on any of the ten, and keep them.
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
  them with the real conditions where you are. If no location is available it says so
  and falls back to the theme's own weather.
- **Occasional visitors.** Santa's sleigh, fireworks, lightning, birds, and a bird you
  can summon by tapping.

---

## Visual style

**2D paper-cutout / paper-craft.** Flat layered paper shapes with soft drop shadows —
no 3D rendering, no perspective projection of geometry, no lighting model. Depth comes
from layering, from scale, and from where a thing stands on the ground.

All artwork is original and drawn for this project. Every sprite has an SVG source in
the repository.

---

## Technical overview

Kotlin, minSdk 26, compileSdk/targetSdk 36.

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

**Themes.** `SceneTheme`/`ThemeCatalog` hold the ten built-in palettes; `SceneCustomization`
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

**Assets.** 125 PNGs in `app/src/main/res/drawable-nodpi/`, each generated from an SVG
source under `tools/assets/sources/svg/`. A Python pipeline renders, measures and checks
them against a registry (`sources/sprites.json`) that records every sprite's size,
content box, anchor rule, scale convention and tint class — and against the Kotlin call
sites that blit them.

---

## Project structure

```
app/src/main/kotlin/com/paperscrape/livewallpaper/
  engine/     the wallpaper service, renderers, GL backend, scene model, themes,
              theme-preview scene descriptions
  ui/         Compose settings UI, one file per destination
  prefs/      DataStore preferences
  weather/    Live Weather fetching
  location/   optional location for sunrise/sunset and weather
  update/     GitHub release check
app/src/main/res/drawable-nodpi/   the 125 shipped sprites
app/src/test/                      Kotlin unit tests
tools/assets/                      SVG sources, sprite registry, Python pipeline
release-notes/                     user-facing notes, one file per release
```

Documentation: `ARCHITECTURE.md` (how the code works), `DESIGN_NOTES.md` (visual and
UX decisions), `ROADMAP.md` (what is next), `RELEASE_HISTORY.md` (what shipped),
`AI_PROJECT_RULES.md` (rules that always apply), `CHANGELOG.md` (full technical log).

---

## Build

Requires JDK 17 and an Android SDK with platform 36. The Gradle wrapper is committed —
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
tag, because the two answer different questions:

| Tag | `versionName` | `versionCode` |
|---|---|---|
| `v1.0` | `1.0` | 1 |
| `v1.1` | `1.1` | 2 |
| `v2.0` | `2.0` | 4 |
| `v2.1` | `2.1` | 5 |
| `v2.2` | `2.2` | 6 |
| `v2.3` | `2.3` | 7 |
| `v2.4` | `2.4` | 8 |
| `v2.5` | `2.5` | 9 |
| `v2.6` | `2.6` | 10 |
| `v2.7` | `2.7` | 11 |
| `v2.8` | `2.8` | 12 |

Every release is published as latest. There is no pre-release tag form yet; one will
be added when it is needed. v2.8 is the current stable release. The `versionCode`
counter only has to increase, not to be contiguous — 3 is unused because no v1.2 was
ever released.

---

## Development

- `AI_PROJECT_RULES.md` is the standing brief — read it before changing anything. It
  covers performance rules for the draw path, asset and anchor rules, verification
  levels and the release process.
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
