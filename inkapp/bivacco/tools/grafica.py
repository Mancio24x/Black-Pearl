"""La grafica di Bivacco: intestazioni, barra delle schede, icone, miniature e numeri dei passi.

Lo schermo dell'X4 Pro mostra solo bianco e nero, e il testo del sistema è sempre nero in tre misure.
Tutto ciò che è più ricco (titoli grandi, scritte bianche su nero, icone, sfumature puntinate) è
quindi un'immagine a 1 bit, preparata qui. Lo stile: taccuino da campo — titoli in Inter Display
Black maiuscolo, etichette in monospaziato, creste di montagna e curve di livello puntinate.

GRAFICA = { 'nome': funzione() -> PIL.Image '1' }
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from arte import Tela, atkinson

TITOLO = '/usr/share/fonts/opentype/inter/InterDisplay-Black.otf'
FORTE = '/usr/share/fonts/opentype/inter/Inter-Bold.otf'
MONO = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf'
SS = 4                                   # sovracampionamento per icone e scritte


def _font(path, px):
    return ImageFont.truetype(path, int(px * SS))


def _tela(w, h, fondo=255):
    im = Image.new('L', (w * SS, h * SS), fondo)
    return im, ImageDraw.Draw(im)


def _netto(im, w, h, soglia=128):
    return im.resize((w, h), Image.LANCZOS).point(lambda v: 255 if v > soglia else 0).convert('1')


def _P(*xy):
    return [v * SS for v in xy]


# ============================================================================ intestazioni

def _creste(w, h, seme, livelli):
    """creste di montagna sovrapposte, ognuna con il suo grigio, in una tela a 1 bit puntinata"""
    c = Tela(w, h, seme=seme)
    r = random.Random(seme)
    for k, (base, ampiezza, tono, ruvido) in enumerate(livelli):
        fasi = [r.uniform(0, 6.28) for _ in range(4)]
        pts = []
        for x in np.linspace(-5, w + 5, 120):
            t = x / w
            y = base - ampiezza * (0.55 * math.sin(2.2 * math.pi * t + fasi[0]) ** 2
                                  + 0.3 * abs(math.sin(5.1 * math.pi * t + fasi[1]))
                                  + ruvido * math.sin(17 * math.pi * t + fasi[2]))
            pts.append((x, y))
        pts += [(w + 5, h + 5), (-5, h + 5)]
        m = c.poligono(pts)
        c.forma(m, tono, rilievo=6, luce=120, grana=10, bordo=0.8, tono_bordo=0)
        if k == len(livelli) - 1:
            # la neve sulle cime più vicine: linee chiare sui pendii
            pass
    return c


def banner(titolo, sotto, seme):
    W, H = 480, 156
    c = _creste(W, H, seme, [(132, 40, 215, 0.04), (146, 30, 160, 0.05), (160, 20, 95, 0.06)])
    # curve di livello nel cielo, leggere
    for k in range(4):
        y0 = 18 + k * 14
        pts = [(x, y0 + 5 * math.sin(x / 37 + k)) for x in np.linspace(392, 480, 24)]
        c.tratto(pts, 0.8, 150)
    g = c.finale(basso=30, alto=230)
    # il titolo e la riga sotto, netti sopra il cielo
    im = g.convert('L').resize((W * SS, H * SS), Image.NEAREST)
    d = ImageDraw.Draw(im)
    d.text(_P(20, 14), titolo, font=_font(TITOLO, 46), fill=0)
    d.text(_P(22, 70), sotto, font=_font(MONO, 13), fill=0)
    return _netto(im, W, H, 100)


# ============================================================================ icone (40 x 40, tratto da 3 px)

def _icona(disegna, lato=40, tratto=3):
    im, d = _tela(lato, lato)
    t = int(tratto * SS)

    def L(*pts, w=None):
        d.line([v * SS for v in pts], fill=0, width=int((w or tratto) * SS), joint='curve')

    def R(x0, y0, x1, y1, r=3, fill=False):
        d.rounded_rectangle(_P(x0, y0, x1, y1), radius=r * SS, outline=0, width=t, fill=0 if fill else None)

    def E(x0, y0, x1, y1, fill=False):
        d.ellipse(_P(x0, y0, x1, y1), outline=0, width=t, fill=0 if fill else None)

    def G(*pts, fill=True):
        d.polygon([v * SS for v in pts], fill=0 if fill else None, outline=0)

    disegna(L, R, E, G)
    return _netto(im, lato, lato)


ICONE = {
    'zaino60': lambda L, R, E, G: (R(9, 6, 31, 36, 7), R(13, 20, 27, 30, 2), L(15, 6, 15, 3, 25, 3, 25, 6)),
    'zaino20': lambda L, R, E, G: (R(11, 12, 29, 35, 7), L(16, 12, 16, 9, 24, 9, 24, 12), L(15, 24, 25, 24)),
    'scarpe': lambda L, R, E, G: (L(6, 10, 6, 30, 34, 30, 34, 25, 24, 21, 18, 12, 18, 10, 6, 10), L(6, 34, 35, 34), L(12, 16, 16, 16), L(12, 21, 18, 21)),
    'sacco': lambda L, R, E, G: (R(10, 4, 30, 36, 10), E(14, 7, 26, 17), L(13, 24, 27, 24), L(13, 29, 27, 29)),
    'tuta': lambda L, R, E, G: (L(11, 5, 29, 5, 31, 36, 23, 36, 20, 15, 17, 36, 9, 36, 11, 5), L(12, 10, 28, 10)),
    'calze': lambda L, R, E, G: (L(14, 4, 24, 4, 24, 24, 32, 30, 28, 36, 14, 30, 14, 4), L(14, 10, 24, 10)),
    'maglietta': lambda L, R, E, G: (L(14, 5, 4, 11, 8, 18, 12, 16, 12, 35, 28, 35, 28, 16, 32, 18, 36, 11, 26, 5), L(14, 5, 17, 9, 23, 9, 26, 5)),
    'cibo': lambda L, R, E, G: (R(10, 12, 30, 35, 3), L(10, 18, 30, 18), L(13, 12, 15, 6, 25, 6, 27, 12), L(16, 25, 24, 25)),
    'fornello': lambda L, R, E, G: (R(12, 22, 28, 36, 3), L(8, 22, 32, 22), L(20, 18, 20, 22), L(16, 15, 18, 9, 20, 13, 22, 7, 24, 15)),
    'acqua': lambda L, R, E, G: (R(13, 10, 27, 36, 5), R(16, 4, 24, 10, 1), L(13, 22, 27, 22)),
    'tenda': lambda L, R, E, G: (L(4, 34, 20, 7, 36, 34, 4, 34), L(20, 7, 20, 34), L(15, 34, 20, 24, 25, 34)),
    'materassino': lambda L, R, E, G: (E(6, 8, 22, 32), L(14, 8, 34, 8), L(14, 32, 34, 32), L(34, 8, 34, 32), L(14, 20, 14, 20.5)),
    'antipioggia': lambda L, R, E, G: (L(13, 6, 7, 12, 9, 36, 31, 36, 33, 12, 27, 6), L(13, 6, 20, 12, 27, 6), L(20, 12, 20, 36), L(5, 4, 4, 7), L(35, 4, 34, 7)),
    'pile': lambda L, R, E, G: (L(13, 5, 5, 12, 8, 19, 11, 17, 11, 36, 29, 36, 29, 17, 32, 19, 35, 12, 27, 5, 13, 5), L(15, 13, 25, 13), L(15, 20, 25, 20), L(15, 27, 25, 27)),
    'piumino': lambda L, R, E, G: (L(13, 5, 5, 12, 8, 19, 11, 17, 11, 36, 29, 36, 29, 17, 32, 19, 35, 12, 27, 5, 13, 5), L(11, 14, 29, 14), L(11, 22, 29, 22), L(11, 30, 29, 30), L(20, 5, 20, 36)),
    'tarp': lambda L, R, E, G: (L(4, 16, 36, 12, 32, 32, 8, 34, 4, 16), L(4, 16, 2, 6), L(36, 12, 38, 4), L(8, 34, 6, 38), L(32, 32, 34, 38)),
    'filtro': lambda L, R, E, G: (R(14, 8, 26, 30, 3), L(20, 30, 20, 36), L(14, 14, 26, 14), L(17, 4, 23, 4, 23, 8, 17, 8, 17, 4), L(17, 20, 23, 20), L(17, 25, 23, 25)),
    'frontale': lambda L, R, E, G: (E(4, 12, 36, 30), R(14, 14, 26, 24, 3, fill=True), L(28, 8, 34, 4), L(30, 14, 37, 12)),
    'coltello': lambda L, R, E, G: (L(5, 33, 15, 23), L(15, 23, 35, 5, 30, 18, 20, 28, 15, 23), L(12, 20, 18, 26)),
    'corda': lambda L, R, E, G: (E(7, 7, 33, 33), E(12, 12, 28, 28), L(26, 30, 32, 38)),
    'kit': lambda L, R, E, G: (R(5, 10, 35, 34, 4), L(15, 10, 15, 6, 25, 6, 25, 10), L(20, 15, 20, 29, w=4), L(13, 22, 27, 22, w=4)),
    'telo': lambda L, R, E, G: (L(6, 8, 34, 8, 30, 34, 10, 34, 6, 8), L(12, 14, 18, 30), L(20, 12, 24, 30)),
    'fuoco': lambda L, R, E, G: (R(14, 18, 26, 36, 3), L(17, 18, 17, 13, 23, 13, 23, 18), L(20, 10, 18, 6, 20, 2, 22, 6, 20, 10)),
    'mappa': lambda L, R, E, G: (L(4, 9, 14, 5, 26, 9, 36, 5, 36, 31, 26, 35, 14, 31, 4, 35, 4, 9), L(14, 5, 14, 31), L(26, 9, 26, 35)),
    'fischietto': lambda L, R, E, G: (E(5, 15, 25, 35), L(15, 15, 35, 15, 35, 22, 24, 22), E(12, 22, 18, 28, fill=True)),
}


def icona(nome):
    return lambda: _icona(ICONE[nome])


# ============================================================================ pittogrammi dei tutorial (88 x 64)

def _pitto(disegna):
    im, d = _tela(88, 64)

    def L(*pts, w=3):
        d.line([v * SS for v in pts], fill=0, width=int(w * SS), joint='curve')

    def E(x0, y0, x1, y1, fill=False, w=3):
        d.ellipse(_P(x0, y0, x1, y1), outline=0, width=int(w * SS), fill=0 if fill else None)

    def G(*pts):
        d.polygon([v * SS for v in pts], fill=0)

    def T(x, y, s, px=30):
        d.text(_P(x, y), s, font=_font(TITOLO, px), fill=0, anchor='mm')

    disegna(L, E, G, T)
    return _netto(im, 88, 64)


PITTOGRAMMI = {
    'stop': lambda L, E, G, T: (G(30, 6, 58, 6, 74, 22, 74, 46, 58, 60, 30, 60, 14, 46, 14, 22),
                                T(44, 33, 'STOP', 20) if False else None),
    'coltello': lambda L, E, G, T: (L(10, 50, 32, 34, w=7), L(32, 34, 78, 10, 66, 34, 40, 46, 32, 34, w=3), L(26, 30, 38, 42, w=3)),
    'capanna': lambda L, E, G, T: (L(6, 56, 44, 10, 82, 56), L(44, 10, 44, 56), L(14, 56, 30, 30), L(74, 56, 58, 30), L(2, 58, 86, 58), L(36, 56, 44, 40, 52, 56)),
    'acqua': lambda L, E, G, T: (L(44, 4, 30, 30, 27, 40, 31, 50, 44, 58, 57, 50, 61, 40, 58, 30, 44, 4, w=4), L(36, 38, 36, 46, 42, 51)),
    'piano': lambda L, E, G, T: (L(4, 30, 30, 30, 40, 22, 48, 30, 40, 38, 30, 30, w=5), L(84, 34, 58, 34, 48, 42, 40, 34, 48, 26, 58, 34, w=5)),
    'parlato': lambda L, E, G, T: (L(44, 2, 44, 62, w=12), L(4, 20, 36, 26, w=4), L(36, 26, 52, 22), L(36, 42, 52, 38), L(52, 38, 84, 44, w=4)),
    'tendicavo': lambda L, E, G, T: (L(6, 10, 50, 26, w=4), L(50, 26, 70, 56, w=3), L(70, 56, 76, 62, w=6), L(34, 14, 38, 26), L(40, 16, 44, 28), L(48, 20, 52, 32)),
    'prusik': lambda L, E, G, T: (L(44, 2, 44, 62, w=6), L(34, 20, 54, 24), L(34, 28, 54, 32), L(34, 36, 54, 40), L(36, 40, 30, 58, 58, 58, 52, 40)),
    'regole': lambda L, E, G, T: (L(44, 6, 8, 58, 80, 58, 44, 6, w=4), L(44, 24, 44, 42, w=5), E(41, 47, 47, 53, fill=True)),
    'laccio': lambda L, E, G, T: (E(24, 12, 64, 46), L(64, 30, 84, 30), L(84, 30, 84, 62, w=5), L(2, 60, 86, 60)),
    'pesca': lambda L, E, G, T: (L(44, 4, 44, 40), L(44, 40, 40, 52, 30, 54, 24, 46, 26, 40), L(26, 40, 30, 46), L(60, 30, 84, 30, 76, 24), L(84, 30, 76, 36)),
}


def _pitto_stop(L, E, G, T):
    G(30, 6, 58, 6, 74, 22, 74, 46, 58, 60, 30, 60, 14, 46, 14, 22)


SEZ_PITTO = {
    'sopravvivenza': lambda L, E, G, T: (L(44, 60, 22, 36, 30, 18, 38, 30, 44, 6, 58, 30, 54, 48, 44, 60, w=4),),
    'nodi': lambda L, E, G, T: (E(16, 10, 50, 44, w=5), E(38, 22, 72, 56, w=5), L(4, 60, 18, 46, w=5)),
    'trappole': lambda L, E, G, T: (L(44, 60, 44, 8, w=5), L(14, 44, 76, 44, w=5), L(20, 54, 60, 14, w=5)),
    'tarp': lambda L, E, G, T: (L(6, 58, 44, 8, 82, 58, w=5), L(44, 8, 44, 58, w=3), L(2, 60, 86, 60, w=4)),
}


def miniatura(tut_id, img=None, sez=None):
    """la miniatura 88x64 di un tutorial: dal suo disegno se ce l'ha, sennò un pittogramma"""
    def f():
        if img is not None:
            return img
        if tut_id == 'stop':
            im = _pitto(_pitto_stop)
            im2 = im.convert('L').resize((88 * SS, 64 * SS), Image.NEAREST)
            d = ImageDraw.Draw(im2)
            d.text(_P(44, 33), 'STOP', font=_font(TITOLO, 18), fill=255, anchor='mm')
            return _netto(im2, 88, 64)
        if tut_id in PITTOGRAMMI:
            return _pitto(PITTOGRAMMI[tut_id])
        return _pitto(SEZ_PITTO[sez or 'sopravvivenza'])
    return f


# ============================================================================ barra delle schede e sezioni

def _ico_zaino(d, x, y, colore):
    d.rounded_rectangle(_P(x + 5, y + 2, x + 21, y + 26), radius=5 * SS, outline=colore, width=int(2.6 * SS))
    d.line(_P(x + 8, y + 16, x + 18, y + 16), fill=colore, width=int(2.6 * SS))


def _ico_fuoco(d, x, y, colore):
    d.line(_P(x + 13, y + 26, x + 5, y + 16, x + 9, y + 8, x + 13, y + 14, x + 15, y + 2, x + 22, y + 14, x + 20, y + 22, x + 13, y + 26), fill=colore, width=int(2.6 * SS), joint='curve')


def _ico_sentiero(d, x, y, colore):
    d.line(_P(x + 2, y + 26, x + 10, y + 18, x + 6, y + 12, x + 16, y + 6, x + 24, y + 2), fill=colore, width=int(2.6 * SS), joint='curve')
    d.ellipse(_P(x + 19, y, x + 26, y + 7), fill=colore)


def _ico_nodo(d, x, y, colore):
    d.ellipse(_P(x + 3, y + 3, x + 15, y + 15), outline=colore, width=int(2.4 * SS))
    d.ellipse(_P(x + 11, y + 11, x + 23, y + 23), outline=colore, width=int(2.4 * SS))
    d.line(_P(x + 1, y + 25, x + 6, y + 20), fill=colore, width=int(2.4 * SS))


def _ico_trappola(d, x, y, colore):
    d.line(_P(x + 12, y + 26, x + 12, y + 4), fill=colore, width=int(2.6 * SS))
    d.line(_P(x + 2, y + 18, x + 24, y + 18), fill=colore, width=int(2.6 * SS))
    d.line(_P(x + 4, y + 22, x + 16, y + 6), fill=colore, width=int(2.6 * SS))


def tabbar(attiva):
    W, H = 480, 64
    im, d = _tela(W, H)
    d.line(_P(0, 1, W, 1), fill=0, width=int(2 * SS))
    voci = [('attrezzatura', 'Attrezzatura', _ico_zaino), ('tecniche', 'Tecniche', _ico_fuoco), ('percorso', 'Percorso', _ico_sentiero)]
    cw = W / 3
    f = _font(FORTE, 15)
    for k, (id_, nome, ico) in enumerate(voci):
        x0 = k * cw
        on = id_ == attiva
        colore = 255 if on else 0
        if on:
            d.rounded_rectangle(_P(x0 + 8, 8, x0 + cw - 8, H - 6), radius=24 * SS, fill=0)
        tw = d.textlength(nome, font=f) / SS
        tot = 26 + 8 + tw
        sx = x0 + (cw - tot) / 2
        ico(d, sx, 19, colore)
        d.text(_P(sx + 34, 33), nome, font=f, fill=colore, anchor='lm')
    return _netto(im, W, H)


def _ico_tarp(d, x, y, colore):
    d.line(_P(x + 1, y + 24, x + 13, y + 4, x + 25, y + 24), fill=colore, width=int(2.6 * SS), joint='curve')
    d.line(_P(x + 13, y + 4, x + 13, y + 24), fill=colore, width=int(1.6 * SS))
    d.line(_P(x, y + 26, x + 26, y + 26), fill=colore, width=int(2 * SS))


SEZ_W = [124, 94, 118, 104]


def sezioni(attiva):
    W, H = 440, 50
    im, d = _tela(W, H)
    voci = [('sopravvivenza', 'Bushcraft', _ico_fuoco), ('nodi', 'Nodi', _ico_nodo), ('trappole', 'Trappole', _ico_trappola),
            ('tarp', 'Tarp', _ico_tarp)]
    d.rounded_rectangle(_P(1, 1, W - 1, H - 1), radius=24 * SS, outline=0, width=int(2 * SS))
    larghezze = SEZ_W
    f = _font(FORTE, 14)
    x0 = 0
    for k, (id_, nome, ico) in enumerate(voci):
        cw = larghezze[k]
        on = id_ == attiva
        colore = 255 if on else 0
        if on:
            d.rounded_rectangle(_P(x0 + 4, 4, x0 + cw - 4, H - 4), radius=21 * SS, fill=0)
        tw = d.textlength(nome, font=f) / SS
        tot = 26 + 6 + tw
        sx = x0 + (cw - tot) / 2
        ico(d, sx, 12, colore)
        d.text(_P(sx + 32, 25), nome, font=f, fill=colore, anchor='lm')
        x0 += cw
    return _netto(im, W, H)


# ============================================================================ numeri dei passi

def numero(n):
    def f():
        W, H = 104, 64
        im, d = _tela(W, H)
        d.text(_P(0, 2), 'PASSO', font=_font(MONO, 12), fill=0)
        d.text(_P(-2, 64), f'{n:02d}', font=_font(TITOLO, 50), fill=0, anchor='ls')
        return _netto(im, W, H)
    return f


# ============================================================================ bottoni pieni (testo bianco)

def bottone(testo, w, freccia=False, spunta=False, h=64):
    def f():
        im, d = _tela(w, h)
        d.rounded_rectangle(_P(0, 0, w - 1, h - 1), radius=(h // 2) * SS, fill=0)
        fnt = _font(FORTE, 19)
        tw = d.textlength(testo, font=fnt) / SS
        extra = 26 if (freccia or spunta) else 0
        x = (w - tw - extra) / 2
        if spunta:
            d.line(_P(x, h / 2, x + 7, h / 2 + 7, x + 19, h / 2 - 6), fill=255, width=int(3.2 * SS), joint='curve')
            x += extra
        d.text(_P(x, h / 2), testo, font=fnt, fill=255, anchor='lm')
        if freccia:
            fx = x + tw + 14
            d.line(_P(fx, h / 2 - 8, fx + 8, h / 2, fx, h / 2 + 8), fill=255, width=int(3 * SS), joint='curve')
        return _netto(im, w, h)
    return f


# ============================================================================ elenco

GRAFICA = {
    'banner_attrezzatura': lambda: banner('ATTREZZATURA', 'DAL FONDO ALLA CIMA DELLO ZAINO', 3),
    'banner_tecniche': lambda: banner('TECNICHE', 'BUSHCRAFT · NODI · TRAPPOLE · TARP', 7),
    'banner_percorso': lambda: banner('PERCORSO', 'PRIMA DI PARTIRE · LA MAPPA', 11),
    'tab_attrezzatura': lambda: tabbar('attrezzatura'),
    'tab_tecniche': lambda: tabbar('tecniche'),
    'tab_percorso': lambda: tabbar('percorso'),
    'sez_sopravvivenza': lambda: sezioni('sopravvivenza'),
    'sez_nodi': lambda: sezioni('nodi'),
    'sez_trappole': lambda: sezioni('trappole'),
    'sez_tarp': lambda: sezioni('tarp'),
    'btn_avanti': bottone('Avanti', 278, freccia=True),
    'btn_fine': bottone('Fine', 278, spunta=True),
    'btn_inizia': bottone('Inizia', 440, freccia=True),
    'btn_zaino': bottone('Metti nello zaino', 440, spunta=True),
}
for _id in ICONE:
    GRAFICA['ico_' + _id] = icona(_id)
for _n in range(1, 16):
    GRAFICA[f'num_{_n}'] = numero(_n)
