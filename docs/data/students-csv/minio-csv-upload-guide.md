# Upload a Sample Student CSV to Local MinIO

This guide simulates the legacy Student Management System dropping a nightly CSV export into the local MinIO instance. It is a development-only manual operation; the UniHub web application does not provide a CSV upload interface.

## Prerequisites

- Run the commands from the repository root unless a step says otherwise.
- Docker and Docker Compose are installed and running.
- You have the MinIO credentials configured for this local environment. Use the credentials from your local infrastructure configuration; do not add credentials to this document or commit them.
- Choose an existing organization UUID as `<organization-id>`. The upload location is tenant-scoped, so this value identifies the organization that owns the student records.

## 1. Start MinIO and create the required bucket

```bash
cd infra
docker compose up -d minio minio-init
docker compose ps minio minio-init
cd ..
```

Wait until `minio` is running and `minio-init` has completed successfully. The initialization container creates the `unihub-csv-drops` bucket.

## 2. Choose the object key and file name

The current architecture does not mandate a file-name format; object creation time determines processing order. Use the following development convention so that a file's intended export time is clear to operators. The faculty is deliberately not repeated in the file name because the organization-scoped prefix determines it.

```text
unihub-csv-drops/inbox/<organization-id>/students-YYYY-MM-DDTHHMMSSZ.csv
```

Example:

```text
unihub-csv-drops/inbox/00000000-0000-0000-0000-000000000001/students-2026-08-30T010000Z.csv
```

The object-storage lifecycle uses three organization-scoped prefixes:

```text
inbox/<organization-id>/     # New source files eligible for ingestion
archive/<organization-id>/   # Successfully completed source files
rejected/<organization-id>/  # Source files that failed file-level validation
```

Upload development fixtures only to `inbox/<organization-id>/`. Each inbox prefix belongs to one faculty organization, and every CSV row must use that organization's configured faculty value. The ingestion worker moves files to `archive/` or `rejected/`; do not move them manually when testing the worker.

## 3. Upload with the MinIO Console

1. Open `http://localhost:9001` in a browser.
2. Sign in using the MinIO credentials from your local infrastructure configuration.
3. Open the `unihub-csv-drops` bucket.
4. Create the prefix path `inbox/<organization-id>/` if it does not already exist.
5. Upload `docs/data/students-csv/students-2026-08-30T010000Z.csv` into that prefix.
6. Keep the existing file name when prompted.
7. Confirm that the resulting object key exactly matches the convention in Step 2.
