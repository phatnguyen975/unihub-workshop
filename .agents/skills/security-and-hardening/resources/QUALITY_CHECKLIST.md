# Security Hardening Quality Checklist

Run this checklist before considering any change that touches the categories in `SKILL.md`'s "When to Use" section complete. This is the self-review gate; `SECURITY_CHECKLIST.md` is the detailed reference to pull specific items from.

## Threat Modeling Was Actually Done

- [ ] Every trust boundary this feature introduces or touches is listed explicitly, by name — not implied or assumed.
- [ ] STRIDE was walked for each boundary, and for every applicable category, a specific mitigation is recorded — not a generic "we'll be careful" note.
- [ ] At least one abuse case is written for the feature's primary use case, and it exists as an actual test, not only as a comment or a design note.

## Input and Output Discipline

- [ ] Every external input is validated at the boundary — before it influences any decision, query, or downstream call.
- [ ] Every database query involving externally-influenced data is parameterized.
- [ ] Every output is encoded for its destination context using the framework's standard mechanism, with no bypassed auto-escaping.

## Authorization

- [ ] Every privileged or restricted action in this change has its own explicit, server-side check.
- [ ] No authorization decision in this change relies on a client-side UI behavior, a client-supplied role claim that wasn't independently verified, or an assumption about who "would" realistically call this endpoint.
- [ ] Multi-tenant/ownership checks (if applicable) verify the specific resource being acted on, not just that the requester is generally authenticated.

## Secrets

- [ ] No credential, key, or signing secret is hardcoded anywhere in this change.
- [ ] No secret-shaped value appears in a log statement added or modified by this change.
- [ ] A search was actually run against the diff for common secret-shaped patterns before considering this item satisfied — not just a visual scan.

## Fail-Mode Decisions

- [ ] Every external dependency this change's security-relevant logic relies on has an explicit fail-open or fail-closed decision attached to it.
- [ ] Each decision is justified against that specific control's risk — not copied from a different control's decision in this codebase.
- [ ] Fail-open paths emit a log entry or metric distinguishing the degraded state from normal operation.

## Signature / Integrity Verification (if applicable)

- [ ] Every webhook handler or signed-payload consumer in this change verifies the signature/MAC on every request, with no code path that skips verification under any condition.
- [ ] The verification secret/key is sourced from protected configuration, never embedded in the verification logic itself.
- [ ] An invalid signature results in rejection with a generic response — no detail is returned about _why_ verification failed.

## Payment-Specific (if applicable)

- [ ] No client-supplied price, amount, or currency is trusted directly for what's charged — server-side validation/recomputation exists.
- [ ] The endpoint's idempotency design (per `idempotency-and-exactly-once-design`) was applied, not skipped on the assumption that "payment providers handle retries."
- [ ] No raw payment credential appears in any log statement.

## File Upload Specific (if applicable)

- [ ] File type is validated by content inspection, not filename extension or `Content-Type` header alone.
- [ ] A file size limit is enforced before the file is fully read or stored.
- [ ] The storage path/key is generated server-side, never derived directly from a client-supplied filename.

## Final Gate

- [ ] If this change is being reviewed via `/review` and a security finding is identified, it is recorded as its own finding with an explicit severity — not folded silently into an unrelated finding or left unstated.
- [ ] No existing security control in the surrounding code was weakened, disabled, or special-cased to make this change easier to land.
