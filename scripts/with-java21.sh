#!/usr/bin/env zsh
set -euo pipefail

set +u
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env >/dev/null
set -u

if [[ "$(uname -s)" == "Darwin" && -S "${HOME}/.colima/default/docker.sock" ]]; then
  export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  export TESTCONTAINERS_HOST_OVERRIDE="$(colima ls -j | jq -r '.address')"
fi

exec "$@"
