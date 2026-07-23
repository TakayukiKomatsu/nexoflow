#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixtures="$repo_root/scripts/tests/fixtures/schema-docs"
validator="$repo_root/scripts/validate-schema-docs.mjs"

run_validator() {
  local sql_dir="$1"
  local java_dir="$2"
  SRM_SCHEMA_SQL_DIR="$sql_dir" \
  SRM_SCHEMA_JAVA_DIR="$java_dir" \
  SRM_SCHEMA_ER_DOCUMENT="$fixtures/er-diagram.mmd" \
  SRM_SCHEMA_INVENTORY_DOCUMENT="$fixtures/schema-inventory.md" \
    node "$validator"
}

run_validator "$fixtures/sql-valid" "$fixtures/java-valid" >/dev/null

sql_failure="$(mktemp)"
java_failure="$(mktemp)"
trap 'rm -f "$sql_failure" "$java_failure"' EXIT

if run_validator "$fixtures/sql-mutated" "$fixtures/java-valid" >"$sql_failure" 2>&1; then
  echo "schema validator accepted mutated SQL constraint structures" >&2
  exit 1
fi
grep -Fq 'children.fk(parent_id->parents.code)' "$sql_failure"
grep -Fq 'children.check(amount>=0)' "$sql_failure"
grep -Fq 'children.unique(code,parent_id)' "$sql_failure"

if run_validator "$fixtures/sql-valid" "$fixtures/java-mutated" >"$java_failure" 2>&1; then
  echo "schema validator accepted a mutated Java-migration foreign key" >&2
  exit 1
fi
grep -Fq 'children.fk(java_parent_id->parents.code)' "$java_failure"

echo "DOC-SCHEMA-MUTATION-001 passed: SQL and Java add-constraint structure drift is rejected"
