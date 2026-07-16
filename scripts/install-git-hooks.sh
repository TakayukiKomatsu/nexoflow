#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_repo="${1:-$repo_root}"
hooks_dir="$(git -C "$target_repo" rev-parse --path-format=absolute --git-path hooks)"

for hook in commit-msg pre-commit pre-push; do
  source_hook="$repo_root/scripts/hooks/$hook"
  [[ -f "$source_hook" ]] || {
    echo "missing source hook: $source_hook" >&2
    exit 1
  }
  install -m 0755 "$source_hook" "$hooks_dir/$hook"
done

echo "Installed SRM Git hooks in $hooks_dir"
