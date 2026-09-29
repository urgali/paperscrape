# Concept A — «Layers»

**The idea in one line.** The exact recipe of the Broad Oak carried up into the sky: every body
is a stack of papers glued slightly off centre, the light comes from the upper left, and all the
depth is in the offset between the layers. No rays: the maintainer's note taken literally.

## The choices, one by one

- **Paper layers.** Four for the sun (dark amber base → pale yellow crown) and four for the full
  moon (base `#DCDCDC` → crown `#FFFFFF`), three bands for the crescent and the half moon, two for
  the sparkle. Every layer is glued shifted toward the upper left, the same direction as the light
  on the Oak's crown; the lower layers show at the lower right and act as a shadow without a
  shadow existing.
- **Outlines.** None, like the tree crowns.
- **Cast shadow.** Absent by construction: the layer beneath, showing through, does the shadow's
  job. It is the crown's convention, not the one for objects on the ground (the anchoring ellipse
  makes no sense in the sky).
- **Palette.** Sun: 4 warm tones from the family of the two current ones (#DC8428, #F0A03C,
  #F7CE64, #FBE289). Moons: 4 neutral greys between #DCDCDC and #FFFFFF — the same ratios as the
  crown (a floor of 13.7 % mottling, a mean over the opaque pixels ≥ 220), so the MULTIPLY of the
  theme's moon colour goes through the mask as it goes through the trees. The day/night change is
  not in the PNG: the runtime tint does it, as it does today.
- **Detail according to size.** The disc is seen at ~158 px on this screen: four layers at offsets
  of 4–15 units stay readable there and fade out gracefully in the theme preview (~53 px). The
  craters are 20–27 levels from the tone they sit on — in the current sprite they were 4 levels,
  that is, invisible. The sparkle (10–20 px) gives the paper beneath, rotated by 45°, only a soft
  halo, not a drawing.
- **Departure from the current sprites, one by one.** `sun_body`: from 2 flat concentric circles
  to 4 off-centre layers. `sun_glow`: **the rays are gone**, only the radial halo is left (already
  sanctioned by DESIGN_NOTES for the sun). `moon_full`: from invisible craters to layers and
  readable craters. `moon_crescent`/`moon_half`: from a flat silhouette to bands that share the
  horns and the limb and differ only in the terminator. `star_sparkle`: from a single rhombus to a
  double paper with a light heart.

## Geometry and contracts

Canvases, scale conventions, anchors and tint classes **identical** to the shipped sprites:
240/396/180, `CANVAS_PIXELS`/`SCENE_UNITS`, `SPRITE_CENTRE`, the moons TINTABLE and the rest
FIXED_ART. No call site changes; `SkySpriteAnchoringTest`, `SpriteTintClassTest`,
`SpriteGeometryTest`, `SpriteCanvasConventionTest`, `ThemePreviewSceneTest`,
`BackgroundScrollGeometryTest` pass with these PNGs in place of the shipped ones (verified).
