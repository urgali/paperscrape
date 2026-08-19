# PaperScrape Roadmap

Operational plan only. What shipped and why lives in `RELEASE_HISTORY.md`; how the
code works lives in `ARCHITECTURE.md`; the visual rules live in `DESIGN_NOTES.md`;
the rules that always apply live in `AI_PROJECT_RULES.md`.

**Nothing below is approved. Ask before starting any of it.**

---

## Current status

**v1.0 Stable — released, installed and verified on a Pixel 9.**

`versionCode = 1`, `versionName = "1.0"`.

Last measured: 337 Kotlin unit tests passing, `lintDebug` 41 warnings / 0 errors,
asset `validate` clean across 118 sprites, offline tooling 79 tests with 3 known
failures (D-7).

`assembleDebug` has not been run since v75 and no APK is produced locally — CI
builds the release APK. Compilation is proven by `testDebugUnitTest`, which compiles
the whole `debug` source set; resource linking, dexing and packaging are not.

**Versioning.** Tags are `vMAJOR.MINOR` and must equal `versionName`; `versionCode` is
Android's install counter and increments by one per release, independently. v1.0 →
`versionName 1.0` / `versionCode 1`; v1.1 → `1.1` / `2`; and so on. No pre-release tag
form exists yet. `UpdateChecker` compares `MAJOR.MINOR` and ignores any tag that is not
that shape, so the pre-release history's bare integer tags cannot be misread as newer.

---

## Next priorities

| # | Item | Why it is here |
|---|---|---|
| 1 | **Device pass on v1.0's seasonal scenes** | The roof snow, the People controls, the new star field and the lake lanes all shipped in the last batch before release and have not been looked at together on a device. |
| 2 | **Star-field cost, if it still matters** | Most stars became single `drawCircle` points shortly before v1.0, which cut the per-frame count to roughly a third. Whether the remainder is still worth attention is a question for a device, not for a static count. |
| 3 | **Mountain paths rebuilt per frame** | Two `Path` objects per mountain per frame, from the CPU audit. Real allocation on a draw path; worth doing only if the device shows it. |
| 4 | **Per-vehicle-type toggles** | Cars, taxis, police and fire engines share one visibility switch. Small, self-contained, low value — do it when something else is already open in that file. |
| 5 | **Orphan resources** | Four sprites nothing blits (`house_window`, `road_asphalt`, `road_curb`, `road_line`) and 20 `UnusedResources` lint warnings. Either wire them up or delete them; leaving them is what makes the lint baseline unreadable. |
| 6 | **Material 3 colour scheme** | The settings UI uses Material 3 components without a completed colour scheme. Cosmetic, contained to `ui/`. |
| 7 | **Theme previews** | The settings preview magnifies the size table with per-item fitting factors so three objects of very different heights fit a 120 dp strip. Honest, but not representative. |
| 8 | **README / lint / KDoc tidying** | `UseKtx`, `ObsoleteSdkInt`, `DataExtractionRules`, and KDoc that has accumulated layers across releases. |

**Localisation is explicitly out of scope.** PaperScrape is English-only by decision;
about seventy UI strings remain inline in Compose rather than in `strings.xml`, and
that is fine unless the decision changes.

---

## Deferred

Genuinely open, genuinely not worth doing yet.

| ID | Item | Why deferred |
|---|---|---|
| **D-7** | The shipped PNGs came from the V2 library's own rasteriser; the pinned toolchain renders antialiased edges slightly differently. | Invisible at runtime. Closing it means re-rendering 108 sprites in one change, which needs its own decision and its own device look. Costs three fidelity tests in the offline tooling. |
| **D-10** | 40 sprites carry croppable transparent padding, a few MB of decoded memory. | `normalize --apply` refuses: cropping a `PART_LOCAL` sprite moves its content relative to the local zero its parent composes against, so the crop rule and the anchor model have to be reconciled first. Design work, not a mechanical pass, and every origin it touches needs compensating in the same change. |
| **B5** | The renderer, wallpaper engine, preferences layer and Compose UI cannot be unit tested without being decoupled from `Canvas`/`Context`. | The reason engine fixes are verified on a device rather than by a test. Decoupling is a large refactor with no user-visible result; it earns its place only if engine bugs start recurring. |
| **D1** | The README states the project is not a decompilation of any third-party product; some source comments imply otherwise. | Deferred by the maintainer. Recorded, no action. |
| **D4** | Whether the `MULTIPLY` tint's colour-fidelity trade-off is acceptable. | Accepted in practice across the whole V2 set and never reported as a problem. |
| **D5** | Dependency upgrade — the AndroidX versions are from late 2024. | Nothing is broken by it. Worth taking before any future work that needs a newer API, not before. |
| **D7** | The V2 artwork retired four user-visible colour behaviours (sun colour reaching only the glow, theme star colour reaching nothing, Fall Colors not reaching palm fronds, per-building window lighting). | Approved as consequences of the redesign. Whether each reads well is a judgement to make while looking at the app, and nothing has been reported. |

---

## Completed

- **v1.0 Stable** — first public release.
- **V2 asset redesign** — 118 sprites, every one with an SVG source.
- **GPU renderer** — OpenGL ES 2.0 behind the `SceneCanvas` abstraction, `Canvas` kept as fallback.
- **Asset source pipeline** — SVG sources, a registry, and tooling that renders, validates and compares.
- **Scene proportions, depth and scaling** — `SceneSpace` as the single source of truth.
