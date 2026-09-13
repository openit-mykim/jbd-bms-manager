# Maintenance Mode

## Purpose

Maintenance Mode contains BMS operations that can change battery behavior. It is intentionally separated from the read-oriented monitoring surfaces so ordinary status review cannot accidentally trigger service operations.

## Primary entry

Maintenance Mode lives under the top-level **Control / 제어** tab.

Recommended entry pattern:

1. Open **Control / 제어**.
2. Show the maintenance workspace in a locked state.
3. Require a deliberate unlock action.
4. Show a warning that service operations can affect BMS behavior.
5. Detect supported device capabilities.
6. Expose only verified functions.

A PIN can be added later if commercial/service deployment requires role separation.

Settings is reserved for application preferences and is no longer the primary entry point to Maintenance Mode.

## Relationship to Balance tab

The top-level **Balance / 밸런스** page is read-oriented and used for cell-group observation and diagnosis.

The rule is:

```text
Balance = observe / diagnose
Control = configure / service
```

Balance settings such as enable state, start voltage and delta threshold belong here under Control, not on the Balance tab.

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

### Balance configuration

- Balancing enable / disable
- Balance start voltage
- Cell-delta threshold
- Other verified balance parameters

Current balance status belongs primarily on the read-oriented Balance tab, although Control may show it as context before applying changes.

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

### Diagnostics

- raw device metadata
- firmware / hardware identifiers
- BLE state
- protocol transaction results
- register-level debug information behind service/developer controls

Raw diagnostics are intentionally secondary and must not become a sixth top-level navigation destination.

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
      ↓
Commit (only when every change verified)
      ↓
Post-commit confirmation read
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
- Commit exit not confirmed
- Post-commit confirmation mismatch

Do not report success until the requested value is verified where read-back is available. A committed session is additionally followed by an independent post-commit confirmation read (fresh read/write session) of all changed registers; an unavailable or mismatched confirmation is surfaced as a warning and recorded in the result, and must never be silently treated as success or failure.

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
