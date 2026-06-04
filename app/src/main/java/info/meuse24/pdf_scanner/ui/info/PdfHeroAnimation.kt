package info.meuse24.pdf_scanner.ui.info

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PdfHeroAnimation(modifier: Modifier = Modifier) {
    val cs    = MaterialTheme.colorScheme
    val tm    = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    // ── Press interaction ────────────────────────────────────────────────────
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed         by interactionSource.collectIsPressedAsState()

    // Scale shrinks on press, bounces back on release
    val pressScale by animateFloatAsState(
        targetValue    = if (isPressed) 0.91f else 1f,
        animationSpec  = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    // Burst progress: fires on click (0 → 1 → 0), drives flash + scan boost
    val burst = remember { Animatable(0f) }

    // ── Infinite ambient animations ──────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "pdf_hero")

    val floatY by inf.animateFloat(
        initialValue = 0f, targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fy"
    )
    val scanPos by inf.animateFloat(
        initialValue = 0.04f, targetValue = 0.96f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "sp"
    )
    val glowAlpha by inf.animateFloat(
        initialValue = 0.18f, targetValue = 0.46f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ga"
    )
    val orbitAngle by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "oa"
    )
    val shimmerX by inf.animateFloat(
        initialValue = -1.9f, targetValue = 1.9f,
        animationSpec = infiniteRepeatable(tween(3300, easing = LinearEasing), RepeatMode.Restart),
        label = "sx"
    )
    val pageRot by inf.animateFloat(
        initialValue = -1.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(3700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pr"
    )

    val burstVal = burst.value

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(192.dp)
            .graphicsLayer {
                scaleX          = pressScale
                scaleY          = pressScale
                transformOrigin = TransformOrigin.Center
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null
            ) {
                scope.launch {
                    burst.snapTo(1f)
                    burst.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
                }
            }
    ) {
        val cx  = size.width  / 2f
        val cy  = size.height / 2f
        val fcy = cy + floatY

        // ── 1. AMBIENT RADIAL GLOW ──────────────────────────────────────────
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    cs.primaryContainer.copy(alpha = glowAlpha + burstVal * 0.25f),
                    cs.secondaryContainer.copy(alpha = (glowAlpha + burstVal * 0.15f) * 0.45f),
                    Color.Transparent
                ),
                center = Offset(cx, fcy),
                radius = size.width * (0.44f + burstVal * 0.06f)
            )
        )

        // ── 2. ORBITING PARTICLES ────────────────────────────────────────────
        val orbitRx    = size.width  * 0.285f
        val orbitRy    = size.height * 0.27f
        // During burst the particles leap outward slightly
        val orbitBoost = 1f + burstVal * 0.18f
        val baseAngleR = Math.toRadians((orbitAngle + burstVal * 40f).toDouble())
        for (i in 0 until 6) {
            val ang  = baseAngleR + i * (Math.PI * 2.0 / 6.0)
            val px   = cx + (orbitRx * orbitBoost * cos(ang)).toFloat()
            val py   = fcy * 0.98f + (orbitRy * orbitBoost * sin(ang)).toFloat()
            val fade = ((sin(ang * 3.0 + baseAngleR) + 1.0) / 2.0).toFloat()
            val rad  = if (i % 2 == 0) 4.4f else 2.7f
            drawCircle(
                color  = if (i % 3 == 0) cs.primary else if (i % 3 == 1) cs.secondary else cs.tertiary,
                radius = rad * (1f + burstVal * 0.5f),
                center = Offset(px, py),
                alpha  = (0.15f + 0.45f * fade + burstVal * 0.35f).coerceAtMost(0.95f)
            )
        }

        // ── 3. DOCUMENT GEOMETRY ─────────────────────────────────────────────
        val dw = 108.dp.toPx()
        val dh = 140.dp.toPx()
        val cr = CornerRadius(10.dp.toPx())
        val dl = cx - dw / 2f
        val dt = fcy - dh / 2f

        // ── 4. BACK PAGES ────────────────────────────────────────────────────
        listOf(
            Triple(-8.5f + pageRot * 0.55f, -9.dp.toPx(),  4.dp.toPx()),
            Triple( 5.5f - pageRot * 0.38f,  9.dp.toPx(),  7.dp.toPx())
        ).forEachIndexed { idx, (rotDeg, xOff, yOff) ->
            val alpha = if (idx == 0) 0.68f else 0.48f
            withTransform({ rotate(rotDeg, Offset(cx, fcy)) }) {
                drawRoundRect(
                    color        = cs.surfaceVariant.copy(alpha = alpha),
                    topLeft      = Offset(dl + xOff, dt + yOff),
                    size         = Size(dw, dh),
                    cornerRadius = cr
                )
                drawRoundRect(
                    color        = cs.outline.copy(alpha = 0.09f),
                    topLeft      = Offset(dl + xOff, dt + yOff),
                    size         = Size(dw, dh),
                    cornerRadius = cr,
                    style        = Stroke(1.dp.toPx())
                )
            }
        }

        // ── 5. DROP SHADOW ────────────────────────────────────────────────────
        drawContext.canvas.drawRoundRect(
            left    = dl + 4f,
            top     = dt + 10f,
            right   = dl + dw - 4f,
            bottom  = dt + dh - 2f,
            radiusX = cr.x,
            radiusY = cr.y,
            paint   = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    color       = cs.primary.copy(alpha = 0.30f + burstVal * 0.20f).toArgb()
                    maskFilter  = BlurMaskFilter(24f + burstVal * 10f, BlurMaskFilter.Blur.NORMAL)
                }
            }
        )

        // ── 6. MAIN CARD ─────────────────────────────────────────────────────
        drawRoundRect(
            color        = cs.surface,
            topLeft      = Offset(dl, dt),
            size         = Size(dw, dh),
            cornerRadius = cr
        )

        // ── 7. PDF HEADER BAR ─────────────────────────────────────────────────
        val hh = 32.dp.toPx()
        drawRoundRect(
            color        = cs.error,
            topLeft      = Offset(dl, dt),
            size         = Size(dw, hh),
            cornerRadius = cr
        )
        drawRect(
            color   = cs.error,
            topLeft = Offset(dl, dt + hh - cr.x),
            size    = Size(dw, cr.x)
        )
        val pdfLayout = tm.measure(
            AnnotatedString("PDF"),
            TextStyle(
                color         = Color.White,
                fontSize      = 12.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )
        )
        drawText(
            textLayoutResult = pdfLayout,
            topLeft          = Offset(
                dl + (dw - pdfLayout.size.width)  / 2f,
                dt + (hh - pdfLayout.size.height) / 2f
            )
        )

        // ── 8. TEXT LINE PLACEHOLDERS ─────────────────────────────────────────
        val lx         = dl + 12.dp.toPx()
        val lineMaxW   = dw - 24.dp.toPx()
        val lineData   = listOf(1.00f, 0.82f, 0.62f, 0.90f, 0.50f, 0.73f)
        val scanCutoff = dt + hh + (dh - hh) * scanPos
        lineData.forEachIndexed { i, frac ->
            val lineY     = dt + hh + 14.dp.toPx() + i * 12.dp.toPx()
            val scanned   = lineY + 5.dp.toPx() < scanCutoff
            val baseAlpha = if (i % 2 == 0) 0.40f else 0.22f
            drawRoundRect(
                color        = cs.onSurface.copy(
                    alpha = if (scanned) baseAlpha + 0.18f + burstVal * 0.15f else baseAlpha
                ),
                topLeft      = Offset(lx, lineY),
                size         = Size(lineMaxW * frac, 5.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

        // ── 9. SCANNER LINE (boosted during burst) ────────────────────────────
        val scanAreaTop  = dt + hh
        val scanAreaBot  = dt + dh - 4.dp.toPx()
        val scanY        = scanAreaTop + (scanAreaBot - scanAreaTop) * scanPos
        val haloAlpha    = 0.18f + burstVal * 0.28f
        val beamAlpha    = 0.72f + burstVal * 0.25f
        val haloHalf     = 22f   + burstVal * 12f
        val beamHalf     =  8f   + burstVal *  5f

        drawRect(
            brush   = Brush.verticalGradient(
                colors = listOf(Color.Transparent, cs.primary.copy(alpha = haloAlpha), Color.Transparent),
                startY = scanY - haloHalf, endY = scanY + haloHalf
            ),
            topLeft = Offset(dl, scanY - haloHalf),
            size    = Size(dw, haloHalf * 2f)
        )
        drawRect(
            brush   = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    cs.primary.copy(alpha = beamAlpha),
                    cs.tertiary.copy(alpha = beamAlpha * 0.75f),
                    Color.Transparent
                ),
                startY = scanY - beamHalf, endY = scanY + beamHalf
            ),
            topLeft = Offset(dl, scanY - beamHalf),
            size    = Size(dw, beamHalf * 2f)
        )
        drawLine(
            color       = cs.primary.copy(alpha = 0.90f + burstVal * 0.10f),
            start       = Offset(dl + 2.dp.toPx(), scanY),
            end         = Offset(dl + dw - 2.dp.toPx(), scanY),
            strokeWidth = 1.5f + burstVal * 1.5f,
            cap         = StrokeCap.Round
        )

        // ── 10. CORNER FOLD ───────────────────────────────────────────────────
        val foldSize = 13.dp.toPx()
        val foldPath = Path().apply {
            moveTo(dl + dw - foldSize, dt)
            lineTo(dl + dw,            dt)
            lineTo(dl + dw,            dt + foldSize)
            close()
        }
        drawPath(foldPath, color = cs.surfaceVariant.copy(alpha = 0.85f))
        drawLine(
            color       = cs.outline.copy(alpha = 0.20f),
            start       = Offset(dl + dw - foldSize, dt),
            end         = Offset(dl + dw,            dt + foldSize),
            strokeWidth = 1.dp.toPx()
        )

        // ── 11. SHIMMER SWEEP ─────────────────────────────────────────────────
        val shimC = dl + dw * 0.5f + shimmerX * dw * 0.55f
        drawRoundRect(
            brush        = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                start  = Offset(shimC - dw * 0.35f, dt),
                end    = Offset(shimC + dw * 0.35f, dt + dh)
            ),
            topLeft      = Offset(dl, dt),
            size         = Size(dw, dh),
            cornerRadius = cr
        )

        // ── 12. BURST FLASH OVERLAY ───────────────────────────────────────────
        if (burstVal > 0f) {
            drawRoundRect(
                brush        = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = burstVal * 0.28f),
                        cs.primary.copy(alpha  = burstVal * 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(cx, dt + dh * 0.45f),
                    radius = dw * 0.7f
                ),
                topLeft      = Offset(dl, dt),
                size         = Size(dw, dh),
                cornerRadius = cr
            )
        }

        // ── 13. CARD BORDER ───────────────────────────────────────────────────
        drawRoundRect(
            color        = cs.outline.copy(alpha = 0.13f + burstVal * 0.12f),
            topLeft      = Offset(dl, dt),
            size         = Size(dw, dh),
            cornerRadius = cr,
            style        = Stroke(1.dp.toPx())
        )
    }
}
