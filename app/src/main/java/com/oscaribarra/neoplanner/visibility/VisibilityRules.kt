package com.oscaribarra.neoplanner.visibility

object VisibilityRules {

    /**
     * Visible when:
     *  - NEO altitude >= minAltDeg
     *  - Sun altitude <= twilightLimitDeg (e.g. -12 nautical twilight)
     */
    fun isVisible(
        neoAltDeg: Double,
        sunAltDeg: Double,
        minAltDeg: Double,
        twilightLimitDeg: Double
    ): Boolean {
        return neoAltDeg >= minAltDeg && sunAltDeg <= twilightLimitDeg
    }
}
