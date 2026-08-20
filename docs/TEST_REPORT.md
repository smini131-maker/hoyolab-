# 빌드·테스트·최적화 보고서

검증일: 2026-08-20 UTC

환경: Linux, JDK 17, Gradle 9.3.1, AGP 9.1.1, Android Platform 37, Build Tools 36.0.0

## 최종 clean 재현

최종 소스에서 병렬 작업 수를 1로 제한해 다음을 순서대로 실행했다.

```bash
./gradlew clean testDebugUnitTest --offline --no-daemon --max-workers=1
./gradlew lintDebug --offline --no-daemon --max-workers=1
./gradlew assembleDebug assembleRelease assembleDebugAndroidTest \
  --offline --no-daemon --max-workers=1
```

세 명령 모두 `BUILD SUCCESSFUL`이었다. 앞선 병렬 조합 실행에서는 Gradle 임시 바이너리 저장소와 ART 프로필 컴파일에서 각각 일시적 내부 오류가 한 번 발생했으나, 소스/테스트 오류는 아니었고 작업을 분리하고 `--max-workers=1`로 재실행했을 때 clean 상태에서 재현되지 않았다. CI도 같은 방식으로 단계를 분리한다.

## 단위 테스트

| 테스트 클래스 | 개수 | 범위 |
|---|---:|---|
| `HoyolabCheckInClientTest` | 9 | 성공, 이미 완료, 인증 만료, 429, 5xx, 비JSON, 필드 누락, 위험 인증, `-5003` |
| `WorkTimeCalculatorTest` | 6 | 오전/저녁/자정, DST gap/overlap, 시각 역행 |
| `AttendanceRunnerTest` | 3 | 첫 성공, 같은 날 0요청, 쿠키 없음/시각 변경 |
| `AesGcmCodecTest` | 2 | AES-GCM 왕복, 잘못된 키 실패 |
| `CookieFilterTest` | 2 | 허용 목록, 필수 쿠키 쌍 누락 |
| `RedactingLoggerTest` | 1 | 쿠키/토큰/이메일/긴 ID 마스킹 |
| 합계 | 23 | 실패 0, 오류 0, 건너뜀 0 |

정상 미출석 경로는 HTTP 2회, 이미 출석한 서버 경로는 1회, 같은 서버 날짜에 성공 후 재실행은 0회가 모의 전송 계층 테스트로 확인됐다.

## Android Lint

- 결과: 통과
- 오류: 0
- 경고: 78

비차단 경고의 대부분은 한국어 전용 단일 언어 앱의 XML 하드코딩 문자열, `SharedPreferences`의 의도적 동기 `commit()`(성공 상태/암호문 원자성 확인), KTX 제안, 레이아웃 overdraw/미사용 색상 같은 품질 제안이다. API 레벨 오류, cleartext, exported 컴포넌트, 권한 누락 오류는 없다.

## 계측 테스트

다음 Android 계측 테스트가 컴파일되어 `GenshinCheckInHelper-v1.0.0-androidTest.apk`에 포함됐다.

- Android Keystore 암호화 저장/복호화/삭제
- WorkManager 테스트 초기화와 오전·저녁 고유 작업 예약

이 환경에는 Android 에뮬레이터/실기기가 없어 `connectedDebugAndroidTest`는 실행하지 않았다. 따라서 Keystore 하드웨어/StrongBox 특성, OEM별 예약 복원, 실제 로그인 쿠키 호환성은 미검증이다.

## APK와 최적화

| 파일 | 크기 | 서명 | 용도 |
|---|---:|---|---|
| `GenshinCheckInHelper-v1.0.0-debug.apk` | 4,158,690바이트 | Android 디버그 서명 | 즉시 설치/기능 검증 |
| `GenshinCheckInHelper-v1.0.0-release-unsigned.apk` | 814,334바이트 | 없음 | R8/리소스 축소 릴리스 후보 |
| `GenshinCheckInHelper-v1.0.0-androidTest.apk` | 367,869바이트 | 테스트 서명 | 계측 테스트 |

릴리스는 R8 최적화와 리소스 축소가 적용됐다. 앱 직접 의존성은 AppCompat와 WorkManager뿐이며, 별도 HTTP/JSON/분석 SDK를 추가하지 않았다. WorkManager가 내부적으로 Room/JobScheduler를 사용하지만 앱 자체 데이터베이스나 상주 서비스는 없다.

## 정적 보안 점검

- APK 권한: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, WorkManager의 `WAKE_LOCK` 및 동적 리시버 보호 권한
- WorkManager가 병합하는 불필요한 `FOREGROUND_SERVICE` 권한은 manifest merger로 제거
- 앱 Activity/Receiver 중 런처 Activity만 외부 공개; WorkManager 공개 컴포넌트는 시스템 바인딩/DUMP 권한으로 보호
- `usesCleartextTraffic=false`, 시스템 CA만 허용
- 앱 백업 비활성화
- 앱/릴리스 DEX에서 실제 `ltoken=`, `cookie_token=`, `Bearer` 값 패턴 0건
- 소스의 실제 자격증명 0건; 테스트용 더미 값은 test/androidTest 소스에만 존재

## 실기기에서 남은 성능 검증

다음 수치는 실행 기기 없이는 정직하게 측정할 수 없어 목표값으로만 유지했다.

- 수동/자동 1회 실행의 실제 벽시계 시간과 네트워크 지연
- Worker 실행 중 피크 메모리와 WebView 종료 후 회수
- Android Profiler/LeakCanary 기반 누수 확인
- Doze, 앱 강제 종료, 재부팅, OEM 절전 정책에서의 실제 실행 지연
- 24시간당 실제 요청 수

실기기에서는 Android Studio Profiler로 로그인 화면 진입 전/후/종료 후 heap dump를 비교하고, `adb shell dumpsys jobscheduler com.smini131.hoyocheckin.debug`와 진단 화면의 요청 횟수를 함께 기록해야 한다. 앱 강제 종료는 Android가 예약 작업을 중단할 수 있으므로 다시 앱을 열기 전 자동 실행을 보장하지 않는다.

## SHA-256

```text
479fde37361122aefbe709cb022eb7e0db9b76158132adbf4fb66272acd923aa  GenshinCheckInHelper-v1.0.0-androidTest.apk
8856867acbbb8b01739306ca4d8f62f45b4e8b1f5c7c4f851b433efb8d9d2180  GenshinCheckInHelper-v1.0.0-debug.apk
d74e591e7c74c439947848e5d0763ebfe97c4cfcadf3f445079046fd487ac97e  GenshinCheckInHelper-v1.0.0-release-unsigned.apk
```
