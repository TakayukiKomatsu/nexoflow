#!/usr/bin/env bash
# OBS-INSPECT: Log in as the local ADMIN reviewer account, call /actuator/prometheus
# with the bearer token (never printed), and assert bounded SRM metric presence
# and absence of sensitive or identifier-shaped label values.
set -euo pipefail

# Pass credentials by environment name rather than embedding them in the shell
# command line. The disposable smoke container is removed after the scrape.
export SRM_DEV_ADMIN_EMAIL="${SRM_DEV_ADMIN_EMAIL:-admin@srm.local}"
export SRM_DEV_ADMIN_PASSWORD="${SRM_DEV_ADMIN_PASSWORD:-local-admin-review-only-not-a-real-password}"
trap 'unset SRM_DEV_ADMIN_EMAIL SRM_DEV_ADMIN_PASSWORD' EXIT

_inner=$(cat <<'INNER'
set -eu
LOGIN=$(
  printf '{"email":"%s","password":"%s"}' \
    "$SRM_DEV_ADMIN_EMAIL" "$SRM_DEV_ADMIN_PASSWORD" \
    | curl --fail --silent --show-error \
        -H "Content-Type: application/json" \
        --data-binary @- \
        http://backend:8080/api/v1/auth/login
)
TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "OBS-INSPECT-000 failed: ADMIN login returned no access token" >&2; exit 1; }
printf 'header = "Authorization: Bearer %s"\nurl = "http://backend:8080/actuator/prometheus"\n' "$TOKEN" \
  | curl --fail --silent --show-error --config -
INNER
)

metrics="$(docker compose --profile smoke run --rm --no-deps \
  -e SRM_DEV_ADMIN_EMAIL -e SRM_DEV_ADMIN_PASSWORD \
  --entrypoint sh smoke -c "$_inner")"

srm_metrics="$(printf '%s\n' "$metrics" | grep -E '^srm_' || true)"
[[ -n "$srm_metrics" ]] || {
  echo 'OBS-INSPECT-001 failed: no SRM financial metrics were exposed' >&2
  exit 1
}

for required in \
  srm_quote_duration_seconds \
  srm_settlement_duration_seconds \
  srm_report_duration_seconds \
  srm_fx_provider_attempt_duration_seconds \
  srm_quote_outcomes_total \
  srm_simulation_outcomes_total \
  srm_settlement_outcomes_total \
  srm_fx_stale_rates_total \
  srm_fx_provider_failures_total \
  srm_fx_resilience_outcomes_total \
  srm_statement_queries_total; do
  printf '%s\n' "$srm_metrics" | grep -q "^${required}" || {
    echo "OBS-INSPECT-001 failed: required metric $required was not exposed" >&2
    exit 1
  }
done

! printf '%s\n' "$srm_metrics" \
  | grep -iE 'authorization|bearer|jwt|password|token|email|idempotency|correlation|assignor|receivable|quote_id|settlement_id|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' \
  > /dev/null \
  || {
    echo 'OBS-INSPECT-002 failed: sensitive or identifier-shaped SRM metric data was exposed' >&2
    exit 1
  }

echo 'OBS-INSPECT-001 passed: ADMIN-authenticated scrape contains every required bounded SRM metric'
echo 'OBS-INSPECT-002 passed: SRM metrics contain no sensitive or identifier-shaped labels'
