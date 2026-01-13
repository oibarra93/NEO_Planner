package com.oscaribarra.neoplanner.astro.spk

data class SpkSegmentSummary(
    val startEt: Double,
    val endEt: Double,
    val target: Int,
    val center: Int,
    val frame: Int,
    val spkType: Int,
    val dataStartAddress: Int,
    val dataEndAddress: Int
)

data class SpkKernelIndex(
    val fileFormat: String,
    val nd: Int,
    val ni: Int,
    val segments: List<SpkSegmentSummary>
) {
    fun findSegments(target: Int, center: Int): List<SpkSegmentSummary> =
        segments.filter { it.target == target && it.center == center }
}