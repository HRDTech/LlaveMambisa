package com.solucioneshr.llavemambisa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratchet_sessions")
data class RatchetSessionEntity(
    @PrimaryKey val sessionIdHex: String,
    val contactPhoneNumber: String,
    val rootKeyEncrypted: ByteArray,
    val sendingChainKeyEncrypted: ByteArray?,
    val receivingChainKeyEncrypted: ByteArray?,
    val lastSentEphemeralPrivateEncrypted: ByteArray?,
    val lastReceivedEphemeralPublic: ByteArray?,
    val lastUpdated: Long = System.currentTimeMillis()
)
