"""Scrive gli SVG dei passi in art/svg/ e li trasforma in PNG a 1 bit (440x250) in art/svg_png/.

    python3 -I tools/svg_esporta.py [nome ...]

Usato da make_art.py; da solo serve a vedere i disegni mentre si lavora.
"""
import os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from PIL import Image  # noqa: E402

from arte import atkinson  # noqa: E402
from svgkit import Scena  # noqa: E402
import svg_passi  # noqa: E402

SVG = os.path.join(APP, 'art', 'svg')
CACHE = os.path.join(APP, 'build', 'svg3x')


def esporta(nomi=None):
    os.makedirs(SVG, exist_ok=True)
    nomi = nomi or sorted(svg_passi.PASSI)
    for n in nomi:
        s = Scena()
        svg_passi.PASSI[n](s)
        testo = s.svg(n)
        path = os.path.join(SVG, n + '.svg')
        vecchio = open(path, encoding='utf-8').read() if os.path.exists(path) else None
        if testo != vecchio:
            open(path, 'w', encoding='utf-8').write(testo)
    subprocess.run(['node', os.path.join(HERE, 'rendi_svg.js'), SVG, CACHE], check=True, stdout=subprocess.DEVNULL)
    return nomi


def a_un_bit(nome):
    im = Image.open(os.path.join(CACHE, nome + '.png')).convert('L')
    im = im.resize((440, 250), Image.LANCZOS)
    return atkinson(im, 50, 215)


if __name__ == '__main__':
    nomi = esporta(sys.argv[1:] or None)
    out = os.path.join(APP, 'art', 'svg_png')
    os.makedirs(out, exist_ok=True)
    for n in nomi:
        a_un_bit(n).save(os.path.join(out, n + '.png'))
    print(len(nomi), 'disegni')
