package com.xmu.course.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * ComCampus 品牌色板（Phase 9 · Apple-inspired 迁移）。
 *
 * 设计语言：中性灰 surface ramp + 单一品牌蓝 Accent。
 * 关键修复：Material3 默认 surfaceContainer* 为紫灰色调，
 * 与品牌中性背景冲突（“薰衣草卡”问题），此处全部覆盖为中性灰色。
 * 状态徽标语义色（StatusTone*）仅供 UI 组件使用，不属于 Provider 契约层。
 */
object AppColors {
    /** 品牌主色（与 Color.kt 中既有值保持一致）。 */
    val BrandPrimary = Color(0xFF66CCFF)
    val BrandPrimaryDark = BrandPrimary
    val BrandPrimaryContainer = Color(0xFFDCF3FF)
    val BrandPrimaryContainerDark = Color(0xFF07364B)
    val BrandAction = Color(0xFF00658A)

    /** 页面背景浅灰蓝 + 分组卡片纯白，形成 Apple grouped-background 层级。 */
    val PageBackground = Color(0xFFF3F5F8)
    val PageBackgroundDark = Color(0xFF000000)
    val CardSurface = Color(0xFFFFFFFF)
    val CardSurfaceDark = Color(0xFF1C1C1E)
    val OnPage = Color(0xFF172A3A)
    val OnPageDark = Color(0xFFF2F2F7)
    val Muted = Color(0xFF5B6878)
    val MutedDark = Color(0xFFAEAEB2)

    /** 分组 surface（iOS systemGroupedBackground 式：白卡浮于浅灰页底）。 */
    val GroupedCard = Color(0xFFFFFFFF)
    val GroupedCardDark = Color(0xFF1C1C1E)

    /** 蓝灰中性 surface ramp（Light）。 */
    val SurfaceDimLight = Color(0xFFE5E5EA)
    val SurfaceBrightLight = Color(0xFFFAFAFC)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val SurfaceContainerLowLight = Color(0xFFFFFFFF)
    val SurfaceContainerLight = Color(0xFFFFFFFF)
    val SurfaceContainerHighLight = PageBackground
    val SurfaceContainerHighestLight = Color(0xFFE5E5EA)

    /** 蓝灰中性 surface ramp（Dark，以黑底和抬升的灰色内容面建立层级）。 */
    val SurfaceDimDark = Color(0xFF000000)
    val SurfaceBrightDark = Color(0xFF2C2C2E)
    val SurfaceContainerLowestDark = Color(0xFF000000)
    val SurfaceContainerLowDark = Color(0xFF1C1C1E)
    val SurfaceContainerDark = Color(0xFF1C1C1E)
    val SurfaceContainerHighDark = Color(0xFF2C2C2E)
    val SurfaceContainerHighestDark = Color(0xFF3A3A3C)
}

internal val ComCampusLightScheme = lightColorScheme(
    primary = AppColors.BrandPrimary,
    onPrimary = Color(0xFF003247),
    primaryContainer = AppColors.BrandPrimaryContainer,
    onPrimaryContainer = Color(0xFF003247),
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
    surfaceVariant = Color(0xFFEBEBF0),
    onSurfaceVariant = AppColors.Muted,
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFDEE5ED),
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
    onPrimary = Color(0xFF003247),
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
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = AppColors.MutedDark,
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF38383A),
    surfaceDim = AppColors.SurfaceDimDark,
    surfaceBright = AppColors.SurfaceBrightDark,
    surfaceContainerLowest = AppColors.SurfaceContainerLowestDark,
    surfaceContainerLow = AppColors.SurfaceContainerLowDark,
    surfaceContainer = AppColors.SurfaceContainerDark,
    surfaceContainerHigh = AppColors.SurfaceContainerHighDark,
    surfaceContainerHighest = AppColors.SurfaceContainerHighestDark,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF2C3138),
    inversePrimary = AppColors.BrandPrimary,
)

/** 状态徽标的前景色 + 底色对。 */
internal data class StatusToneColors(val foreground: Color, val background: Color)

internal val SuccessToneLight = StatusToneColors(Color(0xFF1E7A45), Color(0xFFD8F0E0))
internal val SuccessToneDark = StatusToneColors(Color(0xFF6ED89B), Color(0xFF143824))
internal val WarningToneLight = StatusToneColors(Color(0xFF8A5A00), Color(0xFFFBE7B6))
internal val WarningToneDark = StatusToneColors(Color(0xFFF2C36B), Color(0xFF3D2E0C))
internal val InfoToneLight = StatusToneColors(AppColors.BrandAction, AppColors.BrandPrimaryContainer)
internal val InfoToneDark = StatusToneColors(AppColors.BrandPrimary, AppColors.BrandPrimaryContainerDark)
internal val ErrorToneLight = StatusToneColors(Color(0xFFB3261E), Color(0xFFF9DEDC))
internal val ErrorToneDark = StatusToneColors(Color(0xFFFFB4AB), Color(0xFF5F1A16))
internal val NeutralToneLight = StatusToneColors(Color(0xFF636366), Color(0xFFEEEEF2))
internal val NeutralToneDark = StatusToneColors(Color(0xFFAEAEB2), Color(0xFF2C2C2E))

/** The cyan brand fill stays exact; foreground actions need contrast on their own surface. */
val ColorScheme.action: Color
    get() {
        if (uiContrastRatio(primary, surface) >= 4.5f) return primary
        if (primary == AppColors.BrandPrimary && uiContrastRatio(AppColors.BrandAction, surface) >= 4.5f) {
            return AppColors.BrandAction
        }
        return if (uiContrastRatio(onPrimaryContainer, surface) >= 4.5f) onPrimaryContainer else onSurface
    }

internal fun uiContrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
        (minOf(firstLuminance, secondLuminance) + 0.05f)
}
