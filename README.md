# JBD BMS Manager

Unofficial open-source Android manager for JBD / Jiabaida smart BMS devices.

`JBD BMS Manager` is an extended derivative project based on the OpenJBD monitoring foundation. Its core product decision is to separate everyday battery monitoring from technician-oriented maintenance operations.

> **Agent handoff:** If you are Hermes or another coding agent, read `AGENTS.md` first, then `PROJECT_STATUS.md`. The repository is intentionally prepared so development can continue from this GitHub path alone.

## Product model

### Monitor Mode

Safe, read-oriented daily use:

- State of charge (SOC)
- Pack voltage, current and power
- Remaining / learned capacity
- Cycle count
- Cell-group voltages
- Min / max / average cell voltage
- Cell delta (ΔV)
- Temperatures
- Charge / discharge MOS state
- Balance state
- Protection / alarm state
- Auto reconnect
- Landscape dashboard

Monitor Mode must not expose protection, calibration or arbitrary write operations.

### Maintenance Mode

Technician-oriented service functions:

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

This repository was created independently rather than with GitHub's native Fork button. Local clones should treat OpenJBD as the `upstream` remote. The initial source import is designed to preserve upstream Git history and MIT attribution.

See `docs/upstream-sync.md` and `scripts/bootstrap-upstream.sh`.

## Current project state

The repository is currently at **Phase 0: upstream baseline import and bootstrap**.

The first coding sequence is:

1. Import OpenJBD baseline with provenance preserved.
2. Verify upstream tests and debug APK build.
3. Fix Android 16 edge-to-edge / system-bar overlap.
4. Stabilize Monitor Mode and add Korean localization.
5. Introduce Maintenance Mode framework.
6. Add calibration and writable configuration incrementally.
7. Add backup / restore and diagnostics.
8. Harden CI, tests and release workflow.

See `PROJECT_STATUS.md` and `docs/development-plan.md` for the live execution state.

## Hermes + Paseo workflow

This project is designed for Hermes to coordinate work through Paseo worktree isolation.

Repository-level Paseo settings are in `paseo.json`.

Typical isolated task:

```bash
paseo run \
  --new-workspace worktree \
  --worktree-mode branch-off \
  --new-branch fix/android16-insets \
  --base origin/main \
  "Read AGENTS.md and PROJECT_STATUS.md, implement the active scoped task, run focused verification, and report changed files plus test evidence."
```

Use `origin/main`, not an unqualified local `main`, as the normal Paseo worktree base.

## Documentation map

- `AGENTS.md` — authoritative coding-agent execution contract
- `HERMES.md` — Hermes project entry point
- `PROJECT_STATUS.md` — current phase and immediate next action
- `docs/development-plan.md` — detailed phase-by-phase implementation plan
- `docs/architecture.md` — application/layer architecture
- `docs/maintenance-mode.md` — service-mode UX and write transaction design
- `docs/hermes-paseo.md` — orchestration and worktree workflow
- `docs/testing.md` — test and physical-device validation policy
- `docs/upstream-sync.md` — OpenJBD provenance and sync procedure
- `docs/repository-governance.md` — branching, PR, CI and release policy
- `docs/roadmap.md` — concise feature roadmap

## Bootstrap

After cloning this repository into a clean working tree, the coordinator can stage the initial OpenJBD import with:

```bash
bash scripts/bootstrap-upstream.sh
```

The script intentionally stops at a reviewable merge state. Inspect the result, resolve any conflicts, preserve project-control files, run verification and commit only after review.

Normal verification after source import:

```bash
bash scripts/verify.sh
```

## Android 16 initial compatibility issue

The OpenJBD baseline targets SDK 36 but can render the top toolbar and bottom navigation underneath Android system-bar insets on Android 16 devices. The first product code change after baseline import is to replace opt-out assumptions with explicit WindowInsets handling.

## Repository governance

- Integration branch: `main`
- Normal work: short-lived branches + PR
- Preferred normal merge: squash merge
- Upstream history sync: merge commit may be used where provenance matters
- Branch prefixes: `feat/`, `fix/`, `refactor/`, `test/`, `docs/`, `chore/`
- Commit style: Conventional Commits where practical

Required `main` branch checks should only be enabled after the corresponding CI workflow has been imported and proven stable.

## Safety

Changing BMS protection, calibration, balancing, temperature, capacity or MOS parameters can affect battery safety.

This project therefore requires:

- explicit Maintenance Mode unlock
- conservative capability detection
- staged change review
- value validation
- read-back verification where supported
- physical BMS validation before declaring write features production-ready

A successful BLE write alone is not sufficient evidence that a setting is correct or persistent.

## Attribution

OpenJBD is MIT licensed. The initial upstream copyright notice must remain available after source import. This project's own top-level license is also MIT.

This is an unofficial community project and is not affiliated with or endorsed by JBD / Jiabaida.

## License

MIT License. See `LICENSE`.
