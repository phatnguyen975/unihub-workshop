# Continuous Integration

## Workflow Responsibilities

| Location                   | Responsibility                                                                  |
| -------------------------- | ------------------------------------------------------------------------------- |
| `workflows/ci-gate.yml`    | PR scope detection, application workflow orchestration and required merge check |
| `workflows/ci-backend.yml` | Backend secret scanning and unit tests                                          |
| `workflows/ci-workers.yml` | Workers secret scanning and unit tests                                          |
| `workflows/ci-web.yml`     | Web secret scanning, lint, type checks, tests, build and browser verification   |
| `ci/scopes.cjs`            | Application selection and gate evaluation                                       |
| `ci/scopes.test.cjs`       | Scope-selection and gate-policy regression tests                                |

Paths in this table are relative to `.github/`. Application tooling owns the commands executed by its workflow.

## Pull Request Checks

`ci-gate.yml` runs on every pull request targeting `main`. It compares the PR head against the merge base with its base commit, including deleted paths and both sides of renames. Scope detection uses the complete Git diff rather than a truncated file list from an API or workflow path filter.

| Changed Paths                                                      | Selected Workflows      |
| ------------------------------------------------------------------ | ----------------------- |
| `apps/backend/**`, `ci-backend.yml`                                | Backend                 |
| `apps/workers/**`, `ci-workers.yml`                                | Workers                 |
| `apps/web/**`, `ci-web.yml`                                        | Web                     |
| `.gitleaks.toml`, `.gitattributes`, `ci-gate.yml`, `.github/ci/**` | All three               |
| Documentation, mobile or other paths alone                         | No application workflow |

Workflow filenames in this table are under `.github/workflows/`. Multiple selected application workflows run in parallel. Only applications registered in the scope policy have application verification; unregistered paths receive scope-policy and gate checks only.

Application workflows expose `workflow_call` and are invoked only for selected scopes. Each selected workflow runs its secret scan before its verification suite. PRs without selected applications run scope-policy tests and the final gate without installing application toolchains.

`CI Gate` runs after scope detection and selected workflows finish. It requires successful detection, successful selected workflows and skipped unselected workflows. Failure, cancellation, missing outputs or unexpected skips fail the gate. Dependencies belong to the same workflow run and PR revision; no result polling or reuse of an older commit's checks is involved. New PR commits cancel the previous orchestration run.

## Main and Manual Runs

Each application workflow also runs directly on pushes to `main` matching its app, its own workflow or shared CI paths. A manual dispatch can run an individual application workflow. These runs verify the application but do not replace the PR's required `CI Gate`.

## Branch Protection

Configure the rule targeting `main` through **Settings → Branches → Branch Protection Rules**, or through **Settings → Rules → Rulesets**.

1. Enable **Require a pull request before merging**.
2. Enable **Require status checks to pass before merging**.
3. Add the exact check name **`CI Gate`**, provided by GitHub Actions when a source selector is available.
4. Use `CI Gate` as the sole required CI check. Application jobs are conditional and are not individual merge requirements.
5. Enable **Require branches to be up to date before merging** so base-branch updates require verification against the updated base.
6. Save the rule.

The check must have been emitted by a PR run to be available in the check selector. Passing CI does not replace review or artifact approval. This configuration uses PR updates with branch freshness enforcement; merge queues require a separately configured `merge_group` trigger and scope policy.

## Registering an Application

Add application CI as one coordinated configuration change:

1. Define its reusable workflow with `workflow_call`, scoped `push` paths and `workflow_dispatch`.
2. Register its scope and affected paths in `ci/scopes.cjs`, including shared CI paths.
3. Add its scope output and conditional workflow call to `workflows/ci-gate.yml`.
4. Add the workflow to the final gate's dependencies and evaluation policy.
5. Cover scope selection, successful verification, failure, cancellation and unexpected skips in the policy tests.
6. Update the workflow registry, trigger documentation, application tooling guide and relevant README badge.

Keep application job names distinct across workflows. Preserve the stable `CI Gate` check name so application registration does not require changing branch protection.

## Local Policy Verification

Run `node --test .github/ci/scopes.test.cjs` from the repository root. These tests cover scope selection and gate decisions without installing app dependencies. Application verification commands remain owned by their respective app tooling.
