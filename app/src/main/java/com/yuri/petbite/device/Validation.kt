package com.yuri.petbite.device

import java.net.URI

object Validation {
    fun portion(grams: Int) = grams in 5..300
    fun time(hour: Int, minute: Int) = hour in 0..23 && minute in 0..59
    fun address(raw: String): String {
        val value = raw.trim().trimEnd('/')
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IllegalArgumentException("Escribe una dirección válida, por ejemplo http://192.168.1.100")
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/") && (uri.port == -1 || uri.port in 1..65535)) {
            "Usa http://IP o https://dominio, sin rutas ni contraseñas en la dirección."
        }
        return value
    }
}
