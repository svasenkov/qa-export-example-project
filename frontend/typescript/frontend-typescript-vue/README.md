# frontend-typescript-vue

Product UI — TypeScript + Vue 3 (same screens as `frontend-typescript-react` / vanilla).

Vite + Vue 3 + Vue Router (`createWebHistory` under `/frontend-typescript-vue/`).
Lean design-system CSS from
[`vendor/ds`](vendor/ds/);
thin Vue wrappers for Panel / Button / PlaqueField / AppHeader (header markup stays SSOT in `js/header.js`).

Vitest + Testing Library live in [`src/test/`](src/test/) — same module as the product.

Prod URL: `https://autotests.ai/stack/{backend}/frontend-typescript-vue/`  
(Host `/` is empty.)

## vue-router 5

The v5 major changed nothing in `src/router/index.ts` — this is still the recommended
shape, and `createMemoryHistory` is still what the specs mount against:

```ts
export const router = createRouter({
  history: createWebHistory(`${APP_BASE}/`),
  routes: [ /* … */ ],
});
```

v5 does ship a new matcher and an `experimental_createRouter`, but both are exported under
`EXPERIMENTAL_*` names — not something a teaching stand should pin its routing to.

TypeScript here is pinned to **6.0.3**, not the 7.0.2 the rest of the matrix uses: `vue-tsc`
resolves `typescript/lib/tsc`, and TypeScript 7 dropped that path from its `exports` map, so
`npm run typecheck` dies with `ERR_PACKAGE_PATH_NOT_EXPORTED` before it reads a single file.
Move to 7 once `vue-tsc` supports it.

## Routes

| Route | Screen | Key testids |
|-------|--------|-------------|
| `/` | `HomePage` | `multistack-layout`, `health-panel`/`health-status`, `items-list`/`item-row`, `welcome-panel`/`welcome-message`, `logout-button`, `delete-account-button` |
| `/login` | `LoginPage` | `login-panel`, `login-form`, `login-input`, `password-input`, `submit-button`, `error-message`, `register-link`, `login-form-title` |
| `/register` | `RegisterPage` | `register-panel`, `register-form`, `register-login-input`, `register-password-input`, `confirm-password-input`, `register-submit-button`, `register-error-message`, `login-link`, `register-form-title` |

(Router history base strips the mount; header/`appPath` use absolute `/frontend-typescript-vue/…`.)

## Session panel

Visible only once `GET /api/auth/me` returned a profile. Two actions, both ending in the
same logged-out state at `/login`:

| Button | Request | Meaning |
|--------|---------|---------|
| `logout-button` (`btn--primary`) | `POST /api/auth/logout` | Ends this session. The JWT is **not** invalidated server-side — logout is stateless by design. |
| `delete-account-button` (`btn--danger`) | `DELETE /api/auth/me` | **Deletes the account.** The user row is gone and the same token now yields 401. |

Delete account asks `window.confirm('Delete this account? This cannot be undone.')` first;
cancel sends no request. Both calls are best effort and both drop the local token even when
the API fails — a token the server has already rejected must never keep the UI signed in.

## Спецификации и UI-соглашения

Поведение — в [OpenSpec](../../../openspec/README.md). Атрибуты форм и сведения
о подключении дизайн-системы — в [auth-ui.md](docs/auth-ui.md).

## Contracts preserved for Selenide

- Every `data-testid` used by `tests/.../pages/*.java`.
- Exact strings: validation messages (`lib/messages.ts`), `Welcome, {username}!`,
  `→ {status} | service: {service}`, form titles `Login Form` / `Register`.

## Header

The design-system header is SSOT and is **not** reimplemented in Vue. `<AppHeader>`
publishes `window.headerConfig` and injects `js/header.js` from the mount
(`vendor/ds` overlay in this module's nginx image).

## Scripts

```bash
npm run dev        # Vite on :9813 — conflicts with compose publish of the same port
npm run build      # → dist/ (packed by this module's Dockerfile)
npm run typecheck  # vue-tsc --noEmit
npm test           # Vitest + Testing Library (src/test/)
```

`npm run dev` serves `vendor/ds` (`js/header.js` + templates) so the header mounts without Docker. Compose/catalog nginx still overlays the same files in the image.

`npm test` runs Vitest under `--no-experimental-webstorage` for the same reason as the React
module: on Node 26 the runtime's own empty `localStorage` global wins over the jsdom one.

## Build notes

- `outDir` is module-local `dist/` with `emptyOutDir: true`.
- Asset filenames are stable (unhashed).
- Peer CSS: lean DS from `vendor/ds/css` + product CSS in `css/`.
- Image build context is this folder (`docker build .`); no repo-root `COPY` of `_shared`.

## PWA baseline

| Output | Role |
|--------|------|
| `manifest.webmanifest` | `scope`/`start_url` under mount |
| `sw.js` | Precache app shell; `/api/*` denylisted |
| `public/icons/pwa-*.png` | Install + apple-touch (`icons/pwa-192.png`) |

SW registered in `src/pwa/registerServiceWorker.ts` under the Vite base path.

## Allure

Vitest пишет результаты в `allure-results/`. HTML-отчёт использует общий
[tools/allure](../../../tools/allure/README.md) через локальный
[allurerc.mjs](allurerc.mjs). Отдельная копия конфигурации и CLI не нужна.

## Полная область coverage

`@vitest/coverage-v8` закреплён точно на версии установленного Vitest 4.1.10.
`npm ci` использует lockfile. `npm test -- --coverage` измеряет все `src/**/*.{ts,js,vue}`,
включая неимпортированные файлы, и собственный `vendor/ds/js/app-base.js`.
Исключены только тесты и декларации `.d.ts` в src.
Пороги lines/statements/functions/branches = 100; текущий неполный результат падает.
`COVERAGE_DIR` позволяет сохранять каждую попытку отдельно. Нельзя удалять прошлые
отчёты; используйте новый каталог. Vendor DS и inline boot в index.html требуют
отдельной проверки состава: coverage src не объявляется покрытием всей поставки.
