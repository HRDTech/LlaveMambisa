package com.solucioneshr.llavemambisa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.solucioneshr.llavemambisa.di.AppContainer
import com.solucioneshr.llavemambisa.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EncryptedSmsApp : Application() {

    lateinit var container: AppContainer
        private set

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Logger.setDebugBuild(BuildConfig.DEBUG)
        Logger.i("App", "Iniciando Llave Mambisa (debug=${BuildConfig.DEBUG})")
        container = AppContainer(this)
        createNotificationChannels()
        startInboxPolling()
        Logger.i("App", "Inicialización completada")
    }

    private fun startInboxPolling() {
        appScope.launch {
            delay(5_000L)
            Logger.i("InboxPoller", "Iniciando escaneo inicial del inbox")
            try {
                val count = container.smsRepository.scanInboxForNewMessages()
                Logger.i("InboxPoller", "Escaneo inicial completado: $count mensajes importados")
            } catch (e: Exception) {
                Logger.e("InboxPoller", "Error en escaneo inicial del inbox", e)
            }

            while (isActive) {
                delay(60_000L)
                Logger.i("InboxPoller", "Polling periódico del inbox")
                try {
                    val count = container.smsRepository.scanInboxForNewMessages()
                    if (count > 0) Logger.i("InboxPoller", "Importados $count mensajes nuevos del inbox")
                } catch (e: Exception) {
                    Logger.e("InboxPoller", "Error polling inbox", e)
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val smsChannel = NotificationChannel(
                CHANNEL_ENCRYPTED_SMS,
                "Mensajes cifrados entrantes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de nuevos SMS cifrados o normales"
                enableVibration(true)
            }
            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                "Alertas de seguridad",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de intentos de falsificación o errores críticos de seguridad"
            }
            manager.createNotificationChannels(listOf(smsChannel, securityChannel))
        }
    }

    companion object {
        const val CHANNEL_ENCRYPTED_SMS = "encrypted_sms_incoming"
        const val CHANNEL_SECURITY_ALERTS = "security_alerts"
    }
}
