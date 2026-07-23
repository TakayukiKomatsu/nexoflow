#!/usr/bin/env bash
set -euo pipefail

# The operational schema is intentionally a fixed allowlist: it admits no raw
# request header, body, subject, or business-record field. Production logging
# must remain centralized so newly added callsites cannot bypass that schema.
logger=backend/src/main/java/com/srm/creditengine/shared/runtime/SafeOperationalLogger.java
telemetry=backend/src/main/java/com/srm/creditengine/shared/runtime/FinancialTelemetry.java
frontend_logger=frontend/src/observability/SafeUiLogger.ts
log_call_pattern='LoggerFactory|getLogger[[:space:]]*\(|\b(LOG|log|logger)\.(trace|debug|info|warn|error)[[:space:]]*\(|\.(atTrace|atDebug|atInfo|atWarn|atError)[[:space:]]*\(|System\.(out|err)\.(print|println|printf)[[:space:]]*\(|console\.(log|info|warn|error|debug)[[:space:]]*\('
forbidden_log_field_pattern='authorization|bearer|jwt|password|secret|token|credential|api.?key|idempotency|request\.getHeader|request\.getBody|email|subject|payload|receivableId|settlementId'

validate_log_callsites() {
  local allowed_gateway=$1
  shift
  local callsites
  local callsite
  local found_gateway=false
  local unexpected=false

  callsites=$(rg -l \
    --glob '*.java' --glob '*.ts' --glob '*.tsx' --glob '*.js' --glob '*.jsx' \
    "$log_call_pattern" "$@" || true)
  while IFS= read -r callsite; do
    [[ -z "$callsite" ]] && continue
    if [[ "$callsite" == "$allowed_gateway" ]]; then
      found_gateway=true
    else
      echo "Unapproved production log callsite: $callsite" >&2
      unexpected=true
    fi
  done <<< "$callsites"

  [[ "$found_gateway" == true && "$unexpected" == false ]]
}

validate_log_callsites "$logger" backend/src/main/java
validate_log_callsites "$frontend_logger" frontend/src

rg -q 'HTTP_REQUEST_COMPLETED' "$logger"
rg -q 'FINANCIAL_CONFLICT' "$logger"
if rg -ni "$forbidden_log_field_pattern" "$logger" >/dev/null; then
  echo "Operational logger contains a forbidden sensitive-field reference: $logger" >&2
  exit 1
fi
if rg -ni 'counter\([^)]*(actor|idempotency|correlation|receivable|settlementid|quoteid|payload|email|password|jwt)' "$telemetry" >/dev/null; then
  echo "Financial telemetry attaches a forbidden high-cardinality field: $telemetry" >&2
  exit 1
fi
rg -Fq 'allowed.contains(normalized)' "$telemetry"
rg -Fqx '  console.error(UI_RENDER_FAILURE);' "$frontend_logger"
[[ "$(rg -c "$log_call_pattern" "$frontend_logger")" == 1 ]]
if rg -ni 'authorization|bearer|jwt|password|secret|token|credential|api.?key|idempotency|email|subject|payload|receivable|settlement|quote' "$frontend_logger" >/dev/null; then
  echo "UI logger contains a forbidden sensitive-field reference: $frontend_logger" >&2
  exit 1
fi

mutation_root=$(mktemp -d)
trap 'rm -rf -- "$mutation_root"' EXIT
cp -R backend/src/main/java/. "$mutation_root"/
mkdir -p "$mutation_root/com/srm/creditengine/mutation"
cp scripts/tests/fixtures/log-redaction/SecretBearingLogMutation.java.fixture \
  "$mutation_root/com/srm/creditengine/mutation/SecretBearingLogMutation.java"
mutation_gateway="$mutation_root/com/srm/creditengine/shared/runtime/SafeOperationalLogger.java"
if validate_log_callsites "$mutation_gateway" "$mutation_root" >/dev/null 2>&1; then
  echo 'Log-redaction validator accepted a secret-bearing production log mutation' >&2
  exit 1
fi
if ! rg -ni "$forbidden_log_field_pattern" \
  scripts/tests/fixtures/log-redaction/SecretBearingLogMutation.java.fixture >/dev/null; then
  echo 'Secret-bearing mutation fixture no longer exercises the forbidden-field detector' >&2
  exit 1
fi

echo 'OBS-RED-001 passed: repository log callsites are centralized and the secret-log mutation is rejected'
