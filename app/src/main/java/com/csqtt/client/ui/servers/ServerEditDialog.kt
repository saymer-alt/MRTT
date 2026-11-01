package com.csqtt.client.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.csqtt.client.servers.VpsServer
import com.csqtt.client.ui.common.MrttFormDialog
import com.csqtt.client.ui.common.FormField
import com.csqtt.client.ui.components.MrttSegmentedControl
import com.csqtt.client.ui.components.MrttSettingRow
import com.csqtt.client.ui.sanitizeSshPassword


@Composable
internal fun ServerEditDialog(
    initial: VpsServer,
    onDismiss: () -> Unit,
    onSave: (VpsServer) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var host by rememberSaveable { mutableStateOf(initial.host) }
    var sshPort by rememberSaveable { mutableStateOf(initial.sshPort.toString()) }
    var sshUser by rememberSaveable { mutableStateOf(initial.sshUser) }
    var keyMode by rememberSaveable { mutableStateOf(initial.sshPrivateKey.isNotBlank()) }
    var sshPassword by rememberSaveable { mutableStateOf(initial.sshPassword) }
    var privateKey by rememberSaveable { mutableStateOf(initial.sshPrivateKey) }
    var passphrase by rememberSaveable { mutableStateOf(initial.sshKeyPassphrase) }
    var peerPort by rememberSaveable { mutableStateOf(initial.peerPort.toString()) }
    var apiPort by rememberSaveable { mutableStateOf(initial.apiPort.toString()) }
    var docker by rememberSaveable { mutableStateOf(initial.docker) }
    var mainPassword by rememberSaveable { mutableStateOf(initial.mainPassword) }

    val ports = listOf(sshPort, peerPort, apiPort).map { it.toIntOrNull() }
    val portsValid = ports.all { it != null && it in 1..65535 } && peerPort != apiPort
    val authValid = if (keyMode) privateKey.isNotBlank() else sshPassword.isNotEmpty()
    val valid = host.isNotBlank() && !host.contains(':') && sshUser.isNotBlank() && authValid &&
        portsValid && com.csqtt.client.ui.utils.isValidTunnelPassword(mainPassword)
    val changed = initial.host.isBlank() || name.trim() != initial.name || host.trim() != initial.host ||
        sshPort != initial.sshPort.toString() || sshUser.trim() != initial.sshUser ||
        keyMode != initial.sshPrivateKey.isNotBlank() || sshPassword != initial.sshPassword ||
        privateKey.trim() != initial.sshPrivateKey || passphrase != initial.sshKeyPassphrase ||
        peerPort != initial.peerPort.toString() || apiPort != initial.apiPort.toString() ||
        docker != initial.docker || mainPassword != initial.mainPassword

    MrttFormDialog(
        title = if (initial.host.isBlank()) "Новый сервер" else "Сервер",
        onDismiss = onDismiss,
        confirmEnabled = valid && changed,
        onConfirm = {
            onSave(
                initial.copy(
                    name = name.trim(),
                    host = host.trim(),
                    sshPort = sshPort.toInt(),
                    sshUser = sshUser.trim(),
                    sshPassword = if (keyMode) "" else sshPassword,
                    sshPrivateKey = if (keyMode) privateKey.trim() else "",
                    sshKeyPassphrase = if (keyMode) passphrase else "",
                    peerPort = peerPort.toInt(),
                    apiPort = apiPort.toInt(),
                    docker = docker,
                    mainPassword = mainPassword,
                ),
            )
        },
    ) {
        FormField(name, { name = it }, "Название (необязательно)")
        FormField(host, { host = it.filterNot(Char::isWhitespace) }, "IP или домен VPS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(sshUser, { sshUser = it.filterNot(Char::isWhitespace) }, "SSH-логин", Modifier.weight(1f))
            FormField(sshPort, { sshPort = it }, "SSH-порт", Modifier.weight(0.7f), numeric = true)
        }
        MrttSegmentedControl(
            options = listOf(false to "Пароль", true to "SSH-ключ"),
            selected = keyMode,
            onSelected = { keyMode = it },
        )
        if (keyMode) {
            FormField(privateKey, { privateKey = it }, "Приватный ключ (PEM / OpenSSH)", singleLine = false, minLines = 3)
            FormField(passphrase, { passphrase = it }, "Пароль ключа (если есть)", secret = true)
        } else {
            FormField(sshPassword, { sshPassword = sanitizeSshPassword(it) }, "SSH-пароль", secret = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(peerPort, { peerPort = it }, "UDP-порт", Modifier.weight(1f), numeric = true)
            FormField(apiPort, { apiPort = it }, "Порт API", Modifier.weight(1f), numeric = true)
        }
        Text(
            "Порт API слушает только 127.0.0.1 на сервере: приложение подключается к нему через SSH, в интернет он не открывается.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(mainPassword, { mainPassword = it }, "Пароль владельца")
        MrttSettingRow(title = "Установка в Docker", checked = docker, onCheckedChange = { docker = it })
        if (!valid) {
            Text(
                "Заполните адрес, SSH-доступ, корректные разные порты и пароль владельца. ${com.csqtt.client.ui.utils.TUNNEL_PASSWORD_RULE}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
