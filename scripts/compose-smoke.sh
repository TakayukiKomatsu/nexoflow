#!/bin/sh
set -eu

curl --fail --silent --show-error http://frontend:8080/ >/dev/null
curl --fail --silent --show-error http://backend:8080/actuator/health/liveness >/dev/null
curl --fail --silent --show-error http://backend:8080/actuator/health/readiness >/dev/null
curl --fail --silent --show-error http://mock-fx:8080/api/v1/rates/USD-BRL >/dev/null

# No request body, JWT, idempotency key, or UUID is printed by this smoke test.
require_json_safe() {
  if printf '%s' "$1" | LC_ALL=C grep -q '[[:cntrl:]]'; then
    echo 'OBS-SMOKE-001 failed: configured credentials contain a JSON control character' >&2
    exit 1
  fi
}

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/\\n/g'
}

login() {
  require_json_safe "$1"
  require_json_safe "$2"
  email="$(json_escape "$1")"
  password="$(json_escape "$2")"
  response="$(printf '{"email":"%s","password":"%s"}' "$email" "$password" | curl --fail --silent --show-error -H 'Content-Type: application/json' --data-binary @- \
    http://backend:8080/api/v1/auth/login)"
  printf '%s' "$response" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}
admin_token="$(login "$SRM_SMOKE_ADMIN_EMAIL" "$SRM_SMOKE_ADMIN_PASSWORD")"
operator_token="$(login "$SRM_SMOKE_OPERATOR_EMAIL" "$SRM_SMOKE_OPERATOR_PASSWORD")"
[ -n "$admin_token" ] && [ -n "$operator_token" ] || {
  echo 'OBS-SMOKE-001 failed: local reviewer login returned no access token' >&2
  exit 1
}
admin_auth="Authorization: Bearer $admin_token"
operator_auth="Authorization: Bearer $operator_token"
assignor_id="$(cat /proc/sys/kernel/random/uuid)"
assignor_tax_id="$(printf '%s' "$assignor_id" | tr -d '-')"
receivable_id="$(cat /proc/sys/kernel/random/uuid)"
rates="$(curl --fail --silent --show-error -H "$admin_auth" \
  'http://backend:8080/api/v1/exchange-rates?base=USD&quote=BRL')"
case "$rates" in
  *'"rate":5.2000000000,"source":"deterministic-mock","observedAt":"2030-01-15T12:00:00Z"'*) ;;
  *)
    curl --fail --silent --show-error -X POST -H "$admin_auth" \
      'http://backend:8080/api/v1/fx-sync?base=USD&quote=BRL' >/dev/null
    ;;
esac
curl --fail --silent --show-error -H "$operator_auth" -H 'Content-Type: application/json' \
  -d "{\"id\":\"$assignor_id\",\"legalName\":\"Compose Smoke Assignor\",\"taxId\":\"$assignor_tax_id\",\"active\":true}" \
  http://backend:8080/api/v1/assignors >/dev/null
curl --fail --silent --show-error -H "$operator_auth" -H 'Content-Type: application/json' \
  -d "{\"id\":\"$receivable_id\",\"assignorId\":\"$assignor_id\",\"productType\":\"MERCANTILE_INVOICE\",\"faceAmount\":\"1000.0000\",\"faceCurrency\":\"BRL\",\"issueDate\":\"2026-01-01\",\"dueDate\":\"2030-12-31\"}" \
  http://backend:8080/api/v1/receivables >/dev/null
curl --fail --silent --show-error -H "$operator_auth" -H 'Content-Type: application/json' \
  -d '{"faceAmount":"1000.0000","faceCurrency":"BRL","productType":"MERCANTILE_INVOICE","dueDate":"2030-12-31","settlementCurrency":"BRL"}' \
  http://backend:8080/api/v1/pricing-simulations >/dev/null
quote="$(curl --fail --silent --show-error -H "$operator_auth" -H 'Content-Type: application/json' -d "{\"receivableId\":\"$receivable_id\",\"settlementCurrency\":\"BRL\"}" http://backend:8080/api/v1/pricing-quotes)"
quote_id="$(printf '%s' "$quote" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
[ -n "$quote_id" ] || { echo 'OBS-SMOKE-002 failed: quote was not created' >&2; exit 1; }
idempotency_key="smoke-$(cat /proc/sys/kernel/random/uuid)"
curl --fail --silent --show-error -H "$operator_auth" -H 'Content-Type: application/json' -H "Idempotency-Key: $idempotency_key" -d "{\"quoteIds\":[\"$quote_id\"]}" http://backend:8080/api/v1/settlements >/dev/null
curl --fail --silent --show-error -H "$operator_auth" http://backend:8080/api/v1/settlement-statements >/dev/null

echo 'OBS-SMOKE-003 passed: login, simulation, quote, preview, settlement, and statement completed without exposing request secrets'
