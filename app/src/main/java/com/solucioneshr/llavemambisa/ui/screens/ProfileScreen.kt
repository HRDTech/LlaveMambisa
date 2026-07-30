package com.solucioneshr.llavemambisa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solucioneshr.llavemambisa.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editMode by remember { mutableStateOf(false) }
    var editPhone by remember { mutableStateOf(state.myPhone) }
    var editName by remember { mutableStateOf(state.myName) }

    LaunchedEffect(state.myPhone, state.myName) {
        editPhone = state.myPhone
        editName = state.myName
    }

    val sendResult = state.testSendResult
    if (sendResult != null) {
        val isError = sendResult.startsWith("Error") || sendResult.startsWith("Configura") || sendResult.startsWith("Añádate")
        AlertDialog(
            onDismissRequest = { viewModel.clearTestResult() },
            title = { Text(if (isError) "Error" else "Resultado") },
            text = { Text(sendResult) },
            confirmButton = { TextButton(onClick = { viewModel.clearTestResult() }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(if (editMode) Icons.Filled.Done else Icons.Filled.Edit, "Editar perfil")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (editMode) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Editar identidad", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Nombre mostrado") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("N\u00famero (ej: +5355...)") },
                            leadingIcon = { Icon(Icons.Filled.Phone, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                viewModel.updateIdentity(editPhone, editName)
                                editMode = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Actualizar QR") }
                    }
                }
            }

            Text(
                text = state.myName.ifBlank { "Yo" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (state.myPhone.isNotBlank()) {
                Text(state.myPhone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.myPhone.isBlank() && state.myName.isBlank() && !editMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Configura tu nombre y tel\u00e9fono para generar tu perfil cifrado. Presiona el icono de editar arriba.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            HorizontalDivider()

            Text(
                "Comparte este QR para que otros puedan a\u00f1adirte como contacto cifrado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            when {
                state.isLoading -> {
                    Box(
                        Modifier
                            .size(260.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(state.error!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { viewModel.loadProfile() }) { Text("Reintentar") }
                        }
                    }
                }
                state.qrBitmap != null -> {
                    Box(
                        Modifier
                            .size(260.dp)
                            .background(androidx.compose.ui.graphics.Color.White, MaterialTheme.shapes.medium)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = state.qrBitmap!!.asImageBitmap(),
                            contentDescription = "C\u00f3digo QR de identidad p\u00fablica",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.small)
                        )
                    }
                }
            }

            if (state.publicKeyText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Llave p\u00fablica", state.publicKeyText))
                            Toast.makeText(context, "Clave copiada al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar texto")
                    }
                    Button(
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, state.publicKeyText)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Mi clave p\u00fablica - Llave Mambisa")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir clave"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                }
            }

            HorizontalDivider()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Security, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Tu clave privada nunca sale del dispositivo. Solo compartes las claves p\u00fablicas necesarias para que otros puedan enviarte mensajes cifrados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (state.myPhone.isNotBlank()) {
                OutlinedButton(
                    onClick = { viewModel.addSelfAsContact() },
                    enabled = !state.selfContactAdded,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (state.selfContactAdded) Icons.Filled.CheckCircle else Icons.Filled.PersonAdd,
                        null, Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.selfContactAdded) "Ya añadido como contacto" else "Añadirme como contacto")
                }

                if (state.selfContactAdded) {
                    OutlinedButton(
                        onClick = { viewModel.sendTestToSelf() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enviar SMS de prueba")
                    }
                }
            }
        }
    }
}


