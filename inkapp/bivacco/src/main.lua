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
