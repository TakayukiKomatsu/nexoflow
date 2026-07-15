#!/usr/bin/env zsh
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
config="$repo_root/.sdkmanrc"

if [[ ! -f "$config" ]]; then
  echo "expected SDKMAN project configuration at $config" >&2
  exit 1
fi

if ! grep -Fxq 'java=21.0.8-tem' "$config"; then
  echo "expected .sdkmanrc to pin java=21.0.8-tem" >&2
  exit 1
fi

set +u
source "$HOME/.sdkman/bin/sdkman-init.sh"
cd "$repo_root"
sdk env >/dev/null
set -u
major="$(java -version 2>&1 | awk -F '[\".]' '/version/ { print $2; exit }')"
if [[ "$major" != '21' ]]; then
  echo "expected SDKMAN environment to select Java 21, got Java $major" >&2
  exit 1
fi

echo "JAVA-ENV-001 passed: SDKMAN selects Java 21"
