#!/usr/bin/env bash
# DOCS-001: Validate documentation consistency — Markdown local links, Mermaid
# diagrams, migration-to-ER coverage, OpenAPI configuration, and forbidden claims.
# Delegates structural checks to test_architecture_docs.sh, then adds doc-quality gates.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=== DOCS-001: architecture docs, Markdown links, Mermaid, and ER checks ==="
"$repo_root/scripts/tests/test_architecture_docs.sh" \
  || { echo "DOCS-001 FAILED: architecture document check did not pass" >&2; exit 1; }

echo "=== DOCS-002: executable OpenAPI contract ==="
"$repo_root/scripts/with-java21.sh" "$repo_root/backend/gradlew" \
  -p "$repo_root/backend" test \
  --tests '*RuntimeMetadataContractTest.exposesOpenApiAndHealthProbes' \
  || { echo "DOCS-002 FAILED: the generated OpenAPI endpoint contract did not pass" >&2; exit 1; }
echo "DOCS-002 passed: generated /v3/api-docs contract is executable and reachable"

echo "=== DOCS-003: forbidden claim scan ==="
# Reject evidence-quality claims that promise production behaviour not yet demonstrated.
FORBIDDEN_PATTERNS=(
  'TODO: production'
  'FIXME: production'
  'TODO: add to production'
  'NOT FOR PRODUCTION'
  'placeholder — replace'
  'implement later'
)
forbidden_hits=()
for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
  while IFS= read -r hit; do
    forbidden_hits+=("$hit")
  done < <(
    grep -rFn "$pattern" \
      "$repo_root/docs" \
      "$repo_root/README.md" \
      "$repo_root/AI_USAGE.md" \
      2>/dev/null || true
  )
done

if [[ "${#forbidden_hits[@]}" -gt 0 ]]; then
  echo "DOCS-003 FAILED: forbidden claims found in documentation:" >&2
  printf '  %s\n' "${forbidden_hits[@]}" >&2
  exit 1
fi
echo "DOCS-003 passed: no forbidden production claims in documentation"

echo ""
echo "validate-docs: all documentation gates passed"
