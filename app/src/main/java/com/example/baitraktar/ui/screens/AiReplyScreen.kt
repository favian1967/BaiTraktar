package com.example.baitraktar.ui.screens

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.OnLime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.baitraktar.model.Order
import com.example.baitraktar.ui.components.BackTopBar
import com.example.baitraktar.ui.components.PrimaryButton
import com.example.baitraktar.ui.components.SecondaryButton
import com.example.baitraktar.ui.components.SurfaceCard
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary

/**
 * [order.aiReplyPending] — черновик ещё пишется на сервере (локальная
 * GRAVITAS или облако, см. freelance_parser README → "Черновики откликов").
 * Пока текста нет, отправлять нечего — кнопка ведёт на обновление, а не
 * "на Kwork".
 */
@Composable
fun AiReplyScreen(
    order: Order,
    onBack: () -> Unit,
    onSend: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenKwork: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BackTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
        ) {
            Text("Черновик отклика", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                order.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Space.block))
            if (order.aiReplyPending) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Сервер ещё пишет черновик. Обычно это десятки секунд — "
                            + "обновите экран чуть позже.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            } else if (order.aiReply.isBlank()) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Черновика для этого заказа не будет: заказ уже не свежий, кончился "
                            + "дневной лимит или модель не справилась. Откликнитесь своими словами.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            } else {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            order.aiReply,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f).padding(top = 6.dp),
                        )
                        CopyButton(text = order.aiReply)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Текст скопируется в поле отклика как есть — редактируйте прямо на бирже перед отправкой.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(Space.block))
        }
        Column(modifier = Modifier.padding(horizontal = Space.screen, vertical = 12.dp)) {
            if (order.aiReplyPending) {
                PrimaryButton(
                    text = if (isRefreshing) "Обновляю…" else "Обновить",
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    icon = Icons.Outlined.Refresh,
                )
            } else {
                PrimaryButton(text = "Отметить: откликнулся", onClick = onSend, icon = Icons.AutoMirrored.Outlined.Send)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Открыть на ${order.sourceLabel}", onClick = onOpenKwork, icon = Icons.Outlined.OpenInNew)
            }
        }
    }
}

/** Copies [text] to the clipboard; the icon flips to a check and a "Скопировано" pill shows for a moment. */
@Composable
private fun CopyButton(text: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val tint by animateColorAsState(if (copied) Lime else TextSecondary, label = "copyTint")

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(
            onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("reply", text))) }
                copied = true
            },
            modifier = Modifier.size(48.dp),
        ) {
            Crossfade(targetState = copied, label = "copyIcon") { done ->
                Icon(
                    if (done) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = if (done) "Скопировано" else "Скопировать ответ",
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = copied,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.offset(y = 46.dp),
        ) {
            Text(
                "Скопировано",
                style = MaterialTheme.typography.labelSmall,
                color = OnLime,
                modifier = Modifier
                    .background(Lime, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
