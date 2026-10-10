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
                   wall_face, bay_window, door, glass_door, steps, awning, sign_plate, lantern,
                   snow_cap, row_stamp, rows, bay_glass)

T = dict(amp=1.1, relief=(2.4, 3.2))
R = dict(amp=0.9, relief=(1.9, 2.6))
B = dict(amp=0.75, relief=(1.5, 2.0))

#: The left x of the two windows upstairs on each bar (v5.12): 12 x 11 with frame and sill on the bar
#: with the signboard, 11 x 11 framed like the ground floor on the corner bar. On the bar with the
#: signboard the first is the window a person has always stood at; the corner bar had nobody upstairs
#: until v5.12.
B_SIGNBOARD_UPSTAIRS = (8, -12)
B_CHAMFER_UPSTAIRS = (-10, 6)


#: The towers' three heights (v5.11, inventory I-402; the maintainer's choice B of 2026-10-06): a
#: short body of two tiers, the three-tier body every tower had until v5.11 (a storey taller since, with
#: the hall), and a tall one. Each is the same
#: stepped figure grown or shrunk by whole window rows -- 12 units, one storey of the row stamps --
#: so the three read as one family and every row, bay and snow cap sits on the same grid.
#: `tier1_rows` and `tier2_rows` are rows added to (or, below zero, taken from) the first and second
#: tiers; `tier3` is whether the third, narrowest tier stands on top. The engine gives each tower one
#: of the three by a coin of its own, so the crowns the deal hands out are the ones they were.
TOWER_SHAPES = {
    "short": dict(tier1_rows=-1, tier2_rows=0, tier3=False),
    "mid": dict(tier1_rows=0, tier2_rows=0, tier3=True),
    "tall": dict(tier1_rows=2, tier2_rows=1, tier3=True),
}


def tower_body(shape="mid"):
    """Three stepped tiers -- two on the short body -- as one card per tier (each cropped to its
    own box: a single sheet was 45 % empty), the windows in stamped rows, the bays with stamped
    glass. `shape` is a key of [TOWER_SHAPES].

    **The entrance storey is where the people are** (v5.11, inventory I-406: the maintainer's B2 of
    2026-10-06, *«ok B2 ma ci devono essere due finestre con persone anche accanto alla porta»*). The
    hall is a storey taller than it was -- 44 units, which is what grows every body by one row of 12 --
    with a row of four large windows over the canopy and, beside the door, two more where two small
    ones were: the six windows a bust stands in. Everything above is small windows, the second tier's
    bay included. Until v5.11 the people's windows were two at mid-height and one on the second tier,
    among the small ones, at the towers' own low rate, so a tower rarely showed anyone.

    A card's group name is its wobble seed and its PNG's name, so a tier keeps one name where it is
    one card (tier 3 on the mid and the tall body; tier 2 on the short and the mid; every snow cap but
    the short body's wide one) and takes a suffix where it is not."""
    k = TOWER_SHAPES[shape]
    hall_top = -44
    h1 = 108 + 12 + 12 * k["tier1_rows"]
    h2 = 40 + 12 * k["tier2_rows"]
    top1, top2 = -h1, -h1 - h2
    top3 = top2 - 28
    total = -top3 if k["tier3"] else -top2
    suffix = "" if shape == "mid" else "_" + shape
    p = Piece("k2_t_gradini" + suffix, total)
    t1, t2, t3 = rect(-33, top1, 33, 0), rect(-26, top2, 26, top1), rect(-18, top3, 18, top2)
    g = Group("k2_t_gradini_1" + suffix)
    g.face("t1", t1)
    g.add(t1, WALL, relief=T["relief"], amp=T["amp"])
    g.add(rect(-33, hall_top, 33, 0), BASE_LIGHT, amp=T["amp"] * 0.5)
    g.add(chamfered(-16, -27.5, 16, -24, 0.8), TRIM, relief=T["relief"], amp=T["amp"] * 0.6, host="t1", label="canopy")
    glass_door(g, "t1", -8, -23, 16, 21, T["relief"], T["amp"])
    # The two beside the door, the size of a bust's pane.
    door_bay = bay_glass("k2_t_bay_door", 12, 12, T["amp"])
    for x in (-30, 18):
        win, _ = bay_window(g, "t1", x, -22, 12, 12, T["relief"], T["amp"], frame=1.5, sill=False, glass=False)
        p.windows.append(win); p.stamp(door_bay, x, -22)
    # The four over the canopy.
    bay = bay_glass("k2_t_bay", 12, 11, T["amp"])
    for x in (-30, -14, 2, 18):
        win, _ = bay_window(g, "t1", x, -40, 12, 11, T["relief"], T["amp"], frame=1.5, sill=False, glass=False)
        p.windows.append(win); p.stamp(bay, x, -40)
    # Small windows up the rest of the first tier, every 12 units from the top down to the hall.
    ys = list(range(top1 + 8, hall_top - 7, 12))
    rows(p, g, "t1", row_stamp("k2_t_row5", 5, 6, 7, 12, T["amp"]), 5, 6, 7, 12, -27, ys)
    # Christmas strings under the lowest and the highest row of the first tier.
    p.lights += [(-27, ys[-1] + 7, 18), (9, ys[-1] + 7, 18), (-27, ys[0] + 7, 18), (9, ys[0] + 7, 18)]
    p.lamps.append((-13, -26.5))
    p.place(g)
    g2 = Group("k2_t_gradini_2" + ("_tall" if k["tier2_rows"] else ""))
    g2.face("t2", t2)
    g2.add(t2, W_TIER2 or W(0.92, CREAM), relief=T["relief"], amp=T["amp"])
    ys2 = [top1 - 10 - 12 * r for r in range(3 + k["tier2_rows"])][::-1]
    rows(p, g2, "t2", row_stamp("k2_t_row4", 4, 6, 7, 12, T["amp"]), 4, 6, 7, 12, -22, ys2)
    p.place(g2)
    if k["tier3"]:
        g3 = Group("k2_t_gradini_3")
        g3.face("t3", t3)
        g3.add(t3, WALL, relief=T["relief"], amp=T["amp"])
        rows(p, g3, "t3", row_stamp("k2_t_row3", 3, 6, 7, 11, T["amp"]), 3, 6, 7, 11, -14, [top2 - 22, top2 - 10])
        p.place(g3)
    p.place(snow_cap("k2_t_gradini_snow_l1", [[(-33, top1), (-26, top1)]], T["amp"], body=3.5))
    p.place(snow_cap("k2_t_gradini_snow_r1", [[(26, top1), (33, top1)]], T["amp"], body=3.5))
    if k["tier3"]:
        p.place(snow_cap("k2_t_gradini_snow_l2", [[(-26, top2), (-18, top2)]], T["amp"], body=3.5))
        p.place(snow_cap("k2_t_gradini_snow_r2", [[(18, top2), (26, top2)]], T["amp"], body=3.5))
        p.place(snow_cap("k2_t_gradini_snow_top", [[(-18, top3), (18, top3)]], T["amp"], body=3.5))
    else:
        p.place(snow_cap("k2_t_gradini_snow_top_wide", [[(-26, top2), (26, top2)]], T["amp"], body=3.5))
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
    # Upstairs (v5.12, inventory I-505 and I-509; version A of the proposals): two windows the same,
    # the bay a person has always stood at (x 8) and its twin where a bare small one was. Both stand
    # right of the lantern over the door (x -22.2..-17.8): the small window of before sat on it, and
    # the two read as one tall shape.
    for x in B_SIGNBOARD_UPSTAIRS:
        win, sill = bay_window(g, "wall", x, -40, 12, 11, B["relief"], B["amp"])
        p.windows.append(win)
        p.lights.append(sill)
    # A person may stand at any of the four; the bar brings the people its three windows did.
    p.people = 3
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
    # Upstairs (v5.12, inventory I-505 and I-508; version A of the proposals): two windows the same,
    # framed like the ground floor's, both right of the plaque (x -32..-14). Of the three bare small
    # windows of before, the first was drawn over the lower half of the plaque, red bar and all.
    for x in B_CHAMFER_UPSTAIRS:
        win, _ = bay_window(g, "wall", x, -41, 11, 11, B["relief"], B["amp"], frame=1.5, sill=False)
        p.windows.append(win)
        p.lights.append((x, -41 + 11 + 1.5, 11))
    # A person may stand at any of the five; the bar brings the people its three windows below did.
    p.people = 3
    p.lamps.append((20, -27))
    p.place(g)
    p.place(snow_cap("k2_b_smusso_snow", [[(-36, -52), (20, -52)], [(21, -51), (35, -37)]], B["amp"], body=3.5))
    return p
