package com.oscaribarra.neoplanner.astro.spk

import android.content.Context
import java.io.File

class EphemerisStore(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "ephemeris").apply { mkdirs() }
    }

    fun kernelFile(): File = File(dir, NaifSources.KERNEL_FILE)

    fun tempDownloadFile(): File = File(dir, "${NaifSources.KERNEL_FILE}.download")
}