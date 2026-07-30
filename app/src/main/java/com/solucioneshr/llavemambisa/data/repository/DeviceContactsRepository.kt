package com.solucioneshr.llavemambisa.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.solucioneshr.llavemambisa.data.DeviceContact
import com.solucioneshr.llavemambisa.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceContactsRepository(private val context: Context) {

    suspend fun searchContacts(query: String? = null): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            val selection = if (query.isNullOrBlank()) null
            else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = if (query.isNullOrBlank()) null
            else arrayOf("%$query%", "%$query%")
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            val contactMap = linkedMapOf<String, DeviceContact>()
            context.contentResolver.query(
                uri, projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else return@use
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Sin nombre" else "Sin nombre"
                    val number = if (numIdx >= 0) cursor.getString(numIdx)?.filter { it.isDigit() || it == '+' } ?: "" else ""
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    if (number.isBlank()) continue
                    val existing = contactMap[id]
                    if (existing != null) {
                        contactMap[id] = existing.copy(
                            phoneNumbers = (existing.phoneNumbers + number).distinct()
                        )
                    } else {
                        contactMap[id] = DeviceContact(id, name, listOf(number), photo)
                    }
                }
            }
            contactMap.values.toList()
        } catch (e: Exception) {
            Logger.e("DeviceContactsRepo", "Error al leer contactos", e)
            emptyList()
        }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
}
