#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
scanner="$repo_root/scripts/verify-public-snapshot.sh"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

git -C "$tmp" init -q
git -C "$tmp" config user.email scanner@example.invalid
git -C "$tmp" config user.name scanner-test
mkdir -p "$tmp/.github/ISSUE_TEMPLATE"
touch "$tmp/README.md" "$tmp/LICENSE" "$tmp/SECURITY.md" "$tmp/GOVERNANCE.md" "$tmp/CODE_OF_CONDUCT.md" "$tmp/CITATION.cff" "$tmp/.github/ISSUE_TEMPLATE/config.yml" "$tmp/.github/PULL_REQUEST_TEMPLATE.md" "$tmp/.github/dependabot.yml"
git -C "$tmp" add .
git -C "$tmp" commit -qm clean
"$scanner" "$tmp"

printf 'token=%s%s\n' "AIza" "123456789012345678901234567890" > "$tmp/leak.txt"
git -C "$tmp" add leak.txt
git -C "$tmp" commit -qm leak
if "$scanner" "$tmp"; then
  printf 'scanner accepted a Google API key fixture\n' >&2
  exit 1
fi

rm "$tmp/leak.txt"
git -C "$tmp" add -u
git -C "$tmp" commit -qm remove-leak
mkdir -p "$tmp/artifacts"
printf 'private\n' > "$tmp/artifacts/session.log"
git -C "$tmp" add -f artifacts/session.log
git -C "$tmp" commit -qm forbidden-file
if "$scanner" "$tmp"; then
  printf 'scanner accepted a forbidden tracked path\n' >&2
  exit 1
fi

printf 'public snapshot scanner tests passed\n'
