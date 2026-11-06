package com.mrtt.client.ui

import com.mrtt.client.showRaisedToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.SettingsStore
import com.mrtt.client.TunnelManager
import com.mrtt.client.TunnelAuthSnapshot
import com.mrtt.client.MrttConstants
import com.mrtt.client.WorkerCountPolicy
import com.mrtt.client.VkHashValidationCodec
import com.mrtt.client.VkHashValidator
import com.mrtt.client.shouldConfirmAutoJsMode
import com.mrtt.client.shouldConfirmTcpTransport
import com.mrtt.client.vkAuthModeForHashMode
import com.mrtt.client.ui.components.MrttScreen
import com.mrtt.client.ui.components.MrttSettingRow
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSizes
import com.mrtt.client.ui.dialogs.HashesDialog
import com.mrtt.client.ui.dialogs.VkTokenRevokeDialog
import com.mrtt.client.ui.tunnel.WorkersInfoDialog
import com.mrtt.client.ui.utils.parseMrttLink
import com.mrtt.client.ui.utils.stripVkUrlStatic
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.widget.Toast

private const val WORKERS_PER_GROUP = MrttConstants.Tunnel.WORKERS_PER_GROUP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTab(
    settingsStore: SettingsStore,
    tunnelAuthSettings: TunnelAuthSnapshot,
    onMaxAuthRequested: (Int, String) -> Unit,
    validationRequest: Int = 0,
    onVkAuthRequested: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    SettingsTabContent(
        context,
        scope,
        settingsStore,
        tunnelAuthSettings,
        onMaxAuthRequested,
        validationRequest,
        onVkAuthRequested,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTabContent(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    settingsStore: SettingsStore,
    tunnelAuthSettings: TunnelAuthSnapshot,
    onMaxAuthRequested: (Int, String) -> Unit,
    validationRequest: Int,
    onVkAuthRequested: () -> Unit,
) {
    val workersPerHash = remember(settingsStore) {
        settingsStore.workersPerHash
        .map<Int, Int?> { it }
    }
    val savedWorkersPerHash by workersPerHash
        .collectAsStateWithLifecycle(initialValue = SettingsStore.cachedWorkersPerHash)

    val activeProfile = tunnelAuthSettings.profile
    val mrttLinkMode = tunnelAuthSettings.mrttLinkMode
    val mrttLink = tunnelAuthSettings.mrttLink
    val manualPortsEnabled by settingsStore.manualPortsEnabled.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedManualPortsEnabled ?: false,
    )
    val savedServerPeerPort by settingsStore.serverPeerPort.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedServerPeerPort ?: MrttConstants.Network.DEFAULT_SERVER_PEER_PORT,
    )

    val activeFingerprint by settingsStore.selectedFingerprint.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_FINGERPRINT)
    val activeClientIds by settingsStore.activeClientIds.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_CLIENT_IDS)
    val vkHashCheckResultsJson by settingsStore.vkHashCheckResults.collectAsStateWithLifecycle(initialValue = "{}")
    val vkHashCheckResults = remember(vkHashCheckResultsJson) {
        VkHashValidationCodec.decode(vkHashCheckResultsJson)
    }
    val savedHashMode by settingsStore.vkHashMode.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedVkHashMode)
    val savedVkAccessToken by settingsStore.vkSession.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedVkSession)
    val savedMaxGroupId by settingsStore.maxGroupId.collectAsStateWithLifecycle(initialValue = "")
    val savedMaxAccessToken by settingsStore.maxAccessToken.collectAsStateWithLifecycle(initialValue = "")
    val transportMode by settingsStore.transportMode.collectAsStateWithLifecycle(initialValue = MrttConstants.Transport.MODE_VK)
    val connectionPassword by settingsStore.connectionPassword.collectAsStateWithLifecycle(initialValue = "")

    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    var showHashModeGeneralDialog by rememberSaveable { mutableStateOf(false) }
    var showHashModeDetailDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showWorkModeGeneralDialog by rememberSaveable { mutableStateOf(false) }
    var showWorkModeDetailDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showVkRevokeDialog by rememberSaveable { mutableStateOf(false) }
    var vkAuthMode by rememberSaveable { mutableStateOf(MrttConstants.VkAuth.MODE_CALLS) }
    val autoJsRiskAcknowledged by settingsStore.autoJsRiskAcknowledged.collectAsStateWithLifecycle(initialValue = false)
    var showAutoJsRiskDialog by remember { mutableStateOf(false) }
    var showWorkersInfoDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAutoJsSelection by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRequiredFieldErrors by rememberSaveable(activeProfile) { mutableStateOf(false) }

    val turnTransport by settingsStore.turnTransport.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedTurnTransport ?: MrttConstants.Tunnel.DEFAULT_TURN_TRANSPORT,
    )
    val tunnelMtu by settingsStore.tunnelMtu.collectAsStateWithLifecycle(
        initialValue = MrttConstants.Vpn.DEFAULT_MTU,
    )
    val tcpTransportRiskAcknowledged by settingsStore.tcpTransportRiskAcknowledged.collectAsStateWithLifecycle(
        initialValue = false,
    )
    var showTurnTransportGeneralDialog by rememberSaveable { mutableStateOf(false) }
    var showTurnTransportDetailDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showTcpTransportRiskDialog by remember { mutableStateOf(false) }
    val maxTransportRiskAcknowledged by settingsStore.maxTransportRiskAcknowledged.collectAsStateWithLifecycle(
        initialValue = false,
    )
    var showMaxTransportRiskDialog by remember { mutableStateOf(false) }
    val maxTransportSelected = transportMode == MrttConstants.Transport.MODE_MAX
    // Настройки VK (хеши, режим кредов, капча) нужны только транспорту VK
    val vkTransportSelected = transportMode == MrttConstants.Transport.MODE_VK
    val jitsiRoomOverride by settingsStore.jitsiRoomOverride.collectAsStateWithLifecycle(initialValue = "")
    val jitsiWorkers by settingsStore.jitsiWorkers.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_JITSI_WORKERS)
    var showJitsiRoomDialog by rememberSaveable { mutableStateOf(false) }
    var pendingTcpTransportSelection by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun applyTurnTransport(transport: String) {
        scope.launch { settingsStore.saveTurnTransport(transport) }
    }

    val srtpMode by settingsStore.srtpMode.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedSrtpMode ?: MrttConstants.SrtpMode.DEFAULT,
    )

    fun applySrtpMode(mode: String) {
        scope.launch { settingsStore.saveSrtpMode(mode) }
    }

    fun requestTurnTransport(transport: String) {
        if (shouldConfirmTcpTransport(turnTransport, transport, tcpTransportRiskAcknowledged)) {
            pendingTcpTransportSelection = { applyTurnTransport(transport) }
            showTcpTransportRiskDialog = true
        } else {
            applyTurnTransport(transport)
        }
    }

    val hashSettingsLoaded = savedHashMode != null && savedVkAccessToken != null
    var peerInput by rememberSaveable { mutableStateOf("") }
    var peerPortInput by rememberSaveable(activeProfile) {
        val initialPort = if (SettingsStore.cachedManualPortsEnabled == true) {
            (SettingsStore.cachedServerPeerPort ?: MrttConstants.Network.DEFAULT_SERVER_PEER_PORT).toString()
        } else {
            MrttConstants.Network.DEFAULT_SERVER_PEER_PORT.toString()
        }
        mutableStateOf(initialPort)
    }
    var vkHash1 by rememberSaveable { mutableStateOf("") }
    var vkHash2 by rememberSaveable { mutableStateOf("") }
    var vkHash3 by rememberSaveable { mutableStateOf("") }
    var vkHash4 by rememberSaveable { mutableStateOf("") }
    var vkHash5 by rememberSaveable { mutableStateOf("") }
    var vkHash6 by rememberSaveable { mutableStateOf("") }
    var showHashesDialog by rememberSaveable { mutableStateOf(false) }
    var autoCaptchaEnabled by rememberSaveable { mutableStateOf(true) }
    var useWVCaptcha by rememberSaveable { mutableStateOf(false) }
    var isManualMode by rememberSaveable { mutableStateOf(true) }
    var wbvManualMode by rememberSaveable { mutableStateOf(true) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var peerPortSaveJob by remember { mutableStateOf<Job?>(null) }
    var peerPortEdited by rememberSaveable(activeProfile) { mutableStateOf(false) }
    var linkSaveJob by remember { mutableStateOf<Job?>(null) }
    var linkText by remember(activeProfile, mrttLink) { mutableStateOf(mrttLink) }
    var initialized by rememberSaveable(activeProfile) { mutableStateOf(false) }
    val participantMode = mrttLinkMode

    val allHashes = remember(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6) {
        listOf(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6)
    }
    val uniqueHashes = remember(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6, vkHashCheckResults) {
        VkHashValidationCodec.active(allHashes, vkHashCheckResults)
    }
    val parsedMrttLink = remember(linkText) { parseMrttLink(linkText) }
    val jitsiRoomReady = jitsiRoomOverride.isNotBlank() || !parsedMrttLink?.jitsiRoom.isNullOrBlank()
    val preferLinkHashes by settingsStore.vkPreferLinkHashes.collectAsStateWithLifecycle(initialValue = false)
    val ownVkBound = savedVkAccessToken?.isNotBlank() == true
    val linkVkAvailable = participantMode &&
        (parsedMrttLink?.hashes.orEmpty().isNotEmpty() || parsedMrttLink?.vkAccessToken.orEmpty().isNotEmpty())
    // Свой привязанный VK важнее ссылки; хеши из ссылки берутся, только если так выбрано у профиля.
    val useLinkVk = !ownVkBound || preferLinkHashes
    val linkHashes = if (useLinkVk) parsedMrttLink?.hashes.orEmpty() else emptyList()
    val linkVkAccessToken = if (useLinkVk) parsedMrttLink?.vkAccessToken.orEmpty() else ""
    val serverWorkers by TunnelManager.serverAssignedWorkers.collectAsState()
    val effectiveServerWorkers = serverWorkers.takeIf { tunnelRunning && it in 3..100 }
    val linkTokenActive = participantMode && linkVkAccessToken.isNotEmpty()
    val effectiveVkAuthMode = if (
        participantMode && linkHashes.isNotEmpty() && vkAuthMode == MrttConstants.VkAuth.MODE_AUTO_JS
    ) {
        MrttConstants.VkAuth.MODE_CALLS
    } else {
        vkAuthMode
    }
    val effectiveHashMode = if (
        linkTokenActive && savedHashMode == MrttConstants.VkAutoHash.MODE_MANUAL
    ) {
        MrttConstants.VkAutoHash.MODE_AUTO_API
    } else {
        savedHashMode
    }
    val accountAutoJsMode = effectiveVkAuthMode == MrttConstants.VkAuth.MODE_AUTO_JS
    val autoHashMode = accountAutoJsMode || (
        effectiveHashMode != null && effectiveHashMode != MrttConstants.VkAutoHash.MODE_MANUAL
    )
    val vkTokenActive = savedVkAccessToken?.isNotBlank() == true
    val effectiveVkTokenActive = vkTokenActive || linkTokenActive
    val filledHashCount = remember(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6) { uniqueHashes.size }
    val combinedHashes = remember(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6) {
        allHashes.filter { it.isNotBlank() && it.length >= 16 }.distinct().joinToString(",")
    }
    // Ползунок не даёт выбрать больше, чем держат звонки: 2 звонка это 54 воркера, а не 99.
    val selectableMaxWorkers = WorkerCountPolicy.maximumForSources(
        linkMode = participantMode,
        linkHashCount = linkHashes.size,
        autoHashMode = autoHashMode,
        manualHashCount = uniqueHashes.size,
    ).coerceAtMost(SLIDER_MAX_WORKERS).toFloat()
    var sniInput by rememberSaveable { mutableStateOf("") }

    val currentWorkers = roundToGroup(
        (savedWorkersPerHash ?: WORKERS_PER_GROUP).toFloat()
            .coerceIn(WORKER_STEPS.first(), selectableMaxWorkers),
        selectableMaxWorkers,
    )

    val hashErrors = remember(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6) {
        buildList {
            allHashes.forEachIndexed { i, h ->
                if (h.isNotBlank() && h.length < 16) add("Хеш ${i + 1} — короткий")
            }
            val filled = allHashes.filter { it.isNotBlank() && it.length >= 16 }
            if (filled.size != filled.distinct().size) add("Есть дубликаты хешей")
        }
    }
    val hasInputHashErrors = remember(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6) { hashErrors.isNotEmpty() }

    LaunchedEffect(validationRequest) {
        if (validationRequest > 0) showRequiredFieldErrors = true
    }

    fun parseHashes(raw: String) {
        val parts = raw.split(Regex("[,\\s\\n]+")).map { stripVkUrlStatic(it) }.filter { it.isNotEmpty() }
        vkHash1 = parts.getOrElse(0) { "" }
        vkHash2 = parts.getOrElse(1) { "" }
        vkHash3 = parts.getOrElse(2) { "" }
        vkHash4 = parts.getOrElse(3) { "" }
        vkHash5 = parts.getOrElse(4) { "" }
        vkHash6 = parts.getOrElse(5) { "" }
    }

    fun normalizeHashes(vararg hashes: String): String {
        return hashes
            .map { stripVkUrlStatic(it) }
            .filter { it.isNotBlank() && it.length >= 16 }
            .distinct()
            .joinToString(",")
    }

    LaunchedEffect(activeProfile) {
        saveJob?.cancel()
        linkSaveJob?.cancel()
        peerPortSaveJob?.cancel()
        val peer = settingsStore.peer.first()
        val hashes = settingsStore.vkHashes.first()
        val loadedVkAuthMode = settingsStore.vkAuthMode.first()
        val captchaMode = settingsStore.captchaMode.first()
        val captchaMethod = settingsStore.captchaSolveMethod.first()
        val wbvCaptchaMethod = settingsStore.captchaWbvSolveMethod.first()
        
        peerInput = peer
        val port = settingsStore.serverPeerPort.first()
        val manual = settingsStore.manualPortsEnabled.first()
        if (!peerPortEdited) {
            val targetPort = if (manual) port.toString() else MrttConstants.Network.DEFAULT_SERVER_PEER_PORT.toString()
            if (peerPortInput != targetPort) {
                peerPortInput = targetPort
            }
        }
        parseHashes(hashes)
        vkAuthMode = loadedVkAuthMode
        sniInput = settingsStore.sni.first()
        autoCaptchaEnabled = captchaMode == "auto"
        useWVCaptcha = captchaMode != "rjs"
        wbvManualMode = wbvCaptchaMethod != "auto"
        isManualMode = if (captchaMode == "wv") wbvManualMode else captchaMethod != "auto"
        
        initialized = true
    }

    LaunchedEffect(activeProfile, manualPortsEnabled, savedServerPeerPort) {
        if (!peerPortEdited) {
            val targetPort = if (manualPortsEnabled) {
                savedServerPeerPort.toString()
            } else {
                MrttConstants.Network.DEFAULT_SERVER_PEER_PORT.toString()
            }
            if (peerPortInput != targetPort) {
                peerPortInput = targetPort
            }
        }
    }

    LaunchedEffect(activeProfile, manualPortsEnabled) {
        if (!manualPortsEnabled) {
            peerPortSaveJob?.cancel()
            val defaultPort = MrttConstants.Network.DEFAULT_SERVER_PEER_PORT.toString()
            if (peerPortInput != defaultPort) {
                peerPortInput = defaultPort
            }
            peerPortEdited = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveJob?.cancel()
            linkSaveJob?.cancel()
            peerPortSaveJob?.cancel()
        }
    }

    fun saveTunnelSettingsNow(hashes: String = combinedHashes, onSaved: (() -> Unit)? = null) {
        saveJob?.cancel()
        scope.launch {
            if (participantMode) {
                settingsStore.saveParticipantTunnelSettings(hashes, currentWorkers.toInt())
            } else {
                settingsStore.save(
                    peerInput, hashes, "",
                    currentWorkers.toInt(), "udp", 0, sniInput, false
                )
            }
            onSaved?.invoke()
        }
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            if (participantMode) {
                settingsStore.saveParticipantTunnelSettings(combinedHashes, currentWorkers.toInt())
            } else {
                settingsStore.save(
                    peerInput, combinedHashes, "",
                    currentWorkers.toInt(), "udp", 0, sniInput, false
                )
            }
        }
    }

    fun applyWorkMode(mode: String) {
        vkAuthMode = mode
        scope.launch {
            settingsStore.saveVkAuthMode(mode)
        }
    }

    fun applyHashMode(mode: String) {
        val nextWorkMode = vkAuthModeForHashMode(mode, vkAuthMode)
        vkAuthMode = nextWorkMode
        scope.launch {
            settingsStore.saveVkHashMode(mode)
        }
    }

    fun requestWorkMode(mode: String) {
        if (shouldConfirmAutoJsMode(vkAuthMode, mode, autoJsRiskAcknowledged)) {
            pendingAutoJsSelection = { applyWorkMode(mode) }
            showAutoJsRiskDialog = true
        } else {
            applyWorkMode(mode)
        }
    }

    fun requestHashMode(mode: String) {
        val nextWorkMode = vkAuthModeForHashMode(mode, vkAuthMode)
        if (shouldConfirmAutoJsMode(vkAuthMode, nextWorkMode, autoJsRiskAcknowledged)) {
            pendingAutoJsSelection = { applyHashMode(mode) }
            showAutoJsRiskDialog = true
        } else {
            applyHashMode(mode)
        }
    }

    LaunchedEffect(initialized, participantMode, linkHashes, linkTokenActive, vkAuthMode, savedHashMode) {
        if (!initialized || !participantMode) return@LaunchedEffect
        when {
            linkHashes.isNotEmpty() && vkAuthMode == MrttConstants.VkAuth.MODE_AUTO_JS -> {
                applyWorkMode(MrttConstants.VkAuth.MODE_CALLS)
            }
            linkTokenActive && savedHashMode == MrttConstants.VkAutoHash.MODE_MANUAL -> {
                applyHashMode(MrttConstants.VkAutoHash.MODE_AUTO_API)
            }
        }
    }

    LaunchedEffect(initialized, currentWorkers, savedWorkersPerHash) {
        val savedWorkers = savedWorkersPerHash
        if (initialized && savedWorkers != null && currentWorkers.toInt() != savedWorkers) {
            scope.launch { settingsStore.saveWorkersPerHash(currentWorkers.toInt()) }
        }
    }

    val scrollState = rememberScrollState()

    val isPeerValid = peerInput.isNotBlank() && !peerInput.contains(":")
    val isPeerPortValid = peerPortInput.toIntOrNull() in 1..65535
    val maxTransportReady = savedMaxAccessToken.isNotBlank() &&
        (!parsedMrttLink?.maxGroupId.isNullOrBlank() || savedMaxGroupId.isNotBlank())
    val hashesReadyForTunnel = when (transportMode) {
        MrttConstants.Transport.MODE_MAX -> maxTransportReady
        MrttConstants.Transport.MODE_JITSI -> jitsiRoomReady
        else -> when {
            participantMode && linkHashes.isNotEmpty() -> true
            participantMode && linkTokenActive -> true
            autoHashMode -> effectiveVkTokenActive
            else -> filledHashCount > 0
        }
    }
    val peerRequiredError = showRequiredFieldErrors && !participantMode && !isPeerValid
    val peerPortRequiredError = showRequiredFieldErrors && !participantMode && manualPortsEnabled && !isPeerPortValid
    val linkRequiredError = showRequiredFieldErrors && participantMode && parsedMrttLink == null
    val hashesRequiredError = showRequiredFieldErrors && !hashesReadyForTunnel
    val passwordRequiredError = showRequiredFieldErrors && !participantMode && connectionPassword.isBlank()
    if (showHashesDialog) {
        HashesDialog(
            hash1 = vkHash1,
            hash2 = vkHash2,
            hash3 = vkHash3,
            hash4 = vkHash4,
            hash5 = vkHash5,
            hash6 = vkHash6,
            validationResults = vkHashCheckResults,
            onValidationResultsChange = { results ->
                scope.launch {
                    settingsStore.saveVkHashCheckResults(VkHashValidationCodec.encode(results))
                }
            },
            onCheck = { hashes ->
                VkHashValidator.check(context, hashes, activeFingerprint, activeClientIds)
            },
            onAutoSave = { h1, h2, h3, h4, h5, h6 ->
                val cleaned1 = stripVkUrlStatic(h1)
                val cleaned2 = stripVkUrlStatic(h2)
                val cleaned3 = stripVkUrlStatic(h3)
                val cleaned4 = stripVkUrlStatic(h4)
                val cleaned5 = stripVkUrlStatic(h5)
                val cleaned6 = stripVkUrlStatic(h6)
                vkHash1 = cleaned1
                vkHash2 = cleaned2
                vkHash3 = cleaned3
                vkHash4 = cleaned4
                vkHash5 = cleaned5
                vkHash6 = cleaned6
                saveTunnelSettingsNow(normalizeHashes(cleaned1, cleaned2, cleaned3, cleaned4, cleaned5, cleaned6))
            },
            onSave = { h1, h2, h3, h4, h5, h6 ->
                val cleaned1 = stripVkUrlStatic(h1)
                val cleaned2 = stripVkUrlStatic(h2)
                val cleaned3 = stripVkUrlStatic(h3)
                val cleaned4 = stripVkUrlStatic(h4)
                val cleaned5 = stripVkUrlStatic(h5)
                val cleaned6 = stripVkUrlStatic(h6)
                vkHash1 = cleaned1
                vkHash2 = cleaned2
                vkHash3 = cleaned3
                vkHash4 = cleaned4
                vkHash5 = cleaned5
                vkHash6 = cleaned6
                saveTunnelSettingsNow(normalizeHashes(cleaned1, cleaned2, cleaned3, cleaned4, cleaned5, cleaned6)) {
                    showHashesDialog = false
                }
            },
            onDismiss = {
                saveTunnelSettingsNow(normalizeHashes(vkHash1, vkHash2, vkHash3, vkHash4, vkHash5, vkHash6)) {
                    showHashesDialog = false
                }
            }
        )
    }

    if (showVkRevokeDialog) {
        VkTokenRevokeDialog(
            onCancel = { showVkRevokeDialog = false },
            onRevokeToken = {
                showVkRevokeDialog = false
                com.mrtt.client.VkAuthWebViewManager.clearVkSessionCookies()
                scope.launch { settingsStore.clearVkSession() }
            }
        )
    }

    MrttScreen {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            TransportModeSelector(
                settingsStore = settingsStore,
                transportMode = transportMode,
                maxReady = maxTransportReady,
                jitsiReady = jitsiRoomReady,
                onMaxRequested = {
                    if (maxTransportRiskAcknowledged) {
                        scope.launch { settingsStore.saveTransportMode(MrttConstants.Transport.MODE_MAX) }
                    } else {
                        showMaxTransportRiskDialog = true
                    }
                },
            )
            if (maxTransportSelected) {
                MaxTransportSettings(settingsStore, onBindRequested = {
                    onMaxAuthRequested(activeProfile, if (participantMode) parsedMrttLink?.maxGroupId.orEmpty() else "")
                })
            }
            AppSectionCard(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (linkText.isBlank()) {
                    Text(
                        text = "Выберите профиль во вкладке «Профили»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (linkRequiredError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (transportMode == MrttConstants.Transport.MODE_JITSI) {
                    JitsiRoomButton(
                        overrideRoom = jitsiRoomOverride,
                        linkRoom = parsedMrttLink?.jitsiRoom.orEmpty(),
                        onClick = { showJitsiRoomDialog = true },
                    )
                } else if (!vkTransportSelected) {
                    // MAX не использует VK-хеши и токен из ссылки.
                } else {
                    if (linkVkAvailable && ownVkBound) CompactDropdownSetting(
                        title = "Хеши VK",
                        selectedKey = if (preferLinkHashes) "link" else "own",
                        options = listOf("own" to "Мой аккаунт VK", "link" to "Из ссылки"),
                        enabled = !tunnelRunning,
                        onSelected = { choice -> scope.launch { settingsStore.saveVkPreferLinkHashes(choice == "link") } },
                    )
                if (linkHashes.isNotEmpty()) {
                    Text(
                        text = "VK хеши из ссылки: ${linkHashes.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    if (linkTokenActive) {
                        Text(
                            text = "VK токен из ссылки",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    VkHashModeControls(
                        hashSettingsLoaded = hashSettingsLoaded,
                        autoHashMode = autoHashMode,
                        savedHashMode = effectiveHashMode,
                        vkTokenActive = effectiveVkTokenActive,
                        tunnelRunning = tunnelRunning,
                        filledHashCount = filledHashCount,
                        hasInputHashErrors = hasInputHashErrors || hashesRequiredError,
                        hashErrorTexts = hashErrors.filter { !it.contains("короткий") },
                        onOpenHashes = { showHashesDialog = true },
                        onTitleInfo = { showHashModeGeneralDialog = true },
                        onInfo = { mode -> showHashModeDetailDialog = mode },
                        onSelected = ::requestHashMode,
                        onLogin = onVkAuthRequested,
                        onRevokeToken = { showVkRevokeDialog = true },
                        authorizationRequiredError = hashesRequiredError && autoHashMode,
                        manualAllowed = !linkTokenActive,
                        tokenFromLink = linkTokenActive,
                    )
                }
                }

                if (vkTransportSelected) CompactDropdownSetting(
                    title = "Режим кредов",
                    selectedKey = effectiveVkAuthMode,
                    options = listOf(
                        MrttConstants.VkAuth.MODE_CAPTCHA to "Капча",
                        MrttConstants.VkAuth.MODE_CALLS to "Авто",
                        MrttConstants.VkAuth.MODE_AUTO_JS to "Авто ВК",
                    ).filterNot { (mode, _) ->
                        participantMode && linkHashes.isNotEmpty() && mode == MrttConstants.VkAuth.MODE_AUTO_JS
                    },
                    enabled = true,
                    indicatorProvider = { mode ->
                        when (mode) {
                            MrttConstants.VkAuth.MODE_CAPTCHA -> ModeIndicator(progress = 0.30f, color = Color(0xFFE53935))
                            MrttConstants.VkAuth.MODE_CALLS -> ModeIndicator(progress = 0.78f, color = Color(0xFF43A047))
                            MrttConstants.VkAuth.MODE_AUTO_JS -> ModeIndicator(progress = 1.0f, color = Color(0xFF8A9099))
                            else -> null
                        }
                    },
                    onTitleInfo = { showWorkModeGeneralDialog = true },
                    onInfo = { mode -> showWorkModeDetailDialog = mode },
                    onSelected = ::requestWorkMode,
                )

                CompactDropdownSetting(
                    title = "Режим SRTP",
                    selectedKey = srtpMode,
                    options = listOf(
                        MrttConstants.SrtpMode.WRAP to "WRAP-SRTP",
                        MrttConstants.SrtpMode.DTLS to "DTLS-SRTP",
                        MrttConstants.SrtpMode.AUTO to "Авто",
                    ),
                    enabled = true,
                    indicatorProvider = { mode ->
                        when (mode) {
                            MrttConstants.SrtpMode.WRAP -> ModeIndicator(progress = 0.5f, color = Color(0xFF43A047))
                            MrttConstants.SrtpMode.DTLS -> ModeIndicator(progress = 1.0f, color = Color(0xFF43A047))
                            MrttConstants.SrtpMode.AUTO -> ModeIndicator(progress = 1.0f, color = Color(0xFF8A9099))
                            else -> null
                        }
                    },
                    onTitleInfo = null,
                    onInfo = null,
                    onSelected = ::applySrtpMode,
                )

                // UDP/TCP выбирает путь до TURN; транспорт Jitsi идёт через комнату и TURN не использует.
                if (transportMode != MrttConstants.Transport.MODE_JITSI) CompactDropdownSetting(
                    title = "Транспорт",
                    selectedKey = turnTransport,
                    options = listOf(
                        MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS to "TCP",
                        MrttConstants.Tunnel.DEFAULT_TURN_TRANSPORT to "UDP",
                    ),
                    enabled = true,
                    indicatorProvider = { transport ->
                        when (transport) {
                            MrttConstants.Tunnel.DEFAULT_TURN_TRANSPORT -> ModeIndicator(
                                progress = 1.0f,
                                color = Color(0xFF8A9099),
                            )
                            MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS -> ModeIndicator(
                                progress = 0.70f,
                                color = Color(0xFF43A047),
                            )
                            else -> null
                        }
                    },
                    onTitleInfo = { showTurnTransportGeneralDialog = true },
                    onInfo = { transport -> showTurnTransportDetailDialog = transport },
                    onSelected = ::requestTurnTransport,
                )

                CompactDropdownSetting(
                    title = "MTU",
                    selectedKey = tunnelMtu.toString(),
                    options = listOf(
                        "1280" to "1280",
                        "1400" to "1400",
                        "1500" to "1500",
                    ),
                    enabled = true,
                    indicatorProvider = null,
                    onTitleInfo = null,
                    onInfo = null,
                    onSelected = { value ->
                        val mtu = value.toIntOrNull() ?: MrttConstants.Vpn.DEFAULT_MTU
                        scope.launch { settingsStore.saveTunnelMtu(mtu) }
                    },
                )

                if (transportMode == MrttConstants.Transport.MODE_JITSI) {
                    JitsiWorkersControl(
                        value = jitsiWorkers,
                        fixedSeats = parsedMrttLink?.jitsiSeats?.takeIf { it > 0 && jitsiRoomOverride.isBlank() },
                        // Места сервера из ссылки, если комната своя не задана: больше воркеров они не примут.
                        maximum = parsedMrttLink?.jitsiSeats?.takeIf { it > 0 && jitsiRoomOverride.isBlank() }
                            ?: MrttConstants.Tunnel.MAX_JITSI_WORKERS,
                        enabled = !tunnelRunning,
                        onValueChange = { scope.launch { settingsStore.saveJitsiWorkers(it) } },
                    )
                } else {
                    TunnelWorkersControl(
                        value = currentWorkers,
                        maximum = selectableMaxWorkers,
                        enabled = !tunnelRunning,
                        onValueChange = { value ->
                            val newWorkers = value.toInt()
                            scope.launch {
                                settingsStore.saveWorkersPerHash(newWorkers)
                            }
                        },
                        onInfo = { showWorkersInfoDialog = true },
                        fixedWorkers = effectiveServerWorkers,
                    )
                }

                AnimatedVisibility(
                    visible = vkTransportSelected && vkAuthMode == MrttConstants.VkAuth.MODE_CAPTCHA,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (autoCaptchaEnabled) "Авто капча" else "Ручная капча",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoCaptchaEnabled,
                                enabled = !tunnelRunning,
                                onCheckedChange = { enabled ->
                                    autoCaptchaEnabled = enabled
                                    scope.launch {
                                        if (enabled) {
                                            settingsStore.saveCaptchaMode("auto")
                                            settingsStore.saveCaptchaSolveMethod("auto")
                                        } else {
                                            val mode = if (useWVCaptcha) "wv" else "rjs"
                                            settingsStore.saveCaptchaMode(mode)
                                            settingsStore.saveCaptchaSolveMethod(if (mode == "wv" && isManualMode) "manual" else "auto")
                                        }
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = !autoCaptchaEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Метод обхода капчи",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ProtocolChip("WBV", useWVCaptcha, enabled = !tunnelRunning) {
                                            useWVCaptcha = true
                                            isManualMode = wbvManualMode
                                            scope.launch {
                                                settingsStore.saveCaptchaMode("wv")
                                                settingsStore.saveCaptchaSolveMethod(if (wbvManualMode) "manual" else "auto")
                                            }
                                        }
                                        ProtocolChip("RJS", !useWVCaptcha, enabled = !tunnelRunning, isError = false) {
                                            useWVCaptcha = false
                                            isManualMode = false
                                            scope.launch {
                                                settingsStore.saveCaptchaMode("rjs")
                                                settingsStore.saveCaptchaSolveMethod("auto")
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Режим обхода",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (useWVCaptcha) {
                                            ProtocolChip(
                                                "РУЧ",
                                                isManualMode,
                                                enabled = !tunnelRunning,
                                                isError = false
                                            ) {
                                                isManualMode = true
                                                wbvManualMode = true
                                                scope.launch { settingsStore.saveWbvCaptchaSolveMethod("manual") }
                                            }
                                            ProtocolChip(
                                                "АВТ",
                                                !isManualMode,
                                                enabled = !tunnelRunning,
                                                isError = false
                                            ) {
                                                isManualMode = false
                                                wbvManualMode = false
                                                scope.launch { settingsStore.saveWbvCaptchaSolveMethod("auto") }
                                            }
                                        } else {
                                            ProtocolChip(
                                                "АВТ",
                                                selected = true,
                                                enabled = false,
                                                isError = false
                                            ) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }


            if (showHashModeGeneralDialog) {
                HashModeInfoDialog(mode = null, onDismiss = { showHashModeGeneralDialog = false })
            }
            showHashModeDetailDialog?.let { mode ->
                HashModeInfoDialog(mode = mode, onDismiss = { showHashModeDetailDialog = null })
            }

            if (showWorkModeGeneralDialog) {
                WorkModeInfoDialog(mode = null, onDismiss = { showWorkModeGeneralDialog = false })
            }
            showWorkModeDetailDialog?.let { mode ->
                WorkModeInfoDialog(mode = mode, onDismiss = { showWorkModeDetailDialog = null })
            }
            if (showAutoJsRiskDialog) {
                AutoVkRiskDialog(
                    requireAcknowledgement = true,
                    onAcknowledge = {
                        showAutoJsRiskDialog = false
                        pendingAutoJsSelection?.invoke()
                        pendingAutoJsSelection = null
                    },
                    onDoNotRemind = {
                        scope.launch { settingsStore.saveAutoJsRiskAcknowledged(true) }
                        showAutoJsRiskDialog = false
                        pendingAutoJsSelection?.invoke()
                        pendingAutoJsSelection = null
                    },
                )
            }
            if (showJitsiRoomDialog) {
                JitsiRoomDialog(
                    current = jitsiRoomOverride,
                    onSave = { room ->
                        showJitsiRoomDialog = false
                        scope.launch { settingsStore.saveJitsiRoomOverride(room) }
                    },
                    onDismiss = { showJitsiRoomDialog = false },
                )
            }
            if (showWorkersInfoDialog) {
                WorkersInfoDialog(onDismiss = { showWorkersInfoDialog = false })
            }
            if (showTurnTransportGeneralDialog) {
                TurnTransportInfoDialog(mode = null, onDismiss = { showTurnTransportGeneralDialog = false })
            }
            showTurnTransportDetailDialog?.let { transport ->
                TurnTransportInfoDialog(mode = transport, onDismiss = { showTurnTransportDetailDialog = null })
            }
            if (showMaxTransportRiskDialog) {
                MaxTransportRiskDialog(
                    onCancel = { showMaxTransportRiskDialog = false },
                    onContinue = { doNotWarnAgain ->
                        showMaxTransportRiskDialog = false
                        scope.launch {
                            if (doNotWarnAgain) settingsStore.saveMaxTransportRiskAcknowledged(true)
                            settingsStore.saveTransportMode(MrttConstants.Transport.MODE_MAX)
                        }
                    },
                )
            }
            if (showTcpTransportRiskDialog) {
                TcpTransportRiskDialog(
                    onAcknowledge = {
                        showTcpTransportRiskDialog = false
                        pendingTcpTransportSelection?.invoke()
                        pendingTcpTransportSelection = null
                    },
                    onDoNotRemind = {
                        scope.launch { settingsStore.saveTcpTransportRiskAcknowledged(true) }
                        showTcpTransportRiskDialog = false
                        pendingTcpTransportSelection?.invoke()
                        pendingTcpTransportSelection = null
                    },
                )
            }
        }
    }
}
}

@Composable
private fun VkHashAuthControls(
    tunnelRunning: Boolean,
    tokenActive: Boolean,
    tokenFromLink: Boolean,
    isError: Boolean,
    onLogin: () -> Unit,
    onRevokeToken: () -> Unit,
) {
    if (tokenFromLink) {
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.height(44.dp),
            shape = MrttShapes.Pill,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text("Из ссылки", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    } else if (tokenActive) {
        OutlinedButton(
            onClick = onRevokeToken,
            enabled = true,
            modifier = Modifier.height(44.dp),
            shape = MrttShapes.Pill,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text("Активно", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    } else if (isError) {
        OutlinedButton(
            onClick = onLogin,
            modifier = Modifier.height(44.dp),
            shape = MrttShapes.Pill,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text("Вход", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    } else {
        Button(
            onClick = onLogin,
            enabled = true,
            modifier = Modifier.height(44.dp),
            shape = MrttShapes.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        ) {
            Text("Вход", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun VkHashModeControls(
    hashSettingsLoaded: Boolean,
    autoHashMode: Boolean,
    savedHashMode: String?,
    vkTokenActive: Boolean,
    tunnelRunning: Boolean,
    filledHashCount: Int,
    hasInputHashErrors: Boolean,
    hashErrorTexts: List<String>,
    onOpenHashes: () -> Unit,
    onTitleInfo: () -> Unit,
    onInfo: (String) -> Unit,
    onSelected: (String) -> Unit,
    onLogin: () -> Unit,
    onRevokeToken: () -> Unit,
    authorizationRequiredError: Boolean = false,
    manualAllowed: Boolean = true,
    tokenFromLink: Boolean = false,
) {
    if (!hashSettingsLoaded) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!autoHashMode) {
            OutlinedButton(
                onClick = onOpenHashes,
                modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                shape = MrttShapes.Pill,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(
                    1.dp,
                    if (hasInputHashErrors) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Filled.Phone, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Ссылки VK звонков",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (tokenFromLink) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                shape = MrttShapes.Pill,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.colorScheme.primary,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Из ссылки", fontWeight = FontWeight.SemiBold)
            }
        } else if (vkTokenActive) {
            OutlinedButton(
                onClick = onRevokeToken,
                enabled = true,
                modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                shape = MrttShapes.Pill,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            ) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("Сессия VK активна", fontWeight = FontWeight.SemiBold)
            }
        } else if (authorizationRequiredError) {
            OutlinedButton(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                shape = MrttShapes.Pill,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Вход", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onLogin,
                enabled = true,
                modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                shape = MrttShapes.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Вход", fontWeight = FontWeight.SemiBold)
            }
        }

        if (!autoHashMode && hashErrorTexts.isNotEmpty()) {
            Text(
                text = hashErrorTexts.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    CompactDropdownSetting(
        title = "Режим хешей",
        selectedKey = if (!manualAllowed && savedHashMode == MrttConstants.VkAutoHash.MODE_MANUAL) {
            MrttConstants.VkAutoHash.MODE_AUTO_API
        } else {
            savedHashMode ?: MrttConstants.VkAutoHash.MODE_MANUAL
        },
        options = listOf(
            MrttConstants.VkAutoHash.MODE_MANUAL to "Ручной",
            MrttConstants.VkAutoHash.MODE_AUTO_API to "Авто API",
            MrttConstants.VkAutoHash.MODE_AUTO_JS to "Авто ВК",
        ).filterNot { (mode, _) -> !manualAllowed && mode == MrttConstants.VkAutoHash.MODE_MANUAL },
        enabled = true,
        indicatorProvider = { mode ->
            when (mode) {
                MrttConstants.VkAutoHash.MODE_MANUAL -> ModeIndicator(progress = 0.78f, color = Color(0xFF43A047))
                MrttConstants.VkAutoHash.MODE_AUTO_API -> ModeIndicator(progress = 1.0f, color = Color(0xFF8A9099))
                MrttConstants.VkAutoHash.MODE_AUTO_JS -> ModeIndicator(progress = 1.0f, color = Color(0xFF8A9099))
                else -> null
            }
        },
        onTitleInfo = onTitleInfo,
        onInfo = onInfo,
        onSelected = onSelected,
        leadingContent = null,
    )
}

@Composable
private fun TransportModeSelector(
    settingsStore: SettingsStore,
    transportMode: String,
    maxReady: Boolean,
    jitsiReady: Boolean,
    onMaxRequested: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val profiles = remember { com.mrtt.client.profiles.ProfileRepository(appContext) }
    AppSectionCard(contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Транспорт", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransportModeChip(
                    label = "VK",
                    selected = transportMode == MrttConstants.Transport.MODE_VK,
                    enabled = true,
                    onClick = {
                        scope.launch {
                            settingsStore.saveTransportMode(MrttConstants.Transport.MODE_VK)
                            profiles.rememberTransport(MrttConstants.Transport.MODE_VK)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                TransportModeChip(
                    label = "MAX",
                    selected = transportMode == MrttConstants.Transport.MODE_MAX,
                    enabled = true,
                    onClick = {
                        if (transportMode != MrttConstants.Transport.MODE_MAX) onMaxRequested()
                    },
                    modifier = Modifier.weight(1f),
                )
                TransportModeChip(
                    label = "Jitsi",
                    selected = transportMode == MrttConstants.Transport.MODE_JITSI,
                    enabled = true,
                    onClick = {
                        scope.launch {
                            settingsStore.saveTransportMode(MrttConstants.Transport.MODE_JITSI)
                            profiles.rememberTransport(MrttConstants.Transport.MODE_JITSI)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                when {
                    transportMode == MrttConstants.Transport.MODE_MAX && !maxReady ->
                        "Чтобы подключиться через MAX, нажмите «Привязать MAX» ниже."
                    transportMode == MrttConstants.Transport.MODE_JITSI && !jitsiReady ->
                        "Вставьте ссылку на комнату Jitsi в «Комната Jitsi» ниже."
                    transportMode == MrttConstants.Transport.MODE_JITSI ->
                        "Jitsi: туннель идёт через комнату сервера на видеомосте, UDP поддерживается."
                    else -> "Выберите транспорт для подключения."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Как «Ссылки VK звонков»: кнопка показывает, откуда комната, и открывает ввод своей. */
@Composable
private fun JitsiRoomButton(overrideRoom: String, linkRoom: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
            shape = MrttShapes.Pill,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        ) {
            Icon(Icons.Filled.Phone, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Комната Jitsi", fontWeight = FontWeight.SemiBold)
        }
        Text(
            when {
                overrideRoom.isNotBlank() -> "Своя комната: $overrideRoom"
                linkRoom.isNotBlank() -> "Из ссылки: $linkRoom"
                else -> "Вставьте ссылку на комнату, в которой сидит ваш сервер"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Воркеры Jitsi: каждый заходит в комнату отдельным участником, больше мест сервера (16) смысла нет. */
@Composable
private fun JitsiWorkersControl(value: Int, maximum: Int, enabled: Boolean, fixedSeats: Int?, onValueChange: (Int) -> Unit) {
    val minimum = MrttConstants.Tunnel.MIN_JITSI_WORKERS
    val top = maximum.coerceAtLeast(minimum)
    var local by remember(value, top) { mutableStateOf(value.coerceIn(minimum, top).toFloat()) }
    val shown = fixedSeats?.toFloat() ?: local
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (fixedSeats != null) "Потоки (места сервера)" else "Потоки",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                shown.toInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        CompactSteppedSlider(
            value = shown,
            onValueChange = { local = it },
            onValueChangeFinished = { onValueChange(local.toInt()) },
            valueRange = minimum.toFloat()..maxOf(top, shown.toInt()).toFloat(),
            stepSize = 1f,
            enabled = enabled && fixedSeats == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Каждый поток заходит в комнату Jitsi отдельным участником, примерно 4 Мбит/с на каждого. Потоков не больше, чем мест у сервера.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JitsiRoomDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current.isBlank()) "" else "https://$current") }
    val parsed = com.mrtt.client.ui.utils.parseJitsiRoomInput(text)
    com.mrtt.client.ui.common.MrttFormDialog(
        title = "Комната Jitsi",
        onDismiss = onDismiss,
        confirmText = "Сохранить",
        confirmEnabled = (text.isBlank() || parsed != null) && parsed.orEmpty() != current,
        onConfirm = { onSave(parsed.orEmpty()) },
    ) {
        com.mrtt.client.ui.common.JitsiRoomField(text, { text = it })
        Text(
            when {
                text.isBlank() -> "Пусто: комната берётся из ссылки профиля."
                parsed == null -> "Нужна ссылка вида https://сервер/комната, в имени комнаты только латиница и цифры."
                else -> "Сервер должен сидеть в этой же комнате: на своём сервере укажите её в «Серверы» → сервер → «Настройки» → Jitsi."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (text.isNotBlank() && parsed == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransportModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        shape = MrttShapes.Pill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            selectedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            disabledSelectedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ),
    )
}
