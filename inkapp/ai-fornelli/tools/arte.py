"""Strumenti per disegnare i piatti in bianco e nero per lo schermo e-ink dell'X4 Pro.

Si disegna in toni di grigio, in coordinate di progetto (la misura finale), a una risoluzione
S volte più grande; poi si riduce alla misura finale, si regolano i livelli e si fa il dithering di
Atkinson (lascia puliti i chiari, come un'incisione a puntini). Il risultato è un'immagine a 1 bit:
lo schermo del lettore mostra solo bianco e nero.

    c = Tela(400, 230)
    c.ombra(200, 150, 160, 50)
    c.piatto(200, 120, 170, 95)
    c.forma(c.ellisse(200, 120, 60, 30), tono=90, rilievo=6, grana=14)
    img = c.finale()            # PIL '1'
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 3                                 # sovracampionamento


class Tela:
    def __init__(self, w, h, seme=1):
        self.w, self.h = w, h
        self.W, self.H = w * S, h * S
        self.a = np.full((self.H, self.W), 255.0, dtype=np.float32)
        self.rng = random.Random(seme)
        self.np_rng = np.random.default_rng(seme)

    # ------------------------------------------------------------ maschere (coordinate di progetto)

    def _vuota(self):
        return Image.new('L', (self.W, self.H), 0)

    @staticmethod
    def _np(im):
        return np.asarray(im, dtype=np.float32) / 255.0

    def ellisse(self, cx, cy, rx, ry):
        im = self._vuota()
        ImageDraw.Draw(im).ellipse([(cx - rx) * S, (cy - ry) * S, (cx + rx) * S, (cy + ry) * S], fill=255)
        return self._np(im)

    def poligono(self, pts):
        im = self._vuota()
        ImageDraw.Draw(im).polygon([(x * S, y * S) for x, y in pts], fill=255)
        return self._np(im)

    def rett(self, x, y, w, h, r=0):
        im = self._vuota()
        ImageDraw.Draw(im).rounded_rectangle([x * S, y * S, (x + w) * S, (y + h) * S], radius=r * S, fill=255)
        return self._np(im)

    def tratto_maschera(self, pts, larg):
        im = self._vuota()
        d = ImageDraw.Draw(im)
        p = [(x * S, y * S) for x, y in pts]
        d.line(p, fill=255, width=max(1, int(larg * S)), joint='curve')
        r = larg * S / 2
        for x, y in (p[0], p[-1]):
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        return self._np(im)

    def blob(self, cx, cy, rx, ry, irregolare=0.18, punti=18, rot=0.0):
        """una forma morbida e irregolare (mozzarella, polpetta, patata...)"""
        pts = []
        fasi = [self.rng.uniform(0, 6.28) for _ in range(3)]
        for k in range(punti * 4):
            t = 2 * math.pi * k / (punti * 4)
            r = 1 + irregolare * (0.6 * math.sin(3 * t + fasi[0]) + 0.3 * math.sin(5 * t + fasi[1]) + 0.2 * math.sin(7 * t + fasi[2]))
            x, y = rx * r * math.cos(t), ry * r * math.sin(t)
            xr = x * math.cos(rot) - y * math.sin(rot)
            yr = x * math.sin(rot) + y * math.cos(rot)
            pts.append((cx + xr, cy + yr))
        return self.poligono(pts)

    def foglia(self, cx, cy, lung, larg, ang):
        pts = []
        for k in range(41):
            t = k / 40
            w = larg * math.sin(math.pi * t) ** 0.8 / 2
            pts.append((t * lung, w))
        for k in range(40, -1, -1):
            t = k / 40
            w = larg * math.sin(math.pi * t) ** 0.8 / 2
            pts.append((t * lung, -w))
        c, s = math.cos(ang), math.sin(ang)
        return self.poligono([(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]), \
            [(cx, cy), (cx + lung * 0.9 * c, cy + lung * 0.9 * s)]

    # ------------------------------------------------------------ pittura

    def _sfoca(self, m, sigma):
        im = Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8))
        return self._np(im.filter(ImageFilter.GaussianBlur(sigma * S)))

    def _grana(self, forza, grana=1.0):
        h, w = max(1, int(self.h / grana)), max(1, int(self.w / grana))
        n = self.np_rng.normal(0, forza, (h, w)).astype(np.float32)
        return np.asarray(Image.fromarray(n, mode='F').resize((self.W, self.H), Image.BILINEAR))

    def forma(self, m, tono, rilievo=5.0, luce=110.0, grana=10.0, bordo=1.4, tono_bordo=20, macchie=None,
              taglio=None, scuro_bordi=40.0, chiaro=None):
        """dipinge la maschera m con un tono di grigio, un rilievo illuminato da in alto a sinistra,
        una grana, un contorno e, se richieste, macchie scure ((quante, raggio, tono))"""
        if taglio is not None:
            m = m * taglio
        sel = m > 0.5
        if not sel.any():
            return m
        t = np.full_like(self.a, float(tono))
        if rilievo:
            b = self._sfoca(m, rilievo)
            gy, gx = np.gradient(b)
            lum = (gx * 0.7 + gy * 0.7) * (rilievo * S)        # il pendio verso la luce (alto a sinistra)
            t += lum * luce
            t -= (1 - b) * scuro_bordi                          # i bordi girano via dalla luce
        if chiaro:
            cx, cy, r, quanto = chiaro                          # un riflesso
            yy, xx = np.mgrid[0:self.H, 0:self.W]
            d = np.sqrt((xx - cx * S) ** 2 + (yy - cy * S) ** 2) / (r * S)
            t += np.clip(1 - d, 0, 1) ** 2 * quanto
        if grana:
            t += self._grana(grana)
        if macchie:
            quante, raggio, tono_m = macchie
            im = Image.new('L', (self.W, self.H), 0)
            d = ImageDraw.Draw(im)
            ys, xs = np.nonzero(sel[::S, ::S])
            for _ in range(quante):
                if len(xs) == 0:
                    break
                i = self.rng.randrange(len(xs))
                x, y = xs[i] * S, ys[i] * S
                r = raggio * S * self.rng.uniform(0.5, 1.3)
                d.ellipse([x - r, y - r * 0.8, x + r, y + r * 0.8], fill=255)
            mm = self._np(im)
            t = t * (1 - mm) + mm * tono_m
        self.a[sel] = t[sel]
        if bordo:
            self.contorno(m, bordo, tono_bordo)
        return m

    def contorno(self, m, larg=1.4, tono=20):
        im = Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8))
        k = max(3, int(2 * larg * S) | 1)
        er = self._np(im.filter(ImageFilter.MinFilter(k)))
        bordo = (m > 0.5) & (er < 0.5)
        self.a[bordo] = tono

    def tratto(self, pts, larg=1.2, tono=20):
        im = Image.fromarray(self.a)
        ImageDraw.Draw(im).line([(x * S, y * S) for x, y in pts], fill=float(tono), width=max(1, int(larg * S)), joint='curve')
        self.a = np.asarray(im, dtype=np.float32).copy()

    def punti(self, m, quanti, raggio, tono):
        """puntini sparsi dentro m (pepe, semi, briciole)"""
        im = Image.fromarray(self.a)
        d = ImageDraw.Draw(im)
        ys, xs = np.nonzero(m[::S, ::S] > 0.5)
        for _ in range(quanti):
            if len(xs) == 0:
                break
            i = self.rng.randrange(len(xs))
            x, y = (xs[i] + self.rng.random()) * S, (ys[i] + self.rng.random()) * S
            r = raggio * S * self.rng.uniform(0.6, 1.2)
            d.ellipse([x - r, y - r, x + r, y + r], fill=float(tono))
        self.a = np.asarray(im, dtype=np.float32).copy()

    def tratteggio(self, m, passo=4, ang=0.7, tono=60, larg=0.8):
        """linee parallele dentro m: ombre da incisione"""
        im = Image.new('L', (self.W, self.H), 0)
        d = ImageDraw.Draw(im)
        c, s = math.cos(ang), math.sin(ang)
        L = self.W + self.H
        for k in range(-L, L, int(passo * S)):
            x0, y0 = k * -s, k * c
            d.line([(x0 - L * c, y0 - L * s), (x0 + L * c, y0 + L * s)], fill=255, width=max(1, int(larg * S)))
        lin = self._np(im) * (m > 0.5)
        self.a = np.where(lin > 0.5, np.minimum(self.a, tono), self.a)

    # ------------------------------------------------------------ oggetti comuni

    def ombra(self, cx, cy, rx, ry, tono=200, morbida=6):
        m = self._sfoca(self.ellisse(cx, cy, rx, ry), morbida)
        self.a = np.minimum(self.a, 255 - (255 - tono) * m)

    def piatto(self, cx, cy, rx, ry, tesa=0.22, tono=252):
        """un piatto visto di tre quarti: la tesa e il fondo"""
        self.ombra(cx + 6, cy + ry * 0.18, rx * 0.98, ry * 0.9)
        fuori = self.ellisse(cx, cy, rx, ry)
        self.forma(fuori, tono, rilievo=6, luce=25, grana=3, bordo=1.5, tono_bordo=0, scuro_bordi=25)
        dentro = self.ellisse(cx, cy + ry * 0.04, rx * (1 - tesa), ry * (1 - tesa))
        self.forma(dentro, tono - 4, rilievo=4, luce=-30, grana=3, bordo=0, scuro_bordi=0)
        self.contorno(dentro, 0.8, 70)
        return dentro

    def ciotola(self, cx, cy, rx, ry, alt, tono=248):
        """una scodella vista di tre quarti: il corpo, l'orlo e la superficie dentro"""
        self.ombra(cx + 8, cy + alt + ry * 0.4, rx * 0.9, ry * 0.7)
        corpo = np.maximum(self.ellisse(cx, cy, rx, ry), self._metaellisse_sotto(cx, cy, rx, alt))
        self.forma(corpo, tono, rilievo=10, luce=30, grana=4, bordo=1.4, scuro_bordi=70)
        self.tratteggio(self._metaellisse_sotto(cx + rx * 0.45, cy, rx * 0.55, alt) * corpo, passo=4, ang=1.2, tono=150, larg=0.6)
        orlo = self.ellisse(cx, cy, rx, ry)
        self.forma(orlo, tono, rilievo=3, luce=20, grana=2, bordo=1.4, scuro_bordi=10)
        dentro = self.ellisse(cx, cy + 2, rx * 0.92, ry * 0.86)
        return dentro

    def _metaellisse_sotto(self, cx, cy, rx, alt):
        pts = [(cx + rx * math.cos(t), cy + alt * math.sin(t)) for t in np.linspace(0, math.pi, 60)]
        return self.poligono(pts)

    def basilico(self, cx, cy, n=3, lung=28, seme=0):
        for k in range(n):
            a = -2.4 + k * 1.7 / max(1, n - 1) + self.rng.uniform(-0.2, 0.2)
            m, ner = self.foglia(cx, cy, lung * self.rng.uniform(0.85, 1.1), lung * 0.55, a)
            self.forma(m, 70, rilievo=4, luce=90, grana=12, bordo=1.2)
            self.tratto(ner, 0.8, 200)

    def rosmarino(self, x0, y0, x1, y1, aghi=14):
        self.tratto([(x0, y0), (x1, y1)], 1.6, 40)
        for k in range(aghi):
            t = (k + 0.5) / aghi
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            ang = math.atan2(y1 - y0, x1 - x0)
            for lato in (-1, 1):
                a = ang + lato * 0.9
                self.tratto([(x, y), (x + 9 * math.cos(a), y + 9 * math.sin(a))], 1.6, 50)

    # ------------------------------------------------------------ uscita

    def grigio(self, larg=None, alt=None, scatola=None):
        im = Image.fromarray(np.clip(self.a, 0, 255).astype(np.uint8))
        if scatola:
            x, y, w, h = scatola
            im = im.crop((x * S, y * S, (x + w) * S, (y + h) * S))
        return im.resize((larg or self.w, alt or self.h), Image.LANCZOS)

    def finale(self, larg=None, alt=None, scatola=None, basso=40, alto=222, nitido=True):
        g = self.grigio(larg, alt, scatola)
        if nitido:
            g = g.filter(ImageFilter.UnsharpMask(radius=1.2, percent=80, threshold=2))
        return atkinson(g, basso, alto)


def atkinson(g, basso=40, alto=222):
    a = np.asarray(g, dtype=np.float32)
    a = np.clip((a - basso) * 255.0 / (alto - basso), 0, 255)
    h, w = a.shape
    for y in range(h):
        riga = a[y]
        for x in range(w):
            vecchio = riga[x]
            nuovo = 255.0 if vecchio >= 128 else 0.0
            riga[x] = nuovo
            e = (vecchio - nuovo) / 8.0
            if e == 0:
                continue
            if x + 1 < w: riga[x + 1] += e
            if x + 2 < w: riga[x + 2] += e
            if y + 1 < h:
                r1 = a[y + 1]
                if x > 0: r1[x - 1] += e
                r1[x] += e
                if x + 1 < w: r1[x + 1] += e
            if y + 2 < h:
                a[y + 2][x] += e
    return Image.fromarray((a >= 128).astype(np.uint8) * 255).convert('1')
