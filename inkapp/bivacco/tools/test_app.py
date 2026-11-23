"""Test di Bivacco sul simulatore (tools/anteprima.py).

    python3 -I tools/test_app.py
"""
import os, sys, traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anteprima import Lettore  # noqa: E402

LIMITE = 900          # il lettore disegna circa 1024 comandi per schermata e salta il resto


def lista(t):
    return [t[k] for k in range(1, len(t) + 1)]


def test_ogni_tutorial_e_ogni_passo():
    l = Lettore()
    for k, t in enumerate(lista(l.api.tecniche), 1):
        l.api.apri(k)
        l.disegna()
        assert l.comandi < LIMITE, (t.id, l.comandi)
        l.tocca_testo('Rivedi i passi' if t.id in l.api.fatti() else 'Inizia')
        n = len(t.passi)
        for p in range(1, n + 1):
            testi = l.testi()
            assert f'{p} / {n}' in testi, (t.id, p, testi)
            assert l.comandi < LIMITE, (t.id, p, l.comandi)
            # l'ultima parola del testo del passo è sullo schermo: il testo entra
            ultima = t.passi[p][2].split(' ')[-1]
            assert any(ultima in s for s in testi), (t.id, p, 'testo tagliato')
            l.tocca_testo('Avanti' if p < n else 'Fine')
        assert l.api.scr() == 'tutorial'
        assert l.api.fatti()[t.id]


def test_immagini_dei_passi():
    l = Lettore()
    for k, t in enumerate(lista(l.api.tecniche), 1):
        for p in lista(t.passi):
            if p.img:
                l.api.apri(k)
                break
    # ogni nome di immagine usato esiste in data/img.txt
    import re
    app = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    nomi = re.findall(r'(\w+) = \d+', open(os.path.join(app, 'src', 'immagini.lua')).read())
    for t in lista(l.api.tecniche):
        for p in lista(t.passi):
            if p.img:
                assert p.img in nomi, p.img


def test_spunte_e_conteggio():
    l = Lettore()
    assert '0 / 20 nello zaino' in l.testi()
    x, y = l.dove('Sacco a pelo')
    l.tocca(30, y + 10)                           # la casella
    assert '1 / 20 nello zaino' in l.testi()
    l.tocca_testo('Pantalone tuta')
    assert l.api.scr() == 'voce'
    l.tocca_testo('Metti nello zaino')
    assert l.api.scr() == 'attrezzatura'
    assert '2 / 20 nello zaino' in l.testi()
    l2 = Lettore(store=dict(l.store))
    assert '2 / 20 nello zaino' in l2.testi()


def test_tutte_le_pagine_attrezzatura():
    l = Lettore()
    viste = set()
    for _ in range(6):
        viste.update(l.testi())
        assert l.comandi < LIMITE
        l.scorri('swipe_left')
    for g in lista(l.api.attrezzatura):
        for v in lista(g.voci):
            assert v.nome in viste, v.nome
    assert 'Togli tutte le spunte' in viste


def test_ogni_voce_si_apre():
    l = Lettore()
    for _ in range(6):
        for g in lista(l.api.attrezzatura):
            for v in lista(g.voci):
                if v.nome in l.testi():
                    l.tocca_testo(v.nome)
                    assert l.api.scr() == 'voce'
                    assert l.comandi < LIMITE
                    l.tasto('back')
        l.scorri('swipe_left')


def test_sezioni_e_avviso_trappole():
    l = Lettore()
    l.tocca_testo('Tecniche')
    l.tocca_testo('Nodi')
    assert 'Nodo a otto' in l.testi()
    l.tocca_testo('Trappole')
    t = ' '.join(l.testi())
    assert 'vietate' in t and 'Trappola a quattro' in t


def test_percorso():
    l = Lettore()
    l.tocca_testo('Percorso')
    assert 'Prima di partire' in l.testi()
    x, y = l.dove('Ho controllato il bollettino meteo e quello')
    l.tocca(30, y + 10)
    assert l.api.prima()['meteo']


def test_tasti():
    l = Lettore()
    l.tocca_testo('Tecniche')
    l.tocca_testo('Accendere il fuoco')
    l.tocca_testo('Inizia')
    l.tasto('right')
    assert l.api.passo() == 2
    l.tasto('left')
    assert l.api.passo() == 1
    l.tasto('back')
    assert l.api.scr() == 'tutorial'
    l.tasto('back')
    assert l.api.scr() == 'tecniche'


def main():
    fall = 0
    for nome, fn in list(globals().items()):
        if nome.startswith('test_'):
            try:
                fn()
                print('ok  ', nome)
            except Exception:
                fall += 1
                print('FAIL', nome)
                traceback.print_exc()
    print(f'{fall} falliti' if fall else 'tutti i test passati')
    sys.exit(1 if fall else 0)


if __name__ == '__main__':
    main()
