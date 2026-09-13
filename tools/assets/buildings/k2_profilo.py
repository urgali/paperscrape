#!/usr/bin/env python3
"""K2 «Profilo» -- la sagoma e' il soggetto.

Ogni figura e' UNA carta (fronte piu' tetto tagliati insieme) con la sua ombra; sopra, il piano
terra e' una seconda carta piu' scura, e le finestre sono un ritmo di piccoli vetri; l'insegna e'
una targa con un emblema. Dal riferimento del maintainer: guglie, cappelli, torrette, mansarde,
gradini, cupole, tetti a farfalla, blocchi d'angolo -- due figure per famiglia (costo dichiarato),
e per la torre una figura piu' due corone. Il colore e' deciso e saturo: 4 tinte per istanza.
"""
from core import (Group, Piece, Slot, Building, W, WALL, CREAM, DARK, YELLOW, RED, rect, chamfered, disc, half_disc, scallops)
W_TIER2 = None
from vocab import (BASE_DARK, BASE_LIGHT, TRIM, ROOF_TILE, ROOF_SLATE, DOOR, GLASS,
                   wall_face, grid_windows, bay_window, door, glass_door, steps, awning, sign_plate, lantern, chimney,
                   snow_cap, dormer, row_stamp, rows, bay_glass)

H = dict(amp=0.7, relief=(1.5, 2.0))
T = dict(amp=1.1, relief=(2.4, 3.2))
R = dict(amp=0.9, relief=(1.9, 2.6))
B = dict(amp=0.75, relief=(1.5, 2.0))
HUES = [-50.0, -20.0, 15.0, 45.0]
SAT = 0.40


def hs_guglia():
    g = Group("k2_hs_guglia")
    p = Piece("k2_hs_guglia", 72)
    body = [(-23, 0), (-23, -40), (-26, -40), (0, -72), (26, -40), (23, -40), (23, 0)]
    g.face("wall", rect(-23, -40, 23, 0))
    g.add(body, WALL, relief=H["relief"], amp=H["amp"])
    roof = [(-26.5, -39.5), (0, -72.5), (26.5, -39.5), (26.5, -36.5), (0, -68), (-26.5, -36.5)]
    g.add(roof, ROOF_TILE, relief=H["relief"], amp=H["amp"] * 0.8)
    g.add(rect(-23, -22, 23, 0), BASE_DARK, amp=H["amp"] * 0.5)
    steps(g, "wall", 5.5, 19.5, 0, 1, 2.0, H["relief"], H["amp"])
    door(g, "wall", 7, -20, 11, 18, H["relief"], H["amp"], "arch")
    win, sill = bay_window(g, "wall", -18, -20, 13, 12, H["relief"], H["amp"])
    p.windows.append(win); p.lights.append(sill)
    grid_windows(g, "wall", [-17, 6], [-36], 7, 9, H["amp"])
    p.lights += [(-17, -27, 7), (6, -27, 7)]
    g.add(disc(0, -52, 3.5, 10), GLASS, amp=H["amp"] * 0.3)
    p.smoke = chimney(g, 12, -62, 5, 12, H["relief"], H["amp"])
    p.lamps.append((20.5, -13))
    p.place(g)
    p.place(snow_cap("k2_hs_guglia_snow", [[(-26, -40), (0, -72), (26, -40)]], H["amp"]))
    return p


def hs_cappello():
    g = Group("k2_hs_cappello")
    p = Piece("k2_hs_cappello", 54)
    g.face("wall", rect(-32, -32, 32, 0))
    g.add(rect(-32, -32, 32, 0), WALL, relief=H["relief"], amp=H["amp"])
    g.add([(-37, -31), (-14, -54), (14, -54), (37, -31)], ROOF_TILE, relief=H["relief"], amp=H["amp"] * 0.8)
    g.add(rect(-32, -20, 32, 0), BASE_DARK, amp=H["amp"] * 0.5)
    steps(g, "wall", -7, 7, 0, 1, 2.0, H["relief"], H["amp"])
    door(g, "wall", -5.5, -20, 11, 18, H["relief"], H["amp"], "arch")
    win, sill = bay_window(g, "wall", -27, -19, 13, 12, H["relief"], H["amp"])
    p.windows.append(win); p.lights.append(sill)
    grid_windows(g, "wall", [14], [-18], 8, 10, H["amp"])
    grid_windows(g, "wall", [-24, -3.5, 17], [-30], 7, 8, H["amp"])
    p.lights += [(14, -8, 8)]
    p.smoke = chimney(g, 18, -60, 6, 14, H["relief"], H["amp"])
    p.lamps.append((9, -14))
    p.place(g)
    p.place(snow_cap("k2_hs_cappello_snow", [[(-20, -48), (-14, -54), (14, -54), (20, -48)]], H["amp"]))
    return p


def hl_torretta():
    g = Group("k2_hl_torretta")
    p = Piece("k2_hl_torretta", 94)
    g.face("wall", rect(-38, -56, 32, 0))
    g.add(rect(-38, -56, 32, 0), WALL, relief=H["relief"], amp=H["amp"])
    g.add([(-42, -55), (-3, -84), (36, -55)], ROOF_TILE, relief=H["relief"], amp=H["amp"] * 0.8)
    p.smoke = chimney(g, -30, -76, 6, 12, H["relief"], H["amp"])
    g.add(rect(-38, -22, 32, 0), BASE_DARK, amp=H["amp"] * 0.5)
    for x in (-33, -12):
        win, sill = bay_window(g, "wall", x, -20, 13, 12, H["relief"], H["amp"])
        p.windows.append(win); p.lights.append(sill)
    rows(p, g, "wall", row_stamp("k2_hl_torretta_row3", 3, 8, 10, 19, H["amp"]), 3, 8, 10, 19, -32, [-48])
    p.lights += [(-32, -38, 8), (-12, -38, 8), (6, -38, 8)]
    p.lamps.append((18, -14))
    p.place(g)
    t = Group("k2_hl_torretta_turret")
    turret = rect(20, -72, 40, 0)
    t.face("turret", turret)
    t.add(turret, WALL, relief=H["relief"], amp=H["amp"])
    t.add([(16, -71), (30, -94), (44, -71)], ROOF_SLATE, relief=H["relief"], amp=H["amp"] * 0.8)
    t.add(rect(20, -22, 40, 0), BASE_DARK, amp=H["amp"] * 0.5)
    steps(t, "turret", 23, 36, 0, 1, 2.0, H["relief"], H["amp"])
    door(t, "turret", 24.5, -20, 11, 18, H["relief"], H["amp"], "arch")
    grid_windows(t, "turret", [27.5], [-64, -46, -30], 5, 7, H["amp"])
    p.place(t)
    p.place(snow_cap("k2_hl_torretta_snow_gable", [[(-42, -55), (-3, -84), (36, -55)]], H["amp"], cover=0.6))
    p.place(snow_cap("k2_hl_torretta_snow_cone", [[(16, -71), (30, -94), (44, -71)]], H["amp"], cover=0.6))
    return p


def hl_mansarda():
    g = Group("k2_hl_mansarda")
    p = Piece("k2_hl_mansarda", 82)
    g.face("wall", rect(-43, -52, 43, 0))
    g.add(rect(-43, -52, 43, 0), WALL, relief=H["relief"], amp=H["amp"])
    slope = [(-46, -51), (-37, -74), (37, -74), (46, -51)]
    g.face("slope", slope)
    g.add(slope, ROOF_TILE, relief=H["relief"], amp=H["amp"] * 0.8)
    g.add([(-38, -74), (-32, -82), (32, -82), (38, -74)], ROOF_TILE, relief=H["relief"], amp=H["amp"] * 0.8)
    for cx in (-18, 16):
        dormer(g, "slope", cx, -56, 9, 8, H["relief"], H["amp"])
    p.smoke = chimney(g, 22, -90, 5, 14, H["relief"], H["amp"])
    g.add(rect(-43, -22, 43, 0), BASE_DARK, amp=H["amp"] * 0.5)
    steps(g, "wall", 24.5, 38.5, 0, 1, 2.0, H["relief"], H["amp"])
    door(g, "wall", 26, -21, 12, 19, H["relief"], H["amp"], "arch")
    for x in (-38, -14):
        win, sill = bay_window(g, "wall", x, -20, 13, 12, H["relief"], H["amp"])
        p.windows.append(win); p.lights.append(sill)
    rows(p, g, "wall", row_stamp("k2_hl_mansarda_row4", 4, 8, 10, 20, H["amp"]), 4, 8, 10, 20, -36, [-44])
    p.lights += [(-36, -34, 8), (-16, -34, 8), (4, -34, 8), (24, -34, 8)]
    p.lamps.append((25, -14))
    p.place(g)
    p.place(snow_cap("k2_hl_mansarda_snow", [[(-32, -82), (32, -82)]], H["amp"]))
    return p


def tower_body():
    """Tre livelli a gradini: tre carte (una per livello, ognuna ritagliata sul suo riquadro: un
    foglio unico era per il 45 % vuoto), le finestre a righe timbrate, gli affacci col vetro timbrato."""
    p = Piece("k2_t_gradini", 176)
    t1, t2, t3 = rect(-33, -108, 33, 0), rect(-26, -148, 26, -108), rect(-18, -176, 18, -148)
    g = Group("k2_t_gradini_1")
    g.face("t1", t1)
    g.add(t1, WALL, relief=T["relief"], amp=T["amp"])
    g.add(rect(-33, -26, 33, 0), BASE_LIGHT, amp=T["amp"] * 0.5)
    g.add(chamfered(-16, -27.5, 16, -24, 0.8), TRIM, relief=T["relief"], amp=T["amp"] * 0.6, host="t1", label="pensilina")
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


def r_padiglione():
    g = Group("k2_r_padiglione")
    p = Piece("k2_r_padiglione", 58)
    g.face("wall", rect(-50, -40, 50, 0))
    g.add(rect(-50, -40, 50, 0), WALL, relief=R["relief"], amp=R["amp"])
    g.face("coping", rect(-51, -43, 51, -39.5))
    g.add(chamfered(-51, -43, 51, -39.5, 0.6), TRIM, relief=R["relief"], amp=R["amp"] * 0.6)
    g.add(half_disc(0, -42, 14, 12), CREAM, relief=R["relief"], amp=R["amp"] * 0.6, host="coping", rests=True, label="insegna a cupola", margin=1.0)
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


def r_farfalla():
    g = Group("k2_r_farfalla")
    p = Piece("k2_r_farfalla", 78)
    g.face("wall", rect(-48, -42, 48, 0))
    g.add(rect(-48, -42, 48, 0), WALL, relief=R["relief"], amp=R["amp"])
    roof = [(-50, -60), (-50, -41), (50, -41), (50, -60), (0, -46)]
    g.face("roof", roof)
    g.add(roof, ROOF_SLATE, relief=R["relief"], amp=R["amp"] * 0.8)
    sign_plate(g, "roof", -44, -78, -24, -58, R["relief"], R["amp"], "disc", rests=True)
    for x in (-40, -14):
        win, _ = bay_window(g, "wall", x, -28, 20, 15, R["relief"], R["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights += [(-40, -11.5, 20), (-14, -11.5, 20)]
    g.add(chamfered(17, -27, 35, -24, 0.6), TRIM, relief=R["relief"], amp=R["amp"] * 0.6, host="wall", label="pensilina")
    steps(g, "wall", 18.5, 33.5, 0, 1, 2.0, R["relief"], R["amp"])
    door(g, "wall", 20, -22, 12, 20, R["relief"], R["amp"], "flat")
    grid_windows(g, "wall", [38], [-28], 8, 10, R["amp"])
    p.lamps.append((16, -26))
    p.place(g)
    p.place(snow_cap("k2_r_farfalla_snow", [[(-50, -60), (-25, -53)], [(25, -53), (50, -60)], [(-44, -78), (-24, -78)]], R["amp"], body=3.5, cover=1.0))
    return p


def b_insegna():
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


def b_smusso():
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


def buildings():
    """La dotazione che sta nel budget: due figure per casa piccola e bar, una per casa grande e
    ristorante, torre = un corpo + due corone. La dotazione piena (due figure per OGNI famiglia) e'
    `buildings_full()`: costa 5 738 616 B [M], 1 475 460 B sopra il budget, e viene resa a parte
    (`--riserva`) perche' il maintainer la veda e decida cosa vale il posto."""
    return {
        "HOUSE_SMALL": Building("HOUSE_SMALL", [Slot([hs_guglia(), hs_cappello()])], 27.0, HUES, SAT),
        "HOUSE_LARGE": Building("HOUSE_LARGE", [Slot([hl_mansarda()])], 40.0, HUES, SAT),
        "TOWER": Building("TOWER", [Slot([tower_body()]), Slot([crown_spire(), crown_dome()])], 35.0, HUES, SAT),
        "RESTAURANT": Building("RESTAURANT", [Slot([r_padiglione()])], 50.0, HUES, SAT),
        "BAR": Building("BAR", [Slot([b_insegna(), b_smusso()])], 33.0, HUES, SAT),
    }


def buildings_full():
    return {
        "HOUSE_SMALL": Building("HOUSE_SMALL", [Slot([hs_guglia(), hs_cappello()])], 27.0, HUES, SAT),
        "HOUSE_LARGE": Building("HOUSE_LARGE", [Slot([hl_torretta(), hl_mansarda()])], 40.0, HUES, SAT),
        "TOWER": Building("TOWER", [Slot([tower_body()]), Slot([crown_spire(), crown_dome()])], 35.0, HUES, SAT),
        "RESTAURANT": Building("RESTAURANT", [Slot([r_padiglione(), r_farfalla()])], 50.0, HUES, SAT),
        "BAR": Building("BAR", [Slot([b_insegna(), b_smusso()])], 33.0, HUES, SAT),
    }
