package com.xmu.course.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** 课程卡底色混合比例；只用于底色，不淡化文字或点击目标。 */
internal fun courseCardRenderAlpha(
    isCurrentWeek: Boolean,
    configAlpha: Float,
    isSkipped: Boolean,
): Float {
    val base = if (isCurrentWeek) configAlpha.coerceIn(0f, 1f) else configAlpha * 0.35f
    return (base * if (isSkipped) 0.45f else 1f).coerceIn(0f, 1f)
}

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
    val tint = courseCardColor(course).desaturateForRender()
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
    compact: Boolean = false,
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(container)
            .border(
                if (!isCurrentWeek || isSkipped) 1.dp else 0.5.dp,
                foreground.copy(alpha = if (!isCurrentWeek || isSkipped) 0.28f else 0.10f),
                RoundedCornerShape(cornerRadius.dp),
            )
            .semantics {
                contentDescription = listOfNotNull(
                    cleanImportedCourseName(course.name),
                    course.location.takeIf { showLocation && it.isNotBlank() },
                    course.teacher.takeIf { showTeacher && it.isNotBlank() },
                ).joinToString("，")
                stateDescription = listOfNotNull(
                    if (!isCurrentWeek) "非本周" else null,
                    if (isSkipped) "已标记翘课" else null,
                ).joinToString("，")
            }
            .clickable(onClickLabel = "查看课程详情", onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp),
    ) {
        val hasLocation = showLocation && course.location.isNotBlank()
        val hasTeacher = showTeacher && course.teacher.isNotBlank()
        val status = listOfNotNull(
            if (!isCurrentWeek) "非本周" else null,
            if (isSkipped) "翘课" else null,
        ).joinToString(" · ")
        val secondaryLineHeight = with(LocalDensity.current) { (secondaryText + 3).sp.toDp() }
        val titleLineHeight = with(LocalDensity.current) { (titleTextSize + 3).sp.toDp() }
        // The course name gets the first real line, including at large system font sizes.
        // Metadata remains available in the full card description and the existing details.
        val showLocationLine = hasLocation && maxHeight >= titleLineHeight + secondaryLineHeight + 1.dp
        val showTeacherLine = hasTeacher && maxHeight >= titleLineHeight +
            secondaryLineHeight * (if (showLocationLine) 2 else 1) + (if (showLocationLine) 2 else 1).dp
        val fieldCount = (if (showLocationLine) 1 else 0) + (if (showTeacherLine) 1 else 0)
        val titleBudget = (maxHeight - secondaryLineHeight * fieldCount - fieldCount.dp)
            .coerceAtLeast(minOf(titleLineHeight, maxHeight))
        val titleLines = (titleBudget / titleLineHeight).toInt()
            .coerceIn(1, if (course.duration <= 1) 2 else 3)
        val usedHeight = titleLineHeight * titleLines + secondaryLineHeight * fieldCount + fieldCount.dp
        val showStatus = status.isNotEmpty() && maxHeight >= usedHeight + secondaryLineHeight + 1.dp
        val showNoteLine = showNote && !compact && course.note.isNotBlank() &&
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
                fontSize = titleTextSize.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (titleTextSize + 1).sp,
                textAlign = textAlign,
                modifier = Modifier.heightIn(max = titleBudget).testTag("course_card_name"),
            )
            if (showLocationLine) {
                Text(
                    text = "@" + course.location,
                    color = foreground,
                    fontSize = secondaryText.sp,
                    lineHeight = (secondaryText + 1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier.testTag("course_card_location"),
                )
            }
            if (showTeacherLine) {
                // 教师始终出现；窄卡里可以省略，但不允许整块消失。
                Text(
                    text = course.teacher,
                    color = foreground,
                    fontSize = secondaryText.sp,
                    lineHeight = (secondaryText + 1).sp,
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
                    fontSize = secondaryText.sp,
                    lineHeight = (secondaryText + 1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
            }
            if (showNoteLine) {
                Text(
                    text = course.note,
                    color = foreground,
                    fontSize = secondaryText.sp,
                    lineHeight = (secondaryText + 1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
            }
        }
    }
}
