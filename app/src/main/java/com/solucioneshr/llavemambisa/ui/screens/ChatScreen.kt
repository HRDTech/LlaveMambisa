package com.solucioneshr.llavemambisa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solucioneshr.llavemambisa.domain.model.ChatMessage
import com.solucioneshr.llavemambisa.domain.model.DeliveryState
import com.solucioneshr.llavemambisa.domain.model.MessageDirection
import com.solucioneshr.llavemambisa.domain.model.TrustLevel
import com.solucioneshr.llavemambisa.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val OutgoingBubbleLight = Color(0xFFD9FDD3)
private val OutgoingBubbleDark = Color(0xFF005C4B)
private val IncomingBubbleLight = Color(0xFFFFFFFF)
private val IncomingBubbleDark = Color(0xFF1F2C33)

private sealed class ChatListItem {
    data class DateHeader(val label: String) : ChatListItem() {
        override val id: String get() = "date_$label"
    }
    data class Message(val message: ChatMessage) : ChatListItem() {
        override val id: String get() = "msg_${message.id}"
    }
    abstract val id: String
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val contactInfo by viewModel.contactInfo.collectAsStateWithLifecycle()
    val safetyNumber by viewModel.safetyNumber.collectAsStateWithLifecycle()
    val sendState by viewModel.sendState.collectAsStateWithLifecycle()
    val shareState by viewModel.shareState.collectAsStateWithLifecycle()
    val pasteState by viewModel.pasteState.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var userScrolledUp by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val chatItems = remember(messages) {
        buildChatItems(messages)
    }

    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty() && !userScrolledUp) {
            val lastIndex = chatItems.lastIndex
            if (lastIndex >= 0) {
                listState.scrollToItem(lastIndex)
            }
        }
    }

    LaunchedEffect(shareState) {
        if (shareState is ChatViewModel.ShareState.Success) {
            val ciphertext = (shareState as ChatViewModel.ShareState.Success).ciphertext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Mensaje cifrado", ciphertext))
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, ciphertext)
                putExtra(Intent.EXTRA_SUBJECT, "Mensaje cifrado - Llave Mambisa")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir mensaje cifrado"))
            viewModel.clearShareState()
        }
    }

    LaunchedEffect(pasteState) {
        if (pasteState is ChatViewModel.PasteState.Success) {
            viewModel.clearPasteState()
        }
    }

    if (showSafetyDialog && safetyNumber != null) {
        SafetyNumberDialog(
            name = contactInfo?.displayName ?: viewModel.phoneNumber,
            safetyNumber = safetyNumber!!,
            trustLevel = contactInfo?.trustLevel ?: TrustLevel.UNVERIFIED,
            onVerify = {
                viewModel.verifyContact()
                showSafetyDialog = false
            },
            onDismiss = { showSafetyDialog = false }
        )
    }

    if (sendState is ChatViewModel.SendState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error al enviar") },
            text = { Text((sendState as ChatViewModel.SendState.Error).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    if (shareState is ChatViewModel.ShareState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.clearShareState() },
            title = { Text("Error al compartir") },
            text = { Text((shareState as ChatViewModel.ShareState.Error).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearShareState() }) { Text("OK") }
            }
        )
    }

    if (pasteState is ChatViewModel.PasteState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.clearPasteState() },
            title = { Text("Error al descifrar") },
            text = { Text((pasteState as ChatViewModel.PasteState.Error).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPasteState() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable { showSafetyDialog = true }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (contactInfo?.displayName ?: viewModel.phoneNumber)
                                        .take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contactInfo?.displayName ?: viewModel.phoneNumber,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (contactInfo?.trustLevel == TrustLevel.QR_VERIFIED || contactInfo?.trustLevel == TrustLevel.MANUAL_VERIFIED) {
                                    Icon(
                                        Icons.Filled.Verified,
                                        null,
                                        Modifier.padding(start = 4.dp).size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = if (contactInfo != null) "Cifrado E2E" else viewModel.phoneNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Opciones")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Ver código de seguridad") },
                                onClick = {
                                    showMenu = false
                                    showSafetyDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Lock, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                isSending = sendState is ChatViewModel.SendState.Sending,
                isSharing = shareState is ChatViewModel.ShareState.Sharing,
                isPasting = pasteState is ChatViewModel.PasteState.Pasting,
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                        userScrolledUp = false
                        scope.launch {
                            val lastIndex = chatItems.lastIndex
                            if (lastIndex >= 0) listState.scrollToItem(lastIndex)
                        }
                    }
                },
                onShare = {
                    if (inputText.isNotBlank()) {
                        viewModel.shareEncrypted(inputText)
                    }
                },
                onPaste = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString() ?: ""
                        if (text.isNotBlank()) {
                            viewModel.decryptPasted(text)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (isDark) Modifier.background(Color(0xFF0B141A))
                    else Modifier.background(Color(0xFFECE5DD))
                )
        ) {
            if (chatItems.isEmpty()) {
                EmptyChatPlaceholder()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(chatItems, key = { it.id }) { item ->
                        when (item) {
                            is ChatListItem.DateHeader -> DateSeparator(item.label)
                            is ChatListItem.Message -> MessageBubble(msg = item.message, isDark = isDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyNumberDialog(
    name: String,
    safetyNumber: String,
    trustLevel: TrustLevel,
    onVerify: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verificar seguridad") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Shield, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Para verificar el cifrado con $name, compara estos números con su dispositivo:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = safetyNumber,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            lineHeight = 32.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    "Si los números coinciden, el cifrado de extremo a extremo está garantizado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            if (trustLevel != TrustLevel.MANUAL_VERIFIED) {
                Button(onClick = onVerify) { Text("Marcar como verificado") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun EmptyChatPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text("Conversación cifrada", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                "Los mensajes están protegidos\ncon cifrado de extremo a extremo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildChatItems(messages: List<ChatMessage>): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()
    val items = mutableListOf<ChatListItem>()
    var lastDay = -1
    for (msg in messages) {
        val cal = Calendar.getInstance().apply { timeInMillis = msg.timestamp }
        val day = cal.get(Calendar.DAY_OF_YEAR)
        if (day != lastDay) {
            lastDay = day
            items.add(ChatListItem.DateHeader(formatDateHeader(msg.timestamp)))
        }
        items.add(ChatListItem.Message(msg))
    }
    return items
}

@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            shadowElevation = 1.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, isDark: Boolean) {
    val isOutgoing = msg.direction == MessageDirection.OUTGOING
    val bubbleColor = if (isOutgoing) (if (isDark) OutgoingBubbleDark else OutgoingBubbleLight)
                      else (if (isDark) IncomingBubbleDark else IncomingBubbleLight)
    val textColor = if (isOutgoing) (if (isDark) Color.White else Color(0xFF303030))
                    else MaterialTheme.colorScheme.onSurface
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isOutgoing) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Box(
        modifier = Modifier.fillMaxWidth().padding(start = if (isOutgoing) 48.dp else 0.dp, end = if (isOutgoing) 0.dp else 48.dp),
        contentAlignment = alignment
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp).clip(shape).background(bubbleColor).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(text = msg.body, color = textColor, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (msg.isEncryptedProtocol) {
                    Icon(
                        if (msg.signatureValid == false) Icons.Filled.Warning else Icons.Filled.Lock,
                        null, Modifier.size(10.dp),
                        tint = if (msg.signatureValid == false) MaterialTheme.colorScheme.error else textColor.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(text = formatChatTimestamp(msg.timestamp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = textColor.copy(alpha = 0.55f))
                if (isOutgoing) {
                    Spacer(Modifier.width(3.dp))
                    DeliveryStateIcon(msg.deliveryState, textColor)
                }
            }
        }
    }
}

@Composable
private fun DeliveryStateIcon(state: DeliveryState, textColor: Color) {
    val (icon, alpha) = when (state) {
        DeliveryState.PENDING -> Icons.Filled.Schedule to 0.35f
        DeliveryState.SENT -> Icons.Filled.Done to 0.5f
        DeliveryState.DELIVERED -> Icons.Filled.DoneAll to 0.65f
        DeliveryState.FAILED -> Icons.Filled.Error to 0.8f
        DeliveryState.RECEIVED -> Icons.Filled.DoneAll to 0.65f
    }
    Icon(icon, state.name, Modifier.size(12.dp), tint = if (state == DeliveryState.FAILED) MaterialTheme.colorScheme.error else textColor.copy(alpha = alpha))
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: Boolean,
    isSharing: Boolean,
    isPasting: Boolean,
    onSend: () -> Unit,
    onShare: () -> Unit,
    onPaste: () -> Unit
) {
    Surface(shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje") }, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend() }),
                maxLines = 4, textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
            )
            IconButton(
                onClick = onPaste,
                enabled = !isPasting,
                modifier = Modifier.size(36.dp)
            ) {
                if (isPasting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.ContentPaste, "Pegar cifrado", Modifier.size(20.dp))
            }
            FilledIconButton(onClick = onSend, enabled = text.isNotBlank() && !isSending, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(50)) {
                if (isSending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.AutoMirrored.Filled.Send, "Enviar SMS", Modifier.size(20.dp))
            }
            IconButton(
                onClick = onShare,
                enabled = text.isNotBlank() && !isSharing,
                modifier = Modifier.size(36.dp)
            ) {
                if (isSharing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Share, "Compartir cifrado", Modifier.size(20.dp))
            }
        }
    }
}

private fun formatChatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Ahora"
        diff < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 604_800_000L -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("d/M/yy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDateHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> "Hoy"
        now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 && now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> "Ayer"
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", Locale.forLanguageTag("es")).format(Date(timestamp))
        else -> SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("es")).format(Date(timestamp))
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
