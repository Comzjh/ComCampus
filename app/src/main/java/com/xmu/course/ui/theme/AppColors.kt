package com.xmu.course.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * ComCampus 品牌色板（Phase 9 · Apple-inspired 迁移）。
 *
 * 设计语言：柔和蓝灰中性 surface ramp + 单一品牌蓝 Accent。
 * 关键修复：Material3 默认 surfaceContainer* 为紫灰色调，
 * 与品牌蓝灰背景冲突（“薰衣草卡”问题），此处全部覆盖为蓝灰中性色。
 * 状态徽标语义色（StatusTone*）仅供 UI 组件使用，不属于 Provider 契约层。
 */
object AppColors {
    /** 品牌主色（与 Color.kt 中既有值保持一致）。 */
    val BrandPrimary = Color(0xFF1A5CAB)
    val BrandPrimaryDark = Color(0xFF9ECAFF)
    val BrandPrimaryContainer = Color(0xFFD3E4FF)
    val BrandPrimaryContainerDark = Color(0xFF00497D)

    /** 页面背景浅灰蓝 + 分组卡片纯白，形成 Apple grouped-background 层级。 */
    val PageBackground = Color(0xFFF5F7FA)
    val PageBackgroundDark = Color(0xFF0F1319)
    val CardSurface = Color(0xFFFFFFFF)
    val CardSurfaceDark = Color(0xFF171C24)
    val OnPage = Color(0xFF171B21)
    val OnPageDark = Color(0xFFE1E5EC)
    val Muted = Color(0xFF5C6470)
    val MutedDark = Color(0xFFA3AAB6)

    /** 分组 surface（iOS systemGroupedBackground 式：白卡浮于浅灰页底）。 */
    val GroupedCard = Color(0xFFFFFFFF)
    val GroupedCardDark = Color(0xFF1A212B)

    /** 蓝灰中性 surface ramp（Light）。 */
    val SurfaceDimLight = Color(0xFFE3E7ED)
    val SurfaceBrightLight = Color(0xFFFDFDFF)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val SurfaceContainerLowLight = Color(0xFFFFFFFF)
    val SurfaceContainerLight = Color(0xFFFFFFFF)
    val SurfaceContainerHighLight = Color(0xFFEEF2F7)
    val SurfaceContainerHighestLight = Color(0xFFE5EAF1)

    /** 蓝灰中性 surface ramp（Dark，避免纯黑 + 高对比 OLED 风）。 */
    val SurfaceDimDark = Color(0xFF0C1015)
    val SurfaceBrightDark = Color(0xFF1A2028)
    val SurfaceContainerLowestDark = Color(0xFF090D12)
    val SurfaceContainerLowDark = Color(0xFF1A212B)
    val SurfaceContainerDark = Color(0xFF171C24)
    val SurfaceContainerHighDark = Color(0xFF1C232D)
    val SurfaceContainerHighestDark = Color(0xFF222A35)
}

internal val ComCampusLightScheme = lightColorScheme(
    primary = AppColors.BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = AppColors.BrandPrimaryContainer,
    onPrimaryContainer = Color(0xFF001B33),
    secondary = Color(0xFF516075),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5F0),
    onSecondaryContainer = Color(0xFF0E1D2E),
    tertiary = Color(0xFF3E675F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EBE1),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = AppColors.PageBackground,
    onBackground = AppColors.OnPage,
    surface = AppColors.CardSurface,
    onSurface = AppColors.OnPage,
    surfaceVariant = Color(0xFFE1E6EE),
    onSurfaceVariant = AppColors.Muted,
    outline = Color(0xFFC6CCD6),
    outlineVariant = Color(0xFFE2E7EF),
    surfaceDim = AppColors.SurfaceDimLight,
    surfaceBright = AppColors.SurfaceBrightLight,
    surfaceContainerLowest = AppColors.SurfaceContainerLowestLight,
    surfaceContainerLow = AppColors.SurfaceContainerLowLight,
    surfaceContainer = AppColors.SurfaceContainerLight,
    surfaceContainerHigh = AppColors.SurfaceContainerHighLight,
    surfaceContainerHighest = AppColors.SurfaceContainerHighestLight,
    inverseSurface = Color(0xFF2C3138),
    inverseOnSurface = Color(0xFFF1F3F7),
    inversePrimary = AppColors.BrandPrimaryDark,
)

internal val ComCampusDarkScheme = darkColorScheme(
    primary = AppColors.BrandPrimaryDark,
    onPrimary = Color(0xFF002E52),
    primaryContainer = AppColors.BrandPrimaryContainerDark,
    onPrimaryContainer = AppColors.BrandPrimaryContainer,
    secondary = Color(0xFFB6C4DB),
    onSecondary = Color(0xFF1D2B3D),
    secondaryContainer = Color(0xFF38465A),
    onSecondaryContainer = Color(0xFFD3E1F8),
    tertiary = Color(0xFFB5D3C9),
    onTertiary = Color(0xFF213B34),
    tertiaryContainer = Color(0xFF37544C),
    onTertiaryContainer = Color(0xFFD1EFE5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF691A16),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AppColors.PageBackgroundDark,
    onBackground = AppColors.OnPageDark,
    surface = AppColors.CardSurfaceDark,
    onSurface = AppColors.OnPageDark,
    surfaceVariant = Color(0xFF414855),
    onSurfaceVariant = AppColors.MutedDark,
    outline = Color(0xFF6E7681),
    outlineVariant = Color(0xFF343B47),
    surfaceDim = AppColors.SurfaceDimDark,
    surfaceBright = AppColors.SurfaceBrightDark,
    surfaceContainerLowest = AppColors.SurfaceContainerLowestDark,
    surfaceContainerLow = AppColors.SurfaceContainerLowDark,
    surfaceContainer = AppColors.SurfaceContainerDark,
    surfaceContainerHigh = AppColors.SurfaceContainerHighDark,
    surfaceContainerHighest = AppColors.SurfaceContainerHighestDark,
    inverseSurface = Color(0xFFE1E5EC),
    inverseOnSurface = Color(0xFF2C3138),
    inversePrimary = AppColors.BrandPrimary,
)

/** 状态徽标的前景色 + 底色对。 */
internal data class StatusToneColors(val foreground: Color, val background: Color)

internal val SuccessToneLight = StatusToneColors(Color(0xFF1E7A45), Color(0xFFD8F0E0))
internal val SuccessToneDark = StatusToneColors(Color(0xFF6ED89B), Color(0xFF143824))
internal val WarningToneLight = StatusToneColors(Color(0xFF8A5A00), Color(0xFFFBE7B6))
internal val WarningToneDark = StatusToneColors(Color(0xFFF2C36B), Color(0xFF3D2E0C))
internal val InfoToneLight = StatusToneColors(Color(0xFF1A5CAB), Color(0xFFD3E4FF))
internal val InfoToneDark = StatusToneColors(Color(0xFF9ECAFF), Color(0xFF00314F))
internal val ErrorToneLight = StatusToneColors(Color(0xFFB3261E), Color(0xFFF9DEDC))
internal val ErrorToneDark = StatusToneColors(Color(0xFFFFB4AB), Color(0xFF5F1A16))
internal val NeutralToneLight = StatusToneColors(Color(0xFF5C6470), Color(0xFFE7EAF0))
internal val NeutralToneDark = StatusToneColors(Color(0xFFA3AAB6), Color(0xFF2A303B))
