// service-worker.js - HEmax Service Worker
// Обеспечивает работу в офлайн-режиме и быструю загрузку

const CACHE_NAME = 'hemax-v2.0';
const urlsToCache = [
  '/',
  '/index.html',
  '/manifest.json'
];

// Установка service worker
self.addEventListener('install', event => {
  console.log('[SW] Установка...');
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('[SW] Кэширование файлов');
        return cache.addAll(urlsToCache);
      })
      .then(() => self.skipWaiting())
  );
});

// Активация и очистка старых кэшей
self.addEventListener('activate', event => {
  console.log('[SW] Активация...');
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(cacheName => {
          if (cacheName !== CACHE_NAME) {
            console.log('[SW] Удаление старого кэша:', cacheName);
            return caches.delete(cacheName);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Перехват запросов
self.addEventListener('fetch', event => {
  // Не кэшируем WebSocket и API-запросы
  if (event.request.url.includes('/ws/') || 
      event.request.url.includes('/api/') ||
      event.request.method !== 'GET') {
    return;
  }
  
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        // Возвращаем из кэша, если есть
        if (response) {
          return response;
        }
        
        // Иначе запрашиваем с сервера
        return fetch(event.request).then(networkResponse => {
          // Не кэшируем ошибки
          if (!networkResponse || networkResponse.status !== 200) {
            return networkResponse;
          }
          
          // Кэшируем успешные ответы
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME)
            .then(cache => {
              cache.put(event.request, responseToCache);
            });
          return networkResponse;
        });
      })
      .catch(() => {
        // Офлайн-режим: показываем заглушку
        if (event.request.url.includes('/')) {
          return caches.match('/index.html');
        }
        return new Response('Вы офлайн. Подключитесь к интернету.', {
          status: 503,
          statusText: 'Service Unavailable'
        });
      })
  );
});

// Обработка push-уведомлений (для будущей реализации)
self.addEventListener('push', event => {
  const data = event.data ? event.data.json() : {};
  const options = {
    body: data.body || 'Новое сообщение',
    icon: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"%3E%3Crect width="100" height="100" fill="%23000000"/%3E%3Ctext x="50" y="70" font-size="60" text-anchor="middle" fill="%23ffffff"%3E#%3C/text%3E%3C/svg%3E',
    badge: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"%3E%3Crect width="100" height="100" fill="%23000000"/%3E%3Ctext x="50" y="70" font-size="60" text-anchor="middle" fill="%23ffffff"%3E#%3C/text%3E%3C/svg%3E',
    vibrate: [200, 100, 200],
    data: {
      url: data.url || '/'
    }
  };
  
  event.waitUntil(
    self.registration.showNotification(data.title || 'HEmax', options)
  );
});

// Обработка клика по уведомлению
self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil(
    clients.openWindow(event.notification.data.url || '/')
  );
});
