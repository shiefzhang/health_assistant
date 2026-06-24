package com.healthassistant.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 敏感配置的加密存储（API Key、WebDAV 密码等）
 */
object SecurePrefs {
    private const val PREFS_NAME = "health_secure_prefs"
    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return prefs!!
    }

    fun getString(context: Context, key: String, default: String = ""): String {
        return getPrefs(context).getString(key, default) ?: default
    }

    fun putString(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun remove(context: Context, key: String) {
        getPrefs(context).edit().remove(key).apply()
    }
}

/** 普通偏好设置（非敏感数据） */
object NormalPrefs {
    private const val PREFS_NAME = "health_normal_prefs"

    fun getString(context: Context, key: String, default: String = ""): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, default) ?: default
    }

    fun putString(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, default)
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
    }
}
