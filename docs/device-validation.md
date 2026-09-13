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
- Balance tab with a connected BMS: every cell voltage, highest/lowest highlighting, balancing marks and min/max/average/delta statistics;
- Detail tab: content and empty state;
- Control tab: locked state → unlock warning → target BMS block → Diagnostics (진단) panel expands read-only and collapses again on re-lock;
- Android 16 system bars: main, device list, about and licenses screens;
- BLE connect / reconnect behavior after the navigation migration.

Record each violation with a screenshot and the app version (tag).

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
