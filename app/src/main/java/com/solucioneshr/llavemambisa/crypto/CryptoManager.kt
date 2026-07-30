package com.solucioneshr.llavemambisa.crypto

import com.solucioneshr.llavemambisa.data.local.SecureFieldCrypto
import com.solucioneshr.llavemambisa.data.local.dao.RatchetSessionDao
import com.solucioneshr.llavemambisa.data.local.entity.RatchetSessionEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * Punto de entrada único para cifrar/descifrar SMS. Orquesta:
 *  1. [KeyManager]      -> identidad de largo plazo.
 *  2. [EphemeralKeyManager] -> ratchet por mensaje (forward secrecy).
 *  3. [SmsWireFormat]   -> serialización optimizada.
 *
 * Mejorado con persistencia de sesiones para evitar pérdida de sincronía.
 */
class CryptoManager(
    private val keyManager: KeyManager,
    private val sessionDao: RatchetSessionDao,
    private val secureFieldCrypto: SecureFieldCrypto,
    private val ephemeralKeyManager: EphemeralKeyManager = EphemeralKeyManager()
) {

    sealed class CryptoResult<out T> {
        data class Success<T>(val value: T) : CryptoResult<T>()
        data class Failure(val reason: FailureReason, val message: String) : CryptoResult<Nothing>()
    }

    enum class FailureReason {
        INVALID_PUBLIC_KEY,
        SIGNATURE_MISMATCH,
        DECRYPTION_FAILED,
        MALFORMED_ENVELOPE,
        NO_SESSION,
        DOWNGRADE_SUSPECTED
    }

    // Caché en memoria para rendimiento
    private val sessionCache = ConcurrentHashMap<String, EphemeralKeyManager.RatchetSessionState>()

    private suspend fun getOrLoadSession(sessionIdHex: String): EphemeralKeyManager.RatchetSessionState? {
        sessionCache[sessionIdHex]?.let { return it }
        
        val entity = sessionDao.getSession(sessionIdHex) ?: return null
        val state = EphemeralKeyManager.RatchetSessionState(
            rootKey = secureFieldCrypto.decryptField(entity.rootKeyEncrypted, sessionIdHex.toByteArray()),
            sendingChainKey = entity.sendingChainKeyEncrypted?.let { secureFieldCrypto.decryptField(it, sessionIdHex.toByteArray()) },
            receivingChainKey = entity.receivingChainKeyEncrypted?.let { secureFieldCrypto.decryptField(it, sessionIdHex.toByteArray()) },
            lastSentEphemeralPrivate = entity.lastSentEphemeralPrivateEncrypted?.let { secureFieldCrypto.decryptField(it, sessionIdHex.toByteArray()) },
            lastReceivedEphemeralPublic = entity.lastReceivedEphemeralPublic
        )
        sessionCache[sessionIdHex] = state
        return state
    }

    private suspend fun saveSession(sessionIdHex: String, phone: String, state: EphemeralKeyManager.RatchetSessionState) {
        sessionCache[sessionIdHex] = state
        val entity = RatchetSessionEntity(
            sessionIdHex = sessionIdHex,
            contactPhoneNumber = phone,
            rootKeyEncrypted = secureFieldCrypto.encryptField(state.rootKey, sessionIdHex.toByteArray()),
            sendingChainKeyEncrypted = state.sendingChainKey?.let { secureFieldCrypto.encryptField(it, sessionIdHex.toByteArray()) },
            receivingChainKeyEncrypted = state.receivingChainKey?.let { secureFieldCrypto.encryptField(it, sessionIdHex.toByteArray()) },
            lastSentEphemeralPrivateEncrypted = state.lastSentEphemeralPrivate?.let { secureFieldCrypto.encryptField(it, sessionIdHex.toByteArray()) },
            lastReceivedEphemeralPublic = state.lastReceivedEphemeralPublic
        )
        sessionDao.upsert(entity)
    }

    fun clearSession(sessionId: ByteArray) {
        val hex = sessionId.toHex()
        sessionCache.remove(hex)
        runBlocking { sessionDao.deleteSession(hex) }
    }

    fun encryptOutgoingSms(
        plaintext: String,
        recipientIdentity: PublicIdentityBundle,
        sessionId: ByteArray,
        sequence: Int,
        phoneNumber: String
    ): CryptoResult<String> = runBlocking {
        if (!validatePublicKeyBundle(recipientIdentity)) {
            return@runBlocking CryptoResult.Failure(
                FailureReason.INVALID_PUBLIC_KEY,
                "Paquete de claves públicas inválido."
            )
        }

        try {
            val sessionKey = sessionId.toHex()
            var state = getOrLoadSession(sessionKey)
            
            if (state == null) {
                val seed = ephemeralKeyManager.randomBytes(32)
                val encryptor = keyManager.getHybridEncryptor(recipientIdentity.hybridPublicKeyset)
                val wrappedSeed = encryptor.encrypt(seed, sessionId)
                state = ephemeralKeyManager.initSession(seed)
                seed.fill(0)
                
                encryptWithBootstrap(state, wrappedSeed, plaintext, recipientIdentity, sessionId, sequence, phoneNumber)
            } else {
                encryptContinuing(state, plaintext, recipientIdentity, sessionId, sequence, phoneNumber)
            }
        } catch (e: Exception) {
            CryptoResult.Failure(FailureReason.DECRYPTION_FAILED, e.message ?: "Error de cifrado")
        }
    }

    private suspend fun encryptWithBootstrap(
        state: EphemeralKeyManager.RatchetSessionState,
        wrappedSeed: ByteArray,
        plaintext: String,
        recipientIdentity: PublicIdentityBundle,
        sessionId: ByteArray,
        sequence: Int,
        phoneNumber: String
    ): CryptoResult<String> {
        val ephemeral = ephemeralKeyManager.generateEphemeralKeyPair()
        val chainKey = ephemeralKeyManager.deriveChainKey(state.rootKey, "EncryptedSMS-Bootstrap")
        val (msgKey, advancedChain) = ephemeralKeyManager.deriveMessageKeyAndAdvanceChain(chainKey)

        val payload = buildPayloadBytes(plaintext)
        val ciphertext = ephemeralKeyManager.encryptWithMessageKey(msgKey, payload, sessionId)
        msgKey.fill(0)

        val newState = state.copy(
            sendingChainKey = advancedChain,
            lastSentEphemeralPrivate = ephemeral.privateKey
        )
        saveSession(sessionId.toHex(), phoneNumber, newState)

        val signature = keyManager.getSigner().sign(ephemeral.publicKey + ciphertext)
        val envelope = SmsWireFormat.Envelope(
            version = 1,
            ephemeralPublicKey = ephemeral.publicKey,
            ciphertext = ciphertext,
            signature = signature,
            sessionId = sessionId,
            sequence = sequence,
            bootstrap = wrappedSeed
        )
        return CryptoResult.Success(SmsWireFormat.serialize(envelope))
    }

    private suspend fun encryptContinuing(
        state: EphemeralKeyManager.RatchetSessionState,
        plaintext: String,
        recipientIdentity: PublicIdentityBundle,
        sessionId: ByteArray,
        sequence: Int,
        phoneNumber: String
    ): CryptoResult<String> {
        val recipientRatchetPublic = state.lastReceivedEphemeralPublic
            ?: return CryptoResult.Failure(
                FailureReason.NO_SESSION,
                "Esperando clave de ratchet del contacto."
            )

        val (newState, ephemeral, chainKey) = ephemeralKeyManager.ratchetSend(state, recipientRatchetPublic)
        val (msgKey, advancedChain) = ephemeralKeyManager.deriveMessageKeyAndAdvanceChain(chainKey)

        val payload = buildPayloadBytes(plaintext)
        val ciphertext = ephemeralKeyManager.encryptWithMessageKey(msgKey, payload, sessionId)
        msgKey.fill(0)

        val finalState = newState.copy(sendingChainKey = advancedChain)
        saveSession(sessionId.toHex(), phoneNumber, finalState)

        val signature = keyManager.getSigner().sign(ephemeral.publicKey + ciphertext)
        val envelope = SmsWireFormat.Envelope(
            version = 1,
            ephemeralPublicKey = ephemeral.publicKey,
            ciphertext = ciphertext,
            signature = signature,
            sessionId = sessionId,
            sequence = sequence
        )
        return CryptoResult.Success(SmsWireFormat.serialize(envelope))
    }

    fun decryptIncomingSms(
        smsBody: String,
        senderIdentity: PublicIdentityBundle,
        phoneNumber: String
    ): CryptoResult<String> = runBlocking {
        val envelope = SmsWireFormat.deserialize(smsBody)
            ?: return@runBlocking CryptoResult.Failure(FailureReason.MALFORMED_ENVELOPE, "Formato SMS inválido.")

        if (!validatePublicKeyBundle(senderIdentity)) {
            return@runBlocking CryptoResult.Failure(FailureReason.INVALID_PUBLIC_KEY, "Clave pública inválida.")
        }

        val verifier = try {
            keyManager.getVerifier(senderIdentity.signingPublicKeyset)
        } catch (e: Exception) {
            return@runBlocking CryptoResult.Failure(FailureReason.INVALID_PUBLIC_KEY, "Error cargando clave firma.")
        }
        
        try {
            verifier.verify(envelope.signature, envelope.ephemeralPublicKey + envelope.ciphertext)
        } catch (e: java.security.GeneralSecurityException) {
            return@runBlocking CryptoResult.Failure(FailureReason.SIGNATURE_MISMATCH, "Firma inválida.")
        }

        try {
            val sessionKey = envelope.sessionId.toHex()
            val state = getOrLoadSession(sessionKey)
            val wrappedSeed = envelope.bootstrap

            if (wrappedSeed != null) {
                val decryptor = keyManager.getHybridDecryptor()
                val seed = decryptor.decrypt(wrappedSeed, envelope.sessionId)
                val initState = ephemeralKeyManager.initSession(seed)
                seed.fill(0)

                val chainKey = ephemeralKeyManager.deriveChainKey(initState.rootKey, "EncryptedSMS-Bootstrap")
                val (msgKey, advancedChain) = ephemeralKeyManager.deriveMessageKeyAndAdvanceChain(chainKey)
                val plaintextBytes = ephemeralKeyManager.decryptWithMessageKey(msgKey, envelope.ciphertext, envelope.sessionId)
                msgKey.fill(0)

                val newState = initState.copy(
                    receivingChainKey = advancedChain,
                    lastReceivedEphemeralPublic = envelope.ephemeralPublicKey
                )
                saveSession(sessionKey, phoneNumber, newState)

                return@runBlocking CryptoResult.Success(String(plaintextBytes, StandardCharsets.UTF_8))
            }

            if (state == null) {
                return@runBlocking CryptoResult.Failure(FailureReason.NO_SESSION, "Sesión no encontrada.")
            }

            val (newState, chainKey) = ephemeralKeyManager.ratchetReceive(
                state,
                myLastEphemeralPrivate = state.lastSentEphemeralPrivate
                    ?: return@runBlocking CryptoResult.Failure(FailureReason.NO_SESSION, "Falta material local."),
                senderEphemeralPublicKey = envelope.ephemeralPublicKey
            )
            val (msgKey, advancedChain) = ephemeralKeyManager.deriveMessageKeyAndAdvanceChain(chainKey)
            val plaintextBytes = ephemeralKeyManager.decryptWithMessageKey(msgKey, envelope.ciphertext, envelope.sessionId)
            msgKey.fill(0)

            val finalState = newState.copy(receivingChainKey = advancedChain)
            saveSession(sessionKey, phoneNumber, finalState)

            CryptoResult.Success(String(plaintextBytes, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            CryptoResult.Failure(FailureReason.DECRYPTION_FAILED, "Fallo al descifrar.")
        }
    }

    private fun validatePublicKeyBundle(bundle: PublicIdentityBundle): Boolean {
        return bundle.signingPublicKeyset.size >= 16 && bundle.hybridPublicKeyset.size >= 16
    }

    fun deriveSessionId(myIdentity: PublicIdentityBundle, theirIdentity: PublicIdentityBundle): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val a = myIdentity.signingPublicKeyset
        val b = theirIdentity.signingPublicKeyset
        val (first, second) = if (a.toHex() < b.toHex()) a to b else b to a
        digest.update(first)
        digest.update(second)
        return digest.digest().copyOfRange(0, 8)
    }

    private fun buildPayloadBytes(plaintext: String): ByteArray {
        val json = org.json.JSONObject().apply {
            put("t", System.currentTimeMillis())
            put("m", plaintext)
        }
        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
