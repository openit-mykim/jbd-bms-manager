# Project Status

Last updated: 2026-09-13

## Current phase

**Phase 0 — Upstream baseline import and repository bootstrap**

Status: **READY TO EXECUTE**

The repository is prepared for Hermes + Paseo development. The OpenJBD application source itself has not yet been imported.

## Immediate next action

Hermes should execute `scripts/bootstrap-upstream.sh` from a clean clone in a dedicated Paseo worktree/branch, review the resulting merge, run baseline verification, and open a PR.

Expected upstream baseline:

- Upstream: `gytxtx/OpenJBD`
- Baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- Baseline date: 2026-08-08
- License: MIT

Primary tracking issue: **#1 Phase 0: Import OpenJBD baseline with provenance**.

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
- [ ] OpenJBD source imported with provenance preserved
- [ ] Baseline unit tests pass
- [ ] Baseline debug APK builds
- [ ] Android 16 system-bar overlap reproduced and fixed
- [ ] Monitor Mode baseline stabilized
- [ ] Korean localization added
- [ ] Maintenance Mode shell added
- [ ] Calibration protocol implemented and tested
- [ ] Supported configuration features implemented and tested
- [ ] Backup / restore implemented
- [ ] Release CI and signed distribution defined

## Phase issue map

- #1 — Phase 0: upstream baseline import
- #2 — Phase 1: Android 16 system-bar insets
- #3 — Phase 2: Monitor Mode + Korean localization
- #4 — Phase 3: Maintenance Mode framework
- #5 — Phase 4: calibration
- #6 — Phase 5: configuration controls
- #8 — Phase 6: backup / restore / diagnostics
- #9 — Phase 7: CI / release / long-term maintenance

Issue #7 is a closed duplicate and should be ignored.

## Known initial defect

On Android 16 / API 36, the current OpenJBD baseline can render the top toolbar and bottom navigation underneath system-bar insets. The first product code change after baseline import must replace opt-out assumptions with explicit WindowInsets handling.

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

Every merged phase-completing PR must update this file. Keep it short. Move design detail into `docs/` rather than growing this status file indefinitely.
