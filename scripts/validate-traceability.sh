#!/usr/bin/env bash
# TRACE-001: Every stable Scenario ID declared by the numbered SDD documents
# must resolve to existing classified source artifacts and a structurally valid
# verification command. Documented Make targets are resolved against Makefile
# without executing their potentially expensive recipes.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
traceability="${TRACEABILITY_FILE:-$repo_root/docs/REQUIREMENT_TRACEABILITY.md}"
makefile="${TRACEABILITY_MAKEFILE:-$repo_root/Makefile}"
sdd_dir="$repo_root/docs/sdd"

[[ -s "$traceability" ]] \
  || { echo "TRACE-001 FAILED: $traceability is missing or empty" >&2; exit 1; }
[[ -s "$makefile" ]] \
  || { echo "TRACE-001 FAILED: $makefile is missing or empty" >&2; exit 1; }

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
make_targets=()

while IFS= read -r make_line; do
  make_line="${make_line%%#*}"
  [[ "$make_line" == *:* ]] || continue
  target_list="${make_line%%:*}"
  [[ "$target_list" != *"="* ]] || continue
  for target in $target_list; do
    if [[ "$target" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
      make_targets+=("$target")
    fi
  done
done <"$makefile"

make_target_exists() {
  local expected="$1"
  local available
  for available in "${make_targets[@]}"; do
    [[ "$available" == "$expected" ]] && return 0
  done
  return 1
}

validate_make_invocations() {
  local id="$1"
  local command="$2"
  local separated="$command"
  separated="${separated//&&/$'\n'}"
  separated="${separated//||/$'\n'}"
  separated="${separated//;/$'\n'}"
  separated="${separated//|/$'\n'}"
  local segment
  while IFS= read -r segment; do
    local tokens=()
    read -r -a tokens <<<"$segment"
    local make_index=-1
    local index
    for ((index = 0; index < ${#tokens[@]}; index++)); do
      if [[ "${tokens[$index]}" == "make" ]]; then
        make_index="$index"
        break
      fi
    done
    [[ "$make_index" -ge 0 ]] || continue

    local expects_option_value=false
    for ((index = make_index + 1; index < ${#tokens[@]}; index++)); do
      local token="${tokens[$index]}"
      if [[ "$expects_option_value" == true ]]; then
        expects_option_value=false
        continue
      fi
      case "$token" in
        -C|-f|-I|--directory|--file|--makefile|--include-dir)
          expects_option_value=true
          continue
          ;;
        -*|*=*)
          continue
          ;;
      esac
      [[ "$token" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || continue
      if ! make_target_exists "$token"; then
        failures+=("$id: documented Make target does not exist: $token")
      fi
    done
  done <<<"$separated"
}

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
  source_cell="$(printf '%s\n' "$row" | awk -F '|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $4); print $4}')"
  verification_cell="$(printf '%s\n' "$row" | awk -F '|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $5); print $5}')"
  id_found=false
  artifact_found=false
  reference_found=false
  while IFS= read -r reference; do
    reference_found=true
    reference="${reference#\`}"
    reference="${reference%\`}"
    reference="${reference%%#*}"
    if [[ ! -e "$repo_root/$reference" ]]; then
      failures+=("$id: classified source path does not exist: $reference")
      continue
    fi
    artifact_found=true
    if grep -Eq "(^|[^[:alnum:]-])${id}([^[:alnum:]-]|$)" "$repo_root/$reference"; then
      id_found=true
    fi
  done < <(
    printf '%s\n' "$source_cell" \
      | grep -oE '`[^`]+\.(java|feature|sh|mjs|ts|tsx|yml|yaml|md)`' \
      || true
  )

  if [[ "$reference_found" != true ]]; then
    failures+=("$id: row lacks a classified source path")
  elif [[ "$artifact_found" != true ]]; then
    failures+=("$id: row lacks an existing feature/test/script path")
  fi

  executable_command_found=false
  while IFS= read -r command; do
    command="${command#\`}"
    command="${command%\`}"
    if printf '%s\n' "$command" \
      | grep -Eq '(^|[[:space:];&|])(make |npm |docker compose |(\./)?scripts/|(\./)?backend/gradlew|(\./)?gradlew)'; then
      executable_command_found=true
    fi
    validate_make_invocations "$id" "$command"
  done < <(
    printf '%s\n' "$verification_cell" | grep -oE '`[^`]+`' || true
  )

  if [[ "$executable_command_found" != true ]]; then
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
      evidence_reference_found=false
      while IFS= read -r reference; do
        evidence_reference_found=true
        reference="${reference#\`}"
        reference="${reference%\`}"
        reference="${reference%%#*}"
        if [[ ! -e "$repo_root/$reference" ]]; then
          classification_failures+=("$requirement: classified source path does not exist: $reference")
        fi
      done < <(
        printf '%s\n' "$evidence" \
          | grep -oE '`((backend|frontend|docs|scripts|\.github)/[^`]+|[^`/]+\.(java|feature|sh|mjs|ts|tsx|yml|yaml|md|json))`' \
          || true
      )
      if [[ "$evidence_reference_found" != true ]]; then
        classification_failures+=("$requirement: Implemented claim lacks a concrete source/report path")
      fi
      verification_command_found=false
      while IFS= read -r command; do
        command="${command#\`}"
        command="${command%\`}"
        if printf '%s\n' "$command" \
          | grep -Eq '(^|[[:space:];&|])(make |npm |docker compose |(\./)?scripts/|git )'; then
          verification_command_found=true
        fi
        validate_make_invocations "$requirement" "$command"
      done < <(
        printf '%s\n' "$verification" | grep -oE '`[^`]+`' || true
      )
      if [[ "$verification_command_found" != true ]]; then
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

echo "TRACE-001 passed: all ${#scenario_ids[@]} SDD IDs, ${#executable_ids[@]} Cucumber IDs, and ${#required_check_ids[@]} supplemental IDs resolve to existing source artifacts and structurally valid commands"
echo "DOC-CLAIM-005 passed: all $classified_rows source claims use meaningful Implemented, Proposed, or Gap evidence"
