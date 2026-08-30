# PaperScrape v4.12 — release candidate, verification report

Companion to `V4_11_REPORT.md`, same place, same purpose: what was actually run, and what was not.

Legend: **VERIFIED** = executed here with the output observed · **OBSERVED** = seen at runtime ·
**INFERRED** = reasoned, not executed · **NOT VERIFIED** = not done, with the reason.

## Baseline

**VERIFIED** — v4.11 (`versionCode 42`), clean tree, 747 files, 27 goldens, 221 PNGs, all prior
BCK/WEA/ARC/SCL/REN fixes present.

## Device

**VERIFIED** — `sdk_gphone16k_x86_64` emulator, API 37 / Android 17, x86_64, 1080x2424, `user`
build (not rooted).

## What this release contains

- **Automatic day/night colours.** One `AutoColorMode` per day/night pair, one transform, one place
  it is applied. See `ARCHITECTURE.md` "Automatic day/night colours".
- **Skyscraper window sharpness: diagnosed, not changed.** See below and `CLAUDE.md` D4.
- **No performance change**, because the scan found nothing worth the risk. See below.

## Test execution

| Suite | Result |
|---|---|
| `./gradlew test --rerun-tasks` | **1104 tests, 0 failures** (v4.11 had 1086; +18 new) |
| `./gradlew connectedDebugAndroidTest` | **102 tests, 0 failures**, 33/33 golden PASS |
| `./gradlew lint` | **BUILD SUCCESSFUL**, 0 errors |
| `clean` / `assembleDebug` / `assembleDebugAndroidTest` / `assembleRelease` / `bundleRelease` | all **BUILD SUCCESSFUL** |
| `python3 -m unittest discover -s tests` (assets) | 103 tests, **1 failure** — the pre-existing rasteriser-fingerprint failure, environmental, unchanged from v4.11 and not masked |
| `validate` / `normalize` / `inventory` | registry OK 221/221, anchors 221 determined, normalisation OK, 27.09 MB / 4.51 MB padding |

**No golden changed**, and that is the expected result rather than a lucky one: every pair defaults
to `MANUAL`, so the scene a default install draws is bit-identical to v4.11's.

## Runtime verification on the device

**VERIFIED, with an exact numeric match.** The feature was driven through the real UI on the Big
City theme with the clock frozen so the comparison was controlled:

1. Buildings Day Colour 1 and 2 set to `#E53935` through the hex field.
2. Both pairs switched to "Day sets night". The DataStore was read back directly and holds
   `obj_BUILDINGS_auto_mode_1 = from_day` and `..._2 = from_day`.
3. Fixed hour moved to midnight and the wallpaper captured.

The night facades render **RGB (140, 42, 39)**. Computing the transform by hand from `#E53935`
(L 0.553 x 0.635 = 0.351, S 0.772 x 0.725 = 0.560, hue held at 1.2 degrees) gives **(140, 42, 39)**.
The UI, the DataStore, the single-authority derivation and the GL renderer agree to the last level
on all three channels.

Also **VERIFIED** on the device: the three-way control appears under every pair; the derived half is
shown greyed and is not tappable; the mode survives leaving and re-entering the screen.

**NOT VERIFIED at runtime**: process restart, backup export/import round trip, and theme switching
with a mode set. All three are covered by the JVM suite (persistence is one `stringPreferencesKey`
per pair read with a `MANUAL` default, and the JSON round trip is a field on each config with an
`optString` default), and the risk of the untested path is low: an absent or unrecognised value
reads as `MANUAL`, which is the behaviour that shipped in v4.11.

## Skyscraper windows — changed, and verified on the device

**VERIFIED.** The tower's daytime window grid came from `skyscraper_wall`, which carries the wall's
tint, so its windows were the colour of its own bricks. Houses and the restaurant have always shown
cool glass by day and warm light at night.

`skyscraper_wall_lit.svg` was regenerated through the normal pipeline as a **white mask** (the
convention `restaurant_window` already followed), and the renderer tints it with `windowGlassColor`,
a single crossfade between `WINDOW_GLASS_DAY` `#B9CBD9` and `WINDOW_GLASS_NIGHT` `#FFE79A` on the
frame's `nightGlow`. One tinted blit replaces one faded blit; the registry entry moved `FIXED_ART`
-> `TINTABLE` to match.

**Observed on the emulator**, Big City theme, clock frozen:

| check | result |
|---|---|
| Noon, default facade | windows clearly cool blue-grey, strong contrast against the slate wall, same look as the houses |
| Midnight, same tower | windows clearly warm yellow, reading as lights on |
| Facade `#E53935` (saturated), `FROM_DAY`, noon | windows cool and highly legible over the red |
| the same at midnight | facade is the **derived** dark red and windows are warm — AutoColorMode unaffected |
| Facade night `#1A1A2E` (very dark), `FROM_NIGHT`, noon | facade is the derived navy, windows cool and legible |

**NOT VERIFIED**: real hardware, other API levels, other GL drivers.

## The sharpness question (area B as originally reported) — measured, no change

**VERIFIED by measurement and closed.** `skyscraper_wall` is opaque throughout (121 488 of 121 495
pixels at alpha 255) and paints its grid as grey **234** on a **255/244** wall under `MULTIPLY`, so a
window is 8.2% darker than its wall in proportion. In CIELAB:

| tint | ΔL* |
|---|---|
| white / `#F2F2F2` | 7.3 / 7.0 |
| neutral `#808080` | 4.2 |
| **saturated `#E53935`** | **4.1** |
| pure red / pure blue | 4.4 / 3.0 |
| Big City's own default `#454B57` | 2.7 |
| very dark `#1A1A2E` | 1.2 |

Saturated colours are the strong case, not the weak one. GL minifying a 3x sprite with `GL_LINEAR`
and no mipmaps is real but unfixable: ES 2.0 allows non-power-of-two textures only with a
non-mipmapped minification filter, and the sprites are NPOT and atlased.

## Goldens

**VERIFIED, analysed before regenerating.** 24 of 33 assertions failed on the first run. The actual
frames were pulled from the device and compared against the committed images *before* anything was
regenerated:

- **All 24 Canvas goldens** change only inside **y 507..558**, one 50-pixel band, and the colours are
  exactly the two constants: `(84,97,110)` -> `(185,203,217)` = `#B9CBD9` by day, `(255,233,168)` ->
  `(255,231,154)` = `#FFE79A` at night, and a cool/warm mix at `dusk`. Between 0.30% and 0.71% of
  each frame.
- **The three GL goldens passed** while showing the old windows: their change is **0.485%**, just
  under the 0.500% `GlTarget` gate. They were regenerated with the rest rather than left under the
  threshold — the same staleness pattern the v4.11 investigation found and the reason it is worth
  refusing to leave.

Every changed golden is therefore accounted for by one cause, and the visual result was confirmed on
the device before the images were replaced. Re-run after regeneration: **33/33 PASS, 102/102
instrumented**.

## Performance (area C) — assessed, nothing implemented

**VERIFIED by inspection.** The candidates were looked for and are not there: no allocation inside
any `draw*` function, five caches already in place (`GlTextureCache`, `GradientShaderCache`,
`SpriteCache`, `SpriteCacheIndex`, `TintFilterCache`), `drawSpriteFaded` already early-returns at
alpha 0, and no performance finding left open by the previous audits. The one real candidate —
mipmapped minification — is blocked by ES 2.0 as above.

Per-object colour blending was considered and **rejected as not worth it**: `ColorUtils.blendARGB`
is a handful of operations, a few hundred calls a frame, and caching it would add state to save
work that does not register. Nothing was changed for the sake of having changed something.

## Regression review

**VERIFIED** — untouched and confirmed by the green suites: BCK-01/02 (`ThemeCustomizationPersistenceTest`
13 tests), WEA-01/02/06, ARC-01/05/06/07, SCL-01/03/05/06, REN-03, Santa origin, asset registry,
goldens, backup/DataStore. `resetCategory` and `clearAllThemeCustomizationKeys` were both extended
to clear the new mode keys, so "reset to default" cannot leave a derived colour overriding a
restored default.

**VERIFIED not touched**: no artwork, no golden, no manifest, no dependency, no `tools/`, no backup
schema version. `git status` shows only the eleven files listed in the report.

## Mutation testing

**VERIFIED — executed for this release**, one per new authority:

| Mutation | Result |
|---|---|
| `NIGHT_LIGHTNESS_FACTOR` 0.635 → 0.5 | `DayNightColorTest` **FAILS** (2 tests) |
| `resolve()` drops the `FROM_NIGHT` branch | `DayNightColorTest` **FAILS** (2 tests) |
| `windowGlassColor` blends night → day instead of day → night | **initially SURVIVED**; now `SkyscraperWindowTest` **FAILS** |
| the tower reverts to `drawSpriteFaded(..., litWindowAlpha(...))` | **initially SURVIVED**; now `SkyscraperWindowTest` **FAILS** (2 tests) |

**Two mutations survived the whole JVM suite on the first pass**, and that is a finding about the
tests rather than a pass. `SpriteTintClassTest`'s notion of "tinted" is a hand-written list, not the
call sites, so it could not see either; the only thing covering the window rule was the instrumented
goldens, which do not run on a machine without a device.

`SkyscraperWindowTest` closes it by reading the call sites — the same idea `tools/assets`' own
`validate` already applies to blits, one level in. It pins that the tower's grid is tinted rather
than laid over untinted, that the tower and the restaurant go through the same function, that the
crossfade runs day → night and not the reverse, and that the two constants are actually cool and
actually warm. All four mutations now fail. Every mutation was reverted and the tree confirmed
afterwards.
