#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
generated_document="$repo_root/backend/build/generated/openapi/srm-openapi.json"

rm -f "$generated_document"
make -C "$repo_root" validate-frontend-api-contract

if [[ ! -s "$generated_document" ]]; then
  echo "validate-frontend-api-contract did not generate $generated_document" >&2
  exit 1
fi

echo "API-CONTRACT-CLEAN-001 passed: validation generated and consumed a fresh OpenAPI document"
