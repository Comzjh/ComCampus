package com.xmu.course.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 统一间距规格：页面与组件应引用这里，而不是散落的硬编码 dp 值。
 *
 * 基准栅格为 4dp，页面标准外边距 16dp（对应审计结论中的主流值）。
 */
object AppSpacing {
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp

    /** 页面内容外边距。 */
    val PagePadding = Lg

    /** 卡片内边距。 */
    val CardPadding = Lg

    /** 列表项 / 相邻卡片间距。 */
    val ItemGap = Md

    /** 分区（Section）之间的间距。 */
    val SectionGap = Xxl
}
