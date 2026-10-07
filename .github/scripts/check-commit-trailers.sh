#!/usr/bin/env bash
# Fail when any commit in BASE..HEAD carries an AI attribution trailer.
#
# Usage: check-commit-trailers.sh <base-sha> <head-sha>
#
# Why: this project's CLAUDE.md prohibits AI attribution in commit history.
# A "Co-Authored-By: <AI>" trailer makes GitHub list the AI account as a
# contributor, and "Claude-Session:" links point at private sessions. Once such
# a commit is pushed, GitHub keeps it reachable through refs/pull/*, which only
# GitHub Support can purge, so the check has to run before the merge.
#
# Human co-author trailers are allowed.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <base-sha> <head-sha>" >&2
  exit 2
fi
base="$1"
head="$2"

# Case-insensitive. Matched per line of each commit message.
pattern='^co-authored-by:.*(claude|anthropic|copilot|openai|gpt|gemini)'
pattern="${pattern}|^(claude-session|generated-by|ai-assisted):"
pattern="${pattern}|anthropic\.com|openai\.com"

failed=0
checked=0
while read -r sha; do
  checked=$((checked + 1))
  message="$(git log -1 --format=%B "$sha")"
  if matches="$(printf '%s\n' "$message" | grep -iE "$pattern")"; then
    failed=1
    subject="$(git log -1 --format=%s "$sha")"
    echo "::error title=AI attribution trailer::Commit ${sha:0:7} (${subject}) carries an AI attribution trailer."
    printf '%s\n' "$matches" | sed 's/^/    /'
  fi
done < <(git rev-list "${base}..${head}")

if [ "$failed" -ne 0 ]; then
  echo
  echo "Remove the trailer before merging, for example:"
  echo "  git rebase -i ${base}   # mark the commit as 'reword', delete the line"
  echo "  git push --force-with-lease"
  echo "See CLAUDE.md (commit hygiene)."
  exit 1
fi

echo "Checked ${checked} commit(s): no AI attribution trailers."
