#!/usr/bin/env python3
"""v5.0 phase 3 -- the card vocabulary (shared by the three concepts, not a concept).

Each function adds cards to a Group in piece space and, where needed, declares the host face
for the containment check. The sizes of the openings DERIVE from what has to fit inside them:
a bust (49x57 u of canvas, drawn at 0.85*w/60 -> 0.69 w wide, 0.81 w tall) wants an almost
square pane of about 12 u (= 10-17 px on the BV6600): the houses' storeys 13 x 12 and ground floors
14 x 13, the shops' 12-16. Since v5.12 the school's upper floor and the corner bar's hold people at
11 x 11 too -- every window of a floor the same, and a person may stand at any of them -- where a bust
is drawn 15 % smaller than at 13, the bust being sized by its pane. A grid window hosts nothing and is
the building's texture (5-9 u), with no frame: at 5-13 px a frame is a line -- the towers' rows, and
the turret's two 5 x 7; a dormer's pane (5-6 u) hosts nobody either.
"""
from core import (W, WALL, _rgb, _hex, mix, CREAM, CREAM_U, RED, RED_U, WOOD, WOOD_U, YELLOW, STONE, SNOW, SNOW_S, SNOW_L,
                  DARK, TERRACOTTA, SLATE, INK_ROOF, Group, rect, chamfered, arch, disc, half_disc, scallops, offset)
import math

# cards derived from the wall: w * wall + (1 - w) * k  (a weight, not a mask)
BASE_DARK = W(0.72, DARK)        # the darker ground floor (shops, tower): the card underneath
BASE_LIGHT = W(0.55, CREAM)      # the pale plastered ground floor (houses)
TRIM = W(0.35, CREAM)            # cornices, string courses, coping: near cream, tinted by the wall
ROOF_TILE = W(0.30, TERRACOTTA)  # the roof tile: 70 % terracotta, 30 % the wall
ROOF_SLATE = W(0.30, SLATE)      # the slate of the towers and the shops
DOOR = W(0.45, WOOD)             # the door: wood tinted by the wall
SIDE = W(0.66, DARK)             # the side face (K3): the shadow paper turned into wall
TOP = W(0.50, CREAM)             # the lit top face (K3)
CHIMNEY = W(0.60, DARK)
GLASS = "glass"


def wall_face(g: Group, name, pts, fill=WALL, relief=None, amp=0.0, chamfer=0.0, host=None, margin=1.0, rests=True):
    """A wall face; registers its box as host `name`."""
    g.face(name, pts)
    g.add(pts, fill, relief=relief, amp=amp, host=host, margin=margin, rests=rests, label=f"face {name}")
    return pts


def grid_windows(g: Group, host, cols_x, rows_y, w, h, amp=0.0, fill=GLASS, margin=1.2):
    """The grid windows: bare glass (lights up at night), no frame. cols_x = left x of each
    column, rows_y = top y of each row."""
    for y in rows_y:
        for x in cols_x:
            g.add(chamfered(x, y, x + w, y + h, 0.5), fill, amp=amp * 0.4, host=host, margin=margin, label="grid window")
            if fill == GLASS:
                g.panes.append((x, y, w, h))


def bay_glass(name, w, h, amp, chamfer=0.6):
    """Only the glass of a bay opening, as a stamp (the frame stays in the body): so the body's
    glass mask does not have to cover the whole height of a tower."""
    g = Group(name)
    g.add(chamfered(0, 0, w, h, chamfer), GLASS, amp=amp * 0.4)
    return g


def bay_window(g: Group, host, x, y, w, h, relief, amp, frame=2.0, sill=True, chamfer=1.0, margin=1.5, glass=True):
    """The bay opening: cream frame with shadow paper and glass; returns the pane
    (x, y, w, h) for the busts and the sill (x, y_sill, w) for the lights."""
    g.add(chamfered(x - frame, y - frame, x + w + frame, y + h + frame, chamfer), CREAM, relief=relief, amp=amp,
          host=host, margin=margin, label="bay window")
    if glass:
        g.add(chamfered(x, y, x + w, y + h, chamfer * 0.6), GLASS, amp=amp * 0.4)
        g.panes.append((x, y, w, h))
    if sill:
        g.add(chamfered(x - frame - 1.5, y + h + frame, x + w + frame + 1.5, y + h + frame + 1.8, 0.6), CREAM,
              relief=relief, amp=amp * 0.6, host=host, margin=margin * 0.6, label="sill")
    return (x, y, w, h), (x, y + h + frame, w)


def door(g: Group, host, x, y, w, h, relief, amp, style="arch", fill=DOOR, margin=1.2):
    """Door with its shadow paper (a card glued onto the wall); three-facet arch or straight."""
    pts = arch(x, y, x + w, y + h, min(4.0, w * 0.32)) if style == "arch" else chamfered(x, y, x + w, y + h, 0.8, top_only=True)
    g.add(pts, fill, relief=relief, amp=amp, host=host, margin=margin, label="door")
    if style != "plain":
        g.add(disc(x + w * 0.78, y + h * 0.55, 0.9), YELLOW)   # the handle: a dot
    return pts


def glass_door(g: Group, host, x, y, w, h, relief, amp, margin=1.2):
    """The glass entrance of the tower / the shop: cream frame, two glass leaves."""
    g.add(chamfered(x - 1.5, y - 1.5, x + w + 1.5, y + h, 0.8, top_only=True), CREAM, relief=relief, amp=amp,
          host=host, margin=margin, label="entrance")
    g.add(rect(x, y, x + w / 2 - 0.6, y + h), GLASS, amp=amp * 0.3)
    g.add(rect(x + w / 2 + 0.6, y, x + w, y + h), GLASS, amp=amp * 0.3)


def steps(g: Group, host, x0, x1, y, n, rise, relief, amp, margin=0.5):
    """Stone steps under the door, each with its shadow paper (y = foot, they climb)."""
    for i in range(n):
        sp = (n - i) * 1.5
        g.add(chamfered(x0 - sp, y - (i + 1) * rise, x1 + sp, y - i * rise, 0.5), STONE, relief=relief, amp=amp * 0.6,
              host=host, margin=margin, label="step", rests=True)


def awning(g: Group, host, x0, x1, y, depth, relief, amp, n=None, colour=RED, margin=1.0):
    """The awning: a red card with a scalloped edge and its shadow, hung from the line y."""
    n = n or max(3, int((x1 - x0) / 9))
    pts = [(x0, y), (x1, y)] + scallops(x1, x0, y + depth * 0.55, depth * 0.45, n)[1:]
    g.add(pts, colour, relief=relief, amp=amp * 0.5, host=host, margin=margin, label="awning", rests=True)


def sign_plate(g: Group, host, x0, y0, x1, y1, relief, amp, emblem="disc", colour=RED, rests=False, margin=1.0):
    """The sign: a cream plaque with its shadow and ONE emblem (a disc / a drop): a shape,
    not a text -- at 1 px per unit a written name would be unreadable."""
    g.add(chamfered(x0, y0, x1, y1, 0.8), CREAM, relief=relief, amp=amp * 0.6, host=host, margin=margin, rests=rests, label="sign")
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    r = (y1 - y0) * 0.32
    if emblem == "disc":
        g.add(disc(cx, cy, r, 12), colour)
        g.add(disc(cx, cy, r * 0.45, 8), CREAM)
    elif emblem == "mug":      # the tankard: a rectangle with a handle (two cards)
        g.add(chamfered(cx - r * 0.9, cy - r, cx + r * 0.6, cy + r, 0.4), YELLOW)
        g.add(rect(cx + r * 0.7, cy - r * 0.5, cx + r * 1.3, cy + r * 0.4), YELLOW)
        g.add(chamfered(cx - r * 0.9, cy - r * 1.35, cx + r * 0.6, cy - r * 0.85, 0.3), CREAM_U)
    elif emblem == "bar":       # a dark bar: the bar's plaque
        g.add(rect(x0 + (x1 - x0) * 0.2, cy - 1.0, x1 - (x1 - x0) * 0.2, cy + 1.0), colour)


def lantern(g: Group, host, x, y, relief, amp, margin=0.5):
    """Wall lantern: dark bracket and a pane of glass, which lights with the building's windows.

    The pane was yellow in the fixed layer until v5.11, so the bar's lantern burned at noon beside
    a lamp that -- since v5.11 -- does not (inventory I-408). As glass it takes the glass mask's
    colour: cool by day, warm at night, and dark while the bar is closed, as its windows are."""
    g.add(rect(x - 0.8, y - 5, x + 0.8, y), DARK, host=host, margin=margin, label="lantern bracket")
    g.add(chamfered(x - 2.2, y - 9, x + 2.2, y - 4, 0.6), GLASS, relief=relief, amp=amp * 0.4, host=host, margin=margin, label="lantern")


def chimney(g: Group, x, y_top, w, h, relief, amp, fill=CHIMNEY):
    g.add(chamfered(x, y_top, x + w, y_top + h, 0.6, top_only=True), fill, relief=relief, amp=amp * 0.6)
    g.add(rect(x - 0.8, y_top - 1.2, x + w + 0.8, y_top + 0.6), TRIM)
    return (x + w / 2, y_top)


def snow_cap(name, lines, amp, up=2.0, body=5.0, shade_d=1.8, light=2.2, cover=0.70):
    """The snow on the ridge: three cards (cold shadow, body, light) like tree_canopy_snowcap [M].
    `lines` = polylines of the roofs' top edge; on a two-pitched roof the cap stops at `cover`
    of the slope from the ridge (a cap, not a blanket). Returns a Group with snow=True."""
    g = Group(name, snow=True)
    for k, line in enumerate(lines):
        if len(line) == 3 and cover < 1.0:
            (xl, yl), (xm, ym), (xr, yr) = line
            line = [(xm + (xl - xm) * cover, ym + (yl - ym) * cover), (xm, ym), (xm + (xr - xm) * cover, ym + (yr - ym) * cover)]
        outer = [(x, y - up) for x, y in line]

        def inner(d, sc):
            pts = []
            q = [(x, y + d) for x, y in line][::-1]
            for (xa, ya), (xb, yb) in zip(q[:-1], q[1:]):
                seg = math.hypot(xb - xa, yb - ya)
                n = max(1, int(seg / 8))
                for i in range(1, n + 1):
                    t = i / n
                    x, y = xa + (xb - xa) * t, ya + (yb - ya) * t
                    if sc and i < n:
                        pts.append((x - (xb - xa) / n * 0.5, y + 1.6))
                    pts.append((x, y))
            return pts
        g.add(outer + inner(body + shade_d, True), SNOW_S, amp=amp * 0.5, seed=f"{name}s{k}")
        g.add(outer + inner(body, True), SNOW, amp=amp * 0.5, seed=f"{name}b{k}")
        g.add(outer + inner(light, False), SNOW_L, amp=amp * 0.4, seed=f"{name}l{k}")
    return g


def dormer(g: Group, host, cx, y_base, w, h, relief, amp, fill=WALL, roof=ROOF_TILE, margin=1.0):
    """A dormer: a low wall with a small two-pitched roof and a grid window; rests on the slope."""
    g.add(rect(cx - w / 2, y_base - h, cx + w / 2, y_base), fill, relief=relief, amp=amp * 0.6, host=host, margin=margin, rests=True, label="dormer")
    g.add([(cx - w / 2 - 1.5, y_base - h + 0.5), (cx, y_base - h - w * 0.45), (cx + w / 2 + 1.5, y_base - h + 0.5)], roof, relief=relief, amp=amp * 0.6)
    g.add(chamfered(cx - w * 0.28, y_base - h * 0.85, cx + w * 0.28, y_base - h * 0.2, 0.4), GLASS, amp=amp * 0.3)
    g.panes.append((cx - w * 0.28, y_base - h * 0.85, w * 0.56, h * 0.65))


def shaded(paper, t=0.34):
    """The shadow paper of a derived card, as a card in its own right:
    W(w, k) -> W(w(1-t), k') with the same fixed term the fixed layer would carry
    (K3: the side face is the shadow turned into wall)."""
    if isinstance(paper, W):
        w2 = paper.w * (1 - t)
        r = [(1 - paper.w) * (1 - t) * c + t * d for c, d in zip(_rgb(paper.k), _rgb(DARK))]
        return W(w2, _hex([v / (1 - w2) for v in r]))
    return mix(paper, DARK, t)


def skew_rect(fx, x0, x1, y0, y1, k):
    """A rectangle on the side face (K3): verticals stay vertical, horizontals rise by k per
    unit of depth. x0, x1 are absolute abscissae (>= fx, the edge of the front)."""
    return [(x0, y0 - (x0 - fx) * k), (x1, y0 - (x1 - fx) * k), (x1, y1 - (x1 - fx) * k), (x0, y1 - (x0 - fx) * k)]


def row_stamp(name, n, w, h, pitch, amp, skew=0.0):
    """A row of n grid windows (glass only): a stamp reused row after row BY THE SAME figure
    (not a sticker shared between buildings of different sizes). Local: the first window is
    (0,0)-(w,h). With `skew` the horizontals rise (K3's flank)."""
    g = Group(name)
    for i in range(n):
        x = i * pitch
        if skew:
            g.add(skew_rect(0, x, x + w, 0, h, skew), GLASS, amp=amp * 0.3)
        else:
            g.add(chamfered(x, 0, x + w, h, 0.5), GLASS, amp=amp * 0.4)
    return g


def rows(piece, body, host, stamp, n, w, h, pitch, x, ys, label="window row", skew=0.0, margin=1.2):
    """Places the stamp `stamp` at every y of `ys` and declares each row inside the host face."""
    for y in ys:
        piece.stamp(stamp, x, y)
        x1 = x + (n - 1) * pitch + w
        top = y - (x1 - x) * skew if skew else y
        body.declare((x, top, x1, y + h), host, margin=margin, label=label)
