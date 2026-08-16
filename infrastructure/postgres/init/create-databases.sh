#!/usr/bin/env bash
# Run by the postgres image's docker-entrypoint-initdb.d on a fresh volume only. Creates a role and a database
# per service from the six FINANCE_BOT_*_DB* variables, and the vector extension inside the connector's
# database. The bootstrap superuser (POSTGRES_USER/POSTGRES_DB) is used by nothing else.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    CREATE ROLE "$FINANCE_BOT_LEDGER_DB_USER" WITH LOGIN PASSWORD '$FINANCE_BOT_LEDGER_DB_PASSWORD' REPLICATION;
    CREATE DATABASE "$FINANCE_BOT_LEDGER_DB" OWNER "$FINANCE_BOT_LEDGER_DB_USER";

    CREATE ROLE "$FINANCE_BOT_AI_DB_USER" WITH LOGIN PASSWORD '$FINANCE_BOT_AI_DB_PASSWORD';
    CREATE DATABASE "$FINANCE_BOT_AI_DB" OWNER "$FINANCE_BOT_AI_DB_USER";
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$FINANCE_BOT_AI_DB" <<-SQL
    CREATE EXTENSION IF NOT EXISTS vector;
SQL
