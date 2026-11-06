package com.mrtt.client.ui.servers

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import com.mrtt.client.ui.components.MrttGlassButton as Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.DeployManager
import com.mrtt.client.TunnelManager
import com.mrtt.client.TunnelService
import com.mrtt.client.servers.ServerApi
import com.mrtt.client.servers.ServerRepository
import com.mrtt.client.servers.VpsServer
import com.mrtt.client.showRaisedToast
import com.mrtt.client.ui.AppSectionCard
import com.mrtt.client.ui.components.MrttScreen
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSizes
import com.mrtt.client.ui.friendlyDeployError
import com.mrtt.client.ui.performDeploy
import com.mrtt.client.ui.performUninstall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

private const val NEW_SERVER = "new"

/** Сервер, для которого идёт установка или удаление: прогресс и ошибки показываются только у него. */
internal val deployTarget = MutableStateFlow("")

@Composable
internal fun ServersTab() {
    val context = LocalContext.current.applicationContext
    val repository = remember { ServerRepository(context) }
    val servers by repository.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Экран снимается, пока приложение свёрнуто: открытое окно и его поля должны это пережить.
    // В состояние кладём только id: пароли остаются в репозитории и в полях формы.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val newServer = remember(editingId) { VpsServer(mainPassword = com.mrtt.client.ui.utils.generate16CharPassword()) }

    LaunchedEffect(Unit) {
        DeployManager.init(context)
        repository.migrateLegacy()
    }

    val opened = servers.firstOrNull { it.id == openedId }
    if (opened != null) {
        BackHandler { openedId = null }
        ServerManageScreen(
            server = opened,
            onBack = { openedId = null },
            onEdit = { editingId = opened.id },
            onDeploy = { startDeploy(context, opened) },
            onUninstall = { startUninstall(context, opened) },
            onDelete = {
                scope.launch {
                    ServerApi.forget(opened.id)
                    repository.delete(opened.id)
                    openedId = null
                }
            },
        )
    } else {
        MrttScreen {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Серверы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "VPS, на которые вы ставите MRTT. Управление конфигами, группами и настройками идёт через SSH: никаких веб-страниц и открытых портов, кроме порта подключения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DeployProgressCard()
                if (servers.isEmpty()) {
                    AppSectionCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text("Серверов пока нет", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "1. Добавьте VPS: адрес, SSH-логин и пароль или ключ.\n" +
                                "2. Откройте сервер и установите MRTT.\n" +
                                "3. Создайте конфиги и подписки и раздайте их ссылкой или QR-кодом.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                servers.forEach { server ->
                    AppSectionCard(modifier = Modifier.clickable { openedId = server.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${server.host} · SSH ${server.sshPort} · UDP ${server.peerPort}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { editingId = NEW_SERVER },
                    modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                    shape = MrttShapes.Pill,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Добавить сервер", modifier = Modifier.padding(start = 8.dp))
                }
                Spacer24()
            }
        }
    }

    editingId?.let { id ->
        // Список серверов ещё грузится после возврата в приложение: окно появится вместе с ним.
        val server = (if (id == NEW_SERVER) newServer else servers.firstOrNull { it.id == id }) ?: return@let
        ServerEditDialog(
            initial = server,
            onDismiss = { editingId = null },
            onSave = { updated ->
                editingId = null
                scope.launch {
                    ServerApi.forget(updated.id)
                    repository.save(updated)
                }
            },
        )
    }
}

@Composable
internal fun DeployProgressCard() {
    val isDeploying by DeployManager.isDeploying.collectAsStateWithLifecycle()
    val progress by DeployManager.deployProgress.collectAsStateWithLifecycle()
    val step by DeployManager.currentStep.collectAsStateWithLifecycle()
    // Только ход установки: ошибка показывается один раз, на экране самого сервера.
    if (isDeploying) {
        AppSectionCard {
            Text(step.ifBlank { "Установка..." }, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Подробности во вкладке «Логи»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Spacer24() = androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))

private fun startDeploy(context: Context, server: VpsServer) {
    if (DeployManager.isDeploying.value) return
    if (!server.hasSshAuth) {
        context.showRaisedToast("Укажите SSH-пароль или ключ сервера")
        return
    }
    ServerApi.forget(server.id)
    deployTarget.value = server.id
    runDeployJob(context, "Установка") {
        performDeploy(
            context = context,
            host = server.host,
            user = server.sshUser,
            pass = server.sshPassword,
            port = server.sshPort,
            mainPass = server.mainPassword,
            peerPort = server.peerPort,
            webPort = server.apiPort,
            onProgress = DeployManager::updateProgress,
            privateKey = server.sshPrivateKey,
            keyPassphrase = server.sshKeyPassphrase,
            installInDocker = server.docker,
        )
    }
}

private fun startUninstall(context: Context, server: VpsServer) {
    if (DeployManager.isDeploying.value) return
    ServerApi.forget(server.id)
    deployTarget.value = server.id
    runDeployJob(context, "Удаление") {
        performUninstall(
            context = context,
            host = server.host,
            user = server.sshUser,
            pass = server.sshPassword,
            port = server.sshPort,
            peerPort = server.peerPort,
            onProgress = DeployManager::updateProgress,
            privateKey = server.sshPrivateKey,
            keyPassphrase = server.sshKeyPassphrase,
        )
    }
}

private fun runDeployJob(context: Context, label: String, block: suspend () -> Boolean) {
    // Деплой идёт в IO-скоупе, а тосты можно показывать только с главного потока.
    suspend fun toast(message: String) = withContext(Dispatchers.Main) { context.showRaisedToast(message) }
    DeployManager.startDeploy()
    DeployManager.scope.launch {
        try {
            context.startForegroundService(Intent(context, TunnelService::class.java).apply { action = "DEPLOY_START" })
            if (block()) {
                toast("$label успешно завершено")
            } else {
                toast(DeployManager.lastResult.value.ifBlank { "$label: ошибка" })
            }
        } catch (e: CancellationException) {
            DeployManager.stopDeploy("$label отменено")
            throw e
        } catch (e: Exception) {
            val message = friendlyDeployError(e.message)
            TunnelManager.addDeployErrorLog(message)
            DeployManager.stopDeploy(message)
            toast(message)
        } finally {
            runCatching {
                context.startService(Intent(context, TunnelService::class.java).apply { action = "DEPLOY_STOP" })
            }
        }
    }
}
