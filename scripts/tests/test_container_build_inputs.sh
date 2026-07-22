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

echo "CONTAINER-PIN-001 passed: every Dockerfile and Compose base image is digest-pinned"
echo "CONTAINER-PIN-002 passed: runtime images do not perform uncontrolled apk upgrades"
