# Release

Run only when the user asks for a release.

## 1. Release components

Every release includes all three components. A release missing any of them is incomplete.

| Component     | Rule                                                                              | Source of truth         |
| ------------- | --------------------------------------------------------------------------------- | ----------------------- |
| **Version**   | `<MAJOR>.<MINOR>.<PATCH>` per SemVer; bump derived from the changes (section 3)   | Tag                     |
| **Changelog** | An entry for the version, written for consumers and grouped by impact (section 4) | The project's changelog |
| **Tag**       | Annotated tag on the release commit; immutable once published (section 5)         | Git                     |

Tag, version, and changelog must agree. Derive the version from the tag; do not hand-edit it in several places.

## 2. Preconditions

Fetch the primary remote first and verify the intended trunk ref/SHA. Trunk must be current, verification/CI must be green for the intended release revision, and the working tree must be clean. Identify the previous release tag using the project's tag naming convention. For example, use `git describe --tags --abbrev=0 origin/<trunk>` when all eligible tags are release tags; if the repository also uses non-release tags, select the latest valid release tag explicitly. If no prior tag exists, follow section 3 and ask the user for the starting version; do not assume a baseline. If the remote cannot be refreshed, the target revision is unclear, or required verification fails, stop and tell the user.

## 3. Version determination

Analyze `git log <last-tag>..origin/<trunk>`:

| Change signal / impact                                                     | Default handling for projects using SemVer and Conventional Commits                                                                                                                                                               |
| -------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Confirmed breaking change (`!` in the header or `BREAKING CHANGE:` footer) | MAJOR for stable versions `1.0.0` and later, subject to the project's release policy                                                                                                                                              |
| `feat` with new backward-compatible functionality                          | MINOR for stable versions `1.0.0` and later                                                                                                                                                                                       |
| `fix` with a backward-compatible bug fix                                   | PATCH for stable versions `1.0.0` and later                                                                                                                                                                                       |
| Other types (`perf`, `security`, `refactor`, `build`, `docs`, etc.)        | Conventional Commits assigns no implicit SemVer bump to these types. Inspect the actual change and the project's release policy; classify consumer impact rather than treating the type alone as proof that no release is needed. |

Use commit headers as signals, not as a substitute for inspecting the changes. Select the highest applicable impact. If a change may be breaking but evidence is inconclusive, describe the uncertainty and ask the user before choosing a version. For `0.y.z` projects or custom/pre-release schemes, follow the project convention; if none is documented, propose a bump and obtain confirmation. If no tag exists, analyze the history and ask the user for the starting version. If the history does not follow Conventional Commits, inspect the changes and ask the user to confirm the proposed bump.

## 4. Changelog

| Item             | Rule                                                                                                                                                                                                |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Format           | Follow the project's existing changelog. If none exists, create `CHANGELOG.md` with the groups `Added / Changed / Deprecated / Removed / Fixed / Security`, newest version first                    |
| Content          | Written for consumers, by impact; never a pasted commit log                                                                                                                                         |
| When to write    | Where the project maintains the changelog by hand: add the entry under `Unreleased` in the same PR as the consumer-facing change. At release time, rename `Unreleased` to the version with its date |
| Source           | The commit log is only input for drafting missing entries; edit by impact afterwards                                                                                                                |
| Breaking changes | Include migration instructions and the deprecation window the project planned                                                                                                                       |

## 5. Tag and naming

| Item             | Rule                                                                                                                                                                                                                                                                                                                                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Type             | Annotated: `git tag -a <tag> <sha> -m "Release <version>"`                                                                                                                                                                                                                                                                                                                                              |
| Format           | `v<MAJOR>.<MINOR>.<PATCH>` — always three components, even when `PATCH` is 0; follow the project's format if it already uses another                                                                                                                                                                                                                                                                    |
| Target           | The actual commit on the target branch that contains the final release version/changelog after merge (or the corresponding commit on the release branch for a patch release). Resolve the SHA after the PR merge method has completed; verify it is reachable from the target branch and that the release files match the tag. Do not tag the pre-merge PR head or assume the target is the branch tip. |
| Publish          | Push **exactly one tag**: `git push origin <tag>`; never `--tags`                                                                                                                                                                                                                                                                                                                                       |
| Immutability     | A published tag is never moved or deleted                                                                                                                                                                                                                                                                                                                                                               |
| Version in files | If the project derives its version from the tag, edit no file. If it stores the version in files, update every location in the release commit so each value matches the tag                                                                                                                                                                                                                             |

**Naming for release-related objects:** For stable releases, `<version>` is the full `<MAJOR>.<MINOR>.<PATCH>` version without the leading `v`. A release maintenance branch identifies a version line, not a specific release version.

| Object                                            | Format                      | Example                 | Meaning                                                                                                                                       |
| ------------------------------------------------- | --------------------------- | ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Stable version                                    | `<MAJOR>.<MINOR>.<PATCH>`   | `1.4.0`                 | Identifies one specific stable release.                                                                                                       |
| Git tag                                           | `v<version>`                | `v1.4.0`                | Identifies the released commit. A published tag is immutable.                                                                                 |
| Release PR branch (short-lived)                   | `chore/release-<version>`   | `chore/release-1.4.0`   | One branch per release-metadata PR. Targets trunk for a standard release or the corresponding release maintenance branch for a patch release. |
| Release commit and PR title                       | `chore(release): <version>` | `chore(release): 1.4.0` | The release-specific exception documented in `commit-convention.md`.                                                                          |
| Release maintenance branch (long-lived, optional) | `release/<MAJOR>.<MINOR>`   | `release/1.4`           | Maintains the corresponding version line, such as `1.4.x`, across multiple patch releases.                                                    |
| Patch release tag                                 | `v<version>`                | `v1.4.1`                | Identifies a specific patch release on the corresponding release maintenance branch.                                                          |

These are the default naming conventions for stable releases. If the project supports pre-release versions or uses a different versioning convention, follow the project's existing convention consistently across version files, branches, tags, commits, and release PRs.

## 6. Release flow

1. Check preconditions (section 2); determine the version (section 3); tell the user the version and the list of changes.
2. Create branch `chore/release-<version>` from trunk.
3. Update the changelog (section 4) and any file that stores the version (section 5).
4. Create the non-interactive commit `git commit -m "chore(release): <version>"`; push; open a PR with trunk as base.
5. A human merges the PR.
6. After the user confirms the merge, `git fetch` and verify the resulting release commit and release files on the target branch. For squash/rebase/merge-commit workflows, identify the actual resulting commit on that target branch; never assume the source-branch PR head SHA survived the merge.
7. Create `git tag -a v<version> <verified-target-sha> -m "Release <version>"`, confirm the tag resolves to that SHA, then push exactly that tag under R7. The release PR must be confirmed merged before the tag is published.
8. Other publish steps (artifacts, platform release notes) run only as the project documents them and only when the user explicitly asks.

## 7. Release maintenance branch and patch release

| Item            | Rule                                                                                                                                                                                                                                                                                                                                                                                          |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| When            | Use only when the project maintains a supported release line or explicitly requires patching an older version while trunk has moved on. Do not create a release maintenance branch automatically for every release.                                                                                                                                                                           |
| Create          | `release/<MAJOR>.<MINOR>` from the exact released tag or commit (for example, `release/1.4` from `v1.4.0`). A human creates and pushes the maintenance branch; the agent prepares the commands.                                                                                                                                                                                               |
| Fix source      | Implement and test the fix on a normal `fix/<description>` branch from trunk, then merge it into trunk through a PR. After that, create a distinct, unused backport branch such as `fix/<description>-release-<MAJOR>.<MINOR>` from the release maintenance branch, run `git cherry-pick -x <sha>` for the verified trunk fix commit, and open a PR targeting the release maintenance branch. |
| Merge direction | Trunk → release branch by cherry-pick only. A release branch is never merged back into trunk                                                                                                                                                                                                                                                                                                  |
| Direct commits  | No direct commits or pushes to shared trunk/release branches. Prepare the backport and release-metadata commits on short-lived branches and merge them by PR                                                                                                                                                                                                                                  |
| Tag             | After the backport PR and the release-metadata PR have both been merged, create the next patch tag (for example `v1.4.1`) on the verified post-merge commit, per section 5                                                                                                                                                                                                                    |
| Changelog       | Update the patch entry to the released version on the release branch. Reflect the released patch on trunk through a separate PR/new commit according to project policy, never by reverse-cherry-picking the release branch                                                                                                                                                                    |
| Lifecycle       | A human deletes the branch when releases are no longer made from it                                                                                                                                                                                                                                                                                                                           |

### Patch-release sequence

1. Merge the fix into trunk through the normal PR workflow and verify the resulting trunk commit.
2. Create a distinct backport branch from `release/<MAJOR>.<MINOR>`, cherry-pick the verified trunk fix with `-x`, run the project's verification commands, then push and open a PR targeting the release branch.
3. After a human merges the backport PR, fetch and confirm the fix is present on the release branch.
4. Create `chore/release-<version>` from the updated release branch. Update the release branch's changelog entry and any version-storing files, commit `chore(release): <version>`, then push and open a second PR targeting the release branch.
5. After a human merges the release-metadata PR and the user confirms the merge, fetch and verify the actual post-merge release-branch commit contains the fix, version, and changelog. Create the annotated tag on that SHA and push exactly that tag under R7. Never tag the pre-merge PR head.
6. Reflect the patch release in trunk through a separate PR/new commit following the project's changelog/version policy. Do not reverse-cherry-pick the release branch into trunk.
