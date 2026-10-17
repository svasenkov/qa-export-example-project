# OpenSpec CLI

Версия закреплена в [package.json](package.json), транзитивные зависимости —
в `package-lock.json`. Требуется Node.js 20.19+ и npm. Установка из корня проекта:

```bash
npm ci --prefix tools/openspec --ignore-scripts
```

Команды ниже выполняются из корня проекта. Обёртка `cli.mjs` всегда выбирает
корень этой сборки, отключает телеметрию и проверку обновлений; она не устанавливает
пакеты автоматически и не меняет глобальные настройки.

```bash
# Структурная проверка всех текущих спецификаций и активных изменений
npm run validate --prefix tools/openspec
# Список текущих возможностей
npm run openspec --prefix tools/openspec -- list --specs
# Чтение одной спецификации
npm run openspec --prefix tools/openspec -- show authentication --type spec
```

## Новое изменение

Ниже `change-name` заменяется именем реальной задачи. Это команды инструмента,
а не автоматическая генерация кода или запуск тестов.

```bash
npm run openspec --prefix tools/openspec -- new change change-name
npm run openspec --prefix tools/openspec -- instructions proposal --change change-name
```

Агент пишет `proposal.md` по заданию и полученной инструкции. Далее аналогично
запросите инструкции для `specs`, `design`, `tasks` и создайте соответствующие
артефакты. Общий контекст и правила приходят из `openspec/config.yaml`.

```bash
npm run openspec --prefix tools/openspec -- status --change change-name
npm run openspec --prefix tools/openspec -- instructions apply --change change-name
```

Выполните задачи и проверки по [порядку работы](../../openspec/README.md).
После завершения, для изменения с дельтами:

```bash
npm run validate --prefix tools/openspec
npm run openspec --prefix tools/openspec -- archive change-name
npm run validate --prefix tools/openspec
```

Архивация интерактивно показывает обновления. Не используйте `--yes` для обхода
незавершённых задач; `--no-validate` не нужен для обычной работы. Для чисто
технического change без дельт укажите `skip_specs: true` в его `.openspec.yaml`
и передайте `--skip-specs` при архивации.

## Интеграции с ассистентами

Точка входа этой сборки — [AGENTS.md](../../AGENTS.md) и существующие `skills/`.
Сейчас работа с OpenSpec доступна через CLI и инструкции выше; tool-specific
slash-команды и сгенерированные skills не устанавливались. `openspec init` или
`update` могут записывать инструкции в служебные каталоги конкретного ассистента.
Подключайте их при выборе runtime, сверяя с текущим каталогом QA-процедур.

Обновление версии CLI выполняйте отдельным изменением с обновлением lockfile
и проверкой `validate`. CI использует этот же закреплённый пакет.

