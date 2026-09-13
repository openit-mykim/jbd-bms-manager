# GitHub Repository Settings

Repository: `openit-mykim/jbd-bms-manager`

This document records the intended GitHub-side configuration. Some settings should be enabled only after the Android source and CI workflow exist, to avoid blocking all merges.

## Current repository facts

- Visibility: Public
- Default branch: `main`
- Owner: `openit-mykim`
- License: MIT
- CODEOWNERS: `.github/CODEOWNERS`
- Pull request template: `.github/PULL_REQUEST_TEMPLATE.md`
- Issue templates: `.github/ISSUE_TEMPLATE/`

## Recommended General settings

Repository description:

`Unofficial open-source Android manager for JBD / Jiabaida BMS — Monitor and Maintenance modes.`

Suggested topics:

- `android`
- `kotlin`
- `jbd`
- `jiabaida`
- `bms`
- `bluetooth-le`
- `battery-monitor`
- `battery-management`

Recommended merge options:

- Squash merge: ON
- Merge commits: ON, because upstream-history integration may need them
- Rebase merge: optional
- Automatically delete head branches: ON
- Auto-merge: optional after CI is stable

## Main branch protection / ruleset

Enable after Phase 0 has imported the build workflow and the required checks have run successfully at least once.

Recommended protections:

- Target branch: `main`
- Require pull request before merging
- Require conversation resolution
- Require status checks
- Required check: Android unit tests
- Required check: debug APK build
- Block force pushes
- Block branch deletion
- Require CODEOWNER review when another reviewer is available
- Do not require linear history because upstream merge commits may be intentional

Avoid turning on a required check whose workflow name does not yet exist.

## Actions

After upstream import, preserve or adapt the OpenJBD Android debug CI as the starting workflow.

Minimum PR workflow:

```text
checkout
→ JDK setup
→ ./gradlew testDebugUnitTest
→ ./gradlew assembleDebug
→ upload debug APK artifact
```

Do not put release signing material directly in workflow YAML.

## Security

Recommended:

- Secret scanning: ON where available
- Push protection: ON where available
- Dependabot alerts: ON
- Dependency graph: ON
- Private vulnerability reporting: optional for a public community project

Never commit signing keys, API tokens or credentials.

## Issues

The roadmap is represented as phase issues. Agents should work from the earliest incomplete phase rather than selecting later issues arbitrarily.

## GitHub native Fork status

This repository was created independently, so GitHub does not show a native fork relationship to `gytxtx/OpenJBD`.

That is acceptable for this project. Development clones should configure:

```text
origin   = openit-mykim/jbd-bms-manager
upstream = gytxtx/OpenJBD
```

The repository preserves provenance through the recorded baseline commit, upstream remote procedure and third-party attribution.

If a native GitHub fork relationship is later considered important, evaluate migration separately rather than replacing this repository casually.
