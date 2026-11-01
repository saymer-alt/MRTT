package com.csqtt.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.csqtt.client.ui.design.MrttShapes
import com.csqtt.client.ui.design.MrttSizes
import kotlinx.coroutines.delay

@Composable
internal fun AutoVkRiskDialog(
    requireAcknowledgement: Boolean,
    onAcknowledge: () -> Unit,
    onDoNotRemind: (() -> Unit)? = null,
) {
    TimedWarningDialog(
        title = "Внимание!",
        paragraphs = listOf(
            "«Авто ВК» использует неофициальные браузерные методы VK API для создания звонка и получения TURN credentials без анонимных звонков. Сетевой стек PRIMP маскирует HTTP/TLS-профиль под обычный браузер.",
            "Разработчик не может исключить блокировку VK-аккаунта и честно оценить её вероятность. Используйте режим на свой риск; если важен основной аккаунт, выбирайте второстепенный. Рекомендуется включать «Авто ВК», когда «Авто» и «Авто API» не работают.",
        ),
        requireAcknowledgement = requireAcknowledgement,
        onAcknowledge = onAcknowledge,
        onDoNotRemind = onDoNotRemind,
    )
}

@Composable
internal fun TcpTransportRiskDialog(
    onAcknowledge: () -> Unit,
    onDoNotRemind: () -> Unit,
) {
    TimedWarningDialog(
        title = "Внимание!",
        paragraphs = listOf(
            "TCP — экспериментальный режим для сетей, в которых провайдер ограничивает UDP-трафик. TCP используется только между вашим устройством и серверами VK TURN; между VK TURN и вашим VPS трафик всегда идёт по UDP.",
            "Если UDP работает без ограничений, используйте его: UDP стабильнее, быстрее и по своей природе ближе к настоящим звонкам.",
        ),
        requireAcknowledgement = true,
        onAcknowledge = onAcknowledge,
        onDoNotRemind = onDoNotRemind,
    )
}

@Composable
internal fun MaxTransportRiskDialog(
    onCancel: () -> Unit,
    onContinue: (doNotWarnAgain: Boolean) -> Unit,
) {
    var doNotWarnAgain by rememberSaveable { mutableStateOf(false) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }
    val continueEnabled = secondsLeft == 0

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = MrttShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Внимание!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = doNotWarnAgain,
                            role = Role.Checkbox,
                            onValueChange = { doNotWarnAgain = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = doNotWarnAgain, onCheckedChange = null)
                    Text(
                        text = "Больше не предупреждать",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                listOf(
                    "Транспорт MAX работает через ваш личный аккаунт MAX: туннель создаёт звонок в группе и держит его открытым, используя неофициальные методы веб-клиента.",
                    "Такое использование нарушает правила сервиса и заметно повышает риск ограничения или блокировки аккаунта MAX, вплоть до потери привязанного номера. Оценить вероятность бана заранее невозможно.",
                    "Используйте отдельный, второстепенный аккаунт и не привязывайте основной. Продолжая, вы принимаете эти риски на себя.",
                ).forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                        shape = MrttShapes.Pill,
                    ) {
                        Text("Отмена", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onContinue(doNotWarnAgain) },
                        enabled = continueEnabled,
                        modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                        shape = MrttShapes.Pill,
                    ) {
                        Text(
                            text = if (continueEnabled) "Всё равно продолжить" else "Всё равно продолжить ($secondsLeft)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimedWarningDialog(
    title: String,
    paragraphs: List<String>,
    requireAcknowledgement: Boolean,
    onAcknowledge: () -> Unit,
    onDoNotRemind: (() -> Unit)?,
) {
    var secondsLeft by rememberSaveable(requireAcknowledgement) {
        mutableIntStateOf(if (requireAcknowledgement) 8 else 0)
    }
    if (requireAcknowledgement) {
        LaunchedEffect(Unit) {
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft -= 1
            }
        }
    }
    val actionsEnabled = secondsLeft == 0

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = MrttShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onDoNotRemind != null) {
                        OutlinedButton(
                            onClick = onDoNotRemind,
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                            shape = MrttShapes.Pill,
                        ) {
                            Text("Не напоминать", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = onAcknowledge,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth().height(MrttSizes.ControlHeight),
                        shape = MrttShapes.Pill,
                    ) {
                        Text("Понятно", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (!actionsEnabled) {
                        Text(
                            text = secondsLeft.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
