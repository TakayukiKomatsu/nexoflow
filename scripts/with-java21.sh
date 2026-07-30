#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" == "Darwin" &&
      -s "$HOME/.sdkman/bin/sdkman-init.sh" &&
      "${SRM_JAVA21_ZSH:-0}" != "1" ]]; then
  export SRM_JAVA21_ZSH=1
  exec zsh "$0" "$@"
fi

if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  set +u
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env >/dev/null
  set -u
fi

if [[ "$(uname -s)" == "Darwin" && -S "${HOME}/.colima/default/docker.sock" ]]; then
  export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  export TESTCONTAINERS_HOST_OVERRIDE="$(colima ls -j | jq -r '.address')"
fi

exec "$@"
