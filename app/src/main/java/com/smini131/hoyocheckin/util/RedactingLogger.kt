package com.smini131.hoyocheckin.util

import android.util.Log
import com.smini131.hoyocheckin.BuildConfig

object RedactingLogger {
    private val cookiePattern = Regex(
        "(?i)(ltoken(?:_v2)?|cookie_token(?:_v2)?|account_id(?:_v2)?|ltuid(?:_v2)?)=([^;\\s]+)"
    )
    private val bearerPattern = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*")
    private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val longIdPattern = Regex("(?<!\\d)\\d{8,}(?!\\d)")

    fun sanitize(message: String): String = message
        .replace(cookiePattern) { "${it.groupValues[1]}=<redacted>" }
        .replace(bearerPattern, "Bearer <redacted>")
        .replace(emailPattern, "<email-redacted>")
        .replace(longIdPattern, "<id-redacted>")

    fun debug(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, sanitize(message))
    }
}
