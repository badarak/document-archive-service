# Permissions

Each account the service uses holds the least it needs. This page lists them, and how to create them.

## PostgreSQL

| Role | Used by | Rights |
|---|---|---|
| `archive_migrator` | The Liquibase migrations (`ARCHIVE_DB_MIGRATOR_*`) | Owner of the `archive` schema: creates and alters its tables, holds the Liquibase tracking tables |
| `archive_app` | The application at runtime (`ARCHIVE_DB_USERNAME`, `ARCHIVE_DB_PASSWORD`) | `CONNECT` on the database, `USAGE` on the schema, `SELECT`, `INSERT`, `UPDATE` and `DELETE` on its tables. No DDL, no other schema, not superuser |

Neither role is superuser, nor may create databases or roles. A superuser only runs the script below, once.

```sql
-- As a superuser, connected to the archive database.
CREATE ROLE archive_migrator LOGIN PASSWORD '<from Vault>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE archive_app LOGIN PASSWORD '<from Vault>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

REVOKE ALL ON DATABASE archive FROM PUBLIC;
GRANT CONNECT ON DATABASE archive TO archive_migrator, archive_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA archive AUTHORIZATION archive_migrator;
GRANT USAGE ON SCHEMA archive TO archive_app;
-- Every table the migrator creates is readable and writable by the application, and nothing more.
ALTER DEFAULT PRIVILEGES FOR ROLE archive_migrator IN SCHEMA archive
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO archive_app;
```

Locally, `docker/postgres/init/01-create-roles.sh` runs the same statements with the accounts of `.env`.

The application refuses to start while the migrations run at startup without a migrator account. Once a separate job
runs them, set `ARCHIVE_DB_MIGRATIONS_ENABLED=false` on the application: it then never needs the migrator password.

To check the rights of an existing database:

```sql
SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'archive';
SELECT table_name, string_agg(privilege_type, ', ') FROM information_schema.role_table_grants
WHERE grantee = 'archive_app' GROUP BY table_name;
```

## Object storage

The application only puts, gets and deletes objects, under the `documents/` prefix of one bucket: it never lists the
bucket, nor creates one.

| Provider | Account of the application |
|---|---|
| **Cloudflare R2** (production) | An API token with the **Object Read & Write** permission, **limited to the archive bucket**, one per environment. R2 cannot grant deletion apart from writing: this is the least it allows |
| AWS S3 | No keys: a role (IRSA on EKS, instance role) found by the standard AWS chain, with the policy below |
| MinIO (local) | `ARCHIVE_S3_ACCESS_KEY`, created by `docker/minio/setup.sh` with the policy below. The root account (`ARCHIVE_S3_ROOT_*`) only serves the console and the setup |

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::<bucket>/documents/*"]
    }
  ]
}
```

The bucket itself stays private (no public access, no public URL), and is created outside the application: by the
platform in production, by the setup container locally.
