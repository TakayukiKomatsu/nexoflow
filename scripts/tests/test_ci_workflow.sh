#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
workflow="$repo_root/.github/workflows/ci.yml"

if [[ ! -s "$workflow" ]]; then
  echo "expected CI workflow at $workflow" >&2
  exit 1
fi

for expected in 'permissions:' 'contents: read' 'actions/checkout@' 'actions/setup-java@' 'actions/setup-node@' 'make verify-fast' 'Docker validation is deferred'; do
  if ! grep -Fq "$expected" "$workflow"; then
    echo "expected CI workflow to contain: $expected" >&2
    exit 1
  fi
done

echo "CI-001 passed: CI declares least privilege, Java/Node setup, verification, and Docker deferral"
