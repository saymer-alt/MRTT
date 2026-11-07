package com.mrtt.client.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mrtt.client.SettingsStore
import com.mrtt.client.BatterySaver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.R

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.verticalScroll
import com.mrtt.client.MrttConstants
import com.mrtt.client.TunnelManager
import com.mrtt.client.ui.components.MrttSegmentedControl
import com.mrtt.client.ui.components.MrttSettingRow
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSizes
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.mrtt.client.ui.dialogs.ImportExportDialog
import com.mrtt.client.ui.dialogs.QrScannerDialog
import com.mrtt.client.ui.dialogs.TurnSubnetsDialog

@Composable
fun FloatingToolbar(
    settingsStore: SettingsStore,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    activeFingerprint: String,
    onFingerprintChange: (String) -> Unit,
    autoPauseOnWifi: Boolean,
    onAutoPauseOnWifiChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vkAuthMode by settingsStore.vkAuthMode.collectAsStateWithLifecycle(
        initialValue = MrttConstants.VkAuth.MODE_CALLS,
    )
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val selectedTurnSubnet by settingsStore.selectedTurnSubnet.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val turnSubnetIpLimit by settingsStore.turnSubnetIpLimit.collectAsStateWithLifecycle(
        initialValue = "ALL",
    )
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showImportExportDialog by rememberSaveable { mutableStateOf(false) }
    var showAutoPauseInfo by rememberSaveable { mutableStateOf(false) }
    var showBatterySaverInfo by rememberSaveable { mutableStateOf(false) }
    val batterySaverMode by settingsStore.batterySaverMode.collectAsStateWithLifecycle(
        initialValue = BatterySaver.DEFAULT_MODE,
    )
    val batterySaverPercent by settingsStore.batterySaverPercent.collectAsStateWithLifecycle(
        initialValue = BatterySaver.DEFAULT_PERCENT,
    )
    var showTurnSubnetsDialog by rememberSaveable { mutableStateOf(false) }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val settingsIconTint = if (isDarkTheme) Color.White else Color.Black

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showQrScanner = true
        } else {
            Toast.makeText(context, "Требуется разрешение на камеру для сканирования QR-кода", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        DeployJobsButton()

        IconButton(
            onClick = { showImportExportDialog = true },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_import_export),
                contentDescription = "Импорт и экспорт конфигурации",
                modifier = Modifier.size(24.dp),
                tint = settingsIconTint,
            )
        }

        IconButton(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    showQrScanner = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_qr_scan),
                contentDescription = "Сканировать QR-код",
                modifier = Modifier.size(24.dp),
                tint = settingsIconTint,
            )
        }

        IconButton(
            onClick = { isExpanded = true },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_gear),
                contentDescription = stringResource(R.string.quick_settings),
                modifier = Modifier.size(24.dp),
                tint = settingsIconTint,
            )
        }
    }

    if (showImportExportDialog) {
        ImportExportDialog(
            settingsStore = settingsStore,
            onDismissRequest = { showImportExportDialog = false }
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismissRequest = { showQrScanner = false },
            onQrCodeScanned = { rawLink ->
                showQrScanner = false
                scope.launch {
                    // QR может содержать и одну ссылку, и целую сабку: обе попадают в «Профили».
                    val added = com.mrtt.client.profiles.ProfileRepository(context).import(rawLink)
                    val message = when (added) {
                        null -> "QR-код не содержит ссылку MRTT"
                        0 -> "Эти ссылки уже есть в профилях"
                        else -> "Добавлено в профили: $added"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (isExpanded) {
            Dialog(
                onDismissRequest = { isExpanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = MrttShapes.Dialog,
                    color = appSectionCardColor(),
                    border = BorderStroke(1.dp, appSectionCardBorderColor()),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Настройки",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    CompactDropdownSetting(
                        title = "Отпечаток",
                        selectedKey = activeFingerprint,
                        options = listOf(
                            "chrome" to "Chrome",
                            "safari" to "Safari",
                            "firefox" to "Firefox",
                            "edge" to "Edge",
                            "opera" to "Opera",
                        ),
                        enabled = true,
                        onSelected = onFingerprintChange,
                    )

                    CompactDropdownSetting(
                        title = "Оформление",
                        selectedKey = currentTheme,
                        options = listOf(
                            MrttConstants.Theme.MODE_SYSTEM to "Система",
                            MrttConstants.Theme.MODE_LIGHT to "Светлая",
                            MrttConstants.Theme.MODE_DARK to "Тёмная",
                        ),
                        enabled = true,
                        onSelected = onThemeChange,
                    )

                    MrttSettingRow(
                        title = "Автопауза при Wi-Fi",
                        checked = autoPauseOnWifi,
                        onCheckedChange = onAutoPauseOnWifiChange,
                        onInfoClick = { showAutoPauseInfo = true },
                        infoContentDescription = "Как работает автопауза при Wi-Fi",
                    )

                    CompactDropdownSetting(
                        title = BatterySaver.TITLE,
                        selectedKey = batterySaverMode,
                        options = BatterySaver.MODES,
                        enabled = true,
                        onSelected = { mode -> scope.launch { settingsStore.saveBatterySaverMode(mode) } },
                        onTitleInfo = { showBatterySaverInfo = true },
                    )

                    // Уровень важен только в «Свой уровень»; в «Авто» работает экономия самого телефона
                    if (batterySaverMode != BatterySaver.MODE_OFF) {
                        CompactDropdownSetting(
                            title = BatterySaver.LEVEL_TITLE,
                            selectedKey = batterySaverPercent.toString(),
                            options = (BatterySaver.PERCENT_CHOICES + batterySaverPercent).distinct().sorted()
                                .map { percent -> percent.toString() to "≤ $percent %" },
                            enabled = batterySaverMode == BatterySaver.MODE_LEVEL,
                            onSelected = { key ->
                                key.toIntOrNull()?.let { percent -> scope.launch { settingsStore.saveBatterySaverPercent(percent) } }
                            },
                        )
                    }

                    run {
                        OutlinedButton(
                            onClick = { showTurnSubnetsDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MrttSizes.ControlHeight),
                            shape = MrttShapes.Pill,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            Text(
                                text = "Выбор TURN подсетей",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBatterySaverInfo) {
        BatterySaverInfoDialog(onDismiss = { showBatterySaverInfo = false })
    }
    if (showAutoPauseInfo) {
        WifiAutoPauseInfoDialog(onDismiss = { showAutoPauseInfo = false })
    }

    if (showTurnSubnetsDialog) {
        TurnSubnetsDialog(
            selectedSubnetId = selectedTurnSubnet,
            turnSubnetIpLimit = turnSubnetIpLimit,
            onSelectSubnet = { id ->
                scope.launch { settingsStore.saveSelectedTurnSubnet(id) }
            },
            onSelectIpLimit = { limit ->
                scope.launch { settingsStore.saveTurnSubnetIpLimit(limit) }
            },
            onDismiss = { showTurnSubnetsDialog = false },
        )
    }

}
