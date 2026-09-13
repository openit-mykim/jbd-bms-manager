# Development Roadmap

## Phase 0 — Baseline and provenance

- Import the OpenJBD codebase while preserving upstream copyright and MIT license notices.
- Record the upstream commit used as the initial baseline.
- Rename application identifiers and visible app name for JBD BMS Manager.
- Confirm debug build and installation on a current Android device.

## Phase 1 — Android 16 UI compatibility

- Remove reliance on `windowOptOutEdgeToEdgeEnforcement`.
- Apply proper status-bar and navigation-bar insets.
- Ensure toolbar and bottom navigation are not overlapped by system bars.
- Verify portrait and landscape layouts.
- Verify light and dark themes.

## Phase 2 — Monitor Mode

- Keep the OpenJBD BLE connection and read-only monitoring foundation.
- Refine the main dashboard for rapid battery-state checking.
- Prioritize SOC, pack voltage, current, power, cell delta and temperature.
- Preserve detailed cell-group readings and status information.
- Add Korean localization.
- Keep network/cloud functionality out of the default monitoring path.

## Phase 3 — Maintenance Mode framework

- Add a separate Maintenance Mode entry point.
- Protect entry using an explicit unlock flow.
- Separate read-only status screens from writable service screens.
- Introduce a common Read → Edit → Review → Apply transaction pattern.
- Add confirmation and change summaries before every BMS write operation.

## Phase 4 — Calibration

Implement and validate:

- Pack voltage calibration
- Cell voltage calibration
- Idle current calibration
- Charge current calibration
- Discharge current calibration
- Capacity / SOC correction where supported by the BMS protocol

Calibration screens must always show current measured values, requested target values and the exact change that will be written.

## Phase 5 — BMS configuration

Implement protocol-safe editors for:

- Over-voltage and under-voltage protection
- Charge / discharge over-current protection
- Temperature protection
- Balancing enable / start voltage / delta settings
- Charge and discharge MOS control
- Capacity parameters

Where protocol support differs between JBD firmware variants, features must be capability-gated instead of assuming universal support.

## Phase 6 — Backup, restore and diagnostics

- Read and export supported BMS configuration.
- Restore configurations only after compatibility checks.
- Add raw device / firmware / register information for service diagnostics.
- Add clear warnings for unsupported or unknown fields.

## Phase 7 — Quality and distribution

- Add unit tests for frame encoding, decoding and checksums.
- Add regression tests for read/write register handling.
- Add UI tests for dangerous-action confirmation flows.
- Add GitHub Actions APK builds.
- Produce signed release builds only after protocol and device testing.

## Safety principle

Monitor Mode should remain safe for ordinary users. Maintenance Mode is a service tool. Any operation capable of changing protection, balancing, calibration, capacity or MOS behavior must be explicit, reviewable and reversible where the hardware permits it.
