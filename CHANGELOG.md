# Changelog

Each version here corresponds to a zip delivered in chat and a commit on the
user's GitHub repository. From here on every output is versioned: the
delivered file is named `PaperScrape_vN.zip` and this changelog entry
summarizes its contents, so it's always clear what each commit (`v1`, `v2`,
`v3`, ...) contains without having to diff by hand.

## v13 — in progress

- **In-app update checker**, on every app launch (never a background
  service, never a system notification):
  - `update/UpdateChecker.kt` makes a single HTTPS request to the public
    GitHub Releases API (`/repos/{owner}/{repo}/releases/latest`), compares
    the returned tag (e.g. `v13`) against `BuildConfig.VERSION_CODE`, and
    fails silently on any network/parsing error — a broken connection must
    never crash or interrupt app startup.
  - If a newer version is found, an in-app dialog offers **"Update now"**
    (opens the GitHub release page in the browser — the app does not
    silently download or install anything itself, keeping the user in
    control of the install step) or **"Remind me later"**, which opens a
    second choice: **"Next app launch"** (no-op — the check already runs
    every launch by design) or **"In a month"** (persisted via
    `update/UpdatePrefs.kt`, tied to that specific version so a newer
    release during the snooze period still prompts immediately).
  - Requires the `INTERNET` permission — previously the README stated the
    app made *no* network calls at all; that claim is now updated to be
    accurate rather than left stale. This is still the *only* network
    access anywhere in the app: the wallpaper engine itself remains fully
    offline.
  - **Repo-specific**: `UpdateChecker`'s `OWNER`/`REPO` constants are set to
    the original repository. Update them if you fork or rename it, or the
    checker will silently find nothing (404s fail the same as no internet).

## v12 — in progress

- **App version shown in-app**: a "Version vN (x.y)" row at the bottom of
  Settings, read from `BuildConfig.VERSION_CODE`/`VERSION_NAME` (required
  enabling `buildFeatures.buildConfig = true`, off by default since AGP 8).
- **Every theme now has both house and building candidate slots**: 8 themes
  previously had no buildings at all and 2 had no houses at all — added a
  small background building/house to each so "show houses"/"show
  buildings" and the density slider always have something to work with,
  regardless of theme. Verified programmatically (all 10 themes checked)
  rather than just by eye.
- **Generalized the density+colors customization system from
  houses/buildings to 4 more categories**: dogs, cars, umbrellas, and
  trees now get the same treatment — a visibility toggle, a 0-100% density
  slider, and 4 editable day/night colors each (2 variants, deterministically
  assigned per instance, blending day→night like the rest of the scene).
  - Rewrote `HouseBuildingConfig.kt` into `SceneCustomization.kt`:
    `SceneCustomization` now holds one `ObjectVariantConfig` per category
    instead of hand-duplicated fields, with generic `keepCandidate()` /
    `colorFor()` helpers shared across all 6 categories.
  - `WallpaperPrefs` rewritten with per-category DataStore keys generated
    from the `ObjectCategory` enum, instead of 44 hand-written fields.
  - The "Houses & Buildings" screen became "Scene Objects": one collapsible
    section per category, all built from a single reusable
    `ObjectCategorySection` composable instead of six copy-pasted blocks.
  - The live preview (added in v11) now shows a house, tree, dog, and
    building together instead of just the first two.
  - Parasols are a special case: their 5 wedges alternate between the 2
    configured colors per-wedge (not one color per whole umbrella like
    every other category), via a dedicated `parasolStripeColor()` helper.
- **Doubled the size of every scene element that was too small**: houses,
  buildings, dogs, trees, and cars (via a single `GLOBAL_OBJECT_SCALE = 2f`
  multiplier applied at their `canvas.scale()` call), the road they drive
  on (margins, stroke widths, and dash sizes doubled to match the now-
  bigger cars), and Santa's sleigh + the gifts it drops. Left the paper-bird
  touch effect and firework bursts at their original size — these are
  short-lived touch/particle effects, not persistent scene content, so
  they weren't part of what "too small" was describing.

## v11 — in progress

- **Touch-and-drag color picker**: replaced the three linear Hue/Saturation/
  Brightness sliders with the classic "drag your finger across the
  palette" UX — a saturation/brightness square (drag or tap to jump) plus
  a draggable hue strip, hex field still there for precise/typed input.
  Used for all 8 house/building colors.
- **Fixed house/building color changes not visibly taking effect**: found
  two real gaps while investigating —
  1. `PaperWallpaperService` only forced an immediate redraw when the
     *theme* changed, not when `HouseBuildingConfig` changed on its own
     (e.g. just picking a new house color). The engine would technically
     pick up the new colors on its next scheduled frame, but there was no
     guaranteed *immediate* redraw the way theme changes already got.
     Fixed: config changes now force an immediate redraw too.
  2. More importantly: **none of the in-app previews ever drew houses or
     buildings at all** (`ThemeScenePreview` only ever drew sky/hills/sun),
     so changing a color produced no visible feedback anywhere inside the
     app itself — you'd have had to back out to the actual home screen to
     see anything. Fixed by adding a real live preview (one house + one
     building, drawn with the exact same code the wallpaper uses, via a
     new `SceneObjectRenderer.drawPreviewPair()`) directly at the top of
     the Houses & Buildings screen, with a day/night toggle since colors
     blend between the two.
- "Reset to defaults" remains exactly as before — confirmed still fully
  wired end to end while making the above changes.

## v10 — in progress

- **Configurable houses & buildings**, applied globally across every theme:
  - Independent "show houses" / "show buildings" toggles.
  - A single **density slider (0-100%)** that thins each theme's candidate
    house/building slots in a stable, non-flickering way (based on each
    object's fixed position, never `Random()`), so moving the slider adds
    or removes the same specific houses each time rather than reshuffling.
  - **4 editable colors each** for houses and buildings (Day 1, Night 1,
    Day 2, Night 2). Each individual house/building instance is
    deterministically assigned variant 1 or 2 and blends between its
    variant's day/night color exactly like the rest of the scene — houses
    at night don't all suddenly look identical or flicker between colors.
  - New reusable **HSV color picker** (hue/saturation/brightness sliders +
    two-way-synced hex field) for editing any of the 8 colors.
  - New "Houses & Buildings" screen hosting all of the above, plus a
    "Reset colors to defaults" button.
- **More house/building candidate slots** added to most themes (Sunset,
  Autumn, Winter, Desert, Christmas, Easter, Beach, and Tundra now have
  multiple house slots; New Year's Eve and Big City have more skyscraper
  slots, City reaching 8 at 100% density) — needed so the density slider
  has an actual range to work with, since most themes previously only
  defined 1 house.
- New files: `engine/HouseBuildingConfig.kt` (config model + stable
  per-instance density/color-variant helpers).
- Fixed a bug introduced while wiring this up: `SceneObjectRenderer`'s
  `drawRoad()` still referenced the constructor's `layout` parameter after
  it stopped being a stored property (needed so `layout.staticObjects`
  could be filtered by the new config before becoming the renderer's
  runtime object list) — switched to reading lane positions from
  `carRuntimes` instead.

## v9 — in progress

- **Custom themes**: new "Manage Themes" screen (roadmap item #2, still with
  color values inherited from the current look rather than a full
  color-picker editor — see note below) —
  - **Save the current look as a new theme**, with your own name.
  - **Replace any built-in theme with the current look** ("Replace with
    current"), overriding it everywhere (live wallpaper, previews) while
    keeping its original name and slot in the gallery.
  - **Reset to default** on any customized built-in theme, one tap, fully
    reversible — removes the override and instantly restores the original.
  - **Rename** and **delete** for your own independent custom themes.
  - New files: `engine/CustomThemeData.kt` (data model + hand-rolled JSON
    (de)serialization via `org.json`, already built into Android -- no new
    dependency), `prefs/CustomThemeStore.kt` (DataStore persistence),
    `engine/CustomThemeRegistry.kt` (synchronous in-memory cache, since the
    render thread calls `ThemeCatalog.byId`/`SceneObjectCatalog.layoutFor`
    synchronously and can't suspend on a DataStore read).
  - Fixed a cache-invalidation bug this surfaced: `PaperRenderer`'s object-
    layout cache was keyed only on `theme.id`, but overriding/resetting a
    built-in theme changes what that *same* id resolves to without the id
    changing -- added a generation counter to `CustomThemeRegistry` that
    the cache now also checks.
  - Not yet a full color-picker editor: "custom" currently means "a saved
    snapshot of a look you already reached" (via Randomize, or a future
    picker), not hand-picking individual colors in a dedicated UI. Full
    per-color editing is still a follow-up.
- **Gallery overhaul**: theme previews now render an actual mini scene (sky
  gradient, real hill colors, sun) plus emoji hints for signature objects,
  instead of a flat color swatch — both in the inline gallery and the new
  Manage Themes screen.

## v8 — in progress

- **Fixed app updates failing with "app not installed"**: every CI run was
  signing the debug APK with a fresh, randomly-generated debug certificate
  (GitHub Actions runners start clean each time), so upgrading from one
  version to the next meant installing over a build signed with a
  *different* key — Android refuses that. Fixed by committing a fixed
  `debug.keystore` at the repo root (standard, publicly-known debug
  credentials — holds no real security value, this is not a
  release-signing key) and wiring it into `app/build.gradle.kts`'s debug
  `signingConfig`, so every build (CI or local) is signed identically and
  upgrades work.
- **Added a prominent "Set as wallpaper" button** right under the live
  preview at the top of the settings screen — reachable immediately,
  without scrolling, instead of only being available via the phone's
  system wallpaper picker.
- **Fixed broken scrolling in the settings screen**: the main layout was
  missing a scroll modifier entirely, so anything taller than one screen
  (Touch effects, Parallax strength, and everything below) was simply
  unreachable. Added `verticalScroll` to the root column.
- **Replaced the theme picker with a real gallery**: instead of small flat
  color swatches, each theme now shows an actual mini scene preview (sky
  gradient, layered hills in the theme's real colors, the sun) plus emoji
  hints for its signature objects (🎄🎁 for Christmas, 🐰🥚 for Easter,
  etc.) — laid out as 2-per-row cards so you can actually see what a
  theme looks like before applying it.

## v7

- **Fixed CI build failure introduced by v5's own security fix**: the
  `gradle wrapper --gradle-version 8.9` step (which regenerates the
  wrapper since `gradle-wrapper.jar` isn't committed) started failing
  after v5 added `distributionSha256Sum` to `gradle-wrapper.properties`.
  Gradle's `wrapper` task refuses to run when the properties file already
  has a checksum it wasn't explicitly told to reproduce, rather than
  silently dropping or mismatching it. Fixed by passing
  `--gradle-distribution-sha256-sum` with the same value to the CI command.
- **Release naming now tracks `versionCode`, not the Actions run number**:
  the GitHub Release created on every successful push is now tagged/titled
  `vN` from `app/build.gradle.kts`'s `versionCode` (bumped to 7) instead of
  the unrelated Actions run counter — so the release name always matches
  the version delivered in chat. If the same `versionCode` is pushed again
  (e.g. a quick follow-up fix before bumping it), the existing release for
  that tag is replaced rather than failing the build.

## v6

- **Automatic theme by date/period** (opt-in setting, roadmap item #1):
  new `SeasonalThemeRules.kt` resolves the current date to a themeId —
  Christmas (Dec 18 – Jan 6), New Year's Eve (Dec 30 – Jan 1, takes
  priority over the Christmas window), Easter (± 3 days around Easter
  Sunday, calculated with the standard Computus algorithm — not a fixed
  date), and summer/Beach (Jun 21 – Sep 21). Falls back to the user's
  manually selected theme when no window matches. Re-evaluated on every
  settings change and whenever the wallpaper becomes visible again (so a
  day boundary crossed overnight is picked up promptly). Designed from the
  start to resolve through a plain `themeId` string, so the future custom
  theme editor can plug in without any changes here.
- **New Easter theme**: pastel spring palette, plus two new object types
  (`EASTER_EGG`, decorative; `BUNNY`, tappable) added to the shared object
  system — also available in the Randomize pool.
- Settings screen shows a live "today's automatic theme" indicator when
  the feature is on, and the preview card reflects the effective
  (possibly auto-overridden) theme rather than only the manual pick.
- Renamed the remaining Italian Kotlin identifiers (`NATALE` →
  `CHRISTMAS`, `CAPODANNO` → `NEW_YEAR`, `SPIAGGIA` → `BEACH`, `CITTA` →
  `CITY`) that were missed in v5's string/ID translation pass — only the
  `val` constant names, not user-facing text, so this has no visible
  effect but keeps the codebase's identifiers consistent with the rest of
  v5.

## v5

- **Security & hardening remediation** (reviewed against an external
  security assessment, cross-checked line by line against the real code
  before applying anything):
  - Location updates are now properly stopped (and `hasFixLocation` reset)
    when the user disables the "use location for sunrise/sunset" toggle —
    previously they kept running in the background until the wallpaper
    engine was destroyed, ignoring the user's choice.
  - Added the official SHA-256 checksum (`distributionSha256Sum`) for the
    Gradle 8.9 binary distribution, verified against
    `gradle.org/release-checksums`.
  - CI workflow hardened: added a least-privilege `permissions: contents:
    read` default (with `contents: write` scoped only to the job that
    publishes releases), and pinned `actions/checkout`, `actions/setup-java`,
    and `actions/upload-artifact` to full commit SHAs (cross-verified
    against independent sources) instead of mutable version tags.
    `gradle/actions/setup-gradle` is **not yet pinned** — a verified full
    SHA for its current `v5` tag could not be resolved; left as an
    outstanding item rather than guessing (a wrong SHA would silently break
    every build).
  - `android:allowBackup` disabled (`false`) so wallpaper preferences are
    never swept into Android cloud backup / `adb backup`.
  - Dependency freshness (AGP 8.7.2, Kotlin 2.0.21, and the various AndroxX
    libraries) was **not** changed in this pass — deliberately kept as a
    separate future commit from security fixes, so a dependency-bump
    regression can be reverted without losing the security work. No CVE
    scanner was run (none available in this environment); treat this as
    unverified rather than "confirmed clean".
  - Not checked (out of scope without deeper tooling): git history for
    leaked secrets, and no compiled APK was independently audited.
- **Full English translation**: all user-facing strings (`strings.xml`,
  the Compose settings screen, theme display names), all internal theme
  IDs (`natale` → `christmas`, `capodanno` → `new_year`, `spiaggia` →
  `beach`, `citta` → `city`), and all project documentation (README,
  CONTRIBUTING, this changelog) are now in English for an international
  audience.
- **Automatic GitHub Releases**: every successful CI build on a push to
  `main` now creates a GitHub Release (tagged `build-<run number>`)
  containing only the debug APK, via the GitHub CLI already available on
  the runner (no extra third-party action added, keeping the CI's
  supply-chain surface as small as possible).

## v4

- **Smoother parallax while swiping between home screens**: the hills
  (`buildHillPath`) were being rebuilt from scratch — random control points
  included — on every single frame, even though the shape never actually
  changed (only its position did). Each layer's shape is now computed once
  (cached, invalidated only on theme/screen-size change), and every frame
  just applies a `canvas.translate()`, which is far cheaper. On top of
  that, redraws now fire immediately when a new offset arrives from the
  launcher (`onOffsetsChanged`), instead of waiting for the next scheduled
  ~33ms tick.
- **Fixed snowman/tree interaction**: tapping them now triggers an
  amplified wobble/sway (with sound) instead of just spawning a paper bird
  like the free background does.
- **Two-lane road** under the cars (edges + dashed center line, darker at
  night), in every theme that has traffic.

## v3

- **Full rename**: the project is no longer called PaperScape but
  **PaperScrape** — Kotlin package (`com.paperscrape.livewallpaper`),
  `applicationId`, app name, Compose theme (`PaperScrapeTheme`), Android
  style (`Theme.PaperScrape`), all references in README/CONTRIBUTING.
- **Cats removed** from the whole app (scene objects, sounds, random
  generator, UI text).
- **Santa's sleigh**: new periodic event (Christmas theme) — at random
  intervals it crosses the sky pulled by two reindeer, dropping gifts.
- **Wiki section** added to the README (themes, objects/interactions,
  settings explained in tables).
- **Roadmap updated**: removed the "real sounds" goal; added a new
  priority goal, "automatic theme by date/period"; noted the planning
  needed to connect the custom theme editor to the date-based automation
  (see the Roadmap section in the README).
- Introduced this changelog and the versioning convention.

## v2

- Fixed 2 compile errors surfaced by CI (`companion object` not allowed
  inside an `inner class`; missing opt-in for Material3's experimental
  `TopAppBar` API).
- Silenced the AGP warning about `compileSdk 36` not yet being certified.
- Updated CI actions (`checkout`, `setup-java`, `setup-gradle`,
  `upload-artifact`) to the latest versions compatible with Node 24,
  resolving the deprecation warnings.
- Removed all textual references to third-party products from
  README/CONTRIBUTING/code comments.

## v1

- First working release: paper-layer rendering engine with parallax,
  day/night cycle, 4 base color themes (Sunset, Autumn, Winter, Desert).
- Animated, interactive objects (cars, dogs, cats, houses, trees) with
  touch reaction and synthetic sound.
- 5 additional seasonal/festive themes as distinct scenes (Christmas,
  New Year's Eve with fireworks, Beach, Big City, Tundra).
- Randomize function: procedural generation of themes/objects.
- Repo structure ready for GitHub: MIT license, `.gitignore`, GitHub
  Actions CI, README, CONTRIBUTING.
