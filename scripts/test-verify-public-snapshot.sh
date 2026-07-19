#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "$0")/.." && pwd)
scanner="$repo_root/scripts/verify-public-snapshot.sh"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

git -C "$tmp" init -q
git -C "$tmp" config user.email scanner@example.invalid
git -C "$tmp" config user.name scanner-test
printf '# clean\n' > "$tmp/README.md"
git -C "$tmp" add README.md
git -C "$tmp" commit -qm clean
"$scanner" "$tmp"

printf 'token=AIza123456789012345678901234567890\n' > "$tmp/leak.txt"
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
