package com.oscaribarra.neoplanner.astro.spk

object NaifSources {
    // NAIF directory with planetary SPK kernels
    const val BASE_URL = "https://naif.jpl.nasa.gov/pub/naif/generic_kernels/spk/planets/"

    // Your required kernel
    const val KERNEL_FILE = "de442s.bsp"

    // MD5 list used for verification (space-separated pairs on one line)
    const val CHECKSUMS_FILE = "aa_checksums.txt"

    fun kernelUrl(): String = BASE_URL + KERNEL_FILE
    fun checksumsUrl(): String = BASE_URL + CHECKSUMS_FILE
}