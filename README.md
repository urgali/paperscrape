# PaperScrape 🗻📄

> ⚠️ **Disclaimer**
>
> Hello! I'm not a software dev, just a humble networker; I really like
> android and this kind of wallpaper so i just asked AI to build it; this is
> ENTIRELY AI, I don't know what I'm doing :) ..have fun!

Open-source Android live wallpaper with a layered "paper cutout" landscape,
parallax scrolling, a sun and moon that follow the time of day (real or set
manually), and interchangeable color themes.

Written **from scratch**, inspired by the general concept of classic
"paper cutout" animated live wallpapers, but with fully original code, name,
and assets — see the legal note at the bottom of this file.

Target: **Android 16 (API 36)**, `minSdk 26` (Android 8.0+), Kotlin + Jetpack
Compose for settings, pure 2D `Canvas` rendering (no OpenGL dependency:
lightweight and compatible with any device).

> 📌 Every release has an entry in [CHANGELOG.md](CHANGELOG.md) — useful for
> understanding what each commit/tag (`v1`, `v2`, `v3`, ...) contains.

---

## ✨ Features

- Animated background with **3 layers of paper hills** with independent
  parallax, moving in sync with home-screen scrolling.
- **Day/night cycle**: the sun (or moon, at night) moves along an arc across
  the sky, and sky/hill colors blend gradually between dawn, day, dusk, and
  night.
- **Animated objects in the scene**, different per theme: houses with windows
  that light up at night, swaying trees, cars that cross the screen on their
  own lane, and — in seasonal themes — palm trees, beach umbrellas, and
  skyscrapers.
- **Seasonal decorations, on any theme**: snowmen, gifts, balloons,
  penguins, Easter bunnies, Easter eggs, and pumpkins get their own
  editing screen, separate from "Scene Objects" — same visibility/
  density/color controls, but usable on *any* theme, not locked to one.
  Built-in themes still ship with sensible defaults (Christmas already
  has snowmen and gifts, Easter already has bunnies and eggs), but
  nothing stops you from adding pumpkins to Christmas or snowmen to the
  beach, and any change can be saved back over the built-in theme or as
  your own custom theme.
- **Automatic fireworks** at night in the New Year's Eve theme.
- **Santa's sleigh** (Christmas theme): every so often (at random intervals)
  it crosses the sky pulled by two reindeer, dropping gifts that fall to the
  ground.
- Optional sync with **real location** to calculate precise sunrise/sunset
  times (location permission requested only if enabled).
- **10 themes/scenes included** — Sunset, Autumn, Winter, Desert,
  **Christmas**, **New Year's Eve** (with fireworks at midnight), **Beach**,
  **Big City** (skyscrapers and 3-lane traffic), **Tundra**, and **Easter**
  (pastel colors) — each with its own color palette. Adding a new one
  takes just a few lines in two files.
- **Automatic theme by date** (opt-in setting): switches to Christmas, New
  Year's Eve, Easter, or Beach automatically during their season, based on
  configurable date rules — falls back to your manually selected theme
  outside of any seasonal window.
- **Custom themes**: save your current look as a new theme, replace any
  built-in theme with it, and reset any customized built-in back to
  default with one tap — all from the "Manage Themes" screen.
- **Every theme offers the same maximum customization range**: exactly 10
  candidate slots for each of the 5 structural customizable categories
  (houses, buildings, cars, umbrellas, trees), generated the same way in
  every theme — whether a theme ends up looking like a quiet village or a
  packed city is entirely your choice via the density sliders, never
  baked into the theme itself.
- **Configurable scene objects, live per theme**: a density slider (0-100%),
  a visibility toggle, and 4 editable colors (touch-and-drag palette + hex
  field), independently for each of the 5 categories above. Edits apply
  immediately to whichever theme you're currently on and don't leak into
  any other theme; switch themes and each one keeps its own look. Want to
  keep an edit permanently? Save it from "Manage Themes" (Replace with
  current / Save as new theme) — the density/visibility/colors you picked
  get baked into that saved theme. Every instance randomly (but stably, no
  flicker) picks one of the 2 color variants and blends into its night
  version as it gets dark. Nearer objects render larger than farther ones,
  matching real depth perception.
- **Buildings are a mix of commercial types** — skyscrapers, storefront
  restaurants with a striped awning, and bars with a hanging sign — not
  just skyscrapers, stably varied per instance and all sharing the same
  customizable color.
- Settings screen in Jetpack Compose with a live preview of themes.
- All preferences persisted with Jetpack **DataStore**.

## 📚 Wiki

### Available themes

| Theme | Distinctive elements |
|---|---|
| **Sunset** | House, cars on 2 lanes |
| **Autumn** | House, trees, car |
| **Winter** | House, trees, car |
| **Desert** | Trees, car |
| **Christmas** | House, tree, **Santa's sleigh pulled by reindeer** flying across the sky at random intervals dropping gifts |
| **New Year's Eve** | Skyscrapers, **automatic fireworks at midnight** (particle effect) |
| **Beach** | Swaying palm trees, colorful wedge-slice beach umbrellas |
| **Big City** | Skyscrapers with windows that light up randomly at night, 3-lane traffic |
| **Tundra** | House, trees, car |
| **Easter** | Pastel colors |
| **🎲 Random** | Combination generated on the fly: harmonious colors + 3-6 objects picked at random from the full pool |

Snowmen, gifts, balloons, penguins, bunnies, Easter eggs, and pumpkins used
to be hardcoded to one "traditional" theme each (Christmas got snowmen,
Easter got bunnies, etc.) with no way to turn them off, recolor them, or
move them to a different theme. They're now edited the same way as houses/
trees/etc. (see below) — built-in themes keep sensible starting defaults,
but everything is yours to change.

### Seasonal decorations (any theme)

A second, separate customization screen from "Scene Objects" — **🎃
Seasonal decorations** — for extras that aren't part of every theme's core
structure: snowmen, gifts, balloons, penguins, Easter bunnies, Easter
eggs, and pumpkins. Same visibility toggle, density slider, and 4 editable
colors as "Scene Objects", and the same per-theme editing model: changes
apply live to your current theme only, and you keep them permanently via
"Manage Themes" (Replace with current / Save as new theme) exactly like
structural categories. The difference is just the *starting point* per
theme — Christmas starts with snowmen and gifts on, Easter starts with
bunnies and eggs on, Tundra starts with snowmen and penguins on, New
Year's Eve starts with balloons on, Winter starts with a snowman on, and
every other theme starts with all of it off — but any of that is yours to
change: want pumpkins at Christmas or snowmen on the beach instead? Turn
them on there and save it.

### Objects and behavior

| Object | Behavior |
|---|---|
| Bunny | Idles in a loop |
| Penguin | Waddles while walking |
| Gift | Static, no animation |
| Tree / Palm tree | Sways gently |
| Snowman | Gentle wobble |
| Car | Loops across the screen on its own dedicated **two-lane road**, independent of hill parallax |
| House | Windows gradually light up at night |
| Skyscraper | Windows randomly turn on/off at night |
| Beach umbrella | Gentle vertical bob |
| Balloon | Floats up and down |
| Easter egg | Decorative, static |
| Pumpkin | Decorative, static |
| Free background | — | Tapping makes a paper bird fly |

### Settings

| Setting | What it does |
|---|---|
| Theme | Choose among the 10 fixed themes (see table above) |
| 🖼️ Manage themes | Opens the theme gallery: save the current look, replace a built-in theme with it, reset a customized one back to default (or all of them at once — useful if a theme seems to be missing objects an app update added), rename/delete your own custom themes |
| 🎨 Scene objects | Live preview + per-theme editor: for each of 5 structural categories (houses, buildings, cars, umbrellas, trees) — show/hide, a 0-100% density slider, and 4 editable colors (touch-and-drag palette + hex field). Applies live to your current theme only; save it via "Manage Themes" to keep it |
| 🎃 Seasonal decorations | Same show/hide + density + 4-color editor as Scene Objects, for 7 extras (snowmen, gifts, balloons, penguins, Easter bunnies, Easter eggs, pumpkins). Per-theme, same as Scene Objects — built-in themes ship with sensible defaults (Christmas already has snowmen/gifts, Easter already has bunnies/eggs, etc.), fully editable and saveable via "Manage Themes" |
| 🎲 Generate random theme | Creates a new color/object combination; the seed is saved, so it survives a restart until you generate another one |
| Automatic theme by date | Opt-in — overrides your manual pick during Christmas, New Year's Eve, Easter, or summer (Beach); falls back to your manual pick otherwise |
| Follow real time | Sun/moon follow the device's clock instead of a fixed hour |
| Use location for sunrise/sunset | Calculates precise sunrise/sunset times based on lat/lon (requires location permission) |
| Parallax strength | From 0.5x to 2x, how much the hills shift as the home screen scrolls |

## 📁 Project structure

```
PaperScrape/
├── app/src/main/kotlin/com/paperscrape/livewallpaper/
│   ├── engine/
│   │   ├── PaperWallpaperService.kt   # WallpaperService + Engine: render loop, location
│   │   ├── PaperRenderer.kt           # Draws sky, stars, sun/moon, hill layers
│   │   ├── SceneTheme.kt              # Theme data model + built-in theme catalog
│   │   ├── SceneObject.kt             # Scene object data model (cars/houses/trees) per theme
│   │   ├── RandomSceneGenerator.kt    # Procedural generator powering "Randomize"
│   │   ├── SeasonalThemeRules.kt      # Date-based rules for "automatic theme by date"
│   │   ├── SceneObjectRenderer.kt     # Draws and animates scene objects
│   │   ├── FireworkEffect.kt          # Automatic fireworks (New Year's Eve theme, at night)
│   │   ├── SantaSleighEffect.kt       # Santa's sleigh (Christmas theme, at random intervals)
│   │   ├── SunPositionCalculator.kt   # Sun/moon position and sunrise/sunset calculation
│   │   ├── CustomThemeData.kt         # Custom theme data model + JSON (de)serialization
│   │   ├── CustomThemeRegistry.kt     # Synchronous in-memory cache of custom themes/overrides
│   │   └── SceneCustomization.kt      # Global per-category density, visibility, colors (5 structural + 7 seasonal categories)
│   ├── prefs/
│   │   ├── WallpaperPrefs.kt          # User preferences (DataStore)
│   │   └── CustomThemeStore.kt        # Custom theme / built-in override persistence (DataStore)
│   ├── update/
│   │   ├── UpdateChecker.kt           # GitHub Releases API check (network, once per app launch)
│   │   └── UpdatePrefs.kt             # "Remind me later" snooze persistence (DataStore)
│   └── ui/
│       ├── SettingsActivity.kt        # Activity hosting the Compose screen
│       ├── SettingsScreen.kt          # Settings UI (themes, switches, sliders, Manage Themes)
│       └── theme/PaperScrapeTheme.kt  # App's Material3 theme
├── app/src/main/res/
│   ├── xml/wallpaper.xml              # Live wallpaper metadata
│   ├── drawable/                      # Vector icons + wallpaper thumbnail
│   └── values/                        # Strings, colors, themes
├── .github/workflows/android-build.yml # CI: automatic debug APK build + GitHub Release on every push
├── CONTRIBUTING.md                    # Quick guide for extending the project
└── LICENSE                            # MIT
```

## 🛠️ How to build it

### Option A — Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or newer).
2. `File → Open` and select the `PaperScrape/` folder.
3. Android Studio automatically generates the Gradle Wrapper (`gradlew`) on
   the first sync — nothing manual needed.
4. Make sure you have **Android SDK Platform 36** installed via the SDK
   Manager (`Tools → SDK Manager`). If it's not yet available in your
   Studio version, temporarily set `compileSdk`/`targetSdk` to 35 in
   `app/build.gradle.kts`.
5. Press ▶️ Run to install on a device/emulator, or `Build → Build
   Bundle(s)/APK(s) → Build APK(s)`.

### Option B — command line

```bash
# If you don't have a gradlew in the repo yet (the wrapper's binary jar isn't included):
gradle wrapper --gradle-version 8.9

./gradlew assembleDebug
# APK generated at: app/build/outputs/apk/debug/app-debug.apk
```

> Note: the repository does not include the `gradle-wrapper.jar` binary (a
> binary file, not suited to a plain-text diff). Opening the project in
> Android Studio regenerates it automatically; from the command line, just
> run the `gradle wrapper` command above once.

### How to set it as your wallpaper

After installing, open the **PaperScrape** app from your launcher → pick a
theme → "Set as wallpaper". Alternatively: `System Settings → Wallpaper →
Live Wallpapers → PaperScrape`.

## 🧠 Architecture in brief

- `PaperWallpaperService.Engine` is the core: it manages the wallpaper's
  lifecycle, a **~30 fps** loop via `Handler.postDelayed`, and the
  home-screen scroll offset (`onOffsetsChanged`).
- `PaperRenderer` is **stateless between frames** (except for the star
  field, generated once per screen size) and draws everything with
  `Canvas`/`Path`/`LinearGradient`/`RadialGradient` — no external graphics
  library.
- `SunPositionCalculator` computes a normalized day phase (0–1) and the x/y
  position of the sun or moon along an arc; if the user enables location,
  it uses a simplified NOAA formula for real sunrise/sunset times.
- Preferences (`WallpaperPrefs`) are a `Flow` observed both by the Compose
  UI and the rendering engine: changing a theme in settings reflects live
  on the wallpaper, with nothing needing a restart.

## 🔒 Security

This project has gone through a security/hardening review — see
`CHANGELOG.md` for the full list of what was checked and fixed (location
updates properly stopped when disabled, Gradle wrapper checksum
verification, least-privilege CI permissions with SHA-pinned actions,
`allowBackup` disabled).

**Network access**: the app requests `INTERNET` for exactly one purpose —
the in-app update checker (see below) makes a single HTTPS `GET` request to
the public GitHub Releases API once per app launch, to check the latest
release tag. No other network calls exist anywhere in the app or in the
live wallpaper engine itself; the wallpaper rendering, all themes, and all
settings work entirely offline. The update check sends nothing about you —
it's an unauthenticated, anonymous request to a public API endpoint.

### ✅ Verifying a downloaded release

Every release APK in [Releases](../../releases) is built by CI, never
uploaded by hand, and comes with two ways to confirm what you downloaded
is actually what CI produced from this repository's source at that commit:

- **Checksum**: each release includes an `app-release.apk.sha256` file
  alongside the APK. `sha256sum -c app-release.apk.sha256` confirms the
  bytes weren't corrupted or swapped in transit.
- **Build provenance attestation** (stronger — proves *origin*, not just
  integrity): every release APK is signed via
  [Sigstore](https://www.sigstore.dev/) and published as a GitHub
  attestation. Verify with the [GitHub CLI](https://cli.github.com/):
  ```bash
  gh attestation verify app-release.apk --repo urgali/paperscrape
  ```
  This confirms the exact file was built by this repository's own GitHub
  Actions workflow from a specific commit — not hand-uploaded, not built
  somewhere else and relabeled.
- **Dependency scanning**: the dependency graph submitted by CI (see
  `.github/workflows/dependency-submission.yml`) feeds GitHub's Dependabot
  alerts, so known vulnerabilities in any dependency (including
  transitive ones) surface automatically in this repo's Security tab as
  they're disclosed, not just at the moment a dependency was added.

### 🔄 Update checker

On every app launch, PaperScrape checks GitHub for a newer release than the
one installed. If one is found, an in-app dialog offers:

- **Update now** — opens the release page in your browser to download the
  new APK (PaperScrape does not silently download or install anything
  itself — you stay in control of the install step, same as the very first
  install).
- **Remind me later** — asks whether to ask again at the **next app
  launch**, or **in a month**. Snoozing is tied to that specific version:
  if an even newer release shows up during the snooze period, you'll be
  told about *that* one right away rather than staying silent.

This is deliberately **not** a background service or a system notification
— nothing happens unless you have the app open, and the automatic check
only ever runs once, at launch. A **"🔄 Check for updates" button** at the
bottom of the settings screen lets you trigger the same check on demand at
any time — if you're already on the latest version, it says so right there
instead of doing nothing.

> If you fork or rename this repository, update the `OWNER`/`REPO`
> constants at the top of `update/UpdateChecker.kt` — they're currently set
> to the original repo and won't find releases anywhere else.

## 🎯 Roadmap toward a complete experience

Project goal: build a full-featured "paper cutout" live wallpaper with
entirely original code/assets (see legal note below).

### Done ✅

- Layered paper landscape with parallax
- Automatic day/night cycle + sunrise/sunset from real location
- Multiple color themes
- Animated objects (cars, houses, trees) per theme
- Seasonal/festive themes as distinct scenes (Christmas, New Year's Eve,
  Beach, Big City, Tundra, Easter, plus Sunset/Autumn/Winter/Desert) with
  dedicated objects, not just a different color palette
- Randomize function: generates infinite combinations of colors
  (harmonious, not purely random) and objects on the fly — not just a
  choice among the 10 fixed themes
- Themed special event: Santa's sleigh flying across the sky at random
  intervals dropping gifts (Christmas theme)
- **Automatic theme switching by date/period**, opt-in: Christmas theme
  during Christmas week, Easter theme around Easter Sunday (calculated with
  the standard Computus algorithm, not a fixed date), New Year's Eve theme
  for the turn of the year, Beach theme during summer — falls back to the
  user's manually selected theme outside of any of these windows.
- **Custom themes — save, override, reset**: save the current look as a
  new named theme, replace any built-in theme with the current look
  ("Replace with current"), and undo that with one tap ("Reset to
  default"), all from the new "Manage Themes" screen. The date-based
  automation above already resolves through a generic `themeId`, so it
  transparently works with these too, not just the 10 built-ins.
- **Theme gallery**: previews now render an actual mini scene (sky
  gradient, real hill colors, sun, emoji hints for signature objects)
  instead of a flat color swatch.

### What's missing for 1:1 parity with the original app

| # | Missing feature | Notes |
|---|---|---|
| 1 | **Live weather** influencing the scene | Needs a weather API (e.g. Open-Meteo, free and keyless) |
| 2 | **Full color-picker editor** for custom themes | Custom themes currently save a *snapshot* of a look you already reached (via Randomize or the live preview) — hand-picking individual sky/hill/accent colors in a dedicated editor UI is still missing |
| 3 | **Screenshot/sharing** of the current scene | Capture the wallpaper's canvas and export it as an image |
| 4 | **Home screen widget** for quick theme switching | Requires an `AppWidgetProvider` + dedicated layout |

No other structural blocker is known beyond these 4 items: the rendering
engine, theme/object system, and settings are already extensible enough to
absorb them without rewrites. If you want to follow ongoing work or suggest
a different order, open an issue or check `CONTRIBUTING.md`.

## ⚖️ Legal note

This project is **not a fork or a decompilation of any third-party
product**: it is an original implementation, written from scratch, that
merely shares a general concept common among "paper cutout" live
wallpapers (an animated paper landscape with a day/night cycle). Name,
package, icons, and code are original and distributed under the MIT
license.

## 📄 License

MIT — see [LICENSE](LICENSE). Do whatever you want with it, including
renaming it, modifying it, and publishing it under your own name.
