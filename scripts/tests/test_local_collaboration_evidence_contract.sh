#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output_file="$(mktemp)"
trap 'rm -f "$output_file"' EXIT

"$repo_root/scripts/test-local-collaboration-evidence.sh" > "$output_file" 2>&1 \
  || { cat "$output_file" >&2; exit 1; }

for marker in \
  'COLLAB-LOCAL-001 passed: local PR branch was reviewed and fast-forwarded into disposable main' \
  'REBASE-LOCAL-002 passed: actual interactive autosquash rewrote two unpublished commits into one' \
  'range-diff: verified' \
  'remote mutations: none'; do
  grep -Fq "$marker" "$output_file" \
    || { echo "local collaboration evidence missing marker: $marker" >&2; cat "$output_file" >&2; exit 1; }
done

echo "COLLAB-CONTRACT-001 passed: local PR and autosquash/rebase proof is executable and remote-free"
