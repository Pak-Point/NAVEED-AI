const CACHE = "naveed-ai-v2";
const BASE = new URL("./", self.location.href);
const FILES = [BASE.href, new URL("index.html", BASE).href, new URL("manifest.json", BASE).href];
self.addEventListener("install", event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES))));
self.addEventListener("activate", event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))));
self.addEventListener("fetch", event => {
  if (event.request.method !== "GET" || new URL(event.request.url).origin !== self.location.origin) return;
  event.respondWith(fetch(event.request).catch(() => caches.match(event.request).then(hit => hit || caches.match(new URL("index.html", BASE).href))));
});
