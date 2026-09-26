package com.xmu.course.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 统一圆角规格。
 */
object AppShapes {
    val Card: CornerBasedShape = RoundedCornerShape(16.dp)
    val Section: CornerBasedShape = RoundedCornerShape(20.dp)
    val Button: CornerBasedShape = RoundedCornerShape(12.dp)
    val Chip: CornerBasedShape = CircleShape
    val SheetTop: CornerBasedShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}
