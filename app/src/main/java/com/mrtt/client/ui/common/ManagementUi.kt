package com.mrtt.client.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.mrtt.client.ui.components.MrttGlassButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrtt.client.showRaisedToast
import com.mrtt.client.ui.appSectionCardBorderColor
import com.mrtt.client.ui.appSectionCardColor
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSizes
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Запуск запроса к серверу: ошибки и успех показываются тостом, интерфейс не падает. */
internal class ActionRunner(private val context: Context, private val scope: CoroutineScope) {
    fun run(success: String? = null, after: () -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess {
                    success?.let { context.showRaisedToast(it) }
                    after()
                }
                .onFailure { context.showRaisedToast(it.message ?: "Ошибка запроса к серверу") }
        }
    }
}

@Composable
internal fun rememberActionRunner(): ActionRunner {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { ActionRunner(context, scope) }
}

@Composable
internal fun MrttFormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = "Сохранить",
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    dismissText: String = "Отмена",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MrttShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = MrttSizes.ControlHeight),
                        shape = MrttShapes.Pill,
                    ) { Text(dismissText) }
                    if (confirmText != null) {
                        Button(
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            modifier = Modifier.weight(1f).heightIn(min = MrttSizes.ControlHeight),
                            shape = MrttShapes.Pill,
                        ) { Text(confirmText, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MrttFormDialog(title = title, onDismiss = onDismiss, confirmText = confirmText, onConfirm = onConfirm) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    secret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(if (numeric) it.filter(Char::isDigit).take(6) else it) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = MrttShapes.Control.takeIf { singleLine } ?: RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

internal fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    showRaisedToast("Скопировано")
}

/** Ссылка подписки или сабки: текст, QR, копирование и отправка. */
@Composable
internal fun LinkShareDialog(title: String, link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(link) { runCatching { qrBitmap(link).asImageBitmap() }.getOrNull() }
    MrttFormDialog(
        title = title,
        onDismiss = onDismiss,
        dismissText = "Закрыть",
        confirmText = "Поделиться",
        onConfirm = {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
            context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    ) {
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(bitmap = bitmap, contentDescription = "QR-код: $title", modifier = Modifier.size(240.dp))
            }
        }
        Text(
            link,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(max = 90.dp),
        )
        OutlinedButton(
            onClick = { context.copyToClipboard(title, link) },
            modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
            shape = MrttShapes.Pill,
        ) { Text("Копировать ссылку") }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val units = listOf("КБ", "МБ", "ГБ", "ТБ")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

internal fun formatDate(unixSeconds: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(unixSeconds * 1000))

/** Сколько дней осталось (0 — бессрочно), как ждёт сервер в поле days. */
internal fun remainingDays(expires: Long): Int =
    if (expires == 0L) 0 else ((expires * 1000 - System.currentTimeMillis()).coerceAtLeast(0) / 86_400_000L + 1).toInt()
