package com.smini131.hoyocheckin.api

enum class OutcomeKind {
    SUCCESS,
    ALREADY_SIGNED,
    LOCAL_ALREADY_SUCCESS,
    LOGIN_REQUIRED,
    MISSING_COOKIE,
    RATE_LIMITED,
    TEMPORARY_NETWORK_ERROR,
    SERVER_ERROR,
    RISK_VERIFICATION_REQUIRED,
    INVALID_RESPONSE,
    PERMANENT_ERROR,
    DISABLED,
    BUSY
}

data class CheckInOutcome(
    val kind: OutcomeKind,
    val userMessage: String,
    val shouldRetry: Boolean = false,
    val needsLogin: Boolean = false,
    val httpStatus: Int? = null,
    val serverDay: String? = null,
    val serverEpochMillis: Long? = null,
    val networkRequestCount: Int = 0
) {
    val isSuccess: Boolean
        get() = kind == OutcomeKind.SUCCESS ||
            kind == OutcomeKind.ALREADY_SIGNED ||
            kind == OutcomeKind.LOCAL_ALREADY_SUCCESS
}

interface CheckInClient {
    fun checkIn(cookieHeader: String): CheckInOutcome
}
