#!/usr/bin/env bash
# COLLAB-LOCAL-001 / REBASE-LOCAL-002: executable, remote-free GitHub Flow
# simulation. All commits, refs, rebase, review metadata, and merge operations
# occur only in a disposable local clone.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
clone_dir="$(mktemp -d)"
cleanup() {
  status=$?
  rm -rf "$clone_dir"
  trap - EXIT
  exit "$status"
}
trap cleanup EXIT

git -C "$repo_root" clone --local --shared --quiet "$repo_root" "$clone_dir"
git -C "$clone_dir" remote remove origin
git -C "$clone_dir" checkout -B main --quiet
BASE_SHA="$(git -C "$clone_dir" rev-parse HEAD)"

git -C "$clone_dir" checkout -b feature/local-pr-simulation --quiet
printf 'reviewable behavior\n' > "$clone_dir/local-pr-simulation.txt"
git -C "$clone_dir" add local-pr-simulation.txt
git -C "$clone_dir" \
  -c user.name='Local Collaboration Simulation' \
  -c user.email='collaboration-sim@srm.local' \
  commit --quiet -m 'feat(simulation): add reviewable local change'
FIRST_SHA="$(git -C "$clone_dir" rev-parse HEAD)"

printf 'review feedback addressed\n' >> "$clone_dir/local-pr-simulation.txt"
git -C "$clone_dir" add local-pr-simulation.txt
git -C "$clone_dir" \
  -c user.name='Local Collaboration Simulation' \
  -c user.email='collaboration-sim@srm.local' \
  commit --quiet -m 'fixup! feat(simulation): add reviewable local change'
OLD_TIP="$(git -C "$clone_dir" rev-parse HEAD)"
[[ "$(git -C "$clone_dir" rev-list --count "$BASE_SHA..$OLD_TIP")" -eq 2 ]] \
  || { echo 'REBASE-LOCAL-002 FAILED: expected feature plus fixup commits' >&2; exit 1; }

printf '%s\n' \
  '# Local pull request simulation' \
  '' \
  'Title: feat(simulation): add reviewable local change' \
  'Base: main' \
  'Head: feature/local-pr-simulation' \
  'Scope: harmless disposable fixture only' \
  'Checks: git diff --check and fixture assertions' \
  'Security: no credentials, financial data, or remote operations' \
  'Rollback: revert the single squashed commit' \
  'Residual risk: this proves local workflow mechanics, not hosted review' \
  > "$clone_dir/.git/PULL_REQUEST_SIMULATION.md"

GIT_SEQUENCE_EDITOR=: git -C "$clone_dir" rebase -i --autosquash main --quiet
NEW_TIP="$(git -C "$clone_dir" rev-parse HEAD)"
[[ "$NEW_TIP" != "$OLD_TIP" && "$NEW_TIP" != "$FIRST_SHA" ]] \
  || { echo 'REBASE-LOCAL-002 FAILED: autosquash did not rewrite unpublished history' >&2; exit 1; }
[[ "$(git -C "$clone_dir" rev-list --count "$BASE_SHA..$NEW_TIP")" -eq 1 ]] \
  || { echo 'REBASE-LOCAL-002 FAILED: autosquash did not produce one coherent commit' >&2; exit 1; }
[[ "$(git -C "$clone_dir" show -s --format=%s HEAD)" == 'feat(simulation): add reviewable local change' ]] \
  || { echo 'REBASE-LOCAL-002 FAILED: final commit subject is not coherent' >&2; exit 1; }

range_diff_file="$clone_dir/.git/autosquash-range-diff.txt"
git -C "$clone_dir" range-diff "$BASE_SHA..$OLD_TIP" "$BASE_SHA..$NEW_TIP" > "$range_diff_file"
[[ -s "$range_diff_file" ]] \
  || { echo 'REBASE-LOCAL-002 FAILED: range-diff evidence is empty' >&2; exit 1; }
echo 'REBASE-LOCAL-002 passed: actual interactive autosquash rewrote two unpublished commits into one'
echo '  range-diff: verified'

git -C "$clone_dir" update-ref refs/pull-simulation/1/head "$NEW_TIP"
[[ "$(git -C "$clone_dir" rev-parse refs/pull-simulation/1/head)" == "$NEW_TIP" ]] \
  || { echo 'COLLAB-LOCAL-001 FAILED: local PR head ref is incorrect' >&2; exit 1; }
for field in 'Title:' 'Base: main' 'Head: feature/local-pr-simulation' 'Checks:' 'Rollback:' 'Residual risk:'; do
  grep -Fq "$field" "$clone_dir/.git/PULL_REQUEST_SIMULATION.md" \
    || { echo "COLLAB-LOCAL-001 FAILED: PR description lacks $field" >&2; exit 1; }
done
git -C "$clone_dir" diff --check "main...feature/local-pr-simulation"
grep -Fxq 'review feedback addressed' "$clone_dir/local-pr-simulation.txt" \
  || { echo 'COLLAB-LOCAL-001 FAILED: reviewed fixture behavior is missing' >&2; exit 1; }

git -C "$clone_dir" checkout main --quiet
git -C "$clone_dir" merge --ff-only feature/local-pr-simulation --quiet
[[ "$(git -C "$clone_dir" rev-parse HEAD)" == "$NEW_TIP" ]] \
  || { echo 'COLLAB-LOCAL-001 FAILED: disposable main does not match reviewed head' >&2; exit 1; }
[[ -z "$(git -C "$clone_dir" remote)" ]] \
  || { echo 'COLLAB-LOCAL-001 FAILED: disposable repository unexpectedly retains a remote' >&2; exit 1; }

echo 'COLLAB-LOCAL-001 passed: local PR branch was reviewed and fast-forwarded into disposable main'
echo "  base: $BASE_SHA"
echo "  old unpublished tip: $OLD_TIP"
echo "  reviewed head: $NEW_TIP"
echo '  remote mutations: none'
