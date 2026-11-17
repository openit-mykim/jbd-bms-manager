# Decision Log

This file records product and engineering decisions that should not be re-litigated by future agents unless new evidence justifies a change.

## D001 — Repository and product name

Decision: use `jbd-bms-manager` as repository name and `JBD BMS Manager` as app display name.

Reason: the project covers monitoring and maintenance, not only Bluetooth transport. Avoid `BT` in the product name so future transports are not constrained by branding.

## D002 — Upstream relationship

Decision: use OpenJBD as the initial code foundation, but keep this repository as an independent product repository rather than a GitHub-native fork.

Reason: the project has a different product scope, governance and roadmap. Preserve upstream history and attribution through Git remotes and provenance-aware merges.

Initial upstream baseline: `gytxtx/OpenJBD@7e3e225a128f6e0d69425b98a2670d8d69594885`.

## D003 — Two operating domains

Decision: separate read-oriented monitoring from technician-oriented maintenance.

- Read-oriented monitoring is presented through Overview, Detail and Balance.
- Maintenance Mode lives under Control.

Reason: calibration and BMS configuration must not be mixed into ordinary monitoring UX.

## D004 — Maintenance write transaction

Decision: all writable maintenance operations use:

`Read → Edit → Validate → Review → Apply → Read-back verify`

Reason: direct UI-to-register writes are too easy to trigger accidentally and do not provide adequate verification.

## D005 — Unknown devices default to read-only

Decision: writable features require positive capability evidence for the connected BMS/firmware variant.

Reason: JBD-compatible hardware and firmware vary. Register adjacency or partial protocol similarity is not enough evidence for safe write support.

## D006 — Local-first BLE operation

Decision: core operation requires no cloud account and no remote backend.

Reason: the primary use case is direct battery monitoring and service over local BLE. Cloud dependency adds complexity without being necessary for core value.

## D007 — Android 16 insets are first product-code fix

Decision: stabilize system-bar behavior before broader UI feature work.

Reason: the observed OpenJBD baseline can render toolbars and bottom navigation under Android 16 system bars. A stable base UI is required before productization.

## D008 — Hermes coordinates, Paseo isolates work

Decision: Hermes is the coordinating agent; Paseo worktrees are used for non-trivial implementation and review tasks.

Reason: isolated worktrees reduce file collisions and allow implementation/review/test agents to operate independently while keeping `main` clean.

## D009 — Remote base for Paseo worktrees

Decision: branch from `origin/main`, not unqualified local `main`.

Reason: a local `main` may be stale. Paseo documentation recommends an explicit remote-tracking base for current worktree creation.

## D010 — Physical validation required for write support claims

Decision: protocol documentation and unit tests are necessary but not sufficient for production-ready write functionality.

Reason: real BMS behavior, persistence, firmware variation and scaling must be confirmed on supported hardware.

## D011 — Preserve MIT attribution

Decision: keep this project's MIT license and preserve OpenJBD's original MIT notice separately after import.

Reason: imported OpenJBD source remains subject to its upstream copyright/license notice.

## D012 — Five-tab bottom navigation

Decision: use exactly five top-level destinations:

```text
개요 / 상세 / 밸런스 / 제어 / 설정
Overview / Detail / Balance / Control / Settings
```

Responsibilities:

- Overview: compact battery-state summary.
- Detail: detailed read-only operation/device information.
- Balance: cell-voltage and balancing observation/diagnosis.
- Control: locked Maintenance Mode and supported service operations.
- Settings: application preferences only.

Reason: this maps the UI to user intent instead of exposing the upstream `Parameters` structure directly. Five items fit the intended bottom-navigation maximum while leaving room for monitoring, cell diagnostics and maintenance as first-class concepts.

Constraint: do not add a sixth top-level tab. Secondary functions belong under one of these five destinations.

## D013 — Balance vs Control boundary

Decision: Balance is read-oriented; balance configuration belongs under Control.

Rule:

```text
밸런스 = 상태 확인과 진단
제어 = 유지보수 작업
```

Reason: users should be able to inspect individual cells without being placed next to writable BMS settings.

## D014 — Settings is app-only

Decision: Settings contains application preferences and app information only. Maintenance Mode must not use Settings as its primary navigation entry.

Reason: application preferences and BMS service functions are different domains. The Control tab makes the safety boundary visible and understandable.

## D015 — Multi-BMS registry and single device-management entry point

Decision: support multiple registered BMS devices with user aliases while maintaining exactly one explicit active/selected BMS at a time.

UI rules:

- the left app-bar device-list button is the only entry point for BMS discovery, registration, selection and reconnection.
- the top-right app-bar text shows the currently connected BMS name or `BMS 미연결`; it is status-only and is not clickable.
- when there is no registered/saved BMS, the left button opens BLE device discovery immediately.
- when one or more BMS devices are registered, the left button shows the registered-device list first.
- the registered-device screen contains a bottom `+ 새 BMS 검색` action for discovering another device.
- selecting a registered BMS disconnects/cancels reconnect for the previous device and immediately connects the selected device.
- startup auto-connect targets only the last explicitly selected BMS.
- green means connected or currently seen/reachable, gray means registered but not currently seen, and red is reserved for explicit connection failure/error.

Identity rule: store MAC address but do not treat it as the only permanent identity. Add a stable device fingerprint using BMS serial/model/firmware identity when available.

Safety reason: device management should not have duplicated controls, and monitoring data, backups, calibration records and Maintenance Mode writes must all be scoped to the explicitly selected physical BMS.

See `docs/multi-bms-design.md`.

## D016 — Settings session hardening policy

Decision: settings read/write sessions follow this hardening policy:

- bounded retries on loss of response only (reads: up to 2 extra attempts; register writes / factory entry / no-commit exit: 1 extra attempt; the `28 28` commit exit is never retried);
- error status (`0x80`/`0x81`) and malformed responses are definitive and never retried;
- a committed session is followed by an independent post-commit confirmation read (fresh factory session) of all changed registers;
- bitfield writes are read-modify-write, verified by full read-back (never a blindly composed mask).

Reason: cross-verified against `sshoecraft/jbdtool` (retry-on-verify-failure history, BatteryConfig silent-clear incident) and SmartBMSUtility (timeout retry, read/write mode tracking, variant rejections). Persistence semantics of `28 28` vs `00 00` differ across community sources and firmware variants, so persistence must be independently confirmed rather than assumed.

Evidence/details: `docs/protocol-design.md` (Factory/configuration mode, Defensive patterns).

Migration impact: none — the session coordinator API stays backward compatible; retry and confirmation behavior are additive.

## D017 — Balance diagnostics and history scope

Decision (master-approved scope for the visualization round):

- **Balance safety bands**: cell colors use an absolute 3-level band (normal / caution / danger) derived from the measured protection limits, or from an explicit NMC default labelled as such; highest/lowest/balancing move to text badges so one channel carries one meaning. 5-step or gradient color scales are rejected (they blur the boundary judgment that makes bands useful).
- **Balance delta steps**: 5 mV steps only (5 mV minimum, 10 mV recommended); a non-multiple entry is rejected explicitly rather than silently rounded.
- **Balance all-cell bar chart**: not built. Decided against after the bands and badges shipped — they already satisfy "identify the abnormal cell", and a chart would add rendering cost and a second color channel without new diagnostic value.
- **Overview SOC gauge**: three-level band (danger ≤ 10 %, caution ≤ 25 %), plus a text status; the XML default indicator color is neutral so a failed render path never shows a reassuring green.
- **Detail history**: sampling **10 s**, retention **7 days**. The recorder samples the existing `BmsRepository` snapshot — it never issues additional BLE reads — and does not interpolate across disconnection gaps.

Reason: field-usage review (`docs/ui-feature-proposal.md`, video evidence) showed the practical pain is recognizing an abnormal cell and knowing when a pack needs charging, not long-term trend analysis. Bands and badges address that directly; a history chart is secondary and therefore kept to a bounded, low-cost data layer.

Evidence/details: `docs/ui-feature-proposal.md` (§4–8), `docs/navigation-design.md` (Balance as a first-class diagnostic surface, Detail keeping historical charts as a future item).

Migration impact: none — display-only additions plus a new local `history.db` (created on first run; no existing data affected).

## D018 — JBD SP14S004 unused-cell wiring rule

Decision: the official JBD SP14S004 wiring rule takes priority over the generic absent-cell floor/hysteresis:

- remaining upper sense wires are redundantly connected to pack+;
- a channel whose adjacent sense-wire difference is `0 V` (tolerance ±50 mV) is an unused cell;
- the first such channel confirms that channel and every upper slot as unused, and the pack is recognized with a lower S count;
- this confirmation applies immediately (no 3-sample debounce), while the existing 0.5 V floor + hysteresis remains the fallback for non-zero absent readings and recovery.

Reason: manual-confirmed wiring behavior is stronger evidence than a generic voltage floor, and immediate confirmation avoids reporting ghost upper cells as real.

Evidence/details: JBD SP14S004 official wiring manual; `overlays/app/src/main/kotlin/com/gytxtx/openjbd/cells/CellVoltagePolicy.kt`.

Migration impact: none — display/statistics only; no BMS writes.

## D019 — Execution layer migrated from Paseo to Claude Code

Old decision: D008/D009 — Paseo worktrees for non-trivial implementation and review; branch from `origin/main`.

New decision: Hermes coordinates; Claude Code runs directly in isolated Git worktrees (`git fetch origin` → `git worktree add -b <branch> origin/main` → `claude`, Max subscription auth). Single canonical clone per repository. Branch naming and `origin/main` base rules unchanged.

Evidence/reason: owner directive 2026-11-16; in-progress branches preserved to origin (Travit 9/9 branches pushed with upstream; preservation commits for uncommitted work) before daemon removal.

Migration impact: `docs/hermes-paseo.md` → `docs/hermes-claude.md`; `paseo.json` deleted; AGENTS.md/HERMES.md/README/README.ko/docs/github-setup.md updated.

## D020 — Write gate: verified profile or device-bound experimental consent

Old decision: D005 — unknown devices default to read-only; writes require positive capability evidence. In v0.1.1 this was not enforced in code: the capability resolver was test-only and any BMS that accepted factory-mode entry was writable after the UI-only unlock.

New decision: every write session is authorized in `SettingsRepository.applyChanges` (not only in the UI) by `WriteAuthorization` / `WritePolicy`. A write is allowed only when Maintenance Mode is unlocked for the connected device **and** either the device matches a verified entry of the local compatibility table, or a technician has given explicit experimental consent for that exact device. Consent is bound to the fingerprint `address|bmsModel|firmwareVersion` (a firmware change voids it), persists until revoked, and is shown on the Control screen. Without a readable address and firmware version no write is possible. Denied sessions send no frame (`NotPermitted`). Every permitted session, consent grant and revocation is appended to a local audit log (`files/maintenance_audit.log`, bounded, never uploaded). The unlock is in memory only, survives screen rotation, and is bound to the device it was granted for.

Evidence/reason: 2026-11-16 code review (#29). Strict enforcement with the empty compatibility table would block every write, including the physical validation needed to add table entries (#23); option 2 keeps D005's read-only default while allowing evidence collection under explicit, audited consent.

Migration impact: users of v0.1.1 must allow experimental writes once per device on the Control screen before writing. The compatibility table still ships empty; entries are added only from recorded hardware evidence.

## D021 — BLE response matching fails safe; MOS edits from live FET state

Old decision: settings responses were matched to the active command by register address only; a late frame after a timeout could answer the retry or the next command (for example a read response taken as a write acknowledgement). The frame assembler skipped a whole declared length on a parse failure, frames of a previous connection could complete a command of the next one, a cancelled settings command could still be transmitted, and the MOS screen required a 0xE1 read before editing.

New decision (issue #30):

- After any command timeout the transport quarantines responses for 300 ms (partial buffer and already-assembled frames dropped, notifications discarded, no command started).
- Every write-type response (factory enter/exit, register write, calibration, MOS) must echo the address, carry status 0 **and an empty payload**. Any other shape is `MalformedResponse`: not retried, never success, never followed by a commit. The empty-payload acknowledgement is the documented community format and is **NOT HARDWARE VERIFIED** (#23); if hardware shows a different ack, writes fail safe until the check is adjusted with evidence.
- Frames carry a connection generation; a connect/disconnect or timeout invalidates frames assembled earlier, and notifications from a closed GATT are dropped.
- The assembler resynchronizes byte by byte on a parse failure.
- A settings command cancelled before its GATT write starts is never transmitted; a started write cannot be recalled.
- The MOS screen edits against the live basic-info FET state; 0xE1 is not read (the write is already built from live state and never committed).

Evidence/reason: 2026-11-16 review findings; conservative choice because the ack payload format is not yet recorded on target hardware.

Migration impact: none for users; settings safety rules (PROJECT_STATUS section 1) unchanged.

## D022 — Multi-BMS registry, device-scoped data and target identity check

Old decision: D015 defined the single device-management entry point, but the app persisted only the last connected device (OpenJBD `last_device_address/name`); history, backups and write permission were not tied to an explicitly selected BMS.

New decision (issue #24, `docs/multi-bms-design.md` Phase B–D):

- **Registry**: registered BMS devices persist locally (`bms_registry` preferences, versioned JSON). The registry key is the normalized BLE address; each entry has an alias (≤ 32 characters, rejected rather than truncated; blank falls back to the advertised name), the advertised name, registration / last-connection time and the learned identity (BMS model, firmware). Exactly one device may be selected; removing the selected device leaves nothing selected (no silent switch). Only the selected device is auto-connected at startup or auto-reconnected.
- **Identity**: the target identity is `address + BMS model`. The model is learned on first connection where it is read (trust on first use) and is never overwritten automatically; a later different model at the same address is a mismatch until the user removes and re-registers the device. Firmware is recorded but is not part of the target check, because firmware-bound write consent is already enforced by D020.
- **Write / restore gate**: `WritePolicy` gains `DenyReason.TARGET_MISMATCH`. `WriteAuthorization.authorize()` — the existing repository-layer gate every write session (settings, calibration, MOS, restore) passes — now also requires `DeviceTargetCheck` = `MATCH` (selected device present, connected address equal, model equal once learned, model read once learned). Any other result is fail-closed: no frame is sent. D020 (unlock + verified profile or consent) and the write sequence are unchanged.
- **Restore**: a backup can only be restored onto the BMS it was exported from (backup address = selected device, and backup model ≠ registered model blocks). Cross-device configuration cloning is intentionally not supported.
- **Device-scoped data**: history samples carry the device key (`history.db` v2, primary key `(device, ts)`, 70 000-sample cap per device). Every export also keeps a copy under `files/backups/<device>/` (20 per device). Calibration / service records are the D020 audit entries, already bound to address and fingerprint, and are shown per selected device. Removing a device deletes its history only; saved backups and the audit log are kept.
- **Reachability**: a 4-second scan with the JBD service filter runs only on explicit request in the BMS management sheet and stops when the sheet closes. Green = connected or seen in the latest scan (with RSSI), gray = not seen / connecting, red = explicit connection failure of that device (`INVALID_DEVICE`, `CONNECTION_FAILED`, service/characteristic/notification failures, `ERROR`, `PARSE_ERROR`). Out of range is never red. Scan results are memory-only.

Evidence/reason: safety principle of #24 — monitoring state, backups, calibration records and writes always belong to exactly one real BMS the user explicitly selected. The repository-layer gate already guarantees "no frame on deny", so adding the target check there covers every write path without touching BLE connection internals (#30).

Migration impact: on first start the legacy `last_device_address/name` becomes the first registered and selected BMS. `history.db` v1 → v2 assigns all existing samples to that device; without a legacy device, on any other version change, or if the migration fails, the history table is recreated (disposable cache, no crash). The legacy preference keys keep mirroring the selected device so an older APK installed over this one still reconnects. No BMS write behaviour changes for a single registered device.

## D023 — Provenance by pinned baseline and license notice; no upstream history merge

Old decision: Phase 0 / issue #1 planned to merge OpenJBD's Git history (`--allow-unrelated-histories`) and replace the overlay model with an imported source tree.

New decision: keep the pinned-baseline + overlay model and do not merge upstream history. Provenance is preserved by:

- the pinned upstream repository and commit in `version.properties` (`gytxtx/OpenJBD` @ `7e3e225`), used by every build;
- the upstream MIT license and copyright (`Copyright (c) 2026 KFACBT`) in `THIRD_PARTY_LICENSES/OpenJBD-LICENSE` and `THIRD_PARTY_NOTICES.md`;
- the in-app About entry linking to the original project;
- the `upstream` remote and the sync record in `docs/upstream-sync.md`.

`scripts/bootstrap-upstream.sh` (the unused history-merge helper, which also resolved conflicts with `-X theirs` and could silently replace project files) is removed; it remains in Git history.

Evidence/reason (2026-11-17): MIT requires the copyright and license notice, not Git history. Upstream has had no commits since 2026-08-08 (upstream/main = the pinned commit), so a history merge would add only `git blame` attribution while requiring a rewrite of the build model (overlay script, CI, design/terminology checks, `verify.sh`, docs). Revisit only if upstream becomes active enough that regular merges are cheaper than porting changes into the overlay.

Migration impact: none; builds and releases are unchanged.

## Change rule

If a future PR changes one of these decisions materially, update this file in the same PR with:

- old decision
- new decision
- evidence/reason
- migration impact
