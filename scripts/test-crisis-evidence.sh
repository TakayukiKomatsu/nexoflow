#!/usr/bin/env bash
# CRISIS-002: Disposable shared clone whose local branch is deliberately named
# main. It injects a dedicated regression, proves the fixture fails, reverts it,
# proves exact tree restoration, and proves fast-gate recovery.
# Never pushes, tags, or touches the working repository.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="scripts/tests/test_crisis_fixture.sh"
recovery_target="${CRISIS_RECOVERY_TARGET:-verify-fast}"

[[ "$recovery_target" =~ ^[a-zA-Z0-9_-]+$ ]] \
  || { echo "CRISIS-002 FAILED: invalid recovery Make target" >&2; exit 1; }

clone_dir="$(mktemp -d)"
trap 'rm -rf "$clone_dir"' EXIT

echo "=== CRISIS-002: creating disposable shared clone from HEAD ==="
git -C "$repo_root" clone --local --shared --quiet "$repo_root" "$clone_dir"
echo "  clone: $clone_dir"

ln -s "$repo_root/frontend/node_modules" "$clone_dir/frontend/node_modules"

echo "=== CRISIS-002: naming the disposable release branch main ==="
git -C "$clone_dir" branch -M main
branch_name="$(git -C "$clone_dir" symbolic-ref --short HEAD)"
[[ "$branch_name" == "main" ]] \
  || { echo "CRISIS-002 FAILED: disposable branch is $branch_name, expected main" >&2; exit 1; }
RELEASE_CANDIDATE_SHA="$(git -C "$clone_dir" rev-parse HEAD)"
RELEASE_CANDIDATE_TREE="$(git -C "$clone_dir" rev-parse 'HEAD^{tree}')"

echo "=== CRISIS-002: injecting controlled regression ==="
sed -i.bak 's/CRISIS_FIXTURE_STATE=healthy/CRISIS_FIXTURE_STATE=regressed/' "$clone_dir/$fixture"
rm "$clone_dir/$fixture.bak"
git -C "$clone_dir" add "$fixture"
git -C "$clone_dir" \
  -c user.email="crisis-sim@srm.local" \
  -c user.name="Crisis Simulation" \
  commit --quiet -m "test(crisis): inject controlled regression for revert proof"
CRISIS_SHA="$(git -C "$clone_dir" rev-parse HEAD)"
echo "  regression commit: $CRISIS_SHA"

echo "=== CRISIS-002: proving the injected regression is detected ==="
if "$clone_dir/$fixture" 2>/dev/null; then
  echo "CRISIS-002 FAILED: injected regression passed the dedicated fixture" >&2
  exit 1
fi
echo "  regression correctly detected (exit non-zero)"

echo "=== CRISIS-002: reverting the regression ==="
git -C "$clone_dir" \
  -c user.email="crisis-sim@srm.local" \
  -c user.name="Crisis Simulation" \
  revert --no-edit HEAD --quiet
REVERT_SHA="$(git -C "$clone_dir" rev-parse HEAD)"
echo "  revert commit: $REVERT_SHA"

echo "=== CRISIS-002: proving recovery after revert ==="
"$clone_dir/$fixture" \
  || { echo "CRISIS-002 FAILED: fixture still fails after revert" >&2; exit 1; }
make -C "$clone_dir" "$recovery_target" \
  || { echo "CRISIS-002 FAILED: $recovery_target gate still fails after revert" >&2; exit 1; }
echo "  recovery confirmed"

echo "=== CRISIS-002: asserting the revert restored the complete release-candidate tree ==="
RESTORED_TREE="$(git -C "$clone_dir" rev-parse 'HEAD^{tree}')"
[[ "$RESTORED_TREE" == "$RELEASE_CANDIDATE_TREE" ]] \
  || { echo "CRISIS-002 FAILED: reverted tree differs from release candidate $RELEASE_CANDIDATE_SHA" >&2; exit 1; }
git -C "$clone_dir" diff --quiet "$RELEASE_CANDIDATE_SHA" "$REVERT_SHA" \
  || { echo "CRISIS-002 FAILED: reverted files differ from release candidate" >&2; exit 1; }
echo "  restored tree verified equal to release candidate"

echo "=== CRISIS-002: asserting exactly 2 simulation commits ==="
commit_count="$(git -C "$clone_dir" rev-list --count HEAD~2..HEAD)"
test "$commit_count" -eq 2 \
  || { echo "CRISIS-002 FAILED: expected 2 commits (regression + revert), got $commit_count" >&2; exit 1; }
evidence_file="$clone_dir/crisis-evidence.txt"
printf '%s\n' \
  "branch=main" \
  "release_candidate_sha=$RELEASE_CANDIDATE_SHA" \
  "release_candidate_tree=$RELEASE_CANDIDATE_TREE" \
  "regression_sha=$CRISIS_SHA" \
  "revert_sha=$REVERT_SHA" \
  "restored_tree=$RESTORED_TREE" \
  "recovery_target=$recovery_target" \
  "commits_verified=$commit_count" \
  > "$evidence_file"
test -s "$evidence_file" \
  || { echo "CRISIS-002 FAILED: disposable evidence record was not written" >&2; exit 1; }

echo ""
echo "CRISIS-002 passed: crisis/revert evidence recorded"
echo "  branch:           main"
echo "  release candidate: $RELEASE_CANDIDATE_SHA"
echo "  regression SHA:   $CRISIS_SHA"
echo "  revert SHA:       $REVERT_SHA"
echo "  restored tree:    verified equal to release candidate"
echo "  recovery target:  $recovery_target"
echo "  commits verified: $commit_count"
