package com.oscaribarra.neoplanner.ui

import java.net.HttpURLConnection
import java.net.URL

/**
 * Tiny HTTP helper to avoid adding new networking deps.
 * (If you already use OkHttp/Ktor elsewhere, we can swap this later.)
 */
object SimpleHttp {
    fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("HTTP $code: $text")
            text
        } finally {
            conn.disconnect()
        }
    }
}
