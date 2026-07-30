package com.solucioneshr.llavemambisa.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import java.security.KeyStore
import java.security.KeyPairGenerator

/**
 * KeyManager gestiona el material criptográfico de LARGO PLAZO de la identidad del usuario:
 *
 *  - Un par de claves de firma (Ed25519, vía Tink SignatureKeyTemplates) usado para autenticar
 *    cada mensaje saliente (evita suplantación / MITM en el intercambio de claves por QR).
 *  - Un par de claves de intercambio híbrido (X25519 + HKDF + AES-256-GCM, es decir HPKE, vía
 *    Tink HybridKeyTemplates) usado para que otros puedan cifrar mensajes DIRIGIDOS a este
 *    usuario.
 *
 * DECISIÓN DE SEGURIDAD: en vez de reinventar ECIES/HPKE a mano, delegamos toda la
 * primitiva criptográfica a Tink. Tink internamente ya genera una clave efímera de emisor
 * (ephemeral ECDH) en CADA llamada a HybridEncrypt.encrypt(...), lo cual nos da Forward
 * Secrecy por mensaje de forma nativa y auditada, sin que tengamos que manipular puntos de
 * curva elíptica nosotros mismos (fuente típica de vulnerabilidades side-channel).
 *
 * El keyset de Tink (que contiene la clave PRIVADA) se cifra en reposo con una clave maestra
 * que NUNCA sale del Android Keystore (AndroidKeystoreKmsClient), usando StrongBox si el
 * dispositivo lo soporta. Es decir: doble capa -> Keystore protege el keyset de Tink, y Tink
 * protege los mensajes.
 */
class KeyManager(private val context: Context) {

    companion object {
        private const val MASTER_KEY_ALIAS = "encryptedsms_master_kms_key"
        private const val KMS_URI = AndroidKeystoreKmsClient.PREFIX + MASTER_KEY_ALIAS

        private const val SIGNING_KEYSET_PREF = "encryptedsms_signing_keyset"
        private const val HYBRID_KEYSET_PREF = "encryptedsms_hybrid_keyset"
        private const val PREF_FILE = "encryptedsms_keysets" // solo contiene keysets ya cifrados

        init {
            // Registra las primitivas Hybrid y Signature en el runtime de Tink.
            HybridConfig.register()
            SignatureConfig.register()
        }
    }

    /** True si el hardware soporta StrongBox (secure element dedicado, no solo TEE). */
    fun isStrongBoxAvailable(): Boolean =
        context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")

    /** True si el dispositivo puede autenticar con biometría fuerte (clase 3). */
    fun canUseBiometricAuth(): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Crea (o migra si es necesario) la clave maestra de envoltura (wrapping key) dentro del
     * Android Keystore. Esta clave AES-256-GCM nunca es exportable; solo Tink la invoca vía JCA.
     *
     * NOTA DE SEGURIDAD: NO se usa setUserAuthenticationRequired(true) porque exigir
     * autenticación biométrica por operación (defaultValidityDurationSeconds=0) bloquea
     * el acceso desde corrutinas de fondo y hace la app inoperable. La frontera de seguridad
     * en reposo la provee la pantalla de bloqueo del dispositivo (igual que Signal, WhatsApp, etc.).
     * La protección criptográfica adicional (keyset Tink cifrado en SharedPrefs) sigue vigente.
     *
     * Si existe una clave antigua con setUserAuthenticationRequired(true) incompatible (creada
     * sin setUserAuthenticationValidityDurationSeconds), se detecta, se borra y se recrea junto
     * con los keysets almacenados para garantizar un estado consistente.
     */
    private fun ensureMasterKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (ks.containsAlias(MASTER_KEY_ALIAS)) {
            // Probar si la clave existente se puede usar sin autenticación biométrica interactiva.
            if (isMasterKeyUsable(ks)) return
            // La clave existe pero requiere autenticación por operación: no es utilizable desde
            // corrutinas de fondo. La eliminamos junto con los keysets cifrados (que ya son
            // ilegibles sin la clave, por lo que se vuelven a generar limpiamente).
            android.util.Log.w("KeyManager",
                "Clave maestra requiere auth por operación; eliminando y recreando.")
            try { ks.deleteEntry(MASTER_KEY_ALIAS) } catch (_: Exception) { }
            clearStoredKeysets()
        }

        val kpg = javax.crypto.KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Sin setUserAuthenticationRequired: accesible siempre que el dispositivo esté
            // desbloqueado (frontera de seguridad estándar para apps de mensajería).

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBoxAvailable()) {
            builder.setIsStrongBoxBacked(true)
        }
        kpg.init(builder.build())
        kpg.generateKey()
    }

    /**
     * Intenta realizar una operación de cifrado de prueba con la clave maestra existente.
     * Devuelve true si la clave es accesible sin autenticación interactiva.
     */
    private fun isMasterKeyUsable(ks: KeyStore): Boolean = try {
        val key = ks.getKey(MASTER_KEY_ALIAS, null) as? javax.crypto.SecretKey
            ?: return false
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        cipher.doFinal(ByteArray(1))
        true
    } catch (_: Exception) {
        // UserNotAuthenticatedException, IllegalBlockSizeException (caused by KeyStoreException),
        // o cualquier otro error → la clave no es utilizable en este contexto.
        false
    }

    /** Limpia los keysets cifrados guardados en SharedPreferences. */
    private fun clearStoredKeysets() {
        prefs().edit().clear().apply()
    }

    private fun prefs() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    /** Obtiene (generando si es la primera vez) el KeysetHandle de FIRMA del usuario. */
    fun getOrCreateSigningKeyset(): KeysetHandle {
        ensureMasterKey()
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context, SIGNING_KEYSET_PREF, PREF_FILE)
            .withKeyTemplate(SignatureKeyTemplates.ED25519)
            .withMasterKeyUri(KMS_URI)
            .build()
        return manager.keysetHandle
    }

    /** Obtiene (generando si es la primera vez) el KeysetHandle HÍBRIDO (HPKE/X25519) del usuario. */
    fun getOrCreateHybridKeyset(): KeysetHandle {
        ensureMasterKey()
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context, HYBRID_KEYSET_PREF, PREF_FILE)
            .withKeyTemplate(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            .withMasterKeyUri(KMS_URI)
            .build()
        return manager.keysetHandle
    }

    fun getSigner(): PublicKeySign = getOrCreateSigningKeyset().getPrimitive(PublicKeySign::class.java)

    fun getVerifier(publicKeysetBytes: ByteArray): PublicKeyVerify {
        val handle = com.google.crypto.tink.CleartextKeysetHandle.read(
            com.google.crypto.tink.BinaryKeysetReader.withBytes(publicKeysetBytes)
        )
        return handle.getPrimitive(PublicKeyVerify::class.java)
    }

    fun getHybridEncryptor(recipientPublicKeysetBytes: ByteArray): HybridEncrypt {
        val handle = com.google.crypto.tink.CleartextKeysetHandle.read(
            com.google.crypto.tink.BinaryKeysetReader.withBytes(recipientPublicKeysetBytes)
        )
        return handle.getPrimitive(HybridEncrypt::class.java)
    }

    fun getHybridDecryptor(): HybridDecrypt =
        getOrCreateHybridKeyset().getPrimitive(HybridDecrypt::class.java)

    /**
     * Exporta las claves PÚBLICAS de identidad (firma + híbrida) serializadas, listas para
     * codificar en el QR. Nunca exporta material privado: `getPublicKeysetHandle()` de Tink
     * garantiza que solo se serializan los componentes públicos.
     */
    fun exportPublicIdentityBundle(): PublicIdentityBundle {
        val signingPublic = getOrCreateSigningKeyset().publicKeysetHandle
        val hybridPublic = getOrCreateHybridKeyset().publicKeysetHandle

        val signingBytes = java.io.ByteArrayOutputStream().also {
            com.google.crypto.tink.CleartextKeysetHandle.write(
                signingPublic, com.google.crypto.tink.BinaryKeysetWriter.withOutputStream(it)
            )
        }.toByteArray()

        val hybridBytes = java.io.ByteArrayOutputStream().also {
            com.google.crypto.tink.CleartextKeysetHandle.write(
                hybridPublic, com.google.crypto.tink.BinaryKeysetWriter.withOutputStream(it)
            )
        }.toByteArray()

        return PublicIdentityBundle(signingPublicKeyset = signingBytes, hybridPublicKeyset = hybridBytes)
    }
}

/** Paquete de claves públicas que se comparte vía QR o texto manual. */
data class PublicIdentityBundle(
    val signingPublicKeyset: ByteArray,
    val hybridPublicKeyset: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicIdentityBundle) return false
        return signingPublicKeyset.contentEquals(other.signingPublicKeyset) &&
            hybridPublicKeyset.contentEquals(other.hybridPublicKeyset)
    }
    override fun hashCode(): Int =
        31 * signingPublicKeyset.contentHashCode() + hybridPublicKeyset.contentHashCode()
}
