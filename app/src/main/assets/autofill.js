function (P) {
  var filled = [];
  function setVal(el, v) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) desc.set.call(el, v); else el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
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
  var skip = ['hidden', 'checkbox', 'radio', 'submit', 'button', 'password', 'file', 'image', 'reset'];
  var nodes = document.querySelectorAll('input, textarea');
  for (var i = 0; i < nodes.length; i++) {
    var el = nodes[i];
    var type = (el.getAttribute('type') || 'text').toLowerCase();
    if (skip.indexOf(type) !== -1) continue;
    if (el.value || el.disabled || el.readOnly) continue;
    if (el.offsetParent === null) continue;
    var t = labelOf(el);
    var v = '', what = '';
    if (type === 'email' || /e-?mail/.test(t)) { v = P.email; what = 'email'; }
    else if (type === 'url' || /url|link|profile|listing/.test(t)) { v = P.url; what = 'listing link'; }
    else if (/first/.test(t)) { v = P.first; what = 'first name'; }
    else if (/last|surname/.test(t)) { v = P.last; what = 'last name'; }
    else if (/name/.test(t)) { v = P.full; what = 'name'; }
    else if (/city/.test(t)) { v = P.city; what = 'city'; }
    else if (/\bstate\b/.test(t)) { v = P.st; what = 'state'; }
    if (v) { setVal(el, v); filled.push(what); }
  }
  return JSON.stringify({ filled: filled });
}
