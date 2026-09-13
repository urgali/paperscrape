#!/usr/bin/env python3
"""K1 «Scatola» -- la grammatica: un edificio e' una PILA di pezzi scelti per istanza.

Non esiste una facciata: esistono piani terra, piani e tetti, ognuno una carta con la sua ombra,
in tre larghezze (A 60 u: casa piccola, bar, torre; B 84 u: casa grande; C 96 u: ristorante). Il
compositore sceglie per ogni slot un'alternativa e un numero di ripetizioni dall'identita'
dell'edificio, cosi' due case vicine hanno tetti, altezze e colori diversi (dal riferimento del
maintainer: sagome tutte diverse; piano terra diverso dai piani; finestre piccole in griglia; le
insegne sono targhe). I fianchi sono piatti: un pezzo sta bene isolato e affiancato.
"""
from core import (Group, Piece, Slot, Building, W, WALL, CREAM, DARK, YELLOW, rect, chamfered, disc, half_disc, scallops)
from vocab import (BASE_DARK, BASE_LIGHT, TRIM, ROOF_TILE, ROOF_SLATE, DOOR, GLASS, CHIMNEY,
                   wall_face, grid_windows, bay_window, door, glass_door, steps, awning, sign_plate, lantern, chimney,
                   snow_cap, dormer)

H = dict(amp=0.7, relief=(1.5, 2.0))       # case (e pezzi A/B condivisi con bar)
T = dict(amp=1.1, relief=(2.4, 3.2))       # torre
R = dict(amp=0.9, relief=(1.9, 2.6))       # ristorante (C)
HUES = [-35.0, -12.0, 0.0, 18.0, 40.0]
SAT = 0.30


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


def ground_shop_C():
    g = Group("k1_ground_shop_c")
    p = Piece("k1_ground_shop_c", 32)
    wall_face(g, "wall", rect(-48, -32, 48, 0), BASE_DARK, relief=R["relief"], amp=R["amp"])
    sign_plate(g, "wall", -16, -30, 16, -24, R["relief"], R["amp"], "disc")
    awning(g, "wall", -45, 18, -23.5, 5.0, R["relief"], R["amp"], n=7)
    for x in (-42, -22, -2):
        win, _ = bay_window(g, "wall", x, -19.5, 16, 14, R["relief"], R["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights.append((-42, -4.5, 16)); p.lights.append((-2, -4.5, 16))
    steps(g, "wall", 22.5, 37.5, 0, 1, 2.0, R["relief"], R["amp"])
    door(g, "wall", 24, -22, 12, 20, R["relief"], R["amp"], "flat")
    p.lamps.append((41, -20))
    p.place(g)
    return p


def ground_bar_A():
    g = Group("k1_ground_bar_a")
    p = Piece("k1_ground_bar_a", 32)
    wall_face(g, "wall", rect(-30, -32, 30, 0), BASE_DARK, relief=H["relief"], amp=H["amp"])
    steps(g, "wall", -26.5, -11.5, 0, 1, 2.0, H["relief"], H["amp"])
    door(g, "wall", -25, -22, 12, 20, H["relief"], H["amp"], "flat")
    lantern(g, "wall", -19, -22.5, H["relief"], H["amp"])
    for x in (-6, 11):
        win, sill = bay_window(g, "wall", x, -20, 13, 12, H["relief"], H["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights.append((-6, -6.5, 30))
    sign_plate(g, "wall", -8, -30, 26, -23.5, H["relief"], H["amp"], "mug")
    p.lamps.append((-19, -27))
    p.place(g)
    return p


def ground_tower_A():
    g = Group("k1_ground_tower_a")
    p = Piece("k1_ground_tower_a", 30)
    wall_face(g, "wall", rect(-30, -30, 30, 0), BASE_LIGHT, relief=T["relief"], amp=T["amp"])
    g.add(chamfered(-17, -27.5, 17, -24, 0.8), TRIM, relief=T["relief"], amp=T["amp"] * 0.6, host="wall", label="pensilina")
    glass_door(g, "wall", -9, -23, 18, 21, T["relief"], T["amp"])
    grid_windows(g, "wall", [-25, 19], [-21], 6, 8, T["amp"])
    p.lamps.append((-14.5, -26.5))
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


def storey_C():
    g = Group("k1_storey_c")
    p = Piece("k1_storey_c", 22)
    wall_face(g, "wall", rect(-48, -22, 48, 0), WALL, relief=R["relief"], amp=R["amp"])
    win, sill = bay_window(g, "wall", -40, -18, 13, 12, R["relief"], R["amp"])
    p.windows.append(win); p.lights.append(sill)
    grid_windows(g, "wall", [-16, 0, 24], [-17], 8, 10, R["amp"])
    p.lights += [(-16, -7, 8), (0, -7, 8), (24, -7, 8)]
    p.place(g)
    return p


def storey_tower_A():
    g = Group("k1_storey_tower_a")
    p = Piece("k1_storey_tower_a", 14)
    wall_face(g, "wall", rect(-30, -14, 30, 0), WALL, relief=T["relief"], amp=T["amp"])
    grid_windows(g, "wall", [-24, -11, 5, 18], [-11], 6, 7, T["amp"])
    p.lights += [(-24, -4, 19), (5, -4, 19)]
    p.place(g)
    return p


def storey_bay_A():
    g = Group("k1_storey_bay_a")
    p = Piece("k1_storey_bay_a", 22)
    wall_face(g, "wall", rect(-30, -22, 30, 0), WALL, relief=H["relief"], amp=H["amp"])
    g.add(chamfered(-26, -5.5, 26, -2, 0.6), TRIM, relief=H["relief"], amp=H["amp"] * 0.6, host="wall", label="balcone")
    for x in (-22, 9):
        win, _ = bay_window(g, "wall", x, -18.5, 13, 12, H["relief"], H["amp"], sill=False)
        p.windows.append(win); p.lights.append((x, -6.5, 13))
    p.place(g)
    return p


# --- tetti ---------------------------------------------------------------------------------------
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


def roof_flat_A():
    g = Group("k1_roof_flat_a")
    p = Piece("k1_roof_flat_a", 12)
    g.face("parapet", rect(-31, -12, 31, 0))
    g.add(rect(-31, -9, 31, 0), WALL, relief=T["relief"], amp=T["amp"])
    g.add(chamfered(-32, -12, 32, -8, 0.6), TRIM, relief=T["relief"], amp=T["amp"] * 0.6)
    g.add(chamfered(-9, -20, 9, -12, 0.8, top_only=True), W(0.85, DARK), relief=T["relief"], amp=T["amp"])
    p.beacon = (0, -22)
    p.place(g)
    p.place(snow_cap("k1_roof_flat_a_snow", [[(-32, -12), (32, -12)], [(-9, -20), (9, -20)]], T["amp"], body=3.5))
    return p


def roof_stepped_A():
    g = Group("k1_roof_stepped_a")
    p = Piece("k1_roof_stepped_a", 26)
    g.add(rect(-31, -8, 31, 0), WALL, relief=T["relief"], amp=T["amp"])
    g.add(rect(-22, -16, 22, -8), W(0.9, CREAM), relief=T["relief"], amp=T["amp"])
    g.add(rect(-12, -26, 12, -16), WALL, relief=T["relief"], amp=T["amp"])
    g.add(rect(-0.7, -31, 0.7, -26), DARK)
    p.beacon = (0, -32)
    p.place(g)
    p.place(snow_cap("k1_roof_stepped_a_snow", [[(-31, -8), (-22, -8)], [(22, -8), (31, -8)], [(-22, -16), (-12, -16)], [(12, -16), (22, -16)], [(-12, -26), (12, -26)]], T["amp"], body=3.5))
    return p


def roof_dome_A():
    g = Group("k1_roof_dome_a")
    p = Piece("k1_roof_dome_a", 30)
    g.add(rect(-19, -9, 19, 0), WALL, relief=T["relief"], amp=T["amp"])
    g.add(half_disc(0, -9, 17, 14), ROOF_SLATE, relief=T["relief"], amp=T["amp"])
    g.add(rect(-0.8, -31, 0.8, -25), DARK)
    g.add(disc(0, -31.5, 1.6, 8), YELLOW)
    p.beacon = (0, -34)
    p.place(g)
    p.place(snow_cap("k1_roof_dome_a_snow", [[(-10, -22.5), (0, -26), (10, -22.5)]], T["amp"], body=4.0, cover=1.0))
    return p


def roof_turret_B():
    """Tetto a falda con la torretta: due gruppi (falda, torretta) ritagliati ognuno sul suo riquadro,
    e due calotte di neve: un solo foglio sarebbe stato per il 40 % vuoto (misurato: 190 KB -> 140)."""
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


def roof_flat_C():
    g = Group("k1_roof_flat_c")
    p = Piece("k1_roof_flat_c", 12)
    g.add(rect(-49, -9, 49, 0), WALL, relief=R["relief"], amp=R["amp"])
    cornice = [(-50, -13), (50, -13)] + scallops(50, -50, -9, 2.5, 10)[1:]
    g.add(cornice, TRIM, relief=R["relief"], amp=R["amp"] * 0.6)
    g.add(chamfered(-6, -18, 6, -13, 0.6, top_only=True), W(0.85, DARK), relief=R["relief"], amp=R["amp"])
    p.place(g)
    p.place(snow_cap("k1_roof_flat_c_snow", [[(-50, -13), (50, -13)]], R["amp"], body=3.5))
    return p


def pieces():
    """Tutti i pezzi per nome (fase 4: il mix pesca solo quelli delle case)."""
    gh_a, gh_b, gs_c, gb_a, gt_a = ground_house_A(), ground_house_B(), ground_shop_C(), ground_bar_A(), ground_tower_A()
    sh_a, sh_b, s_c, st_a, sb_a = storey_house_A(), storey_house_B(), storey_C(), storey_tower_A(), storey_bay_A()
    gable_a = roof_gable("k1_roof_gable_a", 34, 30, H, ROOF_TILE, chim_x=13)
    mans_a = roof_mansard("k1_roof_mansard_a", 33, H, ROOF_TILE, [-8], 12)
    gable_b = roof_gable("k1_roof_gable_b", 46, 34, H, ROOF_TILE, chim_x=22, dormer_x=-16)
    mans_b = roof_mansard("k1_roof_mansard_b", 45, H, ROOF_TILE, [-20, 14], 24)
    turret_b = roof_turret_B()
    gable_c = roof_gable("k1_roof_gable_c", 52, 28, R, ROOF_SLATE, chim_x=None)
    flat_a, step_a, dome_a, flat_c = roof_flat_A(), roof_stepped_A(), roof_dome_A(), roof_flat_C()
    return dict(gh_a=gh_a, gh_b=gh_b, gs_c=gs_c, gb_a=gb_a, gt_a=gt_a, sh_a=sh_a, sh_b=sh_b, s_c=s_c, st_a=st_a, sb_a=sb_a,
                roof_gable_a=gable_a, roof_mansard_a=mans_a, roof_gable_b=gable_b, roof_mansard_b=mans_b, roof_turret_b=turret_b,
                roof_gable_c=gable_c, roof_flat_a=flat_a, roof_stepped_a=step_a, roof_dome_a=dome_a, roof_flat_c=flat_c)


def buildings():
    k = pieces()
    gh_a, gh_b, gs_c, gb_a, gt_a, sh_a, sh_b, s_c, st_a, sb_a = (k[n] for n in ("gh_a", "gh_b", "gs_c", "gb_a", "gt_a", "sh_a", "sh_b", "s_c", "st_a", "sb_a"))
    gable_a, mans_a, gable_b, mans_b, turret_b, gable_c, flat_a, step_a, dome_a, flat_c = (k[n] for n in ("roof_gable_a", "roof_mansard_a", "roof_gable_b", "roof_mansard_b", "roof_turret_b", "roof_gable_c", "roof_flat_a", "roof_stepped_a", "roof_dome_a", "roof_flat_c"))
    return {
        "HOUSE_SMALL": Building("HOUSE_SMALL", [Slot([gh_a]), Slot([sh_a], 0, 1), Slot([gable_a, mans_a])], 30.0, HUES, SAT),
        "HOUSE_LARGE": Building("HOUSE_LARGE", [Slot([gh_b]), Slot([sh_b], 1, 2), Slot([gable_b, mans_b, turret_b])], 42.0, HUES, SAT),
        "TOWER": Building("TOWER", [Slot([gt_a]), Slot([st_a], 3, 4), Slot([sb_a]), Slot([st_a], 2, 4), Slot([flat_a, step_a, dome_a])], 34.0, HUES, SAT),
        "RESTAURANT": Building("RESTAURANT", [Slot([gs_c]), Slot([s_c], 1, 2), Slot([flat_c, gable_c])], 50.0, HUES, SAT),
        "BAR": Building("BAR", [Slot([gb_a]), Slot([sh_a], 0, 1), Slot([sb_a]), Slot([flat_a, gable_a])], 32.0, HUES, SAT),
    }
