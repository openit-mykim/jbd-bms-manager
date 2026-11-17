#!/usr/bin/env bash
# Release guard (mobile dev standard 7): inspect the built APK before it is published.
#   1. hard-coded credentials (tokens, cloud keys, private keys)  -> always blocking
#   2. debug signing certificate                                -> blocking unless
#      --allow-debug-signing is given (only for unpublished check builds that did not
#      receive the release keystore secret; see docs/release-process.md "Signing")
# Usage: bash scripts/release-guard.sh <apk> [--allow-debug-signing]
# Prints a guard log on stdout; exit 1 blocks the release.
set -euo pipefail

apk="${1:?usage: release-guard.sh <apk> [--allow-debug-signing]}"
allow_debug="${2:-}"
status=0

echo "release-guard: $(basename "$apk") sha256=$(sha256sum "$apk" | cut -d' ' -f1)"

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
unzip -q -o "$apk" -d "$workdir"

secret_pattern='ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{40,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|xox[abprs]-[A-Za-z0-9-]{10,}|-----BEGIN ([A-Z]+ )?PRIVATE KEY-----'
# Code, the compiled resource table and raw assets are where literals end up in an APK.
mapfile -t payloads < <(find "$workdir" -type f \( -name '*.dex' -o -name 'resources.arsc' -o -path '*/assets/*' \))
hits=""
for f in "${payloads[@]}"; do
  found="$(strings -a "$f" | grep -EIo "$secret_pattern" | sort -u || true)"
  [[ -n "$found" ]] && hits+="$(sed "s|^|${f#"$workdir"/}: |" <<<"$found")"$'\n'
done
if [[ -n "$hits" ]]; then
  echo "BLOCK credentials found:"; echo "$hits" | sed -E 's/(:[^:]{6})[^ ]*/\1…/'
  status=1
else
  echo "PASS  no hard-coded credentials"
fi

apksigner="${APKSIGNER:-$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)}"
if [[ -z "$apksigner" || ! -x "$apksigner" ]]; then
  echo "BLOCK apksigner not found (set ANDROID_HOME or APKSIGNER)"; status=1
else
  if ! certs="$("$apksigner" verify --print-certs "$apk" 2>&1)"; then
    echo "BLOCK APK signature does not verify"; status=1
  elif grep -q 'CN=Android Debug' <<<"$certs"; then
    if [[ "$allow_debug" == "--allow-debug-signing" ]]; then
      echo "WARN  debug-signed APK (allowed only for an unpublished check build)"
    else
      echo "BLOCK debug-signed APK"; status=1
    fi
  else
    echo "PASS  release signing certificate: $(grep -m1 'certificate DN' <<<"$certs" | sed 's/.*DN: //')"
  fi
fi

[[ $status -eq 0 ]] && echo "release-guard: PASSED" || echo "release-guard: BLOCKED"
exit $status
