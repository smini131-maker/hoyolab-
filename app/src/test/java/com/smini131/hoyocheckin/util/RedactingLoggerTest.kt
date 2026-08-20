package com.smini131.hoyocheckin.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactingLoggerTest {
    @Test
    fun `쿠키 토큰 이메일과 긴 ID를 모두 마스킹한다`() {
        val raw = "ltoken_v2=topsecret; account_id_v2=123456789 user@example.com Bearer abc.def"
        val safe = RedactingLogger.sanitize(raw)
        assertFalse(safe.contains("topsecret"))
        assertFalse(safe.contains("123456789"))
        assertFalse(safe.contains("user@example.com"))
        assertFalse(safe.contains("abc.def"))
        assertTrue(safe.contains("<redacted>"))
    }
}
