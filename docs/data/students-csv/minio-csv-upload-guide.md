# Upload a Sample Student CSV to Local MinIO

This guide simulates the legacy Student Management System dropping a nightly CSV export into the local MinIO instance. It is a development-only manual operation; the UniHub web application does not provide a CSV upload interface.

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
unihub-csv-drops/inbox/information-technology/students-2026-08-31T010000Z.csv
```

The object-storage lifecycle uses three organization-scoped prefixes:

```text
inbox/<organization-slug>/                  # New source files eligible for ingestion
archive/<organization-slug>/                # Successfully completed source files
rejected/<organization-slug>/source-files/  # Source files that failed file-level validation
rejected/<organization-slug>/row-reports/   # Reports of invalid rows from completed files
```

Upload development fixtures only to `inbox/<organization-slug>/`. Each inbox prefix belongs to one faculty organization. The worker resolves the prefix slug to the seeded organization UUID and name, requires that name in `INGESTION_EXPECTED_FACULTIES`, and validates every CSV row against that resolved faculty value. Ensure the upload has completed before the scheduled ingestion run starts. The ingestion worker moves invalid source files to `rejected/<organization-slug>/source-files/` and uploads row reports to `rejected/<organization-slug>/row-reports/`; do not move either manually when testing the worker.

## 3. Upload with the MinIO Console

1. Open `http://localhost:9001` in a browser for MinIO Console.
2. Sign in using the MinIO credentials from your local infrastructure configuration.
3. Open the `unihub-csv-drops` bucket.
4. Create the prefix path `inbox/<organization-slug>/` if it does not already exist.
5. Upload `docs/data/students-csv/students-2026-08-31T010000Z.csv` into that prefix.
6. Keep the existing file name when prompted.
7. Wait for the upload to finish and confirm that the resulting object key exactly matches the convention in Step 2 before running the ingestion worker.
