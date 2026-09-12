package com.xmu.course.ui.background

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xmu.course.R
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.TimetableConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 统一内置/默认背景流水线参数：确保校园照片不会影响课表可读性。 */
object BackgroundPipelineDefaults {
    const val BUILT_IN_BLUR_RADIUS_DP = 14
    const val BUILT_IN_OVERLAY_COLOR = 0xFF000000.toInt()
    const val BUILT_IN_OVERLAY_ALPHA = 0.35f
    const val MIN_BLUR_RADIUS_DP = 0
    const val MAX_BLUR_RADIUS_DP = 25

    fun validate(blurRadius: Int, overlayAlpha: Float): Boolean =
        blurRadius in MIN_BLUR_RADIUS_DP..MAX_BLUR_RADIUS_DP && overlayAlpha in 0f..0.70f
}

/** 内置厦大主题背景。 */
val xmuBackgrounds = listOf(
    "jiageng" to R.drawable.bg_xmu_jiageng,
    "furong_lake" to R.drawable.bg_xmu_furong_lake,
    "shangxian" to R.drawable.bg_xmu_shangxian,
    "siming_night" to R.drawable.bg_xmu_siming_night,
    "xiangan" to R.drawable.bg_xmu_xiangan,
)

/** 内置纯色背景；backgroundValue 保存可解析颜色字符串。 */
val solidBackgrounds = listOf(
    "厦大蓝" to "#FF1A5799",
    "黑色" to "#FF000000",
    "深灰" to "#FF202124",
    "白色" to "#FFFFFFFF",
    "米白" to "#FFF5F1E8",
)

fun solidColor(value: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF1A5799))

fun backgroundResource(value: String?): Int? =
    xmuBackgrounds.firstOrNull { it.first == value }?.second

/** 简单平均亮度：用于自动遮罩选择黑色或白色。 */
private fun ImageBitmap.averageBrightness(): Float {
    val bitmap = asAndroidBitmap()
    val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 32)
    var sum = 0.0
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = bitmap.getPixel(x, y)
            sum += (0.299 * android.graphics.Color.red(c) +
                0.587 * android.graphics.Color.green(c) +
                0.114 * android.graphics.Color.blue(c)) / 255.0
            count++
            x += step
        }
        y += step
    }
    return if (count == 0) 0.5f else (sum / count).toFloat()
}

private suspend fun loadImageBitmap(context: Context, uri: String?): ImageBitmap? {
    val value = uri ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(value))?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

/**
 * 背景渲染层。背景只负责视觉显示，绝不参与 LayoutEngine / 节次高度 / 冲突分栏计算。
 */
@Composable
fun BackgroundContent(
    config: TimetableConfig,
    modifier: Modifier = Modifier,
    enableCropTransform: Boolean = false,
) {
    val context = LocalContext.current
    val customImage by produceState<ImageBitmap?>(initialValue = null, config.backgroundValue) {
        value = if (config.backgroundType == BackgroundType.CUSTOM) {
            loadImageBitmap(context, config.backgroundValue)
        } else {
            null
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (config.backgroundType) {
            BackgroundType.BUILT_IN -> {
                val resId = backgroundResource(config.backgroundValue)
                if (resId != null) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(config.blurRadius.dp, BlurredEdgeTreatment.Unbounded),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1A5799)))
                }
            }
            BackgroundType.SOLID -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(solidColor(config.backgroundValue)),
                )
            }
            BackgroundType.CUSTOM -> {
                val bitmap = customImage
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = config.cropScale
                                scaleY = config.cropScale
                                translationX = config.cropOffsetX
                                translationY = config.cropOffsetY
                            }
                            .blur(config.blurRadius.dp, BlurredEdgeTreatment.Unbounded),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1A5799)))
                }
            }
            BackgroundType.NONE -> Box(Modifier.fillMaxSize().background(Color.Transparent))
        }

        // 可读性遮罩：overlayColor=0 表示 AUTO；亮图用黑色遮罩，暗图用白色遮罩。
        val overlayColor = if (config.overlayColor == 0) {
            val brightness = customImage?.averageBrightness() ?: 0.5f
            if (brightness > 0.68f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        } else {
            config.overlayColor
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(overlayColor).copy(alpha = config.overlayAlpha)),
        )
    }
}

/** 课表页入口：Scaffold 底层背景。 */
@Composable
fun TimetableBackground(config: TimetableConfig, modifier: Modifier = Modifier) {
    BackgroundContent(
        config = config,
        modifier = modifier,
        enableCropTransform = true,
    )
}
