package com.mrtt.client.ui.profiles

import androidx.compose.foundation.clickable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.TunnelManager
import com.mrtt.client.profiles.ConnectionProfile
import com.mrtt.client.profiles.ProfileRepository
import com.mrtt.client.showRaisedToast
import com.mrtt.client.ui.AppSectionCard
import com.mrtt.client.ui.common.ConfirmDialog
import com.mrtt.client.ui.common.MrttFormDialog
import com.mrtt.client.ui.common.FormField
import com.mrtt.client.ui.common.LinkShareDialog
import com.mrtt.client.ui.common.rememberActionRunner
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.utils.parseMrttLink

/** Вкладка «Профили»: список профилей и сабок; нажатие на профиль открывает его в «Транспорт». */
@Composable
internal fun ProfilesTab(onOpened: () -> Unit) {
    com.mrtt.client.ui.components.MrttScreen {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfilesSection(onOpened = onOpened)
        }
    }
}

/** Хост и порт адреса проверки; null, если адрес не разобрать. */
private fun pingTarget(url: String): Pair<String, Int>? = runCatching {
    val parsed = java.net.URL(url.trim())
    parsed.host.takeIf { it.isNotBlank() }?.let { it to if (parsed.port > 0) parsed.port else parsed.defaultPort }
}.getOrNull()

/** Раздел «Профили»: добавленные конфиги и подписки, свои и чужие. Выбранный профиль — то, к чему подключаемся. */
@Composable
internal fun ProfilesSection(onOpened: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val repository = remember { ProfileRepository(context) }
    val profiles by repository.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by repository.activeId.collectAsStateWithLifecycle(initialValue = "")
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val runner = rememberActionRunner()
    var adding by rememberSaveable { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renaming by remember { mutableStateOf<ConnectionProfile?>(null) }
    var deleting by remember { mutableStateOf<ConnectionProfile?>(null) }
    // id профиля -> RTT в мс, -1 — не ответил; нет ключа — ещё не мерили.
    val pings = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var pinging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ConnectionProfile?>(null) }
    var pingSettings by remember { mutableStateOf(false) }
    val settingsStore = remember { com.mrtt.client.SettingsStore(context) }
    val pingUrl by settingsStore.pingUrl.collectAsStateWithLifecycle(initialValue = com.mrtt.client.SettingsStore.DEFAULT_PING_URL)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) { repository.migrateLegacy() }

    AppSectionCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Профили", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (profiles.isNotEmpty()) {
                            androidx.compose.material3.TextButton(
                    enabled = !pinging,
                    onClick = {
                        pinging = true
                        pings.clear()
                        scope.launch {
                            val target = pingTarget(pingUrl)
                            val slots = kotlinx.coroutines.sync.Semaphore(8)
                            profiles.map { profile ->
                                async {
                                    slots.withPermit {
                                        val ms = if (profile.id == activeId && tunnelRunning) {
                                            target?.let { TunnelManager.probeThroughTunnel(it.first, it.second) }
                                        } else {
                                            parseMrttLink(profile.link)?.let { TunnelManager.probeDirect(context, it.host, it.port) }
                                        }
                                        pings[profile.id] = ms ?: -1
                                    }
                                }
                            }.awaitAll()
                            pinging = false
                        }
                    },
                ) { Text(if (pinging) "Пинг..." else "Пинг") }
                IconButton(onClick = { pingSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Адрес проверки пинга")
                }
            }
        }
        OutlinedButton(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MrttShapes.Pill,
        ) { Text("Добавить конфиг или подписку", maxLines = 1) }
        if (profiles.isEmpty()) {
            Text(
                "Добавьте конфиг mrtt:// или подписку mrttsub://: вставьте ссылку или отсканируйте QR. Конфиги и подписки своего сервера создаются в разделе «Серверы».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profiles.groupBy { it.group }.forEach { (group, items) ->
            if (group.isNotEmpty()) {
                Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeId,
                    ping = pings[profile.id],
                    onSelect = {
                        when {
                            profile.id == activeId -> onOpened()
                            tunnelRunning -> context.showRaisedToast("Отключитесь, чтобы сменить профиль")
                            else -> runner.run { repository.activate(profile); onOpened() }
                        }
                    },
                    onShare = { sharing = profile.name to profile.link },
                    onEdit = { editing = profile },
                    onRename = { renaming = profile },
                    onDelete = { deleting = profile },
                )
            }
        }
    }

    if (adding) {
        var text by rememberSaveable { mutableStateOf("") }
        MrttFormDialog(
            title = "Добавить профиль",
            onDismiss = { adding = false },
            confirmText = "Добавить",
            confirmEnabled = text.isNotBlank(),
            onConfirm = {
                runner.run {
                    when (val added = repository.import(text)) {
                        null -> context.showRaisedToast("Это не конфиг MRTT и не подписка")
                        0 -> { adding = false; context.showRaisedToast("Эти ссылки уже добавлены") }
                        else -> { adding = false; context.showRaisedToast("Добавлено профилей: $added") }
                    }
                }
            },
        ) {
            FormField(text, { text = it.trim() }, "Конфиг mrtt:// или подписка mrttsub://", singleLine = false, minLines = 2)
            Text(
                "QR-код можно отсканировать кнопкой в шапке приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    sharing?.let { (title, link) -> LinkShareDialog(title = title, link = link, onDismiss = { sharing = null }) }
    editing?.let { profile ->
        ProfileEditDialog(
            profile = profile,
            onDismiss = { editing = null },
            onSave = { link -> editing = null; runner.run("Сохранено") { repository.updateLink(profile.id, link) } },
        )
    }
    if (pingSettings) {
        var url by remember { mutableStateOf(pingUrl) }
        val valid = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }
        MrttFormDialog(
            title = "Адрес проверки",
            onDismiss = { pingSettings = false },
            confirmEnabled = valid && url.trim() != pingUrl,
            onConfirm = { pingSettings = false; runner.run { settingsStore.savePingUrl(url) } },
        ) {
            FormField(url, { url = it }, "URL для пинга")
            Text(
                "Подключённый профиль: TCP-соединение с этим адресом через туннель. Остальные: DTLS-рукопожатие с сервером напрямую по UDP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    renaming?.let { profile ->
        var name by remember(profile) { mutableStateOf(profile.name) }
        MrttFormDialog(
            title = "Переименовать",
            onDismiss = { renaming = null },
            confirmEnabled = name.isNotBlank(),
            onConfirm = { renaming = null; runner.run { repository.rename(profile.id, name) } },
        ) { FormField(name, { name = it.take(64) }, "Название") }
    }
    deleting?.let { profile ->
        ConfirmDialog(
            title = "Удалить профиль",
            text = "Удалить «${profile.name}» из приложения? На сервере конфиг останется.",
            confirmText = "Удалить",
            onConfirm = { deleting = null; runner.run { repository.delete(profile.id) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ConnectionProfile,
    active: Boolean,
    ping: Int?,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val link = remember(profile.link) { parseMrttLink(profile.link) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (active) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (active) "Активный профиль" else null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(profile.name, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                link?.let { "${it.host}:${it.port}" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ping != null) {
            Text(
                if (ping < 0) "нет ответа" else "$ping мс",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    ping < 0 -> MaterialTheme.colorScheme.error
                    ping < 150 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Действия с профилем")
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Поделиться: ссылка и QR") }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Изменить хеши и данные") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Свои VK-хеши, ID группы MAX и комната Jitsi у добавленного конфига: ссылка пересобирается. */
@Composable
private fun ProfileEditDialog(profile: ConnectionProfile, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val link = remember(profile.link) { parseMrttLink(profile.link) } ?: run { onDismiss(); return }
    var hashes by remember { mutableStateOf(link.hashes.joinToString(",")) }
    var maxGroup by remember { mutableStateOf(link.maxGroupId) }
    var jitsiRoom by remember { mutableStateOf(link.jitsiRoom.takeIf { it.isNotBlank() }?.let { "https://$it" }.orEmpty()) }
    var transport by remember { mutableStateOf(link.transport) }
    val hashList = com.mrtt.client.ui.common.splitVkHashes(hashes)
    val room = if (jitsiRoom.isBlank()) "" else com.mrtt.client.ui.utils.parseJitsiRoomInput(jitsiRoom)
    val error = when {
        hashList.size > 6 -> "Не больше 6 хешей"
        hashList.any { it.length < 16 } -> "Хеш короче 16 символов"
        maxGroup.isNotBlank() && !com.mrtt.client.ui.utils.isValidMaxGroupId(maxGroup.trim()) -> "Неверный ID группы MAX"
        room == null -> "Неверная ссылка на комнату Jitsi"
        else -> null
    }
    val rebuilt = if (error == null) {
        runCatching {
            com.mrtt.client.ui.utils.formatMrttLink(
                host = link.host,
                peer = link.port,
                password = link.password,
                hashes = hashList,
                // Свои хеши заменяют VK-аккаунт сервера из ссылки, без хешей он остаётся.
                token = if (hashList.isEmpty()) link.vkAccessToken else "",
                maxGroupId = maxGroup.trim(),
                jitsiRoom = room.orEmpty(),
                jitsiSeats = if (room == link.jitsiRoom) link.jitsiSeats else 0,
                transport = transport,
            )
        }.getOrNull()
    } else {
        null
    }
    MrttFormDialog(
        title = profile.name,
        onDismiss = onDismiss,
        confirmEnabled = rebuilt != null && parseMrttLink(rebuilt) != link,
        onConfirm = { rebuilt?.let(onSave) },
    ) {
        com.mrtt.client.ui.CompactDropdownSetting(
            title = "Транспорт в ссылке",
            selectedKey = transport,
            options = listOf("" to "Не задан", "vk" to "VK", "jitsi" to "Jitsi"),
            enabled = true,
            onSelected = { transport = it },
        )
        com.mrtt.client.ui.common.VkHashesButton(hashes) { hashes = it }
        FormField(maxGroup, { maxGroup = it.trim() }, "ID группы MAX")
        FormField(jitsiRoom, { jitsiRoom = it }, "Ссылка на комнату Jitsi")
        Text(
            error ?: "Сервер и пароль не меняются. MAX в ссылку не попадает: его данные приватные.",
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
