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

[[ "${#scenario_ids[@]}" -gt 0 ]] \
  || { echo "TRACE-001 FAILED: no stable Scenario IDs were parsed from docs/sdd" >&2; exit 1; }

failures=()

for id in "${scenario_ids[@]}"; do
  row="$(
    awk -F '|' -v id="$id" '
      {
        cell = $2
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", cell)
        if (cell == id) {
          print
          exit
        }
      }
    ' "$traceability"
  )"

  if [[ -z "$row" ]]; then
    failures+=("$id: no exact scenario row")
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
      | grep -oE '`[^`]+\.(java|feature|sh|ts|tsx|yml|yaml|md)`' \
      || true
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

if [[ "${#failures[@]}" -gt 0 ]]; then
  echo "TRACE-001 FAILED: incomplete SDD scenario traceability:" >&2
  printf '  %s\n' "${failures[@]}" >&2
  exit 1
fi

echo "TRACE-001 passed: all ${#scenario_ids[@]} SDD Scenario IDs resolve to executable artifacts and commands"
