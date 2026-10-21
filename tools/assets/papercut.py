"""The cut-paper geometry the sleigh's and the Halloween moon's generators share (v5.15).

The same move the fir's generator makes (`fir/build_fir.py`): every outline is a polygon whose vertices are nudged by
a hash of the shape's name and the vertex's index -- the scissors' small wobble, never a random draw, so a run
redraws the same bytes -- and every piece of paper a flat colour, its relief an *under-paper*, the same cut moved
down and to the right in a darker tone of the piece's own colour (`DESIGN_NOTES.md` §2, "An outline goes outside").
"""
from __future__ import annotations

import hashlib
import io
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from paperscrape_assets.raster import render_svg  # noqa: E402

Pt = tuple[float, float]


def wobble(seed: str, i: int, amp: float) -> Pt:
    d = hashlib.sha256(f"{seed}:{i}".encode()).digest()
    return (d[0] / 255.0 - 0.5) * 2 * amp, (d[1] / 255.0 - 0.5) * 2 * amp


def cut(points, seed: str, amp: float = 0.3):
    """The scissors' wobble: every vertex nudged by a hash of its index."""
    out = []
    for i, (x, y) in enumerate(points):
        dx, dy = wobble(seed, i, amp)
        out.append((x + dx, y + dy))
    return out


def ellipse(cx, cy, rx, ry, n=16, rot=0.0, start=0.0):
    """A faceted ellipse: n cuts, as a pair of scissors goes round a curve."""
    r = math.radians(rot)
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n + math.radians(start)
        x, y = rx * math.cos(a), ry * math.sin(a)
        pts.append((cx + x * math.cos(r) - y * math.sin(r), cy + x * math.sin(r) + y * math.cos(r)))
    return pts


def capsule(x0, y0, x1, y1, w0, w1=None, n=4):
    """A band from (x0,y0) to (x1,y1), w0 wide at the start and w1 at the end, with faceted round ends."""
    w1 = w0 if w1 is None else w1
    a = math.atan2(y1 - y0, x1 - x0)
    pts = []
    for k in range(n + 1):  # the end cap, from one side round to the other
        t = a - math.pi / 2 + math.pi * k / n
        pts.append((x1 + w1 / 2 * math.cos(t), y1 + w1 / 2 * math.sin(t)))
    for k in range(n + 1):  # the start cap
        t = a + math.pi / 2 + math.pi * k / n
        pts.append((x0 + w0 / 2 * math.cos(t), y0 + w0 / 2 * math.sin(t)))
    return pts


def polyline_band(points, w, n=3):
    """A band of width w along a polyline, its two ends cut square-ish (a strap, a runner)."""
    left, right = [], []
    for i, (x, y) in enumerate(points):
        if i == 0:
            dx, dy = points[1][0] - x, points[1][1] - y
        elif i == len(points) - 1:
            dx, dy = x - points[i - 1][0], y - points[i - 1][1]
        else:
            dx, dy = points[i + 1][0] - points[i - 1][0], points[i + 1][1] - points[i - 1][1]
        L = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / L * w / 2, dx / L * w / 2
        left.append((x + nx, y + ny))
        right.append((x - nx, y - ny))
    return left + right[::-1]


def rotate(points, cx, cy, deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in points]


def move(points, dx, dy):
    return [(x + dx, y + dy) for x, y in points]


def mirror_x(points, axis=0.0):
    return [(2 * axis - x, y) for x, y in points]


def star(cx, cy, r_out, r_in, n=5, rot=-90.0):
    pts = []
    for k in range(2 * n):
        r = r_out if k % 2 == 0 else r_in
        a = math.radians(rot + k * 180.0 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def d(points) -> str:
    return "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in points) + " Z"


def darker(hex_colour: str, k: float) -> str:
    h = hex_colour.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return "#%02X%02X%02X" % (round(r * k), round(g * k), round(b * k))


class Sheet:
    """The pieces of one sprite, in the order they are laid down: (outline, colour, clip-name or None)."""

    def __init__(self):
        self.pieces: list[tuple[list[Pt], str, str | None]] = []
        self.clips: dict[str, list[list[Pt]]] = {}

    def add(self, points, colour, clip=None):
        self.pieces.append((points, colour, clip))
        return points

    def relief(self, points, colour, drop=(1.0, 1.4), k=0.74, clip=None):
        """A piece over its under-paper: the same cut, moved down and right, in a darker tone."""
        self.add(move(points, *drop), darker(colour, k), clip)
        return self.add(points, colour, clip)

    def clip(self, name, shapes):
        self.clips[name] = shapes

    def body(self) -> list[str]:
        out = []
        if self.clips:
            out.append("  <defs>" + "".join(
                f'<clipPath id="{n}">' + "".join(f'<path d="{d(s)}"/>' for s in shapes) + "</clipPath>"
                for n, shapes in self.clips.items()) + "</defs>")
        for pts, colour, clip in self.pieces:
            c = f' clip-path="url(#{clip})"' if clip else ""
            out.append(f'  <path d="{d(pts)}" fill="{colour}"{c}/>')
        return out


def render_body(body: list[str], box, px: int) -> bytes:
    """The PNG of `body` drawn in the box (x, y, w, h) at px pixels a unit."""
    x, y, w, h = box
    svg = "\n".join([f'<svg xmlns="http://www.w3.org/2000/svg" width="{w * px}" height="{h * px}" viewBox="{x} {y} {w} {h}">',
                     *body, "</svg>"])
    return render_svg(svg).png_bytes


def content_box(body: list[str], frame, px: int, margin: int = 6):
    """The content of `body`, drawn over `frame` (x, y, w, h) grown by a margin, in whole units: (x, y, w, h)."""
    fx, fy, fw, fh = frame
    probe = "\n".join([f'<svg xmlns="http://www.w3.org/2000/svg" width="{(fw + 2 * margin) * px}" height="{(fh + 2 * margin) * px}" '
                       f'viewBox="{fx - margin} {fy - margin} {fw + 2 * margin} {fh + 2 * margin}">', *body, "</svg>"])
    a = np.array(Image.open(io.BytesIO(render_svg(probe).png_bytes)).convert("RGBA"))[:, :, 3]
    ys, xs = np.where(a > 0)
    if len(xs) == 0:
        raise ValueError("nothing drawn")
    x0, y0 = xs.min() // px + fx - margin, ys.min() // px + fy - margin
    x1, y1 = -(-(xs.max() + 1) // px) + fx - margin, -(-(ys.max() + 1) // px) + fy - margin
    return int(x0), int(y0), int(x1 - x0), int(y1 - y0)


def svg_document(body: list[str], box, px: int, comment: str) -> str:
    x, y, w, h = box
    return "\n".join([f'<svg xmlns="http://www.w3.org/2000/svg" width="{w * px}" height="{h * px}" viewBox="{x} {y} {w} {h}">',
                      f"  <!-- {comment} -->", *body, "</svg>"]) + "\n"
