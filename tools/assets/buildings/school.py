#!/usr/bin/env python3
"""The sixth family: the school -- one cut-out figure, with its sign on the facade.

Where it comes from. The concept rounds drew it as «S2 Orologio» and the maintainer chose it in
three steps, each of which is a number in this file rather than a preference:

 - **S2_C** (round v5.6C) put four bay windows on the ground floor. S2 as first proposed declared
   *no* windows a bust could stand in -- its ground floor was the tower's 6x7 window texture -- so
   "a school shows children" would have been a rule about an empty set. The four panes are 13x15
   units, over the 12 `vocab`'s docstring requires for a bust, and the porch light moved from the
   side of the door (12, -18) to over the canopy (0, -28) because the right-hand row used to run
   through the doorway and through the light's own halo.
 - **B_matita** (round v5.6E) hangs a cream plaque with a red pencil on the facade, between the
   upper windows and over the porch light that lights it. It costs **no new PNG and no new atlas
   row**: the plaque sits inside canvases the school already has, and its colours are the fixed
   ones `sign_plate` already uses. The price is paid in windows -- the two upper rows go from four
   panes to three (`k2_t_row3`, the tower's own stamp, already shipped) to clear 38 units of wall
   in the middle.

**The group is named `k2_s_orologio_c`, and that name is load-bearing.** `core.Group.add` seeds
each card's wobble from `name#index`, so renaming the group moves every vertex by up to a unit and
the drawing stops being the one the maintainer approved -- measured in v5.6E, where rendering the
six sign variants under six names produced +-8 kB of pure cropping noise. `names.py` maps it to the
shipped `school_*`, exactly as it maps `k2_r_padiglione` to `restaurant_pavilion`. The plaque and
the emblem are added **last** for the same reason: every card before them keeps the index, and
therefore the seed, it had without the sign.
"""
from core import Group, Piece, W, WALL, CREAM, DARK, YELLOW, RED, rect, chamfered, disc
import math
import vocab
from vocab import TRIM, steps, door, snow_cap, row_stamp, rows, bay_window
import k2_profilo

#: The school's own hand: wobble amplitude and shadow-paper offset. Between the restaurant's
#: (0.9 / 1.9, 2.6) and the tower's (1.1 / 2.4, 3.2), which is where a 72-unit building belongs.
S = dict(amp=0.85, relief=(1.7, 2.3))

#: The door, the canopy over it and the porch light, as rectangles, because three other things are
#: placed by their distance from them -- see `PROPOSTE_V5_6C.md` §2.3 for the printed distances.
DOOR_BOX = (-7, -23, 7, -2)
CANOPY = (-10, -25.5, 10, -23)
LAMP = (0, -28)

CLOCK = (0, -60, 5.5)

#: The four ground-floor bays: pane 13x15 units, two each side of the entrance. The inner pair
#: clears the bottom step by 2.90 units, which is the tightest distance in the figure and is
#: positive; the outer pair by 20.11.
BAYS_GROUND = [(-48, -24), (-30, -24), (17, -24), (35, -24)]

#: The plaque on the facade, and the pencil inside it. 32x17.5 units at 1.163 px per unit (the
#: depth the proposal round measured at) is 37x20 px, and no part of the pencil is under 2 px:
#: the eraser, the smallest, is 2.2 units = 2.6 px.
PLAQUE = (-16, -48.5, 16, -31)
EMBLEM_HEIGHT = 12.0
EMBLEM_CENTRE = (0, -40.5)


def flagpole(g, x, y_base, h):
    g.add(rect(x - 0.6, y_base - h, x + 0.6, y_base), DARK, label="pole")
    g.add([(x + 0.6, y_base - h + 0.5), (x + 8, y_base - h + 2.5), (x + 0.6, y_base - h + 4.8)], RED, amp=0.3, label="flag")


def clock(g, cx, cy, r):
    """The turret clock: a cream disc with its shadow paper, and two hands of ink."""
    g.add(disc(cx, cy, r, 12), CREAM, relief=S["relief"], amp=0.3)
    g.add(rect(cx - 0.5, cy - r * 0.7, cx + 0.5, cy), DARK)
    g.add(rect(cx, cy - 0.5, cx + r * 0.55, cy + 0.5), DARK)


def shell(name):
    """Body, turret, clock, cornices, door, steps, canopy, flag: everything but the openings."""
    g = Group(name)
    p = Piece(name, 72)
    W2, H = 52, 50
    g.face("wall", rect(-W2, -H, W2, 0))
    g.add([(-W2, 0), (-W2, -H), (-11, -H), (-11, -66), (-7, -72), (7, -72), (11, -66), (11, -H), (W2, -H), (W2, 0)],
          WALL, relief=S["relief"], amp=S["amp"])
    g.face("tower", rect(-11, -72, 11, -H))
    g.add(chamfered(-W2 - 1, -H - 2.5, W2 + 1, -H + 0.5, 0.6), TRIM, relief=S["relief"], amp=S["amp"] * 0.6)
    # The coping is declared as a face and carries no card of its own: it is the ledge the v5.6E
    # round measured a roof-standing plaque against, and declaring it costs nothing in pixels.
    g.face("coping", rect(-W2 - 1, -H - 2.5, W2 + 1, -H + 0.5))
    g.add([(-12, -66), (-7.5, -73.5), (7.5, -73.5), (12, -66), (12, -63.5), (7, -70.5), (-7, -70.5), (-12, -63.5)],
          TRIM, relief=S["relief"], amp=S["amp"] * 0.6)
    g.add(rect(-W2, -25, W2, 0), vocab.BASE_DARK, amp=S["amp"] * 0.5)
    clock(g, *CLOCK)
    steps(g, "wall", -9, 9, 0, 2, 1.8, S["relief"], S["amp"])
    door(g, "wall", DOOR_BOX[0], DOOR_BOX[1], DOOR_BOX[2] - DOOR_BOX[0], DOOR_BOX[3] - DOOR_BOX[1],
         S["relief"], S["amp"], "flat")
    g.add(chamfered(*CANOPY, 0.6), TRIM, relief=S["relief"], amp=S["amp"] * 0.6, host="wall", label="canopy")
    flagpole(g, 44, -H - 2.5, 18)
    return g, p, W2, H


def upper_rows(g, p):
    """The two upper rows: three panes each, at the tower's own stamp and its own wobble.

    Four each is what S2_C drew, and `B_matita` trades the fourth pane of each row for the 38
    units of clear wall the plaque stands on. `k2_t_row3` is `tower_row_tier3`, already shipped
    and already drawn at `k2_profilo.T["amp"]`, so the school pays nothing for it.
    """
    stamp = row_stamp("k2_t_row3", 3, 6, 7, 11, k2_profilo.T["amp"])
    rows(p, g, "wall", stamp, 3, 6, 7, 11, -47, [-44])
    rows(p, g, "wall", stamp, 3, 6, 7, 11, 19, [-44])
    p.lights += [(-47, -37, 28), (19, -37, 28)]


def ground_bays(g, p):
    """The four openings a child stands in, each declared both as a host in the wall and as a pane."""
    for x, y in BAYS_GROUND:
        win, sill = bay_window(g, "wall", x, y, 13, 15, S["relief"], S["amp"], frame=1.5, sill=True)
        p.windows.append(win)
        p.lights.append(sill)


def plaque(g):
    """The sign board: `vocab.sign_plate`'s own cream plaque, hosted inside the wall face."""
    g.add(chamfered(*PLAQUE, 0.8), CREAM, relief=S["relief"], amp=S["amp"] * 0.6,
          host="wall", margin=1.5, label="sign")


def pencil(g, cx, cy, h):
    """A pencil at 45 degrees, point low and left: red barrel, ink point, yellow eraser.

    Three cards and no outline, which is the plaque vocabulary: the emblem is told from the
    restaurant's disc and the bar's tankard by being the one diagonal in the street. Every part is
    over two on-screen pixels at the depth the school is drawn at -- barrel 3.6 units thick, point
    3.5 long, eraser 2.2.
    """
    w = 0.26 * h
    length = h * math.sqrt(2) - w
    point, eraser = max(3.0, 0.25 * h), 2.2
    d = (math.cos(math.pi / 4), -math.sin(math.pi / 4))
    n = (math.cos(math.pi / 4), math.sin(math.pi / 4))
    origin = (cx - d[0] * length / 2, cy - d[1] * length / 2)

    def pt(u, v):
        return (origin[0] + d[0] * u + n[0] * v, origin[1] + d[1] * u + n[1] * v)

    g.add([pt(0, 0), pt(point, -w / 2), pt(point, w / 2)], DARK)
    g.add([pt(point - 0.3, -w / 2), pt(length - eraser + 0.3, -w / 2),
           pt(length - eraser + 0.3, w / 2), pt(point - 0.3, w / 2)], RED)
    g.add([pt(length - eraser, -w / 2), pt(length, -w / 2), pt(length, w / 2), pt(length - eraser, w / 2)], YELLOW)


def school():
    """The one piece the family is made of."""
    g, p, W2, H = shell("k2_s_orologio_c")
    upper_rows(g, p)
    ground_bays(g, p)
    p.lamps.append(LAMP)
    plaque(g)
    pencil(g, EMBLEM_CENTRE[0], EMBLEM_CENTRE[1], EMBLEM_HEIGHT)
    p.place(g)
    p.place(snow_cap(
        g.name + "_snow",
        [[(-W2 - 1, -H - 2.5), (-12, -H - 2.5)], [(12, -H - 2.5), (W2 + 1, -H - 2.5)], [(-7.5, -73.5), (7.5, -73.5)]],
        S["amp"], body=3.5,
    ))
    return p
