package com.csqtt.client.ui.profiles

import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csqtt.client.TunnelManager
import com.csqtt.client.profiles.ConnectionProfile
import com.csqtt.client.profiles.ProfileRepository
import com.csqtt.client.profiles.Subscription
import com.csqtt.client.profiles.encodeSubscription
import com.csqtt.client.servers.ServerControl
import com.csqtt.client.servers.ServerRepository
import com.csqtt.client.showRaisedToast
import com.csqtt.client.ui.AppSectionCard
import com.csqtt.client.ui.common.ConfirmDialog
import com.csqtt.client.ui.common.MrttFormDialog
import com.csqtt.client.ui.common.FormField
import com.csqtt.client.ui.common.LinkShareDialog
import com.csqtt.client.ui.common.rememberActionRunner
import com.csqtt.client.ui.design.MrttShapes
import com.csqtt.client.ui.servers.CheckRow
import com.csqtt.client.ui.utils.parseMrttLink

/** Вкладка «Профили»: список профилей и сабок; нажатие на профиль открывает его в «Транспорт». */
@Composable
internal fun ProfilesTab(onOpened: () -> Unit) {
    com.csqtt.client.ui.components.MrttScreen {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfilesSection(onOpened = onOpened)
        }
    }
}

/** Системный ping: на Android доступен без root; null, если хост не ответил. */
private suspend fun pingHost(host: String): Int? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val process = ProcessBuilder("ping", "-c", "1", "-W", "2", host).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Regex("time=([0-9.]+)").find(output)?.groupValues?.get(1)?.toDouble()?.toInt()
    }.getOrNull()
}

/** Раздел «Профили»: добавленные ссылки и сабки, свои и чужие. Выбранный профиль — то, к чему подключаемся. */
@Composable
internal fun ProfilesSection(onOpened: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val repository = remember { ProfileRepository(context) }
    val profiles by repository.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by repository.activeId.collectAsStateWithLifecycle(initialValue = "")
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val runner = rememberActionRunner()
    var adding by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renaming by remember { mutableStateOf<ConnectionProfile?>(null) }
    var deleting by remember { mutableStateOf<ConnectionProfile?>(null) }
    // id профиля -> RTT в мс, -1 — не ответил; нет ключа — ещё не мерили.
    val pings = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var pinging by remember { mutableStateOf(false) }
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
                            profiles.forEach { profile ->
                                val host = parseMrttLink(profile.link)?.host ?: return@forEach
                                pings[profile.id] = pingHost(host) ?: -1
                            }
                            pinging = false
                        }
                    },
                ) { Text(if (pinging) "Пинг..." else "Пинг") }
            }
        }
        if (profiles.isEmpty()) {
            Text(
                "Добавьте ссылку csqttr:// или сабку: вставьте её, отсканируйте QR или добавьте подписку со своего сервера в разделе «Серверы».",
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
                    onRename = { renaming = profile },
                    onDelete = { deleting = profile },
                )
            }
        }
        // Одна высота у обеих кнопок, даже если подпись одной из них переносится.
        Row(
            modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonModifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
            val padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            OutlinedButton(onClick = { adding = true }, modifier = buttonModifier, shape = MrttShapes.Pill, contentPadding = padding) {
                Text("Добавить", maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            OutlinedButton(onClick = { building = true }, modifier = buttonModifier, shape = MrttShapes.Pill, contentPadding = padding) {
                Text("Создать сабку", maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    if (adding) {
        var text by remember { mutableStateOf("") }
        MrttFormDialog(
            title = "Добавить профиль",
            onDismiss = { adding = false },
            confirmText = "Добавить",
            confirmEnabled = text.isNotBlank(),
            onConfirm = {
                runner.run {
                    when (val added = repository.import(text)) {
                        null -> context.showRaisedToast("Это не ссылка MRTT и не сабка")
                        0 -> { adding = false; context.showRaisedToast("Эти ссылки уже добавлены") }
                        else -> { adding = false; context.showRaisedToast("Добавлено профилей: $added") }
                    }
                }
            },
        ) {
            FormField(text, { text = it.trim() }, "Ссылка csqttr:// или сабка mrttsub://", singleLine = false, minLines = 2)
            Text(
                "QR-код можно отсканировать кнопкой в шапке приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (building) {
        SubscriptionBuilderDialog(
            profiles = profiles,
            onDismiss = { building = false },
            onBuilt = { subscription ->
                building = false
                sharing = subscription.name to encodeSubscription(subscription)
            },
        )
    }
    sharing?.let { (title, link) -> LinkShareDialog(title = title, link = link, onDismiss = { sharing = null }) }
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
            text = "Удалить «${profile.name}» из приложения? На сервере подписка останется.",
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
                listOfNotNull(
                    link?.let { "${it.host}:${it.port}" },
                    when {
                        link == null -> null
                        link.maxGroupId.isNotEmpty() -> "MAX"
                        link.jitsiRoom.isNotEmpty() -> "Jitsi"
                        link.vkAccessToken.isNotEmpty() -> "VK-токен"
                        link.hashes.isNotEmpty() -> "VK-хеши: ${link.hashes.size}"
                        else -> null
                    },
                ).joinToString(" · "),
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
                DropdownMenuItem(text = { Text("Ссылка и QR") }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Сабка собирается из любых ссылок: своих профилей и подписок с ваших серверов. */
@Composable
private fun SubscriptionBuilderDialog(
    profiles: List<ConnectionProfile>,
    onDismiss: () -> Unit,
    onBuilt: (Subscription) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val servers by remember { ServerRepository(context).servers }.collectAsStateWithLifecycle(initialValue = emptyList())
    var name by remember { mutableStateOf("") }
    // ключ — ссылка, значение — имя внутри сабки
    var chosen by remember { mutableStateOf(linkedMapOf<String, String>()) }
    val serverLinks = remember { mutableStateOf<Map<String, List<Pair<String, String>>>>(emptyMap()) }
    val serverErrors = remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(servers) {
        servers.forEach { server ->
            if (server.id in serverLinks.value || server.id in serverErrors.value) return@forEach
            runCatching { ServerControl(context, server).links() }
                .onSuccess { links -> serverLinks.value = serverLinks.value + (server.id to links.map { (n, l) -> "$n · ${server.title}" to l }) }
                .onFailure { serverErrors.value = serverErrors.value + (server.id to (it.message ?: "недоступен")) }
        }
    }

    fun toggle(label: String, link: String) {
        chosen = LinkedHashMap(chosen).apply { if (containsKey(link)) remove(link) else put(link, label) }
    }

    MrttFormDialog(
        title = "Новая сабка",
        onDismiss = onDismiss,
        confirmText = "Создать",
        confirmEnabled = name.isNotBlank() && chosen.isNotEmpty(),
        onConfirm = { onBuilt(Subscription(name.trim(), chosen.map { (link, label) -> label to link })) },
    ) {
        FormField(name, { name = it.take(64) }, "Название сабки")
        Text(
            "Сабка — одна ссылка и QR-код, внутри которых выбранные подключения. Тот, кто её добавит, получит их все в разделе «Профили».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profiles.isNotEmpty()) {
            Text("Мои профили", fontWeight = FontWeight.SemiBold)
            profiles.forEach { profile -> CheckRow(profile.name, profile.link in chosen) { toggle(profile.name, profile.link) } }
        }
        servers.forEach { server ->
            Text(server.title, fontWeight = FontWeight.SemiBold)
            serverErrors.value[server.id]?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            val links = serverLinks.value[server.id]
            if (links == null && server.id !in serverErrors.value) {
                Text("Загрузка...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            links?.forEach { (label, link) -> CheckRow(label, link in chosen) { toggle(label, link) } }
        }
    }
}
