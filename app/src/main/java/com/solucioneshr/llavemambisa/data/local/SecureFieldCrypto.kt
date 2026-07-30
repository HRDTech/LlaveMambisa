package com.solucioneshr.llavemambisa.data.local

import android.content.Context
import android.os.Build
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient

/**
 * Cifra/descifra campos individuales antes de persistirlos en Room, usando AES-256-GCM (Tink
 * AEAD) cuya clave está envuelta por el Android Keystore. Esto es lo que hace que la base de
 * datos SQLite en disco sea "zero-knowledge": un volcado directo del archivo .db no revela
 * contactos ni mensajes sin acceso al Keystore del dispositivo.
 */
class SecureFieldCrypto(context: Context) {

    companion object {
        private const val MASTER_KEY_ALIAS = "encryptedsms_db_master_kms_key"
        private const val KMS_URI = AndroidKeystoreKmsClient.PREFIX + MASTER_KEY_ALIAS
        private const val KEYSET_PREF = "encryptedsms_db_field_keyset"
        private const val PREF_FILE = "encryptedsms_db_keysets"

        init { AeadConfig.register() }
    }

    private val aead: Aead

    init {
        // Igual que en KeyManager: clave de envoltura en Keystore, StrongBox si disponible.
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(MASTER_KEY_ALIAS)) {
            val kg = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val hasStrongBox = context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox) setIsStrongBoxBacked(true) }
                .build()
            kg.init(spec)
            kg.generateKey()
        }

        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_PREF, PREF_FILE)
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri(KMS_URI)
            .build()
        aead = manager.keysetHandle.getPrimitive(Aead::class.java)
    }

    /** [associatedData] debe ser un identificador estable del registro (p.ej. el id de fila o el
     * número de teléfono) para prevenir ataques de sustitución de campos entre filas. */
    fun encryptField(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    fun decryptField(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)

    fun encryptString(plaintext: String, associatedData: String): ByteArray =
        encryptField(plaintext.toByteArray(Charsets.UTF_8), associatedData.toByteArray(Charsets.UTF_8))

    fun decryptString(ciphertext: ByteArray, associatedData: String): String =
        String(decryptField(ciphertext, associatedData.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
}
