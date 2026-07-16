#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for required in \
  "$repo_root/docs/GIT_WORKFLOW.md" \
  "$repo_root/docs/architecture/er-diagram.mmd" \
  "$repo_root/docs/REQUIREMENT_TRACEABILITY.md" \
  "$repo_root/docs/adr/0001-modular-monolith.md" \
  "$repo_root/docs/adr/0002-postgresql-write-model.md" \
  "$repo_root/docs/adr/0003-sql-reporting-read-model.md" \
  "$repo_root/docs/adr/0004-decimal-financial-calculation.md" \
  "$repo_root/docs/adr/0005-local-jwt-and-oidc-evolution.md" \
  "$repo_root/docs/adr/0006-immutable-pricing-quotes.md" \
  "$repo_root/docs/adr/0007-atomic-idempotent-settlement.md"; do
  if [[ ! -s "$required" ]]; then
    echo "expected non-empty architecture document: $required" >&2
    exit 1
  fi
done

grep -Fq 'GitHub Flow' "$repo_root/docs/GIT_WORKFLOW.md"
grep -Fq 'receivables' "$repo_root/docs/architecture/er-diagram.mmd"
grep -Fq 'README_case_dev_srm.md' "$repo_root/docs/REQUIREMENT_TRACEABILITY.md"

echo "ARCH-DOCS-001 passed: governed architecture documents exist and link to requirements"
