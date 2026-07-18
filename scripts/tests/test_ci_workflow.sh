#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
workflow="$repo_root/.github/workflows/ci.yml"

if [[ ! -s "$workflow" ]]; then
  echo "expected CI workflow at $workflow" >&2
  exit 1
fi

for expected in 'permissions:' 'contents: read' 'make verify-fast' 'make build' 'make test-runtime' 'make verify-compose' 'make license-check' 'gitleaks/gitleaks-action@' 'actions/dependency-review-action@'; do
  if ! grep -Fq "$expected" "$workflow"; then
    echo "expected CI workflow to contain: $expected" >&2
    exit 1
  fi
done

if grep -Eq 'uses: [^ ]+@v[0-9]' "$workflow"; then
  echo "workflow actions must be pinned to immutable commit SHAs" >&2
  exit 1
fi

action_count="$(grep -Ec 'uses: [^ ]+@[0-9a-f]{40}([[:space:]]|$)' "$workflow")"
if [[ "$action_count" -lt 6 ]]; then
  echo "expected every workflow action to use a 40-character commit SHA" >&2
  exit 1
fi

echo "CI-001 passed: CI uses immutable actions, least privilege, builds, Docker runtime tests, license compliance, and security review"
