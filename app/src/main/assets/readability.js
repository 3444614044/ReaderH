/**
 * 精简版 Readability：无任何外部依赖，注入到 WebView 页面后抽取正文。
 * 返回一个 JSON 字符串：{title, byline, content, text, length, nextPageUrl, siteName}
 */
(function () {
  function nodeText(el) {
    if (!el) return '';
    return (el.textContent || '').replace(/\s+/g, ' ').trim();
  }

  function getClassId(el) {
    return ((el.className || '') + ' ' + (el.id || '')).toString().toLowerCase();
  }

  var NEGATIVE = /comment|comment-|footer|header|nav|sidebar|advert|ads|banner|share|social|related|recommend|popup|modal|menu|breadcrumb|pagination|copyright|toolbar|widget|qrcode|wechat|app-download/;
  var POSITIVE = /article|content|entry|post|main|body|text|story|detail|news|chapter|read/;

  function cleanDocument(doc) {
    var kill = doc.querySelectorAll('script,style,noscript,iframe,svg,canvas,video,audio,form,input,button,select,textarea');
    for (var i = 0; i < kill.length; i++) {
      var k = kill[i];
      if (k.parentNode) k.parentNode.removeChild(k);
    }
    var blocks = doc.querySelectorAll('nav,header,footer,aside');
    for (var j = 0; j < blocks.length; j++) {
      var b = blocks[j];
      if (b.getElementsByTagName('p').length < 2 && b.parentNode) b.parentNode.removeChild(b);
    }
  }

  function isHidden(el) {
    var s = window.getComputedStyle ? window.getComputedStyle(el) : null;
    if (!s) return false;
    if (s.display === 'none' || s.visibility === 'hidden') return true;
    if (parseFloat(s.opacity || '1') < 0.1) return true;
    var r = el.getBoundingClientRect();
    return r.width < 2 || r.height < 2;
  }

  function scoreElement(el) {
    var score = 0;
    var ci = getClassId(el);
    if (POSITIVE.test(ci)) score += 30;
    if (NEGATIVE.test(ci)) score -= 40;
    switch (el.tagName) {
      case 'ARTICLE': score += 30; break;
      case 'MAIN': score += 20; break;
      case 'SECTION': score += 5; break;
      case 'DIV': score += 5; break;
      default: break;
    }
    var text = nodeText(el);
    score += Math.min(Math.floor(text.length / 120), 40);
    var commas = (text.match(/[，,。！？；]/g) || []).length;
    score += Math.min(commas, 30);
    var ps = el.getElementsByTagName('p').length;
    score += Math.min(ps * 3, 30);
    var imgs = el.getElementsByTagName('img').length;
    score += Math.min(imgs * 2, 10);
    var links = el.getElementsByTagName('a');
    var linkText = 0;
    for (var i = 0; i < links.length; i++) linkText += nodeText(links[i]).length;
    if (text.length > 0 && linkText / text.length > 0.5) score -= 25;
    return score;
  }

  function pickBest(doc) {
    var candidates = doc.querySelectorAll('article,main,section,div,td');
    var best = null, bestScore = -9999;
    for (var i = 0; i < candidates.length; i++) {
      var el = candidates[i];
      if (isHidden(el)) continue;
      var s = scoreElement(el);
      var parent = el.parentNode;
      var depth = 0;
      while (parent && parent.nodeType === 1 && depth < 3) {
        s -= 6;
        parent = parent.parentNode;
        depth++;
      }
      if (s > bestScore) { bestScore = s; best = el; }
    }
    var body = doc.body;
    if (!best || bestScore < 20) {
      var bodyScore = scoreElement(body);
      if (!best || bodyScore >= bestScore) best = body;
    }
    return best;
  }

  function absolutize(doc, base) {
    var imgs = doc.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      var src = img.getAttribute('data-src') || img.getAttribute('data-original') || img.getAttribute('data-actualsrc') || img.getAttribute('src');
      if (!src) { if (img.parentNode) img.parentNode.removeChild(img); continue; }
      if (/^\/\//.test(src)) src = location.protocol + src;
      try { img.setAttribute('src', new URL(src, base).href); } catch (e) { }
      img.removeAttribute('srcset');
      img.removeAttribute('loading');
      img.removeAttribute('style');
      img.setAttribute('style', 'max-width:100%;height:auto;display:block;margin:12px auto;');
    }
    var as = doc.querySelectorAll('a');
    for (var k = 0; k < as.length; k++) {
      var a = as[k];
      var href = a.getAttribute('href');
      if (href) { try { a.setAttribute('href', new URL(href, base).href); } catch (e) { } }
    }
  }

  function simplify(root) {
    var nodes = root.querySelectorAll('*');
    for (var i = nodes.length - 1; i >= 0; i--) {
      var el = nodes[i];
      if (!el.parentNode) continue;
      var ci = getClassId(el);
      if (NEGATIVE.test(ci)) { el.parentNode.removeChild(el); continue; }
      var t = nodeText(el);
      if (el.tagName !== 'IMG' && t.length === 0 && el.getElementsByTagName('img').length === 0) {
        el.parentNode.removeChild(el);
        continue;
      }
      if (el.tagName === 'P' && t.length < 8 && el.getElementsByTagName('img').length === 0) {
        el.parentNode.removeChild(el);
      }
      ['class', 'style', 'id', 'width', 'height', 'align', 'bgcolor', 'onclick'].forEach(function (attr) {
        if (el.tagName === 'IMG' && attr === 'style') return;
        el.removeAttribute(attr);
      });
    }
  }

  function findNext(doc, base) {
    var link = doc.querySelector('link[rel=next]');
    if (link && link.getAttribute('href')) {
      try { return new URL(link.getAttribute('href'), base).href; } catch (e) { }
    }
    var as = doc.querySelectorAll('a');
    var words = ['下一页', '下一章', '下一节', '下页', '后章', 'next', '>', '»'];
    for (var i = 0; i < as.length; i++) {
      var a = as[i];
      var t = nodeText(a).toLowerCase();
      for (var w = 0; w < words.length; w++) {
        if (t === words[w] || t.indexOf(words[w]) === 0 && t.length < 12) {
          var href = a.getAttribute('href');
          if (href && href.indexOf('javascript') !== 0) {
            try { return new URL(href, base).href; } catch (e) { }
          }
        }
      }
    }
    return '';
  }

  function byTitle(doc) {
    var meta = doc.querySelector('meta[property="og:title"],meta[name="twitter:title"]');
    if (meta && meta.getAttribute('content')) return meta.getAttribute('content').trim();
    var h1 = doc.querySelector('h1');
    if (h1 && nodeText(h1)) return nodeText(h1);
    return (doc.title || '').trim();
  }

  var result = { title: '', byline: '', content: '', text: '', length: 0, nextPageUrl: '', siteName: '' };
  try {
    var base = location.href;
    var doc = document;
    result.siteName = (function () {
      var m = doc.querySelector('meta[property="og:site_name"]');
      if (m && m.getAttribute('content')) return m.getAttribute('content').trim();
      try { return location.hostname; } catch (e) { return ''; }
    })();
    cleanDocument(doc);
    var best = pickBest(doc);
    var root = best || doc.body;
    absolutize(doc, base);
    simplify(root);
    var html = root.innerHTML;
    result.title = byTitle(doc);
    var authorMeta = doc.querySelector('meta[name=author],meta[property="article:author"]');
    result.byline = authorMeta ? (authorMeta.getAttribute('content') || '').trim() : '';
    result.content = html;
    result.text = nodeText(root);
    result.length = result.text.length;
    result.nextPageUrl = findNext(doc, base);
  } catch (e) {
    result.title = doc.title || '';
    result.content = doc.body ? doc.body.innerHTML : '';
    result.text = doc.body ? (doc.body.textContent || '') : '';
    result.length = result.text.length;
  }
  return JSON.stringify(result);
})();
