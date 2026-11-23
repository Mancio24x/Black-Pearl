"""Test di Ai Fornelli sul simulatore (tools/anteprima.py).

    python3 -I tools/test_app.py
"""
import os, sys, traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anteprima import Lettore  # noqa: E402

LIMITE_COMANDI = 900          # il lettore ne disegna circa 1024 per schermata e salta il resto


def ricette(l):
    R = l.api.ricette
    return [R[k] for k in range(1, len(R) + 1)]


def test_ogni_ricetta_e_ogni_passo():
    l = Lettore()
    for k, r in enumerate(ricette(l), 1):
        l.api.apri(k)
        l.disegna()
        assert l.comandi < LIMITE_COMANDI, (r.id, 'dettaglio', l.comandi)
        assert r.nome.split(' ')[0] in ' '.join(l.testi()), r.id
        # tutte le pagine di ingredienti
        for _ in range(4):
            l.scorri('swipe_left')
        l.tocca_testo('Consigli')
        assert l.comandi < LIMITE_COMANDI
        for _ in range(3):
            l.scorri('swipe_left')
        l.tasto('back')
        l.tocca_testo('Inizia a cucinare')
        n = len(r.passi)
        for p in range(1, n + 1):
            assert l.api.passo() == p
            t = l.testi()
            assert f'{p} / {n}' in t, (r.id, p, t)
            assert l.comandi < LIMITE_COMANDI, (r.id, p, l.comandi)
            # il testo del passo non deve finire sotto il riquadro del timer
            for s, x, y in l.scritti:
                assert x >= 0 and x + 1 <= 480, (r.id, p, s)
            if p < n:
                l.tocca_testo('Avanti')
        assert 'Fatto!' in l.testi()
        l.tasto('back')
        l.tasto('back')


def test_testo_dei_passi_entra():
    """ogni passo deve stare per intero sullo schermo, sopra il timer o le righe in fondo"""
    l = Lettore()
    for k, r in enumerate(ricette(l), 1):
        l.api.apri(k)
        l.disegna()
        l.tocca_testo('Inizia a cucinare')
        for p in range(1, len(r.passi) + 1):
            l.disegna()
            testo = r.passi[p][2]
            parole = testo.split(' ')
            visto = ' '.join(s for s, x, y in l.scritti if y < (566 if r.passi[p].t else 644))
            assert parole[-1] in visto, (r.id, p, 'il testo non entra')
            if p < len(r.passi):
                l.tocca_testo('Avanti')


def test_dosi():
    l = Lettore()
    d = l.api.dose
    assert d('{500} g', 1) == '500 g'
    assert d('{500} g', 0.5) == '250 g'
    assert d('{7}-{8} g', 2) == '14-16 g'
    assert d('{1}', 0.5) == '½'
    assert d('{3}', 0.5) == '1½'
    assert d('{1,8} l', 0.5) == '0,9 l'
    assert d('{1/2}', 2) == '1'
    assert d('{2} x 300-350 g', 2) == '4 x 300-350 g'
    assert d('q.b.', 3) == 'q.b.'
    assert d('{28} g', 1.5) == '42 g'
    assert d('{320} g', 0.25) == '80 g'


def test_porzioni_cambiano_e_restano():
    l = Lettore()
    k = [r.id for r in ricette(l)].index('carbonara') + 1
    l.api.apri(k)
    l.disegna()
    assert 'Dosi per 4 persone' in l.testi()
    assert '320 g' in l.testi()
    y = l.dove('Dosi per 4 persone')[1] + 8
    for _ in range(2):
        l.tocca(40, y)                                          # il meno
    assert 'Dosi per 2 persone' in l.testi(), l.testi()
    assert '160 g' in l.testi()
    l.tocca(40, l.dove('Dosi per 2 persone')[1] + 8)
    assert 'Dosi per 1 persona' in l.testi()
    l2 = Lettore(store=dict(l.store))
    l2.api.apri(k)
    assert 'Dosi per 1 persona' in l2.testi()


def test_timer_avvisa_e_resta_dopo_riavvio():
    l = Lettore()
    k = [r.id for r in ricette(l)].index('carbonara') + 1
    l.api.apri(k)
    l.disegna()
    l.tocca_testo('Inizia a cucinare')
    l.tocca_testo('Avvia')                                         # passo 1: 10 minuti
    assert '10 min' in l.testi()
    assert l.awake > 0, 'con un timer acceso il lettore non deve addormentarsi'
    l.avanti(4 * 60)
    assert '6 min' in l.testi(), l.testi()
    # riavvio dell'app: il timer continua
    l2 = Lettore(store=dict(l.store), epoch=l.epoch + 4 * 60)
    l2.api.apri(k)
    l2.disegna()
    l2.tocca_testo('Inizia a cucinare')
    assert '6 min' in l2.testi(), l2.testi()
    l2.avanti(5 * 60 + 30)
    assert '30 s' in l2.testi(), l2.testi()
    l2.avanti(31)
    assert len(l2.scritti) == 0, 'durante il lampeggio lo schermo è nero'
    l2.avanti(10)
    t = l2.testi()
    assert 'Tempo scaduto!' in t, t
    assert l2.full > 0                                            # il lampeggio finisce con un aggiornamento pieno
    l2.tocca_testo('OK')
    assert len(l2.api.timers()) == 0
    assert 'Tempo scaduto!' not in l2.testi()


def test_timer_altro_passo_visibile():
    l = Lettore()
    k = [r.id for r in ricette(l)].index('lasagne') + 1
    l.api.apri(k)
    l.disegna()
    l.tocca_testo('Inizia a cucinare')
    l.tocca_testo('Avvia')
    l.tocca_testo('Avanti')
    assert any(s.startswith('Timer: passo 1') for s in l.testi()), l.testi()
    l.tasto('back')
    l.tocca_testo('Oggi') if 'Oggi' in l.testi() else None
    l.tasto('back')
    l.disegna()
    assert l.api.scr() == 'oggi'
    assert any(s.startswith('Timer: Lasagne') for s in l.testi()), l.testi()


def test_fine_ricetta_segna_nel_diario():
    l = Lettore()
    k = [r.id for r in ricette(l)].index('tazza') + 1
    l.api.apri(k)
    l.disegna()
    l.tocca_testo('Inizia a cucinare')
    for _ in range(5):
        l.tocca_testo('Avanti')
    l.tocca_testo('Fatto!')
    assert 'Buon appetito!' in l.testi()
    l.tocca_testo('Sì, segna')
    assert l.api.scr() == 'cucinati'
    t = l.testi()
    assert 'Tazza Sburrata Gocciole e Panna' in t, t
    assert '1 piatto · 1 ricetta diversa' in t
    l.tocca_testo('Oggi')
    assert any('Questo mese: 1 piatto' in s for s in l.testi())
    # tenendo premuto si toglie
    l.tocca_testo('Cucinati')
    l.tocca_testo('Tazza Sburrata Gocciole e Panna', gesto='long')
    l.tocca_testo('Togli')
    assert len(l.api.diario()) == 0


def test_filtri():
    l = Lettore()
    l.tocca_testo('Ricette')
    assert '17' in l.testi()
    l.tocca_testo('Dolci')
    assert '4' in l.testi(), l.testi()
    l.tocca_testo('Nonna')
    assert '3' in l.testi()
    l.tocca_testo('Tutte')
    l.tocca_testo('Nonna')
    l.tocca_testo('Sotto 30 min')
    t = l.testi()
    assert 'Spaghetti alla carbonara' in t and 'Tazza Sburrata Gocciole e Panna' in t, t
    l.tocca_testo('Nonna')
    l.tocca_testo('Sotto 30 min')
    l.tocca_testo('Pizza')
    assert 'Nessuna ricetta con questi filtri' in l.testi()


def test_lista_a_pagine():
    l = Lettore()
    l.tocca_testo('Ricette')
    visti = set()
    for _ in range(8):
        for r in ricette(l):
            if r.nome in l.testi():
                visti.add(r.id)
        assert l.comandi < LIMITE_COMANDI
        l.scorri('swipe_left')
    assert len(visti) == 17, visti


def test_oggi_cambia_con_un_altro_e_col_giorno():
    l = Lettore()
    a = l.api.del_giorno()
    l.tocca_testo('Un altro')
    b = l.api.del_giorno()
    assert a != b
    l2 = Lettore(epoch=l.epoch + 86400)
    assert l2.api.del_giorno() != a


def test_calendario_e_date():
    l = Lettore()
    dd, gd = l.api.data_da_giorno, l.api.giorno_da_data
    for g in (0, 20780, 11016, 21000, 24000):
        y, m, d = dd(g)
        assert gd(y, m, d) == g
    assert tuple(dd(20780)) == (2026, 11, 23)
    l.tocca_testo('Cucinati')
    assert 'Novembre 2026' in l.testi()
    l.scorri('swipe_left')
    assert 'Dicembre 2026' in l.testi()
    l.scorri('swipe_left')
    assert 'Gennaio 2027' in l.testi()
    assert l.comandi < LIMITE_COMANDI


def test_tasti_laterali():
    l = Lettore()
    l.tasto('ok')
    assert l.api.scr() == 'dettaglio'
    l.tocca_testo('Inizia a cucinare')
    l.tasto('right')
    assert l.api.passo() == 2
    l.tasto('left')
    assert l.api.passo() == 1
    l.tasto('back')
    assert l.api.scr() == 'dettaglio'
    l.tasto('back')
    assert l.api.scr() == 'oggi'


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
