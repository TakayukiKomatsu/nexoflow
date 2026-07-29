#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
validator="$repo_root/scripts/validate-traceability.sh"
traceability="$repo_root/docs/REQUIREMENT_TRACEABILITY.md"
fixture_dir="$(mktemp -d)"
trap 'rm -rf "$fixture_dir"' EXIT

"$validator" >/dev/null

missing_path_fixture="$fixture_dir/missing-path.md"
awk '
  {
    if (index($0, "| UI-SIM-002 |") > 0) {
      sub(/`frontend\/src\/App.test.tsx`/, "`frontend/src/App.test.tsx`; `frontend/src/DoesNotExist.tsx`")
    }
    print
  }
' "$traceability" >"$missing_path_fixture"

if TRACEABILITY_FILE="$missing_path_fixture" "$validator" \
  >"$fixture_dir/missing-path.out" 2>&1; then
  echo "traceability validator accepted a nonexistent classified source path" >&2
  exit 1
fi
grep -Fq "frontend/src/DoesNotExist.tsx" "$fixture_dir/missing-path.out" \
  || { echo "missing-path failure did not identify the bad reference" >&2; exit 1; }

missing_target_fixture="$fixture_dir/missing-target.md"
awk '
  {
    if (index($0, "| FIN-GIT-001 |") > 0) {
      sub(/make test-hooks/, "make target-that-does-not-exist")
    }
    print
  }
' "$traceability" >"$missing_target_fixture"

if TRACEABILITY_FILE="$missing_target_fixture" "$validator" \
  >"$fixture_dir/missing-target.out" 2>&1; then
  echo "traceability validator accepted a nonexistent Make target" >&2
  exit 1
fi
grep -Fq "target-that-does-not-exist" "$fixture_dir/missing-target.out" \
  || { echo "missing-target failure did not identify the bad target" >&2; exit 1; }

compound_fixture="$fixture_dir/compound-command.md"
awk '
  {
    if (index($0, "| FIN-GIT-001 |") > 0) {
      sub(/make test-hooks/, "make test-hooks \\&\\& make verify-fast")
    }
    print
  }
' "$traceability" >"$compound_fixture"

TRACEABILITY_FILE="$compound_fixture" "$validator" >/dev/null

source_matrix_path_fixture="$fixture_dir/source-matrix-path.md"
awk '
  {
    if (index($0, "| Java backend and modern SPA |") > 0) {
      sub(/`frontend\/src\/App.tsx`/, "`frontend/src/DoesNotExist.tsx`")
    }
    print
  }
' "$traceability" >"$source_matrix_path_fixture"

if TRACEABILITY_FILE="$source_matrix_path_fixture" "$validator" \
  >"$fixture_dir/source-matrix-path.out" 2>&1; then
  echo "traceability validator accepted a nonexistent source-matrix path" >&2
  exit 1
fi
grep -Fq "frontend/src/DoesNotExist.tsx" "$fixture_dir/source-matrix-path.out" \
  || { echo "source-matrix failure did not identify the bad reference" >&2; exit 1; }

source_matrix_target_fixture="$fixture_dir/source-matrix-target.md"
awk '
  {
    if (index($0, "| Java backend and modern SPA |") > 0) {
      sub(/make build/, "make target-that-does-not-exist")
    }
    print
  }
' "$traceability" >"$source_matrix_target_fixture"

if TRACEABILITY_FILE="$source_matrix_target_fixture" "$validator" \
  >"$fixture_dir/source-matrix-target.out" 2>&1; then
  echo "traceability validator accepted a nonexistent source-matrix Make target" >&2
  exit 1
fi
grep -Fq "target-that-does-not-exist" "$fixture_dir/source-matrix-target.out" \
  || { echo "source-matrix target failure did not identify the bad target" >&2; exit 1; }

missing_script_fixture="$fixture_dir/missing-script.md"
awk '
  {
    if (index($0, "| Java backend and modern SPA |") > 0) {
      sub(/make build/, "scripts/does-not-exist.sh")
    }
    print
  }
' "$traceability" >"$missing_script_fixture"

if TRACEABILITY_FILE="$missing_script_fixture" "$validator" \
  >"$fixture_dir/missing-script.out" 2>&1; then
  echo "traceability validator accepted a nonexistent local script" >&2
  exit 1
fi
grep -Fq "scripts/does-not-exist.sh" "$fixture_dir/missing-script.out" \
  || { echo "local-script failure did not identify the bad command" >&2; exit 1; }

missing_npm_script_fixture="$fixture_dir/missing-npm-script.md"
awk '
  {
    if (index($0, "| Java backend and modern SPA |") > 0) {
      sub(/make build/, "npm --prefix frontend run does-not-exist")
    }
    print
  }
' "$traceability" >"$missing_npm_script_fixture"

if TRACEABILITY_FILE="$missing_npm_script_fixture" "$validator" \
  >"$fixture_dir/missing-npm-script.out" 2>&1; then
  echo "traceability validator accepted a nonexistent npm script" >&2
  exit 1
fi
grep -Fq "does-not-exist" "$fixture_dir/missing-npm-script.out" \
  || { echo "npm-script failure did not identify the bad command" >&2; exit 1; }

invalid_status_fixture="$fixture_dir/invalid-status.md"
awk '
  {
    if (index($0, "| Remote collaboration, publication, tag, release |") > 0) {
      sub(/\*\*Pending hosted evidence\*\*/, "**Pending hosted evidence plus**")
    }
    print
  }
' "$traceability" >"$invalid_status_fixture"

if TRACEABILITY_FILE="$invalid_status_fixture" "$validator" \
  >"$fixture_dir/invalid-status.out" 2>&1; then
  echo "traceability validator accepted a near-miss source requirement status" >&2
  exit 1
fi
grep -Fq "invalid status 'Pendinghostedevidenceplus'" "$fixture_dir/invalid-status.out" \
  || { echo "invalid-status failure did not identify the normalized near-miss status" >&2; exit 1; }

echo "TRACE-VALIDATOR-001 passed: classified paths and commands resolve, exact source statuses pass, and near-miss statuses fail"
