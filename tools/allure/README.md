# Общая отчётность Allure

Один npm-пакет для всей сборки: CLI, оформление, графики и quality gate.
Java и Vue используют его через небольшие `allurerc.mjs` в своих модулях.
Адаптеры тестовых раннеров остаются в модулях: они записывают Allure results.
Go сейчас не создаёт Allure results; для него сначала нужен адаптер или конвертер.

## Установка и проверка

Из корня репозитория, Node 26 (версия — в [.nvmrc](.nvmrc)):

```bash
npm ci --prefix tools/allure
npm test --prefix tools/allure
node tools/allure/validate.mjs tests/java/tests-java-junit5-rest_assured-selenide/allurerc.mjs
node tools/allure/validate.mjs frontend/typescript/frontend-typescript-vue/allurerc.mjs
```

Это локальные проверки отчётности; продуктовые тесты и обращения к стендам не выполняются.
Версия report-kit 0.3.5 предупреждает `overview-preset`, когда Sonar-панель отсутствует:
встроенная проверка библиотеки ожидает обе панели. Наш валидатор проверяет фактическую
компоновку с необязательным Sonar; фиктивный успешный результат для устранения предупреждения не нужен.

## Генерация

После выполнения тестов, из корня репозитория:

```bash
# Java API/UI/E2E
tools/allure/node_modules/.bin/allure generate \
  --config tests/java/tests-java-junit5-rest_assured-selenide/allurerc.mjs \
  --output tests/java/tests-java-junit5-rest_assured-selenide/build/reports/allure-local \
  tests/java/tests-java-junit5-rest_assured-selenide/build/allure-results

# Vue component
tools/allure/node_modules/.bin/allure generate \
  --config frontend/typescript/frontend-typescript-vue/allurerc.mjs \
  --output frontend/typescript/frontend-typescript-vue/build/reports/allure-local \
  frontend/typescript/frontend-typescript-vue/allure-results
```

Выбирайте новый выходной каталог для каждой попытки, если предыдущий отчёт нужен.
История хранится у каждого модуля отдельно. Генерация дописывает её; исходные результаты
не очищает. Накопленные результаты нескольких запусков не равны последнему запуску.
Gradle `allureReport` тоже использует Java-конфигурацию; общие npm-зависимости должны быть установлены.

## Новый тестовый модуль

Подключите адаптер Allure своего раннера и добавьте `allurerc.mjs` по образцу
[Vue](../../frontend/typescript/frontend-typescript-vue/allurerc.mjs).
Укажите импорт общей фабрики, `slug`, `resultsDir` и `historyFile` через `new URL(..., import.meta.url)`.
Передавайте тот же каталог результатов в CLI. Пути тогда не зависят от текущей рабочей папки.
Копировать `tools/allure` или создавать ещё один package-lock для отчётности не нужно.

Правила в [quality-gate.mjs](quality-gate.mjs) проверяют падения и метаданные шагов/вложений
по Allure labels `layer` и `tag`. Новый адаптер должен передавать эти labels, если нужны
соответствующие проверки. Quality gate отчёта не заменяет exit code раннера или статус CI.

`sonarFile` — необязательный путь к JSON с фактическим результатом Sonar.
Java-конфигурация берёт его из `SONAR_QUALITY_GATE_JSON`. Нет входа — нет панели;
некорректный вход — ошибка. Автоматического запроса Sonar и отправки уведомлений нет.

Общего отчёта всех CI jobs пока нет: workflow сохраняет результаты отдельно.
Новый адаптер или агрегация подключаются отдельной задачей; перенос конфигурации сам по себе
не добавляет результаты Go или объединение запусков.
