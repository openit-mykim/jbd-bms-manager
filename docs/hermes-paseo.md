# Hermes + Paseo Development Workflow

This project is intended for long-running, agent-assisted development with Hermes as coordinator and Paseo as the worktree/task execution layer.

## Operating model

Hermes owns planning, decomposition, task assignment, review and handoff. Paseo provides isolated workspaces so implementation tasks can proceed without contaminating the main checkout.

Use one worktree per non-trivial task. Keep `main` clean and use it only as the integration branch.

Recommended task flow:

```text
Hermes reads repository context
        ↓
Hermes selects the next roadmap item
        ↓
Hermes decomposes work into narrow tasks
        ↓
Paseo creates isolated worktree(s)
        ↓
Agent implements + verifies
        ↓
Hermes reviews diff + evidence
        ↓
PR / merge
        ↓
Update docs / handoff state
```

## Worktree naming

Suggested branch names:

- `fix/android16-insets`
- `feat/monitor-cells-view`
- `feat/korean-localization`
- `feat/maintenance-shell`
- `test/protocol-regression`
- `docs/upstream-notes`

Avoid vague names such as `agent-work`, `temp`, `new-feature` or `fixes`.

## Paseo execution pattern

When using Paseo, create a fresh branch from the remote integration branch rather than a possibly stale local checkout.

Example pattern:

```bash
paseo run \
  --new-workspace worktree \
  --worktree-mode branch-off \
  --new-branch fix/android16-insets \
  --base origin/main \
  "Fix Android 16 system-bar overlap. Read AGENTS.md and the relevant docs first, keep the change scoped, run focused verification, and report changed files plus test evidence."
```

Exact Paseo flags may change by installed version. If the local CLI differs, preserve the workflow intent: isolated worktree, explicit branch, explicit base, narrow task, explicit verification.

## Parallel work

Parallel agents are useful only when file overlap is low.

Good parallelization examples:

- UI fix + independent documentation update
- protocol parser test expansion + localization
- release workflow review + architecture documentation

Poor parallelization examples:

- two agents editing the same activity/layout pair
- two agents changing the same protocol encoder
- simultaneous package renames and feature work

When overlap is unavoidable, serialize work or define merge order before starting.

## Task prompt template

A Hermes delegated task should include:

```text
Goal:
Scope:
Files/subsystem likely involved:
Read first:
Constraints:
Verification required:
Completion evidence:
Do not modify:
```

Example:

```text
Goal: Correct Android 16 top/bottom system-bar overlap.
Scope: MainActivity/SystemBars/main layout only unless evidence requires more.
Read first: AGENTS.md, docs/architecture.md, docs/development-plan.md.
Constraints: No Monitor Mode redesign in this task.
Verification: unit tests + debug build + layout review notes.
Completion evidence: changed files, commands run, results, remaining device-test needs.
Do not modify: protocol, BLE connection logic, maintenance features.
```

## Review contract

Hermes should not accept a sub-agent's statement that work is complete without checking:

- actual diff
- changed file scope
- test/build output
- any skipped verification
- whether architecture/docs became stale
- whether unrelated formatting churn was introduced

## Long-running task policy

Long tasks are allowed. Do not impose an arbitrary short timeout on builds or static analysis that are known to take time.

However, if the same command appears stalled or repeatedly fails, investigate instead of blindly rerunning it. Prefer focused verification and subsystem tests during development; reserve broader verification for integration points.

## Repository handoff

A future Hermes Agent should be able to receive only:

`https://github.com/openit-mykim/jbd-bms-manager`

and continue by reading `AGENTS.md` and this document before touching code.
