# Hermes Project Entry Point

Repository: `openit-mykim/jbd-bms-manager`

This repository is prepared so a Hermes Agent can resume work from the repository URL alone.

## Start here

Read in this order before editing:

1. `AGENTS.md`
2. `PROJECT_STATUS.md`
3. `docs/development-plan.md`
4. `docs/hermes-claude.md`
5. `docs/architecture.md`
6. `docs/protocol-design.md`
7. `docs/maintenance-mode.md`
8. `docs/testing.md`
9. `docs/device-validation.md`
10. `docs/upstream-sync.md`
11. `docs/repository-governance.md`
12. `docs/github-setup.md`
13. `docs/decision-log.md`

`AGENTS.md` is authoritative. `PROJECT_STATUS.md` tells you the active phase and immediate next action.

## Agent role

Hermes is the coordinator. Use isolated Git worktrees for non-trivial implementation, review, tests, documentation and investigation when work can be separated safely.

Hermes should:

- inspect the repository before planning changes;
- continue the active phase rather than jumping ahead;
- keep tasks narrow enough for independent worktrees;
- require verification evidence from each delegated task;
- review diffs before merge;
- keep architecture/status documentation synchronized;
- prefer small, reviewable pull requests over large mixed changes.

## Current objective

The app is released as `v0.1.1` (all five tabs and every Control maintenance
section implemented, 311 unit tests). `PROJECT_STATUS.md` → "Immediate next
action" is authoritative; at the time of writing it is physical BMS validation
of the released build per `docs/device-validation.md`.

Upstream reference (the build materializes this pinned baseline plus the
`overlays/` tree; full source-history import remains open as Phase 0, issue #1):

- repository: `gytxtx/OpenJBD`
- baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- license: MIT

## Work completion rule

A task is complete only when:

- the requested change is implemented;
- relevant tests/build checks have been run;
- failures are explained rather than hidden;
- the diff has been reviewed by the coordinating agent;
- hardware validation status is explicit when BMS behavior matters;
- documentation is updated when behavior or architecture changed;
- the next task remains clear from repository state.

Do not treat a generated patch as completed work without verification.
