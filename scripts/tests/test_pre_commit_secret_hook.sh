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

# Built via concatenation so this test file's own source never contains the
# contiguous canary literal (it would otherwise trip the real repo's
# installed pre-commit hook when this file itself is staged/committed).
canary_prefix='SRM_TEST_SECRET_DO_NOT_USE'
canary="${canary_prefix}_7F3A"
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

git -C "$test_repo" reset --quiet HEAD fixture.env
rm -f "$test_repo/fixture.env"
generic_fixture='client_secret=fixture-value-9Q8W7E6R5T4Y3U2I'
printf '%s\n' "$generic_fixture" > "$test_repo/application.properties"
git -C "$test_repo" add application.properties

set +e
git -C "$test_repo" commit -m "test: stage generic fake credential" >"$test_repo/generic.out" 2>"$test_repo/generic.err"
generic_status=$?
set -e

if [[ "$generic_status" -eq 0 ]]; then
  echo "expected a generic staged credential assignment to be rejected" >&2
  exit 1
fi
grep -Fq 'application.properties' "$test_repo/generic.err" \
  || { echo "expected generic rejection to identify application.properties" >&2; exit 1; }
if grep -Fq 'fixture-value-9Q8W7E6R5T4Y3U2I' "$test_repo/generic.err"; then
  echo "expected generic rejection to avoid printing the fixture value" >&2
  exit 1
fi

git -C "$test_repo" reset --quiet HEAD application.properties
rm -f "$test_repo/application.properties"
printf 'CLIENT_SECRET=replace-with-local-secret\n' > "$test_repo/.env.example"
git -C "$test_repo" add .env.example
git -C "$test_repo" commit --quiet -m "test: permit documented placeholder"

echo "FIN-GIT-003 passed: generic secret-shaped assignments fail while explicit placeholders pass"
