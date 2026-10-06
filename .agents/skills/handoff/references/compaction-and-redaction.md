# Compaction & Redaction

Detail behind Workflow steps 2–5 in `SKILL.md`: judging what's "already durable" vs. what belongs in the live thread, and redacting before writing.

## Durable vs. live thread

**Test:** If the next session never read this file, but had access to the project's other artifacts, would this still be findable?

- **Yes → durable.** Belongs in References as a pointer, not restated. E.g. an ADR, a spec requirement, a commit/PR, a filed issue.
- **No → live thread.** Only exists in this session's working memory right now — e.g. "tried A, failed because of X, trying B next." Capture it or it's gone.

**Secondary test:** Would writing this down just paraphrase something already committed? Drop it, point at the original instead.

### Common judgment calls

- **Decision made this session, not written anywhere yet** → live thread, goes under "Decisions Made This Session".
- **Decision made and immediately committed/documented** → durable, reference it.
- **A file was fully rewritten** → name it under "Done This Session", don't paste the diff — the file itself is now durable.
- **An approach tried and abandoned, not written anywhere** → live thread, worth capturing briefly (what, why it failed) — saves the next session from repeating it.
- **A discussion converged on a plan that got written into a spec file** → the plan is durable; only note the conversation produced it if that's relevant to Current State.

## Scope-filtering

Apply the declared next-session focus as a second filter, after compaction:

- Squarely in scope → full detail.
- Adjacent, not blocking → one-line mention or drop, at judgment.
- Outside scope but would block/surprise if omitted (a known blocker, a breaking change elsewhere) → keep, briefly, regardless of scope.

**Goal:** a file sized to the actual next task, not the whole project.

## Redaction patterns

Scan the compacted content — not the raw conversation, do this last, right before writing — and replace with a placeholder naming the _type_, never the value:

| Pattern                   | Examples                                                                          | Replace with                      |
| ------------------------- | --------------------------------------------------------------------------------- | --------------------------------- |
| API keys / tokens         | `sk-...`, `ghp_...`, bearer tokens, JWTs                                          | `<REDACTED: API key>`             |
| Passwords / secrets       | Literal passwords, connection strings with embedded credentials                   | `<REDACTED: credential>`          |
| PII                       | Real names tied to personal accounts, emails, phone numbers, non-public addresses | `<REDACTED: PII>`                 |
| Internal-only identifiers | Internal customer IDs, non-public hostnames                                       | `<REDACTED: internal identifier>` |

A credential embedded in a command or log line — redact just that substring, keep the rest intact; don't discard useful debugging context over part of a line.

Always fill in **Redaction Note**, even when nothing was found — e.g. "No credentials, tokens, or PII found." A missing note reads the same as a forgotten check.
