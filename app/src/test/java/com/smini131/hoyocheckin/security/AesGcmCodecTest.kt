package com.smini131.hoyocheckin.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmCodecTest {
    @Test
    fun `AES 256 GCM 암호화와 복호화가 성공한다`() {
        val keyGenerator = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGenerator.generateKey()
        val plaintext = "ltuid_v2=123; ltoken_v2=secret".toByteArray()
        val payload = AesGcmCodec().encrypt(plaintext, key)
        assertNotEquals(String(plaintext), payload.ciphertextBase64)
        assertArrayEquals(plaintext, AesGcmCodec().decrypt(payload, key))
    }

    @Test
    fun `다른 키나 손상된 키로는 안전하게 실패한다`() {
        val keyGenerator = KeyGenerator.getInstance("AES").apply { init(256) }
        val payload = AesGcmCodec().encrypt("secret".toByteArray(), keyGenerator.generateKey())
        assertThrows(Exception::class.java) {
            AesGcmCodec().decrypt(payload, keyGenerator.generateKey())
        }
    }

    @Test
    fun `암호화할 때마다 Cipher가 서로 다른 IV를 만든다`() {
        val keyGenerator = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGenerator.generateKey()
        val codec = AesGcmCodec()

        val first = codec.encrypt("same-value".toByteArray(), key)
        val second = codec.encrypt("same-value".toByteArray(), key)

        assertNotEquals(first.ivBase64, second.ivBase64)
        assertNotEquals(first.ciphertextBase64, second.ciphertextBase64)
    }
}
