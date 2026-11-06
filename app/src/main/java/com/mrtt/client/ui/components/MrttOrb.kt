package com.mrtt.client.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Кнопка подключения: капля, чей край идёт медленной волной, сильнее при подключении, и кольца, расходящиеся от неё.
 * Пока идёт подключение, по краю бежит яркий штрих. Между состояниями всё переходит плавно, ничего не прыгает.
 */
@Composable
internal fun MrttOrb(
    connected: Boolean,
    connecting: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wave by animateFloatAsState(if (connected) 1f else if (connecting) 0.6f else 0.3f, tween(700), label = "wave")
    val spin by animateFloatAsState(if (connecting) 1f else 0f, tween(500), label = "spin")
    val lit by animateFloatAsState(if (connected || connecting) 1f else 0f, tween(600), label = "lit")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )

    var angle by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableLongStateOf(0L) }
    val currentSpin by rememberUpdatedState(spin)
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = (now - last).coerceIn(1L, 100L).toFloat()
            last = now
            clock += dt.toLong()
            angle = (angle + dt * (0.14f + 0.24f * currentSpin)) % 360f
            phase = (phase + dt * (0.00035f + 0.001f * currentSpin)) % (2f * PI.toFloat())
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val day = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val edge = remember { Path() }

    Canvas(
        modifier = modifier.clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClickLabel = description,
            onClick = onClick,
        ),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * 0.33f * press
        val line = 1.dp.toPx()

        // Кольца выходят от края и гаснут, не дойдя до подписи.
        if (lit > 0.01f) {
            val cycle = 3400f
            repeat(3) { index ->
                val travel = ((clock % cycle.toLong()) / cycle + index / 3f) % 1f
                drawCircle(
                    color = ink.copy(alpha = (1f - travel) * (1f - travel) * 0.27f * wave * lit),
                    radius = base * (1.08f + 0.5f * travel),
                    center = center,
                    style = Stroke(width = line),
                )
            }
            // Мягкий свет за каплей растёт с подключением.
            drawCircle(
                brush = Brush.radialGradient(listOf(ink.copy(alpha = 0.13f * lit), Color.Transparent), center, base * 1.55f),
                radius = base * 1.55f,
                center = center,
            )
        }

        edge.rewind()
        val steps = 144
        for (step in 0..steps) {
            val theta = step * 2.0 * PI / steps
            val swell = 1f + wave * 0.06f * sin(8.0 * theta + phase * 8).toFloat() + wave * 0.02f * sin(3.0 * theta - phase * 3).toFloat()
            val x = center.x + base * swell * cos(theta).toFloat()
            val y = center.y + base * swell * sin(theta).toFloat()
            if (step == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
        }
        edge.close()

        // Капля: тонкая сверху, свет собирается низко в теле.
        drawPath(
            path = edge,
            brush = Brush.radialGradient(
                listOf(
                    if (day) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.24f),
                    if (day) Color(0xFFE8E8F0).copy(alpha = 0.47f) else Color.White.copy(alpha = 0.06f),
                ),
                Offset(center.x, center.y + base * 0.62f),
                base * 1.5f,
            ),
            alpha = if (day) 1f else 0.5f + 0.5f * lit,
        )
        // Яркое пятно слева вверху и отсвет под ним.
        val light = Math.toRadians(241.0).toFloat()
        drawPath(
            path = edge,
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (day) 0.78f else 0.24f), Color.Transparent),
                Offset(center.x + base * 0.7f * cos(light), center.y + base * 0.7f * sin(light)),
                base * 0.8f,
            ),
        )
        drawPath(
            path = edge,
            brush = Brush.radialGradient(
                listOf((if (day) Color.Black else Color.White).copy(alpha = if (day) 0.09f else 0.31f), Color.Transparent),
                Offset(center.x, center.y + base * 1.05f),
                base * 0.85f,
            ),
            alpha = if (day) 1f else 0.42f + 0.58f * lit,
        )
        val rim = if (day) Color.Black else Color.White
        drawPath(
            path = edge,
            brush = Brush.verticalGradient(
                listOf(rim.copy(alpha = if (day) 0.13f else 0.75f), rim.copy(alpha = if (day) 0.06f else 0.18f)),
                center.y - base,
                center.y + base,
            ),
            style = Stroke(width = 1.2f * line),
        )

        // Штрих, бегущий по краю при подключении.
        if (spin > 0.01f) {
            val ring = base * 1.14f
            rotate(angle * 2f, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.62f to ink,
                        0.63f to Color.Transparent,
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 225f,
                    useCenter = false,
                    topLeft = Offset(center.x - ring, center.y - ring),
                    size = Size(ring * 2f, ring * 2f),
                    alpha = spin,
                    style = Stroke(width = 3f * line, cap = StrokeCap.Round),
                )
            }
        }

        // Значок питания.
        val glyph = base * 0.34f
        val glyphColor = lerp(muted, ink, lit)
        val glyphStroke = base * 0.055f
        drawArc(
            color = glyphColor,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(center.x - glyph, center.y - glyph),
            size = Size(glyph * 2f, glyph * 2f),
            style = Stroke(width = glyphStroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = glyphColor,
            start = Offset(center.x, center.y - glyph * 1.3f),
            end = Offset(center.x, center.y - glyph * 0.15f),
            strokeWidth = glyphStroke,
            cap = StrokeCap.Round,
        )
    }
}
