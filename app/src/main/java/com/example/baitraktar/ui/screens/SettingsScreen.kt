package com.example.baitraktar.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.baitraktar.data.OrdersRepository
import com.example.baitraktar.data.SettingsStore
import com.example.baitraktar.ui.AuthResult
import com.example.baitraktar.ui.authenticate
import com.example.baitraktar.ui.canProtectToken
import com.example.baitraktar.ui.findFragmentActivity
import com.example.baitraktar.ui.components.KworkSwitch
import com.example.baitraktar.ui.components.PrimaryButton
import com.example.baitraktar.ui.components.SectionRule
import com.example.baitraktar.ui.components.SurfaceCard
import com.example.baitraktar.ui.components.TextAction
import com.example.baitraktar.ui.theme.LabelStyle
import com.example.baitraktar.ui.theme.Ink
import com.example.baitraktar.ui.theme.Lime
import com.example.baitraktar.ui.theme.MonoType
import com.example.baitraktar.ui.theme.OutlineSoft
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private enum class ConnectionCheck { IDLE, CHECKING, OK, FAILED }

/**
 * Settings as a log, not as iOS groups: section rules, rows on the field separated by
 * hairlines, values in mono on the right. Icons are quiet (secondary tint, no discs) —
 * lime is reserved for state.
 *
 * "Сервер" — адрес и токен парсера на VPS (см. freelance_parser README → "API для
 * мобильного приложения"). Без них приложение не покажет ни одного реального заказа.
 */
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    repository: OrdersRepository,
    onOpenFilters: () -> Unit,
    onSettingsSaved: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsState()
    var urlInput by remember { mutableStateOf(settings.baseUrl) }
    /** Адрес скрыт, пока это общий сервер: людям нужен только ключ из бота. */
    var customServer by remember { mutableStateOf(settings.baseUrl != SettingsStore.DEFAULT_SERVER_URL) }
    var tokenInput by remember { mutableStateOf(settings.apiToken) }
    var check by remember { mutableStateOf(if (settings.isConfigured) ConnectionCheck.OK else ConnectionCheck.IDLE) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canProtect = remember { canProtectToken(context) }
    /** Итог последней попытки зашифровать токен — показывается под кнопкой, null — сказать нечего. */
    var protectNote by remember { mutableStateOf<String?>(null) }

    /**
     * Шифрует токен ключом под отпечатком и сохраняет. false — пользователь отменил или ключ не
     * создался; [protectNote] тогда объясняет, что произошло.
     */
    suspend fun saveSealed(url: String, token: String): Boolean {
        val activity = context.findFragmentActivity() ?: return false
        val cipher = try {
            settingsStore.vault.encryptCipher()
        } catch (e: Exception) {
            protectNote = "Не удалось создать ключ: ${e.message ?: e.javaClass.simpleName}"
            return false
        }
        return when (val r = authenticate(activity, cipher, "Защитить токен", "Токен зашифруется ключом на этом устройстве")) {
            is AuthResult.Success -> {
                settingsStore.setServerSealed(url, token, r.cipher)
                protectNote = null
                true
            }
            AuthResult.Cancelled -> {
                protectNote = "Без подтверждения токен не сохранён."
                false
            }
            is AuthResult.Failed -> {
                protectNote = r.message
                false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen),
    ) {
        Spacer(Modifier.height(Space.block))
        Text("Настройки", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Spacer(Modifier.height(Space.section))

        SectionRule("Сервер")
        val label = when {
            !settings.isConfigured -> "Не подключён"
            check == ConnectionCheck.FAILED -> "Нет связи с сервером"
            else -> "Подключено"
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (settings.isConfigured && check != ConnectionCheck.FAILED) Lime else TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (customServer) "Адрес и токен своего парсера — API_TOKEN из его .env."
            else "Ключ выдаёт бот @Parserkworktrbot по команде /start. Там же — профиль, по которому ИИ отбирает заказы.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        if (!customServer) {
            TextAction("Получить ключ в Telegram", onClick = { openBot(context) })
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            if (customServer) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it; check = ConnectionCheck.IDLE },
                    label = { Text("Адрес сервера") },
                    placeholder = { Text("203.0.113.10:8766") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it; check = ConnectionCheck.IDLE },
                label = { Text(if (customServer) "Токен (API_TOKEN)" else "Ключ из бота") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = when (check) {
                    ConnectionCheck.CHECKING -> "Проверяю…"
                    ConnectionCheck.OK -> "Подключено"
                    ConnectionCheck.FAILED -> "Не удалось — повторить"
                    ConnectionCheck.IDLE -> "Проверить и сохранить"
                },
                enabled = check != ConnectionCheck.CHECKING && urlInput.isNotBlank() && tokenInput.isNotBlank(),
                onClick = {
                    check = ConnectionCheck.CHECKING
                    scope.launch {
                        val ok = repository.checkConnection(urlInput, tokenInput)
                        if (!ok) {
                            check = ConnectionCheck.FAILED
                            return@launch
                        }
                        val saved = if (canProtect) {
                            saveSealed(urlInput, tokenInput)
                        } else {
                            settingsStore.setServerPlain(urlInput, tokenInput)
                            true
                        }
                        check = if (saved) ConnectionCheck.OK else ConnectionCheck.IDLE
                        if (saved) onSettingsSaved()
                    }
                },
            )
            if (check == ConnectionCheck.FAILED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (customServer) {
                        "Сервер не отвечает или токен неверный. Проверьте, что api.enabled=true " +
                            "и API_TOKEN совпадает с .env на сервере."
                    } else {
                        "Сервер не отвечает или ключ неверный. Новый ключ — командой /newkey в боте."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextAction(
                if (customServer) "Общий сервер" else "Свой сервер",
                color = TextMuted,
                onClick = {
                    customServer = !customServer
                    urlInput = if (customServer) "" else SettingsStore.DEFAULT_SERVER_URL
                    check = ConnectionCheck.IDLE
                },
            )
            protectNote?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        Spacer(Modifier.height(Space.section))
        SectionRule("Защита")
        val keyLocation = remember(settings.tokenSealed) { settingsStore.vault.keyLocation() }
        SettingRow(Icons.Outlined.Fingerprint, "Вход по отпечатку") {
            when {
                settings.tokenSealed -> Text("ключ в ${keyLocation?.label ?: "Keystore"}", style = LabelStyle, color = Lime)
                !canProtect -> Text("нет биометрии", style = LabelStyle, color = TextMuted)
                settings.isConfigured -> TextAction("Включить", onClick = {
                    scope.launch { saveSealed(settings.baseUrl, settings.apiToken) }
                })
                else -> Text("после подключения", style = LabelStyle, color = TextMuted)
            }
        }
        if (settings.tokenSealed) {
            NavRow(Icons.Outlined.Lock, "Заблокировать сейчас", "", onClick = settingsStore::lock)
        }
        Text(
            when {
                settings.tokenSealed -> (
                    "Токен зашифрован AES-256 ключом из Android Keystore. Ключ не извлекается из устройства, " +
                        "работает только после отпечатка и уничтожается, если в системе добавят новый. " +
                        "Через минуту в фоне приложение блокируется снова."
                    )
                !canProtect ->
                    "На устройстве нет отпечатка или блокировки экрана — токен хранится без шифрования."
                else -> "Токен хранится открытым текстом. Включите, чтобы закрыть его отпечатком."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = Space.gap),
        )

        Spacer(Modifier.height(Space.section))
        SectionRule("Поиск")
        NavRow(Icons.Outlined.FilterAlt, "Фильтры", "профиль и порог", onClick = onOpenFilters)
        ToggleRow(
            Icons.Outlined.AutoAwesome, "AI-ответы", "Черновик отклика к каждому заказу",
            settings.aiRepliesEnabled, settingsStore::setAiRepliesEnabled,
        )

        Spacer(Modifier.height(Space.section))
        SectionRule("Уведомления")
        ToggleRow(
            Icons.Outlined.NotificationsNone, "Push", "Когда заказ прошёл фильтр",
            settings.notificationsEnabled, settingsStore::setNotificationsEnabled,
        )
        SettingRow(Icons.Outlined.Link, "Telegram-бот") {
            Text("работает параллельно", style = LabelStyle, color = TextMuted)
        }

        Spacer(Modifier.height(48.dp))
        Text("Kwork Tracker, версия 1.0", style = LabelStyle, color = TextMuted)
        Spacer(Modifier.height(Space.section))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lime,
    unfocusedBorderColor = OutlineSoft,
    focusedLabelColor = Lime,
    cursorColor = Lime,
)

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        trailing()
    }
    HorizontalDivider(color = OutlineSoft)
}

/** Title on the left, mono value + chevron on the right. */
@Composable
private fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    SettingRow(icon, title, modifier = Modifier.clickable(onClick = onClick)) {
        Text(value, style = LabelStyle, color = TextMuted)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Spacer(Modifier.width(12.dp))
        KworkSwitch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = OutlineSoft)
}

/** Бот, который выдаёт ключ. Telegram может быть не установлен — тогда откроется браузер или ничего. */
private fun openBot(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, SettingsStore.KEY_BOT_URL.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Откройте в Telegram: @Parserkworktrbot", Toast.LENGTH_LONG).show()
    }
}
