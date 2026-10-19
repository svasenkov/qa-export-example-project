# Состояние основы QA — 19 октября 2026

**Готовность: НЕ ДОСТИГНУТА.** Измеряемые gates продуктового и Java-infrastructure
кода проходят на 100%; GitHub workflow выполнен и полностью зелёный дважды
подряд (последний — [run 39272807675](https://github.com/svasenkov/qa-export-example-project/actions/runs/39272807675)
на `d96ab72`, все 9 джобов включая `sonar-gate`). Product scope **согласован**
владельцем 19.10 — решения применены в `product-scope` (реализованные пункты
покрыты: axe-гейт двух уровней, offline-оболочка, переснятые linux-goldens);
branch protection **применён** (репо стал публичным, 9 required checks,
strict=true, linear history). Блокерами остаются: независимый model runtime
для H-01–H-11 и human review. Активные OpenSpec changes
[reference-quality](../../openspec/changes/reference-quality/proposal.md) и
[product-scope](../../openspec/changes/product-scope/proposal.md) не архивированы.

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
  от фильтра тегов; агент включён также для API/UI/E2E. Union-сборка
  воспроизводится одной документированной командой —
  [scripts/jacoco-union.sh](../../tests/java/tests-java-junit5-rest_assured-selenide/scripts/jacoco-union.sh):
  девять вариантных форков (`ci`, `mock`, `attach`, `quiet`, `keep`, `skipopen`,
  `harvideo`, `conlog-off`, `report-off`), каждый со своим exec и результатом,
  затем общий `jacocoTestCoverageVerification`.
- Проверка coverage отклоняет неполные/повреждённые/старые отчёты и изменение
  исходников, сверяет LCOV с JSON. Повторяющиеся Go-блоки объединяются, не суммируются.
- Git hook теперь отклоняет и ошибку самого `git grep`, а не только найденный секрет.
- CI — классический: `checks` (OpenSpec, compose-конфиг, тесты repo-инструментов
  и evaluator controls), модульные тесты, стендовые прогоны, `sonar-gate`.
  Гейт мержа — required checks на все джобы в branch protection.

## Доказательства и версии

Свежие результаты независимой проверки:
[artifacts/reference-2026-10-19-review](../../artifacts/reference-2026-10-19-review/)
(`go/`, `frontend/`, `java/`, `baseline/`): воспроизведение на отдельном
compose-проекте `qa-review-1019` с уникальными портами (gateway 19821, backend
18831, frontend 19813, mock 19911) и отдельным PostgreSQL-кластером (порт 55441).
Прогоны предыдущего исполнителя: [artifacts/reference-2026-10-19](../../artifacts/reference-2026-10-19/)
(`go/`, `frontend/`, `java/`, `eval/`). Снимки 18.10 сохранены в
`reference-2026-10-18*` и не редактировались. Накопленный Allure не считается
результатом одной попытки; точные результаты берутся из JSONL/JUnit XML.
Java-евиденс: `gate.json` (все 4 метрики 100%, свежесть и source-digest
проверены), `jacocoTestReport.xml`, `union/` (9 exec свежих вариантных
прогонов), `test-results-ci/` (39 классов, 213 тестов).

Условия: macOS arm64, Go 1.27.0, Node 26.10.0 (из локального nvm), Vitest/V8 4.1.10,
Java Corretto 21.0.9, Gradle wrapper 9.6.0, JaCoCo 0.8.14, Coverage.py 7.15.0.
PostgreSQL integration выполнялась на временном кластере (порт 55438/55439,
изолированная БД `qa_scratch_1019`) и повторно на кластере ревью
(порт 55441, `qa_review_scratch`); probe-аккаунты после диагностики удалены.
Java UI/E2E выполнялись против локального Compose: `stand-gateway-ci` (9821),
`backend-go-stdlib` (8831), `frontend-typescript-vue`, `api-mock` WireMock (9911)
— и повторно против изолированного проекта `qa-review-1019`: gateway 19821,
backend 18831, frontend 19813, mock 19911.
Образы пересобраны из этого checkout; идентичность проверена по JWT alg HS256 и
AUTH-003 поведению (точный пароль 200 / изменённый суффикс 401).

Осторожно: тот же compose project name (`autotests-ai-multistack-app`) используется
основным checkout'ом и его watchdog (`zero-design-system` `scripts/stands`);
чужой `compose up` перемонтирует контейнеры образами другого стека.
Поэтому свежие прогоны выполнены только на отдельном project/портах/образах;
общий стенд как доказательство не используется.

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

Screenshot-tag выполнен на обоих окружениях. Каноническое — linux CI
(`SCREENSHOT_OS=linux`, pin 148.0.7778.178 через `install-chrome-for-testing.sh`
в job `ui-mock-tests`): screenshot-набор **зелёный** в runs 39252131337,
39254310359 и 39272807675 — включая compare после пересъёмки goldens под
a11y-фиксы (7 файлов; пересняты в linux/amd64-контейнере с тем же pin-Chrome,
compare в том же окружении и на GHA зелёный 12/12 — осознанная регенерация
под изменение продукта, не «чтобы зелёным было»). Локальный прогон на
macOS arm64 зафиксирован справочно: 2/12 (missing goldens + diff шапки
5.44%/3.96%/6.56% при пороге 1.50%) — окружение, статистика в
[artifacts/reference-2026-10-19-screenshot](../../artifacts/reference-2026-10-19-screenshot/).
Системный Chrome в прогонах не участвовал.

Объём product scope **согласован владельцем** в OpenSpec change
[product-scope](../../openspec/changes/product-scope/proposal.md): app shell
(header/navigation/layout, пассивный поиск, Stage/Prod везде, дефолты
dark/en/zds-*, двухуровневый axe-гейт), каталог (read-only демо-список,
4 состояния), service status, PWA (артефакты + unit-регистрация +
offline-оболочка; Lighthouse CI и install UX — backlog). Change провалидирован
(`npm run validate --prefix tools/openspec` — 6/6). Реализация покрыта:
атрибуты поиска шапки, пустой каталог (`HomeEmptyItemsTests` + `items-empty`),
PWA-артефакты (`PwaAssetsApiTests`), axe-аудит браузерный
(`AccessibilityAxeTests`, Deque 4.10.1) и component (`a11y.test.ts`,
axe-core 4.14.0), offline-оболочка (`PwaOfflineSmokeTests`, CDP). Первый
браузерный аудит нашёл реальные нарушения (контраст muted-текста, ссылки
только цветом, отсутствие h1) — исправлены в продукте app-уровнем без правок
vendor DS; повторный аудит чист, goldens пересняты на linux. Схемы HTTP
ошибок/ограничения форм в OpenAPI остаются неполными — отдельный вопрос.

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
- I-08 CI → **RUN, зелёный**. GitHub Actions: серия прогонов 19.10
  (39244431007 → [39272807675](https://github.com/svasenkov/qa-export-example-project/actions/runs/39272807675)),
  три подряд полностью зелёных — все 9 джобов `success`. Evidence:
  [artifacts/reference-2026-10-19-github-run](../../artifacts/reference-2026-10-19-github-run/).
  По пути исправлены реальные дефекты: SCRAM-auth для тестовых PostgreSQL-ролей,
  гонка глобального Allure lifecycle (`@Isolated`), установка pin-Chrome в
  infra/sonar джобах, порядок шагов PATH для sonar-scanner и host-dependent
  ветки `ScreenshotHelper` (детерминированные швы).
  **Branch protection применён** 19.10: после смены видимости репо на public
  payload принят — 9 required contexts, strict=true, enforce_admins,
  required_linear_history, запрет force-push/deletions
  (`branch-protection-applied.json`).
- I-09 Sonar → `https://sonar.qa.guru` (авторизованный сервер): все три модуля
  проанализированы в CI, server Quality Gate **OK** для каждого projectKey
  (`sonar.qualitygate.wait=true` в sonar.sh и Gradle; `BUILD SUCCESSFUL` в
  каждом шаге анализа run 39252131337). Найденные дефекты исправлены до
  повторной выгрузки: SRI для swagger-ui CDN в openapi-docs.html,
  CPD-исключение i18n-словарей, три reliability-находки в Java support
  (regex DoS-паттерны и record с byte[]).
- I-10 Переносимость новой генерации → **NOT RUN**; шаблон не передаёт ей свой статус.

Java infrastructure slice (`infraCoverageSlice`: весь support-код `src/test/java`,
исключены тела проверок `tests/{api,ui,e2e,infra,manual}`) — по union exec из
девяти вариантных форков (см. `scripts/jacoco-union.sh`):

- **lines 1259/1259, branches 506/506, instructions 5759/5759, methods 271/271**
- `jacocoTestCoverageVerification` — PASS, минимум 1.0 по каждой метрике,
  подтверждено в CI (шаг `JaCoCo union coverage gate`, run 39252131337;
  [XML с раннера](../../artifacts/reference-2026-10-19-github-run/run-artifacts/reports-sonar-gate/tests/java/tests-java-junit5-rest_assured-selenide/build/reports/jacoco/test/jacocoTestReport.xml)
  — 0 промахов) и локально на изолированном стенде `qa-review-1019`
  ([gate](../../artifacts/reference-2026-10-19/java/gate.json),
  [XML](../../artifacts/reference-2026-10-19/java/jacocoTestReport.xml)).

Покрытие получено детерминированными harness-тестами (fake WebDriver/WebElement,
fake W3C hub, shadow classloader) и вариантными прогонами конфигурации — без
искусственного упрощения product-поведения. Правки support-кода сведены к
тестируемым швам (перегрузки с `TestConfig`/env-getter, public ctor фейков для
`Augmenter`, `Objects.requireNonNullElse` вместо недостижимой null-ветки).

Python infrastructure: `tools/quality/coverage.py` — 17 unittest PASS
(197/197 statements, 106/106 branches по свежему измерению собственного кода);
manifest теперь связывает результат не только с исходниками, но и с инвентарём
тестов, конфигурацией/lockfiles и параметрами runtime. `eval/` controls —
4 unittest PASS; `prepare.py` — 40/40 statements, 8/8 branches.
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
Закреплённый CLI — SonarScanner 8.1.0.6389 (`~/.local/share/sonar-scanner/`,
в CI скачивается из binaries.sonarsource.com). Java — Gradle Sonar plugin 7.5.0.8588.
Токен хранится в `~/.config/sonar/sonar.qa.guru.token` (chmod 600, вне репозитория);
окружение поднимается через `. tools/quality/sonar-env.sh`; в CI — GitHub secret
`SONAR_TOKEN`. Конвенция для учащегося задокументирована в
[tools/quality README](../../tools/quality/README.md) и `.env.diploma.example`.

Проведены реальные анализы на `https://sonar.qa.guru` (evidence:
`artifacts/reference-2026-10-19-review/sonar/` — report-task.txt, gate JSON,
и CI run 39252131337): все три projectKey имеют server Quality Gate **OK** при
overall coverage=100%. CI job `sonar-gate` — настоящий pipeline: скачивает
coverage-артефакты, устанавливает pin-Chrome, поднимает compose-стенд,
прогоняет union JaCoCo с gate (pass: 1259/1259 lines, 506/506 branches), затем
три анализа с ожиданием серверного gate — все зелёные. При падении публикует
jacocoTestReport.xml и run-JSON артефактом (проверено на прогоне 39248298393 —
именно так найдена host-dependent ветка `ScreenshotHelper.readImage`, закрытая
детерминированным harness-тестом в `727cffe`). Поведенческий gate агента отложен
до изолированного model runner и исключён из CI — статус фиксируется в
[eval/README.md](../../eval/README.md), а не красной заглушкой в workflow.

GitHub workflow: серия реальных прогонов 19.10 (39244431007 → 39272807675).
Первые ловили реальные дефекты (PostgreSQL SCRAM-роли, Allure-гонка,
отсутствие pin-Chrome в infra/sonar, порядок PATH, host-dependent
JaCoCo-ветки); три последних — полностью зелёные, включая прогон с новыми
a11y/offline-тестами и переснятыми goldens. Evidence по каждому сохранён в
`artifacts/reference-2026-10-19-github-run/`. Branch protection применён —
все 9 джобов required, strict=true (репо переведён в public по решению
владельца; до этого API возвращал 403).

Docker-упаковка подтверждена полностью: холодный `docker compose up -d --build`
из чистого detached-worktree `qa-clean-1019` (проект и порты свои, ~37s build):
postgres/backend/frontend/gateway поднялись, `/api/health` и gateway-ответы
зелёные, 37 API smoke-тестов прошли; стенд удалён (`down -v`, volumes и сеть
снесены). Проблем первого запуска не зафиксировано — `.env` не требуется
(дефолты compose), BuildKit-кэш не нужен для холодного старта. Проект закрепляет
148.0.7778.178: pin установлен и локально, и в CI jobs (infra, ui-mock,
sonar-gate); системный Chrome не использовался; screenshot-tag зелёный на
каноническом linux-окружении, macOS-расхождения задокументированы без
перезаписи эталонов. Ручных проверок человеком нет.

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

Остаток конкретен: human review (чеклист —
`docs/qa/human-review-checklist.md`), включая семантические вердикты кейсов
с `human_required`. Поведенческий eval выполнен на локальном runtime:
`eval/runner/` (координатор, oracle H-01–H-11, OllamaToolAdapter,
CodexSdkAdapter), 100% branch coverage самого runner'а (36 тестов).
Измерено на `qwen3-coder:30b` (Ollama localhost, temp=0, tool-loop,
3 попытки/кейс, артефакты в `artifacts/eval-dev-001/`,
`artifacts/eval-ver-001/`): development — pass 3/3: H-04, H-05, H-07,
H-08, H-09 (H-04/05/07/09 требуют семантической проверки человеком);
fail 0/3: H-01 (не выбрана точная команда `node --test`), H-02 (дефект
диагностирован, но не исправлен), H-03 (изменён subject или тест проходит
на дефектной фикстуре), H-06 (сводка статусов не выделена в артефакт);
verification — H-10 fail 0/3 (unicode-контроль не прошёл), H-11 pass 3/3.
Это измерение одной модели, а не доказательство обобщения; cases публичны.
Product scope согласован и
реализован по решениям владельца (a11y-гейт двух уровней нашёл и закрыл
реальные дефекты; offline-оболочка покрыта); branch protection применён
(9 checks, strict). CI пройден полностью трижды подряд (9/9, включая
реальный sonar-gate с тремя server Quality Gate OK); compose-холодный
старт и screenshot-набор проверены на каноническом linux.

Официальные основания параметров: [Vitest coverage](https://vitest.dev/guide/coverage.html),
[Gradle JaCoCo](https://docs.gradle.org/current/userguide/jacoco_plugin.html),
[Go coverage](https://go.dev/doc/build-cover),
[Sonar analysis parameters](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/analysis-parameters/parameters-not-settable-in-ui).
