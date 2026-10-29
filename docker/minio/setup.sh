#!/bin/sh
# Local provisioning of the object storage, as in production (docs/security/permissions.md): the bucket, private, and
# an account for the application that may only put, get and delete the objects under documents/. Runs at each
# "docker compose up" and changes nothing when everything already exists.
set -eu

until mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do sleep 1; done

mc mb --ignore-existing "local/$ARCHIVE_S3_BUCKET"
mc anonymous set none "local/$ARCHIVE_S3_BUCKET"

cat > /tmp/archive-app.json <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::$ARCHIVE_S3_BUCKET/documents/*"]
    }
  ]
}
JSON
mc admin policy create local archive-app /tmp/archive-app.json

mc admin user add local "$ARCHIVE_S3_ACCESS_KEY" "$ARCHIVE_S3_SECRET_KEY"
if ! mc admin user info local "$ARCHIVE_S3_ACCESS_KEY" | grep -q "archive-app"; then
  mc admin policy attach local archive-app --user "$ARCHIVE_S3_ACCESS_KEY"
fi
echo "Bucket $ARCHIVE_S3_BUCKET and account $ARCHIVE_S3_ACCESS_KEY ready"
