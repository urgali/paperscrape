# Concept C — «Fretwork»

**The idea in one line.** The carved pumpkin's move («carved rather than painted») extended to the
whole family: the drawing is in what the scissors **took away**. The craters are holes cut right
through, where the sky shows, the rays are part of the disc's silhouette, and the halo is a
perforated cutting line.

## The choices, one by one

- **Paper layers.** The fewest of the three: one for the moons and the sparkle, two for the sun
  (silhouette + heart). Everything else is absence.
- **Outlines.** None.
- **Cast shadow.** None. The full moon's depth is suggested by an arc-shaped slit cut parallel to
  the limb at the lower right: a shadow made of absent paper alone.
- **Palette.** The poorest: the sun 2 tones (the current ones), the moons **1 tone** (solid white,
  zero mottling — all the information is in the cuts, and the holes stay sky whatever colour the
  user picks for the moon: it is the only one of the three concepts in which the craters are
  *guaranteed* readable under every tint), the sparkle 1 tone.
- **Detail according to size.** The holes are sized to read at 158 px (a radius of 12–19 units ≈
  8–13 px on screen); the sparkle's hole reads only on the large sparkles and disappears on the
  small ones, which go back to the classic shape — declared in the source. The halo's perforated
  line (24 dashes, r 150–158) is deliberately discreet: the renderer's `drawRadialGlow` already
  gives the ambient falloff.
- **The rays.** The third answer to the note: neither removed (A) nor softened (B), but **fused
  into the silhouette** — twelve triangular points reaching radius 102 with arched troughs at
  radius 84, one single piece. The sun is the only one of the three in which the word «rays»
  describes the shape and not a hanging ornament.
- **Departure from the current sprites, one by one.** `sun_body`: from a smooth disc to a
  postage-stamp sun in one piece. `sun_glow`: from gradient + 8 triangles to **no gradient** and a
  ring of perforation. `moon_full`: from invisible painted craters to holed craters.
  `moon_crescent`/`moon_half`: shapes identical to the shipped ones + holes near the limb.
  `star_sparkle`: a holed heart.

## Geometry and contracts

Canvases, conventions, anchors and tint classes identical to the shipped sprites (240/396/180, the
moons TINTABLE with an evenodd fill rule like `moon_jack_o_lantern`, which this concept would take
as its natural sibling in phase 2). The sprites' JVM suite is green with these PNGs in place of the
shipped ones (verified).
