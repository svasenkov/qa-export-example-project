#!/usr/bin/env bash
# Idempotent: create the backend database (safe on existing volumes).
# Emitted cell: backend-go-stdlib only. Naming: multistack_app_{language}_{stack}
# — mirrors backend module id without "backend-".
set -euo pipefail

export PGPASSWORD="${POSTGRES_PASSWORD:-multistack}"
HOST="${PGHOST:-postgres}"
USER="${POSTGRES_USER:-multistack}"

for db in multistack_app_go_stdlib; do
  exists="$(psql -h "$HOST" -U "$USER" -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname='${db}'")"
  if [[ "$exists" != "1" ]]; then
    psql -h "$HOST" -U "$USER" -d postgres -v ON_ERROR_STOP=1 \
      -c "CREATE DATABASE ${db}"
    echo "created database ${db}"
  else
    echo "database ${db} already exists"
  fi
done
