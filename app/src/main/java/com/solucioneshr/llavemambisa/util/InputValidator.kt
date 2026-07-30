package com.solucioneshr.llavemambisa.util

import android.util.Patterns

/**
 * Validador de entrada de datos para la aplicación.
 */
object InputValidator {

    private const val PHONE_MIN_LEN = 7
    private const val PHONE_MAX_LEN = 15
    private const val NAME_MIN_LEN = 2
    private const val NAME_MAX_LEN = 100
    private const val MSG_MAX_LEN = 480 // ~3 partes SMS

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val messages: List<String>) : ValidationResult()
        fun isValid(): Boolean = this is Valid
        fun firstError(): String? = (this as? Invalid)?.messages?.firstOrNull()
        fun allErrors(): List<String> = (this as? Invalid)?.messages ?: emptyList()
    }

    fun validatePhoneNumber(phone: String?): ValidationResult {
        if (phone.isNullOrBlank()) return ValidationResult.Invalid(listOf("El número no puede estar vacío"))
        val trimmed = phone.trim()
        val errs = mutableListOf<String>()
        // Primero verificar que solo contenga +, dígitos y espacios/guiones admitidos
        if (!trimmed.matches(Regex("^\\+?[\\d\\s\\-()]+$"))) {
            errs += "El número contiene caracteres inválidos (solo dígitos, +, -, espacios)"
            return ValidationResult.Invalid(errs)
        }
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < PHONE_MIN_LEN) errs += "Mínimo $PHONE_MIN_LEN dígitos"
        if (digits.length > PHONE_MAX_LEN) errs += "Máximo $PHONE_MAX_LEN dígitos"
        return if (errs.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errs)
    }

    fun validateDisplayName(name: String?): ValidationResult {
        if (name.isNullOrBlank()) return ValidationResult.Invalid(listOf("El nombre no puede estar vacío"))
        val n = name.trim()
        val errs = mutableListOf<String>()
        if (n.length < NAME_MIN_LEN) errs += "El nombre debe tener al menos $NAME_MIN_LEN caracteres"
        if (n.length > NAME_MAX_LEN) errs += "El nombre no puede exceder $NAME_MAX_LEN caracteres"
        if (n.contains(Regex("[<>\"'%;()&+]"))) errs += "El nombre contiene caracteres especiales inválidos"
        return if (errs.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errs)
    }

    fun validateMessage(message: String?): ValidationResult {
        if (message.isNullOrBlank()) return ValidationResult.Invalid(listOf("El mensaje no puede estar vacío"))
        return if (message.length > MSG_MAX_LEN)
            ValidationResult.Invalid(listOf("El mensaje excede el límite ($MSG_MAX_LEN caracteres)"))
        else ValidationResult.Valid
    }

    fun validateEmail(email: String?): ValidationResult {
        if (email.isNullOrBlank()) return ValidationResult.Invalid(listOf("El email no puede estar vacío"))
        return if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())
            ValidationResult.Invalid(listOf("Email con formato inválido"))
        else ValidationResult.Valid
    }

    fun validateContact(phoneNumber: String?, displayName: String?): ValidationResult {
        val errs = mutableListOf<String>()
        validatePhoneNumber(phoneNumber).allErrors().let { errs += it }
        validateDisplayName(displayName).allErrors().let { errs += it }
        return if (errs.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errs)
    }
}
