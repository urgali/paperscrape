# Contributing to PaperScrape

Thanks for your interest! The project is deliberately small and readable:
any Android developer with basic Kotlin knowledge should be able to find
their way around in a few minutes.

## Where to make changes

| I want to... | File to edit |
|---|---|
| Add a new color theme | `engine/SceneTheme.kt` → add a `SceneTheme` to `ThemeCatalog.ALL` |
| Change the shape of the hills | `engine/PaperRenderer.kt` → `buildBaseHillPath()` |
| Add/move objects in a theme (cars, houses, trees) | `engine/SceneObject.kt` → `SceneObjectCatalog.builtinLayoutFor()` |
| Add a new *seasonal decoration* (independent of any theme) | `engine/SceneObject.kt` → `seasonalDecorationCandidates()` + `SceneCustomization` (new `ObjectVariantConfig` field + `configFor` mapping) + `prefs/WallpaperPrefs.kt` (new `ObjectCategory` entry + add to `SEASONAL_CATEGORIES`) + `ui/SettingsScreen.kt` (`SeasonalDecorationsDialog`) |
| Add a new animated object type | `engine/SceneObject.kt` (new `SceneObjectType` + spec) + `engine/SceneObjectRenderer.kt` (drawing) |
| Tweak the random theme/object algorithm ("Randomize") | `engine/RandomSceneGenerator.kt` |
| Add a theme-linked automatic effect (like fireworks or Santa's sleigh) | `engine/FireworkEffect.kt` or `engine/SantaSleighEffect.kt` as a model + enable/disable from `SceneTheme` and `PaperRenderer.draw()` |
| Change sunrise/sunset logic | `engine/SunPositionCalculator.kt` |
| Add a settings option | `prefs/WallpaperPrefs.kt` (new field) + `ui/SettingsScreen.kt` (new control) |
| Change refresh rate / battery usage | `engine/PaperWallpaperService.kt` → `FRAME_INTERVAL_MS` constant |
| Add/change a date-based seasonal rule (e.g. Halloween week) | `engine/SeasonalThemeRules.kt` → add a `Window` entry |
| Ship a new version | Bump **both** numbers in `app/build.gradle.kts` first: `versionName` to the new `MAJOR.MINOR`, and `versionCode` up by one. CI checks the tag against `versionName` and fails the release if they disagree. Also add `release-notes/vMAJOR.MINOR.md` (see below) — without it the release still ships, just with a generic placeholder instead of a real "what's new" |
| Write release notes | `release-notes/vMAJOR.MINOR.md`, plain language, no code/file references — this is what regular users see both on the GitHub release page and right inside the app's own update dialog (`UpdateChecker` reads the same GitHub release body). Keep `CHANGELOG.md` for the technical/dev-facing history; these are two different audiences, don't merge them |
| Set up real release signing | `scripts/generate-release-keystore.sh` (run locally, never commit the result — see the script's own comments and the "Release signing" section below) |

## Ideas for future contributions

Priority order aligned with the README (see also the planning note there on
how the custom theme editor should connect to the date-based automation):

- [ ] Live weather (requires an external API key, e.g. Open-Meteo which is free and keyless)
- [ ] Custom theme editor (color picker) with multiple saves — must be referenceable from `SeasonalThemeRules`
- [ ] Screenshot/sharing of the current scene
- [ ] Home screen widget for quickly switching themes
- [ ] Snow/rain support as an additional particle layer
- [ ] Dogs, back from scratch (removed in full — see CHANGELOG's v28 entry — rather than
      patched over): a new `SceneObjectType`, its own draw function, its own
      `SceneCustomization`/`ObjectCategory` entry, and placement rows that respect
      `PaperRenderer.ROAD_SAFE_ROW_LIMIT`
- [ ] Touch interaction and sound, redesigned from scratch (also fully removed in v28): tap
      detection/hit-testing, reaction animations, and any accompanying sound
- [ ] More seasonal decorations, following the pattern added in v32 (see `seasonalDecorationCandidates`
      in `SceneObject.kt`): Halloween bats and trick-or-treat pumpkin-under-trees behavior,
      Thanksgiving turkey, St. Patrick's Day leprechaun + four-leaf-clover clouds, Valentine's Day
      cupid + heart clouds, Easter flowers/baskets, Halloween building-window lights. Also missing:
      per-decoration extra toggles some references have beyond visibility/density/color (e.g. "day
      fireworks", "constant fireworks", "random gifts", "African American Santa") -- the current
      `ObjectVariantConfig` shape doesn't have a slot for boolean sub-options like these yet

## Code conventions

- Idiomatic Kotlin, no `!!` when avoidable.
- Every new theme must provide *all* fields of `SceneTheme` (day/night for sky and hills).
- Preferences always go through `WallpaperPrefs` (DataStore), never direct `SharedPreferences`.
- The scene is 2D, but the render loop is **not** pure `Canvas`: it draws through
  `SceneCanvas`, and `GlSceneTarget` (OpenGL ES 2.0, on a per-engine render thread)
  is the normal path. `CanvasSceneTarget` is the settings preview and the fallback
  when EGL is unavailable, which is what keeps all Android 8+ devices working. Keep
  both backends drawing the same picture, and add no third graphics dependency --
  no Vulkan, no external engine. See `ARCHITECTURE.md` §3.

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
