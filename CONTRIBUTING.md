# Contributing

JBD BMS Manager is developed with a strong preference for small, reviewable changes and explicit verification.

Before contributing, read:

- `AGENTS.md`
- `PROJECT_STATUS.md`
- `docs/development-plan.md`
- `docs/testing.md`
- `docs/repository-governance.md`

## Workflow

1. Start from current `origin/main`.
2. Create a focused branch.
3. Keep the change limited to one primary concern.
4. Run focused verification during development.
5. Run `bash scripts/verify.sh` before merge when applicable.
6. Open a PR using the repository template.
7. Update documentation if architecture, workflow or supported behavior changed.

## Branch names

Use:

- `feat/...`
- `fix/...`
- `refactor/...`
- `test/...`
- `docs/...`
- `chore/...`

## Commit messages

Conventional Commits are preferred, for example:

```text
fix(ui): handle Android 16 system bar insets
feat(monitor): add cell spread summary
chore(upstream): sync OpenJBD baseline
```

## Device-dependent work

If a change depends on a physical BMS, state the tested model/firmware and Android environment. If no hardware was available, mark the PR as `NOT HARDWARE VERIFIED` rather than implying verification.

## Upstream

OpenJBD is the source upstream. See `docs/upstream-sync.md` before syncing upstream history or replacing imported files.

## Scope discipline

Do not combine unrelated refactors, dependency upgrades and feature work into one PR unless the dependency is unavoidable for the feature.
