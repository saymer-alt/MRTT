package com.mrtt.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
internal fun AppBackdrop(
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val day = colors.background.luminance() > 0.5f
    // Мягкий свет под стекло днём; ночью чистый чёрный OLED.
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (!day) return@Canvas
        val light = androidx.compose.ui.graphics.Color(70, 70, 90, 26)
        listOf(Triple(0.92f, 0.1f, 0.85f), Triple(0.02f, 0.48f, 0.8f), Triple(0.8f, 0.95f, 0.9f)).forEach { (x, y, r) ->
            drawRect(
                Brush.radialGradient(
                    listOf(light, androidx.compose.ui.graphics.Color.Transparent),
                    androidx.compose.ui.geometry.Offset(size.width * x, size.height * y),
                    size.width * r,
                ),
            )
        }
    }
}
