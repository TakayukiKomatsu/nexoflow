#!/usr/bin/env zsh
set -euo pipefail

set +u
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env >/dev/null
set -u

exec "$@"
