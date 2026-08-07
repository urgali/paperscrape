# PaperScrape 🗻📄

Open-source Android live wallpaper with a layered "paper cutout" landscape,
parallax scrolling, a sun and moon that follow the time of day (real or set
manually), interchangeable color themes, and touch effects.

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
  that light up at night, swaying trees, dogs that wag their tails in a loop,
  cars that cross the screen on their own lane, and — in seasonal themes —
  snowmen, gifts, palm trees, beach umbrellas, skyscrapers, penguins, and
  floating balloons.
- **Touch interaction on dogs/penguins/gifts/cars**: tapping them triggers a
  reaction animation (a hop) plus a short, differentiated sound
  (bark/squawk/honk/chime, generated on the fly — see the sound note below).
  Tapping the free background instead makes a paper bird fly.
- **Automatic fireworks** at night in the New Year's Eve theme.
- **Santa's sleigh** (Christmas theme): every so often (at random intervals)
  it crosses the sky pulled by two reindeer, dropping gifts that fall to the
  ground.
- Optional sync with **real location** to calculate precise sunrise/sunset
  times (location permission requested only if enabled).
- **9 themes/scenes included** — Sunset, Autumn, Winter, Desert,
  **Christmas**, **New Year's Eve** (with fireworks at midnight), **Beach**,
  **Big City** (skyscrapers and 3-lane traffic), and **Tundra** — each with
  its own combination of colors *and* dedicated objects (not just different
  palettes: Christmas has snowmen and gifts, Beach has palm trees and
  umbrellas, Big City has skyscrapers with windows that light up at night,
  etc.). Adding a new one takes just a few lines in two files.
- Settings screen in Jetpack Compose with a live preview of themes.
- All preferences persisted with Jetpack **DataStore**.

### 🔊 A note on sound

Reaction sounds (bark/squawk/honk/chime) are generated with
`android.media.ToneGenerator`, built into Android — no external audio file
needed, so the project stays compilable right away without having to
source/license audio assets. They're short, recognizable beeps, not
realistic recordings. The `app/src/main/res/raw/` folder is already set up
for when you want to replace them with real sounds: just add the files there
and update `ReactionSoundPlayer.kt` (instructions in the file's own TODO).

## 📚 Wiki

### Available themes

| Theme | Distinctive elements |
|---|---|
| **Sunset** | House, dogs, cars on 2 lanes |
| **Autumn** | House, trees, dog, car |
| **Winter** | House, trees, snowman, car |
| **Desert** | Trees, dog, car |
| **Christmas** | House, snowmen, gifts, tree, **Santa's sleigh pulled by reindeer** flying across the sky at random intervals dropping gifts |
| **New Year's Eve** | Skyscrapers, balloons, **automatic fireworks at midnight** (particle effect) |
| **Beach** | Swaying palm trees, colorful wedge-slice beach umbrellas |
| **Big City** | Skyscrapers with windows that light up randomly at night, 3-lane traffic |
| **Tundra** | Snowman, penguins (tappable, with their own sound) |
| **🎲 Random** | Combination generated on the fly: harmonious colors + 3-6 objects picked at random from the full pool |

### Objects and interactions

| Object | Tappable | Behavior |
|---|---|---|
| Dog | ✅ | Wags its tail in a loop; hops and barks on tap |
| Penguin | ✅ | Waddles while walking; hops with a high-pitched sound on tap |
| Gift | ✅ | Hops with a chime on tap |
| Tree / Palm tree | ✅ | Sways gently; the sway amplifies briefly (with sound) on tap |
| Snowman | ✅ | Gentle wobble; the wobble amplifies briefly (with sound) on tap |
| Car | ✅ (honk) | Loops across the screen on its own dedicated **two-lane road**, independent of hill parallax |
| House | ❌ | Windows gradually light up at night |
| Skyscraper | ❌ | Windows randomly turn on/off at night |
| Beach umbrella | ❌ | Gentle vertical bob |
| Balloon | ❌ | Floats up and down |
| Free background | — | Tapping makes a paper bird fly |

### Settings

| Setting | What it does |
|---|---|
| Theme | Choose among the 9 fixed themes (see table above) |
| 🎲 Generate random theme | Creates a new color/object combination; the seed is saved, so it survives a restart until you generate another one |
| Follow real time | Sun/moon follow the device's clock instead of a fixed hour |
| Use location for sunrise/sunset | Calculates precise sunrise/sunset times based on lat/lon (requires location permission) |
| Touch effects | Toggles object reactions and the paper bird effect |
| Parallax strength | From 0.5x to 2x, how much the hills shift as the home screen scrolls |

## 📁 Project structure

```
PaperScrape/
├── app/src/main/kotlin/com/paperscrape/livewallpaper/
│   ├── engine/
│   │   ├── PaperWallpaperService.kt   # WallpaperService + Engine: render loop, touch, location
│   │   ├── PaperRenderer.kt           # Draws sky, stars, sun/moon, hill layers
│   │   ├── SceneTheme.kt              # Theme data model + built-in theme catalog
│   │   ├── SceneObject.kt             # Scene object data model (cars/dogs/houses/trees) per theme
│   │   ├── RandomSceneGenerator.kt    # Procedural generator powering "Randomize"
│   │   ├── SceneObjectRenderer.kt     # Draws and animates scene objects, handles touch hit-testing
│   │   ├── ReactionSoundPlayer.kt     # Touch reaction sounds (bark/squawk/honk via ToneGenerator)
│   │   ├── FireworkEffect.kt          # Automatic fireworks (New Year's Eve theme, at night)
│   │   ├── SantaSleighEffect.kt       # Santa's sleigh (Christmas theme, at random intervals)
│   │   ├── SunPositionCalculator.kt   # Sun/moon position and sunrise/sunset calculation
│   │   └── PaperBird.kt               # "Paper bird" particle for tapping the free background
│   ├── prefs/
│   │   └── WallpaperPrefs.kt          # User preferences (DataStore)
│   └── ui/
│       ├── SettingsActivity.kt        # Activity hosting the Compose screen
│       ├── SettingsScreen.kt          # Settings UI (themes, switches, sliders)
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
  lifecycle, a **~30 fps** loop via `Handler.postDelayed`, touch events, and
  the home-screen scroll offset (`onOffsetsChanged`).
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
`allowBackup` disabled). The app requests **no `INTERNET` permission** and
makes no network calls of any kind.

## 🎯 Roadmap toward a complete experience

Project goal: build a full-featured "paper cutout" live wallpaper with
entirely original code/assets (see legal note below).

### Done ✅

- Layered paper landscape with parallax
- Automatic day/night cycle + sunrise/sunset from real location
- Multiple color themes
- Animated, interactive objects (cars, dogs, houses, trees) with touch
  reaction and sound
- Seasonal/festive themes as distinct scenes (Christmas, New Year's Eve,
  Beach, Big City, Tundra, plus Sunset/Autumn/Winter/Desert) with dedicated
  objects, not just a different color palette
- Randomize function: generates infinite combinations of colors
  (harmonious, not purely random) and objects on the fly — not just a
  choice among the 9 fixed themes
- Themed special event: Santa's sleigh flying across the sky at random
  intervals dropping gifts (Christmas theme)

### What's missing for 1:1 parity with the original app

| # | Missing feature | Notes |
|---|---|---|
| 1 | **Automatic theme switching by date/period** | E.g. Christmas theme during Christmas week, Easter theme during Easter week, summer theme (Beach) during summer months, etc. — configurable rules/priority |
| 2 | **Live weather** influencing the scene | Needs a weather API (e.g. Open-Meteo, free and keyless) |
| 3 | **Custom theme editor** with color picker | Saving multiple custom themes, not just the last one generated by Randomize — see the planning note below |
| 4 | **Screenshot/sharing** of the current scene | Capture the wallpaper's canvas and export it as an image |
| 5 | **Home screen widget** for quick theme switching | Requires an `AppWidgetProvider` + dedicated layout |

**Planning note — item 3 × item 1:** once the custom theme editor arrives,
the date/period automation (item 1) will need to be able to pick **from**
the user's custom saved themes too, not just the 9 built-in ones. In
practice, the "date → theme" rule system should be designed to reference
any `themeId` (built-in, Randomize-generated, or custom-saved), not a fixed
list — so the user could, for example, assign their own hand-made Christmas
theme instead of the default one. Item 1 will be implemented with this
constraint already in mind, even though item 3 doesn't exist yet.

No other structural blocker is known beyond these 5 items: the rendering
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
