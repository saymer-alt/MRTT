package com.mrtt.client

import com.mrtt.client.ui.design.mrttGlass
import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.mrtt.client.ui.AppUpdateDialog
import com.mrtt.client.ui.ConnectionTab
import com.mrtt.client.ui.ExceptionsTab
import com.mrtt.client.ui.FloatingToolbar
import com.mrtt.client.ui.LogsTab
import com.mrtt.client.ui.SettingsTab
import com.mrtt.client.MrttConstants
import com.mrtt.client.ui.InfoTab
import com.mrtt.client.ui.components.LocalMrttHeaderActions
import com.mrtt.client.ui.design.MrttMotion
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.dialogs.VkAuthDialog
import com.mrtt.client.ui.dialogs.MaxAuthDialog
import com.mrtt.client.ui.utils.parseMrttLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val appSettingsStore by lazy { SettingsStore(applicationContext) }
    private var renderUi by mutableStateOf(false)
    private var showVkAuthDialog by mutableStateOf(false)
    private var batteryPromptInFlight = false

    private val batteryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryPromptInFlight = false
        lifecycleScope.launch {
            appSettingsStore.markBatteryOptimizationPromptHandled()
        }
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        checkAndRequestBattery()
    }

    companion object {
        @Volatile
        var activeActivities = 0
        val isForeground: Boolean
            get() = activeActivities > 0
    }

    override fun onStart() {
        super.onStart()
        activeActivities++
        renderUi = true
        ManlCaptchaWebViewManager.checkAndShowPendingCaptcha(this)
    }

    override fun onStop() {
        renderUi = false
        activeActivities = (activeActivities - 1).coerceAtLeast(0)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val discardStaleUiState = UpdateLaunchGuard.consumeStaleSavedState(this)
        super.onCreate(if (discardStaleUiState) null else savedInstanceState)

        importMrttLink(intent)

        TunnelManager.initObservers(this)

        enableEdgeToEdge()

        checkAndRequestNotifications()

        setContent {
            val authContext = LocalContext.current.applicationContext
            val authScope = rememberCoroutineScope()
            val uiStateHolder = rememberSaveableStateHolder()
            val settingsStore = remember { appSettingsStore }
            val themeMode by settingsStore.themeMode.collectAsStateWithLifecycle(initialValue = MrttConstants.Theme.MODE_SYSTEM)
            var maxAuthProfile by rememberSaveable { mutableStateOf<Int?>(null) }
            var maxAuthGroupId by rememberSaveable { mutableStateOf("") }
            if (renderUi) {
                uiStateHolder.SaveableStateProvider("main_ui") {
            val activeFingerprint by settingsStore.selectedFingerprint.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_FINGERPRINT)
            val scope = rememberCoroutineScope()

            val baseDensity = LocalDensity.current
            val fixedDensity = remember(baseDensity.density) { Density(baseDensity.density, 1f) }

            CompositionLocalProvider(LocalDensity provides fixedDensity) {
            MrttAppTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(
                        settingsStore = settingsStore,
                        onMaxAuthRequested = { profile, groupId ->
                            maxAuthGroupId = groupId
                            maxAuthProfile = profile
                        },
                        themeMode = themeMode,
                        onThemeChange = { mode ->
                            scope.launch {
                                settingsStore.saveThemeMode(mode)
                            }
                        },
                        activeFingerprint = activeFingerprint,
                        onFingerprintChange = { fp ->
                            scope.launch { settingsStore.saveFingerprint(fp) }
                        },
                        onVkAuthRequested = { showVkAuthDialog = true },
                    )
                }
            }
            }
                }
            }
            // Keep the login WebView alive when onStop removes the main screen.
            maxAuthProfile?.let { profile ->
                MrttAppTheme(themeMode = themeMode) {
                    MaxAuthDialog(
                        requiredGroupId = maxAuthGroupId,
                        onBind = { credentials ->
                            settingsStore.saveMaxBinding(credentials.token, credentials.groupId, profile)
                        },
                        onDismiss = { maxAuthProfile = null },
                    )
                }
            }
            if (showVkAuthDialog) {
                VkAuthDialog(
                    onToken = { payload ->
                        showVkAuthDialog = false
                        authScope.launch {
                            if (payload.cookies.isBlank()) {
                                authContext.showRaisedToast("VK не отдал сессию, войдите ещё раз", Toast.LENGTH_SHORT)
                            } else {
                                appSettingsStore.saveVkSession(payload.cookies, payload.userId)
                                authContext.showRaisedToast("VK привязан", Toast.LENGTH_SHORT)
                            }
                        }
                    },
                    onDismiss = { showVkAuthDialog = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importMrttLink(intent)
    }

    private fun importMrttLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val raw = intent.dataString ?: return
        val subscription = com.mrtt.client.profiles.decodeSubscription(raw)
        val link = parseMrttLink(raw)
        if (subscription == null && link == null) {
            showRaisedToast("Неверная ссылка MRTT", Toast.LENGTH_SHORT)
            return
        }
        val message = subscription?.let { "Подписка «${it.name}»: конфигов ${it.links.size}" }
            ?: "Хост: ${link?.host}\nPeer: ${link?.port}"
        android.app.AlertDialog.Builder(this)
            .setTitle("Добавить в профили")
            .setMessage(message)
            .setPositiveButton("Добавить") { _, _ ->
                lifecycleScope.launch {
                    val added = com.mrtt.client.profiles.ProfileRepository(this@MainActivity).import(raw)
                    showRaisedToast(
                        if (added == 0) "Эти ссылки уже есть в профилях" else "Добавлено в профили",
                        Toast.LENGTH_SHORT,
                    )
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun checkAndRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndRequestBattery()
            }
        } else {
            checkAndRequestBattery()
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkAndRequestBattery() {
        if (batteryPromptInFlight) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        lifecycleScope.launch {
            val shouldRequest = shouldRequestBatteryOptimizationExemption(
                promptHandled = appSettingsStore.isBatteryOptimizationPromptHandled(),
                alreadyExempt = powerManager.isIgnoringBatteryOptimizations(packageName),
            )
            if (!shouldRequest) {
                if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    appSettingsStore.markBatteryOptimizationPromptHandled()
                }
                return@launch
            }

            batteryPromptInFlight = true
            val launched = runCatching {
                batteryLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        "package:$packageName".toUri(),
                    ),
                )
                true
            }.getOrDefault(false)
            if (!launched) {
                batteryPromptInFlight = false
                appSettingsStore.markBatteryOptimizationPromptHandled()
            }
        }
    }
}

private data class NavItem(
    val id: Int,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val navItems = listOf(
    NavItem(0, R.string.nav_connection, Icons.Filled.PowerSettingsNew, Icons.Filled.PowerSettingsNew),
    NavItem(5, R.string.nav_profiles, Icons.Filled.Person, Icons.Outlined.Person),
    NavItem(1, R.string.nav_tunnel, Icons.Filled.VpnKey, Icons.Outlined.VpnKey),
    NavItem(2, R.string.nav_deploy, Icons.Filled.Cloud, Icons.Outlined.Cloud),
    NavItem(3, R.string.nav_logs, Icons.Filled.Terminal, Icons.Outlined.Terminal),
    NavItem(4, R.string.nav_exceptions, Icons.Filled.FilterList, Icons.Outlined.FilterList),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsStore: SettingsStore,
    onMaxAuthRequested: (Int, String) -> Unit,
    themeMode: String = MrttConstants.Theme.MODE_SYSTEM,
    onThemeChange: (String) -> Unit = {},
    activeFingerprint: String = MrttConstants.Tunnel.DEFAULT_FINGERPRINT,
    onFingerprintChange: (String) -> Unit = {},
    onVkAuthRequested: () -> Unit = {},
) {
    val unreadErrors by TunnelManager.unreadErrorCount.collectAsStateWithLifecycle()
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val tunnelAutoPausedForWifi by TunnelManager.autoPausedForWifi.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val tunnelAuthSettings by settingsStore.tunnelAuthSnapshot.collectAsStateWithLifecycle(
        initialValue = TunnelAuthSnapshot()
    )
    val autoPauseOnWifi by settingsStore.autoPauseOnWifi.collectAsStateWithLifecycle(initialValue = false)
    val excludedApps by settingsStore.excludedApps.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedExcludedApps ?: ""
    )
    val whitelistApps by settingsStore.whitelistApps.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedWhitelistApps ?: ""
    )
    val showSystemApps by settingsStore.showSystemApps.collectAsStateWithLifecycle(initialValue = false)
    val isWhitelist by settingsStore.isWhitelist.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedIsWhitelist ?: false
    )
    val infoActionsExpanded by settingsStore.infoActionsExpanded.collectAsStateWithLifecycle(initialValue = false)
    val infoProjectExpanded by settingsStore.infoProjectExpanded.collectAsStateWithLifecycle(initialValue = false)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var tunnelValidationRequest by rememberSaveable { mutableIntStateOf(0) }
    var wasRunning by remember { mutableStateOf(tunnelRunning) }
    val updateCheckIntervalHours by settingsStore.updateCheckIntervalHours.collectAsStateWithLifecycle(
        initialValue = DEFAULT_UPDATE_CHECK_INTERVAL_HOURS
    )
    var pendingRelease by remember { mutableStateOf<AppReleaseInfo?>(null) }
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v")}" }
    val safeBottomInset = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
    val navToolbarHeight = 66.dp
    val navToolbarBottomMargin = 10.dp
    val navContentGap = 5.dp
    val navOverlayReserve = safeBottomInset + navToolbarBottomMargin + navToolbarHeight + navContentGap

    val serverRoutingActive by TunnelManager.serverRoutingActive.collectAsStateWithLifecycle()

    val activeNavItems = navItems
    val tabStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(Unit) {
        // Старые слоты «владельца/участника» один раз переезжают в «Профили» и «Серверы».
        com.mrtt.client.profiles.ProfileRepository(context).migrateLegacy()
        com.mrtt.client.servers.ServerRepository(context).migrateLegacy()
    }

    LaunchedEffect(tunnelRunning, tunnelAutoPausedForWifi) {
        if (wasRunning && !tunnelRunning && !tunnelAutoPausedForWifi) {
            TunnelManager.startCooldown(MrttConstants.Timeouts.VPN_PERMISSION_COOLDOWN_MS)
        }
        wasRunning = tunnelRunning
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) TunnelManager.clearUnreadErrors()
    }

    LaunchedEffect(updateCheckIntervalHours) {
        if (updateCheckIntervalHours == UPDATE_CHECK_NEVER) return@LaunchedEffect

        val intervalMillis = updateIntervalHoursToMillis(updateCheckIntervalHours)
            ?: updateIntervalHoursToMillis(DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)
            ?: 12L * 60L * 60L * 1000L

        suspend fun runUpdateCheck(reason: String) {
            val checkedAt = System.currentTimeMillis()
            val release = fetchLatestReleaseInfo(currentVersion)
            settingsStore.saveUpdateState(
                lastCheckAt = checkedAt,
                latestVersion = release?.versionTag ?: "",
                error = if (release == null) "Не удалось проверить" else ""
            )

            if (release == null) {
                Log.w("MRTT", "[WARN] Update check: no release info, local=$currentVersion reason=$reason")
                return
            }

            val hasUpdate = isNewerVersion(currentVersion, release.versionTag)
            val postponeVer = settingsStore.updatePostponeVersion.first()
            val postponeUntil = settingsStore.updatePostponeUntil.first()
            val isPostponed = postponeVer == release.versionTag && checkedAt < postponeUntil
            Log.i(
                "MRTT",
                "Update check: local=$currentVersion remote=${release.versionTag} newer=$hasUpdate postponed=$isPostponed reason=$reason"
            )

            if (hasUpdate && !isPostponed) {
                settingsStore.saveUpdateDialogShown(release.versionTag, checkedAt)
                pendingRelease = release
            }
        }

        runUpdateCheck("startup")

        while (isActive) {
            val now = System.currentTimeMillis()
            val lastCheck = settingsStore.updateLastCheckAt.first()
            val nextCheckAt = lastCheck + intervalMillis
            val waitMs = (nextCheckAt - now).coerceAtLeast(intervalMillis)
            delay(waitMs)
            if (isActive) {
                runUpdateCheck("periodic")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackdrop(modifier = Modifier.matchParentSize())

        CompositionLocalProvider(
            LocalMrttHeaderActions provides {
                FloatingToolbar(
                    settingsStore = settingsStore,
                    currentTheme = themeMode,
                    onThemeChange = onThemeChange,
                    activeFingerprint = activeFingerprint,
                    onFingerprintChange = onFingerprintChange,
                    autoPauseOnWifi = autoPauseOnWifi,
                    onAutoPauseOnWifiChange = { enabled ->
                        scope.launch { settingsStore.saveAutoPauseOnWifi(enabled) }
                    },
                )
            },
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                containerColor = Color.Transparent,
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = navOverlayReserve),
                    ) {
                        key(selectedTab) {
                            val tab = selectedTab
                            val tabStateKey: Any = when (tab) {
                                0 -> "connection:${tunnelAuthSettings.profile}"
                                1 -> "tunnel:${tunnelAuthSettings.profile}"
                                2 -> "servers"
                                else -> tab
                            }
                            tabStateHolder.SaveableStateProvider(tabStateKey) {
                                when (tab) {
                                0 -> if (tunnelAuthSettings.isLoaded) {
                                    ConnectionTab(
                                        settingsStore = settingsStore,
                                        tunnelAuthSettings = tunnelAuthSettings,
                                        onInvalidConfiguration = {
                                            selectedTab = 1
                                            tunnelValidationRequest += 1
                                        },
                                        onProfileRequired = { selectedTab = 5 },
                                    )
                                } else {
                                    Spacer(modifier = Modifier.fillMaxSize())
                                }
                                1 -> if (tunnelAuthSettings.isLoaded) {
                                    SettingsTab(
                                        settingsStore = settingsStore,
                                        tunnelAuthSettings = tunnelAuthSettings,
                                        onMaxAuthRequested = onMaxAuthRequested,
                                        validationRequest = tunnelValidationRequest,
                                        onVkAuthRequested = onVkAuthRequested,
                                    )
                                } else {
                                    Spacer(modifier = Modifier.fillMaxSize())
                                }
                                2 -> com.mrtt.client.ui.servers.ServersTab()
                                // Выбранный профиль сразу открывается в «Транспорт» со своими настройками.
                                5 -> com.mrtt.client.ui.profiles.ProfilesTab(onOpened = { selectedTab = 1 })
                                3 -> LogsTab(settingsStore)
                                4 -> ExceptionsTab(
                                    settingsStore = settingsStore,
                                    savedExcluded = excludedApps,
                                    savedWhitelist = whitelistApps,
                                    showSystemApps = showSystemApps,
                                    isWhitelist = isWhitelist,
                                    serverRoutingActive = serverRoutingActive,
                                )
                                else -> Spacer(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }

                    ProxyNavigationBar(
                        navItems = activeNavItems,
                        selectedTab = selectedTab,
                        unreadErrors = unreadErrors,
                        tunnelRunning = tunnelRunning,
                        onTabSelected = { index ->
                            if (selectedTab != index) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectedTab = index
                                if (index == 3) TunnelManager.clearUnreadErrors()
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    pendingRelease?.let { release ->
        AppUpdateDialog(
            release = release,
            onPostpone = {
                pendingRelease = null
                context.showRaisedToast("Обновление отложено на 24 часа.", Toast.LENGTH_SHORT)
                scope.launch {
                    val now = System.currentTimeMillis()
                    settingsStore.saveUpdatePostpone(
                        version = release.versionTag,
                        until = now + 24L * 60L * 60L * 1000L
                    )
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_POSTPONED,
                        actedAt = now
                    )
                }
            },
            onUpdate = {
                pendingRelease = null
                scope.launch {
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_UPDATE,
                        actedAt = System.currentTimeMillis()
                    )
                    openReleaseUrl(context, release.releaseUrl)
                }
            }
        )
    }
}

@Composable
private fun ProxyNavigationBar(
    navItems: List<NavItem>,
    selectedTab: Int,
    unreadErrors: Int,
    tunnelRunning: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val selectedColor = colors.onSurface
    val unselectedColor = colors.onSurfaceVariant
    val indicatorShape = RoundedCornerShape(20.dp)
    val selectedVisualIndex = remember(selectedTab, navItems) {
        navItems.indexOfFirst { it.id == selectedTab }.coerceAtLeast(0)
    }
    val indicatorIndex = remember { Animatable(selectedVisualIndex.toFloat()) }
    LaunchedEffect(selectedVisualIndex) {
        indicatorIndex.animateTo(
            targetValue = selectedVisualIndex.toFloat(),
            animationSpec = tween(
                durationMillis = MrttMotion.Fast,
                easing = MrttMotion.StandardEasing,
            )
        )
    }
    val dragVisualIndex = indicatorIndex.value

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val trackPadding = 4.dp
        val itemWidth = (maxWidth - trackPadding * 2) / navItems.size
        val indicatorOffset = trackPadding + itemWidth * dragVisualIndex

        Surface(
            shape = MrttShapes.Card,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().mrttGlass(MrttShapes.Card)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .padding(vertical = 7.dp)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .mrttGlass(indicatorShape, colors.onSurface)
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = trackPadding, vertical = 7.dp)
                        .selectableGroup()
                ) {
                    navItems.forEachIndexed { index, item ->
                        val emphasis = (1f - abs(index - dragVisualIndex)).coerceIn(0f, 1f)
                        val iconColor = lerp(unselectedColor, selectedColor, emphasis)
                        val label = stringResource(item.labelRes)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(indicatorShape)
                                .selectable(
                                    selected = item.id == selectedTab,
                                    role = Role.Tab,
                                    onClick = { onTabSelected(item.id) },
                                ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = if (emphasis > 0.55f) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = iconColor
                                )
                                if (item.id == 3 && unreadErrors > 0) {
                                    Badge(
                                        containerColor = if (tunnelRunning) colors.primary else MrttColors.warning,
                                        contentColor = if (tunnelRunning) colors.onPrimary else Color.Black,
                                        modifier = Modifier.offset(x = 12.dp, y = (-8).dp)
                                    ) {
                                        Text("$unreadErrors")
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = if (emphasis > 0.55f) FontWeight.SemiBold else FontWeight.Medium,
                                color = iconColor,
                                maxLines = 1,
                                softWrap = false,
                                // Шрифт уменьшается, пока подпись не влезет целиком при любом масштабе текста.
                                autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 10.sp, stepSize = 0.5.sp),
                                // Отступ от скруглённых краёв индикатора: подпись ужимается, а не обрезается.
                                modifier = Modifier.padding(horizontal = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openReleaseUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        context.showRaisedToast("Не удалось открыть ссылку", Toast.LENGTH_SHORT)
    }
}
