package com.healthassistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ClinicalLightColorScheme = lightColorScheme(
    primary = ClinicalPrimary,
    onPrimary = Color.White,
    primaryContainer = ClinicalPrimaryContainer,
    onPrimaryContainer = ClinicalPrimary,
    secondary = ClinicalSecondary,
    onSecondary = Color.White,
    background = ClinicalBackground,
    onBackground = ClinicalOnBackground,
    surface = ClinicalSurface,
    onSurface = ClinicalOnSurface,
    surfaceVariant = ClinicalSurfaceVariant,
    onSurfaceVariant = ClinicalOnSurfaceVariant,
    outline = ClinicalOutline,
    outlineVariant = ClinicalOutlineVariant,
    error = ClinicalError,
    onError = Color.White,
    errorContainer = ClinicalErrorContainer,
    onErrorContainer = ClinicalError,
    inversePrimary = DarkPrimary,
    surfaceTint = ClinicalPrimary,
)

private val ProfessionalDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF003731),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkPrimary,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF1C353A),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = Color(0xFF690005),
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkError,
    inversePrimary = ClinicalPrimary,
    surfaceTint = DarkPrimary,
)

/** 三种外观模式 */
enum class ThemeMode(val key: String, val label: String) {
    CLINICAL_LIGHT("light", "临床浅色"),
    PROFESSIONAL_DARK("dark", "专业深色"),
    FOLLOW_SYSTEM("system", "跟随系统");
}

@Composable
fun HealthTrackerTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.CLINICAL_LIGHT -> false
        ThemeMode.PROFESSIONAL_DARK -> true
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) ProfessionalDarkColorScheme else ClinicalLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HealthTypography,
        content = content,
    )
}

/** 指标卡片配色便捷引用 */
val metricGreenColor: Color get() = MetricGreen
val metricBlueColor: Color get() = MetricBlue
val metricOrangeColor: Color get() = MetricOrange
val metricPurpleColor: Color get() = MetricPurple
val metricPinkColor: Color get() = MetricPink

/** 根据血糖值返回对应状态色 */
fun glucoseStatusColor(value: Double): Color = when {
    value < 3.9 -> GlucoseWarning
    value <= 10.0 -> GlucoseNormal
    value <= 13.9 -> GlucoseWarning
    else -> GlucoseHigh
}
