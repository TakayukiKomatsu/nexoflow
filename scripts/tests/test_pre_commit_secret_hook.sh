#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
hook_source="$repo_root/scripts/hooks/pre-commit"

test_repo="$(mktemp -d)"
cleanup() {
  rm -rf "$test_repo"
}
trap cleanup EXIT

if [[ ! -f "$hook_source" ]]; then
  echo "expected pre-commit hook at $hook_source" >&2
  exit 1
fi

git -C "$test_repo" init --quiet
git -C "$test_repo" config user.name "Ana Developer"
git -C "$test_repo" config user.email "ana@example.test"
printf 'baseline\n' > "$test_repo/README.md"
git -C "$test_repo" add README.md
git -C "$test_repo" commit --quiet -m "chore: establish baseline"

canary='SRM_TEST_SECRET_DO_NOT_USE_7F3A'
printf 'token=%s\n' "$canary" > "$test_repo/fixture.env"
git -C "$test_repo" add fixture.env
install -m 0755 "$hook_source" "$test_repo/.git/hooks/pre-commit"

set +e
git -C "$test_repo" commit -m "test: stage fake credential" >"$test_repo/commit.out" 2>"$test_repo/commit.err"
commit_status=$?
set -e

if [[ "$commit_status" -eq 0 ]]; then
  echo "expected staged fake credential to be rejected" >&2
  exit 1
fi

if ! grep -Fq 'fixture.env' "$test_repo/commit.err"; then
  echo "expected rejection to identify fixture.env" >&2
  cat "$test_repo/commit.err" >&2
  exit 1
fi

if grep -Fq "$canary" "$test_repo/commit.err"; then
  echo "expected rejection to avoid printing the complete canary" >&2
  exit 1
fi

echo "FIN-GIT-002 passed: staged fake credential was rejected safely"
