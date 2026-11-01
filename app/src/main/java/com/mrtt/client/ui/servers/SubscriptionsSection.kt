package com.mrtt.client.ui.servers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrtt.client.profiles.ProfileRepository
import com.mrtt.client.profiles.Subscription
import com.mrtt.client.profiles.encodeSubscription
import com.mrtt.client.servers.ServerClient
import com.mrtt.client.servers.ServerControl
import com.mrtt.client.servers.ServerGroup
import com.mrtt.client.servers.VpsServer
import com.mrtt.client.showRaisedToast
import com.mrtt.client.ui.AppSectionCard
import com.mrtt.client.ui.CompactDropdownSetting
import com.mrtt.client.ui.common.ConfirmDialog
import com.mrtt.client.ui.common.MrttFormDialog
import com.mrtt.client.ui.common.FormField
import com.mrtt.client.ui.common.LinkShareDialog
import com.mrtt.client.ui.common.formatBytes
import com.mrtt.client.ui.common.formatDate
import com.mrtt.client.ui.common.remainingDays
import com.mrtt.client.ui.common.rememberActionRunner
import com.mrtt.client.ui.components.MrttSettingRow
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSizes

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubscriptionsSection(control: ServerControl, server: VpsServer) {
    val runner = rememberActionRunner()
    val context = LocalContext.current.applicationContext
    val profiles = remember { ProfileRepository(context) }
    var clients by remember { mutableStateOf<List<ServerClient>?>(null) }
    var groups by remember { mutableStateOf<List<ServerGroup>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var editing by remember { mutableStateOf<ServerClient?>(null) }
    var creating by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<ServerClient?>(null) }
    var bundling by remember { mutableStateOf(false) }
    var bundleShare by remember { mutableStateOf<Pair<String, String>?>(null) }
    var batchGroup by remember { mutableStateOf(false) }
    var batchDelete by remember { mutableStateOf(false) }

    LaunchedEffect(control, reload) {
        runCatching { control.clients() to control.groups() }
            .onSuccess { (loadedClients, loadedGroups) ->
                clients = loadedClients
                groups = loadedGroups
                error = null
                selected.retainAll(loadedClients.map { it.password }.toSet())
            }
            .onFailure { error = it.message ?: "Сервер недоступен" }
    }
    fun refresh() { reload++ }

    val visible = clients.orEmpty()
        .filter { groupFilter == null || it.groupId == groupFilter }
        .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
        .sortedWith(compareBy({ !it.isOwner }, { it.name.lowercase() }))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        error?.let { message ->
            AppSectionCard {
                Text("Не удалось связаться с сервером", fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(
                    "Проверьте SSH-доступ и что MRTT установлен на сервере (меню ⋮ → Установить).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = ::refresh, shape = MrttShapes.Pill) { Text("Повторить") }
            }
        }
        if (clients == null && error == null) {
            Text("Загрузка...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (clients != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { creating = true },
                    modifier = Modifier.weight(1f).height(MrttSizes.ControlHeight),
                    shape = MrttShapes.Pill,
                ) { Text("Создать конфиг", maxLines = 1) }
                OutlinedButton(
                    onClick = { bundling = true },
                    enabled = clients.orEmpty().isNotEmpty(),
                    modifier = Modifier.weight(1f).height(MrttSizes.ControlHeight),
                    shape = MrttShapes.Pill,
                ) { Text("Создать подписку", maxLines = 1) }
            }
            FormField(search, { search = it }, "Поиск по имени")
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = groupFilter == null, onClick = { groupFilter = null }, label = { Text("Все") })
                FilterChip(selected = groupFilter == "", onClick = { groupFilter = "" }, label = { Text("Без группы") })
                groups.forEach { group ->
                    FilterChip(selected = groupFilter == group.id, onClick = { groupFilter = group.id }, label = { Text(group.name) })
                }
            }
            if (selected.isNotEmpty()) {
                AppSectionCard {
                    Text("Выбрано: ${selected.size}", fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val chosen = selected.toList()
                        AssistChip(onClick = { runner.run("Включены", ::refresh) { control.batch("toggle", chosen) { put("active", true) } } }, label = { Text("Вкл") })
                        AssistChip(onClick = { runner.run("Выключены", ::refresh) { control.batch("toggle", chosen) { put("active", false) } } }, label = { Text("Выкл") })
                        AssistChip(onClick = { runner.run("Продлены на 30 дней", ::refresh) { control.batch("extend", chosen) { put("days", 30) } } }, label = { Text("+30 дней") })
                        AssistChip(onClick = { runner.run("Трафик сброшен", ::refresh) { control.batch("reset-traffic", chosen) } }, label = { Text("Сброс трафика") })
                        AssistChip(onClick = { batchGroup = true }, label = { Text("В группу") })
                        AssistChip(onClick = { batchDelete = true }, label = { Text("Удалить") })
                        AssistChip(onClick = { selected.clear() }, label = { Text("Снять выбор") })
                    }
                }
            }
            visible.forEach { client ->
                SubscriptionCard(
                    client = client,
                    groupName = groups.firstOrNull { it.id == client.groupId }?.name,
                    selected = client.password in selected,
                    onToggleSelect = {
                        if (client.isOwner) return@SubscriptionCard
                        if (client.password in selected) selected.remove(client.password) else selected.add(client.password)
                    },
                    onOpen = { editing = client },
                )
            }
            if (visible.isEmpty()) {
                Text("Конфигов нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (creating) {
        ClientEditDialog(
            client = null,
            groups = groups,
            onDismiss = { creating = false },
            onSave = { form ->
                creating = false
                runner.run("Конфиг создан", ::refresh) {
                    control.createClient(form.name, form.groupId, form.days, form.workers, form.maxDevices)
                }
            },
        )
    }
    editing?.let { client ->
        ClientEditDialog(
            client = client,
            groups = groups,
            onDismiss = { editing = null },
            onSave = { form ->
                editing = null
                runner.run("Сохранено", ::refresh) {
                    control.updateClient(client, form.name, form.groupId, form.days, form.workers, form.maxDevices, form.vkHashes, form.vkToken)
                }
            },
            onShare = { sharing = client },
            onAddToProfiles = {
                runner.run {
                    val added = profiles.import(client.link, "${client.name} · ${server.title}")
                    context.showRaisedToast(if (added == 0) "Уже есть в профилях" else "Добавлено в профили")
                }
            },
            onToggle = { editing = null; runner.run("Статус изменён", ::refresh) { control.toggleClient(client.password) } },
            onExtend = { editing = null; runner.run("Продлено на 30 дней", ::refresh) { control.extendClient(client.password) } },
            onResetTraffic = { editing = null; runner.run("Трафик сброшен", ::refresh) { control.resetTraffic(client.password) } },
            onDelete = { editing = null; runner.run("Конфиг удалён", ::refresh) { control.deleteClient(client.password) } },
            onUnbind = { deviceId ->
                runner.run("Устройство отвязано", ::refresh) { control.unbindDevice(client.password, deviceId) }
                editing = client.copy(
                    devices = if (deviceId.isEmpty()) emptyList() else client.devices.filterNot { it.id == deviceId },
                )
            },
        )
    }
    sharing?.let { client ->
        LinkShareDialog(title = client.name, link = client.link, onDismiss = { sharing = null })
    }
    if (bundling) {
        SubscriptionBuilderDialog(
            clients = clients.orEmpty(),
            onDismiss = { bundling = false },
            onBuilt = { subscription ->
                bundling = false
                bundleShare = subscription.name to encodeSubscription(subscription)
            },
        )
    }
    bundleShare?.let { (title, link) -> LinkShareDialog(title = title, link = link, onDismiss = { bundleShare = null }) }
    if (batchGroup) {
        var target by remember { mutableStateOf("") }
        MrttFormDialog(
            title = "Перенести в группу",
            onDismiss = { batchGroup = false },
            confirmText = "Перенести",
            onConfirm = {
                batchGroup = false
                val chosen = selected.toList()
                runner.run("Перенесено", ::refresh) { control.batch("group", chosen) { put("group_id", target) } }
            },
        ) {
            CompactDropdownSetting(
                title = "Группа",
                selectedKey = target,
                options = listOf("" to "Без группы") + groups.map { it.id to it.name },
                enabled = true,
                onSelected = { target = it },
            )
        }
    }
    if (batchDelete) {
        ConfirmDialog(
            title = "Удалить конфиги",
            text = "Удалить выбранные конфиги (${selected.size})? Ссылки перестанут работать.",
            confirmText = "Удалить",
            onConfirm = {
                batchDelete = false
                val chosen = selected.toList()
                selected.clear()
                runner.run("Удалено", ::refresh) { control.batch("delete", chosen) }
            },
            onDismiss = { batchDelete = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionCard(
    client: ServerClient,
    groupName: String?,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    AppSectionCard(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onToggleSelect),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!client.isOwner) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(client.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        when {
                            client.isOwner -> "владелец"
                            !client.active && client.expires != 0L && client.expires * 1000 < System.currentTimeMillis() -> "истекла"
                            !client.active -> "выключена"
                            else -> "активна"
                        },
                        groupName,
                        if (client.expires == 0L) "бессрочно" else "до ${formatDate(client.expires)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (client.active || client.isOwner) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
        val online = client.devices.count { it.online }
        val limit = if (client.maxDevices == 0) "∞" else client.maxDevices.toString()
        Text(
            "Устройства: ${client.devices.size}/$limit (в сети $online) · ↑ ${formatBytes(client.up)} ↓ ${formatBytes(client.down)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal data class ClientForm(
    val name: String,
    val groupId: String,
    val days: Int,
    val workers: Int,
    val maxDevices: Int,
    val vkHashes: String,
    val vkToken: Boolean,
)

@Composable
private fun ClientEditDialog(
    client: ServerClient?,
    groups: List<ServerGroup>,
    onDismiss: () -> Unit,
    onSave: (ClientForm) -> Unit,
    onShare: () -> Unit = {},
    onAddToProfiles: () -> Unit = {},
    onToggle: () -> Unit = {},
    onExtend: () -> Unit = {},
    onResetTraffic: () -> Unit = {},
    onDelete: () -> Unit = {},
    onUnbind: (String) -> Unit = {},
) {
    val owner = client?.isOwner == true
    var name by remember { mutableStateOf(client?.name.orEmpty()) }
    var groupId by remember { mutableStateOf(client?.groupId.orEmpty()) }
    var days by remember { mutableStateOf(client?.let { remainingDays(it.expires) }?.toString() ?: "30") }
    var workers by remember { mutableStateOf((client?.workers ?: 0).toString()) }
    // Новые подписки по умолчанию без лимита устройств.
    var maxDevices by remember { mutableStateOf((client?.maxDevices ?: 0).toString()) }
    var vkHashes by remember { mutableStateOf(client?.vkHashes.orEmpty()) }
    var vkToken by remember { mutableStateOf(client?.vkToken ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val workersValue = workers.toIntOrNull() ?: 0
    val valid = (owner || name.isNotBlank()) && (days.toIntOrNull() ?: -1) in 0..3650 &&
        (workersValue == 0 || workersValue in 3..100) && (maxDevices.toIntOrNull() ?: -1) in 0..1000
    val changed = client == null || name != client.name || groupId != client.groupId ||
        days != remainingDays(client.expires).toString() || workers != client.workers.toString() ||
        maxDevices != client.maxDevices.toString() || vkHashes != client.vkHashes || vkToken != client.vkToken

    MrttFormDialog(
        title = if (client == null) "Новый конфиг" else client.name,
        onDismiss = onDismiss,
        confirmEnabled = valid && changed,
        onConfirm = {
            onSave(
                ClientForm(
                    name = name.trim(),
                    groupId = groupId,
                    days = days.toInt(),
                    workers = workersValue,
                    maxDevices = maxDevices.toInt(),
                    vkHashes = vkHashes.trim(),
                    vkToken = vkToken,
                ),
            )
        },
    ) {
        if (!owner) {
            FormField(name, { name = it.take(128) }, "Имя")
            CompactDropdownSetting(
                title = "Группа",
                selectedKey = groupId,
                options = listOf("" to "Без группы") + groups.map { it.id to it.name },
                enabled = true,
                onSelected = { groupId = it },
            )
            FormField(days, { days = it }, "Срок, дней (0 — бессрочно)", numeric = true)
        }
        FormField(workers, { workers = it }, "Потоки (0 — как в приложении, 3–100)", numeric = true)
        FormField(maxDevices, { maxDevices = it }, "Устройств (0 — без лимита)", numeric = true)
        if (client != null) {
            var hashSource by remember {
                mutableStateOf(
                    when {
                        vkToken -> "server"
                        vkHashes.isNotBlank() -> "manual"
                        else -> "client"
                    },
                )
            }
            CompactDropdownSetting(
                title = "Хеши VK",
                selectedKey = hashSource,
                options = listOf(
                    "client" to "Аккаунт клиента",
                    "server" to "Аккаунт сервера",
                    "manual" to "Свои хеши",
                ),
                enabled = true,
                onSelected = { source ->
                    hashSource = source
                    vkToken = source == "server"
                    if (source != "manual") vkHashes = ""
                },
            )
            when (hashSource) {
                "manual" -> com.mrtt.client.ui.common.VkHashesButton(vkHashes) { vkHashes = it }
                "server" -> Text(
                    "Хеши создаются автоматически с VK-аккаунта, привязанного в настройках сервера.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    "Приложение само создаёт хеши с VK-аккаунта, привязанного у клиента во вкладке «Транспорт».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Устройства", fontWeight = FontWeight.SemiBold)
            if (client.devices.isEmpty()) {
                Text("Нет привязанных устройств", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            client.devices.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            (if (device.online) "● " else "○ ") + device.name.ifBlank { device.id.take(8) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${device.ip} · ${if (device.lastSeen > 0) formatDate(device.lastSeen) else "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onUnbind(device.id) }) { Text("Отвязать") }
                }
            }
            if (client.devices.isNotEmpty()) {
                TextButton(onClick = { onUnbind("") }) { Text("Отвязать все устройства") }
            }
            Text(
                "Трафик: ↑ ${formatBytes(client.up)} ↓ ${formatBytes(client.down)} · сессий ${client.activeSessions}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(onClick = onShare, label = { Text("Ссылка и QR") })
                AssistChip(onClick = onAddToProfiles, label = { Text("В мои профили") })
                if (!owner) {
                    AssistChip(onClick = onToggle, label = { Text(if (client.active) "Выключить" else "Включить") })
                    AssistChip(onClick = onExtend, label = { Text("+30 дней") })
                }
                AssistChip(onClick = onResetTraffic, label = { Text("Сброс трафика") })
                if (!owner) AssistChip(onClick = { confirmDelete = true }, label = { Text("Удалить") })
            }
        }
    }
    if (confirmDelete && client != null) {
        ConfirmDialog(
            title = "Удалить конфиг",
            text = "Удалить «${client.name}»? Ссылка перестанет работать на всех устройствах.",
            confirmText = "Удалить",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Подписка: одна ссылка и QR-код, внутри которых выбранные конфиги этого сервера. */
@Composable
private fun SubscriptionBuilderDialog(
    clients: List<ServerClient>,
    onDismiss: () -> Unit,
    onBuilt: (Subscription) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    MrttFormDialog(
        title = "Новая подписка",
        onDismiss = onDismiss,
        confirmText = "Создать",
        confirmEnabled = name.isNotBlank() && chosen.isNotEmpty(),
        onConfirm = {
            onBuilt(Subscription(name.trim(), clients.filter { it.password in chosen }.map { it.name to it.link }))
        },
    ) {
        FormField(name, { name = it.take(64) }, "Название подписки")
        Text(
            "Подписка: одна ссылка и QR-код, внутри которых выбранные конфиги. Тот, кто её добавит, получит их все в разделе «Профили».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        clients.forEach { client ->
            CheckRow(client.name, client.password in chosen) {
                if (client.password in chosen) chosen.remove(client.password) else chosen.add(client.password)
            }
        }
    }
}
