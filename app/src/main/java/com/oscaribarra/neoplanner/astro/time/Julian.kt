package com.oscaribarra.neoplanner.astro.time

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor

/**
 * Julian Date utilities.
 *
 * We work primarily with:
 *  - JD(UTC) for user-facing time and Earth rotation (GMST)
 *  - JD(TT) and seconds since J2000 for ephemeris time (SPK)
 */
object Julian {
    /** Julian Date of J2000 epoch: 2000-01-01 12:00:00 TT */
    const val JD_J2000_TT = 2451545.0

    /** Seconds per day. */
    private const val SECONDS_PER_DAY = 86400.0

    /**
     * Convert an Instant (UTC) to Julian Date (UTC approximation).
     *
     * This uses the standard Unix epoch conversion:
     * JD = 2440587.5 + unixSeconds / 86400
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun jdUtc(instant: Instant): Double {
        val unixSeconds = instant.epochSecond.toDouble()
        val nanos = instant.nano.toDouble()
        return 2440587.5 + (unixSeconds + nanos * 1e-9) / SECONDS_PER_DAY
    }

    fun jdUtc(zdt: ZonedDateTime): Double = jdUtc(zdt.toInstant())

    /**
     * Convert JD(UTC) to Instant (UTC) using the inverse of jdUtc().
     * Note: This is a pure math inverse; leap seconds are ignored at this layer.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun instantFromJdUtc(jdUtc: Double): Instant {
        val daysSinceUnix = jdUtc - 2440587.5
        val totalSeconds = daysSinceUnix * SECONDS_PER_DAY
        val sec = floor(totalSeconds).toLong()
        val nanos = ((totalSeconds - sec) * 1e9).toLong().coerceIn(0, 999_999_999)
        return Instant.ofEpochSecond(sec, nanos)
    }

    /**
     * Convert JD(UTC) to seconds since J2000 (TT) approximately.
     * This calls TimeScales.jdTtFromJdUtc() for UTC->TT.
     */
    fun secondsSinceJ2000TtFromJdUtc(jdUtc: Double): Double {
        val jdTt = TimeScales.jdTtFromJdUtc(jdUtc)
        return (jdTt - JD_J2000_TT) * SECONDS_PER_DAY
    }

    fun secondsSinceJ2000Tt(instant: Instant): Double =
        secondsSinceJ2000TtFromJdUtc(jdUtc(instant))

    fun toLocalZdt(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        ZonedDateTime.ofInstant(instant, zoneId)
}
