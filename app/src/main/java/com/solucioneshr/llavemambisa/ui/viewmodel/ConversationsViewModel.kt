package com.solucioneshr.llavemambisa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solucioneshr.llavemambisa.data.repository.SmsRepository
import com.solucioneshr.llavemambisa.domain.model.ChatMessage
import com.solucioneshr.llavemambisa.domain.model.Contact
import kotlinx.coroutines.flow.*

data class ConversationItem(
    val lastMessage: ChatMessage,
    val contactName: String?
)

class ConversationsViewModel(private val repository: SmsRepository) : ViewModel() {

    val conversations: StateFlow<List<ConversationItem>> = combine(
        repository.observeRecentActivity(),
        repository.observeContacts()
    ) { messages, contacts ->
        val contactMap: Map<String, Contact> = contacts.associateBy { normalizePhone(it.phoneNumber) }
        messages
            .filter { it.isEncryptedProtocol }
            .distinctBy { it.contactPhoneNumber }
            .map { msg ->
                ConversationItem(
                    lastMessage = msg,
                    contactName = contactMap[normalizePhone(msg.contactPhoneNumber)]?.displayName
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun normalizePhone(raw: String) = raw.filter { it.isDigit() || it == '+' }

    class Factory(private val repository: SmsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConversationsViewModel(repository) as T
    }
}
