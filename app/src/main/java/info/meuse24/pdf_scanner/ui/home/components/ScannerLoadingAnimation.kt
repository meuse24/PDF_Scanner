package info.meuse24.pdf_scanner.ui.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
internal fun ScannerLoadingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanProgress"
    )
    val primary      = MaterialTheme.colorScheme.primary
    val surfaceVar   = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = Modifier.size(72.dp)) {
        val pad  = 8.dp.toPx()
        val left = pad
        val top  = pad
        val right = size.width - pad
        val bot  = size.height - pad
        val docW = right - left
        val docH = bot - top

        drawRoundRect(
            color        = surfaceVar,
            topLeft      = Offset(left, top),
            size         = Size(docW, docH),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        val lx0     = left  + 8.dp.toPx()
        val lx1     = right - 8.dp.toPx()
        val lxSh    = right - 20.dp.toPx()
        val lStroke = 2.dp.toPx()
        drawLine(outlineColor.copy(0.35f), Offset(lx0, top + docH * 0.28f), Offset(lx1,  top + docH * 0.28f), lStroke, StrokeCap.Round)
        drawLine(outlineColor.copy(0.35f), Offset(lx0, top + docH * 0.50f), Offset(lx1,  top + docH * 0.50f), lStroke, StrokeCap.Round)
        drawLine(outlineColor.copy(0.35f), Offset(lx0, top + docH * 0.72f), Offset(lxSh, top + docH * 0.72f), lStroke, StrokeCap.Round)

        val scanY     = top + 4.dp.toPx() + (docH - 8.dp.toPx()) * scanProgress
        val glowBrush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, primary.copy(0.9f), primary, primary.copy(0.9f), Color.Transparent),
            startX = left, endX = right
        )
        drawLine(glowBrush, Offset(left + 2.dp.toPx(), scanY - 3.dp.toPx()), Offset(right - 2.dp.toPx(), scanY - 3.dp.toPx()), 5.dp.toPx())
        drawLine(glowBrush, Offset(left + 2.dp.toPx(), scanY),               Offset(right - 2.dp.toPx(), scanY),               2.dp.toPx())
    }
}
