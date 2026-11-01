package com.mrtt.client

import android.content.Context
import android.os.Build
import java.io.File
import java.util.UUID

/** Стабильный ID установки: по нему сервер привязывает устройство к ссылке. */
internal object DeviceIdentity {
    private const val FILE_NAME = "device_id"

    @Synchronized
    fun id(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        val existing = runCatching { file.readText().trim() }.getOrDefault("")
        if (existing.matches(Regex("[A-Za-z0-9-]{8,64}"))) return existing
        val created = UUID.randomUUID().toString()
        runCatching { file.writeText(created) }
        return created
    }

    fun name(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
        .ifBlank { "Android" }
}
