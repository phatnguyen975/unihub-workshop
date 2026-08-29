# Spec: Nightly Student CSV Ingestion

> **Status:** APPROVED  
> **Domain:** 9 — Student Data Synchronization  
> **Owner/area:** `apps/workers` — `batch_ingestion`  
> **Last revised:** 2026-08-30 — first version

## 1. Overview

This feature synchronizes authoritative student identity data from nightly CSV exports placed by the legacy Student Management System into object storage, so student accounts can be verified during registration without a legacy-system API. It is owned by the worker-side `batch_ingestion` area and must safely validate, process, quarantine, archive, and report each eligible export without exposing a user-facing upload or API surface.

## 2. Actors & Preconditions

| Actor                                             | Precondition                                                                                                                                          |
| :------------------------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Legacy Student Management System                  | It has placed a CSV export in the configured organization-scoped inbox location in object storage.                                                    |
| Scheduled ingestion process                       | The configured nightly schedule has occurred and the worker can access its configured object-storage, database, and message-broker dependencies.      |
| `ORGANIZER` users in the source-file organization | Receive a broadcast in-app and email notification after an ingestion attempt finishes or is rejected; this feature does not provide a direct trigger. |

## 3. Functional Requirements

### File discovery and validation

- **FR-01:** The system SHALL run the ingestion process nightly according to its configured schedule.
- **FR-02:** The system SHALL consider only CSV files in the configured organization-scoped inbox location that have not been archived.
- **FR-03:** The system SHALL process each eligible file for an organization in ascending object-creation-time order.
- **FR-04:** The system SHALL derive the target organization from the organization-scoped inbox location when it begins an eligible file.
- **FR-05:** The system SHALL reject an eligible file before processing any student row when its size exceeds 100 MB.
- **FR-06:** The system SHALL reject an eligible file before processing any student row when its header omits any of `student_number`, `email`, `full_name`, or `faculty`, or contains either `organization_id` or `status`.
- **FR-07:** The system SHALL move a file that fails file-level validation from its inbox location to the organization-scoped rejected location before publishing an organization-scoped failure notification event.

### Row processing and synchronization

- **FR-08:** The system SHALL process a valid file without loading its complete contents into memory.
- **FR-09:** The system SHALL quarantine a row when any required field is blank after trimming surrounding whitespace.
- **FR-10:** The system SHALL quarantine a row when `email` is not a valid email address.
- **FR-11:** The system SHALL quarantine a row when `student_number` exceeds 50 characters or `full_name` or `faculty` exceeds 200 characters.
- **FR-12:** The system SHALL quarantine a row when its `faculty` value does not match the trusted expected faculty configured for the organization derived from the source-file location.
- **FR-13:** The system SHALL keep the first occurrence of a duplicate student row within a processing chunk and quarantine every later occurrence from that chunk with a duplicate reason.
- **FR-14:** The system SHALL create or update a valid student-directory record using its organization together with the student number and email as the documented uniqueness keys.
- **FR-15:** The system SHALL set the status of every created student-directory record to `ACTIVE` when the valid source row is processed.
- **FR-16:** The system SHALL record the source file key and import time for every created or updated student-directory record.
- **FR-17:** The system SHALL not delete, deactivate, or otherwise change a student-directory record solely because it is absent from a processed file.
- **FR-18:** The system SHALL persist progress at processing-chunk boundaries so an interrupted execution can continue from the next uncommitted chunk without reapplying committed chunks.

### Completion and reporting

- **FR-19:** The system SHALL append every quarantined row and its rejection reason to a reject artifact for that ingestion attempt.
- **FR-20:** The system SHALL upload a reject artifact to object storage only when the ingestion attempt quarantined one or more rows.
- **FR-21:** The system SHALL assign every uploaded reject artifact an object key that is unique to its ingestion attempt.
- **FR-22:** The system SHALL archive a successfully completed source file after all of its rows have been handled and any required reject artifact has been uploaded.
- **FR-23:** The system SHALL publish an ingestion summary after a successful ingestion attempt, including the source file key, outcome metrics, and the reject artifact key when one exists.
- **FR-24:** The system SHALL publish an ingestion summary with `rejectCount` equal to zero and no reject artifact key when a successful ingestion attempt has no quarantined rows.
- **FR-25:** The system SHALL publish an organization-scoped completion notification event after a successful ingestion attempt, targeting every `ORGANIZER` user in the source-file organization.

## 4. Business Rules

- **BR-01:** A source CSV MUST contain `student_number`, `email`, `full_name`, and `faculty`; it MUST NOT contain `organization_id` or `status`.
- **BR-02:** The organization is derived from the configured organization-scoped inbox location, never from a source-row value.
- **BR-03:** Valid source rows are bounded by the `student_directory` schema: student number up to 50 characters, email as a valid email address, full name up to 200 characters, and faculty up to 200 characters.
- **BR-04:** The student directory is a verification source, not an authentication source.
- **BR-05:** Duplicate rows within one processing chunk preserve the first occurrence; later occurrences are quarantined and do not replace it.
- **BR-06:** A bad row is quarantined and MUST NOT abort the rest of its valid file.
- **BR-07:** A file-level validation failure aborts that file before any row is processed, moves it to the organization-scoped rejected location, and publishes a failure notification event for every `ORGANIZER` user in that organization.
- **BR-08:** An archived source file is not eligible for later ingestion; the archive is the completion marker that prevents it from being selected again.
- **BR-09:** A reject artifact is created only when at least one row was quarantined; a clean run is represented by zero rejects and no artifact key.
- **BR-10:** The synchronization is additive and updating only: absence from an export is not evidence that a student should be removed or made inactive.
- **BR-11:** The organization-scoped inbox contains eligible source files, the rejected location contains files that failed file-level validation, and the archive contains successfully completed source files.
- **BR-12:** An organization-scoped source file contains rows for that organization's configured faculty only; a row whose `faculty` value differs is invalid.
- **BR-13:** The ingestion worker publishes notification events but does not send in-app notifications or emails directly; the downstream notifications feature fans out each event to every `ORGANIZER` user in the target organization through both channels.

## 5. State Transitions

N/A — this feature does not introduce or modify an application-owned stateful entity. Execution and restart metadata are maintained by the framework-managed batch repository; its internal lifecycle is outside this feature's business contract.

## 6. Non-Functional Requirements

- **Performance:** File processing MUST use a bounded-memory approach; processing a file up to 100 MB MUST NOT require retaining the complete file in memory.
- **Concurrency:** For a given organization, eligible files MUST be selected in ascending object-creation-time order. Database writes MUST preserve the documented uniqueness of organization plus student number and organization plus email.
- **Idempotency:** Persisted chunk progress and atomic create-or-update behavior MUST prevent an interrupted execution from duplicating already committed student data when it resumes.
- **Rate-limiting:** N/A — this feature has no client-facing network endpoint.
- **Security:** The worker MUST derive tenant scope and the expected faculty only from trusted configuration and the object-storage location; it MUST NOT accept tenant scope, student status, or a mismatched faculty value from the CSV.
- **Reliability / Failure modes:** A file-level validation failure MUST cause no partial student-data update for that file, move the source file to the rejected location, and publish a failure notification event. A malformed row MUST be quarantined while remaining rows continue to be processed. A source file MUST be archived only after successful completion.
- **Observability:** Each completed ingestion attempt MUST publish outcome metrics sufficient to identify the source file, organization, processed-row count, quarantined-row count, and reject artifact key when present. File-level failure and completion notification events MUST identify the target organization so the downstream notifications feature can broadcast in-app and email notifications to all of its `ORGANIZER` users.

## 7. Acceptance Criteria

### Scenario 1: Processes unarchived organization files in order

```gherkin
Given an organization inbox contains two eligible CSV files created at different times
And neither file has been archived
When the nightly ingestion process runs
Then it processes the earlier file before the later file
And it derives the same organization's scope from each file's inbox location
```

_(Traces to: FR-01, FR-02, FR-03, FR-04)_

### Scenario 2: Rejects an oversized file before data changes

```gherkin
Given an eligible CSV file is larger than 100 MB
When the ingestion process validates the file
Then it rejects the file before processing any student row
And it moves the file to the organization-scoped rejected location
And it publishes a failure notification event for every ORGANIZER user in that organization
And it does not create or update a student-directory record from that file
```

_(Traces to: FR-05, FR-07)_

### Scenario 3: Rejects an invalid header before data changes

```gherkin
Given an eligible CSV file is missing a required header column or contains organization_id or status
When the ingestion process validates the file
Then it rejects the file before processing any student row
And it moves the file to the organization-scoped rejected location
And it publishes a failure notification event for every ORGANIZER user in that organization
And it does not create or update a student-directory record from that file
```

_(Traces to: FR-06, FR-07)_

### Scenario 4: Updates existing student data and creates new student data

```gherkin
Given a valid CSV row matches an existing student-directory record for the same organization
And a different valid CSV row has no matching student-directory record
When the ingestion process handles the rows
Then it updates the matching record with the valid source data and source-file lineage
And it creates a new ACTIVE record for the unmatched row
And it does not rewrite unrelated student-directory records
```

_(Traces to: FR-14, FR-15, FR-16)_

### Scenario 5: Does not remove a student absent from an export

```gherkin
Given an organization has an ACTIVE student-directory record
And a valid processed CSV file for that organization does not contain that student
When the ingestion attempt completes
Then the existing student-directory record remains unchanged because of that absence
```

_(Traces to: FR-17)_

### Scenario 6: Quarantines malformed rows while continuing valid rows

```gherkin
Given a valid CSV file contains one row with an invalid email and one valid row
When the ingestion process handles the rows
Then it quarantines the invalid row with its rejection reason
And it creates or updates the record for the valid row
And the invalid row does not abort the file
```

_(Traces to: FR-09, FR-10, FR-11, FR-14, FR-19)_

### Scenario 7: Quarantines a row for a different faculty

```gherkin
Given a CSV file is in the inbox for the Information Technology organization
And the organization is configured with Information Technology as its expected faculty
And the file contains a row whose faculty is Chemistry
When the ingestion process handles the row
Then it quarantines the row with a faculty-mismatch reason
And it does not create or update a student-directory record from that row
```

_(Traces to: FR-12)_

### Scenario 8: Resolves duplicate rows within a chunk deterministically

```gherkin
Given a valid CSV file contains two duplicate rows in the same processing chunk
When the ingestion process handles the chunk
Then it processes the first occurrence
And it quarantines the later occurrence with a duplicate reason
And it does not allow the later occurrence to replace the first
```

_(Traces to: FR-13, FR-19)_

### Scenario 9: Resumes after an interruption without duplicating committed data

```gherkin
Given an ingestion attempt has committed one or more chunks
And execution is interrupted before the next chunk commits
When the ingestion attempt resumes
Then it continues from the next uncommitted chunk
And it does not reapply the previously committed chunks
```

_(Traces to: FR-18)_

### Scenario 10: Archives a completed file and reports a reject artifact

```gherkin
Given a valid CSV file has one or more quarantined rows
When all rows have been handled successfully
Then the system uploads one reject artifact containing the quarantined rows and their reasons
And the reject artifact key is unique to that ingestion attempt
And it archives the source file
And it publishes a summary containing the source file key, outcome metrics, and reject artifact key
```

_(Traces to: FR-19, FR-20, FR-21, FR-22, FR-23)_

### Scenario 11: Completes a clean file without an empty reject artifact

```gherkin
Given a valid CSV file has no quarantined rows
When all rows have been handled successfully
Then the system archives the source file
And it publishes a summary with rejectCount equal to zero
And the summary has no reject artifact key
And it does not upload an empty reject artifact
```

_(Traces to: FR-20, FR-22, FR-24)_

### Scenario 12: Broadcasts a successful ingestion result to organization organizers

```gherkin
Given an organization has two ORGANIZER users
And an ingestion attempt for that organization completes successfully
When the system publishes the completion notification event
Then the event targets both ORGANIZER users through the organization's notification audience
And the downstream notifications feature can deliver an in-app notification and email to each organizer
```

_(Traces to: FR-25)_

## 8. Out of Scope

- A user interface, REST endpoint, or ad-hoc manual action for uploading student CSV files.
- Receiving CSV files directly from the legacy Student Management System through an API or a bidirectional synchronization protocol.
- Deleting, deactivating, or reconciling student-directory records that are absent from an otherwise valid export.
- Providing organizers a list of past executions or a pre-signed download URL for reject artifacts; this belongs to `ingestion-reject-report`.
- Authentication, login, or account creation behavior based on the imported student data.
- Correcting, repairing, or re-exporting invalid source rows in the legacy Student Management System.

## 9. Linked References

- `docs/overview.md` — §3 Functional Objectives; §5 Student Data Synchronization; §7 Unidirectional Legacy Integration.
- `docs/feature-catalog.md` — §9 Student Data Synchronization; §9.1 `nightly-student-csv-ingestion`; §9.2 `ingestion-reject-report` boundary.
- `docs/system-design.md` — §4 Nightly Legacy CSV Ingestion Flow.
- `docs/database-schema.md` — `student_directory`; `batch_*` (Framework-Managed); §4 Replication Lag & Read-After-Write Consistency.
- `docs/project-structure.md` — Background Workers; Internal Module Communication.
- `docs/adrs.md` — ADR 011: Object Storage; ADR 017: Message Broker; ADR 025: Batch Processing Strategy.
- `docs/api-standards.md` — applicability assessment: no network-facing interface is introduced by this feature.
