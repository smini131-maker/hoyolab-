# 호환성·정책·요청 구조 조사

확인일: 2026-08-20 UTC

## 결론

HoYoLAB의 원신 일일 출석 페이지는 공식 웹 화면이지만, 이 앱이 사용하는 상태 확인/출석 요청은 문서화된 공식 공개 API가 아니다. 공식 약관 페이지에서 개인 자동 출석 도구를 명확히 허용한다는 근거를 확인하지 못했고, 반대로 이 특정 최소 요청을 명시적으로 금지하는 조항도 단정하지 않았다. 따라서 계정 위험과 유지보수 위험을 README 첫 화면에 경고하고, CAPTCHA/위험 인증을 절대 우회하지 않는 보수적 구현으로 제한했다.

## 공식 근거

- [HoYoLAB](https://www.hoyolab.com/): 공식 서비스 진입점.
- [HoYoLAB 이용약관](https://www.hoyolab.com/agreement): 사용 전 사용자가 직접 확인해야 하는 최신 계약 원문. 렌더링과 지역에 따라 조항 표시가 달라질 수 있으므로 프로젝트가 허용 여부를 법적으로 단정하지 않는다.
- [원신 일일 출석 공식 웹 페이지](https://act.hoyolab.com/ys/event/signin-sea-v3/index.html?act_id=e202102251931481): 로그인 WebView가 여는 공식 페이지.
- [Android 17 SDK 설정](https://developer.android.com/about/versions/17/setup-sdk): API 37 compile/target 근거.
- [Android Gradle Plugin 9.1.1 릴리스 노트](https://developer.android.com/build/releases/agp-9-1-0-release-notes): Gradle 9.3.1, JDK 17, API 37 호환 조합 근거.
- [WorkManager 릴리스 노트](https://developer.android.com/jetpack/androidx/releases/work): 안정 버전 2.11.0 선택 근거.
- [지속 작업 개요](https://developer.android.com/develop/background-work/background-tasks/persistent): 앱 재시작/재부팅을 견디는 예약 작업에 WorkManager를 선택한 근거.
- [고유 작업 관리](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work): 중복 enqueue 방지를 위해 unique one-time work를 사용한 근거.
- [백그라운드 작업 제한](https://developer.android.com/develop/background-work/background-tasks/bg-work-restrictions)과 [Doze/App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby): 08:00/20:00을 정확 시각이 아니라 목표 시각으로 안내한 근거.

## 비공개 요청 구조 교차 확인

공식 출석 페이지의 현재 동작과 공개 오픈소스 클라이언트의 최신 구현을 교차 확인했다. 아래 자료는 공식 API 보증이 아니라 변경 감지용 참고자료다.

- [`genshin.py` routes.py](https://github.com/seriaati/genshin.py/blob/42f3f25638e3bcceeddb8288e90560069ad554a5/genshin/client/routes.py)
- [`genshin.py` daily.py](https://github.com/seriaati/genshin.py/blob/42f3f25638e3bcceeddb8288e90560069ad554a5/genshin/client/components/daily.py)
- [`genshin.py` errors.py](https://github.com/seriaati/genshin.py/blob/42f3f25638e3bcceeddb8288e90560069ad554a5/genshin/errors.py)

2026-08-20에 채택한 최소 어댑터:

| 항목 | 값 |
|---|---|
| 이벤트 ID | `e202102251931481` |
| 상태 확인 | `GET https://sg-hk4e-api.hoyolab.com/event/sol/info` |
| 출석 실행 | `POST https://sg-hk4e-api.hoyolab.com/event/sol/sign` |
| 공통 쿼리 | `act_id`, `lang=ko-kr` |
| 최소 헤더 | `Cookie`, `Referer`, `User-Agent`, `x-rpc-signgame: hk4e` |
| 출석 POST 본문 | `act_id`, `lang` JSON |

`is_sign=true` 또는 `retcode=-5003`/`2001`이면 이미 완료로 종료한다. `-100`, `10001`, `10103`, HTTP 401/403은 인증 만료로 처리한다. 429, 5xx, `-110`, `1028`만 일시 오류로 본다. `gt_result`, `risk_code`, `gt`, `challenge`가 나타나면 추가 보안 확인으로 중단한다.

## 날짜와 중복 방지

응답의 `today`가 유효한 `YYYY-MM-DD`이면 이를 최우선으로 저장한다. 응답 날짜가 없으면 HTTP `Date`를 UTC+8(`Asia/Shanghai`)로 변환하고, 그것도 없을 때만 기기 시각을 같은 서버 날짜 정책으로 변환한다. 기기 시각/시간대 변경 방송을 받으면 로컬 fast path를 무효화해 서버 상태를 한 번 다시 확인한다.

## 변경 대응 지점

- URL/이벤트/언어/어댑터 버전: `app/src/main/java/com/smini131/hoyocheckin/api/ApiContract.kt`
- 요청 및 응답 코드: `app/src/main/java/com/smini131/hoyocheckin/api/HoyolabCheckInClient.kt`
- 허용 쿠키 이름: `app/src/main/java/com/smini131/hoyocheckin/security/CookieFilter.kt`
- 서버 날짜 정책: `app/src/main/java/com/smini131/hoyocheckin/work/TimeProvider.kt`

API 변경으로 `INVALID_RESPONSE`가 발생하면 원문 응답을 로그에 남기지 말고, 공식 페이지에서 정상 출석되는지 먼저 확인한 뒤 모의 JSON 테스트를 갱신해야 한다.
