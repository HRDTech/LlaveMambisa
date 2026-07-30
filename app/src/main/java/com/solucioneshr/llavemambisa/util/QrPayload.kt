package com.solucioneshr.llavemambisa.util

import android.util.Base64
import com.solucioneshr.llavemambisa.crypto.PublicIdentityBundle
import org.json.JSONObject

/**
 * Formato de intercambio de identidad pública vía QR o texto.
 * Payload: "LLAVE:1:<base64_del_json>"
 * JSON: { v:1, phone:"...", name:"...", sig:"<base64>", hyb:"<base64>" }
 */
object QrPayload {
    private const val PREFIX = "LLAVE:1:"

    fun encode(phone: String, name: String, bundle: PublicIdentityBundle): String {
        val json = JSONObject().apply {
            put("v", 1)
            put("phone", phone)
            put("name", name)
            put("sig", Base64.encodeToString(bundle.signingPublicKeyset, Base64.NO_WRAP))
            put("hyb", Base64.encodeToString(bundle.hybridPublicKeyset, Base64.NO_WRAP))
        }
        val encoded = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return PREFIX + encoded
    }

    data class ParsedContact(
        val phone: String,
        val name: String,
        val bundle: PublicIdentityBundle
    )

    fun decode(text: String): ParsedContact? = runCatching {
        val payload = if (text.startsWith(PREFIX)) text.removePrefix(PREFIX) else text
        val jsonStr = String(Base64.decode(payload, Base64.NO_WRAP), Charsets.UTF_8)
        val json = JSONObject(jsonStr)
        ParsedContact(
            phone = json.getString("phone"),
            name = json.getString("name"),
            bundle = PublicIdentityBundle(
                signingPublicKeyset = Base64.decode(json.getString("sig"), Base64.NO_WRAP),
                hybridPublicKeyset  = Base64.decode(json.getString("hyb"), Base64.NO_WRAP)
            )
        )
    }.getOrNull()
}

