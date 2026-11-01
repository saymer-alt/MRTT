package com.csqtt.client

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class MrttApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DeployManager.init(this)

        // Tile/widget launches also need state updates before MainActivity exists (upstream PR #3).
        TunnelManager.initObservers(this)

        val settingsStore = SettingsStore(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                settingsStore.loggingEnabled.collect { enabled ->
                    TunnelManager.isLoggingEnabled = enabled
                }
            } catch (e: Exception) {
                Log.e("MrttApp", "Не удалось отслеживать флаг логирования: ${e.message}")
            }
        }
    }
}
