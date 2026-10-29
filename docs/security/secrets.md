# Secrets

The service never holds a secret in the repository, nor a default value for one: each comes from its environment, and
the application does not start when a required one is missing or empty.

## Where the secrets live

| Environment | Store | Delivery to the application |
|---|---|---|
| Local | `.env`, ignored by Git (`.env.example` lists the variables, all empty) | Read by Compose and by the `local` profile |
| Production | **HashiCorp Vault**, KV version 2 engine | Rendered as **files**, one per variable, in `/run/secrets` (`ARCHIVE_SECRETS_DIR`) |

Files are preferred over environment variables: those are visible in `/proc/<pid>/environ`, in `docker inspect` and in
some crash reports. The application reads each file of `ARCHIVE_SECRETS_DIR` as the variable it is named after
(`spring.config.import: optional:configtree:…`); an environment variable of the same name still wins, for local runs.

## Secrets of the service

| Variable | Secret | Rotation |
|---|---|---|
| `ARCHIVE_DB_PASSWORD` | Password of the `archive_app` PostgreSQL role (see [permissions](permissions.md)) | Set a new password on the role, update Vault, restart the instances; the old password stops working at once, so rotate during a quiet period, or create a second role and switch to it |
| `ARCHIVE_DB_MIGRATOR_PASSWORD` | Password of the `archive_migrator` role, read by the migration job only | As above, between two releases |
| `ARCHIVE_S3_ACCESS_KEY`, `ARCHIVE_S3_SECRET_KEY` | R2 API token limited to the archive bucket | Create a second token, update Vault, restart the instances, then revoke the first token |

The database username and URL, the bucket, the endpoint and the authorization server URIs are configuration, not
secrets: they may stay plain environment variables.

## Vault layout

```
secret/data/document-archive-service/<environment>
    ARCHIVE_DB_PASSWORD
    ARCHIVE_S3_ACCESS_KEY
    ARCHIVE_S3_SECRET_KEY
secret/data/document-archive-service-migrations/<environment>
    ARCHIVE_DB_MIGRATOR_PASSWORD
```

Each workload reads only its own path, with the Kubernetes auth method:

```hcl
# Policy document-archive-service-<environment>
path "secret/data/document-archive-service/<environment>" {
  capabilities = ["read"]
}
```

```bash
vault write auth/kubernetes/role/document-archive-service-<environment> \
  bound_service_account_names=document-archive-service \
  bound_service_account_namespaces=<namespace> \
  policies=document-archive-service-<environment> \
  ttl=1h
```

The migration job has its own service account, role and policy on the `-migrations` path: the running application
never sees the migrator password.

## Rendering the files

With the Vault Agent Injector, the pod annotations render each key as a file of `/run/secrets`:

```yaml
metadata:
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: document-archive-service-<environment>
    vault.hashicorp.com/secret-volume-path: /run/secrets
    vault.hashicorp.com/agent-inject-secret-ARCHIVE_DB_PASSWORD: secret/data/document-archive-service/<environment>
    vault.hashicorp.com/agent-inject-template-ARCHIVE_DB_PASSWORD: |
      {{- with secret "secret/data/document-archive-service/<environment>" -}}{{ .Data.data.ARCHIVE_DB_PASSWORD }}{{- end -}}
    # Same pair of annotations for ARCHIVE_S3_ACCESS_KEY and ARCHIVE_S3_SECRET_KEY.
```

The Vault CSI provider is an equivalent option. Either way, the agent authenticates with the token of the pod's
service account: keep it mounted for the agent, the application itself never calls the Kubernetes API.

The secrets are read at startup: after a rotation, restart the instances (a rolling restart keeps the service up).

## What must never happen

- A secret in Git, in an image or in a log: `.env` is ignored, `.dockerignore` excludes it, and neither the logs, the
  traces nor the error answers carry credentials (a test checks it with sentinel values).
- A default value for a secret in `application.yaml`.
- The `local` profile outside a developer machine: it publishes the Swagger UI with the secret of a local Keycloak
  client.
- The password in `ARCHIVE_DB_URL` (`?password=`): connection errors print the URL.
