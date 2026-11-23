"""Costruisce Bivacco.

    python3 -I tools/build.py            index.lua + controlli + dist/bivacco.xtapp
    python3 -I tools/build.py --solo-lua solo index.lua e i controlli

1. Unisce vendor/gamekit.lua.in (gamekit.lua di touch-licorice-apps) e i moduli di src/ in index.lua (sul lettore non c'è
   `require` per i file del pacchetto: ogni modulo diventa una funzione dentro index.lua).
2. Controlla i testi: solo lettere Latin-1 (accenti italiani sì, trattini lunghi e virgolette curve
   no: il carattere del lettore potrebbe non averli) e niente assegnazioni alle variabili dei cicli
   for (in Lua 5.5 sono costanti).
3. Impacchetta con vendor/xtpack.py (da touch-licorice-apps, MIT): manifest, index.lua, data/*.txt
   e icon.png diventano dist/bivacco.xtapp, cifrato per la chiave dei lettori X4 Pro.
"""
import os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)


def bundle():
    kit = open(os.path.join(APP, 'vendor', 'gamekit.lua.in'), encoding='utf-8').read().rstrip('\n')
    parts = ["-- GENERATO da tools/build.py da vendor/gamekit.lua.in e src/: modifica quelli, non questo file.\n",
             "local kit = (function()\n" + kit + "\nend)()\n",
             """
local __mods, __loaded = {}, {}
local function require(n)
  local v = __loaded[n]
  if v ~= nil then return v end
  local f = __mods[n]
  if not f then error('modulo mancante: ' .. tostring(n)) end
  v = f()
  if v == nil then v = true end
  __loaded[n] = v
  return v
end
"""]
    src = os.path.join(APP, 'src')
    for f in sorted(os.listdir(src)):
        if f.endswith('.lua') and f != 'main.lua':
            body = open(os.path.join(src, f), encoding='utf-8').read().rstrip('\n')
            parts.append(f"\n__mods['{f[:-4]}'] = function()\n{body}\nend\n")
    parts.append('\n' + open(os.path.join(src, 'main.lua'), encoding='utf-8').read())
    text = ''.join(parts)
    open(os.path.join(APP, 'index.lua'), 'w', encoding='utf-8', newline='\n').write(text)
    return text


def controlla(text):
    errori = []
    for n, riga in enumerate(text.splitlines(), 1):
        for ch in riga:
            if ord(ch) > 255:
                errori.append(f'index.lua:{n}: carattere {ch!r} (U+{ord(ch):04X}) fuori da Latin-1')
    # Lua 5.5: le variabili dei for sono costanti (vendor/lualint55.py, da touch-licorice-apps)
    sys.path.insert(0, os.path.join(APP, 'vendor'))
    import lualint55
    errori += [f'Lua 5.5: {p}' for p in lualint55.check(os.path.join(APP, 'index.lua'))]
    return errori


def main():
    text = bundle()
    print(f'index.lua: {len(text.encode())} byte')
    err = controlla(text)
    for e in err:
        print('ATTENZIONE', e)
    if err:
        raise SystemExit('testi da correggere')
    if '--solo-lua' in sys.argv:
        return
    os.makedirs(os.path.join(APP, 'dist'), exist_ok=True)
    out = os.path.join(APP, 'dist', 'bivacco.xtapp')
    subprocess.run([sys.executable, '-I', os.path.join(APP, 'vendor', 'xtpack.py'), APP, '-o', out], check=True)
    print('pacchetto:', out, os.path.getsize(out), 'byte')


if __name__ == '__main__':
    main()
