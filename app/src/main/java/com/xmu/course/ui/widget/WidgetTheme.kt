package com.xmu.course.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.compose.runtime.Composable
import android.content.res.Configuration
import com.xmu.course.ui.theme.BluePrimary

/** 桌面小组件可用的四套纯绘制主题（不使用照片）。 */
enum class WidgetBackgroundStyle {
    XMU_BLUE,
    JIAGENG,
    FURONG_LAKE,
    NIGHT_XMU,
}

/**
 * Widget 主题：背景由 Android Canvas 绘制成低尺寸位图；
 * 文字仍由 Glance UI 绘制，确保无障碍和清晰度。
 */
object WidgetTheme {

    val defaultStyle = WidgetBackgroundStyle.XMU_BLUE

    val textPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val textSecondary = androidx.compose.ui.graphics.Color(0xD8FFFFFF)
    // 翘课课程统一按约 0.45 alpha 呈现，仍保留名称、时间和地点。
    val disabledText = androidx.compose.ui.graphics.Color(0x73FFFFFF)

    /** 倒计时前缀的轻量语义色；标题和课程元数据仍使用原有文字色。 */
    fun todoUrgencyColor(urgency: TodoUrgency): androidx.compose.ui.graphics.Color = when (urgency) {
        TodoUrgency.NORMAL -> androidx.compose.ui.graphics.Color(0xFFE6EEF7)
        TodoUrgency.UPCOMING -> androidx.compose.ui.graphics.Color(0xFFFFD166)
        TodoUrgency.URGENT -> androidx.compose.ui.graphics.Color(0xFFFFA45B)
        TodoUrgency.CRITICAL -> androidx.compose.ui.graphics.Color(0xFFFF8078)
        TodoUrgency.OVERDUE -> androidx.compose.ui.graphics.Color(0xFFFF5C5C)
    }

    @Composable
    fun isDarkTheme(): Boolean {
        val uiMode = LocalContext.current.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @Composable
    fun background(style: WidgetBackgroundStyle = defaultStyle): ImageProvider {
        return ImageProvider(drawBackground(style, isDarkTheme()))
    }

    /** 主色/文字颜色；供测试和无障碍校验使用。 */
    fun textColor(isSkipped: Boolean): androidx.compose.ui.graphics.Color =
        if (isSkipped) disabledText else textPrimary

    fun secondaryTextColor(isSkipped: Boolean): androidx.compose.ui.graphics.Color =
        if (isSkipped) disabledText else textSecondary

    private fun drawBackground(style: WidgetBackgroundStyle, isDark: Boolean): Bitmap {
        val width = 220
        val height = 220
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colors = gradientColors(style, isDark)
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            colors.first, colors.second, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = null
        when (style) {
            WidgetBackgroundStyle.XMU_BLUE -> drawSoftLight(canvas, paint, width, height)
            WidgetBackgroundStyle.JIAGENG -> drawRoofLines(canvas, paint, width, height)
            WidgetBackgroundStyle.FURONG_LAKE -> drawWaves(canvas, paint, width, height)
            WidgetBackgroundStyle.NIGHT_XMU -> drawNightDots(canvas, paint, width, height)
        }
        return bitmap
    }

    internal fun gradientColors(style: WidgetBackgroundStyle, isDark: Boolean): Pair<Int, Int> = when (style) {
        WidgetBackgroundStyle.XMU_BLUE ->
            if (isDark) 0xFF0E2A4D.toInt() to 0xFF124070.toInt()
            else BluePrimary.toArgb() to 0xFF3C8BC7.toInt()
        WidgetBackgroundStyle.JIAGENG ->
            if (isDark) 0xFF3B2418.toInt() to 0xFF5C3A25.toInt()
            else 0xFF8C4A2F.toInt() to 0xFFB86A45.toInt()
        WidgetBackgroundStyle.FURONG_LAKE ->
            if (isDark) 0xFF153C52.toInt() to 0xFF1C5B74.toInt()
            else 0xFF3A8FB0.toInt() to 0xFF66B8CE.toInt()
        WidgetBackgroundStyle.NIGHT_XMU ->
            if (isDark) 0xFF071A33.toInt() to 0xFF10294C.toInt()
            else 0xFF27476E.toInt() to 0xFF416B96.toInt()
    }

    private fun drawSoftLight(canvas: Canvas, paint: Paint, width: Int, height: Int) {
        paint.color = 0x1FFFFFFF
        canvas.drawCircle(width * 0.82f, height * 0.20f, width * 0.24f, paint)
        paint.color = 0x12FFFFFF
        canvas.drawCircle(width * 0.24f, height * 0.86f, width * 0.30f, paint)
    }

    private fun drawRoofLines(canvas: Canvas, paint: Paint, width: Int, height: Int) {
        paint.color = 0x2AFFFFFF
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val roof = Path().apply {
            moveTo(0f, height * 0.70f)
            lineTo(width * 0.24f, height * 0.58f)
            lineTo(width * 0.46f, height * 0.70f)
            lineTo(width * 0.70f, height * 0.55f)
            lineTo(width.toFloat(), height * 0.70f)
        }
        canvas.drawPath(roof, paint)
        paint.strokeWidth = 2f
        canvas.drawLine(0f, height * 0.78f, width.toFloat(), height * 0.78f, paint)
        canvas.drawLine(0f, height * 0.90f, width.toFloat(), height * 0.90f, paint)
    }

    private fun drawWaves(canvas: Canvas, paint: Paint, width: Int, height: Int) {
        paint.color = 0x23FFFFFF
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        listOf(0.46f, 0.62f, 0.78f, 0.92f).forEachIndexed { index, ratio ->
            val y = height * ratio
            val path = Path().apply {
                moveTo(-width * 0.1f, y)
                quadTo(
                    width * (0.24f + index * 0.05f), y - 12f,
                    width * 0.55f, y,
                )
                quadTo(
                    width * (0.84f + index * 0.03f), y + 10f,
                    width * 1.1f, y,
                )
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawNightDots(canvas: Canvas, paint: Paint, width: Int, height: Int) {
        paint.style = Paint.Style.FILL
        listOf(
            0.18f to 0.24f,
            0.36f to 0.15f,
            0.68f to 0.20f,
            0.82f to 0.36f,
            0.54f to 0.48f,
            0.24f to 0.66f,
        ).forEachIndexed { index, pair ->
            paint.alpha = if (index % 2 == 0) 70 else 48
            canvas.drawCircle(width * pair.first, height * pair.second, if (index % 3 == 0) 3f else 2f, paint)
        }
    }
}
