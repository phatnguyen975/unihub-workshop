# Mobile Persistence Design

These tables live only in the React Native mobile app's local SQLite database and are not part of the PostgreSQL backend.

## Table: `mobile_assigned_workshops`

**Table Description & UX Context:** Caches the workshops assigned to the STAFF user so they can select an event to scan for while offline.

**Schema Definition Table:**

| Column Name | Data Type | Constraints  | Description/UX Logic                  |
| :---------- | :-------- | :----------- | :------------------------------------ |
| id          | TEXT      | PK, NOT NULL | Workshop identifier (UUIDv4 as text). |
| title       | TEXT      | NOT NULL     | Workshop title for offline selection. |
| starts_at   | TEXT      | NOT NULL     | Start time in ISO8601 format.         |
| location    | TEXT      | NOT NULL     | Location display string.              |

## Table: `mobile_public_keys`

**Table Description & UX Context:** Stores the backend's asymmetric public keys to verify student QR codes offline (Zero-Trust).

**Schema Definition Table:**

| Column Name    | Data Type | Constraints  | Description/UX Logic                  |
| :------------- | :-------- | :----------- | :------------------------------------ |
| key_id         | TEXT      | PK, NOT NULL | Key identifier used for rotation.     |
| public_key_pem | TEXT      | NOT NULL     | PEM-encoded public key material.      |
| issued_at      | TEXT      | NOT NULL     | Issuance timestamp in ISO8601 format. |

## Table: `mobile_sync_queue`

**Table Description & UX Context:** Offline queue for attendance scans; records are stored immediately upon scan and synced in bulk when connectivity returns.

**Schema Definition Table:**

| Column Name     | Data Type | Constraints  | Description/UX Logic                                                          |
| :-------------- | :-------- | :----------- | :---------------------------------------------------------------------------- |
| sync_id         | TEXT      | PK, NOT NULL | UUIDv4 generated on device.                                                   |
| workshop_id     | TEXT      | NOT NULL     | Workshop identifier from QR context.                                          |
| registration_id | TEXT      | NOT NULL     | Registration identifier extracted from QR.                                    |
| scanned_at      | TEXT      | NOT NULL     | Scan timestamp in ISO8601 format.                                             |
| sync_status     | TEXT      | NOT NULL     | Sync state: `PENDING`, `SUCCESS`, `ALREADY_USED`, `INVALID_TICKET`, `FAILED`. |
