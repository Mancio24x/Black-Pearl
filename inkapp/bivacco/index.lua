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
-- GENERATO da tools/make_art.py: il numero di riga di ogni immagine in data/img.txt
return {
  zaino = 1,
  fuoco1 = 2,
  fuoco2 = 3,
  fuoco3 = 4,
  fuoco4 = 5,
  fuoco5 = 6,
  fuoco6 = 7,
  piuma1 = 8,
  piuma2 = 9,
  piuma3 = 10,
  piuma4 = 11,
  tarp1 = 12,
  tarp2 = 13,
  tarp3 = 14,
  tarp4 = 15,
  tarp5 = 16,
  nord1 = 17,
  nord2 = 18,
  nord3 = 19,
  nord4 = 20,
  otto1 = 21,
  otto2 = 22,
  otto3 = 23,
  otto4 = 24,
  gassa1 = 25,
  gassa2 = 26,
  gassa3 = 27,
  gassa4 = 28,
  gassa5 = 29,
  quattro1 = 30,
  quattro2 = 31,
  quattro3 = 32,
  quattro4 = 33,
  quattro5 = 34,
}
end

__mods['tecniche'] = function()
-- Le tecniche, in tre sezioni. Ogni tutorial:
--   id, titolo, sez ('sopravvivenza', 'nodi', 'trappole'), livello (1-3), tempo
--   intro       a cosa serve
--   serve       cosa ti serve (facoltativo)
--   attenzione  l'avviso di sicurezza o di legge (facoltativo)
--   passi       { titolo, testo, img = 'nome' } ; img è il disegno del passo (tools/disegni.py)
-- Testi solo in Latin-1: niente trattini lunghi né virgolette curve.

return {

------------------------------------------------------------------ sopravvivenza

{ id = 'stop', titolo = 'Ti sei perso: S.T.O.P.', sez = 'sopravvivenza', livello = 1, tempo = '5 min',
  intro = "La prima cosa da fare quando qualcosa va storto: fermarsi. La maggior parte degli incidenti peggiora perché si continua a camminare a caso.",
  passi = {
    { "S: fermati", "Siediti, bevi un sorso, mangia qualcosa. Non fare un passo finché non hai un piano. Il panico fa camminare veloce nella direzione sbagliata." },
    { "T: pensa", "Respira. Quando hai visto l'ultimo segnale del sentiero? Quante ore di luce restano? Che tempo fa? Qualcuno sa dove sei e quando rientri?" },
    { "O: osserva", "Guardati intorno: tracce, segnavia, rumore di un torrente o di una strada. Controlla la carta. Il telefono: chiama il 112 se serve, poi mettilo in modalità aereo per risparmiare batteria." },
    { "P: pianifica", "Se ritrovi il sentiero con certezza, torna indietro sulle tue tracce. Se no, o se sta per fare buio o c'è nebbia, resta dove sei: chi ti cerca parte dall'ultimo punto noto." },
    { "Le priorità: la regola del 3", "Si resiste circa 3 ore al freddo senza riparo, 3 giorni senza acqua, 3 settimane senza cibo. Quindi, in ordine: proteggiti dal freddo e dal vento, fatti trovare, poi acqua, poi fuoco. Il cibo viene per ultimo." },
    { "Fatti trovare", "Mettiti in un punto aperto e visibile, stendi qualcosa di colorato. Il segnale di soccorso alpino: 6 fischi (o lampi di luce) in un minuto, poi un minuto di pausa, e ripeti. All'elicottero: braccia alzate a Y vuol dire 'sì, serve aiuto'." },
  } },

{ id = 'fuoco', titolo = 'Accendere il fuoco', sez = 'sopravvivenza', livello = 2, tempo = '30 min',
  intro = "Un fuoco a tepee (a cono), il più facile da accendere anche con legna non perfetta. Il segreto non è l'accensione: è la preparazione.",
  serve = "Acciarino o accendino, coltello, legna secca in tre misure.",
  attenzione = "In molte regioni accendere fuochi nei boschi è vietato, soprattutto d'estate. Usa il fornelletto: il fuoco solo nelle aree attrezzate o in una vera emergenza. Mai sotto gli alberi o col vento forte.",
  passi = {
    { "Scegli il posto", "Terra nuda, a 3 metri da tenda, alberi, rocce strapiombanti e erba secca. Niente rami sopra. Riparato dal vento. Pulisci un cerchio di un metro fino alla terra e, se ci sono, metti intorno delle pietre asciutte (mai prese da un torrente: con il calore possono scoppiare).", img = 'fuoco1' },
    { "Prepara tre mucchi", "Prima di accendere raccogli tutto. Esca: un pugno di materiale finissimo e secco. Legnetti: rametti dallo spessore di un fiammifero a quello di una matita. Legna: dal pollice al polso. Raccogline il doppio di quello che pensi. Il legno buono è morto e ancora attaccato all'albero: si spezza con un crack secco.", img = 'fuoco2' },
    { "La base e il nido", "Se il terreno è umido fai una base di rametti secchi affiancati. Sopra metti l'esca a forma di nido: erba secca, corteccia di betulla, aghi di pino secchi, i riccioli di un bastoncino piumato (vedi il tutorial).", img = 'fuoco3' },
    { "Il tepee", "Appoggia i legnetti più sottili intorno al nido, a cono, punte che si toccano in alto. Lascia una porta dalla parte da cui arriva il vento, per arrivare all'esca. Poi un secondo giro di legnetti più grossi.", img = 'fuoco4' },
    { "Accendi", "Con l'acciarino: appoggia la punta della barra dentro il nido, tieni il raschietto fermo e tira indietro la barra con forza: le scintille cadono sull'esca senza disfare il nido. Quando l'esca fuma soffia piano, alla base, finché fiammeggia.", img = 'fuoco5' },
    { "Alimenta e spegni", "Aggiungi legna sempre un po' più grossa, senza soffocare la fiamma: il fuoco ha bisogno di aria. Per spegnere: acqua, mescola le braci, ancora acqua, finché la cenere è fredda al tatto. Poi cancella ogni traccia.", img = 'fuoco6' },
  } },

{ id = 'piuma', titolo = 'Il bastoncino piumato', sez = 'sopravvivenza', livello = 2, tempo = '15 min',
  intro = "Quando tutto è bagnato, l'interno del legno morto è ancora asciutto. Con il coltello lo trasformi in riccioli sottili che prendono fuoco con una scintilla.",
  serve = "Un coltello a lama fissa, un ramo secco morto spesso come il polso.",
  attenzione = "Taglia sempre lontano dal corpo e mai verso le gambe. Vedi anche Coltello e intaglio.",
  passi = {
    { "Trova il cuore asciutto", "Prendi un ramo morto ancora sull'albero (non da terra). Spaccalo per il lungo: appoggia la lama sopra e batti il dorso con un altro legno. Il legno chiaro e asciutto dentro è quello che serve.", img = 'piuma1' },
    { "La presa giusta", "Appoggia una punta del legno su un ceppo o su un sasso, un po' inclinato. Tieni il coltello con tutto il pugno, il braccio teso, la lama che lavora verso il basso e lontano da te.", img = 'piuma2' },
    { "I riccioli", "Appoggia la lama quasi piatta sul legno e spingi giù con un movimento lungo e lento, togliendo una striscia sottilissima. Fermati prima della fine: il ricciolo deve restare attaccato. Ruota un poco il legno e ripeti.", img = 'piuma3' },
    { "Il risultato", "Alla fine hai un ciuffo di riccioli in fondo al bastoncino. Raschia con il dorso della lama anche un po' di polvere fine di legno: con l'acciarino si accende subito. Usane due o tre nel nido del fuoco.", img = 'piuma4' },
  } },

{ id = 'coltello', titolo = 'Coltello e intaglio: il picchetto', sez = 'sopravvivenza', livello = 2, tempo = '20 min',
  intro = "Le regole per lavorare il legno senza farsi male, e il primo oggetto utile: un picchetto per la tenda o il tarp.",
  serve = "Un coltello a lama fissa, un ramo duro e dritto spesso come il pollice.",
  attenzione = "Un taglio nell'interno coscia può raggiungere l'arteria femorale: lì non si taglia mai.",
  passi = {
    { "Il triangolo di sangue", "Siediti: l'area tra le ginocchia e l'inguine è il triangolo di sangue. Non tagliare mai in quella direzione. Lavora con i gomiti sulle ginocchia e il legno davanti, fuori dalle gambe." },
    { "Lo spazio intorno", "Tieni il braccio teso e ruota: nessuno deve essere a portata. Quando non usi il coltello, rimettilo nel fodero, non piantarlo nel terreno." },
    { "Il legno", "Taglia un pezzo lungo 25 cm da un ramo duro: nocciolo, faggio, frassino. Togli i rametti con colpi verso l'esterno." },
    { "La punta", "Appoggia il legno su un ceppo e fai la punta con tagli lunghi verso il basso, girando il pezzo: quattro facce, come una matita. Non troppo sottile, o si rompe." },
    { "La tacca per la corda", "A 3 cm dalla testa fai un taglio dritto di traverso, profondo un terzo. Poi un taglio inclinato dal basso che arriva al primo: salta via un cuneo. La corda si aggancia lì e non scivola." },
    { "La testa", "Smussa il bordo della testa con piccoli tagli tutto intorno: quando lo batti con un sasso non si scheggia. Pianta il picchetto inclinato all'indietro, a 45 gradi rispetto alla corda." },
  } },

{ id = 'tarp', titolo = 'Riparo con il tarp', sez = 'sopravvivenza', livello = 1, tempo = '20 min',
  intro = "Il riparo a tettoia: un telo, una corda e quattro picchetti. Ripara da pioggia e vento, e davanti puoi fare il fuoco che ti scalda.",
  serve = "Tarp, 10 m di corda, 4-6 picchetti, due alberi a 3-4 metri.",
  passi = {
    { "Scegli il posto", "Due alberi a 3-4 metri, terreno piano o in leggera pendenza, mai in una conca dove si ferma l'acqua. Guarda in alto: niente rami secchi sopra. Da dove arriva il vento? Il lato chiuso del riparo andrà da quella parte.", img = 'tarp1' },
    { "Tendi la dorsale", "Lega la corda al primo albero con una gassa d'amante, a circa un metro di altezza, e all'altro con un tendicavo, così la tendi bene. Più bassa è, meglio ripara dal vento.", img = 'tarp2' },
    { "Appendi il tarp", "Appoggia il tarp sulla corda, con un lato lungo che scende fino a terra e l'altro più corto. Lega i due angoli alti alla corda, ben tesi.", img = 'tarp3' },
    { "Picchetta il lato al vento", "Il lato lungo va a terra dalla parte del vento: picchetta prima gli angoli, poi il centro, tirando il telo. I picchetti inclinati all'indietro.", img = 'tarp4' },
    { "Controlla e sistemati", "Il telo deve essere teso senza pieghe dove si ferma l'acqua. Sotto: il materassino, o uno strato spesso di frasche e foglie secche. Il fuoco eventualmente davanti, a 2 metri, mai sotto il telo.", img = 'tarp5' },
  } },

{ id = 'capanna', titolo = 'Capanna di frasche', sez = 'sopravvivenza', livello = 3, tempo = '2-3 ore',
  intro = "Senza tenda né telo: un riparo fatto solo di rami e foglie, che isola come un sacco a pelo. Costa fatica: comincia molto prima che faccia buio.",
  serve = "Un ramo lungo e robusto, molti rami, tantissime foglie secche.",
  passi = {
    { "La trave", "Trova un ramo robusto lungo una volta e mezza la tua altezza. Appoggia un capo su un ceppo o in una forcella all'altezza della vita, l'altro a terra. Sdraiati sotto: devi entrarci con un palmo di spazio, non di più. Piccola è calda." },
    { "Le costole", "Appoggia ai due lati della trave tanti rami, fitti, che formano una A. Non devono sporgere molto sopra la trave." },
    { "Il graticcio", "Intreccia rami sottili e frasche di traverso sulle costole: servono a tenere su le foglie." },
    { "La copertura", "Ammucchia sopra foglie secche, aghi di pino, felci: almeno un braccio di spessore. È lo spessore che scalda e tiene fuori la pioggia. Sopra qualche ramo perché il vento non la porti via." },
    { "Il letto", "Dentro, uno strato di foglie secche spesso 30 cm, ben pressato: il freddo arriva soprattutto dal terreno." },
    { "La porta", "Entra con i piedi per primi e chiudi l'apertura tirando dentro un mucchio di foglie, o uno zaino." },
  } },

{ id = 'acqua', titolo = "Trovare e rendere sicura l'acqua", sez = 'sopravvivenza', livello = 1, tempo = '',
  intro = "In montagna l'acqua non manca quasi mai, ma anche quella più limpida può contenere batteri e parassiti.",
  serve = "Pentolino, filtro o pastiglie, una bandana.",
  passi = {
    { "Dove cercarla", "L'acqua scende: segui le valli e i canaloni verso il basso. Erba più verde, felci, muschi, salici indicano acqua vicina. Al mattino e alla sera gli uccelli e gli insetti volano verso l'acqua." },
    { "Scegli quella buona", "Meglio un torrente che scorre veloce di una pozza ferma. Prendila dal centro della corrente. Evita l'acqua sotto pascoli, malghe e paesi: è quella più contaminata." },
    { "Prefiltra", "Se è torbida, lasciala riposare nel pentolino e versa piano la parte limpida, oppure filtrala attraverso una bandana. Serve solo a togliere la terra: non la rende sicura." },
    { "Rendila sicura", "Bollire è il metodo più sicuro: un minuto di bollore pieno, tre minuti sopra i 2000 metri. Oppure il filtro (non ferma i virus) o le pastiglie (aspetta il tempo scritto sulla confezione)." },
    { "Rugiada e neve", "All'alba passa una maglietta sull'erba bagnata e strizzala nel pentolino. La neve va sciolta, mai mangiata: raffredda il corpo. Metti un fondo d'acqua nel pentolino prima della neve, o lo bruci." },
    { "Quanta bere", "2-3 litri al giorno, di più col caldo o in salita. Bevi poco e spesso. Se l'urina è scura, stai bevendo troppo poco." },
  } },

{ id = 'nord', titolo = 'Trovare il nord', sez = 'sopravvivenza', livello = 1, tempo = '20 min',
  intro = "Senza bussola: con l'ombra di un bastone, con l'orologio o con le stelle. Il muschio sugli alberi non è affidabile.",
  serve = "Un bastone dritto lungo un metro, due sassi.",
  passi = {
    { "Pianta il bastone", "Su un terreno piano, in pieno sole, pianta un bastone dritto in verticale.", img = 'nord1' },
    { "Segna la prima ombra", "Metti un sasso esattamente sulla punta dell'ombra.", img = 'nord2' },
    { "Aspetta e segna ancora", "Aspetta 15-20 minuti. L'ombra si è spostata: metti un secondo sasso sulla nuova punta.", img = 'nord3' },
    { "Leggi la direzione", "La linea dal primo al secondo sasso va da ovest a est. Mettiti con il primo sasso a sinistra e il secondo a destra: davanti a te c'è il nord.", img = 'nord4' },
    { "Con l'orologio a lancette", "Tieni l'orologio piatto e punta la lancetta delle ore verso il sole. A metà tra la lancetta delle ore e le 12 c'è il sud (con l'ora legale usa l'1 invece delle 12). Il nord è dalla parte opposta." },
    { "Con le stelle", "Trova il Grande Carro. Prendi le due stelle del bordo esterno del carro e prolunga la loro distanza circa 5 volte: arrivi alla Stella Polare, l'unica che non si muove. Sotto di lei, all'orizzonte, c'è il nord." },
  } },

------------------------------------------------------------------ nodi

{ id = 'otto', titolo = 'Nodo a otto', sez = 'nodi', livello = 1, tempo = '2 min',
  intro = "Il nodo d'arresto: fa un ingrossamento alla fine della corda, così non scappa da un anello o da un occhiello. Facile da sciogliere anche dopo un carico.",
  passi = {
    { "Forma un occhiello", "Tieni la parte fissa nella sinistra. Con il capo fai un occhiello, passando sopra la parte fissa.", img = 'otto1' },
    { "Gira dietro", "Porta il capo dietro la parte fissa.", img = 'otto2' },
    { "Infila", "Passa il capo dentro l'occhiello, da davanti verso dietro.", img = 'otto3' },
    { "Stringi", "Tira il capo e la parte fissa. Se il nodo ha la forma di un 8 è giusto. Lascia un capo lungo almeno un palmo.", img = 'otto4' },
  } },

{ id = 'gassa', titolo = "Gassa d'amante", sez = 'nodi', livello = 2, tempo = '3 min',
  intro = "Il re dei nodi: un'asola fissa che non si stringe sotto carico e si scioglie facilmente. Per legare la corda a un albero, la dorsale del tarp, una persona.",
  attenzione = "Non va bene per l'arrampicata se non è assicurata: lì si usa il nodo a otto inseguito.",
  passi = {
    { "La tana", "Sulla parte fissa fai un piccolo occhiello, con la parte verso il capo che passa sopra. È la tana del coniglio. Il resto della corda forma la grande asola in basso.", img = 'gassa1' },
    { "Il coniglio esce dalla tana", "Il capo (il coniglio) sale dentro l'occhiello, da sotto.", img = 'gassa2' },
    { "Gira intorno all'albero", "Il capo passa dietro la parte fissa (l'albero).", img = 'gassa3' },
    { "Torna nella tana", "Il capo scende di nuovo dentro l'occhiello, accanto a sé stesso.", img = 'gassa4' },
    { "Stringi", "Tieni insieme il capo e il lato dell'asola, e tira la parte fissa. Il capo deve restare lungo almeno un palmo.", img = 'gassa5' },
  } },

{ id = 'piano', titolo = 'Nodo piano', sez = 'nodi', livello = 1, tempo = '1 min',
  intro = "Per unire i due capi della stessa corda intorno a qualcosa: un fascio di legna, una benda, il sacco a pelo.",
  attenzione = "Non usarlo per unire due corde che devono reggere un peso: sotto strappi può sciogliersi.",
  passi = {
    { "Destra sopra sinistra", "Incrocia il capo destro sopra il sinistro e fallo passare sotto: è il primo mezzo nodo." },
    { "Sinistra sopra destra", "Ora il capo che è a sinistra va sopra quello di destra, e poi sotto." },
    { "Controlla", "I due capi devono uscire dallo stesso lato del nodo, paralleli alle loro parti. Se escono storti hai fatto il nodo della nonna, che scivola: rifallo." },
    { "Sciogli", "Si scioglie tirando uno dei capi indietro verso la sua parte: il nodo si rovescia e scorre via." },
  } },

{ id = 'parlato', titolo = 'Nodo parlato', sez = 'nodi', livello = 1, tempo = '2 min',
  intro = "Per legare velocemente una corda a un palo o a un ramo, e regolarne la lunghezza.",
  attenzione = "Su un palo liscio o con strappi può scivolare: aggiungi un mezzo collo con il capo intorno alla parte fissa.",
  passi = {
    { "Primo giro", "Passa la corda intorno al palo, da davanti a dietro, e riportala davanti." },
    { "La croce", "Fai passare il capo sopra la parte fissa: si forma una X." },
    { "Secondo giro", "Fai un secondo giro intorno al palo, sotto al primo, sempre nella stessa direzione." },
    { "Infila", "Passa il capo sotto il secondo giro, al centro della X: esce accanto alla parte fissa, in direzione opposta." },
    { "Stringi", "Tira i due capi in direzioni opposte. Le due spire devono essere affiancate e la X al centro." },
  } },

{ id = 'tendicavo', titolo = 'Tendicavo', sez = 'nodi', livello = 2, tempo = '3 min',
  intro = "Il nodo dei tiranti: scorre per tendere la corda e si blocca da solo sotto tensione. Per i tiranti della tenda e del tarp.",
  passi = {
    { "Intorno al picchetto", "Passa il capo intorno al picchetto (o all'albero) e riportalo verso la parte tesa della corda, lasciando un'asola." },
    { "Due giri dentro", "Avvolgi il capo due volte intorno alla parte tesa, lavorando verso il picchetto, dentro l'asola." },
    { "Un giro fuori", "Porta il capo oltre i due giri, fuori dall'asola, e fai un altro giro intorno alla parte tesa. Infila il capo sotto quest'ultimo giro." },
    { "Tendi", "Stringi i giri. Fai scorrere il nodo lungo la corda per tendere: sotto carico si blocca. Per allentare, fallo scorrere con le dita." },
  } },

{ id = 'prusik', titolo = 'Prusik', sez = 'nodi', livello = 3, tempo = '3 min',
  intro = "Un nodo autobloccante fatto con un anello di cordino intorno a una corda più grossa: scorre quando è scarico e si blocca sotto carico. Per appendere oggetti alla dorsale del tarp e spostarli.",
  serve = "Un anello di cordino di diametro più piccolo della corda (circa i due terzi).",
  attenzione = "Per l'arrampicata e la risalita su corda serve un corso: non imparare dalle immagini.",
  passi = {
    { "L'anello", "Chiudi un pezzo di cordino lungo un metro con un nodo inglese doppio: ottieni un anello." },
    { "Il primo giro", "Appoggia l'anello dietro la corda, passa un capo dell'anello davanti e infilalo dentro l'altro capo." },
    { "Tre giri", "Ripeti, avvolgendo l'anello intorno alla corda e dentro sé stesso, per tre giri. Le spire devono restare dentro l'anello, ordinate e parallele." },
    { "Prova", "Sistema le spire senza incroci. Tira l'anello: deve bloccarsi. Spingi il nodo con la mano: deve scorrere. Se scivola sotto carico, aggiungi un giro." },
  } },

------------------------------------------------------------------ trappole

{ id = 'regole', titolo = 'Prima di tutto: la legge', sez = 'trappole', livello = 1, tempo = '3 min',
  intro = "Leggi questa pagina prima delle altre trappole.",
  passi = {
    { "In Italia è vietato", "Cacciare con trappole, lacci e trabocchetti è vietato (legge 157 del 1992): è bracconaggio, punito anche con l'arresto. Nei parchi è vietato anche disturbare gli animali. La pesca richiede la licenza e ha regole precise." },
    { "Solo in una vera emergenza", "Queste tecniche sono per una situazione in cui la vita è in pericolo e non c'è altro modo. In Italia, in una montagna normale, i soccorsi arrivano in ore o giorni: per sopravvivere non serve cacciare. Chiama il 112." },
    { "Il cibo viene per ultimo", "Si resiste settimane senza mangiare. Riparo, calore, acqua e farsi trovare sono molto più importanti, e cacciare consuma energie e acqua." },
    { "Sicurezza e rispetto", "Una trappola può ferire una persona, un cane, un animale protetto. Non lasciarla mai incustodita, segna dove l'hai messa e smontala sempre prima di andare via." },
  } },

{ id = 'quattro', titolo = 'Trappola a quattro', sez = 'trappole', livello = 3, tempo = '1 ora',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietata. Armala con le mani sempre fuori da sotto il peso.",
  intro = "Tre bastoncini incastrati a forma di 4 reggono una pietra piatta. Quando un piccolo animale tocca l'esca, il 4 si scompone e la pietra cade. Insegna a intagliare tacche precise.",
  serve = "Coltello, tre bastoncini dritti spessi un dito, una pietra piatta pesante.",
  passi = {
    { "I tre pezzi", "Montante: 30 cm, verticale, con la cima tagliata a scalpello piatto. Asta: 30 cm, la diagonale. Braccio: 40 cm, orizzontale, porterà l'esca.", img = 'quattro1' },
    { "Le tacche", "Asta: una tacca vicino a un capo, che si appoggia sulla cima del montante, e l'altro capo a scalpello. Braccio: una tacca quadrata a metà, che aggancia il montante, e una tacca in punta dove si infila lo scalpello dell'asta.", img = 'quattro2' },
    { "Monta il 4", "Tieni il montante verticale. Aggancia il braccio orizzontale con la sua tacca a metà del montante. Appoggia l'asta in diagonale: la sua tacca sulla cima del montante, lo scalpello nella tacca in punta al braccio. Insieme formano un 4.", img = 'quattro3' },
    { "Appoggia la pietra", "Appoggia un bordo della pietra a terra e l'altro sulla cima dell'asta: il peso tiene il 4 in tensione. Le mani sempre fuori da sotto la pietra.", img = 'quattro4' },
    { "Come scatta", "Un tocco al braccio lo sgancia dal montante: il 4 si scompone e la pietra cade. Provala più volte con un ramo, da lontano. Controllala spesso e smontala prima di andare via.", img = 'quattro5' },
  } },

{ id = 'laccio', titolo = 'Il laccio', sez = 'trappole', livello = 2, tempo = '20 min',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietato. Un laccio dimenticato continua a uccidere: smontalo sempre.",
  intro = "Il metodo più semplice e antico: un cappio di filo sottile lungo un passaggio usato dagli animali.",
  serve = "Filo sottile e rigido (o cordino fine), un paletto.",
  passi = {
    { "Trova il passaggio", "Cerca i sentieri degli animali: tracce, erba schiacciata, passaggi sotto i cespugli, vicino all'acqua. Il laccio va dove l'animale è costretto a passare." },
    { "Il cappio", "Fai un piccolo occhiello a un capo del filo e passa l'altro capo dentro: ottieni un cappio che si stringe scorrendo." },
    { "La misura", "Il cappio deve essere largo come una mano per un animale piccolo, con il bordo basso a un pugno da terra." },
    { "Fissalo", "Lega l'altro capo a un paletto ben piantato o a un ramo robusto. Metti dei rametti ai lati del passaggio perché l'animale passi proprio nel cappio." },
    { "Controlla e smonta", "Controlla almeno due volte al giorno. Quando te ne vai, smonta tutto e porta via il filo." },
  } },

{ id = 'pesca', titolo = 'Pesca di sopravvivenza', sez = 'trappole', livello = 2, tempo = '',
  attenzione = "In Italia pescare richiede la licenza e rispetta stagioni e misure: questo è solo per l'emergenza.",
  intro = "Nei torrenti di montagna ci sono trote: con una lenza e un amo di fortuna si può pescare senza canna.",
  serve = "Filo o cordino sottile, un amo (o una spina, un osso, un fermaglio), esca.",
  passi = {
    { "L'amo di fortuna", "Una spina di rovo o di biancospino legata a un rametto, un fermaglio piegato, oppure un bastoncino appuntito ai due capi legato al centro: l'esca lo nasconde e il pesce lo inghiotte di traverso." },
    { "L'esca", "Vermi e larve sotto i sassi e nel legno marcio, insetti, cavallette. Mettila in modo che copra la punta." },
    { "Dove pescare", "Le trote stanno ferme dove la corrente rallenta: dietro i massi, nelle buche profonde, sotto le rive. Avvicinati piano, senza farti vedere e senza ombra sull'acqua." },
    { "La lenza", "Lega la lenza a un ramo lungo e flessibile. Lascia che l'esca scenda con la corrente verso la buca." },
    { "Linee fisse", "Più lenze legate a rami sulla riva pescano mentre fai altro. Controllale spesso e toglile tutte prima di andare via." },
  } },

}
end

-- Bivacco: attrezzatura, tecniche e percorso per la montagna, per Xteink X4 Pro (XTOS Licorice, API 2.0).
--
-- Tre schede in basso:
--   Attrezzatura  la lista nell'ordine in cui si carica lo zaino, da spuntare; ogni voce ha dove va e perché
--   Tecniche      Sopravvivenza, Nodi, Trappole: tutorial passo passo, con un disegno per passo
--   Percorso      per ora la lista "Prima di partire": la mappa arriverà dopo
-- I testi sono in attrezzatura.lua e tecniche.lua; i disegni in data/img.txt (tools/make_art.py),
-- immagini.lua dice in che riga è ciascuno.

local ATTR = require('attrezzatura')
local TEC = require('tecniche')
local IMG = require('immagini')

local W, H = kit.W, kit.H
local M = 20
local TAB_Y = 736
local BTN_Y, BTN_H = 716, 64
local IMG_W, IMG_H = 440, 250

local SEZIONI = { { 'sopravvivenza', 'Sopravvivenza' }, { 'nodi', 'Nodi' }, { 'trappole', 'Trappole' } }
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

local larghezza_riga = nil
local function riga_img(n)
  local ok, r = pcall(function() return ctx.package:open_text('img.txt', { max_line_bytes = 65536 }) end)
  if not ok or r == nil or type(r) == 'string' then return nil end
  local riga
  pcall(function()
    if not larghezza_riga then
      local prima_riga = r:read_line()
      if not prima_riga then return end
      larghezza_riga = #prima_riga + 1
      if n == 1 then riga = prima_riga; return end
    end
    r:seek((n - 1) * larghezza_riga)
    riga = r:read_line()
  end)
  pcall(function() r:close() end)
  return riga and (riga:gsub(' +$', ''))
end

local cache, ordine = {}, {}
local function immagine(nome)
  if not nome or not IMG[nome] then return nil end
  if cache[nome] ~= nil then return cache[nome] or nil end
  while #ordine >= 4 do
    local v = table.remove(ordine, 1)
    if cache[v] then pcall(function() cache[v]:release() end) end
    cache[v] = nil
  end
  local img = false
  if ctx.capabilities and ctx.capabilities.image_decode and ctx.image and ctx.base64 then
    local b64 = riga_img(IMG[nome])
    if b64 then
      local ok, bytes = pcall(function() return ctx.base64:decode(b64) end)
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

local function barra(gg)
  riga_doppia(gg, TAB_Y)
  local voci = { { 'attrezzatura', 'Attrezzatura' }, { 'tecniche', 'Tecniche' }, { 'percorso', 'Percorso' } }
  local w = W // 3
  local attivo = scr
  if scr == 'voce' or scr == 'zaino' then attivo = 'attrezzatura' end
  if scr == 'tutorial' or scr == 'passo' then attivo = 'tecniche' end
  for k, v in ipairs(voci) do
    local cx = (k - 1) * w + w // 2
    testo_c(gg, cx, TAB_Y + 18, v[2], 'medium')
    if attivo == v[1] then
      local lw = larg('medium', v[2])
      gg:rect(cx - lw // 2, TAB_Y + 48, lw, 4, { fill = true })
    end
    zona((k - 1) * w, TAB_Y, w, H - TAB_Y, function() scr, da = v[1], {} end)
  end
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

------------------------------------------------------------------ Attrezzatura

local function conta()
  local tot, ok = 0, 0
  for _g, gr in ipairs(ATTR) do
    if not gr.facoltativo then
      for _k, v in ipairs(gr.voci) do tot = tot + 1; if spunte[v.id] then ok = ok + 1 end end
    end
  end
  return ok, tot
end

local RIGA_TIT, RIGA_VOCE = 46, 58

local function righe_attr()
  local out = { { tipo = 'zaino' } }
  for gi, gr in ipairs(ATTR) do
    out[#out + 1] = { tipo = 'gruppo', g = gi }
    for vi, _v in ipairs(gr.voci) do out[#out + 1] = { tipo = 'voce', g = gi, v = vi } end
  end
  out[#out + 1] = { tipo = 'azzera' }
  return out
end

local function alt_riga(r) return r.tipo == 'voce' and RIGA_VOCE or r.tipo == 'zaino' and 64 or RIGA_TIT end

local function pagine_attr(alto)
  local righe = righe_attr()
  local pagine, h = { 1 }, 0
  for k, r in ipairs(righe) do
    local a = alt_riga(r)
    -- un titolo di gruppo non resta da solo in fondo alla pagina
    if r.tipo == 'gruppo' then a = a + RIGA_VOCE end
    if h + a > alto and k > pagine[#pagine] then pagine[#pagine + 1] = k; h = 0 end
    h = h + alt_riga(r)
  end
  return righe, pagine
end

local function disegna_attrezzatura(gg)
  local ok, tot = conta()
  local top = intestazione(gg, 'Attrezzatura', ok .. ' / ' .. tot .. ' nello zaino')
  local y0 = top + 6
  local alto = TAB_Y - 50 - y0
  local righe, pagine = pagine_attr(alto)
  if pag_attr > #pagine then pag_attr = #pagine end
  per_pag_attr = #pagine
  local y = y0
  local fine = (pagine[pag_attr + 1] or (#righe + 1)) - 1
  for k = pagine[pag_attr], fine do
    local r = righe[k]
    if r.tipo == 'zaino' then
      gg:rect(M, y + 6, W - 2 * M, 52, { radius = 12, line_width = 2 })
      testo(gg, M + 16, y + 10, 'Come caricare lo zaino', 'medium')
      testo(gg, M + 16, y + 34, 'Il fondo, il centro, la cima: perché', 'small')
      freccia(gg, W - M - 18, y + 32, 'dx', 8)
      zona(M, y + 6, W - 2 * M, 52, function() vai('zaino') end)
    elseif r.tipo == 'gruppo' then
      local gr = ATTR[r.g]
      testo(gg, M, y + 14, gr.titolo, 'medium')
      gg:line(M, y + RIGA_TIT - 4, W - M, y + RIGA_TIT - 4)
      gg:line(M, y + RIGA_TIT - 3, W - M, y + RIGA_TIT - 3)
    elseif r.tipo == 'voce' then
      local v = ATTR[r.g].voci[r.v]
      local on = spunte[v.id]
      casella(gg, M, y + 13, 30, on)
      testo(gg, M + 46, y + 6, v.nome, 'medium')
      testo(gg, M + 46, y + 32, kit.short(v.dove, 40), 'small')
      freccia(gg, W - M - 8, y + RIGA_VOCE // 2, 'dx', 6)
      gg:line(M + 46, y + RIGA_VOCE - 1, W - M, y + RIGA_VOCE - 1)
      zona(M + 40, y, W - 2 * M - 40, RIGA_VOCE, function() voce = r; vai("voce") end)
      zona(0, y, M + 40, RIGA_VOCE, function() spunte[v.id] = not on or nil; salva() end)
    elseif r.tipo == 'azzera' then
      bottone(gg, M + 80, y + 8, W - 2 * M - 160, 40, 'Togli tutte le spunte', false, function()
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

local function disegna_voce(gg)
  local gr = ATTR[voce.g]
  local v = gr.voci[voce.v]
  local top = intestazione(gg, v.nome, nil, true)
  local y = top + 14
  testo(gg, M, y, gr.titolo, 'small')
  y = y + lh('small') + 10
  local function blocco(titolo, s)
    testo(gg, M, y, titolo, 'large')
    y = y + lh('large') + 4
    y = paragrafo(gg, M, y, s, 'medium', W - 2 * M, BTN_Y - 20, 6) + 10
  end
  blocco('Dove va', v.dove)
  blocco('Perché', v.perche)
  if v.peso then testo(gg, M, y, 'Peso: circa ' .. (v.peso >= 1000 and (string.format('%.1f', v.peso / 1000):gsub('%.', ',') .. ' kg') or (v.peso .. ' g')), 'small'); y = y + lh('small') + 14 end
  blocco('Consiglio', v.consiglio)
  local on = spunte[v.id]
  bottone(gg, M, BTN_Y, W - 2 * M, BTN_H, on and 'Nello zaino  (tocca per togliere)' or 'Metti nello zaino', not on, function()
    spunte[v.id] = not on or nil; salva()
    if not on then indietro() end
  end)
  if on then casella(gg, M + 24, BTN_Y + 18, 28, true) end
end

local function disegna_zaino(gg)
  local top = intestazione(gg, 'Come caricare lo zaino', nil, true)
  local y = top + 8
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
  y = y + IMG_H + 10
  local regole = {
    "In fondo il leggero che serve solo la sera: sacco a pelo e vestiti asciutti.",
    "Al centro, contro la schiena e all'altezza delle scapole, il pesante: cibo, acqua, fornello, tenda.",
    "In cima e nelle tasche quello che serve all'improvviso: antipioggia, pile, frontale, coltello.",
    "Prima la cintura sui fianchi, poi gli spallacci: il peso va sulle gambe, non sulle spalle.",
  }
  for _k, r in ipairs(regole) do
    gg:circle(M + 5, y + 11, 4, { fill = true })
    y = paragrafo(gg, M + 18, y, r, 'small', W - 2 * M - 18, H - 10, 2) + 8
  end
end

------------------------------------------------------------------ Tecniche

local function di_sezione()
  local out = {}
  for k, t in ipairs(TEC) do if t.sez == sez then out[#out + 1] = k end end
  return out
end

local function con_immagini(t)
  for _k, p in ipairs(t.passi) do if p.img then return true end end
  return false
end

local TEC_RIGA = 74

local function disegna_tecniche(gg)
  local lista = di_sezione()
  local top = intestazione(gg, 'Tecniche', nil)
  local y = top + 10
  local cw = (W - 2 * M) // #SEZIONI
  gg:rect(M, y, W - 2 * M, 44, { radius = 22, line_width = 2 })
  for k, s in ipairs(SEZIONI) do
    local x = M + (k - 1) * cw
    if sez == s[1] then gg:rect(x + 3, y + 4, cw - 6, 36, { radius = 18, line_width = 3 }) end
    testo_c(gg, x + cw // 2, y + 12, s[2], 'small')
    zona(x, y, cw, 44, function() sez = s[1]; pag_tec = 1; salva() end)
  end
  y = y + 56
  if sez == 'trappole' then
    gg:rect(M, y, W - 2 * M, 54, { line_width = 3 })
    paragrafo(gg, M + 12, y + 6, "In Italia le trappole sono vietate: solo per una vera emergenza. Leggi prima 'la legge'.", 'small', W - 2 * M - 24, y + 52, 2)
    y = y + 64
  end
  local per_pag = (TAB_Y - 46 - y) // TEC_RIGA
  local pagine = math.max(1, (#lista + per_pag - 1) // per_pag)
  if pag_tec > pagine then pag_tec = pagine end
  for j = 1, per_pag do
    local k = lista[(pag_tec - 1) * per_pag + j]
    if not k then break end
    local t = TEC[k]
    local ry = y + (j - 1) * TEC_RIGA
    local tit = kit.short(t.titolo, 34)
    testo(gg, M, ry + 10, tit, 'medium')
    if fatti[t.id] then avanti_spuntato(gg, M + larg('medium', tit) + 10, ry + 12) end
    local meta = #t.passi .. ' passi · ' .. LIVELLI[t.livello] .. (t.tempo ~= '' and (' · ' .. t.tempo) or '')
      .. (con_immagini(t) and ' · con disegni' or '')
    testo(gg, M, ry + 40, meta, 'small')
    freccia(gg, W - M - 8, ry + TEC_RIGA // 2, 'dx', 7)
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
  local t = TEC[tut]
  local top = intestazione(gg, t.titolo, nil, true)
  local y = top + 12
  local fondo = BTN_Y - 14
  testo(gg, M, y, #t.passi .. ' passi · ' .. LIVELLI[t.livello] .. (t.tempo ~= '' and (' · ' .. t.tempo) or ''), 'small')
  y = y + lh('small') + 10
  y = paragrafo(gg, M, y, t.intro, 'medium', W - 2 * M, fondo, 6) + 8
  if t.serve then
    testo(gg, M, y, 'Ti serve', 'medium')
    y = y + lh('medium') + 2
    y = paragrafo(gg, M, y, t.serve, 'small', W - 2 * M, fondo, 2) + 10
  end
  if t.attenzione then
    local righe = kit.wrap(t.attenzione, 'small', W - 2 * M - 28)
    local h = #righe * (lh('small') + 2) + 40
    gg:rect(M, y, W - 2 * M, h, { line_width = 3 })
    testo(gg, M + 14, y + 8, 'Attenzione', 'medium')
    local yy = y + 8 + lh('medium')
    for _k, l in ipairs(righe) do testo(gg, M + 14, yy, l, 'small'); yy = yy + lh('small') + 2 end
    y = y + h + 12
  end
  testo(gg, M, y, 'I passi', 'medium')
  y = y + lh('medium') + 4
  for k, p in ipairs(t.passi) do
    if y + lh('small') > fondo then break end
    testo(gg, M, y, k .. '. ' .. kit.short(p[1], 42), 'small')
    local yy = y
    zona(M, yy - 2, W - 2 * M, lh('small') + 4, function() passo = k; vai('passo') end)
    y = y + lh('small') + 4
  end
  bottone(gg, M, BTN_Y, W - 2 * M, BTN_H, fatti[t.id] and 'Rivedi i passi' or 'Inizia', true, inizia)
end

local function disegna_passo(gg)
  local t = TEC[tut]
  local n = #t.passi
  local p = t.passi[passo]
  local top = intestazione(gg, kit.short(t.titolo, 32), passo .. ' / ' .. n, true)
  local gap = n > 12 and 3 or 5
  local sw = (W - 2 * M - (n - 1) * gap) / n
  local y = top + 10
  for k = 1, n do
    local x = math.floor(M + (k - 1) * (sw + gap))
    if k <= passo then gg:rect(x, y, math.floor(sw), 8, { fill = true }) else gg:rect(x, y, math.floor(sw), 8) end
  end
  y = y + 18
  if p.img and disegno(gg, p.img, (W - IMG_W) // 2, y) then
    gg:rect((W - IMG_W) // 2, y, IMG_W, IMG_H)
    y = y + IMG_H + 10
  end
  testo(gg, M, y, 'Passo ' .. passo, 'small')
  y = y + lh('small') + 4
  local tit = kit.wrap(p[1], 'large', W - 2 * M)
  for k = 1, math.min(2, #tit) do testo(gg, M, y, tit[k], 'large'); y = y + lh('large') + 2 end
  y = y + 6
  local fondo = BTN_Y - 12
  local f, corpo, alt
  for _k, prova in ipairs({ { 'large', 6 }, { 'medium', 6 }, { 'medium', 2 }, { 'small', 3 }, { 'small', 1 } }) do
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
  bottone(gg, M + 162, BTN_Y, W - 2 * M - 162, BTN_H, passo < n and 'Avanti' or 'Fine', true, function()
    if passo < n then passo = passo + 1 else fatti[t.id] = true; salva(); indietro() end
  end)
end

------------------------------------------------------------------ Percorso

local function disegna_percorso(gg)
  local top = intestazione(gg, 'Percorso', nil)
  local y = top + 14
  testo(gg, M, y, 'Prima di partire', 'large')
  y = y + lh('large') + 10
  for _k, v in ipairs(PRIMA) do
    local on = prima[v[1]]
    casella(gg, M, y + 4, 30, on)
    local righe = kit.wrap(v[2], 'medium', W - 2 * M - 46)
    local yy = y
    for i = 1, math.min(2, #righe) do testo(gg, M + 46, yy, righe[i], 'medium'); yy = yy + lh('medium') + 2 end
    local h = math.max(44, yy - y + 8)
    zona(0, y, W, h, function() prima[v[1]] = not on or nil; salva() end)
    y = y + h + 6
  end
  y = y + 10
  gg:rect(M, y, W - 2 * M, TAB_Y - 16 - y, { radius = 12, line_width = 1 })
  testo(gg, M + 16, y + 12, 'La mappa', 'medium')
  paragrafo(gg, M + 16, y + 12 + lh('medium') + 6,
    "Qui arriveranno la mappa e il percorso: lo progettiamo insieme nel prossimo passo.",
    'small', W - 2 * M - 32, TAB_Y - 20, 2)
  barra(gg)
end

------------------------------------------------------------------ callback

local DISEGNA = { attrezzatura = disegna_attrezzatura, voce = disegna_voce, zaino = disegna_zaino,
                  tecniche = disegna_tecniche, tutorial = disegna_tutorial, passo = disegna_passo,
                  percorso = disegna_percorso }

function on_draw(c, gg)
  zone = {}
  local ok, e = pcall(function()
    gg:clear(0)
    DISEGNA[scr](gg)
    if kit.modal() then kit.draw_modal(gg) end
  end)
  if not ok then ERR = tostring(e) end
  if ERR then
    for k, l in ipairs(kit.wrap('ERR ' .. ERR, 'small', W - 2 * M)) do gg:text(M, 600 + (k - 1) * 22, l, { font = kit.font('small') }) end
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
    local n = #TEC[tut].passi
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
  if prima_scr ~= scr then pcall(function() ctx:invalidate('full') end) else kit.refresh() end
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
    tecniche = TEC, attrezzatura = ATTR, err = function() return ERR end,
    apri = function(k) tut = k; da = { 'tecniche' }; scr = 'tutorial' end,
    set = function(s) scr = s end, conta = conta,
  }
end
