package com.csqtt.client.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.csqtt.client.servers.RoutingRuleData
import com.csqtt.client.servers.ServerClient
import com.csqtt.client.servers.ServerControl
import com.csqtt.client.servers.ServerGroup
import com.csqtt.client.ui.AppSectionCard
import com.csqtt.client.ui.CompactDropdownSetting
import com.csqtt.client.ui.common.ConfirmDialog
import com.csqtt.client.ui.common.MrttFormDialog
import com.csqtt.client.ui.common.FormField
import com.csqtt.client.ui.common.formatBytes
import com.csqtt.client.ui.common.rememberActionRunner
import com.csqtt.client.ui.components.MrttSegmentedControl
import com.csqtt.client.ui.components.MrttSettingRow
import com.csqtt.client.ui.design.MrttShapes
import com.csqtt.client.ui.design.MrttSizes

/** Загрузка с перезапросом: [reload] увеличивается после каждого изменения на сервере. */
@Composable
internal fun <T> rememberServerData(control: ServerControl, reload: Int, load: suspend ServerControl.() -> T): Pair<T?, String?> {
    var data by remember(control) { mutableStateOf<T?>(null) }
    var error by remember(control) { mutableStateOf<String?>(null) }
    LaunchedEffect(control, reload) {
        runCatching { control.load() }
            .onSuccess { data = it; error = null }
            .onFailure { error = it.message ?: "Сервер недоступен" }
    }
    return data to error
}

@Composable
internal fun LoadState(loaded: Boolean, error: String?) {
    when {
        error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        !loaded -> Text("Загрузка...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun GroupsSection(control: ServerControl) {
    val runner = rememberActionRunner()
    var reload by remember { mutableStateOf(0) }
    val (data, error) = rememberServerData(control, reload) { groups() to routingRules() }
    var editing by remember { mutableStateOf<ServerGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ServerGroup?>(null) }
    fun refresh() { reload++ }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadState(data != null, error)
        val (groups, rules) = data ?: return@Column
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight), shape = MrttShapes.Pill) {
            Text("Создать группу")
        }
        groups.forEach { group ->
            AppSectionCard(modifier = Modifier.clickable { editing = group }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(group.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "Подписок: ${group.memberCount} · в сети ${group.activeSessions} · ↑ ${formatBytes(group.up)} ↓ ${formatBytes(group.down)}" +
                        (rules.firstOrNull { it.id == group.routingRuleId }?.let { " · маршрут «${it.name}»" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (groups.isEmpty()) Text("Групп пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        MrttFormDialog(
            title = "Новая группа",
            onDismiss = { creating = false },
            confirmText = "Создать",
            confirmEnabled = name.isNotBlank(),
            onConfirm = {
                creating = false
                runner.run("Группа создана", ::refresh) { control.createGroup(name.trim()) }
            },
        ) { FormField(name, { name = it.take(64) }, "Название группы") }
    }
    editing?.let { group ->
        var name by remember(group) { mutableStateOf(group.name) }
        var ruleId by remember(group) { mutableStateOf(group.routingRuleId) }
        MrttFormDialog(
            title = group.name,
            onDismiss = { editing = null },
            confirmEnabled = name.isNotBlank() && (name.trim() != group.name || ruleId != group.routingRuleId),
            onConfirm = {
                editing = null
                runner.run("Сохранено", ::refresh) { control.updateGroup(group.id, name.trim(), ruleId) }
            },
        ) {
            FormField(name, { name = it.take(64) }, "Название группы")
            CompactDropdownSetting(
                title = "Маршрутизация",
                selectedKey = ruleId,
                options = listOf("" to "Нет") + data?.second.orEmpty().map { it.id to it.name },
                enabled = true,
                onSelected = { ruleId = it },
            )
            TextButton(onClick = { deleting = group; editing = null }) {
                Text("Удалить группу", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    deleting?.let { group ->
        ConfirmDialog(
            title = "Удалить группу",
            text = "Удалить «${group.name}» вместе со всеми её конфигами (${group.memberCount})? Их ссылки перестанут работать.",
            confirmText = "Удалить",
            onConfirm = { deleting = null; runner.run("Группа удалена", ::refresh) { control.deleteGroup(group.id) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
internal fun RoutingSection(control: ServerControl) {
    val runner = rememberActionRunner()
    var reload by remember { mutableStateOf(0) }
    val (data, error) = rememberServerData(control, reload) { Triple(routingRules(), groups(), clients()) }
    var editing by remember { mutableStateOf<RoutingRuleData?>(null) }
    fun refresh() { reload++ }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Правила раздельной маршрутизации, которые сервер отправляет клиентам: какие приложения, домены и IP идут мимо туннеля.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LoadState(data != null, error)
        val (rules, _, _) = data ?: return@Column
        Button(onClick = { editing = RoutingRuleData() }, modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight), shape = MrttShapes.Pill) {
            Text("Создать правило")
        }
        rules.forEach { rule ->
            AppSectionCard(modifier = Modifier.clickable { editing = rule }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rule.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "Приложений: ${rule.packages.size} · доменов: ${rule.domains.size}${if (rule.templateDomains) " + шаблон" else ""} · IP: ${rule.ips.size} · групп: ${rule.groups.size} · подписок: ${rule.clients.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rules.isEmpty()) Text("Правил пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    editing?.let { rule ->
        val (_, groups, clients) = data ?: return@let
        RoutingRuleDialog(
            rule = rule,
            groups = groups,
            clients = clients,
            onDismiss = { editing = null },
            onSave = { updated -> editing = null; runner.run("Правило сохранено", ::refresh) { control.saveRoutingRule(updated) } },
            onDelete = { editing = null; runner.run("Правило удалено", ::refresh) { control.deleteRoutingRule(rule.id) } },
        )
    }
}

private fun lines(text: String): List<String> =
    text.split(Regex("[\\n,;]+")).map(String::trim).filter(String::isNotEmpty).distinct()

@Composable
private fun RoutingRuleDialog(
    rule: RoutingRuleData,
    groups: List<ServerGroup>,
    clients: List<ServerClient>,
    onDismiss: () -> Unit,
    onSave: (RoutingRuleData) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(rule.name) }
    var appsWhitelist by remember { mutableStateOf(rule.appsWhitelist) }
    var packages by remember { mutableStateOf(rule.packages.joinToString("\n")) }
    var templateDomains by remember { mutableStateOf(rule.templateDomains) }
    var domainsWhitelist by remember { mutableStateOf(rule.domainsWhitelist) }
    var domains by remember { mutableStateOf(rule.domains.joinToString("\n")) }
    var ipWhitelist by remember { mutableStateOf(rule.ipWhitelist) }
    var ips by remember { mutableStateOf(rule.ips.joinToString("\n")) }
    var assignedGroups by remember { mutableStateOf(rule.groups.toSet()) }
    var assignedClients by remember { mutableStateOf(rule.clients.toSet()) }

    MrttFormDialog(
        title = if (rule.id.isEmpty()) "Новое правило" else rule.name,
        onDismiss = onDismiss,
        onConfirm = {
            onSave(
                rule.copy(
                    name = name.trim().ifBlank { "Правило" },
                    appsWhitelist = appsWhitelist,
                    packages = lines(packages),
                    templateDomains = templateDomains,
                    domainsWhitelist = domainsWhitelist,
                    domains = lines(domains),
                    ipWhitelist = ipWhitelist,
                    ips = lines(ips),
                    groups = assignedGroups.toList(),
                    clients = assignedClients.toList(),
                ),
            )
        },
    ) {
        FormField(name, { name = it.take(64) }, "Название")
        Text("Режим исключения приложений", fontWeight = FontWeight.SemiBold)
        MrttSegmentedControl(
            options = listOf(false to "Чёрный список", true to "Белый список"),
            selected = appsWhitelist,
            onSelected = { appsWhitelist = it },
        )
        FormField(packages, { packages = it }, "Пакеты, по одному в строке", singleLine = false, minLines = 2)
        Text("Режим исключения доменов", fontWeight = FontWeight.SemiBold)
        MrttSegmentedControl(
            options = listOf(false to "Чёрный список", true to "Белый список"),
            selected = domainsWhitelist,
            onSelected = { domainsWhitelist = it },
        )
        MrttSettingRow(title = "Готовый список RU доменов", checked = templateDomains, onCheckedChange = { templateDomains = it })
        FormField(domains, { domains = it }, "Домены, по одному в строке", singleLine = false, minLines = 2)
        Text("Режим исключения адресов", fontWeight = FontWeight.SemiBold)
        MrttSegmentedControl(
            options = listOf(false to "Чёрный список", true to "Белый список"),
            selected = ipWhitelist,
            onSelected = { ipWhitelist = it },
        )
        FormField(ips, { ips = it }, "Адреса: IP, CIDR или диапазон, по одному в строке", singleLine = false, minLines = 2)
        if (groups.isNotEmpty()) {
            Text("Применить к группам", fontWeight = FontWeight.SemiBold)
            groups.forEach { group ->
                CheckRow(group.name, group.id in assignedGroups) {
                    assignedGroups = if (group.id in assignedGroups) assignedGroups - group.id else assignedGroups + group.id
                }
            }
        }
        Text("Применить к конфигам", fontWeight = FontWeight.SemiBold)
        clients.forEach { client ->
            CheckRow(client.name, client.password in assignedClients) {
                assignedClients = if (client.password in assignedClients) assignedClients - client.password else assignedClients + client.password
            }
        }
        if (rule.id.isNotEmpty()) {
            TextButton(onClick = onDelete) { Text("Удалить правило", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
