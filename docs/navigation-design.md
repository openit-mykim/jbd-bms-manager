# Navigation Design

This document defines the primary information architecture of JBD BMS Manager.

## Bottom navigation

The application uses five top-level destinations:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

The five tabs divide the product by user intent rather than by the upstream OpenJBD screen structure.

- **개요 / Overview** — understand the current battery state immediately.
- **상세 / Detail** — inspect detailed operating and device information.
- **밸런스 / Balance** — observe and diagnose cell-group balance behavior.
- **제어 / Control** — enter the technician-oriented maintenance workspace.
- **설정 / Settings** — configure the Android application itself.

The key UX boundary is:

```text
밸런스 = 상태 확인과 진단
제어 = 유지보수 작업
```

## 1. 개요 / Overview

Purpose: provide a battery snapshot that can be understood within a few seconds.

Primary content:

- SOC
- pack voltage
- current
- power
- representative temperature
- protection / alarm summary
- cell delta
- charge / discharge MOS state
- pack summary
- connection state

The Overview page should avoid long parameter tables and should not duplicate every cell voltage.

## 2. 상세 / Detail

Purpose: show detailed read-only operating and device information that does not belong on the Overview page.

Content may include:

- remaining / learned / nominal capacity
- cycle count
- firmware and hardware information
- manufacturing date
- Bluetooth identity
- battery/device identity fields when available
- detailed temperature list
- detailed protection and MOS state
- refresh/update timestamps
- future local historical charts or session data

Useful content from the upstream OpenJBD `Parameters` page should be redistributed here. Technician-only raw information should move to Control diagnostics.

## 3. 밸런스 / Balance

Purpose: make cell-group condition and balancing behavior a first-class diagnostic surface.

Primary content:

- voltage of every cell group
- minimum cell voltage
- maximum cell voltage
- average cell voltage
- delta voltage
- actively balancing cell indication
- highest / lowest cell highlighting
- cell count

Future extensions may include cell-delta trends, repeated weak-cell detection, balancing activity history, and abnormal-cell warnings.

This page is read-oriented. Balance configuration belongs under Control.

## 4. 제어 / Control

Purpose: provide the top-level entry point for Maintenance Mode.

Default state: locked. The user deliberately unlocks the maintenance workspace before service functions are shown.

Planned sections include calibration, protection, balance configuration, capacity management, temperature configuration, MOS control, backup/restore and diagnostics.

The Control tab is the UI home of Maintenance Mode. Settings is no longer the primary entry path for maintenance functions.

## 5. 설정 / Settings

Purpose: configure the application rather than the BMS.

Examples:

- language
- theme
- temperature unit
- refresh interval
- auto-connect
- BLE/application preferences
- app version
- source repository
- licenses

BMS maintenance functions do not belong in Settings.

## Route names

Code-facing route names should remain language-neutral:

```text
overview
detail
balance
control
settings
```

Recommended Korean labels:

```text
개요
상세
밸런스
제어
설정
```

Recommended English labels:

```text
Overview
Detail
Balance
Control
Settings
```

## Navigation constraints

Five items is the intended top-level maximum. New functions should be placed under one of these five domains or opened as a secondary screen.

Each top-level destination should preserve its own scroll/navigation state when switching tabs where practical.

The existing landscape Dashboard remains a secondary monitoring surface rather than a sixth navigation item.

## Migration from OpenJBD

Current upstream destinations:

```text
Overview / Parameters / Settings
```

Target product destinations:

```text
Overview / Detail / Balance / Control / Settings
```

Migration guidance:

- simplify existing Overview into the new Overview page.
- redistribute user-facing Parameters content into Detail.
- move cell-voltage presentation into Balance.
- add Control as the Maintenance Mode entry point.
- retain Settings for application preferences only.
- move raw diagnostics under Control.
