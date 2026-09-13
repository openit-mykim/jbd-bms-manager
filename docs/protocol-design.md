# JBD Protocol Design Notes

This document is an implementation guide, not a claim that every JBD-compatible BMS supports every register below.

The project must distinguish:

- **verified upstream behavior** already used by OpenJBD;
- **documented community register mappings** that still need local verification;
- **physically validated device behavior** confirmed on supported BMS hardware.

Unknown firmware defaults to read-only.

## BLE transport used by the OpenJBD baseline

OpenJBD currently uses these GATT UUIDs:

```text
Service: 0000ff00-0000-1000-8000-00805f9b34fb
Notify : 0000ff01-0000-1000-8000-00805f9b34fb
Write  : 0000ff02-0000-1000-8000-00805f9b34fb
CCCD   : 00002902-0000-1000-8000-00805f9b34fb
```

These should remain in the transport layer rather than being repeated throughout feature code.

## Frame model

Common JBD serial/BLE framing is documented as:

```text
Start  : 0xDD
Mode   : 0xA5 read / 0x5A write
Address: 1 byte register
Length : 1 byte
Data   : N bytes
Checksum: 2 bytes
End    : 0x77
```

Responses contain register, status, length, payload, checksum and end marker.

Checksum handling belongs in one protocol utility with unit tests. UI/domain code must never assemble frames directly.

## Layering

Recommended package direction after baseline stabilization:

```text
transport/
  BleTransport
  BleSession

protocol/
  JbdFrameCodec
  JbdReadCommand
  JbdWriteCommand
  JbdRegister
  JbdProtocolVariant

domain/
  capability/
  calibration/
  protection/
  balance/
  capacity/
  maintenance/

ui/
  monitor/
  maintenance/
```

Do not rush package restructuring during the baseline import. Introduce abstractions incrementally when Phase 3 starts.

## Factory/configuration mode

Community JBD register maps describe a factory/configuration session before reading/writing many EEPROM settings.

Candidate flow to verify per firmware:

```text
optional password authorization
        ↓
enter factory/config mode
        ↓
read current configuration
        ↓
perform validated writes
        ↓
read back
        ↓
exit/save factory mode
```

Do not hard-code one universal sequence until verified against the target SP14S004-class hardware and firmware.

## Candidate calibration registers

Community protocol references identify the following addresses. Treat these as **research candidates until tested**:

| Address | Candidate function | Notes |
|---|---|---|
| `0xAD` | idle current calibration | commonly documented as zero-current calibration |
| `0xAE` | charge current calibration | write measured positive current; raw scaling must be confirmed |
| `0xAF` | discharge current calibration | write measured magnitude; scaling/sign handling must be confirmed |
| `0xB0..0xCF` | per-cell voltage calibration | up to 32 cells, commonly 1 mV units |
| `0xD0..0xD7` | NTC calibration | commonly 0.1 K units |
| `0xE0` | remaining capacity | behavior/units require variant confirmation |
| `0xE1` | MOS control | bit semantics must be capability-gated |
| `0xE2` | balance control | service/factory behavior differs by firmware |

The app must not expose these merely because an address exists in a community register map.

## Candidate EEPROM configuration groups

Community maps commonly describe groups for:

- design/full capacity
- SOC voltage points
- charge/discharge temperature thresholds
- pack over/under-voltage thresholds
- cell over/under-voltage thresholds
- charge/discharge over-current thresholds
- balancing start voltage and delta/window
- NTC enable bits
- cell count
- function flags

Before implementing any field:

1. identify register address;
2. identify signedness;
3. identify unit/scaling;
4. identify allowed range;
5. identify firmware/board compatibility;
6. test read behavior;
7. add encode/decode unit test;
8. implement staged write;
9. read back;
10. physically validate on target BMS.

## Capability model

Do not use a single `supportsWrites = true` flag.

Use granular capabilities, for example:

```text
supportsFactoryMode
supportsPackVoltageCalibration
supportsCellVoltageCalibration
supportsCurrentCalibration
supportsNtcCalibration
supportsProtectionWrite
supportsBalanceConfig
supportsMosControl
supportsCapacityWrite
supportsBackupRestore
```

Capability evidence may come from:

- exact BMS model
- hardware revision
- firmware version
- successful safe register reads
- known protocol variant
- local compatibility table

If evidence is ambiguous, capability is false/read-only.

## Transaction API

Writable domain code should invoke an operation abstraction rather than raw BLE:

```text
MaintenanceTransaction
  readCurrent()
  validate(candidate)
  diff(current, candidate)
  apply(approvedDiff)
  readBack()
  verify()
```

A BLE ACK is not enough for success. The result model should distinguish:

```text
SuccessVerified
WriteRejected
TransportDisconnected
Timeout
MalformedResponse
Unsupported
ReadBackMismatch
PartialApply
```

## Calibration UX contract

Calibration must always show three concepts separately:

```text
BMS reading
External reference measurement
Resulting calibration operation
```

Examples:

- pack voltage: BMS voltage vs calibrated multimeter reference
- current: BMS current vs external current reference
- idle current: explicit zero-current condition
- cell voltage: BMS cell-group reading vs verified reference measurement

Do not provide a one-tap automatic calibration that writes using unverified sensor assumptions.

## Protocol evidence sources

Initial research references:

- OpenJBD upstream source: `https://github.com/gytxtx/OpenJBD`
- syssi ESPHome JBD implementation and protocol reference: `https://github.com/syssi/esphome-jbd-bms`
- community JBD serial/register map mirror: `https://github.com/ieb/N2KLifePo4/blob/main/JBD-BMS-SERIAL-INTERFACE.md`
- open_battery project referenced by OpenJBD: `https://shishir-dey.github.io/open_battery/`

These sources are useful engineering evidence, but physical compatibility still must be recorded for the BMS variants this project claims to support.
