"""I 17 piatti di Ai Fornelli, disegnati con tools/arte.py su una tela di 400 x 230.

Ogni funzione riceve la tela e restituisce la scatola (x, y, lato) da cui ritagliare la miniatura.
"""
import math

import numpy as np

from arte import Tela

W, H = 400, 230


# ---------------------------------------------------------------------------- pezzi riusati

def penna(c, x, y, ang, lung=28, larg=10, tono=200):
    ca, sa = math.cos(ang), math.sin(ang)
    def p(u, v): return (x + u * ca - v * sa, y + u * sa + v * ca)
    m = c.poligono([p(-lung / 2 + 3, -larg / 2), p(lung / 2, -larg / 2), p(lung / 2 - 3, larg / 2), p(-lung / 2, larg / 2)])
    c.forma(m, tono, rilievo=2.5, luce=70, grana=8, bordo=1.1)
    for k in range(1, 5):
        u = -lung / 2 + k * lung / 5
        c.tratto([p(u, -larg / 2 + 1), p(u - 1.5, larg / 2 - 1)], 0.6, 120)
    buco = c.ellisse(*p(lung / 2 - 1.5, 0), 2.2, larg / 2 - 1)
    c.forma(buco, 40, rilievo=0, grana=0, bordo=0)


def pomodorino_mezzo(c, x, y, r=11):
    c.forma(c.blob(x, y, r, r * 0.85, 0.05), 120, rilievo=4, luce=60, grana=10, bordo=1.2)
    dentro = c.ellisse(x - 1, y - 1, r * 0.72, r * 0.6)
    c.forma(dentro, 175, rilievo=2, luce=40, grana=10, bordo=0.8, tono_bordo=90)
    for k in range(3):
        a = k * 2.1 + 0.4
        c.forma(c.ellisse(x - 1 + r * 0.33 * math.cos(a), y - 1 + r * 0.28 * math.sin(a), 2.6, 1.8), 95, rilievo=0, grana=0, bordo=0)


def mozzarella(c, x, y, r=12, tono=250):
    c.forma(c.blob(x, y, r, r * 0.8, 0.22), tono, rilievo=5, luce=40, grana=4, bordo=1.2, scuro_bordi=30)


def cubo(c, x, y, s, tono, ang=0.0):
    """un dadino visto di tre quarti (verdure, guanciale, patate)"""
    ca, sa = math.cos(ang), math.sin(ang)
    def p(u, v): return (x + u * ca - v * sa, y + u * sa + v * ca)
    top = c.poligono([p(-s / 2, -s / 2), p(s / 2, -s / 2), p(s / 2 + s * 0.3, -s * 0.15), p(-s / 2 + s * 0.3, -s * 0.15)])
    fronte = c.poligono([p(-s / 2, -s / 2), p(-s / 2 + s * 0.3, -s * 0.15), p(-s / 2 + s * 0.3, s * 0.6), p(-s / 2, s * 0.3)])
    lato = c.poligono([p(-s / 2 + s * 0.3, -s * 0.15), p(s / 2 + s * 0.3, -s * 0.15), p(s / 2 + s * 0.3, s * 0.6), p(-s / 2 + s * 0.3, s * 0.6)])
    c.forma(top, min(250, tono + 30), rilievo=0, grana=8, bordo=0.9)
    c.forma(lato, tono, rilievo=0, grana=8, bordo=0.9)
    c.forma(fronte, max(10, tono - 45), rilievo=0, grana=8, bordo=0.9)


def fagiolo(c, x, y, ang, tono=95):
    m = c.blob(x, y, 7, 4.3, 0.06, rot=ang)
    c.forma(m, tono, rilievo=2, luce=90, grana=14, bordo=1.0, chiaro=(x - 2, y - 2, 4, 70))


def fetta_a_strati(c, x, y, w, h, prof, strati, cima, seme=0):
    """una fetta di torta salata o dolce vista di tre quarti: fronte a strati, lato più scuro, cima"""
    # cima: parallelogramma che va indietro verso destra
    dx, dy = prof * 0.9, -prof * 0.55
    top = c.poligono([(x, y), (x + w, y), (x + w + dx, y + dy), (x + dx, y + dy)])
    tono_cima, macchie = cima
    c.forma(top, tono_cima, rilievo=3, luce=50, grana=16, bordo=1.3, macchie=macchie)
    lato = c.poligono([(x + w, y), (x + w + dx, y + dy), (x + w + dx, y + dy + h), (x + w, y + h)])
    fronte = c.poligono([(x, y), (x + w, y), (x + w, y + h), (x, y + h)])
    tot = sum(s[0] for s in strati)
    yy = y
    for alt, tono, grana in strati:
        hh = h * alt / tot
        fascia = c.rett(x - 1, yy, w + 2, hh + 0.5)
        onda = c.poligono([(x - 2 + k * 6, yy + 1.6 * math.sin(k * 1.3 + seme)) for k in range(int(w / 6) + 2)]
                          + [(x + w + 2, yy + hh + 1), (x - 2, yy + hh + 1)])
        c.forma(np.maximum(fascia * 0, onda) * fronte, tono, rilievo=0, grana=grana, bordo=0)
        c.forma(c.poligono([(x + w, yy), (x + w + dx, yy + dy), (x + w + dx, yy + dy + hh), (x + w, yy + hh)]) * lato,
                max(10, tono - 50), rilievo=0, grana=grana, bordo=0)
        yy += hh
    c.contorno(fronte, 1.3, 20)
    c.contorno(lato, 1.3, 20)
    c.tratteggio(lato, passo=3.5, ang=1.1, tono=60, larg=0.6)


def forchetta(c, x, y, ang=-0.25, lung=150):
    ca, sa = math.cos(ang), math.sin(ang)
    def p(u, v): return (x + u * ca - v * sa, y + u * sa + v * ca)
    manico = c.poligono([p(0, -5), p(lung * 0.62, -3), p(lung * 0.62, 3), p(0, 5)])
    c.forma(manico, 225, rilievo=3, luce=60, grana=4, bordo=1.2)
    testa = c.poligono([p(lung * 0.6, -4), p(lung * 0.72, -9), p(lung, -9), p(lung, 9), p(lung * 0.72, 9), p(lung * 0.6, 4)])
    c.forma(testa, 225, rilievo=3, luce=60, grana=4, bordo=1.2)
    for v in (-4.5, 0, 4.5):
        c.tratto([p(lung * 0.78, v), p(lung + 1, v)], 1.4, 255)
    c.contorno(testa, 1.2, 20)


def cucchiaio(c, x, y, ang=-0.25, lung=150):
    ca, sa = math.cos(ang), math.sin(ang)
    def p(u, v): return (x + u * ca - v * sa, y + u * sa + v * ca)
    manico = c.poligono([p(0, -5), p(lung * 0.65, -2.5), p(lung * 0.65, 2.5), p(0, 5)])
    c.forma(manico, 225, rilievo=3, luce=60, grana=4, bordo=1.2)
    cx, cy = p(lung * 0.83, 0)
    c.forma(c.blob(cx, cy, lung * 0.18, 12, 0.02, rot=ang), 220, rilievo=6, luce=-60, grana=4, bordo=1.2)


# ---------------------------------------------------------------------------- i piatti

def pizza(c):
    c.ombra(208, 170, 170, 44)
    tagliere = c.rett(30, 28, 340, 192, 18)
    c.forma(tagliere, 185, rilievo=4, luce=40, grana=6, bordo=1.4)
    for k in range(9):
        y = 36 + k * 21
        c.tratto([(34, y), (120, y + 3), (220, y - 2), (366, y + 2)], 0.7, 140)
    cx, cy, rx, ry = 200, 122, 150, 86
    c.forma(c.blob(cx, cy, rx, ry, 0.025, punti=30), 200, rilievo=8, luce=70, grana=12, bordo=1.5,
            macchie=(26, 3.2, 60))
    sugo = c.blob(cx, cy + 2, rx * 0.84, ry * 0.8, 0.04, punti=30)
    c.forma(sugo, 115, rilievo=3, luce=40, grana=18, bordo=1.0, tono_bordo=60)
    for (x, y, r) in [(150, 100, 17), (232, 92, 15), (196, 132, 18), (262, 140, 16), (128, 148, 14), (210, 165, 12),
                      (300, 110, 12), (110, 118, 10), (170, 172, 11)]:
        mozzarella(c, x, y, r)
    c.basilico(180, 112, n=2, lung=24)
    c.basilico(268, 122, n=2, lung=22)
    c.basilico(140, 160, n=1, lung=20)
    for a in (0.3, 1.35, 2.4, 3.45, 4.5, 5.55):
        c.tratto([(cx + 12 * math.cos(a), cy + 7 * math.sin(a)), (cx + rx * 0.97 * math.cos(a), cy + ry * 0.97 * math.sin(a))], 0.8, 50)
    return (85, 20, 190)


def polpette(c):
    dentro = c.piatto(200, 118, 178, 100)
    sugo = c.blob(200, 120, 132, 72, 0.07, punti=26)
    c.forma(sugo, 165, rilievo=6, luce=50, grana=16, bordo=1.0, tono_bordo=60)
    pos = [(150, 100), (205, 92), (258, 104), (175, 140), (235, 146), (290, 132), (118, 132)]
    for (x, y) in pos:
        m = c.blob(x, y, 25, 21, 0.08, punti=20)
        c.forma(m, 120, rilievo=9, luce=160, grana=20, bordo=1.3, tono_bordo=0, chiaro=(x - 8, y - 8, 13, 110), macchie=(8, 1.5, 50))
        c.forma(c.ellisse(x + 2, y + 14, 19, 6) * m, 150, rilievo=0, grana=16, bordo=0)
    c.basilico(206, 118, n=3, lung=24)
    c.punti(sugo, 30, 0.9, 250)
    return (95, 18, 210)


def lasagne(c):
    c.piatto(195, 128, 182, 92)
    fetta_a_strati(c, 102, 92, 150, 80, 70,
                   [(1.2, 225, 8), (1.0, 95, 22), (0.7, 245, 4), (1.0, 220, 8), (1.0, 90, 22), (0.7, 245, 4), (1.1, 222, 8)],
                   (170, (40, 2.8, 70)))
    forchetta(c, 300, 200, ang=-0.55, lung=110)
    return (95, 30, 190)




def pastafredda(c):
    dentro = c.ciotola(200, 100, 160, 62, 105)
    c.forma(c.blob(200, 104, 138, 50, 0.05, punti=24) * dentro, 175, rilievo=6, luce=60, grana=16, bordo=0)
    import random
    r = random.Random(4)
    for k in range(110):
        x, y = 200 + r.uniform(-125, 125), 100 + r.uniform(-42, 40)
        if ((x - 200) / 128) ** 2 + ((y - 102) / 46) ** 2 > 1:
            continue
        penna(c, x, y, r.uniform(-1.4, 1.4))
    for (x, y) in [(150, 88), (240, 80), (270, 118), (130, 116), (205, 120), (180, 76), (300, 92), (95, 100)]:
        pomodorino_mezzo(c, x, y, 11)
    for (x, y) in [(220, 98), (160, 108), (110, 96), (290, 96), (245, 128)]:
        mozzarella(c, x, y, 10)
    c.basilico(196, 90, n=3, lung=22)
    return (100, 25, 200)


def bistecca(c):
    c.piatto(200, 120, 185, 98)
    # patate a spicchi a destra
    import random
    r = random.Random(7)
    for k in range(7):
        x, y = 268 + r.uniform(-30, 40), 92 + k * 13 + r.uniform(-5, 5)
        a = r.uniform(-0.6, 0.6)
        m = c.blob(x, y, 26, 10, 0.12, rot=a)
        c.forma(m, 205, rilievo=5, luce=140, grana=10, bordo=1.2, tono_bordo=0, macchie=(5, 2.2, 120))
    c.rosmarino(300, 60, 350, 110, aghi=10)
    # la bistecca a sinistra, con un paio di fette tagliate
    m = c.blob(140, 112, 78, 48, 0.08, punti=22, rot=-0.15)
    c.forma(m, 115, rilievo=8, luce=150, grana=20, bordo=1.4, tono_bordo=0, macchie=(14, 1.8, 60))
    for k in range(4):
        x = 92 + k * 24
        c.tratto([(x, 82), (x + 26, 138)], 3.2, 15)
    for k, (x, y) in enumerate([(205, 150), (180, 166)]):
        f = c.blob(x, y, 24, 12, 0.08, rot=-0.3)
        c.forma(f, 150, rilievo=3, luce=60, grana=14, bordo=1.3, tono_bordo=0)
        c.contorno(c.blob(x, y, 24, 12, 0.08, rot=-0.3), 2.2, 40)
    mayo = c.blob(250, 150, 24, 12, 0.2)
    c.forma(mayo, 248, rilievo=5, luce=60, grana=3, bordo=1.2, tono_bordo=0)
    c.tratto([(236, 148), (250, 144), (262, 150)], 0.8, 140)
    return (60, 20, 200)


def burger(c):
    c.ombra(205, 205, 150, 18)
    tagliere = c.rett(40, 192, 320, 18, 6)
    c.forma(tagliere, 185, rilievo=2, luce=30, grana=6, bordo=1.3, tono_bordo=0)
    cx = 200
    def strato(y, h, w, tono, ondulato=0.0, macchie=None, gr=12, ril=4):
        pts = [(cx - w / 2 + k * w / 30, y - ondulato * math.sin(k * 1.7)) for k in range(31)]
        pts += [(cx + w / 2 - k * w / 30, y + h + ondulato * math.sin(k * 1.3 + 1)) for k in range(31)]
        m = c.poligono(pts)
        c.forma(m, tono, rilievo=ril, luce=90, grana=gr, bordo=1.3, tono_bordo=0, macchie=macchie)
        return m
    # dal basso
    fondo = c.rett(cx - 112, 168, 224, 28, 12)
    c.forma(fondo, 190, rilievo=6, luce=90, grana=10, bordo=1.4, tono_bordo=0)
    strato(152, 20, 236, 60, 2.5, (20, 1.6, 20), 20, 3)
    strato(142, 12, 240, 248, 3.5, None, 3, 2)              # mozzarella
    for x in (110, 150, 250, 290):
        c.forma(c.blob(x, 160, 6, 9, 0.1), 248, rilievo=2, grana=3, bordo=1.1, tono_bordo=0)
    strato(124, 20, 232, 60, 2.5, (20, 1.6, 20), 20, 3)
    strato(114, 12, 236, 248, 3.5, None, 3, 2)
    strato(104, 11, 226, 130, 1.5, None, 12, 2)              # pomodoro
    for k in range(9):
        x = cx - 100 + k * 25
        c.tratto([(x, 101), (x + 14, 97), (x + 26, 102)], 1.6, 40)   # cipolle
    top = c.poligono([(cx - 115 + 230 * t, 100 - 72 * math.sin(math.pi * t) ** 0.55) for t in np.linspace(0, 1, 60)])
    c.forma(top, 175, rilievo=14, luce=170, grana=10, bordo=1.5, tono_bordo=0, chiaro=(160, 50, 40, 60))
    import random
    r = random.Random(3)
    for k in range(26):
        t = r.uniform(0.12, 0.88)
        x = cx - 115 + 230 * t
        y = 100 - 72 * math.sin(math.pi * t) ** 0.55 + r.uniform(8, 50)
        if y > 96:
            continue
        c.forma(c.blob(x, y, 3.2, 1.8, 0.05, rot=r.uniform(-1, 1)), 250, rilievo=0, grana=0, bordo=0.7, tono_bordo=30)
    return (90, 15, 200)


def tazza(c):
    c.forma(c.ellisse(175, 196, 120, 26), 250, rilievo=4, luce=30, grana=3, bordo=1.4, tono_bordo=0)
    c.contorno(c.ellisse(175, 194, 70, 14), 0.8, 90)
    # manico
    c.forma(c.ellisse(258, 130, 32, 36), 245, rilievo=4, luce=40, grana=3, bordo=1.4, tono_bordo=0)
    c.forma(c.ellisse(258, 130, 18, 22), 255, rilievo=0, grana=0, bordo=1.2, tono_bordo=0)
    corpo = c.poligono([(105, 84), (245, 84), (238, 175), (230, 190), (120, 190), (112, 175)])
    c.forma(corpo, 245, rilievo=10, luce=60, grana=4, bordo=1.4, tono_bordo=0, scuro_bordi=60)
    c.tratteggio(c.poligono([(215, 84), (245, 84), (232, 190), (205, 190)]), passo=4, ang=1.35, tono=120, larg=0.6)
    c.forma(c.ellisse(175, 84, 70, 15), 110, rilievo=0, grana=14, bordo=1.4, tono_bordo=0)
    # panna a spirale
    for k, (r, y) in enumerate([(64, 76), (54, 62), (42, 49), (28, 38), (14, 29)]):
        m = c.blob(175 + (k % 2) * 4 - 2, y, r, 13, 0.08, punti=16)
        c.forma(m, 250, rilievo=5, luce=60, grana=3, bordo=1.2, tono_bordo=0)
    c.tratto([(173, 22), (178, 16), (180, 24)], 1.3, 0)
    for (x, y) in [(150, 60), (190, 48), (165, 40), (205, 70)]:
        c.forma(c.ellisse(x, y, 3, 2), 60, rilievo=0, grana=0, bordo=0)
    # Gocciole
    for (x, y, rr) in [(320, 176, 30), (345, 150, 26)]:
        m = c.blob(x, y, rr, rr * 0.45, 0.04)
        c.forma(m, 165, rilievo=5, luce=90, grana=14, bordo=1.3, tono_bordo=0)
        c.punti(m, 14, 2.2, 20)
    return (80, 10, 200)


def parmigiana(c):
    c.piatto(195, 128, 182, 92)
    fetta_a_strati(c, 104, 92, 150, 76, 70,
                   [(0.8, 120, 18), (0.6, 50, 16), (0.7, 245, 4), (0.7, 120, 18), (0.6, 50, 16), (0.7, 245, 4), (0.7, 120, 18), (0.6, 50, 16)],
                   (125, (30, 3.6, 245)), seme=2)
    c.basilico(170, 72, n=3, lung=22)
    forchetta(c, 300, 200, ang=-0.55, lung=110)
    return (95, 30, 190)


def pollo(c):
    c.piatto(200, 122, 186, 98)
    import random
    r = random.Random(11)
    for k in range(10):
        x, y = 200 + r.uniform(-110, 110), 120 + r.uniform(-50, 50)
        if ((x - 200) / 140) ** 2 + ((y - 120) / 70) ** 2 > 1:
            continue
        cubo(c, x, y, 18, 200, ang=r.uniform(-0.6, 0.6))
    for (x, y, a) in [(150, 100, -0.4), (240, 112, 0.3)]:
        # coscia: polpa e osso
        osso = c.tratto_maschera([(x + 40 * math.cos(a), y + 40 * math.sin(a)), (x + 72 * math.cos(a), y + 72 * math.sin(a))], 8)
        c.forma(osso, 240, rilievo=2, grana=3, bordo=1.2, tono_bordo=0)
        c.forma(c.ellisse(x + 74 * math.cos(a), y + 74 * math.sin(a), 8, 7), 240, rilievo=2, grana=3, bordo=1.2, tono_bordo=0)
        m = c.blob(x, y, 48, 32, 0.1, rot=a)
        c.forma(m, 130, rilievo=10, luce=170, grana=20, bordo=1.4, tono_bordo=0, chiaro=(x - 14, y - 12, 18, 80),
                macchie=(16, 2.2, 60))
    c.rosmarino(90, 150, 140, 175, aghi=9)
    c.rosmarino(280, 60, 330, 80, aghi=9)
    return (90, 20, 200)


def minestrone(c):
    dentro = c.ciotola(200, 96, 160, 60, 108)
    c.forma(dentro, 165, rilievo=4, luce=40, grana=16, bordo=0)
    import random
    r = random.Random(5)
    toni = [230, 120, 200, 70, 245, 150]
    for k in range(34):
        x, y = 200 + r.uniform(-130, 130), 96 + r.uniform(-44, 44)
        if ((x - 200) / 136) ** 2 + ((y - 98) / 46) ** 2 > 1:
            continue
        if k % 3 == 0:
            fagiolo(c, x, y, r.uniform(-1, 1), 100)
        else:
            cubo(c, x, y, r.uniform(9, 13), r.choice(toni), ang=r.uniform(-0.5, 0.5))
    c.basilico(200, 92, n=2, lung=20)
    cucchiaio(c, 270, 118, ang=-0.5, lung=120)
    return (100, 25, 200)


def pastafagioli(c):
    dentro = c.ciotola(200, 96, 160, 60, 108)
    c.forma(dentro, 150, rilievo=4, luce=40, grana=18, bordo=0)
    import random
    r = random.Random(8)
    for k in range(40):
        x, y = 200 + r.uniform(-130, 130), 96 + r.uniform(-44, 44)
        if ((x - 200) / 136) ** 2 + ((y - 98) / 46) ** 2 > 1:
            continue
        if k % 2:
            fagiolo(c, x, y, r.uniform(-1.2, 1.2), 85)
        else:
            m = c.ellisse(x, y, 6, 4.5)
            c.forma(m, 225, rilievo=2, grana=4, bordo=1.0, tono_bordo=0)
            c.forma(c.ellisse(x, y, 2.6, 2), 70, rilievo=0, grana=0, bordo=0)
    c.rosmarino(150, 70, 230, 92, aghi=12)
    c.punti(dentro, 40, 0.8, 240)
    return (100, 25, 200)


def risotto(c):
    dentro = c.piatto(200, 120, 185, 100)
    m = c.blob(200, 118, 125, 62, 0.06, punti=24)
    c.forma(m, 190, rilievo=10, luce=110, grana=6, bordo=1.3, tono_bordo=0)
    import random
    r = random.Random(9)
    ys, xs = np.nonzero(m[::3, ::3] > 0.5)
    for k in range(260):
        i = r.randrange(len(xs))
        x, y = xs[i] + r.random(), ys[i] + r.random()
        c.contorno(c.ellisse(x, y, 3.4, 1.9), 0.5, 90)
    for k in range(10):
        x, y = 200 + r.uniform(-60, 60), 112 + r.uniform(-25, 25)
        a = r.uniform(0, 3)
        c.tratto([(x, y), (x + 9 * math.cos(a), y + 9 * math.sin(a))], 1.3, 20)
    for (x, y) in [(170, 100), (230, 108), (200, 128)]:
        c.forma(c.poligono([(x - 14, y), (x + 8, y - 7), (x + 15, y + 2), (x - 6, y + 7)]), 245, rilievo=2, grana=4, bordo=1.0, tono_bordo=0)
    return (100, 20, 200)


def gnocchi(c):
    c.piatto(200, 120, 185, 100)
    sugo = c.blob(200, 120, 130, 66, 0.08, punti=26)
    c.forma(sugo, 140, rilievo=6, luce=50, grana=18, bordo=1.0, tono_bordo=60)
    import random
    r = random.Random(12)
    for k in range(26):
        x, y = 200 + r.uniform(-105, 105), 120 + r.uniform(-46, 46)
        if ((x - 200) / 112) ** 2 + ((y - 120) / 54) ** 2 > 1:
            continue
        a = r.uniform(-0.8, 0.8)
        m = c.blob(x, y, 14, 9, 0.08, rot=a)
        c.forma(m, 225, rilievo=4, luce=110, grana=6, bordo=1.2, tono_bordo=0)
        for j in (-6, -2, 2, 6):
            ca, sa = math.cos(a), math.sin(a)
            c.tratto([(x + j * ca - 6 * sa, y + j * sa + 6 * ca), (x + j * ca + 6 * sa, y + j * sa - 6 * ca)], 0.8, 110)
    c.basilico(200, 112, n=3, lung=24)
    return (100, 20, 200)


def carbonara(c):
    c.piatto(200, 120, 185, 100)
    import random
    r = random.Random(13)
    nido = c.ellisse(200, 116, 120, 60)
    c.forma(nido, 228, rilievo=10, luce=120, grana=6, bordo=1.3, tono_bordo=0)
    for k in range(150):
        cx, cy = 200 + r.uniform(-30, 30), 116 + r.uniform(-14, 14)
        rx, ry = r.uniform(30, 115), 0
        ry = rx * 0.5
        a0 = r.uniform(0, 6.28)
        pts = [(cx + rx * math.cos(a0 + t), cy + ry * math.sin(a0 + t)) for t in np.linspace(0, r.uniform(1.0, 2.4), 24)]
        pts = [(x, y) for x, y in pts if ((x - 200) / 118) ** 2 + ((y - 116) / 58) ** 2 < 1]
        if len(pts) > 2:
            c.tratto(pts, 1.0, 35)
    for k in range(12):
        x, y = 200 + r.uniform(-80, 80), 112 + r.uniform(-35, 30)
        cubo(c, x, y, 12, 150, ang=r.uniform(-0.6, 0.6))
    c.punti(nido, 120, 0.9, 10)
    return (100, 20, 200)


def tiramisu(c):
    c.piatto(195, 128, 182, 92)
    fetta_a_strati(c, 112, 84, 136, 88, 72,
                   [(0.5, 60, 20), (1.3, 248, 3), (1.0, 120, 22), (1.2, 248, 3), (1.0, 120, 22), (0.4, 248, 3)],
                   (70, (60, 2.0, 25)), seme=4)
    cucchiaio(c, 290, 205, ang=-0.55, lung=110)
    return (95, 25, 190)


def crostata(c):
    c.ombra(205, 170, 170, 44)
    cx, cy, rx, ry = 200, 112, 165, 88
    lato = c.poligono([(cx - rx, cy)] + [(cx + rx * math.cos(t), cy + ry * math.sin(t) + 16) for t in np.linspace(math.pi, 0, 60)] + [(cx + rx, cy)])
    c.forma(np.maximum(lato, c.ellisse(cx, cy + 16, rx, ry)), 170, rilievo=6, luce=90, grana=12, bordo=1.4, tono_bordo=0)
    for t in np.linspace(math.pi * 0.05, math.pi * 0.95, 26):
        x = cx + rx * math.cos(t)
        c.tratto([(x, cy + ry * math.sin(t) + 2), (x, cy + ry * math.sin(t) + 16)], 0.9, 70)
    bordo = c.ellisse(cx, cy, rx, ry)
    c.forma(bordo, 205, rilievo=6, luce=110, grana=10, bordo=1.4, tono_bordo=0)
    marm = c.ellisse(cx, cy + 2, rx * 0.86, ry * 0.84)
    c.forma(marm, 85, rilievo=5, luce=60, grana=16, bordo=1.2, tono_bordo=0)
    for k in range(-3, 4):
        for dirz in (1, -1):
            x0 = cx + k * 40
            pts = [(x0 - dirz * 70, cy - 80), (x0 + dirz * 70, cy + 80)]
            m = c.tratto_maschera(pts, 15) * marm
            c.forma(m, 210, rilievo=3, luce=110, grana=10, bordo=1.1, tono_bordo=0)
    return (100, 15, 200)


def ciambellone(c):
    c.ombra(185, 190, 150, 26)
    c.forma(c.ellisse(175, 182, 150, 30), 250, rilievo=3, grana=3, bordo=1.4, tono_bordo=0)
    cx, cy, rx, ry, alt = 160, 108, 115, 44, 66
    corpo = c.poligono([(cx - rx, cy)] + [(cx + rx * math.cos(t), cy + ry * math.sin(t) + alt) for t in np.linspace(math.pi, 0, 60)] + [(cx + rx, cy)])
    c.forma(corpo, 150, rilievo=10, luce=120, grana=14, bordo=1.4, tono_bordo=0)
    for t in np.linspace(math.pi * 0.08, math.pi * 0.92, 14):
        x = cx + rx * math.cos(t)
        c.tratto([(x, cy + ry * math.sin(t) + 6), (x * 0.98 + cx * 0.02, cy + ry * math.sin(t) + alt - 2)], 1.0, 70)
    top = c.ellisse(cx, cy, rx, ry)
    c.forma(top, 165, rilievo=10, luce=140, grana=14, bordo=1.4, tono_bordo=0)
    buco = c.ellisse(cx, cy - 2, rx * 0.28, ry * 0.3)
    c.forma(buco, 40, rilievo=0, grana=10, bordo=1.2, tono_bordo=0)
    c.punti(top, 260, 1.0, 252)
    c.tratto([(cx - 70, cy - 12), (cx - 30, cy - 22), (cx + 10, cy - 18)], 1.0, 60)
    # la fetta
    f = c.poligono([(262, 118), (352, 132), (346, 186), (258, 172)])
    c.forma(f, 225, rilievo=3, luce=50, grana=8, bordo=1.4, tono_bordo=0)
    c.punti(f, 60, 1.2, 150)
    crosta = c.poligono([(262, 118), (352, 132), (350, 140), (261, 126)])
    c.forma(crosta, 140, rilievo=0, grana=10, bordo=1.0, tono_bordo=0)
    return (60, 10, 210)


PIATTI = {
    'pizza': pizza, 'pastafredda': pastafredda, 'bistecca': bistecca, 'burger': burger, 'tazza': tazza,
    'lasagne': lasagne, 'parmigiana': parmigiana, 'polpette': polpette, 'pollo': pollo, 'minestrone': minestrone,
    'pastafagioli': pastafagioli, 'risotto': risotto, 'gnocchi': gnocchi, 'carbonara': carbonara,
    'tiramisu': tiramisu, 'crostata': crostata, 'ciambellone': ciambellone,
}
