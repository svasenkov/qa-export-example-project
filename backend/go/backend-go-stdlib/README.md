# backend-go-stdlib

Go API на `net/http` с PostgreSQL. Версия Go и зависимости — в [go.mod](go.mod). Маршруты — в [router.go](internal/api/router.go), контракт модуля — в [openapi.yaml](internal/api/openapi.yaml). Общий контракт сборки находится в [_contract/openapi.yaml](../../../_contract/openapi.yaml); при расхождении контрактов и реализации сначала установите ожидаемое поведение.

## Unit

Из этого модуля, без внешней БД:

```bash
env -u TEST_DATABASE_URL go test -count=1 ./...
```

Postgres integration-тесты в этом запуске пропускаются. Это не подтверждение integration-покрытия.

Одна существующая проверка:

```bash
go test -count=1 -v ./internal/security -run '^TestCredentialsValidate$'
```

`-count=1` отключает повторное использование кешированного результата тестов. Проверяйте фактический список выполненных и пропущенных тестов.

## Integration с PostgreSQL

[postgres_integration_test.go](internal/store/postgres_integration_test.go) проверяет хранилище на живой БД. Тесты применяют схему и меняют данные; используйте отдельную одноразовую базу. Это не тест HTTP-обработчика.

Для локальной одноразовой БД можно запустить отдельный контейнер (порт `55432` должен быть свободен):

```bash
docker run --rm -d --name qa-integration-postgres \
  -e POSTGRES_USER=multistack -e POSTGRES_PASSWORD=multistack \
  -e POSTGRES_DB=scratch -p 127.0.0.1:55432:5432 postgres:16-alpine
docker exec qa-integration-postgres pg_isready -U multistack -d scratch
```

Дождитесь успешного `pg_isready`, затем из backend-модуля:

```bash
TEST_DATABASE_URL='postgres://multistack:multistack@localhost:55432/scratch?sslmode=disable' \
  go test -count=1 -v ./internal/store -run '^TestPostgres'
```

Примерные логин и пароль относятся только к этому локальному контейнеру. После сохранения результатов остановите именно созданный для проверки контейнер:

```bash
docker stop qa-integration-postgres
```

Контейнер с `--rm` и его тестовые данные будут удалены. Не используйте для этих проверок базу приложения или внешнего стенда. Комплектный compose не публикует порт PostgreSQL и не создаёт `scratch`; его запуск не заменяет подготовку integration-БД.

## Приложение

Локальный запуск полного приложения описан в [корневом README](../../../README.md). Конфигурация backend — в [internal/config](internal/config), схема — в [schema.sql](schema.sql). Модуль при старте применяет схему и создаёт учебные данные.
