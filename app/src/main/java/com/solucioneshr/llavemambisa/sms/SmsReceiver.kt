package com.solucioneshr.llavemambisa.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.annotation.SuppressLint
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.solucioneshr.llavemambisa.EncryptedSmsApp
import com.solucioneshr.llavemambisa.MainActivity
import com.solucioneshr.llavemambisa.R
import com.solucioneshr.llavemambisa.util.Logger
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            action != Telephony.Sms.Intents.SMS_DELIVER_ACTION
        ) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val grouped = messages.groupBy { it.originatingAddress ?: "unknown" }

        val pendingResult = goAsync()
        val appContext = context.applicationContext as EncryptedSmsApp

        appContext.appScope.launch {
            try {
                grouped.forEach { (address, parts) ->
                    try {
                        val fullBody = parts.joinToString(separator = "") { it.messageBody ?: "" }
                        val chatMessage = appContext.container.smsRepository.handleIncomingSms(address, fullBody)
                        if (chatMessage != null) {
                            notifyIncoming(context, address, chatMessage.isEncryptedProtocol, chatMessage.signatureValid)
                        }
                    } catch (t: Throwable) {
                        Logger.e(TAG, "Error procesando SMS entrante de $address", t)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission", "NotificationPermission")
    private fun notifyIncoming(context: Context, from: String, wasEncrypted: Boolean, signatureValid: Boolean?) {
        val channelId = "encrypted_sms_incoming"
        val title = if (wasEncrypted) {
            if (signatureValid == true) "Mensaje cifrado recibido" else "Mensaje cifrado con firma invalida"
        } else {
            "SMS normal recibido"
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openChatWith", from)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, from.hashCode(), openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_lock_notification)
            .setContentTitle(title)
            .setContentText("De: $from")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(from.hashCode(), notification)
        } catch (se: SecurityException) {
            Logger.w(TAG, "No se pudo mostrar notificacion para SMS entrante", se)
        }
    }
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rowId = intent.getLongExtra("rowId", -1L)
        val part = intent.getIntExtra("part", -1)
        val partsTotal = intent.getIntExtra("partsTotal", -1)
        if (rowId < 0) return
        val newState = when (resultCode) {
            Activity.RESULT_OK -> "SENT"
            else -> "FAILED"
        }

        val reason = when (resultCode) {
            Activity.RESULT_OK -> "OK"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                val noDefault = intent.getBooleanExtra("noDefault", false)
                if (noDefault) "GENERIC_FAILURE (sin SIM predeterminada para SMS)" else "GENERIC_FAILURE"
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
            SmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "SHORT_CODE_NOT_ALLOWED"
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "SHORT_CODE_NEVER_ALLOWED"
            else -> "RESULT_CODE_$resultCode"
        }

        Logger.i("SmsStatusReceiver", "SMS_SENT rowId=$rowId part=$part/$partsTotal state=$newState reason=$reason")

        val appContext = context.applicationContext as EncryptedSmsApp
        appContext.appScope.launch {
            appContext.container.database.messageDao().updateDeliveryState(rowId, newState)
        }
    }
}
