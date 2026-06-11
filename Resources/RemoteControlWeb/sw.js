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

const CACHE_VERSION = 'v2-dev';
const CACHE_NAME = 'vibestudio-shell-' + CACHE_VERSION;

const SHELL_ASSETS = [
  '/',
  '/index.html',
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
      return cache.addAll(SHELL_ASSETS).catch(function (_e) {
        // Partial cache is acceptable — runtime fetches will retry.
      });
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
    // Offline + uncached → minimal fallback.
    return new Response('Offline', { status: 503, statusText: 'Offline' });
  })());
});
