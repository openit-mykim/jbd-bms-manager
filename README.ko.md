# JBD BMS Manager

JBD / Jiabaida 스마트 BMS를 위한 비공식 오픈소스 Android 관리 앱입니다.

이 프로젝트는 [OpenJBD](https://github.com/gytxtx/OpenJBD)를 기반으로 하며, 일반적인 배터리 상태 확인과 기술자용 유지보수 기능을 명확히 분리하면서 하나의 관리 앱으로 통합하는 것을 목표로 합니다.

## 기본 화면 구조

JBD BMS Manager의 하단 메뉴는 다음 5개로 고정합니다.

```text
개요 / 상세 / 밸런스 / 제어 / 설정
```

각 메뉴의 역할은 다음과 같습니다.

- **개요** — SOC, 팩 전압/전류/출력, 온도, 셀 편차, 보호 상태 등 핵심 상태를 빠르게 확인
- **상세** — 용량, 사이클, 펌웨어/장치 정보, 세부 온도와 보호 상태 등 읽기 전용 상세 정보
- **밸런스** — 셀별 전압, 최소/최대/평균, 전압 편차(ΔV), 현재 밸런싱 셀 및 셀 상태 진단
- **제어** — 잠금형 유지보수 모드. 캘리브레이션, 보호 설정, 밸런스 설정, 용량/SOC 관리, MOS 제어, 백업/복원, 진단
- **설정** — 언어, 테마, 온도 단위, 갱신 주기, 자동 연결, 앱 정보 등 앱 자체 설정

설계 원칙은 명확합니다.

```text
밸런스 = 상태 확인과 진단
제어 = 유지보수 작업
```

따라서 밸런싱 시작 전압이나 전압차 기준 같은 설정값 변경은 `밸런스` 화면이 아니라 `제어` 화면에 둡니다.

기존 OpenJBD의 `Overview / Parameters / Settings` 구조는 다음 방향으로 개편합니다.

```text
Overview / Detail / Balance / Control / Settings
```

기존 `Parameters`의 사용자용 정보는 `상세`로 이동하고, 원시 프로토콜 정보와 기술자용 진단은 `제어 → 진단`으로 이동합니다.

자세한 정보 구조는 `docs/navigation-design.md`를 참고하세요.

## 현재 제공 기능

- JBD / Xiaoxiang 계열 BMS BLE 검색 및 연결
- SOC, 팩 전압/전류/출력 표시
- 셀별 전압 및 셀 전압 편차 표시
- 온도, MOS, 밸런싱, 보호 상태 표시
- 자동 재연결
- 가로형 대시보드
- 한국어 / 영어 / 중국어 UI
- Android 16(API 36) edge-to-edge / 시스템바 inset 대응
- 계정이나 클라우드 없이 로컬 BLE 동작

## 제어 / 유지보수 모드

제어 메뉴는 기본적으로 잠긴 상태에서 시작하며, 사용자가 의도적으로 유지보수 모드를 해제한 뒤 지원되는 기능만 표시하도록 설계합니다.

계획된 기능은 다음과 같습니다.

- 팩 전압 캘리브레이션
- 셀 전압 캘리브레이션
- 무부하 전류(Zero) 캘리브레이션
- 충전/방전 전류 캘리브레이션
- 보호 파라미터 읽기/쓰기
- 밸런싱 파라미터 설정
- 용량/SOC 관리
- 충전/방전 MOS 제어
- 설정 백업/복원
- 장치/펌웨어 진단

모든 쓰기 작업은 `읽기 → 편집 → 검증 → 변경사항 확인 → 적용 → 재읽기 검증` 절차를 따르도록 설계합니다.

## Android 16 수정

기존 OpenJBD의 API 36 빌드는 최신 Android에서 상단 Toolbar와 하단 Navigation이 시스템 상태바/제스처 영역과 겹칠 수 있습니다.

JBD BMS Manager는 `WindowInsets`를 명시적으로 처리해 다음 영역을 분리합니다.

```text
상태바 inset
Toolbar
앱 콘텐츠
Bottom Navigation
내비게이션/제스처 inset
```

메인 화면뿐 아니라 장치 검색, 앱 정보, 라이선스 등 별도 Activity의 Toolbar도 동일하게 상태바 inset을 적용해야 합니다.

## 설치

GitHub의 **Releases**에서 최신 `jbd-bms-manager-*.apk`를 내려받아 Android 기기에 설치할 수 있습니다.

초기 알파 버전은 Play Store 배포본이 아니므로 Android에서 "알 수 없는 앱 설치" 권한이 필요할 수 있습니다.

## 개발 및 에이전트 운영

이 저장소는 Hermes Agent + Paseo 기반 장기 개발을 고려해 구성되어 있습니다.

에이전트는 다음 순서로 읽고 작업합니다.

1. `AGENTS.md`
2. `PROJECT_STATUS.md`
3. `HERMES.md`
4. `docs/development-plan.md`
5. `docs/architecture.md`
6. `docs/navigation-design.md`
7. 관련 세부 문서

Paseo에서는 비단순 작업을 독립 worktree로 수행하고, 테스트 및 빌드 결과를 확인한 뒤 PR로 통합합니다.

## Upstream

- 원본 프로젝트: [gytxtx/OpenJBD](https://github.com/gytxtx/OpenJBD)
- 초기 기준 commit: `7e3e225a128f6e0d69425b98a2670d8d69594885`
- 원본 라이선스: MIT
- 원본 저작권: Copyright (c) 2026 KFACBT

원본 MIT 라이선스는 `THIRD_PARTY_LICENSES/OpenJBD-LICENSE`에 보존합니다.

## 라이선스

이 프로젝트는 MIT License로 배포됩니다. 자세한 내용은 `LICENSE`를 참고하세요.

## 주의

BMS 보호값, 캘리브레이션, 밸런싱, 용량 및 MOS 설정 변경은 배터리 안전에 직접 영향을 줄 수 있습니다. 유지보수 모드의 쓰기 기능은 검증된 장치/펌웨어에 한해 단계적으로 활성화합니다.

이 프로젝트는 JBD / Jiabaida의 공식 프로젝트가 아니며 제조사와 제휴 또는 보증 관계가 없습니다.
