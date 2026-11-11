# Integration Plan: <Capability ID>

> **Platform:** <web|mobile>  
> **Mode:** INTEGRATION  
> **Status:** DRAFT  
> **Revision:** <revision>  
> **Mock Acceptance:** <explicit decision and scope>  
> **Contract Source and Revision:** <approved public contract>  
> **Approval:** <pending or explicit human decision/date/scope>

## Scope and Prerequisites

Identify capability, accepted mock revision, approved contract source/revision and integration scope. List unresolved dependencies; do not require unrelated capability acceptance.

## Client Model and Wire Mapping

Map operation/schema fields and errors to owned service inputs/results. Describe missing/null values, enums, time/currency, pagination and SSE where relevant. Identify gaps before implementation.

## Adapter Boundaries and Target Environment

Name owning feature adapters and the platform composition root (`src/app/` for web, `src/bootstrap/` for mobile). Keep Expo route files thin and wire DTOs private to data adapters.

Declare adapter/composition files, target base URL configuration and synthetic account/data setup without credentials. Specify mock/live isolation, cleanup and authorized external effects.

## Ordered Tasks and Dependencies

Use stable integration task IDs with AC references, dependencies, file scope, observable results and checks. Preserve original mock tasks and acceptance.

## Error, Authentication, Retry and State Behavior

Define contract-derived auth expiry, cancellation, duplicate actions, timeout/retry, idempotency and reconciliation behavior. Do not automatically retry unsafe mutations.

## Verification Matrix

Separate pure mapping checks, schema validation, controlled integration scenarios and actual environment journeys. Identify commands, expected outcomes, required targets and provider dependencies.

| Scenario | Unit/Component | Contract | Browser/Native | Human | Pending Dependencies |
| -------- | -------------- | -------- | -------------- | ----- | -------------------- |

## Contract Gaps and Approval-Sensitive Changes

List each gap with operation/requirement references and blocked dependent tasks. Route contract changes to their owner; never patch provider code within client integration.

## Acceptance and Evidence

Identify regression checks and human integrated-acceptance scenarios. Keep mode/revision-labelled records; preserve mock acceptance and leave absent evidence pending.
