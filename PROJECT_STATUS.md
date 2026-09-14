# Project Status

Last updated: 2026-09-14

## Current phase

**Maintenance sections complete — settings, calibration, MOS control and backup/restore are implemented with verified-write flows under Control**

Current distributable line: `v0.1.0-alpha.x` (latest: `v0.1.0-alpha.10`).

The repository has a reproducible GitHub Actions build that checks out the pinned OpenJBD baseline, applies the JBD BMS Manager overlay, runs unit tests (231 test cases in the materialized tree), builds the APK and publishes a GitHub prerelease.

## Current product decisions

Primary bottom navigation is fixed as:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

Responsibilities:

- Overview: compact battery-state summary. The full per-cell list was moved to Balance; the min/max/delta/average stats grid is retained for quick judgment.
- Detail: user-oriented read-only detail page (capacities, cycles, device identity, detailed status, last-refresh timestamp). Raw/technician fields stay out of Detail.
- Balance: read-oriented cell diagnostic surface — every cell-group voltage (V + mV), min/max/average/delta, absolute-threshold safety bands (normal / caution / danger, sourced from the measured protection limits or an NMC default with an explicit "default reference" label), highest/lowest/balancing shown as text badges, active-balancing count, balance current (supported devices) and a band legend.
- Control: locked-by-default Maintenance Mode shell — explicit unlock acknowledgement, target-BMS identity block, a read-only diagnostics panel, and all maintenance sections interactive: configuration sections (밸런스 설정 / 보호 설정 / 온도 설정 / 용량 관리) with 조회 → 수정 → 확인 → 적용 → Read-back verify; 캘리브레이션 (전류·셀 전압·NTC 보정, three-concept contract: BMS reading / external reference / resulting operation); MOS 제어 (충·방전 FET 차단/허용 with live conducting state); 백업 및 복원 (JSON export + staged, verified restore). Sessions keep the write path capability-gated: unknown variants default to read-only, and a session commits only when every approved change verifies.
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

1. Install `v0.1.0-alpha.10` on the physical Android 16 phone and verify:
   - five-tab navigation renders correctly (개요/상세/밸런스/제어/설정),
   - Balance screen: cell diagnostics plus balance current / active-balancing count / legend — confirm each cell shows both V and mV, that band colors match the connected protection limits (or show the "default reference" label when limits were not read), and that highest/lowest/balancing appear as text badges rather than colors,
   - Balance delta: apply 5 / 15 / 20 mV from the presets and confirm the value is accepted and stored (record the rounding the BMS actually applies),
   - Detail page content and empty states,
   - Control lock → unlock → re-lock flow and the read-only diagnostics panel (진단),
   - Control settings sections (밸런스/보호/온도/용량): 조회 succeeds (공장 모드 or 직접 읽기 per device), a small safe edit stages and reviews old → new, apply shows per-change verification + commit state + post-commit confirmation,
   - Calibration section: 무부하 제로 보정 (zero current only), then one 충전/방전·셀 전압·NTC target with a reference instrument — confirm the three-concept display (BMS 읽기값 / 기준 측정값 / 적용될 작업) and the read-back result; note the current-scale precision actually observed (10 mA assumption),
   - MOS 제어: confirm the live conducting state, then toggle one FET only when interrupting charge/discharge is safe; verify read-back and device behavior,
   - 백업 및 복원: export a backup, change one value, restore from the file and review the diff list before applying; confirm staged verification and persistence,
   - values persist after reconnecting (record `PERSISTENCE_VERIFIED` per `docs/device-validation.md`),
   - system-bar insets on all screens including Device List / About / Licenses,
   - BLE connect/reconnect behavior.
2. Capture protocol evidence on supported hardware using `docs/device-validation.md` (read-only evidence capture first, then the write test template) to raise compatibility-table entries.

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
- [x] Balance readout completion (balance current, active count, legend)
- [x] Control/Maintenance shell implemented (locked; write sections capability-gated)
- [x] Maintenance write-transaction framework implemented with tests (Phase 3)
- [x] Read-only diagnostics panel added under Control
- [x] Settings protocol layer + BLE session integration implemented with tests (read/write frames, factory sessions, retries, post-commit confirmation)
- [x] Settings read/write flow implemented in Control for balance/protection/temperature/capacity sections
- [x] Calibration section implemented with tests (전류·셀 전압·NTC, three-concept contract; physical validation pending)
- [x] MOS control implemented with tests (충·방전 FET 차단 via register 0xE1, live conducting state)
- [x] Backup / restore implemented with tests (JSON export + staged verified restore; physical validation pending)
- [x] Balance delta 5 mV step controls implemented with tests (presets, step clamp, step-multiple validation)
- [x] Cell safety band display implemented with tests (measured/default thresholds, badge separation of highest/lowest/balancing)
- [x] Settings limited to application preferences
- [ ] Physical BMS monitor validation completed
- [ ] Physical settings write validation completed (records `PERSISTENCE_VERIFIED` where applicable)
- [ ] Physical balance delta write validation completed (observed rounding recorded)
- [ ] Physical calibration validation completed (reference instruments; current-scale precision)
- [ ] Production signing / release policy finalized
- [ ] Full OpenJBD source/history imported into this repository

## Phase mapping

- Phase 0 — provenance/full upstream source-history import
- Phase 1 — Android 16 compatibility and physical verification
- Phase 2 — five-tab monitoring productization (content complete; awaiting physical verification)
- Phase 3 — Control / Maintenance Mode (implemented: shell, capability model, staged edits, transaction coordinator, audit, BLE settings sessions with retries + post-commit confirmation, all maintenance sections in UI: settings, calibration, MOS control, backup/restore; physical write validation pending)
- Phase 4 — calibration (implemented in UI under the three-concept contract: current 제로/충전/방전, cell voltage, NTC; physical validation/reference measurement pending)
- Phase 5 — protection / balance configuration / MOS control (implemented in UI; physical validation pending)
- Phase 6 — backup / restore / diagnostics (export + staged restore implemented; diagnostics remains read-only; physical validation pending)
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
- Calibration follows the three-concept contract — BMS reading / external reference measurement / resulting operation are always shown separately; there is no one-tap auto calibration
- MOS control writes are limited to the FET disable bits (0xE1) with live conducting-state display and explicit warnings
- Balance delta accepts only 5 mV multiples (5 mV minimum); the UI never silently rounds an entered value
- Cell safety bands are display-only guidance derived from the measured protection limits, or from an explicit NMC default labelled as such — they never claim to guarantee BMS protection behavior
- Settings sessions commit (`28 28`) only when every approved change verifies, and a committed session is followed by an independent post-commit confirmation read
- Unknown BMS variants default to conservative/read-only behavior
- Initial languages: Korean and English; retain upstream languages where practical
- OpenJBD is the upstream foundation, not a branding dependency

## Update rule

Every phase-completing PR or distributable milestone should update this file. Keep detailed design in `docs/`.
