# BACKLOG v5.0 — the neighbourhood in production

Items 113 onward. The numbering is continuous across backlogs, so item 25 means the same thing
wherever it is cited.

## Carried forward from `BACKLOG_v4_31.md`

Restated by number and one line each, so nothing is lost by the move. The reasoning stays in the
file that holds it; read it there.

| item | from | what | still |
|---|---|---|---|
| 25 | `BACKLOG_v4_30.md` | The palm does not belong to the scene's grammar | **OPEN, and more visible.** Out of scope for this release by instruction. The redrawn buildings are cut-out paper with a shadow card and a wobbled edge; the palm is neither, and standing it next to a dealt house makes the difference easier to see than it was next to a flat facade. Not touched, and the photographs in `consegna_v5_0/immagini/` show it beside the new work |
| 67 | `BACKLOG_v4_26.md` | The `_UNITS` frame rule sees Kotlin and not the generators | **OPEN**, untouched here |
| 78 | `BACKLOG_v4_27.md` | The cross-driver GL measurement is no longer taken anywhere | **OPEN, and unchanged by this release.** Still a condition rather than work: only a second GPU vendor closes it. Re-authoring the three references here did **not** cost it — it was already gone, since v4.26. `GlDriverGapGuardTest` measured **0.00 / 0.00 / 0.00%** after the re-authoring, which is the same ~0 it has read for six releases |
| 90 | `BACKLOG_v4_29.md` | A pixel claim that omits its unit is invisible to the guard | **OPEN in its stated half** |
| 92 | `BACKLOG_v4_29.md` | `SceneObjectRenderer.drawPreviewPair` is dead and safely removable | **OPEN, and it was kept deliberately.** It was ported to the new composer rather than removed, because removing dead code and redrawing every building in one change makes the diff unreadable. It is three lines now and still unreachable; delete it in a release that is not also moving the artwork |
| 94, 99, 100, 103 | `BACKLOG_v4_30.md` | see that file | **OPEN** |

`BACKLOG_v4_30.md` also continues to carry forward items 18, 30, 40, 50–55, 63 and 83. **Item 56 is
no longer among them** — it closes below.

---

## Summary

| item | what | outcome |
|---|---|---|
| 56 | The three GL reference frames portray a scene that no longer exists | **CLOSED as a decision, not as a defect that went away** — re-authored on the maintainer's instruction, with the attribution written down and one premise corrected |
| 108 | The dolphin is not on its leap point | **RESOLVED** — moved, and a test measures the origin against the alpha channel so the claim cannot rot again |
| 113 | The two shops draw a little over half the height they declare | **OPEN, for the maintainer** — a question about the drawing, with the numbers and the photographs attached |
| 114 | At the extremes of a user's colours the derived surfaces stop separating from the wall | **ACCEPTED and recorded**, with the measurements, on the maintainer's decision. Inherited from the shipped artwork, not introduced |
| 115 | v4.31's own correction of the dolphin carried three wrong numbers | **RESOLVED** — corrected in both places, and the registry turned out to have been right all along |
| 116 | A tower is 33 sprite blits per frame where the shipped facade was 6 | **MEASURED** — see the v5.0 report for the frame cost and what was done about it |

---

## 56 — The three GL reference frames: **CLOSED**, as a decision

**Not a defect that was resolved. A decision the maintainer took**, in the same form v4.20 used to
close item 1 of `BACKLOG_v4_19.md`: the thing itself is not fixed, what changes is that it is
chosen, written down, and no longer rediscovered.

The instruction:

> si, rifacciamole. E' inutile lasciare i test con un cell non piu' usabile.

**What was wrong with them.** v5.0 redraws every building from a blank sheet, and the three
references portrayed flat facades the scene no longer draws. **MISURATO** on the BV6600 against the
committed files, before touching anything:

| | edge displacement | limit |
|---|---|---|
| `gl-day` | **21.21%** | 3.00% |
| `gl-lake-busy` | **19.49%** | 3.00% |
| `gl-thunderstorm` | **21.64%** | 3.00% |

`GlDriverGapGuardTest` failed beside them on the same three numbers — four reds, one question.
Six to seven times the worst driver difference ever recorded here, so it was never a tolerance
question, and none was moved.

**Proved to be the scene and not the driver, rather than assumed.** The committed `gl-day` agrees
with the committed Canvas `day.png` to **0.017%** of pixels at Δ≥16 across the sky band and
**0.752%** across the road, and disagrees at **27.3%** across the buildings band alone. The
difference is exactly where v5.0 drew, and nowhere else.

**Re-authored** on the Blackview BV6600 (PowerVR Rogue GE8320, Android 10) on **2026-09-13**, one
frame per scene through the shipped `GlSceneTarget`, then re-verified with the update flag *off* so
the Canvas cross-check and the per-region gates judged them as committed files. All three pass, and
the guard reads **0.00 / 0.00 / 0.00%**.

### The premise this item carried for three releases was false

`BACKLOG_v4_30.md`'s line for item 56 calls them "the three **Adreno-authored** references" and says
re-authoring them "is what would cost the cross-driver check `GlDriverGapGuardTest` exists for".
**They have not been Adreno frames since v4.26.** `BACKLOG_v4_26.md` item 71 re-captured all three
on this device's PowerVR driver — forced by the sky and water redraw, ratified by the maintainer in
v4.27 — and v4.28 re-authored `gl-day` again on the same device. `V4_28_REPORT.md` says so in one
line: "which is where the GL references have been authored since v4.26". The measurement above
confirms it independently: frames captured on an Adreno in v4.21 could not contain v4.26's clouds,
v4.28's bird and v4.30's people, and these do.

The sentence survived because it was **copied from a KDoc that was never updated**.
`GlDriverGapGuardTest` and `GlGolden.EdgeDisplacement` both still said "authored on the OnePlus 6T's
Adreno 630 since v4.21". Both are corrected, with the authoring history as a table rather than a
sentence.

**So what this release actually gave up is nothing.** The cross-driver observation was given up in
v4.26 and ratified in v4.27. What item 56 was protecting had been gone for three releases; the item
was being kept open to pay a price that had already been paid.

### What is genuinely lost, and it is the numbers

The cross-driver gap itself is not gone — two conformant rasterisers still disagree at an edge —
but nothing observes it, and after this release nothing in the tree depicts an Adreno frame either.
The figures now survive **only** in `GlGolden.EdgeDisplacement` and `GlDriverGapGuardTest`, with
their device and date beside each:

| measured | on | when |
|---|---|---|
| 1.18 / 1.07 / 0.92% | OnePlus 6T, Adreno 630, against emulator-captured references | when `EdgeDisplacement` was derived; in v4.18's notes (prepared 2026-09-01) |
| 1.2–1.4% | OnePlus 6T, Adreno 630 | v4.19's re-measurement (prepared 2026-09-03) |
| **0.00 / 0.00 / 0.00%** | BV6600, PowerVR Rogue GE8320, Android 10 | v5.0 (2026-09-13) |

**From here `GlDriverGapGuardTest` reads ~0 until a second driver exists**, and this machine cannot
produce one: the maintainer's personal phone is where an update is proved, not a test device. The
test is kept and its 2% limit is untouched — a run that reported nothing would be
indistinguishable from a run that did not happen, and on the day a second vendor appears that limit
is what says whether it sits inside the band the Adreno one did.

**Item 78 stays open** and is the place that tracks it: it is a condition, and one sentence closes
it — run the suite on a second GPU vendor and re-derive the characterisation from that run.

**Nothing else moved.** `MAX_DISPLACED_FRACTION` is still 0.03, `CHARACTERISED_MAX_DISPLACED_FRACTION`
still 0.02, `SceneGolden.MAX_DIFFERING_FRACTION` still 0.0, and the 30 Canvas goldens are
byte-identical to the delivered v4.32 ZIP.

---

## 113 — The two shops draw a little over half the height they declare

**`SceneSpace.SceneVariant` states a real height in metres and a drawn height in local units, and
every proportion in the scene is the first divided by the second.** For the two shops those numbers
now describe a building that is not the one being drawn.

Measured off the shipped table, over every deal each family has, with snow excluded because a drift
is weather and not building:

| family | declares | draws | as metres | ratio |
|---|---|---|---|---|
| `HOUSE_SMALL` | 110 u = 5.76 m | 96.2 – 135.4 u | 5.04 – 7.09 m | 0.87 – 1.23 |
| `HOUSE_LARGE` | 145 u = 7.60 m | 136.9 – 207.0 u | 7.17 – 10.85 m | 0.94 – 1.43 |
| `TOWER` | 182 u = 15.60 m | 197.3 – 202.3 u | 16.91 – 17.34 m | 1.08 – 1.11 |
| **`RESTAURANT`** | **96 u = 8.20 m** | **56.0 u** | **4.78 m** | **0.58** |
| **`BAR`** | **92 u = 7.70 m** | **54.1 – 74.5 u** | **4.53 – 6.24 m** | **0.59 – 0.81** |

The three that vary, vary *around* their declaration: that is the redraw working, because a house
with an extra storey is taller than one without and is meant to be. **The two shops do not vary
around theirs.** The restaurant draws 58 % of it on the only deal it has, and the bar between 59 %
and 81 %.

### What it is not

It is **not a scaling bug, and it cannot be fixed by scaling.** Every family draws at the one metre
the scene measures everything else in — `8.2 m / 96 u = 0.08542 m` per unit — and the pieces are
internally correct at it: the restaurant's own door is 20 units, which is **1.71 m**, which is a
door. Scaling the pavilion up to the 8.2 m it declares would give it a 2.9 m door and a giant's
awning. The figure chosen for each shop is simply a **single-storey building** where the facade it
replaces was two.

### What it is

A property of the artwork the maintainer chose in fase 4, visible in the fase 5 photographs that
the choice was made from — `K0_spedito_schiera_city_h12_giorno_tinta-default` against
`K1_mix_schiera_city_h12_giorno_tinta-default`, where the shops stand about half the height of the
houses beside them and in the shipped set they stand level with them.

### Why it is open rather than decided here

Two answers are available and they are not the implementation's to pick.

1. **Keep the drawing and correct the declaration**: restate `RESTAURANT` as `(4.78 m, 56 u)` and
   `BAR` as the figure it draws. This is *almost* pixel-neutral — the metres-per-unit is the same
   to five figures — but "almost" is not a word `SceneGolden.MAX_DIFFERING_FRACTION = 0.0` accepts,
   so it would have to be measured rather than argued, and it makes the table true at the cost of
   saying the town's shops are 4.8 m buildings.
2. **Redraw the shops taller**, which is a new round of proposals and photographs.

**Nothing was changed.** The rendering is exactly what was photographed and approved, which is the
one option that risks nothing; `BuildingHeightDeclarationTest` states both halves with the numbers
above and fails if either shop moves further from its declaration than it is today.

---

## 114 — At the extremes of a user's colours, the derived surfaces stop separating

**Accepted by the maintainer, recorded here with the measurements rather than left implicit.**

Every tinted surface of a building is `w · wall + (1 − w) · k` with `k` only ink (`#2B2A33`) or
white, so at the ends of the range the term that does the separating has nothing left to work with:

- on a **very pale** wall, the cards derived *towards white* — window frames, coping, the tower's
  atrium — sit at **ΔL 0.03 – 0.09** from it;
- on a **very dark** wall, the cards derived *towards the ink* — the ground-floor band, the roof,
  the door — sit at **ΔL ≈ 0.00**.

**Inherited, not introduced.** The same measurement on the shipped artwork: its roof derivation on
a wall at L 0.03 gives **−0.013**. Photographed on both, in fase 5 §6.4, over four user colour
pairs (default, pale, saturated, dark) by day and by night — the pairs written through the same
`colorDay1/Night1/Day2/Night2` fields the settings screen writes.

**What it would cost to fix.** The direction of each derivation would have to be chosen from the
wall's own luminance, which is one more mask per piece: **up to +1 791 684 B**, against the
707 872 B of headroom the decoded ceiling has after this release. It does not fit, and buying it
would mean giving up silhouettes, which is what the redraw is for.

**And the thing that does not go wrong.** The fixed art and the glass stay legible at every
extreme: `|ΔL|` of the glass against its wall is **≥ 0.17** in all four pairs, because the window
ramp is two constants and does not descend from the wall at all.

**A way to make it visible to the user was asked for and is not offered.** The honest place for it
would be a warning beside the colour picker when a category's colour passes the luminance where its
own derivations collapse — which is a settings-screen feature with a string to translate, not a
free line. It is written here so the option is on the record; it was not built.

---

## 115 — v4.31's correction of the dolphin was right in its conclusion and wrong in its arithmetic

Item 108 measured that the dolphin sits **(+0.87, +1.0) units** off its leap point and corrected
the two comments that said otherwise. **The conclusion is exactly right** — re-measured here off
the alpha channel, `+0.8667, +1.0000`. The derivation printed under it is not:

| said (v4.31) | is | where |
|---|---|---|
| canvas `342x168` | **342x171** | `PaperRenderer` KDoc and the registry note |
| ink at `1,0..342,168` | **[1, 3, 342, 171]** | both |
| content centre `(57.167, 28.0)` | **(57.167, 29.0)** | both |
| origin `(-56.3, -27)` | the code held **(-56.3, -28)** | registry note only |

The stated centre of 28.0 against an origin of −28 gives a y displacement of **zero**, contradicting
the `+1.0` two sentences later in the same paragraph. `sources/sprites.json`'s own `contentBox` had
`[1, 3, 342, 171]` all along and was never wrong.

**Both are corrected, and neither is a comment any more.** `DolphinLeapOriginTest` reads the content
box out of the shipped PNG and asserts the origin is its negative, which is the claim the constants
exist to make. Twice now a correct conclusion has been carried by arithmetic nobody could check;
this is the third time the fix is a check rather than a better sentence.

---

## 116 — A tower is 33 blits per frame where the shipped facade was 6

Counted from `NeighbourhoodTable` — three tiers (fixed + wall mask, plus the atrium's glass), nine
stamped window rows, three bays and a crown — and confirmed on the phone by the composer's own
per-instance log in fase 5. The other families: a large house 9–14, a small house 5–9, each shop 3.

**A count is not a cost**, and the release brief required the cost. See `V5_0_REPORT.md` for the
frame-time and CPU measurement on the `perf` build, on both backends, and for what was decided.
