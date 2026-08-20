package com.smini131.hoyocheckin

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smini131.hoyocheckin.security.SecureCookieStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureCookieStoreInstrumentedTest {
    private val store = SecureCookieStore(ApplicationProvider.getApplicationContext())

    @After
    fun cleanUp() = store.clear()

    @Test
    fun androidKeystoreRoundTripAndLogoutDeletion() {
        val cookie = "ltuid_v2=123; ltoken_v2=test-token"
        store.save(cookie)
        assertEquals(cookie, store.load())
        store.clear()
        assertFalse(store.exists())
        assertNull(store.load())
    }
}
