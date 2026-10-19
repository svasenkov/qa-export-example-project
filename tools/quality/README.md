# Проверка доказательств coverage

Небольшая обёртка Python stdlib поверх штатных Go/Vitest/JaCoCo. Она не запускает
модель, не подменяет assertions, не обращается к Sonar и не является платформой QA.

Из корня, с новым каталогом результатов:

```bash
mkdir -p artifacts/run-001
python3 tools/quality/coverage.py begin frontend frontend/typescript/frontend-typescript-vue artifacts/run-001/frontend.json
COVERAGE_DIR=../../../artifacts/run-001/coverage npm test --prefix frontend/typescript/frontend-typescript-vue -- --coverage
python3 tools/quality/coverage.py check frontend frontend/typescript/frontend-typescript-vue artifacts/run-001/frontend.json --report artifacts/run-001/coverage/coverage-summary.json
python3 -m unittest discover -s tools/quality -v
```

Аналогично `go` с Go-модулем и native `coverage.out`; `jacoco` с Java-модулем и
`build/reports/jacoco/test/jacocoTestReport.xml`. `begin` до команды тестов,
`check` после неё. Shell/CI должен сохранять и учитывать exit code команды тестов:
проверка coverage сама не доказывает прохождение тестов. Начальная попытка не
перезаписывается. Проверяется SHA256 состава измеряемых исходников, freshness,
структура отчёта, наличие всех файлов, непустые счётчики и 100% каждого вида.
LCOV frontend сверяется с JSON summary; repeated Go blocks объединяются по местоположению.
Непопадание type-only файла в provider report требует явного разбора, не auto-pass.
Manifest связывает результат также с тестовыми исходниками, конфигурацией/lockfiles
и фактом выставленных env-флагов (`TEST_DATABASE_URL` для go): изменение любого
из них между `begin` и `check` отклоняет доказательство. Запускайте `begin`
под тем же окружением, что и команду тестов; существенные параметры прогона
записывайте через `--runtime KEY=VALUE`. Для `jacoco` проверяются также exec-файлы:
`build/jacoco/test.exec` и каждый `build/jacoco/union/*.exec` должны быть созданы
после `begin` — старый union-exec не подмешивается незаметно.

Области следуют модулям профиля. В другой генерации пути передаются из её профиля,
не копируются из этой сборки; неподдерживаемый kind завершается ошибкой.
Go storetest исключён из продуктовой суммы и учитывается отдельно как infrastructure.
Список всего собственного кода и неизмеренных частей — в
[reference-quality](../../docs/qa/reference-quality.md).

Sonar: запускать только после успешных тестов и coverage check, на явно разрешённом
сервере; токен только через окружение. Использовать существующие properties/Gradle,
`sonar.qualitygate.wait=true`, сохранять `report-task.txt`, analysis ID и финальный
server status. Проверить, что серверный gate содержит **overall** coverage=100,
а не только new code. До получения этого результата Sonar = blocked/not_run.
Сохранённый URL и наличие токена сами по себе не разрешают отправку исходников.

## Подключённый сервер: sonar.qa.guru

Авторизованный сервер проекта — `https://sonar.qa.guru` (SonarQube Community Build).
Три projectKey уже provisioned и совпадают с `sonar.projectKey` в модулях
(`autotests-ai-multistack-app-{backend-go-stdlib,frontend-typescript-vue,
tests-java-junit5-rest_assured-selenide}`).

**Токен — рекомендуемое хранилище.** Учащийся создаёт токен на
https://sonar.qa.guru → My Account → Security → Generate Token и сохраняет его
в файл `~/.config/sonar/sonar.qa.guru.token` с правами `chmod 600`. Файл живёт
вне репозитория и переживает чистые checkout. Затем:

```bash
. tools/quality/sonar-env.sh   # экспортирует SONAR_* из token-файла, значение не печатается
```

`sonar-env.sh` задаёт `SONAR_HOST_URL`, `SONAR_APPROVED_URL` и `SONAR_TOKEN`
из файла `${SONAR_TOKEN_FILE:-~/.config/sonar/sonar.qa.guru.token}`.
В репозиторий токен не кладётся: ни в `.env`, ни в конфиги — `.env*` gitignored,
но user-level файл надёжнее и не может быть случайно закоммичен.

В CI используется **отдельный токен** типа Global Analysis Token — он умеет
только загружать анализы и читать Quality Gate, без доступа к аккаунту.
Создаётся через API от локального токена, значение не печатается:

```bash
curl -fsS -u "$(cat ~/.config/sonar/sonar.qa.guru.token):" -X POST \
  "$SONAR_HOST_URL/api/user_tokens/generate" \
  --data-urlencode "name=github-actions-<repo>" \
  --data-urlencode "type=GLOBAL_ANALYSIS_TOKEN"
# поле "token" из ответа → gh secret set SONAR_TOKEN
```

Секрет обновляется `gh secret set SONAR_TOKEN`; локальный токен при этом не
затрагивается и отзывается независимо.

Существующие настройки Sonar вызываются для Go/Vue через:

```bash
. tools/quality/sonar-env.sh
sh tools/quality/sonar.sh frontend frontend/typescript/frontend-typescript-vue artifacts/run-001/frontend.json artifacts/run-001/coverage/coverage-summary.json
```

Без явно разрешённых совпадающих SONAR_HOST_URL/SONAR_APPROVED_URL команда
завершается BLOCKED до обращения к серверу. Требуется установленный закреплённый
CLI (`sonar-scanner` 8.1.0.6389 в `~/.local/share/sonar-scanner/`, в CI job
скачивается из binaries.sonarsource.com), его версию скрипт выводит перед анализом.
Путь импортируемого coverage передаётся из проверенного артефакта, а не берётся
от старого файла по умолчанию.

Java-модуль анализируется штатным Gradle-плагином после union-сборки покрытия:

```bash
. tools/quality/sonar-env.sh
(cd tests/java/tests-java-junit5-rest_assured-selenide && bash scripts/jacoco-union.sh)
(cd tests/java/tests-java-junit5-rest_assured-selenide && ./gradlew sonar)
```

`sonar` требует наличия свежего `jacocoTestReport.xml` (guard в `build.gradle`)
и совпадения SONAR_APPROVED_URL с SONAR_HOST_URL; сами тесты он не перезапускает.
`sonar.qualitygate.wait=true` останавливает задачу на серверном Quality Gate:
FAILED = ненулевой exit code.
