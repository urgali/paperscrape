# PaperScrape v4.18 — release verification report (final)

**PREPARED — NOT PUBLISHED.**

1. **Release state**: `versionName "4.18"`, `versionCode 49`. The last really published release is
   **v4.17**, so this is the next official release and nothing here renumbers it. **This artefact
   (`PaperScrape_v4_18.zip`) is the definitive 4.18** and supersedes the five internal candidates,
   most recently SHA-256 `6d5f45b4b514c43b0f3fa8118a1d3ed88ad214a5671755a5b53e6b6a83579d51`
   (5 463 754 bytes, 848 entries, delivered and coordinator-verified). No tag, no push, no GitHub
   Release, no PR, no merge, no APK upload, no maintainer credentials were used. Publication is
   the maintainer's; §10 is the checklist for it.

**Environments.** Every judgement was made on the physical **OnePlus 6T** (ONEPLUS A6013,
Android 15, 1080×2340, Adreno 630). **No emulator was started in this pass**: the GL golden
scenes hold no vehicles and no GL golden moved. Labels: **OSSERVATO** (seen on the phone),
**MISURATO** (method + number), **DEDOTTO** (inferred from code), **DICHIARATO** (known limit).

All commit SHAs cited are **local commits in the working sandbox**
`/home/marco/Claude-shit/batch6/paperscrape` (branch `master`); none exists in the maintainer's
repository. The ZIP is the delivery mechanism; the commits are checkpoints.

---

## 0. Baseline verified before a line was changed — MISURATO

`PaperScrape_v4_18_rc5.zip`: SHA-256 `6d5f45b4…9d51` ✓, **5 463 754 bytes** ✓, **848 entries** ✓.
`git status` clean at `f0a75b1`. The tree the work started from is the tree the coordinator
verified.

---

## 1. What was wrong, and which variable paid for it

The passenger came back in the previous pass and the two busts came out **touching**. The
arithmetic, measured off the artwork: a table-sized frontal head is **18.08 units wide** across
the hair at its widest row, so two of them are 36.16 units — **77% of a 47-unit pane**. The gap
and the two pillar margins need 29% more. Two people fit in 47 units only by overlapping, and
the driver's hair was cut by the passenger's.

**Neither of the two levers named in the brief could pay for it.**

- Lowering the pillar light from 15% to 13% returns **1.70 units** of pane width. Undoing the
  overlap costs **9.40**.
- Narrowing the busts' **shoulders** returns **exactly zero**. Both the gap (crown to chin) and
  the pillar light (the scan band ends at the chin) are computed entirely above the shoulder
  line, where the widest ink is the hair. This was reported before any artwork was touched.

**The room was already in the shell.** The pane was a straight trapezoid inside a curved cabin,
so `car_body`'s A-pillar carried about fifteen spare units at the beltline while the pane was
only 43.2 units wide where the heads are widest — against 58.0 units of cabin there. Re-cutting
the pane to follow the cabin, inset 1.8 units from the shell's own opaque edge at every row,
yields the missing width **without the car changing at all**. That is what shipped, on the
maintainer's decision.

| | previous pass | 4.18 final |
|---|---|---|
| `car_window` | 141×69 px = 47 × 23 units, straight trapezoid | **162×69 px = 54 × 23 units, cut to the cabin** |
| pane at the roof line / hair band / sill | 41 / 43.2 / 47 | **42.3 / 54 / 54** |
| blit origin | (−21, −11) | **(−24, −11)** |
| seat pitch | 11.5 units (busts overlapping 6.6) | **19.8 units, no head overlap** |
| seats (driver / passenger) | −2.8 / 8.7 | **−6.8 / 13.0** |
| `car_body` | — | **byte-identical, hash-pinned** |

---

## 2. The three items — all three met

### 2.1 Clear glass between the two heads — **MISURATO, PASS**

Measured on the rendered pixels at 2160×4800, **every row from the crown to the chin**, both
lanes, saloon, taxi and police car (`theTwoHeadsAreSeparatedByClearGlass`):

| | far lane (0.834) | near lane (0.862) | criterion |
|---|---|---|---|
| clear glass between the heads | **3.65%** | **3.16%** | ≥ 3% |
| head pixels occluded by the other occupant | **0** | **0** | 0 |
| rows checked | 41 | 47 | — |
| light to each pillar | **13.82–14.09%** | | ≥ 13% |
| glass filled by head, head band | **67.1–69.5%** | | ≥ 50% |
| …averaged over the head's own rows | **60.8–61.2%** | | ≥ 50% |
| …at the neck row (reported, not asserted) | 43.7–45.3% | | — |

The occupant ink on each row must form **exactly two runs**; one run is the defect this closes,
and the test says so in those words. Runs separated by less than the criterion's own 3% are
merged before counting — a few pixels of glass showing through an anti-aliased hairline is not a
gap between two people, and without that rule the driver's own hair edge splits into two runs.

**Method note, stated because it changed a number.** Occupant ink is measured as the
*complement* — everything inside the pane that is not glass — rather than by matching the
occupant palette. The palette misses the woman's brown hair and every silhouette's anti-aliased
fringe, and at the crown, where a head is three pixels wide, that reads as one head missing
entirely. The complement is also what the eye does.

**Below the chin the busts still meet, and that is deliberate**: two people sitting one behind
the other occlude at the shoulders, and that contact is the depth cue that separates the seats.
The gap is measured crown-to-chin for exactly that reason.

### 2.2 A divider between the seats — **OSSERVATO, PASS**

A seat back rises between the two occupants: 2.6 units wide, from local y 8 down to the sill,
drawn **after the glass and before either bust**, so both people sit in front of it, neither is
occluded, and it is visible only in the daylight between them.

**It could not go anywhere else, and that is arithmetic.** A mullion in the head gap would eat
the very glass the 3% criterion measures — the gap is 3.2–3.7% and the criterion needs 3%, so
there is about a fifth of a unit of slack there. Below the chin the busts narrow to a throat and
the gap opens to five units, which is where the seat back lives.

**OSSERVATO** on the OnePlus at real scale, live wallpaper, full scene, both lanes, Autumn and
winter: two distinct seats read.

### 2.3 The two occupants are never the same person — **MISURATO + OSSERVATO, PASS**

In this artwork a family carries its hairstyle **and** its clothing: every woman bust is the red
top with the yellow band, every man bust the blue one. The previous pass chose the passenger's
family from its own seed channel and forced only the *tone* apart when family and tone collided,
so two women — or two men — could share a car and read as one person drawn twice.

The passenger is now **the driver's complement by construction** (`passengerKindIdx =
1 - driverKindIdx`), with the tone still drawn from its own seed channel on both seats so cars
differ from each other as well as within themselves. `OneOccupantRuleTest` reads the rule out of
`drawCar` rather than restating it, and also pins that the first two rows of the occupant table
really are the two adults, so "the complement" cannot come to mean a child.

**The richer fix does not fit and is recorded, not hidden.** A clothing-colour axis alongside
the skin axis is 12 sprites × 74 448 B = 893 376 B decoded against 553 236 B of room under the
28.5 MiB ceiling. It is item 5 of `BACKLOG_v4_19.md` with that arithmetic attached.

---

## 3. The invariants, re-verified with numbers

| invariant | result |
|---|---|
| head size from the height table, ±10% vs the pedestrian, **summer** | 1.005 (man), 0.903 (woman) — inside |
| …**winter**, head with headwear, winter against winter | 0.905, 0.964 — inside |
| air above the head, 10–25%, with and without headwear | green, sedan and appliance, whole family |
| zero occupant pixels outside the glass | green, both seats |
| livery drawn before the people | `VehicleDrawOrderTest` green |
| driver in the forward half, both directions | asserted on constants and on rendered pixels |
| day lamps: amber at the nose, brake red at the tail | asserted at both ends, both directions |
| `car_body` untouched | **SHA-256 `64955bd9…c12b`, hash-pinned in a test** |
| bonnet 21 units, tail, wheels, arches, spear, beltline | unchanged with the shell |
| leaves, shop-front light, composition, shop dedup | untouched; goldens for those scenes byte-identical |
| GL goldens | untouched; **no emulator started** |
| performance | §7 — no regression, matched conditions |

---

## 4. Tests — built, installed, started, executed

| suite | started | executed | result |
|---|---|---|---|
| JVM (`testDebugUnitTest`) | 1264 | 1264 | **1264 pass, 0 fail** (previous pass: 1262; **+2**) |
| Instrumented, OnePlus 6T (one test APK built + installed; full run started and completed) | 134 | 134 | **134 pass** (previous: 133; **+1**) |
| Asset pipeline tool suite | 108 | 108 | **108 OK** |

**The three new tests:**

1. `VehicleAndShopFrontTest.the saloon shell is the one the pane was cut against` — the
   claim "the pane took the room out of the pillar mass, not out of the car" is only worth
   anything if the shell really did not move, so it is pinned by content hash.
2. `VehiclePedestrianScaleTest` — the seat pitch must exceed the widest head band (18.08 u)
   outright, so the two heads cannot touch whatever the rendered measurement then says.
3. `VehicleOccupantScaleTest.theTwoHeadsAreSeparatedByClearGlass` — §2.1 above.

**Changed:** the pillar-light gate 15% → 13% (a decision, documented where the test lives, not a
test fix); the constant-side clearance with it; `OneOccupantRuleTest` gained the never-the-same-
family rule read out of the source.

**Both traffic goldens moved, and every changed pixel was attributed before regeneration.**
`traffic-day` **574 px (0.199%)** and `traffic-night` **575 px (0.200%)** against a 0.200% gate —
they *passed*, by one pixel, which is not a state to ship: a golden sitting on its own gate is a
trap for the next pass. Attributed first:

| region | traffic-day | traffic-night |
|---|---|---|
| far lane, car 1 (x 130–175, y 640–660) | 107 px | 107 px |
| far lane, car 2 (x 320–362, y 640–660) | 126 px | 127 px |
| near lane, car 1 (x 74–120, y 660–680) | 168 px | 168 px |
| near lane, car 2 (x 266–312, y 660–680) | 173 px | 173 px |
| **outside all four** | **0 px** | **0 px** |

100% inside the four vehicle cabins; the transitions are glass↔person, body↔glass and four
pixels of new seat back. No road, building, pedestrian, leaf or shop light moved. **No tolerance
was touched** (`CHANNEL_TOLERANCE` 8, `MAX_DIFFERING_FRACTION` 0.002). Regenerated only then,
under `-e updateGoldens true` limited to those two tests: `traffic-day` 8543f2db… → 40fc6113…,
`traffic-night` 46329ead… → a6bbf0c4…. The other 22 Canvas goldens re-rendered byte-identical
and the 3 GL goldens were untouched.

**Asset pipeline**: probe matches the pinned hash, `validate` clean, `normalize` reports no
removable padding, `render` + `compare` = **PIXEL_IDENTICAL 138/138** — including the regenerated
`car_window`, so the shipped PNG demonstrably came from the committed SVG.

---

## 5. Sprite memory — MISURATO

The set is **258 PNGs, 29 331 180 B = 27.972 MiB** decoded against the 28.5 MiB ceiling, leaving
**553 236 B**. This pass added **5 796 B** (car_window 141 → 162 px wide). The `head_car` family
is 2.272 MiB, of which `drawCar` can reach 12 files (0.852 MiB); **20 files / 1.420 MiB are never
decoded**, and deleting all twenty still leaves 26.552 MiB — the 26 MiB ceiling is not
recoverable by pruning the occupants. Items 5 and 7 of `BACKLOG_v4_19.md`.

---

## 6. Full-frame evidence — OSSERVATO on the live wallpaper

All captures are from the **running wallpaper** on the OnePlus home screen — never the harness,
never the Settings preview. Delivered alongside this report:

- **Autumn, full frame**: two visibly separate occupants per saloon and taxi in **both lanes**,
  the fire engine carrying one, pedestrians on the pavement in the same picture, leaves drifting.
- **A 3× crop of the road band**, where the clear glass between the heads, the seat back between
  the shoulders, and the man-and-woman pairing are all legible.
- **Winter, full frame and 3× crop**: winter-hatted occupants two per car in both lanes with
  winter pedestrians in the same frame.

---

## 7. Performance — MISURATO on the real wallpaper, matched A/B

True R8 release configuration (`assembleRelease`, minify + shrink, **`flags=0x0` non-debuggable
verified on device**) under a temporary env-guarded `.rc` suffix, **set as the phone's actual live
wallpaper**, Autumn with leaves, screen held on. The previous pass was rebuilt from its own
delivered ZIP and measured **back to back in the same session, same theme, same elapsed time**,
neither run touching the settings UI.

| | 4.18 final | previous pass |
|---|---|---|
| SurfaceFlinger timestats, wallpaper layer, 31 s | 918 frames | 918 frames |
| dropped / janky | **0 / 0** | 0 / 0 |
| averageFPS | **29.884** | 29.867 |
| CPU (`top`, 3 samples, 800% scale) | **25.9–29.6%** | 25.9–29.6% |
| PSS at ~1 min / ~2 min | **62.8 / 66.4 MB** | 64.4 / 68.0 MB |

Screen off **0.0%**, same pid on wake; logcat filtered to the wallpaper pid: **zero eglError /
glError / exceptions**. The `.rc` scaffold was removed from `app/build.gradle.kts` before the ZIP
and verified absent from the tree and the archive; the `.rc` package is uninstalled.

---

## 8. Artefact, accounting and final device state

**Build from a clean extraction of this ZIP — MISURATO**: JVM **1264 tests, 0 failures**, run with
`--rerun-tasks` so nothing came from a cache; debug APK `versionCode='49' versionName='4.18'`,
SHA-256 `e21efe2f…06ff`; installed on the OnePlus and **byte-identical when pulled back**; it is
the build left running as the wallpaper. The extraction was taken one report-edit before the
final ZIP — this paragraph is that edit — and the delta was checked rather than assumed: the only
files that differ between the APK's build tree and the delivered archive are documentation
outside `app/`, so the APK bits are identical.

**Artefact**: `PaperScrape_v4_18.zip` — **849 entries** (848 tracked + `CLAUDE.md`, the standing
convention; the one new file is `BACKLOG_v4_19.md`). SHA-256 and byte size are printed with the
delivery, the hash not being able to live inside a file it covers. Deterministic build (sorted
walk, modes and mtimes from the tree so `gradlew` stays executable in the extraction);
`unzip -t` clean; ZIP == tree verified entry-by-entry byte-identical; **zero forbidden entries**;
`.gitignore`, `.github/` (both workflows), `CLAUDE.md`, `AI_PROJECT_RULES.md`, `debug.keystore`
present; no `.rc` scaffold, no debug harness, no working-number or phantom version in any
shipping document.

**Accounting — MISURATO, against the previous artefact (848 → 849 entries): 1 added, 0 removed,
19 modified**; 848 + 1 − 0 = 849 ✓.
- Added: `BACKLOG_v4_19.md`.
- 3 asset files: `car_window.png`, `car_window.svg`, and its registry entry in `sprites.json`.
- 1 renderer file: `SceneObjectRenderer.kt`.
- 3 JVM test files (`OneOccupantRuleTest`, `VehicleAndShopFrontTest`,
  `VehiclePedestrianScaleTest`) and 1 instrumented (`VehicleOccupantScaleTest`).
- 2 goldens (traffic-day, traffic-night).
- 5 pipeline reports, regenerated.
- 4 documentation files, including this report.

`car_body.png` is **not** in that list, by design — and a test now fails if it ever is without
the hash being updated in the same change.

**Final device state — OSSERVATO**
- OnePlus 6T: `com.paperscrape.livewallpaper.debug` **4.18 (49), built from this ZIP's clean
  extraction** — active as the phone's live wallpaper (home screen; the lock screen stays on the
  system ImageWallpaper), theme **Autumn with falling leaves**, left running.
- The maintainer's own signed package remains installed and untouched.
- The temporary `.rc` perf package and the test package are **uninstalled**. Stay-awake reverted.

---

## 9. What 4.18 ships without — DICHIARATO

Collected in full in **`BACKLOG_v4_19.md`**, twelve items with the measurement and the cost of
closing each. The ones a reader of this report should know about:

- **Fill at the neck row is 43.7–45.3%**, under 50. A neck is narrower than a head at any seat
  count, and the arithmetic in that file shows 50% there and 13% of pillar light are
  unsatisfiable together at *any* pane width. The criterion holds everywhere the heads are.
- **Children never ride** — decided out of scope by the maintainer; their busts fail the pillar
  light at 11–15%.
- **The pillar light itself has never been derived** — 15% was chosen for a single profile bust
  and 13% was chosen to pay for this pass. It is a number, not a result.
- **GL-GOLDEN-ADRENO**, a characterised 1.2–1.4% driver gap under a 3% gate.
- Winter *visible-skin* parity runs 0.88 one way and 1.58 the other — a coverage difference
  between two drawings, bounded rather than waived.

---

## 10. Publication checklist — for the maintainer

Nothing below has been done, and none of it can be done from here.

**1. Commit.** The tree in the ZIP is the release state. Sixteen modified files and one new one;
`git add -A && git commit`. Suggested subject: `v4.18 — the art-direction release: the street
redrawn`. Nothing in the tree needs editing first.

**2. Verify before tagging** (each is a one-liner and each has passed here):
```bash
ANDROID_HOME=$ANDROID_HOME ./gradlew :app:testDebugUnitTest
ANDROID_HOME=$ANDROID_HOME ./gradlew :app:connectedDebugAndroidTest   # a device must be attached
cd tools/assets && python3 -m paperscrape_assets all && python3 -m unittest discover -s tests
```

**3. Tag.** `git tag -a v4.18 -m "v4.18"` on the release commit, then push the branch and the tag.
`versionName` is `4.18` and `versionCode` is `49`; neither needs changing.

**4. Build what you publish.** The release APK/AAB must be signed with **your own** key, from
your own machine or the `release` CI job — the `RELEASE_*` environment variables or GitHub
Secrets. The `debug.keystore` in the repo is deliberately public and is not a release key.
```bash
export PAPERSCRAPE_RELEASE_STORE_FILE=... PAPERSCRAPE_RELEASE_STORE_PASSWORD=...
export PAPERSCRAPE_RELEASE_KEY_ALIAS=...  PAPERSCRAPE_RELEASE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```
Without those set the build produces an **unsigned** APK on purpose, so a mistake is loud.

**5. Attach to the GitHub Release**: the signed APK, and `release-notes/v4.18.md` as the release
body. `RELEASE_HISTORY.md` and this report stay in the repository rather than on the Release.

**6. After publishing**, move `BACKLOG_v4_19.md`'s items into whatever tracker you use, or leave
the file as the 4.19 starting point — it is written to be read cold.

**PREPARED — NOT PUBLISHED.** The commit, the tag, the push and the GitHub Release are yours.
