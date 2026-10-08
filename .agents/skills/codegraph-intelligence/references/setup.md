# CodeGraph Setup & Interface Resolution

Source of truth for every command on this page: the official [`colbymchenry/codegraph`](https://github.com/colbymchenry/codegraph) project (CLI + MCP server). Do not use commands from forks or reimplementations under similar names.

## Detection

| Signal      | How to check                                                                                         | Verifiable how                                                                       |
| ----------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `CLI_READY` | Run a harmless CLI command, e.g. `codegraph status` or `codegraph version`                           | Directly — the command either succeeds or fails                                      |
| `MCP_READY` | Check whether the agent's own current tool list already contains a tool matching `codegraph_explore` | Only this way — no CLI command reports another agent process's MCP wiring/load state |

## Decision Matrix

### Case A — MCP ready AND CLI ready

Proceed directly to project-level initialization below. Nothing to set up.

### Case B — CLI ready, MCP not ready

Propose MCP setup as optional (recommended, not mandatory):

> "CodeGraph's CLI is available, but MCP isn't wired for this agent yet. Setting it up lets this agent use CodeGraph as a native tool. Want me to walk you through it?"

- **If accepted:** Hand the human the "Non-interactive install" command in step 2 below, wait for their confirmation that it ran, then tell them to restart their session so the MCP tool loads.
- **If declined:** Proceed on the CLI only. Nothing else to do.

### Case C — CLI not ready

Hand the human the install instructions in step 1 below — either the `npx @colbymchenry/codegraph` shortcut (installs the CLI and offers agent wiring interactively, in one go), or the separate CLI-only commands if they want to keep agent wiring as a distinct step. If they end up wiring an agent, remind them to restart their session afterward. Wait for their confirmation, then proceed. If they report the CLI still isn't resolving, suggest opening a new terminal/shell — none of the install methods refresh the PATH of an already-running shell.

## Official Commands

### 1. Install

Two equally official ways to get the CLI and, optionally, agent wiring — pick either.

**Shortcut (installs the CLI and wires agents in one interactive flow):**

```bash
npx @colbymchenry/codegraph
```

**Or as separate steps (useful for a CLI-only install with no agent prompts, or to control each step independently):**

```bash
# 1. Install the CLI
curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh | sh   # macOS/Linux
irm https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.ps1 | iex        # Windows PowerShell
npm i -g @colbymchenry/codegraph                                                           # npm

# 2. Wire agents, once the CLI is on PATH
codegraph install
```

Either path wires agents only — it does not index any project's code (see step 3).

Confirmed auto-detected agents (9): Claude Code, Cursor, Codex CLI, opencode, Hermes Agent, Gemini CLI, Antigravity IDE, Kiro, and GitHub Copilot (as `copilot-vscode`, `copilot-cli`, `copilot-jetbrains`). Behavior per agent:

- Writes that agent's own MCP server config, in its own format.
- Adds a short marker-fenced CodeGraph section to the agent's instructions file (`CLAUDE.md`/`AGENTS.md`/`GEMINI.md`) — except Cursor and Kiro, which get the MCP config only.
- Sets up Claude Code's auto-allow permissions when Claude Code is one of the targets (skip with `--no-permissions`). If the current agent isn't in the 9 above, don't attempt to hand-write an MCP config for it — fall back to CLI-only use and say so.

### 2. Non-interactive install (scripting / CI)

```bash
codegraph install --yes                              # auto-detect agents, install global
codegraph install --yes --init                       # same, then build the current project's index
codegraph install --target=cursor,claude --yes       # explicit target list
codegraph install --target=auto --location=local     # detected agents, project-local
codegraph install --target=copilot-vscode,copilot-cli,copilot-jetbrains --yes  # GitHub Copilot everywhere
codegraph install --print-config codex               # print snippet, no file writes
codegraph install --print-config copilot-vscode      # same, for Copilot in VS Code
```

| Flag                  | Values                                                               | Default           |
| --------------------- | -------------------------------------------------------------------- | ----------------- |
| `--target`            | `auto`, `all`, `none`, or csv (`claude,cursor,...`)                  | prompt            |
| `--location`          | `global`, `local`                                                    | prompt            |
| `--yes`               | boolean                                                              | prompt every step |
| `--init`              | boolean — run `codegraph init` in the current directory after wiring | —                 |
| `--no-permissions`    | boolean — skip Claude auto-allow list                                | permissions on    |
| `--print-config <id>` | dump snippet for one agent and exit                                  | —                 |

### 3. Initialize the current project

```bash
codegraph init
```

Creates `.codegraph/` and builds the full graph in this one command — a separate index-build step isn't needed. This is local and non-destructive. Safe to run directly once the interface is resolved, without needing a separate human confirmation. Run once per project; a single `codegraph install` already covers every project on the machine.

After `init`, the file watcher keeps the graph current automatically for as long as an agent session with it running is open.

### 4. Reversing setup (only if asked)

```bash
# Removes CodeGraph's MCP config/instructions/permissions from every agent
# it configured AND the CLI itself, by default — project indexes are left
# untouched. --keep-cli removes only the agent configs; --target limits
# this to specific agents; --yes runs non-interactively.
codegraph uninstall

# Remove one project's local index (.codegraph/) — not run automatically
codegraph uninit
```
