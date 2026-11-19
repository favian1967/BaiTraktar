package com.example.baitraktar.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** Где физически живёт ключ — то, что система сама о нём сообщает через [KeyInfo]. */
enum class KeyLocation(val label: String) {
    STRONGBOX("StrongBox"),
    TEE("TEE"),
    SOFTWARE("программно"),
}

/** Ключ стал недействителен: на устройстве поменяли отпечатки или убрали блокировку экрана. */
class VaultInvalidatedException : Exception()

/**
 * API_TOKEN, зашифрованный ключом из Android Keystore.
 *
 * Ключ AES-256-GCM создаётся внутри защищённого железа (StrongBox, если он есть, иначе TEE)
 * и наружу не выходит. Каждое шифрование и расшифровка требует биометрии — [Cipher] отсюда
 * отдаётся в BiometricPrompt и работает только после того, как пользователь подтвердил себя.
 * На диске лежит только шифротекст и IV; открытый токен живёт лишь в памяти процесса.
 *
 * Новый отпечаток в системе уничтожает ключ ([KeyGenParameterSpec.Builder.setInvalidatedByBiometricEnrollment]),
 * так что добавить свой палец на чужой телефон и открыть приложение не выйдет.
 */
class TokenVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("token_vault", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    val hasSealedToken: Boolean get() = prefs.contains(KEY_CIPHERTEXT) && keyStore.containsAlias(ALIAS)

    /** Шифр для [seal]. Создаёт ключ, если его ещё нет. */
    fun encryptCipher(): Cipher {
        val key = (keyStore.getKey(ALIAS, null) as? SecretKey) ?: generateKey()
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Старый ключ умер вместе с прошлыми отпечатками — для нового токена просто делаем новый.
            clear()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, generateKey()) }
        }
    }

    /** Шифр для [open]. Бросает [VaultInvalidatedException], если ключ уничтожен системой. */
    fun decryptCipher(): Cipher {
        val key = keyStore.getKey(ALIAS, null) as? SecretKey ?: throw VaultInvalidatedException()
        val iv = Base64.decode(prefs.getString(KEY_IV, null) ?: throw VaultInvalidatedException(), Base64.NO_WRAP)
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw VaultInvalidatedException()
        }
    }

    /** [cipher] — тот, что вернул BiometricPrompt после успешной аутентификации. */
    fun seal(cipher: Cipher, token: String) {
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun open(cipher: Cipher): String {
        val ciphertext = Base64.decode(prefs.getString(KEY_CIPHERTEXT, null) ?: throw VaultInvalidatedException(), Base64.NO_WRAP)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** Стирает и шифротекст, и сам ключ — восстановить токен после этого нельзя. */
    fun clear() {
        prefs.edit().clear().apply()
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    /** null — ключа ещё нет. */
    fun keyLocation(): KeyLocation? {
        val key = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
        val info = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyLocation.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeyLocation.TEE
                else -> KeyLocation.SOFTWARE
            }
        } else {
            // До Android 12 система не различает StrongBox и TEE — помним, что просили при создании.
            @Suppress("DEPRECATION") val inHardware = info.isInsideSecureHardware
            when {
                !inHardware -> KeyLocation.SOFTWARE
                prefs.getBoolean(KEY_STRONGBOX, false) -> KeyLocation.STRONGBOX
                else -> KeyLocation.TEE
            }
        }
    }

    private fun generateKey(): SecretKey {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKey(strongBox = true).also { prefs.edit().putBoolean(KEY_STRONGBOX, true).apply() }
            } catch (_: StrongBoxUnavailableException) {
                // Отдельного чипа нет — у большинства телефонов так; TEE тоже аппаратный.
            }
        }
        prefs.edit().putBoolean(KEY_STRONGBOX, false).apply()
        return generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // 0 — подтверждение на каждую операцию, а не «окно» в N секунд после разблокировки.
                    // Отпечаток/лицо класса Strong или PIN устройства; до Android 11 — только биометрия.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                }
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "api_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_IV = "iv"
        private const val KEY_STRONGBOX = "strongbox"
    }
}
