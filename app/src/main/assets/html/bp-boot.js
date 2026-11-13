/*
 * Black Pearl HTML viewer: progress bootstrap, injected before the page's own scripts.
 * Level 1: mirrors localStorage (the page's own persistence; IndexedDB is kept by WebView itself).
 * Level 2: form fields (input, textarea, select, checkbox, radio) and scroll position.
 * Level 3: attribute changes made by scripts (class, style, disabled, hidden, open, aria-*), keyed by element path.
 * Nothing is clicked or re-run on restore: values and attributes are set directly, and only when the element
 * still has the value it had before the page changed it. Text inserted by scripts is NOT restored.
 * window.__BP_STATE__ is prepended by the app (last saved progress or null).
 */
(function () {
  'use strict';
  var BR = window.BlackPearlProgress;
  var S = window.__BP_STATE__;
  if (!S || typeof S !== 'object') S = {};
  var pageKey = location.pathname + location.search;
  var pages = S.pages && typeof S.pages === 'object' ? S.pages : {};
  var mine = pages[pageKey] || {};
  var dom = mine.dom && typeof mine.dom === 'object' ? mine.dom : {};
  var ATTRS = ['class', 'style', 'disabled', 'hidden', 'open', 'aria-expanded', 'aria-hidden', 'aria-selected',
    'aria-pressed', 'aria-checked', 'aria-current', 'aria-disabled'];
  var MAX_DOM = 3000;
  var ready = false, timer = 0, firstDirty = 0, pending = new Map(), observer = null;

  function lsGet() { try { return window.localStorage; } catch (e) { return null; } }

  // ---- Level 1: restore keys the WebView storage no longer has
  var ls = lsGet();
  if (ls && S.ls && typeof S.ls === 'object') {
    try {
      Object.keys(S.ls).forEach(function (k) { if (ls.getItem(k) === null) ls.setItem(k, S.ls[k]); });
    } catch (e) { /* storage full or blocked */ }
  }

  // ---- element paths: "id:foo/2/0" (anchor + child indexes) or "r/1/3" from <html>
  function pathOf(el) {
    var idx = [];
    var n = el;
    while (n && n.nodeType === 1 && n !== document.documentElement) {
      if (n.id && document.getElementById(n.id) === n) { idx.unshift('id:' + n.id); return idx.join('/'); }
      var p = n.parentElement;
      if (!p) return null;
      idx.unshift(String(Array.prototype.indexOf.call(p.children, n)));
      n = p;
    }
    if (n !== document.documentElement) return null;
    idx.unshift('r');
    return idx.join('/');
  }
  function resolve(path) {
    var parts = String(path).split('/');
    var el = parts[0] === 'r' ? document.documentElement
      : (parts[0].indexOf('id:') === 0 ? document.getElementById(parts[0].slice(3)) : null);
    for (var i = 1; el && i < parts.length; i++) el = el.children[parseInt(parts[i], 10)];
    return el || null;
  }

  // ---- Level 2: forms
  function skipField(el) {
    var t = (el.type || '').toLowerCase();
    return t === 'password' || t === 'file' || t === 'button' || t === 'submit' || t === 'reset' || t === 'image' || t === 'hidden';
  }
  function collectForms() {
    var out = {};
    var els = document.querySelectorAll('input,textarea,select');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (skipField(el)) continue;
      var rec = null, t = (el.type || '').toLowerCase();
      if (el.tagName === 'SELECT') {
        var sel = [], def = [];
        for (var j = 0; j < el.options.length; j++) {
          if (el.options[j].selected) sel.push(j);
          if (el.options[j].defaultSelected) def.push(j);
        }
        if (!el.multiple && def.length === 0) def = el.options.length ? [0] : [];
        if (sel.join() !== def.join()) rec = { s: sel };
      } else if (t === 'checkbox' || t === 'radio') {
        if (el.checked !== el.defaultChecked) rec = { c: el.checked };
      } else if (el.value !== el.defaultValue) {
        rec = { v: el.value };
      }
      if (rec) { var k = pathOf(el); if (k) out[k] = rec; }
    }
    return out;
  }
  function applyForms(forms) {
    if (!forms) return;
    Object.keys(forms).forEach(function (k) {
      var el = resolve(k), r = forms[k];
      if (!el || !r) return;
      try {
        if ('s' in r && el.options) {
          for (var j = 0; j < el.options.length; j++) el.options[j].selected = r.s.indexOf(j) >= 0;
        } else if ('c' in r) { el.checked = !!r.c; } else if ('v' in r) { el.value = r.v; }
      } catch (e) { /* ignore */ }
    });
  }

  // ---- Level 3: attribute changes
  function flushPending() {
    pending.forEach(function (attrs, el) {
      if (!el.isConnected) return;
      var p = pathOf(el);
      if (!p) return;
      attrs.forEach(function (oldValue, a) {
        var key = p + '|' + a;
        var orig = dom[key] ? dom[key].o : oldValue;
        var cur = el.getAttribute(a);
        if (cur === orig) delete dom[key]; else dom[key] = { o: orig, v: cur };
      });
    });
    pending = new Map();
    var keys = Object.keys(dom);
    if (keys.length > MAX_DOM) keys.slice(0, keys.length - MAX_DOM).forEach(function (k) { delete dom[k]; });
  }
  function applyDom() {
    var kept = {};
    Object.keys(dom).forEach(function (key) {
      var cut = key.lastIndexOf('|');
      var el = resolve(key.slice(0, cut)), a = key.slice(cut + 1), r = dom[key];
      if (!el || !r) return;
      var cur = el.getAttribute(a);
      if (cur === r.v) { kept[key] = r; return; }
      if (cur !== r.o) return; // page already changed it by itself: leave it alone
      if (r.v === null) el.removeAttribute(a); else el.setAttribute(a, r.v);
      kept[key] = r;
    });
    dom = kept;
  }

  // ---- snapshot and save
  function snapshot() {
    if (ready) flushPending();
    var page = { forms: collectForms(), dom: dom, y: Math.round(window.pageYOffset || 0) };
    pages[pageKey] = page;
    var lsCopy = null, l = lsGet();
    if (l) {
      lsCopy = {};
      try { for (var i = 0; i < l.length; i++) { var k = l.key(i); lsCopy[k] = l.getItem(k); } } catch (e) { lsCopy = null; }
    }
    return { v: 1, t: Date.now(), ls: lsCopy, pages: pages };
  }
  function save() {
    timer = 0; firstDirty = 0;
    if (!BR || !ready) return;
    var json;
    try { json = JSON.stringify(snapshot()); } catch (e) { return; }
    if (json.length > 1900000) { var s = snapshot(); s.ls = null; json = JSON.stringify(s); }
    try { BR.save(json); } catch (e) { /* bridge gone */ }
  }
  function schedule() {
    var now = Date.now();
    if (!firstDirty) firstDirty = now;
    if (timer) clearTimeout(timer);
    timer = setTimeout(save, now - firstDirty > 3000 ? 0 : 500);
  }
  window.__bpFlush = function () { if (timer) clearTimeout(timer); save(); };

  ['input', 'change', 'click', 'keyup'].forEach(function (ev) { document.addEventListener(ev, schedule, true); });
  window.addEventListener('scroll', schedule, { passive: true });
  window.addEventListener('pagehide', window.__bpFlush);
  document.addEventListener('visibilitychange', function () { if (document.hidden) window.__bpFlush(); });
  try {
    var proto = Storage.prototype, orig = { s: proto.setItem, r: proto.removeItem, c: proto.clear };
    proto.setItem = function () { var r = orig.s.apply(this, arguments); if (this === lsGet()) schedule(); return r; };
    proto.removeItem = function () { var r = orig.r.apply(this, arguments); if (this === lsGet()) schedule(); return r; };
    proto.clear = function () { var r = orig.c.apply(this, arguments); if (this === lsGet()) schedule(); return r; };
  } catch (e) { /* not wrappable */ }

  function restoreScroll(y, tries) {
    if (!y || location.hash) return;
    window.scrollTo(0, y);
    if (Math.abs((window.pageYOffset || 0) - y) > 2 && tries < 20) setTimeout(function () { restoreScroll(y, tries + 1); }, 100);
  }

  document.addEventListener('DOMContentLoaded', function () { applyForms(mine.forms); });
  window.addEventListener('load', function () {
    setTimeout(function () {
      applyForms(mine.forms);
      applyDom();
      restoreScroll(mine.y, 0);
      try {
        observer = new MutationObserver(function (recs) {
          recs.forEach(function (r) {
            var m = pending.get(r.target);
            if (!m) { m = new Map(); pending.set(r.target, m); }
            if (!m.has(r.attributeName)) m.set(r.attributeName, r.oldValue);
          });
          schedule();
        });
        observer.observe(document.documentElement,
          { attributes: true, attributeOldValue: true, subtree: true, attributeFilter: ATTRS });
      } catch (e) { /* no MutationObserver */ }
      ready = true;
    }, 50);
  });
})();
