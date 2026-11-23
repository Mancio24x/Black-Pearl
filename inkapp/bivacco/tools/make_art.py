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
import grafica  # noqa: E402
import svg_esporta  # noqa: E402
import svg_passi  # noqa: E402


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


def scrivi(nome_file, righe):
    L = max(len(r) for r in righe)
    with open(os.path.join(APP, 'data', nome_file), 'w', encoding='ascii', newline='\n') as f:
        for r in righe:
            f.write(r.ljust(L) + '\n')
    return (L + 1) * len(righe)


def b64(img):
    b = io.BytesIO()
    img.save(b, 'PNG', optimize=True)
    return base64.b64encode(b.getvalue()).decode('ascii')


def tutorial_ids():
    testo = re.sub(r'--[^\n]*', '', open(os.path.join(APP, 'src', 'tecniche.lua'), encoding='utf-8').read())
    out = []
    for m in re.finditer(r"\{ id = '(\w+)'", testo):
        fine = testo.find("\n{ id = '", m.end())
        blocco = testo[m.end():fine if fine > 0 else len(testo)]
        imgs = re.findall(r"img = '(\w+)'", blocco)
        sez = re.search(r"sez = '(\w+)'", blocco)
        out.append((m.group(1), imgs[0] if imgs else None, sez.group(1) if sez else None))
    return out


MAX_FILE = 900000          # il lettore apre file di dati fino a 1 MB


def scrivi_a_pezzi(base, righe):
    """righe (nome, b64) in base1.txt, base2.txt... ognuno sotto MAX_FILE; restituisce {nome: (file, riga)}"""
    for f in os.listdir(os.path.join(APP, 'data')):
        if re.match(base + r'\d*\.txt$', f):
            os.remove(os.path.join(APP, 'data', f))
    dove, gruppo, n_file = {}, [], 1
    def chiudi():
        nonlocal gruppo, n_file
        if gruppo:
            nome_file = f'{base}{n_file}.txt'
            scrivi(nome_file, [r for _n, r in gruppo])
            for k, (n, _r) in enumerate(gruppo, 1):
                dove[n] = (nome_file, k)
            gruppo, n_file = [], n_file + 1
    for n, r in righe:
        L = max([len(x) for _y, x in gruppo] + [len(r)]) + 1
        if gruppo and L * (len(gruppo) + 1) > MAX_FILE:
            chiudi()
        gruppo.append((n, r))
    chiudi()
    return dove


def main():
    nomi = nomi_usati()
    mancano = [n for n in nomi if n not in disegni.DISEGNI and n not in svg_passi.PASSI]
    if mancano:
        raise SystemExit(f'manca il disegno di: {mancano}')
    os.makedirs(os.path.join(APP, 'data'), exist_ok=True)
    os.makedirs(os.path.join(APP, 'art', 'ui'), exist_ok=True)
    os.makedirs(os.path.join(APP, 'art', 'svg_png'), exist_ok=True)
    vettoriali = [n for n in nomi if n in svg_passi.PASSI]
    if vettoriali:
        svg_esporta.esporta(vettoriali)
    # 1. i disegni dei passi e le intestazioni: img1.txt, img2.txt...
    grandi, piccole, imgs = [], [], []
    tele = {}
    for i, n in enumerate(nomi):
        if n in disegni.DISEGNI:
            c = arte.Tela(disegni.W, disegni.H, seme=i + 1)
            disegni.DISEGNI[n](c)
            tele[n] = c
            z = disegni.ZOOM.get(n)
            img = c.finale(disegni.W, disegni.H, scatola=z) if z else c.finale()
            img.save(os.path.join(APP, 'art', n + '.png'))
        else:
            img = svg_esporta.a_un_bit(n)
            img.save(os.path.join(APP, 'art', 'svg_png', n + '.png'))
        grandi.append((n, b64(img)))
        imgs.append(img)
    for n in ('banner_attrezzatura', 'banner_tecniche', 'banner_percorso'):
        img = grafica.GRAFICA[n]()
        img.save(os.path.join(APP, 'art', 'ui', n + '.png'))
        grandi.append((n, b64(img)))
    # 2. la grafica piccola: ui1.txt...
    for n, f in grafica.GRAFICA.items():
        if n.startswith('banner_'):
            continue
        img = f()
        img.save(os.path.join(APP, 'art', 'ui', n + '.png'))
        piccole.append((n, b64(img)))
    for tid, prima, sez in tutorial_ids():
        if prima and prima in tele:
            c = tele[prima]
            z = disegni.ZOOM.get(prima) or (0, 0, disegni.W, disegni.H)
            x, y, w, h = z
            nw = h * 88 / 64
            img = c.finale(88, 64, scatola=(x + (w - nw) / 2, y, nw, h))
        elif prima:
            g = Image.open(os.path.join(svg_esporta.CACHE, prima + '.png')).convert('L')
            nw = g.height * 88 / 64
            x0 = (g.width - nw) / 2
            g = g.crop((int(x0), 0, int(x0 + nw), g.height)).resize((88, 64), Image.LANCZOS)
            img = arte.atkinson(g, 50, 215)
        else:
            img = grafica.miniatura(tid, sez=sez)()
        img.save(os.path.join(APP, 'art', 'ui', 'th_' + tid + '.png'))
        piccole.append(('th_' + tid, b64(img)))
    dove = scrivi_a_pezzi('img', grandi)
    dove.update(scrivi_a_pezzi('ui', piccole))
    with open(os.path.join(APP, 'src', 'immagini.lua'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('-- GENERATO da tools/make_art.py: dove sta ogni immagine (file in data/, numero di riga)\nreturn {\n')
        for n, _r in grandi + piccole:
            fl, k = dove[n]
            f.write(f"  {n} = {{ '{fl}', {k} }},\n")
        f.write('}\n')
    icona().save(os.path.join(APP, 'icon.png'))
    col = 3
    rig = math.ceil(len(imgs) / col)
    foglio = Image.new('1', (col * 450, rig * 260), 1)
    for k, im in enumerate(imgs):
        foglio.paste(im, ((k % col) * 450, (k // col) * 260))
    foglio.save(os.path.join(APP, 'art', 'tutti.png'))
    for f in sorted(os.listdir(os.path.join(APP, 'data'))):
        dim = os.path.getsize(os.path.join(APP, 'data', f))
        print(f'data/{f}: {dim} byte')
        assert dim < 1000000, f'{f} supera 1 MB: il lettore non lo apre'
    print(f'{len(grandi)} immagini grandi ({len(vettoriali)} vettoriali), {len(piccole)} piccole')


if __name__ == '__main__':
    main()
