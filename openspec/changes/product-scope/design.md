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

## Открытые вопросы

Список — в `proposal.md`. Спорное поведение в дельты не внесено: пассивное поле
поиска в шапке, видимость Stage/Prod-переключателей на учебном стенде, объём
каталога сверх read-only, границы a11y-аудита, необходимость браузерного
PWA-сценария.
