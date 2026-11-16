// PureGames service worker: keeps a copy of the whole app on the device so it starts fast and
// plays offline. web/build.gradle.kts writes the real sw.js from this file on every production
// build, filling in VERSION (a hash of every file) and FILES (everything to keep).
//
// Each build is one cache. A new deploy changes VERSION, so the browser installs the new worker,
// which downloads the new build into a fresh cache while the old one keeps serving. Once it has
// everything it takes over straight away and reloads every open window into the new version, so a
// deploy reaches everyone within minutes. Saves are kept as you play, so a reload carries on. A
// first visit (no older version) is never reloaded.

const VERSION = '__VERSION__';
const FILES = __FILES__;
const CACHE = 'puregames-' + VERSION;

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(FILES.map((f) => new Request(f, { cache: 'reload' }))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      const old = keys.filter((k) => k.startsWith('puregames-') && k !== CACHE);
      return Promise.all(old.map((k) => caches.delete(k)))
        .then(() => self.clients.claim())
        // an update: bring every open window onto the new version
        .then(() => old.length === 0 ? null : self.clients.matchAll({ type: 'window' })
          .then((wins) => Promise.all(wins.map((w) => w.navigate(w.url).catch(() => null)))));
    })
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  const url = new URL(req.url);
  // Online play talks to the room-code service live: never cache it.
  if (url.pathname.startsWith('/api/')) return;
  // The page itself: this build's copy (so code and page always match), the network if not cached.
  const key = req.mode === 'navigate' ? './' : url.pathname.replace(/^\//, '') || './';
  event.respondWith(
    caches.open(CACHE).then((cache) =>
      cache.match(key, { ignoreSearch: true }).then((hit) => hit || fetch(req).then((res) => {
        if (res.ok && res.type === 'basic') cache.put(req, res.clone());
        return res;
      }).catch(() => cache.match('./')))
    )
  );
});
