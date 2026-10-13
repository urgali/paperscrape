#!/usr/bin/env python3
"""The three buildings of v5.13: the shop, the fire station and the police station.

The maintainer's request of 2026-10-11: three groups of buildings -- the towers, the shops (the
restaurant, the bar and a new **shop**) and the public buildings (the school and a new **fire
station** and **police station**) -- in the style of the buildings the street already has. Each is
one cut-out figure in the language of `k2_profile.py` and `school.py`: the wall in its group's
colour (a weight on the wall mask), the glass on the glass mask, and the fixed art carrying what
says *what* the building is -- the awning's stripes and the shopping bag, the fire station's red
doors and its engine, the police station's chequered band, light bar and shield -- in colours taken
from the vehicles already in the scene (`firetruck_*`, the police car's livery and light bar). No
text anywhere (DESIGN_NOTES "No text").

The three drawings are the versions the maintainer chose from the photographed proposals: the shop
«B» (a rounded false front with the bag on a round plaque, a green-and-white striped awning, two
crates of fruit), the fire station «C (consigliata)» (one red door open on the fire engine, one
shut, the hose tower with its red lamp) and the police station «B (consigliata)» (the chequered
band under the cornice, the light bar on the roof, the shield, a garage). His words, 2026-10-11.

**Every part that says what a building is measures 2.4 units or more** -- the bag's handle and the
gap inside it, the doors' panel lines and the bars between their panes, the engine's lamps, bumper
and light bar, the light bar's base, the star's arms -- **and so does every gap that keeps two of them
apart**: the two crates, the engine's lamps from its windscreen and its bumper, the light bar's two
lamps, a garage door's panes from its panel lines. Each of the six buildings among the houses can
stand in any of the five places (v5.13; three until the v5.13B2 round), and the farthest place draws a unit
at 0.89 px or more on the BV6600 (its depth, the size table's one metre, the size roll of +-8 %): 2.4
units is 2.14 px there at the least, over the two pixels below which a detail stops reading
(`StorefrontDetailTest` holds the numbers). The window frames, the sills, the steps and the door
handles are the vocabulary every building in the street shares at every depth (`vocab.py`) and are
not counted among them.

The group names are the wobble seeds of their cards (`core.Group.add`), so renaming one redraws it;
they are English from the start and `names.py` maps each to itself.
"""
from __future__ import annotations

import math

from core import (Group, Piece, WALL, DARK, CREAM, RED, WOOD, WOOD_U, YELLOW, GREEN,
                  rect, chamfered, disc, half_disc, scallops)
import vocab
from vocab import bay_window, door, steps, snow_cap

# ---- colours taken from the scene's own vehicles (measured off the shipped PNGs) ----------------
FIRE_RED, FIRE_RED_U = "#D6362E", "#B82A24"          # firetruck_body
FIRE_GREY, FIRE_GREY_U = "#C9CDD2", "#93969C"         # firetruck_ladder
TRUCK_GLASS = "#B9D8E4"                               # firetruck_body's windscreen
POLICE_BLUE = "#2B4A73"                               # police_stripe
POLICE_CREAM = "#FBF4E6"                              # police_stripe
LIGHTBAR_RED, LIGHTBAR_BLUE = "#E4623E", "#47698F"    # police_lightbar
GARAGE_BLUE, GARAGE_BLUE_U = "#5D6E86", "#4A586C"     # the police station's garage door

#: The narrowest a part that says what a building is may be, in units (see the module's comment).
DETAIL = 2.4

#: The shop's hand: between the bar's (0.75 / 1.5, 2.0) and the restaurant's (0.9 / 1.9, 2.6).
N = dict(amp=0.8, relief=(1.6, 2.2))
#: The stations' hand: the school's (a building of the same size).
S = dict(amp=0.85, relief=(1.7, 2.3))


# ------------------------------------------------------------------ the vocabulary of these three
def striped_awning(g, host, x0, x1, y, depth, relief, amp, n, a=GREEN, b=CREAM):
    """An awning of n lobes in two papers: the restaurant's scalloped card in `a`, carrying the shadow
    paper, and every second lobe laid over it in `b`, without a shadow of its own."""
    pts = [(x0, y), (x1, y)] + scallops(x1, x0, y + depth * 0.55, depth * 0.45, n)[1:]
    g.add(pts, a, relief=relief, amp=amp * 0.5, host=host, margin=1.0, label="awning", rests=True)
    step = (x1 - x0) / n
    yb, dd = y + depth * 0.55, depth * 0.45
    for i in range(1, n, 2):
        xa, xb = x0 + i * step, x0 + (i + 1) * step
        g.add([(xa, y + 0.3), (xb, y + 0.3), (xb, yb), (xa + 0.75 * step, yb + dd * 0.85), (xa + 0.5 * step, yb + dd),
               (xa + 0.25 * step, yb + dd * 0.85), (xa, yb)], b, amp=amp * 0.3)


#: The bag's handle: its band and the gap it closes over, both [DETAIL] (inventory I-618: the band was
#: 1.8 units and the gap 1.6 in the version photographed, 1.6 to 1.9 px at the far place).
BAG_BAND = DETAIL
BAG_GAP = DETAIL


def bag(g, cx, cy, body=GREEN, handle=DARK):
    """The shop's emblem: a shopping bag -- a trapezoid with an arched handle -- centred on (cx, cy).

    The body is 7 units tall, 8.4 across its top and 9.6 across its foot; the handle a half ring on
    the middle of its top edge, [BAG_BAND] thick round a gap [BAG_GAP] across."""
    r_in = BAG_GAP / 2
    r_out = r_in + BAG_BAND
    top = cy - 1.7
    g.add(half_disc(cx, top, r_out, 10) + half_disc(cx, top, r_in, 6)[::-1], handle)
    g.add([(cx - 4.2, top), (cx + 4.2, top), (cx + 4.8, top + 7.0), (cx - 4.8, top + 7.0)], body)


def crate(g, x0, y_foot, w, h, fruit):
    """A wooden crate on the pavement with fruit heaped in it: the fruit discs 3.8 units across, the
    slat [DETAIL] high."""
    g.add(chamfered(x0, y_foot - h, x0 + w, y_foot, 0.4), WOOD, relief=(1.0, 1.4))
    g.add(rect(x0 + 0.8, y_foot - h * 0.55, x0 + w - 0.8, y_foot - h * 0.55 + DETAIL), WOOD_U)
    n = len(fruit)
    for i, c in enumerate(fruit):
        fx = x0 + w * (i + 0.5) / n
        g.add(disc(fx, y_foot - h - 0.6, 1.9, 8), c)


def flame(g, cx, cy, h):
    """The fire station's emblem: a red flame with a yellow heart (two cards, no outline). The heart is
    half the flame's width, 0.31 of `h`: 2.4 units at `h` = 7.8, [DETAIL]."""
    w = h * 0.62
    outer = [(cx, cy - h / 2), (cx + w * 0.22, cy - h * 0.18), (cx + w * 0.5, cy + h * 0.05), (cx + w * 0.42, cy + h * 0.36),
             (cx, cy + h / 2), (cx - w * 0.42, cy + h * 0.36), (cx - w * 0.5, cy + h * 0.05), (cx - w * 0.25, cy - h * 0.06),
             (cx - w * 0.12, cy - h * 0.26)]
    g.add(outer, FIRE_RED)
    hh = h * 0.5
    inner = [(cx + w * 0.02, cy - hh * 0.25), (cx + w * 0.26, cy + hh * 0.35), (cx + w * 0.2, cy + hh * 0.85),
             (cx, cy + h * 0.46), (cx - w * 0.2, cy + hh * 0.85), (cx - w * 0.24, cy + hh * 0.4)]
    g.add(inner, YELLOW)


def garage_door(g, host, x0, y_top, w, h, relief, amp, panels=3, colour=FIRE_RED, under=FIRE_RED_U):
    """A garage door: a card in `colour` with its cream frame, [DETAIL]-high panel lines and a row of
    small panes of the engine's windscreen glass, [DETAIL] of door between two of them -- fixed art,
    the identity of the building. **The panes stand where the first panel line would**, so the door
    between them and the next line is [DETAIL] or more: in the row above the first line it was under
    one unit, and pane and line read as one."""
    g.add(chamfered(x0 - 1.5, y_top - 1.5, x0 + w + 1.5, 0, 0.8, top_only=True), CREAM, relief=relief, amp=amp * 0.6,
          host=host, margin=0.8, label="garage frame", rests=True)
    g.add(rect(x0, y_top, x0 + w, 0), colour, amp=amp * 0.4)
    for i in range(2, panels + 1):
        y = y_top + h * i / (panels + 1)
        g.add(rect(x0 + 0.6, y - DETAIL / 2, x0 + w - 0.6, y + DETAIL / 2), under)
    n = max(2, int(w // 7))
    pw = (w - 2.0 - (n - 1) * DETAIL) / n
    y = y_top + h / (panels + 1) - 1.8
    for i in range(n):
        x = x0 + 1.0 + i * (pw + DETAIL)
        g.add(rect(x, y, x + pw, y + 3.6), TRUCK_GLASS)


def truck_front(g, x0, y_top, w, h):
    """An open garage door: the dark of the garage, and in it the fire engine seen from the front --
    its red cab, its windscreen inside a [DETAIL] rim of red, two lamps, a grey bumper, the red light
    bar on its roof. Fixed art, in the engine's own colours."""
    g.add(rect(x0, y_top, x0 + w, 0), DARK)
    cab_w = w * 0.78
    cx = x0 + w / 2
    cab_top = y_top + h * 0.18
    g.add(chamfered(cx - cab_w / 2, cab_top, cx + cab_w / 2, -3.0, 1.2, top_only=True), FIRE_RED)
    g.add(chamfered(cx - cab_w / 2 + DETAIL, cab_top + 2.6, cx + cab_w / 2 - DETAIL, cab_top + 2.6 + h * 0.26, 0.6),
          TRUCK_GLASS)
    # The lamps [DETAIL] clear of the windscreen above, of the bumper below and of the cab's sides.
    ly = -9.5
    g.add(disc(cx - cab_w / 2 + 4.1, ly, 1.7, 8), YELLOW)
    g.add(disc(cx + cab_w / 2 - 4.1, ly, 1.7, 8), YELLOW)
    g.add(rect(cx - cab_w / 2 - 0.5, -5.4, cx + cab_w / 2 + 0.5, -3.0), FIRE_GREY)
    g.add(rect(cx - cab_w / 2 + 1.0, -3.0, cx - cab_w / 2 + 4.0, 0), DARK)
    g.add(rect(cx + cab_w / 2 - 4.0, -3.0, cx + cab_w / 2 - 1.0, 0), DARK)
    g.add(rect(cx - 3.0, cab_top - DETAIL, cx + 3.0, cab_top), LIGHTBAR_RED)


def shield(g, cx, cy, h, body=POLICE_BLUE, star=YELLOW):
    """The police station's emblem: a blue shield with a five-pointed star whose arms are [DETAIL]
    across at their root (the inner radius `DETAIL / (2 sin 36°)`, 2.04 units -- 0.17 of `h` at `h` = 12 --
    and the outer 0.32 of `h`)."""
    w = h * 0.8
    top, bottom = cy - h / 2, cy + h / 2
    g.add([(cx - w / 2, top), (cx + w / 2, top), (cx + w / 2, cy + h * 0.08), (cx + w * 0.22, cy + h * 0.38), (cx, bottom),
           (cx - w * 0.22, cy + h * 0.38), (cx - w / 2, cy + h * 0.08)], body)
    r_in = DETAIL / (2 * math.sin(math.pi / 5))
    r_out = max(h * 0.32, r_in * 1.9)
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + r * math.cos(a), cy - h * 0.04 + r * math.sin(a)))
    g.add(pts, star)


def checker_band(g, x0, x1, y, rows=2, size=3.0, a=POLICE_BLUE, b=POLICE_CREAM):
    """The police's chequered band, two rows of 3-unit squares: one card in `b` under the whole band,
    the squares of `a` laid over it."""
    g.add(rect(x0, y, x1, y + rows * size), b, relief=(0.9, 1.2))
    n = int((x1 - x0) // size)
    for r in range(rows):
        for i in range(n):
            if (i + r) % 2 == 0:
                xa = x0 + i * size
                g.add(rect(xa, y + r * size, min(xa + size, x1), y + (r + 1) * size), a)


def light_bar(g, cx, y_base, w=16.0):
    """The police car's light bar on the station's roof: a dark base [DETAIL] high, a red lamp and a
    blue one [DETAIL] apart."""
    g.add(rect(cx - w / 2, y_base - DETAIL, cx + w / 2, y_base), DARK)
    top = y_base - DETAIL
    g.add(chamfered(cx - w / 2 + 0.8, top - 4.0, cx - DETAIL / 2, top, 0.6), LIGHTBAR_RED)
    g.add(chamfered(cx + DETAIL / 2, top - 4.0, cx + w / 2 - 0.8, top, 0.6), LIGHTBAR_BLUE)


# ------------------------------------------------------------------ the shop
def shop():
    """A shop whose front rises over its roof in a rounded false front with the bag on a round
    plaque, one wide window in three equal panes under a green-and-white striped awning, the door at
    the left, and two crates of fruit on the pavement by the window. 64 units wide and 56 tall at the
    crown: shorter than every house (the smallest, the small house without its storey under a gable, is 59
    to its ridge), as a shop is (DESIGN_NOTES
    §5, «A shop is visibly shorter than a house»)."""
    g = Group("shop")
    p = Piece("shop", 56)
    body = [(-32, 0), (-32, -42), (-14, -42), (-12, -48), (-6, -52), (0, -53), (6, -52), (12, -48), (14, -42), (32, -42), (32, 0)]
    g.face("wall", rect(-32, -42, 32, 0))
    g.face("crown", rect(-14, -53, 14, -42))
    g.add(body, WALL, relief=N["relief"], amp=N["amp"])
    g.add([(-33, -44), (-14.5, -44), (-12.5, -50), (-6.2, -54.5), (0, -55.5), (6.2, -54.5), (12.5, -50), (14.5, -44), (33, -44),
           (33, -41), (14, -41), (12, -47.5), (6, -51.5), (0, -52.5), (-6, -51.5), (-12, -47.5), (-14, -41), (-33, -41)],
          vocab.TRIM, relief=N["relief"], amp=N["amp"] * 0.5)
    # The round plaque: 7.6 units across its radius, to hold a bag whose every part is [DETAIL].
    g.add(disc(0, -45.0, 7.6, 16), CREAM, relief=N["relief"], amp=0.3)
    bag(g, 0, -45.0)
    g.add(rect(-32, -24, 32, 0), vocab.BASE_DARK, amp=N["amp"] * 0.5)
    steps(g, "wall", -29.5, -14.5, 0, 1, 2.0, N["relief"], N["amp"])
    door(g, "wall", -28, -22, 12, 20, N["relief"], N["amp"], "flat")
    for x in (-11, 3, 17):
        win, _ = bay_window(g, "wall", x, -21, 11, 13, N["relief"], N["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights.append((-11, -6.5, 39))
    striped_awning(g, "wall", -13, 30, -27, 6.0, N["relief"], N["amp"], 7)
    # Upstairs: two windows the same, as on the bars (decision 54: a floor's windows match).
    for x in (-24, 13):
        win, _ = bay_window(g, "wall", x, -38, 11, 11, N["relief"], N["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    # [DETAIL] of pavement between the two crates, which are one wood.
    crate(g, 3.6, 0.0, 11.0, 6.0, (RED, YELLOW, RED))
    crate(g, 17.0, 0.0, 11.0, 6.0, (GREEN, YELLOW, GREEN))
    # A person may stand at any of the five windows; the shop brings three windows' worth of people,
    # the shops' rate (`WindowOccupants.COMMERCIAL_RATE`) over three, as the bars do.
    p.people = 3
    p.lamps.append((-22, -27))
    p.place(g)
    p.place(snow_cap("shop_snow", [[(-33, -44), (-14.5, -44)], [(14.5, -44), (33, -44)],
                                   [(-12.5, -50), (0, -55.5), (12.5, -50)]], N["amp"], body=3.5, cover=1.0))
    return p


# ------------------------------------------------------------------ the fire station
def fire_station():
    """A block of two red garage doors, the left one open on the fire engine, with the flame on a
    plaque between them and a row of four windows above, and at its right the hose tower -- the tall
    narrow tower a fire station dries its hoses in -- with a door, two slits of glass and a red lamp on
    its cap. 96 units wide (the school is 104), 86 tall at the tower's cap and 91 at its lamp, 88 declared
    (the school's turret is 74).

    **The tower is a group of its own** (inventory I-607): drawn into one canvas with the block it made
    the canvas as tall as the tower over the block's whole width, half of it air -- 299 KB decoded for
    nothing. Two canvases, the tower's first so the block's cornice still laps over its edge as it did
    in the version photographed."""
    tower = Group("fire_station_tower")
    tower.face("tower", rect(30, -78, 46, 0))
    tower.add(rect(30, -78, 46, 0), vocab.BASE_LIGHT, relief=S["relief"], amp=S["amp"])
    tower.add([(28.5, -78), (38, -86), (47.5, -78)], vocab.ROOF_SLATE, relief=S["relief"], amp=S["amp"] * 0.6)
    tower.add(disc(38, -88.5, 2.4, 10), FIRE_RED)
    door(tower, "tower", 33, -21, 10, 19, S["relief"], S["amp"], "flat")
    for y in (-50, -66):
        tower.add(chamfered(35.5, y, 40.5, y + 10, 0.5), vocab.GLASS, amp=S["amp"] * 0.3, host="tower", margin=1.0, label="slit")

    g = Group("fire_station")
    p = Piece("fire_station", 84)
    g.face("wall", rect(-50, -58, 30, 0))
    g.add(rect(-50, -58, 30, 0), WALL, relief=S["relief"], amp=S["amp"])
    g.add(chamfered(-51, -60.5, 31, -57.5, 0.6), vocab.TRIM, relief=S["relief"], amp=S["amp"] * 0.6)
    g.add(rect(-50, -33, 30, 0), vocab.BASE_DARK, amp=S["amp"] * 0.5)
    g.add(chamfered(-47.5, -30.5, -20.5, 0, 0.8, top_only=True), CREAM, relief=S["relief"], amp=S["amp"] * 0.6,
          host="wall", margin=0.8, label="garage frame", rests=True)
    truck_front(g, -46, -29, 24, 29)
    garage_door(g, "wall", -14, -29, 24, 29, S["relief"], S["amp"])
    g.add(chamfered(-25.5, -40.5, -10.5, -31.5, 0.8), CREAM, relief=S["relief"], amp=S["amp"] * 0.6, host="wall", label="sign")
    flame(g, -18, -36, 7.8)
    for x in (-47, -31, -3, 13):
        win, _ = bay_window(g, "wall", x, -55, 11, 11, S["relief"], S["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    p.lights += [(-47, -42.5, 27), (-3, -42.5, 27)]
    # Adults only (`WindowBuildingKind.STATION`), two windows' worth at the shops' rate.
    p.people = 2
    p.lamps.append((38, -25))
    p.place(tower)
    p.place(g)
    p.place(snow_cap("fire_station_tower_snow", [[(28.5, -78), (38, -86), (47.5, -78)]], S["amp"], body=3.5, cover=0.9))
    p.place(snow_cap("fire_station_snow", [[(-51, -60.5), (28, -60.5)]], S["amp"], body=3.5))
    return p


# ------------------------------------------------------------------ the police station
def police_station():
    """The block with the chequered band of the police livery under its cornice and the police car's
    light bar on its roof, a blue-grey garage door at the right, the entrance at the left with the
    shield over it, two windows beside the entrance and four upstairs. 100 units wide, 70 tall at the
    light bar."""
    g = Group("police_station")
    p = Piece("police_station", 70)
    g.face("wall", rect(-50, -62, 50, 0))
    g.add(rect(-50, -62, 50, 0), WALL, relief=S["relief"], amp=S["amp"])
    g.add(chamfered(-51, -64.5, 51, -61.5, 0.6), vocab.TRIM, relief=S["relief"], amp=S["amp"] * 0.6)
    checker_band(g, -50, 50, -61.5, rows=2, size=3.0)
    g.add(rect(-50, -30, 50, 0), vocab.BASE_DARK, amp=S["amp"] * 0.5)
    light_bar(g, 0, -64.5, 18.0)
    steps(g, "wall", -40, -22, 0, 2, 1.8, S["relief"], S["amp"])
    door(g, "wall", -38, -24, 14, 21.4, S["relief"], S["amp"], "flat")
    g.add(chamfered(-39.5, -42, -22.5, -27, 0.8), CREAM, relief=S["relief"], amp=S["amp"] * 0.6, host="wall", label="sign")
    shield(g, -31, -34.5, 12.0)
    garage_door(g, "wall", 18, -27, 28, 27, S["relief"], S["amp"], panels=3, colour=GARAGE_BLUE, under=GARAGE_BLUE_U)
    for x in (-15, 1):
        win, sill = bay_window(g, "wall", x, -24, 12, 13, S["relief"], S["amp"], frame=1.5, sill=True)
        p.windows.append(win)
        p.lights.append(sill)
    for x in (-15, 1, 17, 33):
        win, _ = bay_window(g, "wall", x, -54, 11, 11, S["relief"], S["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    # Adults only (`WindowBuildingKind.STATION`), three windows' worth at the shops' rate.
    p.people = 3
    p.lamps.append((-17, -20))
    p.place(g)
    p.place(snow_cap("police_station_snow", [[(-51, -64.5), (-10, -64.5)], [(10, -64.5), (51, -64.5)]], S["amp"], body=3.5))
    return p
