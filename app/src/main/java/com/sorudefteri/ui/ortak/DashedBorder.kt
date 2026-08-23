package com.sorudefteri.ui.ortak

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kesikli (dashed) kenarlık çizen modifier.
 */
fun Modifier.dashedBorder(
    strokeWidth: Dp = 2.dp,
    color: Color = Color.Gray,
    cornerRadius: Dp = 12.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 6.dp
): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            0f
        )
    )
    drawRoundRect(
        color = color,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
        style = stroke
    )
}
