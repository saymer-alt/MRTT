package com.csqtt.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csqtt.client.SettingsStore

@Composable
fun MaxTransportSettings(settingsStore: SettingsStore, onBindRequested: () -> Unit) {
    val storedGroup by settingsStore.maxGroupId.collectAsStateWithLifecycle(initialValue = "")
    val storedToken by settingsStore.maxAccessToken.collectAsStateWithLifecycle(initialValue = "")
    val ready = storedToken.isNotBlank() && storedGroup.isNotBlank()

    AppSectionCard(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("MAX", style = MaterialTheme.typography.titleMedium)
        Text(
            if (ready) "MAX привязан. Группа: $storedGroup"
            else "Войдите в MAX и откройте нужную группу. Приложение сохранит привязку автоматически.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onBindRequested, modifier = Modifier.fillMaxWidth()) {
            Text(if (ready) "Изменить привязку" else "Привязать MAX")
        }
    }
}
