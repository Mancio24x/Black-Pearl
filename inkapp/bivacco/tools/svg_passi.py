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


# ============================================================================ TARP

def _alberi(s, y=215):
    s.terra(y, seme=40)
    s.albero(50, 30, y)
    s.albero(390, 30, y)


def _dorsale(s, y=110):
    s.linea([(64, y), (220, y + 6), (376, y)], 3)


@passo('tarp_corde_1')
def _(s):
    s.telo([(80, 50), (360, 50), (360, 200), (80, 200)])
    s.linea([(220, 50), (220, 200)], 1.2, tratteggio='6 5')
    for (x, y, dx, dy) in ((80, 50, -1, -1), (360, 50, 1, -1), (80, 200, -1, 1), (360, 200, 1, 1), (220, 50, 0, -1), (220, 200, 0, 1)):
        s.ellisse(x, y, 5, 5, '#fff')
        s.linea([(x, y), (x + 20 * dx, y + 20 * dy)], 2)
        s.ellisse(x + 30 * dx, y + 26 * dy if dy else y, 11, 7, 'none', 2)


@passo('tarp_corde_2')
def _(s):
    s.albero(70, 34)
    s.terra(215, seme=41)
    s.corda([(86, 110), (100, 98), (110, 112), (100, 124), (86, 116)], larg=7, capo=2, chiusa=True)
    s.corda([(110, 112), (200, 120), (300, 126)], larg=7, capo=2)
    for k in range(4):
        s.ellisse(350, 150, 40 - k * 6, 26 - k * 4, 'none', 3)


@passo('tarp_corde_3')
def _(s):
    s.linea([(20, 90), (420, 90)], 5)
    for x in (130, 310):
        s.giri((x - 12, 76), (x - 12, 104), 4, 6, dx=8)
        s.linea([(x, 104), (x, 150)], 2.4)
        s.ellisse(x, 156, 8, 8, '#fff')
    s.telo([(130, 160), (310, 160), (350, 230), (90, 230)])
    s.freccia([(130, 50), (70, 50)], 2)
    s.freccia([(310, 50), (370, 50)], 2)


@passo('tarp_corde_4')
def _(s):
    s.terra(205, seme=42)
    s.picchetto(330, 206)
    s.linea([(60, 40), (324, 188)], 2.4)
    s.giri((206, 106), (220, 128), 3, 6, dx=10, dy=6)
    s.curva((324, 188), (346, 176), (262, 150), 2.4)
    s.telo([(20, 20), (80, 20), (60, 60)])
    s.freccia([(150, 150), (110, 128)], 2)


@passo('tarp_corde_5')
def _(s):
    s.rett(80, 100, 280, 70, CHIARO, 35)
    for k in range(5):
        s.curva((120 + k * 50, 100), (110 + k * 50, 135), (120 + k * 50, 170), 1.2)
    s.ellisse(360, 135, 18, 35, '#fff')
    s.ellisse(360, 135, 6, 12, MEDIO)
    s.linea([(140, 100), (140, 170)], 3)
    s.linea([(300, 100), (300, 170)], 3)


@passo('tarp_a_1')
def _(s):
    _alberi(s)
    _dorsale(s, 130)
    s.omino(220, 214, 90, 'seduto')
    s.freccia([(300, 214), (300, 136)], 1.8)
    s.freccia([(300, 136), (300, 214)], 1.8)
    s.testo(330, 176, '1 m', 18, 800)


@passo('tarp_a_3')
def _(s):
    s.terra(215, seme=43)
    s.albero(220, 26)
    s.telo([(220, 68), (90, 212), (220, 212)])
    s.poly([(220, 68), (250, 110), (260, 150)], '#fff', 2.4, chiuso=False)
    s.picchetto(88, 214)
    s.picchetto(140, 216)
    s.freccia([(300, 120), (350, 200)], 2)


@passo('tarp_diamante_2')
def _(s):
    s.terra(215, seme=44)
    s.albero(60, 30)
    s.telo([(80, 110), (210, 140), (392, 210), (200, 190)])
    s.picchetto(396, 214)
    s.scritta_vento = None
    for k in range(3):
        s.linea([(380 + 0, 60 + k * 12), (420, 60 + k * 12)], 1.4)
    s.freccia([(430, 90), (400, 120)], 2)


@passo('tarp_diamante_4')
def _(s):
    s.terra(215, seme=45)
    s.albero(60, 30)
    s.telo([(80, 110), (210, 212), (390, 210)])
    s.omino(250, 212, 60, 'seduto')
    s.legno((150, 212), (150, 160), 6, '#fff', testa=False)
    s.freccia([(110, 60), (90, 100)], 2)


@passo('tarp_vomere_2')
def _(s):
    s.terra(215, seme=46)
    s.albero(70, 30)
    s.telo([(92, 80), (380, 212), (150, 212)], MEDIO)
    s.picchetto(150, 216)
    s.picchetto(384, 214)
    for k in range(3):
        s.linea([(400, 50 + k * 12), (430, 50 + k * 12)], 1.4)


@passo('tarp_vomere_4')
def _(s):
    s.terra(215, seme=47)
    s.albero(70, 30)
    s.telo([(92, 80), (380, 212), (150, 212)], MEDIO)
    s.telo([(92, 80), (260, 214), (380, 212)])
    s.omino(230, 212, 54, 'seduto')
    s.rett(280, 186, 26, 26, SCURO, 6)


@passo('tarp_cucina_1')
def _(s):
    _alberi(s)
    s.linea([(64, 50), (220, 56), (376, 50)], 3)
    s.omino(220, 214, 90)
    s.freccia([(300, 60), (300, 120)], 1.8)


@passo('tarp_cucina_2')
def _(s):
    _alberi(s)
    s.linea([(64, 50), (220, 56), (376, 50)], 3)
    s.telo([(90, 56), (350, 56), (360, 150), (80, 150)])
    for x in (130, 310):
        s.giri((x - 10, 46), (x - 10, 64), 3, 5, dx=7)


@passo('tarp_cucina_4')
def _(s):
    _alberi(s)
    s.linea([(64, 50), (220, 56), (376, 50)], 3)
    s.telo([(90, 56), (350, 56), (380, 118), (60, 118)])
    for x in (70, 370):
        s.legno((x, 214), (x, 118), 7, '#fff', testa=False)
        s.linea([(x, 118), (x + (40 if x > 200 else -40), 214)], 1.6)
    s.fuoco(220, 212, 26) if False else None
    s.omino(220, 214, 80)


@passo('tarp_pioggia_1')
def _(s):
    s.terra(215, seme=48)
    s.nuvola(40, 40)
    s.pioggia(20, 150, 70, 150)
    for k in range(3):
        s.freccia([(20, 110 + k * 20), (110, 140 + k * 14)], 2, 9)
    s.telo([(140, 214), (360, 90), (380, 120)])
    s.legno((360, 214), (360, 92), 7, '#fff', testa=False)
    s.omino(310, 214, 60, 'seduto')


@passo('tarp_pioggia_2')
def _(s):
    s.rett(10, 20, 200, 210, '#fff', 12)
    s.rett(230, 20, 200, 210, '#fff', 12)
    s.poly([(30, 90), (190, 90)] + [(190 - k * 16, 90 + 40 * math.sin(math.pi * k / 10)) for k in range(11)], CHIARO)
    s.ellisse(110, 112, 40, 12, SCURO)
    s.segno_no(110, 180, 1.4)
    s.poly([(250, 70), (410, 70), (410, 130), (250, 150)], CHIARO)
    s.segno_ok(330, 190, 1.4)


@passo('tarp_pioggia_3')
def _(s):
    s.linea([(10, 70), (430, 70)], 4)
    s.telo([(160, 70), (430, 70), (430, 200), (160, 200)])
    s.linea([(110, 70), (110, 120)], 3)
    for k in range(4):
        s.poly([(110, 132 + k * 22), (104, 144 + k * 22), (116, 144 + k * 22)], CHIARO, 1.2)
    s.freccia([(30, 54), (100, 64)], 2)
    s.segno_ok(60, 180, 1.2)


@passo('tarp_pioggia_4')
def _(s):
    s.poly([(0, 250), (0, 150), (140, 200), (300, 150), (440, 150), (440, 250)], CHIARO)
    s.ellisse(140, 204, 50, 10, SCURO)
    s.segno_no(140, 120, 1.2)
    s.poly([(330, 150), (360, 90), (390, 150)], '#fff')
    s.segno_ok(360, 60, 1.2)


@passo('tarp_pioggia_5')
def _(s):
    s.terra(215, seme=49)
    s.telo([(60, 214), (220, 80), (380, 214)])
    s.picchetto(384, 216)
    s.linea([(330, 180), (384, 214)], 2)
    s.freccia([(380, 160), (420, 190)], 2.4)
    s.ellisse(80, 70, 30, 30, '#fff')
    s.linea([(80, 70), (80, 50)], 2.4)
    s.linea([(80, 70), (96, 76)], 2.4)


# ============================================================================ TRAPPOLE

@passo('regole_1')
def _(s):
    s.rett(60, 40, 150, 180, '#fff', 8)
    s.testo(135, 100, '§', 70)
    s.testo(135, 170, 'L. 157/1992', 16, 800)
    s.ellisse(320, 130, 80, 80, '#fff', 6)
    s.linea([(264, 186), (376, 74)], 6)
    s.giri((290, 120), (350, 120), 1, 8)
    s.ellisse(320, 140, 26, 18, 'none', 3)


@passo('regole_2')
def _(s):
    s.rett(160, 30, 120, 200, '#fff', 18, 3)
    s.rett(172, 56, 96, 140, CHIARO, 4)
    s.testo(220, 126, '112', 40)
    s.ellisse(220, 212, 8, 8)


@passo('regole_3')
def _(s):
    voci = [('RIPARO', 1), ('FARSI TROVARE', 2), ('ACQUA', 3), ('FUOCO', 4), ('CIBO', 5)]
    for k, (t, n) in enumerate(voci):
        y = 24 + k * 44
        s.rett(80, y, 280, 36, CHIARO if k < 4 else '#fff', 8)
        s.testo(110, y + 18, str(n), 18)
        s.testo(240, y + 18, t, 16, 800)
    s.freccia([(390, 30), (390, 210)], 2.4)


@passo('regole_4')
def _(s):
    s.terra(215, seme=50)
    s.poly([(120, 40), (60, 150), (180, 150)], '#fff', 4)
    s.testo(120, 112, '!', 50)
    s.ellisse(320, 178, 50, 22, CHIARO)          # un cane stilizzato
    s.ellisse(370, 150, 18, 16, CHIARO)
    for x in (290, 340):
        s.linea([(x, 196), (x, 214)], 4)
    s.linea([(272, 170), (256, 154)], 4)


@passo('laccio_1')
def _(s):
    s.terra(180, ciuffi=20, seme=51)
    s.ellisse(110, 150, 90, 50, MEDIO)
    s.ellisse(330, 150, 90, 50, MEDIO)
    for k in range(6):
        x = 60 + k * 64
        s.ellisse(x, 210 + (k % 2) * 10, 6, 4, SCURO, 1)
        s.ellisse(x + 10, 214 + (k % 2) * 10, 4, 3, SCURO, 1)
    s.linea([(20, 200), (420, 220)], 1.4, tratteggio='8 8')


@passo('laccio_2')
def _(s):
    s.ellisse(220, 125, 90, 70, 'none', 2.6)
    s.ellisse(220, 125, 90, 70, 'none', 2.6)
    s.ellisse(130, 125, 8, 6, '#fff', 2.4)
    s.linea([(130, 125), (40, 125)], 2.6)
    s.freccia([(80, 160), (122, 132)], 2)


@passo('laccio_3')
def _(s):
    s.terra(215, seme=52)
    s.ellisse(220, 140, 45, 45, 'none', 2.6)
    s.linea([(220, 95), (220, 40)], 2.6)
    s.freccia([(160, 140), (176, 140)], 1.6)
    s.freccia([(280, 140), (264, 140)], 1.6)
    s.linea([(290, 186), (290, 214)], 1.6)
    s.testo(340, 200, 'UN PUGNO', 13, 800)
    s.testo(220, 80, 'UNA MANO', 13, 800)


@passo('laccio_4')
def _(s):
    s.terra(215, seme=53)
    s.legno((220, 230), (220, 30), 10, CHIARO, testa=False)
    s.linea([(220, 40), (250, 120)], 2.4)
    s.ellisse(250, 150, 30, 30, 'none', 2.6)
    for x in (180, 330):
        for k in range(3):
            s.legno((x + k * 8, 214), (x + k * 8 - 4, 140), 5, '#fff', testa=False)


@passo('laccio_5')
def _(s):
    s.ellisse(140, 120, 70, 70, '#fff', 3)
    for k in range(12):
        a = k * math.pi / 6
        s.linea([(140 + 60 * math.cos(a), 120 + 60 * math.sin(a)), (140 + 66 * math.cos(a), 120 + 66 * math.sin(a))], 2)
    s.linea([(140, 120), (140, 70)], 4)
    s.linea([(140, 120), (180, 120)], 4)
    s.testo(140, 220, '2 VOLTE AL GIORNO', 13, 800)
    s.ellisse(330, 120, 40, 40, 'none', 2.6)
    s.segno_no(330, 120, 2)


@passo('pesca_1')
def _(s):
    s.legno((40, 120), (150, 120), 8, CHIARO, testa=False)
    s.linea([(110, 120), (140, 96)], 2.4)
    s.linea([(200, 60), (200, 160), (230, 190), (260, 160)], 3)
    s.linea([(310, 125), (420, 125)], 3)
    s.linea([(310, 125), (300, 118)], 3)
    s.linea([(420, 125), (430, 132)], 3)
    s.linea([(365, 125), (365, 60)], 1.6)
    s.testo(95, 200, 'SPINA', 13, 800)
    s.testo(230, 220, 'FERMAGLIO', 13, 800)
    s.testo(365, 200, 'BASTONCINO', 13, 800)


@passo('pesca_2')
def _(s):
    s.linea([(220, 20), (220, 140), (250, 180), (280, 140)], 3)
    s.poly([(204, 120), (250, 112), (290, 136), (276, 164), (236, 160), (210, 140)], CHIARO)
    for k in range(4):
        s.curva((214 + k * 18, 120), (220 + k * 18, 140), (214 + k * 18, 158), 1.2)


@passo('pesca_3')
def _(s):
    s.acqua(0, 250, 6)
    for (x, y) in ((120, 110), (300, 160)):
        s.sasso(x, y, 50, 30)
        s.poly([(x + 70, y), (x + 100, y - 10), (x + 120, y), (x + 100, y + 10)], SCURO)
        s.poly([(x + 120, y), (x + 132, y - 8), (x + 132, y + 8)], SCURO)
    s.freccia([(400, 40), (300, 60)], 2.4)


@passo('pesca_4')
def _(s):
    s.acqua(150, 250, 4)
    s.terra(150, ciuffi=0, seme=54)
    s.rett(0, 150, 120, 100, CHIARO, sw=0)
    s.omino(60, 150, 90)
    s.legno((76, 90), (300, 20), 6, MEDIO, testa=False)
    s.linea([(300, 20), (320, 200)], 1.4)
    s.ellisse(320, 204, 6, 4, SCURO, 1)


@passo('pesca_5')
def _(s):
    s.acqua(130, 250, 4)
    s.rett(0, 110, 440, 22, CHIARO, sw=0)
    s.linea([(0, 130), (440, 130)], 2)
    for x in (80, 220, 360):
        s.legno((x, 132), (x + 30, 60), 6, MEDIO, testa=False)
        s.linea([(x + 30, 60), (x + 50, 200)], 1.4)
        s.ellisse(x + 50, 204, 5, 4, SCURO, 1)


def _paiute_pezzi(s, montato=False, scattato=False):
    s.terra(215, seme=55)
    if scattato:
        s.poly([(60, 214), (300, 196), (306, 214)], MEDIO)
        return
    s.legno((200, 214), (200, 110), 9, CHIARO, testa=False)             # montante
    s.legno((150, 104), (290, 110), 8, CHIARO)                          # leva
    s.linea([(150, 104), (150, 170)], 1.6)                              # cordino
    s.legno((138, 174), (180, 174), 5, '#fff', testa=False)            # levetta
    s.legno((180, 176), (290, 196), 4, '#fff', testa=False)            # bastoncino dell'esca
    s.poly([(290, 112), (300, 104), (420, 206), (404, 214)], MEDIO)    # pietra


@passo('paiute_1')
def _(s):
    s.terra(225, ciuffi=0, seme=56)
    s.legno((40, 50), (190, 50), 9, CHIARO)
    s.legno((40, 100), (220, 100), 8, CHIARO)
    s.legno((40, 150), (90, 150), 5, '#fff')
    s.legno((40, 195), (300, 195), 4, '#fff')
    s.linea([(260, 50), (260, 130)], 1.6)
    for y, t in ((50, 'MONTANTE'), (100, 'LEVA'), (150, 'LEVETTA'), (195, 'ESCA')):
        s.testo(370, y, t, 13, 800)


@passo('paiute_2')
def _(s):
    s.terra(215, seme=57)
    s.legno((200, 214), (200, 110), 9, CHIARO, testa=False)
    s.legno((150, 104), (290, 110), 8, CHIARO)
    s.linea([(150, 104), (150, 170)], 1.6)
    s.poly([(290, 112), (300, 104), (420, 206), (404, 214)], MEDIO)


@passo('paiute_3')
def _(s):
    _paiute_pezzi(s)
    s.ellisse(150, 174, 26, 18, 'none', 1.4)


@passo('paiute_4')
def _(s):
    _paiute_pezzi(s)
    s.ellisse(286, 196, 10, 6, SCURO)
    s.freccia([(250, 240), (282, 204)], 2)


@passo('paiute_5')
def _(s):
    _paiute_pezzi(s, scattato=True)
    s.freccia_curva((330, 60), (360, 100), (300, 180))
    s.legno((80, 60), (180, 60), 6, '#fff', testa=False)
    s.freccia([(186, 60), (240, 150)], 2)


def _pioli(s, x, y, agganciati=True):
    s.legno((x, y + 70), (x, y - 10), 10, CHIARO, testa=False)
    s.poly([(x - 5, y + 6), (x + 5, y + 6), (x + 5, y + 16)], '#fff', 1.4)
    if agganciati:
        s.legno((x + 8, y + 2), (x + 70, y + 2), 10, CHIARO, testa=False)


@passo('molla_1')
def _(s):
    s.terra(205, seme=58)
    _pioli(s, 160, 130, agganciati=False)
    s.legno((240, 90), (330, 90), 10, CHIARO)
    s.poly([(250, 85), (250, 95), (262, 85)], '#fff', 1.4)
    s.linea([(330, 90), (380, 40)], 1.8)
    s.freccia([(230, 110), (176, 140)], 2)


@passo('molla_2')
def _(s):
    s.terra(215, seme=59)
    s.curva((380, 214), (380, 80), (200, 60), 7)
    s.linea([(200, 60), (180, 160)], 1.8)
    s.freccia_curva((300, 20), (250, 20), (210, 50))


@passo('molla_3')
def _(s):
    s.terra(215, seme=60)
    s.curva((380, 214), (380, 80), (200, 60), 7)
    _pioli(s, 160, 150)
    s.linea([(200, 60), (200, 150)], 1.8)


@passo('molla_4')
def _(s):
    s.terra(215, seme=61)
    s.curva((380, 214), (380, 80), (200, 60), 7)
    _pioli(s, 160, 150)
    s.linea([(200, 60), (200, 150)], 1.8)
    s.linea([(230, 152), (260, 170)], 1.6)
    s.ellisse(260, 190, 22, 22, 'none', 2.4)


@passo('molla_5')
def _(s):
    s.terra(215, seme=62)
    s.linea([(380, 214), (380, 30)], 7)
    s.legno((80, 214), (180, 120), 8, '#fff', testa=False)
    s.freccia([(186, 116), (230, 80)], 2)
    s.segno_ok(80, 60, 1.2)


@passo('nassa_2')
def _(s):
    s.rett(110, 90, 220, 80, '#fff', 30, 2.4)
    s.poly([(112, 94), (180, 122), (196, 126), (196, 134), (180, 138), (112, 166)], CHIARO, 1.8)
    for k in range(3):
        s.linea([(118, 98 + k * 28), (130, 104 + k * 28)], 2)
    for x in range(220, 320, 18):
        for y in (110, 150):
            s.ellisse(x, y, 3, 3, NERO, 0)


@passo('nassa_3')
def _(s):
    s.rett(110, 90, 220, 80, '#fff', 30, 2.4)
    s.poly([(112, 94), (180, 122), (196, 126), (196, 134), (180, 138), (112, 166)], CHIARO, 1.8)
    for k in range(3):
        s.curva((240 + k * 20, 140), (250 + k * 20, 150), (240 + k * 20, 160), 2)
    s.freccia([(300, 40), (270, 120)], 2)


@passo('nassa_4')
def _(s):
    s.acqua(40, 210, 5)
    s.rett(0, 210, 440, 40, CHIARO, sw=0)
    s.linea([(0, 210), (440, 210)], 2)
    s.rett(150, 140, 180, 60, '#fff', 26, 2.4)
    s.poly([(152, 144), (200, 164), (200, 176), (152, 196)], CHIARO, 1.6)
    for x in (140, 340):
        s.sasso(x, 204, 22, 12)
    s.freccia([(420, 80), (300, 80)], 2.4)
    s.freccia([(60, 170), (140, 170)], 1.8)


@passo('nassa_5')
def _(s):
    s.poly([(60, 60), (380, 90), (380, 160), (60, 190)], '#fff', 2.4)
    for k in range(10):
        x = 80 + k * 30
        s.linea([(x, 62 + k * 3), (x, 188 - k * 3)], 1.4)
    for k in range(4):
        s.curva((60, 80 + k * 30), (220, 100 + k * 18), (380, 100 + k * 18), 1.4)
    s.poly([(60, 60), (140, 116), (140, 134), (60, 190)], CHIARO, 1.8)


# ============================================================================ NODI (corde in 3D)

from svgkit import giro, _catmull3  # noqa: E402


def fr(ctrl, k):
    """la frazione di corda che arriva al punto di controllo k (per disegnare un nodo fino a lì)"""
    pts = _catmull3(ctrl)
    L = [0.0]
    for i in range(1, len(pts)):
        L.append(L[-1] + math.dist(pts[i][:2], pts[i - 1][:2]))
    return L[min(len(pts) - 1, k * 12)] / L[-1] + 0.002


def palo_v(s, cx=220, w=30, y0=0, y1=250):
    return lambda: s.legno((cx, y1), (cx, y0), w, CHIARO, testa=False)


def _parlato():
    c = [(20, 92, 10), (120, 92, 10), (178, 92, 20)]
    c += giro(220, 0, 'v', -60, 90, 28, 92, 100)
    i1 = len(c)
    c += giro(220, 0, 'v', 90, 270, 28, 102, 150)[1:]
    i2 = len(c)
    c += giro(220, 0, 'v', 270, 450, 34, 150, 66)[1:]
    i3 = len(c)
    c += giro(220, 0, 'v', 450, 630, 28, 66, 124)[1:]
    i4 = len(c)
    c += giro(220, 0, 'v', 630, 780, 24, 124, 130)[1:]
    c += [(270, 130, 10), (340, 130, 10), (430, 130, 10)]
    return c, (i1, i2, i3, i4)


def _disegna_parlato(s, k=None, freccia=None):
    c, idx = _parlato()
    f = 1.0 if k is None else fr(c, idx[k])
    s.corde3d([c], oggetto=palo_v(s, 220, 40), fino=[f], capo=[0.3], dietro_x=(200, 240), larg=14, dopo=freccia)


@passo('parlato_1')
def _(s): _disegna_parlato(s, 1, [(150, 170), (186, 152)])


@passo('parlato_2')
def _(s): _disegna_parlato(s, 2, [(150, 40), (182, 62)])


@passo('parlato_3')
def _(s): _disegna_parlato(s, 3, [(150, 170), (186, 130)])


@passo('parlato_4')
def _(s): _disegna_parlato(s)


@passo('parlato_5')
def _(s):
    _disegna_parlato(s)
    s.freccia([(70, 70), (20, 70)], 2.4)
    s.freccia([(370, 150), (420, 150)], 2.4)


# ---------------------------------------------------------------- nodo piano

def _fascio(s):
    for k in range(5):
        s.legno((90, 196 + k * 8), (350, 196 + k * 8), 9, CHIARO)


def _piano(secondo=1.0):
    """i due capi (S bianco da sinistra, D grigio da destra) intrecciati: prima D sopra S, poi S sopra D"""
    r = 18
    L = [(120, 236, 0), (160, 214, 0), (202, 196, 0)]
    R = [(320, 236, 0), (280, 214, 0), (238, 196, 0)]
    for k in range(13):
        t = 180 + 360 * k / 12
        L.append((220 + r * math.cos(math.radians(t)), 190 - 50 * k / 12, r * math.sin(math.radians(t))))
        R.append((220 + r * math.cos(math.radians(t - 180)), 190 - 50 * k / 12, r * math.sin(math.radians(t - 180))))
    L.append((200, 128, 0)); R.append((240, 128, 0))
    for k in range(13):
        t = 540 - 360 * k / 12
        L.append((220 + r * math.cos(math.radians(t)), 122 - 50 * k / 12, r * math.sin(math.radians(t))))
        R.append((220 + r * math.cos(math.radians(t - 180)), 122 - 50 * k / 12, r * math.sin(math.radians(t - 180))))
    L += [(196, 62, 0), (150, 30, 0), (110, 14, 0)]
    R += [(244, 62, 0), (290, 30, 0), (330, 14, 0)]
    return L, R


def _disegna_piano(s, fino=1.0, freccia=None):
    for k in range(5):
        s.legno((90, 236 + k * 3), (350, 236 + k * 3), 8, CHIARO)
    L, R = _piano()
    s.corde3d([L, R], fino=[fino, fino], capo=[2, 0.0], larg=14, dopo=freccia)
    s.testo(84, 200, 'S', 22)
    s.testo(356, 200, 'D', 22)


@passo('piano_1')
def _(s): _disegna_piano(s, fr(_piano()[0], 17), [(300, 110), (250, 126)])


@passo('piano_2')
def _(s): _disegna_piano(s)


@passo('piano_3')
def _(s):
    _disegna_piano(s)
    s.segno_ok(380, 60, 1.4)


@passo('piano_4')
def _(s):
    _disegna_piano(s)
    s.freccia([(110, 14), (60, 60)], 2.4)


# ---------------------------------------------------------------- nodo di bandiera

def _bandiera():
    A = [(20, 100, 0), (150, 100, 0), (250, 100, 0), (276, 108, 0), (284, 125, 0), (276, 142, 0), (250, 150, 0), (150, 150, 0), (20, 150, 0)]
    B = [(430, 125, -8), (330, 125, -8), (290, 125, -8), (255, 125, 6), (232, 104, 8), (218, 84, 4), (200, 82, -6), (190, 110, -10),
         (190, 150, -10), (196, 172, -2), (214, 166, 8), (222, 146, 6), (226, 124, 2), (244, 106, 2), (262, 100, 2)]
    return A, B


def _disegna_bandiera(s, fb, freccia=None, mostra_b=True):
    A, B = _bandiera()
    fili, fino = ([A, B], [1, fb]) if mostra_b else ([A], [1])
    s.corde3d(fili, fino=fino, capo=[2, 0.0][:len(fili)] if mostra_b else [2], larg=14, dopo=freccia)


@passo('bandiera_1')
def _(s):
    A, _B = _bandiera()
    s.corde3d([A], capo=[2], larg=16)


@passo('bandiera_2')
def _(s): _disegna_bandiera(s, fr(_bandiera()[1], 5), [(210, 60), (196, 78)])


@passo('bandiera_3')
def _(s): _disegna_bandiera(s, fr(_bandiera()[1], 11), [(200, 200), (222, 170)])


@passo('bandiera_4')
def _(s): _disegna_bandiera(s, 1)


@passo('bandiera_5')
def _(s):
    _disegna_bandiera(s, 1)
    s.freccia([(60, 125), (10, 125)], 2.4)
    s.freccia([(380, 160), (430, 160)], 2.4)


# ---------------------------------------------------------------- due mezzi colli (intorno al palo, poi sulla parte fissa)

def _mezzicolli():
    c = [(430, 96, 4), (200, 96, 4), (108, 96, 6)]
    c += giro(70, 0, 'v', 90, 450, 30, 96, 134)[1:]
    i0 = len(c)
    c += [(150, 136, 4), (190, 134, 4)]
    c += giro(0, 96, 'o', 90, -270, 22, 206, 232)
    c += [(214, 128, -6), (196, 140, -6), (180, 136, 0)]
    i1 = len(c)
    c += giro(0, 96, 'o', 90, -270, 22, 170, 144)
    c += [(150, 128, -6), (134, 142, -4), (118, 146, 4)]
    return c, (i0, i1)


def _disegna_mezzicolli(s, k=None, freccia=None):
    c, idx = _mezzicolli()
    f = 1.0 if k is None else fr(c, idx[k])
    s.corde3d([c], oggetto=palo_v(s, 70, 40), fino=[f], capo=[0.25], dietro_x=(50, 90), larg=13, dopo=freccia)


@passo('mezzicolli_1')
def _(s): _disegna_mezzicolli(s, 0, [(170, 170), (200, 120)])


@passo('mezzicolli_2')
def _(s): _disegna_mezzicolli(s, 1, [(150, 180), (160, 120)])


@passo('mezzicolli_3')
def _(s): _disegna_mezzicolli(s)


@passo('mezzicolli_4')
def _(s):
    _disegna_mezzicolli(s)
    s.freccia([(380, 60), (430, 60)], 2.4)


# ---------------------------------------------------------------- tendicavo

def _tendicavo():
    c = [(200, -4, 2), (200, 40, 2), (200, 196, 2), (204, 222, 4), (218, 234, -8), (234, 222, 4), (244, 200, 4), (244, 186, 4)]
    i0 = len(c)
    c += giro(200, 0, 'v', 90, -630, 24, 132, 186)
    i1 = len(c)
    c += [(176, 196, 12), (166, 150, 12), (170, 100, 8), (184, 84, 4)]
    c += giro(200, 0, 'v', -90, -450, 24, 76, 96)[1:]
    c += [(216, 112, -6), (226, 130, -6)]
    return c, (i0, i1)


def _disegna_tendicavo(s, k=None, freccia=None):
    s.terra(236, ciuffi=6, seme=70)
    s.picchetto(222, 240)
    c, idx = _tendicavo()
    f = 1.0 if k is None else fr(c, idx[k])
    s.corde3d([c], fino=[f], capo=[0.5], larg=12, dopo=freccia)


@passo('tendicavo_1')
def _(s): _disegna_tendicavo(s, 0, [(280, 150), (244, 160)])


@passo('tendicavo_2')
def _(s): _disegna_tendicavo(s, 1, [(140, 200), (174, 150)])


@passo('tendicavo_3')
def _(s): _disegna_tendicavo(s)


@passo('tendicavo_4')
def _(s):
    _disegna_tendicavo(s)
    s.freccia([(280, 80), (280, 30)], 2.4)
    s.freccia([(120, 30), (120, 4)], 2.4)


# ---------------------------------------------------------------- prusik (schema)

def _prusik(s, giri=3, anello=True):
    s.legno((220, 250), (220, 0), 22, '#fff', testa=False)
    for k in range(giri):
        y = 80 + k * 26
        s.giri((200, y), (240, y), 2, 7, dy=9)
    if anello:
        s.corda([(214, 80 + giri * 26), (190, 190), (220, 236), (250, 190), (226, 80 + giri * 26)], larg=9, capo=0.0, chiusa=True)


@passo('prusik_1')
def _(s):
    s.corda([(220, 40), (330, 60), (380, 130), (320, 200), (220, 210), (120, 200), (70, 130), (110, 60), (220, 40)], larg=10, capo=0.0, chiusa=True)
    s.giri((206, 30), (206, 54), 4, 6, dx=7)


@passo('prusik_2')
def _(s):
    _prusik(s, 1)
    s.freccia([(300, 110), (250, 96)], 2)


@passo('prusik_3')
def _(s): _prusik(s, 3)


@passo('prusik_4')
def _(s):
    _prusik(s, 3)
    s.freccia([(300, 200), (300, 240)], 2.4)
    s.segno_no(320, 230, 0.8)
    s.freccia([(120, 140), (120, 60)], 2.4)
    s.segno_ok(100, 40, 0.8)


# ---------------------------------------------------------------- nodo del camionista

def _camionista():
    c = [(60, 100, 0), (140, 100, 0), (200, 100, 0)]
    i0 = len(c)
    c += [(214, 104, 0), (228, 124, 4), (222, 144, 4), (206, 138, -4), (206, 116, -4), (222, 104, 0)]
    i1 = len(c)
    c += [(300, 100, 0), (386, 98, 0), (404, 96, -8), (414, 112, -8), (404, 130, 4), (330, 150, 4), (250, 160, 4), (224, 154, -6)]
    i2 = len(c)
    c += [(214, 124, 8), (250, 120, 8), (330, 126, 8), (360, 128, 8)]
    return c, (i0, i1, i2)


def _disegna_camionista(s, k=None, freccia=None):
    s.terra(215, ciuffi=6, seme=71)
    s.albero(40, 30)
    s.albero(410, 30)
    s.giri((50, 92), (50, 108), 3, 5, dx=-6)
    c, idx = _camionista()
    f = 1.0 if k is None else fr(c, idx[k])
    s.corde3d([c], fino=[f], capo=[0.75], larg=10, dopo=freccia)


@passo('camionista_1')
def _(s): _disegna_camionista(s, 0, [(220, 100), (300, 100)])


@passo('camionista_2')
def _(s): _disegna_camionista(s, 1, [(240, 100), (360, 100)])


@passo('camionista_3')
def _(s): _disegna_camionista(s, 2)


@passo('camionista_4')
def _(s):
    _disegna_camionista(s)
    s.freccia([(370, 128), (430, 136)], 2.6)


@passo('camionista_5')
def _(s):
    _disegna_camionista(s)
    s.giri((246, 116), (246, 166), 2, 6, dx=10)
    s.segno_ok(300, 60, 1.2)


# ---------------------------------------------------------------- legature (schema a fasce)

@passo('quadra_4')
def _(s):
    s.legno((220, 245), (220, 5), 22, CHIARO, testa=False)
    s.legno((60, 130), (380, 130), 20, CHIARO)
    for k in range(3):
        s.giri((198 - k * 7, 112), (198 - k * 7, 148), 1, 7)
        s.giri((242 + k * 7, 112), (242 + k * 7, 148), 1, 7)
        s.giri((206, 108 - k * 7), (234, 108 - k * 7), 1, 7)
        s.giri((206, 152 + k * 7), (234, 152 + k * 7), 1, 7)
    s.giri((292, 118), (292, 142), 2, 6, dx=8)
    s.freccia([(330, 60), (390, 60)], 2)
    s.freccia([(330, 60), (270, 60)], 2)
    s.segno_ok(110, 60, 1.2)


def _x_pali(s):
    s.legno((90, 235), (350, 15), 18, CHIARO)
    s.legno((90, 15), (350, 235), 18, CHIARO)


def _fasce_diag(s, ang, n=3):
    ca, sa = math.cos(ang), math.sin(ang)
    for k in range(n):
        o = (k - (n - 1) / 2) * 10
        cx, cy = 220 - sa * o, 125 + ca * o
        s.giri((cx - 42 * ca, cy - 42 * sa), (cx + 42 * ca, cy + 42 * sa), 1, 8)


@passo('diagonale_1')
def _(s):
    _x_pali(s)
    _fasce_diag(s, math.pi / 2, 2)
    s.corda([(240, 150), (300, 200), (330, 240)], larg=7, capo=2)


@passo('diagonale_2')
def _(s):
    _x_pali(s)
    _fasce_diag(s, math.pi / 2, 3)


@passo('diagonale_3')
def _(s):
    _x_pali(s)
    _fasce_diag(s, math.pi / 2, 3)
    _fasce_diag(s, 0, 3)


@passo('diagonale_4')
def _(s):
    _x_pali(s)
    _fasce_diag(s, math.pi / 2, 3)
    _fasce_diag(s, 0, 3)
    s.ellisse(220, 125, 14, 14, 'none', 5)
    s.giri((300, 60), (316, 70), 2, 6, dx=6)


def _tre_pali(s, aperti=False):
    for k, rov in enumerate((False, True, False)):
        y = 90 + k * 30
        if rov:
            s.legno((400, y), (40, y), 20, CHIARO)
        else:
            s.legno((40, y), (400, y), 20, CHIARO)


@passo('treppiede_1')
def _(s):
    _tre_pali(s)
    s.freccia([(60, 200), (20, 200)], 2)
    s.testo(220, 210, 'QUELLO IN MEZZO ROVESCIATO', 13, 800)


@passo('treppiede_2')
def _(s):
    _tre_pali(s)
    s.giri((330, 78), (330, 102), 2, 6, dx=8)
    s.corda([(346, 100), (360, 160), (380, 200)], larg=7, capo=2)


@passo('treppiede_3')
def _(s):
    _tre_pali(s)
    for k in range(6):
        s.giri((300 + k * 8, 76), (300 + k * 8, 164), 1, 7)


@passo('treppiede_4')
def _(s):
    _tre_pali(s)
    for k in range(6):
        s.giri((300 + k * 8, 76), (300 + k * 8, 164), 1, 7)
    for y in (105, 135):
        s.giri((290, y - 4), (356, y - 4), 2, 6, dy=6)


def _due_pali(s):
    s.legno((40, 100), (400, 100), 22, CHIARO)
    s.legno((40, 134), (400, 134), 22, CHIARO)


@passo('cesoia_1')
def _(s): _due_pali(s)


@passo('cesoia_2')
def _(s):
    _due_pali(s)
    s.giri((330, 86), (330, 112), 2, 6, dx=8)
    s.corda([(346, 112), (360, 180), (380, 220)], larg=7, capo=2)


@passo('cesoia_3')
def _(s):
    _due_pali(s)
    for k in range(7):
        s.giri((300 + k * 8, 86), (300 + k * 8, 148), 1, 7)


@passo('cesoia_4')
def _(s):
    _due_pali(s)
    for k in range(7):
        s.giri((300 + k * 8, 86), (300 + k * 8, 148), 1, 7)
    s.giri((294, 113), (362, 121), 2, 6, dy=4)
    s.giri((250, 88), (250, 112), 2, 6, dx=8)


@passo('cesoia_5')
def _(s):
    s.terra(215, seme=72)
    for x in (110, 330):
        s.legno((x - 60, 214), (x + 10, 70), 12, CHIARO)
        s.legno((x + 60, 214), (x - 10, 70), 12, CHIARO)
        s.giri((x - 10, 80), (x + 10, 80), 3, 6, dy=6)
    s.legno((60, 78), (380, 78), 12, MEDIO)
