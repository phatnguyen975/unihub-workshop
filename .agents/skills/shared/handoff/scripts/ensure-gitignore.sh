#!/usr/bin/env bash
# Ensure HANDOFF.md is listed in .gitignore at the given directory (default: current directory).
# Idempotent: safe to run multiple times, never duplicates the entry.
#
# Usage:
#   ./ensure-gitignore.sh [target_dir]
#
# Exit codes:
#   0 - success (created .gitignore, appended entry, or entry already present)
#   1 - target directory does not exist

set -euo pipefail

ENTRY="HANDOFF.md"
TARGET_DIR="${1:-.}"
GITIGNORE_PATH="$TARGET_DIR/.gitignore"

if [ ! -d "$TARGET_DIR" ]; then
  echo "ERROR: directory not found: $TARGET_DIR" >&2
  exit 1
fi

if [ ! -f "$GITIGNORE_PATH" ]; then
  echo "$ENTRY" > "$GITIGNORE_PATH"
  echo "Created $GITIGNORE_PATH and added '$ENTRY'."
  exit 0
fi

# Exact, whole-line match — avoids false positives from substrings or patterns like *.md
if grep -qxF "$ENTRY" "$GITIGNORE_PATH"; then
  echo "'$ENTRY' is already present in $GITIGNORE_PATH — no change made."
  exit 0
fi

printf '\n%s\n' "$ENTRY" >> "$GITIGNORE_PATH"
echo "Appended '$ENTRY' to $GITIGNORE_PATH."
