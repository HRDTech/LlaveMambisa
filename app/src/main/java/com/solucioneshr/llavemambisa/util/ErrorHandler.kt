package com.solucioneshr.llavemambisa.util

import java.io.IOException
import java.security.GeneralSecurityException

/**
 * ErrorHandler centralizado. Convierte excepciones en AppError de forma consistente.
 */
object ErrorHandler {

    fun fromThrowable(throwable: Throwable): AppError = when (throwable) {
        is GeneralSecurityException -> AppError.CryptoError(
            message = "Error de seguridad: ${throwable.message}",
            reason = throwable.javaClass.simpleName,
            cause = throwable
        )
        is IllegalArgumentException -> AppError.InvalidInput(
            message = throwable.message ?: "Argumento inválido",
            cause = throwable
        )
        is IOException -> AppError.NetworkError(
            message = "Error de E/S: ${throwable.message}",
            cause = throwable
        )
        is SecurityException -> AppError.PermissionError(
            message = throwable.message ?: "Permiso denegado",
            cause = throwable
        )
        is DatabaseException -> AppError.DatabaseError(
            message = throwable.message ?: "Error en base de datos",
            operation = throwable.operation,
            cause = throwable
        )
        is NullPointerException -> AppError.RuntimeError(
            message = "Error interno: referencia nula (${throwable.message})",
            cause = throwable
        )
        else -> AppError.UnknownError(
            message = throwable.message ?: "Error desconocido",
            cause = throwable
        )
    }

    /** Envuelve un bloque en Result capturando cualquier excepción. */
    inline fun <T> safe(block: () -> T): Result<T> = try {
        Result.Success(block())
    } catch (e: Throwable) {
        Logger.e("ErrorHandler", "Excepción capturada", e)
        Result.Error(fromThrowable(e))
    }

    /** Devuelve un valor por defecto si el bloque lanza excepción. */
    inline fun <T> withDefault(default: T, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        Logger.w("ErrorHandler", "Excepción capturada, usando valor por defecto", e)
        default
    }

    /** Valida condición; devuelve Error si es falsa. */
    fun <T> check(condition: Boolean, errorMessage: String, value: T): Result<T> =
        if (condition) Result.Success(value)
        else Result.Error(AppError.InvalidInput(errorMessage))

    /** Devuelve Error si value es null. */
    fun <T : Any> requireNonNull(value: T?, errorMessage: String): Result<T> =
        if (value != null) Result.Success(value)
        else Result.Error(AppError.RuntimeError(errorMessage))
}

class DatabaseException(
    message: String,
    val operation: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

fun Throwable.toAppError(): AppError {
    Logger.e("ExceptionHandler", "Excepción: $message", this)
    return ErrorHandler.fromThrowable(this)
}
