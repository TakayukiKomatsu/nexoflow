#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

npm --prefix "$repo_root/frontend" run test -- --run
npm --prefix "$repo_root/frontend" run typecheck
npm --prefix "$repo_root/frontend" run lint
npm --prefix "$repo_root/frontend" run format:check

echo "FRONTEND-QUALITY-001 passed: frontend test, typecheck, lint, and format checks are available"
