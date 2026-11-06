package com.mrtt.client.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mrtt.client.showRaisedToast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Имя файла журнала: `MRTT log 2026-11-06 15-30-12.txt`. */
internal fun logFileName(prefix: String, now: Date = Date()): String =
    "$prefix ${SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(now)}.txt"

/** Отправляет журнал текстовым файлом, а не текстом сообщения. */
internal fun Context.shareLogFile(prefix: String, text: String) {
    try {
        // Живёт только последний отправленный файл: журналы не копятся в кэше.
        val directory = File(cacheDir, "logs").apply {
            mkdirs()
            listFiles()?.forEach(File::delete)
        }
        val file = File(directory, logFileName(prefix)).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(this, "$packageName.logs", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Поделиться логом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        showRaisedToast("Не удалось открыть отправку")
    }
}
