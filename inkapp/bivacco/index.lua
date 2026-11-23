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
-- GENERATO da tools/make_art.py: dove sta ogni immagine (file in data/, numero di riga)
return {
  zaino = { 'img1.txt', 1 },
  stop_1 = { 'img1.txt', 2 },
  stop_2 = { 'img1.txt', 3 },
  stop_3 = { 'img1.txt', 4 },
  stop_4 = { 'img1.txt', 5 },
  stop_5 = { 'img1.txt', 6 },
  stop_6 = { 'img1.txt', 7 },
  fuoco1 = { 'img1.txt', 8 },
  fuoco2 = { 'img1.txt', 9 },
  fuoco3 = { 'img1.txt', 10 },
  fuoco4 = { 'img1.txt', 11 },
  fuoco5 = { 'img1.txt', 12 },
  fuoco6 = { 'img1.txt', 13 },
  piuma1 = { 'img1.txt', 14 },
  piuma2 = { 'img1.txt', 15 },
  piuma3 = { 'img1.txt', 16 },
  piuma4 = { 'img1.txt', 17 },
  coltello_1 = { 'img1.txt', 18 },
  coltello_2 = { 'img1.txt', 19 },
  coltello_3 = { 'img1.txt', 20 },
  coltello_4 = { 'img1.txt', 21 },
  coltello_5 = { 'img1.txt', 22 },
  coltello_6 = { 'img1.txt', 23 },
  tarp1 = { 'img1.txt', 24 },
  tarp2 = { 'img1.txt', 25 },
  tarp3 = { 'img1.txt', 26 },
  tarp4 = { 'img1.txt', 27 },
  tarp5 = { 'img1.txt', 28 },
  capanna_1 = { 'img1.txt', 29 },
  capanna_2 = { 'img1.txt', 30 },
  capanna_3 = { 'img1.txt', 31 },
  capanna_4 = { 'img1.txt', 32 },
  capanna_5 = { 'img1.txt', 33 },
  capanna_6 = { 'img1.txt', 34 },
  acqua_1 = { 'img1.txt', 35 },
  acqua_2 = { 'img1.txt', 36 },
  acqua_3 = { 'img1.txt', 37 },
  acqua_4 = { 'img1.txt', 38 },
  acqua_5 = { 'img1.txt', 39 },
  acqua_6 = { 'img1.txt', 40 },
  nord1 = { 'img1.txt', 41 },
  nord2 = { 'img1.txt', 42 },
  nord3 = { 'img1.txt', 43 },
  nord4 = { 'img1.txt', 44 },
  nord_5 = { 'img1.txt', 45 },
  nord_6 = { 'img1.txt', 46 },
  otto1 = { 'img1.txt', 47 },
  otto2 = { 'img1.txt', 48 },
  otto3 = { 'img1.txt', 49 },
  otto4 = { 'img1.txt', 50 },
  gassa1 = { 'img1.txt', 51 },
  gassa2 = { 'img1.txt', 52 },
  gassa3 = { 'img1.txt', 53 },
  gassa4 = { 'img1.txt', 54 },
  gassa5 = { 'img1.txt', 55 },
  piano_1 = { 'img1.txt', 56 },
  piano_2 = { 'img1.txt', 57 },
  piano_3 = { 'img1.txt', 58 },
  piano_4 = { 'img1.txt', 59 },
  parlato_1 = { 'img1.txt', 60 },
  parlato_2 = { 'img1.txt', 61 },
  parlato_3 = { 'img1.txt', 62 },
  parlato_4 = { 'img1.txt', 63 },
  parlato_5 = { 'img1.txt', 64 },
  tendicavo_1 = { 'img1.txt', 65 },
  tendicavo_2 = { 'img1.txt', 66 },
  tendicavo_3 = { 'img1.txt', 67 },
  tendicavo_4 = { 'img1.txt', 68 },
  prusik_1 = { 'img1.txt', 69 },
  prusik_2 = { 'img1.txt', 70 },
  prusik_3 = { 'img1.txt', 71 },
  prusik_4 = { 'img1.txt', 72 },
  regole_1 = { 'img1.txt', 73 },
  regole_2 = { 'img1.txt', 74 },
  regole_3 = { 'img1.txt', 75 },
  regole_4 = { 'img1.txt', 76 },
  quattro1 = { 'img1.txt', 77 },
  quattro2 = { 'img1.txt', 78 },
  quattro3 = { 'img1.txt', 79 },
  quattro4 = { 'img1.txt', 80 },
  quattro5 = { 'img1.txt', 81 },
  laccio_1 = { 'img1.txt', 82 },
  laccio_2 = { 'img1.txt', 83 },
  laccio_3 = { 'img2.txt', 1 },
  laccio_4 = { 'img2.txt', 2 },
  laccio_5 = { 'img2.txt', 3 },
  pesca_1 = { 'img2.txt', 4 },
  pesca_2 = { 'img2.txt', 5 },
  pesca_3 = { 'img2.txt', 6 },
  pesca_4 = { 'img2.txt', 7 },
  pesca_5 = { 'img2.txt', 8 },
  tipi1 = { 'img2.txt', 9 },
  tipi2 = { 'img2.txt', 10 },
  tipi3 = { 'img2.txt', 11 },
  tipifuoco_4 = { 'img2.txt', 12 },
  tipifuoco_5 = { 'img2.txt', 13 },
  dakota1 = { 'img2.txt', 14 },
  dakota2 = { 'img2.txt', 15 },
  dakota3 = { 'img2.txt', 16 },
  dakota_4 = { 'img2.txt', 17 },
  dakota_5 = { 'img2.txt', 18 },
  archetto1 = { 'img2.txt', 19 },
  archetto_2 = { 'img2.txt', 20 },
  archetto2 = { 'img2.txt', 21 },
  archetto_4 = { 'img2.txt', 22 },
  archetto_5 = { 'img2.txt', 23 },
  archetto_6 = { 'img2.txt', 24 },
  archetto_7 = { 'img2.txt', 25 },
  batoning_1 = { 'img2.txt', 26 },
  baton1 = { 'img2.txt', 27 },
  baton2 = { 'img2.txt', 28 },
  batoning_4 = { 'img2.txt', 29 },
  batoning_5 = { 'img2.txt', 30 },
  cordame_1 = { 'img2.txt', 31 },
  cordame_2 = { 'img2.txt', 32 },
  cordame_3 = { 'img2.txt', 33 },
  cordame_4 = { 'img2.txt', 34 },
  cordame_5 = { 'img2.txt', 35 },
  cordame_6 = { 'img2.txt', 36 },
  gancio1 = { 'img2.txt', 37 },
  gancio_2 = { 'img2.txt', 38 },
  gancio_3 = { 'img2.txt', 39 },
  gancio_4 = { 'img2.txt', 40 },
  gancio_5 = { 'img2.txt', 41 },
  mezzicolli_1 = { 'img2.txt', 42 },
  mezzicolli_2 = { 'img2.txt', 43 },
  mezzicolli_3 = { 'img2.txt', 44 },
  mezzicolli_4 = { 'img2.txt', 45 },
  camionista_1 = { 'img2.txt', 46 },
  camionista_2 = { 'img2.txt', 47 },
  camionista_3 = { 'img2.txt', 48 },
  camionista_4 = { 'img2.txt', 49 },
  camionista_5 = { 'img2.txt', 50 },
  bandiera_1 = { 'img2.txt', 51 },
  bandiera_2 = { 'img2.txt', 52 },
  bandiera_3 = { 'img2.txt', 53 },
  bandiera_4 = { 'img2.txt', 54 },
  bandiera_5 = { 'img2.txt', 55 },
  quadra1 = { 'img2.txt', 56 },
  quadra2 = { 'img2.txt', 57 },
  quadra3 = { 'img2.txt', 58 },
  quadra_4 = { 'img2.txt', 59 },
  diagonale_1 = { 'img2.txt', 60 },
  diagonale_2 = { 'img2.txt', 61 },
  diagonale_3 = { 'img2.txt', 62 },
  diagonale_4 = { 'img2.txt', 63 },
  treppiede_1 = { 'img2.txt', 64 },
  treppiede_2 = { 'img2.txt', 65 },
  treppiede_3 = { 'img2.txt', 66 },
  treppiede_4 = { 'img2.txt', 67 },
  treppiede1 = { 'img2.txt', 68 },
  cesoia_1 = { 'img2.txt', 69 },
  cesoia_2 = { 'img2.txt', 70 },
  cesoia_3 = { 'img2.txt', 71 },
  cesoia_4 = { 'img2.txt', 72 },
  cesoia_5 = { 'img2.txt', 73 },
  tarp_corde_1 = { 'img2.txt', 74 },
  tarp_corde_2 = { 'img2.txt', 75 },
  tarp_corde_3 = { 'img2.txt', 76 },
  tarp_corde_4 = { 'img2.txt', 77 },
  tarp_corde_5 = { 'img2.txt', 78 },
  tarp_a_1 = { 'img2.txt', 79 },
  tarpa1 = { 'img2.txt', 80 },
  tarp_a_3 = { 'img2.txt', 81 },
  tarpa2 = { 'img2.txt', 82 },
  diam1 = { 'img2.txt', 83 },
  tarp_diamante_2 = { 'img2.txt', 84 },
  diam2 = { 'img2.txt', 85 },
  tarp_diamante_4 = { 'img3.txt', 1 },
  vomere1 = { 'img3.txt', 2 },
  tarp_vomere_2 = { 'img3.txt', 3 },
  vomere2 = { 'img3.txt', 4 },
  tarp_vomere_4 = { 'img3.txt', 5 },
  tarp_cucina_1 = { 'img3.txt', 6 },
  tarp_cucina_2 = { 'img3.txt', 7 },
  cucina1 = { 'img3.txt', 8 },
  tarp_cucina_4 = { 'img3.txt', 9 },
  tarp_pioggia_1 = { 'img3.txt', 10 },
  tarp_pioggia_2 = { 'img3.txt', 11 },
  tarp_pioggia_3 = { 'img3.txt', 12 },
  tarp_pioggia_4 = { 'img3.txt', 13 },
  tarp_pioggia_5 = { 'img3.txt', 14 },
  paiute_1 = { 'img3.txt', 15 },
  paiute_2 = { 'img3.txt', 16 },
  paiute_3 = { 'img3.txt', 17 },
  paiute_4 = { 'img3.txt', 18 },
  paiute_5 = { 'img3.txt', 19 },
  molla_1 = { 'img3.txt', 20 },
  molla_2 = { 'img3.txt', 21 },
  molla_3 = { 'img3.txt', 22 },
  molla_4 = { 'img3.txt', 23 },
  molla_5 = { 'img3.txt', 24 },
  nassa1 = { 'img3.txt', 25 },
  nassa_2 = { 'img3.txt', 26 },
  nassa_3 = { 'img3.txt', 27 },
  nassa_4 = { 'img3.txt', 28 },
  nassa_5 = { 'img3.txt', 29 },
  banner_attrezzatura = { 'img3.txt', 30 },
  banner_tecniche = { 'img3.txt', 31 },
  banner_percorso = { 'img3.txt', 32 },
  tab_attrezzatura = { 'ui1.txt', 1 },
  tab_tecniche = { 'ui1.txt', 2 },
  tab_percorso = { 'ui1.txt', 3 },
  sez_sopravvivenza = { 'ui1.txt', 4 },
  sez_nodi = { 'ui1.txt', 5 },
  sez_trappole = { 'ui1.txt', 6 },
  sez_tarp = { 'ui1.txt', 7 },
  btn_avanti = { 'ui1.txt', 8 },
  btn_fine = { 'ui1.txt', 9 },
  btn_inizia = { 'ui1.txt', 10 },
  btn_zaino = { 'ui1.txt', 11 },
  ico_zaino60 = { 'ui1.txt', 12 },
  ico_zaino20 = { 'ui1.txt', 13 },
  ico_scarpe = { 'ui1.txt', 14 },
  ico_sacco = { 'ui1.txt', 15 },
  ico_tuta = { 'ui1.txt', 16 },
  ico_calze = { 'ui1.txt', 17 },
  ico_maglietta = { 'ui1.txt', 18 },
  ico_cibo = { 'ui1.txt', 19 },
  ico_fornello = { 'ui1.txt', 20 },
  ico_acqua = { 'ui1.txt', 21 },
  ico_tenda = { 'ui1.txt', 22 },
  ico_materassino = { 'ui1.txt', 23 },
  ico_antipioggia = { 'ui1.txt', 24 },
  ico_pile = { 'ui1.txt', 25 },
  ico_piumino = { 'ui1.txt', 26 },
  ico_tarp = { 'ui1.txt', 27 },
  ico_filtro = { 'ui1.txt', 28 },
  ico_frontale = { 'ui1.txt', 29 },
  ico_coltello = { 'ui1.txt', 30 },
  ico_corda = { 'ui1.txt', 31 },
  ico_kit = { 'ui1.txt', 32 },
  ico_telo = { 'ui1.txt', 33 },
  ico_fuoco = { 'ui1.txt', 34 },
  ico_mappa = { 'ui1.txt', 35 },
  ico_fischietto = { 'ui1.txt', 36 },
  num_1 = { 'ui1.txt', 37 },
  num_2 = { 'ui1.txt', 38 },
  num_3 = { 'ui1.txt', 39 },
  num_4 = { 'ui1.txt', 40 },
  num_5 = { 'ui1.txt', 41 },
  num_6 = { 'ui1.txt', 42 },
  num_7 = { 'ui1.txt', 43 },
  num_8 = { 'ui1.txt', 44 },
  num_9 = { 'ui1.txt', 45 },
  num_10 = { 'ui1.txt', 46 },
  num_11 = { 'ui1.txt', 47 },
  num_12 = { 'ui1.txt', 48 },
  num_13 = { 'ui1.txt', 49 },
  num_14 = { 'ui1.txt', 50 },
  num_15 = { 'ui1.txt', 51 },
  th_stop = { 'ui1.txt', 52 },
  th_fuoco = { 'ui1.txt', 53 },
  th_piuma = { 'ui1.txt', 54 },
  th_coltello = { 'ui1.txt', 55 },
  th_tarp = { 'ui1.txt', 56 },
  th_capanna = { 'ui1.txt', 57 },
  th_acqua = { 'ui1.txt', 58 },
  th_nord = { 'ui1.txt', 59 },
  th_otto = { 'ui1.txt', 60 },
  th_gassa = { 'ui1.txt', 61 },
  th_piano = { 'ui1.txt', 62 },
  th_parlato = { 'ui1.txt', 63 },
  th_tendicavo = { 'ui1.txt', 64 },
  th_prusik = { 'ui1.txt', 65 },
  th_regole = { 'ui1.txt', 66 },
  th_quattro = { 'ui1.txt', 67 },
  th_laccio = { 'ui1.txt', 68 },
  th_pesca = { 'ui1.txt', 69 },
  th_tipifuoco = { 'ui1.txt', 70 },
  th_dakota = { 'ui1.txt', 71 },
  th_archetto = { 'ui1.txt', 72 },
  th_batoning = { 'ui1.txt', 73 },
  th_cordame = { 'ui1.txt', 74 },
  th_gancio = { 'ui1.txt', 75 },
  th_mezzicolli = { 'ui1.txt', 76 },
  th_camionista = { 'ui1.txt', 77 },
  th_bandiera = { 'ui1.txt', 78 },
  th_quadra = { 'ui1.txt', 79 },
  th_diagonale = { 'ui1.txt', 80 },
  th_treppiede = { 'ui1.txt', 81 },
  th_cesoia = { 'ui1.txt', 82 },
  th_tarp_corde = { 'ui1.txt', 83 },
  th_tarp_a = { 'ui1.txt', 84 },
  th_tarp_diamante = { 'ui1.txt', 85 },
  th_tarp_vomere = { 'ui1.txt', 86 },
  th_tarp_cucina = { 'ui1.txt', 87 },
  th_tarp_pioggia = { 'ui1.txt', 88 },
  th_paiute = { 'ui1.txt', 89 },
  th_molla = { 'ui1.txt', 90 },
  th_nassa = { 'ui1.txt', 91 },
}
end

__mods['tecniche'] = function()
-- Le tecniche, in tre sezioni. Ogni tutorial:
--   id, titolo, sez ('sopravvivenza' = Bushcraft, 'nodi', 'trappole', 'tarp'), livello (1-3), tempo
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
    { "S: fermati", "Siediti, bevi un sorso, mangia qualcosa. Non fare un passo finché non hai un piano. Il panico fa camminare veloce nella direzione sbagliata.", img = 'stop_1' },
    { "T: pensa", "Respira. Quando hai visto l'ultimo segnale del sentiero? Quante ore di luce restano? Che tempo fa? Qualcuno sa dove sei e quando rientri?", img = 'stop_2' },
    { "O: osserva", "Guardati intorno: tracce, segnavia, rumore di un torrente o di una strada. Controlla la carta. Il telefono: chiama il 112 se serve, poi mettilo in modalità aereo per risparmiare batteria.", img = 'stop_3' },
    { "P: pianifica", "Se ritrovi il sentiero con certezza, torna indietro sulle tue tracce. Se no, o se sta per fare buio o c'è nebbia, resta dove sei: chi ti cerca parte dall'ultimo punto noto.", img = 'stop_4' },
    { "Le priorità: la regola del 3", "Si resiste circa 3 ore al freddo senza riparo, 3 giorni senza acqua, 3 settimane senza cibo. Quindi, in ordine: proteggiti dal freddo e dal vento, fatti trovare, poi acqua, poi fuoco. Il cibo viene per ultimo.", img = 'stop_5' },
    { "Fatti trovare", "Mettiti in un punto aperto e visibile, stendi qualcosa di colorato. Il segnale di soccorso alpino: 6 fischi (o lampi di luce) in un minuto, poi un minuto di pausa, e ripeti. All'elicottero: braccia alzate a Y vuol dire 'sì, serve aiuto'.", img = 'stop_6' },
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
    { "Il triangolo di sangue", "Siediti: l'area tra le ginocchia e l'inguine è il triangolo di sangue. Non tagliare mai in quella direzione. Lavora con i gomiti sulle ginocchia e il legno davanti, fuori dalle gambe.", img = 'coltello_1' },
    { "Lo spazio intorno", "Tieni il braccio teso e ruota: nessuno deve essere a portata. Quando non usi il coltello, rimettilo nel fodero, non piantarlo nel terreno.", img = 'coltello_2' },
    { "Il legno", "Taglia un pezzo lungo 25 cm da un ramo duro: nocciolo, faggio, frassino. Togli i rametti con colpi verso l'esterno.", img = 'coltello_3' },
    { "La punta", "Appoggia il legno su un ceppo e fai la punta con tagli lunghi verso il basso, girando il pezzo: quattro facce, come una matita. Non troppo sottile, o si rompe.", img = 'coltello_4' },
    { "La tacca per la corda", "A 3 cm dalla testa fai un taglio dritto di traverso, profondo un terzo. Poi un taglio inclinato dal basso che arriva al primo: salta via un cuneo. La corda si aggancia lì e non scivola.", img = 'coltello_5' },
    { "La testa", "Smussa il bordo della testa con piccoli tagli tutto intorno: quando lo batti con un sasso non si scheggia. Pianta il picchetto inclinato all'indietro, a 45 gradi rispetto alla corda.", img = 'coltello_6' },
  } },

{ id = 'tarp', titolo = 'Tarp a tettoia', sez = 'tarp', livello = 1, tempo = '20 min',
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
    { "La trave", "Trova un ramo robusto lungo una volta e mezza la tua altezza. Appoggia un capo su un ceppo o in una forcella all'altezza della vita, l'altro a terra. Sdraiati sotto: devi entrarci con un palmo di spazio, non di più. Piccola è calda.", img = 'capanna_1' },
    { "Le costole", "Appoggia ai due lati della trave tanti rami, fitti, che formano una A. Non devono sporgere molto sopra la trave.", img = 'capanna_2' },
    { "Il graticcio", "Intreccia rami sottili e frasche di traverso sulle costole: servono a tenere su le foglie.", img = 'capanna_3' },
    { "La copertura", "Ammucchia sopra foglie secche, aghi di pino, felci: almeno un braccio di spessore. È lo spessore che scalda e tiene fuori la pioggia. Sopra qualche ramo perché il vento non la porti via.", img = 'capanna_4' },
    { "Il letto", "Dentro, uno strato di foglie secche spesso 30 cm, ben pressato: il freddo arriva soprattutto dal terreno.", img = 'capanna_5' },
    { "La porta", "Entra con i piedi per primi e chiudi l'apertura tirando dentro un mucchio di foglie, o uno zaino.", img = 'capanna_6' },
  } },

{ id = 'acqua', titolo = "Trovare e rendere sicura l'acqua", sez = 'sopravvivenza', livello = 1, tempo = '',
  intro = "In montagna l'acqua non manca quasi mai, ma anche quella più limpida può contenere batteri e parassiti.",
  serve = "Pentolino, filtro o pastiglie, una bandana.",
  passi = {
    { "Dove cercarla", "L'acqua scende: segui le valli e i canaloni verso il basso. Erba più verde, felci, muschi, salici indicano acqua vicina. Al mattino e alla sera gli uccelli e gli insetti volano verso l'acqua.", img = 'acqua_1' },
    { "Scegli quella buona", "Meglio un torrente che scorre veloce di una pozza ferma. Prendila dal centro della corrente. Evita l'acqua sotto pascoli, malghe e paesi: è quella più contaminata.", img = 'acqua_2' },
    { "Prefiltra", "Se è torbida, lasciala riposare nel pentolino e versa piano la parte limpida, oppure filtrala attraverso una bandana. Serve solo a togliere la terra: non la rende sicura.", img = 'acqua_3' },
    { "Rendila sicura", "Bollire è il metodo più sicuro: un minuto di bollore pieno, tre minuti sopra i 2000 metri. Oppure il filtro (non ferma i virus) o le pastiglie (aspetta il tempo scritto sulla confezione).", img = 'acqua_4' },
    { "Rugiada e neve", "All'alba passa una maglietta sull'erba bagnata e strizzala nel pentolino. La neve va sciolta, mai mangiata: raffredda il corpo. Metti un fondo d'acqua nel pentolino prima della neve, o lo bruci.", img = 'acqua_5' },
    { "Quanta bere", "2-3 litri al giorno, di più col caldo o in salita. Bevi poco e spesso. Se l'urina è scura, stai bevendo troppo poco.", img = 'acqua_6' },
  } },

{ id = 'nord', titolo = 'Trovare il nord', sez = 'sopravvivenza', livello = 1, tempo = '20 min',
  intro = "Senza bussola: con l'ombra di un bastone, con l'orologio o con le stelle. Il muschio sugli alberi non è affidabile.",
  serve = "Un bastone dritto lungo un metro, due sassi.",
  passi = {
    { "Pianta il bastone", "Su un terreno piano, in pieno sole, pianta un bastone dritto in verticale.", img = 'nord1' },
    { "Segna la prima ombra", "Metti un sasso esattamente sulla punta dell'ombra.", img = 'nord2' },
    { "Aspetta e segna ancora", "Aspetta 15-20 minuti. L'ombra si è spostata: metti un secondo sasso sulla nuova punta.", img = 'nord3' },
    { "Leggi la direzione", "La linea dal primo al secondo sasso va da ovest a est. Mettiti con il primo sasso a sinistra e il secondo a destra: davanti a te c'è il nord.", img = 'nord4' },
    { "Con l'orologio a lancette", "Tieni l'orologio piatto e punta la lancetta delle ore verso il sole. A metà tra la lancetta delle ore e le 12 c'è il sud (con l'ora legale usa l'1 invece delle 12). Il nord è dalla parte opposta.", img = 'nord_5' },
    { "Con le stelle", "Trova il Grande Carro. Prendi le due stelle del bordo esterno del carro e prolunga la loro distanza circa 5 volte: arrivi alla Stella Polare, l'unica che non si muove. Sotto di lei, all'orizzonte, c'è il nord.", img = 'nord_6' },
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
    { "Destra sopra sinistra", "Incrocia il capo destro sopra il sinistro e fallo passare sotto: è il primo mezzo nodo.", img = 'piano_1' },
    { "Sinistra sopra destra", "Ora il capo che è a sinistra va sopra quello di destra, e poi sotto.", img = 'piano_2' },
    { "Controlla", "I due capi devono uscire dallo stesso lato del nodo, paralleli alle loro parti. Se escono storti hai fatto il nodo della nonna, che scivola: rifallo.", img = 'piano_3' },
    { "Sciogli", "Si scioglie tirando uno dei capi indietro verso la sua parte: il nodo si rovescia e scorre via.", img = 'piano_4' },
  } },

{ id = 'parlato', titolo = 'Nodo parlato', sez = 'nodi', livello = 1, tempo = '2 min',
  intro = "Per legare velocemente una corda a un palo o a un ramo, e regolarne la lunghezza.",
  attenzione = "Su un palo liscio o con strappi può scivolare: aggiungi un mezzo collo con il capo intorno alla parte fissa.",
  passi = {
    { "Primo giro", "Passa la corda intorno al palo, da davanti a dietro, e riportala davanti.", img = 'parlato_1' },
    { "La croce", "Fai passare il capo sopra la parte fissa: si forma una X.", img = 'parlato_2' },
    { "Secondo giro", "Fai un secondo giro intorno al palo, sotto al primo, sempre nella stessa direzione.", img = 'parlato_3' },
    { "Infila", "Passa il capo sotto il secondo giro, al centro della X: esce accanto alla parte fissa, in direzione opposta.", img = 'parlato_4' },
    { "Stringi", "Tira i due capi in direzioni opposte. Le due spire devono essere affiancate e la X al centro.", img = 'parlato_5' },
  } },

{ id = 'tendicavo', titolo = 'Tendicavo', sez = 'nodi', livello = 2, tempo = '3 min',
  intro = "Il nodo dei tiranti: scorre per tendere la corda e si blocca da solo sotto tensione. Per i tiranti della tenda e del tarp.",
  passi = {
    { "Intorno al picchetto", "Passa il capo intorno al picchetto (o all'albero) e riportalo verso la parte tesa della corda, lasciando un'asola.", img = 'tendicavo_1' },
    { "Due giri dentro", "Avvolgi il capo due volte intorno alla parte tesa, lavorando verso il picchetto, dentro l'asola.", img = 'tendicavo_2' },
    { "Un giro fuori", "Porta il capo oltre i due giri, fuori dall'asola, e fai un altro giro intorno alla parte tesa. Infila il capo sotto quest'ultimo giro.", img = 'tendicavo_3' },
    { "Tendi", "Stringi i giri. Fai scorrere il nodo lungo la corda per tendere: sotto carico si blocca. Per allentare, fallo scorrere con le dita.", img = 'tendicavo_4' },
  } },

{ id = 'prusik', titolo = 'Prusik', sez = 'nodi', livello = 3, tempo = '3 min',
  intro = "Un nodo autobloccante fatto con un anello di cordino intorno a una corda più grossa: scorre quando è scarico e si blocca sotto carico. Per appendere oggetti alla dorsale del tarp e spostarli.",
  serve = "Un anello di cordino di diametro più piccolo della corda (circa i due terzi).",
  attenzione = "Per l'arrampicata e la risalita su corda serve un corso: non imparare dalle immagini.",
  passi = {
    { "L'anello", "Chiudi un pezzo di cordino lungo un metro con un nodo inglese doppio: ottieni un anello.", img = 'prusik_1' },
    { "Il primo giro", "Appoggia l'anello dietro la corda, passa un capo dell'anello davanti e infilalo dentro l'altro capo.", img = 'prusik_2' },
    { "Tre giri", "Ripeti, avvolgendo l'anello intorno alla corda e dentro sé stesso, per tre giri. Le spire devono restare dentro l'anello, ordinate e parallele.", img = 'prusik_3' },
    { "Prova", "Sistema le spire senza incroci. Tira l'anello: deve bloccarsi. Spingi il nodo con la mano: deve scorrere. Se scivola sotto carico, aggiungi un giro.", img = 'prusik_4' },
  } },

------------------------------------------------------------------ trappole

{ id = 'regole', titolo = 'Prima di tutto: la legge', sez = 'trappole', livello = 1, tempo = '3 min',
  intro = "Leggi questa pagina prima delle altre trappole.",
  passi = {
    { "In Italia è vietato", "Cacciare con trappole, lacci e trabocchetti è vietato (legge 157 del 1992): è bracconaggio, punito anche con l'arresto. Nei parchi è vietato anche disturbare gli animali. La pesca richiede la licenza e ha regole precise.", img = 'regole_1' },
    { "Solo in una vera emergenza", "Queste tecniche sono per una situazione in cui la vita è in pericolo e non c'è altro modo. In Italia, in una montagna normale, i soccorsi arrivano in ore o giorni: per sopravvivere non serve cacciare. Chiama il 112.", img = 'regole_2' },
    { "Il cibo viene per ultimo", "Si resiste settimane senza mangiare. Riparo, calore, acqua e farsi trovare sono molto più importanti, e cacciare consuma energie e acqua.", img = 'regole_3' },
    { "Sicurezza e rispetto", "Una trappola può ferire una persona, un cane, un animale protetto. Non lasciarla mai incustodita, segna dove l'hai messa e smontala sempre prima di andare via.", img = 'regole_4' },
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
    { "Trova il passaggio", "Cerca i sentieri degli animali: tracce, erba schiacciata, passaggi sotto i cespugli, vicino all'acqua. Il laccio va dove l'animale è costretto a passare.", img = 'laccio_1' },
    { "Il cappio", "Fai un piccolo occhiello a un capo del filo e passa l'altro capo dentro: ottieni un cappio che si stringe scorrendo.", img = 'laccio_2' },
    { "La misura", "Il cappio deve essere largo come una mano per un animale piccolo, con il bordo basso a un pugno da terra.", img = 'laccio_3' },
    { "Fissalo", "Lega l'altro capo a un paletto ben piantato o a un ramo robusto. Metti dei rametti ai lati del passaggio perché l'animale passi proprio nel cappio.", img = 'laccio_4' },
    { "Controlla e smonta", "Controlla almeno due volte al giorno. Quando te ne vai, smonta tutto e porta via il filo.", img = 'laccio_5' },
  } },

{ id = 'pesca', titolo = 'Pesca di sopravvivenza', sez = 'trappole', livello = 2, tempo = '',
  attenzione = "In Italia pescare richiede la licenza e rispetta stagioni e misure: questo è solo per l'emergenza.",
  intro = "Nei torrenti di montagna ci sono trote: con una lenza e un amo di fortuna si può pescare senza canna.",
  serve = "Filo o cordino sottile, un amo (o una spina, un osso, un fermaglio), esca.",
  passi = {
    { "L'amo di fortuna", "Una spina di rovo o di biancospino legata a un rametto, un fermaglio piegato, oppure un bastoncino appuntito ai due capi legato al centro: l'esca lo nasconde e il pesce lo inghiotte di traverso.", img = 'pesca_1' },
    { "L'esca", "Vermi e larve sotto i sassi e nel legno marcio, insetti, cavallette. Mettila in modo che copra la punta.", img = 'pesca_2' },
    { "Dove pescare", "Le trote stanno ferme dove la corrente rallenta: dietro i massi, nelle buche profonde, sotto le rive. Avvicinati piano, senza farti vedere e senza ombra sull'acqua.", img = 'pesca_3' },
    { "La lenza", "Lega la lenza a un ramo lungo e flessibile. Lascia che l'esca scenda con la corrente verso la buca.", img = 'pesca_4' },
    { "Linee fisse", "Più lenze legate a rami sulla riva pescano mentre fai altro. Controllale spesso e toglile tutte prima di andare via.", img = 'pesca_5' },
  } },


------------------------------------------------------------------ bushcraft (altre)

{ id = 'tipifuoco', titolo = 'Tipi di fuoco', sez = 'sopravvivenza', livello = 1, tempo = '',
  intro = "Ogni fuoco ha il suo scopo: accendere in fretta, cucinare, scaldare tutta la notte. La preparazione (esca, legnetti, legna) è sempre la stessa.",
  attenzione = "Valgono le stesse regole di 'Accendere il fuoco': divieti regionali, terra nuda, niente rami sopra, sempre spento con acqua.",
  passi = {
    { "Tepee (a cono)", "Il più facile da accendere: i legnetti a cono intorno all'esca. Brucia alto e veloce, buono per partire e per far bollire l'acqua in fretta. Quando crolla diventa una base di brace.", img = 'tipi1' },
    { "Capanna (a catasta)", "Due legni paralleli, poi due di traverso sopra, e così via come una capanna di tronchi, con l'esca e un piccolo tepee al centro. Brucia regolare e lascia tanta brace: il migliore per cucinare.", img = 'tipi2' },
    { "Stella", "Quattro o cinque tronchi grossi a raggiera con le punte che si toccano al centro, dove arde il fuoco. Man mano che bruciano li spingi verso il centro. Consuma poco: dura tutta la notte con poca sorveglianza.", img = 'tipi3' },
    { "Con il riflettore", "Dietro il fuoco una parete di tronchi verdi appoggiati a due paletti, o una roccia: rimanda il calore verso il riparo. Il fuoco va a un passo e mezzo dall'entrata del riparo.", img = 'tipifuoco_4' },
    { "Quale scegliere", "Per partire: tepee. Per cucinare: capanna, e si cucina sulla brace, non sulla fiamma. Per la notte: stella, con un riflettore. Con il vento forte: il fuoco Dakota (vedi il tutorial).", img = 'tipifuoco_5' },
  } },

{ id = 'dakota', titolo = 'Il fuoco Dakota', sez = 'sopravvivenza', livello = 2, tempo = '30 min',
  intro = "Un fuoco dentro una buca, con un tunnel per l'aria: col vento non si spegne, fa poco fumo, si vede poco e consuma poca legna. Ottimo per cucinare.",
  serve = "Un coltello o una paletta, un pentolino, legna corta e sottile.",
  attenzione = "Non scavare tra le radici (il fuoco può covare sottoterra) né sulla torba. Valgono i divieti sui fuochi.",
  passi = {
    { "La buca del fuoco", "Su terreno compatto, senza radici né sassi, togli la zolla d'erba e mettila da parte. Scava una buca larga e profonda come un avambraccio (25-30 cm), con le pareti dritte.", img = 'dakota1' },
    { "Il tunnel dell'aria", "A una spanna dalla prima, dalla parte da cui arriva il vento, scava una buca più stretta e falla scendere in diagonale fino a sbucare sul fondo della prima. Da lì entra l'aria.", img = 'dakota2' },
    { "Accendi sul fondo", "Metti l'esca e i legnetti sul fondo della buca grande e accendi. Il tiraggio del tunnel ravviva la fiamma da solo: alimenta dall'alto con legna corta.", img = 'dakota3' },
    { "Cucina", "Appoggia il pentolino su due bastoni verdi messi di traverso sulla buca. La fiamma resta concentrata sotto il fondo.", img = 'dakota_4' },
    { "Chiudi", "Spegni con acqua, mescola, ancora acqua. Riempi le buche con la terra e rimetti la zolla: non resta traccia.", img = 'dakota_5' },
  } },

{ id = 'archetto', titolo = "Fuoco ad archetto", sez = 'sopravvivenza', livello = 3, tempo = '1-2 ore',
  intro = "Accendere il fuoco per attrito, senza fiammiferi: un fuso che gira su una tavoletta finché la polvere di legno diventa brace. È la tecnica più impegnativa: provala a casa prima di averne bisogno.",
  serve = "Coltello, cordino, legno secco morto e tenero (tiglio, salice, pioppo, nocciolo), un sasso o un legno duro con un incavo.",
  passi = {
    { "I quattro pezzi", "Tavoletta: spessa 2 cm, larga tre dita. Fuso: dritto, lungo una spanna, spesso un pollice, dello stesso legno, la punta in alto appuntita e quella in basso arrotondata. Archetto: un ramo un po' curvo lungo come il braccio, con il cordino legato non troppo teso. Impugnatura: un sasso o un legno duro con un incavo.", img = 'archetto1' },
    { "Il primo foro", "Con la punta del coltello fai un piccolo incavo sulla tavoletta, a un dito dal bordo. Lì appoggerai il fuso.", img = 'archetto_2' },
    { "La posizione", "In ginocchio sul destro, il piede sinistro sulla tavoletta vicino al foro. Avvolgi una volta il cordino intorno al fuso. Il fuso verticale nel foro, l'impugnatura sopra, il polso sinistro bloccato contro lo stinco.", img = 'archetto2' },
    { "Brucia il foro", "Muovi l'archetto avanti e indietro con colpi lunghi e regolari, usando tutto il cordino. Quando il foro è nero e largo come il fuso, fermati.", img = 'archetto_4' },
    { "La tacca", "Taglia una tacca a V dal bordo della tavoletta fino al centro del foro, larga come un ottavo di torta. Lì si raccoglie la polvere che diventerà brace. Metti sotto un pezzo di corteccia.", img = 'archetto_5' },
    { "La brace", "Ricomincia: prima piano, poi più veloce e premendo di più. Quando esce tanto fumo continua per altri 20 colpi. Se la polvere nera nella tacca continua a fumare da sola, hai la brace.", img = 'archetto_6' },
    { "Dal carbone alla fiamma", "Sposta la brace con la corteccia dentro il nido d'esca, chiudilo intorno e soffia piano e a lungo finché prende fiamma. Poi il fuoco come al solito.", img = 'archetto_7' },
  } },

{ id = 'batoning', titolo = 'Spaccare la legna col coltello', sez = 'sopravvivenza', livello = 2, tempo = '10 min',
  intro = "Il batoning: spaccare un ciocco battendo il dorso del coltello con un bastone. Serve per arrivare al legno asciutto quando fuori è tutto bagnato.",
  serve = "Un coltello a lama fissa robusto (lama e manico in un pezzo solo), un bastone duro da battere.",
  attenzione = "Mai con un coltello pieghevole. Legna più sottile della lunghezza della lama e senza nodi. Le gambe fuori dalla traiettoria.",
  passi = {
    { "Prepara il ceppo", "Metti un ciocco largo come base. Appoggia sopra in piedi il pezzo da spaccare, lungo al massimo come il tuo avambraccio.", img = 'batoning_1' },
    { "Appoggia la lama", "Appoggia il filo della lama al centro della testa del pezzo, dritta, con la punta che sporge dall'altra parte di qualche dito.", img = 'baton1' },
    { "Batti", "Con il bastone batti il dorso della lama vicino al manico, con colpi decisi. Quando la lama è entrata, batti sulla punta che sporge, così scende dritta.", img = 'baton2' },
    { "Apri", "Continua finché il pezzo si divide. Se la lama si incastra in un pezzo lungo, infila un cuneo di legno nella spaccatura e batti quello.", img = 'batoning_4' },
    { "Ancora a metà", "Spacca ogni metà in due e poi ancora: i quarti interni sono asciutti anche dopo giorni di pioggia. Ne ricavi esca, legnetti e il bastoncino piumato.", img = 'batoning_5' },
  } },

{ id = 'cordame', titolo = 'Cordino di fibre naturali', sez = 'sopravvivenza', livello = 3, tempo = '1 ora',
  intro = "Con le fibre di ortica, di tiglio o di erba lunga si intreccia un cordino robusto: il metodo della torsione inversa, usato in tutto il mondo da migliaia di anni.",
  serve = "Fibre lunghe e flessibili: steli di ortica secchi, corteccia interna di tiglio o salice, erbe lunghe.",
  passi = {
    { "Prepara le fibre", "Ortica: schiaccia lo stelo secco per il lungo, apri, togli la parte legnosa e tieni le fibre esterne. Tiglio: la corteccia interna, lasciata a bagno qualche giorno, si sfoglia in strisce.", img = 'cordame_1' },
    { "Piega il fascio", "Prendi un fascio di fibre spesso come un fiammifero e piegalo non a metà, ma a un terzo della lunghezza: le giunte cadranno in punti diversi.", img = 'cordame_2' },
    { "Il primo nodino", "Tieni la piega tra pollice e indice e torci il ramo di sopra lontano da te finché si arriccia e fa un piccolo nodo alla piega.", img = 'cordame_3' },
    { "Torci e avvolgi", "Torci il ramo di sopra lontano da te, poi portalo sopra l'altro verso di te. Ora quello che era sotto è sopra: torci, avvolgi, e ripeti. Le due torsioni opposte si bloccano a vicenda.", img = 'cordame_4' },
    { "Allunga", "Quando un ramo sta per finire, sovrapponi un nuovo ciuffo di fibre per qualche centimetro e continua a torcerli insieme.", img = 'cordame_5' },
    { "Finisci e prova", "Chiudi con un nodo semplice e taglia i ciuffi sporgenti. Prova a tirare: un buon cordino non si srotola e regge molto più di quanto sembri.", img = 'cordame_6' },
  } },

{ id = 'gancio', titolo = 'Il gancio per il pentolino', sez = 'sopravvivenza', livello = 2, tempo = '30 min',
  intro = "Appendere il pentolino sopra il fuoco con legni intagliati: due forcelle, una traversa e un gancio regolabile.",
  serve = "Coltello, legno verde (che brucia poco): due rami a forcella, una traversa, un ramo con un moncone.",
  passi = {
    { "Le due forcelle", "Taglia due rami verdi con una forcella in cima, lunghi come il tuo braccio. Appuntiscili in basso e piantali ai due lati del fuoco, ben dritti.", img = 'gancio1' },
    { "La traversa", "Un ramo verde dritto e robusto appoggiato nelle due forcelle, sopra il fuoco.", img = 'gancio_2' },
    { "Il gancio", "Cerca un ramo con un moncone laterale: taglia il moncone lungo due dita e inclinato verso l'alto. Quello è il gancio dove si appende il manico del pentolino.", img = 'gancio_3' },
    { "Regolare l'altezza", "In cima al gancio intaglia una tacca e appendilo alla traversa. Per avere più altezze, fai due o tre tacche a distanze diverse: più in basso cuoce, più in alto tiene in caldo.", img = 'gancio_4' },
    { "Controlla", "Il legno verde col tempo si secca e può bruciare: guardalo ogni tanto e cambia la traversa se si annerisce.", img = 'gancio_5' },
  } },

------------------------------------------------------------------ nodi e legature (altri)

{ id = 'mezzicolli', titolo = 'Due mezzi colli', sez = 'nodi', livello = 1, tempo = '1 min',
  intro = "Il modo più semplice per fissare una corda a un albero, a un palo o a un anello, e si scioglie subito. Chiude anche molti altri nodi.",
  passi = {
    { "Intorno al palo", "Passa il capo intorno all'albero o al palo, e riportalo verso la parte fissa.", img = 'mezzicolli_1' },
    { "Primo mezzo collo", "Passa il capo intorno alla parte fissa e infilalo dentro l'occhiello che si è formato.", img = 'mezzicolli_2' },
    { "Secondo mezzo collo", "Ripeti uguale, più in là sulla parte fissa, girando nello stesso verso. Se giri al contrario il nodo scivola.", img = 'mezzicolli_3' },
    { "Stringi", "Fai scorrere i due mezzi colli contro il palo e tira la parte fissa.", img = 'mezzicolli_4' },
  } },

{ id = 'camionista', titolo = 'Nodo del camionista', sez = 'nodi', livello = 2, tempo = '3 min',
  intro = "Tende una corda con la forza di una carrucola: per la dorsale del tarp, per legare un carico. Si scioglie con uno strattone.",
  passi = {
    { "Il primo ancoraggio", "Lega un capo della corda al primo albero con una gassa d'amante o due mezzi colli.", img = 'camionista_1' },
    { "L'asola a metà", "Più o meno a due terzi della distanza, fai un'asola sulla corda: un occhiello ripiegato chiuso con un mezzo nodo (o un'asola a farfalla se la sai fare).", img = 'camionista_2' },
    { "Intorno al secondo albero", "Passa il capo libero intorno al secondo albero e riportalo indietro, facendolo entrare nell'asola.", img = 'camionista_3' },
    { "Tira", "Tira il capo verso il secondo albero: l'asola fa da carrucola e la corda si tende il doppio di quanto tireresti a mano.", img = 'camionista_4' },
    { "Blocca", "Tenendo la tensione con una mano, chiudi con due mezzi colli intorno ai due rami della corda, sotto l'asola. Per scioglierlo veloce, fai l'ultimo mezzo collo con un'ansa (ganciato).", img = 'camionista_5' },
  } },

{ id = 'bandiera', titolo = 'Nodo di bandiera', sez = 'nodi', livello = 1, tempo = '2 min',
  intro = "Per unire due corde, anche di spessore diverso: quella che avanza del tarp con un cordino, due corde corte per farne una lunga.",
  passi = {
    { "L'ansa", "Con la corda più grossa fai un'ansa, piegandola a U.", img = 'bandiera_1' },
    { "Entra", "Passa il capo della corda sottile dentro l'ansa, da sotto.", img = 'bandiera_2' },
    { "Gira dietro", "Portalo intorno a tutti e due i rami dell'ansa, passando dietro.", img = 'bandiera_3' },
    { "Sotto sé stesso", "Infila il capo sotto sé stesso, ma sopra l'ansa: non dentro l'ansa.", img = 'bandiera_4' },
    { "Stringi e controlla", "Stringi tirando le parti lunghe. I due capi corti devono uscire dallo stesso lato. Per più sicurezza fai un secondo giro (nodo di bandiera doppio).", img = 'bandiera_5' },
  } },

{ id = 'quadra', titolo = 'Legatura quadra', sez = 'nodi', livello = 2, tempo = '5 min',
  intro = "Unisce due pali incrociati ad angolo retto: per tavoli, rastrelliere, le strutture dei ripari. È la legatura più usata.",
  serve = "Due pali, 3-4 metri di cordino.",
  passi = {
    { "Il nodo d'inizio", "Fai un nodo parlato sul palo verticale, appena sotto il punto dove appoggerai la traversa. Avvolgi il capo corto intorno alla corda per non lasciarlo libero.", img = 'quadra1' },
    { "I giri", "Porta la corda sopra la traversa, dietro il palo verticale, sotto la traversa dall'altra parte e di nuovo dietro il verticale. Ripeti 3-4 volte tirando forte: sulla traversa i giri vanno uno fuori dall'altro, sul verticale uno dentro l'altro.", img = 'quadra2' },
    { "Le strozzature", "Ora gira la corda tra i due pali, intorno a tutti i giri: 2-3 strozzature, tirate più che puoi. Sono loro che rendono la legatura rigida.", img = 'quadra3' },
    { "Chiudi", "Chiudi con un nodo parlato sulla traversa. La legatura non deve muoversi se scuoti i pali.", img = 'quadra_4' },
  } },

{ id = 'diagonale', titolo = 'Legatura diagonale', sez = 'nodi', livello = 2, tempo = '5 min',
  intro = "Per due pali incrociati che tendono ad allontanarsi, come le diagonali di un telaio. Li tira uno contro l'altro.",
  serve = "Due pali, 3-4 metri di cordino.",
  passi = {
    { "Il nodo di legname", "Intorno ai due pali, nel punto dove si incrociano, fai un nodo di legname: passa la corda intorno a entrambi, gira il capo intorno alla parte fissa e poi su sé stesso tre volte. Tirando, stringe i pali insieme.", img = 'diagonale_1' },
    { "Tre giri su una diagonale", "Avvolgi la corda tre volte intorno all'incrocio in diagonale, nello stesso verso del nodo di legname.", img = 'diagonale_2' },
    { "Tre giri sull'altra", "Ora tre giri sull'altra diagonale, a formare una X.", img = 'diagonale_3' },
    { "Strozzature e chiusura", "Due strozzature tra i pali, intorno ai giri, e chiudi con un nodo parlato su un palo.", img = 'diagonale_4' },
  } },

{ id = 'treppiede', titolo = 'Legatura a treppiede', sez = 'nodi', livello = 2, tempo = '10 min',
  intro = "Tre pali legati in cima che si aprono a treppiede: per appendere il pentolino sul fuoco, fare la struttura di un riparo o tenere alto lo zaino.",
  serve = "Tre pali di lunghezza simile, 3-4 metri di cordino.",
  passi = {
    { "Affianca i pali", "Metti i tre pali affiancati a terra, con quello in mezzo rovesciato (la punta grossa dalla parte opposta). Le cime sporgono tutte di una spanna oltre la legatura.", img = 'treppiede_1' },
    { "Inizia", "Fai un nodo parlato su un palo esterno, vicino alla cima.", img = 'treppiede_2' },
    { "I giri", "Avvolgi la corda intorno ai tre pali per 6-8 giri, passando sopra e sotto i pali alternati, senza stringere troppo: devono poter ruotare per aprirsi.", img = 'treppiede_3' },
    { "Le strozzature", "Fai due strozzature in ciascuno dei due spazi tra i pali, intorno ai giri.", img = 'treppiede_4' },
    { "Chiudi e apri", "Chiudi con un nodo parlato sull'altro palo esterno. Alza il treppiede e apri le gambe, incrociando quello di mezzo: la legatura si stringe da sola.", img = 'treppiede1' },
  } },

{ id = 'cesoia', titolo = 'Legatura a cesoia', sez = 'nodi', livello = 2, tempo = '5 min',
  intro = "Due pali legati in cima che si aprono ad A: il bipode per la dorsale di un riparo quando mancano gli alberi, o la testata di una capanna.",
  serve = "Due pali, 2-3 metri di cordino.",
  passi = {
    { "Affianca", "Metti i due pali paralleli, uno accanto all'altro.", img = 'cesoia_1' },
    { "Inizia", "Fai un nodo parlato (o di legname) su un palo, a una spanna dalle cime.", img = 'cesoia_2' },
    { "I giri", "Avvolgi 6-8 giri intorno ai due pali insieme, non troppo stretti.", img = 'cesoia_3' },
    { "Strozzature e chiusura", "Due strozzature tra i pali e un nodo parlato sull'altro palo.", img = 'cesoia_4' },
    { "Apri ad A", "Apri i pali a forbice: la legatura si stringe. Con due bipodi e un palo di traverso hai il telaio di un riparo.", img = 'cesoia_5' },
  } },

------------------------------------------------------------------ tarp (altri)

{ id = 'tarp_corde', titolo = 'Preparare il tarp', sez = 'tarp', livello = 1, tempo = '30 min',
  intro = "Un tarp ben preparato a casa si monta in cinque minuti anche sotto la pioggia, con i guanti, al buio.",
  serve = "Il tarp, 15 m di cordino da 3-4 mm, 8 picchetti, 2 anelli di cordino sottile per i prusik.",
  passi = {
    { "Un cordino per ogni occhiello", "Lega ai quattro angoli e agli occhielli dei lati un cordino da 2 metri con una gassa d'amante. Avvolgili e tienili legati: non si aggrovigliano.", img = 'tarp_corde_1' },
    { "La dorsale", "Un cordino lungo 10-12 metri per la dorsale. Un capo già pronto con una gassa d'amante da passare intorno all'albero.", img = 'tarp_corde_2' },
    { "I prusik", "Sulla dorsale metti due anelli di cordino sottile con il nodo prusik (vedi Nodi). Agganci lì gli angoli del tarp e li fai scorrere per tenderlo.", img = 'tarp_corde_3' },
    { "Tendicavi ai picchetti", "Ai capi dei cordini degli angoli bassi farai un tendicavo intorno al picchetto: così regoli la tensione senza slegare niente.", img = 'tarp_corde_4' },
    { "Riporlo", "Arrotola il tarp sulla dorsale, con i cordini dentro. La prossima volta srotoli e tiri.", img = 'tarp_corde_5' },
  } },

{ id = 'tarp_a', titolo = 'Tarp a capanna (A)', sez = 'tarp', livello = 1, tempo = '15 min',
  intro = "Il tarp a cavallo della dorsale, i due lati a terra: una tenda senza fondo. Ripara da pioggia e vento da due lati.",
  serve = "Tarp, dorsale, 6 picchetti.",
  passi = {
    { "La dorsale bassa", "Tendi la dorsale tra due alberi a un metro da terra (o poco più alta della tua spalla da seduto), con il camionista per tenderla bene.", img = 'tarp_a_1' },
    { "A cavallo", "Appoggia il tarp a metà sulla dorsale, con la linea centrale esattamente sopra la corda. Fissa i due occhielli centrali con i prusik.", img = 'tarpa1' },
    { "Picchetta i lati", "Tira un lato verso terra e picchetta i due angoli, poi l'altro lato. Stessa inclinazione a destra e a sinistra.", img = 'tarp_a_3' },
    { "Tendi", "Regola con i tendicavi finché il telo è liscio. Se un'estremità è verso il vento, abbassala o chiudila con un sacco." , img = 'tarpa2' },
  } },

{ id = 'tarp_diamante', titolo = 'Tarp a diamante', sez = 'tarp', livello = 2, tempo = '10 min',
  intro = "Il tarp girato a rombo, con un solo albero: un angolo in alto, quello opposto a terra. Veloce, leggero, perfetto per una persona.",
  serve = "Tarp quadrato, un albero, 3-4 picchetti, un bastone (facoltativo).",
  passi = {
    { "L'angolo alto", "Lega un angolo del tarp all'albero, all'altezza del petto, con due mezzi colli o un tendicavo.", img = 'diam1' },
    { "L'angolo basso", "Tira l'angolo opposto lontano dall'albero e picchettalo a terra, dalla parte da cui arriva il vento.", img = 'tarp_diamante_2' },
    { "I due lati", "Tendi i due angoli laterali verso il basso e in fuori, con i cordini ai picchetti. Più li abbassi, più ripara; più li alzi, più spazio hai.", img = 'diam2' },
    { "Dentro", "Si dorme con la testa verso l'albero, dove il telo è più alto. Con un bastone sotto il centro di un lato guadagni spazio.", img = 'tarp_diamante_4' },
  } },

{ id = 'tarp_vomere', titolo = 'Tarp a punta (piramide)', sez = 'tarp', livello = 2, tempo = '15 min',
  intro = "Il 'vomere': tre angoli a terra e uno in alto, appeso a un albero o a un bastone. Chiuso su tre lati, tiene il vento meglio di tutti.",
  serve = "Tarp quadrato, 4 picchetti, un albero o un bastone lungo 1,3 m.",
  passi = {
    { "L'angolo in alto", "Lega un angolo del tarp all'albero a un metro e mezzo, o appoggialo sulla punta di un bastone piantato dritto e tenuto da un tirante.", img = 'vomere1' },
    { "I due angoli posteriori", "Picchetta i due angoli adiacenti a terra, ben tesi, dalla parte del vento: formano la schiena del riparo.", img = 'tarp_vomere_2' },
    { "L'angolo davanti", "Il quarto angolo è l'entrata: picchettalo a terra per chiuderti dentro, oppure alzalo con un bastone per fare una veranda.", img = 'vomere2' },
    { "Sistema", "Tendi i lati con gli occhielli intermedi. Dentro c'è spazio per una persona e lo zaino.", img = 'tarp_vomere_4' },
  } },

{ id = 'tarp_cucina', titolo = 'Tettoia per stare in piedi', sez = 'tarp', livello = 1, tempo = '15 min',
  intro = "Il tarp alto e quasi piatto, inclinato: una tettoia sotto cui cucinare, mangiare e aspettare che smetta di piovere.",
  serve = "Tarp, dorsale, 4 picchetti, due bastoni lunghi 1,5 m (facoltativi).",
  attenzione = "Il fornelletto sotto il tarp solo se il telo è alto e c'è tanta aria. Mai il fuoco sotto il telo.",
  passi = {
    { "La dorsale alta", "Tendi la dorsale tra due alberi più in alto della tua testa.", img = 'tarp_cucina_1' },
    { "Il lato alto", "Appendi un lato lungo del tarp alla dorsale con i prusik, così che il telo stia tutto da una parte.", img = 'tarp_cucina_2' },
    { "Il lato basso", "Tira l'altro lato in giù a un metro e mezzo, con i cordini ai picchetti. Deve pendere abbastanza da far scolare la pioggia da quel lato.", img = 'cucina1' },
    { "Più spazio", "Per alzare il lato basso, mettilo su due bastoni dritti tenuti da tiranti. Togli le pieghe: dove resta una conca, l'acqua si accumula e il telo cede.", img = 'tarp_cucina_4' },
  } },

{ id = 'tarp_pioggia', titolo = 'Tarp col brutto tempo', sez = 'tarp', livello = 2, tempo = '',
  intro = "Le regole per restare asciutti con la pioggia battente e il vento forte.",
  passi = {
    { "Il lato al vento", "Il lato basso e chiuso del tarp va sempre verso il vento. Se il vento gira, abbassa quel lato fino a terra.", img = 'tarp_pioggia_1' },
    { "Niente conche", "Il telo deve essere teso e in pendenza ovunque. Una conca piena d'acqua pesa chili e può strappare gli occhielli.", img = 'tarp_pioggia_2' },
    { "I gocciolatoi", "La pioggia scende lungo la dorsale e i cordini fino a te. Lega un pezzetto di cordino sulla dorsale appena fuori dal tarp, che pende: l'acqua gocciola lì e non entra.", img = 'tarp_pioggia_3' },
    { "Il terreno", "Mai in una conca o sotto una pendenza da cui scende l'acqua. Meglio un piccolo rilievo, con uno strato di frasche sotto il materassino.", img = 'tarp_pioggia_4' },
    { "Ritendi", "Il telo bagnato si allunga: dopo un'ora di pioggia ritendi i tendicavi. Di notte allentali un poco se fa molto freddo: il nylon gelato si accorcia.", img = 'tarp_pioggia_5' },
  } },

------------------------------------------------------------------ trappole (altre)

{ id = 'paiute', titolo = 'Trappola Paiute', sez = 'trappole', livello = 3, tempo = '1 ora',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietata. Le mani mai sotto la pietra mentre la armi.",
  intro = "Una variante della trappola a quattro che usa un cordino: scatta con un tocco leggerissimo ed è più sensibile. Inventata dal popolo Paiute del Nord America.",
  serve = "Coltello, quattro bastoncini, 20 cm di cordino, una pietra piatta pesante.",
  passi = {
    { "I pezzi", "Montante: 25 cm, verticale. Leva: 30 cm, appoggiata in cima al montante. Levetta: 5 cm, legata al cordino. Bastoncino dell'esca: lungo e sottile.", img = 'paiute_1' },
    { "La leva", "Lega il cordino a un capo della leva. Appoggia la leva in cima al montante: un capo regge il bordo della pietra, dall'altro pende il cordino.", img = 'paiute_2' },
    { "La levetta", "Lega la levetta all'altro capo del cordino e girala intorno al montante: il cordino tirato dalla leva la tiene premuta contro il montante.", img = 'paiute_3' },
    { "L'esca", "Incastra il bastoncino dell'esca tra la punta della levetta e il bordo della pietra. Un tocco all'esca libera la levetta, la leva scatta e la pietra cade.", img = 'paiute_4' },
    { "Prova e smonta", "Provala più volte con un ramo, da lontano. Controllala spesso e smontala sempre prima di andare via.", img = 'paiute_5' },
  } },

{ id = 'molla', titolo = 'Laccio a molla', sez = 'trappole', livello = 3, tempo = '40 min',
  attenzione = "Solo in una vera emergenza di sopravvivenza: in Italia è vietato. Un ramo in tensione può colpire il viso: lavora di lato.",
  intro = "Il laccio legato a un ramo flessibile piegato: quando scatta, il ramo si raddrizza e solleva la preda, che non scappa e resta lontano da altri animali.",
  serve = "Cordino, filo per il cappio, coltello, un alberello flessibile vicino al passaggio, due pioli.",
  passi = {
    { "Il grilletto", "Due pioli con una tacca ciascuno che si incastrano tra loro: uno piantato a terra con la tacca verso il basso, l'altro legato al cordino con la tacca verso l'alto.", img = 'molla_1' },
    { "La molla", "Lega il cordino alla cima di un alberello flessibile e piegalo verso il passaggio degli animali.", img = 'molla_2' },
    { "Arma", "Incastra il piolo del cordino sotto quello piantato: la tensione dell'alberello li tiene agganciati.", img = 'molla_3' },
    { "Il cappio", "Al piolo del cordino lega il laccio, aperto sul passaggio come nel tutorial 'Il laccio'. Quando l'animale tira il cappio, i pioli si sganciano.", img = 'molla_4' },
    { "Controlla e smonta", "Controlla almeno due volte al giorno. Prima di andare via, sgancia la molla con un bastone e smonta tutto.", img = 'molla_5' },
  } },

{ id = 'nassa', titolo = 'Nassa per pesci', sez = 'trappole', livello = 2, tempo = '30 min',
  attenzione = "In Italia pescare con le nasse è vietato senza permesso: solo per l'emergenza.",
  intro = "Una trappola a imbuto: il pesce entra seguendo l'esca e non trova più l'uscita. La versione più semplice si fa con una bottiglia di plastica.",
  serve = "Una bottiglia grande di plastica (o rami sottili e cordino), coltello, esca, sassi.",
  passi = {
    { "Taglia la bottiglia", "Taglia la bottiglia dove inizia a stringersi. Capovolgi la parte del collo e infilala dentro il corpo: diventa l'imbuto d'entrata.", img = 'nassa1' },
    { "Fissa e fora", "Fissa l'imbuto con qualche punto di cordino. Fai dei buchi nel corpo, così l'acqua passa e la bottiglia affonda.", img = 'nassa_2' },
    { "L'esca", "Dentro metti l'esca: vermi, pane, insetti.", img = 'nassa_3' },
    { "Posizionala", "In acqua bassa e calma, vicino alla riva, con l'imbuto verso valle: il pesce risale la corrente. Tienila ferma con dei sassi.", img = 'nassa_4' },
    { "Con i rami", "La versione grande si intreccia con rami sottili: un cono d'entrata dentro un cesto più lungo, legati con cordino. Il principio è lo stesso.", img = 'nassa_5' },
  } },


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
local TEC = require('tecniche')
local IMG = require('immagini')

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

-- le immagini decodificate, la più usata di recente in fondo; il lettore ne tiene al massimo 8
local cache, ordine = {}, {}
local MAX_IMG = 7
local function immagine(nome)
  local dove = nome and IMG[nome]
  if not dove then return nil end
  if cache[nome] ~= nil then
    for k, v in ipairs(ordine) do if v == nome then table.remove(ordine, k); break end end
    ordine[#ordine + 1] = nome
    return cache[nome] or nil
  end
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
  return #t.passi .. ' passi · ' .. LIVELLI[t.livello] .. (t.tempo ~= '' and (' · ' .. t.tempo) or '')
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
  local t = TEC[tut]
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
  local t = TEC[tut]
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
