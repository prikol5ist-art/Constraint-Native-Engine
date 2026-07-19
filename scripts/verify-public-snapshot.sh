#!/usr/bin/env bash
set -euo pipefail

root=${1:-.}
root=$(cd "$root" && pwd)

forbidden_paths='(^|/)(artifacts|build|\.gradle|\.idea|docs/superpowers)(/|$)|(^|/)(local\.properties|\.env|\.DS_Store)$|IMPLEMENTATION_PLAN\.md$'
secret_pattern='AIza[0-9A-Za-z_-]{20,}|github_pat_[0-9A-Za-z_]{20,}|ghp_[0-9A-Za-z]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|sk-[0-9A-Za-z]{20,}'
local_path_pattern='/Users/[^/[:space:]]+|[A-Za-z]:\\Users\\[^\\[:space:]]+'

tracked=$(git -C "$root" ls-files)
if printf '%s\n' "$tracked" | grep -E "$forbidden_paths"; then
  printf 'forbidden paths are tracked\n' >&2
  exit 1
fi

required_files=(
  "LICENSE"
  "SECURITY.md"
  "GOVERNANCE.md"
  "CODE_OF_CONDUCT.md"
  "CITATION.cff"
  ".github/ISSUE_TEMPLATE/config.yml"
  ".github/PULL_REQUEST_TEMPLATE.md"
  ".github/dependabot.yml"
)
for f in "${required_files[@]}"; do
  if ! printf '%s\n' "$tracked" | grep -Fqx "$f"; then
    printf "required file is missing or untracked: %s\n" "$f" >&2
    exit 1
  fi
done

if git -C "$root" grep -I -n -E "$secret_pattern" -- .; then
  printf 'secret-like value found in current tree\n' >&2
  exit 1
fi

if git -C "$root" grep -I -n -E "$local_path_pattern" -- .; then
  printf 'absolute developer path found in current tree\n' >&2
  exit 1
fi

if git -C "$root" log -p --all -- . | grep -E "$secret_pattern"; then
  printf 'secret-like value found in Git history\n' >&2
  exit 1
fi

printf 'public snapshot verification passed\n'
