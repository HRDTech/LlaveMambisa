package com.solucioneshr.llavemambisa.util

/**
 * Wrapper de resultado con manejo explícito de errores.
 */
sealed class Result<T> {
    class Success<T>(val data: T) : Result<T>()
    class Error<T>(val error: AppError) : Result<T>()
    class Loading<T> : Result<T>()

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(error)
        is Loading -> Loading()
    }

    fun onSuccess(action: (T) -> Unit): Result<T> { if (this is Success) action(data); return this }
    fun onError(action: (AppError) -> Unit): Result<T> { if (this is Error) action(error); return this }
    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): AppError? = (this as? Error)?.error
    fun getOrElse(default: T): T = (this as? Success)?.data ?: default
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
}

sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class InvalidInput(override val message: String, val fieldName: String? = null, override val cause: Throwable? = null) : AppError(message, cause)
    data class ValidationError(override val message: String, val errors: Map<String, String> = emptyMap(), override val cause: Throwable? = null) : AppError(message, cause)
    data class CryptoError(override val message: String, val reason: String? = null, override val cause: Throwable? = null) : AppError(message, cause)
    data class DatabaseError(override val message: String, val operation: String? = null, override val cause: Throwable? = null) : AppError(message, cause)
    data class NetworkError(override val message: String, val statusCode: Int? = null, override val cause: Throwable? = null) : AppError(message, cause)
    data class PermissionError(override val message: String, val permission: String? = null, override val cause: Throwable? = null) : AppError(message, cause)
    data class RuntimeError(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class UnknownError(override val message: String = "Un error desconocido ocurrió", override val cause: Throwable? = null) : AppError(message, cause)
    override fun toString(): String = "${this::class.simpleName}: $message".let {
        cause?.let { c -> "$it\nCausa: ${c.javaClass.simpleName}: ${c.message}" } ?: it
    }
}

fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Error -> throw IllegalStateException("Error: ${error.message}")
    is Result.Loading -> throw IllegalStateException("Result en estado Loading")
}

inline fun <T> tryResult(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Throwable) {
    Result.Error(AppError.UnknownError(e.message ?: "Error desconocido", e))
}

suspend inline fun <T> trySuspendResult(crossinline block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Throwable) {
    Result.Error(AppError.UnknownError(e.message ?: "Error desconocido", e))
}
