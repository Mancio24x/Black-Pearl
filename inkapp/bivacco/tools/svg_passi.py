"""I disegni vettoriali dei passi (440 x 250), uno per passo: PASSI = { 'nome': funzione(Scena) }.

Il nome è '<id del tutorial>_<numero del passo>' e va in src/tecniche.lua come img = '...'.
Si vedono su Claude Design (tools/design_canvas.py) e diventano immagini a 1 bit con make_art.py.
"""
import math

from svgkit import CHIARO, MEDIO, SCURO, NERO, bezier

PASSI = {}


def passo(nome):
    def reg(f):
        PASSI[nome] = f
        return f
    return reg


def lettera(s, ch):
    s.rett(16, 14, 52, 52, '#fff', 10, 3)
    s.testo(42, 42, ch, 34)


def segnavia(s, x, y):
    s.legno((x, y), (x, y - 70), 6, '#fff', testa=False)
    s.rett(x - 18, y - 74, 36, 22, '#fff', 2, 1.8)
    s.rett(x - 18, y - 67, 36, 8, SCURO, 0, 0)


# ============================================================================ S.T.O.P.

@passo('stop_1')
def _(s):
    s.terra(205, seme=1)
    lettera(s, 'S')
    s.sasso(220, 196, 46, 18)
    s.omino(212, 180, 100, 'seduto')
    s.rett(260, 170, 14, 28, CHIARO, 4)          # la borraccia
    s.rett(262, 162, 10, 8, '#fff', 1)


@passo('stop_2')
def _(s):
    s.terra(205, seme=2)
    lettera(s, 'T')
    s.sasso(160, 196, 46, 18)
    s.omino(152, 180, 100, 'seduto')
    for (x, y, r) in ((196, 98, 5), (210, 84, 8)):
        s.ellisse(x, y, r, r)
    s.ellisse(300, 56, 96, 42)
    s.sole(250, 56, 11)
    s.ellisse(305, 56, 15, 15)
    s.linea([(305, 56), (305, 46)], 2)
    s.linea([(305, 56), (313, 60)], 2)
    s.testo(360, 56, '?', 40)


@passo('stop_3')
def _(s):
    s.terra(205, seme=3)
    lettera(s, 'O')
    s.omino(130, 205, 100, 'indica')
    s.linea([(170, 112), (318, 140)], 1.4, tratteggio='6 6')
    segnavia(s, 340, 200)
    s.curva((380, 205), (400, 150), (430, 140), 1.6)


@passo('stop_4')
def _(s):
    lettera(s, 'P')
    s.poly([(90, 40), (200, 26), (310, 40), (420, 26), (420, 226), (310, 240), (200, 226), (90, 240)], '#fff')
    s.linea([(200, 26), (200, 226)], 1.4)
    s.linea([(310, 40), (310, 240)], 1.4)
    for k in range(5):
        s.curva((100, 70 + k * 30), (250, 40 + k * 34), (410, 80 + k * 26), 0.9)
    s.linea([(150, 200), (190, 170), (230, 172), (280, 130), (330, 112)], 3, tratteggio='8 6')
    s.ellisse(150, 200, 7, 7, NERO)
    s.segno_no(340, 104, 0.8)
    s.freccia([(300, 170), (200, 205)], 2.2)


@passo('stop_5')
def _(s):
    for k, (tit, sotto) in enumerate((('3', 'ORE AL FREDDO'), ('3', 'GIORNI SENZA ACQUA'), ('3', 'SETTIMANE SENZA CIBO'))):
        x = 20 + k * 140
        s.rett(x, 30, 120, 190, '#fff', 12, 2.4)
        s.testo(x + 60, 150, tit, 54)
        s.testo(x + 60, 196, sotto.split(' ')[0], 13, 700)
        s.testo(x + 60, 210, ' '.join(sotto.split(' ')[1:]), 11, 700)
    s.poly([(52, 110), (80, 60), (108, 110)], CHIARO)          # riparo
    s.linea([(80, 60), (80, 110)], 1.4)
    s.poly([(220, 56), (204, 86), (210, 104), (230, 104), (236, 86)], CHIARO)   # goccia
    s.rett(338, 66, 64, 38, CHIARO, 16)                          # pane
    for k in range(3):
        s.linea([(352 + k * 16, 72), (346 + k * 16, 98)], 1.4)


@passo('stop_6')
def _(s):
    s.terra(215, seme=6)
    s.omino(150, 215, 110, 'braccia_y')
    s.rett(70, 206, 70, 12, SCURO, 2, 1.4)                       # il telo colorato
    # l'elicottero
    s.ellisse(330, 60, 40, 20, CHIARO)
    s.linea([(290, 34), (372, 34)], 3)
    s.linea([(330, 34), (330, 40)], 3)
    s.linea([(370, 60), (420, 52)], 4)
    s.linea([(300, 82), (360, 82)], 3)
    for k in range(3):
        s.curva((380 + k * 10, 150 - k * 12), (392 + k * 12, 160), (380 + k * 10, 172 + k * 12), 2)
    s.rett(346, 152, 24, 14, '#fff', 6)
    s.testo(404, 196, '6x', 22)


# ============================================================================ coltello e intaglio

@passo('coltello_1')
def _(s):
    s.terra(235, ciuffi=0, seme=7)
    for x0, x1 in ((120, 196), (320, 244)):
        s.poly([(x0 - 26, 230), (x0 + 26, 230), (x1 + 22, 60), (x1 - 22, 60)], CHIARO)
    s.poly([(150, 225), (290, 225), (220, 80)], MEDIO, 2.4)
    s.segno_no(220, 170, 1.4)
    s.coltello(330, 30, 2.3, 110)
    s.freccia([(300, 70), (360, 40)], 2.2)


@passo('coltello_2')
def _(s):
    s.terra(215, seme=8)
    s.el.append('<ellipse cx="220" cy="120" rx="120" ry="88" fill="none" stroke="#000" stroke-width="1.8" stroke-dasharray="8 6"/>')
    s.omino(220, 215, 110, 'indica')
    s.coltello(268, 116, -0.35, 60)
    s.omino(390, 215, 80)
    s.segno_no(390, 100, 0.8)


@passo('coltello_3')
def _(s):
    s.legno((40, 130), (400, 120), 20, CHIARO)
    for x in (120, 210, 300):
        s.legno((x, 124), (x + 30, 92), 6, '#fff', testa=False)
    s.coltello(200, 190, -0.25, 120)
    s.freccia([(150, 160), (260, 120)], 2.2)


@passo('coltello_4')
def _(s):
    s.terra(222, ciuffi=2, seme=9)
    s.rett(120, 180, 200, 42, MEDIO, 6)
    s.ellisse(220, 180, 100, 12, CHIARO)
    p = s.legno((200, 30), (212, 180), 18, CHIARO, testa=False)
    s.poly([(202, 150), (222, 150), (212, 180)], '#fff', 1.6)
    s.coltello(290, 40, 2.2, 120)
    s.freccia([(270, 90), (236, 150)], 2.2)


@passo('coltello_5')
def _(s):
    s.legno((40, 125), (400, 125), 40, CHIARO)
    s.poly([(150, 105), (150, 125), (196, 105)], '#fff', 2)       # la tacca
    s.linea([(150, 105), (150, 125)], 3)
    s.freccia([(150, 50), (150, 96)], 2.2)
    s.freccia([(230, 60), (196, 100)], 2.2)
    s.testo(150, 36, '1', 20)
    s.testo(240, 48, '2', 20)


@passo('coltello_6')
def _(s):
    s.terra(200, seme=10)
    s.legno((150, 230), (230, 110), 14, CHIARO, testa=False)
    s.poly([(224, 112), (236, 108), (240, 118), (230, 122)], '#fff', 1.6)
    s.linea([(214, 136), (226, 140)], 3)
    s.linea([(226, 138), (380, 60)], 2)
    s.sasso(280, 196, 22, 12)
    s.freccia_curva((300, 160), (270, 120), (246, 108))


# ============================================================================ capanna di frasche

def _capanna(s, costole=True, graticcio=False, copertura=False):
    s.terra(215, seme=11)
    if copertura:
        pts = [(40, 215)] + [(40 + 340 * t, 215 - 120 * math.sin(math.pi * t) ** 0.6 * (1 - 0.6 * t)) for t in [k / 30 for k in range(31)]] + [(380, 215)]
        s.poly(pts, MEDIO, 2.6)
        for k in range(30):
            x = 60 + k * 10
            s.linea([(x, 190 - (k % 5) * 8), (x + 6, 186 - (k % 5) * 8)], 1.2)
    s.legno((60, 200), (60, 110), 16, CHIARO, testa=False)
    s.legno((48, 120), (70, 100), 8, CHIARO, testa=False)
    s.legno((60, 110), (380, 210), 14, CHIARO)
    if costole and not copertura:
        for k in range(9):
            x = 80 + k * 34
            y = 110 + (x - 60) * 100 / 320
            s.legno((x - 6, 214), (x + 4, y - 2), 6, '#fff', testa=False)
    if graticcio and not copertura:
        for k in range(4):
            s.linea([(80, 200 - k * 22), (330, 214 - k * 6)], 3)


@passo('capanna_1')
def _(s):
    _capanna(s, costole=False)
    s.omino(240, 214, 70)
    s.freccia([(60, 230), (380, 230)], 1.6)


@passo('capanna_2')
def _(s):
    _capanna(s)


@passo('capanna_3')
def _(s):
    _capanna(s, graticcio=True)


@passo('capanna_4')
def _(s):
    _capanna(s, copertura=True)
    s.freccia([(400, 60), (400, 120)], 2)
    s.freccia([(400, 120), (400, 60)], 2)


@passo('capanna_5')
def _(s):
    s.terra(215, seme=12)
    s.poly([(60, 215), (220, 30), (380, 215)], MEDIO, 2.6)
    s.poly([(150, 215), (220, 110), (290, 215)], '#fff', 2.2)
    s.poly([(156, 215), (284, 215), (276, 188), (164, 188)], CHIARO, 1.6)
    s.ellisse(220, 118, 8, 8, CHIARO)
    s.freccia([(330, 202), (296, 202)], 2)
    s.freccia([(110, 202), (146, 202)], 2)


@passo('capanna_6')
def _(s):
    s.terra(215, seme=13)
    s.poly([(60, 215), (220, 60), (380, 215)], MEDIO, 2.6)
    s.poly([(170, 215), (220, 150), (270, 215)], SCURO, 2)
    s.ellisse(120, 200, 50, 16, CHIARO)
    s.freccia_curva((110, 170), (150, 150), (190, 196))


# ============================================================================ l'acqua

@passo('acqua_1')
def _(s):
    s.poly([(0, 250), (0, 90), (120, 40), (220, 190), (320, 50), (440, 100), (440, 250)], CHIARO)
    s.poly([(170, 250), (210, 196), (232, 196), (270, 250)], '#fff', 1.8)
    for k in range(3):
        s.linea([(206 + k * 8, 210 + k * 12), (214 + k * 8, 206 + k * 12)], 1.4)
    for (x, y) in ((190, 176), (250, 172), (180, 196)):
        for a in (-0.6, 0, 0.6):
            s.linea([(x, y), (x + 14 * math.sin(a), y - 14 * math.cos(a))], 1.6)
    s.freccia([(60, 110), (180, 180)], 2)
    s.freccia([(380, 110), (262, 180)], 2)
    for x in (100, 300):
        s.curva((x, 30), (x + 8, 24), (x + 16, 30), 2)
        s.curva((x + 16, 30), (x + 24, 24), (x + 32, 30), 2)


@passo('acqua_2')
def _(s):
    s.rett(10, 20, 200, 210, '#fff', 12)
    s.rett(230, 20, 200, 210, '#fff', 12)
    s.poly([(20, 140), (80, 120), (200, 150), (200, 200), (20, 200)], '#ececec', 1.6)
    for k in range(4):
        s.freccia([(40 + k * 40, 160), (70 + k * 40, 170)], 1.8, 8)
    s.sasso(110, 190, 20, 10)
    s.ellisse(330, 170, 80, 24, MEDIO)
    s.linea([(300, 170), (310, 166)], 1.2)
    s.segno_ok(110, 70, 1.5)
    s.segno_no(330, 70, 1.5)


@passo('acqua_3')
def _(s):
    s.pentola(220, 150, 110, 70)
    s.poly([(170, 150), (270, 150), (250, 172), (190, 172)], CHIARO, 1.6)
    s.rett(260, 40, 40, 70, '#fff', 8)
    s.linea([(262, 112), (230, 140)], 1.6)
    for k in range(4):
        s.linea([(228 + k * 4, 128 + k * 3), (226 + k * 4, 140 + k * 3)], 1.4, tratteggio='3 4')


@passo('acqua_4')
def _(s):
    s.terra(215, ciuffi=0, seme=14)
    s.fuoco(120, 212, 40)
    s.pentola(120, 120, 80, 50)
    for k in range(4):
        s.ellisse(100 + k * 14, 112, 4, 4)
    s.rett(230, 80, 40, 110, '#fff', 8)
    s.rett(236, 60, 28, 22, CHIARO, 4)
    s.linea([(236, 120), (264, 120)], 1.4)
    for k in range(3):
        s.ellisse(330 + k * 30, 160, 12, 12, CHIARO)
    s.testo(120, 40, '1 MIN', 18)
    s.testo(250, 40, 'FILTRO', 14, 700)
    s.testo(360, 130, 'PASTIGLIE', 13, 700)


@passo('acqua_5')
def _(s):
    s.terra(160, ciuffi=26, seme=15)
    s.rett(60, 140, 90, 24, CHIARO, 4)
    s.freccia([(60, 180), (20, 180)], 2)
    s.pentola(300, 150, 100, 60)
    s.ellisse(300, 146, 40, 10, '#fff')
    for k in range(6):
        s.ellisse(270 + k * 12, 120 - (k % 2) * 8, 7, 5, '#fff')
    s.freccia([(300, 60), (300, 100)], 2)


@passo('acqua_6')
def _(s):
    for k in range(3):
        x = 110 + k * 110
        s.rett(x - 22, 70, 44, 130, CHIARO if k < 2 else '#fff', 10)
        s.rett(x - 12, 52, 24, 18, '#fff', 3)
    s.testo(220, 230, '2-3 LITRI AL GIORNO', 18, 900)


# ============================================================================ nord: orologio e stelle

@passo('nord_5')
def _(s):
    s.sole(380, 50)
    s.ellisse(190, 140, 90, 90, '#fff', 3)
    for k in range(12):
        a = k * math.pi / 6
        s.linea([(190 + 80 * math.cos(a), 140 + 80 * math.sin(a)), (190 + 88 * math.cos(a), 140 + 88 * math.sin(a))], 2)
    s.testo(190, 72, '12', 14, 800)
    ang_ore = math.atan2(50 - 140, 380 - 190)
    s.linea([(190, 140), (190 + 60 * math.cos(ang_ore), 140 + 60 * math.sin(ang_ore))], 6)
    s.linea([(190, 140), (190, 66)], 2)
    bis = (ang_ore + (-math.pi / 2)) / 2
    s.linea([(190 - 120 * math.cos(bis), 140 - 120 * math.sin(bis)), (190 + 120 * math.cos(bis), 140 + 120 * math.sin(bis))], 1.6, tratteggio='8 6')
    s.testo(190 + 104 * math.cos(bis) + 6, 140 + 104 * math.sin(bis) - 14, 'S', 20)
    s.testo(190 - 104 * math.cos(bis) - 6, 140 - 104 * math.sin(bis) + 14, 'N', 20)


@passo('nord_6')
def _(s):
    s.rett(-2, -2, 444, 254, SCURO, sw=0)
    s.rett(-2, 220, 444, 32, NERO, sw=0)
    carro = [(60, 150), (110, 140), (150, 150), (190, 166), (196, 206), (250, 210), (256, 170)]
    s.linea(carro, 1.2, '#fff')
    s.linea([(190, 166), (256, 170)], 1.2, '#fff')
    for (x, y) in carro:
        s.ellisse(x, y, 5, 5, '#fff', 0)
    s.linea([(256, 170), (276, 40)], 1.6, '#fff')
    s.el.append('<polyline points="256,170 276,40" stroke="#fff" stroke-width="1.6" stroke-dasharray="8 6" fill="none"/>')
    s.ellisse(276, 40, 9, 9, '#fff', 0)
    s.linea([(276, 50), (276, 214)], 1.4, '#fff', '4 6')
    s.el.append('<text x="276" y="236" font-family="Inter, Arial" font-size="20" font-weight="900" fill="#fff" text-anchor="middle" dominant-baseline="middle">N</text>')


# ============================================================================ tipi di fuoco

@passo('tipifuoco_4')
def _(s):
    s.terra(215, seme=16)
    s.fuoco(200, 212, 50)
    for x in (90, 130):
        s.legno((x, 216), (x, 120), 8, CHIARO, testa=False)
    for k in range(5):
        s.legno((70, 206 - k * 18), (150, 206 - k * 18), 16, CHIARO)
    s.poly([(300, 214), (300, 120), (420, 214)], CHIARO)
    s.omino(340, 214, 60, 'seduto')
    for k in range(3):
        s.freccia([(150, 120 + k * 20), (300, 120 + k * 20)], 1.6, 8)


@passo('tipifuoco_5')
def _(s):
    s.terra(180, ciuffi=0, seme=17)
    # tepee
    for k in range(5):
        s.legno((60 + k * 12, 178), (84, 110), 5, '#fff', testa=False)
    s.fiamma(84, 172, 30, 20)
    # capanna
    for k in range(3):
        s.legno((180, 176 - k * 14), (260, 176 - k * 14), 10, CHIARO)
    s.fiamma(220, 170, 30, 26)
    # stella
    for a in range(5):
        t = a * 2 * math.pi / 5
        s.legno((360 + 60 * math.cos(t), 168 + 14 * math.sin(t)), (360, 168), 10, CHIARO, testa=False)
    s.fiamma(360, 168, 30, 24)
    for x, t in ((84, 'PARTIRE'), (220, 'CUCINARE'), (360, 'NOTTE')):
        s.testo(x, 220, t, 15, 900)


# ============================================================================ Dakota (in sezione)

def _dakota(s, chiuso=False):
    s.rett(-2, 95, 444, 160, CHIARO, sw=0)
    s.linea([(0, 95), (440, 95)], 2)
    if not chiuso:
        s.poly([(120, 95), (160, 95), (262, 200), (250, 210), (236, 204)], '#fff', 1.8)
        s.rett(250, 92, 80, 118, '#fff', 6, 1.8)
    else:
        s.rett(250, 82, 80, 14, MEDIO, 3, 1.8)
        for x in range(254, 330, 8):
            s.linea([(x, 82), (x + 2, 74)], 1.2)
        s.linea([(250, 96), (250, 210), (330, 210), (330, 96)], 1.4, tratteggio='6 6')


@passo('dakota_4')
def _(s):
    _dakota(s)
    s.fiamma(290, 206, 90, 50)
    for k in range(2):
        s.legno((236, 92 - k * 9), (344, 92 - k * 9), 8, CHIARO)
    s.pentola(290, 36, 70, 40)


@passo('dakota_5')
def _(s):
    _dakota(s, chiuso=True)
    s.poly([(180, 30), (166, 56), (171, 72), (189, 72), (194, 56)], CHIARO)
    s.freccia([(200, 60), (250, 80)], 2)
    s.segno_ok(380, 50, 1.4)


# ============================================================================ archetto

def _tavola(s, buco=True, nero=False, tacca=False):
    s.poly([(60, 150), (380, 150), (420, 200), (100, 200)], CHIARO, 2.2)
    if buco:
        s.ellisse(170, 172, 20, 8, SCURO if nero else '#fff', 1.8)
    if tacca:
        s.poly([(148, 196), (170, 172), (192, 196)], '#fff', 1.8)


@passo('archetto_2')
def _(s):
    _tavola(s, buco=True)
    s.coltello(250, 60, 2.2, 140)
    s.freccia_curva((230, 140), (210, 150), (190, 166))


@passo('archetto_4')
def _(s):
    _tavola(s, nero=True)
    s.legno((170, 168), (170, 30), 18, CHIARO, testa=False)
    for k in range(3):
        s.curva((140 + k * 30, 140), (130 + k * 30, 110), (146 + k * 30, 90), 1.6)


@passo('archetto_5')
def _(s):
    s.poly([(60, 60), (380, 60), (380, 200), (60, 200)], CHIARO, 2.2)
    s.ellisse(220, 130, 34, 34, SCURO, 2)
    s.poly([(196, 200), (220, 130), (244, 200)], '#fff', 2)
    s.rett(160, 200, 120, 30, MEDIO, 4)
    s.freccia([(300, 230), (250, 214)], 2)


@passo('archetto_6')
def _(s):
    s.rett(140, 190, 160, 34, MEDIO, 4)
    s.ellisse(220, 186, 34, 12, SCURO)
    s.ellisse(220, 180, 8, 5, NERO)
    for k in range(4):
        s.curva((196 + k * 16, 170), (188 + k * 16, 130), (206 + k * 16, 90), 1.6)
    s.testo(330, 120, '+20', 26)


@passo('archetto_7')
def _(s):
    s.ellisse(220, 150, 110, 50, CHIARO, 2.2)
    for k in range(18):
        a = k * 2 * math.pi / 18
        s.curva((220 + 100 * math.cos(a), 150 + 44 * math.sin(a)), (220 + 60 * math.cos(a + 0.5), 150 + 26 * math.sin(a + 0.5)), (220 + 30 * math.cos(a + 1), 150 + 12 * math.sin(a + 1)), 1.2)
    s.ellisse(220, 150, 12, 7, NERO)
    s.freccia([(400, 90), (320, 130)], 2.2)
    for k in range(3):
        s.curva((190 + k * 24, 110), (180 + k * 24, 70), (198 + k * 24, 30), 1.6)


# ============================================================================ batoning

def _ceppo(s):
    s.terra(222, ciuffi=4, seme=18)
    s.rett(110, 180, 220, 42, MEDIO, 6)
    s.ellisse(220, 180, 110, 12, CHIARO)


@passo('batoning_1')
def _(s):
    _ceppo(s)
    s.rett(190, 80, 60, 100, CHIARO, 4)
    s.ellisse(220, 80, 30, 7, '#fff')
    s.freccia([(300, 60), (256, 90)], 2)


@passo('batoning_4')
def _(s):
    _ceppo(s)
    s.poly([(170, 30), (214, 30), (214, 180), (180, 180)], CHIARO)
    s.poly([(226, 30), (270, 30), (260, 180), (226, 180)], CHIARO)
    s.poly([(210, 40), (230, 40), (222, 110)], '#fff', 1.8)
    s.legno((300, 0), (236, 44), 18, MEDIO)


@passo('batoning_5')
def _(s):
    for k, (cx, n) in enumerate(((110, 1), (220, 2), (330, 4))):
        s.ellisse(cx, 125, 46, 46, CHIARO)
        for j in range(n if n > 1 else 0):
            a = j * math.pi / (n / 2 if n > 2 else 1) if n > 2 else math.pi / 2
            s.linea([(cx + 46 * math.cos(a), 125 + 46 * math.sin(a)), (cx - 46 * math.cos(a), 125 - 46 * math.sin(a))], 2.4)
        if k < 2:
            s.freccia([(cx + 56, 125), (cx + 88, 125)], 2)
    s.ellisse(330, 125, 12, 12, '#fff')
    s.testo(330, 210, 'ASCIUTTO DENTRO', 14, 900)


# ============================================================================ cordino di fibre

def _cordino(s, x0, x1, y, giri=14, sp=10):
    passo_ = (x1 - x0) / giri
    for k in range(giri):
        x = x0 + k * passo_
        s.poly([(x, y - sp / 2), (x + passo_ * 0.9, y - sp / 2), (x + passo_ * 0.4, y + sp / 2), (x - passo_ * 0.5, y + sp / 2)],
               CHIARO if k % 2 else '#fff', 1.4)


@passo('cordame_1')
def _(s):
    s.legno((30, 90), (410, 90), 14, CHIARO, testa=False)
    s.poly([(30, 150), (410, 150), (410, 166), (30, 166)], '#fff', 1.8)
    for k in range(8):
        s.curva((60 + k * 40, 166), (70 + k * 40, 200), (50 + k * 40, 230), 1.2)
    s.freccia([(220, 106), (220, 140)], 2)


@passo('cordame_2')
def _(s):
    for k in range(6):
        s.linea([(60, 100 + k * 3), (300, 100 + k * 3)], 1.2)
        s.linea([(60, 140 + k * 3), (200, 140 + k * 3)], 1.2)
    s.curva((60, 100), (20, 128), (60, 155), 2)
    s.linea([(200, 130), (200, 175)], 1, tratteggio='4 4')
    s.testo(130, 200, '1/3', 20)
    s.testo(250, 70, '2/3', 20)


@passo('cordame_3')
def _(s):
    _cordino(s, 70, 140, 128, 4, 12)
    for k in range(6):
        s.linea([(140, 120 + k * 3), (360, 108 + k * 3)], 1.2)
        s.linea([(140, 128 + k * 3), (360, 150 + k * 3)], 1.2)
    s.freccia_curva((300, 70), (340, 90), (320, 110))


@passo('cordame_4')
def _(s):
    _cordino(s, 50, 300, 125, 14, 16)
    s.freccia_curva((330, 60), (370, 80), (350, 104))
    s.freccia_curva((340, 190), (300, 170), (320, 144))
    s.testo(380, 50, '1', 20)
    s.testo(380, 210, '2', 20)


@passo('cordame_5')
def _(s):
    _cordino(s, 40, 240, 125, 11, 16)
    for k in range(5):
        s.linea([(200, 108 + k * 2), (400, 98 + k * 2)], 1.2)
    s.freccia([(300, 70), (260, 100)], 2)


@passo('cordame_6')
def _(s):
    _cordino(s, 60, 380, 125, 18, 14)
    s.ellisse(60, 125, 12, 12, CHIARO)
    s.freccia([(392, 125), (432, 125)], 2.4)
    s.freccia([(48, 160), (10, 160)], 2.4)


# ============================================================================ gancio per il pentolino

def _forcelle(s):
    s.terra(215, seme=19)
    s.fuoco(220, 212, 30)
    for x in (90, 350):
        s.legno((x, 214), (x, 60), 10, CHIARO, testa=False)
        s.legno((x, 72), (x - 16, 48), 8, CHIARO, testa=False)
        s.legno((x, 72), (x + 16, 48), 8, CHIARO, testa=False)


@passo('gancio_2')
def _(s):
    _forcelle(s)
    s.legno((70, 62), (372, 62), 10, MEDIO)
    s.freccia([(220, 20), (220, 52)], 2)


@passo('gancio_3')
def _(s):
    s.legno((220, 20), (220, 230), 18, CHIARO, testa=False)
    s.legno((220, 190), (262, 150), 10, CHIARO, testa=False)
    s.linea([(262, 150), (262, 140)], 1.6)
    s.curva((150, 200), (240, 150), (300, 200), 3)
    s.freccia([(330, 120), (272, 150)], 2)


@passo('gancio_4')
def _(s):
    s.legno((200, 20), (200, 230), 16, CHIARO, testa=False)
    for k, y in enumerate((60, 110, 160)):
        s.poly([(208, y), (208, y + 14), (224, y)], '#fff', 1.6)
        s.testo(260, y + 6, str(k + 1), 18)
    s.legno((150, 60), (300, 60), 10, MEDIO)


@passo('gancio_5')
def _(s):
    _forcelle(s)
    s.legno((70, 62), (372, 62), 10, SCURO)
    s.legno((220, 62), (220, 140), 8, CHIARO, testa=False)
    s.pentola(220, 150, 64, 40)
    s.freccia([(300, 20), (250, 56)], 2)
