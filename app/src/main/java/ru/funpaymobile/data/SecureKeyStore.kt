package ru.funpaymobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Хранит golden_key в зашифрованном виде (Android Keystore). Ключ не попадает в логи и резервные копии. */
class SecureKeyStore(context: Context) {
    private val prefs: SharedPreferences? = try {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    val available: Boolean get() = prefs != null

    fun read(): String? = try {
        prefs?.getString(KEY, null)
    } catch (e: Exception) {
        null
    }

    fun save(value: String): Boolean = try {
        prefs?.edit()?.putString(KEY, value)?.commit() ?: false
    } catch (e: Exception) {
        false
    }

    fun clear() {
        try {
            prefs?.edit()?.remove(KEY)?.commit()
        } catch (e: Exception) {
            // ничего
        }
    }

    private companion object {
        const val KEY = "golden_key"
    }
}
