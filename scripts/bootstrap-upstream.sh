#!/usr/bin/env bash
set -euo pipefail

UPSTREAM_URL="https://github.com/gytxtx/OpenJBD.git"
UPSTREAM_REMOTE="upstream"
UPSTREAM_REF="7e3e225a128f6e0d69425b98a2670d8d69594885"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

if git remote get-url "$UPSTREAM_REMOTE" >/dev/null 2>&1; then
  git remote set-url "$UPSTREAM_REMOTE" "$UPSTREAM_URL"
else
  git remote add "$UPSTREAM_REMOTE" "$UPSTREAM_URL"
fi

git fetch "$UPSTREAM_REMOTE" --tags

if ! git cat-file -e "${UPSTREAM_REF}^{commit}" 2>/dev/null; then
  echo "Expected upstream commit not found: $UPSTREAM_REF" >&2
  exit 1
fi

mkdir -p THIRD_PARTY_LICENSES

git show "${UPSTREAM_REF}:LICENSE" > THIRD_PARTY_LICENSES/OpenJBD-LICENSE

# Merge upstream history while preserving this repository's orchestration and product docs.
# A no-commit merge is intentional so the coordinator can review provenance and conflicts.
git merge "$UPSTREAM_REF" --allow-unrelated-histories --no-commit -X theirs || {
  echo "Merge requires manual conflict resolution. Preserve local project-control files listed below." >&2
}

for path in README.md LICENSE AGENTS.md PROJECT_STATUS.md CLAUDE.md HERMES.md paseo.json scripts docs .github; do
  if git ls-files --unmerged -- "$path" | grep -q .; then
    git checkout --ours -- "$path" || true
    git add "$path" || true
  fi
done

# Recreate upstream license notice after merge resolution.
mkdir -p THIRD_PARTY_LICENSES
git show "${UPSTREAM_REF}:LICENSE" > THIRD_PARTY_LICENSES/OpenJBD-LICENSE
git add THIRD_PARTY_LICENSES/OpenJBD-LICENSE

cat <<EOF

OpenJBD baseline staged for review.

Upstream: $UPSTREAM_URL
Baseline: $UPSTREAM_REF

Next steps:
  1. git status
  2. inspect merge result and conflicts
  3. ensure local README/AGENTS/docs/paseo files are preserved
  4. ensure OpenJBD source/build/test files are present
  5. run: bash scripts/verify.sh
  6. update PROJECT_STATUS.md
  7. commit merge with a message such as:
     chore(upstream): import OpenJBD baseline 7e3e225

Do not commit blindly if conflicts remain.
EOF
