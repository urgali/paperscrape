# PaperScrape Roadmap

Operational plan only. What shipped and why lives in `RELEASE_HISTORY.md`; how the
code works lives in `ARCHITECTURE.md`; the visual rules live in `DESIGN_NOTES.md`;
the rules that always apply live in `AI_PROJECT_RULES.md`.

**Nothing below is approved. Ask before starting any of it.**

---

## Current status

**v2.15 Stable — the storm flashes only when something is falling, the sky knows about the weather,
and the snow path was finally seen running.**

`versionCode = 19`, `versionName = "2.15"`. The existing lightning system was found already wired
to Live Weather; what was missing was the gate, so a thunderstorm code with every measurement at
zero would have flashed over a dry scene. `isThunderstorm` now means "the scene should storm" —
the condition and something falling — and the source precedence joined the cloud rule in
`LiveWeatherSceneRules`. **D9 is closed**: the snow path was verified on a device against a live
Open-Meteo snowfall, and the weather-driven / theme-driven separation held.

The batch then closed the sky itself. `StormAtmosphere` turns the forecast into one 0–1 strength
that drives sky darkening, cloud darkening and sun attenuation together, as a blend of the theme's
own colours rather than a storm palette, and independent of — but combining with — day/night. Two
defects were found by watching it run and fixed in the same batch: lightning bolts were born above
the cloud band rather than inside it and were three times too large, and the rain response was so
flat at the low end that everyday rain looked like a clear afternoon.

Last measured: 688 Kotlin unit tests passing, `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK. **Seen running on a clean Android 17 emulator** against live provider data for a
thunderstorm, a snowfall and a light drizzle, and through a full controlled A–H weather matrix.

**v2.14 Stable — the settings screens were the wrong size, the sky was not the forecast's, and a
second weather provider.**

`versionCode = 18`, `versionName = "2.14"`. The bottom-spacing bug is closed: it was never
spacing. A settings destination is a full-screen `Dialog` whose content Compose measures against
the display while the window manager sizes the window to the space between the system bars — 2423
px of content in a 2219 px window on the Pixel 9 — so the last 204 px of every screen was laid out
outside the window and clipped. Content is now sized to the window. Live Weather gained a provider
abstraction and Visual Crossing alongside Open-Meteo, with per-provider keys, a required-key state
that makes no request, and no silent fallback between providers. Two Live Weather defects found by
reproducing the reported Florence scene on a clean emulator and fixed before the tag: a weather code
could outvote four measurements reading zero and rain on a dry forecast, and the theme's own cloud
switch could veto the forecast's cloud cover while the same forecast's rain still drew.

Last measured: 636 Kotlin unit tests passing, `lintDebug` 0 errors / 40 warnings, `assembleDebug`
producing an APK. **Seen running on a Pixel 9** (Android 16, gesture navigation, 1080x2424 at
2.625x) and on a **clean Android 17 emulator** against live Open-Meteo data — see
`RELEASE_HISTORY.md` for exactly which paths were exercised and which were not.

**Versioning.** Tags are `vMAJOR.MINOR` and must equal `versionName`; `versionCode` is
Android's install counter and only has to increase, independently. v1.0 → 1, v1.1 → 2,
v2.0 → 4, v2.1 → 5, v2.2 → 6, v2.3 → 7, v2.4 → 8, v2.5 → 9, v2.6 → 10, v2.7 → 11, v2.8 → 12, v2.9 → 13, v2.10 → 14, v2.11 → 15, v2.12 → 16, v2.13 → 17, v2.14 → 18, v2.15 → 19 — 3 is unused because no v1.2 was released,
and the counter has no obligation to be contiguous. No pre-release tag form exists yet. `UpdateChecker` compares `MAJOR.MINOR` and ignores any tag that is not
that shape, so the pre-release history's bare integer tags cannot be misread as newer.

---

## Next priorities

| # | Item | Why it is here |
|---|---|---|
| 1 | **Device pass on v2.0's theme defaults** | Every built-in theme's defaults were reviewed and corrected: the winter family now enables the winter presentation (roof snow, snow-capped trees, winter clothing), Autumn enables Fall Colors and pumpkins, umbrellas leave the cold themes, the tundra lake loses its yachts and dolphins, Beach stands on sand, Desert gets palms, City is built rather than settled. Winter and Christmas are now two independent flags, so a snowy scene without fairy lights and a lit scene without snow are both expressible. Winter and Christmas snow by default. A fresh install now looks materially different per theme, and v2.0 shipped without any of it having been seen rendering. |
| 2 | **Star-field cost, if it still matters** | Most stars became single `drawCircle` points shortly before v1.0, which cut the per-frame count to roughly a third. Whether the remainder is still worth attention is a question for a device, not for a static count. |
| 3 | **Mountain paths rebuilt per frame** | Two `Path` objects per mountain per frame, from the CPU audit. Real allocation on a draw path; worth doing only if the device shows it. |
| 4 | **Per-vehicle-type toggles** | Cars, taxis, police and fire engines share one visibility switch. Small, self-contained, low value — do it when something else is already open in that file. |
| 5 | **Orphan resources** | Four sprites nothing blits (`house_window`, `road_asphalt`, `road_curb`, `road_line`) and 20 `UnusedResources` lint warnings. Either wire them up or delete them; leaving them is what makes the lint baseline unreadable. |
| 6 | **Device pass on the parts v2.14 did not reach** | v2.14 saw the five destinations, the colour scheme and the settings shells rendering on a Pixel 9, which closes the bottom-spacing half of this. Still unseen: the updater's end-to-end run, the twelve mini-scene previews (verified by rasterising the scene description the code produces, not by the app drawing it), and v2.12's sun/moon and people work. |
| 7 | **End-to-end updater run** | The download/verify/install path is covered by unit tests over its pure parts, but no APK has been fetched from a real release and installed. Needs v2.11 published and a v2.12 to update to. |
| 8 | **README / lint / KDoc tidying** | `UseKtx`, `ObsoleteSdkInt`, `DataExtractionRules`, and KDoc that has accumulated layers across releases. |

**Localisation is explicitly out of scope.** PaperScrape is English-only by decision;
about seventy UI strings remain inline in Compose rather than in `strings.xml`, and
that is fine unless the decision changes.

---

## Deferred

Genuinely open, genuinely not worth doing yet.

| ID | Item | Why deferred |
|---|---|---|
| **B5** | The renderer, wallpaper engine, preferences layer and Compose UI cannot be unit tested without being decoupled from `Canvas`/`Context`. | The reason engine fixes are verified on a device rather than by a test. Decoupling is a large refactor with no user-visible result; it earns its place only if engine bugs start recurring. |
| **D1** | The README states the project is not a decompilation of any third-party product; some source comments imply otherwise. | Deferred by the maintainer. Recorded, no action. |
| **D4** | Whether the `MULTIPLY` tint's colour-fidelity trade-off is acceptable. | Accepted in practice across the whole V2 set and never reported as a problem. |
| **D5** | Dependency upgrade — the AndroidX versions are from late 2024. | Nothing is broken by it. Worth taking before any future work that needs a newer API, not before. |
| **D8** | Visual Crossing is not verified end to end: no account was available, so its parser is tested against fixtures built from the published field list rather than against a captured live response. | Needs a free API key. The missing-key path, the failure path and the request URL are all verified on the device; only a *successful* response is not. Worth closing the next time a key is to hand, not worth blocking on. |
| **D7** | The V2 artwork retired four user-visible colour behaviours (sun colour reaching only the glow, theme star colour reaching nothing, Fall Colors not reaching palm fronds, per-building window lighting). | Approved as consequences of the redesign. Whether each reads well is a judgement to make while looking at the app, and nothing has been reported. |

---

## Completed

- **v2.15 Stable** — thunderstorm reviewed end to end (the lightning system existed and was wired;
  the missing piece was gating it on precipitation actually falling), and **D9 closed**: the snow
  path verified on a device against a live Open-Meteo snowfall at Mawson, with the weather-driven
  falling snow and the theme-driven winter presentation confirmed independent. Storm atmosphere
  (darker sky and clouds) deliberately **not** added — it would reverse a recorded visual decision;
  raised as an open question instead.
- **v2.14 Stable** — bottom spacing root-caused to the dialog window's size and closed, immediate
  Live Weather refresh widened to every input it depends on, a location fix now belongs to its
  source, a second weather provider behind a common abstraction, and the forecast-to-scene step
  corrected in both directions: measurements now outrank the weather code, and Live Weather drives
  the cloud layer as completely as it already drove precipitation.
- **v2.13 Stable** — three-action update dialog with in-app install, install-permission deep link, and measurement-first weather mapping.
- **v2.12 Stable** — day/night blend continuity, Sun/Cloud Height corrected and rescaled, day/night people density, two-source bottom inset.
- **v2.11 Stable** — in-app update flow with SHA-256 verification, and the World & scene preview merged into the gallery's preview system.
- **v2.10 Stable** — one central bottom-inset rule for every settings screen, and custom location by city search.
- **v2.9 Stable** — settings UI restructured into five destinations, complete Material 3 colour scheme, Material Symbols, and theme previews drawn from the real sprite library.
- **v2.8 Stable** — shop first floors, small-house harmonisation, a shorter tower with a coarse grid and a real entrance, Christmas firs and scattered lights.
- **v2.7 Stable** — roof-snow and leaf-spawn bugs fixed, flowers toggle, window Christmas lights, balloons removed, building hierarchy and skyscraper grid corrected.
- **v2.6 Stable** — a true outer silhouette outline replacing v2.5's inner rim, which flickered across the walk frames, and a wider small-house facade.
- **v2.5 Stable** — readability rim baked into 39 sprites, world scale +12.5%, Halloween palms, orange moon, two-window small house, the `spring` theme and a calendar covering every day.
- **v2.4 Stable** — the gull, dolphin and carved moon redrawn; the dolphin splash on both crossings; the Halloween theme added with both switches preset.
- **v2.3 Stable** — Halloween and Horror Sky as two independent flags, a stateless dolphin re-entry splash, and the dolphin and bird sprites redrawn.
- **v2.2 Stable** — D-10 closed; 1.49 MB of sprite padding removed with every blit origin compensated in the same change.
- **D-10 — sprite padding, closed.** It was never an asset problem. `SpriteBlitter` puts the
  bitmap's pixel (0,0) on the caller's origin, so a crop is only correct together with a
  compensation in the renderer — and the v76.9 abort that made it look like a conflict
  between the crop rule and the anchor model was a tooling defect, an anchor re-derivation
  guarded on `has_anchor` where it meant `derives_anchor_from_box`. Done in two passes:
  the trailing padding first, which needs no compensation because pixel (0,0) does not
  move, then the leading padding together with all 34 origin changes. Every sprite's ink
  was hashed as (x, y, RGBA) before the crop and reproduced afterwards under exactly the
  translation its origin was compensated by, for all 118. Ten sprites stay uncropped by
  recorded decision — the canvas-anchored sky set, whose shared origin constant would have
  to be split per sprite, and the two palm fronds.
- **v2.1 Stable** — D-7 closed; offline tooling and documentation only, nothing user-visible.
- **D-7 — rasteriser fidelity, closed.** The three failing fidelity tests were not
  a rasteriser matter at all: they still asserted the **pre-V2** sprite library.
  `house_shared_planter` was pinned as a white full-canvas rounded rectangle at
  78x18 radius 6, but the V2 artwork is a `#C98F5A` box occupying only the lower
  part of its viewBox with three foliage circles over it — 113 solid/empty
  conflicts and a max RGB difference of 176, a different picture rather than a
  different antialiasing decision. `road_line` was pinned at 52x8 radius 3.9 and
  ships at 54x9 radius 4.5, so it failed on size before anything was measured.
  The count stayed at three across the redesign, which is why the mislabel
  survived. The assertions were re-derived against `house_large_trim`, which
  really is a full-canvas rounded rectangle in the V2 set, and against the
  sprites that genuinely score under the IoU reporting floor while reproducing
  exactly. `reports/geometry-fit.json` carried the same staleness — it still
  named `house_large_planter` and `house_small_planter`, removed in Phase 3.4 —
  and was regenerated. The residual rasteriser divergence is now measured rather
  than asserted: across all 118 sprites there is **no solid/empty conflict
  anywhere**, so no sprite's shape differs from its source, and no single pixel's
  coverage moves by as much as half (worst case 121/255, one pixel of
  `rainbow_arc`). Both bounds are pinned by `ShippedAgainstSourceTest`. The
  108-sprite re-render that was thought to be the price of closing this was never
  required; it would only have made three unrelated assertions pass.
- **v2.0 Stable** — complete review of every built-in theme; winter and Christmas split into independent flags.
- **v1.1 Stable** — semver release tags, and an update checker that can read them.
- **v1.0 Stable** — first public release.
- **V2 asset redesign** — 118 sprites, every one with an SVG source.
- **GPU renderer** — OpenGL ES 2.0 behind the `SceneCanvas` abstraction, `Canvas` kept as fallback.
- **Asset source pipeline** — SVG sources, a registry, and tooling that renders, validates and compares.
- **Scene proportions, depth and scaling** — `SceneSpace` as the single source of truth.
