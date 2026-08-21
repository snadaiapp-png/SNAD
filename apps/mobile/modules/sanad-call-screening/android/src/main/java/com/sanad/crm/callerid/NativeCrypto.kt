package com.sanad.crm.callerid

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed AES-256-GCM for the native projection (G8 EXECUTION 05
 * §22–§23): two aliases —
 *  - `g8_native_pii_key_v1`: encrypts display/account names at rest;
 *  - `g8_native_dataset_wrap_key_v1`: wraps the Track D dataset HMAC key.
 * NEVER plain SharedPreferences / plain SQLite / BuildConfig / logs.
 */
object NativeCrypto {

    const val PII_ALIAS = "g8_native_pii_key_v1"
    const val DATASET_WRAP_ALIAS = "g8_native_dataset_wrap_key_v1"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private fun keyStore(): KeyStore {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        return ks
    }

    private fun ensureKey(alias: String) {
        if (keyStore().containsAlias(alias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    }

    private fun key(alias: String): SecretKey {
        ensureKey(alias)
        return keyStore().getKey(alias, null) as SecretKey
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(PII_ALIAS))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(payload: String): String {
        val parts = payload.split(":")
        require(parts.size == 2) { "malformed encrypted payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(PII_ALIAS), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun wrapDatasetKey(datasetKey: String): String = encrypt(datasetKey)

    fun unwrapDatasetKey(wrapped: String): String = decrypt(wrapped)

    fun deleteAliases() {
        val ks = keyStore()
        for (alias in listOf(PII_ALIAS, DATASET_WRAP_ALIAS)) {
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        }
    }

    fun piiAliasExists(): Boolean = keyStore().containsAlias(PII_ALIAS)
}
