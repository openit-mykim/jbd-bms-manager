# JBD BMS Manager

[한국어 README](README.ko.md)

Unofficial open-source Android manager for JBD / Jiabaida smart BMS devices.

`JBD BMS Manager` is an extended derivative project based on the OpenJBD monitoring foundation. Its core product decision is to separate everyday battery monitoring from technician-oriented maintenance operations.

> **Current alpha:** `v0.1.0-alpha.1` adds Android 16 system-bar inset handling, Korean UI resources, and product branding on top of the pinned OpenJBD baseline.

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

This repository was created independently rather than with GitHub's native Fork button. Local clones should treat OpenJBD as the `upstream` remote.

For reproducible alpha builds, GitHub Actions checks out the pinned OpenJBD commit, applies this repository's product overlay, runs unit tests, builds the debug APK, and publishes the APK as a prerelease artifact.

See `docs/upstream-sync.md`, `scripts/bootstrap-upstream.sh`, and `scripts/apply-product-overlay.py`.

## Current project state

The repository is now in an **early executable alpha** state for Monitor Mode. The first distributable package focuses on:

1. OpenJBD monitor baseline.
2. Android 16 edge-to-edge / system-bar overlap correction.
3. Korean UI localization.
4. JBD BMS Manager product identity.
5. Reproducible APK CI/release packaging.

The next major product work is Maintenance Mode and verified calibration/configuration support.

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
  --new-branch fix/android16-insets \
  --base origin/main \
  "Read AGENTS.md and PROJECT_STATUS.md, implement the active scoped task, run focused verification, and report changed files plus test evidence."
```

Use `origin/main`, not an unqualified local `main`, as the normal Paseo worktree base.

## Documentation map

- `AGENTS.md` — authoritative coding-agent execution contract
- `HERMES.md` — Hermes project entry point
- `PROJECT_STATUS.md` — current phase and immediate next action
- `README.ko.md` — Korean project README
- `docs/development-plan.md` — detailed phase-by-phase implementation plan
- `docs/architecture.md` — application/layer architecture
- `docs/maintenance-mode.md` — service-mode UX and write transaction design
- `docs/hermes-paseo.md` — orchestration and worktree workflow
- `docs/testing.md` — test and physical-device validation policy
- `docs/upstream-sync.md` — OpenJBD provenance and sync procedure
- `docs/repository-governance.md` — branching, PR, CI and release policy
- `docs/roadmap.md` — concise feature roadmap

## Bootstrap

For full source-history integration in a local development checkout, the coordinator can stage the initial OpenJBD import with:

```bash
bash scripts/bootstrap-upstream.sh
```

For reproducible overlay builds without importing the full source history first, CI uses:

```bash
python3 scripts/apply-product-overlay.py <OpenJBD checkout>
```

Normal verification after source import:

```bash
bash scripts/verify.sh
```

## Android 16 compatibility

OpenJBD targets SDK 36. On Android 16, the top toolbar and bottom navigation can overlap the status/navigation gesture areas when an app relies on legacy edge-to-edge opt-out behavior.

JBD BMS Manager handles system insets explicitly and removes reliance on `windowOptOutEdgeToEdgeEnforcement` in the alpha build overlay.

## Repository governance

- Integration branch: `main`
- Normal work: short-lived branches + PR
- Preferred normal merge: squash merge
- Upstream history sync: merge commit may be used where provenance matters
- Branch prefixes: `feat/`, `fix/`, `refactor/`, `test/`, `docs/`, `chore/`
- Commit style: Conventional Commits where practical

Required `main` branch checks should only be enabled after the corresponding CI workflow has proven stable.

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

OpenJBD is MIT licensed. The original upstream copyright notice is preserved under `THIRD_PARTY_LICENSES/OpenJBD-LICENSE`.

This project's own top-level license is also MIT.

This is an unofficial community project and is not affiliated with or endorsed by JBD / Jiabaida.

## License

MIT License. See `LICENSE`.
