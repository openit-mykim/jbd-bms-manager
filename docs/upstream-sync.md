# Upstream Sync and Provenance

Upstream project:

- Repository: `https://github.com/gytxtx/OpenJBD`
- Pinned baseline commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- License: MIT
- Upstream copyright: `Copyright (c) 2026 KFACBT`

This repository was created independently rather than with GitHub's native Fork button. Treat OpenJBD as the source upstream in local Git configuration.

## Current model: pinned baseline + overlay

This repository does not contain the OpenJBD source tree or its Git history. It is overlay-only:

- The upstream commit is pinned as `UPSTREAM_COMMIT` (`.github/workflows/android-release.yml` and `scripts/apply-product-overlay.py`).
- CI (and `scripts/verify.sh` locally) checks out OpenJBD at that commit, then `scripts/apply-product-overlay.py` copies `overlays/` on top and applies a few targeted text replacements.
- New product files (Detail/Balance/Control screens, `settings/`, `maintenance/`, `history/`, `protocol/` etc.) are added by the overlay.
- Several upstream files are **replaced wholesale** by overlay copies, for example `BmsConnectionManager.kt`, `MainActivity.kt`, `OverviewFragment.kt`, `data/BmsRepository.kt`, `SystemBars.kt`, `Ext.kt`, `OpenJbdApplication.kt` and `protocol/JbdFrameAssembler.kt` (byte-by-byte resynchronization, #30) (all under `app/src/main/kotlin/com/gytxtx/openjbd/`), plus some resources. Upstream changes to these files are **not** picked up automatically when the pin moves.

To list which overlay paths replace an upstream file, compare `overlays/` against the upstream tree at the pinned commit, e.g. `git ls-tree -r --name-only <UPSTREAM_COMMIT>` in an OpenJBD clone.

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

## Future option: history merge (#1)

Not done yet. Issue #1 tracks replacing the overlay model with a provenance-preserving import. The preferred import is a provenance-preserving merge of upstream history rather than manually copying only files.

Because the repositories began independently, the first merge may require unrelated-history handling. Perform the import in a dedicated worktree/branch, preserve this repository's project-control documents, resolve conflicts deliberately, build the result, then merge through a reviewed PR.

Do not perform broad package or architecture refactors in the same PR as the upstream baseline import.

Warning: `scripts/bootstrap-upstream.sh` merges with `-X theirs`, which auto-resolves overlapping content in favor of upstream. Files such as `README.md` and `LICENSE` can silently become upstream's versions without showing up as conflicts, so the script's `--ours` restore loop does not protect them. Review and fix the script (and inspect the staged result) before using it.

## Files owned by this project

These should survive upstream imports unless intentionally replaced:

- `AGENTS.md`
- `HERMES.md`
- `PROJECT_STATUS.md`
- this repository's `README.md`
- `docs/`
- `.github/` project templates/policies
- local orchestration/config files created for Hermes/Claude Code
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

Process (overlay model — bumping `UPSTREAM_COMMIT`):

1. fetch `upstream`;
2. inspect upstream changes since the last recorded sync (`git log <old>..<new>`);
3. for every file the overlay replaces wholesale, diff the upstream changes (`git diff <old>..<new> -- <path>`) and port relevant fixes into the overlay copy manually;
4. check that the text replacements in `scripts/apply-product-overlay.py` still match (required replacements fail loudly);
5. identify conflicts with JBD BMS Manager architecture;
6. in a dedicated worktree, update `UPSTREAM_COMMIT` in the workflow, `scripts/apply-product-overlay.py` and any other pin reference (`grep -rn <old>`);
7. run `bash scripts/verify.sh`;
8. review UI/protocol changes carefully;
9. update the sync record below;
10. merge by PR.

## What not to blindly sync

Once this project diverges, upstream UI/navigation, package identity, system-bar behavior, protocol abstractions and app branding may conflict with local architecture. Prefer semantic review over automatic overwrite.

## Sync record

Maintain a short table here after each upstream sync:

| Date | Upstream commit | Local PR/commit | Notes |
|---|---|---|---|
| Initial | `7e3e225a128f6e0d69425b98a2670d8d69594885` | overlay pin (CI) | Initial baseline; history merge pending (#1) |
