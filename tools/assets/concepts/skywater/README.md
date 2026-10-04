# Sky-and-water drawings

The scripts that drew the dolphin, the sailboat (hull and sail) and the cloud the app ships,
beside the proposals they drew for v4.26. Run them from `tools/assets`, in the tooling's
environment ([Setup](../../README.md#setup)). The bird the app ships is not from here: it was
redrawn in v4.28 by `concepts/rainbird/build_rainbird_concepts.py`.

- **`build_skywater_concepts.py`** draws the bird, the cloud, the dolphin, the hull and the sail
  in three styles, `silhouette`, `relief` and `openwork`, and writes `<style>/svg/<name>.svg` and
  `.png` and `<style>/sprites.concept.json`; with no argument it builds all three. The `relief`
  dolphin, hull and sail are the drawings that ship. Every other script here, and the rainbird
  script, imports from it.
- **`build_skywater_round2.py`**, **`_round3.py`** and **`_round4.py`** drew later rounds of the
  cloud and the bird into `round2/`, `round3/` and `round4/`. The shipped `cloud_body.svg` is
  round 4's `puff_dove/svg/cloud_body.svg`, byte for byte.
- **`promote_v4_26.py`** wrote the chosen drawings into `sources/svg/`, with the dolphin's papers
  derived for contrast on the water (`LakeContrastTest`). It is kept as a record and refuses to
  run without `--overwrite-shipped-sources`: the bird has been redrawn and the dolphin, the hull
  and the sail cropped to their content since, so running it would put their v4.26 versions back.

So the shipped dolphin, hull and sail differ from `relief/svg/` only in their cropped canvas
(`width`, `height`, `viewBox`) and, for the dolphin, in those derived papers. The round-2 birds
seed their wobble with the style names they were first drawn under (`SEED_STYLE` in
`build_skywater_round2.py`); changing those strings would redraw them.
