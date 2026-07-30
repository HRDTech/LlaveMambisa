package com.solucioneshr.llavemambisa.domain.model

import com.solucioneshr.llavemambisa.crypto.PublicIdentityBundle

data class Contact(
    val phoneNumber: String,
    val displayName: String,
    val identity: PublicIdentityBundle,
    val verifiedAt: Long,
    val trustLevel: TrustLevel,
    val appDetected: Boolean
)

enum class TrustLevel { UNVERIFIED, QR_VERIFIED, MANUAL_VERIFIED }

enum class MessageDirection { OUTGOING, INCOMING }

enum class DeliveryState { PENDING, SENT, DELIVERED, FAILED, RECEIVED }

data class ChatMessage(
    val id: Long,
    val contactPhoneNumber: String,
    val direction: MessageDirection,
    val body: String,
    val isEncryptedProtocol: Boolean,
    val timestamp: Long,
    val deliveryState: DeliveryState,
    val sequenceNumber: Int,
    val signatureValid: Boolean?
)
