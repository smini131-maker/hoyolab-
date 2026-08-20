package com.smini131.hoyocheckin.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CipherPayload(
    val version: Int,
    val ivBase64: String,
    val ciphertextBase64: String
)

class AesGcmCodec(private val secureRandom: SecureRandom = SecureRandom()) {
    fun encrypt(plaintext: ByteArray, key: SecretKey): CipherPayload {
        val iv = ByteArray(IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        val encrypted = cipher.doFinal(plaintext)
        return CipherPayload(
            version = CURRENT_VERSION,
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted)
        )
    }

    fun decrypt(payload: CipherPayload, key: SecretKey): ByteArray {
        require(payload.version == CURRENT_VERSION) { "지원하지 않는 암호문 버전" }
        val iv = Base64.getDecoder().decode(payload.ivBase64)
        val encrypted = Base64.getDecoder().decode(payload.ciphertextBase64)
        require(iv.size == IV_SIZE_BYTES) { "잘못된 IV" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
    }
}
