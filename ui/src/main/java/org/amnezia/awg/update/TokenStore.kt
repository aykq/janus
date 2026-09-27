/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.amnezia.awg.Application
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object TokenStore {
    private const val KEY_ALIAS = "janus_update_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private val ENCRYPTED = stringPreferencesKey("update_token_enc")

    suspend fun get(): String? {
        val stored = Application.getPreferencesDataStore().data.first()[ENCRYPTED] ?: return null
        return try {
            decrypt(stored)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun set(raw: String?) {
        val token = raw?.let(UpdatePolicy::normalizeToken)
        Application.getPreferencesDataStore().edit {
            if (token == null) it.remove(ENCRYPTED) else it[ENCRYPTED] = encrypt(token)
        }
    }

    suspend fun isSet(): Boolean = Application.getPreferencesDataStore().data.first()[ENCRYPTED] != null

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_LENGTH))
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH))
    }
}
