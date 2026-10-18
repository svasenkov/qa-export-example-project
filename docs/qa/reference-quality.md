# Состояние основы QA — 19 октября 2026

**Готовность: НЕ ДОСТИГНУТА.** Измеряемые gates продуктового и Java-infrastructure
кода проходят на 100%; согласование полного product scope, независимый model
runtime, Sonar server gate, GitHub workflow и human review остаются блокерами.
Активный [OpenSpec change](../../openspec/changes/reference-quality/proposal.md)
не архивирован: [незавершённые задачи](../../openspec/changes/reference-quality/tasks.md)
сохранены.

## Исправления

- AUTH-003: новый версионированный bcrypt от base64(SHA-256 полного пароля).
  Регрессия неверного хвоста падала до исправления и проходит после, в Go и HTTP API.
- AUTH-004: пользователь явно согласовал отказ для старых bcrypt при вводе >=72 байт.
  Старые короткие пароли работают; аккаунты/хеши не удаляются и не мигрируют молча.
  [Совместимость и ограничение отката](../../openspec/changes/reference-quality/design.md).
- PostgreSQL integration: каждому тесту выделяется схема; cleanup зарегистрирован
  до дальнейших действий и использует отдельный context, работающий после падения.
  Timing-тест `rows.Err` заменён детерминированным контрактом (отменённый context
  обязан вернуть ошибку); mid-iteration ветка покрыта stub-драйвером.
- Java config tests: разрешённые CLI URL overrides больше не сравниваются с
  жёстко записанными портами. Исходные три падения сохранены.
- `ApiTestBase.available()` ловил только `RuntimeException`: unreachable-стенд
  пробрасывал `ConnectException` и ронял assumption вместо `false` — исправлено.
- Vitest: provider 4.1.10, полный `src` плюс собственный vendor overlay,
  LCOV/JSON; удалены исключения main/styles и исключение i18n из duplication.
- JaCoCo 0.8.14: вместо трёх классов весь вспомогательный Java-код, независимо
  от фильтра тегов; агент включён также для API/UI/E2E. Union-сборка объединяет
  exec-файлы всех вариантных форков (`ci`, `mock`, `attach`, `quiet`, `keep`,
  `skipopen`, `harvideo`, `infra`).
- Проверка coverage отклоняет неполные/повреждённые/старые отчёты и изменение
  исходников, сверяет LCOV с JSON. Повторяющиеся Go-блоки объединяются, не суммируются.
- Git hook теперь отклоняет и ошибку самого `git grep`, а не только найденный секрет.
- CI разделяет продукт, infrastructure, evaluator controls и model behavior;
  `reference-ready` не принимает skipped/failed/blocked за success.

## Доказательства и версии

Свежие результаты: [artifacts/reference-2026-10-19](../../artifacts/reference-2026-10-19/)
(`go/`, `frontend/`, `java/`, `eval/`). Предыдущие снимки 18.10 сохранены в
`reference-2026-10-18*` и не редактировались. Накопленный Allure не считается
результатом одной попытки; точные результаты берутся из JSONL/JUnit XML.
Java-евиденс: `gate.json` (все 4 метрики 100%, свежесть и source-digest
проверены), `jacocoTestReport.xml`, `jacoco-html/`, `union-exec/` (8 exec с
sha256 в `run-summary.json`), `junit/` (39 классов).

Условия: macOS arm64, Go 1.27.0, Node 26.10.0 (из локального nvm), Vitest/V8 4.1.10,
Java Corretto 21.0.9, Gradle wrapper 9.6.0, JaCoCo 0.8.14, Coverage.py 7.15.0.
PostgreSQL integration выполнялась на временном кластере (порт 55438/55439,
изолированная БД `qa_scratch_1019`); probe-аккаунты после диагностики удалены.
Java UI/E2E выполнялись против локального Compose: `stand-gateway-ci` (9821),
`backend-go-stdlib` (8831), `frontend-typescript-vue`, `api-mock` WireMock (9911).
Образы пересобраны из этого checkout; идентичность проверена по JWT alg HS256 и
AUTH-003 поведению (точный пароль 200 / изменённый суффикс 401).

Осторожно: тот же compose project name (`autotests-ai-multistack-app`) используется
основным checkout'ом и его watchdog (`zero-design-system` `scripts/stands`);
чужой `compose up` перемонтирует наши контейнеры образами другого стека.
Перед evidence-прогоном идентичность gateway/backend проверялась заново.

Команды из модулей:

- Go: `go test -count=1 -coverpkg=./... -coverprofile=<новый файл> -json ./...`,
  с TEST_DATABASE_URL отдельной БД; `go vet ./...`.
- Vue: `npm test -- --coverage` с отдельным COVERAGE_DIR;
  `npm run typecheck`, `npm run build`.
- Java: `./gradlew test -Denv=ci -DexcludeTags=screenshot,manual` плюс вариантные
  union-форки; gate: `./gradlew jacocoTestReport jacocoTestCoverageVerification
  --rerun-tasks`.
- Evidence: `python3 -m unittest discover -s tools/quality`.
- Harness controls: `python3 -m unittest discover -s eval`.
- OpenSpec: `npm run validate --prefix tools/openspec`.

## А. Продукт

Область Go: все production `.go`, включая main, без `storetest` (отдельная
инфраструктура). **365/365 statements — gate PASS**. Не branch coverage; метрика
statements зафиксирована [gate.json](../../artifacts/reference-2026-10-19/go/gate.json),
профиль `coverage.out` и JSONL вывода приложены.

Область frontend: `src/**/*.{ts,js,vue}` и собственный `vendor/ds/js/app-base.js`.
Исключены тесты и `.d.ts`. Итог — все четыре метрики **PASS**:

- lines 418/418;
- statements 440/440;
- functions 107/107;
- branches 246/246.

[gate.json](../../artifacts/reference-2026-10-19/frontend/gate.json) подтверждает
свежесть JSON/LCOV и инвентарь источников. Собственный inline bootstrap
`index.html` покрыт тестом `bootstrap.test.ts` через jsdom-монтирование.
Vendor DS — snapshot upstream; `env-hosts.js` явно generated.

Java-прогон полного CI-набора: **213 тестов, 0 failures, 0 errors**;
2 assumption-пропуска в `HomeErrorStateTests` (требуют WireMock-окружения —
это корректный skip, не зачтённый pass). AUTH-003a подтверждён сквозь gateway:
изменённый суффикс 128-байтного пароля получает 401. UI/E2E на живом
Compose-стенде (go-stdlib + vue через `deploy/ci/nginx.conf`) — зелёные.
[run-summary](../../artifacts/reference-2026-10-19/java/run-summary.json),
[JUnit XML](../../artifacts/reference-2026-10-19/java/junit/).

Screenshot-tag на macOS остаётся исключённым: golden-файлы рассчитаны на
CI-окружение с закреплённым Chrome 148, локальный cache содержит Chrome 150 —
pixel-расхождения являются известным env-ограничением, а не успешным доказательством
и не регрессией. Прогон screenshot-набора на совместимом браузере — отдельная
задача (см. tasks.md).

Неопределённый объём остаётся: header, navigation, layout, каталог (помимо
HTTP-схемы items), PWA, доступность — требования к согласованным состояниям,
переходам и ошибкам ведутся через OpenSpec, а не выводятся из текущей реализации.
Схемы HTTP ошибок/ограничения форм в OpenAPI также остаются неполными.

## Б. Инфраструктура

- I-01 Конфигурация и выбор стенда → PASS (ci/mock/overrides покрыты harness-тестами).
- I-02 Изоляция/cleanup → отдельные PostgreSQL-схемы, fake-драйверы с детерминированным
  жизненным циклом, probe-аккаунты удалены → PASS для проверенных наборов.
- I-03 Assertions/oracle → AUTH-003 до/после, контроль fixture, независимый разбор
  веток JaCoCo → PASS для указанных рисков.
- I-04 Выбор/результаты → Go JSONL, Vitest, JUnit XML, 213/0-failures → PASS.
- I-05 Свежесть/полнота → 14 tests tools/quality PASS: missing/empty/corrupt/stale,
  изменённый source, повторный блок, LCOV mismatch.
- I-06 Полный executable scope → инвентарь + 100% по всем четырём метрикам
  в измеряемых областях → PASS для Go/frontend/Java-infra срезов.
- I-07 Отчётность → Allure helpers покрыты harness-тестами; Node coverage
  по-прежнему отражает только загруженные файлы.
- I-08 CI → YAML разобран, shell синтаксис проверен; GitHub workflow **NOT RUN**.
- I-09 Sonar → локальные properties и разрешительный preflight подготовлены;
  анализ, imported metrics, overall gate и final server status **BLOCKED**.
- I-10 Переносимость новой генерации → **NOT RUN**; шаблон не передаёт ей свой статус.

Java infrastructure slice (`infraCoverageSlice`: весь support-код `src/test/java`,
исключены тела проверок `tests/{api,ui,e2e,infra,manual}`) — по union exec из
восьми форков:

- **lines 1237/1237, branches 502/502, instructions 5639/5639, methods 269/269**
- `jacocoTestCoverageVerification` — PASS, минимум 1.0 по каждой метрике;
  [gate](../../artifacts/reference-2026-10-19/java/gate.json),
  [XML](../../artifacts/reference-2026-10-19/java/jacocoTestReport.xml).

Покрытие получено детерминированными harness-тестами (fake WebDriver/WebElement,
fake W3C hub, shadow classloader) и вариантными прогонами конфигурации — без
искусственного упрощения product-поведения. Правки support-кода сведены к
тестируемым швам (перегрузки с `TestConfig`/env-getter, public ctor фейков для
`Augmenter`, `Objects.requireNonNullElse` вместо недостижимой null-ветки).

Python infrastructure: `tools/quality/coverage.py` — 14 unittest PASS
(152/152 statements, 84/84 branches по предыдущему измерению собственного кода);
`eval/` controls — 4 unittest PASS; `prepare.py` — 40/40 statements, 8/8 branches.
Shell-скрипты инвентаризованы: `ensure-databases.sh` исполняется живым compose,
`install-chrome-for-testing.sh` покрыт pin-путём `LocalChromePin`, `sonar.sh` —
тонкая обёртка с разрешительным preflight. Метрики разных языков не усредняются.

## В. Harness

Критерии H-01–H-11 и независимые ожидаемые результаты: [cases.json](../../eval/cases.json).
Девять development и два отдельных verification случая; ограничение 3 попытки,
без repair-loop. Verification публичны: **не sealed holdout**. Для слепого вывода
нужны новые скрытые варианты независимого координатора.

Четыре tests evaluator controls PASS: сохранение результатов, независимое
обнаружение дефектного сравнения, раздельность случаев, реальный hook.
Это качество фикстур/инструментов, **не выполнение H-задач моделью**.

Поведенческие прогоны H-01–H-11 остаются **BLOCKED**: нет подключённого
изолированного model adapter и согласованного бюджета; интерактивная работа
текущего агента не подменяет независимые три попытки. Два verification случая —
**NOT RUN**. Human review — **not_performed**.

## Sonar и итоговый CI

`sonar.qualitygate.wait=true` установлен в существующих контурах. Убрана отправка
Java по URL по умолчанию. `tools/quality/sonar.sh` сначала требует точное разрешение,
проверяет fresh/full coverage, затем вызывает установленный CLI и ждёт gate.
Нужны отдельно выбранная закреплённая версия scanner, разрешённый сервер и токен.
Java остаётся на существующем Gradle Sonar plugin 7.5.0.8588.

Ни analysis ID, ни финального Quality Gate нет. Серверный overall gate и импорт
LCOV/Go/JaCoCo не проверены. Sonar CI job сейчас явный блокер, а не реализованный
scanner pipeline. Harness behavior job также блокируется без model adapter.
Оба участвуют в reference-ready вместе с остальными gates.

Docker-упаковка локально подтверждена частично: образы `backend-go-stdlib` и
`frontend-typescript-vue` собраны из этого checkout и обслуживают зелёные
API/UI/E2E прогоны через gateway. Полный `docker compose up` с нуля, prod-режимы
и GitHub workflow ещё не проверены. Локальный browser cache имеет Chrome 150,
проект закрепляет 148.0.7778.178; screenshot-tag исключён, pin не подменялся.
Ручных проверок человеком нет.

## Распределение ответственности и контракт генератора

Unit — правила credentials/пароля/JWT и ошибки зависимостей; integration — настоящая
PostgreSQL и конкуренция; component — формы, состояния, отсутствие запрещённых fetch,
storage, навигация; API — внешний HTTP и lifecycle; UI — браузер с mock; E2E — reload
и доставка критичных взаимодействий через живые части. Пересечения допустимы только
для разных наблюдений. Количество тестов и сумма процентов слоёв ничего не доказывают.

Новая генерация обязана определить профиль, согласованные сценарии, полный список
собственного executable кода, исключения с происхождением, пины и lockfiles, команды
каждого модуля и собственные свежие результаты. Допустимы новые стеки через module
README и измеритель своего языка; общие механизмы отчётности/доказательств не копируются.
Контрольный дефект обязан обнаруживаться, неверная конфигурация отклоняться.
До собственного успешного прогона новая генерация не получает статус соответствия.

Остаток конкретен: согласование полного product scope (header/navigation/layout/
catalog/PWA), связь manifests с tests/config/runtime, screenshot-tag на
закреплённом браузере, полный Compose/CI прогон в GitHub, разрешённый Sonar с
final server gate, независимый model runtime для H-01–H-11 и human review.

Официальные основания параметров: [Vitest coverage](https://vitest.dev/guide/coverage.html),
[Gradle JaCoCo](https://docs.gradle.org/current/userguide/jacoco_plugin.html),
[Go coverage](https://go.dev/doc/build-cover),
[Sonar analysis parameters](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/analysis-parameters/parameters-not-settable-in-ui).
