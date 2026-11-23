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
    for k in range(1, len(l.api.tecniche) + 1):
        t = l.api.tutorial(k)
        l.api.apri(k)
        l.disegna()
        assert l.comandi < LIMITE, (t.id, l.comandi)
        l.tocca(240, 748)                         # Inizia (un'immagine)
        n = len(t.passi)
        for p in range(1, n + 1):
            testi = l.testi()
            assert f'{p} / {n}' in testi, (t.id, p, testi)
            assert l.comandi < LIMITE, (t.id, p, l.comandi)
            # l'ultima parola del testo del passo è sullo schermo: il testo entra
            ultima = t.passi[p][2].split(' ')[-1]
            assert any(ultima in s for s in testi), (t.id, p, 'testo tagliato')
            l.tocca(330, 748)                     # Avanti / Fine (immagini)
        assert l.api.scr() == 'tutorial'
        assert l.api.fatti()[t.id]


def test_immagini_dei_passi():
    """ogni passo ha un disegno, e ogni disegno nominato esiste nell'elenco delle immagini"""
    import re
    l = Lettore()
    app = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    nomi = set(re.findall(r'\|(\w+)=', open(os.path.join(app, 'src', 'immagini.lua')).read()))
    for k in range(1, len(l.api.tecniche) + 1):
        t = l.api.tutorial(k)
        assert len(t.passi) == t.n
        for p in lista(t.passi):
            assert p.img, (t.id, p[1])
            assert p.img in nomi, p.img


def test_spunte_e_conteggio():
    l = Lettore()
    assert '0 / 20 nello zaino' in l.testi()
    x, y = l.dove('Sacco a pelo')
    l.tocca(440, y + 10)                          # la casella, a destra
    assert '1 / 20 nello zaino' in l.testi()
    l.scorri('swipe_left')
    l.tocca_testo('Pantalone tuta')
    assert l.api.scr() == 'voce'
    l.tocca(240, 748)                             # Metti nello zaino (immagine)
    assert l.api.scr() == 'attrezzatura'
    assert '2 / 20 nello zaino' in l.testi()
    l.tocca_testo('Pantalone tuta')
    assert 'Nello zaino · tocca per togliere' in l.testi()
    l.tasto('back')
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


def test_massimo_7_immagini_per_schermata():
    l = Lettore()
    viste = 0
    for scheda in ('Attrezzatura', 'Tecniche', 'Percorso'):
        l.scheda(scheda)
        sezioni = ('Bushcraft', 'Nodi', 'Trappole', 'Tarp') if scheda == 'Tecniche' else (None,)
        for sez in sezioni:
            if sez:
                l.sezione(sez)
            for _ in range(7):
                l.disegna()
                assert l.n_img <= 7, (scheda, sez, l.n_img)
                viste += 1
                l.scorri('swipe_left')
    assert viste > 10


def _giro_completo(l, ogni_disegno):
    for scheda in ('Attrezzatura', 'Tecniche', 'Percorso'):
        l.scheda(scheda)
        ogni_disegno(l)
        sezioni = ('Bushcraft', 'Nodi', 'Trappole', 'Tarp') if scheda == 'Tecniche' else (None,)
        for sez in sezioni:
            if sez:
                l.sezione(sez)
                ogni_disegno(l)
            for _ in range(6):
                l.scorri('swipe_left')
                ogni_disegno(l)
    l.scheda('Tecniche')
    l.sezione('Nodi')
    l.tocca_testo('Nodo piano')
    l.tocca(240, 748)
    for _ in range(4):
        l.tasto('right')
        ogni_disegno(l)


def test_poco_lavoro_per_fotogramma():
    """il lettore chiude l'app se un fotogramma dura più di circa un secondo: al massimo 2 immagini
    decodificate per fotogramma, e prima di decodificare l'app chiede più tempo (ctx.budget:extend)"""
    l = Lettore()
    viste = []
    def controlla(l):
        viste.append(l.ultime_decodifiche)
        assert l.ultime_decodifiche <= 2, (l.api.scr(), l.ultime_decodifiche)
        if l.ultime_decodifiche:
            assert l.ultime_estensioni >= 1, 'decodifica senza chiedere tempo'
    _giro_completo(l, controlla)
    assert max(viste) >= 1


def test_le_immagini_arrivano_tutte():
    """le immagini rimandate arrivano nei fotogrammi dopo: alla fine la schermata le ha tutte"""
    l = Lettore()
    l.scheda('Tecniche')
    l.avanti(2)
    assert l.n_img >= 6, l.n_img            # intestazione, sezioni, barra e le miniature


def test_memoria_viva_bassa():
    """il lettore ha circa 1 MB per l'app: la parte sempre occupata deve restare piccola"""
    l = Lettore()
    kb = l.memoria_viva()
    assert kb < 180, f'memoria viva {kb:.0f} KB'
    l.scheda('Tecniche'); l.sezione('Nodi'); l.tocca_testo('Nodo piano'); l.tocca(240, 748)
    kb = l.memoria_viva()
    assert kb < 190, f'memoria viva in un tutorial {kb:.0f} KB'


def test_tasto_indietro_ovunque():
    """ogni schermata ha il tasto indietro in alto a sinistra; dalle schede torna alla scheda di prima;
    senza niente prima chiede se uscire"""
    l = Lettore()
    l.tocca(42, 32)
    assert 'Uscire da Bivacco?' in l.testi()
    l.tocca_testo('Resta')
    l.scheda('Tecniche')
    l.tocca_testo('Accendere il fuoco')
    l.tocca(240, 748)                         # Inizia
    for atteso in ('tutorial', 'tecniche', 'attrezzatura'):
        l.tocca(42, 32)
        assert l.api.scr() == atteso, (l.api.scr(), atteso)
    l.tocca(42, 32)
    l.tocca_testo('Esci')
    assert getattr(l, 'uscito', False)


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
    l.scheda('Tecniche')
    l.sezione('Nodi')
    assert 'Nodo a otto' in l.testi()
    l.sezione('Trappole')
    t = ' '.join(l.testi())
    assert 'Vietate' in t and 'Trappola a quattro' in t


def test_percorso():
    l = Lettore()
    l.scheda('Percorso')
    assert 'PRIMA DI PARTIRE' in l.testi()
    x, y = l.dove('Ho controllato il bollettino meteo e quello')
    l.tocca(30, y + 10)
    assert l.api.prima()['meteo']


def test_tasti():
    l = Lettore()
    l.scheda('Tecniche')
    l.tocca_testo('Accendere il fuoco')
    l.tocca(240, 748)
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
