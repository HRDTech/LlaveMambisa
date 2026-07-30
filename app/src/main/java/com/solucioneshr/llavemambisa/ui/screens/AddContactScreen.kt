package com.solucioneshr.llavemambisa.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.solucioneshr.llavemambisa.ui.camera.QrScannerView
import com.solucioneshr.llavemambisa.ui.viewmodel.AddContactViewModel
import com.solucioneshr.llavemambisa.util.QrPayload

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddContactScreen(
    viewModel: AddContactViewModel,
    onBack: () -> Unit,
    onContactSaved: (phoneNumber: String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Escanear QR", "Manual")

    LaunchedEffect(state) {
        if (state is AddContactViewModel.State.Saved) {
            onContactSaved((state as AddContactViewModel.State.Saved).phone)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Añadir Contacto", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; viewModel.reset() },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> QrScanTab(state = state, viewModel = viewModel)
                1 -> ManualTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrScanTab(
    state: AddContactViewModel.State,
    viewModel: AddContactViewModel
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        !cameraPermission.status.isGranted -> {
            CameraPermissionRequest(
                showRationale = cameraPermission.status.shouldShowRationale,
                onRequest = { cameraPermission.launchPermissionRequest() }
            )
        }
        state is AddContactViewModel.State.QrScanned -> {
            ConfirmContactCard(
                parsed = state.parsed,
                onConfirm = { viewModel.saveScannedContact(state.parsed) },
                onCancel = { viewModel.reset() }
            )
        }
        state is AddContactViewModel.State.Saving -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state is AddContactViewModel.State.Error -> {
            ErrorCard(message = state.message, onRetry = { viewModel.reset() })
        }
        else -> {
            Box(Modifier.fillMaxSize()) {
                QrScannerView(
                    onQrDetected = { viewModel.onQrDetected(it) },
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay de guía
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))) {
                        Text(
                            "Apunta la cámara al código QR del otro usuario",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualTab(
    state: AddContactViewModel.State,
    viewModel: AddContactViewModel
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var publicKeyText by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DeviceContactPickerDialog(
            repository = viewModel.deviceContactsRepository,
            onContactSelected = { contact ->
                showPicker = false
                phone = contact.primaryPhone ?: phone
                name = contact.displayName
            },
            onDismiss = { showPicker = false }
        )
    }

    when {
        state is AddContactViewModel.State.Saving -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state is AddContactViewModel.State.Error -> {
            ErrorCard(message = state.message, onRetry = { viewModel.reset() })
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Introduce los datos del contacto y pega el texto de su clave pública (obtenido desde su pantalla de Perfil).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Número de teléfono") },
                        leadingIcon = { Icon(Icons.Filled.Phone, null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Filled.People, "Seleccionar contacto")
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    leadingIcon = { Icon(Icons.Filled.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = publicKeyText,
                    onValueChange = { publicKeyText = it },
                    label = { Text("Clave pública (texto LLAVE:1:...)") },
                    leadingIcon = { Icon(Icons.Filled.Key, null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                Button(
                    onClick = { viewModel.saveManualContact(phone, name, publicKeyText) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phone.isNotBlank() && name.isNotBlank() && publicKeyText.isNotBlank()
                ) {
                    Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar contacto")
                }
            }
        }
    }
}

@Composable
private fun ConfirmContactCard(
    parsed: QrPayload.ParsedContact,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Contacto detectado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledInfo("Nombre", parsed.name)
                LabeledInfo("Teléfono", parsed.phone)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Verificado por QR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir")
            }
        }
    }
}

@Composable
private fun LabeledInfo(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label + ":", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CameraPermissionRequest(showRationale: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CameraAlt, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            if (showRationale) "La app necesita acceso a la cámara para escanear el código QR del contacto."
            else "Se requiere permiso de cámara para escanear QR.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Conceder permiso") }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Error, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

