# Worked Example: Identifying and Fixing an N+1 Query

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It extends the "resource reservation with capacity limit" example used across the other skill examples with a new, simple capability — "a user can list their reservations, with each resource's name shown" — and walks through an N+1 defect introduced while building it, how it's caught, and how it's fixed. Pseudocode is used deliberately, with no specific language, framework, or ORM syntax, so this stays applicable regardless of your actual stack.

## The Capability Being Built

`GET /api/v1/users/me/reservations` — returns the authenticated user's reservations, each annotated with the name of the resource it's for (not just the resource's ID), so the client doesn't need a second round trip per item to display a usable list.

## The Defect, As First Written

```
function listMyReservations(userId):
    reservations = reservationRepository.findAllByUserId(userId) // 1 query

    result = []
    for reservation in reservations:
        resource = resourceRepository.findById(reservation.resourceId) // ← N additional queries
        result.append({
            reservationId: reservation.id,
            status: reservation.status,
            resourceName: resource.name
        })
    return result
```

This works correctly. It also issues exactly N+1 queries for a user with N reservations — invisible in manual testing with two or three reservations, and a real cost once a user (or the dataset used in a load test) has dozens or hundreds.

## Step 1 — Recognize the Shape

Per this skill's Anti-Pattern Catalog: a fetch call (`resourceRepository.findById`) sits textually inside a loop body that iterates over the result of an earlier query (`reservationRepository.findAllByUserId`). This is the defining shape of an N+1 query, regardless of whether it was caught while writing the code (`/build`) or while reviewing it afterward (`/review`).

## Step 2 — Apply the Fix: Batch the Related Fetch

```
function listMyReservations(userId):
    reservations = reservationRepository.findAllByUserId(userId) // 1 query

    resourceIds = reservations.map(r => r.resourceId).distinct()
    resourcesById = resourceRepository.findByIds(resourceIds).indexById() // 1 batched query

    result = []
    for reservation in reservations:
        resource = resourcesById[reservation.resourceId] // no additional query
        result.append({
            reservationId: reservation.id,
            status: reservation.status,
            resourceName: resource.name
        })
    return result
```

This is now exactly 2 queries, regardless of how many reservations the user has — 1 for the reservations, 1 batched fetch for every distinct resource referenced by them. (If the underlying data layer supports a direct join or an "include"-style eager-load mechanism for this relationship, that mechanism is generally preferable to this manual two-step batching — use whichever your stack's data layer provides; the manual version is shown here because it stays meaningful across any stack.)

## Step 3 — Confirm Index Support

`resourceRepository.findByIds(resourceIds)` filters on the resource table's primary key — already indexed by definition, so no new index is needed here. Had this batched fetch instead filtered on a non-primary- key column (for example, fetching resources by some other attribute), this step would route through `database-schema-evolution` to confirm a supporting index exists.

## Step 4 — Write a Test That Would Catch a Regression

A correctness test alone (asserting the returned `resourceName` values are right) would pass against _both_ the original N+1 version and the fixed version — it doesn't distinguish them. Per this skill's Best Practices, a separate check on query count is what actually guards against the inefficiency being reintroduced later:

```
test "listing reservations issues a bounded number of queries regardless of reservation count":
    seedReservationsForUser(userId, count: 25) // 25 reservations, several distinct resources

    queryCountBefore = queryLogger.count()
    listMyReservations(userId)
    queryCountAfter = queryLogger.count()

    // Exactly 2 queries (one for reservations, one batched fetch for
    // resources), NOT proportional to the 25 reservations — this is
    // what would fail again if the N+1 shape were reintroduced.
    expect: (queryCountAfter - queryCountBefore) == 2
```

## What This Example Demonstrates

- The defect was correct in its output and still a real performance problem — correctness and query efficiency are different properties, and a test only checking the former would never have caught this.
- The fix used a batched fetch keyed by distinct IDs collected from the first query's results, rather than fetching once per item — the general-purpose version of the fix that applies even without a stack-specific "eager load" mechanism available.
- Index support was checked as part of the fix (Core Principle #3), even though in this specific case the batched query happened to filter on an already-indexed primary key.
- The regression test asserts on query _count_, not just on returned data — directly guarding against the specific defect this skill exists to catch, rather than a generic correctness check that wouldn't notice if the N+1 shape came back.
