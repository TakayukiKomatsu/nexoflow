#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
test_dir="$(mktemp -d)"
fake_bin="$test_dir/bin"
test_repo="$test_dir/repo"
docker_log="$test_dir/docker.log"
make_log="$test_dir/make.log"
mkdir -p "$fake_bin" "$test_repo/scripts" "$test_repo/backend" "$test_repo/frontend"
cp "$repo_root/scripts/security-scan.sh" "$test_repo/scripts/security-scan.sh"
cp "$repo_root/backend/Dockerfile" "$test_repo/backend/Dockerfile"
cp "$repo_root/frontend/Dockerfile" "$test_repo/frontend/Dockerfile"
cp "$repo_root/.trivyignore.yaml" "$test_repo/.trivyignore.yaml"
git -C "$test_repo" init --quiet

cleanup() {
  rm -rf "$test_dir"
}
trap cleanup EXIT

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_DOCKER_LOG"

case "${1:-}" in
  run)
    if [[ "$*" == *"gitleaks"* && "${FAKE_DOCKER_FAIL_AT:-}" == "gitleaks" ]]; then
      exit 41
    fi
    case "$*" in
      *gitleaks.json*)
        if [[ "${FAKE_DOCKER_SKIP_REPORT:-}" != "gitleaks" ]]; then
          printf '[]\n' > "$FAKE_REPO_ROOT/build/security/gitleaks.json"
        fi
        ;;
      *trivy-filesystem.json*) printf '{}\n' > "$FAKE_REPO_ROOT/build/security/trivy-filesystem.json" ;;
      *trivy-secrets.json*) printf '{}\n' > "$FAKE_REPO_ROOT/build/security/trivy-secrets.json" ;;
      *backend-trivy.json*) printf '{}\n' > "$FAKE_REPO_ROOT/build/security/backend-trivy.json" ;;
      *frontend-trivy.json*) printf '{}\n' > "$FAKE_REPO_ROOT/build/security/frontend-trivy.json" ;;
      *sbom.cdx.json*) printf '{}\n' > "$FAKE_REPO_ROOT/build/security/sbom.cdx.json" ;;
    esac
    ;;
  inspect)
    printf 'sha256:fixture-image-id\n'
    ;;
  save)
    while [[ $# -gt 0 ]]; do
      if [[ "$1" == "-o" ]]; then
        : > "$2"
        break
      fi
      shift
    done
    ;;
esac
EOF
chmod +x "$fake_bin/docker"

cat > "$fake_bin/make" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_MAKE_LOG"
EOF
chmod +x "$fake_bin/make"

run_scan() {
  local output_file="$1"
  shift
  env \
    PATH="$fake_bin:$PATH" \
    FAKE_DOCKER_LOG="$docker_log" \
    FAKE_MAKE_LOG="$make_log" \
    FAKE_REPO_ROOT="$test_repo" \
    "$@" \
    /bin/bash "$test_repo/scripts/security-scan.sh" > "$output_file" 2>&1
}

failure_output="$test_dir/failure.out"
set +e
run_scan "$failure_output" FAKE_DOCKER_FAIL_AT=gitleaks
failure_status=$?
set -e

if [[ "$failure_status" -eq 0 ]]; then
  echo "SEC-SCAN-REGRESSION-001 failed: a premature scanner failure was reported as success" >&2
  cat "$failure_output" >&2
  exit 1
fi
grep -Fq 'SEC-SCAN-001' "$failure_output" \
  || { echo "SEC-SCAN-REGRESSION-001 failed: gitleaks scan never started" >&2; exit 1; }
if grep -Fq 'SEC-SCAN: all security gates passed' "$failure_output"; then
  echo "SEC-SCAN-REGRESSION-001 failed: success marker printed after failure" >&2
  exit 1
fi

missing_report_output="$test_dir/missing-report.out"
set +e
run_scan "$missing_report_output" FAKE_DOCKER_SKIP_REPORT=gitleaks
missing_report_status=$?
set -e
if [[ "$missing_report_status" -eq 0 ]]; then
  echo "SEC-SCAN-REGRESSION-002 failed: missing scanner evidence was reported as success" >&2
  cat "$missing_report_output" >&2
  exit 1
fi
grep -Fq 'gitleaks.json was not written' "$missing_report_output" \
  || { echo "SEC-SCAN-REGRESSION-002 failed: missing report failure was not explicit" >&2; exit 1; }

: > "$docker_log"
: > "$make_log"
success_output="$test_dir/success.out"
run_scan "$success_output"

for marker in \
  'SEC-SCAN-001 passed' \
  'SEC-SCAN-002A passed' \
  'SEC-SCAN-002B passed' \
  'SEC-SCAN-003: runtime images built' \
  'SEC-SCAN-004 passed' \
  'SEC-SCAN-005 passed' \
  'SEC-SCAN-006 passed' \
  'SEC-SCAN-007 passed' \
  'SEC-SCAN: all security gates passed'; do
  grep -Fq "$marker" "$success_output" \
    || { echo "SEC-SCAN-REGRESSION-002 failed: missing completion marker: $marker" >&2; exit 1; }
done

for invocation in gitleaks 'trivy-filesystem.json' 'trivy-secrets.json' 'backend-trivy.json' 'frontend-trivy.json' 'sbom.cdx.json'; do
  grep -Fq "$invocation" "$docker_log" \
    || { echo "SEC-SCAN-REGRESSION-002 failed: scanner did not run: $invocation" >&2; exit 1; }
done
grep -Fq 'license-check' "$make_log" \
  || { echo "SEC-SCAN-REGRESSION-002 failed: license gate did not run" >&2; exit 1; }

echo "SEC-SCAN-REGRESSION-001 passed: premature scanner failures remain non-zero"
echo "SEC-SCAN-REGRESSION-002 passed: missing scanner evidence fails closed"
echo "SEC-SCAN-REGRESSION-003 passed: every security phase starts and completes in the harness"
