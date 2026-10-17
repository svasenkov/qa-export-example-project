#!/usr/bin/env bash
# docker-entrypoint-initdb.d (first volume only). Same DB list as ensure-databases.sh.
# Emitted cell: backend-go-stdlib only. Naming: multistack_app_{language}_{stack}
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
  SELECT 'CREATE DATABASE multistack_app_go_stdlib'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'multistack_app_go_stdlib')\gexec
EOSQL
