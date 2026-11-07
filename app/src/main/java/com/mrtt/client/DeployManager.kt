package com.mrtt.client

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeployJob(val progress: Float, val step: String, val startedAt: Long)

object DeployManager {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val message = throwable.message?.take(160) ?: throwable.javaClass.simpleName
        writeError(
            "Uncaught deploy coroutine error: $message\n" +
                throwable.stackTraceToString().take(1200)
        )
        TunnelManager.addDeployErrorLog("Необработанная ошибка установки: $message")
        stopAll("Ошибка установки: $message")
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    /** Идущие установки и удаления по id сервера. */
    val jobs = MutableStateFlow<Map<String, DeployJob>>(emptyMap())
    val isDeploying = MutableStateFlow(false)

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, java.io.Closeable>()
    private val results = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var errorsFile: File? = null

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        errorsFile = File(dir, "errors.log")
    }

    @Synchronized
    fun writeError(msg: String) {
        val file = errorsFile ?: return
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            file.appendText("[$timestamp] $msg\n")

            if (file.length() > 500_000) {
                val text = file.readText()
                file.writeText(text.takeLast(200_000))
            }
        } catch (_: Exception) { }
    }

    /** false, если на этом сервере уже что-то идёт. */
    @Synchronized
    fun startDeploy(id: String): Boolean {
        val now = System.currentTimeMillis()
        jobs.value[id]?.let { running ->
            if (now - running.startedAt <= 30 * 60 * 1000) return false
            writeError("Автосброс: предыдущий деплой завис >30 мин")
            stopDeploy(id)
        }
        if (jobs.value.isEmpty()) TunnelManager.clearLogs()
        results.remove(id)
        jobs.value = jobs.value + (id to DeployJob(0f, "Инициализация...", now))
        isDeploying.value = true
        return true
    }

    @Synchronized
    fun stopDeploy(id: String, result: String = "") {
        jobs.value = jobs.value - id
        isDeploying.value = jobs.value.isNotEmpty()
        if (result.isNotBlank()) results[id] = result
        try { sessions.remove(id)?.close() } catch (_: Exception) {}
    }

    @Synchronized
    fun stopAll(result: String = "") = jobs.value.keys.forEach { stopDeploy(it, result) }

    fun lastResult(id: String): String = results[id].orEmpty()

    fun setSession(id: String, session: java.io.Closeable?) {
        if (session == null) sessions.remove(id) else if (id in jobs.value) sessions[id] = session
    }

    @Synchronized
    fun updateProgress(id: String, progress: Float, step: String) {
        val job = jobs.value[id] ?: return
        jobs.value = jobs.value + (id to job.copy(progress = progress, step = step))
    }
}
