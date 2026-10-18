package com.xmu.course.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.xmu.course.domain.Course
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment
import com.xmu.course.domain.cleanImportedCourseName

/** 课程名 hash → 默认卡片色（与 Repository 导入配色一致的多彩风格）。 */
private val PALETTE = listOf(
    Color(0xFFFAAC8F), Color(0xFFFDCF93), Color(0xFF93D36E), Color(0xFF7FD4E0),
    Color(0xFFA79FE1), Color(0xFFF49BC1), Color(0xFF8FBCFA), Color(0xFFE8C877),
)

/** 各状态保留用户配置的透明度；翘课通过独立灰色底表达。 */
@Suppress("UNUSED_PARAMETER") // 保留现有渲染辅助函数签名，状态不参与透明度计算。
internal fun courseCardRenderAlpha(
    isCurrentWeek: Boolean,
    configAlpha: Float,
    isSkipped: Boolean,
): Float = configAlpha.coerceIn(0f, 1f)

internal fun courseCardColor(course: Course): Color {
    if (course.color.isNotBlank()) {
        runCatching { Color(android.graphics.Color.parseColor(course.color)) }
            .getOrNull()?.let { return it }
    }
    return PALETTE[((course.name.hashCode() % PALETTE.size) + PALETTE.size) % PALETTE.size]
}

/** 在稳定主题底上混合底色，不写回用户颜色，不让壁纸降低文字对比。 */
internal fun courseCardContainerColor(
    course: Course,
    surface: Color,
    isCurrentWeek: Boolean,
    cardAlpha: Float,
    isSkipped: Boolean,
): Color {
    // 翘课覆盖课程配色，但不写回原颜色；取消翘课即可恢复。
    val tint = if (isSkipped) {
        if (surface.luminance() < 0.5f) Color(0xFF555555) else Color(0xFFCCCCCC)
    } else {
        courseCardColor(course).desaturateForRender()
    }
    return tint.copy(
        alpha = tint.alpha * courseCardRenderAlpha(isCurrentWeek, cardAlpha, isSkipped),
    ).compositeOver(surface)
}

/** 根据卡片底色选择对比度更高的前景色，兼顾内置色与用户自定义色。 */
internal fun courseCardForegroundColor(background: Color): Color {
    val light = Color.White
    val dark = Color(0xFF0B1117)
    val backgroundLuminance = background.luminance()

    fun contrastRatio(foreground: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val lighter = maxOf(backgroundLuminance, foregroundLuminance)
        val darker = minOf(backgroundLuminance, foregroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    return if (contrastRatio(light) >= contrastRatio(dark)) light else dark
}

private fun horizontalAlignment(value: TextHorizontalAlignment): Alignment.Horizontal = when (value) {
    TextHorizontalAlignment.START -> Alignment.Start
    TextHorizontalAlignment.CENTER -> Alignment.CenterHorizontally
    TextHorizontalAlignment.END -> Alignment.End
}

private fun verticalAlignment(value: TextVerticalAlignment): Alignment.Vertical = when (value) {
    TextVerticalAlignment.TOP -> Alignment.Top
    TextVerticalAlignment.CENTER -> Alignment.CenterVertically
    TextVerticalAlignment.BOTTOM -> Alignment.Bottom
}

/**
 * Phase 9 视觉降饱和：仅在渲染层向同亮度灰靠拢，
 * 保持课程间可区分、不改布局算法、不改存储颜色。
 */
internal fun Color.desaturateForRender(amount: Float = 0.22f): Color {
    val lum = luminance()
    return Color(
        red = red + (lum - red) * amount,
        green = green + (lum - green) * amount,
        blue = blue + (lum - blue) * amount,
        alpha = alpha,
    )
}

private fun textAlign(value: TextHorizontalAlignment): TextAlign = when (value) {
    TextHorizontalAlignment.START -> TextAlign.Start
    TextHorizontalAlignment.CENTER -> TextAlign.Center
    TextHorizontalAlignment.END -> TextAlign.End
}

/**
 * 网格中的课程卡片：高密度彩色底与独立的高对比文字。
 * 时间由左侧独立时间轴展示；卡片只显示课程名、地点、教师与备注。
 */
@Composable
fun CourseCard(
    course: Course,
    modifier: Modifier = Modifier,
    isCurrentWeek: Boolean = true,
    isSkipped: Boolean = false,
    showTeacher: Boolean = true,
    showLocation: Boolean = true,
    showNote: Boolean = true,
    textSize: Int = 9,
    cornerRadius: Int = 8,
    cardAlpha: Float = 1f,
    textHorizontalAlignment: TextHorizontalAlignment = TextHorizontalAlignment.CENTER,
    textVerticalAlignment: TextVerticalAlignment = TextVerticalAlignment.CENTER,
    @Suppress("UNUSED_PARAMETER") compact: Boolean = false,
    onClick: () -> Unit,
) {
    val container = courseCardContainerColor(
        course, MaterialTheme.colorScheme.surface, isCurrentWeek, cardAlpha, isSkipped,
    )
    val foreground = courseCardForegroundColor(container)
    // 响应式布局只改变卡片宽度，不应把正常课程的标题永久压成 8sp。
    // 保留 compact 参数兼容新版调用方，但沿用稳定版的字号上下限。
    val titleTextSize = textSize.coerceIn(8, 18)
    val secondaryText = (textSize - 2).coerceIn(7, 16)
    val alignment = horizontalAlignment(textHorizontalAlignment)
    val arrangement = verticalAlignment(textVerticalAlignment)
    val textAlign = textAlign(textHorizontalAlignment)
    val status = listOfNotNull(
        if (!isCurrentWeek) "非本周" else null,
        if (isSkipped) "翘课" else null,
    ).joinToString(" · ")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(container)
            .border(
                0.5.dp,
                foreground.copy(alpha = 0.10f),
                RoundedCornerShape(cornerRadius.dp),
            )
            .semantics {
                contentDescription = listOfNotNull(
                    cleanImportedCourseName(course.name),
                    course.location.takeIf { showLocation && it.isNotBlank() },
                    course.teacher.takeIf { showTeacher && it.isNotBlank() },
                ).joinToString("，")
                stateDescription = listOfNotNull(
                    if (isCurrentWeek) "本周" else "非本周",
                    if (isSkipped) "已标记翘课" else null,
                ).joinToString("，")
            }
            .clickable(onClickLabel = "查看课程详情", onClick = onClick)
            // 各状态保留相同内边距，小标记不与课程名重叠，也不改变整块边框。
            .padding(start = 3.dp, end = 3.dp, top = 10.dp, bottom = 4.dp),
    ) {
        if (status.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-7).dp)
                    .testTag("course_card_status_marker"),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!isCurrentWeek) {
                    Canvas(Modifier.size(6.dp).testTag("course_card_inactive_marker")) {
                        val stroke = 1.dp.toPx()
                        drawCircle(foreground, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                        drawLine(foreground, center, Offset(center.x, size.height * 0.2f), stroke)
                        drawLine(foreground, center, Offset(size.width * 0.8f, center.y), stroke)
                    }
                }
                if (isSkipped) {
                    Canvas(Modifier.size(6.dp).testTag("course_card_skipped_marker")) {
                        val inset = 1.dp.toPx()
                        drawLine(foreground, Offset(inset, inset), Offset(size.width - inset, size.height - inset), inset)
                        drawLine(foreground, Offset(size.width - inset, inset), Offset(inset, size.height - inset), inset)
                    }
                }
            }
        }
        val hasLocation = showLocation && course.location.isNotBlank()
        val hasTeacher = showTeacher && course.teacher.isNotBlank()
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val titleStyle = LocalTextStyle.current.copy(
            fontSize = titleTextSize.sp, lineHeight = (titleTextSize + 1).sp,
            fontWeight = FontWeight.SemiBold,
        )
        val secondaryStyle = LocalTextStyle.current.copy(
            fontSize = secondaryText.sp, lineHeight = (secondaryText + 1).sp,
        )
        // Measure the same font metrics that Text uses, including fallback CJK fonts.
        // Estimated sp-to-dp sums can leave the last metadata row partly clipped.
        val secondaryLineHeight = with(density) {
            textMeasurer.measure("国Ag", style = secondaryStyle).size.height.toDp()
        }
        val titleHeights = (1..if (course.duration <= 1) 2 else 3).map { lines ->
            with(density) {
                textMeasurer.measure(
                    text = cleanImportedCourseName(course.name), style = titleStyle,
                    maxLines = lines, overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = maxWidth.roundToPx().coerceAtLeast(1)),
                ).size.height.toDp()
            }
        }
        val titleLineHeight = titleHeights.first()
        // The course name gets the first real line, including at large system font sizes.
        // Metadata remains available in the full card description and the existing details.
        val showLocationLine = hasLocation && maxHeight >= titleLineHeight + secondaryLineHeight + 1.dp
        val showTeacherLine = hasTeacher && maxHeight >= titleLineHeight +
            secondaryLineHeight * (if (showLocationLine) 2 else 1) + (if (showLocationLine) 2 else 1).dp
        val fieldCount = (if (showLocationLine) 1 else 0) + (if (showTeacherLine) 1 else 0)
        val titleBudget = (maxHeight - secondaryLineHeight * fieldCount - fieldCount.dp)
            .coerceAtLeast(minOf(titleLineHeight, maxHeight))
        val titleLines = titleHeights.indexOfLast { it <= titleBudget }.let { if (it < 0) 1 else it + 1 }
        val usedHeight = titleHeights[titleLines - 1] + secondaryLineHeight * fieldCount + fieldCount.dp
        val showStatus = status.isNotEmpty() && maxHeight >= usedHeight + secondaryLineHeight + 1.dp
        // 普通列与冲突列共用层级、字号和高度预算；密度由调用方的显示设置决定。
        val showNoteLine = showNote && course.note.isNotBlank() &&
            maxHeight >= usedHeight + secondaryLineHeight * (if (showStatus) 2 else 1) + 2.dp
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(1.dp, arrangement),
            horizontalAlignment = alignment,
        ) {
            Text(
                // 兼容旧数据库：即使课程是在名称清洗上线前导入的，课表卡片也不再显示班号。
                text = cleanImportedCourseName(course.name),
                color = foreground,
                style = titleStyle,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.heightIn(max = titleBudget).testTag("course_card_name"),
            )
            if (showLocationLine) {
                Text(
                    text = "@" + course.location,
                    color = foreground,
                    style = secondaryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier.testTag("course_card_location"),
                )
            }
            if (showTeacherLine) {
                // 元信息受同一高度预算约束，窄列有界省略，完整内容仍可点详情查看。
                Text(
                    text = course.teacher,
                    color = foreground,
                    style = secondaryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier.testTag("course_card_teacher"),
                )
            }
            if (showStatus) {
                Text(
                    text = status,
                    color = foreground,
                    style = secondaryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier.testTag("course_card_status"),
                )
            }
            if (showNoteLine) {
                Text(
                    text = course.note,
                    color = foreground,
                    style = secondaryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
            }
        }
    }
}
