# Multi-BMS Management Design

## Goal

JBD BMS Manager must support more than one battery/BMS while keeping one explicit active target. Device discovery, registration, selection and reconnection use one entry point only.

This is also a safety boundary: monitoring state, backups, calibration records and future writable Control operations must always belong to the explicitly selected physical BMS.

## Top app bar

The blue top app bar has two distinct responsibilities:

```text
[device-list icon]   개요                         통근용 48V
```

- left navigation/device-list button: the only BMS management entry point
- right-side text: read-only current connection status / active BMS name

The right-side text is not a combo box, dropdown or button. When no BMS is connected it displays `BMS 미연결`.

This removes the previous duplicated interaction where both the left button and the top-right BMS name opened device-selection UI.

## Left button behavior

### No registered BMS

Tapping the left button opens the BLE device search screen immediately. The screen scans for nearby compatible BMS devices and shows search progress/results.

```text
BMS 연결

장치 검색 중...

SP14S004
A4:C1:38:xx:xx:27     -58 dBm
```

Selecting a discovered device registers/remembers it and connects to it. Alias editing becomes part of the full registry implementation.

### One or more registered BMS devices

Tapping the left button opens the registered-device list first.

```text
BMS 연결

●  통근용 48V
   A4:C1:38:xx:xx:01

○  예비 배터리
   A4:C1:38:xx:xx:B1

[ + 새 BMS 검색 ]
```

Selecting a registered BMS performs:

```text
cancel pending reconnect
        ↓
disconnect current BMS
        ↓
set selected device
        ↓
connect selected BMS
        ↓
verify connection
        ↓
refresh active data context and top-right status text
```

The bottom `+ 새 BMS 검색` action opens BLE discovery.

## Device model

Recommended persistent model:

```text
RegisteredBms
- id
- alias
- bleName
- macAddress
- deviceFingerprint
- lastSeenAt
- lastRssi
- lastConnectedAt
- autoReconnect
- notes (optional)
```

`alias` is user-facing and independently editable from the BLE-advertised name.

Store the MAC address, but do not assume it is always the only permanent identity. Where available, model, serial, firmware identity or another stable value should contribute to `deviceFingerprint`.

## Signal semantics

Use three states:

- green: currently connected or seen/reachable during the latest scan
- gray: registered but not seen in the latest scan
- red: explicit connection failure or device error

RSSI should be shown when available, for example `-61 dBm`.

Do not use red merely because a registered device is out of range.

## Scanning policy

Do not continuously scan in the background just to decorate the list.

Preferred behavior:

1. With no registered device, entering BMS management starts discovery immediately.
2. With registered devices, show the saved list first.
3. Start discovery when the user taps `+ 새 BMS 검색`.
4. In the full registry phase, a short optional scan may refresh reachability/RSSI for registered devices.
5. Stop scanning when the discovery window completes or the screen is closed.

## Startup behavior

Only the last explicitly selected BMS is eligible for automatic reconnect at startup.

If several registered BMS devices are visible, the application must not arbitrarily switch to another one.

## Maintenance safety

The active BMS identity is especially important under Control. Before any write transaction, Control repeats the target identity near the operation summary:

```text
대상 BMS
통근용 48V
A4:C1:38:xx:xx:01
```

Future backups, service history and calibration records are keyed to the registered BMS/device fingerprint.

## Current implementation shell

The current alpha still persists only the last device from the OpenJBD baseline. Therefore the executable UI must not pretend that a true multi-device registry already exists.

Current behavior:

- top-right text is read-only connection status/name
- left app-bar button is the single BMS-management entry point
- no saved device: open BLE search immediately
- saved device: show it as the current registered-device row
- tapping the saved row reconnects it
- `+ 새 BMS 검색` opens BLE discovery
- no fake devices and no fake RSSI values

When the persistent registry is implemented, the same BMS-management screen expands from one saved row to the full registered-device list without changing the navigation model.

## Implementation phases

### Phase A — unified device-management shell

- one left-side BMS management entry point
- read-only top-right connection status/name
- no saved device → discovery
- saved device → saved list + new-device search

### Phase B — persistent registry

- multiple registered devices
- aliases
- selected-device persistence
- add/remove/rename workflows

### Phase C — scan state

- short scan for reachability/RSSI
- green/gray state
- explicit red failure state

### Phase D — device-scoped data

- monitoring history per BMS
- configuration backup per BMS
- calibration/service history per BMS
- identity/fingerprint validation before restore/write
