package com.solucioneshr.llavemambisa.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    var myPhone: String
        get() = prefs.getString(KEY_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    var myName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    fun save(phone: String, name: String) {
        prefs.edit()
            .putString(KEY_PHONE, phone)
            .putString(KEY_NAME, name)
            .apply()
    }

    companion object {
        private const val KEY_PHONE = "phone"
        private const val KEY_NAME = "name"
    }
}
