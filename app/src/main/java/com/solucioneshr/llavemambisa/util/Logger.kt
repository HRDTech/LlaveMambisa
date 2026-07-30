package com.solucioneshr.llavemambisa.util

import android.util.Log

/**
 * Logger centralizado para la aplicación.
 * Proporciona logging estructurado con niveles: VERBOSE, DEBUG, INFO, WARNING, ERROR.
 * En debug se registra en logcat; en release se puede integrar con Crashlytics/Firebase.
 */
object Logger {
    private const val APP_TAG = "LlaveMambisa"
    private var isDebugBuild = true

    fun setDebugBuild(debug: Boolean) {
        isDebugBuild = debug
    }

    // VERBOSE: Mensajes muy detallados (trazas de ejecución)
    fun v(tag: String = APP_TAG, message: String, throwable: Throwable? = null) {
        if (isDebugBuild) {
            if (throwable != null) {
                Log.v(tag, message, throwable)
            } else {
                Log.v(tag, message)
            }
        }
    }

    // DEBUG: Mensajes de depuración útiles
    fun d(tag: String = APP_TAG, message: String, throwable: Throwable? = null) {
        if (isDebugBuild) {
            if (throwable != null) {
                Log.d(tag, message, throwable)
            } else {
                Log.d(tag, message)
            }
        }
    }

    // INFO: Mensajes informativos (inicio de operaciones, eventos importantes)
    fun i(tag: String = APP_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.i(tag, message, throwable)
        } else {
            Log.i(tag, message)
        }
    }

    // WARNING: Advertencias (situaciones inesperadas pero recuperables)
    fun w(tag: String = APP_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    // ERROR: Errores (operaciones fallidas, excepciones)
    fun e(tag: String = APP_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    // Para funciones específicas del dominio
    fun cryptoDebug(message: String) = d("Crypto", message)
    fun cryptoError(message: String, throwable: Throwable? = null) = e("Crypto", message, throwable)

    fun smsDebug(message: String) = d("SMS", message)
    fun smsError(message: String, throwable: Throwable? = null) = e("SMS", message, throwable)

    fun dbDebug(message: String) = d("Database", message)
    fun dbError(message: String, throwable: Throwable? = null) = e("Database", message, throwable)

    fun uiDebug(message: String) = d("UI", message)
    fun uiError(message: String, throwable: Throwable? = null) = e("UI", message, throwable)

    fun networkDebug(message: String) = d("Network", message)
    fun networkError(message: String, throwable: Throwable? = null) = e("Network", message, throwable)
}
