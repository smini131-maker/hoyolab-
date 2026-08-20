# 원신 HoYoLAB 출석 도우미

개인 안드로이드 기기에서 원신 HoYoLAB 일일 출석을 확인하고, 필요한 경우 한 번만 요청하는 비공식 오픈소스 앱입니다.

> [!WARNING]
> 이 프로젝트는 HoYoLAB 또는 HoYoverse가 제공·보증·제휴한 공식 앱이 아닙니다. 공식 공개 API가 아닌 웹 요청 구조에 의존하므로 언제든 동작이 바뀔 수 있고, 자동화 사용에 따른 계정 제한 등 위험은 사용자에게 있습니다. [HoYoLAB 약관](https://www.hoyolab.com/agreement)을 직접 확인한 뒤 본인 계정과 기기에서만 사용하세요. CAPTCHA·위험 인증·추가 보안 확인은 우회하지 않고 자동 작업을 중단합니다.

## 바로 설치

Android 8.0(API 26) 이상 기기에서 디버그 서명 APK를 설치할 수 있습니다.

1. [`releases/GenshinCheckInHelper-v1.0.0-debug.apk`](releases/GenshinCheckInHelper-v1.0.0-debug.apk)를 내려받습니다.
2. APK 직접 설치를 허용하거나 ADB로 설치합니다.

```bash
adb install -r releases/GenshinCheckInHelper-v1.0.0-debug.apk
```

3. 앱에서 **HoYoLAB 로그인**을 누르고 공식 페이지에 직접 로그인합니다.
4. **로그인 완료 확인**을 누른 뒤 **지금 출석 확인**으로 계정 연결을 확인합니다.
5. **자동 출석**을 켭니다. 기본 목표 시각은 기기 현지 시각 08:00, 20:00입니다.

디버그 앱 ID는 `com.smini131.hoyocheckin.debug`입니다. 릴리스 후보는 R8/리소스 축소가 적용됐지만 서명되지 않았으므로 그대로 설치할 수 없습니다. 실제 배포에는 본인 키로 `release` 변형을 서명하세요.

## 핵심 동작

- 로그인할 때만 공식 HoYoLAB 페이지를 제한된 WebView에 표시합니다.
- 비밀번호를 읽거나 저장하지 않습니다. 필요한 쿠키 이름만 골라 Android Keystore의 256비트 AES-GCM 키로 암호화합니다.
- WebView를 닫을 때 WebView 쿠키·기록·클라이언트를 제거합니다.
- 평문 HTTP, 임의 리디렉션, 혼합 콘텐츠, 파일 접근, 위치 접근을 허용하지 않습니다.
- 평상시 서버 상태 확인 1회, 미출석이면 출석 요청 1회로 끝납니다.
- 서버 또는 로컬 기록상 이미 완료된 날에는 추가 출석 요청을 하지 않습니다. 동일 날짜 재실행은 로컬 fast path에서 네트워크 요청 0회입니다.
- WorkManager의 고유한 일회성 작업 두 개를 예약합니다. 앱을 상주시키거나 폴링하지 않습니다.
- 재부팅·앱 업데이트·시각/시간대 변경 후 예약을 재계산합니다.
- 네트워크/429/5xx 오류만 지수 백오프로 최대 2회 재시도합니다. 인증 만료·응답 변경·위험 인증은 반복하지 않습니다.
- 성공, 이미 완료, 재로그인 필요, 최종 실패만 Android 알림으로 안내합니다.

Android의 Doze, 제조사 절전 정책, 네트워크 제약 때문에 목표 시각보다 늦게 실행될 수 있습니다. 이 앱은 정확 알람이나 배터리 최적화 제외를 기본 요구하지 않습니다.

## 개인정보와 보안

| 항목 | 처리 방식 |
|---|---|
| 비밀번호 | 앱이 읽거나 저장하지 않음 |
| 인증 쿠키 | 허용 목록 필터 → AES-256-GCM 암호문/무작위 IV 저장 |
| 암호 키 | Android Keystore 안에서 생성, 추출 불가 키로 사용 |
| 백업 | 앱 백업 비활성화, 보안 SharedPreferences 추가 제외 |
| 로그 | 릴리스 로그 없음, 디버그도 쿠키·토큰·이메일·긴 ID 마스킹 |
| 네트워크 | 시스템 신뢰 저장소 기반 HTTPS만 허용, cleartext 차단 |
| 외부 전송 | HoYoLAB 출석 요청 외 분석·광고·텔레메트리 없음 |
| 로그아웃 | 암호문, IV, 키, 출석/예약 상태 삭제 |

자세한 위협 모델과 점검 결과는 [SECURITY.md](SECURITY.md), 데이터 처리 범위는 [PRIVACY.md](PRIVACY.md)를 참고하세요.

## 개발 환경

- Android Studio: API 37/AGP 9.1.1을 지원하는 버전
- JDK 17
- Android SDK Platform 37, Build Tools 36.0.0
- Gradle 9.3.1 wrapper
- Kotlin 2.2.10(AGP 내장), AppCompat 1.8.0, WorkManager 2.11.0
- 최소 Android 8.0(API 26), 대상 Android 17(API 37)

저장소를 Android Studio에서 열고 SDK 동기화를 마치면 됩니다. 명령줄 검증은 다음과 같습니다.

```bash
./gradlew clean testDebugUnitTest --max-workers=1
./gradlew lintDebug --max-workers=1
./gradlew assembleDebug assembleRelease assembleDebugAndroidTest --max-workers=1
```

출력 경로:

- 설치 가능한 디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`
- 서명 전 릴리스 후보: `app/build/outputs/apk/release/app-release-unsigned.apk`
- 계측 테스트 APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

GitHub Actions도 같은 세 단계를 실행하고 APK를 워크플로 아티팩트로 보관합니다.

## 테스트 결과

2026-08-20 UTC, API 37 빌드 도구 기준:

| 검사 | 결과 |
|---|---|
| JVM 단위 테스트 | 23/23 통과, 실패·오류·건너뜀 0 |
| Android Lint | 빌드 통과, 오류 0, 비차단 경고 78 |
| clean 후 debug/release/androidTest 빌드 | 모두 성공 |
| R8 + 리소스 축소 | 릴리스 후보 814,334바이트 |
| APK 정적 비밀정보 검색 | 실제 쿠키·Bearer 토큰 패턴 0건 |
| 계측 테스트 | APK 컴파일 성공, 에뮬레이터/실기기 실행은 미수행 |

실행 기기가 없는 환경이므로 WebView 실로그인, OEM별 WorkManager 지연, Android Profiler 메모리/누수 측정, Keystore 계측 테스트는 아직 실기기 검증이 필요합니다. 재현 절차와 남은 항목은 [docs/TEST_REPORT.md](docs/TEST_REPORT.md)에 구분했습니다.

## APK 무결성

```text
8856867acbbb8b01739306ca4d8f62f45b4e8b1f5c7c4f851b433efb8d9d2180  GenshinCheckInHelper-v1.0.0-debug.apk
d74e591e7c74c439947848e5d0763ebfe97c4cfcadf3f445079046fd487ac97e  GenshinCheckInHelper-v1.0.0-release-unsigned.apk
479fde37361122aefbe709cb022eb7e0db9b76158132adbf4fb66272acd923aa  GenshinCheckInHelper-v1.0.0-androidTest.apk
```

다음 명령으로 확인할 수 있습니다.

```bash
sha256sum releases/*.apk
```

## 구조

```text
app/src/main/java/com/smini131/hoyocheckin/
├── api/       HTTPS 전송, 응답/오류 매핑, API 어댑터
├── data/      최소 상태 저장소와 인터페이스
├── security/  쿠키 필터, AES-GCM, Android Keystore
├── ui/        메인, 로그인, 설정, 진단 화면
├── util/      알림과 비밀정보 마스킹
└── work/      중복 방지, 서버 날짜, WorkManager 예약
```

비공개 요청 주소와 이벤트 ID는 `ApiContract.kt`, 응답 해석은 `HoyolabCheckInClient.kt`에 모아 API 변경 시 수정 범위를 좁혔습니다.

## 실기기 확인 순서

1. 설치 후 공식 페이지 로그인 및 수동 출석을 실행합니다.
2. HoYoLAB 공식 앱/페이지에서 같은 날 출석 상태가 일치하는지 확인합니다.
3. 수동 버튼을 다시 눌러 진단 화면의 네트워크 요청 수가 0인지 확인합니다.
4. 자동 출석을 켜고 `adb shell dumpsys jobscheduler com.smini131.hoyocheckin.debug`로 예약을 확인합니다.
5. 앱을 닫은 상태, 재부팅 후, 네트워크 단절/복구 후 예약 상태를 확인합니다.
6. 공식 페이지에서 세션을 만료시킨 뒤 자동 재시도가 반복되지 않고 재로그인 알림만 오는지 확인합니다.
7. 로그아웃 후 앱 데이터와 Android Keystore 키가 더 이상 읽히지 않는지 계측 테스트로 확인합니다.

## 조사 근거

최신 Android/WorkManager 호환성, HoYoLAB 웹 요청 구조와 약관 위험 검토는 [docs/RESEARCH.md](docs/RESEARCH.md)에 출처와 함께 정리했습니다. 2026-08-20에 확인한 요청 구조는 비공개 구현 세부사항이므로 반드시 실제 계정에 앞서 테스트 계정/공식 페이지와 교차 확인하세요.

## 라이선스

[MIT License](LICENSE). HoYoLAB, HoYoverse, Genshin Impact 및 관련 표장은 각 권리자에게 있습니다.
