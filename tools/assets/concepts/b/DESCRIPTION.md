# Concept B — «Scissors»

> **The record of a concept round, closed.** Concept B was chosen and its eight celestial sprites were promoted
> in v4.23 (`RELEASE_HISTORY.md` § v4.23); the choices this page says the maintainer would make were made then.

**The idea in one line.** Paper cut out *by hand*: every shape is a polygon with a deterministic
wobble of ±2–2.6 units (a cut of the scissors, not a compass), the sun floats on the sky with its
cast shadow, and the rays are not removed but **tamed**: twelve rounded petals in place of the
eight sharp triangles.

## The choices, one by one

- **Paper layers.** Two per body (shape + heart for the sun; shape + craters for the moons), plus
  the shadow where there is one. The layers do not do the work: the edge does.
- **Outlines.** None. The edge is defined by the cut itself: 20–28 vertices with a fixed jitter,
  written into the source's points (never generated at runtime).
- **Cast shadow.** **Yes, and it is the concept's signature — but only where there is light
  behind.** The sun casts its own shape shifted by (+6,+8) in black at 13 %: light from the upper
  left, consistent with the shadow band on the Oak's trunk. The moons and the sparkle do
  **not** cast one, by a choice written into the sources: at night the sky behind the paper is
  already dark, a shadow has nothing to darken (and in the lunar mask it would drag the mean of the
  greys below the MULTIPLY floor). The shadow is a fact of the day.
- **Palette.** The narrowest of the three, together with A, on the sun: the two current colours
  (#F0A03C, #F7CE64) + shadow. Moons: one tone (#F4F4F4) + craters (#E1E1E1), mean ≥ 220.
  Sparkle: the cream of the star points, with four deliberately unequal points.
- **Detail according to size.** A wobble of ±2.4 on a radius of 99 is ~2.4 %: at 158 px it reads
  as a hand, at 53 px (preview) it disappears without smudging. The glow's petals sit in the
  150–198 ring documented at the call site; at arm's length they read as a corolla, not as teeth.
- **Departure from the current sprites, one by one.** `sun_body`: from a compass to freehand +
  shadow. `sun_glow`: from 8 detached triangles to 12 round-tipped petals; the radial halo reduced
  (0.40/0.16) because the corolla already carries presence. `moon_full`: from invisible craters to
  irregular, readable craters on a hand-cut disc. `moon_crescent`/`moon_half`: the same phase, a
  live edge. `star_sparkle`: from a symmetric rhombus to an asymmetric sparkle (N 82, E 78, S 74,
  W 76 units).

## Geometry and contracts

Canvases, conventions, anchors and tint classes identical to the shipped sprites; the disc's
nominal radius is 99 (instead of 102) because the sun's shadow has to stay inside the 240 canvas —
on screen it is 3 % less diameter, below what can be perceived at the distance of use. The sprites'
JVM suite is green with these PNGs in place of the shipped ones (verified).

---

## Round 1b update (2026-09-05, after the maintainer's choice)

The family is now **complete: 8 sprites of 8** — added `moon_gibbous` (the same phase geometry as
the shipped sprite, limb and terminator cut by hand, solid craters) and `moon_jack_o_lantern` (the
**property preserved**: it stays the only moon with **holes** — a carved face cut right through,
the face's geometry identical to the shipped one ×3 — and takes from B only the hand-cut edge and
the single tone; its distinction from the ordinary moon holds by construction: solid craters and
zero holes against eleven holes and zero craters).

**The halo is under revision at the maintainer's request** («one single circle around the sun»):
three variants in `halo/{v1,v2,v3}/`, recorded in `halo/variants.json`, each with its own
compromise on gradient / cut / shadow. `sun_glow.svg` in `svg/` stays the petal version until the
maintainer chooses. `sun_body` is not touched.

## Round 1c update

Round 1b's photographs closed the question of the ring: **no ring reads as rays** (rays radiate, a
ring surrounds) and the petals read as a daisy because of their **gap** from the disc. Round 1c
tries **the attached crown** — scallops coming out of the disc's edge, with no gap — in two
variants that differ only in depth (`crown/low_scallops`, peaks at r=132; `crown/high_scallops`,
r=156), both without a gradient and with B's shadow. The inner radius is derived from the shipped
`sun_glow` and from B's disc (base 94 < 96.6; troughs 104 > 101.4).

Moons: the pumpkin's faceting is matched to the limb's step of the other phases (40 vertices,
9°/facet); the version **with an outer ring** (in a folder of its own) sits beside the single-tone one:
the maintainer chooses. The star is not touched (5–7 real px: no choice is visible).

## Round 1d update

**The maintainer's decisions**: B confirmed; **halo = V2** («a clean ring over the halo»); the
scalloped crowns and V1/V3 closed (the high-scallop crown stays on record as the point where the
sun becomes a sunflower). V2 is tuned on two parameters only, in `halo/v2_refined/{a,b,c}/`: the
ring at **opacity 1.0** (cut paper, like everything else in B) and the gradient as it is / absent /
warmed. The ring's geometry and colour unchanged; `sun_body` not touched.

**The pumpkin redrawn from scratch** (`svg/moon_jack_o_lantern.svg`): the device kept (the only
moon with holes; it must say Halloween at ~100 px), everything else redrawn in B's language — edge
and holes cut by hand, no coordinate from the shipped face, **no outer ring** (the reason is in the
source). Round 1c's ringed version removed: the question is settled.

**`moon_full` at 40 vertices**: round 1c's residue closed; the whole family is at 9°/facet.

**Stars**: no redraw of the sprite — the diagnosis is in round 1d's report (the lever is in the
renderer, photographed with throwaway local builds that never entered the archive).

## Phase 2 / Part A update

**The maintainer's decisions, closed**: halo = `v2_refined/c` (a compass-struck ring, opacity 1.0,
warmed gradient); stars = configuration 1 (`s = radius / 16f`); B moons with `moon_full` and the
pumpkin at 40 vertices; `sun_body` unchanged.

**The pumpkin, two variants** (`pumpkin/{minimal,pushed}/`, register `pumpkin/variants.json`), both
inside the constraint — a round silhouette, one sheet, holes cut through as the only device. Round
1d had taken the eyes from angled wedges to rounded rectangles, which read as slabs: the
**minimal** one keeps round 1d's edge, nose and banded mouth with fangs and takes the eyes back to
a **hand-cut wedge**; the **pushed** one looks for "really Halloween" inside the disc — a wedge with
a steeper brow, a **saw-toothed grin** with fangs from both lips (still a single hole), and **four
tapered ribs** cut into the high crown, the one thing the constraint leaves open. The maintainer
chooses by looking at the captures; `svg/moon_jack_o_lantern.svg` stays round 1d's until then.

**The sparkle redrawn** (`svg/star_sparkle.svg`, a single version, a 180×180 canvas): with
configuration 1 the sparkle sits at 10–13 real px and the shape counts again. A full waist (24–28
units, 27–31% of the radius: the paper is no longer cut thinner than round 1a's ~12, which at that
scale became a one-pixel cross), the unequal points unchanged, concave sides of two segments with a
wobble; sixteen written vertices. Photographed **only** with configuration 1 in a throwaway build:
the patch to the constant is not in this archive (it enters in Part B).
