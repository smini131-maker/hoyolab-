package com.smini131.hoyocheckin.api

/**
 * 비공개 HoYoLAB 웹 요청 구조가 바뀌면 우선 이 파일과
 * [HoyolabCheckInClient]만 확인하면 되도록 주소와 식별자를 한곳에 모은다.
 */
object ApiContract {
    const val ADAPTER_VERSION = "sol-web-2026-08-20"
    const val EVENT_ID = "e202102251931481"
    const val API_ORIGIN = "https://sg-hk4e-api.hoyolab.com"
    const val INFO_URL = "$API_ORIGIN/event/sol/info"
    const val SIGN_URL = "$API_ORIGIN/event/sol/sign"
    const val REFERER = "https://act.hoyolab.com/"
    const val LOGIN_URL =
        "https://act.hoyolab.com/ys/event/signin-sea-v3/index.html?act_id=$EVENT_ID"
    const val LANGUAGE = "ko-kr"
    const val USER_AGENT = "GenshinCheckInHelper/1.0 (Android; unofficial personal tool)"
}
