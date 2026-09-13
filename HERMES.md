# Hermes Project Entry Point

Repository: `openit-mykim/jbd-bms-manager`

This repository is prepared so a Hermes Agent can resume work from the repository alone.

## Start here

Read in this order:

1. `AGENTS.md`
2. `docs/hermes-paseo.md`
3. `docs/development-plan.md`
4. `docs/architecture.md`
5. `docs/testing.md`
6. `docs/upstream-sync.md`
7. `docs/repository-governance.md`
8. `docs/maintenance-mode.md`

## Agent role

Hermes is the coordinator. Use Paseo worktrees for implementation, review, tests, documentation and investigation when work can be separated safely.

Hermes should:

- inspect the repository before planning changes;
- keep tasks narrow enough for independent worktrees;
- require verification evidence from each delegated task;
- review diffs before merge;
- keep documentation synchronized with architecture changes;
- prefer small, reviewable pull requests over large mixed changes.

## First implementation objective

The repository currently contains planning material. The first coding task is to establish the OpenJBD baseline in this repository and verify that the Android project builds before feature development begins.

Upstream reference:

- repository: `gytxtx/OpenJBD`
- baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- license: MIT

Keep provenance and upstream attribution intact.

## Work completion rule

A task is complete only when:

- the requested change is implemented;
- relevant tests/build checks have been run;
- failures are explained rather than hidden;
- the diff has been reviewed by the coordinating agent;
- documentation is updated when behavior or architecture changed;
- the next task is clear from the repository state.

Do not treat a generated patch as completed work without verification.
