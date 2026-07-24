#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

npm --prefix "$repo_root/frontend" run test -- --run
npm --prefix "$repo_root/frontend" run typecheck
npm --prefix "$repo_root/frontend" run lint
npm --prefix "$repo_root/frontend" run format:check

for document in "$repo_root/.env.example" "$repo_root/README.md" "$repo_root/docs/RUNBOOK.md"; do
  grep -Fq 'VITE_BACKEND_ORIGIN' "$document" \
    || { echo "native frontend origin is undocumented in $document" >&2; exit 1; }
  if grep -Fq 'VITE_API_PROXY_TARGET' "$document"; then
    echo "stale native frontend proxy variable remains in $document" >&2
    exit 1
  fi
done

echo "FRONTEND-QUALITY-001 passed: frontend test, typecheck, lint, and format checks are available"
