#!/usr/bin/env python3
"""The sixth family: the school -- one cut-out figure, with its sign on the facade.

Where it comes from. The concept rounds drew it as «S2 Clock» and the maintainer chose it in
three steps, each of which is a number in this file rather than a preference:

 - **S2_C** (round v5.6C) put four bay windows on the ground floor. S2 as first proposed declared
   *no* windows a bust could stand in -- its ground floor was the tower's 6x7 window texture -- so
   "a school shows children" would have been a rule about an empty set. The four panes are 13x15
   units, larger than the pane of about 12 units `vocab`'s docstring gives a bust, and the porch light moved from the
   side of the door (12, -18) to over the canopy (0, -28) because the right-hand row used to run
   through the doorway and through the light's own halo.
 - **B_pencil** (round v5.6E) hangs a cream plaque with a red pencil on the facade, between the
   upper windows and over the porch light that lights it. It costs **no new PNG and no new atlas
   row**: the plaque sits inside canvases the school already has, and its colours are the fixed
   ones `sign_plate` already uses. The price was paid in windows -- the upper floor's row of small
   bare panes, two groups of four, became two groups of three, to clear 38 units of wall in the middle.
 - **The upper floor's windows** (v5.12, inventory I-505; version A of the proposals, the maintainer's
   *«va bene A come avevi consigliato»* of 2026-10-09): every window of a floor the same, so the six
   small bare panes of before became four framed windows, two each side of the plaque, 11 x 11 units
   -- two 14-unit frames 2 units apart, 30 of the 36 units between the wall's edge (x +-52) and the
   plaque (x +-16) --
   framed like the ground floor's, without a sill. They are added **after** the plaque and the pencil
   ([upper_bays]), for the reason below.

**The group is named `k2_s_orologio_c` (Italian for «S2 Clock», corrected), and that name is load-bearing.** `core.Group.add` seeds
each card's wobble from `name#index`, so renaming the group moves every vertex by up to a unit and
the drawing stops being the one the maintainer approved -- measured in v5.6E, where rendering the
six sign variants under six names produced +-8 kB of pure cropping noise. `names.py` maps it to the
shipped `school_*`, exactly as it maps `k2_r_padiglione` to `restaurant_pavilion`. The plaque and
the emblem were added after every card the school had, for the same reason: every card before them
keeps the index, and therefore the seed, it had without the sign. Only the upper floor's four windows
(v5.12, [upper_bays]) come after them.
"""
from core import Group, Piece, W, WALL, CREAM, DARK, YELLOW, RED, rect, chamfered, disc
import math
import vocab
from vocab import TRIM, steps, door, snow_cap, bay_window

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


#: The upper floor's four windows (v5.12): left x of each, their top, and their pane.
UPPER_BAYS = (-48, -32, 21, 37)
UPPER_BAY_Y = -45
UPPER_BAY = (11, 11)


def upper_bays(g, p):
    """The upper floor: four framed windows, two each side of the plaque, at the end of the cards.

    The school's cards are seeded by their index (see above), and until v5.12 the upper floor was a
    row of stamps, in two groups, that added no card to the body; so the four windows are added after every card
    the school had, and the ground bays, the plaque and the pencil keep the wobble the maintainer
    approved. The Christmas strings hang under the two pairs, where they hung under the two groups,
    and first in the list as they were: which strings of a building light is dealt by their order.

    A child may stand at any of them; the school still brings the children its four windows below
    did (`Piece.people`), so a classroom's number is the one it always had.
    """
    w, h = UPPER_BAY
    for x in UPPER_BAYS:
        win, _ = bay_window(g, "wall", x, UPPER_BAY_Y, w, h, S["relief"], S["amp"], frame=1.5, sill=False)
        p.windows.append(win)
    sill_y = UPPER_BAY_Y + h + 1.5
    p.lights[:0] = [(UPPER_BAYS[0], sill_y, UPPER_BAYS[1] + w - UPPER_BAYS[0]),
                    (UPPER_BAYS[2], sill_y, UPPER_BAYS[3] + w - UPPER_BAYS[2])]


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
    ground_bays(g, p)
    p.lamps.append(LAMP)
    plaque(g)
    pencil(g, EMBLEM_CENTRE[0], EMBLEM_CENTRE[1], EMBLEM_HEIGHT)
    upper_bays(g, p)
    p.people = len(BAYS_GROUND)
    p.place(g)
    p.place(snow_cap(
        g.name + "_snow",
        [[(-W2 - 1, -H - 2.5), (-12, -H - 2.5)], [(12, -H - 2.5), (W2 + 1, -H - 2.5)], [(-7.5, -73.5), (7.5, -73.5)]],
        S["amp"], body=3.5,
    ))
    return p
