package com.solucioneshr.llavemambisa.data.local.dao

import androidx.room.*
import com.solucioneshr.llavemambisa.data.local.entity.RatchetSessionEntity

@Dao
interface RatchetSessionDao {
    @Query("SELECT * FROM ratchet_sessions WHERE sessionIdHex = :sessionIdHex LIMIT 1")
    suspend fun getSession(sessionIdHex: String): RatchetSessionEntity?

    @Query("SELECT * FROM ratchet_sessions WHERE contactPhoneNumber = :phone LIMIT 1")
    suspend fun getSessionByPhone(phone: String): RatchetSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: RatchetSessionEntity)

    @Query("DELETE FROM ratchet_sessions WHERE sessionIdHex = :sessionIdHex")
    suspend fun deleteSession(sessionIdHex: String)

    @Query("DELETE FROM ratchet_sessions WHERE contactPhoneNumber = :phone")
    suspend fun deleteByPhone(phone: String)
}
