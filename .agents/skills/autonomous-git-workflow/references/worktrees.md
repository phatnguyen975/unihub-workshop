# Worktrees

A worktree is an additional working directory attached to the same project, with its own checked-out branch.

## When to use

- Several tasks run in parallel
- The working tree holds uncommitted changes that belong to the user
- A PR needs to be checked out in isolation
- Running `git bisect`
- A risky experiment must not touch the current checkout

## Rules

| Item                    | Rule                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Create                  | `git worktree add --no-track -b <type>/<desc> <path> origin/<trunk>`                                                                                                                                                                                                                                                                                                                                                                                 |
| Location                | A directory outside the main working tree (for example `../<project>-<branch-slug>`), or a directory the project designates that is git-ignored                                                                                                                                                                                                                                                                                                      |
| Constraint              | A branch can be checked out in only one worktree. Never use `--force` to bypass this                                                                                                                                                                                                                                                                                                                                                                 |
| Shared across worktrees | Object database, refs, remotes, config, hooks, stashes                                                                                                                                                                                                                                                                                                                                                                                               |
| Not shared              | Untracked and ignored files (local environment files, dependencies, build output). Prepare the environment per project docs; copy local environment files only when the project or user explicitly directs it; never commit files containing secrets. Placeholder-only templates such as `.env.example` may be committed when the project expects them and they contain no secrets                                                                   |
| Agent ownership         | One agent per worktree. Two agents never work in the same worktree. Optionally `git worktree lock --reason "<agent/task>"` for long runs                                                                                                                                                                                                                                                                                                             |
| Verify after creating   | `git worktree list` and `git -C <path> branch --show-current`                                                                                                                                                                                                                                                                                                                                                                                        |
| Cleanup                 | Before removal, inspect `git -C <path> status --short --ignored`; preserve valuable user/local data, including ignored environment files, outside the worktree. Follow the Cleanup order in `branching-and-pr.md`. `git worktree prune -n` is repository-wide preview, not a per-worktree selector; execute `git worktree prune` only under R10 after verifying every listed stale entry is an agent-created worktree that was intentionally removed |
| Limits                  | Remove only worktrees the agent created. `git worktree remove --force` requires an explicit user request and the checks in R9. Do not prune unknown or other-agent worktree metadata                                                                                                                                                                                                                                                                 |

## Procedure

1. Create the worktree and branch with the command above.
2. Prepare the environment following project docs.
3. Run the normal workflow (W3 onward) inside the worktree path.
4. After the PR is merged, clean up per `branching-and-pr.md` section 3.
