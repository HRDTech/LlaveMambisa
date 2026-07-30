package com.solucioneshr.llavemambisa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solucioneshr.llavemambisa.data.repository.SmsRepository
import com.solucioneshr.llavemambisa.domain.model.MessageDirection
import com.solucioneshr.llavemambisa.ui.viewmodel.ConversationItem
import com.solucioneshr.llavemambisa.ui.viewmodel.ConversationsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onOpenChat: (phoneNumber: String) -> Unit,
    onNewConversation: () -> Unit,
    smsRepository: SmsRepository? = null
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Llave Mambisa", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (smsRepository != null) {
                        IconButton(
                            onClick = {
                                isScanning = true
                                scope.launch {
                                    smsRepository.scanInboxForNewMessages()
                                    isScanning = false
                                }
                            },
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, "Buscar en inbox")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewConversation,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("Nueva") }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyConversationsPlaceholder(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(conversations, key = { it.lastMessage.contactPhoneNumber }) { item ->
                    ConversationRow(item = item, onClick = { onOpenChat(item.lastMessage.contactPhoneNumber) })
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(item: ConversationItem, onClick: () -> Unit) {
    val msg = item.lastMessage
    val isEncrypted = msg.isEncryptedProtocol
    val sigBad = msg.signatureValid == false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (item.contactName ?: msg.contactPhoneNumber).take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.contactName ?: msg.contactPhoneNumber,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = formatTimestamp(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (msg.direction == MessageDirection.OUTGOING) {
                    Icon(
                        Icons.Filled.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                if (isEncrypted) {
                    Icon(
                        if (sigBad) Icons.Filled.Warning else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (sigBad) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    text = msg.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun EmptyConversationsPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Forum,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                "Sin conversaciones a\u00fan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "Agrega un contacto y empieza\na chatear de forma cifrada",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
