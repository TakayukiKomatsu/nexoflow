#!/usr/bin/env bash
# Capture representative PostgreSQL plans for the production settlement-statement query.
#
# The backend is started first so Flyway applies the exact SQL and Java migration
# chain. The transaction below then inserts data only, renders every query from
# the production-owned classpath SQL template, captures a selective filter
# matrix, and rolls all representative rows back.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATASET_SQL="${SCRIPT_DIR}/sql/representative_statement_dataset.sql"
QUERY_TEMPLATE="${REPO_ROOT}/backend/src/main/resources/sql/settlement-statement.sql"
QUERY_RENDERER="${SCRIPT_DIR}/render-statement-evidence-sql.mjs"
SQL_MIGRATIONS="${REPO_ROOT}/backend/src/main/resources/db/migration"
JAVA_MIGRATIONS="${REPO_ROOT}/backend/src/main/java/db/migration"
EVIDENCE_FILE="${REPO_ROOT}/docs/evidence/reporting-explain.txt"
RENDERED_SQL="$(mktemp)"

PG_DB="srm_credit_engine"
PG_USER="srm"
PGPASSWORD="${POSTGRES_PASSWORD:-srm-local-only}"
export POSTGRES_PASSWORD="${PGPASSWORD}"
COMPOSE_PROJECT="srm-explain-$$"

compose() {
    docker compose \
        --project-name "${COMPOSE_PROJECT}" \
        --project-directory "${REPO_ROOT}" \
        --file "${REPO_ROOT}/compose.yaml" \
        "$@"
}

log() {
    printf '[explain-statements-representative] %s\n' "$*" >&2
}

checksum() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

teardown() {
    rm -f "${RENDERED_SQL}"
    log "Stopping isolated Compose project ${COMPOSE_PROJECT}..."
    compose down -v --remove-orphans >/dev/null 2>&1 || true
}

count_for() {
    local case_name="$1"
    printf '%s\n' "${PSQL_OUTPUT}" \
        | sed -n "s/^SRM_FILTER_COUNT|${case_name}|//p" \
        | tail -1
}

assert_selective_count() {
    local case_name="$1"
    local count
    count="$(count_for "${case_name}")"
    if ! printf '%s' "${count}" | grep -Eq '^[0-9]+$'; then
        log "ERROR: filter case ${case_name} emitted no numeric count"
        exit 1
    fi
    if [ "${count}" -le 0 ] || [ "${count}" -ge "${BASELINE_COUNT}" ]; then
        log "ERROR: filter case ${case_name} must be positive and selective; got ${count}, baseline ${BASELINE_COUNT}"
        exit 1
    fi
}

trap teardown EXIT

command -v docker >/dev/null 2>&1 || { log "ERROR: docker not found"; exit 1; }
docker info >/dev/null 2>&1 || { log "ERROR: Docker daemon is not running"; exit 1; }
docker compose version >/dev/null 2>&1 || { log "ERROR: Docker Compose is unavailable"; exit 1; }
command -v node >/dev/null 2>&1 || { log "ERROR: node not found"; exit 1; }
[ -f "${DATASET_SQL}" ] || { log "ERROR: ${DATASET_SQL} not found"; exit 1; }
[ -f "${QUERY_TEMPLATE}" ] || { log "ERROR: ${QUERY_TEMPLATE} not found"; exit 1; }
[ -f "${QUERY_RENDERER}" ] || { log "ERROR: ${QUERY_RENDERER} not found"; exit 1; }

node "${QUERY_RENDERER}" --template "${QUERY_TEMPLATE}" > "${RENDERED_SQL}"

EXPECTED_MIGRATION_VERSIONS="$(
    find "${SQL_MIGRATIONS}" "${JAVA_MIGRATIONS}" -type f -name 'V*__*' -print \
        | sed -E 's#^.*/V([0-9]+)__.*#\1#' \
        | sort -n
)"
EXPECTED_LATEST_MIGRATION="$(printf '%s\n' "${EXPECTED_MIGRATION_VERSIONS}" | tail -1)"
[ -n "${EXPECTED_LATEST_MIGRATION}" ] \
    || { log "ERROR: no production Flyway migrations were found"; exit 1; }

log "Starting the real backend so Flyway applies the production migration chain..."
compose up --build -d --wait postgres mock-fx backend

SCHEMA_METADATA="$(
    compose exec -T \
        -e "PGPASSWORD=${PGPASSWORD}" \
        postgres \
        psql -U "${PG_USER}" "${PG_DB}" --no-psqlrc --tuples-only --no-align \
        -c "select 'SRM_MIGRATION|' || version || '|' || description || '|' || type
              from flyway_schema_history where success order by installed_rank;
            select 'SRM_INDEX|' || indexname
              from pg_indexes
             where schemaname = 'public'
               and indexname in (
                 'settlements_created_at_idx',
                 'settlements_statement_filter_idx',
                 'settlement_reversals_settlement_idx',
                 'settlement_reversals_statement_filter_idx',
                 'settlement_items_statement_dimensions_idx',
                 'settlement_items_product_statement_idx'
               )
             order by indexname;"
)"

ACTUAL_LATEST_MIGRATION="$(
    printf '%s\n' "${SCHEMA_METADATA}" \
        | sed -n 's/^SRM_MIGRATION|\([^|]*\)|.*$/\1/p' \
        | tail -1
)"
ACTUAL_MIGRATION_VERSIONS="$(
    printf '%s\n' "${SCHEMA_METADATA}" \
        | sed -n 's/^SRM_MIGRATION|\([^|]*\)|.*$/\1/p'
)"
if [ "${ACTUAL_MIGRATION_VERSIONS}" != "${EXPECTED_MIGRATION_VERSIONS}" ]; then
    log "ERROR: applied Flyway versions do not exactly match repository migration versions"
    printf 'expected:\n%s\nactual:\n%s\n' \
        "${EXPECTED_MIGRATION_VERSIONS}" "${ACTUAL_MIGRATION_VERSIONS}" >&2
    exit 1
fi
if [ "${ACTUAL_LATEST_MIGRATION}" != "${EXPECTED_LATEST_MIGRATION}" ]; then
    log "ERROR: Flyway applied through V${ACTUAL_LATEST_MIGRATION:-none}; repository requires V${EXPECTED_LATEST_MIGRATION}"
    exit 1
fi

for required_index in \
    settlements_created_at_idx \
    settlements_statement_filter_idx \
    settlement_reversals_settlement_idx \
    settlement_reversals_statement_filter_idx \
    settlement_items_statement_dimensions_idx \
    settlement_items_product_statement_idx; do
    printf '%s\n' "${SCHEMA_METADATA}" | grep -Fqx "SRM_INDEX|${required_index}" \
        || { log "ERROR: migrated schema is missing reporting index ${required_index}"; exit 1; }
done

# No application traffic should alter the deterministic evidence dataset after migration.
compose stop backend mock-fx >/dev/null

PG_VERSION="$(
    compose exec -T \
        -e "PGPASSWORD=${PGPASSWORD}" \
        postgres \
        psql -U "${PG_USER}" "${PG_DB}" --no-psqlrc --tuples-only --no-align \
        -c "select version()" 2>/dev/null
)"

log "Loading representative rows and executing the shared-query filter matrix..."
if ! PSQL_OUTPUT="$(
    {
        printf '\\set ON_ERROR_STOP on\n'
        printf 'BEGIN;\n\n'
        sed 's/[[:space:]]*$//' "${DATASET_SQL}"
        printf '\n\n'
        sed 's/[[:space:]]*$//' "${RENDERED_SQL}"
        printf '\n\nROLLBACK;\n'
    } | compose exec -T \
            -e "PGPASSWORD=${PGPASSWORD}" \
            postgres \
            psql -U "${PG_USER}" "${PG_DB}" \
                 --no-psqlrc \
                 --set ON_ERROR_STOP=1 \
                 --no-align \
                 --tuples-only \
                 2>&1
)"; then
    log "ERROR: PostgreSQL evidence session failed"
    printf '%s\n' "${PSQL_OUTPUT}" >&2
    exit 1
fi

BASELINE_COUNT="$(count_for baseline)"
if ! printf '%s' "${BASELINE_COUNT}" | grep -Eq '^[0-9]+$' \
        || [ "${BASELINE_COUNT}" -le 0 ]; then
    log "ERROR: baseline query emitted no positive count"
    exit 1
fi
for filter_case in assignor asset_currency settlement_currency product_type combined; do
    assert_selective_count "${filter_case}"
done

CAPTURE_UTC="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
DATASET_CHECKSUM="$(checksum "${DATASET_SQL}")"
TEMPLATE_CHECKSUM="$(checksum "${QUERY_TEMPLATE}")"
FILTER_SUMMARY="$(
    printf '%s\n' "${PSQL_OUTPUT}" | grep '^SRM_FILTER_COUNT|' || true
)"

mkdir -p "$(dirname "${EVIDENCE_FILE}")"
log "Writing evidence to ${EVIDENCE_FILE}..."
{
cat <<HEADER
SRM Settlement-Statement Representative EXPLAIN Evidence
========================================================

Machine-generated by: scripts/explain-statements-representative.sh
Re-run via:           make explain-statements-representative

The backend applied the repository's real Flyway SQL and Java migrations.
Representative rows were then inserted and rolled back. Every count and plan
was rendered from the same classpath SQL template consumed in production.

── UTC Capture Time ─────────────────────────────────────────────────────────
${CAPTURE_UTC}

── PostgreSQL Version ───────────────────────────────────────────────────────
${PG_VERSION}

── Production Schema Authority ──────────────────────────────────────────────
  SQL migrations : backend/src/main/resources/db/migration
  Java migrations: backend/src/main/java/db/migration
  Applied through: V${ACTUAL_LATEST_MIGRATION}

${SCHEMA_METADATA}

── Data and Query Authority ─────────────────────────────────────────────────
  Data-only seed : scripts/sql/representative_statement_dataset.sql
  Seed SHA-256   : ${DATASET_CHECKSUM}
  Runtime query  : backend/src/main/resources/sql/settlement-statement.sql
  Query SHA-256  : ${TEMPLATE_CHECKSUM}
  Renderer       : scripts/render-statement-evidence-sql.mjs

  JdbcSettlementStatementService loads the runtime query resource. The
  renderer expands its named optional-filter markers with evidence literals;
  no second query body or copied schema exists under scripts/sql.

── Representative Dataset ──────────────────────────────────────────────────
  assignors            :     10
  receivables          : 10,000
  pricing_quotes       : 10,000
  settlements          : 10,000
  settlement_items     : 10,000
  settlement_reversals :  1,000

  Window: [2030-01-10 00:00:00, 2030-01-20 00:00:00)
  Dimensions: ten assignors, BRL/USD asset currencies, BRL/USD settlement
  currencies, and both supported product types.

── Selectivity Proof ────────────────────────────────────────────────────────
Each named filter count is asserted by the runner to be positive and lower
than the unfiltered time-window baseline. The combined case exercises all
four dimensions in one production query.

${FILTER_SUMMARY}

── Representative Evidence Disclaimer ─────────────────────────────────────
This 10,000-row synthetic dataset proves production query shape, current
migration compatibility, filter selectivity, and real PostgreSQL execution.
It is not a production-capacity benchmark and does not guarantee a particular
planner node. Sequential scans may be rational at this scale and selectivity.

── Production SQL Template ──────────────────────────────────────────────────
HEADER

sed 's/[[:space:]]*$//' "${QUERY_TEMPLATE}"

cat <<HEADER

── Rendered Query and Plan Matrix ───────────────────────────────────────────
HEADER

sed 's/[[:space:]]*$//' "${RENDERED_SQL}"

cat <<DIVIDER

── PostgreSQL Session Output (BEGIN → seed → matrix → ROLLBACK) ─────────────
DIVIDER

printf '%s\n' "${PSQL_OUTPUT}" | sed 's/[[:space:]]*$//'

cat <<FOOTER

── Index Scope ───────────────────────────────────────────────────────────────
The migrated schema was checked before execution for all current reporting
indexes: settlements_created_at_idx, settlements_statement_filter_idx,
settlement_reversals_settlement_idx,
settlement_reversals_statement_filter_idx,
settlement_items_statement_dimensions_idx, and
settlement_items_product_statement_idx.

The plans above are retained for review, but the gate does not assert a
specific planner node. It fails closed on migration drift, missing indexes,
query-template marker drift, SQL errors, empty cases, or non-selective filters.

── End of Evidence ───────────────────────────────────────────────────────────
FOOTER
} > "${EVIDENCE_FILE}"

log "Done. Evidence written to ${EVIDENCE_FILE}"
