package com.example.allianceautologin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var loginUrl: String
        get() = prefs.getString(KEY_LOGIN_URL, DEFAULT_LOGIN_URL) ?: DEFAULT_LOGIN_URL
        set(value) {
            val sanitized = if (value.isBlank()) DEFAULT_LOGIN_URL else value.trim()
            prefs.edit().putString(KEY_LOGIN_URL, sanitized).apply()
        }

    fun isConfigured(): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }

    fun save(user: String, pass: String, url: String = DEFAULT_LOGIN_URL) {
        prefs.edit()
            .putString(KEY_USERNAME, user.trim())
            .putString(KEY_PASSWORD, pass)
            .putString(KEY_LOGIN_URL, if (url.isBlank()) DEFAULT_LOGIN_URL else url.trim())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // One-time seamless migration: migrate existing plain preferences if present
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
            val legacyUser = legacyPrefs.getString(KEY_USERNAME, null)
            val legacyPass = legacyPrefs.getString(KEY_PASSWORD, null)
            val legacyUrl = legacyPrefs.getString(KEY_LOGIN_URL, null)

            if (!legacyUser.isNullOrBlank() && !encryptedPrefs.contains(KEY_USERNAME)) {
                encryptedPrefs.edit()
                    .putString(KEY_USERNAME, legacyUser)
                    .putString(KEY_PASSWORD, legacyPass ?: "")
                    .putString(KEY_LOGIN_URL, legacyUrl ?: DEFAULT_LOGIN_URL)
                    .apply()
                legacyPrefs.edit().clear().apply()
            }

            encryptedPrefs
        } catch (e: Exception) {
            Log.e(
                "CredentialsStore",
                "Failed to initialize EncryptedSharedPreferences; falling back to private SharedPreferences",
                e
            )
            context.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val ENCRYPTED_PREF_NAME = "alliance_encrypted_login_prefs"
        private const val LEGACY_PREF_NAME = "alliance_login_prefs"
        private const val KEY_USERNAME = "pref_username"
        private const val KEY_PASSWORD = "pref_password"
        private const val KEY_LOGIN_URL = "pref_login_url"
        const val DEFAULT_LOGIN_URL = "http://10.254.254.57/0/up/"
    }
}
