# UniHub Workshop - Notification Specs

## 1. Get Notification Status

Defines organizer access to notification delivery status for a registration.

| Field               | Details                                                                                                                                                                                                                                                                                                                         |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Use Case Name       | Get Notification Status                                                                                                                                                                                                                                                                                                         |
| Description         | Returns delivery history for notifications tied to a registration.                                                                                                                                                                                                                                                              |
| Actors              | Organizer                                                                                                                                                                                                                                                                                                                       |
| Main Flow           | 1. Validate `registration_id` format.<br>2. Resolve organizer identity from Security Context.<br>3. Enforce ownership by matching `organizer_id` to the workshop; mismatch returns 403.<br>4. Query `NOTIFICATION_LOGS` for the registration_id.<br>5. Return status history with timestamps (QUEUED, SENT, DELIVERED, FAILED). |
| Error Scenarios     | - Registration not found returns 404.<br>- Ownership violation returns 403.<br>- Unauthorized access returns 401.                                                                                                                                                                                                               |
| Constraints         | - Access is limited to the workshop owner.<br>- Logs are read-only and immutable.                                                                                                                                                                                                                                               |
| Acceptance Criteria | - Status history is returned for valid registrations.<br>- Non-owners cannot access logs.                                                                                                                                                                                                                                       |

## 2. Outbox Relay Worker

Defines the relay that publishes notification events to RabbitMQ.

| Field               | Details                                                                                                                                                                                                                                                                              |
| :------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Outbox Relay Worker                                                                                                                                                                                                                                                                  |
| Description         | Publishes notification-related outbox events to RabbitMQ.                                                                                                                                                                                                                            |
| Actors              | Internal System                                                                                                                                                                                                                                                                      |
| Main Flow           | 1. Poll `OUTBOX_EVENTS` for notification events (for example, `WorkshopRegistered`, `RegistrationCanceled`).<br>2. Lock eligible rows for processing.<br>3. Publish payloads to the appropriate RabbitMQ exchanges.<br>4. Mark outbox events as `PROCESSED` in the same transaction. |
| Error Scenarios     | - RabbitMQ unavailable returns retryable failure.<br>- Database unavailable returns 503 (internal error).                                                                                                                                                                            |
| Constraints         | - Each outbox event must be processed at least once.<br>- Publishing and state update must be transactional.                                                                                                                                                                         |
| Acceptance Criteria | - Notification events are published to RabbitMQ.<br>- Outbox entries are marked `PROCESSED`.                                                                                                                                                                                         |

## 3. Transactional Email Consumer

Defines the 1-to-1 email consumer for confirmations and QR delivery.

| Field               | Details                                                                                                                                                                                                                                                                                                                             |
| :------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Transactional Email Consumer                                                                                                                                                                                                                                                                                                        |
| Description         | Sends individual transactional emails using templates and provider APIs.                                                                                                                                                                                                                                                            |
| Actors              | Internal System                                                                                                                                                                                                                                                                                                                     |
| Main Flow           | 1. Consume a notification message from RabbitMQ.<br>2. Fetch template via Template Engine (Table 5).<br>3. Populate dynamic fields (student name, workshop title, QR URL).<br>4. Attach QR code when applicable.<br>5. Call the external Email Provider API.<br>6. Record result in `NOTIFICATION_LOGS` with status and timestamps. |
| Error Scenarios     | - Template not found returns failed status.<br>- Provider API error returns retryable failure.                                                                                                                                                                                                                                      |
| Constraints         | - Template rendering must be deterministic.<br>- Notification logs must capture status transitions.                                                                                                                                                                                                                                 |
| Acceptance Criteria | - Successful sends are logged as SENT/DELIVERED.<br>- Failures are captured with error details.                                                                                                                                                                                                                                     |

## 4. Bulk Notification Consumer

Defines broadcast handling for reschedules and cancellations.

| Field               | Details                                                                                                                                                                                                                            |
| :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Bulk Notification Consumer                                                                                                                                                                                                         |
| Description         | Processes broadcast notifications and queues individual email jobs.                                                                                                                                                                |
| Actors              | Internal System                                                                                                                                                                                                                    |
| Main Flow           | 1. Consume a broadcast message from RabbitMQ.<br>2. Query all `CONFIRMED` or `PAID` attendees for the workshop.<br>3. Enqueue individual notification jobs per attendee.<br>4. Apply provider rate-limiting and batching controls. |
| Error Scenarios     | - Workshop not found returns failed status.<br>- Provider rate limit exceeded returns retryable failure.                                                                                                                           |
| Constraints         | - Bulk sends must respect provider rate limits.<br>- Each attendee must receive an individual notification job.                                                                                                                    |
| Acceptance Criteria | - Broadcast events result in per-attendee jobs.<br>- Rate limits are honored without message loss.                                                                                                                                 |

## 5. Notification Template Engine

Defines template selection and personalization for email content.

| Field               | Details                                                                                                                                                                                             |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Notification Template Engine                                                                                                                                                                        |
| Description         | Renders HTML templates with dynamic data for notifications.                                                                                                                                         |
| Actors              | Internal System                                                                                                                                                                                     |
| Main Flow           | 1. Select template based on event type.<br>2. Load template content from storage.<br>3. Map dynamic data into placeholders (student name, workshop title, QR URL).<br>4. Return rendered HTML body. |
| Error Scenarios     | - Template missing returns internal error.<br>- Rendering failure returns internal error.                                                                                                           |
| Constraints         | - Templates must be versioned and backward compatible.<br>- Rendering must be deterministic for the same inputs.                                                                                    |
| Acceptance Criteria | - Rendered output contains correct dynamic fields.<br>- Template selection is consistent per event type.                                                                                            |

## 6. Fallback, Retry & DLQ Strategy

Defines retry logic and dead-letter handling for failed notifications.

| Field               | Details                                                                                                                                                                                                                                                    |
| :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Fallback, Retry & DLQ Strategy                                                                                                                                                                                                                             |
| Description         | Retries failed notifications and routes exhausted retries to DLQ.                                                                                                                                                                                          |
| Actors              | Internal System                                                                                                                                                                                                                                            |
| Main Flow           | 1. Detect provider failures (timeouts or 5xx).<br>2. Retry delivery with exponential backoff and increment retry count.<br>3. If retries exceed the maximum threshold, move to DLQ.<br>4. Mark notification as `PERMANENT_FAILURE` in `NOTIFICATION_LOGS`. |
| Error Scenarios     | - Retry enqueue fails returns internal error.<br>- DLQ unavailable returns internal error.                                                                                                                                                                 |
| Constraints         | - Exponential backoff is required for retries.<br>- DLQ routing occurs after max retries are exceeded.                                                                                                                                                     |
| Acceptance Criteria | - Transient failures are retried with backoff.<br>- Exhausted retries are routed to DLQ and logged.                                                                                                                                                        |

## 7. System Alert Consumer

Defines system-level alert handling for administrative summaries.

| Field               | Details                                                                                                                                                                                                                                                                        |
| :------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | System Alert Consumer                                                                                                                                                                                                                                                          |
| Description         | Consumes system-level alerts (for example, `CsvIngestionCompleted`) and sends summary emails to Admins.                                                                                                                                                                        |
| Actors              | Internal System                                                                                                                                                                                                                                                                |
| Main Flow           | 1. Consume `CsvIngestionCompleted` message from RabbitMQ.<br>2. Fetch the alert email template via Template Engine (Table 5).<br>3. Populate dynamic fields (batch ID, success count, error count).<br>4. Send email to configured Admin addresses.<br>5. Log delivery status. |
| Error Scenarios     | - Template missing returns internal error.<br>- Provider API error returns retryable failure.                                                                                                                                                                                  |
| Constraints         | - Must use resilient delivery mechanisms (Retry/DLQ).                                                                                                                                                                                                                          |
| Acceptance Criteria | - Admin summary emails are delivered or retried per policy.<br>- Delivery outcomes are recorded in logs.                                                                                                                                                                       |
