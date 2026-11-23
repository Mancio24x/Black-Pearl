"""Kit per disegnare i passi dei tutorial come SVG (440 x 250), nello stile dei disegni a 1 bit di Bivacco.

I disegni SVG sono la fonte: si vedono e si commentano su Claude Design (tools/design_canvas.py) e
tools/rendi_svg.js li trasforma in PNG; make_art.py li riduce a 1 bit con il dithering di Atkinson.
I grigi diventano puntinato: usali come toni (#fff bianco, CHIARO, MEDIO, SCURO), il contorno è nero.

    s = Scena()
    s.terra(215)
    s.albero(60)
    s.freccia([(100, 100), (200, 120)])
    s.svg()   # -> stringa
"""
import math

W, H = 440, 250
CHIARO, MEDIO, SCURO, NERO = '#c4c4c4', '#a0a0a0', '#5c5c5c', '#000'
TRATTO = 2.2


def _f(v):
    return f'{v:.1f}'.rstrip('0').rstrip('.')


def _pts(pts):
    return ' '.join(f'{_f(x)},{_f(y)}' for x, y in pts)


class Scena:
    def __init__(self):
        self.el = []

    def svg(self, titolo=''):
        t = f'<title>{titolo}</title>' if titolo else ''
        return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" '
                f'role="img" aria-label="{titolo}">{t}<rect width="{W}" height="{H}" fill="#fff"/>'
                + ''.join(self.el) + '</svg>')

    # ------------------------------------------------------------ primitive

    def poly(self, pts, fill='#fff', sw=TRATTO, chiuso=True, stroke=NERO):
        tag = 'polygon' if chiuso else 'polyline'
        f = fill if chiuso else 'none'
        self.el.append(f'<{tag} points="{_pts(pts)}" fill="{f}" stroke="{stroke}" stroke-width="{_f(sw)}" '
                       f'stroke-linejoin="round" stroke-linecap="round"/>')

    def linea(self, pts, sw=TRATTO, col=NERO, tratteggio=None):
        d = f' stroke-dasharray="{tratteggio}"' if tratteggio else ''
        self.el.append(f'<polyline points="{_pts(pts)}" fill="none" stroke="{col}" stroke-width="{_f(sw)}" '
                       f'stroke-linejoin="round" stroke-linecap="round"{d}/>')

    def curva(self, p0, p1, p2, sw=TRATTO, col=NERO, n=24):
        self.linea(bezier(p0, p1, p2, n), sw, col)

    def ellisse(self, cx, cy, rx, ry, fill='#fff', sw=TRATTO):
        self.el.append(f'<ellipse cx="{_f(cx)}" cy="{_f(cy)}" rx="{_f(rx)}" ry="{_f(ry)}" fill="{fill}" '
                       f'stroke="{NERO if sw else "none"}" stroke-width="{_f(sw)}"/>')

    def rett(self, x, y, w, h, fill='#fff', r=0, sw=TRATTO):
        self.el.append(f'<rect x="{_f(x)}" y="{_f(y)}" width="{_f(w)}" height="{_f(h)}" rx="{_f(r)}" fill="{fill}" '
                       f'stroke="{NERO if sw else "none"}" stroke-width="{_f(sw)}"/>')

    def testo(self, x, y, s, size=22, peso=900):
        self.el.append(f'<text x="{_f(x)}" y="{_f(y)}" font-family="Inter, Arial, sans-serif" font-size="{size}" '
                       f'font-weight="{peso}" text-anchor="middle" dominant-baseline="middle">{s}</text>')

    # ------------------------------------------------------------ oggetti

    def terra(self, y=215, ciuffi=8, seme=1, tono=CHIARO):
        self.rett(-2, y, W + 4, H - y + 2, tono, sw=0)
        self.linea([(0, y), (W, y)], 2)
        r = _Rnd(seme)
        for _ in range(ciuffi):
            x = r.u(12, W - 12)
            for k in (-1, 0, 1):
                self.linea([(x, y), (x + k * 5, y - r.u(6, 11))], 1.3)

    def albero(self, x, larg=34, y=215, tono=MEDIO):
        self.poly([(x - larg / 2, -4), (x + larg / 2, -4), (x + larg / 2 + 4, y), (x - larg / 2 - 4, y)], tono)
        r = _Rnd(int(x))
        for _ in range(9):
            yy, xx = r.u(6, y - 30), x + r.u(-larg / 2 + 5, larg / 2 - 5)
            self.linea([(xx, yy), (xx, yy + r.u(12, 26))], 1.2)

    def legno(self, p0, p1, sp=9, tono=CHIARO, testa=True):
        (x0, y0), (x1, y1) = p0, p1
        L = math.hypot(x1 - x0, y1 - y0)
        a = math.atan2(y1 - y0, x1 - x0)
        ca, sa = math.cos(a), math.sin(a)
        def p(u, v): return (x0 + u * ca - v * sa, y0 + u * sa + v * ca)
        self.poly([p(0, -sp / 2), p(L, -sp / 2), p(L, sp / 2), p(0, sp / 2)], tono, 1.8)
        if L > 40:
            for k in range(1, int(L / 30)):
                u = k * 30 - 8
                self.linea([p(u, -sp / 6), p(u + 14, -sp / 6)], 0.9)
        if testa:
            cx, cy = p(L, 0)
            self.el.append(f'<ellipse cx="{_f(cx)}" cy="{_f(cy)}" rx="{_f(max(1.5, sp * 0.22))}" ry="{_f(sp / 2)}" '
                           f'transform="rotate({_f(math.degrees(a))} {_f(cx)} {_f(cy)})" fill="#fff" stroke="#000" stroke-width="1.6"/>')
        return p

    def picchetto(self, x, y):
        self.legno((x - 8, y - 22), (x + 4, y + 4), 6, '#fff', testa=False)

    def sasso(self, x, y, rx, ry, tono=MEDIO):
        r = _Rnd(int(x * 3 + y))
        pts = []
        for k in range(16):
            t = 2 * math.pi * k / 16
            f = 1 + r.u(-0.12, 0.12)
            pts.append((x + rx * f * math.cos(t), y + ry * f * math.sin(t)))
        self.poly(pts, tono)

    def freccia(self, pts, sw=2.6, punta=11):
        self.linea(pts, sw)
        (xa, ya), (xb, yb) = pts[-2], pts[-1]
        a = math.atan2(yb - ya, xb - xa)
        self.poly([(xb + punta * 0.3 * math.cos(a), yb + punta * 0.3 * math.sin(a)),
                   (xb - punta * math.cos(a - 0.45), yb - punta * math.sin(a - 0.45)),
                   (xb - punta * math.cos(a + 0.45), yb - punta * math.sin(a + 0.45))], NERO, 1)

    def freccia_curva(self, p0, p1, p2, sw=2.6):
        self.freccia(bezier(p0, p1, p2, 20), sw)

    def fiamma(self, x, y, h, w=None):
        w = w or h * 0.6
        fuori = [(x - w / 2, y)] + [(x - w / 2 + w * t, y - h * math.sin(math.pi * t) ** 1.6 * (1 - 0.25 * math.sin(6 * t) ** 2))
                                     for t in [k / 30 for k in range(31)]] + [(x + w / 2, y)]
        self.poly(fuori, '#fff')
        dentro = [(x - w / 4, y)] + [(x - w / 4 + w / 2 * t, y - h * 0.55 * math.sin(math.pi * t) ** 1.4) for t in [k / 20 for k in range(21)]]
        self.poly(dentro, MEDIO, 1.2)

    def fuoco(self, x, y, h=40):
        for k in range(3):
            self.legno((x - 30 + k * 8, y + 2), (x + 26 - k * 6, y - 8 + k * 3), 7, CHIARO, testa=False)
        self.fiamma(x, y - 2, h, h * 0.8)

    def pentola(self, x, y, w=64, h=40):
        self.rett(x - w / 2, y, w, h, CHIARO, 6)
        self.linea([(x - w / 2, y + 5), (x + w / 2, y + 5)], 1.4)
        self.curva((x - w / 2 + 6, y), (x, y - 24), (x + w / 2 - 6, y), 1.6)

    def coltello(self, x, y, ang, lung=120):
        ca, sa = math.cos(ang), math.sin(ang)
        def p(u, v): return (x + u * ca - v * sa, y + u * sa + v * ca)
        self.poly([p(0, -6), p(lung * 0.42, -6), p(lung * 0.44, -8), p(lung * 0.44, 8), p(lung * 0.42, 6), p(0, 6)], SCURO)
        self.poly([p(lung * 0.44, -5), p(lung * 0.9, -5), p(lung, 0), p(lung * 0.92, 5), p(lung * 0.44, 5)], '#fff')

    def omino(self, x, y, h=80, posa='piedi'):
        r = h * 0.11
        if posa == 'seduto':
            self.ellisse(x, y - h * 0.72, r, r, '#fff')
            self.linea([(x, y - h * 0.72 + r), (x, y - h * 0.3)])
            self.linea([(x, y - h * 0.3), (x + h * 0.25, y - h * 0.3), (x + h * 0.25, y)])
            self.linea([(x - h * 0.15, y - h * 0.45), (x, y - h * 0.55), (x + h * 0.18, y - h * 0.4)])
            return
        self.ellisse(x, y - h + r, r, r, '#fff')
        self.linea([(x, y - h + 2 * r), (x, y - h * 0.45)])
        if posa == 'braccia_y':
            self.linea([(x - h * 0.25, y - h * 1.0), (x, y - h * 0.72), (x + h * 0.25, y - h * 1.0)])
        elif posa == 'indica':
            self.linea([(x - h * 0.15, y - h * 0.55), (x, y - h * 0.72), (x + h * 0.32, y - h * 0.8)])
        else:
            self.linea([(x - h * 0.2, y - h * 0.55), (x, y - h * 0.72), (x + h * 0.2, y - h * 0.55)])
        self.linea([(x - h * 0.16, y), (x, y - h * 0.45), (x + h * 0.16, y)])

    def acqua(self, y0, y1, onde=4, tono='#ececec'):
        self.rett(-2, y0, W + 4, y1 - y0, tono, sw=0)
        for k in range(onde):
            yy = y0 + 8 + k * (y1 - y0 - 10) / max(1, onde)
            self.linea([(x, yy + 3 * math.sin(x / 14 + k)) for x in range(0, W + 1, 10)], 1.1)

    def telo(self, pts, tono=CHIARO):
        self.poly(pts, tono, 2.4)

    def nuvola(self, x, y, s=1.0):
        for dx, dy, r in ((0, 0, 18), (20, -8, 22), (44, 0, 18), (22, 6, 18)):
            self.ellisse(x + dx * s, y + dy * s, r * s, r * 0.8 * s, '#fff', 1.8)
        self.rett(x - 4 * s, y + 2 * s, 52 * s, 14 * s, '#fff', sw=0)

    def pioggia(self, x0, x1, y0, y1, n=14):
        r = _Rnd(int(x0 + y0))
        for _ in range(n):
            x, y = r.u(x0, x1), r.u(y0, y1)
            self.linea([(x, y), (x - 4, y + 12)], 1.4)

    def sole(self, x, y, r=16):
        self.ellisse(x, y, r, r, '#fff')
        for k in range(8):
            a = k * math.pi / 4
            self.linea([(x + (r + 6) * math.cos(a), y + (r + 6) * math.sin(a)), (x + (r + 13) * math.cos(a), y + (r + 13) * math.sin(a))], 2)

    def segno_ok(self, x, y, s=1.0):
        self.linea([(x - 12 * s, y), (x - 3 * s, y + 9 * s), (x + 14 * s, y - 10 * s)], 4 * s)

    def segno_no(self, x, y, s=1.0):
        self.linea([(x - 10 * s, y - 10 * s), (x + 10 * s, y + 10 * s)], 4 * s)
        self.linea([(x + 10 * s, y - 10 * s), (x - 10 * s, y + 10 * s)], 4 * s)

    # ------------------------------------------------------------ la corda (con incroci sopra e sotto)

    def corda(self, ctrl, sopra=None, fino=1.0, larg=12, capo=0.6, dopo=None, chiusa=False):
        """come disegni.corda: incroci alternati lungo la corda, tranne quelli in sopra={(x,y): 'primo'|'secondo'};
        fino = frazione disegnata, capo = da dove la corda è il capo (grigio), dopo = una freccia dalla fine"""
        pts = _catmull(ctrl)
        n = len(pts)
        lung = [0.0]
        for i in range(1, n):
            lung.append(lung[-1] + math.dist(pts[i], pts[i - 1]))
        tot = lung[-1]
        inc = []
        for i in range(n - 1):
            for j in range(i + 3, n - 1):
                q = _seg_x(pts[i], pts[i + 1], pts[j], pts[j + 1])
                if q:
                    inc.append((i, j, q))
        z = [0] * n
        win = max(3, int(larg * 1.6 / max(1e-6, tot / n)))
        eventi = sorted([(i, k, 0) for k, (i, j, q) in enumerate(inc)] + [(j, k, 1) for k, (i, j, q) in enumerate(inc)])
        alt = {}
        for pos, (_x, k, quale) in enumerate(eventi):
            if quale == 0:
                alt[k] = 'primo' if pos % 2 == 0 else 'secondo'
        for k, (i, j, q) in enumerate(inc):
            scelta = None
            if sopra:
                kk = min(sopra, key=lambda s: math.dist(s, q))
                if math.dist(kk, q) < 26:
                    scelta = sopra[kk]
            scelta = scelta or alt[k]
            alto, basso = (i, j) if scelta == 'primo' else (j, i)
            for m in range(max(0, alto - win), min(n, alto + win + 1)):
                z[m] = 1
            for m in range(max(0, basso - win), min(n, basso + win + 1)):
                if z[m] <= 0:
                    z[m] = -1
        fine = n
        for k in range(n):
            if lung[k] > tot * fino:
                fine = k
                break
        pts, z, lung = pts[:fine], z[:fine], lung[:fine]
        pezzi, ini = [], 0
        for k in range(1, len(pts) + 1):
            if k == len(pts) or z[k] != z[ini]:
                pezzi.append((z[ini], max(0, ini - 1), k))
                ini = k
        for _zz, a, b in sorted(pezzi, key=lambda t: t[0]):
            seg = pts[a:b]
            if len(seg) < 2:
                continue
            self.el.append(f'<polyline points="{_pts(seg)}" fill="none" stroke="#000" stroke-width="{larg}" stroke-linejoin="round"/>')
            self.el.append(f'<polyline points="{_pts(seg)}" fill="none" stroke="#fff" stroke-width="{larg - 5}" stroke-linejoin="round"/>')
            cs = [p for p, L in zip(seg, lung[a:b]) if L >= tot * capo]
            if len(cs) > 1:
                self.el.append(f'<polyline points="{_pts(cs)}" fill="none" stroke="{MEDIO}" stroke-width="{larg - 5}" stroke-linejoin="round"/>')
        if len(pts) > 2 and not chiusa:
            for (xa, ya), (xb, yb) in ((pts[1], pts[0]), (pts[-2], pts[-1])):
                ang = math.atan2(yb - ya, xb - xa)
                h = larg / 2
                self.linea([(xb + h * math.cos(ang + 1.5708), yb + h * math.sin(ang + 1.5708)),
                            (xb - h * math.cos(ang + 1.5708), yb - h * math.sin(ang + 1.5708))], 1.8)
        if dopo:
            self.freccia(dopo, 2.4)
        return pts

    def palo(self, x0, y0, x1, y1, sp=20, tono=CHIARO):
        return self.legno((x0, y0), (x1, y1), sp, tono)

    def giri(self, p0, p1, n, sp=7, dx=0, dy=0):
        """n giri di corda affiancati (fasce), dal segmento p0-p1 spostato di (dx, dy) ogni volta"""
        for k in range(n):
            a = (p0[0] + k * dx, p0[1] + k * dy)
            b = (p1[0] + k * dx, p1[1] + k * dy)
            self.el.append(f'<polyline points="{_pts([a, b])}" fill="none" stroke="#000" stroke-width="{sp}" stroke-linecap="butt"/>')
            self.el.append(f'<polyline points="{_pts([a, b])}" fill="none" stroke="#fff" stroke-width="{sp - 4}" stroke-linecap="butt"/>')


# ============================================================================ aiuti

class _Rnd:
    def __init__(self, seme):
        self.s = (seme * 9301 + 49297) % 233280 or 1

    def u(self, a, b):
        self.s = (self.s * 9301 + 49297) % 233280
        return a + (b - a) * self.s / 233280


def bezier(p0, p1, p2, n=20):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in [k / n for k in range(n + 1)]]


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


def _seg_x(a, b, c, d):
    def cr(o, p, q): return (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0])
    d1, d2, d3, d4 = cr(c, d, a), cr(c, d, b), cr(a, b, c), cr(a, b, d)
    if ((d1 > 0) != (d2 > 0)) and ((d3 > 0) != (d4 > 0)):
        t = d1 / (d1 - d2)
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    return None


# ============================================================================ corde in 3D

def _catmull3(ctrl, per=12):
    P = [ctrl[0]] + list(ctrl) + [ctrl[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for k in range(per):
            t = k / per
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in (0, 1, 2)))
    out.append(tuple(ctrl[-1]))
    return out


def giro(cx, cy, asse, ang0, ang1, r, avanza0, avanza1, n=None):
    """punti 3D di una corda che gira intorno a un asse: asse 'v' (verticale, in x=cx) o 'o' (orizzontale,
    in y=cy). ang in gradi: 0 = davanti, 90 = a destra (v) o sotto (o), 180 = dietro.
    avanza = la coordinata lungo l'asse (y per 'v', x per 'o') all'inizio e alla fine."""
    n = n or max(4, int(abs(ang1 - ang0) / 30))
    out = []
    for k in range(n + 1):
        t = k / n
        a = math.radians(ang0 + (ang1 - ang0) * t)
        s = avanza0 + (avanza1 - avanza0) * t
        if asse == 'v':
            out.append((cx + r * math.sin(a), s, r * math.cos(a)))
        else:
            out.append((s, cy + r * math.sin(a), r * math.cos(a)))
    return out


def _corde3d(self, fili, oggetto=None, larg=12, fino=None, capo=None, dopo=None, dietro_x=None):
    """fili: liste di punti di controllo (x, y, z), z > 0 verso chi guarda. Gli incroci si decidono dalla z;
    i tratti con z < 0 dentro dietro_x=(x0, x1) sono nascosti dall'oggetto (un palo) disegnato con oggetto().
    fino / capo: per filo (frazione disegnata, da dove è 'capo' grigio)."""
    nf = len(fili)
    fino = fino or [1.0] * nf
    capo = capo or [0.6] * nf
    P = [_catmull3(c) for c in fili]
    # incroci fra tutti i segmenti (tranne i vicini dello stesso filo)
    segs = [(f, i) for f in range(nf) for i in range(len(P[f]) - 1)]
    z = [[0] * len(P[f]) for f in range(nf)]
    win = 4
    for a in range(len(segs)):
        fa, ia = segs[a]
        pa, pb = P[fa][ia], P[fa][ia + 1]
        for b in range(a + 1, len(segs)):
            fb, ib = segs[b]
            if fa == fb and ib <= ia + 2:
                continue
            pc, pd = P[fb][ib], P[fb][ib + 1]
            q = _seg_x(pa[:2], pb[:2], pc[:2], pd[:2])
            if not q:
                continue
            za, zb = (pa[2] + pb[2]) / 2, (pc[2] + pd[2]) / 2
            (fo, io), (fu, iu) = ((fa, ia), (fb, ib)) if za >= zb else ((fb, ib), (fa, ia))
            for m in range(max(0, io - win), min(len(P[fo]), io + win + 2)):
                z[fo][m] = max(z[fo][m], 1) if z[fo][m] >= 0 else 1
            for m in range(max(0, iu - win), min(len(P[fu]), iu + win + 2)):
                if z[fu][m] <= 0:
                    z[fu][m] = -1
    pezzi = []
    for f in range(nf):
        pts = P[f]
        lung = [0.0]
        for i in range(1, len(pts)):
            lung.append(lung[-1] + math.dist(pts[i][:2], pts[i - 1][:2]))
        tot = lung[-1]
        fine = next((k for k in range(len(pts)) if lung[k] > tot * fino[f]), len(pts))
        def strato(k):
            nasc = dietro_x and pts[k][2] < -1 and dietro_x[0] - larg < pts[k][0] < dietro_x[1] + larg
            return (-10 if nasc else 0) + z[f][k]
        ini = 0
        for k in range(1, fine + 1):
            if k == fine or strato(k) != strato(ini):
                seg = [p[:2] for p in pts[max(0, ini - 1):k]]
                cs = [p[:2] for p, L in zip(pts[max(0, ini - 1):k], lung[max(0, ini - 1):k]) if L >= tot * capo[f]]
                pezzi.append((strato(ini), seg, cs))
                ini = k
        # chiusure delle punte
        p_fin = [pts[0][:2], pts[1][:2]], [pts[fine - 1][:2], pts[fine - 2][:2]]
        pezzi.append((99, 'punte', p_fin))
    fatto_oggetto = False
    for st, seg, cs in sorted([p for p in pezzi if p[1] != 'punte'], key=lambda t: t[0]):
        if not fatto_oggetto and st > -5 and oggetto:
            oggetto(); fatto_oggetto = True
        if len(seg) < 2:
            continue
        self.el.append(f'<polyline points="{_pts(seg)}" fill="none" stroke="#000" stroke-width="{larg}" stroke-linejoin="round"/>')
        self.el.append(f'<polyline points="{_pts(seg)}" fill="none" stroke="#fff" stroke-width="{larg - 5}" stroke-linejoin="round"/>')
        if len(cs) > 1:
            self.el.append(f'<polyline points="{_pts(cs)}" fill="none" stroke="{MEDIO}" stroke-width="{larg - 5}" stroke-linejoin="round"/>')
    if oggetto and not fatto_oggetto:
        oggetto()
    for st, _t, coppie in [p for p in pezzi if p[1] == 'punte']:
        for (xb, yb), (xa, ya) in coppie:
            ang = math.atan2(yb - ya, xb - xa)
            h = larg / 2
            self.linea([(xb + h * math.cos(ang + 1.5708), yb + h * math.sin(ang + 1.5708)),
                        (xb - h * math.cos(ang + 1.5708), yb - h * math.sin(ang + 1.5708))], 1.8)
    if dopo:
        self.freccia(dopo, 2.4)


Scena.corde3d = _corde3d
