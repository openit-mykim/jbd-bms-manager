# Project Status

Last updated: 2026-09-13

## Current phase

**Executable alpha established — full source-history import still pending**

Status: **v0.1.0-alpha.1 BUILT / DEVICE VALIDATION PENDING**

A reproducible GitHub Actions build now checks out the pinned OpenJBD baseline, applies the JBD BMS Manager product overlay, runs unit tests, builds the APK, and publishes a GitHub prerelease.

The full OpenJBD source-history import into this repository remains a separate provenance task for Hermes/Paseo. The installable alpha does not depend on completing that import first.

## Current distributable

- Version: `v0.1.0-alpha.1`
- applicationId: `com.openit.jbdbmsmanager`
- Upstream baseline: `gytxtx/OpenJBD@7e3e225a128f6e0d69425b98a2670d8d69594885`
- Build: GitHub Actions
- Unit tests: **PASS**
- Debug APK build: **PASS**
- GitHub prerelease: **PUBLISHED**
- APK SHA-256: `0844a6ab9359cdebcaf6cd1bbb26678b500cbbd0800a464d1c75600b8a3961c9`

Implemented in the alpha overlay:

- Android 16/API 36 explicit WindowInsets handling for top/bottom system bars
- removal of reliance on edge-to-edge opt-out behavior
- Korean UI resources
- Korean option in the in-app language selector
- `JBD BMS Manager` app identity
- unique Android applicationId so the derivative can coexist with upstream OpenJBD
- Korean README
- reproducible APK artifact + prerelease publishing workflow

## Immediate next action

1. Install `v0.1.0-alpha.1` on the Android 16 test phone.
2. Verify the original top Toolbar / bottom Navigation overlap is gone.
3. Connect the actual JBD BMS and verify Monitor Mode, reconnect, cell values, temperature and landscape dashboard.
4. Record any UI/translation issues.
5. Hermes/Paseo then continues full source-history import and Maintenance Mode work.

## Phase checklist

- [x] Repository created: `openit-mykim/jbd-bms-manager`
- [x] MIT license selected for this project
- [x] Agent execution contract added
- [x] Architecture and maintenance-mode concepts documented
- [x] Hermes + Paseo operating model documented
- [x] Paseo project configuration added
- [x] Upstream import procedure documented
- [x] OpenJBD third-party MIT license preserved
- [x] GitHub PR/issue/CODEOWNERS templates added
- [x] Phase issues created
- [ ] Full OpenJBD source/history imported into this repository
- [x] Pinned-baseline unit tests pass in CI
- [x] Pinned-baseline derivative debug APK builds in CI
- [x] Android 16 system-bar fix implemented in product overlay
- [ ] Android 16 system-bar fix physically verified on device
- [x] Korean localization implemented
- [ ] Korean localization physically reviewed on device
- [x] Alpha APK prerelease published
- [ ] Monitor Mode physical BMS validation completed
- [ ] Maintenance Mode shell added
- [ ] Calibration protocol implemented and tested
- [ ] Supported configuration features implemented and tested
- [ ] Backup / restore implemented
- [ ] Production signing / release policy finalized

## Phase issue map

- #1 — Phase 0: full upstream baseline/history import
- #2 — Phase 1: Android 16 system-bar insets (implementation done; device verification pending)
- #3 — Phase 2: Monitor Mode + Korean localization (localization implemented; device validation pending)
- #4 — Phase 3: Maintenance Mode framework
- #5 — Phase 4: calibration
- #6 — Phase 5: configuration controls
- #8 — Phase 6: backup / restore / diagnostics
- #9 — Phase 7: CI / release / long-term maintenance

Issue #7 is a closed duplicate and should be ignored.

## Product decisions already made

- Repository name: `jbd-bms-manager`
- App display name: `JBD BMS Manager`
- Primary transport: BLE
- Core operation: local-first; no account or cloud required
- Modes: Monitor Mode and Maintenance Mode
- Monitor Mode remains read-oriented for service configuration
- Maintenance Mode uses explicit unlock + staged changes
- Unknown BMS variants default to conservative/read-only behavior
- Android initial languages: Korean and English; retain upstream languages where practical
- OpenJBD is the upstream foundation, not a branding dependency

## Update rule

Every phase-completing PR or distributable milestone should update this file. Keep detailed design in `docs/`.
