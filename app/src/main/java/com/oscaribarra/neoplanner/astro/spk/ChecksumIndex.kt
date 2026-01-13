package com.oscaribarra.neoplanner.astro.spk

import okhttp3.OkHttpClient
import okhttp3.Request

class ChecksumIndex(
    private val http: OkHttpClient = OkHttpClient()
) {
    /**
     * Returns lowercase MD5 hex for the given filename, or null if not found.
     */
    fun fetchMd5For(filename: String): String? {
        val req = Request.Builder().url(NaifSources.checksumsUrl()).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Checksum fetch failed: HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()

            // Format appears as: "<md5> <file> <md5> <file> ..." on one line
            val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            var i = 0
            while (i + 1 < tokens.size) {
                val md5 = tokens[i]
                val file = tokens[i + 1]
                if (file == filename) return md5.lowercase()
                i += 2
            }
            return null
        }
    }
}