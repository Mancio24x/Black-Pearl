-- GENERATO da tools/build.py da vendor/gamekit.lua.in e src/: modifica quelli, non questo file.
local kit = (function()
-- gamekit: shared pieces for the API 2.9 puzzle games (Lights Out, Peg Solitaire, Nonograms,
-- Knight's Tour). tools/gamekit/bundle.py puts it in front of a game's src/main.lua as
-- `local kit = (function() ... end)()`, since the device has no require for package files.
--
--   kit.W, kit.H                      screen size
--   kit.font(name), kit.width(f, s), kit.line_h(name)
--   kit.short(s, n), kit.drop_last(s), kit.wrap(s, fname, maxw)
--   kit.read(name), kit.write(name, s), kit.kv(s)   ctx.storage helpers ("key=value" lines)
--   kit.now_ms(), kit.refresh()
--   kit.moved()                       count a move (a hook for a periodic full refresh; see below)
--   kit.key_accepted(ev)              side buttons send several events per press: keep one
--   kit.show(m), kit.close(), kit.modal()   a box over the screen: { title, lines, rows, cancel },
--                                     rows = { { {label, fn, enabled}, .. }, .. }
--   kit.draw_modal(gg), kit.tap_modal(x, y) -> true when the box took the tap
--   kit.button(gg, x, y, w, h, label, opts)  a framed button with a centred label
-- Every ctx.* function is a method on this API: colon calls everywhere. Nothing is filled black:
-- large black areas ghost on e-ink, and the host draws text black even when asked for white.

local kit = {}
kit.W, kit.H = 480, 800

-- a font by name ('small' / 'medium' / 'large'), or a number: that many pixels (ctx.fonts:size,
-- API 2.3: the reader's .ttf font, or the nearest system font when there is none)
function kit.font(name)
  local F = ctx.fonts
  if not F then return nil end
  if type(name) == 'number' then
    local ok, f = pcall(function() return F:size(name) end)
    return ok and f or F.medium
  end
  return F[name]
end
function kit.width(f, s)
  local ok, w = pcall(function() return f:measure(s) end)
  return (ok and type(w) == 'number') and w or #s * 11
end
function kit.line_h(name)
  local ok, h = pcall(function() return kit.font(name).line_height end)
  return (ok and tonumber(h)) or (type(name) == 'number' and name * 5 // 4) or (name == 'large' and 30 or name == 'small' and 20 or 24)
end

-- drop the last character (a whole UTF-8 sequence)
function kit.drop_last(s) return (s:gsub('[%z\1-\127\192-\255][\128-\191]*$', '')) end
function kit.short(s, n)
  s = tostring(s)
  if #s <= n then return s end
  local t = s:sub(1, n)
  while #t > n - 1 or (#t > 0 and t:sub(-1):byte() >= 128 and not utf8.len(t)) do t = kit.drop_last(t) end
  return t .. '~'
end
-- word wrap on ASCII spaces only (%s may also match 0xA0, the second byte of some letters)
function kit.wrap(s, fname, maxw)
  local f, out = kit.font(fname), {}
  for para in (tostring(s) .. '\n'):gmatch('([^\n]*)\n') do
    local line = ''
    for word in para:gmatch('[^ \t]+') do
      local cand = line == '' and word or (line .. ' ' .. word)
      if line ~= '' and kit.width(f, cand) > maxw then out[#out + 1] = line; line = word else line = cand end
    end
    out[#out + 1] = line
  end
  return out
end

function kit.read(name)
  local ok, s = pcall(function() return ctx.storage:read(name) end)
  return (ok and type(s) == 'string') and s or nil
end
function kit.write(name, s) pcall(function() ctx.storage:write(name, s) end) end
function kit.kv(s)
  local t = {}
  for k, v in (s or ''):gmatch('([%w_]+)=([^\n]*)') do t[k] = (v:gsub('%s+$', '')) end
  return t
end

function kit.now_ms() local ok, t = pcall(function() return ctx.clock:millis() end); return ok and tonumber(t) or nil end
function kit.refresh() pcall(function() ctx:invalidate() end) end

-- e-ink full refresh: API 0.8 had ctx.display:full_refresh(); API 2.9 only offers
-- ctx:defer_full_refresh(n), which on the device (26102401) did not trigger one, and which by its
-- name may rather postpone the host's own periodic refresh. So nothing is asked for: the games
-- showed no ghosting problem without it. kit.moved() stays as the place to add one.
local moves = 0
function kit.moved() moves = moves + 1 end

local KEY_GAP_MS = 250
local last_key, last_key_ms = nil, nil
function kit.key_accepted(ev)
  local ph = ev.phase or ev.action or ev.state
  if ph == 'up' or ph == 'release' or ph == 'released' or ev.pressed == false or ev.down == false then return false end
  local t = tonumber(ev.time_ms) or kit.now_ms()
  if t and last_key == ev.key and last_key_ms and t - last_key_ms >= 0 and t - last_key_ms < KEY_GAP_MS then return false end
  last_key, last_key_ms = ev.key, t
  return true
end

------------------------------------------------------------------ boxes

local BOX_X, ROW_H, GAP = 20, 56, 10
local BOX_W = kit.W - 40      -- refreshed in layout(): an app may set kit.W, kit.H for a landscape screen
local modal = nil

function kit.show(m) modal = m end
function kit.close() modal = nil end
function kit.modal() return modal end

local function layout(m)
  BOX_W = kit.W - 40
  local lines = {}
  if m.title then for _k, l in ipairs(kit.wrap(m.title, 'medium', BOX_W - 40)) do lines[#lines + 1] = { l, 'medium' } end end
  for _k, s in ipairs(m.lines or {}) do
    for _j, l in ipairs(kit.wrap(s, 'small', BOX_W - 40)) do lines[#lines + 1] = { l, 'small' } end
  end
  local th = 0
  for _k, l in ipairs(lines) do th = th + kit.line_h(l[2]) + 4 end
  local h = 20 + th + 14 + #m.rows * (ROW_H + GAP) + 10
  local y = math.max(60, math.floor((kit.H - h) / 2))
  local btns, by = {}, y + 20 + th + 14
  for _k, row in ipairs(m.rows) do
    local n = #row
    local bw = math.floor((BOX_W - 40 - (n - 1) * GAP) / n)
    for k, btn in ipairs(row) do
      btns[#btns + 1] = { x = BOX_X + 20 + (k - 1) * (bw + GAP), y = by, w = bw, h = ROW_H, b = btn }
    end
    by = by + ROW_H + GAP
  end
  return lines, btns, y, h
end

function kit.button(gg, x, y, w, h, label, opts)
  opts = opts or {}
  local f = kit.font(opts.font or 'medium')
  gg:rect(x, y, w, h, { line_width = opts.line_width or 2 })
  local s = tostring(label)
  while #s > 1 and kit.width(f, s) > w - 10 do s = kit.drop_last(s) end
  gg:text(x + math.floor((w - kit.width(f, s)) / 2), y + math.floor((h - kit.line_h(opts.font or 'medium')) / 2), s, { font = f })
end

function kit.draw_modal(gg)
  local lines, btns, y, h = layout(modal)
  -- paper first, so the page under the box does not show through it
  gg:rect(BOX_X, y, BOX_W, h, { fill = true, color = 0 })
  gg:rect(BOX_X, y, BOX_W, h)
  gg:rect(BOX_X + 3, y + 3, BOX_W - 6, h - 6)
  local ty = y + 20
  for _k, l in ipairs(lines) do gg:text(BOX_X + 20, ty, l[1], { font = kit.font(l[2]) }); ty = ty + kit.line_h(l[2]) + 4 end
  for _k, bt in ipairs(btns) do
    local enabled = bt.b[3] ~= false
    kit.button(gg, bt.x, bt.y, bt.w, bt.h, enabled and bt.b[1] or ('(' .. bt.b[1] .. ')'), { line_width = enabled and 2 or 1 })
  end
end

function kit.tap_modal(x, y)
  if not modal then return false end
  local _l, btns, my, mh = layout(modal)
  for _k, bt in ipairs(btns) do
    if x >= bt.x and x < bt.x + bt.w and y >= bt.y and y < bt.y + bt.h then
      if bt.b[3] ~= false then bt.b[2]() end
      return true
    end
  end
  if (y < my or y > my + mh) and modal.cancel then modal.cancel() end
  return true
end

return kit
end)()

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

__mods['attrezzatura'] = function()
-- L'attrezzatura, nell'ordine in cui si carica lo zaino da 60 litri: dal fondo alla cima.
--
-- La regola: in fondo ciò che è leggero e serve solo la sera, al centro e contro la schiena ciò che
-- pesa, in cima e nelle tasche ciò che serve durante il cammino. Così il peso sta vicino al corpo e
-- in alto sulle spalle, e non devi svuotare lo zaino per trovare la giacca quando piove.
--
-- Ogni gruppo: { titolo, spiega, img (facoltativa), voci = { { id, nome, peso (grammi, circa), dove, perche, consiglio } } }
-- L'id serve a salvare la spunta: non cambiarlo.

return {

{ titolo = 'Gli zaini', img = 'zaino',
  spiega = "Prima si sceglie e si regola lo zaino grande: tutto il resto si dispone dentro di lui.",
  voci = {
    { id = 'zaino60', nome = 'Zaino 60 L', peso = 1800, dove = 'Sulle spalle',
      perche = "Porta tutto per più giorni: riparo, cucina, vestiti, cibo.",
      consiglio = "Carico, il peso va sui fianchi: allaccia prima la cintura sopra le ossa del bacino, poi stringi gli spallacci, poi i tiranti di carico in alto. Fodera l'interno con un sacco della spazzatura robusto: è il miglior coprizaino." },
    { id = 'zaino20', nome = 'Zaino 20 L', peso = 400, dove = 'Piegato in cima allo zaino grande',
      perche = "Al campo diventa lo zainetto per la vetta o per cercare acqua e legna, senza portarsi dietro tutto.",
      consiglio = "Dentro, per la giornata: acqua, filtro, cibo, pile, antipioggia, frontale e coltello." },
  } },

{ titolo = 'Addosso',
  spiega = "Quello che indossi non va nello zaino.",
  voci = {
    { id = 'scarpe', nome = 'Scarpe da trekking', peso = 1200, dove = 'Ai piedi',
      perche = "Proteggono le caviglie e tengono sul bagnato e sulle pietre.",
      consiglio = "Mai nuove il giorno della partenza: rodale prima in passeggiate brevi. La sera allenta i lacci e togli le solette per farle asciugare." },
  } },

{ titolo = '1. Il fondo',
  spiega = "Leggero, voluminoso, serve solo la sera: il sonno. Riempie il fondo e fa da cuscinetto.",
  voci = {
    { id = 'sacco', nome = 'Sacco a pelo', peso = 1100, dove = 'In fondo, nella sacca stagna',
      perche = "Senza un sonno caldo la notte in montagna è pericolosa, anche d'estate.",
      consiglio = "Sceglilo con la temperatura di comfort adatta alla notte più fredda prevista. Tienilo sempre asciutto: un sacco bagnato non scalda." },
    { id = 'tuta', nome = 'Pantalone tuta', peso = 350, dove = 'In fondo, col sacco a pelo',
      perche = "È il pantalone asciutto per la sera e per dormire.",
      consiglio = "Non usarlo per camminare: deve restare sempre asciutto." },
    { id = 'calze', nome = 'Calze di ricambio', peso = 100, dove = 'In fondo, nella sacca stagna',
      perche = "Piedi asciutti vuol dire niente vesciche e niente freddo.",
      consiglio = "Un paio per la notte da tenere sempre asciutto. Quelle bagnate si asciugano di notte dentro il sacco, sul petto." },
    { id = 'maglietta', nome = 'Maglietta di ricambio', peso = 150, dove = 'In fondo, nella sacca stagna',
      perche = "Al campo togli subito quella sudata: il sudore che si raffredda ruba calore.",
      consiglio = "Meglio lana merino o sintetica. Il cotone bagnato non scalda più." },
  } },

{ titolo = '2. Il centro, contro la schiena',
  spiega = "Le cose pesanti: vicino alla schiena e all'altezza delle scapole, così lo zaino non ti tira indietro.",
  voci = {
    { id = 'cibo', nome = 'Cibo', peso = 800, dove = 'Al centro, contro la schiena',
      perche = "Energia per camminare e calore per la notte.",
      consiglio = "Circa 600-800 g al giorno di cibo secco e calorico: frutta secca, pasta, riso, avena, cioccolato. Gli snack del giorno nelle tasche della cintura." },
    { id = 'fornello', nome = 'Fornelletto', peso = 350, dove = 'Al centro, con la cartuccia dentro il pentolino',
      perche = "Pasti caldi e acqua bollita, cioè sicura da bere.",
      consiglio = "Porta l'accendino anche se ha l'accensione automatica. Mai cucinare dentro la tenda chiusa: monossido di carbonio." },
    { id = 'acqua', nome = 'Acqua', peso = 2000, dove = 'Sacca idrica contro la schiena, o borracce nelle tasche laterali',
      perche = "È la cosa più pesante e la più importante.",
      consiglio = "Parti con 1,5-2 litri e studia sulla carta dove riempire. Bevi poco e spesso, prima di avere sete." },
    { id = 'tenda', nome = 'Tenda', peso = 1600, dove = 'Al centro; i paletti in verticale lungo un fianco',
      perche = "Riparo da pioggia, vento, insetti e freddo.",
      consiglio = "Montala a casa almeno una volta. Se è bagnata, mettila nella tasca esterna per non bagnare il resto." },
    { id = 'materassino', nome = 'Materassino', peso = 450, dove = 'Gonfiabile dentro in verticale; in espanso fuori, sotto o di lato',
      perche = "Isola dal terreno: è da sotto che si perde più calore di notte.",
      consiglio = "Dentro lo zaino, arrotolato lungo la schiena, fa anche da schienale. Controlla le valvole prima di partire." },
  } },

{ titolo = '3. La cima',
  spiega = "Quello che può servire all'improvviso: pioggia, vento, una sosta al freddo.",
  voci = {
    { id = 'antipioggia', nome = 'Antipioggia', peso = 350, dove = 'In cima, sopra tutto',
      perche = "Il tempo in montagna cambia in mezz'ora. Ripara anche dal vento.",
      consiglio = "Giacca, e se puoi anche i pantaloni. Indossala ai primi goccioloni, non quando sei già bagnato." },
    { id = 'pile', nome = 'Pile', peso = 350, dove = 'In cima',
      perche = "Lo strato che scalda anche quando è umido.",
      consiglio = "Mettilo nelle soste e toglilo prima di ripartire: si cammina con un po' di freddo." },
    { id = 'piumino', nome = 'Piumino', peso = 400, dove = 'In cima, compresso nella sua sacca',
      perche = "Il calore della sera e delle soste lunghe, in pochissimo peso.",
      consiglio = "La piuma bagnata non scalda: sotto la pioggia va sotto l'antipioggia, mai sopra." },
    { id = 'tarp', nome = 'Tarp', peso = 400, dove = "In cima o nella tasca esterna",
      perche = "Un telo per il riparo veloce: pausa sotto la pioggia, cucina coperta, o riparo per la notte.",
      consiglio = "Tienilo con i cordini già legati agli angoli: lo monti in pochi minuti (vedi Tecniche, Riparo con il tarp)." },
    { id = 'filtro', nome = 'Filtro acqua', peso = 100, dove = 'In cima o nella tasca del cappuccio',
      perche = "Rende bevibile l'acqua dei torrenti senza doverla bollire.",
      consiglio = "Non filtra i virus né le sostanze chimiche. D'inverno tienilo nel sacco a pelo la notte: se gela si rompe." },
  } },

{ titolo = '4. Tasche e cintura',
  spiega = "Le piccole cose che servono subito, senza togliere lo zaino.",
  voci = {
    { id = 'frontale', nome = 'Torcia frontale', peso = 90, dove = 'Tasca del cappuccio',
      perche = "Mani libere al buio: montare la tenda, cucinare, camminare se fai tardi.",
      consiglio = "Batterie cariche e una di scorta. Il blocco tasti evita che si accenda nello zaino." },
    { id = 'coltello', nome = 'Coltello', peso = 150, dove = 'Tasca della cintura o del cappuccio',
      perche = "Legna per il fuoco, corda, cibo, riparazioni.",
      consiglio = "Lama fissa robusta per il bushcraft. Attenzione alle leggi: portarlo senza un motivo è vietato, in escursione il motivo c'è." },
    { id = 'corda', nome = 'Corda', peso = 200, dove = 'Tasca esterna',
      perche = "Riparo, stendere i panni, appendere il cibo, riparazioni.",
      consiglio = "15-20 m di cordino da 3-4 mm (paracord). Vedi Tecniche, Nodi." },
  } },

{ titolo = 'Ti consiglio di aggiungere', facoltativo = true,
  spiega = "Non erano nella tua lista, ma in montagna fanno la differenza in un'emergenza.",
  voci = {
    { id = 'kit', nome = 'Kit di primo soccorso', peso = 250, dove = 'In cima',
      perche = "Vesciche, tagli, distorsioni.",
      consiglio = "Cerotti per vesciche, garze, disinfettante, benda elastica, antidolorifico, i tuoi farmaci." },
    { id = 'telo', nome = 'Telo termico', peso = 60, dove = 'Tasca del cappuccio',
      perche = "Pesa nulla e trattiene il calore del corpo in caso di incidente.",
      consiglio = "Avvolgilo intorno al corpo, sotto la giacca." },
    { id = 'fuoco', nome = 'Accendino e acciarino', peso = 60, dove = 'In due posti diversi',
      perche = "Il fuoco non si improvvisa: due modi per accenderlo, separati.",
      consiglio = "L'acciarino (ferro-cerio) funziona anche bagnato e al freddo." },
    { id = 'mappa', nome = 'Carta e bussola', peso = 100, dove = 'Tasca della cintura',
      perche = "Il telefono si scarica o non prende.",
      consiglio = "Carta topografica 1:25.000 della zona, in una busta impermeabile." },
    { id = 'fischietto', nome = 'Fischietto', peso = 10, dove = 'Legato allo spallaccio',
      perche = "Si sente molto più lontano della voce e non stanca.",
      consiglio = "Il segnale di soccorso alpino: 6 fischi in un minuto, poi un minuto di pausa. Si risponde con 3." },
  } },

}
end

__mods['immagini'] = function()
-- GENERATO da tools/make_art.py: dove sta ogni immagine ("|nome=numero del file,riga|")
return { file = { 'img1.txt', 'img2.txt', 'img3.txt', 'img4.txt', 'img5.txt', 'img6.txt', 'img7.txt', 'ui1.txt' },
  s = "|zaino=1,1|stop_1=1,2|stop_2=1,3|stop_3=1,4|stop_4=1,5|stop_5=1,6|stop_6=1,7|fuoco1=1,8|fuoco2=1,9|fuoco3=1,10|fuoco4=1,11|fuoco5=1,12|fuoco6=1,13|piuma1=1,14|piuma2=1,15|piuma3=1,16|piuma4=1,17|coltello_1=1,18|coltello_2=1,19|coltello_3=1,20|coltello_4=1,21|coltello_5=1,22|coltello_6=1,23|tarp1=1,24|tarp2=1,25|tarp3=1,26|tarp4=1,27|tarp5=1,28|capanna_1=1,29|capanna_2=1,30|capanna_3=1,31|capanna_4=1,32|capanna_5=1,33|capanna_6=1,34|acqua_1=2,1|acqua_2=2,2|acqua_3=2,3|acqua_4=2,4|acqua_5=2,5|acqua_6=2,6|nord1=2,7|nord2=2,8|nord3=2,9|nord4=2,10|nord_5=2,11|nord_6=2,12|otto1=2,13|otto2=2,14|otto3=2,15|otto4=2,16|gassa1=2,17|gassa2=2,18|gassa3=2,19|gassa4=2,20|gassa5=2,21|piano_1=2,22|piano_2=2,23|piano_3=3,1|piano_4=3,2|parlato_1=3,3|parlato_2=3,4|parlato_3=3,5|parlato_4=3,6|parlato_5=3,7|tendicavo_1=3,8|tendicavo_2=3,9|tendicavo_3=3,10|tendicavo_4=3,11|prusik_1=3,12|prusik_2=3,13|prusik_3=3,14|prusik_4=3,15|regole_1=3,16|regole_2=3,17|regole_3=3,18|regole_4=3,19|quattro1=3,20|quattro2=3,21|quattro3=3,22|quattro4=3,23|quattro5=3,24|laccio_1=3,25|laccio_2=3,26|laccio_3=4,1|laccio_4=4,2|laccio_5=4,3|pesca_1=4,4|pesca_2=4,5|pesca_3=4,6|pesca_4=4,7|pesca_5=4,8|tipi1=4,9|tipi2=4,10|tipi3=4,11|tipifuoco_4=4,12|tipifuoco_5=4,13|dakota1=4,14|dakota2=4,15|dakota3=4,16|dakota_4=4,17|dakota_5=4,18|archetto1=4,19|archetto_2=4,20|archetto2=4,21|archetto_4=4,22|archetto_5=4,23|archetto_6=5,1|archetto_7=5,2|batoning_1=5,3|baton1=5,4|baton2=5,5|batoning_4=5,6|batoning_5=5,7|cordame_1=5,8|cordame_2=5,9|cordame_3=5,10|cordame_4=5,11|cordame_5=5,12|cordame_6=5,13|gancio1=5,14|gancio_2=5,15|gancio_3=5,16|gancio_4=5,17|gancio_5=5,18|mezzicolli_1=5,19|mezzicolli_2=5,20|mezzicolli_3=5,21|mezzicolli_4=5,22|camionista_1=5,23|camionista_2=5,24|camionista_3=5,25|camionista_4=5,26|camionista_5=5,27|bandiera_1=5,28|bandiera_2=5,29|bandiera_3=5,30|bandiera_4=5,31|bandiera_5=5,32|quadra1=5,33|quadra2=5,34|quadra3=5,35|quadra_4=6,1|diagonale_1=6,2|diagonale_2=6,3|diagonale_3=6,4|diagonale_4=6,5|treppiede_1=6,6|treppiede_2=6,7|treppiede_3=6,8|treppiede_4=6,9|treppiede1=6,10|cesoia_1=6,11|cesoia_2=6,12|cesoia_3=6,13|cesoia_4=6,14|cesoia_5=6,15|tarp_corde_1=6,16|tarp_corde_2=6,17|tarp_corde_3=6,18|tarp_corde_4=6,19|tarp_corde_5=6,20|tarp_a_1=6,21|tarpa1=6,22|tarp_a_3=6,23|tarpa2=6,24|diam1=6,25|tarp_diamante_2=6,26|diam2=6,27|tarp_diamante_4=6,28|vomere1=6,29|tarp_vomere_2=6,30|vomere2=6,31|tarp_vomere_4=6,32|tarp_cucina_1=6,33|tarp_cucina_2=7,1|cucina1=7,2|tarp_cucina_4=7,3|tarp_pioggia_1=7,4|tarp_pioggia_2=7,5|tarp_pioggia_3=7,6|tarp_pioggia_4=7,7|tarp_pioggia_5=7,8|paiute_1=7,9|paiute_2=7,10|paiute_3=7,11|paiute_4=7,12|paiute_5=7,13|molla_1=7,14|molla_2=7,15|molla_3=7,16|molla_4=7,17|molla_5=7,18|nassa1=7,19|nassa_2=7,20|nassa_3=7,21|nassa_4=7,22|nassa_5=7,23|banner_attrezzatura=7,24|banner_tecniche=7,25|banner_percorso=7,26|tab_attrezzatura=8,1|tab_tecniche=8,2|tab_percorso=8,3|sez_sopravvivenza=8,4|sez_nodi=8,5|sez_trappole=8,6|sez_tarp=8,7|btn_avanti=8,8|btn_fine=8,9|btn_inizia=8,10|btn_zaino=8,11|ico_zaino60=8,12|ico_zaino20=8,13|ico_scarpe=8,14|ico_sacco=8,15|ico_tuta=8,16|ico_calze=8,17|ico_maglietta=8,18|ico_cibo=8,19|ico_fornello=8,20|ico_acqua=8,21|ico_tenda=8,22|ico_materassino=8,23|ico_antipioggia=8,24|ico_pile=8,25|ico_piumino=8,26|ico_tarp=8,27|ico_filtro=8,28|ico_frontale=8,29|ico_coltello=8,30|ico_corda=8,31|ico_kit=8,32|ico_telo=8,33|ico_fuoco=8,34|ico_mappa=8,35|ico_fischietto=8,36|num_1=8,37|num_2=8,38|num_3=8,39|num_4=8,40|num_5=8,41|num_6=8,42|num_7=8,43|num_8=8,44|num_9=8,45|num_10=8,46|num_11=8,47|num_12=8,48|num_13=8,49|num_14=8,50|num_15=8,51|th_stop=8,52|th_fuoco=8,53|th_piuma=8,54|th_coltello=8,55|th_tarp=8,56|th_capanna=8,57|th_acqua=8,58|th_nord=8,59|th_otto=8,60|th_gassa=8,61|th_piano=8,62|th_parlato=8,63|th_tendicavo=8,64|th_prusik=8,65|th_regole=8,66|th_quattro=8,67|th_laccio=8,68|th_pesca=8,69|th_tipifuoco=8,70|th_dakota=8,71|th_archetto=8,72|th_batoning=8,73|th_cordame=8,74|th_gancio=8,75|th_mezzicolli=8,76|th_camionista=8,77|th_bandiera=8,78|th_quadra=8,79|th_diagonale=8,80|th_treppiede=8,81|th_cesoia=8,82|th_tarp_corde=8,83|th_tarp_a=8,84|th_tarp_diamante=8,85|th_tarp_vomere=8,86|th_tarp_cucina=8,87|th_tarp_pioggia=8,88|th_paiute=8,89|th_molla=8,90|th_nassa=8,91|" }
end

__mods['indice_tecniche'] = function()
-- GENERATO da tools/build.py da src/tecniche.lua: i testi sono in data/tutorial.txt
return {
  { id = 'stop', titolo = "Ti sei perso: S.T.O.P.", sez = 'sopravvivenza', livello = 1, tempo = "5 min", n = 6 },
  { id = 'fuoco', titolo = "Accendere il fuoco", sez = 'sopravvivenza', livello = 2, tempo = "30 min", n = 6 },
  { id = 'piuma', titolo = "Il bastoncino piumato", sez = 'sopravvivenza', livello = 2, tempo = "15 min", n = 4 },
  { id = 'coltello', titolo = "Coltello e intaglio: il picchetto", sez = 'sopravvivenza', livello = 2, tempo = "20 min", n = 6 },
  { id = 'tarp', titolo = "Tarp a tettoia", sez = 'tarp', livello = 1, tempo = "20 min", n = 5 },
  { id = 'capanna', titolo = "Capanna di frasche", sez = 'sopravvivenza', livello = 3, tempo = "2-3 ore", n = 6 },
  { id = 'acqua', titolo = "Trovare e rendere sicura l'acqua", sez = 'sopravvivenza', livello = 1, tempo = "", n = 6 },
  { id = 'nord', titolo = "Trovare il nord", sez = 'sopravvivenza', livello = 1, tempo = "20 min", n = 6 },
  { id = 'otto', titolo = "Nodo a otto", sez = 'nodi', livello = 1, tempo = "2 min", n = 4 },
  { id = 'gassa', titolo = "Gassa d'amante", sez = 'nodi', livello = 2, tempo = "3 min", n = 5 },
  { id = 'piano', titolo = "Nodo piano", sez = 'nodi', livello = 1, tempo = "1 min", n = 4 },
  { id = 'parlato', titolo = "Nodo parlato", sez = 'nodi', livello = 1, tempo = "2 min", n = 5 },
  { id = 'tendicavo', titolo = "Tendicavo", sez = 'nodi', livello = 2, tempo = "3 min", n = 4 },
  { id = 'prusik', titolo = "Prusik", sez = 'nodi', livello = 3, tempo = "3 min", n = 4 },
  { id = 'regole', titolo = "Prima di tutto: la legge", sez = 'trappole', livello = 1, tempo = "3 min", n = 4 },
  { id = 'quattro', titolo = "Trappola a quattro", sez = 'trappole', livello = 3, tempo = "1 ora", n = 5 },
  { id = 'laccio', titolo = "Il laccio", sez = 'trappole', livello = 2, tempo = "20 min", n = 5 },
  { id = 'pesca', titolo = "Pesca di sopravvivenza", sez = 'trappole', livello = 2, tempo = "", n = 5 },
  { id = 'tipifuoco', titolo = "Tipi di fuoco", sez = 'sopravvivenza', livello = 1, tempo = "", n = 5 },
  { id = 'dakota', titolo = "Il fuoco Dakota", sez = 'sopravvivenza', livello = 2, tempo = "30 min", n = 5 },
  { id = 'archetto', titolo = "Fuoco ad archetto", sez = 'sopravvivenza', livello = 3, tempo = "1-2 ore", n = 7 },
  { id = 'batoning', titolo = "Spaccare la legna col coltello", sez = 'sopravvivenza', livello = 2, tempo = "10 min", n = 5 },
  { id = 'cordame', titolo = "Cordino di fibre naturali", sez = 'sopravvivenza', livello = 3, tempo = "1 ora", n = 6 },
  { id = 'gancio', titolo = "Il gancio per il pentolino", sez = 'sopravvivenza', livello = 2, tempo = "30 min", n = 5 },
  { id = 'mezzicolli', titolo = "Due mezzi colli", sez = 'nodi', livello = 1, tempo = "1 min", n = 4 },
  { id = 'camionista', titolo = "Nodo del camionista", sez = 'nodi', livello = 2, tempo = "3 min", n = 5 },
  { id = 'bandiera', titolo = "Nodo di bandiera", sez = 'nodi', livello = 1, tempo = "2 min", n = 5 },
  { id = 'quadra', titolo = "Legatura quadra", sez = 'nodi', livello = 2, tempo = "5 min", n = 4 },
  { id = 'diagonale', titolo = "Legatura diagonale", sez = 'nodi', livello = 2, tempo = "5 min", n = 4 },
  { id = 'treppiede', titolo = "Legatura a treppiede", sez = 'nodi', livello = 2, tempo = "10 min", n = 5 },
  { id = 'cesoia', titolo = "Legatura a cesoia", sez = 'nodi', livello = 2, tempo = "5 min", n = 5 },
  { id = 'tarp_corde', titolo = "Preparare il tarp", sez = 'tarp', livello = 1, tempo = "30 min", n = 5 },
  { id = 'tarp_a', titolo = "Tarp a capanna (A)", sez = 'tarp', livello = 1, tempo = "15 min", n = 4 },
  { id = 'tarp_diamante', titolo = "Tarp a diamante", sez = 'tarp', livello = 2, tempo = "10 min", n = 4 },
  { id = 'tarp_vomere', titolo = "Tarp a punta (piramide)", sez = 'tarp', livello = 2, tempo = "15 min", n = 4 },
  { id = 'tarp_cucina', titolo = "Tettoia per stare in piedi", sez = 'tarp', livello = 1, tempo = "15 min", n = 4 },
  { id = 'tarp_pioggia', titolo = "Tarp col brutto tempo", sez = 'tarp', livello = 2, tempo = "", n = 5 },
  { id = 'paiute', titolo = "Trappola Paiute", sez = 'trappole', livello = 3, tempo = "1 ora", n = 5 },
  { id = 'molla', titolo = "Laccio a molla", sez = 'trappole', livello = 3, tempo = "40 min", n = 5 },
  { id = 'nassa', titolo = "Nassa per pesci", sez = 'trappole', livello = 2, tempo = "30 min", n = 5 },
}
end

-- Bivacco: attrezzatura, tecniche e percorso per la montagna, per Xteink X4 Pro (XTOS Licorice, API 2.0).
--
-- Tre schede in basso:
--   Attrezzatura  la lista nell'ordine in cui si carica lo zaino, da spuntare; ogni voce ha dove va e perché
--   Tecniche      Bushcraft, Nodi (e legature), Trappole, Tarp: tutorial passo passo, con i disegni
--   Percorso      per ora la lista "Prima di partire": la mappa arriverà dopo
-- I testi sono in attrezzatura.lua e tecniche.lua; i disegni in data/img.txt (tools/make_art.py),
-- immagini.lua dice in che riga è ciascuno.

local ATTR = require('attrezzatura')
local TEC = require('indice_tecniche')   -- solo titoli e numeri: i testi sono in data/tutorial.txt
local IMG_T = require('immagini')
local function IMG(nome)
  local f, r = IMG_T.s:match('|' .. nome .. '=(%d+),(%d+)|')
  if not f then return nil end
  return { IMG_T.file[tonumber(f)], tonumber(r) }
end

local W, H = kit.W, kit.H
local M = 20
local TAB_Y = 736
local BTN_Y, BTN_H = 716, 64
local IMG_W, IMG_H = 440, 250

local SEZIONI = { { 'sopravvivenza', 'Bushcraft' }, { 'nodi', 'Nodi' }, { 'trappole', 'Trappole' }, { 'tarp', 'Tarp' } }
local LIVELLI = { 'facile', 'medio', 'esperto' }
local PRIMA = {
  { 'detto', "Ho lasciato detto a qualcuno dove vado e quando rientro" },
  { 'meteo', "Ho controllato il bollettino meteo e quello della montagna" },
  { 'buio', "So a che ora fa buio e quanto dura il giro" },
  { 'carica', "Telefono carico, batteria di scorta, frontale con pile nuove" },
  { 'carta', "Ho la carta della zona e so dove riempire l'acqua" },
  { 'soccorso', "So che il numero è il 112 (e ho l'app GeoResQ)" },
}

local ERR = nil

------------------------------------------------------------------ stato

local scr = 'attrezzatura'   -- 'attrezzatura', 'voce', 'zaino', 'tecniche', 'tutorial', 'passo', 'percorso'
local da = {}
local spunte = {}            -- id della voce -> true
local prima = {}             -- id della lista "prima di partire" -> true
local fatti = {}             -- id del tutorial -> true (arrivato alla fine)
local sez = 'sopravvivenza'
local pag_attr, pag_tec = 1, 1
local voce = nil             -- la voce aperta: { gruppo, voce }
local tut = 1                -- il tutorial aperto
local passo = 1
local pag_intro = 1
local zone = {}
local per_pag_attr = 1

local function salva()
  local function lista(t) local o = {} for k in pairs(t) do o[#o + 1] = k end table.sort(o) return table.concat(o, ',') end
  kit.write('stato.txt', table.concat({ 'spunte=' .. lista(spunte), 'prima=' .. lista(prima), 'fatti=' .. lista(fatti),
                                        'sez=' .. sez }, '\n'))
end

local function carica()
  local s = kit.kv(kit.read('stato.txt'))
  for id in (s.spunte or ''):gmatch('[%w_]+') do spunte[id] = true end
  for id in (s.prima or ''):gmatch('[%w_]+') do prima[id] = true end
  for id in (s.fatti or ''):gmatch('[%w_]+') do fatti[id] = true end
  for _k, x in ipairs(SEZIONI) do if x[1] == s.sez then sez = s.sez end end
end

------------------------------------------------------------------ immagini

local larghezze = {}          -- file -> lunghezza di una riga + 1 (tutte le righe sono lunghe uguali)
local function riga_img(file, n)
  local ok, r = pcall(function() return ctx.package:open_text(file, { max_line_bytes = 65536 }) end)
  if not ok or r == nil or type(r) == 'string' then return nil end
  local riga
  pcall(function()
    if not larghezze[file] then
      local prima_riga = r:read_line()
      if not prima_riga then return end
      larghezze[file] = #prima_riga + 1
      if n == 1 then riga = prima_riga; return end
    end
    r:seek((n - 1) * larghezze[file])
    riga = r:read_line()
  end)
  pcall(function() r:close() end)
  return riga and (riga:gsub(' +$', ''))
end

-- le immagini decodificate, la più usata di recente in fondo; il lettore ne tiene al massimo 8.
-- Il lettore chiude l'app se un fotogramma dura più di circa un secondo: ogni fotogramma decodifica al
-- massimo DEC_PER_FOTOGRAMMA immagini (chiedendo prima più tempo); le altre arrivano nei fotogrammi dopo.
local cache, ordine = {}, {}
local MAX_IMG = 7
local DEC_PER_FOTOGRAMMA = 2
local decodificate = 0       -- in questo fotogramma
local rimandate = false      -- qualche immagine aspetta il prossimo fotogramma
local function immagine(nome)
  if not nome then return nil end
  if cache[nome] ~= nil then
    for k, v in ipairs(ordine) do if v == nome then table.remove(ordine, k); break end end
    ordine[#ordine + 1] = nome
    return cache[nome] or nil
  end
  local dove = IMG(nome)
  if not dove then return nil end
  if decodificate >= DEC_PER_FOTOGRAMMA then rimandate = true; return nil end
  if decodificate == 0 then pcall(function() ctx.budget:extend() end) end
  decodificate = decodificate + 1
  while #ordine >= MAX_IMG do
    local v = table.remove(ordine, 1)
    if cache[v] then pcall(function() cache[v]:release() end) end
    cache[v] = nil
  end
  local img = false
  if ctx.capabilities and ctx.capabilities.image_decode and ctx.image and ctx.base64 then
    local b64 = riga_img(dove[1], dove[2])
    if b64 then
      local ok, bytes = pcall(function() return ctx.base64:decode(b64) end)
      b64 = nil
      if ok and bytes then
        local oki, im = pcall(function() return ctx.image:decode(bytes) end)
        if oki and im then img = im end
      end
    end
  end
  cache[nome] = img
  ordine[#ordine + 1] = nome
  return img or nil
end

-- il tutorial k per intero (intro, serve, attenzione, passi), da data/tutorial.txt; ne tiene uno solo
local tut_k, tut_dati = nil, nil
local function TUT(k)
  if tut_k == k then return tut_dati end
  local riga = riga_img('tutorial.txt', k)
  local t = { id = TEC[k].id, titolo = TEC[k].titolo, sez = TEC[k].sez, livello = TEC[k].livello, tempo = TEC[k].tempo,
              n = TEC[k].n, passi = {} }
  if riga then
    local campi = {}
    for c in (riga .. '\t'):gmatch('([^\t]*)\t') do campi[#campi + 1] = c end
    local function v(i) local x = campi[i]; return (x and x ~= '') and x or nil end
    t.intro, t.serve, t.attenzione = v(2) or '', v(3), v(4)
    for j = 1, t.n do
      local b = 4 + (j - 1) * 3
      t.passi[j] = { campi[b + 1] or '', campi[b + 2] or '', img = v(b + 3) }
    end
  end
  tut_k, tut_dati = k, t
  return t
end

------------------------------------------------------------------ disegno: pezzi

local F = {}
local function font(n) F[n] = F[n] or kit.font(n); return F[n] end
local function larg(n, s) return kit.width(font(n), s) end
local function lh(n) return kit.line_h(n) end
local function testo(gg, x, y, s, n) gg:text(x, y, s, { font = font(n or 'medium') }) end
local function testo_dx(gg, xd, y, s, n) testo(gg, xd - larg(n or 'medium', s), y, s, n) end
local function testo_c(gg, xc, y, s, n) testo(gg, xc - larg(n or 'medium', s) // 2, y, s, n) end
local function zona(x, y, w, h, fn) zone[#zone + 1] = { x = x, y = y, w = w, h = h, fn = fn } end
local function riga_doppia(gg, y) gg:line(M, y, W - M, y); gg:line(M, y + 1, W - M, y + 1) end

local function freccia(gg, x, y, dir, s)
  s = s or 9
  local d = dir == 'sx' and 1 or -1
  for k = 0, 1 do
    gg:line(x + d * s // 2 + k, y - s, x - d * s // 2 + k, y)
    gg:line(x - d * s // 2 + k, y, x + d * s // 2 + k, y + s)
  end
end

local function bottone(gg, x, y, w, h, s, forte, fn)
  gg:rect(x, y, w, h, { radius = h // 2, line_width = forte and 4 or 2 })
  testo_c(gg, x + w // 2, y + (h - lh('medium')) // 2, s, 'medium')
  if fn then zona(x, y, w, h, fn) end
end

local function casella(gg, x, y, s, piena)
  gg:rect(x, y, s, s, { radius = 4, line_width = 2 })
  if piena then
    for k = 0, 2 do
      gg:line(x + 5, y + s // 2 + k - 1, x + s * 2 // 5, y + s - 7 + k - 1)
      gg:line(x + s * 2 // 5, y + s - 7 + k - 1, x + s - 5, y + 6 + k - 1)
    end
  end
end

local function avanti_spuntato(gg, x, y)          -- il segno di spunta piccolo, accanto al titolo
  for k = 0, 1 do
    gg:line(x, y + 8 + k, x + 5, y + 13 + k)
    gg:line(x + 5, y + 13 + k, x + 14, y + 2 + k)
  end
end

local function indietro()
  scr = table.remove(da) or 'attrezzatura'
end

local function vai(s)
  da[#da + 1] = scr
  scr = s
end

local function intestazione(gg, titolo, destra, con_indietro)
  local x = M
  if con_indietro then
    freccia(gg, M + 8, 34, 'sx', 11)
    zona(0, 0, 64, 64, indietro)
    x = M + 32
  end
  local f = 'large'
  local maxw = W - M - x - (destra and larg('small', destra) + 12 or 0)
  if larg(f, titolo) > maxw then f = 'medium' end
  local righe = kit.wrap(titolo, f, maxw)
  if #righe > 2 then righe = { righe[1], kit.short(righe[2], 30) } end
  local y = #righe == 1 and (f == 'large' and 16 or 20) or 8
  for k, r in ipairs(righe) do testo(gg, x, y + (k - 1) * (lh(f) + 2), r, f) end
  if destra then testo_dx(gg, W - M, (#righe == 1 and 26 or 20), destra, 'small') end
  local fondo = math.max(60, y + #righe * (lh(f) + 2) + 6)
  riga_doppia(gg, fondo)
  return fondo + 2
end

-- la barra delle schede: un'immagine (la scheda attiva è una pillola nera con il testo bianco)
local function barra(gg)
  local attivo = scr
  if scr == 'voce' or scr == 'zaino' then attivo = 'attrezzatura' end
  if scr == 'tutorial' or scr == 'passo' then attivo = 'tecniche' end
  local img = immagine('tab_' .. attivo)
  if img then pcall(function() gg:image(img, 0, TAB_Y) end)
  else
    riga_doppia(gg, TAB_Y)
    testo_c(gg, 80, TAB_Y + 18, 'Attrezzatura', 'medium'); testo_c(gg, 240, TAB_Y + 18, 'Tecniche', 'medium'); testo_c(gg, 400, TAB_Y + 18, 'Percorso', 'medium')
  end
  local voci = { 'attrezzatura', 'tecniche', 'percorso' }
  for k, v in ipairs(voci) do zona((k - 1) * 160, TAB_Y, 160, H - TAB_Y, function() scr, da = v, {} end) end
end

local function paginatore(gg, y, pag, pagine, cambia)
  if pagine <= 1 then return end
  testo_c(gg, W // 2, y + 6, pag .. ' / ' .. pagine, 'medium')
  if pag > 1 then freccia(gg, M + 14, y + 18, 'sx', 10) end
  if pag < pagine then freccia(gg, W - M - 14, y + 18, 'dx', 10) end
  zona(0, y - 4, W // 3, 44, function() cambia(-1) end)
  zona(2 * W // 3, y - 4, W // 3, 44, function() cambia(1) end)
end

-- un testo lungo a capo, fino a y_max; restituisce la y finale
local function paragrafo(gg, x, y, s, f, maxw, y_max, spazio)
  for _k, l in ipairs(kit.wrap(s, f, maxw)) do
    if y + lh(f) > y_max then break end
    testo(gg, x, y, l, f)
    y = y + lh(f) + (spazio or 4)
  end
  return y
end

local function disegno(gg, nome, x, y)
  local img = immagine(nome)
  if img then pcall(function() gg:image(img, x, y) end); return true end
  return false
end

-- un bottone pieno (immagine nera con il testo bianco), o in mancanza un bottone a bordo spesso
local function bottone_pieno(gg, nome, x, y, w, testo_alt, fn)
  if not disegno(gg, nome, x, y) then bottone(gg, x, y, w, BTN_H, testo_alt, true, nil) end
  zona(x, y, w, BTN_H, fn)
end

-- l'etichetta di un gruppo: maiuscolo piccolo a sinistra, un conteggio a destra, una riga spessa sotto
local function etichetta(gg, y, s, destra)
  testo(gg, M, y + 6, s, 'small')
  if destra then testo_dx(gg, W - M, y + 6, destra, 'small') end
  gg:rect(M, y + 30, W - 2 * M, 3, { fill = true })
  return y + 34
end

-- un riquadro tratteggiato (i consigli, le cose in arrivo)
local function tratteggio(gg, x, y, w, h)
  local passo_t, pieno = 10, 6
  for xx = x, x + w - pieno, passo_t do
    gg:line(xx, y, xx + pieno, y); gg:line(xx, y + 1, xx + pieno, y + 1)
    gg:line(xx, y + h, xx + pieno, y + h); gg:line(xx, y + h - 1, xx + pieno, y + h - 1)
  end
  for yy = y, y + h - pieno, passo_t do
    gg:line(x, yy, x, yy + pieno); gg:line(x + 1, yy, x + 1, yy + pieno)
    gg:line(x + w, yy, x + w, yy + pieno); gg:line(x + w - 1, yy, x + w - 1, yy + pieno)
  end
end

local function maiuscolo(s) return (s:gsub('^(%d+)%. ', '%1 · ')):upper() end

local function peso(g)
  if not g then return nil end
  if g >= 1000 then return (string.format('%.1f', g / 1000):gsub('%.', ',')) .. ' kg' end
  return g .. ' g'
end

------------------------------------------------------------------ Attrezzatura

local function conta(gr)
  local tot, ok = 0, 0
  for _g, g in ipairs(gr and { gr } or ATTR) do
    if gr or not g.facoltativo then
      for _k, v in ipairs(g.voci) do tot = tot + 1; if spunte[v.id] then ok = ok + 1 end end
    end
  end
  return ok, tot
end

local BANNER_H = 156
local RIGA_TIT, RIGA_VOCE = 40, 64

local function righe_attr()
  local out = { { tipo = 'zaino' } }
  for gi, gr in ipairs(ATTR) do
    out[#out + 1] = { tipo = 'gruppo', g = gi }
    for vi, _v in ipairs(gr.voci) do out[#out + 1] = { tipo = 'voce', g = gi, v = vi } end
  end
  out[#out + 1] = { tipo = 'azzera' }
  return out
end

local function alt_riga(r) return r.tipo == 'voce' and RIGA_VOCE or r.tipo == 'zaino' and 70 or r.tipo == 'azzera' and 60 or RIGA_TIT end

-- la prima pagina ha l'intestazione illustrata; ogni pagina al massimo 5 icone (con la barra: 7 immagini)
local function pagine_attr()
  local righe = righe_attr()
  local pagine = { 1 }
  local function alto(p) return TAB_Y - 46 - (p == 1 and (BANNER_H + 44) or 104) end
  local h, icone = 0, 0
  for k, r in ipairs(righe) do
    local a = alt_riga(r)
    local serve = a + (r.tipo == 'gruppo' and RIGA_VOCE or 0)       -- un titolo non resta da solo in fondo
    if (h + serve > alto(#pagine) or (r.tipo == 'voce' and icone >= 5)) and k > pagine[#pagine] then
      pagine[#pagine + 1] = k; h, icone = 0, 0
    end
    h = h + a
    if r.tipo == 'voce' then icone = icone + 1 end
  end
  return righe, pagine
end

local function avanzamento(gg, y, ok, tot)
  local s = ok .. ' / ' .. tot .. ' nello zaino'
  testo(gg, M, y, s, 'medium')
  local x0 = M + larg('medium', s) + 14
  local w = W - M - x0
  gg:rect(x0, y + 6, w, 14, { line_width = 2 })
  local cella = (w - 4) / tot
  for k = 1, ok do
    gg:rect(math.floor(x0 + 2 + (k - 1) * cella) + 1, y + 9, math.max(2, math.floor(cella) - 2), 8, { fill = true })
  end
end

local function disegna_attrezzatura(gg)
  local ok, tot = conta()
  local righe, pagine = pagine_attr()
  if pag_attr > #pagine then pag_attr = #pagine end
  per_pag_attr = #pagine
  local y
  if pag_attr == 1 and disegno(gg, 'banner_attrezzatura', 0, 0) then
    y = BANNER_H + 8
  else
    testo(gg, M, 16, 'Attrezzatura', 'large')
    y = 60
    gg:rect(M, y, W - 2 * M, 3, { fill = true })
    y = y + 10
  end
  avanzamento(gg, y, ok, tot)
  y = y + 36
  local fine = (pagine[pag_attr + 1] or (#righe + 1)) - 1
  for k = pagine[pag_attr], fine do
    local r = righe[k]
    if r.tipo == 'zaino' then
      gg:rect(M, y + 4, W - 2 * M, 60, { radius = 12, line_width = 2 })
      testo(gg, M + 16, y + 10, 'Come caricare lo zaino', 'medium')
      testo(gg, M + 16, y + 36, 'Il fondo, il centro, la cima: perché', 'small')
      freccia(gg, W - M - 20, y + 34, 'dx', 8)
      zona(M, y + 4, W - 2 * M, 60, function() vai('zaino') end)
    elseif r.tipo == 'gruppo' then
      local gr = ATTR[r.g]
      local o, t = conta(gr)
      etichetta(gg, y, maiuscolo(gr.titolo), o .. ' / ' .. t)
    elseif r.tipo == 'voce' then
      local v = ATTR[r.g].voci[r.v]
      local on = spunte[v.id]
      disegno(gg, 'ico_' .. v.id, M, y + 12)
      testo(gg, M + 54, y + 8, v.nome, 'medium')
      local sotto = v.dove .. (v.peso and (' · ' .. peso(v.peso)) or '')
      while #sotto > 1 and larg('small', sotto) > W - 2 * M - 54 - 44 do sotto = kit.drop_last(sotto) end
      testo(gg, M + 54, y + 34, sotto, 'small')
      casella(gg, W - M - 32, y + 16, 32, on)
      gg:line(M, y + RIGA_VOCE - 1, W - M, y + RIGA_VOCE - 1)
      zona(0, y, W - M - 56, RIGA_VOCE, function() voce = r; vai('voce') end)
      zona(W - M - 56, y, 56 + M, RIGA_VOCE, function() spunte[v.id] = not on or nil; salva() end)
    elseif r.tipo == 'azzera' then
      bottone(gg, M + 70, y + 12, W - 2 * M - 140, 44, 'Togli tutte le spunte', false, function()
        kit.show({ title = 'Togliere tutte le spunte?', lines = { 'Per preparare lo zaino del prossimo giro.' },
                   rows = { { { 'Togli', function() spunte = {}; salva(); kit.close() end },
                              { 'Annulla', function() kit.close() end } } },
                   cancel = function() kit.close() end })
      end)
    end
    y = y + alt_riga(r)
  end
  paginatore(gg, TAB_Y - 42, pag_attr, #pagine, function(d) pag_attr = math.max(1, math.min(#pagine, pag_attr + d)) end)
  barra(gg)
end

local function testata_dettaglio(gg, sopra, titolo, icona)
  freccia(gg, M + 6, 26, 'sx', 9)
  testo(gg, M + 24, 16, sopra, 'small')
  zona(0, 0, W, 50, indietro)
  local y = 52
  local x = M
  if icona then
    gg:rect(M, y, 64, 64, { radius = 12, line_width = 2 })
    disegno(gg, icona, M + 12, y + 12)
    x = M + 80
  end
  local righe = kit.wrap(titolo, 'large', W - M - x)
  local ty = icona and (y + (64 - #righe * (lh('large') + 2)) // 2) or y
  for k = 1, math.min(2, #righe) do testo(gg, x, ty + (k - 1) * (lh('large') + 2), righe[k], 'large') end
  y = math.max(icona and (y + 64) or 0, ty + math.min(2, #righe) * (lh('large') + 2)) + 12
  gg:rect(M, y, W - 2 * M, 3, { fill = true })
  return y + 3
end

local function disegna_voce(gg)
  local gr = ATTR[voce.g]
  local v = gr.voci[voce.v]
  local y = testata_dettaglio(gg, maiuscolo(gr.titolo), v.nome, 'ico_' .. v.id)
  local fondo = BTN_Y - 16
  -- la scheda: dove e peso
  local function riga_scheda(etich, valore)
    testo(gg, M, y + 12, etich, 'small')
    local yy = y + 8
    for _k, l in ipairs(kit.wrap(valore, 'medium', W - 2 * M - 96)) do testo(gg, M + 96, yy, l, 'medium'); yy = yy + lh('medium') + 2 end
    y = math.max(y + 44, yy + 8)
    gg:line(M, y, W - M, y)
  end
  riga_scheda('DOVE', v.dove)
  if v.peso then riga_scheda('PESO', 'circa ' .. peso(v.peso)) end
  y = y + 16
  testo(gg, M, y, 'PERCHÉ', 'small')
  y = paragrafo(gg, M, y + lh('small') + 4, v.perche, 'medium', W - 2 * M, fondo, 6) + 14
  -- il consiglio nel riquadro tratteggiato
  local righe = kit.wrap(v.consiglio, 'medium', W - 2 * M - 30)
  local alt = lh('medium') + 6
  local max = (fondo - y - 50) // alt
  if #righe > max then righe = kit.wrap(v.consiglio, 'small', W - 2 * M - 30); alt = lh('small') + 4; max = (fondo - y - 50) // alt end
  local n = math.min(#righe, math.max(0, max))
  local h = 44 + n * alt
  tratteggio(gg, M, y, W - 2 * M, h)
  testo(gg, M + 15, y + 12, 'CONSIGLIO', 'small')
  for k = 1, n do testo(gg, M + 15, y + 38 + (k - 1) * alt, righe[k], alt > lh('small') + 4 and 'medium' or 'small') end
  local on = spunte[v.id]
  if on then
    bottone(gg, M, BTN_Y, W - 2 * M, BTN_H, 'Nello zaino · tocca per togliere', false, function() spunte[v.id] = nil; salva() end)
  else
    bottone_pieno(gg, 'btn_zaino', M, BTN_Y, W - 2 * M, 'Metti nello zaino', function() spunte[v.id] = true; salva(); indietro() end)
  end
end

local function disegna_zaino(gg)
  local y = testata_dettaglio(gg, 'ATTREZZATURA', 'Come caricare lo zaino')
  y = y + 10
  local x0 = (W - IMG_W) // 2
  if disegno(gg, 'zaino', x0, y) then
    testo(gg, x0 + 276, y + 40, 'Cima', 'medium')
    testo(gg, x0 + 276, y + 62, 'pioggia, freddo', 'small')
    testo(gg, x0 + 276, y + 110, 'Centro', 'medium')
    testo(gg, x0 + 276, y + 132, 'il peso, vicino', 'small')
    testo(gg, x0 + 276, y + 150, 'alla schiena', 'small')
    testo(gg, x0 + 276, y + 186, 'Fondo', 'medium')
    testo(gg, x0 + 276, y + 208, 'la notte', 'small')
  end
  y = y + IMG_H + 14
  local regole = {
    { 'FONDO', "Il leggero che serve solo la sera: sacco a pelo e vestiti asciutti." },
    { 'CENTRO', "Contro la schiena, all'altezza delle scapole: cibo, acqua, fornello, tenda." },
    { 'CIMA', "Quello che serve all'improvviso: antipioggia, pile, frontale." },
    { 'CINTURA', "Prima sui fianchi, poi gli spallacci: il peso va sulle gambe." },
  }
  for _k, r in ipairs(regole) do
    testo(gg, M, y + 2, r[1], 'small')
    local yy = paragrafo(gg, M + 96, y, r[2], 'small', W - 2 * M - 96, H - 10, 2)
    y = yy + 8
    gg:line(M, y - 4, W - M, y - 4)
  end
end

------------------------------------------------------------------ Tecniche

local function di_sezione()
  local out = {}
  for k, t in ipairs(TEC) do if t.sez == sez then out[#out + 1] = k end end
  return out
end

local TEC_RIGA = 88
local SEZ_W = { 124, 94, 118, 104 } -- le larghezze delle sezioni nell'immagine sez_* (tools/grafica.py)

local function meta_tut(t)
  return t.n .. ' passi · ' .. LIVELLI[t.livello] .. (t.tempo ~= '' and (' · ' .. t.tempo) or '')
end

local function disegna_tecniche(gg)
  local lista = di_sezione()
  local y = 0
  if disegno(gg, 'banner_tecniche', 0, 0) then y = BANNER_H + 6
  else testo(gg, M, 16, 'Tecniche', 'large'); y = 64 end
  if not disegno(gg, 'sez_' .. sez, M, y) then
    for k, x in ipairs(SEZIONI) do testo(gg, M + (k - 1) * 110, y + 12, (sez == x[1] and '> ' or '') .. x[2], 'small') end
  end
  local x = M
  for k, sx in ipairs(SEZIONI) do
    zona(x, y, SEZ_W[k], 50, function() sez = sx[1]; pag_tec = 1; salva() end)
    x = x + SEZ_W[k]
  end
  y = y + 58
  if sez == 'trappole' then
    tratteggio(gg, M, y, W - 2 * M, 52)
    paragrafo(gg, M + 14, y + 6, "Vietate in Italia: solo per una vera emergenza. Leggi prima 'la legge'.", 'small', W - 2 * M - 28, y + 50, 2)
    y = y + 60
  end
  local per_pag = math.min(4, (TAB_Y - 46 - y) // TEC_RIGA)
  local pagine = math.max(1, (#lista + per_pag - 1) // per_pag)
  if pag_tec > pagine then pag_tec = pagine end
  for j = 1, per_pag do
    local k = lista[(pag_tec - 1) * per_pag + j]
    if not k then break end
    local t = TEC[k]
    local ry = y + (j - 1) * TEC_RIGA
    gg:rect(M, ry + 10, 92, 68, { line_width = 2 })
    disegno(gg, 'th_' .. t.id, M + 2, ry + 12)
    local tx = M + 108
    local tit = kit.wrap(t.titolo, 'medium', W - M - tx - 30)
    local ty = ry + (#tit > 1 and 12 or 20)
    for i = 1, math.min(2, #tit) do testo(gg, tx, ty, tit[i], 'medium'); ty = ty + lh('medium') end
    testo(gg, tx, ty + 4, meta_tut(t), 'small')
    if fatti[t.id] then avanti_spuntato(gg, W - M - 16, ry + 36) else freccia(gg, W - M - 8, ry + TEC_RIGA // 2, 'dx', 7) end
    gg:line(M, ry + TEC_RIGA - 1, W - M, ry + TEC_RIGA - 1)
    zona(M, ry, W - 2 * M, TEC_RIGA, function() tut = k; pag_intro = 1; vai('tutorial') end)
  end
  paginatore(gg, TAB_Y - 42, pag_tec, pagine, function(d) pag_tec = math.max(1, math.min(pagine, pag_tec + d)) end)
  barra(gg)
end

local function inizia()
  passo = 1
  vai('passo')
end

local function disegna_tutorial(gg)
  local t = TUT(tut)
  local nome_sez = ''
  for _k, x in ipairs(SEZIONI) do if x[1] == t.sez then nome_sez = x[2]:upper() end end
  local y = testata_dettaglio(gg, nome_sez, t.titolo)
  local fondo = BTN_Y - 14
  y = y + 10
  testo(gg, M, y, meta_tut(t), 'small')
  y = y + lh('small') + 10
  y = paragrafo(gg, M, y, t.intro, 'medium', W - 2 * M, fondo, 6) + 8
  if t.serve then
    testo(gg, M, y, 'TI SERVE', 'small')
    y = paragrafo(gg, M, y + lh('small') + 2, t.serve, 'small', W - 2 * M, fondo, 2) + 10
  end
  if t.attenzione then
    local righe = kit.wrap(t.attenzione, 'small', W - 2 * M - 28)
    local h = #righe * (lh('small') + 2) + 40
    gg:rect(M, y, W - 2 * M, h, { radius = 10, line_width = 3 })
    testo(gg, M + 14, y + 8, 'ATTENZIONE', 'small')
    local yy = y + 10 + lh('small')
    for _k, l in ipairs(righe) do testo(gg, M + 14, yy, l, 'small'); yy = yy + lh('small') + 2 end
    y = y + h + 12
  end
  if y + 40 < fondo then y = etichetta(gg, y, 'I PASSI', tostring(#t.passi)) + 6 end
  for k, p in ipairs(t.passi) do
    if y + lh('small') > fondo then break end
    testo(gg, M, y, string.format('%02d', k), 'small')
    testo(gg, M + 34, y, kit.short(p[1], 40), 'small')
    zona(M, y - 2, W - 2 * M, lh('small') + 4, function() passo = k; vai('passo') end)
    y = y + lh('small') + 4
  end
  bottone_pieno(gg, 'btn_inizia', M, BTN_Y, W - 2 * M, fatti[t.id] and 'Rivedi i passi' or 'Inizia', inizia)
end

local function disegna_passo(gg)
  local t = TUT(tut)
  local n = #t.passi
  local p = t.passi[passo]
  freccia(gg, M + 6, 26, 'sx', 9)
  testo(gg, M + 24, 16, kit.short(t.titolo:upper(), 34), 'small')
  testo_dx(gg, W - M, 16, passo .. ' / ' .. n, 'small')
  zona(0, 0, W - 80, 50, indietro)
  local gap = n > 12 and 3 or 5
  local sw = (W - 2 * M - (n - 1) * gap) / n
  local y = 50
  for k = 1, n do
    local x = math.floor(M + (k - 1) * (sw + gap))
    if k <= passo then gg:rect(x, y, math.floor(sw), 8, { fill = true }) else gg:rect(x, y + 2, math.floor(sw), 6, { line_width = 2 }) end
  end
  y = y + 20
  if p.img and disegno(gg, p.img, (W - IMG_W) // 2, y) then
    gg:rect((W - IMG_W) // 2 - 2, y - 2, IMG_W + 4, IMG_H + 4, { radius = 10, line_width = 2 })
    y = y + IMG_H + 14
  end
  -- il numero grande e il titolo
  local tx = M
  if n <= 15 and disegno(gg, 'num_' .. passo, M, y) then tx = M + 116 end
  local tit = kit.wrap(p[1], 'large', W - M - tx)
  local hn = tx > M and 64 or 0
  local ty = y + math.max(0, hn - #tit * (lh('large') + 2) - 2)
  if #tit > 2 then tit = { tit[1], kit.short(tit[2], 22) } end
  for k = 1, #tit do testo(gg, tx, ty + (k - 1) * (lh('large') + 2), tit[k], 'large') end
  y = math.max(y + hn, ty + #tit * (lh('large') + 2)) + 12
  local fondo = BTN_Y - 12
  local f, corpo, alt
  for _k, prova in ipairs({ { 'large', 6 }, { 'medium', 8 }, { 'medium', 2 }, { 'small', 3 }, { 'small', 1 } }) do
    f = prova[1]
    corpo = kit.wrap(p[2], f, W - 2 * M)
    alt = lh(f) + prova[2]
    if #corpo * alt <= fondo - y then break end
  end
  for k = 1, #corpo do
    if y + lh(f) > fondo then break end
    testo(gg, M, y, corpo[k], f); y = y + alt
  end
  bottone(gg, M, BTN_Y, 150, BTN_H, 'Indietro', false, function()
    if passo > 1 then passo = passo - 1 else indietro() end
  end)
  if passo < n then
    bottone_pieno(gg, 'btn_avanti', M + 162, BTN_Y, W - 2 * M - 162, 'Avanti', function() passo = passo + 1 end)
  else
    bottone_pieno(gg, 'btn_fine', M + 162, BTN_Y, W - 2 * M - 162, 'Fine', function() fatti[t.id] = true; salva(); indietro() end)
  end
end

------------------------------------------------------------------ Percorso

local function disegna_percorso(gg)
  local y
  if disegno(gg, 'banner_percorso', 0, 0) then y = BANNER_H + 8
  else testo(gg, M, 16, 'Percorso', 'large'); y = 64 end
  local fatte = 0
  for _k, v in ipairs(PRIMA) do if prima[v[1]] then fatte = fatte + 1 end end
  y = etichetta(gg, y, 'PRIMA DI PARTIRE', fatte .. ' / ' .. #PRIMA) + 4
  for k, v in ipairs(PRIMA) do
    local on = prima[v[1]]
    local righe = kit.wrap(v[2], 'medium', W - 2 * M - 50)
    local h = math.max(56, math.min(2, #righe) * (lh('medium') + 2) + 14)
    casella(gg, M, y + (h - 32) // 2, 32, on)
    local yy = y + (h - math.min(2, #righe) * (lh('medium') + 2)) // 2
    for i = 1, math.min(2, #righe) do testo(gg, M + 50, yy, righe[i], 'medium'); yy = yy + lh('medium') + 2 end
    if k < #PRIMA then gg:line(M, y + h - 1, W - M, y + h - 1) end
    zona(0, y, W, h, function() prima[v[1]] = not on or nil; salva() end)
    y = y + h
  end
  y = y + 12
  local h = math.min(90, TAB_Y - 14 - y)
  if h > 40 then
    tratteggio(gg, M, y, W - 2 * M, h)
    testo(gg, M + 16, y + 10, 'LA MAPPA · IN ARRIVO', 'small')
    if h > 70 then paragrafo(gg, M + 16, y + 36, 'La progettiamo insieme nel prossimo passo.', 'small', W - 2 * M - 32, y + h - 4, 2) end
  end
  barra(gg)
end

------------------------------------------------------------------ callback

local DISEGNA = { attrezzatura = disegna_attrezzatura, voce = disegna_voce, zaino = disegna_zaino,
                  tecniche = disegna_tecniche, tutorial = disegna_tutorial, passo = disegna_passo,
                  percorso = disegna_percorso }

local seguito = nil
function on_draw(c, gg)
  zone = {}
  decodificate, rimandate = 0, false
  local ok, e = pcall(function()
    gg:clear(0)
    DISEGNA[scr](gg)
    if kit.modal() then kit.draw_modal(gg) end
  end)
  if not ok then ERR = tostring(e) end
  if ERR then
    for k, l in ipairs(kit.wrap('ERR ' .. ERR, 'small', W - 2 * M)) do gg:text(M, 600 + (k - 1) * 22, l, { font = kit.font('small') }) end
  end
  if rimandate and not seguito then
    -- le immagini rimandate: un altro fotogramma tra poco
    pcall(function() seguito = ctx:after(80, function() seguito = nil; kit.refresh() end) end)
  end
end

local function tocca(x, y)
  if kit.modal() then kit.tap_modal(x, y); return end
  for k = #zone, 1, -1 do
    local z = zone[k]
    if x >= z.x and x < z.x + z.w and y >= z.y and y < z.y + z.h then z.fn(); return end
  end
end

local function sfoglia(d)
  if scr == 'passo' then
    local n = TEC[tut].n
    if d > 0 and passo < n then passo = passo + 1 elseif d < 0 and passo > 1 then passo = passo - 1 end
  elseif scr == 'attrezzatura' then
    pag_attr = math.max(1, math.min(per_pag_attr, pag_attr + d))
  elseif scr == 'tecniche' then
    pag_tec = math.max(1, pag_tec + d)
  end
end

function on_input(c, ev)
  if type(ev) ~= 'table' then return false end
  local preso = false
  local prima_scr = scr
  local ok, e = pcall(function()
    if ev.type == 'pointer' or ev.type == 'touch' then
      preso = true
      if ev.phase and ev.phase ~= 'up' then return end
      local g = ev.gesture
      if g == 'swipe_left' and not kit.modal() then sfoglia(1)
      elseif g == 'swipe_right' and not kit.modal() then sfoglia(-1)
      elseif g == 'tap' or g == 'long' or g == nil then tocca(ev.x or 0, ev.y or 0) end
    elseif ev.type == 'key' then
      if not kit.key_accepted(ev) then preso = true; return end
      local k = ev.key
      if kit.modal() then
        if k == 'back' then kit.close(); preso = true end
        return
      end
      if k == 'back' then
        if #da > 0 then indietro(); preso = true
        elseif scr ~= 'attrezzatura' then scr = 'attrezzatura'; preso = true end
      elseif k == 'right' or k == 'down' then sfoglia(1); preso = true
      elseif k == 'left' or k == 'up' then sfoglia(-1); preso = true end
    end
  end)
  if not ok then ERR = tostring(e) end
  if prima_scr ~= scr then
    pcall(function() ctx.gc:collect() end)      -- cambio di schermata: libera subito la memoria della precedente
    pcall(function() ctx:invalidate('full') end)
  else
    kit.refresh()
  end
  return preso
end

function on_load(c)
  local ok, e = pcall(carica)
  if not ok then ERR = tostring(e) end
end

function on_leave() pcall(salva) end

if rawget(_G, '__TEST') then
  __TEST.api = {
    scr = function() return scr end, passo = function() return passo end, tut = function() return tut end,
    spunte = function() return spunte end, fatti = function() return fatti end, prima = function() return prima end,
    tecniche = TEC, tutorial = TUT, attrezzatura = ATTR, err = function() return ERR end,
    apri = function(k) tut = k; da = { 'tecniche' }; scr = 'tutorial' end,
    set = function(s) scr = s end, conta = conta,
  }
end
