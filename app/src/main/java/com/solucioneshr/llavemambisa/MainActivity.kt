package com.solucioneshr.llavemambisa

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.solucioneshr.llavemambisa.di.AppContainer
import com.solucioneshr.llavemambisa.ui.navigation.AppNavigation
import com.solucioneshr.llavemambisa.ui.theme.LlaveMambisaTheme
import com.solucioneshr.llavemambisa.util.Logger

/**
 * Actividad principal de Llave Mambisa.
 * Gestiona permisos SMS y arranca la navegación completa de la app.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Logger.uiDebug("MainActivity.onCreate")

        val app = application as EncryptedSmsApp
        val container = app.container
        // El extra "openChatWith" puede venir desde SmsReceiver vía notificación
        val openChatWith = intent?.getStringExtra("openChatWith")

        setContent {
            LlaveMambisaTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(container = container, openChatWith = openChatWith)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.uiDebug("MainActivity.onDestroy")
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun AppRoot(container: AppContainer, openChatWith: String?) {
    val smsPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
    )
    if (smsPermissions.allPermissionsGranted) {
        AppNavigation(container = container, startChatWith = openChatWith)
    } else {
        SmsPermissionsScreen(
            showRationale = smsPermissions.shouldShowRationale,
            onRequest = { smsPermissions.launchMultiplePermissionRequest() }
        )
    }
}

@Composable
private fun SmsPermissionsScreen(showRationale: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Llave Mambisa",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Mensajería SMS cifrada de extremo a extremo",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Sms, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Llave Mambisa necesita permisos de SMS para funcionar.", style = MaterialTheme.typography.bodyMedium)
                }
                if (showRationale) {
                    Text(
                        "Los permisos son estrictamente necesarios para enviar y recibir mensajes cifrados. Tu información nunca se comparte con terceros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Conceder permisos")
        }
    }
}
