#!/usr/bin/env bash
# TRACE-001: Every stable Scenario ID declared by the numbered SDD documents
# must resolve to an existing executable artifact and an executable command.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
traceability="$repo_root/docs/REQUIREMENT_TRACEABILITY.md"
sdd_dir="$repo_root/docs/sdd"

[[ -s "$traceability" ]] \
  || { echo "TRACE-001 FAILED: $traceability is missing or empty" >&2; exit 1; }

scenario_ids=()
while IFS= read -r id; do
  scenario_ids+=("$id")
done < <(
  sed -nE \
    's/^[[:space:]]*Scenario( Outline)?:[[:space:]]+([A-Z][A-Z0-9]*(-[A-Z0-9]+)*-[0-9]{3})([[:space:]].*)?$/\2/p' \
    "$sdd_dir"/[0-9][0-9]_sdd_*.md \
    | sort -u
)

executable_ids=()
while IFS= read -r id; do
  executable_ids+=("$id")
done < <(
  sed -nE \
    's/^[[:space:]]*Scenario( Outline)?:[[:space:]]+([A-Z][A-Z0-9]*(-[A-Z0-9]+)*-[0-9]{3})([[:space:]].*)?$/\2/p' \
    "$repo_root"/backend/src/integrationTest/resources/features/*.feature \
    | sort -u
)

all_scenario_ids=()
while IFS= read -r id; do
  all_scenario_ids+=("$id")
done < <(printf '%s\n' "${scenario_ids[@]}" "${executable_ids[@]}" | sort -u)

[[ "${#scenario_ids[@]}" -gt 0 ]] \
  || { echo "TRACE-001 FAILED: no stable Scenario IDs were parsed from docs/sdd" >&2; exit 1; }

# Supplemental documentation and contract checks are stable traceability IDs even
# though they are not declared as Gherkin scenarios.
required_check_ids=(
  DOC-LINK-001 DOC-SCHEMA-002 DOC-TRACE-003 DOC-MONEY-004 DOC-CLAIM-005
  DOC-OPENAPI-006 AUTHORITY-001 API-CONTRACT-001 FIN-GIT-003 COLLAB-LOCAL-001 REBASE-LOCAL-002
)
check_ids=("${all_scenario_ids[@]}" "${required_check_ids[@]}")

failures=()

for id in "${check_ids[@]}"; do
  row_count="$(
    awk -F '|' -v id="$id" '
      {
        cell = $2
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", cell)
        if (cell == id) {
          count++
          if (count == 1) row = $0
        }
      }
      END {
        print count
        if (count == 1) print row
      }
    ' "$traceability"
  )"
  count="${row_count%%$'\n'*}"
  row="${row_count#*$'\n'}"

  if [[ "$count" -eq 0 ]]; then
    failures+=("$id: no exact scenario row")
    continue
  fi

  if [[ "$count" -ne 1 ]]; then
    failures+=("$id: expected exactly one traceability row, found $count")
    continue
  fi

  status="$(printf '%s\n' "$row" | awk -F '|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $3); print $3}')"
  id_found=false
  artifact_found=false
  while IFS= read -r reference; do
    reference="${reference#\`}"
    reference="${reference%\`}"
    reference="${reference%%#*}"
    if [[ -e "$repo_root/$reference" ]]; then
      artifact_found=true
      if grep -Eq "(^|[^[:alnum:]-])${id}([^[:alnum:]-]|$)" "$repo_root/$reference"; then
        id_found=true
      fi
    fi
  done < <(
    printf '%s\n' "$row" \
      | grep -oE '`[^`]+\.(java|feature|sh|mjs|ts|tsx|yml|yaml|md)`' \
  )

  if [[ "$artifact_found" != true ]]; then
    failures+=("$id: row lacks an existing feature/test/script path")
  fi

  if ! printf '%s\n' "$row" \
    | grep -Eq '`(make |npm |docker compose |(\./)?scripts/|(\./)?backend/gradlew|(\./)?gradlew)[^`]*`'; then
    failures+=("$id: row lacks an executable verification command")
  fi

  if [[ "$status" == "**Implemented**" && "$id_found" != true ]]; then
    failures+=("$id: implemented row lacks an executable artifact containing the exact stable ID")
  fi
done

classification_failures=()
classified_rows=0
while IFS='|' read -r _ requirement status evidence verification _; do
  requirement="$(printf '%s' "$requirement" | sed -E 's/^[[:space:]]+|[[:space:]]+$//g')"
  status="$(printf '%s' "$status" | sed -E 's/[[:space:]*]//g')"
  [[ -n "$requirement" && "$requirement" != "Source requirement" && "$requirement" != "---" ]] || continue
  classified_rows=$((classified_rows + 1))
  case "$status" in
    Implemented)
      if ! printf '%s' "$evidence" | grep -Eq '`[^`]+(/[^`]+|\.(java|feature|sh|mjs|ts|tsx|yml|yaml|md|json))`'; then
        classification_failures+=("$requirement: Implemented claim lacks a concrete source/report path")
      fi
      if ! printf '%s' "$verification" | grep -Eq '`(make |npm |docker compose |(\./)?scripts/|git )[^`]*`'; then
        classification_failures+=("$requirement: Implemented claim lacks an executable command")
      fi
      ;;
    Proposed)
      if ! printf '%s' "$evidence" | grep -Eiq '(design|proposal|not production|no production|not implemented)'; then
        classification_failures+=("$requirement: Proposed claim lacks an explicit design-only qualification")
      fi
      ;;
    Gap)
      if ! printf '%s' "$evidence" | grep -Eiq '(blocked|outside|not implemented|no remote|remain)'; then
        classification_failures+=("$requirement: Gap claim lacks a concrete limitation or blocker")
      fi
      ;;
    *) classification_failures+=("$requirement: invalid status '$status'") ;;
  esac
done < <(
  awk '
    /^## Source requirement matrix/ { in_matrix = 1; next }
    in_matrix && /^## / { exit }
    in_matrix && /^\|/ { print }
  ' "$traceability"
)

if [[ "$classified_rows" -eq 0 ]]; then
  classification_failures+=("source requirement matrix has no classified rows")
fi

if [[ "${#classification_failures[@]}" -gt 0 ]]; then
  echo "DOC-CLAIM-005 FAILED: requirement claims are not meaningfully classified:" >&2
  printf '  %s\n' "${classification_failures[@]}" >&2
  exit 1
fi

if [[ "${#failures[@]}" -gt 0 ]]; then
  echo "TRACE-001 FAILED: incomplete SDD scenario or supplemental-check traceability:" >&2
  printf '  %s\n' "${failures[@]}" >&2
  exit 1
fi

echo "TRACE-001 passed: all ${#scenario_ids[@]} SDD IDs, ${#executable_ids[@]} Cucumber IDs, and ${#required_check_ids[@]} supplemental IDs resolve to executable artifacts and commands"
echo "DOC-CLAIM-005 passed: all $classified_rows source claims use meaningful Implemented, Proposed, or Gap evidence"
