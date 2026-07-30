package com.solucioneshr.llavemambisa.data.repository

import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.solucioneshr.llavemambisa.crypto.CryptoManager
import com.solucioneshr.llavemambisa.crypto.KeyManager
import com.solucioneshr.llavemambisa.crypto.PublicIdentityBundle
import com.solucioneshr.llavemambisa.crypto.SmsWireFormat
import com.solucioneshr.llavemambisa.crypto.FingerprintUtils
import com.solucioneshr.llavemambisa.data.local.AppDatabase
import com.solucioneshr.llavemambisa.data.local.SecureFieldCrypto
import com.solucioneshr.llavemambisa.data.local.entity.ContactEntity
import com.solucioneshr.llavemambisa.data.local.entity.MessageEntity
import com.solucioneshr.llavemambisa.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repositorio único que coordina: cifrado (CryptoManager/KeyManager), persistencia local
 * cifrada (Room + SecureFieldCrypto), y el envío/recepción real por SMS (SmsManager).
 */
class SmsRepository(
    private val context: Context,
    private val keyManager: KeyManager,
    private val cryptoManager: CryptoManager,
    private val secureFieldCrypto: SecureFieldCrypto,
    db: AppDatabase = AppDatabase.getInstance(context)
) {
    companion object {
        private const val TAG = "SmsRepository"
        private const val MAX_SMS_PARTS = 10
    }

    private val contactDao = db.contactDao()
    private val messageDao = db.messageDao()
    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
        ?: SmsManager.getDefault()

    // --- Contactos ---

    fun observeContacts(): Flow<List<Contact>> =
        contactDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addOrUpdateContact(
        phoneNumber: String,
        displayName: String,
        identity: PublicIdentityBundle,
        trustLevel: TrustLevel
    ) {
        withContext(Dispatchers.IO) {
            val normalized = normalizePhoneNumber(phoneNumber)
            val entity = ContactEntity(
                phoneNumber = normalized,
                displayName = displayName,
                signingPublicKeyEncrypted = secureFieldCrypto.encryptField(identity.signingPublicKeyset, normalized.toByteArray()),
                hybridPublicKeyEncrypted = secureFieldCrypto.encryptField(identity.hybridPublicKeyset, normalized.toByteArray()),
                verifiedAt = System.currentTimeMillis(),
                trustLevel = trustLevel.ordinal,
                appDetected = true
            )
            contactDao.upsert(entity)
        }
    }

    suspend fun addSelfContact(phoneNumber: String, displayName: String) {
        val identity = keyManager.exportPublicIdentityBundle()
        addOrUpdateContact(phoneNumber, displayName, identity, TrustLevel.MANUAL_VERIFIED)
    }

    suspend fun updateTrustLevel(phoneNumber: String, level: TrustLevel) {
        withContext(Dispatchers.IO) {
            val normalized = normalizePhoneNumber(phoneNumber)
            contactDao.updateTrustLevel(normalized, level.ordinal)
        }
    }

    fun getFingerprintFor(theirIdentity: PublicIdentityBundle): String {
        val myIdentity = keyManager.exportPublicIdentityBundle()
        return FingerprintUtils.getFingerprint(myIdentity, theirIdentity)
    }

    // --- Mensajería ---

    fun observeHistory(phoneNumber: String): Flow<List<ChatMessage>> =
        messageDao.observeForContact(normalizePhoneNumber(phoneNumber))
            .map { list -> list.map { it.toDomain(secureFieldCrypto) } }

    fun observeRecentActivity(): Flow<List<ChatMessage>> =
        messageDao.observeRecent().map { list ->
            list.map { it.toDomain(secureFieldCrypto) }
        }

    sealed class SendResult {
        data class Success(val messageId: Long, val parts: Int) : SendResult()
        data class Failure(val reason: String) : SendResult()
    }

    suspend fun sendMessage(
        phoneNumber: String,
        plaintext: String,
        forceFallbackPlaintext: Boolean = false
    ): SendResult = withContext(Dispatchers.IO) {
        val normalized = normalizePhoneNumber(phoneNumber)
        val contactEntity = contactDao.findByPhone(normalized)

        if (forceFallbackPlaintext || contactEntity == null) {
            return@withContext sendPlainFallback(normalized, plaintext)
        }

        val identity = contactEntity.toIdentity(secureFieldCrypto)
        val myIdentity = keyManager.exportPublicIdentityBundle()
        val sessionId = cryptoManager.deriveSessionId(myIdentity, identity)
        val sequence = getNextSequenceFor(normalized)

        when (val result = cryptoManager.encryptOutgoingSms(plaintext, identity, sessionId, sequence, normalized)) {
            is CryptoManager.CryptoResult.Success -> {
                val serialized = result.value
                val parts = smsManager.divideMessage(serialized)
                val rowId = messageDao.insert(
                    MessageEntity(
                        contactPhoneNumber = normalized,
                        direction = "OUTGOING",
                        bodyEncrypted = secureFieldCrypto.encryptString(plaintext, normalized),
                        isEncryptedProtocol = true,
                        timestamp = System.currentTimeMillis(),
                        deliveryState = "PENDING",
                        sequenceNumber = sequence
                    )
                )
                val sendError = sendSmsParts(normalized, parts, rowId)
                if (sendError != null) {
                    messageDao.updateDeliveryState(rowId, "FAILED")
                    return@withContext SendResult.Failure(sendError)
                }
                SendResult.Success(rowId, parts.size)
            }
            is CryptoManager.CryptoResult.Failure -> SendResult.Failure(result.message)
        }
    }

    suspend fun sendTestToSelf(phoneNumber: String): SendResult {
        return sendMessage(phoneNumber, "Prueba de cifrado ESM1 (Autotransmisión)")
    }

    private suspend fun sendPlainFallback(phoneNumber: String, plaintext: String): SendResult {
        val parts = smsManager.divideMessage(plaintext)
        val rowId = messageDao.insert(
            MessageEntity(
                contactPhoneNumber = phoneNumber,
                direction = "OUTGOING",
                bodyEncrypted = secureFieldCrypto.encryptString(plaintext, phoneNumber),
                isEncryptedProtocol = false,
                timestamp = System.currentTimeMillis(),
                deliveryState = "PENDING",
                sequenceNumber = 0
            )
        )
        val sendError = sendSmsParts(phoneNumber, parts, rowId)
        if (sendError != null) {
            messageDao.updateDeliveryState(rowId, "FAILED")
            return SendResult.Failure(sendError)
        }
        return SendResult.Success(rowId, parts.size)
    }

    private fun sendSmsParts(phoneNumber: String, parts: ArrayList<String>, rowId: Long): String? {
        if (parts.isEmpty()) return "No hay contenido para enviar"

        val sendSmsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!sendSmsGranted) return "Permiso SEND_SMS no concedido"

        if (parts.size > MAX_SMS_PARTS) {
            return "Mensaje demasiado largo (${parts.size} partes)."
        }

        val smsCandidates = resolveSmsManagersForSend()
        val sentIntents = ArrayList<PendingIntent>()
        repeat(parts.size) { index ->
            val sentIntent = Intent("com.solucioneshr.llavemambisa.SMS_SENT").apply {
                putExtra("rowId", rowId)
                putExtra("part", index)
                putExtra("partsTotal", parts.size)
                setPackage(context.packageName)
            }
            sentIntents.add(
                PendingIntent.getBroadcast(
                    context, (rowId * 1000 + index).toInt(), sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        var lastError: String? = null
        for ((_, manager) in smsCandidates) {
            try {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
                return null
            } catch (t: Throwable) {
                lastError = t.message
                Log.e(TAG, "Error enviando SMS", t)
            }
        }
        return lastError ?: "Fallo al enviar"
    }

    private fun resolveSmsManagersForSend(): List<Pair<Int, SmsManager>> {
        val candidates = mutableListOf<Pair<Int, SmsManager>>()
        val defaultSubId = SmsManager.getDefaultSmsSubscriptionId()

        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            candidates.add(defaultSubId to SmsManager.getSmsManagerForSubscriptionId(defaultSubId))
        }

        val subManager = context.getSystemService(SubscriptionManager::class.java)
        try {
            subManager?.activeSubscriptionInfoList?.forEach { sub ->
                if (sub.subscriptionId != defaultSubId) {
                    candidates.add(sub.subscriptionId to SmsManager.getSmsManagerForSubscriptionId(sub.subscriptionId))
                }
            }
        } catch (_: SecurityException) {}

        if (candidates.isEmpty()) {
            candidates.add(SubscriptionManager.INVALID_SUBSCRIPTION_ID to smsManager)
        }
        return candidates
    }

    suspend fun handleIncomingSms(fromPhoneNumber: String, body: String): ChatMessage? = withContext(Dispatchers.IO) {
        val normalized = normalizePhoneNumber(fromPhoneNumber)

        if (!SmsWireFormat.looksEncrypted(body)) {
            val entity = MessageEntity(
                contactPhoneNumber = normalized,
                direction = "INCOMING",
                bodyEncrypted = secureFieldCrypto.encryptString(body, normalized),
                isEncryptedProtocol = false,
                timestamp = System.currentTimeMillis(),
                deliveryState = "RECEIVED",
                sequenceNumber = 0
            )
            val id = messageDao.insert(entity)
            return@withContext entity.copy(id = id).toDomain(secureFieldCrypto)
        }

        try {
            val contactEntity = findContactByPhone(normalized)
            if (contactEntity == null) {
                return@withContext saveUnknownEncryptedMessage(normalized)
            }

            val identity = contactEntity.toIdentity(secureFieldCrypto)
            val result = cryptoManager.decryptIncomingSms(body, identity, normalized)
            
            val (plaintext, sigValid, seq) = when (result) {
                is CryptoManager.CryptoResult.Success -> {
                    val envelope = SmsWireFormat.deserialize(body)
                    Triple(result.value, true, envelope?.sequence ?: 0)
                }
                is CryptoManager.CryptoResult.Failure -> {
                    if (result.reason == CryptoManager.FailureReason.SIGNATURE_MISMATCH) {
                        Log.w(TAG, "Firma inválida para $normalized — posible identidad desactualizada")
                    }
                    Triple("[Fallo al descifrar: ${result.message}]", false, 0)
                }
            }

            val entity = MessageEntity(
                contactPhoneNumber = normalized,
                direction = "INCOMING",
                bodyEncrypted = secureFieldCrypto.encryptString(plaintext, normalized),
                isEncryptedProtocol = true,
                timestamp = System.currentTimeMillis(),
                deliveryState = "RECEIVED",
                sequenceNumber = seq,
                signatureValid = sigValid
            )
            val id = messageDao.insert(entity)
            entity.copy(id = id).toDomain(secureFieldCrypto)
        } catch (t: Throwable) {
            Log.e(TAG, "Error en handleIncomingSms", t)
            null
        }
    }

    private suspend fun saveUnknownEncryptedMessage(normalized: String): ChatMessage {
        val entity = MessageEntity(
            contactPhoneNumber = normalized,
            direction = "INCOMING",
            bodyEncrypted = secureFieldCrypto.encryptString(
                "[Mensaje cifrado de remitente desconocido]",
                normalized
            ),
            isEncryptedProtocol = true,
            timestamp = System.currentTimeMillis(),
            deliveryState = "RECEIVED",
            sequenceNumber = 0,
            signatureValid = false
        )
        val id = messageDao.insert(entity)
        return entity.copy(id = id).toDomain(secureFieldCrypto)
    }

    suspend fun scanInboxForNewMessages(): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(Telephony.Sms.Inbox._ID, Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.BODY)
            val sortOrder = "${Telephony.Sms.Inbox._ID} DESC LIMIT 50"

            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { c ->
                val addrIdx = c.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.Inbox.BODY)

                while (c.moveToNext()) {
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""
                    if (SmsWireFormat.looksEncrypted(body)) {
                        val address = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                        if (handleIncomingSms(address, body) != null) count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando inbox", e)
        }
        count
    }

    suspend fun encryptMessageForExternal(phoneNumber: String, plaintext: String): String? = withContext(Dispatchers.IO) {
        val normalized = normalizePhoneNumber(phoneNumber)
        val contactEntity = contactDao.findByPhone(normalized) ?: return@withContext null
        val identity = contactEntity.toIdentity(secureFieldCrypto)
        val myIdentity = keyManager.exportPublicIdentityBundle()
        val sessionId = cryptoManager.deriveSessionId(myIdentity, identity)
        val sequence = getNextSequenceFor(normalized)

        val result = cryptoManager.encryptOutgoingSms(plaintext, identity, sessionId, sequence, normalized)
        val ciphertext = (result as? CryptoManager.CryptoResult.Success)?.value ?: return@withContext null

        messageDao.insert(
            MessageEntity(
                contactPhoneNumber = normalized,
                direction = "OUTGOING",
                bodyEncrypted = secureFieldCrypto.encryptString(plaintext, normalized),
                isEncryptedProtocol = true,
                timestamp = System.currentTimeMillis(),
                deliveryState = "SENT",
                sequenceNumber = sequence
            )
        )
        ciphertext
    }

    suspend fun decryptExternalMessage(ciphertext: String, fromPhone: String): String? = withContext(Dispatchers.IO) {
        val normalized = normalizePhoneNumber(fromPhone)
        val contact = findContactByPhone(normalized) ?: return@withContext null
        val identity = contact.toIdentity(secureFieldCrypto)
        when (val result = cryptoManager.decryptIncomingSms(ciphertext, identity, normalized)) {
            is CryptoManager.CryptoResult.Success -> result.value
            is CryptoManager.CryptoResult.Failure -> null
        }
    }

    private suspend fun findContactByPhone(phone: String): ContactEntity? {
        var entity = contactDao.findByPhone(phone)
        if (entity == null) {
            val alt = if (phone.startsWith("+")) phone.substring(1) else "+$phone"
            if (alt != phone) entity = contactDao.findByPhone(alt)
        }
        return entity
    }

    private suspend fun getNextSequenceFor(phone: String): Int {
        return (messageDao.getLastSequenceForContact(phone) ?: 0) + 1
    }

    private fun normalizePhoneNumber(raw: String): String = raw.filter { it.isDigit() || it == '+' }

    private fun ContactEntity.toDomain(): Contact = Contact(
        phoneNumber = phoneNumber,
        displayName = displayName,
        identity = toIdentity(secureFieldCrypto),
        verifiedAt = verifiedAt,
        trustLevel = TrustLevel.entries[trustLevel],
        appDetected = appDetected
    )

    private fun ContactEntity.toIdentity(crypto: SecureFieldCrypto): PublicIdentityBundle = PublicIdentityBundle(
        signingPublicKeyset = crypto.decryptField(signingPublicKeyEncrypted, phoneNumber.toByteArray()),
        hybridPublicKeyset = crypto.decryptField(hybridPublicKeyEncrypted, phoneNumber.toByteArray())
    )

    private fun MessageEntity.toDomain(crypto: SecureFieldCrypto): ChatMessage = ChatMessage(
        id = id,
        contactPhoneNumber = contactPhoneNumber,
        direction = if (direction == "OUTGOING") MessageDirection.OUTGOING else MessageDirection.INCOMING,
        body = crypto.decryptString(bodyEncrypted, contactPhoneNumber),
        isEncryptedProtocol = isEncryptedProtocol,
        timestamp = timestamp,
        deliveryState = DeliveryState.valueOf(deliveryState),
        sequenceNumber = sequenceNumber,
        signatureValid = signatureValid
    )
}
