#!/usr/bin/env bash
# Focused regression tests for license normalization and rejection logic.
# Tests check-frontend-licenses.mjs directly without a live npm install.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$repo_root/scripts/check-frontend-licenses.mjs"
policy="$repo_root/frontend/config/allowed-licenses.json"
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }

# Helper: run checker with an inline JSON report and expect success or failure.
run_check() {
  local label="$1" report="$2" expect_exit="$3"
  local report_file="$tmpdir/report.json"
  printf '%s' "$report" > "$report_file"
  if node "$checker" "$report_file" "$policy" >/dev/null 2>&1; then
    local actual_exit=0
  else
    local actual_exit=1
  fi
  if [[ "$actual_exit" -eq "$expect_exit" ]]; then
    pass "$label"
  else
    fail "$label (expected exit $expect_exit, got $actual_exit)"
  fi
}

# LIC-001: MIT-licensed dep is approved.
run_check "LIC-001 MIT approved" \
  '{"react@19.2.7":{"licenses":"MIT"}}' 0

# LIC-002: BSD-3-Clause is approved.
run_check "LIC-002 BSD-3-Clause approved" \
  '{"some-pkg@1.0.0":{"licenses":"BSD-3-Clause"}}' 0

# LIC-003: Apache-2.0 is approved.
run_check "LIC-003 Apache-2.0 approved" \
  '{"some-pkg@1.0.0":{"licenses":"Apache-2.0"}}' 0

# LIC-004: GPL-3.0 is rejected.
run_check "LIC-004 GPL-3.0 rejected" \
  '{"bad-pkg@1.0.0":{"licenses":"GPL-3.0"}}' 1

# LIC-005: UNKNOWN (null licenses) is rejected.
run_check "LIC-005 null licenses rejected" \
  '{"mystery-pkg@1.0.0":{"licenses":null}}' 1

# LIC-006: 'SEE LICENSE IN' is rejected (not an SPDX identifier).
run_check "LIC-006 SEE LICENSE IN rejected" \
  '{"shady-pkg@1.0.0":{"licenses":"SEE LICENSE IN LICENSE.md"}}' 1

# LIC-007: OR expression where all parts are allowed passes.
run_check "LIC-007 MIT OR Apache-2.0 approved" \
  '{"dual-pkg@1.0.0":{"licenses":"MIT OR Apache-2.0"}}' 0

# LIC-008: OR expression where one part is disallowed fails.
run_check "LIC-008 MIT OR GPL-3.0 rejected" \
  '{"mixed-pkg@1.0.0":{"licenses":"MIT OR GPL-3.0"}}' 1

# LIC-009: Parenthesized expression is stripped and evaluated.
run_check "LIC-009 (MIT OR Apache-2.0) approved" \
  '{"paren-pkg@1.0.0":{"licenses":"(MIT OR Apache-2.0)"}}' 0

# LIC-010: Makefile defines license-check target.
makefile="$repo_root/Makefile"
if grep -Eq '^license-check:' "$makefile"; then
  pass "LIC-010 Makefile defines license-check target"
else
  fail "LIC-010 Makefile missing license-check target"
fi

# LIC-011: license-check is in .PHONY.
if grep -q 'license-check' "$makefile"; then
  pass "LIC-011 Makefile includes license-check in .PHONY"
else
  fail "LIC-011 Makefile .PHONY missing license-check"
fi

# LIC-012: CI workflow references make license-check.
workflow="$repo_root/.github/workflows/ci.yml"
if grep -Fq 'make license-check' "$workflow"; then
  pass "LIC-012 CI workflow calls make license-check"
else
  fail "LIC-012 CI workflow does not call make license-check"
fi

echo ""
echo "All license regression checks passed."
