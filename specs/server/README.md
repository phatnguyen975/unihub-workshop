# Server Specifications

## Scope

Each feature directory contains the behavior specification and task plan for backend or worker implementation. Feature IDs, readiness and delivery status are defined in Server Feature Delivery.

## Structure

```text
server/
├── README.md
└── <feature-id>/
    ├── SPEC.md
    ├── tasks.md
    ├── improvement-proposal.md    # When an approved artifact needs an amendment
    └── review-notes.md            # Review findings and human disposition
```

## Artifacts

| Artifact | Responsibility |
| --- | --- |
| SPEC.md | Actors, requirements, business rules, transitions and acceptance criteria |
| tasks.md | Ordered vertical slices, dependencies, file scope and observable acceptance |
| improvement-proposal.md | Proposed artifact changes and the two approval gates |
| review-notes.md | Severity-classified findings, human confirmation and resolution evidence |

## References

[Server Feature Delivery](../../docs/server/feature-delivery.md) · [Product Features](../../docs/product-features.md) · [HTTP Contract Registry](../../contracts/http/README.md) · [Server Templates](../../.agents/templates/server/README.md) · [Shared Templates](../../.agents/templates/shared/README.md)

## Workflow

Follow [Server Workflow](../../.agents/workflows/server.md): server-spec → server-plan → server-build → server-review, with server-fix for human-confirmed findings. Each stage follows its required human invocation and approval. Spec/task revisions, requirement IDs, checkpoints and approval records belong to the feature history. Contracts are linked from the public registry and participate in the same applicable approval gates.

## Artifact Index

| Feature ID | Specification | Task Plan | HTTP Contract |
| --- | --- | --- | --- |
| [active-session-management](active-session-management/) | [Specification](active-session-management/SPEC.md) | [Task Plan](active-session-management/tasks.md) | [HTTP Contract](../../contracts/http/features/active-session-management.md) |
| [ai-brochure-summarization](ai-brochure-summarization/) | [Specification](ai-brochure-summarization/SPEC.md) | [Task Plan](ai-brochure-summarization/tasks.md) | [HTTP Contract](../../contracts/http/features/ai-brochure-summarization.md) |
| [attendance-sync-reconciliation](attendance-sync-reconciliation/) | [Specification](attendance-sync-reconciliation/SPEC.md) | [Task Plan](attendance-sync-reconciliation/tasks.md) | [HTTP Contract](../../contracts/http/features/attendance-sync-reconciliation.md) |
| [authentication-and-session-management](authentication-and-session-management/) | [Specification](authentication-and-session-management/SPEC.md) | [Task Plan](authentication-and-session-management/tasks.md) | [HTTP Contract](../../contracts/http/features/authentication-and-session-management.md) |
| [distributed-rate-limiting](distributed-rate-limiting/) | [Specification](distributed-rate-limiting/SPEC.md) | [Task Plan](distributed-rate-limiting/tasks.md) | — |
| [free-workshop-registration](free-workshop-registration/) | [Specification](free-workshop-registration/SPEC.md) | [Task Plan](free-workshop-registration/tasks.md) | [HTTP Contract](../../contracts/http/features/free-workshop-registration.md) |
| [in-app-notification-management](in-app-notification-management/) | [Specification](in-app-notification-management/SPEC.md) | [Task Plan](in-app-notification-management/tasks.md) | [HTTP Contract](../../contracts/http/features/in-app-notification-management.md) |
| [infrastructure-bootstrap-and-seed-data](infrastructure-bootstrap-and-seed-data/) | [Specification](infrastructure-bootstrap-and-seed-data/SPEC.md) | [Task Plan](infrastructure-bootstrap-and-seed-data/tasks.md) | — |
| [ingestion-reject-report](ingestion-reject-report/) | [Specification](ingestion-reject-report/SPEC.md) | [Task Plan](ingestion-reject-report/tasks.md) | [HTTP Contract](../../contracts/http/features/ingestion-reject-report.md) |
| [live-attendance-monitoring](live-attendance-monitoring/) | [Specification](live-attendance-monitoring/SPEC.md) | [Task Plan](live-attendance-monitoring/tasks.md) | [HTTP Contract](../../contracts/http/features/live-attendance-monitoring.md) |
| [mobile-offline-checkin](mobile-offline-checkin/) | [Specification](mobile-offline-checkin/SPEC.md) | [Task Plan](mobile-offline-checkin/tasks.md) | [HTTP Contract](../../contracts/http/features/mobile-offline-checkin.md) |
| [nightly-student-csv-ingestion](nightly-student-csv-ingestion/) | [Specification](nightly-student-csv-ingestion/SPEC.md) | [Task Plan](nightly-student-csv-ingestion/tasks.md) | — |
| [notification-delivery-pipeline](notification-delivery-pipeline/) | [Specification](notification-delivery-pipeline/SPEC.md) | [Task Plan](notification-delivery-pipeline/tasks.md) | — |
| [observability-platform-setup](observability-platform-setup/) | [Specification](observability-platform-setup/SPEC.md) | [Task Plan](observability-platform-setup/tasks.md) | — |
| [organization-member-management](organization-member-management/) | [Specification](organization-member-management/SPEC.md) | [Task Plan](organization-member-management/tasks.md) | [HTTP Contract](../../contracts/http/features/organization-member-management.md) |
| [paid-workshop-registration](paid-workshop-registration/) | [Specification](paid-workshop-registration/SPEC.md) | [Task Plan](paid-workshop-registration/tasks.md) | [HTTP Contract](../../contracts/http/features/paid-workshop-registration.md) |
| [password-reset](password-reset/) | [Specification](password-reset/SPEC.md) | [Task Plan](password-reset/tasks.md) | [HTTP Contract](../../contracts/http/features/password-reset.md) |
| [payment-history-and-status](payment-history-and-status/) | [Specification](payment-history-and-status/SPEC.md) | [Task Plan](payment-history-and-status/tasks.md) | [HTTP Contract](../../contracts/http/features/payment-history-and-status.md) |
| [public-key-distribution](public-key-distribution/) | [Specification](public-key-distribution/SPEC.md) | [Task Plan](public-key-distribution/tasks.md) | [HTTP Contract](../../contracts/http/features/public-key-distribution.md) |
| [real-time-event-streaming-hub](real-time-event-streaming-hub/) | [Specification](real-time-event-streaming-hub/SPEC.md) | [Task Plan](real-time-event-streaming-hub/tasks.md) | [HTTP Contract](../../contracts/http/features/real-time-event-streaming-hub.md) |
| [registration-reconciliation-and-cancellation](registration-reconciliation-and-cancellation/) | [Specification](registration-reconciliation-and-cancellation/SPEC.md) | [Task Plan](registration-reconciliation-and-cancellation/tasks.md) | [HTTP Contract](../../contracts/http/features/registration-reconciliation-and-cancellation.md) |
| [resilience-platform-setup](resilience-platform-setup/) | [Specification](resilience-platform-setup/SPEC.md) | [Task Plan](resilience-platform-setup/tasks.md) | — |
| [user-profile-management](user-profile-management/) | [Specification](user-profile-management/SPEC.md) | [Task Plan](user-profile-management/tasks.md) | [HTTP Contract](../../contracts/http/features/user-profile-management.md) |
| [user-registration-and-identity-verification](user-registration-and-identity-verification/) | [Specification](user-registration-and-identity-verification/SPEC.md) | [Task Plan](user-registration-and-identity-verification/tasks.md) | [HTTP Contract](../../contracts/http/features/user-registration-and-identity-verification.md) |
| [workshop-cancellation](workshop-cancellation/) | [Specification](workshop-cancellation/SPEC.md) | [Task Plan](workshop-cancellation/tasks.md) | [HTTP Contract](../../contracts/http/features/workshop-cancellation.md) |
| [workshop-catalog-browsing](workshop-catalog-browsing/) | [Specification](workshop-catalog-browsing/SPEC.md) | [Task Plan](workshop-catalog-browsing/tasks.md) | [HTTP Contract](../../contracts/http/features/workshop-catalog-browsing.md) |
| [workshop-creation-and-draft-management](workshop-creation-and-draft-management/) | [Specification](workshop-creation-and-draft-management/SPEC.md) | [Task Plan](workshop-creation-and-draft-management/tasks.md) | [HTTP Contract](../../contracts/http/features/workshop-creation-and-draft-management.md) |
| [workshop-lifecycle-sweeper](workshop-lifecycle-sweeper/) | [Specification](workshop-lifecycle-sweeper/SPEC.md) | [Task Plan](workshop-lifecycle-sweeper/tasks.md) | — |
| [workshop-publishing-and-update](workshop-publishing-and-update/) | [Specification](workshop-publishing-and-update/SPEC.md) | [Task Plan](workshop-publishing-and-update/tasks.md) | [HTTP Contract](../../contracts/http/features/workshop-publishing-and-update.md) |
| [workshop-registration-monitoring-and-roster](workshop-registration-monitoring-and-roster/) | [Specification](workshop-registration-monitoring-and-roster/SPEC.md) | [Task Plan](workshop-registration-monitoring-and-roster/tasks.md) | [HTTP Contract](../../contracts/http/features/workshop-registration-monitoring-and-roster.md) |
| [workshop-rescheduling](workshop-rescheduling/) | [Specification](workshop-rescheduling/SPEC.md) | [Task Plan](workshop-rescheduling/tasks.md) | [HTTP Contract](../../contracts/http/features/workshop-rescheduling.md) |
| [workshop-staff-assignment](workshop-staff-assignment/) | [Specification](workshop-staff-assignment/SPEC.md) | [Task Plan](workshop-staff-assignment/tasks.md) | [HTTP Contract](../../contracts/http/features/workshop-staff-assignment.md) |
| [workshop-full-text-search](workshop-full-text-search/) | — | — | — |
| [payment-webhook-handling](payment-webhook-handling/) | — | — | — |
