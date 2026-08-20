package com.smini131.hoyocheckin.work

import com.smini131.hoyocheckin.api.CheckInClient
import com.smini131.hoyocheckin.api.CheckInOutcome
import com.smini131.hoyocheckin.api.OutcomeKind
import com.smini131.hoyocheckin.data.AttendanceRepository
import com.smini131.hoyocheckin.data.CookieStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AttendanceRunnerTest {
    @Test
    fun `첫 실행 성공 후 두 번째 실행은 네트워크 0회다`() {
        val time = FixedTime("2026-08-20T01:00:00Z")
        val state = MemoryState()
        val client = CountingClient()
        val runner = AttendanceRunner(client, MemoryCookieStore("ltuid_v2=1; ltoken_v2=x"), state, time)

        val first = runner.run(Trigger.AUTOMATIC)
        val second = runner.run(Trigger.AUTOMATIC)

        assertEquals(OutcomeKind.SUCCESS, first.kind)
        assertEquals(OutcomeKind.LOCAL_ALREADY_SUCCESS, second.kind)
        assertEquals(0, second.networkRequestCount)
        assertEquals(1, client.callCount)
    }

    @Test
    fun `쿠키가 없으면 네트워크 없이 로그인 필요다`() {
        val client = CountingClient()
        val state = MemoryState()
        val result = AttendanceRunner(
            client,
            MemoryCookieStore(null),
            state,
            FixedTime("2026-08-20T01:00:00Z")
        ).run(Trigger.MANUAL)
        assertEquals(OutcomeKind.MISSING_COOKIE, result.kind)
        assertTrue(result.needsLogin)
        assertEquals(0, client.callCount)
    }

    @Test
    fun `시간 변경 표시가 있으면 로컬 성공이어도 서버를 다시 조회한다`() {
        val time = FixedTime("2026-08-20T01:00:00Z")
        val state = MemoryState().apply {
            successDay = ServerDayPolicy.dayKey(time.nowMillis())
            clockChanged = true
        }
        val client = CountingClient()
        val result = AttendanceRunner(
            client,
            MemoryCookieStore("ltuid_v2=1; ltoken_v2=x"),
            state,
            time
        ).run(Trigger.AUTOMATIC)
        assertEquals(OutcomeKind.SUCCESS, result.kind)
        assertEquals(1, client.callCount)
    }

    private class CountingClient : CheckInClient {
        var callCount = 0
        override fun checkIn(cookieHeader: String): CheckInOutcome {
            callCount += 1
            return CheckInOutcome(
                OutcomeKind.SUCCESS,
                "성공",
                serverDay = "2026-08-20",
                networkRequestCount = 2
            )
        }
    }

    private class MemoryCookieStore(private var value: String?) : CookieStore {
        override fun save(cookieHeader: String) { value = cookieHeader }
        override fun load(): String? = value
        override fun exists(): Boolean = value != null
        override fun clear() { value = null }
    }

    private class MemoryState : AttendanceRepository {
        var successDay: String? = null
        var clockChanged = false
        var loginRequired = false
        override fun isSuccessfulFor(dayKey: String) = successDay == dayKey
        override fun canUseLocalFastPath() = !clockChanged
        override fun markClockChanged() { clockChanged = true }
        override fun clearClockChanged() { clockChanged = false }
        override fun recordAttempt(outcome: CheckInOutcome, attemptedAtMillis: Long) = Unit
        override fun markSuccess(dayKey: String, successAtMillis: Long, serverEpochMillis: Long?): Boolean {
            successDay = dayKey
            return true
        }
        override fun setNeedsLogin(value: Boolean) { loginRequired = value }
    }

    private class FixedTime(iso: String) : TimeProvider {
        private val epoch = Instant.parse(iso).toEpochMilli()
        override fun nowMillis() = epoch
        override fun deviceZone() = ZoneId.of("Asia/Seoul")
    }
}
