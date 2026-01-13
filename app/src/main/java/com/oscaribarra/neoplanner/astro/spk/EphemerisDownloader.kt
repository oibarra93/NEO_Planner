package com.oscaribarra.neoplanner.astro.spk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class EphemerisDownloader(
    private val store: EphemerisStore,
    private val checksumIndex: ChecksumIndex = ChecksumIndex(),
    private val http: OkHttpClient = OkHttpClient()
) {

    /**
     * Ensures the DE442s kernel exists and matches NAIF's published MD5.
     * Returns the local kernel file.
     */
    suspend fun ensureLatestDe442s(): File = withContext(Dispatchers.IO) {
        val target = store.kernelFile()
        val tmp = store.tempDownloadFile()

        val expectedMd5 = checksumIndex.fetchMd5For(NaifSources.KERNEL_FILE)
            ?: error("MD5 not found for ${NaifSources.KERNEL_FILE} in aa_checksums.txt")

        // If file exists and matches MD5, we’re done.
        if (target.exists()) {
            val current = Md5.hexOf(target)
            if (current.equals(expectedMd5, ignoreCase = true)) {
                return@withContext target
            }
        }

        // Fresh download to temp, then verify, then move into place
        if (tmp.exists()) tmp.delete()

        downloadToFile(NaifSources.kernelUrl(), tmp)

        val downloadedMd5 = Md5.hexOf(tmp)
        if (!downloadedMd5.equals(expectedMd5, ignoreCase = true)) {
            tmp.delete()
            error("Kernel MD5 mismatch. Expected=$expectedMd5 got=$downloadedMd5")
        }

        // Atomic-ish replace
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // fallback copy
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }

        target
    }

    private fun downloadToFile(url: String, outFile: File) {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Kernel download failed: HTTP ${resp.code}")
            val body = resp.body ?: error("Empty response body")
            FileOutputStream(outFile).use { fos ->
                body.byteStream().use { input ->
                    val buf = ByteArray(1024 * 64)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                    }
                }
            }
        }
    }
}
