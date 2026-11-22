package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "SecureKeyStore"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "jarvis_api_key_master_aes256"
private const val SECURE_PREFS_NAME = "jarvis_secure_keys"
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12

sealed interface KeyStorageResult {
    data object Success : KeyStorageResult
    data object IgnoredEmptyInput : KeyStorageResult
    data class Failure(val reason: String) : KeyStorageResult
}

interface ApiKeyStore {
    fun getApiKey(providerKey: String): String?
    fun hasApiKey(providerKey: String): Boolean
    fun saveApiKey(providerKey: String, rawApiKey: String): KeyStorageResult
    fun clearApiKey(providerKey: String)
    fun migrateLegacyPlaintextKey(
        providerKey: String,
        legacyPrefsName: String,
        legacyKeyName: String
    ): Boolean
}

object SecretRedactor {
    private val bearerRegex = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._\-+/=]{6,}""")
    private val skKeyRegex = Regex("""\b(sk-[A-Za-z0-9_\-]{6,}|AIza[A-Za-z0-9_\-]{10,}|nvapi-[A-Za-z0-9_\-]{8,})\b""")

    fun redact(input: String, extraSecrets: List<String> = emptyList()): String {
        var sanitized = input
        extraSecrets.filter { it.length >= 4 }.forEach { secret ->
            sanitized = sanitized.replace(secret, "[REDACTED]")
        }
        sanitized = sanitized.replace(bearerRegex, "$1[REDACTED]")
        sanitized = sanitized.replace(skKeyRegex, "[REDACTED_KEY]")
        return sanitized
    }

    fun maskForDisplay(secret: String?): String {
        val trimmed = secret?.trim().orEmpty()
        if (trimmed.isEmpty()) return "Not configured"
        if (trimmed.length <= 8) return "••••"
        return "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
    }
}

class AndroidKeyStoreApiKeyStore(
    private val prefsProvider: (String) -> SharedPreferences,
    private val cipherProvider: KeyStoreCipher = AndroidKeyStoreCipher()
) : ApiKeyStore {

    constructor(
        context: Context,
        cipherProvider: KeyStoreCipher = AndroidKeyStoreCipher()
    ) : this(
        prefsProvider = { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE) },
        cipherProvider = cipherProvider
    )

    private val securePrefs: SharedPreferences by lazy {
        prefsProvider(SECURE_PREFS_NAME)
    }

    override fun getApiKey(providerKey: String): String? {
        val encoded = securePrefs.getString(prefKey(providerKey), null) ?: return null
        return try {
            val decrypted = cipherProvider.decrypt(encoded)
            decrypted.trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            runCatching { Log.e(TAG, "Unable to decrypt stored API key for $providerKey", e) }
            null
        }
    }

    override fun hasApiKey(providerKey: String): Boolean = !getApiKey(providerKey).isNullOrBlank()

    override fun saveApiKey(providerKey: String, rawApiKey: String): KeyStorageResult {
        val trimmed = rawApiKey.trim()
        if (trimmed.isEmpty()) {
            return KeyStorageResult.IgnoredEmptyInput
        }
        return try {
            val encrypted = cipherProvider.encrypt(trimmed)
            val saved = securePrefs.edit()
                .putString(prefKey(providerKey), encrypted)
                .commit()
            if (saved) {
                KeyStorageResult.Success
            } else {
                KeyStorageResult.Failure("Failed to commit encrypted credential to storage.")
            }
        } catch (e: Exception) {
            runCatching { Log.e(TAG, "Secure key storage unavailable for $providerKey", e) }
            KeyStorageResult.Failure("Secure Keystore unavailable on this device.")
        }
    }

    override fun clearApiKey(providerKey: String) {
        securePrefs.edit().remove(prefKey(providerKey)).commit()
    }

    override fun migrateLegacyPlaintextKey(
        providerKey: String,
        legacyPrefsName: String,
        legacyKeyName: String
    ): Boolean {
        val legacyPrefs = prefsProvider(legacyPrefsName)
        val legacyPlaintext = legacyPrefs.getString(legacyKeyName, null)?.trim().orEmpty()
        if (legacyPlaintext.isEmpty()) return false

        if (!hasApiKey(providerKey)) {
            val result = saveApiKey(providerKey, legacyPlaintext)
            if (result !is KeyStorageResult.Success) {
                return false
            }
        }
        val verified = getApiKey(providerKey)
        if (!verified.isNullOrBlank()) {
            legacyPrefs.edit().remove(legacyKeyName).commit()
            return true
        }
        return false
    }

    private fun prefKey(providerKey: String): String = "enc_key_$providerKey"
}

interface KeyStoreCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(encodedCiphertext: String): String
}

class AndroidKeyStoreCipher : KeyStoreCipher {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(encodedCiphertext: String): String {
        val combined = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH_BYTES) { "Malformed encrypted payload" }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, StandardCharsets.UTF_8)
    }
}
