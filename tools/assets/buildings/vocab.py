#!/usr/bin/env python3
"""v5.0 fase 3 -- il vocabolario delle carte (condiviso dai tre concept, non un concept).

Ogni funzione aggiunge carte a un Group nello spazio del pezzo e, dove serve, dichiara la faccia
ospite per il controllo di contenimento. Le dimensioni delle aperture DISCENDONO da cosa ci deve
stare dentro: un busto (49x57 u di tela, disegnato a 0.85*w/60 -> 0.69 w largo, 0.81 w alto) vuole
un pane quasi quadrato di >= 12 u (= 10-17 px sul BV6600); una finestra di serie non ospita nulla
ed e' la texture dell'edificio (6-9 u), senza cornice: a 5-13 px una cornice e' una riga.
"""
from core import (W, WALL, _rgb, _hex, mix, CREAM, CREAM_U, RED, RED_U, WOOD, WOOD_U, YELLOW, STONE, SNOW, SNOW_S, SNOW_L,
                  DARK, TERRACOTTA, SLATE, INK_ROOF, Group, rect, chamfered, arch, disc, half_disc, scallops, offset)
import math

# carte derivate dal muro: w * muro + (1 - w) * k  (un peso, non una maschera)
BASE_DARK = W(0.72, DARK)        # il piano terra piu' scuro (negozi, torre): la carta sotto
BASE_LIGHT = W(0.55, CREAM)      # il piano terra intonacato chiaro (case)
TRIM = W(0.35, CREAM)            # cornici, marcapiani, coping: quasi crema, tinta dal muro
ROOF_TILE = W(0.30, TERRACOTTA)  # la tegola: per il 70 % terracotta, per il 30 % il muro
ROOF_SLATE = W(0.30, SLATE)      # l'ardesia delle torri e dei negozi
DOOR = W(0.45, WOOD)             # la porta: legno tinto dal muro
SIDE = W(0.66, DARK)             # la faccia laterale (K3): la carta d'ombra fatta muro
TOP = W(0.50, CREAM)             # la faccia superiore illuminata (K3)
CHIMNEY = W(0.60, DARK)
GLASS = "glass"


def wall_face(g: Group, name, pts, fill=WALL, relief=None, amp=0.0, chamfer=0.0, host=None, margin=1.0, rests=True):
    """Una faccia di muro; registra il suo riquadro come ospite `name`."""
    g.face(name, pts)
    g.add(pts, fill, relief=relief, amp=amp, host=host, margin=margin, rests=rests, label=f"faccia {name}")
    return pts


def grid_windows(g: Group, host, cols_x, rows_y, w, h, amp=0.0, fill=GLASS, margin=1.2):
    """Le finestre di serie: vetro nudo (si accende di notte), senza cornice. cols_x = x sinistro
    di ogni colonna, rows_y = y alto di ogni riga."""
    for y in rows_y:
        for x in cols_x:
            g.add(chamfered(x, y, x + w, y + h, 0.5), fill, amp=amp * 0.4, host=host, margin=margin, label="finestra di serie")


def bay_glass(name, w, h, amp, chamfer=0.6):
    """Il solo vetro di un'apertura d'affaccio, come timbro (la cornice resta nel corpo): cosi' la
    maschera del vetro del corpo non deve coprire tutta l'altezza di una torre."""
    g = Group(name)
    g.add(chamfered(0, 0, w, h, chamfer), GLASS, amp=amp * 0.4)
    return g


def bay_window(g: Group, host, x, y, w, h, relief, amp, frame=2.0, sill=True, chamfer=1.0, margin=1.5, glass=True):
    """L'apertura d'affaccio: cornice crema con carta d'ombra e vetro; restituisce il pane
    (x, y, w, h) per i busti e il davanzale (x, y_sill, w) per le luci."""
    g.add(chamfered(x - frame, y - frame, x + w + frame, y + h + frame, chamfer), CREAM, relief=relief, amp=amp,
          host=host, margin=margin, label="finestra d'affaccio")
    if glass:
        g.add(chamfered(x, y, x + w, y + h, chamfer * 0.6), GLASS, amp=amp * 0.4)
    if sill:
        g.add(chamfered(x - frame - 1.5, y + h + frame, x + w + frame + 1.5, y + h + frame + 1.8, 0.6), CREAM,
              relief=relief, amp=amp * 0.6, host=host, margin=margin * 0.6, label="davanzale")
    return (x, y, w, h), (x, y + h + frame, w)


def door(g: Group, host, x, y, w, h, relief, amp, style="arch", fill=DOOR, margin=1.2):
    """Porta con la sua carta d'ombra (una carta incollata sul muro); arco a tre faccette o retta."""
    pts = arch(x, y, x + w, y + h, min(4.0, w * 0.32)) if style == "arch" else chamfered(x, y, x + w, y + h, 0.8, top_only=True)
    g.add(pts, fill, relief=relief, amp=amp, host=host, margin=margin, label="porta")
    if style != "plain":
        g.add(disc(x + w * 0.78, y + h * 0.55, 0.9), YELLOW)   # la maniglia: un punto
    return pts


def glass_door(g: Group, host, x, y, w, h, relief, amp, margin=1.2):
    """Il portale di vetro della torre / del negozio: cornice crema, due ante di vetro."""
    g.add(chamfered(x - 1.5, y - 1.5, x + w + 1.5, y + h, 0.8, top_only=True), CREAM, relief=relief, amp=amp,
          host=host, margin=margin, label="portale")
    g.add(rect(x, y, x + w / 2 - 0.6, y + h), GLASS, amp=amp * 0.3)
    g.add(rect(x + w / 2 + 0.6, y, x + w, y + h), GLASS, amp=amp * 0.3)


def steps(g: Group, host, x0, x1, y, n, rise, relief, amp, margin=0.5):
    """Gradini di pietra sotto la porta, ognuno con la sua carta d'ombra (y = piede, salgono)."""
    for i in range(n):
        sp = (n - i) * 1.5
        g.add(chamfered(x0 - sp, y - (i + 1) * rise, x1 + sp, y - i * rise, 0.5), STONE, relief=relief, amp=amp * 0.6,
              host=host, margin=margin, label="gradino", rests=True)


def awning(g: Group, host, x0, x1, y, depth, relief, amp, n=None, colour=RED, margin=1.0):
    """La tenda: una carta rossa a orlo smerlato con la sua ombra, appesa alla riga y."""
    n = n or max(3, int((x1 - x0) / 9))
    pts = [(x0, y), (x1, y)] + scallops(x1, x0, y + depth * 0.55, depth * 0.45, n)[1:]
    g.add(pts, colour, relief=relief, amp=amp * 0.5, host=host, margin=margin, label="tenda", rests=True)


def sign_plate(g: Group, host, x0, y0, x1, y1, relief, amp, emblem="disc", colour=RED, rests=False, margin=1.0):
    """L'insegna: una targa crema con la sua ombra e UN emblema (un disco / una goccia): una forma,
    non un testo -- a 1 px per unita' un nome scritto sarebbe illeggibile."""
    g.add(chamfered(x0, y0, x1, y1, 0.8), CREAM, relief=relief, amp=amp * 0.6, host=host, margin=margin, rests=rests, label="insegna")
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    r = (y1 - y0) * 0.32
    if emblem == "disc":
        g.add(disc(cx, cy, r, 12), colour)
        g.add(disc(cx, cy, r * 0.45, 8), CREAM)
    elif emblem == "mug":      # il boccale: un rettangolo con un'ansa (due carte)
        g.add(chamfered(cx - r * 0.9, cy - r, cx + r * 0.6, cy + r, 0.4), YELLOW)
        g.add(rect(cx + r * 0.7, cy - r * 0.5, cx + r * 1.3, cy + r * 0.4), YELLOW)
        g.add(chamfered(cx - r * 0.9, cy - r * 1.35, cx + r * 0.6, cy - r * 0.85, 0.3), CREAM_U)
    elif emblem == "bar":       # una barra scura: la targa del bar
        g.add(rect(x0 + (x1 - x0) * 0.2, cy - 1.0, x1 - (x1 - x0) * 0.2, cy + 1.0), colour)


def lantern(g: Group, host, x, y, relief, amp, margin=0.5):
    """Il lampione a muro: staffa scura + lume giallo (fisso, la lampada vera e' il LAMP del runtime)."""
    g.add(rect(x - 0.8, y - 5, x + 0.8, y), DARK, host=host, margin=margin, label="staffa lanterna")
    g.add(chamfered(x - 2.2, y - 9, x + 2.2, y - 4, 0.6), YELLOW, relief=relief, amp=amp * 0.4, host=host, margin=margin, label="lanterna")


def chimney(g: Group, x, y_top, w, h, relief, amp, fill=CHIMNEY):
    g.add(chamfered(x, y_top, x + w, y_top + h, 0.6, top_only=True), fill, relief=relief, amp=amp * 0.6)
    g.add(rect(x - 0.8, y_top - 1.2, x + w + 0.8, y_top + 0.6), TRIM)
    return (x + w / 2, y_top)


def snow_cap(name, lines, amp, up=2.0, body=5.0, shade_d=1.8, light=2.2, cover=0.70):
    """La neve sul colmo: tre carte (ombra fredda, corpo, luce) come tree_canopy_snowcap [M]. `lines`
    = polilinee del bordo alto dei tetti; su una falda a due spioventi la calotta si ferma a `cover`
    della falda dal colmo (calotta, non coperta). Restituisce un Group con snow=True."""
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
    """Un abbaino: un muretto con un tettino a due falde e una finestra di serie; poggia sulla falda."""
    g.add(rect(cx - w / 2, y_base - h, cx + w / 2, y_base), fill, relief=relief, amp=amp * 0.6, host=host, margin=margin, rests=True, label="abbaino")
    g.add([(cx - w / 2 - 1.5, y_base - h + 0.5), (cx, y_base - h - w * 0.45), (cx + w / 2 + 1.5, y_base - h + 0.5)], roof, relief=relief, amp=amp * 0.6)
    g.add(chamfered(cx - w * 0.28, y_base - h * 0.85, cx + w * 0.28, y_base - h * 0.2, 0.4), GLASS, amp=amp * 0.3)


def shaded(paper, t=0.34):
    """La carta d'ombra di una carta derivata, come carta a se': W(w, k) -> W(w(1-t), k') con lo
    stesso termine fisso che il livello fisso porterebbe (K3: la faccia laterale e' l'ombra fatta muro)."""
    if isinstance(paper, W):
        w2 = paper.w * (1 - t)
        r = [(1 - paper.w) * (1 - t) * c + t * d for c, d in zip(_rgb(paper.k), _rgb(DARK))]
        return W(w2, _hex([v / (1 - w2) for v in r]))
    return mix(paper, DARK, t)


def skew_rect(fx, x0, x1, y0, y1, k):
    """Un rettangolo sulla faccia laterale (K3): le verticali restano verticali, le orizzontali salgono
    di k per unita' di profondita'. x0, x1 sono ascisse assolute (>= fx, il bordo del fronte)."""
    return [(x0, y0 - (x0 - fx) * k), (x1, y0 - (x1 - fx) * k), (x1, y1 - (x1 - fx) * k), (x0, y1 - (x0 - fx) * k)]


def row_stamp(name, n, w, h, pitch, amp, skew=0.0):
    """Una riga di n finestre di serie (solo vetro): un timbro riusato riga per riga DALLA STESSA
    figura (non un adesivo condiviso fra edifici di taglia diversa). Locale: la prima finestra e'
    (0,0)-(w,h). Con `skew` le orizzontali salgono (fianco di K3)."""
    g = Group(name)
    for i in range(n):
        x = i * pitch
        if skew:
            g.add(skew_rect(0, x, x + w, 0, h, skew), GLASS, amp=amp * 0.3)
        else:
            g.add(chamfered(x, 0, x + w, h, 0.5), GLASS, amp=amp * 0.4)
    return g


def rows(piece, body, host, stamp, n, w, h, pitch, x, ys, label="riga di finestre", skew=0.0, margin=1.2):
    """Piazza il timbro `stamp` a ogni y di `ys` e dichiara ogni riga dentro la faccia ospite."""
    for y in ys:
        piece.stamp(stamp, x, y)
        x1 = x + (n - 1) * pitch + w
        top = y - (x1 - x) * skew if skew else y
        body.declare((x, top, x1, y + h), host, margin=margin, label=label)
