function (P) {
  var filled = [];
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
  var skip = ['hidden', 'checkbox', 'radio', 'submit', 'button', 'password', 'file', 'image', 'reset'];
  var nodes = document.querySelectorAll('input, textarea');
  for (var i = 0; i < nodes.length; i++) {
    var el = nodes[i];
    var type = (el.getAttribute('type') || 'text').toLowerCase();
    if (skip.indexOf(type) !== -1) continue;
    if (el.value || el.disabled || el.readOnly) continue;
    if (hiddenOne(el)) continue;
    var t = labelOf(el);
    var v = '', what = '';
    if (type === 'email' || /e-?mail/.test(t)) { v = P.email; what = 'email'; }
    else if (type === 'url' || /url|link|profile|listing/.test(t)) { v = P.url; what = 'listing link'; }
    else if (/first/.test(t)) { v = P.first; what = 'first name'; }
    else if (/middle/.test(t)) { v = P.middle; what = 'middle name'; }
    else if (/last|surname/.test(t)) { v = P.last; what = 'last name'; }
    else if (/zip|postal/.test(t)) { v = P.zip; what = 'ZIP'; }
    else if (type === 'tel' || /phone|mobile/.test(t)) { v = P.phone; what = 'phone'; }
    else if (/street|address/.test(t)) { v = P.street; what = 'street'; }
    else if (/city|town/.test(t)) { v = P.city; what = 'city'; }
    else if (/\bstate\b/.test(t)) { v = P.st; what = 'state'; }
    else if (/name/.test(t)) { v = P.full; what = 'name'; }
    if (v) { setVal(el, v); filled.push(what); }
  }
  var sels = document.querySelectorAll('select');
  for (var s = 0; s < sels.length; s++) {
    var sel = sels[s];
    if (sel.disabled || hiddenOne(sel) || sel.selectedIndex > 0) continue;
    if (!/\bstate\b/.test(labelOf(sel)) || !P.st) continue;
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
