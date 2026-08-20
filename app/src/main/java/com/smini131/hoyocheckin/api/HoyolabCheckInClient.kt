package com.smini131.hoyocheckin.api

import org.json.JSONException
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class HoyolabCheckInClient(
    private val transport: HttpTransport = HttpsUrlConnectionTransport()
) : CheckInClient {

    override fun checkIn(cookieHeader: String): CheckInOutcome {
        var requests = 0
        return try {
            val infoResponse = transport.execute(buildInfoRequest(cookieHeader))
            requests += 1
            val infoFailure = mapHttpFailure(infoResponse, requests)
            if (infoFailure != null) return infoFailure

            val infoJson = parseEnvelope(infoResponse.body)
                ?: return invalidResponse(infoResponse.status, requests)
            mapRetcode(infoJson, infoResponse, requests)?.let { return it }

            val data = infoJson.optJSONObject("data")
                ?: return invalidResponse(infoResponse.status, requests)
            if (!data.has("is_sign") || !data.has("total_sign_day")) {
                return invalidResponse(infoResponse.status, requests)
            }
            val serverDay = validDay(data.optString("today", ""))
            if (data.optBoolean("is_sign", false)) {
                return CheckInOutcome(
                    kind = OutcomeKind.ALREADY_SIGNED,
                    userMessage = "오늘 출석이 이미 완료되어 있습니다.",
                    serverDay = serverDay,
                    serverEpochMillis = infoResponse.serverEpochMillis,
                    networkRequestCount = requests
                )
            }

            val signResponse = transport.execute(buildSignRequest(cookieHeader))
            requests += 1
            val signFailure = mapHttpFailure(signResponse, requests)
            if (signFailure != null) return signFailure

            val signJson = parseEnvelope(signResponse.body)
                ?: return invalidResponse(signResponse.status, requests)
            mapRetcode(signJson, signResponse, requests)?.let { return it }
            if (requiresRiskVerification(signJson)) {
                return CheckInOutcome(
                    kind = OutcomeKind.RISK_VERIFICATION_REQUIRED,
                    userMessage = "HoYoLAB의 추가 보안 확인이 필요합니다. 공식 페이지에서 직접 확인해 주세요.",
                    needsLogin = true,
                    httpStatus = signResponse.status,
                    serverDay = serverDay,
                    serverEpochMillis = signResponse.serverEpochMillis,
                    networkRequestCount = requests
                )
            }

            CheckInOutcome(
                kind = OutcomeKind.SUCCESS,
                userMessage = "원신 HoYoLAB 출석이 완료되었습니다.",
                httpStatus = signResponse.status,
                serverDay = serverDay,
                serverEpochMillis = signResponse.serverEpochMillis,
                networkRequestCount = requests
            )
        } catch (_: UnknownHostException) {
            temporary("인터넷 주소를 찾지 못했습니다. 연결 후 다시 시도합니다.", requests)
        } catch (_: SocketTimeoutException) {
            temporary("HoYoLAB 응답 시간이 초과되었습니다. 나중에 다시 시도합니다.", requests)
        } catch (_: SSLException) {
            CheckInOutcome(
                OutcomeKind.PERMANENT_ERROR,
                "보안 연결을 확인할 수 없어 중단했습니다.",
                networkRequestCount = requests
            )
        } catch (_: InterruptedIOException) {
            temporary("네트워크 작업이 중단되었습니다. 나중에 다시 시도합니다.", requests)
        } catch (_: SecurityException) {
            CheckInOutcome(
                OutcomeKind.PERMANENT_ERROR,
                "보안 정책 때문에 요청을 실행할 수 없습니다.",
                networkRequestCount = requests
            )
        } catch (_: Exception) {
            temporary("일시적인 네트워크 오류가 발생했습니다.", requests)
        }
    }

    private fun buildInfoRequest(cookie: String): HttpRequest {
        val url = "${ApiContract.INFO_URL}?act_id=${encoded(ApiContract.EVENT_ID)}&lang=${encoded(ApiContract.LANGUAGE)}"
        return HttpRequest(url, "GET", commonHeaders(cookie))
    }

    private fun buildSignRequest(cookie: String): HttpRequest {
        val url = "${ApiContract.SIGN_URL}?act_id=${encoded(ApiContract.EVENT_ID)}&lang=${encoded(ApiContract.LANGUAGE)}"
        val json = JSONObject()
            .put("act_id", ApiContract.EVENT_ID)
            .put("lang", ApiContract.LANGUAGE)
            .toString()
            .toByteArray(Charsets.UTF_8)
        return HttpRequest(
            url = url,
            method = "POST",
            headers = commonHeaders(cookie) + mapOf(
                "Content-Type" to "application/json; charset=UTF-8",
                "Origin" to "https://act.hoyolab.com"
            ),
            body = json
        )
    }

    private fun commonHeaders(cookie: String): Map<String, String> = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Cookie" to cookie,
        "Referer" to ApiContract.REFERER,
        "User-Agent" to ApiContract.USER_AGENT,
        "x-rpc-signgame" to "hk4e"
    )

    private fun parseEnvelope(body: String): JSONObject? = try {
        val json = JSONObject(body)
        if (!json.has("retcode")) null else json
    } catch (_: JSONException) {
        null
    }

    private fun mapHttpFailure(response: HttpResponse, requests: Int): CheckInOutcome? = when {
        response.status == 429 -> CheckInOutcome(
            OutcomeKind.RATE_LIMITED,
            "요청이 너무 많아 잠시 뒤 다시 시도합니다.",
            shouldRetry = true,
            httpStatus = response.status,
            serverEpochMillis = response.serverEpochMillis,
            networkRequestCount = requests
        )
        response.status == HttpURLConnectionCodes.UNAUTHORIZED ||
            response.status == HttpURLConnectionCodes.FORBIDDEN -> CheckInOutcome(
            OutcomeKind.LOGIN_REQUIRED,
            "HoYoLAB 로그인이 만료되었습니다.",
            needsLogin = true,
            httpStatus = response.status,
            serverEpochMillis = response.serverEpochMillis,
            networkRequestCount = requests
        )
        response.status == 408 || response.status in 500..599 -> CheckInOutcome(
            OutcomeKind.SERVER_ERROR,
            "HoYoLAB 서버가 일시적으로 응답하지 않습니다.",
            shouldRetry = true,
            httpStatus = response.status,
            serverEpochMillis = response.serverEpochMillis,
            networkRequestCount = requests
        )
        response.status !in 200..299 -> CheckInOutcome(
            OutcomeKind.PERMANENT_ERROR,
            "HoYoLAB 요청을 처리할 수 없습니다(HTTP ${response.status}).",
            httpStatus = response.status,
            serverEpochMillis = response.serverEpochMillis,
            networkRequestCount = requests
        )
        else -> null
    }

    private fun mapRetcode(
        json: JSONObject,
        response: HttpResponse,
        requests: Int
    ): CheckInOutcome? {
        val code = json.optInt("retcode", Int.MIN_VALUE)
        return when (code) {
            0 -> null
            -5003, 2001 -> CheckInOutcome(
                OutcomeKind.ALREADY_SIGNED,
                "오늘 출석이 이미 완료되어 있습니다.",
                httpStatus = response.status,
                serverEpochMillis = response.serverEpochMillis,
                networkRequestCount = requests
            )
            -100, 10001, 10103 -> CheckInOutcome(
                OutcomeKind.LOGIN_REQUIRED,
                "HoYoLAB 로그인이 만료되었습니다.",
                needsLogin = true,
                httpStatus = response.status,
                serverEpochMillis = response.serverEpochMillis,
                networkRequestCount = requests
            )
            -110, 1028 -> CheckInOutcome(
                OutcomeKind.RATE_LIMITED,
                "HoYoLAB 요청 제한으로 잠시 뒤 다시 시도합니다.",
                shouldRetry = true,
                httpStatus = response.status,
                serverEpochMillis = response.serverEpochMillis,
                networkRequestCount = requests
            )
            -10002 -> CheckInOutcome(
                OutcomeKind.PERMANENT_ERROR,
                "이 HoYoLAB 계정에 연결된 원신 글로벌 계정을 찾지 못했습니다.",
                httpStatus = response.status,
                networkRequestCount = requests
            )
            else -> CheckInOutcome(
                OutcomeKind.PERMANENT_ERROR,
                "HoYoLAB이 요청을 거부했습니다(응답 코드 $code).",
                httpStatus = response.status,
                serverEpochMillis = response.serverEpochMillis,
                networkRequestCount = requests
            )
        }
    }

    private fun requiresRiskVerification(json: JSONObject): Boolean {
        val data = json.optJSONObject("data") ?: return false
        val result = data.optJSONObject("gt_result") ?: data
        val riskCode = result.optInt("risk_code", 0)
        return riskCode != 0 &&
            (result.has("gt") || result.has("challenge") || result.has("success"))
    }

    private fun invalidResponse(status: Int, requests: Int) = CheckInOutcome(
        OutcomeKind.INVALID_RESPONSE,
        "HoYoLAB 응답 형식이 변경되었을 수 있습니다. 앱 업데이트를 확인해 주세요.",
        httpStatus = status,
        networkRequestCount = requests
    )

    private fun temporary(message: String, requests: Int) = CheckInOutcome(
        OutcomeKind.TEMPORARY_NETWORK_ERROR,
        message,
        shouldRetry = true,
        networkRequestCount = requests
    )

    private fun validDay(value: String): String? =
        value.takeIf { DAY_PATTERN.matches(it) }

    private fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8")

    private object HttpURLConnectionCodes {
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
    }

    companion object {
        private val DAY_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
