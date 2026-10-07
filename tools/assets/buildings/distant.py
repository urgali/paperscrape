#!/usr/bin/env python3
"""The distant houses (v5.11): three little houses that stand on the mountains.

Chosen by the maintainer on 2026-10-06 from the photographs of the proposal round: on the
mountains, off as every theme ships, all three drawings mixed. They are drawn **to be read at
14 px** -- the size `SceneSpace.distantHousePixelsTall` gives them on the BV6600 -- so they are
chunky and few: a window is five or six units of a house 22 to 30 units tall, about three pixels on
screen, and the smallest thing in them (the chalet's attic window, the snow's lip) stays above the
two-pixel floor the project holds every drawn detail to.

The same paper as the village's houses: the wall is the houses' own colour (`W`), the roof and the
door are the wall towards the ink, the glass is the scene's glass, and the snow is a layer over the
roof drawn only with the winter palette. So one coin per house picks one of the houses' two colours
and nothing here carries a colour of its own.
"""
from core import (W, WALL, DARK, Group, Piece, rect, chamfered)
import vocab

#: A house this size has no paper relief to speak of: a smaller wobble and a thinner shadow.
H = dict(amp=0.35, relief=(0.9, 1.1))
ROOF = W(0.52, DARK)
DOOR = W(0.40, DARK)
CHIMNEY = W(0.60, DARK)


def cottage():
    """A one-room house with a chimney: 18 units wide, the roof's peak 23 units up."""
    g = Group("distant_cottage")
    p = Piece("distant_cottage", 23)
    g.face("w", rect(-9, -13, 9, 0))
    g.add(rect(-7, -21, -4, -14), CHIMNEY, relief=H["relief"], amp=H["amp"])
    g.add(rect(-9, -13, 9, 0), WALL, relief=H["relief"], amp=H["amp"])
    g.add([(-11.5, -12.5), (0, -23), (11.5, -12.5)], ROOF, relief=H["relief"], amp=H["amp"])
    g.add(chamfered(-6, -10, -1, -5, 0.4), "glass", amp=0.1)
    g.add(chamfered(2, -8, 6, 0, 0.4, top_only=True), DOOR, amp=0.1)
    p.place(g)
    # Thicker than the chalet's: a unit of this house is a smaller share of a pixel (see `tall`).
    p.place(vocab.snow_cap("distant_cottage_snow", [[(-11.5, -12.5), (0, -23), (11.5, -12.5)]], 0.3,
                           up=1.4, body=2.8, shade_d=1.0, light=1.1, cover=0.85))
    return p


def chalet():
    """A broad chalet: two windows and one under the roof, 24 units wide and 22 up."""
    g = Group("distant_chalet")
    p = Piece("distant_chalet", 22)
    g.face("w", rect(-12, -11, 12, 0))
    g.add(rect(-12, -11, 12, 0), WALL, relief=H["relief"], amp=H["amp"])
    g.add([(-15, -10), (0, -22), (15, -10)], ROOF, relief=H["relief"], amp=H["amp"])
    g.add(chamfered(-9, -8, -4, -3, 0.4), "glass", amp=0.1)
    g.add(chamfered(4, -8, 9, -3, 0.4), "glass", amp=0.1)
    g.add(chamfered(-2, -15.5, 2, -11.5, 0.4), "glass", amp=0.1)
    p.place(g)
    p.place(vocab.snow_cap("distant_chalet_snow", [[(-15, -10), (0, -22), (15, -10)]], 0.3,
                           up=1.2, body=2.2, shade_d=0.9, light=1.0, cover=0.85))
    return p


def tall():
    """A narrow house of two floors, 14 units wide and 30 up, a window each floor."""
    g = Group("distant_tall")
    p = Piece("distant_tall", 30)
    g.face("w", rect(-7, -21, 7, 0))
    g.add(rect(-7, -21, 7, 0), WALL, relief=H["relief"], amp=H["amp"])
    g.add([(-9, -20.5), (0, -30), (9, -20.5)], ROOF, relief=H["relief"], amp=H["amp"])
    # Six units square, not five: at 14 px a unit of this house is under half a pixel, and five units
    # (2.3 px) came out on the BV6600's screen as one full row of light where the window fell between
    # two rows (v5.11B, measured on the screenshot); six are 2.8 px.
    g.add(chamfered(-3, -19, 3, -13, 0.4), "glass", amp=0.1)
    g.add(chamfered(-3, -10, 3, -4, 0.4), "glass", amp=0.1)
    p.place(g)
    # 5.8 units at the ridge, not the chalet's 4.3: at 14 px the chalet's cap came out on the BV6600's
    # screen as about 1.7 px of snow on this house (v5.11B, measured on the screenshot); 5.8 are 2.7 px.
    p.place(vocab.snow_cap("distant_tall_snow", [[(-9, -20.5), (0, -30), (9, -20.5)]], 0.3,
                           up=1.6, body=3.2, shade_d=1.0, light=1.2, cover=0.85))
    return p


def pieces():
    """The three, in the order the engine indexes them (`NeighbourhoodTable.DISTANT_HOUSES`)."""
    return [cottage(), chalet(), tall()]
