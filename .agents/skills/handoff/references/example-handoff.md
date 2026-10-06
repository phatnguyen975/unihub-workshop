# Example — A Filled-In HANDOFF.md

This is a realistic, fully populated example of the Output template from `SKILL.md`, for a session that added rate limiting to an API endpoint and is being handed off partway through. Notice what's absent as much as what's present: no restated architecture, no copied code, no project history — only what's needed to resume.

```markdown
# Handoff

> **Generated:** 2026-09-11T14:32:00+07:00
> **Next session scope:** Finish the rate-limiting middleware for the `/orders` endpoint and get its tests passing.

## Current State

Rate limiting is implemented for `/orders` using a token-bucket algorithm, but one edge case (concurrent requests at the exact bucket-refill boundary) is causing an intermittent test failure. The middleware itself is written and wired in; only that one test is red.

## Done This Session

- Implemented `RateLimiter` middleware in `src/middleware/rate-limiter.ts` using a token-bucket algorithm (60 requests/minute, burst of 10).
- Wired the middleware into the `/orders` route in `src/routes/orders.ts`.
- Wrote unit tests in `src/middleware/rate-limiter.test.ts` — 7 of 8 passing.
- Confirmed via manual `curl` testing that normal traffic isn't affected and that the 429 response includes a `Retry-After` header.

## Remaining

- Fix the intermittent failure in `rate-limiter.test.ts` — the "concurrent requests at refill boundary" case (see Gotchas below).
- Add the same middleware to the `/products` endpoint (mentioned in the original ticket, not yet started).
- Update the API docs to note the new 429 response and rate limit headers.

## Decisions Made This Session

- Chose a token-bucket algorithm over a fixed-window counter — fixed-window allows short bursts right at window boundaries that would defeat the purpose here. Not yet written into a design doc; worth adding if this pattern gets reused elsewhere.
- Set the limit at 60/min with a burst of 10, based on current peak traffic numbers a teammate shared verbally this session (not yet documented anywhere) — flagging in case this needs to go into a config doc rather than staying a hardcoded value.

## Exact Next Action

Run `npm test -- rate-limiter.test.ts` and investigate the failing "concurrent requests at refill boundary" case — it appears to be a race condition in how the bucket's `lastRefill` timestamp is read and updated, not a logic error in the algorithm itself.

## How to Verify

The next action is complete when `npm test -- rate-limiter.test.ts` shows 8/8 passing, including three consecutive clean runs (the failure is intermittent, so one green run isn't sufficient confirmation).

## References

- Ticket: PROJ-482 (rate limiting for public API endpoints)
- Existing middleware pattern this follows: `src/middleware/auth.ts`

## Gotchas / Blockers

- The failing test only reproduces about 1 in 5 runs — it's timing-dependent, not deterministic. Running the single test file repeatedly is more reliable than running the full suite once.
- The team's shared traffic numbers used for the 60/min figure came from a Slack message, not a doc — worth confirming that number is still accurate before this ships.

## Redaction Note

No credentials, tokens, or PII found in this session's content.
```

## What to notice

- **No code was pasted into the file.** The middleware implementation lives in `src/middleware/rate-limiter.ts` — the next session reads it directly rather than reviewing a stale copy embedded here.
- **The architecture choice (token-bucket) is explained in one line**, not with a full design write-up — durable rationale like that could later graduate into a real design doc, but for now it's live-thread information captured just well enough not to be lost.
- **The Gotcha about test flakiness is exactly the kind of thing** that would cost a fresh session real time to rediscover on its own — that's what earns it a place in the file.
- **The Exact Next Action is one investigatable step**, not "finish rate limiting" — a fresh agent can start immediately without first figuring out what "finish" means here.
