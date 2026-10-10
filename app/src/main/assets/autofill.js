function (P) {
  var filled = [];
  function keyOf(el) {
    return [el.name, el.id, el.placeholder].join('|');
  }
  // A box you have typed in or cleared yourself is yours: it is never filled again.
  if (!window.__gdWatch) {
    window.__gdWatch = true;
    document.addEventListener('input', function (e) {
      try {
        if (!e.isTrusted) return;
        var path = e.composedPath ? e.composedPath() : [];
        var own = path.length ? path[0] : e.target;
        if (own && own.setAttribute) own.setAttribute('data-gd-mine', keyOf(own));
      } catch (x) {
      }
    }, true);
  }
  // The box you tapped last, so a chip still knows where to type after focus leaves the page.
  if (!window.__gdFocus) {
    window.__gdFocus = true;
    document.addEventListener('focusin', function (e) {
      try {
        var path = e.composedPath ? e.composedPath() : [];
        var t = path.length ? path[0] : e.target;
        if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) window.__gdLast = t;
      } catch (x) {
      }
    }, true);
  }
  function mine(el) {
    return el.getAttribute('data-gd-mine') === keyOf(el);
  }
  function fire(el) {
    var evs = ['keydown', 'keypress', 'input', 'keyup', 'change', 'blur'];
    for (var k = 0; k < evs.length; k++) el.dispatchEvent(new Event(evs[k], { bubbles: true }));
  }
  function setVal(el, v) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) desc.set.call(el, v); else el.value = v;
    fire(el);
  }
  function labelOf(el) {
    var t = [el.name, el.id, el.placeholder, el.getAttribute('aria-label'), el.getAttribute('autocomplete')].join(' ');
    try {
      if (el.id) {
        var lab = document.querySelector('label[for="' + el.id.replace(/"/g, '') + '"]');
        if (lab) t += ' ' + lab.innerText;
      }
      var wrap = el.closest('label');
      if (wrap) t += ' ' + wrap.innerText;
    } catch (e) {
    }
    return t.toLowerCase();
  }
  function hiddenOne(el) {
    if (el.offsetParent === null || el.tabIndex === -1) return true;
    var rc = el.getBoundingClientRect();
    var cs = window.getComputedStyle(el);
    if (rc.width < 8 || rc.height < 8 || rc.right < 0 || rc.bottom < 0) return true;
    return cs.visibility === 'hidden' || cs.opacity === '0';
  }
  function inSearch(el) {
    try {
      if ((el.getAttribute('type') || '').toLowerCase() === 'search') return true;
      if ((el.getAttribute('role') || '').toLowerCase() === 'searchbox') return true;
      return !!el.closest('header, nav, [role="search"], [role="banner"]');
    } catch (e) {
      return false;
    }
  }
  // Boxes about someone filing on your behalf are left alone, whatever they ask for.
  var AGENT = /agent|representative|behalf|guardian|attorney/;
  // A "name" box that is not asking for your own name.
  var NOTME = /compan|business|organi[sz]ation|employer|institution|agenc|(^|[^a-z])(firm|entity)|domain|user[ _-]?name|login|nick|maiden|alias|former|previous|other names?/;
  // A second address line or an old address stays empty.
  var NOTSTREET = /line[ _-]?2|address[ _-]?2|addr[ _-]?2|street[ _-]?2|previous|former|prior/;
  // A box for a code they text or email you never gets your phone number, email or link.
  var CODE = /(^|[^a-z])otp|otp(code|input|field|token|[^a-z]|$)|totp|passcode|one[ _-]?time|(verification|verify|security|confirmation|confirm|access|auth|sms|text|login|2fa)[ _-]?code|\d[ -]?digit (code|verification|pin)|enter (the |your |a )?code|code (we|you|that|was|sent|from)|sent (you )?(a|the|your) code/;
  var CODEWORD = /(^|[^a-z])code([^a-z]|$)/;
  // A box for your phone's advertising ID is not a phone number box.
  var ADID = /advertis|(^|[^a-z])(maid|idfa|aaid|gaid|ad[ _-]?id)([^a-z]|$)|(device|mobile)[ _-]?id([^a-z]|$)/;
  function codeBox(el, t) {
    var max = parseInt(el.getAttribute('maxlength') || '0', 10);
    return CODE.test(t) || (max > 0 && max < 10);
  }
  var skip = ['hidden', 'checkbox', 'radio', 'submit', 'button', 'password', 'file', 'image', 'reset'];
  var nodes = document.querySelectorAll('input, textarea');
  for (var i = 0; i < nodes.length; i++) {
    var el = nodes[i];
    var type = (el.getAttribute('type') || 'text').toLowerCase();
    if (skip.indexOf(type) !== -1) continue;
    if (el.value || el.disabled || el.readOnly) continue;
    if (hiddenOne(el) || inSearch(el) || mine(el)) continue;
    var t = labelOf(el);
    if (AGENT.test(t) || /\bssn\b|social sec/.test(t)) continue;
    var v = '', what = '';
    var code = codeBox(el, t);
    if (type === 'email' || /e-?mail/.test(t)) { if (type === 'email' || !code) { v = P.email; what = 'email'; } }
    else if (type === 'url' || /url|link|profile|listing/.test(t)) { if (type === 'url' || !code) { v = P.url; what = 'listing link'; } }
    else if (/first|given/.test(t) && /last|surname|family/.test(t)) { v = P.full; what = 'name'; }
    else if (/first|given/.test(t)) { v = P.first; what = 'first name'; }
    else if (/middle/.test(t)) { v = P.middle; what = 'middle name'; }
    else if (/last|surname|family/.test(t)) { v = P.last; what = 'last name'; }
    else if (/zip|postal/.test(t)) { v = P.zip; what = 'ZIP'; }
    else if (type === 'tel' || /phone|mobile|(^|[^a-z])tel([^a-z]|$)/.test(t)) {
      if (!code && !ADID.test(t) && (/phone|mobile|cell|(^|[^a-z])tel([^a-z]|$)/.test(t) || !CODEWORD.test(t))) { v = P.phone; what = 'phone'; }
    }
    else if (/street|address/.test(t)) { if (!NOTSTREET.test(t)) { v = P.street; what = 'street'; } }
    else if (/city|town/.test(t)) { v = P.city; what = 'city'; }
    else if (/\bstate\b/.test(t)) { v = P.st; what = 'state'; }
    else if (/name/.test(t)) { if (!NOTME.test(t)) { v = P.full; what = 'name'; } }
    if (v && /search/.test(t) && what.indexOf('name') === -1) v = '';
    if (v) { setVal(el, v); filled.push(what); }
  }
  var sels = document.querySelectorAll('select');
  for (var s = 0; s < sels.length; s++) {
    var sel = sels[s];
    if (sel.disabled || hiddenOne(sel) || sel.selectedIndex > 0 || mine(sel)) continue;
    var st = labelOf(sel);
    if (AGENT.test(st) || !/\bstate\b/.test(st) || !P.st) continue;
    for (var o = 0; o < sel.options.length; o++) {
      var val = (sel.options[o].value || '').trim().toUpperCase();
      var txt = (sel.options[o].text || '').trim().toUpperCase();
      if (val === P.st || txt === P.st || (P.stateName && txt === P.stateName.toUpperCase())) {
        sel.selectedIndex = o;
        fire(sel);
        filled.push('state');
        break;
      }
    }
  }
  return JSON.stringify({ filled: filled });
}
