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

-- una riga di un file di dati, dalla sua posizione in byte (le righe non hanno spazi di riempimento)
local function riga_img(file, pos)
  local ok, r = pcall(function() return ctx.package:open_text(file, { max_line_bytes = 65536 }) end)
  if not ok or r == nil or type(r) == 'string' then return nil end
  local riga
  pcall(function()
    if pos > 0 then r:seek(pos) end
    riga = r:read_line()
  end)
  pcall(function() r:close() end)
  return riga
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
  local riga = riga_img('tutorial.txt', TEC[k].o)
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
  if #da > 20 then table.remove(da, 1) end
  scr = s
end

-- il tasto indietro di ogni schermata: torna alla schermata di prima; se non ce n'è, chiede se uscire
local function indietro_o_esci()
  if #da > 0 then indietro(); return end
  kit.show({ title = 'Uscire da Bivacco?', lines = { 'Le spunte e i tutorial fatti restano salvati.' },
             rows = { { { 'Esci', function() kit.close(); pcall(salva); pcall(function() ctx:quit() end) end },
                        { 'Resta', function() kit.close() end } } },
             cancel = function() kit.close() end })
end

-- il tasto: un tondo di 44 pixel con la freccia spessa, in alto a sinistra; si tocca anche un po' fuori
local TASTO = 44
local function tasto_indietro(gg, x, y)
  gg:rect(x, y, TASTO, TASTO, { radius = TASTO // 2, fill = true, color = 0 })
  gg:rect(x, y, TASTO, TASTO, { radius = TASTO // 2, line_width = 3 })
  local cx, cy = x + TASTO // 2 - 2, y + TASTO // 2
  for k = -1, 1 do
    gg:line(cx + 6 + k, cy - 10, cx - 4 + k, cy)
    gg:line(cx - 4 + k, cy, cx + 6 + k, cy + 10)
  end
  zona(0, 0, x + TASTO + 22, y + TASTO + 12, indietro_o_esci)
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
  for k, v in ipairs(voci) do zona((k - 1) * 160, TAB_Y, 160, H - TAB_Y, function() if scr ~= v then vai(v) end end) end
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
    testo(gg, M + TASTO + 14, 22, 'Attrezzatura', 'large')
    y = 64
    gg:rect(M, y, W - 2 * M, 3, { fill = true })
    y = y + 10
  end
  tasto_indietro(gg, M, 10)
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
  tasto_indietro(gg, M, 10)
  testo(gg, M + TASTO + 14, 22, sopra, 'small')
  local y = 66
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
  else testo(gg, M + TASTO + 14, 22, 'Tecniche', 'large'); y = 64 end
  tasto_indietro(gg, M, 10)
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
  tasto_indietro(gg, M, 8)
  testo(gg, M + TASTO + 14, 20, kit.short(t.titolo:upper(), 28), 'small')
  testo_dx(gg, W - M, 20, passo .. ' / ' .. n, 'small')
  local gap = n > 12 and 3 or 5
  local sw = (W - 2 * M - (n - 1) * gap) / n
  local y = 62
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
  else testo(gg, M + TASTO + 14, 22, 'Percorso', 'large'); y = 64 end
  tasto_indietro(gg, M, 10)
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
