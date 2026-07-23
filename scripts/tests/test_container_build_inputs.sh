#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

for dockerfile in "$repo_root/backend/Dockerfile" "$repo_root/frontend/Dockerfile"; do
  while IFS= read -r from_line; do
    if [[ ! "$from_line" =~ ^FROM[[:space:]]+[^[:space:]]+@sha256:[0-9a-f]{64}([[:space:]]+AS[[:space:]]+[A-Za-z0-9_-]+)?$ ]]; then
      echo "CONTAINER-PIN-001 failed: unpinned Dockerfile base: $from_line" >&2
      exit 1
    fi
  done < <(grep '^FROM ' "$dockerfile")
done

while IFS= read -r image_line; do
  if [[ ! "$image_line" =~ image:[[:space:]]+[^[:space:]]+@sha256:[0-9a-f]{64}$ ]]; then
    echo "CONTAINER-PIN-001 failed: unpinned Compose image: $image_line" >&2
    exit 1
  fi
done < <(grep -E '^[[:space:]]+image:' "$repo_root/compose.yaml")

if grep -REn 'apk[[:space:]]+upgrade' "$repo_root/backend/Dockerfile" "$repo_root/frontend/Dockerfile"; then
  echo "CONTAINER-PIN-002 failed: runtime package upgrades make builds time-dependent" >&2
  exit 1
fi

for dockerfile in "$repo_root/backend/Dockerfile" "$repo_root/frontend/Dockerfile"; do
  final_user="$(grep '^USER ' "$dockerfile" | tail -1 | awk '{ print $2 }')"
  if [[ -z "$final_user" || "$final_user" == "root" || "$final_user" == "0" ]]; then
    echo "CONTAINER-PIN-003 failed: Dockerfile must select a final non-root USER: $dockerfile" >&2
    exit 1
  fi
  if ! awk '
    /^RUN apk add / { in_apk = 1; next }
    in_apk {
      line = $0
      sub(/^[[:space:]]+/, "", line)
      sub(/[[:space:]]*\\$/, "", line)
      if (line !~ /^[a-z0-9+_.-]+=[a-z0-9+_.-]+$/) failed = 1
      if ($0 !~ /\\$/) in_apk = 0
    }
    END { exit failed }
  ' "$dockerfile"; then
    echo "CONTAINER-PIN-002 failed: apk package upgrades must pin exact versions: $dockerfile" >&2
    exit 1
  fi
done

legacy_zone_passthrough='SRM_MIGRATION_V23_LEGACY_TIME_ZONE: ${SRM_MIGRATION_V23_LEGACY_TIME_ZONE:-}'
[[ "$(grep -Fc "$legacy_zone_passthrough" "$repo_root/compose.yaml")" == 3 ]] || {
  echo "CONTAINER-CONFIG-004 failed: every Flyway-capable Compose service must receive the optional V23 legacy time zone" >&2
  exit 1
}
grep -Eq '^SRM_MIGRATION_V23_LEGACY_TIME_ZONE=' "$repo_root/.env.example" || {
  echo "CONTAINER-CONFIG-004 failed: .env.example must expose the V23 legacy time-zone override" >&2
  exit 1
}

echo "CONTAINER-PIN-001 passed: every Dockerfile and Compose base image is digest-pinned"
echo "CONTAINER-PIN-002 passed: runtime images do not perform uncontrolled apk upgrades"
echo "CONTAINER-PIN-003 passed: runtime Dockerfiles select an explicit non-root user"
echo "CONTAINER-CONFIG-004 passed: every Flyway-capable Compose service exposes the V23 legacy time-zone override"
