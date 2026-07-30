package com.solucioneshr.llavemambisa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solucioneshr.llavemambisa.domain.model.TrustLevel
import com.solucioneshr.llavemambisa.ui.viewmodel.ContactsViewModel
import com.solucioneshr.llavemambisa.ui.viewmodel.MergedContact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onContactClick: (phoneNumber: String) -> Unit,
    onAddContact: () -> Unit
) {
    val mergedContacts by viewModel.mergedContacts.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val displayContacts = if (searchQuery.isBlank()) mergedContacts
    else mergedContacts.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.phoneNumber.contains(searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contactos", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddContact,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Añadir cifrado") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchDeviceContacts(it)
                },
                placeholder = { Text("Buscar contacto...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true
            )

            if (displayContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.People,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Sin contactos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Escanea el QR de otro usuario\npara añadirlo como contacto cifrado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(displayContacts, key = { it.phoneNumber }) { contact ->
                        MergedContactRow(
                            contact = contact,
                            onClick = { onContactClick(contact.phoneNumber) }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MergedContactRow(contact: MergedContact, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    contact.displayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.canEncrypt) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Cifrado disponible",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        supportingContent = {
            Text(
                contact.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = if (contact.canEncrypt) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (contact.canEncrypt) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!contact.isDeviceContact) {
                    Icon(
                        Icons.Filled.Phone,
                        contentDescription = "Solo app",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                if (contact.trustLevel != null) {
                    TrustBadge(contact.trustLevel)
                }
            }
        }
    )
}

@Composable
private fun TrustBadge(trustLevel: TrustLevel) {
    val (icon, label, color) = when (trustLevel) {
        TrustLevel.QR_VERIFIED -> Triple(Icons.Filled.QrCodeScanner, "QR", MaterialTheme.colorScheme.primary)
        TrustLevel.MANUAL_VERIFIED -> Triple(Icons.Filled.VerifiedUser, "Manual", MaterialTheme.colorScheme.tertiary)
        TrustLevel.UNVERIFIED -> Triple(Icons.AutoMirrored.Filled.HelpOutline, "No verif.", MaterialTheme.colorScheme.outline)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
