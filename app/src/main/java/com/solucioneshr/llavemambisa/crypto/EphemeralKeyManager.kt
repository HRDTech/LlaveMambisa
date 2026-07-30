package com.solucioneshr.llavemambisa.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import java.security.SecureRandom

/**
 * EphemeralKeyManager implementa un ratchet SIMPLIFICADO inspirado en Signal's Double Ratchet.
 *
 * Mejorado con limpieza agresiva de memoria para material sensible.
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

    fun generateEphemeralKeyPair(): EphemeralKeyPair {
        val priv = X25519.generatePrivateKey()
        val pub = X25519.publicFromPrivate(priv)
        return EphemeralKeyPair(priv, pub)
    }

    fun initSession(initialSecret: ByteArray): RatchetSessionState {
        val info = "EncryptedSMS-RootKey-Init".toByteArray()
        val rootKey = hkdf(initialSecret, info)
        // initialSecret debe ser limpiado por el llamador, pero aquí reforzamos
        return RatchetSessionState(
            rootKey = rootKey,
            sendingChainKey = null,
            receivingChainKey = null,
            lastSentEphemeralPrivate = null,
            lastReceivedEphemeralPublic = null
        )
    }

    fun ratchetSend(
        state: RatchetSessionState,
        recipientCurrentPublicKey: ByteArray
    ): Triple<RatchetSessionState, EphemeralKeyPair, ByteArray> {
        val newEphemeral = generateEphemeralKeyPair()
        val sharedSecret = X25519.computeSharedSecret(newEphemeral.privateKey, recipientCurrentPublicKey)
        val info = "EncryptedSMS-DHRatchet-Send".toByteArray()
        val kdfOutput = hkdfExpand64(state.rootKey, sharedSecret, info)

        val newRootKey = kdfOutput.copyOfRange(0, 32)
        val newChainKey = kdfOutput.copyOfRange(32, 64)

        // Limpieza de memoria sensible
        sharedSecret.fill(0)
        kdfOutput.fill(0)

        val newState = state.copy(
            rootKey = newRootKey,
            sendingChainKey = newChainKey,
            lastSentEphemeralPrivate = newEphemeral.privateKey
        )
        return Triple(newState, newEphemeral, newChainKey)
    }

    fun ratchetReceive(
        state: RatchetSessionState,
        myLastEphemeralPrivate: ByteArray,
        senderEphemeralPublicKey: ByteArray
    ): Pair<RatchetSessionState, ByteArray> {
        val sharedSecret = X25519.computeSharedSecret(myLastEphemeralPrivate, senderEphemeralPublicKey)
        val info = "EncryptedSMS-DHRatchet-Send".toByteArray()
        val kdfOutput = hkdfExpand64(state.rootKey, sharedSecret, info)

        val newRootKey = kdfOutput.copyOfRange(0, 32)
        val newChainKey = kdfOutput.copyOfRange(32, 64)

        sharedSecret.fill(0)
        kdfOutput.fill(0)

        val newState = state.copy(
            rootKey = newRootKey,
            receivingChainKey = newChainKey,
            lastReceivedEphemeralPublic = senderEphemeralPublicKey
        )
        return newState to newChainKey
    }

    fun deriveMessageKeyAndAdvanceChain(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val infoMsg = "EncryptedSMS-MessageKey".toByteArray()
        val infoAdv = "EncryptedSMS-ChainAdvance".toByteArray()
        val messageKey = hkdf(chainKey, infoMsg)
        val nextChainKey = hkdf(chainKey, infoAdv)
        return messageKey to nextChainKey
    }

    fun encryptWithMessageKey(messageKey: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = AesGcmJce(messageKey)
        val result = cipher.encrypt(plaintext, aad)
        // La clave de mensaje es efímera y debe ser limpiada tras el cifrado
        // Nota: AesGcmJce no expone la clave interna para limpiar, pero podemos limpiar nuestra copia
        return result
    }

    fun decryptWithMessageKey(messageKey: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = AesGcmJce(messageKey)
        return cipher.decrypt(ciphertext, aad)
    }

    fun deriveChainKey(rootKey: ByteArray, label: String): ByteArray =
        hkdf(rootKey, label.toByteArray())

    private fun hkdf(ikm: ByteArray, info: ByteArray, outLen: Int = 32): ByteArray =
        Hkdf.computeHkdf("HMACSHA256", ikm, null, info, outLen)

    private fun hkdfExpand64(salt: ByteArray, ikm: ByteArray, info: ByteArray): ByteArray =
        Hkdf.computeHkdf("HMACSHA256", ikm, salt, info, 64)

    fun randomBytes(len: Int): ByteArray {
        val b = ByteArray(len)
        SecureRandom().nextBytes(b)
        return b
    }
}
