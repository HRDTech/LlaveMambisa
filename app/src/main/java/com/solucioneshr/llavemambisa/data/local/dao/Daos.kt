package com.solucioneshr.llavemambisa.data.local.dao

import androidx.room.*
import com.solucioneshr.llavemambisa.data.local.entity.ContactEntity
import com.solucioneshr.llavemambisa.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET trustLevel = :level WHERE phoneNumber = :phone")
    suspend fun updateTrustLevel(phone: String, level: Int)

    @Delete
    suspend fun delete(contact: ContactEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactPhoneNumber = :phone ORDER BY timestamp ASC")
    fun observeForContact(phone: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET deliveryState = :state WHERE id = :id")
    suspend fun updateDeliveryState(id: Long, state: String)

    @Query("DELETE FROM messages WHERE contactPhoneNumber = :phone")
    suspend fun deleteHistoryForContact(phone: String)

    @Query("SELECT MAX(sequenceNumber) FROM messages WHERE contactPhoneNumber = :phone")
    suspend fun getLastSequenceForContact(phone: String): Int?
}
