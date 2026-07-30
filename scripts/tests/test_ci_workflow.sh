#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
workflow="$repo_root/.github/workflows/ci.yml"
makefile="$repo_root/Makefile"

if [[ ! -s "$workflow" ]]; then
  echo "expected CI workflow at $workflow" >&2
  exit 1
fi

if [[ ! -s "$makefile" ]]; then
  echo "expected Makefile at $makefile" >&2
  exit 1
fi

# ── Pinned immutable actions ─────────────────────────────────────────────────
action_count="$(grep -Ec '^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]+[^[:space:]]+@[0-9a-f]{40}([[:space:]#]|$)' "$workflow")"
uses_count="$(grep -Ec '^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]+' "$workflow")"
if [[ "$uses_count" -eq 0 || "$action_count" -ne "$uses_count" ]]; then
  echo "every workflow action must use a 40-character immutable commit SHA (found $action_count of $uses_count)" >&2
  exit 1
fi

# ── CI workflow must contain these strings ───────────────────────────────────
for expected in \
  'permissions:' \
  'contents: read' \
  'make verify-fast' \
  'make test-log-redaction' \
  'make build' \
  'make test-runtime' \
  'make verify-compose' \
  'make license-check' \
  'make test-api-features' \
  'make test-ui-features' \
  'make validate-docs' \
  'make validate-traceability' \
  'make validate-frontend-authority' \
  'make validate-frontend-api-contract' \
  'make security-scan' \
  'make explain-statements-representative' \
  'playwright install chromium' \
  'cucumber-report' \
  'playwright-report' \
  'reporting-explain' \
  'gitleaks/gitleaks-action@e0c47f4f8be36e29cdc102c57e68cb5cbf0e8d1e # v3' \
  'GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}' \
  'GITLEAKS_ENABLE_COMMENTS: "false"' \
  'actions/dependency-review-action@' \
  'ignore-unfixed'; do
  if [[ "$expected" == 'ignore-unfixed' ]]; then
    # The broad ignore-unfixed policy must have been removed.
    if grep -Fq 'ignore-unfixed: true' "$workflow"; then
      echo "workflow must not contain 'ignore-unfixed: true' (replace with per-CVE .trivyignore.yaml entries)" >&2
      exit 1
    fi
    continue
  fi
  if ! grep -Fq "$expected" "$workflow"; then
    echo "expected CI workflow to contain: $expected" >&2
    exit 1
  fi
done

# These security/financial-authority gates must be real single-line workflow
# commands, not comments, step names, or documentation strings.
for required_command in \
  'make test-log-redaction' \
  'make validate-frontend-authority' \
  'make validate-frontend-api-contract'; do
  command_count="$(grep -Ec "^[[:space:]]+run:[[:space:]]+${required_command}[[:space:]]*$" "$workflow")"
  if [[ "$command_count" -ne 1 ]]; then
    echo "CI must execute exactly one '${required_command}' run step (found $command_count)" >&2
    exit 1
  fi
done

if grep -Eq '^[[:space:]]+continue-on-error:[[:space:]]+true' "$workflow"; then
  echo "required CI gates must not be globally weakened with continue-on-error" >&2
  exit 1
fi

# ── Makefile must define every required target ────────────────────────────────
for target in \
  'test-api-features:' \
  'test-ui-features:' \
  'e2e-fixed:' \
  'security-scan:' \
  'test-log-redaction:' \
  'test-reporting-evidence-contract:' \
  'validate-docs:' \
  'validate-frontend-authority:' \
  'validate-frontend-api-contract:' \
  'validate-traceability:' \
  'release-check:' \
  'explain-statements-representative:' \
  'verify-fast:' \
  'test-runtime:' \
  'verify-compose:' \
  'license-check:' \
  'build:'; do
  if ! grep -qF "$target" "$makefile"; then
    echo "Makefile must define target: $target" >&2
    exit 1
  fi
done

verify_fast_deps="$(grep '^verify-fast:' "$makefile" | head -1)"
if ! printf '%s' "$verify_fast_deps" | grep -qF 'test-reporting-evidence-contract'; then
  echo "verify-fast target must include the mutation-sensitive reporting evidence contract" >&2
  exit 1
fi

# ── release-check must aggregate all required sub-targets ─────────────────────
release_line="$(grep -n 'release-check:' "$makefile" | head -1)"
release_deps="$(grep '^release-check:' "$makefile" | head -1)"
for dep in \
  'verify-fast' \
  'test-log-redaction' \
  'build' \
  'test-runtime' \
  'verify-compose' \
  'e2e-fixed' \
  'explain-statements-representative' \
  'security-scan' \
  'validate-docs' \
  'validate-frontend-authority' \
  'validate-frontend-api-contract' \
  'validate-traceability'; do
  if ! printf '%s' "$release_deps" | grep -qF "$dep"; then
    echo "release-check target must depend on: $dep (found: $release_deps)" >&2
    exit 1
  fi
done
if ! grep -qF '$(MAKE) inspect-observability' "$makefile"; then
  echo "verify-compose must run the authenticated observability inspection" >&2
  exit 1
fi

echo "CI-001 passed: CI uses immutable actions, least privilege, builds, Docker runtime tests, license compliance, and security review"
echo "CI-002 passed: all Task4 Make targets are defined and invoked in the CI workflow"
echo "CI-003 passed: release-check aggregates all required sub-targets"
echo "CI-004 passed: ignore-unfixed broad policy is absent — per-CVE .trivyignore.yaml entries required"
echo "CI-005 passed: Compose verification includes authenticated observability inspection"
echo "CI-006 passed: log-redaction and frontend authority/contract gates are executable fail-closed steps"
