package com.oscaribarra.neoplanner

import android.os.Build
import androidx.annotation.RequiresApi
import com.oscaribarra.neoplanner.astro.time.Julian
import com.oscaribarra.neoplanner.astro.time.SiderealTime
import java.time.Instant
import kotlin.math.abs
import org.junit.Test

class JulianTest {

    @Test
    fun unixEpochIsKnownJd() {
        val jd = Julian.jdUtc(Instant.EPOCH)
        assertEquals(2440587.5, jd, 1e-9)
    }

    @Test
    fun roundTripJdInstant() {
        val inst = Instant.parse("2026-01-12T12:34:56Z")
        val jd = Julian.jdUtc(inst)
        val inst2 = Julian.instantFromJdUtc(jd)
        // allow small nanosecond-level rounding error
        val diff = abs(inst.epochSecond - inst2.epochSecond)
        assertEquals(0.0, diff.toDouble(), 1.0)
    }

    @Test
    fun gmstIsNormalized() {
        val jd = 2451545.0
        val gmst = SiderealTime.gmstRadFromJdUtc(jd)
        // should be within [0, 2pi)
        assert(gmst >= 0.0 && gmst < 2.0 * Math.PI)
    }
}