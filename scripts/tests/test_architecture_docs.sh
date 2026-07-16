#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for required in \
  "$repo_root/docs/GIT_WORKFLOW.md" \
  "$repo_root/docs/architecture/er-diagram.mmd" \
  "$repo_root/docs/architecture/c4-context.mmd" \
  "$repo_root/docs/architecture/c4-container.mmd" \
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

while IFS= read -r markdown; do
  while IFS= read -r target; do
    case "$target" in
      http://*|https://*|mailto:*|\#*|'') continue ;;
    esac
    target="${target%%#*}"
    if [[ ! -e "$(dirname "$markdown")/$target" ]]; then
      echo "broken local Markdown link in $markdown: $target" >&2
      exit 1
    fi
  done < <(sed -nE 's/.*\]\(([^)]+)\).*/\1/p' "$markdown")
done < <(find "$repo_root/docs" -type f -name '*.md' -not -path '*/.omc/*' -not -path '*/.pi-subagents/*')

while IFS= read -r table; do
  if ! grep -Eq "^[[:space:]]*$table[[:space:]]*\\{" "$repo_root/docs/architecture/er-diagram.mmd"; then
    echo "migration table missing from ER diagram: $table" >&2
    exit 1
  fi
done < <(sed -nE 's/^create table ([a-z_]+).*/\1/p' "$repo_root"/backend/src/main/resources/db/migration/*.sql | grep -v '^schema_metadata$')

render_dir="$repo_root/backend/build/mermaid"
mkdir -p "$render_dir"
for diagram in \
  "$repo_root/docs/architecture/er-diagram.mmd" \
  "$repo_root/docs/architecture/c4-context.mmd" \
  "$repo_root/docs/architecture/c4-container.mmd"; do
  output="$render_dir/$(basename "${diagram%.mmd}").svg"
  npm --prefix "$repo_root/frontend" exec -- mmdc -i "$diagram" -o "$output" >/dev/null
  if [[ ! -s "$output" ]]; then
    echo "Mermaid rendering produced no output for $diagram" >&2
    exit 1
  fi
done

echo "ARCH-DOCS-001 passed: foundation artifacts, local links, Mermaid renders, and migration-to-ER tables are consistent"
