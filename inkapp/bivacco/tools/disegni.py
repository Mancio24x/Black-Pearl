"""I disegni dei passaggi di Bivacco, 440 x 250, con tools/arte.py.

DISEGNI = { 'nome': funzione(tela) }. I nomi sono quelli usati come img = '...' in src/tecniche.lua
e src/attrezzatura.lua.
"""
import math

import numpy as np

from arte import Tela

W, H = 440, 250


# ============================================================================ pezzi

def terreno(c, y=215, ciuffi=10, seme=0):
    import random
    r = random.Random(seme)
    m = c.rett(0, y, W, H - y)
    c.forma(m, 225, rilievo=0, grana=10, bordo=0)
    c.tratto([(0, y), (W, y)], 1.6, 0)
    for _ in range(ciuffi):
        x = r.uniform(10, W - 10)
        for k in (-1, 0, 1):
            c.tratto([(x, y), (x + k * 5 + r.uniform(-1, 1), y - r.uniform(6, 11))], 1.0, 20)


def legno(c, p0, p1, sp=8, tono=175, testa=True, venature=True):
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    a = math.atan2(y1 - y0, x1 - x0)
    ca, sa = math.cos(a), math.sin(a)
    def p(u, v): return (x0 + u * ca - v * sa, y0 + u * sa + v * ca)
    m = c.poligono([p(0, -sp / 2), p(L, -sp / 2), p(L, sp / 2), p(0, sp / 2)])
    c.forma(m, tono, rilievo=2, luce=90, grana=10, bordo=1.1, tono_bordo=0)
    if venature and L > 30:
        import random
        r = random.Random(int(x0 * 7 + y0))
        for _ in range(int(L / 25)):
            u = r.uniform(4, L - 14)
            v = r.uniform(-sp / 3, sp / 3)
            c.tratto([p(u, v), p(u + r.uniform(8, 16), v)], 0.6, 70)
    if testa:
        cx, cy = p(L, 0)
        e = c.ellisse(cx, cy, max(1.5, sp * 0.22), sp / 2)
        c.forma(e, 230, rilievo=0, grana=0, bordo=0.9, tono_bordo=0)
    return p


def freccia(c, pts, larg=2.2, tono=0, punta=11):
    c.tratto(pts, larg, tono)
    (xa, ya), (xb, yb) = pts[-2], pts[-1]
    a = math.atan2(yb - ya, xb - xa)
    t = [(xb + punta * 0.3 * math.cos(a), yb + punta * 0.3 * math.sin(a)),
         (xb - punta * math.cos(a - 0.45), yb - punta * math.sin(a - 0.45)),
         (xb - punta * math.cos(a + 0.45), yb - punta * math.sin(a + 0.45))]
    c.forma(c.poligono(t), tono, rilievo=0, grana=0, bordo=0)


def curva(p0, p1, p2, n=20):
    """una curva di Bézier quadratica (per le frecce)"""
    out = []
    for k in range(n + 1):
        t = k / n
        out.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]))
    return out


def albero(c, x, larg=34, y_terra=215, tono=150):
    m = c.poligono([(x - larg / 2, -5), (x + larg / 2, -5), (x + larg / 2 + 4, y_terra), (x - larg / 2 - 4, y_terra)])
    c.forma(m, tono, rilievo=8, luce=120, grana=14, bordo=1.4, tono_bordo=0)
    import random
    r = random.Random(x)
    for _ in range(14):
        yy = r.uniform(0, y_terra - 20)
        xx = x + r.uniform(-larg / 2 + 4, larg / 2 - 6)
        c.tratto([(xx, yy), (xx + r.uniform(-1, 1), yy + r.uniform(12, 26))], 1.0, 40)


def pietra(c, x, y, rx, ry, tono=165):
    m = c.blob(x, y, rx, ry, 0.12)
    c.forma(m, tono, rilievo=5, luce=110, grana=12, bordo=1.3, tono_bordo=0)
    return m


def fiamma(c, x, y, h, w=None):
    w = w or h * 0.55
    pts = []
    for k in range(41):
        t = k / 40
        a = math.pi * t
        xx = x - w / 2 * math.cos(a)
        yy = y - h * (math.sin(a / 2) ** 0.7) * (0.6 + 0.4 * math.sin(a * 3) ** 2)
        pts.append((xx, yy))
    pts.append((x, y - h))
    m = c.poligono([(x - w / 2, y)] + [(x - w / 2 + w * t, y - h * math.sin(math.pi * t) ** 1.6 * (1 - 0.25 * math.sin(6 * t) ** 2)) for t in np.linspace(0, 1, 40)] + [(x + w / 2, y)])
    c.forma(m, 235, rilievo=0, grana=0, bordo=1.4, tono_bordo=0)
    inner = c.poligono([(x - w / 4, y)] + [(x - w / 4 + w / 2 * t, y - h * 0.55 * math.sin(math.pi * t) ** 1.4) for t in np.linspace(0, 1, 30)] + [(x + w / 4, y)])
    c.forma(inner, 130, rilievo=0, grana=12, bordo=0.8, tono_bordo=0)


def coltello(c, x, y, ang, lung=130):
    ca, sa = math.cos(ang), math.sin(ang)
    def p(u, v): return (x + u * ca - v * sa, y + u * sa + v * ca)
    manico = c.poligono([p(0, -7), p(lung * 0.42, -7), p(lung * 0.44, -9), p(lung * 0.44, 9), p(lung * 0.42, 7), p(0, 7)])
    c.forma(manico, 110, rilievo=3, luce=80, grana=14, bordo=1.2, tono_bordo=0)
    lama = c.poligono([p(lung * 0.44, -6), p(lung * 0.9, -6), p(lung, -1), p(lung * 0.92, 6), p(lung * 0.44, 6)])
    c.forma(lama, 235, rilievo=2, luce=60, grana=3, bordo=1.2, tono_bordo=0)
    c.tratto([p(lung * 0.46, 2), p(lung * 0.92, 3)], 0.8, 120)


def omino(c, x, y_terra, h=70):
    """una sagoma stilizzata, per dare le proporzioni"""
    r = h * 0.11
    c.forma(c.ellisse(x, y_terra - h + r, r, r), 250, rilievo=0, grana=0, bordo=1.4, tono_bordo=0)
    c.tratto([(x, y_terra - h + 2 * r), (x, y_terra - h * 0.45)], 2.2, 0)
    c.tratto([(x - h * 0.2, y_terra - h * 0.62), (x, y_terra - h * 0.72), (x + h * 0.2, y_terra - h * 0.62)], 2.2, 0)
    c.tratto([(x - h * 0.16, y_terra), (x, y_terra - h * 0.45), (x + h * 0.16, y_terra)], 2.2, 0)


def scritta_vento(c, x, y):
    for k in range(3):
        yy = y + k * 12
        c.tratto([(x, yy), (x + 30, yy), (x + 36, yy - 4), (x + 32, yy - 8)], 1.2, 30)
    freccia(c, [(x + 40, y + 12), (x + 70, y + 12)], 1.6, 0, 8)


# ============================================================================ la corda

def _catmull(ctrl, per=12):
    P = [ctrl[0]] + list(ctrl) + [ctrl[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for k in range(per):
            t = k / per
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in (0, 1)))
    out.append(ctrl[-1])
    return out


def _seg_x(a, b, c_, d):
    def cr(o, p, q): return (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0])
    d1, d2, d3, d4 = cr(c_, d, a), cr(c_, d, b), cr(a, b, c_), cr(a, b, d)
    if ((d1 > 0) != (d2 > 0)) and ((d3 > 0) != (d4 > 0)):
        t = d1 / (d1 - d2)
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    return None


def corda(c, ctrl, sopra=None, fino=1.0, larg=13, capo=0.62, freccia_dopo=None, mostra_incroci=False):
    """una corda che passa per i punti ctrl. sopra = {(x, y): 'primo' | 'secondo'}: in ogni incrocio
    vicino a (x, y), quale dei due passaggi (nell'ordine lungo la corda) sta sopra; gli incroci non
    elencati alternano. fino = la frazione di corda disegnata (i passi di un nodo). capo = da che
    frazione in poi la corda è il 'capo' (puntinato), il resto è la parte fissa (bianca).
    freccia_dopo = punti di una freccia che parte dalla fine della corda disegnata."""
    pts = _catmull(ctrl)
    n = len(pts)
    lung = [0.0]
    for i in range(1, n):
        lung.append(lung[-1] + math.dist(pts[i], pts[i - 1]))
    tot = lung[-1]
    # gli incroci, sulla corda intera
    incroci = []
    for i in range(n - 1):
        for j in range(i + 3, n - 1):
            q = _seg_x(pts[i], pts[i + 1], pts[j], pts[j + 1])
            if q:
                incroci.append((i, j, q))
    z = [0] * n
    win = max(3, int(larg * 1.6 / max(1e-6, tot / n)))
    # alternato lungo la corda: il primo incrocio che incontri passa sopra, il secondo sotto...
    eventi = sorted([(i, k, 0) for k, (i, j, q) in enumerate(incroci)] + [(j, k, 1) for k, (i, j, q) in enumerate(incroci)])
    alt = {}
    for pos, (_idx, k, quale) in enumerate(eventi):
        if quale == 0:
            alt[k] = 'primo' if pos % 2 == 0 else 'secondo'
    for k, (i, j, q) in enumerate(incroci):
        scelta = None
        if sopra:
            kk = min(sopra, key=lambda s: math.dist(s, q))
            if math.dist(kk, q) < 30:
                scelta = sopra[kk]
        if scelta is None:
            scelta = alt[k]
        alto, basso = (i, j) if scelta == 'primo' else (j, i)
        for k in range(max(0, alto - win), min(n, alto + win + 1)):
            z[k] = max(z[k], 1) if z[k] >= 0 else 1
        for k in range(max(0, basso - win), min(n, basso + win + 1)):
            if z[k] <= 0:
                z[k] = -1
    if mostra_incroci:
        for (_i, _j, q) in incroci:
            print('incrocio a', tuple(round(v) for v in q))
    # taglia a 'fino'
    fine = n
    for k in range(n):
        if lung[k] > tot * fino:
            fine = k
            break
    pts, z = pts[:fine], z[:fine]
    lung = lung[:fine]
    # pezzi con lo stesso z, disegnati dal più basso
    pezzi, inizio = [], 0
    for k in range(1, len(pts) + 1):
        if k == len(pts) or z[k] != z[inizio]:
            pezzi.append((z[inizio], max(0, inizio - 1), k))
            inizio = k
    for zz, a, b in sorted(pezzi, key=lambda t: t[0]):
        seg = pts[a:b]
        if len(seg) < 2:
            continue
        c.tratto(seg, larg, 0)
        c.tratto(seg, larg - 5, 255)
        # il capo puntinato
        cs = [p for p, L in zip(seg, lung[a:b]) if L >= tot * capo]
        if len(cs) > 1:
            c.tratto(cs, larg - 5, 175)
        # la torsione della corda
        for k in range(a + 1, b - 1, 3):
            (x0, y0), (x1, y1) = pts[k - 1], pts[k + 1]
            ang = math.atan2(y1 - y0, x1 - x0)
            h = (larg - 6) / 2
            cx, cy = pts[k]
            c.tratto([(cx + h * math.cos(ang + 1.2), cy + h * math.sin(ang + 1.2)),
                      (cx - h * math.cos(ang + 1.9), cy - h * math.sin(ang + 1.9))], 0.6, 90)
    # chiude la punta
    if len(pts) > 2:
        (x0, y0), (x1, y1) = pts[-2], pts[-1]
        ang = math.atan2(y1 - y0, x1 - x0)
        h = larg / 2
        c.tratto([(x1 + h * math.cos(ang + 1.5708), y1 + h * math.sin(ang + 1.5708)),
                  (x1 - h * math.cos(ang + 1.5708), y1 - h * math.sin(ang + 1.5708))], 1.4, 0)
        if freccia_dopo:
            freccia(c, freccia_dopo)
    return pts


def etichetta(c, x, y, testo_sotto=True):
    pass


# ============================================================================ lo zaino

def zaino(c):
    # sezione di uno zaino visto di lato, con le zone di carico
    cx = 150
    corpo = c.rett(cx - 70, 22, 140, 210, 26)
    c.forma(corpo, 240, rilievo=10, luce=40, grana=4, bordo=1.8, tono_bordo=0)
    zone = [(22, 60, 'cima', 205), (82, 80, 'centro', 120), (162, 70, 'fondo', 220)]
    for y, h, _n, tono in zone:
        m = c.rett(cx - 64, y + 4, 128, h - 4, 18) * corpo
        c.forma(m, tono, rilievo=0, grana=14, bordo=0)
        c.tratto([(cx - 66, y + h), (cx + 66, y + h)], 1.0, 0)
    # la schiena e gli spallacci
    c.forma(c.rett(cx - 92, 36, 18, 150, 9), 200, rilievo=3, luce=60, grana=8, bordo=1.3, tono_bordo=0)
    c.tratto([(cx - 90, 48), (cx - 110, 70), (cx - 100, 140), (cx - 84, 160)], 3.0, 0)
    c.forma(c.rett(cx - 98, 176, 30, 26, 8), 160, rilievo=3, grana=10, bordo=1.3, tono_bordo=0)
    # il baricentro
    c.forma(c.ellisse(cx - 30, 122, 9, 9), 0, rilievo=0, grana=0, bordo=0)
    freccia(c, [(cx - 30, 132), (cx - 30, 168)], 2.0, 0, 9)
    # le linee verso le etichette (le scritte le mette l'app)
    for y, h, _n, _t in zone:
        c.tratto([(cx + 70, y + h / 2), (cx + 110, y + h / 2)], 1.2, 0)
        c.forma(c.ellisse(cx + 112, y + h / 2, 3, 3), 0, rilievo=0, grana=0, bordo=0)


# ============================================================================ fuoco

def _cerchio_pietre(c, cx, cy, rx=95, ry=26, n=12, salta_davanti=False):
    for k in range(n):
        a = 2 * math.pi * k / n
        if salta_davanti and math.sin(a) > 0.5:
            continue
        pietra(c, cx + rx * math.cos(a), cy + ry * math.sin(a), 16, 9, 175)


def _focolare(c, cx=220, cy=190):
    m = c.ellisse(cx, cy, 90, 22)
    c.forma(m, 200, rilievo=0, grana=18, bordo=0)
    c.punti(m, 120, 0.8, 90)


def fuoco1(c):
    terreno(c, 200, ciuffi=18, seme=1)
    _focolare(c)
    # pietre dietro
    for k in range(12):
        a = 2 * math.pi * k / 12
        if math.sin(a) < 0:
            pietra(c, 220 + 100 * math.cos(a), 190 + 28 * math.sin(a), 17, 10, 175)
    for k in range(12):
        a = 2 * math.pi * k / 12
        if math.sin(a) >= 0:
            pietra(c, 220 + 100 * math.cos(a), 190 + 28 * math.sin(a), 17, 10, 175)
    # la distanza dalla tenda
    tenda = c.poligono([(338, 196), (382, 120), (430, 196)])
    c.forma(tenda, 210, rilievo=4, luce=60, grana=10, bordo=1.4, tono_bordo=0)
    c.tratto([(382, 120), (382, 196)], 1.0, 0)
    freccia(c, [(330, 150), (360, 150)], 1.6)
    freccia(c, [(330, 150), (300, 150)], 1.6)
    c.tratto([(330, 144), (330, 156)], 1.4, 0)


def _mucchio(c, x, y, tipo):
    import random
    r = random.Random(hash(tipo) % 100)
    if tipo == 'esca':
        m = c.blob(x, y - 14, 36, 18, 0.25, punti=24)
        c.forma(m, 200, rilievo=5, luce=70, grana=16, bordo=1.2, tono_bordo=0)
        for _ in range(40):
            a = r.uniform(0, 6.28)
            rr = r.uniform(4, 30)
            x0, y0 = x + rr * math.cos(a), y - 14 + rr * 0.5 * math.sin(a)
            c.tratto([(x0, y0), (x0 + r.uniform(-9, 9), y0 + r.uniform(-5, 5))], 0.7, 40)
    else:
        sp, L, n = (3, 70, 16) if tipo == 'legnetti' else (9, 100, 7)
        for k in range(n):
            yy = y - 4 - (k % 4) * sp * 0.9 - (k // 4) * sp * 0.7
            dx = r.uniform(-12, 12)
            legno(c, (x - L / 2 + dx, yy + r.uniform(-2, 2)), (x + L / 2 + dx, yy + r.uniform(-3, 3)), sp, 180, venature=sp > 5)


def fuoco2(c):
    terreno(c, 200, ciuffi=6, seme=2)
    _mucchio(c, 75, 198, 'esca')
    _mucchio(c, 210, 198, 'legnetti')
    _mucchio(c, 350, 198, 'legna')
    for x, w in ((75, 0), (210, 0), (350, 0)):
        c.tratto([(x, 205), (x, 228)], 1.0, 0)


def fuoco3(c):
    terreno(c, 200, ciuffi=4, seme=3)
    _focolare(c)
    for k in range(7):
        legno(c, (170, 196 - k * 1.2), (270, 192 - k * 1.2 + (k % 2) * 3), 7, 185, venature=False)
    m = c.blob(220, 178, 34, 16, 0.25, punti=24)
    c.forma(m, 205, rilievo=6, luce=80, grana=16, bordo=1.2, tono_bordo=0)
    import random
    r = random.Random(3)
    for _ in range(45):
        a = r.uniform(0, 6.28)
        rr = r.uniform(4, 30)
        x0, y0 = 220 + rr * math.cos(a), 178 + rr * 0.45 * math.sin(a)
        c.tratto([(x0, y0), (x0 + r.uniform(-8, 8), y0 + r.uniform(-4, 4))], 0.7, 40)
    c.forma(c.ellisse(220, 176, 12, 6), 120, rilievo=0, grana=12, bordo=1.0, tono_bordo=0)


def _tepee(c, cx=220, base=190, h=110, n=11, sp=5, secondo=True):
    import random
    r = random.Random(4)
    cima = (cx, base - h)
    for k in range(n):
        a = math.pi * (0.1 + 0.8 * k / (n - 1))
        if 0.42 * math.pi < a < 0.58 * math.pi:
            continue                               # la porta
        bx = cx - 60 * math.cos(a)
        by = base + 10 * math.sin(a) - 6
        legno(c, (bx, by), (cima[0] + r.uniform(-4, 4), cima[1] + r.uniform(-3, 6)), sp, 180, testa=False, venature=False)
    if secondo:
        for k in range(7):
            a = math.pi * (0.05 + 0.9 * k / 6)
            if 0.4 * math.pi < a < 0.6 * math.pi:
                continue
            bx = cx - 85 * math.cos(a)
            by = base + 14 * math.sin(a) - 4
            legno(c, (bx, by), (cima[0] + r.uniform(-5, 5), cima[1] + 12), 9, 165, testa=False)


def fuoco4(c):
    terreno(c, 200, ciuffi=4, seme=4)
    _focolare(c)
    m = c.blob(220, 182, 30, 13, 0.25)
    c.forma(m, 205, rilievo=4, grana=16, bordo=1.2, tono_bordo=0)
    _tepee(c)
    scritta_vento(c, 50, 70)
    freccia(c, curva((150, 120), (170, 170), (205, 178)), 1.6)


def fuoco5(c):
    terreno(c, 200, ciuffi=4, seme=5)
    _focolare(c)
    _tepee(c, secondo=False)
    # l'acciarino: barra e raschietto, scintille nel nido
    legno(c, (300, 120), (232, 172), 7, 60, testa=False, venature=False)
    c.forma(c.rett(330, 98, 46, 20, 6), 120, rilievo=2, grana=10, bordo=1.2, tono_bordo=0)
    legno(c, (300, 120), (330, 106), 12, 120, testa=False, venature=False)
    c.forma(c.poligono([(250, 140), (300, 150), (304, 160), (254, 152)]), 225, rilievo=0, grana=3, bordo=1.2, tono_bordo=0)
    import random
    r = random.Random(5)
    for _ in range(26):
        a = r.uniform(2.2, 3.6)
        L = r.uniform(10, 34)
        x0, y0 = 236, 168
        c.tratto([(x0 + 4 * math.cos(a), y0 + 4 * math.sin(a)), (x0 + L * math.cos(a), y0 + L * 0.6 * math.sin(a) + 8)], 1.0, 0)
    freccia(c, [(345, 135), (390, 112)], 1.8)


def fuoco6(c):
    terreno(c, 200, ciuffi=4, seme=6)
    _focolare(c)
    for k, (a, b) in enumerate([((150, 196), (230, 150)), ((290, 196), (215, 150)), ((180, 200), (250, 140)), ((265, 200), (195, 140))]):
        legno(c, a, b, 11, 150 + k * 8)
    fiamma(c, 220, 182, 120, 90)
    fiamma(c, 190, 186, 70, 40)
    fiamma(c, 252, 186, 80, 44)
    legno(c, (330, 120), (390, 104), 14, 165)
    freccia(c, curva((330, 100), (300, 80), (272, 112)), 1.8)


# ============================================================================ bastoncino piumato

def _bastone(c, x0, y0, x1, y1, sp=26, spaccato=False):
    p = legno(c, (x0, y0), (x1, y1), sp, 150)
    if spaccato:
        m = c.poligono([p(4, -2), p(math.dist((x0, y0), (x1, y1)) - 2, -2), p(math.dist((x0, y0), (x1, y1)) - 2, sp / 2 - 2), p(4, sp / 2 - 2)])
        c.forma(m, 235, rilievo=0, grana=6, bordo=0.8, tono_bordo=40)
        for k in range(8):
            u = 10 + k * (math.dist((x0, y0), (x1, y1)) - 20) / 8
            c.tratto([p(u, 2), p(u + 18, 4)], 0.6, 120)
    return p


def piuma1(c):
    terreno(c, 215, ciuffi=3, seme=7)
    # il ramo spaccato e il bastone che batte
    _bastone(c, 60, 140, 330, 140, 30, spaccato=True)
    coltello(c, 140, 120, 0.0, 150)
    legno(c, (300, 60), (230, 112), 16, 140)
    freccia(c, [(320, 40), (300, 70)], 1.8)
    for k in range(3):
        c.tratto([(232 + k * 8, 104 - k * 3), (238 + k * 8, 96 - k * 3)], 1.0, 0)


def piuma2(c):
    terreno(c, 215, ciuffi=3, seme=8)
    c.forma(c.rett(150, 196, 140, 22, 6), 160, rilievo=3, grana=12, bordo=1.3, tono_bordo=0)
    p = _bastone(c, 232, 30, 214, 200, 30, spaccato=False)
    coltello(c, 300, 40, 2.1, 140)
    freccia(c, [(300, 90), (264, 160)], 1.8)


def _riccioli(c, x, y_top, y_bot, n, lato=1):
    for k in range(n):
        yy = y_top + k * (y_bot - y_top) / max(1, n - 1)
        r = 6 + k * 0.6
        pts = [(x + lato * (6 + r * (1 - math.cos(t))), yy + 18 * t / 3.14 + r * 0.6 * math.sin(t)) for t in np.linspace(0, 3.4, 14)]
        c.tratto(pts, 2.2, 0)
        c.tratto(pts, 1.0, 245)


def piuma3(c):
    terreno(c, 215, ciuffi=3, seme=9)
    c.forma(c.rett(150, 196, 140, 22, 6), 160, rilievo=3, grana=12, bordo=1.3, tono_bordo=0)
    _bastone(c, 232, 30, 214, 200, 30)
    _riccioli(c, 230, 130, 175, 5)
    coltello(c, 300, 30, 2.0, 140)
    freccia(c, [(290, 80), (262, 130)], 1.8)


def piuma4(c):
    terreno(c, 215, ciuffi=3, seme=10)
    _bastone(c, 222, 20, 214, 205, 30)
    _riccioli(c, 226, 110, 190, 9, 1)
    _riccioli(c, 212, 120, 190, 7, -1)
    m = c.blob(320, 200, 38, 10, 0.3)
    c.forma(m, 180, rilievo=0, grana=14, bordo=1.0, tono_bordo=0)
    c.punti(m, 80, 0.8, 20)


# ============================================================================ riparo con il tarp

def _due_alberi(c):
    terreno(c, 215, ciuffi=10, seme=11)
    albero(c, 60)
    albero(c, 380)


def tarp1(c):
    _due_alberi(c)
    freccia(c, [(90, 235), (350, 235)], 1.4)
    freccia(c, [(350, 235), (90, 235)], 1.4)
    scritta_vento(c, 150, 40)


def _dorsale(c):
    corda(c, [(78, 120), (220, 126), (362, 120)], larg=7, capo=2)


def tarp2(c):
    _due_alberi(c)
    _dorsale(c)
    for x in (60, 380):
        c.forma(c.ellisse(x, 120, 22, 8), 255, rilievo=0, grana=0, bordo=0)
    corda(c, [(40, 116), (60, 112), (80, 118)], larg=7, capo=2)
    corda(c, [(360, 116), (380, 112), (400, 118)], larg=7, capo=2)
    freccia(c, [(250, 150), (330, 150)], 1.6)
    c.tratto([(220, 126), (220, 215)], 0.8, 0)


def _telo(c, alto_y=126, basso='terra', picchetti=False):
    # il telo visto di tre quarti: davanti la parte che scende
    if basso == 'terra':
        m = c.poligono([(90, alto_y), (350, alto_y), (390, 208), (60, 208)])
    else:
        m = c.poligono([(90, alto_y), (350, alto_y), (370, 168), (70, 168)])
    c.forma(m, 200, rilievo=3, luce=40, grana=10, bordo=1.4, tono_bordo=0)
    for x in np.linspace(110, 330, 7):
        c.tratto([(x, alto_y + 2), (x + (x - 220) * 0.15, (208 if basso == 'terra' else 168) - 2)], 0.6, 120)
    return m


def tarp3(c):
    _due_alberi(c)
    _dorsale(c)
    _telo(c, basso='appeso')
    for x in (92, 348):
        corda(c, [(x, 126), (x + (8 if x < 200 else -8), 118)], larg=5, capo=2)
    freccia(c, [(220, 190), (220, 212)], 1.6)


def _picchetto(c, x, y):
    legno(c, (x - 8, y - 22), (x + 4, y + 4), 6, 170, venature=False)


def tarp4(c):
    _due_alberi(c)
    _dorsale(c)
    _telo(c, basso='terra')
    for x in (62, 150, 220, 290, 388):
        _picchetto(c, x, 212)
    scritta_vento(c, 160, 30)


def tarp5(c):
    _due_alberi(c)
    _dorsale(c)
    _telo(c, basso='terra')
    for x in (62, 150, 220, 290, 388):
        _picchetto(c, x, 212)
    c.forma(c.rett(130, 198, 180, 12, 5), 120, rilievo=0, grana=12, bordo=1.1, tono_bordo=0)
    import random
    r = random.Random(12)
    for _ in range(24):
        x = r.uniform(135, 305)
        c.tratto([(x, 199), (x + r.uniform(-8, 8), 194)], 0.8, 30)


# ============================================================================ trovare il nord

def _nord_base(c):
    terreno(c, 120, ciuffi=0)
    m = c.rett(0, 120, W, 130)
    c.forma(m, 240, rilievo=0, grana=6, bordo=0)
    # il sole
    c.forma(c.ellisse(380, 36, 16, 16), 250, rilievo=0, grana=0, bordo=1.4, tono_bordo=0)
    for k in range(8):
        a = k * math.pi / 4
        c.tratto([(380 + 22 * math.cos(a), 36 + 22 * math.sin(a)), (380 + 30 * math.cos(a), 36 + 30 * math.sin(a))], 1.6, 0)


def _bastone_verticale(c, x=220, y=190):
    legno(c, (x, y), (x, y - 120), 8, 150, venature=False)


def _ombra(c, x0, y0, x1, y1):
    m = c.tratto_maschera([(x0, y0), (x1, y1)], 7)
    c.forma(m, 80, rilievo=0, grana=16, bordo=0)


def nord1(c):
    _nord_base(c)
    _bastone_verticale(c)
    _ombra(c, 220, 190, 140, 210)
    freccia(c, [(250, 60), (230, 80)], 1.6)


def nord2(c):
    _nord_base(c)
    _bastone_verticale(c)
    _ombra(c, 220, 190, 140, 210)
    pietra(c, 136, 212, 11, 7, 150)


def nord3(c):
    _nord_base(c)
    _bastone_verticale(c)
    pietra(c, 136, 212, 11, 7, 150)
    _ombra(c, 220, 190, 160, 228)
    pietra(c, 158, 230, 11, 7, 150)
    freccia(c, curva((130, 230), (130, 240), (148, 236)), 1.2, 0, 7)


def nord4(c):
    # vista dall'alto: i due sassi, la linea ovest-est e il nord
    m = c.rett(0, 0, W, H)
    c.forma(m, 238, rilievo=0, grana=8, bordo=0)
    pietra(c, 130, 170, 13, 10, 150)
    pietra(c, 310, 170, 13, 10, 150)
    c.tratto([(60, 170), (380, 170)], 1.6, 0)
    c.scritta(130, 200, '1')
    c.scritta(310, 200, '2')
    c.scritta(40, 170, 'O', 22)
    c.scritta(400, 170, 'E', 22)
    freccia(c, [(220, 170), (220, 50)], 3.0, 0, 16)
    c.scritta(220, 30, 'N', 26)
    c.scritta(220, 222, 'S', 22)
    c.tratto([(212, 170), (212, 162), (220, 162)], 1.2, 0)


# ============================================================================ nodi

OTTO = [(20, 160), (120, 160), (232, 160), (282, 148), (292, 102), (252, 70), (206, 84), (196, 124), (202, 166),
        (196, 200), (152, 214), (118, 188), (124, 140), (162, 108), (240, 112), (300, 120), (350, 128), (420, 116)]
OTTO_SOPRA = None
OTTO_CAPO = 0.36


def _otto(c, fino, freccia_dopo=None):
    corda(c, OTTO, sopra=OTTO_SOPRA, fino=fino, capo=OTTO_CAPO, freccia_dopo=freccia_dopo)


def otto1(c): _otto(c, 0.52, [(190, 210), (150, 222)])
def otto2(c): _otto(c, 0.70, [(140, 120), (186, 104)])
def otto3(c): _otto(c, 1.0)
def otto4(c):
    corda(c, [(20, 130), (150, 130), (186, 128), (212, 112), (232, 124), (220, 144), (196, 140), (206, 118), (236, 108),
              (250, 132), (226, 152), (206, 138), (240, 124), (300, 128), (420, 132)], fino=1.0, capo=0.55)
    freccia(c, [(80, 170), (30, 170)], 1.6)
    freccia(c, [(360, 170), (410, 170)], 1.6)


GASSA = [(230, 0), (230, 60), (232, 96), (226, 128), (202, 132), (190, 110), (204, 90), (240, 96), (262, 118),
         (268, 170), (250, 225), (200, 240), (160, 222), (160, 180), (176, 150), (206, 118), (214, 86), (222, 52),
         (240, 36), (258, 48), (248, 66), (226, 76), (220, 94), (219, 112), (217, 134), (216, 178)]
# chi sta sopra in ogni incrocio ('primo' = il passaggio che viene prima lungo la corda)
GASSA_SOPRA = {
    (232, 93): 'secondo',    # l'occhiello si chiude passando sopra la parte fissa
    (198, 128): 'primo',     # il coniglio esce dalla tana: sotto il bordo basso...
    (213, 89): 'secondo',    # ...e sopra il bordo alto
    (230, 42): 'primo',      # gira dietro l'albero
    (231, 73): 'secondo',    # e ripassa davanti
    (221, 90): 'secondo',    # torna nella tana: sopra il bordo alto...
    (217, 133): 'primo',     # ...e sotto il bordo basso
}


def _T(p):
    """la gassa è disegnata in verticale: la giro in orizzontale e la ingrandisco"""
    x, y = p
    return (440 - y * 1.75, 125 + (x - 225) * 1.6)


def _gassa(c, fino, freccia_dopo=None):
    corda(c, [_T(p) for p in GASSA], sopra={_T(k): v for k, v in GASSA_SOPRA.items()}, fino=fino, capo=0.56,
          larg=15, freccia_dopo=[_T(p) for p in freccia_dopo] if freccia_dopo else None)


def gassa1(c): _gassa(c, 0.64, [(170, 160), (192, 128)])
def gassa2(c): _gassa(c, 0.78, [(222, 40), (250, 28)])
def gassa3(c): _gassa(c, 0.87, [(236, 74), (222, 96)])
def gassa4(c): _gassa(c, 1.0)
def gassa5(c):
    _gassa(c, 1.0)
    freccia(c, [(380, 230), (430, 230)], 1.8)
    freccia(c, [(90, 230), (30, 230)], 1.8)


# ============================================================================ trappola a quattro

def _montante(c, x=200, y0=205, h=130):
    p = legno(c, (x, y0), (x, y0 - h), 10, 165, testa=False)
    return p


def quattro1(c):
    terreno(c, 228, ciuffi=0)
    legno(c, (40, 60), (190, 60), 10, 165)
    legno(c, (40, 120), (190, 120), 10, 165)
    legno(c, (40, 185), (250, 185), 10, 165)
    # la cima a scalpello del montante
    c.forma(c.poligono([(190, 55), (204, 60), (190, 65)]), 165, rilievo=0, grana=10, bordo=1.1, tono_bordo=0)
    for y, L in ((60, 150), (120, 150), (185, 210)):
        c.tratto([(40, y + 16), (40 + L, y + 16)], 0.8, 0)
        c.tratto([(40, y + 12), (40, y + 20)], 0.8, 0)
        c.tratto([(40 + L, y + 12), (40 + L, y + 20)], 0.8, 0)


def _tacca(c, x, y, w=10, d=5, sotto=True):
    s = 1 if sotto else -1
    c.forma(c.poligono([(x - w / 2, y), (x + w / 2, y), (x + w / 2, y + s * d), (x - w / 2, y + s * d)]), 255, rilievo=0, grana=0, bordo=0)
    c.tratto([(x - w / 2, y), (x - w / 2, y + s * d), (x + w / 2, y + s * d), (x + w / 2, y)], 1.0, 0)


def quattro2(c):
    terreno(c, 228, ciuffi=0)
    legno(c, (40, 70), (220, 70), 14, 165)
    _tacca(c, 70, 77, 12, 6, sotto=False)
    c.forma(c.poligono([(220, 63), (236, 70), (220, 77)]), 165, rilievo=0, grana=10, bordo=1.1, tono_bordo=0)
    legno(c, (40, 160), (290, 160), 14, 165)
    _tacca(c, 150, 153, 14, 6, sotto=True)
    _tacca(c, 278, 167, 10, 6, sotto=False)
    coltello(c, 300, 40, 2.6, 110)
    for x, y in ((70, 96), (150, 186), (278, 186)):
        freccia(c, [(x, y + 22), (x, y + 4)], 1.4, 0, 8)


def _quattro(c, esca=True):
    # il 4: il montante verticale, il braccio orizzontale agganciato a metà del montante con l'esca
    # sotto la pietra (a destra), l'asta diagonale appoggiata sulla cima del montante, con il capo
    # basso agganciato alla punta sinistra del braccio e il capo alto che regge la pietra
    terreno(c, 215, ciuffi=6, seme=13)
    _montante(c, 200, 214, 122)
    legno(c, (128, 150), (330, 150), 10, 170)              # il braccio
    legno(c, (132, 146), (262, 66), 10, 180)               # l'asta
    if esca:
        c.forma(c.blob(318, 143, 11, 7, 0.3), 110, rilievo=2, grana=14, bordo=1.0, tono_bordo=0)


def quattro3(c):
    _quattro(c)
    freccia(c, curva((370, 100), (350, 125), (330, 138)), 1.4, 0, 8)


def quattro4(c):
    _quattro(c)
    # la pietra: un bordo a terra a destra, l'altro appoggiato sul capo alto dell'asta
    m = c.poligono([(250, 58), (276, 50), (420, 206), (392, 216)])
    c.forma(m, 150, rilievo=8, luce=100, grana=16, bordo=1.5, tono_bordo=0)
    _quattro_sopra = None


def quattro5(c):
    terreno(c, 215, ciuffi=6, seme=13)
    legno(c, (100, 208), (180, 200), 10, 175)
    legno(c, (140, 196), (210, 170), 10, 165)
    m = c.poligono([(180, 196), (420, 196), (424, 214), (176, 214)])
    c.forma(m, 150, rilievo=6, luce=80, grana=16, bordo=1.5, tono_bordo=0)
    freccia(c, curva((260, 40), (330, 60), (360, 170)), 2.0)
    for x in (300, 340):
        c.tratto([(x - 20, 70), (x, 140)], 1.0, 0)


# ============================================================================ tipi di fuoco

def tipi1(c):
    terreno(c, 200, ciuffi=4, seme=21)
    _focolare(c)
    _tepee(c)
    fiamma(c, 220, 186, 46, 30)


def tipi2(c):
    terreno(c, 205, ciuffi=4, seme=22)
    _focolare(c, 220, 196)
    # la capanna di tronchi: livelli alternati (tronchi di fronte e teste dei tronchi di lato)
    for liv in range(5):
        y = 192 - liv * 17
        if liv % 2 == 0:
            legno(c, (140, y), (300, y - 2), 14, 160 + liv * 6)
        else:
            for x in (150, 290):
                m = c.ellisse(x, y, 8, 8)
                c.forma(m, 215, rilievo=2, grana=8, bordo=1.2, tono_bordo=0)
                c.tratto([(x - 3, y), (x + 3, y)], 0.6, 90)
    fiamma(c, 220, 175, 70, 50)


def tipi3(c):
    terreno(c, 150, ciuffi=0, seme=23)
    m = c.rett(0, 150, W, 100)
    c.forma(m, 215, rilievo=0, grana=10, bordo=0)
    cx, cy = 220, 190
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5 + 0.3
        x0, y0 = cx + 190 * math.cos(a), cy + 55 * math.sin(a)
        legno(c, (x0, y0), (cx + 18 * math.cos(a), cy + 6 * math.sin(a)), 16, 150 + k * 10)
    fiamma(c, cx, cy + 4, 80, 56)
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5 + 0.3
        x0, y0 = cx + 150 * math.cos(a), cy + 44 * math.sin(a)
        freccia(c, [(x0, y0 - 10), (cx + 70 * math.cos(a), cy + 20 * math.sin(a) - 10)], 1.2, 0, 7)


# ============================================================================ fuoco Dakota (in sezione)

def _terra_sezione(c, y=95):
    terreno(c, y, ciuffi=12, seme=24)
    m = c.rett(0, y, W, H - y)
    c.forma(m, 175, rilievo=0, grana=18, bordo=0)
    c.punti(m, 120, 1.2, 60)
    c.tratto([(0, y), (W, y)], 1.8, 0)


def _buca(c, x0, x1, y0, y1):
    m = c.rett(x0, y0, x1 - x0, y1 - y0, 6)
    c.forma(m, 252, rilievo=0, grana=0, bordo=1.4, tono_bordo=0)


def dakota1(c):
    _terra_sezione(c)
    _buca(c, 250, 330, 92, 210)
    zolla = c.rett(70, 74, 70, 20, 3)
    c.forma(zolla, 150, rilievo=2, grana=14, bordo=1.2, tono_bordo=0)
    for x in range(74, 138, 7):
        c.tratto([(x, 74), (x + 2, 66)], 0.8, 0)
    freccia(c, curva((300, 60), (200, 30), (120, 66)), 1.4)
    c.tratto([(340, 92), (340, 210)], 0.8, 0)
    c.tratto([(336, 92), (344, 92)], 0.8, 0)
    c.tratto([(336, 210), (344, 210)], 0.8, 0)


def _dakota_base(c):
    _terra_sezione(c)
    _buca(c, 250, 330, 92, 210)
    tunnel = c.poligono([(120, 95), (160, 95), (262, 200), (252, 210), (240, 205)])
    c.forma(tunnel, 252, rilievo=0, grana=0, bordo=1.4, tono_bordo=0)
    c.forma(c.rett(252, 96, 76, 112, 5), 252, rilievo=0, grana=0, bordo=0)


def dakota2(c):
    _dakota_base(c)
    scritta_vento(c, 20, 50)
    freccia(c, [(130, 102), (220, 192)], 1.6)


def dakota3(c):
    _dakota_base(c)
    for k in range(4):
        legno(c, (262 + k * 14, 206), (270 + k * 10, 186), 6, 150, testa=False, venature=False)
    fiamma(c, 290, 200, 100, 56)
    legno(c, (230, 92), (350, 92), 9, 140)
    legno(c, (232, 82), (352, 82), 9, 150)
    pot = c.rett(258, 40, 64, 40, 6)
    c.forma(pot, 205, rilievo=6, luce=90, grana=8, bordo=1.4, tono_bordo=0)
    c.tratto([(258, 44), (322, 44)], 1.0, 0)
    for k in range(3):
        freccia(c, [(132 + k * 10, 110 + k * 4), (230 + k * 6, 196)], 1.0, 0, 6)


# ============================================================================ archetto

def _tavoletta(c, x, y, w=200):
    m = c.rett(x, y, w, 20, 3)
    c.forma(m, 185, rilievo=3, luce=80, grana=12, bordo=1.3, tono_bordo=0)
    c.forma(c.ellisse(x + 40, y + 4, 9, 4), 40, rilievo=0, grana=0, bordo=0)
    c.forma(c.poligono([(x + 32, y + 20), (x + 40, y + 6), (x + 48, y + 20)]), 30, rilievo=0, grana=0, bordo=0)


def archetto1(c):
    _tavoletta(c, 30, 190)
    legno(c, (270, 200), (420, 200), 14, 175)
    c.forma(c.poligono([(420, 193), (432, 200), (420, 207)]), 175, rilievo=0, grana=8, bordo=1.0, tono_bordo=0)
    arco = [(40 + t * 360, 120 - 40 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 30)]
    m = c.tratto_maschera(arco, 9)
    c.forma(m, 160, rilievo=2, grana=10, bordo=1.0, tono_bordo=0)
    c.tratto([(42, 120), (220, 106), (398, 120)], 1.4, 0)
    pietra(c, 220, 40, 40, 20, 160)
    c.forma(c.ellisse(220, 46, 9, 5), 60, rilievo=0, grana=0, bordo=0)


def archetto2(c):
    terreno(c, 215, ciuffi=4, seme=25)
    _tavoletta(c, 110, 196, 220)
    # il piede sulla tavoletta
    c.forma(c.rett(170, 176, 70, 22, 8), 120, rilievo=4, luce=80, grana=12, bordo=1.3, tono_bordo=0)
    legno(c, (150, 200), (150, 56), 13, 180, testa=False, venature=False)
    pietra(c, 150, 46, 30, 14, 160)
    # l'archetto con il cordino avvolto
    arco = [(20 + t * 380, 120 - 28 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 30)]
    m = c.tratto_maschera(arco, 9)
    c.forma(m, 160, rilievo=2, grana=10, bordo=1.0, tono_bordo=0)
    c.tratto([(22, 122), (142, 128), (158, 124), (142, 118), (398, 120)], 1.4, 0)
    freccia(c, [(250, 160), (330, 160)], 1.8)
    freccia(c, [(250, 160), (170, 160)], 1.8)


# ============================================================================ batoning

def _ceppo_e_pezzo(c):
    terreno(c, 220, ciuffi=6, seme=26)
    m = c.rett(110, 180, 220, 42, 6)
    c.forma(m, 165, rilievo=4, luce=80, grana=14, bordo=1.4, tono_bordo=0)
    c.forma(c.ellisse(220, 180, 110, 12), 220, rilievo=0, grana=8, bordo=1.2, tono_bordo=0)
    pezzo = c.rett(190, 80, 60, 100, 4)
    c.forma(pezzo, 175, rilievo=4, luce=90, grana=14, bordo=1.4, tono_bordo=0)
    c.forma(c.ellisse(220, 80, 30, 7), 225, rilievo=0, grana=6, bordo=1.2, tono_bordo=0)


def baton1(c):
    _ceppo_e_pezzo(c)
    coltello(c, 90, 76, 0.0, 200)


def baton2(c):
    _ceppo_e_pezzo(c)
    coltello(c, 90, 96, 0.0, 200)
    c.tratto([(218, 104), (222, 130), (216, 160), (220, 178)], 1.6, 0)
    legno(c, (330, 20), (282, 84), 18, 140)
    freccia(c, [(350, 40), (318, 84)], 1.8)


# ============================================================================ gancio per il pentolino

def gancio1(c):
    terreno(c, 215, ciuffi=6, seme=27)
    _focolare(c, 220, 206)
    for x in (90, 350):
        legno(c, (x, 214), (x, 60), 10, 165, testa=False)
        legno(c, (x, 72), (x - 16, 48), 8, 165, testa=False, venature=False)
        legno(c, (x, 72), (x + 16, 48), 8, 165, testa=False, venature=False)
    legno(c, (70, 62), (372, 62), 10, 150)
    legno(c, (220, 62), (220, 140), 8, 170, testa=False, venature=False)
    legno(c, (220, 132), (234, 120), 6, 170, testa=False, venature=False)
    c.tratto([(200, 160), (220, 126), (240, 160)], 1.6, 0)
    pot = c.rett(188, 156, 64, 40, 6)
    c.forma(pot, 205, rilievo=6, luce=90, grana=8, bordo=1.4, tono_bordo=0)
    fiamma(c, 220, 212, 30, 50)


# ============================================================================ legature

def _pali_quadra(c):
    legno(c, (220, 245), (220, 5), 22, 175, testa=False)
    legno(c, (60, 130), (380, 130), 20, 165)


def _fascia(c, p0, p1, larg=7):
    corda(c, [p0, p1], larg=larg, capo=2)


def quadra1(c):
    _pali_quadra(c)
    for k in range(2):
        _fascia(c, (204, 152 + k * 8), (236, 154 + k * 8))
    corda(c, [(236, 156), (260, 170), (300, 200), (330, 240)], larg=7, capo=2)
    freccia(c, curva((300, 130), (330, 100), (300, 80)), 1.4)


def quadra2(c):
    _pali_quadra(c)
    for k in range(3):
        _fascia(c, (198 - k * 7, 112), (198 - k * 7, 148))
        _fascia(c, (242 + k * 7, 112), (242 + k * 7, 148))
        _fascia(c, (206, 108 - k * 7), (234, 108 - k * 7))
        _fascia(c, (206, 152 + k * 7), (234, 152 + k * 7))


def quadra3(c):
    quadra2(c)
    for (x, y) in ((206, 114), (234, 114), (206, 146), (234, 146)):
        _fascia(c, (x - 6, y - 6), (x + 6, y + 6), 6)
    freccia(c, curva((300, 60), (270, 70), (250, 100)), 1.4)


def treppiede1(c):
    terreno(c, 220, ciuffi=6, seme=28)
    _focolare(c, 220, 212)
    for (x0, x1) in ((80, 228), (360, 212), (220, 220)):
        legno(c, (x0, 222), (x1, 22), 12, 165, testa=False)
    for k in range(4):
        _fascia(c, (206, 46 + k * 6), (234, 46 + k * 6), 6)
    c.tratto([(220, 70), (220, 140)], 1.4, 0)
    pot = c.rett(188, 150, 64, 40, 6)
    c.tratto([(196, 154), (220, 138), (244, 154)], 1.4, 0)
    c.forma(pot, 205, rilievo=6, luce=90, grana=8, bordo=1.4, tono_bordo=0)
    fiamma(c, 220, 214, 26, 46)


# ============================================================================ nassa

def nassa1(c):
    # l'acqua
    m = c.rett(0, 60, W, 190)
    c.forma(m, 225, rilievo=0, grana=8, bordo=0)
    for k in range(5):
        y = 70 + k * 10
        c.tratto([(x, y + 3 * math.sin(x / 15 + k)) for x in np.linspace(10, 430, 40)], 0.8, 90)
    sabbia = c.rett(0, 210, W, 40)
    c.forma(sabbia, 170, rilievo=0, grana=16, bordo=0)
    c.tratto([(0, 210), (W, 210)], 1.2, 0)
    # la bottiglia orizzontale, imbuto a sinistra (verso valle)
    corpo = c.rett(110, 120, 220, 80, 30)
    c.forma(corpo, 245, rilievo=4, luce=40, grana=3, bordo=1.6, tono_bordo=0)
    c.tratto([(112, 124), (180, 152), (196, 156), (196, 164), (180, 168), (112, 196)], 1.6, 0)
    for x in range(220, 320, 18):
        for y in (140, 178):
            c.forma(c.ellisse(x, y, 2.5, 2.5), 0, rilievo=0, grana=0, bordo=0)
    c.forma(c.blob(290, 186, 10, 5, 0.3), 110, rilievo=0, grana=12, bordo=1.0, tono_bordo=0)
    for x in (100, 340):
        pietra(c, x, 206, 24, 12, 150)
    freccia(c, [(40, 160), (100, 160)], 1.8)


# ============================================================================ i montaggi del tarp

def _alberello_dietro(c, x=220):
    m = c.poligono([(x - 14, 0), (x + 14, 0), (x + 18, 215), (x - 18, 215)])
    c.forma(m, 165, rilievo=6, luce=100, grana=14, bordo=1.2, tono_bordo=0)


def tarpa1(c):
    terreno(c, 215, ciuffi=8, seme=29)
    _alberello_dietro(c)
    c.forma(c.ellisse(220, 70, 6, 6), 0, rilievo=0, grana=0, bordo=0)
    m = c.poligono([(220, 70), (196, 196), (244, 196)])
    c.forma(m, 200, rilievo=3, luce=40, grana=10, bordo=1.5, tono_bordo=0)
    freccia(c, [(190, 170), (110, 205)], 1.6)
    freccia(c, [(250, 170), (330, 205)], 1.6)


def tarpa2(c):
    terreno(c, 215, ciuffi=8, seme=30)
    _alberello_dietro(c)
    m = c.poligono([(220, 68), (90, 212), (350, 212)])
    c.forma(m, 200, rilievo=3, luce=40, grana=10, bordo=1.5, tono_bordo=0)
    c.tratto([(220, 68), (220, 212)], 0.8, 90)
    c.forma(c.ellisse(220, 68, 6, 6), 0, rilievo=0, grana=0, bordo=0)
    for x in (86, 354):
        _picchetto(c, x, 214)
    for (a, b) in (((90, 212), (40, 214)), ((350, 212), (400, 214))):
        c.tratto([a, b], 1.0, 0)
        _picchetto(c, b[0], 216)
    omino(c, 220, 214, 46) if False else None


def diam1(c):
    terreno(c, 215, ciuffi=8, seme=31)
    albero(c, 60)
    m = c.poligono([(80, 110), (150, 120), (170, 214), (100, 200)])
    c.forma(m, 200, rilievo=3, luce=40, grana=10, bordo=1.5, tono_bordo=0)
    corda(c, [(78, 110), (60, 104), (44, 110)], larg=6, capo=2)
    freccia(c, curva((180, 200), (280, 180), (380, 208)), 1.6)


def diam2(c):
    terreno(c, 215, ciuffi=8, seme=32)
    albero(c, 60)
    retro = c.poligono([(80, 110), (300, 150), (390, 210)])
    c.forma(retro, 150, rilievo=0, grana=12, bordo=1.2, tono_bordo=0)
    fronte = c.poligono([(80, 110), (210, 212), (390, 210)])
    c.forma(fronte, 205, rilievo=3, luce=40, grana=10, bordo=1.5, tono_bordo=0)
    corda(c, [(78, 110), (60, 104), (44, 110)], larg=6, capo=2)
    c.tratto([(210, 212), (180, 216)], 1.0, 0)
    _picchetto(c, 176, 216)
    _picchetto(c, 394, 214)


def vomere1(c):
    terreno(c, 215, ciuffi=8, seme=33)
    albero(c, 70)
    m = c.poligono([(92, 80), (160, 100), (140, 212), (100, 200)])
    c.forma(m, 200, rilievo=3, luce=40, grana=10, bordo=1.5, tono_bordo=0)
    corda(c, [(90, 80), (70, 74), (54, 80)], larg=6, capo=2)
    freccia(c, curva((170, 190), (240, 170), (300, 210)), 1.6)


def vomere2(c):
    terreno(c, 215, ciuffi=8, seme=34)
    albero(c, 70)
    retro = c.poligono([(92, 80), (380, 212), (150, 212)])
    c.forma(retro, 150, rilievo=0, grana=12, bordo=1.2, tono_bordo=0)
    fronte = c.poligono([(92, 80), (260, 214), (380, 212)])
    c.forma(fronte, 205, rilievo=3, luce=40, grana=10, bordo=1.5, tono_bordo=0)
    corda(c, [(90, 80), (70, 74), (54, 80)], larg=6, capo=2)
    for x in (150, 260, 384):
        _picchetto(c, x, 216)


def cucina1(c):
    _due_alberi(c)
    corda(c, [(78, 50), (220, 56), (362, 50)], larg=7, capo=2)
    m = c.poligono([(90, 56), (350, 56), (392, 150), (48, 150)])
    c.forma(m, 205, rilievo=3, luce=40, grana=10, bordo=1.5, tono_bordo=0)
    for x in (48, 392):
        c.tratto([(x, 150), (x + (30 if x > 200 else -30), 214)], 1.0, 0)
        _picchetto(c, x + (30 if x > 200 else -30), 216)
    omino(c, 220, 214, 90)


# le scene piccole si ingrandiscono ritagliando la tela (x, y, larghezza, altezza; 440:250)
ZOOM = {'tipi1': (40, 50, 352, 200), 'fuoco1': (60, 40, 352, 200), 'fuoco3': (100, 110, 246, 140), 'fuoco4': (40, 50, 352, 200),
        'fuoco5': (60, 50, 352, 200), 'fuoco6': (60, 45, 352, 200)}

DISEGNI = {
    'zaino': zaino,
    'fuoco1': fuoco1, 'fuoco2': fuoco2, 'fuoco3': fuoco3, 'fuoco4': fuoco4, 'fuoco5': fuoco5, 'fuoco6': fuoco6,
    'piuma1': piuma1, 'piuma2': piuma2, 'piuma3': piuma3, 'piuma4': piuma4,
    'tarp1': tarp1, 'tarp2': tarp2, 'tarp3': tarp3, 'tarp4': tarp4, 'tarp5': tarp5,
    'nord1': nord1, 'nord2': nord2, 'nord3': nord3, 'nord4': nord4,
    'otto1': otto1, 'otto2': otto2, 'otto3': otto3, 'otto4': otto4,
    'gassa1': gassa1, 'gassa2': gassa2, 'gassa3': gassa3, 'gassa4': gassa4, 'gassa5': gassa5,
    'tipi1': tipi1, 'tipi2': tipi2, 'tipi3': tipi3, 'dakota1': dakota1, 'dakota2': dakota2, 'dakota3': dakota3,
    'archetto1': archetto1, 'archetto2': archetto2, 'baton1': baton1, 'baton2': baton2, 'gancio1': gancio1,
    'quadra1': quadra1, 'quadra2': quadra2, 'quadra3': quadra3, 'treppiede1': treppiede1, 'nassa1': nassa1,
    'tarpa1': tarpa1, 'tarpa2': tarpa2, 'diam1': diam1, 'diam2': diam2, 'vomere1': vomere1, 'vomere2': vomere2,
    'cucina1': cucina1,
    'quattro1': quattro1, 'quattro2': quattro2, 'quattro3': quattro3, 'quattro4': quattro4, 'quattro5': quattro5,
}
