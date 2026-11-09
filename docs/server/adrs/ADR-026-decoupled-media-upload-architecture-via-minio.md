# ADR-026: Decoupled Media Upload Architecture via MinIO

> **Scope:** server

- **Context:** Workshop drafting and user profile management require storing media assets (workshop cover images, speaker portraits, user avatars). Naive implementations that embed binary data (Base64) into main JSON payloads inflate network overhead, trigger database bloat, and increase transactional latency. Furthermore, tightly coupling file upload to draft creation creates race conditions during multi-step authoring, while failing to delete previous assets leaves orphaned files in storage buckets.
- **Decision:** Use MinIO object storage partitioned by domain prefix in the public read bucket `unihub-media`, adopting a two-tier strategy based on entity lifecycle:
  1. **Decoupled 2-Step Upload for Complex Forms (Workshops):**
     - **Step 1:** Frontend uploads media via a dedicated endpoint (`POST /api/v1/admin/workshops/media/upload` with multipart `file` and `type: COVER_IMAGE | SPEAKER_AVATAR`). The backend validates size (<= 5MB) and MIME type (`image/jpeg`, `image/jpg`, `image/png`, `image/webp`; allowed extensions: `.jpg`, `.jpeg`, `.png`, `.webp`), generates a unique UUID-based key, streams to MinIO, and returns the public URL. No database record is modified.
     - **Step 2:** The frontend immediately renders an image preview and includes the returned URL in the subsequent Save Draft payload (`POST /api/v1/admin/workshops/drafts`), persisting the URL atomically in PostgreSQL.
  2. **1-Step Direct Upload for Self-Contained Profile Avatars (IAM):**
     - Users update their avatar via a dedicated endpoint (`POST /api/v1/users/me/avatar`). The backend streams the file to `users/avatars/{uuid}.{ext}`, atomically updates `users.avatar_url` in PostgreSQL, deletes the user's previous avatar from MinIO (if present), and returns the updated profile snapshot. `PATCH /api/v1/users/me` strictly manages mutable text attributes (`fullName`, `phoneNumber`) with `avatarUrl` removed from its request body.
  3. **MinIO Key Partitioning in `unihub-media`:**
     - `workshops/covers/{uuid}.{ext}`
     - `speakers/avatars/{uuid}.{ext}`
     - `users/avatars/{uuid}.{ext}`
  4. **Media Lifecycle & Cleanup Guarantee:**
     - On draft update (`POST /api/v1/admin/workshops/drafts`): replaced or removed cover image and speaker avatar objects stored in MinIO are deleted.
     - On draft deletion (`DELETE /api/v1/admin/workshops/{id}`): all associated cover and speaker media in MinIO are permanently deleted.
  5. **Shared Kernel Storage Abstraction:**
     - All MinIO client operations and validation utilities reside in `shared/storage` (`MediaStoragePort` and `MinioMediaStorageAdapter`). `workshops` and `iam` depend only on `shared`, maintaining strict Spring Modulith boundaries.
- **Rationale:** Decoupled uploads keep JSON payloads lightweight, eliminate database lock contention during slow file transfers, and allow immediate UI preview during form editing. Dedicated avatar endpoints eliminate stale URL overrides, and bucket cleanup prevents storage leaks.
- **Alternatives Considered:**
  - _Direct Presigned S3 URLs:_ Rejected because local Docker MinIO signature verification and endpoint host translation between host and containers introduce operational fragility for developer environments.
  - _Bundled Multipart in Save Draft:_ Rejected because re-uploading large binaries on every progressive draft save or auto-save creates unnecessary I/O and complicates optimistic locking (`@Version`).
- **Consequences:** Backend acts as a streaming proxy to MinIO; requires MinIO connectivity in all application runtime environments.
