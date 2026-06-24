package com.healthassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.healthassistant.data.model.AppSettings
import com.healthassistant.ui.navigation.AppNavigation
import com.healthassistant.ui.theme.HealthTrackerTheme
import com.healthassistant.ui.theme.ThemeMode
import com.healthassistant.ui.theme.ThemeState
import com.healthassistant.util.NormalPrefs
import com.healthassistant.util.SecurePrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as HealthTrackerApp

        // 初始化主题状态
        val themeKey = NormalPrefs.getString(this, "theme", "system")
        ThemeState.currentMode = ThemeMode.entries.find { it.key == themeKey } ?: ThemeMode.FOLLOW_SYSTEM

        setContent {
            HealthTrackerTheme(themeMode = ThemeState.currentMode) {
                AppNavigation(repository = app.repository)
            }
        }
    }

    companion object {
        /** 从 SharedPreferences 加载设置 */
        fun loadSettings(context: android.content.Context): AppSettings {
            return AppSettings(
                theme = NormalPrefs.getString(context, "theme", "system"),
                webdavUrl = NormalPrefs.getString(context, "dav_url", ""),
                webdavUser = NormalPrefs.getString(context, "dav_user", ""),
                webdavPassword = SecurePrefs.getString(context, "dav_password", ""),
                aiBaseUrl = NormalPrefs.getString(context, "ai_url", "https://api.openai.com/v1"),
                aiKey = SecurePrefs.getString(context, "ai_key", ""),
                aiModel = NormalPrefs.getString(context, "ai_model", "gpt-4.1-mini"),
            )
        }

        /** 保存设置到 SharedPreferences（同时更新全局主题状态） */
        fun saveSettings(context: android.content.Context, settings: AppSettings) {
            NormalPrefs.putString(context, "theme", settings.theme)
            NormalPrefs.putString(context, "dav_url", settings.webdavUrl)
            NormalPrefs.putString(context, "dav_user", settings.webdavUser)
            SecurePrefs.putString(context, "dav_password", settings.webdavPassword)
            NormalPrefs.putString(context, "ai_url", settings.aiBaseUrl)
            SecurePrefs.putString(context, "ai_key", settings.aiKey)
            NormalPrefs.putString(context, "ai_model", settings.aiModel)

            // 同步更新全局主题状态，让 UI 立即响应
            ThemeState.currentMode = ThemeMode.entries.find { it.key == settings.theme } ?: ThemeMode.FOLLOW_SYSTEM
        }
    }
}
