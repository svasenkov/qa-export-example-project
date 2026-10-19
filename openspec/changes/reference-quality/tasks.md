## Реализовано и проверено
- [x] Сохранить исходные изменения, baseline и AUTH-003 failure.
- [x] Согласовать совместимость и исправить полный пароль без удаления аккаунтов.
- [x] Выполнить unit/component и локальную PostgreSQL integration.
- [x] Включить полный src в Vitest с точным provider и LCOV.
- [x] Расширить JaCoCo с трёх классов на весь вспомогательный код.
- [x] Добавить проверку свежести/полноты и отрицательные контроли отчётов.
- [x] Подготовить изолированные H-01–H-11 и независимый контроль дефекта.
- [x] Разделить CI jobs и исключить readiness при blocked/skipped.
- [x] Достичь продуктовых 100% code coverage без исключения собственного кода:
  Go 365/365 statements, frontend 418/418 lines + 440/440 statements +
  107/107 functions + 246/246 branches (artifacts/reference-2026-10-19).
- [x] Достичь 100% измеряемого infrastructure кода: Java slice 1259/1259 lines,
  506/506 branches, 5759/5759 instructions, 271/271 methods по union exec
  (проверено в CI run 39252131337, 0 промахов в union XML); tools/quality и
  eval контроли покрыты собственными unittest-наборами.

- [x] Выполнить UI/E2E с закреплённым браузером и полной Compose упаковкой:
  холодный `docker compose up -d --build` из чистого worktree `qa-clean-1019`
  (37s build, health+gateway+37 API smoke зелёные, стенд снесён `down -v`);
  screenshot-tag зелёный на каноническом linux CI (pin 148.0.7778.178 в job
  `ui-mock-tests`), macOS-прогон зафиксирован справочно с diff-статистикой
  (10/12 окружение-расхождений, эталоны не переписаны).
- [x] Подключить разрешённый Sonar, выполнить анализ, проверить imported overall
  metrics и final gate: три projectKey — server Quality Gate OK на
  sonar.qa.guru, подтверждено в CI run 39252131337.
- [x] Выполнить workflow в GitHub: run 39252131337 (push 727cffe) — все 9
  джобов success, включая реальный sonar-gate; по пути исправлены дефекты
  PostgreSQL-ролей, Allure-гонки, pin-Chrome в infra/sonar и host-dependent
  JaCoCo-ветки. Часть гейта «branch protection» невыполнима на текущем
  тарифе: GitHub API вернул 403 — required checks для приватного репо
  требуют Pro/paid; готовый payload с 9 contexts и strict=true сохранён
  в artifacts/reference-2026-10-19-github-run/ для владельца.

- [x] Согласовать полный product scope header/navigation/layout/catalog/PWA:
  решения владельца 19.10 применены в change `product-scope` (каталог read-only,
  поиск пассивный+backlog, Stage/Prod везде, dark/en/zds-* контракт,
  axe-гейт двух уровней, PWA offline-smoke, Lighthouse отложен); реализация
  покрыта — axe-браузер нашёл реальные дефекты, исправлены и перепроверены,
  goldens пересняты на linux, CI 9/9 зелёный.
- [x] Включить branch protection на main: применено 19.10 после смены
  видимости репо на public — 9 required contexts, strict=true,
  enforce_admins, linear history (branch-protection-applied.json).

## Не завершено — не архивировать как завершённое изменение
- [ ] Выполнить переносимость в новой генерации (manifest↔tests/config/runtime
  реализовано и проверено; шаблон новой генерации не прогонялся).
- [ ] Подключить разрешённый model runtime, выполнить независимые поведенческие
  прогоны H-01–H-11 и human review. Координатор, oracle и CodexSdkAdapter
  реализованы и проверены на стабе (eval/runner/, eval/test_runner.py 9/9);
  ждут CURSOR_API_KEY + авторизованный бюджет и выбор модели.
