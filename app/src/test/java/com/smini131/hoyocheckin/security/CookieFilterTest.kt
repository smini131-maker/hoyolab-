package com.smini131.hoyocheckin.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieFilterTest {
    @Test
    fun `필요한 도메인 쿠키만 남긴다`() {
        val result = CookieFilter.extract(
            "analytics=remove; ltuid_v2=123; ltoken_v2=secret; theme=dark"
        )
        assertNotNull(result)
        assertTrue(result!!.contains("ltuid_v2=123"))
        assertTrue(result.contains("ltoken_v2=secret"))
        assertFalse(result.contains("analytics"))
        assertFalse(result.contains("theme"))
    }

    @Test
    fun `인증 쌍 일부가 누락되면 거부한다`() {
        assertNull(CookieFilter.extract("ltuid_v2=123; theme=dark"))
    }
}
