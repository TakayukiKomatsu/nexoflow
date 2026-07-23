#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixtures="$repo_root/scripts/tests/fixtures/api-docs"
validator="$repo_root/scripts/validate-api-docs.mjs"

SRM_OPENAPI_DOCUMENT="$fixtures/openapi.json" \
SRM_API_INVENTORY_DOCUMENT="$fixtures/api-endpoints-valid.md" \
  node "$validator" >/dev/null

failure_output="$(mktemp)"
trap 'rm -f "$failure_output"' EXIT
if SRM_OPENAPI_DOCUMENT="$fixtures/openapi.json" \
   SRM_API_INVENTORY_DOCUMENT="$fixtures/api-endpoints-stale.md" \
   node "$validator" >"$failure_output" 2>&1; then
  echo "API documentation validator accepted missing and stale operations" >&2
  exit 1
fi

grep -Fq 'undocumented OpenAPI operation: POST /api/v1/widgets' "$failure_output"
grep -Fq 'undocumented OpenAPI operation: DELETE /api/v1/widgets/{id}' "$failure_output"
grep -Fq 'stale documentation: GET /api/v1/ghosts' "$failure_output"

echo "DOC-OPENAPI-MUTATION-001 passed: generated operations are authoritative and drift is rejected"
