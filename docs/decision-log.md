# Decision Log

This file records product and engineering decisions that should not be re-litigated by future agents unless new evidence justifies a change.

## D001 — Repository and product name

Decision: use `jbd-bms-manager` as repository name and `JBD BMS Manager` as app display name.

Reason: the project covers monitoring and maintenance, not only Bluetooth transport. Avoid `BT` in the product name so future transports are not constrained by branding.

## D002 — Upstream relationship

Decision: use OpenJBD as the initial code foundation, but keep this repository as an independent product repository rather than a GitHub-native fork.

Reason: the project has a different product scope, governance and roadmap. Preserve upstream history and attribution through Git remotes and provenance-aware merges.

Initial upstream baseline: `gytxtx/OpenJBD@7e3e225a128f6e0d69425b98a2670d8d69594885`.

## D003 — Two operating domains

Decision: separate read-oriented monitoring from technician-oriented maintenance.

- Read-oriented monitoring is presented through Overview, Detail and Balance.
- Maintenance Mode lives under Control.

Reason: calibration and BMS configuration must not be mixed into ordinary monitoring UX.

## D004 — Maintenance write transaction

Decision: all writable maintenance operations use:

`Read → Edit → Validate → Review → Apply → Read-back verify`

Reason: direct UI-to-register writes are too easy to trigger accidentally and do not provide adequate verification.

## D005 — Unknown devices default to read-only

Decision: writable features require positive capability evidence for the connected BMS/firmware variant.

Reason: JBD-compatible hardware and firmware vary. Register adjacency or partial protocol similarity is not enough evidence for safe write support.

## D006 — Local-first BLE operation

Decision: core operation requires no cloud account and no remote backend.

Reason: the primary use case is direct battery monitoring and service over local BLE. Cloud dependency adds complexity without being necessary for core value.

## D007 — Android 16 insets are first product-code fix

Decision: stabilize system-bar behavior before broader UI feature work.

Reason: the observed OpenJBD baseline can render toolbars and bottom navigation under Android 16 system bars. A stable base UI is required before productization.

## D008 — Hermes coordinates, Paseo isolates work

Decision: Hermes is the coordinating agent; Paseo worktrees are used for non-trivial implementation and review tasks.

Reason: isolated worktrees reduce file collisions and allow implementation/review/test agents to operate independently while keeping `main` clean.

## D009 — Remote base for Paseo worktrees

Decision: branch from `origin/main`, not unqualified local `main`.

Reason: a local `main` may be stale. Paseo documentation recommends an explicit remote-tracking base for current worktree creation.

## D010 — Physical validation required for write support claims

Decision: protocol documentation and unit tests are necessary but not sufficient for production-ready write functionality.

Reason: real BMS behavior, persistence, firmware variation and scaling must be confirmed on supported hardware.

## D011 — Preserve MIT attribution

Decision: keep this project's MIT license and preserve OpenJBD's original MIT notice separately after import.

Reason: imported OpenJBD source remains subject to its upstream copyright/license notice.

## D012 — Five-tab bottom navigation

Decision: use exactly five top-level destinations:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

Responsibilities:

- Overview: compact battery-state summary.
- Detail: detailed read-only operation/device information.
- Balance: cell-voltage and balancing observation/diagnosis.
- Control: locked Maintenance Mode and supported service operations.
- Settings: application preferences only.

Reason: this maps the UI to user intent instead of exposing the upstream `Parameters` structure directly. Five items fit the intended bottom-navigation maximum while leaving room for monitoring, cell diagnostics and maintenance as first-class concepts.

Constraint: do not add a sixth top-level tab. Secondary functions belong under one of these five destinations.

## D013 — Balance vs Control boundary

Decision: Balance is read-oriented; balance configuration belongs under Control.

Rule:

```text
밸런스 = 상태 확인과 진단
제어 = 유지보수 작업
```

Reason: users should be able to inspect individual cells without being placed next to writable BMS settings.

## D014 — Settings is app-only

Decision: Settings contains application preferences and app information only. Maintenance Mode must not use Settings as its primary navigation entry.

Reason: application preferences and BMS service functions are different domains. The Control tab makes the safety boundary visible and understandable.

## D015 — Multi-BMS registry and persistent active target

Decision: support multiple registered BMS devices with user aliases, while maintaining exactly one explicit active/selected BMS at a time.

UI rules:

- the blue top app bar shows the active BMS name at the right on all five primary tabs.
- tapping the active BMS opens a bottom sheet of registered devices.
- registered devices show alias/name, address, and live reachability/RSSI when scanning is implemented.
- green means currently seen/reachable, gray means registered but not currently seen, and red is reserved for explicit connection failure/error.
- selecting a registered BMS disconnects/cancels reconnect for the previous device and immediately connects the selected device.
- startup auto-connect targets only the last explicitly selected BMS.

Identity rule: store MAC address but do not treat it as the only permanent identity. Add a stable device fingerprint using BMS serial/model/firmware identity when available.

Safety reason: monitoring data, backups, calibration records and Maintenance Mode writes must all be scoped to the explicitly selected physical BMS.

See `docs/multi-bms-design.md`.

## Change rule

If a future PR changes one of these decisions materially, update this file in the same PR with:

- old decision
- new decision
- evidence/reason
- migration impact
