package com.smini131.hoyocheckin.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CipherPayload(
    val version: Int,
    val ivBase64: String,
    val ciphertextBase64: String
)

class AesGcmCodec {
    fun encrypt(plaintext: ByteArray, key: SecretKey): CipherPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore에서 randomizedEncryptionRequired=true인 키는 호출자가
        // 지정한 IV를 거부한다. Cipher가 안전한 무작위 IV를 직접 만들게 해야 한다.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size == IV_SIZE_BYTES) { "잘못된 GCM IV 길이" }
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
