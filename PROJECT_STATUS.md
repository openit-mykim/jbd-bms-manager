# Project Status

Last updated: 2026-09-14

## Current phase

**Maintenance framework established — five-tab content plus the tested write-transaction framework and a read-only diagnostics view**

Current distributable line: `v0.1.0-alpha.x` (latest: `v0.1.0-alpha.7`).

The repository has a reproducible GitHub Actions build that checks out the pinned OpenJBD baseline, applies the JBD BMS Manager overlay, runs unit tests, builds the APK and publishes a GitHub prerelease.

## Current product decisions

Primary bottom navigation is fixed as:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

Responsibilities:

- Overview: compact battery-state summary. The full per-cell list was moved to Balance; the min/max/delta/average stats grid is retained for quick judgment.
- Detail: user-oriented read-only detail page (capacities, cycles, device identity, detailed status, last-refresh timestamp). Raw/technician fields stay out of Detail.
- Balance: read-oriented cell diagnostic surface — every cell-group voltage, min/max/average/delta, highest/lowest highlighting and balancing-cell indication.
- Control: locked-by-default Maintenance Mode shell — explicit unlock acknowledgement, target-BMS identity block, a read-only diagnostics panel (connection / firmware / device / extended information), and planned sections listed as disabled ("준비 중"). No write operations are exposed yet; the Phase 3 write-transaction framework (capability model, staged edits, transaction coordinator, audit) is implemented with unit tests.
- Settings: application preferences only (verified — no maintenance entry points).

The key boundary is:

```text
밸런스 = 상태 확인과 진단
제어 = 유지보수 작업
```

See `docs/navigation-design.md`, `docs/maintenance-mode.md` and `docs/protocol-design.md`.

## Android 16 status

The shared inset layer covers the main toolbar plus the device-list, about and licenses toolbars. The fix is included in the alpha.5+ builds; physical verification on the Android 16 device is still pending.

## Immediate next action

1. Install `v0.1.0-alpha.7` on the physical Android 16 phone and verify:
   - five-tab navigation renders correctly (개요/상세/밸런스/제어/설정),
   - Balance cell diagnostics with a real BMS (cell list, highlighting, balancing marks),
   - Detail page content and empty states,
   - Control lock → unlock → re-lock flow and the read-only diagnostics panel (진단),
   - system-bar insets on all screens including Device List / About / Licenses,
   - BLE connect/reconnect behavior after the navigation migration.
2. Capture protocol evidence on supported hardware using `docs/device-validation.md` (read-only evidence capture) to raise compatibility-table entries.
3. Then connect verified operations (Phase 4+) into the implemented transaction framework.

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
- [x] Five-tab bottom navigation implemented (all five destinations carry product content)
- [x] Overview content migrated/simplified
- [x] Detail screen implemented from useful Parameters content
- [x] Balance screen implemented as first-class cell diagnostic surface
- [x] Control/Maintenance shell implemented (locked; no writes yet)
- [x] Maintenance write-transaction framework implemented with tests (Phase 3)
- [x] Read-only diagnostics panel added under Control
- [x] Settings limited to application preferences
- [ ] Physical BMS monitor validation completed
- [ ] Calibration protocol implemented and tested
- [ ] Supported configuration features implemented and tested
- [ ] Backup / restore implemented
- [ ] Production signing / release policy finalized
- [ ] Full OpenJBD source/history imported into this repository

## Phase mapping

- Phase 0 — provenance/full upstream source-history import
- Phase 1 — Android 16 compatibility and physical verification
- Phase 2 — five-tab monitoring productization (content complete; awaiting physical verification)
- Phase 3 — Control / Maintenance Mode framework (implemented: shell, capability model, staged edits, transaction coordinator, audit; verified operations follow protocol validation)
- Phase 4 — calibration (blocked on protocol evidence/physical validation)
- Phase 5 — protection / balance configuration / MOS control (same gate)
- Phase 6 — backup / restore / diagnostics (read-only diagnostics started; restore remains write-gated)
- Phase 7 — CI / release / long-term maintenance

## Product decisions already made

- Repository: `jbd-bms-manager`
- App name: `JBD BMS Manager`
- Primary transport: BLE
- Core operation: local-first; no account or cloud required
- Top-level navigation: Overview / Detail / Balance / Control / Settings
- Monitoring surfaces are read-oriented
- Control is the home of Maintenance Mode; unlocked state is session-scoped and not persisted
- Settings is application-only
- Balance is observation/diagnosis; balance configuration is under Control
- Maintenance Mode uses explicit unlock + staged changes
- Maintenance writes are capability-gated; the compatibility table ships empty and only exact local evidence can raise it
- Unknown BMS variants default to conservative/read-only behavior
- Initial languages: Korean and English; retain upstream languages where practical
- OpenJBD is the upstream foundation, not a branding dependency

## Update rule

Every phase-completing PR or distributable milestone should update this file. Keep detailed design in `docs/`.
