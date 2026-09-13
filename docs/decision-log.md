# Decision Log

This file records product and engineering decisions that should not be re-litigated by future agents unless new evidence justifies a change.

## D001 — Repository and product name

Decision: use `jbd-bms-manager` as repository name and `JBD BMS Manager` as app display name.

Reason: the project covers monitoring and maintenance, not only Bluetooth transport. Avoid `BT` in the product name so future transports are not constrained by branding.

## D002 — Upstream relationship

Decision: use OpenJBD as the initial code foundation, but keep this repository as an independent product repository rather than a GitHub-native fork.

Reason: the project has a different product scope, governance and roadmap. Preserve upstream history and attribution through Git remotes and provenance-aware merges.

Initial upstream baseline: `gytxtx/OpenJBD@7e3e225a128f6e0d69425b98a2670d8d69594885`.

## D003 — Two operating modes

Decision: separate the application into Monitor Mode and Maintenance Mode.

- Monitor Mode is read-oriented and safe for everyday use.
- Maintenance Mode is technician-oriented and contains calibration/configuration functions.

Reason: writable BMS functions should not be mixed into normal monitoring UX.

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

Decision: after baseline import, fix system-bar overlap before UI feature work.

Reason: the observed OpenJBD baseline can render the top toolbar and bottom navigation under Android 16 system bars. A stable base UI is required before productization.

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

## Change rule

If a future PR changes one of these decisions materially, update this file in the same PR with:

- old decision
- new decision
- evidence/reason
- migration impact
