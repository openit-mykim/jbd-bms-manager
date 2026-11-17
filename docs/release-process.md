# Release Process

## Versioning rule

Releases use official semantic versions only. The current line is `0.1.x`.

- Current release: `v0.1.2` (`versionCode = 17`).
- Next fix release: `v0.1.3` (`versionCode = 18`), and so on.
- Do not reintroduce `alpha`, `beta` or numbered preview suffixes in version names, workflow tags, APK names or release notes.
- `versionCode` must increase on every published release. Never reuse a tag or overwrite an existing official release.
- If a release must be corrected after publication, publish the next patch version instead of moving the old tag.

## Source of version values

| Value | Location |
|---|---|
| `versionCode` / `versionName` / pinned upstream | `version.properties` (single source; scripts and the workflow read it) |
| Release notes | `release-notes/v<version>.md` (required; the workflow fails without it) |

The overlay is applied as a single step. Do not add per-release `apply-alphaN.py` scripts; put packaging changes in `apply-product-overlay.py`.

## Procedure

1. `git fetch origin` and branch from `origin/main` as `chore/release-<version>`.
2. Update the version table above and `PROJECT_STATUS.md`, both README files, and any version assertions.
3. Run the materialized unit-test suite locally if practical:
   `python3 scripts/apply-product-overlay.py <upstream-src>` then `./gradlew testDebugUnitTest`.
4. Open the release PR and wait for the `build` check to pass.
5. Merge with a merge commit to match repository history.
6. The push to `main` runs `Android Release Build`. It runs unit tests (including Robolectric screen tests) and Android lint, builds the release APK signed with the fixed release key, writes the SHA-256 file and publishes an official GitHub release (not a prerelease) targeting the merge commit. If a release for `RELEASE_TAG` already exists, the workflow skips publishing with a warning and never replaces published assets — bump the version to release.
7. Verify with `gh release view <tag> --json tagName,isPrerelease,isLatest,assets`.
8. Download the APK and SHA-256 asset, then compare:
   `sha256sum -c <apk>.sha256` and compare against the workflow artifact.
9. Record hardware verification separately. A green CI build does not prove BLE write behavior; use `docs/device-validation.md` and report `NOT HARDWARE VERIFIED` when no device was attached.

## Signing (fixed release key, issue #9)

Every published APK since `v0.1.3` is signed with one fixed release key so that new versions install over old ones.

| Item | Value |
|---|---|
| Certificate | `CN=JBD BMS Manager Release, O=openit-mykim`, RSA 4096, valid until 2059-09-25 |
| Certificate SHA-256 | `BF:94:8E:45:89:E1:23:11:C3:6B:BF:8D:87:61:29:2E:6E:9B:A3:36:BA:94:5B:77:EB:FA:06:DA:6F:57:3F:AE` |
| Keystore | PKCS12, alias `jbd-bms-manager`, store password = key password |
| CI secrets | `JBD_RELEASE_KEYSTORE_B64` (base64 keystore), `JBD_RELEASE_STORE_PASSWORD`, `JBD_RELEASE_KEY_ALIAS` |
| Owner copies | maintainer machine only, outside every repository (two copies, mode 600). Never commit, print or upload the keystore or its password. |

How it works:

- `apply-product-overlay.py` adds a `release` signing config that reads `JBD_RELEASE_KEYSTORE_FILE`, `JBD_RELEASE_STORE_PASSWORD` and `JBD_RELEASE_KEY_ALIAS` from the environment. Without them the release variant is unsigned.
- The workflow decodes the keystore into `$RUNNER_TEMP`, runs `assembleRelease`, and deletes the file in an `always()` step.
- `release-guard.sh` blocks debug-signed APKs. `--allow-debug-signing` is passed only to check builds that did not receive the secret (fork PRs); those builds are never published, and `main` fails instead of falling back.
- Losing the key means users must uninstall to update again. Keep an offline backup (for example an encrypted USB copy) in addition to the machine copies.
- Upgrade from `v0.1.2` or older (each CI run used a fresh debug key) needs one uninstall; release notes must say so.

Verify a downloaded APK: `apksigner verify --print-certs <apk>` must show the certificate SHA-256 above.

Local signed build: export the three variables (keystore path, password from the owner copy) and run `./gradlew assembleRelease` in the materialized tree.

## Release gate

- `build` check green on the release PR and on `main`.
- Official release published with `isPrerelease = false`.
- APK asset hash matches the published SHA-256 and the CI artifact.
- Release notes list known limitations and the hardware-verification status.
- `release-guard.txt` shows `PASS  release signing certificate` (no debug signing).
