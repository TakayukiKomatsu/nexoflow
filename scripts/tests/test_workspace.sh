#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for required in \
  "$repo_root/backend/gradlew" \
  "$repo_root/backend/build.gradle" \
  "$repo_root/backend/src/main/java/com/srm/creditengine/CreditEngineApplication.java" \
  "$repo_root/frontend/package.json" \
  "$repo_root/frontend/src/App.tsx"; do
  if [[ ! -f "$required" ]]; then
    echo "expected workspace file: $required" >&2
    exit 1
  fi
done

echo "WORKSPACE-001 passed: backend and frontend workspace files exist"
