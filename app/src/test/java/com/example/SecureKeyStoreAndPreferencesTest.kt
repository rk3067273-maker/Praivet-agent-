package com.example

import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.ai.AiProviderId
import com.example.data.local.ConfigSaveResult
import com.example.data.local.JarvisPreferences
import com.example.data.security.AndroidKeyStoreApiKeyStore
import com.example.data.security.KeyStorageResult
import com.example.data.security.KeyStoreCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

class SecureKeyStoreAndPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testCipher = object : KeyStoreCipher {
        override fun encrypt(plaintext: String): String {
            return "ENC:" + Base64.getEncoder().encodeToString(plaintext.toByteArray(StandardCharsets.UTF_8))
        }

        override fun decrypt(encodedCiphertext: String): String {
            require(encodedCiphertext.startsWith("ENC:"))
            val bytes = Base64.getDecoder().decode(encodedCiphertext.removePrefix("ENC:"))
            return String(bytes, StandardCharsets.UTF_8)
        }
    }

    @Test
    fun `migrates legacy plaintext key and never overwrites valid key with empty string`() {
        val backingStores = mutableMapOf<String, InMemorySharedPreferences>()
        val provider: (String) -> SharedPreferences = { name ->
            backingStores.getOrPut(name) { InMemorySharedPreferences() }
        }

        val legacyPrefs = provider("jarvis_legacy_keys")
        legacyPrefs.edit().putString("api_key_OPENAI", "  sk-legacy-123456  ").commit()

        val keyStore = AndroidKeyStoreApiKeyStore(provider, testCipher)
        val migrated = keyStore.migrateLegacyPlaintextKey(
            providerKey = "OPENAI",
            legacyPrefsName = "jarvis_legacy_keys",
            legacyKeyName = "api_key_OPENAI"
        )
        assertTrue(migrated)
        assertEquals("sk-legacy-123456", keyStore.getApiKey("OPENAI"))
        assertNull(legacyPrefs.getString("api_key_OPENAI", null))

        // Blank save must NOT overwrite valid key
        val emptySave = keyStore.saveApiKey("OPENAI", "   ")
        assertTrue(emptySave is KeyStorageResult.IgnoredEmptyInput)
        assertEquals("sk-legacy-123456", keyStore.getApiKey("OPENAI"))

        // Explicit clear removes it
        keyStore.clearApiKey("OPENAI")
        assertFalse(keyStore.hasApiKey("OPENAI"))
    }

    @Test
    fun `provider switching preserves isolated custom configurations and validates webhook https`() = runTest {
        val dsFile = File(tempFolder.root, "test_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { dsFile })
        val prefs = JarvisPreferences(dataStore)

        prefs.setProvider(AiProviderId.CUSTOM)
        val saveCustom = prefs.saveProviderConfig(
            provider = AiProviderId.CUSTOM,
            baseUrl = "https://my-gateway.example.com/v1/",
            modelId = "my-custom-model-v2",
            maxTokens = 4096,
            temperature = 0.35f
        )
        assertTrue(saveCustom is ConfigSaveResult.Saved)

        prefs.setProvider(AiProviderId.AGENTROUTER)
        val saveAr = prefs.saveProviderConfig(
            provider = AiProviderId.AGENTROUTER,
            baseUrl = "https://agentrouter.org/v1",
            modelId = "deepseek-v3",
            maxTokens = 2048,
            temperature = 0.7f
        )
        assertTrue(saveAr is ConfigSaveResult.Saved)

        // Switching back to CUSTOM must preserve custom settings
        prefs.setProvider(AiProviderId.CUSTOM)
        val current = prefs.settings.first()
        assertEquals(AiProviderId.CUSTOM, current.selectedProvider)
        assertEquals("https://my-gateway.example.com/v1", current.activeConfig.baseUrl)
        assertEquals("my-custom-model-v2", current.activeConfig.modelId)
        assertEquals(4096, current.activeConfig.maxTokens)

        val badWebhook = prefs.setWebhookUrl("http://insecure.example.com/hook")
        assertTrue(badWebhook is ConfigSaveResult.ValidationError)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }

            override fun commit(): Boolean {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                data.putAll(pending)
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
