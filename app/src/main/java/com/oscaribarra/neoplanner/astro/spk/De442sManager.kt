package com.oscaribarra.neoplanner.astro.spk

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class De442sManager(context: Context) {
    private val store = EphemerisStore(context)
    private val downloader = EphemerisDownloader(store)

    /**
     * Downloads/verifies kernel and returns the file.
     */
    suspend fun ensureKernel(): File = downloader.ensureLatestDe442s()

    /**
     * Downloads/verifies kernel and returns parsed segment summaries.
     */
    suspend fun loadKernelIndex(): SpkKernelIndex = withContext(Dispatchers.IO) {
        val file = ensureKernel()
        SpkDafReader(file).readIndex()
    }
}