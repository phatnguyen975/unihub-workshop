#!/usr/bin/env sh
set -eu

: "${AWS_ACCESS_KEY_ID:?}"
: "${AWS_SECRET_ACCESS_KEY:?}"
: "${AWS_S3_ENDPOINT:?}"

max_attempts=30
attempt=1

until mc alias set local "$AWS_S3_ENDPOINT" "$AWS_ACCESS_KEY_ID" "$AWS_SECRET_ACCESS_KEY" >/dev/null 2>&1 \
  && mc admin info local >/dev/null 2>&1; do
  if [ "$attempt" -ge "$max_attempts" ]; then
    echo "MinIO not ready after ${max_attempts} attempts."
    exit 1
  fi
  echo "Waiting for MinIO to be ready (${attempt}/${max_attempts})..."
  attempt=$((attempt + 1))
  sleep 2
done

mc mb --ignore-existing local/unihub-brochures
mc mb --ignore-existing local/unihub-csv-drops

mc anonymous set download local/unihub-brochures

echo "MinIO buckets ready."
touch /tmp/minio-init.done
