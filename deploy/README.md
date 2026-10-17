# Локальные стенды Compose

Эти файлы используются корневым [docker-compose.yml](../docker-compose.yml).
Команды запуска и отдельные локальные порты — в [README](../README.md).

- `ci/nginx.conf` — gateway к настоящим backend и frontend.
- `mock/nginx.conf` — gateway к frontend и WireMock, включая `/__admin/` для переключения сценариев.
- `mock/mappings/` — ответы и сценарии API для UI-тестов; от языка тестового раннера не зависят.
- `postgres/ensure-databases.sh` — создание БД до запуска backend на новом или существующем volume.

Сервис `postgres-ensure-dbs` запускается после готовности PostgreSQL, а backend ждёт его завершения.
Отдельного init-скрипта с тем же SQL нет. Данные существующей БД сохраняются.
При добавлении backend с другой БД нужно явно дополнить конфигурацию; новый набор тестов
сам по себе не требует копии `deploy`.

Две конфигурации nginx оставлены явными: mock дополнительно открывает WireMock admin API.
Продакшен-деплой здесь не настроен. Прежний `docker-compose.prod.yml` повторял
`restart: unless-stopped` из основного файла и не менял конфигурацию.

Проверка конфигурации без запуска контейнеров, из корня:

```bash
docker compose --profile mock config --quiet
bash -n deploy/postgres/ensure-databases.sh
```
