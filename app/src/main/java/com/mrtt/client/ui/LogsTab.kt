package com.mrtt.client.ui

import androidx.compose.material.icons.outlined.Share
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.DeployManager
import com.mrtt.client.LogEntry
import com.mrtt.client.R
import com.mrtt.client.SettingsStore
import com.mrtt.client.TunnelManager
import com.mrtt.client.ui.common.shareLogFile
import com.mrtt.client.ui.components.MrttEmptyState
import com.mrtt.client.ui.components.MrttScreen
import com.mrtt.client.ui.components.MrttSettingRow
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSpacing
import com.mrtt.client.ui.design.MrttTheme
import kotlinx.coroutines.launch

import androidx.compose.foundation.BorderStroke

@Composable
fun LogsTab(settingsStore: SettingsStore) {
    val loggingEnabled by settingsStore.loggingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    MrttScreen {
        AppSectionCard(
            contentPadding = PaddingValues(horizontal = MrttSpacing.Md, vertical = MrttSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(MrttSpacing.None),
        ) {
            MrttSettingRow(
                title = stringResource(R.string.logs_enabled),
                checked = loggingEnabled,
                onCheckedChange = { enabled ->
                    TunnelManager.isLoggingEnabled = enabled
                    scope.launch {
                        settingsStore.saveLoggingEnabled(enabled)
                    }
                },
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MrttShapes.Card,
            color = MrttTheme.extendedColors.terminalBackground,
            contentColor = MrttTheme.extendedColors.terminalText,
        ) {
            LogsTerminal()
        }
    }
}

@Composable
private fun LogsTerminal() {
    val context = LocalContext.current
    val currentLogs = TunnelManager.logs
    val listState = rememberLazyListState()
    val isDeploying by DeployManager.isDeploying.collectAsStateWithLifecycle()

    LaunchedEffect(currentLogs.size, isDeploying) {
        if (isDeploying) {
            val latestDeployIndex = currentLogs.indexOfLast { it.key.startsWith("deploy_") }
            if (latestDeployIndex >= 0) listState.animateScrollToItem(latestDeployIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MrttEmptyState(
                    title = stringResource(R.string.logs_empty_title),
                    icon = Icons.Outlined.Terminal,
                    modifier = Modifier.padding(MrttSpacing.Md),
                    contentColor = MrttTheme.extendedColors.terminalText,
                    secondaryContentColor = MrttTheme.extendedColors.terminalMuted,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = MrttSpacing.Md, top = MrttSpacing.Md, end = MrttSpacing.Md, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(MrttSpacing.Xs),
            ) {
                items(
                    items = currentLogs,
                    key = { it.key },
                    contentType = { "log_line" },
                ) { entry ->
                    LogLine(entry)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { shareFullLog(context) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Поделиться полным логом",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = TunnelManager::clearLogs,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Полный журнал с данными устройства: пароли и токены уже затёрты, его можно отправить кому угодно. */
private fun shareFullLog(context: Context) {
    val text = buildString {
        appendLine("MRTT: полный лог")
        appendLine(buildSupportReport())
        appendLine()
        append(com.mrtt.client.FullLog.text().takeLast(400_000).ifBlank { "Лог пуст: подключитесь и повторите." })
    }
    context.shareLogFile("MRTT log", text)
}
