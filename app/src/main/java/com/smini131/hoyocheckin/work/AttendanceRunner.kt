package com.smini131.hoyocheckin.work

import com.smini131.hoyocheckin.api.CheckInClient
import com.smini131.hoyocheckin.api.CheckInOutcome
import com.smini131.hoyocheckin.api.OutcomeKind
import com.smini131.hoyocheckin.data.AttendanceRepository
import com.smini131.hoyocheckin.data.CookieStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

enum class Trigger { MANUAL, AUTOMATIC }

class AttendanceRunner(
    private val client: CheckInClient,
    private val cookieStore: CookieStore,
    private val state: AttendanceRepository,
    private val timeProvider: TimeProvider = SystemTimeProvider
) {
    fun run(trigger: Trigger): CheckInOutcome {
        if (!MUTEX.tryLock(2, TimeUnit.SECONDS)) {
            return CheckInOutcome(OutcomeKind.BUSY, "다른 출석 확인이 실행 중입니다.")
        }
        try {
            val now = timeProvider.nowMillis()
            val localServerDay = ServerDayPolicy.dayKey(now)
            if (state.canUseLocalFastPath() && state.isSuccessfulFor(localServerDay)) {
                val outcome = CheckInOutcome(
                    OutcomeKind.LOCAL_ALREADY_SUCCESS,
                    "오늘은 이미 성공 처리되어 네트워크 요청 없이 종료했습니다.",
                    serverDay = localServerDay,
                    networkRequestCount = 0
                )
                state.recordAttempt(outcome, now)
                return outcome
            }

            val cookie = cookieStore.load()
            if (cookie.isNullOrBlank()) {
                val outcome = CheckInOutcome(
                    OutcomeKind.MISSING_COOKIE,
                    "저장된 HoYoLAB 로그인이 없습니다. 먼저 로그인해 주세요.",
                    needsLogin = true
                )
                state.setNeedsLogin(true)
                state.recordAttempt(outcome, now)
                return outcome
            }

            val outcome = client.checkIn(cookie)
            if (outcome.isSuccess) {
                val successDay = outcome.serverDay
                    ?: outcome.serverEpochMillis?.let(ServerDayPolicy::dayKey)
                    ?: localServerDay
                if (!state.markSuccess(successDay, now, outcome.serverEpochMillis)) {
                    val storageFailure = CheckInOutcome(
                        OutcomeKind.TEMPORARY_NETWORK_ERROR,
                        "출석 응답은 성공했지만 상태 저장에 실패했습니다. 다음 실행에서 다시 확인합니다.",
                        shouldRetry = true,
                        networkRequestCount = outcome.networkRequestCount
                    )
                    state.recordAttempt(storageFailure, now)
                    return storageFailure
                }
            } else if (outcome.needsLogin) {
                state.setNeedsLogin(true)
            }
            if (outcome.networkRequestCount > 0) state.clearClockChanged()
            state.recordAttempt(outcome, now)
            return outcome
        } finally {
            MUTEX.unlock()
        }
    }

    companion object {
        private val MUTEX = ReentrantLock()
    }
}
