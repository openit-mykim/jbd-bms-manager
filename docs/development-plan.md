# Development Plan

This plan is the execution roadmap for Hermes and Paseo workers. Work should proceed phase by phase unless a blocker requires a temporary research branch.

## Phase 0 — Baseline import and repository bootstrap

Goal: establish a reproducible, traceable OpenJBD-derived codebase without losing this repository's project documentation.

Tasks:

- Add OpenJBD as `upstream` remote.
- Fetch and record baseline commit `7e3e225a128f6e0d69425b98a2670d8d69594885`.
- Merge upstream history using `--allow-unrelated-histories` so provenance is visible in Git history.
- Preserve this repository's project-control files and docs.
- Preserve upstream MIT license separately under `THIRD_PARTY_LICENSES/OpenJBD-LICENSE`.
- Retain upstream implementation and test files.
- Build baseline debug APK.
- Run upstream unit tests.

Exit criteria:

- `./gradlew testDebugUnitTest` succeeds.
- `./gradlew assembleDebug` succeeds.
- provenance and upstream license are present.
- `PROJECT_STATUS.md` is updated.

## Phase 1 — Android 16 compatibility

Goal: correct top and bottom system-bar overlap on API 36 devices.

Tasks:

- Remove functional dependence on `windowOptOutEdgeToEdgeEnforcement`.
- Implement explicit status-bar and navigation-bar WindowInsets handling.
- Ensure every Activity toolbar sits below the status-bar inset.
- Ensure bottom navigation sits above gesture/navigation inset.
- Avoid double-padding on older devices.
- Check portrait and landscape layouts.
- Check gesture navigation and 3-button navigation.
- Check light and dark themes.

Exit criteria:

- No overlap on API 36 reference device/emulator.
- Existing lower API behavior remains acceptable.
- screenshot evidence or manual verification note is attached to PR.

## Phase 2 — Five-tab monitoring productization

Goal: replace the upstream three-tab information architecture with the JBD BMS Manager five-tab product model.

Target bottom navigation:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

Authoritative UX design: `docs/navigation-design.md`.

### Overview

Primary priorities:

1. SOC
2. pack voltage
3. current
4. power
5. temperature
6. cell delta
7. alarm / protection summary
8. charge/discharge MOS state

The page should remain compact and suitable for a quick battery-state judgment.

### Detail

Redistribute useful content from upstream `Parameters` into a user-oriented detailed read-only page:

- capacities
- cycles
- firmware / device identity
- manufacturing information
- detailed temperatures
- protection / MOS state
- future charts/session information

Do not preserve `Parameters` as a top-level destination merely because it exists upstream.

### Balance

Create a dedicated read-oriented cell page:

- every cell-group voltage
- min / max / average
- delta voltage
- active balancing state
- strongest / weakest cell highlighting
- foundation for future weak-cell and divergence diagnostics

Balance configuration itself belongs under Control.

### Control

Add the top-level shell and locked Maintenance Mode entry point. Broad write support is still implemented in later phases.

### Settings

Retain application preferences only. Remove any design assumption that Settings is the main entry point to Maintenance Mode.

Other Phase 2 tasks:

- Rename visible app branding.
- Keep unique application ID.
- Add/complete Korean strings for the five labels and moved screens.
- Preserve landscape dashboard as a secondary monitoring surface.
- Ensure connection/reconnect flows remain clear.

Exit criteria:

- five bottom destinations are present and localized.
- no `Parameters` top-level destination remains.
- Overview/Detail/Balance are read-oriented.
- Control exists but does not claim unsupported write functions.
- Settings contains application preferences only.
- stable BLE connect/reconnect with supported JBD test BMS.

## Phase 3 — Control / Maintenance Mode shell

Goal: build the technician domain behind the Control tab without yet enabling broad high-risk writes.

Architecture:

- locked-by-default Control page.
- deliberate maintenance unlock acknowledgement.
- capability discovery before exposing supported operations.
- common staged edit session.
- change review screen.
- apply transaction coordinator.
- read-back verification.
- audit/result record for each maintenance operation.

Initial sections:

- Device / Firmware
- Calibration
- Protection
- Balance configuration
- Capacity
- Temperature
- MOS Control
- Backup / Restore
- Diagnostics

Exit criteria:

- Control is clearly separated from the three read-oriented monitoring tabs.
- no placeholder screen pretends unsupported commands work.
- common write transaction framework has tests.

## Phase 4 — Calibration

Goal: safely implement calibration functions supported by verified JBD protocol variants.

Candidate functions:

- pack voltage calibration
- cell voltage calibration
- idle current zero calibration
- charge current calibration
- discharge current calibration
- capacity/SOC correction when protocol support is confirmed

Rules:

- never infer register semantics.
- document units and scaling.
- validate allowed range before encode.
- display current reading and target reference.
- require explicit apply.
- read back after write.

Exit criteria:

- each implemented command has protocol tests.
- supported firmware/device scope is documented.
- at least one physical-device validation is recorded before declaring production-ready.

## Phase 5 — Protection, balance configuration and control

Goal: implement validated service configuration under the Control tab.

Areas:

- cell/pack over-voltage and under-voltage protection
- charge/discharge over-current protection
- temperature protection
- balance enable/start voltage/delta
- charge/discharge MOS control
- capacity parameters

Rules:

- use capability gates.
- preserve safety defaults.
- do not expose raw arbitrary register write in normal UI.
- bulk changes require backup first or a clear override.
- Balance tab remains observation/diagnosis only.

## Phase 6 — Backup, restore and diagnostics

Goal: make technician work reproducible.

Backup should include:

- app version
- device identity fields
- firmware/hardware identifiers
- cell count
- timestamp
- readable configuration fields
- protocol variant identifier
- unsupported/unknown field markers

Restore must validate compatibility, show a diff, reject incompatible critical fields and read values back when supported.

Diagnostics live under Control and may include raw device metadata, BLE state and register-level debug output behind service/developer controls.

## Phase 7 — Quality, CI and release

Goal: make maintenance sustainable.

- GitHub Actions test + debug APK on PR.
- release workflow for tagged versions.
- lint / static analysis appropriate to Android project.
- protocol unit-test suite.
- UI regression checks for five-tab navigation and critical flows.
- signed APK/AAB strategy.
- changelog and release notes.
- versioning policy.

## Phase 8 — Optional future expansion

Only after the core BLE app is stable:

- UART/USB transport
- additional JBD variants
- service log export
- battery health analytics
- optional local historical charts
- device profile library

Do not let optional expansion delay safe implementation of the maintenance core.
