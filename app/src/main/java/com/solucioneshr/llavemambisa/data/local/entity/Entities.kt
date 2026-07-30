package com.solucioneshr.llavemambisa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Contacto con su bundle de claves públicas de identidad. Los campos de claves se guardan
 * como BLOB pero pasan primero por [com.solucioneshr.llavemambisa.data.local.SecureFieldCrypto]
 * (AES-256-GCM con clave anclada a Keystore) antes de tocar disco: zero-knowledge en
 * almacenamiento incluso si el archivo de base de datos es exfiltrado directamente.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val phoneNumber: String, // normalizado E.164
    val displayName: String,
    val signingPublicKeyEncrypted: ByteArray,
    val hybridPublicKeyEncrypted: ByteArray,
    val verifiedAt: Long, // timestamp del escaneo QR / confirmación manual
    val trustLevel: Int, // 0=no verificado, 1=verificado por QR, 2=verificado manualmente
    val appDetected: Boolean = false // detectado vía prefijo ESM1: en mensajes previos
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactEntity) return false
        return phoneNumber == other.phoneNumber
    }
    override fun hashCode(): Int = phoneNumber.hashCode()
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactPhoneNumber: String,
    val direction: String, // "OUTGOING" | "INCOMING"
    val bodyEncrypted: ByteArray, // texto plano cifrado en reposo con SecureFieldCrypto
    val isEncryptedProtocol: Boolean, // true = fue cifrado ESM1, false = SMS normal (fallback)
    val timestamp: Long,
    val deliveryState: String, // "PENDING" | "SENT" | "DELIVERED" | "FAILED" | "RECEIVED"
    val sequenceNumber: Int,
    val signatureValid: Boolean? = null // null si no aplica (mensajes salientes o SMS normales)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
