package com.csqtt.client

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Полный журнал для отправки: все строки нативного клиента и приложения, без фильтров экрана «Логи».
 * Пароли, токены и ссылки подключения затираются, чтобы журналом можно было делиться.
 */
internal object FullLog {
    private const val MAX_LINES = 4000
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val secrets = listOf(
        Regex("""(?i)\b(password|pass|pwd|token|access_token|secret|key|hashes|hash|cookie|remixsid)=([^&\s"']+)""") to "$1=***",
        Regex("""(?i)"(password|token|access_token|secret|cookie)"\s*:\s*"[^"]*"""") to "\"$1\":\"***\"",
        Regex("""vk1\.a\.[A-Za-z0-9_\-]+""") to "vk1.a.***",
        Regex("""(?i)\b(mrtt|csqttr|csqtt|mrttsub)://[A-Za-z0-9_\-+/=%.]+""") to "$1://***",
    )

    fun redact(text: String): String = secrets.fold(text) { acc, (pattern, replacement) -> acc.replace(pattern, replacement) }

    fun add(source: String, line: String) {
        if (line.isBlank()) return
        val entry = "${synchronized(time) { time.format(Date()) }} [$source] ${redact(line.trim()).take(1000)}"
        synchronized(lines) {
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(entry)
        }
    }

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    fun clear() = synchronized(lines) { lines.clear() }
}
