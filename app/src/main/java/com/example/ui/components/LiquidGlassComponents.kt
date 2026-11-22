package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanArc
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.GlassNavy
import com.example.ui.theme.IceBlue
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.PlasmaTeal
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.VoidObsidian
import kotlin.math.cos
import kotlin.math.sin

enum class CoreVisualMode {
    IDLE,
    LISTENING,
    THINKING,
    STREAMING,
    ALERT
}

@Composable
fun CyberGridBackground(
    accentColor: Color = CyanArc,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VoidObsidian,
                        DeepSpace,
                        Color(0xFF050B18)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val gridSpacing = 44.dp.toPx()
            val gridColor = accentColor.copy(alpha = 0.045f)

            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += gridSpacing
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.18f),
                    radius = size.maxDimension * 0.55f
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(size.width * 0.5f, size.height * 0.18f)
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = CyanArc,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier,
        shape = shape,
        color = GlassNavy.copy(alpha = 0.72f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.45f),
                    accentColor.copy(alpha = 0.10f),
                    NeuralViolet.copy(alpha = 0.25f)
                )
            )
        ),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun HologramCoreCanvas(
    mode: CoreVisualMode,
    onTapCore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = when (mode) {
        CoreVisualMode.IDLE -> CyanArc
        CoreVisualMode.LISTENING -> PlasmaTeal
        CoreVisualMode.THINKING -> NeuralViolet
        CoreVisualMode.STREAMING -> IceBlue
        CoreVisualMode.ALERT -> TacticalAmber
    }

    val transition = rememberInfiniteTransition(label = "jarvis_core")
    val outerRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    CoreVisualMode.THINKING, CoreVisualMode.STREAMING -> 4200
                    CoreVisualMode.LISTENING -> 6000
                    else -> 14000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_rot"
    )
    val innerRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    CoreVisualMode.THINKING -> 2800
                    CoreVisualMode.LISTENING -> 4200
                    else -> 9500
                },
                easing = LinearEasing
            )
        ),
        label = "inner_rot"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = if (mode == CoreVisualMode.LISTENING || mode == CoreVisualMode.STREAMING) 1.08f else 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mode == CoreVisualMode.IDLE) 2200 else 780,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClickLabel = "Activate JARVIS voice core",
                onClick = onTapCore
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val minDim = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (minDim / 2f) * 0.82f

            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.30f * pulse),
                        primaryColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.25f
                ),
                radius = baseRadius * 1.25f,
                center = center
            )

            // Outer segmented telemetry ring
            rotate(degrees = outerRotation, pivot = center) {
                val r = baseRadius * 0.96f
                val arcRect = Size(r * 2f, r * 2f)
                val topLeft = Offset(center.x - r, center.y - r)
                for (i in 0 until 6) {
                    drawArc(
                        color = primaryColor.copy(alpha = if (i % 2 == 0) 0.85f else 0.32f),
                        startAngle = i * 60f + 6f,
                        sweepAngle = 42f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcRect,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Middle orbital ring
            rotate(degrees = innerRotation, pivot = center) {
                val r = baseRadius * 0.72f
                val arcRect = Size(r * 2f, r * 2f)
                val topLeft = Offset(center.x - r, center.y - r)
                for (i in 0 until 4) {
                    drawArc(
                        color = IceBlue.copy(alpha = 0.75f),
                        startAngle = i * 90f + 12f,
                        sweepAngle = 62f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcRect,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Inner harmonic radial spokes
            val spokeRadiusOuter = baseRadius * 0.56f * pulse
            val spokeRadiusInner = baseRadius * 0.36f
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians((deg + outerRotation * 0.4f).toDouble())
                val start = Offset(
                    x = center.x + (cos(rad) * spokeRadiusInner).toFloat(),
                    y = center.y + (sin(rad) * spokeRadiusInner).toFloat()
                )
                val end = Offset(
                    x = center.x + (cos(rad) * spokeRadiusOuter).toFloat(),
                    y = center.y + (sin(rad) * spokeRadiusOuter).toFloat()
                )
                drawLine(
                    color = primaryColor.copy(alpha = if (deg % 45 == 0) 0.75f else 0.25f),
                    start = start,
                    end = end,
                    strokeWidth = if (deg % 45 == 0) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Luminous center core orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryColor.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.15f)
                    ),
                    center = center,
                    radius = baseRadius * 0.30f * pulse
                ),
                radius = baseRadius * 0.30f * pulse,
                center = center
            )
        }
    }
}
