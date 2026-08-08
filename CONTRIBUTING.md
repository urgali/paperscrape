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
| Ship a new version | Bump `versionCode` in `app/build.gradle.kts` first — CI reads it directly to name the GitHub Release (`vN`), so it must match the version you're actually shipping |
| Set up real release signing | `scripts/generate-release-keystore.sh` (run locally, never commit the result — see the script's own comments and the "Release signing" section below) |

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

## Release signing

`app/build.gradle.kts`'s `release` build type only signs with a real key when
`PAPERSCRAPE_RELEASE_STORE_FILE` (and the matching password/alias env vars)
are present — otherwise `assembleRelease` produces an intentionally unsigned,
uninstallable APK, so a missing key fails loudly instead of silently.

1. Generate your own keystore locally: `./scripts/generate-release-keystore.sh`
   (never commit the resulting `.jks` — `.gitignore` already excludes it).
2. For local builds, export the four `PAPERSCRAPE_RELEASE_*` env vars the
   script prints, then run `./gradlew assembleRelease`.
3. For CI to build signed releases, add these repository secrets (Settings →
   Secrets and variables → Actions): `RELEASE_KEYSTORE_BASE64` (the keystore
   file, base64-encoded), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD`. The `release` job in
   `.github/workflows/android-build.yml` decodes the keystore into a
   runner-local temp file at build time only — it's never written to the
   repository, and these secrets are never exposed to the `build` job that
   runs on every push/PR, only to `release`, which only runs on pushes to
   `main`.

## Pull requests

1. Fork + descriptive branch (`feature/snow-theme`, `fix/parallax-jump`, ...)
2. Verify that `./gradlew lint assembleDebug` passes
3. Open the PR explaining *what* changes and *why*
