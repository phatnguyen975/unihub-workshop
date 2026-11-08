# Workflows

Each workflow: **Trigger → Steps → Done when**. Workflows tied to the branch lifecycle point to its stages in `branching-and-pr.md` instead of repeating them. Command permissions are defined in `command-policy.md`.

## W1 Session start

**Trigger:** the first git-related action in a session.

1. Take the snapshot (read-only):
   | Need                                           | Command or source                                                                                                                                  |
   | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
   | Working tree state                             | `git status --short --branch`                                                                                                                      |
   | Current branch                                 | `git branch --show-current`                                                                                                                        |
   | Primary remote                                 | `git remote -v` (usually `origin`)                                                                                                                 |
   | Trunk                                          | `git symbolic-ref --short refs/remotes/<remote>/HEAD` (prints `<remote>/<trunk>`); fallback `git remote show <remote>`; if both fail, ask the user |
   | Existing worktrees                             | `git worktree list`                                                                                                                                |
   | Recent history and message pattern             | `git log --oneline -10`                                                                                                                            |
   | Existing stashes                               | `git stash list`                                                                                                                                   |
   | Hooks and commit lint                          | hooks directory, lint config files                                                                                                                 |
   | Verify commands (test, lint, typecheck, build) | project docs, CI configuration                                                                                                                     |
   | Merge method (merge commit, squash, rebase)    | project docs; ask the user when it matters and is unknown                                                                                          |
   | Tag format in use                              | `git tag --list --sort=-creatordate`                                                                                                               |
2. Identify changes that existed before the session (dirty tree rule in `SKILL.md`).
3. If `git status` reports a merge, rebase, cherry-pick, or bisect in progress, start no new work: treat it as W13.

**Done when:** trunk, conventions, verify commands, and pre-existing changes are known, and no operation is left in progress.

## W2 Start task

**Trigger:** a new task that will change code.

1. Run stage 1 (Create) of the branch lifecycle.
2. If the working tree holds changes that are not the agent's, apply the dirty tree rule: use a worktree (`worktrees.md`) or ask.

**Done when:** on a new branch based on the latest trunk tip, working tree clean.

## W3 Develop loop

**Trigger:** implementing a task.

1. Split the task into slices: the smallest change that can be verified on its own.
2. Implement one slice.
3. Verify with the project's commands.
4. Run the pre-commit checks and commit (`commit-convention.md` section 7).
5. Repeat for the next slice.

If verification fails, fix the cause, or restore only the files the agent just changed: `git restore --source=HEAD --staged --worktree -- <path>`. Never use `reset --hard` to recover.

**Done when:** all slices are committed and verified.

## W4 Fix bug

1. Reproduce the bug, preferably with a failing test.
2. Fix it; confirm the test passes.
3. Commit as `fix:` with the fix and its regression test together. The body states the root cause and the affected scope.

**Done when:** the bug no longer reproduces and a regression test exists (or the commit states why none is possible).

## W5 Refactor

1. Confirm existing tests cover the area; if not, add tests first in a separate commit.
2. Commit as `refactor:` with no behavior change; keep it out of feature or fix commits, ideally out of the same PR.
3. Confirm behavior is unchanged by running the verify commands.

**Done when:** tests pass before and after, and the diff contains no behavior change.

## W6 Organize a messy working tree

**Trigger:** the user explicitly asks to organize/commit a set of existing uncommitted changes. Do not infer permission to commit pre-existing user changes merely because the tree is dirty.

1. Read `git status` and `git diff`.
2. Group changes by logical intent.
3. Stage each group by explicit path. When one file mixes concerns, write a patch containing only the intended hunks and apply it with `git apply --cached <patch>`.
4. Commit each group with a conventional message.
5. Confirm the tree is clean and nothing was left behind.

**Done when:** `git status` is clean and every commit is atomic.

## W7 Sync with trunk and resolve conflicts

**Trigger:** trunk has new commits in a relevant area, or a sync produced conflicts.

1. Apply the sync rules (`branching-and-pr.md` section 2).
2. For each conflict: read both sides and the commit that introduced each; resolve minimally while keeping the intent of both; never take `--ours` or `--theirs` wholesale without understanding the files.
3. Run the verify commands.
4. If a conflict is semantic and the intent is unclear, preserve the conflict state, explain the unresolved decision, and ask the user. Do not abort until the relevant state is preserved and the abort is confirmed as the safe next step under W13.

**Done when:** the branch contains the latest trunk and verification passes.

## W8 Push and open PR

1. Run stage 3 (Publish) of the branch lifecycle.
2. Open the PR following `branching-and-pr.md` sections 4 and 5, using the platform's CLI or API tool if available; otherwise output the title and body.
3. If the push is rejected because the remote branch has new commits: `git fetch`, `git merge --no-edit origin/<name>`, re-verify, push again. Never force.
4. If the project has no remote, stop after the commits and report `not pushed: no remote`.

**Done when:** the branch is on the remote and the PR exists (or its text was handed over), or the reason for stopping was reported.

## W9 Address review feedback

1. Run stage 4 (Review) of the branch lifecycle.
2. One new commit per group of feedback, using a conventional message.
3. Push; update the PR description if its content changed; summarize the changes for the reviewer.

**Done when:** every comment is addressed or answered, and checks pass.

## W10 Post-merge cleanup

Run stages 5 (Merged) and 6 (Cleanup) of the branch lifecycle.

## W11 Hotfix

1. Reproduce and implement the fix on a normal `fix/<desc>` branch created from trunk; merge it into trunk through the normal PR workflow. Do not commit directly to trunk.
2. If a released version on a release branch also needs the fix, follow the patch flow in `release.md`.
3. Never implement the fix directly on the release branch and merge it back into trunk.

**Done when:** the fix is on trunk and, where required, on the release branch via cherry-pick.

## W12 Revert

1. Create `revert/<desc>` from the latest trunk tip.
2. `git revert --no-commit <sha>` (for a merge commit, determine and confirm the mainline parent before adding `-m <parent>`).
3. Inspect the staged diff and run the project verification commands.
4. Commit non-interactively in the revert format (`commit-convention.md` section 5), for example `git commit -m "revert: <reverted-header>" -m "Refs: <sha>"`; replace the placeholders with the actual header and SHA, then push and open a PR.

Never delete published commits with `reset` or a force push.

**Done when:** the revert PR is open and verification passes.

## W13 Recovery

**Trigger:** lost changes, a wrong operation, or an unexpected repository state.

1. Stop write operations and inspect `git status`, the current operation state, and `git reflog`.
2. Before any restore/abort that could overwrite content, preserve the current diff and any potentially valuable untracked files in a safe snapshot outside the affected working tree; inspect the exact paths and intended effect. Do not overwrite or discard user data.
3. Distinguish lost commits/ref movement from uncommitted file contents: reflog can help locate earlier ref/commit positions but is not a backup of arbitrary uncommitted bytes. If uncommitted content is missing, check editor local history, backups, snapshots, or file-recovery options; do not promise Git can recover it.
4. Report the observed state and proposed recovery action to the user before any uncertain or potentially destructive step. Ask for clarification when intent or impact is unclear, and obtain any authorization required by `command-policy.md`.
5. Once the recovery path is clear and permitted, recover a commit by creating a branch at the verified SHA (`git branch chore/recover-<desc> <sha>`) or restore only a specified path from a verified source (`git restore --source=<sha> -- <path>`). Abort an unfinished merge/rebase/cherry-pick only after checking and preserving the current state and confirming that aborting is appropriate.

**Done when:** recovered data is verified, or the limits and next recovery options are reported without overstating what Git can restore.

## W14 Investigate history

1. Start with read-only commands: `git log -S<text>`, `git log -G<regex>`, `git log --grep=<text>`, `git blame`, `git show`.
2. `git bisect` changes the checkout and can change working-tree contents. Use it only in a dedicated worktree or a clean checkout, as permitted by `command-policy.md`; record the starting state and always finish with `git bisect reset`, then verify the original branch/commit and working-tree state.

**Done when:** the responsible commit or line origin is identified and the repository is back to its original state.
