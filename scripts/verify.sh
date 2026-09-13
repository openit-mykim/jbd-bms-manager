#!/usr/bin/env bash
set -euo pipefail

if [[ ! -x ./gradlew ]]; then
  chmod +x ./gradlew 2>/dev/null || true
fi

if [[ ! -f ./gradlew ]]; then
  echo "gradlew not found. OpenJBD baseline has not been imported yet." >&2
  exit 2
fi

./gradlew testDebugUnitTest
./gradlew assembleDebug
