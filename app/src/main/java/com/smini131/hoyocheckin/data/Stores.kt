package com.smini131.hoyocheckin.data

import com.smini131.hoyocheckin.api.CheckInOutcome

interface CookieStore {
    fun save(cookieHeader: String)
    fun load(): String?
    fun exists(): Boolean
    fun clear()
}

interface AttendanceRepository {
    fun isSuccessfulFor(dayKey: String): Boolean
    fun canUseLocalFastPath(): Boolean
    fun markClockChanged()
    fun clearClockChanged()
    fun recordAttempt(outcome: CheckInOutcome, attemptedAtMillis: Long)
    fun markSuccess(dayKey: String, successAtMillis: Long, serverEpochMillis: Long?): Boolean
    fun setNeedsLogin(value: Boolean)
}
