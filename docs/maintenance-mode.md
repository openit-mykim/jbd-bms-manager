# Maintenance Mode

## Purpose

Maintenance Mode contains BMS operations that can change battery behavior. It is intentionally separated from normal monitoring so ordinary users cannot accidentally alter protection, balancing or calibration parameters.

## Entry

Recommended entry pattern:

1. Open Settings.
2. Select **Maintenance Mode**.
3. Long-press or perform another deliberate unlock action.
4. Show a warning that changes affect BMS behavior and battery safety.
5. Enter the maintenance workspace.

A PIN can be added later if commercial/service deployment requires role separation.

## Sections

### Calibration

- Pack voltage calibration
- Cell voltage calibration
- Idle-current zero calibration
- Charge-current calibration
- Discharge-current calibration
- Capacity / SOC correction when supported

Calibration should require stable external reference measurements. The UI should show both the current BMS reading and the reference value entered by the technician.

### Protection

Read and edit supported settings such as:

- Cell over-voltage protection
- Cell over-voltage release
- Cell under-voltage protection
- Cell under-voltage release
- Charge over-current protection
- Discharge over-current protection
- Relevant protection delays

Do not expose registers on unknown firmware variants unless support is verified.

### Balance

- Balancing enable / disable
- Balance start voltage
- Cell-delta threshold
- Current balance status

### Capacity

- Nominal / design capacity
- Learned / full capacity
- Remaining capacity where supported
- SOC reset / correction where supported

### Temperature

- Read temperature sensors
- Read supported temperature protection thresholds
- Edit temperature parameters only on verified variants
- Calibration where protocol support is confirmed

### MOS Control

- Charge MOS state
- Discharge MOS state

Manual MOS switching should require a separate confirmation because it can immediately interrupt charging or discharge.

### Backup / Restore

Backup should capture only fields actually read from the connected BMS and should include device-identification metadata.

Restore flow:

1. Read connected BMS identity and capabilities.
2. Load backup.
3. Compare compatibility.
4. Show exact differences.
5. Require explicit confirmation.
6. Apply supported values.
7. Read back and verify.

A backup from a different or unknown BMS variant should never be blindly written.

## Write workflow

All writable settings should follow the same transaction model:

```text
Current BMS value
      ↓
Edit working value
      ↓
Validation
      ↓
Change summary
      ↓
Apply confirmation
      ↓
Protocol write
      ↓
Read-back verification
```

Example review:

```text
Cell OVP       4.250 V → 4.200 V
Cell UVP       2.800 V → 3.000 V
Balance delta  15 mV   → 10 mV

Apply 3 changes
```

## Failure handling

A write transaction should distinguish at least:

- BLE disconnected before write
- Write frame rejected
- Checksum / malformed response
- Timeout
- Write acknowledged but read-back differs
- Feature unsupported by connected firmware

Do not report success until the requested value is verified where read-back is available.

## Audit / service history

A later version may maintain a local service history containing:

- Date/time
- Device identifier
- BMS model / firmware
- Changed field
- Previous value
- New value
- Verification result

This should remain local by default unless a future product requirement explicitly adds synchronization.
