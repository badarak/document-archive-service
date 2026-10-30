{{/*
The names stay those of the chart, whatever the release: the Vault roles are bound to the service account names.
*/}}
{{- define "archive.name" -}}
{{ .Chart.Name }}
{{- end }}

{{- define "archive.migrationName" -}}
{{ .Chart.Name }}-migrations
{{- end }}

{{- define "archive.selectorLabels" -}}
app.kubernetes.io/name: {{ include "archive.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "archive.labels" -}}
{{ include "archive.selectorLabels" . }}
app.kubernetes.io/version: {{ .Values.image.tag | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end }}

{{- define "archive.image" -}}
{{ .Values.image.repository }}:{{ required "image.tag is required: the commit of the image to deploy" .Values.image.tag }}
{{- end }}

{{/*
Vault agent annotations: each key becomes a file of /run/secrets, rendered once before the start, with no sidecar.
Arguments: role, path, keys.
*/}}
{{- define "archive.vaultAnnotations" -}}
vault.hashicorp.com/agent-inject: "true"
vault.hashicorp.com/agent-pre-populate-only: "true"
vault.hashicorp.com/role: {{ .role }}
vault.hashicorp.com/secret-volume-path: /run/secrets
{{- range .keys }}
vault.hashicorp.com/agent-inject-secret-{{ . }}: {{ $.path }}
vault.hashicorp.com/agent-inject-template-{{ . }}: {{ printf "{{- with secret %q -}}{{ .Data.data.%s }}{{- end -}}" $.path . | quote }}
{{- end }}
{{- end }}

{{/*
The same restrictions for every container of the chart: non-root, no privilege, read-only root file system.
*/}}
{{- define "archive.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: 65532
runAsGroup: 65532
fsGroup: 65532
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{- define "archive.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: [ALL]
{{- end }}
