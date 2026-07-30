package com.solucioneshr.llavemambisa.crypto

import android.util.Base64
import org.json.JSONObject

/**
 * Formato de "sobre" (envelope) optimizado para minimizar el tamaño del SMS.
 * Usa claves de una sola letra para reducir el overhead del JSON.
 *
 * v: version
 * e: ephemeralPublicKey
 * c: ciphertext
 * g: signature (sig)
 * i: sessionId (sid)
 * q: sequence (seq)
 * b: bootstrap (boot)
 */
object SmsWireFormat {

    const val MAGIC_PREFIX = "ESM1:"
    private const val SINGLE_SMS_SAFE_LEN = 130

    data class Envelope(
        val version: Int,
        val ephemeralPublicKey: ByteArray,
        val ciphertext: ByteArray,
        val signature: ByteArray,
        val sessionId: ByteArray,
        val sequence: Int,
        val bootstrap: ByteArray? = null
    )

    fun serialize(envelope: Envelope): String {
        val json = JSONObject().apply {
            put("v", envelope.version)
            put("e", Base64.encodeToString(envelope.ephemeralPublicKey, Base64.NO_WRAP))
            put("c", Base64.encodeToString(envelope.ciphertext, Base64.NO_WRAP))
            put("g", Base64.encodeToString(envelope.signature, Base64.NO_WRAP))
            put("i", Base64.encodeToString(envelope.sessionId, Base64.NO_WRAP))
            put("q", envelope.sequence)
            envelope.bootstrap?.let {
                put("b", Base64.encodeToString(it, Base64.NO_WRAP))
            }
        }
        return MAGIC_PREFIX + json.toString()
    }

    fun looksEncrypted(smsBody: String): Boolean = smsBody.startsWith(MAGIC_PREFIX)

    fun deserialize(smsBody: String): Envelope? {
        if (!looksEncrypted(smsBody)) return null
        return try {
            val json = JSONObject(smsBody.removePrefix(MAGIC_PREFIX))
            Envelope(
                version = json.getInt("v"),
                ephemeralPublicKey = Base64.decode(json.getString("e"), Base64.NO_WRAP),
                ciphertext = Base64.decode(json.getString("c"), Base64.NO_WRAP),
                signature = Base64.decode(json.getString("g"), Base64.NO_WRAP),
                sessionId = Base64.decode(json.getString("i"), Base64.NO_WRAP),
                sequence = json.getInt("q"),
                bootstrap = if (json.has("b")) Base64.decode(json.getString("b"), Base64.NO_WRAP) else null
            )
        } catch (e: Exception) {
            null
        }
    }
}
