# People drawings

The scripts that draw every person the app ships. Run them from `tools/assets`, in the tooling's
environment ([Setup](../../README.md#setup)).

- **`build_people_concepts.py`** draws the four characters as cut-paper polygons: three walk
  frames, a window bust and a car bust, in summer and winter, 40 sprites per style. For each
  style it writes `<style>/svg/<name>.svg` and `.png` and `<style>/sprites.concept.json`; with no
  argument it builds all four. **`relief_eyes` is the style that ships**: its SVGs are the
  `person_*` files in `sources/svg/`. `stencil`, `relief` and `doll` are the other proposals of
  v4.25, not shipped.
- **`build_carry_sprites.py`** draws the carrying pose (the near arm raised to hold the umbrella)
  into `carry/`, with `carry/hands.json`, the grip `SceneObjectRenderer`'s `carryHandX`,
  `carryHandY` and `carryCrownY` are copied from. Each run first checks that it still reproduces
  every shipped walk frame byte for byte; if that fails, stop and look. `build_carry_concepts.py`
  drew the candidate poses in `carry_concepts/`; nothing uses it.

The wallpaper does not draw these PNGs. `tools/generate_people_layers.py` imports both scripts
and writes every figure into `res/drawable-nodpi` as fixed art plus up to four region weight
masks (skin, head, shirt or coat, trousers), and writes `engine/PeopleLayerTable.kt`;
`tools/update_people_registry.py` then rewrites the person entries of `sources/sprites.json`.
Run both, in that order, from the repository root. To change a person, edit the drawing (a
region is declared by piece, in `_PIECE_REGION`), re-run `build_people_concepts.py`, copy the
changed `relief_eyes/svg/*.png` over the shipped bases and the matching `.svg` over
`sources/svg/`, then run those two scripts.

There is no outline: every piece sits on a darker under-paper offset down and right, and
`tests/test_relief.py` checks that the shipped walk frames and busts still carry it.
`SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS` is measured off the shipped man's head, and
`OccupantHeadFitTest` fails if a redraw moves it by more than half a unit. The busts seed their
wobble with the style names they were first drawn under (`SEED_STYLE`); changing those strings
would move every vertex.
