#!/bin/sh
# Local provisioning of the database accounts, as in production (docs/security/permissions.md). Runs once, as the
# superuser of the container, when the data volume is first initialised: use "docker compose down -v" to replay it.
#   - the migrator owns the archive schema: Liquibase creates and alters the tables with it;
#   - the application only reads and writes rows, in the tables the migrator creates.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v database="$POSTGRES_DB" \
  -v migrator="$ARCHIVE_DB_MIGRATOR_USERNAME" -v migrator_password="$ARCHIVE_DB_MIGRATOR_PASSWORD" \
  -v app="$ARCHIVE_DB_USERNAME" -v app_password="$ARCHIVE_DB_PASSWORD" <<'SQL'
CREATE ROLE :"migrator" LOGIN PASSWORD :'migrator_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE :"app" LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database" TO :"migrator", :"app";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA archive AUTHORIZATION :"migrator";
GRANT USAGE ON SCHEMA archive TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator" IN SCHEMA archive GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
SQL
