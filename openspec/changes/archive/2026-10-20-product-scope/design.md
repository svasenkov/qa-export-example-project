## Источники наблюдаемого поведения

Дельты формализуют то, что уже реализовано и частично проверено. Формулировки —
предложение агента; утверждение — ревью change.

- Шапка и навигация: `src/components/AppHeader.vue`, `src/lib/headerConfig.ts`,
  `vendor/ds/js/header.js` и `env-hosts.js` (frontend-модуль). Проверки:
  `HeaderTests`, `HeaderActiveNavTests`, `BurgerMenuTests` (Java UI),
  `headerConfig.test.ts`, `header-active-nav.test.ts` (component).
- Раскладка и каталог: `src/pages/HomePage.vue`, `src/lib/api.ts`. Проверки:
  `HomeLayoutTests`, `HomeErrorStateTests` (UI, mock-сценарии WireMock),
  `HomePage.test.ts` (component), `HealthItemsApiTests`, `SeedDataApiTests`,
  `BackendWiringApiTests` (api), `HomeTests` (e2e).
- Панель состояния: `src/pages/HomePage.vue` (health), тот же набор проверок.
- PWA: `vite.config.ts` (VitePWA/manifest/workbox), `src/pwa/*`. Проверки:
  `pwa-register.test.ts`, `registerServiceWorker.test.ts` (component);
  отдача `manifest.webmanifest`, `sw.js`, иконок через gateway подтверждена
  живым запросом к изолированному стенду (HTTP 200).

## Способ проверки после согласования

Уровни — по `docs/coverage-profile.md`: состояния списка и хром — component
(Vitest); отдача PWA-артефактов и HTTP-контракты — api; навигация, активный
пункт, меню, язык/тема и раскладка в браузере — ui на mock-стенде;
критический путь «главная грузит health+items» — e2e на живом compose.

## Решения по открытым вопросам

Утверждены владельцем 2026-10-19 (см. `proposal.md` → «Решения»): каталог —
read-only демо-список; поиск шапки — пассивный элемент + backlog-задача;
Stage/Prod видны везде; дефолты `dark`/`en`/`zds-*` — контракт; a11y —
автоматизированный аудит двух уровней (vitest-axe + axe-selenium-java);
PWA — артефакты + unit-регистрация + offline-smoke по CDP; Lighthouse CI и
install UX отложены.

Новая реализация в рамках change: `vitest-axe` component-проверки,
`axe-selenium-java` UI-прогон на mock-стенде (Home/Login/Register),
offline-smoke тест через Selenium DevTools (`Network.emulateNetworkConditions`).
Backlog (non-goals): поведение поиска шапки, Lighthouse CI, браузерный
install/offline-UX сценарий, ручной WCAG-аудит.
