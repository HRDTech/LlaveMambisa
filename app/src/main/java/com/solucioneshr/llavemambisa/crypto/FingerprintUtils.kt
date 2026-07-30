package com.solucioneshr.llavemambisa.crypto

import java.security.MessageDigest

object FingerprintUtils {

    /**
     * Genera una huella digital (Safety Number) legible para el usuario.
     * Combina ambas identidades (emisor y receptor) de forma determinística
     * y devuelve una cadena de números agrupados.
     */
    fun getFingerprint(myBundle: PublicIdentityBundle, theirBundle: PublicIdentityBundle): String {
        val digest = MessageDigest.getInstance("SHA-256")
        
        // Ordenamos para que sea la misma huella en ambos dispositivos
        val a = myBundle.signingPublicKeyset + myBundle.hybridPublicKeyset
        val b = theirBundle.signingPublicKeyset + theirBundle.hybridPublicKeyset
        
        val (first, second) = if (compareByteArrays(a, b) < 0) a to b else b to a
        
        digest.update(first)
        digest.update(second)
        val hash = digest.digest()
        
        // Convertimos a bloques de 5 dígitos para fácil lectura
        return hash.toFormattedString()
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val res = a[i].compareTo(b[i])
            if (res != 0) return res
        }
        return a.size.compareTo(b.size)
    }

    private fun ByteArray.toFormattedString(): String {
        // Tomamos los primeros 30 bytes para generar 12 grupos de 5 dígitos
        val sb = StringBuilder()
        for (i in 0 until 6) {
            val offset = i * 4
            // Convertimos 4 bytes a un número largo y tomamos 5 dígitos
            val value = ((this[offset].toInt() and 0xFF) shl 24) or
                        ((this[offset + 1].toInt() and 0xFF) shl 16) or
                        ((this[offset + 2].toInt() and 0xFF) shl 8) or
                        (this[offset + 3].toInt() and 0xFF)
            
            val segment = (value.toLong() and 0xFFFFFFFFL) % 100000
            sb.append(String.format("%05d ", segment))
            if (i == 2) sb.append("\n") // Salto de línea a la mitad
        }
        return sb.toString().trim()
    }
}
