# CodeGraph Commands Reference

Covers the non-setup commands — the ones used while actually working a task. Setup-only commands (`init`, `install`, `uninstall`, `uninit`) are in `references/setup.md`.

Flags shown below are confirmed from the official CodeGraph CLI reference. For anything not listed here, or if a flag doesn't behave as expected, run `codegraph help <command>` for the current, authoritative syntax rather than assuming.

## MCP vs. CLI Availability

Only one tool is exposed over MCP by default: `codegraph_explore` (mirrors the `explore` CLI command's output exactly). Every other command below is reached via the CLI regardless of whether MCP is ready for the current agent — don't wait for or try to enable additional MCP tools mid-task; just shell out to the CLI equivalent.

## Commands

### `codegraph explore <query>`

One-shot: returns relevant symbols' source plus call paths in one call — same output as the `codegraph_explore` MCP tool. The default first move for most questions — broad enough to often replace several narrower queries.

### `codegraph node <symbol|file>`

One symbol's source plus its caller/callee trail, or — given a file path — the whole file read with line numbers (same output as the `codegraph_node` MCP tool). Use when you already know exactly which symbol or file you need, rather than asking a broader question.

### `codegraph query <search>`

Search symbols by name/signature across the whole indexed codebase — not a general string/content search (use `grep` for that). Flags: `--kind`, `--limit`, `--json`.

### `codegraph callers <symbol>`

One hop: everything that calls the given symbol. Flags: `--limit`, `--json`.

### `codegraph callees <symbol>`

One hop: everything the given symbol calls. Flags: `--limit`, `--json`.

### `codegraph impact <symbol>`

Analyzes what code is affected by changing a symbol — transitive blast radius. Flags: `--depth`, `--json`. Run this before renaming a symbol or changing its signature.

### `codegraph affected [files...]`

Traces import dependencies transitively to find which test files are affected by a set of changed source files. The core command for Code Review and CI/Test Impact use cases.

```bash
codegraph affected src/utils.ts src/api.ts           # files as arguments
git diff --name-only | codegraph affected --stdin    # piped from git diff
codegraph affected src/auth.ts --filter "e2e/*"      # custom test-file pattern
```

Flags: `--stdin` (read the file list from stdin — required when piping, not automatic), `-d, --depth <n>` (max dependency traversal depth, default `5`), `-f, --filter <glob>` (custom glob to identify test files, default auto-detect), `-j, --json`, `-q, --quiet` (file paths only).

### `codegraph files [path]`

Shows the indexed file structure for a path — faster than a filesystem scan for understanding layout. Flags: `--format`, `--filter`, `--pattern`, `--max-depth`, `--json`.

### `codegraph status [path]`

Index health and coverage: file/symbol statistics. Flag: `--json`. Also doubles as a CLI-readiness probe (see `references/setup.md`) since it fails cleanly if the CLI isn't on PATH or the project isn't indexed.

## Output Shape Notes

- `explore` and `node` return actual source text, not just structural facts — treat their output as equivalent to having read the file(s) directly.
- Results may carry a staleness indicator when the underlying file changed very recently and the index hasn't caught up yet (the file watcher syncs automatically, but only while it's running). When present, either read that specific file directly, or run `codegraph sync` to force an incremental refresh of the index before re-querying.
- `query`, `callers`, `callees`, `impact`, `affected`, `files`, and `status` all support `--json` for structured parsing when a use case needs to process the result programmatically rather than read it as text.

## Troubleshooting

- `codegraph unlock [path]` — removes a stale lock file that's blocking indexing, if a prior `init`/`index`/`sync` was interrupted.
- `codegraph index [path]` — full re-index from scratch (`--force`, `--quiet`, `--verbose`); only needed when the file watcher is disabled or the index is being driven outside an agent session. Routine use doesn't need this — `init` already builds the full graph on its own.
