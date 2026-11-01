package com.csqtt.client.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.csqtt.client.servers.ServerControl
import com.csqtt.client.servers.VpsServer
import com.csqtt.client.ui.common.ConfirmDialog
import com.csqtt.client.ui.components.MrttScreen

private val SECTIONS = listOf("Подписки", "Группы", "Маршруты", "Прокси", "Настройки", "Логи", "Статистика")

@Composable
internal fun ServerManageScreen(
    server: VpsServer,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeploy: () -> Unit,
    onUninstall: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val control = remember(server) { ServerControl(context, server) }
    var section by rememberSaveable(server.id) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    MrttScreen {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Column(modifier = Modifier.weight(1f)) {
                Text(server.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(server.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Действия с сервером") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Изменить доступ") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Установить / обновить MRTT") }, onClick = { menu = false; confirm = "deploy" })
                    DropdownMenuItem(text = { Text("Удалить MRTT с сервера") }, onClick = { menu = false; confirm = "uninstall" })
                    DropdownMenuItem(text = { Text("Убрать из списка") }, onClick = { menu = false; confirm = "delete" })
                }
            }
        }
        DeployProgressCard()
        ScrollableTabRow(
            selectedTabIndex = section,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            SECTIONS.forEachIndexed { index, title ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(title) })
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            when (section) {
                0 -> SubscriptionsSection(control, server)
                1 -> GroupsSection(control)
                2 -> RoutingSection(control)
                3 -> ProxySection(control)
                4 -> ServerSettingsSection(control)
                5 -> ServerLogsSection(control)
                else -> ServerStatsSection(control)
            }
        }
    }

    when (confirm) {
        "deploy" -> ConfirmDialog(
            title = "Установка MRTT",
            text = "Установить или обновить MRTT на ${server.host}? База подписок сохранится.",
            confirmText = "Установить",
            onConfirm = { confirm = null; onDeploy() },
            onDismiss = { confirm = null },
        )
        "uninstall" -> ConfirmDialog(
            title = "Удаление MRTT",
            text = "Удалить MRTT с ${server.host}? Все подписки на этом сервере перестанут работать.",
            confirmText = "Удалить",
            onConfirm = { confirm = null; onUninstall() },
            onDismiss = { confirm = null },
        )
        "delete" -> ConfirmDialog(
            title = "Убрать сервер",
            text = "Убрать ${server.title} из приложения? На самом VPS ничего не изменится.",
            confirmText = "Убрать",
            onConfirm = { confirm = null; onDelete() },
            onDismiss = { confirm = null },
        )
    }
}
