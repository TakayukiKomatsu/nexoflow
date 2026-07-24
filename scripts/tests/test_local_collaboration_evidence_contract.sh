#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output_file="$(mktemp)"
trap 'rm -f "$output_file"' EXIT

"$repo_root/scripts/test-local-collaboration-evidence.sh" > "$output_file" 2>&1 \
  || { cat "$output_file" >&2; exit 1; }

for marker in \
  'COLLAB-LOCAL-001 passed: local PR branch was reviewed and fast-forwarded into disposable main' \
  'REBASE-LOCAL-002 passed: actual interactive autosquash rewrote two unpublished commits into one' \
  'range-diff: verified' \
  'remote mutations: none'; do
  grep -Fq "$marker" "$output_file" \
    || { echo "local collaboration evidence missing marker: $marker" >&2; cat "$output_file" >&2; exit 1; }
done

review_record="$repo_root/docs/evidence/pull-request-simulation.md"
[[ -s "$review_record" ]] \
  || { echo "committed local pull-request simulation record is missing: $review_record" >&2; exit 1; }
git -C "$repo_root" ls-files --error-unmatch docs/evidence/pull-request-simulation.md >/dev/null 2>&1 \
  || { echo "local pull-request record must be tracked by git" >&2; exit 1; }

for marker in \
  'Local simulation — not a hosted pull request' \
  '## Source and target' \
  '## Scope' \
  '## Verification' \
  '## Security' \
  '## Migrations' \
  '## Rollback' \
  '## Residual risks' \
  '## Review conclusion' \
  'Remote mutations: none'; do
  grep -Fq "$marker" "$review_record" \
    || { echo "local pull-request record missing marker: $marker" >&2; exit 1; }
done

grep -Eq 'Source candidate: `[^`]+` at `[0-9a-f]{40}`' "$review_record" \
  || { echo "local pull-request record lacks an exact source branch and SHA" >&2; exit 1; }
grep -Eq 'Target base: `[^`]+` at `[0-9a-f]{40}`' "$review_record" \
  || { echo "local pull-request record lacks an exact target branch and SHA" >&2; exit 1; }
source_sha="$(sed -n 's/^Source candidate: `[^`]*` at `\([0-9a-f]\{40\}\)`.*/\1/p' "$review_record")"
target_sha="$(sed -n 's/^Target base: `[^`]*` at `\([0-9a-f]\{40\}\)`.*/\1/p' "$review_record")"
[[ "$source_sha" =~ ^[0-9a-f]{40}$ && "$target_sha" =~ ^[0-9a-f]{40}$ ]] \
  || { echo "local pull-request record must contain exactly one source and target commit SHA" >&2; exit 1; }
git -C "$repo_root" cat-file -e "${source_sha}^{commit}" 2>/dev/null \
  || { echo "local pull-request source SHA is not a commit object" >&2; exit 1; }
git -C "$repo_root" cat-file -e "${target_sha}^{commit}" 2>/dev/null \
  || { echo "local pull-request target SHA is not a commit object" >&2; exit 1; }
if grep -Eq 'https?://|github\.com|gitlab\.com' "$review_record"; then
  echo "local pull-request record must not imply a hosted review URL" >&2
  exit 1
fi

echo "COLLAB-CONTRACT-001 passed: local PR and autosquash/rebase proof is executable and remote-free"
