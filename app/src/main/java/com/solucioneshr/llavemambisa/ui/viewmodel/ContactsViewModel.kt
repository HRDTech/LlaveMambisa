package com.solucioneshr.llavemambisa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solucioneshr.llavemambisa.data.DeviceContact
import com.solucioneshr.llavemambisa.data.repository.DeviceContactsRepository
import com.solucioneshr.llavemambisa.data.repository.SmsRepository
import com.solucioneshr.llavemambisa.domain.model.Contact
import com.solucioneshr.llavemambisa.domain.model.TrustLevel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MergedContact(
    val phoneNumber: String,
    val displayName: String,
    val canEncrypt: Boolean,
    val trustLevel: TrustLevel?,
    val isAppContact: Boolean,
    val isDeviceContact: Boolean
)

class ContactsViewModel(
    private val repository: SmsRepository,
    private val deviceContactsRepository: DeviceContactsRepository
) : ViewModel() {

    private val deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())

    val appContacts: StateFlow<List<Contact>> = repository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mergedContacts: StateFlow<List<MergedContact>> = combine(
        appContacts,
        deviceContacts
    ) { app, device ->
        val appMap = app.associateBy { normalizePhone(it.phoneNumber) }

        val deviceMerged = device.map { dc ->
            val phone = normalizePhone(dc.primaryPhone ?: "")
            val appContact = appMap[phone]
            MergedContact(
                phoneNumber = phone,
                displayName = dc.displayName,
                canEncrypt = appContact != null,
                trustLevel = appContact?.trustLevel,
                isAppContact = appContact != null,
                isDeviceContact = true
            )
        }

        val appOnly = app
            .filter { a -> deviceMerged.none { it.phoneNumber == normalizePhone(a.phoneNumber) } }
            .map { a ->
                MergedContact(
                    phoneNumber = normalizePhone(a.phoneNumber),
                    displayName = a.displayName,
                    canEncrypt = true,
                    trustLevel = a.trustLevel,
                    isAppContact = true,
                    isDeviceContact = false
                )
            }

        (deviceMerged + appOnly).sortedBy { it.displayName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refreshDeviceContacts() }

    fun refreshDeviceContacts() {
        viewModelScope.launch {
            deviceContacts.value = deviceContactsRepository.searchContacts()
        }
    }

    fun searchDeviceContacts(query: String) {
        viewModelScope.launch {
            deviceContacts.value = deviceContactsRepository.searchContacts(query.ifBlank { null })
        }
    }

    private fun normalizePhone(raw: String) = raw.filter { it.isDigit() || it == '+' }

    class Factory(
        private val repository: SmsRepository,
        private val deviceContactsRepository: DeviceContactsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ContactsViewModel(repository, deviceContactsRepository) as T
    }
}
