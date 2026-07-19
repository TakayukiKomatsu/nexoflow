#!/usr/bin/env bash
set -euo pipefail

compose=(docker compose)

health_status() {
  "${compose[@]}" --profile smoke run --rm --no-deps --entrypoint curl smoke \
    --silent --output /dev/null --write-out '%{http_code}' "$1"
}

wait_for_status() {
  local endpoint="$1"
  local expected="$2"
  local actual
  for _ in $(seq 1 30); do
    actual="$(health_status "$endpoint" || true)"
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "OPS-RUN-001 failed: expected $endpoint to return $expected, got ${actual:-no response}" >&2
  return 1
}

wait_for_postgres_health() {
  local container
  container="$("${compose[@]}" ps -q postgres)"
  for _ in $(seq 1 30); do
    if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$container")" == "healthy" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "OPS-RUN-001 failed: PostgreSQL did not become healthy" >&2
  return 1
}

wait_for_status http://backend:8080/actuator/health/liveness 200
wait_for_status http://backend:8080/actuator/health/readiness 200
"${compose[@]}" stop postgres
wait_for_status http://backend:8080/actuator/health/liveness 200
wait_for_status http://backend:8080/actuator/health/readiness 503
"${compose[@]}" start postgres
wait_for_postgres_health
wait_for_status http://backend:8080/actuator/health/readiness 200

echo "OPS-RUN-001 passed: readiness follows PostgreSQL recovery without restarting the backend"
