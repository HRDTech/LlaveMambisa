package com.solucioneshr.llavemambisa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.solucioneshr.llavemambisa.data.local.dao.ContactDao
import com.solucioneshr.llavemambisa.data.local.dao.MessageDao
import com.solucioneshr.llavemambisa.data.local.dao.RatchetSessionDao
import com.solucioneshr.llavemambisa.data.local.entity.ContactEntity
import com.solucioneshr.llavemambisa.data.local.entity.MessageEntity
import com.solucioneshr.llavemambisa.data.local.entity.RatchetSessionEntity

@Database(
    entities = [ContactEntity::class, MessageEntity::class, RatchetSessionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun ratchetSessionDao(): RatchetSessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "encryptedsms.db"
                )
                    .enableMultiInstanceInvalidation() // Recomendación: Soporte para múltiples procesos (SmsReceiver en :sms_worker)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
