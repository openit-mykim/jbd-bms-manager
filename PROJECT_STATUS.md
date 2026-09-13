# Project Status

Last updated: 2026-09-13

## Current phase

**Executable alpha established — five-tab productization is now the active UI direction**

Current distributable line: `v0.1.0-alpha.x`.

The repository has a reproducible GitHub Actions build that checks out the pinned OpenJBD baseline, applies the JBD BMS Manager overlay, runs unit tests, builds the APK and publishes a GitHub prerelease.

## Current product decisions

Primary bottom navigation is now fixed as:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

Responsibilities:

- Overview: compact battery-state summary.
- Detail: detailed read-only operation/device information.
- Balance: cell-group voltage and balancing diagnosis.
- Control: locked Maintenance Mode and supported service functions.
- Settings: application preferences only.

The key boundary is:

```text
밸런스 = 상태 확인과 진단
제어 = 유지보수 작업
```

See `docs/navigation-design.md`.

## Android 16 status

The first Android 16 system-bar fix corrected the main screen, but physical testing showed that standalone screens such as Device List use different toolbar IDs and still required status-bar inset handling.

The shared inset layer has therefore been expanded to cover all known Activity toolbars, including:

- main toolbar
- device-list toolbar
- about toolbar
- licenses toolbar

A new alpha build is used for physical verification of this follow-up fix.

## Immediate next action

1. Verify the follow-up Android 16 toolbar fix on the physical Android 16 phone.
2. Implement the five-tab bottom navigation shell.
3. Migrate existing content:
   - existing Overview → Overview
   - user-facing Parameters → Detail
   - cell voltage/balance presentation → Balance
   - Maintenance shell → Control
   - app preferences → Settings
4. Preserve BLE connect/reconnect behavior during the navigation migration.
5. Continue Maintenance Mode implementation only after the five-tab shell is stable.

## Phase checklist

- [x] Repository created: `openit-mykim/jbd-bms-manager`
- [x] MIT license selected
- [x] Agent execution contract added
- [x] Hermes + Paseo operating model documented
- [x] Upstream OpenJBD baseline pinned
- [x] Reproducible unit-test/debug-APK CI established
- [x] Korean localization added
- [x] Initial Android 16 system-bar handling implemented
- [x] Follow-up inset design expanded to standalone Activity toolbars
- [x] Five-tab information architecture decided and documented
- [ ] Follow-up Android 16 fix physically verified
- [ ] Five-tab bottom navigation implemented
- [ ] Overview content migrated/simplified
- [ ] Detail screen implemented from useful Parameters content
- [ ] Balance screen implemented as first-class cell diagnostic surface
- [ ] Control/Maintenance shell implemented
- [ ] Settings limited to application preferences
- [ ] Physical BMS monitor validation completed
- [ ] Calibration protocol implemented and tested
- [ ] Supported configuration features implemented and tested
- [ ] Backup / restore implemented
- [ ] Production signing / release policy finalized
- [ ] Full OpenJBD source/history imported into this repository

## Phase mapping

- Phase 0 — provenance/full upstream source-history import
- Phase 1 — Android 16 compatibility and physical verification
- Phase 2 — five-tab monitoring productization
- Phase 3 — Control / Maintenance Mode framework
- Phase 4 — calibration
- Phase 5 — protection / balance configuration / MOS control
- Phase 6 — backup / restore / diagnostics
- Phase 7 — CI / release / long-term maintenance

## Product decisions already made

- Repository: `jbd-bms-manager`
- App name: `JBD BMS Manager`
- Primary transport: BLE
- Core operation: local-first; no account or cloud required
- Top-level navigation: Overview / Detail / Balance / Control / Settings
- Monitoring surfaces are read-oriented
- Control is the home of Maintenance Mode
- Settings is application-only
- Balance is observation/diagnosis; balance configuration is under Control
- Maintenance Mode uses explicit unlock + staged changes
- Unknown BMS variants default to conservative/read-only behavior
- Initial languages: Korean and English; retain upstream languages where practical
- OpenJBD is the upstream foundation, not a branding dependency

## Update rule

Every phase-completing PR or distributable milestone should update this file. Keep detailed design in `docs/`.
