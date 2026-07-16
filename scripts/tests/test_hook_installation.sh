#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
installer="$repo_root/scripts/install-git-hooks.sh"
test_repo="$(mktemp -d)"
cleanup() { rm -rf "$test_repo"; }
trap cleanup EXIT

if [[ ! -x "$installer" ]]; then
  echo "expected executable hook installer at $installer" >&2
  exit 1
fi

git -C "$test_repo" init --quiet
"$installer" "$test_repo"

for hook in commit-msg pre-commit pre-push; do
  installed="$test_repo/.git/hooks/$hook"
  if [[ ! -x "$installed" ]]; then
    echo "expected installed executable hook: $installed" >&2
    exit 1
  fi
done

echo "HOOK-INSTALL-001 passed: all local hooks are installed"
