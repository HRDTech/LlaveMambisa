package com.solucioneshr.llavemambisa.data

data class DeviceContact(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String? = null
) {
    val primaryPhone: String? get() = phoneNumbers.firstOrNull()
}
