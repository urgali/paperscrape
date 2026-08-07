# Changelog

Each version here corresponds to a zip delivered in chat and a commit on the
user's GitHub repository. From here on every output is versioned: the
delivered file is named `PaperScrape_vN.zip` and this changelog entry
summarizes its contents, so it's always clear what each commit (`v1`, `v2`,
`v3`, ...) contains without having to diff by hand.

## v5 — in progress

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
