# Commit Convention

A project's own commit convention (lint config, contributing docs, consistent `git log` pattern) takes precedence over the defaults below.

## 1. Format

```
<type>[(scope)][!]: <description>

[body]

[footer(s)]
```

## 2. Types

| Type                                          | Use when                                  | SemVer |
| --------------------------------------------- | ----------------------------------------- | ------ |
| `feat`                                        | A feature is added                        | MINOR  |
| `fix`                                         | A bug is fixed                            | PATCH  |
| `refactor`                                    | Code structure changes, behavior does not | —      |
| `perf`                                        | Performance improves                      | —      |
| `docs`                                        | Documentation only                        | —      |
| `test`                                        | Tests added or changed                    | —      |
| `build`                                       | Build system or dependencies              | —      |
| `ci`                                          | CI configuration                          | —      |
| `style`                                       | Formatting only, no logic change          | —      |
| `chore`                                       | Remaining maintenance                     | —      |
| `revert`                                      | A previous commit is reverted             | —      |
| any type + `!` or a `BREAKING CHANGE:` footer | Backward-incompatible change              | MAJOR  |

If the project defines a different set of types, use the project's set.

## 3. Format rules

| Part            | Rule                                                                                                                                                                                                            |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Type            | Lowercase                                                                                                                                                                                                       |
| Scope           | Optional lowercase noun (kebab-case) naming the affected area; reuse scopes already present in `git log`; omit when unsure                                                                                      |
| Description     | Imperative mood ("add", not "added"/"adds"); lowercase first letter; no trailing period                                                                                                                         |
| Header length   | Target ≤ 50 characters; hard limit 72, including `type(scope):`                                                                                                                                                 |
| Body            | Separated from the header by one blank line; wrapped at 72 columns; explains **what** and **why**, not how                                                                                                      |
| Footer          | Separated from the body by one blank line; trailer-like format (`Token: value` or `Token #value`); tokens use `-` instead of spaces, except the standard special tokens `BREAKING CHANGE` and `BREAKING-CHANGE` |
| Breaking change | `!` before the colon and/or a `BREAKING CHANGE: <description>` footer (uppercase)                                                                                                                               |
| Issue reference | `Refs: #123`; use the platform's closing keyword only when the commit fully resolves the issue                                                                                                                  |
| Language        | English unless the project uses another language                                                                                                                                                                |

**Release-commit exception:** The release commit and its corresponding PR title `chore(release): <version>`, as defined in `release.md`, are an intentional exception to the imperative-mood and lowercase-first-character guidelines above. In this format, `<version>` is a version literal rather than a prose description. Use this exception only for release-metadata commits and their corresponding PR titles; all other headers must follow the normal description rules.

## 4. Examples

**Good:**

```
fix(session): reject expired tokens on refresh
```

```
feat(search): add prefix matching to the query parser

Allow queries such as "auth*" so users can find related terms without typing them in full.

Refs: #214
```

```
refactor!: drop support for the legacy configuration format

BREAKING CHANGE: configuration files in the v1 format are no longer read. Convert them before upgrading.
```

**Bad:**

| Message                                    | Problem                                                  |
| ------------------------------------------ | -------------------------------------------------------- |
| `fix` / `update` / `wip` / `misc`          | No type/description pairing; says nothing                |
| `feat: Added new feature.`                 | Past tense, capitalized, trailing period, no information |
| `Fix: login bug`                           | Type not lowercase; description vague                    |
| `feat: add search, fix login, update docs` | Three changes in one commit                              |

## 5. Revert format

```
revert: <header of the reverted commit>

Refs: <sha>[, <sha>]
```

## 6. Atomic commit rules

- One logical change per commit. If the description needs "and" to join two different kinds of change, split.
- Keep concerns separate: refactor, formatting, feature, bug fix, and dependency update are separate commits.
- A bug fix and its regression test belong in the same commit.
- Keep each commit independently buildable and testable where practical. Run the applicable project verification before committing; if a required check cannot run, report that limitation rather than claiming it passed.
- Size: target about 100 changed lines; around 300 is usually reviewable. Above 300, assess whether the change can be split; above 1000, normally split unless the changes are tightly coupled or project policy differs.
- Commit messages are written for developers. Changelog entries are written for consumers (see `release.md`).

## 7. Staging and pre-commit checks

1. `git status` — confirm the file list.
2. Stage **explicit paths**. Do not use `git add .`, `git add -A`, or `git commit -a` unless every changed file has been matched to the intended change.
3. `git diff --staged` — read the entire staged diff.
4. Confirm the diff contains no secrets, credentials, private environment values, build output, or unintended large files. Placeholder-only templates such as `.env.example` may be committed when the project expects them and the diff contains no secrets.
5. Run the project's verify commands (test, lint, typecheck, build) appropriate to the change. If no verify command can be found, do not invent one: state that verification was not possible.
6. Commit with hooks enabled. If a hook fails, fix the cause, re-stage, and commit again.
7. `git status` after committing to confirm the result.

## 8. Amend and fixup

| Situation                                                                                                                                   | Action                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| ------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| HEAD commit is confirmed unpublished and was created by the agent in this session; the agent has reliable evidence it has never been pushed | `git commit --amend --no-edit` (use `-m` to change the message) only when the index contains exactly the intended agent-owned change and no pre-existing user-staged changes. Missing upstream configuration alone is not evidence that a commit is unpublished. If uncertain, add a new commit.                                                                                                                                                                                                                                                        |
| An agent-authored commit in the current session that is confirmed unpublished and is not HEAD needs a correction                            | After verifying the index contains only the intended agent-owned correction, run `git commit --fixup=<sha>`, then use `GIT_SEQUENCE_EDITOR=true git rebase -i --autosquash <base>` with the correct base for the unpublished series. Every commit that the rebase would rewrite must be agent-authored in this session and confirmed unpublished. Set `GIT_SEQUENCE_EDITOR` using the active shell syntax. Autosquash before the first push. If ownership, publication status, index state, or the correct base is uncertain, add a new commit instead. |
| The commit is already pushed                                                                                                                | Add a new commit. Never amend or rebase pushed commits                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

## 9. Attribution

Follow the project's convention for marking AI-assisted commits. If the project defines none, add no attribution trailer. Never add `Signed-off-by`: only a human can certify it.

## 10. Repository hygiene

| Item                  | Rule                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `.gitignore`          | Inspect the project's existing ignore policy. Propose a separate `chore:` change only when required by the project and after selecting patterns appropriate to its build output, dependencies, local environment files, keys/certificates, and editor artifacts                                                                                                                                                                                                                             |
| Generated files       | Commit only when the project requires it (for example lockfiles, migrations)                                                                                                                                                                                                                                                                                                                                                                                                                |
| Secrets               | If a secret appears in a proposed diff, stop and do not commit it; tell the user. If it is in an agent-authored commit proven never pushed, correct the local commit only after verifying no pre-existing user-staged state would be changed; remove the secret from versioned changes without deleting a user-owned local file. If publication status is uncertain, ask. If pushed, tell the user to revoke/rotate it and remove it in a follow-up commit; never rewrite published history |
| Large or binary files | Do not commit unless the project's convention allows it                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| Identity              | Do not change `user.name`, `user.email`, or the commit author                                                                                                                                                                                                                                                                                                                                                                                                                               |
| Message content       | No secrets, personal machine paths, or pasted long logs                                                                                                                                                                                                                                                                                                                                                                                                                                     |
