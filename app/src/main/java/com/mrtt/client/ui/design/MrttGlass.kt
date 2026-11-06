package com.mrtt.client.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Стеклянная поверхность: полупрозрачное тело чуть светлее сверху и один тонкий ровный ободок. */
@Composable
fun Modifier.mrttGlass(shape: Shape, tint: Color? = null): Modifier {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val high = tint?.copy(alpha = if (light) 36 / 255f else 44 / 255f)
        ?: if (light) Color.White.copy(alpha = 205 / 255f) else Color.White.copy(alpha = 15 / 255f)
    val low = tint?.copy(alpha = if (light) 20 / 255f else 26 / 255f)
        ?: if (light) Color(0xFFF8F8FA).copy(alpha = 160 / 255f) else Color.White.copy(alpha = 9 / 255f)
    val line = tint ?: if (light) Color.Black else Color.White
    val rimTop = line.copy(alpha = if (tint != null) 90 / 255f else if (light) 20 / 255f else 30 / 255f)
    val rimBottom = line.copy(alpha = if (tint != null) 50 / 255f else if (light) 12 / 255f else 14 / 255f)
    return this
        .background(Brush.verticalGradient(listOf(high, low)), shape)
        .border(0.75.dp, Brush.verticalGradient(listOf(rimTop, rimBottom)), shape)
}
