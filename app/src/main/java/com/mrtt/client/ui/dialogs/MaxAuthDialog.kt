package com.mrtt.client.ui.dialogs

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mrtt.client.MaxAuthCredentials
import com.mrtt.client.MaxAuthSession
import com.mrtt.client.MaxAuthWebViewManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MaxAuthDialog(
    requiredGroupId: String,
    onBind: suspend (MaxAuthCredentials) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentOnBind by rememberUpdatedState(onBind)
    var uiReady by remember { mutableStateOf(false) }
    var tokenDetected by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var bindingSaved by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val webView = remember { MaxAuthWebViewManager.acquire(context, darkTheme) }
    val session = remember {
        MaxAuthSession(
            requiredGroupId = requiredGroupId,
            darkTheme = darkTheme,
            onUiReady = { uiReady = true },
            onLoading = { uiReady = false },
            onTokenDetected = { tokenDetected = true },
            onCredentials = { credentials ->
                saving = true
                scope.launch {
                    try {
                        currentOnBind(credentials)
                        bindingSaved = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorMessage = "Не удалось сохранить привязку. Попробуйте ещё раз."
                    } finally {
                        saving = false
                    }
                }
            },
            onError = { errorMessage = it },
        )
    }

    DisposableEffect(session, webView, lifecycleOwner) {
        session.attach(webView)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> session.pause()
                Lifecycle.Event.ON_START -> session.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            session.stop()
            MaxAuthWebViewManager.release(webView)
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().imePadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Привязка MAX", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss, enabled = !saving) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                Text(
                    when {
                        bindingSaved -> "Токен и группа привязаны. MAX готов к подключению."
                        saving -> "Токен получен. Группа найдена — сохраняем привязку…"
                        tokenDetected && requiredGroupId.isNotEmpty() -> "Токен получен. Открываем группу из ссылки подключения…"
                        tokenDetected -> "Токен получен. Откройте группу для привязки."
                        else -> "Ожидание входа. Войдите в свой аккаунт MAX."
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bindingSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                isFocusable = false
                                isFocusableInTouchMode = false
                                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                                addView(
                                    webView,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                                post { webView.requestFocus() }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if ((!uiReady && errorMessage == null) || saving) CircularProgressIndicator()
                }
                errorMessage?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = {
                            errorMessage = null
                            uiReady = false
                            tokenDetected = false
                            session.retry()
                        }) { Text("Повторить") }
                    }
                }
                if (bindingSaved) {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Готово")
                    }
                }
            }
        }
    }
}
