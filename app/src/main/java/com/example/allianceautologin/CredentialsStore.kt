package com.example.allianceautologin

import android.content.Context
import android.content.SharedPreferences

class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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

    companion object {
        private const val PREF_NAME = "alliance_login_prefs"
        private const val KEY_USERNAME = "pref_username"
        private const val KEY_PASSWORD = "pref_password"
        private const val KEY_LOGIN_URL = "pref_login_url"
        const val DEFAULT_LOGIN_URL = "http://10.254.254.57/0/up/"
    }
}
