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

echo "TRACE-VALIDATOR-001 passed: scenario and source-matrix paths and compound Make commands resolve"
