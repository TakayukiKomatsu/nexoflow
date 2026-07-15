#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
hook_source="$repo_root/scripts/hooks/commit-msg"

test_repo="$(mktemp -d)"
cleanup() {
  rm -rf "$test_repo"
}
trap cleanup EXIT

if [[ ! -f "$hook_source" ]]; then
  echo "expected commit-message hook at $hook_source" >&2
  exit 1
fi

git -C "$test_repo" init --quiet
git -C "$test_repo" config user.name "Ana Developer"
git -C "$test_repo" config user.email "ana@example.test"
printf 'baseline\n' > "$test_repo/README.md"
git -C "$test_repo" add README.md
git -C "$test_repo" commit --quiet -m "chore: establish baseline"
head_before="$(git -C "$test_repo" rev-parse HEAD)"

install -m 0755 "$hook_source" "$test_repo/.git/hooks/commit-msg"
printf 'safe fixture\n' > "$test_repo/note.txt"
git -C "$test_repo" add note.txt

set +e
git -C "$test_repo" commit -m "updates" >"$test_repo/commit.out" 2>"$test_repo/commit.err"
commit_status=$?
set -e

if [[ "$commit_status" -eq 0 ]]; then
  echo "expected malformed commit message to be rejected" >&2
  exit 1
fi

if ! grep -Fq "Conventional Commit" "$test_repo/commit.err"; then
  echo "expected rejection to mention Conventional Commit" >&2
  cat "$test_repo/commit.err" >&2
  exit 1
fi

head_after="$(git -C "$test_repo" rev-parse HEAD)"
if [[ "$head_before" != "$head_after" ]]; then
  echo "expected HEAD to remain unchanged after rejected commit" >&2
  exit 1
fi

echo "FIN-GIT-001 passed: malformed commit was rejected and HEAD remained unchanged"
