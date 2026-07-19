#!/usr/bin/env bash
set -euo pipefail

# The operational schema is intentionally a fixed allowlist: it admits no raw
# request header, body, subject, or business-record field.
logger=backend/src/main/java/com/srm/creditengine/shared/runtime/SafeOperationalLogger.java
telemetry=backend/src/main/java/com/srm/creditengine/shared/runtime/FinancialTelemetry.java

rg -q 'HTTP_REQUEST_COMPLETED' "$logger"
rg -q 'FINANCIAL_CONFLICT' "$logger"
! rg -ni 'authorization|bearer|jwt|password|idempotency|request\.getHeader|request\.getBody|email|subject|payload|receivableId|settlementId' "$logger" >/dev/null
! rg -ni 'counter\([^)]*(actor|idempotency|correlation|receivable|settlementid|quoteid|payload|email|password|jwt)' "$telemetry" >/dev/null
rg -Fq 'allowed.contains(normalized)' "$telemetry"

echo 'OBS-RED-001 passed: logs and metric labels exclude credentials, request data, and business identifiers'
