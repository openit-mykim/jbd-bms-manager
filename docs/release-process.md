# Release Process

## Versioning rule

Releases use official semantic versions only. The current line is `0.1.x`.

- Current release: `v0.1.1` (`versionCode = 16`).
- Next fix release: `v0.1.2` (`versionCode = 17`), then `v0.1.3`, and so on.
- Do not reintroduce `alpha`, `beta` or numbered preview suffixes in version names, workflow tags, APK names or release notes.
- `versionCode` must increase on every published release. Never reuse a tag or overwrite an existing official release.
- If a release must be corrected after publication, publish the next patch version instead of moving the old tag.

## Source of version values

| Value | Location |
|---|---|
| `versionCode` / `versionName` | `scripts/apply-product-overlay.py` |
| About-screen version (EN) | `scripts/apply-product-overlay.py` (`about_version`) |
| About-screen version (KO) | `overlays/app/src/main/res/values-ko/strings.xml` |
| Tag, APK name, release title | `.github/workflows/android-release.yml` env |

The overlay is applied as a single step. Do not add per-release `apply-alphaN.py` scripts; put packaging changes in `apply-product-overlay.py`.

## Procedure

1. `git fetch origin` and branch from `origin/main` as `chore/release-<version>`.
2. Update the version table above and `PROJECT_STATUS.md`, both README files, and any version assertions.
3. Run the materialized unit-test suite locally if practical:
   `python3 scripts/apply-product-overlay.py <upstream-src>` then `./gradlew testDebugUnitTest`.
4. Open the release PR and wait for the `build` check to pass.
5. Merge with a merge commit to match repository history.
6. The push to `main` runs `Android Release Build`. It tests, builds the debug APK, writes the SHA-256 file and publishes an official GitHub release (not a prerelease) targeting the merge commit.
7. Verify with `gh release view <tag> --json tagName,isPrerelease,isLatest,assets`.
8. Download the APK and SHA-256 asset, then compare:
   `sha256sum -c <apk>.sha256` and compare against the workflow artifact.
9. Record hardware verification separately. A green CI build does not prove BLE write behavior; use `docs/device-validation.md` and report `NOT HARDWARE VERIFIED` when no device was attached.

## Release gate

- `build` check green on the release PR and on `main`.
- Official release published with `isPrerelease = false`.
- APK asset hash matches the published SHA-256 and the CI artifact.
- Release notes list known limitations and the hardware-verification status.
