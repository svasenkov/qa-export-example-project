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
- [x] Достичь 100% измеряемого infrastructure кода: Java slice 1237/1237 lines,
  502/502 branches, 5639/5639 instructions, 269/269 methods по union exec;
  tools/quality и eval контроли покрыты собственными unittest-наборами.

## Не завершено — не архивировать как завершённое изменение
- [ ] Согласовать полный product scope header/navigation/layout/catalog/PWA.
- [ ] Связать manifests также с tests/config/runtime и выполнить переносимость в новой генерации.
- [ ] Выполнить UI/E2E с закреплённым браузером и полной Compose упаковкой:
  живые прогоны на локальном compose зелёные, но screenshot-tag на macOS
  исключён (Chrome 150 vs pin 148), сборка с нуля и GitHub runner не проверены.
- [ ] Подключить разрешённый Sonar, выполнить анализ, проверить imported overall metrics и final gate.
- [ ] Подключить разрешённый model runtime, выполнить независимые поведенческие прогоны и human review.
- [ ] Выполнить workflow в GitHub и проверить весь итоговый ready gate.
