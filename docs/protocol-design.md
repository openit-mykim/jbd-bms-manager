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

Concrete community-documented sequence for JBD-class firmware (candidates until confirmed on target hardware):

```text
enter:        write 56 78 → register 0x00
exit:         write 00 00 → register 0x01          (no EEPROM commit)
exit/commit:  write 28 28 → register 0x01          (persists EEPROM values, resets error counters)
password set: write length-prefixed password → register 0x06 before entering factory mode
password mgmt: set_password 0x07, clear_password 0x09
```

The commit exit has a documented side effect: error counters (`0xAA`) are reset to zero. The app should snapshot error counters before a commit exit so the audit record preserves them.

Cross-verified multi-source evidence (2026-09-14):

- `sshoecraft/jbdtool` (C, production CLI) performs its parameter writes inside `enter(56 78) → writes → exit(00 00)` sessions and never uses `28 28`.
- SmartBMSUtility (Swift, production app) likewise enters `56 78` and always exits with `00 00`, keeping the mode open only while its configuration screen is active.
- The bms-tools-derived register map documents `28 28` as "update EEPROM values and reset error counters".

Because persistence semantics across firmware variants are ambiguous, this app's policy is:

1. read back every changed register **inside** the session;
2. exit with commit (`28 28`) only when every approved change verified — otherwise exit without commit (`00 00`) and report precisely which changes verified;
3. after a successful commit, run an independent **post-commit confirmation read** (fresh factory session) because it is the strongest available persistence evidence;
4. snapshot error counters (`0xAA`) before the commit exit because `28 28` resets them.

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

Concrete register candidates extracted from the community register-map mirror (see references; accessed 2026-09-14; **all candidates until hardware-verified**). All stored registers are 16-bit big-endian unless noted; reads use mode `0xA5`, writes use mode `0x5A`; responses carry register address, status (`0x00` OK / `0x80` error), length and data.

| Address | Candidate meaning | Format / unit | Notes |
|---|---|---|---|
| `0x10` | design capacity | U16, 10 mAh | |
| `0x11` | cycle capacity | U16, 10 mAh | |
| `0x12`, `0x13`, `0x32..0x35` | SOC estimate points (100 / 0 / 80 / 60 / 40 / 20 %) | U16, 1 mV | raw cell-voltage points |
| `0x14` | cell self-discharge rate estimate | U16, 0.1 % | |
| `0x15` | manufacture date | packed bits | mirror of basic-info date field |
| `0x16` | serial number | U16 | |
| `0x17` | cycle count | U16, 1 cycle | |
| `0x18..0x1B` | charge temperature thresholds + releases | U16, 0.1 K | chgot / chgot_rel / chgut / chgut_rel |
| `0x1C..0x1F` | discharge temperature thresholds + releases | U16, 0.1 K | dsgot / dsgot_rel / dsgut / dsgut_rel |
| `0x20..0x23` | pack over/under-voltage thresholds + releases | U16, 10 mV | povp / povp_rel / puvp / puvp_rel |
| `0x24..0x27` | cell over/under-voltage thresholds + releases | U16, 1 mV | covp / covp_rel / cuvp / cuvp_rel |
| `0x28`, `0x29` | charge / discharge over-current thresholds | S16, 10 mA | charge positive, discharge negative |
| `0x2A` | balancing start voltage | S16, 1 mV | signedness disputed across community sources (bms-tools S16 / jbdtool unsigned); values are positive in practice; raw-byte verification unaffected |
| `0x2B` | balancing delta/window | U16, 1 mV | jbdtool treats this as signed; kept as U16 |
| `0x2C` | shunt resistor value | U16, 0.1 mΩ | |
| `0x2D` | function config bits | U16 bitfield | bit 2 balance enable, bit 3 charge-balance enable (also switch/scrl/led bits) |
| `0x2E` | NTC enable bits | U16 bitfield | NTC 1..8 |
| `0x2F` | cell count | U16, 1 cell | |
| `0x30`, `0x31` | FET control time setting / LED display time setting | U16 | names per jbdtool (`fet_ctrl_time_set`, `led_disp_time_set`); exact semantics partially documented |
| `0x36..0x39` | secondary protections | mixed | secondary cell OV/UV, short-circuit, secondary over-current packs |
| `0x3A..0x3F` | release delay byte packs | 2 × U8 | temperature, pack voltage, cell voltage, over-current release delays (seconds) |
| `0x40`, `0x41` | GPS voltage / time registers | S16 | present in jbdtool; variant-specific, absent on most packs |
| `0x42..0x47` | additional SOC estimate points (90 / 70 / 50 / 30 / 10 / 100 %) | U16, 1 mV | jbdtool VOLCAP90..100 |
| `0x48..0x9F` | unassigned in community map | — | do not touch |
| `0x0A`, `0xE3` | factory reset magic sequences | destructive | community tools use magic payloads; this app must never expose reset writes |
| `0xA0..0xA2` | manufacturer / device name / barcode | length-prefixed strings | |
| `0xAA` | error counters | 11 × U16 | read-only |

Cross-check: SmartBMSUtility writes design capacity as `value / 10` (10 mAh units) and self-discharge rate as `value × 10` (0.1 % units), independently confirming those scale conventions; jbdtool's parameter table matches the addresses above.

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

## Defensive patterns (cross-verified)

Patterns validated against two independent production implementations (`sshoecraft/jbdtool`, SmartBMSUtility):

- **Retry only on loss of response** (nothing received). Error status and malformed frames are definitive outcomes and are never retried, and a lost response to the commit exit (`28 28`) is never retried because the commit is ambiguous and resets error counters. Evidence: SmartBMSUtility re-sends on timeout and aborts after repeated failures; jbdtool retries failed response verifications on serial links.
- **Read-modify-write for bitfields**, preserving unrelated bits, verified by a full read-back — never write a blindly composed mask. Evidence: jbdtool had a field incident where a composed `BatteryConfig` write silently cleared the field and disabled balancing; it now validates the composed value before writing. This app stages bit-level changes against a fresh read.
- **Treat response status `0x80`/`0x81` as a rejection of that operation**, not as data. Some reseller variants reject read/write mode entirely (reported for LionTron packs via register `0x00`/`0xE1` responses) — those must stay read-only through the capability gate. Some older tooling ignores nonzero status entirely; this app is deliberately stricter.
- **Write acknowledgment format**: a successful write answers with a zero-length payload frame `DD <reg> <status> <00> <crcH> <crcL> 77` (with `status = 0`, the checksum bytes are `00 00`).
- **Keep read/write mode scoped to the session** and surface a warning when exit cannot be confirmed (device may remain in factory mode). SmartBMSUtility keeps the mode open only while its configuration screen is active, tracks mode state, and re-enters automatically when needed.
- **Application-side guards**: conservative per-field raw ranges, strict scale alignment on encode (no silent coercion), staged review with old → new values, and diff-based writes (only changed fields are staged).

## Factory password (firmware ≥ 0x16)

- `0x06 use_password` — write the current 6-byte password (length-prefixed) before entering factory mode when a password is set.
- `0x07 set_password` — change password (payload length 12: current + new).
- `0x09 clear_password` — write ASCII `J1B2D4` to clear.

Evidence: SmartBMSUtility supports creating/removing a Bluetooth password and reading/writing configuration on password-protected devices. This app does **not** implement password entry yet; password-protected devices surface as blocked sessions until a password flow with explicit UI exists.

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
- official Jiabaida communication protocol PDF (mirrored in syssi repo): `https://github.com/syssi/esphome-jbd-bms/blob/main/docs/Jiabaida.communication.protocol.pdf`
- community JBD serial/register map mirror: `https://github.com/ieb/N2KLifePo4/blob/main/JBD-BMS-SERIAL-INTERFACE.md`
- bms-tools upstream register-map origin (GitLab): `https://gitlab.com/bms-tools/bms-tools` — map file: `https://gitlab.com/bms-tools/bms-tools/-/blob/master/JBD_REGISTER_MAP.md`
- open_battery project referenced by OpenJBD: `https://shishir-dey.github.io/open_battery/`

Settings read/write implementation references (sample code for EEPROM access):

- jbdtool parameter read/write utility (C CLI; named parameter `-r`/`-w` incl. `BalanceStartVoltage`, `BalanceWindow`, `BatteryConfig` bitfield read/write): `https://github.com/sshoecraft/jbdtool`
- jbdtool design notes incl. parameter table, write flow, and the BatteryConfig silent-clear incident history: `https://github.com/sshoecraft/jbdtool/blob/main/docs/main.md`
- SmartBMSUtility app source (Swift; BLE read/write mode handling, config read/write queues, timeout retry, password-protected config sessions): `https://github.com/KG-Development/SmartBMSUtility`
- JiabaidaBMS ESP32 implementation that reads state and writes configuration: `https://github.com/beelsebob/JiabaidaBMS`
- JBD-UP16S010 protocol notes incl. write frames (`DD 5A …`) and a Modbus-RTU variant: `https://gist.github.com/PhracturedBlue/7ef619594eaa4c27f4ff068b461865b8` — updated revision: `https://gist.github.com/dmitrych5/e2fa4ef16b0b483808e4f4089846d0d0`

Balance and monitoring read references:

- ESP32 BLE reader with per-cell and balance-state display: `https://github.com/kolins-cz/Smart-BMS-Bluetooth-ESP32`
- Jiabaida Protocol V4 host tool (balance-state interval formatting `1-3, 5, 7-9`, timeout/retry design): `https://github.com/wow-meow/bms-uart`
- Cross-platform UART/BLE library with Python parsers: `https://github.com/ethycS0/jbd_bms`

These sources are useful engineering evidence, but physical compatibility still must be recorded for the BMS variants this project claims to support.
