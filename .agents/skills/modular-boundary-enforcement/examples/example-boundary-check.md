# Worked Example: Catching and Fixing a Boundary Violation

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It extends the "resource reservation with capacity limit" example used across the `spec-driven-development`, `api-contract-design`, `task-planning-and-breakdown`, and `incremental-implementation` skill examples by introducing a second module — `notifications` — and showing a realistic boundary mistake, how an architectural fitness function catches it, and how it's corrected. Pseudocode is used deliberately, with no specific language, framework, or fitness-function library syntax, so this stays applicable regardless of your actual stack.

## The Setup

Two modules exist in this codebase:

```
modules/
├── reservations/
│   ├── public/                   ← the module's declared public surface (e.g. ReservationCreatedEvent)
│   │   └── reservation-events.*
│   └── internal/                 ← everything else; not reachable from outside
│       ├── domain/
│       ├── application/
│       └── infrastructure/
│           └── reservation-repository.*  ← persistence-bound, internal
│
└── notifications/
    ├── public/
    │   └── notification-sender.*
    └── internal/
        └── ...
```

`reservations` is responsible for the domain logic from the earlier examples. `notifications` is responsible for sending a confirmation message when a reservation is created. It needs to know _that_ a reservation was created and _for whom_ — it does not need to know anything about how reservations are stored or modeled internally.

## The Violation

While implementing the notification-sending behavior, the change reaches directly into `reservations`' internal repository to fetch reservation details, instead of using the event the `reservations` module already publishes on its public surface:

```
// Inside notifications module — THIS IS THE VIOLATION
import { ReservationRepository } from "reservations/internal/infrastructure/reservation-repository"

class NotifyOnReservationCreated:
    constructor(reservationRepository: ReservationRepository, sender: NotificationSender):
        this.reservationRepository = reservationRepository // ← reaching into another module's internals
        this.sender = sender

    handle(reservationId):
        reservation = this.reservationRepository.findById(reservationId)
        this.sender.send(reservation.userId, "Your reservation is confirmed.")
```

This compiles. It works in manual testing. Nothing about running the application surfaces a problem today.

## How the Fitness Function Catches It

The project's architectural fitness-function suite includes a rule roughly equivalent to:

```
test "no module may depend on another module's internal package":
    for each module in all_modules:
        forbid: any import from <module>/internal/** originating outside that same module's own root

    run against the full dependency graph
    expect: zero violations
```

Running the suite produces a failure:

```
FAIL: Architectural fitness function — module boundary violation
  notifications/.../notify-on-reservation-created.* imports
  reservations/internal/infrastructure/reservation-repository.*

  Modules may only depend on another module's declared public surface (reservations/public/**), never its internal package.
```

This fails the build. The violation is caught here — before code review, before merge — regardless of whether a human reviewer happened to notice the import.

## The Fix

The correct mechanism already exists on `reservations`' public surface: It publishes a `ReservationCreatedEvent` precisely so other modules don't need to reach into its internals to learn that a reservation was created.

```
// reservations/public/reservation-events.*  (already existed, unchanged)
event ReservationCreatedEvent:
    reservationId: string
    userId: string
    resourceId: string
    createdAt: timestamp
```

The `notifications` module is corrected to depend only on this public event shape:

```
// Inside notifications module — CORRECTED
import { ReservationCreatedEvent } from "reservations/public/reservation-events"

class NotifyOnReservationCreated:
    constructor(sender: NotificationSender):
        this.sender = sender

    handle(event: ReservationCreatedEvent):
        this.sender.send(event.userId, "Your reservation is confirmed.")
```

**Notice what changed:** `notifications` no longer depends on `ReservationRepository` at all. It needed exactly two fields (`userId`, and implicitly which reservation this was about), and the already-existing public event already carried them. No new public surface had to be added — the fix was to _use_ the public surface that already existed, instead of reaching past it out of habit or convenience.

Re-running the fitness function suite now passes:

```
PASS: Architectural fitness function — module boundary violation
  0 violations found across 2 modules
```

## What This Example Demonstrates

- The violation was small and easy to write without noticing — one import, motivated by "I just need one field from this entity."
- It would very plausibly have passed casual code review; the import statement doesn't look alarming at a glance unless the reviewer specifically checks the dependency direction.
- The fitness function caught it unconditionally, the same way it would catch it on any future change, by anyone, without depending on a reviewer's attention that day.
- The fix did not require widening `reservations`' public surface — it required _using_ the public surface correctly instead of bypassing it. (Had the event genuinely not carried enough information, the correct next step would have been to deliberately extend the event's shape — a conscious decision, not a silent repository reach-through.)
- `notifications` is now decoupled from `reservations`' persistence model entirely — a future change to how reservations are stored cannot break the notification flow, because notifications never knew about that storage model in the first place.
