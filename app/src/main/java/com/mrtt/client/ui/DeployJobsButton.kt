package com.mrtt.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrtt.client.DeployManager
import com.mrtt.client.ui.common.MrttFormDialog

/** Кнопка в шапке, пока идёт установка или удаление: в окне статус каждого сервера и отмена. */
@Composable
internal fun DeployJobsButton() {
    val jobs by DeployManager.jobs.collectAsStateWithLifecycle()
    var opened by rememberSaveable { mutableStateOf(false) }
    if (jobs.isEmpty()) {
        opened = false
        return
    }

    IconButton(
        onClick = { opened = true },
        modifier = Modifier.size(38.dp).semantics { contentDescription = "Установка на серверах: ${jobs.size}" },
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
    }

    if (opened) {
        MrttFormDialog(
            title = "Установка на серверах",
            onDismiss = { opened = false },
            confirmText = "Отменить все".takeIf { jobs.size > 1 },
            onConfirm = DeployManager::cancelAll,
            dismissText = "Закрыть",
        ) {
            jobs.forEach { (id, job) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(job.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            job.step,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    IconButton(onClick = { DeployManager.cancel(id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Отменить: ${job.title}")
                    }
                }
            }
        }
    }
}
