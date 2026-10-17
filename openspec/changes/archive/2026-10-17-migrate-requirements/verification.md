# Проверка миграции — 2026-10-17

Проверялось рабочее дерево поверх `e296151f9d22757c6dc22275a6ecfbc220c16338`,
с уже существовавшими пользовательскими изменениями. Новый commit не создавался.
Хеши затронутых файлов сохранены в `file-hashes.json` рядом с этим документом.
Источник `login-flow` v0.9 до переноса имел SHA-256
`dd80b58b6f6a3ae10741e5ca99e104d76192cd56e5f19c8211d87e76b1152be3`.

## Локальный инструмент

Node.js v24.18.0, npm 11.16.0, OpenSpec 1.14.3. Установлены 57 пакетов.
Первая попытка установки в ограниченной среде завершилась ENOTFOUND для npm registry;
повторная разрешённая загрузка завершилась успешно. Воспроизводимость lockfile
проверена переустановкой из локального кеша без сети:

```bash
npm ci --prefix tools/openspec --ignore-scripts --offline --no-audit --no-fund --cache /private/tmp/qa-openspec-npm-cache
npm run openspec --prefix tools/openspec -- --version
npm run openspec --prefix tools/openspec -- instructions proposal --change migrate-requirements --json
npm run openspec --prefix tools/openspec -- status --change migrate-requirements --json
npm run validate --prefix tools/openspec
```

Установка и перечисленные проверки завершились с exit code 0. Инструкции содержат
project context и rules из `openspec/config.yaml`. До архивации строгая валидация:
4 спецификации и 1 технический change, 5 passed / 0 failed. `skip_specs: true`
обработан как явное отсутствие изменения поведения; фиктивных дельт нет.

## OpenAPI backend

Применена процедура `skills/qa-run-tests/SKILL.md`. Из backend-модуля:

```bash
env -u TEST_DATABASE_URL GOCACHE=/private/tmp/qa-openspec-go-cache go test -count=1 -v ./internal/api -run '^TestOpenAPI'
```

Go go1.27.0 darwin/arm64. Внешний стенд и БД не требуются. Exit code 0,
выполнены 2 теста, 0 падений, 0 пропусков; `-count=1` исключает повторное
использование результата тестов из кеша:

```text
--- PASS: TestOpenAPISpecMatchesContractCopy (0.00s)
--- PASS: TestOpenAPIDocsServesSwaggerUI (0.00s)
PASS
ok  dev.multistack/backend-go-stdlib/internal/api  0.872s
```

Это проверка равенства YAML-копий, выдачи OpenAPI и Swagger UI. Она не доказывает
соответствие всех HTTP-обработчиков каждому требованию.

## Сохранность данных и ссылок

Одноразовые проверки относительно сохранённого состояния до миграции подтвердили:

- Все 18 точных значений сообщений en/ru совпадают с источником.
- YAML OpenAPI семантически совпадает с исходным, кроме двух описательных полей;
  embedded-копия совпадает побайтно с новым каноническим файлом.
- Исходные задания и настройки CI сохранены; добавлен только `openspec-check`.
- В существующем OpenAPI-тесте изменены только путь эталона и диагностическое сообщение.
- В существующих продуктовых файлах изменились только три README, описательные
  ссылки OpenAPI, комментарий и путь в тесте. Продуктовая логика не изменена.
- Проверены 137 локальных Markdown-ссылок в 26 документах на момент проверки.
- 19 ID требований и 46 ID сценариев уникальны; карта всех 16 исходных acceptance-пунктов
  сохранена в `migration-map.md`. Это не измерение тестового покрытия.
- `git diff --check` не выявил ошибок whitespace; действующих ссылок на старый
  каталог контрактов не осталось.

Одноразовые результаты сравнения сохранены в каталоге проверки
`/var/folders/m8/p2rt38nn7l79mn7dzx9vyxf80000gn/T/openspec-migration-i7g4vfav/`:
`migration-checks.json`, `schema-checks.txt`; там же — исходные документы.
Этот локальный временный каталог не является распространяемой частью проекта.

## Границы проверки

Продуктовые API/UI/E2E, PostgreSQL integration, визуальные и ручные проверки
не запускались. CI настроен, но прогон на GitHub не выполнялся. Запускаемых
агентов, eval-runner и модельных вызовов нет; интеграции slash-команд не установлены.
Известные пробелы доказательств сохранены в `docs/qa/auth-checks.md`.

## Архивация

После завершения всех задач выполнено
`npm run openspec --prefix tools/openspec -- archive migrate-requirements --skip-specs --yes`.
CLI подтвердил `Task status: Complete` и переместил change в
`openspec/changes/archive/2026-10-17-migrate-requirements/`. Основные спецификации
при этой технической архивации не обновлялись; их хеши проверяются по manifest.

После архивации: `validate --all --strict --no-interactive` — 4 passed / 0 failed;
`validate --archived --strict --no-interactive` — 1 passed / 0 failed;
`list --json` — пустой список активных changes. Все команды через локальную обёртку,
exit code 0. Финальная проверка разрешила все 138 локальных ссылок в 27 документах;
`git diff --check` прошёл. Основные спеки совпали с хешами до архивации.
