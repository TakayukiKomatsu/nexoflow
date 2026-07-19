#!/usr/bin/env bash
# CRISIS-001: Disposable shared clone that injects a regression, proves it fails,
# reverts it, proves recovery, and records both commit hashes.
# Never pushes, tags, or touches the working repository.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

clone_dir="$(mktemp -d)"
trap 'rm -rf "$clone_dir"' EXIT

echo "=== CRISIS-001: creating disposable shared clone from HEAD ==="
git -C "$repo_root" clone --local --shared --quiet "$repo_root" "$clone_dir"
echo "  clone: $clone_dir"

# The clone needs frontend/node_modules for Mermaid rendering in the arch-doc check.
# Symlink from the host repo (same package.json) to avoid reinstalling.
ln -s "$repo_root/frontend/node_modules" "$clone_dir/frontend/node_modules"

echo "=== CRISIS-001: creating simulation branch ==="
git -C "$clone_dir" checkout -b simulation/crisis-revert --quiet

echo "=== CRISIS-001: injecting controlled regression ==="
echo 'exit 99' >> "$clone_dir/scripts/tests/test_architecture_docs.sh"
git -C "$clone_dir" add scripts/tests/test_architecture_docs.sh
git -C "$clone_dir" \
  -c user.email="crisis-sim@srm.local" \
  -c user.name="Crisis Simulation" \
  commit --quiet -m "test(crisis): inject controlled regression for revert proof"
CRISIS_SHA="$(git -C "$clone_dir" rev-parse HEAD)"
echo "  regression commit: $CRISIS_SHA"

echo "=== CRISIS-001: proving the injected regression is detected ==="
if "$clone_dir/scripts/tests/test_architecture_docs.sh" 2>/dev/null; then
  echo "CRISIS-001 FAILED: injected regression passed the architecture-doc check — simulation invalid" >&2
  exit 1
fi
echo "  regression correctly detected (exit non-zero)"

echo "=== CRISIS-001: reverting the regression ==="
git -C "$clone_dir" \
  -c user.email="crisis-sim@srm.local" \
  -c user.name="Crisis Simulation" \
  revert --no-edit HEAD --quiet
REVERT_SHA="$(git -C "$clone_dir" rev-parse HEAD)"
echo "  revert commit: $REVERT_SHA"

echo "=== CRISIS-001: proving recovery after revert ==="
"$clone_dir/scripts/tests/test_architecture_docs.sh" \
  || { echo "CRISIS-001 FAILED: architecture-doc check still fails after revert" >&2; exit 1; }
echo "  recovery confirmed"

echo "=== CRISIS-001: asserting exactly 2 simulation commits ==="
commit_count="$(git -C "$clone_dir" rev-list --count HEAD~2..HEAD)"
test "$commit_count" -eq 2 \
  || { echo "CRISIS-001 FAILED: expected 2 commits (regression + revert), got $commit_count" >&2; exit 1; }
evidence_file="$clone_dir/crisis-evidence.txt"
printf '%s\n' \
  "branch=simulation/crisis-revert" \
  "regression_sha=$CRISIS_SHA" \
  "revert_sha=$REVERT_SHA" \
  "commits_verified=$commit_count" \
  > "$evidence_file"
test -s "$evidence_file" \
  || { echo "CRISIS-001 FAILED: disposable evidence record was not written" >&2; exit 1; }

echo ""
echo "CRISIS-001 passed: crisis/revert evidence recorded"
echo "  branch:           simulation/crisis-revert"
echo "  regression SHA:   $CRISIS_SHA"
echo "  revert SHA:       $REVERT_SHA"
echo "  commits verified: $commit_count"
