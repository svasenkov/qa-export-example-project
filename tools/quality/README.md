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
Файлы тестов/config пока не связаны с manifest: этот дополнительный контроль
нужен до сертификации переносимых артефактов; в CI используются отчёты того же job.

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

Существующие настройки Sonar вызываются для Go/Vue через:

```bash
sh tools/quality/sonar.sh frontend frontend/typescript/frontend-typescript-vue artifacts/run-001/frontend.json artifacts/run-001/coverage/coverage-summary.json
```

Без явно разрешённых совпадающих SONAR_HOST_URL/SONAR_APPROVED_URL команда
завершается BLOCKED до обращения к серверу. Требуется установленный закреплённый
CLI, его версию скрипт выводит перед анализом. Путь импортируемого coverage передаётся
из проверенного артефакта, а не берётся от старого файла по умолчанию.
