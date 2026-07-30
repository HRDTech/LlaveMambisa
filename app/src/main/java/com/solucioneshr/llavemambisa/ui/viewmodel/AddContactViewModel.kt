package com.solucioneshr.llavemambisa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.solucioneshr.llavemambisa.data.repository.DeviceContactsRepository
import com.solucioneshr.llavemambisa.data.repository.SmsRepository
import com.solucioneshr.llavemambisa.domain.model.TrustLevel
import com.solucioneshr.llavemambisa.util.QrPayload
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AddContactViewModel(
    private val repository: SmsRepository,
    val deviceContactsRepository: DeviceContactsRepository
) : ViewModel() {

    sealed class State {
        object Idle : State()
        data class QrScanned(val parsed: QrPayload.ParsedContact) : State()
        object Saving : State()
        data class Saved(val name: String, val phone: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onQrDetected(rawQr: String) {
        val parsed = QrPayload.decode(rawQr)
        if (parsed == null) {
            _state.value = State.Error("Código QR inválido o no compatible con Llave Mambisa")
        } else {
            _state.value = State.QrScanned(parsed)
        }
    }

    fun saveScannedContact(parsed: QrPayload.ParsedContact) {
        viewModelScope.launch {
            _state.value = State.Saving
            runCatching {
                repository.addOrUpdateContact(
                    phoneNumber = parsed.phone,
                    displayName = parsed.name,
                    identity = parsed.bundle,
                    trustLevel = TrustLevel.QR_VERIFIED
                )
            }.onSuccess {
                _state.value = State.Saved(parsed.name, parsed.phone)
            }.onFailure { e ->
                _state.value = State.Error("Error al guardar: ${e.message}")
            }
        }
    }

    fun saveManualContact(phone: String, name: String, publicKeyText: String) {
        if (phone.isBlank()) { _state.value = State.Error("Número de teléfono requerido"); return }
        if (name.isBlank()) { _state.value = State.Error("Nombre requerido"); return }
        val parsed = QrPayload.decode(publicKeyText)
        if (parsed == null) {
            _state.value = State.Error("Texto de clave pública inválido. Copia el texto completo desde el perfil del otro usuario.")
            return
        }
        saveScannedContact(parsed.copy(phone = phone.trim(), name = name.trim()))
    }

    fun reset() { _state.value = State.Idle }

    class Factory(
        private val repository: SmsRepository,
        private val deviceContactsRepository: DeviceContactsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddContactViewModel(repository, deviceContactsRepository) as T
    }
}
