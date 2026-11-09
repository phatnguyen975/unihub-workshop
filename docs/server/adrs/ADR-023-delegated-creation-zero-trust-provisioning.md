# ADR-023: Delegated Creation & Zero-Trust Provisioning

> **Scope:** server

- **Context:** `ORGANIZERs` provision `STAFF` and other `ORGANIZER` accounts without a shared admin console, and passwords must not be exposed to the inviter.
- **Decision:** Use delegated creation with system-generated temporary passwords, mandatory first-login password change, and explicit workshop assignment for `STAFF`.
- **Rationale:** Zero-trust provisioning prevents password exposure and ensures new accounts are secured at first login. Explicit assignment prevents `STAFF` from scanning unassigned workshops even within the same organization.
- **Alternatives Considered:** Shared initial passwords were rejected because they expose credentials to the inviter and violate zero-trust principles. Self-service account creation was rejected because `STAFF` and delegated `ORGANIZER` provisioning must remain scoped, auditable, and organization-bound.
- **Consequences:** Requires email delivery reliability and account-state enforcement on first login.
