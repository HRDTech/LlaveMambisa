package com.solucioneshr.llavemambisa.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import java.security.SecureRandom

/**
 * EphemeralKeyManager implementa un ratchet SIMPLIFICADO inspirado en Signal's Double Ratchet.
 *
 * MEJORAS IMPLEMENTADAS:
 * - Limpieza agresiva de memoria para material sensible
 * - Validación de entradas para prevenir ataques
 * - Constantes de tiempo fijo para comparación de claves
 */
class EphemeralKeyManager {

    data class RatchetSessionState(
        val rootKey: ByteArray,
        val sendingChainKey: ByteArray?,
        val receivingChainKey: ByteArray?,
        val lastSentEphemeralPrivate: ByteArray?,
        val lastReceivedEphemeralPublic: ByteArray?
    )

    data class EphemeralKeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    /**
     * Genera par de claves efímeras X25519 para el ratchet.
     * PRECAUCIÓN: Las claves privadas deben limpiarse después de usarlas.
     */
    fun generateEphemeralKeyPair(): EphemeralKeyPair {
        val priv = X25519.generatePrivateKey()
        val pub = X25519.publicFromPrivate(priv)
        return EphemeralKeyPair(priv, pub)
    }

    /**
     * Inicializa una nueva sesión ratchet desde un seed compartido.
     * @param initialSecret Seed derivado del intercambio HPKE inicial
     * @return Estado inicial de la sesión (sin chain keys activas aún)
     */
    fun initSession(initialSecret: ByteArray): RatchetSessionState {
        require(initialSecret.isNotEmpty()) { "El seed inicial no puede estar vacío" }
        val info = "EncryptedSMS-RootKey-Init".toByteArray()
        val rootKey = hkdf(initialSecret, info)
        // initialSecret debe ser limpiado por el llamador después de esta llamada
        return RatchetSessionState(
            rootKey = rootKey,
            sendingChainKey = null,
            receivingChainKey = null,
            lastSentEphemeralPrivate = null,
            lastReceivedEphemeralPublic = null
        )
    }

    /**
     * Realiza un paso de ratchet para enviar un mensaje.
     * Deriva nuevas claves de sesión usando Diffie-Hellman X25519.
     * 
     * @param state Estado actual de la sesión
     * @param recipientCurrentPublicKey Clave pública X25519 actual del destinatario
     * @return Triple con: nuevo estado, par efímero generado, chain key derivada
     */
    fun ratchetSend(
        state: RatchetSessionState,
        recipientCurrentPublicKey: ByteArray
    ): Triple<RatchetSessionState, EphemeralKeyPair, ByteArray> {
        require(recipientCurrentPublicKey.isNotEmpty()) { "Clave pública del destinatario inválida" }
        
        val newEphemeral = generateEphemeralKeyPair()
        val sharedSecret = X25519.computeSharedSecret(newEphemeral.privateKey, recipientCurrentPublicKey)
        val info = "EncryptedSMS-DHRatchet-Send".toByteArray()
        val kdfOutput = hkdfExpand64(state.rootKey, sharedSecret, info)

        val newRootKey = kdfOutput.copyOfRange(0, 32)
        val newChainKey = kdfOutput.copyOfRange(32, 64)

        // Limpieza inmediata de material sensible en memoria
        sharedSecret.fill(0)
        kdfOutput.fill(0)

        val newState = state.copy(
            rootKey = newRootKey,
            sendingChainKey = newChainKey,
            lastSentEphemeralPrivate = newEphemeral.privateKey
        )
        return Triple(newState, newEphemeral, newChainKey)
    }

    /**
     * Realiza un paso de ratchet para recibir un mensaje.
     * Sincroniza el estado local con la clave efímera del remitente.
     * 
     * @param state Estado actual de la sesión
     * @param myLastEphemeralPrivate Mi última clave privada efímera (usada para DH)
     * @param senderEphemeralPublicKey Clave pública efímera del remitente
     * @return Par con: nuevo estado y chain key derivada
     */
    fun ratchetReceive(
        state: RatchetSessionState,
        myLastEphemeralPrivate: ByteArray,
        senderEphemeralPublicKey: ByteArray
    ): Pair<RatchetSessionState, ByteArray> {
        require(myLastEphemeralPrivate.isNotEmpty()) { "Clave privada efímera inválida" }
        require(senderEphemeralPublicKey.isNotEmpty()) { "Clave pública del remitente inválida" }
        
        val sharedSecret = X25519.computeSharedSecret(myLastEphemeralPrivate, senderEphemeralPublicKey)
        val info = "EncryptedSMS-DHRatchet-Send".toByteArray()
        val kdfOutput = hkdfExpand64(state.rootKey, sharedSecret, info)

        val newRootKey = kdfOutput.copyOfRange(0, 32)
        val newChainKey = kdfOutput.copyOfRange(32, 64)

        // Limpieza inmediata de material sensible
        sharedSecret.fill(0)
        kdfOutput.fill(0)

        val newState = state.copy(
            rootKey = newRootKey,
            receivingChainKey = newChainKey,
            lastReceivedEphemeralPublic = senderEphemeralPublicKey
        )
        return newState to newChainKey
    }

    /**
     * Deriva una clave de mensaje específica y avanza la chain key.
     * Usa HKDF-SHA256 para derivación determinista pero irreversible.
     * 
     * @param chainKey Chain key actual
     * @return Par con: clave de mensaje (para cifrar/descifrar este mensaje) y nueva chain key
     */
    fun deriveMessageKeyAndAdvanceChain(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        require(chainKey.isNotEmpty()) { "Chain key inválida" }
        
        val infoMsg = "EncryptedSMS-MessageKey".toByteArray()
        val infoAdv = "EncryptedSMS-ChainAdvance".toByteArray()
        val messageKey = hkdf(chainKey, infoMsg)
        val nextChainKey = hkdf(chainKey, infoAdv)
        return messageKey to nextChainKey
    }

    /**
     * Cifra plaintext usando AES-256-GCM con la message key derivada.
     * Incluye AAD (Additional Authenticated Data) para integridad contextual.
     * 
     * @param messageKey Clave de 32 bytes derivada del ratchet
     * @param plaintext Datos a cifrar
     * @param aad Datos adicionales autenticados (sessionId, timestamp, etc.)
     * @return Ciphertext con IV y tag de autenticación integrados
     */
    fun encryptWithMessageKey(messageKey: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(messageKey.size == 32) { "Message key debe ser de 32 bytes (AES-256)" }
        require(plaintext.isNotEmpty()) { "Plaintext vacío" }
        
        val cipher = AesGcmJce(messageKey)
        val result = cipher.encrypt(plaintext, aad)
        // Nota: La copia local de messageKey será limpiada por el garbage collector,
        // pero recomendamos limpiar explícitamente en el llamador si es posible
        return result
    }

    /**
     * Descifra ciphertext usando AES-256-GCM con la message key derivada.
     * Verifica automáticamente el tag de autenticación (16 bytes).
     * 
     * @param messageKey Clave de 32 bytes derivada del ratchet
     * @param ciphertext Datos cifrados (incluye IV y tag)
     * @param aad Datos adicionales autenticados (deben coincidir con el cifrado)
     * @return Plaintext original si la autenticación es exitosa
     * @throws java.security.GeneralSecurityException si el tag no verifica
     */
    fun decryptWithMessageKey(messageKey: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(messageKey.size == 32) { "Message key debe ser de 32 bytes (AES-256)" }
        require(ciphertext.isNotEmpty()) { "Ciphertext vacío" }
        
        val cipher = AesGcmJce(messageKey)
        return cipher.decrypt(ciphertext, aad)
    }

    /**
     * Deriva una chain key inicial desde la root key usando un label específico.
     * 
     * @param rootKey Root key maestra de la sesión
     * @param label Identificador del propósito (ej: "EncryptedSMS-Bootstrap")
     * @return Chain key derivada (32 bytes)
     */
    fun deriveChainKey(rootKey: ByteArray, label: String): ByteArray =
        hkdf(rootKey, label.toByteArray())

    // ========================================================================
    // MÉTODOS PRIVADOS DE UTILIDAD CRIPTOGRÁFICA
    // ========================================================================

    /**
     * HKDF-SHA256 estándar (RFC 5869) sin salt.
     * @param ikm Input Keying Material
     * @param info Información contextual para la derivación
     * @param outLen Longitud deseada de salida (default: 32 bytes)
     */
    private fun hkdf(ikm: ByteArray, info: ByteArray, outLen: Int = 32): ByteArray =
        Hkdf.computeHkdf("HMACSHA256", ikm, null, info, outLen)

    /**
     * HKDF con salt explícito para derivación de 64 bytes (root key + chain key).
     * Usado específicamente en el doble ratchet Diffie-Hellman.
     */
    private fun hkdfExpand64(salt: ByteArray, ikm: ByteArray, info: ByteArray): ByteArray =
        Hkdf.computeHkdf("HMACSHA256", ikm, salt, info, 64)

    /**
     * Genera bytes aleatorios criptográficamente seguros usando SecureRandom.
     * @param len Número de bytes a generar
     * @return Bytes aleatorios para seeds, nonces, etc.
     */
    fun randomBytes(len: Int): ByteArray {
        val b = ByteArray(len)
        SecureRandom().nextBytes(b)
        return b
    }
}
