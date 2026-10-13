# Document Archive Service

Shared service that archives documents for other backends. Metadata and archiving state are stored
in PostgreSQL; the binary content goes to an S3-compatible object storage (MinIO locally).

## Prerequisites

- JDK 21
- Docker with Docker Compose v2

Maven is not required: the wrapper (`./mvnw`) downloads it.

## Local startup

Run every command from the project root.

### 1. Create the environment file

```bash
cp .env.example .env
```

Then fill in the four empty values in `.env`: the database username and password, and the object
storage access key and secret key (at least 8 characters). Any value works locally; the same file
configures both the containers and the application. `.env` is ignored by git.

### 2. Start PostgreSQL and MinIO

```bash
docker compose up -d
```

- PostgreSQL listens on port 5432. On its first start, an init script creates the `archive` schema.
- MinIO serves the S3 API on port 9000 and a web console on <http://localhost:9001>. Sign in to the
  console with the access key and secret key from `.env`.

### 3. Create the bucket

The bucket is not created automatically. Create it once per MinIO volume:

```bash
docker compose exec minio sh -c \
  'mc alias set local http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" \
   && mc mb --ignore-existing local/archive-documents'
```

`archive-documents` must match `ARCHIVE_S3_BUCKET` in `.env`.

### 4. Start the application

```bash
./mvnw package -DskipTests
java -jar infrastructure/target/infrastructure-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Liquibase creates or updates the tables at startup. Check that the service is up:

```bash
curl http://localhost:8080/actuator/health
```

It answers `{"groups":["liveness","readiness"],"status":"UP"}`.

## Environment variables

The application has no default for these settings: it does not start if one is missing.
`.env.example` is the reference list, with a comment on each variable.

| Variables | Purpose |
|---|---|
| `ARCHIVE_DB_NAME`, `ARCHIVE_DB_PORT` | Database name and published port of the PostgreSQL container |
| `ARCHIVE_DB_USERNAME`, `ARCHIVE_DB_PASSWORD` | Database credentials, shared by the container and the application |
| `ARCHIVE_DB_URL` | JDBC URL used by the application; keep it consistent with the name and port above |
| `ARCHIVE_S3_PORT`, `ARCHIVE_S3_CONSOLE_PORT` | Published ports of the MinIO container |
| `ARCHIVE_S3_ENDPOINT`, `ARCHIVE_S3_REGION`, `ARCHIVE_S3_BUCKET` | Object storage targeted by the application |
| `ARCHIVE_S3_ACCESS_KEY`, `ARCHIVE_S3_SECRET_KEY` | Object storage credentials; locally they are also the MinIO root account |
| `ARCHIVE_JWT_ISSUER`, `ARCHIVE_JWT_JWKS_URI`, `ARCHIVE_JWT_AUDIENCE` | Access token validation, see [Security](#security) |

## The `local` profile

The `local` profile only loads the `.env` file, so the application reads the same values as Docker
Compose. Without it, the variables must be set in the environment.

The file is looked up in the working directory and in its parent: start the application from the
project root or from the `infrastructure` module. From an IDE, activate the `local` profile and keep
one of these two working directories.

## Security

The service is an OAuth2 resource server: callers authenticate with a JWT access token sent in the
`Authorization: Bearer` header.

| Request | Requirement |
|---|---|
| `/actuator/health` and its probes | None, so monitoring can call them |
| `GET /api/v1/documents/**` | Valid token with the `documents:read` scope |
| Other methods on `/api/v1/documents/**` | Valid token with the `documents:write` scope |
| Anything else | Denied |

A token is valid when it is signed by a key published at `ARCHIVE_JWT_JWKS_URI`, issued by
`ARCHIVE_JWT_ISSUER`, intended for `ARCHIVE_JWT_AUDIENCE` and not expired. A missing or invalid
token gets `401`; a valid token without the required scope gets `403`.

**No identity provider runs locally yet.** The `ARCHIVE_JWT_*` values in `.env.example` are
placeholders: they let the application start, but no token can be obtained, so every request
outside the health endpoint answers `401`. Token validation is covered by the integration tests,
which sign their own tokens.

## Troubleshooting

- **The `archive` schema is missing**: the PostgreSQL init script only runs on an empty data volume.
  `docker compose down -v` deletes the volumes, so the next start replays it. This also erases the
  local data and the bucket.
- **The container name `miniostorage` is already in use**: the name is fixed, so two copies of the
  project cannot run on the same machine at the same time.
