package com.mrtt.client.ui

import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.BatterySaver
import com.mrtt.client.ConnectionSource
import com.mrtt.client.ConnectionStartBlocker
import com.mrtt.client.ConnectionStartPolicy
import com.mrtt.client.MrttConstants
import com.mrtt.client.SettingsStore
import com.mrtt.client.StoredSecretState
import com.mrtt.client.TunnelAuthSnapshot
import com.mrtt.client.TunnelManager
import com.mrtt.client.TunnelService
import com.mrtt.client.VkHashValidationCodec
import com.mrtt.client.newTunnelSessionId
import com.mrtt.client.resolveConnectionSource
import com.mrtt.client.showRaisedToast
import com.mrtt.client.ui.components.MrttScreen
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.utils.parseMrttLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun ConnectionTab(
    settingsStore: SettingsStore,
    tunnelAuthSettings: TunnelAuthSnapshot,
    onInvalidConfiguration: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val connectionPassword = tunnelAuthSettings.connectionPassword
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val tunnelStarting by TunnelManager.starting.collectAsStateWithLifecycle()
    val tunnelStopping by TunnelManager.stopping.collectAsStateWithLifecycle()
    val autoPausedForWifi by TunnelManager.autoPausedForWifi.collectAsStateWithLifecycle()
    val cooldownActive by TunnelManager.cooldownActive.collectAsStateWithLifecycle()

    val mrttLinkMode = tunnelAuthSettings.mrttLinkMode
    val mrttLink = tunnelAuthSettings.mrttLink
    val peer by settingsStore.peer.collectAsStateWithLifecycle(initialValue = "")
    val vkHashes by settingsStore.vkHashes.collectAsStateWithLifecycle(initialValue = "")
    val vkHashCheckResultsJson by settingsStore.vkHashCheckResults.collectAsStateWithLifecycle(initialValue = "{}")
    val workersPerHash by settingsStore.workersPerHash.collectAsStateWithLifecycle(initialValue = 18)
    val sni by settingsStore.sni.collectAsStateWithLifecycle(initialValue = "")
    val turnTransport by settingsStore.turnTransport.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_TURN_TRANSPORT)
    val srtpMode by settingsStore.srtpMode.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedSrtpMode ?: MrttConstants.SrtpMode.DEFAULT)
    val customTurnIps by settingsStore.customTurnIps.collectAsStateWithLifecycle(initialValue = "")
    val tunnelMtu by settingsStore.tunnelMtu.collectAsStateWithLifecycle(initialValue = MrttConstants.Vpn.DEFAULT_MTU)
    val manualPortsEnabled by settingsStore.manualPortsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val serverPeerPort by settingsStore.serverPeerPort.collectAsStateWithLifecycle(
        initialValue = MrttConstants.Network.DEFAULT_SERVER_PEER_PORT,
    )
    val vkAuthMode by settingsStore.vkAuthMode.collectAsStateWithLifecycle(initialValue = MrttConstants.VkAuth.MODE_CALLS)
    val captchaMode by settingsStore.captchaMode.collectAsStateWithLifecycle(initialValue = "auto")
    val captchaSolveMethod by settingsStore.captchaSolveMethod.collectAsStateWithLifecycle(initialValue = "auto")
    val activeFingerprint by settingsStore.selectedFingerprint.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_FINGERPRINT)
    val activeClientIds by settingsStore.activeClientIds.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_CLIENT_IDS)
    val savedHashMode by settingsStore.vkHashMode.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedVkHashMode)
    val savedVkAccessToken by settingsStore.vkAccessToken.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedVkAccessToken)
    val savedMaxGroupId by settingsStore.maxGroupId.collectAsStateWithLifecycle(initialValue = "")
    val savedMaxAccessToken by settingsStore.maxAccessToken.collectAsStateWithLifecycle(initialValue = "")
    val transportMode by settingsStore.transportMode.collectAsStateWithLifecycle(initialValue = MrttConstants.Transport.MODE_VK)
    val jitsiRoomOverride by settingsStore.jitsiRoomOverride.collectAsStateWithLifecycle(initialValue = "")

    var pendingStartAfterVpnPermission by remember { mutableStateOf(false) }

    val parsedLink = remember(mrttLink) { parseMrttLink(mrttLink) }
    val linkHashes = parsedLink?.hashes.orEmpty()
    val linkTokenActive = mrttLinkMode && parsedLink?.vkAccessToken?.isNotEmpty() == true
    val rawManualHashes = remember(vkHashes) {
        vkHashes.split(Regex("[,\\s\\n]+"))
            .map(String::trim)
            .filter { it.length >= 16 }
            .distinct()
    }
    val activeManualHashes = remember(vkHashes, vkHashCheckResultsJson) {
        VkHashValidationCodec.active(
            rawManualHashes,
            VkHashValidationCodec.decode(vkHashCheckResultsJson),
        )
    }
    val manualHashes = if (activeManualHashes.isNotEmpty()) activeManualHashes else rawManualHashes
    val accountAutoJsMode = vkAuthMode == MrttConstants.VkAuth.MODE_AUTO_JS
    val hashSettingsLoaded = savedHashMode != null && savedVkAccessToken != null
    val autoHashMode = accountAutoJsMode || (
        savedHashMode != null && savedHashMode != MrttConstants.VkAutoHash.MODE_MANUAL
    )
    val vkTokenActive = savedVkAccessToken?.isNotBlank() == true
    val maxLinkActive = mrttLinkMode && !parsedLink?.maxGroupId.isNullOrBlank()
    val maxBindingActive = savedMaxGroupId.isNotBlank()
    val maxReady = (maxLinkActive || maxBindingActive) && savedMaxAccessToken.isNotBlank()
    val hashesReady = when (transportMode) {
        MrttConstants.Transport.MODE_MAX -> maxReady
        MrttConstants.Transport.MODE_JITSI -> jitsiRoomOverride.isNotBlank() || (mrttLinkMode && !parsedLink?.jitsiRoom.isNullOrBlank())
        else -> hashSettingsLoaded && ConnectionStartPolicy.hasReadyVkCredentials(
            linkMode = mrttLinkMode,
            linkHashCount = linkHashes.size,
            linkTokenPresent = linkTokenActive,
            autoHashMode = autoHashMode,
            storedTokenPresent = vkTokenActive,
            manualHashCount = manualHashes.size,
        )
    }
    val peerPortValid = !manualPortsEnabled || serverPeerPort in 1..65535
    val trimmedPeer = peer.trim()
    val isLocalOrUnspecifiedPeer = trimmedPeer == "0.0.0.0" || trimmedPeer == "127.0.0.1" || trimmedPeer.equals("localhost", ignoreCase = true) || trimmedPeer == "::1"
    val startBlocker = ConnectionStartPolicy.blocker(
        linkMode = mrttLinkMode,
        linkValid = parsedLink != null,
        peerValid = trimmedPeer.isNotBlank() && !trimmedPeer.contains(":") && !isLocalOrUnspecifiedPeer,
        peerPortValid = peerPortValid,
        connectionPasswordSet = connectionPassword.isNotBlank(),
        connectionPasswordReadable = tunnelAuthSettings.connectionPasswordState != StoredSecretState.Unreadable,
        hashesReady = hashesReady,
    )
    val isValid = startBlocker == null

    fun startIntent(source: ConnectionSource, generationId: Long, salt: String): Intent =
        Intent(context, TunnelService::class.java).apply {
            action = "START"
            putExtra("peer", source.peer)
            putExtra("vk_hashes", source.hashes)
            putExtra("vk_hashes_from_link", source.hashesFromLink)
            putExtra("vk_access_token", source.vkAccessToken)
            putExtra("vk_token_from_link", source.tokenFromLink)
            putExtra("max_group_id", source.maxGroupId)
            putExtra("jitsi_room", source.jitsiRoom)
            putExtra("jitsi_seats", source.jitsiSeats)
            putExtra("workers_per_hash", workersPerHash)
            putExtra("port", 0)
            putExtra("sni", sni)
            putExtra("connection_password", source.password)
            putExtra("vk_auth_mode", vkAuthMode)
            putExtra("captcha_mode", captchaMode)
            putExtra("captcha_solve_method", captchaSolveMethod)
            putExtra("fingerprint", activeFingerprint)
            putExtra("client_ids", activeClientIds)
            putExtra("obfs_mode", MrttConstants.Tunnel.DEFAULT_OBFS_MODE)
            putExtra("turn_transport", turnTransport)
            putExtra("srtp_mode", srtpMode)
            putExtra("custom_turn_ips", customTurnIps)
            putExtra("mtu", tunnelMtu)
            putExtra("generation_id", generationId)
            putExtra("session_salt", salt)
        }

    fun startTunnelService() {
        scope.launch {
            val source = resolveConnectionSource(settingsStore) ?: run {
                context.showRaisedToast("Заполните настройки подключения", Toast.LENGTH_SHORT)
                return@launch
            }
            TunnelManager.isLoggingEnabled = settingsStore.loggingEnabled.first()
            val nextGen = System.currentTimeMillis() / 1000L
            val salt = newTunnelSessionId()
            val intent = startIntent(source, nextGen, salt)
            runCatching { context.startForegroundService(intent) }
                .onFailure { error ->
                    TunnelManager.updateLog(
                        "foreground_request_error",
                        "Android заблокировал запуск VPN: ${error.message ?: error.javaClass.simpleName}",
                        99,
                        true,
                    )
                    Toast.makeText(
                        context,
                        "Android заблокировал запуск VPN. Проверьте ограничения батареи приложения.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingStartAfterVpnPermission) {
            pendingStartAfterVpnPermission = false
            if (VpnService.prepare(context) == null) {
                startTunnelService()
            } else {
                context.showRaisedToast("VPN-разрешение не выдано", Toast.LENGTH_SHORT)
            }
        }
    }

    fun toggleTunnel() {
        if (TunnelManager.stopping.value) return
        if (com.mrtt.client.tunnelPowerIsOn(TunnelManager.running.value, TunnelManager.starting.value, TunnelManager.autoPausedForWifi.value)) {
            pendingStartAfterVpnPermission = false
            context.startService(Intent(context, TunnelService::class.java).apply { action = "STOP" })
            return
        }
        if (!isValid) {
            val blocker = requireNotNull(startBlocker)
            TunnelManager.updateLog(
                "connection_configuration_${blocker.name}",
                "[ПОДКЛЮЧЕНИЕ] Запуск не начат: ${blocker.message}",
                99,
                true,
            )
            if (blocker == ConnectionStartBlocker.StoredSecret) {
                scope.launch {
                    val reset = settingsStore.resetUnreadableConnectionPassword()
                    if (reset) {
                        context.showRaisedToast(
                            "Сохранённый пароль сброшен. Введите его заново в разделе «Авторизация».",
                            Toast.LENGTH_LONG,
                        )
                    }
                    onInvalidConfiguration()
                }
                return
            }
            onInvalidConfiguration()
            return
        }
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            pendingStartAfterVpnPermission = true
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startTunnelService()
        }
    }

    MrttScreen {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
                .offset(y = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            TunnelConnectionPanel(
                tunnelRunning = tunnelRunning,
                tunnelStarting = tunnelStarting,
                tunnelStopping = tunnelStopping,
                autoPausedForWifi = autoPausedForWifi,
                cooldownActive = cooldownActive,
                configurationValid = isValid,
                onToggleTunnel = { toggleTunnel() },
            )
        }
    }
}

@Composable
private fun TunnelConnectionPanel(
    tunnelRunning: Boolean,
    tunnelStarting: Boolean,
    tunnelStopping: Boolean,
    autoPausedForWifi: Boolean,
    cooldownActive: Boolean,
    configurationValid: Boolean,
    onToggleTunnel: () -> Unit,
) {
    val isStarting = tunnelStarting && !tunnelRunning && !tunnelStopping
    val canToggle = !tunnelStopping && (tunnelRunning || isStarting || autoPausedForWifi || !cooldownActive || !configurationValid)
    val activeVisual = tunnelRunning || isStarting || autoPausedForWifi || tunnelStopping
    val logoScale = if (activeVisual) 1.06f else 0.96f
    val statusColor = when {
        autoPausedForWifi -> MaterialTheme.colorScheme.tertiary
        tunnelStopping -> MaterialTheme.colorScheme.tertiary
        tunnelRunning -> MaterialTheme.colorScheme.primary
        isStarting -> MaterialTheme.colorScheme.primary
        cooldownActive -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when {
        tunnelStopping -> "Остановка"
        autoPausedForWifi -> "Ожидание. Автопауза при Wi-Fi"
        tunnelRunning -> "Подключено"
        isStarting -> "Подключение"
        cooldownActive -> "Ожидание"
        else -> "Отключено"
    }

    AppSectionCard(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val activeWorkers by TunnelManager.activeWorkers.collectAsStateWithLifecycle()
            val targetWorkers by TunnelManager.targetWorkers.collectAsStateWithLifecycle()
            val showWorkers = (tunnelRunning || isStarting) && !tunnelStopping && targetWorkers > 0
            androidx.compose.material3.Surface(
                onClick = onToggleTunnel,
                enabled = canToggle,
                shape = MrttShapes.Pill,
                color = if (activeVisual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (activeVisual) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeVisual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .size(172.dp)
                    .scale(logoScale)
                    .alpha(if (canToggle || activeVisual) 1f else 0.5f)
                    .semantics {
                        contentDescription = when {
                            tunnelStopping -> "Ожидание отключения VPN"
                            tunnelRunning || autoPausedForWifi -> "Остановить VPN"
                            else -> "Включить VPN"
                        }
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    if (showWorkers) {
                        Text(
                            text = "$activeWorkers / $targetWorkers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "воркеров",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center,
            )

            TunnelUptimeText(tunnelRunning = tunnelRunning || autoPausedForWifi || tunnelStopping)

            // «Экономия батареи» in force: said under the status, where the user looks
            val batterySaving by BatterySaver.active.collectAsStateWithLifecycle()
            if (batterySaving && tunnelRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BatterySaver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = BatterySaver.BADGE,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TunnelUptimeText(tunnelRunning: Boolean) {
    val uptimeSeconds by TunnelManager.uptimeSeconds.collectAsStateWithLifecycle()
    Text(
        text = formatTunnelUptime(uptimeSeconds),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (tunnelRunning) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
        },
    )
}

private fun formatTunnelUptime(seconds: Long?): String {
    val totalSeconds = seconds?.coerceAtLeast(0L) ?: 0L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, sec)
}

private fun formatMillis(value: Double): String = when {
    value < 0.05 -> "wall 0 мс"
    value < 10.0 -> String.format(Locale.US, "wall %.1f мс", value)
    else -> String.format(Locale.US, "wall %.0f мс", value)
}

private fun formatRate(value: Double): String = when {
    value < 0.05 -> "0/с"
    value < 10.0 -> String.format(Locale.US, "%.1f/с", value)
    else -> String.format(Locale.US, "%.0f/с", value)
}
