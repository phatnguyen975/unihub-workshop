---
name: agent-device
description: "Client native: inspect and operate physical-device native UI."
---

## Project Invocation

Client scope only. Read `.agents/rules/client.md` and the matching web/mobile rules, then use the approved task/baseline and caller mode. Project invocation policy takes precedence over defaults/examples below. Use physical native targets only. The immediate-open shortcut below applies only after the current physical target and project have been proven; discovery and setup checks are permitted when that prerequisite is missing. Never use `close --shutdown` on a user-owned physical device. Prove host/device access at initial setup or capability change, then bind the correct target. Do not auto-boot emulators. A viewed screenshot with coordinate actions and verified outcomes can work when AX is sparse. If reliable access is absent, finish code checks and prepare human native UI review; never substitute browser/component results.

# agent-device

For a normal app-driving task, start immediately. Do not probe first with `--help`, `--version`, `devices`, `appstate`, `snapshot`, or `screenshot`:

```bash
agent-device open <app> --foreground
```

That starts the session and returns the initial interactive snapshot with `@refs`.

Loop: act with `press|click|fill|longpress <target> ... --settle`, `scroll <direction> --settle`, or `back --settle`; continue from the printed diff, verify the named expectation (`wait text "..."`, `is`, `get`, or `find`), then run `agent-device close`, or `agent-device close --shutdown` if your session booted a local simulator or emulator, unless the user asks to keep it running.

Reaching an off-screen target is one command, not a scroll-and-check loop: `scroll down --until <selector>` scrolls until that element is on screen, and `scroll bottom` runs to the end of the content. Repeated bare `scroll down` calls are the slow way to find something.

Copy refs byte-for-byte: `@e12`, `@e12~s4` — keep the `@` and any `~sN`. Prefer current refs, then `id`/`label`/`role` selectors; coordinates are a last resort. If snapshot reports sparse/AX-unavailable, its refs and selectors are invalid: run `agent-device screenshot`, inspect the image, use coordinates, then retry `snapshot -i` after navigating. Otherwise run `snapshot -i` only when the diff lacks the next target.

Error output includes corrective hints; follow them instead of re-planning. Only when the task is specialized (for example gestures, scripting, TV, macOS, remote, or debugging) or a command shape is unclear, run `agent-device help <topic>`. `agent-device --help` lists topics, but is not a startup step.

To capture network traffic on a Limrun session, see `agent-device help remote` or the Limrun docs.
