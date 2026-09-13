#!/usr/bin/env python3
"""v5.0 fase 3 -- la meccanica del generatore (non artwork): carte, gruppi, resa SVG->PNG col
rasterizzatore del progetto, scomposizione in fisso + maschere di peso sommate (come
tools/generate_people_layers.py), ritaglio sulla griglia da 3 px, controllo di contenimento,
tabella Kotlin a SLOT (pezzi impilati scelti per istanza), contabilita', compositore host.

Il colore: ogni carta e' o un colore fisso (#RRGGBB), o `glass` (peso 1 sulla maschera del vetro),
o `W(w, k)` = w * colore_muro + (1-w) * k: un PESO sul muro piu' un termine fisso. Cosi' un
edificio ha UNA maschera muro (+ una vetro) qualunque numero di carte derivate abbia; la carta
d'ombra di una carta e' la stessa sagoma spostata e mescolata a RELIEF_T verso DARK (la mano del
progetto, misurata in misura_mano.py), e il suo termine scuro finisce nel livello fisso.
"""
from __future__ import annotations

import hashlib
import io
import json
import math
import os
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]   # tools/assets/buildings -> the repository root
TOOL_ROOT = REPO / "tools" / "assets"
RES = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
sys.path.insert(0, str(TOOL_ROOT))
from paperscrape_assets import raster  # noqa: E402

UNIT = 3                       # SpriteBlitter.SPRITE_PIXELS_PER_UNIT
DARK = "#2B2A33"               # l'inchiostro scuro della scena (persone, ombre) [M]
RELIEF_T = 0.34                # carta d'ombra: mix(carta, DARK, 0.34) [M] persone
M_PER_UNIT = 8.2 / 96.0        # metro comune: quello del ristorante (0.08542 m/u)

FAMILIES = ("HOUSE_SMALL", "HOUSE_LARGE", "TOWER", "RESTAURANT", "BAR")
UNITS_TALL = {"HOUSE_SMALL": 5.76 / M_PER_UNIT, "HOUSE_LARGE": 7.6 / M_PER_UNIT, "TOWER": 15.6 / M_PER_UNIT,
              "RESTAURANT": 96.0, "BAR": 7.7 / M_PER_UNIT}
KIND = {"HOUSE_SMALL": "HOUSE", "HOUSE_LARGE": "HOUSE", "TOWER": "SKYSCRAPER", "RESTAURANT": "COMMERCIAL", "BAR": "COMMERCIAL"}
#: px per unita' sul BV6600 alle profondita' della schiera [M] fase 1c/2:
#: ppu = m*45/u * (0.2565 + 0.4503*d) * 0.6 * spriteUnitsTall/unitsTall
PPU = {"HOUSE_SMALL": 0.862 * 110 / UNITS_TALL["HOUSE_SMALL"], "HOUSE_LARGE": 0.862 * 145 / UNITS_TALL["HOUSE_LARGE"],
       "TOWER": 0.8715 * 182 / UNITS_TALL["TOWER"], "RESTAURANT": 1.052, "BAR": 1.303 * 92 / UNITS_TALL["BAR"]}
#: carta d'ombra (dx, dy): ~3 % dell'altezza, mai < 2 px sullo schermo, dy/dx ~ 1.35 [M] persone
RELIEF = {"HOUSE_SMALL": (1.5, 2.0), "HOUSE_LARGE": (1.5, 2.0), "TOWER": (2.4, 3.2), "RESTAURANT": (1.9, 2.6), "BAR": (1.5, 2.0)}
#: tremolio massimo per vertice ~1 px sullo schermo
WOBBLE = {"HOUSE_SMALL": 0.7, "HOUSE_LARGE": 0.7, "TOWER": 1.1, "RESTAURANT": 0.9, "BAR": 0.75}
CHAMFER = {"HOUSE_SMALL": 1.5, "HOUSE_LARGE": 1.5, "TOWER": 2.2, "RESTAURANT": 1.8, "BAR": 1.5}

# palette fissa di famiglia (quella gia' in scena) [M] misura_mano.py
CREAM, CREAM_U = "#F4E9D2", "#D8CDB8"
RED, RED_U = "#E4623E", "#A54F3A"
WOOD, WOOD_U = "#8C5A38", "#533217"
YELLOW = "#F7CE64"
GREEN = "#3F8A4A"
STONE, STONE_U = "#D4C6B0", "#9B9186"
SNOW, SNOW_S, SNOW_L = "#DCE7EF", "#9FB0BD", "#FFFFFF"
GLASS_DAY, GLASS_NIGHT = "#B9CBD9", "#FFE79A"
TERRACOTTA = "#A9573F"       # la tegola: il rosso della vela portato verso il legno
SLATE = "#4B5566"            # l'ardesia: l'inchiostro portato verso il vetro di giorno
INK_ROOF = "#1A1410"
WALL_DAY = {"HOUSE": "#F3E6D0", "COMMERCIAL": "#5C6A78", "SKYSCRAPER": "#5C6A78"}
WALL_NIGHT = {"HOUSE": "#6B5F52", "COMMERCIAL": "#303842", "SKYSCRAPER": "#303842"}
BUDGET = 4_263_156


def _rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _hex(rgb):
    return "#%02X%02X%02X" % tuple(int(round(min(255, max(0, v)))) for v in rgb)


def mix(a, b, t):
    ra, rb = _rgb(a), _rgb(b)
    return _hex([x + (y - x) * t for x, y in zip(ra, rb)])


def shade(c, t=RELIEF_T):
    return mix(c, DARK, t)


@dataclass(frozen=True)
class W:
    """Una carta derivata dal muro: w * muro + (1 - w) * k."""
    w: float
    k: str = "#000000"


WALL = W(1.0)


def paper_terms(fill):
    """(peso muro, peso vetro, termine fisso rgb 0..255) di una carta."""
    if isinstance(fill, W):
        return fill.w, 0.0, tuple((1 - fill.w) * c for c in _rgb(fill.k))
    if fill == "glass":
        return 0.0, 1.0, (0.0, 0.0, 0.0)
    return 0.0, 0.0, tuple(float(c) for c in _rgb(fill))


def paper_colour(fill, bases):
    wm, wg, k = paper_terms(fill)
    return _hex([wm * a + wg * b + c for a, b, c in zip(_rgb(bases["wall"]), _rgb(bases["glass"]), k)])


# ------------------------------------------------------------------ geometria
def wobble(seed, i, amp):
    d = hashlib.sha256(f"{seed}:{i}".encode()).digest()
    return (d[0] / 255.0 - 0.5) * 2 * amp, (d[1] / 255.0 - 0.5) * 2 * amp


def cut(points, seed, amp):
    return [(x + wobble(seed, i, amp)[0], y + wobble(seed, i, amp)[1]) for i, (x, y) in enumerate(points)]


def rect(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


def chamfered(x0, y0, x1, y1, c=1.5, top_only=False):
    """Rettangolo con gli angoli tagliati: le forbici non girano l'angolo del compasso."""
    c = min(c, (x1 - x0) / 3, (y1 - y0) / 3)
    if top_only:
        return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1), (x0, y1), (x0, y0 + c)]
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


def arch(x0, y0, x1, y1, c):
    """Porta ad arco: un rettangolo con la testa a tre faccette."""
    w = x1 - x0
    return [(x0, y0 + c), (x0 + w * 0.25, y0 + c * 0.25), (x0 + w * 0.5, y0), (x0 + w * 0.75, y0 + c * 0.25), (x1, y0 + c), (x1, y1), (x0, y1)]


def disc(cx, cy, r, n=16, seed="disc", amp=0.0):
    pts = [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]
    return cut(pts, seed, amp) if amp else pts


def half_disc(cx, cy, r, n=12):
    """Cupola: mezzo disco che guarda in su, appoggiato sul diametro y = cy."""
    return [(cx + r * math.cos(math.pi * i / n), cy - r * math.sin(math.pi * i / n)) for i in range(n + 1)]


def scallops(x_from, x_to, y, depth, n):
    """Orlo smerlato (lobi verso il basso) da x_from a x_to sulla riga y."""
    pts = []
    step = (x_to - x_from) / n
    for i in range(n):
        xa, xb = x_from + i * step, x_from + (i + 1) * step
        pts.append((xa, y))
        pts.append((xa + step * 0.25, y + depth * 0.85))
        pts.append((xa + step * 0.5, y + depth))
        pts.append((xa + step * 0.75, y + depth * 0.85))
    pts.append((x_to, y))
    return pts


def offset(pts, dx, dy):
    return [(x + dx, y + dy) for x, y in pts]


def bbox(pts):
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


# ------------------------------------------------------------------ carte e gruppi
@dataclass
class Part:
    points: list
    fill: object                    # W | "glass" | "#RRGGBB"
    relief: tuple | None = None     # (dx, dy) carta d'ombra
    opacity: float | None = None


class ContainmentError(Exception):
    pass


@dataclass
class Group:
    """Carte rese in un solo PNG fisso + maschere (muro, vetro). Coordinate: spazio del pezzo
    (x centrato, y <= 0, piede a 0). `face()` dichiara una faccia ospite; `add(..., host=...)`
    dichiara che la carta poggia dentro quella faccia (contenimento) o vi si appoggia (`rests`)."""
    name: str
    snow: bool = False
    parts: list = field(default_factory=list)
    faces: dict = field(default_factory=dict)
    checks: list = field(default_factory=list)   # (kind, child_bbox, host, margin, label)
    _n: int = 0

    def face(self, name, points):
        self.faces[name] = bbox(points)

    def add(self, points, fill, relief=None, amp=0.0, seed=None, opacity=None, host=None, margin=1.5, rests=False, label=None):
        self._n += 1
        pts = cut(points, seed or f"{self.name}#{self._n}", amp) if amp > 0 else list(points)
        self.parts.append(Part(pts, fill, relief, opacity))
        if host is not None:
            self.checks.append(("rests" if rests else "inside", bbox(points), host, margin, label or f"carta {self._n}"))
        return pts

    def declare(self, box, host, margin=1.5, label="apertura"):
        """Un riquadro senza carta (finestra d'affaccio, davanzale) che deve stare nella faccia."""
        self.checks.append(("inside", box, host, margin, label))

    def check(self):
        """Il controllo di contenimento: fallisce (ContainmentError) se un pezzo esce dalla faccia."""
        for kind, (x0, y0, x1, y1), host, m, label in self.checks:
            if host not in self.faces:
                raise ContainmentError(f"{self.name}: {label} dichiara la faccia '{host}' che non esiste")
            hx0, hy0, hx1, hy1 = self.faces[host]
            if kind == "inside":
                ok = x0 >= hx0 + m and x1 <= hx1 - m and y0 >= hy0 + m and y1 <= hy1 - m
            else:   # rests: dentro in x, il piede dentro la faccia in y
                ok = x0 >= hx0 + m and x1 <= hx1 - m and hy0 <= y1 <= hy1
            if not ok:
                raise ContainmentError(
                    f"{self.name}: {label} [{x0:.1f},{y0:.1f}]-[{x1:.1f},{y1:.1f}] esce dalla faccia '{host}' "
                    f"[{hx0:.1f},{hy0:.1f}]-[{hx1:.1f},{hy1:.1f}] (margine {m})")

    def roles(self):
        out = []
        if any(isinstance(p.fill, W) for p in self.parts):
            out.append("wall")
        if any(p.fill == "glass" for p in self.parts):
            out.append("glass")
        return out

    def bbox(self):
        xs, ys = [], []
        for p in self.parts:
            for x, y in p.points:
                xs.append(x); ys.append(y)
                if p.relief:
                    xs.append(x + p.relief[0]); ys.append(y + p.relief[1])
        return min(xs), min(ys), max(xs), max(ys)

    def canvas(self):
        x0, y0, x1, y1 = self.bbox()
        ox, oy = math.floor(x0) - 1, math.floor(y0) - 1
        return ox, oy, math.ceil(x1) + 1 - ox, math.ceil(y1) + 1 - oy

    def svg(self, bases, region=None):
        ox, oy, w, h = self.canvas()
        out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w * UNIT}" height="{h * UNIT}" viewBox="0 0 {w} {h}">',
               f"<!-- v5.0 fase 3, {self.name}: carte con tremolio scritto nelle coordinate; sotto ogni carta la sua "
               f"carta d'ombra spostata e mescolata al {int(RELIEF_T * 100)} % verso {DARK}. Generato da build_fase3.py. -->"]

        def poly(pts, fill, extra=""):
            s = " ".join(f"{x - ox:.2f},{y - oy:.2f}" for x, y in pts)
            return f'<polygon points="{s}" fill="{fill}"{extra}/>'

        for p in self.parts:
            wm, wg, _ = paper_terms(p.fill)
            if region is None:
                top = paper_colour(p.fill, bases)
                under = shade(top)
            else:
                wgt = wm if region == "wall" else wg
                gt, gu = round(255 * wgt), round(255 * wgt * (1 - RELIEF_T))
                top = "#%02X%02X%02X" % (gt, gt, gt)
                under = "#%02X%02X%02X" % (gu, gu, gu)
            extra = f' opacity="{p.opacity}"' if p.opacity is not None else ""
            if p.relief:
                out.append("<!-- paperscrape-relief -->")
                out.append(poly(offset(p.points, *p.relief), under, extra))
            out.append(poly(p.points, top, extra))
        out.append("</svg>")
        return "\n".join(out)


def render_rgba(svg):
    return np.array(Image.open(io.BytesIO(raster.render_svg(svg).png_bytes)).convert("RGBA"))


def crop_grid(arr):
    a = arr[:, :, 3]
    ys, xs = np.where(a > 0)
    if len(xs) == 0:
        return arr[:UNIT, :UNIT], 0, 0
    x0, x1 = (xs.min() // UNIT) * UNIT, ((xs.max() // UNIT) + 1) * UNIT
    y0, y1 = (ys.min() // UNIT) * UNIT, ((ys.max() // UNIT) + 1) * UNIT
    return arr[y0:y1, x0:x1], x0 // UNIT, y0 // UNIT


@dataclass
class Layer:
    suffix: str
    role: str
    pixels: np.ndarray
    dx: int
    dy: int


MASK_SUFFIX = {"wall": "mw", "glass": "mg"}
ROLE_KT = {"wall": "ADD_WALL", "glass": "ADD_GLASS"}


def bases_for(kind, night=False):
    return {"wall": WALL_NIGHT[kind] if night else WALL_DAY[kind], "glass": GLASS_NIGHT if night else GLASS_DAY}


def decompose(g: Group, bases: dict, svg_dir: Path) -> list[Layer]:
    """fisso = premoltiplicato - somma(peso * base); ricomposizione entro 2 livelli o si ferma."""
    g.check()
    art_svg = g.svg(bases)
    (svg_dir / f"{g.name}.svg").write_text(art_svg, encoding="utf-8")
    art = render_rgba(art_svg).astype(np.float64)
    alpha = art[:, :, 3]
    premul = art[:, :, :3] * (alpha / 255.0)[:, :, None]
    weights = {}
    for role in g.roles():
        grey = render_rgba(g.svg(bases, region=role)).astype(np.float64)
        w = grey[:, :, 0] * (grey[:, :, 3] / 255.0) / 255.0
        q = np.clip(np.floor(w * 255.0), 0, 255)
        if q.max() > 0:
            weights[role] = q
    fixed = premul.copy()
    for role, q in weights.items():
        fixed -= (q / 255.0)[:, :, None] * np.array(_rgb(bases[role]), dtype=np.float64)[None, None, :]
    if fixed.min() < -2.5 or (fixed - alpha[:, :, None]).max() > 2.5:
        sys.exit(f"{g.name}: livello fisso fuori intervallo [{fixed.min():.2f}, {(fixed - alpha[:, :, None]).max():.2f}]")
    safe = np.where(alpha > 0, alpha, 255.0)
    straight = np.clip(np.round(fixed / (safe / 255.0)[:, :, None]), 0, 255)
    fx = np.zeros_like(art, dtype=np.uint8)
    fx[:, :, :3] = straight.astype(np.uint8)
    fx[:, :, 3] = alpha.astype(np.uint8)
    back = fx[:, :, :3].astype(np.float64) * (alpha / 255.0)[:, :, None]
    for role, q in weights.items():
        back += (q / 255.0)[:, :, None] * np.array(_rgb(bases[role]), dtype=np.float64)[None, None, :]
    worst = float(np.abs(back - premul).max())
    if worst > 2.01:
        sys.exit(f"{g.name}: ricomposizione fuori di {worst:.2f} livelli")
    layers = []
    arr, dx, dy = crop_grid(fx)
    layers.append(Layer("fx", "SNOW" if g.snow else "FIXED", arr, dx, dy))
    for role, q in weights.items():
        m = np.zeros_like(art, dtype=np.uint8)
        m[:, :, :3] = 255
        m[:, :, 3] = q.astype(np.uint8)
        arr, dx, dy = crop_grid(m)
        layers.append(Layer(MASK_SUFFIX[role], ROLE_KT[role], arr, dx, dy))
    return layers


# ------------------------------------------------------------------ pezzi, slot, edifici
@dataclass
class Piece:
    """Un pezzo impilabile: gruppi (carte) piu' cio' che i comportamenti vogliono sapere, tutto nello
    spazio del pezzo (piede a y = 0). `height` e' di quanto sale la base del pezzo successivo."""
    name: str
    height: float
    groups: list = field(default_factory=list)     # (Group, x, y)
    windows: list = field(default_factory=list)    # (x, y, w, h) affacci: busti
    lights: list = field(default_factory=list)     # (x, y_sill, w) davanzali di Natale
    lamps: list = field(default_factory=list)      # (x, y) luce del portico / negozio
    stamps: list = field(default_factory=list)     # (Group, x, y) timbri (vetro) disegnati DOPO i corpi
    smoke: tuple = (0.0, 0.0)
    beacon: tuple = (0.0, 0.0)

    def place(self, group, x=0.0, y=0.0):
        self.groups.append((group, x, y))
        return group

    def stamp(self, group, x=0.0, y=0.0):
        self.stamps.append((group, x, y))
        return group

    def ordered(self):
        """Corpi, poi timbri, poi neve: un timbro di vetro deve stare sopra il muro che lo ospita."""
        bodies = [t for t in self.groups if not t[0].snow]
        snows = [t for t in self.groups if t[0].snow]
        return bodies + self.stamps + snows


@dataclass
class Slot:
    options: list
    rmin: int = 1
    rmax: int = 1


@dataclass
class Building:
    family: str
    slots: list
    shadow_half: float
    hues: list
    sat_lift: float

    @property
    def kind(self):
        return KIND[self.family]

    @property
    def units_tall(self):
        return UNITS_TALL[self.family]


# ------------------------------------------------------------------ produzione
def build_concept(concept: str, buildings: dict, out: Path):
    """Rende ogni gruppo una volta, scrive SVG/PNG; restituisce (tabella, pezzi, file)."""
    svg_dir, png_dir = out / concept / "svg", out / concept / "png"
    for d in (svg_dir, png_dir):
        d.mkdir(parents=True, exist_ok=True)
        for f in d.iterdir():
            f.unlink()
    rendered: dict[str, list[Layer]] = {}
    files: dict[str, tuple[int, int]] = {}
    pieces: dict[str, dict] = {}

    def piece_entry(piece: Piece, kind):
        if piece.name in pieces:
            return
        parts = []
        bases = bases_for(kind)
        body_done = False
        for g, px, py in piece.ordered():
            if g.snow and not body_done:
                parts.append(("OCCUPANTS", 0, 0.0, 0.0)); body_done = True
            if g.name not in rendered:
                rendered[g.name] = decompose(g, bases, svg_dir)
                for layer in rendered[g.name]:
                    pname = f"{g.name}_{layer.suffix}"
                    Image.fromarray(layer.pixels, "RGBA").save(png_dir / f"{pname}.png", optimize=True)
                    files[pname] = (layer.pixels.shape[1], layer.pixels.shape[0])
            ox, oy, _, _ = g.canvas()
            for layer in rendered[g.name]:
                parts.append((layer.role, f"{g.name}_{layer.suffix}", px + ox + layer.dx, py + oy + layer.dy))
        if not body_done:
            parts.append(("OCCUPANTS", 0, 0.0, 0.0))
        for x, y in piece.lamps:
            parts.append(("LAMP", 0, x, y))
        pieces[piece.name] = {"height": piece.height, "parts": parts, "windows": piece.windows,
                              "lights": piece.lights, "smoke": piece.smoke, "beacon": piece.beacon}

    table = {}
    for family in FAMILIES:
        b = buildings[family]
        for s in b.slots:
            for p in s.options:
                piece_entry(p, b.kind)
        table[family] = {"unitsTall": round(b.units_tall, 3), "shadowHalf": round(b.shadow_half, 1), "kind": b.kind,
                         "slots": [{"options": [p.name for p in s.options], "rmin": s.rmin, "rmax": s.rmax} for s in b.slots],
                         "hues": b.hues, "satLift": b.sat_lift}
    return table, pieces, files


# ------------------------------------------------------------------ contabilita'
def uploaded_bytes(path):
    im = Image.open(path)
    a = np.array(im.convert("RGBA"))[:, :, 3]
    ys, xs = np.where(a > 0)
    w, h = im.size
    return int((min(xs.max() + 1, w - 1) - max(xs.min() - 1, 0) + 1) * (min(ys.max() + 1, h - 1) - max(ys.min() - 1, 0) + 1) * 4)


def budget(concepts, files_by: dict, out: Path):
    shipped = {}
    for n in sorted(os.listdir(RES)):
        if n.endswith(".png") and n.split("_")[0] in ("house", "skyscraper", "restaurant", "bar") and "_q" not in n:
            im = Image.open(RES / n)
            shipped[n[:-4]] = (im.size[0] * im.size[1] * 4, uploaded_bytes(RES / n))
    rep = {"shipped_perimeter": {"files": len(shipped), "decoded": sum(v[0] for v in shipped.values()),
                                 "uploaded_level0": sum(v[1] for v in shipped.values())}, "concepts": {}}
    lines = ["# Contabilita' dei concept contro i due tetti (generata da build_fase3.py)", "",
             f"Perimetro spedito ({len(shipped)} PNG, senza palma): {rep['shipped_perimeter']['decoded']} B decodificati, "
             f"{rep['shipped_perimeter']['uploaded_level0']} B caricati (livello 0, ritaglio + 1 texel). Budget {BUDGET} B.", ""]
    for concept, (files, png_dir) in files_by.items():
        dec, up = 0, 0
        rows = []
        for name, (w, h) in sorted(files.items()):
            u = uploaded_bytes(png_dir / f"{name}.png")
            dec += w * h * 4; up += u
            rows.append((name, w, h, w * h * 4, u))
        q = concepts.index(concept) + 1
        rep["concepts"][concept] = {"files": len(files), "decoded": dec, "uploaded_level0": up,
                                    "per_file": {n: {"w": w, "h": h, "decoded": d, "uploaded": u} for n, w, h, d, u in rows}}
        lines += [f"## K{q} {concept}: {len(files)} PNG, {dec} B decodificati ({'sotto' if dec <= BUDGET else 'SOPRA'} il budget di {BUDGET - dec:+d} B), {up} B caricati", "",
                  "| PNG | px | decodificati B | caricati B |", "|---|---|---:|---:|"]
        lines += [f"| {n}_q{q} | {w}x{h} | {d} | {u} |" for n, w, h, d, u in rows]
        lines.append("")
    (out / "budget.json").write_text(json.dumps(rep, indent=1))
    (out / "budget.md").write_text("\n".join(lines))
    return rep


# ------------------------------------------------------------------ compositore host (anteprima)
def instance_fraction(tile_x, depth, salt):
    raw = tile_x * 7919.0 + depth * 7919.0 * 131.0 + salt
    return raw - math.floor(raw)


def shifted_colour(hexc, hue_deg, sat_lift):
    import colorsys
    r, g, b = [c / 255.0 for c in _rgb(hexc)]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    h = ((h * 360 + hue_deg) % 360) / 360.0
    s = min(1.0, s + (1 - s) * sat_lift)
    return _hex([c * 255 for c in colorsys.hls_to_rgb(h, l, s)])


def stack_instance(table_entry, pieces, tile_x, depth):
    """Riproduce la scelta del compositore Kotlin: (pezzo, baseY, primo indice finestra)."""
    placed, base_y, wcount = [], 0.0, 0
    for si, s in enumerate(table_entry["slots"]):
        opt = min(int(instance_fraction(tile_x, depth, 3.7 * si + 11.3) * len(s["options"])), len(s["options"]) - 1)
        span = s["rmax"] - s["rmin"] + 1
        n = s["rmin"] + min(int(instance_fraction(tile_x, depth, 5.1 * si + 23.9) * span), span - 1)
        for _ in range(n):
            p = pieces[s["options"][opt]]
            placed.append((p, base_y, wcount))
            wcount += len(p["windows"])
            base_y -= p["height"]
    hue_i = min(int(instance_fraction(tile_x, depth, 41.7) * len(table_entry["hues"])), len(table_entry["hues"]) - 1)
    return placed, table_entry["hues"][hue_i]


def composite_instance(table_entry, pieces, png_dir, kind, night, winter, ppu, tile_x, depth, lit=None):
    """Ricompone un'istanza come il compositore (fisso source-over, maschere sommate), alla
    risoluzione autorata, poi la riduce alla scala dello schermo. Restituisce (rgba premult float,
    ox_px, oy_px) con l'origine al piede dell'edificio."""
    placed, hue = stack_instance(table_entry, pieces, tile_x, depth)
    wall = shifted_colour(WALL_NIGHT[kind] if night else WALL_DAY[kind], hue, table_entry["satLift"])
    glass = GLASS_NIGHT if night else GLASS_DAY
    if lit is not None:
        glass = mix(GLASS_DAY, GLASS_NIGHT, lit)
    cols = {"ADD_WALL": wall, "ADD_GLASS": glass}
    xs, ys, sizes = [], [], {}
    for p, by, _ in placed:
        for role, name, x, y in p["parts"]:
            if role in ("OCCUPANTS", "LAMP"):
                continue
            if name not in sizes:
                sizes[name] = Image.open(png_dir / f"{name}.png").size
            xs += [x, x + sizes[name][0] / UNIT]; ys += [by + y, by + y + sizes[name][1] / UNIT]
    sh = table_entry["shadowHalf"]
    xs += [-sh, sh]; ys += [-6, 6]
    x0, y0 = math.floor(min(xs)) - 2, math.floor(min(ys)) - 2
    Wp, Hp = (math.ceil(max(xs)) + 2 - x0) * UNIT, (math.ceil(max(ys)) + 2 - y0) * UNIT
    buf = np.zeros((Hp, Wp, 4), dtype=np.float64)
    # ombra a terra
    yy, xx = np.mgrid[0:Hp, 0:Wp]
    cx, cy = (-x0) * UNIT, (-y0) * UNIT
    ell = ((xx - cx) / (sh * UNIT)) ** 2 + ((yy - cy) / (5 * UNIT)) ** 2 <= 1
    buf[ell, 3] = 46 / 255.0

    def blit(name, x, y, mode, colour=None, alpha=1.0):
        im = np.array(Image.open(png_dir / f"{name}.png").convert("RGBA")).astype(np.float64) / 255.0
        h, w = im.shape[:2]
        px, py = int(round((x - x0) * UNIT)), int(round((y - y0) * UNIT))
        dst = buf[py:py + h, px:px + w]
        if mode == "src":
            a = im[:, :, 3:4] * alpha
            dst[:, :, :3] = im[:, :, :3] * a + dst[:, :, :3] * (1 - a)
            dst[:, :, 3:4] = a + dst[:, :, 3:4] * (1 - a)
        else:
            a = im[:, :, 3:4]
            dst[:, :, :3] += a * (np.array(_rgb(colour)) / 255.0)[None, None, :]
    for p, by, _ in placed:
        for role, name, x, y in p["parts"]:
            if role == "FIXED":
                blit(name, x, by + y, "src")
            elif role == "SNOW" and winter:
                blit(name, x, by + y, "src")
            elif role in ("ADD_WALL", "ADD_GLASS"):
                blit(name, x, by + y, "add", cols[role])
            elif role == "LAMP":
                a = 60 / 255.0 + (90 / 255.0 if night else 0.0)
                lx, ly = (x - x0) * UNIT, (by + y - y0) * UNIT
                m = ((xx - lx) ** 2 + (yy - ly) ** 2) <= (6 * UNIT) ** 2
                buf[m, :3] = buf[m, :3] * (1 - a) + np.array(_rgb("#FFD97A")) / 255.0 * a
                buf[m, 3] = buf[m, 3] * (1 - a) + a
                m = ((xx - lx) ** 2 + (yy - ly) ** 2) <= (2.6 * UNIT) ** 2
                buf[m, :3] = np.array(_rgb("#FFD97A")) / 255.0; buf[m, 3] = 1.0
    buf = np.clip(buf, 0, 1)
    f = ppu / UNIT
    im = Image.fromarray((buf * 255).astype(np.uint8), "RGBA").resize((max(1, round(Wp * f)), max(1, round(Hp * f))), Image.BILINEAR)
    return np.array(im) / 255.0, -x0 * ppu, -y0 * ppu, placed, hue
