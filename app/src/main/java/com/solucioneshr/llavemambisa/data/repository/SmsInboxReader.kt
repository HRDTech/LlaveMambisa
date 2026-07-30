package com.solucioneshr.llavemambisa.data.repository

import android.content.Context
import android.provider.Telephony
import com.solucioneshr.llavemambisa.crypto.SmsWireFormat
import com.solucioneshr.llavemambisa.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsInboxItem(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val isEncrypted: Boolean
)

class SmsInboxReader(private val context: Context) {

    suspend fun readInbox(): List<SmsInboxItem> = withContext(Dispatchers.IO) {
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.Inbox._ID,
                Telephony.Sms.Inbox.ADDRESS,
                Telephony.Sms.Inbox.BODY,
                Telephony.Sms.Inbox.DATE
            )
            val sortOrder = "${Telephony.Sms.Inbox.DATE} DESC"

            val items = mutableListOf<SmsInboxItem>()
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms.Inbox._ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.Inbox.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.Inbox.DATE)
                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val address = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    if (body.isBlank()) continue
                    items.add(SmsInboxItem(id, address, body, date, SmsWireFormat.looksEncrypted(body)))
                }
            }
            items
        } catch (e: Exception) {
            Logger.e("SmsInboxReader", "Error al leer inbox SMS", e)
            emptyList()
        }
    }
}
