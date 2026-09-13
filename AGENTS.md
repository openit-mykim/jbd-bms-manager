# Agent Execution Contract

This repository is intended to be developed and maintained primarily through **Hermes Agent + Paseo worktree orchestration**.

If you are an AI coding agent, read this file first, then read:

1. `PROJECT_STATUS.md`
2. `docs/development-plan.md`
3. `docs/architecture.md`
4. `docs/maintenance-mode.md`
5. `docs/testing.md`
6. `docs/upstream-sync.md`
7. `docs/hermes-paseo.md`

Do not begin implementation from README alone.

## Project objective

Build **JBD BMS Manager**, an unofficial Android application for JBD / Jiabaida smart BMS devices. It extends the OpenJBD monitoring foundation into two clearly separated operating modes:

- **Monitor Mode**: safe, read-oriented everyday monitoring.
- **Maintenance Mode**: technician-oriented calibration, configuration, diagnostics, backup and controlled BMS writes.

The application must remain local-first over BLE and must not require a cloud account for core operation.

## Current execution rule

Always inspect `PROJECT_STATUS.md` before starting work. Continue the first incomplete phase. Do not skip directly to later features because they appear more interesting.

The expected initial sequence is:

1. Import OpenJBD upstream baseline and preserve provenance.
2. Confirm clean debug build and tests.
3. Fix Android 16 edge-to-edge / system-bar overlap.
4. Stabilize Monitor Mode.
5. Add Korean localization.
6. Build Maintenance Mode framework.
7. Add calibration and writable configuration incrementally.
8. Add backup / restore and diagnostics.
9. Harden tests and release process.

## Paseo worktree policy

Use a **separate Paseo worktree for every non-trivial task**. Branch from `origin/main`, not a possibly stale local `main`.

Preferred pattern:

```bash
paseo run \
  --new-workspace worktree \
  --worktree-mode branch-off \
  --new-branch feat/<scope>-<short-desc> \
  --base origin/main \
  "<task>"
```

Use these branch prefixes:

- `feat/` new functionality
- `fix/` bug fix
- `refactor/` structural change without behavior change
- `test/` test-only work
- `docs/` documentation
- `chore/` repository, dependency or build maintenance

Do not allow two agents to edit the same files in different worktrees unless the parent agent has explicitly planned the merge order.

## Agent delegation

Hermes is the coordinating agent. It may use Paseo to delegate independent work such as:

- protocol research
- UI implementation
- tests
- static analysis
- documentation
- regression investigation

Delegated agents must receive a narrow task, explicit files or subsystem scope, expected verification command and completion criteria.

The coordinating agent must review the diff and test evidence before merge.

## Development discipline

### Before editing

- Fetch latest remote state.
- Read the relevant architecture and phase documents.
- Inspect existing implementation before proposing replacement architecture.
- For JBD write commands, verify protocol behavior from a reliable source or actual device observation. Never infer a writable register only from nearby register numbers.

### During editing

- Prefer the smallest change that satisfies the active phase.
- Preserve read-only Monitor Mode boundaries.
- Keep transport, protocol, domain validation and UI concerns separated.
- Do not bind editable UI controls directly to BLE writes.
- All maintenance writes must follow: **Read → Edit → Validate → Review → Apply → Read-back verify**.
- Unknown BMS variants default to read-only behavior.

### After editing

Run verification appropriate to the change. Prefer focused tests first.

Do not repeatedly launch the same full Gradle verification after a long-running failure. Investigate the cause before rerunning.

For ordinary changes:

```bash
bash scripts/verify.sh
```

For narrowly scoped work, run the smallest relevant Gradle test first, then the repository verification script before PR merge when practical.

## Test policy

- Protocol encoder/decoder changes require unit tests.
- Writable register changes require encode/decode and read-back verification tests where possible.
- Calibration logic requires validation tests for bounds and unit conversion.
- UI changes involving dangerous actions require confirmation-flow tests.
- Android system-bar changes must be checked in portrait and landscape and on gesture-navigation layouts.
- Hardware-dependent tests must be clearly marked; lack of attached hardware is not permission to fake success.

See `docs/testing.md`.

## Safety boundaries

This project can change battery-management protection and calibration values. Incorrect writes can create unsafe battery behavior.

Therefore:

- Do not weaken protection defaults merely to make a test pass.
- Do not silently coerce unsafe values into writes.
- Do not expose raw write operations in Monitor Mode.
- Do not perform automatic calibration without an explicit service workflow.
- Show old and new values before applying configuration changes.
- Verify successful writes by reading values back whenever supported.
- Keep an exportable configuration backup before bulk changes.

## Commit and PR rules

Use Conventional Commits where practical:

```text
feat(scope): ...
fix(scope): ...
refactor(scope): ...
test(scope): ...
docs(scope): ...
chore(scope): ...
```

A PR body should include:

- Summary
- Why
- Scope
- Testing / verification evidence
- Safety impact, when applicable
- Follow-up work

Do not merge a PR with unexplained failing checks.

## Upstream rules

OpenJBD upstream:

- Repository: `https://github.com/gytxtx/OpenJBD`
- Initial baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- License: MIT

This repository was created independently rather than through GitHub's native Fork button. Treat `gytxtx/OpenJBD` as the `upstream` Git remote and preserve provenance according to `docs/upstream-sync.md`.

Do not remove upstream copyright notices from imported source.

## Source of truth priority

If documents disagree, use this priority:

1. `AGENTS.md`
2. `PROJECT_STATUS.md`
3. `docs/architecture.md`
4. active phase in `docs/development-plan.md`
5. subsystem-specific docs
6. README

When architecture changes materially, update the documentation in the same PR.
