# Эталоны скриншотов

В текущей сборке сравнение скриншотов выполняет Java/Selenide-модуль.
Тесты помечены `@Tag("screenshot")`; команды запуска и ограничения стендов — в
[README модуля](../README.md).
Корневой [CI workflow](../../../../.github/workflows/ci.yml) содержит UI-проверки
на mock и ручной параметр обновления эталонов. Наличие конфигурации не подтверждает
успешное выполнение; расписание этих проверок не настроено.

Эталоны находятся в `tests/java/tests-java-junit5-rest_assured-selenide/src/test/resources/screenshots/`.
Путь выбирает [ScreenshotHelper](../src/test/java/helpers/ScreenshotHelper.java):

```text
{mock|stage|prod}/{linux|macos|windows}/{browser}-{major}/{area}/{viewport}.png
```

`-Denv=mock` выбирает `mock/`, `stage` — `stage/`, `prod` и `ci` — `prod/`.
Это соответствие каталогов, а не указание обращаться к prod при локальном запуске.
URL определяется конфигурацией стенда.

Переменные `SCREENSHOT_OS` и `SCREENSHOT_BROWSER` переопределяют сегменты пути.
Для ОС используются преобразования `darwin` → `macos`, `linux` → `linux`, `win32` → `windows`.
Браузер по умолчанию — `chrome`. Сейчас major берётся из Chrome for Testing pin
через `LocalChromePin` даже при другом `SCREENSHOT_BROWSER`: имя `firefox-148`
не подтверждает версию Firefox. Эти переменные сами по себе не выбирают браузер запуска.

`-DupdateScreenshots=true` записывает эталоны. Применяйте его после согласованного
изменения UI и просмотра различий, а не для устранения неизвестного падения.
Эталоны разных стендов, ОС и браузеров храните раздельно.

Vitest/Testing Library в frontend-модуле проверяют компоненты; эти проверки
не выполняют сравнение эталонных изображений.
