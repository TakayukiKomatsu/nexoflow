#!/usr/bin/env bash
set -euo pipefail

fixture_state="CRISIS_FIXTURE_STATE=healthy"
[[ "$fixture_state" == *"=healthy" ]] \
  || { echo "CRISIS-002 fixture failed: expected healthy state" >&2; exit 1; }

echo "CRISIS-002 fixture passed"
