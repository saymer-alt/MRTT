package com.mrtt.client.ui.servers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import com.mrtt.client.ui.components.MrttGlassButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.servers.DNS_PROVIDERS
import com.mrtt.client.servers.ProxyProfile
import com.mrtt.client.servers.ServerControl
import com.mrtt.client.servers.ServerSettings
import com.mrtt.client.showRaisedToast
import com.mrtt.client.ui.AppSectionCard
import com.mrtt.client.ui.CompactDropdownSetting
import com.mrtt.client.ui.common.ConfirmDialog
import com.mrtt.client.ui.common.MrttFormDialog
import com.mrtt.client.ui.common.FormField
import com.mrtt.client.ui.common.formatBytes
import com.mrtt.client.ui.common.rememberActionRunner
import com.mrtt.client.ui.common.shareLogFile
import com.mrtt.client.ui.components.MrttSettingRow
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSizes
import com.mrtt.client.ui.utils.generate16CharPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProxySection(control: ServerControl) {
    val runner = rememberActionRunner()
    var reload by remember { mutableStateOf(0) }
    val (state, error) = rememberServerData(control, reload) { proxy() }
    var editing by remember { mutableStateOf<ProxyProfile?>(null) }
    fun refresh() { reload++ }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Локальный SOCKS5 на сервере: весь трафик клиентов можно отправить через прокси, запущенный на этом же VPS (например, каскад).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LoadState(state != null, error)
        val proxy = state ?: return@Column
        AppSectionCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (proxy.activeId.isEmpty()) "Маршрут через прокси выключен" else "Маршрут через прокси включён", fontWeight = FontWeight.SemiBold)
            if (proxy.activeId.isNotEmpty()) {
                Text(
                    if (proxy.routeActive) "Маршрут применён" else "Маршрут применяется...",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!proxy.portListening) Text("Порт прокси не слушает", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (proxy.healthError.isNotBlank()) Text(proxy.healthError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { runner.run("Прокси выключен", ::refresh) { control.deactivateProxy() } }, shape = MrttShapes.Pill) {
                    Text("Выключить")
                }
            }
        }
        Button(onClick = { editing = ProxyProfile() }, modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight), shape = MrttShapes.Pill) {
            Text("Добавить профиль SOCKS5")
        }
        proxy.profiles.forEach { profile ->
            AppSectionCard(modifier = Modifier.clickable { editing = profile }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name + if (profile.id == proxy.activeId) " · активен" else "", fontWeight = FontWeight.SemiBold)
                Text("127.0.0.1:${profile.port}" + if (profile.username.isNotEmpty()) " · с авторизацией" else "", style = MaterialTheme.typography.bodySmall)
                if (profile.id != proxy.activeId) {
                    TextButton(onClick = { runner.run("Прокси включён", ::refresh) { control.activateProxy(profile.id) } }) { Text("Сделать активным") }
                }
            }
        }
    }

    editing?.let { profile ->
        var name by remember(profile) { mutableStateOf(profile.name) }
        var port by remember(profile) { mutableStateOf(profile.port.toString()) }
        var username by remember(profile) { mutableStateOf(profile.username) }
        var password by remember(profile) { mutableStateOf(profile.password) }
        MrttFormDialog(
            title = if (profile.id.isEmpty()) "Новый SOCKS5" else profile.name,
            onDismiss = { editing = null },
            confirmEnabled = (port.toIntOrNull() ?: 0) in 1..65535 && (
                profile.id.isEmpty() || name.trim() != profile.name || port != profile.port.toString() ||
                    username != profile.username || password != profile.password
                ),
            onConfirm = {
                editing = null
                runner.run("Сохранено", ::refresh) {
                    control.saveProxy(profile.copy(name = name.trim(), port = port.toInt(), username = username, password = password))
                }
            },
        ) {
            FormField(name, { name = it.take(64) }, "Название")
            FormField(port, { port = it }, "Порт на 127.0.0.1", numeric = true)
            FormField(username, { username = it }, "Логин (необязательно)")
            FormField(password, { password = it }, "Пароль (необязательно)", secret = true)
            if (profile.id.isNotEmpty()) {
                TextButton(onClick = { editing = null; runner.run("Удалено", ::refresh) { control.deleteProxy(profile.id) } }) {
                    Text("Удалить профиль", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
internal fun ServerSettingsSection(control: ServerControl, server: com.mrtt.client.servers.VpsServer) {
    val runner = rememberActionRunner()
    val context = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    val (loaded, error) = rememberServerData(control, reload) { settings() }
    var form by remember(loaded) { mutableStateOf(loaded ?: ServerSettings()) }
    var confirmReboot by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var bindingVk by remember { mutableStateOf(false) }
    val repository = remember { com.mrtt.client.servers.ServerRepository(context) }
    val allServers by repository.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    var addingExit by remember { mutableStateOf(false) }
    var settingUpExit by remember { mutableStateOf(false) }
    var removingExit by remember { mutableStateOf<com.mrtt.client.servers.ServerExit?>(null) }
    fun refresh() { reload++ }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runner.run("Конфигурация сохранена") {
            val json = control.exportConfig()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runner.run {
            pendingImport = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadState(loaded != null, error)
        if (loaded == null) return@Column
        if (loaded.restartRequired) {
            Text("Пароль владельца изменён: перезапустите сервер, чтобы он применился.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        AppSectionCard {
            Text("Пароль владельца", fontWeight = FontWeight.SemiBold)
            FormField(form.mainPassword, { form = form.copy(mainPassword = it.filter(Char::isLetterOrDigit).take(128)) }, "Пароль")
            TextButton(onClick = { form = form.copy(mainPassword = generate16CharPassword()) }) { Text("Сгенерировать") }
        }
        AppSectionCard {
            Text("DNS для клиентов", fontWeight = FontWeight.SemiBold)
            CompactDropdownSetting(
                title = "Провайдер",
                selectedKey = form.dnsProvider,
                options = DNS_PROVIDERS,
                enabled = true,
                onSelected = { form = form.copy(dnsProvider = it) },
            )
            if (form.dnsProvider == "custom") {
                FormField(form.dnsPrimary, { form = form.copy(dnsPrimary = it.trim()) }, "Основной IPv4")
                FormField(form.dnsSecondary, { form = form.copy(dnsSecondary = it.trim()) }, "Запасной IPv4 (необязательно)")
            }
            CompactDropdownSetting(
                title = "Автоперезапуск",
                selectedKey = form.autoRestartHours.toString(),
                options = listOf("0" to "Выключен", "12" to "12 ч", "24" to "24 ч", "48" to "48 ч", "72" to "72 ч"),
                enabled = true,
                onSelected = { form = form.copy(autoRestartHours = it.toInt()) },
            )
        }
        AppSectionCard {
            Text("VK-аккаунт сервера", fontWeight = FontWeight.SemiBold)
            Text(
                if (form.vkToken.isBlank()) {
                    "Не привязан. Привяжите аккаунт, и конфиги с режимом «Аккаунт сервера» будут получать хеши автоматически."
                } else {
                    "Привязан. Конфиги с режимом «Аккаунт сервера» получают хеши с этого аккаунта автоматически."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { bindingVk = true }, shape = MrttShapes.Pill) {
                    Text(if (form.vkToken.isBlank()) "Привязать VK" else "Перепривязать")
                }
                if (form.vkToken.isNotBlank()) {
                    TextButton(onClick = { form = form.copy(vkToken = "") }) { Text("Отвязать") }
                }
            }
            Text("Общие VK-хеши, необязательно", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.mrtt.client.ui.common.VkHashesButton(form.vkHashes) { form = form.copy(vkHashes = it) }
            TextButton(onClick = {
                runner.run("VK применён ко всем конфигам", ::refresh) { control.applyVkToAllClients(form.vkToken, form.vkHashes) }
            }) { Text("Применить ко всем конфигам") }
        }
        AppSectionCard {
            Text("Jitsi", fontWeight = FontWeight.SemiBold)
            var roomInput by remember(loaded) { mutableStateOf(loaded.jitsiRoom.takeIf { it.isNotBlank() }?.let { "https://$it" }.orEmpty()) }
            com.mrtt.client.ui.common.JitsiRoomField(roomInput, { value ->
                roomInput = value
                form = form.copy(jitsiRoom = com.mrtt.client.ui.utils.parseJitsiRoomInput(value) ?: value)
            })
            CompactDropdownSetting(
                title = "Участников в комнате",
                selectedKey = form.jitsiSeats.toString(),
                options = listOf(2, 4, 6, 8, 12, 16).map { it.toString() to it.toString() },
                enabled = true,
                onSelected = { form = form.copy(jitsiSeats = it.toInt()) },
            )
            Text(
                if (com.mrtt.client.ui.utils.parseJitsiRoomInput(roomInput) == null) "Создайте конференцию на любом сервере Jitsi и вставьте ссылку: сервер зайдёт в неё сразу после сохранения, переустанавливать не нужно."
                else "Сервер сидит в ${form.jitsiRoom}. Новые ссылки подписок несут эту комнату; у своего профиля её можно вставить в «Комнату Jitsi».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSectionCard {
            Text("Выходной IP", fontWeight = FontWeight.SemiBold)
            CompactDropdownSetting(
                title = "Приоритет выходного IP",
                selectedKey = if (form.exitPriority == com.mrtt.client.servers.EXIT_PRIORITY_CONFIG) "config" else "server",
                options = listOf("server" to "Настройка сервера", "config" to "Настройка конфига"),
                enabled = true,
                onSelected = { form = form.copy(exitPriority = if (it == "config") com.mrtt.client.servers.EXIT_PRIORITY_CONFIG else "") },
            )
            CompactDropdownSetting(
                title = "Выход сервера",
                selectedKey = form.defaultExit.takeIf { id -> form.exits.any { it.id == id } }.orEmpty(),
                options = listOf("" to "Этот сервер") + form.exits.map { it.id to it.name },
                enabled = true,
                onSelected = { form = form.copy(defaultExit = it) },
            )
            form.exits.forEach { exit ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${exit.name} · ${exit.host}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { removingExit = exit }) { Text("Убрать") }
                }
            }
            OutlinedButton(
                onClick = { addingExit = true },
                enabled = !settingUpExit,
                modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                shape = MrttShapes.Pill,
            ) { Text(if (settingUpExit) "Подключаем серверы..." else "Добавить выход через другой VPS", maxLines = 1) }
            Text(
                "Трафик клиентов выходит через выбранный VPS по протоколу MRTT, UDP тоже. Если связь между серверами упадёт, такие клиенты теряют интернет, а не выходят через этот сервер.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { runner.run("Настройки сохранены", ::refresh) { control.saveSettings(form) } },
            enabled = form != loaded,
            modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
            shape = MrttShapes.Pill,
        ) { Text("Сохранить настройки") }
        AppSectionCard {
            Text("Обслуживание", fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { exportLauncher.launch("mrtt-config.json") }, label = { Text("Экспорт") })
                AssistChip(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }, label = { Text("Импорт") })
                AssistChip(onClick = { runner.run("Память освобождена") { control.trimMemory() } }, label = { Text("Очистить память") })
                AssistChip(onClick = { confirmReboot = true }, label = { Text("Перезапустить") })
            }
        }
    }

    if (addingExit) {
        val candidates = allServers.filter { other -> other.id != server.id && form.exits.none { it.host == other.host } }
        var chosen by remember { mutableStateOf(candidates.firstOrNull()?.id.orEmpty()) }
        com.mrtt.client.ui.common.MrttFormDialog(
            title = "Выход через другой VPS",
            onDismiss = { addingExit = false },
            confirmText = "Добавить",
            confirmEnabled = chosen.isNotEmpty(),
            onConfirm = {
                addingExit = false
                val target = candidates.firstOrNull { it.id == chosen } ?: return@MrttFormDialog
                settingUpExit = true
                runner.run("Выход добавлен", { settingUpExit = false; refresh() }) {
                    try {
                        val created = com.mrtt.client.ui.setupExit(context, server, target)
                        val exit = com.mrtt.client.servers.ServerExit(created.id, target.title, target.host, created.table, created.remotePassword)
                        control.saveExits(form.exits + exit, form.defaultExit)
                    } catch (error: Exception) {
                        settingUpExit = false
                        throw error
                    }
                }
            },
        ) {
            if (candidates.isEmpty()) {
                Text("Других серверов нет: добавьте VPS во вкладке «Серверы».", style = MaterialTheme.typography.bodySmall)
            } else {
                CompactDropdownSetting(
                    title = "Сервер",
                    selectedKey = chosen,
                    options = candidates.map { it.id to it.title },
                    enabled = true,
                    onSelected = { chosen = it },
                )
                Text(
                    "Этот сервер подключится к выбранному как обычный конфиг, он появится там в списке конфигов. Удалять его там не нужно, для этого есть кнопка «Убрать» здесь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    removingExit?.let { exit ->
        ConfirmDialog(
            title = "Убрать выход",
            text = "Убрать выход через ${exit.name}? Конфиги с этим выходом пойдут через этот сервер.",
            confirmText = "Убрать",
            onConfirm = {
                removingExit = null
                runner.run("Выход убран", ::refresh) {
                    val remaining = form.exits.filterNot { it.id == exit.id }
                    // Сначала настройки: клиенты не должны остаться с маршрутом в пропавшую таблицу.
                    control.saveExits(remaining, form.defaultExit.takeUnless { it == exit.id }.orEmpty())
                    com.mrtt.client.ui.removeExit(context, server, allServers.firstOrNull { it.host == exit.host }, exit.id, exit.remotePassword)
                }
            },
            onDismiss = { removingExit = null },
        )
    }
    if (bindingVk) {
        com.mrtt.client.ui.dialogs.VkAuthDialog(
            onToken = { payload ->
                bindingVk = false
                // Сразу сохраняем на сервер: токен в приложении не хранится и не показывается.
                form = form.copy(vkToken = payload.token)
                runner.run("VK-аккаунт привязан к серверу", ::refresh) { control.saveSettings(form) }
            },
            onDismiss = { bindingVk = false },
            freshSession = true,
        )
    }
    if (confirmReboot) {
        ConfirmDialog(
            title = "Перезапуск сервера",
            text = "Перезапустить MRTT на сервере? Все клиенты переподключатся.",
            confirmText = "Перезапустить",
            onConfirm = { confirmReboot = false; runner.run("Перезапуск запрошен") { control.reboot() } },
            onDismiss = { confirmReboot = false },
        )
    }
    pendingImport?.let { json ->
        ConfirmDialog(
            title = "Импорт конфигурации",
            text = "Добавить конфиги, группы и правила из файла и применить его настройки? Существующие подписки сохранятся.",
            confirmText = "Импортировать",
            onConfirm = {
                pendingImport = null
                runner.run("Импорт выполнен", ::refresh) { control.importConfig(json) }
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
internal fun ServerLogsSection(control: ServerControl) {
    val runner = rememberActionRunner()
    var reload by remember { mutableStateOf(0) }
    val (logs, error) = rememberServerData(control, reload) { logs() }
    val context = LocalContext.current
    fun refresh() { reload++ }
    LaunchedEffect(control) {
        // Лог живой: новые строки подтягиваются, пока раздел открыт.
        while (true) {
            kotlinx.coroutines.delay(5_000)
            reload++
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadState(logs != null, error)
        val json = logs ?: return@Column
        MrttSettingRow(
            title = "Запись логов на сервере",
            checked = json.optBoolean("active"),
            onCheckedChange = { active -> runner.run(after = ::refresh) { control.setLogging(active) } },
        )
        val array = json.optJSONArray("lines")
        val lines = if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Самые новые строки снизу, и экран всегда стоит на них.
        LaunchedEffect(lines.size, lines.lastOrNull()) {
            if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MrttShapes.Card,
            color = com.mrtt.client.ui.design.MrttTheme.extendedColors.terminalBackground,
            contentColor = com.mrtt.client.ui.design.MrttTheme.extendedColors.terminalText,
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 64.dp),
                ) {
                    if (lines.isEmpty()) item { Text("Логи пусты", style = MaterialTheme.typography.bodySmall) }
                    items(lines.size) { index ->
                        Text(lines[index], style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.material3.IconButton(
                        enabled = lines.isNotEmpty(),
                        onClick = { context.shareLogFile("MRTT server log", lines.joinToString("\n")) },
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Share, contentDescription = "Поделиться логом сервера")
                    }
                    androidx.compose.material3.IconButton(
                        onClick = { runner.run("Логи очищены", ::refresh) { control.clearLogs() } },
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = "Очистить логи сервера")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ServerStatsSection(control: ServerControl) {
    val context = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    val (stats, error) = rememberServerData(control, reload) { stats() }
    LaunchedEffect(control) {
        // Статистика живая: обновляем раз в 10 секунд, пока раздел открыт.
        while (true) {
            kotlinx.coroutines.delay(10_000)
            reload++
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadState(stats != null, error)
        val s = stats ?: return@Column
        val uptime = s.optLong("uptime")
        AppSectionCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatRow("Статус", s.optString("status"))
            StatRow("Публичный IP", s.optString("public_ip"))
            StatRow("Порт подключения", s.optInt("peer_port").toString())
            StatRow("Аптайм", "${uptime / 86400} д ${uptime % 86400 / 3600} ч ${uptime % 3600 / 60} мин")
            StatRow("Сессий сейчас", s.optInt("active").toString())
            StatRow("Подключений всего", s.optLong("total").toString())
            StatRow("Подписок", s.optInt("passwords").toString())
        }
        AppSectionCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatRow("CPU", "${s.optInt("cpu_normalized")}%")
            StatRow("Память", s.optString("ram"))
            StatRow("Пик памяти", s.optString("ram_peak"))
            StatRow("Трафик от клиентов", formatBytes(s.optLong("up")))
            StatRow("Трафик к клиентам", formatBytes(s.optLong("down")))
            StatRow("UDP пакетов RX / TX", "${s.optLong("udp_rx_packets")} / ${s.optLong("udp_tx_packets")}")
            StatRow("UDP ошибок RX / TX", "${s.optLong("udp_rx_errors")} / ${s.optLong("udp_tx_errors")}")
            StatRow("Аллокатор", s.optString("allocator"))
        }
        AppSectionCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatRow("Локальный прокси", if (s.optBoolean("local_proxy_active")) "включён" else "выключен")
            StatRow("TCP / UDP через прокси", "${s.optInt("local_proxy_tcp_sessions")} / ${s.optInt("local_proxy_udp_flows")}")
            StatRow("Горячих сессий", "${s.optInt("hot_sessions")} / ${s.optInt("hot_session_capacity")}")
            StatRow("Буфер логов", "${s.optInt("mem_log_ring")} строк")
            StatRow("DPI-кадров в буфере", s.optInt("mem_dpi_ring").toString())
        }
        OutlinedButton(onClick = { reload++; context.showRaisedToast("Обновлено") }, shape = MrttShapes.Pill) { Text("Обновить") }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
