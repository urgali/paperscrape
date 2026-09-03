# BACKLOG_v4_19.md — what 4.18 shipped without, and what 4.19 closed

**Updated for v4.19.** Items closed by that pass are kept with their closing note rather than
deleted: what a release *stopped* being true about is as much history as what it shipped. New
items found during the pass are at the end.


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

## 2. Pane fill at the neck row — **CLOSED in v4.19** by changing the criterion, as recommended

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

**Closed in v4.19 by taking that recommendation.** Fill is measured over the head's own rows --
crown to chin, the band stated explicitly in `CAR_HEAD_X_UNITS` and scanned by
`VehicleOccupantScaleTest` -- and the neck row is not part of any band. The floor stays at 50%,
which is v4.18's intent re-expressed rather than a new number; the new artwork measures
**50.8-66.3%** across all twelve shell x family x season combinations, so the criterion is met
without the row that could never satisfy it.

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

## 4. Children do not ride — **CLOSED in v4.19**

**What.** Only adults are ever seated in a vehicle. The child `head_car` busts ship as
pedestrian-coverage parity and are never drawn.

**Measured.** A child's frontal bust carries a wider shoulder-and-scarf line than an adult's —
19.5 (boy, winter) and 21.6 (girl, winter) against the adults' 18.4 — and seating one drops the
pillar light to **11-15%**, under the criterion at any authorised pane width.

**Decision.** The maintainer put this outside 4.18's perimeter explicitly, so that the one item
at risk of failing could not hold the release hostage. Recorded here as a decision taken.

**Closed in v4.19, by the second of those two routes.** The pass did not narrow a child; it drew
three new cabins around the widest bust in the set. The seat pitch was chosen on the winter girl
rather than on the adults -- at the concept pass's 20 units her bunches left 0.33 units of clear
glass between the two heads, and 23 units leaves 3.34 -- and the pillar light was re-derived
against the head instead of the pane (item 6). Measured on the shipped artwork over three bodies
x {two adults, adult+boy, adult+girl} x {summer, winter}: zero occupant pixels outside the glass
everywhere, pillar light 18.2-63.0% of head width, head gap 15.2-38.6%, fill 50.8-66.3%. The
driver is still always an adult; the passenger is now any of the four families, and is never the
driver's own.

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

## 6. The pillar light is 13%, and nobody has derived it — **CLOSED in v4.19**

**What.** The criterion "15% of the pane's width of visible glass between a head and each pillar"
was written in pass five for a single profile bust and was never derived from anything. The closing
pass lowered it to 13% to pay for clear glass between two heads, which was the right call for this
release and is still not a derivation.

**Measured.** At 13% the shipped saloon runs 13.82–14.09%, so it clears by about a point. At the
old 15% the two heads cannot be separated at all inside a cabin this shell can hold — that is the
arithmetic the closing pass turned on.

**Closed in v4.19, exactly as this entry proposed.** The criterion is now *the light between a
head and its pillar, as a share of that head's own width*, and it no longer moves when the glass
does. The floor is **15%**, derived from legibility rather than chosen: the smallest a car is ever
drawn is the far lane, where the projection puts **1.242 px on a local unit** on the reference
1080x2340 device; a band of glass under about 3 px reads as an antialiasing seam rather than as
daylight, and 3 / 1.242 = 2.41 units = 13.3% of an adult head's 18.08, rounded up to 15% so the
floor sits above the threshold it comes from rather than on it. The same number and the same
argument give the head-gap floor. Measured worst case on the shipped artwork: 18.2%.

Two of the three bodies were redrawn to clear it. The compact and the saloon both brought a
driver's crown within about a unit of a raked A-pillar; their glasshouses were stood up (glass
top-front -15 -> -19 on both, roof fronts moved to match) rather than the criterion lowered.

---

## 7. The sprite memory ceiling, and 20 files the renderer can never reach — **partly closed in v4.19**

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

**v4.19 did exactly that, and it paid for three car bodies.** The four adult bases were deleted
(297 792 B) after verifying pixel-by-pixel that each was byte-identical to one of its own tone
copies -- the man's to `_skin1`, the woman's to `_skin0` -- so no tone was lost. The registry now
declares the retirement (`retiredBases`) and points the generator at the surviving heir, because
"a derived variant's base must ship" is a real rule that a silent deletion would have broken.

**The 16 child busts are no longer dead weight**: children ride (item 4), so the twelve child tone
files are decoded content. Four child *bases* remain redundant in the same way the adults' were,
and were deliberately left alone -- the pass brief put children's artwork behind a hard "never",
and 0.244 MiB of headroom did not make it necessary.

**Second lever, and the larger one: the lamps.** v4.18 shipped two full-car-width overlays,
282x18 px each and almost entirely transparent, to paint two tiny lenses. v4.19 replaces them with
four small lenses (18x12 and 12x12 px) shared by all three bodies *and* the fire engine -- 2 880 B
against 40 608 B, and one drawing instead of one per shell.

**Where the set stands: 260 PNGs, 29 629 044 B = 28.256 MiB against the 28.5 MiB ceiling, 255 372 B
free.** The ceiling was not raised and did not need to be.

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


---

## 13. A force-stopped wallpaper does not come back — **new in v4.19**, and it is the platform

**What.** If the process hosting the live wallpaper is force-stopped -- from Settings, from a task
killer, or by any equivalent hard kill -- Android does not restart it. `WallpaperManagerService`
rebinds with a **null** component, which resolves to `com.android.systemui/ImageWallpaper`, and
then calls `saveSettingsLocked`: the fallback is **persisted**, so it survives a reboot too. The
user has to set the wallpaper again by hand.

**Measured, on the OnePlus 6T running Android 15.** Reproduced deliberately twice. The log is
unambiguous and contains no crash:

```
WallpaperManagerService: bindWallpaperComponentLocked: componentName=null
WallpaperManagerService: WPMS.onServiceConnected-ComponentInfo{com.android.systemui/...ImageWallpaper}
WallpaperManagerService: WPMS.saveSettingsLocked-0
```

**Why it is here.** The v4.19 concept pass opened with the phone showing the static system image
rather than PaperScrape, which looked like a stability defect. It is not one: there is **no crash
record of the package anywhere on the device** -- zero dropbox entries, nothing in the crash
logcat buffer, and the only two tombstones are the vendor radio daemon (`qcrild`) aborting, which
is unrelated. What is left is this path.

**Cost of closing.** Nothing PaperScrape can do from inside its own process: the decision is the
platform's and it is taken after the process is gone. What *could* be done is to notice it --
`WallpaperManager.getWallpaperInfo()` returning something other than this app while the settings
screen is open would let the app say "I am not your wallpaper any more" and offer the picker.
That is a UI feature, not a fix, and it is not scheduled.

---

## 14. Special-vehicle spawn density still doubles up — **still open, now photographed**

Item 11 was recorded from the code. A v4.19 night capture shows **two fire engines in the same
frame in the same lane**, which is what that item predicted. Unchanged in cause and cost; the
photograph is in the pass's capture set.

---

## 15. A pedestrian behind a nearer car is hidden to the shins — **new in v4.19**

**What.** A pedestrian on the near pavement standing behind a far-lane car has their feet covered
by the car's body. The draw order is right -- people are painted before traffic since v4.6,
because a car *is* nearer -- but with the taller v4.19 bodies more of the figure goes behind it.

**Measured.** The deepest figure's feet sit **0.0135 of screen height below a far-lane car's roof
line**, 32 px on a 2400 px screen, against v4.18's 0.0100 and 24 px. `PeopleTrafficDepthTest`
carries both numbers.

**Cost of closing.** Moving the pavement rows or the lanes, which is a scene-layout change with
consequences for every object that stands on them -- out of proportion to eight pixels. Recorded
so the next pass that moves either knows this is one of the things that moves with them.
