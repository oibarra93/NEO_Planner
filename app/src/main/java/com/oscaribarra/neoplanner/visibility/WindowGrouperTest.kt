package com.oscaribarra.neoplanner.visibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WindowGrouperTest {

    @Test
    fun groupsAndPicksBestWindow() {
        val tz = ZoneId.of("America/Los_Angeles")
        val t0 = ZonedDateTime.of(2026, 1, 12, 18, 0, 0, 0, tz)

        val cfg = VisibilityConfig(stepMinutes = 30, minAltDeg = 20.0, twilightLimitDeg = -12.0)

        // Sun is dark enough always (-20), NEO alt rises and falls
        val samples = listOf(
            s(t0, 10.0, 100.0, -20.0), // not visible
            s(t0.plusMinutes(30), 25.0, 110.0, -20.0), // window 1 start
            s(t0.plusMinutes(60), 40.0, 120.0, -20.0), // peak
            s(t0.plusMinutes(90), 22.0, 130.0, -20.0), // window 1 end
            s(t0.plusMinutes(120), 15.0, 140.0, -20.0), // gap
            s(t0.plusMinutes(150), 21.0, 150.0, -20.0), // window 2 start
            s(t0.plusMinutes(180), 30.0, 160.0, -20.0), // peak2
        )

        val result = VisibilityEngine.evaluate(samples, cfg)

        assertEquals(2, result.windows.size)
        val best = result.best
        assertNotNull(best)

        // Best should be window1 because peak 40 > 30
        assertEquals(t0.plusMinutes(30), best!!.start)
        assertEquals(t0.plusMinutes(90), best.end)
        assertEquals(40.0, best.peakAltDeg, 1e-9)
        assertEquals(t0.plusMinutes(60), best.peakTime)
    }

    private fun s(t: ZonedDateTime, neoAlt: Double, neoAz: Double, sunAlt: Double) =
        VisibilitySample(t, neoAlt, neoAz, sunAlt)
}
