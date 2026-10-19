# Spec Delta

## Purpose

PWA-оболочка приложения: web app manifest и service worker production-сборки
с семантикой регистрации и обновления. Dev-режим service worker не регистрирует.

## ADDED Requirements

### Requirement: PWA-001 Манифест приложения
Production-сборка SHALL поставлять `manifest.webmanifest` по пути монтирования
приложения: name и short_name продукта, `display: standalone`, стартовый URL и
scope в пределах mount, иконки 192x192, 512x512 и maskable 512x512.

#### Scenario: PWA-001a Отдача манифеста
- **WHEN** запрашивается `manifest.webmanifest` у развёрнутого приложения
- **THEN** возвращается манифест с перечисленными полями и доступными иконками

### Requirement: PWA-002 Service worker production-сборки
Production-сборка SHALL генерировать `sw.js` с precache оболочки и
навигационным fallback на `index.html`; fallback SHALL NOT применяться к
`/api/` и статическим ресурсам. Service worker использует autoUpdate,
skipWaiting, clientsClaim и cleanupOutdatedCaches.

#### Scenario: PWA-002a Отдача service worker
- **WHEN** запрашивается `sw.js` у развёрнутого приложения
- **THEN** возвращается service worker с precache оболочки и ограниченным
  навигационным fallback

### Requirement: PWA-003 Регистрация и обновление
Клиент SHALL регистрировать service worker в production-сборке по URL в пределах
mount приложения и пропускать регистрацию в dev-режиме. Перезагрузка страницы
выполняется один раз только при замене существующего controller.

#### Scenario: PWA-003a Пропуск в dev
- **WHEN** приложение работает в dev-режиме сборки
- **THEN** регистрация service worker не выполняется

#### Scenario: PWA-003b Первый захват без перезагрузки
- **WHEN** service worker впервые становится controller страницы
- **THEN** перезагрузка страницы не выполняется

#### Scenario: PWA-003c Обновление существующего controller
- **WHEN** новый service worker заменяет ранее действовавший controller
- **THEN** страница перезагружается один раз

### Requirement: PWA-004 Offline-оболочка
После регистрации service worker и заполнения precache страница SHALL при
эмулированном offline-режиме сети отображать оболочку приложения при
перезагрузке; поведение проверяется через CDP-эмуляцию сети в живом браузере.

#### Scenario: PWA-004a Перезагрузка при offline
- **GIVEN** service worker зарегистрирован и precache оболочки заполнен
- **WHEN** сеть браузера переводится в offline и страница перезагружается
- **THEN** оболочка приложения отображается из precache

Non-goals: сценарий install UX (install prompt) — ручная проверка, ни один
стек его надёжно не автоматизирует; Lighthouse CI-аудит — отдельная задача
backlog.
