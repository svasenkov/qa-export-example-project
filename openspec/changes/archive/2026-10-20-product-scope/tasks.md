## Выполнено
- [x] Инвентаризация реализации: шапка/навигация/раскладка (`AppHeader.vue`,
  `headerConfig.ts`, `vendor/ds/js/header.js`), каталог и health
  (`HomePage.vue`, `src/lib/api.ts`), PWA (`vite.config.ts`, `src/pwa/`).
  Проверка: файлы перечислены в `design.md`.
- [x] Инвентаризация существующих проверок: component (Vitest), api, ui (mock),
  e2e, screenshot-tag; соответствие «поведение → проверка» в `design.md`.
- [x] Проект дельт `app-shell`, `catalog`, `service-status`, `pwa` по
  наблюдаемому поведению; `npm run validate --prefix tools/openspec` зелёный.
- [x] Фактическая отдача PWA-артефактов на изолированном стенде
  (`manifest.webmanifest`, `sw.js`, `icons/*` — HTTP 200, gateway 19821).

## Решения пользователя (2026-10-19)
- [x] Ответы на открытые вопросы `proposal.md` получены: каталог — read-only
  демо-список; поиск шапки — пассивный + backlog-задача; Stage/Prod видны
  везде; дефолты `dark`/`en`/`zds-*` — контракт; a11y — автоматизированный
  аудит двух уровней; PWA — базовый gate + offline-smoke, Lighthouse отложен.
- [x] Корректировка дельт по принятым решениям (SHELL-009 пассивный поиск,
  SHELL-010 axe-контракт, PWA-004 offline-оболочка); `validate` зелёный.

## Реализация по согласованному scope
- [x] Component-уровень a11y: axe-core в jsdom (`src/test/a11y.test.ts`,
  3 проверки Home/Login/Register; color-contrast исключён — нет layout,
  покрывается браузерным уровнем).
- [x] Браузерный уровень a11y: `axe-selenium-java` 4.10.1 (Deque) —
  `AccessibilityAxeTests` аудит `/`, `/login`, `/register` на mock-стенде.
  Первый прогон нашёл реальные нарушения (color-contrast, link-in-text-block,
  page-has-heading-one) — исправлены в продукте (app-css оверрайд токена,
  underline по умолчанию, h1 на страницах), повторный аудит чист.
- [x] PWA offline-smoke: `PwaOfflineSmokeTests` через CDP
  `Network.emulateNetworkConditions` — SW зарегистрирован → offline → reload
  → оболочка из precache; прогон на изолированном стенде зелёный.
- [x] Golden-скриншоты пересняты на linux-окружении (контейнер linux/amd64,
  pin-Chrome 148.0.7778.178) — 7 файлов под визуальные правки; compare-прогон
  в том же окружении зелёный 12/12.
- [x] Свежие доказательства coverage по `tools/quality` (begin → прогон → check)
  на изолированном стенде для затронутых доменов; CI-прогон зелёный.
  Frontend/Go/Java-гейты прошли в прогонах 39275727368, 39288685527
  (frontend coverage gate, Go coverage gate, JaCoCo union 1259/1259).
- [x] Пробел покрытия: живая отдача PWA-артефактов через gateway — `PwaAssetsApiTests`.
- [x] Пробел покрытия: пустой каталог на UI-уровне — `HomeEmptyItemsTests` +
  mock-сценарий `items-empty`.
- [x] Обновить связи требование → проверка в `docs/qa/` и reference-quality
  (раздел «Объём product scope» перечисляет capability → проверки).

## Backlog (non-goals, вне этого change)
- Поведение поиска в шапке (поле остаётся пассивным по SHELL-009).
- Lighthouse CI-аудит PWA installability.
- Браузерный сценарий install UX (ручная проверка по индустриальному консенсусу).
- Ручной WCAG-аудит (screen reader, порядок фокуса) — часть human review.
- Развитие каталога до карточки/поиска/сортировки/пагинации.

Архивация — после завершения реализации и свежих доказательств, отдельным
действием по `openspec/README.md`.
