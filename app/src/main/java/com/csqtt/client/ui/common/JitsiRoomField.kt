package com.csqtt.client.ui.common

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.csqtt.client.ui.design.MrttShapes
import com.csqtt.client.ui.utils.parseJitsiRoomInput

/** Поле ссылки на конференцию Jitsi в стиле полей VK хешей: вставка из буфера и удаление. */
@Composable
internal fun JitsiRoomField(value: String, onValueChange: (String) -> Unit, label: String = "Ссылка на комнату Jitsi") {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.trim()) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text("https://meet.example.org/комната", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = value.isNotBlank() && parseJitsiRoomInput(value) == null,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                IconButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                            ?.takeIf { it.isNotBlank() }?.let { onValueChange(it.trim()) }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Outlined.ContentPaste, "Вставить из буфера", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onValueChange("") }, enabled = value.isNotEmpty(), modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        "Удалить ссылку",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = if (value.isNotEmpty()) 1f else 0.38f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MrttShapes.Control,
    )
}
