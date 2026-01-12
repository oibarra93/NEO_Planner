package com.oscaribarra.neoplanner.visibility

data class VisibilityConfig(
    val stepMinutes: Int = 30,
    val minAltDeg: Double = 20.0,
    val twilightLimitDeg: Double = -12.0
) {
    init {
        require(stepMinutes > 0) { "stepMinutes must be > 0" }
    }
}
