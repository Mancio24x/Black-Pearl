"""Simulatore di Ai Fornelli: esegue index.lua (Lua 5.4 via lupa) con un finto lettore e disegna lo
schermo 480x800 in bianco e nero, per guardare le schermate sul computer e per i test.

    from anteprima import Lettore
    l = Lettore()                    # carica index.lua, chiama on_load
    l.tocca(x, y); l.tasto('right'); l.scorri('swipe_left'); l.avanti(secondi)
    l.foto('schermata.png')          # salva lo schermo
    l.testi()                        # i testi disegnati

    python3 -I tools/anteprima.py    # salva le schermate principali in art/schermate/

I caratteri del lettore non sono quelli del computer: le misure sono simili (righe da 20, 24 e 30
pixel), non identiche. Il simulatore serve a vedere l'impaginazione, non è il lettore vero.
"""
import base64, os, sys

from lupa import lua54
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
DIM = {'small': (15, 20), 'medium': (18, 24), 'large': (23, 30)}

LUA = r'''
local P = ...
local function M(t)
  local out = {}
  for k, f in pairs(t) do
    out[k] = function(self, ...) assert(self == out, 'serve la chiamata con i due punti: ' .. k); return f(...) end
  end
  return out
end
local function font(n, lh)
  local f = { line_height = lh, ascent = lh - 4, name = n }
  f.measure = function(self, s) return P.misura(n, s) end
  return f
end
gg = setmetatable({}, { __index = function(_, k)
  return function(self, ...) assert(self == gg, 'gg con i due punti: ' .. k); return P.disegna(k, ...) end
end })
ctx = {
  invalidate = function(self, how) P.ridisegna(how) end,
  after = function(self, ms, fn) return P.dopo(ms, fn) end,
  storage = M({ write = function(n, d) P.store[n] = d; return true end,
                read = function(n) local v = P.store[n]; if v then return v end; return nil, 'not_found' end }),
  fonts = { small = font('small', 20), medium = font('medium', 24), large = font('large', 30) },
  clock = M({ now = function() return P.adesso() end, millis = function() return P.millis() end }),
  capabilities = { image_decode = true },
  base64 = M({ decode = function(s) return P.b64(s) end }),
  image = M({ decode = function(b) return P.immagine(b) end }),
  package = M({ open_text = function(name, opts) return P.apri(name) end }),
  device = M({ keep_awake = function(ms) P.sveglio(ms) end }),
}
__TEST = {}
'''


class _Lettore_file:
    def __init__(self, data):
        self.data, self.pos = data, 0


class Lettore:
    def __init__(self, store=None, epoch=1795426200, offset=3600):
        subprocess_build()
        self.L = lua54.LuaRuntime(unpack_returned_tuples=True)
        self.epoch, self.offset = epoch, offset            # 23 nov 2026, 10:30 (ora italiana)
        self.ms = 0
        self.timers = []                                   # (scadenza_ms, fn, vivo)
        self.store = self.L.table_from(store or {})
        self.imgs, self.blob = {}, {}
        self.full = 0
        self.awake = 0
        self.fonts = {n: ImageFont.truetype(FONT, d[0]) for n, d in DIM.items()}
        self.im = None
        self.comandi = 0
        P = self.L.table_from({
            'misura': lambda n, s: int(self.fonts[n].getlength(s)),
            'disegna': self._disegna, 'ridisegna': self._ridisegna, 'dopo': self._dopo,
            'store': self.store, 'adesso': self._adesso, 'millis': lambda: self.ms,
            'b64': self._b64, 'immagine': self._immagine, 'apri': self._apri,
            'sveglio': self._sveglio,
        })
        self.L.execute(LUA, P)
        self.L.execute(open(os.path.join(APP, 'index.lua'), encoding='utf-8').read())
        self.g = self.L.globals()
        self.api = self.g['__TEST'].api
        self.g.on_load(self.g.ctx)
        self.disegna()

    # -------------------------------------------------- finto lettore

    def _adesso(self):
        e = self.epoch + self.ms // 1000
        return self.L.table_from({'epoch_sec': e, 'local_sec': e + self.offset, 'hour': 10, 'minute': 30, 'second': 0})

    def _sveglio(self, ms):
        self.awake = ms

    def _ridisegna(self, how=None):
        if how == 'full':
            self.full += 1

    def _dopo(self, ms, fn):
        t = {'quando': self.ms + int(ms), 'fn': fn, 'vivo': True}
        self.timers.append(t)
        return self.L.table_from({'cancel': lambda self_=None: t.update(vivo=False)})

    def _b64(self, s):
        k = 'B%d' % len(self.blob)
        self.blob[k] = base64.b64decode(s)
        return k

    def _immagine(self, k):
        import io
        im = Image.open(io.BytesIO(self.blob[k])).convert('1')
        i = 'I%d' % len(self.imgs)
        self.imgs[i] = im
        return self.L.table_from({'id': i, 'width': im.width, 'height': im.height, 'release': lambda *_: None})

    def _apri(self, name):
        path = os.path.join(APP, 'data', name)
        if not os.path.exists(path):
            return None
        f = _Lettore_file(open(path, encoding='ascii').read())

        def read_line(_self=None):
            if f.pos >= len(f.data):
                return None
            j = f.data.find('\n', f.pos)
            j = len(f.data) if j < 0 else j
            r = f.data[f.pos:j]
            f.pos = j + 1
            return r

        def seek(_self, pos):
            f.pos = int(pos)

        return self.L.table_from({'read_line': read_line, 'seek': seek, 'close': lambda *_: None})

    def _opt(self, o, k, d=None):
        try:
            v = o[k] if o is not None else None
        except Exception:
            v = None
        return d if v is None else v

    def _disegna(self, k, *a):
        self.comandi += 1
        d = self.draw
        if k == 'clear':
            d.rectangle([0, 0, 480, 800], fill=1)
        elif k == 'text':
            x, y, s = a[0], a[1], a[2]
            o = a[3] if len(a) > 3 else None
            f = self._opt(o, 'font')
            n = f['name'] if f is not None else 'medium'
            lhh = DIM[n][1]
            d.text((x, y + (lhh - DIM[n][0]) // 2 - 1), s, font=self.fonts[n], fill=0)
            self.scritti.append((s, x, y))
        elif k == 'rect':
            x, y, w, h = a[:4]
            o = a[4] if len(a) > 4 else None
            fill = self._opt(o, 'fill', False)
            col = self._opt(o, 'color', 15)
            r = self._opt(o, 'radius', 0)
            lw = self._opt(o, 'line_width', 1)
            if fill:
                d.rounded_rectangle([x, y, x + w - 1, y + h - 1], radius=r, fill=0 if col else 1)
            else:
                d.rounded_rectangle([x, y, x + w - 1, y + h - 1], radius=r, outline=0, width=int(lw))
        elif k == 'line':
            d.line([a[0], a[1], a[2], a[3]], fill=0, width=1)
        elif k == 'circle':
            cx, cy, r = a[:3]
            o = a[3] if len(a) > 3 else None
            if self._opt(o, 'fill', False):
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=0 if self._opt(o, 'color', 15) else 1)
            else:
                d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=0, width=int(self._opt(o, 'line_width', 1)))
        elif k == 'image':
            img, x, y = a[0], a[1], a[2]
            self.im.paste(self.imgs[img['id']], (int(x), int(y)))
        else:
            raise RuntimeError('comando di disegno sconosciuto: ' + k)

    # -------------------------------------------------- uso

    def disegna(self):
        self.im = Image.new('1', (480, 800), 1)
        self.draw = ImageDraw.Draw(self.im)
        self.scritti, self.comandi = [], 0
        self.g.on_draw(self.g.ctx, self.g.gg)
        err = self.api.err()
        if err:
            raise RuntimeError('errore Lua: ' + str(err))
        return self.im

    def testi(self):
        self.disegna()
        return [s for s, _x, _y in self.scritti]

    def dove(self, testo):
        self.disegna()
        for s, x, y in reversed(self.scritti):        # l'ultimo disegnato è quello in cima (un avviso sopra la pagina)
            if s == testo:
                return x, y
        raise KeyError(f'{testo!r} non è sullo schermo: {[s for s, *_ in self.scritti]}')

    def tocca(self, x, y, gesto='tap'):
        ev = self.L.table_from({'type': 'pointer', 'phase': 'up', 'gesture': gesto, 'x': x, 'y': y})
        self.g.on_input(self.g.ctx, ev)
        self.disegna()

    def trova(self, testo, pagine=10):
        for _ in range(pagine):
            if testo in self.testi():
                return
            self.scorri('swipe_left')
        raise KeyError(testo)

    def tocca_testo(self, testo, gesto='tap'):
        x, y = self.dove(testo)
        self.tocca(x + 4, y + 8, gesto)

    def scorri(self, gesto):
        self.tocca(240, 400, gesto)

    def tasto(self, k):
        self.ms += 400
        self.g.on_input(self.g.ctx, self.L.table_from({'type': 'key', 'key': k}))
        self.disegna()

    def avanti(self, secondi):
        """fa passare il tempo, eseguendo i ctx:after che scadono"""
        fine = self.ms + int(secondi * 1000)
        while True:
            vivi = [t for t in self.timers if t['vivo'] and t['quando'] <= fine]
            if not vivi:
                break
            t = min(vivi, key=lambda t: t['quando'])
            t['vivo'] = False
            self.ms = t['quando']
            t['fn']()
        self.ms = fine
        self.disegna()

    def foto(self, path, scala=1):
        im = self.disegna()
        if scala != 1:
            im = im.resize((480 * scala, 800 * scala), Image.NEAREST)
        im.save(path)


def subprocess_build():
    import subprocess
    subprocess.run([sys.executable, '-I', os.path.join(HERE, 'build.py'), '--solo-lua'], check=True,
                   stdout=subprocess.DEVNULL)


def main():
    out = os.path.join(APP, 'art', 'schermate')
    os.makedirs(out, exist_ok=True)
    l = Lettore()
    l.foto(os.path.join(out, '1-oggi.png'))
    l.tocca_testo('Ricette')
    l.foto(os.path.join(out, '2-ricette.png'))
    l.trova('Lasagne alla bolognese')
    l.tocca_testo('Lasagne alla bolognese')
    l.foto(os.path.join(out, '3-ricetta.png'))
    l.tocca_testo('Inizia a cucinare')
    for _ in range(3):
        l.tocca_testo('Avanti')
    l.tocca_testo('Avvia')
    l.avanti(30 * 60)
    l.foto(os.path.join(out, '4-cucina.png'))
    l.tasto('back')
    l.tocca_testo('Consigli')
    l.foto(os.path.join(out, '5-consigli.png'))
    l2 = Lettore(store={'diario.txt': '20765\tpizza\n20770\tlasagne\n20770\ttiramisu\n20776\tcarbonara\n20778\tpolpette\n20780\tciambellone'})
    l2.tocca_testo('Cucinati')
    l2.foto(os.path.join(out, '6-cucinati.png'))
    print('schermate in', out)


if __name__ == '__main__':
    main()
