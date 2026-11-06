package com.mrtt.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSpacing
import com.mrtt.client.ui.design.mrttGlass

/** Сплошной цвет для окон и шторок: стекло в них читалось бы плохо. */
@Composable
fun appSectionCardColor(): Color = MaterialTheme.colorScheme.surface

@Composable
fun appSectionCardBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
fun AppSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MrttSpacing.Md),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(MrttSpacing.Md),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MrttShapes.Card,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().mrttGlass(MrttShapes.Card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}
