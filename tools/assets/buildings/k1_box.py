#!/usr/bin/env python3
"""K1 «Box» -- the grammar: a building is a STACK of pieces chosen per instance.

There is no facade: there are ground floors, storeys and roofs, each a card with its shadow,
in two widths (A 60 u: small house; B 84 u: large house). For
each slot the composer picks an alternative and a number of repeats from the building's
identity, so two neighbouring houses have different roofs, heights and colours (from the
maintainer's reference: silhouettes all different; ground floor different from the storeys;
small windows in a grid; the signs are plaques). The flanks are flat: a piece looks right both
standing alone and side by side.

The concept also drew A-width pieces for the bar and the tower and a C width (96 u) for the
restaurant: eleven pieces that the shipped mix never took (`build_neighbourhood.families()` takes
the houses' nine), and that v5.9F removed (inventory row I-33). The neighbourhood regenerates
byte for byte without them.
"""
from core import (Group, Piece, W, WALL, DARK, rect)
from vocab import (ROOF_TILE, ROOF_SLATE, wall_face, grid_windows, bay_window, door, steps, chimney,
                   snow_cap, dormer)

H = dict(amp=0.7, relief=(1.5, 2.0))       # houses


def ground_house_A():
    g = Group("k1_ground_house_a")
    p = Piece("k1_ground_house_a", 28)
    wall_face(g, "wall", rect(-30, -28, 30, 0), W(0.78, DARK), relief=H["relief"], amp=H["amp"])
    steps(g, "wall", 6.5, 21.5, 0, 1, 2.0, H["relief"], H["amp"])
    door(g, "wall", 8, -21, 12, 19, H["relief"], H["amp"], "arch")
    win, sill = bay_window(g, "wall", -23, -22, 14, 13, H["relief"], H["amp"])
    p.windows.append(win); p.lights.append(sill); p.lamps.append((24.5, -15))
    p.place(g)
    return p


def ground_house_B():
    g = Group("k1_ground_house_b")
    p = Piece("k1_ground_house_b", 28)
    wall_face(g, "wall", rect(-42, -28, 42, 0), W(0.78, DARK), relief=H["relief"], amp=H["amp"])
    steps(g, "wall", 22.5, 37.5, 0, 1, 2.0, H["relief"], H["amp"])
    door(g, "wall", 24, -21, 12, 19, H["relief"], H["amp"], "arch")
    for x in (-36, -10):
        win, sill = bay_window(g, "wall", x, -22, 14, 13, H["relief"], H["amp"])
        p.windows.append(win); p.lights.append(sill)
    p.lamps.append((39.5, -15))
    p.place(g)
    return p


def storey_house_A():
    g = Group("k1_storey_house_a")
    p = Piece("k1_storey_house_a", 22)
    wall_face(g, "wall", rect(-30, -22, 30, 0), WALL, relief=H["relief"], amp=H["amp"])
    win, sill = bay_window(g, "wall", -22, -18, 13, 12, H["relief"], H["amp"])
    p.windows.append(win); p.lights.append(sill)
    grid_windows(g, "wall", [12], [-17], 8, 10, H["amp"])
    p.lights.append((12, -7, 8))
    p.place(g)
    return p


def storey_house_B():
    g = Group("k1_storey_house_b")
    p = Piece("k1_storey_house_b", 22)
    wall_face(g, "wall", rect(-42, -22, 42, 0), WALL, relief=H["relief"], amp=H["amp"])
    win, sill = bay_window(g, "wall", -34, -18, 13, 12, H["relief"], H["amp"])
    p.windows.append(win); p.lights.append(sill)
    grid_windows(g, "wall", [-6, 10, 26], [-17], 8, 10, H["amp"])
    p.lights += [(-6, -7, 8), (10, -7, 8), (26, -7, 8)]
    p.place(g)
    return p


# --- roofs ---------------------------------------------------------------------------------------
def roof_gable(name, half, h, params, fill, chim_x=None, dormer_x=None):
    g = Group(name)
    p = Piece(name, h)
    tri = [(-half, 0), (0, -h), (half, 0)]
    g.face("slope", tri)
    g.add(tri, fill, relief=params["relief"], amp=params["amp"])
    if dormer_x is not None:
        dormer(g, "slope", dormer_x, -10, 10, 9, params["relief"], params["amp"], roof=fill)
    if chim_x is not None:
        ys = -h * (1 - abs(chim_x + 3) / half)
        p.smoke = chimney(g, chim_x, ys - 8, 6, 10, params["relief"], params["amp"])
    p.place(g)
    p.place(snow_cap(f"{name}_snow", [tri], params["amp"]))
    return p


def roof_mansard(name, half, params, fill, dormers, chim_x):
    g = Group(name)
    p = Piece(name, 26 if half < 40 else 30)
    lo, cap = (-18, -26) if half < 40 else (-20, -30)
    slope = [(-half, 0), (-half + 8, lo), (half - 8, lo), (half, 0)]
    top = [(-half + 7, lo), (-half + 12, cap), (half - 12, cap), (half - 7, lo)]
    g.face("slope", slope)
    g.add(slope, fill, relief=params["relief"], amp=params["amp"])
    g.add(top, fill, relief=params["relief"], amp=params["amp"])
    for dx in dormers:
        dormer(g, "slope", dx, -4, 9, 8, params["relief"], params["amp"], roof=fill)
    p.smoke = chimney(g, chim_x, cap - 5, 5, 5 - cap + lo, params["relief"], params["amp"])
    p.place(g)
    p.place(snow_cap(f"{name}_snow", [[(-half + 12, cap), (half - 12, cap)]], params["amp"]))
    return p


def roof_turret_B():
    """Pitched roof with the turret: two groups (slope, turret), each cropped to its own box, and
    two snow caps: a single sheet would have been 40 % empty (measured: 190 KB -> 140)."""
    p = Piece("k1_roof_turret_b", 44)
    g = Group("k1_roof_turret_b_gable")
    tri = [(-46, 0), (-8, -30), (30, 0)]
    g.face("slope", tri)
    g.add(tri, ROOF_TILE, relief=H["relief"], amp=H["amp"])
    p.smoke = chimney(g, -34, -20, 6, 10, H["relief"], H["amp"])
    p.place(g)
    t = Group("k1_roof_turret_b_tower")
    turret = rect(22, -36, 40, 0)
    t.face("turret", turret)
    t.add(turret, WALL, relief=H["relief"], amp=H["amp"])
    cone = [(18, -36), (31, -54), (44, -36)]
    t.add(cone, ROOF_SLATE, relief=H["relief"], amp=H["amp"])
    grid_windows(t, "turret", [28.5], [-30, -18], 5, 7, H["amp"])
    p.lights += [(28.5, -11, 5)]
    p.place(t)
    p.place(snow_cap("k1_roof_turret_b_gable_snow", [tri], H["amp"], cover=0.6))
    p.place(snow_cap("k1_roof_turret_b_tower_snow", [cone], H["amp"], cover=0.6))
    return p


def pieces():
    """The houses' pieces by name: all nine are the ones the mix takes (phase 4)."""
    gh_a, gh_b = ground_house_A(), ground_house_B()
    sh_a, sh_b = storey_house_A(), storey_house_B()
    gable_a = roof_gable("k1_roof_gable_a", 34, 30, H, ROOF_TILE, chim_x=13)
    mans_a = roof_mansard("k1_roof_mansard_a", 33, H, ROOF_TILE, [-8], 12)
    gable_b = roof_gable("k1_roof_gable_b", 46, 34, H, ROOF_TILE, chim_x=22, dormer_x=-16)
    mans_b = roof_mansard("k1_roof_mansard_b", 45, H, ROOF_TILE, [-20, 14], 24)
    turret_b = roof_turret_B()
    return dict(gh_a=gh_a, gh_b=gh_b, sh_a=sh_a, sh_b=sh_b,
                roof_gable_a=gable_a, roof_mansard_a=mans_a, roof_gable_b=gable_b, roof_mansard_b=mans_b, roof_turret_b=turret_b)
