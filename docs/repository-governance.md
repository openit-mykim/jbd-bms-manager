# Repository Governance

## Branching

`main` is the integration branch. Development should normally happen on short-lived branches created from current `origin/main`.

Recommended prefixes:

- `feat/`
- `fix/`
- `refactor/`
- `test/`
- `docs/`
- `chore/`

Do not use a permanent `develop` branch unless the project later has a concrete release-management need for one.

## Pull requests

Prefer small PRs with one main concern. A PR should state:

- what changed;
- why it changed;
- files/subsystems affected;
- verification performed;
- hardware verification status when relevant;
- follow-up work.

Architecture-changing PRs must update documentation in the same change.

## Recommended `main` protection

Configure GitHub branch protection/ruleset for `main` with these goals after the baseline source and CI workflow are present:

- require pull request before merge;
- require at least one approval when another reviewer is available;
- dismiss stale approvals after material changes where practical;
- require conversation resolution;
- require CI checks for unit tests and debug build;
- block force pushes;
- block branch deletion;
- allow repository owner/admin emergency override only when necessary;
- do not require linear history if upstream merge commits are intentionally preserved.

Do not enable a required check before the corresponding workflow exists and is stable, otherwise automation can deadlock the repository.

## Merge strategy

Preferred default for normal feature work: **squash merge**.

Exception: provenance-preserving upstream integration may use a merge commit when retaining upstream history is useful.

## CODEOWNERS

Repository owner is the default code owner. High-risk protocol/maintenance changes should receive deliberate owner review even if automated checks pass.

## Issues

Use issues for:

- reproducible bugs;
- roadmap-sized features;
- protocol compatibility investigations;
- hardware compatibility records;
- release blockers.

Do not create an issue for every tiny implementation step that is already contained in a single PR task.

## CI expectations

Once Android source is imported, PR CI should minimally run:

```text
unit tests
+ debug APK build
```

Add lint/static checks only after their baseline is understood. Do not turn a large pre-existing lint backlog into an immediate merge blocker without first establishing a baseline.

## Releases

Use semantic versioning intent:

- `0.x` while architecture and device support are still evolving;
- `1.0` only after Monitor Mode and supported Maintenance Mode features have verified hardware coverage and a documented release process.

Tag format:

```text
v0.1.0
v0.2.0
...
```

Each release should include:

- summary;
- notable fixes/features;
- Android compatibility notes;
- tested BMS/device matrix changes;
- known limitations;
- artifact provenance.

## Secrets

Never commit:

- signing keystores;
- keystore passwords;
- personal access tokens;
- GitHub tokens;
- private device credentials.

Use GitHub Actions secrets or local secure storage for release signing when that phase begins.

## Dependency updates

Dependency updates should be separate from feature work where possible. Verify Gradle, Android Gradle Plugin, Kotlin and Material/AndroidX changes with a clean build and regression checks before merge.
