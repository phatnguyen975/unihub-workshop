# UniHub Workshop — HTTP Contract Registry

## Scope

This registry identifies the source and format for each HTTP interface scope. Read the selected source together with [HTTP Standards](standards.md), verify its approval status and applicable revision, and use it as the provider/consumer boundary. A draft remains a proposal regardless of its listed format.

## Interface Sources

| Feature                                        | Interface source                                                     | Format   |
| ---------------------------------------------- | -------------------------------------------------------------------- | -------- |
| `active-session-management`                    | [Contract](features/active-session-management.md)                    | Markdown |
| `ai-brochure-summarization`                    | [Contract](features/ai-brochure-summarization.md)                    | Markdown |
| `attendance-sync-reconciliation`               | [Contract](features/attendance-sync-reconciliation.md)               | Markdown |
| `authentication-and-session-management`        | [Contract](features/authentication-and-session-management.md)        | Markdown |
| `free-workshop-registration`                   | [Contract](features/free-workshop-registration.md)                   | Markdown |
| `in-app-notification-management`               | [Contract](features/in-app-notification-management.md)               | Markdown |
| `ingestion-reject-report`                      | [Contract](features/ingestion-reject-report.md)                      | Markdown |
| `live-attendance-monitoring`                   | [Contract](features/live-attendance-monitoring.md)                   | Markdown |
| `mobile-offline-checkin`                       | [Contract](features/mobile-offline-checkin.md)                       | Markdown |
| `organization-member-management`               | [Contract](features/organization-member-management.md)               | Markdown |
| `paid-workshop-registration`                   | [Contract](features/paid-workshop-registration.md)                   | Markdown |
| `password-reset`                               | [Contract](features/password-reset.md)                               | Markdown |
| `payment-history-and-status`                   | [Contract](features/payment-history-and-status.md)                   | Markdown |
| `public-key-distribution`                      | [Contract](features/public-key-distribution.md)                      | Markdown |
| `real-time-event-streaming-hub`                | [Contract](features/real-time-event-streaming-hub.md)                | Markdown |
| `registration-reconciliation-and-cancellation` | [Contract](features/registration-reconciliation-and-cancellation.md) | Markdown |
| `user-profile-management`                      | [Contract](features/user-profile-management.md)                      | Markdown |
| `user-registration-and-identity-verification`  | [Contract](features/user-registration-and-identity-verification.md)  | Markdown |
| `workshop-cancellation`                        | [Contract](features/workshop-cancellation.md)                        | Markdown |
| `workshop-catalog-browsing`                    | [Contract](features/workshop-catalog-browsing.md)                    | Markdown |
| `workshop-creation-and-draft-management`       | [Contract](features/workshop-creation-and-draft-management.md)       | Markdown |
| `workshop-publishing-and-update`               | [Contract](features/workshop-publishing-and-update.md)               | Markdown |
| `workshop-registration-monitoring-and-roster`  | [Contract](features/workshop-registration-monitoring-and-roster.md)  | Markdown |
| `workshop-rescheduling`                        | [Contract](features/workshop-rescheduling.md)                        | Markdown |
| `workshop-staff-assignment`                    | [Contract](features/workshop-staff-assignment.md)                    | Markdown |

## Contract Formats

Markdown feature documents and OpenAPI definitions use the canonical locations in [Contract Structure](../README.md#structure). The registry selects the authoritative source for the approved interface scope; applications do not infer authority from a format, directory or generated documentation endpoint.

OpenAPI authoring uses `contracts/http/openapi.yaml`, referenced operations in `paths/` and reusable definitions in `components/`. Supplemental semantics belong in `behavior/`. Format conversion requires schema/behavior parity, human approval and an updated registry entry for the affected scope.

## Change Control

Follow [Contract Workflow](../../.agents/workflows/contracts.md). Keep this index, selected interface sources and affected approved specifications consistent through the owning approval process. Implementation and integration follow their own server/client gates.
