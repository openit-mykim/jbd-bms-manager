# Physical BMS Validation

Writable Maintenance Mode features are not production-ready until validated on real supported hardware.

## Validation record

Create one record per device/firmware combination.

```text
Date:
Tester:
App commit/tag:
BMS manufacturer/model:
PCB/model marking:
Firmware version:
Hardware version:
Cell count:
Battery chemistry:
Nominal pack voltage:
Connection transport:
BLE address/name:
External instruments used:
Test power source/load:
Safety setup:
```

## Read-only baseline

Record before any write test:

```text
Pack voltage:
Pack current:
SOC:
Cell voltages:
Max/min/delta:
Temperatures:
MOS state:
Protection state:
Balance state:
Relevant configuration values:
```

Compare pack/cell voltage and current against suitable reference instruments before calibration testing.

## App UI / navigation verification (alpha builds)

Verify on the reference Android 16 phone after installing the current alpha:

- five-tab bottom navigation (개요 / 상세 / 밸런스 / 제어 / 설정) renders and switches correctly;
- Balance tab with a connected BMS: every cell voltage, highest/lowest highlighting, balancing marks and min/max/average/delta statistics, balance current (when the device reports it), active-balancing count and the highlight legend;
- Detail tab: content and empty state;
- Control tab: locked state → unlock warning → target BMS block → Diagnostics (진단) panel expands read-only and collapses again on re-lock;
- Control settings sections (밸런스 설정 / 보호 설정 / 온도 설정 / 용량 관리): 조회 shows current values with units (공장 모드 or 직접 읽기), editing is enabled only after a successful read, a small safe edit stages and reviews old → new, applying shows per-change verification, commit state and post-commit confirmation; reconnect and confirm persistence (see Settings flow verification below);
- Android 16 system bars: main, device list, about and licenses screens;
- BLE connect / reconnect behavior after the navigation migration.

Record each violation with a screenshot and the app version (tag).

## Settings flow verification (alpha.8+)

Run in order, conservatively (prefer a small, non-safety-critical change first — e.g. balancing window or design capacity — before touching protection thresholds):

1. `조회`: open each settings section; confirm values appear with units and the access mode (공장 모드 / 직접 읽기) is reported. `직접 읽기` results are read-only by design.
2. `수정 → 확인`: stage one small change; confirm the review shows old → new and that invalid values are rejected before any BLE traffic.
3. `적용`: confirm the dialog lists the change and the warnings (test stage + commit rule + error-counter reset notice); apply.
4. `결과`: record per-change verification (`적용·검증됨` required for success), commit state, post-commit confirmation (matched count / mismatches) and any warnings.
5. `PERSISTENCE`: disconnect, reconnect (and power-cycle if appropriate), re-read the same section. Persistence is verified only when the new value is still present — record `PERSISTENCE_VERIFIED`.
6. On any mismatch, no-commit result or unknown warning, stop and capture the full result screen before retrying.

## Protocol evidence capture (before implementing writable fields)

Read-only only. Capture evidence before any writable field is implemented and before the compatibility table is extended.

For each candidate register (start with the calibration candidates listed in `docs/protocol-design.md`):

```text
Register / candidate:
Function (per community mapping):
Device + firmware/board under test:
Read attempt result (raw bytes / status):
Decoded value vs external reference (instrument, reading):
Stable across retries? (N):
Notes:
```

## Write test template

For each maintenance command:

```text
Feature:
Capability detected by app:
Register/command:
Raw encoded request:
Human-readable original value:
Human-readable requested value:
Validation range result:
BMS response status:
Immediate read-back:
Read-back after reconnect:
Read-back after BMS power cycle, if appropriate:
Observed functional behavior:
Pass/Fail:
Notes:
```

## Calibration validation

### Idle current

- establish a genuine zero-current condition;
- record BMS current before calibration;
- perform service workflow;
- record immediate and post-reconnect reading;
- confirm no unexpected offset during later charge/discharge measurement.

### Charge/discharge current

- use a suitable external reference measurement;
- avoid testing at unnecessarily high pack current;
- document actual reference current and BMS reading before/after calibration;
- confirm sign/direction handling separately.

### Voltage

- use an appropriate calibrated/reliable voltage reference;
- verify pack vs per-cell calibration semantics independently;
- do not assume one register mapping applies to every firmware variant.

## Protection-setting validation

Protection features require conservative service conditions.

Do not deliberately drive a large traction battery into hazardous over-voltage, under-voltage, over-current or thermal states solely to prove UI behavior.

Prefer:

- protocol-level tests;
- read-back verification;
- controlled bench hardware where physical trip-point testing is necessary;
- conservative test settings and suitable current/voltage-limited equipment.

## Result status

A feature may be classified as:

- `UNVERIFIED` — documented/researched only
- `READ_VERIFIED` — values read successfully on target hardware
- `WRITE_VERIFIED` — write + read-back verified
- `PERSISTENCE_VERIFIED` — value persists across reconnect/power cycle where expected
- `BEHAVIOR_VERIFIED` — device behavior confirmed under controlled conditions

The compatibility table must not claim a higher support level than the evidence recorded here.
