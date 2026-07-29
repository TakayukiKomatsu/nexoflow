#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for required in \
  "$repo_root/docs/GIT_WORKFLOW.md" \
  "$repo_root/docs/architecture/er-diagram.mmd" \
  "$repo_root/docs/architecture/c4-context.mmd" \
  "$repo_root/docs/architecture/c4-container.mmd" \
  "$repo_root/docs/architecture/settlement-state.mmd" \
  "$repo_root/docs/architecture/settlement-sequence.mmd" \
  "$repo_root/docs/architecture/scale-evolution.mmd" \
  "$repo_root/docs/architecture/schema-inventory.md" \
  "$repo_root/docs/architecture/api-endpoints.md" \
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

for required in \
  "$repo_root/.editorconfig" \
  "$repo_root/.env.example" \
  "$repo_root/.github/PULL_REQUEST_TEMPLATE.md" \
  "$repo_root/README.md" \
  "$repo_root/AI_USAGE.md"; do
  if [[ ! -s "$required" ]]; then
    echo "expected non-empty foundation artifact: $required" >&2
    exit 1
  fi
done

grep -Fq 'GitHub Flow' "$repo_root/docs/GIT_WORKFLOW.md"
grep -Fq 'receivables' "$repo_root/docs/architecture/er-diagram.mmd"
grep -Fq 'README_case_dev_srm.md' "$repo_root/docs/REQUIREMENT_TRACEABILITY.md"

for adr in "$repo_root"/docs/adr/[0-9][0-9][0-9][0-9]-*.md; do
  for heading in '## Status' '## Context' '## Decision' '## Alternatives considered' '## Consequences' '## Revisit triggers'; do
    grep -Fq "$heading" "$adr" \
      || { echo "ADR missing required section '$heading': $adr" >&2; exit 1; }
  done
done

while IFS= read -r markdown; do
  while IFS= read -r target; do
    case "$target" in
      http://*|https://*|mailto:*|\#*|'') continue ;;
    esac
    target="${target#<}"
    target="${target%>}"
    target="${target%%#*}"
    if [[ ! -e "$(dirname "$markdown")/$target" ]]; then
      echo "broken local Markdown link in $markdown: $target" >&2
      exit 1
    fi
  done < <(grep -oE '\]\([^)]+\)' "$markdown" | sed -E 's/^\]\((.*)\)$/\1/')
done < <(
  find "$repo_root/docs" -type f -name '*.md' -not -path '*/.omc/*' -not -path '*/.pi-subagents/*'
  printf '%s\n' "$repo_root/README.md" "$repo_root/AI_USAGE.md" "$repo_root/HT_USAGE.md" "$repo_root/frontend/README.md"
)

while IFS= read -r table; do
  if ! grep -Eq "^[[:space:]]*$table[[:space:]]*\\{" "$repo_root/docs/architecture/er-diagram.mmd"; then
    echo "migration table missing from ER diagram: $table" >&2
    exit 1
  fi
done < <(sed -nE 's/^create table ([a-z_]+).*/\1/p' "$repo_root"/backend/src/main/resources/db/migration/*.sql)

node "$repo_root/scripts/validate-schema-docs.mjs"
"$repo_root/scripts/tests/test_schema_docs_validator.sh"
"$repo_root/scripts/with-java21.sh" "$repo_root/backend/gradlew" \
  -p "$repo_root/backend" exportOpenApi --no-daemon
node "$repo_root/scripts/validate-api-docs.mjs"
"$repo_root/scripts/tests/test_api_docs_validator.sh"

render_dir="$repo_root/backend/build/mermaid"
mkdir -p "$render_dir"
for diagram in "$repo_root"/docs/architecture/*.mmd; do
  output="$render_dir/$(basename "${diagram%.mmd}").svg"
  npm --prefix "$repo_root/frontend" exec -- mmdc -i "$diagram" -o "$output" >/dev/null
  if [[ ! -s "$output" ]]; then
    echo "Mermaid rendering produced no output for $diagram" >&2
    exit 1
  fi
done

# Historical audit reconciliation contract
historical_audit="$repo_root/docs/evidence/historical/2026-07-22-audit-discrepancies.md"
test ! -e "$repo_root/docs/AUDIT_DISCREPANCIES.md" \
  || { echo "active stale audit must be archived" >&2; exit 1; }
test -s "$historical_audit" \
  || { echo "historical audit evidence is missing" >&2; exit 1; }
grep -Fq 'Status: Historical — findings resolved' "$historical_audit" \
  || { echo "historical audit lacks resolved status" >&2; exit 1; }
grep -Fq 'FinancialModuleLayeringTest.java' "$historical_audit" \
  || { echo "historical audit lacks layering remediation evidence" >&2; exit 1; }
grep -Fq 'AuditEventQuery' "$historical_audit" \
  || { echo "historical audit lacks audit-layer remediation evidence" >&2; exit 1; }

echo "ARCH-DOCS-001 passed: foundation artifacts, local links, Mermaid renders, and migration-to-ER tables are consistent"
