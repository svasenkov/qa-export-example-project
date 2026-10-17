# Профиль выбранного проекта

Этот файл описывает конкретную сборку из autotests.ai. Общие skills читают его,
а команды и соглашения берут из README и конфигурации указанных модулей.
`write` разрешает изменение в пределах задачи, `read` — только чтение,
`none` — отсутствие доступной поверхности. Это не метрика покрытия.
Профиль не подтверждает наличие инструмента или доступ к внешнему стенду.

```yaml
product:
  backend:
    stack: go-stdlib
    access: write
    module: backend/go/backend-go-stdlib
  frontend:
    stack: typescript-vue
    access: write
    module: frontend/typescript/frontend-typescript-vue
automation:
  unit:
    access: write
    stack: go-stdlib
    module: backend/go/backend-go-stdlib
  integration:
    access: write
    stack: go-stdlib
    module: backend/go/backend-go-stdlib
  component:
    access: write
    stack: typescript-vue
    module: frontend/typescript/frontend-typescript-vue
  api:
    access: write
    stack: java-junit5-rest_assured-selenide
    module: tests/java/tests-java-junit5-rest_assured-selenide
  ui:
    access: write
    stack: java-junit5-rest_assured-selenide
    module: tests/java/tests-java-junit5-rest_assured-selenide
  e2e:
    access: write
    stack: java-junit5-rest_assured-selenide
    module: tests/java/tests-java-junit5-rest_assured-selenide
  manual:
    access: write
    stack: java-junit5-rest_assured-selenide
    module: tests/java/tests-java-junit5-rest_assured-selenide
load:
  access: none
  stack: slot
  module: ''
harness:
  agents:
    cline:
      access: write
      module: .clinerules
    cursor:
      access: write
      module: AGENTS.md
    claude:
      access: none
      module: .claude
    codex:
      access: write
      module: AGENTS.md
    copilot:
      access: none
      module: .github/copilot-instructions.md
    gigacode:
      access: none
      module: .gigacode
    yandex:
      access: none
      module: .yandex-code
```
