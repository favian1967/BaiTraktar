package com.example.baitraktar.data

import android.content.Context
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Адрес и токен парсера (см. freelance_parser README → "API для мобильного
 * приложения"), плюс пара локальных переключателей UI. */
data class ServerSettings(
    val baseUrl: String = SettingsStore.DEFAULT_SERVER_URL,
    val apiToken: String = "",
    val aiRepliesEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    /** Онбординг показывается один раз — иначе каждый запуск начинается с приветствия. */
    val onboardingDone: Boolean = false,
    /** Токен лежит в [TokenVault] под биометрией, а не открытым текстом. */
    val tokenSealed: Boolean = false,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiToken.isNotBlank()

    /** Токен есть, но ещё не расшифрован в этом запуске — нужен отпечаток. */
    val isLocked: Boolean get() = tokenSealed && apiToken.isBlank()
}

/**
 * Хранится в обычных SharedPreferences: значений — четыре штуки, между
 * процессами/устройствами синхронизировать не нужно, а DataStore ради этого
 * был бы лишней зависимостью.
 *
 * Токен — исключение: если на устройстве есть биометрия, он уходит в [vault]
 * зашифрованным, а здесь живёт только в памяти, пока приложение разблокировано.
 * Открытым текстом в prefs он остаётся лишь на телефонах без сенсора и PIN-а.
 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("server_settings", Context.MODE_PRIVATE)

    val vault = TokenVault(context)

    private val _settings = MutableStateFlow(
        ServerSettings(
            baseUrl = prefs.getString(KEY_URL, null) ?: DEFAULT_SERVER_URL,
            apiToken = prefs.getString(KEY_TOKEN, "") ?: "",
            aiRepliesEnabled = prefs.getBoolean(KEY_AI, true),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
            tokenSealed = vault.hasSealedToken,
        ),
    )
    val settings: StateFlow<ServerSettings> = _settings

    /** [cipher] — шифр из [TokenVault.encryptCipher], уже подтверждённый отпечатком. */
    fun setServerSealed(baseUrl: String, apiToken: String, cipher: Cipher) {
        vault.seal(cipher, apiToken.trim())
        prefs.edit().putString(KEY_URL, baseUrl.trim()).remove(KEY_TOKEN).apply()
        _settings.update { it.copy(baseUrl = baseUrl.trim(), apiToken = apiToken.trim(), tokenSealed = true) }
    }

    /** Для устройств без биометрии: токен в prefs, как раньше. */
    fun setServerPlain(baseUrl: String, apiToken: String) {
        vault.clear()
        prefs.edit().putString(KEY_URL, baseUrl.trim()).putString(KEY_TOKEN, apiToken.trim()).apply()
        _settings.update { it.copy(baseUrl = baseUrl.trim(), apiToken = apiToken.trim(), tokenSealed = false) }
    }

    /** Бросает исключение, если ключ уничтожен или шифротекст не сходится — тогда только [forgetToken]. */
    fun unlock(cipher: Cipher) {
        val token = vault.open(cipher)
        _settings.update { it.copy(apiToken = token) }
    }

    /** Убирает токен из памяти. Ничего не делает, если он не зашифрован — его неоткуда будет вернуть. */
    fun lock() {
        if (_settings.value.tokenSealed) _settings.update { it.copy(apiToken = "") }
    }

    /** Стирает токен совсем (и ключ к нему). Адрес сервера остаётся — токен вводится заново. */
    fun forgetToken() {
        vault.clear()
        prefs.edit().remove(KEY_TOKEN).apply()
        _settings.update { it.copy(apiToken = "", tokenSealed = false) }
    }

    fun setAiRepliesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI, enabled).apply()
        _settings.update { it.copy(aiRepliesEnabled = enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _settings.update { it.copy(notificationsEnabled = enabled) }
    }

    fun setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
        _settings.update { it.copy(onboardingDone = true) }
    }

    companion object {
        /** Общий сервер приложения (kwork_app на VPS): ключ к нему выдаёт бот [KEY_BOT_URL]. */
        const val DEFAULT_SERVER_URL = "https://kwork.favian1967.site"
        const val KEY_BOT_URL = "https://t.me/Parserkworktrbot"

        private const val KEY_URL = "base_url"
        private const val KEY_TOKEN = "api_token"
        private const val KEY_AI = "ai_replies_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_ONBOARDING = "onboarding_done"

        @Volatile private var instance: SettingsStore? = null

        /** Один экземпляр на процесс — держит единственный источник правды о настройках. */
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
