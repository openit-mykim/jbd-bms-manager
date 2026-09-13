# Testing Strategy

Testing is layered so agents can move quickly without replacing evidence with assumption.

## 1. Fast local checks

Run the smallest relevant checks while editing. Examples after the OpenJBD source is imported:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

If a narrower module/test target exists, run it first.

## 2. Protocol tests

Protocol changes require deterministic tests for:

- frame construction
- checksum handling
- fragmented BLE notification reassembly where applicable
- parser behavior for short/invalid responses
- unit/scaling conversion
- capability detection inputs

Writable maintenance features must add tests for the application transaction around the protocol layer, not only UI rendering.

## 3. UI/state tests

Priorities:

- connect/disconnect states
- reconnect banner/state
- empty device state
- Monitor/Maintenance separation
- review/confirmation flow for maintenance actions
- localization layout regressions
- dark/light themes for critical screens

## 4. Android compatibility matrix

Minimum target matrix after Phase 1:

| Area | Required |
|---|---|
| API 36 / Android 16 | Yes |
| API 35 | Yes |
| One older supported API | Yes |
| Portrait | Yes |
| Landscape | Yes |
| Gesture navigation | Yes |
| Button navigation | Best effort / device availability |
| Light theme | Yes |
| Dark theme | Yes |

The Android 16 system-bar fix must include manual or screenshot evidence because a successful Gradle build does not prove correct inset behavior.

## 5. Hardware verification

BLE and device behavior cannot be fully proven by unit tests.

For hardware-dependent work, record:

- BMS model
- firmware/hardware identifier if available
- Android device / OS version
- connection path tested
- operation performed
- expected result
- observed result
- whether read-back matched

If no physical BMS was available, state `NOT HARDWARE VERIFIED` in the PR. Do not convert absence of hardware into a pass.

## 6. Verification script

After source import, maintain `scripts/verify.sh` as the default repository check. It should stay predictable and reasonably bounded. The initial intent is:

```text
unit tests
→ debug build
→ selected static checks when configured
```

Do not make every small worktree run an unnecessarily expensive full integration suite if focused evidence is enough during development. Broader checks belong at PR/integration boundaries.

## 7. Failure policy

- Do not rerun the same failing command repeatedly without investigation.
- Distinguish baseline failure from regression introduced by the current task.
- Capture relevant log excerpt in the task report.
- If a check is intentionally skipped, state why and what remains to verify.

## 8. Release gate

A release candidate should have:

- clean CI
- successful debug/release build as applicable
- protocol regression suite pass
- Android 16 UI verification
- supported-device notes
- no unresolved critical maintenance-flow issue
- release notes and version update
