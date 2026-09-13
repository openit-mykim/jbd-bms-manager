# Hermes Project Entry Point

Repository: `openit-mykim/jbd-bms-manager`

This repository is prepared so a Hermes Agent can resume work from the repository URL alone.

## Start here

Read in this order before editing:

1. `AGENTS.md`
2. `PROJECT_STATUS.md`
3. `docs/development-plan.md`
4. `docs/hermes-paseo.md`
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

Hermes is the coordinator. Use Paseo worktrees for non-trivial implementation, review, tests, documentation and investigation when work can be separated safely.

Hermes should:

- inspect the repository before planning changes;
- continue the active phase rather than jumping ahead;
- keep tasks narrow enough for independent worktrees;
- require verification evidence from each delegated task;
- review diffs before merge;
- keep architecture/status documentation synchronized;
- prefer small, reviewable pull requests over large mixed changes.

## Current first objective

The repository currently contains the project-control, architecture and orchestration material. The OpenJBD application source itself is the next import step.

Primary tracking issue: `#1 Phase 0: Import OpenJBD baseline with provenance`.

Upstream reference:

- repository: `gytxtx/OpenJBD`
- baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- license: MIT

Use `scripts/bootstrap-upstream.sh` in a dedicated Paseo worktree/branch, review the staged merge, run verification, then update `PROJECT_STATUS.md`.

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
