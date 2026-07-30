package com.solucioneshr.llavemambisa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solucioneshr.llavemambisa.data.repository.SmsInboxItem
import com.solucioneshr.llavemambisa.data.repository.SmsInboxReader
import com.solucioneshr.llavemambisa.data.repository.SmsRepository
import com.solucioneshr.llavemambisa.util.Logger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxScannerDialog(
    inboxReader: SmsInboxReader,
    smsRepository: SmsRepository,
    onDismiss: () -> Unit,
    onChatOpened: (String) -> Unit
) {
    var inboxItems by remember { mutableStateOf<List<SmsInboxItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var processCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            inboxItems = inboxReader.readInbox()
        } catch (e: Exception) {
            Logger.e("InboxScanner", "Error reading inbox", e)
            errorMsg = "Error al leer la bandeja de entrada: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bandeja de entrada", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Cerrar")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                    return@Column
                }
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Leyendo mensajes...", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    return@Column
                }

                if (processCount > 0) {
                    Text(
                        "$processCount mensajes cifrados procesados",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }

                val encryptedItems = inboxItems.filter { it.isEncrypted }
                if (encryptedItems.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Sms, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("No se encontraron mensajes cifrados", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    Text(
                        "${encryptedItems.size} mensaje(s) cifrado(s) encontrado(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                        items(encryptedItems, key = { it.id }) { item ->
                            InboxItemRow(item = item)
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                if (encryptedItems.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                var count = 0
                                for (item in encryptedItems) {
                                    try {
                                        smsRepository.handleIncomingSms(item.address, item.body)
                                        count++
                                    } catch (e: Exception) {
                                        Logger.e("InboxScanner", "Error procesando SMS ${item.id}", e)
                                    }
                                }
                                processCount = count
                                inboxItems = emptyList()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ImportContacts, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Importar mensajes cifrados")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun InboxItemRow(item: SmsInboxItem) {
    ListItem(
        headlineContent = {
            Text(
                item.address,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                item.body.take(80),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Cifrado",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    )
}
