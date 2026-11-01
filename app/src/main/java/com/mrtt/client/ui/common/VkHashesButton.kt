package com.mrtt.client.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.MrttConstants
import com.mrtt.client.SettingsStore
import com.mrtt.client.VkHashValidationStatus
import com.mrtt.client.VkHashValidator
import com.mrtt.client.ui.design.MrttShapes
import com.mrtt.client.ui.design.MrttSizes
import com.mrtt.client.ui.dialogs.HashesDialog
import com.mrtt.client.ui.utils.stripVkUrlStatic

/** Кнопка с шестью полями VK-хешей, как во вкладке «Транспорт». Значение хранится строкой через запятую. */
@Composable
internal fun VkHashesButton(value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val fingerprint by store.selectedFingerprint.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_FINGERPRINT)
    val clientIds by store.activeClientIds.collectAsStateWithLifecycle(initialValue = MrttConstants.Tunnel.DEFAULT_CLIENT_IDS)
    var open by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyMap<String, VkHashValidationStatus>()) }
    val hashes = splitVkHashes(value)

    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
        shape = MrttShapes.Pill,
    ) {
        Icon(Icons.Filled.Phone, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (hashes.isEmpty()) "Ссылки VK звонков" else "Ссылки VK звонков: ${hashes.size}",
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (open) {
        val slots = (hashes + List(VK_HASH_SLOTS) { "" }).take(VK_HASH_SLOTS)
        HashesDialog(
            hash1 = slots[0], hash2 = slots[1], hash3 = slots[2],
            hash4 = slots[3], hash5 = slots[4], hash6 = slots[5],
            validationResults = results,
            onValidationResultsChange = { results = it },
            onCheck = { VkHashValidator.check(context, it, fingerprint, clientIds) },
            onSave = { h1, h2, h3, h4, h5, h6 ->
                onValueChange(joinVkHashes(listOf(h1, h2, h3, h4, h5, h6)))
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

private const val VK_HASH_SLOTS = 6

internal fun splitVkHashes(value: String): List<String> =
    value.split(Regex("[,\\s]+")).map(String::trim).filter(String::isNotEmpty).distinct()

internal fun joinVkHashes(hashes: List<String>): String =
    hashes.map { stripVkUrlStatic(it).trim() }.filter(String::isNotEmpty).distinct().joinToString(",")
