#!/usr/bin/env bash
# Local equivalent of the CI build: materialize the pinned OpenJBD baseline,
# apply the product overlay, then run unit tests and the debug APK build.
#
# Usage: bash scripts/verify.sh [--tests-only]
# Env:   JBD_BUILD_DIR (default: .build/upstream-src), ANDROID_HOME / ANDROID_SDK_ROOT
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
props="$repo_root/version.properties"
build_dir="${JBD_BUILD_DIR:-$repo_root/.build/upstream-src}"

# Single source of truth for the pinned baseline: version.properties.
upstream_repo="$(sed -n 's/^upstreamRepo=//p' "$props")"
upstream_commit="$(sed -n 's/^upstreamCommit=//p' "$props")"
if [[ -z "$upstream_repo" || -z "$upstream_commit" ]]; then
  echo "Could not read upstreamRepo / upstreamCommit from $props" >&2
  exit 2
fi

# Always start from a clean pinned checkout so stale overlay files never leak in.
if [[ ! -d "$build_dir/.git" ]]; then
  mkdir -p "$(dirname "$build_dir")"
  git clone --quiet "https://github.com/${upstream_repo}.git" "$build_dir"
fi
git -C "$build_dir" fetch --quiet origin "$upstream_commit" 2>/dev/null || true
git -C "$build_dir" checkout --quiet --force "$upstream_commit"
git -C "$build_dir" clean -fdq -e local.properties -e .gradle -e build -e app/build

python3 "$repo_root/scripts/apply-product-overlay.py" "$build_dir"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -n "$sdk" && ! -f "$build_dir/local.properties" ]]; then
  echo "sdk.dir=$sdk" > "$build_dir/local.properties"
fi

cd "$build_dir"
chmod +x ./gradlew
./gradlew testDebugUnitTest
if [[ "${1:-}" != "--tests-only" ]]; then
  ./gradlew assembleDebug
  echo "APK: $build_dir/app/build/outputs/apk/debug/app-debug.apk"
fi
