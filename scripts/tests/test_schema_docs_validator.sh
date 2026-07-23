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
inline_failure="$(mktemp)"
type_failure="$(mktemp)"
nullability_failure="$(mktemp)"
trap 'rm -f "$sql_failure" "$java_failure" "$inline_failure" "$type_failure" "$nullability_failure"' EXIT

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

if run_validator "$fixtures/sql-inline-mutated" "$fixtures/java-valid" >"$inline_failure" 2>&1; then
  echo "schema validator accepted mutated inline check and table foreign key structures" >&2
  exit 1
fi
grep -Fq 'children.check(amount>=0)' "$inline_failure"
grep -Fq 'children.fk(table_parent_id->parents.code)' "$inline_failure"

if run_validator "$fixtures/sql-type-mutated" "$fixtures/java-valid" >"$type_failure" 2>&1; then
  echo "schema validator accepted drift from exact decimal to floating-point storage" >&2
  exit 1
fi
grep -Fq 'ER type mismatch: children.money migration double_precision, ER decimal_19_4' "$type_failure"

if run_validator "$fixtures/sql-nullability-mutated" "$fixtures/java-valid" >"$nullability_failure" 2>&1; then
  echo "schema validator accepted nullable financial storage drift" >&2
  exit 1
fi
grep -Fq 'children.numeric(money:numeric(19,4):nullable)' "$nullability_failure"

echo "DOC-SCHEMA-MUTATION-001 passed: constraint, type, and financial-nullability drift is rejected"
