package com.oscaribarra.neoplanner.visibility

import java.time.ZonedDateTime

data class VisibilitySample(
    val t: ZonedDateTime,
    val neoAltDeg: Double,
    val neoAzDeg: Double,
    val sunAltDeg: Double
) {
    val isFinite: Boolean
        get() = neoAltDeg.isFinite() && neoAzDeg.isFinite() && sunAltDeg.isFinite()
}

data class WindowPoint(
    val t: ZonedDateTime,
    val altDeg: Double,
    val azDeg: Double
)

data class VisibilityWindow(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val peak: WindowPoint
) {
    val peakAltDeg: Double get() = peak.altDeg
    val peakAzDeg: Double get() = peak.azDeg
    val peakTime: ZonedDateTime get() = peak.t
}
