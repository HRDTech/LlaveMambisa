package com.solucioneshr.llavemambisa.di

import android.content.Context
import com.solucioneshr.llavemambisa.crypto.CryptoManager
import com.solucioneshr.llavemambisa.crypto.KeyManager
import com.solucioneshr.llavemambisa.data.local.AppDatabase
import com.solucioneshr.llavemambisa.data.local.SecureFieldCrypto
import com.solucioneshr.llavemambisa.data.UserPreferences
import com.solucioneshr.llavemambisa.data.repository.DeviceContactsRepository
import com.solucioneshr.llavemambisa.data.repository.SmsInboxReader
import com.solucioneshr.llavemambisa.data.repository.SmsRepository

class AppContainer(context: Context) {
    val database = AppDatabase.getInstance(context)
    val keyManager = KeyManager(context)
    val secureFieldCrypto = SecureFieldCrypto(context)
    val cryptoManager = CryptoManager(
        keyManager = keyManager,
        sessionDao = database.ratchetSessionDao(),
        secureFieldCrypto = secureFieldCrypto
    )
    val smsRepository = SmsRepository(
        context = context,
        keyManager = keyManager,
        cryptoManager = cryptoManager,
        secureFieldCrypto = secureFieldCrypto,
        db = database
    )
    val deviceContactsRepository = DeviceContactsRepository(context)
    val smsInboxReader = SmsInboxReader(context)
    val userPreferences = UserPreferences(context)
}
