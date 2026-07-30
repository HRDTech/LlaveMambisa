package com.solucioneshr.llavemambisa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solucioneshr.llavemambisa.data.DeviceContact
import com.solucioneshr.llavemambisa.data.repository.DeviceContactsRepository
import com.solucioneshr.llavemambisa.util.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceContactPickerDialog(
    repository: DeviceContactsRepository,
    onContactSelected: (DeviceContact) -> Unit,
    onDismiss: () -> Unit
) {
    var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun loadContacts(query: String?) {
        searchJob?.cancel()
        searchJob = scope.launch {
            isLoading = true
            errorMsg = null
            try {
                contacts = repository.searchContacts(query)
            } catch (e: Exception) {
                Logger.e("ContactPicker", "Error loading contacts", e)
                errorMsg = "Error al cargar contactos: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadContacts(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Contactos del dispositivo", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Cerrar")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; loadContacts(it.ifBlank { null }) },
                    placeholder = { Text("Buscar contacto...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                when {
                    errorMsg != null -> {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                    }
                    isLoading -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    contacts.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("Sin contactos disponibles", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(contacts, key = { it.id }) { contact ->
                                DeviceContactRow(
                                    contact = contact,
                                    onClick = { onContactSelected(contact) }
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun DeviceContactRow(contact: DeviceContact, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(contact.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                contact.primaryPhone ?: "Sin número",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    )
}
