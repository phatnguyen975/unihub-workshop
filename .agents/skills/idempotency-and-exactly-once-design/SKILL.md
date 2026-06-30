---
name: idempotency-and-exactly-once-design
description: Designs the idempotency key lifecycle, the atomic deduplication mechanism, and the exact failure scenarios that naive implementations miss. Use when designing or implementing any mutating operation with retry risk — a payment, a financial transaction, a webhook handler, a message consumer, a batch sync operation, or any endpoint a client might legitimately call more than once for the same logical intent. Do not use for read-only operations, which are naturally safe to repeat.
---

# Idempotency and Exactly-Once Design

## Overview

Networks fail. Clients time out and retry. Message brokers redeliver. None of this is exceptional — it is the normal operating condition of any distributed system, including a single client talking to a single server over the internet. An operation that mutates state and isn't designed to tolerate being called more than once for the same logical intent **will**, eventually, be called more than once — and the result is a duplicate charge, a duplicate order, or silently corrupted state.

This skill governs how to design that tolerance deliberately: The idempotency key's scope and lifecycle, the atomic mechanism that prevents two concurrent attempts from both succeeding, and the specific failure scenarios — not just "network timeout" — that naive implementations consistently miss.

## When to Use

- Designing or implementing a mutating endpoint with genuine retry risk (the client might legitimately resend the same logical request: network timeout, mobile connectivity drop, explicit user retry).
- A payment or other financial transaction.
- A webhook handler receiving events from an external provider (most webhook providers redeliver on any non-success response, by design).
- A message/event consumer in an at-least-once delivery system (the default guarantee of most message brokers).
- A batch or bulk synchronization operation that might be re-run, in whole or in part, after a partial failure.

## When NOT to Use

- Read-only (`GET`-style) operations — these are naturally safe to repeat and need no additional design here.
- An operation that is already naturally idempotent by its own semantics (e.g. "set this field to value X" — repeating it leaves the same end state) and has no other side effect that isn't itself idempotent. Confirm this carefully, though — see **Idempotency vs. Deduplication** section below; many operations that look naturally idempotent have a non-idempotent side effect hiding inside them (e.g. also sending a notification every time).

## Core Principles

1. **Idempotency is about outcome; deduplication is about suppression.** These are related but distinct properties, and conflating them is the single most common source of confusion in this area. _Idempotent_ means the system's observable end state after N executions is the same as after 1 — it does not by itself mean the operation only runs once, or that no side effect fires more than once. _Deduplication_ means detecting that a request is a repeat of one already handled, and suppressing the repeat so the underlying operation executes exactly once. An idempotency-key mechanism provides deduplication, which is what actually gets you to "the operation's real-world side effects happened once" — not just "the database ended up in a consistent state."
2. **Exactly-once delivery is not achievable end-to-end; exactly-once _processing_ is, and that's what you're actually building.** It is a proven result that a distributed system cannot guarantee a message is delivered exactly once across an unreliable network. What _is_ achievable, and is the actual goal of this skill, is: at-least-once delivery (the network/broker may redeliver) combined with idempotent processing (redelivery has no additional effect) — together these produce an effectively-exactly-once _outcome_, even though delivery itself was not exactly-once.
3. **The atomic deduplication check must happen at the same layer as the durable state change, in the same transaction.** A check that asks "have I seen this key before?" and a separate step that performs the operation are two operations with a race condition between them — two concurrent requests with the same key can both pass the check before either has recorded itself. The only correct mechanism is an atomic, database-enforced operation (an insert that fails on conflict) that combines "claim this key" and "is this a duplicate" into one indivisible step.
4. **Persist the response artifact, not just an execution flag.** It is not enough to record "this key has been processed" — a retry needs the _actual stored response_ to return, not a re-derivation of it. If the only thing persisted is a boolean or a status enum, a retry after the first attempt succeeded has nothing to safely return to the caller, and either re-executes (defeating the purpose) or fails in a way the caller can't distinguish from a real error.
5. **Every async boundary is its own idempotency domain.** Idempotency handled at the HTTP layer does not automatically protect a message consumer two hops downstream, and idempotency handled in one message consumer does not automatically protect whatever it calls next. The assumption that "someone else in this chain is already handling retries safely" is one of the most expensive false beliefs in distributed system design — each boundary that can independently redeliver or retry needs its own explicit idempotency treatment.
6. **The idempotency key is the client's statement of intent, not a request identifier.** The client (not the server) generates the key, and generates exactly one key per _logical operation_ — not one per HTTP attempt. A genuine retry of the same logical operation reuses the same key; a new logical operation (even if it happens to look similar) gets a new key. This distinction is what makes the server's job tractable: same key, same intent.
7. **TTL must match the real retry window, not be set arbitrarily.** Too short, and a legitimate late retry (a mobile client reconnecting after an extended outage) is treated as a brand-new operation. Too long, and storage grows unnecessarily and stale keys linger far past any retry that could plausibly still arrive.

## Idempotency vs. Deduplication

A naturally idempotent operation (e.g. `PUT /resources/{id}` setting a field to a specific value) still needs explicit deduplication if it has _any_ side effect that isn't itself naturally idempotent — most commonly, sending a notification, calling a third-party API, or incrementing a counter as a side effect of the "idempotent" write. Don't assume an endpoint is safe to retry just because its primary database write looks idempotent on its own; audit every side effect the operation triggers, not only its headline state change.

## Failure Scenarios This Skill Specifically Addresses

Beyond the well-covered "client times out, retries" case, design explicitly for these:

### Completed-but-undelivered

The server receives the request, processes it successfully, durably writes the result — and then the connection drops before the response reaches the client. The client, having received nothing, cannot distinguish this from "the request never arrived" or "the request failed outright," and retries. **This is why Core Principle #4 matters** — If the stored record only says "completed" with no stored response body, the retry has no correct artifact to return, even though the operation genuinely succeeded the first time.

### Concurrent retries racing each other

Two requests carrying the same idempotency key arrive close enough together that a naive "check, then act" sequence lets both pass the check before either has recorded itself. This is exactly what **Core Principle #3**'s atomic insert-or-conflict mechanism exists to prevent — design for this explicitly; it is not a rare edge case under any meaningful concurrent load.

### In-flight key, second request arrives before the first finishes

A request with a given key is still being processed (not yet completed or failed) when a second request with the same key arrives — common when a client retries _too eagerly_, before the original attempt's response time budget has even elapsed. Decide explicitly what this returns: typically a transient "still processing" signal that tells the caller to wait and retry again shortly, never a silent duplicate execution and never an indefinite hang.

### Multi-step operations and which key covers what

A single logical operation often spans multiple steps (e.g. charge a payment method, then create an order record, then schedule a confirmation). If a later step fails after an earlier step has already taken effect, the idempotency key must be scoped to the _whole logical operation_, not to each individual step in isolation — otherwise a retry re-executes the already-succeeded earlier step under a "different, narrower" key scope that doesn't recognize it as already done.

### Webhook/event redelivery as its own domain

A webhook provider or message broker redelivering an event is a completely separate retry source from the original client request that triggered the underlying operation, often arriving on a different code path entirely. It needs its own idempotency key (commonly the provider's own event/message ID) and its own deduplication record — having handled the original client-facing request idempotently does nothing to protect this separate, independent delivery channel.

## Workflow

### Step 1 — Confirm This Operation Actually Needs This Skill

Per **When NOT to Use** section: Confirm the operation mutates state and has genuine retry risk. If it's read-only, stop here.

### Step 2 — Define the Idempotency Key's Scope

**State explicitly:** what counts as "the same logical operation" for this specific endpoint/consumer? **Commonly:** `(actor, key)` — the combination of who is making the request and the client-supplied key — so that two different users supplying the same key value by coincidence don't collide. For a webhook/event consumer, the scope is usually the provider's own globally unique event ID, not anything the receiving service invents.

### Step 3 — Design the State Machine

Define the states this operation's idempotency record moves through — commonly something like: an initial claimed/in-progress state, a terminal success state with the stored response, and a terminal failure state with the stored error. Define, explicitly, what each state means for a retry that arrives while the record is in it (see **Failure Scenarios This Skill Specifically Addresses** section above, especially the in-flight case).

### Step 4 — Design the Atomic Claim Mechanism

Specify the exact atomic operation that claims a key: an insert into a durable store with a uniqueness constraint on the key (scoped per **Step 2**), performed in the same transaction as the very first effect of the operation. On a uniqueness violation, the request is a duplicate by definition — look up and return the existing record's stored outcome; do not re-attempt the underlying operation.

### Step 5 — Decide What Gets Persisted on Success and on Failure

Specify exactly what's stored for a completed record: at minimum, the response status/code and the response body that should be replayed to a retry. Specify the same for a failed record — whether failures are replayed identically on retry (no re-attempt) or whether failure is treated as "safe to retry fresh" depends on the specific failure (a permanent validation error vs. a transient downstream timeout) — state this explicitly per failure category, don't leave it implicit.

### Step 6 — Define the TTL

State the TTL and the reasoning behind it — tied to the realistic retry window for this specific client/channel (a mobile app reconnecting after an outage has a very different realistic window than a synchronous server-to-server call), not a default copied from an unrelated system.

### Step 7 — Identify Every Other Async Boundary This Operation Crosses

Per **Core Principle #5**: If this operation, once accepted, hands off to a message queue, a webhook delivery, or any other component that can itself redeliver or retry, that boundary needs its **own** idempotency treatment — list each one explicitly rather than assuming the boundary you just designed for covers everything downstream of it.

### Step 8 — Write the Concurrent-Retry Test Scenario

Before considering the design complete, define the specific test that proves the atomic claim mechanism actually prevents the race in **Concurrent retries racing each other** section above — two simulated concurrent attempts with the same key, asserting exactly one underlying side effect occurred and both callers received the same stored response. (See `test-driven-development` for how this fits the project's broader testing approach — this skill defines _what_ must be proven; that skill governs _how_ it's proven as a test.)

### Step 9 — Self-Review Against the Quality Checklist

Run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before considering the design ready.

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] The operation was confirmed to genuinely need this skill (mutating, with real retry risk) — not applied reflexively to a read-only path.
- [ ] The idempotency key's scope is stated explicitly (e.g. `(actor, key)`, or a provider's event ID for webhook/consumer paths).
- [ ] The claim-and-record mechanism is a single atomic, database-enforced operation — never a separate "check" step followed by a separate "act" step.
- [ ] The stored record includes the actual response artifact to replay (status/code and body), not only a completion flag.
- [ ] The in-flight (still-processing) case has an explicit, defined response — not a silent duplicate execution and not an indefinite hang.
- [ ] Every side effect of the operation was audited for its own idempotency, not just the primary database write.
- [ ] For multi-step operations, the key scopes the entire logical operation, not each step independently.
- [ ] Every additional async boundary this operation crosses (message queue, webhook delivery, downstream consumer) has its own, separately designed idempotency treatment.
- [ ] The TTL is justified against this specific operation's realistic retry window, not copied from an unrelated default.
- [ ] A concurrent-retry test scenario is defined, proving exactly one underlying side effect occurs under simultaneous duplicate attempts.

## Anti-Patterns

| Anti-Pattern                                                                                 | Why It Fails                                                                                                                                                                                      |
| :------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Check-then-act:** query whether a key exists, then separately insert/process if not        | Has a race condition by construction — two concurrent requests with the same key can both pass the check before either has recorded itself, defeating the entire purpose                          |
| Storing only a completion flag/status, not the actual response body                          | A retry after a genuine success has no correct artifact to return — it either re-executes the operation (a duplicate side effect) or returns an unhelpfully generic error the caller can't act on |
| Generating the idempotency key server-side instead of accepting one from the client          | The server cannot know the client's retry intent if it generates a new key on every attempt — this silently defeats deduplication while still técnically having "a key"                           |
| Scoping the idempotency key per HTTP attempt instead of per logical operation                | A client that legitimately retries ends up with a different key each time, and the server has no way to recognize the retry as the same logical intent                                            |
| Treating one idempotency-key check anywhere in the pipeline as protecting the whole pipeline | Each async boundary (HTTP, queue, webhook, downstream consumer) is its own retry/redelivery domain — protecting one does nothing for the others                                                   |
| Assuming a "naturally idempotent" write means the whole endpoint is safe to retry            | A side effect riding along with that write (a notification sent, a counter incremented, a downstream call made) is very often not itself idempotent, even when the primary write is               |
| Setting the TTL to an arbitrary round number copied from another system                      | Too short silently breaks legitimate late retries; too long accumulates storage and keeps stale records around well past any retry that could plausibly arrive                                    |

## Best Practices

- Design the idempotency mechanism at the same time as the operation itself — not as a retrofit once a double-execution incident has already happened in production.
- Make the stored record's failure category explicit (permanent validation failure vs. transient downstream error) so a retry against a failed record can correctly decide whether to replay the stored failure or whether this specific failure category is meant to be retried fresh.
- Where the underlying client library or platform already provides an idempotency mechanism (e.g. a payment processor's own idempotency-key support), use it as one layer, but still implement your own deduplication for everything your own service does around that call (e.g. creating your own order record) — the provider's guarantee does not extend to your side effects.
- Log every detected duplicate (a request that matched an existing key) distinctly from a fresh request — this is valuable operational signal for understanding actual retry rates and client behavior, not just an internal implementation detail to discard.
- When in doubt about whether two requests represent the same logical operation or two genuinely different ones, default to treating them as different (i.e. require an explicit, deliberate key reuse from the client) rather than silently merging intents that might not actually be the same.

## Common Rationalizations

| Rationalization                                                                     | Reality                                                                                                                                                                                                                                                                                                                  |
| :---------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "The client won't retry that fast, the race condition is theoretical."              | Concurrent retries happen for reasons that have nothing to do with how "fast" a client retries — a load balancer routing a duplicate, a mobile client's background retry firing while the user also manually retries, a proxy resending on a timeout it interpreted independently. Under real traffic, this is not rare. |
| "We'll add idempotency if it becomes a problem in production."                      | By the time it's a problem, callers already exist without the idempotency-key contract — adding the requirement later is a breaking change for every one of them. The decision belongs at design time, even if the chosen answer for a given low-risk endpoint is "not needed."                                          |
| "The database write is idempotent, so the endpoint is safe to retry."               | Audit every side effect, not just the primary write. A side effect that fires on every call (a notification, a third-party charge, an event published) breaks this assumption even when the core write itself is perfectly idempotent.                                                                                   |
| "One idempotency check at the API gateway covers the whole request lifecycle."      | A gateway-level cache is a useful performance optimization for the fast path, but it is not the authoritative source of correctness for state-mutating operations, and it does nothing for retries/redeliveries that occur at any boundary downstream of it (queues, webhooks, consumers).                               |
| "Exactly-once messaging from our broker means we don't need idempotent processing." | No broker can deliver exactly-once across an unreliable network in the strict mathematical sense — what a broker markets as "exactly-once" is at-least-once delivery plus its own idempotent producer/consumer bookkeeping. Build idempotent processing in your own handler regardless of what the broker claims.        |

## Output

This skill does not produce a standalone document artifact — its output is the idempotency design applied directly to the relevant `API_CONTRACT.md` (the Idempotency Behavior section, per `api-contract-design`), the implementation itself, and the concurrent-retry test scenario defined in Step 8.

**Worked example:** [`examples/example-idempotency-design.md`](examples/example-idempotency-design.md) walks through the full idempotency design for the `POST /resources/{resourceId}/reservations` endpoint from the `api-contract-design` skill's worked example — continuing the domain-neutral "resource reservation" example used across the other skill examples. Illustrative only, with pseudocode rather than any specific language, framework, or database syntax.
