/* VibeStudio Remote — Service Worker
 *
 * Caches the static shell (index.html, app.js, app.css, xterm vendor bundle)
 * so the SPA boots instantly on repeat visits and survives a brief network
 * outage. API endpoints (/api/*) and WebSocket traffic (/ws/*) are NEVER
 * cached — they MUST hit the origin so auth/state stays correct.
 *
 * Strategy:
 *   - Shell assets: cache-first (with stale-while-revalidate refresh).
 *   - Everything else: network-only.
 *
 * Cache name is versioned; bump CACHE_VERSION to roll out changes.
 */

const CACHE_VERSION = 'v9';
const CACHE_NAME = 'vibestudio-shell-' + CACHE_VERSION;

const SHELL_ASSETS = [
  '/',
  '/app.js',
  '/app.css',
  '/vendor/xterm.min.js',
  '/vendor/xterm.min.css',
  '/vendor/xterm-addon-fit.min.js',
  '/vendor/xterm-addon-web-links.min.js'
];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function (cache) {
      // Cache each asset INDEPENDENTLY (not cache.addAll). addAll is
      // all-or-nothing: a single failing request (e.g. a 404) aborted the
      // whole install and left the cache empty, so returning users — whose
      // SW now controls the page — were served the "Offline" fallback even
      // though the server was reachable. Per-asset caching tolerates a bad
      // entry and still caches the rest, so the shell always boots.
      return Promise.all(SHELL_ASSETS.map(function (url) {
        return cache.add(url).catch(function (_e) { /* tolerate one bad asset */ });
      }));
    }).then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.map(function (k) {
        if (k !== CACHE_NAME) return caches.delete(k);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (event) {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  if (url.origin !== location.origin) return;

  // Never cache API or WebSocket traffic.
  if (url.pathname.startsWith('/api/')) return;
  if (url.pathname.startsWith('/ws/')) return;

  // Shell assets — cache-first with background revalidate.
  event.respondWith((async function () {
    const cache = await caches.open(CACHE_NAME);
    const cached = await cache.match(req, { ignoreSearch: true });
    const networkPromise = fetch(req).then(function (resp) {
      if (resp && resp.ok && resp.type === 'basic') {
        cache.put(req, resp.clone()).catch(function () {});
      }
      return resp;
    }).catch(function () { return null; });

    if (cached) {
      // Refresh in background.
      networkPromise;
      return cached;
    }
    const fresh = await networkPromise;
    if (fresh) return fresh;

    // Network failed and nothing cached. For a top-level navigation, fall back
    // to the cached app shell ('/') so the SPA still boots and can show the
    // PIN/terminal once the connection recovers — far better than a dead
    // "Offline" page. Only if even '/' is uncached do we emit the 503.
    if (req.mode === 'navigate') {
      const shell = await cache.match('/', { ignoreSearch: true });
      if (shell) return shell;
    }
    return new Response('Offline', { status: 503, statusText: 'Offline' });
  })());
});
