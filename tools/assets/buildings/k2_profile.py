#!/usr/bin/env python3
"""K2 «Profile» -- the silhouette is the subject.

Each figure is ONE card (front plus roof cut out together) with its shadow; on top of it, the
ground floor is a second, darker card, and the windows are a rhythm of small panes; the sign is
a plaque with an emblem. What ships from this concept is the tower (the stepped body and its two
crowns, spire and dome), the restaurant's pavilion and the bar's two figures, which
build_neighbourhood.py picks and colours. The concept's other figures -- the small house's spire
and hat, the large house's turret and mansard, the butterfly-roof restaurant -- and the two sets
that grouped them for the concept sheets were drawn by nothing once the neighbourhood shipped,
and were removed in v5.9C (inventory row I-22).
"""
from core import (Group, Piece, W, WALL, CREAM, DARK, YELLOW, RED, rect, chamfered, disc, half_disc)
W_TIER2 = None
from vocab import (BASE_DARK, BASE_LIGHT, TRIM, ROOF_SLATE, DOOR, GLASS,
                   wall_face, grid_windows, bay_window, door, glass_door, steps, awning, sign_plate, lantern,
                   snow_cap, row_stamp, rows, bay_glass)

T = dict(amp=1.1, relief=(2.4, 3.2))
R = dict(amp=0.9, relief=(1.9, 2.6))
B = dict(amp=0.75, relief=(1.5, 2.0))


def tower_body():
    """Three stepped tiers: three cards (one per tier, each cropped to its own box: a single
    sheet was 45 % empty), the windows in stamped rows, the bays with stamped glass."""
    p = Piece("k2_t_gradini", 176)
    t1, t2, t3 = rect(-33, -108, 33, 0), rect(-26, -148, 26, -108), rect(-18, -176, 18, -148)
    g = Group("k2_t_gradini_1")
    g.face("t1", t1)
    g.add(t1, WALL, relief=T["relief"], amp=T["amp"])
    g.add(rect(-33, -26, 33, 0), BASE_LIGHT, amp=T["amp"] * 0.5)
    g.add(chamfered(-16, -27.5, 16, -24, 0.8), TRIM, relief=T["relief"], amp=T["amp"] * 0.6, host="t1", label="canopy")
    glass_door(g, "t1", -8, -23, 16, 21, T["relief"], T["amp"])
    grid_windows(g, "t1", [-27, 21], [-21], 6, 8, T["amp"])
    rows(p, g, "t1", row_stamp("k2_t_row5", 5, 6, 7, 12, T["amp"]), 5, 6, 7, 12, -27, [-100, -88, -76, -52, -40])
    bay = bay_glass("k2_t_bay", 12, 11, T["amp"])
    for x in (-27, 9):
        win, _ = bay_window(g, "t1", x, -64, 12, 11, T["relief"], T["amp"], frame=1.5, sill=False, glass=False)
        p.windows.append(win); p.stamp(bay, x, -64)
    p.lights += [(-27, -33, 18), (9, -33, 18), (-27, -93, 18), (9, -93, 18)]
    p.lamps.append((-13, -26.5))
    p.place(g)
    g2 = Group("k2_t_gradini_2")
    g2.face("t2", t2)
    g2.add(t2, W_TIER2 or W(0.92, CREAM), relief=T["relief"], amp=T["amp"])
    rows(p, g2, "t2", row_stamp("k2_t_row4", 4, 6, 7, 12, T["amp"]), 4, 6, 7, 12, -22, [-142, -118])
    win, _ = bay_window(g2, "t2", -6, -131, 12, 11, T["relief"], T["amp"], frame=1.5, sill=False, glass=False)
    p.windows.append(win); p.stamp(bay, -6, -131)
    p.place(g2)
    g3 = Group("k2_t_gradini_3")
    g3.face("t3", t3)
    g3.add(t3, WALL, relief=T["relief"], amp=T["amp"])
    rows(p, g3, "t3", row_stamp("k2_t_row3", 3, 6, 7, 11, T["amp"]), 3, 6, 7, 11, -14, [-170, -158])
    p.place(g3)
    p.place(snow_cap("k2_t_gradini_snow_l1", [[(-33, -108), (-26, -108)]], T["amp"], body=3.5))
    p.place(snow_cap("k2_t_gradini_snow_r1", [[(26, -108), (33, -108)]], T["amp"], body=3.5))
    p.place(snow_cap("k2_t_gradini_snow_l2", [[(-26, -148), (-18, -148)]], T["amp"], body=3.5))
    p.place(snow_cap("k2_t_gradini_snow_r2", [[(18, -148), (26, -148)]], T["amp"], body=3.5))
    p.place(snow_cap("k2_t_gradini_snow_top", [[(-18, -176), (18, -176)]], T["amp"], body=3.5))
    return p


def crown_spire():
    g = Group("k2_crown_spire")
    p = Piece("k2_crown_spire", 20)
    g.add(rect(-9, -7, 9, 0), WALL, relief=T["relief"], amp=T["amp"])
    g.add(rect(-1, -22, 1, -7), DARK)
    g.add(rect(-3.5, -14, 3.5, -12), DARK)
    p.beacon = (0, -24)
    p.place(g)
    p.place(snow_cap("k2_crown_spire_snow", [[(-9, -7), (9, -7)]], T["amp"], body=3.0))
    return p


def crown_dome():
    g = Group("k2_crown_dome")
    p = Piece("k2_crown_dome", 18)
    g.add(rect(-15, -5, 15, 0), TRIM, relief=T["relief"], amp=T["amp"] * 0.6)
    g.add(half_disc(0, -5, 14, 12), ROOF_SLATE, relief=T["relief"], amp=T["amp"])
    g.add(rect(-0.8, -24, 0.8, -19), DARK)
    g.add(disc(0, -25, 1.6, 8), YELLOW)
    p.beacon = (0, -27)
    p.place(g)
    p.place(snow_cap("k2_crown_dome_snow", [[(-8, -16), (0, -19), (8, -16)]], T["amp"], body=3.5, cover=1.0))
    return p


def r_pavilion():
    g = Group("k2_r_padiglione")
    p = Piece("k2_r_padiglione", 58)
    g.face("wall", rect(-50, -40, 50, 0))
    g.add(rect(-50, -40, 50, 0), WALL, relief=R["relief"], amp=R["amp"])
    g.face("coping", rect(-51, -43, 51, -39.5))
    g.add(chamfered(-51, -43, 51, -39.5, 0.6), TRIM, relief=R["relief"], amp=R["amp"] * 0.6)
    g.add(half_disc(0, -42, 14, 12), CREAM, relief=R["relief"], amp=R["amp"] * 0.6, host="coping", rests=True, label="dome sign", margin=1.0)
    g.add(disc(0, -48, 4.5, 12), RED); g.add(disc(0, -48, 2.0, 8), CREAM)
    awning(g, "wall", -46, 30, -30, 6.0, R["relief"], R["amp"], n=8)
    for x in (-44, -24, -4):
        win, _ = bay_window(g, "wall", x, -26, 16, 15, R["relief"], R["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights += [(-44, -9.5, 16), (-4, -9.5, 16)]
    steps(g, "wall", 24.5, 39.5, 0, 1, 2.0, R["relief"], R["amp"])
    door(g, "wall", 26, -22, 12, 20, R["relief"], R["amp"], "flat")
    p.lamps.append((43, -20))
    p.place(g)
    p.place(snow_cap("k2_r_padiglione_snow", [[(-51, -43), (-10, -43)], [(10, -43), (51, -43)], [(-8, -51), (0, -56), (8, -51)]], R["amp"], body=3.5, cover=1.0))
    return p


def b_signboard():
    g = Group("k2_b_insegna")
    p = Piece("k2_b_insegna", 72)
    g.face("wall", rect(-32, -48, 32, 0))
    g.add(rect(-32, -48, 32, 0), WALL, relief=B["relief"], amp=B["amp"])
    g.add(chamfered(-33, -50, 33, -47, 0.6), TRIM, relief=B["relief"], amp=B["amp"] * 0.6)
    g.face("coping", rect(-33, -50, 33, -47))
    g.add(rect(8, -56, 10, -50), DARK)
    sign_plate(g, "coping", -8, -72, 26, -50, B["relief"], B["amp"], "mug", rests=True)
    g.add(rect(-32, -22, 32, 0), BASE_DARK, amp=B["amp"] * 0.5)
    steps(g, "wall", -27.5, -12.5, 0, 1, 2.0, B["relief"], B["amp"])
    door(g, "wall", -26, -22, 12, 20, B["relief"], B["amp"], "flat")
    lantern(g, "wall", -20, -22.5, B["relief"], B["amp"])
    for x in (-8, 9):
        win, _ = bay_window(g, "wall", x, -20, 13, 12, B["relief"], B["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights += [(-8, -6.5, 30)]
    win, sill = bay_window(g, "wall", 8, -40, 12, 11, B["relief"], B["amp"])
    p.windows.append(win); p.lights.append(sill)
    grid_windows(g, "wall", [-24], [-40], 8, 10, B["amp"])
    p.lights += [(-24, -30, 8)]
    p.lamps.append((-20, -27))
    p.place(g)
    p.place(snow_cap("k2_b_insegna_snow", [[(-33, -50), (-10, -50)], [(-8, -72), (26, -72)]], B["amp"], body=3.5))
    return p


def b_chamfer():
    g = Group("k2_b_smusso")
    p = Piece("k2_b_smusso", 52)
    body = [(-35, -50), (20, -50), (35, -35), (35, 0), (-35, 0)]
    g.face("wall", rect(-35, -50, 35, 0))
    g.add(body, WALL, relief=B["relief"], amp=B["amp"])
    g.add([(-36, -52), (20.5, -52), (35.5, -37), (35.5, -33.5), (19, -49), (-36, -49)], TRIM, relief=B["relief"], amp=B["amp"] * 0.6)
    sign_plate(g, "wall", -32, -44, -14, -36, B["relief"], B["amp"], "bar", colour=RED)
    g.add(rect(-35, -22, 35, 0), BASE_DARK, amp=B["amp"] * 0.5)
    for x in (-30, -13, 4):
        win, _ = bay_window(g, "wall", x, -20, 13, 12, B["relief"], B["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights += [(-30, -6.5, 30)]
    steps(g, "wall", 17.5, 32.5, 0, 1, 2.0, B["relief"], B["amp"])
    door(g, "wall", 19, -22, 12, 20, B["relief"], B["amp"], "flat")
    lantern(g, "wall", 20, -22.5, B["relief"], B["amp"])
    grid_windows(g, "wall", [-26, -8, 10], [-40], 7, 9, B["amp"])
    p.lights += [(-26, -31, 7), (-8, -31, 7), (10, -31, 7)]
    p.lamps.append((20, -27))
    p.place(g)
    p.place(snow_cap("k2_b_smusso_snow", [[(-36, -52), (20, -52)], [(21, -51), (35, -37)]], B["amp"], body=3.5))
    return p
