# Branching and Pull Requests

## 1. Branch naming

**Format:** `<type>/<kebab-description>` or `<type>/<ticket-id>-<kebab-description>`.

| Rule                                              | Detail                                                                                                                                                                                                                                              |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `type`                                            | Same set as commit types: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `ci`, `chore`, `style`, `revert`                                                                                                                              |
| Description                                       | Lowercase `a-z`, digits, `-`; ≤ ~50 characters; describes the purpose, not file names. Version numbers may contain `.` (for example `release-1.4.0`)                                                                                                |
| Ticket id                                         | Add when the task is tied to an issue; keep the tracker's id format; place it right after `type/`                                                                                                                                                   |
| Release PR branch (short-lived)                   | `chore/release-<version>` — one branch per release PR. For a standard release, create it from trunk; for a patch release, create it from the corresponding release maintenance branch (see `release.md`).                                           |
| Release maintenance branch (long-lived, optional) | `release/<MAJOR>.<MINOR>` — use only when the project explicitly maintains a released version line for future patch releases (for example, `release/1.4` for the `1.4.x` line). Create it from the exact released tag or commit (see `release.md`). |
| Forbidden characters                              | Spaces, uppercase letters (the ticket id is the only exception), `~ ^ : ? * [ \`, `..`, `@{`; no trailing `.lock`, `/`, or `.` (per `git check-ref-format`)                                                                                         |
| Trunk                                             | Never name a branch the same as the trunk                                                                                                                                                                                                           |

**Examples:** `feat/user-export`, `fix/PROJ-142-duplicate-orders`, `refactor/auth-token-parsing`, `ci/cache-dependencies`, `chore/release-1.4.0`, `release/1.4`, `fix/cache-timeout-release-1.4`.

The project's own branch naming convention, when it exists, takes precedence.

## 2. Sync rules

| Branch-only commit state | Sync with trunk                                                                                                              |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| Confirmed unpublished    | `git fetch`, then `git rebase origin/<trunk>`                                                                                |
| Published or uncertain   | `git fetch`, then `git merge --no-edit origin/<trunk>` (no rebase, no rewrite); the merge commit keeps Git's default message |

A branch may exist on a remote without a configured local upstream, and a commit may have been pushed through a different ref; missing upstream information is not proof of unpublished history. Treat branch-only commits as confirmed unpublished only when the agent has reliable session-level evidence that they have never been pushed. Refresh the relevant remote-tracking refs, but if publication status remains uncertain, use the published-or-uncertain sync path and add a new commit instead of rewriting history. Sync before the first push, before opening a PR, and whenever trunk changed in an area the branch touches.

## 3. Branch lifecycle

Every branch the agent creates passes through these stages in order and ends at **Cleanup** or **Abandon**.

| Stage          | Entry condition                                                        | Actions                                                                                                                                                                                                                                                           | Exit condition                                    |
| -------------- | ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| **1. Create**  | Session-start snapshot done; working tree clean, or a worktree is used | `git fetch` → `git switch --no-track -c <name> origin/<trunk>` (or `git worktree add --no-track -b <name> <path> origin/<trunk>`) → confirm branch and base                                                                                                       | Branch exists; base is the latest trunk tip       |
| **2. Develop** | Branch created                                                         | Develop loop (W3); sync per section 2. At the configured branch-lifetime limit (2 days by default): stop, tell the user, and propose splitting the finished part into its own PR                                                                                  | Task complete and verified                        |
| **3. Publish** | Verification passes; synced with trunk                                 | `git push -u origin <name>`                                                                                                                                                                                                                                       | Branch exists on the remote                       |
| **4. Review**  | PR opened (section 4)                                                  | Address feedback with new commits; sync by merging trunk; never rewrite; update the PR description when content changes                                                                                                                                           | A human merges the PR, or it is closed unmerged   |
| **5. Merged**  | A human merged the PR                                                  | `git fetch`, then confirm the PR state on the platform; if unavailable, identify and verify the corresponding change/commit on `origin/<trunk>`. Do not rely on a commit-message search alone; if merge status or the resulting commit is uncertain, ask the user | Merge confirmed                                   |
| **6. Cleanup** | Stage 5 complete                                                       | Deletion conditions and order below                                                                                                                                                                                                                               | Local branch gone; remote branch handled as below |
| **6. Abandon** | PR closed unmerged, task cancelled, or branch replaced by split PRs    | Ask the user to confirm abandoning; record the tip sha; delete per the Cleanup rules using `-D` (R4)                                                                                                                                                              | Local branch gone; remote branch handled per R6   |

**Deletion conditions** (all must hold):

1. The PR is merged and the merge is confirmed (stage 5), or the user confirmed abandoning the branch.
2. No uncommitted changes or unpushed commits remain outside what was merged.
3. No reviewer follow-up is pending on this branch.
4. No worktree has the branch checked out.

**Cleanup order:**

1. For any worktree the agent created, inspect `git -C <path> status --short --ignored` before removal. Preserve user-owned, non-reproducible, or otherwise valuable untracked/ignored data outside the worktree; do not assume ignored files are disposable. Remove the worktree with `git worktree remove <path>` only after this check, and never use `--force` without the explicit authorization required by R9.
2. Move to the checkout that holds trunk (`git switch <trunk>` if no worktree holds it), then `git fetch --prune` and `git merge --ff-only origin/<trunk>`.
3. `git branch -d <name>` (never while standing on that branch).

**`git branch -d` is not proof of merge.** It checks the branch against its upstream (or against HEAD when no upstream is set), not against trunk. Stage 5 is the only merge confirmation.

**When `git branch -d` refuses** (for example the upstream is gone after `fetch --prune` following a squash or rebase merge, or the branch holds commits its upstream lacks): re-confirm stage 5, record the tip sha, and ask the user before using `git branch -D` (R4).

**Remote branch:** the platform deletes it on merge, or a human does. The agent deletes its own remote branch only when the user asks (R6).

## 4. Pull request rules

| Item                       | Rule                                                                                                                                                  |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Title                      | Conventional Commit header, ≤ 72 characters; release PR titles follow the release-specific format defined in `release.md` and `commit-convention.md`. |
| Scope                      | One logical change; size limits as in `SKILL.md`                                                                                                      |
| Base                       | Trunk for standard release PRs; `release/<MAJOR>.<MINOR>` for patch-release PRs targeting a maintained release line.                                  |
| Merge method               | Follows the project; keep commits and the PR title both conventional so any method yields correct history                                             |
| When to open               | After local verification passes and the branch is synced with trunk                                                                                   |
| Description                | Written for the reviewer and understandable on its own: context or problem first, then what and why; no pasted diffs or long logs                     |
| Issue links                | Closing keyword per platform (`Closes #n`) when the PR fully resolves the issue; `Refs #n` when it only contributes                                   |
| Labels, reviewers          | Assign when the project requires them and a tool supports it                                                                                          |
| After pushing more commits | Update the description so it matches the PR                                                                                                           |
| Project PR template        | Use it and fill every required section from section 5; add any section the template lacks                                                             |
| No platform tool available | Output the title and body for the user to submit                                                                                                      |

## 5. Pull request template

**Required sections:** `Summary`, `Related issues` (when an issue exists), `Changes`, `Not touched`, `Verification`, `User-facing impact`, `Checklist`. Conditional sections appear only when they apply; never leave a section empty.

```markdown
<type>[(scope)][!]: <description>

## Summary

<Context or problem, then the solution: what changed and why. 2–5 sentences.>

## Related issues

Closes #<n> <!-- or: Refs #<n> when the PR only partly addresses the issue -->

## Changes

- `<path or module>`: <what changed>

## Not touched (intentional)

- `<path or module>`: <why it is out of scope>

## Verification

- `<command>`: <result>
- Manual: <steps and outcome>

## User-facing impact

<None | behavior change | breaking change with migration steps | release note>

## Screenshots / output

<!-- conditional: UI or CLI output changes -->

<before / after>

## Rollout & rollback

<!-- conditional: feature flag, migration, config -->

<how it is enabled, how to revert>

## Reviewer notes

<!-- conditional -->

<where to focus, known limitations, planned follow-ups>

## Checklist

- [ ] Reviewed my own diff
- [ ] Tests added or updated and passing
- [ ] Docs and changelog updated where needed
- [ ] No secrets and no unrelated changes
- [ ] Commits and title follow Conventional Commits
```
