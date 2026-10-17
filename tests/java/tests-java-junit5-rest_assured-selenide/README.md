# Автотесты API, UI и E2E

Этот модуль использует Java, Gradle, JUnit Jupiter, Rest Assured, Selenide и Allure. Имя каталога историческое; фактические версии зависимостей и Java toolchain задаёт [build.gradle](build.gradle). Версия Gradle — в [wrapper properties](gradle/wrapper/gradle-wrapper.properties).

Backend unit/integration и frontend component-тесты находятся в своих модулях из [профиля](../../../docs/coverage-profile.md). Их нельзя запускать или оценивать через этот Gradle-модуль.

## Стенды

Выбирайте `-Denv` явно. Адреса — в [config](src/test/resources/config), переопределения — по ключам [default.properties](src/test/resources/config/default.properties). Проверьте фактические значения перед запуском.

- `ci` — локальный gateway с живым backend; приложение запускается по [корневому README](../../../README.md).
- `mock` — локальный gateway с WireMock; для браузерных UI-проверок.
- `stage` и `prod` — внешние адреса из конфигурации. Наличие профиля не подтверждает доступность и не разрешает запуск. Для браузерного запуска требуется рабочий `remoteUrl`; секреты передавайте способом, который не раскрывает их в логах и переписке.

Для внешнего стенда заранее согласуйте набор тестов и допустимость данных/операций. Для prod используйте согласованный smoke-набор, не весь e2e.

## Команды

Все команды ниже выполняются из этого модуля. Браузерным проверкам нужен доступный браузер; настройка локального Chrome описана в [LocalChromePin.java](src/test/java/helpers/LocalChromePin.java), pin — в [chrome-for-testing.properties](src/test/resources/chrome-for-testing.properties).

```bash
# HTTP к локальному приложению
./gradlew test -Denv=ci -DincludeTags=api
# Интерфейс с подменённым API
./gradlew test -Denv=mock -DincludeTags=ui -DexcludeTags=screenshot
# Сквозные сценарии через живой backend
./gradlew test -Denv=ci -DincludeTags=e2e -DexcludeTags=screenshot
```

Выбор одного существующего теста и новое выполнение независимо от up-to-date:

```bash
./gradlew test --rerun \
  --tests 'tests.e2e.LoginTests.shouldShowErrorWhenPasswordIsWrong' \
  -Denv=ci -DincludeTags=e2e
```

Для проверки выбора без исполнения тела теста:

```bash
./gradlew test --rerun --test-dry-run \
  --tests 'tests.e2e.LoginTests.shouldShowErrorWhenPasswordIsWrong' \
  -Denv=ci -DincludeTags=e2e -DskipHealthCheck=true
```

Dry-run требует зависимостей и компиляции, создаёт отчёты с пропусками и не доказывает поведение продукта. Здесь `skipHealthCheck` допустим только потому, что тело теста не исполняется. Для реального e2e health-check не пропускайте.

В этом build нет обработки `-Dtest=Class#method`. Используйте `--tests` с точкой между классом и методом. Для тегов используйте поддерживаемые JUnit выражения. Пример prod-фильтра, а не разрешение его выполнить: `-DincludeTags='e2e & smoke' -DexcludeTags=screenshot`.

## Соглашения

Перед добавлением или изменением теста прочитайте [TESTING.md](TESTING.md): Page Object, fluent, локаторы, API-клиенты, данные и Allure для этого модуля.

Читайте существующий тест нужного вида и его базовый класс. API использует [ApiTestBase](src/test/java/api/ApiTestBase.java), браузерные проверки — [TestBase](src/test/java/tests/TestBase.java) и объекты из [pages](src/test/java/pages). Не создавайте драйвер и дублирующие селекторы в методе теста. Метаданные и теги должны соответствовать фактической проверке.

`screenshot` — способ проверки интерфейса. Обновление эталонов требует согласованного изменения UI и не является способом исправить неизвестное падение.

Manual-сценарии в [ExploratoryManualTests](src/test/java/tests/manual/ExploratoryManualTests.java) записывают шаги для человека. Их успешный запуск не подтверждает выполнение этих шагов.

## Результаты и CI

Отчёт Gradle: `build/reports/tests/test/`; XML: `build/test-results/test/`; Allure: `build/allure-results/`. Сохраняйте результаты каждой попытки отдельно перед повторным запуском. Не считайте весь накопленный каталог Allure результатом последней команды.

Проверки с тегом `infra` относятся к тестовым утилитам. JaCoCo gate из `build.gradle` ограничен выбранными утилитами и не измеряет покрытие продукта.

В сборке нет GitHub Actions workflow или готовых composite actions. CI требует отдельной конфигурации. Allure CLI и его зависимости заданы в [package.json](package.json); перед использованием CLI установите зависимости по lock-файлу.

Проверки утилит отчётности: `npm run test:meta`. Проверка конфигурации: `node scripts/validate-allurerc.mjs`. Они не обращаются к стенду продукта.

Sonar-панель появляется только при наличии результата в `allure/sonar-quality-gate.json` или файле из `SONAR_QUALITY_GATE_JSON`. Без результата панель отсутствует; некорректный файл вызывает ошибку. Демонстрационные успешные результаты не подставляются. Шаблон уведомлений по умолчанию не содержит Sonar-панели; отправка требует отдельной настройки адресата.
