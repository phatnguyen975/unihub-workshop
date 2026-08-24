# Spec: Resource Reservation With Capacity Limit

> **Status:** APPROVED  
> **Owner/area:** `reservations` module  
> **Last revised:** N/A — first version

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It does not belong to any specific project — it models a generic "reserve a unit of limited capacity" use case (e.g. booking a seat, claiming a slot, reserving inventory) to demonstrate the structure and level of detail expected, including how to handle concurrency and idempotency in the spec.

## 1. Overview

This feature allows an authenticated end user to reserve one unit of a finite-capacity resource (for example, a seat, a slot, or an inventory item). The reservation module owns this feature. It must guarantee that the resource's capacity is never exceeded, even when many users attempt to reserve the same resource at the same time, and that a single user cannot end up with two simultaneous active reservations for the same resource due to a network retry.

## 2. Actors & Preconditions

| Actor                     | Precondition                                                                          |
| :------------------------ | :------------------------------------------------------------------------------------ |
| End User                  | Must be authenticated. Must not already hold an active reservation for this resource. |
| System (capacity tracker) | The resource must exist and have a defined, non-negative remaining capacity.          |

## 3. Functional Requirements

- **FR-01:** The system SHALL accept a reservation request only from an authenticated end user.
- **FR-02:** The system SHALL reject a reservation request when the resource's remaining capacity is zero.
- **FR-03:** The system SHALL reject a reservation request when the requesting user already holds an active reservation for the same resource.
- **FR-04:** The system SHALL decrement the resource's remaining capacity by exactly one when a reservation is successfully created.
- **FR-05:** The system SHALL accept a client-supplied idempotency token on every reservation request and, when a request is retried with the same token, SHALL return the original result without creating a duplicate reservation or decrementing capacity again.
- **FR-06:** The system SHALL restore (increment) the resource's remaining capacity by exactly one when an active reservation is canceled.
- **FR-07:** The system SHALL allow only the reservation's owner to cancel it.

## 4. Business Rules

- **BR-01:** Remaining capacity must never go negative under any circumstance, including concurrent requests for the last unit of capacity.
- **BR-02:** A reservation is "active" from the moment it is successfully created until it is either canceled or expires (see State Transitions).
- **BR-03:** Capacity changes and reservation status changes must be applied together — the system must never end up in a state where capacity was decremented but no corresponding active reservation exists, or vice versa.

## 5. State Transitions

| From State | Event/Trigger                                                                    | To State | Valid? | Rejection Behavior (if invalid)                                  |
| :--------- | :------------------------------------------------------------------------------- | :------- | :----: | :--------------------------------------------------------------- |
| N/A        | User requests reservation, capacity available                                    | ACTIVE   |  Yes   | —                                                                |
| N/A        | User requests reservation, capacity zero                                         | N/A      |   No   | Reject with "capacity exhausted" error; no record created        |
| N/A        | User requests reservation, user already has active reservation for this resource | N/A      |   No   | Reject with "duplicate reservation" error; no new record created |
| ACTIVE     | Owner cancels                                                                    | CANCELED |  Yes   | —                                                                |
| ACTIVE     | Non-owner attempts cancel                                                        | ACTIVE   |   No   | Reject with authorization error; state unchanged                 |
| CANCELED   | Any further cancel attempt                                                       | CANCELED |   No   | Reject with "already canceled" error; state unchanged            |

## 6. Non-Functional Requirements

- **Concurrency:** Under concurrent reservation requests for the last available unit of capacity, exactly one request must succeed and all others must be rejected with the capacity-exhausted error — never more than one success for one remaining unit.
- **Idempotency:** A request retried with the same idempotency token (e.g. due to a client-side network timeout) must produce the same result as the original request, without any duplicate side effect.
- **Security:** Only the authenticated owner of a reservation may cancel it; this is enforced server-side, not assumed from client behavior.
- **Reliability:** If reservation creation fails after capacity has been tentatively reduced, the capacity must be restored — a failed reservation attempt must never permanently consume capacity.

## 7. Acceptance Criteria

### Scenario 1: Successful reservation with available capacity

```gherkin
Given an authenticated user with no existing active reservation for resource R
And resource R has at least 1 unit of remaining capacity
When the user requests a reservation for resource R
Then the reservation is created with status ACTIVE
And resource R's remaining capacity is decremented by exactly 1
```

### Scenario 2: Reservation rejected when capacity is exhausted

```gherkin
Given resource R has 0 remaining capacity
When an authenticated user requests a reservation for resource R
Then the request is rejected with a capacity-exhausted error
And no reservation record is created
And resource R's remaining capacity is unchanged
```

### Scenario 3: Duplicate reservation attempt is rejected

```gherkin
Given a user already holds an active reservation for resource R
When the same user requests another reservation for resource R
Then the request is rejected with a duplicate-reservation error
And resource R's remaining capacity is unchanged
```

### Scenario 4: Retried request with the same idempotency token returns the original result

```gherkin
Given a user successfully created a reservation for resource R using idempotency token T
When the same user retries the request using the same idempotency token T
Then the system returns the original reservation result
And resource R's remaining capacity is not decremented a second time
```

### Scenario 5: Concurrent requests for the last unit of capacity

```gherkin
Given resource R has exactly 1 remaining unit of capacity
When two different users simultaneously request a reservation for resource R
Then exactly one request succeeds with status ACTIVE
And the other request is rejected with a capacity-exhausted error
And resource R's remaining capacity ends at 0, not negative
```

### Scenario 6: Only the owner can cancel a reservation

```gherkin
Given user A holds an active reservation for resource R
When user B (not the owner) attempts to cancel that reservation
Then the request is rejected with an authorization error
And the reservation remains ACTIVE
```

## 8. Out of Scope

- Waitlisting users when capacity is exhausted (a separate feature, not covered here).
- Modifying an existing reservation (e.g. changing the reserved resource) — only creation and cancellation are covered.
- Automatic expiration of unconfirmed reservations after a timeout — this spec assumes reservations are immediately final; time-bound holds are a separate feature.
- Notification delivery when a reservation is created or canceled.

## 9. Linked References

- (In a real project, this section would list the specific sections of the project's architecture/design docs, data model docs, and any prior decision records that informed this spec — e.g. "Design doc section Resource Locking Strategy," "Data model doc section reservations table.")

## 10. Open Questions / Assumptions

- Assumed that "active reservation" means exactly one per user per resource, never more — confirmed against BR-02/FR-03 above; flag for the approver if multiple simultaneous reservations per user should ever be allowed for the same resource.
