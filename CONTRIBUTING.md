# Contributing to PaperScrape

Thanks for your interest! The project is deliberately small and readable:
any Android developer with basic Kotlin knowledge should be able to find
their way around in a few minutes.

## Where to make changes

| I want to... | File to edit |
|---|---|
| Add a new color theme | `engine/SceneTheme.kt` → add a `SceneTheme` to `ThemeCatalog.ALL` |
| Change the shape of the hills | `engine/PaperRenderer.kt` → `buildBaseHillPath()` |
| Add/move objects in a theme (cars, dogs, houses, trees, seasonal objects) | `engine/SceneObject.kt` → `SceneObjectCatalog.layoutFor()` |
| Add a new animated object type | `engine/SceneObject.kt` (new `SceneObjectType` + spec) + `engine/SceneObjectRenderer.kt` (drawing + optional hit-test) |
| Change the touch reaction sound | `engine/ReactionSoundPlayer.kt` |
| Tweak the random theme/object algorithm ("Randomize") | `engine/RandomSceneGenerator.kt` |
| Add a new touch effect on the free background | `engine/PaperBird.kt` (or create a new "particle" class on the same model) |
| Add a theme-linked automatic effect (like fireworks or Santa's sleigh) | `engine/FireworkEffect.kt` or `engine/SantaSleighEffect.kt` as a model + enable/disable from `SceneTheme` and `PaperRenderer.draw()` |
| Change sunrise/sunset logic | `engine/SunPositionCalculator.kt` |
| Add a settings option | `prefs/WallpaperPrefs.kt` (new field) + `ui/SettingsScreen.kt` (new control) |
| Change refresh rate / battery usage | `engine/PaperWallpaperService.kt` → `FRAME_INTERVAL_MS` constant |
| Add/change a date-based seasonal rule (e.g. Halloween week) | `engine/SeasonalThemeRules.kt` → add a `Window` entry |

## Ideas for future contributions

Priority order aligned with the README (see also the planning note there on
how the custom theme editor should connect to the date-based automation):

- [ ] Live weather (requires an external API key, e.g. Open-Meteo which is free and keyless)
- [ ] Custom theme editor (color picker) with multiple saves — must be referenceable from `SeasonalThemeRules`
- [ ] Screenshot/sharing of the current scene
- [ ] Home screen widget for quickly switching themes
- [ ] Snow/rain support as an additional particle layer

## Code conventions

- Idiomatic Kotlin, no `!!` when avoidable.
- Every new theme must provide *all* fields of `SceneTheme` (day/night for sky and hills).
- Preferences always go through `WallpaperPrefs` (DataStore), never direct `SharedPreferences`.
- Keep the render loop pure 2D Canvas: no OpenGL/Vulkan dependencies, to stay
  lightweight and compatible with all Android 8+ devices.

## Quick testing without a physical device

Use the Android Studio emulator (API 34+ recommended) and set the wallpaper
from the emulator's Settings → Wallpaper, or launch `SettingsActivity`
directly and press "Set as wallpaper".

## Pull requests

1. Fork + descriptive branch (`feature/snow-theme`, `fix/parallax-jump`, ...)
2. Verify that `./gradlew lint assembleDebug` passes
3. Open the PR explaining *what* changes and *why*
