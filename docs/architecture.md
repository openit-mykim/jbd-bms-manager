# Architecture

## Product concept

JBD BMS Manager is an Android application for JBD / Jiabaida smart BMS devices. The application is divided into two clearly separated operating domains:

1. **Monitor Mode** — safe, read-oriented daily use.
2. **Maintenance Mode** — technician-oriented calibration, configuration and diagnostics.

The separation is intentional. Normal users should not encounter protection thresholds, calibration registers or destructive control operations during ordinary battery monitoring.

## Base application

The initial implementation is planned to derive from OpenJBD, which already provides:

- Native Android BLE communication
- JBD frame parsing
- Connection and reconnection handling
- StateFlow-based BMS state repository
- Overview, parameter and settings screens
- Cell-group monitoring
- Landscape dashboard

The upstream baseline should be recorded before source import so later changes remain traceable.

## Proposed application layers

```text
UI
├─ Monitor Mode
│  ├─ Overview
│  ├─ Cells
│  ├─ Status / Health
│  └─ Dashboard
└─ Maintenance Mode
   ├─ Calibration
   ├─ Protection
   ├─ Balance
   ├─ Capacity
   ├─ Temperature
   ├─ MOS Control
   ├─ Backup / Restore
   └─ Diagnostics

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

The OpenJBD baseline targets recent Android versions. Android 16 enforces edge-to-edge behavior for apps targeting API 36, so JBD BMS Manager should use explicit window-inset handling rather than relying on opt-out flags.

The main layout should conceptually be:

```text
status-bar inset
Toolbar
application content
Bottom Navigation
navigation-bar / gesture inset
```

Both portrait and landscape screens must be tested against gesture navigation and traditional navigation-button modes.

## Localization

Initial languages:

- Korean
- English

Additional upstream languages can be retained where practical.

## Safety boundaries

- Monitor Mode performs no protection or calibration writes.
- Maintenance Mode requires an explicit unlock action.
- Dangerous values must be validated against reasonable protocol and hardware ranges.
- Every write should show old value → new value before execution.
- Backup should be encouraged before large configuration changes.
- Unsupported registers must not be guessed from adjacent JBD variants.
