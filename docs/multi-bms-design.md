# Multi-BMS Management Design

## Goal

JBD BMS Manager must support more than one battery/BMS and make the active target obvious at all times.

This is not only a convenience feature. The selected BMS becomes the ownership boundary for monitoring state, future service history, backups, calibration records and configuration changes.

## Top app bar selector

The right side of the blue top app bar shows the currently selected BMS name as a persistent selector.

Example:

```text
☰   개요                              NANROBOT ▼
```

States:

- connected: user alias or BLE name
- reconnecting: keep the selected name visible while connection state is handled elsewhere
- no saved/selected BMS: `BMS 미연결`

The selector remains visible on all five primary tabs so the user always knows which battery is being observed or controlled.

## Selector interaction

Tapping the BMS name opens a bottom sheet rather than a narrow popup menu.

Target layout:

```text
등록된 BMS

●  출퇴근 자전거
   A4:C1:38:xx:xx:01       -58 dBm

●  NANROBOT
   A4:C1:38:xx:xx:27       -71 dBm

●  테스트팩 21700
   A4:C1:38:xx:xx:93       -64 dBm

●  예비 배터리
   A4:C1:38:xx:xx:B1       신호 없음

[ + 새 BMS 등록 ]
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
refresh header and active data context
```

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

`alias` is the user-facing name and should be editable independently from the BLE-advertised name.

The MAC address should be stored, but future implementation must not assume the MAC address is always the only permanent identity. Some BLE devices may use changing/private addresses. Where available, device model, serial number, firmware identity or other stable values should contribute to `deviceFingerprint`.

## Registration

When a new BMS is selected from scanning, offer registration:

```text
Bluetooth name: SP14S004
MAC: A4:C1:38:xx:xx:27

관리 이름
[ NANROBOT 배터리 ]

[ 등록 ]
```

The alias is optional; if omitted, BLE name or address is used as fallback display text.

## Signal indicator

Use three states rather than interpreting absence as an error:

- green: device found in the current scan / currently reachable
- gray: registered but not seen in the latest scan
- red: explicit connection failure or device error

RSSI should be shown when available, for example `-61 dBm`.

Do not use red merely because the device is out of range.

## Scanning policy

Do not run continuous background scanning solely for the selector.

Preferred behavior:

1. User opens BMS selector.
2. Start a short scan window, approximately 3–5 seconds.
3. Match advertisements to registered devices.
4. Update reachability indicator and RSSI.
5. Stop scanning.

This reduces unnecessary BLE activity and battery use.

## Startup behavior

Only the last selected/active BMS is eligible for automatic reconnect at startup.

If several registered BMS devices are visible, the application must not arbitrarily choose another device.

## Maintenance safety

The active BMS identity is especially important under Control.

Before any write transaction, Control should repeat the target identity near the operation summary:

```text
대상 BMS
NANROBOT
A4:C1:38:xx:xx:27
```

Future backups, service history and calibration records are keyed to the registered BMS/device fingerprint.

## Initial UI shell

Before full multi-device persistence and scanning are implemented, the product may expose a non-deceptive shell:

- top-right active BMS selector
- bottom sheet
- currently saved/last device shown if available
- green indicator only when that exact device is currently connected
- gray when merely saved
- tapping the saved device reconnects it
- multi-device registration and live scan fields explicitly marked as under development

Do not render fake devices or fake RSSI values in the executable application.

## Implementation phases

### Phase A — selector shell

- persistent top-right selector
- show connected/saved device name
- bottom sheet
- reconnect current saved device

### Phase B — registry

- multiple registered devices
- alias editing
- selected-device persistence
- add/remove/rename workflows

### Phase C — scan state

- short scan on sheet open
- RSSI and green/gray status
- explicit red failure state

### Phase D — device-scoped data

- monitoring history per BMS
- configuration backup per BMS
- calibration/service history per BMS
- identity/fingerprint validation before restore/write
