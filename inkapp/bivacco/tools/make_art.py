"""Disegna le immagini di Bivacco.

    python3 -I tools/make_art.py

Scrive data/img.txt (una riga per immagine: PNG a 1 bit 440x250 in base64, righe della stessa
lunghezza), src/immagini.lua (nome -> numero di riga), icon.png e art/ (i PNG e un foglio con tutto).
Le immagini usate dall'app sono quelle nominate con img = '...' in src/*.lua; un nome senza disegno
in tools/disegni.py ferma la costruzione.
"""
import base64, io, math, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from PIL import Image, ImageDraw  # noqa: E402

import arte  # noqa: E402
import disegni  # noqa: E402


def nomi_usati():
    out = []
    for f in sorted(os.listdir(os.path.join(APP, 'src'))):
        if f.endswith('.lua') and f != 'immagini.lua':
            testo = re.sub(r'--[^\n]*', '', open(os.path.join(APP, 'src', f), encoding='utf-8').read())
            for n in re.findall(r"img = '(\w+)'", testo):
                if n not in out:
                    out.append(n)
    return out


def icona():
    S = 8
    im = Image.new('L', (96 * S, 96 * S), 255)
    d = ImageDraw.Draw(im)
    def P(x, y): return (x * S, y * S)
    # le montagne
    d.polygon([P(2, 86), P(36, 22), P(52, 50), P(64, 34), P(94, 86)], fill=0)
    d.polygon([P(36, 22), P(28, 38), P(34, 35), P(39, 41), P(44, 36)], fill=255)      # la neve
    d.polygon([P(64, 34), P(58, 45), P(64, 42), P(70, 45)], fill=255)
    # la tenda davanti
    d.polygon([P(30, 88), P(50, 58), P(70, 88)], fill=255)
    d.line([P(30, 88), P(50, 58), P(70, 88), P(30, 88)], fill=0, width=5 * S)
    d.polygon([P(44, 88), P(50, 72), P(56, 88)], fill=0)
    return im.resize((96, 96), Image.LANCZOS).point(lambda v: 255 if v > 140 else 0).convert('1')


def main():
    nomi = nomi_usati()
    mancano = [n for n in nomi if n not in disegni.DISEGNI]
    if mancano:
        raise SystemExit(f'manca il disegno di: {mancano}')
    os.makedirs(os.path.join(APP, 'data'), exist_ok=True)
    os.makedirs(os.path.join(APP, 'art'), exist_ok=True)
    righe, imgs = [], []
    for i, n in enumerate(nomi):
        c = arte.Tela(disegni.W, disegni.H, seme=i + 1)
        disegni.DISEGNI[n](c)
        z = disegni.ZOOM.get(n)
        img = c.finale(disegni.W, disegni.H, scatola=z) if z else c.finale()
        img.save(os.path.join(APP, 'art', n + '.png'))
        b = io.BytesIO()
        img.save(b, 'PNG', optimize=True)
        righe.append(base64.b64encode(b.getvalue()).decode('ascii'))
        imgs.append(img)
        print(f'{n:10s} {len(righe[-1]):6d} B')
    L = max(len(r) for r in righe)
    with open(os.path.join(APP, 'data', 'img.txt'), 'w', encoding='ascii', newline='\n') as f:
        for r in righe:
            f.write(r.ljust(L) + '\n')
    with open(os.path.join(APP, 'src', 'immagini.lua'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('-- GENERATO da tools/make_art.py: il numero di riga di ogni immagine in data/img.txt\nreturn {\n')
        for k, n in enumerate(nomi, 1):
            f.write(f"  {n} = {k},\n")
        f.write('}\n')
    icona().save(os.path.join(APP, 'icon.png'))
    col = 3
    rig = math.ceil(len(imgs) / col)
    foglio = Image.new('1', (col * 450, rig * 260), 1)
    for k, im in enumerate(imgs):
        foglio.paste(im, ((k % col) * 450, (k // col) * 260))
    foglio.save(os.path.join(APP, 'art', 'tutti.png'))
    print(len(nomi), 'immagini,', os.path.getsize(os.path.join(APP, 'data', 'img.txt')), 'byte')


if __name__ == '__main__':
    main()
