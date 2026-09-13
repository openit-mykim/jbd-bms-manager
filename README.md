# JBD BMS Manager

[한국어 README](README.ko.md)

Unofficial open-source Android manager for JBD / Jiabaida smart BMS devices.

`JBD BMS Manager` is an extended derivative project based on the OpenJBD monitoring foundation. The product separates read-oriented battery observation from technician-oriented maintenance while keeping both in one coherent application.

> **Agent handoff:** If you are Hermes or another coding agent, read `AGENTS.md` first, then `PROJECT_STATUS.md`. The repository is intentionally prepared so development can continue from this GitHub path alone.

## Primary navigation

The product uses exactly five top-level bottom-navigation destinations:

```text
Overview / Detail / Balance / Control / Settings
개요 / 상세 / 밸런스 / 제어 / 설정
```

Responsibilities:

- **Overview** — compact battery-state summary: SOC, voltage, current, power, temperature, cell delta, protection summary and MOS state.
- **Detail** — detailed read-only operating/device information such as capacities, cycles, firmware, identity, detailed temperatures and protection state.
- **Balance** — cell-group voltage and balancing observation/diagnosis: all cell voltages, min/max/average, delta and active balancing indication.
- **Control** — locked Maintenance Mode entry for calibration, protection configuration, balance configuration, capacity/SOC management, MOS control, backup/restore and diagnostics.
- **Settings** — application preferences only: language, theme, temperature unit, refresh interval, auto-connect, source and license information.

The key UX boundary is:

```text
Balance = observe / diagnose
Control = configure / service
```

The upstream OpenJBD `Overview / Parameters / Settings` structure is therefore migrated toward `Overview / Detail / Balance / Control / Settings`. User-facing parameter information moves into Detail; raw/service diagnostics move under Control.

See `docs/navigation-design.md` for the authoritative information architecture.

## Monitor surfaces

The read-oriented product surfaces are Overview, Detail and Balance. These screens must not expose calibration or BMS configuration writes.

Core monitor capabilities include:

- State of charge (SOC)
- Pack voltage, current and power
- Remaining / learned capacity
- Cycle count
- Cell-group voltages
- Min / max / average cell voltage
- Cell delta
- Temperatures
- Charge / discharge MOS state
- Balance state
- Protection / alarm state
- Auto reconnect
- Landscape dashboard

## Control / Maintenance Mode

Control is the top-level entry to the technician workspace and is locked by default.

Planned service functions:

- Pack voltage calibration
- Cell voltage calibration
- Idle current calibration
- Charge current calibration
- Discharge current calibration
- Capacity / SOC management
- Protection parameter read / write
- Balance parameter read / write
- Temperature parameter management
- Charge / discharge MOS control
- Configuration backup / restore
- Raw BMS information and diagnostics

Maintenance writes follow a staged workflow:

```text
Read current values
        ↓
Edit working copy
        ↓
Validate
        ↓
Review old → new values
        ↓
Explicit Apply
        ↓
Write
        ↓
Read-back verify
```

Unknown or unsupported BMS variants default to read-only behavior.

## Development foundation

Upstream project:

- Repository: `gytxtx/OpenJBD`
- Initial baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- Language: Kotlin
- License: MIT

This repository was created independently rather than with GitHub's native Fork button. Local clones should treat OpenJBD as the `upstream` remote.

For reproducible alpha builds, GitHub Actions checks out the pinned OpenJBD commit, applies this repository's product overlay, runs unit tests, builds the debug APK, and publishes the APK as a prerelease artifact.

See `docs/upstream-sync.md`, `scripts/bootstrap-upstream.sh`, and `scripts/apply-product-overlay.py`.

## Current project state

The repository is in an early executable alpha state. Current work focuses on:

1. OpenJBD monitoring baseline.
2. Android 16 edge-to-edge / system-bar compatibility.
3. Korean localization.
4. JBD BMS Manager product identity.
5. Five-tab information architecture — shell and content in place for this alpha line.
6. Reproducible APK CI/release packaging.

The next major product work is physical verification of the current alpha and protocol evidence capture on supported hardware; the Control/Maintenance write-transaction framework (Read → Edit → Validate → Review → Apply → Read-back) is implemented and awaits verified operations.

See `PROJECT_STATUS.md` and `docs/development-plan.md` for the live execution state.

## Installation

Download the latest prerelease APK from the repository **Releases** page. The package is currently a debug-signed alpha build intended for direct testing, not Play Store distribution.

Android may require permission to install apps from the browser or file manager used to open the APK.

## Hermes + Paseo workflow

This project is designed for Hermes to coordinate work through Paseo worktree isolation.

Repository-level Paseo settings are in `paseo.json`.

Typical isolated task:

```bash
paseo run \
  --new-workspace worktree \
  --worktree-mode branch-off \
  --new-branch feat/five-tab-navigation \
  --base origin/main \
  "Read AGENTS.md, PROJECT_STATUS.md and docs/navigation-design.md. Implement the active scoped task, run focused verification, and report changed files plus test evidence."
```

Use `origin/main`, not an unqualified local `main`, as the normal Paseo worktree base.

## Documentation map

- `AGENTS.md` — authoritative coding-agent execution contract
- `HERMES.md` — Hermes project entry point
- `PROJECT_STATUS.md` — current phase and immediate next action
- `README.ko.md` — Korean project README
- `docs/navigation-design.md` — authoritative five-tab information architecture
- `docs/development-plan.md` — detailed phase-by-phase implementation plan
- `docs/architecture.md` — application/layer architecture
- `docs/maintenance-mode.md` — Control/Maintenance UX and write transaction design
- `docs/hermes-paseo.md` — orchestration and worktree workflow
- `docs/testing.md` — test and physical-device validation policy
- `docs/upstream-sync.md` — OpenJBD provenance and sync procedure
- `docs/repository-governance.md` — branching, PR, CI and release policy
- `docs/roadmap.md` — concise feature roadmap

## Android 16 compatibility

OpenJBD targets SDK 36. On Android 16, toolbars and bottom navigation can overlap the status/navigation gesture areas when an app relies on legacy edge-to-edge behavior.

JBD BMS Manager handles system insets explicitly. All standalone Activity toolbars must receive status-bar inset handling, not only the main screen.

## Repository governance

- Integration branch: `main`
- Normal work: short-lived branches + PR
- Preferred normal merge: squash merge
- Upstream history sync: merge commit may be used where provenance matters
- Branch prefixes: `feat/`, `fix/`, `refactor/`, `test/`, `docs/`, `chore/`
- Commit style: Conventional Commits where practical

## Safety

Changing BMS protection, calibration, balancing, temperature, capacity or MOS parameters can affect battery safety.

This project therefore requires:

- explicit Control/Maintenance unlock
- conservative capability detection
- staged change review
- value validation
- read-back verification where supported
- physical BMS validation before declaring write features production-ready

A successful BLE write alone is not sufficient evidence that a setting is correct or persistent.

## Attribution

OpenJBD is MIT licensed. The original upstream copyright notice is preserved under `THIRD_PARTY_LICENSES/OpenJBD-LICENSE`.

This project's own top-level license is also MIT.

This is an unofficial community project and is not affiliated with or endorsed by JBD / Jiabaida.

## License

MIT License. See `LICENSE`.
