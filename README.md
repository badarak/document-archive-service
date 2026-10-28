# Document Archive Service

Shared service that archives documents for other backends. Metadata and archiving state are stored
in PostgreSQL; the binary content goes to an S3-compatible object storage (MinIO locally).

## Prerequisites

- JDK 21
- Maven 3.9 or later
- Docker with Docker Compose v2
- `jq`, to read the access token in the local examples

## Local startup

Run every command from the project root.

### 1. Create the environment file

```bash
cp .env.example .env
```

Then fill in the nine empty values in `.env`: the database username and password, the object
storage access key and secret key (at least 8 characters), the Keycloak admin username and password,
the secrets of the two calling services and the secret of the Prometheus scraper. Any value works locally; the same file configures both
the containers and the application. `.env` is ignored by git.

An existing `.env` must also get the `ARCHIVE_KEYCLOAK_*` variables of `.env.example`: Docker Compose
checks the variables of every service, so without them it starts none of the containers.

### 2. Start PostgreSQL, MinIO and Keycloak

```bash
docker compose up -d
```

- PostgreSQL listens on port 5432. On its first start, an init script creates the `archive` schema.
- MinIO serves the S3 API on port 9000 and a web console on <http://localhost:9001>. Sign in to the
  console with the access key and secret key from `.env`.
- Keycloak, the local authorization server, listens on port 8180, with its admin console on
  <http://localhost:8180>. It imports the `archive` realm from `docker/keycloak` at each start and
  keeps nothing: changes made in the console are lost on restart.

### 3. Create the bucket

The bucket is not created automatically. Create it once per MinIO volume, from the MinIO console
at <http://localhost:9001>. Its name must match `ARCHIVE_S3_BUCKET` in `.env` (`archive-documents`
by default).

### 4. Start the application

```bash
mvn spring-boot:run
```

The command builds the modules and starts the application with the `local` profile. Liquibase
creates or updates the tables at startup. Check that the service is up:

```bash
curl http://localhost:8080/actuator/health
```

It answers `{"groups":["liveness","readiness"],"status":"UP"}`.

## Environment variables

The application has no default for these settings, except the optional ones: it does not start if
one is missing.
`.env.example` is the reference list, with a comment on each variable.

| Variables | Purpose |
|---|---|
| `ARCHIVE_DB_NAME`, `ARCHIVE_DB_PORT` | Database name and published port of the PostgreSQL container |
| `ARCHIVE_DB_USERNAME`, `ARCHIVE_DB_PASSWORD` | Database credentials, shared by the container and the application |
| `ARCHIVE_DB_URL` | JDBC URL used by the application; keep it consistent with the name and port above |
| `ARCHIVE_S3_PORT`, `ARCHIVE_S3_CONSOLE_PORT` | Published ports of the MinIO container |
| `ARCHIVE_KEYCLOAK_PORT`, `ARCHIVE_KEYCLOAK_ADMIN_USERNAME`, `ARCHIVE_KEYCLOAK_ADMIN_PASSWORD` | Published port and admin account of the Keycloak container |
| `ARCHIVE_KEYCLOAK_ACME_CLIENT_SECRET`, `ARCHIVE_KEYCLOAK_GLOBEX_CLIENT_SECRET` | Secrets of the two calling services declared in the local realm |
| `ARCHIVE_KEYCLOAK_PROMETHEUS_CLIENT_SECRET` | Secret of the `prometheus` client of the local realm, whose tokens carry only the `metrics:read` scope |
| `ARCHIVE_S3_ENDPOINT`, `ARCHIVE_S3_REGION`, `ARCHIVE_S3_BUCKET` | Object storage targeted by the application |
| `ARCHIVE_S3_ACCESS_KEY`, `ARCHIVE_S3_SECRET_KEY` | Object storage credentials; locally they are also the MinIO root account |
| `ARCHIVE_S3_API_CALL_TIMEOUT` | Optional. Longest time a storage call may take, retries included (`2m` by default) |
| `MANAGEMENT_SERVER_PORT` | Optional. Port of the actuator endpoints (health probes and Prometheus), the application port by default; a separate port keeps them off the public network |
| `ARCHIVE_OTLP_URL` | Optional. OTLP endpoint of the `observability` profile (`http://localhost:4318` by default) |
| `ARCHIVE_GRAFANA_PORT`, `ARCHIVE_OTLP_PORT` | Optional. Ports of the Grafana container of the `observability` Compose profile (`3000` and `4318` by default) |
| `ARCHIVE_TRACING_SAMPLING` | Optional. Share of the requests whose trace is recorded, from `0` to `1` (`0.1` by default, `1.0` with the `local` profile) |
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | Optional. OTLP endpoint of the collector the recorded traces are sent to, such as `http://otel-collector:4318/v1/traces`; none by default |
| `ARCHIVE_LOG_FORMAT` | Optional. Console log format: `ecs` (JSON lines, by default), `logstash`, `gelf`, or empty for the readable format of the `local` profile |
| `ARCHIVE_LOG_TENANT` | Optional. Whether the log line of each API operation names the tenant of the caller (`false` by default) |
| `ARCHIVE_SHUTDOWN_TIMEOUT` | Optional. Longest wait for the requests in progress on shutdown (`150s` by default). Keep it above `ARCHIVE_S3_API_CALL_TIMEOUT`, and give the orchestrator at least as long before it kills the process |
| `ARCHIVE_JWT_ISSUER`, `ARCHIVE_JWT_JWKS_URI`, `ARCHIVE_JWT_AUDIENCE` | Access token validation, see [Security](#security) |
| `ARCHIVE_JWT_TENANT_CLAIM`, `ARCHIVE_JWT_SOURCE_CLAIM` | Optional. Token claims naming the caller's tenant and the calling service (`tenant_id` and `azp` by default) |
| `ARCHIVE_JWT_ALGORITHMS` | Optional. Signature algorithms accepted from the issuer (`RS256` by default) |
| `ARCHIVE_OPENAPI_ENABLED` | Optional. Publishes the OpenAPI description and the Swagger UI (`true` with the `local` profile, `false` otherwise) |
| `ARCHIVE_RECONCILIATION_ENABLED`, `ARCHIVE_RECONCILIATION_INTERVAL`, `ARCHIVE_RECONCILIATION_GRACE_PERIOD`, `ARCHIVE_RECONCILIATION_BATCH_SIZE` | Optional. Resumption of the interrupted deletions and abandonment of the interrupted archivings, see [API](#api) (`true`, `5m`, `15m` and `100` by default) |
| `ARCHIVE_ALLOWED_EXTENSIONS` | Optional. Formats callers may archive, among `pdf`, `doc`, `docx`, `mp3`, `wav`, `m4a`, `ogg` and `eml` (all by default). An unknown extension stops the application at startup |
| `ARCHIVE_MAX_FILE_SIZE` | Optional. Maximum size of an archived file, in bytes or with a unit such as `20MB` (`52428800`, 50 MiB, by default). Above 50 MiB, `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` and `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` must be raised too, or the application does not start |
| `ARCHIVE_REJECT_MIME_MISMATCH` | Optional. Whether a declared content type that does not match the format of the file is rejected (`true` by default); when `false`, the media type of the format is stored instead. A content that does not match its extension is always rejected |
| `SPRING_SERVLET_MULTIPART_LOCATION` | Optional. Directory of the temporary files the uploads are written to, before their validation and their upload; the temporary directory of the server by default. Point it to a directory only the application can read. Each file is removed once its request is answered |

## The `local` profile

The `local` profile only loads the `.env` file, so the application reads the same values as Docker
Compose. Without it, the variables must be set in the environment. `mvn spring-boot:run`
activates it by itself.

The file is looked up in the working directory and in its parent: start the application from the
project root or from the `infrastructure` module. From an IDE, activate the `local` profile and keep
one of these two working directories.

## API

The API is described by OpenAPI: browse it at <http://localhost:8080/swagger-ui/index.html>, or read
the raw description at `/v3/api-docs`. Both are published with the `local` profile only; set
`ARCHIVE_OPENAPI_ENABLED=true` to publish them elsewhere.

Archiving a document takes a multipart request with a `file` part and a JSON `metadata` part:

```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@invoice-123.pdf;type=application/pdf" \
  -F 'metadata={"documentType": "INVOICE", "businessReference": "INV-2026-000123"};type=application/json'
```

The answer is `201 Created`, with the document's `Location` and metadata. Errors are Problem Details
documents (RFC 9457) with a `code` property, such as `unsupported-format` or `document-too-large`;
the OpenAPI description lists them per status.

Before anything is recorded or uploaded, the file is validated; a refused file leaves no trace and
never becomes `AVAILABLE`:

| Check | Refusal |
|---|---|
| Not empty, and not above `ARCHIVE_MAX_FILE_SIZE` (50 MiB by default) | `400 empty-document`, `413 document-too-large` |
| A file name without path (`/`, `\`, `..`), control character nor invisible formatting character (such as a right-to-left override) | `400 invalid-document` |
| An extension among `ARCHIVE_ALLOWED_EXTENSIONS` | `415 unsupported-format` |
| A declared content type matching the extension (or replaced by the media type of the format, with `ARCHIVE_REJECT_MIME_MISMATCH=false`) | `415 content-type-mismatch` |
| A content of the format the extension announces, read from its first bytes; a program (Windows, Linux, macOS, script) is never accepted, whatever its name | `415 content-mismatch` |
| A well-formed file of that format (below) | `422 malformed-document` |

| Format | Structure checked |
|---|---|
| PDF | An end-of-file marker in the last kilobyte |
| DOC | An OLE2 compound file holding a `WordDocument` stream, without `Macros` storage |
| DOCX | A ZIP package listing `[Content_Types].xml` and `word/document.xml`, without VBA project (macros) |
| MP3 | Two consecutive valid MPEG audio frames, shortly after the optional ID3 tag |
| WAV | A RIFF file whose format chunk (encoding, channels, sample rate) comes before its audio data |
| M4A | An MPEG-4 file type box naming an audio or generic MPEG-4 brand |
| OGG | A first page opening a Vorbis, Opus, FLAC or Speex stream |
| EML | Text starting with RFC 5322 header fields, among them `From` or `Date`, in its first 64 KiB |

Only bounded parts of the file are read for these checks, from the temporary file of the upload:
nothing is decompressed nor loaded whole into memory. They tell what a file is, not that it is
harmless: a well-formed PDF may still carry malicious content.

A deletion first records the document as `DELETING`, which hides it, then removes its content from
the storage and records it as `DELETED`. When the storage fails in between, the answer is `503` and
the document stays `DELETING`: replaying the `DELETE` finishes it, and so does the reconciliation that
every instance runs in the background, for deletions left over for longer than the grace period.

An archiving first records the document as `PENDING`, uploads its content, then records it as
`AVAILABLE`. When it fails, the answer is an error and the caller archives the file again as a new
document: the service removes whatever content reached the storage and records the failed one as
`FAILED`. If that cleanup fails too, or if the instance stops during the upload, the document stays
`PENDING`; the same reconciliation removes its content and records it as `FAILED` once the grace
period is over. The grace period must therefore exceed `ARCHIVE_S3_API_CALL_TIMEOUT`, the longest an
upload can take, or the application does not start.

Every status change is recorded only if the document is still in the status it leaves, in a single
statement: of two concurrent changes, such as a deletion replayed during the reconciliation, only
one applies. An upload that completes after its archiving was abandoned removes its content and is
answered with `503` instead of making the document `AVAILABLE`.

The reconciliation handles each document on its own: one that fails, such as an object the storage
refuses to delete, stays in its status for the next run without holding back the others, and a row
that no longer meets the domain rules is left out. A run stops after three failures in a row, the
sign of an outage. Every abandoned archiving and every failure is logged as a warning, and each run
publishes Micrometer metrics, tagged with `status` (`pending` or `deleting`), ready for any exporter:

| Metric | Meaning |
|---|---|
| `archive.reconciliation.backlog` | Documents still stalled after the last run |
| `archive.reconciliation.backlog.oldest.age` | Seconds since the oldest of them was last updated |
| `archive.reconciliation.documents` | Documents moved on or not, tagged `outcome` (`completed`, `failed`) |
| `archive.reconciliation.runs` | Runs, tagged `outcome` (`completed`, `interrupted`, `failed`) |

A backlog that grows, or an oldest age far above the grace period, means the reconciliation is
blocked or disabled.

## Observability

### Logs

Outside the `local` profile, the logs are JSON lines in the Elastic Common Schema. Each operation of
the document API writes one line once its answer is complete, downloads included, with as key-value
pairs `operation` (`UPLOAD`, `DOWNLOAD`, `DELETE`, `READ`, `SEARCH`), `documentId`, `status`, `outcome`
(`success`, `client-error`, `server-error`), `durationMs`, `errorCode` (the `code` of the error
answer) and, only when `ARCHIVE_LOG_TENANT` is `true`, `tenantId`. The other lines of the request,
such as a stack trace, share its `traceId`. Requests refused by the security are logged too. The content, the token, the storage credentials, the file
name and the business reference are never logged.

### Metrics

Micrometer records, tagged with `application`:

| Metric | Type | Tags |
|---|---|---|
| `archive.upload`, `archive.download`, `archive.delete`, `archive.read`, `archive.search` | Timer: count and duration of each API operation, downloads streamed to their end | `outcome` (`success`, `client-error`, `server-error`), `error.code` (`none` on success) |
| `archive.storage` | Timer: count and duration of each call to the object storage, until the first bytes for a read | `operation` (`store`, `open`, `delete`), `outcome` (`success`, `missing`, `failure`) |
| `archive.reconciliation.*` | See the reconciliation above | `status` |

No identifier is ever a tag: the document and the tenant belong to the logs.

Prometheus scrapes them at `/actuator/prometheus`, with a token carrying the `metrics:read` scope
(`authorization` in its scrape configuration). Set `MANAGEMENT_SERVER_PORT` to serve the actuator
endpoints, the probes included, on a port of their own that the public network does not reach.

Locally, the `prometheus` client of the Keycloak realm gets such a token:

```bash
source .env
TOKEN=$(curl -s http://localhost:8180/realms/archive/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=prometheus \
  -d client_secret="$ARCHIVE_KEYCLOAK_PROMETHEUS_CLIENT_SECRET" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/prometheus | grep '^archive_'
```

### Grafana, locally

The `observability` Compose profile starts Grafana with Prometheus, Loki and Tempo (the
`grafana/otel-lgtm` image), and the `observability` Spring profile makes the application push its
metrics, logs and traces to it over OTLP:

```bash
docker compose --profile observability up -d
mvn spring-boot:run -Dspring-boot.run.profiles=local,observability   # or both profiles in the IDE
```

Open <http://localhost:3000> (`admin` / `admin`): the **Document Archive Service** dashboard shows the
API operations by outcome and error code, their duration, the download error rate, the storage calls,
the stalled documents and the reconciliation runs, next to the operation logs and the warnings. A log
line links to its trace in Tempo. The dashboard is `docker/grafana/dashboards/document-archive.json`,
and its queries work as well on a Prometheus that scrapes `/actuator/prometheus`.

Without the Spring profile nothing is pushed; with it, the application logs export errors while the
Grafana container is down.

### Traces

Every request gets a W3C trace, continued from the `traceparent` header of the caller when it sends
one. Its `traceId` is in every log line of the request and in every error answer, so that a caller
can report the exact request that failed. A share of the traces is recorded (`ARCHIVE_TRACING_SAMPLING`,
10% by default, all of them with the `local` profile), with a span per API operation and per storage
call, and sent to an OpenTelemetry collector once
`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` names one. The metrics are not pushed over
OTLP: Prometheus scrapes them.

## Security

The service is an OAuth2 resource server: callers authenticate with a JWT access token sent in the
`Authorization: Bearer` header.

| Request | Requirement |
|---|---|
| `/actuator/health` and its probes | None, so monitoring can call them |
| `GET /actuator/prometheus` | Valid token with the `metrics:read` scope; the Prometheus scraping endpoint |
| `/.well-known/oauth-protected-resource` | None; standard metadata published by Spring Security (the service URL and the bearer method) |
| `/v3/api-docs` and `/swagger-ui/**` | None; the OpenAPI description of the API, when `ARCHIVE_OPENAPI_ENABLED` is true |
| `GET /api/v1/documents/**` (read, search, download) | Valid token with the `documents:read` scope |
| `POST /api/v1/documents` (archive) | Valid token with the `documents:write` scope |
| `DELETE /api/v1/documents/{id}` (delete) | Valid token with the `documents:delete` scope |
| Anything else, including other methods on the document API | Denied, even with a valid token |

The authorization server signs each token with its private key, which it never shares. It publishes
the matching public keys at `ARCHIVE_JWT_JWKS_URI`; the service downloads them to verify the
signature. A token is valid when:

- it is signed with an accepted algorithm, `ARCHIVE_JWT_ALGORITHMS` (`RS256` by default), and one of
  these public keys verifies the signature: an unsigned token is always refused;
- it was issued by `ARCHIVE_JWT_ISSUER`;
- it is intended for `ARCHIVE_JWT_AUDIENCE`;
- it is already valid and has not expired;
- it is sent in the `Authorization` header, never in the URL.

A missing or invalid token gets `401`; a valid token without the required scope gets `403`.

Each archived document records the tenant and the source of its caller, read from two token claims:
`tenant_id` for the tenant and `azp` (the OAuth2 client the token was issued to) for the source. The
authorization server must add the tenant claim, which is not standard.

### Getting a token locally

The local Keycloak declares two calling services, each with its own tenant, which get tokens with the
OAuth2 client credentials grant:

| Client | `tenant_id` | Secret |
|---|---|---|
| `billing-service` | `acme` | `ARCHIVE_KEYCLOAK_ACME_CLIENT_SECRET` |
| `globex-billing-service` | `globex` | `ARCHIVE_KEYCLOAK_GLOBEX_CLIENT_SECRET` |

A token carries the scopes it asks for, among `documents:read`, `documents:write` and
`documents:delete`, and is valid for 15 minutes:

```bash
source .env
TOKEN=$(curl -s http://localhost:8180/realms/archive/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=billing-service \
  -d client_secret="$ARCHIVE_KEYCLOAK_ACME_CLIENT_SECRET" \
  --data-urlencode "scope=documents:read documents:write documents:delete" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/documents?documentType=INVOICE"
```

A token of `globex-billing-service` never sees the documents of `acme`, and a token without the
required scope gets `403`. Token validation is also covered by the integration tests, which sign
their own tokens.

### Getting a token from the Swagger UI

With the `local` profile, the Swagger UI gets the token itself: under *Authorize*, in the `oauth2`
section, tick the scopes and click *Authorize*. It acts as `billing-service`, the calling service of
the `acme` tenant, whose client id and secret it reads from `.env`. To act for `globex`, replace them
with `globex-billing-service` and its secret in the same dialog, or paste any token in the
`bearer-jwt` section.

## Tests

```bash
mvn test      # unit tests only
mvn verify    # unit and integration tests, code style and the 80 % coverage threshold
```

- **Unit tests** are named `*Test`. They use JUnit and MockK, and start no Spring context.
- **Integration tests** are named `*IT` and live in the `infrastructure` module only. They start a
  Spring context with the `test` profile: an in-memory H2 database in PostgreSQL mode and
  placeholder storage settings. They need neither Docker nor the `.env` file.

Nothing is tested automatically against a real PostgreSQL or MinIO.

## Troubleshooting

- **The `archive` schema is missing**: the PostgreSQL init script only runs on an empty data volume.
  `docker compose down -v` deletes the volumes, so the next start replays it. This also erases the
  local data and the bucket.
- **The container name `miniostorage` is already in use**: the name is fixed, so two copies of the
  project cannot run on the same machine at the same time.

## AI assistance

This repository is developed with the help of Claude (Anthropic), through Claude Code: code, tests,
reviews and the analyses in `docs/`. The assistant follows the rules in `CLAUDE.md`, and every change
is reviewed, tested and merged by the maintainer, who remains responsible for it. Assisted commits
carry a `Co-Authored-By: Claude` trailer (`git log --grep "Co-Authored-By: Claude"` lists them), and
assisted pull requests say so at the end of their description.
