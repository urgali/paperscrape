# PaperScrape Roadmap

Operational plan only. The release-by-release account and the completed list moved to
[`docs/archive/ROADMAP_HISTORY.md`](docs/archive/ROADMAP_HISTORY.md) in the documentation
pass of 2026-09-07; what shipped and why lives in `RELEASE_HISTORY.md`; how the code works
lives in `ARCHITECTURE.md`; the visual rules live in `DESIGN_NOTES.md`; the rules that
always apply live in `AI_PROJECT_RULES.md`.

**Nothing below is approved. Ask before starting any of it.**

---

## Current status

**v4.27 prepared — not published and not approved.**

`versionCode = 58`, `versionName = "4.27"`. **No tag, no push, no GitHub Release** — that half is
the maintainer's and has not been done for this version.

**Baseline v4.26, and it is published.** Read from the public GitHub API on 2026-09-10: `v4.26` is
published, non-draft, non-prerelease, tagged at 2026-09-09 22:21:32 UTC, with its APK attached — as
are `v4.16` through `v4.25`. **Re-read the API rather than this line** — nothing in a working tree
learns that a release went out:

```bash
curl -s https://api.github.com/repos/urgali/paperscrape/releases | grep -o '"tag_name": *"[^"]*"' | head
```

**Five backlogs are open.** `BACKLOG_v4_23.md` carries the artwork and renderer items — 18, 25, 30
carried forward, and item 25 (the palm) is still the most visible piece of the old drawing language
left. `BACKLOG_v4_24.md` carries what the documentation review found and did not fix, items 49-55.
`BACKLOG_v4_25.md` carries 56-65, of which **56, 58 and 63 stay open**. `BACKLOG_v4_26.md` carries
66-71, of which **67 stays open** (the `_UNITS` frame rule reads Kotlin and not the generators);
**66 and 71 were both decided by the maintainer in v4.27** — the `perf` build type is committed with
its check, and the PowerVR re-capture is ratified. `BACKLOG_v4_27.md` carries 72-78, of which **78
stays open**: a condition rather than work, recording that the cross-driver GL gap is no longer
measured anywhere and that only a second GPU vendor can bring it back.

**Verified at Level 3 here.** The numbers are in the v4.27 report.

```
v4.27 [x] the rain that could not be seen, and a bird that was not backwards
 |- rain and sky measured against each other over the twelve themes, the clock swept in five-minute
 |  steps, clear / live rain / thunderstorm, thirteen heights down the fall -- 10 368 situations, on
 |  the host. Eleven of the twelve themes have an hour at which the two are the same brightness;
 |  the worst is 0.00 against a median of 28.62
 |- the drop's colour derived per frame against the sky it falls through, to a gap placed by the
 |  v4.22 rule between the 0.00 the failing case gives and the 26.94 the same drop gives over the
 |  hills. No tolerance moved; a theme already clear is drawn bit-identically to v4.26
 |- snow measured the same way and left alone with the number: worst 19.39, so the correction never
 |  fires on it
 |- `rain-worst-sky`: a golden at the measured worst case, with the test that names it asserting it
 |  is still the worst case -- the frame and the measurement cannot drift apart
 |- the reported bird defect does not hold: the sprite already faces its own travel and mirroring it
 |  would have created the defect. `bird-facing` pins one bird at the size it ships
 |- DESIGN_NOTES 14: "in both directions" means the directions the shipped build reaches. v4.26 met
 |  that requirement with a capture-only mirror, which is how the birds were never really judged
 |- the horror sky never recorded the sky it drew, so a lake under it mirrored a colour that was
 |  never computed. One line, reachable in any theme
 \- item 66 decided by the maintainer: the `perf` build type is committed, with the check that keeps
    committing it safe, and the published APK proved identical with and without it

```

```
v4.26 [x] the sky and the sea redrawn
 |- the water becomes a mirror of the sky -- concept S1 "Specchio", chosen from photographs on the
 |  device against a long swell and rows of strokes -- with the light's path under the sun or the
 |  moon, and a struck waterline whose colour is derived per frame so the shore cannot vanish into
 |  a sky the water is reflecting
 |- cloud C1 "Batuffolo" and bird A "Colomba" promoted through the asset pipeline from committed
 |  SVG sources; the bird drops from 90x24 to 51x21 canvas pixels and its flap axis moves with it,
 |  so the wing-beat is a wing-beat instead of a mirror of a symmetric shape
 |- the dolphin's papers derived instead of chosen: CIELab dE 1.53 from the water at its worst on
 |  the shipped artwork, 17.97 now, against a gate placed by the v4.22 method
 |- BACKLOG_v4_25 item 61 closed with proposal C: UnitFrameTest fails any expression naming two of
 |  the seven length systems without one of the five conversions, and it was shown to bite by
 |  reintroducing the real v4.25 defect
 |- item 65 closed by derivation: three new gates over the cloud band, the bird band and the water
 |  band, no tolerance moved and no number lowered
 \- item 64 decided by the maintainer: the double golden regeneration now runs only on the scenes
    whose frames changed
```

```
v4.24 [x] a documentation review: the documents made to say what is true
 |- the mandatory reading list goes from 148 824 words to 13 272; ARCHITECTURE and DESIGN_NOTES
 |  become conditional reads, RELEASE_HISTORY a targeted consultation. ~2 000 words deleted in all
 |- docs/archive/ takes the closed backlogs, the eleven pass reports and the ROADMAP history, with
 |  an index; no source file edited to follow them, because the citations are bare names
 |- AI_PROJECT_RULES 14.9 / 14.10 / 14.11: one report per release, no paragraph correcting another
 |  in the same document, and a command wherever a number decays
 |- stale counts replaced by their commands: 688 tests (1 340), 148 instrumented (149), 1 085
 |  tests, 132 person sprites (166), 0 compiler warnings (19)
 |- DESIGN_NOTES 16-30 regrouped under the themes they belong to, zero lines lost; D1-D5 given
 |  one home in §12, and ANSWERED_QUESTIONS.md folded in and deleted
 \- the publication state read from the Releases API instead of from this file: v4.20-v4.23 are
    published, which this file had denied since the day it stopped being true
```

```
v4.23 [x] the sky cut with scissors: eight celestial sprites redrawn from concept B "Forbici"
 |- sun, sunburst, four moon phases, the pumpkin moon and the star sparkle, promoted through the
 |  asset pipeline from committed SVG sources; every canvas unchanged, so the decoded sprite set
 |  and every blit origin are exactly what they were
 |- the sparkle is blitted at twice the scale (STAR_SPRITE_RADIUS_DIVISOR 32 -> 16) and redrawn
 |  for it: a waist that survives the reduction instead of a one-pixel cross. No star moved
 |- the tile extents re-derived from what is actually drawn rather than doubling the star radius,
 |  which would have hidden the same symptom by changing the star field itself
 |- 16 of 24 Canvas goldens regenerated after a per-region attribution; the four gate rectangles
 |  measure zero on every scene, the three GL goldens deliberately untouched, no tolerance moved
 |- round 2C: a golden for the pumpkin moon, with Halloween AND realistic phases both on, so the
 |  frame can fail if the renderer's override ever leaks. First committed frame in which a
 |  celestial body is drawn clear of the cloud band -- no other one contained a sun or a moon
 \- round 2C: the "Realistic Moon Phases" switch is shown off and locked under Halloween, with
    the reason. The stored preference is overridden, never written: it comes back per theme
```

---

## Known broken

Nothing. **D13** -- the in-app updater hanging on `Downloading` -- was the only entry and is closed
in v3.0; `RELEASE_HISTORY.md` carries what it actually was. The v3.0 assessment's five P0/P1/P2-now
items were closed in v3.1, and its two P1 test-infrastructure items plus P2-3, P2-4 and P2-7 in v3.2.

---

## Next priorities

**What is left of the v3.0 assessment**, plus the one thing v3.2 created. None of it is approved.

| # | ID | Item | Why it is here |
|---|---|---|---|
| A | — | **Real-hardware verification of v4.0** | `targetSdk 37` shipped in v4.0 and every check that an emulator can perform passed. What an emulator **cannot** settle is **certificate transparency** and **ECH**: its network stack, CA store and system image are not a phone's, and a CT or ECH failure would present as a plain connection failure on real hardware while passing here. All five HTTPS hosts connected at `targetSdk = 37` on the emulator, and no workaround was added, so this is a confirmation rather than an open risk — but it is the maintainer's to confirm. **Also outstanding: OpenWeather and WeatherAPI.com end to end**, which v4.0 could not drive because both supplied keys had been revoked (`401` from `curl` on the host as well as from the app). |
| B | — | **Preview/renderer offset agreement is guarded for two groups** | The tree and the tower. The other 47 shared sprites agree today as plain literals and nothing stops them drifting. v3.8 checked all of them and found no third case worth the indirection; the guard would have to encode each renderer draw function's nested transforms, which is a second copy of the thing being checked. **Not scheduled.** |
| C | — | **The lit night facade's placement is unverified against the artwork** | v3.8 made the preview follow the renderer, which is documented intent (*"laid over it at the same origin"*) and is confirmed by the Christmas window-light grid hanging at the same `+5`. What was *not* possible was measuring it the way the snow cap was: the wall sprite is a plain tintable rectangle with no detectable window grid, so there is no artwork feature to align against. **Not scheduled** — recorded so the basis for the choice stays visible. |
| D | — | **The instrumented tests have no automated trigger** | Unchanged since v3.6. `AI_PROJECT_RULES.md` 10.12 and 10.13 state what a future E2E job must satisfy. **Not scheduled.** |

Deliberately **not** scheduled: any further weather-provider change — the three the comparative
assessed are all now implemented and **Open-Meteo stays the default** — and any refactor of
`PaperRenderer`, `SceneObjectRenderer` or `ThemePreviewScene`. `targetSdk 37` shipped in v4.0 and is no
longer scheduled work; what remains of it is item A's confirmation on real hardware. **D10 is
closed by v4.0** -- `targetSdk` and `compileSdk` are both 37 now.

---

## Deferred

Genuinely open, genuinely not worth doing yet. The rows numbered 1-7 came from a section
called "Older priorities" and were merged here by the 2026-09-07 documentation pass: the
section held nothing that was not either deferred or already closed, and the closed rows
are in `docs/archive/ROADMAP_HISTORY.md`.

| ID | Item | Why deferred |
|---|---|---|
| **B5** | The renderer, wallpaper engine, preferences layer and Compose UI cannot be unit tested without being decoupled from `Canvas`/`Context`. | The reason engine fixes are verified on a device rather than by a test. Decoupling is a large refactor with no user-visible result; it earns its place only if engine bugs start recurring. |
| **D4** | Whether the `MULTIPLY` tint's colour-fidelity trade-off is acceptable. | Accepted in practice across the whole V2 set and never reported as a problem. |
| **D11** | Three lint findings that only appeared once the tooling was current: `ConfigurationScreenWidthHeight` on `SettingsInsets.kt:115`, and three `AutoboxingStateCreation` hints on `SettingsComponents.kt`. | New checks over unchanged code, not regressions. `SettingsInsets` is the file that closed v2.14's dialog-sizing bug, so swapping `Configuration.screenHeightDp` for `LocalWindowInfo.current.containerSize` is a change to the one thing that bug turned on — worth doing deliberately, with a device pass, not as a lint tidy-up. |
| **D12** | Every `OutlinedButton` changed colour in the upgrade. Material3 `1.4.0` moved the default content colour from `primary` to `onSurfaceVariant` and the default border from `outline` to `outlineVariant`, so "Reset this theme's scene to defaults" and the other six outlined buttons now read grey-brown with a pale border instead of orange with a mid border. Verified on an Android 17 emulator by sampling the pixels: the new values are exactly this project's own `onSurfaceVariant` (`0xFF54443A`) and `outlineVariant` (`0xFFD9C7B7`). `TextButton` and filled `Button` are unchanged. | This is Material 3's own current default, and rule 3 says the app follows Material 3 — so it was **left as Material draws it** rather than pinned back, which would mean hard-coding a superseded default into seven call sites. It is nevertheless the one user-visible change the whole upgrade produced, and whether the quieter outlined button reads well is a judgement to make while looking at the app. Pinning it back is one argument: `colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)` plus `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`. |
| **D7** | The V2 artwork retired four user-visible colour behaviours (sun colour reaching only the glow, theme star colour reaching nothing, Fall Colors not reaching palm fronds, per-building window lighting). | Approved as consequences of the redesign. Whether each reads well is a judgement to make while looking at the app, and nothing has been reported. |

| # | Item | Why it is here |
|---|---|---|
| 1 | **Device pass on v2.0's theme defaults** | Every built-in theme's defaults were reviewed and corrected: the winter family now enables the winter presentation (roof snow, snow-capped trees, winter clothing), Autumn enables Fall Colors and pumpkins, umbrellas leave the cold themes, the tundra lake loses its yachts and dolphins, Beach stands on sand, Desert gets palms, City is built rather than settled. Winter and Christmas are now two independent flags, so a snowy scene without fairy lights and a lit scene without snow are both expressible. Winter and Christmas snow by default. A fresh install now looks materially different per theme, and v2.0 shipped without any of it having been seen rendering. |
| 2 | **Star-field cost, if it still matters** | Most stars became single `drawCircle` points shortly before v1.0, which cut the per-frame count to roughly a third. Whether the remainder is still worth attention is a question for a device, not for a static count. |
| 3 | **Mountain paths rebuilt per frame** | Two `Path` objects per mountain per frame, from the CPU audit. Real allocation on a draw path; worth doing only if the device shows it. |
| 4 | **Per-vehicle-type toggles** | Cars, taxis, police and fire engines share one visibility switch. Small, self-contained, low value — do it when something else is already open in that file. |
| 6 | **Device pass on the parts v2.14 did not reach** | v2.14 saw the five destinations, the colour scheme and the settings shells rendering on a Pixel 9, which closes the bottom-spacing half of this. Still unseen: the twelve mini-scene previews (verified by rasterising the scene description the code produces, not by the app drawing it), and v2.12's sun/moon and people work. The updater's end-to-end run left this row for **D13** and was done in v3.0. |
| 7 | **README / lint / KDoc tidying** | `UseKtx`, `ObsoleteSdkInt`, `DataExtractionRules`, and KDoc that has accumulated layers across releases. |

**Localisation is explicitly out of scope.** PaperScrape is English-only by decision;
about seventy UI strings remain inline in Compose rather than in `strings.xml`, and
that is fine unless the decision changes.
