package com.smini131.hoyocheckin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class HoyolabCheckInClientTest {
    @Test
    fun `출석 성공은 조회와 출석 두 요청만 사용한다`() {
        val transport = FakeTransport(
            HttpResponse(200, infoJson(false), 1_700_000_000_000),
            HttpResponse(200, """{"retcode":0,"message":"OK","data":{}}""", 1_700_000_001_000)
        )

        val result = HoyolabCheckInClient(transport).checkIn(validCookie)

        assertEquals(OutcomeKind.SUCCESS, result.kind)
        assertEquals(2, result.networkRequestCount)
        assertEquals(2, transport.requests.size)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("POST", transport.requests[1].method)
        assertEquals("hk4e", transport.requests[1].headers["x-rpc-signgame"])
        assertTrue(String(transport.requests[1].body!!).contains(ApiContract.EVENT_ID))
    }

    @Test
    fun `이미 출석이면 조회 한 번 후 성공 처리한다`() {
        val transport = FakeTransport(HttpResponse(200, infoJson(true), 1_700_000_000_000))
        val result = HoyolabCheckInClient(transport).checkIn(validCookie)
        assertEquals(OutcomeKind.ALREADY_SIGNED, result.kind)
        assertEquals(1, result.networkRequestCount)
        assertEquals("2026-08-20", result.serverDay)
    }

    @Test
    fun `로그인 만료 retcode를 구분한다`() {
        val response = HttpResponse(200, """{"retcode":-100,"message":"Please login","data":null}""", null)
        val result = HoyolabCheckInClient(FakeTransport(response)).checkIn(validCookie)
        assertEquals(OutcomeKind.LOGIN_REQUIRED, result.kind)
        assertTrue(result.needsLogin)
        assertFalse(result.shouldRetry)
    }

    @Test
    fun `429는 지수 백오프 가능한 상태다`() {
        val result = HoyolabCheckInClient(FakeTransport(HttpResponse(429, "{}", null))).checkIn(validCookie)
        assertEquals(OutcomeKind.RATE_LIMITED, result.kind)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun `5xx는 재시도 가능 상태다`() {
        val result = HoyolabCheckInClient(FakeTransport(HttpResponse(503, "{}", null))).checkIn(validCookie)
        assertEquals(OutcomeKind.SERVER_ERROR, result.kind)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun `잘못된 JSON은 응답 변경으로 분류한다`() {
        val result = HoyolabCheckInClient(FakeTransport(HttpResponse(200, "not-json", null))).checkIn(validCookie)
        assertEquals(OutcomeKind.INVALID_RESPONSE, result.kind)
    }

    @Test
    fun `필수 응답 필드 누락을 거부한다`() {
        val response = HttpResponse(200, """{"retcode":0,"data":{"is_sign":false}}""", null)
        val result = HoyolabCheckInClient(FakeTransport(response)).checkIn(validCookie)
        assertEquals(OutcomeKind.INVALID_RESPONSE, result.kind)
    }

    @Test
    fun `출석 요청의 이미 완료 retcode도 성공이다`() {
        val transport = FakeTransport(
            HttpResponse(200, infoJson(false), null),
            HttpResponse(200, """{"retcode":-5003,"message":"Already signed","data":null}""", null)
        )
        val result = HoyolabCheckInClient(transport).checkIn(validCookie)
        assertEquals(OutcomeKind.ALREADY_SIGNED, result.kind)
    }

    @Test
    fun `위험 인증은 우회하지 않고 중단한다`() {
        val transport = FakeTransport(
            HttpResponse(200, infoJson(false), null),
            HttpResponse(
                200,
                """{"retcode":0,"data":{"gt_result":{"risk_code":1,"gt":"x","challenge":"y","success":1}}}""",
                null
            )
        )
        val result = HoyolabCheckInClient(transport).checkIn(validCookie)
        assertEquals(OutcomeKind.RISK_VERIFICATION_REQUIRED, result.kind)
        assertTrue(result.needsLogin)
        assertFalse(result.shouldRetry)
    }

    private fun infoJson(signed: Boolean): String =
        """{"retcode":0,"message":"OK","data":{"is_sign":$signed,"total_sign_day":20,"today":"2026-08-20"}}"""

    private class FakeTransport(vararg responses: HttpResponse) : HttpTransport {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return queue.removeFirst()
        }
    }

    companion object {
        private const val validCookie = "ltuid_v2=123; ltoken_v2=secret"
    }
}
