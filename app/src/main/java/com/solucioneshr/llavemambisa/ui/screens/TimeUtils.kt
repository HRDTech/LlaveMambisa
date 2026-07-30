package com.solucioneshr.llavemambisa.ui.screens

import java.text.SimpleDateFormat
import java.util.*

internal fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "Ahora"
        diff < 3_600_000L -> "${diff / 60_000}m"
        diff < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 604_800_000L -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}

