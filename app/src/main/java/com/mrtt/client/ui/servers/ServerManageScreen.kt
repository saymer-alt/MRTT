package com.mrtt.client.ui.servers

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.DeployManager
import com.mrtt.client.servers.ServerControl
import com.mrtt.client.servers.VpsServer
import com.mrtt.client.ui.AppSectionCard
import com.mrtt.client.ui.common.ConfirmDialog
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.friendlyDeployError
import com.mrtt.client.ui.components.MrttScreen

private val SECTIONS = listOf("Конфиги", "Группы", "Маршруты", "Прокси", "Настройки", "Логи", "Статистика")

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
    val isDeploying by DeployManager.isDeploying.collectAsStateWithLifecycle()
    val target by deployTarget.collectAsStateWithLifecycle()
    val deployingHere = isDeploying && target == server.id
    // Каждая установка начинает новое поколение: разделы пересоздаются и грузят данные заново.
    var generation by remember(server.id) { mutableIntStateOf(0) }
    var wasDeploying by remember(server.id) { mutableStateOf(deployingHere) }
    LaunchedEffect(deployingHere) {
        if (wasDeploying && !deployingHere) generation++
        wasDeploying = deployingHere
    }
    var retry by remember(server.id) { mutableIntStateOf(0) }
    // null: проверяем; "": сервер отвечает; иначе причина, по которой MRTT не отвечает.
    var reachError by remember(server) { mutableStateOf<String?>(null) }
    LaunchedEffect(control, generation, retry, deployingHere) {
        if (deployingHere) return@LaunchedEffect
        reachError = null
        reachError = runCatching { control.settings() }.fold(
            onSuccess = { "" },
            onFailure = { friendlyDeployError(it.message) },
        )
        if (reachError == "" && target == server.id) DeployManager.lastResult.value = ""
    }

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
        if (target == server.id) DeployProgressCard()
        when {
            deployingHere -> return@MrttScreen
            reachError == null -> ServerStatusTile(title = "Подключаемся к серверу...", busy = true)
            reachError != "" -> ServerStatusTile(title = "Сервер не отвечает", text = reachError.orEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, shape = MrttShapes.Pill, modifier = Modifier.weight(1f)) { Text("Изменить доступ", maxLines = 1) }
                    OutlinedButton(onClick = { retry++ }, shape = MrttShapes.Pill, modifier = Modifier.weight(1f)) { Text("Повторить", maxLines = 1) }
                }
            }
        }
        if (reachError != "") return@MrttScreen
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
            key(generation) { when (section) {
                0 -> SubscriptionsSection(control, server)
                1 -> GroupsSection(control)
                2 -> RoutingSection(control)
                3 -> ProxySection(control)
                4 -> ServerSettingsSection(control)
                5 -> ServerLogsSection(control)
                else -> ServerStatsSection(control)
            } }
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
            text = "Удалить MRTT с ${server.host}? Все конфиги на этом сервере перестанут работать.",
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

@Composable
private fun ServerStatusTile(
    title: String,
    text: String = "",
    busy: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    AppSectionCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        if (text.isNotBlank()) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}
