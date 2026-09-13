# Development Plan

This plan is the execution roadmap for Hermes and Paseo workers. Work should proceed phase by phase unless a blocker requires a temporary research branch.

## Phase 0 — Baseline import and repository bootstrap

Goal: establish a reproducible, traceable OpenJBD-derived codebase without losing this repository's project documentation.

Tasks:

- Add OpenJBD as `upstream` remote.
- Fetch and record baseline commit `7e3e225a128f6e0d69425b98a2670d8d69594885`.
- Merge upstream history using `--allow-unrelated-histories` so provenance is visible in Git history.
- Preserve this repository's `README.md`, `LICENSE`, `AGENTS.md`, `PROJECT_STATUS.md`, `CLAUDE.md`, `HERMES.md`, `paseo.json`, `scripts/`, and `docs/`.
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
- Ensure toolbar's visual content sits below status-bar inset.
- Ensure bottom navigation sits above gesture/navigation inset.
- Avoid double-padding on pre-API 35 devices.
- Check portrait and landscape layouts.
- Check gesture navigation and 3-button navigation.
- Check light and dark themes.
- Add regression coverage where practical.

Exit criteria:

- No overlap on API 36 reference device/emulator.
- Existing lower API behavior remains acceptable.
- screenshot evidence or manual verification note is attached to PR.

## Phase 2 — Monitor Mode productization

Goal: turn upstream monitoring UI into the stable daily-use surface of JBD BMS Manager.

Primary screen priorities:

1. SOC
2. pack voltage
3. current
4. power
5. cell delta
6. temperature
7. alarm / protection state

Tasks:

- Rename visible app branding.
- Review package/application ID migration strategy; do not rename packages until baseline is stable.
- Simplify high-frequency monitoring UI.
- Keep detailed cells screen.
- Preserve landscape dashboard.
- Add Korean strings.
- Ensure connection/reconnect flows are clear.
- Keep Monitor Mode free of protection/calibration writes.

Exit criteria:

- stable BLE connect/reconnect with supported JBD test BMS.
- no write path reachable from Monitor Mode.
- Korean/English core screens complete.

## Phase 3 — Maintenance Mode shell

Goal: introduce a service domain without yet enabling broad high-risk writes.

Architecture:

- explicit Maintenance Mode entry.
- unlock acknowledgement.
- capability discovery before exposing write controls.
- common staged edit session.
- change review screen.
- apply transaction coordinator.
- read-back verification.
- audit/result record for each maintenance operation.

Initial screens:

- Device / Firmware
- Calibration
- Protection
- Balance
- Capacity
- Temperature
- MOS Control
- Backup / Restore
- Diagnostics

Exit criteria:

- Maintenance Mode shell is separated from Monitor Mode.
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

## Phase 5 — Protection, balance and control

Goal: implement validated service configuration.

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

Restore must:

- validate device compatibility.
- show diff before write.
- reject incompatible or unknown critical fields.
- read back all restored values when supported.

Diagnostics:

- raw device metadata
- BLE state
- protocol transaction result
- register-level debug output behind developer/service controls

## Phase 7 — Quality, CI and release

Goal: make maintenance sustainable.

- GitHub Actions test + debug APK on PR.
- release workflow for tagged versions.
- lint / static analysis appropriate to Android project.
- protocol unit-test suite.
- UI regression checks for critical flows.
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
