"""Disegna le foto dei piatti e l'icona di Ai Fornelli.

    python3 -I tools/make_art.py

Scrive:
  data/foto_g.txt   una riga per ricetta (nell'ordine di src/ricette.lua): PNG a 1 bit, 400x230, in base64
  data/foto_p.txt   le miniature 88x88, stesso ordine
  icon.png          l'icona 96x96 della griglia Ink App
  art/              le stesse immagini come PNG, per guardarle sul computer, e un foglio con tutte

Le righe di un file hanno tutte la stessa lunghezza (spazi in fondo), così l'app salta alla foto N
con un solo seek, come l'app Tarot di touch-licorice-apps.
"""
import base64, io, math, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from PIL import Image, ImageDraw  # noqa: E402

import arte  # noqa: E402
import piatti  # noqa: E402

GRANDE = (400, 230)
PICCOLA = 88
LATO_MINI = 165          # il riquadro della tela che diventa la miniatura (uno zoom sul cibo)


def ids():
    src = open(os.path.join(APP, 'src', 'ricette.lua'), encoding='utf-8').read()
    return re.findall(r"^\{ id = '(\w+)'", src, re.M)


def png(img):
    b = io.BytesIO()
    img.save(b, 'PNG', optimize=True)
    return b.getvalue()


def riga_file(nome, righe):
    n = max(len(r) for r in righe)
    with open(os.path.join(APP, 'data', nome), 'w', encoding='ascii', newline='\n') as f:
        for r in righe:
            f.write(r.ljust(n) + '\n')
    return n


def icona():
    S = 8
    im = Image.new('L', (96 * S, 96 * S), 255)
    d = ImageDraw.Draw(im)
    def P(x, y): return (x * S, y * S)
    w = int(6 * S)
    # vapore: tre riccioli
    for x0 in (30, 46, 62):
        pts = [P(x0 + 5 * math.sin(t * 2.2), 30 - t * 6) for t in [k / 10 for k in range(0, 31)]]
        d.line(pts, fill=0, width=int(4.2 * S), joint='curve')
    # coperchio con pomello
    d.rounded_rectangle([*P(14, 37), *P(82, 45)], radius=3 * S, fill=0)
    d.rounded_rectangle([*P(42, 32), *P(54, 38)], radius=2 * S, fill=0)
    # pentola
    d.rounded_rectangle([*P(18, 47), *P(78, 84)], radius=8 * S, outline=0, width=w)
    d.rounded_rectangle([*P(6, 52), *P(20, 58)], radius=3 * S, fill=0)
    d.rounded_rectangle([*P(76, 52), *P(90, 58)], radius=3 * S, fill=0)
    # fiamme
    for x0 in (30, 48, 66):
        d.polygon([P(x0 - 5, 94), P(x0, 87), P(x0 + 5, 94)], fill=0)
    # un cuore sulla pentola: fatto in casa
    d.ellipse([*P(39, 58), *P(49, 68)], fill=0)
    d.ellipse([*P(47, 58), *P(57, 68)], fill=0)
    d.polygon([P(39.6, 65), P(56.4, 65), P(48, 76)], fill=0)
    return im.resize((96, 96), Image.LANCZOS).point(lambda v: 255 if v > 140 else 0).convert('1')


def main():
    os.makedirs(os.path.join(APP, 'data'), exist_ok=True)
    out_art = os.path.join(APP, 'art')
    os.makedirs(out_art, exist_ok=True)
    nomi = ids()
    mancano = [n for n in nomi if n not in piatti.PIATTI]
    if mancano:
        raise SystemExit(f'manca il disegno di: {mancano}')
    grandi, piccole, fogli = [], [], []
    for i, n in enumerate(nomi):
        c = arte.Tela(*GRANDE, seme=i + 1)
        x, y, s = piatti.PIATTI[n](c)
        cx, cy = x + s / 2, y + s / 2
        L = LATO_MINI
        bx = int(min(max(cx - L / 2, 0), GRANDE[0] - L))
        by = int(min(max(cy - L / 2, 0), GRANDE[1] - L))
        g = c.finale()
        p = c.finale(PICCOLA, PICCOLA, scatola=(bx, by, L, L))
        g.save(os.path.join(out_art, f'{n}.png'))
        p.save(os.path.join(out_art, f'{n}_mini.png'))
        grandi.append(base64.b64encode(png(g)).decode('ascii'))
        piccole.append(base64.b64encode(png(p)).decode('ascii'))
        fogli.append((n, g, p))
        print(f'{n:13s} grande {len(grandi[-1]):6d} B  mini {len(piccole[-1]):5d} B')
    lg = riga_file('foto_g.txt', grandi)
    lp = riga_file('foto_p.txt', piccole)
    print(f'foto_g.txt {lg + 1} B a riga, foto_p.txt {lp + 1} B a riga')
    icona().save(os.path.join(APP, 'icon.png'))
    # il foglio con tutto, per guardarlo
    col = 2
    rig = math.ceil(len(fogli) / col)
    foglio = Image.new('1', (col * 510, rig * 245), 1)
    for k, (n, g, p) in enumerate(fogli):
        x, y = (k % col) * 510, (k // col) * 245
        foglio.paste(g, (x, y))
        foglio.paste(p, (x + 408, y))
    foglio.save(os.path.join(out_art, 'tutti.png'))


if __name__ == '__main__':
    main()
