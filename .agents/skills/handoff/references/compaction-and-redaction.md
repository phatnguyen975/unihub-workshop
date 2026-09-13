# Compaction & Redaction — Detailed Guidance

This is the detail behind Workflow steps 2–5 in `SKILL.md`: how to judge what's "already durable" versus what belongs in the live thread, and how to redact sensitive content before writing.

## Judging "already durable" vs. "live thread"

The single question to ask about every candidate piece of information: **if the next session never read this file at all, but had access to the project's other durable artifacts, would this information still be findable?**

- **Yes → durable.** It belongs in References as a pointer, not restated in the body. Examples: an architectural decision written into an ADR, a requirement already in a spec, a change already captured in a commit message or PR description, a bug already filed as an issue.
- **No → live thread.** It only exists right now, in this session's working memory. Examples: "I tried approach A, it didn't work because of X, so I'm about to try approach B" — unless that reasoning was written into a commit or doc, it lives only in the conversation and needs to be captured or it's gone.

A useful secondary test: **would writing this down duplicate wording that already exists in a file the next session can open?** If restating it would just be a paraphrase of something already committed, drop it and point at the original instead.

### Common judgment calls

- **A decision was made this session but hasn't been written anywhere yet.** → Live thread; goes under "Decisions Made This Session." (If the project keeps its own running decisions or requirements record, note that too, so it eventually lands there — but don't assume it already has.)
- **A decision was made and immediately committed/documented.** → Durable; reference it, don't restate the decision itself.
- **A file was fully rewritten this session.** → Don't paste the diff or the new content. Name the file under "Done This Session" and let the next session read the file directly — that's what makes it durable already.
- **An approach was tried and abandoned.** → This is genuinely live-thread information if it's not written anywhere — knowing what _didn't_ work saves the next session from repeating it. Keep it brief: what was tried, why it didn't work, one line each.
- **A long discussion converged on a plan, but the plan itself got written into a plan/spec file.** → The plan is durable (reference it); only the fact that this conversation is what produced it, if relevant to "Current State," might be worth one line.

## Scope-filtering against next-session focus

Once compaction removes duplicated content, apply the declared next-session scope as a second filter:

- Anything squarely inside the stated scope → keep at full detail.
- Anything adjacent but not blocking → reduce to a one-line mention or drop, at judgment.
- Anything outside the scope but that would still block or surprise the next session if omitted (a known blocker, a breaking change elsewhere in the codebase) → keep, even if brief, regardless of scope.

The goal is a file sized to the actual next task, not the whole project.

## Redaction patterns

Scan the compacted content (not the raw conversation — do this as the last step, right before writing) for the following, and replace each with a placeholder describing the _type_ of thing redacted, never the value itself:

| Pattern                                                 | Examples                                                                                                          | Replace with                      |
| ------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| API keys / tokens                                       | `sk-...`, `ghp_...`, bearer tokens, JWTs                                                                          | `<REDACTED: API key>`             |
| Passwords / secrets                                     | Literal passwords, connection strings with embedded credentials                                                   | `<REDACTED: credential>`          |
| Personally identifiable information                     | Real names tied to personal accounts, emails, phone numbers, physical addresses not already public in the project | `<REDACTED: PII>`                 |
| Internal-only identifiers that shouldn't leave a system | Internal customer IDs, non-public infrastructure hostnames, if their exposure would be a problem                  | `<REDACTED: internal identifier>` |

If a command or log line contains a credential inline (e.g. a curl command with an Authorization header, or an error message that echoes back a connection string), redact just the sensitive substring and keep the rest of the line intact — don't discard useful debugging context just because part of it needs redaction.

Always populate the **Redaction Note** section explicitly, even when nothing was found — write something like "No credentials, tokens, or PII found in this session's content." Silence on this point is not an acceptable substitute for stating it; a missing note is indistinguishable from a forgotten check.
