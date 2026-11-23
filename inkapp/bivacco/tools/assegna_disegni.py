"""Aggiunge img = '<tutorial>_<passo>' ai passi di src/tecniche.lua che non hanno un disegno, quando
tools/svg_passi.py ne ha uno con quel nome. Si può rilanciare: i passi già collegati non cambiano.

    python3 -I tools/assegna_disegni.py
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import svg_passi  # noqa: E402

p = os.path.join(APP, 'src', 'tecniche.lua')
righe = open(p, encoding='utf-8').read().split('\n')
tid, k, aggiunti = None, 0, 0
for i, r in enumerate(righe):
    m = re.match(r"^\{ id = '(\w+)'", r)
    if m:
        tid, k = m.group(1), 0
        continue
    if tid and re.match(r'^    \{ ["\']', r) and r.rstrip().endswith('},'):
        k += 1
        if 'img = ' in r:
            continue
        nome = f'{tid}_{k}'
        if nome in svg_passi.PASSI:
            righe[i] = re.sub(r'\s*\},\s*$', f", img = '{nome}' }},", r)
            aggiunti += 1
open(p, 'w', encoding='utf-8').write('\n'.join(righe))
print('collegati', aggiunti, 'disegni')
