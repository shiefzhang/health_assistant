package com.healthassistant.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 全局主题状态，确保设置页切换后立即生效 */
object ThemeState {
    var currentMode: ThemeMode by mutableStateOf(ThemeMode.FOLLOW_SYSTEM)
}
