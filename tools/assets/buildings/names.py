"""Production names for the neighbourhood pieces.

The generator draws under the names the concept rounds used (`k1_` = «Scatola», `k2_` =
«Profilo»), and those letters are round labels: they name which *proposal* a drawing came from,
which is a fact about September 2026 and not about the artwork. What ships is the mix, which is
no K at all, so the shipped drawables are named for what they are. The map is here rather than in
the drawing code so the concept scripts stay byte-comparable with the ones the proposals were
photographed from -- the PNGs this produces are the fase-4/5 pixels under another file name.

Longest prefix wins, so `k1_roof_turret_b_tower` is not read as `k1_roof_turret_b`.
"""
from __future__ import annotations

PREFIXES: dict[str, str] = {
    # K1 «Scatola» -- the two houses, a stack of pieces per instance
    "k1_ground_house_a": "house_small_ground",
    "k1_storey_house_a": "house_small_storey",
    "k1_roof_gable_a": "house_small_roof_gable",
    "k1_roof_mansard_a": "house_small_roof_mansard",
    "k1_ground_house_b": "house_large_ground",
    "k1_storey_house_b": "house_large_storey",
    "k1_roof_gable_b": "house_large_roof_gable",
    "k1_roof_mansard_b": "house_large_roof_mansard",
    "k1_roof_turret_b_gable": "house_large_roof_turret_gable",
    "k1_roof_turret_b_tower": "house_large_roof_turret_tower",
    # K2 «Profilo» -- one cut-out figure each
    "k2_t_gradini_1": "tower_tier1",
    "k2_t_gradini_2": "tower_tier2",
    "k2_t_gradini_3": "tower_tier3",
    "k2_t_gradini_snow_l1": "tower_snow_left1",
    "k2_t_gradini_snow_r1": "tower_snow_right1",
    "k2_t_gradini_snow_l2": "tower_snow_left2",
    "k2_t_gradini_snow_r2": "tower_snow_right2",
    "k2_t_gradini_snow_top": "tower_snow_top",
    "k2_t_row5": "tower_row_tier1",
    "k2_t_row4": "tower_row_tier2",
    "k2_t_row3": "tower_row_tier3",
    "k2_t_bay": "tower_bay",
    "k2_crown_spire": "tower_crown_spire",
    "k2_crown_dome": "tower_crown_dome",
    "k2_r_padiglione": "restaurant_pavilion",
    "k2_b_insegna": "bar_signboard",
    "k2_b_smusso": "bar_chamfer",
    # Piece names (a piece is a stackable unit; its cards are the groups above). `k1_roof_turret_b`
    # is a piece whose two cards are the gable and the little tower, so it needs a name of its own
    # and longest-prefix matching keeps the three apart.
    "k1_roof_turret_b": "house_large_roof_turret",
    "k2_t_gradini": "tower_body",
}

_ORDERED = sorted(PREFIXES, key=len, reverse=True)


def production(name: str) -> str:
    """`k1_roof_gable_a_snow_fx` -> `house_small_roof_gable_snow_fx`."""
    for old in _ORDERED:
        if name == old or name.startswith(old + "_"):
            return PREFIXES[old] + name[len(old):]
    raise KeyError(f"no production name declared for {name!r}")
