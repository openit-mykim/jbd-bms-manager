# JBD 캘리브레이션 GATT 쓰기 오류 수정

Cwd: /home/mykim/.paseo/worktrees/218ey8ki/fix-calibration-gatt-write-retry

## 상태

- 완료. `GATT write could not start` (0x26) 원인 수정, 단위 테스트, PR #20, CI green 확인.
- PR: https://github.com/openit-mykim/jbd-bms-manager/pull/20 (OPEN, MERGEABLE).
- 실기기 검증은 미실시(`NOT HARDWARE VERIFIED`). 물리 BMS 미연결.

원인 요약:

- Android는 연결당 GATT 작업 1건만 허용한다.
- 기존 코드는 BMS 응답(notification)이 오면 `onCharacteristicWrite` 도착 전에도 다음 write를 시작했다.
- 이때 `writeCharacteristic()`이 false를 반환하며 `BmsConnectionManager.kt:359`의 `GATT write could not start`가 발생했다.
- 응답/쓰기/연결 콜백이 서로 다른 스레드에서 큐와 in-flight 플래그를 수정해 이중 drain 경쟁도 있었다.

수정 요약:

- 모든 GATT 콜백을 메인 looper로 직렬화.
- `GattWriteQueue` 추가: 명령 완료와 write 콜백 완료를 분리. 둘 다 끝나야 다음 write 시작.
- start 거부(false)는 전송된 바이트가 없으므로 큐 head에 재삽입, 최대 2회 재시도(순서 보존, 중복 쓰기 불가).
- write가 실제 시작된 요청은 transport 계층에서 재시도하지 않음(응답 유실은 기존 세션 정책 담당).
- write 콜백 유실 시 3초 watchdog으로 연결 실패/재연결. 큐 영구 정지 방지.

## 변경 파일

- `overlays/app/src/main/kotlin/com/gytxtx/openjbd/BmsConnectionManager.kt`
- `overlays/app/src/main/kotlin/com/gytxtx/openjbd/ble/GattWriteQueue.kt` (신규)
- `overlays/app/src/test/kotlin/com/gytxtx/openjbd/ble/GattWriteQueueTest.kt` (신규, 8 tests)

동작 불변:

- 캘리브레이션 값/검증 범위/프로토콜 프레임 변경 없음.
- Monitor/Maintenance 경계와 read-back verify 흐름 변경 없음.

## 검증

로컬: pinned upstream + overlay 체인 materialize 후 실행.

```
./gradlew testDebugUnitTest --tests "com.gytxtx.openjbd.ble.GattWriteQueueTest"
BUILD SUCCESSFUL in 35s
tests="8" skipped="0" failures="0" errors="0"
```

```
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 9s
aggregate: files=35 tests=311 failures=0 errors=0 skipped=0 (exit 0)
```

CI (`Android Alpha Build`, run 39528793782):

```
✓ build in 1m54s
  ✓ Run unit tests
  ✓ Build debug APK
build: SUCCESS (exit 0)
```

## 커밋

- `d80664d` fix(ble): serialize GATT writes and retry refused starts
- PR #20 생성, push 완료, CI green.

## 제약과 후속

- 승인 유발 작업 없이 CWD 안에서만 작업했다. `/tmp`·홈 경로 쓰기 없음.
- `.local-upstream`은 테스트 후 삭제했고 커밋에 포함되지 않았다.
- 후속 1: 물리 BMS에서 캘리브레이션 write 세션 실행 후 `WRITE_VERIFIED` 기록.
- 후속 2: factory-mode read/write 연속 구간에서 0x26 재현 여부 확인.
- 릴리스: CI는 main merge 시 alpha.14 prerelease APK를 발행한다. 릴리스 전 실기기 검증과 릴리스 노트에 본 수정 반영이 필요하다.
