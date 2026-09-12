package com.xmu.course.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmu.course.domain.Course
import com.xmu.course.domain.TextHorizontalAlignment
import com.xmu.course.domain.TextVerticalAlignment

/** 课程名 hash → 默认卡片色（与 Repository 导入配色一致的多彩风格）。 */
private val PALETTE = listOf(
    Color(0xFFFAAC8F), Color(0xFFFDCF93), Color(0xFF93D36E), Color(0xFF7FD4E0),
    Color(0xFFA79FE1), Color(0xFFF49BC1), Color(0xFF8FBCFA), Color(0xFFE8C877),
)

/** 课程卡最终透明度；翘课只影响视觉，不影响布局。 */
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

private fun textAlign(value: TextHorizontalAlignment): TextAlign = when (value) {
    TextHorizontalAlignment.START -> TextAlign.Start
    TextHorizontalAlignment.CENTER -> TextAlign.Center
    TextHorizontalAlignment.END -> TextAlign.End
}

/**
 * 网格中的课程卡片（超级课程表风格：高密度、彩色底、白字）。
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
    onClick: () -> Unit,
) {
    val container = if (isSkipped) Color(0xFF8A8A8A) else courseCardColor(course)
    // 字号设置直接控制课程名；地点/教师略小，保证窄列信息密度。
    val titleTextSize = textSize.coerceIn(8, 18)
    val secondaryText = (textSize - 2).coerceIn(7, 16)
    val alignment = horizontalAlignment(textHorizontalAlignment)
    val arrangement = verticalAlignment(textVerticalAlignment)
    val textAlign = textAlign(textHorizontalAlignment)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(container, RoundedCornerShape(cornerRadius.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .alpha(courseCardRenderAlpha(isCurrentWeek, cardAlpha, isSkipped)),
        verticalArrangement = Arrangement.spacedBy(1.dp, arrangement),
        horizontalAlignment = alignment,
        content = {
            Text(
                text = course.name,
                color = Color.White,
                fontSize = titleTextSize.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (course.duration <= 1) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (titleTextSize + 1).sp,
                textAlign = textAlign,
                modifier = Modifier.testTag("course_card_name"),
            )
            if (showLocation && course.location.isNotBlank()) {
                Text(
                    text = "@" + course.location,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = secondaryText.sp,
                    lineHeight = (secondaryText + 1).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Visible,
                    textAlign = textAlign,
                    modifier = Modifier.testTag("course_card_location"),
                )
            }
            if (showTeacher && course.teacher.isNotBlank()) {
                // 教师始终出现；窄卡里可以省略，但不允许整块消失。
                Text(
                    text = course.teacher,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = secondaryText.sp,
                    lineHeight = (secondaryText + 1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
            }
            // 空间不足时隐藏顺序：备注；地点/教师始终保留。
            if (showNote && course.note.isNotBlank()) {
                var noteOverflowed by remember(course.id, course.note) { mutableStateOf(false) }
                if (!noteOverflowed) {
                    Text(
                        text = course.note,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = secondaryText.sp,
                        lineHeight = (secondaryText + 1).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = textAlign,
                        onTextLayout = { if (it.hasVisualOverflow) noteOverflowed = true },
                    )
                }
            }
        },
    )
}
