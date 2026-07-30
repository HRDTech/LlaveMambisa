package com.solucioneshr.llavemambisa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solucioneshr.llavemambisa.data.repository.SmsRepository
import com.solucioneshr.llavemambisa.domain.model.ChatMessage
import com.solucioneshr.llavemambisa.domain.model.Contact
import com.solucioneshr.llavemambisa.domain.model.TrustLevel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: SmsRepository,
    val phoneNumber: String
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = repository.observeHistory(phoneNumber)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contactInfo: StateFlow<Contact?> = repository.observeContacts()
        .map { contacts -> contacts.find { it.phoneNumber == normalizePhone(phoneNumber) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val safetyNumber: StateFlow<String?> = contactInfo.map { contact ->
        contact?.let { repository.getFingerprintFor(it.identity) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed class SendState {
        object Idle : SendState()
        object Sending : SendState()
        data class Error(val message: String) : SendState()
    }

    sealed class ShareState {
        object Idle : ShareState()
        object Sharing : ShareState()
        data class Success(val ciphertext: String) : ShareState()
        data class Error(val message: String) : ShareState()
    }

    sealed class PasteState {
        object Idle : PasteState()
        object Pasting : PasteState()
        data class Success(val plaintext: String) : PasteState()
        data class Error(val message: String) : PasteState()
    }

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState.asStateFlow()

    private val _pasteState = MutableStateFlow<PasteState>(PasteState.Idle)
    val pasteState: StateFlow<PasteState> = _pasteState.asStateFlow()

    fun sendMessage(text: String, forcePlaintext: Boolean = false) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _sendState.value = SendState.Sending
            _sendState.value = when (val r = repository.sendMessage(phoneNumber, text.trim(), forcePlaintext)) {
                is SmsRepository.SendResult.Success -> SendState.Idle
                is SmsRepository.SendResult.Failure -> SendState.Error(r.reason)
            }
        }
    }

    fun shareEncrypted(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _shareState.value = ShareState.Sharing
            val ciphertext = repository.encryptMessageForExternal(phoneNumber, text.trim())
            _shareState.value = if (ciphertext != null) {
                ShareState.Success(ciphertext)
            } else {
                ShareState.Error("No se pudo cifrar. ¿Contacto añadido?")
            }
        }
    }

    fun decryptPasted(ciphertext: String) {
        if (ciphertext.isBlank()) return
        viewModelScope.launch {
            _pasteState.value = PasteState.Pasting
            val plaintext = repository.decryptExternalMessage(ciphertext.trim(), phoneNumber)
            _pasteState.value = if (plaintext != null) {
                PasteState.Success(plaintext)
            } else {
                PasteState.Error("No se pudo descifrar. ¿Contacto añadido?")
            }
        }
    }

    fun verifyContact() {
        viewModelScope.launch {
            repository.updateTrustLevel(phoneNumber, TrustLevel.MANUAL_VERIFIED)
        }
    }

    fun clearError() { _sendState.value = SendState.Idle }

    fun clearShareState() { _shareState.value = ShareState.Idle }

    fun clearPasteState() { _pasteState.value = PasteState.Idle }

    private fun normalizePhone(raw: String) = raw.filter { it.isDigit() || it == '+' }

    class Factory(
        private val repository: SmsRepository,
        private val phoneNumber: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(repository, phoneNumber) as T
    }
}
