# Project Status

Last updated: 2026-09-13

## Current phase

**Phase 0 — Upstream baseline import and repository bootstrap**

Status: **READY TO EXECUTE**

The repository documentation and agent orchestration contract are being prepared before importing the OpenJBD application source.

## Immediate next action

Hermes should execute `scripts/bootstrap-upstream.sh` from a clean clone of this repository, review the resulting merge, run the baseline verification, and open a PR if the bootstrap is performed on a feature branch.

Expected upstream baseline:

- Upstream: `gytxtx/OpenJBD`
- Baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- Baseline date: 2026-08-08
- License: MIT

## Phase checklist

- [x] Repository created: `openit-mykim/jbd-bms-manager`
- [x] MIT license selected for this project
- [x] Agent execution contract added
- [x] Architecture and maintenance-mode concepts documented
- [x] Hermes + Paseo operating model documented
- [x] Paseo project configuration added
- [x] Upstream import procedure documented
- [ ] OpenJBD source imported with provenance preserved
- [ ] Upstream license notice preserved in repository
- [ ] Baseline unit tests pass
- [ ] Baseline debug APK builds
- [ ] Android 16 system-bar overlap reproduced and fixed
- [ ] Monitor Mode baseline stabilized
- [ ] Korean localization added
- [ ] Maintenance Mode shell added
- [ ] Calibration protocol implemented and tested
- [ ] Protection / balance / capacity writes implemented and tested
- [ ] Backup / restore implemented
- [ ] Release CI and signed distribution defined

## Known initial defect

On Android 16 / API 36, the current OpenJBD baseline can render the top toolbar and bottom navigation underneath system bar insets. The upstream project targets SDK 36 but still relies partly on edge-to-edge opt-out behavior.

The first product code change after baseline import must replace opt-out assumptions with explicit WindowInsets handling.

## Product decisions already made

- Repository name: `jbd-bms-manager`
- App display name: `JBD BMS Manager`
- Primary transport: BLE
- Core operation: local-first; no account or cloud required
- Modes: Monitor Mode and Maintenance Mode
- Monitor Mode: read-only with respect to protection/calibration configuration
- Maintenance Mode: explicit unlock + staged writes
- Maintenance transaction: Read → Edit → Validate → Review → Apply → Read-back verify
- Unknown BMS variants: default to read-only
- Android initial languages: Korean and English; retain upstream languages where practical
- OpenJBD is the upstream foundation, not a branding dependency

## Update rule

Every merged phase-completing PR must update this file. Keep it short. Move design detail into `docs/` rather than growing this status file indefinitely.
