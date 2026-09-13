# Upstream Sync and Provenance

Upstream project:

- Repository: `https://github.com/gytxtx/OpenJBD`
- Initial baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- License: MIT
- Upstream copyright: `Copyright (c) 2026 KFACBT`

This repository was created independently rather than with GitHub's native Fork button. Treat OpenJBD as the source upstream in local Git configuration.

## Local remote setup

After cloning this repository:

```bash
git remote add upstream https://github.com/gytxtx/OpenJBD.git
git fetch upstream
```

Confirm:

```bash
git remote -v
```

Expected conceptual remotes:

```text
origin   -> openit-mykim/jbd-bms-manager
upstream -> gytxtx/OpenJBD
```

## Initial source import

The preferred initial import is a provenance-preserving merge of upstream history rather than manually copying only files.

Because the repositories began independently, the first merge may require unrelated-history handling. Perform the import in a dedicated worktree/branch, preserve this repository's project-control documents, resolve conflicts deliberately, build the result, then merge through a reviewed PR.

Do not perform broad package or architecture refactors in the same PR as the upstream baseline import.

## Files owned by this project

These should survive upstream imports unless intentionally replaced:

- `AGENTS.md`
- `HERMES.md`
- `PROJECT_STATUS.md`
- this repository's `README.md`
- `docs/`
- `.github/` project templates/policies
- local orchestration/config files created for Hermes/Paseo
- project-specific scripts

## Attribution

Imported upstream source remains covered by the upstream MIT notice. Keep a copy of the upstream license under a third-party notice/license location after import.

The top-level `LICENSE` describes this derivative project's own MIT licensing. Do not delete upstream copyright notices from substantial imported portions.

## Ongoing upstream sync

Do not auto-merge upstream into `main`.

Use a dedicated branch such as:

```text
chore/sync-openjbd-YYYYMMDD
```

Process:

1. fetch `upstream`;
2. inspect upstream changes since the last recorded sync;
3. identify conflicts with JBD BMS Manager architecture;
4. merge/cherry-pick in a dedicated worktree;
5. run tests/build;
6. review UI/protocol changes carefully;
7. update the recorded upstream sync point;
8. merge by PR.

## What not to blindly sync

Once this project diverges, upstream UI/navigation, package identity, system-bar behavior, protocol abstractions and app branding may conflict with local architecture. Prefer semantic review over automatic overwrite.

## Sync record

Maintain a short table here after each upstream sync:

| Date | Upstream commit | Local PR/commit | Notes |
|---|---|---|---|
| Initial | `7e3e225a128f6e0d69425b98a2670d8d69594885` | pending | Initial baseline |
