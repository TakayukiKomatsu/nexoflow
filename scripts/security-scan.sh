#!/usr/bin/env bash
# SEC-SCAN: Pinned-image security gate for gitleaks, Trivy FS+image, Syft SBOM, and licenses.
# Fails on HIGH/CRITICAL fixed vulnerabilities and every secret finding.
# Unfixed CVEs must be explicitly listed in .trivyignore.yaml; no global ignore-unfixed.
#
# Usage:
#   ./scripts/security-scan.sh           # full security scan
#   ./scripts/security-scan.sh --check   # verify prerequisites only (no builds/scans)
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
git_common_dir="$(cd "$(git rev-parse --git-common-dir)" && pwd)"
gitleaks_git_mount=()
if [[ -f "$repo_root/.git" ]]; then
  gitleaks_git_mount=(-v "$git_common_dir:$git_common_dir:ro")
fi

# ── Pinned container images ──────────────────────────────────────────────────
# Tool images are pinned to immutable multi-architecture manifest digests.
GITLEAKS_IMAGE="ghcr.io/gitleaks/gitleaks@sha256:2bcceac45179b3a91bff11a824d0fb952585b429e54fc928728b1d4d5c3e5176" # v8.24.0
TRIVY_IMAGE="aquasec/trivy@sha256:fc10faf341a1d8fa8256c5ff1a6662ef74dd38b65034c8ce42346cf958a02d5d" # 0.62.1
SYFT_IMAGE="anchore/syft@sha256:eaf0517f7dcd9a29915eabb2c007dbc65b2f3f31f6e17906717e506d1d37a1c0" # v1.21.0

# ── Output artefacts ─────────────────────────────────────────────────────────
SBOM_DIR="$repo_root/build/security"
TRIVY_CACHE_DIR="$repo_root/build/.trivy-cache"
DIGEST_FILE="$SBOM_DIR/image-digests.txt"
mkdir -p "$SBOM_DIR" "$TRIVY_CACHE_DIR"
rm -f \
  "$SBOM_DIR/gitleaks.json" \
  "$SBOM_DIR/trivy-filesystem.json" \
  "$SBOM_DIR/trivy-secrets.json" \
  "$SBOM_DIR/backend-trivy.json" \
  "$SBOM_DIR/frontend-trivy.json" \
  "$SBOM_DIR/sbom.cdx.json" \
  "$DIGEST_FILE"

# ── Cleanup trap: remove tar exports that may contain image layer content ────
cleanup() {
  cleanup_status=$?
  rm -f "$SBOM_DIR/backend.tar" "$SBOM_DIR/frontend.tar"
  trap - EXIT
  exit "$cleanup_status"
}
trap cleanup EXIT
validate_ignore_policy() {
  if grep -REq 'ignore-unfixed:[[:space:]]*true' "$repo_root/.github" 2>/dev/null \
    || grep -Eq '^[[:space:]]*--ignore-unfixed([=[:space:]]|$)' "$repo_root/scripts/security-scan.sh"; then
    echo "FAIL: broad ignore-unfixed policy is forbidden" >&2
    return 1
  fi

  awk '
    function validate() {
      if (cve == "") return
      missing = ""
      if (block !~ /Package:/) missing = missing " Package"
      if (block !~ /Image:/) missing = missing " Image"
      if (block !~ /Justification:/) missing = missing " Justification"
      if (block !~ /Owner:/) missing = missing " Owner"
      if (block !~ /Expires: [0-9]{4}-[0-9]{2}-[0-9]{2}/) missing = missing " Expires"
      if (missing != "") {
        print "FAIL: " cve " exception lacks:" missing > "/dev/stderr"
        failed = 1
      }
    }
    /^[[:space:]]*-[[:space:]]+id:[[:space:]]+CVE-/ {
      validate()
      cve = $0
      block = $0
      next
    }
    cve != "" {
      if ($0 ~ /^[[:space:]]*-[[:space:]]+id:/) {
        validate()
        cve = ""
        block = ""
      } else {
        block = block "\n" $0
      }
    }
    END {
      validate()
      exit failed
    }
  ' "$repo_root/.trivyignore.yaml"
}

require_report() {
  report_id="$1"
  report_path="$2"
  [[ -s "$report_path" ]] \
    || { echo "$report_id FAILED: ${report_path#$repo_root/} was not written" >&2; exit 1; }
}

# ── --check mode: verify prerequisites without running scans ─────────────────
if [[ "${1:-}" == "--check" ]]; then
  echo "SEC-SCAN --check: verifying prerequisites"
  rc=0

  # Docker daemon
  if ! docker info >/dev/null 2>&1; then
    echo "FAIL: docker daemon is not running" >&2
    rc=1
  else
    echo "  OK: docker daemon reachable"
  fi

  # Pinned images pullable (verify manifest exists without downloading layers)
  for img in "$GITLEAKS_IMAGE" "$TRIVY_IMAGE" "$SYFT_IMAGE"; do
    if docker manifest inspect "$img" >/dev/null 2>&1; then
      echo "  OK: $img manifest available"
    else
      echo "  WARN: $img manifest not reachable (will be pulled at scan time)" >&2
    fi
  done

  # Dockerfiles present
  for df in backend/Dockerfile frontend/Dockerfile; do
    if [[ -f "$repo_root/$df" ]]; then
      echo "  OK: $df exists"
    else
      echo "FAIL: $df missing — image scan will fail" >&2
      rc=1
    fi
  done

  # Ignore policy must be explicit and reviewable.
  if [[ ! -f "$repo_root/.trivyignore.yaml" ]]; then
    echo "FAIL: .trivyignore.yaml missing" >&2
    rc=1
  elif validate_ignore_policy; then
    echo "  OK: .trivyignore.yaml has no broad or incomplete CVE exceptions"
  else
    rc=1
  fi

  # make license-check target reachable
  if make -C "$repo_root" -n license-check >/dev/null 2>&1; then
    echo "  OK: make license-check target exists"
  else
    echo "FAIL: make license-check target not found" >&2
    rc=1
  fi

  if [[ $rc -eq 0 ]]; then
    echo "SEC-SCAN --check: all prerequisites satisfied"
  else
    echo "SEC-SCAN --check: some prerequisites missing (see above)" >&2
  fi
  exit "$rc"
fi

validate_ignore_policy

echo "=== SEC-SCAN-001: gitleaks repository history and content scan ==="
if [[ "${#gitleaks_git_mount[@]}" -gt 0 ]]; then
  docker run --rm \
    -v "$repo_root:/repo:ro" \
    "${gitleaks_git_mount[@]}" \
    -v "$SBOM_DIR:/output" \
    "$GITLEAKS_IMAGE" \
    detect --source /repo --redact \
      --report-format json --report-path /output/gitleaks.json \
    || { echo "SEC-SCAN-001 FAILED: gitleaks found secrets in repository history or files" >&2; exit 1; }
else
  docker run --rm \
    -v "$repo_root:/repo:ro" \
    -v "$SBOM_DIR:/output" \
    "$GITLEAKS_IMAGE" \
    detect --source /repo --redact \
      --report-format json --report-path /output/gitleaks.json \
    || { echo "SEC-SCAN-001 FAILED: gitleaks found secrets in repository history or files" >&2; exit 1; }
fi
require_report "SEC-SCAN-001" "$SBOM_DIR/gitleaks.json"
echo "SEC-SCAN-001 passed: no secrets detected in repository history or files"

echo "=== SEC-SCAN-002A: Trivy filesystem vulnerability and misconfiguration scan ==="
docker run --rm \
  -v "$repo_root:/repo:ro" \
  -v "$repo_root/.trivyignore.yaml:/.trivyignore.yaml:ro" \
  -v "$SBOM_DIR:/output" \
  -v "$TRIVY_CACHE_DIR:/root/.cache/trivy" \
  "$TRIVY_IMAGE" fs \
    --ignorefile /.trivyignore.yaml \
    --scanners vuln,misconfig \
    --severity HIGH,CRITICAL \
    --exit-code 1 \
    --format json \
    --output /output/trivy-filesystem.json \
    --skip-dirs /repo/.claude \
    --skip-dirs /repo/build \
    --skip-dirs /repo/frontend/node_modules \
    /repo \
  || { echo "SEC-SCAN-002A FAILED: Trivy found HIGH/CRITICAL filesystem vulnerabilities or misconfigurations" >&2; exit 1; }
require_report "SEC-SCAN-002A" "$SBOM_DIR/trivy-filesystem.json"
echo "SEC-SCAN-002A passed: filesystem vulnerability and configuration scan clean"

echo "=== SEC-SCAN-002B: Trivy filesystem secret scan ==="
docker run --rm \
  -v "$repo_root:/repo:ro" \
  -v "$SBOM_DIR:/output" \
  -v "$TRIVY_CACHE_DIR:/root/.cache/trivy" \
  "$TRIVY_IMAGE" fs \
    --scanners secret \
    --exit-code 1 \
    --format json \
    --output /output/trivy-secrets.json \
    --skip-dirs /repo/.claude \
    --skip-dirs /repo/build \
    --skip-dirs /repo/frontend/node_modules \
    /repo \
  || { echo "SEC-SCAN-002B FAILED: Trivy found a secret in the repository" >&2; exit 1; }
require_report "SEC-SCAN-002B" "$SBOM_DIR/trivy-secrets.json"
echo "SEC-SCAN-002B passed: filesystem secret scan clean"

echo "=== SEC-SCAN-003: Build runtime images for image scanning ==="
docker build --pull --quiet --tag srm-backend:security-scan \
  -f "$repo_root/backend/Dockerfile" "$repo_root" \
  || { echo "SEC-SCAN-003 FAILED: backend image build failed" >&2; exit 1; }
docker build --pull --quiet --tag srm-frontend:security-scan \
  -f "$repo_root/frontend/Dockerfile" "$repo_root" \
  || { echo "SEC-SCAN-003 FAILED: frontend image build failed" >&2; exit 1; }

# Record exact image digests for audit trail
{
  echo "# Runtime image content digests — $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "backend  $(docker inspect --format='{{.Id}}' srm-backend:security-scan)"
  echo "frontend $(docker inspect --format='{{.Id}}' srm-frontend:security-scan)"
  echo "gitleaks $GITLEAKS_IMAGE"
  echo "trivy   $TRIVY_IMAGE"
  echo "syft    $SYFT_IMAGE"
} > "$DIGEST_FILE"
echo "SEC-SCAN-003: runtime images built — digests written to build/security/image-digests.txt"

echo "=== SEC-SCAN-004: Trivy backend runtime image scan ==="
BACKEND_DIGEST="$(docker inspect --format='{{.Id}}' srm-backend:security-scan)"
docker save srm-backend:security-scan -o "$SBOM_DIR/backend.tar"
docker run --rm \
  -v "$SBOM_DIR/backend.tar:/image.tar:ro" \
  -v "$repo_root/.trivyignore.yaml:/.trivyignore.yaml:ro" \
  -v "$SBOM_DIR:/output" \
  -v "$TRIVY_CACHE_DIR:/root/.cache/trivy" \
  "$TRIVY_IMAGE" image \
    --ignorefile /.trivyignore.yaml \
    --input /image.tar \
    --scanners vuln,misconfig \
    --severity HIGH,CRITICAL \
    --exit-code 1 \
    --format json \
    --output /output/backend-trivy.json \
  || { echo "SEC-SCAN-004 FAILED: backend image ($BACKEND_DIGEST) has HIGH/CRITICAL vulnerabilities" >&2; exit 1; }
rm -f "$SBOM_DIR/backend.tar"
require_report "SEC-SCAN-004" "$SBOM_DIR/backend-trivy.json"
echo "SEC-SCAN-004 passed: backend runtime image clean ($BACKEND_DIGEST)"

echo "=== SEC-SCAN-005: Trivy frontend runtime image scan ==="
FRONTEND_DIGEST="$(docker inspect --format='{{.Id}}' srm-frontend:security-scan)"
docker save srm-frontend:security-scan -o "$SBOM_DIR/frontend.tar"
docker run --rm \
  -v "$SBOM_DIR/frontend.tar:/image.tar:ro" \
  -v "$repo_root/.trivyignore.yaml:/.trivyignore.yaml:ro" \
  -v "$SBOM_DIR:/output" \
  -v "$TRIVY_CACHE_DIR:/root/.cache/trivy" \
  "$TRIVY_IMAGE" image \
    --ignorefile /.trivyignore.yaml \
    --input /image.tar \
    --scanners vuln,misconfig \
    --severity HIGH,CRITICAL \
    --exit-code 1 \
    --format json \
    --output /output/frontend-trivy.json \
  || { echo "SEC-SCAN-005 FAILED: frontend image ($FRONTEND_DIGEST) has HIGH/CRITICAL vulnerabilities" >&2; exit 1; }
rm -f "$SBOM_DIR/frontend.tar"
require_report "SEC-SCAN-005" "$SBOM_DIR/frontend-trivy.json"
echo "SEC-SCAN-005 passed: frontend runtime image clean ($FRONTEND_DIGEST)"

echo "=== SEC-SCAN-006: CycloneDX/Syft SBOM generation ==="
docker run --rm \
  -v "$repo_root:/repo:ro" \
  -v "$SBOM_DIR:/output" \
  "$SYFT_IMAGE" \
    scan /repo \
    --output "cyclonedx-json=/output/sbom.cdx.json" \
  || { echo "SEC-SCAN-006 FAILED: SBOM generation failed" >&2; exit 1; }
require_report "SEC-SCAN-006" "$SBOM_DIR/sbom.cdx.json"
echo "SEC-SCAN-006 passed: SBOM written to build/security/sbom.cdx.json"

echo "=== SEC-SCAN-007: backend and frontend license compliance ==="
make -C "$repo_root" license-check \
  || { echo "SEC-SCAN-007 FAILED: license compliance check failed" >&2; exit 1; }
echo "SEC-SCAN-007 passed: all production licenses approved"

echo ""
echo "SEC-SCAN: all security gates passed"
echo "  Image digests: build/security/image-digests.txt"
echo "  SBOM:          build/security/sbom.cdx.json"
echo "  Trivy ignore:  .trivyignore.yaml"
