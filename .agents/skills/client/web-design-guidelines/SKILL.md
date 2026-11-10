---
name: web-design-guidelines
description: "Client: audit web UI code against interface guidelines."
metadata:
  author: vercel
  version: "1.0.0"
  argument-hint: <file-or-pattern>
---

## Project Invocation

Client scope only. Read `.agents/rules/client.md` and the matching web/mobile rules, then use the approved task/baseline and caller mode. Project invocation policy takes precedence over defaults/examples below. Web code audit only. Save the fetched guideline snapshot/hash/time for the audit, reconcile it with the approved web baseline and merge findings into the caller's review.

# Web Interface Guidelines

Review files for compliance with Web Interface Guidelines.

## How It Works

1. Fetch the latest guidelines from the source URL below
2. Read the specified files (or prompt user for files/pattern)
3. Check against all rules in the fetched guidelines
4. Output findings in the terse `file:line` format

## Guidelines Source

Fetch fresh guidelines before each review:

```
https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md
```

Use WebFetch to retrieve the latest rules. The fetched content contains all the rules and output format instructions.

## Usage

When a user provides a file or pattern argument:
1. Fetch guidelines from the source URL above
2. Read the specified files
3. Apply all rules from the fetched guidelines
4. Output findings using the format specified in the guidelines

If no files specified, ask the user which files to review.
