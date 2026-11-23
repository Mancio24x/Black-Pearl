"""Crea gli storyboard dei tutorial per il canvas Claude Design "Bivacco e-ink redesign".

    python3 -I tools/design_canvas.py CARTELLA_DEL_CANVAS

Scrive CARTELLA/project/tut_<id>.dc.html (una tavola per tutorial: ogni passo con il suo disegno,
il numero e il titolo) e aggiorna CARTELLA/project/canvas.json con una pagina per sezione.
I disegni vettoriali (tools/svg_passi.py) vanno in linea come SVG; quelli a puntinato già esistenti
sono le immagini caricate nel canvas (tools/design_assets.json).
"""
import html, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from svgkit import Scena  # noqa: E402
import svg_passi  # noqa: E402

SEZIONI = [('sopravvivenza', 'bushcraft', 'Bushcraft'), ('nodi', 'nodi', 'Nodi e legature'),
           ('trappole', 'trappole', 'Trappole'), ('tarp', 'tarp', 'Tarp')]
COL, PW, PH, GAP, CAP = 2, 440, 250, 24, 64


def tutorial():
    testo = re.sub(r'--[^\n]*', '', open(os.path.join(APP, 'src', 'tecniche.lua'), encoding='utf-8').read())
    out = []
    for m in re.finditer(r"\n\{ id = '(\w+)', titolo = (\"[^\"]*\"|'[^']*'), sez = '(\w+)'", testo):
        fine = testo.find("\n{ id = '", m.end())
        blocco = testo[m.end():fine if fine > 0 else len(testo)]
        passi = []
        for r in blocco.split('\n'):
            pm = re.match(r'^    \{ ("(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\')', r)
            if pm:
                im = re.search(r"img = '(\w+)'", r)
                passi.append((pm.group(1)[1:-1], im.group(1) if im else None))
        out.append((m.group(1), m.group(2)[1:-1], m.group(3), passi))
    return out


def svg_chiuso(svg):
    """gli elementi SVG che si chiudono da soli diventano con il tag di chiusura (regola del formato)"""
    return re.sub(r'<(\w+)([^<>]*?)/>', r'<\1\2></\1>', svg)


def tavola(tid, titolo, nome_sez, passi, assets):
    righe = (len(passi) + COL - 1) // COL
    w = COL * PW + (COL + 1) * GAP
    h = 104 + righe * (PH + CAP + GAP) + GAP
    pannelli = []
    for k, (t, img) in enumerate(passi, 1):
        if img and img in svg_passi.PASSI:
            s = Scena()
            svg_passi.PASSI[img](s)
            fig = svg_chiuso(s.svg(f'Passo {k}: {t}'))
        elif img and img in assets:
            fig = f'<img src="{assets[img]}" alt="Passo {k}: {html.escape(t)}" style="display: block; width: 440px; height: 250px; image-rendering: pixelated">'
        else:
            fig = '<div style="width: 440px; height: 250px; display: flex; align-items: center; justify-content: center; font-size: 14px">senza disegno</div>'
        pannelli.append(
            f'<figure style="margin: 0; display: flex; flex-direction: column; gap: 8px">'
            f'<div style="width: 440px; height: 250px; border: 2px solid #000; border-radius: 10px; overflow: hidden; box-sizing: content-box">{fig}</div>'
            f'<figcaption style="display: flex; gap: 10px; align-items: baseline">'
            f'<span style="font-family: \'JetBrains Mono\', monospace; font-weight: 700; font-size: 13px">{k:02d}</span>'
            f'<span style="font-size: 16px; line-height: 22px">{html.escape(t)}</span></figcaption></figure>')
    corpo = '\n'.join(pannelli)
    return w, h, f'''<!doctype html>
<html lang="it">
<head>
<meta charset="utf-8">
<title>{html.escape(titolo)}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;900&amp;family=JetBrains+Mono:wght@700&amp;display=swap" rel="stylesheet">
<style>
body{{margin:0}}
a{{color:#000}}a:hover{{color:#000}}
</style>
</helmet>
<div style="width: {w}px; height: {h}px; box-sizing: border-box; padding: {GAP}px; background: #ffffff; color: #000000; font-family: Inter, system-ui, sans-serif; display: flex; flex-direction: column; gap: 20px">
<div style="display: flex; align-items: flex-end; justify-content: space-between; border-bottom: 3px solid #000; padding-bottom: 10px">
<h1 style="margin: 0; font-size: 34px; font-weight: 900; line-height: 1.1">{html.escape(titolo)}</h1>
<span style="font-family: 'JetBrains Mono', monospace; font-weight: 700; font-size: 13px; letter-spacing: 0.06em">{html.escape(nome_sez.upper())} · {len(passi)} PASSI</span>
</div>
<div style="display: grid; grid-template-columns: repeat({COL}, minmax(0, 1fr)); column-gap: {GAP}px; row-gap: {GAP}px">
{corpo}
</div>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
class Component extends DCLogic {{
  renderVals() {{
    return {{}};
  }}
}}
</script>
</body>
</html>
'''


def main(cartella):
    assets = json.load(open(os.path.join(HERE, 'design_assets.json')))
    proj = os.path.join(cartella, 'project')
    pass
    indice = json.load(open(os.path.join(proj, 'canvas.json')))
    indice['pages'] = [{'id': 'linguaggio', 'name': 'Linguaggio e schermate'}] + [{'id': pid, 'name': nome} for _s, pid, nome in SEZIONI]
    for b in indice['boards'].values():
        if not b.get('page'):
            b['page'] = 'linguaggio'
    nomi_sez = {s: (pid, nome) for s, pid, nome in SEZIONI}
    pos = {pid: [0, 0, 0] for _s, pid, _n in SEZIONI}       # x, y, altezza massima della riga
    scritti = []
    for tid, titolo, sez, passi in tutorial():
        pid, nome = nomi_sez[sez]
        w, h, testo = tavola(tid, titolo, nome, passi, assets)
        path = f'tut_{tid}.dc.html'
        open(os.path.join(proj, path), 'w', encoding='utf-8').write(testo)
        x, y, hmax = pos[pid]
        if x > 0 and x + w > 3 * (w + 80):
            x, y, hmax = 0, y + hmax + 120, 0
        indice['boards'][path] = {'x': x, 'y': y, 'w': w, 'h': h, 'title': titolo, 'page': pid}
        if path not in indice['order']:
            indice['order'].append(path)
        pos[pid] = [x + w + 80, y, max(hmax, h)]
        scritti.append(path)
    json.dump(indice, open(os.path.join(proj, 'canvas.json'), 'w'), ensure_ascii=False)
    print(len(scritti), 'tavole')
    return scritti


if __name__ == '__main__':
    main(sys.argv[1])
