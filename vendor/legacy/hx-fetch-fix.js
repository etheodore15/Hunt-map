// The fetch polyfill can't handle file:// URLs (XHR status 0) — route
// relative/local requests through a bare XHR shim instead.
(function () {
  var realFetch = window.fetch;
  window.fetch = function (url, opts) {
    var u = String(url);
    if (!/^https?:/i.test(u)) {
      return new Promise(function (resolve, reject) {
        var x = new XMLHttpRequest();
        x.open('GET', u, true);
        x.onload = x.onerror = function () {
          if (x.responseText && x.responseText.length) {
            resolve({
              ok: true, status: 200,
              json: function () {
                try { return Promise.resolve(JSON.parse(x.responseText)); }
                catch (e) { return Promise.reject(e); }
              },
              text: function () { return Promise.resolve(x.responseText); }
            });
          } else {
            reject(new Error('load failed: ' + u));
          }
        };
        x.send();
      });
    }
    return realFetch.call(window, url, opts);
  };
})();
