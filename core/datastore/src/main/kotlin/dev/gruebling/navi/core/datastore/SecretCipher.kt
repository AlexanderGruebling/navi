package dev.gruebling.navi.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/** Encrypts small secrets. Abstracted so tests can run without the Android Keystore. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(encrypted: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a non-exportable key in the Android Keystore. Output = 12-byte IV + ciphertext.
 * If the key is lost (app data cleared, device restored), decrypt throws and the user logs in again.
 */
class KeystoreSecretCipher @Inject constructor() : SecretCipher {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted, 0, IV_SIZE))
        return cipher.doFinal(encrypted, IV_SIZE, encrypted.size - IV_SIZE)
    }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "navi_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
