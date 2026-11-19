package com.example.baitraktar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.baitraktar.data.SettingsStore
import com.example.baitraktar.data.VaultInvalidatedException
import com.example.baitraktar.ui.AuthResult
import com.example.baitraktar.ui.authenticate
import com.example.baitraktar.ui.components.Logo
import com.example.baitraktar.ui.components.PrimaryButton
import com.example.baitraktar.ui.components.TextAction
import com.example.baitraktar.ui.findFragmentActivity
import com.example.baitraktar.ui.theme.Space
import com.example.baitraktar.ui.theme.TextMuted
import com.example.baitraktar.ui.theme.TextPrimary
import com.example.baitraktar.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Shown while the server token is sealed and not yet opened in this run. The system prompt
 * comes up by itself; the button is for when it was dismissed. Nothing behind this screen
 * can reach the server until the fingerprint unlocks the key.
 */
@Composable
fun UnlockScreen(settingsStore: SettingsStore, onUnlocked: () -> Unit, onTokenLost: (reason: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val location = remember { settingsStore.vault.keyLocation() }

    fun unlock() {
        val activity = context.findFragmentActivity() ?: return
        scope.launch {
            val cipher = try {
                settingsStore.vault.decryptCipher()
            } catch (_: VaultInvalidatedException) {
                settingsStore.forgetToken()
                onTokenLost("Отпечатки на устройстве изменились — система уничтожила ключ. Введите токен заново.")
                return@launch
            }
            when (val r = authenticate(activity, cipher, "Вход в Kwork Tracker", "Токен сервера откроется после подтверждения")) {
                is AuthResult.Success -> try {
                    settingsStore.unlock(r.cipher)
                    onUnlocked()
                } catch (_: Exception) {
                    // Шифротекст не сошёлся с ключом — восстанавливать нечего.
                    settingsStore.forgetToken()
                    onTokenLost("Токен не расшифровался. Введите его заново.")
                }
                AuthResult.Cancelled -> message = null
                is AuthResult.Failed -> message = r.message
            }
        }
    }

    LaunchedEffect(Unit) { unlock() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.screen),
    ) {
        Spacer(Modifier.height(12.dp))
        Logo(size = 32.dp)
        Spacer(Modifier.weight(1f))
        Text("Заблокировано", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Spacer(Modifier.height(Space.gap))
        Text(
            buildString {
                append("Токен сервера зашифрован ключом")
                if (location != null) append(" в ${location.label}")
                append(". Ключ не покидает устройство и работает только после отпечатка.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        if (message != null) {
            Spacer(Modifier.height(Space.gap))
            Text(message!!, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Spacer(Modifier.height(Space.section))
        PrimaryButton(text = "Разблокировать", onClick = ::unlock)
        Spacer(Modifier.height(Space.gap))
        TextAction(
            "Забыть токен и ввести заново",
            color = TextMuted,
            onClick = {
                settingsStore.forgetToken()
                onTokenLost("Токен стёрт. Введите его заново.")
            },
        )
        Spacer(Modifier.height(Space.block))
    }
}
