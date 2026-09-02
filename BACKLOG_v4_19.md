# BACKLOG_v4_19.md — what 4.18 ships without

Everything still open at the close of **v4.18**, in one place, so 4.18 is closable and nothing
is lost. Each entry says what it is, what has actually been measured about it, and what a fix
would cost — not just that it exists.

Nothing here is a regression introduced by 4.18 unless the entry says so. Items marked
**decided** were closed as decisions rather than left as unmet goals.

---

## 1. GL-GOLDEN-ADRENO — a characterised driver gap

**What.** The three GL goldens (`gl-day`, `gl-lake-busy`, `gl-thunderstorm`) do not render
identically on the OnePlus 6T's Adreno 630 and on the emulator's reference driver.

**Measured.** 1.2–1.4% edge displacement on byte-identical content, against a 3% gate — so it
passes everywhere and is not a test failure on either environment. It was 1.108 / 1.290 / 1.682%
against a 0.500% gate before pass six widened the gate on the evidence. The goldens have been authored
on the emulator's reference driver since pass six and pass on both.

**Cost of closing.** Either per-driver golden sets (doubles the GL golden maintenance and makes
"the golden" ambiguous) or a shader/rounding change that removes the driver's freedom at the
sail edges. Neither is worth doing for a gap that is under the gate.

**Recommendation.** Leave characterised. Re-measure if the gate is ever tightened.

---

## 2. Pane fill at the neck row — 43.7–45.3%, and why no pane width fixes it

**What.** The pass eight fill criterion (≥50% of the glass filled by occupant) holds at the head band
(67.1–69.5%) and averaged over the head's own rows (60.8–61.2%), and fails at the very bottom
of the scanned band — the row four units above the sill, which crosses the **neck**.

**Measured.** 43.7–45.3% across both lanes and all three civilian vehicle types. A neck is
narrower than a head at any seat count; the shortfall is not a seating problem.

**The arithmetic (why it is not a pane-width problem).** Let *w* be the bust ink at a row and
*W* the pane there. Fill needs `2w ≥ 0.5W`; the pillar light needs `2w + gap ≤ (1−2L)W` with L = 0.13. At the
neck the ink collapses to ~11 units while the pane is at its widest, so fill wants a *narrow*
pane exactly where light wants a wide one. The two cross only below ~39 units of pane at the head
band — where the light criterion has already failed, and the pane is 54. **No pane width satisfies both.**

**Cost of closing.** Redraw the busts with a wider neck/collar, which is head artwork and was
out of scope for the whole 4.18 arc; or drop the neck row from the fill criterion, which is a
criterion change and the maintainer's call.

**Recommendation.** Change the criterion, not the artwork: measure fill over the head's rows
(where it is 60.8–61.2%) and state the band explicitly.

---

## 3. Winter visible-skin parity — 0.88 one way, 1.58 the other

**What.** Winter facial parity between a seated occupant and a winter pedestrian, measured on
*visible skin*, is 0.876 (man) and 1.576 (woman).

**Measured, and what it is not.** Measured head-with-headwear — crown of the hat to the chin,
the landmark a viewer actually reads — the same pairs are **0.905 and 0.964**, inside the ±10%
band the summer faces are held to, and that is asserted in `OccupantHeadFitTest` since pass eight. No
scale error produces a ratio below 1 for one figure and above 1 for the other; the cause is that
the woman's winter *walking* sprite hides more of her face than her winter bust does.

**Cost of closing.** Re-drawing how much chin a winter scarf covers, on both the walk cycle and
the bust, for both adult families — head artwork, and it would move nothing a viewer can see.

**Recommendation.** Leave. It is bounded (0.5–2.0) and named for what it is.

---

## 4. Children do not ride — **decided**, not missed

**What.** Only adults are ever seated in a vehicle. The child `head_car` busts ship as
pedestrian-coverage parity and are never drawn.

**Measured.** A child's frontal bust carries a wider shoulder-and-scarf line than an adult's —
19.5 (boy, winter) and 21.6 (girl, winter) against the adults' 18.4 — and seating one drops the
pillar light to **11-15%**, under the criterion at any authorised pane width.

**Decision.** The maintainer put this outside 4.18's perimeter explicitly, so that the one item
at risk of failing could not hold the release hostage. Recorded here as a decision taken.

**Cost of closing.** A child-specific bust with a narrower shoulder line, or a vehicle with wider
glazing than the saloon (a bus, an estate) that can light one.

---

## 5. A clothing-colour axis for the occupants — blocked by the sprite ceiling

**What.** In the shipped artwork a family carries its hairstyle **and** its clothing: every woman
bust is the red top with the yellow band, every man bust the blue one. 4.18 guarantees the two
occupants of a car are never the same family, which settles the twin-pair defect by construction
— but it also means every car carries one man and one woman.

**Measured.** A second outfit across both adult families, both seasons and three tones is
**12 sprites × 74 448 B = 893 376 B decoded**. The set has **553 236 B** of room under the
28.5 MiB ceiling. It does not fit. A partial axis (one family only, 6 sprites, 446 688 B) does
fit, and would half-solve it.

**Cost of closing.** Either raise the ceiling (see item 7) or free the room (also item 7), then generate
the variants the way `tools/generate_skin_variants.py` already generates the skin tones — it
moves one flat colour and verifies every other colour keeps its exact pixel mask, which is
exactly the shape of this change.

---

## 6. The pillar light is 13%, and nobody has derived it

**What.** The criterion "15% of the pane's width of visible glass between a head and each pillar"
was written in pass five for a single profile bust and was never derived from anything. The closing
pass lowered it to 13% to pay for clear glass between two heads, which was the right call for this
release and is still not a derivation.

**Measured.** At 13% the shipped saloon runs 13.82–14.09%, so it clears by about a point. At the
old 15% the two heads cannot be separated at all inside a cabin this shell can hold — that is the
arithmetic the closing pass turned on.

**Cost of closing.** Decide what the number is *for*. If it is "the head must not look wedged
against the pillar", it should be expressed against the head's own width rather than the pane's,
and it would then stop moving every time the pane does.

---

## 7. The sprite memory ceiling, and 20 files the renderer can never reach

**What.** The decoded-sprite ceiling was raised 26 → 28.5 MiB in pass seven to fit the occupant heads.
The shipped set is **27.972 MiB** against it.

**Measured.** The `head_car` family is 32 files / 2.272 MiB (8.1% of the set). `drawCar` can
reach **12** of them (two adults × two seasons × three tones, 0.852 MiB). **20 files / 1.420 MiB
are never decoded**: 16 child busts (1.136 MiB, unseatable — see item 4) and 4 adult *base* drawings
(0.284 MiB) whose `_skinN` copies already carry the tones. Deleting all twenty leaves
**26.552 MiB — still 0.552 MiB over 26**, so pruning the occupants does not recover the old
ceiling; that would need the `head_window` family (3.319 MiB) or a share of the 96 walker
recolours (11.486 MiB).

**Cost of closing.** Deleting the 4 redundant adult bases is free and safe (0.284 MiB) and would
pay for half of item 5. Deleting the 16 child busts breaks the pass seven coverage-parity criterion and
should not be done while item 4 is only "decided" and not "declined".

**Recommendation.** Reclaim the 4 redundant bases; keep the ceiling at 28.5 and make the next
pass that wants to grow the set argue for it in `SpriteGeometryTest`, as pass seven did.

---

## 8. `VehicleOccupantAbCapture` cannot be used as direction evidence

**What.** The harness hardcodes `reverse = true` on every vehicle it renders, so every frame it
produces faces left by construction.

**Consequence.** Direction evidence must come from live screengrabs of the running wallpaper.
This has cost a wrong conclusion once and is worth fixing or deleting.

**Cost of closing.** Small — parameterise `reverse` and render both. It was left alone through
pass six–4.18 because it is measurement scaffolding, not shipped code.

---

## 9. Custom themes saved before pass six can carry duplicate storefronts

**What.** A theme saved by a build older than pass six can contain two of the same shop type on one
tile. pass six fixed the generator and added the dedup, but did not migrate stored themes.

**Cost of closing.** A one-shot migration in the theme store's schema upgrade path, plus a test
with a stored before pass six theme as a fixture.

---

## 10. Shops and towers share the buildings palette

**What.** A shop and a tower take their body colour from the same palette entry, so a theme
cannot tint them apart. The painted shop fronts added in 4.18 now carry the differentiation, so
this reads much less than it did.

**Cost of closing.** A theme-and-backup **schema** change (a new palette slot), which means a
migration and a backup-format version bump.

---

## 11. Special-vehicle spawn density can double up

**What.** Two police cars, or two fire engines, can be on screen at once more often than the
density slider implies; the per-type spawn is not de-duplicated against what is already on the
road.

**Cost of closing.** Small — a per-type cap in the candidate system, plus a presence test.

---

## 12. Release dates before v73 are unrecoverable

**What.** The repository was received without Git history, so no release date before v73 can be
stated. `RELEASE_HISTORY.md` leaves them blank deliberately rather than guessing. Dates are
recorded from v73 onward. Nothing to fix; recorded so it is not rediscovered.
