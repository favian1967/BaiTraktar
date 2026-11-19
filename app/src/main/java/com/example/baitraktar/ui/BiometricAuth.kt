package com.example.baitraktar.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

sealed interface AuthResult {
    /** [cipher] разблокирован — им можно сделать ровно одну операцию в TokenVault. */
    data class Success(val cipher: Cipher) : AuthResult
    data object Cancelled : AuthResult
    data class Failed(val message: String) : AuthResult
}

/**
 * Должно совпадать с тем, что разрешено ключу в TokenVault. Android 11+ принимает и PIN
 * устройства; раньше крипто-операцию можно подтвердить только биометрией класса Strong.
 */
private val promptAuthenticators =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

/** Есть чем подтвердить — сенсор есть и на нём что-то записано (или PIN на Android 11+). */
fun canProtectToken(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(promptAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS

/** Системный диалог отпечатка поверх [cipher]. Неверный палец — не результат: диалог ждёт следующую попытку. */
suspend fun authenticate(activity: FragmentActivity, cipher: Cipher, title: String, subtitle: String): AuthResult =
    suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (cont.isActive) cont.resume(if (unlocked != null) AuthResult.Success(unlocked) else AuthResult.Failed("Система не вернула ключ"))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val result = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> AuthResult.Cancelled
                        else -> AuthResult.Failed(errString.toString())
                    }
                    if (cont.isActive) cont.resume(result)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(promptAuthenticators)
            .apply {
                // С PIN-ом кнопку «Отмена» рисует сама система; без него она обязательна.
                if (promptAuthenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Отмена")
            }
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }

/** LocalContext в Compose может быть обёрткой — ищем саму Activity. */
fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
