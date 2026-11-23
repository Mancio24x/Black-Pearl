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
