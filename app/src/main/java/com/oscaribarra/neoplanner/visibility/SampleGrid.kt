package com.oscaribarra.neoplanner.visibility

import java.time.Duration
import java.time.ZonedDateTime

object SampleGrid {

    /**
     * Generates times from start..end inclusive (if aligned), stepping by config.stepMinutes.
     * If end isn't aligned, the last time will be < end (typical sampling behavior).
     */
    fun generate(
        start: ZonedDateTime,
        hoursAhead: Int,
        stepMinutes: Int
    ): List<ZonedDateTime> {
        require(hoursAhead > 0) { "hoursAhead must be > 0" }
        require(stepMinutes > 0) { "stepMinutes must be > 0" }

        val end = start.plusHours(hoursAhead.toLong())
        val step = Duration.ofMinutes(stepMinutes.toLong())

        val out = ArrayList<ZonedDateTime>(hoursAhead * (60 / stepMinutes) + 2)
        var t = start
        while (!t.isAfter(end)) {
            out.add(t)
            t = t.plus(step)
        }
        return out
    }
}
