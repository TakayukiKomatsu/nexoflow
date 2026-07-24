#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
template="$repo_root/backend/src/main/resources/sql/settlement-statement.sql"
renderer="$repo_root/scripts/render-statement-evidence-sql.mjs"
runner="$repo_root/scripts/explain-statements-representative.sh"
dataset="$repo_root/scripts/sql/representative_statement_dataset.sql"
service_sql="$repo_root/backend/src/main/java/com/srm/creditengine/reporting/application/SettlementStatementSql.java"
obsolete_copy="$repo_root/scripts/sql/explain_settlement_statement.sql"
temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT

for required_file in "$template" "$renderer" "$runner" "$dataset" "$service_sql"; do
    [[ -s "$required_file" ]] \
        || { echo "reporting evidence contract is missing $required_file" >&2; exit 1; }
done
[[ ! -e "$obsolete_copy" ]] \
    || { echo "copied reporting query must be removed: $obsolete_copy" >&2; exit 1; }

rendered="$temporary_directory/rendered.sql"
node "$renderer" --template "$template" > "$rendered"

for evidence_case in baseline assignor asset_currency settlement_currency product_type combined; do
    grep -Fq "SRM_FILTER_CASE|$evidence_case" "$rendered" \
        || { echo "renderer omitted filter case $evidence_case" >&2; exit 1; }
    grep -Fq "SRM_FILTER_COUNT|$evidence_case|" "$rendered" \
        || { echo "renderer omitted count proof for $evidence_case" >&2; exit 1; }
    grep -Fq "SRM_PLAN_CASE|$evidence_case" "$rendered" \
        || { echo "renderer omitted EXPLAIN proof for $evidence_case" >&2; exit 1; }
done

for predicate in \
    "assignor_id = md5('rep:assignor:1')::uuid" \
    "asset_currency_code = 'USD'" \
    "settlement_currency_code = 'USD'" \
    "product_type_code = 'POST_DATED_CHEQUE'"; do
    grep -Fq "$predicate" "$rendered" \
        || { echo "renderer omitted selective predicate: $predicate" >&2; exit 1; }
done

if grep -Eiq '^[[:space:]]*(create|alter)[[:space:]]+(table|index)' "$dataset"; then
    echo "representative dataset must contain data only, never copied schema DDL" >&2
    exit 1
fi
grep -Fq 'Flyway schema history is absent' "$dataset" \
    || { echo "dataset does not fail closed when Flyway is absent" >&2; exit 1; }
grep -Fq 'static final String RESOURCE = "sql/settlement-statement.sql"' "$service_sql" \
    || { echo "runtime does not load the production-owned statement SQL resource" >&2; exit 1; }
grep -Fq 'compose up --build -d --wait postgres mock-fx backend' "$runner" \
    || { echo "evidence runner does not boot the real backend migration path" >&2; exit 1; }
grep -Fq 'from flyway_schema_history where success' "$runner" \
    || { echo "evidence runner does not verify the applied Flyway chain" >&2; exit 1; }
for index_name in \
    settlements_statement_filter_idx \
    settlement_reversals_statement_filter_idx \
    settlement_items_product_statement_idx; do
    grep -Fq "$index_name" "$runner" \
        || { echo "evidence runner does not verify current index $index_name" >&2; exit 1; }
done

missing_marker="$temporary_directory/missing-marker.sql"
grep -Fv '/*?productType*/' "$template" > "$missing_marker"
if node "$renderer" --template "$missing_marker" \
        >"$temporary_directory/missing-marker.out" \
        2>"$temporary_directory/missing-marker.err"; then
    echo "renderer accepted a template with a missing product filter marker" >&2
    exit 1
fi
grep -Fq 'SQL template filter markers must be exactly' "$temporary_directory/missing-marker.err" \
    || { echo "missing-marker mutation failed for an unexpected reason" >&2; exit 1; }

mutated_query="$temporary_directory/mutated-query.sql"
sed "s/'SETTLEMENT' as entry_type/'SETTLEMENT_MUTATED' as entry_type/" \
    "$template" > "$mutated_query"
node "$renderer" --template "$mutated_query" > "$temporary_directory/mutated-render.sql"
grep -Fq "'SETTLEMENT_MUTATED' as entry_type" "$temporary_directory/mutated-render.sql" \
    || { echo "renderer did not consume the mutated production query resource" >&2; exit 1; }

echo "REPORT-EVIDENCE-001 passed: runtime and evidence share one mutation-sensitive SQL template"
echo "REPORT-EVIDENCE-002 passed: evidence uses Flyway schema authority and proves every filter branch"
