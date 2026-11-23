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

__mods['ricette'] = function()
-- Le ricette di "Ai Fornelli".
--
-- Ogni ricetta:
--   id      nome breve (lettere minuscole), usato per le foto e i salvataggi: non cambiarlo
--   nome    titolo
--   chi     'leo' (Quaderno delle Ricette Sburrate di Chef Leo) o 'nonna' (i classici della nonna)
--   cat     'primi', 'secondi', 'zuppe', 'pizza' o 'dolci'
--   min     minuti in tutto (per il filtro "Sotto 30 min"), tempo = come si legge sullo schermo
--   diff    difficoltà da 1 a 5
--   porz    per quante persone (o pezzi) sono le dosi, unita = cosa si conta ('persone', 'tazze'...)
--   desc    due righe di presentazione
--   ing     { quantità, ingrediente }: i numeri tra graffe, {500}, cambiano con le porzioni
--   passi   { titolo, testo, t = minuti del timer (facoltativo) }
--   note    consigli
-- Solo lettere dell'alfabeto latino (accenti compresi): niente trattini lunghi o virgolette curve.

return {

------------------------------------------------------------------ Chef Leo

{ id = 'pizza', nome = 'Pizza con lievito madre', chi = 'leo', cat = 'pizza',
  min = 480, tempo = '2 giorni', diff = 4, porz = 4, unita = 'panetti',
  desc = "Lievitazione naturale, fatta con calma: dal rinfresco del lievito madre ai panetti soffici pronti da stendere.",
  ing = {
    { '{500} g', 'farina tipo 0 per pizza' },
    { '{7}-{8} g', 'sale fino' },
    { '{100} g', 'lievito madre rinfrescato e attivo' },
    { '{300} g', 'acqua se impasti a mano (60%)' },
    { '{350} g', "acqua con l'impastatrice (70%)" },
  },
  passi = {
    { 'Prepara la pallina da 50 g', "Il giorno prima unisci 1 cucchiaio di lievito madre, 2-3 cucchiai di farina e acqua quanto basta. Lavora fino a ottenere una pallina di circa 50 g." },
    { 'Incidi una croce e fai crescere', "Incidi una croce sulla superficie, copri la pallina e lasciala a temperatura ambiente finché triplica di volume. Può riposare tutta la notte: la croce rende più visibile la crescita." },
    { 'Il giorno dopo: porta a 100 g', "Riprendi la pallina, aggiungi acqua e farina e rimpasta fino a una pallina da 100 g. Aspetta che il lievito torni attivo e ben sviluppato prima di usarlo." },
    { 'Metti la farina', "Versa 500 g di farina tipo 0 per pizza in una ciotola capiente oppure nella vasca dell'impastatrice." },
    { 'Spezzetta il lievito madre', "Spezzetta i 100 g di lievito madre attivo e lavoralo con la farina, distribuendolo in tutto l'impasto." },
    { "Versa l'acqua poco per volta", "Unisci l'acqua gradualmente: 300 g (60%) se impasti a mano, 350 g (70%) con l'impastatrice. Continua a mescolare mentre la versi, senza metterla tutta insieme." },
    { 'Aggiungi il sale', "Quando l'impasto comincia a prendere forma, aggiungi 7-8 g di sale e continua a mescolare." },
    { "Impasta: all'inizio è appiccicoso", "All'inizio l'impasto sembra quasi liquido: è normale. Se non riesci a gestirlo fermati, lascialo riposare un poco e riprendi. Non aggiungere farina." },
    { 'Copri e attendi 40 minuti', "Copri l'impasto nella ciotola e lascialo riposare 40 minuti: la pausa aiuta la struttura e poi si lavora meglio.", t = 40 },
    { 'Afferra, solleva e sbatti', "Lavora l'impasto a dita aperte: afferralo dai lati, sollevalo e sbattilo sul piano. Ripeti finché comincia a staccarsi dal tavolo. Se serve, un'altra breve pausa, sempre senza farina." },
    { "Lavora dall'alto verso il basso", "Quando si stacca dal piano, ripiegalo dall'alto verso il basso dandogli tensione, finché ottieni un panetto liscio che non si appiccica più." },
    { 'Lievitazione principale', "Copri il panetto e lascialo riposare almeno 5-6 ore. Dipende dalla temperatura e dalla forza del lievito: vai avanti quando è visibilmente cresciuto e arioso.", t = 330 },
    { 'Forma i panetti', "Dividi l'impasto in 3 o 4 panetti e forma delle palline senza strapparle. A mano vengono circa 908 g (3 da 303 g o 4 da 227 g); con l'impastatrice circa 958 g (3 da 319 g o 4 da 240 g)." },
    { 'Lascia i panetti al caldo', "Copri i panetti con un canovaccio e lasciali riposare circa 1 ora al caldo: puoi scaldare il forno a 30-35 °C, spegnerlo e metterli dentro coperti.", t = 60 },
    { 'Stendi, condisci e cuoci', "Allarga i panetti dal centro verso l'esterno senza schiacciare il bordo. Condisci e cuoci nel forno al massimo (250-300 °C), con teglia, pietra o acciaio ben preriscaldati." },
  },
  note = {
    "Acqua: a mano al massimo 300 g (60%), con l'impastatrice 350 g (70%). Le percentuali sono sulla farina, escluso il lievito madre.",
    "Non correggere mai l'impasto con altra farina: fai una pausa e riprendi con la tecnica solleva e sbatti.",
    "Le 5-6 ore sono indicative: guarda la lievitazione, non solo l'orologio.",
    "Forno tiepido per i panetti: preriscalda a 30-35 °C e spegni prima di metterli dentro. Mai il canovaccio con resistenze o grill accesi.",
    "Con pietra o acciaio servono 40-60 minuti di preriscaldamento al massimo.",
  },
},

{ id = 'pastafredda', nome = 'Pasta fredda alle Nuvole Sburrate', chi = 'leo', cat = 'primi',
  min = 50, tempo = '20 min + freddo', diff = 1, porz = 2, unita = 'persone',
  desc = "Pennette, pomodorini freschi, bufala e una crema di Philadelphia vellutata. Con un ingrediente segreto, a piacere.",
  ing = {
    { '{180} g', 'pennette' },
    { '{250} g', 'pomodorini freschi' },
    { '{150} g', 'mozzarella di bufala' },
    { '{50} g', 'Philadelphia' },
    { '{1} cucchiaio', "olio extravergine d'oliva" },
    { 'q.b.', 'sale e pepe' },
    { 'a piacere', 'basilico fresco' },
    { '{1} cucchiaino', 'bicarbonato per lavare' },
    { 'a piacere', 'ingrediente segreto' },
  },
  passi = {
    { 'Lava i pomodorini', "Metti i pomodorini in una ciotola con acqua e un cucchiaino di bicarbonato. Lasciali 10 minuti, poi risciacquali bene con acqua potabile.", t = 10 },
    { 'Taglia i pomodorini', "Asciugali e tagliali a metà, o in quarti se sono grandi. Tienili da parte." },
    { 'Prepara la bufala', "Scola la mozzarella di bufala, tamponala delicatamente e dividila in bocconi. Lasciala sgocciolare perché non rilasci troppa acqua." },
    { 'Cuoci le pennette', "Cuocile in abbondante acqua salata, scolale al dente e raffreddale subito sotto l'acqua fredda. Scolale molto bene.", t = 10 },
    { 'Crea la crema', "In una ciotola amalgama il Philadelphia con l'olio, un pizzico di sale e il pepe. Se serve, un goccio di acqua di cottura fredda per renderla cremosa." },
    { 'Condisci e servi', "Unisci pennette, pomodorini e bufala alla crema e mescola piano. Aggiungi il basilico e per ultimo l'ingrediente segreto, quanto ne vuoi. Servi fresca." },
  },
  note = {
    "Per una pasta davvero sburrata, distribuisci bene la crema sulle pennette prima di aggiungere la bufala.",
    "L'ingrediente segreto è a discrezione dello chef.",
    "Tienila in frigorifero fino al momento di servire, non a lungo fuori.",
  },
},

{ id = 'bistecca', nome = 'Bistecca con patate al forno sburrate', chi = 'leo', cat = 'secondi',
  min = 60, tempo = '60 min', diff = 3, porz = 2, unita = 'persone',
  desc = "Bistecca ben rosolata e succosa, patate dorate e croccanti, e una generosa maionese fatta in casa: la sburrata.",
  ing = {
    { '{2} x 300-350 g', 'bistecche di manzo alte 2,5-3 cm' },
    { '{600} g', 'patate' },
    { '{2} spicchi', 'aglio' },
    { '{2} rametti', 'rosmarino fresco' },
    { '{3} cucchiai', "olio extravergine d'oliva" },
    { 'q.b.', 'sale e pepe' },
    { '{1}', 'tuorlo pastorizzato (maionese)' },
    { '{190} ml', 'olio di semi delicato (maionese)' },
    { '{1} cucchiaio', 'succo di limone o aceto (maionese)' },
  },
  passi = {
    { 'Prepara le patate', "Lava le patate e tagliale a spicchi simili. Per più croccantezza lasciale 20 minuti in acqua fredda, poi asciugale molto bene.", t = 20 },
    { 'Inforna fino a doratura', "Condiscile con 2 cucchiai d'olio, sale, pepe, aglio schiacciato e rosmarino. Un solo strato, forno statico a 210 °C per 35-45 minuti, girandole a metà.", t = 40 },
    { 'Prepara la bistecca', "Mentre cuociono le patate togli la carne dal frigorifero. Tamponala con carta da cucina, poco olio e pepe. Sala prima di cuocerla o appena dopo." },
    { 'Rosola la carne', "Scalda molto bene una padella pesante. Cuoci 2-4 minuti per lato secondo lo spessore (meglio con un termometro), poi lasciala riposare 5 minuti.", t = 3 },
    { 'La maionese (la sburrata)', "In un bicchiere stretto: tuorlo, limone o aceto e un pizzico di sale. Monta con frusta o mixer versando l'olio di semi prima a gocce, poi a filo, fino a una crema densa. Tienila al fresco." },
    { 'Impiatta e sburra', "Affetta la bistecca dopo il riposo e accompagnala con le patate appena sfornate. Maionese sulle patate, quanta ne vuoi, rosmarino e pepe. Servi subito." },
  },
  note = {
    "Patate croccanti: asciugale bene e non sovrapporle in teglia.",
    "La maionese è una salsa cruda: usa il tuorlo pastorizzato, tienila a 4 °C o meno e consumala entro 24 ore.",
    "La cottura della bistecca dipende da spessore e temperatura: il termometro non sbaglia.",
  },
},

{ id = 'burger', nome = 'Double Smash Burger alla Leo', chi = 'leo', cat = 'secondi',
  min = 45, tempo = '45 min', diff = 3, porz = 2, unita = 'burger',
  desc = "Doppio smash con la crosta, mozzarella filante, bufala o provola, cipolle dolci e pomodoro arrosto. La sburrata definitiva.",
  ing = {
    { '{320} g', 'manzo macinato 80/20' },
    { '{2}', 'bun brioche per hamburger' },
    { '{1}', 'pomodoro grande e sodo' },
    { '{1}', 'cipolla dolce grande' },
    { '{100} g', 'mozzarella per pizza asciutta' },
    { '{80} g', 'bufala scolata o provola affumicata' },
    { '{40} g', 'maionese' },
    { '{15} ml', 'olio extravergine' },
    { '{10} g', 'burro per tostare il pane' },
    { '{1} cucchiaino', 'origano o timo' },
    { 'q.b.', 'sale e pepe' },
  },
  passi = {
    { 'Prepara tutto', "Dividi il manzo in 4 palline da 80 g senza pressarle. Affetta sottile la mozzarella e asciugala. Scola la bufala per 20-30 minuti (o affetta la provola). Apri i bun e prepara la maionese." },
    { 'Pomodoro lento al forno', "Affetta il pomodoro a rondelle di 1 cm, asciugale e condiscile con olio, origano, sale e pepe. Forno statico a 190 °C per 20-25 minuti: devono asciugarsi, non diventare salsa.", t = 22 },
    { 'Cipolle dolci alla piastra', "Taglia la cipolla a mezzaluna e cuocila con poco olio a fuoco medio-basso per 12-18 minuti, mescolando ogni tanto. Un cucchiaio d'acqua solo se serve, niente zucchero.", t = 15 },
    { 'Smasha la carne', "Scalda molto bene la ghisa. Due palline per volta: schiacciale subito a 5-7 mm con una spatola robusta e un quadrato di carta forno. Sala e lascia fare la crosta 2-3 minuti, poi stacca e gira.", t = 3 },
    { 'Fai fondere la mozzarella', "Su ogni patty girato metti circa 25 g di mozzarella, copri un attimo con un coperchio e finisci la cottura: 71 °C al cuore. Intanto tosta i bun nel burro.", t = 2 },
    { 'Assembla il burger', "Maionese sul fondo e sul coperchio, due patty con la mozzarella fusa, la bufala o la provola, le cipolle e 1-2 fette di pomodoro. Chiudi senza schiacciare e servi subito." },
  },
  note = {
    "La mozzarella asciutta fonde sulla carne; la bufala va aggiunta fuori dal fuoco, ben scolata, per non bagnare il pane.",
    "Non schiacciare una seconda volta gli smash mentre cuociono: perderesti i succhi.",
    "Con la provola puoi metterla sopra la mozzarella negli ultimi istanti in piastra.",
  },
},

{ id = 'tazza', nome = 'Tazza Sburrata Gocciole e Panna', chi = 'leo', cat = 'dolci',
  min = 20, tempo = '20 min', diff = 1, porz = 2, unita = 'tazze',
  desc = "Latte caldo al pentolino, caffè filtrato appena macinato, Gocciole e una nuvola di panna. La quantità? A discrezione dello chef.",
  ing = {
    { '{500} ml', 'latte intero' },
    { '{28} g', 'caffè in chicchi' },
    { '{450} ml', 'acqua calda per il filtro' },
    { 'a discrezione', 'Gocciole' },
    { 'a discrezione', 'panna fresca montata' },
    { 'facoltativi', 'zucchero, cacao, scaglie di cioccolato' },
  },
  passi = {
    { 'Ingredienti pronti', "Prepara latte, caffè in chicchi, Gocciole e panna fresca da montare. Tieni pronta una tazza capiente." },
    { 'Latte al pentolino', "Scalda il latte a fuoco dolce mescolando. Quando fuma (60-65 °C) spegni: non serve farlo bollire.", t = 5 },
    { 'Macinatura fresca', "Macina il caffè appena prima, a grana media per il filtro." },
    { 'Caffè filtrato', "Versa piano l'acqua a 92-96 °C sul caffè nel filtro e raccogli il caffè nella caraffa. Niente fondi.", t = 4 },
    { 'Gocciole e caffellatte', "Unisci il caffè al latte caldo. Metti le Gocciole nella tazza e versa il caffellatte; per più croccantezza aggiungile all'ultimo." },
    { 'Panna sburrata', "Completa con panna montata ben fredda, a discrezione. Puccia appena una Gocciola e sbriciolane un po' sulla panna. Lascia intiepidire un attimo: scotta." },
  },
  note = {
    "Da bere? Poche Gocciole. Dessert al cucchiaio? Spezzane qualcuna in più.",
    "La panna va messa all'ultimo, così resta soffice sul caffellatte caldo.",
    "La Gocciola perfetta: morbida fuori, ancora croccante dentro.",
  },
},

------------------------------------------------------------------ della nonna

{ id = 'lasagne', nome = 'Lasagne alla bolognese', chi = 'nonna', cat = 'primi',
  min = 210, tempo = '3 ore e mezza', diff = 3, porz = 6, unita = 'persone',
  desc = "Il pranzo della domenica: ragù cotto piano piano, besciamella fatta in casa e sfoglia all'uovo. Il bordo croccante è il pezzo conteso.",
  ing = {
    { '{500} g', "sfoglia all'uovo per lasagne" },
    { '{300} g', 'macinato di manzo' },
    { '{200} g', 'macinato di maiale' },
    { '{1}', 'carota' },
    { '{1}', 'costa di sedano' },
    { '{1}', 'cipolla' },
    { '{700} g', 'passata di pomodoro' },
    { '{125} ml', 'vino rosso' },
    { '{3} cucchiai', "olio extravergine d'oliva" },
    { '{1} l', 'latte intero (besciamella)' },
    { '{80} g', 'burro (besciamella)' },
    { '{80} g', 'farina 00 (besciamella)' },
    { '{150} g', 'parmigiano grattugiato' },
    { 'q.b.', 'sale, pepe, noce moscata' },
  },
  passi = {
    { 'Il soffritto', "Trita fini carota, sedano e cipolla. Falli appassire nell'olio a fuoco basso, mescolando, finché sono morbidi e lucidi.", t = 10 },
    { 'Rosola la carne', "Alza il fuoco, aggiungi i due macinati e sgranali col cucchiaio di legno. Rosola finché la carne non è più rosa e l'acqua è evaporata.", t = 10 },
    { 'Sfuma col vino', "Versa il vino rosso e lascialo evaporare del tutto, mescolando, finché non senti più l'odore dell'alcol.", t = 3 },
    { 'Il ragù cuoce piano', "Aggiungi la passata, un bicchiere d'acqua, sale e pepe. Coperchio socchiuso e fuoco al minimo per almeno 2 ore: deve sobbollire appena. Se si asciuga troppo, un goccio d'acqua.", t = 120 },
    { 'La besciamella', "Sciogli il burro, aggiungi la farina e mescola 1 minuto. Versa il latte caldo a filo sbattendo con la frusta, poi sale e noce moscata. Cuoci finché vela il cucchiaio.", t = 10 },
    { 'Componi la teglia', "Forno a 200 °C. Sul fondo della teglia un velo di ragù e besciamella, poi sfoglia, ragù, besciamella e parmigiano. Ripeti per 5-6 strati e finisci con ragù, besciamella e tanto parmigiano." },
    { 'In forno', "Cuoci a 200 °C per 30-35 minuti, finché la superficie è dorata e ai bordi fa le bolle.", t = 35 },
    { 'Lascia riposare', "Sforna e aspetta almeno 10 minuti prima di tagliare: così le fette restano in piedi.", t = 10 },
  },
  note = {
    "Il ragù è più buono il giorno dopo: la nonna lo preparava il sabato.",
    "Con la sfoglia secca, tieni la besciamella un po' più liquida o passa le sfoglie in acqua bollente.",
    "Si congela benissimo, già porzionata, anche cotta.",
  },
},

{ id = 'parmigiana', nome = 'Parmigiana di melanzane', chi = 'nonna', cat = 'secondi',
  min = 150, tempo = '2 ore e mezza', diff = 3, porz = 6, unita = 'persone',
  desc = "Melanzane fritte, sugo al basilico, mozzarella e parmigiano a strati. Ancora più buona tiepida, o il giorno dopo.",
  ing = {
    { '{3}', 'melanzane tonde (circa 1,2 kg)' },
    { 'q.b.', 'sale grosso' },
    { 'q.b.', 'olio di semi di arachide per friggere' },
    { '{700} g', 'passata di pomodoro' },
    { '{1} spicchio', 'aglio' },
    { '{2} cucchiai', "olio extravergine d'oliva" },
    { '{1} mazzetto', 'basilico' },
    { '{250} g', 'fiordilatte ben scolato' },
    { '{120} g', 'parmigiano grattugiato' },
  },
  passi = {
    { 'Affetta e sala', "Taglia le melanzane a fette di mezzo centimetro per il lungo. Mettile a strati in uno scolapasta con sale grosso, un peso sopra, per 30 minuti: perdono l'acqua amara.", t = 30 },
    { 'Il sugo', "In un tegame scalda l'olio con l'aglio, aggiungi la passata, sale e qualche foglia di basilico. Cuoci a fuoco dolce 20 minuti, poi togli l'aglio.", t = 20 },
    { 'Asciuga e friggi', "Sciacqua le fette, strizzale e asciugale bene con un canovaccio. Friggile poche alla volta nell'olio di semi ben caldo finché sono dorate, poi su carta assorbente." },
    { 'Prepara il fiordilatte', "Taglia il fiordilatte a fettine o a dadini e lascialo sgocciolare: la parmigiana non deve venire acquosa." },
    { 'Componi gli strati', "Forno a 180 °C. In una pirofila: un velo di sugo, melanzane, sugo, fiordilatte, parmigiano e basilico. Ripeti fino a finire e chiudi con sugo e tanto parmigiano." },
    { 'In forno', "Cuoci a 180 °C per 40 minuti, finché sopra fa la crosticina e il sugo bolle ai lati.", t = 40 },
    { 'Fai riposare', "Lasciala riposare almeno 15 minuti prima di tagliarla: si compatta e si serve meglio.", t = 15 },
  },
  note = {
    "La nonna friggeva le melanzane al naturale; c'è chi le passa prima in farina e uovo: più ricca, più pesante.",
    "Versione leggera: melanzane grigliate o al forno invece che fritte.",
    "Si conserva 2-3 giorni in frigorifero e si congela bene.",
  },
},

{ id = 'polpette', nome = 'Polpette al sugo', chi = 'nonna', cat = 'secondi',
  min = 75, tempo = '75 min', diff = 2, porz = 4, unita = 'persone',
  desc = "Morbide, col pane ammollato nel latte, cotte piano nel sugo di pomodoro. E poi la scarpetta.",
  ing = {
    { '{400} g', 'macinato misto manzo e maiale' },
    { '{80} g', 'pane raffermo senza crosta' },
    { '{100} ml', 'latte' },
    { '{2}', 'uova' },
    { '{60} g', 'parmigiano grattugiato' },
    { '{1} ciuffo', 'prezzemolo tritato' },
    { '{1} spicchio', 'aglio tritato finissimo' },
    { 'q.b.', 'sale, pepe, noce moscata' },
    { '{700} g', 'passata di pomodoro' },
    { '{1/2}', 'cipolla' },
    { '{3} cucchiai', "olio extravergine d'oliva" },
    { 'a piacere', 'basilico' },
  },
  passi = {
    { 'Ammolla il pane', "Spezzetta il pane e lascialo nel latte finché è morbido, poi strizzalo bene con le mani.", t = 10 },
    { "L'impasto", "In una ciotola unisci carne, pane strizzato, uova, parmigiano, prezzemolo, aglio, sale, pepe e noce moscata. Impasta con le mani finché è omogeneo e compatto." },
    { 'Forma le polpette', "Con le mani bagnate forma delle palline grandi come una noce, circa 30 g l'una. Mettile su un vassoio, senza schiacciarle." },
    { 'Il sugo', "In un tegame largo fai appassire la cipolla tritata nell'olio, poi aggiungi la passata, un bicchiere d'acqua e un pizzico di sale. Porta a bollore.", t = 10 },
    { 'Cuoci nel sugo', "Adagia le polpette nel sugo una accanto all'altra. Coperchio e fuoco dolce per 30-40 minuti; dopo i primi 10 minuti girale piano con un cucchiaio.", t = 35 },
    { 'Servi', "Completa con basilico fresco e servi con il loro sugo, e del pane per la scarpetta." },
  },
  note = {
    "Per una crosticina dorata, rosolale prima in padella con poco olio e poi finisci la cottura nel sugo.",
    "Avanzate? Il sugo condisce la pasta del giorno dopo.",
    "Mai pressare troppo l'impasto: le polpette restano più morbide.",
  },
},

{ id = 'pollo', nome = 'Pollo arrosto con patate', chi = 'nonna', cat = 'secondi',
  min = 90, tempo = '90 min', diff = 2, porz = 4, unita = 'persone',
  desc = "Il profumo della domenica: pollo dorato con rosmarino e salvia, patate che si insaporiscono nel suo fondo.",
  ing = {
    { '{1}', 'pollo a pezzi (circa 1,3 kg)' },
    { '{800} g', 'patate' },
    { '{3} spicchi', 'aglio in camicia' },
    { '{3} rametti', 'rosmarino' },
    { '{6} foglie', 'salvia' },
    { '{100} ml', 'vino bianco secco' },
    { '{5} cucchiai', "olio extravergine d'oliva" },
    { 'q.b.', 'sale e pepe' },
  },
  passi = {
    { 'Accendi il forno', "Porta il forno statico a 200 °C. Togli il pollo dal frigorifero un quarto d'ora prima e asciugalo con carta da cucina." },
    { 'Insaporisci il pollo', "In una teglia grande condisci il pollo con olio, sale, pepe, aglio schiacciato, rosmarino e salvia. Massaggialo bene, anche sotto la pelle." },
    { 'Le patate', "Sbuccia le patate, tagliale a tocchi e mettile intorno al pollo con un filo d'olio e sale. Tutto in un solo strato." },
    { 'Prima cottura', "Inforna a 200 °C per 30 minuti.", t = 30 },
    { 'Sfuma e gira', "Bagna il pollo col vino bianco, gira i pezzi e le patate, e rimetti in forno.", t = 35 },
    { 'La pelle croccante', "Se vuoi la pelle più croccante, gli ultimi 5 minuti accendi il grill. Lascia riposare 5 minuti fuori dal forno prima di servire.", t = 5 },
  },
  note = {
    "La carne è cotta quando, bucandola vicino all'osso, il succo esce trasparente.",
    "Non affollare la teglia: le patate devono arrostire, non bollire.",
    "Il fondo di cottura è oro: versalo sulle patate prima di servire.",
  },
},

{ id = 'minestrone', nome = 'Minestrone della nonna', chi = 'nonna', cat = 'zuppe',
  min = 90, tempo = '90 min', diff = 1, porz = 6, unita = 'persone',
  desc = "Tante verdure di stagione e la crosta di parmigiano nel brodo. Si fa in quantità e il giorno dopo è ancora più buono.",
  ing = {
    { '{2}', 'patate' },
    { '{2}', 'carote' },
    { '{2}', 'zucchine' },
    { '{1}', 'cipolla' },
    { '{1}', 'costa di sedano' },
    { '{150} g', 'fagiolini' },
    { '{200} g', 'fagioli borlotti lessati' },
    { '{150} g', 'bietole o verza' },
    { '{2}', 'pomodori pelati' },
    { '{1}', 'crosta di parmigiano' },
    { '{1,8} l', 'acqua o brodo vegetale' },
    { '{120} g', 'pasta corta o riso (facoltativo)' },
    { '{4} cucchiai', "olio extravergine d'oliva" },
    { 'q.b.', 'sale' },
  },
  passi = {
    { 'Lava e taglia', "Lava tutte le verdure e tagliale a dadini piccoli e simili; i fagiolini a pezzetti, le bietole a striscioline.", t = 15 },
    { 'Il soffritto', "In una pentola capiente fai appassire cipolla, sedano e carota nell'olio a fuoco dolce.", t = 5 },
    { 'Le verdure dure', "Aggiungi patate, fagiolini, bietole e pomodori. Copri con l'acqua o il brodo, metti la crosta di parmigiano raschiata e un pizzico di sale." },
    { 'Cuoci piano', "Porta a bollore, abbassa e cuoci con il coperchio socchiuso per 40 minuti.", t = 40 },
    { 'Zucchine e fagioli', "Aggiungi zucchine e borlotti e continua per altri 20 minuti. Assaggia di sale.", t = 20 },
    { 'La pasta (se vuoi)', "Butta la pasta o il riso direttamente nel minestrone e cuoci il tempo indicato sulla confezione, mescolando.", t = 10 },
    { 'Servi', "Togli la crosta di parmigiano. Servi con un filo d'olio a crudo e parmigiano grattugiato." },
  },
  note = {
    "Le verdure cambiano con la stagione: zucca in autunno, piselli in primavera.",
    "D'estate la nonna lo mangiava tiepido, quasi freddo.",
    "Se lo conservi, cuoci la pasta a parte: in frigorifero si gonfierebbe.",
  },
},

{ id = 'pastafagioli', nome = 'Pasta e fagioli', chi = 'nonna', cat = 'zuppe',
  min = 50, tempo = '50 min', diff = 1, porz = 4, unita = 'persone',
  desc = "Cremosa, con metà fagioli frullati e il rosmarino. Il piatto povero che scalda la casa.",
  ing = {
    { '{500} g', 'fagioli borlotti lessati' },
    { '{200} g', 'pasta mista o ditalini' },
    { '{1}', 'carota' },
    { '{1}', 'costa di sedano' },
    { '{1}', 'cipolla' },
    { '{1} spicchio', 'aglio' },
    { '{1} rametto', 'rosmarino' },
    { '{150} g', 'passata di pomodoro' },
    { '{1} l', 'brodo vegetale caldo' },
    { '{1}', 'crosta di parmigiano' },
    { '{3} cucchiai', "olio extravergine d'oliva" },
    { 'q.b.', 'sale e pepe' },
  },
  passi = {
    { 'Il soffritto', "Trita carota, sedano, cipolla e aglio e falli appassire nell'olio con il rametto di rosmarino.", t = 8 },
    { 'Fagioli e pomodoro', "Aggiungi i fagioli scolati e la passata, mescola e lascia insaporire qualche minuto." },
    { 'Il brodo', "Versa il brodo caldo, aggiungi la crosta di parmigiano e cuoci a fuoco medio con il coperchio socchiuso.", t = 20 },
    { 'Frulla metà', "Togli rosmarino e crosta. Frulla metà della minestra col frullatore a immersione: resta cremosa ma con i fagioli interi." },
    { 'La pasta', "Butta la pasta nella pentola e cuocila nella minestra, mescolando spesso; se si asciuga troppo, un mestolo di brodo.", t = 10 },
    { 'Riposo e servizio', "Spegni e lascia riposare 5 minuti. Servi con olio a crudo e una macinata di pepe.", t = 5 },
  },
  note = {
    "Con i fagioli secchi: 200 g in ammollo una notte, poi lessati 1 ora e mezza con salvia e aglio.",
    "La nonna aggiungeva una cotenna o un pezzetto di pancetta nel soffritto.",
    "Più buona tiepida che bollente.",
  },
},

{ id = 'risotto', nome = 'Risotto alla milanese', chi = 'nonna', cat = 'primi',
  min = 35, tempo = '35 min', diff = 3, porz = 4, unita = 'persone',
  desc = "Giallo di zafferano, mantecato con burro e parmigiano, all'onda. Pochi ingredienti, tutta attenzione.",
  ing = {
    { '{320} g', 'riso Carnaroli' },
    { '{1}', 'cipolla piccola' },
    { '{60} g', 'burro freddo' },
    { '{1,2} l', 'brodo di carne caldo' },
    { '{100} ml', 'vino bianco secco' },
    { '{1} bustina', 'zafferano (o pistilli)' },
    { '{70} g', 'parmigiano grattugiato' },
    { 'q.b.', 'sale' },
  },
  passi = {
    { 'Brodo e zafferano', "Tieni il brodo caldo sul fuoco accanto. Sciogli lo zafferano in mezzo mestolo di brodo (con i pistilli lasciali in infusione 10 minuti)." },
    { 'La cipolla', "Trita finissima la cipolla e falla appassire con metà del burro a fuoco dolce, senza farla colorire.", t = 5 },
    { 'Tosta il riso', "Versa il riso e tostalo 2-3 minuti mescolando, finché i chicchi diventano lucidi e caldi.", t = 3 },
    { 'Sfuma', "Versa il vino bianco e lascialo evaporare mescolando." },
    { 'Cuoci col brodo', "Aggiungi il brodo un mestolo alla volta, solo quando il precedente è assorbito, mescolando spesso. A metà cottura unisci lo zafferano. Circa 16-18 minuti.", t = 17 },
    { 'Manteca', "Il riso deve essere al dente e ancora morbido. Spegni, aggiungi il burro freddo rimasto e il parmigiano e mescola energicamente. Coperchio e riposo 2 minuti.", t = 2 },
  },
  note = {
    "All'onda: scuotendo il piatto il risotto si muove come un'onda, non sta fermo come un budino.",
    "La tradizione vuole un po' di midollo di bue nel soffritto.",
    "Avanzato? Diventa riso al salto, croccante in padella col burro.",
  },
},

{ id = 'gnocchi', nome = 'Gnocchi di patate al pomodoro', chi = 'nonna', cat = 'primi',
  min = 90, tempo = '90 min', diff = 3, porz = 4, unita = 'persone',
  desc = "Patate farinose, poca farina e le mani della nonna. Rigati con la forchetta, conditi con sugo e basilico.",
  ing = {
    { '{1} kg', 'patate farinose' },
    { '{270} g', 'farina 00' },
    { '{1}', 'uovo' },
    { 'q.b.', 'sale' },
    { '{500} g', 'passata di pomodoro' },
    { '{1} spicchio', 'aglio' },
    { '{3} cucchiai', "olio extravergine d'oliva" },
    { 'a piacere', 'basilico e parmigiano' },
  },
  passi = {
    { 'Lessa le patate', "Lessa le patate con la buccia, partendo da acqua fredda, per 30-40 minuti: devono essere tenere fino al cuore.", t = 35 },
    { 'Schiaccia calde', "Sbucciale ancora calde e passale subito con lo schiacciapatate sulla spianatoia. Lasciale intiepidire allargate." },
    { "L'impasto", "Aggiungi la farina poco alla volta, l'uovo e un pizzico di sale. Impasta il minimo indispensabile: deve essere morbido, appena non appiccicoso." },
    { 'Forma gli gnocchi', "Fai dei cordoni spessi un dito, tagliali a tocchetti di 2 cm e passali sui rebbi di una forchetta. Mettili su un vassoio infarinato, separati." },
    { 'Il sugo', "Scalda olio e aglio, aggiungi la passata, sale e basilico e cuoci a fuoco dolce.", t = 15 },
    { 'Cuoci gli gnocchi', "In abbondante acqua salata, pochi per volta: appena salgono a galla sono pronti. Raccoglili con la schiumarola.", t = 2 },
    { 'Condisci', "Passali subito nel sugo, mescola piano e servi con basilico fresco e parmigiano." },
  },
  note = {
    "Le patate giuste sono vecchie e farinose, a pasta bianca: con quelle nuove serve troppa farina.",
    "Meno farina metti, più gli gnocchi sono leggeri. Fai una prova di cottura con uno gnocco.",
    "Si congelano crudi sul vassoio, poi in un sacchetto; si cuociono ancora surgelati.",
  },
},

{ id = 'carbonara', nome = 'Spaghetti alla carbonara', chi = 'nonna', cat = 'primi',
  min = 25, tempo = '25 min', diff = 2, porz = 4, unita = 'persone',
  desc = "Guanciale croccante, tuorli e pecorino, tanto pepe. Niente panna: la cremosità viene dall'acqua di cottura.",
  ing = {
    { '{320} g', 'spaghetti' },
    { '{150} g', 'guanciale' },
    { '{4}', 'tuorli' },
    { '{1}', 'uovo intero' },
    { '{60} g', 'pecorino romano grattugiato' },
    { 'q.b.', 'pepe nero macinato al momento' },
    { 'q.b.', "sale grosso per l'acqua" },
  },
  passi = {
    { "Metti l'acqua", "Porta a bollore abbondante acqua e salala, ma poco: il guanciale e il pecorino sono già saporiti.", t = 10 },
    { 'Il guanciale', "Togli la cotenna e taglia il guanciale a listarelle. Rosolalo in padella senza olio a fuoco medio finché è dorato e croccante.", t = 8 },
    { 'La crema', "In una ciotola sbatti tuorli, uovo intero, pecorino e tanto pepe fino a una crema densa." },
    { 'Cuoci gli spaghetti', "Butta gli spaghetti e cuocili al dente. Tieni da parte una tazza di acqua di cottura.", t = 10 },
    { 'Manteca fuori dal fuoco', "Scola gli spaghetti nella padella del guanciale, fuori dal fuoco. Versa la crema e mescola veloce con un goccio d'acqua di cottura fino a una salsa cremosa, non a uova strapazzate." },
    { 'Servi', "Impiatta subito con altro pecorino e una macinata di pepe." },
  },
  note = {
    "Il segreto è la temperatura: la padella calda ma spenta, così l'uovo si addensa senza rapprendersi.",
    "Se non trovi il guanciale, la pancetta va bene, ma non è la stessa cosa.",
    "Un cucchiaio del grasso del guanciale nella crema le dà sapore.",
  },
},

{ id = 'tiramisu', nome = 'Tiramisù', chi = 'nonna', cat = 'dolci',
  min = 280, tempo = '40 min + 4 ore', diff = 2, porz = 8, unita = 'persone',
  desc = "Savoiardi nel caffè, crema al mascarpone e cacao amaro. Si prepara prima e si aspetta: il tempo lo fa diventare buono.",
  ing = {
    { '{500} g', 'mascarpone' },
    { '{4}', 'uova freschissime (meglio pastorizzate)' },
    { '{100} g', 'zucchero' },
    { '{300} g', 'savoiardi' },
    { '{300} ml', 'caffè della moka, freddo' },
    { 'q.b.', 'cacao amaro in polvere' },
  },
  passi = {
    { 'Il caffè', "Prepara il caffè con la moka, zuccheralo poco o niente e lascialo raffreddare in un piatto fondo.", t = 10 },
    { 'Tuorli e zucchero', "Separa le uova. Monta i tuorli con lo zucchero finché sono chiari e spumosi.", t = 5 },
    { 'Il mascarpone', "Aggiungi il mascarpone ai tuorli un cucchiaio alla volta e mescola fino a una crema liscia." },
    { 'Gli albumi', "Monta gli albumi a neve ferma e incorporali alla crema poco per volta, dal basso verso l'alto, per non smontarla." },
    { 'Inzuppa e componi', "Passa velocemente ogni savoiardo nel caffè (dentro e fuori) e fai uno strato nella pirofila. Copri con metà crema, poi un altro strato di savoiardi e il resto della crema." },
    { 'In frigorifero', "Copri e metti in frigorifero per almeno 4 ore, meglio tutta la notte.", t = 240 },
    { 'Il cacao', "Prima di servire, spolvera il cacao amaro con un colino fine." },
  },
  note = {
    "Savoiardi solo bagnati, non zuppi: altrimenti il tiramisù si allaga.",
    "Le uova sono crude: usale freschissime o pastorizzate, e tieni il dolce in frigorifero.",
    "Il cacao va messo all'ultimo, altrimenti si inumidisce.",
  },
},

{ id = 'crostata', nome = 'Crostata di marmellata', chi = 'nonna', cat = 'dolci',
  min = 110, tempo = '1 ora e 50', diff = 2, porz = 8, unita = 'fette',
  desc = "Pasta frolla al limone e marmellata della dispensa, con la griglia di strisce. La merenda di sempre.",
  ing = {
    { '{300} g', 'farina 00' },
    { '{150} g', 'burro freddo' },
    { '{120} g', 'zucchero' },
    { '{1}', 'uovo intero' },
    { '{1}', 'tuorlo' },
    { '{1}', 'limone (scorza grattugiata)' },
    { '{1} pizzico', 'sale' },
    { '{350} g', 'marmellata (albicocche o visciole)' },
  },
  passi = {
    { 'Sabbia la frolla', "Sulla spianatoia lavora velocemente la farina con il burro freddo a pezzetti, con la punta delle dita, finché diventa sabbia." },
    { "Chiudi l'impasto", "Aggiungi zucchero, uovo, tuorlo, scorza di limone e sale. Impasta in fretta, giusto il tempo di compattare: la frolla non va scaldata." },
    { 'Riposo in frigorifero', "Avvolgi la frolla nella pellicola e mettila in frigorifero per almeno 30 minuti.", t = 30 },
    { 'Stendi e riempi', "Forno a 180 °C. Stendi due terzi della frolla a 4-5 mm e fodera una tortiera di 24 cm imburrata. Bucherella il fondo e spalma la marmellata." },
    { 'La griglia', "Stendi la frolla rimasta, taglia delle strisce larghe 1,5 cm e disponile a griglia sulla marmellata. Ripiega il bordo sopra le strisce." },
    { 'In forno', "Cuoci a 180 °C per 35-40 minuti, finché la frolla è dorata.", t = 35 },
    { 'Raffredda', "Lasciala raffreddare del tutto nella tortiera prima di toglierla: da calda si rompe." },
  },
  note = {
    "Mani fredde e poco lavoro: è il segreto di una frolla friabile.",
    "Se la marmellata è molto liquida, mescolala con un cucchiaio di pangrattato.",
    "Sotto una campana di vetro si conserva 4-5 giorni.",
  },
},

{ id = 'ciambellone', nome = 'Ciambellone della nonna', chi = 'nonna', cat = 'dolci',
  min = 65, tempo = '65 min', diff = 1, porz = 10, unita = 'fette',
  desc = "Soffice, alto, profumato di limone. Da inzuppare nel latte la mattina, o con il tè il pomeriggio.",
  ing = {
    { '{300} g', 'farina 00' },
    { '{200} g', 'zucchero' },
    { '{3}', 'uova' },
    { '{120} ml', 'latte' },
    { '{100} ml', 'olio di semi' },
    { '{1}', 'limone (scorza grattugiata)' },
    { '{1} bustina', 'lievito per dolci (16 g)' },
    { 'q.b.', 'zucchero a velo' },
  },
  passi = {
    { 'Prepara lo stampo', "Forno statico a 175 °C. Imburra e infarina bene uno stampo da ciambella di 24 cm." },
    { 'Uova e zucchero', "Monta le uova con lo zucchero finché sono chiare, gonfie e spumose: ci vogliono circa 10 minuti con le fruste.", t = 10 },
    { 'I liquidi', "Sempre montando, aggiungi a filo l'olio e poi il latte, e la scorza di limone." },
    { 'Farina e lievito', "Setaccia farina e lievito e incorporali poco alla volta con una spatola, dal basso verso l'alto." },
    { 'In forno', "Versa nello stampo e cuoci 40-45 minuti senza aprire il forno per i primi 30. Prova lo stecchino: deve uscire asciutto.", t = 40 },
    { 'Raffredda e spolvera', "Lascialo intiepidire 10 minuti nello stampo, poi capovolgilo su una gratella. Da freddo, zucchero a velo.", t = 10 },
  },
  note = {
    "Variante marmorizzata: a metà impasto aggiungi 2 cucchiai di cacao e versa a cucchiaiate alternate.",
    "Tutti gli ingredienti a temperatura ambiente: monta meglio.",
    "Si conserva 3-4 giorni sotto una campana o in un sacchetto.",
  },
},

}
end

-- Ai Fornelli: il ricettario da tenere accanto ai fornelli, per Xteink X4 Pro (XTOS Licorice, API 2.0).
--
-- Schermate: Oggi (il piatto del giorno), Ricette (filtri e pagine), la ricetta (porzioni e
-- ingredienti), Cucina (un passo alla volta, con i timer) e Cucinati (il calendario di quello che
-- hai cucinato). Le ricette sono in ricette.lua, le foto in data/foto_g.txt e data/foto_p.txt
-- (PNG a 1 bit in base64, una riga per ricetta: tools/make_art.py).
--
-- Il lettore non ha suoni: quando un timer finisce lo schermo lampeggia e compare un avviso.
-- I timer sono salvati come il momento in cui finiscono, così restano giusti anche se esci dall'app.
-- Niente grandi aree nere: sull'e-ink lasciano l'ombra; i bottoni importanti hanno il bordo spesso.

local RICETTE = require('ricette')

local W, H = kit.W, kit.H
local M = 20                       -- margine
local TAB_Y = 736                  -- la barra in basso
local BTN_Y, BTN_H = 716, 64       -- i bottoni in fondo alle schermate senza barra
local FOTO_W, FOTO_H = 400, 230
local MINI = 88

local CATEGORIE = { { 'tutte', 'Tutte' }, { 'primi', 'Primi' }, { 'secondi', 'Secondi' }, { 'zuppe', 'Zuppe' },
                    { 'pizza', 'Pizza' }, { 'dolci', 'Dolci' } }
local NOME_CAT = { primi = 'Primi', secondi = 'Secondi', zuppe = 'Zuppe', pizza = 'Pizza', dolci = 'Dolci' }
local FILTRI = { { 'leo', 'Chef Leo' }, { 'nonna', 'Nonna' }, { 'veloci', 'Sotto 30 min' }, { 'facili', 'Facili' },
                 { 'nuove', 'Mai fatte' } }
local SINGOLARE = { persone = 'persona', panetti = 'panetto', tazze = 'tazza', fette = 'fetta' }
local GIORNI = { 'dom', 'lun', 'mar', 'mer', 'gio', 'ven', 'sab' }
local GIORNI_GRIGLIA = { 'L', 'M', 'M', 'G', 'V', 'S', 'D' }
local MESI = { 'gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre',
               'ottobre', 'novembre', 'dicembre' }

local ERR = nil

------------------------------------------------------------------ stato

local scr = 'oggi'            -- 'oggi', 'ricette', 'dettaglio', 'consigli', 'cucina', 'cucinati'
local da = {}                 -- da dove si torna indietro: una pila di schermate
local cur = 1                 -- la ricetta aperta
local porz = {}               -- porzioni scelte, per ricetta
local pag_ing = 1             -- pagina degli ingredienti
local pag_cons = 1            -- pagina dei consigli
local passo = 1               -- passo in cucina
local cat = 'tutte'
local filtri = {}             -- id -> true
local pag_lista = 1
local oggi_scarto, oggi_giorno = 0, -1
local diario = {}             -- { giorno, id }, dal più vecchio
local timers = {}             -- { rid, passo, fine (secondi), tot } ; fine = scaduto quando <= ora
local mese_vista = nil        -- { anno, mese } nel calendario
local giorno_scelto = nil     -- un giorno toccato nel calendario
local zone = {}               -- le zone toccabili del disegno corrente
local lampo = 0               -- fotogrammi del lampeggio di fine timer
local sveglia = nil           -- il prossimo ctx:after
local sveglio_ms = 0

local function per_id(id)
  for k, r in ipairs(RICETTE) do if r.id == id then return k, r end end
  return nil
end

------------------------------------------------------------------ tempo e date

local base_ms = nil
local function ora_t()
  local ok, t = pcall(function() return ctx.clock:now() end)
  return (ok and type(t) == 'table') and t or {}
end
-- secondi dal 1970; senza orologio, secondi da quando l'app è aperta
local function ora()
  local e = tonumber(ora_t().epoch_sec)
  if e then return math.floor(e) end
  local ms = kit.now_ms() or 0
  base_ms = base_ms or ms
  return (ms - base_ms) // 1000 + 1000000
end
-- il giorno locale (giorni dal 1970)
local function oggi()
  local t = ora_t()
  local l = tonumber(t.local_sec) or tonumber(t.epoch_sec)
  if not l then return 20000 end
  return math.floor(l) // 86400
end

local function data_da_giorno(d)
  local z = d + 719468
  local era = z // 146097
  local doe = z - era * 146097
  local yoe = (doe - doe // 1460 + doe // 36524 - doe // 146096) // 365
  local y = yoe + era * 400
  local doy = doe - (365 * yoe + yoe // 4 - yoe // 100)
  local mp = (5 * doy + 2) // 153
  local dd = doy - (153 * mp + 2) // 5 + 1
  local m = mp < 10 and mp + 3 or mp - 9
  if m <= 2 then y = y + 1 end
  return y, m, dd
end

local function giorno_da_data(y, m, d)
  if m <= 2 then y = y - 1 end
  local era = y // 400
  local yoe = y - era * 400
  local doy = (153 * (m > 2 and m - 3 or m + 9) + 2) // 5 + d - 1
  local doe = yoe * 365 + yoe // 4 - yoe // 100 + doy
  return era * 146097 + doe - 719468
end

local function settimana(d) return (d + 4) % 7 + 1 end      -- 1 = domenica

local function data_breve(d)
  local _y, m, dd = data_da_giorno(d)
  return GIORNI[settimana(d)] .. ' ' .. dd .. ' ' .. MESI[m]:sub(1, 3)
end

------------------------------------------------------------------ salvataggi

local function salva()
  local t = { 'scarto=' .. oggi_scarto, 'giorno=' .. oggi_giorno, 'cat=' .. cat }
  local f = {}
  for _k, x in ipairs(FILTRI) do if filtri[x[1]] then f[#f + 1] = x[1] end end
  t[#t + 1] = 'filtri=' .. table.concat(f, ',')
  local p = {}
  for id, n in pairs(porz) do p[#p + 1] = id .. ':' .. n end
  table.sort(p)
  t[#t + 1] = 'porz=' .. table.concat(p, ',')
  kit.write('impostazioni.txt', table.concat(t, '\n'))
  local d = {}
  for _k, e in ipairs(diario) do d[#d + 1] = e[1] .. '\t' .. e[2] end
  kit.write('diario.txt', table.concat(d, '\n'))
  local tm = {}
  for _k, x in ipairs(timers) do tm[#tm + 1] = x.rid .. '\t' .. x.passo .. '\t' .. x.fine .. '\t' .. x.tot end
  kit.write('timer.txt', table.concat(tm, '\n'))
end

local function carica()
  local s = kit.kv(kit.read('impostazioni.txt'))
  oggi_scarto = tonumber(s.scarto) or 0
  oggi_giorno = tonumber(s.giorno) or -1
  for _k, c in ipairs(CATEGORIE) do if c[1] == s.cat then cat = s.cat end end
  for f in (s.filtri or ''):gmatch('[^,]+') do filtri[f] = true end
  for id, n in (s.porz or ''):gmatch('(%w+):(%d+)') do porz[id] = tonumber(n) end
  diario = {}
  for g, id in (kit.read('diario.txt') or ''):gmatch('(%d+)\t(%w+)') do
    if per_id(id) then diario[#diario + 1] = { tonumber(g), id } end
  end
  timers = {}
  for a, b, c, d in (kit.read('timer.txt') or ''):gmatch('(%w+)\t(%d+)\t(%d+)\t(%d+)') do
    if per_id(a) then timers[#timers + 1] = { rid = a, passo = tonumber(b), fine = tonumber(c), tot = tonumber(d) } end
  end
end

------------------------------------------------------------------ foto

local larghezze = {}      -- file -> lunghezza di una riga + 1
local function riga_foto(file, n)
  local ok, r = pcall(function() return ctx.package:open_text(file, { max_line_bytes = 65536 }) end)
  if not ok or r == nil or type(r) == 'string' then return nil end
  local riga
  pcall(function()
    if not larghezze[file] then
      local prima = r:read_line()
      if not prima then return end
      larghezze[file] = #prima + 1
      if n == 1 then riga = prima; return end
    end
    r:seek((n - 1) * larghezze[file])
    riga = r:read_line()
  end)
  pcall(function() r:close() end)
  return riga and (riga:gsub(' +$', ''))
end

local cache, ordine = {}, {}
local function foto(tipo, n)          -- tipo 'g' (400x230) o 'p' (88x88)
  local id = tipo .. n
  if cache[id] ~= nil then
    for k, v in ipairs(ordine) do if v == id then table.remove(ordine, k); break end end
    ordine[#ordine + 1] = id
    return cache[id] or nil
  end
  while #ordine >= 7 do
    local vecchia = table.remove(ordine, 1)
    if cache[vecchia] then pcall(function() cache[vecchia]:release() end) end
    cache[vecchia] = nil
  end
  local img = false
  local puo = ctx.capabilities and ctx.capabilities.image_decode and ctx.image and ctx.base64
  local b64 = puo and riga_foto('foto_' .. tipo .. '.txt', n)
  if b64 then
    local ok, bytes = pcall(function() return ctx.base64:decode(b64) end)
    if ok and bytes then
      local oki, im = pcall(function() return ctx.image:decode(bytes) end)
      if oki and im then img = im end
    end
  end
  cache[id] = img
  ordine[#ordine + 1] = id
  return img or nil
end

------------------------------------------------------------------ dosi

local function num(s)
  local a, b = s:match('^(%d+)/(%d+)$')
  if a then return tonumber(a) / tonumber(b) end
  return tonumber((s:gsub(',', '.')))
end

local function scrivi_num(v, decimale)
  if decimale then
    local x = math.floor(v * 10 + 0.5) / 10
    if x == math.floor(x) then return tostring(math.floor(x)) end
    return (string.format('%.1f', x):gsub('%.', ','))
  end
  if v >= 50 then return tostring(math.floor(v / 5 + 0.5) * 5) end
  if v >= 10 then return tostring(math.floor(v + 0.5)) end
  local mezzi = math.floor(v * 2 + 0.5)
  if mezzi < 1 then mezzi = 1 end
  local intero, mezzo = mezzi // 2, mezzi % 2 == 1
  if intero == 0 then return '½' end
  return tostring(intero) .. (mezzo and '½' or '')
end

-- una quantità come '{500} g' per f volte la dose
local function dose(q, f)
  return (q:gsub('{([%d,/]+)}', function(s)
    if f == 1 then return s end
    local v = num(s)
    if not v then return s end
    return scrivi_num(v * f, s:find(',') ~= nil)
  end))
end

local function porzioni(r) return porz[r.id] or r.porz end

------------------------------------------------------------------ timer

local function rimasti(t) return t.fine - ora() end

local function timer_di(rid, p)
  for k, t in ipairs(timers) do if t.rid == rid and t.passo == p then return t, k end end
  return nil
end

local function testo_tempo(sec)
  if sec <= 0 then return 'finito' end
  if sec <= 60 then return sec .. ' s' end
  local m = (sec + 59) // 60
  if m < 60 then return m .. ' min' end
  return (m // 60) .. ' h ' .. (m % 60) .. ' min'
end

local function testo_minuti(m)
  if m < 60 then return m .. ' min' end
  if m % 60 == 0 then return (m // 60) .. (m == 60 and ' ora' or ' ore') end
  return (m // 60) .. ' h ' .. (m % 60) .. ' min'
end

local schedula, controlla

local function tieni_sveglio()
  local D = ctx.device
  if not (D and D.keep_awake) then return end
  local ms = 0
  if #timers > 0 then ms = 14400000 end                 -- con un timer acceso il lettore non dorme (4 ore al massimo)
  if scr == 'cucina' then ms = math.max(ms, 1800000) end -- in cucina: 30 minuti dall'ultimo tocco
  pcall(function() D:keep_awake(ms) end)
  sveglio_ms = ms
end

-- il prossimo momento in cui lo schermo deve cambiare: il minuto mostrato, l'ultimo minuto a secondi
local function prossimo()
  local meglio = nil
  for _k, t in ipairs(timers) do
    local s = rimasti(t)
    if s > 0 then
      local a = s <= 60 and 1 or ((s - 1) % 60 + 1)
      if s > 60 and s - 60 < a then a = s - 60 end
      if not meglio or a < meglio then meglio = a end
    end
  end
  return meglio
end

local function passo_di(t)
  local _k, r = per_id(t.rid)
  return r, r and r.passi[t.passo]
end

local function lampeggia()
  lampo = lampo - 1
  if lampo > 0 then
    pcall(function() ctx:after(900, function() lampeggia() end) end)
    kit.refresh()
  else
    pcall(function() ctx:invalidate('full') end)
  end
end

local function avvisa(t)
  local r, p = passo_di(t)
  kit.show({ title = 'Tempo scaduto!',
             lines = { r and (r.nome .. ', passo ' .. t.passo) or '', p and p[1] or '' },
             rows = { { { 'Vai al passo', function()
                            kit.close()
                            local k = per_id(t.rid)
                            if k then cur, passo, scr = k, t.passo, 'cucina' end
                            controlla(true)
                          end },
                        { 'OK', function() kit.close(); controlla(true) end } } } })
  lampo = 8
  lampeggia()
end

-- toglie i timer finiti dopo l'avviso; 'chiudi' = l'avviso è stato letto
controlla = function(chiudi)
  local cambiato = false
  for k = #timers, 1, -1 do
    local t = timers[k]
    if rimasti(t) <= 0 then
      if chiudi and t.avvisato then table.remove(timers, k); cambiato = true
      elseif not t.avvisato and not kit.modal() then t.avvisato = true; avvisa(t); cambiato = true end
    end
  end
  if cambiato then salva() end
  schedula()
end

schedula = function()
  if sveglia then pcall(function() sveglia:cancel() end); sveglia = nil end
  tieni_sveglio()
  local s = prossimo()
  if not s then return end
  local ok, w = pcall(function() return ctx:after(s * 1000 + 150, function()
    local ok2, e = pcall(function() controlla(false) end)
    if not ok2 then ERR = tostring(e) end
    kit.refresh()
  end) end)
  if ok then sveglia = w end
end

local function avvia_timer(r, p)
  local min = r.passi[p].t
  if not min or timer_di(r.id, p) then return end
  timers[#timers + 1] = { rid = r.id, passo = p, fine = ora() + min * 60, tot = min * 60 }
  salva()
  schedula()
end

local function ferma_timer(r, p)
  local _t, k = timer_di(r.id, p)
  if k then table.remove(timers, k); salva(); schedula() end
end

------------------------------------------------------------------ filtri e oggi

local function volte(id)
  local n = 0
  for _k, e in ipairs(diario) do if e[2] == id then n = n + 1 end end
  return n
end

local function filtrate()
  local out = {}
  for k, r in ipairs(RICETTE) do
    local ok = cat == 'tutte' or r.cat == cat
    if filtri.leo and not filtri.nonna and r.chi ~= 'leo' then ok = false end
    if filtri.nonna and not filtri.leo and r.chi ~= 'nonna' then ok = false end
    if filtri.veloci and r.min > 30 then ok = false end
    if filtri.facili and r.diff > 2 then ok = false end
    if filtri.nuove and volte(r.id) > 0 then ok = false end
    if ok then out[#out + 1] = k end
  end
  return out
end

local function del_giorno()
  local d = oggi()
  if d ~= oggi_giorno then oggi_giorno, oggi_scarto = d, 0 end
  local n = #RICETTE
  return (d * 7 + oggi_scarto * 5) % n + 1
end

local function nel_mese(y, m)
  local piatti, diverse, visti = 0, 0, {}
  for _k, e in ipairs(diario) do
    local ey, em = data_da_giorno(e[1])
    if ey == y and em == m then
      piatti = piatti + 1
      if not visti[e[2]] then visti[e[2]] = true; diverse = diverse + 1 end
    end
  end
  return piatti, diverse
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

local function linea(gg, x1, y1, x2, y2, spesso)
  gg:line(x1, y1, x2, y2)
  if spesso then gg:line(x1, y1 + 1, x2, y2 + 1) end
end

local function freccia(gg, x, y, dir, s)        -- una freccia < o >, centrata in x, y
  s = s or 9
  local d = dir == 'sx' and 1 or -1
  for k = 0, 1 do
    gg:line(x + d * s // 2 + k, y - s, x - d * s // 2 + k, y)
    gg:line(x - d * s // 2 + k, y, x + d * s // 2 + k, y + s)
  end
end

local function riga_doppia(gg, y) gg:line(M, y, W - M, y); gg:line(M, y + 1, W - M, y + 1) end

local function bottone(gg, x, y, w, h, s, forte, fn)
  gg:rect(x, y, w, h, { radius = h // 2, line_width = forte and 4 or 2 })
  testo_c(gg, x + w // 2, y + (h - lh('medium')) // 2, s, 'medium')
  if fn then zona(x, y, w, h, fn) end
end

local function pallini(gg, x, y, n)             -- la difficoltà: 5 pallini, n pieni
  for k = 1, 5 do
    local cx = x + (k - 1) * 13 + 5
    pcall(function() gg:circle(cx, y, 5, k <= n and { fill = true } or { line_width = 1 }) end)
  end
  return 5 * 13
end

local function intestazione(gg, titolo, destra, indietro)
  local x = M
  if indietro then
    freccia(gg, M + 8, 34, 'sx', 11)
    zona(0, 0, 64, 64, indietro)
    x = M + 32
  end
  local f = 'large'
  local maxw = W - M - x - (destra and larg('small', destra) + 12 or 0)
  local s = titolo
  if larg(f, s) > maxw then f = 'medium' end
  local righe = kit.wrap(s, f, maxw)
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
  local voci = { { 'oggi', 'Oggi' }, { 'ricette', 'Ricette' }, { 'cucinati', 'Cucinati' } }
  local w = W // 3
  for k, v in ipairs(voci) do
    local cx = (k - 1) * w + w // 2
    testo_c(gg, cx, TAB_Y + 18, v[2], 'medium')
    if scr == v[1] then
      local lw = larg('medium', v[2])
      gg:rect(cx - lw // 2, TAB_Y + 48, lw, 4, { fill = true })
    end
    zona((k - 1) * w, TAB_Y, w, H - TAB_Y, function() scr, da = v[1], {} end)
  end
end

local function immagine(gg, tipo, n, x, y)
  local img = foto(tipo, n)
  if img then
    pcall(function() gg:image(img, x, y) end)
  else
    local w, h = tipo == 'g' and FOTO_W or MINI, tipo == 'g' and FOTO_H or MINI
    gg:rect(x, y, w, h)
    testo_c(gg, x + w // 2, y + h // 2 - 10, tipo == 'g' and RICETTE[n].nome or '', 'small')
  end
end

local function apri(k, verso)
  da[#da + 1] = scr
  cur = k
  scr = verso or 'dettaglio'
  pag_ing, pag_cons = 1, 1
end

local function indietro()
  scr = table.remove(da) or 'oggi'
  if scr == 'cucina' then scr = 'dettaglio' end
end

local function meta(r)
  local s = (r.chi == 'nonna' and 'Della nonna' or 'Chef Leo') .. ' · ' .. NOME_CAT[r.cat] .. ' · ' .. r.tempo
  return s
end

------------------------------------------------------------------ Oggi

local function disegna_oggi(gg)
  local d = oggi()
  local top = intestazione(gg, 'Ai Fornelli', data_breve(d))
  local k = del_giorno()
  local r = RICETTE[k]
  local y = top + 12
  testo(gg, M, y, 'Il piatto di oggi', 'medium')
  testo_dx(gg, W - M - 18, y, 'Un altro', 'medium')
  freccia(gg, W - M - 5, y + 12, 'dx', 7)
  zona(W // 2, y - 10, W // 2, 44, function() oggi_scarto = oggi_scarto + 1; salva() end)
  y = y + 38
  local cy = y
  immagine(gg, 'g', k, (W - FOTO_W) // 2, y + 6)
  y = y + 6 + FOTO_H + 6
  local righe = kit.wrap(r.nome, 'large', W - 2 * M - 24)
  for j = 1, math.min(2, #righe) do testo(gg, M + 12, y, righe[j], 'large'); y = y + lh('large') + 2 end
  testo(gg, M + 12, y + 2, meta(r), 'small')
  y = y + lh('small') + 8
  local px = M + 12
  pallini(gg, px, y + 10, r.diff)
  testo(gg, px + 74, y, '· ' .. #r.passi .. ' passi', 'small')
  y = y + lh('small') + 8
  local desc = kit.wrap(r.desc, 'small', W - 2 * M - 24)
  for j = 1, math.min(2, #desc) do
    local s = desc[j]
    if j == 2 and #desc > 2 then
      while #s > 1 and larg('small', s .. ' ...') > W - 2 * M - 24 do s = kit.drop_last(s) end
      s = s .. ' ...'
    end
    testo(gg, M + 12, y, s, 'small'); y = y + lh('small') + 2
  end
  y = y + 8
  gg:rect(M, cy, W - 2 * M, y - cy, { radius = 12, line_width = 2 })
  zona(M, cy, W - 2 * M, y - cy, function() apri(k) end)
  y = y + 12
  bottone(gg, M, y, W - 2 * M, 58, 'Vedi la ricetta', true, function() apri(k) end)
  y = y + 58 + 14
  local an, me = data_da_giorno(d)
  local piatti, diverse = nel_mese(an, me)
  testo(gg, M, y, 'Questo mese: ' .. piatti .. (piatti == 1 and ' piatto' or ' piatti') .. ' · '
        .. diverse .. (diverse == 1 and ' ricetta' or ' ricette'), 'small')
  y = y + lh('small') + 8
  -- un timer acceso, o un consiglio
  if #timers > 0 then
    local t = timers[1]
    local rr = passo_di(t)
    local s = 'Timer: ' .. kit.short(rr.nome, 22) .. ', passo ' .. t.passo .. ' - ' .. testo_tempo(rimasti(t))
    gg:rect(M, y, W - 2 * M, 40, { line_width = 2 })
    testo(gg, M + 10, y + 9, s, 'small')
    zona(M, y, W - 2 * M, 40, function() local kk = per_id(t.rid); da = { 'oggi' }; cur, passo, scr = kk, t.passo, 'cucina' end)
  elseif r.note and r.note[1] and y + 2 * lh('small') < TAB_Y then
    local c = kit.wrap('Consiglio: ' .. r.note[1], 'small', W - 2 * M)
    for j = 1, math.min(#c, (TAB_Y - y - 6) // (lh('small') + 2)) do testo(gg, M, y, c[j], 'small'); y = y + lh('small') + 2 end
  end
  barra(gg)
end

------------------------------------------------------------------ Ricette

local LISTA_RIGA = 102
local lista_per_pag = 5

local function disegna_ricette(gg)
  local lista = filtrate()
  local top = intestazione(gg, 'Ricette', tostring(#lista))
  local y = top + 10
  -- le categorie
  local cw = (W - 2 * M) // #CATEGORIE
  gg:rect(M, y, W - 2 * M, 42, { radius = 21, line_width = 2 })
  for k, c in ipairs(CATEGORIE) do
    local x = M + (k - 1) * cw
    if cat == c[1] then gg:rect(x + 3, y + 4, cw - 6, 34, { radius = 17, line_width = 3 }) end
    testo_c(gg, x + cw // 2, y + 11, c[2], 'small')
    zona(x, y, cw, 42, function() cat = c[1]; pag_lista = 1; salva() end)
  end
  y = y + 52
  -- i filtri
  local x = M
  for _k, f in ipairs(FILTRI) do
    local w = larg('small', f[2]) + 24
    if x + w > W - M then x = M; y = y + 40 end
    local on = filtri[f[1]]
    gg:rect(x, y, w, 32, { radius = 16, line_width = on and 3 or 1 })
    if on then gg:rect(x + 4, y + 4, w - 8, 24, { radius = 12, line_width = 1 }) end
    testo(gg, x + 12, y + 6, f[2], 'small')
    zona(x, y, w, 32, function() filtri[f[1]] = not filtri[f[1]] or nil; pag_lista = 1; salva() end)
    x = x + w + 8
  end
  y = y + 44
  riga_doppia(gg, y - 4)
  -- la lista, a pagine
  local per_pag = (TAB_Y - 44 - y) // LISTA_RIGA
  lista_per_pag = per_pag
  local pagine = math.max(1, (#lista + per_pag - 1) // per_pag)
  if pag_lista > pagine then pag_lista = pagine end
  if #lista == 0 then
    testo_c(gg, W // 2, y + 60, 'Nessuna ricetta con questi filtri', 'medium')
    testo_c(gg, W // 2, y + 96, 'Tocca un filtro per toglierlo', 'small')
  end
  for j = 1, per_pag do
    local k = lista[(pag_lista - 1) * per_pag + j]
    if not k then break end
    local r = RICETTE[k]
    local ry = y + (j - 1) * LISTA_RIGA
    immagine(gg, 'p', k, M, ry + 6)
    gg:rect(M, ry + 6, MINI, MINI)
    local tx = M + MINI + 16
    local nr = kit.wrap(r.nome, 'medium', W - M - tx)
    local ty = ry + (#nr > 1 and 8 or 18)
    for i = 1, math.min(2, #nr) do testo(gg, tx, ty, nr[i], 'medium'); ty = ty + lh('medium') end
    ty = ty + 6
    local s = NOME_CAT[r.cat] .. ' · ' .. r.tempo .. ' · '
    testo(gg, tx, ty, s, 'small')
    local px = tx + larg('small', s)
    pallini(gg, px, ty + 10, r.diff)
    local v = volte(r.id)
    if v > 0 then testo_dx(gg, W - M, ty, v == 1 and 'fatta' or ('x' .. v), 'small') end
    if j < per_pag then gg:line(tx, ry + LISTA_RIGA - 1, W - M, ry + LISTA_RIGA - 1) end
    zona(M, ry, W - 2 * M, LISTA_RIGA, function() apri(k) end)
  end
  -- le pagine
  local py = TAB_Y - 40
  if pagine > 1 then
    testo_c(gg, W // 2, py + 6, pag_lista .. ' / ' .. pagine, 'medium')
    if pag_lista > 1 then freccia(gg, M + 14, py + 18, 'sx', 10) end
    if pag_lista < pagine then freccia(gg, W - M - 14, py + 18, 'dx', 10) end
    zona(0, py - 4, W // 3, 44, function() if pag_lista > 1 then pag_lista = pag_lista - 1 end end)
    zona(2 * W // 3, py - 4, W // 3, 44, function() if pag_lista < pagine then pag_lista = pag_lista + 1 end end)
  end
  barra(gg)
end

------------------------------------------------------------------ la ricetta

local ING_RIGA = 36

local function disegna_dettaglio(gg)
  local r = RICETTE[cur]
  local top = intestazione(gg, r.nome, nil, indietro)
  local y = top + 6
  immagine(gg, 'g', cur, (W - FOTO_W) // 2, y)
  y = y + FOTO_H + 4
  local s = meta(r) .. ' · '
  testo(gg, M, y, s, 'small')
  local px = M + larg('small', s)
  pallini(gg, px, y + 10, r.diff)
  testo(gg, px + 70, y, '· ' .. #r.passi .. ' passi', 'small')
  y = y + lh('small') + 10
  -- le porzioni
  local n = porzioni(r)
  gg:rect(M, y, W - 2 * M, 50, { radius = 25, line_width = 2 })
  pcall(function() gg:circle(M + 26, y + 25, 17, { line_width = 2 }) end)
  gg:line(M + 17, y + 25, M + 35, y + 25); gg:line(M + 17, y + 26, M + 35, y + 26)
  pcall(function() gg:circle(W - M - 26, y + 25, 17, { line_width = 2 }) end)
  gg:line(W - M - 35, y + 25, W - M - 17, y + 25); gg:line(W - M - 35, y + 26, W - M - 17, y + 26)
  gg:line(W - M - 26, y + 16, W - M - 26, y + 34); gg:line(W - M - 25, y + 16, W - M - 25, y + 34)
  testo_c(gg, W // 2, y + 13, 'Dosi per ' .. n .. ' ' .. (n == 1 and (SINGOLARE[r.unita] or r.unita) or r.unita), 'medium')
  zona(M, y, 90, 50, function() if n > 1 then porz[r.id] = n - 1; salva() end end)
  zona(W - M - 90, y, 90, 50, function() if n < 24 then porz[r.id] = n + 1; salva() end end)
  y = y + 60
  -- gli ingredienti, a pagine
  local fondo = BTN_Y - 44
  local per_pag = (fondo - y) // ING_RIGA
  local pagine = math.max(1, (#r.ing + per_pag - 1) // per_pag)
  if pag_ing > pagine then pag_ing = pagine end
  local f = n / r.porz
  for j = 1, per_pag do
    local ing = r.ing[(pag_ing - 1) * per_pag + j]
    if not ing then break end
    local iy = y + (j - 1) * ING_RIGA
    local q = dose(ing[1], f)
    local qw = larg('small', q)
    testo_dx(gg, W - M, iy + 6, q, 'small')
    local nome = ing[2]
    while #nome > 1 and larg('small', nome) > W - 2 * M - qw - 16 do nome = kit.drop_last(nome) end
    if nome ~= ing[2] then nome = kit.drop_last(nome) .. '.' end
    testo(gg, M, iy + 6, nome, 'small')
    gg:line(M, iy + ING_RIGA - 2, W - M, iy + ING_RIGA - 2)
  end
  local py = fondo + 4
  testo_c(gg, W // 2, py + 6, 'Ingredienti' .. (pagine > 1 and (' ' .. pag_ing .. ' / ' .. pagine) or ''), 'small')
  if pagine > 1 then
    if pag_ing > 1 then freccia(gg, M + 10, py + 16, 'sx', 8) end
    if pag_ing < pagine then freccia(gg, W - M - 10, py + 16, 'dx', 8) end
    zona(0, py - 6, W // 3, 40, function() if pag_ing > 1 then pag_ing = pag_ing - 1 end end)
    zona(2 * W // 3, py - 6, W // 3, 40, function() if pag_ing < pagine then pag_ing = pag_ing + 1 end end)
  end
  bottone(gg, M, BTN_Y, 150, BTN_H, 'Consigli', false, function() da[#da + 1] = scr; scr = 'consigli'; pag_cons = 1 end)
  bottone(gg, M + 162, BTN_Y, W - 2 * M - 162, BTN_H, 'Inizia a cucinare', true, function()
    da[#da + 1] = scr; passo = 1; scr = 'cucina'; schedula()
  end)
end

------------------------------------------------------------------ consigli

local function righe_consigli(r)
  local out = {}
  for _k, l in ipairs(kit.wrap(r.desc, 'medium', W - 2 * M)) do out[#out + 1] = { l, 'medium' } end
  out[#out + 1] = { '', 'small' }
  out[#out + 1] = { 'Consigli', 'large' }
  for _k, n in ipairs(r.note or {}) do
    local w = kit.wrap(n, 'medium', W - 2 * M - 22)
    for i, l in ipairs(w) do out[#out + 1] = { l, 'medium', i == 1 } end
    out[#out + 1] = { '', 'small' }
  end
  return out
end

local function disegna_consigli(gg)
  local r = RICETTE[cur]
  local top = intestazione(gg, r.nome, nil, indietro)
  local righe = righe_consigli(r)
  local alto = BTN_Y - 50 - top
  -- dividi in pagine
  local pagine, h, inizio = { 1 }, 0, 1
  for k, l in ipairs(righe) do
    local a = lh(l[2]) + 4
    if h + a > alto and k > inizio then pagine[#pagine + 1] = k; inizio = k; h = 0 end
    h = h + a
  end
  if pag_cons > #pagine then pag_cons = #pagine end
  local y = top + 14
  local fine = pagine[pag_cons + 1] and pagine[pag_cons + 1] - 1 or #righe
  for k = pagine[pag_cons], fine do
    local l = righe[k]
    local x = M
    if l[2] == 'medium' and k > 2 and righe[k][3] ~= nil then x = M + 22 end
    if l[3] then pcall(function() gg:circle(M + 6, y + 12, 4, { fill = true }) end) end
    if l[1] ~= '' then testo(gg, x, y, l[1], l[2]) end
    y = y + lh(l[2]) + 4
  end
  if #pagine > 1 then
    local py = BTN_Y - 40
    testo_c(gg, W // 2, py, pag_cons .. ' / ' .. #pagine, 'medium')
    if pag_cons > 1 then freccia(gg, M + 14, py + 12, 'sx', 10) end
    if pag_cons < #pagine then freccia(gg, W - M - 14, py + 12, 'dx', 10) end
    zona(0, py - 10, W // 3, 46, function() if pag_cons > 1 then pag_cons = pag_cons - 1 end end)
    zona(2 * W // 3, py - 10, W // 3, 46, function() if pag_cons < #pagine then pag_cons = pag_cons + 1 end end)
  end
  bottone(gg, M, BTN_Y, W - 2 * M, BTN_H, 'Inizia a cucinare', true, function()
    da[#da + 1] = scr; passo = 1; scr = 'cucina'; schedula()
  end)
end

------------------------------------------------------------------ Cucina

local function segna_cucinata(r)
  diario[#diario + 1] = { oggi(), r.id }
  salva()
end

local function orologio(gg, cx, cy)
  pcall(function() gg:circle(cx, cy, 15, { line_width = 3 }) end)
  gg:line(cx, cy, cx, cy - 9); gg:line(cx + 1, cy, cx + 1, cy - 9)
  gg:line(cx, cy, cx + 7, cy + 3)
  gg:rect(cx - 4, cy - 21, 8, 3, { fill = true })
end

local function avanti()
  local r = RICETTE[cur]
  if passo < #r.passi then
    passo = passo + 1
  else
    kit.show({ title = 'Buon appetito!',
               lines = { r.nome .. ' è pronta. La segno tra i piatti cucinati di oggi?' },
               rows = { { { 'Sì, segna', function()
                              kit.close(); segna_cucinata(r); da = {}; scr = 'cucinati'; giorno_scelto = nil; mese_vista = nil
                            end },
                          { 'No', function() kit.close(); indietro() end } } },
               cancel = function() kit.close() end })
  end
end

local function disegna_cucina(gg)
  local r = RICETTE[cur]
  local top = intestazione(gg, kit.short(r.nome, 34), passo .. ' / ' .. #r.passi, indietro)
  local p = r.passi[passo]
  -- la barra di avanzamento: un trattino per passo
  local n = #r.passi
  local gap = n > 12 and 3 or 5
  local sw = (W - 2 * M - (n - 1) * gap) / n
  local y = top + 10
  for k = 1, n do
    local x = math.floor(M + (k - 1) * (sw + gap))
    local w = math.floor(sw)
    if k <= passo then gg:rect(x, y, w, 8, { fill = true }) else gg:rect(x, y, w, 8) end
  end
  y = y + 24
  testo(gg, M, y, 'Passo ' .. passo, 'small')
  if p.t then
    local s = 'Timer ' .. testo_minuti(p.t)
    local w = larg('small', s) + 24
    gg:rect(W - M - w, y - 4, w, 30, { radius = 15, line_width = 2 })
    testo(gg, W - M - w + 12, y + 1, s, 'small')
  end
  y = y + lh('small') + 10
  local tit = kit.wrap(p[1], 'large', W - 2 * M)
  for k = 1, math.min(2, #tit) do testo(gg, M, y, tit[k], 'large'); y = y + lh('large') + 2 end
  y = y + 10
  -- il testo: grande se ci sta, sennò più piccolo
  local fondo_testo = p.t and 552 or 630
  local f, corpo, alt = nil, nil, nil
  for _k, prova in ipairs({ { 'large', 8 }, { 'medium', 8 }, { 'medium', 2 }, { 'small', 4 } }) do
    f = prova[1]
    corpo = kit.wrap(p[2], f, W - 2 * M)
    alt = lh(f) + prova[2]
    if #corpo * alt <= fondo_testo - y then break end
  end
  for k = 1, #corpo do
    if y + alt > fondo_testo + 6 then break end
    testo(gg, M, y, corpo[k], f); y = y + alt
  end
  -- il timer di questo passo
  if p.t then
    local by = 566
    gg:rect(M, by, W - 2 * M, 72, { radius = 14, line_width = 2 })
    orologio(gg, M + 34, by + 38)
    local t = timer_di(r.id, passo)
    local bw = 150
    if not t then
      testo(gg, M + 64, by + 10, 'Questo passo dura', 'small')
      testo(gg, M + 64, by + 34, testo_minuti(p.t), 'large')
      bottone(gg, W - M - bw - 10, by + 12, bw, 48, 'Avvia', true, function() avvia_timer(r, passo) end)
    elseif rimasti(t) > 0 then
      testo(gg, M + 64, by + 10, 'Mancano', 'small')
      testo(gg, M + 64, by + 34, testo_tempo(rimasti(t)), 'large')
      bottone(gg, W - M - bw - 10, by + 12, bw, 48, 'Ferma', false, function() ferma_timer(r, passo) end)
    else
      testo(gg, M + 64, by + 22, 'Tempo scaduto!', 'large')
      bottone(gg, W - M - bw - 10, by + 12, bw, 48, 'OK', true, function() ferma_timer(r, passo) end)
    end
  end
  -- dopo, e gli altri timer accesi
  local ny = 650
  gg:line(M, ny - 6, W - M, ny - 6)
  local altri = {}
  for _k, t in ipairs(timers) do
    if not (t.rid == r.id and t.passo == passo) then
      altri[#altri + 1] = (t.rid == r.id and ('passo ' .. t.passo) or kit.short(passo_di(t).nome, 14)) .. ' ' .. testo_tempo(rimasti(t))
    end
  end
  if #altri > 0 then
    testo(gg, M, ny, kit.short('Timer: ' .. table.concat(altri, ' · '), 46), 'small')
    zona(M, ny - 6, W - 2 * M, 30, function()
      local t = nil
      for _k, x in ipairs(timers) do if not (x.rid == r.id and x.passo == passo) then t = x; break end end
      if t then local kk = per_id(t.rid); cur, passo = kk, t.passo end
    end)
    ny = ny + lh('small') + 4
  end
  if passo < n then
    testo(gg, M, ny, kit.short('Dopo: ' .. r.passi[passo + 1][1], 44), 'small')
  else
    testo(gg, M, ny, 'Ultimo passo', 'small')
  end
  bottone(gg, M, BTN_Y, 150, BTN_H, 'Indietro', false, function()
    if passo > 1 then passo = passo - 1 else indietro() end
  end)
  bottone(gg, M + 162, BTN_Y, W - 2 * M - 162, BTN_H, passo < n and 'Avanti' or 'Fatto!', true, avanti)
end

------------------------------------------------------------------ Cucinati

local function disegna_cucinati(gg)
  local d = oggi()
  local ay, am = data_da_giorno(d)
  mese_vista = mese_vista or { ay, am }
  local y0, m0 = mese_vista[1], mese_vista[2]
  local piatti, diverse = nel_mese(y0, m0)
  local top = intestazione(gg, 'Cucinati', nil)
  local y = top + 10
  local titolo = MESI[m0]:sub(1, 1):upper() .. MESI[m0]:sub(2) .. ' ' .. y0
  testo_c(gg, W // 2, y, titolo, 'medium')
  freccia(gg, M + 14, y + 12, 'sx', 10)
  freccia(gg, W - M - 14, y + 12, 'dx', 10)
  zona(0, y - 10, 120, 46, function()
    m0 = m0 - 1; if m0 < 1 then m0 = 12; y0 = y0 - 1 end; mese_vista = { y0, m0 }; giorno_scelto = nil
  end)
  zona(W - 120, y - 10, 120, 46, function()
    m0 = m0 + 1; if m0 > 12 then m0 = 1; y0 = y0 + 1 end; mese_vista = { y0, m0 }; giorno_scelto = nil
  end)
  y = y + 40
  -- la griglia: lunedì per primo
  local cw, ch = (W - 2 * M) // 7, 46
  local gx = M + (W - 2 * M - 7 * cw) // 2
  for k = 1, 7 do testo_c(gg, gx + (k - 1) * cw + cw // 2, y, GIORNI_GRIGLIA[k], 'small') end
  y = y + lh('small') + 6
  local primo = giorno_da_data(y0, m0, 1)
  local col0 = (settimana(primo) + 5) % 7           -- 0 = lunedì
  local nm = m0 == 12 and giorno_da_data(y0 + 1, 1, 1) or giorno_da_data(y0, m0 + 1, 1)
  local quanti = {}
  for _k, e in ipairs(diario) do if e[1] >= primo and e[1] < nm then quanti[e[1]] = (quanti[e[1]] or 0) + 1 end end
  for g = primo, nm - 1 do
    local i = g - primo + col0
    local cx, cy = gx + (i % 7) * cw, y + (i // 7) * ch
    local _yy, _mm, dd = data_da_giorno(g)
    testo_c(gg, cx + cw // 2, cy + 6, tostring(dd), 'small')
    if quanti[g] then
      pcall(function() gg:circle(cx + cw // 2, cy + 16, 17, { line_width = 3 }) end)
      for j = 1, math.min(3, quanti[g]) do
        pcall(function() gg:circle(cx + cw // 2 + (j - 2) * 8, cy + 40, 3, { fill = true }) end)
      end
    end
    if g == d then gg:rect(cx + 4, cy - 2, cw - 8, ch - 2) end
    if g == giorno_scelto then gg:rect(cx + 2, cy - 4, cw - 4, ch + 2, { line_width = 3 }); gg:rect(cx + 3, cy - 3, cw - 6, ch) end
    zona(cx, cy - 4, cw, ch, function() giorno_scelto = (giorno_scelto ~= g) and g or nil end)
  end
  local righe_griglia = (col0 + nm - primo + 6) // 7
  y = y + righe_griglia * ch + 6
  riga_doppia(gg, y)
  y = y + 10
  testo(gg, M, y, piatti .. (piatti == 1 and ' piatto' or ' piatti') .. ' · ' .. diverse
        .. (diverse == 1 and ' ricetta diversa' or ' ricette diverse'), 'medium')
  y = y + lh('medium') + 8
  -- l'elenco del mese (o del giorno toccato), dal più recente
  local voci = {}
  for k = #diario, 1, -1 do
    local e = diario[k]
    if (giorno_scelto and e[1] == giorno_scelto) or (not giorno_scelto and e[1] >= primo and e[1] < nm) then
      voci[#voci + 1] = k
    end
  end
  if #voci == 0 then
    local s = giorno_scelto and 'Niente in questo giorno' or 'Ancora niente questo mese'
    testo(gg, M, y + 4, s, 'small')
    testo(gg, M, y + 30, 'Finisci una ricetta in Cucina per segnarla qui.', 'small')
  end
  local riga = 38
  local max = (TAB_Y - 8 - y) // riga
  for j = 1, math.min(#voci, max) do
    local k = voci[j]
    local e = diario[k]
    local ry = y + (j - 1) * riga
    local _yy, _mm, dd = data_da_giorno(e[1])
    local kk, r = per_id(e[2])
    testo(gg, M, ry + 6, GIORNI[settimana(e[1])] .. ' ' .. dd, 'small')
    testo(gg, M + 78, ry + 4, kit.short(r.nome, 34), 'medium')
    gg:line(M, ry + riga - 2, W - M, ry + riga - 2)
    zona(M, ry, W - 2 * M, riga, function() apri(kk) end)
    zone[#zone].lungo = function()
      kit.show({ title = 'Togliere dal diario?', lines = { r.nome .. ', ' .. data_breve(e[1]) },
                 rows = { { { 'Togli', function() table.remove(diario, k); salva(); kit.close() end },
                            { 'Annulla', function() kit.close() end } } },
                 cancel = function() kit.close() end })
    end
  end
  barra(gg)
end

------------------------------------------------------------------ callback

local DISEGNA = { oggi = disegna_oggi, ricette = disegna_ricette, dettaglio = disegna_dettaglio,
                  consigli = disegna_consigli, cucina = disegna_cucina, cucinati = disegna_cucinati }

function on_draw(c, gg)
  zone = {}
  local ok, e = pcall(function()
    gg:clear(0)
    if lampo > 0 and lampo % 2 == 1 then
      gg:rect(0, 0, W, H, { fill = true })
      return
    end
    DISEGNA[scr](gg)
    if kit.modal() then kit.draw_modal(gg) end
  end)
  if not ok then ERR = tostring(e) end
  if ERR then
    local righe = kit.wrap('ERR ' .. ERR, 'small', W - 2 * M)
    for k, l in ipairs(righe) do gg:text(M, 600 + (k - 1) * 22, l, { font = kit.font('small') }) end
  end
end

local function tocca(x, y, lungo)
  if kit.modal() then kit.tap_modal(x, y); return end
  for k = #zone, 1, -1 do
    local z = zone[k]
    if x >= z.x and x < z.x + z.w and y >= z.y and y < z.y + z.h then
      if lungo and z.lungo then z.lungo() else z.fn() end
      return
    end
  end
end

local function sfoglia(dir)          -- dir 1 = avanti, -1 = indietro
  if scr == 'cucina' then
    if dir > 0 then avanti() elseif passo > 1 then passo = passo - 1 end
  elseif scr == 'ricette' then
    local per_pag = lista_per_pag
    local n = #filtrate()
    pag_lista = math.max(1, math.min(pag_lista + dir, math.max(1, (n + per_pag - 1) // per_pag)))
  elseif scr == 'dettaglio' then
    pag_ing = math.max(1, pag_ing + dir)
  elseif scr == 'consigli' then
    pag_cons = math.max(1, pag_cons + dir)
  elseif scr == 'cucinati' then
    local y0, m0 = mese_vista[1], mese_vista[2] + dir
    if m0 < 1 then m0 = 12; y0 = y0 - 1 elseif m0 > 12 then m0 = 1; y0 = y0 + 1 end
    mese_vista = { y0, m0 }; giorno_scelto = nil
  elseif scr == 'oggi' and dir > 0 then
    oggi_scarto = oggi_scarto + 1; salva()
  end
end

function on_input(c, ev)
  if type(ev) ~= 'table' then return false end
  local preso = false
  local prima = scr
  local ok, e = pcall(function()
    if ev.type == 'pointer' or ev.type == 'touch' then
      preso = true
      if ev.phase and ev.phase ~= 'up' then return end
      local g = ev.gesture
      if g == 'swipe_left' and not kit.modal() then sfoglia(1)
      elseif g == 'swipe_right' and not kit.modal() then sfoglia(-1)
      elseif g == 'tap' or g == 'long' or g == nil then tocca(ev.x or 0, ev.y or 0, g == 'long') end
    elseif ev.type == 'key' then
      if not kit.key_accepted(ev) then preso = true; return end
      local k = ev.key
      if kit.modal() then
        if k == 'back' then kit.close(); controlla(true); preso = true end
        return
      end
      if k == 'back' then
        if scr == 'oggi' or scr == 'ricette' or scr == 'cucinati' then
          if scr ~= 'oggi' then scr = 'oggi'; preso = true end
        else indietro(); preso = true end
      elseif k == 'right' or k == 'down' then sfoglia(1); preso = true
      elseif k == 'left' or k == 'up' then sfoglia(-1); preso = true
      elseif k == 'ok' and scr == 'oggi' then apri(del_giorno()); preso = true end
    end
  end)
  if not ok then ERR = tostring(e) end
  if prima ~= scr or scr == 'cucina' then tieni_sveglio() end
  if prima ~= scr then pcall(function() ctx:invalidate('full') end) else kit.refresh() end
  return preso
end

function on_load(c)
  local ok, e = pcall(function()
    carica()
    controlla(false)
  end)
  if not ok then ERR = tostring(e) end
  schedula()
end

function on_resume(c)
  pcall(function() controlla(false) end)
  kit.refresh()
end

function on_leave()
  pcall(salva)
  pcall(function() if sveglio_ms > 0 and ctx.device then ctx.device:keep_awake(0) end end)
  sveglio_ms = 0
end

if rawget(_G, '__TEST') then
  __TEST.api = {
    scr = function() return scr end, set = function(s) scr = s end, cur = function() return cur end,
    passo = function() return passo end, timers = function() return timers end, diario = function() return diario end,
    dose = dose, porz = function() return porz end, filtrate = filtrate, data_da_giorno = data_da_giorno,
    giorno_da_data = giorno_da_data, apri = apri, ricette = RICETTE, del_giorno = del_giorno, err = function() return ERR end,
  }
end
