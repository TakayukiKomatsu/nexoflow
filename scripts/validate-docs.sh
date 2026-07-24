#!/usr/bin/env bash
# DOCS-001: Validate documentation consistency — Markdown local links, Mermaid
# diagrams, migration-to-ER coverage, OpenAPI configuration, and forbidden claims.
# Delegates structural checks to test_architecture_docs.sh, then adds doc-quality gates.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=== DOCS-001: architecture docs, Markdown links, Mermaid, and ER checks ==="
"$repo_root/scripts/tests/test_architecture_docs.sh" \
  || { echo "DOCS-001 FAILED: architecture document check did not pass" >&2; exit 1; }

echo "=== DOCS-002: executable OpenAPI contract ==="
"$repo_root/scripts/with-java21.sh" "$repo_root/backend/gradlew" \
  -p "$repo_root/backend" test \
  --tests '*RuntimeMetadataContractTest.exposesOpenApiAndHealthProbes' \
  || { echo "DOCS-002 FAILED: the generated OpenAPI endpoint contract did not pass" >&2; exit 1; }
echo "DOCS-002 passed: generated /v3/api-docs contract is executable and reachable"

echo "=== DOCS-003: forbidden claim scan ==="
# Reject evidence-quality claims that promise production behaviour not yet demonstrated.
FORBIDDEN_PATTERNS=(
  'TODO: production'
  'FIXME: production'
  'TODO: add to production'
  'NOT FOR PRODUCTION'
  'placeholder — replace'
  'implement later'
)
forbidden_hits=()
for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
  while IFS= read -r hit; do
    forbidden_hits+=("$hit")
  done < <(
    grep -rFn "$pattern" \
      "$repo_root/docs" \
      "$repo_root/README.md" \
      "$repo_root/AI_USAGE.md" \
      2>/dev/null || true
  )
done

if [[ "${#forbidden_hits[@]}" -gt 0 ]]; then
  echo "DOCS-003 FAILED: forbidden claims found in documentation:" >&2
  printf '  %s\n' "${forbidden_hits[@]}" >&2
  exit 1
fi
echo "DOCS-003 passed: no forbidden production claims in documentation"

echo "=== DOCS-004: claim classification, release truth, and usage disclosure ==="
for required_readme_text in \
  '## GitHub Flow rationale' \
  '## Proposed evolution to 1M transactions/minute' \
  '16,667 transactions/second' \
  'RPO' \
  'RTO' \
  'not production-capacity evidence'; do
  grep -Fq "$required_readme_text" "$repo_root/README.md" \
    || { echo "DOCS-004 FAILED: root README lacks: $required_readme_text" >&2; exit 1; }
done

cucumber_scenarios="$(grep -hEc '^[[:space:]]*Scenario( Outline)?:' "$repo_root"/backend/src/integrationTest/resources/features/*.feature | awk '{ total += $1 } END { print total + 0 }')"
[[ "$cucumber_scenarios" -eq 12 ]] \
  || { echo "DOCS-004 FAILED: expected 12 executable Cucumber scenarios, found $cucumber_scenarios" >&2; exit 1; }
if grep -REiq '\b(ten|10)[[:space:]]+(executable[[:space:]]+)?Cucumber scenarios' "$repo_root/README.md" "$repo_root/docs/RUNBOOK.md"; then
  echo "DOCS-004 FAILED: reviewer docs still claim ten Cucumber scenarios" >&2
  exit 1
fi

tag_target="$(git -C "$repo_root" rev-list -n 1 v1.0.0 2>/dev/null || true)"
if [[ -n "$tag_target" ]]; then
  tag_short="${tag_target:0:7}"
  grep -Fq "existing local annotated \`v1.0.0\` tag points to \`$tag_short\`" "$repo_root/README.md" \
    || { echo "DOCS-004 FAILED: README does not disclose the existing stale local tag target $tag_short" >&2; exit 1; }
fi

for usage_heading in \
  '## Strategic prompts and outcomes' \
  '## Hallucinations and rework caught by verification' \
  '## Estimated time saved and lost'; do
  grep -Fq "$usage_heading" "$repo_root/AI_USAGE.md" \
    || { echo "DOCS-004 FAILED: AI_USAGE.md lacks: $usage_heading" >&2; exit 1; }
done
echo "DOCS-004 passed: scale/release claims and AI-use costs are explicit and current"

echo ""
echo "validate-docs: all documentation gates passed"
