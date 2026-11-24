package com.example.qrattendance.presentation.scanner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Dimmed overlay with a transparent square viewfinder, corner brackets and a scan line. */
@Composable
fun ScannerOverlay(
    frameSize: Dp,
    isScanning: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "scanLine")
    val lineProgress by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanLineProgress",
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val frame = frameSize.toPx()
        val left = (size.width - frame) / 2f
        val top = (size.height - frame) / 2f
        val radius = 24.dp.toPx()

        drawRect(Color.Black.copy(alpha = 0.6f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(frame, frame),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Clear,
        )
        drawCornerBrackets(left, top, frame, if (isScanning) accentColor else Color.White)

        if (isScanning) {
            val y = top + frame * lineProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accentColor, Color.Transparent),
                    startX = left,
                    endX = left + frame,
                ),
                start = Offset(left + 12.dp.toPx(), y),
                end = Offset(left + frame - 12.dp.toPx(), y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawCornerBrackets(left: Float, top: Float, frame: Float, color: Color) {
    val length = 34.dp.toPx()
    val stroke = 5.dp.toPx()
    val right = left + frame
    val bottom = top + frame
    val inset = stroke / 2f

    fun line(start: Offset, end: Offset) =
        drawLine(color = color, start = start, end = end, strokeWidth = stroke, cap = StrokeCap.Round)

    // Top-left
    line(Offset(left + inset, top + inset), Offset(left + length, top + inset))
    line(Offset(left + inset, top + inset), Offset(left + inset, top + length))
    // Top-right
    line(Offset(right - inset, top + inset), Offset(right - length, top + inset))
    line(Offset(right - inset, top + inset), Offset(right - inset, top + length))
    // Bottom-left
    line(Offset(left + inset, bottom - inset), Offset(left + length, bottom - inset))
    line(Offset(left + inset, bottom - inset), Offset(left + inset, bottom - length))
    // Bottom-right
    line(Offset(right - inset, bottom - inset), Offset(right - length, bottom - inset))
    line(Offset(right - inset, bottom - inset), Offset(right - inset, bottom - length))
}
