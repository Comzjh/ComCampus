package com.xmu.course.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

/**
 * ComCampus 全局主题。
 *
 * 默认品牌深蓝配色（Phase 8 决策①C）；用户可在
 * 「设置 → 外观 → 跟随系统色彩」开启 Android 12+ Material You 动态取色。
 * 显式传入 dynamicColor 时覆盖用户偏好（用于测试或特殊页面）。
 */
@Composable
fun XmuCourseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    ThemePreferences.ensureLoaded(context)
    val followSystemColor by ThemePreferences.followSystemColor.collectAsState()
    val useDynamicColor = dynamicColor ?: followSystemColor
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ComCampusDarkScheme
        else -> ComCampusLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            small = AppShapes.Button,
            medium = AppShapes.Card,
            large = AppShapes.Section,
        ),
        content = content,
    )
}
