package com.smini131.hoyocheckin.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.smini131.hoyocheckin.data.CookieStore
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecureCookieStore(context: Context) : CookieStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val codec = AesGcmCodec()

    @Synchronized
    override fun save(cookieHeader: String) {
        val plaintext = cookieHeader.toByteArray(Charsets.UTF_8)
        try {
            val payload = codec.encrypt(plaintext, getOrCreateKey())
            check(
                preferences.edit()
                    .putInt(KEY_VERSION, payload.version)
                    .putString(KEY_IV, payload.ivBase64)
                    .putString(KEY_CIPHERTEXT, payload.ciphertextBase64)
                    .commit()
            ) { "암호화된 인증정보를 저장하지 못했습니다." }
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    override fun load(): String? {
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val payload = CipherPayload(preferences.getInt(KEY_VERSION, -1), iv, ciphertext)
        val plaintext = try {
            codec.decrypt(payload, getExistingKey() ?: return handleCorruption())
        } catch (_: Exception) {
            return handleCorruption()
        }
        return try {
            String(plaintext, Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun exists(): Boolean = preferences.contains(KEY_CIPHERTEXT)

    @Synchronized
    override fun clear() {
        preferences.edit().clear().commit()
        val keyStore = keyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun handleCorruption(): String? {
        preferences.edit().clear().commit()
        return null
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun getExistingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "genshin_checkin_cookie_aes_v1"
        private const val PREFS_NAME = "secure_cookie_store"
        private const val KEY_VERSION = "version"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
    }
}
