# API Contract: <Feature Name>

> **Status:** DRAFT or APPROVED  
> **Spec:** specs/<feature-name>/SPEC.md  
> **Last revised:** <date> — <one-line reason, only after first approval>

## 1. Overview

<!--
One paragraph linking this contract to its spec file and the
module/component it belongs to. State what part of the system surface
this contract covers.
-->

## 2. Global Standards Applied

<!--
Confirm, explicitly, which of the project's existing API standards apply
to this contract. Do not invent new conventions here — reference what the
project has already established. List only what's relevant; remove lines
that don't apply to this contract.
-->

- **URI structure / versioning:** ...
- **Pagination convention:** (if collection endpoints exist)
- **Success response shape:** ...
- **Error response format:** ...
- **Idempotency-key convention:** (if applicable)
- **Auth header convention:** ...
- **Date/time formatting convention:** ...

## 3. Endpoints

<!--
Repeat this block for every endpoint in the contract.
-->

### <HTTP METHOD> <URI path>

**Description:** <!-- one sentence -->  
**Required Role / Auth:** <!-- e.g. PUBLIC | AUTHENTICATED | <specific role(s)> -->  
**Idempotency Requirement:** Required | Not Required — <!-- one-line reason -->

#### Request

**Headers:**

| Header | Required | Description |
| :----- | :------- | :---------- |
|        |          |             |

**Path Parameters:**

| Parameter | Type | Description |
| :-------- | :--- | :---------- |
|           |      |             |

**Query Parameters:**

| Parameter | Type | Required | Description |
| :-------- | :--- | :------- | :---------- |
|           |      |          |             |

**Request Body (if applicable):**

```json
{
  "fieldName": "type — description"
}
```

**Validation Rules:**

- `fieldName` — <!-- format, required/optional, range, max length, etc. -->

#### Response

**Success — <status code> <reason phrase>:**

```json
{
  "fieldName": "type — description"
}
```

**Error Responses:**

<!--
Enumerate EVERY status code this endpoint can return, with the exact
condition that triggers it. "Other errors may occur" is not acceptable
(Rule A-6).
-->

| Status | Error Identifier | Condition |
| :----- | :--------------- | :-------- |
|        |                  |           |

#### Side Effects (if any)

<!--
Anything beyond the response itself that a successful call causes:
an event published, a downstream system notified, another record
created/modified elsewhere. Leave as "None." if genuinely none.
-->

#### Spec Traceability

<!-- Which FRs and Acceptance Criteria scenarios this endpoint satisfies. -->

- **Satisfies:** FR-XX, Scenario N

<!-- Repeat the "### <HTTP METHOD> <URI path>" block above for each additional endpoint. -->

## 4. Rate Limiting Behavior (if applicable)

<!--
Describe any rate-limiting or throttling behavior that applies to
endpoints in this contract, and which endpoints it applies to.
-->

## 5. Idempotency Behavior (if applicable)

<!--
For every endpoint marked "Idempotency Requirement: Required" above,
describe the idempotency key lifecycle in more detail here, or reference
the relevant section of the idempotency-and-exactly-once-design skill
output if one was produced separately.
-->

## 6. Cross-Endpoint Consistency Notes

<!--
Note anywhere this contract intentionally reuses a field name, type, or
error shape from another endpoint in this contract or another existing
contract in the project, to make consistency decisions visible to a
reviewer.
-->
