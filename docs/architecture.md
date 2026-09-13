# Architecture

## Product concept

JBD BMS Manager is an Android application for JBD / Jiabaida smart BMS devices. The product separates everyday observation from technician-oriented maintenance while presenting both through one coherent five-tab navigation model.

Primary bottom navigation:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

The navigation model is authoritative. See `docs/navigation-design.md` for detailed screen responsibilities.

The core separation remains:

1. **Read-oriented monitoring surfaces** — Overview, Detail and Balance.
2. **Maintenance workspace** — Control.
3. **Application preferences** — Settings.

Normal users should not encounter calibration or BMS configuration operations while reviewing battery status.

## Base application

The initial implementation derives from OpenJBD, which already provides:

- Native Android BLE communication
- JBD frame parsing
- Connection and reconnection handling
- StateFlow-based BMS state repository
- Overview, parameter and settings screens
- Cell-group monitoring
- Landscape dashboard

The upstream baseline is pinned and recorded so later changes remain traceable.

## Proposed application layers

```text
UI
├─ Overview
│  └─ compact battery-state summary
├─ Detail
│  └─ detailed read-only operating/device information
├─ Balance
│  └─ cell-group voltage and balancing diagnostics
├─ Control
│  └─ Maintenance Mode
│     ├─ Calibration
│     ├─ Protection
│     ├─ Balance configuration
│     ├─ Capacity
│     ├─ Temperature
│     ├─ MOS Control
│     ├─ Backup / Restore
│     └─ Diagnostics
└─ Settings
   └─ application preferences only

Domain / Application
├─ Read operations
├─ Maintenance transactions
├─ Capability detection
├─ Validation
└─ Change review / confirmation

Protocol
├─ JBD frame encoder
├─ JBD frame decoder
├─ Read-register commands
├─ Write-register commands
├─ Calibration commands
└─ Checksum / response validation

Transport
└─ Android BLE GATT
```

## Navigation responsibility

### Overview

Fast status judgment: SOC, voltage, current, power, temperature, protection summary, cell delta and MOS state.

### Detail

Detailed read-only information such as capacity, cycles, firmware/device identity, detailed temperatures and protection state. User-facing content from the upstream `Parameters` screen should migrate here.

### Balance

Cell-group voltages, min/max/average, delta and active-balancing indication. This is an observation/diagnostic surface, not a configuration surface.

### Control

Top-level entry to Maintenance Mode. Calibration, protection settings, balance settings, capacity management, MOS operations, backup/restore and diagnostics live here. Control is locked by default and capability-gated.

### Settings

Application preferences only: language, theme, temperature unit, refresh interval, auto-connect and app information. BMS configuration must not be placed here.

## Maintenance transaction model

Writable operations should not directly bind form controls to BLE writes.

Use this flow instead:

```text
Read current BMS configuration
        ↓
Create editable working copy
        ↓
Validate candidate values
        ↓
Generate change set
        ↓
Show review screen
        ↓
Explicit Apply confirmation
        ↓
Write to BMS
        ↓
Read back and verify
```

A successful transport write is not sufficient. Where supported, the application should read the value back from the BMS and verify that the requested setting was actually stored.

## Capability detection

JBD-compatible devices and firmware revisions can differ. Maintenance functions should therefore be enabled according to known capabilities rather than exposing every command on every device.

Possible capability inputs include:

- Firmware / hardware version
- BMS model information
- Cell count
- Supported extension fields
- Successful register reads
- Known protocol variant

Unknown devices should default to conservative, read-only behavior until write compatibility is confirmed.

## Android UI / system bars

The OpenJBD baseline targets recent Android versions. Android 16 enforces edge-to-edge behavior for apps targeting API 36, so JBD BMS Manager uses explicit window-inset handling rather than relying on opt-out flags.

The main layout should conceptually be:

```text
status-bar inset
Toolbar
application content
Bottom Navigation (5 destinations)
navigation-bar / gesture inset
```

All standalone Activity toolbars must also receive status-bar insets. Both portrait and landscape screens must be tested against gesture navigation and traditional navigation-button modes.

## Localization

Initial languages:

- Korean
- English

Additional upstream languages can be retained where practical.

## Safety boundaries

- Overview, Detail and Balance perform no calibration/configuration writes.
- Control requires an explicit maintenance unlock action.
- Dangerous values must be validated against reasonable protocol and hardware ranges.
- Every write should show old value → new value before execution.
- Backup should be encouraged before large configuration changes.
- Unsupported registers must not be guessed from adjacent JBD variants.
