function (P) {
  function norm(s) {
    return (' ' + String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ') + ' ').replace(/\s+/g, ' ');
  }
  var body = document.body;
  var text = body ? (body.innerText || '') : '';
  var title = String(document.title || '');
  var low = (title + ' ' + text).toLowerCase();
  var out = { blocked: false, echo: false, none: false, chars: text.length, title: title.slice(0, 70), listings: [] };

  var WALL = /just a moment|verify you are (a )?human|verifying you are human|are you a robot|are you human|attention required|access denied|unusual traffic|press & hold|press and hold|checking your browser|checking if the site connection is secure|enable javascript and cookies|pardon our interruption|you have been blocked|security check|complete the captcha|human verification/;
  var frame = document.querySelector('iframe[src*="challenges.cloudflare.com"], iframe[src*="hcaptcha.com"], iframe[src*="captcha-delivery.com"], #challenge-form, #px-captcha');
  if ((WALL.test(low) || frame) && text.length < 3000) {
    out.blocked = true;
    return JSON.stringify(out);
  }
  out.none = /no (results|records|matches|people) (were )?found|no results for|returned no results|(did not|didn['\u2019]t|could not|couldn['\u2019]t) find any|(^|[^0-9])0 (results|records|matches)|we found 0\b/.test(low);

  var names = [];
  function addName(f, l) {
    var nf = norm(f), nl = norm(l);
    if (nf.length > 2 && nl.length > 2) names.push([nf, nl]);
  }
  addName(P.first, P.last);
  var others = P.otherNames || [];
  for (var j = 0; j < others.length; j++) {
    var parts = String(others[j]).trim().split(/\s+/);
    if (parts.length >= 2) addName(parts[0], parts[parts.length - 1]);
  }
  function nameIn(h) {
    for (var k = 0; k < names.length; k++) {
      if (h.indexOf(names[k][0]) !== -1 && h.indexOf(names[k][1]) !== -1) return true;
    }
    return false;
  }
  out.echo = nameIn(norm(low));

  var locs = P.locations || [];
  function stateIn(raw, h, L) {
    if (h.indexOf(norm(L.state)) !== -1) return true;
    return new RegExp('(^|[^A-Za-z])' + L.st + '([^A-Za-z]|$)').test(raw);
  }
  function locIn(raw, h) {
    var best = 0;
    for (var k = 0; k < locs.length; k++) {
      var s = stateIn(raw, h, locs[k]);
      if (s && h.indexOf(norm(locs[k].city)) !== -1) return 2;
      if (s) best = 1;
    }
    return best;
  }
  function ageCheck(raw) {
    if (!P.age) return 0;
    var m = raw.match(/\bage[^0-9]{0,14}(\d{2,3})(?:\s*[-\u2013]\s*(\d{2,3}))?(s)?/i);
    if (m) {
      var a = parseInt(m[1], 10);
      if (a >= 18 && a <= 115) {
        if (m[2]) return (P.age >= a - 1 && P.age <= parseInt(m[2], 10) + 1) ? 1 : -1;
        if (m[3]) return (P.age >= a - 1 && P.age <= a + 10) ? 1 : -1;
        return Math.abs(a - P.age) <= 2 ? 1 : -1;
      }
    }
    var y = raw.match(/\bborn[^0-9]{0,20}((?:19|20)\d{2})/i);
    if (y && P.birthYear) return Math.abs(parseInt(y[1], 10) - P.birthYear) <= 1 ? 1 : -1;
    return 0;
  }

  var EV = /\b(relatives?|related to|lived in|lives in|resides in|also known as|aka|born|view details|view profile|view record|view report|open report|full report)\b/i;
  var HARD = /\(?\b\d{3}\)?[-. ]\d{3}[-. ]\d{4}\b|\b\d{5}(-\d{4})?\b|\b\d{1,6}\s+\w+(\s\w+)?\s(st|ave|rd|dr|ln|blvd|ct|way|hwy|pkwy|cir|pl|trl|street|avenue|road|drive|lane|court)\b/i;
  var AD = /sponsored|advertisement/i;

  var lens = new Map();
  function tlen(node) {
    var v = lens.get(node);
    if (v === undefined) { v = (node.innerText || '').length; lens.set(node, v); }
    return v;
  }
  var grades = new Map();
  function grade(node) {
    var g = grades.get(node);
    if (g !== undefined) return g;
    var raw = node.innerText || '';
    var h = norm(raw);
    g = 0;
    if (nameIn(h)) {
      var ac = ageCheck(raw);
      if (ac !== -1) {
        var lc = locIn(raw, h);
        if (ac === 1 && lc === 2) g = 2;
        else if (ac === 1 && lc === 1) g = 1;
        else if (lc === 2 && (EV.test(raw) || HARD.test(raw))) g = 1;
      }
    }
    grades.set(node, g);
    return g;
  }
  function isAd(node) {
    var base = tlen(node), up = node;
    for (var i = 0; i < 3 && up && up !== body; i++) {
      if (tlen(up) > base + 120) break;
      if (AD.test(up.innerText || '')) return true;
      up = up.parentElement;
    }
    return false;
  }
  var site = location.hostname.replace(/^www\./, '');
  function sameSite(href) {
    try {
      var hn = new URL(href).hostname;
      return hn === site || hn.slice(-(site.length + 1)) === '.' + site;
    } catch (e) {
      return false;
    }
  }

  var picked = [];
  var anchors = document.querySelectorAll('a[href]');
  var max = Math.min(anchors.length, 1500);
  for (var x = 0; x < max && out.listings.length < 8; x++) {
    var el = anchors[x], pick = null, weak = null;
    for (var d = 0; d < 8 && el && el !== body; d++) {
      var len = tlen(el);
      if (len > 900) break;
      if (len >= 25) {
        var g = grade(el);
        if (g === 2) { pick = el; break; }
        if (g === 1 && !weak) weak = el;
      }
      el = el.parentElement;
    }
    var strong = !!pick;
    if (!pick) pick = weak;
    if (!pick) continue;
    var dup = false;
    for (var z = 0; z < picked.length; z++) {
      if (picked[z].contains(pick) || pick.contains(picked[z])) { dup = true; break; }
    }
    if (dup) continue;
    if (isAd(pick)) continue;
    var link = '';
    if (pick.tagName === 'A' && sameSite(pick.href || '')) link = pick.href;
    var inner = pick.querySelectorAll('a[href]');
    for (var y2 = 0; y2 < inner.length; y2++) {
      var href = inner[y2].href || '';
      if (!sameSite(href)) continue;
      if (!link) link = href;
      if (nameIn(norm(inner[y2].innerText || ''))) { link = href; break; }
    }
    if (!link) continue;
    picked.push(pick);
    out.listings.push({ url: link, strong: strong, text: (pick.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 220) });
  }
  var strongOnly = [];
  for (var s2 = 0; s2 < out.listings.length; s2++) {
    if (out.listings[s2].strong) strongOnly.push(out.listings[s2]);
  }
  if (strongOnly.length) out.listings = strongOnly;

  if (!out.listings.length && out.echo && !out.none && ageCheck(text) === 1 && locIn(text, norm(text)) === 2) {
    out.listings.push({ url: location.href, strong: false, text: ('Matched on page text: ' + title).slice(0, 220) });
  }
  return JSON.stringify(out);
}
