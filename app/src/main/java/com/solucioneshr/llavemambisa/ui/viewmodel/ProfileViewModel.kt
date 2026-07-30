package com.solucioneshr.llavemambisa.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.solucioneshr.llavemambisa.crypto.KeyManager
import com.solucioneshr.llavemambisa.data.UserPreferences
import com.solucioneshr.llavemambisa.data.repository.SmsRepository
import com.solucioneshr.llavemambisa.util.Logger
import com.solucioneshr.llavemambisa.util.QrPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(
    private val keyManager: KeyManager,
    private val userPreferences: UserPreferences,
    private val smsRepository: SmsRepository
) : ViewModel() {

    data class ProfileState(
        val qrBitmap: Bitmap? = null,
        val publicKeyText: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val myPhone: String = "",
        val myName: String = "",
        val selfContactAdded: Boolean = false,
        val testSendResult: String? = null
    )

    private val _state = MutableStateFlow(
        ProfileState(
            myPhone = userPreferences.myPhone,
            myName = userPreferences.myName
        )
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        if (userPreferences.myPhone.isNotBlank() || userPreferences.myName.isNotBlank()) {
            loadProfile(userPreferences.myPhone, userPreferences.myName)
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun loadProfile(myPhone: String = _state.value.myPhone, myName: String = _state.value.myName) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, myPhone = myPhone, myName = myName) }
            runCatching {
                val bundle = withContext(Dispatchers.IO) { keyManager.exportPublicIdentityBundle() }
                val qrContent = QrPayload.encode(myPhone, myName.ifBlank { "Yo" }, bundle)
                val bitmap = withContext(Dispatchers.Default) { generateQrBitmap(qrContent) }
                qrContent to bitmap
            }.onSuccess { (text, bmp) ->
                _state.update { it.copy(qrBitmap = bmp, publicKeyText = text, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = "No se pudo exportar el perfil: ${e.message}") }
            }
        }
    }

    fun updateIdentity(myPhone: String, myName: String) {
        userPreferences.save(myPhone, myName)
        loadProfile(myPhone, myName)
    }

    fun addSelfAsContact() {
        val phone = _state.value.myPhone
        if (phone.isBlank()) {
            _state.update { it.copy(testSendResult = "Configura tu número en el perfil primero") }
            return
        }
        val name = _state.value.myName.ifBlank { "Yo" }
        viewModelScope.launch {
            smsRepository.addSelfContact(phone, name)
            _state.update { it.copy(selfContactAdded = true) }
        }
    }

    fun sendTestToSelf() {
        val phone = _state.value.myPhone
        if (phone.isBlank()) {
            _state.update { it.copy(testSendResult = "Configura tu número en el perfil primero") }
            return
        }
        if (!_state.value.selfContactAdded) {
            _state.update { it.copy(testSendResult = "Añádate como contacto primero") }
            return
        }
        viewModelScope.launch {
            val result = smsRepository.sendTestToSelf(phone)
            _state.update {
                it.copy(
                    testSendResult = when (result) {
                        is SmsRepository.SendResult.Success -> "SMS de prueba enviado (${result.parts} parte(s))"
                        is SmsRepository.SendResult.Failure -> "Error: ${result.reason}"
                    }
                )
            }
        }
    }

    fun clearTestResult() {
        _state.update { it.copy(testSendResult = null) }
    }

    private fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    class Factory(
        private val keyManager: KeyManager,
        private val userPreferences: UserPreferences,
        private val smsRepository: SmsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(keyManager, userPreferences, smsRepository) as T
    }
}
