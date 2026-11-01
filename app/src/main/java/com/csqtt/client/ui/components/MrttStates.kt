package com.csqtt.client.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.csqtt.client.ui.design.MrttShapes
import com.csqtt.client.ui.design.MrttSpacing

@Composable
fun MrttLoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MrttSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MrttSpacing.Md),
    ) {
        CircularProgressIndicator()
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MrttEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MrttSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MrttSpacing.Xs),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = secondaryContentColor)
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = contentColor)
        description?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = secondaryContentColor)
        }
    }
}

@Composable
fun MrttBanner(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MrttShapes.Control,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(MrttSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(MrttSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
