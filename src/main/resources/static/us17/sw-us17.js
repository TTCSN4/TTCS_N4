"use strict";
const CACHE = "meeting-us17-mobile-us17-v5";
const BASE = new URL("./", self.location.href);
const SHELL = ["mobile-meetings.html", "css/mobile-meetings.css", "js/mobile-meetings.js", "manifest-us17.webmanifest", "icons/meeting-192.png", "icons/meeting-512.png"].map(path => new URL(path, BASE).href);
const PAGE = SHELL[0];
self.addEventListener("install", event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)));
});
self.addEventListener("activate", event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith("meeting-us17-mobile-us17-") && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", event => {
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  const canonical = `${url.origin}${url.pathname}`;
  // Cache only this app's shell. Never intercept other pages or APIs.
  if (!SHELL.includes(canonical)) return;
  event.respondWith((async () => {
    try {
      const response = await fetch(event.request);
      if (response.ok) { const cache = await caches.open(CACHE); await cache.put(canonical, response.clone()); return response; }
      return await caches.match(canonical) || response;
    } catch (_) {
      return await caches.match(canonical) || (event.request.mode === "navigate" ? await caches.match(PAGE) : undefined) || Response.error();
    }
  })());
});
