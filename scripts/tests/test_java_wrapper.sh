#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
sandbox="$(mktemp -d)"
trap 'rm -rf "$sandbox"' EXIT

mkdir -p "$sandbox/home" "$sandbox/bin"
ln -s "$(command -v bash)" "$sandbox/bin/bash"
ln -s "$(command -v uname)" "$sandbox/bin/uname"

output="$({
  HOME="$sandbox/home" \
  PATH="$sandbox/bin" \
    "$repo_root/scripts/with-java21.sh" bash -c 'printf portable-java-wrapper'
} 2>&1)" || {
  echo "CI-JAVA-001 FAILED: Java wrapper requires unavailable shell or SDKMAN state: $output" >&2
  exit 1
}

if [[ "$output" != "portable-java-wrapper" ]]; then
  echo "CI-JAVA-001 FAILED: unexpected Java wrapper output: $output" >&2
  exit 1
fi

echo "CI-JAVA-001 passed: Java wrapper runs with bash and without SDKMAN"
