#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output_file="$(mktemp)"
trap 'rm -f "$output_file"' EXIT

CRISIS_RECOVERY_TARGET=test-hooks \
  "$repo_root/scripts/test-crisis-evidence.sh" > "$output_file" 2>&1 \
  || { cat "$output_file" >&2; exit 1; }

grep -Fq 'branch:           main' "$output_file" \
  || { echo "CRISIS-CONTRACT-001 failed: disposable defect did not land on branch main" >&2; cat "$output_file" >&2; exit 1; }
grep -Fq 'restored tree:    verified equal to release candidate' "$output_file" \
  || { echo "CRISIS-CONTRACT-001 failed: exact tree restoration was not asserted" >&2; cat "$output_file" >&2; exit 1; }
grep -Fq 'regression correctly detected (exit non-zero)' "$output_file" \
  || { echo "CRISIS-CONTRACT-001 failed: regression failure was not observed" >&2; exit 1; }
grep -Fq 'recovery confirmed' "$output_file" \
  || { echo "CRISIS-CONTRACT-001 failed: post-revert recovery gate was not green" >&2; exit 1; }

echo "CRISIS-CONTRACT-001 passed: disposable main regression, revert, and exact tree restoration are executable"
